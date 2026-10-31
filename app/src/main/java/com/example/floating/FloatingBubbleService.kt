package com.example.floating

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlin.math.abs

class FloatingBubbleService : Service() {

    private var windowManager: WindowManager? = null
    private var bubbleView: ImageView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    companion object {
        const val TAG = "FloatingBubbleService"
        const val ACTION_START = "com.example.floating.ACTION_START"
        const val ACTION_STOP = "com.example.floating.ACTION_STOP"
        const val ACTION_UPDATE_TAB = "com.example.floating.ACTION_UPDATE_TAB"
        const val EXTRA_TAB = "extra_tab"

        private const val NOTIFICATION_ID = 4099
        private const val CHANNEL_ID = "floating_bubble_channel"

        @Volatile
        var isRunning: Boolean = false
            private set

        fun isOverlayPermissionGranted(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        }

        fun isIgnoringBatteryOptimizations(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                pm?.isIgnoringBatteryOptimizations(context.packageName) == true
            } else {
                true
            }
        }

        fun requestIgnoreBatteryOptimizations(context: Context) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val intent = Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}")
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error opening battery optimization settings", e)
            }
        }

        fun openOverlayPermissionSettings(context: Context) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error opening overlay permission settings", e)
            }
        }

        fun start(context: Context) {
            FloatingPreferences.init(context)
            if (!isOverlayPermissionGranted(context)) {
                Log.w(TAG, "Overlay permission not granted. Cannot start bubble service.")
                return
            }
            val intent = Intent(context, FloatingBubbleService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingBubbleService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun updateMinimizedTab(context: Context, tabName: String) {
            FloatingPreferences.init(context)
            FloatingPreferences.setMinimizedTab(tabName)
            if (isRunning) {
                val intent = Intent(context, FloatingBubbleService::class.java).apply {
                    action = ACTION_UPDATE_TAB
                    putExtra(EXTRA_TAB, tabName)
                }
                context.startService(intent)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        FloatingPreferences.init(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        createNotificationChannel()
        acquireWakeLock()
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                wakeLock = pm?.newWakeLock(
                    android.os.PowerManager.PARTIAL_WAKE_LOCK,
                    "Rabbit:FloatingBubbleWakeLock"
                )?.apply {
                    setReferenceCounted(false)
                    acquire(10 * 60 * 1000L /* 10 minutes timeout safeguard per lock acquire */)
                }
            } else if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(10 * 60 * 1000L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring wake lock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wake lock", e)
        }
        wakeLock = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        if (action == ACTION_STOP) {
            releaseWakeLock()
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

        acquireWakeLock()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        isRunning = true

        val tab = intent?.getStringExtra(EXTRA_TAB) ?: FloatingPreferences.configFlow.value.minimizedTab
        if (action == ACTION_UPDATE_TAB) {
            updateBubbleIcon(tab)
        } else {
            showOrUpdateBubble(tab)
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Rabbit Floating App Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the floating app icon and window active in background"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val stopIntent = Intent(this, FloatingBubbleService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Rabbit Floating App")
            .setContentText("Floating icon is active. Tap to open or Turn Off to stop.")
            .setSmallIcon(R.drawable.black_rabbit_icon)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .addAction(0, "Turn Off", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private fun showOrUpdateBubble(tab: String) {
        if (!isOverlayPermissionGranted(this)) return

        val config = FloatingPreferences.configFlow.value
        val sizePx = dpToPx(config.bubbleSizeDp)

        if (bubbleView == null) {
            val iv = ImageView(this).apply {
                setBackgroundResource(R.drawable.bg_floating_bubble)
                scaleType = ImageView.ScaleType.FIT_CENTER
                val pad = (sizePx * 0.22f).toInt()
                setPadding(pad, pad, pad, pad)
                alpha = config.bubbleAlpha
                elevation = dpToPx(6).toFloat()
            }
            bubbleView = iv

            val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val displayMetrics = resources.displayMetrics
            val defaultX = if (config.bubbleX >= 0) config.bubbleX else displayMetrics.widthPixels - sizePx - dpToPx(16)
            val defaultY = if (config.bubbleY >= 0) config.bubbleY else displayMetrics.heightPixels / 3

            val params = WindowManager.LayoutParams(
                sizePx,
                sizePx,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = defaultX
                y = defaultY
            }
            layoutParams = params

            setupBubbleTouchListener(iv, params)

            try {
                windowManager?.addView(iv, params)
            } catch (e: Exception) {
                Log.e(TAG, "Error adding bubble view to WindowManager", e)
            }
        } else {
            layoutParams?.let { params ->
                params.width = sizePx
                params.height = sizePx
                bubbleView?.alpha = config.bubbleAlpha
                val pad = (sizePx * 0.22f).toInt()
                bubbleView?.setPadding(pad, pad, pad, pad)
                try {
                    windowManager?.updateViewLayout(bubbleView, params)
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating bubble layout", e)
                }
            }
        }

        updateBubbleIcon(tab)
    }

    private fun updateBubbleIcon(tab: String) {
        val iv = bubbleView ?: return
        when (tab.uppercase()) {
            "MESSAGES" -> iv.setImageResource(R.drawable.ic_bubble_message)
            "BROWSER" -> iv.setImageResource(R.drawable.ic_bubble_browser)
            "CLONE_SPACE" -> iv.setImageResource(R.drawable.ic_bubble_clones)
            else -> iv.setImageResource(R.drawable.black_rabbit_icon)
        }
    }

    private fun setupBubbleTouchListener(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = true
        val clickSlop = dpToPx(6)

        view.setOnTouchListener { _, event ->
            val locked = FloatingPreferences.configFlow.value.lockPosition

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()

                    if (abs(dx) > clickSlop || abs(dy) > clickSlop) {
                        isClick = false
                    }

                    if (!locked && !isClick) {
                        params.x = initialX + dx
                        params.y = initialY + dy
                        try {
                            windowManager?.updateViewLayout(view, params)
                        } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        launchFloatingWindow()
                    } else if (!locked) {
                        FloatingPreferences.setBubblePosition(params.x, params.y)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun launchFloatingWindow() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("from_floating_bubble", true)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        releaseWakeLock()
        bubbleView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing bubble view", e)
            }
        }
        bubbleView = null
    }
}
