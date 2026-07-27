package com.turbomates.openapi.ktor

import com.turbomates.openapi.MediaType
import com.turbomates.openapi.SecurityScheme
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.swagger.parser.OpenAPIParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable

class OperationMetadataTest {
    @Test
    fun `an operation is described by what its types cannot say`() = testApplication {
        install(OpenAPI)
        routing {
            get<TestResponse>("/users", {
                summary = "List users"
                description = "Every user of the account, newest first."
                operationId = "listUsers"
                deprecated = true
                tags("Users", "Public API")
            }) { TestResponse(true) }
        }

        val parsed = document(client.get("/openapi.json").bodyAsText())
        val operation = parsed.openAPI.paths.getValue("/users").get

        assertEquals(emptyList(), parsed.messages)
        assertEquals("List users", operation.summary)
        assertEquals("Every user of the account, newest first.", operation.description)
        assertEquals("listUsers", operation.operationId)
        assertEquals(true, operation.deprecated)
        assertEquals(listOf("Users", "Public API"), operation.tags)
    }

    @Test
    fun `a response says what the status code means`() = testApplication {
        install(OpenAPI)
        routing {
            get<TestResponse>("/users") { TestResponse(true) }
        }

        val parsed = document(client.get("/openapi.json").bodyAsText())

        assertEquals(emptyList(), parsed.messages)
        // Until something better is said, the meaning of the code itself is better than a
        // placeholder repeated on every endpoint of every document.
        assertEquals("OK", parsed.openAPI.paths.getValue("/users").get.responses.getValue("200").description)
    }

    @Test
    fun `a described response keeps its description and its body`() = testApplication {
        install(OpenAPI)
        routing {
            get<TestResponse>("/users/{id}", {
                response(HttpStatusCode.OK, "the user")
            }) { TestResponse(true) }
        }

        val parsed = document(client.get("/openapi.json").bodyAsText())
        val ok = parsed.openAPI.paths.getValue("/users/{id}").get.responses.getValue("200")

        assertEquals(emptyList(), parsed.messages)
        assertEquals("the user", ok.description)
        assertTrue(ok.content.containsKey(MediaType.JSON))
    }

    @Test
    fun `one operation answers with a body of its own per status code`() = testApplication {
        install(OpenAPI)
        routing {
            get<TestResponse>("/users/{id}", {
                response(HttpStatusCode.OK, "the user")
                responseOf<TestError>(HttpStatusCode.NotFound, "no user with that id")
                response(HttpStatusCode.NoContent)
                defaultOf<TestError>("unexpected failure")
            }) { TestResponse(true) }
        }

        val parsed = document(client.get("/openapi.json").bodyAsText())
        val responses = parsed.openAPI.paths.getValue("/users/{id}").get.responses

        assertEquals(emptyList(), parsed.messages)
        assertEquals(
            "#/components/schemas/TestResponse",
            responses.getValue("200").content.getValue(MediaType.JSON).schema.`$ref`
        )
        assertEquals(
            "#/components/schemas/TestError",
            responses.getValue("404").content.getValue(MediaType.JSON).schema.`$ref`
        )
        assertEquals("no user with that id", responses.getValue("404").description)
        // A code the route does not answer with carries no body, and `204` may not carry one at all.
        assertNull(responses.getValue("204").content)
        assertEquals("No Content", responses.getValue("204").description)
        assertEquals("unexpected failure", responses.getValue("default").description)
    }

    @Test
    fun `headers and cookies are described as parameters`() = testApplication {
        install(OpenAPI)
        routing {
            get<TestResponse>("/users", {
                header<String>("X-Request-Id", required = true, description = "Request correlation id")
                cookie<String>("session")
                queryParameter<Int>("limit")
            }) { TestResponse(true) }
        }

        val parsed = document(client.get("/openapi.json").bodyAsText())
        val parameters = parsed.openAPI.paths.getValue("/users").get.parameters

        assertEquals(emptyList(), parsed.messages)
        assertEquals(
            listOf("X-Request-Id" to "header", "session" to "cookie", "limit" to "query"),
            parameters.map { it.name to it.`in` }
        )
        assertEquals(true, parameters.first().required)
        assertEquals("Request correlation id", parameters.first().description)
        assertEquals("integer", parameters.last().schema.type)
    }

    @Test
    fun `media types of the body and of the responses are the ones stated`() = testApplication {
        install(OpenAPI)
        routing {
            post<TestResponse, TestRequest>("/uploads", {
                consumes(MediaType.FORM)
                produces(MediaType.JSON, "text/csv")
            }) { TestResponse(true) }
        }

        val parsed = document(client.get("/openapi.json").bodyAsText())
        val operation = parsed.openAPI.paths.getValue("/uploads").post

        assertEquals(emptyList(), parsed.messages)
        assertEquals(setOf(MediaType.FORM), operation.requestBody.content.keys)
        assertEquals(setOf(MediaType.JSON, "text/csv"), operation.responses.getValue("200").content.keys)
    }

    @Test
    fun `an operation requires the scheme it names`() = testApplication {
        install(OpenAPI) {
            configure = { openApi -> openApi.securityScheme("OAuth2", SecurityScheme.bearer()) }
        }
        routing {
            get<TestResponse>("/users", { security("OAuth2", "users:read") }) { TestResponse(true) }
        }

        val parsed = document(client.get("/openapi.json").bodyAsText())
        val security = parsed.openAPI.paths.getValue("/users").get.security

        assertEquals(emptyList(), parsed.messages)
        assertEquals(listOf(mapOf("OAuth2" to listOf("users:read"))), security.map { it.toMap() })
    }

    @Test
    fun `a route registered again keeps what it already describes`() = testApplication {
        install(OpenAPI)
        routing {
            get<TestResponse>("/users", {
                summary = "List users"
                tags("Users")
            }) { TestResponse(true) }
            get<TestResponse>("/users", {
                summary = "Something else"
                operationId = "listUsers"
                tags("Public API")
            }) { TestResponse(true) }
        }

        val parsed = document(client.get("/openapi.json").bodyAsText())
        val operation = parsed.openAPI.paths.getValue("/users").get

        assertEquals(emptyList(), parsed.messages)
        assertEquals("List users", operation.summary)
        // What the first registration left out the second one may still fill in.
        assertEquals("listUsers", operation.operationId)
        assertEquals(listOf("Users", "Public API"), operation.tags)
    }

    private fun document(json: String) = OpenAPIParser().readContents(json, null, null)

    @Serializable
    data class TestResponse(val isDone: Boolean)

    @Serializable
    data class TestRequest(val body: String)

    @Serializable
    data class TestError(val message: String)
}
