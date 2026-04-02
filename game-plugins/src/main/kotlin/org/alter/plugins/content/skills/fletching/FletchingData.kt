package org.alter.plugins.content.skills.fletching

import org.alter.api.cfg.Animation

data class FletchingLogEntry(
    val log: String,
    val knife: String = "item.knife",
    val products: List<FletchingLogProduct>,
) {
    init {
        require(log.isNotBlank()) { "Fletching log cannot be blank." }
        require(knife.isNotBlank()) { "Fletching knife cannot be blank." }
        require(products.isNotEmpty()) { "Fletching log entry must define at least one product." }
    }
}

data class FletchingLogProduct(
    val item: String,
    val level: Int,
    val experience: Double,
    val amount: Int = 1,
    val ticks: Int = 2,
    val animation: Int = Animation.FLETCHING_LOG_CUT,
    val message: String? = null,
) {
    init {
        require(item.isNotBlank()) { "Fletching product item cannot be blank." }
        require(level >= 1) { "Fletching product level must be >= 1." }
        require(experience >= 0.0) { "Fletching product experience cannot be negative." }
        require(amount >= 1) { "Fletching product amount must be >= 1." }
        require(ticks >= 1) { "Fletching product ticks must be >= 1." }
    }
}

data class FletchingStringEntry(
    val unstrung: String,
    val strung: String,
    val level: Int,
    val experience: Double,
    val ticks: Int = 2,
    val animation: Int = Animation.FLETCHING_SHORTBOW_STRING,
    val message: String? = null,
) {
    init {
        require(unstrung.isNotBlank()) { "Fletching unstrung item cannot be blank." }
        require(strung.isNotBlank()) { "Fletching strung item cannot be blank." }
        require(level >= 1) { "Fletching stringing level must be >= 1." }
        require(experience >= 0.0) { "Fletching stringing experience cannot be negative." }
        require(ticks >= 1) { "Fletching stringing ticks must be >= 1." }
    }
}

data class FletchingAssemblyEntry(
    val first: String,
    val second: String,
    val output: String,
    val level: Int,
    val experience: Double,
    val outputAmount: Int = 1,
    val firstAmount: Int = 1,
    val secondAmount: Int = 1,
    val ticks: Int = 2,
    val animation: Int = Animation.FLETCHING_LOG_CUT,
    val message: String? = null,
) {
    init {
        require(first.isNotBlank()) { "Fletching first ingredient cannot be blank." }
        require(second.isNotBlank()) { "Fletching second ingredient cannot be blank." }
        require(output.isNotBlank()) { "Fletching output cannot be blank." }
        require(level >= 1) { "Fletching assembly level must be >= 1." }
        require(experience >= 0.0) { "Fletching assembly experience cannot be negative." }
        require(outputAmount >= 1) { "Fletching assembly output amount must be >= 1." }
        require(firstAmount >= 1) { "Fletching first ingredient amount must be >= 1." }
        require(secondAmount >= 1) { "Fletching second ingredient amount must be >= 1." }
        require(ticks >= 1) { "Fletching assembly ticks must be >= 1." }
    }
}
