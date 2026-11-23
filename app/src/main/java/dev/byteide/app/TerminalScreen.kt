package dev.byteide.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

private val ansiPalette = listOf(
    0xFF000000, 0xFFCD3131, 0xFF0DBC79, 0xFFE5E510,
    0xFF2472C8, 0xFFBC3FBC, 0xFF11A8CD, 0xFFE5E5E5,
    0xFF666666, 0xFFF14C4C, 0xFF23D18B, 0xFFF5F543,
    0xFF3B8EEA, 0xFFD670D6, 0xFF29B8DB, 0xFFFFFFFF
).map { Color(it) }

private fun xtermColor(index: Int): Color {
    if (index < 16) return ansiPalette[index.coerceAtLeast(0)]
    if (index < 232) {
        val n = index - 16
        fun channel(v: Int) = if (v == 0) 0 else 55 + v * 40
        return Color(channel(n / 36) / 255f, channel((n / 6) % 6) / 255f, channel(n % 6) / 255f)
    }
    val gray = (8 + (index - 232).coerceIn(0, 23) * 10) / 255f
    return Color(gray, gray, gray)
}

/** Convert SGR color/style sequences to Compose spans; non-SGR controls are safely hidden. */
private fun ansiAnnotatedText(input: String) = buildAnnotatedString {
    var foreground = Color(0xFFD7E2F2)
    var background = Color.Transparent
    var bold = false
    var italic = false
    var underline = false
    var index = 0
    var plainStart = 0

    fun flush(end: Int) {
        if (end > plainStart) {
            pushStyle(SpanStyle(color = foreground, background = background,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
                textDecoration = if (underline) TextDecoration.Underline else TextDecoration.None))
            append(input.substring(plainStart, end))
            pop()
        }
    }

    while (index < input.length) {
        if (input[index] == '\u001B' && index + 1 < input.length && input[index + 1] == '[') {
            val end = input.indexOf('m', index + 2)
            if (end >= 0) {
                flush(index)
                val values = input.substring(index + 2, end).split(';').map { it.toIntOrNull() ?: 0 }
                var i = 0
                while (i < values.size) {
                    when (val code = values[i]) {
                        0 -> { foreground = Color(0xFFD7E2F2); background = Color.Transparent; bold = false; italic = false; underline = false }
                        1 -> bold = true
                        3 -> italic = true
                        4 -> underline = true
                        22 -> bold = false
                        23 -> italic = false
                        24 -> underline = false
                        in 30..37 -> foreground = ansiPalette[code - 30]
                        in 90..97 -> foreground = ansiPalette[code - 90 + 8]
                        in 40..47 -> background = ansiPalette[code - 40]
                        in 100..107 -> background = ansiPalette[code - 100 + 8]
                        39 -> foreground = Color(0xFFD7E2F2)
                        49 -> background = Color.Transparent
                        38, 48 -> {
                            val targetForeground = code == 38
                            if (i + 2 < values.size && values[i + 1] == 5) {
                                val color = xtermColor(values[i + 2].coerceIn(0, 255))
                                if (targetForeground) foreground = color else background = color
                                i += 2
                            } else if (i + 4 < values.size && values[i + 1] == 2) {
                                val color = Color(values[i + 2].coerceIn(0,255) / 255f,
                                    values[i + 3].coerceIn(0,255) / 255f,
                                    values[i + 4].coerceIn(0,255) / 255f)
                                if (targetForeground) foreground = color else background = color
                                i += 4
                            }
                        }
                    }
                    i++
                }
                index = end + 1
                plainStart = index
                continue
            }
        }
        index++
    }
    flush(input.length)
}

/**
 * Selectable terminal output with ANSI SGR styling and automatic scroll-to-bottom.
 * Cursor-addressing/control sequences are not a full VT screen implementation yet.
 */
@Composable
fun TerminalScreen(
    output: String,
    modifier: Modifier = Modifier,
    maxCharacters: Int = 24000
) {
    val scroll = rememberScrollState()
    val visibleText = output.takeLast(maxCharacters)
    val styledText = ansiAnnotatedText(visibleText)
    LaunchedEffect(visibleText.length) { scroll.scrollTo(scroll.maxValue) }

    Box(modifier.fillMaxSize().background(Color(0xFF080C12)).verticalScroll(scroll)) {
        SelectionContainer {
            Text(
                text = styledText.ifEmpty { ansiAnnotatedText("Terminal ready. Enter a command below.") },
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}
