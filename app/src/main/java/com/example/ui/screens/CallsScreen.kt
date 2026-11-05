package com.example.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.CallLogEntry
import com.example.data.models.CallLogGroup
import com.example.data.models.CallType
import com.example.data.models.groupConsecutiveCalls
import com.example.telecom.PriorityContactManager
import com.example.telecom.SimCardInfo
import com.example.telecom.TelecomHelper
import com.example.ui.components.SimSelectionDialog
import com.example.ui.viewmodel.CallFilter

data class FavoriteContact(
    val id: String,
    val name: String,
    val number: String,
    val color: Color,
    val simLabel: String? = null
)

@Composable
fun CallsScreen(
    callLogs: List<CallLogEntry>,
    filter: CallFilter,
    onFilterChange: (CallFilter) -> Unit,
    onDeleteCall: (CallLogEntry) -> Unit,
    onClearAll: () -> Unit,
    onClearMissedCalls: () -> Unit = {},
    onMarkMissedSeen: () -> Unit = {},
    onStartSms: (String, String?) -> Unit,
    onOpenContacts: () -> Unit,
    onStartCall: (String, String?) -> Unit,
    onStartCallWithSim: (String, String?, SimCardInfo?) -> Unit = { num, name, _ -> onStartCall(num, name) },
    onWhatsAppCall: (String, String?) -> Unit = { _, _ -> },
    onSimulateIncomingCall: () -> Unit = {},
    onSpeedBoost: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCallForHistory by remember { mutableStateOf<CallLogEntry?>(null) }
    var expandedLogId by remember { mutableStateOf<Long?>(null) }
    var favoritesCollapsed by remember { mutableStateOf(false) }
    var callNumberTarget by remember { mutableStateOf<String?>(null) }
    var callNameTarget by remember { mutableStateOf<String?>(null) }
    var availableSims by remember { mutableStateOf<List<SimCardInfo>>(emptyList()) }
    var selectedCallIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    val isSelectionMode = selectedCallIds.isNotEmpty()

    BackHandler(enabled = isSelectionMode) {
        selectedCallIds = emptySet()
    }

    LaunchedEffect(Unit) {
        availableSims = TelecomHelper.getAvailableSimCards(context)
    }

    val simCarrierMap = remember(availableSims) {
        availableSims.associate { it.subscriptionId to it.carrierName }
    }
    val defaultCarrierName = remember(availableSims) {
        availableSims.firstOrNull()?.carrierName
    }

    LaunchedEffect(Unit, callLogs.size) {
        onMarkMissedSeen()
    }

    fun placeCall(number: String, name: String? = null) {
        val clean = number.trim()
        if (clean.isBlank()) return
        if (TelecomHelper.isUssdCode(clean)) {
            val sims = TelecomHelper.getAvailableSimCards(context)
            if (sims.size > 1) {
                availableSims = sims
                callNumberTarget = clean
                callNameTarget = name
                return
            }
            onStartCallWithSim(clean, name, sims.firstOrNull())
            return
        }
        if (PriorityContactManager.isPriorityNumber(clean, context)) {
            val preferredSim = TelecomHelper.getPreferredSimForPriorityCall(context)
            onStartCallWithSim(clean, name, preferredSim)
            return
        }
        val sims = TelecomHelper.getAvailableSimCards(context)
        if (sims.size > 1 && callNumberTarget == null) {
            availableSims = sims
            callNumberTarget = clean
            callNameTarget = name
            return
        }
        onStartCallWithSim(clean, name, sims.firstOrNull())
    }

    // Favorites derived from recent call logs (empty if none)
    val defaultFavorites = remember(callLogs) {
        val uniqueFromLogs = callLogs.map { it.displayName to it.number }.distinctBy { it.second }.take(6)
        val palette = listOf(
            Color(0xFFE65100), Color(0xFF2E7D32), Color(0xFF1B5E20),
            Color(0xFF00838F), Color(0xFFC2185B), Color(0xFF512DA8)
        )
        uniqueFromLogs.mapIndexed { i, (name, num) ->
            FavoriteContact("fav_$i", name, num, palette[i % palette.size], "SIM")
        }
    }

    // Contact History Screen (Screenshot 9)
    if (selectedCallForHistory != null) {
        ContactHistoryView(
            contactCall = selectedCallForHistory!!,
            allLogs = callLogs,
            availableSims = availableSims,
            onBack = { selectedCallForHistory = null },
            onCall = { num, name -> placeCall(num, name) },
            onMessage = { num, name -> onStartSms(num, name) },
            modifier = modifier
        )
        return
    }

    // Filter call logs
    val filteredLogs = remember(callLogs, filter, searchQuery) {
        val base = when (filter) {
            CallFilter.ALL -> callLogs
            CallFilter.MISSED -> callLogs.filter { it.type == CallType.MISSED || it.type == CallType.REJECTED }
            CallFilter.INCOMING -> callLogs.filter { it.type == CallType.INCOMING }
            CallFilter.OUTGOING -> callLogs.filter { it.type == CallType.OUTGOING }
        }
        if (searchQuery.isBlank()) base
        else {
            val q = searchQuery.trim().lowercase()
            base.filter { it.displayName.lowercase().contains(q) || it.number.contains(q) }
        }
    }

    // Group logs into consecutive 3-hour call bundles, then into Today, Yesterday, Older
    val groupedLogs = remember(filteredLogs) {
        val bundledGroups = groupConsecutiveCalls(filteredLogs, maxWindowMs = 3 * 60 * 60 * 1000L)
        val today = mutableListOf<CallLogGroup>()
        val yesterday = mutableListOf<CallLogGroup>()
        val older = mutableListOf<CallLogGroup>()
        val now = System.currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L

        for (group in bundledGroups) {
            val diff: Long = now - group.latestEntry.date
            when {
                diff < oneDayMs -> today.add(group)
                diff < 2 * oneDayMs -> yesterday.add(group)
                else -> older.add(group)
            }
        }
        listOf(
            "Today" to today,
            "Yesterday" to yesterday,
            "Older" to older
        ).filter { it.second.isNotEmpty() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121214))
            .testTag("calls_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Top Bar: Contextual Selection Bar or Search Bar
            if (isSelectionMode) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = Color(0xFF232529)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { selectedCallIds = emptySet() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Cancel selection",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${selectedCallIds.size} selected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    val allIds = filteredLogs.map { it.id }.toSet()
                                    selectedCallIds = if (selectedCallIds.size == allIds.size) emptySet() else allIds
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SelectAll,
                                    contentDescription = "Select all",
                                    tint = Color(0xFFB0B0B4),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = { showDeleteConfirmDialog = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Delete selected calls",
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = Color(0xFF232529)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onOpenContacts,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = "Menu / Contacts",
                                tint = Color(0xFFB0B0B4),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    text = "Search contacts",
                                    color = Color(0xFF8E8E93),
                                    fontSize = 16.sp
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                cursorColor = Color.White,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Clear search",
                                    tint = Color(0xFF8E8E93),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Horizontal Filter Chips: All, Missed, Contacts, Non-spam, Spam (Screenshot 3)
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    CallFilter.ALL to "All",
                    CallFilter.MISSED to "Missed",
                    CallFilter.INCOMING to "Contacts",
                    CallFilter.OUTGOING to "Non-spam"
                ).forEach { (f, label) ->
                    val isSelected = filter == f
                    item(key = label) {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { onFilterChange(f) },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) Color(0xFF38383E) else Color(0xFF1E1E22)
                        ) {
                            Text(
                                text = label,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else Color(0xFFB0B0B4),
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Call Log & Favorites List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // 3. Favorites Section (Screenshot 3)
                if (searchQuery.isBlank() && defaultFavorites.isNotEmpty()) {
                    item(key = "favorites_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { favoritesCollapsed = !favoritesCollapsed }
                            ) {
                                Text(
                                    text = "Favorites",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = if (favoritesCollapsed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            TextButton(onClick = onOpenContacts) {
                                Text(
                                    text = "View contacts",
                                    color = Color(0xFFB0B0B4),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    if (!favoritesCollapsed) {
                        item(key = "favorites_row") {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(defaultFavorites, key = { it.id }) { fav ->
                                    FavoriteContactItem(
                                        favorite = fav,
                                        onClick = { placeCall(fav.number, fav.name) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }

                // 4. Recents Call Log grouped by Date (Screenshot 3 & 7)
                if (groupedLogs.isEmpty()) {
                    item(key = "empty_recents") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No calls found",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFF8E8E93)
                            )
                        }
                    }
                } else {
                    groupedLogs.forEach { (sectionTitle, groups) ->
                        item(key = "header_$sectionTitle") {
                            Text(
                                text = sectionTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFB0B0B4),
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                        }

                        itemsIndexed(
                            items = groups,
                            key = { idx, group -> "call_grp_${group.latestEntry.id}_${group.latestEntry.number}_${group.count}_$idx" }
                        ) { _, group ->
                            val entry = group.latestEntry
                            val isExpanded = remember(expandedLogId, entry.id) { expandedLogId == entry.id }
                            val isSelected = remember(selectedCallIds, entry.id) { entry.id in selectedCallIds }
                            
                            RecentsCallCard(
                                entry = entry,
                                callCount = group.count,
                                isExpanded = isExpanded,
                                isSelected = isSelected,
                                isSelectionMode = isSelectionMode,
                                availableSims = availableSims,
                                simCarrierMap = simCarrierMap,
                                defaultCarrierName = defaultCarrierName,
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedCallIds = if (isSelected) {
                                            selectedCallIds - entry.id
                                        } else {
                                            selectedCallIds + entry.id
                                        }
                                    } else {
                                        expandedLogId = if (isExpanded) null else entry.id
                                    }
                                },
                                onLongClick = {
                                    selectedCallIds = if (isSelected) {
                                        selectedCallIds - entry.id
                                    } else {
                                        selectedCallIds + entry.id
                                    }
                                },
                                onCallClick = { placeCall(entry.number, entry.displayName) },
                                onMessageClick = { onStartSms(entry.number, entry.displayName) },
                                onWhatsAppClick = { onWhatsAppCall(entry.number, entry.displayName) },
                                onHistoryClick = { selectedCallForHistory = entry },
                                onDeleteClick = {
                                    group.entries.forEach { onDeleteCall(it) }
                                }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }

    // Delete Multiple Selected Calls Confirmation Dialog
    if (showDeleteConfirmDialog && selectedCallIds.isNotEmpty()) {
        val count = selectedCallIds.size
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Call History", color = Color.White) },
            text = {
                Text(
                    "Delete $count selected ${if (count > 1) "calls" else "call"} from call history?",
                    color = Color(0xFFD0D0D4)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDelete = callLogs.filter { it.id in selectedCallIds }
                        toDelete.forEach { onDeleteCall(it) }
                        selectedCallIds = emptySet()
                        showDeleteConfirmDialog = false
                    }
                ) {
                    Text("Delete", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = Color(0xFFB0B0B4))
                }
            },
            containerColor = Color(0xFF232529)
        )
    }

    // Dual-SIM Selector
    callNumberTarget?.let { num ->
        SimSelectionDialog(
            phoneNumber = num,
            simCards = availableSims,
            onSimSelected = { sim ->
                callNumberTarget = null
                onStartCallWithSim(num, callNameTarget, sim)
            },
            onDismiss = { callNumberTarget = null }
        )
    }
}

/**
 * Favorite Contact Avatar with bottom-right phone badge (Screenshot 3)
 */
@Composable
private fun FavoriteContactItem(
    favorite: FavoriteContact,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(68.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.size(54.dp),
                shape = CircleShape,
                color = favorite.color
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = favorite.name.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                }
            }

            // Small black circular badge with phone icon at bottom-right
            Surface(
                modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.BottomEnd),
                shape = CircleShape,
                color = Color.Black
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Call,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = favorite.name,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Recents Call Card matching Screenshot 3, expandable inline to show Message & History (Screenshot 7)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentsCallCard(
    entry: CallLogEntry,
    callCount: Int = 1,
    isExpanded: Boolean,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    availableSims: List<SimCardInfo>,
    simCarrierMap: Map<Int, String> = emptyMap(),
    defaultCarrierName: String? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCallClick: () -> Unit,
    onMessageClick: () -> Unit,
    onWhatsAppClick: () -> Unit = {},
    onHistoryClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val avatarColor = remember(entry.displayName) {
        val colors = listOf(
            Color(0xFFC62828), Color(0xFF2E7D32), Color(0xFF1565C0),
            Color(0xFFE65100), Color(0xFF6A1B9A), Color(0xFF00838F)
        )
        val hash = kotlin.math.abs(entry.displayName.hashCode())
        colors[hash % colors.size]
    }

    val titleText = remember(entry.displayName, callCount) {
        if (callCount > 1) "${entry.displayName} ($callCount)" else entry.displayName
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("call_item_${entry.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF2E2E36) else Color(0xFF1E1E22)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left avatar + details
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (isSelectionMode) {
                        Icon(
                            imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                            contentDescription = if (isSelected) "Selected" else "Not selected",
                            tint = if (isSelected) Color(0xFFFF4081) else Color(0xFF8E8E93),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Surface(
                        modifier = Modifier.size(46.dp),
                        shape = CircleShape,
                        color = avatarColor
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = entry.displayName.take(1).uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        }
                    }

                    Column {
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = when (entry.type) {
                                    CallType.OUTGOING -> Icons.Filled.CallMade
                                    CallType.MISSED, CallType.REJECTED -> Icons.Filled.CallMissed
                                    else -> Icons.Filled.CallReceived
                                },
                                contentDescription = null,
                                tint = if (entry.type == CallType.MISSED || entry.type == CallType.REJECTED) Color(0xFFFF5252) else Color(0xFF9E9E9E),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Mobile • ${entry.formattedTimeOfDay}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFB0B0B4)
                            )
                        }

                        // SIM Name in pink (Screenshot 3 & 7) - fast O(1) carrier lookup
                        val simName = remember(entry.simSlot, entry.simName, simCarrierMap, defaultCarrierName) {
                            entry.simName?.takeIf { it.isNotBlank() }
                                ?: simCarrierMap[entry.simSlot]
                                ?: defaultCarrierName
                                ?: "SIM ${entry.simSlot + 1}"
                        }
                        Text(
                            text = simName,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFF4081),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // WhatsApp call icon with smaller thin green circle (16dp size)
                    IconButton(
                        onClick = onWhatsAppClick,
                        modifier = Modifier
                            .size(16.dp)
                            .border(width = 1.dp, color = Color(0xFF25D366), shape = CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Phone,
                            contentDescription = "WhatsApp Call",
                            tint = Color(0xFF25D366),
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    // Normal call icon with smaller thin white circle (16dp size)
                    IconButton(
                        onClick = onCallClick,
                        modifier = Modifier
                            .size(16.dp)
                            .border(width = 1.dp, color = Color.White, shape = CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Call,
                            contentDescription = "Call",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // Inline Expansion (Screenshot 7): Message & History Buttons & Delete option
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onMessageClick),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF28282D)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Message,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Message",
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onHistoryClick),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF28282D)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.History,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "History",
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onDeleteClick),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF382326)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = null,
                                tint = Color(0xFFFF6B6B),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Delete from history",
                                color = Color(0xFFFF6B6B),
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Contact History Screen (Screenshot 9)
 */
@Composable
fun ContactHistoryView(
    contactCall: CallLogEntry,
    allLogs: List<CallLogEntry>,
    availableSims: List<SimCardInfo>,
    onBack: () -> Unit,
    onCall: (String, String?) -> Unit,
    onMessage: (String, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val contactLogs = remember(contactCall, allLogs) {
        val cleanTarget = contactCall.number.filter { it.isDigit() }
        allLogs.filter { log ->
            val clean = log.number.filter { it.isDigit() }
            clean == cleanTarget || (clean.length >= 7 && cleanTarget.endsWith(clean.takeLast(7)))
        }.sortedByDescending { it.date }
    }

    val groupedContactLogs = remember(contactLogs) {
        val today = mutableListOf<CallLogEntry>()
        val older = mutableListOf<CallLogEntry>()
        val now = System.currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L

        for (log in contactLogs) {
            val diff: Long = now - log.date
            if (diff < oneDayMs) {
                today.add(log)
            } else {
                older.add(log)
            }
        }
        listOf(
            "Today" to today,
            "Older" to older
        ).filter { it.second.isNotEmpty() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121214))
            .testTag("contact_history_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top App Bar (Screenshot 9)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = Color(0xFFC62828)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = contactCall.displayName.take(1).uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    }

                    Column {
                        Text(
                            text = contactCall.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Mobile • ${contactCall.number}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF9E9E9E)
                        )
                    }
                }

                IconButton(onClick = { /* More options */ }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Options",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Grouped History Call Items (Screenshot 9)
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                groupedContactLogs.forEach { (section, logs) ->
                    item(key = "hist_sec_$section") {
                        Text(
                            text = section,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFB0B0B4),
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }

                    items(logs, key = { "hist_log_${it.id}_${it.date}" }) { log ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E22))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = when (log.type) {
                                            CallType.OUTGOING -> Icons.Filled.CallMade
                                            CallType.MISSED, CallType.REJECTED -> Icons.Filled.CallMissed
                                            else -> Icons.Filled.CallReceived
                                        },
                                        contentDescription = null,
                                        tint = if (log.type == CallType.MISSED || log.type == CallType.REJECTED) Color(0xFFFF5252) else Color(0xFF9E9E9E),
                                        modifier = Modifier.size(18.dp)
                                    )

                                    Column {
                                        Text(
                                            text = when (log.type) {
                                                CallType.OUTGOING -> "Outgoing call"
                                                CallType.MISSED, CallType.REJECTED -> "Missed call"
                                                else -> "Incoming call"
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = log.formattedTimeOfDay,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF9E9E9E)
                                            )
                                            val simName = remember(log.simSlot, log.simName, availableSims) {
                                                log.simName?.takeIf { it.isNotBlank() }
                                                    ?: availableSims.find { it.subscriptionId == log.simSlot }?.carrierName
                                                    ?: availableSims.firstOrNull()?.carrierName
                                                    ?: "SIM ${log.simSlot + 1}"
                                            }
                                            Text(
                                                text = simName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFFFF4081),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = if (log.durationSeconds > 0) log.formattedDuration else "Missed",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF9E9E9E)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Bar in Contact History Screen (Screenshot 9)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = Color(0xFF18181C),
            tonalElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Call Pill Button (Green)
                Surface(
                    modifier = Modifier
                        .width(130.dp)
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .clickable { onCall(contactCall.number, contactCall.displayName) },
                    shape = RoundedCornerShape(26.dp),
                    color = Color(0xFF4CAF50)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Call,
                            contentDescription = "Call",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Message Pill Button
                Surface(
                    modifier = Modifier
                        .width(130.dp)
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .clickable { onMessage(contactCall.number, contactCall.displayName) },
                    shape = RoundedCornerShape(26.dp),
                    color = Color(0xFF28282D)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Message,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Message",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
