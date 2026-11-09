package mx.cargap.supplier.application

import mx.cargap.supplier.domain.*

/** Coordina la captura sin conocer HTTP, cámara ni almacenamiento. */
class EnrollmentUseCases(private val repository: EnrollmentRepository) {
    suspend fun load() = repository.load()
    suspend fun save(type: PersonType, tradeName: String, revision: Int): Enrollment {
        if (tradeName.length > 200 || (type == PersonType.LEGAL && tradeName.isBlank())) throw AccessException(Failure.INVALID_INPUT)
        return repository.save(type, tradeName, revision)
    }
    suspend fun upload(evidence: Evidence, requirement: DocumentRequirement, revision: Int): Enrollment {
        if (evidence.bytes.isEmpty() || evidence.bytes.size > requirement.maxBytes || evidence.mimeType !in requirement.mimeTypes)
            throw AccessException(Failure.INVALID_INPUT)
        return repository.upload(evidence, requirement.code, revision)
    }
    suspend fun submit(enrollment: Enrollment): Enrollment {
        if (!enrollment.canSubmit) throw AccessException(Failure.INVALID_INPUT)
        return repository.submit(enrollment.revision)
    }
}
