package dev.byte.ide.ui

/**
 * Pure layout logic from the design spec (sections 3, 4, 9, 10).
 * No Android types here so it can be unit-tested on the JVM.
 */
enum class PanelSnap(val portraitFraction: Float) {
    Collapsed(0f), Normal(0.25f), Expanded(0.80f)
}

enum class AutoFold { Always, LandscapeOnly, Off }

enum class KeyRowKind { Code, Terminal }

enum class OutputTab(val title: String) {
    Output("Output"), Terminal("Terminal"), Problems("Problems"), Debug("Debug"), Logs("Logs")
}

data class FoldSettings(
    val autoFold: AutoFold = AutoFold.Always,
    val keepFileTabsInPortrait: Boolean = true,
    val redDotOnFoldedLine: Boolean = true,
    val tapFoldedLineToPeek: Boolean = true,
    val foldInCursorMode: Boolean = true,
    val closePeekWhenTyping: Boolean = true,
)

data class LayoutInput(
    val landscape: Boolean,
    val imeVisible: Boolean,
    val cursorEditorOpen: Boolean,
    val activeTab: OutputTab,
    val terminalFocused: Boolean,
    val userSnap: PanelSnap?,
    val peekOpen: Boolean,
    val hasError: Boolean,
    val settings: FoldSettings = FoldSettings(),
)

data class LayoutSpec(
    /** Output panel collapsed to the thin line (20 dp visual, 44 dp touch). */
    val folded: Boolean,
    /** Portrait: share of the height. Landscape: share of the width. 0 when folded/collapsed. */
    val panelFraction: Float,
    val panelAtSide: Boolean,
    val fileTabsFolded: Boolean,
    /** Panel temporarily drawn over the editor (never moves the code). */
    val peekVisible: Boolean,
    /** Extra-key row above the system keyboard, or null when not typing. */
    val keyRow: KeyRowKind?,
    val showErrorDot: Boolean,
)

const val LANDSCAPE_PANEL_FRACTION = 0.33f
const val TERMINAL_FRACTION = 0.40f

fun computeLayout(i: LayoutInput): LayoutSpec {
    val s = i.settings
    val typing = i.imeVisible || (i.cursorEditorOpen && s.foldInCursorMode)
    val foldApplies = when (s.autoFold) {
        AutoFold.Always -> true
        AutoFold.LandscapeOnly -> i.landscape
        AutoFold.Off -> false
    }
    val folded = typing && foldApplies && !i.terminalFocused

    val fraction = when {
        folded -> 0f
        i.landscape -> LANDSCAPE_PANEL_FRACTION
        i.terminalFocused && i.userSnap == null -> TERMINAL_FRACTION
        i.userSnap != null -> i.userSnap.portraitFraction
        i.activeTab == OutputTab.Terminal -> TERMINAL_FRACTION
        else -> PanelSnap.Normal.portraitFraction
    }

    return LayoutSpec(
        folded = folded,
        panelFraction = fraction,
        panelAtSide = i.landscape,
        // Portrait keeps the file tabs; landscape folds both (spec section 9).
        fileTabsFolded = folded && (i.landscape || !s.keepFileTabsInPortrait),
        peekVisible = folded && i.peekOpen && s.tapFoldedLineToPeek,
        keyRow = if (typing) {
            if (i.terminalFocused) KeyRowKind.Terminal else KeyRowKind.Code
        } else null,
        // A failed run is always visible, even when the panel is collapsed.
        showErrorDot = i.hasError && s.redDotOnFoldedLine,
    )
}
