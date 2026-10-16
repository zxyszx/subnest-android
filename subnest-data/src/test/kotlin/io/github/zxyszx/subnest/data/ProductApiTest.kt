package io.github.zxyszx.subnest.data

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress

class ProductApiTest {
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
