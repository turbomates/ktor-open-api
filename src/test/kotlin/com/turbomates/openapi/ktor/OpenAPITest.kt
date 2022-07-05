package com.turbomates.openapi.ktor

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.swagger.parser.OpenAPIParser
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.time.Duration

class OpenAPITest {
    @Test
    fun `swagger json`() = testApplication {
        install(OpenAPI)
        routing {
            post<TestResponse, TestRequest>("/test") {
                TestResponse(HttpStatusCode.OK, "test")
            }
        }
        val response = client.get("/openapi.json")
        val result = OpenAPIParser().readContents(response.bodyAsText(), null, null)
        assertEquals(0, result.messages.count())
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "\"paths\":{\"/test\"")
    }

    private data class TestResponse(val status: HttpStatusCode, val body: String)
    private data class TestRequest(val body: String)

    @Test
    fun `primitive types json`() = testApplication {
        install(OpenAPI)
        routing {
            post<TestResponse, TestPrimitiveRequest>("/test") {
                TestResponse(HttpStatusCode.OK, "test")
            }
        }
        val response = client.get("/openapi.json")
        val result = OpenAPIParser().readContents(response.bodyAsText(), null, null)
        assertEquals(0, result.messages.count())
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "\"paths\":{\"/test\"")
    }

    private data class TestPrimitiveRequest(val body: Double)
}
