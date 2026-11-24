package dev.byte.ide.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/** Dark palette; text/background pairs are all >= 4.5:1. */
object C {
    val bg = Color(0xFF14161B)
    val panel = Color(0xFF0E1014)
    val surface = Color(0xFF1C1F26)
    val key = Color(0xFF2A2D35)
    val keyActive = Color(0xFF34507A)
    val text = Color(0xFFE6E8EC)
    val muted = Color(0xFF9AA0AA)
    val green = Color(0xFF8FD694)
    val red = Color(0xFFFF8A80)
    val accent = Color(0xFF5AA9FF)
    val line = Color(0xFF252932)
}

val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = C.text, lineHeight = 22.sp)
