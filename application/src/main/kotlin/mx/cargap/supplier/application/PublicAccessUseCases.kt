package mx.cargap.supplier.application

import mx.cargap.supplier.domain.*

class PublicAccessUseCases(private val repository: PublicAccessRepository) {
    suspend fun recover(email: String) {
        val value = email.trim().lowercase()
        if (value.length > 254 || !Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(value)) throw AccessException(Failure.INVALID_INPUT)
        repository.recoverPassword(value)
    }
}
