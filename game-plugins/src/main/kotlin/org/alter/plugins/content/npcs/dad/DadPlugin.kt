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
import org.alter.game.model.World
import org.alter.game.model.combat.NpcCombatDef
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
        setMultiCombatRegion(DadArena.REGION_ID)

        spawnNpc("npc.dad", tile = DadArena.DAD_SPAWN_TILE, walkRadius = 4, direction = Direction.SOUTH)
        DadArena.THROWER_SPAWNS.forEach { spawn ->
            spawnNpc(spawn.npc, tile = spawn.tile, walkRadius = 0, direction = spawn.direction)
        }

        onObjOption(obj = DAD_CAVE_ENTRANCE, option = 1, lineOfSightDistance = 2) {
            val obj = player.getInteractingGameObj()
            if (obj.tile.sameAs(DadArena.CAVE_ENTRANCE_TILE)) {
                enterDadCave()
            }
        }

        DAD_ARENA_ENTRANCES.forEach { entrance ->
            onObjOption(obj = entrance, option = "open", lineOfSightDistance = 2) {
                val obj = player.getInteractingGameObj()
                if (DadArena.ENTRANCE_TILES.any { obj.tile.sameAs(it) }) {
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

        DadArena.THROWER_SPAWNS.map { it.npc }.distinct().forEach { thrower ->
            setCombatDef(thrower, THROWER_COMBAT_DEF)
        }
    }

    private fun Plugin.enterDadCave() {
        player.playSound(Sound.TELEPORT_ALL)
        player.message("You enter Dad's cave.")
        player.moveTo(DadArena.GATE_ENTRANCE_TILE)
    }

    private fun Plugin.enterDadArena() {
        player.message("You enter Dad's arena.")
        player.moveTo(DadArena.CENTER)
    }

    private companion object {
        private const val DAD_CAVE_ENTRANCE = "object.cave_entrance_36556"
        private val DAD_ARENA_ENTRANCES = arrayOf("object.arena_entrance", "object.arena_entrance_3783")
        private val THROWER_COMBAT_DEF =
            NpcCombatDef.DEFAULT.copy(
                hitpoints = -1,
                attackSpeed = 4,
                attackAnimation = Animation.THROWER_TROLL_ATTACK,
                blockAnimation = Animation.TROLL_DEFEND,
                deathAnimation = listOf(Animation.TROLL_DEATH),
                defaultAttackSound = Sound.TROLL_THROW_ROCK,
                defaultBlockSound = Sound.TROLL_HIT,
                defaultDeathSound = Sound.TROLL_DEATH,
                respawnDelay = 0,
                aggressiveRadius = 0,
                aggroTargetDelay = 0,
                aggressiveTimer = Int.MIN_VALUE,
                followRange = 0,
                LootTables = null,
            )
    }
}
