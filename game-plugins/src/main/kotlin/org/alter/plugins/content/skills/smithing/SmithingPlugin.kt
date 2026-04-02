package org.alter.plugins.content.skills.smithing

import org.alter.api.Skills
import org.alter.api.cfg.Animation
import org.alter.api.ext.*
import org.alter.game.Server
import org.alter.game.model.entity.Player
import org.alter.game.model.queue.TaskPriority
import org.alter.game.plugin.KotlinPlugin
import org.alter.game.plugin.PluginRepository
import org.alter.plugins.content.skills.core.ProcessingOutput
import org.alter.plugins.content.skills.core.ProcessingRecipe
import org.alter.plugins.content.skills.core.runProcessing

class SmithingPlugin(
    r: PluginRepository,
    world: org.alter.game.model.World,
    server: Server,
) : KotlinPlugin(r, world, server) {

    init {
        loadService(SmithingService())

        onWorldInit {
            val service = world.getService(SmithingService::class.java) ?: return@onWorldInit

            val registeredSmelts = mutableSetOf<Pair<String, String>>()
            val registeredForges = mutableSetOf<Pair<String, String>>()

            service.smelts.forEach { recipe ->
                recipe.objectNames.forEach { obj ->
                    recipe.inputs.distinct().forEach { item ->
                        if (registeredSmelts.add(obj to item)) {
                            onItemOnObj(obj = obj, item = item) {
                                player.queue(TaskPriority.STRONG) {
                                    val itemId = player.getInteractingItemId()
                                    val candidates =
                                        service
                                            .smeltsForInput(itemId)
                                            .filter { candidate -> candidate.objectNames.contains(obj) }
                                            .sortedByDescending { candidate -> candidate.level }
                                    val recipeToUse = candidates.firstOrNull() ?: return@queue
                                    smelt(this, player, recipeToUse)
                                }
                            }
                        }
                    }
                }
            }

            service.forges.forEach { recipe ->
                recipe.objectNames.forEach { obj ->
                    if (registeredForges.add(obj to recipe.bar)) {
                        onItemOnObj(obj = obj, item = recipe.bar) {
                            player.queue(TaskPriority.STRONG) {
                                val barId = player.getInteractingItemId()
                                val candidates =
                                    service
                                        .forgesForBar(barId)
                                        .filter { candidate -> candidate.objectNames.contains(obj) }
                                        .sortedBy { candidate -> candidate.level }
                                if (candidates.isEmpty()) {
                                    return@queue
                                }

                                val maxCraftable =
                                    candidates
                                        .maxOfOrNull { candidate -> player.inventory.getItemCount(candidate.barId) / candidate.barsRequired }
                                        ?.coerceAtLeast(0)
                                        ?: 0

                                produceItemBox(
                                    player,
                                    *candidates.map { it.productId }.toIntArray(),
                                    title = "What would you like to smith?",
                                    maxProducable = maxCraftable.coerceAtMost(player.inventory.capacity),
                                ) { productId, qty ->
                                    val selected = candidates.firstOrNull { it.productId == productId } ?: return@produceItemBox
                                    val smith = this
                                    smith.queue(TaskPriority.STRONG) {
                                        forge(this, smith, selected, qty)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun smelt(task: org.alter.game.model.queue.QueueTask, player: Player, recipe: SmithSmeltRecipe) {
        if (player.getSkills().getCurrentLevel(Skills.SMITHING) < recipe.level) {
            player.message("You need a Smithing level of ${recipe.level} to smelt this bar.")
            return
        }

        val processing =
            ProcessingRecipe(
                name = recipe.output.substringAfter("item.").replace('_', ' '),
                skill = Skills.SMITHING,
                level = recipe.level,
                experience = recipe.experience,
                inputs = recipe.ingredients,
                outputs = listOf(ProcessingOutput(recipe.outputId, recipe.outputAmount)),
                cycles = recipe.ticks,
                animationId = recipe.animation,
            )

        val furnace = player.getInteractingGameObj()
        player.faceTile(furnace.tile)
        task.runProcessing(player, processing, target = furnace) {
            player.message("You smelt a ${recipe.output.substringAfter("item.").replace('_', ' ')}.")
        }
        player.animate(Animation.RESET_CHARACTER)
    }

    private suspend fun forge(
        task: org.alter.game.model.queue.QueueTask,
        player: Player,
        recipe: SmithForgeRecipe,
        qty: Int,
    ) {
        if (player.getSkills().getCurrentLevel(Skills.SMITHING) < recipe.level) {
            player.message("You need a Smithing level of ${recipe.level} to make that.")
            return
        }

        val amount = qty.coerceAtLeast(1)
        repeat(amount) {
            if (player.getSkills().getCurrentLevel(Skills.SMITHING) < recipe.level) {
                return
            }
            if (player.inventory.getItemCount(recipe.barId) < recipe.barsRequired) {
                if (it == 0) {
                    player.message("You don't have enough bars to make that.")
                }
                return
            }
            if (player.inventory.isFull && !player.inventory.contains(recipe.productId)) {
                player.message("You don't have enough inventory space to smith that.")
                return
            }

            player.faceTile(player.getInteractingGameObj().tile)
            player.lock()
            try {
                player.animate(recipe.animation)
                task.wait(recipe.ticks)
                if (player.inventory.remove(recipe.barId, recipe.barsRequired, assureFullRemoval = true).hasFailed()) {
                    return
                }
                player.inventory.add(recipe.productId, recipe.productCount)
                player.addXp(Skills.SMITHING, recipe.experience)
                player.message("You make ${articleFor(recipe.product)} ${recipe.product.substringAfter("item.").replace('_', ' ')}.")
            } finally {
                player.animate(Animation.RESET_CHARACTER)
                player.unlock()
            }
        }
    }

    private fun articleFor(name: String): String {
        val first = name.substringAfter("item.").firstOrNull()?.lowercaseChar() ?: return "a"
        return if (first in listOf('a', 'e', 'i', 'o', 'u')) "an" else "a"
    }
}
