package mx.cargap.supplier.data

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import mx.cargap.supplier.domain.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

private fun fixture(actor: Actor = Actor.CUSTOMER, associated: Boolean = true, expiration: Long = 4_000_000_000,
                    token: String = "fixture-token", userCode: String = "USER"): String {
    val key = if (actor == Actor.CUSTOMER) "customer" else "supplier"
    val association = if (associated) ",\"$key\":{\"${key}Code\":\"ACTOR\",\"tradeName\":\"Empresa de prueba\",\"logo\":\"\",\"state\":\"active\"}" else ""
    return """{"headerResponse":{"statusCode":201},"businessResponse":{"authorization":"$token","sessionCode":"SESSION","activeWorkspace":"WS","expiration":$expiration,"policies":[],"idTransaction":"TX","user":{"userCode":"$userCode","user":"persona@example.invalid","roleCode":"${actor.role}","state":"active","validity":0,"groups":[],"workspaces":[{"workspaceCode":"WS","name":"Pruebas"}],"name":{"firstName":"Ana","lastNamePaternal":"Prueba","lastNameMaternal":""},"picture":""$association}}}"""
}
private const val CLOSED = """{"headerResponse":{"statusCode":200}}"""
private class MemoryStore : SecureSessionStore {
    var bytes: String? = null
    var failWrites = false
    override suspend fun read() = bytes
    override suspend fun write(data: String) { if (failWrites) throw AccessException(Failure.STORAGE); bytes = data }
    override suspend fun remove() { bytes = null }
}
private class StubGateway(vararg results: Any) : GatewayTransport {
    val queue = ArrayDeque(results.toList())
    val requests = mutableListOf<GatewayRequest>()
    val accountResults = ArrayDeque<Any>()
    private var lastIdentity: JsonObject? = null
    var waitForRenewal: CompletableDeferred<Unit>? = null
    override suspend fun send(request: GatewayRequest): String {
        requests += request
        if (request.path.takeLast(2) == listOf("account", "me")) {
            if (accountResults.isNotEmpty()) { val result = accountResults.removeFirst(); if (result is Failure) throw AccessException(result); return result as String }
            val actor = request.path.first(); val associated = lastIdentity?.get(actor) != null
            return """{"headerResponse":{"statusCode":200},"businessResponse":{"entityCode":"${if (associated) "ACTOR" else ""}","associationName":"Empresa de prueba","entityState":"${if (associated) actor else ""}","enrollmentState":"not_started","canOperate":$associated}}"""
        }
        val result = queue.removeFirst()
        if (request.method == "PUT") waitForRenewal?.await()
        if (result is Failure) throw AccessException(result)
        val text = result as String
        runCatching { Json.parseToJsonElement(text).jsonObject["businessResponse"]?.jsonObject?.get("user")?.jsonObject }.getOrNull()?.let { lastIdentity = it }
        return text
    }
}
private suspend fun failure(expected: Failure, action: suspend () -> Unit) {
    try { action(); fail("Se esperaba $expected") } catch (e: AccessException) { assertEquals(expected, e.failure) }
}
@OptIn(ExperimentalCoroutinesApi::class)
class SessionRepositoryTest {
    @Test fun rotatedCredentialSurvivesTemporaryAccountFailureWithoutEnablingRequests() = runTest {
        val store = MemoryStore()
        val gateway = StubGateway(fixture(), fixture(token = "rotated"), fixture(token = "recovered"))
        val repo = APISessionRepository(gateway, store, Actor.CUSTOMER, backgroundScope)
        repo.signIn("x", "p")
        gateway.accountResults.add(Failure.UNAVAILABLE)
        failure(Failure.UNAVAILABLE) { repo.restore() }
        assertEquals("rotated", SessionDTO.fromStored(store.read()!!).authorization)
        failure(Failure.ASSOCIATION_MISSING) { repo.sendAuthenticated(GatewayRequest("GET", listOf("customer", "enrollment"), 200)) }
        repo.restore()
        assertEquals("rotated", gateway.requests.filter { it.method == "PUT" }.last().authorization)
    }
    @Test fun signInPersistsOnlyValidatedSession() = runTest {
        val store = MemoryStore(); val gateway = StubGateway(fixture())
        val repo = APISessionRepository(gateway, store, Actor.CUSTOMER, backgroundScope)
        val result = repo.signIn("persona@example.invalid", " pass ")
        assertTrue(result.persisted); assertEquals("ACTOR", result.session.associationCode); assertNotNull(store.bytes)
        assertNull(gateway.requests.first().authorization)
        assertEquals(" pass ", Json.parseToJsonElement(gateway.requests.first().body!!).jsonObject.string("password"))
    }
    @Test fun supplierHasItsOwnAssociation() = runTest {
        val repo = APISessionRepository(StubGateway(fixture(Actor.SUPPLIER)), MemoryStore(), Actor.SUPPLIER, backgroundScope)
        assertEquals("ACTOR", repo.signIn("x", "p").session.associationCode)
    }
    @Test fun wrongActorIsRevokedAndNeverPersisted() = runTest {
        val store = MemoryStore(); val gateway = StubGateway(fixture(Actor.SUPPLIER), CLOSED)
        val repo = APISessionRepository(gateway, store, Actor.CUSTOMER, backgroundScope)
        failure(Failure.WRONG_ACTOR) { repo.signIn("x", "p") }; assertNull(store.bytes)
        assertEquals("DELETE", gateway.requests.last().method)
    }
    @Test fun missingAssociationAllowsOnlyPendingAccountAndRestores() = runTest {
        for (actor in Actor.entries) {
            val store = MemoryStore()
            val gateway = StubGateway(fixture(actor, associated = false), fixture(actor, associated = false), fixture(actor))
            val repo = APISessionRepository(gateway, store, actor, backgroundScope)
            val result = repo.signIn("x", "p")
            assertTrue(result.session.requiresAccountCompletion)
            assertEquals("", result.session.associationCode); assertEquals("Ana", result.session.greetingName)
            assertNotNull(store.bytes)
            assertTrue(repo.restore()!!.session.requiresAccountCompletion)
            val linked = repo.restore()!!.session
            assertFalse(linked.requiresAccountCompletion); assertEquals("ACTOR", linked.associationCode)
            assertEquals(listOf("POST", "GET", "PUT", "GET", "PUT", "GET"), gateway.requests.map { it.method })
        }
    }
    @Test fun inactiveOrMalformedAssociationDoesNotAuthorize() = runTest {
        for ((body, expected) in listOf(
            fixture().replace("\"state\":\"active\"", "\"state\":\"registered\"") to Failure.FORBIDDEN,
            fixture().replace("\"customerCode\":\"ACTOR\"", "\"customerCode\":\"\"") to Failure.ASSOCIATION_MISSING)) {
            val store = MemoryStore()
            val repo = APISessionRepository(StubGateway(body, CLOSED), store, Actor.CUSTOMER, backgroundScope)
            failure(expected) { repo.signIn("x", "p") }; assertNull(store.bytes)
        }
    }
    @Test fun incorrectCredentialsDoNotPersist() = runTest {
        val store = MemoryStore(); val repo = APISessionRepository(StubGateway(Failure.CREDENTIALS), store, Actor.CUSTOMER, backgroundScope)
        failure(Failure.CREDENTIALS) { repo.signIn("x", "p") }; assertNull(store.bytes)
    }
    @Test fun storageFailureAllowsMemorySessionOnly() = runTest {
        val store = MemoryStore().apply { failWrites = true }
        val repo = APISessionRepository(StubGateway(fixture()), store, Actor.CUSTOMER, backgroundScope)
        assertFalse(repo.signIn("x", "p").persisted); assertNull(store.bytes)
    }
    @Test fun aNewRepositoryRestoresWithoutRotatingOrExtending() = runTest {
        val store = MemoryStore()
        APISessionRepository(StubGateway(fixture()), store, Actor.CUSTOMER, backgroundScope).signIn("x", "p")
        val gateway = StubGateway(fixture().replace("statusCode\":201", "statusCode\":200"))
        val repo = APISessionRepository(gateway, store, Actor.CUSTOMER, backgroundScope)
        assertNotNull(repo.restore())
        assertEquals("fixture-token", gateway.requests.first().authorization)
        assertEquals("POST", gateway.requests.first().method)
        assertEquals(listOf("security", "session", "restore"), gateway.requests.first().path)
        assertEquals("fixture-token", SessionDTO.fromStored(store.bytes!!).authorization)
    }
    @Test fun coldRestoreRejectsCredentialRotationAndExtendedExpiration() = runTest {
        for (response in listOf(fixture(token = "unexpected"), fixture(expiration = 4_000_000_001))) {
            val store = MemoryStore()
            APISessionRepository(StubGateway(fixture()), store, Actor.CUSTOMER, backgroundScope).signIn("x", "p")
            val gateway = StubGateway(response.replace("statusCode\":201", "statusCode\":200"))
            val repository = APISessionRepository(gateway, store, Actor.CUSTOMER, backgroundScope)
            failure(Failure.INVALID_RESPONSE) { repository.restore() }
            assertNull(store.bytes)
            assertEquals(listOf("security", "session", "restore"), gateway.requests.single().path)
            assertEquals("fixture-token", gateway.requests.single().authorization)
        }
    }
    @Test fun networkFailureRetainsCredentialForRetry() = runTest {
        val store = MemoryStore(); val repo = APISessionRepository(StubGateway(fixture(), Failure.CONNECTION, fixture()), store, Actor.CUSTOMER, backgroundScope)
        repo.signIn("x", "p"); failure(Failure.CONNECTION) { repo.restore() }
        assertNotNull(store.bytes); assertNotNull(repo.restore())
    }
    @Test fun definitiveRejectionClearsCredential() = runTest {
        val store = MemoryStore(); val repo = APISessionRepository(StubGateway(fixture(), Failure.EXPIRED), store, Actor.CUSTOMER, backgroundScope)
        repo.signIn("x", "p"); failure(Failure.EXPIRED) { repo.restore() }; assertNull(store.bytes)
    }
    @Test fun corruptStorageDoesNotAuthorize() = runTest {
        val store = MemoryStore().apply { bytes = "broken" }
        val repo = APISessionRepository(StubGateway(), store, Actor.CUSTOMER, backgroundScope)
        failure(Failure.STORAGE) { repo.restore() }; assertNull(store.bytes)
    }
    @Test fun expiredStorageDoesNotReachTheNetwork() = runTest {
        val store = MemoryStore().apply { bytes = envelope(fixture(expiration = 1), 201).toString() }
        val gateway = StubGateway(); val repo = APISessionRepository(gateway, store, Actor.CUSTOMER, backgroundScope)
        failure(Failure.EXPIRED) { repo.restore() }; assertTrue(gateway.requests.isEmpty())
    }
    @Test fun concurrentRenewalsShareOneRequest() = runTest {
        val gate = CompletableDeferred<Unit>(); val gateway = StubGateway(fixture(), fixture()).apply { waitForRenewal = gate }
        val repo = APISessionRepository(gateway, MemoryStore(), Actor.CUSTOMER, backgroundScope)
        repo.signIn("x", "p")
        val a = async { repo.restore() }; val b = async { repo.restore() }; runCurrent()
        assertEquals(1, gateway.requests.count { it.method == "PUT" }); gate.complete(Unit)
        assertEquals(a.await(), b.await())
    }
    @Test fun lateRenewalCannotUndoLogout() = runTest {
        val store = MemoryStore(); val gate = CompletableDeferred<Unit>()
        val gateway = StubGateway(fixture(), fixture(token = "rotated"), CLOSED, CLOSED).apply { waitForRenewal = gate }
        val repo = APISessionRepository(gateway, store, Actor.CUSTOMER, backgroundScope)
        repo.signIn("x", "p")
        val late = async { runCatching { repo.restore() } }; runCurrent()
        repo.signOut(); gate.complete(Unit)
        assertEquals(Failure.CANCELLED, (late.await().exceptionOrNull() as AccessException).failure)
        assertNull(store.bytes)
    }
    @Test fun remoteLogoutFailureStillClearsLocalState() = runTest {
        val store = MemoryStore(); val repo = APISessionRepository(StubGateway(fixture(), Failure.CONNECTION), store, Actor.CUSTOMER, backgroundScope)
        repo.signIn("x", "p"); assertFalse(repo.signOut()); assertNull(store.bytes); assertNull(repo.restore())
    }
    @Test fun aDifferentIdentityOnRenewalIsRejected() = runTest {
        val store = MemoryStore(); val repo = APISessionRepository(StubGateway(fixture(), fixture(userCode = "OTHER")), store, Actor.CUSTOMER, backgroundScope)
        repo.signIn("x", "p"); failure(Failure.INVALID_RESPONSE) { repo.restore() }; assertNull(store.bytes)
    }
    @Test fun businessEnvelopeIsValidated() = runTest {
        failure(Failure.EXPIRED) { envelope("""{"headerResponse":{"statusCode":401}}""", 201) }
        failure(Failure.INVALID_RESPONSE) { envelope("{}", 201) }
    }
    @Test fun httpSendsOnlyRawAuthorizationAndValidatesStatus() = runTest {
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(201).setBody(fixture()))
            val gateway = OkHttpGateway(server.url("/").toString(), true)
            gateway.send(GatewayRequest("PUT", listOf("security", "session", "restore"), 201, "{}", "fixture-token"))
            val request = server.takeRequest()
            assertEquals("fixture-token", request.getHeader("Authorization")); assertEquals("{}", request.body.readUtf8())
            server.enqueue(MockResponse().setResponseCode(401))
            failure(Failure.EXPIRED) { gateway.send(GatewayRequest("GET", listOf("x"), 200)) }
        } finally { server.shutdown() }
    }
    @Test fun redirectsCannotLeakAuthorization() = runTest {
        val server = MockWebServer(); val other = MockWebServer(); server.start(); other.start()
        try {
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", other.url("/")))
            val gateway = OkHttpGateway(server.url("/").toString(), true)
            failure(Failure.INVALID_RESPONSE) { gateway.send(GatewayRequest("GET", listOf("x"), 200, authorization = "fixture-token")) }
            assertEquals(0, other.requestCount)
        } finally { server.shutdown(); other.shutdown() }
    }
    @Test fun externalCleartextURLsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { OkHttpGateway("http://example.invalid", true) }
    }
}
