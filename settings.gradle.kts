rootProject.name = "openapi"

dependencyResolutionManagement {
    versionCatalogs {
        create("deps") {
            version("ktor", "2.3.3")
            version("detekt", "1.23.1")
            version("kotlin", "1.9.0")
            version("swagger_webjar", "5.4.2")
            version("kotlin_serialization_json", "1.6.0-RC")
            version("openapi_validator", "2.1.16")
            version("nexus_staging", "0.30.0")
            version("gradle_versions", "0.47.0")
            library("ktor_webjar", "io.ktor", "ktor-server-webjars").versionRef("ktor")
            library("ktor_locations", "io.ktor", "ktor-server-locations").versionRef("ktor")
            library("ktor_server_core", "io.ktor", "ktor-server-core").versionRef("ktor")
            library("ktor_test", "io.ktor", "ktor-server-test-host").versionRef("ktor")
            library("kotlin_test", "org.jetbrains.kotlin", "kotlin-test-junit5").versionRef("kotlin")
            library("kotlin_serialization_json", "org.jetbrains.kotlinx", "kotlinx-serialization-json").versionRef("kotlin_serialization_json")
            library("kotlin_serialization", "org.jetbrains.kotlin", "kotlin-serialization").versionRef("kotlin")
            library("kotlin_reflect", "org.jetbrains.kotlin", "kotlin-reflect").versionRef("kotlin")
            library("swagger_webjar", "org.webjars", "swagger-ui").versionRef("swagger_webjar")
            library("openapi_validator", "io.swagger.parser.v3", "swagger-parser").versionRef("openapi_validator")
            plugin("kotlin_serialization", "org.jetbrains.kotlin.plugin.serialization").versionRef("kotlin")
            plugin("detekt", "io.gitlab.arturbosch.detekt").versionRef("detekt")
            plugin("gradle_versions", "com.github.ben-manes.versions").versionRef("gradle_versions")
            library("detekt_formatting", "io.gitlab.arturbosch.detekt", "detekt-formatting").versionRef("detekt")
            plugin("nexus_release","io.codearte.nexus-staging").versionRef("nexus_staging")
        }
    }
}
