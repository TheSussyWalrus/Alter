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

data class CraftingJewelryEntry(
    val mould: String,
    val bar: String,
    val products: List<CraftingJewelryProduct>,
) {
    init {
        require(mould.isNotBlank()) { "Crafting jewellery mould cannot be blank." }
        require(bar.isNotBlank()) { "Crafting jewellery bar cannot be blank." }
        require(products.isNotEmpty()) { "Crafting jewellery entry must define at least one product." }
    }
}

data class CraftingJewelryProduct(
    val item: String,
    val level: Int,
    val experience: Double,
    val gem: String? = null,
    val barAmount: Int = 1,
    val gemAmount: Int = if (gem != null) 1 else 0,
    val ticks: Int = 2,
    val animation: Int = Animation.SMITHING_SMELT,
    val message: String? = null,
) {
    init {
        require(item.isNotBlank()) { "Crafting jewellery product item cannot be blank." }
        require(level >= 1) { "Crafting jewellery product level must be >= 1." }
        require(experience >= 0.0) { "Crafting jewellery experience cannot be negative." }
        require(barAmount >= 1) { "Crafting jewellery bar amount must be >= 1." }
        require(gemAmount >= 0) { "Crafting jewellery gem amount cannot be negative." }
        require(ticks >= 1) { "Crafting jewellery ticks must be >= 1." }
    }
}

data class CraftingStringEntry(
    val base: String,
    val string: String,
    val output: String,
    val level: Int,
    val experience: Double,
    val baseAmount: Int = 1,
    val stringAmount: Int = 1,
    val ticks: Int = 2,
    val animation: Int = Animation.LEATHER_CRAFTING,
    val message: String? = null,
) {
    init {
        require(base.isNotBlank()) { "Crafting string base item cannot be blank." }
        require(string.isNotBlank()) { "Crafting string item cannot be blank." }
        require(output.isNotBlank()) { "Crafting string output cannot be blank." }
        require(level >= 1) { "Crafting string level must be >= 1." }
        require(experience >= 0.0) { "Crafting string experience cannot be negative." }
        require(baseAmount >= 1) { "Crafting string base amount must be >= 1." }
        require(stringAmount >= 1) { "Crafting string amount must be >= 1." }
        require(ticks >= 1) { "Crafting string ticks must be >= 1." }
    }
}

data class CraftingSpinningEntry(
    val objectNames: List<String>,
    val input: String,
    val output: String,
    val level: Int,
    val experience: Double,
    val inputAmount: Int = 1,
    val outputAmount: Int = 1,
    val ticks: Int = 2,
    val animation: Int = Animation.PLAYER_SPINNING_WHEEL_ACTION,
    val message: String? = null,
) {
    init {
        require(objectNames.isNotEmpty()) { "Crafting spinning entry must define at least one object." }
        require(input.isNotBlank()) { "Crafting spinning input cannot be blank." }
        require(output.isNotBlank()) { "Crafting spinning output cannot be blank." }
        require(level >= 1) { "Crafting spinning level must be >= 1." }
        require(experience >= 0.0) { "Crafting spinning experience cannot be negative." }
        require(inputAmount >= 1) { "Crafting spinning input amount must be >= 1." }
        require(outputAmount >= 1) { "Crafting spinning output amount must be >= 1." }
        require(ticks >= 1) { "Crafting spinning ticks must be >= 1." }
    }
}

data class CraftingPotteryEntry(
    val objectNames: List<String>,
    val input: String,
    val products: List<CraftingPotteryProduct>,
) {
    init {
        require(objectNames.isNotEmpty()) { "Crafting pottery entry must define at least one object." }
        require(input.isNotBlank()) { "Crafting pottery input cannot be blank." }
        require(products.isNotEmpty()) { "Crafting pottery entry must define at least one product." }
    }
}

data class CraftingPotteryProduct(
    val item: String,
    val level: Int,
    val experience: Double,
    val amount: Int = 1,
    val inputAmount: Int = 1,
    val ticks: Int = 2,
    val animation: Int = Animation.PLAYER_POTTERY,
    val message: String? = null,
) {
    init {
        require(item.isNotBlank()) { "Crafting pottery product item cannot be blank." }
        require(level >= 1) { "Crafting pottery level must be >= 1." }
        require(experience >= 0.0) { "Crafting pottery experience cannot be negative." }
        require(amount >= 1) { "Crafting pottery amount must be >= 1." }
        require(inputAmount >= 1) { "Crafting pottery input amount must be >= 1." }
        require(ticks >= 1) { "Crafting pottery ticks must be >= 1." }
    }
}

data class CraftingPotteryFireEntry(
    val objectNames: List<String>,
    val unfired: String,
    val fired: String,
    val level: Int,
    val experience: Double,
    val unfiredAmount: Int = 1,
    val firedAmount: Int = 1,
    val ticks: Int = 2,
    val animation: Int = Animation.PLAYER_POTTERY_OVEN,
    val message: String? = null,
) {
    init {
        require(objectNames.isNotEmpty()) { "Crafting pottery fire entry must define at least one object." }
        require(unfired.isNotBlank()) { "Crafting pottery unfired item cannot be blank." }
        require(fired.isNotBlank()) { "Crafting pottery fired item cannot be blank." }
        require(level >= 1) { "Crafting pottery fire level must be >= 1." }
        require(experience >= 0.0) { "Crafting pottery fire experience cannot be negative." }
        require(unfiredAmount >= 1) { "Crafting pottery unfired amount must be >= 1." }
        require(firedAmount >= 1) { "Crafting pottery fired amount must be >= 1." }
        require(ticks >= 1) { "Crafting pottery fire ticks must be >= 1." }
    }
}
