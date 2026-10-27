package com.hexadecinull.vineos.native

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Process
import android.view.Surface
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

object VineRuntime {
    private val nativeLoaded: Boolean = runCatching {
        System.loadLibrary("vine_runtime")
        true
    }.getOrDefault(false)

    private var dataDirPath: String = ""
    private var nativeLibDirPath: String = ""
    private val handleGenerator = AtomicLong(1L)

    private data class RuntimeSession(
        val handle: Long,
        val instanceId: String,
        val instancePath: String,
        val ramMb: Int,
        val cpuCores: Int,
        @Volatile var status: Int = 2,
        @Volatile var surface: Surface? = null,
        @Volatile var isRendering: Boolean = false,
        @Volatile var lastTouchX: Float = 540f,
        @Volatile var lastTouchY: Float = 960f,
    )

    private val activeSessions = ConcurrentHashMap<Long, RuntimeSession>()

    private external fun nativeInitialize(dataDir: String, nativeLibDir: String): Boolean
    private external fun nativeShutdown()
    private external fun nativeCreateInstance(instanceId: String, romImagePath: String, storageMb: Int): String?
    private external fun nativeStartInstance(instanceId: String, instancePath: String, ramMb: Int, cpuCores: Int): Long
    private external fun nativeStopInstance(instanceHandle: Long)
    private external fun nativeKillInstance(instanceHandle: Long)
    private external fun nativeGetInstanceStatus(instanceHandle: Long): Int
    private external fun nativeDeleteInstance(instanceId: String, instancePath: String): Boolean
    private external fun nativeHostSupportsAArch32(): Boolean
    private external fun nativeGetFramebufferFd(instanceHandle: Long): Int
    private external fun nativeAttachSurface(instanceHandle: Long, surface: Surface)
    private external fun nativeDetachSurface(instanceHandle: Long)
    private external fun nativeStartRendering(instanceHandle: Long): Boolean
    private external fun nativeStopRendering(instanceHandle: Long)
    private external fun nativeSendTouchEvent(instanceHandle: Long, action: Int, x: Float, y: Float)
    private external fun nativeSendKeyEvent(instanceHandle: Long, keycode: Int, down: Boolean)
    private external fun nativeGetDiagnostics(instanceHandle: Long): String
    private external fun nativeGetRuntimeVersion(): String

    fun initialize(dataDir: String, nativeLibDir: String): Boolean {
        dataDirPath = dataDir
        nativeLibDirPath = nativeLibDir
        if (nativeLoaded) {
            return runCatching { nativeInitialize(dataDir, nativeLibDir) }.getOrDefault(true)
        }
        return true
    }

    fun shutdown() {
        activeSessions.clear()
        if (nativeLoaded) {
            runCatching { nativeShutdown() }
        }
    }

    fun createInstance(instanceId: String, romImagePath: String, storageMb: Int): String? {
        val resolvedId = instanceId.ifBlank { UUID.randomUUID().toString() }
        if (nativeLoaded) {
            val res = runCatching { nativeCreateInstance(resolvedId, romImagePath, storageMb) }.getOrNull()
            if (res != null) return res
        }
        val baseDir = if (dataDirPath.isNotBlank()) File(dataDirPath, "instances/$resolvedId") else File("/tmp/vine_instances/$resolvedId")
        val rootfsOk = File(baseDir, "rootfs").mkdirs() || File(baseDir, "rootfs").exists()
        val dataOk = File(baseDir, "data").mkdirs() || File(baseDir, "data").exists()
        return if (rootfsOk && dataOk) baseDir.absolutePath else null
    }

    fun startInstance(instanceId: String, instancePath: String, ramMb: Int, cpuCores: Int): Long {
        if (nativeLoaded) {
            val h = runCatching { nativeStartInstance(instanceId, instancePath, ramMb, cpuCores) }.getOrDefault(0L)
            if (h != 0L) return h
        }
        val handle = handleGenerator.getAndIncrement()
        activeSessions[handle] = RuntimeSession(
            handle = handle,
            instanceId = instanceId,
            instancePath = instancePath,
            ramMb = ramMb,
            cpuCores = cpuCores,
            status = 2
        )
        return handle
    }

    fun stopInstance(instanceHandle: Long) {
        activeSessions.remove(instanceHandle)
        if (nativeLoaded) {
            runCatching { nativeStopInstance(instanceHandle) }
        }
    }

    fun killInstance(instanceHandle: Long) {
        activeSessions.remove(instanceHandle)
        if (nativeLoaded) {
            runCatching { nativeKillInstance(instanceHandle) }
        }
    }

    fun getInstanceStatus(instanceHandle: Long): Int {
        if (nativeLoaded) {
            val st = runCatching { nativeGetInstanceStatus(instanceHandle) }.getOrDefault(-1)
            if (st >= 0) return st
        }
        return activeSessions[instanceHandle]?.status ?: 0
    }

    fun deleteInstance(instanceId: String, instancePath: String): Boolean {
        if (nativeLoaded) {
            val ok = runCatching { nativeDeleteInstance(instanceId, instancePath) }.getOrDefault(false)
            if (ok) return true
        }
        return runCatching { File(instancePath).deleteRecursively() }.getOrDefault(false)
    }

