package io.github.zxyszx.subnest.data

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WorkspaceRulesTest {
    private val today = LocalDate.of(2026, 10, 17)

    private fun subscription(id: String, date: String, status: String = "active") = Subscription(
        id = id, name = "Netflix", price = "45", currency = "CNY", billingCycle = "monthly",
        nextBillingDate = date, status = status, autoRenew = false, autoCalculateNextBillingDate = false,
        platformName = "Netflix", accountNumber = id.toInt(),
    )

    @Test
    fun `today and overdue never return the seven day collection`() {
        val rows = listOf(
            subscription("10", "2026-10-17"),
            subscription("11", "2026-10-16", "expired"),
            subscription("12", "2026-10-24"),
            subscription("13", "2026-10-25"),
            subscription("14", "2026-10-17", "paused"),
        )
        assertEquals(listOf("10"), subscriptionsMatching(rows, "", null, ListFilter.TODAY, today).map { it.id })
        assertEquals(listOf("11"), subscriptionsMatching(rows, "", null, ListFilter.OVERDUE, today).map { it.id })
        assertEquals(listOf("10", "12"), subscriptionsMatching(rows, "", null, ListFilter.UPCOMING, today).map { it.id })
        assertEquals(listOf("10"), subscriptionsMatching(rows, "10", "Netflix", ListFilter.ALL, today).map { it.id })
        assertTrue(subscriptionsMatching(rows, "10", "YouTube", ListFilter.ALL, today).isEmpty())
    }

    @Test
    fun `renewal catches up overdue cycles without losing month end anchor`() {
        val row = subscription("1", "2026-02-28").copy(startDate = "2026-01-31", autoCalculateNextBillingDate = true)
        assertEquals(LocalDate.of(2026, 3, 31), row.suggestedNextDate(LocalDate.of(2026, 3, 1)))
        assertEquals(LocalDate.of(2026, 10, 31), row.suggestedNextDate(today))
        assertEquals(LocalDate.of(2026, 11, 17), subscription("2", "2026-10-17").suggestedNextDate(today))
        assertEquals(
            LocalDate.of(2028, 2, 29),
            row.copy(
                startDate = "2024-02-29",
                nextBillingDate = "2027-02-28",
                billingCycle = "annual",
            ).suggestedNextDate(LocalDate.of(2027, 2, 28)),
        )
    }

    @Test
    fun `custom cycle uses its unit and rejects zero length`() {
        val row = subscription("1", "2026-10-17").copy(billingCycle = "custom", customDays = 2, customCycleUnit = "month")
        assertEquals(LocalDate.of(2026, 12, 17), row.suggestedNextDate(today))
        assertThrows(IllegalArgumentException::class.java) { row.copy(customDays = 0).suggestedNextDate(today) }
        assertFalse(row.copy(autoRenew = true).canRenew)
    }

    @Test
    fun `vacancy ignores paused accounts and full excludes zero capacity`() {
        val base = SharedAccount("a", SharingSubscription("s", "Netflix", "Netflix"), "Netflix", 10, "test@example.com", 5, 4, "2026-10-17", "active")
        val rows = listOf(base, base.copy(id = "b", occupiedSeats = 5), base.copy(id = "c", status = "paused"), base.copy(id = "d", capacity = 0, occupiedSeats = 0))
        assertEquals(listOf("a"), accountsMatching(rows, "", null, ListFilter.VACANT, today).map { it.id })
        assertEquals(listOf("b"), accountsMatching(rows, "", null, ListFilter.FULL, today).map { it.id })
    }

    @Test
    fun `server requires HTTPS origin without userinfo or paths`() {
        assertEquals("https://dingyue.xzys.me/", serverOrigin("https://dingyue.xzys.me", false).toString())
        listOf("http://example.com", "https://name:secret@example.com", "https://example.com/sharing", "https://example.com/?a=b").forEach {
            assertThrows(IllegalArgumentException::class.java) { serverOrigin(it, false) }
        }
        assertEquals("localhost", serverOrigin("http://localhost:43210", true).host)
        assertThrows(IllegalArgumentException::class.java) { serverOrigin("http://localhost:43210", false) }
    }

    @Test
    fun `persistent cookies cannot move to another server or port and logout clears them`() {
        val store = MemoryCookies()
        val origin = "https://first.example/".toHttpUrl()
        val jar = SessionCookies(origin, store)
        jar.saveFromResponse(origin, listOf(Cookie.Builder().name("renewlet_csrf").value("test-token").hostOnlyDomain(origin.host).path("/").build()))
        assertEquals("test-token", SessionCookies(origin, store).csrf())
        assertTrue(jar.loadForRequest("https://second.example/".toHttpUrl()).isEmpty())
        assertTrue(SessionCookies("https://second.example/".toHttpUrl(), store).loadForRequest("https://second.example/".toHttpUrl()).isEmpty())
        assertTrue(SessionCookies("https://first.example:444/".toHttpUrl(), store).loadForRequest("https://first.example:444/".toHttpUrl()).isEmpty())
        jar.clear()
        assertTrue(SessionCookies(origin, store).loadForRequest(origin).isEmpty())
    }
}

private class MemoryCookies : CookieStorage {
    private var value = ""
    override fun read() = value
    override fun write(value: String) {
        this.value = value
    }
}
