# Language Server Protocol Runtime

## Scope

This stage adds a local LSP process owner for Android and hardens the Rust JSON-RPC framing primitives. It is the transport/lifecycle foundation; it does not bundle language servers or install them.

## Android process runtime

`LspServerProcess` starts an explicitly configured executable with `ProcessBuilder`, a project working directory, and caller-provided environment variables. The executable is expected to have been installed and verified by Byte's future toolchain manager in app-private storage.

The process owner provides:

- LSP Content-Length framed JSON-RPC messages over stdin/stdout.
- Separate stderr consumption so server logs do not corrupt protocol messages.
- Bounded message and header sizes.
- Request ID correlation and callbacks.
- `initialize` / `initialized`, generic requests and notifications.
- `textDocument/didOpen`, full-text `didChange`, and `didClose`.
- `shutdown` / `exit` and process termination.

## Rust protocol primitives

`core/src/lsp.rs` provides a streaming frame decoder, message encoder, request tracking, document version checks, and lifecycle notification builders. The decoder supports partial reads and multiple frames per input chunk and applies a configurable payload limit.

## Security and Android constraints

- This API does not download or execute project scripts.
- Callers must only pass executables installed and checksum-verified by the toolchain manager.
- Keep servers inside app-private storage; do not request broad storage permissions for them.
- Server stdout is reserved exclusively for LSP protocol frames; diagnostics belong on stderr.
- Android may terminate background processes. The workspace must own server lifetime and stop servers when projects close or the app is shutting down.
- Some language servers require runtimes or host capabilities unavailable on Android. Those servers need an alternate implementation or a remote language-server provider.
- The current process API is a foundation; workspace-level server discovery, per-language configuration, document synchronization integration, cancellation/timeouts, and device testing remain future work.

## Validation status

Source files were committed and fetched from GitHub. Cargo tests, Gradle compilation, and on-device process execution have not been run in this environment.