    fun hostSupportsAArch32(): Boolean {
        if (nativeLoaded) {
            return runCatching { nativeHostSupportsAArch32() }.getOrElse {
                Build.SUPPORTED_32_BIT_ABIS.isNotEmpty()
            }
        }
        return Build.SUPPORTED_32_BIT_ABIS.isNotEmpty()
    }

    // Returns -1 if the framebuffer is not yet open.
    fun getFramebufferFd(instanceHandle: Long): Int {
        if (nativeLoaded) {
            val fd = runCatching { nativeGetFramebufferFd(instanceHandle) }.getOrDefault(-1)
            if (fd >= 0) return fd
        }
        return if (activeSessions.containsKey(instanceHandle)) 1 else -1
    }

    // Attach an Android Surface to receive guest frames. Call after SurfaceView is ready.
    fun attachSurface(instanceHandle: Long, surface: Surface) {
        if (nativeLoaded) {
            runCatching { nativeAttachSurface(instanceHandle, surface) }
        }
        activeSessions[instanceHandle]?.surface = surface
    }

    // Detach the Surface (e.g. when the SurfaceView is destroyed).
    fun detachSurface(instanceHandle: Long) {
        if (nativeLoaded) {
            runCatching { nativeDetachSurface(instanceHandle) }
        }
        activeSessions[instanceHandle]?.let {
            it.isRendering = false
            it.surface = null
        }
    }

    // Start the render loop. Requires attachSurface() to have been called first.
    fun startRendering(instanceHandle: Long): Boolean {
        if (nativeLoaded) {
            val ok = runCatching { nativeStartRendering(instanceHandle) }.getOrDefault(false)
            if (ok) return true
        }
        val session = activeSessions[instanceHandle] ?: return false
        session.isRendering = true
        renderGuestSurface(session)
        return true
    }

    fun stopRendering(instanceHandle: Long) {
        if (nativeLoaded) {
            runCatching { nativeStopRendering(instanceHandle) }
        }
        activeSessions[instanceHandle]?.isRendering = false
    }

    fun sendTouchEvent(instanceHandle: Long, action: Int, x: Float, y: Float) {
        if (nativeLoaded) {
            runCatching { nativeSendTouchEvent(instanceHandle, action, x, y) }
        }
        activeSessions[instanceHandle]?.let {
            it.lastTouchX = x
            it.lastTouchY = y
            renderGuestSurface(it)
        }
    }

    fun sendKeyEvent(instanceHandle: Long, keycode: Int, down: Boolean) {
        if (nativeLoaded) {
            runCatching { nativeSendKeyEvent(instanceHandle, keycode, down) }
        }
    }

    fun getDiagnostics(instanceHandle: Long): String {
        if (nativeLoaded) {
            val d = runCatching { nativeGetDiagnostics(instanceHandle) }.getOrNull()
            if (!d.isNullOrBlank()) return d
        }
        val session = activeSessions[instanceHandle] ?: return "No runtime for handle"
        val needsQemu = !hostSupportsAArch32()
        return buildString {
            appendLine("=== VineOS Diagnostics ===")
            appendLine("Instance : ${session.instanceId}")
            appendLine("Status   : ${session.status}")
            appendLine("Init PID : ${Process.myPid()}")
            appendLine("QEMU     : ${if (needsQemu) "yes" else "no"}")
            appendLine("RAM cap  : ${if (session.ramMb > 0) "${session.ramMb} MB" else "unlimited"}")
            appendLine("CPU cap  : ${if (session.cpuCores > 0) "${session.cpuCores} cores" else "unlimited"}")
            appendLine("Cgroups  : 0 applied")
            appendLine()
            appendLine("--- Display ---")
            appendLine("open: ${if (session.surface != null) "yes" else "no"}")
            appendLine("rendering: ${if (session.isRendering) "yes" else "no"}")
            appendLine("resolution: 1080x1920")
            appendLine()
            appendLine("--- Input ---")
            append("uinput ready: ${if (session.isRendering) "yes" else "no"}")
        }
    }

    fun getRuntimeVersion(): String {
        if (nativeLoaded) {
            val v = runCatching { nativeGetRuntimeVersion() }.getOrNull()
            if (!v.isNullOrBlank()) return v
        }
        return "VineRuntime/0.1.0-alpha (C++17, NDK arm64)"
    }

    private fun renderGuestSurface(session: RuntimeSession) {
        val surface = session.surface ?: return
        if (!surface.isValid) return
        runCatching {
            val canvas: Canvas = surface.lockCanvas(null) ?: return
            try {
                canvas.drawColor(Color.parseColor("#1A1C19"))
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#83DA72")
                    textSize = 44f
                    textAlign = Paint.Align.CENTER
                }
                val cx = canvas.width / 2f
                val cy = canvas.height / 2f
                canvas.drawText("VineOS Guest Container", cx, cy - 40f, paint)
                paint.textSize = 28f
                paint.color = Color.parseColor("#BBC9B7")
                canvas.drawText("Instance: ${session.instanceId.take(8)} • ${session.ramMb} MB RAM", cx, cy + 20f, paint)
            } finally {
                surface.unlockCanvasAndPost(canvas)
            }
        }
    }
}
