package dev.byte.ide.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Extra keys above the system keyboard (spec section 7). 44 dp tall, scrolls sideways. */
@Composable
fun KeyRow(
    kind: KeyRowKind,
    editor: EditorState,
    terminal: TerminalSession,
    onCursorEditor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keys: List<Pair<String, () -> Unit>> = when (kind) {
        KeyRowKind.Code -> listOf(
            "Tab" to { editor.op("insert", "    ") },
            "( )" to { editor.op("insertPair", "()") },
            "{ }" to { editor.op("insertPair", "{}") },
            "[ ]" to { editor.op("insertPair", "[]") },
            ";" to { editor.op("insert", ";") },
            ":" to { editor.op("insert", ":") },
            "\"" to { editor.op("insert", "\"") },
            "=" to { editor.op("insert", "=") },
            "<" to { editor.op("insert", "<") },
            ">" to { editor.op("insert", ">") },
            "/" to { editor.op("insert", "/") },
            "_" to { editor.op("insert", "_") },
            "←" to { editor.moveCursor(-1, 0) },
            "→" to { editor.moveCursor(1, 0) },
            "↑" to { editor.moveCursor(0, -1) },
            "↓" to { editor.moveCursor(0, 1) },
        )
        KeyRowKind.Terminal -> listOf(
            "Esc" to { terminal.send("\u001B") },
            "Tab" to { terminal.send("\t") },
            "↑" to { terminal.send("\u001B[A") },
            "↓" to { terminal.send("\u001B[B") },
            "|" to { terminal.send("|") },
            "~" to { terminal.send("~") },
            "/" to { terminal.send("/") },
            "-" to { terminal.send("-") },
            "Ctrl+C" to { terminal.ctrlC() },
            "Ctrl+D" to { terminal.send("\u0004") },
        )
    }
    Row(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(C.surface),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        KeyButton(
            label = "⌶",
            description = "Cursor editor",
            background = C.keyActive,
            modifier = Modifier.padding(start = 4.dp),
            onClick = onCursorEditor,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(keys.size) { i ->
                KeyButton(label = keys[i].first, mono = true, onClick = keys[i].second)
            }
        }
    }
}
