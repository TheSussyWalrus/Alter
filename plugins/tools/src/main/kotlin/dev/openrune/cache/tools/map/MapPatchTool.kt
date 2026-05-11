package dev.openrune.cache.tools.map

import com.displee.cache.CacheLibrary
import dev.openrune.cache.CacheManager
import dev.openrune.cache.MAPS
import dev.openrune.cache.filestore.loadLocations
import dev.openrune.cache.tools.tasks.impl.MapPatchTask
import java.nio.file.Path
import kotlin.io.path.createDirectories

object MapPatchTool {
    @JvmStatic
    fun main(args: Array<String>) {
        val options = PatchToolOptions.from(args)
        options.reportDir.createDirectories()

        if (options.findLocationIds.isNotEmpty()) {
            findLocations(options)
            return
        }
        if (options.listBounds != null) {
            listLocations(options)
            return
        }
        if (options.inspectObjectIds.isNotEmpty()) {
            inspectObjects(options)
            return
        }

        val task =
            MapPatchTask(
                patchDir = options.patchDir,
                reportDir = options.reportDir,
                rscmDir = options.rscmDir,
                revision = options.revision,
                dryRun = options.dryRun,
            )

        val library = CacheLibrary(options.cacheDir.toString())
        try {
            task.init(library)
            if (!options.dryRun) {
                library.update()
            }
        } finally {
            library.close()
        }
    }

    private fun findLocations(options: PatchToolOptions) {
        val library = CacheLibrary(options.cacheDir.toString())
        try {
            val regions =
                options.findRegion?.let { listOf(it) }
                    ?: buildList {
                        for (x in 0..255) {
                            for (z in 0..255) {
                                add(x to z)
                            }
                        }
                    }
            regions.forEach { region ->
                val landArchive = "l${region.first}_${region.second}"
                val locationData = library.data(MAPS, landArchive) ?: return@forEach
                loadLocations(locationData) { location ->
                    if (location.id in options.findLocationIds) {
                        val x = region.first * 64 + location.localX
                        val z = region.second * 64 + location.localY
                        println(
                            "loc id=${location.id} type=${location.type} orientation=${location.orientation} " +
                                "tile=$x,$z,${location.height} region=${region.first},${region.second}",
                        )
                    }
                }
            }
        } finally {
            library.close()
        }
    }

    private fun listLocations(options: PatchToolOptions) {
        val bounds = options.listBounds ?: return
        val region = bounds.minX.floorDiv(REGION_SIZE) to bounds.minZ.floorDiv(REGION_SIZE)
        require(bounds.maxX.floorDiv(REGION_SIZE) == region.first && bounds.maxZ.floorDiv(REGION_SIZE) == region.second) {
            "--list-bounds must stay inside one region for this inspection helper."
        }
        val library = CacheLibrary(options.cacheDir.toString())
        try {
            CacheManager.init(options.cacheDir, options.revision)
            val landArchive = "l${region.first}_${region.second}"
            val locationData = library.data(MAPS, landArchive) ?: return
            loadLocations(locationData) { location ->
                val x = region.first * REGION_SIZE + location.localX
                val z = region.second * REGION_SIZE + location.localY
                if (location.height == bounds.height && x in bounds.minX..bounds.maxX && z in bounds.minZ..bounds.maxZ) {
                    val obj = CacheManager.getObjectOrDefault(location.id)
                    println(
                        "loc id=${location.id} name=${obj.name} type=${location.type} orientation=${location.orientation} " +
                            "tile=$x,$z,${location.height} size=${obj.sizeX}x${obj.sizeY} actions=${obj.actions}",
                    )
                }
            }
        } finally {
            library.close()
        }
    }

    private fun inspectObjects(options: PatchToolOptions) {
        CacheManager.init(options.cacheDir, options.revision)
        options.inspectObjectIds.sorted().forEach { id ->
            val obj = CacheManager.getObjectOrDefault(id)
            println(
                "object id=$id name=${obj.name} size=${obj.sizeX}x${obj.sizeY} " +
                    "models=${obj.objectModels} types=${obj.objectTypes} actions=${obj.actions} " +
                    "interactive=${obj.interactive} solid=${obj.solid} clipType=${obj.clipType} " +
                    "mapScene=${obj.mapSceneID} varbit=${obj.varbitId} varp=${obj.varpId} transforms=${obj.transforms}",
            )
        }
    }
}

