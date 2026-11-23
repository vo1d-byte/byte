//! Stateful VT terminal emulation backed by the vt100 parser.
//!
//! PTY output is a byte stream, not plain text. Feed every chunk into this
//! parser in order; do not decode chunks independently before parsing them.

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct TerminalSnapshot {
    pub contents: String,
    pub cursor_row: u16,
    pub cursor_column: u16,
    pub rows: u16,
    pub columns: u16,
}

pub struct TerminalEmulator {
    parser: vt100::Parser,
    rows: u16,
    columns: u16,
    scrollback: usize,
}

impl TerminalEmulator {
    pub fn new(rows: u16, columns: u16, scrollback: usize) -> Self {
        let rows = rows.max(1);
        let columns = columns.max(1);
        Self { parser: vt100::Parser::new(rows, columns, scrollback), rows, columns, scrollback }
    }

    /// Feed raw PTY bytes. The parser retains state across split escape sequences.
    pub fn process(&mut self, bytes: &[u8]) {
        self.parser.process(bytes);
    }

    /// Resize the virtual screen; the caller must resize the PTY as well.
    pub fn resize(&mut self, rows: u16, columns: u16) {
        self.rows = rows.max(1);
        self.columns = columns.max(1);
        self.parser.set_size(self.rows, self.columns);
    }

    pub fn snapshot(&self) -> TerminalSnapshot {
        let screen = self.parser.screen();
        let (cursor_row, cursor_column) = screen.cursor_position();
        TerminalSnapshot {
            contents: screen.contents(),
            cursor_row,
            cursor_column,
            rows: self.rows,
            columns: self.columns,
        }
    }

    pub fn dimensions(&self) -> (u16, u16) { (self.rows, self.columns) }
    pub fn scrollback_limit(&self) -> usize { self.scrollback }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn interprets_carriage_return_and_line_feed() {
        let mut terminal = TerminalEmulator::new(4, 12, 100);
        terminal.process(b"hello\r\nworld");
        let snapshot = terminal.snapshot();
        assert!(snapshot.contents.contains("hello"));
        assert!(snapshot.contents.contains("world"));
    }

    #[test]
    fn parser_state_survives_split_escape_sequences() {
        let mut terminal = TerminalEmulator::new(4, 12, 100);
        terminal.process(b"\x1b[");
        terminal.process(b"2Jready");
        assert!(terminal.snapshot().contents.contains("ready"));
    }

    #[test]
    fn resize_clamps_zero_dimensions() {
        let mut terminal = TerminalEmulator::new(0, 0, 10);
        assert_eq!(terminal.dimensions(), (1, 1));
        terminal.resize(0, 80);
        assert_eq!(terminal.dimensions(), (1, 80));
    }

    #[test]
    fn cursor_position_tracks_printed_text() {
        let mut terminal = TerminalEmulator::new(5, 20, 10);
        terminal.process(b"abc");
        let snapshot = terminal.snapshot();
        assert_eq!(snapshot.cursor_row, 0);
        assert_eq!(snapshot.cursor_column, 3);
    }
}
