package org.alter.plugins.content.tools.client

import org.alter.api.ext.message
import org.alter.api.ext.openUrl
import org.alter.api.ext.player
import org.alter.game.Server
import org.alter.game.model.World
import org.alter.game.plugin.KotlinPlugin
import org.alter.game.plugin.PluginRepository

class ClientToolsPlugin(
    r: PluginRepository,
    world: World,
    server: Server,
) : KotlinPlugin(r, world, server) {
    private val manifestService = ClientManifestService()

    init {
        loadService(manifestService)

        onCommand("clientmanifest", description = "Open the official Alter client manifest") {
            player.message("Opening the Alter client manifest: $CLIENT_MANIFEST_URL")
            player.openUrl(CLIENT_MANIFEST_URL)
        }
    }

    private companion object {
        private const val CLIENT_MANIFEST_URL = "http://127.0.0.1:4567/client_manifest.json"
    }
}
