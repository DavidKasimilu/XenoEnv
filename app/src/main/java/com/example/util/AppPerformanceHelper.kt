package com.example.util

import android.app.Activity
import android.content.ComponentCallbacks2
import android.os.Build
import android.util.LruCache
import android.view.WindowManager
import com.example.data.models.ContactItem
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Centralized helper for general app performance, speed, and UI smoothness.
 *
 * Responsibilities:
 * 1. High-refresh-rate (90Hz/120Hz+) & hardware-accelerated window optimization for smooth scrolling and animations.
 * 2. Fast O(1) in-memory LRU caching for phone number normalization and Contact Name resolution,
 *    eliminating N+1 ContentResolver Binder IPC calls during SMS thread & Call Log loading.
 * 3. Coalescing/throttling guards for high-frequency data syncs so the UI thread never stutters.
 * 4. Automatic memory-pressure cache trimming to prevent GC frame drops.
 */
object AppPerformanceHelper {

    private const val NEGATIVE_CACHE_SENTINEL = "__NO_CONTACT_MATCH__"

    // Thread-safe LRU cache for phone -> contact name lookups (avoids repeated ContentResolver IPC)
    private val contactNameCache = object : LruCache<String, String>(512) {}

    // Fast normalized phone tail (last 7-10 digits) -> display name map populated from loaded contacts
    private val contactTailLookupMap = ConcurrentHashMap<String, String>(512)

    // Cache of raw phone string -> normalized digits string to avoid repeated char filtering in tight loops
    private val normalizedDigitsCache = object : LruCache<String, String>(1024) {}

    // Track last full data load timestamp to coalesce redundant back-to-back refreshes (e.g. init + ON_RESUME)
    private val lastFullLoadTimestamp = AtomicLong(0L)
    private val lastCallSyncTimestamp = AtomicLong(0L)

    /**
     * Optimizes the Activity window for maximum frame rate (90Hz/120Hz/144Hz where supported)
     * and ensures full hardware acceleration for smooth Compose transitions and WebView scrolling.
     */
    fun optimizeWindowForSmoothness(activity: Activity) {
        try {
            val window = activity.window ?: return

            // Ensure hardware acceleration is enabled at the window level
            window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)

            // Request highest available display refresh rate mode (90Hz / 120Hz+) at the current resolution
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    activity.display
                } else {
                    @Suppress("DEPRECATION")
                    activity.windowManager.defaultDisplay
                }

