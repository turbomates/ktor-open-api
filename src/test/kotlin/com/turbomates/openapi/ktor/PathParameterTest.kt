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

class PathParameterTest {
    @Test
    fun `path parameter is documented without params type`() = testApplication {
        install(OpenAPI)
        routing {
            route("/users/{id}") {
                get<TestResponse> { TestResponse(true) }
            }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        assertContains(response, "{\"name\":\"id\",\"in\":\"path\",\"required\":true,\"schema\":{\"nullable\":false,\"type\":\"string\"}}")
    }

    @Test
    fun `path parameter is documented for overload without params type`() = testApplication {
        install(OpenAPI)
        routing {
            get<TestResponse>("/regex/{id}") { TestResponse(true) }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        assertContains(response, "\"name\":\"id\",\"in\":\"path\"")
    }

    @Test
    fun `every path parameter of a nested route is documented`() = testApplication {
        install(OpenAPI)
        routing {
            route("/companies/{companyId}") {
                route("/users/{userId}") {
                    get<TestResponse> { TestResponse(true) }
                }
            }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        assertContains(response, "\"name\":\"companyId\",\"in\":\"path\"")
        assertContains(response, "\"name\":\"userId\",\"in\":\"path\"")
    }

    @Test
    fun `declared path parameter is not duplicated`() = testApplication {
        install(OpenAPI)
        routing {
            get<TestResponse, TestPathParams>("/users/{id}") { TestResponse(true) }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(1, Regex("\"name\":\"id\"").findAll(response).count())
    }

    @Test
    fun `declared path parameter is not duplicated on repeated registration`() = testApplication {
        install(OpenAPI)
        routing {
            route("/users/{id}") {
                get<TestResponse> { TestResponse(true) }
                get<TestResponse> { TestResponse(true) }
            }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        assertEquals(1, Regex("\"name\":\"id\"").findAll(response).count())
    }

    @Serializable
    data class TestResponse(val done: Boolean)

    @Serializable
    data class TestPathParams(val id: String)
}
