//! Stateful VT terminal emulation and serializable cell-grid snapshots.

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

    pub fn process(&mut self, bytes: &[u8]) { self.parser.process(bytes); }

    pub fn resize(&mut self, rows: u16, columns: u16) {
        self.rows = rows.max(1);
        self.columns = columns.max(1);
        self.parser.set_size(self.rows, self.columns);
    }

    pub fn snapshot(&self) -> TerminalSnapshot {
        let screen = self.parser.screen();
        let (cursor_row, cursor_column) = screen.cursor_position();
        TerminalSnapshot { contents: screen.contents(), cursor_row, cursor_column, rows: self.rows, columns: self.columns }
    }

    /// Serialize the active VT screen as a compact JSON grid. Color values use
    /// [kind, r, g, b]: kind 0=default, 1=indexed (r=index), 2=RGB.
    pub fn grid_json(&self) -> String {
        use serde_json::{json, Value};
        let screen = self.parser.screen();
        let (cursor_row, cursor_column) = screen.cursor_position();
        let mut rows: Vec<Value> = Vec::with_capacity(self.rows as usize);
        for row in 0..self.rows {
            let mut cells: Vec<Value> = Vec::with_capacity(self.columns as usize);
            for column in 0..self.columns {
                if let Some(cell) = screen.cell(row, column) {
                    let fg = match cell.fgcolor() {
                        vt100::Color::Default => json!([0, 0, 0, 0]),
                        vt100::Color::Idx(index) => json!([1, index, 0, 0]),
                        vt100::Color::Rgb(r, g, b) => json!([2, r, g, b]),
                    };
                    let bg = match cell.bgcolor() {
                        vt100::Color::Default => json!([0, 0, 0, 0]),
                        vt100::Color::Idx(index) => json!([1, index, 0, 0]),
                        vt100::Color::Rgb(r, g, b) => json!([2, r, g, b]),
                    };
                    let attr = cell.attributes();
                    let flags = (if attr.bold() { 1 } else { 0 })
                        | (if attr.italic() { 2 } else { 0 })
                        | (if attr.underline() { 4 } else { 0 })
                        | (if attr.inverse() { 8 } else { 0 })
                        | (if attr.strikethrough() { 16 } else { 0 });
                    cells.push(json!({"t": cell.contents(), "f": fg, "b": bg, "a": flags}));
                } else {
                    cells.push(json!({"t":" ", "f":[0,0,0,0], "b":[0,0,0,0], "a":0}));
                }
            }
            rows.push(json!(cells));
        }
        json!({"rows":self.rows,"columns":self.columns,"cursor":{"row":cursor_row,"column":cursor_column},"grid":rows}).to_string()
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

    #[test]
    fn grid_snapshot_contains_dimensions_cursor_and_cells() {
        let mut terminal = TerminalEmulator::new(2, 8, 10);
        terminal.process(b"\x1b[31mred");
        let grid: serde_json::Value = serde_json::from_str(&terminal.grid_json()).unwrap();
        assert_eq!(grid["rows"], 2);
        assert_eq!(grid["columns"], 8);
        assert_eq!(grid["grid"][0][0]["t"], "r");
        assert_eq!(grid["grid"][0][0]["f"][0], 1);
    }
}
