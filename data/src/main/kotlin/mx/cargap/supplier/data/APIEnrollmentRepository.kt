package mx.cargap.supplier.data

import kotlinx.serialization.json.*
import mx.cargap.supplier.domain.*
import java.io.ByteArrayOutputStream
import java.util.UUID

/** Transporte autenticado con el único propietario de credenciales. */
class AuthenticatedSessionGateway(private val sessions: APISessionRepository) : GatewayTransport {
    override suspend fun send(request: GatewayRequest) = sessions.sendAuthenticated(request)
}
/** Convierte DTO a modelos de dominio después de comprobar estructura y estados. */
class APIEnrollmentRepository(private val gateway: GatewayTransport) : EnrollmentRepository {
    private fun decode(source: String): Enrollment {
        try {
            val value = envelope(source, 200)
            fun JsonObject.text(key: String): String {
                val field = getValue(key).jsonPrimitive
                require(field.isString)
                return field.content
            }
            fun text(key: String) = value.text(key)
            val state = EnrollmentStatus.entries.first { it.code == text("state") }
            val person = PersonType.entries.firstOrNull { it.code == text("personType") }
            val revision = value.getValue("revision").jsonPrimitive.int
            require(revision >= 0 && (state == EnrollmentStatus.NOT_STARTED || (text("enrollmentCode").isNotBlank() && person != null)))
            val requirements = value.getValue("requirements").jsonArray.map { raw ->
                val item = raw.jsonObject
                DocumentRequirement(item.text("code"), item.text("label"),
                    item.getValue("step").jsonPrimitive.int, item.getValue("mimeTypes").jsonArray.map { it.jsonPrimitive.content },
                    item.getValue("maxBytes").jsonPrimitive.int, item["instruction"]?.jsonPrimitive?.content ?: "").also {
                    require(it.code.isNotEmpty() && it.label.isNotEmpty() && it.step in 2..4 && it.maxBytes in 1..10485760 && it.mimeTypes.isNotEmpty())
                    require(it.mimeTypes.all { mime -> mime in listOf("application/pdf", "image/png", "image/jpeg") })
                }
            }
            val documents = value.getValue("documents").jsonArray.map { raw ->
                val item = raw.jsonObject
                EnrollmentDocument(item.text("requirementCode"), item.text("operationCode"),
                    item.getValue("version").jsonPrimitive.int, item.text("name"),
                    DocumentStatus.entries.first { it.code == item.text("state") }, item.text("reason")).also {
                    UUID.fromString(it.operationCode)
                    require(it.version > 0 && requirements.any { requirement -> requirement.code == it.requirementCode })
                    require(it.state != DocumentStatus.REJECTED || it.reason.isNotBlank())
                }
            }
            require(requirements.size <= 10 && requirements.distinctBy { it.code }.size == requirements.size)
            require(documents.size <= 10 && documents.distinctBy { it.requirementCode }.size == documents.size)
            require(text("address").length <= 1000)
            return Enrollment(text("enrollmentCode"), revision, person, state, text("address"), requirements, documents, value["tradeName"]?.jsonPrimitive?.content ?: "",
                value["pendingUploads"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList())
        } catch (e: Exception) { throw e as? AccessException ?: AccessException(Failure.INVALID_RESPONSE) }
    }
    override suspend fun load() = decode(gateway.send(GatewayRequest("GET", listOf("supplier", "enrollment"), 200)))
    override suspend fun save(type: PersonType, tradeName: String, revision: Int) = decode(gateway.send(
        GatewayRequest("PUT", listOf("supplier", "enrollment"), 200, buildJsonObject {
            put("personType", type.code); put("tradeName", tradeName); put("expectedRevision", revision)
        }.toString())))
    override suspend fun submit(revision: Int) = decode(gateway.send(GatewayRequest("POST", listOf("supplier", "enrollment", "submit"), 200,
        buildJsonObject { put("expectedRevision", revision) }.toString())))
    override suspend fun upload(evidence: Evidence, requirement: String, revision: Int): Enrollment {
        val boundary = "cargap-" + UUID.randomUUID()
        val meta = buildJsonObject { put("requirementCode", requirement); put("expectedRevision", revision); put("operationCode", evidence.operationCode) }
        val ext = when (evidence.mimeType) { "application/pdf" -> "pdf"; "image/png" -> "png"; else -> "jpg" }
        val body = ByteArrayOutputStream().apply {
            write("--$boundary\r\nContent-Disposition: form-data; name=\"input\"\r\nContent-Type: application/json\r\n\r\n$meta\r\n".toByteArray())
            write("--$boundary\r\nContent-Disposition: form-data; name=\"document\"; filename=\"documento.$ext\"\r\nContent-Type: ${evidence.mimeType}\r\n\r\n".toByteArray())
            write(evidence.bytes); write("\r\n--$boundary--\r\n".toByteArray())
        }.toByteArray()
        return decode(gateway.send(GatewayRequest("POST", listOf("supplier", "enrollment", "documents"), 200,
            contentType = "multipart/form-data; boundary=$boundary", binaryBody = body)))
    }
}
