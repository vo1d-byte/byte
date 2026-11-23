# Language Toolchain Manager

## Delivered foundation

`ToolchainInstaller` installs catalog-supplied ZIP packages asynchronously into app-private storage.

- HTTPS-only artifact downloads; HTTP downgrade redirects are rejected.
- Pinned SHA-256 verification before extraction.
- Compressed size, expanded size, and entry-count limits.
- ZIP path traversal and absolute-path protection.
- Staging-directory extraction followed by same-filesystem rename.
- Only explicitly declared executable paths are marked executable.
- Installed package metadata is persisted in an app-private JSON registry.
- An optional language-server executable can be registered with `LspWorkspaceManager` after installation.

## Catalog contract

The starter catalog uses `schemaVersion`, `catalogVersion`, and `packages`. Each package declares an ID, display name, language ID, version, HTTPS artifact URL, pinned SHA-256, ZIP format, executable paths, and optional language-server command.

The committed catalog intentionally contains no packages. No binary URLs and digests have been reviewed for this repository, so guessed catalog entries would be unsafe.

## Security and limitations

A pinned SHA-256 proves that downloaded bytes match the digest supplied by the catalog; it does not authenticate the catalog itself. Production must sign catalog metadata and verify it with an app-embedded public key.

Only ZIP archives are supported. Package scripts are never executed. Dependency bootstrapping, ABI resolution, updates, rollback, removal, quotas, and resumable downloads remain future work. Android host compatibility must be established for each package. This milestone has not been built or tested on a device.

## Storage

```
files/toolchains/<package-id>/<version>/
files/toolchains/installed.json
cache/<package-id>-<version>.zip.part
```
