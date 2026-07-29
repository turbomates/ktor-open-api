package com.turbomates.openapi.ktor

import com.turbomates.openapi.openApiKType
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.server.testing.testApplication
import io.swagger.parser.OpenAPIParser
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.serialization.Serializable
import com.turbomates.openapi.OpenAPI as SwaggerOpenAPI

class HttpMethodTest {
    @Test
    fun `head options and trace are documented`() = testApplication {
        install(OpenAPI)
        routing {
            listOf(HttpMethod.Head, HttpMethod.Options, HttpMethod("TRACE")).forEach { method ->
                openApi.addToPath("/users", method, response = typeOf<TestResponse>())
            }
        }

        val response = client.get("/openapi.json").bodyAsText()

        val parsed = OpenAPIParser().readContents(response, null, null)
        assertEquals(emptyList(), parsed.messages)
        val pathItem = parsed.openAPI.paths.getValue("/users")
        assertEquals(
            listOf("head", "options", "trace"),
            listOfNotNull(
                pathItem.head?.let { "head" },
                pathItem.options?.let { "options" },
                pathItem.trace?.let { "trace" }
            )
        )
    }

    @Test
    fun `a method openapi cannot describe is skipped instead of failing`() = testApplication {
        install(OpenAPI)
        routing {
            openApi.addToPath("/users", HttpMethod("LINK"), response = typeOf<TestResponse>())
            openApi.addToPath("/users", HttpMethod.Get, response = typeOf<TestResponse>())
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        assertContains(response, "\"/users\":{\"get\"")
        assertFalse(response.contains("link"))
    }

    @Test
    fun `a lowercase method is still recognized`() = testApplication {
        install(OpenAPI)
        routing {
            openApi.addToPath("/users", HttpMethod("delete"), response = typeOf<TestResponse>())
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        assertContains(response, "\"/users\":{\"delete\"")
    }

    @Test
    fun `head and options carry no request body`() {
        val api = SwaggerOpenAPI("localhost")
        val body = typeOf<TestRequest>().openApiKType.objectType()
        listOf(SwaggerOpenAPI.Method.HEAD, SwaggerOpenAPI.Method.OPTIONS).forEach { method ->
            api.addToPath("/users", method, body = body)
        }

        assertEquals(null, api.root.paths.getValue("/users").head?.requestBody)
        assertEquals(null, api.root.paths.getValue("/users").options?.requestBody)
    }

    @Test
    fun `a method without a body keeps none when its path is registered again`() {
        val api = SwaggerOpenAPI("localhost")
        val body = typeOf<TestRequest>().openApiKType.objectType()
        repeat(2) {
            api.addToPath("/users", SwaggerOpenAPI.Method.GET, body = body)
        }

        assertEquals(null, api.root.paths.getValue("/users").get?.requestBody)
    }

    @Serializable
    data class TestResponse(val isDone: Boolean)

    data class TestRequest(val name: String)
}
