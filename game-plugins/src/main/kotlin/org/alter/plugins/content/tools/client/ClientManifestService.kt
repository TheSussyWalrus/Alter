package org.alter.plugins.content.tools.client

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import dev.openrune.cache.CacheManager
import gg.rsmod.util.ServerProperties
import org.alter.game.Server
import org.alter.game.model.World
import org.alter.game.service.Service
import org.alter.game.service.rsa.RsaService
import org.alter.plugins.service.worldlist.model.WorldEntry
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.time.Instant

class ClientManifestService : Service {
    private lateinit var configPath: Path
    private var config = JsonObject()

    override fun init(
        server: Server,
        world: World,
        serviceProperties: ServerProperties,
    ) {
        configPath = resolveConfigPath(serviceProperties.get("client.manifest") ?: DEFAULT_CONFIG)
        reload()
    }

    fun reload() {
        config =
            if (Files.exists(configPath)) {
                Files.newBufferedReader(configPath).use { reader ->
                    JsonParser.parseReader(reader).asJsonObject
                }
            } else {
                JsonObject()
            }
    }

    fun manifest(world: World): JsonObject {
        val context = world.gameContext
        val rsa = world.getService(RsaService::class.java)

        return JsonObject().apply {
            addProperty("game", context.name)
            addProperty("revision", context.revision)
            addProperty("environment", config.string("environment", "local"))
            addProperty("generatedAt", Instant.now().toString())
            add("endpoints", endpoints(context))
            add("cache", cache(context.revision))
            add("rsa", rsa(rsa))
            add("client", client())
            add("plugins", plugins())
            add("webClient", webClient())
            add("features", features())
            config.string("notes", null)?.let { addProperty("notes", it) }
        }
    }

    fun schema(): JsonObject =
        JsonParser.parseString(CLIENT_MANIFEST_SCHEMA).asJsonObject

    fun javConfig(world: World): String {
        val context = world.gameContext
        val endpointConfig = endpoints(context)
        val js5Host = endpointConfig.get("js5Host").asString
        val worldListUrl = endpointConfig.get("worldListUrl").asString
        val codebase = config.string("javConfigCodebase", "http://$js5Host/")!!
        val initialJar = config.string("initialJar", "gamepack_${context.revision}.jar")
        val initialClass = config.string("initialClass", "client.class")
        val worldId = config.int("worldId", 1)
        val worldTypeMask = config.int("worldTypeMask", 1)

        return buildString {
            appendLine("title=${config.string("title", context.name)}")
            appendLine("adverturl=http://$js5Host/")
            appendLine("codebase=$codebase")
            appendLine("cachedir=${config.string("cacheDir", "dodian")}")
            appendLine("storebase=0")
            appendLine("initial_jar=$initialJar")
            appendLine("initial_class=$initialClass")
            appendLine("termsurl=http://$js5Host/terms")
            appendLine("privacyurl=http://$js5Host/privacy")
            appendLine("viewerversion=${config.int("viewerVersion", context.revision)}")
            appendLine("win_sub_version=1")
            appendLine("mac_sub_version=2")
            appendLine("other_sub_version=2")
            appendLine("download=0")
            appendLine("window_preferredwidth=800")
            appendLine("window_preferredheight=600")
            appendLine("advert_height=0")
            appendLine("applet_minwidth=765")
            appendLine("applet_minheight=503")
            appendLine("applet_maxwidth=5760")
            appendLine("applet_maxheight=2160")
            appendLine("runelite.worldparam=1")
            appendLine("msg=lang0=English")
            appendLine("msg=loading_app=Loading Dodian")
            appendLine("msg=err_get_file=Error getting file")
            appendLine("msg=err_downloading=Error downloading")
            appendLine("msg=ok=OK")
            appendLine("msg=cancel=Cancel")
            appendLine("msg=message=Message")
            appendLine("msg=information=Information")
            appendLine("param=14=0")
            appendLine("param=12=$worldId")
            appendLine("param=11=http://$js5Host/")
            appendLine("param=13=.dodian.local")
            appendLine("param=3=true")
            appendLine("param=6=0")
            appendLine("param=7=0")
            appendLine("param=9=${config.string("jagexToken", "")}")
            appendLine("param=15=0")
            appendLine("param=10=5")
            appendLine("param=8=true")
            appendLine("param=17=$worldListUrl")
            appendLine("param=2=http://$js5Host/")
            appendLine("param=18=")
            appendLine("param=4=0")
            appendLine("param=1=$worldId")
            appendLine("param=19=")
            appendLine("param=16=false")
            appendLine("param=5=$worldTypeMask")
        }
    }

