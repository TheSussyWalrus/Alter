package org.alter.plugins.content.skills.crafting

import org.alter.api.cfg.Animation

data class CraftingGemEntry(
    val uncut: String,
    val cut: String,
    val level: Int,
    val experience: Double,
    val ticks: Int = 2,
    val animation: Int = Animation.GEM_CUT_SAPPHIRE,
) {
    init {
        require(uncut.isNotBlank()) { "Crafting gem uncut item cannot be blank." }
        require(cut.isNotBlank()) { "Crafting gem cut item cannot be blank." }
        require(level >= 1) { "Crafting gem level must be >= 1." }
        require(experience >= 0.0) { "Crafting gem experience cannot be negative." }
        require(ticks >= 1) { "Crafting gem ticks must be >= 1." }
    }
}

data class CraftingLeatherEntry(
    val needle: String = "item.needle",
    val leather: String = "item.leather",
    val thread: String = "item.thread",
    val products: List<CraftingLeatherProduct>,
) {
    init {
        require(needle.isNotBlank()) { "Crafting needle item cannot be blank." }
        require(leather.isNotBlank()) { "Crafting leather item cannot be blank." }
        require(thread.isNotBlank()) { "Crafting thread item cannot be blank." }
        require(products.isNotEmpty()) { "Crafting leather entry must define at least one product." }
    }
}

data class CraftingLeatherProduct(
    val item: String,
    val level: Int,
    val experience: Double,
    val amount: Int = 1,
    val leatherAmount: Int = 1,
    val threadAmount: Int = 1,
    val ticks: Int = 2,
    val animation: Int = Animation.LEATHER_CRAFTING,
    val message: String? = null,
) {
    init {
        require(item.isNotBlank()) { "Crafting leather product item cannot be blank." }
        require(level >= 1) { "Crafting leather product level must be >= 1." }
        require(experience >= 0.0) { "Crafting leather product experience cannot be negative." }
        require(amount >= 1) { "Crafting leather product amount must be >= 1." }
        require(leatherAmount >= 1) { "Crafting leather amount must be >= 1." }
        require(threadAmount >= 1) { "Crafting thread amount must be >= 1." }
        require(ticks >= 1) { "Crafting leather product ticks must be >= 1." }
    }
}
