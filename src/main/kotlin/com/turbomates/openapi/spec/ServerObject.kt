package com.turbomates.openapi.spec

import kotlinx.serialization.Serializable

@Serializable
data class ServerObject(
    val url: String,
    val description: String? = null,
    val variables: Map<String, ServerVariableObject>? = null
)

/**
 * A variable of a server URL template.
 *
 * `enum` is the set of values the variable may take, so it is a list; `default` is the one used
 * when the caller says nothing, and is the only part a variable cannot do without.
 */
@Serializable
data class ServerVariableObject(
    val default: String,
    val enum: List<String>? = null,
    val description: String? = null
)
