package mx.cargap.supplier.data

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import mx.cargap.supplier.domain.*

/** Propietario único de la credencial, con exclusión mutua y generación de operaciones. */
class APISessionRepository(
    private val gateway: GatewayTransport, private val store: SecureSessionStore, private val actor: Actor,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
) : SessionRepository {
    private val mutex = Mutex()
    private var memory: SessionDTO? = null
    private var accountSession: Session? = null
    private var generation = 0L
    private var restoration: Deferred<Result<SessionAccess?>>? = null
    override suspend fun signIn(user: String, password: String): SessionAccess {
        val version = mutex.withLock { clearLocal(); generation }
        val body = buildJsonObject { put("user", user); put("password", password) }.toString()
        val dto = SessionDTO(envelope(gateway.send(GatewayRequest("POST", listOf("security", "session"), 201, body)), 201))
        if (!current(version)) { revoke(dto); throw AccessException(Failure.CANCELLED) }
        try { dto.projection(actor, now()) } catch (e: Exception) { revoke(dto); throw normalized(e) }
        val account = try { resolveAccount(dto) } catch (e: Exception) { withContext(NonCancellable) { revoke(dto) }; throw normalized(e) }
        return mutex.withLock {
            if (generation != version) null else accept(dto, account)
        } ?: run { revoke(dto); throw AccessException(Failure.CANCELLED) }
    }
    override suspend fun restore(): SessionAccess? {
        val pending = mutex.withLock {
            restoration ?: run {
                val version = generation
                scope.async(start = CoroutineStart.LAZY) {
                    try { Result.success(performRestore(version)) }
                    catch (e: Exception) { if (e is CancellationException) throw e; Result.failure(e) }
                }.also { restoration = it }
            }
        }
        pending.start()
        return try { pending.await().getOrThrow() } finally { mutex.withLock { if (restoration === pending) restoration = null } }
    }
    private suspend fun performRestore(version: Long): SessionAccess? {
        val rotateCredential = mutex.withLock { memory != null }
        val previous = mutex.withLock {
            if (version != generation) throw AccessException(Failure.CANCELLED)
            try { memory ?: store.read()?.let { SessionDTO.fromStored(it) } }
            catch (_: Exception) { clearLocal(); throw AccessException(Failure.STORAGE) }
        } ?: return null
        try { previous.projection(actor, now()) }
        catch (e: Exception) { mutex.withLock { if (generation == version) clearLocal() }; throw normalized(e) }
        try {
            val status = if (rotateCredential) 201 else 200
            val dto = SessionDTO(envelope(gateway.send(GatewayRequest(
                if (rotateCredential) "PUT" else "POST",
                listOf("security", "session", if (rotateCredential) "renovate" else "restore"),
                status, "{}", previous.authorization)), status))
            if (!rotateCredential && (dto.authorization != previous.authorization ||
                dto.source.getValue("expiration").jsonPrimitive.long > previous.source.getValue("expiration").jsonPrimitive.long))
                throw AccessException(Failure.INVALID_RESPONSE)
            if (!current(version)) { revoke(dto); throw AccessException(Failure.CANCELLED) }
            if (dto.code != previous.code || dto.userCode != previous.userCode) throw AccessException(Failure.INVALID_RESPONSE)
            try { dto.projection(actor, now()) } catch (e: Exception) { revoke(dto); throw normalized(e) }
            // Conservar la credencial rotada sin habilitar operaciones hasta confirmar la cuenta.
            val retained = mutex.withLock {
                if (version != generation) false else {
                    memory = dto; accountSession = null
                    if (runCatching { store.write(dto.source.toString()) }.isFailure) runCatching { store.remove() }
                    true
                }
            }
            if (!retained) { revoke(dto); throw AccessException(Failure.CANCELLED) }
            val account = try { resolveAccount(dto) } catch (e: Exception) {
                if (e !is AccessException || !e.failure.transient) withContext(NonCancellable) { revoke(dto) }
                throw e
            }
            return mutex.withLock { if (version == generation) accept(dto, account) else null }
                ?: run { revoke(dto); throw AccessException(Failure.CANCELLED) }
        } catch (e: Exception) {
            val error = normalized(e)
            if (!error.failure.transient) mutex.withLock { if (version == generation) clearLocal() }
            throw error
        }
    }
    /** Ejecuta HTTP autenticado y descarta respuestas de una sesión anterior. */
    suspend fun sendAuthenticated(request: GatewayRequest): String {
        mutex.withLock { restoration }?.await()?.getOrThrow()
        val (dto, version) = mutex.withLock {
            val value = memory ?: throw AccessException(Failure.EXPIRED)
            value.projection(actor, now())
            val ownEnrollment = request.path.size >= 2 && request.path[0] == actor.name.lowercase() && request.path[1] == "enrollment"
            val ownAccount = request.path == listOf(actor.name.lowercase(), "account", "me") && request.method == "GET"
            if (accountSession == null || (accountSession?.requiresAccountCompletion != false && !ownEnrollment && !ownAccount)) throw AccessException(Failure.ASSOCIATION_MISSING)
            value to generation
        }
        try {
            val result = gateway.send(GatewayRequest(request.method, request.path, request.expectedStatus,
                request.body, dto.authorization, request.contentType, request.binaryBody))
            if (!current(version)) throw AccessException(Failure.CANCELLED)
            return result
        } catch (e: AccessException) {
            if (e.failure in listOf(Failure.EXPIRED, Failure.CREDENTIALS)) {
                mutex.withLock {
                    if (generation != version || memory?.authorization != dto.authorization) throw AccessException(Failure.CANCELLED)
                    clearLocal()
                }
                throw AccessException(Failure.EXPIRED)
            }
            throw e
        }
    }
    override suspend fun signOut(): Boolean {
        val previous = mutex.withLock {
            val value = memory ?: runCatching { store.read()?.let { SessionDTO.fromStored(it) } }.getOrNull()
            clearLocal(); value
        }
        return previous?.let { revoke(it) } ?: true
    }
    override suspend fun clear() { mutex.withLock { clearLocal() } }
    private suspend fun current(version: Long) = mutex.withLock { generation == version }
    private suspend fun clearLocal() {
        generation++; memory = null; accountSession = null; restoration = null
        runCatching { store.remove() }
    }
    private suspend fun resolveAccount(dto: SessionDTO): Session {
        val identity = dto.projection(actor, now())
        try {
            val value = envelope(gateway.send(GatewayRequest("GET", listOf(actor.name.lowercase(), "account", "me"), 200, authorization = dto.authorization)), 200)
            val code = value.getValue("entityCode").jsonPrimitive.content
            val name = value.getValue("associationName").jsonPrimitive.content
            val state = value.getValue("entityState").jsonPrimitive.content
            val enrollment = value.getValue("enrollmentState").jsonPrimitive.content
            val canOperate = value.getValue("canOperate").jsonPrimitive.boolean
            require(enrollment in listOf("not_started", "draft", "in_review", "correction_required", "approved"))
            require(!canOperate || (code.isNotBlank() && state == actor.name.lowercase() && enrollment in listOf("not_started", "approved")))
            return identity.copy(associationCode = code, associationName = name.ifBlank { identity.name }, requiresAccountCompletion = !canOperate)
        } catch (e: Exception) { throw normalized(e) }
    }
    private suspend fun accept(dto: SessionDTO, session: Session): SessionAccess {
        memory = dto; accountSession = session
        val persisted = runCatching { store.write(dto.source.toString()) }.isSuccess
        if (!persisted) runCatching { store.remove() }
        return SessionAccess(session, persisted)
    }
    private suspend fun revoke(dto: SessionDTO): Boolean = runCatching {
        envelope(gateway.send(GatewayRequest("DELETE", listOf("security", "session", dto.code), 200,
            authorization = dto.authorization)), 200, needsBody = false)
    }.isSuccess
    private fun normalized(error: Exception): AccessException {
        if (error is CancellationException) throw error
        return error as? AccessException ?: AccessException(Failure.INVALID_RESPONSE)
    }
}
