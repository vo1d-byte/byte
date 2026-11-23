# Editor Rendering Engine

## First rendering pass

The Android editor surface now uses a dedicated Compose component, `ByteCodeEditor`, instead of a plain unadorned text field. It provides a line-number gutter based on the current document line count, monospace text, a fixed line height, and lightweight Kotlin lexical coloring through Compose VisualTransformation.

TextFieldValue remains the source of truth for text, selection, and IME composition. The visual transformation uses OffsetMapping.Identity and does not rewrite document content.

## Correctness boundaries

The current highlighter is a lightweight regex-based lexical pass, not a parser and not a substitute for Tree-sitter or a language server. It colors common Kotlin keywords, common types, strings, comments, and numeric literals. It does not provide semantic highlighting, nested-language support, diagnostics, folding, or incremental parse trees.

The gutter currently renders the complete line-number sequence. A later pass must synchronize gutter scrolling with the editor viewport and add horizontal scrolling, current-line highlighting, indentation guides, and viewport virtualization for very large documents.

## State and lifecycle

The composable receives TextFieldValue from EditorSession and does not own a second text buffer. EditorSession is closed when its keyed file session leaves composition, releasing its native document handle.

## Validation

Source files have been updated in the repository. A Gradle Android build and device test have not been run in this environment; runtime rendering and native library availability remain unverified.
