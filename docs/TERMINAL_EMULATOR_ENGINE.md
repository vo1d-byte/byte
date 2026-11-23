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