                display?.let { disp ->
                    val currentMode = disp.mode
                    val supportedModes = disp.supportedModes
                    // Pick the mode matching current resolution with the highest refresh rate
                    val bestHighRefreshMode = supportedModes
                        .filter {
                            it.physicalWidth == currentMode.physicalWidth &&
                                it.physicalHeight == currentMode.physicalHeight
                        }
                        .maxByOrNull { it.refreshRate }
                        ?: supportedModes.maxByOrNull { it.refreshRate }

                    val params = window.attributes
                    if (bestHighRefreshMode != null && bestHighRefreshMode.refreshRate > 60f) {
                        params.preferredDisplayModeId = bestHighRefreshMode.modeId
                        params.preferredRefreshRate = bestHighRefreshMode.refreshRate
                        window.attributes = params
                    }
                }
            }
        } catch (_: Throwable) {
            // Safe fallback on devices with custom window managers
        }
    }

    /**
     * Fast cached extraction of digits from a phone number string.
     */
    fun normalizeDigitsFast(rawNumber: String): String {
        if (rawNumber.isEmpty()) return ""
        synchronized(normalizedDigitsCache) {
            normalizedDigitsCache.get(rawNumber)?.let { return it }
        }
        val sb = StringBuilder(rawNumber.length)
        for (i in 0 until rawNumber.length) {
            val ch = rawNumber[i]
            if (ch in '0'..'9') {
                sb.append(ch)
            }
        }
        val digits = sb.toString()
        synchronized(normalizedDigitsCache) {
            normalizedDigitsCache.put(rawNumber, digits)
        }
        return digits
    }

    /**
     * Extracts the last 7 digits of a phone number for fast tail matching.
     */
    fun phoneMatchKey(rawNumber: String): String {
        val digits = normalizeDigitsFast(rawNumber)
        return if (digits.length >= 7) digits.takeLast(7) else digits
    }

    /**
     * Pre-warms the O(1) contact lookup caches from the loaded contacts list.
     * Once warmed up, SMS threads and Call Logs can resolve contact names in memory without Binder IPC.
     */
    fun warmUpContactCache(contacts: List<ContactItem>) {
        if (contacts.isEmpty()) return
        for (contact in contacts) {
            val name = contact.displayName
            if (name.isBlank()) continue

            if (contact.primaryNumber.isNotBlank()) {
                putContactInCache(contact.primaryNumber, name)
            }
            for (num in contact.allNumbers) {
                if (num.isNotBlank()) {
                    putContactInCache(num, name)
                }
            }
        }
    }

    /**
     * Caches a resolved contact name (or negative lookup) for a phone number.
     */
    fun putContactInCache(phoneNumber: String, resolvedName: String?) {
        val trimmed = phoneNumber.trim()
        if (trimmed.isEmpty()) return
        val valueToStore = if (resolvedName.isNullOrBlank()) NEGATIVE_CACHE_SENTINEL else resolvedName

        synchronized(contactNameCache) {
            contactNameCache.put(trimmed, valueToStore)
        }
        val key = phoneMatchKey(trimmed)
        if (key.length >= 5 && !resolvedName.isNullOrBlank()) {
            contactTailLookupMap[key] = resolvedName
        }
    }

    /**
     * Attempts an O(1) in-memory lookup of a contact name by phone number.
     * Returns:
     * - Pair(true, displayNameOrNull) if the cache had a definitive hit (including cached negative lookup)
     * - Pair(false, null) if not yet cached
     */
    fun getCachedContactName(phoneNumber: String): Pair<Boolean, String?> {
        val trimmed = phoneNumber.trim()
        if (trimmed.isEmpty()) return Pair(true, null)

        synchronized(contactNameCache) {
            val direct = contactNameCache.get(trimmed)
            if (direct != null) {
                return if (direct == NEGATIVE_CACHE_SENTINEL) {
                    Pair(true, null)
                } else {
                    Pair(true, direct)
                }
            }
        }

        val tailKey = phoneMatchKey(trimmed)
        if (tailKey.length >= 5) {
            val tailMatch = contactTailLookupMap[tailKey]
            if (tailMatch != null) {
                synchronized(contactNameCache) {
                    contactNameCache.put(trimmed, tailMatch)
                }
                return Pair(true, tailMatch)
            }
        }

        return Pair(false, null)
    }

    /**
     * Clears contact caches when the user adds, edits, or deletes a contact.
     */
    fun invalidateContactCache() {
        synchronized(contactNameCache) {
            contactNameCache.evictAll()
        }
        contactTailLookupMap.clear()
    }

    /**
     * Coalesces rapid back-to-back full data loads (e.g. ViewModel init + Activity ON_RESUME firing within 400ms).
     */
    fun shouldRunFullDataLoad(force: Boolean = false, minIntervalMs: Long = 600L): Boolean {
        val now = System.currentTimeMillis()
        val prev = lastFullLoadTimestamp.get()
        if (!force && now - prev < minIntervalMs) {
            return false
        }
        lastFullLoadTimestamp.set(now)
        return true
    }

    /**
     * Coalesces rapid call-log sync bursts so multiple observers don't cause redundant list recompositions.
     */
    fun shouldRunCallSync(force: Boolean = false, minIntervalMs: Long = 120L): Boolean {
        val now = System.currentTimeMillis()
        val prev = lastCallSyncTimestamp.get()
        if (!force && now - prev < minIntervalMs) {
            return false
        }
        lastCallSyncTimestamp.set(now)
        return true
    }

    /**
     * Frees memory caches under system memory pressure to prevent GC pauses and frame drops.
     */
    fun onTrimMemory(level: Int) {
        try {
            if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
                level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
            ) {
                synchronized(normalizedDigitsCache) {
                    normalizedDigitsCache.trimToSize(256)
                }
                synchronized(contactNameCache) {
                    contactNameCache.trimToSize(256)
                }
            }
        } catch (_: Throwable) {}
    }
}
