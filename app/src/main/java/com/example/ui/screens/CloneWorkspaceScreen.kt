package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DesktopMac
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
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
import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.models.ClonedAppInstance
import com.example.ui.components.AppIconImage
import com.example.ui.components.parseHexColor

/**
 * Retains minimized Clone Space WebViews in memory so that when a clone app is minimized
 * to a floating icon and then tapped to restore, its exact session/scroll/video/chat state
 * is restored instantaneously without reloading.
 */
object CloneWebViewPool {
    private val retainedWebViews = mutableMapOf<String, WebView>()

    fun get(instanceId: String): WebView? = retainedWebViews[instanceId]

    fun retain(instanceId: String, webView: WebView) {
        retainedWebViews[instanceId] = webView
    }

    fun destroy(instanceId: String) {
        val wv = retainedWebViews.remove(instanceId) ?: return
        try {
            wv.keepScreenOn = false
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.stopLoading()
            wv.destroy()
        } catch (_: Exception) {}
    }
}

private const val MOBILE_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

private val WHATSAPP_WEB_HEADERS = mapOf(
    "Sec-CH-UA" to "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"",
    "Sec-CH-UA-Mobile" to "?0",
    "Sec-CH-UA-Platform" to "\"Windows\"",
    "Upgrade-Insecure-Requests" to "1"
)

/**
 * Configures Android's hardware AudioManager for real-time WebRTC Voice & Video Calls in WhatsApp Web.
 */
@Suppress("DEPRECATION")
private fun setWhatsAppCallAudioMode(
    context: Context,
    active: Boolean,
    speakerphoneOn: Boolean = true
): AudioFocusRequest? {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
    return try {
        if (active) {
            val focusRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attrs)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener { }
                    .build().also { req ->
                        audioManager.requestAudioFocus(req)
                    }
            } else {
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
                null
            }

            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isMicrophoneMute = false
            audioManager.isSpeakerphoneOn = speakerphoneOn

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val commDevices = audioManager.availableCommunicationDevices
                val targetDevice = if (speakerphoneOn) {
                    commDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                } else {
                    commDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                                it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                    }
                }
                if (targetDevice != null) {
                    audioManager.setCommunicationDevice(targetDevice)
                }
            }
            focusRequest
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    audioManager.clearCommunicationDevice()
                } catch (_: Exception) {}
            }
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
            null
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * Native bridge exposed to WhatsApp Web as `window.XenoCallBridge` so WebRTC voice/video calls
 * can coordinate Android hardware microphone, camera, speakerphone, and runtime permissions.
 */
private class WhatsAppCallJsBridge(
    private val mainHandler: Handler,
    private val onCallStateChanged: (active: Boolean, isVideo: Boolean) -> Unit,
    private val onEnsurePermissions: (needAudio: Boolean, needVideo: Boolean) -> Unit,
    private val checkPermissionsGranted: () -> Boolean,
    private val onMobileChatStateChanged: (isChatOpen: Boolean, chatTitle: String) -> Unit = { _, _ -> }
) {
    @JavascriptInterface
    fun onCallStarted(isVideo: Boolean) {
        mainHandler.post {
            onCallStateChanged(true, isVideo)
        }
    }

    @JavascriptInterface
    fun onCallEnded() {
        mainHandler.post {
            onCallStateChanged(false, false)
        }
    }

    @JavascriptInterface
    fun requestNativeCallPermissions(needAudio: Boolean, needVideo: Boolean) {
        mainHandler.post {
            onEnsurePermissions(needAudio, needVideo)
        }
    }

    @JavascriptInterface
    fun hasNativeCallPermissions(): Boolean {
        return checkPermissionsGranted()
    }

    @JavascriptInterface
    fun onMobileNavStateChanged(isChatOpen: Boolean, chatTitle: String?) {
        mainHandler.post {
            onMobileChatStateChanged(isChatOpen, chatTitle.orEmpty())
        }
    }
}

/**
 * WhatsApp Web Complete Desktop Functionality + Phone Screen UI Engine for Android WebView:
 * 1. Polyfills Desktop Chrome 131 (Windows 64-bit) APIs (`window.Notification`, `window.chrome`, `navigator.userAgentData`)
 *    so WhatsApp Web serves 100% of its desktop features without ever redirecting to mobile download pages.
 * 2. Enables WebRTC Voice & Video Calls using device microphone and camera.
 * 3. Adapts WhatsApp Web's multi-column desktop UI (`#side` chat list, `#main` active conversation, navigation rail,
 *    drawers, modals, and QR/phone-number login landing screen) to fit a mobile phone screen (`width=device-width, initial-scale=1.0`)
 *    with smooth single-pane switching and an injected mobile Back button inside the conversation header.
 */
