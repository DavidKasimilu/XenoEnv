package com.hexadecinull.vineos.shizuku

import android.content.Context
import android.os.Process
import androidx.annotation.Keep

// Instantiated by Shizuku's server inside the shell (ADB) or root process, not VineOS's own app process; both constructors and System.loadLibrary must stay reachable after ProGuard, hence @Keep
class VineShizukuService : IVineShizukuService.Stub {
    constructor() : super()

    @Keep
    constructor(context: Context) : this()

    override fun destroy() {
        System.exit(0)
    }

    override fun exit() {
        destroy()
    }

    override fun probeNamespaces(): String =
        if (nativeLoaded) {
            runCatching { nativeProbeNamespaces() }.getOrElse { fallbackProbe() }
        } else {
            fallbackProbe()
        }

    private fun fallbackProbe(): String =
        "unshare_ok=0;unshare_errno=1;mount_ok=0;mount_errno=1;uid=${Process.myUid()}"

    private external fun nativeProbeNamespaces(): String

    companion object {
        private val nativeLoaded: Boolean = runCatching {
            System.loadLibrary("vine_runtime")
            true
        }.getOrDefault(false)
    }
}
