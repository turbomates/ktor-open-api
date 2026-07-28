package com.turbomates.openapi.ktor

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import io.swagger.parser.OpenAPIParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * The schema describes what the serializer writes (C6), and says which keys have to be there at
 * all (C1).
 */
class SerializedShapeTest {
    @Test
    fun `a schema describes the properties the serializer writes`() = testApplication {
        install(OpenAPI)
        routing {
            get<Dto>("/dto") { Dto("1", kept = "k") }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        // Serial names, declaration order, no `@Transient` property and no computed getter — the
        // spec used to claim `internal` and `full`, call `user_id` `userId`, and sort it all
        // alphabetically.
        assertEquals(
            listOf("user_id", "kept", "nick", "withDefault"),
            response.document().properties("Dto").keys.toList()
        )
    }

    @Test
    fun `a property is required when the key has to be there`() = testApplication {
        install(OpenAPI)
        routing {
            get<Dto>("/dto") { Dto("1", kept = "k") }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        // `withDefault` may be left out because the serializer has a default for it, `nick` because
        // it is nullable. Everything else has to be sent.
        assertEquals(listOf("user_id", "kept"), response.document().required("Dto"))
    }

    @Test
    fun `a nullable property is optional and stays nullable`() = testApplication {
        install(OpenAPI)
        routing {
            get<WithNullable>("/nullable") { WithNullable("name", null) }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        val document = response.document()
        assertEquals(listOf("name"), document.required("WithNullable"))
        assertEquals(true, document.property("WithNullable", "nick").nullable())
    }

    @Test
    fun `a type with no serializer is still described by reflection`() = testApplication {
        install(OpenAPI)
        routing {
            get<Reflected, PlainParams>("/plain") { Reflected("described") }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        // Nothing but reflection to go by: alphabetical order, Kotlin names, required from
        // nullability alone.
        val document = response.document()
        assertEquals(listOf("name", "nick"), document.properties("Reflected").keys.toList())
        assertEquals(listOf("name"), document.required("Reflected"))
    }

    @Test
    fun `an object with no required property says nothing about required`() = testApplication {
        install(OpenAPI)
        routing {
            get<AllOptional>("/optional") { AllOptional() }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        // An empty `required` is not allowed by the spec, so the key is left out entirely.
        assertEquals(emptyList(), response.document().required("AllOptional"))
    }

    @Serializable
    data class Dto(
        @SerialName("user_id") val userId: String,
        @Transient val internal: String = "internal",
        val kept: String,
        val nick: String? = null,
        val withDefault: Int = 1
    ) {
        val full: String get() = "$userId $kept"
    }

    @Serializable
    data class WithNullable(val name: String, val nick: String?)

    @Serializable
    data class AllOptional(val page: Int = 1)

    data class Reflected(val name: String) {
        val nick: String? = null
    }

    data class PlainParams(val page: Int)
}
