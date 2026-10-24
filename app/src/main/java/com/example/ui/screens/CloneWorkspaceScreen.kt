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
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
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

                    defineNavProp('userAgentData', function() { return desktopUaData; });
                    defineNavProp('platform', function() { return 'Win32'; });
                    defineNavProp('vendor', function() { return 'Google Inc.'; });
                    defineNavProp('maxTouchPoints', function() { return 0; });
                    defineNavProp('webdriver', function() { return false; });
                    defineNavProp('hardwareConcurrency', function() { return 8; });
                    defineNavProp('deviceMemory', function() { return 8; });

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

                    // 6. WebRTC MediaDevices (Microphone, Camera, Speaker & getDisplayMedia) Engine
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
                                    var pc = Reflect.construct(target, newArgs, newTarget);
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

                    // 9. Guarantee RTCRtpSender & RTCRtpReceiver Codec Capabilities (Opus, H264, VP8, VP9)
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
                    };
                    if (typeof window.RTCRtpSender !== 'undefined') ensureRtpCapabilities(window.RTCRtpSender);
                    if (typeof window.RTCRtpReceiver !== 'undefined') ensureRtpCapabilities(window.RTCRtpReceiver);
                    if (typeof window.RTCRtpTransceiver !== 'undefined' && window.RTCRtpTransceiver.prototype && typeof window.RTCRtpTransceiver.prototype.setCodecPreferences !== 'function') {
                        window.RTCRtpTransceiver.prototype.setCodecPreferences = function() {};
                    }

                    // 10. Hook WhatsApp Web's internal module loader (window.require / __d) so Calling Capability & ABProp checks return true
                    var patchWhatsAppCallingModuleExports = function(mod) {
                        if (!mod || (typeof mod !== 'object' && typeof mod !== 'function')) return mod;
                        var callCapKeys = [
                            'isCallingSupported', 'isVoiceCallSupported', 'isVideoCallSupported', 'isGroupCallSupported',
                            'supportsCalling', 'supportsVoiceCall', 'supportsVideoCall', 'isWebCallingSupported',
                            'isWebCallingEnabled', 'isVoipSupported', 'isBrowserSupportedForCalling', 'canStartCall',
                            'isCallSupported', 'isCallEnabled', 'hasCallingSupport', 'isCallingEnabled'
                        ];
                        for (var i = 0; i < callCapKeys.length; i++) {
                            var key = callCapKeys[i];
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
                        return mod;
                    };

                    var hookWhatsAppRequire = function() {
                        if (typeof window.require === 'function' && !window.require.__xenoHooked) {
                            var origReq = window.require;
                            var wrappedReq = function(id) {
                                var res = origReq.apply(this, arguments);
                                try {
                                    var idStr = String(id || '');
                                    if (idStr.indexOf('Call') !== -1 || idStr.indexOf('Voip') !== -1 || idStr.indexOf('Webrtc') !== -1 || idStr.indexOf('Browser') !== -1 || idStr.indexOf('Capabilit') !== -1) {
                                        patchWhatsAppCallingModuleExports(res);
                                    } else if (res && typeof res === 'object' && ('isCallingSupported' in res || 'isVoiceCallSupported' in res || 'isWebCallingSupported' in res || 'supportsCalling' in res)) {
                                        patchWhatsAppCallingModuleExports(res);
                                    }
                                } catch(e) {}
                                return res;
                            };
                            Object.keys(origReq).forEach(function(k) {
                                try { wrappedReq[k] = origReq[k]; } catch(e) {}
                            });
                            wrappedReq.__xenoHooked = true;
                            window.require = wrappedReq;
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
                            topBar.innerHTML =
                                '<div style="display:flex;align-items:center;gap:6px;color:#8696a0;font-size:13px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">' +
                                '  <span style="color:#00a884;font-size:14px;">&#128274;</span>' +
                                '  <span>End-to-end encrypted &bull; ' + (isVideo ? 'Video' : 'Voice') + ' Call</span>' +
                                '</div>';

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

                            var videoBtn = makeBtn(isVideo ? 'Cam On' : 'Cam Off', isVideo ? '#00a884' : '#2a3942', function() {
                                isVideo = !isVideo;
                                videoBtn.textContent = isVideo ? 'Cam On' : 'Cam Off';
                                videoBtn.style.background = isVideo ? '#00a884' : '#2a3942';
                                videoContainer.style.display = isVideo ? 'flex' : 'none';
                                videoContainer.style.height = isVideo ? '240px' : '0px';
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
                            }, 4200);
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
                            window.__xenoDismissedChatSignature = null;
                            document.documentElement.classList.add('xeno-wa-chat-open');
                            window.__xenoNotifyMobileNavBridge();
                        } catch(e) {}
                    };

                    window.__xenoCloseMobileChat = function() {
                        try {
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

                            // B. Handle clicks inside the Left Navigation Rail (Chats, Status, Channels, Communities, Settings, Profile)
                            var navRailHit = target.closest('[data-xeno-wa-role="nav-rail"]');
                            if (navRailHit && window.__xenoWaPhoneMode) {
                                window.__xenoCloseMobileChat();
                            }

                            // C. Handle clicks on a chat item or contact item in #pane-side or left drawers to open the conversation on phone screen
                            if (window.__xenoWaPhoneMode) {
                                var paneSideItem = target.closest('#pane-side [role="listitem"], #pane-side [role="row"], #pane-side [data-testid="cell-frame-container"]');
                                var sideDrawerChatItem = !paneSideItem && target.closest('[data-xeno-wa-role="side-col"] [role="listitem"], [data-xeno-wa-role="side-col"] [role="row"], [data-xeno-wa-role="side-col"] [data-testid="cell-frame-container"]');
                                var chatItem = paneSideItem || sideDrawerChatItem;
                                if (chatItem) {
                                    var isArchiveBtn = Boolean(chatItem.querySelector('[data-icon*="archive"]')) ||
                                        (String(chatItem.textContent || '').trim().toLowerCase().indexOf('archived') === 0 && String(chatItem.textContent || '').trim().length < 24);
                                    var isFilterOrHeader = Boolean(target.closest('header, [role="tablist"], button[aria-pressed], input'));
                                    if (!isArchiveBtn && !isFilterOrHeader) {
                                        window.__xenoDismissedChatSignature = null;
                                        window.__xenoLastChatRowTapTime = Date.now();
                                        setTimeout(function() {
                                            if (document.getElementById('main')) {
                                                window.__xenoOpenMobileChat();
                                            }
                                        }, 50);
                                        setTimeout(function() {
                                            if (document.getElementById('main') && !window.__xenoDismissedChatSignature) {
                                                window.__xenoOpenMobileChat();
                                            }
                                        }, 220);
                                    }
                                }
                            }

                            // D. Track clicks on Voice Call / Video Call buttons in WhatsApp Web
                            var btn = target.closest('button, [role="button"], li, [data-icon]');
                            if (!btn) return;
                            var iconEl = btn.querySelector('[data-icon]');
                            var dataIcon = (btn.getAttribute('data-icon') || (iconEl && iconEl.getAttribute('data-icon')) || '').toLowerCase();
                            var ariaLabel = (btn.getAttribute('aria-label') || btn.getAttribute('title') || btn.textContent || '').trim().toLowerCase();

                            var isVoiceBtn = dataIcon.indexOf('voice-call') !== -1 ||
                                dataIcon === 'call' ||
                                dataIcon === 'audio-call' ||
                                ariaLabel.indexOf('voice call') !== -1 ||
                                ariaLabel === 'audio call';
                            var isVideoBtn = dataIcon.indexOf('video-call') !== -1 ||
                                dataIcon === 'videocall' ||
                                ariaLabel.indexOf('video call') !== -1;

                            if (isVoiceBtn || isVideoBtn) {
                                window.__xenoExtractActiveChatContact();
                                window.__xenoLastChatContact.isVideo = isVideoBtn;
                                window.__xenoLastCallTapTime = Date.now();
                            }
                        } catch(e) {}
                    }, true);

                    // Intercept & suppress any "Browser does not support calls" / "Calling isn't supported on this browser" modal
                    window.__xenoInterceptUnsupportedCallDialog = function() {
                        try {
                            var dialogs = document.querySelectorAll('[role="dialog"], [data-animate-modal-popup="true"], [data-animate-modal-body="true"]');
                            for (var i = 0; i < dialogs.length; i++) {
                                var dlg = dialogs[i];
                                if (!dlg || dlg.id === 'xeno-wa-live-call-overlay') continue;
                                var txt = (dlg.textContent || '').toLowerCase();
                                var isUnsupportedCallMsg =
                                    txt.indexOf('does not support calls') !== -1 ||
                                    txt.indexOf('doesn\'t support calls') !== -1 ||
                                    txt.indexOf('calling isn\'t supported') !== -1 ||
                                    txt.indexOf('calling is not supported') !== -1 ||
                                    txt.indexOf('calls aren\'t supported') !== -1 ||
                                    txt.indexOf('calls are not supported') !== -1 ||
                                    (txt.indexOf('not supported on this browser') !== -1 && txt.indexOf('call') !== -1) ||
                                    (txt.indexOf('make calls') !== -1 && (txt.indexOf('get the app') !== -1 || txt.indexOf('download') !== -1 || txt.indexOf('windows') !== -1));

                                if (isUnsupportedCallMsg) {
                                    var backdrop = dlg.closest('.overlay, [data-animate-modal-backdrop="true"]') || dlg.parentElement;
                                    dlg.style.display = 'none';
                                    if (backdrop && backdrop !== document.body && backdrop.id !== 'app') {
                                        backdrop.style.display = 'none';
                                    }
                                    var okBtns = dlg.querySelectorAll('button, [role="button"]');
                                    for (var b = 0; b < okBtns.length; b++) {
                                        try { okBtns[b].click(); } catch(e) {}
                                    }
                                    var contact = window.__xenoExtractActiveChatContact();
                                    window.__xenoStartWhatsAppCallOverlay(contact.name, contact.avatar, Boolean(contact.isVideo));
                                    return true;
                                }
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
                    if (!existingStyle) {
                        var st = document.createElement('style');
                        st.id = styleId;
                        st.textContent =
                            /* Root & App Container Unlocking */
                            'html.xeno-wa-phone-ui, html.xeno-wa-phone-ui body { width: 100vw !important; max-width: 100vw !important; min-width: 0 !important; height: 100% !important; min-height: 0 !important; margin: 0 !important; padding: 0 !important; overflow: hidden !important; -webkit-text-size-adjust: 100% !important; touch-action: manipulation !important; } ' +
                            'html.xeno-wa-phone-ui #app, html.xeno-wa-phone-ui .app-wrapper-web, html.xeno-wa-phone-ui [data-xeno-wa-anc="1"] { width: 100vw !important; max-width: 100vw !important; min-width: 0 !important; height: 100% !important; min-height: 0 !important; margin: 0 !important; padding: 0 !important; top: 0 !important; left: 0 !important; border-radius: 0 !important; box-shadow: none !important; overflow: hidden !important; box-sizing: border-box !important; } ' +
                            'html.xeno-wa-phone-ui .app-wrapper-web::after, html.xeno-wa-phone-ui .app-wrapper-web::before { display: none !important; } ' +

                            /* Landing / QR Code / Link with Phone Number Screen Adaptation */
                            'html.xeno-wa-phone-ui.xeno-wa-landing-mode body, html.xeno-wa-phone-ui.xeno-wa-landing-mode #app { overflow-y: auto !important; overflow-x: hidden !important; height: 100% !important; -webkit-overflow-scrolling: touch !important; } ' +
                            'html.xeno-wa-phone-ui.xeno-wa-landing-mode .landing-wrapper, html.xeno-wa-phone-ui.xeno-wa-landing-mode .landing-window, html.xeno-wa-phone-ui.xeno-wa-landing-mode .landing-main, html.xeno-wa-phone-ui.xeno-wa-landing-mode [data-xeno-wa-landing-anc="1"] { min-width: 0 !important; width: 100% !important; max-width: 100vw !important; height: auto !important; min-height: 0 !important; margin: 0 !important; padding: 10px !important; box-sizing: border-box !important; overflow-x: hidden !important; } ' +
                            'html.xeno-wa-phone-ui.xeno-wa-landing-mode .landing-header { min-width: 0 !important; width: 100% !important; max-width: 100vw !important; padding: 10px 14px !important; margin: 0 !important; height: auto !important; box-sizing: border-box !important; } ' +
                            'html.xeno-wa-phone-ui.xeno-wa-landing-mode [data-xeno-wa-landing-split="1"] { display: flex !important; flex-direction: column-reverse !important; align-items: center !important; justify-content: flex-start !important; gap: 16px !important; width: 100% !important; max-width: 100% !important; min-width: 0 !important; padding: 8px 4px !important; box-sizing: border-box !important; } ' +
                            'html.xeno-wa-phone-ui.xeno-wa-landing-mode [data-xeno-wa-landing-split="1"] > * { width: 100% !important; max-width: 100% !important; min-width: 0 !important; margin: 0 !important; box-sizing: border-box !important; } ' +
                            'html.xeno-wa-phone-ui.xeno-wa-landing-mode canvas { display: block !important; margin: 6px auto !important; max-width: min(240px, 75vw) !important; max-height: min(240px, 75vw) !important; } ' +

                            /* Left Navigation Rail (Chats, Status, Channels, Communities, Settings, Profile) */
                            'html.xeno-wa-phone-ui [data-xeno-wa-role="nav-rail"] { flex: 0 0 48px !important; width: 48px !important; max-width: 48px !important; min-width: 48px !important; height: 100% !important; z-index: 25 !important; box-sizing: border-box !important; } ' +

                            /* Chat List & Left Drawers Column (#side + Profile/Settings/Status/Channels/New Chat drawers) */
                            'html.xeno-wa-phone-ui [data-xeno-wa-role="side-col"] { flex: 1 1 auto !important; width: 100% !important; max-width: 100vw !important; min-width: 0 !important; height: 100% !important; position: relative !important; z-index: 20 !important; overflow: hidden !important; box-sizing: border-box !important; } ' +
                            'html.xeno-wa-phone-ui.xeno-wa-has-nav-rail [data-xeno-wa-role="side-col"] { width: calc(100vw - 48px) !important; max-width: calc(100vw - 48px) !important; } ' +
                            'html.xeno-wa-phone-ui [data-xeno-wa-role="side-col"] > *, html.xeno-wa-phone-ui #side { width: 100% !important; max-width: 100% !important; min-width: 0 !important; box-sizing: border-box !important; } ' +

                            /* Active Chat Conversation Column (#main) - Full-Screen Phone Slide Over */
                            'html.xeno-wa-phone-ui [data-xeno-wa-role="main-col"] { position: fixed !important; top: 0 !important; left: 0 !important; width: 100vw !important; max-width: 100vw !important; min-width: 0 !important; height: 100% !important; margin: 0 !important; padding: 0 !important; box-sizing: border-box !important; transition: transform 0.2s cubic-bezier(0.22, 1, 0.36, 1), opacity 0.16s ease !important; transform: translateX(104vw) !important; opacity: 0 !important; pointer-events: none !important; z-index: 10 !important; } ' +
                            'html.xeno-wa-phone-ui.xeno-wa-chat-open [data-xeno-wa-role="main-col"] { transform: translateX(0) !important; opacity: 1 !important; pointer-events: auto !important; z-index: 45 !important; } ' +
                            'html.xeno-wa-phone-ui #main { width: 100vw !important; max-width: 100vw !important; min-width: 0 !important; height: 100% !important; box-sizing: border-box !important; } ' +

                            /* Right Drawer Column (Contact Info, Group Info, Search Messages) */
                            'html.xeno-wa-phone-ui [data-xeno-wa-role="right-drawer"] { position: fixed !important; top: 0 !important; left: 0 !important; width: 100vw !important; max-width: 100vw !important; min-width: 0 !important; height: 100% !important; transform: translateX(104vw) !important; opacity: 0 !important; pointer-events: none !important; z-index: 10 !important; transition: transform 0.2s ease, opacity 0.16s ease !important; box-sizing: border-box !important; } ' +
                            'html.xeno-wa-phone-ui.xeno-wa-right-drawer-open [data-xeno-wa-role="right-drawer"] { transform: translateX(0) !important; opacity: 1 !important; pointer-events: auto !important; z-index: 60 !important; background: #111b21 !important; } ' +
                            'html.xeno-wa-phone-ui [data-xeno-wa-role="right-drawer"] > * { width: 100% !important; max-width: 100vw !important; min-width: 0 !important; box-sizing: border-box !important; } ' +

                            /* Mobile Chat Header, Bubbles & Compose Footer Refinements */
                            'html.xeno-wa-phone-ui #main > header { padding-left: 4px !important; padding-right: 6px !important; width: 100% !important; max-width: 100vw !important; min-width: 0 !important; box-sizing: border-box !important; } ' +
                            'html.xeno-wa-phone-ui #xeno-wa-back-to-chats-btn { display: inline-flex !important; align-items: center !important; justify-content: center !important; width: 36px !important; height: 36px !important; min-width: 36px !important; border-radius: 50% !important; border: none !important; background: transparent !important; color: inherit !important; cursor: pointer !important; margin-right: 2px !important; padding: 0 !important; flex-shrink: 0 !important; -webkit-tap-highlight-color: transparent !important; } ' +
                            'html.xeno-wa-phone-ui #xeno-wa-back-to-chats-btn:active { background: rgba(134, 150, 160, 0.2) !important; } ' +
                            'html.xeno-wa-phone-ui #main [role="application"], html.xeno-wa-phone-ui #main [data-testid="conversation-panel-messages"] > div { padding-left: 10px !important; padding-right: 10px !important; box-sizing: border-box !important; } ' +
                            'html.xeno-wa-phone-ui #main .message-in, html.xeno-wa-phone-ui #main .message-out { max-width: 86% !important; } ' +
                            'html.xeno-wa-phone-ui #main footer { width: 100% !important; max-width: 100vw !important; min-width: 0 !important; padding-left: 4px !important; padding-right: 4px !important; box-sizing: border-box !important; } ' +
                            'html.xeno-wa-phone-ui [role="dialog"] { max-width: calc(100vw - 16px) !important; min-width: 0 !important; box-sizing: border-box !important; }';
                        document.head.appendChild(st);
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
                        var targetContent = 'width=device-width, initial-scale=1.0, minimum-scale=1.0, maximum-scale=3.0, user-scalable=yes, viewport-fit=cover';
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

                        if (sideCol) {
                            sideCol.setAttribute('data-xeno-wa-role', 'side-col');
                        }

                        // Walk ancestors from mainGrid up to #app and tag with data-xeno-wa-anc="1"
                        var anc = mainGrid;
                        while (anc && anc !== appEl && anc !== document.body) {
                            anc.setAttribute('data-xeno-wa-anc', '1');
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
                                    child.setAttribute('data-xeno-wa-role', 'nav-rail');
                                    hasNavRail = true;
                                } else if (!mainColFound && (child.contains(mainEl) || !mainEl)) {
                                    child.setAttribute('data-xeno-wa-role', 'main-col');
                                    mainColFound = child;
                                } else {
                                    child.setAttribute('data-xeno-wa-role', 'right-drawer');
                                }
                            }

                            // Also check if navRail is a sibling of mainGrid inside mainGrid.parentElement
                            if (!hasNavRail && mainGrid.parentElement && mainGrid.parentElement !== appEl) {
                                var outerChildren = mainGrid.parentElement.children;
                                for (var oc = 0; oc < outerChildren.length; oc++) {
                                    var oChild = outerChildren[oc];
                                    if (oChild === mainGrid) break;
                                    if (oChild && oChild.nodeType === 1 && !oChild.contains(sideCol)) {
                                        oChild.setAttribute('data-xeno-wa-role', 'nav-rail');
                                        hasNavRail = true;
                                    }
                                }
                            }

                            // Also check if right-drawer is mounted inside mainColFound next to #main
                            if (mainColFound && mainEl && mainColFound.children.length > 1) {
                                for (var mc = 0; mc < mainColFound.children.length; mc++) {
                                    var mChild = mainColFound.children[mc];
                                    if (mChild && mChild !== mainEl && !mChild.contains(mainEl) && mChild.querySelector('header, section, [role="button"]')) {
                                        mChild.setAttribute('data-xeno-wa-role', 'right-drawer');
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

                        // Sync Active Chat (#main) Open/Close state & inject Mobile Back Button in #main header
                        if (mainEl) {
                            var currentSig = window.__xenoGetMainChatSignature();
                            if (currentSig && currentSig !== window.__xenoLastKnownChatSignature) {
                                window.__xenoLastKnownChatSignature = currentSig;
                                if (currentSig !== window.__xenoDismissedChatSignature) {
                                    document.documentElement.classList.add('xeno-wa-chat-open');
                                }
                            } else if (!document.documentElement.classList.contains('xeno-wa-chat-open') && !window.__xenoDismissedChatSignature) {
                                document.documentElement.classList.add('xeno-wa-chat-open');
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
                            window.__xenoLastKnownChatSignature = '';
                            window.__xenoDismissedChatSignature = null;
                            document.documentElement.classList.remove('xeno-wa-chat-open', 'xeno-wa-right-drawer-open');
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
                // 1. Passive touch & wheel listener optimization for 60fps+ scroll responsiveness
                if (!window.__xenoIgPassivePatched) {
                    window.__xenoIgPassivePatched = true;
                    var origAddEventListener = EventTarget.prototype.addEventListener;
                    EventTarget.prototype.addEventListener = function(type, listener, options) {
                        if (type === 'touchstart' || type === 'touchmove' || type === 'wheel' || type === 'mousewheel') {
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

                // 2. Smooth Scroll, GPU Layer Promotion & Reels Snap CSS
                var styleId = 'xeno-ig-reels-smooth-style';
                if (!document.getElementById(styleId) && document.head) {
                    var st = document.createElement('style');
                    st.id = styleId;
                    st.textContent =
                        'html, body { -webkit-overflow-scrolling: touch !important; overscroll-behavior-y: contain !important; overflow-x: hidden !important; touch-action: pan-y manipulation !important; -webkit-tap-highlight-color: transparent !important; } ' +
                        'main, section, article, div[role="main"], div[style*="scroll-snap"] { -webkit-overflow-scrolling: touch !important; } ' +
                        'div[style*="scroll-snap-type"] > div, div[class*=" reel"], article { scroll-snap-stop: always !important; } ' +
                        'video { transform: translateZ(0) !important; -webkit-transform: translateZ(0) !important; backface-visibility: hidden !important; -webkit-backface-visibility: hidden !important; will-change: transform !important; object-fit: cover; } ' +
                        '::-webkit-scrollbar { width: 0px !important; height: 0px !important; display: none !important; }';
                    document.head.appendChild(st);
                }

                // 3. Reels Video Inline Playback, Preload & Viewport Intersection Optimizer
                if (!window.__xenoIgVideoHelper) {
                    window.__xenoIgVideoHelper = {
                        userPausedMap: new WeakMap(),
                        observedVideos: new WeakSet(),
                        observer: null
                    };

                    if ('IntersectionObserver' in window) {
                        window.__xenoIgVideoHelper.observer = new IntersectionObserver(function(entries) {
                            entries.forEach(function(entry) {
                                var vid = entry.target;
                                if (!vid || vid.tagName !== 'VIDEO') return;
                                if (entry.isIntersecting && entry.intersectionRatio >= 0.6) {
                                    vid.preload = 'auto';
                                    // Pre-warm next video in DOM for zero-lag Reel swipe
                                    try {
                                        var allVids = Array.prototype.slice.call(document.querySelectorAll('video'));
                                        var idx = allVids.indexOf(vid);
                                        if (idx >= 0 && idx + 1 < allVids.length) {
                                            allVids[idx + 1].preload = 'auto';
                                        }
                                    } catch(e) {}

                                    if (vid.paused && !window.__xenoIgVideoHelper.userPausedMap.get(vid)) {
                                        var playPromise = vid.play();
                                        if (playPromise && typeof playPromise.catch === 'function') {
                                            playPromise.catch(function() {
                                                // If autoplay with sound is blocked before first interaction, play muted smoothly
                                                if (!vid.muted) {
                                                    vid.muted = true;
                                                    vid.play().catch(function(){});
                                                }
                                            });
                                        }
                                    }
                                } else if (!entry.isIntersecting || entry.intersectionRatio < 0.15) {
                                    // Pause off-screen Reels so background decoders don't cause scroll frame drops
                                    if (!vid.paused) {
                                        try { vid.pause(); } catch(e) {}
                                    }
                                }
                            });
                        }, { threshold: [0.0, 0.15, 0.6, 0.85] });
                    }
                }

                function optimizeInstagramVideos() {
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
                            // Track manual tap-to-pause vs scroll-away pause
                            v.addEventListener('click', function() {
                                var self = this;
                                setTimeout(function() {
                                    window.__xenoIgVideoHelper.userPausedMap.set(self, self.paused);
                                }, 80);
                            }, { passive: true });

                            // Recover smoothly from mid-reel buffer stalls
                            v.addEventListener('stalled', function() {
                                var self = this;
                                if (!self.paused && self.readyState >= 2) {
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
                        }, 180);
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
 * Non-intrusive YouTube Normal Video Watch, YouTube Shorts & Smooth Scroll Helper Engine:
 * - Preserves YouTube's native touch gesture listeners (`touchstart`/`touchmove`) so YouTube Shorts
 *   vertical swiping, video player seekbar scrubbing, and SPA video card navigation work natively.
 * - Dynamically adapts page layout & scroll state across 3 modes (`home`, `watch`, `shorts`) via
 *   `data-xeno-yt-mode` and SPA History API hooks (`pushState`/`replaceState`/`popstate`).
 * - In `watch` mode (`/watch?v=...`), guarantees `#player-container-id`, `#player`, `#movie_player`,
 *   `<video class="html5-main-video">`, and `<ytm-watch>` are visible, untransformed, properly sized
 *   at 16:9 at the top of the screen, and actively playing while keeping related videos/comments scrollable below.
 * - In `shorts` mode (`/shorts/...`), clears all document scroll-lock overrides so `<ytm-shorts>` and
 *   `#shorts-container` maintain their full-viewport vertical swipe carousel and auto-plays each Short on swipe.
 */
private fun injectYouTubeAndShortsSmoothHelper(view: WebView?) {
    val js = """
        (function() {
            try {
                // 1. Browser Environment, Audio Unlock & Instant SPA Route Detection
                if (!window.__xenoYtEnvPatched) {
                    window.__xenoYtEnvPatched = true;
                    try {
                        Object.defineProperty(navigator, 'webdriver', { get: function() { return false; }, configurable: true });
                        Object.defineProperty(document, 'hidden', { get: function() { return false; }, configurable: true });
                        Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });
                        if (!window.chrome) {
                            window.chrome = { runtime: {} };
                        }
                    } catch(e) {}

                    // Only make wheel/mousewheel passive — NEVER force touchstart/touchmove passive so Shorts swipe & player scrubbar work natively
                    var origAddEventListener = EventTarget.prototype.addEventListener;
                    EventTarget.prototype.addEventListener = function(type, listener, options) {
                        if (type === 'wheel' || type === 'mousewheel') {
                            var newOpts = { passive: true, capture: false };
                            if (typeof options === 'boolean') {
                                newOpts = { capture: options, passive: true };
                            } else if (typeof options === 'object' && options !== null) {
                                newOpts = Object.assign({}, options, { passive: true });
                            }
                            return origAddEventListener.call(this, type, listener, newOpts);
                        }
                        return origAddEventListener.call(this, type, listener, options);
                    };

                    // Unlock YouTube & Shorts audio on first user interaction
                    window.__xenoYtAudioUnlocked = false;
                    var unlockYtAudio = function() {
                        window.__xenoYtAudioUnlocked = true;
                        var vids = document.querySelectorAll('video');
                        for (var i = 0; i < vids.length; i++) {
                            var v = vids[i];
                            if (v.muted) {
                                v.muted = false;
                                v.volume = 1.0;
                            }
                        }
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

                    // Hook SPA History navigation so transitions between Home, Watch, and Shorts update layout & start playback immediately
                    var origPushState = history.pushState;
                    var origReplaceState = history.replaceState;
                    history.pushState = function() {
                        var ret = origPushState.apply(this, arguments);
                        if (window.__xenoRunYtOptimizer) {
                            setTimeout(window.__xenoRunYtOptimizer, 20);
                            setTimeout(window.__xenoRunYtOptimizer, 250);
                            setTimeout(window.__xenoRunYtOptimizer, 700);
                        }
                        return ret;
                    };
                    history.replaceState = function() {
                        var ret = origReplaceState.apply(this, arguments);
                        if (window.__xenoRunYtOptimizer) {
                            setTimeout(window.__xenoRunYtOptimizer, 20);
                            setTimeout(window.__xenoRunYtOptimizer, 250);
                        }
                        return ret;
                    };
                    window.addEventListener('popstate', function() {
                        if (window.__xenoRunYtOptimizer) {
                            setTimeout(window.__xenoRunYtOptimizer, 20);
                            setTimeout(window.__xenoRunYtOptimizer, 250);
                        }
                    });
                }

                // 2. Mode-Aware CSS for Home Feed, Normal Video Watch Page (/watch), and YouTube Shorts (/shorts)
                var styleId = 'xeno-yt-shorts-smooth-style';
                var styleEl = document.getElementById(styleId);
                if (!styleEl && (document.head || document.documentElement)) {
                    styleEl = document.createElement('style');
                    styleEl.id = styleId;
                    (document.head || document.documentElement).appendChild(styleEl);
                }
                if (styleEl && !styleEl.__xenoUpdatedV2) {
                    styleEl.__xenoUpdatedV2 = true;
                    styleEl.textContent =
                        /* Home & Watch: Smooth vertical page scrolling */
                        'html:not([data-xeno-yt-mode="shorts"]), body:not([data-xeno-yt-mode="shorts"]) { ' +
                        '  overflow-y: auto !important; overflow-x: hidden !important; ' +
                        '  position: static !important; height: auto !important; min-height: 100% !important; ' +
                        '  -webkit-overflow-scrolling: touch !important; overscroll-behavior-y: auto !important; ' +
                        '  -webkit-tap-highlight-color: transparent !important; ' +
                        '} ' +
                        'html:not([data-xeno-yt-mode="shorts"]) ytm-app, ' +
                        'html:not([data-xeno-yt-mode="shorts"]) #app, ' +
                        'html:not([data-xeno-yt-mode="shorts"]) .page-container, ' +
                        'html:not([data-xeno-yt-mode="shorts"]) ytm-browse, ' +
                        'html:not([data-xeno-yt-mode="shorts"]) ytm-search, ' +
                        'html:not([data-xeno-yt-mode="shorts"]) ytm-single-column-browse-results-renderer, ' +
                        'html:not([data-xeno-yt-mode="shorts"]) ytm-single-column-watch-next-results-renderer, ' +
                        'html:not([data-xeno-yt-mode="shorts"]) ytm-rich-grid-renderer, ' +
                        'html:not([data-xeno-yt-mode="shorts"]) ytm-section-list-renderer, ' +
                        'html:not([data-xeno-yt-mode="shorts"]) ytm-item-section-renderer { ' +
                        '  overflow-y: visible !important; -webkit-overflow-scrolling: touch !important; ' +
                        '} ' +
                        /* Watch Mode (/watch?v=...): Guarantee 16:9 Top Video Player & Watch Page are visible and unobstructed */
                        'html[data-xeno-yt-mode="watch"] ytm-browse, ' +
                        'html[data-xeno-yt-mode="watch"] ytm-shorts { ' +
                        '  display: none !important; ' +
                        '} ' +
                        'html[data-xeno-yt-mode="watch"] ytm-watch { ' +
                        '  display: block !important; visibility: visible !important; opacity: 1 !important; ' +
                        '} ' +
                        'html[data-xeno-yt-mode="watch"] #player-container-id { ' +
                        '  display: block !important; visibility: visible !important; opacity: 1 !important; ' +
                        '  pointer-events: auto !important; transform: none !important; -webkit-transform: none !important; ' +
                        '  width: 100% !important; min-height: 56.25vw !important; z-index: 30 !important; ' +
                        '  background-color: #000 !important; ' +
                        '} ' +
                        'html[data-xeno-yt-mode="watch"] #player-container-id #player, ' +
                        'html[data-xeno-yt-mode="watch"] #player-container-id .player-size { ' +
                        '  display: block !important; visibility: visible !important; opacity: 1 !important; ' +
                        '  width: 100% !important; ' +
                        '} ' +
                        'html[data-xeno-yt-mode="watch"] #player-container-id #movie_player, ' +
                        'html[data-xeno-yt-mode="watch"] #player-container-id .html5-video-player, ' +
                        'html[data-xeno-yt-mode="watch"] #player-container-id .html5-video-container { ' +
                        '  display: block !important; visibility: visible !important; opacity: 1 !important; ' +
                        '  width: 100% !important; height: 100% !important; ' +
                        '} ' +
                        'html[data-xeno-yt-mode="watch"] #player-container-id video, ' +
                        'html[data-xeno-yt-mode="watch"] #movie_player video { ' +
                        '  display: block !important; visibility: visible !important; opacity: 1 !important; ' +
                        '  width: 100% !important; height: 100% !important; left: 0 !important; top: 0 !important; ' +
                        '  object-fit: contain !important; background-color: #000 !important; ' +
                        '} ' +
                        'html[data-xeno-yt-mode="watch"] ytm-watch .player-placeholder { ' +
                        '  display: block !important; width: 100% !important; min-height: 56.25vw !important; ' +
                        '} ' +
                        /* Shorts Mode (/shorts/...): Full-viewport vertical swipe layout & visible video player */
                        'html[data-xeno-yt-mode="shorts"], body[data-xeno-yt-mode="shorts"] { ' +
                        '  height: 100% !important; overflow: hidden !important; background-color: #000 !important; ' +
                        '  overscroll-behavior-y: none !important; ' +
                        '} ' +
                        'html[data-xeno-yt-mode="shorts"] ytm-browse, ' +
                        'html[data-xeno-yt-mode="shorts"] ytm-watch { ' +
                        '  display: none !important; ' +
                        '} ' +
                        'html[data-xeno-yt-mode="shorts"] ytm-shorts, ' +
                        'html[data-xeno-yt-mode="shorts"] #shorts-container, ' +
                        'html[data-xeno-yt-mode="shorts"] .reel-video-in-sequence, ' +
                        'html[data-xeno-yt-mode="shorts"] ytm-reel-video-renderer { ' +
                        '  visibility: visible !important; opacity: 1 !important; ' +
                        '} ' +
                        'html[data-xeno-yt-mode="shorts"] #player-container-id, ' +
                        'html[data-xeno-yt-mode="shorts"] #shorts-player, ' +
                        'html[data-xeno-yt-mode="shorts"] #movie_player, ' +
                        'html[data-xeno-yt-mode="shorts"] .html5-video-player, ' +
                        'html[data-xeno-yt-mode="shorts"] .html5-video-container { ' +
                        '  visibility: visible !important; opacity: 1 !important; ' +
                        '} ' +
                        'html[data-xeno-yt-mode="shorts"] video { ' +
                        '  display: block !important; visibility: visible !important; opacity: 1 !important; ' +
                        '  width: 100% !important; height: 100% !important; object-fit: cover !important; ' +
                        '} ' +
                        /* Suppress only genuine app-upsell banners without touching player overlays */
                        'ytm-mealbar-promo-renderer, ytm-app-banner, .upsell-dialog-renderer, .mealbar-promo-renderer, ' +
                        'ytm-statement-banner-renderer, .upsell-dialog-backdrop { ' +
                        '  display: none !important; pointer-events: none !important; ' +
                        '}';
                }

                // 3. Mode Detection, Player Visibility Recovery & Playback Controller
                function getYouTubePageMode() {
                    var path = (window.location.pathname || '').toLowerCase();
                    var search = (window.location.search || '').toLowerCase();
                    if (path.indexOf('/shorts') === 0 || path.indexOf('/shorts/') !== -1) {
                        return 'shorts';
                    }
                    if (path.indexOf('/watch') === 0 || path.indexOf('/watch') !== -1 || (search.indexOf('v=') !== -1 && path.indexOf('/results') === -1)) {
                        return 'watch';
                    }
                    return 'home';
                }

                function getActiveMediaId(mode) {
                    try {
                        if (mode === 'watch') {
                            var params = new URLSearchParams(window.location.search);
                            return params.get('v') || window.location.href;
                        }
                        if (mode === 'shorts') {
                            var parts = (window.location.pathname || '').split('/shorts/');
                            if (parts.length > 1 && parts[1]) {
                                return 'short_' + parts[1].split('/')[0].split('?')[0];
                            }
                            return 'shorts_feed';
                        }
                    } catch(e) {}
                    return '';
                }

                function optimizeYouTubeAndShorts() {
                    var mode = getYouTubePageMode();
                    var mediaId = getActiveMediaId(mode);

                    if (document.documentElement && document.body) {
                        if (document.documentElement.getAttribute('data-xeno-yt-mode') !== mode) {
                            document.documentElement.setAttribute('data-xeno-yt-mode', mode);
                        }
                        if (document.body.getAttribute('data-xeno-yt-mode') !== mode) {
                            document.body.setAttribute('data-xeno-yt-mode', mode);
                        }
                        document.documentElement.removeAttribute('data-xeno-shorts');
                        document.body.removeAttribute('data-xeno-shorts');

                        if (mode === 'shorts') {
                            // Clear all inline scroll-unlock properties so Shorts vertical swipe pager works natively
                            document.documentElement.style.removeProperty('overflow-y');
                            document.documentElement.style.removeProperty('overflow');
                            document.documentElement.style.removeProperty('position');
                            document.documentElement.style.removeProperty('height');
                            document.documentElement.style.removeProperty('min-height');
                            document.documentElement.style.removeProperty('touch-action');

                            document.body.style.removeProperty('overflow-y');
                            document.body.style.removeProperty('overflow');
                            document.body.style.removeProperty('position');
                            document.body.style.removeProperty('height');
                            document.body.style.removeProperty('min-height');
                            document.body.style.removeProperty('top');
                            document.body.style.removeProperty('touch-action');
                        } else {
                            // Unlock scroll on Home, Search & Watch pages
                            document.documentElement.style.setProperty('overflow-y', 'auto', 'important');
                            document.documentElement.style.setProperty('position', 'static', 'important');
                            document.documentElement.style.setProperty('height', 'auto', 'important');
                            document.documentElement.style.removeProperty('touch-action');

                            document.body.style.setProperty('overflow-y', 'auto', 'important');
                            document.body.style.setProperty('position', 'static', 'important');
                            document.body.style.setProperty('height', 'auto', 'important');
                            document.body.style.setProperty('top', 'auto', 'important');
                            document.body.style.removeProperty('touch-action');
                            document.body.classList.remove('no-scroll', 'modal-open', 'lock-scroll');
                            document.body.removeAttribute('scroll-locked');
                        }
                    }

                    // Track media ID transitions (new normal video or newly swiped Short)
                    var isNewMedia = false;
                    if (mediaId && window.__xenoYtCurrentMediaId !== mediaId) {
                        window.__xenoYtCurrentMediaId = mediaId;
                        window.__xenoYtMediaEnteredAt = Date.now();
                        window.__xenoYtUserPausedId = null;
                        isNewMedia = true;
                    }

                    // Dismiss ONLY genuine app-install promo banners (never click player Close or c3-overlay buttons!)
                    var promoBtns = document.querySelectorAll(
                        'ytm-mealbar-promo-renderer button[aria-label*="Dismiss"], ' +
                        'ytm-mealbar-promo-renderer .mealbar-promo-renderer-dismiss-button button, ' +
                        'ytm-mealbar-promo-renderer .mealbar-promo-renderer-dismiss-button, ' +
                        '.upsell-dialog-dismiss-button button'
                    );
                    for (var d = 0; d < promoBtns.length; d++) {
                        try { promoBtns[d].click(); } catch(e) {}
                    }

                    // Ensure Watch & Shorts containers and singleton player are never hidden
                    if (mode === 'watch') {
                        var ytmWatch = document.querySelector('ytm-watch');
                        if (ytmWatch) {
                            ytmWatch.removeAttribute('hidden');
                        }
                        var pc = document.getElementById('player-container-id');
                        if (pc) {
                            pc.removeAttribute('hidden');
                            pc.setAttribute('aria-hidden', 'false');
                        }
                    } else if (mode === 'shorts') {
                        var ytmShorts = document.querySelector('ytm-shorts');
                        if (ytmShorts) {
                            ytmShorts.removeAttribute('hidden');
                        }
                        var pcShorts = document.getElementById('player-container-id');
                        if (pcShorts) {
                            pcShorts.removeAttribute('hidden');
                            pcShorts.setAttribute('aria-hidden', 'false');
                        }
                    }

                    // Drive YouTube HTML5 Player API (#movie_player / #shorts-player) & <video> elements
                    if (mode === 'watch' || mode === 'shorts') {
                        var elapsedSinceEnter = Date.now() - (window.__xenoYtMediaEnteredAt || 0);
                        var shouldAutoStart = isNewMedia || (elapsedSinceEnter < 3000 && window.__xenoYtUserPausedId !== mediaId);

                        try {
                            var playerEl = (mode === 'shorts' ? document.getElementById('shorts-player') : null) ||
                                           document.getElementById('movie_player') ||
                                           document.querySelector('.html5-video-player');
                            if (playerEl) {
                                // On Watch page, if movie_player has loadVideoById and hasn't loaded the requested video ID after SPA nav, load it
                                if (mode === 'watch' && mediaId && mediaId.length === 11 && typeof playerEl.getVideoData === 'function') {
                                    var vData = playerEl.getVideoData();
                                    var loadedId = vData && vData.video_id ? vData.video_id : '';
                                    var pState = typeof playerEl.getPlayerState === 'function' ? playerEl.getPlayerState() : 1;
                                    if (loadedId && loadedId !== mediaId && elapsedSinceEnter > 400 && typeof playerEl.loadVideoById === 'function') {
                                        playerEl.loadVideoById(mediaId);
                                    } else if ((pState === -1 || pState === 5) && shouldAutoStart && typeof playerEl.playVideo === 'function') {
                                        playerEl.playVideo();
                                    }
                                } else if (shouldAutoStart && typeof playerEl.playVideo === 'function') {
                                    var st = typeof playerEl.getPlayerState === 'function' ? playerEl.getPlayerState() : -1;
                                    if (st === -1 || st === 2 || st === 5) {
                                        playerEl.playVideo();
                                    }
                                }
                                if (window.__xenoYtAudioUnlocked && typeof playerEl.isMuted === 'function' && playerEl.isMuted()) {
                                    if (typeof playerEl.unMute === 'function') playerEl.unMute();
                                    if (typeof playerEl.setVolume === 'function') playerEl.setVolume(100);
                                }
                            }
                        } catch(e) {}

                        var videos = document.querySelectorAll('video');
                        for (var i = 0; i < videos.length; i++) {
                            var v = videos[i];
                            if (!v.hasAttribute('playsinline')) v.setAttribute('playsinline', 'true');
                            if (!v.hasAttribute('webkit-playsinline')) v.setAttribute('webkit-playsinline', 'true');
                            v.playsInline = true;
                            if (!v.preload || v.preload === 'none') {
                                v.preload = 'auto';
                            }

                            if (!v.__xenoYtObservedV2) {
                                v.__xenoYtObservedV2 = true;
                                v.addEventListener('loadedmetadata', function() {
                                    var self = this;
                                    self.playsInline = true;
                                    if (window.__xenoYtAudioUnlocked && self.muted) {
                                        self.muted = false;
                                        self.volume = 1.0;
                                    }
                                    var curMode = getYouTubePageMode();
                                    var curId = getActiveMediaId(curMode);
                                    if ((curMode === 'watch' || curMode === 'shorts') && self.paused && window.__xenoYtUserPausedId !== curId) {
                                        self.play().catch(function() {
                                            self.muted = true;
                                            self.play().catch(function(){});
                                        });
                                    }
                                }, { passive: true });

                                v.addEventListener('playing', function() {
                                    var self = this;
                                    if (window.__xenoYtAudioUnlocked && self.muted) {
                                        self.muted = false;
                                        self.volume = 1.0;
                                    }
                                }, { passive: true });

                                // Loop Shorts seamlessly if ended
                                v.addEventListener('ended', function() {
                                    var self = this;
                                    if (getYouTubePageMode() === 'shorts') {
                                        self.currentTime = 0;
                                        self.play().catch(function(){});
                                    }
                                }, { passive: true });
                            }

                            if (shouldAutoStart && v.paused && (v.readyState >= 1 || v.currentSrc || v.src)) {
                                var playPromise = v.play();
                                if (playPromise && typeof playPromise.catch === 'function') {
                                    playPromise.catch(function() {
                                        if (!v.muted) {
                                            v.muted = true;
                                            v.play().catch(function(){});
                                        }
                                    });
                                }
                            }
                        }
                    }

                    // 4. Desktop-Style Offline Download Extractor & In-Page Download Pill
                    var pathLower = (window.location.pathname || '').toLowerCase();
                    if (pathLower.indexOf('/feed/downloads') === 0 && window.XenoYtBridge && typeof window.XenoYtBridge.openOfflineDownloads === 'function') {
                        window.XenoYtBridge.openOfflineDownloads();
                    }

                    function extractActiveYouTubeMetadata() {
                        var m = getYouTubePageMode();
                        var rawId = '';
                        if (m === 'watch') {
                            try {
                                rawId = (new URLSearchParams(window.location.search)).get('v') || '';
                            } catch(e) {}
                        } else if (m === 'shorts') {
                            var sp = (window.location.pathname || '').split('/shorts/');
                            if (sp.length > 1 && sp[1]) {
                                rawId = sp[1].split('/')[0].split('?')[0];
                            }
                        }

                        var pEl = (m === 'shorts' ? document.getElementById('shorts-player') : null) ||
                                  document.getElementById('movie_player') ||
                                  document.querySelector('.html5-video-player');
                        var pr = null;
                        try {
                            if (pEl && typeof pEl.getPlayerResponse === 'function') {
                                pr = pEl.getPlayerResponse();
                            }
                        } catch(e) {}
                        if (!pr && window.ytInitialPlayerResponse) {
                            pr = window.ytInitialPlayerResponse;
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
                                '.reel-player-header-renderer .reel-player-header-title, ' +
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
                                '.slim-owner-channel-name, ' +
                                '.reel-player-header-channel-title'
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
                        try {
                            if (pr && pr.streamingData && (!vDetails || vDetails.videoId === rawId)) {
                                var fmts = pr.streamingData.formats || [];
                                for (var f = 0; f < fmts.length; f++) {
                                    if (fmts[f] && fmts[f].url && fmts[f].url.indexOf('http') === 0) {
                                        directStream = fmts[f].url;
                                        if (fmts[f].itag === 22 || fmts[f].itag === 18) break;
                                    }
                                }
                            }
                        } catch(e) {}
                        if (!directStream) {
                            var domVid = document.querySelector('video');
                            if (domVid) {
                                var src = domVid.currentSrc || domVid.src || '';
                                if (src.indexOf('http://') === 0 || src.indexOf('https://') === 0) {
                                    directStream = src;
                                }
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
                        var meta = extractActiveYouTubeMetadata();
                        if (!meta.videoId) {
                            return false;
                        }
                        if (window.XenoYtBridge && typeof window.XenoYtBridge.downloadVideo === 'function') {
                            window.XenoYtBridge.downloadVideo(
                                meta.videoId,
                                meta.title,
                                meta.author,
                                meta.durationSec,
                                meta.directStreamUrl
                            );
                            return true;
                        }
                        return false;
                    };

                    // Inject or update the sleek in-page Download Pill on Watch & Shorts pages
                    var pillId = 'xeno-yt-download-pill';
                    var existingPill = document.getElementById(pillId);
                    if (mode === 'watch' || mode === 'shorts') {
                        if (!existingPill && document.body) {
                            existingPill = document.createElement('button');
                            existingPill.id = pillId;
                            existingPill.type = 'button';
                            existingPill.style.cssText =
                                'position:fixed; right:14px; bottom:' + (mode === 'shorts' ? '150px' : '22px') + '; ' +
                                'z-index:2147483640; display:flex; align-items:center; gap:6px; ' +
                                'background:rgba(15,15,15,0.92); color:#ffffff; border:1px solid rgba(255,255,255,0.22); ' +
                                'border-radius:20px; padding:8px 14px; font-family:Roboto,sans-serif; font-size:12.5px; ' +
                                'font-weight:600; box-shadow:0 4px 14px rgba(0,0,0,0.55); cursor:pointer; ' +
                                '-webkit-tap-highlight-color:transparent;';
                            existingPill.addEventListener('click', function(ev) {
                                ev.stopPropagation();
                                ev.preventDefault();
                                var curMeta = extractActiveYouTubeMetadata();
                                if (curMeta.videoId && window.XenoYtBridge && typeof window.XenoYtBridge.isDownloaded === 'function' && window.XenoYtBridge.isDownloaded(curMeta.videoId)) {
                                    window.XenoYtBridge.openOfflineDownloads();
                                } else {
                                    window.__xenoTriggerYtDownload();
                                }
                            });
                            document.body.appendChild(existingPill);
                        }
                        if (existingPill) {
                            existingPill.style.bottom = (mode === 'shorts' ? '150px' : '22px');
                            existingPill.style.display = 'flex';
                            var curIdCheck = extractActiveYouTubeMetadata().videoId;
                            var prog = -1;
                            var isSaved = false;
                            try {
                                if (curIdCheck && window.XenoYtBridge) {
                                    prog = window.XenoYtBridge.getProgress(curIdCheck);
                                    isSaved = window.XenoYtBridge.isDownloaded(curIdCheck);
                                }
                            } catch(e) {}
                            if (prog >= 0) {
                                existingPill.textContent = '⬇ Downloading ' + prog + '%';
                                existingPill.style.borderColor = '#FF0000';
                            } else if (isSaved) {
                                existingPill.textContent = '✓ Downloaded (Offline)';
                                existingPill.style.borderColor = '#2BA640';
                            } else {
                                existingPill.textContent = '⬇ Download Offline';
                                existingPill.style.borderColor = 'rgba(255,255,255,0.22)';
                            }
                        }
                    } else if (existingPill) {
                        existingPill.style.display = 'none';
                    }
                }

                window.__xenoRunYtOptimizer = optimizeYouTubeAndShorts;
                optimizeYouTubeAndShorts();

                if (!window.__xenoYtMutationObserver && window.MutationObserver && document.body) {
                    var throttleTimer = null;
                    window.__xenoYtMutationObserver = new MutationObserver(function() {
                        if (throttleTimer) return;
                        throttleTimer = setTimeout(function() {
                            throttleTimer = null;
                            optimizeYouTubeAndShorts();
                        }, 200);
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
                val res = req.resources ?: emptyArray()
                val wantsAudio = res.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                val wantsVideo = res.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                if (wantsAudio || wantsVideo) {
                    isCallActive = true
                    isVideoCallActive = wantsVideo
                    audioFocusRequest = setWhatsAppCallAudioMode(context, active = true, speakerphoneOn = isSpeakerphoneOn)
                    webViewInstance?.keepScreenOn = true
                }
                req.grant(res)
            } catch (_: Exception) {}
            pendingWebPermissionRequest = null
        }

        if (isWhatsapp) {
            injectWhatsAppWebSupportEngine(webViewInstance, isWhatsAppPhoneUi)
        } else if (isDiscord) {
            injectDiscordStabilityAndLoginHelper(webViewInstance, instance.isDesktopMode)
        }
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
        if (isYoutube && showYoutubeOfflineLibrary && YouTubeOfflineRepository.isDeviceOnline(context)) {
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
            .background(MaterialTheme.colorScheme.surface)
            .testTag("clone_workspace_${instance.instanceId}")
    ) {
        // Workspace Top Bar - Sleek, Compact & Space-Efficient
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
                        // YouTube Download Current Video Button
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
                                            // Fallback: parse video ID from current URL if on /watch or /shorts
                                            val urlStr = wv.url ?: currentUrl
                                            val fallbackId = when {
                                                urlStr.contains("v=") -> urlStr.substringAfter("v=").substringBefore("&")
                                                urlStr.contains("/shorts/") -> urlStr.substringAfter("/shorts/").substringBefore("?").substringBefore("/")
                                                else -> ""
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
                                                    "Open any YouTube video or Short first to download it for offline viewing",
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

        // Loading Progress Indicator
        if (isLoading && loadProgress < 100) {
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
                                }
                                webViewInstance = existing
                                existing
                            } else {
                                WebView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    // Use hardware layer for WhatsApp Web and YouTube so WebRTC video/audio codecs and HTML5 MSE work at full capability
                                    if (useSoftwareLayer && !isYoutube && !isWhatsapp) {
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
                                if (isYoutube || isFacebook || isDiscord) {
                                    setOnTouchListener { v, event ->
                                        if (event.action == android.view.MotionEvent.ACTION_DOWN ||
                                            event.action == android.view.MotionEvent.ACTION_MOVE
                                        ) {
                                            v.parent?.requestDisallowInterceptTouchEvent(true)
                                        }
                                        false
                                    }
                                }
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
                                    loadWithOverviewMode = true
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
                                    setSupportMultipleWindows(false)
                                    allowFileAccess = true
                                    allowContentAccess = true
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        offscreenPreRaster = true
                                    }
                                    // Discord web client (/app & /login) requires a desktop Chrome User-Agent to serve the full React web app instead of the mobile download splash screen
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
                                    private fun injectSocialAppHelpers(view: WebView?, url: String?) {
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
                                            injectWhatsAppWebSupportEngine(view, isWhatsAppPhoneUi)
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
                        if (online) {
                            showYoutubeOfflineLibrary = false
                            webViewInstance?.reload()
                        } else {
                            showYoutubeOfflineLibrary = false
                            webViewInstance?.reload()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else if (isLoading && loadProgress < 25 && !hasRenderCrash) {
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
        }
    }
}

