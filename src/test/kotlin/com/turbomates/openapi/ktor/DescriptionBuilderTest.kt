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
import kotlin.test.assertEquals
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** A type the API describes by hand, through `customTypeDescription`, instead of by reflection. */
class DescriptionBuilderTest {
    private val errorDescription = Type.Object(
        "error",
        listOf(Property("error", Type.String())),
        example = buildJsonObject { put("error", "Wrong response") },
        nullable = false
    )

    @Test
    fun `a description given by hand wins over what reflection reads`() = testApplication {
        install(OpenAPI) {
            responseCodeMap = {
                mapOf(400 to typeOf<TestResponse>())
            }
            customTypeDescription = mapOf(typeOf<TestResponse>() to errorDescription)
        }
        routing {
            post<TestResponse, TestRequest>("/test") {
                TestResponse(HttpStatusCode.OK, "test")
            }
        }

        val response = client.get("/openapi.json")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(emptyList(), OpenAPIParser().readContents(body, null, null).messages)
        // The type is described the way it was named rather than the way reflection reads it: the
        // `error` of the description, and not the status and the body the class happens to carry.
        val document = body.document()
        assertEquals(listOf("error"), document.properties("TestResponse").keys.toList())
        assertEquals(
            buildJsonObject { put("error", "Wrong response") },
            document.schema("TestResponse").getValue("example").jsonObject
        )
        assertEquals(setOf("400"), document.responses("/test", "post").keys)
    }

    @Test
    fun `a description that is an object of its own is described once and referenced`() = testApplication {
        install(OpenAPI) {
            responseCodeMap = {
                mapOf(400 to typeOf<TestResponse>())
            }
            customTypeDescription = mapOf(typeOf<TestResponse>() to errorDescription)
        }
        routing {
            post<TestResponse, TestRequest>("/first") {
                TestResponse(HttpStatusCode.OK, "test")
            }
            post<TestResponse, TestRequest>("/second") {
                TestResponse(HttpStatusCode.OK, "test")
            }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        val document = response.document()
        // The description used to be written out wherever the type was used, examples and enums
        // included, so a type a hundred properties carry carried its whole description a hundred
        // times over. It belongs in `components` like any other object.
        assertEquals(setOf("TestResponse", "TestRequest"), document.schemas().keys)
        assertEquals(reference("TestResponse"), document.responseSchema("/first", "post", "400"))
        assertEquals(reference("TestResponse"), document.responseSchema("/second", "post", "400"))
    }

    @Test
    fun `a response code is derived from the type of the response`() = testApplication {
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
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(emptyList(), OpenAPIParser().readContents(body, null, null).messages)
        val document = body.document()
        assertEquals(setOf("400"), document.responses("/test", "post").keys)
        assertEquals(reference("TestTemplateClassAny"), document.responseSchema("/test", "post", "400"))
    }

    private data class TestTemplateClass<T : Any>(val value: T)
    private data class TestResponse(val status: HttpStatusCode, val body: String)
    private data class TestRequest(val body: String)
}
