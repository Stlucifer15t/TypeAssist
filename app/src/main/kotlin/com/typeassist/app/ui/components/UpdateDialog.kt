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
 * Shown once after the app is updated. The notes come from the GitHub release of the installed
 * version, so what it says actually matches what was installed; if the release could not be
 * fetched (offline first launch, missing tag) a short generic note stands in.
 */
@Composable
fun VersionAnnouncementDialog(
    versionName: String,
    release: GitHubRelease?,
    onDismiss: () -> Unit
) {
    AppDialog(
        title = "Prompt AI $versionName",
        message = null,
        onDismiss = onDismiss,
        confirmLabel = "Continue",
        onConfirm = onDismiss
    ) {
        if (release?.body?.isNotBlank() == true) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ReleaseNotes(release.body)
            }
        } else {
            Text(
                text = "Prompt AI has been updated. Details of this release are on the project's " +
                    "Releases page.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
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
