package org.alter.plugins.content.skills.core

import org.alter.api.ext.*
import org.alter.game.model.entity.Player
import org.alter.game.model.queue.QueueTask
import org.alter.game.model.skill.SkillSet
import org.alter.game.model.item.Item

data class ProcessingIngredient(
    val itemId: Int,
    val amount: Int = 1,
)

data class ProcessingOutput(
    val itemId: Int,
    val amount: Int = 1,
)

data class ProcessingRecipe(
    val name: String,
    val skill: Int? = null,
    val level: Int = 1,
    val experience: Double = 0.0,
    val inputs: List<ProcessingIngredient> = emptyList(),
    val outputs: List<ProcessingOutput> = emptyList(),
    val cycles: Int = 1,
    val animationId: Int? = null,
    val requirements: List<SkillingRequirement> = emptyList(),
) {
    fun canProcess(player: Player): Boolean = validation(player).passed

    fun validation(player: Player): SkillingRequirementResult {
        skill?.let {
            if (player.getSkills().getCurrentLevel(it) < level) {
                return SkillingRequirementResult.fail("You need a level of $level to do that.")
            }
        }

        val missingInput = inputs.firstOrNull { player.inventory.getItemCount(it.itemId) < it.amount }
        if (missingInput != null) {
            return SkillingRequirementResult.fail("You don't have the required items to do that.")
        }

        if (player.inventory.freeSlotCount < requiredSpace(player)) {
            return SkillingRequirementResult.fail("You need more free inventory space to do that.")
        }

        val extraRequirements = requirements.firstNotNullOfOrNull { requirement ->
            val result = requirement.check(SkillingActionContext(player = player))
            if (result.passed) null else result
        }
        if (extraRequirements != null) {
            return extraRequirements
        }

        return SkillingRequirementResult.pass()
    }

    fun requiredSpace(player: Player): Int {
        val remainingCounts = remainingInventoryCounts(player)
        return outputs.sumOf { output ->
            val def = dev.openrune.cache.CacheManager.getItem(output.itemId)
            if (def.stackable && remainingCounts.getOrDefault(output.itemId, 0) > 0) {
                0
            } else if (def.stackable) {
                1
            } else {
                output.amount
            }
        }
    }

    private fun remainingInventoryCounts(player: Player): Map<Int, Int> {
        val counts = mutableMapOf<Int, Int>()
        player.inventory.filterNotNull().forEach { item: Item ->
            counts[item.id] = counts.getOrDefault(item.id, 0) + item.amount
        }
        inputs.forEach { input ->
            val current = counts.getOrDefault(input.itemId, 0)
            counts[input.itemId] = (current - input.amount).coerceAtLeast(0)
        }
        return counts
    }

    fun consumeInputs(player: Player): Boolean {
        if (inputs.any { player.inventory.getItemCount(it.itemId) < it.amount }) {
            return false
        }
        inputs.forEach { input ->
            player.inventory.remove(input.itemId, input.amount, assureFullRemoval = true)
        }
        return true
    }

    fun produceOutputs(player: Player): Boolean {
        if (outputs.isEmpty()) {
            return true
        }
        outputs.forEach { output ->
            player.inventory.add(output.itemId, output.amount)
        }
        return true
    }
}

suspend fun QueueTask.runProcessing(
    player: Player,
    recipe: ProcessingRecipe,
    source: Any? = null,
    target: Any? = null,
    onComplete: suspend ProcessingActionContext.() -> Unit = {},
): Boolean {
    val context = ProcessingActionContext(player = player, task = this, recipe = recipe, source = source, target = target)
    val validation = recipe.validation(player)
    if (!validation.passed) {
        validation.message?.let { player.message(it) }
        return false
    }

    player.lock()
    try {
        recipe.animationId?.let { player.animate(it) }
        if (recipe.cycles > 0) {
            wait(recipe.cycles)
        }
        if (!recipe.consumeInputs(player)) {
            player.message("You don't have the required items to do that.")
            return false
        }
        recipe.produceOutputs(player)
        recipe.skill?.let { skill ->
            if (recipe.experience > 0.0) {
                player.addXp(skill, recipe.experience)
            }
        }
        context.onComplete()
        return true
    } finally {
        player.unlock()
    }
}

data class ProcessingActionContext(
    val player: Player,
    val task: QueueTask,
    val recipe: ProcessingRecipe,
    val source: Any? = null,
    val target: Any? = null,
) {
    val world get() = player.world
    val skillName: String? get() = recipe.skill?.let { SkillSet.getSkillName(it) }

    inline fun <reified T> sourceAs(): T? = source as? T

    inline fun <reified T> targetAs(): T? = target as? T
}
