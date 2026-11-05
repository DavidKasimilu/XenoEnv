# Performance Audit Report & Startup Optimization Plan

This document provides a comprehensive, categorized performance audit report identifying the exact factors impacting app launch and operation speed, detailing code redundancies, and outlining safe architectural improvements that preserve all existing application features and functionalities.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> **Audit Confirmation & Scope**:
> In Phase 1, you confirmed the preference for a **Detailed audit report with specific bottlenecks and redundancies** covering **All startup areas (I/O, ViewModel init, and duplicate code)**.
> This report details the exact lines, mechanisms, and architectural trade-offs affecting launch latency and identifies dead/duplicate code.
> 
> **Next Step Recommendation**:
> Review the identified bottlenecks below. If approved, we can proceed to implement the non-breaking optimizations (lazy initialization, deferring MediaStore scans, debouncing sync passes, and removing dead models) without altering any existing UI, features, or behaviors.

---

## 1. Overview & Core Concept

- **What It Does**: Conducts a deep code-level audit of the Android application lifecycle, evaluating cold-boot execution paths (`MainActivity.onCreate`, `XenoViewModel.init`, and initial repository loads) and detecting redundant data structures and repeated queries.
- **Target Audience**: Developers and users experiencing perceived delay or stutter during application cold launch and initial frame rendering.
- **Key Value**: Delivers concrete diagnosis of what slows down app startup, why it occurs, and how to eliminate latency while maintaining 100% feature parity.

---

## 2. Startup Speed Audit: What Is Making The App Start Slow?

Through static profiling and architectural tracing of the cold-start path, four primary bottlenecks were identified:

### 1. Main-Thread SharedPreferences & JSON Parsing During ViewModel `init`
- **Location**: `XenoViewModel.kt` lines 246–256
- **Issue**:
  ```kotlin
  val initContacts = contactsRepo.getCachedContactsSnapshot()
  val initCalls = callLogRepo.getCachedCallLogsSnapshot()
  val initThreads = smsRepo.getCachedThreadsSnapshot()
  ```
  While intended as a "0ms Frame-1 Instant Seed", each of these methods executes synchronous file I/O (`context.getSharedPreferences(...)`) and then parses multi-kilobyte JSON strings (`JSONArray(raw)`) into memory directly on the Android UI Main Thread before the first frame can paint.
- **Impact**: On devices with moderate to large contact/call histories, parsing three JSON trees on the Main Thread blocks the Compose layout engine, causing frame drop and delayed activity rendering.

### 2. Eager Main-Thread Instantiation of 11 Separate Repositories
- **Location**: `XenoViewModel.kt` lines 216–226
- **Issue**:
  ```kotlin
  private val contactsRepo = ContactsRepository(application)
  private val callLogRepo = CallLogRepository(application)
  private val smsRepo = SmsRepository(application, contactsRepo)
  private val browserRepo = BrowserRepository(application)
  private val galleryRepo = GalleryRepository(application)
  private val spaceRepo = SpaceRepository(application)
  private val gameSpaceRepo = GameSpaceRepository(application)
  private val passwordRepo = PasswordRepository(application)
  private val downloadRepo = DownloadRepository(application)
  private val documentRepo = DocumentRepository(application)
  private val musicRepo = MusicRepository(application)
  ```
  All 11 repositories are eagerly created as field initializers during ViewModel construction. Each constructor opens individual `SharedPreferences` files (e.g. `xeno_space_prefs`, `xeno_game_space_prefs`, `xeno_music_favorites_prefs`, `xeno_gallery_prefs`) and creates directory structures (`mkdirs()`) synchronously.
- **Impact**: Heavy classloading overhead and repeated file descriptor opening before the user even sees the Home tab.

### 3. Full MediaStore Device Scan Triggered on Cold Start
- **Location**: `XenoViewModel.kt` lines 636–646 (called inside `boostAppPerformanceAndFastLoadHelper()`)
- **Issue**:
  ```kotlin
  if (_uiState.value.mediaItems.isEmpty() || _uiState.value.showMediaManager || (now - lastGalleryLoadMs) > 45_000L) {
      lastGalleryLoadMs = now
      viewModelScope.launch(Dispatchers.IO) {
          val media = galleryRepo.getMediaItems()
          _uiState.value = _uiState.value.copy(mediaItems = media)
      }
  }
  ```
  On cold boot, `_uiState.value.mediaItems` is always empty. Consequently, `galleryRepo.getMediaItems()` runs on every app launch. `GalleryRepository.getMediaItems()` performs a comprehensive `ContentResolver` query across all device photos and videos, canonicalizes file paths, and performs directory inspections.
- **Impact**: Severe disk I/O and ContentProvider contention while the app is attempting to render the Home/Calls screen.

### 4. Background I/O Stampede & CPU Thread Contention
- **Location**: `XenoViewModel.kt` lines 385–411 (`boostAppPerformanceAndFastLoadHelper()`)
- **Issue**:
  Upon ViewModel creation, the app launches multiple concurrent coroutines on `Dispatchers.IO`:
  - `syncCallHistoryFast()` (Call logs query)
  - `contactsRepo.getContacts(forceRefresh = true)` (Contacts query & indexing)
  - `smsRepo.getConversations()` (SMS threads query)
  - `galleryRepo.getMediaItems()` (Full media scan)
  - `browserRepo.loadTabs()`, `browserRepo.loadHistory()`
  - `spaceRepo.loadClonedInstances()`, `spaceRepo.loadActiveDock()`
  - `passwordRepo.getSavedPasswords()`, `downloadRepo.getTrackedDownloads()`
  - `gameSpaceRepo.loadUserGames()`
- **Impact**: All background threads compete for memory bandwidth, CPU cores, and SQLite database locks on the system `telephony.db` and `contacts2.db` providers.

