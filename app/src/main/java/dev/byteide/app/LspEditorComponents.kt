package dev.byteide.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LspPanel = Color(0xFF141B27)
private val LspPanelRaised = Color(0xFF1B2534)
private val LspText = Color(0xFFE8EEF9)
private val LspMuted = Color(0xFF91A0B5)
private val LspError = Color(0xFFFF647C)
private val LspWarning = Color(0xFFFFC66D)
private val LspInfo = Color(0xFF82AAFF)

/** UI state populated only by actual LSP responses. */
data class LspEditorUiState(
    val completions: List<LspCompletionItem> = emptyList(),
    val completionVisible: Boolean = false,
    val diagnostics: List<LspDiagnostic> = emptyList(),
    val hover: LspHover? = null,
    val busy: Boolean = false
) {
    val errorCount: Int get() = diagnostics.count { it.severity == 1 }
    val warningCount: Int get() = diagnostics.count { it.severity == 2 }
}

/** Completion list; selecting an item is delegated to the editor transaction layer. */
@Composable
fun LspCompletionPopup(
    items: List<LspCompletionItem>,
    visible: Boolean,
    onSelect: (LspCompletionItem) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible || items.isEmpty()) return
    Surface(
        modifier = modifier,
        color = LspPanel,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 12.dp
    ) {
        LazyColumn(Modifier.heightIn(max = 260.dp).widthIn(min = 220.dp, max = 360.dp)) {
            items(items.take(40)) { item ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { onSelect(item) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(item.label, color = LspText, fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    item.detail?.let {
                        Spacer(Modifier.width(8.dp))
                        Text(it, color = LspMuted, fontSize = 11.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** Compact hover documentation surface. Markdown is displayed as readable plain text for now. */
@Composable
fun LspHoverCard(hover: LspHover?, modifier: Modifier = Modifier) {
    val content = hover?.markdown?.takeIf { it.isNotBlank() } ?: return
    Surface(modifier = modifier, color = LspPanel, shape = RoundedCornerShape(12.dp),
        shadowElevation = 8.dp) {
        Text(content, color = LspText, fontSize = 12.sp,
            modifier = Modifier.padding(12.dp))
    }
}

/** Problems panel backed by diagnostics published by a language server. */
@Composable
fun LspProblemsPanel(
    diagnostics: List<LspDiagnostic>,
    onSelect: (LspDiagnostic) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth().background(LspPanel, RoundedCornerShape(12.dp)).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("PROBLEMS", color = LspMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp, modifier = Modifier.weight(1f))
            Text("${diagnostics.count { it.severity == 1 }} errors", color = LspError, fontSize = 10.sp)
            Spacer(Modifier.width(8.dp))
            Text("${diagnostics.count { it.severity == 2 }} warnings", color = LspWarning, fontSize = 10.sp)
        }
        Spacer(Modifier.height(8.dp))
        if (diagnostics.isEmpty()) {
            Text("No language-server diagnostics.", color = LspMuted, fontSize = 12.sp)
        } else {
            LazyColumn(Modifier.heightIn(max = 240.dp)) {
                items(diagnostics) { diagnostic ->
                    val tint = when (diagnostic.severity) {
                        1 -> LspError
                        2 -> LspWarning
                        else -> LspInfo
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(diagnostic) }
                            .padding(vertical = 7.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            when (diagnostic.severity) { 1 -> "●"; 2 -> "▲"; 3 -> "ⓘ"; else -> "•" },
                            color = tint, fontSize = 11.sp
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(diagnostic.message, color = LspText, fontSize = 12.sp)
                            Text(
                                "Line ${diagnostic.range.start.line + 1}, Col ${diagnostic.range.start.character + 1}" +
                                    (diagnostic.source?.let { " · $it" } ?: "") +
                                    (diagnostic.code?.let { " · $it" } ?: ""),
                                color = LspMuted, fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Reusable toolbar summary for the Problems tab. */
@Composable
fun LspDiagnosticsSummary(diagnostics: List<LspDiagnostic>, modifier: Modifier = Modifier) {
    val errors = diagnostics.count { it.severity == 1 }
    val warnings = diagnostics.count { it.severity == 2 }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("$errors errors", color = LspError, fontSize = 11.sp)
        Spacer(Modifier.width(10.dp))
        Text("$warnings warnings", color = LspWarning, fontSize = 11.sp)
    }
}
