package com.typeassist.app.api

import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * ============================== MODEL DOWNLOADER ==============================
 *
 * Downloads a GGUF model into the app's private storage, so using the on-device AI does not
 * involve a file manager and a USB cable. One URL in, one file out, progress reported as it goes.
 *
 * The file is written under a `.part` name and renamed only when the download is complete, so a
 * cancelled or failed download can never be mistaken for a working model. The caller decides
 * where the file belongs; the local-model screen points this at the same cache file the
 * picked-model flow uses, so both paths meet in the same place.
 *
 * No Android imports: OkHttp on a plain thread, so the download rules are unit tested against
 * MockWebServer the same way the API clients are.
 */
class ModelDownloader(private val client: OkHttpClient) {

    @Volatile private var inFlight: Call? = null

    /** True when the download was asked to stop. A new download clears it again. */
    @Volatile var cancelled: Boolean = false
        private set

    /** True when the URL looks like a direct GGUF download; a warning is shown otherwise. */
    fun looksLikeModelUrl(url: String): Boolean {
        val clean = url.substringBefore('?').lowercase()
        return clean.endsWith(".gguf")
    }

    /** Stops the download in flight, if any. */
    fun cancel() {
        cancelled = true
        try {
            inFlight?.cancel()
        } catch (e: Exception) {
            // Already finished; nothing to cancel.
        }
    }

    /**
     * Streams the model at [url] into [destination].
     *
     * [onProgress] receives the bytes downloaded so far and the total size when the server
     * reported one (a negative total means "unknown size"). Exactly one of [onFinished] /
     * [onProgress]-then-[onFinished] ends every run; a cancelled download ends silently.
     */
    fun download(
        url: String,
        destination: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit = { _, _ -> },
        onFinished: (Result<File>) -> Unit
    ) {
        cancelled = false
        val request = try {
            Request.Builder().url(url.trim()).build()
        } catch (e: Exception) {
            onFinished(Result.failure(IllegalArgumentException("That does not look like a web address.")))
            return
        }

        val tempFile = File(destination.parentFile ?: File("."), destination.name + ".part")
        val downloader = client.newBuilder()
            .connectTimeout(20L, TimeUnit.SECONDS)
            .readTimeout(60L, TimeUnit.SECONDS)
            .build()
        val call = downloader.newCall(request)
        inFlight = call

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                tempFile.delete()
                if (!call.isCanceled()) onFinished(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        tempFile.delete()
                        onFinished(
                            Result.failure(
                                IOException("The download failed (HTTP ${resp.code}). Check the link and try again.")
                            )
                        )
                        return
                    }
                    val source = resp.body?.byteStream()
                    if (source == null) {
                        tempFile.delete()
                        onFinished(Result.failure(IOException("Empty response body")))
                        return
                    }
                    val total = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                    var downloaded = 0L
                    var lastReported = 0L
                    try {
                        tempFile.parentFile?.mkdirs()
                        source.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                val buffer = ByteArray(BUFFER_SIZE)
                                while (true) {
                                    if (cancelled) {
                                        tempFile.delete()
                                        return
                                    }
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    output.write(buffer, 0, read)
                                    downloaded += read
                                    if (downloaded - lastReported >= PROGRESS_STEP || downloaded == total) {
                                        lastReported = downloaded
                                        onProgress(downloaded, total)
                                    }
                                }
                            }
                        }
                        // A completed model replaces whatever was there before.
                        destination.delete()
                        if (tempFile.renameTo(destination)) {
                            onFinished(Result.success(destination))
                        } else {
                            tempFile.delete()
                            onFinished(Result.failure(IOException("Could not save the model file.")))
                        }
                    } catch (e: Exception) {
                        tempFile.delete()
                        if (!call.isCanceled()) {
                            onFinished(Result.failure(IOException("The download failed: ${e.message}")))
                        }
                    }
                }
            }
        })
    }

    companion object {
        private const val BUFFER_SIZE = 64 * 1024
        /** Progress is reported in half-megabyte steps, so the UI is not updated millions of times. */
        private const val PROGRESS_STEP = 512 * 1024L
    }
}
