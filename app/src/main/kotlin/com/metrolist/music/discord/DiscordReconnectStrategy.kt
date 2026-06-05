package com.metrolist.music.discord

sealed interface ReconnectAction {
    data class Resume(val sessionId: String, val seq: Int) : ReconnectAction
    data object ReIdentify : ReconnectAction
    data object RefreshAndReIdentify : ReconnectAction
    data object SurfaceFatal : ReconnectAction
}

object DiscordReconnectStrategy {
    fun decide(
        closeCode: Int,
        hadSession: Boolean,
        seq: Int,
        sessionId: String?,
    ): ReconnectAction = when (closeCode) {
        4000 -> if (hadSession && sessionId != null) {
            ReconnectAction.Resume(sessionId, seq)
        } else {
            ReconnectAction.ReIdentify
        }
        4001 -> ReconnectAction.ReIdentify
        4003 -> ReconnectAction.ReIdentify
        4004 -> ReconnectAction.RefreshAndReIdentify
        4005 -> ReconnectAction.ReIdentify
        4007 -> if (hadSession && sessionId != null) {
            ReconnectAction.Resume(sessionId, seq)
        } else {
            ReconnectAction.ReIdentify
        }
        4009 -> if (hadSession && sessionId != null) {
            ReconnectAction.Resume(sessionId, seq)
        } else {
            ReconnectAction.ReIdentify
        }
        4014 -> ReconnectAction.SurfaceFatal
        else -> ReconnectAction.ReIdentify
    }
}
