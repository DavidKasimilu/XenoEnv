package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.ContactItem
import com.example.telecom.SimCardInfo
import com.example.telecom.TelecomHelper
import com.example.ui.components.SimSelectionDialog

data class DialpadKey(val digit: Char, val letters: String, val hasVoicemail: Boolean = false)

private val DIALPAD_KEYS = listOf(
    DialpadKey('1', "", hasVoicemail = true),
    DialpadKey('2', "ABC"),
    DialpadKey('3', "DEF"),
    DialpadKey('4', "GHI"),
    DialpadKey('5', "JKL"),
    DialpadKey('6', "MNO"),
    DialpadKey('7', "PQRS"),
    DialpadKey('8', "TUV"),
    DialpadKey('9', "WXYZ"),
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
    onStartCallWithSim: (String, String?, SimCardInfo?) -> Unit = { num, name, _ -> onStartCall(num, name) },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("xeno_telecom_prefs", Context.MODE_PRIVATE) }
    var selectedSimSlot by remember { mutableStateOf(prefs.getInt("preferred_sim_slot", 0)) }
    var selectedSimName by remember { mutableStateOf(prefs.getString("preferred_sim_name", "SIM 1") ?: "SIM 1") }
    var showSimSelectionModal by remember { mutableStateOf(false) }
    var callNumberTarget by remember { mutableStateOf<String?>(null) }
    var callNameTarget by remember { mutableStateOf<String?>(null) }
    var availableSims by remember { mutableStateOf<List<SimCardInfo>>(emptyList()) }

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

    // Filter contacts for the "Suggested" section
    val suggestedContacts = remember(digits, contacts) {
        if (digits.isBlank()) {
            contacts.take(5)
        } else {
            val clean = digits.filter { it.isDigit() }
            contacts.filter { contact ->
                contact.primaryNumber.filter { it.isDigit() }.contains(clean) ||
                        contact.displayName.contains(digits, ignoreCase = true) ||
                        contact.allNumbers.any { it.filter { c -> c.isDigit() }.contains(clean) }
            }.take(5)
        }
    }

    fun initiateCall(number: String, name: String? = null) {
        val clean = number.trim()
        if (clean.isBlank()) return
        val isUssd = TelecomHelper.isUssdCode(clean)
        val sims = TelecomHelper.getAvailableSimCards(context)
        
        // When dialing USSD or standard call with multiple SIMs, prompt SIM selection if not chosen
        if (sims.size > 1 && callNumberTarget == null) {
            availableSims = sims
            callNumberTarget = clean
            callNameTarget = name
            return
        }

        // Single SIM or already selected
        val defaultSim = sims.firstOrNull()
        onStartCallWithSim(clean, name, defaultSim)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121214))
            .testTag("keypad_screen")
    ) {
        // Upper section: Suggested contacts
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Suggested",
                style = MaterialTheme.typography.titleSmall,
                color = Color(0xFFB0B0B4),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(vertical = 6.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(
                    items = suggestedContacts,
                    key = { "sugg_${it.id}_${it.primaryNumber}" }
                ) { contact ->
                    val avatarColor = remember(contact.displayName) {
                        val colors = listOf(
                            Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFE65100),
                            Color(0xFF00897B), Color(0xFFD81B60), Color(0xFF8E24AA)
                        )
                        val hash = kotlin.math.abs(contact.displayName.hashCode())
                        colors[hash % colors.size]
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                initiateCall(contact.primaryNumber, contact.displayName)
                            },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E22))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    modifier = Modifier.size(42.dp),
                                    shape = CircleShape,
                                    color = avatarColor
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = contact.displayName.take(1).uppercase(),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp
                                        )
                                    }
                                }

                                Column {
                                    Text(
                                        text = contact.displayName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Mobile ${contact.primaryNumber}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF9E9E9E),
                                        maxLines = 1
                                    )
                                }
                            }

                            IconButton(
                                onClick = { initiateCall(contact.primaryNumber, contact.displayName) }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Call,
                                    contentDescription = "Call",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Lower Keypad Container (Screenshot 6 Layout)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = Color(0xFF18181C),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Digits Display and Top-Right Backspace Key
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (digits.isBlank()) "" else digits,
                        fontSize = if (digits.length > 12) 26.sp else 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp)
                            .testTag("dialpad_digits_display")
                    )

                    if (digits.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                triggerVibration()
                                onBackspace()
                            },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(44.dp)
                                .testTag("dialpad_backspace")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Delete",
                                tint = Color(0xFFB0B0B4),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 3x4 Dialpad Grid of Oval / Rounded Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (row in 0..3) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            for (col in 0..2) {
                                val key = DIALPAD_KEYS[row * 3 + col]
                                DialpadPillButton(
                                    key = key,
                                    onClick = {
                                        triggerVibration()
                                        onDigitClick(key.digit)
                                    },
                                    onLongClick = {
                                        if (key.digit == '0') {
                                            triggerVibration()
                                            onDigitClick('+')
                                        } else if (key.digit == '1') {
                                            triggerVibration()
                                            initiateCall("123", "Voicemail")
                                        }
                                    },
                                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Call Button: Wide Green Pill (Screenshot 6)
                Surface(
                    modifier = Modifier
                        .width(160.dp)
                        .height(54.dp)
                        .clip(RoundedCornerShape(27.dp))
                        .clickable {
                            if (digits.isNotBlank()) {
                                triggerVibration()
                                initiateCall(digits)
                            }
                        }
                        .testTag("dialpad_call_button"),
                    shape = RoundedCornerShape(27.dp),
                    color = if (digits.isNotBlank()) Color(0xFF4CAF50) else Color(0xFF2E7D32).copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Call,
                            contentDescription = "Call",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Call",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    // SIM Selection Dialog for Calls & USSD code dialing
    callNumberTarget?.let { num ->
        SimSelectionDialog(
            phoneNumber = num,
            simCards = availableSims,
            onSimSelected = { sim ->
                callNumberTarget = null
                selectedSimSlot = sim.subscriptionId
                val label = if (sim.carrierName.length > 12) "SIM ${sim.subscriptionId + 1}" else sim.carrierName
                selectedSimName = label
                prefs.edit()
                    .putInt("preferred_sim_slot", sim.subscriptionId)
                    .putString("preferred_sim_name", label)
                    .apply()
                onStartCallWithSim(num, callNameTarget, sim)
            },
            onDismiss = { callNumberTarget = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialpadPillButton(
    key: DialpadKey,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(27.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("dialpad_key_${key.digit}"),
        shape = RoundedCornerShape(27.dp),
        color = Color(0xFF24242A)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = key.digit.toString(),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                lineHeight = 24.sp
            )
            if (key.hasVoicemail) {
                Icon(
                    imageVector = Icons.Filled.Voicemail,
                    contentDescription = null,
                    tint = Color(0xFF9E9E9E),
                    modifier = Modifier.size(12.dp)
                )
            } else if (key.letters.isNotEmpty()) {
                Text(
                    text = key.letters,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF9E9E9E),
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
