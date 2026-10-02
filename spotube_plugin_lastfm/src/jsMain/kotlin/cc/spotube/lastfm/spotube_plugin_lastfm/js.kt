package cc.spotube.lastfm.spotube_plugin_lastfm

import app.cash.zipline.Zipline
import dev.krtirtho.plugin_interfaces.core.runPluginInitialized
import dev.krtirtho.plugin_interfaces.host_apis.HttpClientAPI
import dev.krtirtho.plugin_interfaces.host_apis.HttpClientAPI_SERVICE_NAME
import dev.krtirtho.plugin_interfaces.host_apis.CryptoAPI
import dev.krtirtho.plugin_interfaces.host_apis.CryptoAPI_SERVICE_NAME
import dev.krtirtho.plugin_interfaces.host_apis.PersistedStorageAPI
import dev.krtirtho.plugin_interfaces.host_apis.PersistedStorageAPI_SERVICE_NAME
import dev.krtirtho.plugin_interfaces.host_apis.WebViewAPI
import dev.krtirtho.plugin_interfaces.host_apis.WebViewAPI_SERVICE_NAME
import dev.krtirtho.plugin_interfaces.plugin_apis.core.CoreAPI
import dev.krtirtho.plugin_interfaces.plugin_apis.core.CoreAPI_SERVICE_NAME
import cc.spotube.lastfm.spotube_plugin_lastfm.core.Lastfm
import cc.spotube.lastfm.spotube_plugin_lastfm.plugin_apis.scrobble.RealCoreAPI
import dev.krtirtho.plugin_interfaces.plugin_apis.scrobble.ScrobbleAPI
import dev.krtirtho.plugin_interfaces.plugin_apis.scrobble.ScrobbleAPI_SERVICE_NAME
import cc.spotube.lastfm.spotube_plugin_lastfm.plugin_apis.scrobble.RealScrobbleAPI

private val zipline by lazy { Zipline.get() }

@OptIn(ExperimentalJsExport::class)
@JsExport
fun main() {
    runPluginInitialized {
        val httpClient = zipline.take<HttpClientAPI>(HttpClientAPI_SERVICE_NAME)
        val crypto = zipline.take<CryptoAPI>(CryptoAPI_SERVICE_NAME)
        val webview = zipline.take<WebViewAPI>(WebViewAPI_SERVICE_NAME)
        val storage = zipline.take<PersistedStorageAPI>(PersistedStorageAPI_SERVICE_NAME)

        val client = Lastfm(httpClient)
        zipline.bind<CoreAPI>(CoreAPI_SERVICE_NAME, RealCoreAPI(webview, storage, client, crypto))
        zipline.bind<ScrobbleAPI>(ScrobbleAPI_SERVICE_NAME, RealScrobbleAPI(client, storage, crypto))
    }
}
