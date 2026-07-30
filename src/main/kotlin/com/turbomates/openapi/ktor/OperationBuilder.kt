@file:Suppress("unused")

package com.turbomates.openapi.ktor

import com.turbomates.openapi.INType
import com.turbomates.openapi.MediaType
import com.turbomates.openapi.OperationDescription
import com.turbomates.openapi.Parameter
import com.turbomates.openapi.ResponseDescription
import com.turbomates.openapi.ResponseHeader
import com.turbomates.openapi.ResponseHeadersBuilder
import com.turbomates.openapi.TypeResolvers
import com.turbomates.openapi.openApiKType
import com.turbomates.openapi.securityRequirement
import com.turbomates.openapi.spec.ExternalDocumentationObject
import com.turbomates.openapi.spec.SecurityRequirement
import io.ktor.http.HttpStatusCode
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/** Marks the operation DSL, so that its receivers do not leak into one another. */
@DslMarker
annotation class OpenApiDsl

/**
 * Everything about a route the types in its signature cannot say.
 *
 * ```
 * get<UserResponse, UserPath>("/users/{id}", {
 *     summary = "Find a user"
 *     operationId = "getUser"
 *     tags("Users")
 *     header<String>("X-Request-Id", required = true)
 *     responseOf<ErrorResponse>(HttpStatusCode.NotFound, "no user with that id")
 *     security("BearerAuth")
 * }) { path -> service.find(path.id) }
 * ```
 *
 * Everything is optional: a route described by nothing but its types documents exactly as much as
 * it did before.
 */
@OpenApiDsl
class OperationBuilder {
    var summary: String? = null
    var description: String? = null
    var operationId: String? = null
    var deprecated: Boolean = false

    private val tags: MutableList<String> = mutableListOf()
    private val parameters: MutableList<ParameterDescription> = mutableListOf()
    private val responses: MutableMap<String, ResponseTypeDescription> = mutableMapOf()
    private val security: MutableList<SecurityRequirement> = mutableListOf()
    private val responseHeaders: MutableList<ResponseHeadersBuilder.() -> Unit> = mutableListOf()
    private var externalDocs: ExternalDocumentationObject? = null
    private var consumes: List<String> = listOf(MediaType.JSON)
    private var produces: List<String> = listOf(MediaType.JSON)

    /** Groups the operation under [names]; a tool shows one section per tag. */
    fun tags(vararg names: String) {
        tags.addAll(names)
    }

    fun externalDocs(url: String, description: String? = null) {
        externalDocs = ExternalDocumentationObject(url, description)
    }

    /** Media types the request body may come in. `application/json` unless said otherwise. */
    fun consumes(vararg mediaTypes: String) {
        consumes = mediaTypes.toList()
    }

    /** Media types the responses come in. `application/json` unless said otherwise. */
    fun produces(vararg mediaTypes: String) {
        produces = mediaTypes.toList()
    }

    /** A header the operation reads. */
    inline fun <reified T : Any> header(name: String, required: Boolean = false, description: String? = null) {
        parameter(name, typeOf<T>(), INType.HEADER, required, description)
    }

    /** A cookie the operation reads. */
    inline fun <reified T : Any> cookie(name: String, required: Boolean = false, description: String? = null) {
        parameter(name, typeOf<T>(), INType.COOKIE, required, description)
    }

    /** A query parameter the route signature does not carry. */
    inline fun <reified T : Any> queryParameter(name: String, required: Boolean = false, description: String? = null) {
        parameter(name, typeOf<T>(), INType.QUERY, required, description)
    }

    @PublishedApi
    internal fun parameter(name: String, type: KType, location: INType, required: Boolean, description: String?) {
        parameters.add(ParameterDescription(name, type, location, required, description))
    }

    /**
     * Describes the response of [status], which carries whatever the route returns.
     *
     * A code the route does not return on its own is documented with no body at all — a `204`, or
     * one whose body is stated with [responseOf].
     */
    fun response(status: HttpStatusCode, description: String? = null) {
        response(status.value, description)
    }

    fun response(status: Int, description: String? = null) {
        describeResponse(status.toString(), description, null)
    }

    /**
     * Describes the response of [status] together with the headers it carries.
     *
     * ```
     * response(HttpStatusCode.Created, "the order") { header("Location", Type.String()) }
     * ```
     */
    fun response(status: HttpStatusCode, description: String? = null, headers: ResponseHeadersBuilder.() -> Unit) {
        response(status.value, description, headers)
    }

