package org.alter.plugins.content.tools.qabot

import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import net.rsprot.protocol.message.OutgoingGameMessage
import org.alter.api.CommonClientScripts
import org.alter.game.model.World
import org.alter.game.model.entity.Player

class QaPlayer(world: World) : Player(world) {
    val capturedMessages: MutableList<String> = mutableListOf()
    val capturedPackets: MutableList<String> = mutableListOf()
    val lastSkillMenuItems: MutableList<Int> = mutableListOf()

    override fun write(vararg messages: OutgoingGameMessage) {
        messages.forEach { message ->
            trackSkillMenu(message)
            capturedPackets.add(message::class.simpleName ?: message.toString())
            extractMessageText(message)?.let(capturedMessages::add)
        }
    }

    override fun write(vararg messages: Any) {
        messages.forEach { message ->
            trackSkillMenu(message)
            capturedPackets.add(message::class.simpleName ?: message.toString())
            extractMessageText(message)?.let(capturedMessages::add)
        }
    }

    internal fun drainMessages(): List<String> {
        val drained = capturedMessages.toList()
        capturedMessages.clear()
        return drained
    }

    private fun extractMessageText(message: Any): String? =
        runCatching {
            val method = message.javaClass.methods.firstOrNull { it.name == "getMessage" && it.parameterCount == 0 }
            method?.invoke(message) as? String
        }.getOrNull()

    private fun trackSkillMenu(message: Any) {
        val script = message as? RunClientScript ?: return
        if (script.id != CommonClientScripts.SKILL_MULTI_SETUP.script.id) {
            return
        }
        lastSkillMenuItems.clear()
        script.values
            .drop(3)
            .mapNotNull { (it as? Number)?.toInt() }
            .filter { it >= 0 }
            .forEach(lastSkillMenuItems::add)
    }
}
