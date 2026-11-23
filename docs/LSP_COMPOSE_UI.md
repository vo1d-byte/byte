# LSP Compose UI Components

This milestone adds reusable Compose surfaces for displaying actual LSP response models.

## Components

- `LspCompletionPopup`: displays completion items and delegates selection to the caller.
- `LspHoverCard`: displays hover documentation returned by the server.
- `LspProblemsPanel`: displays diagnostics with severity, source, code, and line/column; selecting a diagnostic is delegated to the editor.
- `LspDiagnosticsSummary`: compact error/warning count for tabs and toolbars.
- `LspEditorUiState`: immutable UI state model for completions, diagnostics, hover, and loading state.

## Integration boundary

These composables intentionally do not fabricate server responses or start processes. The workspace must create and initialize a language server, open the document, route `LspEvent.Message` notifications to `LspIntelligence.acceptNotification`, update Compose state on the main thread, and invoke feature requests with the current document URI and UTF-16 position.

Completion selection must be applied as a version-checked editor transaction, honoring `insertText` / `textEdit` and snippet capability. Diagnostic selection should move the editor caret to the diagnostic range. Markdown hover rendering is currently plain text; a safe Markdown renderer can be added later.

## Not yet complete

- Mount these components in the existing workspace Output/Problems panel and editor overlay.
- Add debounced completion requests and stale-response suppression.
- Apply completion edits and WorkspaceEdit through the Rust undoable document transaction API.
- Implement diagnostic decorations in the text editor gutter and inline ranges.
- Test against real language servers and Android devices.

No language server is bundled or automatically installed by this UI-only milestone.
