package org.alter.plugins.content.areas.yanille.spawns

import org.alter.game.Server
import org.alter.game.model.Direction
import org.alter.game.model.Tile
import org.alter.game.model.World
import org.alter.game.plugin.KotlinPlugin
import org.alter.game.plugin.PluginRepository

class SpawnPlugin(
    r: PluginRepository,
    world: World,
    server: Server,
) : KotlinPlugin(r, world, server) {
    init {
        spawnObject(obj = DAD_CAVE_ENTRANCE, tile = DAD_CAVE_ENTRANCE_TILE, direction = Direction.EAST)
    }

    private companion object {
        private const val DAD_CAVE_ENTRANCE = "object.cave_entrance_36556"
        private val DAD_CAVE_ENTRANCE_TILE = Tile(2536, 3090, 0)
    }
}
