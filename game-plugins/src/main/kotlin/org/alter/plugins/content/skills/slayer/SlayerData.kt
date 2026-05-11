package org.alter.plugins.content.skills.slayer

data class SlayerMasterEntry(
    val npc: String,
    val name: String,
    val minLevel: Int,
    val taskNames: List<String>,
    val minAmount: Int,
    val maxAmount: Int,
) {
    lateinit var taskKeys: Set<String>
    var npcId: Int = -1
}

data class SlayerTaskEntry(
    val name: String,
    val npcs: List<String>,
    val minLevel: Int,
    val experience: Double,
    val points: Int,
    val weight: Int,
) {
    lateinit var npcKeys: Set<String>
    lateinit var npcIds: Set<Int>
}
