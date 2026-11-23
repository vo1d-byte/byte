package dev.byteide.app

import androidx.compose.runtime.Stable
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * View-owned editor state. Output-panel visibility and device orientation must
 * never recreate this object: keeping it stable preserves caret and selection.
 *
 * TextFieldValue uses UTF-16 offsets, matching Android/Compose APIs. The Rust
 * document engine uses Unicode scalar offsets; the JNI adapter must translate.
 */
@Stable
class EditorSession(
    initialText: String,
    val filePath: String
) {
    var value: TextFieldValue = TextFieldValue(
        text = initialText,
        selection = TextRange(initialText.length)
    )
        private set

    var revision: Long = 0
        private set

    var isDirty: Boolean = false
        private set

    var firstVisibleLine: Int = 0
        private set

    fun applyValue(next: TextFieldValue) {
        if (next.text != value.text) {
            revision += 1
            isDirty = true
        }
        value = next
    }

    fun markSaved() {
        isDirty = false
    }

    fun updateViewport(firstLine: Int) {
        firstVisibleLine = firstLine.coerceAtLeast(0)
    }

    fun restoreSelection(selection: TextRange) {
        val end = value.text.length
        value = value.copy(
            selection = TextRange(
                selection.start.coerceIn(0, end),
                selection.end.coerceIn(0, end)
            )
        )
    }
}
