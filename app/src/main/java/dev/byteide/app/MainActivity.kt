package dev.byteide.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF0C1018)
private val Panel = Color(0xFF151C28)
private val Accent = Color(0xFF7C9CFF)
private val Muted = Color(0xFF8E9AAF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ByteTheme { ByteHome() } }
    }
}

@Composable
private fun ByteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Accent, background = Ink, surface = Panel,
        onBackground = Color(0xFFEAF0FF), onSurface = Color(0xFFEAF0FF)
    ), content = content)
}

@Composable
private fun ByteHome() {
    var selected by remember { mutableStateOf("Workspace") }
    var terminal by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("fun main() {\n    println(\"Hello, Byte!\")\n}") }
    Scaffold(containerColor = Ink, bottomBar = {
        NavigationBar(containerColor = Panel) {
            listOf("Workspace", "Files", "Terminal", "Git").forEach { item ->
                NavigationBarItem(selected == item, onClick = {
                    selected = item
                    if (item == "Terminal") terminal = true
                }, icon = {
                    Icon(when (item) {
                        "Workspace" -> Icons.Default.Code
                        "Files" -> Icons.Default.Folder
                        "Terminal" -> Icons.Default.Terminal
                        else -> Icons.Default.AccountTree
                    }, contentDescription = item)
                }, label = { Text(item) })
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).background(Accent, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Bolt, null, tint = Ink)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("BYTE", fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                    Text("MOBILE DEVELOPMENT STUDIO", color = Muted, fontSize = 9.sp, letterSpacing = 1.4.sp)
                }
                IconButton(onClick = {}) { Icon(Icons.Default.Settings, "Settings", tint = Muted) }
            }
            Spacer(Modifier.height(26.dp))
            Text("Good evening.", color = Muted, fontSize = 14.sp)
            Text("Build something great.", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("01", "PROJECTS")
                Metric("00", "TOOLCHAINS")
                Metric("DEV", "STATUS")
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(selected.uppercase(), fontSize = 12.sp, letterSpacing = 2.sp, color = Accent, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("BYTE CORE  •  v0.1.0", color = Muted, fontSize = 10.sp)
            }
            Spacer(Modifier.height(10.dp))
            when (selected) {
                "Workspace" -> WorkspaceCard()
                "Files" -> EmptyCard("No workspace opened", "Create or import a project to browse its files.")
                "Git" -> EmptyCard("Repository not connected", "Open a project and initialize Git or connect GitHub.")
                else -> TerminalCard()
            }
            Spacer(Modifier.height(16.dp))
            Text("EDITOR PREVIEW", color = Muted, fontSize = 10.sp, letterSpacing = 1.8.sp)
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth().weight(1f).background(Panel, RoundedCornerShape(16.dp)).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Code, null, tint = Accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Main.kt", fontSize = 12.sp, color = Muted)
                    Spacer(Modifier.weight(1f))
                    Text("KOTLIN", fontSize = 9.sp, color = Accent)
                }
                Spacer(Modifier.height(10.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = code, onValueChange = { code = it },
                    modifier = Modifier.fillMaxSize(),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color(0xFFD7E1F5), fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 21.sp
                    )
                )
            }
        }
    }
    if (terminal) AlertDialog(onDismissRequest = { terminal = false },
        title = { Text("Terminal") },
        text = { Text("The interactive PTY backend is planned. This preview does not execute shell commands.", color = Muted) },
        confirmButton = { TextButton(onClick = { terminal = false }) { Text("Close") } })
}

@Composable
private fun Metric(value: String, label: String) {
    Column(Modifier.weight(1f).background(Panel, RoundedCornerShape(14.dp)).padding(13.dp)) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 9.sp, color = Muted, letterSpacing = 1.sp)
    }
}

@Composable
private fun WorkspaceCard() {
    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.FolderOpen, null, tint = Accent)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Your workspace", fontWeight = FontWeight.SemiBold)
                Text("Start with a project template", color = Muted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Android", "Website", "2D Game").forEach {
                SuggestionChip(onClick = {}, label = { Text(it, fontSize = 11.sp) })
            }
        }
        Text("Project creation and build actions are being implemented.", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun EmptyCard(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.FolderOff, null, tint = Muted, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(8.dp))
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(detail, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun TerminalCard() {
    Column(Modifier.fillMaxWidth().background(Color(0xFF080C12), RoundedCornerShape(16.dp)).padding(16.dp)) {
        Text("byte@android:~ $", color = Color(0xFF85D6A3), fontFamily = FontFamily.Monospace)
        Text("PTY service not connected", color = Muted, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
}
