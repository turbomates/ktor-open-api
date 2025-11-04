# Ktor OpenAPI Library - Improvement Roadmap

This document outlines missing features and improvements for the Ktor OpenAPI library based on OpenAPI 3.0 specification and industry best practices.

## Priority Legend
- **P0**: Critical - Core functionality gaps
- **P1**: High - Common features expected by users
- **P2**: Medium - Nice-to-have improvements
- **P3**: Low - Advanced features

---

## 1. Core OpenAPI 3.0 Features

### P1: Security Schemes Integration
**Current State**: Security classes exist (`SecuritySchemeObject`, `OAuthFlowsObject`) but not integrated
**Missing**:
- No way to define security schemes (OAuth2, Bearer, API Key)
- No security requirements on operations
- No global security configuration

**Expected API**:
```kotlin
install(OpenAPI) {
    security {
        bearerAuth("BearerAuth")
        apiKey("ApiKeyAuth", ApiKeyLocation.HEADER, "X-API-Key")
        oauth2("OAuth2") {
            authorizationCode(
                authorizationUrl = "https://example.com/oauth/authorize",
                tokenUrl = "https://example.com/oauth/token",
                scopes = mapOf("read" to "Read access", "write" to "Write access")
            )
        }
    }
}

route.get<User>("/users/{id}") {
    security("BearerAuth")
    // ...
}
```

### P1: Tags and Categorization
**Current State**: `TagObject` class exists but no integration
**Missing**:
- No way to tag operations
- No operation grouping/categorization
- Tags not rendered in Swagger UI

**Expected API**:
```kotlin
route.get<User>("/users") {
    tags("Users", "Public API")
    // ...
}

// or via configuration
install(OpenAPI) {
    tags {
        tag("Users", "User management operations")
        tag("Public API", "Publicly accessible endpoints")
    }
}
```

### P1: Operation Metadata (operationId, summary, description)
**Current State**: Operations have no metadata
**Missing**:
- No `operationId` (used by code generators and tooling)
- No `summary` (short description shown in collapsed state)
- No `description` (detailed documentation with Markdown support)
- No `deprecated` flag

**Best Practice**: operationId should be unique and follow naming conventions (e.g., "getUserById", "createUser")

**Expected API**:
```kotlin
route.get<User>("/users/{id}") {
    operationId = "getUserById"
    summary = "Get a user by ID"
    description = """
        Retrieves a single user by their unique identifier.

        ## Authorization
        Requires Bearer token with `users:read` scope.
    """.trimIndent()
    deprecated = false
    // ...
}
```

### P2: Response Descriptions
**Current State**: Responses have empty descriptions
**Location**: `OpenAPI.kt:142` - `description = ""`
**Impact**: Generated specs have no response documentation
**Solution**:
- Allow custom response descriptions
- Support multiple response codes with different descriptions
- Add default descriptions for common status codes

**Expected API**:
```kotlin
route.post<User, CreateUserRequest>("/users") {
    responses {
        response(201, "User created successfully")
        response(400, "Invalid request body")
        response(409, "User already exists")
    }
}
```

### P2: Examples Support
**Current State**: Limited example integration
**Missing**:
- No request body examples
- No response examples
- No parameter examples

**Expected API**:
```kotlin
route.post<User, CreateUserRequest>("/users") {
    requestExample = CreateUserRequest(
        email = "user@example.com",
        name = "John Doe"
    )
    responseExamples {
        example(201, "Created", User(id = "123", email = "user@example.com"))
        example(400, "Invalid Email", ErrorResponse(message = "Invalid email format"))
    }
}
```

---

## 2. Type System Enhancements

### P1: Validation Constraints Support
**Current State**: No validation metadata captured
**Missing**:
- String constraints (minLength, maxLength, pattern, format)
- Number constraints (minimum, maximum, multipleOf)
- Array constraints (minItems, maxItems, uniqueItems)
- Required/optional field documentation

**Expected**: Integration with Kotlin validation libraries or custom annotations
```kotlin
data class CreateUserRequest(
    @StringLength(min = 3, max = 50)
    val name: String,

    @Email
    val email: String,

    @Range(min = 18, max = 120)
    val age: Int
)
```

### P1: Polymorphism and Discriminators
**Current State**: `DiscriminatorObject` exists but not integrated
**Missing**:
- No support for sealed classes
- No discriminator mapping
- No oneOf/anyOf/allOf schemas

