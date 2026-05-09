package org.alter.plugins.content.tools.qabot

import com.google.gson.JsonObject
import org.alter.game.model.Tile

data class QaScenario(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val seed: Long = 0L,
    val defaultTimeoutTicks: Int = 50,
    val steps: List<QaScenarioStep> = emptyList(),
)

data class QaScenarioStep(
    val id: String = "",
    val skill: String = "",
    val category: String = "",
    val type: String = "",
    val setup: JsonObject = JsonObject(),
    val action: JsonObject = JsonObject(),
    val expect: JsonObject = JsonObject(),
    val timeoutTicks: Int? = null,
    val failureClass: String = "server_error",
)

data class QaSession(
    val id: String,
    val scenarioId: String,
    val scenarioName: String,
    val seed: Long,
    val requestedBy: String,
    val startedAt: String,
    var finishedAt: String? = null,
    var status: String = QaStatus.RUNNING.value,
    var botName: String = QaBotService.DEFAULT_BOT_NAME,
    var botTile: QaTile? = null,
    var currentStepId: String? = null,
    val steps: MutableList<QaStepResult> = mutableListOf(),
    val observations: MutableList<String> = mutableListOf(),
    val warnings: MutableList<String> = mutableListOf(),
    val assertions: MutableList<QaAssertion> = mutableListOf(),
)

data class QaStepResult(
    val id: String,
    val skill: String,
    val category: String,
    val type: String,
    val startedAtCycle: Int,
    var finishedAtCycle: Int? = null,
    var status: String = QaStatus.RUNNING.value,
    var failureClass: String? = null,
    val observations: MutableList<String> = mutableListOf(),
    val assertions: MutableList<QaAssertion> = mutableListOf(),
    val messages: MutableList<String> = mutableListOf(),
)

data class QaAssertion(
    val name: String,
    val passed: Boolean,
    val expected: String? = null,
    val actual: String? = null,
)

data class QaTile(
    val x: Int,
    val z: Int,
    val height: Int,
    val regionId: Int,
) {
    companion object {
        fun from(tile: Tile): QaTile = QaTile(tile.x, tile.z, tile.height, tile.regionId)
    }
}

data class QaSnapshot(
    val inventory: Map<Int, Int>,
    val xp: Map<String, Double>,
)

enum class QaStatus(val value: String) {
    RUNNING("running"),
    PASSED("passed"),
    FAILED("failed"),
    STOPPED("stopped"),
    SKIPPED("skipped"),
}
