package org.alter.plugins.content.areas.yanille.objs

import org.alter.api.cfg.Sound
import org.alter.api.ext.getInteractingGameObj
import org.alter.api.ext.player
import org.alter.api.ext.playSound
import org.alter.game.Server
import org.alter.game.model.Tile
import org.alter.game.model.World
import org.alter.game.model.attr.INTERACTING_SRC_TILE_ATTR
import org.alter.game.model.move.moveTo
import org.alter.game.plugin.KotlinPlugin
import org.alter.game.plugin.PluginRepository

class MagicGuildDoorPlugin(
    r: PluginRepository,
    world: World,
    server: Server,
) : KotlinPlugin(r, world, server) {

    private val insideTiles =
        mapOf(
            MAGIC_GUILD_DOOR_SOUTH to Tile(2596, 3087, 0),
            MAGIC_GUILD_DOOR_NORTH to Tile(2596, 3088, 0),
        )

    private val outsideTiles =
        mapOf(
            MAGIC_GUILD_DOOR_SOUTH to Tile(2599, 3087, 0),
            MAGIC_GUILD_DOOR_NORTH to Tile(2599, 3088, 0),
        )

    init {
        insideTiles.keys.forEach { doorId ->
            onObjOption(obj = doorId, option = "open") {
                val obj = player.getInteractingGameObj()
                val sourceTile = player.attr[INTERACTING_SRC_TILE_ATTR] ?: player.tile
                val target =
                    if (sourceTile.x >= obj.tile.x) {
                        insideTiles[obj.id] ?: INSIDE_FALLBACK
                    } else {
                        outsideTiles[obj.id] ?: OUTSIDE_FALLBACK
                    }
                player.playSound(Sound.OPEN_DOOR_SFX)
                player.moveTo(Tile(target.x, target.z, obj.tile.height))
            }
        }

        MAGIC_GUILD_UP_STAIRS.forEach { stairs ->
            onObjOption(obj = stairs, option = 1) {
                val tile = player.tile
                player.moveTo(Tile(tile.x, tile.z + MAGIC_GUILD_STAIR_Y_OFFSET, tile.height + 1))
            }
        }

        MAGIC_GUILD_DOWN_STAIRS.forEach { stairs ->
            onObjOption(obj = stairs, option = 1) {
                player.moveTo(MAGIC_GUILD_GROUND_FLOOR)
            }
        }

        onObjOption(obj = "object.magic_portal", option = 1) {
            player.playSound(Sound.TELEPORT_ALL)
            player.moveTo(RUNE_ESSENCE_MINE)
        }

        onObjOption(obj = "object.exit_cavern", option = 1) {
            player.playSound(Sound.TELEPORT_REVERSE)
            player.moveTo(YANILLE_TELEPORT_LOCATION)
        }

        onObjOption(obj = "object.null_34825", option = 1) {
            player.playSound(Sound.TELEPORT_REVERSE)
            player.moveTo(YANILLE_TELEPORT_LOCATION)
        }
    }

    companion object {
        private const val MAGIC_GUILD_DOOR_SOUTH = 1732
        private const val MAGIC_GUILD_DOOR_NORTH = 1733
        private val MAGIC_GUILD_UP_STAIRS =
            arrayOf(
                "object.staircase_15645",
                "object.staircase_15646",
                "object.staircase_15647",
                "object.staircase_15649",
            )
        private val MAGIC_GUILD_DOWN_STAIRS =
            arrayOf(
                "object.staircase_15648",
                "object.staircase_15652",
                "object.staircase_15654",
                "object.staircase_15655",
                "object.staircase_15656",
            )
        private const val MAGIC_GUILD_STAIR_Y_OFFSET = 4
        private val MAGIC_GUILD_GROUND_FLOOR = Tile(2591, 3088, 0)
        private val YANILLE_TELEPORT_LOCATION = Tile(2606, 3093, 0)
        private val RUNE_ESSENCE_MINE = Tile(2911, 4832, 0)
        private val INSIDE_FALLBACK = Tile(2596, 3088, 0)
        private val OUTSIDE_FALLBACK = Tile(2599, 3088, 0)
    }
}
