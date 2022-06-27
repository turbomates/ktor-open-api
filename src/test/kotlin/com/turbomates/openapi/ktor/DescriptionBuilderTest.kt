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
            responseMap = mapOf(TestResponse::class.openApiKType() to 400)
            typeBuilder = { openApiKType ->
                when {
                    openApiKType.isSubtypeOf(TestResponse::class.openApiKType()) -> {
                        Type.Object(
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
                    }

                    else -> openApiKType.objectType("response")
                }
            }
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

    private data class TestResponse(val status: HttpStatusCode, val body: String)
    private data class TestRequest(val body: String)
}
