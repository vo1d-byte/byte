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

Build the Rust core from the core directory with cargo test and cargo build. The initial crate is host-testable. Android cross-compilation and JNI packaging are follow-up integration tasks requiring the Android NDK toolchain.

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

### Workspace and editing
- [ ] Project creation and workspace persistence.
- [ ] File explorer, file operations, search, and tabs.
- [ ] Editor engine, syntax highlighting, and diagnostics.
- [ ] LSP client and language server lifecycle management.

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
