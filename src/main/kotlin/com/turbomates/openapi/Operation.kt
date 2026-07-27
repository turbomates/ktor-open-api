@file:Suppress("unused")

package com.turbomates.openapi

import com.turbomates.openapi.spec.ExternalDocumentationObject
import com.turbomates.openapi.spec.MediaTypeObject
import com.turbomates.openapi.spec.OperationObject
import com.turbomates.openapi.spec.ParameterObject
import com.turbomates.openapi.spec.RequestBodyObject
import com.turbomates.openapi.spec.ResponseObject
import com.turbomates.openapi.spec.SecurityRequirement

/** Media types this library names by itself. Any other may be given as a string. */
object MediaType {
    const val JSON = "application/json"
    const val FORM = "multipart/form-data"
    const val URL_ENCODED = "application/x-www-form-urlencoded"
    const val OCTET_STREAM = "application/octet-stream"
    const val TEXT = "text/plain"
}

/**
 * Everything about an operation that its types cannot tell.
 *
 * The response and body types of a route say what it carries; a summary, the tags it is grouped
 * under, the header it expects or the error it answers with are decisions of the API, and this is
 * how they reach the document.
 */
data class OperationDescription(
    val tags: List<String> = emptyList(),
    val summary: String? = null,
    val description: String? = null,
    val operationId: String? = null,
    val deprecated: Boolean = false,
    val externalDocs: ExternalDocumentationObject? = null,
    /** Security requirements of this operation, any one of which is enough to satisfy it. */
    val security: List<SecurityRequirement>? = null,
    /** Parameters the route signature says nothing about — headers and cookies. */
    val parameters: List<Parameter> = emptyList(),
    val consumes: List<String> = listOf(MediaType.JSON),
    val produces: List<String> = listOf(MediaType.JSON),
    /** Responses by status code, or by [DEFAULT_RESPONSE] for the one covering the rest. */
    val responses: Map<String, ResponseDescription> = emptyMap()
) {
    companion object {
        /** The response key OpenAPI reserves for every code not listed on its own. */
        const val DEFAULT_RESPONSE = "default"
    }
}

/**
 * A response of an operation.
 *
 * [type] is the body it carries, or `null` for a response with no body at all — a `204`, or a code
 * whose shape is described elsewhere.
 */
data class ResponseDescription(val description: String? = null, val type: Type? = null)

/** A parameter of an operation given by hand rather than read off the route signature. */
data class Parameter(
    val name: String,
    val type: Type,
    val location: INType,
    val required: Boolean = location == INType.PATH,
    val description: String? = null,
    val deprecated: Boolean = false
)

/** Builds the operation of a path item, and merges it with the one already described there. */
internal class OperationFactory(private val schemas: SchemaRegistry) {
    fun operation(
        existing: OperationObject?,
        responses: Map<Int, Type>,
        body: Type?,
        routeParameters: List<ParameterObject>,
        description: OperationDescription
    ): OperationObject {
        val parameters = routeParameters + description.parameters.map { it.toParameterObject() }
        val responseObjects = responses.responseObjects(description)
        val requestBody = body?.toRequestBodyObject(description.consumes)
        return existing?.merge(responseObjects, requestBody, parameters, description)
            ?: OperationObject(
                responses = responseObjects,
                tags = description.tags.takeIf { it.isNotEmpty() },
                summary = description.summary,
                description = description.description,
                externalDocs = description.externalDocs,
                operationId = description.operationId,
                parameters = parameters,
                requestBody = requestBody,
                deprecated = true.takeIf { description.deprecated },
                security = description.security
            )
    }

