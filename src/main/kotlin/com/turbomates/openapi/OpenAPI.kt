@file:Suppress("unused")

package com.turbomates.openapi

import com.turbomates.openapi.spec.Components
import com.turbomates.openapi.spec.ExternalDocumentationObject
import com.turbomates.openapi.spec.InfoObject
import com.turbomates.openapi.spec.OperationObject
import com.turbomates.openapi.spec.ParameterObject
import com.turbomates.openapi.spec.PathItemObject
import com.turbomates.openapi.spec.Root
import com.turbomates.openapi.spec.SecurityRequirement
import com.turbomates.openapi.spec.SecuritySchemeObject
import com.turbomates.openapi.spec.ServerObject
import com.turbomates.openapi.spec.ServerVariableObject
import com.turbomates.openapi.spec.TagObject
import kotlinx.serialization.json.JsonElement
import kotlin.reflect.KType

class OpenAPI(host: String) {
    val root: Root = Root("3.0.2", InfoObject("Api", version = "0.1.0"))

    /** The resolvers every type of this document is described through — see [typeResolver]. */
    val resolvers: TypeResolvers = TypeResolvers()
    private val schemas = SchemaRegistry()
    private val operations = OperationFactory(schemas)
    private val securitySchemes: MutableMap<String, SecuritySchemeObject> = mutableMapOf()
    private val configuredServers: MutableList<ServerObject> = mutableListOf()

    /**
     * Where the API is served from, as the server the document lists until another one is given.
     *
     * A bare host — `api.example.com` — is not a URL, so a scheme is put in front of it: `http` for
     * a local address, `https` for everything else. A value that already is a URL, absolute or
     * relative, is left as it is. Call [server] to describe the servers in full instead.
     */
    var host: String = host
        set(value) {
            field = value
            publishServers()
        }

    /** Metadata of the document — its title, version, description and the rest. */
    val info: InfoObject
        get() = root.info

    init {
        publishServers()
    }

    fun addToPath(
        path: String,
        method: Method,
        responses: Map<Int, Type> = emptyMap(),
        body: Type? = null,
        pathParams: Type.Object? = null,
        queryParams: Type.Object? = null,
        operation: OperationDescription = OperationDescription()
    ) {
        var pathItemObject = root.paths[path]
        if (pathItemObject == null) {
            pathItemObject = PathItemObject()
            root.paths[path] = pathItemObject
        }
        val (pathParamsObjects, queryParamsObjects) = classifyParameters(path, pathParams, queryParams)
        val declaredParameters = pathParamsObjects + queryParamsObjects
        pathItemObject.documentPathTemplate(path, pathParamsObjects)

        // A request body is only described for the methods that carry one; the rest keep the shape
        // they had before HEAD, OPTIONS and TRACE joined the enum. The body is dropped before the
        // merge as well, so that a method without one stays without one however many times its path
        // was registered.
        fun describe(existing: OperationObject?, documentsBody: Boolean = true): OperationObject {
            return operations.operation(
                existing,
                responses,
                body.takeIf { documentsBody },
                declaredParameters,
                operation
            )
        }

        when (method) {
            Method.GET -> pathItemObject.get = describe(pathItemObject.get, documentsBody = false)
            Method.HEAD -> pathItemObject.head = describe(pathItemObject.head, documentsBody = false)
            Method.OPTIONS -> pathItemObject.options = describe(pathItemObject.options, documentsBody = false)
            Method.TRACE -> pathItemObject.trace = describe(pathItemObject.trace, documentsBody = false)
            Method.POST -> pathItemObject.post = describe(pathItemObject.post)
            Method.PUT -> pathItemObject.put = describe(pathItemObject.put)
            Method.PATCH -> pathItemObject.patch = describe(pathItemObject.patch)
            Method.DELETE -> pathItemObject.delete = describe(pathItemObject.delete)
        }
        publishComponents()
    }

    /**
     * Describes [model] in `components.schemas` under [name].
     *
     * Every use of the same type is a reference to this schema afterwards, [name] included — a
     * model registered by hand keeps the name it was given instead of the one derived from the type.
     */
    fun addModel(name: String, model: Type.Object) {
        schemas.addModel(name, model)
        publishComponents()
    }

    /**
     * Describes [kType] the way this document does — through its resolvers first, and through
     * reflection for everything they say nothing about.
     */
    fun describe(kType: KType): Type = kType.openApiKType(resolvers).type()

    /**
     * Describes [kType] as an object, which is what the parameters of an operation have to be.
     *
     * Throws [InvalidTypeForOpenApiType] when the description is anything else — including when a
     * resolver made it so.
     */
    fun describeObject(kType: KType): Type.Object = kType.openApiKType(resolvers).objectType()

    /**
     * Describes the types this API names for itself, before reflection gets to read them.
     *
     * ```
     * openApi.typeResolver { kType ->
     *     when (kType.classifier) {
     *         Money::class -> Type.String(format = "money", nullable = kType.isMarkedNullable)
     *         else -> null
     *     }
     * }
     * ```
     *
     * Resolvers are asked in the order they were added, and the first one to answer wins. A type
     * meets them wherever it turns up — as a body, as a response, as a property nested inside one,
     * as an element of a collection — so a type is described the same way throughout the document.
     */
    fun typeResolver(resolver: TypeResolver) {
        resolvers.add(resolver)
    }

