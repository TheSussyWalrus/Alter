package org.alter.plugins.content.tools.qabot

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import dev.openrune.cache.CacheManager.getNpc
import dev.openrune.cache.CacheManager.getObject
import org.alter.api.Skills
import org.alter.game.model.EntityType
import org.alter.game.model.Tile
import org.alter.game.model.attr.INTERACTING_ITEM
import org.alter.game.model.attr.INTERACTING_ITEM_ID
import org.alter.game.model.attr.INTERACTING_ITEM_SLOT
import org.alter.game.model.attr.INTERACTING_NPC_ATTR
import org.alter.game.model.attr.INTERACTING_OBJ_ATTR
import org.alter.game.model.attr.INTERACTING_OPT_ATTR
import org.alter.game.model.attr.INTERACTING_SLOT_ATTR
import org.alter.game.model.attr.OTHER_ITEM_ATTR
import org.alter.game.model.attr.OTHER_ITEM_ID_ATTR
import org.alter.game.model.attr.OTHER_ITEM_SLOT_ATTR
import org.alter.game.model.entity.GameObject
import org.alter.game.model.entity.Npc
import org.alter.game.model.move.moveTo
import org.alter.game.model.queue.QueueTask
import org.alter.rscm.RSCM.getRSCM
import java.lang.ref.WeakReference
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Locale

class QaActionDriver {
    suspend fun runStep(
        task: QueueTask,
        player: QaPlayer,
        step: QaScenarioStep,
    ): QaStepResult {
        val result =
            QaStepResult(
                id = step.id,
                skill = step.skill,
                category = step.category,
                type = step.type,
                startedAtCycle = player.world.currentCycle,
            )
        val before = player.snapshot()
        try {
            applySetup(player, step.setup, result)
            val actionStarted = performAction(player, step, result)
            val timeout = (step.timeoutTicks ?: 50).coerceAtLeast(1)
            var passed = false
            var waited = 0
            while (waited < timeout && !passed) {
                task.wait(1)
                waited++
                result.messages.addAll(player.drainMessages())
                val current = player.snapshot()
                passed = evaluateExpectations(player, step, before, current, result)
            }
            val after = player.snapshot()
            passed = evaluateExpectations(player, step, before, after, result)
            if (!actionStarted && step.type != "missing-content-probe" && step.type != "config-availability-probe" && step.type != "world-sanity-probe") {
                result.status = QaStatus.FAILED.value
                result.failureClass = "interaction_failed"
            } else if (passed) {
                result.status = QaStatus.PASSED.value
            } else {
                result.status = QaStatus.FAILED.value
                result.failureClass = classifyFailure(step, result)
            }
        } catch (t: Throwable) {
            result.status = QaStatus.FAILED.value
            result.failureClass = "server_error"
            result.observations.add(t.message ?: t::class.java.simpleName)
        } finally {
            result.messages.addAll(player.drainMessages())
            result.finishedAtCycle = player.world.currentCycle
        }
        return result
    }

    private fun applySetup(
        player: QaPlayer,
        setup: JsonObject,
        result: QaStepResult,
    ) {
        setup.obj("origin")?.let { origin ->
            val tile =
                Tile(
                    origin.int("x") ?: player.tile.x,
                    origin.int("z") ?: player.tile.z,
                    origin.int("height") ?: player.tile.height.coerceAtLeast(0),
                )
            player.moveTo(tile)
            result.observations.add("Moved QA bot to ${tile.x},${tile.z},${tile.height}.")
        }

        player.inventory.removeAll()
        setup.array("inventory")?.forEachObject { item ->
            val itemName = item.string("item") ?: return@forEachObject
            val amount = item.int("amount") ?: 1
            val itemId = rscmOrNull(itemName)
            if (itemId == null) {
                result.observations.add("Could not resolve setup item '$itemName'.")
            } else {
                player.inventory.add(itemId, amount, assureFullInsertion = false)
            }
        }

        setup.obj("skills")?.entrySet()?.forEach { (name, value) ->
            val level = runCatching { value.asInt }.getOrNull() ?: return@forEach
            val skill = skillId(name)
            if (skill == -1) {
                result.observations.add("Unknown setup skill '$name'.")
            } else {
                player.getSkills().setBaseLevel(skill, level.coerceIn(1, 99))
            }
        }
    }