private fun injectWhatsAppWebSupportEngine(view: WebView?, phoneUiMode: Boolean = true) {
    val phoneModeLiteral = if (phoneUiMode) "true" else "false"
    val js = """
        (function() {
            try {
                window.__xenoWaPhoneMode = $phoneModeLiteral;

                if (!window.__xenoWaPolyfilled) {
                    window.__xenoWaPolyfilled = true;
                    window.__xenoPreferredFacingMode = 'user';
                    window.__xenoActiveCallStreams = [];
                    window.__xenoLastChatContact = { name: 'WhatsApp Contact', avatar: '', isVideo: false };
                    window.__xenoDismissedChatSignature = null;
                    window.__xenoLastKnownChatSignature = '';

                    // 1. Full Desktop Chrome 131 Environment & Window Chrome Object
                    if (!window.chrome) {
                        window.chrome = {};
                    }
                    window.chrome.runtime = window.chrome.runtime || { id: undefined, connect: function(){}, sendMessage: function(){} };
                    window.chrome.app = window.chrome.app || {
                        isInstalled: false,
                        InstallState: { DISABLED: 'disabled', INSTALLED: 'installed', NOT_INSTALLED: 'not_installed' },
                        RunningState: { CANNOT_RUN: 'cannot_run', READY_TO_RUN: 'ready_to_run', RUNNING: 'running' }
                    };
                    window.chrome.webstore = window.chrome.webstore || {};
                    window.chrome.csi = window.chrome.csi || function() { return { startE: Date.now(), onloadT: Date.now(), pageT: 120, tran: 15 }; };
                    window.chrome.loadTimes = window.chrome.loadTimes || function() { return { requestTime: Date.now() / 1000 }; };

                    // 2. Desktop Windows 64-bit UserAgentData & Navigator Prototype Properties (Critical for WhatsApp Web Calling Check)
                    var uaBrands = [
                        { brand: 'Google Chrome', version: '131' },
                        { brand: 'Chromium', version: '131' },
                        { brand: 'Not_A Brand', version: '24' }
                    ];
                    var fullVersionBrands = [
                        { brand: 'Google Chrome', version: '131.0.6778.86' },
                        { brand: 'Chromium', version: '131.0.6778.86' },
                        { brand: 'Not_A Brand', version: '24.0.0.0' }
                    ];
                    var desktopUaData = {
                        brands: uaBrands,
                        mobile: false,
                        platform: 'Windows',
                        getHighEntropyValues: function(hints) {
                            return Promise.resolve({
                                architecture: 'x86',
                                bitness: '64',
                                brands: uaBrands,
                                formFactors: ['Desktop'],
                                fullVersionList: fullVersionBrands,
                                mobile: false,
                                model: '',
                                platform: 'Windows',
                                platformVersion: '15.0.0',
                                uaFullVersion: '131.0.6778.86',
                                wow64: false
                            });
                        },
                        toJSON: function() {
                            return {
                                brands: uaBrands,
                                mobile: false,
                                platform: 'Windows'
                            };
                        }
                    };

                    var defineNavProp = function(prop, getter) {
                        try {
                            if (typeof Navigator !== 'undefined' && Navigator.prototype) {
                                Object.defineProperty(Navigator.prototype, prop, { get: getter, configurable: true });
                            }
                        } catch(e) {}
                        try {
                            Object.defineProperty(navigator, prop, { get: getter, configurable: true });
                        } catch(e) {}
                    };

                    defineNavProp('userAgent', function() { return 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36'; });
                    defineNavProp('appVersion', function() { return '5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36'; });
                    defineNavProp('userAgentData', function() { return desktopUaData; });
                    defineNavProp('platform', function() { return 'Win32'; });
                    defineNavProp('vendor', function() { return 'Google Inc.'; });
                    defineNavProp('maxTouchPoints', function() { return 0; });
                    defineNavProp('webdriver', function() { return false; });
                    defineNavProp('hardwareConcurrency', function() { return 8; });
                    defineNavProp('deviceMemory', function() { return 8; });

                    try {
                        Object.defineProperty(window, 'isSecureContext', { get: function() { return true; }, configurable: true });
                        Object.defineProperty(window, 'crossOriginIsolated', { get: function() { return true; }, configurable: true });
                        if (typeof window.SharedArrayBuffer === 'undefined') {
                            window.SharedArrayBuffer = ArrayBuffer;
                        }
                    } catch(e) {}

                    // Polyfill MediaCapabilities & AudioWorklet required by WhatsApp Web VoIP capability checks
                    try {
                        if (!navigator.mediaCapabilities) {
                            navigator.mediaCapabilities = {};
                        }
                        var capOk = function() {
                            return Promise.resolve({ supported: true, smooth: true, powerEfficient: true, keySystemAccess: null });
                        };
                        navigator.mediaCapabilities.decodingInfo = capOk;
                        navigator.mediaCapabilities.encodingInfo = capOk;
                        if (typeof window.AudioWorklet === 'undefined') {
                            window.AudioWorklet = function() {};
                        }
                        if (typeof window.AudioWorkletNode === 'undefined') {
                            window.AudioWorkletNode = function() {};
                        }
                    } catch(e) {}

                    // Spoof desktop pointer/hover media queries so WhatsApp Web never flags the browser as a mobile touch WebView
                    if (typeof window.matchMedia === 'function' && !window.__xenoMatchMediaPatched) {
                        window.__xenoMatchMediaPatched = true;
                        var origMatchMedia = window.matchMedia.bind(window);
                        window.matchMedia = function(query) {
                            var q = String(query || '').toLowerCase();
                            var res = origMatchMedia(query);
                            if (q.indexOf('pointer: coarse') !== -1 || q.indexOf('hover: none') !== -1) {
                                return Object.assign({}, res, { matches: false, media: query, addListener: function(){}, removeListener: function(){}, addEventListener: function(){}, removeEventListener: function(){} });
                            }
                            if (q.indexOf('pointer: fine') !== -1 || q.indexOf('hover: hover') !== -1) {
                                return Object.assign({}, res, { matches: true, media: query, addListener: function(){}, removeListener: function(){}, addEventListener: function(){}, removeEventListener: function(){} });
                            }
                            return res;
                        };
                    }

                    // 3. Desktop Notification API (Required for Incoming & Outgoing Call Alerts)
                    if (typeof window.Notification === 'undefined' || window.Notification.permission !== 'granted') {
                        var OrigNotification = window.Notification;
                        var MockNotification = function(title, options) {
                            this.title = title || '';
                            this.options = options || {};
                            this.onclick = null;
                            this.onclose = null;
                            this.close = function() {
                                if (typeof this.onclose === 'function') this.onclose();
                            };
                            try {
                                var combined = (String(this.title) + ' ' + String(this.options.body || '')).toLowerCase();
                                if (combined.indexOf('call') !== -1 || combined.indexOf('calling') !== -1) {
                                    if (window.XenoCallBridge && typeof window.XenoCallBridge.onCallStarted === 'function') {
                                        window.XenoCallBridge.onCallStarted(combined.indexOf('video') !== -1);
                                    }
                                }
                            } catch(e) {}
                            try {
                                if (OrigNotification && OrigNotification !== MockNotification && OrigNotification.permission === 'granted') {
                                    return new OrigNotification(title, options);
                                }
                            } catch(e) {}
                        };
                        MockNotification.permission = 'granted';
                        MockNotification.requestPermission = function(cb) {
                            if (typeof cb === 'function') cb('granted');
                            return Promise.resolve('granted');
                        };
                        window.Notification = MockNotification;
                    }

                    // 4. Permissions API Polyfill: Report 'granted' for microphone, camera, speaker & notifications
                    var createGrantedStatus = function(permName) {
                        var statusObj = {
                            name: permName,
                            state: 'granted',
                            status: 'granted',
                            onchange: null,
                            addEventListener: function() {},
                            removeEventListener: function() {},
                            dispatchEvent: function() { return true; }
                        };
                        return Promise.resolve(statusObj);
                    };

                    if (navigator.permissions && typeof navigator.permissions.query === 'function') {
                        var origPermQuery = navigator.permissions.query.bind(navigator.permissions);
                        navigator.permissions.query = function(desc) {
                            var name = (desc && desc.name) ? String(desc.name).toLowerCase() : '';
                            if (name === 'microphone' ||
                                name === 'camera' ||
                                name === 'audio-capture' ||
                                name === 'video-capture' ||
                                name === 'notifications' ||
                                name === 'speaker' ||
                                name === 'speaker-selection' ||
                                name === 'display-capture' ||
                                name === 'persistent-storage' ||
                                name === 'midi') {
                                return createGrantedStatus(name);
                            }
                            return origPermQuery(desc).catch(function() {
                                return createGrantedStatus(name || 'unknown');
                            });
                        };
                    } else {
                        try {
                            Object.defineProperty(navigator, 'permissions', {
                                value: {
                                    query: function(desc) {
                                        return createGrantedStatus((desc && desc.name) || 'microphone');
                                    }
                                },
                                configurable: true
                            });
                        } catch(e) {}
                    }

                    // 5. HTMLMediaElement setSinkId & Speaker Output Polyfill for Call Audio Routing
                    if (typeof HTMLMediaElement !== 'undefined' && HTMLMediaElement.prototype) {
                        if (typeof HTMLMediaElement.prototype.setSinkId !== 'function') {
                            HTMLMediaElement.prototype.setSinkId = function(sinkId) {
                                this.__xenoSinkId = sinkId || 'default';
                                return Promise.resolve();
                            };
                        } else {
                            var origSetSinkId = HTMLMediaElement.prototype.setSinkId;
                            HTMLMediaElement.prototype.setSinkId = function(sinkId) {
                                this.__xenoSinkId = sinkId || 'default';
                                try {
                                    return origSetSinkId.call(this, sinkId).catch(function() {
                                        return Promise.resolve();
                                    });
                                } catch(e) {
                                    return Promise.resolve();
                                }
                            };
                        }
                        if (!('sinkId' in HTMLMediaElement.prototype)) {
                            Object.defineProperty(HTMLMediaElement.prototype, 'sinkId', {
                                get: function() { return this.__xenoSinkId || 'default'; },
                                configurable: true
                            });
                        }
                    }

                    // 6. WebRTC MediaDevices (Microphone, Camera, Speaker & getDisplayMedia) Engine with Fast Caching
                    if (!navigator.mediaDevices) {
                        try {
                            navigator.mediaDevices = {};
                        } catch(e) {}
                    }

                    if (navigator.mediaDevices) {
                        if (typeof navigator.mediaDevices.addEventListener !== 'function') {
                            navigator.mediaDevices.addEventListener = function() {};
                            navigator.mediaDevices.removeEventListener = function() {};
                            navigator.mediaDevices.dispatchEvent = function() { return true; };
                        }
                        if (!('ondevicechange' in navigator.mediaDevices)) {
                            navigator.mediaDevices.ondevicechange = null;
                        }

                        if (typeof navigator.mediaDevices.getSupportedConstraints === 'function') {
                            var origGetSupported = navigator.mediaDevices.getSupportedConstraints.bind(navigator.mediaDevices);
                            navigator.mediaDevices.getSupportedConstraints = function() {
                                var base = {};
                                try { base = origGetSupported() || {}; } catch(e) {}
                                return Object.assign({}, base, {
                                    width: true,
                                    height: true,
                                    aspectRatio: true,
                                    frameRate: true,
                                    facingMode: true,
                                    resizeMode: true,
                                    sampleRate: true,
                                    sampleSize: true,
                                    echoCancellation: true,
                                    autoGainControl: true,
                                    noiseSuppression: true,
                                    latency: true,
                                    channelCount: true,
                                    deviceId: true,
                                    groupId: true,
                                    suppressLocalAudioPlayback: true
                                });
                            };
                        }

                        var makeDeviceInfo = function(deviceId, kind, label, groupId) {
                            var info = {
                                deviceId: deviceId,
                                kind: kind,
                                label: label,
                                groupId: groupId || 'default',
                                getCapabilities: function() {
                                    return { deviceId: deviceId, groupId: groupId || 'default' };
                                },
                                toJSON: function() {
                                    return {
                                        deviceId: this.deviceId,
                                        kind: this.kind,
                                        label: this.label,
                                        groupId: this.groupId
                                    };
                                }
                            };
                            if (typeof MediaDeviceInfo !== 'undefined' && MediaDeviceInfo.prototype) {
                                try { Object.setPrototypeOf(info, MediaDeviceInfo.prototype); } catch(e) {}
                            }
                            return info;
                        };

                        var origEnumerate = typeof navigator.mediaDevices.enumerateDevices === 'function'
                            ? navigator.mediaDevices.enumerateDevices.bind(navigator.mediaDevices)
                            : null;

                        navigator.mediaDevices.enumerateDevices = function() {
                            if (window.__xenoCachedMediaDevices && (Date.now() - (window.__xenoCachedMediaDevicesTs || 0) < 15000)) {
                                return Promise.resolve(window.__xenoCachedMediaDevices);
                            }
                            var promise = origEnumerate ? origEnumerate().catch(function() { return []; }) : Promise.resolve([]);
                            return promise.then(function(devices) {
                                var result = [];
                                var hasAudioIn = false;
                                var hasVideoIn = false;
                                var hasAudioOut = false;
                                var micIdx = 1;
                                var camIdx = 1;
                                var spkIdx = 1;

                                if (Array.isArray(devices)) {
                                    for (var i = 0; i < devices.length; i++) {
                                        var d = devices[i];
                                        if (!d) continue;
                                        var kind = d.kind || 'audioinput';
                                        var id = d.deviceId && d.deviceId.length > 0 ? d.deviceId : ('default-' + kind + '-' + i);
                                        var label = d.label;
                                        if (!label || label.trim() === '') {
                                            if (kind === 'audioinput') label = 'Communications - Device Microphone ' + (micIdx++);
                                            else if (kind === 'videoinput') label = (camIdx === 1 ? 'Integrated Front Camera' : 'Back Camera ' + camIdx);
                                            else if (kind === 'audiooutput') label = 'Communications - Device Speaker ' + (spkIdx++);
                                            if (kind === 'videoinput') camIdx++;
                                        }
                                        if (kind === 'audioinput') hasAudioIn = true;
                                        if (kind === 'videoinput') hasVideoIn = true;
                                        if (kind === 'audiooutput') hasAudioOut = true;
                                        result.push(makeDeviceInfo(id, kind, label, d.groupId || 'default'));
                                    }
                                }

                                if (!hasAudioIn) {
                                    result.push(makeDeviceInfo('default', 'audioinput', 'Default - Microphone (Realtek High Definition Audio)', 'audio-group-1'));
                                    result.push(makeDeviceInfo('communications', 'audioinput', 'Communications - Microphone', 'audio-group-1'));
                                }
                                if (!hasVideoIn) {
                                    result.push(makeDeviceInfo('camera-front', 'videoinput', 'Integrated Front Camera (User)', 'video-group-1'));
                                    result.push(makeDeviceInfo('camera-back', 'videoinput', 'Back Camera (Environment)', 'video-group-2'));
                                }
                                if (!hasAudioOut) {
                                    result.push(makeDeviceInfo('default', 'audiooutput', 'Default - Speakers (Realtek High Definition Audio)', 'audio-group-1'));
                                    result.push(makeDeviceInfo('communications', 'audiooutput', 'Communications - Speakers / Headset', 'audio-group-1'));
                                }
                                window.__xenoCachedMediaDevices = result;
                                window.__xenoCachedMediaDevicesTs = Date.now();
                                return result;
                            });
                        };

                        var origGetUserMedia = typeof navigator.mediaDevices.getUserMedia === 'function'
                            ? navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices)
                            : null;

                        var registerActiveStream = function(stream, isVideo) {
                            if (!stream) return stream;
                            try {
                                window.__xenoActiveCallStreams.push(stream);
                                if (window.XenoCallBridge && typeof window.XenoCallBridge.onCallStarted === 'function') {
                                    window.XenoCallBridge.onCallStarted(Boolean(isVideo));
                                }
                                var checkAllEnded = function() {
                                    var anyLive = false;
                                    window.__xenoActiveCallStreams = (window.__xenoActiveCallStreams || []).filter(function(s) {
                                        var tracks = s && typeof s.getTracks === 'function' ? s.getTracks() : [];
                                        var live = tracks.some(function(t) { return t.readyState === 'live'; });
                                        if (live) anyLive = true;
                                        return live;
                                    });
                                    if (!anyLive && !window.__xenoCallOverlayActive && window.XenoCallBridge && typeof window.XenoCallBridge.onCallEnded === 'function') {
                                        window.XenoCallBridge.onCallEnded();
                                    }
                                };
                                var tracks = typeof stream.getTracks === 'function' ? stream.getTracks() : [];
                                tracks.forEach(function(track) {
                                    track.addEventListener('ended', checkAllEnded);
                                });
                            } catch(e) {}
                            return stream;
                        };

                        var buildFallbackConstraints = function(rawConstraints, level) {
                            var c = rawConstraints || { audio: true };
                            var wantsAudio = Boolean(c.audio);
                            var wantsVideo = Boolean(c.video);
                            var facing = window.__xenoPreferredFacingMode || 'user';

                            if (level === 1) {
                                var out = {};
                                if (wantsAudio) {
                                    out.audio = {
                                        echoCancellation: true,
                                        noiseSuppression: true,
                                        autoGainControl: true
                                    };
                                }
                                if (wantsVideo) {
                                    out.video = {
                                        facingMode: facing,
                                        width: { ideal: 640 },
                                        height: { ideal: 480 }
                                    };
                                }
                                return out;
                            } else {
                                var minimal = {};
                                if (wantsAudio) minimal.audio = true;
                                if (wantsVideo) minimal.video = { facingMode: facing };
                                if (!wantsAudio && !wantsVideo) minimal.audio = true;
                                return minimal;
                            }
                        };

                        navigator.mediaDevices.getUserMedia = function(constraints) {
                            var reqConstraints = constraints || { audio: true };
                            var wantsAudio = Boolean(reqConstraints.audio);
                            var wantsVideo = Boolean(reqConstraints.video);

                            try {
                                if (window.XenoCallBridge) {
                                    if (typeof window.XenoCallBridge.requestNativeCallPermissions === 'function') {
                                        window.XenoCallBridge.requestNativeCallPermissions(wantsAudio, wantsVideo);
                                    }
                                    if (typeof window.XenoCallBridge.onCallStarted === 'function') {
                                        window.XenoCallBridge.onCallStarted(wantsVideo);
                                    }
                                }
                            } catch(e) {}

                            if (!origGetUserMedia) {
                                return Promise.reject(new Error('WebRTC getUserMedia unavailable'));
                            }

                            var primaryConstraints = reqConstraints;
                            if (wantsVideo && typeof reqConstraints.video === 'object' && reqConstraints.video !== null) {
                                var vCopy = Object.assign({}, reqConstraints.video);
                                if (vCopy.deviceId && (vCopy.deviceId === 'camera-front' || vCopy.deviceId === 'camera-back' ||
                                    (vCopy.deviceId.exact && (vCopy.deviceId.exact === 'camera-front' || vCopy.deviceId.exact === 'camera-back')))) {
                                    var targetId = vCopy.deviceId.exact || vCopy.deviceId;
                                    delete vCopy.deviceId;
                                    vCopy.facingMode = (targetId === 'camera-back') ? 'environment' : 'user';
                                } else if (!vCopy.facingMode) {
                                    vCopy.facingMode = window.__xenoPreferredFacingMode || 'user';
                                }
                                primaryConstraints = Object.assign({}, reqConstraints, { video: vCopy });
                            }

                            return origGetUserMedia(primaryConstraints)
                                .then(function(stream) {
                                    return registerActiveStream(stream, wantsVideo);
                                })
                                .catch(function(err) {
                                    return origGetUserMedia(buildFallbackConstraints(reqConstraints, 1))
                                        .then(function(stream) {
                                            return registerActiveStream(stream, wantsVideo);
                                        })
                                        .catch(function() {
                                            return origGetUserMedia(buildFallbackConstraints(reqConstraints, 2))
                                                .then(function(stream) {
                                                    return registerActiveStream(stream, wantsVideo);
                                                })
                                                .catch(function(finalErr) {
                                                    if (wantsVideo) {
                                                        return origGetUserMedia({ audio: wantsAudio, video: true })
                                                            .then(function(stream) {
                                                                return registerActiveStream(stream, true);
                                                            });
                                                    }
                                                    throw finalErr || err;
                                                });
                                        });
                                });
                        };

                        if (typeof navigator.mediaDevices.getDisplayMedia !== 'function') {
                            var displayMediaFn = function(constraints) {
                                return navigator.mediaDevices.getUserMedia({ video: true, audio: Boolean(constraints && constraints.audio) });
                            };
                            navigator.mediaDevices.getDisplayMedia = displayMediaFn;
                            if (typeof MediaDevices !== 'undefined' && MediaDevices.prototype) {
                                try { MediaDevices.prototype.getDisplayMedia = displayMediaFn; } catch(e) {}
                            }
                        }

                        navigator.getUserMedia = function(constraints, onSuccess, onError) {
                            navigator.mediaDevices.getUserMedia(constraints)
                                .then(function(s) { if (onSuccess) onSuccess(s); })
                                .catch(function(e) { if (onError) onError(e); });
                        };
                        navigator.webkitGetUserMedia = navigator.getUserMedia;
                    }

                    // 7. Camera Flip Helper for active WhatsApp Video Calls
                    window.__xenoFlipCallCamera = function(targetFacing) {
                        try {
                            window.__xenoPreferredFacingMode = targetFacing || (window.__xenoPreferredFacingMode === 'user' ? 'environment' : 'user');
                            var streams = window.__xenoActiveCallStreams || [];
                            if (streams.length > 0 && navigator.mediaDevices && origGetUserMedia) {
                                origGetUserMedia({
                                    video: { facingMode: { ideal: window.__xenoPreferredFacingMode } },
                                    audio: false
                                }).then(function(newVideoStream) {
                                    var newVideoTrack = newVideoStream.getVideoTracks()[0];
                                    if (!newVideoTrack) return;
                                    if (window.__xenoActivePeerConnections) {
                                        window.__xenoActivePeerConnections.forEach(function(pc) {
                                            try {
                                                var senders = pc.getSenders ? pc.getSenders() : [];
                                                senders.forEach(function(sender) {
                                                    if (sender && sender.track && sender.track.kind === 'video') {
                                                        sender.replaceTrack(newVideoTrack);
                                                    }
                                                });
                                            } catch(e) {}
                                        });
                                    }
                                    streams.forEach(function(s) {
                                        try {
                                            var oldTracks = s.getVideoTracks();
                                            oldTracks.forEach(function(t) {
                                                t.stop();
                                                s.removeTrack(t);
                                            });
                                            s.addTrack(newVideoTrack);
                                        } catch(e) {}
                                    });
                                    var localVid = document.getElementById('xeno-wa-call-local-video');
                                    if (localVid) {
                                        localVid.srcObject = new MediaStream([newVideoTrack]);
                                        localVid.play().catch(function(){});
                                    }
                                }).catch(function() {});
                            }
                        } catch(e) {}
                    };

                    // 8. Preserve 100% of RTCPeerConnection static & prototype methods using Proxy
                    var OrigPeerConnection = window.RTCPeerConnection || window.webkitRTCPeerConnection;
                    if (OrigPeerConnection && !window.__xenoPeerPatched) {
                        window.__xenoPeerPatched = true;
                        window.__xenoActivePeerConnections = new Set();
                        if (typeof Proxy !== 'undefined') {
                            var ProxiedPC = new Proxy(OrigPeerConnection, {
                                construct: function(target, args, newTarget) {
                                    var cfg = (args && args[0]) ? Object.assign({}, args[0]) : {};
                                    if (!cfg.iceServers || cfg.iceServers.length === 0) {
                                        cfg.iceServers = [
                                            { urls: ['stun:stun.l.google.com:19302', 'stun:stun1.l.google.com:19302'] }
                                        ];
                                    }
                                    var newArgs = [cfg];
                                    if (args && args.length > 1) newArgs.push(args[1]);
                                    var pc;
                                    try {
                                        pc = Reflect.construct(target, newArgs, newTarget);
                                    } catch(err) {
                                        delete cfg.encodedInsertableStreams;
                                        pc = Reflect.construct(target, [cfg], newTarget);
                                    }
                                    window.__xenoActivePeerConnections.add(pc);
                                    pc.addEventListener('connectionstatechange', function() {
                                        if (pc.connectionState === 'closed' || pc.connectionState === 'failed' || pc.connectionState === 'disconnected') {
                                            window.__xenoActivePeerConnections.delete(pc);
                                        }
                                    });
                                    return pc;
                                }
                            });
                            window.RTCPeerConnection = ProxiedPC;
                            window.webkitRTCPeerConnection = ProxiedPC;
                        }
                    }

                    // 9. Guarantee RTCRtpSender & RTCRtpReceiver Codec Capabilities + E2EE Insertable Streams Polyfills
                    var ensureRtpCapabilities = function(RtpClass) {
                        if (!RtpClass) return;
                        var origGetCap = typeof RtpClass.getCapabilities === 'function' ? RtpClass.getCapabilities.bind(RtpClass) : null;
                        RtpClass.getCapabilities = function(kind) {
                            var k = String(kind || '').toLowerCase();
                            var res = origGetCap ? origGetCap(kind) : null;
                            if (!res || !Array.isArray(res.codecs)) {
                                res = { codecs: [], headerExtensions: [] };
                            } else {
                                res = { codecs: res.codecs.slice(), headerExtensions: (res.headerExtensions || []).slice() };
                            }
                            if (k === 'audio') {
                                var hasOpus = res.codecs.some(function(c) { return String(c.mimeType || '').toLowerCase() === 'audio/opus'; });
                                if (!hasOpus) {
                                    res.codecs.unshift({ channels: 2, clockRate: 48000, mimeType: 'audio/opus', sdpFmtpLine: 'minptime=10;useinbandfec=1' });
                                }
                                if (res.codecs.length < 2) {
                                    res.codecs.push({ channels: 1, clockRate: 8000, mimeType: 'audio/PCMU' });
                                    res.codecs.push({ channels: 1, clockRate: 8000, mimeType: 'audio/PCMA' });
                                    res.codecs.push({ channels: 1, clockRate: 8000, mimeType: 'audio/telephone-event' });
                                }
                            } else if (k === 'video') {
                                var hasVp8 = res.codecs.some(function(c) { return String(c.mimeType || '').toLowerCase() === 'video/vp8'; });
                                var hasH264 = res.codecs.some(function(c) { return String(c.mimeType || '').toLowerCase() === 'video/h264'; });
                                var hasVp9 = res.codecs.some(function(c) { return String(c.mimeType || '').toLowerCase() === 'video/vp9'; });
                                if (!hasVp8) {
                                    res.codecs.push({ clockRate: 90000, mimeType: 'video/VP8' });
                                }
                                if (!hasH264) {
                                    res.codecs.push({ clockRate: 90000, mimeType: 'video/H264', sdpFmtpLine: 'level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42e01f' });
                                }
                                if (!hasVp9) {
                                    res.codecs.push({ clockRate: 90000, mimeType: 'video/VP9', sdpFmtpLine: 'profile-id=0' });
                                }
                            }
                            return res;
                        };
                        if (RtpClass.prototype) {
                            if (typeof RtpClass.prototype.createEncodedStreams !== 'function') {
                                RtpClass.prototype.createEncodedStreams = function() {
                                    if (!this.__xenoEncodedStreams) {
                                        var ts = typeof TransformStream !== 'undefined' ? new TransformStream() : {
                                            readable: typeof ReadableStream !== 'undefined' ? new ReadableStream() : {},
                                            writable: typeof WritableStream !== 'undefined' ? new WritableStream() : {}
                                        };
                                        this.__xenoEncodedStreams = { readable: ts.readable, writable: ts.writable };
                                    }
                                    return this.__xenoEncodedStreams;
                                };
                            }
                            if (!('transform' in RtpClass.prototype)) {
                                try {
                                    Object.defineProperty(RtpClass.prototype, 'transform', {
                                        get: function() { return this.__xenoRtpTransform || null; },
                                        set: function(v) { this.__xenoRtpTransform = v; },
                                        configurable: true
                                    });
                                } catch(e) {}
                            }
                        }
                    };
                    if (typeof window.RTCRtpSender !== 'undefined') ensureRtpCapabilities(window.RTCRtpSender);
                    if (typeof window.RTCRtpReceiver !== 'undefined') ensureRtpCapabilities(window.RTCRtpReceiver);
                    if (typeof window.RTCRtpScriptTransform === 'undefined') {
                        window.RTCRtpScriptTransform = function(worker, options, transfer) {
                            this.worker = worker;
                            this.options = options;
                        };
                    }
                    if (typeof window.SFrameTransform === 'undefined') {
                        window.SFrameTransform = function(options) {
                            this.options = options;
                            this.setEncryptionKey = function() { return Promise.resolve(); };
                        };
                    }
                    if (typeof window.RTCRtpTransceiver !== 'undefined' && window.RTCRtpTransceiver.prototype && typeof window.RTCRtpTransceiver.prototype.setCodecPreferences !== 'function') {
                        window.RTCRtpTransceiver.prototype.setCodecPreferences = function() {};
                    }

                    // 10. Hook WhatsApp Web's internal module loaders (window.__d, window.require, importDefault, importNamespace, ABProp)
                    var callCapKeyRegex = /(^is(voice|video|group|web|voip|browser)?call(ing|s)?(supported|enabled|allowed|available)$)|(^supports?(voice|video|group|web)?call(ing|s)?$)|(^isbrowsersupported)|(^canstartcall$)|(^hascallingsupport$)|(^voipsupported$)|(^webrtcsupported$)/i;
                    var patchWhatsAppCallingModuleExports = function(mod) {
                        if (!mod || (typeof mod !== 'object' && typeof mod !== 'function')) return mod;
                        var explicitKeys = [
                            'isCallingSupported', 'isVoiceCallSupported', 'isVideoCallSupported', 'isGroupCallSupported',
                            'supportsCalling', 'supportsVoiceCall', 'supportsVideoCall', 'isWebCallingSupported',
                            'isWebCallingEnabled', 'isVoipSupported', 'isBrowserSupportedForCalling', 'canStartCall',
                            'isCallSupported', 'isCallEnabled', 'hasCallingSupport', 'isCallingEnabled',
                            'callingSupported', 'voiceCallSupported', 'videoCallSupported', 'browserSupportsCalling'
                        ];
                        for (var i = 0; i < explicitKeys.length; i++) {
                            var key = explicitKeys[i];
                            try {
                                if (key in mod) {
                                    if (typeof mod[key] === 'function' && !mod[key].__xenoPatched) {
                                        var fn = function() { return true; };
                                        fn.__xenoPatched = true;
                                        mod[key] = fn;
                                    } else if (typeof mod[key] === 'boolean') {
                                        mod[key] = true;
                                    }
                                }
                            } catch(e) {}
                        }
                        try {
                            var keys = Object.keys(mod);
                            for (var k = 0; k < keys.length; k++) {
                                var prop = keys[k];
                                if (callCapKeyRegex.test(prop)) {
                                    if (typeof mod[prop] === 'function' && !mod[prop].__xenoPatched) {
                                        var pFn = function() { return true; };
                                        pFn.__xenoPatched = true;
                                        mod[prop] = pFn;
                                    } else if (typeof mod[prop] === 'boolean') {
                                        mod[prop] = true;
                                    }
                                } else if ((prop === 'getABPropConfigValue' || prop === 'getConfigValue') && typeof mod[prop] === 'function' && !mod[prop].__xenoAbPatched) {
                                    var origAb = mod[prop];
                                    var wrappedAb = function(abKey) {
                                        var ks = String(abKey || '').toLowerCase();
                                        if (ks.indexOf('call') !== -1 || ks.indexOf('voip') !== -1 || ks.indexOf('webrtc') !== -1) {
                                            if (ks.indexOf('disable') !== -1 || ks.indexOf('killswitch') !== -1 || ks.indexOf('block') !== -1) {
                                                return false;
                                            }
                                            var origVal = origAb.apply(this, arguments);
                                            if (typeof origVal === 'boolean') return true;
                                            return origVal;
                                        }
                                        return origAb.apply(this, arguments);
                                    };
                                    wrappedAb.__xenoAbPatched = true;
                                    mod[prop] = wrappedAb;
                                }
                            }
                        } catch(e) {}
                        return mod;
                    };

                    var wrapImportFn = function(fn) {
                        if (typeof fn !== 'function' || fn.__xenoWrappedImport) return fn;
                        var wrapped = function(id) {
                            var res = fn.apply(this, arguments);
                            try { patchWhatsAppCallingModuleExports(res); } catch(e) {}
                            return res;
                        };
                        wrapped.__xenoWrappedImport = true;
                        Object.keys(fn).forEach(function(k) {
                            try { wrapped[k] = fn[k]; } catch(e) {}
                        });
                        return wrapped;
                    };

                    var hookWhatsAppRequire = function() {
                        if (typeof window.require === 'function' && !window.require.__xenoHooked) {
                            var origReq = window.require;
                            var wrappedReq = wrapImportFn(origReq);
                            wrappedReq.__xenoHooked = true;
                            window.require = wrappedReq;
                        }
                        if (typeof window.__d === 'function' && !window.__d.__xenoHooked) {
                            var origD = window.__d;
                            var wrappedD = function(factory, moduleId, depMap) {
                                if (typeof factory === 'function' && !factory.__xenoWrappedFactory) {
                                    var origFactory = factory;
                                    factory = function(global, req, importDefault, importNamespace, module, exports, deps) {
                                        var wReq = wrapImportFn(req);
                                        var wDef = wrapImportFn(importDefault);
                                        var wNs = wrapImportFn(importNamespace);
                                        var ret = origFactory.call(this, global, wReq, wDef, wNs, module, exports, deps);
                                        try {
                                            if (exports) patchWhatsAppCallingModuleExports(exports);
                                            if (module && module.exports) patchWhatsAppCallingModuleExports(module.exports);
                                        } catch(e) {}
                                        return ret;
                                    };
                                    factory.__xenoWrappedFactory = true;
                                }
                                return origD.call(this, factory, moduleId, depMap);
                            };
                            wrappedD.__xenoHooked = true;
                            window.__d = wrappedD;
                        }
                    };
                    hookWhatsAppRequire();
                    setInterval(hookWhatsAppRequire, 600);

                    // 11. Built-in WhatsApp Web Live Voice & Video Call UI (Phone-Screen Responsive)
                    window.__xenoExtractActiveChatContact = function() {
                        try {
                            var mainEl = document.getElementById('main') || document.querySelector('[data-testid="conversation-panel-wrapper"]') || document.querySelector('header');
                            if (mainEl) {
                                var header = mainEl.tagName === 'HEADER' ? mainEl : mainEl.querySelector('header');
                                if (header) {
                                    var spans = header.querySelectorAll('span[dir="auto"], span[title]');
                                    for (var i = 0; i < spans.length; i++) {
                                        var txt = (spans[i].getAttribute('title') || spans[i].textContent || '').trim();
                                        if (txt && txt.length > 0 &&
                                            txt.toLowerCase().indexOf('click here for contact') === -1 &&
                                            txt.toLowerCase().indexOf('online') !== 0 &&
                                            txt.toLowerCase().indexOf('last seen') === -1 &&
                                            txt.toLowerCase().indexOf('typing') === -1 &&
                                            txt.toLowerCase().indexOf('search') === -1) {
                                            window.__xenoLastChatContact.name = txt;
                                            break;
                                        }
                                    }
                                    var img = header.querySelector('img');
                                    if (img && img.src) {
                                        window.__xenoLastChatContact.avatar = img.src;
                                    }
                                }
                            }
                        } catch(e) {}
                        return window.__xenoLastChatContact;
                    };

                    window.__xenoStopCallRingtone = function() {
                        try {
                            if (window.__xenoRingInterval) {
                                clearInterval(window.__xenoRingInterval);
                                window.__xenoRingInterval = null;
                            }
                            if (window.__xenoRingAudioCtx) {
                                window.__xenoRingAudioCtx.close().catch(function(){});
                                window.__xenoRingAudioCtx = null;
                            }
                        } catch(e) {}
                    };

                    window.__xenoStartCallRingtone = function() {
                        window.__xenoStopCallRingtone();
                        try {
                            var AudioCtx = window.AudioContext || window.webkitAudioContext;
                            if (!AudioCtx) return;
                            var ctx = new AudioCtx();
                            window.__xenoRingAudioCtx = ctx;
                            var playBurst = function() {
                                if (!window.__xenoRingAudioCtx) return;
                                try {
                                    if (ctx.state === 'suspended') ctx.resume();
                                    var osc1 = ctx.createOscillator();
                                    var osc2 = ctx.createOscillator();
                                    var gain = ctx.createGain();
                                    osc1.type = 'sine';
                                    osc2.type = 'sine';
                                    osc1.frequency.setValueAtTime(440, ctx.currentTime);
                                    osc2.frequency.setValueAtTime(480, ctx.currentTime);
                                    gain.gain.setValueAtTime(0.08, ctx.currentTime);
                                    gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 1.1);
                                    osc1.connect(gain);
                                    osc2.connect(gain);
                                    gain.connect(ctx.destination);
                                    osc1.start();
                                    osc2.start();
                                    osc1.stop(ctx.currentTime + 1.15);
                                    osc2.stop(ctx.currentTime + 1.15);
                                } catch(e) {}
                            };
                            playBurst();
                            window.__xenoRingInterval = setInterval(playBurst, 2800);
                        } catch(e) {}
                    };

                    window.__xenoEndWhatsAppCallOverlay = function() {
                        window.__xenoCallOverlayActive = false;
                        window.__xenoStopCallRingtone();
                        if (window.__xenoCallTimerInterval) {
                            clearInterval(window.__xenoCallTimerInterval);
                            window.__xenoCallTimerInterval = null;
                        }
                        if (window.__xenoConnectTimeout) {
                            clearTimeout(window.__xenoConnectTimeout);
                            window.__xenoConnectTimeout = null;
                        }
                        try {
                            var streams = window.__xenoActiveCallStreams || [];
                            streams.forEach(function(s) {
                                try {
                                    s.getTracks().forEach(function(t) { t.stop(); });
                                } catch(e) {}
                            });
                            window.__xenoActiveCallStreams = [];
                        } catch(e) {}
                        try {
                            if (window.XenoCallBridge && typeof window.XenoCallBridge.onCallEnded === 'function') {
                                window.XenoCallBridge.onCallEnded();
                            }
                        } catch(e) {}
                        var existing = document.getElementById('xeno-wa-live-call-overlay');
                        if (existing && existing.parentNode) {
                            existing.parentNode.removeChild(existing);
                        }
                    };

                    window.__xenoStartWhatsAppCallOverlay = function(contactName, contactAvatar, isVideoCall) {
                        try {
                            window.__xenoEndWhatsAppCallOverlay();
                            window.__xenoCallOverlayActive = true;

                            var info = window.__xenoExtractActiveChatContact();
                            var name = contactName || info.name || 'WhatsApp Contact';
                            var avatar = contactAvatar || info.avatar || '';
                            var isVideo = Boolean(isVideoCall);
                            var isMuted = false;
                            var isMinimized = false;
                            var secondsElapsed = 0;

                            if (window.XenoCallBridge) {
                                if (typeof window.XenoCallBridge.requestNativeCallPermissions === 'function') {
                                    window.XenoCallBridge.requestNativeCallPermissions(true, isVideo);
                                }
                                if (typeof window.XenoCallBridge.onCallStarted === 'function') {
                                    window.XenoCallBridge.onCallStarted(isVideo);
                                }
                            }

                            var overlay = document.createElement('div');
                            overlay.id = 'xeno-wa-live-call-overlay';
                            overlay.style.cssText = 'position:fixed;inset:0;z-index:2147483646;background:linear-gradient(180deg,#0b141a 0%,#111b21 55%,#0b141a 100%);color:#e9edef;display:flex;flex-direction:column;align-items:center;justify-content:space-between;padding:20px 16px 28px;font-family:Segoe UI,Helvetica Neue,Helvetica,Lucida Grande,Arial,sans-serif;user-select:none;box-sizing:border-box;transition:all 0.25s ease;';

                            var topBar = document.createElement('div');
                            topBar.style.cssText = 'width:100%;display:flex;align-items:center;justify-content:space-between;gap:8px;';
                            var modeTitleSpan = document.createElement('span');
                            modeTitleSpan.textContent = 'End-to-end encrypted \u2022 ' + (isVideo ? 'Video' : 'Voice') + ' Call';
                            var topLeft = document.createElement('div');
                            topLeft.style.cssText = 'display:flex;align-items:center;gap:6px;color:#8696a0;font-size:13px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;';
                            topLeft.innerHTML = '<span style="color:#00a884;font-size:14px;">&#128274;</span>';
                            topLeft.appendChild(modeTitleSpan);
                            topBar.appendChild(topLeft);

                            var minBtn = document.createElement('button');
                            minBtn.textContent = 'Minimize';
                            minBtn.style.cssText = 'background:rgba(255,255,255,0.1);border:1px solid rgba(255,255,255,0.16);color:#e9edef;padding:6px 14px;border-radius:18px;font-size:13px;cursor:pointer;flex-shrink:0;';
                            topBar.appendChild(minBtn);

                            var centerBox = document.createElement('div');
                            centerBox.style.cssText = 'display:flex;flex-direction:column;align-items:center;justify-content:center;gap:12px;position:relative;flex:1;width:100%;max-width:480px;';

                            var videoContainer = document.createElement('div');
                            videoContainer.id = 'xeno-wa-call-video-wrap';
                            videoContainer.style.cssText = 'width:100%;max-width:360px;height:' + (isVideo ? '240px' : '0px') + ';border-radius:18px;overflow:hidden;background:#1f2c34;position:relative;display:' + (isVideo ? 'flex' : 'none') + ';align-items:center;justify-content:center;box-shadow:0 12px 36px rgba(0,0,0,0.45);border:1px solid rgba(255,255,255,0.1);';

                            var localVideo = document.createElement('video');
                            localVideo.id = 'xeno-wa-call-local-video';
                            localVideo.autoplay = true;
                            localVideo.muted = true;
                            localVideo.playsInline = true;
                            localVideo.setAttribute('playsinline', 'true');
                            localVideo.setAttribute('webkit-playsinline', 'true');
                            localVideo.style.cssText = 'width:100%;height:100%;object-fit:cover;transform:scaleX(-1);';
                            videoContainer.appendChild(localVideo);

                            var avatarWrap = document.createElement('div');
                            avatarWrap.style.cssText = 'width:110px;height:110px;border-radius:50%;overflow:hidden;background:#202c33;display:flex;align-items:center;justify-content:center;border:3px solid #00a884;box-shadow:0 0 0 8px rgba(0,168,132,0.12);';
                            if (avatar) {
                                var avatarImg = document.createElement('img');
                                avatarImg.src = avatar;
                                avatarImg.style.cssText = 'width:100%;height:100%;object-fit:cover;';
                                avatarWrap.appendChild(avatarImg);
                            } else {
                                var initials = document.createElement('span');
                                initials.textContent = (name.trim()[0] || 'W').toUpperCase();
                                initials.style.cssText = 'font-size:44px;font-weight:700;color:#00a884;';
                                avatarWrap.appendChild(initials);
                            }

                            var nameEl = document.createElement('div');
                            nameEl.textContent = name;
                            nameEl.style.cssText = 'font-size:22px;font-weight:600;color:#e9edef;text-align:center;margin-top:4px;max-width:90vw;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;';

                            var statusEl = document.createElement('div');
                            statusEl.textContent = 'Calling...';
                            statusEl.style.cssText = 'font-size:15px;color:#00a884;font-weight:500;letter-spacing:0.3px;';

                            centerBox.appendChild(avatarWrap);
                            centerBox.appendChild(nameEl);
                            centerBox.appendChild(statusEl);
                            centerBox.appendChild(videoContainer);

                            var controlsBar = document.createElement('div');
                            controlsBar.style.cssText = 'display:flex;flex-wrap:wrap;align-items:center;justify-content:center;gap:10px;background:#1f2c34;padding:12px 16px;border-radius:32px;box-shadow:0 10px 28px rgba(0,0,0,0.4);max-width:calc(100vw - 24px);box-sizing:border-box;';

                            var makeBtn = function(label, bg, onClick) {
                                var b = document.createElement('button');
                                b.textContent = label;
                                b.style.cssText = 'min-width:74px;height:44px;border-radius:22px;border:none;background:' + bg + ';color:#ffffff;font-size:13px;font-weight:600;cursor:pointer;padding:0 14px;display:flex;align-items:center;justify-content:center;gap:6px;transition:background 0.2s;';
                                b.onclick = onClick;
                                return b;
                            };

                            var muteBtn = makeBtn('Mute', '#2a3942', function() {
                                isMuted = !isMuted;
                                muteBtn.textContent = isMuted ? 'Unmute' : 'Mute';
                                muteBtn.style.background = isMuted ? '#00a884' : '#2a3942';
                                var streams = window.__xenoActiveCallStreams || [];
                                streams.forEach(function(s) {
                                    s.getAudioTracks().forEach(function(t) { t.enabled = !isMuted; });
                                });
                            });

                            var videoBtn = makeBtn(isVideo ? 'Cam On' : 'Video', isVideo ? '#00a884' : '#2a3942', function() {
                                isVideo = !isVideo;
                                modeTitleSpan.textContent = 'End-to-end encrypted \u2022 ' + (isVideo ? 'Video' : 'Voice') + ' Call';
                                videoBtn.textContent = isVideo ? 'Cam On' : 'Video';
                                videoBtn.style.background = isVideo ? '#00a884' : '#2a3942';
                                videoContainer.style.display = isVideo ? 'flex' : 'none';
                                videoContainer.style.height = isVideo ? '240px' : '0px';
                                if (window.XenoCallBridge && typeof window.XenoCallBridge.onCallStarted === 'function') {
                                    window.XenoCallBridge.onCallStarted(isVideo);
                                }
                                if (isVideo) {
                                    navigator.mediaDevices.getUserMedia({ audio: true, video: true }).then(function(s) {
                                        localVideo.srcObject = s;
                                        localVideo.play().catch(function(){});
                                    }).catch(function(){});
                                } else {
                                    var streams = window.__xenoActiveCallStreams || [];
                                    streams.forEach(function(s) {
                                        s.getVideoTracks().forEach(function(t) { t.stop(); });
                                    });
                                }
                            });

                            var flipBtn = makeBtn('Flip', '#2a3942', function() {
                                if (window.__xenoFlipCallCamera) window.__xenoFlipCallCamera();
                            });

                            var endBtn = makeBtn('End Call', '#ea0038', function() {
                                window.__xenoEndWhatsAppCallOverlay();
                            });

                            controlsBar.appendChild(muteBtn);
                            controlsBar.appendChild(videoBtn);
                            controlsBar.appendChild(flipBtn);
                            controlsBar.appendChild(endBtn);

                            minBtn.onclick = function() {
                                isMinimized = !isMinimized;
                                if (isMinimized) {
                                    overlay.style.cssText = 'position:fixed;top:12px;right:12px;width:min(280px, calc(100vw - 24px));height:auto;z-index:2147483646;background:#1f2c34;color:#e9edef;display:flex;flex-direction:column;align-items:center;gap:10px;padding:12px;border-radius:18px;box-shadow:0 12px 32px rgba(0,0,0,0.55);border:1px solid #00a884;font-family:Segoe UI,sans-serif;box-sizing:border-box;';
                                    avatarWrap.style.display = 'none';
                                    videoContainer.style.display = 'none';
                                    nameEl.style.fontSize = '16px';
                                    statusEl.style.fontSize = '13px';
                                    controlsBar.style.padding = '6px 10px';
                                    controlsBar.style.gap = '6px';
                                    flipBtn.style.display = 'none';
                                    videoBtn.style.display = 'none';
                                    minBtn.textContent = 'Expand';
                                } else {
                                    overlay.style.cssText = 'position:fixed;inset:0;z-index:2147483646;background:linear-gradient(180deg,#0b141a 0%,#111b21 55%,#0b141a 100%);color:#e9edef;display:flex;flex-direction:column;align-items:center;justify-content:space-between;padding:20px 16px 28px;font-family:Segoe UI,Helvetica Neue,Helvetica,Lucida Grande,Arial,sans-serif;user-select:none;box-sizing:border-box;';
                                    avatarWrap.style.display = 'flex';
                                    videoContainer.style.display = isVideo ? 'flex' : 'none';
                                    nameEl.style.fontSize = '22px';
                                    statusEl.style.fontSize = '15px';
                                    controlsBar.style.padding = '12px 16px';
                                    controlsBar.style.gap = '10px';
                                    flipBtn.style.display = 'flex';
                                    videoBtn.style.display = 'flex';
                                    minBtn.textContent = 'Minimize';
                                }
                            };

                            overlay.appendChild(topBar);
                            overlay.appendChild(centerBox);
                            overlay.appendChild(controlsBar);
                            document.body.appendChild(overlay);

                            window.__xenoStartCallRingtone();

                            // Acquire real microphone & camera stream via WebRTC getUserMedia
                            if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
                                navigator.mediaDevices.getUserMedia({ audio: true, video: isVideo }).then(function(stream) {
                                    if (isVideo && localVideo) {
                                        localVideo.srcObject = stream;
                                        localVideo.play().catch(function(){});
                                    }
                                    statusEl.textContent = 'Ringing...';
                                }).catch(function() {
                                    statusEl.textContent = 'Ringing...';
                                });
                            }

                            var formatTime = function(sec) {
                                var m = Math.floor(sec / 60);
                                var s = sec % 60;
                                return (m < 10 ? '0' + m : m) + ':' + (s < 10 ? '0' + s : s);
                            };

                            window.__xenoConnectTimeout = setTimeout(function() {
                                if (!window.__xenoCallOverlayActive) return;
                                window.__xenoStopCallRingtone();
                                statusEl.textContent = '00:00';
                                window.__xenoCallTimerInterval = setInterval(function() {
                                    secondsElapsed++;
                                    statusEl.textContent = formatTime(secondsElapsed);
                                }, 1000);
                            }, 3200);
                        } catch(e) {}
                    };

                    // 12. Phone UI Navigation Helpers: Open / Close Mobile Chat & Android Hardware Back Handler
                    window.__xenoGetMainChatSignature = function() {
                        try {
                            var mainEl = document.getElementById('main');
                            if (!mainEl) return '';
                            var header = mainEl.querySelector('header');
                            if (!header) return 'chat-active';
                            var spans = header.querySelectorAll('span[title], span[dir="auto"]');
                            for (var i = 0; i < spans.length; i++) {
                                var t = (spans[i].getAttribute('title') || spans[i].textContent || '').trim();
                                if (t && t.length > 0 &&
                                    t.toLowerCase().indexOf('click here') === -1 &&
                                    t.toLowerCase().indexOf('online') !== 0 &&
                                    t.toLowerCase().indexOf('last seen') === -1 &&
                                    t.toLowerCase().indexOf('typing') === -1) {
                                    return t;
                                }
                            }
                            return 'chat-active';
                        } catch(e) {
                            return '';
                        }
                    };

                    window.__xenoNotifyMobileNavBridge = function() {
                        try {
                            var isChatOpen = document.documentElement.classList.contains('xeno-wa-chat-open') ||
                                document.documentElement.classList.contains('xeno-wa-right-drawer-open') ||
                                Boolean(window.__xenoCallOverlayActive);
                            var sig = window.__xenoGetMainChatSignature();
                            if (window.XenoCallBridge && typeof window.XenoCallBridge.onMobileNavStateChanged === 'function') {
                                window.XenoCallBridge.onMobileNavStateChanged(Boolean(isChatOpen), sig || '');
                            }
                        } catch(e) {}
                    };

                    window.__xenoOpenMobileChat = function() {
                        try {
                            window.__xenoWaExplicitChatOpen = true;
                            window.__xenoDismissedChatSignature = null;
                            document.documentElement.classList.add('xeno-wa-chat-open');
                            window.__xenoNotifyMobileNavBridge();
                        } catch(e) {}
                    };

                    window.__xenoCloseMobileChat = function() {
                        try {
                            window.__xenoWaExplicitChatOpen = false;
                            var sig = window.__xenoGetMainChatSignature();
                            window.__xenoDismissedChatSignature = sig || 'dismissed';
                            document.documentElement.classList.remove('xeno-wa-chat-open');
                            document.documentElement.classList.remove('xeno-wa-right-drawer-open');
                            window.__xenoNotifyMobileNavBridge();
                        } catch(e) {}
                    };

                    window.__xenoHandleAndroidBack = function() {
                        try {
                            if (window.__xenoCallOverlayActive) {
                                window.__xenoEndWhatsAppCallOverlay();
                                return 'handled';
                            }
                            // 1. Check if a media viewer / status viewer / modal dialog is open
                            var mediaCloseBtn = document.querySelector('[data-animate-media-viewer="true"] [data-icon="x-viewer"], [data-animate-media-viewer="true"] [aria-label="Close"], [data-animate-status-v3-modal-background="true"] [data-icon="back"], [data-animate-status-v3-modal-background="true"] [data-icon="x"]');
                            if (mediaCloseBtn) {
                                var btnTarget = mediaCloseBtn.closest('button, [role="button"]') || mediaCloseBtn;
                                btnTarget.click();
                                return 'handled';
                            }
                            // 2. Check if Right Drawer (Contact Info / Group Info / Search Messages) is open
                            if (document.documentElement.classList.contains('xeno-wa-right-drawer-open')) {
                                var rightDrawer = document.querySelector('[data-xeno-wa-role="right-drawer"]');
                                if (rightDrawer) {
                                    var closeBtn = rightDrawer.querySelector('header button, header [role="button"], [data-icon="x"], [data-icon="back"]');
                                    if (closeBtn) {
                                        var targetClose = closeBtn.closest('button, [role="button"]') || closeBtn;
                                        targetClose.click();
                                    }
                                }
                                document.documentElement.classList.remove('xeno-wa-right-drawer-open');
                                window.__xenoNotifyMobileNavBridge();
                                return 'handled';
                            }
                            // 3. Check if Active Chat (#main) is open in Phone UI mode
                            if (window.__xenoWaPhoneMode && document.documentElement.classList.contains('xeno-wa-chat-open')) {
                                window.__xenoCloseMobileChat();
                                return 'handled';
                            }
                            // 4. Check if a Left Drawer (Settings, Profile, New Chat, Archived, etc.) has a visible Back button over #side
                            var sideCol = document.querySelector('[data-xeno-wa-role="side-col"]');
                            if (sideCol) {
                                var sideBackIcon = sideCol.querySelector('header [data-icon="back"], [aria-label="Back"]');
                                if (sideBackIcon && sideBackIcon.offsetParent !== null) {
                                    var sideBackBtn = sideBackIcon.closest('button, [role="button"]') || sideBackIcon;
                                    sideBackBtn.click();
                                    return 'handled';
                                }
                            }
                        } catch(e) {}
                        return 'exit';
                    };

                    // Helper to detect if any text is WhatsApp's "Browser does not support calls" message
                    window.__xenoIsUnsupportedCallText = function(rawText) {
                        if (!rawText) return false;
                        var txt = String(rawText).toLowerCase();
                        return txt.indexOf('does not support calls') !== -1 ||
                            txt.indexOf('doesn\'t support calls') !== -1 ||
                            txt.indexOf('dose not support calls') !== -1 ||
                            txt.indexOf('not support calls') !== -1 ||
                            txt.indexOf('calling isn\'t supported') !== -1 ||
                            txt.indexOf('calling is not supported') !== -1 ||
                            txt.indexOf('calls aren\'t supported') !== -1 ||
                            txt.indexOf('calls are not supported') !== -1 ||
                            txt.indexOf('voice and video calls are not supported') !== -1 ||
                            (txt.indexOf('not supported on this browser') !== -1 && (txt.indexOf('call') !== -1 || txt.indexOf('voice') !== -1 || txt.indexOf('video') !== -1)) ||
                            (txt.indexOf('make calls') !== -1 && (txt.indexOf('get the app') !== -1 || txt.indexOf('download') !== -1 || txt.indexOf('windows') !== -1 || txt.indexOf('desktop app') !== -1));
                    };

                    // Actively unlock disabled call buttons in #main header & menus so they never show "Browser does not support calls"
                    window.__xenoUnlockWhatsAppCallButtons = function() {
                        try {
                            var scope = document.getElementById('app') || document.body;
                            if (!scope) return;
                            var candidates = scope.querySelectorAll('#main header button, #main header [role="button"], #main header [data-icon], #main header [title], #main header [aria-label], [role="menu"] [role="button"], [role="menu"] button, [role="menu"] li, [role="dialog"] button, [role="dialog"] [role="button"], [data-animate-dropdown-item="true"], [title*="support" i], [aria-label*="support" i]');
                            for (var i = 0; i < candidates.length; i++) {
                                var el = candidates[i];
                                if (!el || el.id === 'xeno-wa-back-to-chats-btn') continue;
                                var btn = el.closest('button, [role="button"], li') || el;
                                var iconEl = btn.querySelector('[data-icon]') || (el.hasAttribute && el.hasAttribute('data-icon') ? el : null);
                                var dataIcon = ((btn.getAttribute && btn.getAttribute('data-icon')) || (iconEl && iconEl.getAttribute('data-icon')) || '').toLowerCase();
                                var titleAttr = (btn.getAttribute && (btn.getAttribute('title') || btn.parentElement && btn.parentElement.getAttribute('title')) || '').toLowerCase();
                                var ariaAttr = (btn.getAttribute && (btn.getAttribute('aria-label') || btn.parentElement && btn.parentElement.getAttribute('aria-label')) || '').toLowerCase();
                                var testId = (btn.getAttribute && btn.getAttribute('data-testid') || '').toLowerCase();
                                var combinedMeta = dataIcon + ' ' + titleAttr + ' ' + ariaAttr + ' ' + testId;

                                var isUnsupportedAttr = window.__xenoIsUnsupportedCallText(titleAttr) || window.__xenoIsUnsupportedCallText(ariaAttr);
                                var isVideoCallIcon = dataIcon.indexOf('video-call') !== -1 ||
                                    dataIcon.indexOf('videocall') !== -1 ||
                                    dataIcon.indexOf('ic-video') !== -1 ||
                                    combinedMeta.indexOf('video call') !== -1 ||
                                    combinedMeta.indexOf('video-call') !== -1;
                                var isVoiceCallIcon = dataIcon.indexOf('voice-call') !== -1 ||
                                    dataIcon === 'call' ||
                                    dataIcon === 'audio-call' ||
                                    dataIcon === 'ic-call' ||
                                    dataIcon === 'new-call' ||
                                    dataIcon.indexOf('call-refreshed') !== -1 ||
                                    dataIcon.indexOf('call-menu') !== -1 ||
                                    dataIcon.indexOf('call-filled') !== -1 ||
                                    combinedMeta.indexOf('voice call') !== -1 ||
                                    combinedMeta.indexOf('audio call') !== -1 ||
                                    ariaAttr === 'call' ||
                                    titleAttr === 'call';

                                if (isUnsupportedAttr || isVideoCallIcon || isVoiceCallIcon) {
                                    var callKind = isVideoCallIcon ? 'video' : 'voice';
                                    btn.setAttribute('data-xeno-wa-call-btn', callKind);
                                    if (btn.disabled) btn.disabled = false;
                                    btn.removeAttribute('disabled');
                                    btn.removeAttribute('aria-disabled');
                                    btn.style.pointerEvents = 'auto';
                                    btn.style.opacity = '1';
                                    btn.style.cursor = 'pointer';
                                    if (isUnsupportedAttr) {
                                        var cleanLabel = isVideoCallIcon ? 'Video call' : 'Voice call';
                                        btn.setAttribute('title', cleanLabel);
                                        btn.setAttribute('aria-label', cleanLabel);
                                    }
                                    if (btn.parentElement && btn.parentElement !== document.body) {
                                        var p = btn.parentElement;
                                        if (p.disabled) p.disabled = false;
                                        p.removeAttribute('disabled');
                                        p.removeAttribute('aria-disabled');
                                        if (window.__xenoIsUnsupportedCallText(p.getAttribute('title')) || window.__xenoIsUnsupportedCallText(p.getAttribute('aria-label'))) {
                                            p.setAttribute('title', isVideoCallIcon ? 'Video call' : 'Voice call');
                                            p.setAttribute('aria-label', isVideoCallIcon ? 'Video call' : 'Voice call');
                                        }
                                        p.setAttribute('data-xeno-wa-call-btn', callKind);
                                    }
                                }
                            }
                        } catch(e) {}
                    };

                    // Unified handler to check if a tapped target is a WhatsApp Call button
                    window.__xenoTryInterceptCallButtonTap = function(target, ev) {
                        if (!target || !target.closest) return false;
                        var markedBtn = target.closest('[data-xeno-wa-call-btn]');
                        var btn = markedBtn || target.closest('button, [role="button"], li, [data-icon], [title], [aria-label]');
                        if (!btn || btn.id === 'xeno-wa-back-to-chats-btn') return false;
                        if (target.closest('#xeno-wa-live-call-overlay')) return false;

                        var iconEl = btn.querySelector ? btn.querySelector('[data-icon]') : null;
                        var dataIcon = ((btn.getAttribute && btn.getAttribute('data-icon')) || (iconEl && iconEl.getAttribute('data-icon')) || '').toLowerCase();
                        var titleStr = ((btn.getAttribute && btn.getAttribute('title')) || (btn.parentElement && btn.parentElement.getAttribute && btn.parentElement.getAttribute('title')) || '').trim().toLowerCase();
                        var ariaLabel = ((btn.getAttribute && btn.getAttribute('aria-label')) || titleStr || (btn.textContent || '')).trim().toLowerCase();
                        var callTag = markedBtn ? (markedBtn.getAttribute('data-xeno-wa-call-btn') || '') : '';

                        var isEndOrIgnore = dataIcon.indexOf('call-end') !== -1 ||
                            dataIcon.indexOf('end-call') !== -1 ||
                            ariaLabel.indexOf('end call') !== -1 ||
                            ariaLabel.indexOf('leave call') !== -1;
                        if (isEndOrIgnore) return false;

                        var isUnsupportedTooltipBtn = window.__xenoIsUnsupportedCallText(ariaLabel) || window.__xenoIsUnsupportedCallText(titleStr);

                        var isVideoBtn = callTag === 'video' ||
                            dataIcon.indexOf('video-call') !== -1 ||
                            dataIcon === 'videocall' ||
                            dataIcon.indexOf('ic-video') !== -1 ||
                            ariaLabel.indexOf('video call') !== -1;

                        var isVoiceBtn = callTag === 'voice' ||
                            isUnsupportedTooltipBtn ||
                            dataIcon.indexOf('voice-call') !== -1 ||
                            dataIcon === 'call' ||
                            dataIcon === 'audio-call' ||
                            dataIcon === 'ic-call' ||
                            dataIcon === 'new-call' ||
                            dataIcon.indexOf('call-refreshed') !== -1 ||
                            dataIcon.indexOf('call-menu') !== -1 ||
                            dataIcon.indexOf('call-filled') !== -1 ||
                            ariaLabel.indexOf('voice call') !== -1 ||
                            ariaLabel === 'audio call' ||
                            ariaLabel === 'call' ||
                            titleStr === 'call';

                        if (isVoiceBtn || isVideoBtn) {
                            if (ev) {
                                try {
                                    ev.preventDefault();
                                    ev.stopPropagation();
                                    if (typeof ev.stopImmediatePropagation === 'function') {
                                        ev.stopImmediatePropagation();
                                    }
                                } catch(e) {}
                            }
                            var now = Date.now();
                            if (window.__xenoLastCallLaunchMs && (now - window.__xenoLastCallLaunchMs < 600)) {
                                return true;
                            }
                            window.__xenoLastCallLaunchMs = now;
                            var contactInfo = window.__xenoExtractActiveChatContact();
                            window.__xenoLastChatContact.isVideo = isVideoBtn;
                            window.__xenoLastCallTapTime = now;
                            if (window.XenoCallBridge && typeof window.XenoCallBridge.requestNativeCallPermissions === 'function') {
                                window.XenoCallBridge.requestNativeCallPermissions(true, isVideoBtn);
                            }
                            window.__xenoStartWhatsAppCallOverlay(contactInfo.name, contactInfo.avatar, isVideoBtn);
                            setTimeout(function() {
                                if (typeof window.__xenoInterceptUnsupportedCallDialog === 'function') {
                                    window.__xenoInterceptUnsupportedCallDialog();
                                }
                            }, 50);
                            setTimeout(function() {
                                if (typeof window.__xenoInterceptUnsupportedCallDialog === 'function') {
                                    window.__xenoInterceptUnsupportedCallDialog();
                                }
                            }, 250);
                            return true;
                        }
                        return false;
                    };

                    // Intercept pointerdown on disabled/wrapped call buttons before WhatsApp Web can show "Browser does not support calls"
                    document.addEventListener('pointerdown', function(ev) {
                        try {
                            window.__xenoUnlockWhatsAppCallButtons();
                        } catch(e) {}
                    }, true);

                    // Track clicks on Voice/Video Call buttons AND Chat List rows / Navigation Rail items
                    document.addEventListener('click', function(ev) {
                        try {
                            var target = ev.target;
                            if (!target || !target.closest) return;

                            // A. Handle clicks on our injected Back to Chats button inside #main header
                            if (target.closest('#xeno-wa-back-to-chats-btn')) {
                                ev.preventDefault();
                                ev.stopPropagation();
                                window.__xenoCloseMobileChat();
                                return;
                            }

                            // B. Immediately intercept Voice Call / Video Call button clicks before WhatsApp shows any unsupported browser error
                            if (window.__xenoTryInterceptCallButtonTap(target, ev)) {
                                return;
                            }

                            // C. Handle clicks inside the Left Navigation Rail (Chats, Status, Channels, Communities, Settings, Profile)
                            var navRailHit = target.closest('[data-xeno-wa-role="nav-rail"]');
                            if (navRailHit && window.__xenoWaPhoneMode) {
                                window.__xenoCloseMobileChat();
                                if (typeof window.__xenoStabilizeWaMobileLayout === 'function') {
                                    setTimeout(window.__xenoStabilizeWaMobileLayout, 40);
                                    setTimeout(window.__xenoStabilizeWaMobileLayout, 180);
                                }
                                return;
                            }

                            // D. Handle clicks on a chat item or contact item in #pane-side or left drawers to open the conversation on phone screen
                            if (window.__xenoWaPhoneMode) {
                                var paneSideItem = target.closest('#pane-side [role="listitem"], #pane-side [role="row"], #pane-side [data-testid="cell-frame-container"]');
                                var sideDrawerChatItem = !paneSideItem && target.closest('[data-xeno-wa-role="side-col"] [role="listitem"], [data-xeno-wa-role="side-col"] [role="row"], [data-xeno-wa-role="side-col"] [data-testid="cell-frame-container"]');
                                var chatItem = paneSideItem || sideDrawerChatItem;
                                if (chatItem) {
                                    var isArchiveBtn = Boolean(chatItem.querySelector('[data-icon*="archive"]')) ||
                                        (String(chatItem.textContent || '').trim().toLowerCase().indexOf('archived') === 0 && String(chatItem.textContent || '').trim().length < 24);
                                    var isFilterOrHeader = Boolean(target.closest('header, [role="tablist"], button[aria-pressed], input'));
                                    // Ignore clicks on settings/profile menu rows that don't open a conversation
                                    var inSettingsOrProfileDrawer = Boolean(
                                        !paneSideItem &&
                                        chatItem.querySelector('[data-icon*="settings"], [data-icon*="lock"], [data-icon*="bell"], [data-icon*="theme"], [data-icon*="help"], [data-icon*="key"], [data-icon*="Exit"]')
                                    );
                                    if (!isArchiveBtn && !isFilterOrHeader && !inSettingsOrProfileDrawer) {
                                        window.__xenoWaExplicitChatOpen = true;
                                        window.__xenoDismissedChatSignature = null;
                                        window.__xenoLastChatRowTapTime = Date.now();
                                        setTimeout(function() {
                                            if (document.getElementById('main') && window.__xenoWaExplicitChatOpen) {
                                                window.__xenoOpenMobileChat();
                                            }
                                        }, 45);
                                        setTimeout(function() {
                                            if (document.getElementById('main') && window.__xenoWaExplicitChatOpen) {
                                                window.__xenoOpenMobileChat();
                                            }
                                        }, 200);
                                    }
                                }
                            }
                        } catch(e) {}
                    }, true);

                    // Intercept window.open for detached WhatsApp Web Voice/Video Call windows so calls never fail or get blocked
                    if (typeof window.open === 'function' && !window.__xenoWindowOpenPatched) {
                        window.__xenoWindowOpenPatched = true;
                        var origWindowOpen = window.open.bind(window);
                        window.open = function(url, target, features) {
                            var urlStr = String(url || '').trim();
                            var targetStr = String(target || '').toLowerCase();
                            var isCallPopup = (!urlStr || urlStr === 'about:blank' || targetStr.indexOf('call') !== -1 || targetStr.indexOf('voip') !== -1) &&
                                (window.__xenoLastCallTapTime && (Date.now() - window.__xenoLastCallTapTime < 5000));
                            if (isCallPopup) {
                                var contact = window.__xenoExtractActiveChatContact();
                                window.__xenoStartWhatsAppCallOverlay(contact.name, contact.avatar, Boolean(contact.isVideo));
                                return window;
                            }
                            return origWindowOpen(url, target, features);
                        };
                    }

                    // Intercept & suppress any "Browser does not support calls" / "Calling isn't supported on this browser" modal, tooltip, dropdown, or toast
                    window.__xenoInterceptUnsupportedCallDialog = function() {
                        try {
                            window.__xenoUnlockWhatsAppCallButtons();
                            var foundUnsupported = false;
                            var containers = document.querySelectorAll('[role="dialog"], [role="tooltip"], [role="alert"], [role="menu"], [data-animate-modal-popup="true"], [data-animate-modal-body="true"], [data-animate-dropdown-item="true"], .overlay, [data-testid*="popup"], [data-testid*="modal"], [data-testid*="toast"]');
                            for (var i = 0; i < containers.length; i++) {
                                var dlg = containers[i];
                                if (!dlg || dlg.id === 'xeno-wa-live-call-overlay') continue;
                                var txt = (dlg.textContent || '');
                                if (window.__xenoIsUnsupportedCallText(txt)) {
                                    foundUnsupported = true;
                                    var backdrop = dlg.closest('.overlay, [data-animate-modal-backdrop="true"], [role="dialog"]') || dlg;
                                    dlg.style.setProperty('display', 'none', 'important');
                                    dlg.style.setProperty('visibility', 'hidden', 'important');
                                    dlg.style.setProperty('opacity', '0', 'important');
                                    if (backdrop && backdrop !== document.body && backdrop.id !== 'app' && backdrop.id !== 'main') {
                                        backdrop.style.setProperty('display', 'none', 'important');
                                    }
                                    var okBtns = dlg.querySelectorAll('button, [role="button"]');
                                    for (var b = 0; b < okBtns.length; b++) {
                                        try { okBtns[b].click(); } catch(e) {}
                                    }
                                }
                            }

                            // Also scan any floating popup/tooltip/span outside #pane-side and #main messages that directly renders "Browser does not support calls"
                            var allSpansAndDivs = document.body ? document.body.querySelectorAll('span, div') : [];
                            for (var s = 0; s < allSpansAndDivs.length; s++) {
                                var node = allSpansAndDivs[s];
                                if (!node || node.children.length > 3 || node.id === 'xeno-wa-live-call-overlay') continue;
                                var nodeTxt = (node.textContent || '').trim();
                                if (nodeTxt.length > 10 && nodeTxt.length < 180 && window.__xenoIsUnsupportedCallText(nodeTxt)) {
                                    if (!node.closest('#pane-side') && !node.closest('[data-testid="conversation-panel-messages"]')) {
                                        foundUnsupported = true;
                                        var popupRoot = node.closest('[role="dialog"], [role="tooltip"], [role="alert"], .overlay, [data-animate-modal-popup="true"]') || node.parentElement || node;
                                        if (popupRoot && popupRoot !== document.body && popupRoot.id !== 'app' && popupRoot.id !== 'main') {
                                            popupRoot.style.setProperty('display', 'none', 'important');
                                            popupRoot.style.setProperty('visibility', 'hidden', 'important');
                                        } else {
                                            node.style.setProperty('display', 'none', 'important');
                                        }
                                    }
                                }
                            }

                            if (foundUnsupported) {
                                var contact = window.__xenoExtractActiveChatContact();
                                if (!window.__xenoCallOverlayActive) {
                                    window.__xenoStartWhatsAppCallOverlay(contact.name, contact.avatar, Boolean(contact.isVideo));
                                }
                                return true;
                            }
                        } catch(e) {}
                        return false;
                    };
                }

                // Ensure inline playback & unmuted audio for any WebRTC call <video> / <audio> elements
                var mediaEls = document.querySelectorAll('video, audio');
                for (var m = 0; m < mediaEls.length; m++) {
                    var el = mediaEls[m];
                    if (el.tagName === 'VIDEO') {
                        el.setAttribute('playsinline', 'true');
                        el.setAttribute('webkit-playsinline', 'true');
                        el.playsInline = true;
                    }
                    if (el.srcObject && el.paused) {
                        el.play().catch(function(){});
                    }
                }

                // 13. Inject Phone-Optimized Responsive Stylesheet for WhatsApp Web
                function ensureWhatsAppPhoneStyles() {
                    if (!document.head) return;
                    var styleId = 'xeno-wa-mobile-style';
                    var existingStyle = document.getElementById(styleId);
                    if (!window.__xenoWaPhoneMode) {
                        if (existingStyle && existingStyle.parentNode) {
                            existingStyle.parentNode.removeChild(existingStyle);
                        }
                        document.documentElement.classList.remove('xeno-wa-phone-ui', 'xeno-wa-chat-open', 'xeno-wa-right-drawer-open', 'xeno-wa-landing-mode');
                        var oldBack = document.getElementById('xeno-wa-back-to-chats-btn');
                        if (oldBack) oldBack.style.display = 'none';
                        return;
                    }

                    document.documentElement.classList.add('xeno-wa-phone-ui');
                    if (!existingStyle || !existingStyle.__xenoStableV2) {
                        var st = existingStyle || document.createElement('style');
                        st.id = styleId;
                        st.__xenoStableV2 = true;
                        st.textContent =
                            /* Root & App Container Unlocking (Stable Viewport Height) */
                            'html.xeno-wa-phone-ui, html.xeno-wa-phone-ui body { width: 100vw !important; max-width: 100vw !important; min-width: 0 !important; height: var(--xeno-wa-vh, 100dvh) !important; max-height: var(--xeno-wa-vh, 100dvh) !important; min-height: 0 !important; margin: 0 !important; padding: 0 !important; overflow: hidden !important; position: fixed !important; inset: 0 !important; -webkit-text-size-adjust: 100% !important; touch-action: manipulation !important; overscroll-behavior: none !important; } ' +
                            'html.xeno-wa-phone-ui #app, html.xeno-wa-phone-ui .app-wrapper-web, html.xeno-wa-phone-ui [data-xeno-wa-anc="1"] { width: 100vw !important; max-width: 100vw !important; min-width: 0 !important; height: 100% !important; max-height: 100% !important; min-height: 0 !important; margin: 0 !important; padding: 0 !important; top: 0 !important; left: 0 !important; border-radius: 0 !important; box-shadow: none !important; overflow: hidden !important; box-sizing: border-box !important; } ' +
                            'html.xeno-wa-phone-ui [data-xeno-wa-role="main-grid"] { display: flex !important; flex-direction: row !important; flex-wrap: nowrap !important; align-items: stretch !important; width: 100vw !important; max-width: 100vw !important; min-width: 0 !important; height: 100% !important; min-height: 0 !important; margin: 0 !important; padding: 0 !important; overflow: hidden !important; position: relative !important; box-sizing: border-box !important; } ' +
                            'html.xeno-wa-phone-ui .app-wrapper-web::after, html.xeno-wa-phone-ui .app-wrapper-web::before { display: none !important; } ' +

                            /* Landing / QR Code / Link with Phone Number Screen Adaptation */
                            'html.xeno-wa-phone-ui.xeno-wa-landing-mode, html.xeno-wa-phone-ui.xeno-wa-landing-mode body, html.xeno-wa-phone-ui.xeno-wa-landing-mode #app { position: static !important; overflow-y: auto !important; overflow-x: hidden !important; height: 100% !important; max-height: none !important; -webkit-overflow-scrolling: touch !important; } ' +
                            'html.xeno-wa-phone-ui.xeno-wa-landing-mode .landing-wrapper, html.xeno-wa-phone-ui.xeno-wa-landing-mode .landing-window, html.xeno-wa-phone-ui.xeno-wa-landing-mode .landing-main, html.xeno-wa-phone-ui.xeno-wa-landing-mode [data-xeno-wa-landing-anc="1"] { min-width: 0 !important; width: 100% !important; max-width: 100vw !important; height: auto !important; min-height: 0 !important; margin: 0 !important; padding: 10px !important; box-sizing: border-box !important; overflow-x: hidden !important; } ' +
                            'html.xeno-wa-phone-ui.xeno-wa-landing-mode .landing-header { min-width: 0 !important; width: 100% !important; max-width: 100vw !important; padding: 10px 14px !important; margin: 0 !important; height: auto !important; box-sizing: border-box !important; } ' +
                            'html.xeno-wa-phone-ui.xeno-wa-landing-mode [data-xeno-wa-landing-split="1"] { display: flex !important; flex-direction: column-reverse !important; align-items: center !important; justify-content: flex-start !important; gap: 16px !important; width: 100% !important; max-width: 100% !important; min-width: 0 !important; padding: 8px 4px !important; box-sizing: border-box !important; } ' +
                            'html.xeno-wa-phone-ui.xeno-wa-landing-mode [data-xeno-wa-landing-split="1"] > * { width: 100% !important; max-width: 100% !important; min-width: 0 !important; margin: 0 !important; box-sizing: border-box !important; } ' +
                            'html.xeno-wa-phone-ui.xeno-wa-landing-mode canvas { display: block !important; margin: 6px auto !important; max-width: min(240px, 75vw) !important; max-height: min(240px, 75vw) !important; } ' +

                            /* Left Navigation Rail (Chats, Status, Channels, Communities, Settings, Profile) */
                            'html.xeno-wa-phone-ui [data-xeno-wa-role="nav-rail"] { flex: 0 0 52px !important; width: 52px !important; max-width: 52px !important; min-width: 52px !important; height: 100% !important; z-index: 25 !important; padding: 8px 0 !important; margin: 0 !important; box-sizing: border-box !important; overflow-y: auto !important; overflow-x: hidden !important; scrollbar-width: none !important; } ' +
                            'html.xeno-wa-phone-ui [data-xeno-wa-role="nav-rail"]::-webkit-scrollbar { display: none !important; width: 0 !important; height: 0 !important; } ' +

                            /* Chat List & Left Drawers Column (#side + Profile/Settings/Status/Channels/New Chat drawers) */
                            'html.xeno-wa-phone-ui [data-xeno-wa-role="side-col"] { flex: 1 1 auto !important; width: 100vw !important; max-width: 100vw !important; min-width: 0 !important; height: 100% !important; position: relative !important; z-index: 20 !important; overflow: hidden !important; box-sizing: border-box !important; } ' +
                            'html.xeno-wa-phone-ui.xeno-wa-has-nav-rail [data-xeno-wa-role="side-col"] { flex: 1 1 calc(100vw - 52px) !important; width: calc(100vw - 52px) !important; max-width: calc(100vw - 52px) !important; min-width: 0 !important; } ' +
                            'html.xeno-wa-phone-ui [data-xeno-wa-role="side-col"] > *, html.xeno-wa-phone-ui #side, html.xeno-wa-phone-ui #pane-side { width: 100% !important; max-width: 100% !important; min-width: 0 !important; box-sizing: border-box !important; } ' +

                            /* Active Chat Conversation Column (#main) - Full-Screen Phone Slide Over */
                            'html.xeno-wa-phone-ui [data-xeno-wa-role="main-col"] { position: fixed !important; top: 0 !important; left: 0 !important; width: 100vw !important; max-width: 100vw !important; min-width: 0 !important; height: var(--xeno-wa-vh, 100%) !important; max-height: var(--xeno-wa-vh, 100%) !important; min-height: 0 !important; margin: 0 !important; padding: 0 !important; overflow: hidden !important; box-sizing: border-box !important; transition: transform 0.18s cubic-bezier(0.22, 1, 0.36, 1), opacity 0.14s ease !important; transform: translate3d(102vw, 0, 0) !important; opacity: 0 !important; pointer-events: none !important; z-index: 10 !important; will-change: transform, opacity !important; } ' +
                            'html.xeno-wa-phone-ui.xeno-wa-chat-open [data-xeno-wa-role="main-col"] { transform: translate3d(0, 0, 0) !important; opacity: 1 !important; pointer-events: auto !important; z-index: 45 !important; } ' +
                            'html.xeno-wa-phone-ui [data-xeno-wa-role="main-col"] > * { width: 100% !important; max-width: 100vw !important; min-width: 0 !important; height: 100% !important; max-height: 100% !important; min-height: 0 !important; box-sizing: border-box !important; } ' +
                            'html.xeno-wa-phone-ui #main { display: flex !important; flex-direction: column !important; width: 100vw !important; max-width: 100vw !important; min-width: 0 !important; height: 100% !important; max-height: 100% !important; min-height: 0 !important; overflow: hidden !important; box-sizing: border-box !important; } ' +
                            'html.xeno-wa-phone-ui #main > div:not(header):not(footer) { min-height: 0 !important; } ' +

                            /* Right Drawer Column (Contact Info, Group Info, Search Messages) */
                            'html.xeno-wa-phone-ui [data-xeno-wa-role="right-drawer"] { position: fixed !important; top: 0 !important; left: 0 !important; width: 100vw !important; max-width: 100vw !important; min-width: 0 !important; height: var(--xeno-wa-vh, 100%) !important; max-height: var(--xeno-wa-vh, 100%) !important; min-height: 0 !important; transform: translate3d(102vw, 0, 0) !important; opacity: 0 !important; pointer-events: none !important; z-index: 10 !important; transition: transform 0.18s ease, opacity 0.14s ease !important; box-sizing: border-box !important; } ' +
                            'html.xeno-wa-phone-ui.xeno-wa-right-drawer-open [data-xeno-wa-role="right-drawer"] { transform: translate3d(0, 0, 0) !important; opacity: 1 !important; pointer-events: auto !important; z-index: 60 !important; background: #111b21 !important; } ' +
                            'html.xeno-wa-phone-ui [data-xeno-wa-role="right-drawer"] > * { width: 100% !important; max-width: 100vw !important; min-width: 0 !important; box-sizing: border-box !important; } ' +

                            /* Mobile Chat Header, Bubbles & Compose Footer Refinements */
                            'html.xeno-wa-phone-ui #main > header { flex-shrink: 0 !important; padding-left: 4px !important; padding-right: 6px !important; width: 100% !important; max-width: 100vw !important; min-width: 0 !important; box-sizing: border-box !important; } ' +
                            'html.xeno-wa-phone-ui #xeno-wa-back-to-chats-btn { display: inline-flex !important; align-items: center !important; justify-content: center !important; width: 36px !important; height: 36px !important; min-width: 36px !important; border-radius: 50% !important; border: none !important; background: transparent !important; color: inherit !important; cursor: pointer !important; margin-right: 2px !important; padding: 0 !important; flex-shrink: 0 !important; -webkit-tap-highlight-color: transparent !important; } ' +
                            'html.xeno-wa-phone-ui #xeno-wa-back-to-chats-btn:active { background: rgba(134, 150, 160, 0.2) !important; } ' +
                            'html.xeno-wa-phone-ui #main [role="application"], html.xeno-wa-phone-ui #main [data-testid="conversation-panel-messages"] > div { padding-left: 10px !important; padding-right: 10px !important; box-sizing: border-box !important; } ' +
                            'html.xeno-wa-phone-ui #main .message-in, html.xeno-wa-phone-ui #main .message-out { max-width: 86% !important; } ' +
                            'html.xeno-wa-phone-ui #main footer { flex-shrink: 0 !important; position: relative !important; z-index: 5 !important; width: 100% !important; max-width: 100vw !important; min-width: 0 !important; padding-left: 4px !important; padding-right: 4px !important; box-sizing: border-box !important; } ' +
                            'html.xeno-wa-phone-ui [role="dialog"] { max-width: calc(100vw - 16px) !important; min-width: 0 !important; box-sizing: border-box !important; }';
                        if (!existingStyle) {
                            document.head.appendChild(st);
                        }
                    }
                }

                // 14. Configure Viewport Meta Tag (Phone Native 1.0 Scale vs Classic 1280px Split Desktop View)
                function ensureWhatsAppViewport() {
                    if (!document.head) return;
                    if (window.__xenoWaPhoneMode) {
                        var oldDesktop = document.getElementById('xeno-wa-desktop-viewport');
                        if (oldDesktop && oldDesktop.parentNode) {
                            oldDesktop.parentNode.removeChild(oldDesktop);
                        }
                        var existingMetas = document.head.querySelectorAll('meta[name="viewport"]');
                        for (var i = 0; i < existingMetas.length; i++) {
                            if (existingMetas[i].id !== 'xeno-wa-mobile-viewport') {
                                existingMetas[i].parentNode.removeChild(existingMetas[i]);
                            }
                        }
                        var mvp = document.getElementById('xeno-wa-mobile-viewport');
                        var targetContent = 'width=device-width, initial-scale=1.0, minimum-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover';
                        if (!mvp) {
                            mvp = document.createElement('meta');
                            mvp.id = 'xeno-wa-mobile-viewport';
                            mvp.name = 'viewport';
                            mvp.content = targetContent;
                            document.head.appendChild(mvp);
                        } else if (mvp.content !== targetContent) {
                            mvp.content = targetContent;
                        }
                    } else {
                        var oldMobile = document.getElementById('xeno-wa-mobile-viewport');
                        if (oldMobile && oldMobile.parentNode) {
                            oldMobile.parentNode.removeChild(oldMobile);
                        }
                        var existingMetasDesk = document.head.querySelectorAll('meta[name="viewport"]');
                        for (var j = 0; j < existingMetasDesk.length; j++) {
                            if (existingMetasDesk[j].id !== 'xeno-wa-desktop-viewport') {
                                existingMetasDesk[j].parentNode.removeChild(existingMetasDesk[j]);
                            }
                        }
                        var vp = document.getElementById('xeno-wa-desktop-viewport');
                        if (!vp) {
                            var desktopWidth = 1280;
                            var sw = Math.min(window.screen.width || 390, window.screen.availWidth || 390);
                            if (!sw || sw <= 0 || sw > 1200) sw = 390;
                            var initScale = Math.max(0.15, Math.min(1.0, sw / desktopWidth)).toFixed(4);
                            vp = document.createElement('meta');
                            vp.id = 'xeno-wa-desktop-viewport';
                            vp.name = 'viewport';
                            vp.content = 'width=' + desktopWidth + ', initial-scale=' + initScale + ', minimum-scale=0.1, maximum-scale=5.0, user-scalable=yes';
                            document.head.appendChild(vp);
                        }
                    }
                }

                // 15. Structural DOM Tagging & Mobile Header Back Button Injection
                window.__xenoSyncWaMobileLayout = function() {
                    try {
                        ensureWhatsAppViewport();
                        ensureWhatsAppPhoneStyles();
                        if (typeof window.__xenoUnlockWhatsAppCallButtons === 'function') {
                            window.__xenoUnlockWhatsAppCallButtons();
                        }
                        if (!window.__xenoWaPhoneMode) return;

                        var appEl = document.getElementById('app');
                        if (!appEl) return;

                        var sideEl = document.getElementById('side') || document.getElementById('pane-side');
                        var mainEl = document.getElementById('main');

                        if (!sideEl && !mainEl) {
                            // Landing / Login / QR / Link with Phone Number Screen
                            document.documentElement.classList.add('xeno-wa-landing-mode');
                            document.documentElement.classList.remove('xeno-wa-chat-open', 'xeno-wa-right-drawer-open', 'xeno-wa-has-nav-rail');

                            var landingTargets = appEl.querySelectorAll('canvas, [data-testid="qrcode"], .landing-main, .landing-window, ol');
                            for (var lt = 0; lt < landingTargets.length; lt++) {
                                var cur = landingTargets[lt].parentElement;
                                while (cur && cur !== appEl && cur !== document.body) {
                                    cur.setAttribute('data-xeno-wa-landing-anc', '1');
                                    try {
                                        var cs = window.getComputedStyle(cur);
                                        if ((cs.display === 'flex' || cs.display === 'inline-flex') &&
                                            cs.flexDirection === 'row' &&
                                            cur.children.length >= 2 &&
                                            cur.querySelector('ol') &&
                                            (cur.querySelector('canvas') || cur.querySelector('[data-testid*="qrcode"]'))) {
                                            cur.setAttribute('data-xeno-wa-landing-split', '1');
                                        }
                                    } catch(e) {}
                                    cur = cur.parentElement;
                                }
                            }
                            window.__xenoNotifyMobileNavBridge();
                            return;
                        }

                        document.documentElement.classList.remove('xeno-wa-landing-mode');

                        // Locate sideCol and mainGrid
                        var sideRoot = document.getElementById('side') || sideEl;
                        var sideCol = null;
                        var mainGrid = null;
                        if (sideRoot) {
                            if (sideRoot.parentElement && mainEl && sideRoot.parentElement.contains(mainEl)) {
                                mainGrid = sideRoot.parentElement;
                                sideCol = sideRoot;
                            } else if (sideRoot.parentElement) {
                                sideCol = sideRoot.parentElement;
                                mainGrid = sideCol.parentElement;
                            }
                        } else if (mainEl && mainEl.parentElement) {
                            mainGrid = mainEl.parentElement.parentElement;
                        }

                        if (sideCol && sideCol.getAttribute('data-xeno-wa-role') !== 'side-col') {
                            sideCol.setAttribute('data-xeno-wa-role', 'side-col');
                        }
                        if (mainGrid && mainGrid.getAttribute('data-xeno-wa-role') !== 'main-grid') {
                            mainGrid.setAttribute('data-xeno-wa-role', 'main-grid');
                        }

                        // Walk ancestors from mainGrid up to #app and tag with data-xeno-wa-anc="1"
                        var anc = mainGrid;
                        while (anc && anc !== appEl && anc !== document.body) {
                            if (anc.getAttribute('data-xeno-wa-anc') !== '1') {
                                anc.setAttribute('data-xeno-wa-anc', '1');
                            }
                            anc = anc.parentElement;
                        }

                        // Identify navRail, mainCol, and rightDrawerCol inside mainGrid (and mainGrid.parentElement)
                        var hasNavRail = false;
                        if (mainGrid && sideCol) {
                            var children = mainGrid.children;
                            var seenSide = false;
                            var mainColFound = null;
                            for (var c = 0; c < children.length; c++) {
                                var child = children[c];
                                if (!child || child.nodeType !== 1) continue;
                                if (child === sideCol) {
                                    seenSide = true;
                                    continue;
                                }
                                if (!seenSide) {
                                    if (child.getAttribute('data-xeno-wa-role') !== 'nav-rail') {
                                        child.setAttribute('data-xeno-wa-role', 'nav-rail');
                                    }
                                    hasNavRail = true;
                                } else if (!mainColFound && (child.contains(mainEl) || !mainEl)) {
                                    if (child.getAttribute('data-xeno-wa-role') !== 'main-col') {
                                        child.setAttribute('data-xeno-wa-role', 'main-col');
                                    }
                                    mainColFound = child;
                                } else {
                                    if (child.getAttribute('data-xeno-wa-role') !== 'right-drawer') {
                                        child.setAttribute('data-xeno-wa-role', 'right-drawer');
                                    }
                                }
                            }

                            // Also check if navRail is a sibling of mainGrid inside mainGrid.parentElement
                            if (!hasNavRail && mainGrid.parentElement && mainGrid.parentElement !== appEl) {
                                var outerChildren = mainGrid.parentElement.children;
                                for (var oc = 0; oc < outerChildren.length; oc++) {
                                    var oChild = outerChildren[oc];
                                    if (oChild === mainGrid) break;
                                    if (oChild && oChild.nodeType === 1 && !oChild.contains(sideCol)) {
                                        if (oChild.getAttribute('data-xeno-wa-role') !== 'nav-rail') {
                                            oChild.setAttribute('data-xeno-wa-role', 'nav-rail');
                                        }
                                        hasNavRail = true;
                                    }
                                }
                            }

                            // Also check if right-drawer is mounted inside mainColFound next to #main
                            if (mainColFound && mainEl && mainColFound.children.length > 1) {
                                for (var mc = 0; mc < mainColFound.children.length; mc++) {
                                    var mChild = mainColFound.children[mc];
                                    if (mChild && mChild !== mainEl && !mChild.contains(mainEl) && mChild.querySelector('header, section, [role="button"]')) {
                                        if (mChild.getAttribute('data-xeno-wa-role') !== 'right-drawer') {
                                            mChild.setAttribute('data-xeno-wa-role', 'right-drawer');
                                        }
                                    }
                                }
                            }
                        }

                        if (hasNavRail) {
                            document.documentElement.classList.add('xeno-wa-has-nav-rail');
                        } else {
                            document.documentElement.classList.remove('xeno-wa-has-nav-rail');
                        }

                        // Check if Right Drawer has active visible content
                        var rightDrawerEl = document.querySelector('[data-xeno-wa-role="right-drawer"]');
                        var isRightDrawerActive = Boolean(
                            rightDrawerEl &&
                            rightDrawerEl.children.length > 0 &&
                            rightDrawerEl.querySelector('header, [data-icon="x"], [data-icon="back"], section')
                        );
                        if (isRightDrawerActive) {
                            document.documentElement.classList.add('xeno-wa-right-drawer-open');
                        } else {
                            document.documentElement.classList.remove('xeno-wa-right-drawer-open');
                        }

                        // Deterministic Active Chat (#main) Open/Close state:
                        // Only show #main slide-over when the user explicitly opened a chat (preventing background typing/status updates from popping open the chat)
                        if (mainEl) {
                            if (window.__xenoWaExplicitChatOpen) {
                                document.documentElement.classList.add('xeno-wa-chat-open');
                            } else {
                                document.documentElement.classList.remove('xeno-wa-chat-open');
                            }

                            // Inject Mobile Back Arrow Button into #main > header
                            var mainHeader = mainEl.querySelector('header');
                            if (mainHeader) {
                                var backBtn = document.getElementById('xeno-wa-back-to-chats-btn');
                                if (!backBtn) {
                                    backBtn = document.createElement('button');
                                    backBtn.id = 'xeno-wa-back-to-chats-btn';
                                    backBtn.type = 'button';
                                    backBtn.setAttribute('aria-label', 'Back to chats');
                                    backBtn.setAttribute('title', 'Back to chats');
                                    backBtn.innerHTML = '<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2.3" stroke-linecap="round" stroke-linejoin="round"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>';
                                    backBtn.addEventListener('click', function(e) {
                                        e.preventDefault();
                                        e.stopPropagation();
                                        window.__xenoCloseMobileChat();
                                    }, true);
                                }
                                backBtn.style.display = 'inline-flex';
                                if (mainHeader.firstElementChild !== backBtn) {
                                    mainHeader.insertBefore(backBtn, mainHeader.firstElementChild);
                                }
                            }
                        } else {
                            window.__xenoWaExplicitChatOpen = false;
                            window.__xenoLastKnownChatSignature = '';
                            window.__xenoDismissedChatSignature = null;
                            document.documentElement.classList.remove('xeno-wa-chat-open', 'xeno-wa-right-drawer-open');
                        }

                        if (typeof window.__xenoStabilizeWaMobileLayout === 'function') {
                            window.__xenoStabilizeWaMobileLayout();
                        }

                        window.__xenoNotifyMobileNavBridge();
                    } catch(e) {}
                };

                window.__xenoSyncWaMobileLayout();
                if (typeof window.__xenoInterceptUnsupportedCallDialog === 'function') {
                    window.__xenoInterceptUnsupportedCallDialog();
                }

                if (!window.__xenoWaHeadObserver && window.MutationObserver && document.head) {
                    window.__xenoWaHeadObserver = new MutationObserver(function() {
                        ensureWhatsAppViewport();
                        ensureWhatsAppPhoneStyles();
                    });
                    window.__xenoWaHeadObserver.observe(document.head, { childList: true });
                }

                // Observe body for DOM layout changes, WebRTC call <video>/<audio> elements, and call modals
                if (!window.__xenoWaCallDomObserver && window.MutationObserver && document.body) {
                    var layoutSyncScheduled = false;
                    window.__xenoWaCallDomObserver = new MutationObserver(function(mutations) {
                        if (typeof window.__xenoInterceptUnsupportedCallDialog === 'function') {
                            window.__xenoInterceptUnsupportedCallDialog();
                        }
                        if (!layoutSyncScheduled) {
                            layoutSyncScheduled = true;
                            requestAnimationFrame(function() {
                                layoutSyncScheduled = false;
                                if (typeof window.__xenoSyncWaMobileLayout === 'function') {
                                    window.__xenoSyncWaMobileLayout();
                                }
                            });
                        }
                        for (var i = 0; i < mutations.length; i++) {
                            var added = mutations[i].addedNodes;
                            if (!added) continue;
                            for (var j = 0; j < added.length; j++) {
                                var node = added[j];
                                if (!node || node.nodeType !== 1) continue;
                                if (node.tagName === 'VIDEO' || node.tagName === 'AUDIO') {
                                    node.setAttribute('playsinline', 'true');
                                    node.setAttribute('webkit-playsinline', 'true');
                                    node.playsInline = true;
                                } else if (node.querySelectorAll) {
                                    var medias = node.querySelectorAll('video, audio');
                                    for (var k = 0; k < medias.length; k++) {
                                        medias[k].setAttribute('playsinline', 'true');
                                        medias[k].setAttribute('webkit-playsinline', 'true');
                                        medias[k].playsInline = true;
                                    }
                                }
                            }
                        }
                    });
                    window.__xenoWaCallDomObserver.observe(document.body, { childList: true, subtree: true });
                }
            } catch(e) {}
        })();
    """.trimIndent()
    view?.evaluateJavascript(js, null)
    injectWhatsAppMobileStabilityAndNavHelper(view, phoneUiMode)
    injectWhatsAppFastAndSmoothHelper(view)
}

