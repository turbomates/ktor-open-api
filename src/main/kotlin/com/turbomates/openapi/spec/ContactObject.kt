package com.turbomates.openapi.spec

import kotlinx.serialization.Serializable

@Serializable
data class ContactObject(val name: String, val url: String, val email: String)
