package rikka.shizuku

import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import java.util.concurrent.CopyOnWriteArrayList

object Shizuku {
    fun interface OnBinderReceivedListener {
        fun onBinderReceived()
    }

    fun interface OnBinderDeadListener {
        fun onBinderDead()
    }

    fun interface OnRequestPermissionResultListener {
        fun onRequestPermissionResult(requestCode: Int, grantResult: Int)
    }

    class UserServiceArgs(val componentName: ComponentName) {
        var isDaemon: Boolean = false
            private set
        var processSuffix: String = ""
            private set
        var isDebuggable: Boolean = false
            private set
        var versionCode: Int = 1
            private set

        fun daemon(enable: Boolean): UserServiceArgs = apply { isDaemon = enable }
        fun processNameSuffix(suffix: String): UserServiceArgs = apply { processSuffix = suffix }
        fun debuggable(enable: Boolean): UserServiceArgs = apply { isDebuggable = enable }
        fun version(ver: Int): UserServiceArgs = apply { versionCode = ver }
    }

    private val receivedListeners = CopyOnWriteArrayList<OnBinderReceivedListener>()
    private val deadListeners = CopyOnWriteArrayList<OnBinderDeadListener>()
    private val permListeners = CopyOnWriteArrayList<OnRequestPermissionResultListener>()

    fun addBinderReceivedListenerSticky(listener: OnBinderReceivedListener) {
        receivedListeners.add(listener)
    }

    fun addBinderDeadListener(listener: OnBinderDeadListener) {
        deadListeners.add(listener)
    }

    fun addRequestPermissionResultListener(listener: OnRequestPermissionResultListener) {
        permListeners.add(listener)
    }

    fun isPreV11(): Boolean = false

    fun requestPermission(requestCode: Int) {
        permListeners.forEach {
            it.onRequestPermissionResult(requestCode, PackageManager.PERMISSION_DENIED)
        }
    }

    fun pingBinder(): Boolean = false

    fun checkSelfPermission(): Int = PackageManager.PERMISSION_DENIED

    fun getUid(): Int = -1

    fun bindUserService(args: UserServiceArgs, connection: ServiceConnection) {
        throw IllegalStateException("Shizuku service is not active")
    }

    fun unbindUserService(args: UserServiceArgs, connection: ServiceConnection, remove: Boolean) {}
}

class ShizukuProvider : ContentProvider() {
    override fun onCreate(): Boolean = true
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
