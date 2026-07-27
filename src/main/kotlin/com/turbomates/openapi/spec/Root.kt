package com.turbomates.openapi.spec

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Root(
    val openapi: String,
    val info: InfoObject,
    // `paths` is required by the spec, so it has to survive `encodeDefaults = false` even for an
    // application that documents no routes at all.
    @EncodeDefault val paths: MutableMap<String, PathItemObject> = mutableMapOf(),
    var servers: List<ServerObject>? = null,
    var components: Components? = null,
    val security: List<SecuritySchemaObject>? = null,
    val tags: List<TagObject>? = null,
    val externalDocs: ExternalDocumentationObject? = null
)

@Serializable
data class InfoObject(
    val title: String,
    val description: String? = null,
    val termsOfService: String? = null,
    val contact: ContactObject? = null,
    val license: LicenseObject? = null,
    val version: String
)

@Serializable
data class TagObject(
    val name: String,
    val description: String?,
    val externalDocs: ExternalDocumentationObject?
)
