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
    private external fun nativeRawHandle(handle: Int, code: Int, value: Int): Int
    private external fun nativeHandleFromData(nativeData: Long, code: Int, value: Int): Int
    private external fun nativeExecProbe(): Int
    private external fun nativeAfAlgProbe(): Int
    private external fun nativePerfProbe(): Int

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
                val unsafeOutcome = runCatching {
                    org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("Landroid/os/BinderProxy;")
                    val unsafeClass = Class.forName("sun.misc.Unsafe")
                    val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply {
                        isAccessible = true
                    }.get(null)
                    val offset = unsafeClass.getMethod("objectFieldOffset", java.lang.reflect.Field::class.java)
                        .invoke(unsafe, Class.forName("android.os.BinderProxy")
                            .getDeclaredField("mNativeData")) as Long
                    val nativeData = unsafeClass.getMethod("getLong", Any::class.java, Long::class.javaPrimitiveType)
                        .invoke(unsafe, service, offset) as Long
                    Log.i("BinderProbe", "unsafe nativeData=$nativeData offset=$offset")
                    nativeHandleFromData(nativeData, 1, 700)
                }
                Log.i("BinderProbe", "unsafe outcome=$unsafeOutcome")
                val raw = runCatching { nativeRawTransact(service, 1, 300) }
                    .getOrElse { -99 }
                val aib = runCatching { nativeAIBinderHandles(service, 1, 400) }
                    .getOrElse { -99 }
                val obj = runCatching { nativeObjectHandles(service, 1, 500) }
                    .getOrElse { -99 }
                val parcel = Parcel.obtain()
                parcel.writeStrongBinder(service)
                val bytes = parcel.marshall()
                parcel.recycle()
                val hex = bytes.joinToString("") { "%02x".format(it) }
                Log.i("BinderProbe", "marshalled=$hex")
                var rawHandle = -1
                for (i in 0..(bytes.size - 12)) {
                    if (bytes[i].toInt() and 0xff == 0x85 &&
                        bytes[i + 1].toInt() and 0xff == 0x2a &&
                        bytes[i + 2].toInt() and 0xff == 0x62 &&
                        bytes[i + 3].toInt() and 0xff == 0x73) {
                        rawHandle = (bytes[i + 8].toInt() and 0xff) or
                            ((bytes[i + 9].toInt() and 0xff) shl 8) or
                            ((bytes[i + 10].toInt() and 0xff) shl 16) or
                            ((bytes[i + 11].toInt() and 0xff) shl 24)
                        Log.i("BinderProbe", "parcel handle=$rawHandle flags=" +
                            ((bytes[i + 4].toInt() and 0xff) or
                                ((bytes[i + 5].toInt() and 0xff) shl 8) or
                                ((bytes[i + 6].toInt() and 0xff) shl 16) or
                                ((bytes[i + 7].toInt() and 0xff) shl 24)))
                        break
                    }
                }
                val rawHandleResult = if (rawHandle >= 0)
                    runCatching { nativeRawHandle(rawHandle, 1, 600) }.getOrElse { -99 }
                else -98
                Log.i("BinderProbe", "connected handle=${service.hashCode()} result=$result native=$native associated=$associated raw=$raw aib=$aib obj=$obj rawHandle=$rawHandleResult")
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
        Log.i("BinderProbe", "execProbe=${runCatching { nativeExecProbe() }.getOrElse { -99 }}")
        Log.i("BinderProbe", "afAlgProbe=${runCatching { nativeAfAlgProbe() }.getOrElse { -99 }}")
        Log.i("BinderProbe", "perfProbe=${runCatching { nativePerfProbe() }.getOrElse { -99 }}")
        startService(Intent(this, WalkerService::class.java))
        startService(Intent(this, VictimService::class.java))
        Log.i("BinderProbe", "binding to ManagerService")
        bindService(Intent(this, ManagerService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        unbindService(connection)
        super.onDestroy()
    }
}
