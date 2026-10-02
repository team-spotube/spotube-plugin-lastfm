# LastFM for Spotube

Spotube music service provider plugin for LastFM.

This project was scaffolded from the Spotube KMP plugin template. API adapters follow the current `plugin_interfaces` contracts and contain TODOs for provider-specific behavior.

## Build

```bash
./gradlew :spotube_plugin_lastfm:packageDevelopmentPlugin
```

Before building, add the Last.fm application credentials to the repository-root `local.properties` file (never commit it):

```properties
LASTFM_API_KEY=your_lastfm_api_key
LASTFM_API_SECRET=your_lastfm_api_secret
```

The build-time source generator XOR-obfuscates these values in the generated plugin bundle. The login form asks users only for their Last.fm username and password. Because the plugin runs on the user's device, obfuscation cannot make the application credentials secret from a determined reverse engineer; use a trusted server to protect them from extraction if that is a requirement.

Plugin metadata is declared through the `spotubePlugin {}` Gradle DSL.

## License

See [LICENSE](LICENSE).
