package org.alter.plugins.content.tools.npcspawns

import org.alter.api.ext.message
import org.alter.api.ext.openUrl
import org.alter.api.ext.player
import org.alter.game.Server
import org.alter.game.model.World
import org.alter.game.model.entity.Player
import org.alter.game.model.priv.Privilege
import org.alter.game.plugin.KotlinPlugin
import org.alter.game.plugin.PluginRepository
import org.alter.plugins.content.tools.npcdefs.NpcDefinitionService
import org.alter.plugins.service.restapi.RestApiService

class NpcSpawnEditorPlugin(
    r: PluginRepository,
    world: World,
    server: Server,
) : KotlinPlugin(r, world, server) {
    private val definitionService = NpcDefinitionService()
    private val spawnService = NpcSpawnService()

    init {
        loadService(definitionService)
        loadService(spawnService)
        loadService(RestApiService())

        onCommand("npcspawns", description = "Open the web NPC spawn editor") {
            openNpcSpawnEditor(player)
        }

        onCommand("npcspawneditor", description = "Open the web NPC spawn editor") {
            openNpcSpawnEditor(player)
        }
    }

    private fun openNpcSpawnEditor(player: Player) {
        if (!player.canUseNpcSpawnEditor()) {
            player.message("You need owner or developer privileges to use the NPC spawn editor.")
            return
        }

        player.message("Opening the local NPC spawn editor: $EDITOR_URL")
        player.openUrl(EDITOR_URL)
    }

    private fun Player.canUseNpcSpawnEditor(): Boolean =
        privilege.powers.contains(Privilege.OWNER_POWER) || privilege.powers.contains(Privilege.DEV_POWER)

    private companion object {
        private const val EDITOR_URL = "http://127.0.0.1:8080/npc-spawns"
    }
}
