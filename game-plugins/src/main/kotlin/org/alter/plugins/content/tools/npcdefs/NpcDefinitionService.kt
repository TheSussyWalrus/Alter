package org.alter.plugins.content.tools.npcdefs

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import dev.openrune.cache.CacheManager.getItem
import dev.openrune.cache.CacheManager.getItems
import dev.openrune.cache.CacheManager.itemSize
import dev.openrune.cache.CacheManager.getNpc
import dev.openrune.cache.CacheManager.npcSize
import gg.rsmod.util.ServerProperties
import org.alter.api.ext.message
import org.alter.api.ext.openShop
import org.alter.game.Server
import org.alter.game.model.World
import org.alter.game.model.attr.AttributeKey
import org.alter.game.model.attr.INTERACTING_NPC_ATTR
import org.alter.game.model.combat.NpcCombatDef
import org.alter.game.model.entity.Npc
import org.alter.game.model.entity.Player
import org.alter.game.model.shop.PurchasePolicy
import org.alter.game.model.shop.Shop
import org.alter.game.model.shop.ShopItem
import org.alter.game.model.shop.StockType
import org.alter.game.service.Service
import org.alter.game.model.weightedTableBuilder.Loot
import org.alter.game.model.weightedTableBuilder.LootTable
import org.alter.game.model.weightedTableBuilder.TableType
import org.alter.plugins.content.mechanics.shops.ItemCurrency
import java.net.URI
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.Locale

class NpcDefinitionService : Service {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val baseCombatDefs: MutableMap<Int, NpcCombatDef?> = mutableMapOf()
    private val appliedCombatDefs: MutableSet<Int> = mutableSetOf()
    private val baseShops: MutableMap<String, Shop?> = mutableMapOf()
    private val appliedShops: MutableSet<String> = mutableSetOf()
    private val attemptedShopBindings: MutableSet<Int> = mutableSetOf()
    private val itemSearchIndex: MutableList<IndexedItem> = mutableListOf()
    private val itemNameIndex: MutableMap<Int, String> = mutableMapOf()

    lateinit var definitionsPath: Path
        private set

    lateinit var shopsPath: Path
        private set

    lateinit var imageCacheDir: Path
        private set

    val definitions: MutableList<NpcDefinitionEntry> = mutableListOf()
    val shops: MutableList<NpcShopDefinition> = mutableListOf()

    var definitionsDirty: Boolean = false
        private set

    var shopsDirty: Boolean = false
        private set

    val dirty: Boolean
        get() = definitionsDirty || shopsDirty

    override fun init(server: Server, world: World, serviceProperties: ServerProperties) {
        definitionsPath = resolveConfigPath(serviceProperties.get("npc.definitions") ?: DEFAULT_DEFINITIONS_CONFIG)
        shopsPath = resolveConfigPath(serviceProperties.get("npc.shops") ?: DEFAULT_SHOPS_CONFIG)
        imageCacheDir = resolveConfigPath(serviceProperties.get("npc.image-cache") ?: DEFAULT_IMAGE_CACHE)
        buildItemSearchIndex()
        loadFromDisk()
        Server.logger.info {
            "Loaded ${definitions.size} NPC definition${if (definitions.size == 1) "" else "s"} and " +
                "${shops.size} NPC shop definition${if (shops.size == 1) "" else "s"} with " +
                "${itemSearchIndex.size} indexed item${if (itemSearchIndex.size == 1) "" else "s"}."
        }
    }

    override fun postLoad(server: Server, world: World) {
        applyToWorld(world)
    }

    fun loadFromDisk() {
        loadDefinitions()
        loadShops()
    }

    fun reload(world: World? = null) {
        loadFromDisk()
        if (world != null) {
            applyToWorld(world)
        }
    }

    fun saveToDisk(): List<Path> =
        listOf(
            saveDefinitions(),
            saveShops(),
        )

    fun applyToWorld(world: World) {
        applyShops(world)
        applyDefinitions(world)
        bindShopOptions(world)
    }

    fun defaultDefinition(id: Int): NpcDefinitionEntry {
        requireValidNpcId(id)
        return NpcDefinitionEntry(
            id = id,
            name = npcName(id) ?: "Unknown NPC",
            combat = combatDefinitionFrom(worldCombatFallback = NpcCombatDef.DEFAULT),
            followRange = NpcCombatDef.DEFAULT.followRange,
        )
    }

