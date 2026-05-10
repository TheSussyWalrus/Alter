package org.alter.plugins.content.tools.qabot

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import gg.rsmod.util.ServerProperties
import net.rsprot.protocol.common.client.OldSchoolClientType
import org.alter.game.Server
import org.alter.game.model.PlayerUID
import org.alter.game.model.Tile
import org.alter.game.model.World
import org.alter.game.model.entity.Player
import org.alter.game.model.move.moveTo
import org.alter.game.model.priv.Privilege
import org.alter.game.service.Service
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

class QaBotService : Service {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val driver = QaActionDriver()
    private val fixtureService = QaFixtureService()
    private val planner = QaPlanner(driver, fixtureService)
    private val scenarios: MutableMap<String, QaScenario> = linkedMapOf()
    private val suites: MutableMap<String, QaSuite> = linkedMapOf()
    private var activeSession: QaSession? = null
    private var activeBot: QaPlayer? = null
    private var stopRequested = false

    lateinit var scenariosPath: Path
        private set

    lateinit var suitesPath: Path
        private set

    lateinit var sessionsPath: Path
        private set

    override fun init(server: Server, world: World, serviceProperties: ServerProperties) {
        scenariosPath = resolvePath(serviceProperties.get("qa.scenarios") ?: DEFAULT_SCENARIOS_PATH)
        suitesPath = resolvePath(serviceProperties.get("qa.suites") ?: DEFAULT_SUITES_PATH)
        sessionsPath = resolvePath(serviceProperties.get("qa.sessions") ?: DEFAULT_SESSIONS_PATH)
        loadScenarios()
        loadSuites()
        Files.createDirectories(sessionsPath)
        Server.logger.info {
            "Loaded ${scenarios.size} QA scenario${if (scenarios.size == 1) "" else "s"} and " +
                "${suites.size} QA suite${if (suites.size == 1) "" else "s"}."
        }
    }

    @Synchronized
    fun listScenarios(): List<QaScenario> = scenarios.values.sortedBy { it.id }

    @Synchronized
    fun listSuites(): List<QaSuite> = suites.values.sortedBy { it.id }

    @Synchronized
    fun getScenario(id: String): QaScenario? = scenarios[id]

    @Synchronized
    fun getSuite(id: String): QaSuite? = suites[id]

    @Synchronized
    fun fixtureStatus(): JsonObject = fixtureService.status()

    @Synchronized
    fun status(): JsonObject =
        JsonObject().apply {
            addProperty("running", activeSession?.status == QaStatus.RUNNING.value)
            addProperty("activeSessionId", activeSession?.id)
            addProperty("botName", activeBot?.username ?: DEFAULT_BOT_NAME)
            addProperty("scenarioCount", scenarios.size)
            addProperty("suiteCount", suites.size)
            addProperty("sessionsPath", sessionsPath.toString())
            addProperty("suitesPath", suitesPath.toString())
            add("fixtures", fixtureService.status())
            add("activeSession", activeSession?.let { gson.toJsonTree(it) } ?: com.google.gson.JsonNull.INSTANCE)
        }

    @Synchronized
    fun listSessionReports(limit: Int = 50): List<JsonObject> {
        if (!Files.exists(sessionsPath)) {
            return emptyList()
        }
        val reports = mutableListOf<JsonObject>()
        Files.list(sessionsPath).use { stream ->
            stream
                .filter { it.fileName.toString().endsWith(".json") }
                .sorted(Comparator.reverseOrder())
                .limit(limit.toLong())
                .forEach { path -> readSession(path)?.let(reports::add) }
        }
        return reports
    }

    @Synchronized
    fun getSessionReport(id: String): JsonObject? {
        activeSession?.takeIf { it.id == id }?.let { return gson.toJsonTree(it).asJsonObject }
        val safeId = id.replace(Regex("[^A-Za-z0-9_.-]"), "")
        if (safeId.isBlank()) {
            return null
        }
        return readSession(sessionsPath.resolve("$safeId.json"))
    }

