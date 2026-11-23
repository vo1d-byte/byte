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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/**
 * Scrollable, selectable terminal text surface.
 *
 * This is the first Compose rendering surface. It intentionally renders the
 * current text stream; cell-level VT colors/cursor state will be wired from
 * the native TerminalSnapshot in a subsequent integration step.
 */
@Composable
fun TerminalScreen(
    output: String,
    modifier: Modifier = Modifier,
    maxCharacters: Int = 24000
) {
    val scroll = rememberScrollState()
    val visibleText = output.takeLast(maxCharacters)
    LaunchedEffect(visibleText.length) { scroll.scrollTo(scroll.maxValue) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF080C12))
            .verticalScroll(scroll)
    ) {
        SelectionContainer {
            Text(
                text = visibleText.ifBlank { "Terminal ready. Enter a command below." },
                color = Color(0xFFD7E2F2),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                style = TextStyle(background = Color.Transparent)
            )
        }
    }
}
