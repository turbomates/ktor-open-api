@file:Suppress("unused")

package com.turbomates.openapi

import com.turbomates.openapi.spec.OAuthFlowObject
import com.turbomates.openapi.spec.OAuthFlowsObject
import com.turbomates.openapi.spec.SecurityRequirement
import com.turbomates.openapi.spec.SecuritySchemeObject

/**
 * The security schemes of the specification, each built with the fields its kind allows.
 *
 * A scheme carries different fields depending on what it is, and a validator rejects a document
 * that mixes them — `bearerFormat` next to an `apiKey` scheme, say. These builders are the way to
 * end up with a scheme that describes one thing only.
 */
object SecurityScheme {
    /** An `Authorization` header of a given HTTP scheme, as registered with IANA. */
    fun http(scheme: String, bearerFormat: String? = null, description: String? = null): SecuritySchemeObject {
        return SecuritySchemeObject(
            type = "http",
            scheme = scheme,
            bearerFormat = bearerFormat,
            description = description
        )
    }

    /** `Authorization: Bearer <token>`, of [bearerFormat] when the format is worth naming. */
    fun bearer(bearerFormat: String? = null, description: String? = null): SecuritySchemeObject {
        return http("bearer", bearerFormat, description)
    }

    fun basic(description: String? = null): SecuritySchemeObject = http("basic", description = description)

    /**
     * A key carried by a header, a query parameter or a cookie.
     *
     * The location has to be one of those three — a key in the path is not a scheme OpenAPI knows.
     */
    fun apiKey(name: String, location: INType = INType.HEADER, description: String? = null): SecuritySchemeObject {
        require(location != INType.PATH) { "an API key lives in a header, a query parameter or a cookie" }
        return SecuritySchemeObject(type = "apiKey", name = name, `in` = location.value, description = description)
    }

    fun oauth2(description: String? = null, flows: OAuthFlowsBuilder.() -> Unit): SecuritySchemeObject {
        return SecuritySchemeObject(type = "oauth2", flows = OAuthFlowsBuilder().apply(flows).build(), description = description)
    }

    fun openIdConnect(url: String, description: String? = null): SecuritySchemeObject {
        return SecuritySchemeObject(type = "openIdConnect", openIdConnectUrl = url, description = description)
    }
}

/** The flows of an `oauth2` scheme, each with the URLs that flow actually has. */
class OAuthFlowsBuilder {
    private var flows = OAuthFlowsObject()

    fun implicit(authorizationUrl: String, scopes: Map<String, String> = emptyMap(), refreshUrl: String? = null) {
        flows = flows.copy(implicit = OAuthFlowObject(authorizationUrl = authorizationUrl, refreshUrl = refreshUrl, scopes = scopes))
    }

    fun password(tokenUrl: String, scopes: Map<String, String> = emptyMap(), refreshUrl: String? = null) {
        flows = flows.copy(password = OAuthFlowObject(tokenUrl = tokenUrl, refreshUrl = refreshUrl, scopes = scopes))
    }

    fun clientCredentials(tokenUrl: String, scopes: Map<String, String> = emptyMap(), refreshUrl: String? = null) {
        flows = flows.copy(
            clientCredentials = OAuthFlowObject(tokenUrl = tokenUrl, refreshUrl = refreshUrl, scopes = scopes)
        )
    }

    fun authorizationCode(
        authorizationUrl: String,
        tokenUrl: String,
        scopes: Map<String, String> = emptyMap(),
        refreshUrl: String? = null
    ) {
        flows = flows.copy(
            authorizationCode = OAuthFlowObject(
                authorizationUrl = authorizationUrl,
                tokenUrl = tokenUrl,
                refreshUrl = refreshUrl,
                scopes = scopes
            )
        )
    }

    fun build(): OAuthFlowsObject = flows
}

/**
 * A requirement to satisfy [scheme], with [scopes] for the schemes that have any.
 *
 * Listing several requirements means any one of them is enough; naming several schemes in one
 * requirement means all of them are needed at once.
 */
fun securityRequirement(scheme: String, vararg scopes: String): SecurityRequirement {
    return mapOf(scheme to scopes.toList())
}
