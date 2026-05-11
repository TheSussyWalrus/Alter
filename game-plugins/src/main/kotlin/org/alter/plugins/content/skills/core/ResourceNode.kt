package org.alter.plugins.content.skills.core

import org.alter.game.model.Tile
import org.alter.game.model.World
import org.alter.game.model.entity.DynamicObject
import org.alter.game.model.entity.GameObject

data class ResourceNodeDefinition(
    val objectId: Int,
    val depletedObjectId: Int? = null,
    val type: Int = 10,
    val respawnCycles: Int = 0,
)

data class ResourceNodeState(
    val definition: ResourceNodeDefinition,
    val tile: Tile,
    val rotation: Int = 0,
    var depletedUntilCycle: Int = -1,
) {
    fun isAvailable(currentCycle: Int): Boolean = !isDepleted(currentCycle)

    fun isDepleted(currentCycle: Int): Boolean = depletedUntilCycle >= 0 && currentCycle < depletedUntilCycle

    fun markDepleted(currentCycle: Int) {
        depletedUntilCycle =
            if (definition.respawnCycles > 0) {
                currentCycle + definition.respawnCycles
            } else {
                Int.MAX_VALUE
            }
    }

    fun markAvailable() {
        depletedUntilCycle = -1
    }

    fun currentObjectId(currentCycle: Int): Int =
        if (isDepleted(currentCycle)) {
            definition.depletedObjectId ?: definition.objectId
        } else {
            definition.objectId
        }
}

fun World.spawnResourceNode(node: ResourceNodeState) {
    spawn(DynamicObject(node.definition.objectId, node.definition.type, node.rotation, node.tile))
}

fun World.depleteResourceNode(node: ResourceNodeState) {
    val existing = getObject(node.tile, node.definition.type)
    if (existing != null) {
        remove(existing)
    }

    node.markDepleted(currentCycle)
    node.definition.depletedObjectId?.let { depletedObjectId ->
        spawn(DynamicObject(depletedObjectId, node.definition.type, node.rotation, node.tile))
    }
}

fun World.restoreResourceNode(node: ResourceNodeState) {
    val existing = getObject(node.tile, node.definition.type)
    if (existing != null) {
        remove(existing)
    }

    node.markAvailable()
    spawn(DynamicObject(node.definition.objectId, node.definition.type, node.rotation, node.tile))
}

fun World.replaceResourceNode(
    node: ResourceNodeState,
    objectId: Int,
) {
    val existing = getObject(node.tile, node.definition.type)
    if (existing != null) {
        remove(existing)
    }
    spawn(DynamicObject(objectId, node.definition.type, node.rotation, node.tile))
}

fun ResourceNodeState.asObject(currentCycle: Int): GameObject =
    DynamicObject(currentObjectId(currentCycle), definition.type, rotation, tile)

