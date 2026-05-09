package org.alter.plugins.service.restapi.controllers

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import org.alter.game.Server
import org.alter.game.model.Direction
import org.alter.game.model.Tile
import org.alter.game.model.World
import org.alter.game.model.entity.Player
import org.alter.game.model.priv.Privilege
import org.alter.game.service.GameService
import org.alter.plugins.content.tools.npcspawns.NpcSpawnEntry
import org.alter.plugins.content.tools.npcspawns.NpcSpawnService
import org.alter.plugins.content.tools.npcdefs.NpcDefinitionEntry
import org.alter.plugins.content.tools.npcdefs.NpcDefinitionService
import org.alter.plugins.content.tools.npcdefs.NpcShopDefinition
import spark.Request
import spark.Response
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class WorldEditorController(private val world: World) {
    private val gson = Gson()

    fun listNpcSpawns(res: Response): String {
        val service = npcSpawnService(res) ?: return currentResponse(res)
        return ok(res, spawnState(service))
    }

    fun searchNpcs(req: Request, res: Response): String {
        val spawnService = npcSpawnService(res) ?: return currentResponse(res)
        val definitionService = npcDefinitionServiceOrNull()
        val query = req.queryParams("q") ?: ""
        val limit = req.queryParams("limit")?.toIntOrNull()?.coerceIn(1, 250) ?: 120
        val results =
            spawnService.searchNpcs(query, limit).fold(JsonArray()) { arr, npc ->
                val definition = definitionService?.getDefinition(npc.id)
                arr.add(
                    JsonObject().apply {
                        addProperty("id", npc.id)
                        addProperty("name", npc.name)
                        addProperty("defined", definition != null)
                        if (definition?.imageUrl != null) {
                            addProperty("imageUrl", npcImageUrl(npc.id))
                        }
                    },
                )
                arr
            }
        return ok(
            res,
            JsonObject().apply {
                addProperty("query", query)
                addProperty("count", results.size())
                add("results", results)
            },
        )
    }

    fun searchItems(req: Request, res: Response): String {
        val service = npcDefinitionService(res) ?: return currentResponse(res)
        val query = req.queryParams("q") ?: ""
        val limit = req.queryParams("limit")?.toIntOrNull()?.coerceIn(1, 250) ?: 120
        val results =
            service.searchItems(query, limit).fold(JsonArray()) { arr, item ->
                arr.add(
                    JsonObject().apply {
                        addProperty("id", item.id)
                        addProperty("name", item.name)
                        addProperty("noted", item.noted)
                        addProperty("placeholder", item.placeholder)
                        addProperty("stackable", item.stackable)
                    },
                )
                arr
            }
        return ok(
            res,
            JsonObject().apply {
                addProperty("query", query)
                addProperty("count", results.size())
                add("results", results)
            },
        )
    }

    fun listDevPlayers(res: Response): String =
        runOnGameThread(res) {
            ok(
                res,
                JsonObject().apply {
                    val players = JsonArray()
                    world.players.forEach { player ->
                        if (player.canUseWorldEditor()) {
                            players.add(player.toJson())
                        }
                    }
                    addProperty("count", players.size())
                    add("players", players)
                },
            )
        }

    fun createNpcSpawn(req: Request, res: Response): String {
        val service = npcSpawnService(res) ?: return currentResponse(res)
        val body = parseBody(req, res) ?: return currentResponse(res)
        return runOnGameThread(res) {
            val npcId = body.optionalInt("npcId") ?: return@runOnGameThread error(res, 400, "npcId is required.")
            val npcName = service.npcName(npcId) ?: return@runOnGameThread error(res, 400, "NPC id $npcId was not found.")
            val player = placementPlayer(body, res) ?: return@runOnGameThread currentResponse(res)
            val tile = player.tile
            val entry =
                NpcSpawnEntry(
                    npcId = npcId,
                    name = npcName,
                    x = tile.x,
                    z = tile.z,
                    height = tile.height,
                )
            applyPatch(body, entry, service, res) ?: return@runOnGameThread currentResponse(res)
            val saved = service.upsert(entry, world)
            ok(res, spawnMutationState(service, "created", saved))
        }
    }

    fun updateNpcSpawn(req: Request, res: Response): String {
        val service = npcSpawnService(res) ?: return currentResponse(res)
        val body = parseBody(req, res) ?: return currentResponse(res)
        return runOnGameThread(res) {
            val current = service.entries.firstOrNull { it.key == req.params("key") }
                ?: return@runOnGameThread error(res, 404, "NPC spawn '${req.params("key")}' was not found.")
            val draft = current.copyForEdit()
            applyPatch(body, draft, service, res) ?: return@runOnGameThread currentResponse(res)
            val saved = service.upsert(draft, world)
            ok(res, spawnMutationState(service, "updated", saved))
        }
    }

    fun moveNpcSpawnToPlayer(req: Request, res: Response): String {
        val service = npcSpawnService(res) ?: return currentResponse(res)
        val body = parseBody(req, res) ?: return currentResponse(res)
        return runOnGameThread(res) {
            val entry = service.entries.firstOrNull { it.key == req.params("key") }
                ?: return@runOnGameThread error(res, 404, "NPC spawn '${req.params("key")}' was not found.")
            val player = placementPlayer(body, res) ?: return@runOnGameThread currentResponse(res)
            val saved = service.moveTo(entry, player.tile, world)
            ok(res, spawnMutationState(service, "moved", saved))
        }
    }

    fun duplicateNpcSpawn(req: Request, res: Response): String {
        val service = npcSpawnService(res) ?: return currentResponse(res)
        val body = parseBody(req, res) ?: return currentResponse(res)
        return runOnGameThread(res) {
            val entry = service.entries.firstOrNull { it.key == req.params("key") }
                ?: return@runOnGameThread error(res, 404, "NPC spawn '${req.params("key")}' was not found.")
            val player = placementPlayer(body, res) ?: return@runOnGameThread currentResponse(res)
            val saved = service.duplicateAt(entry, player.tile, world)
            ok(res, spawnMutationState(service, "duplicated", saved))
        }
    }

    fun deleteNpcSpawn(req: Request, res: Response): String {
        val service = npcSpawnService(res) ?: return currentResponse(res)
        val key = req.params("key")
        return runOnGameThread(res) {
            if (!service.delete(key, world)) {
                return@runOnGameThread error(res, 404, "NPC spawn '$key' was not found.")
            }
            ok(res, spawnState(service).apply { addProperty("action", "deleted") })
        }
    }

    fun saveNpcSpawns(res: Response): String {
        val service = npcSpawnService(res) ?: return currentResponse(res)
        return runOnGameThread(res) {
            val path = service.saveToDisk()
            ok(
                res,
                spawnState(service).apply {
                    addProperty("action", "saved")
                    addProperty("path", path.toString())
                },
            )
        }
    }

    fun reloadNpcSpawns(res: Response): String {
        val service = npcSpawnService(res) ?: return currentResponse(res)
        return runOnGameThread(res) {
            service.reload(world)
            ok(res, spawnState(service).apply { addProperty("action", "reloaded") })
        }
    }

    fun listNpcDefinitions(res: Response): String {
        val service = npcDefinitionService(res) ?: return currentResponse(res)
        return ok(res, definitionState(service))
    }

    fun getNpcDefinition(req: Request, res: Response): String {
        val service = npcDefinitionService(res) ?: return currentResponse(res)
        val npcId = req.params("npcId").toIntOrNull() ?: return error(res, 400, "npcId must be an integer.")
        val definition =
            service.getDefinition(npcId)
                ?: runCatching { service.defaultDefinition(npcId) }.getOrElse { return error(res, 404, it.message ?: "NPC definition was not found.") }
        return ok(
            res,
            JsonObject().apply {
                add("definition", gson.toJsonTree(definition))
                addProperty("dirty", service.dirty)
                addProperty("definitionsDirty", service.definitionsDirty)
                addProperty("shopsDirty", service.shopsDirty)
            },
        )
    }

    fun updateNpcDefinition(req: Request, res: Response): String {
        val service = npcDefinitionService(res) ?: return currentResponse(res)
        val body = parseBody(req, res) ?: return currentResponse(res)
        val npcId = req.params("npcId").toIntOrNull() ?: return error(res, 400, "npcId must be an integer.")
        val incoming = parseTypedBody<NpcDefinitionEntry>(body, res) ?: return currentResponse(res)
        incoming.id = npcId
        return runOnGameThread(res) {
            try {
                val saved = service.upsertDefinition(incoming, world)
                ok(res, definitionMutationState(service, "definition-updated", saved))
            } catch (t: IllegalArgumentException) {
                error(res, 400, t.message ?: "Invalid NPC definition.")
            }
        }
    }

    fun saveNpcDefinitions(res: Response): String {
        val service = npcDefinitionService(res) ?: return currentResponse(res)
        return runOnGameThread(res) {
            val paths = service.saveToDisk()
            service.applyToWorld(world)
            ok(
                res,
                definitionState(service).apply {
                    addProperty("action", "definitions-saved")
                    add("paths", paths.fold(JsonArray()) { arr, path -> arr.add(path.toString()); arr })
                },
            )
        }
    }

    fun reloadNpcDefinitions(res: Response): String {
        val service = npcDefinitionService(res) ?: return currentResponse(res)
        return runOnGameThread(res) {
            service.reload(world)
            ok(res, definitionState(service).apply { addProperty("action", "definitions-reloaded") })
        }
    }

    fun upsertNpcShop(req: Request, res: Response): String {
        val service = npcDefinitionService(res) ?: return currentResponse(res)
        val body = parseBody(req, res) ?: return currentResponse(res)
        val shop = parseTypedBody<NpcShopDefinition>(body, res) ?: return currentResponse(res)
        shop.id = req.params("shopKey").ifBlank { shop.id }
        return runOnGameThread(res) {
            try {
                val saved = service.upsertShop(shop, world)
                ok(res, shopMutationState(service, "shop-updated", saved))
            } catch (t: IllegalArgumentException) {
                error(res, 400, t.message ?: "Invalid NPC shop.")
            }
        }
    }

    fun getNpcImage(req: Request, res: Response): Any? {
        val service = npcDefinitionService(res) ?: return currentResponse(res)
        val npcId = req.params("npcId").toIntOrNull() ?: return error(res, 400, "npcId must be an integer.")
        val content = runCatching { service.imageContent(npcId) }.getOrNull()
        val bytes =
            if (content != null) {
                res.type(content.contentType)
                content.bytes
            } else {
                res.type("image/svg+xml")
                placeholderSvg(service.npcName(npcId) ?: "NPC").toByteArray(StandardCharsets.UTF_8)
            }
        res.status(200)
        res.raw().outputStream.write(bytes)
        res.raw().outputStream.flush()
        return res.raw()
    }

    private fun applyPatch(
        body: JsonObject,
        entry: NpcSpawnEntry,
        service: NpcSpawnService,
        res: Response,
    ): NpcSpawnEntry? {
        if (body.hasValue("npcId")) {
            val npcId = body.optionalInt("npcId") ?: return errorAndNull(res, 400, "npcId must be an integer.")
            val npcName = service.npcName(npcId) ?: return errorAndNull(res, 400, "NPC id $npcId was not found.")
            entry.npcId = npcId
            entry.name = npcName
        }
        if (body.hasValue("x")) {
            entry.x = body.optionalInt("x") ?: return errorAndNull(res, 400, "x must be an integer.")
        }
        if (body.hasValue("z")) {
            entry.z = body.optionalInt("z") ?: return errorAndNull(res, 400, "z must be an integer.")
        }
        if (body.hasValue("height")) {
            entry.height = body.optionalInt("height") ?: return errorAndNull(res, 400, "height must be an integer.")
            if (entry.height !in 0..3) {
                return errorAndNull(res, 400, "height must be between 0 and 3.")
            }
        }
        if (body.hasValue("walkRadius")) {
            entry.walkRadius = body.optionalInt("walkRadius") ?: return errorAndNull(res, 400, "walkRadius must be an integer.")
            if (entry.walkRadius < 0) {
                return errorAndNull(res, 400, "walkRadius must be greater than or equal to 0.")
            }
        }
        if (body.hasValue("facing")) {
            entry.facing = parseFacing(body.optionalString("facing") ?: "")
                ?: return errorAndNull(res, 400, "facing must be one of north, north_east, east, south_east, south, south_west, west, north_west.")
        }
        if (body.hasValue("active")) {
            entry.active = body.optionalBoolean("active") ?: return errorAndNull(res, 400, "active must be true or false.")
        }
        if (body.hasValue("enabled")) {
            entry.enabled = body.optionalBoolean("enabled") ?: return errorAndNull(res, 400, "enabled must be true or false.")
        }
        if (body.has("aggressive")) {
            entry.aggressive = body.optionalNullableBoolean("aggressive") ?: if (body.get("aggressive").isJsonNull) null else return errorAndNull(res, 400, "aggressive must be true, false, or null.")
        }
        if (body.has("aggressionRadius")) {
            entry.aggressionRadius = body.optionalNullableInt("aggressionRadius") ?: if (body.get("aggressionRadius").isJsonNull) null else return errorAndNull(res, 400, "aggressionRadius must be an integer or null.")
            if (entry.aggressionRadius != null && entry.aggressionRadius!! < 0) {
                return errorAndNull(res, 400, "aggressionRadius must be greater than or equal to 0.")
            }
        }
        if (body.has("followRange")) {
            entry.followRange = body.optionalNullableInt("followRange") ?: if (body.get("followRange").isJsonNull) null else return errorAndNull(res, 400, "followRange must be an integer or null.")
            if (entry.followRange != null && entry.followRange!! < 0) {
                return errorAndNull(res, 400, "followRange must be greater than or equal to 0.")
            }
        }
        if (body.has("shopKey")) {
            entry.shopKey = body.optionalString("shopKey")?.trim()?.takeIf { it.isNotBlank() }
        }
        if (body.hasValue("tags")) {
            entry.tags = body.optionalTags("tags") ?: return errorAndNull(res, 400, "tags must be a string or array of strings.")
        }
        if (body.has("notes")) {
            entry.notes = body.optionalString("notes")?.trim()?.takeIf { it.isNotBlank() }
        }
        return entry
    }

    private fun npcSpawnService(res: Response): NpcSpawnService? =
        world.getService(NpcSpawnService::class.java)
            ?: errorAndNull(res, 503, "NpcSpawnService is not loaded.")

    private fun npcDefinitionService(res: Response): NpcDefinitionService? =
        world.getService(NpcDefinitionService::class.java)
            ?: errorAndNull(res, 503, "NpcDefinitionService is not loaded.")

    private fun npcDefinitionServiceOrNull(): NpcDefinitionService? =
        world.getService(NpcDefinitionService::class.java)

    private fun placementPlayer(body: JsonObject, res: Response): Player? {
        val name = body.optionalString("player") ?: body.optionalString("playerName")
        if (name.isNullOrBlank()) {
            return errorAndNull(res, 400, "player is required.")
        }
        val player = world.getPlayerForName(name)
            ?: return errorAndNull(res, 404, "Player '$name' is not online.")
        if (!player.canUseWorldEditor()) {
            return errorAndNull(res, 403, "Player '${player.username}' does not have owner or developer privileges.")
        }
        return player
    }

    private fun parseBody(req: Request, res: Response): JsonObject? {
        val raw = req.body()?.trim().orEmpty()
        if (raw.isBlank()) {
            return JsonObject()
        }
        return try {
            gson.fromJson(raw, JsonObject::class.java) ?: JsonObject()
        } catch (_: JsonSyntaxException) {
            errorAndNull(res, 400, "Request body must be valid JSON.")
        }
    }

    private inline fun <reified T> parseTypedBody(body: JsonObject, res: Response): T? =
        try {
            gson.fromJson(body, T::class.java)
        } catch (_: JsonSyntaxException) {
            errorAndNull(res, 400, "Request body did not match the expected JSON shape.")
        }

    private fun spawnMutationState(
        service: NpcSpawnService,
        action: String,
        entry: NpcSpawnEntry,
    ): JsonObject =
        spawnState(service).apply {
            addProperty("action", action)
            add("entry", entry.toJson())
        }

    private fun spawnState(service: NpcSpawnService): JsonObject =
        JsonObject().apply {
            val sorted = service.entries.sortedWith(compareBy<NpcSpawnEntry> { it.key }.thenBy { it.npcId })
            addProperty("dirty", service.dirty)
            addProperty("count", sorted.size)
            addProperty("configPath", service.configPath.toString())
            add(
                "entries",
                sorted.fold(JsonArray()) { arr, entry ->
                    arr.add(entry.toJson())
                    arr
                },
            )
        }

    private fun definitionMutationState(
        service: NpcDefinitionService,
        action: String,
        definition: NpcDefinitionEntry,
    ): JsonObject =
        definitionState(service).apply {
            addProperty("action", action)
            add("definition", gson.toJsonTree(definition))
        }

    private fun shopMutationState(
        service: NpcDefinitionService,
        action: String,
        shop: NpcShopDefinition,
    ): JsonObject =
        definitionState(service).apply {
            addProperty("action", action)
            add("shop", gson.toJsonTree(shop))
        }

    private fun definitionState(service: NpcDefinitionService): JsonObject =
        JsonObject().apply {
            addProperty("dirty", service.dirty)
            addProperty("definitionsDirty", service.definitionsDirty)
            addProperty("shopsDirty", service.shopsDirty)
            addProperty("definitionCount", service.definitions.size)
            addProperty("shopCount", service.shops.size)
            addProperty("definitionsPath", service.definitionsPath.toString())
            addProperty("shopsPath", service.shopsPath.toString())
            add("definitions", gson.toJsonTree(service.listDefinitions()))
            add("shops", gson.toJsonTree(service.listShops()))
        }

    private fun NpcSpawnEntry.toJson(): JsonObject =
        JsonObject().apply {
            addProperty("key", key)
            addProperty("npcId", npcId)
            addProperty("name", name)
            addProperty("x", x)
            addProperty("z", z)
            addProperty("height", height)
            addProperty("walkRadius", walkRadius)
            addProperty("facing", facing)
            addProperty("active", active)
            addProperty("enabled", enabled)
            add("aggressive", aggressive?.let { com.google.gson.JsonPrimitive(it) } ?: com.google.gson.JsonNull.INSTANCE)
            add("aggressionRadius", aggressionRadius?.let { com.google.gson.JsonPrimitive(it) } ?: com.google.gson.JsonNull.INSTANCE)
            add("followRange", followRange?.let { com.google.gson.JsonPrimitive(it) } ?: com.google.gson.JsonNull.INSTANCE)
            if (shopKey == null) {
                add("shopKey", null)
            } else {
                addProperty("shopKey", shopKey)
            }
            add("tags", tags.orEmpty().fold(JsonArray()) { arr, tag -> arr.add(tag); arr })
            if (notes == null) {
                add("notes", null)
            } else {
                addProperty("notes", notes)
            }
        }

    private fun Player.toJson(): JsonObject =
        JsonObject().apply {
            addProperty("username", username)
            addProperty("privilege", privilege.name)
            add("tile", tile.toJson())
        }

    private fun Tile.toJson(): JsonObject =
        JsonObject().apply {
            addProperty("x", x)
            addProperty("z", z)
            addProperty("height", height)
            addProperty("regionId", regionId)
        }

    private fun Player.canUseWorldEditor(): Boolean =
        privilege.powers.contains(Privilege.OWNER_POWER) || privilege.powers.contains(Privilege.DEV_POWER)

    private fun parseFacing(value: String): String? {
        val normalized = value.trim().uppercase(Locale.ROOT).replace("-", "_").replace(" ", "_")
        return when (normalized) {
            "N", "NORTH" -> Direction.NORTH.name
            "NE", "NORTH_EAST", "NORTHEAST" -> Direction.NORTH_EAST.name
            "E", "EAST" -> Direction.EAST.name
            "SE", "SOUTH_EAST", "SOUTHEAST" -> Direction.SOUTH_EAST.name
            "S", "SOUTH" -> Direction.SOUTH.name
            "SW", "SOUTH_WEST", "SOUTHWEST" -> Direction.SOUTH_WEST.name
            "W", "WEST" -> Direction.WEST.name
            "NW", "NORTH_WEST", "NORTHWEST" -> Direction.NORTH_WEST.name
            else -> null
        }
    }

    private fun JsonObject.hasValue(name: String): Boolean = has(name) && !get(name).isJsonNull

    private fun JsonObject.optionalString(name: String): String? {
        if (!hasValue(name)) {
            return null
        }
        return runCatching { get(name).asString }.getOrNull()
    }

    private fun JsonObject.optionalInt(name: String): Int? {
        if (!hasValue(name)) {
            return null
        }
        return runCatching { get(name).asInt }.getOrNull()
    }

    private fun JsonObject.optionalBoolean(name: String): Boolean? {
        if (!hasValue(name)) {
            return null
        }
        return runCatching { get(name).asBoolean }.getOrNull()
    }

    private fun JsonObject.optionalNullableInt(name: String): Int? {
        if (!has(name) || get(name).isJsonNull) {
            return null
        }
        return runCatching { get(name).asInt }.getOrNull()
    }

    private fun JsonObject.optionalNullableBoolean(name: String): Boolean? {
        if (!has(name) || get(name).isJsonNull) {
            return null
        }
        return runCatching { get(name).asBoolean }.getOrNull()
    }

    private fun JsonObject.optionalTags(name: String): MutableList<String>? {
        if (!hasValue(name)) {
            return mutableListOf()
        }
        val value = get(name)
        val raw =
            when {
                value.isJsonArray -> value.asJsonArray.map { it.asString }
                value.isJsonPrimitive -> value.asString.split(",")
                else -> return null
            }
        return raw.map { it.trim() }.filter { it.isNotBlank() }.distinct().toMutableList()
    }

    private fun ok(res: Response, obj: JsonObject): String {
        res.status(200)
        res.type("application/json")
        return gson.toJson(obj)
    }

    private fun runOnGameThread(res: Response, logic: () -> String): String {
        val gameService = world.getService(GameService::class.java)
            ?: return error(res, 503, "GameService is not loaded.")
        val future = CompletableFuture<String>()
        gameService.submitGameThreadJob {
            try {
                future.complete(logic())
            } catch (t: Throwable) {
                future.completeExceptionally(t)
            }
        }
        return try {
            future.get(5, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            error(res, 504, "Timed out waiting for the game thread.")
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            error(res, 500, "Interrupted while waiting for the game thread.")
        } catch (e: ExecutionException) {
            val cause = e.cause ?: e
            Server.logger.error(cause) { "World editor REST operation failed." }
            error(res, 500, cause.message ?: "World editor operation failed.")
        }
    }

    private fun error(res: Response, status: Int, message: String): String {
        res.status(status)
        res.type("application/json")
        val body = gson.toJson(
            JsonObject().apply {
                addProperty("error", message)
            },
        )
        res.body(body)
        return body
    }

    private fun <T> errorAndNull(res: Response, status: Int, message: String): T? {
        error(res, status, message)
        return null
    }

    private fun currentResponse(res: Response): String =
        res.body().takeIf { !it.isNullOrBlank() } ?: "{}"

    private fun npcImageUrl(npcId: Int): String =
        "http://127.0.0.1:4567/world-editor/npcs/$npcId/image"

    private fun placeholderSvg(name: String): String {
        val safe = name.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").take(42)
        return """
            <svg xmlns="http://www.w3.org/2000/svg" width="180" height="180" viewBox="0 0 180 180">
              <rect width="180" height="180" rx="24" fill="#26372d"/>
              <circle cx="90" cy="70" r="34" fill="#d8cab0"/>
              <path d="M45 153c8-31 26-47 45-47s37 16 45 47" fill="#f6eddb"/>
              <text x="90" y="170" text-anchor="middle" font-family="serif" font-size="14" fill="#fff9eb">$safe</text>
            </svg>
        """.trimIndent()
    }
}
