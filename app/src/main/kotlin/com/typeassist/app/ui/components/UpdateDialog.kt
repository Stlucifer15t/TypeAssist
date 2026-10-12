package com.typeassist.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.typeassist.app.data.model.GitHubRelease
import com.typeassist.app.ui.Spacing
import dev.jeziellago.compose.markdowntext.MarkdownText

/**
 * ============================== UPDATES ==============================
 *
 * Two dialogs, one pattern: what changed, and one button that acts on it. Release notes are
 * markdown from GitHub, so they are rendered as such - but inside a capped, scrollable box, so a
 * long changelog can never push the buttons off the screen.
 */

@Composable
fun UpdateDialog(release: GitHubRelease, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AppDialog(
        title = "Version ${release.tagName} is available",
        message = "You are running an older build. Release notes are shown below.",
        onDismiss = onDismiss,
        confirmLabel = "View release",
        onConfirm = {
            onDismiss()
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl)))
        },
        dismissLabel = "Later"
    ) {
        if (release.body.isNotBlank()) {
            ReleaseNotes(release.body)
        }
    }
}

/**
 * Shown once after the app is updated to a build worth explaining. The notes are written for the
 * person using the app, not for a changelog, so they stay short and concrete.
 */
private val announcementNotes = listOf(
    "Text generation settings: temperature, top-p and an answer-length limit are now sent to " +
        "Gemini, OpenAI-compatible endpoints and Cloudflare Workers AI, with Presets if you would " +
        "rather not think about numbers.",
    "Requests share one connection, so a second command to the same provider starts immediately " +
        "instead of shaking hands again, and a dropped connection is retried once on its own.",
    ".reply now reads the conversation and copies your own wording: your messages are quoted back " +
        "to the model, and Settings > Behaviour > Drafted replies sets the tone and the length.",
    "History keeps processed text for an hour instead of five minutes, and the window is yours to " +
        "choose - from five minutes up to a day.",
    "A new Usage tab next to History shows requests and tokens per provider, for this session and " +
        "in total, so it is obvious what each model costs you.",
    "Connection failures now say what to do next - offline, timeout, wrong key, used-up credit, " +
        "region block - instead of one generic error."
)
@Composable
fun VersionAnnouncementDialog(
    versionName: String,
    onDismiss: () -> Unit
) {
    AppDialog(
        title = "Prompt AI $versionName",
        message = null,
        onDismiss = onDismiss,
        confirmLabel = "Continue",
        onConfirm = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            announcementNotes.forEach { change ->
                Text(
                    text = change,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun ReleaseNotes(markdown: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp)
            .verticalScroll(rememberScrollState())
    ) {
        MarkdownText(
            markdown = markdown,
            style = LocalTextStyle.current.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
            )
        )
    }
}
