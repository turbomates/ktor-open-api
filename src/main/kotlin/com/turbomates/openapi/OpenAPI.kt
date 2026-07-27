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

class OpenAPI(var host: String) {
    val root: Root = Root("3.0.2", InfoObject("Api", version = "0.1.0"))
    private val customTypes: MutableMap<KType, Type> = mutableMapOf()

    fun addToPath(
        path: String,
        method: Method,
        responses: Map<Int, Type> = emptyMap(),
        body: Type.Object? = null,
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
        // they had before HEAD, OPTIONS and TRACE joined the enum.
        fun OperationObject?.mergeOrCreate(documentsBody: Boolean = true): OperationObject {
            return this?.merge(responses, body, declaredParameters, tags) ?: OperationObject(
                responses.mapValues { it.value.toResponseObject() },
                tags = tagsOrNull,
                requestBody = body?.toRequestBodyObject().takeIf { documentsBody },
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

    fun addModel(name: String, model: Type.Object) {
        val components = root.components ?: Components()
        root.components = components.copy(
            schemas = components.schemas.orEmpty().plus(name to model.toSchemaObject())
        )
    }

    fun setCustomClassType(kType: KType, type: Type) {
        customTypes[kType] = type
    }

    private fun Type.toResponseObject(): ResponseObject {
        return ResponseObject(
            "empty description",
            content = mapOf("application/json" to MediaTypeObject(schema = toSchemaObject())),
        )
    }

    private fun Type.toRequestBodyObject(): RequestBodyObject {
        return RequestBodyObject(
            content = mapOf("application/json" to MediaTypeObject(schema = toSchemaObject())),
            required = isRequired
        )
    }

    /**
     * Splits the parameters of an operation into path and query ones by name.
     *
     * A property describes a path parameter when its name is a variable of the path template, and a
     * query parameter otherwise — the shape of the path says nothing about the properties that come
     * with it, so a filter passed alongside a templated path stays in the query where it belongs.
     * A name can only mean one thing per operation, so it is described once even when both
     * [pathParams] and [queryParams] mention it.
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
            ParameterObject(it.name, schema = schema, required = isPath || it.type.isRequired, `in` = `in`.value)
        }
    }

    private fun Type.toSchemaObject(): SchemaObject {
        return when (this) {
            is Type.String -> SchemaObject(type = "string", enum = this.values, example = this.example, nullable = this.isNullable)
            is Type.Array -> SchemaObject(
                type = "array",
                items = this.type.toSchemaObject(),
                enum = this.values,
                nullable = this.isNullable
            )
            is Type.Object ->
                if (customTypes.containsKey(this.returnType) && this.returnType != null) {
                    customTypes.getValue(this.returnType).toSchemaObject()
                } else {
                    SchemaObject(
                        type = "object",
                        properties = this.properties.associate { it.name to it.type.toSchemaObject() },
                        example = this.example,
                        nullable = this.isNullable
                    )
                }

            is Type.Boolean -> SchemaObject(type = "boolean", nullable = this.isNullable)
            is Type.Number -> SchemaObject(type = "number", nullable = this.isNullable)
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
        body: Type.Object? = null,
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
}

data class Property(
    val name: String,
    val type: Type
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
        nullable: kotlin.Boolean = true
    ) : Type(nullable)

    class Array(val type: Type, val values: List<kotlin.String>? = null, nullable: kotlin.Boolean) : Type(nullable)
    class Object(
        val name: kotlin.String,
        val properties: List<Property>,
        val example: JsonElement? = null,
        val returnType: KType? = null,
        nullable: kotlin.Boolean
    ) : Type(nullable)

    class Boolean(nullable: kotlin.Boolean) : Type(nullable)
    class Number(nullable: kotlin.Boolean) : Type(nullable)
}
