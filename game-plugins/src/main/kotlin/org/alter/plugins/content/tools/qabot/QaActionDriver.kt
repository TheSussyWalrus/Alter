package org.alter.plugins.content.tools.qabot

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import dev.openrune.cache.CacheManager.getNpc
import dev.openrune.cache.CacheManager.getObject
import net.rsprot.protocol.game.incoming.resumed.ResumePauseButton
import net.rsprot.protocol.util.CombinedId
import org.alter.api.Skills
import org.alter.game.model.EntityType
import org.alter.game.model.Tile
import org.alter.game.model.attr.CURRENT_SHOP_ATTR
import org.alter.game.model.attr.INTERACTING_GROUNDITEM_ATTR
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
import org.alter.game.model.entity.GroundItem
import org.alter.game.model.entity.Npc
import org.alter.game.model.move.GroundItemRouteAction
import org.alter.game.model.move.ObjectPathAction
import org.alter.game.model.move.PawnPathAction
import org.alter.game.model.move.walkTo
import org.alter.game.model.move.moveTo
import org.alter.game.model.queue.QueueTask
import org.alter.game.model.queue.QueueTaskSet
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
        try {
            applySetup(player, step.setup, result)
            val before = player.snapshot()
            player.lastSkillMenuItems.clear()
            val actionStarted = performAction(player, step, result)
            val selectedRecipe = step.action.string("selectRecipe")
            var recipeSelectionSent = false
            val timeout = (step.timeoutTicks ?: 50).coerceAtLeast(1)
            var passed = false
            var waited = 0
            while (waited < timeout && !passed) {
                task.wait(1)
                waited++
                if (!recipeSelectionSent && selectedRecipe != null) {
                    recipeSelectionSent = submitRecipeSelection(player, selectedRecipe, result)
                }
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
            result.observations.add("Moved ${player.username} to ${tile.x},${tile.z},${tile.height}.")
        }

        setup.array("inventory")?.let { inventory ->
            player.inventory.removeAll()
            inventory.forEachObject { item ->
                val itemName = item.string("item")
                val amount = item.int("amount") ?: 1
                val itemId = item.int("itemId") ?: itemName?.let(::rscmOrNull)
                if (itemId == null) {
                    result.observations.add("Could not resolve setup item '$itemName'.")
                } else {
                    player.inventory.add(itemId, amount, assureFullInsertion = false)
                }
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

        step.action.obj("walkTo")?.let { action ->
            return walkToTile(player, action, result)
        }
        step.action.obj("attackNpc")?.let { action ->
            val target = findTargetNpc(player, step.setup.obj("target") ?: action.obj("target"), result) ?: return false
            return attackNpc(player, target, result)
        }
        step.action.obj("pickupGroundItem")?.let { action ->
            val groundItem = findGroundItem(player, action, result) ?: return false
            return pickupGroundItem(player, groundItem, result)
        }
        step.action.obj("buyShopItem")?.let { action ->
            return buyShopItem(player, action, result)
        }
        step.action.obj("sellInventoryItem")?.let { action ->
            return sellInventoryItem(player, action, result)
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
            val exists = pathExists(path)
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
        player.executePlugin(ObjectPathAction.objectInteractPlugin)
        result.assertions.add(QaAssertion("object-route:${obj.id}:$opt", true, "queued", "queued"))
        return true
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
        if (option.normalizeOption() == "attack") {
            player.attack(npc)
            result.assertions.add(QaAssertion("npc-attack:${npc.id}", true, "started", "started"))
        } else {
            player.executePlugin(PawnPathAction.walkPlugin)
            result.assertions.add(QaAssertion("npc-route:${npc.id}:$opt", true, "queued", "queued"))
        }
        return true
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
        player.executePlugin(ObjectPathAction.itemOnObjectPlugin)
        result.assertions.add(QaAssertion("item-on-object-route:$itemId:${obj.id}", true, "queued", "queued"))
        return true
    }

    private fun walkToTile(
        player: QaPlayer,
        action: JsonObject,
        result: QaStepResult,
    ): Boolean {
        val x = action.int("x") ?: return false
        val z = action.int("z") ?: return false
        val height = action.int("height") ?: player.tile.height
        if (height != player.tile.height) {
            player.moveTo(Tile(player.tile.x, player.tile.z, height))
        }
        player.walkTo(Tile(x, z, height))
        result.observations.add("Queued walk to $x,$z,$height.")
        result.assertions.add(QaAssertion("walk-to:$x:$z:$height", true, "queued", "queued"))
        return true
    }

    private fun attackNpc(
        player: QaPlayer,
        npc: Npc,
        result: QaStepResult,
    ): Boolean {
        player.attack(npc)
        result.observations.add("Started combat with NPC ${npc.id} at ${npc.tile.x},${npc.tile.z}.")
        result.assertions.add(QaAssertion("combat-start:${npc.id}", true, "started", "started"))
        return true
    }

    private fun pickupGroundItem(
        player: QaPlayer,
        groundItem: GroundItem,
        result: QaStepResult,
    ): Boolean {
        player.attr[INTERACTING_OPT_ATTR] = 3
        player.attr[INTERACTING_GROUNDITEM_ATTR] = WeakReference(groundItem)
        player.executePlugin(GroundItemRouteAction.walkPlugin)
        result.observations.add("Queued pickup for ground item ${groundItem.item} at ${groundItem.tile.x},${groundItem.tile.z}.")
        result.assertions.add(QaAssertion("ground-item-route:${groundItem.item}", true, "queued", "queued"))
        return true
    }

    private fun buyShopItem(
        player: QaPlayer,
        action: JsonObject,
        result: QaStepResult,
    ): Boolean {
        val itemName = action.string("item")
        val itemId = action.int("itemId") ?: itemName?.let(::rscmOrNull) ?: return false
        val amount = action.int("amount") ?: 1
        val shop = player.attr[CURRENT_SHOP_ATTR]
        if (shop == null) {
            result.observations.add("No shop is currently open.")
            return false
        }
        val slot = shop.items.indexOfFirst { it?.item == itemId }
        if (slot == -1) {
            result.observations.add("Open shop '${shop.name}' does not stock item $itemId.")
            return false
        }
        player.attr[INTERACTING_OPT_ATTR] = shopAmountOption(amount)
        player.attr[INTERACTING_SLOT_ATTR] = slot + 1
        val handled = player.world.plugins.executeButton(player, SHOP_INTERFACE_ID, SHOP_ITEMS_COMPONENT)
        result.assertions.add(QaAssertion("shop-buy:$itemId", handled, "handled", handled.toString()))
        return handled
    }

    private fun sellInventoryItem(
        player: QaPlayer,
        action: JsonObject,
        result: QaStepResult,
    ): Boolean {
        val itemName = action.string("item")
        val itemId = action.int("itemId") ?: itemName?.let(::rscmOrNull) ?: return false
        val amount = action.int("amount") ?: 1
        if (player.attr[CURRENT_SHOP_ATTR] == null) {
            result.observations.add("No shop is currently open.")
            return false
        }
        val slot = player.inventory.getItemIndex(itemId, skipAttrItems = false)
        if (slot == -1) {
            result.observations.add("Inventory does not contain sell item $itemId.")
            return false
        }
        player.attr[INTERACTING_OPT_ATTR] = shopAmountOption(amount)
        player.attr[INTERACTING_SLOT_ATTR] = slot
        val handled = player.world.plugins.executeButton(player, SHOP_INVENTORY_INTERFACE_ID, SHOP_INVENTORY_COMPONENT)
        result.assertions.add(QaAssertion("shop-sell:$itemId", handled, "handled", handled.toString()))
        return handled
    }

    private fun submitRecipeSelection(
        player: QaPlayer,
        recipeName: String,
        result: QaStepResult,
    ): Boolean {
        val productId = rscmOrNull(recipeName) ?: return missingRscm(recipeName, result)
        val index = player.lastSkillMenuItems.indexOf(productId)
        if (index == -1) {
            return false
        }
        val message = ResumePauseButton(CombinedId(270, 14 + index), 1)
        val submitted = submitPlayerReturnValue(player, message)
        if (submitted) {
            result.observations.add("Selected recipe $recipeName from skill menu.")
        } else {
            result.observations.add("Could not submit recipe selection for $recipeName.")
        }
        return submitted
    }

    private fun submitPlayerReturnValue(
        player: QaPlayer,
        value: Any,
    ): Boolean {
        val field =
            generateSequence(player.javaClass as Class<*>?) { it.superclass }
                .mapNotNull { clazz -> runCatching { clazz.getDeclaredField("queues") }.getOrNull() }
                .firstOrNull() ?: return false
        field.isAccessible = true
        val queues = field.get(player) as? QueueTaskSet ?: return false
        queues.submitReturnValue(value)
        return true
    }

    private fun evaluateExpectations(
        player: QaPlayer,
        step: QaScenarioStep,
        before: QaSnapshot,
        after: QaSnapshot,
        result: QaStepResult,
    ): Boolean {
        if (step.type == "missing-content-probe") {
            val refsMissing = step.setup.array("configRefs")?.all { !pathExists(it.asString) } ?: true
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
        step.expect.obj("tile")?.let { expected ->
            val x = expected.int("x")
            val z = expected.int("z")
            val height = expected.int("height") ?: after.tile.height
            val radius = expected.int("radius") ?: 0
            if (x != null && z != null) {
                val within =
                    after.tile.height == height &&
                        kotlin.math.abs(after.tile.x - x) <= radius &&
                        kotlin.math.abs(after.tile.z - z) <= radius
                assertions.add(QaAssertion("tile", within, "$x,$z,$height radius $radius", "${after.tile.x},${after.tile.z},${after.tile.height}"))
            }
        }
        step.expect.obj("shopOpen")?.let { expected ->
            val nameContains = expected.string("nameContains")
            val expectedOpen = expected.boolean("open") ?: true
            val currentShop = after.currentShop.orEmpty()
            val passed =
                if (!expectedOpen) {
                    currentShop.isBlank()
                } else if (nameContains != null) {
                    currentShop.contains(nameContains, ignoreCase = true)
                } else {
                    currentShop.isNotBlank()
            }
            assertions.add(QaAssertion("shop-open", passed, nameContains ?: expectedOpen.toString(), currentShop.ifBlank { "none" }))
        }
        step.expect.array("objectsAbsent")?.forEachObject { expected ->
            val ids = expected.resolveIds("objects", "objectIds")
            val x = expected.int("x") ?: after.tile.x
            val z = expected.int("z") ?: after.tile.z
            val height = expected.int("height") ?: after.tile.height
            val radius = expected.int("radius") ?: 0
            val found = findAnyObject(player, ids, Tile(x, z, height), radius)
            assertions.add(
                QaAssertion(
                    "objects-absent:${ids.joinToString("|")}:$x:$z:$height",
                    found == null,
                    "absent within radius $radius",
                    found?.let { "${it.id}@${it.tile.x},${it.tile.z},${it.tile.height}" } ?: "absent",
                ),
            )
        }
        step.expect.array("objectsPresent")?.forEachObject { expected ->
            val ids = expected.resolveIds("objects", "objectIds")
            val x = expected.int("x") ?: after.tile.x
            val z = expected.int("z") ?: after.tile.z
            val height = expected.int("height") ?: after.tile.height
            val radius = expected.int("radius") ?: 0
            val found = findAnyObject(player, ids, Tile(x, z, height), radius)
            assertions.add(
                QaAssertion(
                    "objects-present:${ids.joinToString("|")}:$x:$z:$height",
                    found != null,
                    "present within radius $radius",
                    found?.let { "${it.id}@${it.tile.x},${it.tile.z},${it.tile.height}" } ?: "missing",
                ),
            )
        }
        step.expect.boolean("configLoaded")?.let {
            val allExist = step.setup.array("configRefs")?.all { ref -> pathExists(ref.asString) } ?: false
            assertions.add(QaAssertion("config-loaded", allExist == it, it.toString(), allExist.toString()))
        }
        if (step.expect.has("worldAvailable")) {
            assertions.add(QaAssertion("world-available", true, "true", "true"))
        }
        assertions.forEach { assertion ->
            result.assertions.addOrReplace(assertion)
        }
        val blockingAssertions = assertions.filterNot { it.name.startsWith("messages:") }
        return blockingAssertions.isNotEmpty() && blockingAssertions.all { it.passed }
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
        val ids = target?.resolveIds("objects", "objectIds").orEmpty()
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

    private fun findAnyObject(
        player: QaPlayer,
        ids: List<Int>,
        center: Tile,
        radius: Int,
    ): GameObject? {
        if (ids.isEmpty()) {
            return null
        }
        for (distance in 0..radius) {
            for (dx in -distance..distance) {
                for (dz in -distance..distance) {
                    if (kotlin.math.abs(dx) != distance && kotlin.math.abs(dz) != distance) {
                        continue
                    }
                    val tile = Tile(center.x + dx, center.z + dz, center.height)
                    val chunk = player.world.chunks.get(tile, createIfNeeded = false) ?: continue
                    val obj =
                        chunk.getEntities<GameObject>(tile, EntityType.STATIC_OBJECT, EntityType.DYNAMIC_OBJECT)
                            .firstOrNull { it.id in ids }
                    if (obj != null) {
                        return obj
                    }
                }
            }
        }
        return null
    }

    private fun findTargetNpc(
        player: QaPlayer,
        target: JsonObject?,
        result: QaStepResult,
    ): Npc? {
        val ids = target?.resolveIds("npcs", "npcIds").orEmpty()
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

    private fun findGroundItem(
        player: QaPlayer,
        action: JsonObject,
        result: QaStepResult,
    ): GroundItem? {
        val ids =
            action.resolveIds("items", "itemIds")
                .ifEmpty {
                    listOfNotNull(action.int("itemId") ?: action.string("item")?.let(::rscmOrNull))
                }
        if (ids.isEmpty()) {
            result.observations.add("No ground item ids resolved for pickup.")
            return null
        }
        val radius = action.int("searchRadius") ?: 16
        for (distance in 0..radius) {
            for (dx in -distance..distance) {
                for (dz in -distance..distance) {
                    if (kotlin.math.abs(dx) != distance && kotlin.math.abs(dz) != distance) {
                        continue
                    }
                    val tile = Tile(player.tile.x + dx, player.tile.z + dz, player.tile.height)
                    val chunk = player.world.chunks.get(tile, createIfNeeded = false) ?: continue
                    val item =
                        chunk.getEntities<GroundItem>(tile, EntityType.GROUND_ITEM)
                            .firstOrNull { it.item in ids }
                    if (item != null) {
                        result.observations.add("Found ground item ${item.item} at ${item.tile.x},${item.tile.z}.")
                        return item
                    }
                }
            }
        }
        result.observations.add("No target ground item found within $radius tiles.")
        return null
    }

    private fun QaPlayer.snapshot(): QaSnapshot {
        val inventory = mutableMapOf<Int, Int>()
        this.inventory.rawItems.filterNotNull().forEach { item -> inventory.merge(item.id, item.amount, Int::plus) }
        val xp = mutableMapOf<String, Double>()
        for (skill in 0 until getSkills().maxSkills) {
            val name = Skills.getSkillName(world, skill).lowercase(Locale.ROOT)
            xp[name] = getSkills().getCurrentXp(skill)
        }
        return QaSnapshot(
            inventory = inventory,
            xp = xp,
            hitpoints = getCurrentHp(),
            tile = QaTile.from(tile),
            currentShop = attr[CURRENT_SHOP_ATTR]?.name,
        )
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

    private fun pathExists(path: String): Boolean {
        val direct = Paths.get(path)
        return Files.exists(direct) || Files.exists(Paths.get("..").resolve(direct))
    }

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

    private fun JsonObject.resolveIds(
        nameKey: String,
        idKey: String,
    ): List<Int> {
        val ids = mutableListOf<Int>()
        array(nameKey)?.forEach { element ->
            when {
                element.isJsonPrimitive && element.asJsonPrimitive.isNumber -> ids.add(element.asInt)
                element.isJsonPrimitive -> rscmOrNull(element.asString)?.let(ids::add)
            }
        }
        array(idKey)?.forEach { element ->
            when {
                element.isJsonPrimitive && element.asJsonPrimitive.isNumber -> ids.add(element.asInt)
                element.isJsonPrimitive -> element.asString.toIntOrNull()?.let(ids::add)
            }
        }
        return ids.distinct()
    }

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

    private fun shopAmountOption(amount: Int): Int =
        when {
            amount <= 1 -> 2
            amount <= 5 -> 3
            amount <= 10 -> 4
            else -> 5
        }

    private companion object {
        private const val SHOP_INTERFACE_ID = 300
        private const val SHOP_ITEMS_COMPONENT = 16
        private const val SHOP_INVENTORY_INTERFACE_ID = 301
        private const val SHOP_INVENTORY_COMPONENT = 0
    }
}
