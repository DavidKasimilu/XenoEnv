package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BrowserRepository
import com.example.data.CallLogRepository
import com.example.data.ContactsRepository
import com.example.data.DownloadRepository
import com.example.data.GalleryRepository
import com.example.data.PasswordRepository
import com.example.data.SmsRepository
import com.example.data.SpaceRepository
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
import com.example.data.models.InstalledGameItem
import com.example.data.models.MediaItem
import com.example.data.models.SmsMessage
import com.example.data.models.SmsThread
import com.example.data.models.WebTab
import com.example.telecom.SmsDeliverReceiver
import com.example.telecom.XenoNotificationHelper
import com.example.telecom.TelecomHelper
import com.example.telecom.XenoInCallService
import com.example.telecom.XenoNotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class XenoTab {
    CALLS,
    CONTACTS,
    KEYPAD,
    MESSAGES,
    BROWSER,
    GAMES,
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
    val activeGameUrl: String? = null,
    val activeGameTitle: String? = null
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
    private val passwordRepo = PasswordRepository(application)
    private val downloadRepo = DownloadRepository(application)

    private val _uiState = MutableStateFlow(XenoUiState())
    val uiState: StateFlow<XenoUiState> = _uiState.asStateFlow()

    init {
        checkDefaultAppStatus()
        checkNotificationAccess()
        loadBrowserMemory()
        loadSpaceData()
        loadPasswords()
        loadDownloads()
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
            dismissCallPopup()
            if (_uiState.value.activeCallState != null) {
                endActiveCall(0L)
            }
        }
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
        _uiState.value = _uiState.value.copy(
            currentTab = tab,
            activeChatThread = if (tab != XenoTab.MESSAGES) null else _uiState.value.activeChatThread
        )
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
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val calls = callLogRepo.getCallLogs()
            val contacts = contactsRepo.getContacts()
            val threads = smsRepo.getConversations()
            val media = galleryRepo.getMediaItems()

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                callLogs = calls,
                contacts = contacts,
                smsThreads = threads,
                mediaItems = media
            )
        }
    }

    // Call Log functions
    fun setCallFilter(filter: CallFilter) {
        _uiState.value = _uiState.value.copy(callFilter = filter)
    }

    fun selectCallLog(entry: CallLogEntry?) {
        _uiState.value = _uiState.value.copy(selectedCallLog = entry)
    }

    fun deleteCallLog(entry: CallLogEntry) {
        viewModelScope.launch {
            val success = callLogRepo.deleteCallLog(entry.id)
            _uiState.value = _uiState.value.copy(
                callLogs = _uiState.value.callLogs.filter { it.id != entry.id },
                selectedCallLog = null,
                userNotice = if (success) "Call log entry deleted" else "Removed from view"
            )
        }
    }

    fun clearAllCallLogs() {
        viewModelScope.launch {
            callLogRepo.clearCallLogs()
            _uiState.value = _uiState.value.copy(
                callLogs = emptyList(),
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
        viewModelScope.launch {
            val logs = callLogRepo.getCallLogs()
            _uiState.value = _uiState.value.copy(callLogs = logs)
        }
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
        if (show && _uiState.value.mediaItems.isEmpty()) {
            refreshMedia()
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
        _uiState.value = _uiState.value.copy(browserDualTab = tab)
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
        spaceRepo.updateCloneUrl(instanceId, url)
        val updatedList = _uiState.value.spaceClones.map {
            if (it.instanceId == instanceId) it.copy(lastUrl = url, lastActiveTime = System.currentTimeMillis()) else it
        }
        _uiState.value = _uiState.value.copy(spaceClones = updatedList)
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
        XenoNotificationHelper.cancelCallNotification(getApplication())
        XenoInCallService.endCall()
        val call = _uiState.value.activeCallState
        if (call != null) {
            viewModelScope.launch {
                val actualDuration = if (durationSeconds > 0) durationSeconds else 1L
                val callType = if (call.isIncoming) CallType.INCOMING else CallType.OUTGOING
                val newEntry = CallLogEntry(
                    id = System.currentTimeMillis(),
                    number = call.phoneNumber,
                    cachedName = call.contactName,
                    type = callType,
                    date = System.currentTimeMillis(),
                    durationSeconds = actualDuration,
                    photoUri = null
                )
                // Insert to system call log if permission available
                callLogRepo.insertCallLog(call.phoneNumber, call.contactName, actualDuration, callType)
                val logs = callLogRepo.getCallLogs()
                val updatedLogs = if (logs.none { it.number == call.phoneNumber && Math.abs(it.date - newEntry.date) < 5000 }) {
                    listOf(newEntry) + logs
                } else {
                    logs
                }
                _uiState.value = _uiState.value.copy(callLogs = updatedLogs)
            }
        }
        _uiState.value = _uiState.value.copy(activeCallState = null)
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

            XenoNotificationHelper.showSmsNotification(
                context = getApplication(),
                senderName = contactName,
                senderNumber = address,
                body = body,
                threadId = threadId
            )

            _uiState.value = _uiState.value.copy(
                smsThreads = threads,
                incomingSmsPopup = IncomingSmsPopup(
                    threadId = threadId,
                    senderName = contactName,
                    senderNumber = address,
                    body = body,
                    timestamp = date
                )
            )
        }
    }

    fun handleIncomingCall(number: String) {
        viewModelScope.launch {
            val contactName = contactsRepo.resolveContactName(number)

            XenoNotificationHelper.showCallNotification(
                context = getApplication(),
                callerName = contactName,
                callerNumber = number
            )

            startActiveCall(phoneNumber = number, contactName = contactName, isIncoming = true)
        }
    }

    fun acceptActiveCall() {
        XenoNotificationHelper.cancelCallNotification(getApplication())
        XenoInCallService.answerCall()
        val call = _uiState.value.activeCallState
        if (call != null) {
            _uiState.value = _uiState.value.copy(
                activeCallState = call.copy(isIncoming = false)
            )
        }
    }

    fun declineActiveCall() {
        XenoNotificationHelper.cancelCallNotification(getApplication())
        XenoInCallService.rejectCall()
        val call = _uiState.value.activeCallState
        if (call != null) {
            viewModelScope.launch {
                val newEntry = CallLogEntry(
                    id = System.currentTimeMillis(),
                    number = call.phoneNumber,
                    cachedName = call.contactName,
                    type = CallType.MISSED,
                    date = System.currentTimeMillis(),
                    durationSeconds = 0L,
                    photoUri = null
                )
                callLogRepo.insertCallLog(call.phoneNumber, call.contactName, 0L, CallType.MISSED)
                val logs = callLogRepo.getCallLogs()
                _uiState.value = _uiState.value.copy(callLogs = listOf(newEntry) + logs)
            }
        }
        _uiState.value = _uiState.value.copy(activeCallState = null)
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
        XenoNotificationHelper.cancelCallNotification(getApplication())
        _uiState.value = _uiState.value.copy(incomingCallPopup = null)
    }

    fun acceptCallPopup(popup: IncomingCallPopup) {
        dismissCallPopup()
        XenoInCallService.answerCall()
        startActiveCall(popup.callerNumber, popup.callerName, isIncoming = false)
    }

    fun declineCallPopup(popup: IncomingCallPopup) {
        dismissCallPopup()
        XenoInCallService.rejectCall()
        viewModelScope.launch {
            val newEntry = CallLogEntry(
                id = System.currentTimeMillis(),
                number = popup.callerNumber,
                cachedName = popup.callerName,
                type = CallType.MISSED,
                date = System.currentTimeMillis(),
                durationSeconds = 0L,
                photoUri = null
            )
            callLogRepo.insertCallLog(popup.callerNumber, popup.callerName, 0L, CallType.MISSED)
            val logs = callLogRepo.getCallLogs()
            val updatedLogs = if (logs.none { it.number == popup.callerNumber && Math.abs(it.date - newEntry.date) < 5000 }) {
                listOf(newEntry) + logs
            } else logs
            _uiState.value = _uiState.value.copy(
                callLogs = updatedLogs,
                userNotice = "Call declined"
            )
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

        XenoNotificationHelper.showCallNotification(
            context = getApplication(),
            callerName = name,
            callerNumber = number
        )

        startActiveCall(phoneNumber = number, contactName = name, isIncoming = true)
    }

    fun loadInstalledGames(context: android.content.Context) {
        viewModelScope.launch {
            val pm = context.packageManager
            val packages = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(android.content.pm.PackageManager.PackageInfoFlags.of(android.content.pm.PackageManager.GET_ACTIVITIES.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(android.content.pm.PackageManager.GET_ACTIVITIES)
            }
            val games = mutableListOf<InstalledGameItem>()
            for (pkg in packages) {
                val appInfo = pkg.applicationInfo ?: continue
                val appName = pm.getApplicationLabel(appInfo).toString()
                val isGame = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    appInfo.category == android.content.pm.ApplicationInfo.CATEGORY_GAME ||
                            appName.contains("game", ignoreCase = true) ||
                            pkg.packageName.contains("game", ignoreCase = true) ||
                            pkg.packageName.contains("unity", ignoreCase = true) ||
                            pkg.packageName.contains("godot", ignoreCase = true)
                } else {
                    appName.contains("game", ignoreCase = true) || pkg.packageName.contains("game", ignoreCase = true)
                }
                val isUser = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0
                if (isUser || isGame) {
                    games.add(
                        InstalledGameItem(
                            packageName = pkg.packageName,
                            appName = appName,
                            versionName = pkg.versionName,
                            isUserApp = isUser
                        )
                    )
                }
            }
            _uiState.value = _uiState.value.copy(installedGames = games.sortedBy { it.appName })
        }
    }

    fun launchGame(packageName: String, context: android.content.Context) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                _uiState.value = _uiState.value.copy(userNotice = "Cannot launch $packageName")
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(userNotice = "Error launching game: ${e.message}")
        }
    }

    fun installApk(uri: Uri, context: android.content.Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            _uiState.value = _uiState.value.copy(userNotice = "Opening APK installer...")
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(userNotice = "Failed to open installer: ${e.message}")
        }
    }

    fun setActiveGame(title: String?, url: String?) {
        _uiState.value = _uiState.value.copy(
            activeGameTitle = title,
            activeGameUrl = url
        )
    }

    fun replyToNotification(notification: DeviceNotification, replyText: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                userNotice = "Reply sent to ${notification.title.ifBlank { notification.appName }}: \"$replyText\""
            )
        }
    }
}
