package org.alter.plugins.content.tools.npcspawns

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import dev.openrune.cache.CacheManager.getNpc
import dev.openrune.cache.CacheManager.npcSize
import gg.rsmod.util.ServerProperties
import org.alter.game.Server
import org.alter.game.model.Direction
import org.alter.game.model.Tile
import org.alter.game.model.World
import org.alter.game.model.entity.Npc
import org.alter.game.service.Service
import org.alter.plugins.content.tools.npcdefs.NpcDefinitionService
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.Locale

class NpcSpawnService : Service {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val managedNpcs: MutableMap<String, Npc> = mutableMapOf()

    lateinit var configPath: Path
        private set

    val entries: MutableList<NpcSpawnEntry> = mutableListOf()
    var dirty: Boolean = false
        private set

    override fun init(server: Server, world: World, serviceProperties: ServerProperties) {
        configPath = resolveConfigPath(serviceProperties.get("npc.spawns") ?: DEFAULT_CONFIG)
        loadFromDisk()
        Server.logger.info { "Loaded ${entries.size} NPC spawn definition${if (entries.size == 1) "" else "s"}." }
    }

    override fun postLoad(server: Server, world: World) {
        spawnAll(world)
    }

    override fun terminate(server: Server, world: World) {
        removeManaged(world)
    }

    fun loadFromDisk() {
        entries.clear()
        if (!Files.exists(configPath)) {
            Files.createDirectories(configPath.parent)
            dirty = false
            return
        }

        Files.newBufferedReader(configPath).use { reader ->
            val listType = object : TypeToken<MutableList<NpcSpawnEntry>>() {}.type
            val loaded = gson.fromJson<MutableList<NpcSpawnEntry>>(reader, listType) ?: mutableListOf()
            loaded.forEach { entry ->
                val normalized = normalize(entry)
                if (normalized.key.isBlank() || entries.any { it.key == normalized.key }) {
                    normalized.key = generateKey(normalized)
                }
                entries.add(normalized)
            }
            entries.sortWith(compareBy<NpcSpawnEntry> { it.key }.thenBy { it.npcId })
        }
        dirty = false
    }

    fun reload(world: World) {
        removeManaged(world)
        loadFromDisk()
        spawnAll(world)
    }

    fun saveToDisk(): Path {
        Files.createDirectories(configPath.parent)
        val sorted = entries.map { normalize(it.copyForEdit()) }.sortedWith(compareBy<NpcSpawnEntry> { it.key }.thenBy { it.npcId })
        val temp = configPath.resolveSibling("${configPath.fileName}.tmp")
        Files.writeString(temp, gson.toJson(sorted))
        try {
            Files.move(temp, configPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp, configPath, StandardCopyOption.REPLACE_EXISTING)
        }
        entries.clear()
        entries.addAll(sorted)
        dirty = false
        return configPath
    }

    fun upsert(entry: NpcSpawnEntry, world: World): NpcSpawnEntry {
        val normalized = normalize(entry)
        if (normalized.key.isBlank()) {
            normalized.key = generateKey(normalized)
        }

        val index = entries.indexOfFirst { it.key == normalized.key }
        if (index >= 0) {
            entries[index] = normalized
        } else {
            entries.add(normalized)
        }

        entries.sortWith(compareBy<NpcSpawnEntry> { it.key }.thenBy { it.npcId })
        dirty = true
        spawnEntry(world, normalized)
        return normalized
    }

    fun delete(key: String, world: World): Boolean {
        removeManaged(world, key)
        val removed = entries.removeIf { it.key == key }
        if (removed) {
            dirty = true
        }
        return removed
    }

    fun duplicateAt(entry: NpcSpawnEntry, tile: Tile, world: World): NpcSpawnEntry {
        val copy =
            entry.copyForEdit().apply {
                key = ""
                x = tile.x
                z = tile.z
                height = tile.height
            }
        return upsert(copy, world)
    }

    fun moveTo(entry: NpcSpawnEntry, tile: Tile, world: World): NpcSpawnEntry {
        entry.x = tile.x
        entry.z = tile.z
        entry.height = tile.height
        return upsert(entry, world)
    }

    fun nearest(tile: Tile, radius: Int = 64): NpcSpawnEntry? =
        entries
            .filter { it.height == tile.height && it.tile().getDistance(tile) <= radius }
            .minByOrNull { it.tile().getDistance(tile) }

    fun nearby(tile: Tile, radius: Int = 64): List<NpcSpawnEntry> =
        entries
            .filter { it.height == tile.height && it.tile().getDistance(tile) <= radius }
            .sortedBy { it.tile().getDistance(tile) }

