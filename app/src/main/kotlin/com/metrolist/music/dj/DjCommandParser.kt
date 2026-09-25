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

    /**
     * Only returns a command when the utterance addresses DJ 6.
     * Ignores song lyrics / background speech without the wake phrase.
     */
    fun parse(raw: String): DjCommand? {
        val text = raw.trim()
        if (text.isBlank()) return null
        if (!wake.containsMatchIn(text) && !text.matches(Regex("""(?i)^dj\s*6\b.*"""))) {
            // Also accept if wake is at start after strip
            if (!text.lowercase().contains("dj 6") && !text.lowercase().contains("dj6")) {
                return null
            }
        }
        var rest = wake.replaceFirst(text, "").trim()
        if (rest.isBlank()) rest = text.replace(Regex("""(?i)dj\s*6"""), "").trim()
        rest = rest.trim(',', '.', '!', '?')
        val lower = rest.lowercase()

        when {
            lower.matches(Regex("""^(skip|next)(\s+(song|track|one))?$""")) ||
                lower in listOf("skip song", "next song", "skip this", "next track") ->
                return DjCommand.Skip

            lower.matches(Regex("""^(previous|prev|back|last)(\s+(song|track|one))?$""")) ||
                lower in listOf("play previous", "play previous song", "go back") ->
                return DjCommand.Previous

            lower.matches(Regex("""^(pause|stop)(\s+(music|playback))?$""")) ->
                return DjCommand.Pause

            lower.matches(Regex("""^(resume|continue|unpause|play)$""")) ->
                return DjCommand.Resume
        }

        val playMatch =
            Regex("""(?i)^(?:please\s+)?(?:play|put on|queue)\s+(.+)$""").find(rest)
        if (playMatch != null) {
            val query = playMatch.groupValues[1].trim().trimEnd('.', '!', '?')
            if (query.isNotBlank() &&
                query.lowercase() !in listOf("previous", "previous song", "next", "next song")
            ) {
                return DjCommand.Play(query)
            }
        }
        return null
    }
}
