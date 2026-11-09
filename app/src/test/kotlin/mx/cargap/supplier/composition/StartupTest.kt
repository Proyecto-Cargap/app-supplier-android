package mx.cargap.supplier.composition

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import mx.cargap.supplier.application.AccessUseCases
import mx.cargap.supplier.data.*
import mx.cargap.supplier.domain.*
import mx.cargap.supplier.presentation.AccessViewModel
import mx.cargap.supplier.presentation.Phase
import org.junit.*
import org.junit.Assert.*

/** Regresión del arranque con la composición real de marca y sesión, sin servicios externos. */
@OptIn(ExperimentalCoroutinesApi::class)
class StartupTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun freshLaunchReachesLoginWithoutRequestingRemoteBranding() = runTest(dispatcher) {
        val gateway = StartupGateway()
        val sessions = APISessionRepository(gateway, StartupStore(), AccessModule.actor(), backgroundScope)
        val model = AccessViewModel(
            AccessUseCases(sessions, AccessModule.configuration()), AccessModule.actor(),
        )

        model.start()
        runCurrent()

        assertEquals(Phase.LOGIN, model.state.value.phase)
        assertNull(model.state.value.message)
        assertEquals("CARGAP", model.state.value.configuration?.title)
        assertTrue(gateway.requests.isEmpty())
    }

    @Test fun localBrandingDoesNotBypassSessionRenewalOnNetworkFailure() = runTest(dispatcher) {
        val store = StartupStore()
        val gateway = StartupGateway()
        val actor = AccessModule.actor()
        val initial = APISessionRepository(gateway, store, actor, backgroundScope)
        initial.signIn("persona@example.invalid", "fixture-password")
        gateway.requests.clear()
        val sessions = APISessionRepository(gateway, store, actor, backgroundScope)
        val model = AccessViewModel(AccessUseCases(sessions, AccessModule.configuration()), actor)

        model.start()
        runCurrent()

        assertEquals(Phase.BLOCKED, model.state.value.phase)
        assertNull(model.state.value.access)
        assertNotNull(model.state.value.configuration)
        assertNotNull(store.read())
        assertEquals(1, gateway.requests.size)
        assertEquals("POST", gateway.requests.single().method)
        assertEquals(listOf("security", "session", "restore"), gateway.requests.single().path)
    }
}

private class StartupStore : SecureSessionStore {
    private var bytes: String? = null
    override suspend fun read() = bytes
    override suspend fun write(data: String) { bytes = data }
    override suspend fun remove() { bytes = null }
}

private class StartupGateway : GatewayTransport {
    val requests = mutableListOf<GatewayRequest>()
    override suspend fun send(request: GatewayRequest): String {
        requests += request
        if (request.path == listOf("supplier", "account", "me")) return """{"headerResponse":{"statusCode":200},"businessResponse":{"entityCode":"ACTOR","associationName":"Empresa de prueba","entityState":"supplier","enrollmentState":"not_started","canOperate":true}}"""
        if (request.method != "POST" || request.path != listOf("security", "session")) {
            throw AccessException(Failure.CONNECTION)
        }
        return """{"headerResponse":{"statusCode":201},"businessResponse":{"authorization":"fixture-token","sessionCode":"SESSION","activeWorkspace":"WS","expiration":4000000000,"policies":[],"idTransaction":"TX","user":{"userCode":"USER","user":"persona@example.invalid","roleCode":"300-supplier","state":"active","validity":0,"groups":[],"workspaces":[{"workspaceCode":"WS","name":"Pruebas"}],"name":{"firstName":"Ana","lastNamePaternal":"Prueba","lastNameMaternal":""},"picture":"","supplier":{"supplierCode":"ACTOR","tradeName":"Empresa de prueba","logo":"","state":"active"}}}}"""
    }
}
