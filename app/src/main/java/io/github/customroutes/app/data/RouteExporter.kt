package io.github.customroutes.app.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import io.github.customroutes.app.domain.AppearanceSettings
import io.github.customroutes.app.domain.HoldRole
import io.github.customroutes.app.domain.exportDimmingAlpha
import io.github.customroutes.app.domain.RouteProject
import java.io.File
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

data class RouteExportResult(
    val uri: Uri,
    val loadMillis: Long,
    val borderMillis: Long,
    val encodeMillis: Long,
) {
    val totalMillis: Long get() = loadMillis + borderMillis + encodeMillis
}

@ConsistentCopyVisibility
data class RouteExportCandidate internal constructor(
    internal val file: File,
    val displayName: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val loadMillis: Long,
    val borderMillis: Long,
    val encodeMillis: Long,
)

class RouteExporter(
    private val context: Context,
    private val repository: ProjectRepository,
) {
    private val candidateDirectory = File(context.cacheDir, CANDIDATE_DIRECTORY).also { directory ->
        directory.mkdirs()
        directory.listFiles().orEmpty().forEach(File::delete)
    }

    fun generateCandidate(
        project: RouteProject,
        roleColors: Map<HoldRole, Int>,
        appearanceSettings: AppearanceSettings,
        checkCancelled: () -> Unit = {},
    ): RouteExportCandidate {
        val startedAt = SystemClock.elapsedRealtime()
        checkCancelled()
        val output = repository.loadMutableSourceBitmap(project)
        val temporary = File(candidateDirectory, "${UUID.randomUUID()}.part")
        val candidateFile = File(candidateDirectory, "${UUID.randomUUID()}.jpg")
        try {
            checkCancelled()
            val loadedAt = SystemClock.elapsedRealtime()
            drawBorders(output, project, roleColors, appearanceSettings, checkCancelled)
            checkCancelled()
            val borderedAt = SystemClock.elapsedRealtime()
            temporary.outputStream().buffered(256 * 1024).let { stream ->
                CancellationCheckingOutputStream(stream, checkCancelled).use { checked ->
                    check(output.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, checked))
                }
            }
            checkCancelled()
            val encodedAt = SystemClock.elapsedRealtime()
            check(temporary.length() > 0L) { "Export preview JPEG is empty" }
            check(temporary.renameTo(candidateFile)) { "Export preview JPEG could not be finalized" }
            return RouteExportCandidate(
                file = candidateFile,
                displayName = buildFileName(project),
                width = project.sourceWidth,
                height = project.sourceHeight,
                sizeBytes = candidateFile.length(),
                loadMillis = loadedAt - startedAt,
                borderMillis = borderedAt - loadedAt,
                encodeMillis = encodedAt - borderedAt,
            )
        } catch (error: Throwable) {
            temporary.delete()
            candidateFile.delete()
            throw error
        } finally {
            output.recycle()
        }
    }

    fun decodeCandidate(candidate: RouteExportCandidate): Bitmap {
        checkCandidate(candidate)
        var decodedWidth = 0
        var decodedHeight = 0
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(candidate.file)) { decoder, info, _ ->
            check(info.mimeType == "image/jpeg") { "Export preview is not a JPEG" }
            decodedWidth = info.size.width
            decodedHeight = info.size.height
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        check(decodedWidth == candidate.width && decodedHeight == candidate.height) {
            bitmap.recycle()
            "Export preview dimensions changed"
        }
        return bitmap
    }

    fun publish(
        candidate: RouteExportCandidate,
        checkCancelled: () -> Unit = {},
    ): RouteExportResult {
        checkCandidate(candidate)
        checkCancelled()
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, candidate.displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Custom Routes")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)) {
            "Could not create exported image"
        }
        try {
            val copiedBytes = candidate.file.inputStream().buffered(256 * 1024).use { input ->
                checkNotNull(resolver.openOutputStream(uri, "w")).buffered(256 * 1024).use { output ->
                    copyExportBytes(input, output, checkCancelled)
                }
            }
            check(copiedBytes == candidate.sizeBytes) { "Exported JPEG was not copied completely" }
            checkCancelled()
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            check(resolver.update(uri, values, null, null) == 1) { "Exported image could not be published" }
            return RouteExportResult(
                uri = uri,
                loadMillis = candidate.loadMillis,
                borderMillis = candidate.borderMillis,
                encodeMillis = candidate.encodeMillis,
            )
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    fun discard(candidate: RouteExportCandidate) {
        check(!candidate.file.exists() || candidate.file.delete()) { "Export preview could not be deleted" }
    }

    fun isUsable(candidate: RouteExportCandidate): Boolean =
        candidate.file.isFile && candidate.file.length() == candidate.sizeBytes && candidate.sizeBytes > 0L

    private fun drawBorders(
        bitmap: Bitmap,
        project: RouteProject,
        roleColors: Map<HoldRole, Int>,
        appearanceSettings: AppearanceSettings,
        checkCancelled: () -> Unit,
    ) {
        RouteImageRenderer.draw(
            bitmap = bitmap,
            project = project,
            roleColors = roleColors,
            appearanceSettings = appearanceSettings,
            dimmingAlpha = appearanceSettings.exportDimmingAlpha(project.holds.isNotEmpty()),
            checkCancelled = checkCancelled,
        )
    }

    private fun checkCandidate(candidate: RouteExportCandidate) {
        check(isUsable(candidate)) { "Export preview is no longer available" }
    }

    private fun buildFileName(project: RouteProject): String {
        val safeName = project.name
            ?.replace(Regex("[^A-Za-z0-9._-]+"), "_")
            ?.trim('_')
            ?.takeIf { it.isNotBlank() }
            ?: "route"
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        return "${safeName}_$timestamp.jpg"
    }

    private companion object {
        const val CANDIDATE_DIRECTORY = "export-candidates"
        const val JPEG_QUALITY = 95
    }

    private class CancellationCheckingOutputStream(
        output: OutputStream,
        private val checkCancelled: () -> Unit,
    ) : FilterOutputStream(output) {
        override fun write(value: Int) {
            checkCancelled()
            out.write(value)
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            checkCancelled()
            out.write(buffer, offset, length)
        }
    }
}

internal fun copyExportBytes(
    input: InputStream,
    output: OutputStream,
    checkCancelled: () -> Unit = {},
): Long {
    var copied = 0L
    val buffer = ByteArray(256 * 1024)
    while (true) {
        checkCancelled()
        val count = input.read(buffer)
        if (count < 0) return copied
        output.write(buffer, 0, count)
        copied += count
    }
}
