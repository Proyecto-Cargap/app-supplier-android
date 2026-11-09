package mx.cargap.supplier.domain

/** Recuperación pública del acceso mediante el contrato vigente del servidor. */
interface PublicAccessRepository {
    suspend fun recoverPassword(email: String)
}
