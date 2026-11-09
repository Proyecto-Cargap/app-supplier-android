package mx.cargap.supplier.application

import mx.cargap.supplier.domain.*

/** Reglas del formulario; aceptar el código local no verifica el teléfono. */
object RegistrationRules {
    fun names(d: RegistrationDraft) = d.firstName.isNotBlank() && d.lastNamePaternal.isNotBlank() &&
        listOf(d.firstName, d.lastNamePaternal, d.lastNameMaternal).all { it.length <= 1000 } && d.preferredName.length <= 100
    fun email(value: String) = value.trim().length <= 254 && Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(value.trim())
    fun phone(d: RegistrationDraft) = Regex("^\\+?[1-9][0-9]{0,2}$").matches(d.countryCode.trim()) &&
        Regex("^[0-9]{7,14}$").matches(d.phone) && d.countryCode.trim().removePrefix("+").length + d.phone.length <= 15
    fun code(value: String) = Regex("^[0-9]{5}$").matches(value)
}
class RegistrationUseCases(private val repository: RegistrationRepository) {
    suspend fun register(d: RegistrationDraft, actor: Actor) {
        if (!RegistrationRules.names(d) || !RegistrationRules.email(d.email) || !RegistrationRules.phone(d))
            throw AccessException(Failure.INVALID_INPUT)
        repository.register(d.copy(firstName = d.firstName.trim(), lastNamePaternal = d.lastNamePaternal.trim(),
            lastNameMaternal = d.lastNameMaternal.trim(), preferredName = d.preferredName.trim(),
            email = d.email.trim().lowercase(java.util.Locale.ROOT), countryCode = d.countryCode.trim().removePrefix("+")), actor)
    }
}