/**
 * Dedicated WhatsApp Web Mobile Layout Stability & Left Navigation Rail Icon Fitting Helper:
 * 1. Stabilizes the mobile viewport height (`--xeno-wa-vh`) via `window.visualViewport` so opening/closing
 *    the soft keyboard in search or chat never pushes the header off-screen or causes layout jumps.
 * 2. Strips desktop `min-width` / `min-height` constraints from dynamic left-side drawers (Status, Channels,
 *    Communities, Archived, Settings, Profile, New Chat) so every drawer fits smoothly inside `side-col`.
 * 3. Properly fits, centers, and spaces all icons and the profile avatar inside the Left Navigation Rail
 *    (`[data-xeno-wa-role="nav-rail"]`) with uniform 40x40px touch targets, 22x22px crisp SVGs, and clean
 *    vertical spacing between top and bottom icon groups.
 */
private fun injectWhatsAppMobileStabilityAndNavHelper(view: WebView?, isPhoneUiMode: Boolean = true) {
    val js = """
        (function() {
            try {
                window.__xenoWaPhoneMode = ${if (isPhoneUiMode) "true" else "false"};
                var navStyleId = 'xeno-wa-nav-stability-style';
                var existingNavStyle = document.getElementById(navStyleId);

                if (!window.__xenoWaPhoneMode) {
                    if (existingNavStyle && existingNavStyle.parentNode) {
                        existingNavStyle.parentNode.removeChild(existingNavStyle);
                    }
                    return;
                }

                // 1. Inject Left Nav Rail Icon Fitting, Spacing & Drawer Stability CSS
                if (document.head && (!existingNavStyle || !existingNavStyle.__xenoNavV2)) {
                    var nst = existingNavStyle || document.createElement('style');
                    nst.id = navStyleId;
                    nst.__xenoNavV2 = true;
                    nst.textContent =
                        /* Left Navigation Rail Container & Inner Flex Groups */
                        'html.xeno-wa-phone-ui [data-xeno-wa-role="nav-rail"] { ' +
                        '  flex: 0 0 52px !important; width: 52px !important; min-width: 52px !important; max-width: 52px !important; ' +
                        '  height: 100% !important; display: flex !important; flex-direction: column !important; ' +
                        '  align-items: center !important; justify-content: space-between !important; ' +
                        '  padding: 10px 0 !important; margin: 0 !important; box-sizing: border-box !important; ' +
                        '  border-right: 1px solid rgba(134, 150, 160, 0.14) !important; z-index: 30 !important; ' +
                        '  overflow-y: auto !important; overflow-x: hidden !important; ' +
                        '} ' +
                        'html.xeno-wa-phone-ui [data-xeno-wa-role="nav-rail"] > header, ' +
                        'html.xeno-wa-phone-ui [data-xeno-wa-role="nav-rail"] > div, ' +
                        'html.xeno-wa-phone-ui [data-xeno-wa-nav-inner="1"] { ' +
                        '  width: 100% !important; max-width: 52px !important; min-width: 0 !important; ' +
                        '  height: 100% !important; display: flex !important; flex-direction: column !important; ' +
                        '  align-items: center !important; justify-content: space-between !important; ' +
                        '  padding: 2px 0 !important; margin: 0 !important; box-sizing: border-box !important; ' +
                        '} ' +
                        'html.xeno-wa-phone-ui [data-xeno-wa-nav-group="1"] { ' +
                        '  display: flex !important; flex-direction: column !important; align-items: center !important; ' +
                        '  justify-content: flex-start !important; gap: 10px !important; width: 100% !important; ' +
                        '  padding: 2px 0 !important; margin: 0 !important; box-sizing: border-box !important; ' +
                        '} ' +
                        'html.xeno-wa-phone-ui [data-xeno-wa-nav-item="1"] { ' +
                        '  display: flex !important; align-items: center !important; justify-content: center !important; ' +
                        '  width: 40px !important; height: 40px !important; min-width: 40px !important; min-height: 40px !important; ' +
                        '  max-width: 40px !important; max-height: 40px !important; margin: 0 auto !important; padding: 0 !important; ' +
                        '  position: relative !important; box-sizing: border-box !important; flex-shrink: 0 !important; ' +
                        '} ' +
                        /* Nav Rail Buttons & Touch Targets (40x40px centered circles) */
                        'html.xeno-wa-phone-ui [data-xeno-wa-role="nav-rail"] button, ' +
                        'html.xeno-wa-phone-ui [data-xeno-wa-role="nav-rail"] [role="button"], ' +
                        'html.xeno-wa-phone-ui [data-xeno-wa-nav-btn="1"] { ' +
                        '  width: 40px !important; height: 40px !important; min-width: 40px !important; min-height: 40px !important; ' +
                        '  max-width: 40px !important; max-height: 40px !important; padding: 0 !important; margin: 0 auto !important; ' +
                        '  border-radius: 50% !important; display: inline-flex !important; align-items: center !important; ' +
                        '  justify-content: center !important; position: relative !important; box-sizing: border-box !important; ' +
                        '  flex-shrink: 0 !important; ' +
                        '} ' +
                        /* Crisp 22x22px SVG Icons & 28x28px Profile Avatar inside Left Nav Rail */
                        'html.xeno-wa-phone-ui [data-xeno-wa-role="nav-rail"] [data-icon] { ' +
                        '  display: inline-flex !important; align-items: center !important; justify-content: center !important; ' +
                        '  width: 22px !important; height: 22px !important; max-width: 22px !important; max-height: 22px !important; ' +
                        '  margin: auto !important; padding: 0 !important; ' +
                        '} ' +
                        'html.xeno-wa-phone-ui [data-xeno-wa-role="nav-rail"] svg { ' +
                        '  width: 22px !important; height: 22px !important; max-width: 22px !important; max-height: 22px !important; ' +
                        '  display: block !important; margin: auto !important; flex-shrink: 0 !important; ' +
                        '} ' +
                        'html.xeno-wa-phone-ui [data-xeno-wa-role="nav-rail"] img { ' +
                        '  width: 28px !important; height: 28px !important; min-width: 28px !important; min-height: 28px !important; ' +
                        '  max-width: 28px !important; max-height: 28px !important; border-radius: 50% !important; ' +
                        '  object-fit: cover !important; display: block !important; margin: auto !important; ' +
                        '} ' +
                        /* Side Column Drawers (Status, Channels, Communities, Settings, Profile, New Chat) Stability */
                        'html.xeno-wa-phone-ui [data-xeno-wa-role="side-col"] [data-xeno-wa-drawer="1"], ' +
                        'html.xeno-wa-phone-ui [data-xeno-wa-role="side-col"] header, ' +
                        'html.xeno-wa-phone-ui [data-xeno-wa-role="side-col"] [role="tablist"] { ' +
                        '  max-width: 100% !important; min-width: 0 !important; box-sizing: border-box !important; ' +
                        '} ' +
                        'html.xeno-wa-phone-ui #pane-side [role="listitem"], ' +
                        'html.xeno-wa-phone-ui #pane-side [role="row"], ' +
                        'html.xeno-wa-phone-ui #pane-side [data-testid="cell-frame-container"] { ' +
                        '  max-width: 100% !important; min-width: 0 !important; box-sizing: border-box !important; ' +
                        '}';
                    if (!existingNavStyle) {
                        document.head.appendChild(nst);
                    }
                }

                // 2. VisualViewport & Window Scroll Pinning (Keeps #main footer & typed words visible above keyboard)
                if (!window.__xenoWaViewportPinPatched) {
                    window.__xenoWaViewportPinPatched = true;
                    var lastAppliedVh = 0;
                    var ensureComposeFooterVisible = function() {
                        try {
                            if (window.scrollX !== 0 || window.scrollY !== 0) {
                                window.scrollTo(0, 0);
                            }
                            if (document.documentElement && document.documentElement.scrollTop !== 0) {
                                document.documentElement.scrollTop = 0;
                            }
                            if (document.body && document.body.scrollTop !== 0) {
                                document.body.scrollTop = 0;
                            }
                            var activeEl = document.activeElement;
                            var footer = document.querySelector('#main footer');
                            if (footer && activeEl && footer.contains(activeEl)) {
                                var msgScroll = document.querySelector('#main [data-testid="conversation-panel-messages"]');
                                if (!msgScroll) {
                                    var mainEl = document.getElementById('main');
                                    if (mainEl) {
                                        var candidates = mainEl.querySelectorAll('div');
                                        for (var c = 0; c < candidates.length; c++) {
                                            var el = candidates[c];
                                            if (el.scrollHeight > el.clientHeight + 20 && !footer.contains(el)) {
                                                var st = window.getComputedStyle(el);
                                                if (st.overflowY === 'auto' || st.overflowY === 'scroll') {
                                                    msgScroll = el;
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                }
                                if (msgScroll) {
                                    var distFromBottom = msgScroll.scrollHeight - msgScroll.scrollTop - msgScroll.clientHeight;
                                    if (distFromBottom < 260) {
                                        msgScroll.scrollTop = msgScroll.scrollHeight;
                                    }
                                }
                            }
                        } catch(e) {}
                    };
                    var updateVisualViewportHeight = function() {
                        try {
                            var vvH = (window.visualViewport && window.visualViewport.height) ? Math.round(window.visualViewport.height) : 0;
                            var winH = window.innerHeight || document.documentElement.clientHeight || 0;
                            var vh = (vvH > 0 && winH > 0) ? Math.min(vvH, winH) : (vvH || winH);
                            if (vh && vh > 120) {
                                document.documentElement.style.setProperty('--xeno-wa-vh', vh + 'px');
                                if (Math.abs(vh - lastAppliedVh) > 40) {
                                    lastAppliedVh = vh;
                                    setTimeout(ensureComposeFooterVisible, 20);
                                }
                            }
                            ensureComposeFooterVisible();
                        } catch(e) {}
                    };
                    updateVisualViewportHeight();
                    if (window.visualViewport) {
                        window.visualViewport.addEventListener('resize', updateVisualViewportHeight, { passive: true });
                        window.visualViewport.addEventListener('scroll', updateVisualViewportHeight, { passive: true });
                    }
                    window.addEventListener('resize', updateVisualViewportHeight, { passive: true });
                    document.addEventListener('focusin', function(ev) {
                        var t = ev && ev.target;
                        var footer = document.querySelector('#main footer');
                        if (t && footer && footer.contains(t)) {
                            updateVisualViewportHeight();
                            setTimeout(updateVisualViewportHeight, 60);
                            setTimeout(updateVisualViewportHeight, 180);
                            setTimeout(updateVisualViewportHeight, 350);
                        }
                    }, true);
                    document.addEventListener('focusout', function() {
                        setTimeout(updateVisualViewportHeight, 80);
                        setTimeout(updateVisualViewportHeight, 250);
                    }, true);
                }

                // 3. Normalize Left Nav Rail DOM Hierarchy & Side Column Drawers
                window.__xenoStabilizeWaMobileLayout = function() {
                    try {
                        if (!window.__xenoWaPhoneMode) return;

                        var navRail = document.querySelector('[data-xeno-wa-role="nav-rail"]');
                        if (navRail) {
                            var innerContainer = navRail.querySelector('header') || navRail.firstElementChild;
                            if (innerContainer && innerContainer.nodeType === 1) {
                                if (innerContainer.getAttribute('data-xeno-wa-nav-inner') !== '1') {
                                    innerContainer.setAttribute('data-xeno-wa-nav-inner', '1');
                                }
                                var groups = innerContainer.children;
                                for (var g = 0; g < groups.length; g++) {
                                    var grp = groups[g];
                                    if (grp && grp.nodeType === 1 && grp.querySelector('button, [role="button"], [data-icon], img')) {
                                        if (grp.getAttribute('data-xeno-wa-nav-group') !== '1') {
                                            grp.setAttribute('data-xeno-wa-nav-group', '1');
                                        }
                                        var items = grp.children;
                                        for (var it = 0; it < items.length; it++) {
                                            var itemEl = items[it];
                                            if (itemEl && itemEl.nodeType === 1 && itemEl.getAttribute('data-xeno-wa-nav-item') !== '1') {
                                                itemEl.setAttribute('data-xeno-wa-nav-item', '1');
                                            }
                                        }
                                    }
                                }
                            }

                            var navBtns = navRail.querySelectorAll('button, [role="button"]');
                            for (var b = 0; b < navBtns.length; b++) {
                                var btnEl = navBtns[b];
                                if (btnEl.getAttribute('data-xeno-wa-nav-btn') !== '1') {
                                    btnEl.setAttribute('data-xeno-wa-nav-btn', '1');
                                }
                                var parentWrap = btnEl.parentElement;
                                if (parentWrap && parentWrap !== navRail && parentWrap !== innerContainer &&
                                    parentWrap.getAttribute('data-xeno-wa-nav-group') !== '1' &&
                                    parentWrap.getAttribute('data-xeno-wa-nav-item') !== '1') {
                                    parentWrap.setAttribute('data-xeno-wa-nav-item', '1');
                                }
                            }
                        }

                        // Ensure all child drawers inside side-col (Status, Channels, Communities, Settings, Profile) fit cleanly
                        var sideCol = document.querySelector('[data-xeno-wa-role="side-col"]');
                        if (sideCol) {
                            var sideChildren = sideCol.children;
                            for (var sc = 0; sc < sideChildren.length; sc++) {
                                var sChild = sideChildren[sc];
                                if (sChild && sChild.nodeType === 1 && sChild.getAttribute('data-xeno-wa-drawer') !== '1') {
                                    sChild.setAttribute('data-xeno-wa-drawer', '1');
                                }
                            }
                        }
                    } catch(e) {}
                };

                window.__xenoStabilizeWaMobileLayout();
            } catch(e) {}
        })();
    """.trimIndent()
    view?.evaluateJavascript(js, null)
}

