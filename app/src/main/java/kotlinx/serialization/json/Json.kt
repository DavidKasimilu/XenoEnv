package kotlinx.serialization.json

import com.hexadecinull.vineos.data.models.ROMDownloadState
import com.hexadecinull.vineos.data.models.ROMImage
import com.hexadecinull.vineos.data.repository.ROMManifest
import org.json.JSONArray
import org.json.JSONObject

class JsonBuilder {
    var ignoreUnknownKeys: Boolean = false
}

fun Json(builderAction: JsonBuilder.() -> Unit = {}): Json {
    val builder = JsonBuilder().apply(builderAction)
    return Json(builder.ignoreUnknownKeys)
}

class Json(val ignoreUnknownKeys: Boolean = true) {

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T> decodeFromString(string: String): T {
        val kClass = T::class.java
        return when {
            kClass == ROMManifest::class.java -> parseROMManifest(string) as T
            kClass == List::class.java -> parseROMImageList(string) as T
            kClass.simpleName == "VromManifestEntry" -> parseReflectiveObject(string, kClass) as T
            else -> parseReflectiveObject(string, kClass) as T
        }
    }

    fun <T> encodeValueToString(value: T): String {
        if (value is List<*>) {
            val arr = JSONArray()
            for (item in value) {
                if (item is ROMImage) {
                    arr.put(romImageToJson(item))
                }
            }
            return arr.toString()
        }
        return "{}"
    }

    fun encodeToString(value: List<ROMImage>): String = encodeValueToString(value)

    fun parseROMManifest(raw: String): ROMManifest {
        val obj = JSONObject(raw)
        val version = obj.optInt("version", 1)
        val romsArray = obj.optJSONArray("roms") ?: JSONArray()
        val roms = mutableListOf<ROMImage>()
        for (i in 0 until romsArray.length()) {
            val itemObj = romsArray.optJSONObject(i) ?: continue
            roms.add(jsonToRomImage(itemObj))
        }
        return ROMManifest(version = version, roms = roms)
    }

    fun parseROMImageList(raw: String): List<ROMImage> {
        val arr = JSONArray(raw)
        val list = mutableListOf<ROMImage>()
        for (i in 0 until arr.length()) {
            val itemObj = arr.optJSONObject(i) ?: continue
            list.add(jsonToRomImage(itemObj))
        }
        return list
    }

    fun jsonToRomImage(obj: JSONObject): ROMImage {
        val abisArr = obj.optJSONArray("supportedAbis") ?: JSONArray()
        val abis = mutableListOf<String>()
        for (i in 0 until abisArr.length()) {
            abis.add(abisArr.optString(i))
        }
        val stateStr = obj.optString("downloadState", ROMDownloadState.NOT_DOWNLOADED.name)
        val downloadState = runCatching { ROMDownloadState.valueOf(stateStr) }
            .getOrDefault(ROMDownloadState.NOT_DOWNLOADED)
        val localPathVal = if (obj.isNull("localPath")) null else obj.optString("localPath").takeIf { it.isNotEmpty() }

        return ROMImage(
            id = obj.optString("id"),
            displayName = obj.optString("displayName"),
            androidVersion = obj.optString("androidVersion"),
            apiLevel = obj.optInt("apiLevel", 26),
            description = obj.optString("description", ""),
            downloadUrl = obj.optString("downloadUrl", ""),
            sha256 = obj.optString("sha256", ""),
            sizeBytes = obj.optLong("sizeBytes", 0L),
            minHostApiLevel = obj.optInt("minHostApiLevel", 26),
            supportedAbis = abis,
            has32BitSupport = obj.optBoolean("has32BitSupport", false),
            releaseDate = obj.optString("releaseDate", ""),
            isLocal = obj.optBoolean("isLocal", false),
            localPath = localPathVal,
            isDownloaded = obj.optBoolean("isDownloaded", false),
            downloadState = downloadState
        )
    }

    private fun romImageToJson(rom: ROMImage): JSONObject {
        return JSONObject().apply {
            put("id", rom.id)
            put("displayName", rom.displayName)
            put("androidVersion", rom.androidVersion)
            put("apiLevel", rom.apiLevel)
            put("description", rom.description)
            put("downloadUrl", rom.downloadUrl)
            put("sha256", rom.sha256)
            put("sizeBytes", rom.sizeBytes)
            put("minHostApiLevel", rom.minHostApiLevel)
            put("supportedAbis", JSONArray(rom.supportedAbis))
            put("has32BitSupport", rom.has32BitSupport)
            put("releaseDate", rom.releaseDate)
            put("isLocal", rom.isLocal)
            put("localPath", rom.localPath ?: JSONObject.NULL)
            put("isDownloaded", rom.isDownloaded)
            put("downloadState", rom.downloadState.name)
        }
    }

    fun <T> parseReflectiveObject(raw: String, clazz: Class<T>): T {
        val obj = JSONObject(raw)
        val displayName = obj.optString("displayName")
        val androidVersion = obj.optString("androidVersion")
        val apiLevel = obj.optInt("apiLevel", 26)
        val abisArr = obj.optJSONArray("supportedAbis") ?: JSONArray()
        val abis = mutableListOf<String>()
        for (i in 0 until abisArr.length()) {
            abis.add(abisArr.optString(i))
        }
        val has32BitSupport = obj.optBoolean("has32BitSupport", false)
        val releaseDate = obj.optString("releaseDate", "")

        val constructor = clazz.declaredConstructors.firstOrNull { it.parameterCount == 6 }
            ?: clazz.declaredConstructors.first()
        constructor.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return constructor.newInstance(
            displayName,
            androidVersion,
            apiLevel,
            abis,
            has32BitSupport,
            releaseDate
        ) as T
    }

    companion object {
        val Default = Json(ignoreUnknownKeys = true)
    }
}
