package com.turbomates.openapi.ktor

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.swagger.parser.OpenAPIParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class OpenAPITest {
    @Test
    fun `swagger json`() = testApplication {
        install(OpenAPI)

        application {
            this.install(ContentNegotiation) {
                json()
            }
            this.routing {
                post<TestResponse, TestRequest>("/test") {
                    TestResponse(HttpStatusCode.OK.value, "test")
                }
            }
        }

        val response = client.get("/openapi.json")
        val documented = client.post("/test") {
            header("content-type", "application/json")
            setBody(buildJsonObject { put("body", "test") }.toString())
        }
        assertEquals(HttpStatusCode.OK, documented.status)
        assertEquals(TestResponse(HttpStatusCode.OK.value, "test"), Json.decodeFromString(documented.bodyAsText()))
        val result = OpenAPIParser().readContents(response.bodyAsText(), null, null)
        assertEquals(0, result.messages.count())
        assertEquals(HttpStatusCode.OK, response.status)
        assertNotNull(result.openAPI.paths["/test"]!!.post)
    }

    @Serializable
    data class TestResponse(val status: Int, val body: String)

    @Serializable
    data class TestRequest(val body: String)

    @Test
    fun `primitive types json`() = testApplication {
        install(OpenAPI)
        routing {
            post<TestResponse, TestPrimitiveRequest>("/test") {
                TestResponse(HttpStatusCode.OK.value, "test")
            }
        }
        val response = client.get("/openapi.json")
        val result = OpenAPIParser().readContents(response.bodyAsText(), null, null)
        assertEquals(0, result.messages.count())
        assertEquals(HttpStatusCode.OK, response.status)
        assertNotNull(result.openAPI.paths["/test"]!!.post)
    }

    @Test
    fun `query parameters types json`() = testApplication {
        install(OpenAPI)
        routing {
            get<TestResponse, TestPrimitiveRequest>("/test") {
                TestResponse(HttpStatusCode.OK.value, "test")
            }
        }
        val response = client.get("/openapi.json")
        val result = OpenAPIParser().readContents(response.bodyAsText(), null, null)
        assertEquals(0, result.messages.count())
        assertEquals(HttpStatusCode.OK, response.status)
        val parameter = result.openAPI.paths["/test"]!!.get.parameters.single()
        assertEquals("body", parameter.name)
        assertEquals("query", parameter.`in`)
    }

    private data class TestPrimitiveRequest(val body: Double)
}
