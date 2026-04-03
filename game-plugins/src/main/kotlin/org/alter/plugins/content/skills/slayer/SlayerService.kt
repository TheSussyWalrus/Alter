package org.alter.plugins.content.skills.slayer

import gg.rsmod.util.ServerProperties
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import org.alter.game.Server
import org.alter.game.model.World
import org.alter.game.model.entity.Player
import org.alter.game.service.Service
import org.alter.plugins.content.skills.core.SkillJson
import org.alter.rscm.RSCM.getRSCM

class SlayerService : Service {

    val masters: MutableList<SlayerMasterEntry> = mutableListOf()
    val tasks: MutableList<SlayerTaskEntry> = mutableListOf()

    private val mastersByNpc: Int2ObjectOpenHashMap<SlayerMasterEntry> = Int2ObjectOpenHashMap()
    private val tasksByName: MutableMap<String, SlayerTaskEntry> = mutableMapOf()
    private val tasksByNpc: Int2ObjectOpenHashMap<SlayerTaskEntry> = Int2ObjectOpenHashMap()

    override fun init(server: Server, world: World, serviceProperties: ServerProperties) {
        masters += SkillJson.loadList(serviceProperties, "slayer.masters", "../data/cfg/slayer/slayer_masters.json")
        tasks += SkillJson.loadList(serviceProperties, "slayer.tasks", "../data/cfg/slayer/slayer_tasks.json")

        tasks.forEach { task ->
            task.npcKeys = task.npcs.map { "npc.$it" }.toSet()
            task.npcIds = task.npcKeys.map { getRSCM(it) }.toSet()
            tasksByName[task.name.lowercase()] = task
            task.npcIds.forEach { npcId -> tasksByNpc[npcId] = task }
        }

        masters.forEach { master ->
            master.npcId = getRSCM(master.npc)
            master.taskKeys = master.taskNames.map { it.lowercase() }.toSet()
            mastersByNpc[master.npcId] = master
        }

        SkillJson.logLoaded("slayer master", masters.size)
        SkillJson.logLoaded("slayer task", tasks.size)
    }

    fun masterFor(npcId: Int): SlayerMasterEntry? = mastersByNpc[npcId]

    fun taskFor(name: String): SlayerTaskEntry? = tasksByName[name.lowercase()]

    fun taskFor(npcId: Int): SlayerTaskEntry? = tasksByNpc[npcId]

    fun eligibleTasks(master: SlayerMasterEntry, player: Player): List<SlayerTaskEntry> {
        val slayerLevel = player.getSkills().getBaseLevel(org.alter.api.Skills.SLAYER)
        return master.taskKeys.mapNotNull(::taskFor).filter { slayerLevel >= it.minLevel }
    }
}
