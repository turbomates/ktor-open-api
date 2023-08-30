package com.turbomates.openapi.ktor

import com.turbomates.openapi.Type
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.BaseApplicationPlugin
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.request.path
import io.ktor.server.response.respondText
import io.ktor.server.webjars.Webjars
import io.ktor.util.AttributeKey
import kotlin.reflect.KType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.turbomates.openapi.OpenAPI as SwaggerOpenAPI

class OpenAPI(configuration: Configuration) {
    private val responseCodeMap: KType.() -> Map<Int, KType> = configuration.responseCodeMap
    private val documentationBuilder: SwaggerOpenAPI = configuration.documentationBuilder
    private val path: String = configuration.path
    private val json = Json {
        encodeDefaults = false
    }

    fun extendDocumentation(extension: SwaggerOpenAPI.(KType.() -> Map<Int, KType>) -> Unit) {
        documentationBuilder.extension(responseCodeMap)
    }

    class Configuration {
        var responseCodeMap: KType.() -> Map<Int, KType> = { mapOf(HttpStatusCode.OK.value to this) }
        var customTypeDescription: Map<KType, Type> = emptyMap()
        var path = "/openapi.json"
        var configure: (SwaggerOpenAPI) -> Unit = {}
        var documentationBuilder: SwaggerOpenAPI = SwaggerOpenAPI("localhost")
    }

    companion object Plugin : BaseApplicationPlugin<Application, Configuration, OpenAPI> {
        override val key = AttributeKey<OpenAPI>("OpenAPI")
        override fun install(pipeline: Application, configure: Configuration.() -> Unit): OpenAPI {
            val configuration = Configuration().apply(configure)
            val plugin = OpenAPI(configuration)
            pipeline.install(Webjars)
            configuration.configure(plugin.documentationBuilder)
            configuration.customTypeDescription.forEach {
                plugin.documentationBuilder.setCustomClassType(it.key, it.value)
            }
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
