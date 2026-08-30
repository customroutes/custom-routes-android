package io.github.customroutes.app.ml

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

data class ModelFiles(val encoder: File, val decoder: File)

sealed interface ModelStatus {
    data object Checking : ModelStatus
    data object Missing : ModelStatus
    data class Downloading(val fraction: Float) : ModelStatus
    data object Ready : ModelStatus
    data class Failed(val message: String) : ModelStatus
}

class ModelManager(context: Context) {
    private val directory = File(context.filesDir, "models/$MODEL_VERSION")
    private var installedFiles: ModelFiles? = null
    private var statusChecked = false
    private val connectionLock = Any()
    @Volatile private var activeConnection: HttpURLConnection? = null

    fun status(): ModelStatus {
        if (!statusChecked) {
            installedFiles = verifiedFiles()
            statusChecked = true
        }
        return if (installedFiles != null) ModelStatus.Ready else ModelStatus.Missing
    }

    fun files(): ModelFiles = checkNotNull(installedFiles) { "Segmentation model is not installed" }

    fun hasStoredData(): Boolean = directory.listFiles().orEmpty().isNotEmpty()

    fun download(onProgress: (Float) -> Unit, checkCancelled: () -> Unit = {}): ModelFiles {
        directory.mkdirs()
        var completedBytes = 0L
        val totalBytes = artifacts.sumOf { it.size }
        try {
            artifacts.forEach { artifact ->
                checkCancelled()
                val target = File(directory, artifact.fileName)
                if (target.exists() && target.sha256(checkCancelled) == artifact.sha256) {
                    completedBytes += artifact.size
                    onProgress(completedBytes.toFloat() / totalBytes)
                    return@forEach
                }
                val temporary = File(directory, "${artifact.fileName}.download")
                temporary.delete()
                checkCancelled()
                val connection = openModelConnection(artifact.url, checkCancelled)
                try {
                    connection.inputStream.use { input ->
                        temporary.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var artifactBytes = 0L
                            while (true) {
                                checkCancelled()
                                val count = input.read(buffer)
                                if (count < 0) break
                                artifactBytes += count
                                check(artifactBytes <= artifact.size) { "Downloaded model exceeds its declared size" }
                                output.write(buffer, 0, count)
                                onProgress((completedBytes + artifactBytes).toFloat() / totalBytes)
                            }
                        }
                    }
                } finally {
                    if (activeConnection === connection) activeConnection = null
                    connection.disconnect()
                }
                check(temporary.length() == artifact.size) { "Downloaded model has an unexpected size" }
                check(temporary.sha256(checkCancelled) == artifact.sha256) { "Downloaded model failed verification" }
                checkCancelled()
                target.delete()
                check(temporary.renameTo(target)) { "Downloaded model could not be installed" }
                completedBytes += artifact.size
            }
            return checkNotNull(verifiedFiles(checkCancelled)) { "Installed model could not be verified" }
                .also {
                    installedFiles = it
                    statusChecked = true
                }
        } catch (error: Throwable) {
            directory.listFiles { file -> file.extension == "download" }
                .orEmpty()
                .forEach(File::delete)
            throw error
        }
    }

    fun cancelDownload() {
        synchronized(connectionLock) { activeConnection?.disconnect() }
    }

    fun delete() {
        cancelDownload()
        if (directory.exists()) check(directory.deleteRecursively()) { "AI model could not be deleted" }
        installedFiles = null
        statusChecked = true
    }

    private fun openModelConnection(source: String, checkCancelled: () -> Unit): HttpURLConnection {
        var url = URL(source)
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            checkCancelled()
            check(isAllowedModelDownloadUrl(url)) { "Model download redirected to an unapproved host" }
            val connection = synchronized(connectionLock) {
                checkCancelled()
                (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20_000
                    readTimeout = 60_000
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                    activeConnection = this
                }
            }
            val responseCode = try {
                connection.connect()
                checkCancelled()
                connection.responseCode
            } catch (error: Throwable) {
                connection.disconnect()
                if (activeConnection === connection) activeConnection = null
                throw error
            }
            if (responseCode in REDIRECT_CODES) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (activeConnection === connection) activeConnection = null
                check(redirectCount < MAX_REDIRECTS) { "Model download exceeded the redirect limit" }
                check(!location.isNullOrBlank()) { "Model server returned an empty redirect" }
                url = URL(url, location)
            } else {
                if (responseCode !in 200..299) {
                    connection.disconnect()
                    if (activeConnection === connection) activeConnection = null
                    error("Model server returned HTTP $responseCode")
                }
                return connection
            }
        }
        error("Model download exceeded the redirect limit")
    }

    private fun verifiedFiles(checkCancelled: () -> Unit = {}): ModelFiles? {
        val encoder = File(directory, ENCODER.fileName)
        val decoder = File(directory, DECODER.fileName)
        if (!encoder.exists() || encoder.length() != ENCODER.size || encoder.sha256(checkCancelled) != ENCODER.sha256) {
            return null
        }
        if (!decoder.exists() || decoder.length() != DECODER.size || decoder.sha256(checkCancelled) != DECODER.sha256) {
            return null
        }
        return ModelFiles(encoder, decoder)
    }

    private fun File.sha256(checkCancelled: () -> Unit = {}): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                checkCancelled()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private data class Artifact(
        val fileName: String,
        val url: String,
        val size: Long,
        val sha256: String,
    )

    companion object {
        const val MODEL_VERSION = "efficient-sam-ti-2024-01"
        private const val MAX_REDIRECTS = 5
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        private val ENCODER = Artifact(
            fileName = "encoder.onnx",
            url = "https://huggingface.co/spaces/yunyangx/EfficientSAM/resolve/main/efficientsam_ti_encoder.onnx",
            size = 24_799_761,
            sha256 = "84ed466ffcc5c1f8d08409bc34a23bb364ab2c15e402cb12d4335a42be0e0951",
        )
        private val DECODER = Artifact(
            fileName = "decoder.onnx",
            url = "https://huggingface.co/spaces/yunyangx/EfficientSAM/resolve/main/efficientsam_ti_decoder.onnx",
            size = 16_565_728,
            sha256 = "a62f8fa5ea080447c0689418d69e58f1e83e0b7adf9c142e2bd9bcc8045c0b11",
        )
        private val artifacts = listOf(ENCODER, DECODER)
    }
}

internal fun isAllowedModelDownloadUrl(url: URL): Boolean {
    val host = url.host.lowercase(Locale.US)
    val approvedHost = host == "huggingface.co" ||
        host == "cdn-lfs.huggingface.co" ||
        host.endsWith(".cdn.hf.co")
    return url.protocol.equals("https", ignoreCase = true) &&
        (url.port == -1 || url.port == 443) &&
        url.userInfo == null &&
        approvedHost
}
