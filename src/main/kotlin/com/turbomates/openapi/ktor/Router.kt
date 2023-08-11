@file:Suppress("unused")

package com.turbomates.openapi.ktor

import com.turbomates.openapi.openApiKType
import io.ktor.http.HttpMethod
import io.ktor.server.application.plugin
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import kotlin.reflect.KType

fun OpenAPI.addToPath(
    path: String,
    method: HttpMethod,
    response: KType? = null,
    body: KType? = null,
    pathParams: KType? = null,
    queryParams: KType? = null
) {
    extendDocumentation { responseMap ->
        if (response != null) {
            addToPath(
                path,
                com.turbomates.openapi.OpenAPI.Method.valueOf(method.value),
                response.run { responseMap(this).mapValues { it.value.openApiKType.objectType() } },
                body?.openApiKType?.objectType(),
                pathParams?.openApiKType?.objectType(),
                queryParams?.openApiKType?.objectType()
            )
        }
    }
}

fun String.containsPathParameters(): Boolean {
    return this.contains("{")
}

fun Route.buildFullPath(): String {
    return toString().replace(Regex("/\\(.*?\\)"), "")
}

val Route.openApi: OpenAPI
    get() = this.application.plugin(OpenAPI)
