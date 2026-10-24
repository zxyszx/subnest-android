package io.github.zxyszx.subnest.data

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress

class ProductApiTest {
    @Test
    fun `seat edit sends exact period amount and paid status with PUT and csrf`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val origin = "http://127.0.0.1:${server.address.port}/".toHttpUrl()
        var method = ""
        var csrf = ""
        var body = ""
        server.createContext("/api/app/") { exchange ->
            val response = if (exchange.requestURI.path.endsWith("auth/login")) {
                exchange.responseHeaders.add("Set-Cookie", "renewlet_session=fixture; Path=/; HttpOnly")
                exchange.responseHeaders.add("Set-Cookie", "renewlet_csrf=csrf; Path=/")
                """{"data":{"type":"session","user":{"id":"u","name":"Test","email":"test@example.com"}}}"""
            } else {
                method = exchange.requestMethod
                csrf = exchange.requestHeaders.getFirst("X-Renewlet-CSRF").orEmpty()
                body = exchange.requestBody.bufferedReader().readText()
                """{"data":{"account":{"id":"a","subscription":{"id":"s","name":"Server A","platformName":"Server"},"name":"Server","accountNumber":1,"loginAccount":"test@example.com","capacity":5,"occupiedSeats":1,"nextBillingDate":"2026-11-01","status":"active"},"seats":[{"id":"seat","seatNumber":1,"status":"active","memberName":"Member","monthlyPrice":"15","currentReceivable":{"amount":"15","status":"paid","periodStart":"2026-10-16","periodEnd":"2026-11-16"}}]}}"""
            }
            val bytes = response.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val store = object : CookieStorage {
                private var saved = ""
                override fun read() = saved
                override fun write(value: String) {
                    saved = value
                }
            }
            val api = HttpSubNestRepository(origin, SessionCookies(origin, store))
            api.login("test@example.com", "fixture")
            val changes = SeatUpdate("Member", "contact", "wechat", "15", "15", "CNY", 1, "2026-10-16", "2026-11-16", "active", "paid", "preserve notes")
            val detail = api.updateSeat("seat", changes)
            val sent = Json.parseToJsonElement(body).jsonObject
            assertEquals("PUT", method)
            assertEquals("csrf", csrf)
            assertEquals("15", sent.getValue("billingAmount").jsonPrimitive.content)
            assertEquals("paid", sent.getValue("paymentStatus").jsonPrimitive.content)
            assertEquals("preserve notes", sent.getValue("notes").jsonPrimitive.content)
            assertEquals("paid", detail.seats.single().currentReceivable?.status)
            assertEquals("Server A", detail.account.subscription.name)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `login cookies and csrf protect renewal and logout clears persistent session`() = runBlocking {
        val writes = mutableListOf<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val origin = "http://127.0.0.1:${server.address.port}/".toHttpUrl()
        server.createContext("/api/app/") { exchange ->
            val path = exchange.requestURI.path
            val body = exchange.requestBody.bufferedReader().readText()
            val response = when {
                path.endsWith("auth/login") -> {
                    exchange.responseHeaders.add("Set-Cookie", "renewlet_session=fixture-session; Path=/; HttpOnly")
                    exchange.responseHeaders.add("Set-Cookie", "renewlet_csrf=fixture-csrf; Path=/")
                    """{"data":{"type":"session","user":{"id":"u","name":"Test","email":"test@example.com"}}}"""
                }

                else -> {
                    writes.add("${exchange.requestMethod} $path ${exchange.requestHeaders.getFirst("Origin")} ${exchange.requestHeaders.getFirst("X-Renewlet-CSRF")} ${exchange.requestHeaders.getFirst("Cookie")} $body")
                    """{"data":null}"""
                }
            }
            val bytes = response.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val store = object : CookieStorage {
                var value = ""
                override fun read() = value
                override fun write(value: String) {
                    this.value = value
                }
            }
            val jar = SessionCookies(origin, store)
            val api = HttpSubNestRepository(origin, jar)
            assertEquals("u", api.login("test@example.com", "fixture-password").user?.id)
            val row = Subscription("safe-id", "Netflix", "45", "CNY", "monthly", "2026-10-17", "active", false, false)
            api.renew(row, "2026-11-17")
            assertTrue(writes.single().startsWith("POST /api/app/subscriptions/safe-id/renew ${origin.toString().trimEnd('/')} fixture-csrf"))
            assertTrue(writes.single().contains("renewlet_session=fixture-session"))
            assertTrue(writes.single().contains("\"mode\":\"continue\""))
            api.logout()
            assertTrue(SessionCookies(origin, store).loadForRequest(origin).isEmpty())
        } finally {
            server.stop(0)
        }
    }
}
