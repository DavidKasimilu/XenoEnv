package com.hexadecinull.vineos.di

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.hexadecinull.vineos.VineApplication
import com.hexadecinull.vineos.data.models.VMInstance
import com.hexadecinull.vineos.data.models.VMStatus
import com.hexadecinull.vineos.data.repository.AppPreferences
import com.hexadecinull.vineos.data.repository.InstanceRepository
import com.hexadecinull.vineos.data.repository.ROMRepository
import com.hexadecinull.vineos.data.repository.VMInstanceDao
import com.hexadecinull.vineos.domain.VineVMManager
import com.hexadecinull.vineos.shizuku.ShizukuManager
import com.hexadecinull.vineos.ui.viewmodel.AboutViewModel
import com.hexadecinull.vineos.ui.viewmodel.CreateInstanceViewModel
import com.hexadecinull.vineos.ui.viewmodel.HomeViewModel
import com.hexadecinull.vineos.ui.viewmodel.InstanceDetailViewModel
import com.hexadecinull.vineos.ui.viewmodel.ROMDetailViewModel
import com.hexadecinull.vineos.ui.viewmodel.ROMsViewModel
import com.hexadecinull.vineos.ui.viewmodel.SettingsViewModel
import com.hexadecinull.vineos.ui.viewmodel.ShizukuViewModel
import com.hexadecinull.vineos.ui.viewmodel.VMDisplayViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object VineServiceLocator {
    @Volatile
    private var initialized = false

    private lateinit var appContext: Context
    val appPreferences: AppPreferences by lazy { AppPreferences(appContext) }
    val vmInstanceDao: VMInstanceDao by lazy { PersistentVMInstanceDao(appContext) }
    val instanceRepository: InstanceRepository by lazy { InstanceRepository(vmInstanceDao) }
    val okHttpClient by lazy { NetworkModule.provideOkHttpClient() }
    val romRepository: ROMRepository by lazy { ROMRepository(appContext, okHttpClient) }
    val vmManager: VineVMManager by lazy { VineVMManager(appContext) }
    val shizukuManager: ShizukuManager by lazy { ShizukuManager(appContext) }

    fun ensureInitialized(context: Context) {
        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    appContext = context.applicationContext
                    ensureNotificationChannels(appContext)
                    initialized = true
                }
            }
        }
    }

    private fun ensureNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                NotificationChannel(
                    VineApplication.CHANNEL_VM_RUNNING,
                    "Running VMs",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Shown while a VineOS virtual machine is active"
                    setShowBadge(false)
                },
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    VineApplication.CHANNEL_ROM_DOWNLOAD,
                    "ROM Downloads",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Progress notifications for ROM image downloads"
                    setShowBadge(false)
                },
            )
        }
    }

    fun provideViewModelFactory(context: Context): ViewModelProvider.Factory {
        ensureInitialized(context)
        return object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return when {
                    modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                        SettingsViewModel(appPreferences) as T
                    modelClass.isAssignableFrom(HomeViewModel::class.java) ->
                        HomeViewModel(instanceRepository, vmManager) as T
                    modelClass.isAssignableFrom(ROMsViewModel::class.java) ->
                        ROMsViewModel(romRepository) as T
                    modelClass.isAssignableFrom(ShizukuViewModel::class.java) ->
                        ShizukuViewModel(shizukuManager) as T
                    modelClass.isAssignableFrom(AboutViewModel::class.java) ->
                        AboutViewModel(appContext, vmManager) as T
                    modelClass.isAssignableFrom(CreateInstanceViewModel::class.java) ->
                        CreateInstanceViewModel(instanceRepository, romRepository, appPreferences) as T
                    modelClass.isAssignableFrom(InstanceDetailViewModel::class.java) ->
                        InstanceDetailViewModel(instanceRepository, vmManager) as T
                    modelClass.isAssignableFrom(ROMDetailViewModel::class.java) ->
                        ROMDetailViewModel(romRepository) as T
                    modelClass.isAssignableFrom(VMDisplayViewModel::class.java) ->
                        VMDisplayViewModel(vmManager, instanceRepository) as T
                    else -> throw IllegalArgumentException("Unknown VineOS ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}

private class PersistentVMInstanceDao(context: Context) : VMInstanceDao {
    private val dbFile = File(context.filesDir, "vineos_instances.json")
    private val stateFlow = MutableStateFlow(loadFromDisk())

    override fun observeAll(): Flow<List<VMInstance>> = stateFlow.asStateFlow()

    override suspend fun getById(id: String): VMInstance? =
        stateFlow.value.find { it.id == id }

    override suspend fun insert(instance: VMInstance) = withContext(Dispatchers.IO) {
        val updated = (stateFlow.value.filterNot { it.id == instance.id } + instance)
            .sortedByDescending { it.lastUsedAt }
        stateFlow.value = updated
        saveToDisk(updated)
    }

    override suspend fun update(instance: VMInstance) = insert(instance)

    override suspend fun updateStatus(id: String, status: VMStatus) = withContext(Dispatchers.IO) {
        val updated = stateFlow.value.map {
            if (it.id == id) it.copy(status = status) else it
        }
        stateFlow.value = updated
        saveToDisk(updated)
    }

    override suspend fun updateLastUsed(id: String, timestamp: Long) = withContext(Dispatchers.IO) {
        val updated = stateFlow.value.map {
            if (it.id == id) it.copy(lastUsedAt = timestamp) else it
        }.sortedByDescending { it.lastUsedAt }
        stateFlow.value = updated
        saveToDisk(updated)
    }

    override suspend fun delete(instance: VMInstance) = deleteById(instance.id)

    override suspend fun deleteById(id: String) = withContext(Dispatchers.IO) {
        val updated = stateFlow.value.filterNot { it.id == id }
        stateFlow.value = updated
        saveToDisk(updated)
    }

    override suspend fun count(): Int = stateFlow.value.size

    private fun loadFromDisk(): List<VMInstance> {
        if (!dbFile.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(dbFile.readText())
            val list = mutableListOf<VMInstance>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val status = runCatching { VMStatus.valueOf(o.optString("status", "STOPPED")) }
                    .getOrDefault(VMStatus.STOPPED)
                list.add(
                    VMInstance(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        romId = o.optString("romId"),
                        romVersion = o.optString("romVersion"),
                        storagePath = o.optString("storagePath"),
                        status = status,
                        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                        lastUsedAt = o.optLong("lastUsedAt", System.currentTimeMillis()),
                        ramMb = o.optInt("ramMb", 1024),
                        cpuCores = o.optInt("cpuCores", 0),
                        storageMb = o.optInt("storageMb", 4096),
                        androidVersionDisplay = o.optString("androidVersionDisplay"),
                        isRooted = o.optBoolean("isRooted", false),
                        iconEmoji = o.optString("iconEmoji", "\uD83D\uDFE2"),
                    )
                )
            }
            list.sortedByDescending { it.lastUsedAt }
        }.getOrDefault(emptyList())
    }

    private fun saveToDisk(list: List<VMInstance>) {
        runCatching {
            val arr = JSONArray()
            for (item in list) {
                arr.put(
                    JSONObject().apply {
                        put("id", item.id)
                        put("name", item.name)
                        put("romId", item.romId)
                        put("romVersion", item.romVersion)
                        put("storagePath", item.storagePath)
                        put("status", item.status.name)
                        put("createdAt", item.createdAt)
                        put("lastUsedAt", item.lastUsedAt)
                        put("ramMb", item.ramMb)
                        put("cpuCores", item.cpuCores)
                        put("storageMb", item.storageMb)
                        put("androidVersionDisplay", item.androidVersionDisplay)
                        put("isRooted", item.isRooted)
                        put("iconEmoji", item.iconEmoji)
                    }
                )
            }
            dbFile.writeText(arr.toString())
        }
    }
}
