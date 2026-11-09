package mx.cargap.supplier.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mx.cargap.supplier.application.RegistrationRules
import mx.cargap.supplier.application.RegistrationUseCases
import mx.cargap.supplier.domain.*

enum class RegistrationStep { NAMES, EMAIL, PHONE, CONFIRMATION, WELCOME }
data class RegistrationState(val draft: RegistrationDraft = RegistrationDraft(), val code: String = "",
    val step: RegistrationStep = RegistrationStep.NAMES, val busy: Boolean = false, val created: Boolean = false,
    val message: String? = null) {
    val canAdvance: Boolean get() = when(step) {
        RegistrationStep.NAMES -> RegistrationRules.names(draft)
        RegistrationStep.EMAIL -> RegistrationRules.email(draft.email)
        RegistrationStep.PHONE -> RegistrationRules.phone(draft) && RegistrationRules.code(code)
        else -> false
    }
    val displayName: String get() = draft.preferredName.trim().ifBlank { draft.firstName.trim() }
}
/** Un envío por visita al asistente, sin reintentos automáticos ni resultados tardíos. */
@HiltViewModel class RegistrationViewModel @Inject constructor(private val useCases: RegistrationUseCases, val actor: Actor) : ViewModel() {
    private val mutable = MutableStateFlow(RegistrationState())
    val state = mutable.asStateFlow()
    private var generation = 0
    private var attempted = false
    fun reset() { generation++; attempted = false; mutable.value = RegistrationState() }
    fun edit(draft: RegistrationDraft) { if (!attempted) mutable.value = mutable.value.copy(draft = draft) }
    fun code(value: String) { if (!attempted) mutable.value = mutable.value.copy(code = value) }
    fun resend() { code("") }
    fun next() {
        val current = mutable.value
        if (!current.canAdvance) return
        mutable.value = current.copy(step = when(current.step) {
            RegistrationStep.NAMES -> RegistrationStep.EMAIL
            RegistrationStep.EMAIL -> RegistrationStep.PHONE
            RegistrationStep.PHONE -> RegistrationStep.CONFIRMATION
            else -> current.step
        })
    }
    fun back() {
        val current = mutable.value
        mutable.value = current.copy(step = when(current.step) {
            RegistrationStep.EMAIL -> RegistrationStep.NAMES
            RegistrationStep.PHONE -> RegistrationStep.EMAIL
            else -> current.step
        })
    }
    fun welcome() { if (mutable.value.created) mutable.value = mutable.value.copy(step = RegistrationStep.WELCOME) }
    fun registerOnce() {
        if (mutable.value.step != RegistrationStep.CONFIRMATION || attempted) return
        attempted = true
        val version = generation
        val draft = mutable.value.draft
        mutable.value = mutable.value.copy(busy = true, code = "")
        viewModelScope.launch {
            try {
                useCases.register(draft, actor)
                if (version == generation) mutable.value = mutable.value.copy(busy = false, created = true)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (version == generation) mutable.value = mutable.value.copy(busy = false, message =
                    if ((e as? AccessException)?.failure in listOf(Failure.CONFLICT, Failure.FORBIDDEN))
                        "No pudimos registrar este correo. Si ya tienes una cuenta, revisa tu correo de activación o vuelve al inicio de sesión."
                    else "No pudimos confirmar el registro. Revisa tu correo antes de volver a intentarlo.")
            }
        }
    }
}
