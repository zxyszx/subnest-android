package io.github.zxyszx.subnest.data

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress

class SharingLinksTest {
    @Test
    fun `generating a seat link preserves the configured mailbox scope and never copies another account`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val origin = "http://127.0.0.1:${server.address.port}/".toHttpUrl()
        var posted = ""
        var deleted = ""
        server.createContext("/api/app/admin/shared-inbox-links") { exchange ->
            val response = when (exchange.requestMethod) {
                "GET" -> """{"data":{"links":[{"id":"other","shortUrl":"https://example.test/s/other","mailboxId":"other-mailbox","folderIds":["all"],"windowMinutes":120,"status":"active"},{"id":"public","shortUrl":"https://example.test/s/configured","mailboxId":"configured-mailbox","folderIds":["netflix-only"],"windowMinutes":30,"status":"active"},{"id":"member","shortUrl":"https://example.test/s/member","mailboxId":"configured-mailbox","seatId":"seat-1","folderIds":["netflix-only"],"windowMinutes":30,"status":"active"}]}}"""

                "POST" -> {
                    posted = exchange.requestBody.bufferedReader().readText()
                    """{"data":{}}"""
                }

                else -> {
                    deleted = exchange.requestURI.path
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
                override fun read() = ""
                override fun write(value: String) = Unit
            }
            val api = HttpSubNestRepository(origin, SessionCookies(origin, store))
            val account = SharedAccount("account", SharingSubscription("sub", "Netflix", "Netflix"), "Netflix", 1, "local@example.test", 5, 1, "2099-01-01", "active", verificationMode = "email", verificationLink = "https://example.test/s/configured")
            val detail = AccountDetail(account, listOf(Seat("seat-1", 1, "active", "Member", "2099-01-01")))
            assertEquals(setOf("public", "member"), api.links(detail).map { it.id }.toSet())
            api.generateLink(detail, "seat-1")
            val body = Json.parseToJsonElement(posted).jsonObject
            assertEquals("configured-mailbox", body.getValue("mailboxId").jsonPrimitive.content)
            assertEquals("seat-1", body.getValue("seatId").jsonPrimitive.content)
            assertEquals("30", body.getValue("windowMinutes").jsonPrimitive.content)
            assertEquals("[\"netflix-only\"]", body.getValue("folderIds").toString())
            assertFalse(body.containsKey("expiresAt"))
            api.revokeLink(account, "member")
            assertTrue(deleted.endsWith("/member"))
            assertTrue(runCatching { api.generateLink(detail.copy(account = account.copy(verificationMode = "none")), "seat-1") }.exceptionOrNull() is SharingConfigurationException)
        } finally {
            server.stop(0)
        }
    }
}
