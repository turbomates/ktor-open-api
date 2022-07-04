package com.turbomates.openapi.ktor

import com.turbomates.openapi.Property
import com.turbomates.openapi.Type
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.swagger.parser.OpenAPIParser
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class DescriptionBuilderTest {
    @Test
    fun `custom description`() = testApplication {
        install(OpenAPI) {
            responseCodeMap = {
                mapOf(400 to typeOf<TestResponse>())
            }
            customTypeDescription = mapOf(
                typeOf<TestResponse>() to Type.Object(
                    "error",
                    listOf(
                        Property(
                            "error",
                            Type.String()
                        )
                    ),
                    example = buildJsonObject { put("error", "Wrong response") },
                    nullable = false
                )

            )
        }
        routing {
            post<TestResponse, TestRequest>("/test") {
                TestResponse(HttpStatusCode.OK, "test")
            }
        }
        val response = client.get("/openapi.json")
        val result = OpenAPIParser().readContents(response.bodyAsText(), null, null)
        assertEquals(0, result.messages.count())
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "\"responses\":{\"400\"")
        assertContains(response.bodyAsText(), "\"example\":{\"error\":\"Wrong response\"}")

    }

    @Test
    fun `custom response code with template class`() = testApplication {
        install(OpenAPI) {
            responseCodeMap = {
                when {
                    this.isSubtypeOf(typeOf<TestTemplateClass<*>>()) -> mapOf(
                        400 to typeOf<TestTemplateClass<Any>>()
                    )
                    else -> mapOf(200 to typeOf<TestTemplateClass<Any>>())
                }
            }
        }
        routing {
            post<TestTemplateClass<String>, TestRequest>("/test") {
                TestTemplateClass("test")
            }
        }
        val response = client.get("/openapi.json")
        val result = OpenAPIParser().readContents(response.bodyAsText(), null, null)
        assertEquals(0, result.messages.count())
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "\"responses\":{\"400\"")
    }

    private data class TestTemplateClass<T : Any>(val value: T)
    private data class TestResponse(val status: HttpStatusCode, val body: String)
    private data class TestRequest(val body: String)
}
