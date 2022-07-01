package com.turbomates.openapi.ktor

import com.turbomates.openapi.OpenApiKType
import com.turbomates.openapi.Property
import com.turbomates.openapi.Type
import com.turbomates.openapi.openApiKType
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
            responseMap = {
                mapOf(400 to typeOf<TestResponse>())
            }
            customMap = mapOf(
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
    fun `custom description with template class`() = testApplication {
        install(OpenAPI) {
            customMap = mapOf(
                typeOf<TestTemplateClass<String>>() to Type.Object(
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
            post<TestTemplateClass<String>, TestRequest>("/test") {
                TestTemplateClass("test")
            }
        }
        val response = client.get("/openapi.json")
        val result = OpenAPIParser().readContents(response.bodyAsText(), null, null)
        assertEquals(0, result.messages.count())
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "\"responses\":{\"200\"")
        assertContains(response.bodyAsText(), "\"example\":{\"error\":\"Wrong response\"}")
    }

    private data class TestTemplateClass<T : Any>(val value: T)
    private data class TestResponse(val status: HttpStatusCode, val body: String)
    private data class TestRequest(val body: String)
}
