package mx.cargap.supplier.data

import kotlinx.serialization.json.*
import mx.cargap.supplier.domain.*

/** Petición pública sin credenciales. Directory recibe el contacto a través de Security. */
class APIRegistrationRepository(private val gateway: GatewayTransport) : RegistrationRepository {
    override suspend fun register(draft: RegistrationDraft, actor: Actor) {
        val body = buildJsonObject {
            put("user", draft.email); put("roleCode", actor.role); put("workspaceCode", "allWorkspaces")
            putJsonObject("name") {
                put("firstName", draft.firstName); put("lastNamePaternal", draft.lastNamePaternal)
                put("lastNameMaternal", draft.lastNameMaternal); put("preferredName", draft.preferredName)
            }
            putJsonObject("phone") { put("countryCode", draft.countryCode); put("number", draft.phone) }
        }
        val result = envelope(gateway.send(GatewayRequest("POST", listOf("security", "user", "external"), 201, body.toString())), 201)
        if (result.string("userCode").isBlank() || result.string("userCode") != result.string("contactCode") || result.string("state") != "registered") invalid()
    }
}
