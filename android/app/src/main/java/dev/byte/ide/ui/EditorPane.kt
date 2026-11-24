package dev.byte.ide.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One tab strip (spec section 5): tap to switch, + for a new file, dot = unsaved. */
@Composable
fun FileTabs(editor: EditorState, onNewFile: () -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier.fillMaxWidth().height(44.dp).background(C.surface),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(editor.tabs) { i, tab ->
            val active = i == editor.activeIndex
            Row(
                Modifier
                    .height(44.dp)
                    .clickable { editor.activate(i) }
                    .background(if (active) C.bg else C.surface)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(tab.path.substringAfterLast('/'), color = if (active) C.text else C.muted, fontSize = 15.sp)
                if (tab.dirty) Box(Modifier.size(6.dp).clip(CircleShape).background(C.accent))
            }
        }
        item {
            KeyButton("+", description = "New file", background = C.surface, onClick = onNewFile)
        }
    }
}

@Composable
fun EditorPane(editor: EditorState, onTyped: () -> Unit, modifier: Modifier = Modifier) {
    if (editor.activeIndex < 0) {
        Box(modifier.fillMaxSize().background(C.bg), contentAlignment = Alignment.Center) {
            Text("Open a file with +", color = C.muted)
        }
        return
    }
    val vScroll = rememberScrollState()
    val lines = editor.value.text.count { it == '\n' } + 1
    Row(modifier.fillMaxSize().background(C.bg).verticalScroll(vScroll)) {
        Text(
            (1..lines).joinToString("\n"),
            style = Mono,
            color = C.muted,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 40.dp).padding(horizontal = 8.dp),
        )
        BasicTextField(
            value = editor.value,
            onValueChange = {
                if (it.text != editor.value.text) onTyped()
                editor.onValueChange(it)
            },
            textStyle = Mono,
            cursorBrush = SolidColor(C.accent),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(end = 24.dp, bottom = 120.dp),
        )
    }
}
