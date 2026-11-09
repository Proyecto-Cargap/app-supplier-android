package mx.cargap.supplier.application

import java.util.Locale
import mx.cargap.supplier.domain.*

/** Coordina los contratos del dominio sin Android, HTTP ni almacenamiento. */
class AccessUseCases(private val sessions: SessionRepository, private val configuration: PlatformConfigurationRepository) {
    suspend fun loadConfiguration() = configuration.load()
    suspend fun signIn(user: String, password: String): SessionAccess {
        if (user.isBlank() || password.isEmpty()) throw AccessException(Failure.INVALID_INPUT)
        return sessions.signIn(user.trim().lowercase(Locale.ROOT), password)
    }
    suspend fun restore() = sessions.restore()
    suspend fun signOut() = sessions.signOut()
    suspend fun expire() = sessions.clear()
}
