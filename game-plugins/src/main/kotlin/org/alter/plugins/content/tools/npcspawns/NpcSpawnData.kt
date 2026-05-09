package org.alter.plugins.content.tools.npcspawns

import org.alter.game.model.Direction
import org.alter.game.model.Tile

data class NpcSpawnEntry(
    var key: String = "",
    var npcId: Int = -1,
    var name: String = "",
    var x: Int = 0,
    var z: Int = 0,
    var height: Int = 0,
    var walkRadius: Int = 0,
    var facing: String = Direction.SOUTH.name,
    var active: Boolean = true,
    var enabled: Boolean = true,
    var aggressive: Boolean? = null,
    var aggressionRadius: Int? = null,
    var followRange: Int? = null,
    var shopKey: String? = null,
    var tags: MutableList<String>? = mutableListOf(),
    var notes: String? = null,
) {
    fun tile(): Tile = Tile(x, z, height)

    fun copyForEdit(): NpcSpawnEntry =
        copy(
            tags = tags.orEmpty().toMutableList(),
        )
}

data class NpcSearchResult(
    val id: Int,
    val name: String,
)
