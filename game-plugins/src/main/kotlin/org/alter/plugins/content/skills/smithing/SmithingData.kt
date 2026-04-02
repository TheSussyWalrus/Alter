package org.alter.plugins.content.skills.smithing

import org.alter.api.cfg.Animation
import org.alter.plugins.content.skills.core.ProcessingIngredient

data class SmithSmeltRecipe(
    val objectNames: List<String> = listOf("object.furnace"),
    val inputs: List<String>,
    val output: String,
    val outputAmount: Int = 1,
    val level: Int,
    val experience: Double,
    val ticks: Int = 2,
    val animation: Int = Animation.SMITHING_SMELT,
) {
    lateinit var ingredients: List<ProcessingIngredient>
    var outputId: Int = -1
}

data class SmithForgeRecipe(
    val objectNames: List<String> = listOf("object.anvil"),
    val bar: String,
    val product: String,
    val barsRequired: Int,
    val level: Int,
    val experience: Double,
    val productCount: Int = 1,
    val ticks: Int = 2,
    val animation: Int = Animation.SMITHING_ANVIL,
) {
    var barId: Int = -1
    var productId: Int = -1
}
