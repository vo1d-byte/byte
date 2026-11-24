//! Rope-backed text buffer with undo/redo and editor helpers.
//!
//! All indices are Unicode scalar (char) indices unless a method name says
//! `utf16`. The Android UI uses UTF-16 offsets, so conversion helpers exist.
use ropey::Rope;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub struct Selection {
    pub anchor: usize,
    pub head: usize,
}

impl Selection {
    pub fn caret(at: usize) -> Self {
        Self { anchor: at, head: at }
    }
    pub fn start(&self) -> usize {
        self.anchor.min(self.head)
    }
    pub fn end(&self) -> usize {
        self.anchor.max(self.head)
    }
    pub fn is_empty(&self) -> bool {
        self.anchor == self.head
    }
}

#[derive(Debug, Clone)]
struct Edit {
    at: usize,
    removed: String,
    inserted: String,
    before: Selection,
    after: Selection,
}

#[derive(Debug, Default)]
pub struct Buffer {
    rope: Rope,
    sel: Selection,
    undo: Vec<Edit>,
    redo: Vec<Edit>,
    dirty: bool,
}

impl Buffer {
    pub fn from_text(text: &str) -> Self {
        Self { rope: Rope::from_str(text), ..Default::default() }
    }

    pub fn text(&self) -> String {
        self.rope.to_string()
    }
    pub fn len_chars(&self) -> usize {
        self.rope.len_chars()
    }
    pub fn line_count(&self) -> usize {
        self.rope.len_lines()
    }
    pub fn selection(&self) -> Selection {
        self.sel
    }
    pub fn is_dirty(&self) -> bool {
        self.dirty
    }
    pub fn mark_saved(&mut self) {
        self.dirty = false;
    }

    pub fn set_selection(&mut self, anchor: usize, head: usize) {
        let len = self.rope.len_chars();
        self.sel = Selection { anchor: anchor.min(len), head: head.min(len) };
    }

    // ---- UTF-16 helpers (Android text offsets) ----

    pub fn char_to_utf16(&self, idx: usize) -> usize {
        self.rope.chars().take(idx).map(|c| c.len_utf16()).sum()
    }

    pub fn utf16_to_char(&self, units: usize) -> usize {
        let mut acc = 0;
        let mut n = 0;
        for c in self.rope.chars() {
            if acc >= units {
                break;
            }
            acc += c.len_utf16();
            n += 1;
        }
        n
    }

    pub fn selection_utf16(&self) -> (usize, usize) {
        (self.char_to_utf16(self.sel.anchor), self.char_to_utf16(self.sel.head))
    }

    /// Replace the buffer with `new_text` using the smallest single edit, so
    /// undo history stays meaningful when the UI hands us whole strings.
    pub fn sync_from_text(&mut self, new_text: &str, anchor_u16: usize, head_u16: usize) {
        let new: Vec<char> = new_text.chars().collect();
        let old_len = self.rope.len_chars();
        let min = old_len.min(new.len());

        let mut prefix = 0;
        while prefix < min && self.rope.char(prefix) == new[prefix] {
            prefix += 1;
        }
        let mut suffix = 0;
        while suffix < min - prefix
            && self.rope.char(old_len - 1 - suffix) == new[new.len() - 1 - suffix]
        {
            suffix += 1;
        }

        let remove_len = old_len - prefix - suffix;
        let ins: String = new[prefix..new.len() - suffix].iter().collect();
        let changed = remove_len > 0 || !ins.is_empty();
        if changed {
            let after = prefix + ins.chars().count();
            self.edit(prefix, remove_len, &ins, Selection::caret(after));
        }

        // Selection arrives in UTF-16 units of the *new* text.
        let a = self.utf16_to_char(anchor_u16);
        let h = self.utf16_to_char(head_u16);
        self.set_selection(a, h);
        if changed {
            let sel = self.sel;
            if let Some(last) = self.undo.last_mut() {
                last.after = sel;
            }
        }
    }

    // ---- core edit primitive ----

