package com.typeassist.app.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import com.typeassist.app.R
import com.typeassist.app.ui.components.SurfacePanel

@Composable
fun DeveloperCreditSection() {
    SurfacePanel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("Made with care", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Prompt AI is an open-source writing assistant.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(14.dp))
        SocialLink(R.drawable.ic_fab_twitter, "Twitter", "https://twitter.com/estiaksoyeb")
        Spacer(Modifier.height(10.dp))
        SocialLink(R.drawable.ic_fab_github, "Source code on GitHub", "https://github.com/estiaksoyeb/TypeAssist")
        Spacer(Modifier.height(10.dp))
        SocialLink(R.drawable.ic_fab_telegram, "Telegram community", "https://t.me/TypeAssist")
    }
}

@Composable
fun SocialLink(icon: Int, text: String, url: String) {
    val uriHandler = LocalUriHandler.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable { uriHandler.openUri(url) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(text, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun DonationSection() {
    val clipboardManager = LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    SurfacePanel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text("Support development", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Prompt AI is free and open source. If it helps you, you can support its continued development.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(12.dp))
        DonationItem("Binance Pay ID", "724197813", clipboardManager, context)
        DonationItem("USDT (TRC20)", "TPP5S7HdV4Hrrtp5Cjz7TNtttUAfZXJz5a", clipboardManager, context)
    }
}

@Composable
fun DonationItem(
    label: String,
    value: String,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    context: Context
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
        androidx.compose.material3.IconButton(onClick = {
            clipboardManager.setText(AnnotatedString(value))
            Toast.makeText(context, "Copied $label", Toast.LENGTH_SHORT).show()
        }) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Copy $label", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
