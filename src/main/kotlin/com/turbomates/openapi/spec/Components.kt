package com.turbomates.openapi.spec

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class Components(
    val schemas: Map<String, SchemaObject>? = null,
    val responses: Map<String, ResponseObject>? = null,
    val parameters: Map<String, ParameterObject>? = null,
    val examples: Map<String, ExampleObject>? = null,
    val requestBodies: Map<String, RequestBodyObject>? = null,
    val headers: Map<String, HeaderObject>? = null,
    val securitySchemes: Map<String, SecuritySchemeObject>? = null,
    val links: Map<String, LinkObject>? = null,
    val callbacks: Map<String, CallbackObject>? = null
)

@Serializable
data class SchemaObject(
    val nullable: Boolean? = null,
    val discriminator: DiscriminatorObject? = null,
    val readOnly: Boolean? = null,
    val writeOnly: Boolean? = null,
    val `$ref`: String? = null,
//    val xml: XMLObject,
    val externalDocs: ExternalDocumentationObject? = null,
    val example: JsonElement? = null,
    val type: String? = null,
    val format: String? = null,
    val allOf: List<SchemaObject>? = null,
    val oneOf: List<SchemaObject>? = null,
    val properties: Map<String, SchemaObject>? = null,
    val required: List<String>? = null,
    val additionalProperties: SchemaObject? = null,
    val items: SchemaObject? = null,
    val deprecated: Boolean? = null,
    val enum: List<String>? = null
)

@Serializable
data class ResponseObject(
    val description: String? = null,
    val headers: Map<String, HeaderObject>? = null,
    val content: Map<String, MediaTypeObject>? = null,
    val links: Map<String, LinkObject>? = null
)

@Serializable
data class ParameterObject(
    val name: String,
    val `in`: String? = null,
    val description: String? = null,
    val required: Boolean? = null,
    val deprecated: Boolean? = null,
    val allowEmptyValue: Boolean? = null,
    val style: String? = null,
    val explode: Boolean? = null,
    val allowReserved: Boolean? = null,
    val schema: SchemaObject,
    @Contextual val example: Any? = null,
    val examples: Map<String, ExampleObject>? = null
)

@Serializable
data class ExampleObject(
    val summary: String? = null,
    val description: String? = null,
    @Contextual val value: Any? = null,
    val externalValue: String? = null
)

@Serializable
data class RequestBodyObject(
    val description: String? = null,
    val content: Map<String, MediaTypeObject>,
    val required: Boolean? = null
)

@Serializable
data class DiscriminatorObject(val propertyName: String, val mapping: Map<String, String>? = null)

@Serializable
data class MediaTypeObject(
    val schema: SchemaObject? = null,
    @Contextual val example: Any? = null,
    val examples: Map<String, ExampleObject>? = null,
    val encoding: Map<String, EncodingObject>? = null
)

/**
 * A header carried by a response, an encoding or a component.
 *
 * A header is described exactly like a parameter, save for `name` and `in`: it is named by the key
 * it is listed under, and its location is the one the map it belongs to implies. [schema] is
 * required for the same reason a parameter's is — a header describes a value, and OpenAPI has
 * nowhere else here to say what that value looks like.
 */
@Serializable
data class HeaderObject(
    val schema: SchemaObject,
    val description: String? = null,
    val required: Boolean? = null,
    val deprecated: Boolean? = null,
    val allowEmptyValue: Boolean? = null,
    val style: String? = null,
    val explode: Boolean? = null,
    val allowReserved: Boolean? = null,
    @Contextual val example: Any? = null,
    val examples: Map<String, ExampleObject>? = null
)

/**
 * A security scheme the document offers.
 *
 * Everything but [type] is optional, and each field belongs to some of the scheme kinds only —
 * `scheme` and `bearerFormat` to `http`, `name` and `in` to `apiKey`, `flows` to `oauth2`,
 * `openIdConnectUrl` to `openIdConnect`. A field belonging to another kind is not merely useless
 * but rejected by validators, so none of them has a value to fall back on and a scheme is best
 * built through [com.turbomates.openapi.SecurityScheme].
 */
@Serializable
data class SecuritySchemeObject(
    val type: String,
    val description: String? = null,
    val name: String? = null,
    val `in`: String? = null,
    val scheme: String? = null,
    val bearerFormat: String? = null,
    val flows: OAuthFlowsObject? = null,
    val openIdConnectUrl: String? = null
)

/** The OAuth2 flows a scheme supports, each described on its own. */
@Serializable
data class OAuthFlowsObject(
    val implicit: OAuthFlowObject? = null,
    val password: OAuthFlowObject? = null,
    val clientCredentials: OAuthFlowObject? = null,
    val authorizationCode: OAuthFlowObject? = null
)

/**
 * One OAuth2 flow.
 *
 * Which URLs a flow has depends on the flow — an implicit one has an authorization URL and no token
 * URL, client credentials the other way round — so both are optional here and filled in by the
 * [com.turbomates.openapi.SecurityScheme] builder that knows which flow it is describing.
 */
@Serializable
data class OAuthFlowObject(
    val authorizationUrl: String? = null,
    val tokenUrl: String? = null,
    val refreshUrl: String? = null,
    val scopes: Map<String, String> = emptyMap()
)

/**
 * Security schemes a request has to satisfy, keyed by the name of the scheme.
 *
 * The list held against a name is the scopes an `oauth2` or `openIdConnect` scheme requires, and is
 * empty for every other kind.
 */
typealias SecurityRequirement = Map<String, List<String>>

@Serializable
data class LinkObject(
    val operationRef: String? = null,
    val operationId: String? = null,
    val parameters: Map<String, @Contextual Any>? = null,
    @Contextual val requestBody: Any? = null,
    val description: String? = null,
    val server: ServerObject? = null
)

/**
 * Requests the API sends out on its own, keyed by the runtime expression saying where to send them.
 *
 * The expression — `{$request.body#/callbackUrl}` and the like — is the key of the map, so a
 * callback *is* that map rather than an object wrapping one.
 */
typealias CallbackObject = Map<String, PathItemObject>

@Serializable
data class PathItemObject(
    var `$ref`: String? = null,
    var summary: String? = null,
    var description: String? = null,
    var get: OperationObject? = null,
    var post: OperationObject? = null,
    var put: OperationObject? = null,
    var delete: OperationObject? = null,
    var options: OperationObject? = null,
    var head: OperationObject? = null,
    var patch: OperationObject? = null,
    var trace: OperationObject? = null,
    var servers: List<ServerObject>? = null,
    var parameters: List<ParameterObject>? = null
)

@Serializable
data class OperationObject(
    /**
     * Responses of the operation, keyed by status code or by `default` for the one describing every
     * code not listed — a key that is not a number, which is why the codes are strings as well.
     */
    val responses: Map<String, ResponseObject>,
    val tags: List<String>? = null,
    val summary: String? = null,
    val description: String? = null,
    val externalDocs: ExternalDocumentationObject? = null,
    val operationId: String? = null,
    val parameters: List<ParameterObject>? = null,
    val requestBody: RequestBodyObject? = null,
    val callbacks: Map<String, CallbackObject>? = null,
    val deprecated: Boolean? = null,
    @SerialName("security") val security: List<SecurityRequirement>? = null,
    val servers: List<ServerObject>? = null
)

@Serializable
data class ExternalDocumentationObject(val url: String, val description: String? = null)

@Serializable
data class EncodingObject(
    val contentType: String? = null,
    val headers: Map<String, HeaderObject>? = null,
    val style: String? = null,
    val explode: Boolean? = null,
    val allowReserved: Boolean? = null
)
