# Implementation Plan: App Performance Audit & Optimization Recommendations

This document presents a comprehensive, categorized performance audit report and actionable recommendations for improving startup/launch speed, runtime operation smoothness, and cleaning up code redundancies across the application.

---

## 1. Startup & Launch Speed Optimizations

### Findings & Analysis:
1. **Main Thread I/O during ViewModel Instantiation**:
   - `XenoViewModel` instantiates 11 repository objects (`contactsRepo`, `callLogRepo`, `smsRepo`, `browserRepo`, `galleryRepo`, `spaceRepo`, `gameSpaceRepo`, `passwordRepo`, `downloadRepo`, `documentRepo`, `musicRepo`) synchronously on the main thread during `init`.
   - Repository constructors perform synchronous `getSharedPreferences(...)` queries and register broadcast receivers during app cold-start before the first frame renders.
2. **Redundant Snapshot Seeding at Launch**:
   - In `XenoViewModel.init`, lines 236-238 synchronously query `getCachedContactsSnapshot()`, `getCachedCallLogsSnapshot()`, and `getCachedThreadsSnapshot()`.
   - Immediately afterward, `boostAppPerformanceAndFastLoadHelper()` is called on line 247, which re-queries the exact same snapshots on lines 377-379 via `viewModelScope.launch(Dispatchers.Default)`.
3. **Eager Preloading of Off-Screen Resources**:
   - `loadAllData(forceRefresh = true)` runs at app startup and queries gallery media items (`galleryRepo.getMediaItems()`) even when the user opens directly to the Calls or Messages tab.
   - Deferring non-active tab resources (Gallery, Documents, Browser tabs) until those specific screens or features are accessed reduces initial memory allocation and CPU contention during cold boot.
4. **Touch Dispatch Overhead in `MainActivity`**:
   - `MainActivity.dispatchTouchEvent` intercepts every touch gesture window-wide to compute swipe-down gesture bounds for `GlobalSwipeDownTracker`. Performing density conversions and math calculations on every touch event adds unnecessary frame latency on 120Hz displays.

### Proposed Recommendations:
- **Lazy Repository Instantiation**: Convert secondary repositories (Gallery, Documents, Music, Passwords, Browser) to lazy delegates or initialized on-demand when navigating to those tabs.
- **Unified 0ms Snapshot Preloader**: Combine snapshot seeding into a single IO-scheduled initialization pass that populates `_uiState` without duplicate SharedPreferences JSON parsing.
- **Deferred Feature Loading**: Load media, browser history, and documents lazily when the user explicitly switches to those tabs/drawers rather than eagerly on cold start.
- **Optimized Touch Tracking**: Restrict gesture calculation to top-edge drag events rather than calculating distance matrices on every touch move.

---

## 2. Operation Speed & Smoothness Optimizations

### Findings & Analysis:
1. **Monolithic `XenoUiState` Data Class**:
   - `XenoUiState` contains 30+ properties across calls, SMS, contacts, browser tabs, media items, document folders, and notifications.
   - Whenever any single field changes (e.g. dialpad digit input or duration update), a copy of the entire state object is emitted. While Compose skips unchanged composables, splitting high-frequency state (e.g., active call duration, typing text) prevents unnecessary top-level state recalculation.
2. **Unbounded Memory Cache Accumulation**:
   - `SmsRepository` maintains `inMemoryMessages` (`mutableListOf`) and `threadMessagesCache` (`ConcurrentHashMap`). During long sessions with heavy SMS activity, in-memory caches grow indefinitely without an LRU eviction strategy or memory pressure trimming (`onTrimMemory`).
3. **Repeated Telephony System Queries**:
   - `TelecomHelper.getAvailableSimCards(context)` queries `SubscriptionManager` and `TelecomManager` on multiple user interactions (call placement, chat SIM selector, carrier resolution).
   - Caching the available SIM list in memory and invalidating only on `ACTION_DEFAULT_SMS_SUBSCRIPTION_CHANGED` or `ACTION_SUBINFO_STATE_CHANGE` eliminates redundant system IPC calls.
4. **Compose Recomposition & Derived State**:
   - In `CallsScreen.kt` and `ChatScreen.kt`, derived lists and filtered log groupings should consistently leverage `@Stable` / `@Immutable` annotations and `derivedStateOf` to ensure smooth 60fps/120fps scrolling in LazyColumns.

### Proposed Recommendations:
- **LRU Cache Capping**: Implement a max-capacity bound (e.g., 200 items per thread) on `SmsRepository.threadMessagesCache` and `inMemoryMessages`.
- **System Service Caching**: Cache SIM info and active subscription handles with broadcast-driven invalidation.
- **Compose Stability Polish**: Ensure all domain model lists passed into LazyColumn items use stable keys and derived state.

---

## 3. Code Redundancies & Cleanup Areas

### Findings & Analysis:
1. **Duplicate Snapshot Seeding**:
   - Remove redundant `getCached...Snapshot()` calls in `XenoViewModel.init` and consolidate snapshot loading inside `boostAppPerformanceAndFastLoadHelper()`.
2. **Redundant Call History Post-Sync Timers**:
   - In `XenoViewModel.triggerPostCallHistorySync()`, three explicit delayed coroutines (`delay(350)` and `delay(750)`) re-query call logs. Since `CallLogContentObserver` already listens to system provider updates, debouncing the content observer handles async system writes cleanly without speculative timers.
3. **Duplicate Carrier Resolution Logic**:
   - `IncomingCallPopupHelper.resolveCarrierName` and `XenoNotificationHelper` have duplicate fallbacks for determining SIM carrier names. Consolidate into `TelecomHelper.resolveCarrierName`.

---

## Verification & Testing Plan

1. **Compilation Check**:
   - Run `compile_applet` after refactoring to ensure build stability and zero regression.
2. **Launch & Smoothness Benchmarking**:
   - Verify zero-latency initial frame rendering from snapshot seed.
   - Validate smooth tab switching and list scrolling in `CallsScreen` and `ChatScreen`.
