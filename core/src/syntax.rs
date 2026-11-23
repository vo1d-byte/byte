//! Incremental Tree-sitter syntax engine.
//!
//! Source ranges are UTF-8 byte offsets, matching Tree-sitter and Rust strings.
//! Android/Compose adapters must convert these ranges to UTF-16 before styling.

use std::ops::Range;
use tree_sitter::{InputEdit, Parser, Point, Tree};

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SyntaxSpan {
    pub start_byte: usize,
    pub end_byte: usize,
    pub kind: &'static str,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SyntaxDiagnostic {
    pub start_byte: usize,
    pub end_byte: usize,
    pub is_missing: bool,
    pub node_kind: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct SyntaxUpdate {
    /// Byte ranges whose syntax tree structure changed after this edit.
    pub changed_ranges: Vec<Range<usize>>,
    pub spans: Vec<SyntaxSpan>,
    pub diagnostics: Vec<SyntaxDiagnostic>,
}

#[derive(Debug)]
pub enum SyntaxError {
    LanguageUnavailable,
    ParseFailed,
    InvalidUtf8Boundary,
}

impl std::fmt::Display for SyntaxError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::LanguageUnavailable => write!(f, "Kotlin grammar could not be loaded"),
            Self::ParseFailed => write!(f, "Tree-sitter could not parse the source"),
            Self::InvalidUtf8Boundary => write!(f, "Edit range is not on UTF-8 character boundaries"),
        }
    }
}

impl std::error::Error for SyntaxError {}

/// A persistent Kotlin parser. Edits update the previous tree with InputEdit,
/// then reparse against it instead of parsing the complete file from scratch.
pub struct KotlinSyntaxDocument {
    parser: Parser,
    source: String,
    tree: Option<Tree>,
}

impl KotlinSyntaxDocument {
    pub fn new(source: impl Into<String>) -> Result<Self, SyntaxError> {
        let mut parser = Parser::new();
        parser
            .set_language(&tree_sitter_kotlin_ng::LANGUAGE.into())
            .map_err(|_| SyntaxError::LanguageUnavailable)?;

        let source = source.into();
        let tree = parser.parse(&source, None).ok_or(SyntaxError::ParseFailed)?;
        Ok(Self { parser, source, tree: Some(tree) })
    }

    pub fn source(&self) -> &str {
        &self.source
    }

    pub fn update(&mut self) -> SyntaxUpdate {
        self.collect_update(Vec::new())
    }

    pub fn replace(
        &mut self,
        range: Range<usize>,
        replacement: &str,
    ) -> Result<SyntaxUpdate, SyntaxError> {
        let start = range.start.min(self.source.len());
        let end = range.end.min(self.source.len()).max(start);
        if !self.source.is_char_boundary(start) || !self.source.is_char_boundary(end) {
            return Err(SyntaxError::InvalidUtf8Boundary);
        }

        let start_position = point_for_byte(&self.source, start);
        let old_end_position = point_for_byte(&self.source, end);
        let new_end_position = advance_point(start_position, replacement);
        let old_tree = self.tree.take().ok_or(SyntaxError::ParseFailed)?;

        let mut edited_tree = old_tree;
        edited_tree.edit(&InputEdit {
            start_byte: start,
            old_end_byte: end,
            new_end_byte: start + replacement.len(),
            start_position,
            old_end_position,
            new_end_position,
        });

        self.source.replace_range(start..end, replacement);
        let new_tree = self
            .parser
            .parse(&self.source, Some(&edited_tree))
            .ok_or(SyntaxError::ParseFailed)?;

        let changed_ranges = edited_tree
            .changed_ranges(&new_tree)
            .map(|range| range.start_byte..range.end_byte)
            .collect();

        self.tree = Some(new_tree);
        Ok(self.collect_update(changed_ranges))
    }

    fn collect_update(&self, changed_ranges: Vec<Range<usize>>) -> SyntaxUpdate {
        let Some(tree) = self.tree.as_ref() else {
            return SyntaxUpdate { changed_ranges, ..SyntaxUpdate::default() };
        };

        let mut spans = Vec::new();
        let mut diagnostics = Vec::new();
        collect_nodes(tree.root_node(), &self.source, &mut spans, &mut diagnostics);
        SyntaxUpdate { changed_ranges, spans, diagnostics }
    }
}