**Expected**: Automatic discriminator detection for sealed classes
```kotlin
@Serializable
sealed class Animal {
    abstract val type: String

    @Serializable
    data class Dog(override val type: String = "dog", val breed: String) : Animal()

    @Serializable
    data class Cat(override val type: String = "cat", val indoor: Boolean) : Animal()
}

// Should generate discriminator with propertyName = "type"
```

### P2: Format Specifiers
**Current State**: Basic type mapping exists
**Missing**:
- No format field for strings (date, date-time, email, uuid, uri, password, etc.)
- UUID is mapped as String without format="uuid"
- Duration/Locale have no standard format

**Expected**: Add format field to Type.String
```kotlin
sealed class Type {
    data class String(
        val isNullable: Boolean = true,
        val enum: List<kotlin.String>? = null,
        val format: StringFormat? = null  // NEW
    ) : Type(isNullable)
}

enum class StringFormat {
    DATE, DATE_TIME, PASSWORD, BYTE, BINARY, EMAIL, UUID, URI, HOSTNAME, IPV4, IPV6
}
```

### P2: Additional Property Support
**Current State**: Objects don't support additionalProperties
**Missing**:
- Map types with value schemas
- Free-form objects
- Pattern properties

**Expected**: Better Map type handling with schema for values

### P3: ReadOnly and WriteOnly Properties
**Current State**: No property modifiers
**Missing**:
- readOnly (e.g., `id`, `createdAt` - returned but not accepted in requests)
- writeOnly (e.g., `password` - accepted but never returned)

**Expected**: Detect based on mutable properties or annotations

---

## 3. Request/Response Enhancements

### P1: Multiple Response Codes per Operation
**Current State**: `responseCodeMap` only maps types to single status codes
**Expected**: Support multiple responses with different schemas
```kotlin
route.post<Unit, CreateUserRequest>("/users") {
    responses {
        success(201, User::class)
        error(400, ValidationError::class)
        error(409, ConflictError::class)
        error(500, InternalError::class)
    }
}
```

### P1: Content-Type Support
**Current State**: Assumes application/json
**Missing**:
- No explicit content type handling
- No support for multiple media types
- No multipart/form-data support

**Expected**:
```kotlin
route.post<File, MultipartData>("/upload") {
    consumes("multipart/form-data")
    produces("image/png", "image/jpeg")
}
```

### P2: Header Parameters
**Current State**: Infrastructure exists but not exposed in route extensions
**Location**: `ParameterObject` supports header type
**Missing**: No DSL for header parameters

**Expected**:
```kotlin
route.get<User>("/users/me") {
    headerParam<String>("Authorization", required = true, description = "Bearer token")
    headerParam<String>("X-Request-ID", required = false)
}
```

### P3: Cookie Parameters
**Current State**: Not supported
**Expected**: Cookie parameter definitions similar to headers

### P3: File Upload/Download Support
**Current State**: No binary content handling
**Expected**:
- Binary schema type
- File upload documentation
- Content-Type handling for files

---

## 4. Documentation & Developer Experience

### P1: Default Values Documentation
**Current State**: Default values not captured
**Expected**: Show default values from Kotlin property defaults in schema

### P1: Field Deprecation Support
**Current State**: No way to mark fields as deprecated
**Expected**:
```kotlin
data class User(
    val id: String,
    @Deprecated("Use 'name' instead")
    val username: String,
    val name: String
)
// Should set deprecated: true on username field
```

### P2: External Documentation Links
**Current State**: `ExternalDocumentationObject` exists but not used
**Expected**:
```kotlin
install(OpenAPI) {
    externalDocs("https://docs.example.com", "Full API Documentation")
}

route.get<User>("/users") {
    externalDocs("https://docs.example.com/users", "User Management Guide")
}
```

### P2: Server Configuration
**Current State**: `ServerObject` exists but not auto-populated
**Expected**: Auto-detect from Ktor server configuration
```kotlin
install(OpenAPI) {
    servers {
        server("https://api.example.com", "Production")
        server("https://staging.example.com", "Staging")
        server("http://localhost:8080", "Development")
    }
}
```

### P2: Better Error Messages
**Current State**: Generic reflection errors
**Expected**: Clear validation and error messages when:
- Types cannot be reflected
- Duplicate operationIds detected
- Invalid path parameter names
- Missing required configuration

### P3: IDE Integration Hints
**Expected**: KDoc comments with examples on route extension functions

---

## 5. Advanced OpenAPI Features

### P3: Callbacks
**Current State**: `CallbackObject` exists but not integrated
**Use Case**: Document webhooks and async callbacks
**Expected**:
```kotlin
route.post<Subscription, CreateSubscription>("/subscriptions") {
    callback("onEvent", "{$request.body#/callbackUrl}") {
        post<Event>("/") {
            // webhook schema
        }
    }
}
```