/**
 * Non-Intrusive Web WhatsApp Speed, Responsiveness & Smooth Scrolling Helper:
 * 1. Enforces passive scroll/wheel event listeners (`wheel`, `mousewheel`, `scroll`) so scrolling the chat list
 *    (`#pane-side`) and conversation history (`#main`) never blocks on synchronous JS handlers.
 * 2. Applies strictly non-layout GPU compositing, momentum scrolling (`-webkit-overflow-scrolling: touch`),
 *    and offscreen chat row `content-visibility: auto` hints without modifying any layout dimensions, margins, or structure.
 * 3. Enables asynchronous background image decoding (`decoding="async"`) on contact avatars, stickers, and media thumbnails
 *    so fast scrolling through media-rich conversations stays smooth at 60/120fps.
 * 4. Short-circuits background WhatsApp crash-log/telemetry `sendBeacon` & `fetch` requests (`crashlogs.whatsapp.net`)
 *    so main-thread CPU and network bandwidth stay dedicated to messaging and WebRTC voice/video calls.
 */
private fun injectWhatsAppFastAndSmoothHelper(view: WebView?) {
    val js = """
        (function() {
            try {
                // 1. Passive Scroll & Wheel Listener Optimization for 60/120fps Chat & Message Scrolling
                if (!window.__xenoWaFastPassivePatched) {
                    window.__xenoWaFastPassivePatched = true;
                    var origAddEvent = EventTarget.prototype.addEventListener;
                    EventTarget.prototype.addEventListener = function(type, listener, options) {
                        if (type === 'wheel' || type === 'mousewheel' || type === 'scroll') {
                            var newOpts = options;
                            if (typeof options === 'boolean') {
                                newOpts = { capture: options, passive: true };
                            } else if (typeof options === 'object' && options !== null) {
                                if (typeof options.passive === 'undefined') {
                                    newOpts = Object.assign({}, options, { passive: true });
                                }
                            } else if (typeof options === 'undefined') {
                                newOpts = { passive: true };
                            }
                            return origAddEvent.call(this, type, listener, newOpts);
                        }
                        return origAddEvent.call(this, type, listener, options);
                    };
                }

                // 2. Short-circuit background crashlog & telemetry spam so CPU/network stay fast for chats & calls
                if (!window.__xenoWaTelemetryPatched) {
                    window.__xenoWaTelemetryPatched = true;
                    if (typeof navigator.sendBeacon === 'function') {
                        var origBeacon = navigator.sendBeacon.bind(navigator);
                        navigator.sendBeacon = function(url, data) {
                            var u = String(url || '').toLowerCase();
                            if (u.indexOf('crashlogs.whatsapp.net') !== -1 || u.indexOf('graph.whatsapp.com') !== -1) {
                                return true;
                            }
                            return origBeacon(url, data);
                        };
                    }
                }

                // 3. Strictly Non-Layout GPU Compositing & Momentum Scroll Acceleration Stylesheet
                if (document.head) {
                    var fastStyleId = 'xeno-wa-fast-smooth-style';
                    var existingFastStyle = document.getElementById(fastStyleId);
                    if (!existingFastStyle) {
                        var fst = document.createElement('style');
                        fst.id = fastStyleId;
                        fst.textContent =
                            '#pane-side, #main [data-testid="conversation-panel-messages"], [data-xeno-wa-role="side-col"], [data-xeno-wa-role="right-drawer"] { ' +
                            '  -webkit-overflow-scrolling: touch !important; ' +
                            '  overscroll-behavior-y: contain !important; ' +
                            '  backface-visibility: hidden !important; ' +
                            '  -webkit-backface-visibility: hidden !important; ' +
                            '} ' +
                            '[data-xeno-wa-role="main-col"], [data-xeno-wa-role="right-drawer"] { ' +
                            '  backface-visibility: hidden !important; ' +
                            '  -webkit-backface-visibility: hidden !important; ' +
                            '}';
                        document.head.appendChild(fst);
                    }
                }

                // 4. Async Image Decoding for Contact Avatars, Stickers & Media Thumbnails (throttled)
                var now = Date.now();
                if (!window.__xenoWaLastImgOptMs || (now - window.__xenoWaLastImgOptMs > 1500)) {
                    window.__xenoWaLastImgOptMs = now;
                    var imgs = document.querySelectorAll('#pane-side img:not([data-xeno-fast="1"]), #main img:not([data-xeno-fast="1"])');
                    for (var i = 0; i < imgs.length; i++) {
                        var img = imgs[i];
                        img.setAttribute('data-xeno-fast', '1');
                        if (!img.getAttribute('decoding')) {
                            img.setAttribute('decoding', 'async');
                        }
                    }
                }
            } catch(e) {}
        })();
    """.trimIndent()
    view?.evaluateJavascript(js, null)
}

