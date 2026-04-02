package org.alter.plugins.content.skills.core

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import gg.rsmod.util.ServerProperties
import org.alter.game.Server
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object SkillJson {
    val gson: Gson = Gson()

    inline fun <reified T> loadList(
        serviceProperties: ServerProperties,
        propertyKey: String,
        fallbackPath: String,
    ): List<T> {
        val path = Paths.get(serviceProperties.get(propertyKey) ?: fallbackPath)
        return loadList(path)
    }

    inline fun <reified T> loadList(path: Path): List<T> {
        Files.newBufferedReader(path).use { reader ->
            val listType = object : TypeToken<List<T>>() {}.type
            return gson.fromJson(reader, listType)
        }
    }

    fun logLoaded(noun: String, amount: Int) {
        Server.logger.info { "Loaded $amount $noun${if (amount == 1) "" else "s"}." }
    }
}
