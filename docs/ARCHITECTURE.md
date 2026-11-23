# Architecture

## Boundary

Kotlin owns Android lifecycle, UI, permissions, storage access, notifications, and platform APIs. Rust owns portable core logic, workspace indexing, task orchestration, package metadata, Git orchestration, and build planning.

## Native boundary

Use a narrow JNI interface with explicit request/response types. Do not pass Android framework objects into Rust. Keep long-running work asynchronous and cancellable; never block the main thread.

## Toolchain packs

A language pack should be declarative and signed. It describes supported Android ABIs, versions, download artifacts, checksums, install steps, environment variables, LSP servers, formatters, debuggers, and build adapters. Installation must be transactional and support rollback.

## Execution model

A PTY-backed terminal and task runner are separate services. Projects are untrusted: execution requires consent, clear working-directory boundaries, output limits, cancellation, and secret redaction. Android sandbox restrictions must be respected.

## Cross-platform builds

Local Android builds can use Android SDK/NDK components. Linux, Windows, macOS, and iOS outputs may require remote runners, platform-specific SDKs, Apple signing assets, and credentials. The UI must communicate these constraints instead of promising impossible local builds.
