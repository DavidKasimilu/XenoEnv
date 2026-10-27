package com.example.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.models.CallLogEntry
import com.example.data.models.CallLogGroup
import com.example.data.models.CallType
import com.example.data.models.groupConsecutiveCalls
import com.example.telecom.PriorityContactManager
import com.example.telecom.SimCardInfo
import com.example.telecom.TelecomHelper
import com.example.ui.components.ContactAvatar
import com.example.ui.components.SimSelectionDialog
import com.example.ui.theme.MissedRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.viewmodel.CallFilter

@OptIn(ExperimentalMaterial3Api::class)
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
    onSimulateIncomingCall: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var selectedGroup by remember { mutableStateOf<CallLogGroup?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    var callNumberTarget by remember { mutableStateOf<String?>(null) }
    var callNameTarget by remember { mutableStateOf<String?>(null) }
    var availableSims by remember { mutableStateOf<List<SimCardInfo>>(emptyList()) }

    LaunchedEffect(Unit, callLogs.size) {
        onMarkMissedSeen()
    }

    fun placeCall(number: String, name: String? = null) {
        val clean = number.trim()
        if (clean.isBlank()) return
        if (TelecomHelper.isUssdCode(clean)) {
            onStartCallWithSim(clean, name, null)
            return
        }
        // Priority Contacts bypass Dual-SIM selection prompt for immediate zero-delay dialing
        if (PriorityContactManager.isPriorityNumber(clean, context)) {
            val resolvedName = name?.takeIf {
                it.isNotBlank() && it != clean && !PriorityContactManager.isSyntheticPriorityName(it)
            }
            val preferredSim = TelecomHelper.getPreferredSimForPriorityCall(context)
            onStartCallWithSim(clean, resolvedName, preferredSim)
            return
        }
        val sims = TelecomHelper.getSimChoicesForCall(context)
        availableSims = sims
        callNameTarget = name?.takeIf { it.isNotBlank() && it != clean }
        callNumberTarget = clean
    }

    fun placeCallWithSpecificSim(number: String, name: String?, sim: SimCardInfo) {
        val clean = number.trim()
        if (clean.isBlank()) return
        val resolvedName = name?.takeIf { it.isNotBlank() && it != clean }
        onStartCallWithSim(clean, resolvedName, sim)
    }

    val filteredLogs = remember(callLogs, filter) {
        when (filter) {
            CallFilter.ALL -> callLogs
            CallFilter.MISSED -> callLogs.filter { it.type == CallType.MISSED || it.type == CallType.REJECTED }
            CallFilter.INCOMING -> callLogs.filter { it.type == CallType.INCOMING }
            CallFilter.OUTGOING -> callLogs.filter { it.type == CallType.OUTGOING }
        }
    }

    // Bundle consecutive calls to the same person/number within a 3-hour window
    val groupedLogs = remember(filteredLogs) {
        groupConsecutiveCalls(filteredLogs)
    }

    val (missedCount, incomingCount, outgoingCount) = remember(callLogs) {
        var missed = 0
        var incoming = 0
        var outgoing = 0
        for (entry in callLogs) {
            when (entry.type) {
                CallType.MISSED, CallType.REJECTED -> missed++
                CallType.INCOMING -> incoming++
                CallType.OUTGOING -> outgoing++
                else -> {}
            }
        }
        Triple(missed, incoming, outgoing)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("calls_screen")
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
        // Header & Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Call History",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (groupedLogs.size != filteredLogs.size) {
                        "${groupedLogs.size} entries (${filteredLogs.size} recorded calls)"
                    } else {
                        "${filteredLogs.size} recorded calls"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row {
                IconButton(
                    onClick = onOpenContacts,
                    modifier = Modifier.testTag("open_contacts_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.People,
                        contentDescription = "Open Contacts",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (filteredLogs.isNotEmpty()) {
                    IconButton(
                        onClick = { showClearDialog = true },
                        modifier = Modifier.testTag("clear_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ClearAll,
                            contentDescription = if (filter == CallFilter.MISSED) "Clear missed calls" else "Clear call history",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Filter chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "filter_all") {
                FilterChip(
                    selected = filter == CallFilter.ALL,
                    onClick = { onFilterChange(CallFilter.ALL) },
                    label = { Text("All (${callLogs.size})") },
                    colors = FilterChipDefaults.filterChipColors()
                )
            }
            item(key = "filter_missed") {
                FilterChip(
                    selected = filter == CallFilter.MISSED,
                    onClick = { onFilterChange(CallFilter.MISSED) },
                    label = { Text("Missed ($missedCount)") },
                    colors = FilterChipDefaults.filterChipColors()
                )
            }
            item(key = "filter_incoming") {
                FilterChip(
                    selected = filter == CallFilter.INCOMING,
                    onClick = { onFilterChange(CallFilter.INCOMING) },
                    label = { Text("Incoming ($incomingCount)") },
                    colors = FilterChipDefaults.filterChipColors()
                )
            }
            item(key = "filter_outgoing") {
                FilterChip(
                    selected = filter == CallFilter.OUTGOING,
                    onClick = { onFilterChange(CallFilter.OUTGOING) },
                    label = { Text("Outgoing ($outgoingCount)") },
                    colors = FilterChipDefaults.filterChipColors()
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (groupedLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Phone,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No calls found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "New incoming and outgoing calls will appear here",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(
                    items = groupedLogs,
                    key = { index, group ->
                        val head = group.latestEntry
                        "call_group_${head.id}_${head.number}_${group.count}_$index"
                    },
                    contentType = { _, _ -> "call_log_row" }
                ) { _, group ->
                    val entry = group.latestEntry
                    CallLogItem(
                        entry = entry,
                        callCount = group.count,
                        oldestTimeOfDay = if (group.count > 1) group.oldestEntry.formattedTimeOfDay else null,
                        onClick = { selectedGroup = group },
                        onCallClick = { placeCall(entry.number, entry.displayName) }
                    )
                }
            }
        }
    }

    // Call Details Bottom Sheet (shows individual call times when tapped)
    selectedGroup?.let { group ->
        val entry = group.latestEntry
        ModalBottomSheet(
            onDismissRequest = { selectedGroup = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ContactAvatar(nameOrNumber = entry.displayName, size = 56.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (group.count > 1) "${entry.displayName} (${group.count})" else entry.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = entry.number,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (group.count > 1) {
                                "${group.count} consecutive calls • ${group.oldestEntry.formattedTimeOfDay} – ${entry.formattedTimeOfDay}"
                            } else {
                                "${entry.formattedDayLabel}, ${entry.formattedTimeOfDay} • ${entry.formattedDuration}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Call (Choose SIM)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .size(50.dp)
                                .clickable {
                                    val num = entry.number
                                    val name = entry.displayName
                                    selectedGroup = null
                                    placeCall(num, name)
                                }
                                .testTag("sheet_call_choose_sim_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Call,
                                    contentDescription = "Call and choose SIM",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Call", style = MaterialTheme.typography.labelSmall)
                    }

                    // SMS
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier
                                .size(50.dp)
                                .clickable {
                                    onStartSms(entry.number, entry.cachedName)
                                    selectedGroup = null
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Message,
                                    contentDescription = "SMS",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Message", style = MaterialTheme.typography.labelSmall)
                    }

                    // Copy Number
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(50.dp)
                                .clickable {
                                    clipboardManager.setText(AnnotatedString(entry.number))
                                    selectedGroup = null
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = "Copy",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Copy", style = MaterialTheme.typography.labelSmall)
                    }

                    // Delete Call Record(s)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier
                                .size(50.dp)
                                .clickable {
                                    group.entries.forEach { callToDelete ->
                                        onDeleteCall(callToDelete)
                                    }
                                    selectedGroup = null
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (group.count > 1) "Delete (${group.count})" else "Delete",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Quick SIM Call Buttons in Details Sheet
                val sheetSims = remember { TelecomHelper.getSimChoicesForCall(context) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    sheetSims.forEachIndexed { idx, sim ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    val num = entry.number
                                    val name = entry.displayName
                                    selectedGroup = null
                                    placeCallWithSpecificSim(num, name, sim)
                                }
                                .testTag("sheet_quick_sim_${idx + 1}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SimCard,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Call SIM ${sim.subscriptionId + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // Individual Call Times Breakdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (group.count > 1) "Call Times (${group.count})" else "Call Time",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(
                        items = group.entries,
                        key = { idx, item -> "sheet_call_time_${item.id}_${item.date}_$idx" }
                    ) { idx, callItem ->
                        val (typeIcon, typeTint) = when (callItem.type) {
                            CallType.INCOMING -> Icons.Filled.CallReceived to SuccessGreen
                            CallType.OUTGOING -> Icons.Filled.CallMade to MaterialTheme.colorScheme.primary
                            CallType.MISSED, CallType.REJECTED -> Icons.Filled.CallMissed to MissedRed
                            else -> Icons.Filled.Call to MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("call_time_row_$idx")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = typeTint.copy(alpha = 0.14f),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = typeIcon,
                                            contentDescription = callItem.typeLabel,
                                            tint = typeTint,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = callItem.formattedTimeOfDay,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (callItem.type == CallType.MISSED) {
                                                MissedRed
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                        Text(
                                            text = "• ${callItem.formattedDayLabel}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "${callItem.typeLabel} • ${callItem.formattedDuration}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (group.count > 1) {
                                    IconButton(
                                        onClick = {
                                            onDeleteCall(callItem)
                                            val remaining = group.entries.filterNot {
                                                it.id == callItem.id && it.date == callItem.date
                                            }
                                            selectedGroup = if (remaining.isEmpty()) {
                                                null
                                            } else {
                                                CallLogGroup(remaining)
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = "Delete single call record",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (showClearDialog) {
        val hasMissedCalls = callLogs.any { it.type == CallType.MISSED || it.type == CallType.REJECTED }
        val isMissedFilter = filter == CallFilter.MISSED
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text(if (isMissedFilter) "Clear Missed Calls" else "Clear Call History")
            },
            text = {
                Text(
                    if (isMissedFilter) {
                        "Are you sure you want to clear all missed call records? Your incoming and outgoing call history will be kept."
                    } else if (hasMissedCalls) {
                        "Would you like to clear only missed calls or clear your entire call history?"
                    } else {
                        "Are you sure you want to remove all call records from your history?"
                    }
                )
            },
            confirmButton = {
                if (isMissedFilter) {
                    TextButton(
                        onClick = {
                            onClearMissedCalls()
                            showClearDialog = false
                        }
                    ) {
                        Text("Clear Missed", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (hasMissedCalls) {
                            TextButton(
                                onClick = {
                                    onClearMissedCalls()
                                    showClearDialog = false
                                }
                            ) {
                                Text("Clear Missed Only", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        TextButton(
                            onClick = {
                                onClearAll()
                                showClearDialog = false
                            }
                        ) {
                            Text("Clear All", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    callNumberTarget?.let { num ->
        SimSelectionDialog(
            phoneNumber = num,
            contactName = callNameTarget,
            simCards = availableSims,
            onSimSelected = { sim ->
                val targetName = callNameTarget
                callNumberTarget = null
                callNameTarget = null
                placeCallWithSpecificSim(num, targetName, sim)
            },
            onDismiss = {
                callNumberTarget = null
                callNameTarget = null
            }
        )
    }
  }
}

@Composable
fun CallLogItem(
    entry: CallLogEntry,
    callCount: Int = 1,
    oldestTimeOfDay: String? = null,
    onClick: () -> Unit,
    onCallClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("call_item_${entry.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ContactAvatar(nameOrNumber = entry.displayName, size = 44.dp)

            Column(modifier = Modifier.weight(1f)) {
                val isPriority = remember(entry.number) { PriorityContactManager.isPriorityNumber(entry.number) }
                val titleColor = if (entry.type == CallType.MISSED) MissedRed else MaterialTheme.colorScheme.onSurface
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = entry.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = titleColor,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (callCount > 1) {
                        Text(
                            text = "($callCount)",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = titleColor,
                            modifier = Modifier.testTag("call_count_badge_${entry.id}")
                        )
                    }
                    if (isPriority) {
                        Text(
                            text = "★",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val (icon, tint) = when (entry.type) {
                        CallType.INCOMING -> Icons.Filled.CallReceived to SuccessGreen
                        CallType.OUTGOING -> Icons.Filled.CallMade to MaterialTheme.colorScheme.primary
                        CallType.MISSED, CallType.REJECTED -> Icons.Filled.CallMissed to MissedRed
                        else -> Icons.Filled.Call to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = tint
                    )
                    Text(
                        text = if (callCount > 1 && oldestTimeOfDay != null && oldestTimeOfDay != entry.formattedTimeOfDay) {
                            "${entry.formattedDate} ($oldestTimeOfDay – ${entry.formattedTimeOfDay}) • ${entry.formattedDuration}"
                        } else {
                            "${entry.formattedDate} • ${entry.formattedDuration}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onCallClick,
                modifier = Modifier
                    .size(42.dp)
                    .testTag("call_action_${entry.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.Call,
                    contentDescription = "Call ${entry.displayName}",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
