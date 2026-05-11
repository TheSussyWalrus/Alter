package org.alter.plugins.content.skills.herblore

import org.alter.api.Skills
import org.alter.api.cfg.Animation
import org.alter.api.ext.*
import org.alter.game.Server
import org.alter.game.model.entity.Player
import org.alter.game.model.queue.QueueTask
import org.alter.game.model.queue.TaskPriority
import org.alter.game.plugin.KotlinPlugin
import org.alter.game.plugin.PluginRepository
import org.alter.plugins.content.skills.core.addOrDrop
import org.alter.rscm.RSCM.getRSCM

class HerblorePlugin(
    r: PluginRepository,
    world: org.alter.game.model.World,
    server: Server,
) : KotlinPlugin(r, world, server) {

    init {
        loadService(HerbloreService())

        onWorldInit {
            val service = world.getService(HerbloreService::class.java) ?: return@onWorldInit

            service.cleaningEntries.forEach { entry ->
                registerCleaning(entry)
            }

            service.unfinishedEntries.forEach { entry ->
                registerUnfinished(entry)
            }

            service.finishedEntries.forEach { entry ->
                registerFinished(entry)
            }
        }
    }

    private fun registerCleaning(entry: HerbCleaningEntry) {
        onItemOption(item = entry.grimy, option = "clean") {
            player.queue(TaskPriority.STRONG) {
                cleanHerb(this, player, entry)
            }
        }
    }

    private fun registerUnfinished(entry: UnfinishedPotionEntry) {
        onItemOnItem(entry.herb, entry.vial) {
            player.queue(TaskPriority.STRONG) {
                makeUnfinishedPotion(this, player, entry)
            }
        }
    }

    private fun registerFinished(entry: FinishedPotionEntry) {
        onItemOnItem(entry.unfinished, entry.secondary) {
            player.queue(TaskPriority.STRONG) {
                finishPotion(this, player, entry)
            }
        }
    }

    private suspend fun cleanHerb(
        task: QueueTask,
        player: Player,
        entry: HerbCleaningEntry,
    ) {
        val grimyId = getRSCM(entry.grimy)
        val cleanId = getRSCM(entry.clean)
        if (!player.inventory.contains(grimyId)) {
            return
        }
        if (player.getSkills().getCurrentLevel(Skills.HERBLORE) < entry.level) {
            player.message("You need a Herblore level of ${entry.level} to clean this herb.")
            return
        }

        try {
            while (player.inventory.contains(grimyId)) {
                player.animate(entry.animation)
                task.wait(entry.ticks)
                if (!player.inventory.contains(grimyId)) {
                    return
                }

                if (player.inventory.remove(grimyId, 1, assureFullRemoval = true).hasFailed()) {
                    return
                }
                player.addOrDrop(entry.clean, 1)
                player.addXp(Skills.HERBLORE, entry.experience)
                player.message("You clean the ${entry.clean.substringAfter("item.").replace('_', ' ')}.")
            }
        } finally {
            player.animate(Animation.RESET_CHARACTER)
        }
    }

    private suspend fun makeUnfinishedPotion(
        task: QueueTask,
        player: Player,
        entry: UnfinishedPotionEntry,
    ) {
        val herbId = getRSCM(entry.herb)
        val vialId = getRSCM(entry.vial)
        if (!player.inventory.contains(herbId) || !player.inventory.contains(vialId)) {
            return
        }
        if (player.getSkills().getCurrentLevel(Skills.HERBLORE) < entry.level) {
            player.message("You need a Herblore level of ${entry.level} to make this potion.")
            return
        }

        try {
            while (player.inventory.contains(herbId) && player.inventory.contains(vialId)) {
                player.animate(entry.animation)
                task.wait(entry.ticks)
                if (!player.inventory.contains(herbId) || !player.inventory.contains(vialId)) {
                    return
                }

                if (player.inventory.remove(herbId, 1, assureFullRemoval = true).hasFailed()) {
                    return
                }
                if (player.inventory.remove(vialId, 1, assureFullRemoval = true).hasFailed()) {
                    player.addOrDrop(entry.herb, 1)
                    return
                }

                player.addOrDrop(entry.output, 1)
                player.addXp(Skills.HERBLORE, entry.experience)
                player.message("You put the ${entry.herb.substringAfter("item.").replace('_', ' ')} into the vial of water.")
            }
        } finally {
            player.animate(Animation.RESET_CHARACTER)
        }
    }

    private suspend fun finishPotion(
        task: QueueTask,
        player: Player,
        entry: FinishedPotionEntry,
    ) {
        val unfinishedId = getRSCM(entry.unfinished)
        val secondaryId = getRSCM(entry.secondary)
        if (!player.inventory.contains(unfinishedId) || !player.inventory.contains(secondaryId)) {
            return
        }
        if (player.getSkills().getCurrentLevel(Skills.HERBLORE) < entry.level) {
            player.message("You need a Herblore level of ${entry.level} to make this potion.")
            return
        }

        try {
            while (player.inventory.contains(unfinishedId) && player.inventory.contains(secondaryId)) {
                player.animate(entry.animation)
                task.wait(entry.ticks)
                if (!player.inventory.contains(unfinishedId) || !player.inventory.contains(secondaryId)) {
                    return
                }

                if (player.inventory.remove(unfinishedId, 1, assureFullRemoval = true).hasFailed()) {
                    return
                }
                if (player.inventory.remove(secondaryId, 1, assureFullRemoval = true).hasFailed()) {
                    player.addOrDrop(entry.unfinished, 1)
                    return
                }

                player.addOrDrop(entry.output, 1)
                player.addXp(Skills.HERBLORE, entry.experience)
                player.message("You make ${articleFor(entry.output)} ${entry.output.substringAfter("item.").replace('_', ' ')}.")
            }
        } finally {
            player.animate(Animation.RESET_CHARACTER)
        }
    }

    private fun articleFor(name: String): String {
        val first = name.substringAfter("item.").firstOrNull()?.lowercaseChar() ?: return "a"
        return if (first in listOf('a', 'e', 'i', 'o', 'u')) "an" else "a"
    }
}
