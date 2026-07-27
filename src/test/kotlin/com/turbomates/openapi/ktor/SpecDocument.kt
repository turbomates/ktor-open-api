package com.turbomates.openapi.ktor

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Navigation over a generated document, so that a test can say what it means instead of matching
 * substrings. The parser the tests validate with reports validity, not shape.
 */
// The same configuration the plugin serializes the document with.
internal val documentJson = Json { encodeDefaults = false }

internal fun String.document(): JsonObject = documentJson.parseToJsonElement(this).jsonObject

internal fun JsonObject.schemas(): Map<String, JsonObject> {
    return getValue("components").jsonObject.getValue("schemas").jsonObject.mapValues { it.value.jsonObject }
}

internal fun JsonObject.schema(name: String): JsonObject = schemas().getValue(name)

internal fun JsonObject.properties(schema: String): Map<String, JsonObject> {
    return schema(schema).getValue("properties").jsonObject.mapValues { it.value.jsonObject }
}

internal fun JsonObject.property(schema: String, property: String): JsonObject {
    return properties(schema).getValue(property)
}

internal fun JsonObject.required(schema: String): List<String> {
    return schema(schema)["required"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }
}

internal fun JsonObject.operation(path: String, method: String = "get"): JsonObject {
    return getValue("paths").jsonObject.getValue(path).jsonObject.getValue(method).jsonObject
}

internal fun JsonObject.responseSchema(path: String, method: String = "get", code: String = "200"): JsonObject {
    return operation(path, method).getValue("responses").jsonObject
        .getValue(code).jsonObject
        .mediaTypeSchema()
}

internal fun JsonObject.requestSchema(path: String, method: String = "post"): JsonObject {
    return operation(path, method).getValue("requestBody").jsonObject.mediaTypeSchema()
}

private fun JsonObject.mediaTypeSchema(): JsonObject {
    return getValue("content").jsonObject
        .getValue("application/json").jsonObject
        .getValue("schema").jsonObject
}

internal fun reference(name: String): JsonObject = buildJsonObject { put("\$ref", "#/components/schemas/$name") }

internal fun JsonObject.type(): String? = get("type")?.jsonPrimitive?.content

internal fun JsonObject.format(): String? = get("format")?.jsonPrimitive?.content

internal fun JsonObject.nullable(): Boolean? = get("nullable")?.jsonPrimitive?.content?.toBoolean()
