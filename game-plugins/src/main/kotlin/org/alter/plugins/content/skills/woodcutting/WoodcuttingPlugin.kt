package org.alter.plugins.content.skills.woodcutting

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
import org.alter.plugins.content.skills.core.roll
import org.alter.plugins.content.skills.core.replaceWithRespawn

class WoodcuttingPlugin(
    r: PluginRepository,
    world: org.alter.game.model.World,
    server: Server,
) : KotlinPlugin(r, world, server) {

    init {
        loadService(WoodcuttingService())

        onWorldInit {
            val service = world.getService(WoodcuttingService::class.java) ?: return@onWorldInit
            service.entries.forEach { entry ->
                entry.objectIds.forEach { objId ->
                    val actions = getObject(objId).actions.filterNotNull().filter { it.equals("chop down", true) || it.equals("chop", true) }
                    actions.forEach { option ->
                        onObjOption(obj = objId, option = option) {
                            val obj = player.getInteractingGameObj()
                            player.queue { chop(this, player, obj, entry) }
                        }
                    }
                }
            }
        }
    }

    private suspend fun chop(task: QueueTask, player: Player, obj: GameObject, entry: WoodcuttingEntry) {
        val tool = player.findBestTool(entry.tools)
        if (tool == null) {
            player.message("You need a suitable axe to chop this tree.")
            return
        }
        if (player.getSkills().getCurrentLevel(Skills.WOODCUTTING) < entry.level) {
            player.message("You need a Woodcutting level of ${entry.level} to chop this tree.")
            return
        }
        if (world.getLiveResourceObject(obj) == null) {
            player.message("That tree has already been chopped down.")
            return
        }

        val animation = axeAnimation(tool.item)
        player.faceTile(obj.tile)
        try {
            while (true) {
                val liveObj = world.getLiveResourceObject(obj) ?: break
                if (player.inventory.isFull) {
                    player.message("You don't have enough inventory space to hold any more logs.")
                    break
                }

                player.animate(animation)
                task.wait(entry.ticks)

                val liveAfterWait = world.getLiveResourceObject(obj) ?: break
                if (!liveAfterWait.isSpawned(world)) {
                    break
                }

                val successChance = woodcuttingChance(player.getSkills().getCurrentLevel(Skills.WOODCUTTING), entry.level, tool.level)
                if (world.randomDouble() <= successChance) {
                    val reward = world.roll(entry.outputs)
                    player.addXp(Skills.WOODCUTTING, entry.experience)
                    player.addOrDrop(reward.item, reward.amount, "Some logs fall to the floor.")
                    player.message("You get some ${reward.item.replace('_', ' ')}.")
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

    private fun woodcuttingChance(level: Int, requirement: Int, toolLevel: Int): Double {
        val base = 0.25 + (level - requirement).coerceAtLeast(0) * 0.015
        val toolBonus = toolLevel * 0.01
        return (base + toolBonus).coerceIn(0.2, 0.9)
    }

    private fun axeAnimation(tool: String): Int =
        when (tool) {
            "dragon_axe" -> Animation.WOODCUTTING_DRAGON_AXE
            "rune_axe" -> Animation.WOODCUTTING_RUNE_AXE
            "adamant_axe" -> Animation.WOODCUTTING_ADAMANT_AXE
            "mithril_axe" -> Animation.WOODCUTTING_MITHRIL_AXE
            "steel_axe" -> Animation.WOODCUTTING_STEEL_AXE
            "iron_axe" -> Animation.WOODCUTTING_IRON_AXE
            else -> Animation.WOODCUTTING_BRONZE_AXE
        }
}
