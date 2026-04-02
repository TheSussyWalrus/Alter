package org.alter.plugins.content.skills.core

import com.google.gson.reflect.TypeToken
import org.alter.game.model.attr.AttributeKey
import org.alter.game.model.entity.Pawn

class AttributeState<T : Any>(
    val key: AttributeKey<T>,
    private val defaultValue: T,
) {
    fun get(pawn: Pawn): T = pawn.attr.getOrDefault(key, defaultValue)

    fun set(pawn: Pawn, value: T) {
        pawn.attr[key] = value
    }

    fun clear(pawn: Pawn) {
        pawn.attr.remove(key)
    }

    fun exists(pawn: Pawn): Boolean = pawn.attr.has(key)

    fun update(
        pawn: Pawn,
        block: (T) -> T,
    ): T {
        val updated = block(get(pawn))
        set(pawn, updated)
        return updated
    }
}

class JsonState<T : Any>(
    private val key: AttributeKey<String>,
    private val defaultValue: T,
    private val typeToken: TypeToken<T>,
) {
    fun get(pawn: Pawn): T {
        val stored = pawn.attr[key] ?: return defaultValue
        return runCatching { SkillJson.gson.fromJson<T>(stored, typeToken.type) }.getOrDefault(defaultValue)
    }

    fun set(pawn: Pawn, value: T) {
        pawn.attr[key] = SkillJson.gson.toJson(value, typeToken.type)
    }

    fun clear(pawn: Pawn) {
        pawn.attr.remove(key)
    }

    fun exists(pawn: Pawn): Boolean = pawn.attr.has(key)
}

inline fun <reified T : Any> jsonState(
    key: AttributeKey<String>,
    defaultValue: T,
): JsonState<T> = JsonState(key, defaultValue, object : TypeToken<T>() {})

fun <T : Any> Pawn.state(state: AttributeState<T>): T = state.get(this)

fun <T : Any> Pawn.state(state: JsonState<T>): T = state.get(this)

fun <T : Any> Pawn.state(
    state: AttributeState<T>,
    value: T,
) {
    state.set(this, value)
}

fun <T : Any> Pawn.state(
    state: JsonState<T>,
    value: T,
) {
    state.set(this, value)
}
