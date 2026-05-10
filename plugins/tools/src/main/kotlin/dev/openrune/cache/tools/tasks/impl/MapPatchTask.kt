package dev.openrune.cache.tools.tasks.impl

import com.displee.cache.CacheLibrary
import com.google.gson.GsonBuilder
import dev.openrune.cache.MAPS
import dev.openrune.cache.filestore.LocationData
import dev.openrune.cache.filestore.TileData
import dev.openrune.cache.filestore.loadLocations
import dev.openrune.cache.filestore.loadTerrain
import dev.openrune.cache.tools.tasks.CacheTask
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension

class MapPatchTask(
    private val patchDir: Path,
    private val reportDir: Path,
    private val rscmDir: Path,
    private val revision: Int,
    private val dryRun: Boolean,
) : CacheTask() {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    override fun init(library: CacheLibrary) {
        require(revision >= 209) { "Map patch terrain encoding currently supports revision 209+ caches only." }
        require(Files.isDirectory(patchDir)) { "Map patch directory does not exist: $patchDir" }

        reportDir.createDirectories()
        val objectNames = RscmObjectNames.load(rscmDir.resolve("object.rscm"))
        val patches =
            Files.list(patchDir)
                .use { stream ->
                    stream
                        .filter { it.extension.equals("json", ignoreCase = true) }
                        .sorted()
                        .map { path ->
                            Files.newBufferedReader(path).use { reader ->
                                gson.fromJson(reader, MapPatchConfig::class.java)
                            }
                        }
                        .toList()
                }

        require(patches.isNotEmpty()) { "No map patch JSON files found in $patchDir" }

        patches.forEach { config ->
            val report = applyPatch(library, config, objectNames)
            val reportPath = reportDir.resolve("${config.key.ifBlank { "map_patch" }}.json")
            Files.writeString(reportPath, gson.toJson(report))
            println(
                "${if (dryRun) "Dry-run" else "Patched"} ${config.key}: " +
                    "${report.changedTiles} tile(s), ${report.removedLocations.size} removed loc(s), report=$reportPath",
            )
        }
    }

    private fun applyPatch(
        library: CacheLibrary,
        config: MapPatchConfig,
        objectNames: RscmObjectNames,
    ): MapPatchReport {
        require(config.key.isNotBlank()) { "Map patch key is required." }
        require(config.regionX >= 0 && config.regionY >= 0) { "Map patch ${config.key} has invalid region coordinates." }
        require(config.revision == 0 || config.revision == revision) {
            "Map patch ${config.key} targets revision ${config.revision}, but patchMaps is running for revision $revision."
        }

        val mapArchive = "m${config.regionX}_${config.regionY}"
        val landArchive = "l${config.regionX}_${config.regionY}"
        val terrainData = library.data(MAPS, mapArchive) ?: error("Missing map archive $mapArchive in cache index $MAPS.")
        val locationData = library.data(MAPS, landArchive) ?: error("Missing landscape archive $landArchive in cache index $MAPS.")
        val terrain = loadTerrain(terrainData, after208 = true)
        val locations = mutableListOf<LocationData>()
        loadLocations(locationData) { locations.add(it) }

        val report =
            MapPatchReport(
                key = config.key,
                description = config.description,
                dryRun = dryRun,
                regionX = config.regionX,
                regionY = config.regionY,
                mapArchive = mapArchive,
                landArchive = landArchive,
                sourceLocationCount = locations.size,
            )

        val accumulatedFootprint = linkedSetOf<LocalTile>()
        config.operations.removeConnectedLocations.forEach { operation ->
            accumulatedFootprint += removeConnectedLocations(config, operation, locations, objectNames, report)
        }

        config.operations.copyTerrainFromSample?.let { operation ->
            copyTerrainFromSample(config, operation, terrain, accumulatedFootprint, report)
        }

        config.operations.copyTerrainRectangles.forEach { operation ->
            copyTerrainRectangle(library, config, operation, terrain, report)
        }

        config.operations.copySourceRectangles.forEach { operation ->
            accumulatedFootprint += copySourceRectangle(library, config, operation, terrain, locations, objectNames, report)
        }

        config.operations.addLocations
            .filter { it.enabled }
            .forEach { operation ->
                val id = operation.id ?: operation.objectName?.let(objectNames::id)
                if (id == null) {
                    report.warnings += "Could not resolve added location '${operation.objectName}'."
                    return@forEach
                }
                val local = operation.tile.toLocal(config)
                val alreadyExists =
                    locations.any {
                        it.id == id &&
                            it.type == operation.type &&
                            it.orientation == operation.orientation &&
                            it.localX == local.x &&
                            it.localY == local.z &&
                            it.height == local.height
                    }
                if (alreadyExists) {
                    return@forEach
                }
                locations += LocationData(id, operation.type, operation.orientation, local.x, local.z, local.height)
                report.addedLocations +=
                    LocationChange(
                        id = id,
                        names = objectNames.names(id),
                        type = operation.type,
                        orientation = operation.orientation,
                        x = operation.tile.x,
                        z = operation.tile.z,
                        height = operation.tile.height,
                    )
            }

        report.reservedSlots += config.operations.reservedSlots
        report.changedTiles = accumulatedFootprint.size
        report.bounds = accumulatedFootprint.bounds(config)

        if (!dryRun) {
            library.put(MAPS, mapArchive, encodeTerrain(terrain))
            library.put(MAPS, landArchive, encodeLocations(locations))
        }

        return report
    }

    private fun removeConnectedLocations(
        config: MapPatchConfig,
        operation: RemoveConnectedLocationsOperation,
        locations: MutableList<LocationData>,
        objectNames: RscmObjectNames,
        report: MapPatchReport,
    ): Set<LocalTile> {
        val seed = operation.seedTile.toLocal(config)
        val removableIds = objectNames.idsMatching(operation.objectNamePrefixes) + operation.objectIds
        if (removableIds.isEmpty()) {
            report.warnings += "No removable object ids resolved for prefixes ${operation.objectNamePrefixes}."
            return emptySet()
        }

        val candidateTiles =
            locations
                .filter { it.height == seed.height && it.id in removableIds && it.localDistance(seed) <= operation.searchRadius }
                .groupBy { LocalTile(it.localX, it.localY, it.height) }

        if (candidateTiles.isEmpty()) {
            operation.fallbackBounds?.let { bounds ->
                return bounds.toLocalTiles(config)
            }
            report.warnings += "No removable locations found near seed ${operation.seedTile}."
            return emptySet()
        }

        val start =
            candidateTiles.keys
                .filter { it.localDistance(seed) <= operation.startSearchRadius }
                .minByOrNull { it.localDistance(seed) }
                ?: candidateTiles.keys.minBy { it.localDistance(seed) }

        val connected = floodConnected(start, candidateTiles.keys, operation.diagonalConnections)
        val footprint =
            if (operation.fillBoundingBox) {
                connected.boundingBox(operation.terrainPadding)
            } else {
                connected
            }

        val removed = mutableListOf<LocationChange>()
        val iterator = locations.iterator()
        while (iterator.hasNext()) {
            val location = iterator.next()
            if (location.height == seed.height && location.id in removableIds && LocalTile(location.localX, location.localY, location.height) in footprint) {
                iterator.remove()
                removed +=
                    LocationChange(
                        id = location.id,
                        names = objectNames.names(location.id),
                        type = location.type,
                        orientation = location.orientation,
                        x = config.regionX * REGION_SIZE + location.localX,
                        z = config.regionY * REGION_SIZE + location.localY,
                        height = location.height,
                    )
            }
        }

        report.removedLocations += removed
        report.removedObjectIds += removed.map { it.id }.distinct().sorted()

        val nonRemovedInsideFootprint =
            locations
                .filter { it.height == seed.height && LocalTile(it.localX, it.localY, it.height) in footprint }
                .map { "${it.id}:${objectNames.names(it.id).firstOrNull().orEmpty()}@${config.regionX * REGION_SIZE + it.localX},${config.regionY * REGION_SIZE + it.localY},${it.height}" }
                .take(40)
        if (nonRemovedInsideFootprint.isNotEmpty()) {
            report.warnings += "Kept ${nonRemovedInsideFootprint.size} non-removable loc(s) inside footprint sample: $nonRemovedInsideFootprint"
        }

        return footprint
    }

    private fun copyTerrainFromSample(
        config: MapPatchConfig,
        operation: CopyTerrainFromSampleOperation,
        terrain: Array<Array<Array<TileData>>>,
        footprint: Set<LocalTile>,
        report: MapPatchReport,
    ) {
        if (footprint.isEmpty()) {
            report.warnings += "No terrain footprint exists for copyTerrainFromSample."
            return
        }

        val sampleLocal = operation.sampleTile.toLocal(config)
        val sample = terrain[sampleLocal.height][sampleLocal.x][sampleLocal.z]
        if (!operation.allowBlankSample && sample.underlayId.toInt() == 0 && sample.overlayId.toInt() == 0) {
            error("Sample tile ${operation.sampleTile} has no underlay/overlay; refusing to use it as grass source.")
        }

        footprint.forEach { tile ->
            val target = terrain[tile.height][tile.x][tile.z]
            val originalHeight = target.height
            val originalSettings = target.settings
            target.attrOpcode = sample.attrOpcode
            target.overlayId = sample.overlayId
            target.overlayPath = sample.overlayPath
            target.overlayRotation = sample.overlayRotation
            target.underlayId = sample.underlayId
            target.settings = if (operation.copySettings) sample.settings else originalSettings
            if (!operation.preserveHeight) {
                target.height = sample.height
            } else {
                target.height = originalHeight
            }
            report.changedTerrain +=
                TerrainChange(
                    x = config.regionX * REGION_SIZE + tile.x,
                    z = config.regionY * REGION_SIZE + tile.z,
                    height = tile.height,
                    underlayId = target.underlayId.toInt(),
                    overlayId = target.overlayId.toInt(),
                )
        }

        report.sampleTile = operation.sampleTile
    }

    private fun copyTerrainRectangle(
        library: CacheLibrary,
        config: MapPatchConfig,
        operation: CopyTerrainRectangleOperation,
        terrain: Array<Array<Array<TileData>>>,
        report: MapPatchReport,
    ) {
        val sampleRegionX = operation.sampleTile.x.floorDiv(REGION_SIZE)
        val sampleRegionY = operation.sampleTile.z.floorDiv(REGION_SIZE)
        val sampleTerrain =
            if (sampleRegionX == config.regionX && sampleRegionY == config.regionY) {
                terrain
            } else {
                val sampleArchive = "m${sampleRegionX}_${sampleRegionY}"
                val sampleData = library.data(MAPS, sampleArchive) ?: error("Missing terrain sample archive $sampleArchive in cache index $MAPS.")
                loadTerrain(sampleData, after208 = true)
            }
        val sampleLocal = operation.sampleTile.toLocal(sampleRegionX, sampleRegionY)
        val sample = sampleTerrain[sampleLocal.height][sampleLocal.x][sampleLocal.z]
        if (!operation.allowBlankSample && sample.underlayId.toInt() == 0 && sample.overlayId.toInt() == 0) {
            error("Sample tile ${operation.sampleTile} has no underlay/overlay; refusing to use it as terrain source.")
        }

        operation.bounds.toLocalTiles(config).forEach { tile ->
            val target = terrain[tile.height][tile.x][tile.z]
            val originalHeight = target.height
            val originalSettings = target.settings
            target.attrOpcode = sample.attrOpcode
            target.overlayId = sample.overlayId
            target.overlayPath = sample.overlayPath
            target.overlayRotation = sample.overlayRotation
            target.underlayId = sample.underlayId
            target.settings = if (operation.copySettings) sample.settings else originalSettings
            if (!operation.preserveHeight) {
                target.height = sample.height
            } else {
                target.height = originalHeight
            }
            report.changedTerrain +=
                TerrainChange(
                    x = config.regionX * REGION_SIZE + tile.x,
                    z = config.regionY * REGION_SIZE + tile.z,
                    height = tile.height,
                    underlayId = target.underlayId.toInt(),
                    overlayId = target.overlayId.toInt(),
                )
        }
    }

    private fun copySourceRectangle(
        library: CacheLibrary,
        config: MapPatchConfig,
        operation: CopySourceRectangleOperation,
        terrain: Array<Array<Array<TileData>>>,
        locations: MutableList<LocationData>,
        objectNames: RscmObjectNames,
        report: MapPatchReport,
    ): Set<LocalTile> {
        if (!operation.enabled) {
            return emptySet()
        }
        val sourceRegionX = operation.sourceBounds.minX.floorDiv(REGION_SIZE)
        val sourceRegionY = operation.sourceBounds.minZ.floorDiv(REGION_SIZE)
        require(operation.sourceBounds.maxX.floorDiv(REGION_SIZE) == sourceRegionX && operation.sourceBounds.maxZ.floorDiv(REGION_SIZE) == sourceRegionY) {
            "copySourceRectangle source bounds ${operation.sourceBounds} must stay inside one region."
        }
        val width = kotlin.math.abs(operation.sourceBounds.maxX - operation.sourceBounds.minX) + 1
        val length = kotlin.math.abs(operation.sourceBounds.maxZ - operation.sourceBounds.minZ) + 1
        val destinationBounds =
            WorldBounds(
                minX = operation.destinationTile.x,
                minZ = operation.destinationTile.z,
                maxX = operation.destinationTile.x + width - 1,
                maxZ = operation.destinationTile.z + length - 1,
                height = operation.destinationTile.height,
            )
        val destinationTiles = destinationBounds.toLocalTiles(config)

        val sourceTerrain =
            if (sourceRegionX == config.regionX && sourceRegionY == config.regionY) {
                terrain
            } else {
                val sourceArchive = "m${sourceRegionX}_${sourceRegionY}"
                val sourceData = library.data(MAPS, sourceArchive) ?: error("Missing terrain source archive $sourceArchive in cache index $MAPS.")
                loadTerrain(sourceData, after208 = true)
            }

        if (operation.copyTerrain) {
            for (dx in 0 until width) {
                for (dz in 0 until length) {
                    val source = WorldTile(operation.sourceBounds.minX + dx, operation.sourceBounds.minZ + dz, operation.sourceBounds.height)
                        .toLocal(sourceRegionX, sourceRegionY)
                    val destination = WorldTile(operation.destinationTile.x + dx, operation.destinationTile.z + dz, operation.destinationTile.height)
                        .toLocal(config)
                    val sourceTile = sourceTerrain[source.height][source.x][source.z]
                    val target = terrain[destination.height][destination.x][destination.z]
                    val originalHeight = target.height
                    val originalSettings = target.settings
                    target.attrOpcode = sourceTile.attrOpcode
                    target.overlayId = sourceTile.overlayId
                    target.overlayPath = sourceTile.overlayPath
                    target.overlayRotation = sourceTile.overlayRotation
                    target.underlayId = sourceTile.underlayId
                    target.settings = if (operation.copySettings) sourceTile.settings else originalSettings
                    if (!operation.preserveHeight) {
                        target.height = sourceTile.height
                    } else {
                        target.height = originalHeight
                    }
                    report.changedTerrain +=
                        TerrainChange(
                            x = config.regionX * REGION_SIZE + destination.x,
                            z = config.regionY * REGION_SIZE + destination.z,
                            height = destination.height,
                            underlayId = target.underlayId.toInt(),
                            overlayId = target.overlayId.toInt(),
                        )
                }
            }
        }

        if (operation.copyLocations) {
            val sourceLandArchive = "l${sourceRegionX}_${sourceRegionY}"
            val sourceLocationData = library.data(MAPS, sourceLandArchive) ?: error("Missing location source archive $sourceLandArchive in cache index $MAPS.")
            val sourceLocations = mutableListOf<LocationData>()
            loadLocations(sourceLocationData) { sourceLocations += it }
            val allowedIds = operation.objectIds.toSet() + objectNames.idsMatching(operation.objectNamePrefixes)
            sourceLocations
                .filter { source ->
                    val worldX = sourceRegionX * REGION_SIZE + source.localX
                    val worldZ = sourceRegionY * REGION_SIZE + source.localY
                    source.height == operation.sourceBounds.height &&
                        worldX in operation.sourceBounds.minX..operation.sourceBounds.maxX &&
                        worldZ in operation.sourceBounds.minZ..operation.sourceBounds.maxZ &&
                        (allowedIds.isEmpty() || source.id in allowedIds)
                }
                .forEach { source ->
                    val worldX = sourceRegionX * REGION_SIZE + source.localX
                    val worldZ = sourceRegionY * REGION_SIZE + source.localY
                    val destinationWorld =
                        WorldTile(
                            x = operation.destinationTile.x + (worldX - operation.sourceBounds.minX),
                            z = operation.destinationTile.z + (worldZ - operation.sourceBounds.minZ),
                            height = operation.destinationTile.height + (source.height - operation.sourceBounds.height),
                        )
                    val destination = destinationWorld.toLocal(config)
                    val alreadyExists =
                        locations.any {
                            it.id == source.id &&
                                it.type == source.type &&
                                it.orientation == source.orientation &&
                                it.localX == destination.x &&
                                it.localY == destination.z &&
                                it.height == destination.height
                        }
                    if (!alreadyExists) {
                        locations += LocationData(source.id, source.type, source.orientation, destination.x, destination.z, destination.height)
                        report.addedLocations +=
                            LocationChange(
                                id = source.id,
                                names = objectNames.names(source.id),
                                type = source.type,
                                orientation = source.orientation,
                                x = destinationWorld.x,
                                z = destinationWorld.z,
                                height = destinationWorld.height,
                            )
                    }
                }
        }

        return destinationTiles
    }

    private fun floodConnected(
        start: LocalTile,
        candidates: Set<LocalTile>,
        diagonal: Boolean,
    ): Set<LocalTile> {
        val result = linkedSetOf<LocalTile>()
        val queue = ArrayDeque<LocalTile>()
        queue += start
        result += start
        val offsets =
            if (diagonal) {
                listOf(-1 to -1, -1 to 0, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 0, 1 to 1)
            } else {
                listOf(-1 to 0, 0 to -1, 0 to 1, 1 to 0)
            }
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            offsets.forEach { (dx, dz) ->
                val next = LocalTile(current.x + dx, current.z + dz, current.height)
                if (next in candidates && result.add(next)) {
                    queue += next
                }
            }
        }
        return result
    }

    private fun WorldTile.toLocal(config: MapPatchConfig): LocalTile {
        return toLocal(config.regionX, config.regionY)
    }

    private fun WorldTile.toLocal(regionX: Int, regionY: Int): LocalTile {
        val localX = x - regionX * REGION_SIZE
        val localZ = z - regionY * REGION_SIZE
        require(localX in 0 until REGION_SIZE && localZ in 0 until REGION_SIZE && height in 0..3) {
            "Tile $this is outside region $regionX,$regionY."
        }
        return LocalTile(localX, localZ, height)
    }

    private fun LocationData.localDistance(tile: LocalTile): Int =
        kotlin.math.max(kotlin.math.abs(localX - tile.x), kotlin.math.abs(localY - tile.z))

    private companion object {
        private const val REGION_SIZE = 64
    }
}

