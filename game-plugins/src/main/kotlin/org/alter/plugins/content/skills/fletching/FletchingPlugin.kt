package org.alter.plugins.content.skills.fletching

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

class FletchingPlugin(
    r: PluginRepository,
    world: org.alter.game.model.World,
    server: Server,
) : KotlinPlugin(r, world, server) {

    init {
        loadService(FletchingService())

        onWorldInit {
            val service = world.getService(FletchingService::class.java) ?: return@onWorldInit

            service.logEntries.forEach { entry ->
                registerLogRecipe(entry)
            }

            service.stringEntries.forEach { entry ->
                registerStringRecipe(entry)
            }
        }
    }

    private fun registerLogRecipe(entry: FletchingLogEntry) {
        onItemOnItem(entry.knife, entry.log) {
            player.queue(TaskPriority.STRONG) {
                handleLogFletching(this, player, entry)
            }
        }
    }

    private fun registerStringRecipe(entry: FletchingStringEntry) {
        onItemOnItem("item.bow_string", entry.unstrung) {
            player.queue(TaskPriority.STRONG) {
                handleStringing(this, player, entry)
            }
        }
    }

    private suspend fun handleLogFletching(
        task: QueueTask,
        player: Player,
        entry: FletchingLogEntry,
    ) {
        val logId = getRSCM(entry.log)
        val knifeId = getRSCM(entry.knife)
        if (!player.inventory.contains(logId) || !player.inventory.contains(knifeId)) {
            return
        }

        val availableProducts = entry.products.filter { player.getSkills().getCurrentLevel(Skills.FLETCHING) >= it.level }
        if (availableProducts.isEmpty()) {
            player.message("You need a higher Fletching level to work this log.")
            return
        }

        val productIds = availableProducts.map { getRSCM(it.item) }.toIntArray()
        task.produceItemBox(
            player = player,
            *productIds,
            title = "What would you like to fletch?",
            maxProducable = player.inventory.getItemCount(logId),
        ) { selectedItem, qty ->
            val product = availableProducts.firstOrNull { getRSCM(it.item) == selectedItem } ?: return@produceItemBox
            player.queue(TaskPriority.STRONG) {
                repeat(qty.coerceAtMost(player.inventory.getItemCount(logId))) {
                    if (!fletchSingleLog(this, player, entry, product)) {
                        return@queue
                    }
                }
            }
        }
    }

    private suspend fun fletchSingleLog(
        task: QueueTask,
        player: Player,
        entry: FletchingLogEntry,
        product: FletchingLogProduct,
    ): Boolean {
        val logId = getRSCM(entry.log)
        val knifeId = getRSCM(entry.knife)
        if (!player.inventory.contains(logId) || !player.inventory.contains(knifeId)) {
            return false
        }
        if (player.getSkills().getCurrentLevel(Skills.FLETCHING) < product.level) {
            player.message("You need a Fletching level of ${product.level} to make that.")
            return false
        }

        player.lock()
        try {
            player.animate(product.animation)
            task.wait(product.ticks)
            if (!player.inventory.contains(logId) || !player.inventory.contains(knifeId)) {
                return false
            }

            player.inventory.remove(logId)
            player.addXp(Skills.FLETCHING, product.experience)
            player.addOrDrop(product.item, product.amount)
            player.message(product.message ?: "You fletch some ${product.item.substringAfter("item.").replace('_', ' ')}.")
            return true
        } finally {
            player.animate(Animation.RESET_CHARACTER)
            player.unlock()
        }
    }

    private suspend fun handleStringing(
        task: QueueTask,
        player: Player,
        entry: FletchingStringEntry,
    ) {
        val unstrungId = getRSCM(entry.unstrung)
        val stringId = getRSCM("item.bow_string")
        if (!player.inventory.contains(unstrungId) || !player.inventory.contains(stringId)) {
            return
        }
        if (player.getSkills().getCurrentLevel(Skills.FLETCHING) < entry.level) {
            player.message("You need a Fletching level of ${entry.level} to string this bow.")
            return
        }

        val amount = minOf(player.inventory.getItemCount(unstrungId), player.inventory.getItemCount(stringId))
        player.lock()
        try {
            repeat(amount) {
                if (!player.inventory.contains(unstrungId) || !player.inventory.contains(stringId)) {
                    return@repeat
                }

                player.animate(entry.animation)
                task.wait(entry.ticks)

                if (!player.inventory.contains(unstrungId) || !player.inventory.contains(stringId)) {
                    return@repeat
                }

                player.inventory.remove(unstrungId)
                player.inventory.remove(stringId)
                player.addOrDrop(entry.strung, 1)
                player.addXp(Skills.FLETCHING, entry.experience)
                player.message(entry.message ?: "You string the ${entry.strung.substringAfter("item.").replace('_', ' ')}.")
            }
        } finally {
            player.animate(Animation.RESET_CHARACTER)
            player.unlock()
        }
    }
}
