package org.alter.plugins.content.skills.smithing

import gg.rsmod.util.ServerProperties
import org.alter.game.Server
import org.alter.game.model.World
import org.alter.game.service.Service
import org.alter.plugins.content.skills.core.ProcessingIngredient
import org.alter.plugins.content.skills.core.SkillJson
import org.alter.rscm.RSCM.getRSCM
import java.nio.file.Paths

class SmithingService : Service {
    lateinit var smelts: List<SmithSmeltRecipe>
        private set
    lateinit var forges: List<SmithForgeRecipe>
        private set

    private val smeltsByInput = mutableMapOf<Int, MutableList<SmithSmeltRecipe>>()
    private val forgesByBar = mutableMapOf<Int, MutableList<SmithForgeRecipe>>()

    override fun init(
        server: Server,
        world: World,
        serviceProperties: ServerProperties,
    ) {
        smeltsByInput.clear()
        forgesByBar.clear()

        smelts =
            SkillJson.loadList<SmithSmeltRecipe>(
                Paths.get(serviceProperties.get("smithing.smelts") ?: "../data/cfg/smithing/smelts.json"),
            ).onEach { recipe ->
                recipe.ingredients =
                    recipe.inputs
                        .groupingBy { getRSCM(it) }
                        .eachCount()
                        .map { (itemId, amount) -> ProcessingIngredient(itemId, amount) }
                recipe.outputId = getRSCM(recipe.output)
                recipe.ingredients.forEach { ingredient ->
                    smeltsByInput.getOrPut(ingredient.itemId) { mutableListOf() }.add(recipe)
                }
            }

        forges =
            SkillJson.loadList<SmithForgeRecipe>(
                Paths.get(serviceProperties.get("smithing.forges") ?: "../data/cfg/smithing/forges.json"),
            ).onEach { recipe ->
                recipe.barId = getRSCM(recipe.bar)
                recipe.productId = getRSCM(recipe.product)
                forgesByBar.getOrPut(recipe.barId) { mutableListOf() }.add(recipe)
            }

        SkillJson.logLoaded("smithing smelt definition", smelts.size)
        SkillJson.logLoaded("smithing forge definition", forges.size)
    }

    fun smeltsForInput(inputId: Int): List<SmithSmeltRecipe> = smeltsByInput[inputId].orEmpty()

    fun forgesForBar(barId: Int): List<SmithForgeRecipe> = forgesByBar[barId].orEmpty()
}
