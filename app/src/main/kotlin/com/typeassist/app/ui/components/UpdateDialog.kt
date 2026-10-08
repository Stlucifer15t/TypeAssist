package com.typeassist.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.typeassist.app.data.model.GitHubRelease
import dev.jeziellago.compose.markdowntext.MarkdownText

@Composable
fun VersionAnnouncementDialog(versionName: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.RocketLaunch,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )
        },
        title = { Text("Prompt AI $versionName is here", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Here’s what’s new:", fontWeight = FontWeight.SemiBold)
                Text("• The Accept · Reject · Retry chip now appears in the middle of the screen, where the keyboard is less likely to cover it.")
                Text("• .reply copies your writing style from the messages marked Me: and avoids stiff or exaggerated wording.")
                Text("• Dark and AMOLED black look different now. Dark is a lighter dark grey, and AMOLED is true black with near-black cards.")
                Text("• Floating chips, the preview card and the snippet picker follow the Appearance you pick in Settings, not only your phone’s night mode.")
                Text("• The Appearance list shows a small colour preview for each theme.")
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Get started")
            }
        }
    )
}

@Composable
fun UpdateDialog(release: GitHubRelease, onDismiss: () -> Unit) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        ),
        icon = { 
            Icon(Icons.Default.RocketLaunch, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp)) 
        },
        title = { 
            Text(text = "New Update Available!", fontWeight = FontWeight.Bold) 
        },
        text = {
            Column {
                Text("Version ${release.tagName} is now available.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))
                Text("What's New:", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier
                    .heightIn(max = 250.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                ) {
                    MarkdownText(
                        markdown = release.body,
                        style = LocalTextStyle.current.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("View Release")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Later", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
