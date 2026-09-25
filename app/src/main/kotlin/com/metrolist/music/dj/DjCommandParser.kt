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
    /** Wake must include 6: “DJ 6 …”, “dj6 …”, “hey DJ 6 …”. Plain “DJ …” does not match. */
    private val wake =
        Regex("""(?i)(?:hey\s+)?dj\s*6\b[,:]?\s*""")

    private val wakeOnly =
        Regex("""(?i)^(?:hey\s+)?dj\s*6\b[,.!?]*$""")

    private val fillers =
        Regex("""(?i)^(please|uh|um|erm|okay|ok|so)?$""")

    /**
     * Vosk / speech often hears the digit as the word “six”.
     */
    fun normalizeSpoken(raw: String): String =
        raw
            .trim()
            .lowercase()
            .replace(Regex("""\bdee\s*jay\b"""), "dj")
            .replace(Regex("""\bd\s*j\b"""), "dj")
            .replace(Regex("""\bdj\s*six\b"""), "dj 6")
            .replace(Regex("""\bdj6\b"""), "dj 6")
            // Common STT mishearings for command words
            .replace(Regex("""\bscap\b|\bskit\b|\bscip\b"""), "skip")
            .replace(Regex("""\bnecks\b|\bnex\b"""), "next")
            .replace(Regex("""\bprevi?ous\b|\bprevous\b"""), "previous")
            .replace(Regex("""\bpaws\b|\bpors\b"""), "pause")
            .replace(Regex("""\bresum\b|\bresume play\b"""), "resume")
            .replace(Regex("""\s+"""), " ")
            .trim()

    fun containsWake(raw: String): Boolean = wake.containsMatchIn(normalizeSpoken(raw))

    fun stripWake(raw: String): String {
        val text = normalizeSpoken(raw)
        return wake.replaceFirst(text, "").trim().trim(',', '.', '!', '?')
    }

    fun isWakeOnly(raw: String): Boolean {
        val text = normalizeSpoken(raw)
        if (wakeOnly.matches(text)) return true
        if (!containsWake(text)) return false
        val rest = stripWake(text)
        return rest.isBlank() || fillers.matches(rest)
    }

    private fun hasWake(text: String): Boolean = wake.containsMatchIn(text)

    /**
     * @param requireWake when true, only utterances that address “DJ 6” are accepted.
     */
    fun parse(
        raw: String,
        requireWake: Boolean = true,
    ): DjCommand? {
        val text = normalizeSpoken(raw)
        if (text.isBlank()) return null
        val addressed = hasWake(text)
        if (requireWake && !addressed) return null

        var rest =
            if (addressed) {
                stripWake(text)
            } else {
                text
            }
        rest = rest.trim(',', '.', '!', '?')
        if (rest.isBlank()) return null
        val lower = rest.lowercase()

        when {
            lower.matches(Regex("""^(skip|next)(\s+(song|track|one|please))?$""")) ||
                lower.startsWith("skip") ||
                lower.startsWith("next") ||
                lower in listOf("skip this", "skip it", "next track", "next one", "forward") ->
                return DjCommand.Skip

            lower.matches(Regex("""^(previous|prev|back|last)(\s+(song|track|one|please))?$""")) ||
                lower.startsWith("previous") ||
                lower.startsWith("prev") ||
                lower in listOf("play previous", "play previous song", "go back", "go back one") ->
                return DjCommand.Previous

            lower.matches(Regex("""^(pause|stop)(\s+(music|playback|please|song))?$""")) ||
                lower.startsWith("pause") ||
                lower == "stop" ->
                return DjCommand.Pause

            lower.matches(Regex("""^(resume|continue|unpause|start)(\s+(music|playback|please))?$""")) ||
                lower in listOf("resume", "continue", "unpause", "play", "start") ->
                return DjCommand.Resume
        }

        val playMatch =
            Regex("""(?i)^(?:please\s+)?(?:play|put on|queue)\s+(.+)$""").find(rest)
        if (playMatch != null) {
            val query = playMatch.groupValues[1].trim().trimEnd('.', '!', '?')
            val qLower = query.lowercase()
            if (query.isNotBlank() &&
                qLower !in listOf("previous", "previous song", "next", "next song")
            ) {
                return DjCommand.Play(query)
            }
        }
        return null
    }
}
