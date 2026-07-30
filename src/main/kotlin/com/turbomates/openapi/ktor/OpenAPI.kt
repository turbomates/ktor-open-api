package com.turbomates.openapi.ktor

import com.turbomates.openapi.ResponseHeadersBuilder
import com.turbomates.openapi.Type
import com.turbomates.openapi.TypeResolver
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

        internal var responseHeaders: List<ResponseHeadersBuilder.() -> Unit> = emptyList()
            private set

        internal var typeResolvers: List<TypeResolver> = emptyList()
            private set

        /**
         * Describes the types this API names for itself, before reflection gets to read them.
         *
         * ```
         * install(OpenAPI) {
         *     typeResolver { kType ->
         *         when (kType.classifier) {
         *             Money::class -> Type.String(format = "money", nullable = kType.isMarkedNullable)
         *             else -> null
         *         }
         *     }
         * }
         * ```
         *
         * A type meets the resolvers wherever it turns up — as a body, as a response, as a property
         * nested inside one — and the first resolver to describe it wins. The exact types named by
         * [customTypeDescription] are asked before any of them.
         */
        fun typeResolver(resolver: TypeResolver) {
            typeResolvers = typeResolvers + resolver
        }

        /**
         * Headers every response of every operation carries.
         *
         * ```
         * install(OpenAPI) {
         *     globalResponseHeaders {
         *         header("X-Request-Id", Type.String(), "Request correlation id")
         *         header<Int>("X-Rate-Limit-Remaining", "Calls left in the current window")
         *     }
         * }
         * ```
         *
         * A route may describe a header of the same name differently — see `responseHeaders` of the
         * operation block — and the one closer to the response wins.
         */
        fun globalResponseHeaders(block: ResponseHeadersBuilder.() -> Unit) {
            responseHeaders = responseHeaders + block
        }
    }

    companion object Plugin : BaseApplicationPlugin<Application, Configuration, OpenAPI> {
        override val key = AttributeKey<OpenAPI>("OpenAPI")
        override fun install(pipeline: Application, configure: Configuration.() -> Unit): OpenAPI {
            val configuration = Configuration().apply(configure)
            val plugin = OpenAPI(configuration)
            pipeline.install(Webjars)
            configuration.configure(plugin.documentationBuilder)
            // The exact types come first: a resolver is a rule, and a type named on its own is
            // what the API says about that one type whatever the rules are.
            configuration.customTypeDescription.forEach {
                plugin.documentationBuilder.setCustomClassType(it.key, it.value)
            }
            configuration.typeResolvers.forEach(plugin.documentationBuilder::typeResolver)
            // Described last, so that a header stated as a Kotlin type is read through the
            // resolvers of the document like every other type is.
            configuration.responseHeaders.forEach(plugin.documentationBuilder::globalResponseHeaders)
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
