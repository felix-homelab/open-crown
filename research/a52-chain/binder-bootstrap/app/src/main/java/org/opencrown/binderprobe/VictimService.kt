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

/** P in the CVE-2026-64468 topology: receives e2, sends e1, then dies. */
class VictimService : Service() {
    private var walker: IBinder? = null
    private val binder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != 1) return super.onTransact(code, data, reply, flags)
            val target = walker
            if (target == null) {
                Log.e("BinderHarness", "victim has no walker handle")
                return false
            }
            return try {
                HarnessNative.victimChild(BinderNative.nativeData(target))
                Log.i("BinderHarness", "victim sent e1; scheduling death")
                Thread {
                    try {
                        Thread.sleep(10)
                        stopSelf()
                        Process.killProcess(Process.myPid())
                    } catch (_: Throwable) {
                    }
                }.start()
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
