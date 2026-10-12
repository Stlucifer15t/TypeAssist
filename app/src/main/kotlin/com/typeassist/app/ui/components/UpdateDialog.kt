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
    "The whole interface has been rebuilt: one deep-charcoal dark theme, a true-black AMOLED theme " +
        "and a properly designed light theme.",
    "A single electric-lime accent replaces the mixed indigo, teal and orange from before.",
    "Settings is organised by topic - Assistant, Reliability, Data and Learn - with the same " +
        "options as before and nothing hidden.",
    "The floating chip, preview card, snippet picker and loading indicator now follow your " +
        "appearance choice instead of your phone's night mode.",
    "New in Settings: a preview of every floating surface, plus a guide generated from your own " +
        "commands."
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
