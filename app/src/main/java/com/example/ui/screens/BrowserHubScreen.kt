package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.BrowserHistoryItem
import com.example.data.models.ClonedAppInstance
import com.example.data.models.WebTab
import com.example.ui.components.AddCloneDialog
import com.example.ui.components.EditCloneDialog
import com.example.ui.viewmodel.BrowserDualTab

@Composable
fun BrowserHubScreen(
    dualTab: BrowserDualTab,
    onDualTabChanged: (BrowserDualTab) -> Unit,

    // Browser parameters
    tabs: List<WebTab>,
    activeTabId: String,
    history: List<BrowserHistoryItem>,
    showTabSwitcher: Boolean,
    showHistoryDialog: Boolean,
    onOpenNewTab: (String) -> Unit,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onUpdateTabUrl: (String, String) -> Unit,
    onUpdateTabLoading: (Boolean, Int) -> Unit,
    onUpdateTabNavState: (Boolean, Boolean) -> Unit,
    onSetShowTabSwitcher: (Boolean) -> Unit,
    onSetShowHistoryDialog: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    isBrowserHeaderHidden: Boolean,
    onToggleBrowserHeader: () -> Unit,
    isBrowserFullscreenVideo: Boolean = false,
    onSetBrowserFullscreenVideo: (Boolean) -> Unit = {},

    // Space Sandbox parameters
    spaceClones: List<ClonedAppInstance>,
    activeSpaceDock: List<String>,
    activeSpaceInstance: ClonedAppInstance?,
    showAddCloneDialog: Boolean,
    selectedCloneForEdit: ClonedAppInstance?,
    onLaunchClone: (String) -> Unit,
    onCloseCloneWorkspace: () -> Unit,
    onOpenAddClone: () -> Unit,
    onCloseAddClone: () -> Unit,
    onCreateClone: (packageName: String, appName: String, displayName: String, badgeTag: String, badgeColorHex: String) -> Unit,
    onOpenEditClone: (ClonedAppInstance) -> Unit,
    onCloseEditClone: () -> Unit,
    onUpdateClone: (ClonedAppInstance) -> Unit,
    onDeleteClone: (String) -> Unit,
    onCloseFromDock: (String) -> Unit,
    onCloneUrlChanged: (instanceId: String, url: String) -> Unit,
    onToggleDesktopMode: (instanceId: String) -> Unit,

    // Downloads & Passwords parameters
    downloads: List<com.example.data.models.DownloadItem> = emptyList(),
    onDeleteDownload: (com.example.data.models.DownloadItem) -> Unit = {},
    onClearAllDownloads: () -> Unit = {},
    onAddDownloadItem: (dmId: Long, url: String, fileName: String, mimeType: String, fileSize: Long) -> Unit = { _, _, _, _, _ -> },
    savedPasswords: List<com.example.data.models.SavedPassword> = emptyList(),
    showPasswordManager: Boolean = false,
    onSetShowPasswordManager: (Boolean) -> Unit = {},
    onSetShowMediaManager: (Boolean) -> Unit = {},
    onSavePassword: (domain: String, title: String, username: String, pass: String) -> Unit = { _, _, _, _ -> },
    onDeletePassword: (Long) -> Unit = {},

    modifier: Modifier = Modifier
) {
    // If an active sandboxed clone workspace is open with a web URL, show the workspace full-screen
    if (activeSpaceInstance != null && activeSpaceInstance.targetUrl.isNotBlank() && dualTab == BrowserDualTab.SPACE) {
        CloneWorkspaceScreen(
            instance = activeSpaceInstance,
            onCloseWorkspace = onCloseCloneWorkspace,
            onUrlChanged = { url -> onCloneUrlChanged(activeSpaceInstance.instanceId, url) },
            onToggleDesktopMode = { onToggleDesktopMode(activeSpaceInstance.instanceId) },
            onOpenInBrowser = { url ->
                onOpenNewTab(url)
                onCloseCloneWorkspace()
                onDualTabChanged(BrowserDualTab.BROWSER)
            },
            modifier = modifier
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("browser_hub_screen")
    ) {
        // Main Content View
        Box(modifier = Modifier.fillMaxSize()) {
            when (dualTab) {
                BrowserDualTab.BROWSER -> {
                    BrowserScreen(
                        tabs = tabs,
                        activeTabId = activeTabId,
                        history = history,
                        showTabSwitcher = showTabSwitcher,
                        showHistoryDialog = showHistoryDialog,
                        onOpenNewTab = onOpenNewTab,
                        onSelectTab = onSelectTab,
                        onCloseTab = onCloseTab,
                        onUpdateTabUrl = onUpdateTabUrl,
                        onUpdateTabLoading = onUpdateTabLoading,
                        onUpdateTabNavState = onUpdateTabNavState,
                        onSetShowTabSwitcher = onSetShowTabSwitcher,
                        onSetShowHistoryDialog = onSetShowHistoryDialog,
                        onClearHistory = onClearHistory,
                        isBrowserHeaderHidden = isBrowserHeaderHidden,
                        onToggleBrowserHeader = onToggleBrowserHeader,
                        isBrowserFullscreenVideo = isBrowserFullscreenVideo,
                        onSetBrowserFullscreenVideo = onSetBrowserFullscreenVideo,
                        onAddDownloadItem = onAddDownloadItem,
                        savedPasswords = savedPasswords,
                        showPasswordManager = showPasswordManager,
                        onSetShowPasswordManager = onSetShowPasswordManager,
                        onSavePassword = onSavePassword,
                        onDeletePassword = onDeletePassword,
                        onOpenSpace = { onDualTabChanged(BrowserDualTab.SPACE) },
                        onOpenDownloads = { onDualTabChanged(BrowserDualTab.DOWNLOADS) },
                        onSetShowMediaManager = onSetShowMediaManager
                    )
                }
                BrowserDualTab.SPACE -> {
                    SpaceSandboxScreen(
                        clones = spaceClones,
                        activeDockIds = activeSpaceDock,
                        onLaunchInstance = onLaunchClone,
                        onOpenAddClone = onOpenAddClone,
                        onOpenEditClone = onOpenEditClone,
                        onDeleteClone = onDeleteClone,
                        onCloseFromDock = onCloseFromDock,
                        onBackToBrowser = { onDualTabChanged(BrowserDualTab.BROWSER) }
                    )
                }
                BrowserDualTab.DOWNLOADS -> {
                    BrowserDownloadsScreen(
                        downloads = downloads,
                        onDeleteDownload = onDeleteDownload,
                        onClearAllDownloads = onClearAllDownloads,
                        onStartBrowsing = { onDualTabChanged(BrowserDualTab.BROWSER) }
                    )
                }
            }
        }
    }

    // Modal: Add / Clone App
    if (showAddCloneDialog) {
        AddCloneDialog(
            onDismiss = onCloseAddClone,
            onCloneCreated = onCreateClone
        )
    }

    // Modal: Edit / Rename Clone
    selectedCloneForEdit?.let { cloneToEdit ->
        EditCloneDialog(
            instance = cloneToEdit,
            onDismiss = onCloseEditClone,
            onSave = onUpdateClone,
            onDelete = onDeleteClone
        )
    }
}
