package org.alter.plugins.content.skills.herblore

import org.alter.api.cfg.Animation

data class HerbCleaningEntry(
    val grimy: String,
    val clean: String,
    val level: Int,
    val experience: Double,
    val ticks: Int = 1,
    val animation: Int = Animation.HERBLORE_CLEAN_HERB,
) {
    init {
        require(grimy.isNotBlank()) { "Herblore grimy herb cannot be blank." }
        require(clean.isNotBlank()) { "Herblore clean herb cannot be blank." }
        require(level >= 1) { "Herblore cleaning level must be >= 1." }
        require(experience >= 0.0) { "Herblore cleaning experience cannot be negative." }
        require(ticks >= 1) { "Herblore cleaning ticks must be >= 1." }
    }
}

data class UnfinishedPotionEntry(
    val herb: String,
    val vial: String = "item.vial_of_water",
    val output: String,
    val level: Int,
    val experience: Double,
    val ticks: Int = 2,
    val animation: Int = Animation.HERBLORE_POTION_MAKING,
) {
    init {
        require(herb.isNotBlank()) { "Herblore herb cannot be blank." }
        require(vial.isNotBlank()) { "Herblore vial cannot be blank." }
        require(output.isNotBlank()) { "Herblore unfinished potion output cannot be blank." }
        require(level >= 1) { "Herblore unfinished potion level must be >= 1." }
        require(experience >= 0.0) { "Herblore unfinished potion experience cannot be negative." }
        require(ticks >= 1) { "Herblore unfinished potion ticks must be >= 1." }
    }
}

data class FinishedPotionEntry(
    val unfinished: String,
    val secondary: String,
    val output: String,
    val level: Int,
    val experience: Double,
    val ticks: Int = 2,
    val animation: Int = Animation.HERBLORE_POTION_MAKING,
) {
    init {
        require(unfinished.isNotBlank()) { "Herblore unfinished potion cannot be blank." }
        require(secondary.isNotBlank()) { "Herblore secondary ingredient cannot be blank." }
        require(output.isNotBlank()) { "Herblore potion output cannot be blank." }
        require(level >= 1) { "Herblore finished potion level must be >= 1." }
        require(experience >= 0.0) { "Herblore finished potion experience cannot be negative." }
        require(ticks >= 1) { "Herblore finished potion ticks must be >= 1." }
    }
}
