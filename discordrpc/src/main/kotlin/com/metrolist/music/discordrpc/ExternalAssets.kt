package com.metrolist.music.discordrpc

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

@Serializable
private data class ExternalAssetResponse(
    @SerialName("url")
    val url: String? = null,
    @SerialName("external_asset_path")
    val externalAssetPath: String? = null,
)

suspend fun fetchExternalAsset(
    client: HttpClient,
    applicationId: String,
    token: String,
    imageUrl: String,
    userAgent: String,
    superPropertiesBase64: String? = null,
): String? {
    if (imageUrl.startsWith("mp:")) return imageUrl
    val api = "https://discord.com/api/v10/applications/$applicationId/external-assets"
    Timber.tag("ExtAssets").d("Uploading external asset: $imageUrl")
    return runCatching {
        val response = client.post(api) {
            header("Authorization", token)
            header("User-Agent", userAgent)
            if (superPropertiesBase64 != null) header("X-Super-Properties", superPropertiesBase64)
            contentType(ContentType.Application.Json)
            setBody("""{"urls":["$imageUrl"]}""")
        }
        val text = response.body<String>()
        val json = Json { ignoreUnknownKeys = true }
        val list = json.decodeFromString<List<ExternalAssetResponse>>(text)
        val result = list.firstOrNull()?.externalAssetPath?.let { "mp:$it" }
        if (result != null) {
            Timber.tag("ExtAssets").i("Asset uploaded: $imageUrl -> $result")
        } else {
            Timber.tag("ExtAssets").w("Asset upload returned no path: $text")
        }
        result
    }.getOrElse {
        Timber.tag("ExtAssets").e(it, "Asset upload failed for: $imageUrl")
        null
    }
}
