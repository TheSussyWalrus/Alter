package org.alter.plugins.content.skills.woodcutting

import gg.rsmod.util.ServerProperties
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import org.alter.game.Server
import org.alter.game.model.World
import org.alter.game.service.Service
import org.alter.plugins.content.skills.core.SkillJson
import org.alter.rscm.RSCM.getRSCM

class WoodcuttingService : Service {

    val entries: ObjectArrayList<WoodcuttingEntry> = ObjectArrayList()

    private val entriesByObject: Int2ObjectOpenHashMap<WoodcuttingEntry> = Int2ObjectOpenHashMap()

    override fun init(server: Server, world: World, serviceProperties: ServerProperties) {
        entries.addAll(SkillJson.loadList(serviceProperties, "woodcutting", "../data/cfg/woodcutting/trees.json"))

        entries.forEach { entry ->
            entry.objectIds = entry.objects.map { getRSCM(it) }.toIntArray()
            entry.depletedObjectId = getRSCM(entry.depletedObject)
            entry.objectIds.forEach { id -> entriesByObject.put(id, entry) }
        }

        SkillJson.logLoaded("woodcutting definition", entries.size)
    }

    fun lookup(objectId: Int): WoodcuttingEntry? = entriesByObject[objectId]
}
