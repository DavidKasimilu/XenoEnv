package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.ContactsContract
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.ContactItem
import com.example.telecom.SimCardInfo
import com.example.telecom.TelecomHelper
import com.example.ui.components.ContactAvatar
import com.example.ui.components.SimSelectionDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue


data class DialpadKey(val digit: Char, val letters: String)

private val DIALPAD_KEYS = listOf(
    DialpadKey('1', ""),
    DialpadKey('2', "A B C"),
    DialpadKey('3', "D E F"),
    DialpadKey('4', "G H I"),
    DialpadKey('5', "J K L"),
    DialpadKey('6', "M N O"),
    DialpadKey('7', "P Q R S"),
    DialpadKey('8', "T U V"),
    DialpadKey('9', "W X Y Z"),
    DialpadKey('*', ""),
    DialpadKey('0', "+"),
    DialpadKey('#', "")
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KeypadScreen(
    digits: String,
    onDigitClick: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSettingsClick: () -> Unit,
    contacts: List<ContactItem>,
    onStartSms: (String, String?) -> Unit,
    onStartCall: (String, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("xeno_telecom_prefs", Context.MODE_PRIVATE) }
    var selectedSimSlot by remember { mutableStateOf(prefs.getInt("preferred_sim_slot", 0)) }
    var selectedSimName by remember { mutableStateOf(prefs.getString("preferred_sim_name", "SIM 1") ?: "SIM 1") }
    var showSimSelectionModal by remember { mutableStateOf(false) }

    // Find matched contact based on typed digits
    val matchedContact = remember(digits, contacts) {
        if (digits.length >= 3) {
            val clean = digits.filter { it.isDigit() }
            contacts.find { contact ->
                contact.primaryNumber.filter { it.isDigit() }.contains(clean) ||
                        contact.allNumbers.any { it.filter { c -> c.isDigit() }.contains(clean) }
            }
        } else null
    }

    var callNumberTarget by remember { mutableStateOf<String?>(null) }
    var availableSims by remember { mutableStateOf<List<SimCardInfo>>(emptyList()) }

    fun placeCall(number: String, name: String? = null) {
        val clean = number.trim()
        val isUssd = TelecomHelper.isUssdCode(clean)
        val sims = TelecomHelper.getAvailableSimCards(context)
        if (sims.size > 1 && !isUssd && callNumberTarget == null) {
            availableSims = sims
            callNumberTarget = clean
            return
        }
        val selectedSim = sims.find { it.subscriptionId == selectedSimSlot } ?: sims.firstOrNull()
        if (selectedSim?.phoneAccountHandle != null) {
            TelecomHelper.makeCallWithSim(context, clean, selectedSim.phoneAccountHandle)
        } else {
            TelecomHelper.makeCall(context, clean)
        }
        if (!isUssd) {
            val resolvedName = name ?: matchedContact?.displayName ?: contacts.find { it.primaryNumber.filter { c -> c.isDigit() } == number.filter { c -> c.isDigit() } }?.displayName
            onStartCall(number, resolvedName)
        }
    }

    fun triggerVibration() {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(20)
            }
        } catch (_: Exception) {}
    }

    if (showSimSelectionModal) {
        val detectedSims = remember {
            val list = TelecomHelper.getAvailableSimCards(context)
            if (list.size >= 2) list else listOf(
                SimCardInfo(null, "SIM 1 (Primary)", 0),
                SimCardInfo(null, "SIM 2 (Secondary)", 1)
            )
        }
        SimSelectionDialog(
            phoneNumber = "Select SIM Card for Calls",
            simCards = detectedSims,
            onSimSelected = { sim ->
                selectedSimSlot = sim.subscriptionId
                val label = if (sim.carrierName.length > 12) "SIM ${sim.subscriptionId + 1}" else sim.carrierName
                selectedSimName = label
                prefs.edit()
                    .putInt("preferred_sim_slot", sim.subscriptionId)
                    .putString("preferred_sim_name", label)
                    .apply()
                showSimSelectionModal = false
                android.widget.Toast.makeText(
                    context,
                    "Calling SIM set to: ${sim.carrierName}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            },
            onDismiss = { showSimSelectionModal = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .testTag("keypad_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Bar: Preferred SIM selector & Settings button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        showSimSelectionModal = true
                    }
                    .testTag("keypad_sim_selector_btn")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.SimCard,
                        contentDescription = "Set SIM",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = selectedSimName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.testTag("keypad_settings_btn")
            ) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings")
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            contentAlignment = Alignment.Center
        ) {
            if (matchedContact != null) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.clickable {
                        placeCall(matchedContact.primaryNumber, matchedContact.displayName)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ContactAvatar(nameOrNumber = matchedContact.displayName, size = 26.dp)
                        Text(
                            text = matchedContact.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Displayed typed digits area
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (digits.isEmpty()) " " else digits,
                fontSize = if (digits.length > 12) 28.sp else 36.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dialpad_digits_display")
            )
        }

        // 3x4 Dialpad Grid
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            for (row in 0..3) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (col in 0..2) {
                        val index = row * 3 + col
                        val key = DIALPAD_KEYS[index]
                        DialpadButton(
                            key = key,
                            onClick = {
                                triggerVibration()
                                onDigitClick(key.digit)
                            },
                            onLongClick = {
                                if (key.digit == '0') {
                                    triggerVibration()
                                    onDigitClick('+')
                                }
                            }
                        )
                    }
                }
            }
        }

        // Bottom Action Bar: [Add Contact / SMS] - [Call Button] - [Backspace]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Action: SMS or Add Contact
            if (digits.isNotEmpty()) {
                IconButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_INSERT).apply {
                            type = ContactsContract.Contacts.CONTENT_TYPE
                            putExtra(ContactsContract.Intents.Insert.PHONE, digits)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier
                        .size(54.dp)
                        .testTag("dialpad_add_contact")
                ) {
                    Icon(
                        imageVector = Icons.Filled.PersonAdd,
                        contentDescription = "Save to contacts",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                IconButton(
                    onClick = {
                        val intent = context.packageManager.getLaunchIntentForPackage("com.android.stk")
                        if (intent != null) {
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier
                        .size(54.dp)
                        .testTag("dialpad_sim_toolkit")
                ) {
                    Icon(
                        imageVector = Icons.Filled.SimCard,
                        contentDescription = "SIM Toolkit",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Big Call Button
            Surface(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .testTag("dialpad_call_button")
                    .clickable {
                        if (digits.isNotBlank()) {
                            triggerVibration()
                            placeCall(digits)
                        }
                    },
                shape = CircleShape,
                color = if (digits.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                shadowElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Call,
                        contentDescription = "Call",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // Right Action: Backspace
            if (digits.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                            onClick = {
                                triggerVibration()
                                onBackspace()
                            },
                            onLongClick = {
                                triggerVibration()
                                onClear()
                            }
                        )
                        .testTag("dialpad_backspace"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Delete digit",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(54.dp))
            }
        }
    }

    callNumberTarget?.let { num ->
        SimSelectionDialog(
            phoneNumber = num,
            simCards = availableSims,
            onSimSelected = { sim ->
                callNumberTarget = null
                TelecomHelper.makeCallWithSim(context, num, sim.phoneAccountHandle)
                val resolvedName = matchedContact?.displayName ?: contacts.find { it.primaryNumber.filter { c -> c.isDigit() } == num.filter { c -> c.isDigit() } }?.displayName
                onStartCall(num, resolvedName)
            },
            onDismiss = { callNumberTarget = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialpadButton(
    key: DialpadKey,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .size(72.dp)
            .clip(CircleShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("dialpad_key_${key.digit}"),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = key.digit.toString(),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (key.letters.isNotEmpty()) {
                Text(
                    text = key.letters,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
