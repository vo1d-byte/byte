package dev.byte.ide.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.byte.ide.core.Native

data class TabInfo(val path: String, val dirty: Boolean)

/** UI-side mirror of the Rust session: the buffer of truth lives in Rust. */
class EditorState(private val session: Long) {
    var value by mutableStateOf(TextFieldValue(""))
        private set
    var tabs by mutableStateOf(listOf<TabInfo>())
        private set
    var activeIndex by mutableStateOf(-1)
        private set
    var selectMode by mutableStateOf(false)

    fun open(path: String) {
        Native.tabOpen(session, path)
        refresh()
    }

    fun activate(idx: Int) {
        Native.tabActivate(session, idx)
        refresh()
    }

    fun close(idx: Int) {
        Native.tabClose(session, idx)
        refresh()
    }

    /** Called from the text field on every change or selection move. */
    fun onValueChange(v: TextFieldValue) {
        if (activeIndex < 0) return
        Native.bufSync(session, v.text, v.selection.start, v.selection.end)
        value = v
        refreshTabs()
    }

    fun op(name: String, arg: String = "") {
        if (activeIndex < 0) return
        Native.bufOp(session, name, arg)
        pull()
        refreshTabs()
    }

    fun moveCursor(dx: Int, dy: Int) {
        if (dx != 0) op("moveH", "$dx,${if (selectMode) 1 else 0}")
        if (dy != 0) op("moveV", "$dy,${if (selectMode) 1 else 0}")
    }

    fun autosave(): Int {
        val n = Native.saveDirty(session)
        if (n > 0) refreshTabs()
        return n
    }

    private fun pull() {
        val text = Native.bufText(session)
        val (a, h) = Native.bufSelection(session).split(",").map { it.toIntOrNull() ?: 0 }
        value = TextFieldValue(text, TextRange(a.coerceAtMost(text.length), h.coerceAtMost(text.length)))
    }

    private fun refreshTabs() {
        val lines = Native.tabList(session).lines()
        activeIndex = lines.firstOrNull()?.toIntOrNull() ?: -1
        tabs = lines.drop(1).filter { it.contains('|') }.map {
            TabInfo(path = it.substringAfter('|'), dirty = it.startsWith("1|"))
        }
        if (tabs.isEmpty()) activeIndex = -1
    }

    private fun refresh() {
        refreshTabs()
        if (activeIndex >= 0) pull() else value = TextFieldValue("")
    }
}