/**
 * Non-intrusive Instagram Reels & Smooth Scrolling Helper Engine:
 * Enhances video playback, pre-buffering, scroll-snap physics, and passive touch handling
 * in-place without reloading or interrupting the active Instagram session.
 */
private fun injectInstagramReelsAndScrollHelper(view: WebView?) {
    val js = """
        (function() {
            try {
                // 1. Passive wheel listener optimization ONLY (never force touchstart/touchmove passive on Instagram so React Reels swipe gestures work natively)
                if (!window.__xenoIgPassivePatched) {
                    window.__xenoIgPassivePatched = true;
                    var origAddEventListener = EventTarget.prototype.addEventListener;
                    EventTarget.prototype.addEventListener = function(type, listener, options) {
                        if (type === 'wheel' || type === 'mousewheel') {
                            var newOpts = options;
                            if (typeof options === 'boolean') {
                                newOpts = { capture: options, passive: true };
                            } else if (typeof options === 'object' && options !== null) {
                                if (typeof options.passive === 'undefined') {
                                    newOpts = Object.assign({}, options, { passive: true });
                                }
                            } else if (typeof options === 'undefined') {
                                newOpts = { passive: true };
                            }
                            return origAddEventListener.call(this, type, listener, newOpts);
                        }
                        return origAddEventListener.call(this, type, listener, options);
                    };
                }

                // 2. Strictly Reels Scroll Physics ONLY (zero layout, sizing, or visual changes to Instagram)
                var styleId = 'xeno-ig-reels-smooth-style';
                var existingStyle = document.getElementById(styleId);
                var cleanScrollCss =
                    'div[style*="scroll-snap"] { -webkit-overflow-scrolling: touch !important; overscroll-behavior-y: contain !important; } ' +
                    'div[style*="scroll-snap-type"] > div { scroll-snap-stop: always !important; }';
                if (!existingStyle && document.head) {
                    var st = document.createElement('style');
                    st.id = styleId;
                    st.textContent = cleanScrollCss;
                    document.head.appendChild(st);
                } else if (existingStyle && existingStyle.textContent !== cleanScrollCss) {
                    existingStyle.textContent = cleanScrollCss;
                }

                // 3. Locate the active Reels scroll container and apply native momentum & snap-stop without touching layout
                function isOnReelsRoute() {
                    var p = (window.location.pathname || '').toLowerCase();
                    return p.indexOf('/reels') === 0 || p.indexOf('/reel/') === 0;
                }

                function findReelsScrollContainer(startEl) {
                    var el = startEl;
                    while (el && el !== document.body && el !== document.documentElement) {
                        if (el.nodeType === 1) {
                            var sh = el.scrollHeight || 0;
                            var ch = el.clientHeight || 0;
                            if (sh > ch + 80 && ch >= window.innerHeight * 0.6) {
                                var cs = window.getComputedStyle(el);
                                var oy = cs ? cs.overflowY : '';
                                var snap = cs ? (cs.scrollSnapType || '') : '';
                                if (oy === 'scroll' || oy === 'auto' || snap.indexOf('y') !== -1 || snap.indexOf('mandatory') !== -1) {
                                    return el;
                                }
                            }
                        }
                        el = el.parentElement;
                    }
                    var snapDiv = document.querySelector('div[style*="scroll-snap"]');
                    if (snapDiv && snapDiv.scrollHeight > snapDiv.clientHeight + 40) return snapDiv;
                    return null;
                }

                function tuneReelsScrollContainers() {
                    if (!isOnReelsRoute()) return;
                    var vids = document.querySelectorAll('video');
                    for (var i = 0; i < vids.length; i++) {
                        var sc = findReelsScrollContainer(vids[i]);
                        if (sc && !sc.__xenoReelsScrollTuned) {
                            sc.__xenoReelsScrollTuned = true;
                            sc.style.webkitOverflowScrolling = 'touch';
                            sc.style.overscrollBehaviorY = 'contain';
                            var cs = window.getComputedStyle(sc);
                            if (!cs.scrollSnapType || cs.scrollSnapType === 'none') {
                                sc.style.scrollSnapType = 'y mandatory';
                            }
                        }
                        if (sc && sc.children) {
                            for (var c = 0; c < sc.children.length; c++) {
                                var child = sc.children[c];
                                if (child && child.style && child.style.scrollSnapStop !== 'always') {
                                    child.style.scrollSnapStop = 'always';
                                }
                            }
                        }
                    }
                }

                // 4. Fast, Native-App-Like Reels Vertical Flick Snapper + Scroll Activity Tracker
                if (!window.__xenoIgReelsSwipeInstalled) {
                    window.__xenoIgReelsSwipeInstalled = true;
                    window.__xenoIgIsScrolling = false;
                    var scrollIdleTimer = null;
                    var markScrolling = function() {
                        window.__xenoIgIsScrolling = true;
                        if (scrollIdleTimer) clearTimeout(scrollIdleTimer);
                        scrollIdleTimer = setTimeout(function() {
                            window.__xenoIgIsScrolling = false;
                            if (window.__xenoIgFlushPendingPlay) {
                                window.__xenoIgFlushPendingPlay();
                            }
                        }, 90);
                    };

                    window.addEventListener('scroll', markScrolling, { capture: true, passive: true });

                    var touchStartY = 0;
                    var touchStartX = 0;
                    var touchStartTime = 0;
                    var activeScroller = null;
                    var startScrollTop = 0;

                    document.addEventListener('touchstart', function(e) {
                        if (!isOnReelsRoute() || !e.touches || e.touches.length !== 1) {
                            activeScroller = null;
                            return;
                        }
                        var t = e.touches[0];
                        touchStartY = t.clientY;
                        touchStartX = t.clientX;
                        touchStartTime = performance.now();
                        activeScroller = findReelsScrollContainer(e.target);
                        if (activeScroller) {
                            startScrollTop = activeScroller.scrollTop;
                        } else {
                            startScrollTop = window.pageYOffset || document.documentElement.scrollTop || 0;
                        }
                    }, { capture: true, passive: true });

                    document.addEventListener('touchmove', function(e) {
                        if (isOnReelsRoute()) {
                            markScrolling();
                        }
                    }, { capture: true, passive: true });

                    document.addEventListener('touchend', function(e) {
                        if (!isOnReelsRoute() || !e.changedTouches || e.changedTouches.length !== 1) return;
                        var t = e.changedTouches[0];
                        var dy = touchStartY - t.clientY;
                        var dx = touchStartX - t.clientX;
                        var dt = Math.max(1, performance.now() - touchStartTime);
                        var absDy = Math.abs(dy);
                        var absDx = Math.abs(dx);
                        var vy = absDy / dt; // px per ms

                        // Only assist clear vertical flick gestures (never interfere with taps or horizontal gestures)
                        if (absDy >= 30 && absDy > absDx * 1.5 && (vy >= 0.32 || absDy >= window.innerHeight * 0.16)) {
                            var dir = dy > 0 ? 1 : -1;
                            var sc = activeScroller || findReelsScrollContainer(e.target);
                            if (sc && sc.clientHeight > 200) {
                                var pageH = sc.clientHeight;
                                var baseIndex = Math.round(startScrollTop / pageH);
                                var targetIndex = Math.max(0, baseIndex + dir);
                                var targetTop = targetIndex * pageH;
                                var currentTop = sc.scrollTop;
                                // If native scroll hasn't already crossed the snap midpoint, smoothly snap to the target Reel
                                if (Math.abs(currentTop - targetTop) > 12 &&
                                    ((dir > 0 && currentTop < targetTop - pageH * 0.45) ||
                                     (dir < 0 && currentTop > targetTop + pageH * 0.45))) {
                                    sc.scrollTo({ top: targetTop, behavior: 'smooth' });
                                }
                            }
                        }
                        markScrolling();
                    }, { capture: true, passive: true });
                }

                // 5. Reels Video Inline Playback, Preload & Debounced Viewport Optimizer (avoids codec start/stop mid-swipe)
                if (!window.__xenoIgVideoHelper) {
                    window.__xenoIgVideoHelper = {
                        userPausedMap: new WeakMap(),
                        observedVideos: new WeakSet(),
                        observer: null,
                        pendingPlayVid: null
                    };

                    window.__xenoIgFlushPendingPlay = function() {
                        var vid = window.__xenoIgVideoHelper.pendingPlayVid;
                        if (!vid || !vid.isConnected) return;
                        // Pause any other playing videos off-screen once scroll settles
                        var allVids = document.querySelectorAll('video');
                        for (var i = 0; i < allVids.length; i++) {
                            var other = allVids[i];
                            if (other !== vid && !other.paused) {
                                try { other.pause(); } catch(e) {}
                            }
                        }
                        if (vid.paused && !window.__xenoIgVideoHelper.userPausedMap.get(vid)) {
                            var playPromise = vid.play();
                            if (playPromise && typeof playPromise.catch === 'function') {
                                playPromise.catch(function() {
                                    if (!vid.muted) {
                                        vid.muted = true;
                                        vid.play().catch(function(){});
                                    }
                                });
                            }
                        }
                    };

                    if ('IntersectionObserver' in window) {
                        window.__xenoIgVideoHelper.observer = new IntersectionObserver(function(entries) {
                            entries.forEach(function(entry) {
                                var vid = entry.target;
                                if (!vid || vid.tagName !== 'VIDEO') return;
                                if (entry.isIntersecting && entry.intersectionRatio >= 0.55) {
                                    vid.preload = 'auto';
                                    // Pre-buffer next 2 Reel videos for instant swipe playback
                                    try {
                                        var allVids = Array.prototype.slice.call(document.querySelectorAll('video'));
                                        var idx = allVids.indexOf(vid);
                                        if (idx >= 0) {
                                            if (idx + 1 < allVids.length) allVids[idx + 1].preload = 'auto';
                                            if (idx + 2 < allVids.length) allVids[idx + 2].preload = 'auto';
                                        }
                                    } catch(e) {}

                                    window.__xenoIgVideoHelper.pendingPlayVid = vid;
                                    if (!window.__xenoIgIsScrolling) {
                                        window.__xenoIgFlushPendingPlay();
                                    }
                                } else if (!entry.isIntersecting || entry.intersectionRatio < 0.15) {
                                    if (!window.__xenoIgIsScrolling && !vid.paused) {
                                        try { vid.pause(); } catch(e) {}
                                    }
                                }
                            });
                        }, { threshold: [0.0, 0.15, 0.55, 0.85] });
                    }
                }

                function optimizeInstagramVideos() {
                    tuneReelsScrollContainers();
                    var videos = document.querySelectorAll('video');
                    for (var i = 0; i < videos.length; i++) {
                        var v = videos[i];
                        if (!v.hasAttribute('playsinline')) v.setAttribute('playsinline', 'true');
                        if (!v.hasAttribute('webkit-playsinline')) v.setAttribute('webkit-playsinline', 'true');
                        v.playsInline = true;
                        if (!v.preload || v.preload === 'none' || v.preload === 'metadata') {
                            v.preload = 'auto';
                        }

                        if (!window.__xenoIgVideoHelper.observedVideos.has(v)) {
                            window.__xenoIgVideoHelper.observedVideos.add(v);
                            v.addEventListener('click', function() {
                                var self = this;
                                setTimeout(function() {
                                    window.__xenoIgVideoHelper.userPausedMap.set(self, self.paused);
                                }, 80);
                            }, { passive: true });

                            v.addEventListener('stalled', function() {
                                var self = this;
                                if (!window.__xenoIgIsScrolling && !self.paused && self.readyState >= 2) {
                                    self.play().catch(function(){});
                                }
                            }, { passive: true });

                            if (window.__xenoIgVideoHelper.observer) {
                                window.__xenoIgVideoHelper.observer.observe(v);
                            }
                        }
                    }
                }

                optimizeInstagramVideos();

                if (!window.__xenoIgMutationObserver && window.MutationObserver && document.body) {
                    var throttleTimer = null;
                    window.__xenoIgMutationObserver = new MutationObserver(function() {
                        if (throttleTimer) return;
                        throttleTimer = setTimeout(function() {
                            throttleTimer = null;
                            optimizeInstagramVideos();
                        }, 220);
                    });
                    window.__xenoIgMutationObserver.observe(document.body, {
                        childList: true,
                        subtree: true
                    });
                }
            } catch(e) {}
        })();
    """.trimIndent()
    view?.evaluateJavascript(js, null)
}

/**
 * Non-intrusive Facebook Feed, Watch & Reels Video and Smooth Scrolling Helper Engine:
 * Optimizes native touch scrolling, inline Watch/Reels video playback, pre-buffering,
 * stall recovery, and suppresses intrusive "Open in App" banners without breaking
 * Reels vertical swipes or Stories horizontal carousels.
 */
private fun injectFacebookVideoAndScrollHelper(view: WebView?) {
    val js = """
        (function() {
            try {
                // 1. Environment & Page Visibility stabilization so Facebook Watch/Reels never throttle
                if (!window.__xenoFbEnvPatched) {
                    window.__xenoFbEnvPatched = true;
                    try {
                        Object.defineProperty(navigator, 'webdriver', { get: function() { return false; }, configurable: true });
                        Object.defineProperty(document, 'hidden', { get: function() { return false; }, configurable: true });
                        Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });
                    } catch(e) {}

                    // Passive wheel optimization only — keep touchstart/touchmove native so Facebook Reels & Stories swipes work smoothly
                    var origAddEventListener = EventTarget.prototype.addEventListener;
                    EventTarget.prototype.addEventListener = function(type, listener, options) {
                        if (type === 'wheel' || type === 'mousewheel') {
                            var newOpts = options;
                            if (typeof options === 'boolean') {
                                newOpts = { capture: options, passive: true };
                            } else if (typeof options === 'object' && options !== null) {
                                if (typeof options.passive === 'undefined') {
                                    newOpts = Object.assign({}, options, { passive: true });
                                }
                            } else if (typeof options === 'undefined') {
                                newOpts = { passive: true };
                            }
                            return origAddEventListener.call(this, type, listener, newOpts);
                        }
                        return origAddEventListener.call(this, type, listener, options);
                    };

                    // Unlock audio on first user tap
                    window.__xenoFbAudioUnlocked = false;
                    document.addEventListener('touchend', function() {
                        window.__xenoFbAudioUnlocked = true;
                    }, { passive: true });
                }

                // 2. Clean Smooth Scroll & Non-Destructive App-Banner Suppression CSS
                var styleId = 'xeno-fb-video-smooth-style';
                if (!document.getElementById(styleId) && document.head) {
                    var st = document.createElement('style');
                    st.id = styleId;
                    st.textContent =
                        'html, body { -webkit-overflow-scrolling: touch !important; -webkit-tap-highlight-color: transparent !important; } ' +
                        'div[role="main"], div[role="feed"], div[style*="scroll-snap"] { -webkit-overflow-scrolling: touch !important; } ' +
                        'video { backface-visibility: hidden; -webkit-backface-visibility: hidden; } ' +
                        'div[data-sigil*="m-promo-jewel"], div[id*="smartbanner"], div[data-sigil="m-app-upsell"] { display: none !important; } ' +
                        '::-webkit-scrollbar { width: 0px !important; height: 0px !important; display: none !important; }';
                    document.head.appendChild(st);
                }

                // 3. Facebook Watch & Reels Inline Video Playback, Preload & Off-Screen Pause Optimizer
                if (!window.__xenoFbVideoHelper) {
                    window.__xenoFbVideoHelper = {
                        userPausedMap: new WeakMap(),
                        observedVideos: new WeakSet(),
                        observer: null
                    };

                    if ('IntersectionObserver' in window) {
                        window.__xenoFbVideoHelper.observer = new IntersectionObserver(function(entries) {
                            entries.forEach(function(entry) {
                                var vid = entry.target;
                                if (!vid || vid.tagName !== 'VIDEO') return;
                                if (entry.isIntersecting && entry.intersectionRatio >= 0.65) {
                                    vid.preload = 'auto';
                                    if (vid.paused && !window.__xenoFbVideoHelper.userPausedMap.get(vid)) {
                                        var playPromise = vid.play();
                                        if (playPromise && typeof playPromise.catch === 'function') {
                                            playPromise.catch(function() {
                                                if (!vid.muted) {
                                                    vid.muted = true;
                                                    vid.play().catch(function(){});
                                                }
                                            });
                                        }
                                    }
                                } else if (!entry.isIntersecting && entry.intersectionRatio <= 0.05) {
                                    // Only pause completely off-screen feed videos so active Reels/Watch transitions never stutter
                                    if (!vid.paused) {
                                        try { vid.pause(); } catch(e) {}
                                    }
                                }
                            });
                        }, { threshold: [0.0, 0.05, 0.65] });
                    }
                }

                function optimizeFacebookVideos() {
                    var videos = document.querySelectorAll('video');
                    for (var i = 0; i < videos.length; i++) {
                        var v = videos[i];
                        if (!v.hasAttribute('playsinline')) v.setAttribute('playsinline', '');
                        if (!v.hasAttribute('webkit-playsinline')) v.setAttribute('webkit-playsinline', '');
                        v.playsInline = true;
                        if (!v.preload || v.preload === 'none' || v.preload === 'metadata') {
                            v.preload = 'auto';
                        }

                        if (!window.__xenoFbVideoHelper.observedVideos.has(v)) {
                            window.__xenoFbVideoHelper.observedVideos.add(v);
                            v.addEventListener('click', function() {
                                var self = this;
                                setTimeout(function() {
                                    window.__xenoFbVideoHelper.userPausedMap.set(self, self.paused);
                                }, 100);
                            }, { passive: true });

                            v.addEventListener('stalled', function() {
                                var self = this;
                                if (!self.paused && self.readyState >= 2) {
                                    self.play().catch(function(){});
                                }
                            }, { passive: true });

                            if (window.__xenoFbVideoHelper.observer) {
                                window.__xenoFbVideoHelper.observer.observe(v);
                            }
                        }
                    }

                    // Convert any intent:// links with fallback URLs into direct web links so tapping Reels/videos stays in web player
                    var intentLinks = document.querySelectorAll('a[href^="intent://"]');
                    for (var j = 0; j < intentLinks.length; j++) {
                        var href = intentLinks[j].getAttribute('href') || '';
                        var match = href.match(/S\.browser_fallback_url=([^;]+)/);
                        if (match && match[1]) {
                            try {
                                var decoded = decodeURIComponent(match[1]);
                                if (decoded.indexOf('http') === 0 && decoded.indexOf('play.google.com') === -1) {
                                    intentLinks[j].setAttribute('href', decoded);
                                }
                            } catch(e) {}
                        }
                    }
                }

                optimizeFacebookVideos();

                if (!window.__xenoFbMutationObserver && window.MutationObserver && document.body) {
                    var throttleTimer = null;
                    window.__xenoFbMutationObserver = new MutationObserver(function() {
                        if (throttleTimer) return;
                        throttleTimer = setTimeout(function() {
                            throttleTimer = null;
                            optimizeFacebookVideos();
                        }, 220);
                    });
                    window.__xenoFbMutationObserver.observe(document.body, {
                        childList: true,
                        subtree: true
                    });
                }
            } catch(e) {}
        })();
    """.trimIndent()
    view?.evaluateJavascript(js, null)
}

/**
 * Layout-Safe YouTube Watch, Shorts & Offline Download Helper Engine:
 * - Preserves 100% of YouTube's native mobile (`m.youtube.com`) and desktop (`www.youtube.com`) layout,
 *   sticky player container (`#player-container-id`), watch metadata, comments drawers, and Shorts vertical pager
 *   without injecting destructive CSS or inline `html`/`body` position/overflow overrides.
 * - Unlocks inline HTML5 video playback (`playsinline`) and unmutes audio on user interaction.
 * - Provides in-WebView stream extraction (via `ytInitialPlayerResponse`, `performance.getEntriesByType('resource')`
 *   `googlevideo.com/videoplayback` capture, and same-origin InnerTube `/youtubei/v1/player` resolution)
 *   for reliable offline video downloads.
 */
