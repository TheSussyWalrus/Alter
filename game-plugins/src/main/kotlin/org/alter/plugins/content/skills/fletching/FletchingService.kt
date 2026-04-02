package org.alter.plugins.content.skills.fletching

import gg.rsmod.util.ServerProperties
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import org.alter.game.model.World
import org.alter.game.service.Service
import org.alter.game.Server
import org.alter.plugins.content.skills.core.SkillJson

class FletchingService : Service {

    val logEntries: ObjectArrayList<FletchingLogEntry> = ObjectArrayList()
    val stringEntries: ObjectArrayList<FletchingStringEntry> = ObjectArrayList()

    override fun init(server: Server, world: World, serviceProperties: ServerProperties) {
        logEntries.clear()
        stringEntries.clear()
        logEntries.addAll(SkillJson.loadList(serviceProperties, "fletching", "../data/cfg/fletching/logs.json"))
        stringEntries.addAll(SkillJson.loadList(serviceProperties, "fletching", "../data/cfg/fletching/strings.json"))
        SkillJson.logLoaded("fletching log definition", logEntries.size)
        SkillJson.logLoaded("fletching string definition", stringEntries.size)
    }
}
