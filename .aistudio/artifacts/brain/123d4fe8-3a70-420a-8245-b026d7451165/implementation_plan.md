# Persistent Floating Window & Unstoppable Background Service Architecture

Provide uninterrupted background persistence for the floating window overlay, ensuring no external memory killer or OS background limit pauses or stops the floating window and its background tasks until manually terminated by the user.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following user preferences were confirmed during Phase 1:
> - **System Overlay Behavior**: True system overlay (`TYPE_APPLICATION_OVERLAY`) hovering over all third-party apps and home screens.
> - **Persistence Level**: Full persistence using sticky foreground service (`START_STICKY`), wake lock holding (`PowerManager.PARTIAL_WAKE_LOCK`), battery optimization exemption request (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`), and `BOOT_COMPLETED` receiver.
> - **Manual Termination Controls**: Notification quick action button ("Stop Floating App") and an explicit power/exit button inside the floating window top bar.

---

## 1. Overview & Core Concept

- **What It Does**: Upgrades `FloatingBubbleService` into an unstoppable, persistent Android system overlay that runs continuously in the foreground. External app switches, task clearers, or low-memory pressure events cannot kill or freeze the floating window or background threads.
- **Target Experience**: The floating icon / window remains rendered above all Android applications. The service runs as a sticky foreground process with an active ongoing system notification. The user can manually stop or dismiss the floating service at any time using either the notification quick action or the power button in the window header.

---

## 2. User Experience & Visual Design

- **Floating Top Bar Power Button**:
  - Add a dedicated Power/Exit action icon (`Icons.Filled.PowerSettingsNew` / `Icons.Filled.Close`) in `FloatingWindowTopBar.kt`.
  - Tapping this button presents a confirmation toast / dialog ("Stop Rabbit Service?"), allowing the user to shut down the persistent background service completely.
- **Ongoing Persistent System Notification**:
  - Displays a high-priority ongoing notification ("Rabbit Floating Engine Active") with a "Stop Service" action button (`PendingIntent` targeting `ACTION_STOP`).
- **Battery Optimization Exemption Card in Settings**:
  - Add a dedicated status card in `FloatingSettingsDialog.kt` indicating whether Battery Saver / Doze mode exemptions are granted, with a 1-tap button to launch `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

---

## 3. Technical Architecture & Component Structure

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          Android OS System Layer                        │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │
                    BOOT_COMPLETED / App Launcher
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                     FloatingBubbleService (Foreground)                  │
│                                                                         │
│  - START_STICKY onStartCommand()                                         │
│  - Partial Wake Lock (PowerManager)                                     │
│  - Ongoing Foreground Notification ("Rabbit Active")                    │
│  - Quick Action "Stop Service" Intent                                   │
│  - TYPE_APPLICATION_OVERLAY WindowManager View                         │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │
                                 Taps Bubble
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        MainActivity Floating Window                     │
│                                                                         │
│  - Translucent System Card Frame                                        │
│  - FloatingWindowTopBar (Minimize / Fit / Settings / Power Exit Button)  │
│  - Active Tab View (Messages / Browser / Clone Space)                    │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Key Technical Decisions & Implementation Steps

### Step 1: Upgrade `FloatingBubbleService` Foreground Persistence
- Configure `onStartCommand` to return `START_STICKY` so the Android OS automatically resurrects the service if killed under memory pressure.
- Acquire a `PowerManager.WakeLock` (`PARTIAL_WAKE_LOCK`) inside `FloatingBubbleService` while active to prevent CPU sleeping during background network calls or clone tasks.
- Add an explicit `ACTION_STOP` broadcast / intent handler to clean up wake locks, remove system overlay views, and invoke `stopSelf()`.

### Step 2: Add Notification Quick Action ("Stop Service")
- Update `buildNotification()` in `FloatingBubbleService` to include a `NotificationCompat.Action` pointing to `ACTION_STOP`.
- Ensure tapping "Stop Service" in the notification drawer cleanly stops the service and dismisses the floating bubble icon.

### Step 3: Add In-Window Power/Exit Button in `FloatingWindowTopBar`
- Update `FloatingWindowTopBar.kt` to include a Power/Close button on the far right.
- Tapping this button invokes an `onStopApp` callback that sends `ACTION_STOP` to `FloatingBubbleService` and finishes `MainActivity`.

### Step 4: Battery Optimization Exemption Setup
- Implement a helper function `isIgnoringBatteryOptimizations(context)` and `requestIgnoreBatteryOptimizations(context)` in `FloatingBubbleService`.
- Add an interactive card in `FloatingSettingsDialog.kt` allowing the user to verify and request battery optimization exemption for uninterrupted execution during Doze mode.

### Step 5: Service Resurrection Receiver (`BootReceiver`)
- Create `FloatingBootReceiver` listening for `ACTION_BOOT_COMPLETED` and `ACTION_MY_PACKAGE_REPLACED` to automatically restart `FloatingBubbleService` on device reboot if enabled in preferences.