    /**
     * Describes an operation that was registered before all over again, keeping what it already
     * says.
     *
     * The same path and method may be registered more than once — by another overload of the same
     * verb, or by a route mounted twice. What the document already holds wins, so that a second
     * registration adds to the description instead of overwriting it.
     */
    private fun OperationObject.merge(
        responses: Map<String, ResponseObject>,
        body: RequestBodyObject?,
        declaredParameters: List<ParameterObject>,
        description: OperationDescription
    ): OperationObject {
        // A parameter is identified by its name and location, and an operation may not list the same
        // one twice — registering a path and a method again describes the same parameter, not a new
        // one. The description already in the operation wins.
        val mergedParameters: List<ParameterObject> = parameters?.plus(declaredParameters)
            ?.distinctBy { it.name to it.`in` }
            ?: declaredParameters
        return copy(
            responses = this.responses + responses,
            tags = (tags.orEmpty() + description.tags).distinct().takeIf { it.isNotEmpty() },
            summary = summary ?: description.summary,
            description = this.description ?: description.description,
            externalDocs = externalDocs ?: description.externalDocs,
            operationId = operationId ?: description.operationId,
            parameters = mergedParameters,
            requestBody = body ?: requestBody,
            deprecated = deprecated ?: true.takeIf { description.deprecated },
            security = security ?: description.security
        )
    }

    /**
     * Responses of the operation: the ones derived from the response type, described further by
     * the ones stated by hand.
     *
     * A code stated by hand may add a description, a body of its own, or both; a code the response
     * type says nothing about is described all the same, with no content when no type came with it.
     */
    private fun Map<Int, Type>.responseObjects(description: OperationDescription): Map<String, ResponseObject> {
        val described = mapKeys { it.key.toString() }
            .mapValues { ResponseDescription(type = it.value) }
            .toMutableMap()
        description.responses.forEach { (code, response) ->
            val known = described[code]
            described[code] = ResponseDescription(
                response.description ?: known?.description,
                response.type ?: known?.type
            )
        }
        return described.mapValues { (code, response) -> response.toResponseObject(code, description.produces) }
    }

    private fun ResponseDescription.toResponseObject(code: String, produces: List<String>): ResponseObject {
        return ResponseObject(
            // `description` is required of every response, and an empty one documents nothing, so
            // the meaning of the status code is used until something better is said.
            description = description ?: defaultDescription(code),
            content = type?.let { body ->
                produces.associateWith { MediaTypeObject(schema = schemas.schemaObject(body)) }
            }
        )
    }

    private fun Type.toRequestBodyObject(consumes: List<String>): RequestBodyObject {
        return RequestBodyObject(
            content = consumes.associateWith { MediaTypeObject(schema = schemas.schemaObject(this)) },
            required = isRequired
        )
    }

    private fun Parameter.toParameterObject(): ParameterObject {
        return ParameterObject(
            name,
            schema = schemas.schemaObject(type),
            required = required || location == INType.PATH,
            description = description,
            deprecated = true.takeIf { deprecated },
            `in` = location.value
        )
    }

    private fun defaultDescription(code: String): String {
        return REASON_PHRASES[code] ?: if (code == OperationDescription.DEFAULT_RESPONSE) UNEXPECTED else RESPONSE
    }

    private companion object {
        const val UNEXPECTED = "Unexpected error"
        const val RESPONSE = "Response"

        /** What the common status codes mean, used as the description of a response without one. */
        val REASON_PHRASES = mapOf(
            "200" to "OK",
            "201" to "Created",
            "202" to "Accepted",
            "204" to "No Content",
            "301" to "Moved Permanently",
            "302" to "Found",
            "304" to "Not Modified",
            "400" to "Bad Request",
            "401" to "Unauthorized",
            "403" to "Forbidden",
            "404" to "Not Found",
            "405" to "Method Not Allowed",
            "406" to "Not Acceptable",
            "409" to "Conflict",
            "410" to "Gone",
            "415" to "Unsupported Media Type",
            "422" to "Unprocessable Entity",
            "429" to "Too Many Requests",
            "500" to "Internal Server Error",
            "501" to "Not Implemented",
            "502" to "Bad Gateway",
            "503" to "Service Unavailable",
            "504" to "Gateway Timeout"
        )
    }
}