    private fun performAction(
        player: QaPlayer,
        step: QaScenarioStep,
        result: QaStepResult,
    ): Boolean {
        if (step.type == "missing-content-probe") {
            result.observations.add("Marked missing-content probe; no interaction executed.")
            return true
        }
        if (step.type == "config-availability-probe") {
            return performConfigProbe(player, step, result)
        }
        if (step.type == "world-sanity-probe") {
            return performWorldProbe(player, step, result)
        }

        step.action.obj("useItemOnItem")?.let { action ->
            val item = action.string("item") ?: return false
            val target = action.string("targetItem") ?: return false
            return useItemOnItem(player, item, target, result)
        }
        step.action.obj("interactInventory")?.let { action ->
            val item = action.string("item") ?: return false
            val option = action.string("option") ?: "use"
            return interactInventory(player, item, option, result)
        }
        step.action.obj("useItemOnObject")?.let { action ->
            val item = action.string("item") ?: return false
            val targetKey = action.string("target") ?: "target"
            val target = findTargetObject(player, step.setup.obj("target"), targetKey, result) ?: return false
            return useItemOnObject(player, item, target, result)
        }
        step.action.obj("interact")?.let { action ->
            val option = action.string("option") ?: return false
            val target = step.setup.obj("target")
            val obj = findTargetObject(player, target, action.string("target") ?: "target", result)
            if (obj != null) {
                return interactObject(player, obj, option, result)
            }
            val npc = findTargetNpc(player, target, result)
            if (npc != null) {
                return interactNpc(player, npc, option, result)
            }
        }
        result.observations.add("No supported action shape found for step '${step.id}'.")
        return false
    }

    private fun performConfigProbe(
        player: QaPlayer,
        step: QaScenarioStep,
        result: QaStepResult,
    ): Boolean {
        step.setup.array("configRefs")?.forEach { ref ->
            val path = ref.asString
            val exists = Files.exists(Paths.get(path))
            result.assertions.add(QaAssertion("config:$path", exists, "exists", exists.toString()))
        }
        step.action.obj("requestTask")?.let { task ->
            val master = task.string("master")?.substringAfter("npc.") ?: return@let
            val handled = player.world.plugins.executeCommand(player, "slayer", arrayOf(master))
            result.assertions.add(QaAssertion("slayer-command", handled, "handled", handled.toString()))
        }
        result.observations.add("Config availability probe executed.")
        return true
    }

    private fun performWorldProbe(
        player: QaPlayer,
        step: QaScenarioStep,
        result: QaStepResult,
    ): Boolean {
        result.assertions.add(QaAssertion("world-has-players", player.world.players.count() > 0, "true", player.world.players.count().toString()))
        result.assertions.add(QaAssertion("world-has-npcs", player.world.npcs.count() > 0, "true", player.world.npcs.count().toString()))
        step.setup.array("anchors")?.forEachObject { anchor ->
            val name = anchor.string("npcName") ?: return@forEachObject
            val x = anchor.int("x")
            val z = anchor.int("z")
            val found =
                player.world.npcs.firstOrNull { npc ->
                    getNpc(npc.id).name.equals(name, ignoreCase = true) &&
                        (x == null || npc.tile.x == x) &&
                        (z == null || npc.tile.z == z)
                } != null
            result.assertions.add(QaAssertion("anchor:$name", found, "found", found.toString()))
        }
        result.observations.add("World sanity probe executed.")
        return true
    }

    private fun interactObject(
        player: QaPlayer,
        obj: GameObject,
        option: String,
        result: QaStepResult,
    ): Boolean {
        val opt = optionIndex(getObject(obj.id).actions.toList(), option)
        if (opt == -1) {
            result.observations.add("Object ${obj.id} has no option '$option'.")
            return false
        }
        player.attr[INTERACTING_OPT_ATTR] = opt
        player.attr[INTERACTING_OBJ_ATTR] = WeakReference(obj)
        val handled = player.world.plugins.executeObject(player, obj.id, opt)
        result.assertions.add(QaAssertion("object-plugin:${obj.id}:$opt", handled, "handled", handled.toString()))
        return handled
    }

    private fun interactNpc(
        player: QaPlayer,
        npc: Npc,
        option: String,
        result: QaStepResult,
    ): Boolean {
        val opt = optionIndex(getNpc(npc.id).actions.toList(), option)
        if (opt == -1) {
            result.observations.add("NPC ${npc.id} has no option '$option'.")
            return false
        }
        player.attr[INTERACTING_OPT_ATTR] = opt
        player.attr[INTERACTING_NPC_ATTR] = WeakReference(npc)
        val handled = player.world.plugins.executeNpc(player, npc.id, opt)
        result.assertions.add(QaAssertion("npc-plugin:${npc.id}:$opt", handled, "handled", handled.toString()))
        return handled
    }