    fun response(status: Int, description: String? = null, headers: ResponseHeadersBuilder.() -> Unit) {
        describeResponse(status.toString(), description, null, headers)
    }

    /**
     * Describes the response of [status], which carries a [T] rather than what the route returns.
     *
     * This is how one operation answers with several bodies — `200` with the resource and `404`
     * with an error of its own.
     */
    inline fun <reified T : Any> responseOf(status: HttpStatusCode, description: String? = null) {
        responseOf<T>(status.value, description)
    }

    inline fun <reified T : Any> responseOf(status: Int, description: String? = null) {
        describeResponse(status.toString(), description, typeOf<T>())
    }

    inline fun <reified T : Any> responseOf(
        status: HttpStatusCode,
        description: String? = null,
        noinline headers: ResponseHeadersBuilder.() -> Unit
    ) {
        responseOf<T>(status.value, description, headers)
    }

    inline fun <reified T : Any> responseOf(
        status: Int,
        description: String? = null,
        noinline headers: ResponseHeadersBuilder.() -> Unit
    ) {
        describeResponse(status.toString(), description, typeOf<T>(), headers)
    }

    /** Describes every status code not documented on its own. */
    fun default(description: String? = null) {
        describeResponse(OperationDescription.DEFAULT_RESPONSE, description, null)
    }

    inline fun <reified T : Any> defaultOf(description: String? = null) {
        describeResponse(OperationDescription.DEFAULT_RESPONSE, description, typeOf<T>())
    }

    /**
     * Headers every response of this operation carries, on top of the ones the document states
     * globally.
     *
     * ```
     * responseHeaders { header("X-Request-Id", Type.String(), "Request correlation id") }
     * ```
     *
     * A header named here replaces the global one of the same name, and a header named on a single
     * response replaces this one in turn.
     */
    fun responseHeaders(block: ResponseHeadersBuilder.() -> Unit) {
        responseHeaders.add(block)
    }

    @PublishedApi
    internal fun describeResponse(
        code: String,
        description: String?,
        type: KType?,
        headers: (ResponseHeadersBuilder.() -> Unit)? = null
    ) {
        val known = responses[code]
        responses[code] = ResponseTypeDescription(
            description ?: known?.description,
            type ?: known?.type,
            known?.headers.orEmpty() + listOfNotNull(headers)
        )
    }

    /**
     * Requires [scheme] of this operation, with [scopes] for a scheme that has any.
     *
     * Called more than once, it says that satisfying any one of the schemes is enough. The scheme
     * has to be one the document offers — see `OpenAPI.securityScheme`.
     */
    fun security(scheme: String, vararg scopes: String) {
        security.add(securityRequirement(scheme, *scopes))
    }

    /** Says that this operation needs no authentication even where the document requires it. */
    fun noSecurity() {
        security.clear()
        security.add(emptyMap())
    }

    /**
     * The description this block adds up to, with every type in it read through [resolvers].
     *
     * The types are described here rather than where they were named, so that a header or a
     * response body of a route is described the way the document describes that type everywhere
     * else — see `OpenAPI.typeResolver`.
     */
    internal fun build(resolvers: TypeResolvers): OperationDescription {
        return OperationDescription(
            tags = tags.distinct(),
            summary = summary,
            description = description,
            operationId = operationId,
            deprecated = deprecated,
            externalDocs = externalDocs,
            security = security.toList().takeIf { it.isNotEmpty() },
            parameters = parameters.map {
                Parameter(
                    it.name,
                    it.type.openApiKType(resolvers).type(),
                    it.location,
                    it.required,
                    it.description
                )
            },
            consumes = consumes,
            produces = produces,
            responses = responses.mapValues { (_, response) ->
                ResponseDescription(
                    response.description,
                    response.type?.openApiKType(resolvers)?.type(),
                    response.headers.buildResponseHeaders(resolvers)
                )
            },
            responseHeaders = responseHeaders.buildResponseHeaders(resolvers)
        )
    }

    private fun List<ResponseHeadersBuilder.() -> Unit>.buildResponseHeaders(
        resolvers: TypeResolvers
    ): List<ResponseHeader> {
        return flatMap { ResponseHeadersBuilder(resolvers).apply(it).build() }
    }

    private data class ParameterDescription(
        val name: String,
        val type: KType,
        val location: INType,
        val required: Boolean,
        val description: String?
    )

    private data class ResponseTypeDescription(
        val description: String?,
        val type: KType?,
        val headers: List<ResponseHeadersBuilder.() -> Unit> = emptyList()
    )
}
