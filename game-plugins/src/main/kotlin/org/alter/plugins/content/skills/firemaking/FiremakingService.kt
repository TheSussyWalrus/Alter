package org.alter.plugins.content.skills.firemaking

import gg.rsmod.util.ServerProperties
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import org.alter.game.Server
import org.alter.game.model.World
import org.alter.game.service.Service
import org.alter.plugins.content.skills.core.SkillJson
import org.alter.rscm.RSCM.getRSCM

class FiremakingService : Service {

    val entries: ObjectArrayList<FiremakingEntry> = ObjectArrayList()

    private val entriesByLog: Int2ObjectOpenHashMap<FiremakingEntry> = Int2ObjectOpenHashMap()

    override fun init(server: Server, world: World, serviceProperties: ServerProperties) {
        entries.addAll(SkillJson.loadList(serviceProperties, "firemaking", "../data/cfg/firemaking/logs.json"))
        entries.forEach { entry -> entriesByLog.put(getRSCM(entry.log), entry) }
        SkillJson.logLoaded("firemaking definition", entries.size)
    }

    fun lookup(itemId: Int): FiremakingEntry? = entriesByLog[itemId]
}
