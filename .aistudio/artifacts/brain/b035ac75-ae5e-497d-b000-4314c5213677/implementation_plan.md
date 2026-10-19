# Implementation Plan: Fix Black Screen on Launch

## 1. Root Cause Analysis
The screen turns black on launch because `WindowManager.LayoutParams.FLAG_SECURE` was applied to the Activity window when `preventOverlayApps` is enabled (`true` by default).
- In Android, `FLAG_SECURE` instructs `SurfaceFlinger` to block screen capture and virtual display rendering by outputting a **solid black frame** to streamed displays, virtual displays, and screen-mirroring pipelines.
- Removing `FLAG_SECURE` immediately restores normal screen rendering while keeping true anti-overlay protection active.

---

## 2. Technical Fix Strategy
1. **Remove `FLAG_SECURE` from `MainActivity.kt`**:
   - Explicitly clear `WindowManager.LayoutParams.FLAG_SECURE` (`win.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)`) so the app window never renders as black.
2. **Retain Non-Blanking Anti-Overlay Protection**:
   - Keep `win.setHideOverlayWindows(uiState.preventOverlayApps)` (Android 12+) which instructs the system window manager to hide third-party `TYPE_APPLICATION_OVERLAY` windows over the app.
   - Keep `win.decorView.filterTouchesWhenObscured = uiState.preventOverlayApps` to block any touch events if another window attempts to draw over the app.

---

## 3. Files to Modify
- `app/src/main/java/com/example/MainActivity.kt`: Remove ` win.setFlags(WindowManager.LayoutParams.FLAG_SECURE, ...)` and ensure `FLAG_SECURE` is cleared.

---

## 4. Verification Plan
- Run `compile_applet` to verify the updated build compiles cleanly and launches with full UI visibility.
