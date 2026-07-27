package com.turbomates.openapi.spec

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Root(
    val openapi: String,
    // Everything a document says about itself is settable: the title, the servers it is offered at
    // and the tags its operations are grouped by are properties of the application, and the library
    // has no way of knowing them.
    var info: InfoObject,
    // `paths` is required by the spec, so it has to survive `encodeDefaults = false` even for an
    // application that documents no routes at all.
    @EncodeDefault val paths: MutableMap<String, PathItemObject> = mutableMapOf(),
    var servers: List<ServerObject>? = null,
    var components: Components? = null,
    var security: List<SecurityRequirement>? = null,
    var tags: List<TagObject>? = null,
    var externalDocs: ExternalDocumentationObject? = null
)

@Serializable
data class InfoObject(
    var title: String,
    var description: String? = null,
    var termsOfService: String? = null,
    var contact: ContactObject? = null,
    var license: LicenseObject? = null,
    var version: String
)

@Serializable
data class TagObject(
    val name: String,
    val description: String? = null,
    val externalDocs: ExternalDocumentationObject? = null
)