    @Synchronized
    fun getSessionEvents(id: String): JsonArray? =
        getSessionReport(id)?.getAsJsonArray("events")

    @Synchronized
    fun startSession(
        world: World,
        scenarioId: String? = null,
        requestedBy: Player? = null,
    ): QaSession = startSession(world, QaStartOptions(scenarioId = scenarioId), requestedBy)

    @Synchronized
    fun startSession(
        world: World,
        options: QaStartOptions,
        requestedBy: Player? = null,
    ): QaSession {
        activeSession?.takeIf { it.status == QaStatus.RUNNING.value }?.let {
            error("QA session '${it.id}' is already running.")
        }

        val requestedId = options.suiteId?.takeIf { it.isNotBlank() } ?: options.scenarioId?.takeIf { it.isNotBlank() }
        val suite =
            requestedId?.let { suites[it] }
                ?: options.suiteId?.takeIf { it.isNotBlank() }?.let { error("QA suite '$it' was not found.") }
        val scenario =
            if (suite == null) {
                scenarios[requestedId ?: DEFAULT_SCENARIO_ID]
                    ?: scenarios.values.firstOrNull()
                    ?: error("No QA scenarios are loaded.")
            } else {
                null
            }

        stopRequested = false
        val session =
            QaSession(
                id = "${DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(java.time.ZoneOffset.UTC).format(Instant.now())}-${UUID.randomUUID().toString().take(8)}",
                scenarioId = scenario?.id ?: suite!!.id,
                scenarioName = scenario?.name ?: suite!!.name,
                seed = options.seed ?: scenario?.seed ?: suite!!.seed,
                requestedBy = requestedBy?.username ?: "rest-api",
                startedAt = now(),
                runMode = if (suite != null) "suite" else "scenario",
                suiteId = suite?.id,
                suiteName = suite?.name,
                repeatCount = options.repeatCount.coerceIn(1, 25),
                fixtureMode = options.fixtureMode,
            )
        activeSession = session
        val bot = createBot(world, session)
        activeBot = bot
        session.botTile = QaTile.from(bot.tile)
        writeSession(session)

        world.queue {
            try {
                if (suite != null) {
                    runSuite(this, world, bot, suite, session, options)
                } else if (scenario != null) {
                    runScenario(this, world, bot, scenario, session)
                }
            } catch (t: Throwable) {
                synchronized(this@QaBotService) {
                    session.status = QaStatus.FAILED.value
                    session.finishedAt = now()
                    session.warnings.add(t.message ?: t::class.java.simpleName)
                    session.events.add(QaEvent(world.currentCycle, "session_error", t.message ?: t::class.java.simpleName, status = QaStatus.FAILED.value))
                    session.cleanup = fixtureService.cleanupJourney(world, bot)
                    writeSession(session)
                    cleanupBot(world)
                }
            }
        }
        return session
    }

    @Synchronized
    fun stopSession(
        world: World,
        id: String? = null,
    ): QaSession? {
        val session = activeSession ?: return null
        if (id != null && id != session.id) {
            return null
        }
        stopRequested = true
        session.status = QaStatus.STOPPED.value
        session.finishedAt = session.finishedAt ?: now()
        session.warnings.add("Stop requested.")
        session.events.add(QaEvent(world.currentCycle, "session_stopped", "Stop requested.", status = QaStatus.STOPPED.value))
        activeBot?.interruptQueues()
        session.cleanup = fixtureService.cleanupJourney(world, activeBot)
        writeSession(session)
        cleanupBot(world)
        return session
    }

