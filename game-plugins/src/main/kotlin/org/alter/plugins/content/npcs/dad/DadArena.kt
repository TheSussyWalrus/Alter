package org.alter.plugins.content.npcs.dad

import org.alter.game.model.Direction
import org.alter.game.model.Tile
import org.alter.game.model.World
import org.alter.game.model.entity.Player

internal object DadArena {
    const val REGION_ID = 11576
    const val SAFE_RADIUS = 5

    val CAVE_ENTRANCE_TILE = Tile(2536, 3090, 0)
    val GATE_ENTRANCE_TILE = Tile(2891, 3618, 0)
    val CENTER = Tile(2907, 3609, 0)
    val DAD_SPAWN_TILE = Tile(2908, 3609, 0)
    val ENTRANCE_TILES = arrayOf(Tile(2897, 3618, 0), Tile(2897, 3619, 0))

    val THROWER_SPAWNS =
        listOf(
            ThrowerSpawn("npc.thrower_troll_4135", Tile(2901, 3604, 0), Direction.EAST),
            ThrowerSpawn("npc.thrower_troll_4136", Tile(2901, 3609, 0), Direction.EAST),
            ThrowerSpawn("npc.thrower_troll_4137", Tile(2901, 3614, 0), Direction.EAST),
            ThrowerSpawn("npc.thrower_troll_4138", Tile(2905, 3624, 0), Direction.SOUTH),
            ThrowerSpawn("npc.thrower_troll_4139", Tile(2911, 3625, 0), Direction.SOUTH),
            ThrowerSpawn("npc.thrower_troll_4135", Tile(2917, 3624, 0), Direction.SOUTH),
            ThrowerSpawn("npc.thrower_troll_4136", Tile(2922, 3613, 0), Direction.WEST),
            ThrowerSpawn("npc.thrower_troll_4137", Tile(2923, 3608, 0), Direction.WEST),
            ThrowerSpawn("npc.thrower_troll_4138", Tile(2918, 3601, 0), Direction.NORTH),
            ThrowerSpawn("npc.thrower_troll_4139", Tile(2908, 3599, 0), Direction.NORTH),
        )

    private val SWAT_DESTINATIONS =
        listOf(
            Tile(2900, 3605, 0),
            Tile(2904, 3603, 0),
            Tile(2910, 3603, 0),
            Tile(2915, 3605, 0),
            Tile(2916, 3609, 0),
            Tile(2915, 3613, 0),
            Tile(2911, 3615, 0),
            Tile(2904, 3615, 0),
            Tile(2899, 3612, 0),
            Tile(2899, 3607, 0),
        )

    private const val MIN_X = 2898
    private const val MAX_X = 2917
    private const val MIN_Z = 3602
    private const val MAX_Z = 3616

    fun contains(tile: Tile): Boolean =
        tile.height == CENTER.height &&
            tile.x in MIN_X..MAX_X &&
            tile.z in MIN_Z..MAX_Z

    fun isInSafeCenter(tile: Tile): Boolean = contains(tile) && tile.getDistance(CENTER) <= SAFE_RADIUS

    fun activePlayers(world: World): List<Player> {
        val players = mutableListOf<Player>()
        world.players.forEach { player ->
            if (player.isOnline && player.isAlive() && contains(player.tile)) {
                players.add(player)
            }
        }
        return players
    }

    fun closestThrower(tile: Tile): ThrowerSpawn =
        THROWER_SPAWNS.minByOrNull { it.tile.getDistance(tile) } ?: THROWER_SPAWNS.first()

    fun swatDestination(tile: Tile): Tile {
        val dx = tile.x - CENTER.x
        val dz = tile.z - CENTER.z
        if (dx == 0 && dz == 0) {
            return SWAT_DESTINATIONS.random()
        }
        return SWAT_DESTINATIONS.maxByOrNull { destination ->
            val destinationDx = destination.x - CENTER.x
            val destinationDz = destination.z - CENTER.z
            destinationDx * dx + destinationDz * dz
        } ?: SWAT_DESTINATIONS.first()
    }

    data class ThrowerSpawn(
        val npc: String,
        val tile: Tile,
        val direction: Direction,
    )
}
