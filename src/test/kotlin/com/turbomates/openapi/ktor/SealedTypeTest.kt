@file:OptIn(ExperimentalSerializationApi::class)

package com.turbomates.openapi.ktor

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import io.swagger.parser.OpenAPIParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
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
            listOf("#/components/schemas/Circle", "#/components/schemas/Square"),
            document.schema("Shape").oneOf().mapNotNull { it.referenced() }.sorted()
        )
        assertEquals(setOf("radius"), document.properties("Circle").keys)
    }

    @Test
    fun `an option carries the value the discriminator holds for the subclass`() = testApplication {
        install(OpenAPI)
        routing {
            get<Shape>("/shape") { Shape.Circle(1.0) }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        // Through the parent the serializer writes `{"type":"circle","radius":1.0}` and refuses a
        // body without the key, so the option is the subclass and that key, required like any
        // other. The value is the only one this subclass ever carries.
        val carried = response.document().option("Shape", "Circle").carried()
        assertEquals(listOf("type"), carried.required())
        assertEquals("string", carried.property("type").type())
        assertEquals(listOf("circle"), carried.property("type").enum())
    }

    @Test
    fun `a subclass is described with the keys the serializer writes`() = testApplication {
        install(OpenAPI)
        routing {
            get<Shape>("/shape") { Shape.Circle(1.0) }
        }

        val response = client.get("/openapi.json").bodyAsText()

        val document = response.document()
        // Handed the subclass, the serializer writes `{"radius":1.0}`, and that is the schema of
        // the subclass.
        val direct = Json.encodeToJsonElement(Shape.Circle(1.0)).jsonObject
        assertEquals(direct.keys, document.properties("Circle").keys)
        // Through the parent it writes the discriminator as well, and the option adds exactly the
        // key the subclass itself is written without.
        val polymorphic = Json.encodeToJsonElement<Shape>(Shape.Circle(1.0)).jsonObject
        assertEquals(
            polymorphic.keys - direct.keys,
            document.option("Shape", "Circle").carried().getValue("properties").jsonObject.keys
        )
    }

    @Test
    fun `a subclass an endpoint names is described as the serializer writes it alone`() = testApplication {
        install(OpenAPI)
        routing {
            get<Shape>("/shape") { Shape.Circle(1.0) }
            post<Shape.Circle, Shape.Circle>("/circle") { it }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        // The discriminator used to be described on the subclass, so a body built from this schema
        // carried a `type` — and the very server that published the schema refused it, since a
        // reader handed `Circle` knows no such key. There is one schema for the subclass whichever
        // way it is used, and the key belongs to the option of the parent.
        val document = response.document()
        assertEquals(setOf("radius"), document.properties("Circle").keys)
        assertEquals(reference("Circle"), document.requestSchema("/circle"))
        assertEquals(listOf("type"), document.option("Shape", "Circle").carried().required())
    }

    @Test
    fun `the discriminator is the property JsonClassDiscriminator names`() = testApplication {
        install(OpenAPI)
        routing {
            get<Event>("/event") { Event.Created("1") }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        val document = response.document()
        assertEquals("kind", document.schema("Event").discriminator().propertyName())
        assertEquals(listOf("created"), document.option("Event", "Created").carried().property("kind").enum())
    }

    @Test
    fun `a hierarchy deeper than one level is one of its leaves`() = testApplication {
        install(OpenAPI)
        routing {
            get<Command>("/command") { Command.Write.Insert("row") }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        // `Write` is abstract like `Command` itself: no body is ever written under its serial name,
        // so it is not one of the options.
        val document = response.document()
        assertEquals(
            listOf("#/components/schemas/Delete", "#/components/schemas/Insert", "#/components/schemas/Read"),
            document.schema("Command").oneOf().mapNotNull { it.referenced() }.sorted()
        )
        assertEquals(setOf("insert", "delete", "read"), document.schema("Command").discriminator().mapping().keys)
    }

    @Test
    fun `a generic hierarchy is told apart by the names its subclasses are given`() = testApplication {
        install(OpenAPI)
        routing {
            get<Outcome<String>>("/outcome") { Outcome.Success("ok") }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        // A generic subclass has no serializer until its argument is known, and it is polymorphic
        // all the same, so the name comes from `@SerialName` rather than from a descriptor.
        val document = response.document()
        assertEquals(setOf("success", "failure"), document.schema("OutcomeString").discriminator().mapping().keys)
        assertEquals(
            listOf("success"),
            document.option("OutcomeString", "Success").carried().property("type").enum()
        )
    }

    @Test
    fun `a sealed interface is one of its implementations`() = testApplication {
        install(OpenAPI)
        routing {
            get<Payment>("/payment") { Payment.Card("4242") }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        val document = response.document()
        assertEquals(
            listOf("#/components/schemas/Card", "#/components/schemas/Cash"),
            document.schema("Payment").oneOf().mapNotNull { it.referenced() }.sorted()
        )
        assertEquals(listOf("card"), document.option("Payment", "Card").carried().property("type").enum())
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
        val discriminator = response.document().schema("Shape").discriminator()
        assertEquals("type", discriminator.propertyName())
        assertEquals(
            mapOf(
                "circle" to "#/components/schemas/Circle",
                "com.turbomates.openapi.ktor.SealedTypeTest.Shape.Square" to "#/components/schemas/Square"
            ),
            discriminator.mapping()
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
        // Nothing says how these are told apart in JSON, so nothing is claimed about it — neither on
        // the hierarchy nor on the subclasses, which carry no key beyond their own properties.
        val document = response.document()
        val schema = document.schema("Reflected")
        assertEquals(2, schema.oneOf().size)
        assertNull(schema["discriminator"])
        assertEquals(setOf("value"), document.properties("Left").keys)
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

    /** The option of the `oneOf` of [schema] that stands for the subclass described as [component]. */
    private fun JsonObject.option(schema: String, component: String): JsonObject {
        return schema(schema).oneOf().single { it.referenced() == "#/components/schemas/$component" }
    }

    /** The schema an option is a reference to, whether it stands alone or is one of an `allOf`. */
    private fun JsonObject.referenced(): String? {
        return get("\$ref")?.jsonPrimitive?.content
            ?: get("allOf")?.jsonArray?.firstNotNullOfOrNull { it.jsonObject["\$ref"]?.jsonPrimitive?.content }
    }

    /** What an option adds to the subclass it stands for: the discriminator the parent is read by. */
    private fun JsonObject.carried(): JsonObject = getValue("allOf").jsonArray.last().jsonObject

    private fun JsonObject.property(name: String): JsonObject {
        return getValue("properties").jsonObject.getValue(name).jsonObject
    }

    private fun JsonObject.required(): List<String> = getValue("required").jsonArray.map { it.jsonPrimitive.content }

    private fun JsonObject.enum(): List<String> = getValue("enum").jsonArray.map { it.jsonPrimitive.content }

    private fun JsonObject.discriminator(): JsonObject = getValue("discriminator").jsonObject

    private fun JsonObject.propertyName(): String = getValue("propertyName").jsonPrimitive.content

    private fun JsonObject.mapping(): Map<String, String> =
        getValue("mapping").jsonObject.mapValues { it.value.jsonPrimitive.content }

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

    @Serializable
    @JsonClassDiscriminator("kind")
    sealed class Event {
        @Serializable
        @SerialName("created")
        data class Created(val id: String) : Event()

        @Serializable
        @SerialName("removed")
        data class Removed(val id: String) : Event()
    }

    @Serializable
    sealed class Command {
        @Serializable
        @SerialName("read")
        data class Read(val query: String) : Command()

        @Serializable
        sealed class Write : Command() {
            @Serializable
            @SerialName("insert")
            data class Insert(val row: String) : Write()

            @Serializable
            @SerialName("delete")
            data class Delete(val id: String) : Write()
        }
    }

    @Serializable
    sealed class Outcome<T> {
        @Serializable
        @SerialName("success")
        data class Success<T>(val value: T) : Outcome<T>()

        @Serializable
        @SerialName("failure")
        data class Failure<T>(val reason: String) : Outcome<T>()
    }

    @Serializable
    sealed interface Payment {
        @Serializable
        @SerialName("card")
        data class Card(val number: String) : Payment

        @Serializable
        @SerialName("cash")
        data class Cash(val amount: Int) : Payment
    }
}
