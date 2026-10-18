# Implementation Plan: In-App Video Playback, Sandbox Isolation & Notification Quick Replies

## 1. Overview & User Journey
This plan establishes three core functional enhancements across the application:
1. **Built-in Video Player for Browser Downloads**: Tapping any downloaded video in the Browser Downloads tab directly opens the app's native, immersive `VideoPlayerModal` with gesture-based seek, zoom-to-fit aspect ratio controls, and sound/brightness adjustments.
2. **Strict In-App Sandbox Clone Isolation**: Opening any sandboxed app clone (e.g. Instagram, WhatsApp) strictly launches inside the dedicated in-app WebView workspace without triggering device app launches or handling external intent deep links.
3. **Notification Quick Reply with Confirmation**: Replying directly to messages (e.g. WhatsApp, SMS, socials) from within the App Alerts / Notification section without leaving the screen, complete with an inline confirmation bubble showing the sent text.

---

## 2. Visual Architecture & Component Hierarchy
- **Video Player Overlay (`VideoPlayerModal`)**:
  - Full-screen black backdrop with immersive controls (`WindowInsetsCompat.Type.systemBars()`).
  - Animated controls overlay with Play/Pause, 10s Skip Forward/Back, Scrub Slider, Zoom/Fit aspect toggle, and volume/brightness HUD.
  - Smooth dismiss via close button or system back gesture (`BackHandler`).
- **Sandbox Workspace View (`CloneWorkspaceScreen`)**:
  - Clean top app bar with instance badge, tab title, URL bar, desktop mode toggle, reload, and close to dock.
  - Isolated sandboxed WebView blocking `intent://`, `instagram://`, `whatsapp://`, `market://` from starting external Android intents, maintaining navigation within the in-app container.
- **Notification Card (`NotificationCard`)**:
  - App-branded header with avatar, app name, and time ago.
  - Notification title and message preview.
  - Interactive "Reply" action button that expands an inline Material 3 text field and "Send" button.
  - Sent state displays an inline confirmation bubble: `✓ Replied: "[reply text]"` with subtle tonal container styling.

---

## 3. Technical Implementation & Data Flow
- **Browser Downloads to Player**:
  - `BrowserDownloadsScreen`: Detects video MIME types / extensions (`mp4`, `mkv`, `webm`, `avi`, `mov`). Wraps the file into a `MediaItem` and invokes `onPlayVideo(mediaItem)`.
  - `BrowserHubScreen`: Routes `onPlayVideo` callback to the top level.
  - `MainActivity`: Updates `uiState.selectedVideoItem` in `XenoViewModel` and renders `VideoPlayerModal` at top-level z-index whenever `selectedVideoItem != null`.
- **Sandbox Container Containment**:
  - `SpaceSandboxScreen`: Ensure click handlers on dock chips and installed cards invoke `onLaunchInstance(clone.instanceId)` only, without calling `InstalledAppsManager.launchInstalledApp`.
  - `CloneWorkspaceScreen`: In `shouldOverrideUrlLoading`, deep links (`intent://`, `instagram://`, `whatsapp://`, `fb://`, `tg://`, `market://`) return `true` to cancel external intent dispatch.
- **Notification Direct Reply**:
  - `MessagesScreen`: Binds `onReplyNotification` callback through `DeviceNotificationsWindow` to `NotificationCard`.
  - `NotificationCard`: Manages inline reply text state, dispatches reply payload, and renders a persistent sent bubble.
  - `XenoViewModel`: Updates UI notification state and posts feedback confirmation via `userNotice`.

---

## 4. Verification & Testing Steps
1. **Compilation Check**: Run `compile_applet` to confirm error-free Gradle build.
2. **Video Playback Verification**: Test clicking downloaded video files in the Downloads tab and ensure `VideoPlayerModal` renders full screen.
3. **Sandbox Launch Verification**: Test opening the Instagram clone in the sandbox space and verify it stays in-app without triggering external device intents.
4. **Notification Quick Reply Verification**: Test expanding the reply field on a notification in the App Alerts tab, submitting text, and confirming the sent status bubble.
