@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package com.turbomates.openapi

import java.util.Locale
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmErasure
import kotlin.reflect.typeOf
import kotlin.time.Duration

class OpenApiKType(private val original: KType) {
    private val projectionTypes: Map<String, KType> = buildGenericTypes(original)
    private val KType.primitiveType: Type
        get() {
            return when {
                isSubtypeOf(typeOf<String?>()) -> Type.String(nullable = isMarkedNullable)
                isSubtypeOf(typeOf<Locale?>()) -> Type.String(nullable = isMarkedNullable)
                isSubtypeOf(typeOf<UUID?>()) -> Type.String(nullable = isMarkedNullable)
                isSubtypeOf(typeOf<Number?>()) -> Type.Number(isMarkedNullable)
                isSubtypeOf(typeOf<Boolean?>()) -> Type.Boolean(isMarkedNullable)
                isSubtypeOf(typeOf<Duration?>()) -> Type.String(nullable = isMarkedNullable)
                else -> throw UnhandledTypeException(jvmErasure.simpleName!!)
            }
        }

    private fun buildGenericTypes(type: KType): Map<String, KType> {
        val types = mutableMapOf<String, KType>()
        type.jvmErasure.typeParameters.forEachIndexed { index, kTypeParameter ->
            types[kTypeParameter.name] = type.arguments[index].type!!
        }

        return types
    }

    fun isSubtypeOf(openApiKType: OpenApiKType): Boolean {
        return original.isSubtypeOf(openApiKType.original)
    }

    fun objectType(name: String = original.javaType.typeName): Type.Object {
        if (original.isCollection() || original.isPrimitive()) {
            throw InvalidTypeForOpenApiType(original.javaType.typeName, Type.Object::class.simpleName!!)
        }
        return buildType(name, original) as Type.Object
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

    private fun buildType(name: String, type: KType): Type {
        if (type.isCollection() || type.isPrimitive() || type.isEnum()) {
            return buildType(type)
        }

        val kclass = type.classifier as? KClass<*>
        if (kclass != null && kclass.isValue) {
            return buildType(type.jvmErasure.memberProperties.first().returnType)
        }
        return buildObjectType(name, type)
    }

    fun buildObjectType(name: String, type: KType): Type.Object {
        val descriptions = mutableListOf<Property>()
        type.jvmErasure.memberProperties.forEach { property ->
            val memberType = property.returnType
            // ToDo think about parametrization of this option
            if (!property.isLateinit && type != memberType) {
                descriptions.add(Property(property.name, buildType(memberType)))
            }
        }
        return Type.Object(name, descriptions, returnType = type, nullable = type.isMarkedNullable)
    }

    private fun buildType(memberType: KType): Type {
        return when {
            memberType.isCollection() -> {
                var collectionType = if (memberType.arguments.isEmpty()) {
                    memberType.jvmErasure.supertypes.first {
                        it.isSubtypeOf(typeOf<Set<*>>()) || it.isSubtypeOf(typeOf<List<*>>())
                    }.arguments.first().type!!
                } else {
                    memberType.arguments.first().type!!
                }
                if (projectionTypes.containsKey(collectionType.toString())) {
                    collectionType = projectionTypes.getValue(collectionType.toString())
                }
                when {
                    collectionType.isPrimitive() -> Type.Array(collectionType.primitiveType, nullable = memberType.isMarkedNullable)
                    collectionType.isEnum() -> Type.Array(buildType(collectionType), nullable = memberType.isMarkedNullable)
                    else -> Type.Array(
                        buildType(collectionType.jvmErasure.simpleName!!, collectionType),
                        nullable = memberType.isMarkedNullable
                    )
                }
            }

            memberType.isMap() -> {
                val argType = memberType.arguments[0].type!!
                val firstType = projectionTypes.getOrDefault(argType.toString(), argType)
                val argSecondType = memberType.arguments[1].type!!
                val secondType = projectionTypes.getOrDefault(argSecondType.toString(), argSecondType)
                Type.Object(
                    "map",
                    properties = listOf(
                        Property(
                            firstType.jvmErasure.simpleName!!,
                            buildType(secondType)
                        )
                    ),
                    nullable = memberType.isMarkedNullable
                )
            }

            memberType.isEnum() -> {
                val values = memberType.jvmErasure.java.enumConstants
                Type.String(values.map { it.toString() }, nullable = memberType.isMarkedNullable)
            }

            memberType.isPrimitive() ->
                memberType.primitiveType

            else -> {
                val projectionType = projectionTypes.getOrDefault(memberType.toString(), memberType)
                if (projectionType != memberType) {
                    buildType(projectionType.jvmErasure.simpleName!!, projectionType)
                } else buildObjectType(memberType.jvmErasure.simpleName!!, memberType)
            }
        }
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
        return isSubtypeOf(typeOf<Map<*, *>>())
    }

    private fun KType.isEnum(): Boolean {
        return this.javaClass.isEnum || isSubtypeOf(typeOf<Enum<*>?>())
    }
}

val KType.openApiKType: OpenApiKType
    get() = OpenApiKType(this)

inline fun <reified T : Any> KClass<T>.openApiKType(): OpenApiKType {
    return typeOf<T>().openApiKType
}

class UnhandledTypeException(type: String) : Exception("unhandled type $type")
class InvalidTypeForOpenApiType(type: String, openApiType: String) : Exception("Invalid $type to build $openApiType")
