use crate::manifest::{ArchiveFormat, Manifest};
use crate::{Error, Result};
use flate2::read::GzDecoder;
use sha2::{Digest, Sha256};
use std::fs::{self, File};
use std::io::Read;
use std::path::{Component, Path, PathBuf};

fn hex(bytes: &[u8]) -> String {
    bytes.iter().map(|b| format!("{b:02x}")).collect()
}

/// Compare the file's SHA-256 with `expected`. Unpinned hashes are refused.
pub fn verify_sha256(path: &Path, expected: &str) -> Result<()> {
    let expected = expected.trim().to_ascii_lowercase();
    if expected.is_empty() || expected == "pin_me" {
        return Err(Error::Unpinned);
    }
    let mut f = File::open(path)?;
    let mut h = Sha256::new();
    let mut buf = [0u8; 64 * 1024];
    loop {
        let n = f.read(&mut buf)?;
        if n == 0 {
            break;
        }
        h.update(&buf[..n]);
    }
    let actual = hex(&h.finalize());
    if actual == expected {
        Ok(())
    } else {
        Err(Error::HashMismatch { expected, actual })
    }
}

fn extract_tar_gz(archive: &Path, dest: &Path, strip: usize) -> Result<()> {
    let mut ar = tar::Archive::new(GzDecoder::new(File::open(archive)?));
    for entry in ar.entries()? {
        let mut entry = entry?;
        let path = entry.path()?.into_owned();
        if path
            .components()
            .any(|c| matches!(c, Component::ParentDir | Component::RootDir | Component::Prefix(_)))
        {
            return Err(Error::UnsafeEntry(path.display().to_string()));
        }
        if let Some(link) = entry.link_name()? {
            if link.is_absolute() {
                return Err(Error::UnsafeEntry(format!("{} -> {}", path.display(), link.display())));
            }
        }
        let comps: Vec<_> = path
            .components()
            .filter(|c| matches!(c, Component::Normal(_)))
            .skip(strip)
            .collect();
        if comps.is_empty() {
            continue;
        }
        let out = dest.join(comps.iter().collect::<PathBuf>());
        if let Some(parent) = out.parent() {
            fs::create_dir_all(parent)?;
        }
        entry.unpack(&out)?;
    }
    Ok(())
}

/// Verify and extract `archive` into `<root>/toolchains/<id>/<version>`.
/// Extraction happens in a `.partial` directory and is renamed on success.
pub fn install_from_archive(
    manifest: &Manifest,
    archive: &Path,
    abi: &str,
    root: &Path,
) -> Result<PathBuf> {
    let art = manifest.artifact_for(abi)?;
    verify_sha256(archive, &art.sha256)?;

    let base = root.join("toolchains").join(&manifest.id);
    let final_dir = base.join(&manifest.version);
    let partial = base.join(format!("{}.partial", manifest.version));
    if partial.exists() {
        fs::remove_dir_all(&partial)?;
    }
    fs::create_dir_all(&partial)?;

    let result = match art.format {
        ArchiveFormat::TarGz => extract_tar_gz(archive, &partial, art.strip_components),
    };
    if let Err(e) = result {
        let _ = fs::remove_dir_all(&partial);
        return Err(e);
    }
    if final_dir.exists() {
        fs::remove_dir_all(&final_dir)?;
    }
    fs::rename(&partial, &final_dir)?;
    Ok(final_dir)
}

/// List installed toolchains as `(id, version)` pairs.
pub fn installed(root: &Path) -> Vec<(String, String)> {
    let mut out = Vec::new();
    let base = root.join("toolchains");
    let Ok(ids) = fs::read_dir(&base) else { return out };
    for id in ids.flatten() {
        let Ok(versions) = fs::read_dir(id.path()) else { continue };
        for v in versions.flatten() {
            let name = v.file_name().to_string_lossy().into_owned();
            if !name.ends_with(".partial") && v.path().is_dir() {
                out.push((id.file_name().to_string_lossy().into_owned(), name));
            }
        }
    }
    out.sort();
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn unpinned_is_refused() {
        let p = std::env::temp_dir().join("byte-unpinned-test");
        fs::write(&p, b"x").unwrap();
        assert!(matches!(verify_sha256(&p, "PIN_ME"), Err(Error::Unpinned)));
        // sha256("x")
        let ok = "2d711642b726b04401627ca9fbac32f5c8530fb1903cc4db02258717921a4881";
        assert!(verify_sha256(&p, ok).is_ok());
        assert!(matches!(
            verify_sha256(&p, &"0".repeat(64)),
            Err(Error::HashMismatch { .. })
        ));
    }
}
