# Language Server Protocol foundation

## Implemented

The Rust core contains an initial LSP transport/protocol module in `core/src/lsp.rs`.

- JSON-RPC 2.0 request and notification constructors.
- LSP `Content-Length` framing encoder.
- Incremental frame decoder that accepts fragmented input and multiple frames per chunk.
- Configurable maximum payload size.
- Pending request ID tracking and response correlation.
- Document lifecycle notifications: `didOpen`, `didChange`, and `didClose`.
- Monotonic document version checks for full-text changes.
- Shutdown and exit message constructors.

## Tests authored

Unit tests cover partial frame delivery, multiple messages in one chunk, invalid content lengths, payload limits, request correlation, and document version tracking. These tests are source-level additions and have not yet been run with Cargo.

## Not implemented yet

This module is not a complete LSP client and does not launch language servers. The following remain future work:

1. Android process supervisor and stdio pipes.
2. Server installation / executable discovery and lifecycle.
3. Initialize handshake and server capability negotiation.
4. Mapping LSP diagnostics to editor markers and Problems panel.
5. Completion, hover, definition, references, rename, formatting, and code actions.
6. Cancellation, timeouts, restart policy, and graceful process termination.
7. Per-language server profiles (for example rust-analyzer, basedpyright/Pyright, and TypeScript language server).

## Android constraints

Language servers must be packaged or installed as compatible executables for Android ABIs and run within Android's process and storage restrictions. Some upstream servers do not publish Android-host builds. Byte must report unavailable host targets rather than imply desktop binaries can run unchanged.

## Validation status

The LSP module is committed and its source has been fetched from GitHub. Cargo tests, Gradle builds, JNI integration, and on-device process tests have not been run.