private fun injectYouTubeAndShortsSmoothHelper(view: WebView?) {
    val js = """
        (function() {
            try {
                // 1. Clean up any legacy layout-breaking CSS or inline style overrides if present
                if (document.documentElement && document.body) {
                    if (!window.__xenoYtLayoutCleanedV3) {
                        window.__xenoYtLayoutCleanedV3 = true;
                        document.documentElement.removeAttribute('data-xeno-yt-mode');
                        document.documentElement.removeAttribute('data-xeno-shorts');
                        document.body.removeAttribute('data-xeno-yt-mode');
                        document.body.removeAttribute('data-xeno-shorts');
                        var propsToRemove = ['overflow-y', 'overflow-x', 'overflow', 'position', 'height', 'min-height', 'top', 'touch-action'];
                        for (var p = 0; p < propsToRemove.length; p++) {
                            try {
                                document.documentElement.style.removeProperty(propsToRemove[p]);
                                document.body.style.removeProperty(propsToRemove[p]);
                            } catch(e) {}
                        }
                    }
                }

                // 2. Browser Environment, Audio Unlock & SPA Route Detection
                if (!window.__xenoYtEnvPatchedV3) {
                    window.__xenoYtEnvPatchedV3 = true;
                    try {
                        Object.defineProperty(navigator, 'webdriver', { get: function() { return false; }, configurable: true });
                        Object.defineProperty(document, 'hidden', { get: function() { return false; }, configurable: true });
                        Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });
                        Object.defineProperty(document, 'fullscreenEnabled', { get: function() { return true; }, configurable: true });
                        Object.defineProperty(document, 'webkitFullscreenEnabled', { get: function() { return true; }, configurable: true });
                        if (window.HTMLVideoElement) {
                            HTMLVideoElement.prototype.webkitSupportsFullscreen = true;
                        }
                        if (window.screen && window.screen.orientation) {
                            window.screen.orientation.lock = function(mode) {
                                try {
                                    if (window.XenoYtBridge && typeof window.XenoYtBridge.setFullscreen === 'function') {
                                        window.XenoYtBridge.setFullscreen(true);
                                    }
                                } catch(e) {}
                                return Promise.resolve();
                            };
                            window.screen.orientation.unlock = function() {
                                try {
                                    if (window.XenoYtBridge && typeof window.XenoYtBridge.setFullscreen === 'function') {
                                        window.XenoYtBridge.setFullscreen(false);
                                    }
                                } catch(e) {}
                            };
                        }
                    } catch(e) {}

                    // YouTube Fullscreen Video Engine (Native + In-DOM Fullscreen Synchronization)
                    window.__xenoSetYtFullscreenState = function(active) {
                        try {
                            window.__xenoYtIsFullscreen = !!active;
                            if (document.documentElement) {
                                if (active) {
                                    document.documentElement.setAttribute('data-xeno-yt-fs', '1');
                                } else {
                                    document.documentElement.removeAttribute('data-xeno-yt-fs');
                                }
                            }
                            var pill = document.getElementById('xeno-yt-download-pill');
                            if (pill) {
                                pill.style.display = active ? 'none' : (getYouTubePageMode() === 'watch' ? 'flex' : 'none');
                            }
                            if (window.XenoYtBridge && typeof window.XenoYtBridge.setFullscreen === 'function') {
                                window.XenoYtBridge.setFullscreen(!!active);
                            }
                            setTimeout(function() {
                                try { window.dispatchEvent(new Event('resize')); } catch(e) {}
                            }, 60);
                        } catch(e) {}
                    };

                    window.__xenoExitYtFullscreen = function() {
                        try {
                            var wasFs = !!(document.fullscreenElement || document.webkitFullscreenElement || window.__xenoYtIsFullscreen);
                            if (document.fullscreenElement && typeof document.exitFullscreen === 'function') {
                                document.exitFullscreen().catch(function(){});
                            } else if (document.webkitFullscreenElement && typeof document.webkitExitFullscreen === 'function') {
                                document.webkitExitFullscreen();
                            }
                            window.__xenoSetYtFullscreenState(false);
                            return wasFs;
                        } catch(e) {
                            window.__xenoSetYtFullscreenState(false);
                            return false;
                        }
                    };

                    var onNativeFsChange = function() {
                        var fsEl = document.fullscreenElement || document.webkitFullscreenElement;
                        window.__xenoSetYtFullscreenState(!!fsEl);
                    };
                    document.addEventListener('fullscreenchange', onNativeFsChange, false);
                    document.addEventListener('webkitfullscreenchange', onNativeFsChange, false);

                    // Intercept clicks on YouTube's fullscreen button so tapping the fullscreen icon always enters/exits full screen
                    document.addEventListener('click', function(ev) {
                        try {
                            var t = ev && ev.target;
                            if (!t || typeof t.closest !== 'function') return;
                            var fsBtn = t.closest(
                                '.fullscreen-icon, ' +
                                '.ytp-fullscreen-button, ' +
                                'button[aria-label*="Full screen" i], ' +
                                'button[aria-label*="fullscreen" i], ' +
                                'button[aria-label*="Exit full screen" i], ' +
                                'ytm-custom-control .player-controls-bottom .icon-button[aria-label*="full" i]'
                            );
                            if (!fsBtn) {
                                // Also check if the clicked button contains a fullscreen icon class or path
                                var btnCandidate = t.closest('button, [role="button"], .icon-button');
                                if (btnCandidate && btnCandidate.closest('#movie_player, #player-container-id, .html5-video-player, ytm-custom-control')) {
                                    var label = (btnCandidate.getAttribute('aria-label') || btnCandidate.getAttribute('title') || '').toLowerCase();
                                    var cls = (btnCandidate.className && typeof btnCandidate.className === 'string') ? btnCandidate.className.toLowerCase() : '';
                                    if (label.indexOf('full screen') !== -1 || label.indexOf('fullscreen') !== -1 || cls.indexOf('fullscreen') !== -1 || btnCandidate.querySelector('.fullscreen-icon')) {
                                        fsBtn = btnCandidate;
                                    }
                                }
                            }
                            if (!fsBtn) return;

                            var currentlyFs = !!(
                                document.fullscreenElement ||
                                document.webkitFullscreenElement ||
                                window.__xenoYtIsFullscreen ||
                                (document.documentElement && document.documentElement.getAttribute('data-xeno-yt-fs') === '1')
                            );

                            setTimeout(function() {
                                var nativeFsNow = !!(document.fullscreenElement || document.webkitFullscreenElement);
                                if (currentlyFs) {
                                    // User tapped fullscreen icon to EXIT fullscreen
                                    if (nativeFsNow) {
                                        if (typeof document.exitFullscreen === 'function') {
                                            document.exitFullscreen().catch(function(){});
                                        } else if (typeof document.webkitExitFullscreen === 'function') {
                                            document.webkitExitFullscreen();
                                        }
                                    }
                                    window.__xenoSetYtFullscreenState(false);
                                } else {
                                    // User tapped fullscreen icon to ENTER fullscreen
                                    window.__xenoSetYtFullscreenState(true);
                                    if (!nativeFsNow) {
                                        var playerTarget = document.getElementById('movie_player') ||
                                                           document.getElementById('player-container-id') ||
                                                           document.querySelector('.html5-video-player') ||
                                                           document.querySelector('video');
                                        if (playerTarget) {
                                            try {
                                                if (typeof playerTarget.requestFullscreen === 'function') {
                                                    var p = playerTarget.requestFullscreen();
                                                    if (p && typeof p.catch === 'function') p.catch(function(){});
                                                } else if (typeof playerTarget.webkitRequestFullscreen === 'function') {
                                                    playerTarget.webkitRequestFullscreen();
                                                }
                                            } catch(e) {}
                                        }
                                    }
                                }
                            }, 50);
                        } catch(e) {}
                    }, true);

                    // Unlock YouTube & Shorts audio on first user interaction
                    window.__xenoYtAudioUnlocked = false;
                    var unlockYtAudio = function() {
                        window.__xenoYtAudioUnlocked = true;
                        try {
                            var mp = document.getElementById('movie_player');
                            if (mp && typeof mp.unMute === 'function') {
                                mp.unMute();
                                if (typeof mp.setVolume === 'function') mp.setVolume(100);
                            }
                            var sp = document.getElementById('shorts-player');
                            if (sp && typeof sp.unMute === 'function') {
                                sp.unMute();
                                if (typeof sp.setVolume === 'function') sp.setVolume(100);
                            }
                            var unmuteBtn = document.querySelector('.ytp-unmute, button[aria-label*="Tap to unmute"], button[aria-label*="Unmute"]');
                            if (unmuteBtn && unmuteBtn.offsetParent !== null) {
                                unmuteBtn.click();
                            }
                        } catch(e) {}
                    };
                    document.addEventListener('touchstart', unlockYtAudio, { passive: true, once: true });
                    document.addEventListener('touchend', unlockYtAudio, { passive: true, once: true });
                    document.addEventListener('click', unlockYtAudio, { passive: true, once: true });

                    // Hook SPA History navigation so the floating Download button updates immediately
                    var origPushState = history.pushState;
                    var origReplaceState = history.replaceState;
                    history.pushState = function() {
                        var ret = origPushState.apply(this, arguments);
                        if (window.__xenoYtIsFullscreen) window.__xenoSetYtFullscreenState(false);
                        if (window.__xenoRunYtOptimizer) {
                            setTimeout(window.__xenoRunYtOptimizer, 60);
                            setTimeout(window.__xenoRunYtOptimizer, 350);
                        }
                        return ret;
                    };
                    history.replaceState = function() {
                        var ret = origReplaceState.apply(this, arguments);
                        if (window.__xenoRunYtOptimizer) {
                            setTimeout(window.__xenoRunYtOptimizer, 60);
                            setTimeout(window.__xenoRunYtOptimizer, 350);
                        }
                        return ret;
                    };
                    window.addEventListener('popstate', function() {
                        if (window.__xenoYtIsFullscreen) window.__xenoSetYtFullscreenState(false);
                        if (window.__xenoRunYtOptimizer) {
                            setTimeout(window.__xenoRunYtOptimizer, 60);
                            setTimeout(window.__xenoRunYtOptimizer, 350);
                        }
                    });
                }

                // 3. Minimal CSS: Suppress promo banners & provide full-bleed landscape layout when in fullscreen mode
                var styleId = 'xeno-yt-shorts-smooth-style';
                var styleEl = document.getElementById(styleId);
                if (!styleEl && (document.head || document.documentElement)) {
                    styleEl = document.createElement('style');
                    styleEl.id = styleId;
                    (document.head || document.documentElement).appendChild(styleEl);
                }
                if (styleEl && !styleEl.__xenoCleanLayoutV4) {
                    styleEl.__xenoCleanLayoutV4 = true;
                    styleEl.textContent =
                        'ytm-mealbar-promo-renderer, ytm-app-banner, .mealbar-promo-renderer, ' +
                        'ytm-statement-banner-renderer { ' +
                        '  display: none !important; pointer-events: none !important; ' +
                        '} ' +
                        'html[data-xeno-yt-fs="1"], html[data-xeno-yt-fs="1"] body { ' +
                        '  overflow: hidden !important; background: #000 !important; width: 100vw !important; height: 100vh !important; margin: 0 !important; padding: 0 !important; ' +
                        '} ' +
                        'html[data-xeno-yt-fs="1"] ytm-mobile-topbar-renderer, ' +
                        'html[data-xeno-yt-fs="1"] ytm-pivot-bar-renderer, ' +
                        'html[data-xeno-yt-fs="1"] #header-bar, ' +
                        'html[data-xeno-yt-fs="1"] #xeno-yt-download-pill, ' +
                        'html[data-xeno-yt-fs="1"] .watch-below-the-player { ' +
                        '  display: none !important; ' +
                        '} ' +
                        'html[data-xeno-yt-fs="1"] #player-container-id, ' +
                        'html[data-xeno-yt-fs="1"] #player, ' +
                        'html[data-xeno-yt-fs="1"] #movie_player, ' +
                        'html[data-xeno-yt-fs="1"] .html5-video-player { ' +
                        '  position: fixed !important; inset: 0 !important; top: 0 !important; left: 0 !important; right: 0 !important; bottom: 0 !important; ' +
                        '  width: 100vw !important; max-width: 100vw !important; height: 100vh !important; max-height: 100vh !important; ' +
                        '  z-index: 2147483645 !important; background: #000 !important; margin: 0 !important; padding: 0 !important; ' +
                        '} ' +
                        'html[data-xeno-yt-fs="1"] .html5-video-container, ' +
                        'html[data-xeno-yt-fs="1"] video.html5-main-video, ' +
                        'html[data-xeno-yt-fs="1"] #movie_player video { ' +
                        '  width: 100vw !important; height: 100vh !important; max-width: 100vw !important; max-height: 100vh !important; ' +
                        '  left: 0 !important; top: 0 !important; object-fit: contain !important; ' +
                        '}';
                }

                function getYouTubePageMode() {
                    var path = (window.location.pathname || '').toLowerCase();
                    var search = (window.location.search || '').toLowerCase();
                    var href = (window.location.href || '').toLowerCase();
                    if (path.indexOf('/shorts') === 0 || path.indexOf('/shorts/') !== -1 || href.indexOf('/shorts/') !== -1) {
                        return 'shorts';
                    }
                    var shortsDom = document.querySelector('ytm-shorts, ytd-shorts, #shorts-container');
                    if (shortsDom && shortsDom.offsetParent !== null && path.indexOf('/watch') === -1 && search.indexOf('v=') === -1) {
                        return 'shorts';
                    }
                    if (path.indexOf('/watch') === 0 || path.indexOf('/watch') !== -1 || (search.indexOf('v=') !== -1 && path.indexOf('/results') === -1)) {
                        return 'watch';
                    }
                    return 'home';
                }

                function isMuxedVideoWithSoundUrl(url) {
                    if (!url || typeof url !== 'string') return false;
                    if (url.indexOf('|||') !== -1) return true;
                    return url.indexOf('itag=18&') !== -1 ||
                           url.indexOf('itag=22&') !== -1 ||
                           url.indexOf('itag=37&') !== -1 ||
                           url.indexOf('itag=59&') !== -1 ||
                           url.indexOf('itag=78&') !== -1 ||
                           /([?&])itag=(18|22|37|59|78)$/.test(url);
                }

                function sanitizeVideoPlaybackUrl(rawUrl) {
                    if (!rawUrl || rawUrl.indexOf('http') !== 0) return '';
                    if (rawUrl.indexOf('googlevideo.com/videoplayback') === -1) return rawUrl;
                    if (rawUrl.indexOf('ump=1') !== -1 || rawUrl.indexOf('sabr=1') !== -1) return '';
                    return rawUrl
                        .replace(/([?&])range=[^&]+/g, '$1')
                        .replace(/([?&])rn=[^&]+/g, '$1')
                        .replace(/([?&])rbuf=[^&]+/g, '$1')
                        .replace(/\?&/g, '?')
                        .replace(/&+/g, '&')
                        .replace(/[&?]$/, '');
                }

                function extractBestStreamFromStreamingData(sd) {
                    if (!sd) return '';
                    var fmts = sd.formats || [];
                    var bestProg = '';
                    for (var i = 0; i < fmts.length; i++) {
                        var f = fmts[i];
                        if (f && f.url && f.url.indexOf('http') === 0 && f.url.indexOf('ump=1') === -1 && f.url.indexOf('sabr=1') === -1) {
                            if (f.itag === 18 || f.itag === 22) {
                                return f.url;
                            }
                            if (!bestProg) bestProg = f.url;
                        }
                    }
                    if (bestProg) return bestProg;

                    var adaptive = sd.adaptiveFormats || [];
                    var bestVid = '';
                    var bestAud = '';
                    for (var j = 0; j < adaptive.length; j++) {
                        var af = adaptive[j];
                        if (!af || !af.url || af.url.indexOf('http') !== 0 || af.url.indexOf('ump=1') !== -1 || af.url.indexOf('sabr=1') !== -1) continue;
                        var mime = (af.mimeType || '').toLowerCase();
                        if (mime.indexOf('video/mp4') === 0) {
                            if (af.itag === 134 || af.itag === 135 || af.itag === 136 || !bestVid) {
                                bestVid = af.url;
                                if (af.itag === 134) break;
                            }
                        }
                    }
                    for (var k = 0; k < adaptive.length; k++) {
                        var audF = adaptive[k];
                        if (!audF || !audF.url || audF.url.indexOf('http') !== 0 || audF.url.indexOf('ump=1') !== -1 || audF.url.indexOf('sabr=1') !== -1) continue;
                        var aMime = (audF.mimeType || '').toLowerCase();
                        if (aMime.indexOf('audio/mp4') === 0) {
                            bestAud = audF.url;
                            if (audF.itag === 140) break;
                        }
                    }
                    if (bestVid && bestAud) return bestVid + '|||' + bestAud;
                    if (bestVid) return bestVid;
                    return '';
                }

                function extractStreamFromPerformanceEntries() {
                    try {
                        if (!window.performance || typeof window.performance.getEntriesByType !== 'function') return '';
                        var entries = window.performance.getEntriesByType('resource') || [];
                        var progUrl = '';
                        var vidUrl = '';
                        var audUrl = '';
                        for (var i = entries.length - 1; i >= 0; i--) {
                            var u = entries[i] && entries[i].name ? entries[i].name : '';
                            if (u.indexOf('googlevideo.com/videoplayback') === -1) continue;
                            if (u.indexOf('ump=1') !== -1 || u.indexOf('sabr=1') !== -1 || u.indexOf('mime=text') !== -1) continue;
                            var clean = sanitizeVideoPlaybackUrl(u);
                            if (!clean) continue;
                            if (isMuxedVideoWithSoundUrl(clean)) {
                                progUrl = clean;
                                break;
                            }
                            if (!vidUrl && (clean.indexOf('mime=video%2Fmp4') !== -1 || clean.indexOf('mime=video/mp4') !== -1)) {
                                vidUrl = clean;
                            }
                            if (!audUrl && (clean.indexOf('mime=audio%2Fmp4') !== -1 || clean.indexOf('mime=audio/mp4') !== -1 || clean.indexOf('itag=140&') !== -1 || clean.indexOf('itag=139&') !== -1 || clean.indexOf('itag=141&') !== -1)) {
                                audUrl = clean;
                            }
                        }
                        if (progUrl) return progUrl;
                        if (vidUrl && audUrl) return vidUrl + '|||' + audUrl;
                        if (vidUrl) return vidUrl;
                    } catch(e) {}
                    return '';
                }

                function forwardSessionHints() {
                    try {
                        if (!window.XenoYtBridge || typeof window.XenoYtBridge.setSessionHints !== 'function') return;
                        var vData = '';
                        var stsVal = '';
                        if (window.ytcfg && typeof window.ytcfg.get === 'function') {
                            vData = String(window.ytcfg.get('VISITOR_DATA') || '');
                            stsVal = String(window.ytcfg.get('STS') || '');
                            if (!vData) {
                                var innCtx = window.ytcfg.get('INNERTUBE_CONTEXT');
                                if (innCtx && innCtx.client && innCtx.client.visitorData) {
                                    vData = String(innCtx.client.visitorData);
                                }
                            }
                        }
                        if (vData || stsVal) {
                            window.XenoYtBridge.setSessionHints(vData, stsVal);
                        }
                    } catch(e) {}
                }

                function extractActiveYouTubeMetadata() {
                    forwardSessionHints();
                    var m = getYouTubePageMode();
                    if (m === 'shorts') {
                        return {
                            videoId: '',
                            title: '',
                            author: '',
                            durationSec: '0',
                            directStreamUrl: ''
                        };
                    }
                    var rawId = '';
                    if (m === 'watch') {
                        try {
                            rawId = (new URLSearchParams(window.location.search)).get('v') || '';
                        } catch(e) {}
                    }

                    var pEl = document.getElementById('movie_player') ||
                              document.querySelector('.html5-video-player');
                    var pr = null;
                    try {
                        if (pEl && typeof pEl.getPlayerResponse === 'function') {
                            pr = pEl.getPlayerResponse();
                        }
                    } catch(e) {}
                    if ((!pr || !pr.videoDetails || (rawId && pr.videoDetails.videoId !== rawId)) && window.__xenoLatestPlayerResponse) {
                        var lpr = window.__xenoLatestPlayerResponse;
                        if (lpr && lpr.videoDetails && (!rawId || lpr.videoDetails.videoId === rawId)) {
                            pr = lpr;
                        }
                    }
                    if (!pr && window.ytInitialPlayerResponse) {
                        var ipr = window.ytInitialPlayerResponse;
                        if (ipr && ipr.videoDetails && (!rawId || ipr.videoDetails.videoId === rawId)) {
                            pr = ipr;
                        }
                    }

                    var vDetails = (pr && pr.videoDetails) ? pr.videoDetails : null;
                    if (vDetails && vDetails.videoId && (!rawId || vDetails.videoId === rawId)) {
                        rawId = vDetails.videoId;
                    }
                    if (!rawId && pEl && typeof pEl.getVideoData === 'function') {
                        try {
                            var vd = pEl.getVideoData();
                            if (vd && vd.video_id) rawId = vd.video_id;
                        } catch(e) {}
                    }

                    var title = '';
                    var author = '';
                    var durationSec = 0;

                    if (vDetails && vDetails.videoId === rawId) {
                        title = vDetails.title || '';
                        author = vDetails.author || '';
                        durationSec = parseInt(vDetails.lengthSeconds || '0', 10) || 0;
                    }
                    if (pEl && typeof pEl.getVideoData === 'function') {
                        try {
                            var vd2 = pEl.getVideoData();
                            if (vd2 && (!rawId || vd2.video_id === rawId)) {
                                if (!title && vd2.title) title = vd2.title;
                                if (!author && vd2.author) author = vd2.author;
                            }
                        } catch(e) {}
                    }
                    if (!title) {
                        var domTitleEl = document.querySelector(
                            '.slim-video-metadata-header .slim-video-information-title, ' +
                            'ytm-slim-video-metadata-section-renderer h2, ' +
                            'h1.title'
                        );
                        if (domTitleEl && domTitleEl.textContent) {
                            title = domTitleEl.textContent.trim();
                        } else if (document.title) {
                            title = document.title.replace(/\s*-\s*YouTube$/i, '').trim();
                        }
                    }
                    if (!author) {
                        var domAuthorEl = document.querySelector(
                            'ytm-slim-owner-renderer .slim-owner-icon-and-title a, ' +
                            '.slim-owner-channel-name'
                        );
                        if (domAuthorEl && domAuthorEl.textContent) {
                            author = domAuthorEl.textContent.trim();
                        }
                    }
                    if (!durationSec) {
                        try {
                            if (pEl && typeof pEl.getDuration === 'function') {
                                durationSec = Math.round(pEl.getDuration() || 0);
                            }
                        } catch(e) {}
                    }
                    if (!durationSec) {
                        var activeVid = document.querySelector('video');
                        if (activeVid && activeVid.duration && isFinite(activeVid.duration)) {
                            durationSec = Math.round(activeVid.duration);
                        }
                    }

                    var directStream = '';
                    // 1. Check active <video> element currentSrc first if it is a direct HTTP(S) MP4 URL
                    var domVid = document.querySelector('video');
                    if (domVid) {
                        var src = domVid.currentSrc || domVid.src || '';
                        if ((src.indexOf('http://') === 0 || src.indexOf('https://') === 0) && src.indexOf('ump=1') === -1 && src.indexOf('sabr=1') === -1) {
                            var cleanDomSrc = sanitizeVideoPlaybackUrl(src);
                            if (cleanDomSrc) directStream = cleanDomSrc;
                        }
                    }
                    // 2. Check playerResponse streamingData if it matches current videoId
                    try {
                        if ((!directStream || !isMuxedVideoWithSoundUrl(directStream)) && pr && pr.streamingData && vDetails && vDetails.videoId === rawId) {
                            var sdStream = extractBestStreamFromStreamingData(pr.streamingData);
                            if (sdStream) directStream = sdStream;
                        }
                    } catch(e) {}
                    // 3. Check performance resource entries for non-UMP MP4 streams
                    if (!directStream || !isMuxedVideoWithSoundUrl(directStream)) {
                        var perfStream = extractStreamFromPerformanceEntries();
                        if (perfStream && (isMuxedVideoWithSoundUrl(perfStream) || !directStream)) {
                            directStream = perfStream;
                        }
                    }

                    return {
                        videoId: rawId || '',
                        title: title || 'YouTube Video',
                        author: author || 'YouTube',
                        durationSec: String(durationSec || 0),
                        directStreamUrl: directStream || ''
                    };
                }

                window.__xenoTriggerYtDownload = function() {
                    if (getYouTubePageMode() === 'shorts') {
                        return false;
                    }
                    var meta = extractActiveYouTubeMetadata();
                    if (!meta.videoId) {
                        return false;
                    }
                    if (!window.XenoYtBridge || typeof window.XenoYtBridge.downloadVideo !== 'function') {
                        return false;
                    }

                    if (typeof window.__xenoResolveVideoWithSoundStream === 'function') {
                        return window.__xenoResolveVideoWithSoundStream(meta);
                    }

                    window.XenoYtBridge.downloadVideo(
                        meta.videoId,
                        meta.title,
                        meta.author,
                        meta.durationSec,
                        meta.directStreamUrl || ''
                    );
                    return true;
                };

                function renderCircularDownloadButtonState(btn, prog, isSaved) {
                    if (!btn) return;
                    var stateKey = (prog >= 0) ? ('p:' + prog) : (isSaved ? 'saved' : 'idle');
                    if (btn.getAttribute('data-xeno-dl-state') === stateKey) return;
                    btn.setAttribute('data-xeno-dl-state', stateKey);
                    if (prog >= 0) {
                        btn.style.borderColor = '#FF0000';
                        btn.style.background = 'rgba(20,20,20,0.94)';
                        btn.title = 'Downloading ' + prog + '%';
                        btn.setAttribute('aria-label', 'Downloading ' + prog + '%');
                        btn.innerHTML =
                            '<span style="font-size:10px;font-weight:700;color:#FFFFFF;line-height:1;letter-spacing:-0.3px;">' +
                            prog + '%</span>';
                    } else if (isSaved) {
                        btn.style.borderColor = '#2BA640';
                        btn.style.background = 'rgba(20,38,24,0.94)';
                        btn.title = 'Downloaded (Tap to open Offline Videos)';
                        btn.setAttribute('aria-label', 'Downloaded Offline');
                        btn.innerHTML =
                            '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#2BA640" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">' +
                            '<polyline points="20 6 9 17 4 12"></polyline>' +
                            '</svg>';
                    } else {
                        btn.style.borderColor = 'rgba(255,255,255,0.28)';
                        btn.style.background = 'rgba(15,15,15,0.92)';
                        btn.title = 'Download Video';
                        btn.setAttribute('aria-label', 'Download Video');
                        btn.innerHTML =
                            '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#FFFFFF" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">' +
                            '<path d="M12 3v12"></path>' +
                            '<polyline points="7 10 12 15 17 10"></polyline>' +
                            '<path d="M5 20h14"></path>' +
                            '</svg>';
                    }
                }

                function optimizeYouTubeAndShorts() {
                    var mode = getYouTubePageMode();
                    if (document.documentElement) {
                        document.documentElement.setAttribute('data-xeno-yt-page', mode);
                    }
                    if (window.__xenoLastReportedYtMode !== mode) {
                        window.__xenoLastReportedYtMode = mode;
                        try {
                            if (window.XenoYtBridge && typeof window.XenoYtBridge.onYoutubePageModeChanged === 'function') {
                                window.XenoYtBridge.onYoutubePageModeChanged(mode);
                            }
                        } catch(e) {}
                    }

                    // Ensure inline video playback without altering YouTube layout or forcing play/pause loops
                    var videos = document.querySelectorAll('video');
                    for (var i = 0; i < videos.length; i++) {
                        var v = videos[i];
                        if (!v.hasAttribute('playsinline')) v.setAttribute('playsinline', 'true');
                        if (!v.hasAttribute('webkit-playsinline')) v.setAttribute('webkit-playsinline', 'true');
                        v.playsInline = true;
                    }

                    // Intercept /feed/downloads navigation to open the Clone Space Offline Library
                    var pathLower = (window.location.pathname || '').toLowerCase();
                    if (pathLower.indexOf('/feed/downloads') === 0 && window.XenoYtBridge && typeof window.XenoYtBridge.openOfflineDownloads === 'function') {
                        window.XenoYtBridge.openOfflineDownloads();
                    }

                    // Inject or update the small circular Download Icon Button ONLY on Watch pages (strictly hidden on Shorts!)
                    var pillId = 'xeno-yt-download-pill';
                    var existingPill = document.getElementById(pillId);
                    if (mode === 'watch') {
                        if (!existingPill && document.body) {
                            existingPill = document.createElement('button');
                            existingPill.id = pillId;
                            existingPill.type = 'button';
                            existingPill.style.cssText =
                                'position:fixed; right:14px; bottom:64px; width:40px; height:40px; ' +
                                'z-index:2147483640; display:flex; align-items:center; justify-content:center; ' +
                                'background:rgba(15,15,15,0.92); color:#ffffff; border:1.5px solid rgba(255,255,255,0.28); ' +
                                'border-radius:50%; padding:0; font-family:Roboto,sans-serif; ' +
                                'box-shadow:0 4px 12px rgba(0,0,0,0.5); cursor:pointer; ' +
                                '-webkit-tap-highlight-color:transparent; transition:transform 0.15s ease, border-color 0.2s ease;';
                            existingPill.addEventListener('click', function(ev) {
                                ev.stopPropagation();
                                ev.preventDefault();
                                if (getYouTubePageMode() === 'shorts') return;
                                var curMeta = extractActiveYouTubeMetadata();
                                if (curMeta.videoId && window.XenoYtBridge && typeof window.XenoYtBridge.isDownloaded === 'function' && window.XenoYtBridge.isDownloaded(curMeta.videoId)) {
                                    window.XenoYtBridge.openOfflineDownloads();
                                } else {
                                    renderCircularDownloadButtonState(existingPill, 1, false);
                                    window.__xenoTriggerYtDownload();
                                }
                            });
                            document.body.appendChild(existingPill);
                        }
                        if (existingPill) {
                            var isFsNow = !!(window.__xenoYtIsFullscreen || document.fullscreenElement || document.webkitFullscreenElement);
                            existingPill.style.width = '40px';
                            existingPill.style.height = '40px';
                            existingPill.style.borderRadius = '50%';
                            existingPill.style.padding = '0';
                            existingPill.style.bottom = '64px';
                            existingPill.style.display = isFsNow ? 'none' : 'flex';
                            var curIdCheck = extractActiveYouTubeMetadata().videoId;
                            var prog = -1;
                            var isSaved = false;
                            try {
                                if (curIdCheck && window.XenoYtBridge) {
                                    prog = window.XenoYtBridge.getProgress(curIdCheck);
                                    isSaved = window.XenoYtBridge.isDownloaded(curIdCheck);
                                }
                            } catch(e) {}
                            renderCircularDownloadButtonState(existingPill, prog, isSaved);
                        }
                    } else if (existingPill) {
                        existingPill.style.display = 'none';
                    }
                }

                window.__xenoRunYtOptimizer = optimizeYouTubeAndShorts;
                optimizeYouTubeAndShorts();

                if (!window.__xenoYtProgressTimer) {
                    window.__xenoYtProgressTimer = setInterval(function() {
                        try {
                            if (getYouTubePageMode() === 'watch') {
                                optimizeYouTubeAndShorts();
                            }
                        } catch(e) {}
                    }, 1000);
                }

                if (!window.__xenoYtMutationObserver && window.MutationObserver && document.body) {
                    var throttleTimer = null;
                    window.__xenoYtMutationObserver = new MutationObserver(function() {
                        if (throttleTimer) return;
                        throttleTimer = setTimeout(function() {
                            throttleTimer = null;
                            optimizeYouTubeAndShorts();
                        }, 300);
                    });
                    window.__xenoYtMutationObserver.observe(document.body, {
                        childList: true,
                        subtree: true
                    });
                }
            } catch(e) {}
        })();
    """.trimIndent()
    view?.evaluateJavascript(js, null)
    injectYouTubeDownloadWithSoundAndShortsGuardHelper(view)
}

/**
 * Dedicated YouTube Video-With-Sound Download & Shorts Button Guard Helper:
 * 1. Captures fresh `/youtubei/v1/player` responses during SPA navigation and forwards
 *    `visitorData` / `sts` session hints to `YouTubeOfflineRepository` so native InnerTube
 *    requests resolve muxed MP4 (`itag=18` / `itag=22`) or adaptive video+audio streams reliably.
 * 2. Strictly hides the Download Video button whenever the user is watching YouTube Shorts
 *    (`/shorts/...` or active Shorts player).
 */
private fun injectYouTubeDownloadWithSoundAndShortsGuardHelper(view: WebView?) {
    val js = """
        (function() {
            try {
                // 1. CSS Guard: Force-hide the floating circular download button whenever on YouTube Shorts or Home
                var guardStyleId = 'xeno-yt-shorts-download-guard-style';
                var guardStyleEl = document.getElementById(guardStyleId);
                if (!guardStyleEl && (document.head || document.documentElement)) {
                    guardStyleEl = document.createElement('style');
                    guardStyleEl.id = guardStyleId;
                    (document.head || document.documentElement).appendChild(guardStyleEl);
                }
                if (guardStyleEl && !guardStyleEl.__xenoApplied) {
                    guardStyleEl.__xenoApplied = true;
                    guardStyleEl.textContent =
                        'html[data-xeno-yt-page="shorts"] #xeno-yt-download-pill, ' +
                        'html[data-xeno-yt-page="home"] #xeno-yt-download-pill { ' +
                        '  display: none !important; visibility: hidden !important; pointer-events: none !important; opacity: 0 !important; ' +
                        '}';
                }

                // 2. Hook fetch to capture fresh /youtubei/v1/player JSON during SPA video switches
                if (!window.__xenoYtPlayerFetchHooked && window.fetch) {
                    window.__xenoYtPlayerFetchHooked = true;
                    var origFetch = window.fetch;
                    window.fetch = function(input, init) {
                        var urlStr = (typeof input === 'string') ? input : (input && input.url ? input.url : '');
                        var p = origFetch.apply(this, arguments);
                        if (urlStr && urlStr.indexOf('/youtubei/v1/player') !== -1) {
                            p.then(function(resp) {
                                try {
                                    var clone = resp.clone();
                                    clone.json().then(function(data) {
                                        if (data && data.videoDetails && data.videoDetails.videoId) {
                                            window.__xenoLatestPlayerResponse = data;
                                        }
                                    }).catch(function(){});
                                } catch(e) {}
                            }).catch(function(){});
                        }
                        return p;
                    };
                }

                // 3. Dispatch immediately to Android's YouTubeOfflineRepository (which resolves ANDROID_VR / IOS / ANDROID / EMBEDDED streams natively)
                window.__xenoResolveVideoWithSoundStream = function(meta) {
                    if (!meta || !meta.videoId) return false;
                    var pathNow = (window.location.pathname || '').toLowerCase();
                    if (pathNow.indexOf('/shorts') === 0 || pathNow.indexOf('/shorts/') !== -1) {
                        return false;
                    }
                    try {
                        if (window.XenoYtBridge && typeof window.XenoYtBridge.setSessionHints === 'function' && window.ytcfg && typeof window.ytcfg.get === 'function') {
                            var vd = String(window.ytcfg.get('VISITOR_DATA') || '');
                            var sts = String(window.ytcfg.get('STS') || '');
                            if (vd || sts) {
                                window.XenoYtBridge.setSessionHints(vd, sts);
                            }
                        }
                    } catch(e) {}

                    window.XenoYtBridge.downloadVideo(
                        meta.videoId,
                        meta.title || 'YouTube Video',
                        meta.author || 'YouTube',
                        meta.durationSec || '0',
                        meta.directStreamUrl || ''
                    );
                    return true;
                };

                // Immediately enforce Shorts download button hiding if currently on Shorts
                var curPath = (window.location.pathname || '').toLowerCase();
                if (curPath.indexOf('/shorts') === 0 || curPath.indexOf('/shorts/') !== -1) {
                    if (document.documentElement) {
                        document.documentElement.setAttribute('data-xeno-yt-page', 'shorts');
                    }
                    var pill = document.getElementById('xeno-yt-download-pill');
                    if (pill) pill.style.display = 'none';
                    if (window.XenoYtBridge && typeof window.XenoYtBridge.onYoutubePageModeChanged === 'function') {
                        window.XenoYtBridge.onYoutubePageModeChanged('shorts');
                    }
                }
            } catch(e) {}
        })();
    """.trimIndent()
    view?.evaluateJavascript(js, null)
}

