package com.turbomates.openapi.ktor

import io.ktor.http.content.LastModifiedVersion
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.versions
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.request.path
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.copyTo
import java.io.ByteArrayOutputStream


val SwaggerPath = createApplicationPlugin(name = "SwaggerPath") {
    onCallRespond { call ->
        transformBody { data ->
            if (call.request.path().contains("swagger-ui/index.html") && data is OutgoingContent.ReadChannelContent) {
                val output = ByteArrayOutputStream()
                data.readFrom().copyTo(output)
                val response = String(output.toByteArray())
                if (response.isNotBlank() && response.contains("""url: "https://petstore.swagger.io/v2/swagger.json"""")) {
                    object : OutgoingContent.ReadChannelContent() {
                        override val contentType = data.contentType

                        init {
                            versions = versions + LastModifiedVersion(GMTDate())
                        }

                        override fun readFrom(): ByteReadChannel =
                            ByteReadChannel(
                                response.replace(
                                    """url: "https://petstore.swagger.io/v2/swagger.json"""",
                                    """url: "/api/openapi.json",
                                    operationsSorter: "alpha",
                                    tagsSorter: "alpha""""
                                )
                            )
                    }
                } else {
                    data
                }
            } else {
                data
            }
        }
    }
}


