package org.spotube.lastfm.spotube_plugin_lastfm.plugin_apis.scrobble

import org.spotube.lastfm.spotube_plugin_lastfm.core.Lastfm
import org.spotube.plugin_interfaces.host_apis.CryptoAPI
import org.spotube.plugin_interfaces.host_apis.HashAlgorithms
import org.spotube.plugin_interfaces.host_apis.HttpMethod
import org.spotube.plugin_interfaces.host_apis.PersistedStorageAPI
import org.spotube.plugin_interfaces.plugin_apis.scrobble.ScrobbleAPI
import org.spotube.plugin_interfaces.plugin_apis.scrobble.ScrobbleTrack
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object LastfmAuthStorageKeys {
    const val API_KEY = "lastfm.api_key"
    const val API_SECRET = "lastfm.api_secret"
    const val SESSION_KEY = "lastfm.session_key"
}

/**
 * Sends playback records to Last.fm using the app credentials embedded at build time and the
 * user's session key persisted by the core/authentication API.
 */
class RealScrobbleAPI(
    private val service: Lastfm,
    private val storage: PersistedStorageAPI,
    private val crypto: CryptoAPI,
) : ScrobbleAPI {
    override suspend fun scrobble(track: ScrobbleTrack) {
        require(track.trackName.isNotBlank()) { "A track title is required to scrobble" }
        require(track.artistName.isNotBlank()) { "An artist name is required to scrobble" }

        val apiKey = LastfmBuildCredentials.apiKey().requiredCredential("API key")
        val apiSecret = LastfmBuildCredentials.apiSecret().requiredCredential("API secret")
        val sessionKey = storage.getString(LastfmAuthStorageKeys.SESSION_KEY).requiredCredential("session key")

        // Last.fm expects timestamps and durations in seconds. ScrobbleTrack.timestamp is already
        // an epoch timestamp, while durationMs is explicitly in milliseconds.
        val parameters = linkedMapOf(
            "method" to "track.scrobble",
            "api_key" to apiKey,
            "sk" to sessionKey,
            "artist" to track.artistName,
            "track" to track.trackName,
            "timestamp" to track.timestamp.toString(),
        )
        track.albumName?.takeIf(String::isNotBlank)?.let { parameters["album"] = it }
        track.durationMs?.takeIf { it > 0 }?.let { parameters["duration"] = (it / 1_000).toString() }

        val signatureInput = parameters.entries.sortedBy { it.key }.joinToString("") { (key, value) ->
            "$key$value"
        } + apiSecret
        val signature = crypto.hash(HashAlgorithms.MD5, signatureInput.encodeToByteArray()).toLowerHex()
        val requestBody = (parameters + ("api_sig" to signature) + ("format" to "json"))
            .entries
            .joinToString("&") { (key, value) -> "${key.formEncode()}=${value.formEncode()}" }

        val response = service.httpClientAPI.request(
            method = HttpMethod.Post,
            url = API_URL,
            requestHeaders = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
            body = requestBody,
        )

        val responseBody = response.body
        if (response.statusCode !in 200..299) {
            throw IllegalStateException(
                "Last.fm scrobble request failed with HTTP ${response.statusCode}" +
                    (responseBody?.takeIf(String::isNotBlank)?.let { ": $it" } ?: ""),
            )
        }

        val apiError = responseBody?.let { lastFmError(it) }
        if (apiError != null) {
            throw IllegalStateException("Last.fm scrobble failed: $apiError")
        }
    }

    private fun String?.requiredCredential(name: String): String =
        this?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Last.fm $name is not configured; log in to Last.fm first")

    private fun lastFmError(body: String): String? = runCatching {
        val json = Json.parseToJsonElement(body).jsonObject
        json["error"]?.jsonPrimitive?.contentOrNull?.let { code ->
            val message = json["message"]?.jsonPrimitive?.contentOrNull
            if (message.isNullOrBlank()) "API error $code" else "API error $code: $message"
        }
    }.getOrNull()

    private fun ByteArray.toLowerHex(): String {
        val digits = "0123456789abcdef"
        return buildString(size * 2) {
            for (byte in this@toLowerHex) {
                val value = byte.toInt() and 0xff
                append(digits[value shr 4])
                append(digits[value and 0x0f])
            }
        }
    }

    private fun String.formEncode(): String {
        val hex = "0123456789ABCDEF"
        return buildString {
            for (byte in encodeToByteArray()) {
                val value = byte.toInt() and 0xff
                if (value in 'a'.code..'z'.code || value in 'A'.code..'Z'.code ||
                    value in '0'.code..'9'.code || value == '-'.code || value == '_'.code ||
                    value == '.'.code || value == '*'.code
                ) {
                    append(value.toChar())
                } else if (value == ' '.code) {
                    append('+')
                } else {
                    append('%')
                    append(hex[value shr 4])
                    append(hex[value and 0x0f])
                }
            }
        }
    }

    private companion object {
        const val API_URL = "https://ws.audioscrobbler.com/2.0/"
    }
}
