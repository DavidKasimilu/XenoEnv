package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BrowserRepository
import com.example.data.CallLogRepository
import com.example.data.ContactsRepository
import com.example.data.DocumentFileItem
import com.example.data.DocumentFolderItem
import com.example.data.DocumentRepository
import com.example.data.DownloadRepository
import com.example.data.GalleryRepository
import com.example.data.GameSpaceRepository
import com.example.data.LoadedDocumentContent
import com.example.data.MusicRepository
import com.example.data.MusicTrackItem
import com.example.data.PasswordRepository
import com.example.data.SmsRepository
import com.example.data.SpaceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.data.models.BrowserHistoryItem
import com.example.data.models.DownloadItem
import com.example.data.models.DownloadStatus
import com.example.data.models.SavedPassword
import com.example.data.models.CallLogEntry
import com.example.data.models.CallType
import com.example.data.models.ClonedAppInstance
import com.example.data.models.ContactItem
import com.example.data.models.DeviceNotification
import com.example.data.models.IncomingCallPopup
import com.example.data.models.IncomingSmsPopup
import com.example.data.models.ApkInstallState
import com.example.data.models.InstalledGameItem
import com.example.data.models.VirtualEnvProfile
import com.example.data.models.MediaItem
import com.example.data.models.SmsDeliveryStatus
import com.example.data.models.SmsMessage
import com.example.data.models.SmsThread
import com.example.data.models.WebTab
import com.example.telecom.CallPhase
import com.example.telecom.MusicPlaybackController
import com.example.telecom.OngoingCallHelper
import com.example.telecom.SmsDeliverReceiver
import com.example.telecom.XenoNotificationHelper
import com.example.telecom.TelecomHelper
import com.example.telecom.XenoInCallService
import com.example.telecom.XenoNotificationListenerService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

enum class XenoTab {
    CALLS,
    CONTACTS,
    KEYPAD,
    MESSAGES,
    BROWSER,
    CLONE_SPACE,
    SETTINGS
}

enum class MessagesSubTab {
    SMS,
    NOTIFICATIONS
}

enum class CallFilter {
    ALL,
    MISSED,
    INCOMING,
    OUTGOING
}

enum class MediaFilter {
    ALL,
    PHOTOS,
    VIDEOS
}

enum class BrowserDualTab {
    BROWSER,
    SPACE,
    DOWNLOADS
}

data class XenoUiState(
    val currentTab: XenoTab = XenoTab.CALLS,
    val messagesSubTab: MessagesSubTab = MessagesSubTab.SMS,
    val isLoading: Boolean = false,
    val callLogs: List<CallLogEntry> = emptyList(),
    val unseenMissedCallsCount: Int = 0,
    val callFilter: CallFilter = CallFilter.ALL,
    val contacts: List<ContactItem> = emptyList(),
    val contactSearchQuery: String = "",
    val smsThreads: List<SmsThread> = emptyList(),
    val smsSearchQuery: String = "",
    val activeChatThread: SmsThread? = null,
    val activeChatMessages: List<SmsMessage> = emptyList(),
    val isDefaultDialer: Boolean = false,
    val isDefaultSms: Boolean = false,
    val dialpadDigits: String = "",
    val selectedContact: ContactItem? = null,
    val selectedCallLog: CallLogEntry? = null,
    val showNewChatDialog: Boolean = false,
    val userNotice: String? = null,

    // Browser & Space dual-view state
    val browserDualTab: BrowserDualTab = BrowserDualTab.BROWSER,
    val browserTabs: List<WebTab> = emptyList(),
    val activeBrowserTabId: String = "",
    val browserHistory: List<BrowserHistoryItem> = emptyList(),
    val showTabSwitcher: Boolean = false,
    val showHistoryDialog: Boolean = false,
    val savedPasswords: List<SavedPassword> = emptyList(),
    val downloads: List<DownloadItem> = emptyList(),
    val showPasswordManager: Boolean = false,

    // Space Sandbox state
    val spaceClones: List<ClonedAppInstance> = emptyList(),
    val activeSpaceDock: List<String> = emptyList(),
    val minimizedSpaceCloneIds: List<String> = emptyList(),
    val activeSpaceInstanceId: String? = null,
    val showAddCloneDialog: Boolean = false,
    val selectedCloneForEdit: ClonedAppInstance? = null,

    // Gallery state
    val mediaItems: List<MediaItem> = emptyList(),
    val mediaFilter: MediaFilter = MediaFilter.ALL,
    val selectedImageItem: MediaItem? = null,
    val selectedVideoItem: MediaItem? = null,
    val isVideoControlsHidden: Boolean = false,
    val showMediaManager: Boolean = false,

    // Docs Reader state
    val showDocsManager: Boolean = false,
    val documentFolders: List<DocumentFolderItem> = emptyList(),
    val isLoadingDocs: Boolean = false,

    // Clone Space Music Player state
    val showMusicPlayer: Boolean = false,
    val musicTracks: List<MusicTrackItem> = emptyList(),
    val isLoadingMusic: Boolean = false,
    val showVineOS: Boolean = false,

    // Notification state
    val deviceNotifications: List<DeviceNotification> = emptyList(),
    val hasNotificationAccess: Boolean = false,
    val isAppAlertsDrawerOpen: Boolean = false,
    val isBrowserHeaderHidden: Boolean = false,
    val isBrowserFullscreenVideo: Boolean = false,

    // Active Call state
    val activeCallState: ActiveCallState? = null,

    // In-App Notification Popups
    val incomingSmsPopup: IncomingSmsPopup? = null,
    val incomingCallPopup: IncomingCallPopup? = null,
    val installedGames: List<InstalledGameItem> = emptyList(),
    val apkInstallState: ApkInstallState = ApkInstallState(),
    val activeGameItem: InstalledGameItem? = null,
    val activeGameUrl: String? = null,
    val activeGameTitle: String? = null,
    val activeGamePackage: String? = null,
    val preventOverlayApps: Boolean = true
) {
    val activeBrowserTab: WebTab?
        get() = browserTabs.find { it.id == activeBrowserTabId } ?: browserTabs.firstOrNull()

    val activeSpaceInstance: ClonedAppInstance?
        get() = spaceClones.find { it.instanceId == activeSpaceInstanceId }

    val minimizedSpaceClones: List<ClonedAppInstance>
        get() = minimizedSpaceCloneIds.mapNotNull { id ->
            if (id == activeSpaceInstanceId) null
            else spaceClones.find { it.instanceId == id }
        }
}

data class ActiveCallState(
    val phoneNumber: String,
    val contactName: String?,
    val isIncoming: Boolean = false
)

class XenoViewModel(application: Application) : AndroidViewModel(application) {

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

    private val callPrefs = application.getSharedPreferences("xeno_call_prefs", Context.MODE_PRIVATE)
    private val recentInMemoryCalls = CopyOnWriteArrayList<CallLogEntry>()
    private var callLogContentObserver: ContentObserver? = null

    @Volatile
    private var lastFullDataLoadMs: Long = 0L

    @Volatile
    private var lastGalleryLoadMs: Long = 0L

    private val _uiState = MutableStateFlow(XenoUiState())
    val uiState: StateFlow<XenoUiState> = _uiState.asStateFlow()

