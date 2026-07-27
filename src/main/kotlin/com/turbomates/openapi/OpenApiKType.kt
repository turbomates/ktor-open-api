@file:Suppress("MemberVisibilityCanBePrivate", "unused")
@file:OptIn(ExperimentalSerializationApi::class)

package com.turbomates.openapi

import java.math.BigInteger
import java.util.Locale
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.full.createType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.withNullability
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmErasure
import kotlin.reflect.typeOf
import kotlin.time.Duration
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.serializerOrNull

class OpenApiKType(private val original: KType) {
    private val projectionTypes: Map<String, KType> = buildGenericTypes(original)

    /**
     * Types whose object description is being built right now.
     *
     * A type that refers to itself — directly, through a collection or through another type — would
     * otherwise be described forever. Meeting a type from this set means a cycle, and the only way
     * to describe a cycle is a reference to the schema being built ([Type.Ref]).
     */
    private val building: MutableSet<KType> = mutableSetOf()

    private val KType.primitiveType: Type
        get() {
            return when {
                isSubtypeOf(typeOf<String?>()) -> Type.String(nullable = isMarkedNullable)
                isSubtypeOf(typeOf<Locale?>()) -> Type.String(nullable = isMarkedNullable)
                isSubtypeOf(typeOf<UUID?>()) -> Type.String(nullable = isMarkedNullable, format = Format.UUID)
                isSubtypeOf(typeOf<Boolean?>()) -> Type.Boolean(isMarkedNullable)
                isSubtypeOf(typeOf<Duration?>()) -> Type.String(nullable = isMarkedNullable, format = Format.DURATION)
                isSubtypeOf(typeOf<Number?>()) -> numberType()
                else -> throw UnhandledTypeException(jvmErasure.simpleName ?: toString())
            }
        }

    /**
     * A whole number is `integer` rather than `number`, and both say how wide they are.
     *
     * Every `Number` used to be a `number`, so a code generator had no way of telling an `Int` from
     * a `Double` and produced floating point fields for identifiers and counts alike.
     */
    private fun KType.numberType(): Type {
        return when {
            isSubtypeOf(typeOf<Int?>()) || isSubtypeOf(typeOf<Short?>()) || isSubtypeOf(typeOf<Byte?>()) ->
                Type.Integer(isMarkedNullable, Format.INT32)
            isSubtypeOf(typeOf<Long?>()) -> Type.Integer(isMarkedNullable, Format.INT64)
            isSubtypeOf(typeOf<BigInteger?>()) -> Type.Integer(isMarkedNullable)
            isSubtypeOf(typeOf<Float?>()) -> Type.Number(isMarkedNullable, Format.FLOAT)
            isSubtypeOf(typeOf<Double?>()) -> Type.Number(isMarkedNullable, Format.DOUBLE)
            else -> Type.Number(isMarkedNullable)
        }
    }

    /**
     * Description of a type that carries a value rather than a structure — a date, a byte array,
     * a URI.
     *
     * None of these is a primitive as far as reflection goes, so each used to be taken apart into
     * its own internals: a `LocalDate` was described as an object of `year`, `month` and `day`,
     * which is nothing a serializer ever writes. They are matched by name so that types from
     * libraries that may not be on the classpath — `kotlinx.datetime` — are covered as well.
     */
    private fun KType.builtInType(): Type? {
        val qualifiedName = jvmErasure.qualifiedName ?: return null
        BUILT_IN_FORMATS[qualifiedName]?.let {
            return Type.String(nullable = isMarkedNullable, format = it.takeIf(kotlin.String::isNotEmpty))
        }
        // Everything else from `java.time` is written as a string of some shape, which is still
        // closer to the truth than the fields the class happens to have.
        if (qualifiedName.startsWith(JAVA_TIME_PACKAGE)) {
            return Type.String(nullable = isMarkedNullable)
        }
        return null
    }

    /**
     * Actual types behind the generic parameters of [type].
     *
     * A raw type carries no arguments at all and a star projection carries no type, so neither is
     * mapped: such a parameter has no known type, and the properties using it are described as
     * accepting anything.
     */
    private fun buildGenericTypes(type: KType): Map<String, KType> {
        val types = mutableMapOf<String, KType>()
        type.jvmErasure.typeParameters.forEachIndexed { index, kTypeParameter ->
            type.arguments.getOrNull(index)?.type?.let { types[kTypeParameter.name] = it }
        }

        return types
    }

    fun isSubtypeOf(openApiKType: OpenApiKType): Boolean {
        return original.isSubtypeOf(openApiKType.original)
    }

    /**
     * Description of the type, whatever it is — an object, a collection, an enum, a value class or
     * a primitive.
     *
     * Anything a route may respond with or accept as a body, in other words. Use [objectType] only
     * where the description has to be an object, such as the parameters of an operation.
     */
    fun type(name: String = original.javaType.typeName): Type {
        return buildType(name, original)
    }

    fun objectType(name: String = original.javaType.typeName): Type.Object {
        return buildType(name, original) as? Type.Object
            ?: throw InvalidTypeForOpenApiType(original.javaType.typeName, Type.Object::class.simpleName!!)
    }

