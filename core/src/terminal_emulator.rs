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
    previous_grid: Option<Vec<Vec<serde_json::Value>>>,
}

impl TerminalEmulator {
    pub fn new(rows: u16, columns: u16, scrollback: usize) -> Self {
        let rows = rows.max(1);
        let columns = columns.max(1);
        Self { parser: vt100::Parser::new(rows, columns, scrollback), rows, columns, scrollback, previous_grid: None }
    }

    pub fn process(&mut self, bytes: &[u8]) { self.parser.process(bytes); }

    pub fn resize(&mut self, rows: u16, columns: u16) {
        self.rows = rows.max(1);
        self.columns = columns.max(1);
        self.parser.set_size(self.rows, self.columns);
        self.previous_grid = None;
    }

    pub fn snapshot(&self) -> TerminalSnapshot {
        let screen = self.parser.screen();
        let (cursor_row, cursor_column) = screen.cursor_position();
        TerminalSnapshot { contents: screen.contents(), cursor_row, cursor_column, rows: self.rows, columns: self.columns }
    }

    fn cell_json(cell: Option<vt100::Cell>) -> serde_json::Value {
        use serde_json::json;
        if let Some(cell) = cell {
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
            json!({"t":cell.contents(),"f":fg,"b":bg,"a":flags})
        } else {
            json!({"t":" ","f":[0,0,0,0],"b":[0,0,0,0],"a":0})
        }
    }

    fn current_grid(&self) -> Vec<Vec<serde_json::Value>> {
        let screen = self.parser.screen();
        (0..self.rows).map(|row| {
            (0..self.columns).map(|column| Self::cell_json(screen.cell(row, column))).collect()
        }).collect()
    }

    /// Initial frame is full; subsequent frames contain only changed cells and cursor metadata.
    pub fn grid_delta_json(&mut self) -> String {
        use serde_json::json;
        let grid = self.current_grid();
        let (cursor_row, cursor_column) = self.parser.screen().cursor_position();
        let full = self.previous_grid.is_none();
        let mut changed = Vec::new();
        for row in 0..self.rows as usize {
            for column in 0..self.columns as usize {
                let differs = self.previous_grid.as_ref()
                    .and_then(|old| old.get(row).and_then(|line| line.get(column)))
                    .map(|old| old != &grid[row][column])
                    .unwrap_or(true);
                if differs {
                    changed.push(json!({"r":row,"c":column,"cell":grid[row][column]}));
                }
            }
        }
        self.previous_grid = Some(grid.clone());
        if full {
            json!({"full":true,"rows":self.rows,"columns":self.columns,
                "cursor":{"row":cursor_row,"column":cursor_column},"grid":grid}).to_string()
        } else {
            json!({"full":false,"rows":self.rows,"columns":self.columns,
                "cursor":{"row":cursor_row,"column":cursor_column},"cells":changed}).to_string()
        }
    }

    /// Compatibility full-frame serializer for diagnostics and tests.
    pub fn grid_json(&self) -> String {
        use serde_json::json;
        let grid = self.current_grid();
        let (cursor_row, cursor_column) = self.parser.screen().cursor_position();
        json!({"rows":self.rows,"columns":self.columns,
            "cursor":{"row":cursor_row,"column":cursor_column},"grid":grid}).to_string()
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
    fn unicode_text_is_preserved_in_screen_snapshot() {
        let mut terminal = TerminalEmulator::new(3, 16, 10);
        terminal.process("A界🙂".as_bytes());
        let contents = terminal.snapshot().contents;
        assert!(contents.contains("A"));
        assert!(contents.contains("界"));
        assert!(contents.contains("🙂"));
    }

    #[test]
    fn grid_delta_sends_full_frame_once_then_only_changed_cells() {
        let mut terminal = TerminalEmulator::new(2, 8, 10);
        let first: serde_json::Value = serde_json::from_str(&terminal.grid_delta_json()).unwrap();
        assert_eq!(first["full"], true);
        terminal.process(b"hi");
        let delta: serde_json::Value = serde_json::from_str(&terminal.grid_delta_json()).unwrap();
        assert_eq!(delta["full"], false);
        assert_eq!(delta["cells"].as_array().unwrap().len(), 2);
        let idle: serde_json::Value = serde_json::from_str(&terminal.grid_delta_json()).unwrap();
        assert_eq!(idle["cells"].as_array().unwrap().len(), 0);
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
