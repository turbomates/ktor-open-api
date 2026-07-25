@file:Suppress("unused")

package com.turbomates.openapi.ktor

import com.turbomates.openapi.openApiKType
import io.ktor.http.HttpMethod
import io.ktor.server.application.plugin
import io.ktor.server.routing.OpenApiRoutePathFormat
import io.ktor.server.routing.PathSegmentTailcardRouteSelector
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutePathComponent
import io.ktor.server.routing.RoutePathFormat
import io.ktor.server.routing.application
import io.ktor.server.routing.path
import kotlin.reflect.KType
import com.turbomates.openapi.OpenAPI as SwaggerOpenAPI

fun OpenAPI.addToPath(
    path: String,
    method: HttpMethod,
    response: KType? = null,
    body: KType? = null,
    pathParams: KType? = null,
    queryParams: KType? = null
) {
    extendDocumentation { responseMap ->
        val documentedMethod = method.documentedMethod()
        if (response != null && documentedMethod != null) {
            addToPath(
                path,
                documentedMethod,
                response.run { responseMap(this).mapValues { it.value.openApiKType.objectType() } },
                body?.openApiKType?.objectType(),
                pathParams?.openApiKType?.objectType(),
                queryParams?.openApiKType?.objectType()
            )
        }
    }
}

/**
 * OpenAPI counterpart of a Ktor method, or `null` when a path item cannot describe it.
 *
 * `HttpMethod` is open — a route may be registered for anything, including a method OpenAPI has no
 * place for. Such a route is left out of the documentation instead of failing the registration.
 */
private fun HttpMethod.documentedMethod(): SwaggerOpenAPI.Method? {
    return SwaggerOpenAPI.Method.entries.find { it.name.equals(value, ignoreCase = true) }
}

fun String.containsPathParameters(): Boolean {
    return this.contains("{")
}

/**
 * Full path of the route as an OpenAPI path template.
 *
 * Ktor's path syntax is richer than OpenAPI's, so it is normalized: an optional parameter (`{id?}`)
 * becomes a regular `{id}`, since OpenAPI has no optional path parameters and the route does match
 * with the segment present; a tailcard (`{path...}`) keeps its name instead of the `{**}`
 * placeholder, so that the variable can be documented as a parameter. A trailing slash is dropped —
 * Ktor matches the path with and without it, and OpenAPI would treat the two forms as different
 * paths.
 */
fun Route.buildFullPath(): String {
    val path = path(OpenApiPathFormat)
    return if (path.length > 1) path.removeSuffix("/") else path
}

private object OpenApiPathFormat : RoutePathFormat {
    override fun format(component: RoutePathComponent): String {
        if (component is PathSegmentTailcardRouteSelector && component.name.isNotEmpty()) {
            return "{${component.name}}"
        }
        return OpenApiRoutePathFormat.format(component)
    }
}

val Route.openApi: OpenAPI
    get() = this.application.plugin(OpenAPI)