    fun worldList(): ByteArray {
        val path = resolveConfigPath(config.string("worldListConfig", "../data/cfg/world.json")!!)
        val entries =
            if (Files.exists(path)) {
                Files.newBufferedReader(path).use { reader ->
                    Gson().fromJson<List<WorldEntry>>(reader, object : TypeToken<List<WorldEntry>>() {}.type)
                }
            } else {
                emptyList()
            }

        val worldList = ByteArrayOutputStream()
        DataOutputStream(worldList).use { out ->
            out.writeShort(entries.size)
            entries.forEach { entry ->
                val mask = entry.types.fold(0) { acc, type -> acc or type.mask }
                out.writeShort(entry.id)
                out.writeInt(mask)
                out.writeCString(entry.address)
                out.writeCString(entry.activity)
                out.writeByte(entry.location.id)
                out.writeShort(entry.players)
            }
        }

        val framed = ByteArrayOutputStream()
        DataOutputStream(framed).use { out ->
            val bytes = worldList.toByteArray()
            out.writeInt(bytes.size)
            out.write(bytes)
        }
        return framed.toByteArray()
    }

    private fun endpoints(context: org.alter.game.GameContext): JsonObject =
        JsonObject().apply {
            addProperty("loginHost", config.string("loginHost", "127.0.0.1"))
            addProperty("loginPort", config.int("loginPort", DEFAULT_GAME_PORT))
            addProperty("js5Host", config.string("js5Host", config.string("loginHost", "127.0.0.1")))
            add("js5Ports", config.array("js5Ports", listOf(DEFAULT_GAME_PORT)))
            addProperty("worldListUrl", config.string("worldListUrl", "http://127.0.0.1:4567/world_list.ws"))
            addProperty("restApiBaseUrl", config.string("restApiBaseUrl", "http://127.0.0.1:4567"))
            add("home", JsonObject().apply {
                addProperty("x", context.home.x)
                addProperty("z", context.home.z)
                addProperty("height", context.home.height)
            })
        }

    private fun cache(revision: Int): JsonObject =
        JsonObject().apply {
            addProperty("revision", revision)
            addProperty("buildId", cacheBuildId())
            addProperty("updateMode", config.string("cacheUpdateMode", "js5"))
            addProperty("cacheNativeBossAssets", true)
        }

    private fun rsa(service: RsaService?): JsonObject =
        JsonObject().apply {
            addProperty("publicExponent", config.string("publicExponent", DEFAULT_PUBLIC_EXPONENT))
            if (service == null) {
                add("modulus", JsonNull.INSTANCE)
            } else {
                addProperty("modulus", service.getModulus().toString(16))
            }
        }

    private fun client(): JsonObject =
        JsonObject().apply {
            addProperty("minimumVersion", config.string("minimumClientVersion", "0.1.0"))
            addProperty("bootstrapVersion", config.string("bootstrapVersion", "0.1.0"))
            config.string("downloadUrl", null)?.let { addProperty("downloadUrl", it) } ?: add("downloadUrl", JsonNull.INSTANCE)
            add("release", release())
            add("update", update())
        }

    private fun release(): JsonObject =
        JsonObject().apply {
            addProperty("channel", config.string("desktopReleaseChannel", config.string("environment", "local")))
            addProperty("latestVersion", config.string("desktopLatestVersion", config.string("bootstrapVersion", "0.1.0")))
            config.string("desktopReleasePublishedAt", null)?.let { addProperty("publishedAt", it) } ?: add("publishedAt", JsonNull.INSTANCE)
            config.string("desktopReleaseNotesUrl", null)?.let { addProperty("notesUrl", it) } ?: add("notesUrl", JsonNull.INSTANCE)
            config.string("desktopDownloadPageUrl", null)?.let { addProperty("downloadPageUrl", it) } ?: add("downloadPageUrl", JsonNull.INSTANCE)
            add("artifacts", config.jsonArray("desktopReleaseArtifacts"))
        }

    private fun update(): JsonObject =
        JsonObject().apply {
            addProperty("enabled", config.boolean("desktopUpdateEnabled", false))
            addProperty("required", config.boolean("desktopUpdateRequired", false))
            addProperty("rollout", config.double("desktopUpdateRollout", 0.0).coerceIn(0.0, 1.0))
            addProperty("launcherExecutable", config.string("desktopLauncherExecutable", "Dodian.exe"))
            add("eligibleLauncherVersions", config.stringArray("desktopUpdateEligibleLauncherVersions"))
            config.string("desktopUpdateMessage", null)?.let { addProperty("message", it) } ?: add("message", JsonNull.INSTANCE)
        }

