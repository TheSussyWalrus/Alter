package org.alter.plugins.content.skills.crafting

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

class CraftingPlugin(
    r: PluginRepository,
    world: org.alter.game.model.World,
    server: Server,
) : KotlinPlugin(r, world, server) {

    init {
        loadService(CraftingService())

        onWorldInit {
            val service = world.getService(CraftingService::class.java) ?: return@onWorldInit

            service.gemEntries.forEach { entry ->
                registerGemCut(entry)
            }

            service.leatherEntries.forEach { entry ->
                registerLeather(entry)
            }
        }
    }

    private fun registerGemCut(entry: CraftingGemEntry) {
        onItemOnItem("item.chisel", entry.uncut) {
            player.queue(TaskPriority.STRONG) {
                cutGem(this, player, entry)
            }
        }
    }

    private fun registerLeather(entry: CraftingLeatherEntry) {
        onItemOnItem(entry.needle, entry.leather) {
            player.queue(TaskPriority.STRONG) {
                handleLeather(this, player, entry)
            }
        }
    }

    private suspend fun cutGem(
        task: QueueTask,
        player: Player,
        entry: CraftingGemEntry,
    ) {
        val chiselId = getRSCM("item.chisel")
        val uncutId = getRSCM(entry.uncut)
        if (!player.inventory.contains(chiselId) || !player.inventory.contains(uncutId)) {
            return
        }
        if (player.getSkills().getCurrentLevel(Skills.CRAFTING) < entry.level) {
            player.message("You need a Crafting level of ${entry.level} to cut this gem.")
            return
        }

        player.animate(entry.animation)
        try {
            task.wait(entry.ticks)
            if (!player.inventory.contains(chiselId) || !player.inventory.contains(uncutId)) {
                return
            }

            player.inventory.remove(uncutId)
            player.addXp(Skills.CRAFTING, entry.experience)
            player.addOrDrop(entry.cut, 1)
            player.message("You cut the ${entry.cut.substringAfter("item.").replace('_', ' ')}.")
        } finally {
            player.animate(Animation.RESET_CHARACTER)
        }
    }

    private suspend fun handleLeather(
        task: QueueTask,
        player: Player,
        entry: CraftingLeatherEntry,
    ) {
        val needleId = getRSCM(entry.needle)
        val leatherId = getRSCM(entry.leather)
        val threadId = getRSCM(entry.thread)
        if (!player.inventory.contains(needleId) || !player.inventory.contains(leatherId) || !player.inventory.contains(threadId)) {
            return
        }

        val availableProducts = entry.products.filter { player.getSkills().getCurrentLevel(Skills.CRAFTING) >= it.level }
        if (availableProducts.isEmpty()) {
            player.message("You need a higher Crafting level to work this leather.")
            return
        }

        val productIds = availableProducts.map { getRSCM(it.item) }.toIntArray()
        task.produceItemBox(
            player = player,
            *productIds,
            title = "What would you like to craft?",
            maxProducable = minOf(player.inventory.getItemCount(leatherId), player.inventory.getItemCount(threadId)),
        ) { selectedItem, qty ->
            val product = availableProducts.firstOrNull { getRSCM(it.item) == selectedItem } ?: return@produceItemBox
            player.queue(TaskPriority.STRONG) {
                repeat(qty.coerceAtMost(minOf(player.inventory.getItemCount(leatherId), player.inventory.getItemCount(threadId)))) {
                    if (!craftLeatherPiece(this, player, entry, product)) {
                        return@queue
                    }
                }
            }
        }
    }

    private suspend fun craftLeatherPiece(
        task: QueueTask,
        player: Player,
        entry: CraftingLeatherEntry,
        product: CraftingLeatherProduct,
    ): Boolean {
        val needleId = getRSCM(entry.needle)
        val leatherId = getRSCM(entry.leather)
        val threadId = getRSCM(entry.thread)
        if (!player.inventory.contains(needleId) || !player.inventory.contains(leatherId) || !player.inventory.contains(threadId)) {
            return false
        }
        if (player.getSkills().getCurrentLevel(Skills.CRAFTING) < product.level) {
            player.message("You need a Crafting level of ${product.level} to make that.")
            return false
        }

        player.animate(product.animation)
        try {
            task.wait(product.ticks)
            if (!player.inventory.contains(needleId) || !player.inventory.contains(leatherId) || !player.inventory.contains(threadId)) {
                return false
            }

            player.inventory.remove(leatherId)
            player.inventory.remove(threadId)
            player.addXp(Skills.CRAFTING, product.experience)
            player.addOrDrop(product.item, product.amount)
            player.message(product.message ?: "You craft the ${product.item.substringAfter("item.").replace('_', ' ')}.")
            return true
        } finally {
            player.animate(Animation.RESET_CHARACTER)
        }
    }
}
