package dev.byteide.app

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import org.json.JSONObject

private const val TERMINAL_BACKGROUND = 0xFF080C12.toInt()
private const val TERMINAL_FOREGROUND = 0xFFD7E2F2.toInt()

private fun gridColor(value: org.json.JSONArray?, fallback: Int): Int {
    if (value == null || value.length() < 4) return fallback
    return when (value.optInt(0, 0)) {
        1 -> {
            val index = value.optInt(1).coerceIn(0, 255)
            val palette = intArrayOf(
                0xFF000000.toInt(),0xFFCD3131.toInt(),0xFF0DBC79.toInt(),0xFFE5E510.toInt(),
                0xFF2472C8.toInt(),0xFFBC3FBC.toInt(),0xFF11A8CD.toInt(),0xFFE5E5E5.toInt(),
                0xFF666666.toInt(),0xFFF14C4C.toInt(),0xFF23D18B.toInt(),0xFFF5F543.toInt(),
                0xFF3B8EEA.toInt(),0xFFD670D6.toInt(),0xFF29B8DB.toInt(),0xFFFFFFFF.toInt()
            )
            when {
                index < 16 -> palette[index]
                index < 232 -> {
                    val n = index - 16
                    fun channel(v: Int) = if (v == 0) 0 else 55 + v * 40
                    android.graphics.Color.rgb(channel(n / 36), channel((n / 6) % 6), channel(n % 6))
                }
                else -> {
                    val gray = 8 + (index - 232) * 10
                    android.graphics.Color.rgb(gray, gray, gray)
                }
            }
        }
        2 -> android.graphics.Color.rgb(
            value.optInt(1).coerceIn(0, 255),
            value.optInt(2).coerceIn(0, 255),
            value.optInt(3).coerceIn(0, 255)
        )
        else -> fallback
    }
}

