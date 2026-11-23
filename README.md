# Byte IDE

**Byte IDE** is an Android-first, extensible development environment designed to bring serious software development workflows to phones and tablets.

The project combines **Kotlin** for Android platform integration and UI with **Rust** for performance-sensitive core services.

> **Project status:** Early foundation / proof of architecture. This repository does not yet deliver the complete feature set in the roadmap. A production terminal, language toolchain installers, build runners, debuggers, emulators, and cross-platform packaging backends still require implementation and device-level validation.

## Vision

- Install and manage language toolchains and ecosystems through one-tap, versioned environment profiles.
- Edit source code with project navigation, diagnostics, syntax highlighting, and language-server integration.
- Develop Android applications and preview/run them on-device.
- Develop websites and web applications with an integrated browser preview.
- Create 2D and web games with live preview workflows.
- Export projects to Linux, Windows, macOS, Android, and iOS where target toolchains and signing requirements permit.
- Manage Git repositories, branches, commits, diffs, remotes, and GitHub workflows.
- Browse, search, create, move, and manage project files.
- Provide a real shell, process management, debugging, and professional developer workflows.

## Architecture

    Android application (Kotlin)
      ├── Compose UI
      ├── Workspace / project manager
      ├── Editor and preview surfaces
      ├── Android platform integrations
      └── JNI bridge
            |
            v
    Native core (Rust)
      ├── Workspace and filesystem services
      ├── Toolchain registry and environment manager
      ├── Process / task orchestration
      ├── Git integration
      ├── Build and export orchestration
      └── Plugin capability interfaces

## Technology

| Area | Technology | Responsibility |
|---|---|---|
| Android app | Kotlin, Jetpack Compose | UI, lifecycle, permissions, Android APIs |
| Native core | Rust | Fast, memory-safe core services |
| Native bridge | JNI and jni crate | Kotlin-to-Rust calls |
| Build system | Gradle / Android Gradle Plugin | Android packaging |
| Native build | Cargo + Android NDK | Rust Android libraries |
| Version control | Git (planned integration) | Repository operations |
| Editor | Extensible editor surface (planned) | Editing and language features |
| Language support | Toolchain + LSP adapters (planned) | Ecosystem integrations |

## Repository layout

    .
    ├── app/                         # Android application
    │   └── src/main/
    │       ├── java/dev/byteide/app/
    │       └── res/values/
    ├── core/                        # Rust native core
    │   ├── src/
    │   └── Cargo.toml
    ├── docs/                        # Architecture and roadmap
    ├── gradle/
    ├── build.gradle.kts
    ├── settings.gradle.kts
    └── README.md

## Getting started

Requirements:
- Android Studio with a recent stable Android SDK.
- JDK 17.
- Android SDK Platform and Build Tools matching the project configuration.
- Android NDK and CMake for native Android builds.
- Rust stable toolchain for native development.
- Android Rust targets such as aarch64-linux-android, armv7-linux-androideabi, and x86_64-linux-android.

Open the repository root in Android Studio, allow Gradle to sync, install the required SDK components, and run the app configuration on an Android device or emulator.

Build the Rust core from the core directory with cargo test and cargo build. The Rust core is host-testable. JNI entry points are provided for Android; build and package the Android shared library with cargo-ndk as documented in [JNI Integration](docs/JNI_INTEGRATION.md). Gradle automation and device validation remain pending.

## Mobile interaction design

The Android workspace follows the supplied 14-screen mobile IDE brief: editor-first portrait layout, an output drawer with collapsed/normal/expanded states, persistent file tabs, contextual code and terminal key rows, a cursor-pad concept, thumb-reachable quick actions, and a landscape output/editor split. See [Mobile IDE Interaction Specification](docs/MOBILE_IDE_INTERACTION_SPEC.md) for the detailed behavior and implementation status. The current UI is an interaction prototype; OS keyboard visibility, true drag/peek behavior, persistent caret/selection, and production backends are still pending.

## Design principles

