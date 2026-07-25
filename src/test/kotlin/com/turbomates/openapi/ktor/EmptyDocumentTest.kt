package com.turbomates.openapi.ktor

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.application.install
import io.ktor.server.testing.testApplication
import io.swagger.parser.OpenAPIParser
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class EmptyDocumentTest {
    @Test
    fun `application without documented routes still has paths`() = testApplication {
        install(OpenAPI)

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        assertContains(response, "\"paths\":{}")
    }
}
