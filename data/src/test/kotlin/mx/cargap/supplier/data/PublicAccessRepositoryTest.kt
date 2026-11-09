package mx.cargap.supplier.data

import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

class PublicAccessRepositoryTest {
    @Test fun recoveryUsesPublicContractWithoutSessionCredential() = runTest {
        var captured: GatewayRequest? = null
        val gateway = object : GatewayTransport {
            override suspend fun send(request: GatewayRequest): String {
                captured = request
                return """{"headerResponse":{"statusCode":200}}"""
            }
        }
        APIPublicAccessRepository(gateway).recoverPassword("persona@example.invalid")
        val request = requireNotNull(captured)
        assertEquals("POST", request.method)
        assertEquals(listOf("security", "user", "recoverpassword"), request.path)
        assertEquals("""{"user":"persona@example.invalid"}""", request.body)
        assertNull(request.authorization)
        assertEquals(200, request.expectedStatus)
    }
}