    /** Describes [kType] as [type], whichever nullability it is used with. A resolver of one type. */
    fun setCustomClassType(kType: KType, type: Type) {
        resolvers.add(kType, type)
    }

    /**
     * Headers every response of every operation carries.
     *
     * ```
     * openApi.globalResponseHeaders {
     *     header("X-Request-Id", Type.String(), "Request correlation id")
     *     header<Int>("X-Rate-Limit-Remaining", "Calls left in the current window")
     * }
     * ```
     *
     * An operation of its own, or a single response of it, may describe a header of the same name
     * differently, and the one closer to the response wins. The headers reach the operations
     * described from here on, so they are best stated before the routes are registered — which is
     * what configuring the plugin at installation does.
     */
    fun globalResponseHeaders(block: ResponseHeadersBuilder.() -> Unit) {
        globalResponseHeaders(ResponseHeadersBuilder(resolvers).apply(block).build())
    }

    fun globalResponseHeaders(headers: List<ResponseHeader>) {
        operations.globalResponseHeaders = mergeResponseHeaders(operations.globalResponseHeaders, headers)
    }

    /** Describes the document itself — `openApi.info { title = "Orders"; version = "2.0" }`. */
    fun info(block: InfoObject.() -> Unit) {
        root.info.apply(block)
    }

    /**
     * Adds a server the API is offered at.
     *
     * The first call replaces the server derived from [host]: a document either describes its
     * servers or falls back on the host it was built with, never both.
     */
    fun server(
        url: String,
        description: String? = null,
        variables: Map<String, ServerVariableObject>? = null
    ) {
        configuredServers.add(ServerObject(url, description, variables))
        publishServers()
    }

    /** Describes a tag the operations are grouped by. Operations may use tags described here or not. */
    fun tag(name: String, description: String? = null, externalDocs: ExternalDocumentationObject? = null) {
        root.tags = root.tags.orEmpty() + TagObject(name, description, externalDocs)
    }

    fun externalDocs(url: String, description: String? = null) {
        root.externalDocs = ExternalDocumentationObject(url, description)
    }

    /** Offers a security scheme operations may require. Build one with [SecurityScheme]. */
    fun securityScheme(name: String, scheme: SecuritySchemeObject) {
        securitySchemes[name] = scheme
        publishComponents()
    }

    /**
     * Requires [requirements] of every operation that does not state its own.
     *
     * Satisfying any one of them is enough; pass none to say that the API is open by default,
     * which is what an operation of a document without global security means anyway.
     */
    fun security(vararg requirements: SecurityRequirement) {
        root.security = requirements.toList()
    }

    /**
     * Splits the parameters of an operation into path and query ones by name.
     *
     * A property describes a path parameter when its name is a variable of the path template, and a
     * query parameter otherwise — the shape of the path says nothing about the properties that come
     * with it, so a filter passed alongside a templated path stays in the query where it belongs.
     *
     * OpenAPI identifies a parameter by its name together with its location, so the same name may
     * legitimately appear in both. This classification is narrower on purpose: a property describes
     * one parameter, in one location, so a name mentioned by both [pathParams] and [queryParams] is
     * described once — under the path template when it matches one, and in the query otherwise.
     * Ktor resolves such a name to the path segment at request time anyway.
     */
    private fun classifyParameters(
        path: String,
        pathParams: Type.Object?,
        queryParams: Type.Object?
    ): Pair<List<ParameterObject>, List<ParameterObject>> {
        val templateVariables = path.pathTemplateVariables().toSet()
        val declared = (pathParams?.properties.orEmpty() + queryParams?.properties.orEmpty()).distinctBy { it.name }
        val (inPath, inQuery) = declared.partition { templateVariables.contains(it.name) }
        return inPath.toParameterObject(INType.PATH) to inQuery.toParameterObject(INType.QUERY)
    }

    @Suppress("FunctionParameterNaming", "UnusedPrivateMember")
    private fun List<Property>.toParameterObject(`in`: INType): List<ParameterObject> {
        val isPath = `in` == INType.PATH
        return map {
            // A path parameter is part of the URL, so it can be neither optional nor null,
            // whatever the nullability of the property describing it.
            val schema = schemas.schemaObject(it.type).let { schema -> if (isPath) schema.copy(nullable = false) else schema }
            // A query parameter the caller may leave out is optional for the same reason a property
            // with a default is: there is a value to fall back on.
            ParameterObject(it.name, schema = schema, required = isPath || it.isRequired, `in` = `in`.value)
        }
    }

    /** Methods a path item can describe. */
    enum class Method { GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS, TRACE }