private data class MapPatchConfig(
    var key: String = "",
    var description: String = "",
    var regionX: Int = 0,
    var regionY: Int = 0,
    var revision: Int = 0,
    var operations: MapPatchOperations = MapPatchOperations(),
)

private data class MapPatchOperations(
    var removeConnectedLocations: List<RemoveConnectedLocationsOperation> = emptyList(),
    var copyTerrainFromSample: CopyTerrainFromSampleOperation? = null,
    var copyTerrainRectangles: List<CopyTerrainRectangleOperation> = emptyList(),
    var copySourceRectangles: List<CopySourceRectangleOperation> = emptyList(),
    var addLocations: List<AddLocationOperation> = emptyList(),
    var reservedSlots: List<ReservedSlot> = emptyList(),
)

private data class RemoveConnectedLocationsOperation(
    var seedTile: WorldTile = WorldTile(),
    var objectNamePrefixes: List<String> = emptyList(),
    var objectIds: List<Int> = emptyList(),
    var searchRadius: Int = 32,
    var startSearchRadius: Int = 3,
    var diagonalConnections: Boolean = true,
    var fillBoundingBox: Boolean = true,
    var terrainPadding: Int = 0,
    var fallbackBounds: WorldBounds? = null,
)

private data class CopyTerrainFromSampleOperation(
    var sampleTile: WorldTile = WorldTile(),
    var preserveHeight: Boolean = true,
    var copySettings: Boolean = false,
    var allowBlankSample: Boolean = false,
)

