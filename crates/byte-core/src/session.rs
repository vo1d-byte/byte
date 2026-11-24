//! A session owns the open tabs. Each tab keeps its own buffer, so switching
//! tabs preserves text, cursor, selection and undo history.
use crate::buffer::Buffer;
use crate::error::{Error, Result};
use crate::project::Project;
use std::io::ErrorKind;
use std::path::PathBuf;

pub struct Tab {
    pub path: String,
    pub buffer: Buffer,
}

pub struct Session {
    project: Project,
    tabs: Vec<Tab>,
    active: usize,
}

impl Session {
    pub fn new(root: impl Into<PathBuf>) -> Self {
        Self { project: Project::new(root), tabs: Vec::new(), active: 0 }
    }

    pub fn project(&self) -> &Project {
        &self.project
    }
    pub fn tabs(&self) -> &[Tab] {
        &self.tabs
    }
    pub fn active_index(&self) -> usize {
        self.active
    }

    pub fn set_active(&mut self, idx: usize) {
        if idx < self.tabs.len() {
            self.active = idx;
        }
    }

    pub fn active_mut(&mut self) -> Option<&mut Tab> {
        self.tabs.get_mut(self.active)
    }

    /// Open (or focus) a file. Missing files open as empty, unsaved-new buffers.
    pub fn open(&mut self, rel: &str) -> Result<usize> {
        if let Some(i) = self.tabs.iter().position(|t| t.path == rel) {
            self.active = i;
            return Ok(i);
        }
        let text = match self.project.read_to_string(rel) {
            Ok(t) => t,
            Err(Error::Io(e)) if e.kind() == ErrorKind::NotFound => String::new(),
            Err(e) => return Err(e),
        };
        self.tabs.push(Tab { path: rel.to_string(), buffer: Buffer::from_text(&text) });
        self.active = self.tabs.len() - 1;
        Ok(self.active)
    }

    pub fn close(&mut self, idx: usize) {
        if idx < self.tabs.len() {
            self.tabs.remove(idx);
            if self.active >= self.tabs.len() {
                self.active = self.tabs.len().saturating_sub(1);
            }
        }
    }

    /// Auto-save: write every dirty tab. Returns how many were written.
    pub fn save_dirty(&mut self) -> Result<usize> {
        let mut n = 0;
        for tab in &mut self.tabs {
            if tab.buffer.is_dirty() {
                self.project.write_atomic(&tab.path, &tab.buffer.text())?;
                tab.buffer.mark_saved();
                n += 1;
            }
        }
        Ok(n)
    }
}
