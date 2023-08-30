package com.turbomates.openapi.ktor

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.ktor.server.webjars.Webjars
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.junit.jupiter.api.Disabled

class SwaggerPathTest {
    @Test
    fun `swagger replace initial path`() = testApplication {
        install(OpenAPI)
        val response = client.get("/webjars/swagger-ui/swagger-initializer.js")
        assertEquals(HttpStatusCode.OK, response.status)
        println(response.bodyAsText())
        assertContains(response.bodyAsText(), "/openapi.json")
    }
}
