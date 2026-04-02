package org.alter.plugins.content.skills.cooking

import org.alter.api.Skills
import org.alter.api.cfg.Animation
import org.alter.api.ext.*
import org.alter.game.Server
import org.alter.game.model.queue.TaskPriority
import org.alter.game.plugin.KotlinPlugin
import org.alter.game.plugin.PluginRepository
import org.alter.rscm.RSCM.getRSCM

class CookingPlugin(
    r: PluginRepository,
    world: org.alter.game.model.World,
    server: Server,
) : KotlinPlugin(r, world, server) {

    private val heatSources = arrayOf("object.cooking_range", "object.cooking_range_4172", "object.cooking_range_8750", "object.fire_26185")

    init {
        loadService(CookingService())

        onWorldInit {
            val service = world.getService(CookingService::class.java) ?: return@onWorldInit
            service.entries.forEach { entry ->
                heatSources.forEach { obj ->
                    onItemOnObj(obj = obj, item = entry.raw) {
                        player.queue(TaskPriority.STRONG) {
                            val def = service.lookup(getRSCM(entry.raw)) ?: return@queue
                            val rawId = getRSCM(def.raw)
                            if (!player.inventory.contains(rawId)) {
                                return@queue
                            }
                            if (player.getSkills().getCurrentLevel(Skills.COOKING) < def.level) {
                                player.message("You need a Cooking level of ${def.level} to cook this.")
                                return@queue
                            }

                            val source = player.getInteractingGameObj()
                            player.faceTile(source.tile)
                            player.lock()
                            try {
                                player.animate(if (source.id == getRSCM("object.fire_26185")) Animation.COOKING_ON_FIRE else Animation.COOKING_ON_RANGE)
                                wait(def.ticks)
                                if (!player.inventory.contains(rawId)) {
                                    return@queue
                                }

                                player.inventory.remove(rawId)
                                val success = !shouldBurn(player.getSkills().getCurrentLevel(Skills.COOKING), def.level, def.stopBurn)
                                val output = if (success) def.cooked else def.burnt
                                player.inventory.add(item = output, amount = 1)
                                if (success) {
                                    player.addXp(Skills.COOKING, def.experience)
                                    player.message("You successfully cook the ${def.cooked.replace('_', ' ')}.")
                                } else {
                                    player.message("You accidentally burn the food.")
                                }
                            } finally {
                                player.animate(Animation.RESET_CHARACTER)
                                player.unlock()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun shouldBurn(level: Int, requirement: Int, stopBurn: Int): Boolean {
        if (level >= stopBurn) {
            return false
        }
        val span = (stopBurn - requirement).coerceAtLeast(1)
        val progress = (level - requirement).coerceAtLeast(0).toDouble() / span
        val burnChance = (0.55 - (progress * 0.5)).coerceIn(0.05, 0.55)
        return world.randomDouble() < burnChance
    }
}
