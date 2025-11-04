@file:Suppress("unused", "OPT_IN_USAGE")

package com.turbomates.openapi.ktor

import io.ktor.http.HttpMethod
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.plugin
import io.ktor.server.request.receive
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

inline fun <reified TResponse : Any> Route.delete(
    noinline body: suspend RoutingContext.() -> TResponse
): Route {
    val route = method(HttpMethod.Delete) {
        handle {
            call.respond(body())
        }
    }
    openApi.addToPath(
        route.buildFullPath(),
        HttpMethod.Delete,
        response = typeOf<TResponse>()
    )
    return route
}

inline fun <reified TResponse : Any> Route.delete(
    path: String,
    noinline body: suspend RoutingContext.() -> TResponse
): Route {
    val route = route(path, HttpMethod.Delete) {
        handle {
            call.respond(body())
        }
    }
    openApi.addToPath(
        route.buildFullPath(),
        HttpMethod.Delete,
        response = typeOf<TResponse>()
    )
    return route
}

@OptIn(InternalSerializationApi::class)
inline fun <reified TResponse : Any, reified TParams : Any> Route.delete(
    path: String,
    noinline body: suspend RoutingContext.(TParams) -> TResponse
): Route {

    val route = route(path, HttpMethod.Delete) {
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
        HttpMethod.Delete,
        response = typeOf<TResponse>(),
        pathParams = if (route.buildFullPath().containsPathParameters()) typeOf<TParams>() else null,
        queryParams = if (!route.buildFullPath().containsPathParameters()) typeOf<TParams>() else null
    )
    return route
}

inline fun <reified TResponse : Any, reified TBody : Any> Route.deleteWithBody(
    path: String,
    noinline body: suspend RoutingContext.(TBody) -> TResponse
): Route {
    val route = route(path, HttpMethod.Delete) {
        handle {
            call.respond(body(call.receive()))
        }
    }
    openApi.addToPath(
        route.buildFullPath(),
        HttpMethod.Delete,
        response = typeOf<TResponse>(),
        body = typeOf<TBody>()
    )
    return route
}

@OptIn(InternalSerializationApi::class)
inline fun <reified TResponse : Any, reified TQuery : Any, reified TPath : Any> Route.delete(
    path: String,
    noinline body: suspend RoutingContext.(TPath, TQuery) -> TResponse
): Route {
    val route = route(path, HttpMethod.Delete) {
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
        HttpMethod.Delete,
        response = typeOf<TResponse>(),
        queryParams = typeOf<TQuery>(),
        pathParams = typeOf<TPath>()
    )
    return route
}

@OptIn(InternalSerializationApi::class)
inline fun <reified TResponse : Any, reified TQuery : Any, reified TPath : Any, reified TBody : Any> Route.delete(
    path: String,
    noinline body: suspend RoutingContext.(TPath, TQuery, TBody) -> TResponse
): Route {
    val route = route(path, HttpMethod.Delete) {
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
            call.respond(
                body(
                    resourcePath,
                    resourceQuery,
                    call.receive()
                )
            )
        }
    }

    openApi.addToPath(
        route.buildFullPath(),
        HttpMethod.Delete,
        response = typeOf<TResponse>(),
        body = typeOf<TBody>(),
        pathParams = typeOf<TPath>(),
        queryParams = typeOf<TQuery>()
    )
    return route
}


