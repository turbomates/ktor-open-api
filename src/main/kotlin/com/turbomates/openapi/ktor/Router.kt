@file:Suppress("unused", "OPT_IN_USAGE")

package com.turbomates.openapi.ktor

import com.turbomates.openapi.Type
import com.turbomates.openapi.openApiKType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.plugin
import io.ktor.server.locations.locations
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.routing.method
import io.ktor.server.routing.route
import io.ktor.util.pipeline.PipelineContext
import kotlin.reflect.KType
import kotlin.reflect.typeOf

inline fun <reified TResponse : Any> Route.post(
    noinline body: suspend PipelineContext<Unit, ApplicationCall>.() -> TResponse
): Route {
    val route = method(HttpMethod.Post) {
        handle {
            call.respond(body())
        }
    }
    openApi.addToPath(
        route.buildFullPath(),
        HttpMethod.Post,
        response = typeOf<TResponse>()
    )
    return route
}

inline fun <reified TResponse : Any> Route.post(
    path: String,
    noinline body: suspend PipelineContext<Unit, ApplicationCall>.() -> TResponse
): Route {
    val route = route(path, HttpMethod.Post) {
        handle {
            call.respond(body())
        }
    }
    openApi.addToPath(
        route.buildFullPath(),
        HttpMethod.Post,
        response = typeOf<TResponse>()
    )
    return route
}

inline fun <reified TResponse : Any, reified TBody : Any> Route.post(
    path: String,
    noinline body: suspend PipelineContext<Unit, ApplicationCall>.(TBody) -> TResponse
): Route {
    val route = route(path, HttpMethod.Post) {
        handle {
            call.respond(body(call.receive()))
        }
    }
    openApi.addToPath(
        route.buildFullPath(),
        HttpMethod.Post,
        response = typeOf<TResponse>(),
        body = typeOf<TBody>()
    )
    return route
}

inline fun <reified TResponse : Any, reified TParams : Any> Route.postParams(
    path: String,
    noinline body: suspend PipelineContext<Unit, ApplicationCall>.(TParams) -> TResponse
): Route {
    val route = route(path, HttpMethod.Post) {
        handle {
            call.respond(body(locations.resolve(TParams::class, call)))
        }
    }
    openApi.addToPath(
        route.buildFullPath(),
        HttpMethod.Post,
        response = typeOf<TResponse>(),
        pathParams = typeOf<TParams>()
    )
    return route
}

inline fun <reified TResponse : Any, reified TBody : Any, reified TParams : Any> Route.post(
    path: String,
    noinline body: suspend PipelineContext<Unit, ApplicationCall>.(TBody, TParams) -> TResponse
): Route {
    val route = route(path, HttpMethod.Post) {
        handle {
            call.respond(body(call.receive(), locations.resolve(TParams::class, call)))
        }
    }
    openApi.addToPath(
        route.buildFullPath(),
        HttpMethod.Post,
        response = typeOf<TResponse>(),
        body = typeOf<TBody>(),
        pathParams = typeOf<TParams>()
    )
    return route
}

inline fun <reified TResponse : Any, reified TBody : Any, reified TQuery : Any, reified TPath : Any> Route.post(
    path: String,
    noinline body: suspend PipelineContext<Unit, ApplicationCall>.(TBody, TPath, TQuery) -> TResponse
): Route {
    val route = route(path, HttpMethod.Post) {
        handle {
            call.respond(
                body(
                    call.receive(),
                    locations.resolve(TPath::class, call),
                    locations.resolve(TQuery::class, call)
                )
            )
        }
    }
    openApi.addToPath(
        route.buildFullPath(),
        HttpMethod.Post,
        response = typeOf<TResponse>(),
        body = typeOf<TBody>(),
        queryParams = typeOf<TQuery>(),
        pathParams = typeOf<TPath>()
    )
    return route
}

