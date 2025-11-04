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
        val response = client.get("/webjars/swagger-ui/5.4.2/swagger-initializer.js")
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "/openapi.json")
    }
}
