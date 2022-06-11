package com.turbomates.openapi.ktor

import com.turbomates.openapi.OpenApiKType
import com.turbomates.openapi.Type
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.Configuration
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.BaseApplicationPlugin
import io.ktor.server.application.call
import io.ktor.server.request.path
import io.ktor.server.response.respondText
import io.ktor.util.AttributeKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.turbomates.openapi.OpenAPI as SwaggerOpenAPI

typealias ResponseBuilder = (OpenApiKType) -> Map<Int, Type>
typealias TypeBuilder = (OpenApiKType) -> Type.Object

class OpenAPI(configuration: Configuration) {
    private val typeBuilder: TypeBuilder = configuration.typeBuilder
    private val responseBuilder: ResponseBuilder = configuration.responseBuilder
    private val documentationBuilder: SwaggerOpenAPI = configuration.documentationBuilder
    private val path: String = configuration.path
    private val json = Json {
        encodeDefaults = false
    }

    fun extendDocumentation(extension: SwaggerOpenAPI.(ResponseBuilder, TypeBuilder) -> Unit) {
        documentationBuilder.extension(responseBuilder, typeBuilder)
    }

    class Configuration {
        var typeBuilder: (OpenApiKType) -> Type.Object = { type -> type.objectType("response") }
        var responseBuilder: (OpenApiKType) -> Map<Int, Type> =
            { type -> mapOf(HttpStatusCode.OK.value to type.type()) }
        var path = "/openapi.json"
        var configure: (SwaggerOpenAPI) -> Unit = {}
        var documentationBuilder: SwaggerOpenAPI = SwaggerOpenAPI("localhost")
    }

    companion object Plugin : BaseApplicationPlugin<ApplicationCallPipeline, Configuration, OpenAPI> {
        override val key = AttributeKey<OpenAPI>("OpenAPI")
        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): OpenAPI {
            val configuration = Configuration().apply(configure)
            val plugin = OpenAPI(configuration)
            configuration.configure(plugin.documentationBuilder)
            pipeline.intercept(ApplicationCallPipeline.Call) {
                if (call.request.path() == plugin.path) {
                    val response = plugin.json.encodeToString(plugin.documentationBuilder.root)
                    call.response.status(HttpStatusCode.OK)
                    call.respondText(response, contentType = ContentType.Application.Json)
                    finish()
                }
            }
            return plugin
        }
    }
}