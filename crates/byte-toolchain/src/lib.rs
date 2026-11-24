//! One-tap language toolchains: declarative manifests, verified installs.
//!
//! Downloading is done by the host app (it owns networking and progress UI);
//! this crate verifies the archive hash and extracts it safely. That keeps
//! offline installs identical to online ones: just point at a local archive.
pub mod install;
pub mod manifest;

pub use install::{installed, install_from_archive, verify_sha256};
pub use manifest::{Artifact, ArchiveFormat, Manifest, Runtime};

#[derive(Debug, thiserror::Error)]
pub enum Error {
    #[error("io error: {0}")]
    Io(#[from] std::io::Error),
    #[error("manifest error: {0}")]
    Manifest(String),
    #[error("no artifact for abi `{0}`")]
    NoArtifact(String),
    #[error("artifact hash is not pinned; run tools/pin_manifest.py")]
    Unpinned,
    #[error("sha256 mismatch: expected {expected}, got {actual}")]
    HashMismatch { expected: String, actual: String },
    #[error("unsafe archive entry: {0}")]
    UnsafeEntry(String),
}

pub type Result<T> = std::result::Result<T, Error>;
