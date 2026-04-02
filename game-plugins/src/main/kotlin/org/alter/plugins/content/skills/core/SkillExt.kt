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
        val amount = if (product.min == product.max) product.min else random(product.min..product.max)
        return SkillStack(product.item, amount)
    }

    val totalWeight = products.sumOf { it.weight }
    val roll = randomDouble() * totalWeight
    var cumulative = 0.0
    products.forEach { product ->
        cumulative += product.weight
        if (roll <= cumulative) {
            val amount = if (product.min == product.max) product.min else random(product.min..product.max)
            return SkillStack(product.item, amount)
        }
    }

    val fallback = products.last()
    val amount = if (fallback.min == fallback.max) fallback.min else random(fallback.min..fallback.max)
    return SkillStack(fallback.item, amount)
}

fun World.replaceWithRespawn(obj: GameObject, replacementId: Int, respawnTicks: Int) {
    val replacement = DynamicObject(id = replacementId, type = obj.type, rot = obj.rot, tile = obj.tile)
    remove(obj)
    spawn(replacement)
    queue {
        wait(respawnTicks)
        if (isSpawned(replacement)) {
            remove(replacement)
        }
        spawn(DynamicObject(id = obj.id, type = obj.type, rot = obj.rot, tile = obj.tile))
    }
}
