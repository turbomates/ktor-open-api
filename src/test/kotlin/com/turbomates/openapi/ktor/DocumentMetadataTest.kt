package com.turbomates.openapi.ktor

import com.turbomates.openapi.SecurityScheme
import com.turbomates.openapi.securityRequirement
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.swagger.parser.OpenAPIParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.Serializable
import com.turbomates.openapi.OpenAPI as SwaggerOpenAPI

class DocumentMetadataTest {
    @Test
    fun `the host is described as the server of the document`() = testApplication {
        install(OpenAPI) {
            documentationBuilder = SwaggerOpenAPI("api.example.com")
        }
        routing {
            get<TestResponse>("/users") { TestResponse(true) }
        }

        val parsed = document(client.get("/openapi.json").bodyAsText())

        assertEquals(emptyList(), parsed.messages)
        assertEquals(listOf("https://api.example.com"), parsed.openAPI.servers.map { it.url })
    }

    @Test
    fun `a local host is described over http`() = testApplication {
        install(OpenAPI) {
            documentationBuilder = SwaggerOpenAPI("localhost:8080")
        }
        routing {
            get<TestResponse>("/users") { TestResponse(true) }
        }

        val parsed = document(client.get("/openapi.json").bodyAsText())

        assertEquals(emptyList(), parsed.messages)
        assertEquals(listOf("http://localhost:8080"), parsed.openAPI.servers.map { it.url })
    }

    @Test
    fun `a host that already is a url is kept as it is`() = testApplication {
        install(OpenAPI) {
            documentationBuilder = SwaggerOpenAPI("https://api.example.com/v2")
        }
        routing {
            get<TestResponse>("/users") { TestResponse(true) }
        }

        val parsed = document(client.get("/openapi.json").bodyAsText())

        assertEquals(emptyList(), parsed.messages)
        assertEquals(listOf("https://api.example.com/v2"), parsed.openAPI.servers.map { it.url })
    }

    @Test
    fun `described servers replace the one derived from the host`() = testApplication {
        install(OpenAPI) {
            documentationBuilder = SwaggerOpenAPI("api.example.com")
            configure = { openApi ->
                openApi.server("https://api.example.com", "Production")
                openApi.server("https://staging.example.com", "Staging")
            }
        }
        routing {
            get<TestResponse>("/users") { TestResponse(true) }
        }

        val parsed = document(client.get("/openapi.json").bodyAsText())

        assertEquals(emptyList(), parsed.messages)
        assertEquals(
            listOf("https://api.example.com" to "Production", "https://staging.example.com" to "Staging"),
            parsed.openAPI.servers.map { it.url to it.description }
        )
    }

    @Test
    fun `the document describes itself`() = testApplication {
        install(OpenAPI) {
            configure = { openApi ->
                openApi.info {
                    title = "Orders"
                    version = "2.0"
                    description = "Everything about orders"
                }
                openApi.tag("Users", "User management")
                openApi.externalDocs("https://docs.example.com", "The full guide")
            }
        }
        routing {
            get<TestResponse>("/users") { TestResponse(true) }
        }

        val parsed = document(client.get("/openapi.json").bodyAsText())
        val openApi = parsed.openAPI

        assertEquals(emptyList(), parsed.messages)
        assertEquals("Orders", openApi.info.title)
        assertEquals("2.0", openApi.info.version)
        assertEquals("Everything about orders", openApi.info.description)
        assertEquals(listOf("Users" to "User management"), openApi.tags.map { it.name to it.description })
        assertEquals("https://docs.example.com", openApi.externalDocs.url)
    }

    @Test
    fun `a bearer scheme is offered and required by default`() = testApplication {
        install(OpenAPI) {
            configure = { openApi ->
                openApi.securityScheme("BearerAuth", SecurityScheme.bearer("JWT"))
                openApi.security(securityRequirement("BearerAuth"))
            }
        }
        routing {
            get<TestResponse>("/users") { TestResponse(true) }
        }

        val parsed = document(client.get("/openapi.json").bodyAsText())
        val scheme = parsed.openAPI.components.securitySchemes.getValue("BearerAuth")

        assertEquals(emptyList(), parsed.messages)
        assertEquals("http", scheme.type.toString())
        assertEquals("bearer", scheme.scheme)
        assertEquals("JWT", scheme.bearerFormat)
        // A bearer scheme has no OAuth2 flows, and a document that invents empty ones is rejected.
        assertNull(scheme.flows)
        assertEquals(listOf(setOf("BearerAuth")), parsed.openAPI.security.map { it.keys })
    }

    @Test
    fun `an api key scheme names the header it is carried in`() = testApplication {
        install(OpenAPI) {
            configure = { openApi ->
                openApi.securityScheme("ApiKeyAuth", SecurityScheme.apiKey("X-API-Key"))
            }
        }
        routing {
            get<TestResponse>("/users") { TestResponse(true) }
        }

        val parsed = document(client.get("/openapi.json").bodyAsText())
        val scheme = parsed.openAPI.components.securitySchemes.getValue("ApiKeyAuth")

        assertEquals(emptyList(), parsed.messages)
        assertEquals("apiKey", scheme.type.toString())
        assertEquals("X-API-Key", scheme.name)
        assertEquals("header", scheme.`in`.toString())
    }

    @Test
    fun `an oauth2 scheme describes the flow it supports`() = testApplication {
        install(OpenAPI) {
            configure = { openApi ->
                openApi.securityScheme(
                    "OAuth2",
                    SecurityScheme.oauth2 {
                        authorizationCode(
                            authorizationUrl = "https://example.com/oauth/authorize",
                            tokenUrl = "https://example.com/oauth/token",
                            scopes = mapOf("read" to "Read access", "write" to "Write access")
                        )
                    }
                )
            }
        }
        routing {
            get<TestResponse>("/users") { TestResponse(true) }
        }

        val parsed = document(client.get("/openapi.json").bodyAsText())
        val flows = parsed.openAPI.components.securitySchemes.getValue("OAuth2").flows

        assertEquals(emptyList(), parsed.messages)
        assertEquals("https://example.com/oauth/token", flows.authorizationCode.tokenUrl)
        assertEquals(setOf("read", "write"), flows.authorizationCode.scopes.keys)
        // Only the flow that was described is written out.
        assertNull(flows.implicit)
        assertNull(flows.password)
    }

    private fun document(json: String) = OpenAPIParser().readContents(json, null, null)

    @Serializable
    data class TestResponse(val isDone: Boolean)
}