### P3: Links
**Current State**: `LinkObject` exists but not integrated
**Use Case**: HATEOAS and operation chaining
**Expected**: Link responses to related operations

### P3: Webhook Support (OpenAPI 3.1)
**Current State**: Not supported
**Note**: OpenAPI 3.1+ feature, but commonly requested

---

## 6. Configuration & Customization

### P1: Global Response Headers
**Expected**: Define headers returned by all/most operations
```kotlin
install(OpenAPI) {
    globalResponseHeaders {
        header("X-Request-ID", Type.String(), "Request correlation ID")
        header("X-Rate-Limit-Remaining", Type.Number(), "API rate limit remaining")
    }
}
```

### P2: Custom Type Resolvers
**Current State**: `customTypeDescription` is a simple map
**Expected**: Plugin-based type resolver system
```kotlin
install(OpenAPI) {
    typeResolver { kType ->
        when {
            kType.classifier == MyCustomType::class -> Type.String(format = "custom")
            else -> null
        }
    }
}
```

### P2: Schema Customization Hooks
**Expected**: Modify generated schemas before finalization
```kotlin
install(OpenAPI) {
    schemaTransformer { schema, kType ->
        schema.copy(
            description = kType.annotations.find<ApiDoc>()?.value,
            example = kType.annotations.find<Example>()?.value
        )
    }
}
```

### P3: OpenAPI Extension Support (x-*)
**Expected**: Allow custom vendor extensions
```kotlin
route.get<User>("/users") {
    extension("x-rate-limit", mapOf("limit" to 100, "window" to "1m"))
}
```

---

## 7. Testing & Validation

### P1: OpenAPI Spec Validation
**Current State**: Tests use swagger-parser for validation
**Expected**: Built-in validation with helpful error messages

### P2: Contract Testing Support
**Expected**: Generate test fixtures from OpenAPI spec

### P3: Mock Server Generation
**Expected**: Auto-generate mock responses from examples

---

## 8. Code Quality & Maintenance

### P1: Comprehensive Documentation
**Missing**:
- User guide with examples
- Migration guides
- API reference
- Common use cases

### P2: Performance Optimization
**Considerations**:
- Cache reflected type information
- Lazy schema generation
- Minimize allocations in hot paths

### P2: Better Test Coverage
**Current State**: Basic tests exist
**Expected**:
- Test all HTTP methods
- Test complex nested types
- Test edge cases (nullable, generics, sealed classes)
- Integration tests with real Ktor apps

---

## 9. Comparison with Other Libraries

### Features in Ktor-OpenAPI-Generator (competitor)
- Annotation-based documentation
- Custom response examples
- Multiple authentication schemes
- Nested routing support
- Automatic tag generation from route structure

### Features in Springdoc (Spring Boot)
- Automatic parameter detection from annotations
- Global security requirements
- Grouped APIs with multiple specs
- Custom converters for types
- Pagination support

### Features in FastAPI (Python)
- Automatic validation from type hints
- Response model with status codes
- Dependencies injection documentation
- Background task documentation

---

## 10. Quick Wins (Low Effort, High Impact)

1. **Add PUT to Method enum** - 1 line change
2. **Add response descriptions** - Simple DSL addition
3. **Add operationId support** - Basic string field
4. **Default status code descriptions** - Map of common codes
5. **Better default response code** - Use actual HTTP status from context
6. **Format field for UUID/Date types** - Extend Type.String
7. **Expose header parameters** - Already have infrastructure
8. **Server object auto-population** - Read from Ktor config
9. **External docs support** - Simple property addition
10. **Deprecated field support** - Check @Deprecated annotation

---

## Implementation Priority Recommendations

### Phase 1: Core Fixes (1-2 weeks)
- Operation metadata (operationId, summary, description)
- Response descriptions
- Multiple response codes support

### Phase 2: Security & Documentation (2-3 weeks)
- Security schemes integration
- Tags and categorization
- Better error messages
- Basic validation constraints

### Phase 3: Advanced Types (3-4 weeks)
- Format specifiers
- Polymorphism support
- Header parameters
- Content-Type support

### Phase 4: Polish & Extensions (2-3 weeks)
- Examples support
- External documentation
- Server configuration
- Custom extensions

---

## Notes

- All line number references are approximate and may change with updates
- Priority levels are suggestions based on OpenAPI adoption patterns
- Some features may require breaking API changes
- Consider backward compatibility for existing users
