package mx.cargap.supplier.data

import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import mx.cargap.supplier.domain.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Contrato integrado opcional con cuentas sintéticas y destino TEST fijo. */
class LiveTESTContractTest {
    private class LiveStore(var bytes: String?) : SecureSessionStore {
        override suspend fun read() = bytes
        override suspend fun write(data: String) { bytes = data }
        override suspend fun remove() { bytes = null }
    }
    @Test fun persistedSessionAndPrivateEnrollmentAgainstTEST() = runBlocking {
        val file = System.getenv("CARGAP_LIVE_SESSION_FILE")
        val image = System.getenv("CARGAP_LIVE_IMAGE_FILE")
        assumeTrue("Requiere fixture privado de aceptación TEST", file != null && image != null)
        val source = File(file!!).readText()
        val original = SessionDTO.fromStored(source)
        val store = LiveStore(source)
        val sessions = APISessionRepository(OkHttpGateway("https://api.test.cargap.mx"), store, Actor.SUPPLIER)
        val restored = sessions.restore()
        assertTrue(restored != null && restored.session.requiresAccountCompletion)
        assertTrue(SessionDTO.fromStored(store.read()!!).authorization == original.authorization)
        sessions.restore()
        assertTrue(SessionDTO.fromStored(store.read()!!).authorization != original.authorization)
        val enrollments = APIEnrollmentRepository(AuthenticatedSessionGateway(sessions))
        val initial = enrollments.load()
        val draft = enrollments.save(PersonType.NATURAL, "Aceptación nativa TEST", initial.revision)
        assertEquals(EnrollmentStatus.DRAFT, draft.state)
        assertEquals(6, draft.requirements.size)
        val evidence = Evidence(UUID.randomUUID().toString(), "prueba.png", "image/png", File(image!!).readBytes())
        val uploaded = enrollments.upload(evidence, draft.requirements.first { "image/png" in it.mimeTypes }.code, draft.revision)
        assertEquals(1, uploaded.documents.size)
        assertEquals(DocumentStatus.UPLOADED, uploaded.documents.first().state)
        assertTrue(sessions.signOut())
        assertNull(store.read())
    }
}
