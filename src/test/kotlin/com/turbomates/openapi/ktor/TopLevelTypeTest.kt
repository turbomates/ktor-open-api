package com.turbomates.openapi.ktor

import com.turbomates.openapi.InvalidTypeForOpenApiType
import com.turbomates.openapi.Type
import com.turbomates.openapi.openApiKType
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.swagger.parser.OpenAPIParser
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlinx.serialization.Serializable

/**
 * A response and a request body are described whatever their type is (B2, B3): a collection, a
 * primitive, an enum and a value class are as ordinary at the top level as an object is.
 */
class TopLevelTypeTest {
    @Test
    fun `a collection response is described as an array`() = testApplication {
        install(OpenAPI)
        routing {
            get<List<User>>("/users") { emptyList() }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        val schema = OpenAPIParser().readContents(response, null, null).openAPI
            .paths.getValue("/users").get.responses.getValue("200").content.getValue("application/json").schema
        assertEquals("array", schema.type)
        assertEquals("#/components/schemas/User", schema.items.`$ref`)
    }

    @Test
    fun `a primitive response is described as a primitive`() = testApplication {
        install(OpenAPI)
        routing {
            get<String>("/ping") { "pong" }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        assertContains(response, "\"schema\":{\"nullable\":false,\"type\":\"string\"}")
    }

    @Test
    fun `an enum response keeps its values`() = testApplication {
        install(OpenAPI)
        routing {
            get<Color>("/color") { Color.RED }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        val schema = OpenAPIParser().readContents(response, null, null).openAPI
            .paths.getValue("/color").get.responses.getValue("200").content.getValue("application/json").schema
        assertEquals("string", schema.type)
        assertEquals(listOf("RED", "GREEN"), schema.enum)
    }

    @Test
    fun `a value class response is described by what it wraps`() = testApplication {
        install(OpenAPI)
        routing {
            get<Money>("/price") { Money(1) }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        assertContains(response, "\"schema\":{\"nullable\":false,\"type\":\"integer\",\"format\":\"int64\"}")
    }

    @Test
    fun `a collection request body is described as an array`() = testApplication {
        install(OpenAPI)
        routing {
            post<User, List<User>>("/users") { User("created") }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        val body = OpenAPIParser().readContents(response, null, null).openAPI
            .paths.getValue("/users").post.requestBody.content.getValue("application/json").schema
        assertEquals("array", body.type)
        assertEquals("#/components/schemas/User", body.items.`$ref`)
    }

    @Test
    fun `a value class is described by what it wraps in a property as well`() {
        // The top level is described the same way as everything else now, and a value class is a
        // value wherever it stands: `{"price": 1}` is what the serializer emits, not an object.
        val type = typeOf<Order>().openApiKType.objectType()

        assertIs<Type.Integer>(type.properties.single().type)
    }

    @Test
    fun `a type that is no object is rejected where an object is required`() {
        // The parameters of an operation are taken apart property by property, so they do have to
        // be an object — the failure is a clear one instead of a cast error deep inside.
        listOf(typeOf<List<User>>(), typeOf<String>(), typeOf<Color>(), typeOf<Money>()).forEach { type ->
            assertFailsWith<InvalidTypeForOpenApiType> { type.openApiKType.objectType() }
        }
    }

    @Serializable
    data class User(val name: String)

    @Serializable
    data class Order(val price: Money)

    @Serializable
    enum class Color { RED, GREEN }

    @JvmInline
    @Serializable
    value class Money(val amount: Long)
}
