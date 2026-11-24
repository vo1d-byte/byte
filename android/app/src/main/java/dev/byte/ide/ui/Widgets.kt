package dev.byte.ide.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Every tappable control is at least 44 dp (spec section 13). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KeyButton(
    label: String,
    modifier: Modifier = Modifier,
    description: String = label,
    background: Color = C.key,
    foreground: Color = C.text,
    mono: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .widthIn(min = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = foreground,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = if (mono) androidx.compose.ui.text.font.FontFamily.Monospace else null,
        )
    }
}
