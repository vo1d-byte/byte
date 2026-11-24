package dev.byte.ide.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutStateTest {
    private fun input(
        landscape: Boolean = false,
        ime: Boolean = false,
        cursor: Boolean = false,
        tab: OutputTab = OutputTab.Output,
        termFocus: Boolean = false,
        snap: PanelSnap? = null,
        peek: Boolean = false,
        error: Boolean = false,
        settings: FoldSettings = FoldSettings(),
    ) = LayoutInput(landscape, ime, cursor, tab, termFocus, snap, peek, error, settings)

    @Test fun reviewPortraitShowsQuarterPanel() {
        val l = computeLayout(input())
        assertFalse(l.folded)
        assertEquals(0.25f, l.panelFraction, 0f)
        assertNull(l.keyRow)
    }

    @Test fun typingPortraitFoldsOutputButKeepsTabs() {
        val l = computeLayout(input(ime = true))
        assertTrue(l.folded)
        assertFalse(l.fileTabsFolded)
        assertEquals(KeyRowKind.Code, l.keyRow)
    }

    @Test fun typingLandscapeFoldsBoth() {
        val l = computeLayout(input(landscape = true, ime = true))
        assertTrue(l.folded)
        assertTrue(l.fileTabsFolded)
    }

    @Test fun terminalTabWidensPanelAndSwapsKeyRow() {
        assertEquals(0.40f, computeLayout(input(tab = OutputTab.Terminal)).panelFraction, 0f)
        val typing = computeLayout(input(ime = true, tab = OutputTab.Terminal, termFocus = true))
        assertEquals(KeyRowKind.Terminal, typing.keyRow)
        assertFalse(typing.folded)
        assertEquals(0.40f, typing.panelFraction, 0f)
    }

    @Test fun cursorEditorFoldsLikeKeyboard() {
        assertTrue(computeLayout(input(cursor = true)).folded)
        val off = FoldSettings(foldInCursorMode = false)
        assertFalse(computeLayout(input(cursor = true, settings = off)).folded)
    }

    @Test fun autoFoldOffNeverFolds() {
        val l = computeLayout(input(ime = true, settings = FoldSettings(autoFold = AutoFold.Off)))
        assertFalse(l.folded)
    }

    @Test fun landscapeOnlySkipsPortrait() {
        val s = FoldSettings(autoFold = AutoFold.LandscapeOnly)
        assertFalse(computeLayout(input(ime = true, settings = s)).folded)
        assertTrue(computeLayout(input(ime = true, landscape = true, settings = s)).folded)
    }

    @Test fun peekOnlyWhenFolded() {
        assertFalse(computeLayout(input(peek = true)).peekVisible)
        assertTrue(computeLayout(input(ime = true, peek = true)).peekVisible)
    }

    @Test fun userSnapOverridesAndErrorDotAlwaysShows() {
        val l = computeLayout(input(snap = PanelSnap.Collapsed, error = true))
        assertEquals(0f, l.panelFraction, 0f)
        assertTrue(l.showErrorDot)
        assertEquals(0.80f, computeLayout(input(snap = PanelSnap.Expanded)).panelFraction, 0f)
    }
}
