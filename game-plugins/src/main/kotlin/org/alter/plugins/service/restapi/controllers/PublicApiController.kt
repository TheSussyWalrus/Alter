package org.alter.plugins.service.restapi.controllers

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSyntaxException
import dev.openrune.cache.CacheManager.getNpc
import org.alter.game.model.PlayerUID
import org.alter.game.model.Tile
import org.alter.game.model.World
import org.alter.game.model.attr.APPEARANCE_SET_ATTR
import org.alter.game.model.attr.NEW_ACCOUNT_ATTR
import org.alter.game.model.entity.Client
import org.alter.game.model.entity.Player
import org.alter.game.model.skill.SkillSet
import org.alter.game.saving.PlayerDetails
import org.alter.game.saving.PlayerSaving
import org.alter.plugins.content.tools.npcdefs.NpcDefinitionEntry
import org.alter.plugins.content.tools.npcdefs.NpcDefinitionService
import org.alter.plugins.content.tools.npcdefs.NpcDropDefinition
import org.alter.plugins.content.tools.npcdefs.NpcDropEntry
import org.mindrot.jbcrypt.BCrypt
import spark.Request
import spark.Response
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale

class PublicApiController(private val world: World) {
    private val gson = Gson()
    private val skillNames =
        listOf(
            "attack",
            "defence",
            "strength",
            "hitpoints",
            "ranged",
            "prayer",
            "magic",
            "cooking",
            "woodcutting",
            "fletching",
            "fishing",
            "firemaking",
            "crafting",
            "smithing",
            "mining",
            "herblore",
            "agility",
            "thieving",
            "slayer",
            "farming",
            "runecraft",
            "hunter",
            "construction",
        )

    fun status(res: Response): String =
        ok(
            res,
            JsonObject().apply {
                addProperty("status", "online")
                addProperty("apiVersion", "v1")
                addProperty("game", world.gameContext.name)
                addProperty("revision", world.gameContext.revision)
                addProperty("cycle", world.currentCycle)
                addProperty("playersOnline", world.players.count())
                addProperty("playerLimit", world.gameContext.playerLimit)
            },
        )

    fun online(res: Response): String {
        val players = JsonArray()
        world.players.forEach { player ->
            players.add(playerSummary(player, includeSkills = false))
        }
        return ok(
            res,
            JsonObject().apply {
                addProperty("count", players.size())
                add("players", players)
            },
        )
    }

    fun players(req: Request, res: Response): String {
        val query = req.queryParams("q").orEmpty().trim().lowercase(Locale.ROOT)
        val limit = limit(req, default = 50, max = 250)
        val profiles =
            savedProfiles()
                .asSequence()
                .filter { profile -> query.isBlank() || profile.username.lowercase(Locale.ROOT).contains(query) }
                .sortedBy { it.username.lowercase(Locale.ROOT) }
                .take(limit)
                .toList()
        return ok(
            res,
            JsonObject().apply {
                addProperty("query", query)
                addProperty("count", profiles.size)
                add(
                    "players",
                    profiles.fold(JsonArray()) { arr, profile ->
                        arr.add(profile.toJson(includeSkills = false))
                        arr
                    },
                )
            },
        )
    }

    fun player(req: Request, res: Response): String {
        val name = req.params("name")?.trim().orEmpty()
        val live = world.getPlayerForName(name)
        val profile =
            live?.toSavedProfile()
                ?: savedProfiles().firstOrNull { it.username.equals(name, ignoreCase = true) }
                ?: return error(res, 404, "Player '$name' was not found.")
        return ok(
            res,
            JsonObject().apply {
                add("player", profile.toJson(includeSkills = true))
            },
        )
    }

