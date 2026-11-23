# Editor Engine

## Design

The editor is split into a document model and view state. The document owns text, revision, selection, and edit history. The view owns viewport and focus. Output-panel, keyboard, and orientation state must not recreate either object.

## Rust document model

The native core uses Ropey for a rope-backed UTF-8 text buffer. Public positions are Unicode scalar-value offsets, not byte offsets. The API provides text/line metrics, selection, replacement, insertion, backspace/delete, cursor movement, line-column lookup, undo, and redo.

Undo history stores inverse edit records. A new edit clears the redo stack. Revisions advance on text mutations and undo/redo. No-op replacements do not create history entries.

## Kotlin boundary

Compose's TextFieldValue uses UTF-16 offsets. The Rust engine uses Unicode scalar offsets. A future JNI adapter must translate offsets explicitly and must not assume UTF-16 indices equal Rust character indices.

The Kotlin EditorSession keeps TextFieldValue, selection, revision, dirty state, and viewport line in a stable object. UI recomposition and output panel changes should not construct a new session.

## Current scope and limitations

- The first engine version is a single-document editing core, not yet a full editor widget.
- Unicode positions count scalar values; grapheme-cluster navigation is a later enhancement.
- Undo is edit-based, not yet grouped into typing transactions.
- Multi-cursor editing, syntax trees, incremental parsing, LSP, diagnostics, folding, and language-aware completion are future layers.
- JNI/Cargo Android ABI packaging is not wired yet; the Rust core can be tested independently on a host with Cargo.

## Tests

The Rust test suite covers Unicode positions, selection replacement, undo/redo, backspace, line/column mapping, cursor extension, and no-op history behavior.
