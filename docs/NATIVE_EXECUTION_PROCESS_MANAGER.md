# Native Execution and Process Management

## Delivered

- Rust `ProcessManager` launches programs directly with argument arrays and explicit working directories.
- Child stdin is writable; stdout and stderr are streamed asynchronously as typed events.
- Processes can be polled, listed, terminated individually, and terminated as a group.
- Invalid program names, working directories, and environment variable keys are rejected.
- Added host-side tests for invalid specs and captured process output.
- Added an Android `TerminalSession` backed by a persistent app-private `/system/bin/sh`, with streamed output, command submission, and lifecycle cleanup.

## Scope boundary

The Android terminal currently uses pipes, not a PTY. It supports ordinary shell commands and output streaming, but not terminal line discipline, interactive full-screen programs, job control, terminal escape rendering, or reliable foreground Ctrl-C signal delivery. A PTY adapter using Android-compatible openpty/termios integration remains future work.

The Rust process manager is not yet wired through JNI into the Android terminal. The current Android shell session is Kotlin/ProcessBuilder-backed. Android native library packaging and device tests remain pending.

## Security

- The Rust API launches programs using argument arrays, without implicit shell interpolation.
- Shell usage is explicit and runs Android's system shell.
- The shell runs under the app's Android UID and app-private workspace; this is not a hardened sandbox.
- Imported project scripts should require user consent before execution.
