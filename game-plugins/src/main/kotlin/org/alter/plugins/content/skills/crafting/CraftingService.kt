package org.alter.plugins.content.skills.crafting

import gg.rsmod.util.ServerProperties
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import org.alter.game.Server
import org.alter.game.model.World
import org.alter.game.service.Service
import org.alter.plugins.content.skills.core.SkillJson

class CraftingService : Service {

    val gemEntries: ObjectArrayList<CraftingGemEntry> = ObjectArrayList()
    val leatherEntries: ObjectArrayList<CraftingLeatherEntry> = ObjectArrayList()

    override fun init(server: Server, world: World, serviceProperties: ServerProperties) {
        gemEntries.clear()
        leatherEntries.clear()

        gemEntries.addAll(SkillJson.loadList(serviceProperties, "crafting.gems", "../data/cfg/crafting/gems.json"))
        leatherEntries.addAll(SkillJson.loadList(serviceProperties, "crafting.leather", "../data/cfg/crafting/leather.json"))

        SkillJson.logLoaded("crafting gem definition", gemEntries.size)
        SkillJson.logLoaded("crafting leather definition", leatherEntries.size)
    }
}