    fun highscores(req: Request, res: Response): String {
        val skillParam = req.queryParams("skill") ?: req.queryParams("type") ?: "overall"
        val skill = skillParam.trim().lowercase(Locale.ROOT)
        val limit = limit(req, default = 25, max = 250)
        val ranked =
            savedProfiles()
                .asSequence()
                .mapNotNull { profile ->
                    val skillIndex = skillIndex(skill)
                    val score =
                        if (skillIndex == null) {
                            HighscoreScore(profile.totalLevel, profile.totalXp)
                        } else {
                            val entry = profile.skills.firstOrNull { it.id == skillIndex } ?: return@mapNotNull null
                            HighscoreScore(entry.level, entry.xp)
                        }
                    profile to score
                }
                .sortedWith(compareByDescending<Pair<SavedProfile, HighscoreScore>> { it.second.xp }.thenBy { it.first.username.lowercase(Locale.ROOT) })
                .take(limit)
                .toList()
        return ok(
            res,
            JsonObject().apply {
                addProperty("skill", if (skillIndex(skill) == null) "overall" else skill)
                addProperty("count", ranked.size)
                val entries = JsonArray()
                ranked.forEachIndexed { index, (profile, score) ->
                    entries.add(
                        JsonObject().apply {
                            addProperty("rank", index + 1)
                            addProperty("username", profile.username)
                            addProperty("displayName", profile.displayName)
                            addProperty("level", score.level)
                            addProperty("xp", score.xp)
                        },
                    )
                }
                add("entries", entries)
            },
        )
    }

    fun searchNpcs(req: Request, res: Response): String {
        val service = npcDefinitionService(res) ?: return currentResponse(res)
        val query = req.queryParams("q").orEmpty()
        val limit = limit(req, default = 50, max = 250)
        val results = JsonArray()
        service.searchNpcs(query, limit).forEach { npc ->
            results.add(
                JsonObject().apply {
                    addProperty("id", npc.id)
                    addProperty("name", npc.name)
                    addProperty("defined", npc.defined)
                    service.getDefinition(npc.id)?.imageUrl?.let { addProperty("imageUrl", it) }
                },
            )
        }
        return ok(
            res,
            JsonObject().apply {
                addProperty("query", query)
                addProperty("count", results.size())
                add("results", results)
            },
        )
    }

    fun npc(req: Request, res: Response): String {
        val service = npcDefinitionService(res) ?: return currentResponse(res)
        val npcId = req.params("npcId").toIntOrNull() ?: return error(res, 400, "npcId must be an integer.")
        val name = service.npcName(npcId) ?: return error(res, 404, "NPC id $npcId was not found.")
        return ok(
            res,
            JsonObject().apply {
                add("npc", publicNpc(service, npcId, name))
            },
        )
    }

    fun npcDrops(req: Request, res: Response): String {
        val service = npcDefinitionService(res) ?: return currentResponse(res)
        val npcId = req.params("npcId").toIntOrNull() ?: return error(res, 400, "npcId must be an integer.")
        val name = service.npcName(npcId) ?: return error(res, 404, "NPC id $npcId was not found.")
        val definition = service.getDefinition(npcId)
        return ok(
            res,
            JsonObject().apply {
                addProperty("id", npcId)
                addProperty("name", name)
                addProperty("defined", definition != null)
                add("drops", publicDrops(service, definition?.drops ?: NpcDropDefinition()))
            },
        )
    }

    fun searchItems(req: Request, res: Response): String {
        val service = npcDefinitionService(res) ?: return currentResponse(res)
        val query = req.queryParams("q").orEmpty()
        val limit = limit(req, default = 50, max = 250)
        val results = JsonArray()
        service.searchItems(query, limit).forEach { item ->
            results.add(
                JsonObject().apply {
                    addProperty("id", item.id)
                    addProperty("name", item.name)
                    addProperty("noted", item.noted)
                    addProperty("placeholder", item.placeholder)
                    addProperty("stackable", item.stackable)
                },
            )
        }
        return ok(
            res,
            JsonObject().apply {
                addProperty("query", query)
                addProperty("count", results.size())
                add("results", results)
            },
        )
    }

