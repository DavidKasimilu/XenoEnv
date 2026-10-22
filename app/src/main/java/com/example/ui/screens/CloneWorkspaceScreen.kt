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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DesktopMac
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
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
import com.example.data.models.ClonedAppInstance
import com.example.ui.components.AppIconImage
import com.example.ui.components.parseHexColor

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
    private val checkPermissionsGranted: () -> Boolean
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
}

/**
 * Lightweight, idempotent WhatsApp Web Support & Voice/Video Calling Engine for Android WebView:
 * 1. Polyfills Desktop Chrome 131 (Windows 64-bit) APIs (`window.Notification`, `window.chrome`, `navigator.userAgentData`).
 * 2. Enables WebRTC Voice & Video Calls using device microphone and camera:
 *    - Grants `navigator.permissions.query` for `'microphone'`, `'camera'`, `'audio-capture'`, `'video-capture'`, and `'notifications'`.
 *    - Enhances `navigator.mediaDevices.enumerateDevices` so labeled microphone, front/rear cameras, and speaker output are always detected by WhatsApp Web.
 *    - Wraps `navigator.mediaDevices.getUserMedia` with automatic constraint fallback (`OverconstrainedError` / `NotFoundError` protection) so desktop camera/mic constraints seamlessly map to Android device cameras and hardware microphone.
 *    - Polyfills `HTMLMediaElement.prototype.setSinkId` and inline video playback so call audio/video streams never fail.
 * 3. Preserves the untouched 1280px desktop layout.
 */
