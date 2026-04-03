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

            service.jewelryEntries.forEach { entry ->
                registerJewelry(entry)
            }

            service.stringEntries.forEach { entry ->
                registerStringing(entry)
            }

            service.spinningEntries.forEach { entry ->
                registerSpinning(entry)
            }

            service.potteryEntries.forEach { entry ->
                registerPottery(entry)
            }

            service.potteryFireEntries.forEach { entry ->
                registerPotteryFire(entry)
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

    private fun registerJewelry(entry: CraftingJewelryEntry) {
        onItemOnItem(entry.mould, entry.bar) {
            player.queue(TaskPriority.STRONG) {
                handleJewelry(this, player, entry)
            }
        }
    }

    private fun registerStringing(entry: CraftingStringEntry) {
        onItemOnItem(entry.base, entry.string) {
            player.queue(TaskPriority.STRONG) {
                handleStringing(this, player, entry)
            }
        }
    }

    private fun registerSpinning(entry: CraftingSpinningEntry) {
        entry.objectNames.forEach { obj ->
            onItemOnObj(obj = obj, item = entry.input) {
                player.queue(TaskPriority.STRONG) {
                    handleSpinning(this, player, entry)
                }
            }
        }
    }

    private fun registerPottery(entry: CraftingPotteryEntry) {
        entry.objectNames.forEach { obj ->
            onItemOnObj(obj = obj, item = entry.input) {
                player.queue(TaskPriority.STRONG) {
                    handlePottery(this, player, entry)
                }
            }
        }
    }

    private fun registerPotteryFire(entry: CraftingPotteryFireEntry) {
        entry.objectNames.forEach { obj ->
            onItemOnObj(obj = obj, item = entry.unfired) {
                player.queue(TaskPriority.STRONG) {
                    handlePotteryFire(this, player, entry)
                }
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

        try {
            while (player.inventory.contains(chiselId) && player.inventory.contains(uncutId)) {
                player.animate(entry.animation)
                task.wait(entry.ticks)
                if (!player.inventory.contains(chiselId) || !player.inventory.contains(uncutId)) {
                    return
                }

                if (player.inventory.remove(uncutId, 1, assureFullRemoval = true).hasFailed()) {
                    return
                }
                player.addXp(Skills.CRAFTING, entry.experience)
                player.addOrDrop(entry.cut, 1)
                player.message("You cut the ${entry.cut.substringAfter("item.").replace('_', ' ')}.")
            }
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
            maxProducable =
                availableProducts.maxOfOrNull { product ->
                    minOf(
                        player.inventory.getItemCount(leatherId) / product.leatherAmount,
                        player.inventory.getItemCount(threadId) / product.threadAmount,
                    )
                } ?: 0,
        ) { selectedItem, qty ->
            val product = availableProducts.firstOrNull { getRSCM(it.item) == selectedItem } ?: return@produceItemBox
            player.queue(TaskPriority.STRONG) {
                repeat(
                    qty.coerceAtMost(
                        minOf(
                            player.inventory.getItemCount(leatherId) / product.leatherAmount,
                            player.inventory.getItemCount(threadId) / product.threadAmount,
                        ),
                    ),
                ) {
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
        if (
            !player.inventory.contains(needleId) ||
            player.inventory.getItemCount(leatherId) < product.leatherAmount ||
            player.inventory.getItemCount(threadId) < product.threadAmount
        ) {
            return false
        }
        if (player.getSkills().getCurrentLevel(Skills.CRAFTING) < product.level) {
            player.message("You need a Crafting level of ${product.level} to make that.")
            return false
        }

        player.animate(product.animation)
        try {
            task.wait(product.ticks)
            if (
                !player.inventory.contains(needleId) ||
                player.inventory.getItemCount(leatherId) < product.leatherAmount ||
                player.inventory.getItemCount(threadId) < product.threadAmount
            ) {
                return false
            }

            if (player.inventory.remove(leatherId, product.leatherAmount, assureFullRemoval = true).hasFailed()) {
                return false
            }
            if (player.inventory.remove(threadId, product.threadAmount, assureFullRemoval = true).hasFailed()) {
                player.addOrDrop(entry.leather, product.leatherAmount)
                return false
            }
            player.addXp(Skills.CRAFTING, product.experience)
            player.addOrDrop(product.item, product.amount)
            player.message(product.message ?: "You craft the ${product.item.substringAfter("item.").replace('_', ' ')}.")
            return true
        } finally {
            player.animate(Animation.RESET_CHARACTER)
        }
    }

    private suspend fun handleJewelry(
        task: QueueTask,
        player: Player,
        entry: CraftingJewelryEntry,
    ) {
        val mouldId = getRSCM(entry.mould)
        val barId = getRSCM(entry.bar)
        if (!player.inventory.contains(mouldId) || !player.inventory.contains(barId)) {
            return
        }

        val availableProducts =
            entry.products.filter { product ->
                player.getSkills().getCurrentLevel(Skills.CRAFTING) >= product.level &&
                    (product.gem == null || player.inventory.getItemCount(getRSCM(product.gem)) >= product.gemAmount)
            }
        if (availableProducts.isEmpty()) {
            player.message("You don't have the right materials or Crafting level to make that jewellery.")
            return
        }

        val productIds = availableProducts.map { getRSCM(it.item) }.toIntArray()
        task.produceItemBox(
            player = player,
            *productIds,
            title = "What would you like to craft?",
            maxProducable =
                availableProducts.maxOfOrNull { product ->
                    minOf(
                        player.inventory.getItemCount(barId) / product.barAmount,
                        product.gem?.let { player.inventory.getItemCount(getRSCM(it)) / product.gemAmount } ?: Int.MAX_VALUE,
                    )
                } ?: 0,
        ) { selectedItem, qty ->
            val product = availableProducts.firstOrNull { getRSCM(it.item) == selectedItem } ?: return@produceItemBox
            player.queue(TaskPriority.STRONG) {
                repeat(
                    qty.coerceAtMost(
                        minOf(
                            player.inventory.getItemCount(barId) / product.barAmount,
                            product.gem?.let { player.inventory.getItemCount(getRSCM(it)) / product.gemAmount } ?: Int.MAX_VALUE,
                        ),
                    ),
                ) {
                    if (!craftJewelryPiece(this, player, entry, product)) {
                        return@queue
                    }
                }
            }
        }
    }

    private suspend fun craftJewelryPiece(
        task: QueueTask,
        player: Player,
        entry: CraftingJewelryEntry,
        product: CraftingJewelryProduct,
    ): Boolean {
        val mouldId = getRSCM(entry.mould)
        val barId = getRSCM(entry.bar)
        val gemId = product.gem?.let(::getRSCM)
        if (
            !player.inventory.contains(mouldId) ||
            player.inventory.getItemCount(barId) < product.barAmount ||
            (gemId != null && player.inventory.getItemCount(gemId) < product.gemAmount)
        ) {
            return false
        }
        if (player.getSkills().getCurrentLevel(Skills.CRAFTING) < product.level) {
            player.message("You need a Crafting level of ${product.level} to make that.")
            return false
        }

        player.animate(product.animation)
        try {
            task.wait(product.ticks)
            if (
                !player.inventory.contains(mouldId) ||
                player.inventory.getItemCount(barId) < product.barAmount ||
                (gemId != null && player.inventory.getItemCount(gemId) < product.gemAmount)
            ) {
                return false
            }

            if (player.inventory.remove(barId, product.barAmount, assureFullRemoval = true).hasFailed()) {
                return false
            }
            if (gemId != null && player.inventory.remove(gemId, product.gemAmount, assureFullRemoval = true).hasFailed()) {
                player.addOrDrop(entry.bar, product.barAmount)
                return false
            }
            player.addXp(Skills.CRAFTING, product.experience)
            player.addOrDrop(product.item, 1)
            player.message(product.message ?: "You make ${articleFor(product.item)} ${product.item.substringAfter("item.").replace('_', ' ')}.")
            return true
        } finally {
            player.animate(Animation.RESET_CHARACTER)
        }
    }

    private suspend fun handleStringing(
        task: QueueTask,
        player: Player,
        entry: CraftingStringEntry,
    ) {
        val baseId = getRSCM(entry.base)
        val stringId = getRSCM(entry.string)
        if (player.getSkills().getCurrentLevel(Skills.CRAFTING) < entry.level) {
            player.message("You need a Crafting level of ${entry.level} to make that.")
            return
        }

        try {
            while (
                player.inventory.getItemCount(baseId) >= entry.baseAmount &&
                player.inventory.getItemCount(stringId) >= entry.stringAmount
            ) {
                player.animate(entry.animation)
                task.wait(entry.ticks)
                if (
                    player.inventory.getItemCount(baseId) < entry.baseAmount ||
                    player.inventory.getItemCount(stringId) < entry.stringAmount
                ) {
                    return
                }

                if (player.inventory.remove(baseId, entry.baseAmount, assureFullRemoval = true).hasFailed()) {
                    return
                }
                if (player.inventory.remove(stringId, entry.stringAmount, assureFullRemoval = true).hasFailed()) {
                    player.addOrDrop(entry.base, entry.baseAmount)
                    return
                }
                player.addXp(Skills.CRAFTING, entry.experience)
                player.addOrDrop(entry.output, 1)
                player.message(entry.message ?: "You make ${articleFor(entry.output)} ${entry.output.substringAfter("item.").replace('_', ' ')}.")
            }
        } finally {
            player.animate(Animation.RESET_CHARACTER)
        }
    }

    private suspend fun handleSpinning(
        task: QueueTask,
        player: Player,
        entry: CraftingSpinningEntry,
    ) {
        val inputId = getRSCM(entry.input)
        if (player.getSkills().getCurrentLevel(Skills.CRAFTING) < entry.level) {
            player.message("You need a Crafting level of ${entry.level} to spin this.")
            return
        }

        try {
            while (player.inventory.getItemCount(inputId) >= entry.inputAmount) {
                player.animate(entry.animation)
                task.wait(entry.ticks)
                if (player.inventory.getItemCount(inputId) < entry.inputAmount) {
                    return
                }

                if (player.inventory.remove(inputId, entry.inputAmount, assureFullRemoval = true).hasFailed()) {
                    return
                }
                player.addXp(Skills.CRAFTING, entry.experience)
                player.addOrDrop(entry.output, entry.outputAmount)
                player.message(entry.message ?: "You spin ${entry.output.substringAfter("item.").replace('_', ' ')}.")
            }
        } finally {
            player.animate(Animation.RESET_CHARACTER)
        }
    }

    private suspend fun handlePottery(
        task: QueueTask,
        player: Player,
        entry: CraftingPotteryEntry,
    ) {
        val inputId = getRSCM(entry.input)
        if (!player.inventory.contains(inputId)) {
            return
        }

        val availableProducts = entry.products.filter { player.getSkills().getCurrentLevel(Skills.CRAFTING) >= it.level }
        if (availableProducts.isEmpty()) {
            player.message("You need a higher Crafting level to work this clay.")
            return
        }

        val productIds = availableProducts.map { getRSCM(it.item) }.toIntArray()
        task.produceItemBox(
            player = player,
            *productIds,
            title = "What would you like to make?",
            maxProducable =
                availableProducts.maxOfOrNull { product ->
                    player.inventory.getItemCount(inputId) / product.inputAmount
                } ?: 0,
        ) { selectedItem, qty ->
            val product = availableProducts.firstOrNull { getRSCM(it.item) == selectedItem } ?: return@produceItemBox
            player.queue(TaskPriority.STRONG) {
                repeat(qty.coerceAtMost(player.inventory.getItemCount(inputId) / product.inputAmount)) {
                    if (!craftPotteryPiece(this, player, entry, product)) {
                        return@queue
                    }
                }
            }
        }
    }

    private suspend fun craftPotteryPiece(
        task: QueueTask,
        player: Player,
        entry: CraftingPotteryEntry,
        product: CraftingPotteryProduct,
    ): Boolean {
        val inputId = getRSCM(entry.input)
        if (player.inventory.getItemCount(inputId) < product.inputAmount) {
            return false
        }
        if (player.getSkills().getCurrentLevel(Skills.CRAFTING) < product.level) {
            player.message("You need a Crafting level of ${product.level} to make that.")
            return false
        }

        player.animate(product.animation)
        try {
            task.wait(product.ticks)
            if (player.inventory.getItemCount(inputId) < product.inputAmount) {
                return false
            }
            if (player.inventory.remove(inputId, product.inputAmount, assureFullRemoval = true).hasFailed()) {
                return false
            }
            player.addXp(Skills.CRAFTING, product.experience)
            player.addOrDrop(product.item, product.amount)
            player.message(product.message ?: "You make ${articleFor(product.item)} ${product.item.substringAfter("item.").replace('_', ' ')}.")
            return true
        } finally {
            player.animate(Animation.RESET_CHARACTER)
        }
    }

    private suspend fun handlePotteryFire(
        task: QueueTask,
        player: Player,
        entry: CraftingPotteryFireEntry,
    ) {
        val unfiredId = getRSCM(entry.unfired)
        if (player.getSkills().getCurrentLevel(Skills.CRAFTING) < entry.level) {
            player.message("You need a Crafting level of ${entry.level} to fire this pottery.")
            return
        }

        try {
            while (player.inventory.getItemCount(unfiredId) >= entry.unfiredAmount) {
                player.animate(entry.animation)
                task.wait(entry.ticks)
                if (player.inventory.getItemCount(unfiredId) < entry.unfiredAmount) {
                    return
                }
                if (player.inventory.remove(unfiredId, entry.unfiredAmount, assureFullRemoval = true).hasFailed()) {
                    return
                }
                player.addXp(Skills.CRAFTING, entry.experience)
                player.addOrDrop(entry.fired, entry.firedAmount)
                player.message(entry.message ?: "You fire the pottery.")
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
