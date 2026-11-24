package dev.byte.ide.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Replaces the system keyboard: trackpad, arrows, select tools, clipboard (spec section 8). */
@Composable
fun CursorEditor(editor: EditorState, onBackToKeys: () -> Unit, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    val density = LocalDensity.current
    val charPx = with(density) { 14.dp.toPx() }
    val linePx = with(density) { 22.dp.toPx() }
    var accX by remember { mutableFloatStateOf(0f) }
    var accY by remember { mutableFloatStateOf(0f) }

    fun selected(): String {
        val v = editor.value
        return v.text.substring(v.selection.min, v.selection.max)
    }

    Column(
        modifier
            .fillMaxWidth()
            .height(334.dp)
            .background(C.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyButton(
                label = if (editor.selectMode) "Select ON" else "Select",
                background = if (editor.selectMode) C.accent else C.key,
                foreground = if (editor.selectMode) C.bg else C.text,
                modifier = Modifier.weight(1f),
            ) { editor.selectMode = !editor.selectMode }
            KeyButton("Word", modifier = Modifier.weight(1f)) { editor.op("selectWord") }
            KeyButton("Line", modifier = Modifier.weight(1f)) { editor.op("selectLine") }
            KeyButton("Doc", modifier = Modifier.weight(1f)) { editor.op("selectAll") }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(C.panel)
                    .pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            accX += drag.x
                            accY += drag.y
                            val dx = (accX / charPx).toInt()
                            val dy = (accY / linePx).toInt()
                            if (dx != 0 || dy != 0) {
                                accX -= dx * charPx
                                accY -= dy * linePx
                                editor.moveCursor(dx, dy)
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Drag here to move the cursor\nWith Select ON: drag to select",
                    color = C.muted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(12.dp),
                )
            }
            Column(
                Modifier.width(150.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                KeyButton("↑", description = "Up") { editor.moveCursor(0, -1) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KeyButton("←", description = "Left") { editor.moveCursor(-1, 0) }
                    KeyButton("→", description = "Right") { editor.moveCursor(1, 0) }
                }
                KeyButton("↓", description = "Down") { editor.moveCursor(0, 1) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyButton("Cut", modifier = Modifier.weight(1f)) {
                clipboard.setText(AnnotatedString(selected()))
                editor.op("insert", "")
            }
            KeyButton("Copy", modifier = Modifier.weight(1f)) {
                clipboard.setText(AnnotatedString(selected()))
            }
            KeyButton("Paste", modifier = Modifier.weight(1f)) {
                clipboard.getText()?.text?.let { editor.op("insert", it) }
            }
            KeyButton("All", modifier = Modifier.weight(1f)) { editor.op("selectAll") }
        }
        KeyButton(
            "ABC · Back to keys",
            background = C.keyActive,
            modifier = Modifier.fillMaxWidth(),
            onClick = onBackToKeys,
        )
    }
}
