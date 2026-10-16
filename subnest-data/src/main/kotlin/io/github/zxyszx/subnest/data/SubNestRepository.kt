package io.github.zxyszx.subnest.data

import android.content.Context
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ApiException(val status: Int, message: String) : IOException(message)

fun serverOrigin(input: String, debug: Boolean): HttpUrl {
    val url = input.trim().toHttpUrl()
    require(url.username.isEmpty() && url.password.isEmpty() && url.encodedPath == "/" && url.query == null && url.fragment == null) {
        "Use a server origin without credentials, path, query or fragment"
    }
    require(url.isHttps || (debug && url.host in setOf("10.0.2.2", "localhost", "127.0.0.1"))) { "HTTPS is required" }
    return url
}

interface SubNestRepository {
    suspend fun session(): LoginResult
    suspend fun login(email: String, password: String): LoginResult
    suspend fun verify(ticket: String, code: String, recovery: Boolean): LoginResult
    suspend fun subscriptions(): List<Subscription>
    suspend fun accounts(): List<SharedAccount>
    suspend fun detail(id: String): AccountDetail
    suspend fun renew(subscription: Subscription, nextDate: String)
    suspend fun logout()
    fun clearSession()
}

fun createSubNestRepository(context: Context, origin: String, debug: Boolean): SubNestRepository {
    val url = serverOrigin(origin, debug)
    return HttpSubNestRepository(url, SessionCookies(url, EncryptedCookieStorage(context)))
}

class HttpSubNestRepository(private val origin: HttpUrl, private val cookies: SessionCookies) : SubNestRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .cookieJar(cookies)
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    private suspend fun request(vararg segments: String, body: JsonObject? = null): JsonElement {
        val url = origin.newBuilder().addPathSegments("api/app").apply { segments.forEach { addPathSegment(it) } }.build()
        val request = Request.Builder().url(url)
            .header("Accept", "application/json")
            .header("Accept-Language", "zh-CN")
        if (body != null) {
            request.header("Origin", origin.toString().trimEnd('/'))
            cookies.csrf()?.let { request.header("X-Renewlet-CSRF", it) }
            request.post(body.toString().toRequestBody("application/json".toMediaType()))
        }
        val response = suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request.build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isCancelled) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response) { _, value, _ -> value.close() }
                }
            })
        }
        return response.use {
            val payload = runCatching { json.parseToJsonElement(it.body.string()).jsonObject }.getOrNull()
            if (!it.isSuccessful) {
                val message = (payload?.get("error") as? JsonObject)?.get("message")?.jsonPrimitive?.content
                throw ApiException(it.code, message ?: "HTTP ${it.code}")
            }
            payload?.get("data") ?: throw IOException("Invalid SubNest API response")
        }
    }

    override suspend fun session(): LoginResult = json.decodeFromJsonElement(LoginResult.serializer(), request("auth", "session"))

    override suspend fun login(email: String, password: String): LoginResult {
        cookies.clear()
        return json.decodeFromJsonElement(
            LoginResult.serializer(),
            request(
                "auth",
                "login",
                body = buildJsonObject {
                    put("email", email)
                    put("password", password)
                },
            ),
        )
    }

    override suspend fun verify(ticket: String, code: String, recovery: Boolean): LoginResult = json.decodeFromJsonElement(
        LoginResult.serializer(),
        request(
            "auth",
            "mfa",
            "verify",
            body = buildJsonObject {
                put("ticketId", ticket)
                put("method", if (recovery) "recovery_code" else "totp")
                put("code", code)
            },
        ),
    )

    override suspend fun subscriptions(): List<Subscription> =
        json.decodeFromJsonElement(Subscriptions.serializer(), request("subscriptions", "index")).subscriptions

    override suspend fun accounts(): List<SharedAccount> =
        json.decodeFromJsonElement(Accounts.serializer(), request("sharing", "accounts")).accounts

    override suspend fun detail(id: String): AccountDetail =
        json.decodeFromJsonElement(AccountDetail.serializer(), request("sharing", "accounts", id))

    override suspend fun renew(subscription: Subscription, nextDate: String) {
        require(subscription.canRenew)
        require(java.time.LocalDate.parse(nextDate) > java.time.LocalDate.parse(subscription.nextBillingDate))
        request(
            "subscriptions",
            subscription.id,
            "renew",
            body = buildJsonObject {
                put("mode", "continue")
                put("price", subscription.price)
                put("currency", subscription.currency)
                put("nextBillingDate", nextDate)
                put("autoCalculateNextBillingDate", subscription.autoCalculateNextBillingDate)
            },
        )
    }

    override suspend fun logout() {
        try {
            request("auth", "logout", body = buildJsonObject {})
        } finally {
            cookies.clear()
        }
    }

    override fun clearSession() = cookies.clear()
}
