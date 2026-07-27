@file:Suppress("MemberVisibilityCanBePrivate", "unused")
@file:OptIn(ExperimentalSerializationApi::class)

package com.turbomates.openapi

import java.util.Locale
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
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
                isSubtypeOf(typeOf<UUID?>()) -> Type.String(nullable = isMarkedNullable)
                isSubtypeOf(typeOf<Number?>()) -> Type.Number(isMarkedNullable)
                isSubtypeOf(typeOf<Boolean?>()) -> Type.Boolean(isMarkedNullable)
                isSubtypeOf(typeOf<Duration?>()) -> Type.String(nullable = isMarkedNullable)
                else -> throw UnhandledTypeException(jvmErasure.simpleName ?: toString())
            }
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
            Property(
                serialName,
                buildType(property.returnType),
                // A property is required when the key has to be there — that is, when the
                // serializer has no default to fall back on. A nullable one is left optional even
                // so: `nullable: true` already says a value may be missing in every sense a client
                // cares about, and demanding an explicit `null` in the body helps nobody.
                isRequired = !descriptor.isElementOptional(index) && !property.returnType.isMarkedNullable
            )
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
        return when {
            resolved.isCollection() -> resolved.arrayType()
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
     * Type of the elements of a collection, or `null` when it is not known — a star projection
     * (`List<*>`) or a raw type whose supertypes carry no argument either.
     */
    private fun KType.elementType(): KType? {
        if (arguments.isNotEmpty()) {
            return arguments.first().type
        }
        return jvmErasure.supertypes
            .firstOrNull { it.isSubtypeOf(typeOf<Set<*>>()) || it.isSubtypeOf(typeOf<List<*>>()) }
            ?.arguments?.firstOrNull()?.type
    }

    // ToDo a map is an object with `additionalProperties`, not an object with a property named
    //  after the key type (C3 of the audit); the shape is kept as it was, it just no longer fails
    //  on a map whose arguments are not known.
    private fun KType.mapType(): Type {
        val keyName = arguments.getOrNull(0)?.type?.resolveProjection()?.jvmErasure?.simpleName
        val valueType = arguments.getOrNull(1)?.type
        val properties = keyName?.let { name ->
            listOf(Property(name, valueType?.let { buildType(it) } ?: Type.Any()))
        }.orEmpty()
        return Type.Object(MAP_NAME, properties, nullable = isMarkedNullable)
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
                isSubtypeOf(typeOf<Duration?>())
    }

    private fun KType.isCollection(): Boolean {
        return isSubtypeOf(typeOf<Collection<*>?>())
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
    }
}

val KType.openApiKType: OpenApiKType
    get() = OpenApiKType(this)

inline fun <reified T : Any> KClass<T>.openApiKType(): OpenApiKType {
    return typeOf<T>().openApiKType
}

class UnhandledTypeException(type: String) : Exception("unhandled type $type")
class InvalidTypeForOpenApiType(type: String, openApiType: String) : Exception("Invalid $type to build $openApiType")
