# Ktor OpenAPI Library - Improvement Roadmap

This document outlines missing features and improvements for the Ktor OpenAPI library based on OpenAPI 3.0 specification and industry best practices.

## Priority Legend
- **P0**: Critical - Core functionality gaps
- **P1**: High - Common features expected by users
- **P2**: Medium - Nice-to-have improvements
- **P3**: Low - Advanced features

## Status (as of 2026-07-30)

**Done** — schema registry with `$ref`/components (recursion included), polymorphism (`oneOf` +
discriminator for sealed hierarchies), formats and `integer`/`number` split, maps as
`additionalProperties`, the whole operation DSL (metadata, tags, per-status responses, media
types, header/cookie parameters), security schemes and requirements, servers/info/tags/external
docs on the document, serializer-driven schemas (`@SerialName`, required/optional), path template
parameters, HEAD/OPTIONS/TRACE, 91 tests.

**Still open** — examples DSL, validation constraints, readOnly/writeOnly, field-level
deprecation, default values in schemas, custom type resolvers and schema hooks, vendor
extensions (`x-*`), global response headers, callbacks/links/webhooks, per-part `multipart`
encoding, caching of the serialized document, richer error messages.

---

## 1. Core OpenAPI 3.0 Features

### P1: Security Schemes Integration — **done**
**Delivered**: `SecurityScheme` builds a scheme of any kind — `http`, `bearer`, `basic`,
`apiKey` (header/query/cookie), `oauth2` with all four flows, `openIdConnect`. The document
offers schemes with `securityScheme(name, scheme)` and requires them globally with
`security(...)`; an operation states its own with `security("BearerAuth", "scope")` or opts out
with `noSecurity()`.
```kotlin
configure = { api ->
    api.securityScheme("BearerAuth", SecurityScheme.bearer("JWT"))
    api.security(securityRequirement("BearerAuth"))
}
route.get<User>("/users/{id}", { security("BearerAuth") }) { ... }
```
- `Security.kt` (`SecurityScheme`, `OAuthFlowsBuilder`, `securityRequirement`), `OpenAPI.kt`
  (`securityScheme`, `security`), `OperationBuilder.kt` (`security`, `noSecurity`)
- `DocumentMetadataTest` — bearer / api key / oauth2 schemes; `OperationMetadataTest` —
  `an operation requires the scheme it names`

### P1: Tags and Categorization — **done**
**Delivered**: `tags("Users", "Public API")` on the operation DSL groups the operation; the
document describes tags with `tag(name, description, externalDocs)`.
- `OperationBuilder.kt` (`tags`), `OpenAPI.kt` (`tag`)
- `OperationMetadataTest` — `an operation is described by what its types cannot say`

### P1: Operation Metadata (operationId, summary, description) — **done**
**Delivered**: `summary`, `description`, `operationId` and `deprecated` are properties of the
operation DSL; `externalDocs(url, description)` sits next to them. A repeated registration keeps
what the operation already says instead of overwriting it.
```kotlin
route.get<User, UserPath>("/users/{id}", {
    operationId = "getUserById"
    summary = "Get a user by ID"
    description = "Retrieves a single user by their unique identifier."
}) { path -> service.find(path.id) }
```
- `OperationBuilder.kt`, `Operation.kt` (`OperationDescription`, `OperationFactory.merge`)
- `OperationMetadataTest`; `OperationMetadataTest` — `a route registered again keeps what it
  already describes`

### P2: Response Descriptions — **done**
**Delivered**: `response(status, description)` describes a code by hand, and a response without a
description falls back on the meaning of its status code (`200` → "OK", `404` → "Not Found", …)
instead of an empty string.
- `OperationBuilder.kt` (`response`), `Operation.kt` (`REASON_PHRASES`, `defaultDescription`)
- `OperationMetadataTest` — `a response says what the status code means`,
  `a described response keeps its description and its body`

### P2: Examples Support
**Current State**: An example can only be smuggled in through `customTypeDescription`
(`Type.String` / `Type.Object` carry an `example`). Nothing on the operation DSL.
**Missing**:
- No request body examples
- No response examples
- No parameter examples

**Expected API**:
```kotlin
route.post<User, CreateUserRequest>("/users", {
    requestExample = CreateUserRequest(email = "user@example.com", name = "John Doe")
    responseExamples {
        example(201, "Created", User(id = "123", email = "user@example.com"))
        example(400, "Invalid Email", ErrorResponse(message = "Invalid email format"))
    }
}) { ... }
```

---

