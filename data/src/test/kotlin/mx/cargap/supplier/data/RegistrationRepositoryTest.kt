package mx.cargap.supplier.data

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import mx.cargap.supplier.domain.*
import org.junit.Test
import org.junit.Assert.*

class RegistrationRepositoryTest {
    @Test fun registrationPreservesContactFieldsAndNeverSendsCodeOrCredential() = runTest {
        val requests = mutableListOf<GatewayRequest>()
        val gateway = object : GatewayTransport {
            override suspend fun send(request: GatewayRequest): String {
                requests += request
                return """{"headerResponse":{"statusCode":201},"businessResponse":{"userCode":"C1","contactCode":"C1","state":"registered"}}"""
            }
        }
        APIRegistrationRepository(gateway).register(RegistrationDraft("Ana","Pérez","López","Anita","ana@example.invalid","52","5512345678"), Actor.SUPPLIER)
        val request = requests.single()
        assertNull(request.authorization); assertEquals(listOf("security","user","external"), request.path)
        assertEquals(201, request.expectedStatus)
        val body = Json.parseToJsonElement(request.body!!).jsonObject
        assertEquals("ana@example.invalid", body["user"]!!.jsonPrimitive.content)
        assertEquals(Actor.SUPPLIER.role, body["roleCode"]!!.jsonPrimitive.content)
        assertEquals("Anita", body["name"]!!.jsonObject["preferredName"]!!.jsonPrimitive.content)
        assertEquals("López", body["name"]!!.jsonObject["lastNameMaternal"]!!.jsonPrimitive.content)
        assertEquals("52", body["phone"]!!.jsonObject["countryCode"]!!.jsonPrimitive.content)
        assertFalse(body.containsKey("code")); assertFalse(body.containsKey("password")); assertFalse(body.containsKey("verified"))
    }
}
