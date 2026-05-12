package org.alter.plugins.content.npcs.dad

import org.alter.api.PrayerIcon
import org.alter.api.cfg.Animation
import org.alter.api.cfg.Graphic
import org.alter.api.cfg.Sound
import org.alter.api.ext.createProjectile
import org.alter.api.ext.hasPrayerIcon
import org.alter.api.ext.hit
import org.alter.api.ext.message
import org.alter.api.ext.playSound
import org.alter.game.Server
import org.alter.game.model.Direction
import org.alter.game.model.ForcedMovement
import org.alter.game.model.World
import org.alter.game.model.entity.Npc
import org.alter.game.model.entity.Player
import org.alter.game.model.queue.TaskPriority
import org.alter.game.model.timer.ATTACK_DELAY
import org.alter.game.plugin.KotlinPlugin
import org.alter.game.plugin.PluginRepository
import org.alter.plugins.content.combat.getCombatTarget
import org.alter.plugins.content.combat.isBeingAttacked
import org.alter.plugins.content.combat.strategy.RangedCombatStrategy
import org.alter.rscm.RSCM.getRSCM

class DadMechanicsPlugin(
    r: PluginRepository,
    world: World,
    server: Server,
) : KotlinPlugin(r, world, server) {
    private val dadNpcId = getRSCM("npc.dad")

    init {
        onWorldInit {
            world.queue {
                var nextSwatCycle = NO_SWAT_SCHEDULED

                while (true) {
                    val activePlayers = DadArena.activePlayers(world)
                    val dad = findDad()

                    peltUnsafePlayers(activePlayers)

                    if (dad == null || activePlayers.isEmpty() || !dad.hasActiveFight()) {
                        nextSwatCycle = NO_SWAT_SCHEDULED
                    } else {
                        if (nextSwatCycle == NO_SWAT_SCHEDULED) {
                            nextSwatCycle = world.currentCycle + INITIAL_SWAT_DELAY
                        }

                        if (world.currentCycle >= nextSwatCycle) {
                            telegraphSwat(dad, activePlayers)
                            wait(SWAT_TELEGRAPH_DELAY)
                            performSwat(dad)
                            nextSwatCycle = world.currentCycle + SWAT_INTERVAL
                        }
                    }

                    wait(THROWER_PULSE_INTERVAL)
                }
            }
        }
    }

    private fun peltUnsafePlayers(players: List<Player>) {
        players.filterNot { DadArena.isInSafeCenter(it.tile) }.forEach { player ->
            val thrower = findThrower(DadArena.closestThrower(player.tile)) ?: return@forEach
            if (!player.isOnline || !player.isAlive() || !DadArena.contains(player.tile)) {
                return@forEach
            }

            thrower.facePawn(player)
            thrower.animate(Animation.THROWER_TROLL_ATTACK)
            player.playSound(Sound.TROLL_THROW_ROCK)
            world.spawn(
                thrower.createProjectile(
                    target = player,
                    gfx = Graphic.THROWER_TROLL_ROCK,
                    startHeight = 43,
                    endHeight = 31,
                    delay = 32,
                    angle = 15,
                    steepness = 11,
                ),
            )
            player.hit(
                damage = player.throwerDamage(),
                delay = RangedCombatStrategy.getHitDelay(thrower.getFrontFacingTile(player), player.getCentreTile()),
            )
        }
    }

    private fun Player.throwerDamage(): Int =
        if (hasPrayerIcon(PrayerIcon.PROTECT_FROM_MISSILES)) {
            0
        } else {
            world.random(THROWER_DAMAGE)
        }

    private fun telegraphSwat(
        dad: Npc,
        players: List<Player>,
    ) {
        dad.forceChat("Dad winds up a huge swing!")
        dad.animate(Animation.TROLL_ATTACK)
        dad.timers[ATTACK_DELAY] = SWAT_TELEGRAPH_DELAY + POST_SWAT_ATTACK_DELAY
        players.forEach { player -> player.message("Dad winds up a huge swing!") }
    }

    private fun performSwat(dad: Npc) {
        if (!dad.isSpawned() || !dad.isAlive()) {
            return
        }

        dad.forceChat("Hrrraah!")
        dad.animate(Animation.TROLL_ATTACK)
        dad.timers[ATTACK_DELAY] = POST_SWAT_ATTACK_DELAY

        DadArena.activePlayers(world).forEach { player ->
            player.playSound(Sound.TROLL_CHAMPION_SWING)
            player.hit(damage = world.random(SWAT_DAMAGE))
            swatPlayer(player)
        }
    }

    private fun swatPlayer(player: Player) {
        player.queue(TaskPriority.STRONG) {
            if (!player.isOnline || !player.isAlive() || !DadArena.contains(player.tile)) {
                return@queue
            }

            val start = player.tile
            val destination = DadArena.swatDestination(start)
            val direction = Direction.between(start, destination)
            val movement =
                ForcedMovement.of(
                    src = start,
                    dst = destination,
                    clientDuration1 = 24,
                    clientDuration2 = 48,
                    directionAngle = direction.angle,
                )
            player.forceMove(this, movement)
        }
    }

    private fun findDad(): Npc? =
        world.npcs.firstOrNull { npc ->
            npc.id == dadNpcId &&
                npc.isSpawned() &&
                npc.isAlive() &&
                DadArena.contains(npc.tile)
        }

    private fun findThrower(spawn: DadArena.ThrowerSpawn): Npc? {
        val npcId = getRSCM(spawn.npc)
        return world.npcs.firstOrNull { npc ->
            npc.id == npcId &&
                npc.isSpawned() &&
                npc.spawnTile.sameAs(spawn.tile)
        }
    }

    private fun Npc.hasActiveFight(): Boolean = getCombatTarget() != null || isBeingAttacked()

    private companion object {
        private const val NO_SWAT_SCHEDULED = -1
        private const val THROWER_PULSE_INTERVAL = 4
        private const val INITIAL_SWAT_DELAY = 15
        private const val SWAT_INTERVAL = 25
        private const val SWAT_TELEGRAPH_DELAY = 2
        private const val POST_SWAT_ATTACK_DELAY = 5
        private val THROWER_DAMAGE = 2..6
        private val SWAT_DAMAGE = 4..10
    }
}
