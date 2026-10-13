/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.dj

/** Deepgram Flux voices for [deepgram/flux-tts:free] on OpenRouter. */
object DjFluxVoices {
    data class Voice(
        val id: String,
        val label: String,
    )

    val all: List<Voice> =
        listOf(
            Voice("flux-alexis-en", "Alexis (American, F)"),
            Voice("flux-hannah-en", "Hannah (American, F)"),
            Voice("flux-kit-en", "Kit (British, M)"),
            Voice("flux-cliff-en", "Cliff (American, M)"),
            Voice("flux-sienna-en", "Sienna (American, F)"),
            Voice("flux-cole-en", "Cole (American, M)"),
            Voice("flux-brooke-en", "Brooke (American, F)"),
            Voice("flux-colin-en", "Colin (British, M)"),
            Voice("flux-gemma-en", "Gemma (British, F)"),
            Voice("flux-haley-en", "Haley (American, F)"),
            Voice("flux-heather-en", "Heather (American, F)"),
            Voice("flux-miles-en", "Miles (American, M)"),
            Voice("flux-sean-en", "Sean (British, M)"),
            Voice("flux-bree-en", "Bree (American, F)"),
            Voice("flux-brittany-en", "Brittany (American, F)"),
            Voice("flux-bruce-en", "Bruce (American, M)"),
            Voice("flux-conor-en", "Conor (British, M)"),
            Voice("flux-donovan-en", "Donovan (American, M)"),
            Voice("flux-drew-en", "Drew (American, M)"),
            Voice("flux-elise-en", "Elise (American, F)"),
            Voice("flux-jack-en", "Jack (British, M)"),
            Voice("flux-kai-en", "Kai (Singaporean, M)"),
            Voice("flux-kelsey-en", "Kelsey (American, F)"),
            Voice("flux-maeve-en", "Maeve (Irish, F)"),
            Voice("flux-marcelo-en", "Marcelo (Filipino, M)"),
            Voice("flux-marcus-en", "Marcus (American, M)"),
            Voice("flux-meena-en", "Meena (Indian, F)"),
            Voice("flux-meghan-en", "Meghan (American, F)"),
            Voice("flux-naveen-en", "Naveen (Indian, M)"),
            Voice("flux-paige-en", "Paige (American, F)"),
            Voice("flux-priya-en", "Priya (Indian, F)"),
            Voice("flux-rufus-en", "Rufus (British, M)"),
            Voice("flux-sharon-en", "Sharon (Australian, F)"),
            Voice("flux-tanner-en", "Tanner (British, M)"),
            Voice("flux-wade-en", "Wade (American, M)"),
            Voice("flux-wes-en", "Wes (American, M)"),
        )

    fun labelFor(id: String): String = all.firstOrNull { it.id == id }?.label ?: id
}
