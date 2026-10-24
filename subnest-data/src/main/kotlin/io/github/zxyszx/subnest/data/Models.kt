package io.github.zxyszx.subnest.data

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Serializable
data class User(val id: String, val name: String, val email: String)

@Serializable
data class LoginResult(
    val type: String,
    val user: User? = null,
    val ticketId: String? = null,
    val methods: List<String> = emptyList(),
)

@Serializable
data class Subscription(
    val id: String,
    val name: String,
    val price: String,
    val currency: String,
    val billingCycle: String,
    val nextBillingDate: String,
    val status: String,
    val autoRenew: Boolean,
    val autoCalculateNextBillingDate: Boolean,
    val startDate: String? = null,
    val platformName: String = "",
    val accountNumber: Int? = null,
    val familySharingLoginAccount: String = "",
    val customDays: Int? = null,
    val customCycleUnit: String? = null,
) {
    val platform: String get() = platformName.takeUnless { it.isBlank() || it == "__unbound__" } ?: name
    val canRenew: Boolean get() = status in setOf("active", "trial", "expired") && billingCycle != "one-time" && !autoRenew

    fun suggestedNextDate(today: LocalDate = LocalDate.now()): LocalDate {
        val original = LocalDate.parse(nextBillingDate)
        val anchor = if (autoCalculateNextBillingDate) LocalDate.parse(requireNotNull(startDate)) else original
        val threshold = maxOf(today, original)
        // Always advance from the original anchor, so Jan 31 -> Feb 28 -> Mar 31 does not drift.
        for (count in 1L..10000L) {
            val candidate = when (billingCycle) {
                "weekly" -> anchor.plusWeeks(count)

                "monthly" -> anchor.plusMonths(count)

                "quarterly" -> anchor.plusMonths(3 * count)

                "semi-annual" -> anchor.plusMonths(6 * count)

                "annual" -> anchor.plusYears(count)

                "custom" -> {
                    val step = requireNotNull(customDays).toLong()
                    require(step > 0)
                    when (customCycleUnit) {
                        "month" -> anchor.plusMonths(step * count)
                        "year" -> anchor.plusYears(step * count)
                        "week" -> anchor.plusWeeks(step * count)
                        "day" -> anchor.plusDays(step * count)
                        else -> error("Unsupported custom cycle")
                    }
                }

                else -> error("Unsupported billing cycle")
            }
            if (candidate > threshold) return candidate
        }
        error("Renewal advance limit exceeded")
    }
}

@Serializable
data class Subscriptions(val subscriptions: List<Subscription>, val total: Int)

@Serializable
data class SharingSubscription(val id: String, val name: String, val platformName: String)

@Serializable
data class SharedAccount(
    val id: String,
    val subscription: SharingSubscription,
    val name: String,
    val accountNumber: Int,
    val loginAccount: String,
    val capacity: Int,
    val occupiedSeats: Int,
    val nextBillingDate: String,
    val status: String,
    val verificationLink: String? = null,
    val verificationMode: String? = null,
    val monthlyRevenueByCurrency: Map<String, String> = emptyMap(),
    val outstandingAmountByCurrency: Map<String, String> = emptyMap(),
    val paymentMethod: String? = null,
    val cardLast4: String? = null,
    val hasPassword: Boolean = false,
) {
    val available: Int get() = (capacity - occupiedSeats).coerceAtLeast(0)
}

@Serializable
data class Accounts(val accounts: List<SharedAccount>, val total: Int)

@Serializable
data class Seat(
    val id: String,
    val seatNumber: Int,
    val status: String,
    val memberName: String? = null,
    val expiresAt: String? = null,
    val currency: String? = null,
    val monthlyPrice: String? = null,
    val contact: String? = null,
    val contactType: String? = null,
    val billingMonths: Int? = null,
    val startDate: String? = null,
    val notes: String? = null,
    val currentReceivable: Receivable? = null,
)

@Serializable
data class Receivable(val amount: String, val status: String, val periodStart: String, val periodEnd: String)

