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
import kotlin.reflect.KClass

class OpenAPI(var host: String) {
    val root: Root = Root("3.0.2", InfoObject("Api", version = "0.1.0"))
    private val customTypes: MutableMap<String, Type> = mutableMapOf()

    fun addToPath(
        path: String,
        method: Method,
        responses: Map<Int, Type> = emptyMap(),
        body: Type.Object? = null,
        pathParams: Type.Object? = null,
        queryParams: Type.Object? = null
    ) {
        var pathItemObject = root.paths[path]
        if (pathItemObject == null) {
            pathItemObject = PathItemObject()
            root.paths[path] = pathItemObject
        }
        val pathParamsObjects = pathParams?.toParameterObject(INType.PATH).orEmpty()
        val queryParamsObjects = queryParams?.toParameterObject(INType.QUERY).orEmpty()
        when (method) {
            Method.GET ->
                pathItemObject.get = pathItemObject.get?.merge(responses, body, pathParams, queryParams) ?: OperationObject(
                    responses.mapValues { it.value.toResponseObject() },
                    parameters = pathParamsObjects + queryParamsObjects
                )
            Method.POST ->
                pathItemObject.post = pathItemObject.post?.merge(responses, body, pathParams, queryParams) ?: OperationObject(
                    responses.mapValues { it.value.toResponseObject() },
                    requestBody = body?.toRequestBodyObject(),
                    parameters = pathParamsObjects + queryParamsObjects
                )
            Method.DELETE ->
                pathItemObject.delete = pathItemObject.delete?.merge(responses, body, pathParams, queryParams) ?: OperationObject(
                    responses.mapValues { it.value.toResponseObject() },
                    requestBody = body?.toRequestBodyObject(),
                    parameters = pathParamsObjects + queryParamsObjects
                )
            Method.PATCH ->
                pathItemObject.patch = pathItemObject.patch?.merge(responses, body, pathParams, queryParams) ?: OperationObject(
                    responses.mapValues { it.value.toResponseObject() },
                    requestBody = body?.toRequestBodyObject(),
                    parameters = pathParamsObjects + queryParamsObjects
                )
        }
    }

    fun addModel(name: String, model: Type.Object) {
        val components = root.components ?: Components()
        root.components = components.copy(
            schemas = components.schemas.orEmpty().plus(name to model.toSchemaObject())
        )
    }

    fun setCustomClassType(clazz: KClass<*>, type: Type) {
        customTypes[clazz.qualifiedName!!] = type
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

    @Suppress("FunctionParameterNaming", "UnusedPrivateMember")
    private fun Type.Object.toParameterObject(`in`: INType): List<ParameterObject> {
        return properties.map {
            ParameterObject(it.name, schema = it.type.toSchemaObject(), required = it.type.isRequired, `in` = `in`.value)
        }
    }

    private fun Type.toSchemaObject(): SchemaObject {
        return when (this) {
            is Type.String -> SchemaObject(type = "string", enum = this.values, example = this.example, nullable = this.nullable)
            is Type.Array -> SchemaObject(type = "array", items = this.type.toSchemaObject(), enum = this.values, nullable = this.nullable)
            is Type.Object ->
                if (customTypes.containsKey(this.returnType) && this.returnType != null) {
                    customTypes.getValue(this.returnType).toSchemaObject()
                } else {
                    SchemaObject(
                        type = "object",
                        properties = this.properties.associate { it.name to it.type.toSchemaObject() },
                        example = this.example,
                        nullable = this.nullable
                    )
                }
            is Type.Boolean -> SchemaObject(type = "boolean", nullable = this.nullable)
            is Type.Number -> SchemaObject(type = "number", nullable = this.nullable)
        }
    }

    enum class Method { GET, POST, DELETE, PATCH }

    private fun OperationObject.merge(
        responses: Map<Int, Type>,
        body: Type.Object? = null,
        pathParams: Type.Object? = null,
        queryParams: Type.Object? = null,
    ): OperationObject {
        val pathParameterObjects = pathParams?.toParameterObject(INType.PATH).orEmpty()
        val queryParameterObjects = queryParams?.toParameterObject(INType.QUERY).orEmpty()
        val parameters: List<ParameterObject> =
            parameters?.run { plus(pathParameterObjects).plus(queryParameterObjects) } ?: pathParameterObjects.plus(queryParameterObjects)
        val bodyResult = body?.toRequestBodyObject() ?: this.requestBody
        val responsesResult = this.responses + responses.mapValues { it.value.toResponseObject() }
        return copy(parameters = parameters, requestBody = bodyResult, responses = responsesResult)
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

sealed class Type(val nullable: kotlin.Boolean = true) {
    val isRequired: kotlin.Boolean
        get() = !nullable

    class String(val values: List<kotlin.String>? = null, val example: JsonElement? = null, nullable: kotlin.Boolean) : Type(nullable)
    class Array(val type: Type, val values: List<kotlin.String>? = null, nullable: kotlin.Boolean) : Type(nullable)
    class Object(
        val name: kotlin.String,
        val properties: List<Property>,
        val example: JsonElement? = null,
        val returnType: kotlin.String? = null,
        nullable: kotlin.Boolean
    ) : Type(nullable)

    class Boolean(nullable: kotlin.Boolean) : Type(nullable)
    class Number(nullable: kotlin.Boolean) : Type(nullable)
}
