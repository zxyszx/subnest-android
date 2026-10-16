package io.github.zxyszx.subnest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.zxyszx.subnest.data.ListFilter
import io.github.zxyszx.subnest.data.SharedAccount
import io.github.zxyszx.subnest.data.Subscription
import io.github.zxyszx.subnest.data.accountsMatching
import io.github.zxyszx.subnest.data.subscriptionsMatching
import java.time.LocalDate

@Composable
fun SubscriptionScreen(rows: List<Subscription>, busy: Boolean, onRenew: (Subscription, String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var platform by rememberSaveable { mutableStateOf<String?>(null) }
    var filter by rememberSaveable { mutableStateOf(ListFilter.ALL) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val visible = subscriptionsMatching(rows, query, platform, filter, LocalDate.now())
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ListControls(
                query,
                { query = it },
                rows.map { it.platform }.distinct().sorted(),
                platform,
                { platform = it },
                listOf(ListFilter.ALL, ListFilter.TODAY, ListFilter.UPCOMING, ListFilter.OVERDUE),
                filter,
                { filter = it },
            )
        }
        item { Text(stringResource(R.string.count, visible.size), style = MaterialTheme.typography.labelLarge) }
        if (visible.isEmpty() && !busy) {
            item {
                EmptyRecords {
                    query = ""
                    platform = null
                    filter = ListFilter.ALL
                }
            }
        }
        items(visible, key = { it.id }) { row ->
            OutlinedCard(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("${row.platform}${row.accountNumber?.let { " #$it" }.orEmpty()}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(statusLabel(row.status)), style = MaterialTheme.typography.labelMedium)
                    }
                    if (row.familySharingLoginAccount.isNotBlank()) Text(row.familySharingLoginAccount, style = MaterialTheme.typography.bodyMedium)
                    Text("${row.price} ${row.currency} / ${stringResource(cycleLabel(row.billingCycle))}", style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.due, row.nextBillingDate), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (row.canRenew) {
                        Button(onClick = { selectedId = row.id }, enabled = !busy) {
                            Text(stringResource(R.string.renew))
                        }
                    }
                }
            }
        }
    }
    rows.firstOrNull { it.id == selectedId }?.let { row ->
        RenewDialog(row, { selectedId = null }) { date ->
            selectedId = null
            onRenew(row, date)
        }
    }
}

@Composable
fun SharingScreen(rows: List<SharedAccount>, busy: Boolean, onAccount: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var platform by rememberSaveable { mutableStateOf<String?>(null) }
    var filter by rememberSaveable { mutableStateOf(ListFilter.ALL) }
    val visible = accountsMatching(rows, query, platform, filter, LocalDate.now())
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ListControls(
                query,
                { query = it },
                rows.map { it.subscription.platformName }.distinct().sorted(),
                platform,
                { platform = it },
                listOf(ListFilter.ALL, ListFilter.VACANT, ListFilter.FULL, ListFilter.UPCOMING, ListFilter.OVERDUE),
                filter,
                { filter = it },
            )
        }
        item { Text(stringResource(R.string.count, visible.size), style = MaterialTheme.typography.labelLarge) }
        if (visible.isEmpty() && !busy) {
            item {
                EmptyRecords {
                    query = ""
                    platform = null
                    filter = ListFilter.ALL
                }
            }
        }
        items(visible, key = { it.id }) { row ->
            OutlinedCard(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${row.subscription.platformName} #${row.accountNumber}", style = MaterialTheme.typography.titleMedium)
                    Text(row.loginAccount)
                    Text(
                        stringResource(R.string.seats, row.occupiedSeats, row.capacity, row.available),
                        color = if (row.available > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(stringResource(R.string.due, row.nextBillingDate), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { onAccount(row.id) }, enabled = !busy) { Text(stringResource(R.string.manage)) }
                }
            }
        }
    }
}

@Composable
private fun EmptyRecords(onReset: () -> Unit) {
    Column(Modifier.padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.empty))
        TextButton(onClick = onReset) { Text(stringResource(R.string.reset)) }
    }
}

fun statusLabel(status: String): Int = when (status) {
    "active" -> R.string.active
    "trial" -> R.string.trial
    "paused" -> R.string.paused
    "expired" -> R.string.expired
    "archived" -> R.string.archived
    "vacant" -> R.string.unassigned
    else -> R.string.cancelled
}

private fun cycleLabel(cycle: String): Int = when (cycle) {
    "weekly" -> R.string.weekly
    "monthly" -> R.string.monthly
    "quarterly" -> R.string.quarterly
    "semi-annual" -> R.string.semi_annual
    "annual" -> R.string.annual
    "one-time" -> R.string.one_time
    else -> R.string.custom
}