    fun saveDefinitions(): Path {
        val sorted = definitions.map { normalizeDefinition(it.copyForEdit()) }.sortedBy { it.id }
        writeJson(definitionsPath, sorted)
        definitions.clear()
        definitions.addAll(sorted)
        definitionsDirty = false
        return definitionsPath
    }

    fun saveShops(): Path {
        val sorted = shops.map { normalizeShop(it.copyForEdit()) }.sortedBy { it.id }
        writeJson(shopsPath, sorted)
        shops.clear()
        shops.addAll(sorted)
        shopsDirty = false
        return shopsPath
    }

    fun listDefinitions(): List<NpcDefinitionEntry> = definitions.map { it.copyForEdit() }

    fun getDefinition(id: Int): NpcDefinitionEntry? = definitions.firstOrNull { it.id == id }?.copyForEdit()

    fun upsertDefinition(entry: NpcDefinitionEntry, world: World? = null): NpcDefinitionEntry {
        requireValidNpcId(entry.id)
        val normalized = normalizeDefinition(entry.copyForEdit())
        validateDefinition(normalized).also { require(it.valid) { it.errors.joinToString("; ") } }
        val index = definitions.indexOfFirst { it.id == normalized.id }
        if (index >= 0) {
            definitions[index] = normalized
        } else {
            definitions.add(normalized)
        }
        definitions.sortBy { it.id }
        definitionsDirty = true
        if (world != null) {
            applyDefinition(world, normalized)
            refreshLiveNpcs(world, setOf(normalized.id))
            bindShopOptions(world)
        }
        return normalized.copyForEdit()
    }

    fun deleteDefinition(id: Int): Boolean {
        val removed = definitions.removeIf { it.id == id }
        if (removed) {
            definitionsDirty = true
        }
        return removed
    }

    fun listShops(): List<NpcShopDefinition> = shops.map { it.copyForEdit() }

    fun getShop(id: String): NpcShopDefinition? = shops.firstOrNull { it.id == id }?.copyForEdit()

    fun upsertShop(shop: NpcShopDefinition, world: World? = null): NpcShopDefinition {
        val normalized = normalizeShop(shop.copyForEdit())
        val validation = validateShop(normalized)
        require(validation.valid) { validation.errors.joinToString("; ") }
        val index = shops.indexOfFirst { it.id == normalized.id }
        if (index >= 0) {
            shops[index] = normalized
        } else {
            shops.add(normalized)
        }
        shops.sortBy { it.id }
        shopsDirty = true
        if (world != null) {
            applyShop(world, normalized)
            bindShopOptions(world)
        }
        return normalized.copyForEdit()
    }

    fun deleteShop(id: String): Boolean {
        val removed = shops.removeIf { it.id == id }
        if (removed) {
            shopsDirty = true
        }
        return removed
    }

    fun validateDefinition(entry: NpcDefinitionEntry): NpcDefinitionValidationResult {
        val errors = mutableListOf<String>()
        if (!isValidNpc(entry.id)) {
            errors.add("NPC id ${entry.id} does not exist in cache definitions.")
        }
        if (entry.followRange < 0) {
            errors.add("followRange must be zero or greater.")
        }
        if (entry.aggression.radius < 0) {
            errors.add("aggression.radius must be zero or greater.")
        }
        if (entry.drops.mainEmptySlots < 0) {
            errors.add("drops.mainEmptySlots must be zero or greater.")
        }
        if (entry.shopKey != null && shops.none { it.id == entry.shopKey }) {
            errors.add("shopKey '${entry.shopKey}' does not match a configured NPC shop.")
        }
        validateDrops("drops.always", entry.drops.always, errors)
        validateDrops("drops.main", entry.drops.main, errors)
        validateDrops("drops.preroll", entry.drops.preroll, errors)
        validateDrops("drops.tertiary", entry.drops.tertiary, errors)
        return NpcDefinitionValidationResult(errors.isEmpty(), errors)
    }

    fun validateShop(shop: NpcShopDefinition): NpcDefinitionValidationResult {
        val errors = mutableListOf<String>()
        if (shop.id.isBlank()) {
            errors.add("Shop id is required.")
        }
        shop.npcIds.filterNot(::isValidNpc).distinct().forEach { npcId ->
            errors.add("Shop references NPC id $npcId, which does not exist in cache definitions.")
        }
        shop.items.forEachIndexed { index, item ->
            if (item.itemId < 0) {
                errors.add("items[$index].itemId must be zero or greater.")
            } else if (!isValidItem(item.itemId)) {
                errors.add("items[$index].itemId ${item.itemId} does not exist in cache definitions.")
            }
            if (item.amount < 0) {
                errors.add("items[$index].amount must be zero or greater.")
            }
            if (item.price < 0) {
                errors.add("items[$index].price must be zero or greater.")
            }
        }
        return NpcDefinitionValidationResult(errors.isEmpty(), errors)
    }

