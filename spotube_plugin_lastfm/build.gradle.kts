import dev.krtirtho.PluginAbility
import dev.krtirtho.PluginCapability
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension
import java.util.Properties

abstract class GenerateLastfmLoginPage : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val htmlFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val cssFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val javascriptFile: RegularFileProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val localPropertiesFile: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val localProperties = Properties().apply {
            localPropertiesFile.orNull?.asFile?.takeIf { it.isFile }?.inputStream()?.use { load(it) }
        }
        val apiKey = localProperties.getProperty("LASTFM_API_KEY").orEmpty()
        val apiSecret = localProperties.getProperty("LASTFM_API_SECRET").orEmpty()
        val page = htmlFile.get().asFile.readText()
            .replace("<!-- LASTFM_CSS -->", "<style>\n${cssFile.get().asFile.readText()}\n</style>")
            .replace("<!-- LASTFM_JS -->", "<script>\n${javascriptFile.get().asFile.readText()}\n</script>")
        val escapedPage = buildString {
            page.forEach { character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '$' -> append("\\$")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(character)
                }
            }
        }
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(
                """package cc.spotube.lastfm.spotube_plugin_lastfm.plugin_apis.scrobble

internal const val LASTFM_LOGIN_PAGE = "$escapedPage"

internal object LastfmBuildCredentials {
    private const val OBFUSCATION_SEED = 0x4c46
    private val apiKeyData = intArrayOf(${apiKey.obfuscatedInts()})
    private val apiSecretData = intArrayOf(${apiSecret.obfuscatedInts()})

    fun apiKey(): String = decode(apiKeyData)

    fun apiSecret(): String = decode(apiSecretData)

    private fun decode(data: IntArray): String = buildString(data.size) {
        data.forEachIndexed { index, value ->
            append((value xor ((OBFUSCATION_SEED + index * 31) and 0xffff)).toChar())
        }
    }
}
""",
            )
        }
    }

    private fun String.obfuscatedInts(): String = mapIndexed { index, character ->
        (character.code xor ((0x4c46 + index * 31) and 0xffff)).toString()
    }.joinToString(", ")
}

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.zipline.gradle.plugin)
    alias(libs.plugins.spotubeGradle)
}

val lastfmLoginPageOutput = layout.buildDirectory.file("generated/lastfm-login/LastfmLoginPage.kt")
val generateLastfmLoginPage = tasks.register<GenerateLastfmLoginPage>("generateLastfmLoginPage") {
    htmlFile.set(layout.projectDirectory.file("src/jsMain/resources/lastfm-login.html"))
    cssFile.set(layout.projectDirectory.file("src/jsMain/resources/lastfm-login.css"))
    javascriptFile.set(layout.projectDirectory.file("src/jsMain/resources/lastfm-login.js"))
    rootProject.layout.projectDirectory.file("local.properties")
        .takeIf { it.asFile.isFile }
        ?.let(localPropertiesFile::set)
    outputFile.set(lastfmLoginPageOutput)
}

spotubePlugin {
    name = "LastFM"
    version = "0.1.0"
    apiVersion = "0.0.1"
    description = "LastFM scrobbling for Spotube"
    author = "cc.spotube.lastfm"
    capabilities = listOf(
        PluginCapability.NETWORK_REQUESTS,
        PluginCapability.PERSISTENT_STORAGE,
        PluginCapability.WEBVIEW
    )
    abilities = listOf(
        PluginAbility.SCROBBLE,
    )
    license = "AGPL-3.0-or-later"
    contact = "team.spotube@proton.me"
    repository = "https://github.com/team-spotube/spotube-plugin-lastfm"
    bugs = "https://github.com/team-spotube/spotube-plugin-lastfm/issues"
}

kotlin {
    applyDefaultHierarchyTemplate()

    js {
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
        binaries.executable()
    }

    jvm()

    sourceSets {
        jsMain {
            kotlin.srcDir(lastfmLoginPageOutput.map { it.asFile.parentFile })
        }

        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            api(libs.zipline.core)
            implementation(libs.spotube.plugin.interfaces)
            implementation(libs.semver)
        }

        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.cryptography.core)
            implementation(libs.cryptography.provider.optimal)
        }

        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutines.swing)
        }

    }
}

tasks.named("compileKotlinJs").configure {
    dependsOn(generateLastfmLoginPage)
}

zipline {
    mainFunction.set("cc.spotube.lastfm.spotube_plugin_lastfm.main")
}

plugins.withType<YarnPlugin> {
    the<YarnRootExtension>().yarnLockAutoReplace = true
}
