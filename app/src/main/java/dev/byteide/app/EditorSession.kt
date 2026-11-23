package dev.byteide.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Stable view-owned state for a single editor tab. Compose offsets are UTF-16;
 * native Rust document offsets are Unicode scalar values (code points).
 */
@Stable
class EditorSession(
    initialText: String,
    val filePath: String
) : AutoCloseable {
    private val nativeDocument = NativeEditorDocument(initialText)
    private val syntaxDocument = NativeSyntaxDocument(filePath, initialText)

    var syntaxSpans: List<EditorSyntaxSpan> by mutableStateOf(syntaxDocument.spans())
        private set

    var syntaxDiagnostics: List<EditorSyntaxDiagnostic> by mutableStateOf(syntaxDocument.diagnostics())
        private set

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

    val usesNativeEngine: Boolean get() = nativeDocument.isNative

    fun applyValue(next: TextFieldValue) {
        if (next.text != value.text) {
            syncIncrementalEdit(value.text, next.text)
            revision += 1
            isDirty = true
        }
        value = next
        nativeDocument.setSelection(
            utf16ToCodePoint(value.text, value.selection.start),
            utf16ToCodePoint(value.text, value.selection.end)
        )
    }

    private fun syncIncrementalEdit(old: String, new: String) {
        

        var prefix = 0
        val maxPrefix = minOf(old.length, new.length)
        while (prefix < maxPrefix && old[prefix] == new[prefix]) prefix++
        // Never split a UTF-16 surrogate pair.
        if (prefix > 0 && prefix < old.length && old[prefix].isLowSurrogate()) prefix--
        if (prefix > 0 && prefix < new.length && new[prefix].isLowSurrogate()) prefix--

        var oldSuffix = old.length
        var newSuffix = new.length
        while (oldSuffix > prefix && newSuffix > prefix &&
            old[oldSuffix - 1] == new[newSuffix - 1]) {
            oldSuffix--
            newSuffix--
        }
        if (oldSuffix < old.length && old[oldSuffix].isLowSurrogate()) oldSuffix++
        if (newSuffix < new.length && new[newSuffix].isLowSurrogate()) newSuffix++

        val startCp = utf16ToCodePoint(old, prefix)
        val endCp = utf16ToCodePoint(old, oldSuffix)
        if (nativeDocument.isNative) nativeDocument.replace(startCp, endCp, new.substring(prefix, newSuffix))
        val startByte = old.substring(0, prefix).toByteArray(Charsets.UTF_8).size
        val endByte = old.substring(0, oldSuffix).toByteArray(Charsets.UTF_8).size
        syntaxDocument.replace(startByte, endByte, new.substring(prefix, newSuffix))
        syntaxSpans = syntaxDocument.spans()
        syntaxDiagnostics = syntaxDocument.diagnostics()
    }

    fun undo(): Boolean {
        if (!nativeDocument.isNative) return false
        val updated = nativeDocument.undo(value.text)
        if (updated != value.text) {
            value = TextFieldValue(updated, TextRange(updated.length))
            revision++
            isDirty = true
            return true
        }
        return false
    }

    fun redo(): Boolean {
        if (!nativeDocument.isNative) return false
        val updated = nativeDocument.redo(value.text)
        if (updated != value.text) {
            value = TextFieldValue(updated, TextRange(updated.length))
            revision++
            isDirty = true
            return true
        }
        return false
    }

    fun markSaved() {
        isDirty = false
    }

    fun updateViewport(firstLine: Int) {
        firstVisibleLine = firstLine.coerceAtLeast(0)
    }

    fun restoreSelection(selection: TextRange) {
        val end = value.text.length
        val safe = TextRange(
            selection.start.coerceIn(0, end),
            selection.end.coerceIn(0, end)
        )
        value = value.copy(selection = safe)
        nativeDocument.setSelection(
            utf16ToCodePoint(value.text, safe.start),
            utf16ToCodePoint(value.text, safe.end)
        )
    }

    override fun close() {
        nativeDocument.close()
        syntaxDocument.close()
    }

    private fun utf16ToCodePoint(text: String, offset: Int): Int =
        text.codePointCount(0, offset.coerceIn(0, text.length))
}
