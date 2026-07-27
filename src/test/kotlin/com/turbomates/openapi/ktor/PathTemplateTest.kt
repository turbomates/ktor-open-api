package com.turbomates.openapi.ktor

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.application.install
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.swagger.parser.OpenAPIParser
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlinx.serialization.Serializable

class PathTemplateTest {
    @Test
    fun `optional path parameter is normalized`() = testApplication {
        install(OpenAPI)
        routing {
            get<TestResponse>("/optional/{id?}") { TestResponse(true) }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        assertContains(response, "\"/optional/{id}\"")
        assertContains(response, "\"name\":\"id\",\"in\":\"path\",\"required\":true")
    }

    @Test
    fun `tailcard keeps its name`() = testApplication {
        install(OpenAPI)
        routing {
            get<TestResponse>("/files/{path...}") { TestResponse(true) }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        assertContains(response, "\"/files/{path}\"")
        assertContains(response, "\"name\":\"path\",\"in\":\"path\",\"required\":true")
    }

    @Test
    fun `unnamed tailcard is still documented`() = testApplication {
        install(OpenAPI)
        routing {
            get<TestResponse>("/anonymous/{...}") { TestResponse(true) }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        assertContains(response, "\"/anonymous/{**}\"")
        assertContains(response, "\"name\":\"**\",\"in\":\"path\",\"required\":true")
    }

    @Test
    fun `wildcard segment is documented`() = testApplication {
        install(OpenAPI)
        routing {
            get<TestResponse>("/wildcard/*") { TestResponse(true) }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        assertContains(response, "\"/wildcard/{*}\"")
    }

    @Test
    fun `root path is not empty`() = testApplication {
        install(OpenAPI)
        routing {
            get<TestResponse>("/") { TestResponse(true) }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        assertContains(response, "\"paths\":{\"/\"")
    }

    @Test
    fun `parentheses in a constant segment are kept`() = testApplication {
        install(OpenAPI)
        routing {
            get<TestResponse>("/reports/(daily)/summary") { TestResponse(true) }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        assertContains(response, "\"/reports/(daily)/summary\"")
    }

    @Test
    fun `trailing slash does not split the path in two`() = testApplication {
        install(OpenAPI)
        routing {
            route("/trailing/") {
                get<TestResponse> { TestResponse(true) }
            }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        assertContains(response, "\"paths\":{\"/trailing\"")
    }

    @Serializable
    data class TestResponse(val isDone: Boolean)
}
