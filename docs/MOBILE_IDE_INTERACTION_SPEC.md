# Mobile IDE Interaction Specification

This document records the supplied 14-screen design brief and maps its interaction model to the Byte IDE implementation.

## Core principles
- Writing is the default state; secondary tools stay one touch away.
- One interaction model adapts to phone and tablet sizes.
- Output-panel changes must not reset the editor document, selection, or caret.
- Customization is optional; all touch targets are at least 44 dp.

## Portrait layout
- Output drawer: draggable/snap states at collapsed (20 dp peek), normal (~25%), and expanded (~80%).
- File tabs: persistent horizontal strip with 44 dp minimum touch targets and a new-file action.
- Editor: consumes remaining space and remains the visual focus.
- Floating quick-action control: 48 dp, thumb reachable, short press opens actions; long press is intended to run the configured default command.

## Output drawer
Tabs: Output, Terminal, Problems (with count), Debug, Logs.
Only the handle/header should initiate drawer dragging; content remains independently scrollable.
A collapsed state must retain a visible error indicator and allow a temporary peek overlay without resizing the editor.

## Keyboard integration
When the system IME appears, collapse the output drawer to its peek row and show a horizontally scrollable 44 dp code-key row above the keyboard.
The terminal context swaps in Ctrl, Esc, Tab, arrows, pipe, tilde, slash, dash, and Ctrl+C.
Paired delimiters should insert both characters and place the caret between them.
A cursor pad replaces the system keyboard and provides a trackpad, selection modes, navigation, clipboard actions, and an ABC return action.

## Quick actions
First layer: Run, Find, Undo, More.
Second layer: Debug, Files, Git, Replace, Go to definition, Comment line, Duplicate line, Format, Back, Customize.
The action list is user-customizable; the first layer is limited to four commands.

## Landscape and tablet
In landscape, place output in a left column (~33%) and editor/tabs on the right. Keep the same interaction states and actions.
Tablet may reduce output proportion (e.g. 15%) and optionally support split editors.

## Accessibility and quality
- Minimum 44 dp touch targets.
- Text contrast target 4.5:1.
- Never communicate errors by color alone.
- Icon-only controls need accessible descriptions.
- Preserve document and caret state through panel, orientation, and input-mode transitions.

## Implementation status
The current Android UI is an interaction prototype. The system IME visibility integration, true draggable drawer, overlay peek, persistent editor/caret model, trackpad gesture handling, physical keyboard shortcuts, profiles/customization, Git/Files screens, and real build/terminal backends remain implementation tasks. See README for the broader platform roadmap.
