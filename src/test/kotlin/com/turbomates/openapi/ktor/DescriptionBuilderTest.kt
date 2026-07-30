package com.turbomates.openapi.ktor

import com.fasterxml.jackson.databind.JsonNode
import com.turbomates.openapi.MediaType
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
        val schema = result.openAPI.paths.getValue("/test").post
            .responses.getValue("400")
            .content.getValue(MediaType.JSON).schema

        assertEquals(0, result.messages.count())
        assertEquals(HttpStatusCode.OK, response.status)
        // The type is described the way it was named rather than the way reflection reads it, so
        // the response carries the `error` of the description and not the properties of the class.
        assertEquals(setOf("error"), schema.properties.keys)
        assertEquals("Wrong response", (schema.example as JsonNode).get("error").asText())
    }

    @Test
    fun `custom response code with template class`() = testApplication {
        install(OpenAPI) {
            responseCodeMap = {
                if (this.isSubtypeOf(typeOf<TestTemplateClass<*>>())) {
                    mapOf(400 to typeOf<TestTemplateClass<Any>>())
                } else {
                    mapOf(200 to typeOf<TestTemplateClass<Any>>())
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
        assertEquals(setOf("400"), result.openAPI.paths.getValue("/test").post.responses.keys)
    }

    private data class TestTemplateClass<T : Any>(val value: T)
    private data class TestResponse(val status: HttpStatusCode, val body: String)
    private data class TestRequest(val body: String)
}
