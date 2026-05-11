package org.alter.plugins.content.magic.teleports

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.alter.api.InterfaceDestination
import org.alter.api.Spellbook
import org.alter.api.ext.InterfaceEvent
import org.alter.api.ext.closeInterface
import org.alter.api.ext.message
import org.alter.api.ext.openInterface
import org.alter.api.ext.player
import org.alter.api.ext.setComponentHidden
import org.alter.api.ext.setComponentText
import org.alter.api.ext.setInterfaceEvents
import org.alter.api.ext.setInterfaceUnderlay
import org.alter.game.Server
import org.alter.game.model.Tile
import org.alter.game.model.World
import org.alter.game.model.attr.AttributeKey
import org.alter.game.model.entity.Player
import org.alter.game.plugin.KotlinPlugin
import org.alter.game.plugin.PluginRepository
import org.alter.plugins.content.magic.MagicSpells
import org.alter.plugins.content.magic.SpellMetadata
import org.alter.plugins.content.magic.TeleportType
import org.alter.plugins.content.magic.canTeleport
import org.alter.plugins.content.magic.teleport
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class WorldTeleportPlugin(
    r: PluginRepository,
    world: World,
    server: Server,
) : KotlinPlugin(r, world, server) {

    private val gson = Gson()
    private val destinations = loadDestinations()

    init {
        if (!MagicSpells.isLoaded()) {
            MagicSpells.loadSpellRequirements(world)
        }

        bindSpellButton(
            cacheNames = arrayOf("Home Teleport", "Varrock Teleport"),
            debugName = "Home Teleport",
        ) { player ->
            teleportHome(player)
        }

        bindSpellButton(
            cacheNames = arrayOf("World Teleport", "Lumbridge Teleport"),
            debugName = "World Teleport",
        ) { player ->
            openDirectory(player)
        }

        onCommand("worldtele", description = "Open the world teleport directory") {
            openDirectory(player)
        }

        onCommand("worldteleports", description = "Open the world teleport directory") {
            openDirectory(player)
        }

        onButton(DIRECTORY_INTERFACE, CLOSE_COMPONENT) {
            closeDirectory(player)
        }

        SLOT_COMPONENTS.forEach { component ->
            onButton(DIRECTORY_INTERFACE, component) {
                handleDirectoryButton(player, component)
            }
        }
    }

    private fun bindSpellButton(
        cacheNames: Array<String>,
        debugName: String,
        block: (Player) -> Unit,
    ) {
        val spell = findNormalSpell(*cacheNames)
        if (spell == null) {
            Server.logger.warn { "Unable to bind $debugName: spell metadata not found for ${cacheNames.joinToString()}." }
            return
        }

        onButton(spell.interfaceId, spell.component) {
            block(player)
        }
    }

    private fun findNormalSpell(vararg names: String): SpellMetadata? =
        MagicSpells.getSpells()
            .filter { it.spellbook == Spellbook.NORMAL.id }
            .firstOrNull { spell -> names.any { it.equals(spell.name, ignoreCase = true) } }

    private fun teleportHome(player: Player) {
        teleport(player, HOME_TILE, "Home")
    }

    private fun teleport(
        player: Player,
        tile: Tile,
        name: String,
    ) {
        closeDirectory(player)
        if (!player.canTeleport(TeleportType.MODERN)) {
            return
        }
        player.message("Teleporting to $name.")
        player.teleport(tile, TeleportType.MODERN)
    }

    private fun openDirectory(player: Player) {
        if (destinations.isEmpty()) {
            player.message("No world teleports are currently configured.")
            return
        }

        renderCategories(player)
    }

    private fun renderCategories(player: Player) {
        val rows =
            CATEGORY_OPTIONS.map { category ->
                val count = destinationsFor(player, category).size
                DirectorySlot(
                    label = displayCategory(category),
                    sublabel = count.toString(),
                    action = "$ACTION_CATEGORY:$category",
                )
            }

        renderGrid(
            player = player,
            title = "Choose Destination",
            state = DirectoryViewState(screen = SCREEN_CATEGORIES),
            rows = rows,
        )
    }

    private fun renderDestinations(
        player: Player,
        category: String,
        page: Int = 0,
    ) {
        val entries = destinationsFor(player, category)
        val totalPages = ((entries.size + DESTINATION_PAGE_SIZE - 1) / DESTINATION_PAGE_SIZE).coerceAtLeast(1)
        val safePage = page.coerceIn(0, totalPages - 1)
        val pageEntries = entries.drop(safePage * DESTINATION_PAGE_SIZE).take(DESTINATION_PAGE_SIZE)

        val rows =
            pageEntries.map { entry ->
                DirectorySlot(
                    label = entry.name,
                    sublabel = "Go",
                    action = "$ACTION_TELEPORT:${entry.key}",
                )
            }.toMutableList()

        rows += DirectorySlot(label = "Back", sublabel = "Menu", action = ACTION_CATEGORIES)
        if (safePage > 0) {
            rows += DirectorySlot(label = "Previous", sublabel = "Prev", action = ACTION_PREVIOUS_PAGE)
        }
        if (safePage + 1 < totalPages) {
            rows += DirectorySlot(label = "Next", sublabel = "Next", action = ACTION_NEXT_PAGE)
        }

        if (entries.isEmpty()) {
            rows.add(
                0,
                DirectorySlot(
                    label = "No results",
                    sublabel = "None",
                    action = ACTION_NONE,
                ),
            )
        }

        renderGrid(
            player = player,
            title = category,
            state =
                DirectoryViewState(
                    screen = SCREEN_DESTINATIONS,
                    category = category,
                    page = safePage,
                ),
            rows = rows,
        )
    }

    private fun renderGrid(
        player: Player,
        title: String,
        state: DirectoryViewState,
        rows: List<DirectorySlot>,
    ) {
        val visibleRows = rows.take(SLOT_COMPONENTS.size)
        val actions = visibleRows.map { it.action }

        player.attr[VIEW_STATE_ATTR] = gson.toJson(state.copy(actions = actions))
        player.setInterfaceUnderlay(color = -1, transparency = -1)
        player.openInterface(interfaceId = DIRECTORY_INTERFACE, dest = InterfaceDestination.MAIN_SCREEN)
        player.setComponentText(DIRECTORY_INTERFACE, TITLE_COMPONENT, title)

        SLOT_COMPONENTS.forEachIndexed { index, component ->
            val row = visibleRows.getOrNull(index)
            val hidden = row == null
            player.setComponentHidden(DIRECTORY_INTERFACE, component, hidden)
            player.setComponentText(DIRECTORY_INTERFACE, LABEL_COMPONENTS[index], row?.label.orEmpty())
            player.setComponentText(DIRECTORY_INTERFACE, SUBLABEL_COMPONENTS[index], row?.sublabel.orEmpty())
            player.setInterfaceEvents(
                interfaceId = DIRECTORY_INTERFACE,
                component = component,
                range = -1..-1,
                setting = if (hidden) 0 else InterfaceEvent.PAUSE.flag,
            )
        }
    }

    private fun handleDirectoryButton(
        player: Player,
        component: Int,
    ) {
        val state = readState(player) ?: return
        val index = SLOT_COMPONENTS.indexOf(component)
        if (index == -1) {
            return
        }
        val action = state.actions.getOrNull(index) ?: return

        when {
            action == ACTION_NONE -> return
            action == ACTION_CATEGORIES -> renderCategories(player)
            action == ACTION_PREVIOUS_PAGE -> renderDestinations(player, state.category, state.page - 1)
            action == ACTION_NEXT_PAGE -> renderDestinations(player, state.category, state.page + 1)
            action.startsWith("$ACTION_CATEGORY:") -> {
                val category = action.substringAfter(":")
                renderDestinations(player, category)
            }
            action.startsWith("$ACTION_TELEPORT:") -> {
                val destination = destinationByKey(action.substringAfter(":")) ?: return
                teleport(player, destination.tile, destination.name)
            }
        }
    }

    private fun destinationsFor(
        player: Player,
        category: String,
    ): List<WorldTeleportEntry> =
        destinations.filter { it.category.equals(category, ignoreCase = true) }

    private fun destinationByKey(key: String): WorldTeleportEntry? = destinations.firstOrNull { it.key == key }

    private fun displayCategory(category: String): String =
        when (category) {
            "Dungeons" -> "Dungeon"
            "Monsters" -> "Monster"
            "Minigames" -> "Games"
            "Wilderness" -> "Wildy"
            else -> category
        }

    private fun closeDirectory(player: Player) {
        player.attr.remove(VIEW_STATE_ATTR)
        player.closeInterface(DIRECTORY_INTERFACE)
    }

    private fun loadDestinations(): List<WorldTeleportEntry> {
        val path = resolveConfigPath()
        if (path == null) {
            Server.logger.warn { "Unable to load world teleports: data/cfg/magic/world_teleports.json not found." }
            return emptyList()
        }

        return runCatching {
            Files.newBufferedReader(path).use { reader ->
                val listType = object : TypeToken<List<WorldTeleportEntry>>() {}.type
                Gson().fromJson<List<WorldTeleportEntry>>(reader, listType)
                    .orEmpty()
                    .filter { it.enabled }
                    .sortedWith(compareBy<WorldTeleportEntry> { it.category }.thenBy { it.order }.thenBy { it.name })
            }
        }.getOrElse { error ->
            Server.logger.error(error) { "Unable to load world teleports from $path." }
            emptyList()
        }
    }

    private fun resolveConfigPath(): Path? {
        val candidates =
            listOf(
                Paths.get("data/cfg/magic/world_teleports.json"),
                Paths.get("../data/cfg/magic/world_teleports.json"),
                Paths.get("../../data/cfg/magic/world_teleports.json"),
            )

        return candidates
            .map { it.toAbsolutePath().normalize() }
            .firstOrNull { Files.exists(it) }
    }

    private fun readState(player: Player): DirectoryViewState? =
        player.attr[VIEW_STATE_ATTR]?.let { value ->
            runCatching { gson.fromJson(value, DirectoryViewState::class.java) }.getOrNull()
        }

    private data class DirectorySlot(
        val label: String,
        val sublabel: String,
        val action: String,
    )

    private data class DirectoryViewState(
        val screen: String = SCREEN_CATEGORIES,
        val category: String = CATEGORY_CITIES,
        val page: Int = 0,
        val actions: List<String> = emptyList(),
    )

    private data class WorldTeleportEntry(
        val key: String,
        val name: String,
        val description: String = "",
        val category: String = CATEGORY_CITIES,
        val x: Int,
        val z: Int,
        val height: Int = 0,
        val enabled: Boolean = true,
        val order: Int = 0,
        val iconItem: String? = null,
        val iconNpc: String? = null,
        val iconSprite: Int? = null,
        val level: Int = 1,
        val wilderness: Boolean = false,
    ) {
        val tile: Tile
            get() = Tile(x = x, z = z, height = height)
    }

    private companion object {
        private val VIEW_STATE_ATTR = AttributeKey<String>(temp = true)
        private val HOME_TILE = Tile(x = 2606, z = 3093, height = 0)

        private const val DIRECTORY_INTERFACE = 597
        private const val TITLE_COMPONENT = 4
        private const val CLOSE_COMPONENT = 3
        private const val DESTINATION_PAGE_SIZE = 14

        private const val CATEGORY_CITIES = "Cities"

        private const val SCREEN_CATEGORIES = "categories"
        private const val SCREEN_DESTINATIONS = "destinations"

        private const val ACTION_NONE = "none"
        private const val ACTION_CATEGORY = "category"
        private const val ACTION_TELEPORT = "teleport"
        private const val ACTION_CATEGORIES = "categories"
        private const val ACTION_PREVIOUS_PAGE = "previous"
        private const val ACTION_NEXT_PAGE = "next"

        private val SLOT_COMPONENTS = listOf(5, 9, 13, 17, 21, 25, 29, 33, 37, 41, 45, 49, 53, 57, 61, 62, 63, 73)
        private val LABEL_COMPONENTS = listOf(8, 12, 16, 20, 24, 28, 32, 36, 40, 44, 48, 52, 56, 60, 66, 69, 72, 76)
        private val SUBLABEL_COMPONENTS = listOf(7, 11, 15, 19, 23, 27, 31, 35, 39, 43, 47, 51, 55, 59, 65, 68, 71, 75)

        private val CATEGORY_OPTIONS =
            listOf(
                CATEGORY_CITIES,
                "Skilling",
                "Dungeons",
                "Monsters",
                "Bosses",
                "Minigames",
                "Wilderness",
            )
    }
}