private data class CopyTerrainRectangleOperation(
    var sampleTile: WorldTile = WorldTile(),
    var bounds: WorldBounds = WorldBounds(),
    var preserveHeight: Boolean = true,
    var copySettings: Boolean = false,
    var allowBlankSample: Boolean = false,
)

private data class CopySourceRectangleOperation(
    var enabled: Boolean = true,
    var sourceBounds: WorldBounds = WorldBounds(),
    var destinationTile: WorldTile = WorldTile(),
    var objectNamePrefixes: List<String> = emptyList(),
    var objectIds: List<Int> = emptyList(),
    var copyTerrain: Boolean = true,
    var copyLocations: Boolean = true,
    var preserveHeight: Boolean = true,
    var copySettings: Boolean = false,
)

private data class AddLocationOperation(
    var enabled: Boolean = true,
    var id: Int? = null,
    var objectName: String? = null,
    var tile: WorldTile = WorldTile(),
    var type: Int = 10,
    var orientation: Int = 0,
)

private data class ReservedSlot(
    var key: String = "",
    var type: String = "",
    var enabled: Boolean = false,
    var x: Int = 0,
    var z: Int = 0,
    var height: Int = 0,
    var width: Int = 1,
    var length: Int = 1,
    var notes: String = "",
)

private data class WorldTile(
    var x: Int = 0,
    var z: Int = 0,
    var height: Int = 0,
)