/** Native canvas cell-grid renderer. Accepts native VT JSON; plain-text fallback remains supported. */
@Composable
fun TerminalScreen(
    output: String,
    modifier: Modifier = Modifier,
    maxCharacters: Int = 24000,
    onSelectionChanged: (String) -> Unit = {}
) {
    var selectionStart by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    var selectionEnd by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val gridState = remember { mutableStateOf<JSONObject?>(null) }
    val parsedUpdate = remember(output) { runCatching { JSONObject(output) }.getOrNull() }
    LaunchedEffect(parsedUpdate) {
        if (parsedUpdate != null && parsedUpdate.has("full")) {
            if (parsedUpdate.optBoolean("full")) {
                gridState.value = parsedUpdate
            } else {
                val previous = gridState.value
                if (previous != null) {
                    val merged = runCatching { JSONObject(previous.toString()) }.getOrNull()
                    if (merged != null) {
                        val rows = merged.optJSONArray("grid")
                        val cells = parsedUpdate.optJSONArray("cells")
                        if (rows != null && cells != null) {
                            for (index in 0 until cells.length()) {
                                val change = cells.optJSONObject(index) ?: continue
                                val row = rows.optJSONArray(change.optInt("r")) ?: continue
                                row.put(change.optInt("c"), change.optJSONObject("cell") ?: JSONObject())
                            }
                        }
                        merged.put("cursor", parsedUpdate.optJSONObject("cursor") ?: JSONObject())
                        gridState.value = merged
                    }
                }
            }
        }
    }
    val displayFrame = gridState.value ?: parsedUpdate
    val cursorTransition = rememberInfiniteTransition(label = "terminal-cursor")
    val cursorAlpha = cursorTransition.animateFloat(
        initialValue = 1f, targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse),
        label = "cursor-alpha"
    ).value
    Canvas(modifier = modifier.fillMaxSize().background(Color(TERMINAL_BACKGROUND)).pointerInput(displayFrame) {
        detectDragGestures(
            onDragStart = { selectionStart = it; selectionEnd = it },
            onDrag = { change, _ -> selectionEnd = change.position },
            onDragEnd = { }
        )
    }) {
        val canvas = drawContext.canvas.nativeCanvas
        canvas.drawColor(TERMINAL_BACKGROUND)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textSize = 14.dp.toPx()
            color = TERMINAL_FOREGROUND
        }
        val cellWidth = paint.measureText("M").coerceAtLeast(1f)
        val metrics = paint.fontMetrics
        val cellHeight = (metrics.descent - metrics.ascent).coerceAtLeast(1f)
        val baselineOffset = -metrics.ascent

        val parsed = displayFrame
        val grid = parsed?.optJSONArray("grid")
        if (grid == null) {
            val lines = output.takeLast(maxCharacters).split('\n')
            lines.forEachIndexed { row, line ->
                val y = row * cellHeight + baselineOffset
                if (y <= size.height) canvas.drawText(line, 0f, y, paint)
            }
            return@Canvas
        }

        val rowCount = parsed.optInt("rows", grid.length()).coerceAtLeast(0)
        val cursor = parsed.optJSONObject("cursor")
        val cursorRow = cursor?.optInt("row", -1) ?: -1
        val cursorColumn = cursor?.optInt("column", -1) ?: -1
        val startPoint = selectionStart
        val endPoint = selectionEnd
        if (startPoint != null && endPoint != null && grid.length() > 0) {
            val startRow = (startPoint.y / cellHeight).toInt().coerceIn(0, grid.length() - 1)
            val endRow = (endPoint.y / cellHeight).toInt().coerceIn(0, grid.length() - 1)
            val startCol = (startPoint.x / cellWidth).toInt().coerceAtLeast(0)
            val endCol = (endPoint.x / cellWidth).toInt().coerceAtLeast(0)
            val firstRow = minOf(startRow, endRow)
            val lastRow = maxOf(startRow, endRow)
            for (r in firstRow..lastRow) {
                val cells = grid.optJSONArray(r) ?: continue
                val from = if (r == startRow) startCol else 0
                val to = if (r == endRow) endCol else cells.length() - 1
                val left = minOf(from, to).coerceIn(0, cells.length())
                val right = (maxOf(from, to) + 1).coerceIn(left, cells.length())
                if (right > left) {
                    paint.color = 0x665B83D6
                    canvas.drawRect(left * cellWidth, r * cellHeight, right * cellWidth, (r + 1) * cellHeight, paint)
                }
            }
        }

        val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb((cursorAlpha * 255).toInt(), 183, 201, 232)
            style = Paint.Style.STROKE
            strokeWidth = 1.5.dp.toPx()
        }

        for (row in 0 until minOf(rowCount, grid.length())) {
            val cells = grid.optJSONArray(row) ?: continue
            val y = row * cellHeight
            if (y > size.height) break
            for (column in 0 until cells.length()) {
                val cell = cells.optJSONObject(column) ?: continue
                val x = column * cellWidth
                if (x > size.width) break
                var fg = gridColor(cell.optJSONArray("f"), TERMINAL_FOREGROUND)
                var bg = gridColor(cell.optJSONArray("b"), TERMINAL_BACKGROUND)
                val flags = cell.optInt("a", 0)
                if (flags and 8 != 0) { val swap = fg; fg = bg; bg = swap }
                if (bg != TERMINAL_BACKGROUND) {
                    paint.color = bg
                    paint.style = Paint.Style.FILL
                    canvas.drawRect(x, y, x + cellWidth + 0.5f, y + cellHeight, paint)
                }
                paint.color = fg
                paint.style = Paint.Style.FILL
                paint.typeface = Typeface.create(
                    Typeface.MONOSPACE,
                    (if (flags and 1 != 0) Typeface.BOLD else Typeface.NORMAL) or
                        (if (flags and 2 != 0) Typeface.ITALIC else Typeface.NORMAL)
                )
                val text = cell.optString("t", " ")
                if (text.isNotEmpty() && text != " ") {
                    canvas.drawText(text, x, y + baselineOffset, paint)
                }
                if (flags and 4 != 0) {
                    canvas.drawLine(x, y + cellHeight - 1f, x + cellWidth, y + cellHeight - 1f, paint)
                }
                if (flags and 16 != 0) {
                    canvas.drawLine(x, y + cellHeight * 0.55f, x + cellWidth, y + cellHeight * 0.55f, paint)
                }
            }
        }

        if (cursorRow in 0 until rowCount && cursorColumn >= 0) {
            val left = cursorColumn * cellWidth
            val top = cursorRow * cellHeight
            canvas.drawRect(RectF(left, top, left + cellWidth, top + cellHeight), cursorPaint)
        }
    }

    LaunchedEffect(displayFrame, selectionStart, selectionEnd) {
        val frame = displayFrame
        val startPoint = selectionStart
        val endPoint = selectionEnd
        if (frame == null || startPoint == null || endPoint == null) {
            onSelectionChanged("")
        } else {
            val rows = frame.optJSONArray("grid")
            if (rows == null || rows.length() == 0) onSelectionChanged("")
            else {
                val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                    textSize = with(androidx.compose.ui.unit.Density(density)) { 14.dp.toPx() }
                }
                val cw = measurePaint.measureText("M").coerceAtLeast(1f)
                val ch = (measurePaint.fontMetrics.descent - measurePaint.fontMetrics.ascent).coerceAtLeast(1f)
                val sr = (startPoint.y / ch).toInt().coerceIn(0, rows.length() - 1)
                val er = (endPoint.y / ch).toInt().coerceIn(0, rows.length() - 1)
                val sc = (startPoint.x / cw).toInt().coerceAtLeast(0)
                val ec = (endPoint.x / cw).toInt().coerceAtLeast(0)
                val output = StringBuilder()
                for (r in minOf(sr, er)..maxOf(sr, er)) {
                    val cells = rows.optJSONArray(r) ?: continue
                    val from = if (r == sr) sc else 0
                    val to = if (r == er) ec else cells.length() - 1
                    val left = minOf(from, to).coerceIn(0, cells.length())
                    val right = (maxOf(from, to) + 1).coerceIn(left, cells.length())
                    for (c in left until right) output.append(cells.optJSONObject(c)?.optString("t", " ") ?: " ")
                    if (r != maxOf(sr, er)) output.append('\n')
                }
                onSelectionChanged(output.toString().trimEnd())
            }
        }
    }
}
