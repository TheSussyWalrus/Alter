package org.alter.plugins.content.skills.core

import org.alter.game.model.attr.AttributeKey

val SLAYER_STATE_ATTR = AttributeKey<String>("slayer_state")
val FARMING_STATE_ATTR = AttributeKey<String>("farming_state")

data class SlayerTaskState(
    val master: String = "",
    val taskName: String = "",
    val npcKey: String = "",
    val amountAssigned: Int = 0,
    val amountRemaining: Int = 0,
    val streak: Int = 0,
    val points: Int = 0,
)

val SLAYER_STATE = jsonState(SLAYER_STATE_ATTR, SlayerTaskState())

data class FarmingState(
    val patches: Map<String, FarmingPatchState> = emptyMap(),
)

data class FarmingPatchState(
    val patchKey: String,
    val type: String,
    val seed: String = "",
    val stage: Int = 0,
    val plantedAt: Long = 0L,
    val compost: String = "",
    val weedsCleared: Boolean = false,
)

val FARMING_STATE = jsonState(FARMING_STATE_ATTR, FarmingState())