fn point_for_byte(source: &str, byte: usize) -> Point {
    let prefix = &source[..byte];
    let row = prefix.bytes().filter(|byte| *byte == b'\n').count();
    let column = prefix.rsplit('\n').next().unwrap_or("").len();
    Point::new(row, column)
}

fn advance_point(start: Point, inserted: &str) -> Point {
    let lines: Vec<&str> = inserted.split('\n').collect();
    if lines.len() == 1 {
        Point::new(start.row, start.column + inserted.len())
    } else {
        Point::new(start.row + lines.len() - 1, lines.last().unwrap_or(&"").len())
    }
}

fn collect_nodes(
    node: tree_sitter::Node<'_>,
    source: &str,
    spans: &mut Vec<SyntaxSpan>,
    diagnostics: &mut Vec<SyntaxDiagnostic>,
) {
    if node.is_error() || node.is_missing() {
        diagnostics.push(SyntaxDiagnostic {
            start_byte: node.start_byte(),
            end_byte: node.end_byte(),
            is_missing: node.is_missing(),
            node_kind: node.kind().to_owned(),
        });
    }

    if node.child_count() == 0 {
        let kind = node.kind();
        let capture = match kind {
            "line_comment" | "multiline_comment" | "comment" => Some("comment"),
            "string_literal" | "string_content" | "character_literal" => Some("string"),
            "integer_literal" | "real_literal" | "number_literal" => Some("number"),
            "type_identifier" | "user_type" => Some("type"),
            "function_declaration" => Some("function"),
            "true" | "false" | "null" => Some("literal"),
            "package" | "import" | "class" | "object" | "interface" | "fun"
            | "val" | "var" | "if" | "else" | "when" | "for" | "while" | "do"
            | "return" | "break" | "continue" | "this" | "super" | "is" | "in"
            | "as" | "try" | "catch" | "finally" | "throw" | "public" | "private"
            | "protected" | "internal" | "override" | "open" | "abstract" | "data"
            | "sealed" | "enum" | "companion" | "suspend" | "inline" | "lateinit"
            | "const" | "operator" | "infix" | "tailrec" | "where" | "typealias" => {
                Some("keyword")
            }
            _ => None,
        };

        if let Some(kind) = capture {
            if node.start_byte() < node.end_byte() && node.end_byte() <= source.len() {
                spans.push(SyntaxSpan {
                    start_byte: node.start_byte(),
                    end_byte: node.end_byte(),
                    kind,
                });
            }
        }
    }

    let mut cursor = node.walk();
    for child in node.children(&mut cursor) {
        collect_nodes(child, source, spans, diagnostics);
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_kotlin_and_emits_highlight_spans() {
        let doc = KotlinSyntaxDocument::new("fun greet(name: String) = \"Hi $name\"").unwrap();
        let update = doc.update();
        assert!(update.spans.iter().any(|span| span.kind == "keyword"));
        assert!(update.spans.iter().any(|span| span.kind == "string"));
    }

    #[test]
    fn incremental_edit_updates_source_and_tree() {
        let mut doc = KotlinSyntaxDocument::new("fun main() { println(1) }").unwrap();
        let at = doc.source().find('1').unwrap();
        let update = doc.replace(at..at + 1, "42").unwrap();
        assert!(doc.source().contains("println(42)"));
        assert!(!update.spans.is_empty());
    }

    #[test]
    fn edit_rejects_non_utf8_boundaries() {
        let mut doc = KotlinSyntaxDocument::new("val face = \"🙂\"").unwrap();
        let at = doc.source().find('🙂').unwrap();
        assert!(matches!(doc.replace(at + 1..at + 2, "x"), Err(SyntaxError::InvalidUtf8Boundary)));
    }

    #[test]
    fn reports_parse_errors() {
        let doc = KotlinSyntaxDocument::new("fun main( {").unwrap();
        assert!(!doc.update().diagnostics.is_empty());
    }
}
