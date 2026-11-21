package org.opencrown.binderprobe

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.util.Log

class ManagerService : Service() {
    private val brokers = arrayOfNulls<IBinder>(2)
    private val binder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == 1) {
                val value = data.readInt()
                Log.i("BinderProbe", "manager transaction value=$value")
                reply?.writeInt(value + 1)
                return true
            }
            if (code == 10 || code == 12) {
                val slot = if (code == 10) 0 else 1
                val broker = data.readStrongBinder()
                brokers[slot] = broker
                Log.i("BinderProbe", "manager registered slot=$slot broker=${broker != null}")
                reply?.writeInt(if (broker != null) 0 else -1)
                return true
            }
            if (code == 11) {
                val slot = data.readInt()
                val other = brokers.getOrNull(1 - slot)
                Log.i("BinderProbe", "manager get slot=$slot other=${other != null}")
                reply?.writeInt(if (other != null) 0 else -1)
                reply?.writeStrongBinder(other)
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
