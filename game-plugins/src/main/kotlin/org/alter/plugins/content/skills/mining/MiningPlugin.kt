package org.alter.plugins.content.skills.mining

import dev.openrune.cache.CacheManager.getObject
import org.alter.api.Skills
import org.alter.api.cfg.Animation
import org.alter.api.ext.*
import org.alter.game.Server
import org.alter.game.model.entity.GameObject
import org.alter.game.model.entity.Player
import org.alter.game.model.queue.QueueTask
import org.alter.game.plugin.KotlinPlugin
import org.alter.game.plugin.PluginRepository
import org.alter.plugins.content.skills.core.findBestTool
import org.alter.plugins.content.skills.core.addOrDrop
import org.alter.plugins.content.skills.core.getLiveResourceObject
import org.alter.plugins.content.skills.core.replaceWithRespawn
import org.alter.plugins.content.skills.core.roll

class MiningPlugin(
    r: PluginRepository,
    world: org.alter.game.model.World,
    server: Server,
) : KotlinPlugin(r, world, server) {

    init {
        loadService(MiningService())

        onWorldInit {
            val service = world.getService(MiningService::class.java) ?: return@onWorldInit
            service.entries.forEach { entry ->
                entry.objectIds.forEach { objId ->
                    val actions = getObject(objId).actions.filterNotNull().filter { it.equals("mine", true) || it.equals("prospect", true) }
                    actions.filter { it.equals("mine", true) }.forEach { option ->
                        onObjOption(obj = objId, option = option) {
                            val obj = player.getInteractingGameObj()
                            player.queue { mine(this, player, obj, entry) }
                        }
                    }
                }
            }
        }
    }

    private suspend fun mine(task: QueueTask, player: Player, obj: GameObject, entry: MiningEntry) {
        val tool = player.findBestTool(entry.tools)
        if (tool == null) {
            player.message("You need a suitable pickaxe to mine this rock.")
            return
        }
        if (player.getSkills().getCurrentLevel(Skills.MINING) < entry.level) {
            player.message("You need a Mining level of ${entry.level} to mine this rock.")
            return
        }
        if (world.getLiveResourceObject(obj) == null) {
            player.message("There is currently no ore available in this rock.")
            return
        }

        val animation = pickaxeAnimation(tool.item)
        player.faceTile(obj.tile)
        try {
            while (true) {
                val liveObj = world.getLiveResourceObject(obj) ?: break
                if (player.inventory.isFull) {
                    player.message("You don't have enough inventory space to hold any more ore.")
                    break
                }

                player.animate(animation)
                task.wait(entry.ticks)

                val liveAfterWait = world.getLiveResourceObject(obj) ?: break
                if (!liveAfterWait.isSpawned(world)) {
                    break
                }

                val successChance = miningChance(player.getSkills().getCurrentLevel(Skills.MINING), entry.level, tool.level)
                if (world.randomDouble() <= successChance) {
                    val reward = world.roll(entry.outputs)
                    player.addXp(Skills.MINING, entry.experience)
                    player.addOrDrop(reward.item, reward.amount, "The ore drops to the floor.")
                    player.message("You manage to mine some ${reward.item.replace('_', ' ')}.")
                    if (world.randomDouble() <= entry.depletionChance) {
                        world.replaceWithRespawn(liveObj, entry.depletedObjectId, entry.respawnTicks)
                        break
                    }
                }
            }
        } finally {
            player.animate(Animation.RESET_CHARACTER)
        }
    }

    private fun miningChance(level: Int, requirement: Int, toolLevel: Int): Double {
        val base = 0.25 + (level - requirement).coerceAtLeast(0) * 0.015
        val toolBonus = toolLevel * 0.01
        return (base + toolBonus).coerceIn(0.2, 0.9)
    }

    private fun pickaxeAnimation(tool: String): Int =
        when (tool) {
            "dragon_pickaxe" -> Animation.MINING_DRAGON_PICKAXE
            "rune_pickaxe" -> Animation.MINING_RUNE_PICKAXE
            "adamant_pickaxe" -> Animation.MINING_ADAMANT_PICKAXE
            "mithril_pickaxe" -> Animation.MINING_MITHRIL_PICKAXE
            "steel_pickaxe" -> Animation.MINING_STEEL_PICKAXE
            "iron_pickaxe" -> Animation.MINING_IRON_PICKAXE
            else -> Animation.MINING_BRONZE_PICKAXE
        }
}
