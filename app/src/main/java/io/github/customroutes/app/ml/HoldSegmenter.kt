package io.github.customroutes.app.ml

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import io.github.customroutes.app.domain.BinaryMask
import java.io.Closeable
import java.nio.FloatBuffer
import java.nio.LongBuffer

data class SegmentationPrompt(val x: Float, val y: Float, val positive: Boolean)

interface HoldSegmenter : Closeable {
    fun prepare(slot: EmbeddingSlot, bitmap: Bitmap, cancellation: PreparationCancellation? = null)
    fun segment(slot: EmbeddingSlot, prompts: List<SegmentationPrompt>): BinaryMask
    fun release(slot: EmbeddingSlot)
}

enum class EmbeddingSlot { FULL_IMAGE, CROP }

class EfficientSamSegmenter(modelFiles: ModelFiles) : HoldSegmenter {
    private val environment = OrtEnvironment.getEnvironment().apply { setTelemetry(false) }
    private val encoder: OrtSession
    private val decoder: OrtSession
    private val prepared = mutableMapOf<EmbeddingSlot, PreparedImage>()

    init {
        val startedAt = SystemClock.elapsedRealtime()
        encoder = createSession(modelFiles.encoder.absolutePath)
        decoder = createSession(modelFiles.decoder.absolutePath)
        Log.i(TAG, "model_load_ms=${SystemClock.elapsedRealtime() - startedAt}")
    }

    override fun prepare(slot: EmbeddingSlot, bitmap: Bitmap, cancellation: PreparationCancellation?) {
        val startedAt = SystemClock.elapsedRealtime()
        OrtSession.RunOptions().use { runOptions ->
            cancellation?.attach { runCatching { runOptions.setTerminate(true) } }
            try {
                val input = bitmap.toChwRgb()
                OnnxTensor.createTensor(
                    environment,
                    FloatBuffer.wrap(input),
                    longArrayOf(1, 3, bitmap.height.toLong(), bitmap.width.toLong()),
                ).use { tensor ->
                    encoder.run(mapOf("batched_images" to tensor), runOptions).use { result ->
                        val embedding = result.get("image_embeddings").orElseThrow() as OnnxTensor
                        val shape = embedding.info.shape
                        val values = FloatArray(shape.fold(1L, Long::times).toInt())
                        embedding.floatBuffer.get(values)
                        prepared[slot] = PreparedImage(bitmap.width, bitmap.height, values, shape)
                    }
                }
            } finally {
                cancellation?.detach()
            }
        }
        Log.i(
            TAG,
            "encode_ms=${SystemClock.elapsedRealtime() - startedAt} heap_bytes=${Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()}",
        )
    }

    override fun segment(slot: EmbeddingSlot, prompts: List<SegmentationPrompt>): BinaryMask {
        val startedAt = SystemClock.elapsedRealtime()
        require(prompts.isNotEmpty())
        val image = checkNotNull(prepared[slot]) { "An image must be prepared before segmentation" }
        val pointCoordinates = FloatArray(prompts.size * 2)
        val pointLabels = FloatArray(prompts.size)
        prompts.forEachIndexed { index, prompt ->
            pointCoordinates[index * 2] = prompt.x
            pointCoordinates[index * 2 + 1] = prompt.y
            pointLabels[index] = if (prompt.positive) 1f else 0f
        }

        OnnxTensor.createTensor(environment, FloatBuffer.wrap(image.embedding), image.embeddingShape).use { embedding ->
            OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(pointCoordinates),
                longArrayOf(1, 1, prompts.size.toLong(), 2),
            ).use { points ->
                OnnxTensor.createTensor(
                    environment,
                    FloatBuffer.wrap(pointLabels),
                    longArrayOf(1, 1, prompts.size.toLong()),
                ).use { labels ->
                    OnnxTensor.createTensor(
                        environment,
                        LongBuffer.wrap(longArrayOf(image.height.toLong(), image.width.toLong())),
                        longArrayOf(2),
                    ).use { originalSize ->
                        decoder.run(
                            mapOf(
                                "image_embeddings" to embedding,
                                "batched_point_coords" to points,
                                "batched_point_labels" to labels,
                                "orig_im_size" to originalSize,
                            ),
                        ).use { result ->
                            val masks = result.get("output_masks").orElseThrow() as OnnxTensor
                            val ious = result.get("iou_predictions").orElseThrow() as OnnxTensor
                            val maskValues = FloatArray(masks.info.shape.fold(1L, Long::times).toInt())
                            val iouValues = FloatArray(ious.info.shape.fold(1L, Long::times).toInt())
                            masks.floatBuffer.get(maskValues)
                            ious.floatBuffer.get(iouValues)
                            return selectBestMask(image.width, image.height, maskValues, iouValues).also {
                                Log.i(TAG, "prompt_ms=${SystemClock.elapsedRealtime() - startedAt} points=${prompts.size}")
                            }
                        }
                    }
                }
            }
        }
    }

    override fun release(slot: EmbeddingSlot) {
        prepared.remove(slot)
    }

    override fun close() {
        prepared.clear()
        decoder.close()
        encoder.close()
    }

    private fun sessionOptions() = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(maxOf(1, Runtime.getRuntime().availableProcessors() - 1))
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
    }

    private fun createSession(path: String): OrtSession = sessionOptions().use { options ->
        environment.createSession(path, options)
    }

    private fun Bitmap.toChwRgb(): FloatArray {
        val argb = IntArray(width * height)
        getPixels(argb, 0, width, 0, 0, width, height)
        val planeSize = width * height
        return FloatArray(planeSize * 3).also { output ->
            argb.forEachIndexed { index, color ->
                output[index] = ((color shr 16) and 0xff) / 255f
                output[planeSize + index] = ((color shr 8) and 0xff) / 255f
                output[planeSize * 2 + index] = (color and 0xff) / 255f
            }
        }
    }

    private data class PreparedImage(
        val width: Int,
        val height: Int,
        val embedding: FloatArray,
        val embeddingShape: LongArray,
    )

    companion object {
        private const val TAG = "EfficientSAM"
    }
}

internal fun selectBestMask(
    width: Int,
    height: Int,
    maskLogits: FloatArray,
    predictedIous: FloatArray,
): BinaryMask {
    require(width > 0 && height > 0)
    require(predictedIous.isNotEmpty())
    val pixelsPerMask = width * height
    require(maskLogits.size == pixelsPerMask * predictedIous.size)
    val bestMask = predictedIous.indices.maxBy { predictedIous[it] }
    val offset = bestMask * pixelsPerMask
    return BinaryMask.fromBooleans(
        width,
        height,
        BooleanArray(pixelsPerMask) { maskLogits[offset + it] >= 0f },
    )
}
