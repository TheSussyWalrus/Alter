package org.alter.plugins.content.tools.qabot

import org.alter.api.ext.getCommandArgs
import org.alter.api.ext.message
import org.alter.api.ext.player
import org.alter.game.Server
import org.alter.game.model.World
import org.alter.game.model.entity.Player
import org.alter.game.model.priv.Privilege
import org.alter.game.plugin.KotlinPlugin
import org.alter.game.plugin.PluginRepository

class QaBotPlugin(
    r: PluginRepository,
    world: World,
    server: Server,
) : KotlinPlugin(r, world, server) {
    private val service = QaBotService()

    init {
        loadService(service)

        onCommand("qabot", description = "Run the server-side QA bot") {
            if (!player.canUseQaBot()) {
                player.message("You need owner or developer privileges to use the QA bot.")
                return@onCommand
            }
            val args = player.getCommandArgs()
            when (args.getOrNull(0)?.lowercase()) {
                "start" -> {
                    val scenario = args.getOrNull(1)
                    try {
                        val session = service.startSession(world, scenario, player)
                        player.message("QA bot started session ${session.id} (${session.scenarioId}).")
                    } catch (t: IllegalStateException) {
                        player.message(t.message ?: "QA bot could not start.")
                    }
                }
                "stop" -> {
                    val stopped = service.stopSession(world)
                    player.message(if (stopped != null) "QA bot stopped session ${stopped.id}." else "No QA bot session is running.")
                }
                "status" -> {
                    val status = service.status()
                    val active = status.get("activeSessionId")?.takeIf { !it.isJsonNull }?.asString
                    player.message(if (active != null) "QA bot is running session $active." else "QA bot is idle.")
                }
                "report" -> {
                    val latest = service.listSessionReports(limit = 1).firstOrNull()
                    val id = latest?.get("id")?.asString
                    player.message(if (id != null) "Latest QA report: data/qa/sessions/$id.json" else "No QA reports have been written yet.")
                }
                else -> player.message("Usage: ::qabot start [scenario], ::qabot stop, ::qabot status, ::qabot report")
            }
        }
    }

    private fun Player.canUseQaBot(): Boolean =
        privilege.powers.contains(Privilege.OWNER_POWER) || privilege.powers.contains(Privilege.DEV_POWER)
}
