# Multi-language Grammar Registry

## Scope

Byte now has a central Rust registry for the grammars compiled into the native core. Each descriptor provides a stable language ID, canonical name, display name, file extensions, and comment delimiters. Callers can resolve a language by name or infer it from a file path, then request a configured Tree-sitter parser without depending on a language-specific constructor.

## Compiled grammar coverage in this stage

| Language | Extensions | Status |
|---|---|---|
| Kotlin | `.kt`, `.kts` | Compiled |
| Rust | `.rs` | Compiled |

The registry API is designed to add further compiled or separately packaged grammar providers. It does not claim support for Python, JavaScript, TypeScript, C/C++, HTML, CSS, JSON, or Bash yet.

## API

- `languages()`: list installed descriptors.
- `language_by_name(name)`: resolve canonical names and aliases.
- `language_for_path(path)`: detect grammar from file extension.
- `create_parser(name)`: create and configure a Tree-sitter parser.
- `parse(name, source)`: parse a source string.

## Security and packaging boundary

This registry does not download or execute grammar code. A future downloadable grammar catalog must use signed manifests, pinned hashes, ABI compatibility checks, architecture-aware packages, and explicit user consent before installation. Dynamically loading arbitrary shared libraries from a project directory is intentionally not supported.

## Validation

Unit tests cover alias resolution, extension detection, Kotlin and Rust parsing, and unknown-language rejection. Cargo tests and Android builds have not been run in this environment.
