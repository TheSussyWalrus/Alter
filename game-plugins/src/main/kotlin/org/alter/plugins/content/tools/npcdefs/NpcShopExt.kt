package org.alter.plugins.content.tools.npcdefs

import org.alter.api.ext.openShop
import org.alter.game.model.attr.INTERACTING_NPC_ATTR
import org.alter.game.model.entity.Player

fun Player.openNpcDefinedShopOrFallback(fallbackShop: String) {
    val service = world.getService(NpcDefinitionService::class.java)
    val clickedNpc = attr[INTERACTING_NPC_ATTR]?.get()
    val configuredShop =
        clickedNpc
            ?.attr
            ?.get(NpcDefinitionService.NPC_SHOP_KEY_ATTR)
            ?.takeIf { it.isNotBlank() }
            ?: clickedNpc?.let { service?.shopKeyForNpc(it.id) }

    if (configuredShop != null && world.getShop(configuredShop) != null) {
        openShop(configuredShop)
        return
    }

    openShop(fallbackShop)
}
