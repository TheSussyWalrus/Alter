package org.alter.plugins.content.areas.yanille.spawns

import org.alter.game.Server
import org.alter.game.model.Direction
import org.alter.game.model.World
import org.alter.game.plugin.KotlinPlugin
import org.alter.game.plugin.PluginRepository

class SpawnPlugin(
    r: PluginRepository,
    world: World,
    server: Server,
) : KotlinPlugin(r, world, server) {
    init {
        // Temporary low-level fishing spot beside the Yanille test spawn for skilling verification.
        spawnNpc(npc = "npc.fishing_spot", x = 2628, z = 3123, walkRadius = 0, direction = Direction.WEST)
    }
}
