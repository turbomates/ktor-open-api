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
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlinx.serialization.Serializable
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
        println(client.post("/test") {
            header("content-type", "application/json")
            setBody(buildJsonObject { put("body", "test") }.toString())
        }.bodyAsText()
        )
        val result = OpenAPIParser().readContents(response.bodyAsText(), null, null)
        assertEquals(0, result.messages.count())
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "\"paths\":{\"/test\"")
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
        assertContains(response.bodyAsText(), "\"paths\":{\"/test\"")
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
        assertContains(response.bodyAsText(), "{\"name\":\"body\",\"in\":\"query\"")
    }

    private data class TestPrimitiveRequest(val body: Double)
}
