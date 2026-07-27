package com.turbomates.openapi.ktor

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.swagger.parser.OpenAPIParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A `sealed` type is one of its subclasses, and the spec says so (C5). It used to be described by
 * the properties of the parent, which a sealed class rarely has — `{"type":"object"}` and nothing
 * more.
 */
class SealedTypeTest {
    @Test
    fun `a sealed type is one of its subclasses`() = testApplication {
        install(OpenAPI)
        routing {
            get<Shape>("/shape") { Shape.Circle(1.0) }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        val document = response.document()
        assertEquals(reference("Shape"), document.responseSchema("/shape"))
        assertEquals(
            listOf(reference("Circle"), reference("Square")),
            document.schema("Shape").oneOf().sortedBy { it.toString() }
        )
        assertEquals("radius", document.properties("Circle").keys.single())
    }

    @Test
    fun `the discriminator maps the values the serializer writes`() = testApplication {
        install(OpenAPI)
        routing {
            get<Shape>("/shape") { Shape.Circle(1.0) }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        // `type` is the property kotlinx.serialization writes the serial name into, so the mapping
        // is keyed by serial names — `@SerialName` where there is one, the qualified name otherwise.
        val discriminator = response.document().schema("Shape").getValue("discriminator").jsonObject
        assertEquals("type", discriminator.getValue("propertyName").jsonPrimitive.content)
        assertEquals(
            mapOf(
                "circle" to "#/components/schemas/Circle",
                "com.turbomates.openapi.ktor.SealedTypeTest.Shape.Square" to "#/components/schemas/Square"
            ),
            discriminator.getValue("mapping").jsonObject.mapValues { it.value.jsonPrimitive.content }
        )
    }

    @Test
    fun `a hierarchy with no serializer is described without a discriminator`() = testApplication {
        install(OpenAPI)
        routing {
            get<Reflected>("/reflected") { Reflected.Left("left") }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        // Nothing says how these are told apart in JSON, so nothing is claimed about it.
        val schema = response.document().schema("Reflected")
        assertEquals(2, schema.oneOf().size)
        assertNull(schema["discriminator"])
    }

    @Test
    fun `a sealed type that refers to itself is described through references`() = testApplication {
        install(OpenAPI)
        routing {
            get<Expression>("/expression") { Expression.Value(1) }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        val document = response.document()
        assertEquals(setOf("Expression", "Value", "Sum"), document.schemas().keys)
        assertEquals(reference("Expression"), document.property("Sum", "left"))
    }

    private fun JsonObject.oneOf(): List<JsonObject> = getValue("oneOf").jsonArray.map { it.jsonObject }

    @Serializable
    sealed class Shape {
        @Serializable
        @SerialName("circle")
        data class Circle(val radius: Double) : Shape()

        @Serializable
        data class Square(val side: Double) : Shape()
    }

    sealed class Reflected {
        data class Left(val value: String) : Reflected()

        data class Right(val value: Int) : Reflected()
    }

    @Serializable
    sealed class Expression {
        @Serializable
        data class Value(val number: Int) : Expression()

        @Serializable
        data class Sum(val left: Expression, val right: Expression) : Expression()
    }
}
