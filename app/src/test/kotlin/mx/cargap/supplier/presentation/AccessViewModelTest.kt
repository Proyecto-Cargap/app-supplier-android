package mx.cargap.supplier.presentation

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import mx.cargap.supplier.application.AccessUseCases
import mx.cargap.supplier.domain.*
import org.junit.*
import org.junit.Assert.*

private class Sessions : SessionRepository {
    var signed = false; var capturedUser = ""; var capturedPassword = ""; var networkFailure = false
    var result = SessionAccess(Session("S", "U", "Ana", "persona@example.invalid", "W", "Pruebas", "C", "Empresa", 4_000_000_000), true)
    override suspend fun signIn(user: String, password: String): SessionAccess { capturedUser = user; capturedPassword = password; signed = true; return result }
    override suspend fun restore(): SessionAccess? {
        if (networkFailure) throw AccessException(Failure.CONNECTION)
        return if (signed) result else null
    }
    override suspend fun signOut(): Boolean { signed = false; return true }
    override suspend fun clear() { signed = false }
}
private class Configuration : PlatformConfigurationRepository {
    override suspend fun load() = PlatformConfiguration("CARGAP", emptyMap(), emptyMap())
}
@OptIn(ExperimentalCoroutinesApi::class)
class AccessViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }
    @Test fun loginAndLogoutRemovePrivateAccount() = runTest(dispatcher) {
        val repo = Sessions(); val model = AccessViewModel(AccessUseCases(repo, Configuration()), Actor.CUSTOMER)
        model.start(); runCurrent(); assertEquals(Phase.LOGIN, model.state.value.phase)
        model.signIn(" PERSONA@EXAMPLE.INVALID ", " pass "); runCurrent()
        assertEquals(Phase.HOME, model.state.value.phase)
        assertEquals("persona@example.invalid", repo.capturedUser); assertEquals(" pass ", repo.capturedPassword)
        model.signOut(); runCurrent(); assertEquals(Phase.LOGIN, model.state.value.phase); assertNull(model.state.value.access)
    }
    @Test fun restoringWithoutNetworkHidesPrivateData() = runTest(dispatcher) {
        val repo = Sessions(); val model = AccessViewModel(AccessUseCases(repo, Configuration()), Actor.CUSTOMER)
        model.start(); runCurrent(); model.signIn("x", "p"); runCurrent()
        repo.networkFailure = true; model.resume(); runCurrent()
        assertEquals(Phase.BLOCKED, model.state.value.phase); assertNull(model.state.value.access)
        repo.networkFailure = false; model.retry(); runCurrent(); assertEquals(Phase.HOME, model.state.value.phase)
        model.signOut(); runCurrent()
    }
    @Test fun pendingAccountResumesAndLogsOutWithoutPrivateHome() = runTest(dispatcher) {
        val repo = Sessions()
        repo.result = repo.result.copy(session = repo.result.session.copy(requiresAccountCompletion = true, associationCode = "", associationName = ""))
        val model = AccessViewModel(AccessUseCases(repo, Configuration()), Actor.CUSTOMER)
        model.start(); runCurrent(); model.signIn("x", "p"); runCurrent()
        assertEquals(Phase.ONBOARDING, model.state.value.phase)
        repo.networkFailure = true; model.resume(); runCurrent()
        assertEquals(Phase.BLOCKED, model.state.value.phase); assertNull(model.state.value.access)
        repo.networkFailure = false; model.retry(); runCurrent()
        assertEquals(Phase.ONBOARDING, model.state.value.phase)
        model.signOut(); runCurrent()
        assertEquals(Phase.LOGIN, model.state.value.phase); assertNull(model.state.value.access)
    }
    @Test fun missingCredentialsNeverReachRepository() = runTest(dispatcher) {
        val repo = Sessions(); val model = AccessViewModel(AccessUseCases(repo, Configuration()), Actor.CUSTOMER)
        model.start(); runCurrent(); model.signIn("  ", "p"); runCurrent()
        assertFalse(repo.signed); assertNotNull(model.state.value.message)
    }
}
