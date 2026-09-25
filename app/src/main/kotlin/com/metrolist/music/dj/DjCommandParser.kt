/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.dj

sealed class DjCommand {
    data object Skip : DjCommand()

    data object Previous : DjCommand()

    data object Pause : DjCommand()

    data object Resume : DjCommand()

    data class Play(
        val query: String,
    ) : DjCommand()
}

object DjCommandParser {
    private val wake =
        Regex("""(?i)(?:hey\s+)?dj\s*6\b[,:]?\s*""")

    private fun hasWake(text: String): Boolean =
        wake.containsMatchIn(text) ||
            text.lowercase().contains("dj 6") ||
            text.lowercase().contains("dj6")

    /**
     * @param requireWake when true, ignore utterances that don't address DJ 6
     * (avoids normal conversation / lyrics controlling playback). Live listen uses true.
     */
    fun parse(
        raw: String,
        requireWake: Boolean = false,
    ): DjCommand? {
        val text = raw.trim()
        if (text.isBlank()) return null
        val addressed = hasWake(text)
        if (requireWake && !addressed) return null

        var rest =
            if (addressed) {
                wake.replaceFirst(text, "").trim().ifBlank {
                    text.replace(Regex("""(?i)dj\s*6"""), "").trim()
                }
            } else {
                text
            }
        rest = rest.trim(',', '.', '!', '?')
        if (rest.isBlank()) return null
        val lower = rest.lowercase()

        when {
            lower.matches(Regex("""^(skip|next)(\s+(song|track|one))?$""")) ||
                lower in listOf("skip this", "skip it", "next track") ->
                return DjCommand.Skip

            lower.matches(Regex("""^(previous|prev|back|last)(\s+(song|track|one))?$""")) ||
                lower in listOf("play previous", "play previous song", "go back") ->
                return DjCommand.Previous

            lower.matches(Regex("""^(pause|stop)(\s+(music|playback|please))?$""")) ->
                return DjCommand.Pause

            lower.matches(Regex("""^(resume|continue|unpause|play)$""")) ->
                return DjCommand.Resume
        }

        // "play …" without a wake word is easy to false-trigger from lyrics —
        // only accept bare play when the utterance is short/clear, or always with wake.
        val playMatch =
            Regex("""(?i)^(?:please\s+)?(?:play|put on|queue)\s+(.+)$""").find(rest)
        if (playMatch != null) {
            val query = playMatch.groupValues[1].trim().trimEnd('.', '!', '?')
            val qLower = query.lowercase()
            if (query.isNotBlank() &&
                qLower !in listOf("previous", "previous song", "next", "next song")
            ) {
                if (addressed || query.split(Regex("""\s+""")).size <= 8) {
                    return DjCommand.Play(query)
                }
            }
        }
        return null
    }
}
