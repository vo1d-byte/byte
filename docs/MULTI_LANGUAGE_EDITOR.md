# Multi-language Editor Integration

## What this stage adds

The Rust core now exposes `MultiLanguageDocument`, a language-agnostic Tree-sitter document. It selects a compiled grammar from a file path using the grammar registry, keeps a parser and syntax tree alive for the document lifetime, applies incremental edits, and exposes unified highlight spans and parse diagnostics.

A JNI bridge exposes document creation (path + source), destruction, incremental byte-range replacement, highlight spans, and parse diagnostics. Syntax ranges are converted from UTF-8 byte offsets to UTF-16 code-unit offsets before being returned to Compose.

Android now has `NativeSyntaxDocument`, a Kotlin owner for the native handle. `EditorSession` creates it using its file path, sends minimal edits to it, and publishes spans and diagnostics as Compose state. The editor receives the spans in both portrait and landscape layouts.

## Supported compiled grammars

- Kotlin: .kt, .kts
- Rust: .rs
- Python: .py, .pyi
- JavaScript: .js, .mjs, .cjs, .jsx
- TypeScript: .ts, .tsx

## Current limitations

- Highlight classification is a shared initial node-kind classifier, not full language-specific Tree-sitter queries.
- Diagnostics are retrievable through JNI but are not yet rendered in the Problems panel.
- The initial workspace still opens Main.kt; opening arbitrary project files is a separate workspace feature.
- The old Kotlin-specific syntax bridge remains for compatibility.
- LSP, completion, symbol navigation, and formatting are not implemented.

## Coordinate contract

Tree-sitter edit positions use UTF-8 byte offsets. Compose spans use UTF-16 code-unit offsets. Kotlin computes byte offsets at the boundary and JNI converts output spans back to UTF-16. Never pass UTF-16 indices directly as Tree-sitter byte offsets.

## Validation

Files were committed and fetched back from the repository. Cargo tests, Gradle compilation, Android ABI packaging, and on-device tests have not been run. The implementation is committed but not build-verified.
