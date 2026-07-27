@file:Suppress("unused")

package com.turbomates.openapi

import com.turbomates.openapi.spec.Components
import com.turbomates.openapi.spec.InfoObject
import com.turbomates.openapi.spec.MediaTypeObject
import com.turbomates.openapi.spec.OperationObject
import com.turbomates.openapi.spec.ParameterObject
import com.turbomates.openapi.spec.PathItemObject
import com.turbomates.openapi.spec.RequestBodyObject
import com.turbomates.openapi.spec.ResponseObject
import com.turbomates.openapi.spec.Root
import com.turbomates.openapi.spec.SchemaObject
import kotlinx.serialization.json.JsonElement
import kotlin.reflect.KType
import kotlin.reflect.full.withNullability
import kotlin.reflect.jvm.jvmErasure

class OpenAPI(var host: String) {
    val root: Root = Root("3.0.2", InfoObject("Api", version = "0.1.0"))
    private val customTypes: MutableMap<KType, Type> = mutableMapOf()

    /** Component name every described type is registered under, keyed by the type itself. */
    private val componentNames: MutableMap<KType, String> = mutableMapOf()
    private val schemas: MutableMap<String, SchemaObject> = mutableMapOf()

    fun addToPath(
        path: String,
        method: Method,
        responses: Map<Int, Type> = emptyMap(),
        body: Type? = null,
        pathParams: Type.Object? = null,
        queryParams: Type.Object? = null,
        tags: List<String> = emptyList()
    ) {
        var pathItemObject = root.paths[path]
        if (pathItemObject == null) {
            pathItemObject = PathItemObject()
            root.paths[path] = pathItemObject
        }
        val (pathParamsObjects, queryParamsObjects) = classifyParameters(path, pathParams, queryParams)
        val declaredParameters = pathParamsObjects + queryParamsObjects
        pathItemObject.documentPathTemplate(path, pathParamsObjects)
        val tagsOrNull = tags.takeIf { it.isNotEmpty() }

        // A request body is only described for the methods that carry one; the rest keep the shape
        // they had before HEAD, OPTIONS and TRACE joined the enum. The body is dropped before the
        // merge as well, so that a method without one stays without one however many times its path
        // was registered.
        fun OperationObject?.mergeOrCreate(documentsBody: Boolean = true): OperationObject {
            val documentedBody = body?.takeIf { documentsBody }
            return this?.merge(responses, documentedBody, declaredParameters, tags) ?: OperationObject(
                responses.mapValues { it.value.toResponseObject() },
                tags = tagsOrNull,
                requestBody = documentedBody?.toRequestBodyObject(),
                parameters = declaredParameters
            )
        }

        when (method) {
            Method.GET -> pathItemObject.get = pathItemObject.get.mergeOrCreate(documentsBody = false)
            Method.HEAD -> pathItemObject.head = pathItemObject.head.mergeOrCreate(documentsBody = false)
            Method.OPTIONS -> pathItemObject.options = pathItemObject.options.mergeOrCreate(documentsBody = false)
            Method.TRACE -> pathItemObject.trace = pathItemObject.trace.mergeOrCreate(documentsBody = false)
            Method.POST -> pathItemObject.post = pathItemObject.post.mergeOrCreate()
            Method.PUT -> pathItemObject.put = pathItemObject.put.mergeOrCreate()
            Method.PATCH -> pathItemObject.patch = pathItemObject.patch.mergeOrCreate()
            Method.DELETE -> pathItemObject.delete = pathItemObject.delete.mergeOrCreate()
        }
    }

    /**
     * Describes [model] in `components.schemas` under [name].
     *
     * Every use of the same type is a reference to this schema afterwards, [name] included — a
     * model registered by hand keeps the name it was given instead of the one derived from the type.
     */
    fun addModel(name: String, model: Type.Object) {
        model.returnType?.let { componentNames[it.withNullability(false)] = name }
        schemas[name] = model.objectSchemaObject(nullable = false)
        publishComponents()
    }

    fun setCustomClassType(kType: KType, type: Type) {
        customTypes[kType] = type
    }

    private fun Type.toResponseObject(): ResponseObject {
        return ResponseObject(
            "empty description",
            content = mapOf(JSON_MEDIA_TYPE to MediaTypeObject(schema = toSchemaObject())),
        )
    }

