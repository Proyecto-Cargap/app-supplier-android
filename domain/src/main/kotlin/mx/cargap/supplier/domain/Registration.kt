package mx.cargap.supplier.domain

/** Datos efímeros del alta. El código local nunca se envía al servidor. */
data class RegistrationDraft(
    val firstName: String = "", val lastNamePaternal: String = "", val lastNameMaternal: String = "",
    val preferredName: String = "", val email: String = "", val countryCode: String = "+52", val phone: String = "",
)
/** Alta coordinada por Security; no crea una sesión autenticada. */
interface RegistrationRepository { suspend fun register(draft: RegistrationDraft, actor: Actor) }