    fun spawnAll(world: World) {
        entries.forEach { spawnEntry(world, it) }
    }

    fun spawnEntry(world: World, entry: NpcSpawnEntry): Npc? {
        removeManaged(world, entry.key)
        val normalized = normalize(entry)
        if (normalized.key.isBlank()) {
            normalized.key = generateKey(normalized)
        }
        if (!normalized.enabled) {
            return null
        }
        if (!isValidNpc(normalized.npcId)) {
            Server.logger.warn { "Skipping invalid NPC spawn '${normalized.key}' with npcId=${normalized.npcId}." }
            return null
        }

        val npc = Npc(normalized.npcId, normalized.tile(), world)
        if (!world.spawn(npc)) {
            Server.logger.warn { "Failed to spawn managed NPC '${normalized.key}' at ${normalized.x},${normalized.z},${normalized.height}." }
            return null
        }
        applySpawnState(npc, normalized)
        if (world.getService(NpcDefinitionService::class.java)?.applySpawnOverrides(npc, normalized) == true) {
            world.plugins.executeNpcSpawn(npc)
        }
        managedNpcs[normalized.key] = npc
        return npc
    }

    fun applySpawnState(npc: Npc, entry: NpcSpawnEntry) {
        npc.walkRadius = entry.walkRadius.coerceAtLeast(0)
        npc.lastFacingDirection = parseDirection(entry.facing)
        npc.setActive(entry.active)
    }

    fun removeManaged(world: World) {
        managedNpcs.keys.toList().forEach { removeManaged(world, it) }
    }

    fun removeManaged(world: World, key: String) {
        val npc = managedNpcs.remove(key) ?: return
        if (npc.isSpawned()) {
            world.remove(npc)
        }
    }

    fun isValidNpc(id: Int): Boolean = npcName(id) != null

    fun npcName(id: Int): String? {
        if (id < 0 || id >= npcSize()) {
            return null
        }
        return runCatching { getNpc(id).name }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    fun searchNpcs(query: String, limit: Int = 60): List<NpcSearchResult> {
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        if (normalizedQuery.isBlank()) {
            return emptyList()
        }

        return (0 until npcSize())
            .asSequence()
            .mapNotNull { id ->
                val name = npcName(id) ?: return@mapNotNull null
                if (name.lowercase(Locale.ROOT).contains(normalizedQuery)) NpcSearchResult(id, name) else null
            }
            .take(limit)
            .toList()
    }

    fun normalize(entry: NpcSpawnEntry): NpcSpawnEntry {
        entry.name = npcName(entry.npcId) ?: entry.name.ifBlank { "Unknown NPC" }
        entry.height = entry.height.coerceIn(0, 3)
        entry.walkRadius = entry.walkRadius.coerceAtLeast(0)
        entry.aggressionRadius = entry.aggressionRadius?.coerceAtLeast(0)
        entry.followRange = entry.followRange?.coerceAtLeast(0)
        entry.shopKey = entry.shopKey?.trim()?.takeIf { it.isNotBlank() }
        entry.facing = parseDirection(entry.facing).name
        entry.tags =
            entry.tags
                .orEmpty()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .toMutableList()
        entry.notes = entry.notes?.trim()?.takeIf { it.isNotBlank() }
        return entry
    }

    fun generateKey(entry: NpcSpawnEntry): String {
        val namePart =
            entry.name
                .lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
                .take(32)
                .ifBlank { "npc" }
        val base = "${namePart}_${entry.npcId}_${entry.x}_${entry.z}_${entry.height}"
        var key = base
        var suffix = 2
        while (entries.any { it.key == key }) {
            key = "${base}_$suffix"
            suffix++
        }
        return key
    }

    private fun resolveConfigPath(rawPath: String): Path {
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

    companion object {
        private const val DEFAULT_CONFIG = "data/cfg/spawns/npc_spawns.json"

        fun parseDirection(value: String): Direction {
            val normalized = value.trim().uppercase(Locale.ROOT).replace("-", "_").replace(" ", "_")
            return when (normalized) {
                "N", "NORTH" -> Direction.NORTH
                "NE", "NORTH_EAST", "NORTHEAST" -> Direction.NORTH_EAST
                "E", "EAST" -> Direction.EAST
                "SE", "SOUTH_EAST", "SOUTHEAST" -> Direction.SOUTH_EAST
                "S", "SOUTH" -> Direction.SOUTH
                "SW", "SOUTH_WEST", "SOUTHWEST" -> Direction.SOUTH_WEST
                "W", "WEST" -> Direction.WEST
                "NW", "NORTH_WEST", "NORTHWEST" -> Direction.NORTH_WEST
                else -> Direction.SOUTH
            }
        }
    }
}
