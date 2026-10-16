package com.example.telecom

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
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

    fun showCallOverlay(
        context: Context,
        callerName: String?,
        callerNumber: String
    ) {
        if (!canDrawOverlays(context)) {
            Log.w("XenoOverlayService", "Overlay permission not granted, skipping call floating popup")
            return
        }

        dismissCallOverlay(context)

        try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
            val inflater = LayoutInflater.from(context)
            val overlayView = inflater.inflate(R.layout.notification_custom_call, null)

            val nameTv = overlayView.findViewById<TextView>(R.id.notif_caller_name)
            val numberTv = overlayView.findViewById<TextView>(R.id.notif_caller_number)
            val btnDecline = overlayView.findViewById<Button>(R.id.btn_call_decline)
            val btnAnswer = overlayView.findViewById<Button>(R.id.btn_call_answer)

            val displayName = if (!callerName.isNullOrBlank()) callerName else callerNumber
            nameTv?.text = displayName
            numberTv?.text = "Incoming Call ($callerNumber)"

            btnDecline?.setOnClickListener {
                XenoInCallService.rejectCall()
                dismissCallOverlay(context)
                XenoNotificationHelper.cancelCallNotification(context)
            }

            btnAnswer?.setOnClickListener {
                XenoInCallService.answerCall()
                dismissCallOverlay(context)
                XenoNotificationHelper.cancelCallNotification(context)

                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("open_tab", "CALLS")
                    putExtra("incoming_number", callerNumber)
                    putExtra("caller_name", callerName)
                    putExtra("auto_answer", true)
                }
                context.startActivity(intent)
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
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = 50
            }

            windowManager.addView(overlayView, params)
            activeCallOverlayView = overlayView
        } catch (e: Exception) {
            Log.e("XenoOverlayService", "Failed to show call overlay: ${e.message}")
        }
    }

    fun dismissCallOverlay(context: Context) {
        activeCallOverlayView?.let { view ->
            try {
                val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
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
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
            val inflater = LayoutInflater.from(context)
            val overlayView = inflater.inflate(R.layout.notification_custom_sms, null)

            val senderTv = overlayView.findViewById<TextView>(R.id.notif_sms_sender)
            val numberTv = overlayView.findViewById<TextView>(R.id.notif_sms_number)
            val bodyTv = overlayView.findViewById<TextView>(R.id.notif_sms_body)
            val btnMarkRead = overlayView.findViewById<Button>(R.id.btn_sms_mark_read)
            val btnReply = overlayView.findViewById<Button>(R.id.btn_sms_reply)

            senderTv?.text = senderName
            numberTv?.text = senderNumber
            bodyTv?.text = body

            btnMarkRead?.setOnClickListener {
                dismissSmsOverlay(context)
                XenoNotificationHelper.cancelSmsNotification(context, threadId)
            }

            btnReply?.setOnClickListener {
                dismissSmsOverlay(context)
                XenoNotificationHelper.cancelSmsNotification(context, threadId)

                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("open_tab", "MESSAGES")
                    putExtra("thread_id", threadId)
                    putExtra("sender_number", senderNumber)
                }
                context.startActivity(intent)
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
                y = 50
            }

            windowManager.addView(overlayView, params)
            activeSmsOverlayView = overlayView

            // Auto dismiss SMS overlay after 8 seconds
            overlayView.postDelayed({
                dismissSmsOverlay(context)
            }, 8000)

        } catch (e: Exception) {
            Log.e("XenoOverlayService", "Failed to show SMS overlay: ${e.message}")
        }
    }

    fun dismissSmsOverlay(context: Context) {
        activeSmsOverlayView?.let { view ->
            try {
                val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                windowManager?.removeView(view)
            } catch (_: Exception) {}
        }
        activeSmsOverlayView = null
    }
}
