package com.turbomates.openapi.ktor

import com.turbomates.openapi.Type
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.swagger.parser.OpenAPIParser
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.Serializable

class ResponseHeaderTest {
    @Test
    fun `a global header is described on every response of every operation`() = testApplication {
        install(OpenAPI) {
            globalResponseHeaders {
                header("X-Request-Id", Type.String(), "Request correlation id", required = true)
                header<Int>("X-Rate-Limit-Remaining", "Calls left in the current window")
            }
        }
        routing {
            get<TestResponse>("/users") { TestResponse(true) }
            post<TestResponse, TestRequest>("/users") { TestResponse(true) }
        }

        val parsed = document(client.get("/openapi.json").bodyAsText())
        val paths = parsed.openAPI.paths.getValue("/users")

        assertEquals(emptyList(), parsed.messages)
        listOf(paths.get, paths.post).forEach { operation ->
            val headers = operation.responses.getValue("200").headers
            assertEquals(setOf("X-Request-Id", "X-Rate-Limit-Remaining"), headers.keys)
            assertEquals("Request correlation id", headers.getValue("X-Request-Id").description)
            assertEquals(true, headers.getValue("X-Request-Id").required)
            assertEquals("string", headers.getValue("X-Request-Id").schema.type)
            assertEquals("integer", headers.getValue("X-Rate-Limit-Remaining").schema.type)
            // A header nothing says is required is optional, and OpenAPI already says as much.
            assertNull(headers.getValue("X-Rate-Limit-Remaining").required)
        }
    }

    @Test
    fun `a global header reaches every response the operation describes`() = testApplication {
        install(OpenAPI) {
            globalResponseHeaders { header("X-Request-Id", Type.String()) }
        }
        routing {
            get<TestResponse>("/users/{id}", {
                responseOf<TestError>(HttpStatusCode.NotFound, "no user with that id")
                response(HttpStatusCode.NoContent)
                default("unexpected failure")
            }) { TestResponse(true) }
        }

        val parsed = document(client.get("/openapi.json").bodyAsText())
        val responses = parsed.openAPI.paths.getValue("/users/{id}").get.responses

        assertEquals(emptyList(), parsed.messages)
        assertEquals(setOf("200", "404", "204", "default"), responses.keys)
        responses.values.forEach { assertEquals(setOf("X-Request-Id"), it.headers.keys) }
    }

    @Test
    fun `an operation describes headers of its own beside the global ones`() = testApplication {
        install(OpenAPI) {
            globalResponseHeaders { header("X-Request-Id", Type.String(), "Request correlation id") }
        }
        routing {
            get<TestResponse>("/users", {
                responseHeaders { header<Int>("X-Total-Count", "How many users there are in all") }
            }) { TestResponse(true) }
            get<TestResponse>("/health") { TestResponse(true) }
        }

        val parsed = document(client.get("/openapi.json").bodyAsText())
        val users = parsed.openAPI.paths.getValue("/users").get.responses.getValue("200")
        val health = parsed.openAPI.paths.getValue("/health").get.responses.getValue("200")

        assertEquals(emptyList(), parsed.messages)
        assertEquals(setOf("X-Request-Id", "X-Total-Count"), users.headers.keys)
        assertEquals("How many users there are in all", users.headers.getValue("X-Total-Count").description)
        // A header of one operation is described on that operation alone.
        assertEquals(setOf("X-Request-Id"), health.headers.keys)
    }

    @Test
    fun `a header stated closer to the response wins`() = testApplication {
        install(OpenAPI) {
            globalResponseHeaders { header("X-Request-Id", Type.String(), "Request correlation id") }
        }
        routing {
            get<TestResponse>("/users", {
                responseHeaders { header("x-request-id", Type.String(), "The id this call was traced by") }
            }) { TestResponse(true) }
        }

        val parsed = document(client.get("/openapi.json").bodyAsText())
        val headers = parsed.openAPI.paths.getValue("/users").get.responses.getValue("200").headers

        assertEquals(emptyList(), parsed.messages)
        // HTTP header names are case-insensitive, so this is the same header described again rather
        // than a second one.
        assertEquals(setOf("x-request-id"), headers.keys)
        assertEquals("The id this call was traced by", headers.getValue("x-request-id").description)
    }

    @Test
    fun `a header is matched by a name folded the same way everywhere`() {
        // `I` lowercases to a dotless `ı` in Turkish, which would leave `X-Request-Id` and
        // `x-request-id` as two different headers for anyone running with that locale. The names
        // are folded by the invariant locale, so the document comes out the same wherever it is
        // built.
        val locale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"))
            testApplication {
                install(OpenAPI) {
                    globalResponseHeaders { header("X-Request-Id", Type.String(), "Request correlation id") }
                }
                routing {
                    get<TestResponse>("/users", {
                        responseHeaders { header("x-request-id", Type.String(), "The id this call was traced by") }
                    }) { TestResponse(true) }
                }

                val parsed = document(client.get("/openapi.json").bodyAsText())
                val headers = parsed.openAPI.paths.getValue("/users").get.responses.getValue("200").headers

                assertEquals(emptyList(), parsed.messages)
                assertEquals(setOf("x-request-id"), headers.keys)
            }
        } finally {
            Locale.setDefault(locale)
        }
    }

    @Test
    fun `a response carries the headers stated for its status code alone`() = testApplication {
        install(OpenAPI) {
            globalResponseHeaders { header("X-Request-Id", Type.String()) }
        }
        routing {
            post<TestResponse, TestRequest>("/orders", {
                response(HttpStatusCode.Created, "the order") {
                    header("Location", Type.String(), "Where the order can be read from", required = true)
                }
                responseOf<TestError>(HttpStatusCode.TooManyRequests) {
                    header<Int>("Retry-After", "Seconds to wait before trying again")
                }
            }) { TestResponse(true) }
        }

        val parsed = document(client.get("/openapi.json").bodyAsText())
        val responses = parsed.openAPI.paths.getValue("/orders").post.responses

        assertEquals(emptyList(), parsed.messages)
        assertEquals(setOf("X-Request-Id", "Location"), responses.getValue("201").headers.keys)
        assertEquals(true, responses.getValue("201").headers.getValue("Location").required)
        assertEquals(setOf("X-Request-Id", "Retry-After"), responses.getValue("429").headers.keys)
        assertEquals("the order", responses.getValue("201").description)
        assertEquals(setOf("X-Request-Id"), responses.getValue("200").headers.keys)
    }

    @Test
    fun `a document that states no headers describes none`() = testApplication {
        install(OpenAPI)
        routing {
            get<TestResponse>("/users") { TestResponse(true) }
        }

        val parsed = document(client.get("/openapi.json").bodyAsText())

        assertEquals(emptyList(), parsed.messages)
        assertNull(parsed.openAPI.paths.getValue("/users").get.responses.getValue("200").headers)
    }

    private fun document(json: String) = OpenAPIParser().readContents(json, null, null)

    @Serializable
    data class TestResponse(val isDone: Boolean)

    @Serializable
    data class TestRequest(val body: String)

    @Serializable
    data class TestError(val message: String)
}
