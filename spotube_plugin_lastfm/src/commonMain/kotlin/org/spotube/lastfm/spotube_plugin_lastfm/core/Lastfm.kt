package org.spotube.lastfm.spotube_plugin_lastfm.core

import org.spotube.plugin_interfaces.host_apis.HttpClientAPI

/** Provider client boundary. Add endpoint methods and response mapping for the selected service. */
class Lastfm(
    val httpClientAPI: HttpClientAPI,
)
