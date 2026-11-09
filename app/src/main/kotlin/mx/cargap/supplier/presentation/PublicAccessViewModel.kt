package mx.cargap.supplier.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mx.cargap.supplier.application.PublicAccessUseCases
import mx.cargap.supplier.domain.*

data class PublicAccessState(val busy: Boolean = false, val message: String? = null)
@HiltViewModel class PublicAccessViewModel @Inject constructor(private val useCases: PublicAccessUseCases) : ViewModel() {
    private val mutable = MutableStateFlow(PublicAccessState())
    val state = mutable.asStateFlow()
    private var generation = 0
    fun reset() { generation++; mutable.value = PublicAccessState() }
    fun recover(email: String) = perform {
        useCases.recover(email)
        PublicAccessState(message = "Si el correo corresponde a una cuenta, recibirás un enlace para restablecer tu contraseña.")
    }
    private fun perform(operation: suspend () -> PublicAccessState) {
        if (mutable.value.busy) return
        val version = generation
        mutable.value = mutable.value.copy(busy = true, message = null)
        viewModelScope.launch {
            try { val value = operation(); if (generation == version) mutable.value = value }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                if (generation == version) mutable.value = PublicAccessState(message = if ((e as? AccessException)?.failure == Failure.INVALID_INPUT)
                    "Escribe un correo válido." else "No pudimos confirmar la solicitud. Intenta nuevamente.")
            }
        }
    }
}
