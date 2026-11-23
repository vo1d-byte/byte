# Terminal Emulator Engine

Byte's PTY produces a byte stream containing ordinary text and terminal control
sequences. This module interprets that stream as a stateful VT-compatible screen.

## Current implementation

- Persistent vt100 parser; escape sequences may be split across PTY reads.
- Screen contents and cursor-position snapshots.
- Screen resizing with non-zero dimension clamping.
- Configurable scrollback capacity.
- Unit tests for CR/LF, split CSI sequences, cursor movement, and resizing.

## Integration contract

1. Read raw bytes from the PTY.
2. Feed each chunk, in order, to TerminalEmulator::process.
3. Request a snapshot for rendering.
4. When the viewport changes, resize both the emulator and the PTY. Resizing
   the PTY notifies the foreground terminal application to redraw.

## Deliberate limitations

This is the Rust screen-state foundation, not yet a complete Android terminal UI.
The current JNI PTY bridge still exposes text polling; it is not yet wired to
TerminalEmulator. Compose still displays the legacy output text. Cell-level
color/style rendering, selection, clipboard integration, alternate-screen UI
validation, and real-device tests remain follow-up work.

Do not decode PTY chunks as UTF-8 before parsing them: multibyte characters and
escape sequences can be split between reads.


## Compose rendering surface

The Android output panel now uses a dedicated `TerminalScreen` composable with
a dark terminal surface, monospace text, automatic scroll-to-bottom, and text
selection/copy support. This is still a text-stream renderer: it does not yet
consume native screen snapshots or render per-cell ANSI attributes/cursor state.


## ANSI SGR styling in Compose

The Compose terminal surface now interprets common SGR style sequences:
- Standard and bright 16-color foreground/background.
- 256-color indexed palette.
- 24-bit RGB foreground/background.
- Bold, italic, and underline toggles and resets.
- Text selection and automatic scroll-to-bottom remain available.

This is an output-stream styling layer, not a full VT screen renderer.
Cursor addressing, erase commands, alternate-screen state, terminal input
protocols, wide-cell layout, and native parser/JNI snapshots are not yet
implemented in the Android UI.


## PTY-to-VT integration (initial)

The PTY reader now preserves raw byte chunks instead of decoding each read as
lossy UTF-8. JNI drains those bytes into a per-session `TerminalEmulator`
instance and returns the parser's current screen contents to Kotlin.
Terminal resize updates both the PTY window size and the VT parser dimensions.
The Android session polls screen snapshots and replaces the displayed screen
rather than appending repeated full-screen frames.

### Current boundaries

- The JNI snapshot currently exports plain screen contents only. Cursor metadata
  is available through a native cursor-info method, but the Compose view does not
  yet draw the cursor.
- Per-cell foreground/background/style attributes are not exported yet. In
  native VT mode, ANSI SGR sequences are consumed by the VT parser, so the
  Compose stream-based SGR styling is not a substitute for cell-style export.
- Unicode grapheme/wide-cell rendering, alternate-screen visual validation,
  keyboard protocol coverage, and real-device tests remain outstanding.
- The pipe fallback remains a text stream and is not VT-equivalent.


## Native cell-grid renderer

The Rust VT parser now serializes the active screen as JSON containing row/column
dimensions, cursor coordinates, cell text, foreground/background colors (default,
indexed, or RGB), and compact style flags (bold, italic, underline, inverse,
strikethrough). JNI exposes this through `nativePollGrid`. The Android session
polls the grid and Compose draws cells with a monospace native Canvas renderer,
including color backgrounds and a cursor outline.

### Current limitations

- The JSON grid is a full-frame snapshot on each poll, not a dirty-cell/diff protocol.
- Cursor is outlined; blinking and block/bar/underline cursor modes are not yet implemented.
- Wide Unicode grapheme clusters and continuation cells need dedicated width handling.
- Selection/copy is not yet mapped to cell coordinates in grid mode.
- The native cell grid exports styling, so the old stream SGR parser is no longer the
  source of truth in native mode. The plain-text fallback remains intentionally basic.
