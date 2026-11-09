package mx.cargap.supplier.presentation

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import mx.cargap.supplier.application.*
import mx.cargap.supplier.domain.*
import org.junit.*
import org.junit.Assert.*

private class PendingRegistration : RegistrationRepository {
    var calls = 0
    var fail = false
    var pending: CompletableDeferred<Unit>? = null
    var received: RegistrationDraft? = null
    override suspend fun register(draft: RegistrationDraft, actor: Actor) {
        calls++; received = draft
        if (fail) throw AccessException(Failure.UNAVAILABLE)
        pending?.await()
    }
}
@OptIn(ExperimentalCoroutinesApi::class)
class RegistrationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }
    private val draft = RegistrationDraft(" Ana ", " Pérez ", " López ", " Anita ", " ANA@EXAMPLE.INVALID ", "+52", "5512345678")
    private fun ready(model: RegistrationViewModel) { model.edit(draft); model.next(); model.next(); model.code("12345"); model.next(); assertEquals(RegistrationStep.CONFIRMATION, model.state.value.step) }
    @Test fun validationNavigationAndResendPreserveDraft() {
        val model = RegistrationViewModel(RegistrationUseCases(PendingRegistration()), Actor.SUPPLIER)
        assertFalse(model.state.value.canAdvance)
        model.edit(draft); model.next(); model.back(); assertEquals(draft, model.state.value.draft)
        model.next(); model.next(); model.code("1234"); assertFalse(model.state.value.canAdvance)
        model.code("54321"); assertTrue(model.state.value.canAdvance)
        model.resend(); assertEquals("", model.state.value.code)
        assertFalse(RegistrationRules.email("a b@example.invalid"))
    }
    @Test fun successNormalizesContactAndCannotRepeat() = runTest(dispatcher) {
        val repo = PendingRegistration()
        val model = RegistrationViewModel(RegistrationUseCases(repo), Actor.SUPPLIER)
        ready(model); model.registerOnce(); model.registerOnce(); runCurrent()
        assertEquals(1, repo.calls); assertTrue(model.state.value.created)
        assertEquals("Anita", repo.received!!.preferredName); assertEquals("Pérez", repo.received!!.lastNamePaternal)
        assertEquals("ana@example.invalid", repo.received!!.email); assertEquals("52", repo.received!!.countryCode)
        model.welcome(); assertEquals(RegistrationStep.WELCOME, model.state.value.step)
        model.back(); assertEquals(RegistrationStep.WELCOME, model.state.value.step)
    }
    @Test fun lateCompletionIsDiscardedAfterExit() = runTest(dispatcher) {
        val pending = CompletableDeferred<Unit>()
        val repo = PendingRegistration().apply { this.pending = pending }
        val model = RegistrationViewModel(RegistrationUseCases(repo), Actor.SUPPLIER)
        ready(model); model.registerOnce(); runCurrent(); assertTrue(model.state.value.busy)
        model.reset(); pending.complete(Unit); runCurrent()
        assertEquals(RegistrationState(), model.state.value); assertEquals(1, repo.calls)
    }
    @Test fun failureDoesNotAdvanceOrAutomaticallyRetry() = runTest(dispatcher) {
        val repo = PendingRegistration().apply { fail = true }
        val model = RegistrationViewModel(RegistrationUseCases(repo), Actor.SUPPLIER)
        ready(model); model.registerOnce(); runCurrent(); model.registerOnce(); model.welcome(); runCurrent()
        assertEquals(1, repo.calls); assertFalse(model.state.value.created); assertNotNull(model.state.value.message)
        assertEquals(RegistrationStep.CONFIRMATION, model.state.value.step)
    }
}