private data class WorldBounds(
    var minX: Int = 0,
    var minZ: Int = 0,
    var maxX: Int = 0,
    var maxZ: Int = 0,
    var height: Int = 0,
)

private data class LocalTile(
    val x: Int,
    val z: Int,
    val height: Int,
) {
    fun localDistance(other: LocalTile): Int =
        kotlin.math.max(kotlin.math.abs(x - other.x), kotlin.math.abs(z - other.z))
}

private data class MapPatchReport(
    val key: String,
    val description: String,
    val dryRun: Boolean,
    val regionX: Int,
    val regionY: Int,
    val mapArchive: String,
    val landArchive: String,
    val sourceLocationCount: Int,
    var sampleTile: WorldTile? = null,
    var changedTiles: Int = 0,
    var bounds: ReportBounds? = null,
    val removedObjectIds: MutableList<Int> = mutableListOf(),
    val removedLocations: MutableList<LocationChange> = mutableListOf(),
    val addedLocations: MutableList<LocationChange> = mutableListOf(),
    val changedTerrain: MutableList<TerrainChange> = mutableListOf(),
    val reservedSlots: MutableList<ReservedSlot> = mutableListOf(),
    val warnings: MutableList<String> = mutableListOf(),
)

private data class LocationChange(
    val id: Int,
    val names: List<String>,
    val type: Int,
    val orientation: Int,
    val x: Int,
    val z: Int,
    val height: Int,
)