    fn edit(&mut self, at: usize, remove_len: usize, ins: &str, cursor_after: Selection) {
        let len = self.rope.len_chars();
        let at = at.min(len);
        let end = (at + remove_len).min(len);
        let removed = self.rope.slice(at..end).to_string();
        let before = self.sel;

        if end > at {
            self.rope.remove(at..end);
        }
        if !ins.is_empty() {
            self.rope.insert(at, ins);
        }
        self.sel = cursor_after;
        self.dirty = true;
        self.redo.clear();

        // Coalesce consecutive single-char typing into one undo step.
        if removed.is_empty() && ins.chars().count() == 1 && ins != "\n" {
            if let Some(last) = self.undo.last_mut() {
                if last.removed.is_empty()
                    && !last.inserted.ends_with('\n')
                    && last.at + last.inserted.chars().count() == at
                {
                    last.inserted.push_str(ins);
                    last.after = cursor_after;
                    return;
                }
            }
        }
        self.undo.push(Edit {
            at,
            removed,
            inserted: ins.to_string(),
            before,
            after: cursor_after,
        });
    }

    pub fn undo(&mut self) -> bool {
        let Some(e) = self.undo.pop() else { return false };
        let n = e.inserted.chars().count();
        if n > 0 {
            self.rope.remove(e.at..e.at + n);
        }
        if !e.removed.is_empty() {
            self.rope.insert(e.at, &e.removed);
        }
        self.sel = e.before;
        self.dirty = true;
        self.redo.push(e);
        true
    }

    pub fn redo(&mut self) -> bool {
        let Some(e) = self.redo.pop() else { return false };
        let n = e.removed.chars().count();
        if n > 0 {
            self.rope.remove(e.at..e.at + n);
        }
        if !e.inserted.is_empty() {
            self.rope.insert(e.at, &e.inserted);
        }
        self.sel = e.after;
        self.dirty = true;
        self.undo.push(e);
        true
    }

    // ---- editing commands ----

    /// Insert text, replacing the current selection.
    pub fn insert(&mut self, text: &str) {
        let (s, e) = (self.sel.start(), self.sel.end());
        let after = s + text.chars().count();
        self.edit(s, e - s, text, Selection::caret(after));
    }

    pub fn delete_backward(&mut self) {
        if !self.sel.is_empty() {
            let (s, e) = (self.sel.start(), self.sel.end());
            self.edit(s, e - s, "", Selection::caret(s));
        } else if self.sel.head > 0 {
            let h = self.sel.head;
            self.edit(h - 1, 1, "", Selection::caret(h - 1));
        }
    }

    /// Smart pair insert: `{}` puts the caret between; with a selection, wraps it.
    pub fn insert_pair(&mut self, open: &str, close: &str) {
        let (s, e) = (self.sel.start(), self.sel.end());
        let o = open.chars().count();
        if self.sel.is_empty() {
            let text = format!("{open}{close}");
            self.edit(s, 0, &text, Selection::caret(s + o));
        } else {
            let inner = self.rope.slice(s..e).to_string();
            let inner_len = e - s;
            let text = format!("{open}{inner}{close}");
            self.edit(
                s,
                inner_len,
                &text,
                Selection { anchor: s + o, head: s + o + inner_len },
            );
        }
    }

    fn line_bounds(&self, line: usize) -> (usize, usize) {
        let start = self.rope.line_to_char(line);
        let end = if line + 1 < self.rope.len_lines() {
            self.rope.line_to_char(line + 1)
        } else {
            self.rope.len_chars()
        };
        (start, end)
    }

    pub fn duplicate_line(&mut self) {
        let line = self.rope.char_to_line(self.sel.head);
        let (start, end) = self.line_bounds(line);
        let text = self.rope.slice(start..end).to_string();
        let col = self.sel.head - start;
        let (ins, offset) = if text.ends_with('\n') {
            (text.clone(), 0)
        } else {
            (format!("\n{text}"), 1)
        };
        self.edit(end, 0, &ins, Selection::caret(end + offset + col));
    }

