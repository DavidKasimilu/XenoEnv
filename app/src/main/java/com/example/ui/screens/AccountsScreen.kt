package com.example.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.dualspace.DualSpaceAppItem
import com.example.dualspace.DualSpaceBridge
import com.example.dualspace.DualSpaceStatus
import com.example.ui.components.AppIconImage
import com.example.ui.components.parseHexColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val DualSpacePurple = Color(0xFF7050E8)
val DualSpaceLightRing = Color(0xFFE0D8FF)

/**
 * Draws the signature Dual Space icon: two overlapping circles on a #7050E8 purple background,
 * matching `/native/dualspace/app/src/main/res/drawable/ic_launcher.xml`.
 */
@Composable
fun DualSpaceOverlappingCirclesIcon(
    boxSize: Dp = 64.dp,
    cornerRadius: Dp = 18.dp,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(boxSize)
            .clip(RoundedCornerShape(cornerRadius))
            .background(DualSpacePurple)
            .semantics { contentDescription = "Accounts Dual Space Icon" }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val scale = w / 108f
            val radius = 16f * scale
            val stroke = 8f * scale
            val centerY = h * 0.5f
            val leftCenterX = 42f * scale
            val rightCenterX = 66f * scale

            // Left circle (#FFFFFF)
            drawCircle(
                color = Color.White,
                radius = radius,
                center = Offset(leftCenterX, centerY),
                style = Stroke(width = stroke)
            )

            // Right overlapping circle (#E0D8FF)
            drawCircle(
                color = DualSpaceLightRing,
                radius = radius,
                center = Offset(rightCenterX, centerY),
                style = Stroke(width = stroke)
            )
        }
    }
}

/**
 * The "Accounts" tile for the Clone Space grid, matching the exact layout, typography,
 * dimensions, and touch feedback of existing Clone Space tiles.
 */
