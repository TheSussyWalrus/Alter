package org.alter.plugins.content.skills.core

import dev.openrune.cache.CacheManager.getItem
import org.alter.api.ext.hasEquipped
import org.alter.api.ext.message
import org.alter.game.model.World
import org.alter.game.model.entity.DynamicObject
import org.alter.game.model.entity.GameObject
import org.alter.game.model.entity.GroundItem
import org.alter.game.model.entity.Player
import org.alter.rscm.RSCM.getRSCM

fun Player.findBestTool(tools: List<SkillTool>): SkillTool? =
    tools
        .sortedByDescending { it.level }
        .firstOrNull { inventory.contains(it.item) || hasEquipped(arrayOf(it.item)) }

fun Player.hasItems(inputs: List<SkillStack>): Boolean = inputs.all { inventory.getItemCount(getRSCM(it.item)) >= it.amount }

fun Player.removeInputs(inputs: List<SkillStack>) {
    inputs.forEach { input ->
        repeat(input.amount) {
            inventory.remove(getRSCM(input.item))
        }
    }
}

fun Player.canReceive(item: String, amount: Int = 1): Boolean {
    if (!inventory.isFull) {
        return true
    }
    val itemId = getRSCM(item)
    val def = getItem(itemId)
    return def.stackable && inventory.getItemCount(itemId) > 0
}

fun Player.addOrDrop(item: String, amount: Int, messageOnDrop: String? = null) {
    val transaction = inventory.add(item = item, amount = amount)
    if (transaction.hasFailed()) {
        world.spawn(GroundItem(item = getRSCM(item), amount = amount, tile = tile, owner = this))
        messageOnDrop?.let { message(it) }
    }
}

fun World.roll(products: List<WeightedSkillStack>): SkillStack {
    if (products.size == 1) {
        val product = products.first()
        val amount = if (product.safeMin == product.safeMax) product.safeMin else random(product.safeMin..product.safeMax)
        return SkillStack(product.item, amount)
    }

    val totalWeight = products.sumOf { it.weight }
    val roll = randomDouble() * totalWeight
    var cumulative = 0.0
    products.forEach { product ->
        cumulative += product.weight
        if (roll <= cumulative) {
            val amount = if (product.safeMin == product.safeMax) product.safeMin else random(product.safeMin..product.safeMax)
            return SkillStack(product.item, amount)
        }
    }

    val fallback = products.last()
    val amount = if (fallback.safeMin == fallback.safeMax) fallback.safeMin else random(fallback.safeMin..fallback.safeMax)
    return SkillStack(fallback.item, amount)
}

fun World.replaceWithRespawn(obj: GameObject, replacementId: Int, respawnTicks: Int) {
    val current = getObject(obj.tile, obj.type) ?: obj
    val replacement = DynamicObject(id = replacementId, type = current.type, rot = current.rot, tile = current.tile)
    if (isSpawned(current)) {
        remove(current)
    }
    spawn(replacement)
    queue {
        wait(respawnTicks)
        if (isSpawned(replacement)) {
            remove(replacement)
        }
        spawn(DynamicObject(id = current.id, type = current.type, rot = current.rot, tile = current.tile))
    }
}

fun World.getLiveResourceObject(obj: GameObject): GameObject? =
    getObject(obj.tile, obj.type)?.takeIf { it.id == obj.id }
