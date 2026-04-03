package org.alter.plugins.content.skills.slayer

import org.alter.api.Skills
import org.alter.api.ext.*
import org.alter.game.Server
import org.alter.game.model.World
import org.alter.game.model.attr.KILLER_ATTR
import org.alter.game.model.entity.Npc
import org.alter.game.model.entity.Player
import org.alter.game.model.queue.QueueTask
import org.alter.game.plugin.KotlinPlugin
import org.alter.game.plugin.PluginRepository
import org.alter.plugins.content.skills.core.SLAYER_STATE
import org.alter.plugins.content.skills.core.SlayerTaskState
import org.alter.plugins.content.skills.core.state

class SlayerPlugin(
    r: PluginRepository,
    world: World,
    server: Server,
) : KotlinPlugin(r, world, server) {

    init {
        loadService(SlayerService())

        onWorldInit {
            val service = world.getService(SlayerService::class.java) ?: return@onWorldInit
            service.masters.forEach { master ->
                onNpcOption(master.npc, option = "talk-to") {
                    player.queue { dialog(player, master, service) }
                }
            }
        }

        onAnyNpcDeath {
            val service = world.getService(SlayerService::class.java) ?: return@onAnyNpcDeath
            val npc = ctx as? Npc ?: return@onAnyNpcDeath
            val killer = npc.attr[KILLER_ATTR]?.get() as? Player ?: return@onAnyNpcDeath
            handleDeath(killer, npc, service)
        }
    }

    private suspend fun QueueTask.dialog(player: Player, master: SlayerMasterEntry, service: SlayerService) {
        if (player.getSkills().getBaseLevel(Skills.SLAYER) < master.minLevel) {
            chatPlayer(player, "Do you have a task for me?")
            chatNpc(player, "You need a Slayer level of ${master.minLevel} before I can help you.")
            return
        }

        chatNpc(player, "Hello there. What would you like from me?")
        when (options(player, "I need a task.", "What's my current task?", "Nothing for now.")) {
            1 -> assignOrReplaceTask(player, master, service)
            2 -> explainCurrentTask(player)
            3 -> chatPlayer(player, "Nothing for now.")
        }
    }

    private suspend fun QueueTask.assignOrReplaceTask(player: Player, master: SlayerMasterEntry, service: SlayerService) {
        val current = player.state(SLAYER_STATE)
        if (current.amountRemaining > 0) {
            chatNpc(
                player,
                "You're still on a task: ${current.taskName} (${current.amountRemaining} remaining). Do you want me to replace it?",
            )
            when (options(player, "Yes, replace it.", "No, I'll keep it.")) {
                1 -> assignNewTask(player, master, service, current)
                2 -> chatPlayer(player, "I'll keep my current task.")
            }
            return
        }

        assignNewTask(player, master, service, current)
    }

    private suspend fun QueueTask.assignNewTask(
        player: Player,
        master: SlayerMasterEntry,
        service: SlayerService,
        current: SlayerTaskState,
    ) {
        val task = pickTask(player, master, service)
        if (task == null) {
            chatNpc(player, "I don't have any suitable assignments for you right now.")
            return
        }

        val amount = if (master.minAmount == master.maxAmount) master.minAmount else world.random(master.minAmount..master.maxAmount)
        val updated =
            current.copy(
                master = master.name,
                taskName = task.name,
                npcKey = task.npcKeys.firstOrNull().orEmpty(),
                amountAssigned = amount,
                amountRemaining = amount,
            )
        player.state(SLAYER_STATE, updated)

        chatNpc(player, "Your new task is to kill $amount ${task.name}.")
        player.message("New Slayer assignment: ${task.name} ($amount remaining).")
    }

    private suspend fun QueueTask.explainCurrentTask(player: Player) {
        val current = player.state(SLAYER_STATE)
        if (current.amountRemaining <= 0 || current.taskName.isBlank()) {
            chatNpc(player, "You don't currently have a Slayer assignment.")
            return
        }

        chatNpc(player, "You need to kill ${current.amountRemaining} more ${current.taskName}.")
    }

    private fun pickTask(player: Player, master: SlayerMasterEntry, service: SlayerService): SlayerTaskEntry? {
        val candidates = service.eligibleTasks(master, player)
        if (candidates.isEmpty()) {
            return null
        }

        val totalWeight = candidates.sumOf { it.weight.coerceAtLeast(1) }
        var roll = world.random(1..totalWeight)
        candidates.forEach { task ->
            roll -= task.weight.coerceAtLeast(1)
            if (roll <= 0) {
                return task
            }
        }
        return candidates.last()
    }

    private fun handleDeath(player: Player, npc: Npc, service: SlayerService) {
        val current = player.state(SLAYER_STATE)
        if (current.amountRemaining <= 0 || current.taskName.isBlank()) {
            return
        }

        val task = service.taskFor(current.taskName) ?: return
        if (!task.npcIds.contains(npc.id)) {
            return
        }

        val remaining = (current.amountRemaining - 1).coerceAtLeast(0)
        player.addXp(Skills.SLAYER, task.experience)

        if (remaining > 0) {
            player.state(SLAYER_STATE, current.copy(amountRemaining = remaining))
            player.message("Slayer task updated: ${task.name} ($remaining remaining).")
            return
        }

        val completed =
            current.copy(
                taskName = "",
                npcKey = "",
                amountAssigned = 0,
                amountRemaining = 0,
                streak = current.streak + 1,
                points = current.points + task.points,
            )
        player.state(SLAYER_STATE, completed)
        player.message("You've completed your Slayer task: ${task.name}.")
        player.message("Slayer streak: ${completed.streak}. Slayer points: ${completed.points}.")
    }
}
