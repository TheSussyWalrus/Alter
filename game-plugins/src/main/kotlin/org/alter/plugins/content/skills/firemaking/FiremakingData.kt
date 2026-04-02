package org.alter.plugins.content.skills.firemaking

data class FiremakingEntry(
    val log: String,
    val level: Int,
    val experience: Double,
    val ticks: Int = 2,
) {
    init {
        require(log.isNotBlank()) { "Firemaking log cannot be blank." }
        require(level >= 1) { "Firemaking level must be >= 1." }
        require(experience >= 0.0) { "Firemaking experience cannot be negative." }
        require(ticks >= 1) { "Firemaking ticks must be >= 1." }
    }
}