## 2. Type System Enhancements

### P0: Schema Registry with $ref/components — **done**
**Delivered**: every type described by reflection is written to `components.schemas` once and
referenced with `$ref` from everywhere it is used. The name is taken before the schema is built,
so a type referring to itself — directly, through a collection or through another type — resolves
to a reference instead of recursing forever. Names are derived from the class and its type
arguments (`PageUserResponse`), with a counter when two types collide; a model added by hand with
`addModel` keeps its name. A nullable use wraps the reference in `allOf`, since a `$ref` ignores
its siblings in OpenAPI 3.0.
- `SchemaRegistry.kt`; `Type.Ref`, `OpenApiKType.building` for cycle detection
- `SchemaComponentTest` — recursion (self, mutual), reuse across operations, name collisions,
  hand-added models, nullable references

### P1: Validation Constraints Support
**Current State**: No validation metadata captured; `SchemaObject` has no
`minLength`/`maximum`/`pattern`/… fields yet.
**Missing**:
- String constraints (minLength, maxLength, pattern)
- Number constraints (minimum, maximum, multipleOf)
- Array constraints (minItems, maxItems, uniqueItems)

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

### P1: Polymorphism and Discriminators — **done**
**Delivered**: a `sealed` type is described as `oneOf` its leaf subclasses, keyed by the serial
names `kotlinx.serialization` writes, with a `discriminator` and its `mapping` when the hierarchy
is `@Serializable`. `@JsonClassDiscriminator` is honoured; hierarchies deeper than one level list
only their leaves; sealed interfaces reached twice stay one option. Each subclass schema carries
the discriminator property itself — a required enum of the single value that subclass is written
under — so a body generated from the document holds the key `kotlinx` refuses to decode without.
- `OpenApiKType.kt` (`oneOfType`, `sealedLeaves`, `classDiscriminator`, `serialName`,
  `discriminatorProperty`), `Type.OneOf`, `SchemaRegistry.oneOfSchemaObject`
- `SealedTypeTest` — 10 cases: `oneOf` and mapping, the discriminator value per subclass,
  `@JsonClassDiscriminator`, deep hierarchies, generic hierarchies, sealed interfaces, a
  hierarchy with no serializer, and one that refers to itself

### P2: Format Specifiers — **done**
**Delivered**: `format` on strings and numbers throughout. Whole numbers are `integer`
(`int32`/`int64`), floating point stays `number` (`float`/`double`). `UUID` → `uuid`,
`Duration` → `duration`, `ByteArray` → `binary`, `URI`/`URL` → `uri`; `java.time`,
`kotlinx.datetime` and `kotlin.uuid` types are matched by name — no extra dependency — and
described as `date` / `date-time` / `time` strings instead of being taken apart into their
fields.
- `Format`, `OpenApiKType.kt` (`numberType`, `builtInType`, `BUILT_IN_FORMATS`)
- `TypeFormatTest` — integers and widths, values described by format, arrays, maps

### P2: Additional Property Support — **done** (pattern properties aside)
**Delivered**: a `Map<K, V>` is `type: object` with `additionalProperties` holding the schema of
`V` — not a property named after the key class. A value nothing is known about (`Any`, a star
projection, an unresolved type parameter) is an empty schema, which is OpenAPI for "anything".
- `Type.Map`, `Type.Any`, `OpenApiKType.mapType`, `SchemaRegistry.kt`
- `TypeFormatTest` — `a map describes the values it holds, not a property named after its key`
- **Not done**: pattern properties (OpenAPI 3.1).

### P3: ReadOnly and WriteOnly Properties
**Current State**: `SchemaObject` carries the fields, nothing sets them.
**Expected**: Detect based on annotations (e.g. `readOnly` for `id`/`createdAt`, `writeOnly` for
`password`).

---

## 3. Request/Response Enhancements

> **Done.** The operation DSL (`OperationBuilder`) carries everything below. See the DSL block on
> every route in `README.md`, and the tests in `OperationMetadataTest` / `TypeFormatTest`.

### P1: Multiple Response Codes per Operation — **done**
**Delivered**: `response(status, description)`, `responseOf<T>(status, description)`, `default()` and
`defaultOf<T>()` describe as many codes as an operation answers with, each with its own body.
The response type of the route is still documented on its own; these add to it.
```kotlin
route.post<User, CreateUserRequest>("/users", {
    responseOf<ValidationError>(HttpStatusCode.BadRequest, "invalid body")
    responseOf<ConflictError>(HttpStatusCode.Conflict, "user already exists")
    defaultOf<InternalError>("unexpected error")
}) { request -> service.create(request) }
```
- `OperationBuilder.kt` (`response`, `responseOf`, `default`, `defaultOf`)
- `OperationMetadataTest` — `one operation answers with a body of its own per status code`

