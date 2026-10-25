package com.typeassist.app.api

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The download rules for in-app GGUF models, checked against a fake server: the bytes arrive
 * whole, a failed download leaves nothing behind that could be mistaken for a model, and the
 * `.part` temp file never survives.
 */
class ModelDownloaderTest {

    private lateinit var server: MockWebServer

    /** 1 KiB of pseudo-random bytes: small enough to arrive in one chunk, real enough to check. */
    private val payload: ByteArray = ByteArray(1024) { (it % 251).toByte() }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun tempFile(): File {
        val dir = java.nio.file.Files.createTempDirectory("model-download").toFile()
        return File(dir, "model.gguf")
    }

    @Test
    fun aDownloadedModelIsSavedWholeAndRenamed() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(okio.Buffer().write(payload))
        )

        val target = tempFile()
        val downloader = ModelDownloader(OkHttpClient())
        var finished: Result<File>? = null
        val latch = CountDownLatch(1)

        downloader.download(
            url = server.url("/model.gguf").toString(),
            destination = target,
            onFinished = { result ->
                finished = result
                latch.countDown()
            }
        )

        assertTrue("Download never finished", latch.await(30, TimeUnit.SECONDS))
        val file = finished!!.getOrThrow()
        assertEquals(target, file)
        assertTrue(file.exists())
        assertEquals(payload.size.toLong(), file.length())
        assertFalse(
            "No half-downloaded file may be left behind",
            File(target.parentFile, "model.gguf.part").exists()
        )
    }

    @Test
    fun progressIsReportedAsBytesArrive() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(okio.Buffer().write(payload))
        )

        val downloader = ModelDownloader(OkHttpClient())
        var finished: Result<File>? = null
        val latch = CountDownLatch(1)
        var lastDownloaded = 0L
        var lastTotal = -2L

        downloader.download(
            url = server.url("/model.gguf").toString(),
            destination = tempFile(),
            onProgress = { done, total ->
                lastDownloaded = done
                lastTotal = total
            },
            onFinished = { result ->
                finished = result
                latch.countDown()
            }
        )

        assertTrue("Download never finished", latch.await(30, TimeUnit.SECONDS))
        assertTrue(finished!!.isSuccess)
        assertEquals(payload.size.toLong(), lastDownloaded)
        assertEquals(payload.size.toLong(), lastTotal)
    }

    @Test
    fun anHttpErrorIsReportedAndLeavesNoFile() {
        server.enqueue(MockResponse().setResponseCode(404))

        val target = tempFile()
        val downloader = ModelDownloader(OkHttpClient())
        val latch = CountDownLatch(1)
        var failure: Throwable? = null

        downloader.download(
            url = server.url("/missing.gguf").toString(),
            destination = target,
            onFinished = { result ->
                failure = result.exceptionOrNull()
                latch.countDown()
            }
        )

        assertTrue("Download never finished", latch.await(30, TimeUnit.SECONDS))
        assertTrue("Expected a failure", failure is Throwable)
        assertTrue((failure as Exception).message!!.contains("404"))
        assertFalse(target.exists())
        assertFalse(File(target.parentFile, "model.gguf.part").exists())
    }

    @Test
    fun aBadUrlIsRejectedBeforeAnyRequest() {
        val target = tempFile()
        val latch = CountDownLatch(1)
        var failure: Throwable? = null

        ModelDownloader(OkHttpClient()).download(
            url = "not a url at all",
            destination = target,
            onFinished = { result ->
                failure = result.exceptionOrNull()
                latch.countDown()
            }
        )

        assertTrue("Download never finished", latch.await(30, TimeUnit.SECONDS))
        assertTrue((failure as? IllegalArgumentException)?.message?.contains("web address") == true)
        assertFalse(target.exists())
    }

    @Test
    fun aNonGgufLinkIsRecognised() {
        val downloader = ModelDownloader(OkHttpClient())

        assertTrue(downloader.looksLikeModelUrl("https://huggingface.co/x/resolve/main/model.gguf"))
        assertTrue(downloader.looksLikeModelUrl("https://h/x/file.gguf?download=true"))
        assertFalse(downloader.looksLikeModelUrl("https://example.com/readme.txt"))
    }
}