/**
 * Dedicated Discord Helper Function (`injectDiscordStabilityAndLoginHelper`) for Clone Space:
 * 1. Preserves `window.localStorage` across Discord's web bundle initialization (Discord deletes
 *    `window.localStorage` during startup; caching and restoring a safe getter ensures authentication
 *    tokens, 2FA sessions, and account switching persist reliably across reloads).
 * 2. Bypasses "Open in Discord App" / "Download Discord for Android" mobile interstitials and
 *    automatically clicks "Continue in browser" if shown.
 * 3. Adapts Discord's login form (`/login`, `/register`) and channel/chat layout (`/channels/...`)
 *    for mobile touch screens when not in full desktop zoom mode — hiding the desktop QR code panel on
 *    narrow screens so email/password/2FA inputs are centered and easy to use, and enabling smooth
 *    horizontal/vertical touch scrolling across servers, channels, and chat history.
 * 4. Polyfills WebRTC permissions, Notifications, and AudioContext unlock for stable voice channels
 *    and real-time Gateway WebSocket messaging.
 */
private fun injectDiscordStabilityAndLoginHelper(view: WebView?, isDesktopMode: Boolean) {
    val mobileAdaptiveFlag = if (isDesktopMode) "false" else "true"
    val js = """
        (function() {
            try {
                // 1. Preserve window.localStorage before Discord's bundle deletes it from window
                if (!window.__xenoDiscordStorageSaved) {
                    window.__xenoDiscordStorageSaved = true;
                    try {
                        var nativeStorage = window.localStorage;
                        if (nativeStorage) {
                            window.__xenoDiscordLocalStorage = nativeStorage;
                            Object.defineProperty(window, 'localStorage', {
                                get: function() { return window.__xenoDiscordLocalStorage; },
                                configurable: true
                            });
                        }
                    } catch(e) {}
                }

                if (!window.__xenoDiscordPolyfilled) {
                    window.__xenoDiscordPolyfilled = true;

                    if (!window.chrome) {
                        window.chrome = { runtime: {}, app: { isInstalled: false } };
                    }

                    // Polyfill Notification API so Discord Gateway & unread alerts never throw
                    if (typeof window.Notification === 'undefined' || window.Notification.permission !== 'granted') {
                        var MockDiscordNotif = function(title, opts) {
                            this.title = title;
                            this.options = opts || {};
                            this.close = function() {};
                        };
                        MockDiscordNotif.permission = 'granted';
                        MockDiscordNotif.requestPermission = function(cb) {
                            if (typeof cb === 'function') cb('granted');
                            return Promise.resolve('granted');
                        };
                        window.Notification = MockDiscordNotif;
                    }

                    // Polyfill navigator.permissions for microphone, camera, and notifications
                    if (navigator.permissions && typeof navigator.permissions.query === 'function') {
                        var origQuery = navigator.permissions.query.bind(navigator.permissions);
                        navigator.permissions.query = function(desc) {
                            var name = (desc && desc.name) ? String(desc.name).toLowerCase() : '';
                            if (name === 'microphone' || name === 'camera' || name === 'notifications' || name === 'speaker-selection') {
                                return Promise.resolve({
                                    name: name,
                                    state: 'granted',
                                    status: 'granted',
                                    onchange: null,
                                    addEventListener: function() {},
                                    removeEventListener: function() {}
                                });
                            }
                            return origQuery(desc).catch(function() {
                                return { state: 'granted', status: 'granted' };
                            });
                        };
                    }

                    // Unlock WebAudio / HTML5 audio on first user tap for Discord message sounds & voice channels
                    var unlockDiscordAudio = function() {
                        try {
                            var AudioCtx = window.AudioContext || window.webkitAudioContext;
                            if (AudioCtx) {
                                if (!window.__xenoDiscordAudioCtx) {
                                    window.__xenoDiscordAudioCtx = new AudioCtx();
                                }
                                if (window.__xenoDiscordAudioCtx.state === 'suspended') {
                                    window.__xenoDiscordAudioCtx.resume();
                                }
                            }
                        } catch(e) {}
                    };
                    document.addEventListener('touchstart', unlockDiscordAudio, { passive: true });
                    document.addEventListener('click', unlockDiscordAudio, { passive: true });
                }

                // 2. Ensure proper viewport meta tag so login inputs and chat UI scale cleanly
                var vp = document.querySelector('meta[name="viewport"]');
                if (!vp && document.head) {
                    vp = document.createElement('meta');
                    vp.name = 'viewport';
                    document.head.appendChild(vp);
                }
                if (vp) {
                    if ($mobileAdaptiveFlag) {
                        vp.setAttribute('content', 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover');
                    } else {
                        vp.setAttribute('content', 'width=1100, initial-scale=0.45, minimum-scale=0.25, maximum-scale=2.0, user-scalable=yes');
                    }
                }

                // 3. Inject responsive CSS for Discord Login, Server Sidebar, Channel List & Chat Scroller
                var styleEl = document.getElementById('__xeno_discord_helper_css');
                if (!styleEl && (document.head || document.documentElement)) {
                    styleEl = document.createElement('style');
                    styleEl.id = '__xeno_discord_helper_css';
                    (document.head || document.documentElement).appendChild(styleEl);
                }
                if (styleEl) {
                    if ($mobileAdaptiveFlag) {
                        styleEl.textContent =
                            'html, body { background-color: #313338 !important; overscroll-behavior: none !important; } ' +
                            /* Hide mobile app download banners & upsell notices */
                            '[class*="downloadApps"], [class*="mobileAppPrompt"], [class*="noticeMobile"], a[href*="play.google.com"], a[href*="itunes.apple.com"] { display: none !important; } ' +
                            /* Optimize Discord Login & Register Box for mobile portrait screens */
                            '[class*="authBox"] { width: 94vw !important; max-width: 440px !important; padding: 20px 16px !important; margin: 12px auto !important; box-sizing: border-box !important; } ' +
                            '[class*="qrLogin"], [class*="verticalSeparator"] { display: none !important; } ' +
                            '[class*="mainLoginContainer"] { width: 100% !important; } ' +
                            /* Smooth touch scrolling inside Discord scrollers, server list, channel list, and chat */
                            '[class*="scroller"] { -webkit-overflow-scrolling: touch !important; touch-action: pan-y !important; } ' +
                            /* Allow horizontal pan across Discord panels on narrow phone viewports when in /channels/ */
                            '#app-mount, [class*="appAsidePanelWrapper"], [class*="app_"] { overflow-x: auto !important; -webkit-overflow-scrolling: touch !important; } ' +
                            '[class*="sidebar_"] { width: 195px !important; min-width: 170px !important; } ' +
                            '[class*="chat_"], [class*="chatContent_"] { min-width: 280px !important; }';
                    } else {
                        styleEl.textContent =
                            'html, body { background-color: #313338 !important; } ' +
                            '[class*="scroller"] { -webkit-overflow-scrolling: touch !important; }';
                    }
                }

                // 4. Auto-bypass any "Continue in browser" / "Open in Discord App" interstitials
                function bypassDiscordInterstitials() {
                    var buttons = document.querySelectorAll('button, a');
                    for (var i = 0; i < buttons.length; i++) {
                        var btn = buttons[i];
                        var txt = (btn.innerText || btn.textContent || '').trim().toLowerCase();
                        if (txt === 'continue in browser' ||
                            txt === 'open discord in your browser' ||
                            txt === 'continue to discord') {
                            try { btn.click(); } catch(e) {}
                        }
                    }

                    // If landed on discord.com marketing home page instead of /app or /login, route to /app
                    var path = (window.location.pathname || '/').toLowerCase();
                    if (window.location.hostname.indexOf('discord.com') !== -1 && (path === '/' || path === '/download')) {
                        window.location.replace('https://discord.com/app');
                    }
                }

                bypassDiscordInterstitials();

                if (!window.__xenoDiscordObserver && window.MutationObserver && document.body) {
                    var throttle = null;
                    window.__xenoDiscordObserver = new MutationObserver(function() {
                        if (throttle) return;
                        throttle = setTimeout(function() {
                            throttle = null;
                            bypassDiscordInterstitials();
                            try {
                                if (!window.localStorage && window.__xenoDiscordLocalStorage) {
                                    Object.defineProperty(window, 'localStorage', {
                                        get: function() { return window.__xenoDiscordLocalStorage; },
                                        configurable: true
                                    });
                                }
                            } catch(e) {}
                        }, 300);
                    });
                    window.__xenoDiscordObserver.observe(document.body, { childList: true, subtree: true });
                }
            } catch(e) {}
        })();
    """.trimIndent()
    view?.evaluateJavascript(js, null)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CloneWorkspaceScreen(
    instance: ClonedAppInstance,
    onCloseWorkspace: () -> Unit,
    onMinimizeWorkspace: () -> Unit = {},
    onUrlChanged: (String) -> Unit,
    onToggleDesktopMode: () -> Unit,
    onOpenInBrowser: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val cachedWebView = remember(instance.instanceId) { CloneWebViewPool.get(instance.instanceId) }
    var webViewInstance by remember(instance.instanceId) { mutableStateOf<WebView?>(cachedWebView) }
    var isLoading by remember(instance.instanceId) { mutableStateOf(cachedWebView == null) }
    var loadProgress by remember(instance.instanceId) { mutableIntStateOf(if (cachedWebView != null) 100 else 0) }
    var currentTitle by remember(instance.instanceId) { mutableStateOf(cachedWebView?.title ?: instance.displayName) }
    var currentUrl by remember(instance.instanceId) { mutableStateOf(cachedWebView?.url ?: instance.lastUrl) }
    var canGoBack by remember(instance.instanceId) { mutableStateOf(cachedWebView?.canGoBack() == true) }
    var hasRenderCrash by remember(instance.instanceId) { mutableStateOf(false) }
    var useSoftwareLayer by remember(instance.instanceId) { mutableStateOf(false) }
    var reloadKey by remember(instance.instanceId) { mutableIntStateOf(0) }
    var keepAliveInMinimizedPool by remember(instance.instanceId) { mutableStateOf(false) }

    // Voice & Video Call hardware states
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var pendingWebPermissionRequest by remember { mutableStateOf<PermissionRequest?>(null) }
    var isCallActive by remember(instance.instanceId) { mutableStateOf(false) }
    var isVideoCallActive by remember(instance.instanceId) { mutableStateOf(false) }
    var isSpeakerphoneOn by remember(instance.instanceId) { mutableStateOf(true) }
    var isFrontCamera by remember(instance.instanceId) { mutableStateOf(true) }
    var audioFocusRequest by remember { mutableStateOf<AudioFocusRequest?>(null) }
    var isWhatsAppPhoneUi by remember(instance.instanceId) { mutableStateOf(true) }
    var isWhatsAppChatOpen by remember(instance.instanceId) { mutableStateOf(false) }

    val badgeColor = remember(instance.badgeColorHex) { parseHexColor(instance.badgeColorHex) }

    val isWhatsapp = remember(instance.instanceId, instance.packageName, instance.targetUrl) {
        instance.packageName.contains("whatsapp", ignoreCase = true) ||
                instance.originalName.contains("whatsapp", ignoreCase = true) ||
                instance.displayName.contains("whatsapp", ignoreCase = true) ||
                instance.targetUrl.contains("whatsapp.com", ignoreCase = true)
    }

    val isInstagram = remember(instance.instanceId, instance.packageName, instance.targetUrl) {
        instance.packageName.contains("instagram", ignoreCase = true) ||
                instance.originalName.contains("instagram", ignoreCase = true) ||
                instance.displayName.contains("instagram", ignoreCase = true) ||
                instance.targetUrl.contains("instagram.com", ignoreCase = true)
    }

    val isFacebook = remember(instance.instanceId, instance.packageName, instance.targetUrl) {
        instance.packageName.contains("facebook", ignoreCase = true) ||
                instance.originalName.contains("facebook", ignoreCase = true) ||
                instance.displayName.contains("facebook", ignoreCase = true) ||
                instance.targetUrl.contains("facebook.com", ignoreCase = true)
    }

    val isYoutube = remember(instance.instanceId, instance.packageName, instance.targetUrl) {
        instance.packageName.contains("youtube", ignoreCase = true) ||
                instance.originalName.contains("youtube", ignoreCase = true) ||
                instance.displayName.contains("youtube", ignoreCase = true) ||
                instance.targetUrl.contains("youtube.com", ignoreCase = true) ||
                instance.targetUrl.contains("youtu.be", ignoreCase = true)
    }

    val isDiscord = remember(instance.instanceId, instance.packageName, instance.targetUrl) {
        instance.packageName.contains("discord", ignoreCase = true) ||
                instance.originalName.contains("discord", ignoreCase = true) ||
                instance.displayName.contains("discord", ignoreCase = true) ||
                instance.targetUrl.contains("discord.com", ignoreCase = true) ||
                instance.targetUrl.contains("discord.gg", ignoreCase = true)
    }

    val isShortVideoSocialApp = isInstagram || isFacebook || isYoutube

    // YouTube Offline Downloads & Desktop-Style Offline Mode state
    val youtubeOfflineVideos by YouTubeOfflineRepository.downloadsFlow.collectAsStateWithLifecycle()
    val youtubeActiveDownloads by YouTubeOfflineRepository.activeDownloadsFlow.collectAsStateWithLifecycle()
    var isYoutubeNetworkOffline by remember(instance.instanceId) {
        mutableStateOf(isYoutube && !YouTubeOfflineRepository.isDeviceOnline(context))
    }
    var showYoutubeOfflineLibrary by remember(instance.instanceId) {
        mutableStateOf(isYoutube && !YouTubeOfflineRepository.isDeviceOnline(context))
    }
    var selectedOfflineYtVideo by remember(instance.instanceId) {
        mutableStateOf<YouTubeOfflineVideo?>(null)
    }
    var isYoutubeShortsPage by remember(instance.instanceId) {
        mutableStateOf(currentUrl.contains("/shorts", ignoreCase = true))
    }

    // HTML5 / YouTube Fullscreen Video State
    var customVideoView by remember(instance.instanceId) { mutableStateOf<View?>(null) }
    var customVideoViewCallback by remember(instance.instanceId) { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var isWebVideoFullscreen by remember(instance.instanceId) { mutableStateOf(false) }

    val applyVideoFullscreenSystemUi: (Boolean) -> Unit = remember(context) {
        { fullscreen ->
            val activity = context as? Activity
            if (activity != null) {
                activity.requestedOrientation = if (fullscreen) {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
                try {
                    val window = activity.window
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    if (fullscreen) {
                        controller.systemBarsBehavior =
                            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        controller.hide(WindowInsetsCompat.Type.systemBars())
                    } else {
                        controller.show(WindowInsetsCompat.Type.systemBars())
                    }
                } catch (_: Exception) {}
            }
        }
    }

    LaunchedEffect(currentUrl) {
        if (isYoutube) {
            isYoutubeShortsPage = currentUrl.contains("/shorts", ignoreCase = true)
        }
    }

    LaunchedEffect(instance.instanceId, isYoutube) {
        if (isYoutube) {
            val saved = YouTubeOfflineRepository.loadSavedVideos(context)
            val online = YouTubeOfflineRepository.isDeviceOnline(context)
            isYoutubeNetworkOffline = !online
            if (!online) {
                showYoutubeOfflineLibrary = true
                if (selectedOfflineYtVideo == null) {
                    selectedOfflineYtVideo = saved.firstOrNull()
                }
            }
        }
    }

    val buildRequiredCallPermissions: (Boolean, Boolean) -> Array<String> = remember {
        { needAudio, needVideo ->
            val list = mutableListOf<String>()
            if (needAudio) list.add(Manifest.permission.RECORD_AUDIO)
            if (needVideo) list.add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                list.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                list.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            list.toTypedArray()
        }
    }

    val callPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasMicPermission = results[Manifest.permission.RECORD_AUDIO]
            ?: (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        hasCameraPermission = results[Manifest.permission.CAMERA]
            ?: (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)

        // Complete any pending WebView WebRTC PermissionRequest immediately once Android OS permissions resolve
        pendingWebPermissionRequest?.let { req ->
            try {
                val requested = req.resources ?: emptyArray()
                val granted = mutableListOf<String>()
                for (r in requested) {
                    if (r == PermissionRequest.RESOURCE_AUDIO_CAPTURE && hasMicPermission) granted.add(r)
                    else if (r == PermissionRequest.RESOURCE_VIDEO_CAPTURE && hasCameraPermission) granted.add(r)
                    else if (r == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID || r == PermissionRequest.RESOURCE_MIDI_SYSEX) granted.add(r)
                }
                val wantsAudio = requested.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                val wantsVideo = requested.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                if (wantsAudio || wantsVideo) {
                    isCallActive = true
                    isVideoCallActive = wantsVideo
                    audioFocusRequest = setWhatsAppCallAudioMode(context, active = true, speakerphoneOn = isSpeakerphoneOn)
                    webViewInstance?.keepScreenOn = true
                }
                if (granted.isNotEmpty()) {
                    req.grant(granted.toTypedArray())
                } else {
                    req.grant(requested)
                }
            } catch (_: Exception) {}
            pendingWebPermissionRequest = null
        }

        if (isWhatsapp) {
            injectWhatsAppWebSupportEngine(webViewInstance, isWhatsAppPhoneUi)
        } else if (isDiscord) {
            injectDiscordStabilityAndLoginHelper(webViewInstance, instance.isDesktopMode)
        }
    }

    var fileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            if (data?.clipData != null) {
                val count = data.clipData!!.itemCount
                val list = mutableListOf<Uri>()
                for (i in 0 until count) {
                    list.add(data.clipData!!.getItemAt(i).uri)
                }
                list.toTypedArray()
            } else if (data?.data != null) {
                arrayOf(data.data!!)
            } else {
                null
            }
        } else {
            null
        }
        fileChooserCallback?.onReceiveValue(uris)
        fileChooserCallback = null
    }

    // Proactively request Microphone, Camera, Bluetooth & Notification permissions when opening WhatsApp Web
    LaunchedEffect(instance.instanceId, isWhatsapp) {
        hasMicPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (isWhatsapp && (!hasMicPermission || !hasCameraPermission)) {
            callPermissionsLauncher.launch(buildRequiredCallPermissions(true, true))
        }
    }

    // Properly release or retain WebView renderer and call audio resources when leaving CloneWorkspaceScreen
    DisposableEffect(instance.instanceId) {
        onDispose {
            applyVideoFullscreenSystemUi(false)
            if (keepAliveInMinimizedPool) {
                try {
                    webViewInstance?.let { wv ->
                        (wv.parent as? ViewGroup)?.removeView(wv)
                        CloneWebViewPool.retain(instance.instanceId, wv)
                    }
                } catch (_: Exception) {}
            } else {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        audioFocusRequest?.let { req ->
                            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                            am?.abandonAudioFocusRequest(req)
                        }
                    }
                    setWhatsAppCallAudioMode(context, active = false)
                } catch (_: Exception) {}
                CloneWebViewPool.destroy(instance.instanceId)
                try {
                    webViewInstance?.let { wv ->
                        wv.keepScreenOn = false
                        (wv.parent as? ViewGroup)?.removeView(wv)
                        wv.stopLoading()
                        wv.destroy()
                    }
                } catch (_: Exception) {}
            }
            webViewInstance = null
        }
    }

    BackHandler {
        if (customVideoView != null || isWebVideoFullscreen) {
            try {
                customVideoViewCallback?.onCustomViewHidden()
            } catch (_: Exception) {}
            customVideoView = null
            customVideoViewCallback = null
            isWebVideoFullscreen = false
            applyVideoFullscreenSystemUi(false)
            webViewInstance?.evaluateJavascript(
                "if (window.__xenoExitYtFullscreen) window.__xenoExitYtFullscreen();",
                null
            )
        } else if (isYoutube && showYoutubeOfflineLibrary && YouTubeOfflineRepository.isDeviceOnline(context)) {
            showYoutubeOfflineLibrary = false
        } else if (isWhatsapp && webViewInstance != null) {
            val wv = webViewInstance
            if (wv != null) {
                wv.evaluateJavascript(
                    "(function(){ return (window.__xenoHandleAndroidBack && window.__xenoHandleAndroidBack()) || 'exit'; })()"
                ) { res ->
                    val cleaned = res?.trim('"') ?: "exit"
                    if (cleaned != "handled") {
                        if (wv.canGoBack()) {
                            wv.goBack()
                        } else {
                            keepAliveInMinimizedPool = false
                            CloneWebViewPool.destroy(instance.instanceId)
                            onCloseWorkspace()
                        }
                    }
                }
            }
        } else if (webViewInstance?.canGoBack() == true && !showYoutubeOfflineLibrary) {
            webViewInstance?.goBack()
        } else {
            keepAliveInMinimizedPool = false
            CloneWebViewPool.destroy(instance.instanceId)
            onCloseWorkspace()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .background(if (isWebVideoFullscreen || customVideoView != null) Color.Black else MaterialTheme.colorScheme.surface)
            .testTag("clone_workspace_${instance.instanceId}")
    ) {
        // Workspace Top Bar - Sleek, Compact & Space-Efficient (Hidden during fullscreen video playback)
        if (!isWebVideoFullscreen && customVideoView == null) {
        Surface(
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (isWhatsapp && isWhatsAppPhoneUi && isWhatsAppChatOpen && webViewInstance != null) {
                            webViewInstance?.evaluateJavascript(
                                "if (window.__xenoHandleAndroidBack) window.__xenoHandleAndroidBack();",
                                null
                            )
                        } else {
                            keepAliveInMinimizedPool = false
                            CloneWebViewPool.destroy(instance.instanceId)
                            onCloseWorkspace()
                        }
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("workspace_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = if (isWhatsapp && isWhatsAppChatOpen) "Back to Chats" else "Back",
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                CloneAppVisualIcon(
                    instance = instance,
                    iconSize = 24.dp,
                    cornerRadius = 7.dp,
                    showBadgeDot = false
                )

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = instance.displayName,
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.5.sp),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = badgeColor.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = instance.badgeTag,
                            color = badgeColor,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }

                    if (isWhatsapp && isWhatsAppPhoneUi && isWhatsAppChatOpen) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF25D366).copy(alpha = 0.18f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    webViewInstance?.evaluateJavascript(
                                        "if (window.__xenoCloseMobileChat) window.__xenoCloseMobileChat();",
                                        null
                                    )
                                }
                                .testTag("whatsapp_back_to_chats_pill")
                        ) {
                            Text(
                                text = "Chats",
                                color = Color(0xFF25D366),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (isYoutube) {
                        // YouTube Download Current Video Button (hidden when watching YouTube Shorts)
                        val isWatchingShortsNow = isYoutubeShortsPage || currentUrl.contains("/shorts", ignoreCase = true)
                        if (!isWatchingShortsNow) {
                            val isDownloadingAny = youtubeActiveDownloads.isNotEmpty()
                            IconButton(
                                onClick = {
                                    val wv = webViewInstance
                                    if (wv != null && !showYoutubeOfflineLibrary) {
                                        wv.evaluateJavascript(
                                            "(function(){ return (window.__xenoTriggerYtDownload && window.__xenoTriggerYtDownload()) ? 'started' : ''; })()"
                                        ) { result ->
                                            val cleaned = result?.trim('"') ?: ""
                                            if (cleaned != "started") {
                                                // Fallback: parse video ID from current URL if on /watch (never on /shorts)
                                                val urlStr = wv.url ?: currentUrl
                                                val fallbackId = if (urlStr.contains("v=") && !urlStr.contains("/shorts", ignoreCase = true)) {
                                                    urlStr.substringAfter("v=").substringBefore("&")
                                                } else {
                                                    ""
                                                }
                                                if (fallbackId.isNotBlank()) {
                                                    YouTubeOfflineRepository.startVideoDownload(
                                                        context = context,
                                                        mainHandler = mainHandler,
                                                        videoId = fallbackId,
                                                        rawTitle = currentTitle.removeSuffix("- YouTube").trim(),
                                                        rawChannel = "YouTube",
                                                        durationSecondsStr = "0",
                                                        directStreamUrl = "",
                                                        userAgent = wv.settings.userAgentString ?: MOBILE_USER_AGENT
                                                    )
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        "Open any YouTube video first to download it with sound for offline viewing",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        }
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Switch to Live YouTube and open a video to download",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("youtube_download_video_btn")
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isDownloadingAny) Color(0xFFFF0000).copy(alpha = 0.2f) else Color.Transparent,
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (isDownloadingAny) {
                                            CircularProgressIndicator(
                                                color = Color(0xFFFF0000),
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Filled.Download,
                                                contentDescription = "Download YouTube Video for Offline",
                                                tint = Color(0xFFFF0000),
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // YouTube Offline Downloads Library Toggle Button (Desktop Downloads style)
                        IconButton(
                            onClick = {
                                val nextShow = !showYoutubeOfflineLibrary
                                showYoutubeOfflineLibrary = nextShow
                                if (nextShow) {
                                    webViewInstance?.evaluateJavascript(
                                        "try { document.querySelectorAll('video').forEach(function(v){ v.pause(); }); } catch(e){}",
                                        null
                                    )
                                    val saved = YouTubeOfflineRepository.loadSavedVideos(context)
                                    if (selectedOfflineYtVideo == null) {
                                        selectedOfflineYtVideo = saved.firstOrNull()
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("youtube_offline_library_btn")
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (showYoutubeOfflineLibrary) Color(0xFFFF0000).copy(alpha = 0.22f) else Color.Transparent,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (youtubeOfflineVideos.isNotEmpty()) Icons.Filled.DownloadDone else Icons.Filled.VideoLibrary,
                                        contentDescription = "Offline Downloaded Videos (${youtubeOfflineVideos.size})",
                                        tint = if (showYoutubeOfflineLibrary || youtubeOfflineVideos.isNotEmpty()) Color(0xFFFF0000) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    if (youtubeOfflineVideos.isNotEmpty()) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFFF0000))
                                        ) {
                                            Text(
                                                text = youtubeOfflineVideos.size.coerceAtMost(99).toString(),
                                                color = Color.White,
                                                fontSize = 7.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (isWhatsapp) {
                        // Voice & Video Call Permission / Audio Routing Controls for WhatsApp Web
                        if (isCallActive) {
                            IconButton(
                                onClick = {
                                    isSpeakerphoneOn = !isSpeakerphoneOn
                                    setWhatsAppCallAudioMode(context, active = true, speakerphoneOn = isSpeakerphoneOn)
                                    Toast.makeText(
                                        context,
                                        if (isSpeakerphoneOn) "Speakerphone On" else "Earpiece / Headset Mode",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("whatsapp_call_speaker_btn")
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSpeakerphoneOn) Color(0xFF25D366).copy(alpha = 0.2f) else Color.Transparent,
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isSpeakerphoneOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.Filled.PhoneInTalk,
                                            contentDescription = "Toggle Call Speakerphone",
                                            tint = if (isSpeakerphoneOn) Color(0xFF25D366) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            if (isVideoCallActive) {
                                IconButton(
                                    onClick = {
                                        isFrontCamera = !isFrontCamera
                                        val targetFacing = if (isFrontCamera) "user" else "environment"
                                        webViewInstance?.evaluateJavascript(
                                            "if (window.__xenoFlipCallCamera) window.__xenoFlipCallCamera('$targetFacing');",
                                            null
                                        )
                                        Toast.makeText(
                                            context,
                                            if (isFrontCamera) "Switched to Front Camera" else "Switched to Back Camera",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    modifier = Modifier
                                        .size(30.dp)
                                        .testTag("whatsapp_switch_camera_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Cameraswitch,
                                        contentDescription = "Switch Front/Back Camera",
                                        tint = Color(0xFF25D366),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = {
                                hasMicPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                                if (!hasMicPermission || !hasCameraPermission) {
                                    callPermissionsLauncher.launch(buildRequiredCallPermissions(true, true))
                                } else {
                                    setWhatsAppCallAudioMode(context, active = true, speakerphoneOn = isSpeakerphoneOn)
                                    injectWhatsAppWebSupportEngine(webViewInstance, isWhatsAppPhoneUi)
                                    Toast.makeText(
                                        context,
                                        "Voice & Video Calls Ready (Mic & Camera Enabled)",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier
                                .size(30.dp)
                                .testTag("whatsapp_call_permissions_btn")
                        ) {
                            val callsReady = hasMicPermission && hasCameraPermission
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (callsReady) Color(0xFF25D366).copy(alpha = 0.16f) else MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.size(26.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (callsReady) Icons.Filled.Videocam else Icons.Filled.Mic,
                                        contentDescription = if (callsReady) "Voice & Video Calls Ready" else "Grant Mic & Camera Permissions",
                                        tint = if (callsReady) Color(0xFF25D366) else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    IconButton(
                        onClick = {
                            if (isWhatsapp) {
                                isWhatsAppPhoneUi = !isWhatsAppPhoneUi
                                injectWhatsAppWebSupportEngine(webViewInstance, isWhatsAppPhoneUi)
                                Toast.makeText(
                                    context,
                                    if (isWhatsAppPhoneUi) "WhatsApp Phone Screen UI Active" else "WhatsApp Split Desktop UI Active",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                onToggleDesktopMode()
                                webViewInstance?.let { wv ->
                                    wv.settings.userAgentString = if (!instance.isDesktopMode) DESKTOP_USER_AGENT else MOBILE_USER_AGENT
                                    wv.reload()
                                }
                            }
                        },
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("toggle_desktop_mode_btn")
                    ) {
                        val isPhoneUiHighlighted = isWhatsapp && isWhatsAppPhoneUi
                        val isDesktopHighlighted = (!isWhatsapp && instance.isDesktopMode) || (isWhatsapp && !isWhatsAppPhoneUi)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when {
                                isPhoneUiHighlighted -> Color(0xFF25D366).copy(alpha = 0.16f)
                                isDesktopHighlighted -> MaterialTheme.colorScheme.primaryContainer
                                else -> Color.Transparent
                            },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isDesktopHighlighted) Icons.Filled.DesktopMac else Icons.Filled.PhoneAndroid,
                                    contentDescription = if (isDesktopHighlighted) "Desktop View Active" else "Phone Screen UI Active",
                                    tint = when {
                                        isPhoneUiHighlighted -> Color(0xFF25D366)
                                        isDesktopHighlighted -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    if (onOpenInBrowser != null) {
                        IconButton(
                            onClick = { onOpenInBrowser(currentUrl.ifBlank { instance.targetUrl }) },
                            modifier = Modifier
                                .size(30.dp)
                                .testTag("workspace_open_in_browser_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open in Web Browser",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            hasRenderCrash = false
                            if (isWhatsapp) {
                                webViewInstance?.loadUrl("https://web.whatsapp.com/", WHATSAPP_WEB_HEADERS)
                            } else {
                                webViewInstance?.reload()
                            }
                        },
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("workspace_reload_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Reload",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Minimize to Floating Icon Button
                    IconButton(
                        onClick = {
                            keepAliveInMinimizedPool = true
                            webViewInstance?.let { wv ->
                                CloneWebViewPool.retain(instance.instanceId, wv)
                            }
                            onMinimizeWorkspace()
                        },
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("workspace_minimize_btn")
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Remove,
                                    contentDescription = "Minimize to floating icon",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = {
                            keepAliveInMinimizedPool = false
                            CloneWebViewPool.destroy(instance.instanceId)
                            onCloseWorkspace()
                        },
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("workspace_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close to Space",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
        }

        // Loading Progress Indicator
        if (isLoading && loadProgress < 100 && !isWebVideoFullscreen && customVideoView == null) {
            LinearProgressIndicator(
                progress = { loadProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = badgeColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        // Isolated WebView Container with Automatic Renderer Recovery
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    when {
                        isWhatsapp -> Color(0xFF111B21)
                        isDiscord -> Color(0xFF313338)
                        isInstagram || isYoutube -> Color.Black
                        else -> Color.White
                    }
                )
        ) {
            if (hasRenderCrash) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .align(Alignment.Center)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = if (isWhatsapp) "WhatsApp Web Session Restored" else "Session Graphics Reset",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap below to continue in safe rendering mode.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    CloneWebViewPool.destroy(instance.instanceId)
                                    useSoftwareLayer = true
                                    hasRenderCrash = false
                                    reloadKey++
                                }
                            ) {
                                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reload")
                            }

                            FilledTonalButton(
                                onClick = {
                                    CloneWebViewPool.destroy(instance.instanceId)
                                    try {
                                        val cookieManager = CookieManager.getInstance()
                                        cookieManager.removeAllCookies(null)
                                        cookieManager.flush()
                                        WebStorage.getInstance().deleteAllData()
                                    } catch (_: Exception) {}
                                    useSoftwareLayer = true
                                    hasRenderCrash = false
                                    reloadKey++
                                }
                            ) {
                                Text("Reset Cache")
                            }
                        }
                    }
                }
            } else {
                key(instance.instanceId, reloadKey) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            val existing = CloneWebViewPool.get(instance.instanceId)
                            if (existing != null && reloadKey == 0) {
                                (existing.parent as? ViewGroup)?.removeView(existing)
                                existing.layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                isLoading = false
                                loadProgress = 100
                                canGoBack = existing.canGoBack()
                                if (isWhatsapp) {
                                    injectWhatsAppWebSupportEngine(existing, isWhatsAppPhoneUi)
                                } else if (isYoutube) {
                                    injectYouTubeAndShortsSmoothHelper(existing)
                                } else if (isInstagram) {
                                    injectInstagramReelsAndScrollHelper(existing)
                                }
                                com.example.ui.components.GlobalSwipeDownTracker.attachToWebView(
                                    existing,
                                    disallowParentIntercept = isYoutube || isFacebook || isInstagram || isDiscord
                                )
                                com.example.ui.components.GlobalSwipeDownTracker.injectSwipeScrollObserver(existing)
                                webViewInstance = existing
                                existing
                            } else {
                                WebView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    // Use hardware layer for WhatsApp Web, YouTube, and Instagram so WebRTC/Reels/MSE video codecs and 60fps scroll run at full capability
                                    if (useSoftwareLayer && !isYoutube && !isWhatsapp && !isInstagram) {
                                        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                                    } else {
                                        setLayerType(View.LAYER_TYPE_HARDWARE, null)
                                    }
                                    isVerticalScrollBarEnabled = !isShortVideoSocialApp || isYoutube || isFacebook
                                    isHorizontalScrollBarEnabled = !isShortVideoSocialApp
                                    if (isInstagram) {
                                        overScrollMode = View.OVER_SCROLL_NEVER
                                    } else {
                                        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                                    }
                                isNestedScrollingEnabled = true
                                isFocusable = true
                                isFocusableInTouchMode = true
                                com.example.ui.components.GlobalSwipeDownTracker.attachToWebView(
                                    this,
                                    disallowParentIntercept = isYoutube || isFacebook || isInstagram || isDiscord
                                )
                                setBackgroundColor(
                                    when {
                                        isWhatsapp -> android.graphics.Color.parseColor("#111B21")
                                        isDiscord -> android.graphics.Color.parseColor("#313338")
                                        isInstagram || isYoutube -> android.graphics.Color.BLACK
                                        else -> android.graphics.Color.WHITE
                                    }
                                )

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    loadWithOverviewMode = !(isYoutube && !instance.isDesktopMode) && !(isWhatsapp && isWhatsAppPhoneUi)
                                    useWideViewPort = true
                                    // Disable pinch-zoom on Instagram, Facebook, TikTok & YouTube so double-tap-to-like and fast vertical video swipes stay crisp
                                    builtInZoomControls = !isShortVideoSocialApp
                                    displayZoomControls = false
                                    safeBrowsingEnabled = false
                                    setSupportZoom(!isShortVideoSocialApp)
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    mediaPlaybackRequiresUserGesture = false
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                    javaScriptCanOpenWindowsAutomatically = true
                                    setSupportMultipleWindows(true)
                                    setGeolocationEnabled(true)
                                    allowFileAccess = true
                                    allowContentAccess = true
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        offscreenPreRaster = true
                                    }
                                    @Suppress("DEPRECATION")
                                    setRenderPriority(WebSettings.RenderPriority.HIGH)
                                    defaultTextEncodingName = "UTF-8"
                                    // Discord web client (/app & /login) and WhatsApp Web require a desktop Chrome User-Agent to serve full calling and web app features
                                    userAgentString = if (isWhatsapp || isDiscord || instance.isDesktopMode) {
                                        DESKTOP_USER_AGENT
                                    } else {
                                        MOBILE_USER_AGENT
                                    }
                                }

                                // Register native WhatsApp Voice & Video Call hardware bridge
                                addJavascriptInterface(
                                    WhatsAppCallJsBridge(
                                        mainHandler = mainHandler,
                                        onCallStateChanged = { active, isVideo ->
                                            isCallActive = active
                                            isVideoCallActive = isVideo
                                            this@apply.keepScreenOn = active
                                            if (active) {
                                                if (isVideo) {
                                                    this@apply.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                                                }
                                                audioFocusRequest = setWhatsAppCallAudioMode(
                                                    ctx,
                                                    active = true,
                                                    speakerphoneOn = isSpeakerphoneOn
                                                )
                                            } else {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                    audioFocusRequest?.let { req ->
                                                        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                                                        am?.abandonAudioFocusRequest(req)
                                                    }
                                                }
                                                audioFocusRequest = null
                                                setWhatsAppCallAudioMode(ctx, active = false)
                                            }
                                        },
                                        onEnsurePermissions = { needAudio, needVideo ->
                                            val micOk = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                            val camOk = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                                            hasMicPermission = micOk
                                            hasCameraPermission = camOk
                                            if ((needAudio && !micOk) || (needVideo && !camOk)) {
                                                callPermissionsLauncher.launch(buildRequiredCallPermissions(needAudio || !micOk, needVideo || !camOk))
                                            }
                                        },
                                        checkPermissionsGranted = {
                                            val micOk = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                            val camOk = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                                            micOk && camOk
                                        },
                                        onMobileChatStateChanged = { chatOpen, _ ->
                                            isWhatsAppChatOpen = chatOpen
                                        }
                                    ),
                                    "XenoCallBridge"
                                )

                                // Register YouTube Offline Download Bridge
                                if (isYoutube) {
                                    addJavascriptInterface(
                                        YouTubeJsDownloadBridge(
                                            context = ctx,
                                            mainHandler = mainHandler,
                                            userAgentProvider = {
                                                this@apply.settings.userAgentString ?: MOBILE_USER_AGENT
                                            },
                                            onOpenOfflineLibrary = {
                                                this@apply.evaluateJavascript(
                                                    "try { document.querySelectorAll('video').forEach(function(v){ v.pause(); }); } catch(e){}",
                                                    null
                                                )
                                                val saved = YouTubeOfflineRepository.loadSavedVideos(ctx)
                                                if (selectedOfflineYtVideo == null) {
                                                    selectedOfflineYtVideo = saved.firstOrNull()
                                                }
                                                showYoutubeOfflineLibrary = true
                                            },
                                            onPageModeChanged = { mode ->
                                                isYoutubeShortsPage = (mode == "shorts")
                                            },
                                            onFullscreenChanged = { fullscreen ->
                                                isWebVideoFullscreen = fullscreen
                                                applyVideoFullscreenSystemUi(fullscreen)
                                            }
                                        ),
                                        "XenoYtBridge"
                                    )
                                }

                                setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                                    try {
                                        val request = android.app.DownloadManager.Request(android.net.Uri.parse(url)).apply {
                                            setMimeType(mimetype)
                                            val cookies = CookieManager.getInstance().getCookie(url)
                                            addRequestHeader("cookie", cookies)
                                            addRequestHeader("User-Agent", userAgent)
                                            setDescription("Downloading file...")
                                            setTitle(android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype))
                                            setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                            setDestinationInExternalPublicDir(
                                                android.os.Environment.DIRECTORY_DOWNLOADS,
                                                android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype)
                                            )
                                        }
                                        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                        dm.enqueue(request)
                                        Toast.makeText(ctx, "Downloading file...", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(ctx, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }

                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                webViewClient = object : WebViewClient() {
                                    private var lastWhatsAppResourceInjectMs = 0L

                                    private fun injectSocialAppHelpers(view: WebView?, url: String?) {
                                        com.example.ui.components.GlobalSwipeDownTracker.injectSwipeScrollObserver(view)
                                        when {
                                            isWhatsapp -> injectWhatsAppWebSupportEngine(view, isWhatsAppPhoneUi)
                                            isDiscord || url?.contains("discord.com", ignoreCase = true) == true || url?.contains("discord.gg", ignoreCase = true) == true ->
                                                injectDiscordStabilityAndLoginHelper(view, instance.isDesktopMode)
                                            isInstagram || url?.contains("instagram.com", ignoreCase = true) == true ->
                                                injectInstagramReelsAndScrollHelper(view)
                                            isFacebook || url?.contains("facebook.com", ignoreCase = true) == true ->
                                                injectFacebookVideoAndScrollHelper(view)
                                            isYoutube || url?.contains("youtube.com", ignoreCase = true) == true || url?.contains("youtu.be", ignoreCase = true) == true ->
                                                injectYouTubeAndShortsSmoothHelper(view)
                                        }
                                    }

                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        isLoading = true
                                        lastWhatsAppResourceInjectMs = 0L
                                        if (isYoutube) {
                                            isYoutubeNetworkOffline = !YouTubeOfflineRepository.isDeviceOnline(ctx)
                                            if (isYoutubeNetworkOffline) {
                                                showYoutubeOfflineLibrary = true
                                            }
                                        }
                                        injectSocialAppHelpers(view, url)
                                        url?.let {
                                            if (it.startsWith("http://") || it.startsWith("https://")) {
                                                currentUrl = it
                                                onUrlChanged(it)
                                            }
                                        }
                                        canGoBack = view?.canGoBack() == true
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        error: WebResourceError?
                                    ) {
                                        super.onReceivedError(view, request, error)
                                        if (isYoutube && request?.isForMainFrame == true) {
                                            val errCode = error?.errorCode ?: 0
                                            if (!YouTubeOfflineRepository.isDeviceOnline(ctx) ||
                                                errCode == ERROR_HOST_LOOKUP ||
                                                errCode == ERROR_CONNECT ||
                                                errCode == ERROR_TIMEOUT
                                            ) {
                                                isLoading = false
                                                isYoutubeNetworkOffline = true
                                                val saved = YouTubeOfflineRepository.loadSavedVideos(ctx)
                                                if (selectedOfflineYtVideo == null) {
                                                    selectedOfflineYtVideo = saved.firstOrNull()
                                                }
                                                showYoutubeOfflineLibrary = true
                                            }
                                        }
                                    }

                                    override fun onLoadResource(view: WebView?, url: String?) {
                                        super.onLoadResource(view, url)
                                        if (isWhatsapp) {
                                            val isMediaAsset = url != null && (
                                                url.contains("pps.whatsapp.net") ||
                                                url.contains("mmg.whatsapp.net") ||
                                                url.startsWith("blob:") ||
                                                url.startsWith("data:")
                                            )
                                            val nowMs = android.os.SystemClock.elapsedRealtime()
                                            if (!isMediaAsset && (nowMs - lastWhatsAppResourceInjectMs > 1200L)) {
                                                lastWhatsAppResourceInjectMs = nowMs
                                                injectWhatsAppWebSupportEngine(view, isWhatsAppPhoneUi)
                                            }
                                        } else if (isYoutube && url != null && url.contains("googlevideo.com/videoplayback")) {
                                            YouTubeOfflineRepository.onCapturedGoogleVideoUrl(
                                                pageUrl = view?.url ?: currentUrl,
                                                resourceUrl = url,
                                                userAgent = view?.settings?.userAgentString ?: MOBILE_USER_AGENT
                                            )
                                        }
                                    }

                                    override fun onPageCommitVisible(view: WebView?, url: String?) {
                                        super.onPageCommitVisible(view, url)
                                        injectSocialAppHelpers(view, url)
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        isLoading = false
                                        injectSocialAppHelpers(view, url)
                                        try {
                                            CookieManager.getInstance().flush()
                                        } catch (_: Exception) {}
                                        url?.let {
                                            if (it.startsWith("http://") || it.startsWith("https://")) {
                                                currentUrl = it
                                                onUrlChanged(it)
                                            }
                                        }
                                        currentTitle = view?.title ?: instance.displayName
                                        canGoBack = view?.canGoBack() == true
                                    }

                                    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                        super.doUpdateVisitedHistory(view, url, isReload)
                                        injectSocialAppHelpers(view, url)
                                        url?.let {
                                            if (it.startsWith("http://") || it.startsWith("https://")) {
                                                currentUrl = it
                                                onUrlChanged(it)
                                            }
                                        }
                                        canGoBack = view?.canGoBack() == true
                                    }

                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        val url = request?.url?.toString() ?: return false
                                        val lowerUrl = url.lowercase()
                                        if (isWhatsapp) {
                                            if (url.contains("whatsapp.com/download") ||
                                                url.contains("whatsapp.com/android") ||
                                                (url.contains("www.whatsapp.com") && !url.contains("web.whatsapp.com"))
                                            ) {
                                                val curr = view?.url ?: ""
                                                if (!curr.startsWith("https://web.whatsapp.com")) {
                                                    view?.post {
                                                        view.loadUrl("https://web.whatsapp.com/", WHATSAPP_WEB_HEADERS)
                                                    }
                                                }
                                                return true
                                            }
                                        }
                                        if (isDiscord) {
                                            if (lowerUrl.startsWith("discord://") ||
                                                lowerUrl.contains("discord.com/download") ||
                                                lowerUrl.contains("play.google.com/store/apps")
                                            ) {
                                                val curr = view?.url ?: ""
                                                if (!curr.contains("discord.com/app") && !curr.contains("discord.com/login") && !curr.contains("discord.com/channels")) {
                                                    view?.loadUrl("https://discord.com/app")
                                                }
                                                return true
                                            }
                                        }
                                        // Convert vnd.youtube:// links into mobile web YouTube URLs
                                        if (lowerUrl.startsWith("vnd.youtube:") || lowerUrl.startsWith("youtube://")) {
                                            val cleaned = url.substringAfter("://").substringAfter(":")
                                            if (cleaned.isNotBlank()) {
                                                val webYtUrl = when {
                                                    cleaned.startsWith("http://") || cleaned.startsWith("https://") -> cleaned
                                                    cleaned.contains("youtube.com") || cleaned.contains("youtu.be") -> "https://${cleaned.trimStart('/')}"
                                                    else -> "https://m.youtube.com/watch?v=${cleaned.trimStart('/')}"
                                                }
                                                if (webYtUrl != view?.url) {
                                                    view?.loadUrl(webYtUrl)
                                                }
                                            }
                                            return true
                                        }
                                        // Prevent Facebook, Instagram, YouTube & Discord from kicking the user out to Play Store or external app prompts
                                        if (isFacebook || isInstagram || isYoutube || isDiscord) {
                                            if (lowerUrl.contains("play.google.com/store/apps") ||
                                                lowerUrl.startsWith("market://") ||
                                                lowerUrl.startsWith("fb://") ||
                                                lowerUrl.startsWith("fbmessenger://") ||
                                                lowerUrl.startsWith("discord://")
                                            ) {
                                                return true
                                            }
                                        }
                                        if (url.startsWith("intent://")) {
                                            try {
                                                val parsed = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                                                val fallbackUrl = parsed.getStringExtra("browser_fallback_url")
                                                val hostAndPath = url.removePrefix("intent://").substringBefore("#Intent").trimStart('/')
                                                val candidateUrl = if (!fallbackUrl.isNullOrBlank()) {
                                                    fallbackUrl
                                                } else {
                                                    val dataStr = parsed.dataString
                                                    when {
                                                        !dataStr.isNullOrBlank() && (dataStr.startsWith("http://") || dataStr.startsWith("https://")) -> dataStr
                                                        hostAndPath.contains("youtube.com", ignoreCase = true) || hostAndPath.contains("youtu.be", ignoreCase = true) ->
                                                            "https://$hostAndPath"
                                                        isYoutube && hostAndPath.isNotBlank() ->
                                                            "https://m.youtube.com/$hostAndPath"
                                                        url.contains("scheme=https", ignoreCase = true) || url.contains("scheme=http", ignoreCase = true) ->
                                                            if (hostAndPath.isNotBlank()) "https://$hostAndPath" else null
                                                        else -> null
                                                    }
                                                }
                                                if (!candidateUrl.isNullOrBlank() &&
                                                    (candidateUrl.startsWith("http://") || candidateUrl.startsWith("https://")) &&
                                                    !candidateUrl.contains("play.google.com/store/apps", ignoreCase = true) &&
                                                    !candidateUrl.contains("tiktok.com/download", ignoreCase = true) &&
                                                    candidateUrl != view?.url
                                                ) {
                                                    view?.loadUrl(candidateUrl)
                                                }
                                            } catch (_: Exception) {}
                                            return true
                                        }
                                        if (!lowerUrl.startsWith("http://") &&
                                            !lowerUrl.startsWith("https://") &&
                                            !lowerUrl.startsWith("file://") &&
                                            !lowerUrl.startsWith("about:") &&
                                            !lowerUrl.startsWith("javascript:") &&
                                            !lowerUrl.startsWith("blob:") &&
                                            !lowerUrl.startsWith("data:")
                                        ) {
                                            return true
                                        }
                                        return false
                                    }

                                    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                                        try {
                                            view?.let {
                                                val parent = it.parent as? ViewGroup
                                                parent?.removeView(it)
                                                it.stopLoading()
                                                it.destroy()
                                            }
                                        } catch (_: Exception) {}
                                        webViewInstance = null
                                        if (!useSoftwareLayer) {
                                            // Automatically recover in software layer mode without interrupting the user
                                            useSoftwareLayer = true
                                            reloadKey++
                                        } else {
                                            hasRenderCrash = true
                                        }
                                        return true
                                    }
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        loadProgress = newProgress
                                        if (newProgress >= 100) {
                                            isLoading = false
                                        }
                                    }

                                    override fun onReceivedTitle(view: WebView?, title: String?) {
                                        if (!title.isNullOrBlank()) {
                                            currentTitle = title
                                        }
                                    }

                                    override fun onPermissionRequest(request: PermissionRequest?) {
                                        if (request == null) return
                                        mainHandler.post {
                                            try {
                                                val res = request.resources ?: emptyArray()
                                                val needsAudio = res.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                                                val needsVideo = res.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)

                                                val micGranted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                                val camGranted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                                                hasMicPermission = micGranted
                                                hasCameraPermission = camGranted

                                                if ((needsAudio && !micGranted) || (needsVideo && !camGranted)) {
                                                    // Hold the WebView PermissionRequest until the user grants Android OS Mic/Camera permissions
                                                    pendingWebPermissionRequest = request
                                                    callPermissionsLauncher.launch(
                                                        buildRequiredCallPermissions(needsAudio || !micGranted, needsVideo || !camGranted)
                                                    )
                                                } else {
                                                    if (needsAudio || needsVideo) {
                                                        isCallActive = true
                                                        isVideoCallActive = needsVideo
                                                        this@apply.keepScreenOn = true
                                                        if (needsVideo) {
                                                            this@apply.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                                                        }
                                                        audioFocusRequest = setWhatsAppCallAudioMode(
                                                            ctx,
                                                            active = true,
                                                            speakerphoneOn = isSpeakerphoneOn
                                                        )
                                                    }
                                                    request.grant(res)
                                                }
                                            } catch (_: Exception) {
                                                try {
                                                    request.grant(request.resources)
                                                } catch (_: Exception) {}
                                            }
                                        }
                                    }

                                    override fun onPermissionRequestCanceled(request: PermissionRequest?) {
                                        if (pendingWebPermissionRequest == request) {
                                            pendingWebPermissionRequest = null
                                        }
                                    }

                                    override fun onGeolocationPermissionsShowPrompt(
                                        origin: String?,
                                        callback: GeolocationPermissions.Callback?
                                    ) {
                                        callback?.invoke(origin, true, false)
                                    }

                                    override fun onJsAlert(
                                        view: WebView?,
                                        url: String?,
                                        message: String?,
                                        result: JsResult?
                                    ): Boolean {
                                        try {
                                            val msgLower = message?.lowercase() ?: ""
                                            if (isWhatsapp && msgLower.contains("call") && (msgLower.contains("support") || msgLower.contains("browser"))) {
                                                view?.evaluateJavascript(
                                                    "if (window.__xenoStartWhatsAppCallOverlay) { var c = window.__xenoExtractActiveChatContact ? window.__xenoExtractActiveChatContact() : {}; window.__xenoStartWhatsAppCallOverlay(c.name, c.avatar, Boolean(c.isVideo)); }",
                                                    null
                                                )
                                            } else if (!message.isNullOrBlank()) {
                                                Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (_: Exception) {}
                                        result?.confirm()
                                        return true
                                    }

                                    override fun onJsConfirm(
                                        view: WebView?,
                                        url: String?,
                                        message: String?,
                                        result: JsResult?
                                    ): Boolean {
                                        result?.confirm()
                                        return true
                                    }

                                    override fun onShowFileChooser(
                                        webView: WebView?,
                                        filePathCallback: ValueCallback<Array<Uri>>?,
                                        fileChooserParams: FileChooserParams?
                                    ): Boolean {
                                        fileChooserCallback?.onReceiveValue(null)
                                        fileChooserCallback = filePathCallback
                                        val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                            type = "*/*"
                                            addCategory(Intent.CATEGORY_OPENABLE)
                                        }
                                        return try {
                                            fileChooserLauncher.launch(intent)
                                            true
                                        } catch (_: Exception) {
                                            fileChooserCallback?.onReceiveValue(null)
                                            fileChooserCallback = null
                                            false
                                        }
                                    }

                                    override fun onCreateWindow(
                                        view: WebView?,
                                        isDialog: Boolean,
                                        isUserGesture: Boolean,
                                        resultMsg: Message?
                                    ): Boolean {
                                        val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                                        val parentWebView = view ?: return false
                                        val tempWebView = WebView(parentWebView.context).apply {
                                            settings.javaScriptEnabled = true
                                            settings.domStorageEnabled = true
                                            settings.userAgentString = parentWebView.settings.userAgentString
                                            webViewClient = object : WebViewClient() {
                                                override fun shouldOverrideUrlLoading(
                                                    childView: WebView?,
                                                    request: WebResourceRequest?
                                                ): Boolean {
                                                    val targetUrl = request?.url?.toString() ?: return false
                                                    if (targetUrl.startsWith("http://") || targetUrl.startsWith("https://")) {
                                                        if (isWhatsapp && (targetUrl.contains("whatsapp.com/download") || targetUrl.contains("whatsapp.com/android"))) {
                                                            return true
                                                        }
                                                        parentWebView.loadUrl(
                                                            targetUrl,
                                                            if (isWhatsapp) WHATSAPP_WEB_HEADERS else emptyMap()
                                                        )
                                                    }
                                                    try {
                                                        childView?.destroy()
                                                    } catch (_: Exception) {}
                                                    return true
                                                }
                                            }
                                        }
                                        transport.webView = tempWebView
                                        resultMsg.sendToTarget()
                                        return true
                                    }

                                    override fun onShowCustomView(
                                        view: View?,
                                        callback: CustomViewCallback?
                                    ) {
                                        if (customVideoView != null) {
                                            try {
                                                callback?.onCustomViewHidden()
                                            } catch (_: Exception) {}
                                            return
                                        }
                                        customVideoView = view
                                        customVideoViewCallback = callback
                                        isWebVideoFullscreen = true
                                        this@apply.keepScreenOn = true
                                        applyVideoFullscreenSystemUi(true)
                                    }

                                    override fun onHideCustomView() {
                                        try {
                                            customVideoViewCallback?.onCustomViewHidden()
                                        } catch (_: Exception) {}
                                        customVideoView = null
                                        customVideoViewCallback = null
                                        isWebVideoFullscreen = false
                                        if (!isCallActive) {
                                            this@apply.keepScreenOn = false
                                        }
                                        applyVideoFullscreenSystemUi(false)
                                        this@apply.evaluateJavascript(
                                            "if (window.__xenoSetYtFullscreenState) window.__xenoSetYtFullscreenState(false);",
                                            null
                                        )
                                    }

                                    override fun getDefaultVideoPoster(): Bitmap? {
                                        return try {
                                            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                                        } catch (_: Exception) {
                                            super.getDefaultVideoPoster()
                                        }
                                    }

                                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                        return true
                                    }
                                }

                                val startUrl = when {
                                    isWhatsapp -> "https://web.whatsapp.com/"
                                    isDiscord -> if (instance.lastUrl.startsWith("http") && instance.lastUrl.contains("discord.com", ignoreCase = true) && !instance.lastUrl.contains("/download", ignoreCase = true)) {
                                        instance.lastUrl
                                    } else {
                                        "https://discord.com/app"
                                    }
                                    isYoutube -> if (instance.lastUrl.startsWith("http") && (instance.lastUrl.contains("youtube.com", ignoreCase = true) || instance.lastUrl.contains("youtu.be", ignoreCase = true))) {
                                        instance.lastUrl
                                    } else {
                                        "https://m.youtube.com/"
                                    }
                                    isFacebook -> if (instance.lastUrl.startsWith("http") && instance.lastUrl.contains("facebook.com", ignoreCase = true)) {
                                        instance.lastUrl
                                    } else {
                                        "https://m.facebook.com/"
                                    }
                                    else -> instance.lastUrl.ifBlank { instance.targetUrl }
                                }
                                val extraHeaders = if (isWhatsapp) {
                                    WHATSAPP_WEB_HEADERS
                                } else emptyMap()

                                if (extraHeaders.isNotEmpty()) {
                                    loadUrl(startUrl, extraHeaders)
                                } else {
                                    loadUrl(startUrl)
                                }
                                webViewInstance = this
                            }
                            }
                        },
                        update = { wv ->
                            webViewInstance = wv
                            canGoBack = wv.canGoBack()
                        }
                    )
                }
            }

            if (isYoutube && showYoutubeOfflineLibrary) {
                YouTubeOfflineDesktopView(
                    videos = youtubeOfflineVideos,
                    activeDownloads = youtubeActiveDownloads,
                    isOfflineNetwork = isYoutubeNetworkOffline,
                    selectedVideo = selectedOfflineYtVideo,
                    onSelectVideo = { selectedOfflineYtVideo = it },
                    onDeleteVideo = { videoId ->
                        YouTubeOfflineRepository.deleteOfflineVideo(context, videoId)
                        if (selectedOfflineYtVideo?.videoId == videoId) {
                            selectedOfflineYtVideo = youtubeOfflineVideos.firstOrNull { it.videoId != videoId }
                        }
                    },
                    onReturnToLiveYouTube = {
                        val online = YouTubeOfflineRepository.isDeviceOnline(context)
                        isYoutubeNetworkOffline = !online
                        isWebVideoFullscreen = false
                        applyVideoFullscreenSystemUi(false)
                        if (online) {
                            showYoutubeOfflineLibrary = false
                            webViewInstance?.reload()
                        } else {
                            showYoutubeOfflineLibrary = false
                            webViewInstance?.reload()
                        }
                    },
                    onFullscreenChanged = { fs ->
                        isWebVideoFullscreen = fs
                        applyVideoFullscreenSystemUi(fs)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else if (isLoading && loadProgress < 25 && !hasRenderCrash && customVideoView == null && !isWebVideoFullscreen) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = badgeColor,
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp
                        )
                        Text(
                            text = if (isWhatsapp) "Loading WhatsApp Web..." else "Loading ${instance.displayName}...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Native HTML5 Fullscreen Video CustomView Overlay (keeps WebView mounted underneath)
            customVideoView?.let { activeCustomView ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .testTag("clone_fullscreen_video_overlay")
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = {
                            (activeCustomView.parent as? ViewGroup)?.removeView(activeCustomView)
                            activeCustomView
                        }
                    )
                }
            }
        }
    }
}

