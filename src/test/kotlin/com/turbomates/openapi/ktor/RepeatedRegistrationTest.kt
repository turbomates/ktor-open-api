package com.turbomates.openapi.ktor

import com.turbomates.openapi.OpenAPI as SwaggerOpenAPI
import com.turbomates.openapi.openApiKType
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.application.install
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.swagger.parser.OpenAPIParser
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class RepeatedRegistrationTest {
    // The same configuration the plugin serializes the document with.
    private val json = Json { encodeDefaults = false }

    @Test
    fun `query parameters are not duplicated`() = testApplication {
        install(OpenAPI)
        routing {
            get<TestResponse, TestPageable>("/users") { TestResponse(true) }
            get<TestResponse, TestPageable>("/users") { TestResponse(true) }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        assertEquals(1, Regex("\"name\":\"page\"").findAll(response).count())
        assertEquals(1, Regex("\"name\":\"size\"").findAll(response).count())
    }

    @Test
    fun `path and query parameters of the same name keep both places`() = testApplication {
        install(OpenAPI)
        routing {
            get<TestResponse, TestPageable>("/users/{page}") { TestResponse(true) }
            get<TestResponse, TestPageable>("/users/{page}") { TestResponse(true) }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        assertContains(response, "\"name\":\"page\",\"in\":\"path\"")
        assertEquals(1, Regex("\"name\":\"page\"").findAll(response).count())
    }

    @Test
    fun `path parameter declared by one of the registrations is described once per operation`() = testApplication {
        install(OpenAPI)
        routing {
            route("/users/{id}") {
                get<TestResponse> { TestResponse(true) }
            }
            get<TestResponse, TestPathParams>("/users/{id}") { TestResponse(true) }
        }

        val response = client.get("/openapi.json").bodyAsText()

        val parsed = OpenAPIParser().readContents(response, null, null)
        assertEquals(emptyList(), parsed.messages)
        // The registration without a params type left a generated description on the path item; the
        // one that declares `id` describes it on the operation, which the spec lets override it.
        val pathItem = parsed.openAPI.paths.getValue("/users/{id}")
        assertEquals(listOf("id"), pathItem.get.parameters.map { it.name })
        // `number` rather than `integer` is C2, untouched here — the point is that the declared
        // description survived instead of the generated `string` one.
        assertEquals("number", pathItem.get.parameters.single().schema.type)
    }

    @Test
    fun `merging the same path through the core builder does not duplicate parameters`() {
        val api = SwaggerOpenAPI("localhost")
        repeat(2) {
            api.addToPath(
                "/users",
                SwaggerOpenAPI.Method.GET,
                queryParams = typeOf<TestPageable>().openApiKType.objectType()
            )
        }

        val document = json.encodeToString(api.root)

        assertEquals(emptyList(), OpenAPIParser().readContents(document, null, null).messages)
        assertEquals(1, Regex("\"name\":\"page\"").findAll(document).count())
    }

    @Serializable
    data class TestResponse(val isDone: Boolean)

    data class TestPageable(val page: Int, val size: Int)

    data class TestPathParams(val id: Int)
}