- The renderer has not yet been built or exercised on Android hardware; Cargo/Gradle
  compilation and VT compatibility testing remain required.


## Terminal polling performance pass

The JNI grid polling path now sends one initial blank frame and skips JSON
serialization on subsequent idle polling ticks when the PTY has produced no
new bytes. This reduces avoidable JSON allocation and JNI string traffic while
the shell is idle. It is not yet a dirty-cell protocol: every poll that contains
new PTY bytes still serializes the full active grid.

The Kotlin source line-break literals and Rust VT test escape sequences were
also corrected during this pass. This change has been source-verified on GitHub,
but Cargo tests, Gradle compilation, Android device rendering, Unicode wide-cell
behavior, cursor blinking, and cell-based selection/copy have not been run or
completed.


## Cursor and Unicode rendering pass

The Compose cell renderer now animates the cursor outline with a low-cost alpha
transition. The Rust VT layer includes a regression test asserting that CJK and
emoji text survive PTY byte parsing into the screen snapshot. The renderer draws
the complete text returned for each cell, allowing a wide glyph to use its
measured glyph width rather than truncating it to a single character.

### Remaining correctness and performance work

- This is not complete grapheme-cluster or bidirectional Arabic shaping support.
  Contextual joining across adjacent terminal cells still needs a row-run shaping
  strategy and visual tests on Android.
- The cursor currently blinks as an outline; DECSCUSR block/bar/underline modes
  and the terminal's cursor-visibility state are not exported yet.
- Dirty-cell diffs, cell-coordinate selection/copy/paste, and clipboard integration
  are not implemented in this pass.
- Cargo tests and Android Gradle/device validation remain unexecuted.


## Incremental cell updates

The Rust terminal engine now emits an initial full frame followed by cell-level
deltas. Each changed-cell record carries its row, column, and styled cell value;
cursor coordinates and grid dimensions accompany every frame. Resizing resets
the diff baseline so the next frame is full again.

JNI forwards these delta frames. The Compose renderer keeps the last full grid
and applies each changed-cell record to its local grid state inside a
`LaunchedEffect`, avoiding full-screen JSON transfer for ordinary output bursts.

### Current boundaries

- The Rust implementation currently builds a fresh in-memory grid and compares
  cells to detect changes. It reduces serialized JNI payload size, but does not
  yet avoid scanning every cell or allocating the temporary full grid.
- Terminal selection, touch-drag selection, clipboard copy/paste, and cursor
  block/bar/underline modes are not implemented here.
- Full build and device validation are still required; no Cargo/Gradle result is
  claimed by this source update.

## Terminal selection and Android clipboard

The Android terminal grid now accepts touch-drag selection in cell coordinates.
The selected cell text is exposed to the terminal panel, where Copy writes it
to Android's system clipboard and Paste sends clipboard text to the active
terminal input stream through the existing PTY write API. The same paste path
uses the pipe-backed shell writer when native PTY support is unavailable.

Selection is currently a basic rectangular row-span interaction: it does not
yet provide word/line snapping, long-press handles, wide-cell/grapheme-aware
mapping, or selection in the plain-text fallback renderer. Clipboard paste
sends text as terminal input; applications may interpret pasted control
characters, so paste should be treated as user-authorized input. Build and
device validation remain outstanding.

## Cursor shape controls

The Android terminal renderer now supports user-selectable Block, Bar, and
Underline cursor shapes. The existing blink animation is retained for all
three shapes. These controls are a renderer preference; they do not yet mirror
terminal applications' DECSCUSR cursor-shape escape mode or DECTCEM visibility
state. The renderer continues to use the cursor coordinates supplied by the
native VT grid. Full terminal-driven cursor mode synchronization remains future
compatibility work.
