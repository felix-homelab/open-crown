package org.opencrown.binderprobe

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.util.Log

class ManagerService : Service() {
    private val binder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == 1) {
                val value = data.readInt()
                Log.i("BinderProbe", "manager transaction value=$value")
                reply?.writeInt(value + 1)
                return true
            }
            return super.onTransact(code, data, reply, flags)
        }
    }

    override fun onCreate() {
        Log.i("BinderProbe", "manager process created pid=" + android.os.Process.myPid())
        super.onCreate()
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.i("BinderProbe", "manager onBind")
        return binder
    }
}