private data class TerrainChange(
    val x: Int,
    val z: Int,
    val height: Int,
    val underlayId: Int,
    val overlayId: Int,
)

private data class ReportBounds(
    val minX: Int,
    val minZ: Int,
    val maxX: Int,
    val maxZ: Int,
    val height: Int,
)

private class RscmObjectNames private constructor(
    private val nameToId: Map<String, Int>,
) {
    private val idToNames: Map<Int, List<String>> = nameToId.entries.groupBy({ it.value }, { it.key })

    fun id(name: String): Int? = nameToId[normalize(name)]

    fun idsMatching(prefixes: List<String>): Set<Int> =
        prefixes
            .flatMap { prefix ->
                val normalized = normalize(prefix).removeSuffix("*")
                nameToId
                    .filterKeys { name ->
                        name == normalized ||
                            name.startsWith("${normalized}_") ||
                            (prefix.endsWith("*") && name.startsWith(normalized))
                    }
                    .values
            }
            .toSet()

    fun names(id: Int): List<String> = idToNames[id].orEmpty().sorted()

    private fun normalize(name: String): String =
        name.removePrefix("object.").lowercase().replace(" ", "_")

    companion object {
        fun load(path: Path): RscmObjectNames {
            require(Files.exists(path)) { "Missing object RSCM mapping: $path" }
            val mappings =
                Files.readAllLines(path)
                    .mapNotNull { line ->
                        val trimmed = line.trim()
                        if (trimmed.isBlank() || trimmed.startsWith("#") || !trimmed.contains(":")) {
                            null
                        } else {
                            val name = trimmed.substringBefore(":").trim().lowercase()
                            val id = trimmed.substringAfter(":").trim().toIntOrNull()
                            id?.let { name to it }
                        }
                    }
                    .toMap()
            return RscmObjectNames(mappings)
        }
    }
}