    pub fn toggle_line_comment(&mut self, prefix: &str) {
        let line = self.rope.char_to_line(self.sel.head);
        let (start, end) = self.line_bounds(line);
        let text = self.rope.slice(start..end).to_string();
        let indent = text.chars().take_while(|c| *c == ' ' || *c == '\t').count();
        let rest: String = text.chars().skip(indent).collect();
        let at = start + indent;
        let head = self.sel.head;

        if rest.starts_with(prefix) {
            let mut remove = prefix.chars().count();
            if rest[prefix.len()..].starts_with(' ') {
                remove += 1;
            }
            let after = if head > at { head.saturating_sub(remove).max(at) } else { head };
            self.edit(at, remove, "", Selection::caret(after));
        } else {
            let ins = format!("{prefix} ");
            let n = ins.chars().count();
            let after = if head >= at { head + n } else { head };
            self.edit(at, 0, &ins, Selection::caret(after));
        }
    }

    // ---- selection / cursor ----

    pub fn select_all(&mut self) {
        self.sel = Selection { anchor: 0, head: self.rope.len_chars() };
    }

    pub fn select_line(&mut self) {
        let line = self.rope.char_to_line(self.sel.head);
        let (s, e) = self.line_bounds(line);
        self.sel = Selection { anchor: s, head: e };
    }

    pub fn select_word(&mut self) {
        let is_w = |c: char| c.is_alphanumeric() || c == '_';
        let len = self.rope.len_chars();
        let mut s = self.sel.head.min(len);
        let mut e = s;
        while s > 0 && is_w(self.rope.char(s - 1)) {
            s -= 1;
        }
        while e < len && is_w(self.rope.char(e)) {
            e += 1;
        }
        self.sel = Selection { anchor: s, head: e };
    }

    fn set_head(&mut self, head: usize, extend: bool) {
        let head = head.min(self.rope.len_chars());
        if extend {
            self.sel.head = head;
        } else {
            self.sel = Selection::caret(head);
        }
    }

    pub fn move_horizontal(&mut self, delta: isize, extend: bool) {
        let head = (self.sel.head as isize + delta).max(0) as usize;
        self.set_head(head, extend);
    }

    pub fn move_vertical(&mut self, delta: isize, extend: bool) {
        let line = self.rope.char_to_line(self.sel.head);
        let col = self.sel.head - self.rope.line_to_char(line);
        let max_line = self.rope.len_lines().saturating_sub(1);
        let target = (line as isize + delta).clamp(0, max_line as isize) as usize;
        let (s, e) = self.line_bounds(target);
        let mut line_len = e - s;
        if target < max_line && line_len > 0 {
            line_len -= 1; // exclude '\n'
        }
        self.set_head(s + col.min(line_len), extend);
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn insert_undo_redo() {
        let mut b = Buffer::from_text("");
        b.insert("a");
        b.insert("b");
        assert_eq!(b.text(), "ab");
        assert!(b.undo());
        assert_eq!(b.text(), "");
        assert!(b.redo());
        assert_eq!(b.text(), "ab");
    }

    #[test]
    fn smart_pair_places_caret_between() {
        let mut b = Buffer::from_text("");
        b.insert_pair("{", "}");
        assert_eq!(b.text(), "{}");
        assert_eq!(b.selection(), Selection::caret(1));
    }

    #[test]
    fn duplicate_and_comment() {
        let mut b = Buffer::from_text("let x = 1;\n");
        b.set_selection(3, 3);
        b.duplicate_line();
        assert_eq!(b.text(), "let x = 1;\nlet x = 1;\n");
        b.set_selection(0, 0);
        b.toggle_line_comment("//");
        assert!(b.text().starts_with("// let x"));
        b.toggle_line_comment("//");
        assert!(b.text().starts_with("let x"));
    }

    #[test]
    fn sync_makes_minimal_edit_and_utf16_roundtrips() {
        let mut b = Buffer::from_text("héllo 😀 world");
        b.sync_from_text("héllo 😀 brave world", 0, 0);
        assert_eq!(b.text(), "héllo 😀 brave world");
        assert!(b.undo());
        assert_eq!(b.text(), "héllo 😀 world");
        // emoji is 2 UTF-16 units but 1 char
        assert_eq!(b.char_to_utf16(7), 8);
        assert_eq!(b.utf16_to_char(8), 7);
    }

    #[test]
    fn vertical_move_keeps_column() {
        let mut b = Buffer::from_text("abcd\nef\nghij");
        b.set_selection(3, 3);
        b.move_vertical(1, false);
        assert_eq!(b.selection().head, 7); // end of "ef"
        b.move_vertical(1, false);
        assert_eq!(b.selection().head, 10);
    }
}