1. Android-native first: respect Android lifecycle, storage, permission, and background execution constraints.
2. Real capabilities over fake controls: a feature is implemented only when it invokes a working backend and reports errors honestly.
3. Modular language support: language packs describe toolchains, package managers, formatters, language servers, debuggers, and build adapters.
4. Safe execution: untrusted project code must run with explicit user consent and a constrained execution model.
5. Offline-friendly: editing and installed toolchains should work without a network connection.
6. Resource-aware: install components on demand and support cleanup of caches and toolchains.

## Roadmap

### Foundation
- [x] Android application scaffold with Compose.
- [x] Rust core crate and JNI entry-point foundation.
- [x] Initial architecture and roadmap documentation.
- [ ] Automated Android and Rust CI builds.
- [ ] Device ABI matrix and native packaging.

### Editor engine (first implementation)
- [x] Rope-backed Rust text document with Unicode-aware scalar offsets.
- [x] Selection, cursor movement, replacement, backspace, and forward delete.
- [x] Edit-based undo/redo history and revision tracking.
- [x] Kotlin editor-session model preserving UTF-16 selection and viewport state.
- [x] JNI adapter for document lifecycle, text retrieval, range replacement, selection, undo, and redo.\n- [ ] Automated Android ABI packaging and device instrumentation tests.
- [ ] Grapheme-aware navigation, grouped undo, multi-cursor, syntax parsing, and LSP.

### Workspace and editing
- [ ] Project creation and workspace persistence.
- [ ] File explorer, file operations, search, and tabs.
- [x] Initial Compose editor rendering with line-number gutter and Kotlin lexical coloring.
- [x] Kotlin Tree-sitter parser with incremental InputEdit updates and syntax spans.\n- [x] JNI bridge and Compose rendering of native Kotlin syntax spans.\n- [x] Compiled grammar registry with Kotlin and Rust parsers and extension detection.\n- [x] Compiled grammar registry: Kotlin, Rust, Python, JavaScript, and TypeScript.\n- [x] Add path-selected multi-language syntax sessions and unified highlighting spans for Kotlin, Rust, Python, JavaScript, and TypeScript.\n- [x] Path-selected multi-language syntax documents and JNI/Compose integration.\n- [ ] Language-specific query captures, full diagnostics UI, and LSP.\n- [ ] LSP diagnostics and language server lifecycle.
- [x] LSP JSON-RPC framing, request correlation, and document sync primitives.\n- [ ] LSP client stdio transport and language server lifecycle management.

### Execution and language ecosystems
- [ ] Real interactive PTY terminal with resize, signals, and job control.
- [ ] Sandboxed process runner and task console.
- [ ] Signed, declarative language-pack catalog.
- [ ] One-tap toolchain install, update, rollback, and removal.
- [ ] Storage quotas, checksums, and install verification.

### Preview, debugging, and export
- [ ] Local web server and integrated browser preview.
- [ ] Android build / install / logcat workflow.
- [ ] 2D and web game templates and preview.
- [ ] Debug Adapter Protocol integration.
- [ ] Linux, Windows, macOS, Android, and iOS export adapters, subject to host and signing constraints.

### Collaboration
- [ ] Git status, diff, commit, branch, merge, and remote operations.
- [ ] GitHub authentication and repository workflows.
- [ ] Project backup, import, and export.

## Platform constraints

Android restricts background processes, executable locations, filesystem access, and long-running services. Some toolchains do not support Android as a host. Building Windows and macOS applications generally requires platform-specific SDKs and/or remote build runners. macOS signing and distribution require Apple tooling and credentials. iOS packaging and signing require Apple-specific tooling and provisioning; an Android-only device cannot independently replace every part of that workflow. Android app builds require compatible SDK / NDK versions and sufficient storage and memory.

Byte should expose these requirements clearly and offer remote build runners where local execution is not technically possible.

## Security

- Verify downloaded toolchains using signed manifests and pinned checksums.
- Require consent before running project scripts or network-facing development servers.
- Keep secrets out of logs and repository files.
- Treat imported projects and plugins as untrusted input.

## Contributing

Contributions are welcome. Please open an issue before large architectural changes.

## License

