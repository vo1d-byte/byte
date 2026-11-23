# Tree-sitter Syntax Engine

## Implemented Kotlin parser

The Rust core includes a persistent Kotlin Tree-sitter parser using `tree-sitter-kotlin-ng`. `KotlinSyntaxDocument` parses initial source, retains its syntax tree, applies byte-range replacements with Tree-sitter `InputEdit`, and reparses using the edited previous tree. It returns changed byte ranges, lexical spans, and parser error/missing-node diagnostics.

## Android integration

The JNI layer exposes parser creation, destruction, incremental replacement, and syntax-span retrieval. Span offsets are converted from Tree-sitter UTF-8 byte offsets to UTF-16 code-unit offsets before crossing JNI, matching Compose's AnnotatedString indexing.

`NativeKotlinSyntax` owns the native parser handle. `EditorSession` sends the minimal changed source range to both the text document and syntax parser, then publishes the updated syntax spans. `ByteCodeEditor` styles those spans. The regex highlighter remains a fallback when the native parser is unavailable.

## Coordinate contract

Tree-sitter edits use UTF-8 byte offsets and reject boundaries that split a UTF-8 code point. Android editing uses UTF-16 code units; the JNI span output is explicitly converted to UTF-16. The text document engine uses Unicode scalar-value offsets. These coordinate systems must never be interchanged.

## Initial language coverage

Only Kotlin is wired in this stage. Captures currently cover common keywords, strings, comments, numeric literals, type identifiers, and selected literals. Syntax error and missing-node data are collected in Rust, but a dedicated Problems panel adapter is not yet implemented. This is not semantic highlighting and does not replace LSP diagnostics.

## Performance notes

The parser and syntax tree are retained per editor session. Incremental edits allow Tree-sitter to reuse unaffected syntax structure. The current implementation walks the resulting tree to collect the full span list after each edit; future work should use changed ranges to update only affected display spans and debounce large edit bursts.

## Validation status

The crate dependencies and API were checked against the published `tree-sitter-kotlin-ng 1.1.0` documentation. Source changes and JNI integration have been committed, but Cargo tests, Gradle compilation, Android ABI packaging, and on-device runtime tests have not been run. Build success is therefore not yet verified.
