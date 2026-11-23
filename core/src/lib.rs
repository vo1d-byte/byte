//! Byte IDE native core.
//!
//! The editor document model is independent from Android UI state. Positions are
//! Unicode scalar-value (Rust char) offsets; adapters must translate UTF-16 editor
//! offsets at the Kotlin/JNI boundary.

pub mod editor;

pub const CORE_VERSION: &str = env!("CARGO_PKG_VERSION");

pub fn status_message() -> String {
    format!("Byte Core {CORE_VERSION} is ready")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn reports_version() {
        assert_eq!(CORE_VERSION, "0.2.0");
        assert!(status_message().contains("Byte Core"));
    }
}