    fun verify(req: Request, res: Response): String {
        val body = parseBody(req, res) ?: return currentResponse(res)
        val username = normalizeLogin(body.optionalString("username") ?: body.optionalString("loginUsername") ?: "")
            ?: return error(res, 400, "username must be 1-12 letters, numbers, spaces, underscores, or hyphens.")
        val password = body.optionalString("password") ?: return error(res, 400, "password is required.")
        val document = savedPlayerDocument(username) ?: return error(res, 401, "Invalid username or password.")
        val passwordHash = document.optionalString("passwordHash") ?: return error(res, 401, "Invalid username or password.")
        if (!BCrypt.checkpw(password, passwordHash)) {
            return error(res, 401, "Invalid username or password.")
        }
        return ok(
            res,
            JsonObject().apply {
                addProperty("verified", true)
                add("account", accountDto(username))
            },
        )
    }

    fun register(req: Request, res: Response): String {
        val body = parseBody(req, res) ?: return currentResponse(res)
        val username = normalizeLogin(body.optionalString("username") ?: body.optionalString("loginUsername") ?: "")
            ?: return error(res, 400, "username must be 1-12 letters, numbers, spaces, underscores, or hyphens.")
        val password = body.optionalString("password") ?: return error(res, 400, "password is required.")
        if (password.length < 4) {
            return error(res, 400, "password must be at least 4 characters.")
        }
        val client = Client(world).apply {
            loginUsername = username
            this.username = username
            passwordHash = BCrypt.hashpw(password, BCrypt.gensalt(16))
            currentXteaKeys = IntArray(4)
            uid = PlayerUID(username)
            tile = world.gameContext.home
            attr[NEW_ACCOUNT_ATTR] = true
            attr[APPEARANCE_SET_ATTR] = false
        }
        if (PlayerDetails.playerExists(client) || savedPlayerDocument(username) != null) {
            return error(res, 409, "Account '$username' already exists.")
        }
        PlayerDetails.registerAccount(client)
        PlayerSaving.savePlayer(client)
        res.status(201)
        res.type("application/json")
        val bodyJson =
            gson.toJson(
                JsonObject().apply {
                    addProperty("registered", true)
                    add("account", accountDto(username))
                },
            )
        res.body(bodyJson)
        return bodyJson
    }

    private fun publicNpc(
        service: NpcDefinitionService,
        npcId: Int,
        fallbackName: String,
    ): JsonObject {
        val definition = service.getDefinition(npcId)
        val cache = runCatching { getNpc(npcId) }.getOrNull()
        return JsonObject().apply {
            addProperty("id", npcId)
            addProperty("name", definition?.name?.takeIf { it.isNotBlank() } ?: fallbackName)
            addProperty("defined", definition != null)
            addProperty("combatLevel", cache?.combatLevel ?: -1)
            addProperty("size", cache?.size ?: 1)
            addProperty("attackable", cache?.isAttackable() ?: false)
            add("actions", (cache?.actions ?: emptyList<String?>()).fold(JsonArray()) { arr, action ->
                if (!action.isNullOrBlank()) {
                    arr.add(action)
                }
                arr
            })
            if (definition != null) {
                addPublicDefinition(definition)
            }
            add("dropSummary", publicDropSummary(definition?.drops ?: NpcDropDefinition()))
        }
    }

    private fun JsonObject.addPublicDefinition(definition: NpcDefinitionEntry) {
        definition.imageUrl?.let { addProperty("imageUrl", it) }
        definition.shopKey?.let { addProperty("shopKey", it) }
        add("tags", definition.tags.fold(JsonArray()) { arr, tag -> arr.add(tag); arr })
        definition.notes?.let { addProperty("notes", it) }
        add(
            "combat",
            JsonObject().apply {
                add("stats", gson.toJsonTree(definition.combat.stats))
                add("bonuses", gson.toJsonTree(definition.combat.bonuses))
                addNullable("attackSpeed", definition.combat.attackSpeed)
                addNullable("respawnDelay", definition.combat.respawnDelay)
                addNullable("slayerReq", definition.combat.slayerReq)
                addNullable("slayerXp", definition.combat.slayerXp)
            },
        )
        add("aggression", gson.toJsonTree(definition.aggression))
        addProperty("followRange", definition.followRange)
    }

