# Native PTY Terminal

## Implemented in this milestone

- Rust PTY creation using `openpty` and a terminal window size.
- Child process session creation with `setsid`, controlling-terminal assignment, and stdio attached to the PTY slave.
- Background master-side reader that streams output without blocking JNI calls.
- PTY input writes, window resizing with `TIOCSWINSZ`, and foreground process-group signal delivery.
- Signal allow-list for INT, TERM, TSTP, CONT, HUP, and WINCH.
- JNI methods for create, write, poll, liveness, resize, signal, and close.
- Kotlin terminal session prefers the native PTY when `libbyte_core.so` is loadable and retains the pipe-backed Android shell as a development fallback.
- Terminal shortcut row routes Tab, Esc, arrow keys, and Ctrl+C into the terminal session.

## Important integration limits

- The Android Gradle project does not yet build/package `byte_core` for Android ABIs. Until cargo-ndk/NDK packaging is configured and the shared library is installed in the APK, the UI uses the pipe fallback.
- The PTY engine and JNI code have been committed but have not been compiled or run on Android in this environment.
- The UI currently displays raw PTY output as text; a full ANSI/VT100 screen-buffer renderer (colors, cursor addressing, alternate screen, scroll regions, mouse modes) remains future work.
- A native resize API exists, but automatic measurement of the rendered terminal viewport and resize propagation is not yet connected.
- The native engine supports multiple independent handles; the current Compose workspace owns one active terminal session. Session tabs and persistence are not yet implemented.
- Shell job control depends on the child shell and Android environment. Signals are sent to the PTY's current foreground process group, but full job-control UX and testing remain pending.
- Pipe fallback does not provide terminal line discipline or reliable Ctrl+C semantics.

## Build and validation still required

1. Configure cargo-ndk and Android targets (at minimum arm64-v8a; add armeabi-v7a and x86_64 where supported).
2. Package `libbyte_core.so` under the Android JNI library directories for each ABI.
3. Run `cargo test` on a supported host and Android NDK builds for all packaged ABIs.
4. Run Gradle assemble and instrumentation tests on Android 10+ devices.
5. Verify interactive shell behavior, terminal resize, signal delivery, process cleanup, and background/foreground lifecycle.
