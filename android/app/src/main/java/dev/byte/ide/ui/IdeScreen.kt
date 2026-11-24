package dev.byte.ide.ui

import android.content.res.Configuration
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun IdeScreen(editor: EditorState, terminal: TerminalSession, runner: Runner) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val imeVisible = WindowInsets.isImeVisible
    val keyboard = LocalSoftwareKeyboardController.current
    val settings = remember { FoldSettings() }

    var cursorOpen by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(OutputTab.Output) }
    var terminalFocused by remember { mutableStateOf(false) }
    var userSnap by remember { mutableStateOf<PanelSnap?>(null) }
    var peek by remember { mutableStateOf(false) }
    var newFileCounter by remember { mutableStateOf(1) }

    // Auto-save every 1.5 s (spec section 5).
    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            editor.autosave()
        }
    }
    // The cursor editor replaces the system keyboard, never both.
    LaunchedEffect(imeVisible) { if (imeVisible) cursorOpen = false }
    LaunchedEffect(cursorOpen) { if (cursorOpen) keyboard?.hide() }

    val hasError = problemLines(terminal.output).isNotEmpty()
    val layout = computeLayout(
        LayoutInput(
            landscape = landscape,
            imeVisible = imeVisible,
            cursorEditorOpen = cursorOpen,
            activeTab = activeTab,
            terminalFocused = terminalFocused && activeTab == OutputTab.Terminal,
            userSnap = userSnap,
            peekOpen = peek,
            hasError = hasError,
            settings = settings,
        ),
    )
    val typing = layout.keyRow != null
    val activeFile = editor.tabs.getOrNull(editor.activeIndex)?.path ?: "No file"

    fun snapStep(step: Int) {
        val order = PanelSnap.values()
        val current = userSnap ?: PanelSnap.Normal
        userSnap = order[(current.ordinal + step).coerceIn(0, order.lastIndex)]
    }

    fun runAction(a: Action) {
        when (a) {
            Action.Run -> {
                editor.autosave()
                if (editor.activeIndex >= 0) runner.run(activeFile)
                activeTab = OutputTab.Output
                if (userSnap == PanelSnap.Collapsed) userSnap = null
            }
            Action.Undo -> editor.op("undo")
            Action.Redo -> editor.op("redo")
            Action.CommentLine -> editor.op("toggleComment", Runner.commentPrefix(activeFile))
            Action.DuplicateLine -> editor.op("duplicateLine")
            Action.Save -> editor.autosave()
            Action.Terminal -> { activeTab = OutputTab.Terminal; userSnap = null }
            else -> runner.note("${a.label} is not implemented yet (see README roadmap).")
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(C.bg).systemBarsPadding().imePadding()) {
        val totalH = maxHeight
        val totalW = maxWidth
        val panelH by animateDpAsState(totalH * layout.panelFraction, tween(180), label = "panelH")
        val panelW by animateDpAsState(totalW * layout.panelFraction, tween(180), label = "panelW")

        val panel: @Composable (Modifier) -> Unit = { m ->
            OutputPanel(
                activeTab = activeTab,
                onTab = { activeTab = it },
                terminal = terminal,
                runner = runner,
                onTerminalFocus = { terminalFocused = it },
                onSnapStep = ::snapStep,
                modifier = m,
            )
        }
        val foldedLine: @Composable () -> Unit = {
            FoldedLine(
                fileName = activeFile,
                hasError = layout.showErrorDot,
                hint = if (peek) "tap to close" else "tap to peek",
                onClick = { peek = !peek },
            )
        }

        @Composable
        fun editorColumn(modifier: Modifier) {
            Column(modifier) {
                if (!layout.fileTabsFolded) {
                    FileTabs(editor, onNewFile = {
                        editor.open("untitled-${newFileCounter++}.txt")
                    })
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    EditorPane(editor, onTyped = { if (settings.closePeekWhenTyping) peek = false })
                    if (layout.peekVisible) {
                        // Drawn over the editor: the code underneath never moves.
                        panel(Modifier.fillMaxWidth().height(totalH * 0.4f))
                    }
                }
                when {
                    cursorOpen -> CursorEditor(editor, onBackToKeys = { cursorOpen = false })
                    layout.keyRow != null -> KeyRow(
                        kind = layout.keyRow,
                        editor = editor,
                        terminal = terminal,
                        onCursorEditor = { cursorOpen = true },
                    )
                }
            }
        }

        if (landscape) {
            Row(Modifier.fillMaxSize()) {
                if (!layout.folded && panelW > 0.dp) panel(Modifier.width(panelW).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (layout.folded || layout.panelFraction == 0f) foldedLine()
                    editorColumn(Modifier.weight(1f).fillMaxWidth())
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                if (layout.folded || layout.panelFraction == 0f) foldedLine()
                else panel(Modifier.fillMaxWidth().height(panelH))
                editorColumn(Modifier.weight(1f).fillMaxWidth())
            }
        }

        FloatingActions(
            layer1 = DEFAULT_LAYER1,
            layer2 = DEFAULT_LAYER2,
            longPress = Action.Run,
            typing = typing,
            bottomInsetDp = if (typing && !cursorOpen) 44 else 0,
            onAction = ::runAction,
        )
    }
}
