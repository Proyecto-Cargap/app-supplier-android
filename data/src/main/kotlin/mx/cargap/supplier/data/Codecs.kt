package mx.cargap.supplier.data

import kotlinx.serialization.json.*
import mx.cargap.supplier.domain.*

internal fun invalid(): Nothing = throw AccessException(Failure.INVALID_RESPONSE)
internal fun JsonObject.string(key: String): String {
    val value = get(key) as? JsonPrimitive ?: invalid()
    if (!value.isString) invalid()
    return value.content
}
internal fun JsonObject.obj(key: String): JsonObject = get(key) as? JsonObject ?: invalid()
internal fun JsonObject.strings(key: String): List<String> = (get(key) as? JsonArray ?: invalid()).map {
    val value = it as? JsonPrimitive ?: invalid(); if (!value.isString) invalid(); value.content
}
internal fun envelope(raw: String, expected: Int, needsBody: Boolean = true): JsonObject {
    try {
        val parsed = Json.parseToJsonElement(raw).jsonObject
        val status = parsed.obj("headerResponse")["statusCode"]?.jsonPrimitive?.intOrNull ?: invalid()
        if (status != expected) throw AccessException(httpFailure(status))
        return if (needsBody) parsed.obj("businessResponse") else parsed
    } catch (e: AccessException) { throw e } catch (_: Exception) { invalid() }
}

/** DTO interno. Su cadena de depuración nunca serializa los campos. */
internal class SessionDTO(val source: JsonObject) {
    val authorization = source.string("authorization")
    val code = source.string("sessionCode")
    private val user = source.obj("user")
    val userCode = user.string("userCode")
    fun projection(actor: Actor, now: Long): Session {
        val workspaceCode = source.string("activeWorkspace")
        val expiration = source["expiration"]?.jsonPrimitive?.longOrNull ?: invalid()
        if (authorization.isBlank() || authorization.contains('\n') || authorization.contains('\r') ||
            code.isBlank() || source.string("idTransaction").isBlank() || userCode.isBlank() || user.string("user").isBlank() ||
            workspaceCode.isBlank() || workspaceCode == "allWorkspaces") invalid()
        source.strings("policies"); user.strings("groups"); user.string("state"); user.string("picture")
        user["validity"]?.jsonPrimitive?.doubleOrNull?.takeIf { it.isFinite() } ?: invalid()
        val workspaces = user["workspaces"] as? JsonArray ?: invalid()
        val parsedWorkspaces = workspaces.map { it.jsonObject.let { w -> w.string("workspaceCode") to w.string("name") } }
        val workspace = parsedWorkspaces.firstOrNull { it.first == workspaceCode } ?: invalid()
        if (expiration <= now) throw AccessException(Failure.EXPIRED)
        if (user.string("roleCode") != actor.role) throw AccessException(Failure.WRONG_ACTOR)
        val key = if (actor == Actor.CUSTOMER) "customer" else "supplier"
        if (user.string("state") != "active") throw AccessException(Failure.FORBIDDEN)
        val association = when (val raw = user[key]) {
            null, JsonNull -> null
            is JsonObject -> raw
            else -> invalid()
        }
        val associationCode = association?.string(key + "Code") ?: ""
        if (association != null && associationCode.isBlank()) throw AccessException(Failure.ASSOCIATION_MISSING)
        association?.string("logo"); association?.string("state")
        val name = user.obj("name")
        val preferred = if (name["preferredName"] == null || name["preferredName"] == JsonNull) "" else name.string("preferredName").trim()
        return Session(code, userCode,
            listOf(name.string("firstName"), name.string("lastNamePaternal"), name.string("lastNameMaternal")).filter { it.isNotEmpty() }.joinToString(" "),
            user.string("user"), workspaceCode, workspace.second, associationCode,
            association?.string("tradeName")?.ifBlank { associationCode } ?: "", expiration,
            requiresAccountCompletion = association == null, greetingName = preferred.ifBlank { name.string("firstName") })
    }
    companion object {
        fun fromStored(raw: String): SessionDTO = try { SessionDTO(Json.parseToJsonElement(raw).jsonObject) }
            catch (e: AccessException) { throw e } catch (_: Exception) { invalid() }
    }
}
