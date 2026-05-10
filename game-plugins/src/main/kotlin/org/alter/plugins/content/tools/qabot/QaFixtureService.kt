package org.alter.plugins.content.tools.qabot

import org.alter.api.Skills
import org.alter.game.model.Tile
import org.alter.game.model.World
import org.alter.game.model.entity.DynamicObject
import org.alter.game.model.entity.GameObject
import org.alter.game.model.entity.Npc
import org.alter.game.model.move.moveTo
import org.alter.game.model.move.stopMovement
import org.alter.rscm.RSCM.getRSCM
import com.google.gson.JsonObject
import java.util.Locale

class QaFixtureService {
    private val tempNpcs: MutableList<Npc> = mutableListOf()
    private val tempObjects: MutableList<GameObject> = mutableListOf()
    private var lastCleanup: QaCleanupResult = QaCleanupResult()

    fun status(): JsonObject =
        JsonObject().apply {
            addProperty("tempNpcs", tempNpcs.size)
            addProperty("tempObjects", tempObjects.size)
            addProperty("lastRemovedNpcs", lastCleanup.removedNpcs)
            addProperty("lastRemovedObjects", lastCleanup.removedObjects)
            addProperty("lastClearedInventory", lastCleanup.clearedInventory)
        }

    fun applyFixture(
        world: World,
        bot: QaPlayer,
        fixture: QaFixture,
        result: QaJourneyResult? = null,
    ) {
        fixture.origin?.let { origin ->
            bot.moveTo(Tile(origin.x, origin.z, origin.height))
            result?.observations?.add("Moved ${bot.username} to fixture origin ${origin.x},${origin.z},${origin.height}.")
        }

        if (fixture.inventory.isNotEmpty()) {
            bot.inventory.removeAll()
            fixture.inventory.forEach { item ->
                val itemId = item.itemId ?: item.item?.let(::rscmOrNull)
                if (itemId == null) {
                    result?.observations?.add("Could not resolve fixture item '${item.item}'.")
                    return@forEach
                }
                bot.inventory.add(itemId, item.amount.coerceAtLeast(1), assureFullInsertion = false)
            }
            result?.observations?.add("Prepared ${fixture.inventory.size} fixture inventory item(s).")
        }

        fixture.skills.forEach { (name, level) ->
            val skill = skillId(name)
            if (skill == -1) {
                result?.observations?.add("Unknown fixture skill '$name'.")
            } else {
                bot.getSkills().setBaseLevel(skill, level.coerceIn(1, 99))
            }
        }

        fixture.tempNpcs.forEach { npcFixture ->
            val npcId = npcFixture.npcId ?: npcFixture.npc?.let(::rscmOrNull)
            if (npcId == null) {
                result?.observations?.add("Could not resolve fixture npc '${npcFixture.npc}'.")
                return@forEach
            }
            val npc = Npc(bot, npcId, Tile(npcFixture.x, npcFixture.z, npcFixture.height), world)
            npc.walkRadius = npcFixture.walkRadius.coerceAtLeast(0)
            npc.respawns = false
            npc.setActive(npcFixture.active)
            if (world.spawn(npc)) {
                tempNpcs.add(npc)
            }
        }
        if (fixture.tempNpcs.isNotEmpty()) {
            result?.observations?.add("Spawned ${fixture.tempNpcs.size} temporary npc fixture(s).")
        }

        fixture.tempObjects.forEach { objFixture ->
            val objectId = objFixture.objectId ?: objFixture.obj?.let(::rscmOrNull)
            if (objectId == null) {
                result?.observations?.add("Could not resolve fixture object '${objFixture.obj}'.")
                return@forEach
            }
            val obj =
                DynamicObject(
                    objectId,
                    objFixture.type.coerceAtLeast(0),
                    objFixture.rot.coerceIn(0, 3),
                    Tile(objFixture.x, objFixture.z, objFixture.height),
                )
            world.spawn(obj)
            tempObjects.add(obj)
        }
        if (fixture.tempObjects.isNotEmpty()) {
            result?.observations?.add("Spawned ${fixture.tempObjects.size} temporary object fixture(s).")
        }
    }

    fun cleanupJourney(
        world: World,
        bot: QaPlayer?,
        clearInventory: Boolean = true,
    ): QaCleanupResult {
        val cleanup = QaCleanupResult()
        tempNpcs.toList().forEach { npc ->
            runCatching {
                if (npc.index != -1) {
                    world.remove(npc)
                    cleanup.removedNpcs++
                }
            }.onFailure { cleanup.warnings.add(it.message ?: "Failed to remove temporary NPC.") }
        }
        tempNpcs.clear()

        tempObjects.toList().forEach { obj ->
            runCatching {
                if (obj.isSpawned(world)) {
                    world.remove(obj)
                    cleanup.removedObjects++
                }
            }.onFailure { cleanup.warnings.add(it.message ?: "Failed to remove temporary object.") }
        }
        tempObjects.clear()

        bot?.let { player ->
            player.interruptQueues()
            player.stopMovement()
            player.resetInteractions()
            cleanup.interruptedQueues = true
            if (clearInventory) {
                player.inventory.removeAll()
                cleanup.clearedInventory = true
            }
        }
        lastCleanup = cleanup
        return cleanup
    }

    private fun rscmOrNull(name: String): Int? =
        runCatching { getRSCM(name) }.getOrNull().takeIf { it != null && it >= 0 }

    private fun skillId(name: String): Int =
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
            "runecrafting" -> Skills.RUNECRAFTING
            else -> -1
        }
}
