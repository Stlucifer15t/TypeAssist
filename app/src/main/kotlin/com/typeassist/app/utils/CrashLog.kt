package com.typeassist.app.utils

import android.content.Context
import android.os.Build
import com.typeassist.app.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ============================== LAST CRASH ==============================
 *
 * Keeps the trace of the last crash in the app's own files so it can be read — and copied out —
 * from inside the app. Without this, a crash that happens while somebody is typing is only visible
 * in logcat, which is nowhere the person who hit it can look.
 *
 * The handler only *adds* a step: the trace is written first, then the previous handler runs, so
 * Android still reports and handles the crash exactly as it did before. Writing is best-effort and
 * never allowed to make the crash worse.
 */
object CrashLog {

    private const val FILE_NAME = "last_crash.txt"

    /** Enough for the exception, its cause and the frames that lead to it. */
    private const val MAX_CHARS = 12_000

    /**
     * Called once per process, before anything else, so a crash in the accessibility service (same
     * process, and often started without the app being opened) is recorded too.
     */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                write(appContext, thread, error)
            } catch (_: Throwable) {
                // A crash report that fails to save must not hide the crash itself.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val stack = StringWriter()
        PrintWriter(stack).use { error.printStackTrace(it) }
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val report = buildString {
            append("Prompt AI ")
            append(BuildConfig.VERSION_NAME)
            append(" (")
            append(BuildConfig.VERSION_CODE)
            append(")\n")
            append(stamp)
            append(" · Android ")
            append(Build.VERSION.RELEASE)
            append(" (API ")
            append(Build.VERSION.SDK_INT)
            append(") · ")
            append(Build.MANUFACTURER)
            append(' ')
            append(Build.MODEL)
            append("\nThread: ")
            append(thread.name)
            append("\n\n")
            append(stack.toString())
        }
        File(context.filesDir, FILE_NAME).writeText(report.take(MAX_CHARS))
    }

    /** The report, or null when the app has not crashed since the last one was cleared. */
    fun read(context: Context): String? {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return null
        val text = try {
            file.readText()
        } catch (e: Exception) {
            return null
        }
        return text.ifBlank { null }
    }

    fun clear(context: Context) {
        try {
            File(context.filesDir, FILE_NAME).delete()
        } catch (_: Exception) {
            // Nothing to do: the report is optional.
        }
    }

    /**
     * The one line worth putting on Home: the exception itself, without the frames. Falls back to
     * the first line of the report when the trace carries no exception line.
     */
    fun summary(report: String): String =
        report.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.contains("Exception") || it.contains("Error") }
            ?.take(180)
            ?: report.lineSequence().firstOrNull()?.trim()?.take(180)
            ?: "The app stopped unexpectedly."
}