    private fun publicDrops(
        service: NpcDefinitionService,
        drops: NpcDropDefinition,
    ): JsonObject =
        JsonObject().apply {
            add("always", publicDropArray(service, drops.always))
            add("main", publicDropArray(service, drops.main))
            addProperty("mainEmptySlots", drops.mainEmptySlots)
            add("preroll", publicDropArray(service, drops.preroll))
            add("tertiary", publicDropArray(service, drops.tertiary))
        }

    private fun publicDropArray(
        service: NpcDefinitionService,
        drops: List<NpcDropEntry>,
    ): JsonArray =
        drops.fold(JsonArray()) { arr, drop ->
            arr.add(
                JsonObject().apply {
                    addProperty("itemId", drop.itemId)
                    addProperty("name", service.itemName(drop.itemId) ?: drop.name ?: "Unknown item")
                    addProperty("minAmount", drop.minAmount)
                    addProperty("maxAmount", drop.maxAmount)
                    add("weight", drop.weight?.let { JsonPrimitive(it) } ?: JsonNull.INSTANCE)
                    add("chance", drop.chance?.let { JsonPrimitive(it) } ?: JsonNull.INSTANCE)
                    add("numerator", drop.numerator?.let { JsonPrimitive(it) } ?: JsonNull.INSTANCE)
                    add("denominator", drop.denominator?.let { JsonPrimitive(it) } ?: JsonNull.INSTANCE)
                    addProperty("noted", drop.noted)
                },
            )
            arr
        }

    private fun publicDropSummary(drops: NpcDropDefinition): JsonObject =
        JsonObject().apply {
            addProperty("always", drops.always.size)
            addProperty("main", drops.main.size)
            addProperty("preroll", drops.preroll.size)
            addProperty("tertiary", drops.tertiary.size)
            addProperty("total", drops.always.size + drops.main.size + drops.preroll.size + drops.tertiary.size)
        }

    private fun accountDto(username: String): JsonObject {
        val displayName = PlayerDetails.getDisplayName(username)?.currentDisplayName ?: username
        return JsonObject().apply {
            addProperty("username", username)
            addProperty("displayName", displayName)
            addProperty("online", world.getPlayerForName(displayName) != null || world.getPlayerForName(username) != null)
            addProperty("role", savedRole(username))
        }
    }

    private fun savedProfiles(): List<SavedProfile> {
        val byName = linkedMapOf<String, SavedProfile>()
        savedPlayerDocuments().forEach { (loginName, document) ->
            val profile = profileFromDocument(loginName, document)
            byName[profile.username.lowercase(Locale.ROOT)] = profile
        }
        world.players.forEach { player ->
            byName[player.username.lowercase(Locale.ROOT)] = player.toSavedProfile()
        }
        return byName.values.toList()
    }

