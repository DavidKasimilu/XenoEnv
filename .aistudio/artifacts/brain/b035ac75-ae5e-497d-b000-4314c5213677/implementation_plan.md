# Implementation Plan - Games Tab & APK Game Launcher

Add a new **Games** tab to the bottom navigation bar with a gamepad icon, a `+` button to pick and install `.apk` files from device storage (downloads folder), and the ability to view installed games and launch/play them from within the app.

## Proposed Changes

### 1. ViewModel & Data Models (`XenoViewModel.kt`)
- Add `GAMES` to `XenoTab` enum.
- Add `InstalledGameItem` data model (packageName, appName, apkUri, version, icon, launchIntent).
- Add state properties for installed games list and picker/installer handling.
- Implement methods to add/install APK from Uri (`installApk(uri)`), load installed third-party game apps from package manager, and launch games.

### 2. UI Components & Bottom Navigation (`XenoBottomBar.kt`)
- Update `XenoBottomBar` to include the **Games** tab with `Icons.Filled.SportsEsports`.

### 3. Games Screen (`GamesScreen.kt`)
- Create `GamesScreen.kt` featuring:
  - Header with title ("Games") and statistics.
  - Floating Action Button or top action button (`+`) to open system document picker for `.apk` files (`application/vnd.android.package-archive` or `*/*`).
  - LazyVerticalGrid or LazyColumn displaying installed games with icons, names, and a "Play" button.
  - Empty state with instructions on downloading open-source APK games from GitHub.

### 4. MainActivity (`MainActivity.kt`)
- Wire up `XenoTab.GAMES` to render `GamesScreen`.
- Implement `ActivityResultContracts.OpenDocument()` for picking APK files and triggering package installation intent (`ACTION_INSTALL_PACKAGE` / `ACTION_VIEW` with `application/vnd.android.package-archive`).

## Verification Plan

### Automated Tests
- Run `gradle :app:testDebugUnitTest` to ensure compilation and existing logic remain sound.

### Manual Verification
- Verify Games tab appears in bottom navigation with gamepad icon.
- Verify tapping `+` opens document picker to select `.apk` files.
- Verify installed games appear in list and launch successfully.