### P1: Content-Type Support — **done**
**Delivered**: `consumes(...)` / `produces(...)` set the media types of the request body and of the
responses; either takes several. `MediaType` names the common ones (`JSON`, `FORM`,
`URL_ENCODED`, `OCTET_STREAM`, `TEXT`), and any other may be given as a string.
```kotlin
route.post<ByteArray, UploadForm>("/upload", {
    consumes(MediaType.FORM)
    produces("image/png", "image/jpeg")
}) { form -> render(form) }
```
- `OperationBuilder.kt` (`consumes`, `produces`), `Operation.kt` (`MediaType`)
- `OperationMetadataTest` — `media types of the body and of the responses are the ones stated`
- **Not done**: per-part `encoding` for `multipart/form-data` (own content type per field). The
  whole body is described under the `multipart/form-data` key as one object schema.

### P2: Header Parameters — **done**
**Delivered**: `header<T>(name, required, description)` on the operation DSL.
```kotlin
route.get<User>("/users/me", {
    header<String>("Authorization", required = true, description = "Bearer token")
    header<String>("X-Request-ID")
}) { service.me() }
```
- `OperationBuilder.kt` (`header`)
- `OperationMetadataTest` — `headers and cookies are described as parameters`

### P3: Cookie Parameters — **done**
**Delivered**: `cookie<T>(name, required, description)`, the same shape as `header`.
- `OperationBuilder.kt` (`cookie`); same test as headers.

### P3: File Upload/Download Support — **done**
**Delivered**: `ByteArray` is described as `{ type: string, format: binary }`, so a binary body or
response is documented as a file. Combined with `consumes`/`produces` it covers upload and download.
- `OpenApiKType.kt` (`kotlin.ByteArray` → `Format.BINARY`), `README.md` type table
- `TypeFormatTest` — a `ByteArray` property is described as a binary string

---

## 4. Documentation & Developer Experience

### P1: Required vs Optional from the Serializer — **done**
**Delivered**: when a type is `@Serializable`, its serializer is the source of truth: properties
are described under their `@SerialName`, `@Transient` and computed getters are left out, a
property with a default is optional, and a custom serializer (`@Serializable(with = ...)`) is
described by what it writes rather than by the fields of the class. `required` is written per
object schema.
- `OpenApiKType.kt` (`declaredProperties`, `serializedAsPrimitive`), `SchemaRegistry.kt` (`required`)
- `SerializedShapeTest`, `LateInitPropertyTest`

### P1: Default Values Documentation
**Current State**: A property with a default is marked optional (see above), but the default
*value* itself is not written into the schema — `SchemaObject` has no `default` field.
**Expected**: Show default values from Kotlin property defaults in schema.

### P1: Field Deprecation Support
**Current State**: `deprecated` works on operations and hand-declared parameters, not on object
properties.
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

### P2: External Documentation Links — **done**
**Delivered**: `externalDocs(url, description)` on the document, on every operation, and on a tag.
- `OpenAPI.kt` (`externalDocs`, `tag`), `OperationBuilder.kt` (`externalDocs`)
- `DocumentMetadataTest` — `the document describes itself`

### P2: Server Configuration — **done**
**Delivered**: `server(url, description, variables)` describes the servers in full; until the
first call the document lists the server derived from `host`, with `http://` in front of a local
address and `https://` in front of everything else. `info { }` describes title, version,
description and the rest.
```kotlin
configure = { api ->
    api.info { title = "Orders"; version = "2.0" }
    api.server("https://api.example.com", "Production")
    api.server("https://staging.example.com", "Staging")
}
```
- `OpenAPI.kt` (`server`, `host`, `info`, `asServerUrl`)
- `DocumentMetadataTest` — host/server tests
- **Not done**: auto-detection from the Ktor connector configuration.

### P2: Better Error Messages
**Current State**: `UnhandledTypeException` names the type it could not describe; everything else
is still a bare reflection error.
**Expected**: Clear messages when types cannot be reflected, duplicate operationIds are declared,
or path parameter names do not match the template.

### P3: IDE Integration Hints — **partly done**
**Current State**: the operation DSL (`OperationBuilder`) and the core carry KDoc with examples;
the route extension functions (`get`/`post`/…) are still bare.

---

