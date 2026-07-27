package com.turbomates.openapi.ktor

import com.turbomates.openapi.Type
import com.turbomates.openapi.openApiKType
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.swagger.parser.OpenAPIParser
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.serialization.Serializable

/**
 * A type argument is not always there to be read — a star projection and a raw type leave it
 * unknown. That used to be a `NullPointerException` (B4); it is an empty schema now, which is how
 * OpenAPI says "anything".
 */
class UnknownTypeTest {
    @Test
    fun `a star projected type parameter is described as anything`() {
        val type = typeOf<Generic<*>>().openApiKType.objectType()

        assertIs<Type.Any>(type.property("value"))
        val values = assertIs<Type.Array>(type.property("values"))
        assertIs<Type.Any>(values.type)
    }

    @Test
    fun `a star projected collection describes its items as anything`() {
        val type = typeOf<WithStar>().openApiKType.objectType()

        val anything = assertIs<Type.Array>(type.property("anything"))
        assertIs<Type.Any>(anything.type)
    }

    @Test
    fun `a star projected map says nothing about its values`() {
        val type = typeOf<WithStar>().openApiKType.objectType()

        val map = assertIs<Type.Map>(type.property("map"))
        assertIs<Type.Any>(map.valueType)
    }

    @Test
    fun `a collection whose element type is not readable describes its items as anything`() {
        // A collection that is neither a list nor a set carries its argument in a supertype the
        // lookup does not know — there is nothing to read the element type from.
        val type = typeOf<WithCollections>().openApiKType.objectType()

        val bag = assertIs<Type.Array>(type.property("bag"))
        assertIs<Type.Any>(bag.type)
    }

    @Test
    fun `a collection that carries its element type in a supertype is still described`() {
        val type = typeOf<WithCollections>().openApiKType.objectType()

        val names = assertIs<Type.Array>(type.property("names"))
        assertIs<Type.String>(names.type)
    }

    @Test
    fun `a document describing unknown types stays valid`() = testApplication {
        install(OpenAPI)
        routing {
            get<WithStar>("/star") { error("not called") }
            get<Generic<String>>("/generic") { error("not called") }
        }

        val response = client.get("/openapi.json").bodyAsText()

        assertEquals(emptyList(), OpenAPIParser().readContents(response, null, null).messages)
        // The same class parameterized differently is described twice, under names of its own.
        assertContains(response, "\"GenericString\":")
        assertContains(response, "\"Generic\":")
    }

    private fun Type.Object.property(name: String): Type {
        return properties.single { it.name == name }.type
    }

    @Serializable
    data class Generic<T : Any>(val value: T, val values: List<T>)

    data class WithStar(val anything: List<*>, val map: Map<*, *>, val generic: Generic<*>)

    data class WithCollections(val bag: Bag, val names: Names)

    abstract class Bag : Collection<String>

    class Names : ArrayList<String>()
}
