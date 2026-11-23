# Language Package Registry and Installer UI

## Implemented

- Added an Android asset-backed language package catalog parser.
- Added a searchable, category-filtered package browser dialog.
- Added installed toolchain inventory.
- Connected package Install actions to the asynchronous ToolchainInstaller.
- Exposed install phase messages and progress state in package cards.
- Added an explicit empty-catalog state rather than presenting unverified downloads as installable.

## Catalog placement

The Android UI reads app/src/main/assets/language-packs.json. The repository starter catalog remains at catalog/language-packs.json. Both are currently empty and must be kept synchronized when reviewed packages are added.

## Remaining work

- Signed catalog verification and remote refresh.
- ABI filtering/enforcement at install time.
- Package removal, update, rollback, dependency resolution, and storage accounting.
- Background-safe resumable downloads.
- Verified real language artifacts and device tests.
