//! Validation primitives for declarative language toolchain catalogs.
use serde_json::Value;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CatalogPackage {
    pub package_id: String,
    pub language_id: String,
    pub version: String,
    pub artifact_url: String,
    pub sha256: String,
    pub archive_format: String,
}

pub fn parse_catalog(input: &str) -> Result<Vec<CatalogPackage>, String> {
    let root: Value = serde_json::from_str(input).map_err(|e| format!("invalid catalog JSON: {e}"))?;
    if root.get("schemaVersion").and_then(Value::as_u64) != Some(1) {
        return Err("unsupported catalog schemaVersion".into());
    }
    let packages = root.get("packages").and_then(Value::as_array)
        .ok_or_else(|| "catalog packages must be an array".to_string())?;
    let mut parsed = Vec::with_capacity(packages.len());
    for package in packages {
        let package_id = required_string(package, "packageId")?;
        let language_id = required_string(package, "languageId")?;
        let version = required_string(package, "version")?;
        let artifact_url = required_string(package, "artifactUrl")?;
        let sha256 = required_string(package, "sha256")?;
        let archive_format = required_string(package, "archiveFormat")?;
        if !safe_segment(&package_id) || !safe_segment(&version) {
            return Err(format!("unsafe package path for {package_id}"));
        }
        if !artifact_url.starts_with("https://") { return Err(format!("package {package_id} must use HTTPS")); }
        if sha256.len() != 64 || !sha256.bytes().all(|b| b.is_ascii_hexdigit()) {
            return Err(format!("package {package_id} has invalid SHA-256"));
        }
        if archive_format != "zip" { return Err(format!("package {package_id} uses unsupported archive format")); }
        parsed.push(CatalogPackage { package_id, language_id, version, artifact_url, sha256, archive_format });
    }
    Ok(parsed)
}

fn required_string(value: &Value, key: &str) -> Result<String, String> {
    value.get(key).and_then(Value::as_str).filter(|s| !s.trim().is_empty())
        .map(str::to_owned).ok_or_else(|| format!("missing string field {key}"))
}

fn safe_segment(value: &str) -> bool {
    !value.is_empty() && value.len() <= 80
        && value.bytes().all(|b| b.is_ascii_alphanumeric() || b"._+-".contains(&b))
        && value.as_bytes()[0].is_ascii_alphanumeric()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn accepts_empty_catalog() {
        let input = r#"{"schemaVersion":1,"catalogVersion":"0.1.0","packages":[]}"#;
        assert_eq!(parse_catalog(input).unwrap(), Vec::<CatalogPackage>::new());
    }

    #[test]
    fn rejects_http_artifacts() {
        let input = r#"{"schemaVersion":1,"packages":[{"packageId":"python","languageId":"python","version":"3.12","artifactUrl":"http://example.invalid/python.zip","sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","archiveFormat":"zip"}]}"#;
        assert!(parse_catalog(input).unwrap_err().contains("HTTPS"));
    }

    #[test]
    fn rejects_path_traversal_ids() {
        let input = r#"{"schemaVersion":1,"packages":[{"packageId":"../evil","languageId":"x","version":"1","artifactUrl":"https://example.invalid/x.zip","sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","archiveFormat":"zip"}]}"#;
        assert!(parse_catalog(input).unwrap_err().contains("unsafe package path"));
    }
}
