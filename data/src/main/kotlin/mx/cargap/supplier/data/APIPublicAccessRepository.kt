package mx.cargap.supplier.data

import kotlinx.serialization.json.*
import mx.cargap.supplier.domain.*

/** Estas operaciones nunca adjuntan la credencial de una sesión anterior. */
class APIPublicAccessRepository(private val gateway: GatewayTransport) : PublicAccessRepository {
    override suspend fun recoverPassword(email: String) {
        envelope(gateway.send(GatewayRequest("POST", listOf("security", "user", "recoverpassword"), 200,
            buildJsonObject { put("user", email) }.toString())), 200, needsBody = false)
    }
}
