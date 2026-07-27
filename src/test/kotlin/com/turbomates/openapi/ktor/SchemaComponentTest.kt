package com.turbomates.openapi.ktor

import com.turbomates.openapi.openApiKType
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.swagger.parser.OpenAPIParser
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import com.turbomates.openapi.OpenAPI as SwaggerOpenAPI

/**
 * Schemas live in `components` and are used through `$ref` (C7), which is also the only way a type
 * that refers to itself can be described at all (B1).
 */
class SchemaComponentTest {
    // The same configuration the plugin serializes the document with.
    private val json = Json { encodeDefaults = false }

    @Test
    fun `a type referring to itself through a collection is described once`() = testApplication {
        install(OpenAPI)
        routing {
            get<Node>("/nodes") { Node("root", emptyList(), null) }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        val document = response.document()
        assertEquals(setOf("Node"), document.schemas().keys)
        assertEquals(reference("Node"), document.responseSchema("/nodes"))
        assertEquals(reference("Node"), document.schemas().property("Node", "children")["items"])
    }

    @Test
    fun `mutually recursive types reference each other`() = testApplication {
        install(OpenAPI)
        routing {
            get<MutualA>("/mutual") { MutualA(MutualB(null)) }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        val schemas = response.document().schemas()
        assertEquals(setOf("MutualA", "MutualB"), schemas.keys)
        assertEquals(reference("MutualB"), schemas.property("MutualA", "b"))
    }

    @Test
    fun `a nullable reference keeps its nullability`() = testApplication {
        install(OpenAPI)
        routing {
            get<Node>("/nodes") { Node("root", emptyList(), null) }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        // A `$ref` ignores its siblings, so nullability wraps the reference instead of sitting
        // next to it. The schema in `components` describes the type and is never nullable itself.
        val parent = response.document().schemas().property("Node", "parent")
        assertEquals(true, parent.nullable())
        assertEquals(reference("Node"), parent.getValue("allOf").jsonArray.single().jsonObject)
        assertEquals(false, response.document().schemas().getValue("Node").nullable())
    }

    @Test
    fun `a type used by two operations is described once`() = testApplication {
        install(OpenAPI)
        routing {
            get<Node>("/first") { Node("first", emptyList(), null) }
            get<Node>("/second") { Node("second", emptyList(), null) }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        val document = response.document()
        assertEquals(setOf("Node"), document.schemas().keys)
        assertEquals(reference("Node"), document.responseSchema("/first"))
        assertEquals(reference("Node"), document.responseSchema("/second"))
    }

    @Test
    fun `two types with the same simple name get components of their own`() = testApplication {
        install(OpenAPI)
        routing {
            get<Accounts.User>("/accounts") { Accounts.User("account") }
            get<Billing.User>("/billing") { Billing.User(1) }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        val document = response.document()
        assertEquals(setOf("User", "User2"), document.schemas().keys)
        // Same name, different types: whichever got the plain name, the two are described apart.
        assertEquals(
            setOf(reference("User"), reference("User2")),
            setOf(document.responseSchema("/accounts"), document.responseSchema("/billing"))
        )
    }

    @Test
    fun `a model added by hand keeps its name and is referenced by it`() {
        val api = SwaggerOpenAPI("localhost")

        api.addModel("Account", typeOf<Accounts.User>().openApiKType.objectType())
        api.addToPath("/accounts", SwaggerOpenAPI.Method.GET, mapOf(200 to typeOf<Accounts.User>().openApiKType.type()))

        val response = json.encodeToString(api.root)
        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        val document = response.document()
        assertEquals(setOf("Account"), document.schemas().keys)
        assertEquals(reference("Account"), document.responseSchema("/accounts"))
    }

    @Test
    fun `an empty document has no components at all`() = testApplication {
        install(OpenAPI)

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        assertNull(response.document()["components"])
    }

    private fun reference(name: String): JsonObject {
        return buildJsonObject { put("\$ref", "#/components/schemas/$name") }
    }

    private fun String.document(): JsonObject = json.parseToJsonElement(this).jsonObject

    private fun JsonObject.schemas(): Map<String, JsonObject> {
        return getValue("components").jsonObject.getValue("schemas").jsonObject.mapValues { it.value.jsonObject }
    }

    private fun Map<String, JsonObject>.property(schema: String, property: String): JsonObject {
        return getValue(schema).getValue("properties").jsonObject.getValue(property).jsonObject
    }

    private fun JsonObject.responseSchema(path: String): JsonObject {
        return getValue("paths").jsonObject.getValue(path).jsonObject
            .getValue("get").jsonObject
            .getValue("responses").jsonObject
            .getValue("200").jsonObject
            .getValue("content").jsonObject
            .getValue("application/json").jsonObject
            .getValue("schema").jsonObject
    }

    private fun JsonObject.nullable(): Boolean = getValue("nullable").jsonPrimitive.boolean

    @Serializable
    data class Node(val name: String, val children: List<Node>, val parent: Node?)

    @Serializable
    data class MutualA(val b: MutualB)

    @Serializable
    data class MutualB(val a: MutualA?)

    object Accounts {
        @Serializable
        data class User(val login: String)
    }

    object Billing {
        @Serializable
        data class User(val invoices: Int)
    }
}