fun main(args: Array<String>) {
    MapPatchTool.main(args)
}

private data class PatchToolOptions(
    val dryRun: Boolean,
    val cacheDir: Path,
    val patchDir: Path,
    val reportDir: Path,
    val rscmDir: Path,
    val revision: Int,
    val findRegion: Pair<Int, Int>?,
    val findLocationIds: Set<Int>,
    val listBounds: InspectionBounds?,
    val inspectObjectIds: Set<Int>,
) {
    companion object {
        fun from(args: Array<String>): PatchToolOptions {
            var dryRun = false
            var cacheDir = Path.of("data", "cache")
            var patchDir = Path.of("data", "cfg", "map_patches")
            var reportDir = Path.of("build", "map-patches")
            var rscmDir = Path.of("data", "cfg", "rscm")
            var revision = 228
            var findRegion: Pair<Int, Int>? = null
            var findLocationIds: Set<Int> = emptySet()
            var listBounds: InspectionBounds? = null
            var inspectObjectIds: Set<Int> = emptySet()

            args.forEach { arg ->
                when {
                    arg == "--dry-run" -> dryRun = true
                    arg.startsWith("--cache=") -> cacheDir = Path.of(arg.substringAfter("="))
                    arg.startsWith("--patches=") -> patchDir = Path.of(arg.substringAfter("="))
                    arg.startsWith("--reports=") -> reportDir = Path.of(arg.substringAfter("="))
                    arg.startsWith("--rscm=") -> rscmDir = Path.of(arg.substringAfter("="))
                    arg.startsWith("--revision=") -> revision = arg.substringAfter("=").toInt()
                    arg.startsWith("--find-region=") -> {
                        val parts = arg.substringAfter("=").split(",")
                        require(parts.size == 2) { "--find-region must be formatted as x,z." }
                        findRegion = parts[0].toInt() to parts[1].toInt()
                    }
                    arg.startsWith("--find-location-ids=") ->
                        findLocationIds =
                            arg
                                .substringAfter("=")
                                .split(",")
                                .filter { it.isNotBlank() }
                                .map { it.toInt() }
                                .toSet()
                    arg.startsWith("--list-bounds=") -> {
                        val parts = arg.substringAfter("=").split(",")
                        require(parts.size == 5) { "--list-bounds must be formatted as minX,minZ,maxX,maxZ,height." }
                        listBounds =
                            InspectionBounds(
                                minX = parts[0].toInt(),
                                minZ = parts[1].toInt(),
                                maxX = parts[2].toInt(),
                                maxZ = parts[3].toInt(),
                                height = parts[4].toInt(),
                            )
                    }
                    arg.startsWith("--inspect-object-ids=") ->
                        inspectObjectIds =
                            arg
                                .substringAfter("=")
                                .split(",")
                                .filter { it.isNotBlank() }
                                .map { it.toInt() }
                                .toSet()
                    else -> error("Unknown patchMaps argument '$arg'.")
                }
            }

            return PatchToolOptions(
                dryRun = dryRun,
                cacheDir = cacheDir.toAbsolutePath().normalize(),
                patchDir = patchDir.toAbsolutePath().normalize(),
                reportDir = reportDir.toAbsolutePath().normalize(),
                rscmDir = rscmDir.toAbsolutePath().normalize(),
                revision = revision,
                findRegion = findRegion,
                findLocationIds = findLocationIds,
                listBounds = listBounds,
                inspectObjectIds = inspectObjectIds,
            )
        }
    }
}

private data class InspectionBounds(
    val minX: Int,
    val minZ: Int,
    val maxX: Int,
    val maxZ: Int,
    val height: Int,
)

private const val REGION_SIZE = 64
