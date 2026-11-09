package mx.cargap.supplier.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mx.cargap.supplier.application.EnrollmentUseCases
import mx.cargap.supplier.domain.*

data class EnrollmentUiState(val enrollment: Enrollment? = null, val type: PersonType? = null,
    val tradeName: String = "", val address: String = "", val step: Int = 1, val busy: Boolean = false,
    val message: String? = null, val requiresSignIn: Boolean = false, val canRetryUpload: Boolean = false,
    val submitted: Boolean = false, val showingStatus: Boolean = false) {
    val canSave get() = !busy && type != null && (type != PersonType.LEGAL || tradeName.isNotBlank())
    val canContinue get() = !busy && enrollment != null && enrollment.pendingOperations.isEmpty() &&
        enrollment.requirements.any { it.step == step } && enrollment.requirements.filter { it.step == step }.all { requirement ->
            enrollment.documents.any { it.requirementCode == requirement.code && it.state in listOf(DocumentStatus.UPLOADED, DocumentStatus.IN_REVIEW, DocumentStatus.APPROVED) }
        }
}
/** Mantiene únicamente datos de presentación y reconcilia las cargas antes de repetirlas. */
@HiltViewModel class EnrollmentViewModel @Inject constructor(private val useCases: EnrollmentUseCases) : ViewModel() {
    private val mutable = MutableStateFlow(EnrollmentUiState())
    val state = mutable.asStateFlow()
    private var pendingUpload: Pair<Evidence, DocumentRequirement>? = null
    private var generation = 0L
    fun reset() { generation++; pendingUpload = null; mutable.value = EnrollmentUiState() }
    fun select(type: PersonType) { if (mutable.value.enrollment?.personType == null) mutable.value = mutable.value.copy(type = type) }
    fun tradeName(value: String) { if (mutable.value.enrollment?.personType == null) mutable.value = mutable.value.copy(tradeName = value) }
    fun address(value: String) { mutable.value = mutable.value.copy(address = value) }
    fun step(value: Int) { if (!mutable.value.busy) mutable.value = mutable.value.copy(step = value.coerceIn(1, 4)) }
    fun status(value: Boolean) { mutable.value = mutable.value.copy(showingStatus = value, submitted = false) }
    fun load() = perform(restore = true) { useCases.load() }
    fun save() {
        val value = mutable.value; val type = value.type ?: return
        if (!value.canSave) return
        if (value.enrollment?.personType != null) { step(2); return }
        perform(advance = true) { useCases.save(type, value.tradeName.trim(), value.enrollment?.revision ?: 0) }
    }
    fun upload(evidence: Evidence, requirement: DocumentRequirement) {
        val value = mutable.value
        if (value.busy) return
        val current = value.enrollment ?: return
        if (current.pendingOperations.isNotEmpty() || current.documents.any { it.requirementCode == requirement.code && it.state == DocumentStatus.APPROVED }) return
        pendingUpload = evidence to requirement
        perform(uploading = true) { useCases.upload(evidence, requirement, current.revision) }
    }
    fun retryUpload() {
        val pending = pendingUpload ?: return
        perform(uploading = true, restore = true) {
            val fresh = useCases.load()
            when {
                fresh.documents.any { it.operationCode == pending.first.operationCode } -> fresh
                fresh.pendingOperations.isNotEmpty() -> throw AccessException(Failure.CONFLICT)
                else -> useCases.upload(pending.first, pending.second, fresh.revision)
            }
        }
    }
    fun submit() { val current = mutable.value.enrollment ?: return; perform(submitting = true) { useCases.submit(current) } }
    fun acquisitionFailed() { mutable.value = mutable.value.copy(message = "No pudimos abrir el archivo o la cámara. Revisa los permisos, el formato y el límite de 10 MiB.") }
    private fun perform(advance: Boolean = false, uploading: Boolean = false, submitting: Boolean = false, restore: Boolean = false, operation: suspend () -> Enrollment) {
        if (mutable.value.busy) return
        val version = generation
        mutable.value = mutable.value.copy(busy = true, message = null)
        viewModelScope.launch {
            try {
                val value = operation()
                if (version != generation) return@launch
                if (uploading || value.documents.any { it.operationCode == pendingUpload?.first?.operationCode }) pendingUpload = null
                val step = if (advance) 2 else if (restore && value.personType != null) (2..4).firstOrNull { number ->
                    value.requirements.filter { it.step == number }.any { requirement ->
                        value.documents.none { it.requirementCode == requirement.code && it.state in listOf(DocumentStatus.UPLOADED, DocumentStatus.IN_REVIEW, DocumentStatus.APPROVED) }
                    }
                } ?: 4 else mutable.value.step
                mutable.value = mutable.value.copy(enrollment = value, type = value.personType ?: mutable.value.type,
                    tradeName = value.tradeName, address = value.address, step = step,
                    submitted = submitting && value.state == EnrollmentStatus.IN_REVIEW,
                    canRetryUpload = pendingUpload != null)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (version == generation) {
                    val failure = (e as? AccessException)?.failure
                    val expired = failure in listOf(Failure.EXPIRED, Failure.FORBIDDEN)
                    if (expired) pendingUpload = null
                    mutable.value = mutable.value.copy(canRetryUpload = pendingUpload != null, requiresSignIn = expired,
                        enrollment = if (expired) null else mutable.value.enrollment,
                        message = when (failure) {
                            Failure.CONFLICT -> "El expediente cambió o tiene una carga pendiente. Actualiza su estado antes de continuar."
                            Failure.EXPIRED, Failure.FORBIDDEN -> "Tu acceso cambió. Vuelve a iniciar sesión."
                            Failure.INVALID_INPUT, Failure.TOO_LARGE -> "Revisa los datos, el formato del archivo y que no exceda 10 MiB."
                            else -> "No pudimos confirmar la operación. Actualiza el estado antes de volver a intentarlo."
                        })
                }
            } finally { if (version == generation) mutable.value = mutable.value.copy(busy = false) }
        }
    }
}
