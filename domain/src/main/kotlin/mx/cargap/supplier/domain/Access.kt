package mx.cargap.supplier.domain

/** Actor permitido. Los permisos operativos siguen siendo autoridad del servidor. */
enum class Actor(val role: String, val label: String) {
    CUSTOMER("100-customer", "Clientes"), SUPPLIER("300-supplier", "Transportistas")
}
/** Proyección pública de sesión, sin credenciales de transporte. */
data class Session(
    val code: String, val userCode: String, val name: String, val email: String,
    val workspaceCode: String, val workspaceName: String, val associationCode: String,
    val associationName: String, val expiresAt: Long,
    val requiresAccountCompletion: Boolean = false, val greetingName: String = name,
)
data class SessionAccess(val session: Session, val persisted: Boolean)
data class PlatformConfiguration(val title: String, val primary: Map<String, String>, val surface: Map<String, String>)
enum class Failure {
    INVALID_INPUT, CREDENTIALS, EXPIRED, FORBIDDEN, WRONG_ACTOR, ASSOCIATION_MISSING,
    INVALID_RESPONSE, CONNECTION, UNAVAILABLE, STORAGE, CANCELLED, CONFLICT, TOO_LARGE;
    val transient: Boolean get() = this == CONNECTION || this == UNAVAILABLE
}
/** Error estable sin contenido remoto ni credenciales. */
class AccessException(val failure: Failure) : Exception(failure.name)
/** Frontera sustituible de acceso: la implementación conserva la credencial de forma privada. */
interface SessionRepository {
    suspend fun signIn(user: String, password: String): SessionAccess
    suspend fun restore(): SessionAccess?
    suspend fun signOut(): Boolean
    suspend fun clear()
}
/** Frontera de identidad visual de la aplicación. */
interface PlatformConfigurationRepository { suspend fun load(): PlatformConfiguration }
