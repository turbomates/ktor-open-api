package com.turbomates.openapi.ktor

import java.math.BigDecimal
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import io.swagger.parser.OpenAPIParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * A schema says what kind of value it is and in what shape it is written (C2), and a type that
 * carries a value rather than a structure is not taken apart into its fields (C4).
 */
class TypeFormatTest {
    @Test
    fun `whole numbers are integers and say how wide they are`() = testApplication {
        install(OpenAPI)
        routing {
            get<Numbers>("/numbers") { error("not called") }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        val properties = response.document().properties("Numbers")
        assertEquals("integer" to "int32", properties.getValue("int").typeAndFormat())
        assertEquals("integer" to "int64", properties.getValue("long").typeAndFormat())
        assertEquals("integer" to "int32", properties.getValue("short").typeAndFormat())
        assertEquals("number" to "float", properties.getValue("float").typeAndFormat())
        assertEquals("number" to "double", properties.getValue("double").typeAndFormat())
        // Nothing is claimed about a decimal, which has no format of its own.
        assertEquals("number" to null, properties.getValue("decimal").typeAndFormat())
    }

    @Test
    fun `a value is described by its format instead of its fields`() = testApplication {
        install(OpenAPI)
        routing {
            get<Values>("/values") { error("not called") }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        // Each of these used to be reflected into its own internals — a `LocalDate` was an object
        // of `year`, `month` and `day`, a `ByteArray` an object of `size`.
        val properties = response.document().properties("Values")
        assertEquals("string" to "date", properties.getValue("date").typeAndFormat())
        assertEquals("string" to "date-time", properties.getValue("dateTime").typeAndFormat())
        assertEquals("string" to "date-time", properties.getValue("instant").typeAndFormat())
        assertEquals("string" to "duration", properties.getValue("duration").typeAndFormat())
        assertEquals("string" to "binary", properties.getValue("payload").typeAndFormat())
        assertEquals("string" to "uuid", properties.getValue("id").typeAndFormat())
        assertEquals("string" to "uri", properties.getValue("link").typeAndFormat())
    }

    @Test
    fun `an array is an array whether or not it is a collection`() = testApplication {
        install(OpenAPI)
        routing {
            get<Arrays>("/arrays") { error("not called") }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        // `Array` and `IntArray` are no `Collection` in Kotlin, so both used to be described by
        // whatever property the class happens to have — `size`.
        val properties = response.document().properties("Arrays")
        assertEquals("array" to null, properties.getValue("names").typeAndFormat())
        assertEquals("string", properties.getValue("names").items().type())
        assertEquals("array" to null, properties.getValue("counts").typeAndFormat())
        assertEquals("integer" to "int32", properties.getValue("counts").items().typeAndFormat())
    }

    @Test
    fun `a map describes the values it holds, not a property named after its key`() = testApplication {
        install(OpenAPI)
        routing {
            get<WithMap>("/map") { error("not called") }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        // The spec used to claim `meta` was an object with a field called `String`.
        val meta = response.document().property("WithMap", "meta")
        assertEquals("object", meta.type())
        assertEquals("integer" to "int32", meta.additionalProperties().typeAndFormat())
    }

    private fun JsonObject.typeAndFormat(): Pair<String?, String?> = type() to format()

    private fun JsonObject.items(): JsonObject = getValue("items").jsonObject

    private fun JsonObject.additionalProperties(): JsonObject = getValue("additionalProperties").jsonObject

    data class Numbers(
        val int: Int,
        val long: Long,
        val short: Short,
        val float: Float,
        val double: Double,
        val decimal: BigDecimal
    )

    data class Values(
        val date: LocalDate,
        val dateTime: LocalDateTime,
        val instant: Instant,
        val duration: java.time.Duration,
        val payload: ByteArray,
        val id: UUID,
        val link: URI
    )

    data class Arrays(val names: Array<String>, val counts: IntArray)

    data class WithMap(val meta: Map<String, Int>)
}