    private fun Type.toRequestBodyObject(): RequestBodyObject {
        return RequestBodyObject(
            content = mapOf(JSON_MEDIA_TYPE to MediaTypeObject(schema = toSchemaObject())),
            required = isRequired
        )
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
            val schema = it.type.toSchemaObject().let { schema -> if (isPath) schema.copy(nullable = false) else schema }
            // A query parameter the caller may leave out is optional for the same reason a property
            // with a default is: there is a value to fall back on.
            ParameterObject(it.name, schema = schema, required = isPath || it.isRequired, `in` = `in`.value)
        }
    }

    private fun Type.toSchemaObject(): SchemaObject {
        return when (this) {
            is Type.String -> SchemaObject(
                type = "string",
                format = this.format,
                enum = this.values,
                example = this.example,
                nullable = this.isNullable
            )
            is Type.Array -> SchemaObject(
                type = "array",
                items = this.type.toSchemaObject(),
                enum = this.values,
                nullable = this.isNullable
            )
            is Type.Object -> when {
                this.returnType != null && customTypes.containsKey(this.returnType) ->
                    customTypes.getValue(this.returnType).toSchemaObject()
                // A type known by reflection is described once in `components` and referenced from
                // everywhere it is used; one made up on the spot has nothing to be keyed by, so it
                // stays where it is.
                this.returnType != null -> componentSchemaObject(this.returnType)
                else -> objectSchemaObject(nullable = this.isNullable)
            }

            is Type.Map -> SchemaObject(
                type = OBJECT_TYPE,
                additionalProperties = this.valueType.toSchemaObject(),
                nullable = this.isNullable
            )

            is Type.Ref -> referenceSchemaObject(componentName(this.returnType), this.isNullable)
            is Type.Boolean -> SchemaObject(type = "boolean", nullable = this.isNullable)
            is Type.Number -> SchemaObject(type = "number", format = this.format, nullable = this.isNullable)
            is Type.Integer -> SchemaObject(type = "integer", format = this.format, nullable = this.isNullable)
            // An empty schema is how OpenAPI describes a value it knows nothing about.
            is Type.Any -> SchemaObject(nullable = this.isNullable)
        }
    }

    private fun Type.Object.objectSchemaObject(nullable: kotlin.Boolean?): SchemaObject {
        return SchemaObject(
            type = OBJECT_TYPE,
            properties = properties.associate { it.name to it.type.toSchemaObject() },
            required = properties.filter { it.isRequired }.map { it.name }.takeIf { it.isNotEmpty() },
            example = example,
            nullable = nullable
        )
    }

    /**
     * Registers the schema of this object in `components.schemas` and returns a reference to it.
     *
     * The schema itself is never nullable: it describes the type, while being allowed to be `null`
     * is a property of the place the type is used at, and the same type is used in both ways. The
     * name is taken before the properties are described, so that a type referring to itself finds
     * the name of the schema it is part of instead of describing it over again.
     */
    private fun Type.Object.componentSchemaObject(type: KType): SchemaObject {
        val name = componentName(type)
        if (!schemas.containsKey(name)) {
            schemas[name] = objectSchemaObject(nullable = false)
            publishComponents()
        }
        return referenceSchemaObject(name, nullable = isNullable)
    }

    /**
     * A reference to a component schema, made nullable where the use site asks for it.
     *
     * A `$ref` ignores everything next to it in OpenAPI 3.0, so nullability cannot be stated
     * alongside — it has to wrap the reference instead.
     */
    private fun referenceSchemaObject(name: String, nullable: kotlin.Boolean): SchemaObject {
        val reference = SchemaObject(`$ref` = "$COMPONENT_SCHEMA_PATH$name")
        return if (nullable) SchemaObject(nullable = true, allOf = listOf(reference)) else reference
    }

    /**
     * Component name of [type], assigned once and kept.
     *
     * The name has to be unique within the document and may only contain letters, digits and
     * `.`, `-`, `_`, so it is built from the name of the class and of its type arguments, and a
     * counter is added when two different types happen to arrive at the same name.
     */
    private fun componentName(type: KType): String {
        val key = type.withNullability(false)
        componentNames[key]?.let { return it }
        val base = key.componentBaseName()
        var name = base
        var attempt = 1
        while (componentNames.containsValue(name)) {
            attempt++
            name = "$base$attempt"
        }
        componentNames[key] = name
        return name
    }

    private fun KType.componentBaseName(): String {
        val arguments = arguments.mapNotNull { it.type?.jvmErasure?.simpleName }.joinToString("")
        return FORBIDDEN_IN_COMPONENT_NAME.replace(jvmErasure.simpleName.orEmpty() + arguments, "")
            .ifEmpty { DEFAULT_COMPONENT_NAME }
    }

    private fun publishComponents() {
        root.components = (root.components ?: Components()).copy(schemas = schemas.toMap())
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
                    schema = Type.String(nullable = false).toSchemaObject(),
                    required = true,
                    `in` = INType.PATH.value
                )
            }
        if (missing.isNotEmpty()) {
            parameters = documented + missing
        }
    }

    private fun OperationObject.merge(
        responses: Map<Int, Type>,
        body: Type? = null,
        declaredParameters: List<ParameterObject> = emptyList(),
        tags: List<String> = emptyList()
    ): OperationObject {
        // A parameter is identified by its name and location, and an operation may not list the same
        // one twice — registering a path and a method again describes the same parameter, not a new
        // one. The description already in the operation wins.
        val parameters: List<ParameterObject> = parameters?.plus(declaredParameters)
            ?.distinctBy { it.name to it.`in` }
            ?: declaredParameters
        val bodyResult = body?.toRequestBodyObject() ?: this.requestBody
        val responsesResult = this.responses + responses.mapValues { it.value.toResponseObject() }
        val mergedTags = (this.tags.orEmpty() + tags).distinct().takeIf { it.isNotEmpty() }
        return copy(parameters = parameters, requestBody = bodyResult, responses = responsesResult, tags = mergedTags)
    }

    private companion object {
        /** The only media type responses and request bodies are described with so far (see C11). */
        const val JSON_MEDIA_TYPE = "application/json"
        const val COMPONENT_SCHEMA_PATH = "#/components/schemas/"
        const val OBJECT_TYPE = "object"

        /** A component name may only hold `[a-zA-Z0-9._-]`, and a class name may hold more. */
        val FORBIDDEN_IN_COMPONENT_NAME = Regex("[^A-Za-z0-9._-]")
        const val DEFAULT_COMPONENT_NAME = "Schema"
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
    HEADER("header")
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

    class Boolean(nullable: kotlin.Boolean) : Type(nullable)
    class Number(nullable: kotlin.Boolean, val format: kotlin.String? = null) : Type(nullable)

    /** A whole number, which OpenAPI keeps apart from `number`. */
    class Integer(nullable: kotlin.Boolean, val format: kotlin.String? = null) : Type(nullable)

    /**
     * An object of keys that are not known in advance, each holding a [valueType].
     *
     * The keys of a JSON object are strings whatever the key type is, so only the value type is
     * described.
     */
    class Map(val valueType: Type, nullable: kotlin.Boolean) : Type(nullable)

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
