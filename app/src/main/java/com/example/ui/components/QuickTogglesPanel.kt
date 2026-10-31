package com.example.ui.components

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.SignalCellularOff
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide persistent Torch controller that keeps the flashlight ON across
 * App Alerts drawer open/close, screen navigation, and screen-off states until
 * the user explicitly turns the flashlight OFF.
 */
object PersistentTorchController {
    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()

    @Volatile
    private var userRequestedOn: Boolean = false

    @Volatile
    private var initialized: Boolean = false

    private var appContext: Context? = null
    private var cameraManager: CameraManager? = null
    private var torchCameraId: String? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val app = context.applicationContext
            appContext = app
            val cm = app.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            cameraManager = cm
            torchCameraId = try {
                cm?.cameraIdList?.firstOrNull { id ->
                    val chars = cm.getCameraCharacteristics(id)
                    chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }
            } catch (_: Exception) {
                null
            }

            try {
                cm?.registerTorchCallback(
                    object : CameraManager.TorchCallback() {
                        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                            if (cameraId == torchCameraId) {
                                if (userRequestedOn && !enabled) {
                                    // Re-assert torch if system turned it off (e.g., on screen lock/off)
                                    mainHandler.postDelayed({
                                        if (userRequestedOn) {
                                            try {
                                                cm.setTorchMode(cameraId, true)
                                            } catch (_: Exception) {}
                                        }
                                    }, 60L)
                                } else {
                                    _isTorchOn.value = userRequestedOn || enabled
                                }
                            }
                        }
                    },
                    mainHandler
                )
            } catch (_: Exception) {}

            try {
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_USER_PRESENT)
                }
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context?, intent: Intent?) {
                        if (userRequestedOn) {
                            reassertTorch()
                            mainHandler.postDelayed({ if (userRequestedOn) reassertTorch() }, 150L)
                            mainHandler.postDelayed({ if (userRequestedOn) reassertTorch() }, 400L)
                        }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    app.registerReceiver(receiver, filter)
                }
            } catch (_: Exception) {}

            initialized = true
        }
    }

    private fun reassertTorch() {
        val cm = cameraManager ?: return
        val camId = torchCameraId ?: return
        try {
            cm.setTorchMode(camId, true)
        } catch (_: Exception) {}
    }

    private fun updateWakeLock(enable: Boolean) {
        val app = appContext ?: return
        try {
            if (enable) {
                if (wakeLock == null) {
                    val pm = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    wakeLock = pm?.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "xeno:PersistentTorchWakeLock"
                    )?.apply {
                        setReferenceCounted(false)
                    }
                }
                if (wakeLock?.isHeld == false) {
                    wakeLock?.acquire()
                }
            } else {
                if (wakeLock?.isHeld == true) {
                    wakeLock?.release()
                }
            }
        } catch (_: Exception) {}
    }

    fun toggleTorch(context: Context) {
        ensureInitialized(context)
        val nextState = !userRequestedOn
        userRequestedOn = nextState
        _isTorchOn.value = nextState
        updateWakeLock(nextState)

        val cm = cameraManager
        val camId = torchCameraId
        if (cm != null && camId != null) {
            try {
                cm.setTorchMode(camId, nextState)
            } catch (_: Exception) {}
        }
    }
}

