package org.alter.plugins.content.skills.fishing

import org.alter.plugins.content.skills.core.SkillStack
import org.alter.plugins.content.skills.core.SkillTool
import org.alter.plugins.content.skills.core.WeightedSkillStack

data class FishingEntry(
    val npcs: List<String>,
    val option: String,
    val level: Int,
    val experience: Double,
    val ticks: Int = 4,
    val tools: List<SkillTool>,
    val bait: SkillStack? = null,
    val outputs: List<WeightedSkillStack>,
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
