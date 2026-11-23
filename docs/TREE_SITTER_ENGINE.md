# Tree-sitter Syntax Engine

## Scope of this implementation

The Rust core now contains a persistent Kotlin Tree-sitter parser. `KotlinSyntaxDocument` parses an initial source file, retains its syntax tree, and accepts byte-range replacements. Each replacement edits the old tree with Tree-sitter `InputEdit` coordinates and reparses using the edited tree as the previous tree. The update result exposes changed byte ranges, syntax spans, and parser error/missing-node diagnostics.

## Coordinate contract

All syntax span and edit offsets are UTF-8 byte offsets. This is Tree-sitter's native coordinate system and is not interchangeable with Kotlin/Compose UTF-16 offsets or Rust editor scalar-value offsets. The Android bridge must convert at the boundary. Edits that split a UTF-8 code point are rejected.

## Initial language coverage

This stage registers the Kotlin grammar through `tree-sitter-kotlin-ng`. The syntax engine is structured so additional grammar adapters can be added as language packs; other grammars are not claimed to work yet.

The first capture pass emits lexical categories for common Kotlin keywords, strings, comments, numeric literals, and type identifiers. It also records Tree-sitter error and missing nodes as syntax diagnostics. This is an initial grammar-backed foundation, not a complete semantic-highlighting query set.

## Performance model

A parser and syntax tree are retained for the lifetime of a syntax document. Text changes should be submitted as the smallest valid changed byte range. Tree-sitter can then reuse unaffected portions of the previous tree. The current span collector walks the resulting tree to build a complete span list; a later optimization can use changed ranges to update only affected render spans.

## Next integration tasks

- Expose syntax-document lifecycle and incremental edits through JNI.
- Convert UTF-8 spans into UTF-16 Compose ranges.
- Replace the Kotlin regex highlighter with Rust-provided syntax spans.
- Add grammar packs for Rust, JavaScript/TypeScript, JSON, HTML, CSS, Python, and Java.
- Add language-server diagnostics separately from parser syntax errors.

## Validation status

Unit tests are included for Kotlin parsing, incremental edits, UTF-8 boundary safety, and parse errors. A Cargo test run and Android build have not been executed in this environment; dependency resolution and runtime behavior remain to be verified by CI.
