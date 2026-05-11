package org.alter.plugins.service.restapi.routes

import com.google.gson.Gson
import org.alter.game.model.EntityType
import org.alter.game.model.Tile
import org.alter.game.model.World
import org.alter.game.model.entity.GameObject
import org.alter.game.model.entity.GroundItem
import org.alter.game.service.rsa.RsaService
import org.alter.plugins.service.restapi.controllers.OnlinePlayersController
import org.alter.plugins.service.restapi.controllers.PlayerController
import org.alter.plugins.service.restapi.controllers.WorldEditorController
import spark.Spark.delete
import spark.Spark.get
import spark.Spark.patch
import spark.Spark.post

/**
 * @TODO Http-api
 */
class RestApiRoutes(
    private val host: String,
    private val httpPort: Int,
    private val gamePort: Int,
) {
    private val gson = Gson()
    private val defaultWorldId = 2
    private val defaultWorldTypeMask = 1 // MEMBERS

    fun init(
        world: World,
        auth: Boolean,
    ) {
        val worldEditorController = WorldEditorController(world)

        get("/players") { req, res ->
            gson.toJson(OnlinePlayersController(req, res, false).init(world))
        }

        get("/player/:name") { req, res ->
            gson.toJson(PlayerController(req, res, false).init(world))
        }

        get("/client_manifest.json") { _, res ->
            res.type("application/json")
            gson.toJson(buildClientManifest(world))
        }

        get("/client_manifest.schema.json") { _, res ->
            worldEditorController.clientManifestSchema(res)
        }

        get("/world-editor/client-manifest") { _, res ->
            worldEditorController.clientManifest(res)
        }

        get("/world-editor/client-manifest/schema") { _, res ->
            worldEditorController.clientManifestSchema(res)
        }

        get("/jav_config.ws") { _, res ->
            res.type("text/plain")
            res.header("Content-Disposition", "inline; filename=jav_config.ws")
            buildJavConfig()
        }

        get("/world_list.ws") { _, res ->
            res.type("application/json")
            gson.toJson(buildWorlds())
        }

        get("/worlds.js") { _, res ->
            res.type("application/json")
            gson.toJson(buildWorlds())
        }

        get("/world-editor/world-list.ws") { _, res ->
            worldEditorController.worldList(res)
        }

        get("/debug/world") { _, res ->
            res.type("application/json")
            gson.toJson(buildDebugWorld(world))
        }

        get("/debug/players") { _, res ->
            res.type("application/json")
            gson.toJson(buildDebugPlayers(world))
        }

        get("/world-editor/npc-spawns") { _, res ->
            worldEditorController.listNpcSpawns(res)
        }

        get("/world-editor/npcs/search") { req, res ->
            worldEditorController.searchNpcs(req, res)
        }

        get("/world-editor/items/search") { req, res ->
            worldEditorController.searchItems(req, res)
        }

        get("/world-editor/npcs/:npcId/image") { req, res ->
            worldEditorController.getNpcImage(req, res)
        }

        get("/world-editor/qa/status") { _, res ->
            worldEditorController.qaStatus(res)
        }

        get("/world-editor/qa/scenarios") { _, res ->
            worldEditorController.listQaScenarios(res)
        }

        get("/world-editor/qa/suites") { _, res ->
            worldEditorController.listQaSuites(res)
        }

        get("/world-editor/qa/fixtures/status") { _, res ->
            worldEditorController.qaFixtureStatus(res)
        }

        post("/world-editor/qa/sessions") { req, res ->
            worldEditorController.startQaSession(req, res)
        }

        post("/world-editor/qa/sessions/:id/stop") { req, res ->
            worldEditorController.stopQaSession(req, res)
        }

        get("/world-editor/qa/sessions") { req, res ->
            worldEditorController.listQaSessions(req, res)
        }

        get("/world-editor/qa/sessions/:id/events") { req, res ->
            worldEditorController.getQaSessionEvents(req, res)
        }

        get("/world-editor/qa/sessions/:id/report") { req, res ->
            worldEditorController.getQaSessionReport(req, res)
        }

        get("/world-editor/qa/sessions/:id") { req, res ->
            worldEditorController.getQaSession(req, res)
        }

        get("/world-editor/dev-players") { _, res ->
            worldEditorController.listDevPlayers(res)
        }

        get("/world-editor/npc-definitions") { _, res ->
            worldEditorController.listNpcDefinitions(res)
        }

        get("/world-editor/npc-definitions/:npcId") { req, res ->
            worldEditorController.getNpcDefinition(req, res)
        }

        patch("/world-editor/npc-definitions/:npcId") { req, res ->
            worldEditorController.updateNpcDefinition(req, res)
        }

        post("/world-editor/npc-definitions/save") { _, res ->
            worldEditorController.saveNpcDefinitions(res)
        }

        post("/world-editor/npc-definitions/reload") { _, res ->
            worldEditorController.reloadNpcDefinitions(res)
        }

        patch("/world-editor/npc-shops/:shopKey") { req, res ->
            worldEditorController.upsertNpcShop(req, res)
        }

        post("/world-editor/npc-spawns") { req, res ->
            worldEditorController.createNpcSpawn(req, res)
        }

        patch("/world-editor/npc-spawns/:key") { req, res ->
            worldEditorController.updateNpcSpawn(req, res)
        }

        post("/world-editor/npc-spawns/:key/move-to-player") { req, res ->
            worldEditorController.moveNpcSpawnToPlayer(req, res)
        }

        post("/world-editor/npc-spawns/:key/duplicate") { req, res ->
            worldEditorController.duplicateNpcSpawn(req, res)
        }

        delete("/world-editor/npc-spawns/:key") { req, res ->
            worldEditorController.deleteNpcSpawn(req, res)
        }

        post("/world-editor/npc-spawns/save") { _, res ->
            worldEditorController.saveNpcSpawns(res)
        }

        post("/world-editor/npc-spawns/reload") { _, res ->
            worldEditorController.reloadNpcSpawns(res)
        }
    }

    private fun buildDebugWorld(world: World): Map<String, Any?> =
        linkedMapOf(
            "game" to world.gameContext.name,
            "revision" to world.gameContext.revision,
            "plugins" to world.plugins.getPluginCount(),
            "handlers" to world.plugins.getDebugCounts(),
            "players" to world.players.count(),
            "npcs" to world.npcs.count(),
            "activeChunks" to world.chunks.getActiveChunkCount(),
            "activeRegions" to world.chunks.getActiveRegionCount(),
            "home" to linkedMapOf(
                "x" to world.gameContext.home.x,
                "z" to world.gameContext.home.z,
                "height" to world.gameContext.home.height,
            ),
        )

    private fun buildDebugPlayers(world: World): Map<String, Any?> {
        val players = mutableListOf<Map<String, Any?>>()

        world.players.forEach { player ->
            players += linkedMapOf(
                "username" to player.username,
                "index" to player.index,
                "online" to player.isOnline,
                "initiated" to player.initiated,
                "tile" to debugTile(player.tile),
                "region" to player.tile.regionId,
                "lastKnownRegionBase" to player.lastKnownRegionBase?.let { debugCoord(it.x, it.z, it.height) },
                "buildArea" to player.buildArea.toString(),
                "nearbyNpcs" to nearbyNpcs(world, player.tile, 32),
                "nearbyObjects" to nearbyObjects(world, player.tile, 16),
                "nearbyGroundItems" to nearbyGroundItems(world, player.tile, 16),
            )
        }

        return linkedMapOf(
            "count" to players.size,
            "players" to players,
        )
    }

    private fun nearbyNpcs(
        world: World,
        center: Tile,
        radius: Int,
    ): List<Map<String, Any?>> {
        val npcs = mutableListOf<Map<String, Any?>>()
        world.npcs.forEach { npc ->
            if (npc.tile.isWithinRadius(center.x, center.z, center.height, radius)) {
                npcs += linkedMapOf(
                    "index" to npc.index,
                    "id" to npc.id,
                    "tile" to debugTile(npc.tile),
                    "spawned" to npc.isSpawned(),
                    "active" to npc.isActive(),
                    "invisible" to npc.invisible,
                )
            }
        }
        return npcs.take(40)
    }

    private fun nearbyObjects(
        world: World,
        center: Tile,
        radius: Int,
    ): List<Map<String, Any?>> {
        val objects = mutableListOf<Map<String, Any?>>()
        for (x in center.x - radius..center.x + radius) {
            for (z in center.z - radius..center.z + radius) {
                val tile = Tile(x, z, center.height)
                val chunk = world.chunks.get(tile) ?: continue
                chunk.getEntities<GameObject>(tile, EntityType.STATIC_OBJECT, EntityType.DYNAMIC_OBJECT).forEach { obj ->
                    objects += linkedMapOf(
                        "id" to obj.id,
                        "type" to obj.type,
                        "rot" to obj.rot,
                        "tile" to debugTile(obj.tile),
                    )
                }
            }
        }
        return objects.take(80)
    }

    private fun nearbyGroundItems(
        world: World,
        center: Tile,
        radius: Int,
    ): List<Map<String, Any?>> {
        val items = mutableListOf<Map<String, Any?>>()
        for (x in center.x - radius..center.x + radius) {
            for (z in center.z - radius..center.z + radius) {
                val tile = Tile(x, z, center.height)
                val chunk = world.chunks.get(tile) ?: continue
                chunk.getEntities<GroundItem>(tile, EntityType.GROUND_ITEM).forEach { item ->
                    items += linkedMapOf(
                        "id" to item.item,
                        "amount" to item.amount,
                        "tile" to debugTile(item.tile),
                        "public" to item.isPublic(),
                    )
                }
            }
        }
        return items.take(80)
    }

    private fun debugTile(tile: Tile): Map<String, Int> = debugCoord(tile.x, tile.z, tile.height)

    private fun debugCoord(
        x: Int,
        z: Int,
        height: Int,
    ): Map<String, Int> =
        linkedMapOf(
            "x" to x,
            "z" to z,
            "height" to height,
        )

    private fun buildClientManifest(world: World): Map<String, Any?> {
        val rsaService = world.getService(RsaService::class.java)
        val restBaseUrl = "http://$host:$httpPort"

        return linkedMapOf(
            "game" to world.gameContext.name,
            "revision" to world.gameContext.revision,
            "environment" to "local",
            "endpoints" to linkedMapOf(
                "loginHost" to host,
                "loginPort" to gamePort,
                "js5Host" to host,
                "js5Ports" to listOf(gamePort),
                "worldListUrl" to "$restBaseUrl/world_list.ws",
                "restApiBaseUrl" to restBaseUrl,
                "home" to linkedMapOf(
                    "x" to world.gameContext.home.x,
                    "z" to world.gameContext.home.z,
                    "height" to world.gameContext.home.height,
                ),
            ),
            "cache" to linkedMapOf(
                "revision" to world.gameContext.revision,
                "buildId" to "local-${world.gameContext.revision}",
                "updateMode" to "js5",
                "cacheNativeBossAssets" to true,
            ),
            "rsa" to linkedMapOf(
                "publicExponent" to "10001",
                "modulus" to rsaService?.getModulus()?.toString(16),
            ),
            "client" to linkedMapOf(
                "minimumVersion" to "0.1.0",
                "bootstrapVersion" to "local",
                "downloadUrl" to null,
            ),
            "plugins" to linkedMapOf(
                "pluginHubEnabled" to true,
                "allowlistVersion" to "local",
                "allowlist" to listOf("*"),
                "externalAllowlist" to listOf("*"),
            ),
            "webClient" to linkedMapOf(
                "enabled" to false,
                "webSocketGatewayUrl" to null,
            ),
            "features" to linkedMapOf(
                "desktopFirst" to true,
                "curatedPlugins" to true,
                "pluginHubLocked" to false,
                "cacheNativeVisuals" to true,
                "webClientDeferred" to true,
            ),
            "notes" to "Local Alter bootstrap generated by RestApiService.",
        )
    }

    private fun buildJavConfig(): String {
        val restBaseUrl = "http://$host:$httpPort"
        return """
            title=Dodian
            adverturl=$restBaseUrl/
            codebase=http://$host:$gamePort/
            cachedir=dodian
            storebase=0
            initial_jar=gamepack.jar
            initial_class=client.class
            termsurl=$restBaseUrl/
            privacyurl=$restBaseUrl/
            viewerversion=124
            win_sub_version=1
            mac_sub_version=2
            other_sub_version=2
            download=0
            window_preferredwidth=800
            window_preferredheight=600
            advert_height=0
            applet_minwidth=765
            applet_minheight=503
            applet_maxwidth=5760
            applet_maxheight=2160
            runelite.worldparam=12
            msg=lang0=English
            msg=loading_app=Loading Dodian
            msg=err_get_file=Error getting file
            param=14=0
            param=12=$defaultWorldId
            param=11=https://auth.jagex.com/
            param=13=.runescape.com
            param=3=true
            param=6=0
            param=7=0
            param=9=ElZAIrq5NpKN6D3mDdihco3oPeYN2KFy2DCquj7JMmECPmLrDP3Bnw
            param=15=0
            param=10=5
            param=8=true
            param=17=$restBaseUrl/
            param=2=https://payments.jagex.com/operator/v1/
            param=18=$restBaseUrl/world_list.ws
            param=4=1
            param=1=$defaultWorldId
            param=19=
            param=16=false
            param=5=$defaultWorldTypeMask
        """.trimIndent() + "\n"
    }

    private fun buildWorlds(): Map<String, Any> =
        linkedMapOf(
            "worlds" to listOf(
                linkedMapOf(
                    "id" to defaultWorldId,
                    "types" to listOf("MEMBERS"),
                    "address" to host,
                    "activity" to "Dodian",
                    "location" to "UNITED_STATES_OF_AMERICA",
                    "players" to 0,
                ),
            ),
        )
}
