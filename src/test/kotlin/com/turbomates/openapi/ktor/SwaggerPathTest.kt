package com.turbomates.openapi.ktor

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.ktor.server.webjars.Webjars
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class SwaggerPathTest {
    @Test
    fun `swagger replace initial path`() = testApplication {
        install(OpenAPI)
        install(Webjars)
        install(SwaggerPath)
        val response = client.get("/webjars/swagger-ui/index.html?url=/openapi.json")
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "/openapi.json")
    }
}
