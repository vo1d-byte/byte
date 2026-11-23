package dev.byteide.app

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun ToolchainManagerDialog(catalog: LanguagePackCatalog, installer: ToolchainInstaller, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    val installed = installer.installedToolchains
    val states = installer.installStates
    val categories = listOf("All") + catalog.packages.map { it.category }.distinct().sorted()
    val filtered = catalog.packages.filter { pack ->
        (selectedCategory == "All" || pack.category == selectedCategory) &&
            (query.isBlank() || pack.spec.displayName.contains(query, true) ||
                pack.spec.languageId.contains(query, true) || pack.description.contains(query, true))
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(0.92f), shape = RoundedCornerShape(22.dp), color = Color(0xFF101722)) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Language Toolchains", color = Color(0xFFE8EEF9), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Catalog ${catalog.version} · ${installed.size} installed", color = Color(0xFF91A0B5), fontSize = 11.sp)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close", tint = Color(0xFFE8EEF9)) }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, placeholder = { Text("Search languages and toolchains") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.forEach { category ->
                        FilterChip(selected = selectedCategory == category, onClick = { selectedCategory = category },
                            label = { Text(category, fontSize = 11.sp) })
                    }
                }
                Spacer(Modifier.height(10.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (installed.isNotEmpty()) {
                        item { Text("INSTALLED", color = Color(0xFF91A0B5), fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                        items(installed, key = { "${it.packageId}:${it.version}" }) { InstalledToolchainRow(it) }
                    }
                    if (catalog.packages.isEmpty()) {
                        item { EmptyLanguageCatalog() }
                    } else {
                        item { Text("AVAILABLE PACKAGES", color = Color(0xFF91A0B5), fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                        items(filtered, key = { it.spec.packageId }) { pack ->
                            val state = states[pack.spec.packageId]
                            val isInstalled = installed.any { it.packageId == pack.spec.packageId && it.version == pack.spec.version }
                            LanguagePackCard(pack, state, isInstalled) { installer.install(pack.spec) }
                        }
                        if (filtered.isEmpty()) item { Text("No packages match this search.", color = Color(0xFF91A0B5)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLanguageCatalog() {
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color(0xFF8BA8FF), modifier = Modifier.size(42.dp))
        Spacer(Modifier.height(12.dp))
        Text("No verified language packs yet", color = Color(0xFFE8EEF9), fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "The package browser is ready, but this catalog is intentionally empty. Packages will appear after their Android-compatible artifacts, SHA-256 digests, and metadata have been reviewed.",
            color = Color(0xFF91A0B5), fontSize = 12.sp, lineHeight = 18.sp
        )
    }
}

@Composable
private fun InstalledToolchainRow(item: InstalledToolchain) {
    Surface(color = Color(0xFF1B2534), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(item.packageId, color = Color(0xFFE8EEF9), fontWeight = FontWeight.SemiBold)
            Text("${item.languageId} · ${item.version}", color = Color(0xFF91A0B5), fontSize = 11.sp)
        }
    }
}

@Composable
private fun LanguagePackCard(pack: LanguagePack, state: ToolchainInstallState?, installed: Boolean, onInstall: () -> Unit) {
    val abiCompatible = pack.supportedAbis.isNotEmpty() && pack.supportedAbis.any { it in Build.SUPPORTED_ABIS }
    val active = state?.phase in listOf(
        ToolchainInstallPhase.DOWNLOADING, ToolchainInstallPhase.VERIFYING,
        ToolchainInstallPhase.EXTRACTING, ToolchainInstallPhase.REGISTERING
    )
    Surface(color = Color(0xFF1B2534), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(pack.spec.displayName, color = Color(0xFFE8EEF9), fontWeight = FontWeight.SemiBold)
                    Text("${pack.spec.languageId} · ${pack.spec.version} · ${pack.category}", color = Color(0xFF91A0B5), fontSize = 11.sp)
                }
                Button(onClick = onInstall, enabled = !installed && !active && abiCompatible) {
                    Text(if (installed) "Installed" else if (active) "Working" else "Install")
                }
            }
            if (pack.description.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(pack.description, color = Color(0xFFB4C0D1), fontSize = 12.sp)
            }
            Text(
                if (abiCompatible) "Compatible with this device: ${pack.supportedAbis.joinToString()}"
                else "Unavailable: no verified ABI compatibility for this device",
                color = if (abiCompatible) Color(0xFF91A0B5) else Color(0xFFFFB86B), fontSize = 10.sp
            )
            if (state != null) {
                Spacer(Modifier.height(8.dp))
                if (active) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(state.message, color = if (state.phase == ToolchainInstallPhase.FAILED) Color(0xFFFF647C) else Color(0xFF91A0B5), fontSize = 11.sp)
            }
        }
    }
}
