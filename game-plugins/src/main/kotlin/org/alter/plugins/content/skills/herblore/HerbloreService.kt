package org.alter.plugins.content.skills.herblore

import gg.rsmod.util.ServerProperties
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import org.alter.game.Server
import org.alter.game.model.World
import org.alter.game.service.Service
import org.alter.plugins.content.skills.core.SkillJson

class HerbloreService : Service {

    val cleaningEntries: ObjectArrayList<HerbCleaningEntry> = ObjectArrayList()
    val unfinishedEntries: ObjectArrayList<UnfinishedPotionEntry> = ObjectArrayList()
    val finishedEntries: ObjectArrayList<FinishedPotionEntry> = ObjectArrayList()

    override fun init(server: Server, world: World, serviceProperties: ServerProperties) {
        cleaningEntries.clear()
        unfinishedEntries.clear()
        finishedEntries.clear()

        cleaningEntries.addAll(SkillJson.loadList(serviceProperties, "herblore.cleaning", "../data/cfg/herblore/cleaning.json"))
        unfinishedEntries.addAll(SkillJson.loadList(serviceProperties, "herblore.unfinished", "../data/cfg/herblore/unfinished.json"))
        finishedEntries.addAll(SkillJson.loadList(serviceProperties, "herblore.finished", "../data/cfg/herblore/finished.json"))

        SkillJson.logLoaded("herblore cleaning definition", cleaningEntries.size)
        SkillJson.logLoaded("herblore unfinished potion definition", unfinishedEntries.size)
        SkillJson.logLoaded("herblore finished potion definition", finishedEntries.size)
    }
}