    fun validateAll(): NpcDefinitionValidationResult {
        val errors =
            definitions.flatMap { definition ->
                validateDefinition(definition).errors.map { "npc[${definition.id}]: $it" }
            } +
                shops.flatMap { shop ->
                    validateShop(shop).errors.map { "shop[${shop.id}]: $it" }
                }
        return NpcDefinitionValidationResult(errors.isEmpty(), errors)
    }

    fun applySpawnOverrides(npc: Npc, spawn: org.alter.plugins.content.tools.npcspawns.NpcSpawnEntry): Boolean {
        val current = npc.combatDef
        val aggressive = spawn.aggressive
        val aggressionRadius = spawn.aggressionRadius
        val followRange = spawn.followRange
        var combatChanged = false
        if (aggressive != null || aggressionRadius != null || followRange != null) {
            val enablesAggression = aggressive == true || aggressionRadius != null
            npc.combatDef =
                current.copy(
                    aggressiveRadius =
                        when {
                            aggressive == false -> 0
                            aggressionRadius != null -> aggressionRadius.coerceAtLeast(0)
                            enablesAggression && current.aggressiveRadius <= 0 -> DEFAULT_AGGRESSION_RADIUS
                            else -> current.aggressiveRadius
                        },
                    aggroTargetDelay =
                        when {
                            aggressive == false -> 0
                            enablesAggression && current.aggroTargetDelay <= 0 -> DEFAULT_AGGRESSION_SEARCH_DELAY
                            else -> current.aggroTargetDelay
                        },
                    aggressiveTimer =
                        when {
                            aggressive == false -> Int.MIN_VALUE
                            enablesAggression && current.aggressiveTimer <= 0 -> DEFAULT_AGGRESSION_TIMER
                            else -> current.aggressiveTimer
                        },
                    followRange = followRange?.coerceAtLeast(0) ?: current.followRange,
                )
            combatChanged = true
        }
        val shopKey = spawn.shopKey?.trim()?.takeIf { it.isNotBlank() } ?: shopKeyForNpc(npc.id)
        if (shopKey != null) {
            npc.attr[NPC_SHOP_KEY_ATTR] = shopKey
        }
        return combatChanged
    }

    fun shopKeyForNpc(npcId: Int): String? =
        definitions.firstOrNull { it.id == npcId }?.shopKey?.takeIf { getShop(it) != null }
            ?: shops.firstOrNull { npcId in it.npcIds }?.id

    fun imageContent(npcId: Int): NpcImageContent? {
        val definition = definitions.firstOrNull { it.id == npcId } ?: return null
        val imageUrl = definition.imageUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: return null
        Files.createDirectories(imageCacheDir)
        val extension = imageUrl.substringBefore('?').substringAfterLast('.', "img").lowercase(Locale.ROOT).takeIf { it.length in 2..5 } ?: "img"
        val cacheName = "${npcId}_${imageUrl.hashCode().toUInt().toString(16)}.$extension"
        val imagePath = imageCacheDir.resolve(cacheName)
        if (!Files.exists(imagePath)) {
            val connection = URI(imageUrl).toURL().openConnection()
            connection.connectTimeout = IMAGE_TIMEOUT_MS
            connection.readTimeout = IMAGE_TIMEOUT_MS
            connection.getInputStream().use { input ->
                Files.copy(input, imagePath, StandardCopyOption.REPLACE_EXISTING)
            }
        }
        return NpcImageContent(Files.readAllBytes(imagePath), contentTypeFor(extension))
    }

    fun isValidNpc(id: Int): Boolean = npcName(id) != null

    fun isValidItem(id: Int): Boolean =
        id >= 0 && runCatching { getItem(id) }.isSuccess

    fun itemName(id: Int): String? {
        itemNameIndex[id]?.let { return it }
        if (id < 0 || id >= itemSize()) {
            return null
        }
        val item = runCatching { getItem(id) }.getOrNull() ?: return null
        if (item.name.isBlank()) {
            return null
        }
        return if (item.noteTemplateId > 0) {
            val unnoted = runCatching { getItem(item.noteLinkId).name }.getOrDefault(item.name)
            "$unnoted (noted)"
        } else {
            item.name
        }
    }

