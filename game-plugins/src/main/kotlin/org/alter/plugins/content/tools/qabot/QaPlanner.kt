package org.alter.plugins.content.tools.qabot

import org.alter.game.model.World
import org.alter.game.model.queue.QueueTask

class QaPlanner(
    private val driver: QaActionDriver,
    private val fixtureService: QaFixtureService,
) {
    suspend fun runSuite(
        task: QueueTask,
        world: World,
        bot: QaPlayer,
        suite: QaSuite,
        scenarios: Map<String, QaScenario>,
        session: QaSession,
        options: QaStartOptions,
        onProgress: () -> Unit,
    ) {
        session.events.add(QaEvent(world.currentCycle, "suite_started", "Started suite '${suite.id}'.", status = QaStatus.RUNNING.value))
        fixtureService.applyFixture(world, bot, suite.fixture)
        onProgress()

        val repeatCount = options.repeatCount.coerceIn(1, 25)
        for (repeat in 1..repeatCount) {
            if (repeatCount > 1) {
                session.events.add(QaEvent(world.currentCycle, "suite_repeat", "Starting suite repeat $repeat of $repeatCount."))
                onProgress()
            }
            for (journey in suite.journeys) {
                runJourney(task, world, bot, suite, journey, scenarios, session, options, onProgress)
            }
        }

        session.events.add(QaEvent(world.currentCycle, "suite_finished", "Finished suite '${suite.id}'."))
        onProgress()
    }

    private suspend fun runJourney(
        task: QueueTask,
        world: World,
        bot: QaPlayer,
        suite: QaSuite,
        journey: QaJourney,
        scenarios: Map<String, QaScenario>,
        session: QaSession,
        options: QaStartOptions,
        onProgress: () -> Unit,
    ) {
        val result =
            QaJourneyResult(
                id = journey.id,
                name = journey.name.ifBlank { journey.id },
                category = journey.category,
                startedAtCycle = world.currentCycle,
            )
        session.currentJourneyId = journey.id
        session.journeys.add(result)
        session.events.add(QaEvent(world.currentCycle, "journey_started", "Started journey '${journey.id}'.", journeyId = journey.id))
        fixtureService.applyFixture(world, bot, journey.fixture, result)
        onProgress()

        var journeyFailed = false
        for (goal in journey.goals) {
            session.currentStepId = goal.id.ifBlank { goal.stepRef }
            val step = resolveGoal(goal, scenarios, suite, journey)
            val maxAttempts = maxAttempts(goal, journey, suite)
            var finalAttempt: QaGoalResult? = null

            for (attempt in 1..maxAttempts) {
                session.events.add(
                    QaEvent(
                        cycle = world.currentCycle,
                        type = "goal_started",
                        message = "Started goal '${step.id}' attempt $attempt of $maxAttempts.",
                        journeyId = journey.id,
                        goalId = step.id,
                    ),
                )
                onProgress()

                val stepResult = driver.runStep(task, bot, step)
                session.steps.add(stepResult)
                session.assertions.addAll(stepResult.assertions)
                val goalResult =
                    QaGoalResult(
                        id = step.id,
                        name = goal.name.ifBlank { step.id },
                        attempt = attempt,
                        maxAttempts = maxAttempts,
                        step = stepResult,
                    )
                result.goals.add(goalResult)
                finalAttempt = goalResult
                session.events.add(
                    QaEvent(
                        cycle = world.currentCycle,
                        type = "goal_finished",
                        message = "Finished goal '${step.id}' with status ${stepResult.status}.",
                        journeyId = journey.id,
                        goalId = step.id,
                        status = stepResult.status,
                    ),
                )
                stepResult.observations.forEach { observation ->
                    session.observations.add("${journey.id}/${step.id}: $observation")
                }

                if (stepResult.status == QaStatus.PASSED.value) {
                    break
                }

                val failure =
                    QaFailure(
                        journeyId = journey.id,
                        goalId = step.id,
                        failureClass = stepResult.failureClass ?: "timeout",
                        message = stepResult.observations.lastOrNull() ?: "Goal '${step.id}' failed.",
                        attempt = attempt,
                    )
                session.failures.add(failure)
                journeyFailed = true

                if (attempt < maxAttempts && shouldResetFixtures(goal, journey, suite)) {
                    val cleanup = fixtureService.cleanupJourney(world, bot)
                    result.observations.add("Reset fixtures after failed attempt $attempt of '${step.id}'.")
                    result.cleanup = cleanup
                    fixtureService.applyFixture(world, bot, suite.fixture)
                    fixtureService.applyFixture(world, bot, journey.fixture, result)
                }
                onProgress()
            }

            val continueAfterFailure =
                goal.continueOnFailure
                    ?: journey.continueOnFailure
                    ?: options.continueOnFailure
                    && suite.continueOnFailure
            if (finalAttempt?.status == QaStatus.FAILED.value && !continueAfterFailure) {
                break
            }
        }

        result.status = if (journeyFailed || result.goals.any { it.status == QaStatus.FAILED.value }) QaStatus.FAILED.value else QaStatus.PASSED.value
        result.failureClass = result.goals.lastOrNull { it.status == QaStatus.FAILED.value }?.failureClass
        result.finishedAtCycle = world.currentCycle
        result.cleanup = fixtureService.cleanupJourney(world, bot)
        session.cleanup = result.cleanup
        session.events.add(
            QaEvent(
                cycle = world.currentCycle,
                type = "journey_finished",
                message = "Finished journey '${journey.id}' with status ${result.status}.",
                journeyId = journey.id,
                status = result.status,
            ),
        )
        onProgress()
    }

    private fun resolveGoal(
        goal: QaGoal,
        scenarios: Map<String, QaScenario>,
        suite: QaSuite,
        journey: QaJourney,
    ): QaScenarioStep {
        goal.stepRef?.let { ref ->
            val explicitScenario = ref.substringBefore(":", missingDelimiterValue = "")
            val stepId = ref.substringAfter(":", ref)
            val scenarioPool =
                if (explicitScenario.isNotBlank() && explicitScenario != stepId) {
                    listOfNotNull(scenarios[explicitScenario])
                } else {
                    scenarios.values.toList()
                }
            scenarioPool.forEach { scenario ->
                scenario.steps.firstOrNull { it.id == stepId }?.let { return it }
            }
            return QaScenarioStep(
                id = stepId,
                category = journey.category,
                type = "missing-content-probe",
                failureClass = "missing_content",
            )
        }

        return QaScenarioStep(
            id = goal.id.ifBlank { "goal-${journey.goals.indexOf(goal) + 1}" },
            skill = goal.skill,
            category = goal.category.ifBlank { journey.category },
            type = goal.type.ifBlank { "world-sanity-probe" },
            setup = goal.setup,
            action = goal.action,
            expect = goal.expect,
            timeoutTicks = goal.timeoutTicks ?: suite.defaultTimeoutTicks,
            failureClass = goal.failureClass,
        )
    }

    private fun maxAttempts(
        goal: QaGoal,
        journey: QaJourney,
        suite: QaSuite,
    ): Int {
        val configured =
            when {
                goal.retryPolicy.maxAttempts != 3 -> goal.retryPolicy.maxAttempts
                journey.retryPolicy.maxAttempts != 3 -> journey.retryPolicy.maxAttempts
                else -> suite.retryPolicy.maxAttempts
            }
        return configured.coerceIn(1, 5)
    }

    private fun shouldResetFixtures(
        goal: QaGoal,
        journey: QaJourney,
        suite: QaSuite,
    ): Boolean =
        when {
            goal.retryPolicy.maxAttempts != 3 -> goal.retryPolicy.resetFixturesBetweenAttempts
            journey.retryPolicy.maxAttempts != 3 -> journey.retryPolicy.resetFixturesBetweenAttempts
            else -> suite.retryPolicy.resetFixturesBetweenAttempts
        }
}