private fun Set<LocalTile>.boundingBox(padding: Int): Set<LocalTile> {
    if (isEmpty()) {
        return emptySet()
    }
    val height = first().height
    val minX = (minOf { it.x } - padding).coerceAtLeast(0)
    val maxX = (maxOf { it.x } + padding).coerceAtMost(63)
    val minZ = (minOf { it.z } - padding).coerceAtLeast(0)
    val maxZ = (maxOf { it.z } + padding).coerceAtMost(63)
    val tiles = linkedSetOf<LocalTile>()
    for (x in minX..maxX) {
        for (z in minZ..maxZ) {
            tiles += LocalTile(x, z, height)
        }
    }
    return tiles
}

private fun Set<LocalTile>.bounds(config: MapPatchConfig): ReportBounds? {
    if (isEmpty()) {
        return null
    }
    return ReportBounds(
        minX = config.regionX * 64 + minOf { it.x },
        minZ = config.regionY * 64 + minOf { it.z },
        maxX = config.regionX * 64 + maxOf { it.x },
        maxZ = config.regionY * 64 + maxOf { it.z },
        height = first().height,
    )
}

private fun WorldBounds.toLocalTiles(config: MapPatchConfig): Set<LocalTile> {
    require(height in 0..3) { "Bounds $this use invalid height $height." }
    val localMinX = minX - config.regionX * 64
    val localMaxX = maxX - config.regionX * 64
    val localMinZ = minZ - config.regionY * 64
    val localMaxZ = maxZ - config.regionY * 64
    require(localMinX in 0 until 64 && localMaxX in 0 until 64 && localMinZ in 0 until 64 && localMaxZ in 0 until 64) {
        "Bounds $this are outside region ${config.regionX},${config.regionY}."
    }
    val minX = kotlin.math.min(localMinX, localMaxX)
    val maxX = kotlin.math.max(localMinX, localMaxX)
    val minZ = kotlin.math.min(localMinZ, localMaxZ)
    val maxZ = kotlin.math.max(localMinZ, localMaxZ)
    val tiles = linkedSetOf<LocalTile>()
    for (x in minX..maxX) {
        for (z in minZ..maxZ) {
            tiles += LocalTile(x, z, height)
        }
    }
    return tiles
}

