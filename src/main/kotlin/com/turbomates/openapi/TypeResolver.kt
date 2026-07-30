@file:Suppress("unused")

package com.turbomates.openapi

import kotlin.reflect.KType
import kotlin.reflect.full.withNullability

/**
 * Describes a Kotlin type the way the API documents it, rather than the way reflection reads it.
 *
 * Returning `null` leaves the type to the next resolver, and to reflection when none of them
 * answers — a resolver describes the types it knows and says nothing about the rest.
 *
 * ```
 * typeResolver { kType ->
 *     when (kType.classifier) {
 *         Money::class -> Type.String(format = "money", nullable = kType.isMarkedNullable)
 *         else -> null
 *     }
 * }
 * ```
 *
 * The type comes with its nullability, and the description is used as it is given, so a resolver
 * that means to follow the use site reads `isMarkedNullable` off the type it is handed.
 */
fun interface TypeResolver {
    fun resolve(kType: KType): Type?
}

/**
 * The resolvers a document describes its types with, consulted in the order they were added.
 *
 * The first one to answer wins, so a resolver added early describes a type a later one would have
 * described differently. Every type meets them — the body of a request, a response, a property
 * nested deep inside one, an element of a collection — and the description it comes back with is
 * used instead of anything reflection would have said.
 */
class TypeResolvers {
    private val resolvers: MutableList<TypeResolver> = mutableListOf()

    val isEmpty: Boolean
        get() = resolvers.isEmpty()

    fun add(resolver: TypeResolver) {
        resolvers.add(resolver)
    }

    /**
     * Describes [kType] as [type], whichever nullability it is used with.
     *
     * A type is one type whether the property holding it may be `null` or not, so `Money` and
     * `Money?` are the same entry — the description is the one given either way.
     */
    fun add(kType: KType, type: Type) {
        val key = kType.withNullability(false)
        add { candidate -> type.takeIf { candidate.withNullability(false) == key } }
    }

    /** Description of [kType] by the first resolver that has one, or `null` when none has. */
    fun resolve(kType: KType): Type? {
        return resolvers.firstNotNullOfOrNull { it.resolve(kType) }
    }
}
