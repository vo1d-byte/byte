//! Multi-language grammar registry for Byte's syntax engine.
//!
//! This first registry intentionally compiles a small, explicit set of grammars
//! into the native library. It is designed to grow without changing editor APIs.
//! Dynamic grammar downloads are a separate, signed-package feature and are not
//! performed by this registry.

use tree_sitter::{Language, Parser, Tree};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum LanguageId {
    Kotlin,
    Rust,
}

#[derive(Debug, Clone, Copy)]
pub struct LanguageDescriptor {
    pub id: LanguageId,
    pub name: &'static str,
    pub display_name: &'static str,
    pub extensions: &'static [&'static str],
    pub line_comment: &'static str,
    pub block_comment: Option<(&'static str, &'static str)>,
}

#[derive(Debug)]
pub enum RegistryError {
    UnknownLanguage(String),
    ParserConfiguration(String),
    ParseFailed,
}

impl std::fmt::Display for RegistryError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::UnknownLanguage(name) => write!(f, "Unsupported language: {name}"),
            Self::ParserConfiguration(message) => write!(f, "Parser configuration failed: {message}"),
            Self::ParseFailed => write!(f, "Tree-sitter parse failed"),
        }
    }
}
impl std::error::Error for RegistryError {}

const LANGUAGES: &[LanguageDescriptor] = &[
    LanguageDescriptor {
        id: LanguageId::Kotlin,
        name: "kotlin",
        display_name: "Kotlin",
        extensions: &["kt", "kts"],
        line_comment: "//",
        block_comment: Some(("/*", "*/")),
    },
    LanguageDescriptor {
        id: LanguageId::Rust,
        name: "rust",
        display_name: "Rust",
        extensions: &["rs"],
        line_comment: "//",
        block_comment: Some(("/*", "*/")),
    },
];

pub fn languages() -> &'static [LanguageDescriptor] {
    LANGUAGES
}

pub fn language_by_name(name: &str) -> Option<&'static LanguageDescriptor> {
    let normalized = name.trim().to_ascii_lowercase();
    let normalized = match normalized.as_str() {
        "kt" | "kts" => "kotlin",
        "rs" => "rust",
        "rsrc" => "rust",
        _ => normalized.as_str(),
    };
    LANGUAGES.iter().find(|item| item.name == normalized)
}

pub fn language_for_path(path: &str) -> Option<&'static LanguageDescriptor> {
    let extension = path.rsplit('.').next()?;
    LANGUAGES.iter().find(|item| item.extensions.iter().any(|ext| *ext == extension))
}

pub fn grammar(id: LanguageId) -> Language {
    match id {
        LanguageId::Kotlin => tree_sitter_kotlin_ng::LANGUAGE.into(),
        LanguageId::Rust => tree_sitter_rust::LANGUAGE.into(),
    }
}

pub fn create_parser(name: &str) -> Result<Parser, RegistryError> {
    let descriptor = language_by_name(name)
        .ok_or_else(|| RegistryError::UnknownLanguage(name.to_owned()))?;
    let mut parser = Parser::new();
    parser.set_language(&grammar(descriptor.id))
        .map_err(|error| RegistryError::ParserConfiguration(error.to_string()))?;
    Ok(parser)
}

pub fn parse(name: &str, source: &str) -> Result<Tree, RegistryError> {
    let mut parser = create_parser(name)?;
    parser.parse(source, None).ok_or(RegistryError::ParseFailed)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn resolves_language_names_and_aliases() {
        assert_eq!(language_by_name("Kotlin").unwrap().id, LanguageId::Kotlin);
        assert_eq!(language_by_name("rs").unwrap().id, LanguageId::Rust);
    }

    #[test]
    fn detects_language_from_file_extension() {
        assert_eq!(language_for_path("src/main.rs").unwrap().id, LanguageId::Rust);
        assert_eq!(language_for_path("build.gradle.kts").unwrap().id, LanguageId::Kotlin);
        assert!(language_for_path("README").is_none());
    }

    #[test]
    fn parses_kotlin_and_rust_sources() {
        let kotlin = parse("kotlin", "fun greet() = \"hello\"").unwrap();
        let rust = parse("rust", "fn greet() -> &'static str { \"hello\" }").unwrap();
        assert!(!kotlin.root_node().has_error());
        assert!(!rust.root_node().has_error());
    }

    #[test]
    fn rejects_unknown_grammars() {
        assert!(matches!(create_parser("brainfuck"), Err(RegistryError::UnknownLanguage(_))));
    }
}
