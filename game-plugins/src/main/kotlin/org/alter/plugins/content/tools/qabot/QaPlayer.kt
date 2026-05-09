package org.alter.plugins.content.tools.qabot

import net.rsprot.protocol.message.OutgoingGameMessage
import org.alter.game.model.World
import org.alter.game.model.entity.Player

class QaPlayer(world: World) : Player(world) {
    val capturedMessages: MutableList<String> = mutableListOf()
    val capturedPackets: MutableList<String> = mutableListOf()

    override fun write(vararg messages: OutgoingGameMessage) {
        messages.forEach { message ->
            capturedPackets.add(message::class.simpleName ?: message.toString())
            extractMessageText(message)?.let(capturedMessages::add)
        }
    }

    override fun write(vararg messages: Any) {
        messages.forEach { message ->
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
}