    private suspend fun runSuite(
        task: org.alter.game.model.queue.QueueTask,
        world: World,
        bot: QaPlayer,
        suite: QaSuite,
        session: QaSession,
        options: QaStartOptions,
    ) {
        session.observations.add("Started suite '${suite.id}' with ${suite.journeys.size} journey(s).")
        planner.runSuite(task, world, bot, suite, scenarios, session, options) {
            synchronized(this@QaBotService) {
                session.botTile = QaTile.from(bot.tile)
                writeSession(session)
            }
        }
        synchronized(this) {
            val failed = session.journeys.any { it.status == QaStatus.FAILED.value } || session.steps.any { it.status == QaStatus.FAILED.value }
            session.status = if (failed) QaStatus.FAILED.value else QaStatus.PASSED.value
            session.finishedAt = now()
            session.currentStepId = null
            session.currentJourneyId = null
            session.botTile = QaTile.from(bot.tile)
            writeSession(session)
            cleanupBot(world)
        }
    }

    private suspend fun runScenario(
        task: org.alter.game.model.queue.QueueTask,
        world: World,
        bot: QaPlayer,
        scenario: QaScenario,
        session: QaSession,
    ) {
        session.observations.add("Started scenario '${scenario.id}' with ${scenario.steps.size} step(s).")
        for (step in scenario.steps) {
            synchronized(this) {
                if (stopRequested) {
                    session.status = QaStatus.STOPPED.value
                    session.finishedAt = now()
                    writeSession(session)
                    cleanupBot(world)
                    return
                }
                session.currentStepId = step.id
                session.events.add(QaEvent(world.currentCycle, "step_started", "Started scenario step '${step.id}'.", goalId = step.id))
                session.botTile = QaTile.from(bot.tile)
                writeSession(session)
            }

            val result = driver.runStep(task, bot, step)
            synchronized(this) {
                session.steps.add(result)
                session.assertions.addAll(result.assertions)
                session.observations.addAll(result.observations.map { "${step.id}: $it" })
                session.events.add(QaEvent(world.currentCycle, "step_finished", "Finished scenario step '${step.id}' with status ${result.status}.", goalId = step.id, status = result.status))
                session.botTile = QaTile.from(bot.tile)
                writeSession(session)
            }
        }

        synchronized(this) {
            val failed = session.steps.any { it.status == QaStatus.FAILED.value }
            session.status = if (failed) QaStatus.FAILED.value else QaStatus.PASSED.value
            session.finishedAt = now()
            session.currentStepId = null
            session.botTile = QaTile.from(bot.tile)
            session.cleanup = fixtureService.cleanupJourney(world, bot)
            writeSession(session)
            cleanupBot(world)
        }
    }

    private fun createBot(
        world: World,
        session: QaSession,
    ): QaPlayer {
        activeBot?.let { cleanupBot(world) }
        val bot = QaPlayer(world)
        bot.uid = PlayerUID("qabot:${session.id}")
        bot.username = DEFAULT_BOT_NAME
        bot.privilege = world.privileges.get("owner") ?: world.privileges.get("admin") ?: Privilege.DEFAULT
        bot.tile = DEFAULT_START_TILE
        bot.invisible = false
        bot.xpRate = 1.0
        check(bot.register()) { "Unable to register QA bot player." }
        bot.playerInfo = world.network.playerInfoProtocol.alloc(bot.index, OldSchoolClientType.DESKTOP)
        bot.npcInfo = world.network.npcInfoProtocol.alloc(bot.index, OldSchoolClientType.DESKTOP)
        bot.worldEntityInfo = world.network.worldEntityInfoProtocol.alloc(bot.index, OldSchoolClientType.DESKTOP)
        bot.login()
        bot.moveTo(DEFAULT_START_TILE)
        return bot
    }

    private fun cleanupBot(world: World) {
        val bot = activeBot ?: return
        fixtureService.cleanupJourney(world, bot)
        if (bot.index != -1 && world.players.contains(bot)) {
            bot.interruptQueues()
            world.unregister(bot)
        }
        activeBot = null
        if (activeSession?.status != QaStatus.RUNNING.value) {
            activeSession = null
        }
    }

