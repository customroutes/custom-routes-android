package io.github.customroutes.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import io.github.customroutes.app.domain.AppearanceSettings
import io.github.customroutes.app.domain.HoldRole
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlin.math.roundToInt
import org.json.JSONObject

const val THUMBNAIL_LONG_EDGE = 256
const val THUMBNAIL_DIMMING_PERCENT = 60
const val THUMBNAIL_RENDERING_SCHEMA_VERSION = 1

data class ThumbnailSignature(
    val updatedAtEpochMillis: Long,
    val roleColors: Map<HoldRole, Int>,
    val borderWidthPercent: Int,
    val adjustSmallHolds: Boolean,
    val renderingSchemaVersion: Int = THUMBNAIL_RENDERING_SCHEMA_VERSION,
) {
    internal fun toJson(): JSONObject = JSONObject().apply {
        put("updatedAtEpochMillis", updatedAtEpochMillis)
        put("borderWidthPercent", borderWidthPercent)
        put("adjustSmallHolds", adjustSmallHolds)
        put("renderingSchemaVersion", renderingSchemaVersion)
        put(
            "roleColors",
            JSONObject().apply {
                HoldRole.entries.forEach { role ->
                    put(role.name, roleColors[role] ?: role.argb)
                }
            },
        )
    }

    companion object {
        fun forProject(
            project: ProjectSummary,
            roleColors: Map<HoldRole, Int>,
            appearanceSettings: AppearanceSettings,
        ): ThumbnailSignature = ThumbnailSignature(
            updatedAtEpochMillis = project.updatedAtEpochMillis,
            roleColors = HoldRole.entries.associateWith { role -> roleColors[role] ?: role.argb },
            borderWidthPercent = appearanceSettings.borderWidthPercent,
            adjustSmallHolds = appearanceSettings.adjustSmallHolds,
        )

        internal fun fromJson(json: JSONObject): ThumbnailSignature {
            val colors = json.getJSONObject("roleColors")
            return ThumbnailSignature(
                updatedAtEpochMillis = json.getLong("updatedAtEpochMillis"),
                roleColors = HoldRole.entries.associateWith { role -> colors.getInt(role.name) },
                borderWidthPercent = json.getInt("borderWidthPercent"),
                adjustSmallHolds = json.getBoolean("adjustSmallHolds"),
                renderingSchemaVersion = json.getInt("renderingSchemaVersion"),
            )
        }
    }
}

data class ProjectThumbnailUiState(
    val signature: ThumbnailSignature,
    val bitmap: Bitmap? = null,
    val bitmapSignature: ThumbnailSignature? = null,
)

internal data class CachedProjectThumbnail(
    val signature: ThumbnailSignature,
    val bitmap: Bitmap,
)

internal data class ThumbnailDecodeSize(val width: Int, val height: Int)

internal fun thumbnailDecodeSize(sourceWidth: Int, sourceHeight: Int): ThumbnailDecodeSize {
    require(sourceWidth > 0 && sourceHeight > 0)
    val scale = minOf(1f, THUMBNAIL_LONG_EDGE.toFloat() / maxOf(sourceWidth, sourceHeight))
    return ThumbnailDecodeSize(
        width = (sourceWidth * scale).roundToInt().coerceAtLeast(1),
        height = (sourceHeight * scale).roundToInt().coerceAtLeast(1),
    )
}

internal class ProjectThumbnailCache(context: Context) {
    private val directory = File(context.cacheDir, CACHE_DIRECTORY)

    fun read(projectId: String, expectedSize: ThumbnailDecodeSize? = null): CachedProjectThumbnail? {
        val entryName = runCatching { pointerFile(projectId).readText().trim() }.getOrNull() ?: return null
        val prefix = projectPrefix(projectId)
        if (!entryName.startsWith("$prefix--") || entryName.contains('/') || entryName.contains('\\')) return null
        val entryDirectory = File(directory, entryName)
        val imageFile = File(entryDirectory, IMAGE_FILE)
        val signatureFile = File(entryDirectory, SIGNATURE_FILE)
        if (!imageFile.isFile || !signatureFile.isFile) return null

        val signature = runCatching { ThumbnailSignature.fromJson(JSONObject(signatureFile.readText())) }
            .getOrNull()
            ?: return null
        val bitmap = runCatching { decode(imageFile, expectedSize) }.getOrNull() ?: return null
        if (bitmap.width <= 0 || bitmap.height <= 0) {
            bitmap.recycle()
            return null
        }
        return CachedProjectThumbnail(signature, bitmap)
    }

    fun write(projectId: String, signature: ThumbnailSignature, bitmap: Bitmap) {
        check(directory.mkdirs() || directory.isDirectory) { "Thumbnail cache could not be created" }
        val prefix = projectPrefix(projectId)
        val entryName = "$prefix--${UUID.randomUUID()}"
        val entryDirectory = File(directory, entryName)
        check(entryDirectory.mkdirs()) { "Thumbnail cache entry could not be created" }
        val imageFile = File(entryDirectory, IMAGE_FILE)
        val signatureFile = File(entryDirectory, SIGNATURE_FILE)
        val pointer = pointerFile(projectId)
        val pointerTemporary = File(directory, ".${pointer.name}.${UUID.randomUUID()}.tmp")
        var published = false
        try {
            imageFile.outputStream().buffered(64 * 1024).use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, WEBP_QUALITY, stream)) {
                    "Thumbnail could not be encoded"
                }
            }
            signatureFile.writeText(signature.toJson().toString())
            pointerTemporary.writeText(entryName)
            moveReplacing(pointerTemporary, pointer)
            published = true
            directory.listFiles().orEmpty()
                .filter { it.name.startsWith("$prefix--") && it.name != entryName }
                .forEach { it.deleteRecursively() }
        } finally {
            pointerTemporary.delete()
            if (!published) entryDirectory.deleteRecursively()
        }
    }

    fun delete(projectId: String) {
        deleteFile(pointerFile(projectId))
        val prefix = projectPrefix(projectId)
        directory.listFiles().orEmpty()
            .filter { it.name.startsWith("$prefix--") }
            .forEach { entry ->
                check(entry.deleteRecursively()) { "Thumbnail cache entry could not be deleted" }
            }
    }

    fun deleteAll() {
        if (directory.exists()) check(directory.deleteRecursively()) { "Thumbnail cache could not be deleted" }
    }

    private fun decode(file: File, expectedSize: ThumbnailDecodeSize?): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            if (expectedSize == null) {
                require(info.size.width in 1..THUMBNAIL_LONG_EDGE && info.size.height in 1..THUMBNAIL_LONG_EDGE) {
                    "Thumbnail dimensions are invalid"
                }
            } else {
                require(info.size.width == expectedSize.width && info.size.height == expectedSize.height) {
                    "Thumbnail dimensions do not match the source photo"
                }
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }

    private fun pointerFile(projectId: String) = File(directory, "${projectPrefix(projectId)}.current")

    private fun projectPrefix(projectId: String): String = projectId.replace(UNSAFE_FILENAME_CHARACTERS, "_")

    private fun deleteFile(file: File) {
        if (file.exists()) check(file.delete()) { "Thumbnail cache file could not be deleted" }
    }

    private companion object {
        val UNSAFE_FILENAME_CHARACTERS = Regex("[^A-Za-z0-9_-]")
        const val CACHE_DIRECTORY = "project-thumbnails"
        const val IMAGE_FILE = "thumbnail.webp"
        const val SIGNATURE_FILE = "signature.json"
        const val WEBP_QUALITY = 90
    }

    private fun moveReplacing(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (_: UnsupportedOperationException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

}
