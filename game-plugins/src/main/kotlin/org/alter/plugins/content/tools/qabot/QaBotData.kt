package org.alter.plugins.content.tools.qabot

import com.google.gson.JsonObject
import org.alter.game.model.Tile

data class QaStartOptions(
    val scenarioId: String? = null,
    val suiteId: String? = null,
    val seed: Long? = null,
    val repeatCount: Int = 1,
    val continueOnFailure: Boolean = true,
    val fixtureMode: String = "ephemeral",
)

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

data class QaSuite(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val seed: Long = 0L,
    val defaultTimeoutTicks: Int = 80,
    val retryPolicy: QaRetryPolicy = QaRetryPolicy(),
    val continueOnFailure: Boolean = true,
    val fixture: QaFixture = QaFixture(),
    val journeys: List<QaJourney> = emptyList(),
)

data class QaJourney(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val category: String = "",
    val retryPolicy: QaRetryPolicy = QaRetryPolicy(),
    val continueOnFailure: Boolean? = null,
    val fixture: QaFixture = QaFixture(),
    val goals: List<QaGoal> = emptyList(),
)

data class QaGoal(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val stepRef: String? = null,
    val skill: String = "",
    val category: String = "",
    val type: String = "",
    val setup: JsonObject = JsonObject(),
    val action: JsonObject = JsonObject(),
    val expect: JsonObject = JsonObject(),
    val timeoutTicks: Int? = null,
    val failureClass: String = "server_error",
    val retryPolicy: QaRetryPolicy = QaRetryPolicy(),
    val continueOnFailure: Boolean? = null,
)

data class QaRetryPolicy(
    val maxAttempts: Int = 3,
    val resetFixturesBetweenAttempts: Boolean = true,
)

data class QaFixture(
    val origin: QaTile? = null,
    val inventory: List<QaItemFixture> = emptyList(),
    val skills: Map<String, Int> = emptyMap(),
    val tempNpcs: List<QaNpcFixture> = emptyList(),
    val tempObjects: List<QaObjectFixture> = emptyList(),
)

data class QaItemFixture(
    val item: String? = null,
    val itemId: Int? = null,
    val amount: Int = 1,
)

data class QaNpcFixture(
    val npc: String? = null,
    val npcId: Int? = null,
    val x: Int,
    val z: Int,
    val height: Int = 0,
    val walkRadius: Int = 0,
    val active: Boolean = true,
)

data class QaObjectFixture(
    val obj: String? = null,
    val objectId: Int? = null,
    val x: Int,
    val z: Int,
    val height: Int = 0,
    val type: Int = 10,
    val rot: Int = 0,
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
    var runMode: String = "scenario",
    var suiteId: String? = null,
    var suiteName: String? = null,
    var repeatCount: Int = 1,
    var fixtureMode: String = "ephemeral",
    var currentStepId: String? = null,
    var currentJourneyId: String? = null,
    val steps: MutableList<QaStepResult> = mutableListOf(),
    val journeys: MutableList<QaJourneyResult> = mutableListOf(),
    val events: MutableList<QaEvent> = mutableListOf(),
    val failures: MutableList<QaFailure> = mutableListOf(),
    val observations: MutableList<String> = mutableListOf(),
    val warnings: MutableList<String> = mutableListOf(),
    val assertions: MutableList<QaAssertion> = mutableListOf(),
    var cleanup: QaCleanupResult = QaCleanupResult(),
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

data class QaJourneyResult(
    val id: String,
    val name: String,
    val category: String,
    val startedAtCycle: Int,
    var finishedAtCycle: Int? = null,
    var status: String = QaStatus.RUNNING.value,
    var failureClass: String? = null,
    val goals: MutableList<QaGoalResult> = mutableListOf(),
    val observations: MutableList<String> = mutableListOf(),
    var cleanup: QaCleanupResult = QaCleanupResult(),
)

data class QaGoalResult(
    val id: String,
    val name: String,
    val attempt: Int,
    val maxAttempts: Int,
    val step: QaStepResult,
) {
    val status: String
        get() = step.status

    val failureClass: String?
        get() = step.failureClass
}

data class QaAssertion(
    val name: String,
    val passed: Boolean,
    val expected: String? = null,
    val actual: String? = null,
)

data class QaObservation(
    val cycle: Int,
    val journeyId: String? = null,
    val goalId: String? = null,
    val message: String,
)

data class QaFailure(
    val journeyId: String? = null,
    val goalId: String? = null,
    val failureClass: String,
    val message: String,
    val attempt: Int = 1,
)

data class QaEvent(
    val cycle: Int,
    val type: String,
    val message: String,
    val journeyId: String? = null,
    val goalId: String? = null,
    val status: String? = null,
)

data class QaCleanupResult(
    var removedNpcs: Int = 0,
    var removedObjects: Int = 0,
    var clearedInventory: Boolean = false,
    var interruptedQueues: Boolean = false,
    var closedInterfaces: Boolean = false,
    val warnings: MutableList<String> = mutableListOf(),
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
    val hitpoints: Int,
    val tile: QaTile,
    val currentShop: String? = null,
)

enum class QaStatus(val value: String) {
    RUNNING("running"),
    PASSED("passed"),
    FAILED("failed"),
    STOPPED("stopped"),
    SKIPPED("skipped"),
}
