package io.github.customroutes.app.ml

import java.net.URL
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelDownloadUrlTest {
    @Test
    fun allowsPinnedSourceAndApprovedCdnHosts() {
        assertTrue(isAllowedModelDownloadUrl(URL("https://huggingface.co/model.onnx")))
        assertTrue(isAllowedModelDownloadUrl(URL("https://us.aws.cdn.hf.co/model.onnx?signature=abc")))
        assertTrue(isAllowedModelDownloadUrl(URL("https://cdn-lfs.huggingface.co/model.onnx")))
    }

    @Test
    fun rejectsCleartextCredentialsPortsAndUnapprovedHosts() {
        assertFalse(isAllowedModelDownloadUrl(URL("http://huggingface.co/model.onnx")))
        assertFalse(isAllowedModelDownloadUrl(URL("https://user@huggingface.co/model.onnx")))
        assertFalse(isAllowedModelDownloadUrl(URL("https://huggingface.co:8443/model.onnx")))
        assertFalse(isAllowedModelDownloadUrl(URL("https://huggingface.co.example.com/model.onnx")))
        assertFalse(isAllowedModelDownloadUrl(URL("https://cdn.hf.co.example.com/model.onnx")))
        assertFalse(isAllowedModelDownloadUrl(URL("https://cas-bridge.xethub.hf.co/model.onnx")))
    }
}
