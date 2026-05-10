package org.alter.plugins.service.restapi.routes

import com.google.gson.Gson
import org.alter.game.model.World
import org.alter.plugins.service.restapi.controllers.OnlinePlayersController
import org.alter.plugins.service.restapi.controllers.PlayerController
import org.alter.plugins.service.restapi.controllers.WorldEditorController
import spark.Spark.*

/**
 * @TODO Http-api
 */
class RestApiRoutes {
    fun init(
        world: World,
        auth: Boolean,
    ) {
        val worldEditorController = WorldEditorController(world)

        get("/players") {
                req, res ->
            Gson().toJson(OnlinePlayersController(req, res, false).init(world))
        }

        get("/player/:name") {
                req, res ->
            Gson().toJson(PlayerController(req, res, false).init(world))
        }
        get("/jav_config.ws") { req, res ->
            val filePath = "../jav_config.ws"
            res.type("application/octet-stream")
            res.header("Content-Disposition", "attachment; filename=jav_config.ws")
            try {
                val file = java.nio.file.Paths.get(filePath)
                val fileContent = java.nio.file.Files.readAllBytes(file)
                res.raw().outputStream.write(fileContent)
                res.raw().outputStream.flush()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            null
        }






        get("/world_list.ws") { req, res ->
            val filePath = "../world_list.ws"  // Replace with the actual path to your file
            // Set response headers to indicate a file download
            res.type("application/octet-stream")  // You can change this to the correct MIME type if known
            res.header("Content-Disposition", "attachment; filename=world_list.ws")
            // Read the file and return its contents as the response
            try {
                val file = java.nio.file.Paths.get(filePath)
                println(file.toAbsolutePath().toString())
                val fileContent = java.nio.file.Files.readAllBytes(file)
                res.raw().outputStream.write(fileContent)
                res.raw().outputStream.flush()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            // Return null as the response is handled directly by writing to the output stream
            null
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
}
