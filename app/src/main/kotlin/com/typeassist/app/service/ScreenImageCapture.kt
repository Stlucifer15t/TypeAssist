package com.typeassist.app.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.util.Base64
import android.view.Display
import com.typeassist.app.api.PromptImage
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Grabs a single screenshot of the current screen for `.reply`/`.sum`, so the model can read an
 * image a contact sent — something the accessibility text reader cannot see.
 *
 * `AccessibilityService.takeScreenshot` only exists from Android 11 (API 30); on older devices the
 * feature reports "not available" and the command falls back to the text-only path. The capture is
 * downscaled and JPEG-compressed so the request stays small.
 */
object ScreenImageCapture {

    /** Longest edge of the image sent. Enough to read chat text, far cheaper than a full screen. */
    const val MAX_DIMENSION = 1024

    const val JPEG_QUALITY = 80
    const val MIME_TYPE = "image/jpeg"

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /**
     * Captures the screen and hands back a [PromptImage], or null when the platform is too old or
     * the capture failed. The result is delivered on the main thread.
     */
    fun capture(service: AccessibilityService, onResult: (PromptImage?) -> Unit) {
        if (!isSupported()) {
            onResult(null)
            return
        }
        try {
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        onResult(result.toPromptImage())
                    }

                    override fun onFailure(errorCode: Int) {
                        onResult(null)
                    }
                }
            )
        } catch (e: Exception) {
            onResult(null)
        }
    }

    private fun AccessibilityService.ScreenshotResult.toPromptImage(): PromptImage? {
        val buffer = hardwareBuffer
        return try {
            // The hardware buffer is read-only; copy it into a software bitmap we can compress.
            val hardwareBitmap = Bitmap.wrapHardwareBuffer(buffer, colorSpace)
            val software = hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)
            hardwareBitmap?.recycle()
            software?.let { encode(it) }
        } catch (e: Exception) {
            null
        } finally {
            try {
                buffer.close()
            } catch (e: Exception) {
                // Closing twice is harmless; nothing useful to do here.
            }
        }
    }

    private fun encode(bitmap: Bitmap): PromptImage? {
        var scaled = bitmap
        return try {
            scaled = scaleDown(bitmap)
            val out = ByteArrayOutputStream()
            val ok = scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            if (!ok) return null
            val bytes = out.toByteArray()
            if (bytes.isEmpty()) return null
            PromptImage(MIME_TYPE, Base64.encodeToString(bytes, Base64.NO_WRAP))
        } catch (e: Exception) {
            null
        } finally {
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
        }
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= MAX_DIMENSION) return bitmap
        val scale = MAX_DIMENSION.toFloat() / largest
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
}
