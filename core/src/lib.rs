//! Byte IDE native core and JNI bridge.

pub mod editor;
pub mod jni_bridge;
pub mod syntax;
pub mod syntax_jni;
pub mod grammar_registry;
pub mod multi_syntax;
pub mod multi_syntax_jni;

pub const CORE_VERSION: &str = env!("CARGO_PKG_VERSION");

pub fn status_message() -> String {
    format!("Byte Core {CORE_VERSION} is ready")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn reports_version() {
        assert_eq!(CORE_VERSION, "0.3.0");
        assert!(status_message().contains("Byte Core"));
    }
}

pub mod lsp;
pub mod toolchain;

pub mod process;
