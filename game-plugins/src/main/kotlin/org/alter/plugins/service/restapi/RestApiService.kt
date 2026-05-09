package org.alter.plugins.service.restapi

import gg.rsmod.util.ServerProperties
import org.alter.game.Server
import org.alter.game.model.World
import org.alter.game.service.Service
import org.alter.plugins.service.restapi.routes.CorsRoute
import org.alter.plugins.service.restapi.routes.RestApiRoutes
import spark.Spark.ipAddress
import spark.Spark.port
import spark.Spark.stop

class RestApiService : Service {
    override fun init(
        server: Server,
        world: World,
        serviceProperties: ServerProperties,
    ) {
        ipAddress(serviceProperties.getOrDefault("host", "127.0.0.1"))
        port(serviceProperties.getOrDefault("port", 4567))
        CorsRoute(
            serviceProperties.getOrDefault("origin", "*"),
            serviceProperties.getOrDefault("methods", "GET, POST, PATCH, DELETE, OPTIONS"),
            serviceProperties.getOrDefault("headers", "X-PINGOTHER, Content-Type, X-AUTH"),
        )
        RestApiRoutes().init(world, serviceProperties.getOrDefault("auth", false))
    }

    override fun terminate(
        server: Server,
        world: World,
    ) {
        stop()
    }
}
