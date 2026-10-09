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
