package org.alter.plugins.content.skills.cooking

import gg.rsmod.util.ServerProperties
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import org.alter.game.Server
import org.alter.game.model.World
import org.alter.game.service.Service
import org.alter.plugins.content.skills.core.SkillJson
import org.alter.rscm.RSCM.getRSCM

class CookingService : Service {

    val entries: ObjectArrayList<CookingEntry> = ObjectArrayList()

    private val entriesByRaw: Int2ObjectOpenHashMap<CookingEntry> = Int2ObjectOpenHashMap()

    override fun init(server: Server, world: World, serviceProperties: ServerProperties) {
        entries.addAll(SkillJson.loadList(serviceProperties, "cooking", "../data/cfg/cooking/recipes.json"))
        entries.forEach { entry -> entriesByRaw.put(getRSCM(entry.raw), entry) }
        SkillJson.logLoaded("cooking definition", entries.size)
    }

    fun lookup(itemId: Int): CookingEntry? = entriesByRaw[itemId]
}