@Serializable
data class SeatUpdate(
    val memberName: String,
    val contact: String,
    val contactType: String,
    val monthlyPrice: String,
    val billingAmount: String,
    val currency: String,
    val billingMonths: Int,
    val startDate: String,
    val expiresAt: String,
    val status: String,
    val paymentStatus: String,
    val notes: String,
) {
    fun validate() {
        require(memberName.isNotBlank() && memberName.length <= 120)
        require(contact.length <= 320 && notes.length <= 5000)
        require(currency.matches(Regex("[A-Z]{3}")) && billingMonths in 1..120)
        listOf(billingAmount, monthlyPrice).forEach { amount ->
            require(amount.matches(Regex("[0-9]+(?:\\.[0-9]{1,6})?")))
            require(amount.toBigDecimal() <= "1000000000".toBigDecimal())
        }
        require(LocalDate.parse(expiresAt) > LocalDate.parse(startDate))
        require(status in setOf("active", "paused", "archived") && paymentStatus in setOf("pending", "paid"))
    }
}

@Serializable
data class AccountCredentials(val password: String)

data class SeatLink(val id: String, val seatId: String, val url: String, val valid: Boolean)

@Serializable
data class TotpLink(val id: String, val seatId: String, val path: String, val valid: Boolean)

@Serializable
data class TotpLinks(val links: List<TotpLink>)

@Serializable
data class InboxLink(
    val id: String,
    val shortUrl: String,
    val mailboxId: String,
    val seatId: String? = null,
    val folderIds: List<String>,
    val windowMinutes: Int,
    val status: String,
    val expiresAt: String? = null,
)

@Serializable
data class InboxLinks(val links: List<InboxLink>)

class SharingConfigurationException : IllegalStateException()

@Serializable
data class AccountDetail(val account: SharedAccount, val seats: List<Seat>)

enum class ListFilter { ALL, TODAY, UPCOMING, OVERDUE, VACANT, FULL }

fun matchesDate(date: String, filter: ListFilter, today: LocalDate): Boolean {
    val days = ChronoUnit.DAYS.between(today, LocalDate.parse(date))
    return when (filter) {
        ListFilter.TODAY -> days == 0L
        ListFilter.UPCOMING -> days in 0..7
        ListFilter.OVERDUE -> days < 0
        else -> true
    }
}

fun subscriptionsMatching(rows: List<Subscription>, query: String, platform: String?, filter: ListFilter, today: LocalDate): List<Subscription> =
    rows.filter {
        (platform == null || it.platform == platform) &&
            (
                if (query.trim().matches(Regex("[0-9]+"))) {
                    it.accountNumber?.toString() == query.trim()
                } else {
                    query.isBlank() || listOf(it.name, it.platform, it.accountNumber?.toString().orEmpty(), it.familySharingLoginAccount)
                        .any { value -> value.contains(query.trim(), ignoreCase = true) }
                }
                ) &&
            (filter == ListFilter.ALL || (it.status in setOf("active", "trial", "expired") && matchesDate(it.nextBillingDate, filter, today)))
    }.sortedWith(compareBy<Subscription> { it.status !in setOf("active", "trial", "expired") }.thenBy { it.nextBillingDate })

fun accountsMatching(rows: List<SharedAccount>, query: String, platform: String?, filter: ListFilter, today: LocalDate): List<SharedAccount> =
    rows.filter {
        (platform == null || it.subscription.platformName == platform) &&
            (
                if (query.trim().matches(Regex("[0-9]+"))) {
                    it.accountNumber.toString() == query.trim()
                } else {
                    query.isBlank() || listOf(it.name, it.subscription.name, it.loginAccount, it.accountNumber.toString(), it.subscription.platformName)
                        .any { value -> value.contains(query.trim(), ignoreCase = true) }
                }
                ) &&
            when (filter) {
                ListFilter.VACANT -> it.status == "active" && it.available > 0
                ListFilter.FULL -> it.status == "active" && it.capacity > 0 && it.occupiedSeats >= it.capacity
                else -> filter == ListFilter.ALL || (it.status == "active" && matchesDate(it.nextBillingDate, filter, today))
            }
    }.sortedBy { it.accountNumber }
