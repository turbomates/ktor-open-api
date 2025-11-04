![](https://turbomates.com/wp-content/uploads/2019/11/logo-e1573642672476.png)

[![Project Status: WIP – Initial development is in progress, but there has not yet been a stable, usable release suitable for the public.](https://www.repostatus.org/badges/latest/wip.svg)](https://www.repostatus.org/#wip)

# Ktor OpenAPI

Automatically generate OpenAPI 3.0 documentation for your Ktor application based on routing definitions and Kotlin type information. This library uses Kotlin reflection and reified generics to build accurate OpenAPI specifications without manual schema definitions.

## Features

- **Automatic Schema Generation**: Generates OpenAPI schemas from Kotlin types using reflection
- **Type-Safe**: Uses reified generics to capture request/response types at compile time
- **Zero Boilerplate**: No need to manually write OpenAPI annotations or schema definitions
- **Comprehensive Type Support**:
  - Primitive types (String, Number, Boolean, UUID, Duration, Locale)
  - Collections (List, Set, Array)
  - Maps
  - Enums
  - Value classes (inline classes)
  - Generic type parameters
  - Nested objects
- **Path, Query, and Body Parameters**: Automatically extracts parameter definitions from route signatures
- **Swagger UI Integration**: Built-in Swagger UI for interactive API documentation
- **Custom Type Mapping**: Configure custom OpenAPI types for specific Kotlin types

## Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.turbomates:ktor-openapi:VERSION")
}
```

## Quick Start

### 1. Install the Plugin

```kotlin
import com.turbomates.openapi.ktor.OpenAPI
import com.turbomates.openapi.OpenAPI as SwaggerOpenAPI

fun Application.module() {
    install(OpenAPI) {
        documentationBuilder = SwaggerOpenAPI("api.example.com")
        path = "/openapi.json"  // OpenAPI spec endpoint (default)
    }

    routing {
        // Your routes here
    }
}
```

### 2. Define Type-Safe Routes

```kotlin
import com.turbomates.openapi.ktor.*

// Simple GET endpoint with typed response
get<UserResponse>("/users/{id}") { params ->
    val userId = call.parameters["id"]
    // ... fetch user
    UserResponse(id = userId, name = "John Doe")
}

// POST with request body
post<CreatedResponse, CreateUserRequest>("/users") { request ->
    // ... create user
    CreatedResponse(id = UUID.randomUUID())
}

// GET with query parameters
get<List<UserResponse>, UserQueryParams>("/users") { queryParams ->
    // ... fetch users with filters
    listOf(UserResponse(...))
}

// Complex example with path and body parameters
post<Response.Either<Response.Data<UUID>, Response.Errors>, RegisterUser>("/register") { command ->
    command.locale = call.resolveLocale()
    controller<UserController>(this).register(command)
}
```

### 3. Access Documentation

- **OpenAPI Spec**: `http://localhost:8080/openapi.json`
- **Swagger UI**: `http://localhost:8080/swagger-ui/index.html`

## Usage

### HTTP Method Extensions

The library provides type-safe extension functions for all HTTP methods:

#### GET

```kotlin
// Simple response
get<UserResponse>("/users/{id}") { params ->
    UserResponse(...)
}

// With query parameters
get<List<UserResponse>, UserQueryParams>("/users") { query ->
    // query.limit, query.offset, etc.
    listOf(UserResponse(...))
}
```

#### POST

```kotlin
// With request body
post<CreatedResponse, CreateUserRequest>("/users") { body ->
    CreatedResponse(id = service.create(body))
}

// With query parameters and body
post<UpdatedResponse, QueryParams, UpdateRequest>("/users") { query, body ->
    UpdatedResponse(...)
}
```

#### PUT

```kotlin
put<UserResponse, UpdateUserRequest>("/users/{id}") { body ->
    UserResponse(...)
}
```

#### PATCH

```kotlin
patch<UserResponse, PatchUserRequest>("/users/{id}") { body ->
    UserResponse(...)
}
```

#### DELETE

```kotlin
delete<DeleteResponse>("/users/{id}") { params ->
    DeleteResponse(success = true)
}
```

### Configuration

#### Basic Configuration

```kotlin
install(OpenAPI) {
    documentationBuilder = SwaggerOpenAPI("api.example.com")
    path = "/openapi.json"
}
```

#### Advanced Configuration

```kotlin
install(OpenAPI) {
    documentationBuilder = SwaggerOpenAPI("api.example.com")
    path = "/openapi.json"

    // Custom response code mapping
    responseCodeMap = {
        mapOf(
            HttpStatusCode.OK.value to this,
            HttpStatusCode.Created.value to this
        )
    }

    // Custom type descriptions for specific types
    customTypeDescription = mapOf(
        typeOf<MyCustomType>() to Type(
            type = DataType.STRING,
            format = "custom-format",
            description = "Custom type description"
        )
    )

    // Additional configuration
    configure = { openAPI ->
        openAPI.info.title = "My API"
        openAPI.info.version = "1.0.0"
        openAPI.info.description = "API documentation"
    }
}
```

### Type System

The library automatically converts Kotlin types to OpenAPI schemas:

| Kotlin Type | OpenAPI Type | Format |
|-------------|--------------|--------|
| String | string | - |
| Int, Long | integer | int32, int64 |
| Float, Double | number | float, double |
| Boolean | boolean | - |
| UUID | string | uuid |
| Duration | string | duration |
| Locale | string | - |
| LocalDate | string | date |
| LocalDateTime | string | date-time |
| List, Set, Array | array | - |
| Map | object | - |
| Enum | string | enum values |
| Value Class | (unwrapped type) | - |

#### Complex Types

```kotlin
data class UserResponse(
    val id: UUID,
    val name: String,
    val email: String,
    val roles: List<Role>,
    val metadata: Map<String, Any>
)

enum class Role {
    ADMIN, USER, GUEST
}
```

The above will automatically generate:

```json
{
  "UserResponse": {
    "type": "object",
    "properties": {
      "id": { "type": "string", "format": "uuid" },
      "name": { "type": "string" },
      "email": { "type": "string" },
      "roles": {
        "type": "array",
        "items": { "type": "string", "enum": ["ADMIN", "USER", "GUEST"] }
      },
      "metadata": { "type": "object" }
    }
  }
}
```

## Build & Development

### Building

```bash
./gradlew build
# or
make gradlew-build  # alias: make gb
```

### Testing

```bash
./gradlew test
# or
make test  # alias: make t
```

Run a specific test:

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
```

Update detekt baselines:

```bash
make detekt-baseline-main  # alias: make dbm
make detekt-baseline-test  # alias: make dbt
```

### View Available Tasks

```bash
./gradlew tasks
# or
make gradlew-tasks  # alias: make gt
```

## Architecture

### Core Components

1. **OpenAPI Builder** (`com.turbomates.openapi.OpenAPI`): Core class that builds the OpenAPI specification. Manages paths, operations, schemas, and custom type mappings.

2. **Ktor Plugin** (`com.turbomates.openapi.ktor.OpenAPI`): Ktor plugin that integrates with the routing system. Installs the `/openapi.json` endpoint and intercepts route definitions to extract type information.

3. **Type System** (`OpenApiKType`): Reflection-based type introspection that converts Kotlin types to OpenAPI type definitions.

4. **HTTP Method Extensions**: Extension functions for Ktor routing that capture type information via reified generics.

### Key Design Patterns

- **Reified Type Capture**: Uses inline reified functions to capture types at compile time
- **Type Merging**: Routes defined multiple times have their specifications merged
- **Custom Type Mapping**: Plugin configuration allows mapping specific types to custom OpenAPI definitions

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

[Add your license information here]

## Support

For issues, questions, or contributions, please visit the [GitHub repository](https://github.com/turbomates/ktor-open-api).