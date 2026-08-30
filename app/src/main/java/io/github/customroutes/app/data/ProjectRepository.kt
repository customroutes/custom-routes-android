package io.github.customroutes.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import io.github.customroutes.app.domain.BinaryMask
import io.github.customroutes.app.domain.HoldRole
import io.github.customroutes.app.domain.MaskRegion
import io.github.customroutes.app.domain.RouteHold
import io.github.customroutes.app.domain.RouteProject
import io.github.customroutes.app.domain.SourceRect
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class ProjectSummary(
    val id: String,
    val name: String?,
    val updatedAtEpochMillis: Long,
    val holdCount: Int,
    val isComplete: Boolean,
)

class ProjectRepository(private val context: Context) {
    private val projectsDirectory = File(context.filesDir, "projects")
    private val thumbnailCache = ProjectThumbnailCache(context)

    init {
        projectsDirectory.mkdirs()
    }

    fun list(): List<ProjectSummary> = projectsDirectory.listFiles()
        .orEmpty()
        .mapNotNull { directory ->
            runCatching { readSummary(File(directory, PROJECT_FILE)) }.getOrNull()
        }
        .sortedByDescending { it.updatedAtEpochMillis }

    fun storedProjectCount(): Int = projectsDirectory.listFiles().orEmpty().size

    fun import(uri: Uri, now: Long = System.currentTimeMillis()): RouteProject {
        val id = UUID.randomUUID().toString()
        val directory = projectDirectory(id).also { check(it.mkdirs()) }
        val source = File(directory, SOURCE_FILE)
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "The selected photo could not be opened" }
                source.outputStream().use(input::copyTo)
            }
            var sourceWidth = 0
            var sourceHeight = 0
            var workingWidth = 0
            var workingHeight = 0
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, info, _ ->
                sourceWidth = info.size.width
                sourceHeight = info.size.height
                val scale = minOf(1f, WORKING_LONG_EDGE.toFloat() / maxOf(sourceWidth, sourceHeight))
                workingWidth = (sourceWidth * scale).toInt().coerceAtLeast(1)
                workingHeight = (sourceHeight * scale).toInt().coerceAtLeast(1)
                decoder.setTargetSize(workingWidth, workingHeight)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }.recycle()

            return RouteProject(
                id = id,
                name = null,
                sourceFileName = SOURCE_FILE,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                workingWidth = workingWidth,
                workingHeight = workingHeight,
                updatedAtEpochMillis = now,
            ).also(::save)
        } catch (error: Throwable) {
            directory.deleteRecursively()
            throw error
        }
    }

    fun load(id: String): RouteProject = readProject(File(projectDirectory(id), PROJECT_FILE))

    fun save(project: RouteProject) {
        val directory = projectDirectory(project.id)
        check(directory.exists()) { "Project directory does not exist" }
        val target = File(directory, PROJECT_FILE)
        val temporary = File(directory, "$PROJECT_FILE.tmp")
        temporary.writeText(project.toJson().toString())
        Files.move(
            temporary.toPath(),
            target.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    fun delete(id: String) {
        thumbnailCache.delete(id)
        check(projectDirectory(id).deleteRecursively()) { "Project could not be deleted" }
    }

    fun deleteAll() {
        thumbnailCache.deleteAll()
        projectsDirectory.listFiles().orEmpty().forEach { project ->
            check(project.deleteRecursively()) { "Project ${project.name} could not be deleted" }
        }
    }

    fun loadWorkingBitmap(project: RouteProject): Bitmap = decodeBitmap(
        project = project,
        width = project.workingWidth,
        height = project.workingHeight,
        mutable = false,
    )

    fun loadThumbnailBitmap(project: RouteProject): Bitmap {
        val size = thumbnailDecodeSize(project.sourceWidth, project.sourceHeight)
        return decodeBitmap(
            project = project,
            width = size.width,
            height = size.height,
            mutable = true,
        )
    }

    fun loadMutableSourceBitmap(project: RouteProject): Bitmap = decodeBitmap(
        project = project,
        width = project.sourceWidth,
        height = project.sourceHeight,
        mutable = true,
    )

    fun loadCropBitmap(project: RouteProject, bounds: SourceRect): DecodedCrop {
        val plan = cropDecodePlan(project.sourceWidth, project.sourceHeight, bounds)
        val source = File(projectDirectory(project.id), project.sourceFileName)
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, _, _ ->
            decoder.setTargetSize(plan.targetWidth, plan.targetHeight)
            decoder.setCrop(Rect(plan.cropLeft, plan.cropTop, plan.cropRight, plan.cropBottom))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        return DecodedCrop(bitmap, plan.effectiveSourceBounds)
    }

    private fun decodeBitmap(project: RouteProject, width: Int, height: Int, mutable: Boolean): Bitmap {
        val source = File(projectDirectory(project.id), project.sourceFileName)
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, _, _ ->
            decoder.setTargetSize(width, height)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = mutable
        }
    }

    private fun readProject(file: File): RouteProject {
        val json = JSONObject(file.readText())
        require(json.getInt("schemaVersion") == SCHEMA_VERSION) { "Unsupported project format" }
        val sourceWidth = json.getInt("sourceWidth")
        val sourceHeight = json.getInt("sourceHeight")
        val workingWidth = json.getInt("workingWidth")
        val workingHeight = json.getInt("workingHeight")
        val holdsJson = json.getJSONArray("holds")
        val holds = buildList {
            for (index in 0 until holdsJson.length()) {
                val hold = holdsJson.getJSONObject(index)
                val maskJson = hold.getJSONObject("mask")
                val maskWidth = maskJson.getInt("width")
                val maskHeight = maskJson.getInt("height")
                require(maskWidth > 0 && maskHeight > 0)
                val boundsJson = maskJson.getJSONObject("sourceBounds")
                val bounds = SourceRect(
                    left = boundsJson.getDouble("left").toFloat(),
                    top = boundsJson.getDouble("top").toFloat(),
                    right = boundsJson.getDouble("right").toFloat(),
                    bottom = boundsJson.getDouble("bottom").toFloat(),
                )
                require(bounds.left >= 0f && bounds.top >= 0f)
                require(bounds.right <= sourceWidth && bounds.bottom <= sourceHeight)
                val runsJson = maskJson.getJSONArray("runs")
                val runs = IntArray(runsJson.length()) { runsJson.getInt(it) }
                add(
                    RouteHold(
                        id = hold.getString("id"),
                        role = HoldRole.valueOf(hold.getString("role")),
                        maskRegion = MaskRegion(BinaryMask.fromRuns(maskWidth, maskHeight, runs), bounds),
                    ),
                )
            }
        }
        return RouteProject(
            id = json.getString("id"),
            name = if (json.isNull("name")) null else json.getString("name"),
            sourceFileName = json.getString("sourceFileName"),
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            workingWidth = workingWidth,
            workingHeight = workingHeight,
            holds = holds,
            updatedAtEpochMillis = json.getLong("updatedAtEpochMillis"),
        )
    }

    private fun readSummary(file: File): ProjectSummary {
        val json = JSONObject(file.readText())
        val holds = json.getJSONArray("holds")
        var hasStart = false
        var hasFinish = false
        for (index in 0 until holds.length()) {
            when (holds.getJSONObject(index).getString("role")) {
                HoldRole.START.name -> hasStart = true
                HoldRole.FINISH.name -> hasFinish = true
            }
        }
        return ProjectSummary(
            id = json.getString("id"),
            name = if (json.isNull("name")) null else json.getString("name"),
            updatedAtEpochMillis = json.getLong("updatedAtEpochMillis"),
            holdCount = holds.length(),
            isComplete = hasStart && hasFinish,
        )
    }

    private fun RouteProject.toJson(): JSONObject = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("id", id)
        put("name", name ?: JSONObject.NULL)
        put("sourceFileName", sourceFileName)
        put("sourceWidth", sourceWidth)
        put("sourceHeight", sourceHeight)
        put("workingWidth", workingWidth)
        put("workingHeight", workingHeight)
        put("updatedAtEpochMillis", updatedAtEpochMillis)
        put(
            "holds",
            JSONArray().apply {
                holds.forEach { hold ->
                    put(
                        JSONObject().apply {
                            put("id", hold.id)
                            put("role", hold.role.name)
                            put(
                                "mask",
                                JSONObject().apply {
                                    put("width", hold.maskRegion.mask.width)
                                    put("height", hold.maskRegion.mask.height)
                                    put(
                                        "sourceBounds",
                                        JSONObject().apply {
                                            put("left", hold.maskRegion.sourceBounds.left)
                                            put("top", hold.maskRegion.sourceBounds.top)
                                            put("right", hold.maskRegion.sourceBounds.right)
                                            put("bottom", hold.maskRegion.sourceBounds.bottom)
                                        },
                                    )
                                    put("runs", JSONArray(hold.maskRegion.mask.toRuns().toList()))
                                },
                            )
                        },
                    )
                }
            },
        )
    }

    private fun projectDirectory(id: String) = File(projectsDirectory, id)

    companion object {
        private const val PROJECT_FILE = "project.json"
        private const val SOURCE_FILE = "source"
        private const val WORKING_LONG_EDGE = 1024
        private const val SCHEMA_VERSION = 2
    }
}