private fun injectWhatsAppWebSupportEngine(view: WebView?) {
    val js = """
        (function() {
            try {
                if (!window.__xenoWaPolyfilled) {
                    window.__xenoWaPolyfilled = true;
                    window.__xenoPreferredFacingMode = 'user';
                    window.__xenoActiveCallStreams = [];

                    if (!window.chrome) {
                        window.chrome = {
                            runtime: {},
                            app: { isInstalled: false },
                            webstore: {}
                        };
                    }

                    // 1. Desktop Notification API (Required for Incoming Call & Message Alerts)
                    if (typeof window.Notification === 'undefined' || window.Notification.permission !== 'granted') {
                        var OrigNotification = window.Notification;
                        var MockNotification = function(title, options) {
                            this.title = title;
                            this.options = options || {};
                            this.onclick = null;
                            this.onclose = null;
                            this.close = function() {
                                if (typeof this.onclose === 'function') this.onclose();
                            };
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

                    // 2. Desktop Windows 64-bit UserAgentData & Navigator properties for Calling Support
                    Object.defineProperty(navigator, 'userAgentData', {
                        get: function() {
                            return {
                                brands: [
                                    { brand: 'Google Chrome', version: '131' },
                                    { brand: 'Chromium', version: '131' },
                                    { brand: 'Not_A Brand', version: '24' }
                                ],
                                mobile: false,
                                platform: 'Windows',
                                getHighEntropyValues: function() {
                                    return Promise.resolve({
                                        architecture: 'x86',
                                        bitness: '64',
                                        brands: [
                                            { brand: 'Google Chrome', version: '131' },
                                            { brand: 'Chromium', version: '131' },
                                            { brand: 'Not_A Brand', version: '24' }
                                        ],
                                        fullVersionList: [
                                            { brand: 'Google Chrome', version: '131.0.6778.86' },
                                            { brand: 'Chromium', version: '131.0.6778.86' },
                                            { brand: 'Not_A Brand', version: '24.0.0.0' }
                                        ],
                                        mobile: false,
                                        model: '',
                                        platform: 'Windows',
                                        platformVersion: '15.0.0',
                                        uaFullVersion: '131.0.6778.86'
                                    });
                                },
                                toJSON: function() {
                                    return {
                                        brands: this.brands,
                                        mobile: false,
                                        platform: 'Windows'
                                    };
                                }
                            };
                        },
                        configurable: true
                    });
                    Object.defineProperty(navigator, 'platform', { get: function() { return 'Win32'; }, configurable: true });
                    Object.defineProperty(navigator, 'vendor', { get: function() { return 'Google Inc.'; }, configurable: true });
                    Object.defineProperty(navigator, 'maxTouchPoints', { get: function() { return 0; }, configurable: true });
                    Object.defineProperty(navigator, 'webdriver', { get: function() { return false; }, configurable: true });

                    // 3. Permissions API Polyfill: Report 'granted' for microphone, camera, speaker & notifications
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

                    // 4. HTMLMediaElement setSinkId & Speaker Output Polyfill for Call Audio Routing
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

                    // 5. WebRTC MediaDevices (Microphone, Camera & Speaker) Engine
                    if (!navigator.mediaDevices) {
                        try {
                            navigator.mediaDevices = {};
                        } catch(e) {}
                    }

                    if (navigator.mediaDevices) {
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
                                    groupId: true
                                });
                            };
                        }

                        var makeDeviceInfo = function(deviceId, kind, label, groupId) {
                            return {
                                deviceId: deviceId,
                                kind: kind,
                                label: label,
                                groupId: groupId || 'default',
                                toJSON: function() {
                                    return {
                                        deviceId: this.deviceId,
                                        kind: this.kind,
                                        label: this.label,
                                        groupId: this.groupId
                                    };
                                }
                            };
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
                                            if (kind === 'audioinput') label = 'Device Microphone ' + (micIdx++);
                                            else if (kind === 'videoinput') label = (camIdx === 1 ? 'Front Camera' : 'Back Camera ' + camIdx);
                                            else if (kind === 'audiooutput') label = 'Device Speaker ' + (spkIdx++);
                                            if (kind === 'videoinput') camIdx++;
                                        }
                                        if (kind === 'audioinput') hasAudioIn = true;
                                        if (kind === 'videoinput') hasVideoIn = true;
                                        if (kind === 'audiooutput') hasAudioOut = true;
                                        result.push(makeDeviceInfo(id, kind, label, d.groupId || 'default'));
                                    }
                                }

                                if (!hasAudioIn) {
                                    result.push(makeDeviceInfo('default', 'audioinput', 'Default - Android Microphone', 'audio-group-1'));
                                }
                                if (!hasVideoIn) {
                                    result.push(makeDeviceInfo('camera-front', 'videoinput', 'Front Camera (User)', 'video-group-1'));
                                    result.push(makeDeviceInfo('camera-back', 'videoinput', 'Back Camera (Environment)', 'video-group-2'));
                                }
                                if (!hasAudioOut) {
                                    result.push(makeDeviceInfo('default', 'audiooutput', 'Default - Device Speaker / Headset', 'audio-group-1'));
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
                                    if (!anyLive && window.XenoCallBridge && typeof window.XenoCallBridge.onCallEnded === 'function') {
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

                        // Sanitize desktop-specific constraints so Android WebView camera/mic HAL succeeds on first try
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

                            // If video is requested without explicit facingMode, prefer current facing mode
                            var primaryConstraints = reqConstraints;
                            if (wantsVideo && typeof reqConstraints.video === 'object' && reqConstraints.video !== null) {
                                var vCopy = Object.assign({}, reqConstraints.video);
                                // Strip desktop-only mandatory/exact deviceIds that don't match mobile camera IDs
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
                                    // Retry with level-1 mobile-optimized constraints
                                    return origGetUserMedia(buildFallbackConstraints(reqConstraints, 1))
                                        .then(function(stream) {
                                            return registerActiveStream(stream, wantsVideo);
                                        })
                                        .catch(function() {
                                            // Retry with level-2 bare boolean constraints
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

                        navigator.getUserMedia = function(constraints, onSuccess, onError) {
                            navigator.mediaDevices.getUserMedia(constraints)
                                .then(function(s) { if (onSuccess) onSuccess(s); })
                                .catch(function(e) { if (onError) onError(e); });
                        };
                        navigator.webkitGetUserMedia = navigator.getUserMedia;
                    }

                    // 6. Camera Flip Helper for active WhatsApp Video Calls
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
                                    // Replace video track in active PeerConnections & MediaStreams
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
                                }).catch(function() {});
                            }
                        } catch(e) {}
                    };

                    // 7. Track RTCPeerConnections for STUN reliability, camera switching & call state monitoring
                    var OrigPeerConnection = window.RTCPeerConnection || window.webkitRTCPeerConnection;
                    if (OrigPeerConnection && !window.__xenoPeerPatched) {
                        window.__xenoPeerPatched = true;
                        window.__xenoActivePeerConnections = new Set();
                        var PatchedPeerConnection = function(config, constraints) {
                            var cfg = config || {};
                            if (!cfg.iceServers || cfg.iceServers.length === 0) {
                                cfg = Object.assign({}, cfg, {
                                    iceServers: [
                                        { urls: ['stun:stun.l.google.com:19302', 'stun:stun1.l.google.com:19302'] }
                                    ]
                                });
                            }
                            var pc = new OrigPeerConnection(cfg, constraints);
                            window.__xenoActivePeerConnections.add(pc);
                            pc.addEventListener('connectionstatechange', function() {
                                if (pc.connectionState === 'closed' || pc.connectionState === 'failed' || pc.connectionState === 'disconnected') {
                                    window.__xenoActivePeerConnections.delete(pc);
                                }
                            });
                            return pc;
                        };
                        PatchedPeerConnection.prototype = OrigPeerConnection.prototype;
                        Object.keys(OrigPeerConnection).forEach(function(k) {
                            try { PatchedPeerConnection[k] = OrigPeerConnection[k]; } catch(e) {}
                        });
                        window.RTCPeerConnection = PatchedPeerConnection;
                        window.webkitRTCPeerConnection = PatchedPeerConnection;
                    }
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

                // Remove any custom style overrides so WhatsApp Web keeps its exact untouched desktop CSS
                var oldStyle = document.getElementById('xeno-wa-support-style');
                if (oldStyle && oldStyle.parentNode) {
                    oldStyle.parentNode.removeChild(oldStyle);
                }

                // Replace WhatsApp Web's 'width=device-width' meta viewport with a standard 1280px desktop viewport
                // so WebView renders the genuine desktop layout as-is without squishing or resizing panels to phone width
                function ensureDesktopViewport() {
                    if (!document.head) return;
                    var existingMetas = document.head.querySelectorAll('meta[name="viewport"]');
                    for (var i = 0; i < existingMetas.length; i++) {
                        if (existingMetas[i].id !== 'xeno-wa-desktop-viewport') {
                            existingMetas[i].parentNode.removeChild(existingMetas[i]);
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

                ensureDesktopViewport();

                if (!window.__xenoWaHeadObserver && window.MutationObserver && document.head) {
                    window.__xenoWaHeadObserver = new MutationObserver(function() {
                        var extras = document.head ? document.head.querySelectorAll('meta[name="viewport"]:not(#xeno-wa-desktop-viewport)') : [];
                        if (extras.length > 0 || !document.getElementById('xeno-wa-desktop-viewport')) {
                            ensureDesktopViewport();
                        }
                    });
                    window.__xenoWaHeadObserver.observe(document.head, { childList: true });
                }

                // Observe body for dynamically mounted WebRTC call <video>/<audio> elements so they play inline immediately
                if (!window.__xenoWaCallDomObserver && window.MutationObserver && document.body) {
                    window.__xenoWaCallDomObserver = new MutationObserver(function(mutations) {
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
 * Non-intrusive TikTok Vertical Video Feed & Smooth Swipe Helper Engine:
 * - Preserves TikTok's native non-passive touchmove Swiper gesture engine and inline CSS `transform: translate3d(...)`
 *   so vertical video swiping works smoothly.
 * - Spoofs standard Chrome Mobile environment (`navigator.webdriver = false`, `window.chrome`, Page Visibility)
 *   so TikTok's `xgplayer` MSE video streams load and play without WebView restrictions.
 * - Automatically dismisses "Open TikTok App" / "Not now" modal prompts via their close action rather than
 *   hiding layout containers or leaving invisible modal backdrops.
 */
private fun injectTikTokVideoAndScrollHelper(view: WebView?) {
    val js = """
        (function() {
            try {
                // 1. Browser Environment & Page Visibility Stabilization for TikTok xgplayer
                if (!window.__xenoTtEnvPatched) {
                    window.__xenoTtEnvPatched = true;
                    try {
                        Object.defineProperty(navigator, 'webdriver', { get: function() { return false; }, configurable: true });
                        Object.defineProperty(document, 'hidden', { get: function() { return false; }, configurable: true });
                        Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });
                        if (!window.chrome) {
                            window.chrome = { runtime: {} };
                        }
                    } catch(e) {}

                    // Unlock TikTok video audio on first user touch/tap interaction
                    window.__xenoTtAudioUnlocked = false;
                    var unlockTikTokAudio = function() {
                        window.__xenoTtAudioUnlocked = true;
                        var vids = document.querySelectorAll('video');
                        for (var i = 0; i < vids.length; i++) {
                            var v = vids[i];
                            if (v.muted) {
                                v.muted = false;
                                v.volume = 1.0;
                            }
                            if (v.paused && v.readyState >= 2 && !v.__xenoUserPaused) {
                                v.play().catch(function(){});
                            }
                        }
                        // Also click TikTok's unmute overlay button if visible
                        try {
                            var unmuteBtn = document.querySelector('[data-e2e*="video-sound"], [class*="DivVoiceControl"] button, [aria-label*="Unmute"]');
                            if (unmuteBtn && (unmuteBtn.innerText || '').toLowerCase().indexOf('unmute') !== -1) {
                                unmuteBtn.click();
                            }
                        } catch(e) {}
                    };
                    document.addEventListener('touchend', unlockTikTokAudio, { passive: true, once: true });
                    document.addEventListener('click', unlockTikTokAudio, { passive: true, once: true });
                }

                // 2. Non-destructive CSS: Never override `transform` on `video` or Swiper slides, and never hide broad `*OpenApp*` wrapper divs
                var styleId = 'xeno-tt-video-smooth-style';
                if (!document.getElementById(styleId) && document.head) {
                    var st = document.createElement('style');
                    st.id = styleId;
                    st.textContent =
                        'html, body { background-color: #000 !important; -webkit-overflow-scrolling: touch !important; overscroll-behavior-y: none !important; -webkit-tap-highlight-color: transparent !important; } ' +
                        'video { backface-visibility: hidden; -webkit-backface-visibility: hidden; } ' +
                        '[data-e2e="abb-banner"], [data-e2e="FloatingBar"], a[href^="intent://"], a[href^="snssdk"], a[href^="tiktok://"] { display: none !important; } ' +
                        '::-webkit-scrollbar { width: 0px !important; height: 0px !important; display: none !important; }';
                    document.head.appendChild(st);
                }

                // 3. Configure inline playback & gently dismiss "Open TikTok" / "Not now" prompts without fighting xgplayer's internal play/pause lifecycle
                function optimizeTikTokExperience() {
                    var videos = document.querySelectorAll('video');
                    for (var i = 0; i < videos.length; i++) {
                        var v = videos[i];
                        if (!v.hasAttribute('playsinline')) v.setAttribute('playsinline', '');
                        if (!v.hasAttribute('webkit-playsinline')) v.setAttribute('webkit-playsinline', '');
                        if (!v.hasAttribute('x5-playsinline')) v.setAttribute('x5-playsinline', 'true');
                        v.playsInline = true;
                        v.loop = true;

                        if (!v.__xenoTtObserved) {
                            v.__xenoTtObserved = true;
                            v.addEventListener('click', function() {
                                var self = this;
                                if (self.muted) {
                                    self.muted = false;
                                    self.volume = 1.0;
                                }
                                setTimeout(function() {
                                    self.__xenoUserPaused = self.paused;
                                }, 120);
                            }, { passive: true });

                            v.addEventListener('loadeddata', function() {
                                var self = this;
                                if (window.__xenoTtAudioUnlocked && self.muted) {
                                    self.muted = false;
                                }
                                if (self.paused && !self.__xenoUserPaused) {
                                    self.play().catch(function() {
                                        self.muted = true;
                                        self.play().catch(function(){});
                                    });
                                }
                            }, { passive: true });
                        }
                    }

                    // Safely click "Not now" / "Continue on web" buttons on TikTok's app-promotion bottom sheet so its backdrop closes cleanly
                    var buttons = document.querySelectorAll('button, div[role="button"], p, span');
                    for (var b = 0; b < buttons.length; b++) {
                        var el = buttons[b];
                        if (el.children && el.children.length > 1) continue;
                        var txt = (el.textContent || '').trim().toLowerCase();
                        if (txt === 'not now' || txt === 'continue on browser' || txt === 'continue on web' || txt === 'stay on web') {
                            try {
                                el.click();
                            } catch(e) {}
                        }
                    }
                }

                optimizeTikTokExperience();

                if (!window.__xenoTtMutationObserver && window.MutationObserver && document.body) {
                    var throttleTimer = null;
                    window.__xenoTtMutationObserver = new MutationObserver(function() {
                        if (throttleTimer) return;
                        throttleTimer = setTimeout(function() {
                            throttleTimer = null;
                            optimizeTikTokExperience();
                        }, 250);
                    });
                    window.__xenoTtMutationObserver.observe(document.body, {
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
    onUrlChanged: (String) -> Unit,
    onToggleDesktopMode: () -> Unit,
    onOpenInBrowser: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var currentTitle by remember { mutableStateOf(instance.displayName) }
    var currentUrl by remember { mutableStateOf(instance.lastUrl) }
    var canGoBack by remember { mutableStateOf(false) }
    var hasRenderCrash by remember(instance.instanceId) { mutableStateOf(false) }
    var useSoftwareLayer by remember(instance.instanceId) { mutableStateOf(false) }
    var reloadKey by remember(instance.instanceId) { mutableIntStateOf(0) }

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

    val isTiktok = remember(instance.instanceId, instance.packageName, instance.targetUrl) {
        instance.packageName.contains("tiktok", ignoreCase = true) ||
                instance.packageName.contains("musically", ignoreCase = true) ||
                instance.originalName.contains("tiktok", ignoreCase = true) ||
                instance.displayName.contains("tiktok", ignoreCase = true) ||
                instance.targetUrl.contains("tiktok.com", ignoreCase = true)
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

    val isShortVideoSocialApp = isInstagram || isFacebook || isTiktok || isYoutube

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
            injectWhatsAppWebSupportEngine(webViewInstance)
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

    // Properly release WebView renderer and call audio resources when leaving CloneWorkspaceScreen
    DisposableEffect(instance.instanceId) {
        onDispose {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioFocusRequest?.let { req ->
                        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                        am?.abandonAudioFocusRequest(req)
                    }
                }
                setWhatsAppCallAudioMode(context, active = false)
            } catch (_: Exception) {}
            try {
                webViewInstance?.let { wv ->
                    wv.keepScreenOn = false
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    wv.stopLoading()
                    wv.destroy()
                }
            } catch (_: Exception) {}
            webViewInstance = null
        }
    }

    BackHandler {
        if (webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        } else {
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
                    onClick = onCloseWorkspace,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("workspace_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                val appIcon = remember(instance.packageName) {
                    try {
                        if (instance.packageName.isNotBlank()) {
                            context.packageManager.getApplicationIcon(instance.packageName)
                        } else null
                    } catch (_: Exception) {
                        null
                    }
                }
                AppIconImage(
                    iconDrawable = appIcon,
                    fallbackName = instance.originalName,
                    modifier = Modifier.size(22.dp)
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
                }

                Spacer(modifier = Modifier.width(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
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
                                    injectWhatsAppWebSupportEngine(webViewInstance)
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
                                webViewInstance?.loadUrl("https://web.whatsapp.com/", WHATSAPP_WEB_HEADERS)
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
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (instance.isDesktopMode || isWhatsapp) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (instance.isDesktopMode || isWhatsapp) Icons.Filled.DesktopMac else Icons.Filled.PhoneAndroid,
                                    contentDescription = if (instance.isDesktopMode || isWhatsapp) "Desktop View Active" else "Mobile View Active",
                                    tint = if (instance.isDesktopMode || isWhatsapp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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

                    IconButton(
                        onClick = onCloseWorkspace,
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
                        isInstagram || isTiktok || isYoutube -> Color.Black
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
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                // Use software layer if emulator GPU crashed (except YouTube which requires hardware layer for HTML5 MSE video rendering)
                                if ((useSoftwareLayer && !isYoutube) || (isWhatsapp && !isVideoCallActive)) {
                                    setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                                } else {
                                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                                }
                                isVerticalScrollBarEnabled = !isShortVideoSocialApp || isYoutube || isFacebook
                                isHorizontalScrollBarEnabled = !isShortVideoSocialApp
                                if (isInstagram || isTiktok) {
                                    overScrollMode = View.OVER_SCROLL_NEVER
                                } else {
                                    overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                                }
                                isNestedScrollingEnabled = true
                                isFocusable = true
                                isFocusableInTouchMode = true
                                if (isYoutube || isTiktok || isFacebook || isDiscord) {
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
                                        isInstagram || isTiktok || isYoutube -> android.graphics.Color.BLACK
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
                                        }
                                    ),
                                    "XenoCallBridge"
                                )

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
                                            isWhatsapp -> injectWhatsAppWebSupportEngine(view)
                                            isDiscord || url?.contains("discord.com", ignoreCase = true) == true || url?.contains("discord.gg", ignoreCase = true) == true ->
                                                injectDiscordStabilityAndLoginHelper(view, instance.isDesktopMode)
                                            isInstagram || url?.contains("instagram.com", ignoreCase = true) == true ->
                                                injectInstagramReelsAndScrollHelper(view)
                                            isFacebook || url?.contains("facebook.com", ignoreCase = true) == true ->
                                                injectFacebookVideoAndScrollHelper(view)
                                            isTiktok || url?.contains("tiktok.com", ignoreCase = true) == true ->
                                                injectTikTokVideoAndScrollHelper(view)
                                            isYoutube || url?.contains("youtube.com", ignoreCase = true) == true || url?.contains("youtu.be", ignoreCase = true) == true ->
                                                injectYouTubeAndShortsSmoothHelper(view)
                                        }
                                    }

                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        isLoading = true
                                        injectSocialAppHelpers(view, url)
                                        url?.let {
                                            if (it.startsWith("http://") || it.startsWith("https://")) {
                                                currentUrl = it
                                                onUrlChanged(it)
                                            }
                                        }
                                        canGoBack = view?.canGoBack() == true
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
                                        // Prevent Facebook, TikTok, Instagram, YouTube & Discord from kicking the user out to Play Store or external app prompts
                                        if (isFacebook || isTiktok || isInstagram || isYoutube || isDiscord) {
                                            if (lowerUrl.contains("play.google.com/store/apps") ||
                                                lowerUrl.startsWith("market://") ||
                                                lowerUrl.startsWith("fb://") ||
                                                lowerUrl.startsWith("fbmessenger://") ||
                                                lowerUrl.startsWith("tiktok://") ||
                                                lowerUrl.startsWith("snssdk") ||
                                                lowerUrl.startsWith("musical://") ||
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
                                    isTiktok -> if (instance.lastUrl.startsWith("http") && instance.lastUrl.contains("tiktok.com", ignoreCase = true) && !instance.lastUrl.contains("/download", ignoreCase = true)) {
                                        instance.lastUrl
                                    } else {
                                        "https://www.tiktok.com/"
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
                        },
                        update = { wv ->
                            webViewInstance = wv
                            canGoBack = wv.canGoBack()
                        }
                    )
                }
            }

            if (isLoading && loadProgress < 25 && !hasRenderCrash) {
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

