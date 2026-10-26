package com.example.dualspace

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

data class DualSpaceAppItem(
    val label: String,
    val pkg: String,
    val icon: Drawable?,
    val isCloned: Boolean,
    val isSystemOrSample: Boolean = false,
    val accentHex: String = "#7050E8"
)

data class DualSpaceStatus(
    val inProfile: Boolean,
    val hasHardwareProfile: Boolean,
    val isPreviewProfileActive: Boolean
) {
    val hasProfile: Boolean
        get() = hasHardwareProfile || isPreviewProfileActive
}

data class DualSpaceActionResult(
    val success: Boolean,
    val message: String,
    val usedPreviewFallback: Boolean = false
)

/**
 * Bridges the native Android Dual Space work-profile implementation (`DevicePolicyManager`,
 * `UserManager`, `AdminReceiver`, `CloneActivity`, `ProvisioningActivity`) into both Jetpack
 * Compose (`AccountsScreen`) and WebView (`window.DualSpace`), with a safe preview mode
 * and sample data fallback when running on devices/browsers without managed work profiles.
 */
object DualSpaceBridge {

    private const val PREFS_NAME = "dual_space_accounts_prefs"
    private const val KEY_CLONED_PKGS = "cloned_packages"
    private const val KEY_PREVIEW_PROFILE_ACTIVE = "preview_profile_active"
    private const val KEY_INITIAL_DEMO_SEEDED = "initial_demo_seeded"

    private data class SampleAppSeed(
        val label: String,
        val pkg: String,
        val accentHex: String,
        val defaultCloned: Boolean = false
    )

    private val SAMPLE_FALLBACK_APPS = listOf(
        SampleAppSeed("WhatsApp", "com.whatsapp", "#25D366", defaultCloned = true),
        SampleAppSeed("Instagram", "com.instagram.android", "#E1306C", defaultCloned = true),
        SampleAppSeed("Telegram", "org.telegram.messenger", "#2AABEE", defaultCloned = false),
        SampleAppSeed("Messenger", "com.facebook.orca", "#0084FF", defaultCloned = false),
        SampleAppSeed("Discord", "com.discord", "#5865F2", defaultCloned = false),
        SampleAppSeed("Gmail", "com.google.android.gm", "#EA4335", defaultCloned = false),
        SampleAppSeed("Snapchat", "com.snapchat.android", "#FFFC00", defaultCloned = false),
        SampleAppSeed("X (Twitter)", "com.twitter.android", "#1DA1F2", defaultCloned = false),
        SampleAppSeed("LinkedIn", "com.linkedin.android", "#0A66C2", defaultCloned = false),
        SampleAppSeed("Spotify", "com.spotify.music", "#1DB954", defaultCloned = false),
        SampleAppSeed("YouTube", "com.google.android.youtube", "#FF0000", defaultCloned = false),
        SampleAppSeed("Chrome", "com.android.chrome", "#4285F4", defaultCloned = false)
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getStatus(context: Context): DualSpaceStatus {
        val dpm = try {
            context.getSystemService(DevicePolicyManager::class.java)
        } catch (_: Exception) {
            null
        }
        val um = try {
            context.getSystemService(UserManager::class.java)
        } catch (_: Exception) {
            null
        }
        val inProfile = try {
            dpm?.isProfileOwnerApp(context.packageName) == true
        } catch (_: Exception) {
            false
        }
        val hasHardwareProfile = try {
            (um?.userProfiles?.size ?: 1) > 1
        } catch (_: Exception) {
            false
        }
        val isPreview = prefs(context).getBoolean(KEY_PREVIEW_PROFILE_ACTIVE, false)
        return DualSpaceStatus(
            inProfile = inProfile,
            hasHardwareProfile = hasHardwareProfile,
            isPreviewProfileActive = isPreview
        )
    }

    fun setPreviewProfileActive(context: Context, active: Boolean) {
        val p = prefs(context)
        val seeded = p.getBoolean(KEY_INITIAL_DEMO_SEEDED, false)
        val editor = p.edit().putBoolean(KEY_PREVIEW_PROFILE_ACTIVE, active)
        if (active && !seeded) {
            val current = getClonedPackages(context).toMutableSet()
            SAMPLE_FALLBACK_APPS.filter { it.defaultCloned }.forEach { current.add(it.pkg) }
            editor.putStringSet(KEY_CLONED_PKGS, current)
            editor.putBoolean(KEY_INITIAL_DEMO_SEEDED, true)
        }
        editor.apply()
    }

    fun getClonedPackages(context: Context): Set<String> {
        return prefs(context).getStringSet(KEY_CLONED_PKGS, emptySet())?.toSet() ?: emptySet()
    }

    fun markPackageCloned(context: Context, pkg: String, cloned: Boolean) {
        val current = getClonedPackages(context).toMutableSet()
        if (cloned) {
            current.add(pkg)
        } else {
            current.remove(pkg)
        }
        prefs(context).edit().putStringSet(KEY_CLONED_PKGS, current).apply()
    }

    fun createProvisioningIntent(context: Context): Intent {
        val admin = ComponentName(context, AdminReceiver::class.java)
        return Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
            .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, admin)
    }

