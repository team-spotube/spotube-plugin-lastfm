package org.spotube.lastfm.spotube_plugin_lastfm

import app.cash.zipline.Zipline
import org.spotube.plugin_interfaces.core.runPluginInitialized
import org.spotube.plugin_interfaces.host_apis.HttpClientAPI
import org.spotube.plugin_interfaces.host_apis.HttpClientAPI_SERVICE_NAME
import org.spotube.plugin_interfaces.host_apis.CryptoAPI
import org.spotube.plugin_interfaces.host_apis.CryptoAPI_SERVICE_NAME
import org.spotube.plugin_interfaces.host_apis.PersistedStorageAPI
import org.spotube.plugin_interfaces.host_apis.PersistedStorageAPI_SERVICE_NAME
import org.spotube.plugin_interfaces.host_apis.WebViewAPI
import org.spotube.plugin_interfaces.host_apis.WebViewAPI_SERVICE_NAME
import org.spotube.plugin_interfaces.plugin_apis.core.CoreAPI
import org.spotube.plugin_interfaces.plugin_apis.core.CoreAPI_SERVICE_NAME
import org.spotube.lastfm.spotube_plugin_lastfm.core.Lastfm
import org.spotube.lastfm.spotube_plugin_lastfm.plugin_apis.scrobble.RealCoreAPI
import org.spotube.plugin_interfaces.plugin_apis.scrobble.ScrobbleAPI
import org.spotube.plugin_interfaces.plugin_apis.scrobble.ScrobbleAPI_SERVICE_NAME
import org.spotube.lastfm.spotube_plugin_lastfm.plugin_apis.scrobble.RealScrobbleAPI

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
