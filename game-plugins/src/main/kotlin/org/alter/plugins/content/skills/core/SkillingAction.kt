package org.alter.plugins.content.skills.core

import org.alter.api.ext.*
import org.alter.game.model.Tile
import org.alter.game.model.container.ItemContainer
import org.alter.game.model.entity.Pawn
import org.alter.game.model.entity.Player
import org.alter.game.model.queue.QueueTask

data class SkillingActionContext(
    val player: Player,
    val task: QueueTask? = null,
    val source: Any? = null,
    val target: Any? = null,
    val originTile: Tile = player.tile,
) {
    val world get() = player.world

    inline fun <reified T> sourceAs(): T? = source as? T

    inline fun <reified T> targetAs(): T? = target as? T
}

data class SkillingRequirementResult(
    val passed: Boolean,
    val message: String? = null,
) {
    companion object {
        fun pass() = SkillingRequirementResult(true)

        fun fail(message: String? = null) = SkillingRequirementResult(false, message)
    }
}

fun interface SkillingRequirement {
    fun check(context: SkillingActionContext): SkillingRequirementResult
}

object SkillingRequirements {
    fun level(
        skill: Int,
        level: Int,
        message: (SkillingActionContext) -> String = { "You need a level of $level to do that." },
    ): SkillingRequirement =
        SkillingRequirement { context ->
            if (context.player.getSkills().getCurrentLevel(skill) >= level) {
                SkillingRequirementResult.pass()
            } else {
                SkillingRequirementResult.fail(message(context))
            }
        }

    fun item(
        itemId: Int,
        amount: Int = 1,
        container: (Player) -> ItemContainer = { it.inventory },
        message: (SkillingActionContext) -> String = { "You need $amount of item $itemId to do that." },
    ): SkillingRequirement =
        SkillingRequirement { context ->
            if (container(context.player).getItemCount(itemId) >= amount) {
                SkillingRequirementResult.pass()
            } else {
                SkillingRequirementResult.fail(message(context))
            }
        }

    fun inventorySpace(
        amount: Int = 1,
        message: (SkillingActionContext) -> String = { "You need $amount free inventory slot(s)." },
    ): SkillingRequirement =
        SkillingRequirement { context ->
            if (context.player.inventory.freeSlotCount >= amount) {
                SkillingRequirementResult.pass()
            } else {
                SkillingRequirementResult.fail(message(context))
            }
        }

    fun custom(check: (SkillingActionContext) -> SkillingRequirementResult): SkillingRequirement = SkillingRequirement(check)

    fun allOf(vararg requirements: SkillingRequirement): SkillingRequirement =
        SkillingRequirement { context ->
            for (requirement in requirements) {
                val result = requirement.check(context)
                if (!result.passed) {
                    return@SkillingRequirement result
                }
            }
            SkillingRequirementResult.pass()
        }

    fun anyOf(vararg requirements: SkillingRequirement): SkillingRequirement =
        SkillingRequirement { context ->
            for (requirement in requirements) {
                val result = requirement.check(context)
                if (result.passed) {
                    return@SkillingRequirement result
                }
            }
            SkillingRequirementResult.fail()
        }
}

infix fun SkillingRequirement.and(other: SkillingRequirement): SkillingRequirement =
    SkillingRequirement { context ->
        val first = check(context)
        if (!first.passed) {
            first
        } else {
            other.check(context)
        }
    }

infix fun SkillingRequirement.or(other: SkillingRequirement): SkillingRequirement =
    SkillingRequirement { context ->
        val first = check(context)
        if (first.passed) {
            first
        } else {
            other.check(context)
        }
    }

data class SkillingActionDefinition(
    val name: String,
    val requirements: List<SkillingRequirement> = emptyList(),
    val lockPlayer: Boolean = true,
    val animationId: Int? = null,
    val startDelay: Int = 0,
    val cycleDelay: Int = 1,
    val faceSource: Boolean = false,
    val faceTarget: Boolean = false,
    val stopWhen: List<(SkillingActionContext) -> Boolean> = emptyList(),
)

object SkillingActions {
    suspend fun QueueTask.runAction(
        player: Player,
        definition: SkillingActionDefinition,
        source: Any? = null,
        target: Any? = null,
        onStart: suspend SkillingActionContext.() -> Unit = {},
        onCycle: suspend SkillingActionContext.() -> Boolean = { false },
        onFinish: suspend SkillingActionContext.() -> Unit = {},
    ): Boolean {
        val context = SkillingActionContext(player = player, task = this, source = source, target = target)
        val failure = definition.requirements.firstNotNullOfOrNull { requirement ->
            val result = requirement.check(context)
            if (result.passed) null else result.message
        }
        if (failure != null) {
            player.message(failure)
            return false
        }

        if (definition.lockPlayer) {
            player.lock()
        }
        try {
            if (definition.faceSource) {
                context.face(context.source)
            }
            if (definition.faceTarget) {
                context.face(context.target)
            }
            definition.animationId?.let { player.animate(it) }
            if (definition.startDelay > 0) {
                wait(definition.startDelay)
            }

            context.onStart()

            while (true) {
                if (definition.stopWhen.any { it(context) }) {
                    break
                }

                val continueLoop = context.onCycle()
                if (!continueLoop) {
                    break
                }

                if (definition.cycleDelay > 0) {
                    wait(definition.cycleDelay)
                }
            }

            context.onFinish()
            return true
        } finally {
            if (definition.lockPlayer) {
                player.unlock()
            }
        }
    }

    private fun SkillingActionContext.face(entity: Any?) {
        when (entity) {
            is Pawn -> player.facePawn(entity)
            is Tile -> player.faceTile(entity)
        }
    }
}

fun Player.hasLevel(skill: Int, level: Int): Boolean = getSkills().getCurrentLevel(skill) >= level

fun Player.hasInventoryItem(
    itemId: Int,
    amount: Int = 1,
    container: ItemContainer = inventory,
): Boolean = container.getItemCount(itemId) >= amount

fun Player.hasInventorySpace(amount: Int = 1): Boolean = inventory.freeSlotCount >= amount
