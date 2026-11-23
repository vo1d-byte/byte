# LSP Editor Intelligence

This milestone adds a typed Kotlin feature facade over the existing local LSP process transport.

## Features exposed

- `textDocument/completion`: parses CompletionItem arrays and CompletionList results.
- `textDocument/hover`: normalizes string, MarkupContent, MarkedString-like arrays into displayable text.
- `textDocument/definition`: parses Location and LocationLink-style target selection ranges.
- `textDocument/references`: supports includeDeclaration context.
- `textDocument/rename`: returns the server WorkspaceEdit payload for a future safe edit-application layer.
- `textDocument/formatting`: returns TextEdit arrays for a future document edit transaction.
- `textDocument/publishDiagnostics`: parses ranges, messages, severity, code, source, and optional document version.

## Integration contract

Create one `LspIntelligence` per running server. Initialize the server first, then open documents using stable file URIs and language IDs. Forward unmatched `LspEvent.Message` notifications into `acceptNotification`; route request callbacks onto the main dispatcher before updating Compose state. Convert the editor caret's UTF-16 offset to LSP line/character using the negotiated position encoding (this initial facade assumes the LSP default UTF-16 encoding).

Do not apply WorkspaceEdit or formatting edits directly to the text field. They must be validated against the document version and committed as one editor transaction so undo/redo remains correct.

## Remaining work

- Wire the facade to the active editor tab and Compose completion / hover / diagnostics UI.
- Add debounce, cancellation, request timeouts, stale-response suppression, and server capability checks.
- Apply WorkspaceEdit and TextEdit through the Rust document transaction API.
- Add DAP, semantic tokens, signature help, document symbols, and code actions.
- Provide language-server packages and verified installation profiles.
- Run Gradle compilation, protocol fixture tests, and on-device integration tests.

This is an API integration milestone, not a claim that completion UI or real language servers have been tested on-device.
