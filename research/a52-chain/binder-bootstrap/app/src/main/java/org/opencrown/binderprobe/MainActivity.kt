package org.opencrown.binderprobe

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import android.widget.TextView

class MainActivity : Activity() {
    private var manager: IBinder? = null

    private external fun nativeTransact(binder: IBinder, code: Int, value: Int): Int
    private external fun nativeProbeLibbinder(): Boolean
    private external fun nativeAssociatedTransact(binder: IBinder, code: Int, value: Int): Int
    private external fun nativeRawTransact(binder: IBinder, code: Int, value: Int): Int
    private external fun nativeAIBinderHandles(binder: IBinder, code: Int, value: Int): Int
    private external fun nativeObjectHandles(binder: IBinder, code: Int, value: Int): Int

    companion object {
        init { System.loadLibrary("binderprobe") }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            manager = service
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInt(41)
                val ok = service.transact(1, data, reply, 0)
                reply.setDataPosition(0)
                val result = if (ok) reply.readInt() else -1
                val native = runCatching { nativeTransact(service, 1, 100) }
                    .getOrElse { -99 }
                val associated = runCatching { nativeAssociatedTransact(service, 1, 200) }
                    .getOrElse { -99 }
                service.javaClass.declaredFields.forEach {
                    Log.i("BinderProbe", "field ${it.name} ${it.type}")
                }
                val raw = runCatching { nativeRawTransact(service, 1, 300) }
                    .getOrElse { -99 }
                val aib = runCatching { nativeAIBinderHandles(service, 1, 400) }
                    .getOrElse { -99 }
                val obj = runCatching { nativeObjectHandles(service, 1, 500) }
                    .getOrElse { -99 }
                Log.i("BinderProbe", "connected handle=${service.hashCode()} result=$result native=$native associated=$associated raw=$raw aib=$aib obj=$obj")
                findViewById<TextView>(R.id.text).text =
                    "bound: handle=${service.hashCode()} result=$result native=$native"
            } catch (error: Throwable) {
                Log.e("BinderProbe", "transact failed: $error")
            } finally {
                data.recycle()
                reply.recycle()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            manager = null
            Log.w("BinderProbe", "service disconnected")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = TextView(this)
        text.id = R.id.text
        text.text = "binding..."
        setContentView(text)
        Log.i("BinderProbe", "libbinder=${nativeProbeLibbinder()}")
        Log.i("BinderProbe", "binding to ManagerService")
        bindService(Intent(this, ManagerService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        unbindService(connection)
        super.onDestroy()
    }
}