@Composable
fun AccountsShortcutIconItem(
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(96.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .semantics { contentDescription = "Accounts - Dual Space app cloner" }
            .testTag("clone_space_accounts_shortcut")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(68.dp)
        ) {
            DualSpaceOverlappingCirclesIcon(
                boxSize = 64.dp,
                cornerRadius = 18.dp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Accounts",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

private enum class AccountsFilter {
    ALL,
    CLONED
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AccountsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf(DualSpaceBridge.getStatus(context)) }
    var apps by remember { mutableStateOf<List<DualSpaceAppItem>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(true) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var activeFilter by rememberSaveable { mutableStateOf(AccountsFilter.ALL) }
    var friendlyError by rememberSaveable { mutableStateOf<String?>(null) }
    var showHowItWorksSheet by rememberSaveable { mutableStateOf(false) }
    var appPendingRemoval by remember { mutableStateOf<DualSpaceAppItem?>(null) }
    var appPendingCloneConfirm by remember { mutableStateOf<DualSpaceAppItem?>(null) }
    var reloadTrigger by remember { mutableIntStateOf(0) }

    // Load status + installed apps with smooth skeleton transition
    LaunchedEffect(reloadTrigger) {
        isLoadingApps = true
        status = DualSpaceBridge.getStatus(context)
        val loaded = withContext(Dispatchers.IO) {
            DualSpaceBridge.loadInstalledApps(context)
        }
        delay(220) // brief polish delay so skeleton loader transitions smoothly
        apps = loaded
        isLoadingApps = false
    }

    // Refresh automatically when returning from Android work-profile provisioning
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                status = DualSpaceBridge.getStatus(context)
                scope.launch {
                    val loaded = withContext(Dispatchers.IO) {
                        DualSpaceBridge.loadInstalledApps(context)
                    }
                    apps = loaded
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val provisioningLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        status = DualSpaceBridge.getStatus(context)
        if (result.resultCode == Activity.RESULT_OK || status.hasHardwareProfile) {
            friendlyError = null
            Toast.makeText(context, "Dual Space created!", Toast.LENGTH_LONG).show()
            reloadTrigger++
        } else {
            // Enable Safe Preview Mode automatically so the user is never blocked on emulators/devices without managed profiles
            DualSpaceBridge.setPreviewProfileActive(context, true)
            status = DualSpaceBridge.getStatus(context)
            friendlyError = "Hardware work-profile setup was cancelled or unavailable on this device. Safe Preview Mode is now active so you can clone and manage apps seamlessly."
            Toast.makeText(context, "Dual Space active (Safe Preview Mode)", Toast.LENGTH_SHORT).show()
            reloadTrigger++
        }
    }

    fun handleStartProvisioning() {
        friendlyError = null
        val intent = DualSpaceBridge.createProvisioningIntent(context)
        try {
            if (DualSpaceBridge.isManagedProfileProvisioningSupported(context)) {
                provisioningLauncher.launch(intent)
            } else {
                DualSpaceBridge.setPreviewProfileActive(context, true)
                status = DualSpaceBridge.getStatus(context)
                friendlyError = "Work profiles are not natively provisioned on this environment — switched to Safe Preview Mode with full app cloning support."
                Toast.makeText(context, "Dual Space created in Safe Preview Mode!", Toast.LENGTH_SHORT).show()
                reloadTrigger++
            }
        } catch (_: ActivityNotFoundException) {
            DualSpaceBridge.setPreviewProfileActive(context, true)
            status = DualSpaceBridge.getStatus(context)
            friendlyError = "Work profiles are not supported on this device — activated Safe Preview Mode."
            Toast.makeText(context, "Dual Space created in Safe Preview Mode!", Toast.LENGTH_SHORT).show()
            reloadTrigger++
        } catch (e: Exception) {
            DualSpaceBridge.setPreviewProfileActive(context, true)
            status = DualSpaceBridge.getStatus(context)
            friendlyError = "Could not launch system provisioning (${e.localizedMessage ?: "fallback"}). Safe Preview Mode enabled."
            reloadTrigger++
        }
    }

    fun performClone(app: DualSpaceAppItem) {
        val result = DualSpaceBridge.sendCloneOrRemoveRequest(
            context = context,
            pkg = app.pkg,
            appLabel = app.label,
            remove = false
        )
        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
        status = DualSpaceBridge.getStatus(context)
        apps = apps.map {
            if (it.pkg == app.pkg) it.copy(isCloned = true) else it
        }.sortedWith(
            compareByDescending<DualSpaceAppItem> { it.isCloned }
                .thenBy { it.label.lowercase() }
        )
    }

    fun performRemoveClone(app: DualSpaceAppItem) {
        val result = DualSpaceBridge.sendCloneOrRemoveRequest(
            context = context,
            pkg = app.pkg,
            appLabel = app.label,
            remove = true
        )
        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
        status = DualSpaceBridge.getStatus(context)
        apps = apps.map {
            if (it.pkg == app.pkg) it.copy(isCloned = false) else it
        }.sortedWith(
            compareByDescending<DualSpaceAppItem> { it.isCloned }
                .thenBy { it.label.lowercase() }
        )
    }

    val clonedCount = remember(apps) { apps.count { it.isCloned } }
    val filteredApps = remember(apps, searchQuery, activeFilter) {
        apps.filter { app ->
            val matchesFilter = when (activeFilter) {
                AccountsFilter.ALL -> true
                AccountsFilter.CLONED -> app.isCloned
            }
            val q = searchQuery.trim()
            val matchesSearch = q.isEmpty() ||
                app.label.contains(q, ignoreCase = true) ||
                app.pkg.contains(q, ignoreCase = true)
            matchesFilter && matchesSearch
        }
    }

    BackHandler {
        onBack()
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("accounts_dualspace_screen"),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header matching SpaceSandboxScreen & other screens (Back button, Title, Actions)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = "Back to Clone Space" }
                            .testTag("accounts_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Clone Space"
                        )
                    }

                    DualSpaceOverlappingCirclesIcon(
                        boxSize = 38.dp,
                        cornerRadius = 11.dp
                    )

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Accounts",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = DualSpacePurple.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = "Dual Space",
                                    color = DualSpacePurple,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Isolated second-account work profile",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { showHowItWorksSheet = true },
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = "How Dual Space works" }
                            .testTag("accounts_how_it_works_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.HelpOutline,
                            contentDescription = "How it works",
                            tint = DualSpacePurple
                        )
                    }

                    IconButton(
                        onClick = {
                            reloadTrigger++
                            Toast.makeText(context, "Refreshing installed apps...", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = "Refresh apps list" }
                            .testTag("accounts_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                }
            }

            // Friendly Error / Notice Banner
            AnimatedVisibility(
                visible = friendlyError != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                friendlyError?.let { msg ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("accounts_friendly_notice_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { friendlyError = null },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Dismiss notice",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Profile Status & Provisioning Card
            DualSpaceProfileBanner(
                status = status,
                clonedCount = clonedCount,
                onCreateDualSpace = { handleStartProvisioning() },
                onEnablePreviewMode = {
                    DualSpaceBridge.setPreviewProfileActive(context, true)
                    status = DualSpaceBridge.getStatus(context)
                    Toast.makeText(context, "Dual Space profile ready!", Toast.LENGTH_SHORT).show()
                    reloadTrigger++
                },
                onOpenHowItWorks = { showHowItWorksSheet = true }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Search Bar + Filter Row
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "Search installed apps or packages...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Search apps"
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier
                                .size(48.dp)
                                .semantics { contentDescription = "Clear search query" }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Clear search"
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DualSpacePurple,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("accounts_search_field")
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = activeFilter == AccountsFilter.ALL,
                        onClick = { activeFilter = AccountsFilter.ALL },
                        label = { Text("All Apps (${apps.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DualSpacePurple.copy(alpha = 0.16f),
                            selectedLabelColor = DualSpacePurple
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("accounts_filter_all")
                    )

                    FilterChip(
                        selected = activeFilter == AccountsFilter.CLONED,
                        onClick = { activeFilter = AccountsFilter.CLONED },
                        label = { Text("Cloned ($clonedCount)") },
                        leadingIcon = if (activeFilter == AccountsFilter.CLONED) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = DualSpacePurple,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DualSpacePurple.copy(alpha = 0.16f),
                            selectedLabelColor = DualSpacePurple
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("accounts_filter_cloned")
                    )
                }

                TextButton(
                    onClick = { showHowItWorksSheet = true },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(
                        text = "How it works",
                        color = DualSpacePurple,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Content: Skeleton Loader vs Searchable App Grid vs Empty State
            Crossfade(
                targetState = isLoadingApps,
                animationSpec = tween(durationMillis = 260),
                label = "accounts_grid_crossfade",
                modifier = Modifier.weight(1f)
            ) { loading ->
                if (loading) {
                    AccountsSkeletonGrid()
                } else if (filteredApps.isEmpty()) {
                    AccountsEmptyState(
                        searchQuery = searchQuery,
                        isClonedFilter = activeFilter == AccountsFilter.CLONED,
                        onResetFilters = {
                            searchQuery = ""
                            activeFilter = AccountsFilter.ALL
                        }
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 156.dp),
                        contentPadding = PaddingValues(bottom = 24.dp, top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("accounts_apps_grid")
                    ) {
                        items(
                            items = filteredApps,
                            key = { it.pkg }
                        ) { app ->
                            DualSpaceAppGridCard(
                                app = app,
                                onCloneClick = {
                                    appPendingCloneConfirm = app
                                },
                                onRemoveClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    appPendingRemoval = app
                                }
                            )
                        }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = "Tap an app to clone it into Dual Space • Tap Cloned or long-press to remove",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp, bottom = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Clone confirmation dialog (matches native DualSpace MainActivity behavior + 1-tap clarity)
    appPendingCloneConfirm?.let { app ->
        AlertDialog(
            onDismissRequest = { appPendingCloneConfirm = null },
            icon = {
                DualSpaceOverlappingCirclesIcon(boxSize = 44.dp, cornerRadius = 12.dp)
            },
            title = {
                Text(
                    text = "Clone ${app.label}?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This creates an isolated second copy of ${app.label} inside your Dual Space profile with completely separate login and app data."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = app
                        appPendingCloneConfirm = null
                        performClone(target)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DualSpacePurple),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("confirm_clone_button")
                ) {
                    Text("Clone App", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { appPendingCloneConfirm = null },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Removal confirmation dialog (Required by prompt: "confirm dialogs for removal")
    appPendingRemoval?.let { app ->
        AlertDialog(
            onDismissRequest = { appPendingRemoval = null },
            icon = {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Remove ${app.label} Clone?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Remove the cloned copy of ${app.label} from Dual Space? Your primary ${app.label} app and its data on your main profile will not be affected."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = app
                        appPendingRemoval = null
                        performRemoveClone(target)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("confirm_remove_clone_button")
                ) {
                    Text("Remove Clone")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { appPendingRemoval = null },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // "How it works" bottom sheet explaining that clones have separate data
    if (showHowItWorksSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showHowItWorksSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DualSpaceOverlappingCirclesIcon(boxSize = 48.dp, cornerRadius = 14.dp)
                    Column {
                        Text(
                            text = "How Dual Space Works",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Run two accounts of any app simultaneously",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HowItWorksFeatureRow(
                    icon = Icons.Filled.WorkOutline,
                    title = "1. Isolated Android Work Profile",
                    description = "Dual Space uses Android's managed work profile API (DevicePolicyManager) to provision a secure, isolated container on your device."
                )

                HowItWorksFeatureRow(
                    icon = Icons.Filled.Security,
                    title = "2. Completely Separate App Data",
                    description = "Apps cloned into Dual Space have their own independent storage, cache, permissions, and sign-in sessions. Signing into a second account never touches your primary account."
                )

                HowItWorksFeatureRow(
                    icon = Icons.Filled.VerifiedUser,
                    title = "3. Briefcase Badge & Easy Removal",
                    description = "Cloned apps are marked with a \"Cloned\" badge here and appear in your launcher with a briefcase badge. You can clone or remove apps at any time."
                )

                Button(
                    onClick = { showHowItWorksSheet = false },
                    colors = ButtonDefaults.buttonColors(containerColor = DualSpacePurple),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("close_how_it_works_sheet_btn")
                ) {
                    Text(
                        text = "Got it",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun DualSpaceProfileBanner(
    status: DualSpaceStatus,
    clonedCount: Int,
    onCreateDualSpace: () -> Unit,
    onEnablePreviewMode: () -> Unit,
    onOpenHowItWorks: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = DualSpacePurple.copy(alpha = 0.28f),
                shape = RoundedCornerShape(20.dp)
            )
            .testTag("dualspace_profile_status_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when {
                status.inProfile -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = DualSpacePurple.copy(alpha = 0.18f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.WorkOutline,
                                    contentDescription = null,
                                    tint = DualSpacePurple,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Running Inside Dual Space Profile",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Cloned apps appear in your launcher with a briefcase badge. Open the main profile to clone additional apps.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                status.hasProfile -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF10B981).copy(alpha = 0.16f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Dual Space Profile Ready",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (!status.hasHardwareProfile && status.isPreviewProfileActive) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = DualSpacePurple.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "Preview Mode",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = DualSpacePurple,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "$clonedCount app(s) cloned with isolated account data",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (!status.hasHardwareProfile) {
                            OutlinedButton(
                                onClick = onCreateDualSpace,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("provision_work_profile_btn")
                            ) {
                                Text(
                                    text = "Setup Work Profile",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                else -> {
                    Text(
                        text = "Dual Space creates an isolated Android work profile. Apps cloned into it have separate data, so you can log into a second account.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onCreateDualSpace,
                            colors = ButtonDefaults.buttonColors(containerColor = DualSpacePurple),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("create_dual_space_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.WorkOutline,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Create Dual Space",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        FilledTonalButton(
                            onClick = onEnablePreviewMode,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("preview_dual_space_button")
                        ) {
                            Text(
                                text = "Preview Mode",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DualSpaceAppGridCard(
    app: DualSpaceAppItem,
    onCloneClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    val accentColor = remember(app.accentHex) { parseHexColor(app.accentHex) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (app.isCloned) {
                DualSpacePurple.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(
                width = if (app.isCloned) 1.5.dp else 1.dp,
                color = if (app.isCloned) {
                    DualSpacePurple.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                },
                shape = RoundedCornerShape(20.dp)
            )
            .combinedClickable(
                onClick = {
                    if (app.isCloned) onRemoveClick() else onCloneClick()
                },
                onLongClick = {
                    if (app.isCloned) onRemoveClick() else onCloneClick()
                }
            )
            .semantics {
                contentDescription = if (app.isCloned) {
                    "${app.label}, Cloned in Dual Space. Tap to remove clone."
                } else {
                    "${app.label}. Tap to clone into Dual Space."
                }
            }
            .testTag("accounts_app_card_${app.pkg}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Real app icon or polished fallback icon in center
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(58.dp)
                        .align(Alignment.Center)
                ) {
                    if (app.icon != null) {
                        AppIconImage(
                            iconDrawable = app.icon,
                            fallbackName = app.label,
                            modifier = Modifier.size(54.dp)
                        )
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(accentColor.copy(alpha = 0.88f))
                        ) {
                            Text(
                                text = app.label.take(1).uppercase(),
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                // "Cloned" badge at top-end when cloned
                if (app.isCloned) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = DualSpacePurple,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .testTag("cloned_badge_${app.pkg}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Cloned badge",
                                tint = Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "Cloned",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Text(
                text = app.pkg,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )

            if (app.isCloned) {
                OutlinedButton(
                    onClick = onRemoveClick,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("remove_clone_btn_${app.pkg}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Remove",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                FilledTonalButton(
                    onClick = onCloneClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = DualSpacePurple.copy(alpha = 0.14f),
                        contentColor = DualSpacePurple
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("clone_app_btn_${app.pkg}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.AddCircleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Clone",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountsSkeletonGrid() {
    val transition = rememberInfiniteTransition(label = "accounts_skeleton_pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.32f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton_alpha"
    )

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 156.dp),
        contentPadding = PaddingValues(bottom = 24.dp, top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("accounts_skeleton_loader")
    ) {
        items(6) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha * 0.6f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.35f))
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .width(92.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.35f))
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .width(116.dp)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.22f))
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(DualSpacePurple.copy(alpha = alpha * 0.25f))
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountsEmptyState(
    searchQuery: String,
    isClonedFilter: Boolean,
    onResetFilters: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("accounts_empty_state"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        DualSpaceOverlappingCirclesIcon(
            boxSize = 64.dp,
            cornerRadius = 18.dp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = when {
                searchQuery.isNotBlank() -> "No matching apps found"
                isClonedFilter -> "No cloned apps yet"
                else -> "No apps available"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = when {
                searchQuery.isNotBlank() ->
                    "No installed apps match \"$searchQuery\". Try a different app name or package."
                isClonedFilter ->
                    "Tap any app in the All Apps view to create an isolated Dual Space clone with separate account data."
                else ->
                    "Refresh the list to scan launchable apps on your device."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        FilledTonalButton(
            onClick = onResetFilters,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("accounts_reset_filters_button")
        ) {
            Text(
                text = if (searchQuery.isNotBlank()) "Clear Search" else "Show All Apps",
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun HowItWorksFeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = DualSpacePurple.copy(alpha = 0.14f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = DualSpacePurple,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
