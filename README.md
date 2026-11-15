# MuSicX 15

A Material Design YouTube Music client for Android.

[![Stable APK](assets/badges/platform/android-stable.svg)](https://github.com/ufoptg/MuSicX/releases/latest) [![Nightly APK](assets/badges/platform/android-nightly.svg)](https://github.com/ufoptg/MuSicX/releases?q=nightly)

## Introduction

MuSicX 15 is a fresh start of the MuSicX line, rebuilt on the Metrolist v14 Kotlin Multiplatform codebase. It is Android-only for 15.0 and carries the features that made MuSicX distinct — full Spotify integration, Qobuz hi-res streaming, Playlist Enhance, and Discover Something New — alongside the complete YouTube Music experience.

MuSicX 15 installs as `dev.musicx`: a fresh install that coexists with MuSicX v13. Data does not migrate automatically, and the APK signature differs, so v13 cannot update in place.

## Install

Android 7.0+.

- [Stable APK](https://github.com/ufoptg/MuSicX/releases/latest/download/MuSicX.apk)
- [Nightly pre-release](https://github.com/ufoptg/MuSicX/releases?q=nightly) (tag `nightly`; older v13 nightlies are archived under `nightly-v13`)

## Features

- Blazingly fast interruption-free YouTube Music playback
- YouTube Music login support; logged-out users can also fully enjoy the app
- Full Spotify integration: login, Home, Library, Search, track menus, and playback of playlists, albums and liked songs
- Qobuz hi-res streaming with quality ladder, backend selection and ISRC matching
- Playlist Enhance: interleaved recommendations across Spotify, online YouTube Music and local playlists
- Discover Something New: Home row, Library tile and browsable list
- **Material 3 Expressive** design
- **Discord Rich Presence**, scrobbling thru **Last.fm and Listenbrainz**
- **No ads**, ever
- Listen Together with other people online in real-time
- Casting to **Google Cast**, F-Cast, SONOS devices, and over DLNA
- Importing backups/playlists from 29+ other clients and 6 different services
- Music recognition thru **Shazam**
- Fast custom lyrics provider, lyrics translations and romanization support
- Translations in over 15 languages
- **Video playback** support
- Extensive content filtering
- **Canvas from Apple Music**, Spotify, and YouTube Music
- Multiple player, thumbnail, miniplayer, and background types to choose from
- Custom font and app icon support

## Build and CI

Builds are gated by GitHub Actions on standard Ubuntu runners: `.github/workflows/musicx-v15.yml` builds the debug APK and `.github/workflows/nightly.yml` builds the signed nightly release. See [docs/development.md](docs/development.md) for local setup.

## License

GPL-3.0. See [LICENSE](LICENSE).

## Credits

Based on [Metrolist](https://github.com/MetrolistGroup/Metrolist) and continuing the MuSicX v13 fork lineage.
