package org.alter.plugins.service.restapi

import gg.rsmod.util.ServerProperties
import org.alter.game.Server
import org.alter.game.model.World
import org.alter.game.service.Service
import org.alter.plugins.service.restapi.routes.CorsRoute
import org.alter.plugins.service.restapi.routes.RestApiRoutes
import spark.Spark
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class RestApiService : Service {
    private var httpPort = 4567
    private var host = "127.0.0.1"
    private var gamePort = 43594
    private var origin = "*"
    private var methods = "GET, POST, PATCH, DELETE, OPTIONS"
    private var headers = "X-PINGOTHER, Content-Type, X-AUTH"
    private var auth = false
    private var started = false

    override fun init(
        server: Server,
        world: World,
        serviceProperties: ServerProperties,
    ) {
        httpPort = serviceProperties.getOrDefault("port", 4567)
        host = serviceProperties.getOrDefault("host", "127.0.0.1")
        gamePort = serviceProperties.getOrDefault("game-port", 43594)
        origin = serviceProperties.getOrDefault("origin", "*")
        methods = serviceProperties.getOrDefault("methods", "GET, POST, PATCH, DELETE, OPTIONS")
        headers = serviceProperties.getOrDefault("headers", "X-PINGOTHER, Content-Type, X-AUTH")
        auth = serviceProperties.getOrDefault("auth", false)
    }

    override fun bindNet(
        server: Server,
        world: World,
    ) {
        if (started) {
            return
        }

        Spark.ipAddress(host)
        Spark.port(httpPort)
        val worldEditorDist = findWorldEditorDist()
        if (worldEditorDist != null) {
            Spark.externalStaticFileLocation(worldEditorDist.toString())
        }
        CorsRoute(origin, methods, headers)
        RestApiRoutes(host, httpPort, gamePort, worldEditorDist).init(world, auth)
        Spark.init()
        Spark.awaitInitialization()
        started = true
    }

    override fun terminate(
        server: Server,
        world: World,
    ) {
        started = false
        Spark.stop()
    }

    private fun findWorldEditorDist(): Path? {
        val userDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        val roots = listOfNotNull(userDir, userDir.parent, userDir.parent?.parent)
        val candidates =
            roots.flatMap { root ->
                listOf(
                    root.resolve("http-api").resolve("dist"),
                    root.resolve("Alter").resolve("http-api").resolve("dist"),
                )
            }.distinct()
        return candidates.firstOrNull { Files.isRegularFile(it.resolve("index.html")) }
    }
}