### 5. Triple-Redundant Execution of `syncCallHistoryFast()`
- **Location**: `XenoViewModel.kt` line 387 -> line 592, line 628, and line 423
- **Issue**:
  - `syncCallHistoryFast()` is invoked explicitly at line 592.
  - The contacts coroutine at line 628 re-invokes `syncCallHistoryFast()` once contacts finish indexing.
  - `registerCallLogSyncHelper()` attaches a `ContentObserver` that immediately fires another `syncCallHistoryFast()`.
- **Impact**: The system `CallLog.Calls` provider is queried 3 times within the first 500ms of app startup.

---

## 3. Code Redundancies & Cleanup Areas

### 1. Dead Duplicate Data Models
- **Location**: `app/src/main/java/com/example/data/models/CommModels.kt` (lines 288–308)
- **Details**:
  - `data class IncomingSmsPopupData(...)`
  - `data class IncomingCallPopupData(...)`
  These two models are exact functional duplicates of `IncomingSmsPopup` and `IncomingCallPopup` in `app/src/main/java/com/example/data/models/InAppPopupModels.kt`. A codebase grep confirms that neither `IncomingSmsPopupData` nor `IncomingCallPopupData` is used anywhere in the codebase.

### 2. Redundant Carrier Name Resolvers
- **Location**: `CallsScreen.kt` line 783
- **Details**:
  In `RecentsCallCard`, the carrier name is computed dynamically during every recomposition:
  ```kotlin
  availableSims.find { it.subscriptionId == entry.simSlot }?.carrierName
  ```
  In a list with hundreds of calls, performing linear list scans during item layout creates unnecessary garbage collection allocations. A lookup map (`Map<Int, String>`) solves this in O(1).

### 3. Repeated Synthetic Priority Filter Checks
- **Location**: `CallLogEntry.displayName`, `ContactsRepository.kt`, `XenoViewModel.kt` lines 603 & 616
- **Details**:
  Calls to `PriorityContactManager.isSyntheticPriorityName(...)` are repeated multiple times on the same contact string throughout the pipeline rather than normalizing once at the repository boundary.

### 4. Speculative Delayed Sync Timers
- **Location**: `XenoViewModel.triggerPostCallHistorySync()`
- **Details**:
  Uses hardcoded `delay(350)` and `delay(750)` coroutines to poll call logs after a call finishes, even though `CallLogContentObserver` already captures database writes via system callbacks.

---

## 4. Key Product Decisions & Architectural Strategy

```
┌────────────────────────────────────────────────────────────────────────┐
│                        COLD LAUNCH PIPELINE                            │
└────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
                     ┌─────────────────────────────┐
                     │     MainActivity.onCreate   │
                     │  (Fast UI Theme & Scaffold) │
                     └──────────────┬──────────────┘
                                    │
                  ┌─────────────────┴─────────────────┐
                  ▼                                   ▼
      ┌──────────────────────┐            ┌──────────────────────┐
      │   PRIMARY STARTUP    │            │   LAZY SECONDARY     │
      │    (Immediate IO)    │            │     (On-Demand)      │
      ├──────────────────────┤            ├──────────────────────┤
      │ • CallLogRepository  │            │ • GalleryRepository  │
      │ • ContactsRepository │            │ • DocumentRepository │
      │ • SmsRepository      │            │ • MusicRepository    │
      │                      │            │ • BrowserRepository  │
      │  Single-pass sync    │            │ • SpaceRepository    │
      │  No duplicate calls  │            │ • PasswordRepository │
      └──────────────────────┘            └──────────────────────┘
```

### Proposed Safe Optimization Strategy (Zero Feature Alterations)

1. **Lazy Repository Delegation**:
   - Change secondary repositories (`galleryRepo`, `documentRepo`, `musicRepo`, `browserRepo`, `spaceRepo`, `gameSpaceRepo`, `passwordRepo`, `downloadRepo`) to Kotlin `lazy` initializers.
   - They will only be instantiated when the user opens their respective screens (Gallery, Space, Browser, Music), avoiding any file creation or SharedPreferences reads on cold start.

2. **Defer Cold-Start MediaStore Scan**:
   - Remove `galleryRepo.getMediaItems()` from `loadAllData(forceRefresh = true)`.
   - Trigger media loading only when `uiState.showMediaManager` becomes `true` or when the user navigates to the Gallery/Media tool.

3. **Single-Pass Coalesced Call Log Sync**:
   - Coalesce multiple `syncCallHistoryFast()` triggers in `XenoViewModel` using a coroutine debounce or atomic state check so the system CallLog query runs once, not three times concurrently.

4. **Off-Main-Thread Snapshot Seeding**:
   - Move JSON snapshot parsing in `getCachedContactsSnapshot()`, `getCachedCallLogsSnapshot()`, and `getCachedThreadsSnapshot()` into a fast background dispatcher job or lightweight cache read so the Main Thread never performs JSON string parsing during `Activity.onCreate`.

5. **Prune Dead Models & Pre-index SIM Carriers**:
   - Safely remove unused `IncomingSmsPopupData` and `IncomingCallPopupData`.
   - Convert `availableSims` into a keyed `Map<Int, String>` for instantaneous O(1) carrier name lookups in `RecentsCallCard`.

---

## 5. Verification & Testing Plan

1. **Compilation & Syntax Validation**:
   - Run `compile_applet` to verify clean build and type safety.
2. **Cold-Start Performance Verification**:
   - Verify that all Home tab calls, contacts, and SMS threads display immediately without initial stutter.
3. **Feature Preservation Check**:
   - Verify that opening Space, Browser, Music Player, Gallery, and Document Manager functions exactly as before, initializing seamlessly on demand.
