package org.alter.plugins.content.skills.fishing

import gg.rsmod.util.ServerProperties
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import org.alter.game.Server
import org.alter.game.model.World
import org.alter.game.service.Service
import org.alter.plugins.content.skills.core.SkillJson
import org.alter.rscm.RSCM.getRSCM

class FishingService : Service {

    val entries: ObjectArrayList<FishingEntry> = ObjectArrayList()

    private val entriesByNpc: Int2ObjectOpenHashMap<ObjectArrayList<FishingEntry>> = Int2ObjectOpenHashMap()

    override fun init(server: Server, world: World, serviceProperties: ServerProperties) {
        entries.addAll(SkillJson.loadList(serviceProperties, "fishing", "../data/cfg/fishing/spots.json"))

        entries.forEach { entry ->
            entry.npcIds = entry.npcs.map { getRSCM(it) }.toIntArray()
            entry.npcIds.forEach { id ->
                val list = entriesByNpc.getOrDefault(id, ObjectArrayList())
                list.add(entry)
                entriesByNpc.put(id, list)
            }
        }

        SkillJson.logLoaded("fishing definition", entries.size)
    }

    fun lookup(npcId: Int): List<FishingEntry> = entriesByNpc[npcId] ?: emptyList()
}