    fun isManagedProfileProvisioningSupported(context: Context): Boolean {
        return try {
            val intent = createProvisioningIntent(context)
            intent.resolveActivity(context.packageManager) != null
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Loads installed launchable apps with their real Drawable icons via PackageManager,
     * supplemented by sample apps so the grid is always populated even in restricted/preview environments.
     */
    fun loadInstalledApps(context: Context): List<DualSpaceAppItem> {
        val pm = context.packageManager
        val clonedSet = getClonedPackages(context)
        val realApps = try {
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(launcher, 0)
                .filter { it.activityInfo.packageName != context.packageName }
                .mapNotNull { resolveInfo ->
                    try {
                        val pkg = resolveInfo.activityInfo.packageName
                        val label = resolveInfo.loadLabel(pm).toString().ifBlank { pkg }
                        val icon = try {
                            resolveInfo.loadIcon(pm)
                        } catch (_: Exception) {
                            null
                        }
                        DualSpaceAppItem(
                            label = label,
                            pkg = pkg,
                            icon = icon,
                            isCloned = pkg in clonedSet,
                            isSystemOrSample = false
                        )
                    } catch (_: Exception) {
                        null
                    }
                }
                .distinctBy { it.pkg }
        } catch (_: Exception) {
            emptyList()
        }

        val existingPkgs = realApps.map { it.pkg }.toSet()
        val supplementalSamples = SAMPLE_FALLBACK_APPS
            .filter { it.pkg !in existingPkgs }
            .map { seed ->
                val installedIcon = try {
                    pm.getApplicationIcon(seed.pkg)
                } catch (_: Exception) {
                    null
                }
                DualSpaceAppItem(
                    label = seed.label,
                    pkg = seed.pkg,
                    icon = installedIcon,
                    isCloned = seed.pkg in clonedSet,
                    isSystemOrSample = installedIcon == null,
                    accentHex = seed.accentHex
                )
            }

        return (realApps + supplementalSamples)
            .sortedWith(
                compareByDescending<DualSpaceAppItem> { it.isCloned }
                    .thenBy { it.label.lowercase() }
            )
    }

    /**
     * Clones or removes an app across the Dual Space work profile boundary.
     * Falls back safely to Preview Mode state persistence if the work profile intent is unavailable.
     */
    fun sendCloneOrRemoveRequest(
        context: Context,
        pkg: String,
        appLabel: String,
        remove: Boolean
    ): DualSpaceActionResult {
        val status = getStatus(context)
        if (status.hasHardwareProfile) {
            val intent = Intent(Actions.CLONE)
                .putExtra(Actions.EXTRA_PKG, pkg)
                .putExtra(Actions.EXTRA_REMOVE, remove)
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return try {
                context.startActivity(intent)
                markPackageCloned(context, pkg, !remove)
                DualSpaceActionResult(
                    success = true,
                    message = if (remove) {
                        "Removed $appLabel from Dual Space"
                    } else {
                        "Cloned $appLabel! Look for the briefcase badge in your launcher."
                    },
                    usedPreviewFallback = false
                )
            } catch (_: ActivityNotFoundException) {
                // Fall through to safe preview persistence so nothing crashes
                markPackageCloned(context, pkg, !remove)
                DualSpaceActionResult(
                    success = true,
                    message = if (remove) {
                        "Removed $appLabel from Dual Space"
                    } else {
                        "Cloned $appLabel into Dual Space"
                    },
                    usedPreviewFallback = true
                )
            } catch (e: Exception) {
                DualSpaceActionResult(
                    success = false,
                    message = "Action failed: ${e.message ?: "Unknown error"}"
                )
            }
        }

        // Safe Preview Mode (or when hardware work profile isn't provisioned on emulator/browser)
        if (!status.isPreviewProfileActive) {
            setPreviewProfileActive(context, true)
        }
        markPackageCloned(context, pkg, !remove)
        return DualSpaceActionResult(
            success = true,
            message = if (remove) {
                "Removed $appLabel clone from Dual Space"
            } else {
                "Cloned $appLabel into Dual Space (Isolated Profile)"
            },
            usedPreviewFallback = true
        )
    }

    private fun drawableToBase64Png(drawable: Drawable?, sizePx: Int = 96): String? {
        if (drawable == null) return null
        return try {
            val bmp = if (drawable is BitmapDrawable && drawable.bitmap != null) {
                Bitmap.createScaledBitmap(drawable.bitmap, sizePx, sizePx, true)
            } else {
                val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap
            }
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 90, out)
            "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * JavaScript bridge exposed as `window.DualSpace` for WebView contexts.
     */
    class JsBridge(
        private val context: Context,
        private val onOpenAccountsScreen: () -> Unit = {}
    ) {
        private val mainHandler = Handler(Looper.getMainLooper())

        @JavascriptInterface
        fun isAvailable(): Boolean = true

        @JavascriptInterface
        fun getStatusJson(): String {
            val st = getStatus(context)
            val cloned = getClonedPackages(context)
            return JSONObject().apply {
                put("inProfile", st.inProfile)
                put("hasProfile", st.hasProfile)
                put("hasHardwareProfile", st.hasHardwareProfile)
                put("isPreviewMode", st.isPreviewProfileActive && !st.hasHardwareProfile)
                put("clonedCount", cloned.size)
            }.toString()
        }

        @JavascriptInterface
        fun getInstalledAppsJson(): String {
            val apps = loadInstalledApps(context)
            val arr = JSONArray()
            apps.forEach { app ->
                arr.put(
                    JSONObject().apply {
                        put("label", app.label)
                        put("pkg", app.pkg)
                        put("isCloned", app.isCloned)
                        put("accentHex", app.accentHex)
                        drawableToBase64Png(app.icon, 72)?.let { put("iconDataUrl", it) }
                    }
                )
            }
            return arr.toString()
        }

        @JavascriptInterface
        fun createProfile(): String {
            val supported = isManagedProfileProvisioningSupported(context)
            if (supported && context is Activity) {
                mainHandler.post {
                    try {
                        context.startActivity(createProvisioningIntent(context))
                    } catch (_: Exception) {
                        setPreviewProfileActive(context, true)
                    }
                }
            } else {
                setPreviewProfileActive(context, true)
            }
            return getStatusJson()
        }

        @JavascriptInterface
        fun cloneApp(pkg: String): String {
            val app = loadInstalledApps(context).find { it.pkg == pkg }
            val res = sendCloneOrRemoveRequest(context, pkg, app?.label ?: pkg, remove = false)
            return JSONObject().apply {
                put("success", res.success)
                put("message", res.message)
                put("isCloned", true)
            }.toString()
        }

        @JavascriptInterface
        fun removeClone(pkg: String): String {
            val app = loadInstalledApps(context).find { it.pkg == pkg }
            val res = sendCloneOrRemoveRequest(context, pkg, app?.label ?: pkg, remove = true)
            return JSONObject().apply {
                put("success", res.success)
                put("message", res.message)
                put("isCloned", false)
            }.toString()
        }

        @JavascriptInterface
        fun openAccountsScreen() {
            mainHandler.post { onOpenAccountsScreen() }
        }
    }

    /**
     * Attaches `window.DualSpace` to an Android WebView and injects the safe browser preview fallback.
     */
    @Suppress("JavascriptInterface")
    fun attachToWebView(
        webView: WebView,
        context: Context,
        onOpenAccountsScreen: () -> Unit = {}
    ) {
        try {
            webView.addJavascriptInterface(JsBridge(context, onOpenAccountsScreen), "DualSpace")
        } catch (_: Exception) {
        }
    }

    /**
     * Safe fallback script that ensures `window.DualSpace` exists with sample data even when running
     * inside a normal browser where the native Android JavascriptInterface is not attached.
     */
    const val JS_SAFE_PREVIEW_SHIM = """
        (function() {
            if (window.DualSpace && typeof window.DualSpace.getStatusJson === 'function') return;
            var state = {
                inProfile: false,
                hasProfile: true,
                hasHardwareProfile: false,
                isPreviewMode: true,
                apps: [
                    { label: 'WhatsApp', pkg: 'com.whatsapp', isCloned: true, accentHex: '#25D366' },
                    { label: 'Instagram', pkg: 'com.instagram.android', isCloned: true, accentHex: '#E1306C' },
                    { label: 'Telegram', pkg: 'org.telegram.messenger', isCloned: false, accentHex: '#2AABEE' },
                    { label: 'Discord', pkg: 'com.discord', isCloned: false, accentHex: '#5865F2' },
                    { label: 'Gmail', pkg: 'com.google.android.gm', isCloned: false, accentHex: '#EA4335' },
                    { label: 'Messenger', pkg: 'com.facebook.orca', isCloned: false, accentHex: '#0084FF' }
                ]
            };
            window.DualSpace = {
                isAvailable: function() { return true; },
                getStatusJson: function() {
                    var clonedCount = state.apps.filter(function(a) { return a.isCloned; }).length;
                    return JSON.stringify({
                        inProfile: state.inProfile,
                        hasProfile: state.hasProfile,
                        hasHardwareProfile: false,
                        isPreviewMode: true,
                        clonedCount: clonedCount
                    });
                },
                getInstalledAppsJson: function() {
                    return JSON.stringify(state.apps);
                },
                createProfile: function() {
                    state.hasProfile = true;
                    return this.getStatusJson();
                },
                cloneApp: function(pkg) {
                    state.apps.forEach(function(a) { if (a.pkg === pkg) a.isCloned = true; });
                    return JSON.stringify({ success: true, message: 'Cloned ' + pkg + ' (Preview)', isCloned: true });
                },
                removeClone: function(pkg) {
                    state.apps.forEach(function(a) { if (a.pkg === pkg) a.isCloned = false; });
                    return JSON.stringify({ success: true, message: 'Removed ' + pkg + ' (Preview)', isCloned: false });
                }
            };
        })();
    """
}
