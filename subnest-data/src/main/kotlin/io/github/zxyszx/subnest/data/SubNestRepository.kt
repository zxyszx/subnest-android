package io.github.zxyszx.subnest.data

import android.content.Context
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
    suspend fun updateSeat(id: String, changes: SeatUpdate): AccountDetail
    suspend fun credentials(id: String): AccountCredentials
    suspend fun links(detail: AccountDetail): List<SeatLink>
    suspend fun generateLink(detail: AccountDetail, seatId: String)
    suspend fun revokeLink(account: SharedAccount, linkId: String)
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

    private suspend fun request(vararg segments: String, body: JsonObject? = null, method: String = if (body == null) "GET" else "POST"): JsonElement {
        val url = origin.newBuilder().addPathSegments("api/app").apply { segments.forEach { addPathSegment(it) } }.build()
        val request = Request.Builder().url(url)
            .header("Accept", "application/json")
            .header("Accept-Language", "zh-CN")
        if (method != "GET") {
            request.header("Origin", origin.toString().trimEnd('/'))
            cookies.csrf()?.let { request.header("X-Renewlet-CSRF", it) }
            request.method(method, body?.toString()?.toRequestBody("application/json".toMediaType()))
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

    override suspend fun updateSeat(id: String, changes: SeatUpdate): AccountDetail {
        changes.validate()
        return json.decodeFromJsonElement(AccountDetail.serializer(), request("sharing", "seats", id, body = json.encodeToJsonElement(SeatUpdate.serializer(), changes).jsonObject, method = "PUT"))
    }

    override suspend fun credentials(id: String): AccountCredentials =
        json.decodeFromJsonElement(AccountCredentials.serializer(), request("sharing", "accounts", id, "credentials"))

    private suspend fun inboxLinks(): List<InboxLink> = json.decodeFromJsonElement(InboxLinks.serializer(), request("admin", "shared-inbox-links")).links

    override suspend fun links(detail: AccountDetail): List<SeatLink> {
        val account = detail.account
        if (account.verificationMode == "none" || account.status != "active") return emptyList()
        if (account.verificationMode == "totp") {
            return json.decodeFromJsonElement(TotpLinks.serializer(), request("sharing", "accounts", account.id, "totp-links")).links.map { link ->
                val url = if (link.path.matches(Regex("/otp/[A-Za-z0-9_-]{32,128}"))) origin.resolve(link.path)?.toString().orEmpty() else ""
                SeatLink(link.id, link.seatId, url, link.valid && url.isNotBlank())
            }
        }
        if (account.verificationLink.isNullOrBlank()) return emptyList()
        val seatIds = detail.seats.map { it.id }.toSet()
        return inboxLinks().filter { it.seatId in seatIds || (it.seatId.isNullOrBlank() && it.shortUrl == account.verificationLink) }.map {
            SeatLink(it.id, it.seatId.orEmpty(), it.shortUrl, it.status == "active" && (it.expiresAt == null || java.time.Instant.parse(it.expiresAt) > java.time.Instant.now()))
        }
    }

    override suspend fun generateLink(detail: AccountDetail, seatId: String) {
        val account = detail.account
        if (account.verificationMode == "none") throw SharingConfigurationException()
        val seat = detail.seats.single { it.id == seatId }
        require(account.status == "active" && !seat.memberName.isNullOrBlank() && seat.status in setOf("active", "paused") && (seat.expiresAt == null || seat.expiresAt >= java.time.LocalDate.now().toString()))
        if (account.verificationMode == "totp") {
            request("sharing", "accounts", account.id, "totp-links", body = buildJsonObject { put("seatId", seatId) })
        } else {
            // Reuse the configured public scope; never widen folder or time-window access.
            val scope = inboxLinks().firstOrNull { it.seatId.isNullOrBlank() && it.shortUrl == account.verificationLink && it.folderIds.isNotEmpty() && it.status == "active" && (it.expiresAt == null || java.time.Instant.parse(it.expiresAt) > java.time.Instant.now()) } ?: throw SharingConfigurationException()
            request(
                "admin",
                "shared-inbox-links",
                body = buildJsonObject {
                    put("mailboxId", scope.mailboxId)
                    put("seatId", seatId)
                    put("folderIds", JsonArray(scope.folderIds.map(::JsonPrimitive)))
                    put("windowMinutes", scope.windowMinutes)
                },
            )
        }
    }

    override suspend fun revokeLink(account: SharedAccount, linkId: String) {
        if (account.verificationMode == "totp") {
            request("sharing", "accounts", account.id, "totp-links", linkId, method = "DELETE")
        } else {
            request("admin", "shared-inbox-links", linkId, method = "DELETE")
        }
    }

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