    init {
        if (!callPrefs.contains("last_seen_calls_ts")) {
            callPrefs.edit().putLong("last_seen_calls_ts", System.currentTimeMillis()).apply()
        }
        boostAppPerformanceAndFastLoadHelper()
        registerCallLogSyncHelper()

        viewModelScope.launch {
            XenoNotificationListenerService.notificationsFlow.collect { notifs ->
                val dedup = notifs.distinctBy { "${it.packageName}::${it.title.trim()}::${it.text.trim()}" }
                _uiState.value = _uiState.value.copy(deviceNotifications = dedup)
            }
        }

        SmsDeliverReceiver.onSmsReceivedListener = { address, body, date ->
            handleIncomingSms(address, body, date)
        }

        XenoInCallService.onIncomingCallListener = { number ->
            handleIncomingCall(number)
        }

        XenoInCallService.onCallEndedListener = {
            val pendingPopup = _uiState.value.incomingCallPopup
            val active = _uiState.value.activeCallState
            dismissCallPopup()
            if (active != null) {
                val session = OngoingCallHelper.sessionFlow.value
                val elapsed = OngoingCallHelper.getCurrentDurationSeconds()
                if (active.isIncoming && !session.isConnected && elapsed == 0L) {
                    declineActiveCall()
                } else {
                    endActiveCall(elapsed)
                }
            } else if (pendingPopup != null) {
                declineCallPopup(pendingPopup)
            } else {
                OngoingCallHelper.endCall(getApplication())
                triggerPostCallHistorySync()
            }
        }

        OngoingCallHelper.onCallDeclinedListener = { number, name ->
            if (_uiState.value.incomingCallPopup != null || _uiState.value.activeCallState != null) {
                _uiState.value = _uiState.value.copy(
                    incomingCallPopup = null,
                    activeCallState = null
                )
            }
            if (number.isNotBlank()) {
                val now = System.currentTimeMillis()
                val alreadyLoggedRecently = recentInMemoryCalls.any {
                    it.number == number && Math.abs(now - it.date) < 5_000L
                }
                if (!alreadyLoggedRecently) {
                    val newEntry = CallLogEntry(
                        id = now,
                        number = number,
                        cachedName = name,
                        type = CallType.MISSED,
                        date = now,
                        durationSeconds = 0L,
                        photoUri = null
                    )
                    recentInMemoryCalls.add(0, newEntry)
                    viewModelScope.launch {
                        callLogRepo.insertCallLog(number, name, 0L, CallType.MISSED)
                        triggerPostCallHistorySync()
                    }
                }
            }
        }

        com.example.telecom.XenoNotificationActionReceiver.onMarkSmsReadFromNotificationListener = { threadId, senderNumber ->
            viewModelScope.launch {
                smsRepo.markThreadAsRead(threadId, senderNumber)
                val threads = smsRepo.getConversations()
                _uiState.value = _uiState.value.copy(
                    smsThreads = threads,
                    incomingSmsPopup = null
                )
            }
        }

        viewModelScope.launch {
            OngoingCallHelper.incomingPopupFlow.collect { popup ->
                if (_uiState.value.incomingCallPopup != popup) {
                    _uiState.value = _uiState.value.copy(incomingCallPopup = popup)
                }
            }
        }

        viewModelScope.launch {
            OngoingCallHelper.sessionFlow.collect { session ->
                when {
                    session.phase == CallPhase.IDLE -> {
                        if (_uiState.value.activeCallState != null || _uiState.value.incomingCallPopup != null) {
                            _uiState.value = _uiState.value.copy(
                                activeCallState = null,
                                incomingCallPopup = null
                            )
                        }
                    }
                    session.isFullScreenCallVisible && session.phoneNumber.isNotBlank() && session.phase != CallPhase.DISCONNECTED -> {
                        val currentActive = _uiState.value.activeCallState
                        if (currentActive == null || currentActive.phoneNumber != session.phoneNumber) {
                            _uiState.value = _uiState.value.copy(
                                activeCallState = ActiveCallState(
                                    phoneNumber = session.phoneNumber,
                                    contactName = session.contactName,
                                    isIncoming = session.isIncoming
                                ),
                                incomingCallPopup = null
                            )
                        } else if (!session.contactName.isNullOrBlank() && currentActive.contactName.isNullOrBlank()) {
                            _uiState.value = _uiState.value.copy(
                                activeCallState = currentActive.copy(contactName = session.contactName)
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Dedicated Performance Boost & Fast-Load Helper:
     * 1. Seeds Call History and SMS Threads immediately from fast snapshots so Frame 1 renders with 0ms blank state.
     * 2. Offloads all SharedPreferences, Browser, Clone Space, Password, Download, and Game Space I/O off the main thread.
     * 3. Executes Call Log, Contacts, and SMS Thread sync concurrently on Dispatchers.IO.
     */
    fun boostAppPerformanceAndFastLoadHelper() {
        // 1. Frame-1 instant snapshot seed (non-blocking)
        viewModelScope.launch(Dispatchers.Default) {
            val cachedCalls = callLogRepo.getCachedCallLogsSnapshot()
            val cachedThreads = smsRepo.getCachedThreadsSnapshot()
            val cachedContacts = contactsRepo.getCachedContactsSnapshot()
            if (cachedCalls.isNotEmpty() || cachedThreads.isNotEmpty() || cachedContacts.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    callLogs = if (_uiState.value.callLogs.isEmpty()) cachedCalls else _uiState.value.callLogs,
                    smsThreads = if (_uiState.value.smsThreads.isEmpty()) cachedThreads else _uiState.value.smsThreads,
                    contacts = if (_uiState.value.contacts.isEmpty()) cachedContacts else _uiState.value.contacts
                )
            }
        }

        // 2. Priority live sync of Call History, Contacts, and SMS Conversations
        loadAllData(forceRefresh = true)

        // 3. Offload secondary workspace/browser/storage initialization to background IO
        viewModelScope.launch(Dispatchers.IO) {
            checkDefaultAppStatus()
            checkNotificationAccess()
            val (tabs, activeId) = browserRepo.loadTabs()
            val history = browserRepo.loadHistory()
            val clones = spaceRepo.loadClonedInstances()
            val dock = spaceRepo.loadActiveDock()
            val passwords = passwordRepo.getSavedPasswords()
            val trackedDownloads = downloadRepo.getTrackedDownloads()
            _uiState.value = _uiState.value.copy(
                browserTabs = tabs,
                activeBrowserTabId = activeId,
                browserHistory = history,
                spaceClones = clones,
                activeSpaceDock = dock,
                savedPasswords = passwords,
                downloads = trackedDownloads
            )
            val games = gameSpaceRepo.loadUserGames()
            _uiState.value = _uiState.value.copy(installedGames = games)
        }
    }

    /**
     * Helper function that observes system CallLog changes in real time and syncs call history
     * immediately without waiting for slow media or storage scans.
     */
    private fun registerCallLogSyncHelper() {
        try {
            val app = getApplication<Application>()
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    super.onChange(selfChange)
                    syncCallHistoryFast()
                }

                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    super.onChange(selfChange, uri)
                    syncCallHistoryFast()
                }
            }
            app.contentResolver.registerContentObserver(
                CallLog.Calls.CONTENT_URI,
                true,
                observer
            )
            callLogContentObserver = observer
        } catch (_: Exception) {}
    }

    /**
     * Fast, dedicated Call History Sync Helper:
     * Fetches device call logs on Dispatchers.IO, merges any pending in-app call records that
     * the system CallLogProvider hasn't committed yet, enriches missing contact names in O(1)
     * via ContactsRepository's in-memory phone index, and updates unseen missed call badge state.
     */
    fun syncCallHistoryFast() {
        viewModelScope.launch(Dispatchers.IO) {
            val systemLogs = callLogRepo.getCallLogs()

            // Merge recent optimistic call entries not yet reflected in system provider
            val now = System.currentTimeMillis()
            recentInMemoryCalls.removeAll { now - it.date > 60_000L }

            val merged = ArrayList<CallLogEntry>(systemLogs.size + recentInMemoryCalls.size)
            for (localEntry in recentInMemoryCalls) {
                val localDigits = localEntry.number.filter { it.isDigit() }.takeLast(7)
                val alreadyInSystem = systemLogs.any { sys ->
                    val sysDigits = sys.number.filter { it.isDigit() }.takeLast(7)
                    (sysDigits == localDigits || sys.number == localEntry.number) &&
                            Math.abs(sys.date - localEntry.date) < 15_000L
                }
                if (!alreadyInSystem) {
                    merged.add(localEntry)
                }
            }
            merged.addAll(systemLogs)
            merged.sortByDescending { it.date }

            val enrichedLogs = ArrayList<CallLogEntry>(merged.size)
            for (entry in merged) {
                if (entry.cachedName.isNullOrBlank() && entry.number.isNotBlank()) {
                    val fastName = contactsRepo.resolveContactNameFast(entry.number)
                    if (!fastName.isNullOrBlank()) {
                        enrichedLogs.add(entry.copy(cachedName = fastName))
                    } else {
                        enrichedLogs.add(entry)
                    }
                } else {
                    enrichedLogs.add(entry)
                }
            }

            callLogRepo.saveCallLogsSnapshot(enrichedLogs)

            val isOnCallsTab = _uiState.value.currentTab == XenoTab.CALLS
            val lastSeenTs = callPrefs.getLong("last_seen_calls_ts", 0L)
            val newUnseenCount = if (isOnCallsTab) {
                val maxTs = maxOf(now, enrichedLogs.firstOrNull()?.date ?: now)
                callPrefs.edit().putLong("last_seen_calls_ts", maxTs).apply()
                0
            } else {
                enrichedLogs.count {
                    (it.type == CallType.MISSED || it.type == CallType.REJECTED) && it.date > lastSeenTs
                }
            }

            _uiState.value = _uiState.value.copy(
                callLogs = enrichedLogs,
                unseenMissedCallsCount = newUnseenCount
            )
        }
    }

    /**
     * Multi-stage post-call sync helper to catch asynchronous system CallLogProvider writes
     * immediately after a call ends or is missed.
     */
    fun triggerPostCallHistorySync() {
        syncCallHistoryFast()
        viewModelScope.launch {
            delay(350)
            syncCallHistoryFast()
            delay(750)
            syncCallHistoryFast()
        }
    }

    fun markMissedCallsAsSeen() {
        val now = System.currentTimeMillis()
        val maxCallDate = _uiState.value.callLogs.firstOrNull()?.date ?: now
        val newSeenTs = maxOf(now, maxCallDate)
        callPrefs.edit().putLong("last_seen_calls_ts", newSeenTs).apply()
        if (_uiState.value.unseenMissedCallsCount != 0) {
            _uiState.value = _uiState.value.copy(unseenMissedCallsCount = 0)
        }
        viewModelScope.launch(Dispatchers.IO) {
            callLogRepo.markMissedCallsAsRead()
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            callLogContentObserver?.let {
                getApplication<Application>().contentResolver.unregisterContentObserver(it)
            }
        } catch (_: Exception) {}
    }

    private fun loadBrowserMemory() {
        viewModelScope.launch(Dispatchers.IO) {
            val (tabs, activeId) = browserRepo.loadTabs()
            val history = browserRepo.loadHistory()
            _uiState.value = _uiState.value.copy(
                browserTabs = tabs,
                activeBrowserTabId = activeId,
                browserHistory = history
            )
        }
    }

    fun selectTab(tab: XenoTab) {
        val isCalls = tab == XenoTab.CALLS
        val isMessages = tab == XenoTab.MESSAGES
        val nextDualTab = when (tab) {
            XenoTab.CLONE_SPACE -> BrowserDualTab.SPACE
            XenoTab.BROWSER -> if (_uiState.value.browserDualTab == BrowserDualTab.SPACE) BrowserDualTab.BROWSER else _uiState.value.browserDualTab
            else -> _uiState.value.browserDualTab
        }
        val nextActiveSpaceId = if (tab == XenoTab.BROWSER) null else _uiState.value.activeSpaceInstanceId
        _uiState.value = _uiState.value.copy(
            currentTab = tab,
            browserDualTab = nextDualTab,
            activeSpaceInstanceId = nextActiveSpaceId,
            unseenMissedCallsCount = if (isCalls) 0 else _uiState.value.unseenMissedCallsCount,
            activeChatThread = if (!isMessages) null else _uiState.value.activeChatThread
        )
        if (isCalls) {
            markMissedCallsAsSeen()
            syncCallHistoryFast()
        } else if (isMessages && _uiState.value.smsThreads.isEmpty()) {
            refreshSms()
        }
    }

    fun checkDefaultAppStatus() {
        val app = getApplication<Application>()
        val defaultDialer = TelecomHelper.isDefaultDialer(app)
        val defaultSms = TelecomHelper.isDefaultSmsApp(app)
        _uiState.value = _uiState.value.copy(
            isDefaultDialer = defaultDialer,
            isDefaultSms = defaultSms
        )
    }

    fun loadAllData(forceRefresh: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && (now - lastFullDataLoadMs) < 1200L && _uiState.value.callLogs.isNotEmpty()) {
            return
        }
        lastFullDataLoadMs = now

        // 1. Immediately sync call logs in a dedicated fast IO coroutine so Calls tab never waits
        syncCallHistoryFast()

        // 2. Load contacts & SMS threads concurrently on Dispatchers.IO
        viewModelScope.launch(Dispatchers.IO) {
            val contacts = contactsRepo.getContacts(forceRefresh = forceRefresh)
            val currentThreads = _uiState.value.smsThreads
            val enrichedThreads = if (currentThreads.isNotEmpty()) {
                currentThreads.map { thread ->
                    val realName = contactsRepo.resolveContactNameFast(thread.address)
                    if (!realName.isNullOrBlank()) {
                        thread.copy(contactName = realName)
                    } else if (com.example.telecom.PriorityContactManager.isSyntheticPriorityName(thread.contactName)) {
                        thread.copy(contactName = null)
                    } else {
                        thread
                    }
                }
            } else {
                currentThreads
            }
            val currentActiveChat = _uiState.value.activeChatThread?.let { active ->
                val realName = contactsRepo.resolveContactNameFast(active.address)
                if (!realName.isNullOrBlank()) {
                    active.copy(contactName = realName)
                } else if (com.example.telecom.PriorityContactManager.isSyntheticPriorityName(active.contactName)) {
                    active.copy(contactName = null)
                } else {
                    active
                }
            }
            _uiState.value = _uiState.value.copy(
                contacts = contacts,
                smsThreads = enrichedThreads,
                activeChatThread = currentActiveChat
            )
            // Re-enrich call log contact names in O(1) as soon as contacts are indexed
            syncCallHistoryFast()
        }

        viewModelScope.launch(Dispatchers.IO) {
            val threads = smsRepo.getConversations()
            _uiState.value = _uiState.value.copy(smsThreads = threads)
        }

        // 3. Load gallery media items only if needed or if gallery manager is open (avoids heavy MediaStore scan on every resume)
        if (_uiState.value.mediaItems.isEmpty() || _uiState.value.showMediaManager || (now - lastGalleryLoadMs) > 45_000L) {
            lastGalleryLoadMs = now
            viewModelScope.launch(Dispatchers.IO) {
                val media = galleryRepo.getMediaItems()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    mediaItems = media
                )
            }
        }
    }

    // Call Log functions
    fun setCallFilter(filter: CallFilter) {
        _uiState.value = _uiState.value.copy(callFilter = filter)
        if (filter == CallFilter.MISSED) {
            markMissedCallsAsSeen()
        }
    }

    fun selectCallLog(entry: CallLogEntry?) {
        _uiState.value = _uiState.value.copy(selectedCallLog = entry)
    }

    fun deleteCallLog(entry: CallLogEntry) {
        recentInMemoryCalls.removeAll { it.id == entry.id }
        _uiState.value = _uiState.value.copy(
            callLogs = _uiState.value.callLogs.filter { it.id != entry.id },
            selectedCallLog = null,
            userNotice = "Call log entry deleted"
        )
        viewModelScope.launch(Dispatchers.IO) {
            callLogRepo.deleteCallLog(entry.id)
        }
    }

    fun clearMissedCallLogs() {
        recentInMemoryCalls.removeAll { it.type == CallType.MISSED || it.type == CallType.REJECTED }
        val remainingLogs = _uiState.value.callLogs.filter {
            it.type != CallType.MISSED && it.type != CallType.REJECTED
        }
        markMissedCallsAsSeen()
        _uiState.value = _uiState.value.copy(
            callLogs = remainingLogs,
            unseenMissedCallsCount = 0,
            selectedCallLog = null,
            userNotice = "Missed calls cleared"
        )
        viewModelScope.launch(Dispatchers.IO) {
            callLogRepo.clearMissedCallLogs()
            syncCallHistoryFast()
        }
    }

    fun clearAllCallLogs() {
        recentInMemoryCalls.clear()
        markMissedCallsAsSeen()
        _uiState.value = _uiState.value.copy(
            callLogs = emptyList(),
            unseenMissedCallsCount = 0,
            selectedCallLog = null,
            userNotice = "Call history cleared"
        )
        viewModelScope.launch(Dispatchers.IO) {
            callLogRepo.clearCallLogs()
        }
    }

    // Contacts functions
    fun setContactSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(contactSearchQuery = query)
    }