private fun encodeTerrain(tiles: Array<Array<Array<TileData>>>): ByteArray {
    val writer = ByteArraySmartWriter()
    for (height in 0 until 4) {
        for (x in 0 until 64) {
            for (z in 0 until 64) {
                val tile = tiles[height][x][z]
                if (tile.overlayId.toInt() != 0) {
                    val attr =
                        if (tile.attrOpcode in 2..49) {
                            tile.attrOpcode
                        } else {
                            2 + (tile.overlayPath.toInt() * 4) + tile.overlayRotation.toInt()
                        }
                    writer.writeShort(attr)
                    writer.writeShort(tile.overlayId.toInt())
                }
                if (tile.settings.toInt() != 0) {
                    writer.writeShort(49 + (tile.settings.toInt() and 0xff))
                }
                if (tile.underlayId.toInt() != 0) {
                    writer.writeShort(81 + tile.underlayId.toInt())
                }
                if (tile.height != 0) {
                    writer.writeShort(1)
                    writer.writeByte(tile.height)
                } else {
                    writer.writeShort(0)
                }
            }
        }
    }
    return writer.toByteArray()
}

private fun encodeLocations(locations: List<LocationData>): ByteArray {
    val writer = ByteArraySmartWriter()
    var previousId = -1
    locations
        .sortedWith(compareBy<LocationData> { it.id }.thenBy { it.height }.thenBy { it.localX }.thenBy { it.localY }.thenBy { it.type }.thenBy { it.orientation })
        .groupBy { it.id }
        .forEach { (id, group) ->
            writer.writeLargeSmart(id - previousId)
            previousId = id
            var previousPosition = 0
            group
                .sortedBy { (it.height shl 12) or (it.localX shl 6) or it.localY }
                .forEach { location ->
                    val position = (location.height shl 12) or (location.localX shl 6) or location.localY
                    writer.writeSmart(position - previousPosition + 1)
                    previousPosition = position
                    writer.writeByte((location.type shl 2) or (location.orientation and 3))
                }
            writer.writeSmart(0)
        }
    writer.writeLargeSmart(0)
    return writer.toByteArray()
}

private class ByteArraySmartWriter {
    private val out = ByteArrayOutputStream()

    fun writeByte(value: Int) {
        out.write(value and 0xff)
    }

    fun writeShort(value: Int) {
        out.write((value ushr 8) and 0xff)
        out.write(value and 0xff)
    }

    fun writeSmart(value: Int) {
        require(value >= 0) { "Smart values must be non-negative." }
        if (value < 128) {
            writeByte(value)
        } else {
            writeShort(value + 32768)
        }
    }

    fun writeLargeSmart(value: Int) {
        require(value >= 0) { "Large smart values must be non-negative." }
        var remaining = value
        while (remaining >= 32767) {
            writeSmart(32767)
            remaining -= 32767
        }
        writeSmart(remaining)
    }

    fun toByteArray(): ByteArray = out.toByteArray()
}
