package cc.spotube.lastfm.spotube_plugin_lastfm.plugin_apis.scrobble

import cc.spotube.lastfm.spotube_plugin_lastfm.core.Lastfm
import dev.krtirtho.plugin_interfaces.host_apis.CryptoAPI
import dev.krtirtho.plugin_interfaces.host_apis.HashAlgorithms
import dev.krtirtho.plugin_interfaces.host_apis.HttpMethod
import dev.krtirtho.plugin_interfaces.host_apis.PersistedStorageAPI
import dev.krtirtho.plugin_interfaces.host_apis.WebViewAPI
import dev.krtirtho.plugin_interfaces.plugin_apis.core.CoreAPI
import dev.krtirtho.plugin_interfaces.plugin_apis.core.PluginUpdateInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.swiftzer.semver.SemVer

class RealCoreAPI(
    private val webViewAPI: WebViewAPI,
    private val storage: PersistedStorageAPI,
    private val service: Lastfm,
    private val crypto: CryptoAPI,
) : CoreAPI {
    override val requiresAuthentication: Boolean = true

    private val _loggedInFlow = MutableStateFlow(false)
    override val loggedInFlow: StateFlow<Boolean> = _loggedInFlow.asStateFlow()

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            _loggedInFlow.value = hasStoredSession()
        }
    }

    override suspend fun checkPluginUpdates(currentVersion: SemVer): PluginUpdateInfo? =
        null

    override fun supportMarkdownText(currentVersion: SemVer): String =
        "This plugin sends scrobbles to Last.fm. A Last.fm account and Last.fm API credentials are required."

    override suspend fun login() = coroutineScope {
        val apiKey = LastfmBuildCredentials.apiKey()
        val apiSecret = LastfmBuildCredentials.apiSecret()
        check(apiKey.isNotBlank() && apiSecret.isNotBlank()) {
            "Last.fm API credentials are not configured. Set LASTFM_API_KEY and LASTFM_API_SECRET in local.properties, then rebuild the plugin."
        }

        val submissions = Channel<String>(Channel.UNLIMITED)
        val webViewMessages = launch(start = CoroutineStart.UNDISPATCHED) {
            webViewAPI.webviewCreatedFlow().first()
            webViewAPI.postMessagesFlow().collect { message -> submissions.send(message) }
        }

        try {
            webViewAPI.navigateToHTML(LASTFM_LOGIN_PAGE)
            while (true) {
                val message = submissions.receive()
                val credentials = runCatching { Json.parseToJsonElement(message).jsonObject }
                    .getOrNull()
                    ?: continue
                if (credentials["type"]?.jsonPrimitive?.contentOrNull != LOGIN_MESSAGE_TYPE) continue

                try {
                    val username = credentials.requiredText("username")
                    val password = credentials.requiredText("password")

                    val sessionKey = requestMobileSession(username, password, apiKey, apiSecret)
                    webViewAPI.evaluateJavaScript("document.getElementById('password').value = ''")
                    storage.putString(LastfmAuthStorageKeys.SESSION_KEY, sessionKey)
                    _loggedInFlow.value = true
                    return@coroutineScope
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    val messageText = error.message ?: "Unable to sign in to Last.fm"
                    webViewAPI.evaluateJavaScript("window.showLoginError(${messageText.asJavaScriptString()})")
                }
            }
        } finally {
            webViewMessages.cancel()
            submissions.cancel()
            webViewAPI.exitWebView()
        }
    }

    override suspend fun logout() {
        storage.remove(LastfmAuthStorageKeys.SESSION_KEY)
        _loggedInFlow.value = false
    }

    private suspend fun hasStoredSession(): Boolean {
        // Remove API credentials saved by older plugin versions; they're now build-time config.
        storage.remove(LastfmAuthStorageKeys.API_KEY)
        storage.remove(LastfmAuthStorageKeys.API_SECRET)
        return !storage.getString(LastfmAuthStorageKeys.SESSION_KEY).isNullOrBlank()
    }

    private suspend fun requestMobileSession(
        username: String,
        password: String,
        apiKey: String,
        apiSecret: String,
    ): String {
        val parameters = linkedMapOf(
            "method" to "auth.getMobileSession",
            "api_key" to apiKey,
            "username" to username,
            "password" to password,
        )
        val signatureInput = parameters.entries.sortedBy { it.key }.joinToString("") { (key, value) ->
            "$key$value"
        } + apiSecret
        val signature = crypto.hash(HashAlgorithms.MD5, signatureInput.encodeToByteArray()).toLowerHex()
        val body = (parameters + ("api_sig" to signature) + ("format" to "json"))
            .entries
            .joinToString("&") { (key, value) -> "${key.formEncode()}=${value.formEncode()}" }

        val response = service.httpClientAPI.request(
            method = HttpMethod.Post,
            url = LASTFM_API_URL,
            requestHeaders = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
            body = body,
        )
        val responseBody = response.body.orEmpty()
        check(response.statusCode in 200..299) {
            "Last.fm sign-in failed with HTTP ${response.statusCode}"
        }
        val json = Json.parseToJsonElement(responseBody).jsonObject
        val apiError = json["error"]?.jsonPrimitive?.contentOrNull
        if (apiError != null) {
            val message = json["message"]?.jsonPrimitive?.contentOrNull ?: "Authentication failed"
            error("Last.fm error $apiError: $message")
        }
        return json["session"]?.jsonObject?.get("key")?.jsonPrimitive?.contentOrNull
            ?.takeIf(String::isNotBlank)
            ?: error("Last.fm did not return a session key")
    }

    private fun kotlinx.serialization.json.JsonObject.requiredText(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
            ?: error("Please provide your Last.fm $key")

    private fun String.asJavaScriptString(): String = buildString {
        append('"')
        for (character in this@asJavaScriptString) {
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(character)
            }
        }
        append('"')
    }

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
        const val LASTFM_API_URL = "https://ws.audioscrobbler.com/2.0/"
        const val LOGIN_MESSAGE_TYPE = "lastfm-login"
    }
}
