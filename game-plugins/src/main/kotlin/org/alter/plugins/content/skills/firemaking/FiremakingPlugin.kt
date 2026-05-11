package org.alter.plugins.content.skills.firemaking

import org.alter.api.Skills
import org.alter.api.cfg.Animation
import org.alter.api.ext.*
import org.alter.game.Server
import org.alter.game.model.Tile
import org.alter.game.model.entity.DynamicObject
import org.alter.game.model.move.moveTo
import org.alter.game.plugin.KotlinPlugin
import org.alter.game.plugin.PluginRepository
import org.alter.rscm.RSCM.getRSCM

class FiremakingPlugin(
    r: PluginRepository,
    world: org.alter.game.model.World,
    server: Server,
) : KotlinPlugin(r, world, server) {

    private val tinderbox = getRSCM("item.tinderbox")
    private val fireObject = getRSCM("object.fire_26185")

    init {
        loadService(FiremakingService())

        onWorldInit {
            val service = world.getService(FiremakingService::class.java) ?: return@onWorldInit
            service.entries.forEach { entry ->
                onItemOnItem("item.tinderbox", entry.log) {
                    player.queue {
                        val logId = getRSCM(entry.log)
                        if (player.getSkills().getCurrentLevel(Skills.FIREMAKING) < entry.level) {
                            player.message("You need a Firemaking level of ${entry.level} to light these logs.")
                            return@queue
                        }
                        if (!player.inventory.contains(tinderbox) || !player.inventory.contains(logId)) {
                            return@queue
                        }
                        if (world.getObject(player.tile, 10) != null) {
                            player.message("You can't light a fire here.")
                            return@queue
                        }
                        val fireTile = player.tile

                        try {
                            player.animate(Animation.FIREMAKING_TINDERBOX)
                            wait(entry.ticks)
                            if (!player.inventory.contains(logId)) {
                                return@queue
                            }

                            player.inventory.remove(logId)
                            player.addXp(Skills.FIREMAKING, entry.experience)
                            world.spawn(DynamicObject(fireObject, 10, 0, fireTile))
                            world.queue {
                                wait(100)
                                world.getObject(fireTile, 10)?.let { existing ->
                                    if (existing.id == fireObject) {
                                        world.remove(existing)
                                    }
                                }
                            }
                            player.message("You light the logs.")
                            player.moveTo(Tile(player.tile.x, player.tile.z - 1, player.tile.height))
                        } finally {
                            player.animate(Animation.RESET_CHARACTER)
                        }
                    }
                }
            }
        }
    }
}
