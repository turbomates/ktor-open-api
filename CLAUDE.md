# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Ktor Open API is a library that automatically generates OpenAPI documentation based on Ktor routing and response return types. It uses Kotlin reflection and type inference to build OpenAPI specifications from route definitions.

## Build & Development Commands

### Building
```bash
./gradlew build
# or via Makefile
make gradlew-build  # alias: make gb
```

### Testing
```bash
./gradlew test
# or via Makefile
make test  # alias: make t
```

Run a single test:
```bash
./gradlew test --tests "com.turbomates.openapi.ktor.OpenAPITest"
```

### Linting (Detekt)
```bash
# Run all detekt checks
make detekt  # alias: make d

# Run detekt on main sources only
./gradlew detektMain
# or: make detekt-main (alias: make dm)

# Run detekt on test sources only
./gradlew detektTest
# or: make detekt-test (alias: make dt)

# Update detekt baselines
make detekt-baseline-main  # alias: make dbm
make detekt-baseline-test  # alias: make dbt
```

Note: detekt is configured via `detekt.yml` and uses the detekt-formatting plugin.

### View Available Tasks
```bash
./gradlew tasks
# or: make gradlew-tasks (alias: make gt)
```

## Architecture

### Core Components

1. **OpenAPI Builder** (`com.turbomates.openapi.OpenAPI`): Core class that builds the OpenAPI specification. Manages paths, operations, schemas, and custom type mappings.

2. **Ktor Plugin** (`com.turbomates.openapi.ktor.OpenAPI`): Ktor plugin that integrates with the routing system. Installs a `/openapi.json` endpoint and intercepts route definitions to extract type information.

3. **Type System** (`OpenApiKType`): Reflection-based type introspection that converts Kotlin types to OpenAPI type definitions. Handles:
   - Primitive types (String, Number, Boolean, UUID, Duration, Locale)
   - Collections (List, Set, Array)
   - Maps
   - Enums
   - Value classes (inline classes)
   - Generic type parameters and projections
   - Nested objects

4. **HTTP Method Extensions** (`Get.kt`, `Post.kt`, `Patch.kt`, `Delete.kt`): Extension functions for Ktor routing that capture type information via reified generics. Each supports multiple overloads for different parameter combinations (no params, query params, path params, body, or combinations).

### Key Design Patterns

**Reified Type Capture**: Route definitions use inline reified functions to capture response, request body, path parameter, and query parameter types at compile time:
```kotlin
inline fun <reified TResponse : Any, reified TRequest : Any> Route.post(
    path: String,
    noinline body: suspend PipelineContext<Unit, ApplicationCall>.(TRequest) -> TResponse
): Route
```

**Dual OpenAPI Classes**: There are two `OpenAPI` classes:
- `com.turbomates.openapi.OpenAPI`: Core builder (called `SwaggerOpenAPI` in imports)
- `com.turbomates.openapi.ktor.OpenAPI`: Ktor plugin wrapper

**Type Merging**: Routes defined multiple times (e.g., via different overloads) have their specifications merged, combining responses, parameters, and request bodies.

**Custom Type Mapping**: The plugin configuration allows mapping specific KTypes to custom OpenAPI type definitions via `customTypeDescription` map.

### Configuration

The OpenAPI plugin is configured during installation:
```kotlin
install(OpenAPI) {
    documentationBuilder = SwaggerOpenAPI("your-host")
    path = "/openapi.json"  // default endpoint
    responseCodeMap = { mapOf(HttpStatusCode.OK.value to this) }
    customTypeDescription = mapOf(/* KType to Type mappings */)
    configure = { swaggerOpenAPI ->
        // Additional configuration
    }
}
```

### OpenAPI Spec Structure

- **Root** (`spec.Root`): OpenAPI 3.0.2 document root
- **Components**: Reusable schemas stored in `root.components.schemas`
- **Paths**: Routes organized by path with operations per HTTP method
- **Operations**: Include responses, request bodies, and parameters
- **Parameters**: Typed as query, path, or header parameters

### Testing

Tests use `testApplication` from Ktor's test framework and validate generated OpenAPI JSON with `OpenAPIParser` from swagger-parser library. Tests verify that generated specs are valid OpenAPI 3.0 documents.