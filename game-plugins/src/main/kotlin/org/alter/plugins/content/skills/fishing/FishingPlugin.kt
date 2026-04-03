package org.alter.plugins.content.skills.fishing

import dev.openrune.cache.CacheManager.getNpc
import org.alter.api.Skills
import org.alter.api.cfg.Animation
import org.alter.api.ext.*
import org.alter.game.Server
import org.alter.game.model.entity.Npc
import org.alter.game.model.entity.Player
import org.alter.game.model.queue.QueueTask
import org.alter.game.plugin.KotlinPlugin
import org.alter.game.plugin.PluginRepository
import org.alter.plugins.content.skills.core.addOrDrop
import org.alter.plugins.content.skills.core.findBestTool

class FishingPlugin(
    r: PluginRepository,
    world: org.alter.game.model.World,
    server: Server,
) : KotlinPlugin(r, world, server) {

    init {
        loadService(FishingService())

        onWorldInit {
            val service = world.getService(FishingService::class.java) ?: return@onWorldInit
            service.entries.forEach { entry ->
                entry.npcs.forEach { npcName ->
                    val npcId = org.alter.rscm.RSCM.getRSCM(npcName)
                    val option = entry.option.lowercase()
                    if (getNpc(npcId).actions.filterNotNull().none { it.equals(option, true) }) {
                        return@forEach
                    }
                    onNpcOption(npc = npcName, option = option) {
                        val npc = player.getInteractingNpc()
                        player.queue { fish(this, player, npc, entry) }
                    }
                }
            }
        }
    }

    private suspend fun fish(task: QueueTask, player: Player, npc: Npc, entry: FishingEntry) {
        val tool = player.findBestTool(entry.tools)
        if (tool == null) {
            player.message("You need a suitable tool to fish here.")
            return
        }
        if (player.getSkills().getCurrentLevel(Skills.FISHING) < entry.level) {
            player.message("You need a Fishing level of ${entry.level} to fish here.")
            return
        }
        if (entry.bait != null && !player.inventory.contains(entry.bait.item)) {
            player.message("You need ${entry.bait.item.replace('_', ' ')} to fish here.")
            return
        }

        player.facePawn(npc)
        try {
            while (true) {
                if (player.inventory.isFull) {
                    player.message("You don't have enough inventory space to hold any more fish.")
                    break
                }
                if (entry.bait != null && !player.inventory.contains(entry.bait.item)) {
                    player.message("You have run out of ${entry.bait.item.replace('_', ' ')}.")
                    break
                }

                player.animate(fishingAnimation(tool.item, entry.option))
                task.wait(entry.ticks)

                val successChance = fishingChance(player.getSkills().getCurrentLevel(Skills.FISHING), entry.level, tool.level)
                if (world.randomDouble() <= successChance) {
                    val reward = rollCatch(player.getSkills().getCurrentLevel(Skills.FISHING), entry.outputs) ?: continue
                    entry.bait?.let { player.inventory.remove(it.item, it.amount, assureFullRemoval = true) }
                    player.addXp(Skills.FISHING, entry.experience)
                    player.addOrDrop(reward.item, reward.amount, "The fish falls to the floor.")
                    player.message("You catch some ${reward.item.replace('_', ' ')}.")
                }
            }
        } finally {
            player.animate(Animation.RESET_CHARACTER)
            npc.resetFacePawn()
            player.resetFacePawn()
        }
    }

    private fun fishingChance(level: Int, requirement: Int, toolLevel: Int): Double {
        val base = 0.3 + (level - requirement).coerceAtLeast(0) * 0.015
        val toolBonus = toolLevel * 0.01
        return (base + toolBonus).coerceIn(0.25, 0.92)
    }

    private fun fishingAnimation(tool: String, option: String): Int =
        when {
            option.equals("cage", true) -> Animation.FISHING_LOBSTER_POT
            option.equals("harpoon", true) && tool == "dragon_harpoon" -> Animation.FISHING_DRAGON_HARPOON
            option.equals("harpoon", true) -> Animation.FISHING_HARPOON
            option.equals("net", true) -> Animation.FISHING_NET
            else -> Animation.FISHING_ROD
        }

    private fun rollCatch(level: Int, outputs: List<FishingCatch>): CaughtFish? {
        val available = outputs.filter { level >= it.level }
        if (available.isEmpty()) {
            return null
        }
        val totalWeight = available.sumOf { it.weight }
        var roll = world.randomDouble() * totalWeight
        for (output in available) {
            roll -= output.weight
            if (roll <= 0.0) {
                val amount =
                    if (output.safeMin == output.safeMax) {
                        output.safeMin
                    } else {
                        world.random(output.safeMin..output.safeMax)
                    }
                return CaughtFish(output.item, amount)
            }
        }
        val fallback = available.last()
        val amount =
            if (fallback.safeMin == fallback.safeMax) {
                fallback.safeMin
            } else {
                world.random(fallback.safeMin..fallback.safeMax)
            }
        return CaughtFish(fallback.item, amount)
    }

    private data class CaughtFish(val item: String, val amount: Int)
}
