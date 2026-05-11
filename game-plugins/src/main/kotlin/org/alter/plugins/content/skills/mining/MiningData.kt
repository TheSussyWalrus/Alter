package org.alter.plugins.content.skills.mining

import org.alter.plugins.content.skills.core.SkillTool
import org.alter.plugins.content.skills.core.WeightedSkillStack

data class MiningEntry(
    val objects: List<String>,
    val depletedObject: String,
    val respawnTicks: Int,
    val level: Int,
    val experience: Double,
    val ticks: Int = 4,
    val depletionChance: Double = 0.2,
    val tools: List<SkillTool>,
    val outputs: List<WeightedSkillStack>,
) {
    @Transient
    var objectIds: IntArray = intArrayOf()

    @Transient
    var depletedObjectId: Int = -1

    init {
        require(objects.isNotEmpty()) { "Mining entry must define at least one rock object." }
        require(depletedObject.isNotBlank()) { "Mining depleted object cannot be blank." }
        require(respawnTicks >= 1) { "Mining respawn ticks must be >= 1." }
        require(level >= 1) { "Mining level must be >= 1." }
        require(experience >= 0.0) { "Mining experience cannot be negative." }
        require(ticks >= 1) { "Mining ticks must be >= 1." }
        require(depletionChance in 0.0..1.0) { "Mining depletion chance must be between 0.0 and 1.0." }
        require(tools.isNotEmpty()) { "Mining entry must define at least one tool." }
        require(outputs.isNotEmpty()) { "Mining entry must define at least one output." }
    }
}
