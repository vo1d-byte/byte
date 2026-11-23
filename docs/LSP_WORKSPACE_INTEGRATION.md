# LSP Workspace Integration

## Implemented

`LspWorkspaceManager` owns one local language-server process per language for a workspace:

- Explicit language-to-server profile registration.
- One process session per language ID.
- LSP initialize/initialized handshake.
- Queued didOpen notifications until initialization completes.
- Version-increasing didChange and didClose notifications.
- Completion, hover, and definition request routing.
- publishDiagnostics routing into observable Compose state.
- Main-thread delivery for UI state and feature callbacks.
- Executable path restriction to Byte's app-private toolchains directory.

## Registering a server

The toolchain installer must first place a verified executable in the app-private toolchains directory. Register an explicit argument vector, never a shell command string:

```kotlin
manager.register(
    LspServerProfile(
        languageId = "python",
        command = listOf(File(toolchainsDir, "python/bin/pylsp").absolutePath)
    )
)
```

This is an API example only. No pylsp binary is bundled or installed by this milestone.

## Document lifecycle

Call `openDocument(uri, languageId, version, text)` when a file is opened. Forward edits to `changeDocument` with strictly increasing versions and call `closeDocument(uri)` when a tab closes. Use the manager's completion, hover, and definition methods with UTF-16 LSP positions.

Observe the Compose snapshot's `diagnosticsByUri` for Problems UI data.

## Security and remaining work

- Server executables must be checksum-verified by a future signed toolchain manager.
- The manager does not run shell commands or project scripts.
- Initialize capabilities are currently minimal and not fully negotiated.
- Request cancellation, timeouts, restart/backoff, multi-root workspaces, semantic tokens, and diagnostics clearing on server exit remain future work.
- MainActivity editor tabs are not yet connected to this manager.
- No server binary is bundled, and Gradle/device tests have not been run.
