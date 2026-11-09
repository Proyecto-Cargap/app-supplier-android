package mx.cargap.supplier.domain

/** Tipo fiscal; no determina permisos comerciales. */
enum class PersonType(val code: String, val label: String) {
    NATURAL("natural_person", "Persona física"), LEGAL("legal_entity", "Persona moral")
}
enum class EnrollmentStatus(val code: String, val label: String) {
    NOT_STARTED("not_started", "Completa tu cuenta"), DRAFT("draft", "Expediente en preparación"),
    IN_REVIEW("in_review", "Documentos en revisión"), CORRECTION_REQUIRED("correction_required", "Hay documentos por corregir"),
    APPROVED("approved", "Expediente aprobado")
}
enum class DocumentStatus(val code: String, val label: String) {
    UPLOADING("uploading", "Carga pendiente de confirmar"), UPLOADED("uploaded", "Listo para enviar"),
    IN_REVIEW("in_review", "En revisión"), APPROVED("approved", "Validado"), REJECTED("rejected", "Corrección requerida")
}
data class DocumentRequirement(val code: String, val label: String, val step: Int, val mimeTypes: List<String>, val maxBytes: Int, val instruction: String = "")
data class EnrollmentDocument(val requirementCode: String, val operationCode: String, val version: Int,
    val name: String, val state: DocumentStatus, val reason: String)
/** Instantánea acreditada por servidor. La aprobación documental no concede habilitación comercial. */
data class Enrollment(val code: String, val revision: Int, val personType: PersonType?, val state: EnrollmentStatus,
    val address: String, val requirements: List<DocumentRequirement>, val documents: List<EnrollmentDocument>, val tradeName: String = "", val pendingOperations: List<String> = emptyList()) {
    val canSubmit: Boolean get() = state !in listOf(EnrollmentStatus.IN_REVIEW, EnrollmentStatus.APPROVED) &&
        pendingOperations.isEmpty() && requirements.isNotEmpty() && requirements.all { requirement -> documents.any {
            it.requirementCode == requirement.code && it.state in listOf(DocumentStatus.UPLOADED, DocumentStatus.IN_REVIEW, DocumentStatus.APPROVED)
        } }
}
/** Evidencia efímera, sin rutas locales ni credenciales. */
class Evidence(val operationCode: String, val name: String, val mimeType: String, val bytes: ByteArray)
interface EnrollmentRepository {
    suspend fun load(): Enrollment
    suspend fun save(type: PersonType, tradeName: String, revision: Int): Enrollment
    suspend fun upload(evidence: Evidence, requirement: String, revision: Int): Enrollment
    suspend fun submit(revision: Int): Enrollment
}
