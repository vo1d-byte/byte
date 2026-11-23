//! Byte IDE native core.
//!
//! This crate contains platform-independent services. Android UI and platform APIs
//! remain in Kotlin; performance-sensitive and portable logic belongs here.

/// Core version exposed to frontends and diagnostics.
pub const CORE_VERSION: &str = env!("CARGO_PKG_VERSION");

/// Return a stable greeting for smoke tests and frontend integration.
pub fn status_message() -> String {
    format!("Byte Core {CORE_VERSION} is ready")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn reports_version() {
        assert_eq!(CORE_VERSION, "0.1.0");
        assert!(status_message().contains("Byte Core"));
    }
}