    private fun loadScenarios() {
        scenarios.clear()
        if (!Files.exists(scenariosPath)) {
            Files.createDirectories(scenariosPath)
            return
        }
        val files =
            if (Files.isDirectory(scenariosPath)) {
                Files.list(scenariosPath).use { stream ->
                    stream.filter { it.fileName.toString().endsWith(".json") }.toList()
                }
            } else {
                listOf(scenariosPath)
            }
        files.forEach { path ->
            runCatching {
                Files.newBufferedReader(path).use { reader ->
                    val element = com.google.gson.JsonParser.parseReader(reader)
                    when {
                        element.isJsonArray -> {
                            val type = object : TypeToken<List<QaScenario>>() {}.type
                            gson.fromJson<List<QaScenario>>(element, type).forEach { scenario ->
                                if (scenario.id.isNotBlank()) {
                                    scenarios[scenario.id] = scenario
                                }
                            }
                        }
                        element.isJsonObject -> {
                            val scenario = gson.fromJson(element, QaScenario::class.java)
                            if (scenario.id.isNotBlank()) {
                                scenarios[scenario.id] = scenario
                            }
                        }
                    }
                }
            }.onFailure { t ->
                Server.logger.warn(t) { "Failed to load QA scenario config $path." }
            }
        }
    }

    private fun loadSuites() {
        suites.clear()
        if (!Files.exists(suitesPath)) {
            Files.createDirectories(suitesPath)
            return
        }
        val files =
            if (Files.isDirectory(suitesPath)) {
                Files.list(suitesPath).use { stream ->
                    stream.filter { it.fileName.toString().endsWith(".json") }.toList()
                }
            } else {
                listOf(suitesPath)
            }
        files.forEach { path ->
            runCatching {
                Files.newBufferedReader(path).use { reader ->
                    val element = com.google.gson.JsonParser.parseReader(reader)
                    when {
                        element.isJsonArray -> {
                            val type = object : TypeToken<List<QaSuite>>() {}.type
                            gson.fromJson<List<QaSuite>>(element, type).forEach { suite ->
                                if (suite.id.isNotBlank()) {
                                    suites[suite.id] = suite
                                }
                            }
                        }
                        element.isJsonObject -> {
                            val suite = gson.fromJson(element, QaSuite::class.java)
                            if (suite.id.isNotBlank()) {
                                suites[suite.id] = suite
                            }
                        }
                    }
                }
            }.onFailure { t ->
                Server.logger.warn(t) { "Failed to load QA suite config $path." }
            }
        }
    }

    private fun readSession(path: Path): JsonObject? =
        runCatching {
            if (!Files.exists(path)) {
                return null
            }
            Files.newBufferedReader(path).use { reader ->
                gson.fromJson(reader, JsonObject::class.java)
            }
        }.getOrNull()

    private fun writeSession(session: QaSession) {
        Files.createDirectories(sessionsPath)
        val path = sessionsPath.resolve("${session.id}.json")
        val temp = path.resolveSibling("${path.fileName}.tmp")
        Files.writeString(temp, gson.toJson(session))
        try {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun resolvePath(rawPath: String): Path {
        val direct = Paths.get(rawPath)
        val cwd = Paths.get("").toAbsolutePath()
        val parent = cwd.parent
        val candidates =
            listOfNotNull(
                direct,
                cwd.resolve(rawPath),
                parent?.resolve(rawPath),
                Paths.get("..").resolve(rawPath),
            )
        return candidates
            .map { it.toAbsolutePath().normalize() }
            .firstOrNull { Files.exists(it) || Files.exists(it.parent) }
            ?: direct.toAbsolutePath().normalize()
    }

    private fun now(): String = Instant.now().toString()

    companion object {
        const val DEFAULT_BOT_NAME = "Tannie Bot"
        private const val DEFAULT_SCENARIO_ID = "skills-basic"
        private const val DEFAULT_SCENARIOS_PATH = "data/cfg/qa/scenarios"
        private const val DEFAULT_SUITES_PATH = "data/cfg/qa/suites"
        private const val DEFAULT_SESSIONS_PATH = "data/qa/sessions"
        private val DEFAULT_START_TILE = Tile(2606, 3093, 0)
    }
}
