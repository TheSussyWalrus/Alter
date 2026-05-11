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
    val jewelryEntries: ObjectArrayList<CraftingJewelryEntry> = ObjectArrayList()
    val stringEntries: ObjectArrayList<CraftingStringEntry> = ObjectArrayList()
    val spinningEntries: ObjectArrayList<CraftingSpinningEntry> = ObjectArrayList()
    val potteryEntries: ObjectArrayList<CraftingPotteryEntry> = ObjectArrayList()
    val potteryFireEntries: ObjectArrayList<CraftingPotteryFireEntry> = ObjectArrayList()

    override fun init(server: Server, world: World, serviceProperties: ServerProperties) {
        gemEntries.clear()
        leatherEntries.clear()
        jewelryEntries.clear()
        stringEntries.clear()
        spinningEntries.clear()
        potteryEntries.clear()
        potteryFireEntries.clear()

        gemEntries.addAll(SkillJson.loadList(serviceProperties, "crafting.gems", "../data/cfg/crafting/gems.json"))
        leatherEntries.addAll(SkillJson.loadList(serviceProperties, "crafting.leather", "../data/cfg/crafting/leather.json"))
        jewelryEntries.addAll(SkillJson.loadList(serviceProperties, "crafting.jewelry", "../data/cfg/crafting/jewelry.json"))
        stringEntries.addAll(SkillJson.loadList(serviceProperties, "crafting.stringing", "../data/cfg/crafting/stringing.json"))
        spinningEntries.addAll(SkillJson.loadList(serviceProperties, "crafting.spinning", "../data/cfg/crafting/spinning.json"))
        potteryEntries.addAll(SkillJson.loadList(serviceProperties, "crafting.pottery", "../data/cfg/crafting/pottery.json"))
        potteryFireEntries.addAll(SkillJson.loadList(serviceProperties, "crafting.potteryFire", "../data/cfg/crafting/pottery_fire.json"))

        SkillJson.logLoaded("crafting gem definition", gemEntries.size)
        SkillJson.logLoaded("crafting leather definition", leatherEntries.size)
        SkillJson.logLoaded("crafting jewellery definition", jewelryEntries.size)
        SkillJson.logLoaded("crafting string definition", stringEntries.size)
        SkillJson.logLoaded("crafting spinning definition", spinningEntries.size)
        SkillJson.logLoaded("crafting pottery definition", potteryEntries.size)
        SkillJson.logLoaded("crafting pottery fire definition", potteryFireEntries.size)
    }
}
