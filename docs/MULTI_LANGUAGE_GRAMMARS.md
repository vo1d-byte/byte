# Multi-language Grammar Registry

## Compiled grammar catalog

The Rust core now registers five Tree-sitter grammars:

| Language | File extensions | Registry status |
|---|---|---|
| Kotlin | `.kt`, `.kts` | Compiled |
| Rust | `.rs` | Compiled |
| Python | `.py`, `.pyi` | Compiled |
| JavaScript | `.js`, `.mjs`, `.cjs`, `.jsx` | Compiled |
| TypeScript | `.ts`, `.tsx` | Compiled |

The registry exposes descriptors with canonical name, display name, extensions, and comment delimiters. It resolves aliases and file extensions (case-insensitively), constructs a configured Tree-sitter parser, and parses source.

## Important integration boundary

This stage expands the Rust grammar registry and parser factory. The Android editor session is still connected to the Kotlin-specific syntax JNI bridge. Therefore Python, JavaScript, TypeScript and Rust are available to the Rust registry API, but are not yet automatically selected by Android editor tabs and do not yet have language-specific highlighting queries in the Compose editor.

## API

- `languages()`: list compiled language descriptors.
- `language_by_name(name)`: resolve canonical names and aliases.
- `language_for_path(path)`: detect a grammar from the file extension.
- `grammar(id)`: obtain the compiled Tree-sitter language.
- `create_parser(name)`: create and configure a parser.
- `parse(name, source)`: parse source text.

## Security

All grammars in this stage are compiled into the native library. No grammar code is downloaded or dynamically loaded. Future downloadable language packs must use signed manifests, pinned hashes, ABI compatibility checks, and explicit user consent.

## Validation

Unit tests cover name aliases, extension detection (including case-insensitive extensions), parser creation, and sample parsing for all five grammars. Cargo tests and Android builds have not been executed in this environment.
