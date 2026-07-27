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
- **Operation Metadata**: Summary, description, `operationId`, tags, headers, cookies, media types and per-code responses through a DSL block on every route
- **Security Schemes**: Bearer, basic, API key, OAuth2 and OpenID Connect, required globally or per operation
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

### Documenting an Operation

The types of a route say what it carries. Everything else — what it is called, which header it
reads, what it answers with on a `404` — goes into an optional block between the path and the
handler:

```kotlin
get<UserResponse, UserPath>("/users/{id}", {
    summary = "Find a user"
    description = "Looks the user up by id."
    operationId = "getUser"
    tags("Users")
    deprecated = false

    header<String>("X-Request-Id", required = true, description = "Request correlation id")
    cookie<String>("session")
    queryParameter<Boolean>("expand")

    response(HttpStatusCode.OK, "the user")
    responseOf<ErrorResponse>(HttpStatusCode.NotFound, "no user with that id")
    defaultOf<ErrorResponse>("unexpected failure")

    consumes(MediaType.JSON)
    produces(MediaType.JSON, "text/csv")

    security("BearerAuth")
}) { path ->
    service.find(path.id)
}
```

- `response` describes a status code; `responseOf<T>` gives it a body of its own, which is how one
  operation answers with a resource on `200` and an error on `404`.
- `default` and `defaultOf<T>` describe every code not listed.
- A response without a description of its own is described by what its status code means — `200` is
  `OK`, `404` is `Not Found`.
- `noSecurity()` opts an operation out of the security the document requires globally.

The block is available on every verb, and every part of it is optional.

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
        typeOf<MyCustomType>() to Type.String(format = "custom-format", nullable = false)
    )

    // Everything the document says about itself
    configure = { openAPI ->
        openAPI.info {
            title = "My API"
            version = "1.0.0"
            description = "API documentation"
        }

        openAPI.server("https://api.example.com", "Production")
        openAPI.server("https://staging.example.com", "Staging")

        openAPI.tag("Users", "User management operations")
        openAPI.externalDocs("https://docs.example.com", "Full API documentation")

        openAPI.securityScheme("BearerAuth", SecurityScheme.bearer("JWT"))
        openAPI.securityScheme("ApiKeyAuth", SecurityScheme.apiKey("X-API-Key"))
        openAPI.securityScheme(
            "OAuth2",
            SecurityScheme.oauth2 {
                authorizationCode(
                    authorizationUrl = "https://example.com/oauth/authorize",
                    tokenUrl = "https://example.com/oauth/token",
                    scopes = mapOf("read" to "Read access", "write" to "Write access")
                )
            }
        )

        // Required of every operation that does not state its own
        openAPI.security(securityRequirement("BearerAuth"))
    }
}
```

`documentationBuilder = SwaggerOpenAPI("api.example.com")` is the server of the document until
`server` is called: a bare host is given a scheme (`http` for a local address, `https` otherwise),
and a value that already is a URL is kept as it is.

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
| Instant, ZonedDateTime | string | date-time |
| ByteArray | string | binary |
| URI, URL | string | uri |
| List, Set, Array | array | - |
| Map | object | `additionalProperties` |
| Enum | string | enum values |
| Value Class | (unwrapped type) | - |
| Sealed class | `oneOf` + `discriminator` | - |

Types are described in `components.schemas` and referenced with `$ref`, so a type shared by several
endpoints is written once. `@SerialName`, `@Transient`, property order and default values are taken
from the `kotlinx.serialization` descriptor of the type, so the schema describes what is actually
written; a type that is not `@Serializable` is described by reflection instead.

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
      "metadata": { "type": "object", "additionalProperties": {} }
    },
    "required": ["id", "name", "email", "roles", "metadata"]
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