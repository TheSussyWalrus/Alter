package org.alter.plugins.content.skills.core

data class SkillTool(
    val item: String,
    val level: Int = 1,
) {
    init {
        require(item.isNotBlank()) { "Tool item cannot be blank." }
        require(level >= 1) { "Tool level requirement must be >= 1." }
    }
}

data class SkillStack(
    val item: String,
    val amount: Int = 1,
) {
    init {
        require(item.isNotBlank()) { "Stack item cannot be blank." }
        require(amount >= 1) { "Stack amount must be >= 1." }
    }
}

data class WeightedSkillStack(
    val item: String,
    val min: Int = 1,
    val max: Int = min,
    val weight: Double = 1.0,
) {
    init {
        require(item.isNotBlank()) { "Weighted stack item cannot be blank." }
        require(min >= 1) { "Weighted stack minimum amount must be >= 1." }
        require(max >= min) { "Weighted stack maximum cannot be less than minimum." }
        require(weight > 0.0) { "Weighted stack weight must be > 0." }
    }
}

data class DepletionConfig(
    val replacement: String,
    val respawnTicks: Int,
) {
    init {
        require(replacement.isNotBlank()) { "Depletion replacement object cannot be blank." }
        require(respawnTicks >= 1) { "Respawn ticks must be >= 1." }
    }
}

data class ToolProductRecipe(
    val level: Int,
    val experience: Double,
    val ticks: Int = 2,
    val animation: Int,
    val tool: String,
    val input: SkillStack,
    val outputs: List<SkillStack>,
    val message: String? = null,
) {
    init {
        require(level >= 1) { "Recipe level requirement must be >= 1." }
        require(experience >= 0.0) { "Recipe experience cannot be negative." }
        require(ticks >= 1) { "Recipe ticks must be >= 1." }
        require(tool.isNotBlank()) { "Recipe tool cannot be blank." }
        require(outputs.isNotEmpty()) { "Recipe must define at least one output." }
    }
}

data class CombinationRecipe(
    val level: Int,
    val experience: Double,
    val ticks: Int = 2,
    val animation: Int,
    val inputs: List<SkillStack>,
    val outputs: List<SkillStack>,
    val message: String? = null,
) {
    init {
        require(level >= 1) { "Recipe level requirement must be >= 1." }
        require(experience >= 0.0) { "Recipe experience cannot be negative." }
        require(ticks >= 1) { "Recipe ticks must be >= 1." }
        require(inputs.isNotEmpty()) { "Recipe must define at least one input." }
        require(outputs.isNotEmpty()) { "Recipe must define at least one output." }
    }
}
