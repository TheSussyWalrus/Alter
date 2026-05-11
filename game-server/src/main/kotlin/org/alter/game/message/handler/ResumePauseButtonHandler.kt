package org.alter.game.message.handler

import net.rsprot.protocol.game.incoming.resumed.ResumePauseButton
import org.alter.game.message.MessageHandler
import org.alter.game.model.attr.INTERACTING_ITEM_ID
import org.alter.game.model.attr.INTERACTING_OPT_ATTR
import org.alter.game.model.attr.INTERACTING_SLOT_ATTR
import org.alter.game.model.entity.Client

/**
 * @author Tom <rspsmods@gmail.com>
 */
class ResumePauseButtonHandler : MessageHandler<ResumePauseButton> {
    override fun consume(
        client: Client,
        message: ResumePauseButton,
    ) {
        log(client, "Continue dialog: component=[%d:%d], slot=%d", message.interfaceId, message.componentId, message.sub)
        if (!client.interfaces.isVisible(message.interfaceId)) {
            return
        }

        client.attr[INTERACTING_OPT_ATTR] = 1
        client.attr[INTERACTING_ITEM_ID] = -1
        client.attr[INTERACTING_SLOT_ATTR] = message.sub

        if (client.world.plugins.executeButton(client, message.interfaceId, message.componentId)) {
            return
        }

        client.queues.submitReturnValue(message)
    }
}
