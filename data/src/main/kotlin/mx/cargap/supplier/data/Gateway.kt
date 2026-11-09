package mx.cargap.supplier.data

import kotlinx.coroutines.suspendCancellableCoroutine
import mx.cargap.supplier.domain.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Solicitud privada de infraestructura. No debe cruzar a presentación. */
class GatewayRequest(val method: String, val path: List<String>, val expectedStatus: Int,
                     val body: String? = null, val authorization: String? = null,
                     val contentType: String = "application/json", val binaryBody: ByteArray? = null)
interface GatewayTransport { suspend fun send(request: GatewayRequest): String }

/** Cliente limitado al gateway, sin caché, redirecciones ni reintentos automáticos. */
class OkHttpGateway(baseUrl: String, allowLocalHttp: Boolean = false) : GatewayTransport {
    private val base = baseUrl.toHttpUrl()
    private val client = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()
    init {
        require(base.username.isEmpty() && base.password.isEmpty() && base.query == null && base.fragment == null)
        require(base.isHttps || (allowLocalHttp && base.host in setOf("localhost", "127.0.0.1", "10.0.2.2")))
    }
    override suspend fun send(request: GatewayRequest): String = suspendCancellableCoroutine { continuation ->
        val url = base.newBuilder()
        request.path.forEach { part -> require(part.isNotEmpty() && part != ".." && !part.contains('/')); url.addPathSegment(part) }
        val builder = Request.Builder().url(url.build()).header("Accept", "application/json").header("Cache-Control", "no-store")
        request.authorization?.let { builder.header("Authorization", it) }
        builder.method(request.method, request.binaryBody?.toRequestBody(request.contentType.toMediaType()) ?: request.body?.toRequestBody(request.contentType.toMediaType()))
        val call = client.newCall(builder.build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(AccessException(Failure.CONNECTION))
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        if (it.code != request.expectedStatus) throw AccessException(httpFailure(it.code, request.method == "POST"))
                        val bytes = it.body?.string() ?: throw AccessException(Failure.INVALID_RESPONSE)
                        if (continuation.isActive) continuation.resume(bytes)
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(if (e is AccessException) e else AccessException(Failure.CONNECTION))
                    }
                }
            }
        })
    }
}
internal fun httpFailure(status: Int, login: Boolean = false): Failure = when {
    status == 400 -> Failure.INVALID_INPUT
    status == 409 -> Failure.CONFLICT
    status == 413 -> Failure.TOO_LARGE
    status == 401 -> if (login) Failure.CREDENTIALS else Failure.EXPIRED
    status == 403 -> Failure.FORBIDDEN
    status == 406 -> Failure.CREDENTIALS
    status == 408 -> Failure.CONNECTION
    status == 429 || status >= 500 -> Failure.UNAVAILABLE
    else -> Failure.INVALID_RESPONSE
}
