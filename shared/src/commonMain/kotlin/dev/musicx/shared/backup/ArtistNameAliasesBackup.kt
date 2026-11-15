package dev.musicx.shared.backup

import dev.musicx.shared.constants.ArtistNameAliasesJsonKey
import dev.musicx.shared.settings.ArtistNameAliases
import dev.musicx.shared.settings.PortableSetting
import dev.musicx.shared.settings.PortableSettings
import dev.musicx.shared.settings.SettingType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun PortableSettings?.withLegacyArtistNameAliases(serialized: String): PortableSettings {
    val aliases = ArtistNameAliases.deserialize(serialized)
    val settings = this ?: PortableSettings(entries = emptyList())
    return settings.copy(
        entries =
            settings.entries.filterNot { it.key == ArtistNameAliasesJsonKey } +
                PortableSetting(ArtistNameAliasesJsonKey, SettingType.STRING, Json.encodeToString(aliases)),
    )
}
