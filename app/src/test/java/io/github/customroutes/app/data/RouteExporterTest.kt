package io.github.customroutes.app.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RouteExporterTest {
    @Test
    fun copiesCandidateBytesWithoutChangingThem() {
        val source = ByteArray(600_000) { (it % 251).toByte() }
        val output = ByteArrayOutputStream()

        val copied = copyExportBytes(ByteArrayInputStream(source), output)

        assertEquals(source.size.toLong(), copied)
        assertArrayEquals(source, output.toByteArray())
    }

    @Test
    fun stopsCandidateCopyWhenCancelled() {
        val source = ByteArray(600_000) { (it % 251).toByte() }
        val output = ByteArrayOutputStream()
        var checks = 0

        assertThrows(CancellationException::class.java) {
            copyExportBytes(ByteArrayInputStream(source), output) {
                checks++
                if (checks == 2) throw CancellationException("cancelled")
            }
        }

        assertEquals(256 * 1024, output.size())
    }
}
