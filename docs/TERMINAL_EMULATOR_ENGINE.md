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
