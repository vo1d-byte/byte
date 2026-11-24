package dev.byte.ide.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ERROR_LINE = Regex("(?i)(error|exception|traceback|panic)")

/** Rough heuristic until real diagnostics (LSP) exist. */
fun problemLines(text: String): List<String> =
    text.lines().takeLast(200).filter { ERROR_LINE.containsMatchIn(it) }

@Composable
fun OutputPanel(
    activeTab: OutputTab,
    onTab: (OutputTab) -> Unit,
    terminal: TerminalSession,
    runner: Runner,
    onTerminalFocus: (Boolean) -> Unit,
    onSnapStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val problems = problemLines(terminal.output)
    Column(modifier.background(C.panel)) {
        // Drag handle: only the handle and tab strip drag, so content can scroll freely.
        Column(
            Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            if (total < -60f) onSnapStep(1) else if (total > 60f) onSnapStep(-1)
                        },
                        onVerticalDrag = { _, dy -> total += dy },
                    )
                },
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .semantics { contentDescription = "Drag to resize output panel" },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(C.muted))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutputTab.values().forEach { tab ->
                    val label = if (tab == OutputTab.Problems) "Problems ${problems.size}" else tab.title
                    Box(
                        Modifier
                            .height(44.dp)
                            .clickable { onTab(tab) }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            color = if (tab == activeTab) C.text else C.muted,
                            fontSize = 15.sp,
                        )
                    }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)) {
            when (activeTab) {
                OutputTab.Output -> LogText(terminal.output.ifBlank { "Nothing has run yet." })
                OutputTab.Terminal -> TerminalView(terminal, onTerminalFocus)
                OutputTab.Problems -> LogText(if (problems.isEmpty()) "No problems detected." else problems.joinToString("\n"), error = problems.isNotEmpty())
                OutputTab.Debug -> LogText("Debug adapter support is not implemented yet.")
                OutputTab.Logs -> LogText(runner.log.ifBlank { "No logs yet." })
            }
        }
    }
}

@Composable
private fun LogText(text: String, error: Boolean = false) {
    val scroll = rememberScrollState()
    LaunchedEffect(text) { scroll.scrollTo(scroll.maxValue) }
    Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
        Text(text, style = Mono, color = if (error) C.red else C.green, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

/** Line-based terminal input for v0.1 (no raw keystrokes / full-screen programs yet). */
@Composable
private fun TerminalView(terminal: TerminalSession, onFocus: (Boolean) -> Unit) {
    var line by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { LogText(terminal.output) }
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(C.surface)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$ ", style = Mono, color = C.green)
            BasicTextField(
                value = line,
                onValueChange = { line = it },
                singleLine = true,
                textStyle = Mono,
                cursorBrush = SolidColor(C.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    terminal.send(line + "\n")
                    line = ""
                }),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { onFocus(it.isFocused) },
            )
        }
    }
}

/** The thin line the panel folds into (spec section 9). 44 dp touch target, 20 dp visual. */
@Composable
fun FoldedLine(fileName: String, hasError: Boolean, hint: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = (if (hasError) "Error. " else "") + "$fileName. $hint" },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier.fillMaxWidth().height(20.dp).background(C.surface).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (hasError) Box(Modifier.size(8.dp).clip(CircleShape).background(C.red))
            Text(fileName, color = C.text, fontSize = 12.sp)
            Box(Modifier.weight(1f))
            // Text accompanies the dot: colour is never the only signal.
            Text(if (hasError) "Error · $hint" else hint, color = C.muted, fontSize = 12.sp)
        }
    }
}
