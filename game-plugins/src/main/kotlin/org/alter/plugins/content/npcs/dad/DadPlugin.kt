package org.alter.plugins.content.npcs.dad

import org.alter.api.cfg.Animation
import org.alter.api.cfg.Sound
import org.alter.api.dsl.setCombatDef
import org.alter.api.ext.getInteractingGameObj
import org.alter.api.ext.message
import org.alter.api.ext.player
import org.alter.api.ext.playSound
import org.alter.game.Server
import org.alter.game.model.Direction
import org.alter.game.model.Tile
import org.alter.game.model.World
import org.alter.game.model.move.moveTo
import org.alter.game.plugin.KotlinPlugin
import org.alter.game.plugin.Plugin
import org.alter.game.plugin.PluginRepository

class DadPlugin(
    r: PluginRepository,
    world: World,
    server: Server,
) : KotlinPlugin(r, world, server) {
    init {
        spawnNpc("npc.dad", tile = DAD_SPAWN_TILE, walkRadius = 4, direction = Direction.SOUTH)

        onObjOption(obj = DAD_CAVE_ENTRANCE, option = 1, lineOfSightDistance = 2) {
            val obj = player.getInteractingGameObj()
            if (obj.tile.sameAs(DAD_CAVE_ENTRANCE_TILE)) {
                enterDadCave()
            }
        }

        DAD_ARENA_ENTRANCES.forEach { entrance ->
            onObjOption(obj = entrance, option = "open", lineOfSightDistance = 2) {
                val obj = player.getInteractingGameObj()
                if (DAD_ARENA_ENTRANCE_TILES.any { obj.tile.sameAs(it) }) {
                    enterDadArena()
                }
            }
        }

        setCombatDef("npc.dad") {
            configs {
                attackSpeed = 5
                respawnDelay = 35
            }

            aggro {
                radius = 8
                searchDelay = 1
                alwaysAggro()
            }

            stats {
                hitpoints = 120
                attack = 80
                strength = 85
                defence = 65
            }

            bonuses {
                attackCrush = 35
                defenceStab = 20
                defenceSlash = 30
                defenceCrush = 25
                defenceMagic = 10
                defenceRanged = 20
            }

            anims {
                attack = Animation.TROLL_ATTACK
                block = Animation.TROLL_DEFEND
                death = Animation.TROLL_DEATH
            }

            sound {
                attackSound = Sound.TROLL_ATTACK
                blockSound = Sound.TROLL_HIT
                deathSound = Sound.TROLL_DEATH
            }
        }
    }

    private fun Plugin.enterDadCave() {
        player.playSound(Sound.TELEPORT_ALL)
        player.message("You enter Dad's cave.")
        player.moveTo(DAD_GATE_ENTRANCE_TILE)
    }

    private fun Plugin.enterDadArena() {
        player.message("You enter Dad's arena.")
        player.moveTo(DAD_ARENA_CENTER_TILE)
    }

    private companion object {
        private const val DAD_CAVE_ENTRANCE = "object.cave_entrance_36556"
        private val DAD_ARENA_ENTRANCES = arrayOf("object.arena_entrance", "object.arena_entrance_3783")
        private val DAD_CAVE_ENTRANCE_TILE = Tile(2536, 3090, 0)
        private val DAD_ARENA_ENTRANCE_TILES = arrayOf(Tile(2897, 3618, 0), Tile(2897, 3619, 0))
        private val DAD_GATE_ENTRANCE_TILE = Tile(2891, 3618, 0)
        private val DAD_ARENA_CENTER_TILE = Tile(2907, 3623, 0)
        private val DAD_SPAWN_TILE = Tile(2908, 3623, 0)
    }
}
