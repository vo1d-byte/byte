# Actual Editor UI Integration

This milestone mounts the existing LSP presentation components into the Android editor workspace.

## Wired into MainActivity

- Creates an app-private workspace and toolchains directory for the current prototype workspace.
- Creates a workspace-scoped LSP manager and an editor controller for the active Kotlin tab.
- Forwards editor text updates to the LSP controller.
- Displays the completion popup over the editor and applies selected basic completion text.
- Displays hover content when a hover response is requested by the controller.
- Replaces the static Problems tab body with the diagnostics panel.
- Updates the Problems tab count from diagnostics for the active document.
- Selecting a diagnostic restores the editor selection to its reported start position.

## Queueing before toolchain installation

The workspace manager now retains open documents when no server profile is registered. Registering a profile later attempts to start that language server and sends didOpen for queued documents after initialization. This allows editor tabs to exist before language packs are installed.

## Important limitations

- The prototype MainActivity does not yet register a language server profile. No Kotlin LSP executable is bundled, so live completions and diagnostics will remain empty until a verified toolchain installer installs and registers one.
- Hover is rendered but no dedicated hover gesture/button is wired in this milestone.
- Definition navigation is available on the controller but not yet exposed as an editor toolbar action.
- Completion insertion is still the basic insertText/label path. LSP textEdit, additionalTextEdits, snippets, and commit characters are not implemented.
- Diagnostic navigation selects the start offset; visual inline underlines/gutter markers are not implemented.
- The current file tab prototype uses a display name rather than a persisted workspace document URI.
- No Gradle build or device test was run as part of this repository update.
