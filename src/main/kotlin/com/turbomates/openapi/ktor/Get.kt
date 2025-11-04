@file:Suppress("unused", "OPT_IN_USAGE")

package com.turbomates.openapi.ktor

import io.ktor.http.HttpMethod
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.plugin
import io.ktor.server.resources.Resources
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.method
import io.ktor.server.routing.route
import io.ktor.util.pipeline.PipelineContext
import kotlin.reflect.typeOf
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer

inline fun <reified TResponse : Any> Route.get(
    tags: List<String> = emptyList(),
    noinline body: suspend RoutingContext.() -> TResponse
): Route {
    val route = method(HttpMethod.Get) {
        handle {
            call.respond(body())
        }
    }
    openApi.addToPath(
        route.buildFullPath(),
        HttpMethod.Get,
        response = typeOf<TResponse>()
    )
    return route
}

inline fun <reified TResponse : Any> Route.get(
    path: String,
    noinline body: suspend RoutingContext.() -> TResponse
): Route {
    val route = route(path, HttpMethod.Get) {
        handle {
            call.respond(body())
        }
    }
    openApi.addToPath(
        route.buildFullPath(),
        HttpMethod.Get,
        response = typeOf<TResponse>(),
    )
    return route
}

@OptIn(InternalSerializationApi::class)
inline fun <reified TResponse : Any, reified TParams : Any> Route.get(
    path: String,
    noinline body: suspend RoutingContext.(TParams) -> TResponse
): Route {
    val route = route(path, HttpMethod.Get) {
        handle {
            val resources = call.application.plugin(Resources)
            val resource = resources.resourcesFormat.decodeFromParameters(
                TParams::class.serializer(),
                call.parameters
            )
            call.respond(body(resource))
        }
    }

    openApi.addToPath(
        route.buildFullPath(),
        HttpMethod.Get,
        response = typeOf<TResponse>(),
        pathParams = if (route.buildFullPath().containsPathParameters()) typeOf<TParams>() else null,
        queryParams = if (!route.buildFullPath().containsPathParameters()) typeOf<TParams>() else null
    )
    return route
}

@OptIn(InternalSerializationApi::class)
inline fun <reified TResponse : Any, reified TQuery : Any, reified TPath : Any> Route.get(
    path: String,
    noinline body: suspend RoutingContext.(TPath, TQuery) -> TResponse
): Route {
    val route = route(path, HttpMethod.Get) {
        handle {
            val resources = call.application.plugin(Resources)
            val resourcePath = resources.resourcesFormat.decodeFromParameters(
                TPath::class.serializer(),
                call.parameters
            )
            val resourceQuery = resources.resourcesFormat.decodeFromParameters(
                TQuery::class.serializer(),
                call.parameters
            )
            call.respond(body(resourcePath, resourceQuery))
        }
    }
    openApi.addToPath(
        route.buildFullPath(),
        HttpMethod.Get,
        response = typeOf<TResponse>(),
        pathParams = typeOf<TPath>(),
        queryParams = typeOf<TQuery>()
    )
    return route
}
