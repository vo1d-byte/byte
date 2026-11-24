package dev.byte.ide.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

enum class Action(val label: String) {
    Run("Run"), Find("Find"), Undo("Undo"), More("More"),
    Debug("Debug"), Files("Files"), Git("Git"), Replace("Replace"),
    GoToDefinition("Go to definition"), CommentLine("Comment line"),
    DuplicateLine("Duplicate line"), Format("Format"),
    Save("Save"), Redo("Redo"), Terminal("Terminal"),
}

val DEFAULT_LAYER1 = listOf(Action.Run, Action.Find, Action.Undo, Action.More)
val DEFAULT_LAYER2 = listOf(
    Action.Debug, Action.Files, Action.Git, Action.Replace,
    Action.GoToDefinition, Action.CommentLine, Action.DuplicateLine, Action.Format,
)

/**
 * The floating ^ button (spec section 6): 48 dp, draggable, snaps to the nearest
 * side, short tap = layer 1, long press = configured action (default Run).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FloatingActions(
    layer1: List<Action>,
    layer2: List<Action>,
    longPress: Action,
    typing: Boolean,
    bottomInsetDp: Int,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val sizePx = with(density) { 48.dp.toPx() }
    val marginPx = with(density) { 8.dp.toPx() }
    val insetPx = with(density) { bottomInsetDp.dp.toPx() }

    var box by remember { mutableStateOf(IntSize.Zero) }
    var pos by remember { mutableStateOf(Offset.Zero) }
    var placed by remember { mutableStateOf(false) }
    var layer by remember { mutableIntStateOf(0) } // 0 closed, 1 first, 2 second

    fun clampY(y: Float) = y.coerceIn(marginPx, (box.height - sizePx - marginPx - insetPx).coerceAtLeast(marginPx))

    LaunchedEffect(box) {
        if (box != IntSize.Zero && !placed) {
            pos = Offset(box.width - sizePx - marginPx, clampY(box.height * 0.6f))
            placed = true
        }
    }
    LaunchedEffect(insetPx, box) { if (placed) pos = pos.copy(y = clampY(pos.y)) }

    Box(modifier.fillMaxSize().onSizeChanged { box = it }) {
        if (layer != 0) {
            val onRight = pos.x + sizePx / 2 > box.width / 2
            Column(
                Modifier
                    .layout { m, c ->
                        val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
                        layout(p.width, p.height) {
                            val x = if (onRight) pos.x + sizePx - p.width else pos.x
                            p.place(x.roundToInt().coerceAtLeast(0), (pos.y - p.height - marginPx).roundToInt().coerceAtLeast(0))
                        }
                    }
                    .clip(RoundedCornerShape(16.dp))
                    .background(C.surface)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (layer == 1) {
                    // Most used closest to the thumb: bottom of the column.
                    layer1.reversed().forEach { a ->
                        KeyButton(
                            a.label,
                            background = if (a == Action.Run) C.accent else C.key,
                            foreground = if (a == Action.Run) C.bg else C.text,
                        ) {
                            if (a == Action.More) layer = 2 else { layer = 0; onAction(a) }
                        }
                    }
                } else {
                    layer2.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { a -> KeyButton(a.label) { layer = 0; onAction(a) } }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        KeyButton("‹ Back") { layer = 1 }
                        KeyButton("Customize", foreground = C.accent) { layer = 0 }
                    }
                }
            }
        }

        Box(
            Modifier
                .offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }
                .size(48.dp)
                .alpha(if (typing) 0.7f else 1f)
                .clip(CircleShape)
                .background(C.accent)
                .semantics { contentDescription = "Actions menu" }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, d ->
                            change.consume()
                            pos = Offset(
                                (pos.x + d.x).coerceIn(0f, box.width - sizePx),
                                clampY(pos.y + d.y),
                            )
                        },
                        onDragEnd = {
                            val left = pos.x + sizePx / 2 < box.width / 2
                            pos = pos.copy(x = if (left) marginPx else box.width - sizePx - marginPx)
                        },
                    )
                }
                .combinedClickable(
                    onClick = { layer = if (layer == 0) 1 else 0 },
                    onLongClick = { layer = 0; onAction(longPress) },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (layer == 0) "^" else "×", color = C.bg, fontSize = 22.sp)
        }
    }
}