    private fun interactInventory(
        player: QaPlayer,
        itemName: String,
        option: String,
        result: QaStepResult,
    ): Boolean {
        val itemId = rscmOrNull(itemName) ?: return missingRscm(itemName, result)
        val slot = player.inventory.getItemIndex(itemId, skipAttrItems = false)
        if (slot == -1) {
            result.observations.add("Inventory does not contain $itemName.")
            return false
        }
        val opt = optionIndex(getObjectSafeItemActions(itemId), option).takeIf { it != -1 } ?: 1
        player.attr[INTERACTING_OPT_ATTR] = opt
        player.attr[INTERACTING_ITEM_ID] = itemId
        player.attr[INTERACTING_SLOT_ATTR] = slot
        val handled = player.world.plugins.executeItem(player, itemId, opt)
        result.assertions.add(QaAssertion("item-plugin:$itemId:$opt", handled, "handled", handled.toString()))
        return handled
    }

    private fun useItemOnItem(
        player: QaPlayer,
        itemName: String,
        targetName: String,
        result: QaStepResult,
    ): Boolean {
        val itemId = rscmOrNull(itemName) ?: return missingRscm(itemName, result)
        val targetId = rscmOrNull(targetName) ?: return missingRscm(targetName, result)
        val slot = player.inventory.getItemIndex(itemId, skipAttrItems = false)
        val targetSlot = player.inventory.getItemIndex(targetId, skipAttrItems = false)
        if (slot == -1 || targetSlot == -1) {
            result.observations.add("Inventory missing item-on-item inputs $itemName/$targetName.")
            return false
        }
        val item = player.inventory[slot] ?: return false
        val target = player.inventory[targetSlot] ?: return false
        player.attr[INTERACTING_ITEM] = WeakReference(item)
        player.attr[INTERACTING_ITEM_ID] = item.id
        player.attr[INTERACTING_ITEM_SLOT] = slot
        player.attr[OTHER_ITEM_ATTR] = WeakReference(target)
        player.attr[OTHER_ITEM_ID_ATTR] = target.id
        player.attr[OTHER_ITEM_SLOT_ATTR] = targetSlot
        val handled = player.world.plugins.executeItemOnItem(player, item.id, target.id)
        result.assertions.add(QaAssertion("item-on-item:$itemId:$targetId", handled, "handled", handled.toString()))
        return handled
    }

    private fun useItemOnObject(
        player: QaPlayer,
        itemName: String,
        obj: GameObject,
        result: QaStepResult,
    ): Boolean {
        val itemId = rscmOrNull(itemName) ?: return missingRscm(itemName, result)
        val slot = player.inventory.getItemIndex(itemId, skipAttrItems = false)
        if (slot == -1) {
            result.observations.add("Inventory does not contain $itemName.")
            return false
        }
        val item = player.inventory[slot] ?: return false
        player.attr[INTERACTING_ITEM] = WeakReference(item)
        player.attr[INTERACTING_ITEM_SLOT] = slot
        player.attr[INTERACTING_OBJ_ATTR] = WeakReference(obj)
        val handled = player.world.plugins.executeItemOnObject(player, obj.id, item.id)
        result.assertions.add(QaAssertion("item-on-object:$itemId:${obj.id}", handled, "handled", handled.toString()))
        return handled
    }

