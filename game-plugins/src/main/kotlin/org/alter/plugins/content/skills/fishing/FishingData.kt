package org.alter.plugins.content.skills.fishing

import org.alter.plugins.content.skills.core.SkillStack
import org.alter.plugins.content.skills.core.SkillTool

data class FishingCatch(
    val item: String,
    val level: Int = 1,
    val min: Int = 1,
    val max: Int = min,
    val weight: Double = 1.0,
) {
    val safeMin: Int get() = min.coerceAtLeast(1)
    val safeMax: Int get() = max.coerceAtLeast(safeMin)

    init {
        require(item.isNotBlank()) { "Fishing catch item cannot be blank." }
        require(level >= 1) { "Fishing catch level must be >= 1." }
        require(weight > 0.0) { "Fishing catch weight must be > 0." }
    }
}

data class FishingEntry(
    val npcs: List<String>,
    val option: String,
    val level: Int,
    val experience: Double,
    val ticks: Int = 4,
    val tools: List<SkillTool>,
    val bait: SkillStack? = null,
    val outputs: List<FishingCatch>,
) {
    @Transient
    var npcIds: IntArray = intArrayOf()

    init {
        require(npcs.isNotEmpty()) { "Fishing entry must define at least one spot NPC." }
        require(option.isNotBlank()) { "Fishing option cannot be blank." }
        require(level >= 1) { "Fishing level must be >= 1." }
        require(experience >= 0.0) { "Fishing experience cannot be negative." }
        require(ticks >= 1) { "Fishing ticks must be >= 1." }
        require(tools.isNotEmpty()) { "Fishing entry must define at least one tool." }
        require(outputs.isNotEmpty()) { "Fishing entry must define at least one output." }
    }
}