    fun npcName(id: Int): String? {
        if (id < 0 || id >= npcSize()) {
            return null
        }
        return runCatching { getNpc(id).name }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    fun searchItems(query: String, limit: Int = 60): List<NpcItemSearchResult> {
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        if (normalizedQuery.isBlank()) {
            return emptyList()
        }
        val exactId = normalizedQuery.toIntOrNull()
        return itemSearchIndex
            .asSequence()
            .filter { item ->
                item.id == exactId || item.normalizedName.contains(normalizedQuery)
            }
            .sortedWith(
                compareBy<IndexedItem> { it.searchRank(normalizedQuery, exactId) }
                    .thenBy { it.name.length }
                    .thenBy { it.name }
                    .thenBy { it.id },
            )
            .take(limit)
            .map { item ->
                NpcItemSearchResult(
                    id = item.id,
                    name = item.name,
                    noted = item.noted,
                    placeholder = item.placeholder,
                    stackable = item.stackable,
                )
            }
            .toList()
    }

    fun searchNpcs(query: String, limit: Int = 60): List<NpcDefinitionSearchResult> {
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        if (normalizedQuery.isBlank()) {
            return emptyList()
        }
        val definedIds = definitions.map { it.id }.toSet()
        return (0 until npcSize())
            .asSequence()
            .mapNotNull { id ->
                val name = npcName(id) ?: return@mapNotNull null
                if (name.lowercase(Locale.ROOT).contains(normalizedQuery)) {
                    NpcDefinitionSearchResult(id, name, id in definedIds)
                } else {
                    null
                }
            }
            .take(limit)
            .toList()
    }

    private fun applyDefinitions(world: World) {
        val currentIds = definitions.map { it.id }.toSet()
        (appliedCombatDefs - currentIds).forEach { id ->
            val base = baseCombatDefs[id]
            if (base == null) {
                world.plugins.npcCombatDefs.remove(id)
            } else {
                world.plugins.npcCombatDefs[id] = base
            }
        }
        appliedCombatDefs.retainAll(currentIds)
        definitions.forEach { applyDefinition(world, it) }
        refreshLiveNpcs(world, currentIds)
    }

    private fun applyDefinition(world: World, entry: NpcDefinitionEntry) {
        if (!baseCombatDefs.containsKey(entry.id)) {
            baseCombatDefs[entry.id] = world.plugins.npcCombatDefs.getOrDefault(entry.id, null)
        }
        val base = baseCombatDefs[entry.id] ?: world.plugins.npcCombatDefs.getOrDefault(entry.id, null) ?: NpcCombatDef.DEFAULT
        world.plugins.npcCombatDefs[entry.id] = entry.toCombatDef(base)
        appliedCombatDefs.add(entry.id)
    }

    private fun applyShops(world: World) {
        val currentIds = shops.map { it.id }.toSet()
        (appliedShops - currentIds).forEach { id ->
            val base = baseShops[id]
            if (base == null) {
                world.plugins.shops.remove(id)
            } else {
                world.plugins.shops[id] = base
            }
        }
        appliedShops.retainAll(currentIds)
        shops.forEach { applyShop(world, it) }
    }

    private fun applyShop(world: World, shop: NpcShopDefinition) {
        if (!baseShops.containsKey(shop.id)) {
            baseShops[shop.id] = world.plugins.shops.getOrDefault(shop.id, null)
        }
        val stockSize = shop.items.size.coerceAtLeast(Shop.DEFAULT_STOCK_SIZE)
        val built =
            Shop(
                name = shop.name.ifBlank { shop.id },
                stockType = if (shop.sellsItems) StockType.NORMAL else StockType.INFINITE,
                purchasePolicy = if (shop.buysItems) PurchasePolicy.BUY_TRADEABLES else PurchasePolicy.BUY_NONE,
                currency = ItemCurrency(shop.currencyItemId, currencyName(shop.currencyItemId, singular = true), currencyName(shop.currencyItemId, singular = false)),
                items = arrayOfNulls(stockSize),
            )
        shop.items.take(stockSize).forEachIndexed { index, item ->
            built.items[index] =
                ShopItem(
                    item = item.itemId,
                    amount = if (shop.sellsItems) item.amount else 0,
                    sellPrice = item.price.takeIf { it > 0 },
                    buyPrice = item.buyPrice?.takeIf { it >= 0 },
                    resupplyCycles = item.restockTicks ?: shop.restockTicks.coerceAtLeast(1),
                )
        }
        world.plugins.shops[shop.id] = built
        appliedShops.add(shop.id)
    }

    private fun bindShopOptions(world: World) {
        val npcIds = (definitions.map { it.id } + shops.flatMap { it.npcIds }).toSet()
        npcIds.forEach { npcId ->
            if (!attemptedShopBindings.add(npcId)) {
                return@forEach
            }
            val option = tradeOption(npcId) ?: return@forEach
            try {
                val service = this
                world.plugins.bindNpc(npcId, option + 1) {
                    val player = ctx as? Player ?: return@bindNpc
                    val clickedNpc = player.attr[INTERACTING_NPC_ATTR]?.get()
                    val shopKey = clickedNpc?.attr?.get(NPC_SHOP_KEY_ATTR) ?: service.shopKeyForNpc(npcId)
                    if (shopKey == null || world.getShop(shopKey) == null) {
                        player.message("This NPC does not have a configured shop.")
                        return@bindNpc
                    }
                    player.openShop(shopKey)
                }
            } catch (t: Throwable) {
                Server.logger.warn(t) { "Skipping JSON shop option binding for npc=$npcId because another plugin already owns the option." }
            }
        }
    }

    private fun refreshLiveNpcs(world: World, ids: Set<Int>) {
        world.npcs.forEach { npc ->
            if (npc.id in ids && npc.isSpawned()) {
                npc.aggroCheck = null
                world.setNpcDefaults(npc)
                npc.attr[NPC_SHOP_KEY_ATTR] = shopKeyForNpc(npc.id) ?: ""
                world.plugins.executeNpcSpawn(npc)
            }
        }
    }

    fun normalizeDefinition(entry: NpcDefinitionEntry): NpcDefinitionEntry {
        entry.name = npcName(entry.id) ?: entry.name.ifBlank { "Unknown NPC" }
        entry.imageUrl = entry.imageUrl?.trim()?.takeIf { it.isNotBlank() }
        entry.shopKey = entry.shopKey?.trim()?.takeIf { it.isNotBlank() }
        entry.tags = normalizeTags(entry.tags)
        entry.notes = entry.notes?.trim()?.takeIf { it.isNotBlank() }
        entry.followRange = entry.followRange.coerceAtLeast(0)
        entry.aggression.radius = entry.aggression.radius.coerceAtLeast(0)
        entry.aggression.searchDelay = entry.aggression.searchDelay.coerceAtLeast(1)
        entry.aggression.toleranceTicks = entry.aggression.toleranceTicks?.coerceAtLeast(0)
        entry.drops.mainEmptySlots = entry.drops.mainEmptySlots.coerceAtLeast(0)
        normalizeDrops(entry.drops.always)
        normalizeDrops(entry.drops.main)
        normalizeDrops(entry.drops.preroll)
        normalizeDrops(entry.drops.tertiary)
        return entry
    }

    fun normalizeShop(shop: NpcShopDefinition): NpcShopDefinition {
        shop.id = shop.id.trim()
        shop.name = shop.name.trim().ifBlank { shop.id }
        shop.npcIds = shop.npcIds.distinct().sorted().toMutableList()
        shop.currencyItemId = shop.currencyItemId.coerceAtLeast(0)
        shop.restockTicks = shop.restockTicks.coerceAtLeast(0)
        shop.tags = normalizeTags(shop.tags)
        shop.notes = shop.notes?.trim()?.takeIf { it.isNotBlank() }
        shop.items =
            shop.items
                .map {
                    it.copy(
                        name = it.name?.trim()?.takeIf { name -> name.isNotBlank() },
                        amount = it.amount.coerceAtLeast(0),
                        price = it.price.coerceAtLeast(0),
                        buyPrice = it.buyPrice?.coerceAtLeast(0),
                        restockTicks = it.restockTicks?.coerceAtLeast(0),
                    )
                }
                .sortedWith(compareBy<NpcShopItemDefinition> { it.itemId }.thenBy { it.price }.thenBy { it.amount })
                .toMutableList()
        return shop
    }

    private fun NpcDefinitionEntry.toCombatDef(base: NpcCombatDef): NpcCombatDef {
        val stats = combat.stats
        val animations = combat.animations
        val sounds = combat.sounds
        val aggression = aggression
        val tableSet = drops.toLootTables()
        return base.copy(
            attack = stats.attack.takeIf { it > 0 } ?: base.attack,
            strength = stats.strength.takeIf { it > 0 } ?: base.strength,
            defence = stats.defence.takeIf { it > 0 } ?: base.defence,
            ranged = stats.ranged.takeIf { it > 0 } ?: base.ranged,
            magic = stats.magic.takeIf { it > 0 } ?: base.magic,
            hitpoints = stats.hitpoints.takeIf { it > 0 } ?: base.hitpoints,
            attackSpeed = combat.attackSpeed?.coerceAtLeast(1) ?: base.attackSpeed,
            attackAnimation = animations.attack ?: base.attackAnimation,
            blockAnimation = animations.block ?: base.blockAnimation,
            deathAnimation = animations.death?.let { listOf(it) } ?: base.deathAnimation,
            defaultAttackSound = sounds.attack ?: base.defaultAttackSound,
            defaultBlockSound = sounds.block ?: base.defaultBlockSound,
            defaultDeathSound = sounds.death ?: base.defaultDeathSound,
            respawnDelay = combat.respawnDelay?.coerceAtLeast(0) ?: base.respawnDelay,
            aggressiveRadius = if (aggression.aggressive) aggression.radius.coerceAtLeast(0) else 0,
            aggroTargetDelay = if (aggression.aggressive) aggression.searchDelay.coerceAtLeast(1) else 0,
            aggressiveTimer =
                when {
                    !aggression.aggressive -> Int.MIN_VALUE
                    aggression.alwaysAggressive -> Int.MAX_VALUE
                    aggression.toleranceTicks != null -> aggression.toleranceTicks!!.coerceAtLeast(0)
                    base.aggressiveTimer > 0 -> base.aggressiveTimer
                    else -> DEFAULT_AGGRESSION_TIMER
                },
            followRange = followRange.coerceAtLeast(0),
            poisonChance = combat.poisonChance?.coerceAtLeast(0.0) ?: base.poisonChance,
            venomChance = combat.venomChance?.coerceAtLeast(0.0) ?: base.venomChance,
            slayerReq = combat.slayerReq?.coerceAtLeast(1) ?: base.slayerReq,
            slayerXp = combat.slayerXp?.coerceAtLeast(0.0) ?: base.slayerXp,
            bonuses = combat.bonuses.toList(base.bonuses),
            LootTables = tableSet.ifEmpty { base.LootTables ?: mutableSetOf() },
            immunePoison = combat.immunePoison ?: base.immunePoison,
            immuneVenom = combat.immuneVenom ?: base.immuneVenom,
            immuneCannons = combat.immuneCannons ?: base.immuneCannons,
            immuneThralls = combat.immuneThralls ?: base.immuneThralls,
        )
    }

    private fun combatDefinitionFrom(worldCombatFallback: NpcCombatDef): NpcCombatDefinition =
        NpcCombatDefinition(
            stats =
                NpcCombatStats(
                    attack = worldCombatFallback.attack,
                    strength = worldCombatFallback.strength,
                    defence = worldCombatFallback.defence,
                    ranged = worldCombatFallback.ranged,
                    magic = worldCombatFallback.magic,
                    hitpoints = worldCombatFallback.hitpoints,
                ),
            bonuses = NpcCombatBonuses.fromList(worldCombatFallback.bonuses),
            animations =
                NpcCombatAnimations(
                    attack = worldCombatFallback.attackAnimation,
                    block = worldCombatFallback.blockAnimation,
                    death = worldCombatFallback.deathAnimation.firstOrNull(),
                ),
            sounds =
                NpcCombatSounds(
                    attack = worldCombatFallback.defaultAttackSound.takeIf { it >= 0 },
                    block = worldCombatFallback.defaultBlockSound.takeIf { it >= 0 },
                    death = worldCombatFallback.defaultDeathSound.takeIf { it >= 0 },
                ),
            attackSpeed = worldCombatFallback.attackSpeed,
            respawnDelay = worldCombatFallback.respawnDelay,
            slayerReq = worldCombatFallback.slayerReq,
            slayerXp = worldCombatFallback.slayerXp,
            poisonChance = worldCombatFallback.poisonChance,
            venomChance = worldCombatFallback.venomChance,
            immunePoison = worldCombatFallback.immunePoison,
            immuneVenom = worldCombatFallback.immuneVenom,
            immuneCannons = worldCombatFallback.immuneCannons,
            immuneThralls = worldCombatFallback.immuneThralls,
        )

    private fun NpcCombatBonuses.toList(base: List<Int>): List<Int> {
        val values = MutableList(14) { index -> base.getOrElse(index) { 0 } }
        values[0] = attackStab
        values[1] = attackSlash
        values[2] = attackCrush
        values[3] = attackMagic
        values[4] = attackRanged
        values[5] = defenceStab
        values[6] = defenceSlash
        values[7] = defenceCrush
        values[8] = defenceMagic
        values[9] = defenceRanged
        values[10] = meleeStrength
        values[11] = rangedStrength
        values[12] = magicDamage
        return values
    }

    private fun NpcDropDefinition.toLootTables(): MutableSet<LootTable> {
        val tables = mutableSetOf<LootTable>()
        if (always.isNotEmpty()) {
            tables.add(LootTable(TableType.ALWAYS, 0, always.map { it.toLoot(weighted = false) }.toMutableSet()))
        }
        if (main.isNotEmpty()) {
            val drops = main.map { it.toLoot(weighted = true) }.toMutableSet()
            val itemWeight = drops.sumOf { it.weight ?: 1 }.coerceAtLeast(1)
            tables.add(LootTable(TableType.MAIN, itemWeight + mainEmptySlots.coerceAtLeast(0), drops))
        } else if (mainEmptySlots > 0) {
            tables.add(LootTable(TableType.MAIN, mainEmptySlots.coerceAtLeast(1), mutableSetOf()))
        }
        if (preroll.isNotEmpty()) {
            tables.add(LootTable(TableType.PRE_ROLL, null, preroll.map { it.toLoot(weighted = true) }.toMutableSet()))
        }
        if (tertiary.isNotEmpty()) {
            tables.add(LootTable(TableType.TERTIARY, null, tertiary.map { it.toLoot(weighted = true) }.toMutableSet()))
        }
        return tables
    }

    private fun NpcDropEntry.toLoot(weighted: Boolean): Loot =
        Loot(
            item = itemId,
            min = minAmount.coerceAtLeast(1),
            max = maxAmount.coerceAtLeast(minAmount.coerceAtLeast(1)),
            weight = if (weighted) dropWeight() else null,
            announce = false,
        )

    private fun NpcDropEntry.dropWeight(): Int =
        weight ?: denominator?.let { denominator ->
            numerator?.takeIf { it > 0 }?.let { (denominator / it).coerceAtLeast(1) } ?: denominator
        } ?: chance?.takeIf { it > 0.0 }?.let { (1.0 / it).toInt().coerceAtLeast(1) } ?: 1

    private fun tradeOption(npcId: Int): Int? =
        runCatching {
            getNpc(npcId).actions.indexOfFirst { action ->
                val normalized = action?.lowercase(Locale.ROOT)?.replace("-", " ")?.trim()
                normalized == "trade" || normalized == "trade with" || normalized == "shop"
            }.takeIf { it >= 0 }
        }.getOrNull()

    private fun currencyName(itemId: Int, singular: Boolean): String {
        val name = runCatching { getItem(itemId).name.lowercase(Locale.ROOT) }.getOrDefault("coin")
        return if (singular) name.removeSuffix("s") else name
    }

    private fun contentTypeFor(extension: String): String =
        when (extension.lowercase(Locale.ROOT)) {
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            else -> "image/png"
        }

    private fun loadDefinitions() {
        definitions.clear()
        if (!Files.exists(definitionsPath)) {
            Files.createDirectories(definitionsPath.parent)
            definitionsDirty = false
            return
        }
        Files.newBufferedReader(definitionsPath).use { reader ->
            val listType = object : TypeToken<MutableList<NpcDefinitionEntry>>() {}.type
            val loaded = gson.fromJson<MutableList<NpcDefinitionEntry>>(reader, listType) ?: mutableListOf()
            definitions.addAll(loaded.map { normalizeDefinition(it) }.sortedBy { it.id })
        }
        definitionsDirty = false
    }

    private fun loadShops() {
        shops.clear()
        if (!Files.exists(shopsPath)) {
            Files.createDirectories(shopsPath.parent)
            shopsDirty = false
            return
        }
        Files.newBufferedReader(shopsPath).use { reader ->
            val listType = object : TypeToken<MutableList<NpcShopDefinition>>() {}.type
            val loaded = gson.fromJson<MutableList<NpcShopDefinition>>(reader, listType) ?: mutableListOf()
            shops.addAll(loaded.map { normalizeShop(it) }.sortedBy { it.id })
        }
        shopsDirty = false
    }

    private fun writeJson(path: Path, value: Any) {
        Files.createDirectories(path.parent)
        val temp = path.resolveSibling("${path.fileName}.tmp")
        Files.writeString(temp, gson.toJson(value))
        try {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun validateDrops(path: String, drops: List<NpcDropEntry>, errors: MutableList<String>) {
        drops.forEachIndexed { index, drop ->
            if (drop.itemId < 0) {
                errors.add("$path[$index].itemId must be zero or greater.")
            } else if (!isValidItem(drop.itemId)) {
                errors.add("$path[$index].itemId ${drop.itemId} does not exist in cache definitions.")
            }
            if (drop.minAmount < 0) {
                errors.add("$path[$index].minAmount must be zero or greater.")
            }
            if (drop.maxAmount < drop.minAmount) {
                errors.add("$path[$index].maxAmount must be greater than or equal to minAmount.")
            }
            if (drop.weight != null && drop.weight!! <= 0) {
                errors.add("$path[$index].weight must be greater than zero when provided.")
            }
            if (drop.denominator != null && drop.denominator!! <= 0) {
                errors.add("$path[$index].denominator must be greater than zero when provided.")
            }
            if (drop.numerator != null && drop.numerator!! < 0) {
                errors.add("$path[$index].numerator must be zero or greater when provided.")
            }
            if (drop.chance != null && (drop.chance!! < 0.0 || drop.chance!! > 1.0)) {
                errors.add("$path[$index].chance must be between 0.0 and 1.0 when provided.")
            }
        }
    }

    private fun normalizeDrops(drops: MutableList<NpcDropEntry>) {
        val normalized =
            drops
                .map {
                    it.copy(
                        name = itemName(it.itemId) ?: it.name?.trim()?.takeIf { name -> name.isNotBlank() },
                        minAmount = it.minAmount.coerceAtLeast(0),
                        maxAmount = it.maxAmount.coerceAtLeast(it.minAmount.coerceAtLeast(0)),
                        weight = it.weight?.coerceAtLeast(1),
                        chance = it.chance?.coerceIn(0.0, 1.0),
                        numerator = it.numerator?.coerceAtLeast(0),
                        denominator = it.denominator?.coerceAtLeast(1),
                    )
                }
                .sortedWith(compareBy<NpcDropEntry> { it.itemId }.thenBy { it.minAmount }.thenBy { it.maxAmount })
        drops.clear()
        drops.addAll(normalized)
    }

    private fun buildItemSearchIndex() {
        itemSearchIndex.clear()
        itemNameIndex.clear()
        val items = getItems()
        for (id in 0 until itemSize()) {
            val item = items[id] ?: continue
            if (item.isPlaceholder || item.name.isBlank()) {
                continue
            }
            val displayName =
                if (item.noteTemplateId > 0) {
                    val linkedName = itemNameIndex[item.noteLinkId] ?: runCatching { getItem(item.noteLinkId).name }.getOrDefault(item.name)
                    "$linkedName (noted)"
                } else {
                    item.name
                }
            val indexed =
                IndexedItem(
                    id = id,
                    name = displayName,
                    normalizedName = displayName.lowercase(Locale.ROOT),
                    noted = item.noted || item.noteTemplateId > 0,
                    placeholder = item.isPlaceholder,
                    stackable = item.stackable,
                )
            itemSearchIndex.add(indexed)
            itemNameIndex[id] = displayName
        }
    }

    private fun IndexedItem.searchRank(
        query: String,
        exactId: Int?,
    ): Int =
        when {
            id == exactId -> 0
            normalizedName == query -> 1
            normalizedName.startsWith(query) -> 2
            else -> 3
        }

    private fun normalizeTags(tags: List<String>): MutableList<String> =
        tags
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .toMutableList()

    private fun requireValidNpcId(id: Int) {
        require(isValidNpc(id)) { "NPC id $id does not exist in cache definitions." }
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

    private data class IndexedItem(
        val id: Int,
        val name: String,
        val normalizedName: String,
        val noted: Boolean,
        val placeholder: Boolean,
        val stackable: Boolean,
    )

    companion object {
        val NPC_SHOP_KEY_ATTR = AttributeKey<String>()
        private const val DEFAULT_DEFINITIONS_CONFIG = "data/cfg/npcs/definitions.json"
        private const val DEFAULT_SHOPS_CONFIG = "data/cfg/npcs/shops.json"
        private const val DEFAULT_IMAGE_CACHE = "data/cache/npc-images"
        private const val IMAGE_TIMEOUT_MS = 5000
        private const val DEFAULT_AGGRESSION_RADIUS = 1
        private const val DEFAULT_AGGRESSION_SEARCH_DELAY = 5
        private const val DEFAULT_AGGRESSION_TIMER = 1000
    }
}