    private fun savedPlayerDocuments(): Map<String, JsonObject> {
        val detailsDir = saveDir("details") ?: return emptyMap()
        val documents = linkedMapOf<String, JsonObject>()
        return try {
            Files.list(detailsDir).use { stream ->
                stream.filter { Files.isRegularFile(it) }.forEach { path ->
                    val body = Files.readString(path)
                    val json = gson.fromJson(body, JsonObject::class.java)
                    if (json != null) {
                        documents[path.fileName.toString().substringBeforeLast(".")] = json
                    }
                }
            }
            documents
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    private fun savedPlayerDocument(username: String): JsonObject? =
        savedPlayerDocuments().entries.firstOrNull { it.key.equals(username, ignoreCase = true) }?.value

    private fun profileFromDocument(
        loginName: String,
        document: JsonObject,
    ): SavedProfile {
        val username = document.optionalString("loginUsername") ?: loginName
        val displayName = PlayerDetails.getDisplayName(username)?.currentDisplayName ?: username
        val skillsDoc =
            document.optionalObject("attributes")
                ?.optionalObject("skills")
                ?: JsonObject()
        val role =
            document.optionalObject("attributes")
                ?.optionalObject("details")
                ?.optionalString("privilege")
                ?.let(::roleForPrivilege)
                ?: "USER"
        val skills =
            skillsDoc.entrySet()
                .mapNotNull { entry -> entry.value.asJsonObjectOrNull()?.toSkillEntry() }
                .filter { it.id in skillNames.indices }
                .sortedBy { it.id }
        return SavedProfile(
            username = displayName,
            displayName = displayName,
            online = world.getPlayerForName(displayName) != null || world.getPlayerForName(username) != null,
            role = role,
            combatLevel = null,
            totalLevel = skills.sumOf { it.level },
            totalXp = skills.sumOf { it.xp },
            skills = skills,
        )
    }

    private fun Player.toSavedProfile(): SavedProfile {
        val skills =
            (0 until getSkills().maxSkills)
                .filter { it in skillNames.indices }
                .map { id ->
                    SkillEntry(
                        id = id,
                        name = skillNames[id],
                        level = getSkills().getBaseLevel(id),
                        currentLevel = getSkills().getCurrentLevel(id),
                        xp = getSkills().getCurrentXp(id).toLong(),
                    )
                }
        return SavedProfile(
            username = username,
            displayName = username,
            online = isOnline,
            role = roleForPrivilege(privilege.name),
            combatLevel = combatLevel,
            totalLevel = skills.sumOf { it.level },
            totalXp = skills.sumOf { it.xp },
            skills = skills,
        )
    }

    private fun JsonObject.toSkillEntry(): SkillEntry? {
        val id = optionalInt("id") ?: return null
        val xp = optionalDouble("xp") ?: 0.0
        return SkillEntry(
            id = id,
            name = skillNames.getOrElse(id) { "skill_$id" },
            level = SkillSet.getLevelForXp(xp),
            currentLevel = optionalInt("level") ?: SkillSet.getLevelForXp(xp),
            xp = xp.toLong(),
        )
    }

    private fun playerSummary(
        player: Player,
        includeSkills: Boolean,
    ): JsonObject =
        player.toSavedProfile().toJson(includeSkills)

    private fun SavedProfile.toJson(includeSkills: Boolean): JsonObject =
        JsonObject().apply {
            addProperty("username", username)
            addProperty("displayName", displayName)
            addProperty("online", online)
            addProperty("role", role)
            combatLevel?.let { addProperty("combatLevel", it) }
            addProperty("totalLevel", totalLevel)
            addProperty("totalXp", totalXp)
            if (includeSkills) {
                add(
                    "skills",
                    skills.fold(JsonArray()) { arr, skill ->
                        arr.add(skill.toJson())
                        arr
                    },
                )
            }
        }

    private fun SkillEntry.toJson(): JsonObject =
        JsonObject().apply {
            addProperty("id", id)
            addProperty("name", name)
            addProperty("level", level)
            addProperty("currentLevel", currentLevel)
            addProperty("xp", xp)
        }

    private fun parseBody(req: Request, res: Response): JsonObject? {
        val raw = req.body()?.trim().orEmpty()
        if (raw.isBlank()) {
            return JsonObject()
        }
        return try {
            gson.fromJson(raw, JsonObject::class.java) ?: JsonObject()
        } catch (_: JsonSyntaxException) {
            errorAndNull(res, 400, "Request body must be valid JSON.")
        }
    }

    private fun normalizeLogin(raw: String): String? {
        val normalized = raw.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
        if (normalized.length !in 1..12) {
            return null
        }
        if (!normalized.matches(Regex("[a-z0-9 _-]+"))) {
            return null
        }
        return normalized
    }

    private fun skillIndex(skill: String): Int? =
        skill.takeIf { it != "overall" }?.let { normalized ->
            normalized.toIntOrNull()?.takeIf { it in skillNames.indices }
                ?: skillNames.indexOf(normalized).takeIf { it >= 0 }
        }

    private fun savedRole(username: String): String =
        savedPlayerDocument(username)
            ?.optionalObject("attributes")
            ?.optionalObject("details")
            ?.optionalString("privilege")
            ?.let(::roleForPrivilege)
            ?: world.getPlayerForName(username)?.let { roleForPrivilege(it.privilege.name) }
            ?: "USER"

    private fun roleForPrivilege(privilege: String): String =
        when (privilege.trim().lowercase(Locale.ROOT)) {
            "owner", "admin", "administrator", "developer", "dev" -> "ADMIN"
            "mod", "moderator" -> "MODERATOR"
            "donor", "member" -> "DONOR"
            else -> "USER"
        }

    private fun limit(
        req: Request,
        default: Int,
        max: Int,
    ): Int = req.queryParams("limit")?.toIntOrNull()?.coerceIn(1, max) ?: default

    private fun npcDefinitionService(res: Response): NpcDefinitionService? =
        world.getService(NpcDefinitionService::class.java)
            ?: errorAndNull(res, 503, "NpcDefinitionService is not loaded.")

    private fun JsonObject.hasValue(name: String): Boolean = has(name) && !get(name).isJsonNull

    private fun JsonObject.optionalString(name: String): String? {
        if (!hasValue(name)) {
            return null
        }
        return runCatching { get(name).asString }.getOrNull()
    }

    private fun JsonObject.optionalInt(name: String): Int? {
        if (!hasValue(name)) {
            return null
        }
        return runCatching { get(name).asInt }.getOrNull()
    }

    private fun JsonObject.optionalDouble(name: String): Double? {
        if (!hasValue(name)) {
            return null
        }
        return runCatching { get(name).asDouble }.getOrNull()
    }

    private fun JsonObject.optionalObject(name: String): JsonObject? {
        if (!hasValue(name)) {
            return null
        }
        return get(name).asJsonObjectOrNull()
    }

    private fun JsonObject.addNullable(name: String, value: Number?) {
        add(name, value?.let { JsonPrimitive(it) } ?: JsonNull.INSTANCE)
    }

    private fun JsonElement.asJsonObjectOrNull(): JsonObject? =
        if (isJsonObject) asJsonObject else null

    private fun saveDir(collection: String): Path? {
        val cwd = Paths.get("").toAbsolutePath().normalize()
        val parent = cwd.parent
        val candidates =
            listOfNotNull(
                cwd.resolve("data/saves/$collection"),
                cwd.resolve("../data/saves/$collection"),
                parent?.resolve("data/saves/$collection"),
                parent?.resolve("Alter/data/saves/$collection"),
            )
        return candidates.map { it.normalize() }.firstOrNull { Files.isDirectory(it) }
    }

    private fun ok(res: Response, obj: JsonElement): String {
        res.status(200)
        res.type("application/json")
        val body = gson.toJson(obj)
        res.body(body)
        return body
    }

    private fun error(res: Response, status: Int, message: String): String {
        res.status(status)
        res.type("application/json")
        val body =
            gson.toJson(
                JsonObject().apply {
                    addProperty("error", message)
                },
            )
        res.body(body)
        return body
    }

    private fun <T> errorAndNull(res: Response, status: Int, message: String): T? {
        error(res, status, message)
        return null
    }

    private fun currentResponse(res: Response): String =
        res.body().takeIf { !it.isNullOrBlank() } ?: "{}"

    private data class SavedProfile(
        val username: String,
        val displayName: String,
        val online: Boolean,
        val role: String,
        val combatLevel: Int?,
        val totalLevel: Int,
        val totalXp: Long,
        val skills: List<SkillEntry>,
    )

    private data class SkillEntry(
        val id: Int,
        val name: String,
        val level: Int,
        val currentLevel: Int,
        val xp: Long,
    )

    private data class HighscoreScore(
        val level: Int,
        val xp: Long,
    )
}
