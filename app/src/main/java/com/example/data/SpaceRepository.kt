package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.data.models.ClonedAppInstance
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class SpaceRepository(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("xeno_space_prefs", Context.MODE_PRIVATE)

    companion object {
        fun resolveAppUrl(packageName: String, appName: String): String {
            val pkg = packageName.lowercase()
            val name = appName.lowercase()
            return when {
                pkg.contains("instagram") || name.contains("instagram") -> "https://www.instagram.com/"
                pkg.contains("whatsapp") || name.contains("whatsapp") -> "https://web.whatsapp.com/"
                pkg.contains("telegram") || name.contains("telegram") -> "https://web.telegram.org/"
                pkg.contains("twitter") || name.contains("twitter") || pkg.contains(".x.") || name == "x" -> "https://x.com/"
                pkg.contains("tiktok") || name.contains("tiktok") -> "https://www.tiktok.com/"
                pkg.contains("facebook") || name.contains("facebook") -> "https://m.facebook.com/"
                pkg.contains("discord") || name.contains("discord") -> "https://discord.com/app"
                pkg.contains("reddit") || name.contains("reddit") -> "https://www.reddit.com/"
                pkg.contains("youtube") || name.contains("youtube") -> "https://m.youtube.com/"
                pkg.contains("spotify") || name.contains("spotify") -> "https://open.spotify.com/"
                pkg.contains("threads") || name.contains("threads") -> "https://www.threads.net/"
                pkg.contains("messenger") || name.contains("messenger") -> "https://www.messenger.com/"
                pkg.contains("linkedin") || name.contains("linkedin") -> "https://www.linkedin.com/"
                pkg.contains("pinterest") || name.contains("pinterest") -> "https://www.pinterest.com/"
                pkg.contains("twitch") || name.contains("twitch") -> "https://www.twitch.tv/"
                pkg.contains("netflix") || name.contains("netflix") -> "https://www.netflix.com/"
                pkg.contains("chatgpt") || name.contains("chatgpt") || pkg.contains("openai") -> "https://chatgpt.com/"
                pkg.contains("snapchat") || name.contains("snapchat") -> "https://web.snapchat.com/"
                pkg.contains("amazon") || name.contains("amazon") -> "https://www.amazon.com/"
                pkg.contains("chess") || name.contains("chess") -> "https://www.chess.com/play/online"
                pkg.contains("lichess") || name.contains("lichess") -> "https://lichess.org/"
                pkg.contains("roblox") || name.contains("roblox") -> "https://www.roblox.com/"
                pkg.contains("itch") || name.contains("itch") -> "https://itch.io/games/html5"
                pkg.contains("poki") || name.contains("poki") -> "https://poki.com/"
                pkg.contains("crazygames") || name.contains("crazygames") -> "https://www.crazygames.com/"
                pkg.contains("github") || name.contains("github") -> "https://github.com/"
                pkg.contains("gmail") || name.contains("gmail") || pkg.contains(".gm") -> "https://mail.google.com/"
                pkg.contains("chrome") || pkg.contains("browser") || name.contains("browser") -> "https://www.google.com/"
                else -> "https://www.google.com/search?q=${android.net.Uri.encode(appName)}"
            }
        }

        fun getDefaultInitialClones(): List<ClonedAppInstance> {
            val now = System.currentTimeMillis()
            return listOf(
                ClonedAppInstance(
                    instanceId = "clone_instagram_1",
                    packageName = "com.instagram.android",
                    templateId = "com.instagram.android",
                    displayName = "Instagram",
                    originalName = "Instagram",
                    targetUrl = "https://www.instagram.com/",
                    badgeTag = "Clone",
                    badgeColorHex = "#E1306C",
                    createdAt = now,
                    lastActiveTime = now,
                    lastUrl = "https://www.instagram.com/",
                    isDesktopMode = false
                ),
                ClonedAppInstance(
                    instanceId = "clone_whatsapp_1",
                    packageName = "com.whatsapp",
                    templateId = "com.whatsapp",
                    displayName = "WhatsApp",
                    originalName = "WhatsApp",
                    targetUrl = "https://web.whatsapp.com/",
                    badgeTag = "Web",
                    badgeColorHex = "#25D366",
                    createdAt = now,
                    lastActiveTime = now,
                    lastUrl = "https://web.whatsapp.com/",
                    isDesktopMode = true
                ),
                ClonedAppInstance(
                    instanceId = "clone_gmail_1",
                    packageName = "com.google.android.gm",
                    templateId = "com.google.android.gm",
                    displayName = "Gmail",
                    originalName = "Gmail",
                    targetUrl = "https://mail.google.com/",
                    badgeTag = "Mail",
                    badgeColorHex = "#EA4335",
                    createdAt = now,
                    lastActiveTime = now,
                    lastUrl = "https://mail.google.com/",
                    isDesktopMode = false
                )
            )
        }
    }

    fun loadClonedInstances(): List<ClonedAppInstance> {
        val jsonStr = prefs.getString("cloned_instances", null)
        if (jsonStr.isNullOrBlank()) {
            val defaultList = getDefaultInitialClones()
            prefs.edit().putBoolean("gmail_default_clone_added_v1", true).apply()
            saveClonedInstances(defaultList)
            return defaultList
        }

        val list = mutableListOf<ClonedAppInstance>()
        var needsResave = false
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val pkg = obj.optString("packageName", "")
                val origName = obj.optString("originalName", "App")
                val rawDispName = obj.optString("displayName", origName)
                val dispName = when (rawDispName) {
                    "Instagram (Clone 1)" -> {
                        needsResave = true
                        "Instagram"
                    }
                    "WhatsApp (Personal)" -> {
                        needsResave = true
                        "WhatsApp"
                    }
                    else -> rawDispName
                }
                val rawTarget = obj.optString("targetUrl", "")
                val rawLast = obj.optString("lastUrl", "")

                // Auto-repair missing or blank URL for Instagram, WhatsApp, Gmail or any other app
                val isWa = pkg.contains("whatsapp", ignoreCase = true) ||
                        origName.contains("whatsapp", ignoreCase = true) ||
                        dispName.contains("whatsapp", ignoreCase = true) ||
                        rawTarget.contains("whatsapp.com", ignoreCase = true)
                val isGmail = pkg.contains("android.gm", ignoreCase = true) ||
                        pkg.contains("gmail", ignoreCase = true) ||
                        origName.contains("gmail", ignoreCase = true) ||
                        dispName.contains("gmail", ignoreCase = true) ||
                        rawTarget.contains("mail.google.com", ignoreCase = true)
                val resolvedTarget = if (isWa) {
                    "https://web.whatsapp.com/"
                } else if (isGmail) {
                    "https://mail.google.com/"
                } else if (rawTarget.isNotBlank() && !rawTarget.equals("about:blank", ignoreCase = true)) {
                    rawTarget
                } else {
                    needsResave = true
                    resolveAppUrl(pkg, origName.ifBlank { dispName })
                }

                val resolvedLast = if (isWa && (rawLast.isBlank() || rawLast.contains("download") || rawLast.equals("about:blank", ignoreCase = true) || !rawLast.startsWith("https://web.whatsapp.com"))) {
                    needsResave = true
                    "https://web.whatsapp.com/"
                } else if (isGmail && (rawLast.isBlank() || rawLast.equals("about:blank", ignoreCase = true))) {
                    needsResave = true
                    "https://mail.google.com/"
                } else if (rawLast.isNotBlank() && !rawLast.equals("about:blank", ignoreCase = true)) {
                    rawLast
                } else {
                    needsResave = true
                    resolvedTarget
                }

                val effectiveDesktopMode = if (isWa) {
                    true
                } else if (obj.has("isDesktopMode")) {
                    obj.optBoolean("isDesktopMode", false)
                } else {
                    false
                }

                if (isWa && (!obj.optBoolean("isDesktopMode", false) || rawTarget != "https://web.whatsapp.com/")) {
                    needsResave = true
                }

                list.add(
                    ClonedAppInstance(
                        instanceId = obj.optString("instanceId", UUID.randomUUID().toString()),
                        packageName = pkg,
                        templateId = obj.optString("templateId", pkg),
                        displayName = dispName,
                        originalName = origName,
                        targetUrl = resolvedTarget,
                        badgeTag = obj.optString("badgeTag", when {
                            isWa -> "Web"
                            isGmail -> "Mail"
                            else -> "Clone"
                        }),
                        badgeColorHex = obj.optString("badgeColorHex", when {
                            isWa -> "#25D366"
                            isGmail -> "#EA4335"
                            else -> "#E1306C"
                        }),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        lastActiveTime = obj.optLong("lastActiveTime", System.currentTimeMillis()),
                        lastUrl = resolvedLast,
                        isDesktopMode = effectiveDesktopMode
                    )
                )
            }
        } catch (_: Exception) {}

        if (list.isEmpty()) {
            val defaultList = getDefaultInitialClones()
            prefs.edit().putBoolean("gmail_default_clone_added_v1", true).apply()
            saveClonedInstances(defaultList)
            return defaultList
        }

        // Ensure the 3rd default app (Gmail) is added once for existing users
        if (!prefs.getBoolean("gmail_default_clone_added_v1", false)) {
            val hasGmail = list.any {
                it.packageName.contains("android.gm", ignoreCase = true) ||
                        it.packageName.contains("gmail", ignoreCase = true) ||
                        it.originalName.contains("gmail", ignoreCase = true) ||
                        it.displayName.contains("gmail", ignoreCase = true) ||
                        it.targetUrl.contains("mail.google.com", ignoreCase = true)
            }
            if (!hasGmail) {
                val now = System.currentTimeMillis()
                list.add(
                    ClonedAppInstance(
                        instanceId = "clone_gmail_1",
                        packageName = "com.google.android.gm",
                        templateId = "com.google.android.gm",
                        displayName = "Gmail",
                        originalName = "Gmail",
                        targetUrl = "https://mail.google.com/",
                        badgeTag = "Mail",
                        badgeColorHex = "#EA4335",
                        createdAt = now,
                        lastActiveTime = now,
                        lastUrl = "https://mail.google.com/",
                        isDesktopMode = false
                    )
                )
                needsResave = true
            }
            prefs.edit().putBoolean("gmail_default_clone_added_v1", true).apply()
        }

        if (needsResave) {
            saveClonedInstances(list)
        }

        return list
    }

    fun saveClonedInstances(instances: List<ClonedAppInstance>) {
        val array = JSONArray()
        for (item in instances) {
            val obj = JSONObject().apply {
                put("instanceId", item.instanceId)
                put("packageName", item.packageName)
                put("templateId", item.templateId)
                put("displayName", item.displayName)
                put("originalName", item.originalName)
                put("targetUrl", item.targetUrl)
                put("badgeTag", item.badgeTag)
                put("badgeColorHex", item.badgeColorHex)
                put("createdAt", item.createdAt)
                put("lastActiveTime", item.lastActiveTime)
                put("lastUrl", item.lastUrl)
                put("isDesktopMode", item.isDesktopMode)
            }
            array.put(obj)
        }
        prefs.edit().putString("cloned_instances", array.toString()).apply()
    }

    fun addInstalledAppClone(
        packageName: String,
        appName: String,
        displayName: String,
        badgeTag: String,
        badgeColorHex: String
    ): ClonedAppInstance {
        val resolvedUrl = resolveAppUrl(packageName, appName)
        val isWa = packageName.contains("whatsapp", ignoreCase = true) || appName.contains("whatsapp", ignoreCase = true)
        val newClone = ClonedAppInstance(
            instanceId = "clone_${UUID.randomUUID().toString().take(8)}",
            packageName = packageName,
            templateId = packageName,
            displayName = displayName.ifBlank { appName },
            originalName = appName,
            targetUrl = resolvedUrl,
            badgeTag = badgeTag.ifBlank { if (isWa) "Web" else "Clone" },
            badgeColorHex = badgeColorHex.ifBlank { if (isWa) "#25D366" else "#E1306C" },
            createdAt = System.currentTimeMillis(),
            lastActiveTime = System.currentTimeMillis(),
            lastUrl = resolvedUrl,
            isDesktopMode = isWa
        )

        val current = loadClonedInstances().toMutableList()
        current.add(0, newClone)
        saveClonedInstances(current)

        val dock = loadActiveDock().toMutableList()
        if (!dock.contains(newClone.instanceId)) {
            dock.add(newClone.instanceId)
            saveActiveDock(dock)
        }

        return newClone
    }

    fun updateClone(updated: ClonedAppInstance) {
        val current = loadClonedInstances().toMutableList()
        val index = current.indexOfFirst { it.instanceId == updated.instanceId }
        if (index >= 0) {
            current[index] = updated
            saveClonedInstances(current)
        }
    }

    fun deleteClone(instanceId: String) {
        val current = loadClonedInstances().filter { it.instanceId != instanceId }
        saveClonedInstances(current)

        val dock = loadActiveDock().filter { it != instanceId }
        saveActiveDock(dock)
    }

    fun updateCloneUrl(instanceId: String, url: String) {
        val current = loadClonedInstances().toMutableList()
        val index = current.indexOfFirst { it.instanceId == instanceId }
        if (index >= 0) {
            val item = current[index]
            current[index] = item.copy(lastUrl = url, lastActiveTime = System.currentTimeMillis())
            saveClonedInstances(current)
        }
    }

    fun toggleDesktopMode(instanceId: String): Boolean {
        val current = loadClonedInstances().toMutableList()
        val index = current.indexOfFirst { it.instanceId == instanceId }
        if (index >= 0) {
            val item = current[index]
            val newMode = !item.isDesktopMode
            current[index] = item.copy(isDesktopMode = newMode)
            saveClonedInstances(current)
            return newMode
        }
        return false
    }

    fun loadActiveDock(): List<String> {
        val json = prefs.getString("active_dock", null) ?: return emptyList()
        val list = mutableListOf<String>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
        } catch (_: Exception) {}
        return list
    }

    fun saveActiveDock(instanceIds: List<String>) {
        val array = JSONArray()
        for (id in instanceIds) {
            array.put(id)
        }
        prefs.edit().putString("active_dock", array.toString()).apply()
    }
}
