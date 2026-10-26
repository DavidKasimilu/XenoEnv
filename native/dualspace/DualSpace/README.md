# Dual Space (work-profile cloner)

Creates an Android work profile and clones installed apps into it, so each
clone has its own data (e.g. a second WhatsApp/Instagram account).

## How it works
1. **Create Dual Space** fires `ACTION_PROVISION_MANAGED_PROFILE`; Android builds
   an isolated user profile and copies this app into it as the *profile owner*.
2. `AdminReceiver.onProfileProvisioningComplete` runs in the new profile: it enables
   `CloneActivity` there and registers a cross-profile intent filter.
3. In the main profile you tap an app -> `startActivity(CLONE intent)`. Android
   forwards it across the boundary to `CloneActivity` in the work profile.
4. `CloneActivity` calls `DevicePolicyManager.installExistingPackage()` to give
   the profile its own copy of the app. It shows in the launcher with a briefcase badge.

## Build
Push to GitHub. The workflow in `.github/workflows/build.yml` builds the debug APK;
download it from the run's **Artifacts** (`DualSpace-debug-apk`).
Local: `gradle assembleDebug` (Gradle 8.9+, JDK 17, Android SDK 34).

## Limitations
- Won't work if the device already has a device owner / managed profile, or if the
  OEM disabled work profiles.
- Clone requests are not authenticated; harden `CloneActivity` before shipping.
- Some apps detect work profiles and refuse certain features.