    private fun plugins(): JsonObject =
        JsonObject().apply {
            addProperty("pluginHubEnabled", config.boolean("pluginHubEnabled", false))
            addProperty("allowlistVersion", config.string("pluginAllowlistVersion", "local"))
            add("allowlist", config.stringArray("pluginAllowlist"))
            add("externalAllowlist", config.stringArray("externalPluginAllowlist"))
        }

    private fun webClient(): JsonObject =
        JsonObject().apply {
            addProperty("enabled", config.boolean("webClientEnabled", false))
            addProperty("webSocketGatewayUrl", config.string("webSocketGatewayUrl", "ws://127.0.0.1:4568/game"))
        }

    private fun features(): JsonObject =
        JsonObject().apply {
            addProperty("desktopFirst", true)
            addProperty("curatedPlugins", true)
            addProperty("pluginHubLocked", !config.boolean("pluginHubEnabled", false))
            addProperty("cacheNativeVisuals", true)
            addProperty("webClientDeferred", !config.boolean("webClientEnabled", false))
        }

    private fun cacheBuildId(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val versionTable = runCatching { CacheManager.cache.versionTable }.getOrNull()
        if (versionTable != null) {
            digest.update(versionTable)
        } else {
            digest.update(configPath.toAbsolutePath().normalize().toString().toByteArray())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.take(16)
    }

    private fun JsonObject.string(name: String, fallback: String?): String? =
        if (has(name) && !get(name).isJsonNull) {
            runCatching { get(name).asString }.getOrDefault(fallback)
        } else {
            fallback
        }

    private fun JsonObject.int(name: String, fallback: Int): Int =
        if (has(name) && !get(name).isJsonNull) {
            runCatching { get(name).asInt }.getOrDefault(fallback)
        } else {
            fallback
        }

    private fun JsonObject.boolean(name: String, fallback: Boolean): Boolean =
        if (has(name) && !get(name).isJsonNull) {
            runCatching { get(name).asBoolean }.getOrDefault(fallback)
        } else {
            fallback
        }

    private fun JsonObject.double(name: String, fallback: Double): Double =
        if (has(name) && !get(name).isJsonNull) {
            runCatching { get(name).asDouble }.getOrDefault(fallback)
        } else {
            fallback
        }

    private fun JsonObject.array(name: String, fallback: List<Int>): JsonArray {
        if (!has(name) || get(name).isJsonNull || !get(name).isJsonArray) {
            return fallback.fold(JsonArray()) { arr, value -> arr.add(value); arr }
        }
        return get(name).asJsonArray
    }

    private fun JsonObject.stringArray(name: String): JsonArray {
        if (!has(name) || get(name).isJsonNull || !get(name).isJsonArray) {
            return JsonArray()
        }
        return get(name).asJsonArray.fold(JsonArray()) { arr, value ->
            runCatching { value.asString }.getOrNull()?.takeIf { it.isNotBlank() }?.let(arr::add)
            arr
        }
    }

    private fun JsonObject.jsonArray(name: String): JsonArray =
        if (has(name) && !get(name).isJsonNull && get(name).isJsonArray) {
            JsonParser.parseString(get(name).toString()).asJsonArray
        } else {
            JsonArray()
        }

    private fun resolveConfigPath(rawPath: String): Path {
        val direct = Paths.get(rawPath)
        val cwd = Paths.get("").toAbsolutePath()
        val candidates =
            listOf(
                direct,
                cwd.resolve(direct),
                cwd.resolve("..").resolve(direct),
                Paths.get("..").resolve(direct),
            )
        return candidates.firstOrNull { Files.exists(it) }?.normalize() ?: direct
    }

    private fun DataOutputStream.writeCString(value: String) {
        write(value.toByteArray(Charsets.UTF_8))
        writeByte(0)
    }

    companion object {
        private const val DEFAULT_CONFIG = "../data/cfg/client/client_manifest.json"
        private const val DEFAULT_GAME_PORT = 43594
        private const val DEFAULT_PUBLIC_EXPONENT = "10001"
        private val CLIENT_MANIFEST_SCHEMA =
            """
            {
              "${'$'}schema": "https://json-schema.org/draft/2020-12/schema",
              "${'$'}id": "https://alter.local/client_manifest.schema.json",
              "title": "Alter Client Manifest",
              "type": "object",
              "required": ["game", "revision", "environment", "generatedAt", "endpoints", "cache", "rsa", "client", "plugins", "webClient", "features"],
              "properties": {
                "game": { "type": "string" },
                "revision": { "type": "integer", "minimum": 1 },
                "environment": { "type": "string" },
                "generatedAt": { "type": "string" },
                "endpoints": {
                  "type": "object",
                  "required": ["loginHost", "loginPort", "js5Host", "js5Ports", "restApiBaseUrl"],
                  "properties": {
                    "loginHost": { "type": "string" },
                    "loginPort": { "type": "integer", "minimum": 1, "maximum": 65535 },
                    "js5Host": { "type": "string" },
                    "js5Ports": { "type": "array", "items": { "type": "integer", "minimum": 1, "maximum": 65535 } },
                    "worldListUrl": { "type": "string" },
                    "restApiBaseUrl": { "type": "string" },
                    "home": {
                      "type": "object",
                      "required": ["x", "z", "height"],
                      "properties": {
                        "x": { "type": "integer" },
                        "z": { "type": "integer" },
                        "height": { "type": "integer", "minimum": 0, "maximum": 3 }
                      }
                    }
                  }
                },
                "cache": {
                  "type": "object",
                  "required": ["revision", "buildId", "updateMode"],
                  "properties": {
                    "revision": { "type": "integer", "minimum": 1 },
                    "buildId": { "type": "string" },
                    "updateMode": { "type": "string" },
                    "cacheNativeBossAssets": { "type": "boolean" }
                  }
                },
                "rsa": {
                  "type": "object",
                  "required": ["publicExponent", "modulus"],
                  "properties": {
                    "publicExponent": { "type": "string" },
                    "modulus": { "type": ["string", "null"] }
                  }
                },
                "client": {
                  "type": "object",
                  "required": ["minimumVersion", "bootstrapVersion", "release", "update"],
                  "properties": {
                    "minimumVersion": { "type": "string" },
                    "bootstrapVersion": { "type": "string" },
                    "downloadUrl": { "type": ["string", "null"] },
                    "release": {
                      "type": "object",
                      "required": ["channel", "latestVersion", "publishedAt", "notesUrl", "downloadPageUrl", "artifacts"],
                      "properties": {
                        "channel": { "type": "string" },
                        "latestVersion": { "type": "string" },
                        "publishedAt": { "type": ["string", "null"] },
                        "notesUrl": { "type": ["string", "null"] },
                        "downloadPageUrl": { "type": ["string", "null"] },
                        "artifacts": {
                          "type": "array",
                          "items": {
                            "type": "object",
                            "required": ["platform", "arch", "format", "url", "sha256", "sizeBytes"],
                            "properties": {
                              "platform": { "type": "string", "enum": ["windows", "macos", "linux"] },
                              "arch": { "type": "string", "enum": ["x64", "arm64"] },
                              "format": { "type": "string", "enum": ["exe", "msi", "zip", "dmg", "tar.gz", "jar"] },
                              "url": { "type": ["string", "null"] },
                              "sha256": { "type": ["string", "null"], "pattern": "^[a-fA-F0-9]{64}$" },
                              "sizeBytes": { "type": ["integer", "null"], "minimum": 1 }
                            }
                          }
                        }
                      }
                    },
                    "update": {
                      "type": "object",
                      "required": ["enabled", "required", "rollout", "launcherExecutable", "eligibleLauncherVersions", "message"],
                      "properties": {
                        "enabled": { "type": "boolean" },
                        "required": { "type": "boolean" },
                        "rollout": { "type": "number", "minimum": 0, "maximum": 1 },
                        "launcherExecutable": { "type": "string" },
                        "eligibleLauncherVersions": { "type": "array", "items": { "type": "string" } },
                        "message": { "type": ["string", "null"] }
                      }
                    }
                  }
                },
                "plugins": {
                  "type": "object",
                  "required": ["pluginHubEnabled", "allowlistVersion", "allowlist", "externalAllowlist"],
                  "properties": {
                    "pluginHubEnabled": { "type": "boolean" },
                    "allowlistVersion": { "type": "string" },
                    "allowlist": { "type": "array", "items": { "type": "string" } },
                    "externalAllowlist": { "type": "array", "items": { "type": "string" } }
                  }
                },
                "webClient": {
                  "type": "object",
                  "required": ["enabled", "webSocketGatewayUrl"],
                  "properties": {
                    "enabled": { "type": "boolean" },
                    "webSocketGatewayUrl": { "type": "string" }
                  }
                },
                "features": { "type": "object" },
                "notes": { "type": "string" }
              }
            }
            """.trimIndent()
    }
}