    fun getArgumentProjectionType(type: KType): OpenApiKType {
        if (projectionTypes.containsKey(type.toString())) {
            return OpenApiKType(projectionTypes.getValue(type.toString()))
        }
        return OpenApiKType(type)
    }

    override fun equals(other: Any?): Boolean {
        return other is OpenApiKType && other.original == original
    }

    override fun hashCode(): Int {
        return original.hashCode()
    }

    fun buildObjectType(name: String, type: KType): Type.Object {
        val key = type.withNullability(false)
        building.add(key)
        try {
            return Type.Object(name, type.objectProperties(), returnType = type, nullable = type.isMarkedNullable)
        } finally {
            building.remove(key)
        }
    }

    /**
     * Properties of an object as they are serialized.
     *
     * The serializer of the type is the source of truth when there is one: it knows the names the
     * properties are written under (`@SerialName`), which of them are written at all (`@Transient`
     * and computed getters are not), the order they come in, and which ones may be left out (the
     * ones with a default value). Reflection knows none of that — it reports every member property
     * in alphabetical order under its Kotlin name.
     *
     * A type without a serializer — one that is not `@Serializable` — is still described by
     * reflection, since that is all there is to go by.
     */
    private fun KType.objectProperties(): List<Property> {
        val properties = jvmErasure.memberProperties.filterNot { it.isLateinit }
        val descriptor = serialDescriptor()?.takeIf { it.kind is StructureKind }
            ?: return properties.map { property ->
                Property(property.name, buildType(property.returnType))
            }
        val bySerialName = properties.associateBy { it.findAnnotation<SerialName>()?.value ?: it.name }
        return (0 until descriptor.elementsCount).mapNotNull { index ->
            val serialName = descriptor.getElementName(index)
            val property = bySerialName[serialName] ?: return@mapNotNull null
            val described = buildType(property.returnType)
            Property(
                serialName,
                descriptor.getElementDescriptor(index)
                    .takeIf { described is Type.Object }
                    ?.serializedAsPrimitive(property.returnType.isMarkedNullable)
                    ?: described,
                // A property is required when the key has to be there — that is, when the
                // serializer has no default to fall back on. A nullable one is left optional even
                // so: `nullable: true` already says a value may be missing in every sense a client
                // cares about, and demanding an explicit `null` in the body helps nobody.
                isRequired = !descriptor.isElementOptional(index) && !property.returnType.isMarkedNullable
            )
        }
    }

    /**
     * The primitive this descriptor writes, or `null` when it writes something else.
     *
     * A property with a serializer of its own — `@Serializable(with = ...)` — is written as
     * whatever that serializer writes, however many fields the class behind it happens to have.
     * Reflection would take such a type apart, so the serializer is asked first.
     */
    private fun SerialDescriptor.serializedAsPrimitive(nullable: Boolean): Type? {
        return when (kind as? PrimitiveKind ?: return null) {
            PrimitiveKind.STRING, PrimitiveKind.CHAR -> Type.String(nullable = nullable)
            PrimitiveKind.BOOLEAN -> Type.Boolean(nullable)
            PrimitiveKind.BYTE, PrimitiveKind.SHORT, PrimitiveKind.INT -> Type.Integer(nullable, Format.INT32)
            PrimitiveKind.LONG -> Type.Integer(nullable, Format.INT64)
            PrimitiveKind.FLOAT -> Type.Number(nullable, Format.FLOAT)
            PrimitiveKind.DOUBLE -> Type.Number(nullable, Format.DOUBLE)
        }
    }

    /** Descriptor of the serializer of this type, or `null` when the type has none. */
    private fun KType.serialDescriptor(): SerialDescriptor? {
        return runCatching { serializerOrNull(this) }.getOrNull()?.descriptor
    }

    private fun buildType(memberType: KType): Type {
        return buildType(null, memberType)
    }

    private fun buildType(name: String?, type: KType): Type {
        val resolved = type.resolveProjection()
        // An unresolved type parameter (a raw type or a star projection) says nothing about the
        // value, and an empty schema is exactly how OpenAPI spells "anything".
        if (resolved.classifier is KTypeParameter) {
            return Type.Any(resolved.isMarkedNullable)
        }
        val builtIn = resolved.builtInType()
        return when {
            builtIn != null -> builtIn
            resolved.isCollection() || resolved.isArray() -> resolved.arrayType()
            resolved.isMap() -> resolved.mapType()
            resolved.isEnum() -> resolved.enumType()
            resolved.isPrimitive() -> resolved.primitiveType
            resolved.isValue() -> resolved.valueType()
            else -> objectOrReference(name ?: resolved.objectName(), resolved)
        }
    }

    /** The schema of an object already being described is a reference to it, not another copy. */
    private fun objectOrReference(name: String, type: KType): Type {
        val key = type.withNullability(false)
        if (building.contains(key)) {
            return Type.Ref(name, key, nullable = type.isMarkedNullable)
        }
        return buildObjectType(name, type)
    }

    private fun KType.arrayType(): Type {
        val element = elementType()
        val items = if (element == null) Type.Any() else buildType(element)
        return Type.Array(items, nullable = isMarkedNullable)
    }

