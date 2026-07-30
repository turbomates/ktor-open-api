package com.turbomates.openapi

import com.turbomates.openapi.spec.DiscriminatorObject
import com.turbomates.openapi.spec.SchemaObject
import kotlin.reflect.KType
import kotlin.reflect.full.withNullability
import kotlin.reflect.jvm.jvmErasure

/**
 * The schemas of the document and the names they are known by.
 *
 * Every type described by reflection is written to `components.schemas` once and referenced from
 * everywhere it is used, so a type shared by several endpoints appears in the document a single
 * time and a type that refers to itself can be described at all.
 */
internal class SchemaRegistry {
    /** Component name every described type is registered under, keyed by the type itself. */
    private val componentNames: MutableMap<KType, String> = mutableMapOf()
    private val components: MutableMap<String, SchemaObject> = mutableMapOf()

    /** The schemas built so far, ready to be published as `components.schemas`. */
    val schemas: Map<String, SchemaObject>
        get() = components.toMap()

    /**
     * Describes [model] in `components.schemas` under [name].
     *
     * Every use of the same type is a reference to this schema afterwards, [name] included — a
     * model registered by hand keeps the name it was given instead of the one derived from the type.
     */
    fun addModel(name: String, model: Type.Object) {
        model.returnType?.let { componentNames[it.withNullability(false)] = name }
        components[name] = model.objectSchemaObject(nullable = false)
    }

    fun schemaObject(type: Type): SchemaObject = type.toSchemaObject()

    private fun Type.toSchemaObject(): SchemaObject {
        return when (this) {
            is Type.String -> SchemaObject(
                type = "string",
                format = this.format,
                enum = this.values,
                example = this.example,
                nullable = this.isNullable
            )
            is Type.Array -> SchemaObject(
                type = "array",
                items = this.type.toSchemaObject(),
                enum = this.values,
                nullable = this.isNullable
            )
            // A type known by reflection is described once in `components` and referenced from
            // everywhere it is used; one made up on the spot — by a resolver, or by hand — has
            // nothing to be keyed by, so it stays where it is.
            is Type.Object ->
                if (this.returnType != null) {
                    componentSchemaObject(this.returnType, this.isNullable) { objectSchemaObject(nullable = false) }
                } else {
                    objectSchemaObject(nullable = this.isNullable)
                }

            is Type.OneOf ->
                if (this.returnType != null) {
                    componentSchemaObject(this.returnType, this.isNullable) { oneOfSchemaObject() }
                } else {
                    oneOfSchemaObject()
                }

            is Type.Map -> SchemaObject(
                type = OBJECT_TYPE,
                additionalProperties = this.valueType.toSchemaObject(),
                nullable = this.isNullable
            )

            is Type.Ref -> referenceSchemaObject(componentName(this.returnType), this.isNullable)
            is Type.Boolean -> SchemaObject(type = "boolean", nullable = this.isNullable)
            is Type.Number -> SchemaObject(type = "number", format = this.format, nullable = this.isNullable)
            is Type.Integer -> SchemaObject(type = "integer", format = this.format, nullable = this.isNullable)
            // An empty schema is how OpenAPI describes a value it knows nothing about.
            is Type.Any -> SchemaObject(nullable = this.isNullable)
        }
    }

    private fun Type.Object.objectSchemaObject(nullable: Boolean?): SchemaObject {
        return SchemaObject(
            type = OBJECT_TYPE,
            properties = properties.associate { it.name to it.type.toSchemaObject() },
            required = properties.filter { it.isRequired }.map { it.name }.takeIf { it.isNotEmpty() },
            example = example,
            nullable = nullable
        )
    }

    /**
     * A schema of one of the [Type.OneOf.options], told apart by the discriminator when there is
     * one.
     *
     * The mapping is written out even though the values are serial names: a generator has no way
     * of guessing which schema a value stands for, and the names of the components are ours.
     */
    private fun Type.OneOf.oneOfSchemaObject(): SchemaObject {
        val optionSchemas = options.mapValues { it.value.toSchemaObject() }
        return SchemaObject(
            oneOf = optionSchemas.values.toList(),
            discriminator = discriminator?.let { property ->
                DiscriminatorObject(
                    property,
                    optionSchemas.mapNotNull { (value, schema) -> schema.`$ref`?.let { value to it } }.toMap()
                )
            }
        )
    }

    /**
     * Registers [schema] in `components.schemas` under the name of [type] and returns a reference
     * to it.
     *
     * The schema itself is never nullable: it describes the type, while being allowed to be `null`
     * is a property of the place the type is used at, and the same type is used in both ways. The
     * name is taken before [schema] is built, so that a type referring to itself finds the name of
     * the schema it is part of instead of describing it over again.
     */
    private fun componentSchemaObject(type: KType, nullable: Boolean, schema: () -> SchemaObject): SchemaObject {
        val name = componentName(type)
        if (!components.containsKey(name)) {
            components[name] = schema()
        }
        return referenceSchemaObject(name, nullable)
    }

    /**
     * A reference to a component schema, made nullable where the use site asks for it.
     *
     * A `$ref` ignores everything next to it in OpenAPI 3.0, so nullability cannot be stated
     * alongside — it has to wrap the reference instead.
     */
    private fun referenceSchemaObject(name: String, nullable: Boolean): SchemaObject {
        val reference = SchemaObject(`$ref` = "$COMPONENT_SCHEMA_PATH$name")
        return if (nullable) SchemaObject(nullable = true, allOf = listOf(reference)) else reference
    }

    /**
     * Component name of [type], assigned once and kept.
     *
     * The name has to be unique within the document and may only contain letters, digits and
     * `.`, `-`, `_`, so it is built from the name of the class and of its type arguments, and a
     * counter is added when two different types happen to arrive at the same name.
     */
    private fun componentName(type: KType): String {
        val key = type.withNullability(false)
        componentNames[key]?.let { return it }
        val base = key.componentBaseName()
        var name = base
        var attempt = 1
        while (componentNames.containsValue(name)) {
            attempt++
            name = "$base$attempt"
        }
        componentNames[key] = name
        return name
    }

    private fun KType.componentBaseName(): String {
        val arguments = arguments.mapNotNull { it.type?.jvmErasure?.simpleName }.joinToString("")
        return FORBIDDEN_IN_COMPONENT_NAME.replace(jvmErasure.simpleName.orEmpty() + arguments, "")
            .ifEmpty { DEFAULT_COMPONENT_NAME }
    }

    private companion object {
        const val COMPONENT_SCHEMA_PATH = "#/components/schemas/"
        const val OBJECT_TYPE = "object"

        /** A component name may only hold `[a-zA-Z0-9._-]`, and a class name may hold more. */
        val FORBIDDEN_IN_COMPONENT_NAME = Regex("[^A-Za-z0-9._-]")
        const val DEFAULT_COMPONENT_NAME = "Schema"
    }
}
