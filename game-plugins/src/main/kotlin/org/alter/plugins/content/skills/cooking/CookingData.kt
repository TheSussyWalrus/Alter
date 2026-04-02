package org.alter.plugins.content.skills.cooking

data class CookingEntry(
    val raw: String,
    val cooked: String,
    val burnt: String,
    val level: Int,
    val stopBurn: Int,
    val experience: Double,
    val ticks: Int = 2,
) {
    init {
        require(raw.isNotBlank()) { "Cooking raw item cannot be blank." }
        require(cooked.isNotBlank()) { "Cooking cooked item cannot be blank." }
        require(burnt.isNotBlank()) { "Cooking burnt item cannot be blank." }
        require(level >= 1) { "Cooking level must be >= 1." }
        require(stopBurn >= level) { "Cooking stop-burn level must be >= the required level." }
        require(experience >= 0.0) { "Cooking experience cannot be negative." }
        require(ticks >= 1) { "Cooking ticks must be >= 1." }
    }
}