    /**
     * Type of the elements of a collection or an array, or `null` when it is not known — a star
     * projection (`List<*>`) or a raw type whose supertypes carry no argument either.
     */
    private fun KType.elementType(): KType? {
        if (arguments.isNotEmpty()) {
            return arguments.first().type
        }
        jvmErasure.supertypes
            .firstOrNull { it.isSubtypeOf(typeOf<Set<*>>()) || it.isSubtypeOf(typeOf<List<*>>()) }
            ?.arguments?.firstOrNull()?.type
            ?.let { return it }
        // An array of primitives (`IntArray`) has no type argument to read; the component type of
        // the class it erases to is the same thing.
        return jvmErasure.java.componentType?.kotlin?.createType()
    }

    /**
     * A map is an object whose keys are not known in advance.
     *
     * It used to be described as an object with a single property named after the key *class*, so
     * the spec claimed a `Map<String, Int>` was an object with a field called `String` — a field no
     * response ever has. The value type belongs in `additionalProperties`; the key type is not
     * described at all, since the keys of a JSON object are strings whatever it is.
     */
    private fun KType.mapType(): Type {
        val valueType = arguments.getOrNull(1)?.type
        return Type.Map(valueType?.let { buildType(it) } ?: Type.Any(), nullable = isMarkedNullable)
    }

    private fun KType.enumType(): Type.String {
        val values = jvmErasure.java.enumConstants?.map { it.toString() }.orEmpty()
        return Type.String(values.takeIf { it.isNotEmpty() }, nullable = isMarkedNullable)
    }

    /** A value class is described by what it wraps — that is what ends up in the JSON. */
    private fun KType.valueType(): Type {
        val backingType = jvmErasure.memberProperties.firstOrNull()?.returnType
            ?: return Type.Any(isMarkedNullable)
        return buildType(backingType)
    }

    private fun KType.resolveProjection(): KType {
        return projectionTypes[toString()] ?: this
    }

    private fun KType.objectName(): String {
        return jvmErasure.simpleName ?: toString()
    }

    private fun KType.isPrimitive(): Boolean {
        return isSubtypeOf(typeOf<String?>()) ||
                isSubtypeOf(typeOf<Number?>()) ||
                isSubtypeOf(typeOf<Boolean?>()) ||
                isSubtypeOf(typeOf<UUID?>()) ||
                isSubtypeOf(typeOf<Locale?>()) ||
                isSubtypeOf(typeOf<Duration?>())
    }

    private fun KType.isCollection(): Boolean {
        return isSubtypeOf(typeOf<Collection<*>?>())
    }

    /** An array is not a `Collection` in Kotlin, but it is an array in OpenAPI all the same. */
    private fun KType.isArray(): Boolean {
        return jvmErasure.java.isArray
    }

    private fun KType.isMap(): Boolean {
        return isSubtypeOf(typeOf<Map<*, *>?>())
    }

    private fun KType.isEnum(): Boolean {
        return isSubtypeOf(typeOf<Enum<*>?>())
    }

    private fun KType.isValue(): Boolean {
        return (classifier as? KClass<*>)?.isValue == true
    }

    private companion object {
        const val MAP_NAME = "map"
        const val JAVA_TIME_PACKAGE = "java.time."

        /**
         * Types written as a string, and the format they are written in.
         *
         * Matched by name rather than by class: `kotlinx.datetime` is not a dependency of this
         * library, and a project that uses it still deserves its dates described as dates. An empty
         * format means a string of no particular shape.
         */
        val BUILT_IN_FORMATS = mapOf(
            "java.time.LocalDate" to Format.DATE,
            "java.time.LocalDateTime" to Format.DATE_TIME,
            "java.time.OffsetDateTime" to Format.DATE_TIME,
            "java.time.ZonedDateTime" to Format.DATE_TIME,
            "java.time.Instant" to Format.DATE_TIME,
            "java.time.LocalTime" to Format.TIME,
            "java.time.OffsetTime" to Format.TIME,
            "java.time.Duration" to Format.DURATION,
            "java.util.Date" to Format.DATE_TIME,
            "java.net.URI" to Format.URI,
            "java.net.URL" to Format.URI,
            "kotlin.ByteArray" to Format.BINARY,
            "kotlin.uuid.Uuid" to Format.UUID,
            "kotlin.time.Instant" to Format.DATE_TIME,
            "kotlinx.datetime.Instant" to Format.DATE_TIME,
            "kotlinx.datetime.LocalDate" to Format.DATE,
            "kotlinx.datetime.LocalDateTime" to Format.DATE_TIME,
            "kotlinx.datetime.LocalTime" to Format.TIME
        )
    }
}

val KType.openApiKType: OpenApiKType
    get() = OpenApiKType(this)

inline fun <reified T : Any> KClass<T>.openApiKType(): OpenApiKType {
    return typeOf<T>().openApiKType
}

class UnhandledTypeException(type: String) : Exception("unhandled type $type")
class InvalidTypeForOpenApiType(type: String, openApiType: String) : Exception("Invalid $type to build $openApiType")