inline fun <reified TResponse : Any> Route.get(
    noinline body: suspend PipelineContext<Unit, ApplicationCall>.() -> TResponse
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
    noinline body: suspend PipelineContext<Unit, ApplicationCall>.() -> TResponse
): Route {
    val route = route(path, HttpMethod.Get) {
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

inline fun <reified TResponse : Any, reified TParams : Any> Route.get(
    path: String,
    noinline body: suspend PipelineContext<Unit, ApplicationCall>.(TParams) -> TResponse
): Route {
    val route = route(path, HttpMethod.Get) {
        handle {
            call.respond(body(locations.resolve(TParams::class, call)))
        }
    }
    openApi.addToPath(
        route.buildFullPath(),
        HttpMethod.Get,
        response = typeOf<TResponse>(),
        pathParams = typeOf<TParams>()
    )
    return route
}

inline fun <reified TResponse : Any, reified TQuery : Any, reified TPath : Any> Route.get(
    path: String,
    noinline body: suspend PipelineContext<Unit, ApplicationCall>.(TPath, TQuery) -> TResponse
): Route {
    val route = route(path, HttpMethod.Get) {
        handle {
            call.respond(body(locations.resolve(TPath::class, call), locations.resolve(TQuery::class, call)))
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

inline fun <reified TResponse : Any> Route.delete(
    noinline body: suspend PipelineContext<Unit, ApplicationCall>.() -> TResponse
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
    noinline body: suspend PipelineContext<Unit, ApplicationCall>.() -> TResponse
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

inline fun <reified TResponse : Any, reified TParams : Any> Route.delete(
    path: String,
    noinline body: suspend PipelineContext<Unit, ApplicationCall>.(TParams) -> TResponse
): Route {
    val route = route(path, HttpMethod.Delete) {
        handle {
            call.respond(body(locations.resolve(TParams::class, call)))
        }
    }
    openApi.addToPath(
        route.buildFullPath(),
        HttpMethod.Delete,
        response = typeOf<TResponse>(),
        pathParams = typeOf<TParams>()
    )
    return route
}

inline fun <reified TResponse : Any, reified TBody : Any> Route.deleteWithBody(
    path: String,
    noinline body: suspend PipelineContext<Unit, ApplicationCall>.(TBody) -> TResponse
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

inline fun <reified TResponse : Any, reified TQuery : Any, reified TPath : Any> Route.delete(
    path: String,
    noinline body: suspend PipelineContext<Unit, ApplicationCall>.(TPath, TQuery) -> TResponse
): Route {
    val route = route(path, HttpMethod.Delete) {
        handle {
            call.respond(body(locations.resolve(TPath::class, call), locations.resolve(TQuery::class, call)))
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

inline fun <reified TResponse : Any, reified TQuery : Any, reified TPath : Any, reified TBody : Any> Route.delete(
    path: String,
    noinline body: suspend PipelineContext<Unit, ApplicationCall>.(TPath, TQuery, TBody) -> TResponse
): Route {
    val route = route(path, HttpMethod.Delete) {
        handle {
            call.respond(
                body(
                    locations.resolve(TPath::class, call),
                    locations.resolve(TQuery::class, call),
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

fun OpenAPI.addToPath(
    path: String,
    method: HttpMethod,
    response: KType? = null,
    body: KType? = null,
    pathParams: KType? = null,
    queryParams: KType? = null
) {
    extendDocumentation { responseMap, typeBuilder ->
        addToPath(
            path,
            com.turbomates.openapi.OpenAPI.Method.valueOf(method.value),
            response?.run { responseMap(openApiKType) } ?: emptyMap(),
            body?.run { openApiKType.run(typeBuilder) },
            pathParams?.run { openApiKType.run(typeBuilder) },
            queryParams?.run { openApiKType.run(typeBuilder) }
        )
    }
}

fun Route.buildFullPath(): String {
    return toString().replace(Regex("/\\(.*?\\)"), "")
}

val Route.openApi: OpenAPI
    get() = this.application.plugin(OpenAPI)
