package org.alter.plugins.content.skills.woodcutting

import org.alter.plugins.content.skills.core.SkillTool
import org.alter.plugins.content.skills.core.WeightedSkillStack

data class WoodcuttingEntry(
    val objects: List<String>,
    val depletedObject: String,
    val respawnTicks: Int,
    val level: Int,
    val experience: Double,
    val ticks: Int = 4,
    val depletionChance: Double = 0.125,
    val tools: List<SkillTool>,
    val outputs: List<WeightedSkillStack>,
) {
    @Transient
    var objectIds: IntArray = intArrayOf()

    @Transient
    var depletedObjectId: Int = -1

    init {
        require(objects.isNotEmpty()) { "Woodcutting entry must define at least one tree object." }
        require(depletedObject.isNotBlank()) { "Woodcutting depleted object cannot be blank." }
        require(respawnTicks >= 1) { "Woodcutting respawn ticks must be >= 1." }
        require(level >= 1) { "Woodcutting level must be >= 1." }
        require(experience >= 0.0) { "Woodcutting experience cannot be negative." }
        require(ticks >= 1) { "Woodcutting ticks must be >= 1." }
        require(depletionChance in 0.0..1.0) { "Woodcutting depletion chance must be between 0.0 and 1.0." }
        require(tools.isNotEmpty()) { "Woodcutting entry must define at least one tool." }
        require(outputs.isNotEmpty()) { "Woodcutting entry must define at least one output." }
    }
}