    private fun evaluateExpectations(
        player: QaPlayer,
        step: QaScenarioStep,
        before: QaSnapshot,
        after: QaSnapshot,
        result: QaStepResult,
    ): Boolean {
        if (step.type == "missing-content-probe") {
            val refsMissing = step.setup.array("configRefs")?.all { !Files.exists(Paths.get(it.asString)) } ?: true
            result.assertions.addOrReplace(QaAssertion("missing-content-expected", refsMissing, "missing", refsMissing.toString()))
            return refsMissing
        }

        val assertions = mutableListOf<QaAssertion>()
        step.expect.obj("experienceDelta")?.let { xp ->
            val skillName = xp.string("skill") ?: step.skill
            val min = xp.double("min") ?: 0.0
            val delta = (after.xp[skillName] ?: 0.0) - (before.xp[skillName] ?: 0.0)
            assertions.add(QaAssertion("xp:$skillName", delta >= min, ">= $min", delta.toString()))
        }
        step.expect.array("inventoryDelta")?.forEachObject { expected ->
            expectedInventoryAssertion(expected, before, after)?.let(assertions::add)
        }
        step.expect.array("inventoryDeltaAny")?.let { array ->
            val options = array.mapNotNull { expectedInventoryAssertion(it.asJsonObject, before, after) }
            if (options.isNotEmpty()) {
                assertions.add(QaAssertion("inventory:any", options.any { it.passed }, "one matching delta", options.joinToString { "${it.name}=${it.actual}" }))
            }
        }
        step.expect.array("messagesAny")?.let { expected ->
            val messages = result.messages.joinToString("\n")
            val passed = expected.any { messages.contains(it.asString, ignoreCase = true) }
            assertions.add(QaAssertion("messages:any", passed, "one of configured messages", messages.take(200)))
        }
        step.expect.boolean("configLoaded")?.let {
            val allExist = step.setup.array("configRefs")?.all { ref -> Files.exists(Paths.get(ref.asString)) } ?: false
            assertions.add(QaAssertion("config-loaded", allExist == it, it.toString(), allExist.toString()))
        }
        if (step.expect.has("worldAvailable")) {
            assertions.add(QaAssertion("world-available", true, "true", "true"))
        }
        assertions.forEach { assertion ->
            result.assertions.addOrReplace(assertion)
        }
        return assertions.isNotEmpty() && assertions.all { it.passed }
    }

    private fun expectedInventoryAssertion(
        expected: JsonObject,
        before: QaSnapshot,
        after: QaSnapshot,
    ): QaAssertion? {
        val itemName = expected.string("item") ?: return null
        val itemId = rscmOrNull(itemName) ?: return QaAssertion("inventory:$itemName", false, "resolved RSCM", "missing RSCM")
        val beforeCount = before.inventory[itemId] ?: 0
        val afterCount = after.inventory[itemId] ?: 0
        expected.int("minAmount")?.let { min ->
            return QaAssertion("inventory:$itemName:min", afterCount - beforeCount >= min, ">= $min", (afterCount - beforeCount).toString())
        }
        expected.int("maxAmount")?.let { max ->
            return QaAssertion("inventory:$itemName:max", afterCount - beforeCount <= max, "<= $max", (afterCount - beforeCount).toString())
        }
        return null
    }

    private fun findTargetObject(
        player: QaPlayer,
        target: JsonObject?,
        label: String,
        result: QaStepResult,
    ): GameObject? {
        val ids = target?.array("objects")?.mapNotNull { rscmOrNull(it.asString) }.orEmpty()
        if (ids.isEmpty()) {
            result.observations.add("No object ids resolved for target '$label'.")
            return null
        }
        val radius = target?.int("searchRadius") ?: 32
        for (distance in 0..radius) {
            for (dx in -distance..distance) {
                for (dz in -distance..distance) {
                    if (kotlin.math.abs(dx) != distance && kotlin.math.abs(dz) != distance) {
                        continue
                    }
                    val tile = Tile(player.tile.x + dx, player.tile.z + dz, player.tile.height)
                    val chunk = player.world.chunks.get(tile, createIfNeeded = true) ?: continue
                    val obj =
                        chunk.getEntities<GameObject>(tile, EntityType.STATIC_OBJECT, EntityType.DYNAMIC_OBJECT)
                            .firstOrNull { it.id in ids }
                    if (obj != null) {
                        result.observations.add("Found object ${obj.id} for '$label' at ${obj.tile.x},${obj.tile.z}.")
                        return obj
                    }
                }
            }
        }
        result.observations.add("No target object found for '$label' within $radius tiles.")
        return null
    }

    private fun findTargetNpc(
        player: QaPlayer,
        target: JsonObject?,
        result: QaStepResult,
    ): Npc? {
        val ids = target?.array("npcs")?.mapNotNull { rscmOrNull(it.asString) }.orEmpty()
        if (ids.isEmpty()) {
            return null
        }
        val radius = target?.int("searchRadius") ?: 64
        val npc =
            player.world.npcs
                .firstOrNull { it.id in ids && it.tile.height == player.tile.height && it.tile.getDistance(player.tile) <= radius }
        if (npc != null) {
            result.observations.add("Found NPC ${npc.id} at ${npc.tile.x},${npc.tile.z}.")
        } else {
            result.observations.add("No target NPC found within $radius tiles.")
        }
        return npc
    }