@Composable
fun QuickTogglesPanel(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // 1. Persistent Torch State (stays ON across navigation & screen off until explicitly toggled off)
    LaunchedEffect(Unit) {
        PersistentTorchController.ensureInitialized(context)
    }
    val isTorchOn by PersistentTorchController.isTorchOn.collectAsState()

    fun toggleTorch() {
        PersistentTorchController.toggleTorch(context)
    }

    // 2. Wi-Fi State
    var isWifiEnabled by remember {
        val wifiMgr = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        mutableStateOf(wifiMgr?.isWifiEnabled ?: false)
    }

    fun openWifiSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    // 3. Mobile Data State
    var isDataConnected by remember {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val net = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(net)
        mutableStateOf(caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true)
    }

    fun openDataSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
        } else {
            Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    // 4. Bluetooth State
    var isBluetoothOn by remember {
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            mutableStateOf(adapter?.isEnabled ?: false)
        } catch (_: Exception) {
            mutableStateOf(false)
        }
    }

    fun openBluetoothSettings() {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    // 5. SIM Toolkit Launch
    fun openSimToolkit() {
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage("com.android.stk")
            ?: pm.getLaunchIntentForPackage("com.android.stk2")
            ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage("com.android.stk")
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val stkIntent = Intent("android.intent.action.MAIN")
                stkIntent.setClassName("com.android.stk", "com.android.stk.StkLauncherActivity")
                stkIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(stkIntent)
            } catch (_: Exception) {
                try {
                    context.startActivity(Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {}
            }
        }
    }

    // 6. Brightness State
    var brightnessLevel by remember {
        val current = activity?.window?.attributes?.screenBrightness ?: -1f
        val initial = if (current >= 0) current else {
            try {
                val sysBrightness = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                sysBrightness / 255f
            } catch (_: Exception) {
                0.65f
            }
        }
        mutableFloatStateOf(initial.coerceIn(0.05f, 1.0f))
    }

    fun updateBrightness(level: Float) {
        val clamped = level.coerceIn(0.05f, 1.0f)
        brightnessLevel = clamped
        activity?.let { act ->
            val layoutParams = act.window.attributes
            layoutParams.screenBrightness = clamped
            act.window.attributes = layoutParams
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("quick_toggles_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // Quick Toggles Header
            Text(
                text = "Quick Controls",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Toggle Buttons Row (including SIM Toolkit)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Torch Toggle
                QuickToggleButton(
                    icon = if (isTorchOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                    label = "Torch",
                    isActive = isTorchOn,
                    onClick = { toggleTorch() },
                    testTag = "toggle_torch"
                )

                // Wi-Fi Toggle
                QuickToggleButton(
                    icon = if (isWifiEnabled) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                    label = "Wi-Fi",
                    isActive = isWifiEnabled,
                    onClick = {
                        isWifiEnabled = !isWifiEnabled
                        openWifiSettings()
                    },
                    testTag = "toggle_wifi"
                )

                // Mobile Data Toggle
                QuickToggleButton(
                    icon = if (isDataConnected) Icons.Filled.NetworkCell else Icons.Filled.SignalCellularOff,
                    label = "Data",
                    isActive = isDataConnected,
                    onClick = {
                        isDataConnected = !isDataConnected
                        openDataSettings()
                    },
                    testTag = "toggle_data"
                )

                // Bluetooth Toggle
                QuickToggleButton(
                    icon = if (isBluetoothOn) Icons.Filled.Bluetooth else Icons.Filled.BluetoothDisabled,
                    label = "Bluetooth",
                    isActive = isBluetoothOn,
                    onClick = {
                        isBluetoothOn = !isBluetoothOn
                        openBluetoothSettings()
                    },
                    testTag = "toggle_bluetooth"
                )

                // SIM Toolkit
                QuickToggleButton(
                    icon = Icons.Filled.SimCard,
                    label = "SIM Toolkit",
                    isActive = false,
                    onClick = { openSimToolkit() },
                    testTag = "toggle_sim_toolkit"
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Brightness Slider Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = when {
                        brightnessLevel < 0.35f -> Icons.Filled.BrightnessLow
                        brightnessLevel < 0.75f -> Icons.Filled.BrightnessMedium
                        else -> Icons.Filled.BrightnessHigh
                    },
                    contentDescription = "Brightness",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )

                Slider(
                    value = brightnessLevel,
                    onValueChange = { updateBrightness(it) },
                    valueRange = 0.05f..1.0f,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("brightness_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                Text(
                    text = "${(brightnessLevel * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(36.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickToggleButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Surface(
            shape = CircleShape,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .testTag(testTag)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
