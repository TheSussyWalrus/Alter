package org.alter.plugins.content.tools.client

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.openrune.cache.CacheManager
import gg.rsmod.util.ServerProperties
import org.alter.game.Server
import org.alter.game.model.World
import org.alter.game.service.Service
import org.alter.game.service.rsa.RsaService
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
        }

    private fun plugins(): JsonObject =
        JsonObject().apply {
            addProperty("pluginHubEnabled", config.boolean("pluginHubEnabled", false))
            addProperty("allowlistVersion", config.string("pluginAllowlistVersion", "local"))
            add("allowlist", config.stringArray("pluginAllowlist"))
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
                  "required": ["minimumVersion", "bootstrapVersion"],
                  "properties": {
                    "minimumVersion": { "type": "string" },
                    "bootstrapVersion": { "type": "string" },
                    "downloadUrl": { "type": ["string", "null"] }
                  }
                },
                "plugins": {
                  "type": "object",
                  "required": ["pluginHubEnabled", "allowlistVersion", "allowlist"],
                  "properties": {
                    "pluginHubEnabled": { "type": "boolean" },
                    "allowlistVersion": { "type": "string" },
                    "allowlist": { "type": "array", "items": { "type": "string" } }
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
