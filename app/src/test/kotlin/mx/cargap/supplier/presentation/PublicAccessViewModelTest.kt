package mx.cargap.supplier.presentation

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import mx.cargap.supplier.application.PublicAccessUseCases
import mx.cargap.supplier.domain.*
import org.junit.*
import org.junit.Assert.*

private class RecoveryRepository : PublicAccessRepository {
    val emails = mutableListOf<String>()
    var completion: CompletableDeferred<Unit>? = null
    var fail = false
    override suspend fun recoverPassword(email: String) {
        emails += email
        if (fail) throw AccessException(Failure.UNAVAILABLE)
        completion?.await()
    }
}
@OptIn(ExperimentalCoroutinesApi::class)
class PublicAccessViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }
    @Test fun invalidEmailDoesNotSendAndValidEmailIsNormalized() = runTest(dispatcher) {
        val repository = RecoveryRepository()
        val model = PublicAccessViewModel(PublicAccessUseCases(repository))
        model.recover("invalid"); runCurrent()
        assertTrue(repository.emails.isEmpty())
        assertEquals("Escribe un correo válido.", model.state.value.message)
        model.recover(" PERSONA@EXAMPLE.INVALID "); runCurrent()
        assertEquals(listOf("persona@example.invalid"), repository.emails)
        assertTrue(model.state.value.message!!.startsWith("Si el correo"))
    }
    @Test fun failureDoesNotReportSuccessfulDelivery() = runTest(dispatcher) {
        val repository = RecoveryRepository().apply { fail = true }
        val model = PublicAccessViewModel(PublicAccessUseCases(repository))
        model.recover("persona@example.invalid"); runCurrent()
        assertTrue(model.state.value.message!!.startsWith("No pudimos"))
        assertFalse(model.state.value.busy)
    }
    @Test fun leavingDiscardsLateCompletionAndDuplicateSubmission() = runTest(dispatcher) {
        val completion = CompletableDeferred<Unit>()
        val repository = RecoveryRepository().apply { this.completion = completion }
        val model = PublicAccessViewModel(PublicAccessUseCases(repository))
        model.recover("persona@example.invalid"); runCurrent()
        model.recover("persona@example.invalid"); runCurrent()
        assertEquals(1, repository.emails.size)
        model.reset(); completion.complete(Unit); runCurrent()
        assertNull(model.state.value.message)
        assertFalse(model.state.value.busy)
    }
}
