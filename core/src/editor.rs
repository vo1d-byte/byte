//! Rope-backed text document and editing session.
//!
//! Text edits are stored as inverse operations for undo/redo. The rope keeps
//! insertions and deletions efficient for large files compared with rebuilding
//! a single contiguous String on every keystroke.

use ropey::Rope;
use std::ops::Range;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub struct Selection {
    pub anchor: usize,
    pub head: usize,
}

impl Selection {
    pub fn collapsed(at: usize) -> Self {
        Self { anchor: at, head: at }
    }

    pub fn is_empty(self) -> bool {
        self.anchor == self.head
    }

    pub fn range(self) -> Range<usize> {
        self.anchor.min(self.head)..self.anchor.max(self.head)
    }

    pub fn cursor(self) -> usize {
        self.head
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
struct Edit {
    start: usize,
    removed: String,
    inserted: String,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct LineColumn {
    pub line: usize,
    pub column: usize,
}

#[derive(Debug, Clone)]
pub struct TextDocument {
    text: Rope,
    selection: Selection,
    undo: Vec<Edit>,
    redo: Vec<Edit>,
    revision: u64,
}

impl Default for TextDocument {
    fn default() -> Self {
        Self::new("")
    }
}

impl TextDocument {
    pub fn new(text: &str) -> Self {
        let text = Rope::from_str(text);
        let end = text.len_chars();
        Self {
            text,
            selection: Selection::collapsed(end),
            undo: Vec::new(),
            redo: Vec::new(),
            revision: 0,
        }
    }

    pub fn text(&self) -> String {
        self.text.to_string()
    }

    pub fn len_chars(&self) -> usize {
        self.text.len_chars()
    }

    pub fn len_bytes(&self) -> usize {
        self.text.len_bytes()
    }

    pub fn line_count(&self) -> usize {
        self.text.len_lines()
    }

    pub fn revision(&self) -> u64 {
        self.revision
    }

    pub fn selection(&self) -> Selection {
        self.selection
    }

    pub fn set_selection(&mut self, selection: Selection) {
        let len = self.len_chars();
        self.selection = Selection {
            anchor: selection.anchor.min(len),
            head: selection.head.min(len),
        };
    }

    pub fn selected_text(&self) -> String {
        let range = self.selection.range();
        self.text.slice(range).to_string()
    }

    pub fn line_column(&self, char_index: usize) -> LineColumn {
        let index = char_index.min(self.len_chars());
        let line = self.text.char_to_line(index);
        let line_start = self.text.line_to_char(line);
        LineColumn {
            line,
            column: index - line_start,
        }
    }

    pub fn char_to_byte(&self, char_index: usize) -> usize {
        self.text.char_to_byte(char_index.min(self.len_chars()))
    }

    pub fn insert(&mut self, value: &str) {
        self.replace_selection(value);
    }

    pub fn replace_selection(&mut self, value: &str) {
        let range = self.selection.range();
        self.replace(range, value);
    }

    pub fn replace(&mut self, range: Range<usize>, value: &str) {
        let start = range.start.min(self.len_chars());
        let end = range.end.min(self.len_chars()).max(start);
        let removed = self.text.slice(start..end).to_string();
        if removed == value {
            self.selection = Selection::collapsed(start + value.chars().count());
            return;
        }

        self.text.remove(start..end);
        self.text.insert(start, value);
        let edit = Edit {
            start,
            removed,
            inserted: value.to_owned(),
        };
        self.selection = Selection::collapsed(start + value.chars().count());
        self.undo.push(edit);
        self.redo.clear();
        self.revision = self.revision.wrapping_add(1);
    }

    pub fn backspace(&mut self) {
        if !self.selection.is_empty() {
            self.replace_selection("");
        } else if self.selection.head > 0 {
            let at = self.selection.head;
            self.replace(at - 1..at, "");
        }
    }

    pub fn delete_forward(&mut self) {
        if !self.selection.is_empty() {
            self.replace_selection("");
        } else if self.selection.head < self.len_chars() {
            let at = self.selection.head;
            self.replace(at..at + 1, "");
        }
    }

    pub fn move_cursor(&mut self, delta: isize, extend: bool) {
        let len = self.len_chars() as isize;
        let next = (self.selection.head as isize + delta).clamp(0, len) as usize;
        self.selection = Selection {
            anchor: if extend { self.selection.anchor } else { next },
            head: next,
        };
    }

    pub fn move_to(&mut self, position: usize, extend: bool) {
        let next = position.min(self.len_chars());
        self.selection = Selection {
            anchor: if extend { self.selection.anchor } else { next },
            head: next,
        };
    }

    pub fn undo(&mut self) -> bool {
        let Some(edit) = self.undo.pop() else { return false };
        let inserted_len = edit.inserted.chars().count();
        self.text.remove(edit.start..edit.start + inserted_len);
        self.text.insert(edit.start, &edit.removed);
        self.selection = Selection::collapsed(edit.start + edit.removed.chars().count());
        self.redo.push(edit);
        self.revision = self.revision.wrapping_add(1);
        true
    }

    pub fn redo(&mut self) -> bool {
        let Some(edit) = self.redo.pop() else { return false };
        let removed_len = edit.removed.chars().count();
        self.text.remove(edit.start..edit.start + removed_len);
        self.text.insert(edit.start, &edit.inserted);
        self.selection = Selection::collapsed(edit.start + edit.inserted.chars().count());
        self.undo.push(edit);
        self.revision = self.revision.wrapping_add(1);
        true
    }
}

/// Per-view state deliberately lives outside TextDocument. Resizing or hiding a
/// panel should not mutate this state or the underlying document.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct EditorViewState {
    pub selection: Selection,
    pub scroll_line: usize,
    pub focused: bool,
}

impl Default for EditorViewState {
    fn default() -> Self {
        Self {
            selection: Selection::default(),
            scroll_line: 0,
            focused: false,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn inserts_and_tracks_unicode_positions() {
        let mut doc = TextDocument::new("a");
        doc.move_to(1, false);
        doc.insert("🙂é");
        assert_eq!(doc.text(), "a🙂é");
        assert_eq!(doc.len_chars(), 3);
        assert_eq!(doc.len_bytes(), 7);
        assert_eq!(doc.selection().head, 3);
    }

    #[test]
    fn selection_replacement_and_undo_redo() {
        let mut doc = TextDocument::new("hello world");
        doc.set_selection(Selection { anchor: 6, head: 11 });
        doc.insert("Byte");
        assert_eq!(doc.text(), "hello Byte");
        assert!(doc.undo());
        assert_eq!(doc.text(), "hello world");
        assert!(doc.redo());
        assert_eq!(doc.text(), "hello Byte");
    }

    #[test]
    fn backspace_deletes_one_unicode_scalar() {
        let mut doc = TextDocument::new("a🙂");
        doc.backspace();
        assert_eq!(doc.text(), "a");
    }

    #[test]
    fn line_and_column_are_zero_based() {
        let doc = TextDocument::new("one\ntwo\n");
        assert_eq!(doc.line_count(), 3);
        assert_eq!(doc.line_column(5), LineColumn { line: 1, column: 1 });
    }

    #[test]
    fn cursor_selection_can_extend() {
        let mut doc = TextDocument::new("abcd");
        doc.move_to(1, false);
        doc.move_cursor(2, true);
        assert_eq!(doc.selected_text(), "bc");
    }

    #[test]
    fn no_op_does_not_create_history() {
        let mut doc = TextDocument::new("same");
        doc.set_selection(Selection { anchor: 0, head: 4 });
        doc.insert("same");
        assert!(!doc.undo());
    }
}
