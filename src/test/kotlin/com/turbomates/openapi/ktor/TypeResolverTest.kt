package com.turbomates.openapi.ktor

import com.turbomates.openapi.MediaType
import com.turbomates.openapi.Type
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import io.swagger.parser.OpenAPIParser
import io.swagger.v3.oas.models.media.Schema
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.Serializable

class TypeResolverTest {
    @Test
    fun `a resolver describes its type wherever the type turns up`() = testApplication {
        install(OpenAPI) {
            typeResolver { kType -> money(kType) }
        }
        routing {
            get<Invoice>("/invoices") { invoice() }
        }

        val parsed = document(client.get("/openapi.json").bodyAsText())
        val invoice = parsed.openAPI.components.schemas.getValue("Invoice").properties

        assertEquals(emptyList(), parsed.messages)
        // A property, an element of a collection and the value of a map are all the same type, and
        // the API describes it the one way.
        assertEquals("money", invoice.getValue("total").format)
        assertEquals("money", invoice.getValue("paid").items.format)
        assertEquals("money", (invoice.getValue("byMonth").additionalProperties as Schema<*>).format)
        assertEquals("string", invoice.getValue("total").type)
    }

    @Test
    fun `a resolver describes a type reflection would have taken apart`() = testApplication {
        install(OpenAPI) {
            typeResolver { kType -> if (kType.classifier == Period::class) Type.String(format = "period") else null }
        }
        routing {
            get<Report>("/reports") { Report(Period(1, 12), "done") }
        }

        val parsed = document(client.get("/openapi.json").bodyAsText())
        val report = parsed.openAPI.components.schemas.getValue("Report").properties

        assertEquals(emptyList(), parsed.messages)
        // Without a resolver this is an object of `from` and `to`; the API says it is written as a
        // string, and no schema of its own is left behind for it.
        assertEquals("string", report.getValue("period").type)
        assertEquals("period", report.getValue("period").format)
        assertEquals(setOf("Report"), parsed.openAPI.components.schemas.keys)
    }

    @Test
    fun `a resolver is handed the type with its nullability`() = testApplication {
        install(OpenAPI) {
            typeResolver { kType -> money(kType) }
        }
        routing {
            get<Invoice>("/invoices") { invoice() }
        }

        val parsed = document(client.get("/openapi.json").bodyAsText())
        val invoice = parsed.openAPI.components.schemas.getValue("Invoice").properties

        assertEquals(emptyList(), parsed.messages)
        // The same type, described as nullable where the property is and as not where it is not.
        assertEquals(true, invoice.getValue("discount").nullable)
        assertEquals(false, invoice.getValue("total").nullable)
    }

    @Test
    fun `the first resolver to describe a type wins`() = testApplication {
        install(OpenAPI) {
            typeResolver { kType -> if (kType.classifier == Period::class) Type.String(format = "first") else null }
            typeResolver { kType -> if (kType.classifier == Period::class) Type.String(format = "second") else null }
        }
        routing {
            get<Report>("/reports") { Report(Period(1, 12), "done") }
        }

        val parsed = document(client.get("/openapi.json").bodyAsText())
        val report = parsed.openAPI.components.schemas.getValue("Report").properties

        assertEquals(emptyList(), parsed.messages)
        assertEquals("first", report.getValue("period").format)
    }

    @Test
    fun `a type named on its own is described before any rule`() = testApplication {
        install(OpenAPI) {
            customTypeDescription = mapOf(typeOf<Period>() to Type.String(format = "named"))
            typeResolver { kType -> if (kType.classifier == Period::class) Type.String(format = "resolved") else null }
        }
        routing {
            get<Report>("/reports") { Report(Period(1, 12), "done") }
        }

        val parsed = document(client.get("/openapi.json").bodyAsText())
        val report = parsed.openAPI.components.schemas.getValue("Report").properties

        assertEquals(emptyList(), parsed.messages)
        assertEquals("named", report.getValue("period").format)
    }

    @Test
    fun `a type named on its own is described whichever nullability it is used with`() = testApplication {
        install(OpenAPI) {
            customTypeDescription = mapOf(typeOf<Money>() to Type.String(format = "money"))
        }
        routing {
            get<Invoice>("/invoices") { invoice() }
        }

        val parsed = document(client.get("/openapi.json").bodyAsText())
        val invoice = parsed.openAPI.components.schemas.getValue("Invoice").properties

        assertEquals(emptyList(), parsed.messages)
        assertEquals("money", invoice.getValue("total").format)
        assertEquals("money", invoice.getValue("discount").format)
    }

    @Test
    fun `the types of the operation block are described the same way`() = testApplication {
        install(OpenAPI) {
            typeResolver { kType -> money(kType) }
        }
        routing {
            get<Report>("/reports", {
                header<Money>("X-Budget")
                queryParameter<Money>("under")
                responseHeaders { header<Money>("X-Spent") }
                responseOf<Money>(402, "not paid for")
            }) { Report(Period(1, 12), "done") }
        }

        val parsed = document(client.get("/openapi.json").bodyAsText())
        val operation = parsed.openAPI.paths.getValue("/reports").get

        assertEquals(emptyList(), parsed.messages)
        assertEquals(
            listOf("money", "money"),
            operation.parameters.map { it.schema.format }
        )
        assertEquals("money", operation.responses.getValue("200").headers.getValue("X-Spent").schema.format)
        assertEquals(
            "money",
            operation.responses.getValue("402").content.getValue(MediaType.JSON).schema.format
        )
    }

    @Test
    fun `a resolver that describes nothing leaves the type to reflection`() = testApplication {
        install(OpenAPI) {
            typeResolver { null }
        }
        routing {
            get<Report>("/reports") { Report(Period(1, 12), "done") }
        }

        val parsed = document(client.get("/openapi.json").bodyAsText())
        val report = parsed.openAPI.components.schemas.getValue("Report").properties

        assertEquals(emptyList(), parsed.messages)
        assertEquals("#/components/schemas/Period", report.getValue("period").`$ref`)
        assertEquals(setOf("from", "to"), parsed.openAPI.components.schemas.getValue("Period").properties.keys)
    }

    private fun money(kType: kotlin.reflect.KType): Type? {
        if (kType.classifier != Money::class) {
            return null
        }
        return Type.String(format = "money", nullable = kType.isMarkedNullable)
    }

    private fun invoice() = Invoice(Money("10.00"), null, listOf(Money("4.00")), mapOf("07" to Money("6.00")))

    private fun document(json: String) = OpenAPIParser().readContents(json, null, null)

    @Serializable
    @JvmInline
    value class Money(val amount: String)

    @Serializable
    data class Period(val from: Int, val to: Int)

    @Serializable
    data class Invoice(
        val total: Money,
        val discount: Money?,
        val paid: List<Money>,
        val byMonth: Map<String, Money>
    )

    @Serializable
    data class Report(val period: Period, val state: String)
}