    private fun QaPlayer.snapshot(): QaSnapshot {
        val inventory = mutableMapOf<Int, Int>()
        this.inventory.rawItems.filterNotNull().forEach { item -> inventory.merge(item.id, item.amount, Int::plus) }
        val xp = mutableMapOf<String, Double>()
        for (skill in 0 until getSkills().maxSkills) {
            val name = Skills.getSkillName(world, skill).lowercase(Locale.ROOT)
            xp[name] = getSkills().getCurrentXp(skill)
        }
        return QaSnapshot(inventory, xp)
    }

    private fun classifyFailure(
        step: QaScenarioStep,
        result: QaStepResult,
    ): String =
        when {
            result.assertions.any { it.name.startsWith("config") && !it.passed } -> "missing_content"
            result.assertions.any { it.name.startsWith("xp") && !it.passed } -> "no_progress"
            result.assertions.any { it.name.startsWith("inventory") && !it.passed } -> "wrong_reward"
            else -> step.failureClass.replace("-", "_").ifBlank { "timeout" }
        }

    private fun missingRscm(
        name: String,
        result: QaStepResult,
    ): Boolean {
        result.observations.add("RSCM name '$name' could not be resolved.")
        result.assertions.add(QaAssertion("rscm:$name", false, "resolved", "missing"))
        return false
    }

    private fun rscmOrNull(name: String): Int? =
        runCatching { getRSCM(name) }.getOrNull().takeIf { it != null && it >= 0 }

    private fun optionIndex(
        actions: List<String?>,
        option: String,
    ): Int {
        val normalized = option.normalizeOption()
        val index = actions.indexOfFirst { it?.normalizeOption() == normalized }
        return if (index == -1) -1 else index + 1
    }

    private fun getObjectSafeItemActions(itemId: Int): List<String?> =
        runCatching { dev.openrune.cache.CacheManager.getItem(itemId).options.toList() }.getOrDefault(emptyList())

    private fun skillId(name: String): Int =
        Skills.getSkillForNameStub(name)

    private fun String.normalizeOption(): String =
        lowercase(Locale.ROOT).replace("-", " ").replace("_", " ").trim()

    private fun JsonObject.obj(name: String): JsonObject? =
        get(name)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.array(name: String): JsonArray? =
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.int(name: String): Int? =
        runCatching { get(name)?.asInt }.getOrNull()

    private fun JsonObject.double(name: String): Double? =
        runCatching { get(name)?.asDouble }.getOrNull()

    private fun JsonObject.boolean(name: String): Boolean? =
        runCatching { get(name)?.asBoolean }.getOrNull()

    private fun JsonArray.forEachObject(action: (JsonObject) -> Unit) {
        forEach { if (it.isJsonObject) action(it.asJsonObject) }
    }

    private fun MutableList<QaAssertion>.addOrReplace(assertion: QaAssertion) {
        removeIf { it.name == assertion.name }
        add(assertion)
    }

    private fun Skills.getSkillForNameStub(name: String): Int =
        when (name.lowercase(Locale.ROOT).replace(" ", "_")) {
            "attack" -> Skills.ATTACK
            "defence", "defense" -> Skills.DEFENCE
            "strength" -> Skills.STRENGTH
            "hitpoints", "constitution" -> Skills.HITPOINTS
            "ranged" -> Skills.RANGED
            "prayer" -> Skills.PRAYER
            "magic" -> Skills.MAGIC
            "cooking" -> Skills.COOKING
            "woodcutting" -> Skills.WOODCUTTING
            "fletching" -> Skills.FLETCHING
            "fishing" -> Skills.FISHING
            "firemaking" -> Skills.FIREMAKING
            "crafting" -> Skills.CRAFTING
            "smithing" -> Skills.SMITHING
            "mining" -> Skills.MINING
            "herblore" -> Skills.HERBLORE
            "agility" -> Skills.AGILITY
            "thieving" -> Skills.THIEVING
            "slayer" -> Skills.SLAYER
            "farming" -> Skills.FARMING
            "runecrafting", "rune_crafting" -> Skills.RUNECRAFTING
            "hunter" -> Skills.HUNTER
            "construction" -> Skills.CONSTRUCTION
            else -> -1
        }
}