internal data class CropDecodePlan(
    val targetWidth: Int,
    val targetHeight: Int,
    val cropLeft: Int,
    val cropTop: Int,
    val cropRight: Int,
    val cropBottom: Int,
    val effectiveSourceBounds: SourceRect,
)

data class DecodedCrop(val bitmap: Bitmap, val sourceBounds: SourceRect)

internal fun cropDecodePlan(sourceWidth: Int, sourceHeight: Int, bounds: SourceRect): CropDecodePlan {
    require(bounds.left >= 0f && bounds.top >= 0f)
    require(bounds.right <= sourceWidth && bounds.bottom <= sourceHeight)
    val scale = minOf(1f, 1024f / maxOf(bounds.width, bounds.height))
    val targetWidth = (sourceWidth * scale).toInt().coerceAtLeast(1)
    val targetHeight = (sourceHeight * scale).toInt().coerceAtLeast(1)
    val scaleX = targetWidth.toFloat() / sourceWidth
    val scaleY = targetHeight.toFloat() / sourceHeight
    val cropLeft = (bounds.left * scaleX).toInt().coerceIn(0, targetWidth - 1)
    val cropTop = (bounds.top * scaleY).toInt().coerceIn(0, targetHeight - 1)
    val cropRight = (bounds.right * scaleX).toInt().coerceIn(cropLeft + 1, targetWidth)
    val cropBottom = (bounds.bottom * scaleY).toInt().coerceIn(cropTop + 1, targetHeight)
    return CropDecodePlan(
        targetWidth = targetWidth,
        targetHeight = targetHeight,
        cropLeft = cropLeft,
        cropTop = cropTop,
        cropRight = cropRight,
        cropBottom = cropBottom,
        effectiveSourceBounds = SourceRect(
            left = cropLeft / scaleX,
            top = cropTop / scaleY,
            right = cropRight / scaleX,
            bottom = cropBottom / scaleY,
        ),
    )
}
