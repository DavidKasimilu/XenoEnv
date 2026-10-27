package com.hexadecinull.vineos.shizuku

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel

interface IVineShizukuService : IInterface {
    fun destroy()
    fun exit()
    fun probeNamespaces(): String

    abstract class Stub : Binder(), IVineShizukuService {
        init {
            this.attachInterface(this, DESCRIPTOR)
        }

        override fun asBinder(): IBinder = this

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            return when (code) {
                TRANSACTION_destroy -> {
                    data.enforceInterface(DESCRIPTOR)
                    destroy()
                    reply?.writeNoException()
                    true
                }
                TRANSACTION_exit -> {
                    data.enforceInterface(DESCRIPTOR)
                    exit()
                    reply?.writeNoException()
                    true
                }
                TRANSACTION_probeNamespaces -> {
                    data.enforceInterface(DESCRIPTOR)
                    val result = probeNamespaces()
                    reply?.writeNoException()
                    reply?.writeString(result)
                    true
                }
                INTERFACE_TRANSACTION -> {
                    reply?.writeString(DESCRIPTOR)
                    true
                }
                else -> super.onTransact(code, data, reply, flags)
            }
        }

        private class Proxy(private val remote: IBinder) : IVineShizukuService {
            override fun asBinder(): IBinder = remote

            override fun destroy() {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    remote.transact(TRANSACTION_destroy, data, reply, 0)
                    reply.readException()
                } finally {
                    reply.recycle()
                    data.recycle()
                }
            }

            override fun exit() {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    remote.transact(TRANSACTION_exit, data, reply, 0)
                    reply.readException()
                } finally {
                    reply.recycle()
                    data.recycle()
                }
            }

            override fun probeNamespaces(): String {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                return try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    remote.transact(TRANSACTION_probeNamespaces, data, reply, 0)
                    reply.readException()
                    reply.readString().orEmpty()
                } finally {
                    reply.recycle()
                    data.recycle()
                }
            }
        }

        companion object {
            private const val DESCRIPTOR = "com.hexadecinull.vineos.shizuku.IVineShizukuService"
            const val TRANSACTION_destroy = 16777114
            const val TRANSACTION_exit = IBinder.FIRST_CALL_TRANSACTION + 0
            const val TRANSACTION_probeNamespaces = IBinder.FIRST_CALL_TRANSACTION + 1

            fun asInterface(obj: IBinder?): IVineShizukuService {
                if (obj == null) throw IllegalArgumentException("Binder is null")
                val iin = obj.queryLocalInterface(DESCRIPTOR)
                if (iin is IVineShizukuService) return iin
                return Proxy(obj)
            }
        }
    }
}
