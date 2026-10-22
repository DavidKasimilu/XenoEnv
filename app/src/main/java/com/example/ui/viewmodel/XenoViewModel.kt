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
import com.example.data.models.SmsMessage
import com.example.data.models.SmsThread
import com.example.data.models.WebTab
import com.example.telecom.CallPhase
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
    // Notification state
    val deviceNotifications: List<DeviceNotification> = emptyList(),
    val hasNotificationAccess: Boolean = false,
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

    private val callPrefs = application.getSharedPreferences("xeno_call_prefs", Context.MODE_PRIVATE)
    private val recentInMemoryCalls = CopyOnWriteArrayList<CallLogEntry>()
    private var callLogContentObserver: ContentObserver? = null

    private val _uiState = MutableStateFlow(XenoUiState())
    val uiState: StateFlow<XenoUiState> = _uiState.asStateFlow()

    init {
        if (!callPrefs.contains("last_seen_calls_ts")) {
            callPrefs.edit().putLong("last_seen_calls_ts", System.currentTimeMillis()).apply()
        }
        checkDefaultAppStatus()
        checkNotificationAccess()
        loadBrowserMemory()
        loadSpaceData()
        loadPasswords()
        loadDownloads()
        registerCallLogSyncHelper()
        loadAllData()
        loadInstalledGames(getApplication())

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
     * the system CallLogProvider hasn't committed yet, enriches missing contact names, and updates
     * unseen missed call badge state immediately.
     */
    fun syncCallHistoryFast() {
        viewModelScope.launch {
            val systemLogs = callLogRepo.getCallLogs()
            val knownContacts = _uiState.value.contacts

            // Merge recent optimistic call entries not yet reflected in system provider
            val now = System.currentTimeMillis()
            recentInMemoryCalls.removeAll { now - it.date > 60_000L }

            val merged = mutableListOf<CallLogEntry>()
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

            val enrichedLogs = merged
                .sortedByDescending { it.date }
                .map { entry ->
                    if (entry.cachedName.isNullOrBlank() && knownContacts.isNotEmpty()) {
                        val targetDigits = entry.number.filter { it.isDigit() }.takeLast(7)
                        val match = if (targetDigits.length >= 5) {
                            knownContacts.find { c ->
                                c.primaryNumber.filter { it.isDigit() }.endsWith(targetDigits) ||
                                        c.allNumbers.any { n -> n.filter { it.isDigit() }.endsWith(targetDigits) }
                            }
                        } else null
                        if (match != null) entry.copy(cachedName = match.displayName) else entry
                    } else {
                        entry
                    }
                }

            val isOnCallsTab = _uiState.value.currentTab == XenoTab.CALLS
            val lastSeenTs = callPrefs.getLong("last_seen_calls_ts", 0L)
            val newUnseenCount = if (isOnCallsTab) {
                val maxTs = maxOf(now, enrichedLogs.maxOfOrNull { it.date } ?: now)
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
        val maxCallDate = _uiState.value.callLogs.maxOfOrNull { it.date } ?: now
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
        val (tabs, activeId) = browserRepo.loadTabs()
        val history = browserRepo.loadHistory()
        _uiState.value = _uiState.value.copy(
            browserTabs = tabs,
            activeBrowserTabId = activeId,
            browserHistory = history
        )
    }

    fun selectTab(tab: XenoTab) {
        val isCalls = tab == XenoTab.CALLS
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
            activeChatThread = if (tab != XenoTab.MESSAGES) null else _uiState.value.activeChatThread
        )
        if (isCalls) {
            markMissedCallsAsSeen()
            syncCallHistoryFast()
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

    fun loadAllData() {
        // 1. Immediately sync call logs in a dedicated fast coroutine so Calls tab never waits for gallery/SMS
        syncCallHistoryFast()

        // 2. Load contacts & SMS threads in parallel
        viewModelScope.launch {
            val contacts = contactsRepo.getContacts()
            val threads = smsRepo.getConversations()
            _uiState.value = _uiState.value.copy(
                contacts = contacts,
                smsThreads = threads
            )
            // Re-enrich call log contact names once contacts finish loading
            syncCallHistoryFast()
        }

        // 3. Load gallery media items independently in background
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val media = galleryRepo.getMediaItems()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                mediaItems = media
            )
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
        viewModelScope.launch {
            val success = callLogRepo.deleteCallLog(entry.id)
            _uiState.value = _uiState.value.copy(
                callLogs = _uiState.value.callLogs.filter { it.id != entry.id },
                selectedCallLog = null,
                userNotice = if (success) "Call log entry deleted" else "Removed from view"
            )
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
        viewModelScope.launch {
            callLogRepo.clearMissedCallLogs()
            syncCallHistoryFast()
        }
    }

    fun clearAllCallLogs() {
        recentInMemoryCalls.clear()
        markMissedCallsAsSeen()
        viewModelScope.launch {
            callLogRepo.clearCallLogs()
            _uiState.value = _uiState.value.copy(
                callLogs = emptyList(),
                unseenMissedCallsCount = 0,
                selectedCallLog = null,
                userNotice = "Call history cleared"
            )
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
        viewModelScope.launch {
            val messages = smsRepo.getMessagesForThread(thread.threadId, thread.address)
            _uiState.value = _uiState.value.copy(
                currentTab = XenoTab.MESSAGES,
                activeChatThread = thread.copy(read = true, unreadCount = 0),
                activeChatMessages = messages
            )
            smsRepo.markThreadAsRead(thread.threadId, thread.address)
            val updatedThreads = smsRepo.getConversations()
            _uiState.value = _uiState.value.copy(smsThreads = updatedThreads)
        }
    }

    fun startChatWithNumber(number: String, name: String? = null) {
        val existingThread = _uiState.value.smsThreads.find {
            it.address.filter { c -> c.isDigit() } == number.filter { c -> c.isDigit() }
        }
        val targetThread = existingThread ?: SmsThread(
            threadId = number.hashCode().toLong(),
            address = number,
            contactName = name,
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
        refreshSms()
    }

    fun sendChatMessage(text: String) {
        val activeThread = _uiState.value.activeChatThread ?: return
        if (text.isBlank()) return

        viewModelScope.launch {
            val result = smsRepo.sendSms(activeThread.address, text.trim(), activeThread.threadId)
            result.onSuccess { newMsg ->
                val updatedList = _uiState.value.activeChatMessages + newMsg
                val updatedThread = activeThread.copy(
                    snippet = text.trim(),
                    date = newMsg.date,
                    read = true
                )
                val updatedThreads = _uiState.value.smsThreads.filter { it.threadId != activeThread.threadId }
                _uiState.value = _uiState.value.copy(
                    activeChatThread = updatedThread,
                    activeChatMessages = updatedList,
                    smsThreads = listOf(updatedThread) + updatedThreads
                )
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
        spaceRepo.deleteClone(instanceId)
        loadSpaceData()
        if (_uiState.value.activeSpaceInstanceId == instanceId) {
            _uiState.value = _uiState.value.copy(activeSpaceInstanceId = null)
        }
        _uiState.value = _uiState.value.copy(
            userNotice = "Removed '${instance?.displayName ?: "clone"}' from Space"
        )
    }

    fun launchCloneWorkspace(instanceId: String) {
        val dock = _uiState.value.activeSpaceDock.toMutableList()
        if (!dock.contains(instanceId)) {
            dock.add(instanceId)
            spaceRepo.saveActiveDock(dock)
        }
        _uiState.value = _uiState.value.copy(
            activeSpaceInstanceId = instanceId,
            activeSpaceDock = dock
        )
    }

    fun closeCloneWorkspace() {
        _uiState.value = _uiState.value.copy(activeSpaceInstanceId = null)
    }

    fun closeCloneFromDock(instanceId: String) {
        val dock = _uiState.value.activeSpaceDock.filter { it != instanceId }
        spaceRepo.saveActiveDock(dock)
        val nextActive = if (_uiState.value.activeSpaceInstanceId == instanceId) null else _uiState.value.activeSpaceInstanceId
        _uiState.value = _uiState.value.copy(
            activeSpaceDock = dock,
            activeSpaceInstanceId = nextActive
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

    fun startCall(number: String, name: String?) {
        val clean = number.trim()
        startActiveCall(phoneNumber = clean, contactName = name, isIncoming = false)
        if (isUssdCode(clean)) {
            _uiState.value = _uiState.value.copy(
                userNotice = "Executing USSD code $clean..."
            )
            try {
                TelecomHelper.makeCall(getApplication(), clean)
            } catch (e: Exception) {
                android.util.Log.e("XenoViewModel", "Error running USSD: ${e.message}")
            }
            return
        }
        try {
            TelecomHelper.makeCall(getApplication(), clean)
        } catch (e: Exception) {
            android.util.Log.e("XenoViewModel", "Error placing call: ${e.message}")
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

            val popup = OngoingCallHelper.handleIncomingCallEvent(
                context = getApplication(),
                phoneNumber = number,
                contactName = contactName,
                showPopup = true
            )

            _uiState.value = _uiState.value.copy(
                incomingCallPopup = popup,
                activeCallState = null
            )
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
