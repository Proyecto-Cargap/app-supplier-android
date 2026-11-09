package mx.cargap.supplier.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import mx.cargap.supplier.application.AccessUseCases
import mx.cargap.supplier.domain.*

enum class Phase { LOADING, LOGIN, ONBOARDING, HOME, BLOCKED }
data class AccessState(val phase: Phase = Phase.LOADING, val configuration: PlatformConfiguration? = null,
                       val access: SessionAccess? = null, val busy: Boolean = false,
                       val message: String? = null, val notice: String? = null)

/** Estado MVVM sin credenciales; las vistas entregan el formulario como acción efímera. */
@HiltViewModel class AccessViewModel @Inject constructor(private val useCases: AccessUseCases, val actor: Actor) : ViewModel() {
    private val mutable = MutableStateFlow(AccessState())
    val state: StateFlow<AccessState> = mutable.asStateFlow()
    private var operation = 0L
    private var expiry: Job? = null
    private var started = false
    fun start() { if (!started) { started = true; retry() } }
    fun retry() {
        if (mutable.value.busy) return
        val version = ++operation
        mutable.update { it.copy(phase = Phase.LOADING, busy = true, access = null, message = null) }
        viewModelScope.launch {
            try {
                val config = mutable.value.configuration ?: useCases.loadConfiguration()
                if (version != operation) return@launch
                mutable.update { it.copy(configuration = config) }
                val restored = useCases.restore()
                if (version != operation) return@launch
                if (restored == null) mutable.update { it.copy(phase = Phase.LOGIN) } else show(restored)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (version == operation) {
                    val failure = (e as? AccessException)?.failure ?: Failure.INVALID_RESPONSE
                    mutable.update { it.copy(phase = if (it.configuration == null || failure.transient) Phase.BLOCKED else Phase.LOGIN, message = text(failure)) }
                }
            } finally { if (version == operation) mutable.update { it.copy(busy = false) } }
        }
    }
    fun resume() {
        if (!started || mutable.value.busy) return
        if (mutable.value.phase == Phase.BLOCKED) { retry(); return }
        if (mutable.value.phase !in listOf(Phase.HOME, Phase.ONBOARDING)) return
        val version = ++operation
        expiry?.cancel()
        mutable.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                val result = useCases.restore()
                if (version != operation) return@launch
                if (result == null) mutable.update { it.copy(phase = Phase.LOGIN, access = null) } else show(result)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                val failure = (e as? AccessException)?.failure ?: Failure.INVALID_RESPONSE
                if (version == operation) mutable.update { it.copy(access = null, phase = if (failure.transient) Phase.BLOCKED else Phase.LOGIN, message = text(failure)) }
            } finally { if (version == operation) mutable.update { it.copy(busy = false) } }
        }
    }
    fun signIn(email: String, password: String) {
        if (mutable.value.busy || mutable.value.configuration == null) return
        val version = ++operation
        mutable.update { it.copy(busy = true, message = null, notice = null) }
        viewModelScope.launch {
            try {
                val result = useCases.signIn(email, password)
                if (version == operation) show(result)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (version == operation) mutable.update { it.copy(message = text((e as? AccessException)?.failure ?: Failure.INVALID_RESPONSE)) }
            } finally { if (version == operation) mutable.update { it.copy(busy = false) } }
        }
    }
    fun signOut() {
        val version = ++operation
        expiry?.cancel()
        mutable.update { it.copy(phase = Phase.LOGIN, access = null, busy = true, message = null, notice = null) }
        viewModelScope.launch {
            val revoked = useCases.signOut()
            if (version == operation) mutable.update { it.copy(busy = false, notice = if (revoked) null else
                "La sesión se cerró en este dispositivo. No se pudo confirmar el cierre en el servidor.") }
        }
    }
    fun invalidateSession() {
        val version = ++operation
        expiry?.cancel()
        mutable.update { it.copy(phase = Phase.LOGIN, access = null, busy = true, notice = null, message = "Inicia sesión nuevamente para continuar.") }
        viewModelScope.launch { useCases.expire(); if (version == operation) mutable.update { it.copy(busy = false) } }
    }
    private fun show(result: SessionAccess) {
        mutable.update { it.copy(phase = if (result.session.requiresAccountCompletion) Phase.ONBOARDING else Phase.HOME, access = result, message = null, notice = if (result.persisted) null else
            "La sesión no se conservará al cerrar la aplicación: el almacenamiento seguro no está disponible.") }
        expiry?.cancel()
        val version = operation
        expiry = viewModelScope.launch {
            delay(((result.session.expiresAt - System.currentTimeMillis() / 1000).coerceIn(0, 315_360_000)) * 1000)
            if (version != operation) return@launch
            useCases.expire()
            if (version == operation) mutable.update { it.copy(phase = Phase.LOGIN, access = null, message = text(Failure.EXPIRED)) }
        }
    }
    private fun text(error: Failure): String = when (error) {
        Failure.INVALID_INPUT -> "Escribe tu correo y contraseña."
        Failure.CREDENTIALS -> "El correo o la contraseña no son correctos."
        Failure.EXPIRED -> "Tu sesión terminó. Inicia sesión nuevamente."
        Failure.FORBIDDEN -> "Esta cuenta no tiene acceso disponible."
        Failure.WRONG_ACTOR -> "Esta cuenta no corresponde a la aplicación de ${actor.label.lowercase()}."
        Failure.ASSOCIATION_MISSING -> "No se pudo confirmar tu vínculo con un cliente o transportista. Contacta al administrador."
        Failure.CONFLICT -> "La información cambió. Actualiza e intenta nuevamente."
        Failure.TOO_LARGE -> "El archivo excede el tamaño permitido."
        Failure.INVALID_RESPONSE -> "No fue posible validar la respuesta del servicio."
        Failure.CONNECTION -> "No fue posible conectar. Revisa tu conexión e intenta nuevamente."
        Failure.UNAVAILABLE -> "El servicio no está disponible en este momento. Intenta nuevamente."
        Failure.STORAGE -> "No fue posible recuperar la sesión guardada. Inicia sesión nuevamente."
        Failure.CANCELLED -> "La operación se canceló."
    }
}
