package com.example.telecom

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import com.example.MainActivity
import com.example.R

object XenoOverlayService {

    private var activeCallOverlayView: View? = null
    private var activeSmsOverlayView: View? = null

    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density).toInt()
    }

    fun showCallOverlay(
        context: Context,
        callerName: String?,
        callerNumber: String
    ) {
        if (!canDrawOverlays(context)) {
            Log.w("XenoOverlayService", "Overlay permission not granted, relying on system heads-up call notification")
            return
        }

        dismissCallOverlay(context)

        try {
            val appContext = context.applicationContext
            val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
            val inflater = LayoutInflater.from(appContext)

            val container = FrameLayout(appContext).apply {
                val sidePad = dpToPx(appContext, 12)
                val topPad = dpToPx(appContext, 8)
                setPadding(sidePad, topPad, sidePad, topPad)
            }
            val overlayView = inflater.inflate(R.layout.notification_custom_call, container, false)
            container.addView(overlayView)

            val avatarTv = overlayView.findViewById<TextView>(R.id.notif_call_avatar_text)
            val nameTv = overlayView.findViewById<TextView>(R.id.notif_caller_name)
            val numberTv = overlayView.findViewById<TextView>(R.id.notif_caller_number)
            val btnDecline = overlayView.findViewById<Button>(R.id.btn_call_decline)
            val btnAnswer = overlayView.findViewById<Button>(R.id.btn_call_answer)

            val displayName = if (!callerName.isNullOrBlank()) callerName else callerNumber
            val initials = XenoNotificationHelper.extractInitials(displayName, callerNumber)
            val subtitle = if (displayName != callerNumber && callerNumber.isNotBlank()) {
                "Incoming Voice Call • $callerNumber"
            } else {
                "Incoming Voice Call"
            }

            avatarTv?.text = initials
            nameTv?.text = displayName
            numberTv?.text = subtitle

            overlayView.setOnClickListener {
                dismissCallOverlay(appContext)
                OngoingCallHelper.expandIncomingCallToFullScreen(appContext)
                val intent = Intent(appContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("open_tab", "CALLS")
                    putExtra("incoming_number", callerNumber)
                    putExtra("caller_name", callerName)
                    putExtra("open_full_screen_call", true)
                }
                appContext.startActivity(intent)
            }

            btnDecline?.setOnClickListener {
                OngoingCallHelper.declineIncomingCall(appContext)
            }

            btnAnswer?.setOnClickListener {
                OngoingCallHelper.answerIncomingCallAndOpenFullScreen(
                    context = appContext,
                    phoneNumber = callerNumber,
                    contactName = callerName
                )

                val intent = Intent(appContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("open_tab", "CALLS")
                    putExtra("incoming_number", callerNumber)
                    putExtra("caller_name", callerName)
                    putExtra("auto_answer", true)
                }
                appContext.startActivity(intent)
            }

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dpToPx(appContext, 16)
            }

            windowManager.addView(container, params)
            activeCallOverlayView = container
        } catch (e: Exception) {
            Log.e("XenoOverlayService", "Failed to show call overlay: ${e.message}")
        }
    }

    fun dismissCallOverlay(context: Context) {
        activeCallOverlayView?.let { view ->
            try {
                val windowManager = context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                windowManager?.removeView(view)
            } catch (_: Exception) {}
        }
        activeCallOverlayView = null
    }

    fun showSmsOverlay(
        context: Context,
        senderName: String,
        senderNumber: String,
        body: String,
        threadId: Long
    ) {
        if (!canDrawOverlays(context)) {
            Log.w("XenoOverlayService", "Overlay permission not granted, skipping SMS floating popup")
            return
        }

        dismissSmsOverlay(context)

        try {
            val appContext = context.applicationContext
            val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
            val inflater = LayoutInflater.from(appContext)

            val container = FrameLayout(appContext).apply {
                val sidePad = dpToPx(appContext, 12)
                val topPad = dpToPx(appContext, 8)
                setPadding(sidePad, topPad, sidePad, topPad)
            }
            val overlayView = inflater.inflate(R.layout.notification_custom_sms, container, false)
            container.addView(overlayView)

            val avatarTv = overlayView.findViewById<TextView>(R.id.notif_sms_avatar_text)
            val senderTv = overlayView.findViewById<TextView>(R.id.notif_sms_sender)
            val numberTv = overlayView.findViewById<TextView>(R.id.notif_sms_number)
            val timeTv = overlayView.findViewById<TextView>(R.id.notif_sms_time)
            val bodyTv = overlayView.findViewById<TextView>(R.id.notif_sms_body)
            val btnMarkRead = overlayView.findViewById<Button>(R.id.btn_sms_mark_read)
            val btnReply = overlayView.findViewById<Button>(R.id.btn_sms_reply)

            avatarTv?.text = XenoNotificationHelper.extractInitials(senderName, senderNumber)
            senderTv?.text = senderName
            numberTv?.text = senderNumber
            timeTv?.text = XenoNotificationHelper.formatNotificationTime()
            bodyTv?.text = body

            btnMarkRead?.setOnClickListener {
                dismissSmsOverlay(appContext)
                XenoNotificationHelper.cancelSmsNotification(appContext, threadId, senderNumber)
            }

            btnReply?.setOnClickListener {
                dismissSmsOverlay(appContext)
                XenoNotificationHelper.cancelSmsNotification(appContext, threadId, senderNumber)

                val intent = Intent(appContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("open_tab", "MESSAGES")
                    putExtra("thread_id", threadId)
                    putExtra("sender_number", senderNumber)
                }
                appContext.startActivity(intent)
            }

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dpToPx(appContext, 16)
            }

            windowManager.addView(container, params)
            activeSmsOverlayView = container

            // Auto dismiss SMS overlay after 8 seconds
            container.postDelayed({
                dismissSmsOverlay(appContext)
            }, 8000)

        } catch (e: Exception) {
            Log.e("XenoOverlayService", "Failed to show SMS overlay: ${e.message}")
        }
    }

    fun dismissSmsOverlay(context: Context) {
        activeSmsOverlayView?.let { view ->
            try {
                val windowManager = context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                windowManager?.removeView(view)
            } catch (_: Exception) {}
        }
        activeSmsOverlayView = null
    }
}