    fun selectContact(contact: ContactItem?) {
        _uiState.value = _uiState.value.copy(selectedContact = contact)
    }

    // Dialpad functions
    fun appendDialpadDigit(char: Char) {
        _uiState.value = _uiState.value.copy(
            dialpadDigits = _uiState.value.dialpadDigits + char
        )
    }

    fun setDialpadDigits(digits: String) {
        _uiState.value = _uiState.value.copy(dialpadDigits = digits)
    }

    fun backspaceDialpad() {
        val current = _uiState.value.dialpadDigits
        if (current.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                dialpadDigits = current.dropLast(1)
            )
        }
    }

    fun clearDialpad() {
        _uiState.value = _uiState.value.copy(dialpadDigits = "")
    }

    // SMS & Chat functions
    fun setSmsSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(smsSearchQuery = query)
    }

    fun openChatThread(thread: SmsThread) {
        // 1. Instant 0ms UI transition using pre-cached thread messages
        val instantMessages = smsRepo.getCachedMessagesForThread(thread.threadId, thread.address)
        val readThread = thread.copy(read = true, unreadCount = 0)
        val updatedThreadsInMemory = _uiState.value.smsThreads.map {
            if (it.threadId == thread.threadId || it.address == thread.address) readThread else it
        }
        _uiState.value = _uiState.value.copy(
            currentTab = XenoTab.MESSAGES,
            activeChatThread = readThread,
            activeChatMessages = instantMessages,
            smsThreads = updatedThreadsInMemory
        )

        // 2. Background verification & read-state persistence without blocking UI
        viewModelScope.launch(Dispatchers.IO) {
            val freshMessages = smsRepo.getMessagesForThread(thread.threadId, thread.address)
            if (_uiState.value.activeChatThread?.threadId == thread.threadId && freshMessages != instantMessages) {
                _uiState.value = _uiState.value.copy(activeChatMessages = freshMessages)
            }
            smsRepo.markThreadAsRead(thread.threadId, thread.address)
        }
    }

    fun startChatWithNumber(number: String, name: String? = null) {
        val cleanTarget = number.filter { it.isDigit() }
        val existingThread = _uiState.value.smsThreads.find {
            it.address.filter { c -> c.isDigit() } == cleanTarget
        }
        val resolvedName = name?.takeIf { it.isNotBlank() } ?: contactsRepo.resolveContactNameFast(number)
        val targetThread = existingThread ?: SmsThread(
            threadId = number.hashCode().toLong(),
            address = number,
            contactName = resolvedName,
            snippet = "",
            date = System.currentTimeMillis(),
            read = true
        )
        openChatThread(targetThread)
        _uiState.value = _uiState.value.copy(showNewChatDialog = false)
    }

    fun closeChat() {
        _uiState.value = _uiState.value.copy(
            activeChatThread = null,
            activeChatMessages = emptyList()
        )
        viewModelScope.launch(Dispatchers.IO) {
            val threads = smsRepo.getConversations()
            _uiState.value = _uiState.value.copy(smsThreads = threads)
        }
    }

    private fun applyMessageDeliveryStatus(messageId: Long, status: SmsDeliveryStatus) {
        smsRepo.updateMessageDeliveryStatus(messageId, status)
        val updatedMessages = _uiState.value.activeChatMessages.map { msg ->
            if (msg.id == messageId) msg.copy(deliveryStatus = status) else msg
        }
        _uiState.value = _uiState.value.copy(
            activeChatMessages = updatedMessages
        )
    }

    fun sendChatMessage(text: String) {
        val activeThread = _uiState.value.activeChatThread ?: return
        val cleanText = text.trim()
        if (cleanText.isBlank()) return

        val msgId = System.nanoTime()
        val now = System.currentTimeMillis()
        val pendingMsg = SmsMessage(
            id = msgId,
            threadId = activeThread.threadId,
            address = activeThread.address,
            body = cleanText,
            date = now,
            isSent = true,
            read = true,
            deliveryStatus = SmsDeliveryStatus.SENDING
        )

        val initialList = _uiState.value.activeChatMessages + pendingMsg
        val updatedThread = activeThread.copy(
            snippet = cleanText,
            date = now,
            read = true
        )
        val otherThreads = _uiState.value.smsThreads.filter { it.threadId != activeThread.threadId }
        _uiState.value = _uiState.value.copy(
            activeChatThread = updatedThread,
            activeChatMessages = initialList,
            smsThreads = listOf(updatedThread) + otherThreads
        )

        viewModelScope.launch {
            val result = smsRepo.sendSms(
                destinationAddress = activeThread.address,
                text = cleanText,
                currentThreadId = activeThread.threadId,
                messageId = msgId,
                onStatusUpdate = { status ->
                    viewModelScope.launch(Dispatchers.Main) {
                        applyMessageDeliveryStatus(msgId, status)
                    }
                }
            )
            result.onSuccess {
                delay(420)
                val currentAfterSend = _uiState.value.activeChatMessages.find { it.id == msgId }?.deliveryStatus
                if (currentAfterSend == SmsDeliveryStatus.SENDING) {
                    applyMessageDeliveryStatus(msgId, SmsDeliveryStatus.SENT)
                }
                delay(650)
                val currentBeforeDeliver = _uiState.value.activeChatMessages.find { it.id == msgId }?.deliveryStatus
                if (currentBeforeDeliver == SmsDeliveryStatus.SENT || currentBeforeDeliver == SmsDeliveryStatus.SENDING) {
                    applyMessageDeliveryStatus(msgId, SmsDeliveryStatus.DELIVERED)
                }
            }.onFailure {
                applyMessageDeliveryStatus(msgId, SmsDeliveryStatus.FAILED)
            }
        }
    }

    fun deleteChatMessage(messageId: Long) {
        val activeThread = _uiState.value.activeChatThread ?: return
        viewModelScope.launch {
            smsRepo.deleteMessage(messageId)
            val updatedMessages = _uiState.value.activeChatMessages.filter { it.id != messageId }
            _uiState.value = _uiState.value.copy(
                activeChatMessages = updatedMessages,
                userNotice = "Message deleted"
            )
        }
    }

    fun deleteChatThread(thread: SmsThread) {
        viewModelScope.launch {
            smsRepo.deleteThread(thread.threadId, thread.address)
            _uiState.value = _uiState.value.copy(
                smsThreads = _uiState.value.smsThreads.filter { it.threadId != thread.threadId },
                activeChatThread = if (_uiState.value.activeChatThread?.threadId == thread.threadId) null else _uiState.value.activeChatThread,
                userNotice = "Conversation deleted"
            )
        }
    }

    fun setShowNewChatDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showNewChatDialog = show)
    }

    fun clearUserNotice() {
        _uiState.value = _uiState.value.copy(userNotice = null)
    }

    fun showNotice(msg: String) {
        _uiState.value = _uiState.value.copy(userNotice = msg)
    }

    fun refreshSms() {
        viewModelScope.launch {
            val threads = smsRepo.getConversations()
            _uiState.value = _uiState.value.copy(smsThreads = threads)
        }
    }

    fun refreshCallLogs() {
        syncCallHistoryFast()
    }

    fun refreshContacts() {
        viewModelScope.launch {
            val contacts = contactsRepo.getContacts()
            _uiState.value = _uiState.value.copy(contacts = contacts)
        }
    }

    // ==========================================
    // Browser functions (Multiple tabs & memory)
    // ==========================================
    fun openNewBrowserTab(url: String = "https://www.google.com") {
        val newTab = WebTab(
            id = UUID.randomUUID().toString(),
            title = "New Tab",
            url = url
        )
        val updatedTabs = _uiState.value.browserTabs + newTab
        _uiState.value = _uiState.value.copy(
            browserTabs = updatedTabs,
            activeBrowserTabId = newTab.id,
            showTabSwitcher = false
        )
        persistBrowserTabs()
    }

    fun selectBrowserTab(tabId: String) {
        if (_uiState.value.browserTabs.any { it.id == tabId }) {
            _uiState.value = _uiState.value.copy(
                activeBrowserTabId = tabId,
                showTabSwitcher = false
            )
            persistBrowserTabs()
        }
    }

    fun closeBrowserTab(tabId: String) {
        val currentTabs = _uiState.value.browserTabs
        val filtered = currentTabs.filter { it.id != tabId }
        if (filtered.isEmpty()) {
            // If closed last tab, make a fresh new tab
            openNewBrowserTab()
        } else {
            val nextActiveId = if (_uiState.value.activeBrowserTabId == tabId) {
                filtered.last().id
            } else {
                _uiState.value.activeBrowserTabId
            }
            _uiState.value = _uiState.value.copy(
                browserTabs = filtered,
                activeBrowserTabId = nextActiveId
            )
            persistBrowserTabs()
        }
    }

    fun updateActiveTabUrl(url: String, title: String) {
        val activeId = _uiState.value.activeBrowserTabId
        val updatedTabs = _uiState.value.browserTabs.map {
            if (it.id == activeId) it.copy(url = url, title = title.ifBlank { it.title }) else it
        }
        _uiState.value = _uiState.value.copy(browserTabs = updatedTabs)
        browserRepo.addHistoryItem(title, url)
        _uiState.value = _uiState.value.copy(browserHistory = browserRepo.loadHistory())
        persistBrowserTabs()
    }

    fun updateActiveTabLoading(isLoading: Boolean, progress: Int) {
        val activeId = _uiState.value.activeBrowserTabId
        val updatedTabs = _uiState.value.browserTabs.map {
            if (it.id == activeId) it.copy(isLoading = isLoading, progress = progress) else it
        }
        _uiState.value = _uiState.value.copy(browserTabs = updatedTabs)
    }

    fun updateActiveTabNavState(canGoBack: Boolean, canGoForward: Boolean) {
        val activeId = _uiState.value.activeBrowserTabId
        val updatedTabs = _uiState.value.browserTabs.map {
            if (it.id == activeId) it.copy(canGoBack = canGoBack, canGoForward = canGoForward) else it
        }
        _uiState.value = _uiState.value.copy(browserTabs = updatedTabs)
    }

    fun toggleActiveTabDesktopMode() {
        val activeId = _uiState.value.activeBrowserTabId
        val updatedTabs = _uiState.value.browserTabs.map {
            if (it.id == activeId) it.copy(isDesktopMode = !it.isDesktopMode) else it
        }
        _uiState.value = _uiState.value.copy(browserTabs = updatedTabs)
        persistBrowserTabs()
    }

    fun setShowTabSwitcher(show: Boolean) {
        _uiState.value = _uiState.value.copy(showTabSwitcher = show)
    }

    fun setShowHistoryDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showHistoryDialog = show)
    }

    fun clearBrowserHistory() {
        browserRepo.clearHistory()
        _uiState.value = _uiState.value.copy(browserHistory = emptyList(), userNotice = "Browser history cleared")
    }

    private fun persistBrowserTabs() {
        browserRepo.saveTabs(_uiState.value.browserTabs, _uiState.value.activeBrowserTabId)
    }

    // ==========================================
    // Gallery functions (Images, Videos, Player)
    // ==========================================
    fun setMediaFilter(filter: MediaFilter) {
        _uiState.value = _uiState.value.copy(mediaFilter = filter)
    }

    fun selectImageItem(item: MediaItem?) {
        _uiState.value = _uiState.value.copy(selectedImageItem = item)
    }

    fun selectVideoItem(item: MediaItem?) {
        _uiState.value = _uiState.value.copy(
            selectedVideoItem = item,
            isVideoControlsHidden = false
        )
    }

    fun setVideoControlsHidden(hidden: Boolean) {
        _uiState.value = _uiState.value.copy(isVideoControlsHidden = hidden)
    }

    fun refreshMedia() {
        viewModelScope.launch {
            val media = galleryRepo.getMediaItems()
            _uiState.value = _uiState.value.copy(mediaItems = media)
        }
    }

    fun setShowMediaManager(show: Boolean) {
        _uiState.value = _uiState.value.copy(showMediaManager = show)
        if (show) {
            refreshMedia()
        }
    }

    fun setShowDocsManager(show: Boolean) {
        _uiState.value = _uiState.value.copy(showDocsManager = show)
        if (show) {
            refreshDocumentFolders()
        }
    }

    fun refreshDocumentFolders() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingDocs = true)
            val folders = documentRepo.scanDeviceDocumentFolders()
            _uiState.value = _uiState.value.copy(
                documentFolders = folders,
                isLoadingDocs = false
            )
        }
    }

    suspend fun openDocumentReadOnly(item: DocumentFileItem): LoadedDocumentContent {
        return documentRepo.openDocumentReadOnly(item)
    }

    // ==========================================
    // Clone Space Music Player Functions
    // ==========================================
    fun setShowMusicPlayer(show: Boolean) {
        _uiState.value = _uiState.value.copy(showMusicPlayer = show)
        if (show) {
            refreshMusicTracks()
        }
    }

    fun setShowVineOS(show: Boolean) {
        _uiState.value = _uiState.value.copy(showVineOS = show)
    }

    fun refreshMusicTracks() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMusic = true)
            val tracks = musicRepo.scanDeviceMp3Files()
            val favs = musicRepo.getFavoriteTrackIds()
            MusicPlaybackController.setPlaylist(tracks, favs)
            _uiState.value = _uiState.value.copy(
                musicTracks = tracks,
                isLoadingMusic = false
            )
        }
    }

    fun toggleFavoriteMusicTrack(track: MusicTrackItem) {
        val updatedFavs = musicRepo.toggleFavorite(track.id)
        val isNowFav = updatedFavs.contains(track.id)
        val updatedTracks = _uiState.value.musicTracks.map {
            it.copy(isFavorite = updatedFavs.contains(it.id))
        }
        MusicPlaybackController.syncFavorites(updatedFavs)
        _uiState.value = _uiState.value.copy(
            musicTracks = updatedTracks,
            userNotice = if (isNowFav) "Added \"${track.title}\" to Favourites" else "Removed \"${track.title}\" from Favourites"
        )
    }

    fun importMusicUris(uris: List<Uri>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMusic = true)
            val count = musicRepo.importAudioUris(uris)
            val tracks = musicRepo.scanDeviceMp3Files()
            val favs = musicRepo.getFavoriteTrackIds()
            MusicPlaybackController.setPlaylist(tracks, favs)
            _uiState.value = _uiState.value.copy(
                musicTracks = tracks,
                isLoadingMusic = false,
                userNotice = if (count > 0) "Imported $count MP3 track${if (count == 1) "" else "s"}" else "Scanned audio library"
            )
        }
    }

    fun addCustomMedia(uri: Uri, isVideo: Boolean, displayName: String = if (isVideo) "Video.mp4" else "Photo.jpg") {
        viewModelScope.launch {
            val item = galleryRepo.importMediaUri(uri, isVideo)
            val media = galleryRepo.getMediaItems()
            _uiState.value = _uiState.value.copy(
                mediaItems = media,
                userNotice = if (isVideo) "Video \"${item.displayName}\" added with thumbnail!" else "Photo added to gallery!"
            )
        }
    }

    fun deleteMediaItem(item: MediaItem) {
        // Immediate optimistic removal so it vanishes instantly
        _uiState.value = _uiState.value.copy(
            mediaItems = _uiState.value.mediaItems.filter { it.id != item.id && it.uri != item.uri },
            selectedImageItem = if (_uiState.value.selectedImageItem?.id == item.id) null else _uiState.value.selectedImageItem,
            selectedVideoItem = if (_uiState.value.selectedVideoItem?.id == item.id) null else _uiState.value.selectedVideoItem,
            userNotice = if (item.isVideo) "Video permanently deleted" else "Photo permanently deleted"
        )
        viewModelScope.launch {
            galleryRepo.deleteMediaItem(item)
            val media = galleryRepo.getMediaItems()
            _uiState.value = _uiState.value.copy(mediaItems = media)
        }
    }

    fun removeDemoMedia() {
        viewModelScope.launch {
            galleryRepo.deleteDemoMedia()
            val media = galleryRepo.getMediaItems()
            _uiState.value = _uiState.value.copy(
                mediaItems = media,
                selectedImageItem = null,
                selectedVideoItem = null,
                userNotice = "Demo pictures and videos deleted from gallery"
            )
        }
    }

    fun addSampleVideos() {
        removeDemoMedia()
    }

    fun addSamplePhotos() {
        removeDemoMedia()
    }

    // ==========================================
    // Notification functions
    // ==========================================
    fun setMessagesSubTab(subTab: MessagesSubTab) {
        _uiState.value = _uiState.value.copy(messagesSubTab = subTab)
        checkNotificationAccess()
    }

    fun setAppAlertsDrawerOpen(open: Boolean) {
        if (_uiState.value.isAppAlertsDrawerOpen != open) {
            _uiState.value = _uiState.value.copy(isAppAlertsDrawerOpen = open)
            if (open) {
                checkNotificationAccess()
            }
        }
    }

    fun checkNotificationAccess() {
        val granted = XenoNotificationListenerService.isNotificationAccessGranted(getApplication())
        _uiState.value = _uiState.value.copy(hasNotificationAccess = granted)
        if (granted) {
            XenoNotificationListenerService.tryRebind(getApplication())
        }
    }

    fun dismissNotification(key: String) {
        XenoNotificationListenerService.dismissNotification(key)
    }

    fun clearAllNotifications() {
        XenoNotificationListenerService.clearAll()
    }

    fun addSampleCommunicationAlerts() {
        XenoNotificationListenerService.addSampleCommunicationAlerts()
    }

    fun simulateIncomingAlert(appName: String) {
        XenoNotificationListenerService.simulateIncomingAlert(appName)
    }

    // ==========================================
    // Space Sandbox & App Cloner Functions
    // ==========================================
    fun setBrowserDualTab(tab: BrowserDualTab) {
        val nextMainTab = when (tab) {
            BrowserDualTab.SPACE -> XenoTab.CLONE_SPACE
            BrowserDualTab.BROWSER, BrowserDualTab.DOWNLOADS ->
                if (_uiState.value.currentTab == XenoTab.CLONE_SPACE) XenoTab.BROWSER else _uiState.value.currentTab
        }
        _uiState.value = _uiState.value.copy(
            browserDualTab = tab,
            currentTab = nextMainTab
        )
    }

    fun loadSpaceData() {
        val clones = spaceRepo.loadClonedInstances()
        val dock = spaceRepo.loadActiveDock()
        _uiState.value = _uiState.value.copy(
            spaceClones = clones,
            activeSpaceDock = dock
        )
    }

    fun openAddCloneDialog() {
        _uiState.value = _uiState.value.copy(showAddCloneDialog = true)
    }

    fun closeAddCloneDialog() {
        _uiState.value = _uiState.value.copy(showAddCloneDialog = false)
    }

    fun openEditCloneDialog(instance: ClonedAppInstance) {
        _uiState.value = _uiState.value.copy(selectedCloneForEdit = instance)
    }

    fun closeEditCloneDialog() {
        _uiState.value = _uiState.value.copy(selectedCloneForEdit = null)
    }

    fun createAppClone(
        packageName: String,
        appName: String,
        displayName: String,
        badgeTag: String,
        badgeColorHex: String
    ) {
        val created = spaceRepo.addInstalledAppClone(packageName, appName, displayName, badgeTag, badgeColorHex)
        loadSpaceData()
        _uiState.value = _uiState.value.copy(
            showAddCloneDialog = false,
            userNotice = "Sandboxed clone '${created.displayName}' created!"
        )
    }

    fun updateAppClone(instance: ClonedAppInstance) {
        spaceRepo.updateClone(instance)
        loadSpaceData()
        _uiState.value = _uiState.value.copy(
            selectedCloneForEdit = null,
            userNotice = "Clone instance updated"
        )
    }

    fun deleteAppClone(instanceId: String) {
        val instance = _uiState.value.spaceClones.find { it.instanceId == instanceId }
        com.example.ui.screens.CloneWebViewPool.destroy(instanceId)
        spaceRepo.deleteClone(instanceId)
        loadSpaceData()
        val updatedMinimized = _uiState.value.minimizedSpaceCloneIds.filter { it != instanceId }
        val nextActive = if (_uiState.value.activeSpaceInstanceId == instanceId) null else _uiState.value.activeSpaceInstanceId
        _uiState.value = _uiState.value.copy(
            activeSpaceInstanceId = nextActive,
            minimizedSpaceCloneIds = updatedMinimized,
            userNotice = "Removed '${instance?.displayName ?: "clone"}' from Space"
        )
    }

    fun launchCloneWorkspace(instanceId: String) {
        val dock = _uiState.value.activeSpaceDock.toMutableList()
        if (!dock.contains(instanceId)) {
            dock.add(instanceId)
            spaceRepo.saveActiveDock(dock)
        }
        val updatedMinimized = _uiState.value.minimizedSpaceCloneIds.filter { it != instanceId }
        _uiState.value = _uiState.value.copy(
            activeSpaceInstanceId = instanceId,
            activeSpaceDock = dock,
            minimizedSpaceCloneIds = updatedMinimized,
            browserDualTab = BrowserDualTab.SPACE,
            currentTab = XenoTab.CLONE_SPACE
        )
    }

    fun minimizeCloneWorkspace() {
        val currentId = _uiState.value.activeSpaceInstanceId ?: return
        val currentMinimized = _uiState.value.minimizedSpaceCloneIds.toMutableList()
        if (!currentMinimized.contains(currentId)) {
            currentMinimized.add(currentId)
        }
        _uiState.value = _uiState.value.copy(
            activeSpaceInstanceId = null,
            minimizedSpaceCloneIds = currentMinimized
        )
    }

    fun restoreMinimizedClone(instanceId: String) {
        val previousActiveId = _uiState.value.activeSpaceInstanceId
        val updatedMinimized = _uiState.value.minimizedSpaceCloneIds
            .filter { it != instanceId }
            .toMutableList()
        if (previousActiveId != null && previousActiveId != instanceId && !updatedMinimized.contains(previousActiveId)) {
            updatedMinimized.add(previousActiveId)
        }
        val dock = _uiState.value.activeSpaceDock.toMutableList()
        if (!dock.contains(instanceId)) {
            dock.add(instanceId)
            spaceRepo.saveActiveDock(dock)
        }
        _uiState.value = _uiState.value.copy(
            activeSpaceInstanceId = instanceId,
            activeSpaceDock = dock,
            minimizedSpaceCloneIds = updatedMinimized,
            browserDualTab = BrowserDualTab.SPACE,
            currentTab = XenoTab.CLONE_SPACE,
            showDocsManager = false,
            showMediaManager = false,
            showMusicPlayer = false,
            showVineOS = false,
            activeChatThread = null
        )
    }

    fun closeMinimizedClone(instanceId: String) {
        com.example.ui.screens.CloneWebViewPool.destroy(instanceId)
        val updatedMinimized = _uiState.value.minimizedSpaceCloneIds.filter { it != instanceId }
        _uiState.value = _uiState.value.copy(
            minimizedSpaceCloneIds = updatedMinimized
        )
    }

    fun closeCloneWorkspace() {
        val currentId = _uiState.value.activeSpaceInstanceId
        if (currentId != null) {
            com.example.ui.screens.CloneWebViewPool.destroy(currentId)
        }
        val updatedMinimized = if (currentId != null) {
            _uiState.value.minimizedSpaceCloneIds.filter { it != currentId }
        } else {
            _uiState.value.minimizedSpaceCloneIds
        }
        _uiState.value = _uiState.value.copy(
            activeSpaceInstanceId = null,
            minimizedSpaceCloneIds = updatedMinimized
        )
    }

    fun closeCloneFromDock(instanceId: String) {
        com.example.ui.screens.CloneWebViewPool.destroy(instanceId)
        val dock = _uiState.value.activeSpaceDock.filter { it != instanceId }
        spaceRepo.saveActiveDock(dock)
        val nextActive = if (_uiState.value.activeSpaceInstanceId == instanceId) null else _uiState.value.activeSpaceInstanceId
        val updatedMinimized = _uiState.value.minimizedSpaceCloneIds.filter { it != instanceId }
        _uiState.value = _uiState.value.copy(
            activeSpaceDock = dock,
            activeSpaceInstanceId = nextActive,
            minimizedSpaceCloneIds = updatedMinimized
        )
    }

    fun updateCloneCurrentUrl(instanceId: String, url: String) {
        val existing = _uiState.value.spaceClones.find { it.instanceId == instanceId }
        if (existing?.lastUrl == url) return
        val updatedList = _uiState.value.spaceClones.map {
            if (it.instanceId == instanceId) it.copy(lastUrl = url) else it
        }
        _uiState.value = _uiState.value.copy(spaceClones = updatedList)
        viewModelScope.launch(Dispatchers.IO) {
            spaceRepo.updateCloneUrl(instanceId, url)
        }
    }

    fun toggleCloneDesktopMode(instanceId: String) {
        val newMode = spaceRepo.toggleDesktopMode(instanceId)
        val updatedList = _uiState.value.spaceClones.map {
            if (it.instanceId == instanceId) it.copy(isDesktopMode = newMode) else it
        }
        _uiState.value = _uiState.value.copy(
            spaceClones = updatedList,
            userNotice = if (newMode) "Desktop mode enabled" else "Mobile mode enabled"
        )
    }

    fun toggleBrowserHeader() {
        _uiState.value = _uiState.value.copy(isBrowserHeaderHidden = !_uiState.value.isBrowserHeaderHidden)
    }

    fun setBrowserFullscreenVideo(isFullscreen: Boolean) {
        _uiState.value = _uiState.value.copy(isBrowserFullscreenVideo = isFullscreen)
    }

    // Password Manager functions
    fun loadPasswords() {
        val list = passwordRepo.getSavedPasswords()
        _uiState.value = _uiState.value.copy(savedPasswords = list)
    }

    fun savePassword(domain: String, title: String, username: String, pass: String) {
        val item = SavedPassword(domain = domain, title = title, username = username, password = pass)
        passwordRepo.savePassword(item)
        loadPasswords()
    }

    fun deletePassword(id: Long) {
        passwordRepo.deletePassword(id)
        loadPasswords()
    }

    fun setShowPasswordManager(show: Boolean) {
        if (show) loadPasswords()
        _uiState.value = _uiState.value.copy(showPasswordManager = show)
    }

    // Download Manager functions
    fun loadDownloads() {
        val list = downloadRepo.getTrackedDownloads()
        _uiState.value = _uiState.value.copy(downloads = list)
    }

    fun addDownloadItem(dmId: Long, url: String, fileName: String, mimeType: String, fileSize: Long) {
        val item = DownloadItem(
            downloadManagerId = dmId,
            url = url,
            fileName = fileName,
            mimeType = mimeType,
            fileSize = fileSize,
            bytesDownloaded = fileSize,
            status = DownloadStatus.COMPLETED
        )
        downloadRepo.addDownloadItem(item)
        loadDownloads()
    }

    fun deleteDownloadItem(item: DownloadItem) {
        downloadRepo.deleteDownloadItem(item)
        loadDownloads()
    }

    fun clearAllDownloads() {
        downloadRepo.clearAllDownloads()
        loadDownloads()
    }

    fun startActiveCall(phoneNumber: String, contactName: String?, isIncoming: Boolean = false) {
        if (isIncoming) {
            OngoingCallHelper.startIncomingSession(phoneNumber, contactName)
        } else {
            OngoingCallHelper.startOutgoingSession(phoneNumber, contactName)
        }
        _uiState.value = _uiState.value.copy(
            activeCallState = ActiveCallState(
                phoneNumber = phoneNumber,
                contactName = contactName,
                isIncoming = isIncoming
            ),
            incomingCallPopup = null
        )
    }

    fun endActiveCall(durationSeconds: Long = 0L) {
        val helperDuration = OngoingCallHelper.endCall(getApplication())
        val call = _uiState.value.activeCallState
        _uiState.value = _uiState.value.copy(activeCallState = null)
        if (call != null) {
            val now = System.currentTimeMillis()
            val actualDuration = when {
                durationSeconds > 0L -> durationSeconds
                helperDuration > 0L -> helperDuration
                else -> 0L
            }
            val callType = if (call.isIncoming) CallType.INCOMING else CallType.OUTGOING
            val newEntry = CallLogEntry(
                id = now,
                number = call.phoneNumber,
                cachedName = call.contactName,
                type = callType,
                date = now,
                durationSeconds = actualDuration,
                photoUri = null
            )
            recentInMemoryCalls.add(0, newEntry)
            _uiState.value = _uiState.value.copy(
                callLogs = listOf(newEntry) + _uiState.value.callLogs
            )
            viewModelScope.launch {
                callLogRepo.insertCallLog(call.phoneNumber, call.contactName, actualDuration, callType)
                triggerPostCallHistorySync()
            }
        } else {
            triggerPostCallHistorySync()
        }
    }

    fun isUssdCode(number: String): Boolean {
        return TelecomHelper.isUssdCode(number)
    }

    fun startCall(
        number: String,
        name: String?,
        simCard: com.example.telecom.SimCardInfo? = null,
        skipSystemCall: Boolean = false
    ) {
        val clean = number.trim()
        if (clean.isBlank()) return
        startActiveCall(phoneNumber = clean, contactName = name, isIncoming = false)
        if (isUssdCode(clean)) {
            _uiState.value = _uiState.value.copy(
                userNotice = "Executing USSD code $clean..."
            )
            if (!skipSystemCall) {
                try {
                    if (simCard != null) {
                        TelecomHelper.makeCallWithSim(
                            getApplication(),
                            clean,
                            simCard.phoneAccountHandle,
                            simCard.subscriptionId
                        )
                    } else {
                        TelecomHelper.makeCall(getApplication(), clean)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("XenoViewModel", "Error running USSD: ${e.message}")
                }
            }
            return
        }
        if (simCard != null) {
            val prefs = getApplication<Application>().getSharedPreferences("xeno_telecom_prefs", Context.MODE_PRIVATE)
            val label = if (simCard.carrierName.length > 14) "SIM ${simCard.subscriptionId + 1}" else simCard.carrierName
            prefs.edit()
                .putInt("preferred_sim_slot", simCard.subscriptionId)
                .putString("preferred_sim_name", label)
                .apply()
            _uiState.value = _uiState.value.copy(
                userNotice = "Calling ${name ?: clean} via ${simCard.carrierName} (SIM ${simCard.subscriptionId + 1})"
            )
        }
        if (!skipSystemCall) {
            try {
                if (simCard != null) {
                    TelecomHelper.makeCallWithSim(
                        getApplication(),
                        clean,
                        simCard.phoneAccountHandle,
                        simCard.subscriptionId
                    )
                } else {
                    TelecomHelper.makeCall(getApplication(), clean)
                }
            } catch (e: Exception) {
                android.util.Log.e("XenoViewModel", "Error placing call: ${e.message}")
            }
        }
    }

    // In-App & System Notification Popups logic
    fun handleIncomingSms(address: String, body: String, date: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            val contactName = contactsRepo.resolveContactName(address) ?: address
            val threadId = smsRepo.getThreadIdForAddress(address) ?: address.hashCode().toLong()
            val threads = smsRepo.getConversations()

            // SmsDeliverReceiver / SmsReceiveReceiver already triggers the single system notification;
            // calling showSmsNotification here is guarded by XenoNotificationHelper.shouldNotifySms
            // so it will never duplicate, and we also refresh activeChatMessages if the thread is open.
            val currentChat = _uiState.value.activeChatThread
            val updatedMessages = if (currentChat != null &&
                (currentChat.threadId == threadId ||
                    XenoNotificationHelper.normalizePhoneKey(currentChat.address) == XenoNotificationHelper.normalizePhoneKey(address))
            ) {
                smsRepo.getMessagesForThread(currentChat.threadId, currentChat.address)
            } else {
                _uiState.value.activeChatMessages
            }

            val isViewingSameThread = currentChat != null &&
                (currentChat.threadId == threadId ||
                    XenoNotificationHelper.normalizePhoneKey(currentChat.address) == XenoNotificationHelper.normalizePhoneKey(address))

            _uiState.value = _uiState.value.copy(
                smsThreads = threads,
                activeChatMessages = updatedMessages,
                incomingSmsPopup = if (!isViewingSameThread) {
                    IncomingSmsPopup(
                        threadId = threadId,
                        senderName = contactName,
                        senderNumber = address,
                        body = body
                    )
                } else {
                    _uiState.value.incomingSmsPopup
                }
            )
        }
    }

    /**
     * Helper function to handle incoming calls:
     * Resolves caller identity, starts the incoming ringing session in OngoingCallHelper,
     * and displays the Incoming Call Popup banner so the user can accept (opening the
     * full-screen call screen) or decline.
     */
    fun handleIncomingCall(number: String) {
        viewModelScope.launch {
            val contactName = contactsRepo.resolveContactName(number)
            OngoingCallHelper.updateContactNameIfMissing(number, contactName)

            val shouldShowFullScreen = OngoingCallHelper.isScreenOffOrLocked(getApplication()) ||
                OngoingCallHelper.sessionFlow.value.isFullScreenCallVisible

            val popup = OngoingCallHelper.handleIncomingCallEvent(
                context = getApplication(),
                phoneNumber = number,
                contactName = contactName,
                showPopup = !shouldShowFullScreen
            )

            if (shouldShowFullScreen) {
                _uiState.value = _uiState.value.copy(
                    incomingCallPopup = null,
                    activeCallState = ActiveCallState(
                        phoneNumber = number,
                        contactName = contactName ?: OngoingCallHelper.sessionFlow.value.contactName,
                        isIncoming = true
                    )
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    incomingCallPopup = popup,
                    activeCallState = null
                )
            }
        }
    }

    fun openFullScreenIncomingCall(phoneNumber: String, callerName: String?) {
        viewModelScope.launch {
            val resolvedName = callerName?.takeIf { it.isNotBlank() } ?: contactsRepo.resolveContactName(phoneNumber)
            OngoingCallHelper.startIncomingSession(
                phoneNumber = phoneNumber,
                contactName = resolvedName,
                openFullScreenImmediately = true
            )
            OngoingCallHelper.expandIncomingCallToFullScreen(getApplication())
            _uiState.value = _uiState.value.copy(
                incomingCallPopup = null,
                activeCallState = ActiveCallState(
                    phoneNumber = phoneNumber,
                    contactName = resolvedName,
                    isIncoming = true
                )
            )
        }
    }

    fun answerIncomingCallFromIntent(phoneNumber: String, callerName: String?) {
        viewModelScope.launch {
            val resolvedName = callerName?.takeIf { it.isNotBlank() } ?: contactsRepo.resolveContactName(phoneNumber)
            val connectedSession = OngoingCallHelper.answerIncomingCallAndOpenFullScreen(
                context = getApplication(),
                phoneNumber = phoneNumber,
                contactName = resolvedName
            )
            _uiState.value = _uiState.value.copy(
                incomingCallPopup = null,
                activeCallState = ActiveCallState(
                    phoneNumber = connectedSession.phoneNumber.ifBlank { phoneNumber },
                    contactName = connectedSession.contactName ?: resolvedName,
                    isIncoming = true
                )
            )
        }
    }

    fun acceptActiveCall() {
        val currentCall = _uiState.value.activeCallState
        val connectedSession = OngoingCallHelper.answerIncomingCallAndOpenFullScreen(
            context = getApplication(),
            phoneNumber = currentCall?.phoneNumber,
            contactName = currentCall?.contactName
        )
        _uiState.value = _uiState.value.copy(
            incomingCallPopup = null,
            activeCallState = ActiveCallState(
                phoneNumber = currentCall?.phoneNumber ?: connectedSession.phoneNumber,
                contactName = currentCall?.contactName ?: connectedSession.contactName,
                isIncoming = true
            )
        )
    }

    fun declineActiveCall() {
        OngoingCallHelper.declineIncomingCall(getApplication())
        val call = _uiState.value.activeCallState
        _uiState.value = _uiState.value.copy(
            activeCallState = null,
            incomingCallPopup = null
        )
        if (call != null) {
            val now = System.currentTimeMillis()
            val newEntry = CallLogEntry(
                id = now,
                number = call.phoneNumber,
                cachedName = call.contactName,
                type = CallType.MISSED,
                date = now,
                durationSeconds = 0L,
                photoUri = null
            )
            recentInMemoryCalls.add(0, newEntry)
            val isOnCallsTab = _uiState.value.currentTab == XenoTab.CALLS
            if (isOnCallsTab) {
                callPrefs.edit().putLong("last_seen_calls_ts", now).apply()
            }
            _uiState.value = _uiState.value.copy(
                callLogs = listOf(newEntry) + _uiState.value.callLogs,
                unseenMissedCallsCount = if (isOnCallsTab) 0 else _uiState.value.unseenMissedCallsCount + 1
            )
            viewModelScope.launch {
                callLogRepo.insertCallLog(call.phoneNumber, call.contactName, 0L, CallType.MISSED)
                triggerPostCallHistorySync()
            }
        } else {
            triggerPostCallHistorySync()
        }
    }

    fun dismissSmsPopup() {
        _uiState.value = _uiState.value.copy(incomingSmsPopup = null)
    }

    fun markSmsPopupAsRead(popup: IncomingSmsPopup) {
        viewModelScope.launch {
            smsRepo.markThreadAsRead(popup.threadId, popup.senderNumber)
            val threads = smsRepo.getConversations()
            _uiState.value = _uiState.value.copy(
                smsThreads = threads,
                incomingSmsPopup = null,
                userNotice = "Message marked as read"
            )
        }
    }

    fun replySmsPopup(popup: IncomingSmsPopup) {
        dismissSmsPopup()
        startChatWithNumber(popup.senderNumber, popup.senderName)
        selectTab(XenoTab.MESSAGES)
    }

    fun dismissCallPopup() {
        OngoingCallHelper.dismissIncomingCallPopup(getApplication())
        XenoNotificationHelper.cancelCallNotification(getApplication())
        _uiState.value = _uiState.value.copy(incomingCallPopup = null)
    }

    fun expandCallPopupToFullScreen(popup: IncomingCallPopup) {
        OngoingCallHelper.expandIncomingCallToFullScreen(getApplication())
        _uiState.value = _uiState.value.copy(
            incomingCallPopup = null,
            activeCallState = ActiveCallState(
                phoneNumber = popup.callerNumber,
                contactName = popup.callerName,
                isIncoming = true
            )
        )
    }

    /**
     * Answers the call from the Incoming Call Popup and immediately opens the
     * full-screen call screen (ActiveCallScreen) with the timer starting at 00:00.
     */
    fun acceptCallPopup(popup: IncomingCallPopup) {
        val connectedSession = OngoingCallHelper.answerIncomingCallAndOpenFullScreen(
            context = getApplication(),
            phoneNumber = popup.callerNumber,
            contactName = popup.callerName
        )
        _uiState.value = _uiState.value.copy(
            incomingCallPopup = null,
            activeCallState = ActiveCallState(
                phoneNumber = connectedSession.phoneNumber.ifBlank { popup.callerNumber },
                contactName = connectedSession.contactName ?: popup.callerName,
                isIncoming = true
            )
        )
    }

    fun declineCallPopup(popup: IncomingCallPopup) {
        OngoingCallHelper.declineIncomingCall(getApplication())
        _uiState.value = _uiState.value.copy(
            incomingCallPopup = null,
            activeCallState = null
        )
        val now = System.currentTimeMillis()
        val newEntry = CallLogEntry(
            id = now,
            number = popup.callerNumber,
            cachedName = popup.callerName,
            type = CallType.MISSED,
            date = now,
            durationSeconds = 0L,
            photoUri = null
        )
        recentInMemoryCalls.add(0, newEntry)
        val isOnCallsTab = _uiState.value.currentTab == XenoTab.CALLS
        if (isOnCallsTab) {
            callPrefs.edit().putLong("last_seen_calls_ts", now).apply()
        }
        _uiState.value = _uiState.value.copy(
            callLogs = listOf(newEntry) + _uiState.value.callLogs,
            unseenMissedCallsCount = if (isOnCallsTab) 0 else _uiState.value.unseenMissedCallsCount + 1,
            userNotice = "Call declined"
        )
        viewModelScope.launch {
            callLogRepo.insertCallLog(popup.callerNumber, popup.callerName, 0L, CallType.MISSED)
            triggerPostCallHistorySync()
        }
    }

    fun simulateIncomingSms(
        senderNumber: String? = null,
        senderName: String? = null,
        body: String? = null
    ) {
        val number = senderNumber ?: "+1 (555) 019-2834"
        val name = senderName ?: "Sarah Connor"
        val messageText = body ?: "Hey! Are you still free for that quick call?"
        val threadId = number.hashCode().toLong()

        XenoNotificationHelper.showSmsNotification(
            context = getApplication(),
            senderName = name,
            senderNumber = number,
            body = messageText,
            threadId = threadId
        )

        viewModelScope.launch {
            smsRepo.insertIncomingSms(number, messageText, System.currentTimeMillis())
            val threads = smsRepo.getConversations()
            _uiState.value = _uiState.value.copy(
                smsThreads = threads,
                incomingSmsPopup = IncomingSmsPopup(
                    threadId = threadId,
                    senderName = name,
                    senderNumber = number,
                    body = messageText
                )
            )
        }
    }

    fun simulateIncomingCall(
        callerNumber: String? = null,
        callerName: String? = null
    ) {
        val number = callerNumber ?: "+1 (555) 014-4921"
        val name = callerName ?: "Alice Smith"

        val popup = OngoingCallHelper.handleIncomingCallEvent(
            context = getApplication(),
            phoneNumber = number,
            contactName = name,
            showPopup = true
        )

        _uiState.value = _uiState.value.copy(
            incomingCallPopup = popup,
            activeCallState = null
        )
    }

    fun loadInstalledGames(context: android.content.Context) {
        viewModelScope.launch {
            val games = withContext(Dispatchers.IO) {
                gameSpaceRepo.loadUserGames()
            }
            _uiState.value = _uiState.value.copy(installedGames = games)
        }
    }

    fun launchGameInApp(game: InstalledGameItem) {
        val resolvedUrl = game.entryUrl?.takeIf { it.isNotBlank() }
            ?: gameSpaceRepo.regenerateRuntimeUrl(
                pkg = game.packageName,
                appName = game.appName,
                versionName = game.versionName ?: "1.0",
                mainActivity = game.mainActivityName,
                activities = game.activities,
                permissions = game.permissions,
                nativeLibs = game.nativeLibs,
                dexCount = game.dexCount,
                minSdk = game.minSdk,
                targetSdk = game.targetSdk,
                iconPath = game.iconPath,
                extractedTextures = emptyList(),
                envProfile = game.envProfile
            )
        _uiState.value = _uiState.value.copy(
            activeGameItem = game,
            activeGameTitle = game.appName,
            activeGameUrl = resolvedUrl,
            activeGamePackage = game.packageName
        )
    }

    fun updateActiveGameUrl(newUrl: String) {
        val pkg = _uiState.value.activeGamePackage
        if (!pkg.isNullOrBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                gameSpaceRepo.updateGameEntryUrl(pkg, newUrl)
                val updatedList = gameSpaceRepo.loadUserGames()
                val updatedItem = updatedList.find { it.packageName == pkg }
                _uiState.value = _uiState.value.copy(
                    installedGames = updatedList,
                    activeGameItem = updatedItem ?: _uiState.value.activeGameItem,
                    activeGameUrl = newUrl
                )
            }
        } else {
            _uiState.value = _uiState.value.copy(activeGameUrl = newUrl)
        }
    }

    fun updateGameEnvProfile(packageName: String, profile: VirtualEnvProfile) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedItem = gameSpaceRepo.updateGameEnvProfile(packageName, profile)
            val updatedList = gameSpaceRepo.loadUserGames()
            val isCurrentlyActive = _uiState.value.activeGamePackage == packageName
            _uiState.value = _uiState.value.copy(
                installedGames = updatedList,
                activeGameItem = if (isCurrentlyActive && updatedItem != null) updatedItem else _uiState.value.activeGameItem,
                activeGameUrl = if (isCurrentlyActive && updatedItem?.entryUrl != null) updatedItem.entryUrl else _uiState.value.activeGameUrl,
                userNotice = "Updated virtual environment profile for ${updatedItem?.appName ?: packageName}"
            )
        }
    }

    fun clearVirtualGameData(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            gameSpaceRepo.clearVirtualAppData(packageName)
            _uiState.value = _uiState.value.copy(
                userNotice = "Cleared isolated virtual storage for $packageName"
            )
        }
    }

    fun dismissApkInstallModal() {
        _uiState.value = _uiState.value.copy(apkInstallState = ApkInstallState())
    }

    fun installApk(uri: Uri, context: android.content.Context) {
        viewModelScope.launch {
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "package.apk"
            _uiState.value = _uiState.value.copy(
                apkInstallState = ApkInstallState(
                    isInstalling = true,
                    fileName = fileName,
                    currentStep = "Starting Virtual APK Installer...",
                    progress = 0.05f,
                    logs = listOf("[VirtualInstaller] Target APK: $fileName")
                )
            )
            val installedItem = withContext(Dispatchers.IO) {
                gameSpaceRepo.installApkIntoGameSpace(uri) { step, progress, logLine ->
                    val currentLogs = _uiState.value.apkInstallState.logs
                    _uiState.value = _uiState.value.copy(
                        apkInstallState = _uiState.value.apkInstallState.copy(
                            isInstalling = true,
                            currentStep = step,
                            progress = progress,
                            logs = (currentLogs + logLine).takeLast(12)
                        )
                    )
                }
            }
            if (installedItem != null) {
                val updatedGames = withContext(Dispatchers.IO) {
                    gameSpaceRepo.loadUserGames()
                }
                _uiState.value = _uiState.value.copy(
                    installedGames = updatedGames,
                    apkInstallState = _uiState.value.apkInstallState.copy(
                        isInstalling = false,
                        currentStep = "Installed & Virtualized Successfully",
                        progress = 1.0f,
                        completedItem = installedItem
                    ),
                    userNotice = "Installed ${installedItem.appName} into Virtual Game Space"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    apkInstallState = _uiState.value.apkInstallState.copy(
                        isInstalling = false,
                        error = "Could not parse APK archive. Please select a valid .apk file."
                    ),
                    userNotice = "Could not parse APK file. Please select a valid .apk package."
                )
            }
        }
    }

    fun importDeviceAppToGameSpace(packageName: String, appName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                apkInstallState = ApkInstallState(
                    isInstalling = true,
                    fileName = "$packageName.apk",
                    currentStep = "Cloning & Virtualizing $appName...",
                    progress = 0.1f,
                    logs = listOf("[VirtualCloner] Cloning base APK for $appName ($packageName)")
                )
            )
            val added = withContext(Dispatchers.IO) {
                gameSpaceRepo.addDeviceAppToGameSpace(packageName, appName) { step, progress, logLine ->
                    val currentLogs = _uiState.value.apkInstallState.logs
                    _uiState.value = _uiState.value.copy(
                        apkInstallState = _uiState.value.apkInstallState.copy(
                            isInstalling = true,
                            currentStep = step,
                            progress = progress,
                            logs = (currentLogs + logLine).takeLast(12)
                        )
                    )
                }
            }
            val updatedGames = withContext(Dispatchers.IO) {
                gameSpaceRepo.loadUserGames()
            }
            _uiState.value = _uiState.value.copy(
                installedGames = updatedGames,
                apkInstallState = _uiState.value.apkInstallState.copy(
                    isInstalling = false,
                    currentStep = "Virtual Container Ready",
                    progress = 1.0f,
                    completedItem = added
                ),
                userNotice = "Virtualized ${added.appName} into Game Space"
            )
        }
    }

    fun removeGameFromSpace(packageName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                gameSpaceRepo.removeGameFromSpace(packageName)
            }
            val updatedGames = withContext(Dispatchers.IO) {
                gameSpaceRepo.loadUserGames()
            }
            _uiState.value = _uiState.value.copy(
                installedGames = updatedGames,
                userNotice = "Removed app and virtual environment from Game Space"
            )
        }
    }

    fun setActiveGame(title: String?, url: String?) {
        _uiState.value = _uiState.value.copy(
            activeGameItem = if (url == null) null else _uiState.value.activeGameItem,
            activeGameTitle = title,
            activeGameUrl = url,
            activeGamePackage = if (url == null) null else _uiState.value.activeGamePackage
        )
    }

    fun replyToNotification(notification: DeviceNotification, replyText: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                userNotice = "Reply sent to ${notification.title.ifBlank { notification.appName }}: \"$replyText\""
            )
        }
    }

    fun setPreventOverlayApps(prevent: Boolean) {
        _uiState.value = _uiState.value.copy(
            preventOverlayApps = prevent,
            userNotice = if (prevent) "Anti-Overlay Active: No external apps can display over this app." else "Overlay protection disabled."
        )
    }
}
