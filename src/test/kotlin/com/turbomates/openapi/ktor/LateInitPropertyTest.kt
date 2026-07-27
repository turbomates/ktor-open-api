package com.turbomates.openapi.ktor

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.swagger.parser.OpenAPIParser
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Properties a request type carries but a request does not — a locale, a principal, anything the
 * handler puts there after the body was read.
 */
class LateInitPropertyTest {
    @Test
    fun `a property the serializer skips is no part of the request`() = testApplication {
        install(OpenAPI)
        routing {
            post<TestResponse, TransientCommand>("/commands") { TestResponse(true) }
        }

        val parsed = document(client.get("/openapi.json").bodyAsText())
        val schema = parsed.openAPI.components.schemas.getValue("TransientCommand")

        assertEquals(emptyList(), parsed.messages)
        // `@Transient` is what says "the handler fills this in": kotlinx neither reads nor writes
        // it, and neither does the schema.
        assertEquals(setOf("email"), schema.properties.keys)
        assertEquals(listOf("email"), schema.required)
    }

    @Test
    fun `a lateinit property of a type that is not serializable is left out`() = testApplication {
        install(OpenAPI)
        routing {
            post<TestResponse, PlainCommand>("/commands") { TestResponse(true) }
        }

        val parsed = document(client.get("/openapi.json").bodyAsText())
        val schema = parsed.openAPI.components.schemas.getValue("PlainCommand")

        assertEquals(emptyList(), parsed.messages)
        // Nothing describes this type but reflection, and a property with no value until something
        // sets one does not come from the client.
        assertEquals(setOf("email"), schema.properties.keys)
    }

    @Test
    fun `a lateinit property the serializer does read is described`() = testApplication {
        install(OpenAPI)
        routing {
            post<TestResponse, SerializedLateInitCommand>("/commands") { TestResponse(true) }
        }

        val parsed = document(client.get("/openapi.json").bodyAsText())
        val schema = parsed.openAPI.components.schemas.getValue("SerializedLateInitCommand")

        assertEquals(emptyList(), parsed.messages)
        // `lateinit` without `@Transient` is an element of the serializer like any other, and
        // kotlinx answers a body without it with a `MissingFieldException`. Leaving it out of the
        // schema would document a request the server refuses.
        assertEquals(setOf("email", "userId"), schema.properties.keys)
        assertEquals(listOf("email", "userId"), schema.required)
    }

    private fun document(json: String) = OpenAPIParser().readContents(json, null, null)

    @Serializable
    data class TransientCommand(val email: String) {
        @Transient
        lateinit var locale: Locale
    }

    data class PlainCommand(val email: String) {
        lateinit var locale: Locale
    }

    @Serializable
    data class SerializedLateInitCommand(val email: String) {
        lateinit var userId: String
    }

    @Serializable
    data class TestResponse(val isDone: Boolean)
}
