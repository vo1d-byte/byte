# Byte

**A native, offline-first mobile IDE for Android, written in Rust and Kotlin.**

Byte aims to let a software engineer do real work without a laptop: edit, run, debug, version and ship projects from a phone or tablet. The performance-critical core (text buffer, PTY, toolchain installer, Git) is Rust; the UI and Android integration are Kotlin with Jetpack Compose.

> **Status: v0.1, foundation.** This repository contains the architecture, the Rust core and a Kotlin/Compose shell that implements the adaptive layout from the design spec. It is an early scaffold, not a finished IDE. See [Status](#status) for an honest breakdown. **The code has not been compiled or run yet** (it was written without a Rust/Android toolchain available), so expect to fix build errors on the first build.

## Goals

| Goal | Approach |
| --- | --- |
| Typing-first UX on a 6" screen | Auto-folding output panel, floating action button, extra-key row, trackpad-style cursor editor ([UI spec, Arabic](docs/ui-spec.ar.md)) |
| Fast and light | Rope-based buffer, native PTY, no background daemons |
| Offline-capable | Toolchains install from local archives; editing, Git and terminal need no network |
| Any language, one tap | Declarative toolchain manifests (`toolchains/*.toml`) verified by SHA-256 |
| Apps, sites, 2D/Web games | Run and preview through an embedded WebView and a device-side build pipeline (planned) |
| Export to desktop / mobile | Local build where possible; GitHub Actions templates where the target OS is required |
| Git / GitHub | libgit2 through Rust (`byte-git`) with token auth |
| Real terminal | PTY allocated natively (`byte-term`) |

## Status

| Area | State |
| --- | --- |
| Rope buffer: undo/redo, smart pairs, duplicate line, toggle comment, word/line select, UTF-16 sync | Implemented (Rust, with unit tests) |
| Sandboxed project file operations, atomic writes | Implemented |
| Multi-tab session with auto-save | Implemented |
| PTY terminal (spawn, read, write, resize) | Implemented; UI input is **line-based** and ANSI escapes are stripped |
| Toolchain manifests, SHA-256 verification, safe tar.gz extraction | Implemented |
| Git: status, commit, clone, push (token) | Implemented (basic) |
| JNI bridge | Implemented |
| Layout state machine (fold, peek, terminal expand, landscape) | Implemented, with JVM unit tests |
| Compose UI: output panel, tabs, editor, floating `^` menu, key row, cursor editor | Implemented, first pass |
| Syntax highlighting, completion, LSP client | Planned |
| Debug adapter (DAP) client | Planned |
| Full VT/ANSI terminal emulation (`vte`) | Planned |
| File tree drawer, Git panel, search and replace UI | Planned (screens not designed yet) |
| Customize-actions screen, two-finger undo, hardware keyboard shortcuts | Planned |
| Proot rootfs manager for glibc toolchains | Planned |
| Web / 2D game preview | Planned |
| Export: Linux, Windows, macOS, APK, iOS | Planned (see constraints) |

## Platform constraints

These are real limits that shape the design.

1. **Executing downloaded binaries.** Android 10+ blocks `exec` from an app's writable directory for apps targeting API 29+. Byte therefore targets **API 28** and is meant to be **sideloaded** (F-Droid / GitHub releases), like Termux. It cannot ship on Google Play with this model.
2. **glibc toolchains.** Most upstream toolchains (Python, Node, GCC) are built for glibc, while Android uses bionic. Byte plans to run them in a **proot-based Linux rootfs**. Statically linked tools (for example Go) can run natively. Manifests declare which runtime they need.
3. **iOS and macOS export** require Apple's toolchain and cannot be built on an Android device. Byte generates the project and a GitHub Actions workflow (`templates/`) so a macOS runner produces the artifact. Windows and Linux desktop targets use local cross-compilation when the toolchain allows it, and CI otherwise.
4. **APK builds on-device** need an ARM64 Android build toolchain (aapt2, d8, apksigner). This is planned through the rootfs; until then APK export can go through CI.

## Architecture

```
Android (Kotlin / Compose)
  IdeScreen, OutputPanel, FileTabs, EditorPane, FloatingActions, KeyRow, CursorEditor
  LayoutState (pure logic)   EditorState   TerminalSession
        |  JNI: dev.byte.ide.core.Native
byte-jni (libbyte_jni.so)
  byte-core        byte-term     byte-toolchain       byte-git
  buffer/project   PTY           manifest + sha256    libgit2
  session/tabs                   safe extraction
```

Details: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Repository layout

```
Cargo.toml              Rust workspace
crates/byte-core        buffer, project files, tab session
crates/byte-term        PTY
crates/byte-toolchain   manifests, verification, installation
crates/byte-git         Git operations
crates/byte-jni         JNI bridge (cdylib)
android/                Gradle project (Kotlin + Compose)
toolchains/             One-tap language manifests
templates/              CI templates (ci.yml, export-macos-ios.yml)
tools/pin_manifest.py   Fills SHA-256 hashes into manifests
docs/                   Architecture and UI spec
```

## Building

Requirements: JDK 17, Android SDK + NDK, Rust (stable) with Android targets, and [`cargo-ndk`](https://github.com/bbqsrc/cargo-ndk).

```bash
rustup target add aarch64-linux-android x86_64-linux-android
cargo install cargo-ndk

# Rust unit tests (host)
cargo test -p byte-core -p byte-term -p byte-toolchain

# Android debug APK (builds the Rust library with cargo-ndk first)
cd android
gradle wrapper        # one-time: generates the Gradle wrapper
./gradlew assembleDebug testDebugUnitTest
```

Pass `-PskipCargo` to Gradle to skip the Rust step when `jniLibs` is already populated. `byte-git` vendors libgit2 and OpenSSL, so the first Android build is slow and needs `perl` and `make`.

**CI:** copy `templates/ci.yml` to `.github/workflows/ci.yml` to build the APK on GitHub Actions (useful when you only have a phone).

## Toolchain manifests

Each language is one TOML file in `toolchains/`:

```toml
id = "go"
version = "1.23.3"
runtime = "native"            # or "rootfs"
[[artifacts]]
abi = "linux-arm64"
url = "https://.../go1.23.3.linux-arm64.tar.gz"
sha256 = "PIN_ME"
format = "tar_gz"
```

Installation **refuses** any artifact whose hash is missing or still `PIN_ME`. Pin hashes with `python3 tools/pin_manifest.py toolchains/*.toml` and review the diff before committing. For offline use, put the archive on the device and install from the local file.

## UI behaviour

Implemented in `LayoutState.kt` and covered by unit tests:

- Output panel snaps between collapsed, normal (25%) and expanded (80%); focusing the terminal widens it to about 40%.
- Opening the system keyboard or the cursor editor folds the panel to a thin line with an error dot; tapping the line peeks the panel **over** the editor without moving code.
- Landscape moves the panel to a left column (about 33%) and folds both panel and tabs while typing.
- Touch targets are at least 44 dp.

## Roadmap

1. Get v0.1 compiling and running on a device; fix integration errors.
2. Syntax highlighting (tree-sitter) and a Rust-backed editor surface.
3. VT100/ANSI terminal emulation with raw key input.
4. Proot rootfs manager and the first working one-tap toolchains.
5. LSP and DAP clients.
6. File tree, Git and search panels.
7. Web/2D preview, then export pipelines.

## Contributing

Issues and pull requests are welcome. Please run `cargo fmt`, `cargo clippy` and the unit tests before submitting.

## License

Not chosen yet. Add a `LICENSE` file before accepting outside contributions.
