use crate::{Error, Result};
use serde::Deserialize;
use std::collections::BTreeMap;

#[derive(Debug, Clone, Copy, Deserialize, PartialEq, Eq, Default)]
#[serde(rename_all = "snake_case")]
pub enum Runtime {
    /// Binaries run directly on Android (statically linked).
    #[default]
    Native,
    /// Binaries need a glibc Linux rootfs (proot).
    Rootfs,
}

#[derive(Debug, Clone, Copy, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum ArchiveFormat {
    TarGz,
}

#[derive(Debug, Clone, Deserialize)]
pub struct Artifact {
    pub abi: String,
    pub url: String,
    pub sha256: String,
    pub format: ArchiveFormat,
    #[serde(default)]
    pub strip_components: usize,
}

#[derive(Debug, Clone, Deserialize, Default)]
pub struct Language {
    #[serde(default)]
    pub extensions: Vec<String>,
    /// Command template; `{file}` is replaced with the active file.
    pub run: Option<String>,
    pub lsp: Option<String>,
    pub debug: Option<String>,
    pub comment: Option<String>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct Manifest {
    pub id: String,
    pub name: String,
    pub version: String,
    #[serde(default)]
    pub runtime: Runtime,
    #[serde(default)]
    pub requires: Vec<String>,
    /// Directories (relative to the install root) to add to PATH.
    #[serde(default)]
    pub bin: Vec<String>,
    #[serde(default)]
    pub env: BTreeMap<String, String>,
    #[serde(default)]
    pub language: Language,
    pub artifacts: Vec<Artifact>,
}

impl Manifest {
    pub fn parse(toml_text: &str) -> Result<Self> {
        let m: Manifest =
            toml::from_str(toml_text).map_err(|e| Error::Manifest(e.to_string()))?;
        if m.id.is_empty()
            || !m.id.chars().all(|c| c.is_ascii_alphanumeric() || c == '-' || c == '_')
        {
            return Err(Error::Manifest(format!("invalid id `{}`", m.id)));
        }
        Ok(m)
    }

    pub fn artifact_for(&self, abi: &str) -> Result<&Artifact> {
        self.artifacts
            .iter()
            .find(|a| a.abi == abi)
            .ok_or_else(|| Error::NoArtifact(abi.to_string()))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_manifest() {
        let m = Manifest::parse(
            r#"
id = "go"
name = "Go"
version = "1.23.3"
[[artifacts]]
abi = "linux-arm64"
url = "https://example.com/go.tar.gz"
sha256 = "PIN_ME"
format = "tar_gz"
strip_components = 1
"#,
        )
        .unwrap();
        assert_eq!(m.runtime, Runtime::Native);
        assert_eq!(m.artifact_for("linux-arm64").unwrap().strip_components, 1);
        assert!(m.artifact_for("x86").is_err());
    }

    #[test]
    fn rejects_bad_id() {
        assert!(Manifest::parse(
            "id=\"../x\"\nname=\"x\"\nversion=\"1\"\nartifacts=[]\n"
        )
        .is_err());
    }
}
