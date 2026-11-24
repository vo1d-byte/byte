# Architecture

## Principles

1. **Rust owns the data and the heavy work**: text buffers, PTY, archive verification, Git. Kotlin owns drawing and Android integration.
2. **The buffer of truth is in Rust.** The Compose text field is a view: on every change the UI passes the new string to `bufSync`, which computes the smallest edit (common prefix/suffix) and records it for undo/redo. Commands (`duplicateLine`, `insertPair`, `toggleComment`, cursor motion) run in Rust and the UI pulls the result.
3. **Offsets**: Rust uses Unicode scalar indices; Android uses UTF-16. The buffer converts at the boundary (`selection_utf16`, `utf16_to_char`).
4. **Layout is a pure function.** `computeLayout(LayoutInput) -> LayoutSpec` encodes every rule from the UI spec (fold, peek, terminal expand, landscape) and is covered by JVM unit tests. Compose only renders the result.
5. **No work when idle.** No background services; one auto-save tick every 1.5 s that does nothing if no tab is dirty.

## Modules

| Crate / package | Responsibility |
| --- | --- |
| `byte-core::buffer` | Rope buffer, undo/redo with typing coalescing, smart pairs, line commands, selection |
| `byte-core::project` | Path-sandboxed file operations, atomic writes |
| `byte-core::session` | Tabs; each tab keeps its own buffer, cursor and history |
| `byte-term` | PTY via `posix_openpt`, spawn with a controlling terminal, resize |
| `byte-toolchain` | TOML manifests, SHA-256 verification (unpinned hashes refused), safe `tar.gz` extraction, atomic install |
| `byte-git` | libgit2: init/open, status, commit-all, clone, push with token |
| `byte-jni` | Handle-based JNI API, panic-safe, string results prefixed `ERR:` on failure |
| `dev.byte.ide.ui` | Compose UI and layout logic |

## Toolchain install flow

1. The app downloads the archive for the device ABI (or the user picks a local archive: offline path).
2. `toolchainInstall` parses the manifest, **verifies SHA-256**, extracts to `<root>/toolchains/<id>/<version>.partial`, then renames into place.
3. `bin` directories are added to `PATH` for the terminal and runner. Manifests with `runtime = "rootfs"` will be launched through proot once the rootfs manager exists.

## Security notes

- Archive entries with `..`, absolute paths or absolute link targets are rejected.
- Project paths cannot escape the project root.
- Git tokens are passed per call and never stored by the Rust layer. Store them with Android Keystore in the app (not implemented yet).

## Export design (planned)

| Target | Strategy |
| --- | --- |
| Linux, Windows | Cross-compile locally when the language toolchain supports it; otherwise generate a CI workflow |
| macOS, iOS | Generate project + `templates/export-macos-ios.yml`, push to GitHub, run on a macOS runner |
| Android APK | Local build through the rootfs toolchain (aapt2/d8/apksigner), CI as fallback |
| Web / games | Bundle and serve locally, preview in WebView; static export as zip |
