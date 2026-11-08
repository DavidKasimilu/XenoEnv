# Implementation Plan - In-App WhatsApp Calling & Draggable Floating PiP Overlay

Implement seamless, in-app WhatsApp calling directly from **Contacts**, **Call History**, and **Keypad** screens without exiting the application or switching away from your current screen. Calls initiate in-process via the embedded WhatsApp engine and present a global, draggable floating Picture-in-Picture (PiP) call overlay equipped with live call controls.

---

## Technical Approach & Architecture

### 1. In-App WhatsApp Calling Session Management
- **ViewModel State**: Extend `XenoViewModel` with `whatsAppCallOverlayState: StateFlow<WhatsAppCallSession?>` to track ongoing in-app WhatsApp call sessions (`phoneNumber`, `contactName`, `isVideo`, `callPhase`, `durationSeconds`, `isMuted`, `isSpeakerOn`).
- **In-Process Call Trigger**: When initiating a WhatsApp call from Contacts, Call History, or Keypad, route the call through the embedded WhatsApp Web sandbox renderer without launching external Android Intent choosers or leaving the app.

### 2. Global Draggable Floating PiP Overlay Component (`WhatsAppCallFloatingOverlay.kt`)
- Build a custom Compose overlay rendered at the root level of `XenoApp` in `MainActivity.kt` above all tab screens.
- **Draggable Gesture**: Support smooth 2D dragging using `Modifier.pointerInput` with `detectDragGestures` and bounds clamping so users can position the PiP card anywhere on screen while continuing to use the app.
- **Dynamic Call Phases**:
  1. **Calling / Ringing Phase**: Shows recipient avatar, name/number, "WhatsApp Voice/Video Call...", animated pulse ring, and a red End Call button.
  2. **Answered / Connected Phase**: Displays call duration counter (`00:00`), interactive **Mute / Unmute** button, **Speakerphone** toggle, **Camera Switch** (for video calls), and **End Call** button.
  3. **Minimizable & Expandable**: Tap to collapse into a compact floating pill or expand back to full PiP controls.

### 3. Contacts, Call History, & Keypad Integration
- Update WhatsApp call actions in `ContactsScreen.kt`, `CallsScreen.kt`, `KeypadScreen.kt`, and `InAppNotificationOverlay.kt` to invoke `viewModel.startInAppWhatsAppCall(number, name, isVideo)` instead of external intents.
- Ensure the app stays strictly on the user's active screen (Contacts, Call History, Messages, etc.) without navigating away.

---

## Proposed Changes

### UI & Overlay Components
#### `app/src/main/java/com/example/ui/components/WhatsAppCallFloatingOverlay.kt`
- Create `WhatsAppCallFloatingOverlay` composable with animated entry, smooth drag gestures, recipient details, live call timer, and control action callbacks.

#### `app/src/main/java/com/example/MainActivity.kt`
- Render `WhatsAppCallFloatingOverlay` at the top level inside `XenoApp` scaffold above bottom navigation and tab screens.

### State & Business Logic
#### `app/src/main/java/com/example/ui/viewmodel/XenoViewModel.kt`
- Add `whatsAppCallSessionState` flow and helper methods (`startInAppWhatsAppCall`, `toggleWhatsAppCallMute`, `toggleWhatsAppCallSpeaker`, `endInAppWhatsAppCall`).
- Connect background audio/video controls with the WhatsApp sandbox renderer.

### Screen Call Action Integration
#### `app/src/main/java/com/example/ui/screens/ContactsScreen.kt`
- Direct WhatsApp Call icon button click to `viewModel.startInAppWhatsAppCall(...)`.

#### `app/src/main/java/com/example/ui/screens/CallsScreen.kt`
- Direct WhatsApp call actions in Call History to `viewModel.startInAppWhatsAppCall(...)`.

#### `app/src/main/java/com/example/ui/screens/KeypadScreen.kt`
- Direct WhatsApp dial button long-press or menu option to in-app WhatsApp calling.

---

## Verification Plan

### Manual Verification Steps
1. **Initiate Call from Contacts**: Open Contacts screen, tap the WhatsApp Call button for a contact. Verify that the app **does NOT exit** or open external apps.
2. **Verify Floating PiP Overlay**: Confirm a draggable PiP card appears on top of the Contacts screen showing "Calling [Name] via WhatsApp...". Drag the card around the screen to ensure smooth positioning.
3. **Test Call Controls**:
   - Verify Mute/Unmute toggles audio state.
   - Verify Speakerphone toggles audio output mode.
   - Verify End Call dismisses the overlay and cleans up resources cleanly.
4. **Test Across Screens**: Navigate between Contacts, Call History, Messages, and Space while a WhatsApp call is active, confirming the floating PiP overlay remains accessible across all screens.
