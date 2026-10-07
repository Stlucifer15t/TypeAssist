package com.typeassist.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.typeassist.app.ui.components.PageHeading

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestScreen(onStartTest: () -> Unit, onStopTest: () -> Unit, onBack: () -> Unit) {
    var t by remember { mutableStateOf("") }
    
    val view = LocalView.current
    val primaryColor = MaterialTheme.colorScheme.primary

    DisposableEffect(Unit) { onStartTest(); onDispose { onStopTest() } }

    val presets = listOf(
        "What is the capital of Bangladesh? .ta",
        "Sp3ll1ng and gr@mm3r mistake shall be fixing .g",
        "এটি একটি এআই ভিত্তিক অ্যাপ। .tr"
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Test Lab") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { p ->
        Column(modifier = Modifier.padding(p).padding(16.dp).verticalScroll(rememberScrollState())) {
            PageHeading(
                title = "Try a shortcut",
                description = "This field is connected to the Accessibility Service. Use a preset or type your own test text.",
                eyebrow = "TEST LAB"
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value = t, onValueChange = { t=it }, label = { Text("Type or tap a preset...") }, modifier = Modifier.fillMaxWidth().height(150.dp))
            Spacer(Modifier.height(24.dp))
            Text("Quick Test Triggers:", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            presets.forEach { item ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { t = item }, elevation = CardDefaults.cardElevation(2.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.TouchApp, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)); Text(item, fontSize = 14.sp) }
                }
            }
        }
    }
}