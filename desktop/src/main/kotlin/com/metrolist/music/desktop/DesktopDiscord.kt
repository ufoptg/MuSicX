package com.metrolist.music.desktop

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal fun encodeFrame(opcode: Int, payload: String): ByteArray {
    val body = payload.toByteArray(Charsets.UTF_8)
    val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(opcode).putInt(body.size).array()
    return header + body
}

internal fun decodeFrame(bytes: ByteArray): Pair<Int, String> {
    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val opcode = buf.int
    val length = buf.int
    val body = ByteArray(length)
    buf.get(body)
    return opcode to String(body, Charsets.UTF_8)
}

internal fun handshakePayload(clientId: String): String =
    buildJsonObject {
        put("v", 1)
        put("client_id", clientId)
    }.toString()

internal fun setActivityPayload(
    nonce: String,
    pid: Long,
    details: String?,
    state: String?,
): String =
    buildJsonObject {
        put("cmd", "SET_ACTIVITY")
        put("nonce", nonce)
        put(
            "args",
            buildJsonObject {
                put("pid", pid)
                put(
                    "activity",
                    buildJsonObject {
                        if (details != null) put("details", details)
                        if (state != null) put("state", state)
                        put("timestamps", buildJsonObject { put("start", System.currentTimeMillis()) })
                    },
                )
            },
        )
    }.toString()

internal fun clearActivityPayload(nonce: String, pid: Long): String =
    buildJsonObject {
        put("cmd", "SET_ACTIVITY")
        put("nonce", nonce)
        put(
            "args",
            buildJsonObject {
                put("pid", pid)
                put("activity", kotlinx.serialization.json.JsonNull)
            },
        )
    }.toString()

/** Minimal byte transport to the local Discord IPC endpoint (Windows named pipe or Unix socket). */
internal interface IpcTransport {
    fun connect(): Boolean

    fun write(bytes: ByteArray)

    fun readExact(length: Int): ByteArray?

    fun close()
}

internal class PlatformIpcTransport : IpcTransport {
    private var pipe: java.io.RandomAccessFile? = null
    private var channel: java.nio.channels.SocketChannel? = null
    private var input: java.io.InputStream? = null
    private var output: java.io.OutputStream? = null

    override fun connect(): Boolean {
        val isWindows = System.getProperty("os.name").orEmpty().lowercase().contains("win")
        for (index in 0..9) {
            runCatching {
                if (isWindows) {
                    val raf = java.io.RandomAccessFile("\\\\.\\pipe\\discord-ipc-$index", "rw")
                    pipe = raf
                    return true
                } else {
                    val base = System.getenv("XDG_RUNTIME_DIR")?.takeIf { it.isNotBlank() }
                        ?: System.getenv("TMPDIR")?.takeIf { it.isNotBlank() }
                        ?: "/tmp"
                    val ch = java.nio.channels.SocketChannel.open(java.net.UnixDomainSocketAddress.of("$base/discord-ipc-$index"))
                    channel = ch
                    input = java.nio.channels.Channels.newInputStream(ch)
                    output = java.nio.channels.Channels.newOutputStream(ch)
                    return true
                }
            }.onFailure { /* try the next endpoint index */ }
        }
        return false
    }

    override fun write(bytes: ByteArray) {
        pipe?.write(bytes) ?: output?.write(bytes) ?: error("not connected")
    }

    override fun readExact(length: Int): ByteArray? {
        val buffer = ByteArray(length)
        var read = 0
        while (read < length) {
            val n =
                pipe?.read(buffer, read, length - read)
                    ?: input?.read(buffer, read, length - read)
                    ?: return null
            if (n < 0) return null
            read += n
        }
        return buffer
    }

    override fun close() {
        runCatching { pipe?.close() }
        runCatching { channel?.close() }
        pipe = null
        channel = null
        input = null
        output = null
    }
}

/**
 * Best-effort Discord rich presence over the local IPC socket. All failures are logged and
 * swallowed so playback is never affected; [start] must be called before [update].
 */
internal class DesktopDiscord(
    private val appId: String,
    private val showActivityName: Boolean = true,
    private val transport: IpcTransport = PlatformIpcTransport(),
) {
    @Volatile
    var connected: Boolean = false
        private set

    private val lock = Any()

    fun start() {
        synchronized(lock) {
            if (connected) return
            runCatching {
                if (!transport.connect()) {
                    DesktopLog.log("discord: no local Discord IPC endpoint available")
                    return
                }
                transport.write(encodeFrame(0, handshakePayload(appId)))
                val header = transport.readExact(8) ?: error("discord: no handshake reply")
                val buf = java.nio.ByteBuffer.wrap(header).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                buf.int // opcode
                val length = buf.int
                transport.readExact(length)
                connected = true
            }.onFailure {
                DesktopLog.log("discord: start failed", it)
                connected = false
                transport.close()
            }
        }
    }

    fun update(title: String, artist: String?) {
        send(setActivityPayload(nonce(), pid(), title, if (showActivityName) artist else null))
    }

    fun clear() {
        if (connected) send(clearActivityPayload(nonce(), pid()))
    }

    fun stop() {
        synchronized(lock) {
            runCatching { clear() }
            runCatching { transport.close() }
            connected = false
        }
    }

    private fun send(payload: String) {
        synchronized(lock) {
            if (!connected) return
            runCatching { transport.write(encodeFrame(1, payload)) }.onFailure {
                DesktopLog.log("discord: send failed", it)
                connected = false
                transport.close()
            }
        }
    }

    private fun nonce() = java.util.UUID.randomUUID().toString()

    private fun pid() = ProcessHandle.current().pid()
}

