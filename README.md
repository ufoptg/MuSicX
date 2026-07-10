<div align="center">

<img src="fastlane/metadata/android/en-US/images/icon.png" alt="MuSicX app icon" width="200" />

# MuSicX

### YouTube Music client with Spotify integration & FLAC hi-res streaming for Android — supercharged.

<br/>

[![Latest release](https://img.shields.io/github/v/release/ufoptg/MuSicX?style=for-the-badge&labelColor=0d1117)](https://github.com/ufoptg/MuSicX/releases)
[![License](https://img.shields.io/github/license/ufoptg/MuSicX?style=for-the-badge)](https://github.com/ufoptg/MuSicX/blob/main/LICENSE)
[![Downloads](https://img.shields.io/github/downloads/ufoptg/MuSicX/total?style=for-the-badge)](https://github.com/ufoptg/MuSicX/releases)
[![Buy Me a Coffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-ffdd00?style=for-the-badge&logo=buy-me-a-coffee&logoColor=black)](https://buymeacoffee.com/TrueSaiyan)
<br/>

[**Download**](#download-now) · [**Features**](#features) · [**What's New**](#whats-new-in-musicx) · [**Roadmap**](#roadmap) · [**FAQ**](#faq)

</div>

> [!NOTE]
> **MuSicX** is a maintained fork of [Metrolist](https://github.com/MetrolistGroup/Metrolist) with additional integrations (Spotify, SponsorBlock, Music Recognition, Podcasts, LyricsPlus, and experimental FLAC / Hi-Res streaming via Qobuz), crash reporting, and a hardened playback pipeline. Same great UX, more music sources, more resilience.

> [!WARNING]
> **Regional Restriction** — If YouTube Music is unavailable in your region, this app will not work without a **VPN or proxy** connecting to a supported region.

---

<div align="center">

<h1><a id="screenshots"></a>Screenshots</h1>

<img src="fastlane/metadata/android/en-US/images/screenshots/screenshot_1.png" alt="Home screen" width="30%" />
<img src="fastlane/metadata/android/en-US/images/screenshots/screenshot_2.png" alt="Artist screen" width="30%" />
<img src="fastlane/metadata/android/en-US/images/screenshots/screenshot_3.png" alt="Recognize music screen" width="30%" />
<img src="fastlane/metadata/android/en-US/images/screenshots/screenshot_4.png" alt="Listen together screen" width="30%" />
<img src="fastlane/metadata/android/en-US/images/screenshots/screenshot_5.png" alt="Player screen" width="30%" />
<img src="fastlane/metadata/android/en-US/images/screenshots/screenshot_6.png" alt="Player lyrics screen" width="30%" />

</div>

---

<div align="center">

<h1><a id="whats-new-in-musicx"></a>What's New in MuSicX</h1>

Features added on top of Metrolist upstream:

</div>

| Feature | Status | Notes |
|---|---|---|
| 🟢 **Spotify integration** | Shipped | Log in with your own Spotify account via in-app WebView (uses `sp_dc` cookie — no client secret needed). Home, Library, Search & Now-Playing hooks bridge tracks to YouTube Music equivalents. |
| 🟢 **SponsorBlock** | Shipped | Auto-skip sponsor segments and non-music intros/outros in videos, powered by the [SponsorBlock](https://sponsor.ajay.app) community API. Settings live under Player Settings → Misc. |
| 🟢 **Crash reporting to GitHub Issues** | Shipped | Unhandled crashes are packaged (device info + sanitized stacktrace) and opened as GitHub Issues automatically, so bugs never get lost. |
| 🟢 **ANR Watchdog** | Shipped | Detects UI freezes ≥ 5s and captures a stack dump for diagnostics — inspired by meld. |
| 🟢 **Recently Played + Continue Listening** | Shipped v13.6.9 | Native local Recently Played row on Home (fed by `database.events()`), pinned directly under Spotify's *Your Top Tracks*. New **Continue Listening** hero card at the very top of Home for one-tap resume of the last track. |
| 🟢 **LyricsPlus / ExperimentalLyrics** | Shipped | Fluid karaoke-style synced lyrics with word-level timing, translation-friendly rendering, and better lifecycle awareness than the original meld implementation. Toggle: *Settings → Content → Enable LyricsPlus* + *Settings → Appearance → Experimental Lyrics*. |
| 🟢 **Music-Recognition Widget** (Shazam) | Shipped | Home-screen widget + Quick Settings tile for instant Shazam-style recognition. Full recognition screen at *Library → Recognize Music*. No API key required. |
| 🟢 **Podcasts** | Shipped | Full podcast browsing, subscriptions, episode player, and library sync. Discover via *Search → Podcasts chip*, *Home → Podcasts chip*, or manage subscriptions at *Library → Podcasts*. |
| 🟢 **Qobuz hi-res streaming** | Shipped v13.8.0 | Experimental FLAC / Hi-Res playback via third-party Qobuz resolvers (Monokenny / Jumo / Squid / TrypT HiFi). AAC 320 → CD (16-bit / 44.1 kHz) → Hi-Res (up to 24-bit / 192 kHz). Falls back silently to YouTube on failure. Uses Spotify ISRC when available for tighter matching. Toggle at *Settings → Spotify Integration → Audio quality (experimental)*. Country code editor + live Backend Status section added in v13.8.2. R8/release-build playback regression fixed in v13.8.3. |
| 🟢 **Instant playback from Spotify home** | Shipped v13.8.4 | The Spotify personalized-radio queue used to run its entire recommendation engine synchronously before the first note played — tap-to-play latency was 4–6 s. Now the engine is deferred to `nextPage()` and runs on a background coroutine after audio is already playing. Tap-to-play drops to ~100–500 ms warm / ~1 s cold. |
| 🟢 **Spotify Liked Songs queue depth + Shuffle** | Shipped v13.8.5 / v13.8.6 | Playing a big Liked Songs list used to cap the queue at 23 songs — shuffle only randomised those 23. Now the queue accepts the ViewModel's already-loaded track list, and `MusicService` grows the queue to 60 items in the background *after* audio starts (5-item fast-start window so Shuffle stays near-instant). New **Shuffle button** on the Liked Songs screen ships a pre-shuffled ordering so the shuffle randomises across every loaded track. |
| 🟢 **Shuffle button on every playlist screen** ✨ new | Shipped v13.8.7 | The v13.8.5 "pre-shuffled backing list" trick is now on Spotify user playlists, YouTube Music playlists, Spotify albums, and every local playlist (Liked / Auto / Top / Cache / user-created). Shuffle randomises across all loaded tracks — not just the fast-start window ExoPlayer's shuffle toggle would randomise mid-playback. |
| 🟢 **Listen Together — orbs artwork** ✨ new | Shipped v13.8.7 | The Listen Together screen header now uses a dedicated three-orbs artwork (`R.drawable.listen_together_orbs`, 17 KB WebP) instead of the plain two-people icon. |

<br/>

<div align="center">

<h1><a id="roadmap"></a>Roadmap — Coming Soon</h1>

Features currently being ported from [meld](https://github.com/AudreyProject/meld):

</div>

| Planned Feature | Priority | Description |
|---|---|---|
| 🎨 **New Player Design** | P2 — Next | Redesigned Now-Playing screen with animated blur backdrops, refined gesture handling, and minimal / immersive variants. |
| 🌐 **Wrapped / Year-in-review** | P3 | End-of-year listening summary — top tracks, top artists, listening minutes, shareable card exports. |
| 🎧 **Cross-device queue sync** | P3 | Sync the current queue and playback position to other MuSicX installs via Emergent-managed cloud (or self-hosted). |
| 🌍 **Better offline mode** | P3 | Smart queue pre-fetch, richer download management, per-playlist auto-download rules. |

> Want a feature bumped? Open an issue or vote on existing ones.

---

<div align="center">

<h1><a id="features"></a>Full Feature List</h1>

<table>
  <tr>
    <td width="50%" valign="top">

#### Playback
- Stream any song or video from YouTube Music
- Background playback
- Download & cache for offline use
- Skip silence
- Sleep timer
- **SponsorBlock — skip sponsor/intro segments** ✨
- **Qobuz FLAC / Hi-Res streaming** ✨
- **Continue Listening hero card** on Home ✨
- **Shuffle across every loaded track** on all playlist screens ✨ new

</td>
    <td width="50%" valign="top">

#### Audio
- Audio normalization
- Tempo & pitch control
- Equalizer
- **Lossless FLAC 16-bit / 44.1 kHz (CD)** ✨
- **Hi-Res FLAC up to 24-bit / 192 kHz** ✨

</td>
  </tr>
  <tr>
    <td width="50%" valign="top">

#### Lyrics & Discovery
- Live synced lyrics
- **LyricsPlus / Experimental Lyrics — word-timed karaoke** ✨
- AI-powered lyrics translation
- Personalized quick picks
- **Local Recently Played row** on Home ✨
- **Music Recognition (Shazam-style)** — home widget + Quick Settings tile ✨
- Search songs, albums, artists, videos, playlists, **podcasts & episodes** ✨

</td>
    <td width="50%" valign="top">

#### Library & Account
- Full library management
- Local playlists
- Import playlists
- Reorder songs in playlist or queue
- YouTube Music account login
- **Spotify login — sync liked songs, playlists & recent tracks** ✨
- **Podcasts library — episodes + channels** ✨
- Sync songs, artists, albums, and playlists

</td>
  </tr>
  <tr>
    <td width="50%" valign="top">

#### Social
- Listen together with friends in real-time

</td>
    <td width="50%" valign="top">

#### Interface
- Home screen widget
- Light / Dark / Black / Dynamic theme modes
- Dynamic color + 19 preset color palettes
- Built with Material 3

</td>
  </tr>
  <tr>
    <td width="50%" valign="top">

#### Reliability ✨
- **ANR Watchdog** — auto-detects UI freezes
- **Crash reporter** — one-tap submit to GitHub Issues
- **R8-safe Qobuz code path** — v13.8.3 hardened the resolver so a Qobuz failure can never abort the ExoPlayer callback and kill playback

</td>
    <td width="50%" valign="top">

#### Coming Soon
- New Player Design (P2)
- Wrapped / Year-in-review (P3)
- Cross-device queue sync (P3)
- Smarter offline mode (P3)

</td>
  </tr>
</table>

</div>

---

<div align="center">

<h1><a id="download-now"></a>Download Now</h1>

<h2>Stable Release</h2>

<table>
  <tr>
    <th align="center">GitHub</th>
    <th align="center">Obtainium</th>
  </tr>
  <tr>
    <td align="center">
      <a href="https://github.com/ufoptg/MuSicX/releases/latest/download/MuSicX.apk">
        <img src="https://github.com/machiav3lli/oandbackupx/blob/034b226cea5c1b30eb4f6a6f313e4dadcbb0ece4/badge_github.png" alt="Download from GitHub" height="60">
      </a>
    </td>
    <td align="center">
      <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/ufoptg/MuSicX/">
        <img src="https://github.com/ImranR98/Obtainium/blob/main/assets/graphics/badge_obtainium.png" alt="Download from Obtainium" height="40">
      </a>
    </td>
  </tr>
</table>

<h2>Nightly Build</h2>

<table>
  <tr>
    <th align="center">GitHub Nightly (foss)</th>
    <th align="center">GitHub Nightly (with Google Cast)</th>
  </tr>
  <tr>
    <td align="center">
      <a href="https://nightly.link/ufoptg/MuSicX/workflows/build/main/MuSicX-Nightly.zip">
        <img src="https://github.com/machiav3lli/oandbackupx/blob/034b226cea5c1b30eb4f6a6f313e4dadcbb0ece4/badge_github.png" alt="MuSicX Nightly" height="60">
      </a>
    </td>
    <td align="center">
      <a href="https://nightly.link/ufoptg/MuSicX/workflows/build/main/MuSicX-Nightly-with-Google-Cast.zip">
        <img src="https://github.com/machiav3lli/oandbackupx/blob/034b226cea5c1b30eb4f6a6f313e4dadcbb0ece4/badge_github.png" alt="MuSicX Nightly (Cast)" height="60">
      </a>
    </td>
  </tr>
</table>

</div>

---

<div align="center">

<h1><a id="faq"></a>FAQ</h1>

<details>
<summary><strong>Is my Spotify password sent anywhere?</strong></summary>
No. Spotify login happens in an in-app WebView pointing to Spotify's real login page. MuSicX only captures the `sp_dc` cookie locally to call the Spotify Web API on your behalf. Nothing leaves your device except direct Spotify API calls.
</details>

<details>
<summary><strong>What data does the crash reporter send?</strong></summary>
Only when a crash occurs and you tap "Report": sanitized stacktrace, app version, Android version, device model. No account tokens, no personal data.
</details>

<details>
<summary><strong>Is Qobuz hi-res streaming free?</strong></summary>
Yes — MuSicX resolves tracks via public third-party resolver services (Monokenny / Jumo / Squid / TrypT HiFi). No Qobuz subscription or account needed on your side. That said, these are community-run services that can go offline at any time; MuSicX automatically falls back to standard YouTube playback whenever a resolver fails or a track isn't on Qobuz. It's marked *experimental* for that reason.
</details>

<details>
<summary><strong>How much extra data does lossless use?</strong></summary>
Roughly 3–10× a standard YouTube AAC stream. CD-quality FLAC is ~1 MB per minute; Hi-Res 24-bit / 192 kHz can hit ~6 MB per minute. If you're on mobile data, either stick to AAC 320 in the Qobuz settings or leave Qobuz off.
</details>

<details>
<summary><strong>How do I find podcasts?</strong></summary>
Three ways: (1) <strong>Search</strong> → type any podcast name → tap the "Podcasts" chip in the results chip row; (2) <strong>Home</strong> → tap the "Podcasts" chip in the top chip row for recommendations; (3) <strong>Library → Podcasts</strong> for shows you've already subscribed to (Episodes / Channels tabs).
</details>

<details>
<summary><strong>How is MuSicX different from Metrolist?</strong></summary>
MuSicX is a fork that adds: full Spotify integration (login, home, library, search bridging), SponsorBlock, ANR watchdog, GitHub-issue crash reporting, Music Recognition (Shazam-style widget + Quick Settings tile), Podcasts library, LyricsPlus/ExperimentalLyrics with word-timed karaoke, and now Qobuz hi-res streaming (experimental). Upstream Metrolist commits are automatically synced on top, so no fixes get lost.
</details>

</div>

---

<div align="center">

<h1>Special Thanks</h1>

<h3>MuSicX stands on the shoulders of incredible open-source work.</h3>

<h3>Main Inspirations</h3>

<table>
  <thead>
    <tr>
      <th align="center">Project</th>
      <th align="center">Authors</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td align="center"><strong><a href="https://github.com/MetrolistGroup/Metrolist">Metrolist</a></strong></td>
      <td align="center"><a href="https://github.com/mostafaalagamy">Mo Agamy</a> — the upstream base of MuSicX</td>
    </tr>
    <tr>
      <td align="center"><strong><a href="https://github.com/AudreyProject/meld">meld</a></strong></td>
      <td align="center">Feature ports (Spotify hooks, SponsorBlock, ANR, CrashReporter, Music Recognition, Podcasts, LyricsPlus, Qobuz)</td>
    </tr>
    <tr>
      <td align="center"><strong>InnerTune</strong></td>
      <td align="center"><a href="https://github.com/z-huang">Zion Huang</a> · <a href="https://github.com/Malopieds">Malopieds</a></td>
    </tr>
    <tr>
      <td align="center"><strong>OuterTune</strong></td>
      <td align="center"><a href="https://github.com/DD3Boh">Davide Garberi</a> · <a href="https://github.com/mikooomich">Michael Zh</a></td>
    </tr>
  </tbody>
</table>

<h3>Libraries & Integrations</h3>

<table>
  <thead>
    <tr>
      <th align="center">Project</th>
      <th align="center">Contribution</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td align="center"><a href="https://sponsor.ajay.app"><strong>SponsorBlock</strong></a></td>
      <td>Crowdsourced sponsor / intro / outro segment skipping</td>
    </tr>
    <tr>
      <td align="center"><a href="https://better-lyrics.boidu.dev"><strong>Better Lyrics</strong></a></td>
      <td>Time-synced lyrics with word-by-word highlighting & YouTube Music integration</td>
    </tr>
    <tr>
      <td align="center"><a href="https://github.com/aleksey-saenko/MusicRecognizer"><strong>MusicRecognizer</strong></a></td>
      <td>Shazam-style audio recognition — home widget + Quick Settings tile</td>
    </tr>
    <tr>
      <td align="center"><strong>Qobuz resolver services</strong></td>
      <td>Monokenny / Jumo / Squid / TrypT HiFi — community-run public endpoints powering the experimental FLAC / Hi-Res streaming</td>
    </tr>
    <tr>
      <td align="center"><a href="https://github.com/Spotube/Spotube"><strong>Spotube</strong></a></td>
      <td>Inspiration for the cookie-based Spotify auth flow</td>
    </tr>
    <tr>
      <td align="center"><a href="https://github.com/ZemerTeam/zemer-cipher"><strong>zemer-cipher</strong></a></td>
      <td>YouTube cipher deobfuscation and PoToken generation</td>
    </tr>
  </tbody>
</table>

<h3>We also thank the entire open-source community — for every library, tool, and API that powers this project.</h3>

</div>

---

<div align="center">

<h1>Contributors</h1>

<a href="https://github.com/ufoptg/MuSicX/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=ufoptg/MuSicX" alt="Contributors" />
</a>

</div>

---

<div align="center">

<h1>Disclaimer</h1>

This project is **not affiliated with, funded, authorized, endorsed by, or in any way associated** with YouTube, Google LLC, Spotify AB, Qobuz, Metrolist Group LLC, meld, or any of their affiliates and subsidiaries.

All trademarks, service marks, and intellectual property rights referenced in this project belong to their respective owners.

</div>

---

<div align="center">

<br/>

**MuSicX — maintained by [ufoptg](https://github.com/ufoptg)**
**Built on the shoulders of [Metrolist](https://github.com/MetrolistGroup/Metrolist) by [Mo Agamy](https://github.com/mostafaalagamy)**

</div>
