package com.example.floating

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FloatingConfig(
    val isServiceEnabled: Boolean = true,
    val lockPosition: Boolean = false,
    val bubbleSizeDp: Int = 58,
    val bubbleAlpha: Float = 0.95f,
    val startOnBoot: Boolean = true,
    val windowScale: Float = 0.85f,
    val isFitToScreen: Boolean = false,
    val minimizedTab: String = "MESSAGES",
    val bubbleX: Int = -1,
    val bubbleY: Int = -1
)

object FloatingPreferences {
    private const val PREFS_NAME = "xeno_floating_prefs"
    private const val KEY_SERVICE_ENABLED = "key_service_enabled"
    private const val KEY_LOCK_POSITION = "key_lock_position"
    private const val KEY_BUBBLE_SIZE = "key_bubble_size"
    private const val KEY_BUBBLE_ALPHA = "key_bubble_alpha"
    private const val KEY_START_ON_BOOT = "key_start_on_boot"
    private const val KEY_WINDOW_SCALE = "key_window_scale"
    private const val KEY_IS_FIT = "key_is_fit"
    private const val KEY_MINIMIZED_TAB = "key_minimized_tab"
    private const val KEY_BUBBLE_X = "key_bubble_x"
    private const val KEY_BUBBLE_Y = "key_bubble_y"

    private var prefs: SharedPreferences? = null
    private val _configFlow = MutableStateFlow(FloatingConfig())
    val configFlow: StateFlow<FloatingConfig> = _configFlow.asStateFlow()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadConfig()
        }
    }

    private fun loadConfig() {
        val p = prefs ?: return
        val config = FloatingConfig(
            isServiceEnabled = p.getBoolean(KEY_SERVICE_ENABLED, true),
            lockPosition = p.getBoolean(KEY_LOCK_POSITION, false),
            bubbleSizeDp = p.getInt(KEY_BUBBLE_SIZE, 58),
            bubbleAlpha = p.getFloat(KEY_BUBBLE_ALPHA, 0.95f),
            startOnBoot = p.getBoolean(KEY_START_ON_BOOT, true),
            windowScale = p.getFloat(KEY_WINDOW_SCALE, 0.85f),
            isFitToScreen = p.getBoolean(KEY_IS_FIT, false),
            minimizedTab = p.getString(KEY_MINIMIZED_TAB, "MESSAGES") ?: "MESSAGES",
            bubbleX = p.getInt(KEY_BUBBLE_X, -1),
            bubbleY = p.getInt(KEY_BUBBLE_Y, -1)
        )
        _configFlow.value = config
    }

    fun setLockPosition(locked: Boolean) {
        prefs?.edit()?.putBoolean(KEY_LOCK_POSITION, locked)?.apply()
        _configFlow.value = _configFlow.value.copy(lockPosition = locked)
    }

    fun setBubbleSize(sizeDp: Int) {
        prefs?.edit()?.putInt(KEY_BUBBLE_SIZE, sizeDp)?.apply()
        _configFlow.value = _configFlow.value.copy(bubbleSizeDp = sizeDp)
    }

    fun setBubbleAlpha(alpha: Float) {
        prefs?.edit()?.putFloat(KEY_BUBBLE_ALPHA, alpha)?.apply()
        _configFlow.value = _configFlow.value.copy(bubbleAlpha = alpha)
    }

    fun setStartOnBoot(start: Boolean) {
        prefs?.edit()?.putBoolean(KEY_START_ON_BOOT, start)?.apply()
        _configFlow.value = _configFlow.value.copy(startOnBoot = start)
    }

    fun setWindowScale(scale: Float) {
        val clamped = scale.coerceIn(0.60f, 0.98f)
        prefs?.edit()?.putFloat(KEY_WINDOW_SCALE, clamped)?.apply()
        _configFlow.value = _configFlow.value.copy(windowScale = clamped)
    }

    fun setFitToScreen(isFit: Boolean) {
        prefs?.edit()?.putBoolean(KEY_IS_FIT, isFit)?.apply()
        _configFlow.value = _configFlow.value.copy(isFitToScreen = isFit)
    }

    fun setMinimizedTab(tab: String) {
        prefs?.edit()?.putString(KEY_MINIMIZED_TAB, tab)?.apply()
        _configFlow.value = _configFlow.value.copy(minimizedTab = tab)
    }

    fun setBubblePosition(x: Int, y: Int) {
        prefs?.edit()?.putInt(KEY_BUBBLE_X, x)?.putInt(KEY_BUBBLE_Y, y)?.apply()
        _configFlow.value = _configFlow.value.copy(bubbleX = x, bubbleY = y)
    }

    fun setServiceEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_SERVICE_ENABLED, enabled)?.apply()
        _configFlow.value = _configFlow.value.copy(isServiceEnabled = enabled)
    }
}