No license has been selected yet. Until a license is added, all rights remain with the copyright holder; do not assume the code is available under an open-source license.
\n\n## LSP runtime\n\nThe Android process owner and protocol framing foundation are documented in [LSP Runtime](docs/LSP_RUNTIME.md). Language-server installation, workspace discovery, full lifecycle wiring, and device validation remain pending.\n

### LSP intelligence
- [x] Typed Kotlin facade for completion, hover, definition, references, rename, formatting, and publishDiagnostics parsing.
- [ ] Connect LSP notifications and request callbacks to active editor tabs and Compose UI.
- [ ] Debounce/cancel requests, suppress stale responses, and negotiate server capabilities/position encoding.
- [ ] Apply WorkspaceEdit and TextEdit as version-checked undoable editor transactions.

See [LSP Editor Intelligence](docs/LSP_INTELLIGENCE.md).

### LSP Compose UI
- [x] Reusable completion popup, hover card, diagnostics panel, and error/warning summary components.
- [ ] Mount components in the workspace and connect live LSP events to Compose state.
- [ ] Apply completion and WorkspaceEdit through version-checked undoable editor transactions.
- [ ] Add inline diagnostic decorations and real-server/device tests.

See [LSP Compose UI](docs/LSP_COMPOSE_UI.md).

### LSP workspace integration
- [x] Workspace-scoped manager with one local server session per language.
- [x] Initialize handshake, queued document open, versioned document changes, and close notifications.
- [x] Route completion, hover, definition, and publishDiagnostics to observable workspace state.
- [x] Restrict server executables to Byte's private toolchains directory and use argument arrays rather than shell strings.
- [ ] Connect manager lifecycle to actual editor tabs and file URIs in MainActivity.
- [ ] Ship verified language-server toolchain profiles; add cancellation, timeout, restart, and capability negotiation.
- [ ] Run Gradle and on-device tests.

See [LSP Workspace Integration](docs/LSP_WORKSPACE_INTEGRATION.md).

### Live editor LSP integration
- [x] Editor controller binds a tab to the workspace manager and document lifecycle.
- [x] Debounced completion requests (180 ms) with stale-response suppression.
- [x] Hover and definition request helpers.
- [x] Basic identifier-prefix completion insertion.
- [ ] Mount controller and LSP surfaces in MainActivity/editor overlay.
- [ ] Register verified installed server profiles through the toolchain installer.
- [ ] Apply completion text edits/snippets and WorkspaceEdit via undoable native editor transactions.
- [ ] Run Gradle compilation and Android device tests.

See [Live Editor LSP](docs/LIVE_EDITOR_LSP.md).

### Actual editor UI integration
- [x] Mount LSP completion popup and hover card over the editor surface.
- [x] Connect active document edits to the LSP editor controller.
- [x] Replace the static Problems tab with the LSP diagnostics panel and count.
- [x] Navigate from a diagnostic to its reported start position.
- [x] Queue documents until a language-server profile is registered.
- [ ] Install/register verified language-server profiles; validate real completion and diagnostics.
- [ ] Add hover and definition editor actions, textEdit/snippet support, and inline diagnostic decorations.
- [ ] Run Gradle compilation and Android device tests.

See [Actual Editor UI Integration](docs/ACTUAL_EDITOR_UI_INTEGRATION.md).

### Language toolchain manager
- [x] Kotlin installer for app-private ZIP toolchains with HTTPS-only downloads.
- [x] Pinned SHA-256 verification, bounded download/extraction, and ZIP path traversal protection.
- [x] Staging-directory publication and installed package registry.
- [x] Optional language-server registration after package installation.
- [x] Rust catalog schema validation with tests for empty catalogs, HTTP rejection, and unsafe IDs.
- [ ] Authenticate catalog metadata with an app-pinned signing key.
- [ ] Populate catalog with reviewed Android-host-compatible packages and verified artifact digests.
- [ ] Add Compose package browser, install/update/rollback/removal, and ABI/dependency resolution.
- [ ] Run Cargo tests, Gradle build, and Android device tests.

See [Toolchain Manager](docs/TOOLCHAIN_MANAGER.md).