package dev.musicx.shared

/** Sideloading changes bundle IDs and drops version suffixes, so neither identifies the release. */
fun iosSentryRelease(): String = "dev.musicx.ios@${BuildConfig.APP_VERSION}"

fun iosSentrySourceRevision(): String = BuildConfig.SOURCE_REVISION