## 5. Advanced OpenAPI Features

### P3: Callbacks
**Current State**: `CallbackObject` exists in the spec model but is not integrated.
**Use Case**: Document webhooks and async callbacks.

### P3: Links
**Current State**: `LinkObject` exists in the spec model but is not integrated.
**Use Case**: HATEOAS and operation chaining.

### P3: Webhook Support (OpenAPI 3.1)
**Current State**: Not supported.
**Note**: OpenAPI 3.1+ feature, but commonly requested.

---

## 6. Configuration & Customization

### P2: Schema Customization Hooks
**Expected**: Modify generated schemas before finalization (description/example from annotations,
and the like).

### P3: OpenAPI Extension Support (x-*)
**Expected**: Allow custom vendor extensions
```kotlin
route.get<User>("/users", {
    extension("x-rate-limit", mapOf("limit" to 100, "window" to "1m"))
}) { ... }
```

---

## 7. Testing & Validation

### P1: OpenAPI Spec Validation — **partly done**
**Current State**: every test validates the generated document with `swagger-parser` and asserts
on the parsed model. No built-in validation at runtime.
**Remaining**: validation with helpful messages as part of the library itself, for consumers.

### P2: Contract Testing Support
**Expected**: Generate test fixtures from OpenAPI spec.

### P3: Mock Server Generation
**Expected**: Auto-generate mock responses from examples.

---

## 8. Code Quality & Maintenance

### P1: Comprehensive Documentation — **partly done**
**Current State**: `README.md` documents installation, the DSL on every route shape, the security
and document configuration, and the full type table (formats included). No separate user guide,
migration guide or API reference site.

### P2: Performance Optimization
**Current State**: the schema registry builds each component once per document, but reflection
still runs per registration, and the plugin serializes the whole document on every request to
`/openapi.json`.
**Remaining**:
- Cache the serialized document once routing is done
- Cache reflected type information across registrations

### P2: Better Test Coverage — **done**
**Delivered**: 91 tests across 15 files — every HTTP method (`HttpMethodTest`), nested and
recursive types (`SchemaComponentTest`), generics (`TopLevelTypeTest`, `DescriptionBuilderTest`),
sealed hierarchies (`SealedTypeTest`), serializer shapes (`SerializedShapeTest`,
`LateInitPropertyTest`), formats (`TypeFormatTest`), path templates (`PathTemplateTest`,
`PathParameterTest`), repeated registration (`RepeatedRegistrationTest`), unknown types
(`UnknownTypeTest`), empty documents (`EmptyDocumentTest`), document metadata
(`DocumentMetadataTest`), operation metadata (`OperationMetadataTest`). All run through
`testApplication` and validate the document with `swagger-parser`.

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

1. ~~**Add PUT to Method enum**~~ — done, along with HEAD, OPTIONS and TRACE
2. ~~**Add response descriptions**~~ — done (`response(status, description)`)
3. ~~**Add operationId support**~~ — done
4. ~~**Default status code descriptions**~~ — done (`REASON_PHRASES`)
5. ~~**Better default response code**~~ — done via the responses DSL (`responseOf`, `default`)
6. ~~**Format field for UUID/Date types**~~ — done (`Format`, `BUILT_IN_FORMATS`)
7. ~~**Expose header parameters**~~ — done (`header<T>`, `cookie<T>`)
8. ~~**Server object auto-population**~~ — done (`host` → server URL, `server(...)`)
9. ~~**External docs support**~~ — done (document, operation and tag level)
10. **Deprecated field support** — still open: check `@Deprecated` on object properties

---

## Implementation Priority Recommendations

Phases 1–3 of the original plan (operation metadata, responses, security, tags, formats,
polymorphism, headers, content types) are complete. What remains, roughly in order of value:

### Next: Consumer-facing polish
- Examples DSL (request/response/parameter examples)
- Field-level deprecation and default values in schemas
- Validation constraints (minLength, minimum, pattern, …)
- readOnly/writeOnly properties

### Then: Extensibility
- Custom type resolvers and schema customization hooks
- Vendor extensions (`x-*`)
- Global response headers
- Per-part `multipart/form-data` encoding

### Later: Advanced spec features and performance
- Callbacks, links, webhooks (3.1)
- Cache the serialized document; cache reflection across registrations
- Built-in spec validation with helpful messages

---

## Notes

- All line number references are approximate and may change with updates
- Priority levels are suggestions based on OpenAPI adoption patterns
- Some features may require breaking API changes
- Consider backward compatibility for existing users
