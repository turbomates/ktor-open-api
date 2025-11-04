rootProject.name = "openapi"

dependencyResolutionManagement {
    versionCatalogs {
        create("deps") {
            version("ktor", "3.3.0")
            version("detekt", "1.23.1")
            version("kotlin", "2.2.20")
            version("swagger_webjar", "5.30.1")
            version("kotlin_serialization_json", "1.9.0")
            version("openapi_validator", "2.1.35")
            version("nexus_staging", "2.0.0")
            library("ktor_webjar", "io.ktor", "ktor-server-webjars").versionRef("ktor")
            library("ktor_locations", "io.ktor", "ktor-server-resources").versionRef("ktor")
            library("ktor_server_core", "io.ktor", "ktor-server-core").versionRef("ktor")
            library("ktor_server_core_jvm", "io.ktor", "ktor-server-core-jvm").versionRef("ktor")
            library("ktor_server_core_webjars_jvm", "io.ktor", "ktor-server-webjars-jvm").versionRef("ktor")
            library("ktor_test", "io.ktor", "ktor-server-test-host").versionRef("ktor")
            library("kotlin_test", "org.jetbrains.kotlin", "kotlin-test-junit5").versionRef("kotlin")
            library("kotlin_serialization_json", "org.jetbrains.kotlinx", "kotlinx-serialization-json").versionRef("kotlin_serialization_json")
            library("kotlin_serialization", "org.jetbrains.kotlin", "kotlin-serialization").versionRef("kotlin")
            library("kotlin_reflect", "org.jetbrains.kotlin", "kotlin-reflect").versionRef("kotlin")
            library("swagger_webjar", "org.webjars", "swagger-ui").versionRef("swagger_webjar")
            library("openapi_validator", "io.swagger.parser.v3", "swagger-parser").versionRef("openapi_validator")
            plugin("kotlin_serialization", "org.jetbrains.kotlin.plugin.serialization").versionRef("kotlin")
            plugin("detekt", "io.gitlab.arturbosch.detekt").versionRef("detekt")
            library("detekt_formatting", "io.gitlab.arturbosch.detekt", "detekt-formatting").versionRef("detekt")
            plugin("nexus.release", "io.github.gradle-nexus.publish-plugin").versionRef("nexus_staging")

            bundle(
                "ktor", listOf(
                    "ktor_server_core",
                    "ktor_server_core_jvm",
                    "ktor_server_core_webjars_jvm",
                    "ktor_locations",
                    "ktor_webjar"
                )
            )
        }
    }
}
