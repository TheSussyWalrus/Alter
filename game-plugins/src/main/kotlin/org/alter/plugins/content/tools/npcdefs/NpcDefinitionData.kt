package org.alter.plugins.content.tools.npcdefs

data class NpcDefinitionEntry(
    var id: Int = -1,
    var name: String = "",
    var imageUrl: String? = null,
    var shopKey: String? = null,
    var tags: MutableList<String> = mutableListOf(),
    var notes: String? = null,
    var combat: NpcCombatDefinition = NpcCombatDefinition(),
    var aggression: NpcAggressionDefinition = NpcAggressionDefinition(),
    var followRange: Int = 16,
    var drops: NpcDropDefinition = NpcDropDefinition(),
) {
    fun copyForEdit(): NpcDefinitionEntry =
        copy(
            tags = tags.toMutableList(),
            combat = combat.copyForEdit(),
            aggression = aggression.copy(),
            drops = drops.copyForEdit(),
        )
}

data class NpcCombatDefinition(
    var stats: NpcCombatStats = NpcCombatStats(),
    var bonuses: NpcCombatBonuses = NpcCombatBonuses(),
    var animations: NpcCombatAnimations = NpcCombatAnimations(),
    var sounds: NpcCombatSounds = NpcCombatSounds(),
    var attackSpeed: Int? = null,
    var respawnDelay: Int? = null,
    var slayerReq: Int? = null,
    var slayerXp: Double? = null,
    var poisonChance: Double? = null,
    var venomChance: Double? = null,
    var immunePoison: Boolean? = null,
    var immuneVenom: Boolean? = null,
    var immuneCannons: Boolean? = null,
    var immuneThralls: Boolean? = null,
) {
    fun copyForEdit(): NpcCombatDefinition =
        copy(
            stats = stats.copy(),
            bonuses = bonuses.copy(),
            animations = animations.copy(),
            sounds = sounds.copy(),
        )
}

data class NpcCombatStats(
    var attack: Int = 0,
    var strength: Int = 0,
    var defence: Int = 0,
    var ranged: Int = 0,
    var magic: Int = 0,
    var hitpoints: Int = 0,
)

data class NpcCombatBonuses(
    var attackStab: Int = 0,
    var attackSlash: Int = 0,
    var attackCrush: Int = 0,
    var attackMagic: Int = 0,
    var attackRanged: Int = 0,
    var defenceStab: Int = 0,
    var defenceSlash: Int = 0,
    var defenceCrush: Int = 0,
    var defenceMagic: Int = 0,
    var defenceRanged: Int = 0,
    var meleeStrength: Int = 0,
    var rangedStrength: Int = 0,
    var magicDamage: Int = 0,
) {
    companion object {
        fun fromList(values: List<Int>): NpcCombatBonuses =
            NpcCombatBonuses(
                attackStab = values.getOrElse(0) { 0 },
                attackSlash = values.getOrElse(1) { 0 },
                attackCrush = values.getOrElse(2) { 0 },
                attackMagic = values.getOrElse(3) { 0 },
                attackRanged = values.getOrElse(4) { 0 },
                defenceStab = values.getOrElse(5) { 0 },
                defenceSlash = values.getOrElse(6) { 0 },
                defenceCrush = values.getOrElse(7) { 0 },
                defenceMagic = values.getOrElse(8) { 0 },
                defenceRanged = values.getOrElse(9) { 0 },
                meleeStrength = values.getOrElse(10) { 0 },
                rangedStrength = values.getOrElse(11) { 0 },
                magicDamage = values.getOrElse(12) { 0 },
            )
    }
}

data class NpcCombatAnimations(
    var attack: Int? = null,
    var rangedAttack: Int? = null,
    var magicAttack: Int? = null,
    var block: Int? = null,
    var death: Int? = null,
)

data class NpcCombatSounds(
    var attack: Int? = null,
    var block: Int? = null,
    var death: Int? = null,
)

data class NpcAggressionDefinition(
    var aggressive: Boolean = false,
    var radius: Int = 0,
    var searchDelay: Int = 5,
    var toleranceTicks: Int? = null,
    var alwaysAggressive: Boolean = false,
    var retaliates: Boolean = true,
)

data class NpcDropDefinition(
    var always: MutableList<NpcDropEntry> = mutableListOf(),
    var main: MutableList<NpcDropEntry> = mutableListOf(),
    var mainEmptySlots: Int = 0,
    var preroll: MutableList<NpcDropEntry> = mutableListOf(),
    var tertiary: MutableList<NpcDropEntry> = mutableListOf(),
) {
    fun copyForEdit(): NpcDropDefinition =
        copy(
            always = always.map { it.copy() }.toMutableList(),
            main = main.map { it.copy() }.toMutableList(),
            preroll = preroll.map { it.copy() }.toMutableList(),
            tertiary = tertiary.map { it.copy() }.toMutableList(),
        )
}

data class NpcDropEntry(
    var itemId: Int = -1,
    var name: String? = null,
    var minAmount: Int = 1,
    var maxAmount: Int = 1,
    var weight: Int? = null,
    var chance: Double? = null,
    var numerator: Int? = null,
    var denominator: Int? = null,
    var noted: Boolean = false,
)

data class NpcShopDefinition(
    var id: String = "",
    var name: String = "",
    var npcIds: MutableList<Int> = mutableListOf(),
    var currencyItemId: Int = COINS_ITEM_ID,
    var buysItems: Boolean = true,
    var sellsItems: Boolean = true,
    var restockTicks: Int = 100,
    var tags: MutableList<String> = mutableListOf(),
    var notes: String? = null,
    var items: MutableList<NpcShopItemDefinition> = mutableListOf(),
) {
    fun copyForEdit(): NpcShopDefinition =
        copy(
            npcIds = npcIds.toMutableList(),
            tags = tags.toMutableList(),
            items = items.map { it.copy() }.toMutableList(),
        )

    companion object {
        const val COINS_ITEM_ID: Int = 995
    }
}

data class NpcShopItemDefinition(
    var itemId: Int = -1,
    var name: String? = null,
    var amount: Int = 1,
    var price: Int = 0,
    var buyPrice: Int? = null,
    var restockTicks: Int? = null,
)

data class NpcDefinitionSearchResult(
    val id: Int,
    val name: String,
    val defined: Boolean,
)

data class NpcItemSearchResult(
    val id: Int,
    val name: String,
    val noted: Boolean,
    val placeholder: Boolean,
    val stackable: Boolean,
)

data class NpcImageContent(
    val bytes: ByteArray,
    val contentType: String,
)

data class NpcDefinitionValidationResult(
    val valid: Boolean,
    val errors: List<String> = emptyList(),
)
