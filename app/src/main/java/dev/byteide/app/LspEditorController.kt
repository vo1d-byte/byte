package dev.byteide.app

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import java.io.Closeable

/**
 * Binds one EditorSession to the workspace LSP manager.
 *
 * Completion is debounced and stale responses are discarded by generation number.
 * The controller never invents suggestions: its state is populated only by LSP.
 */
class LspEditorController(
    private val editor: EditorSession,
    private val workspace: LspWorkspaceManager,
    val documentUri: String,
    val languageId: String,
    private val onDefinition: (List<LspLocation>) -> Unit = {}
) : Closeable {
    private val handler = Handler(Looper.getMainLooper())
    private var completionGeneration = 0L
    private var completionRunnable: Runnable? = null
    private var opened = false
    private var lastSyncedText = editor.value.text

    var uiState by mutableStateOf(LspEditorUiState())
        private set

    var connectionStatus by mutableStateOf("Connecting")
        private set

    init {
        opened = workspace.openDocument(documentUri, languageId, 1, editor.value.text)
        connectionStatus = if (opened) "Connecting" else "Language server unavailable"
    }

    /** Call after every editor value update, including edits from the soft-key row. */
    fun onEditorValueChanged(value: TextFieldValue, requestCompletion: Boolean = true) {
        if (!opened) return
        if (value.text != lastSyncedText) {
            workspace.changeDocument(documentUri, nextVersion(), value.text)
            lastSyncedText = value.text
        }
        if (requestCompletion) scheduleCompletion(value)
    }

    fun requestCompletionNow(value: TextFieldValue = editor.value) {
        if (!opened) return
        completionRunnable?.let(handler::removeCallbacks)
        val generation = ++completionGeneration
        uiState = uiState.copy(busy = true)
        val position = value.toLspPosition(value.selection.start)
        val accepted = workspace.completion(documentUri, position) { items ->
            if (generation != completionGeneration) return@completion
            uiState = uiState.copy(completions = items, completionVisible = items.isNotEmpty(), busy = false)
        }
        if (!accepted) uiState = uiState.copy(busy = false, completionVisible = false)
    }

    fun requestHover(value: TextFieldValue = editor.value) {
        if (!opened) return
        workspace.hover(documentUri, value.toLspPosition(value.selection.start)) { hover ->
            uiState = uiState.copy(hover = hover)
        }
    }

    fun requestDefinition(value: TextFieldValue = editor.value) {
        if (!opened) return
        workspace.definition(documentUri, value.toLspPosition(value.selection.start), onDefinition)
    }

    fun selectCompletion(item: LspCompletionItem) {
        val current = editor.value
        val caret = current.selection.start.coerceIn(0, current.text.length)
        var start = caret
        while (start > 0 && (current.text[start - 1].isLetterOrDigit() || current.text[start - 1] == '_')) start--
        val insertion = item.insertText ?: item.label
        val updatedText = current.text.replaceRange(start, caret, insertion)
        val cursor = start + insertion.length
        editor.applyValue(TextFieldValue(updatedText, TextRange(cursor)))
        workspace.changeDocument(documentUri, nextVersion(), updatedText)
        lastSyncedText = updatedText
        completionGeneration++
        uiState = uiState.copy(completionVisible = false, completions = emptyList(), busy = false)
    }

    fun dismissCompletion() {
        completionGeneration++
        completionRunnable?.let(handler::removeCallbacks)
        completionRunnable = null
        uiState = uiState.copy(completionVisible = false, busy = false)
    }

    fun updateDiagnostics(items: List<LspDiagnostic>) {
        uiState = uiState.copy(diagnostics = items)
    }

    private fun scheduleCompletion(value: TextFieldValue) {
        completionRunnable?.let(handler::removeCallbacks)
        val generation = ++completionGeneration
        val task = Runnable {
            if (generation == completionGeneration) requestCompletionNow(value)
        }
        completionRunnable = task
        handler.postDelayed(task, COMPLETION_DEBOUNCE_MS)
    }

    private var documentVersion = 1
    private fun nextVersion(): Int = ++documentVersion

    override fun close() {
        completionGeneration++
        completionRunnable?.let(handler::removeCallbacks)
        completionRunnable = null
        if (opened) workspace.closeDocument(documentUri)
        opened = false
        connectionStatus = "Closed"
    }

    private fun TextFieldValue.toLspPosition(offset: Int): LspPosition {
        val safe = offset.coerceIn(0, text.length)
        val line = text.substring(0, safe).count { it == '\n' }
        val lineStart = text.lastIndexOf('\n', (safe - 1).coerceAtLeast(0)).let {
            if (it < 0) 0 else it + 1
        }
        return LspPosition(line, safe - lineStart)
    }

    companion object {
        private const val COMPLETION_DEBOUNCE_MS = 180L
    }
}
