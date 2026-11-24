//! Sandboxed project file operations. Every path is relative to the project root.
use crate::error::{Error, Result};
use std::fs;
use std::path::{Component, Path, PathBuf};

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Entry {
    pub name: String,
    pub is_dir: bool,
}

#[derive(Debug, Clone)]
pub struct Project {
    root: PathBuf,
}

impl Project {
    pub fn new(root: impl Into<PathBuf>) -> Self {
        Self { root: root.into() }
    }

    pub fn root(&self) -> &Path {
        &self.root
    }

    /// Resolve a relative path, rejecting absolute paths and `..` escapes.
    pub fn resolve(&self, rel: &str) -> Result<PathBuf> {
        let mut out = self.root.clone();
        for c in Path::new(rel).components() {
            match c {
                Component::Normal(p) => out.push(p),
                Component::CurDir => {}
                _ => return Err(Error::InvalidPath(rel.to_string())),
            }
        }
        Ok(out)
    }

    /// Directories first, then files, both alphabetical (case-insensitive).
    pub fn list(&self, rel: &str) -> Result<Vec<Entry>> {
        let mut entries = Vec::new();
        for item in fs::read_dir(self.resolve(rel)?)? {
            let item = item?;
            entries.push(Entry {
                name: item.file_name().to_string_lossy().into_owned(),
                is_dir: item.file_type()?.is_dir(),
            });
        }
        entries.sort_by(|a, b| {
            b.is_dir
                .cmp(&a.is_dir)
                .then_with(|| a.name.to_lowercase().cmp(&b.name.to_lowercase()))
        });
        Ok(entries)
    }

    pub fn read_to_string(&self, rel: &str) -> Result<String> {
        Ok(fs::read_to_string(self.resolve(rel)?)?)
    }

    /// Write via a temp file in the same directory, then rename (crash-safe).
    pub fn write_atomic(&self, rel: &str, contents: &str) -> Result<()> {
        let path = self.resolve(rel)?;
        if let Some(parent) = path.parent() {
            fs::create_dir_all(parent)?;
        }
        let mut tmp = path.clone().into_os_string();
        tmp.push(".byte-tmp");
        let tmp = PathBuf::from(tmp);
        fs::write(&tmp, contents)?;
        fs::rename(&tmp, &path)?;
        Ok(())
    }

    pub fn create_dir(&self, rel: &str) -> Result<()> {
        Ok(fs::create_dir_all(self.resolve(rel)?)?)
    }

    pub fn rename(&self, from: &str, to: &str) -> Result<()> {
        let dest = self.resolve(to)?;
        if let Some(parent) = dest.parent() {
            fs::create_dir_all(parent)?;
        }
        Ok(fs::rename(self.resolve(from)?, dest)?)
    }

    pub fn delete(&self, rel: &str) -> Result<()> {
        let p = self.resolve(rel)?;
        if p == self.root {
            return Err(Error::InvalidPath(rel.to_string()));
        }
        if p.is_dir() {
            fs::remove_dir_all(p)?;
        } else {
            fs::remove_file(p)?;
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rejects_escape() {
        let p = Project::new("/tmp/proj");
        assert!(p.resolve("../etc/passwd").is_err());
        assert!(p.resolve("/etc/passwd").is_err());
        assert!(p.resolve("src/main.rs").is_ok());
    }
}
