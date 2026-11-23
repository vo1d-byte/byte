//! Language-agnostic incremental syntax document.
use std::ops::Range;
use tree_sitter::{InputEdit, Parser, Point, Tree};
use crate::grammar_registry::{create_parser, language_for_path, RegistryError};

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct HighlightSpan { pub start_byte: usize, pub end_byte: usize, pub kind: &'static str }
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ParseDiagnostic { pub start_byte: usize, pub end_byte: usize, pub missing: bool, pub message: String }
pub struct MultiLanguageDocument { parser: Parser, source: String, tree: Tree }

impl MultiLanguageDocument {
    pub fn new(path: &str, source: impl Into<String>) -> Result<Self, RegistryError> {
        let language = language_for_path(path).ok_or_else(|| RegistryError::UnknownLanguage(path.to_owned()))?;
        Self::with_language(language.name, source)
    }
    pub fn with_language(name: &str, source: impl Into<String>) -> Result<Self, RegistryError> {
        let mut parser = create_parser(name)?;
        let source = source.into();
        let tree = parser.parse(&source, None).ok_or(RegistryError::ParseFailed)?;
        Ok(Self { parser, source, tree })
    }
    pub fn source(&self) -> &str { &self.source }
    pub fn replace(&mut self, range: Range<usize>, replacement: &str) -> Result<(), RegistryError> {
        let start = range.start.min(self.source.len());
        let end = range.end.min(self.source.len()).max(start);
        if !self.source.is_char_boundary(start) || !self.source.is_char_boundary(end) {
            return Err(RegistryError::ParseFailed);
        }
        let start_position = point(&self.source, start);
        let old_end_position = point(&self.source, end);
        let new_end_position = advance(start_position, replacement);
        self.tree.edit(&InputEdit {
            start_byte: start, old_end_byte: end, new_end_byte: start + replacement.len(),
            start_position, old_end_position, new_end_position,
        });
        self.source.replace_range(start..end, replacement);
        self.tree = self.parser.parse(&self.source, Some(&self.tree)).ok_or(RegistryError::ParseFailed)?;
        Ok(())
    }
    pub fn spans(&self) -> Vec<HighlightSpan> {
        let mut out = Vec::new();
        visit(self.tree.root_node(), &mut out);
        out
    }
    pub fn diagnostics(&self) -> Vec<ParseDiagnostic> {
        let mut out = Vec::new();
        visit_errors(self.tree.root_node(), &mut out);
        out
    }
}
fn point(source: &str, byte: usize) -> Point {
    let prefix = &source[..byte];
    Point::new(prefix.bytes().filter(|b| *b == b'\n').count(), prefix.rsplit('\n').next().unwrap_or("").len())
}
fn advance(start: Point, text: &str) -> Point {
    let mut row = start.row; let mut column = start.column;
    for b in text.bytes() { if b == b'\n' { row += 1; column = 0; } else { column += 1; } }
    Point::new(row, column)
}
fn classify(kind: &str) -> Option<&'static str> {
    if kind.contains("comment") { return Some("comment"); }
    if kind.contains("string") || kind.contains("char_literal") || kind.contains("template_string") { return Some("string"); }
    if kind.contains("integer") || kind.contains("float") || kind.contains("number") { return Some("number"); }
    if kind.contains("type") || kind == "primitive_type" || kind == "predefined_type" { return Some("type"); }
    if kind == "true" || kind == "false" || kind == "null" || kind == "None" { return Some("literal"); }
    if matches!(kind, "def" | "class" | "return" | "if" | "else" | "elif" | "for" | "while" | "import" | "from" | "as" | "async" | "await" | "fn" | "let" | "mut" | "pub" | "use" | "mod" | "impl" | "trait" | "match" | "loop" | "struct" | "enum" | "const" | "static" | "function" | "var" | "const" | "let" | "export" | "default" | "interface" | "extends" | "implements" | "new" | "this" | "throw" | "try" | "catch" | "finally" | "switch" | "case" | "break" | "continue" | "typeof" | "instanceof" | "void" | "public" | "private" | "protected" | "package" | "object" | "fun" | "val" | "when" | "is" | "in") { return Some("keyword"); }
    None
}
fn visit(node: tree_sitter::Node<'_>, out: &mut Vec<HighlightSpan>) {
    if node.child_count() == 0 {
        if let Some(kind) = classify(node.kind()) {
            if node.start_byte() < node.end_byte() {
                out.push(HighlightSpan { start_byte: node.start_byte(), end_byte: node.end_byte(), kind });
            }
        }
    }
    let mut cursor = node.walk();
    for child in node.children(&mut cursor) { visit(child, out); }
}
fn visit_errors(node: tree_sitter::Node<'_>, out: &mut Vec<ParseDiagnostic>) {
    if node.is_error() || node.is_missing() {
        out.push(ParseDiagnostic { start_byte: node.start_byte(), end_byte: node.end_byte(), missing: node.is_missing(), message: node.kind().to_owned() });
    }
    let mut cursor = node.walk();
    for child in node.children(&mut cursor) { visit_errors(child, out); }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test] fn path_selects_python_and_typescript() {
        assert!(MultiLanguageDocument::new("hello.py", "def f(): return 1").is_ok());
        assert!(MultiLanguageDocument::new("index.ts", "const n: number = 1;").is_ok());
        assert!(MultiLanguageDocument::new("unknown.xyz", "").is_err());
    }
    #[test] fn incremental_edits_and_spans_work_for_multiple_languages() {
        let mut py = MultiLanguageDocument::new("main.py", "def f(): return 1").unwrap();
        let at = py.source().find('1').unwrap();
        py.replace(at..at + 1, "2").unwrap();
        assert!(py.source().contains("return 2"));
        assert!(py.spans().iter().any(|s| s.kind == "keyword"));
        let js = MultiLanguageDocument::new("main.js", "function f(){ return 'x'; }").unwrap();
        assert!(js.spans().iter().any(|s| s.kind == "string"));
    }
}
