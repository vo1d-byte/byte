# Live Editor LSP Integration

## Editor controller

`LspEditorController` binds one `EditorSession` to a `LspWorkspaceManager` and provides:

- didOpen on controller creation and didClose on disposal.
- Versioned didChange forwarding for editor text updates.
- 180 ms debounced completion requests.
- Generation-based stale completion response suppression.
- Completion popup state populated only from actual LSP responses.
- Hover and go-to-definition request helpers.
- A basic completion insertion path that replaces the identifier prefix at the caret.

## Host integration contract

Create a controller only after the workspace manager is ready and a matching language-server profile has been registered. Use a stable file URI for each tab. Call `onEditorValueChanged(value)` after each accepted text change. Render `controller.uiState.completions` with `LspCompletionPopup`, and call `selectCompletion(item)` when an item is selected. Render diagnostics from `workspace.snapshot.diagnosticsByUri[documentUri]`.

The controller assumes the default LSP UTF-16 position encoding. The completion insertion path currently uses the identifier prefix and `insertText`/label; it does not yet interpret CompletionItem.textEdit, additionalTextEdits, snippets, or commit characters. For production use, route all edits through an undoable editor transaction and update the native document atomically.

## Current limitations

- MainActivity does not yet create/register installed language-server profiles, so no language gets live completion until a toolchain installer provides a verified executable.
- This controller is an integration API; it has not yet been mounted in the editor composable.
- Diagnostics must be observed from the workspace snapshot and passed into controller/UI state by the host.
- No Gradle or device tests have been run in this milestone.
