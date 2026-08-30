package io.github.customroutes.app.data

import android.graphics.Bitmap
import io.github.customroutes.app.domain.MaskRaster
import java.nio.ByteBuffer

internal fun MaskRaster.toAlphaBitmap(): Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8).also { bitmap ->
    val buffer = ByteBuffer.allocate(bitmap.byteCount)
    repeat(height) { y ->
        buffer.put(alpha, y * width, width)
        repeat(bitmap.rowBytes - width) { buffer.put(0) }
    }
    buffer.rewind()
    bitmap.copyPixelsFromBuffer(buffer)
}