    /**
     * Describes every variable of the path template as a path item parameter.
     *
     * OpenAPI requires each path template variable to be documented with `in: path`, while the
     * routing DSL only knows about the ones the caller passed explicitly through `pathParams`.
     * Variables missing from [operationParameters] are documented as required strings on the path
     * item, so that the spec stays valid no matter which overload was used.
     */
    private fun PathItemObject.documentPathTemplate(path: String, operationParameters: List<ParameterObject>) {
        val documented = parameters.orEmpty()
        val documentedNames = (documented + operationParameters)
            .filter { it.`in` == INType.PATH.value }
            .map { it.name }
            .toSet()
        val missing = path.pathTemplateVariables()
            .filterNot { documentedNames.contains(it) }
            .map { name ->
                ParameterObject(
                    name,
                    schema = schemas.schemaObject(Type.String(nullable = false)),
                    required = true,
                    `in` = INType.PATH.value
                )
            }
        if (missing.isNotEmpty()) {
            parameters = documented + missing
        }
    }

    private fun publishComponents() {
        root.components = (root.components ?: Components()).copy(
            schemas = schemas.schemas.takeIf { it.isNotEmpty() },
            securitySchemes = securitySchemes.toMap().takeIf { it.isNotEmpty() }
        )
    }

    private fun publishServers() {
        root.servers = configuredServers.toList().ifEmpty { listOf(ServerObject(host.asServerUrl())) }
    }

    private fun String.asServerUrl(): String {
        if (contains(SCHEME_SEPARATOR) || startsWith("/")) {
            return this
        }
        val scheme = if (LOCAL_HOSTS.any { this == it || startsWith("$it:") }) "http" else "https"
        return "$scheme$SCHEME_SEPARATOR$this"
    }

    private companion object {
        const val SCHEME_SEPARATOR = "://"
        val LOCAL_HOSTS = listOf("localhost", "127.0.0.1", "0.0.0.0", "[::1]")
    }
}

data class Property(
    val name: String,
    val type: Type,
    /**
     * Whether the key has to be there at all, which is not the same as being allowed to be `null`.
     *
     * Unless said otherwise, a property is required exactly when it cannot be `null` — that is all
     * a type alone can tell.
     */
    val isRequired: Boolean = !type.isNullable
)

enum class INType(val value: String) {
    PATH("path"),
    QUERY("query"),
    HEADER("header"),
    COOKIE("cookie")
}

sealed class Type(val isNullable: kotlin.Boolean = true) {
    val isRequired: kotlin.Boolean
        get() = !isNullable

    class String(
        val values: List<kotlin.String>? = null,
        val example: JsonElement? = null,
        nullable: kotlin.Boolean = true,
        val format: kotlin.String? = null
    ) : Type(nullable)

    class Array(val type: Type, val values: List<kotlin.String>? = null, nullable: kotlin.Boolean) : Type(nullable)
    class Object(
        val name: kotlin.String,
        val properties: List<Property>,
        val example: JsonElement? = null,
        val returnType: KType? = null,
        nullable: kotlin.Boolean
    ) : Type(nullable)

    /**
     * A type described elsewhere in the document.
     *
     * This is how a type that refers to itself is described: the schema of the enclosing object is
     * built once, and the property that points back at it points at that schema instead of starting
     * another copy of it.
     */
    class Ref(
        val name: kotlin.String,
        val returnType: KType,
        nullable: kotlin.Boolean
    ) : Type(nullable)

    class Boolean(nullable: kotlin.Boolean = true) : Type(nullable)
    class Number(nullable: kotlin.Boolean = true, val format: kotlin.String? = null) : Type(nullable)

    /** A whole number, which OpenAPI keeps apart from `number`. */
    class Integer(nullable: kotlin.Boolean = true, val format: kotlin.String? = null) : Type(nullable)

    /**
     * An object of keys that are not known in advance, each holding a [valueType].
     *
     * The keys of a JSON object are strings whatever the key type is, so only the value type is
     * described.
     */
    class Map(val valueType: Type, nullable: kotlin.Boolean) : Type(nullable)

    /**
     * One of several types, told apart by [discriminator] when there is something to tell them
     * apart by.
     *
     * [options] is keyed by the value the discriminator property carries for each of them — the
     * serial name of the type, for a `sealed` hierarchy written by `kotlinx.serialization`.
     */
    class OneOf(
        val options: kotlin.collections.Map<kotlin.String, Type>,
        val discriminator: kotlin.String? = null,
        val returnType: KType? = null,
        nullable: kotlin.Boolean
    ) : Type(nullable)

    /** A value nothing is known about — an unresolved type parameter or a star projection. */
    class Any(nullable: kotlin.Boolean = true) : Type(nullable)
}

/**
 * Formats this library describes types with.
 *
 * `format` is an open string in OpenAPI: these are the ones that are generated on their own, and
 * any other may be given through `customTypeDescription`.
 */
object Format {
    const val INT32 = "int32"
    const val INT64 = "int64"
    const val FLOAT = "float"
    const val DOUBLE = "double"
    const val UUID = "uuid"
    const val DATE = "date"
    const val DATE_TIME = "date-time"
    const val TIME = "time"
    const val DURATION = "duration"
    const val BINARY = "binary"
    const val URI = "uri"
}
