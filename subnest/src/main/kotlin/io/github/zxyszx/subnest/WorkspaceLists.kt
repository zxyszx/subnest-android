package io.github.zxyszx.subnest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.component.LayerCard
import io.github.plaza.designsys.component.TonalTag
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
            LayerCard(
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(0.dp),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        PlatformAvatar(row.platform)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${row.name.trim().ifBlank { row.platform }}${row.accountNumber?.let { " #$it" }.orEmpty()}", style = MaterialTheme.typography.titleMedium)
                            if (row.platform != row.name.trim()) Text(row.platform, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (row.familySharingLoginAccount.isNotBlank()) Text(row.familySharingLoginAccount, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        StatusPill(stringResource(statusLabel(row.status)))
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${row.price} ${row.currency} / ${stringResource(cycleLabel(row.billingCycle))}", style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.due, row.nextBillingDate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (row.canRenew) {
                            Button(onClick = { selectedId = row.id }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.renew))
                            }
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
            LayerCard(
                Modifier.fillMaxWidth(),
                onClick = if (busy) {
                    null
                } else {
                    { onAccount(row.id) }
                },
                contentPadding = PaddingValues(0.dp),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        PlatformAvatar(row.subscription.platformName)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${row.subscription.platformName} #${row.accountNumber}", style = MaterialTheme.typography.titleMedium)
                            Text(row.loginAccount, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StatusPill(stringResource(if (row.available > 0) R.string.vacant else R.string.full))
                    }
                    Text(
                        stringResource(R.string.seats, row.occupiedSeats, row.capacity, row.available),
                        color = if (row.available > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    LinearProgressIndicator(progress = { if (row.capacity > 0) (row.occupiedSeats.toFloat() / row.capacity).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth())
                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.due, row.nextBillingDate), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Button(onClick = { onAccount(row.id) }, enabled = !busy) { Text(stringResource(R.string.manage)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlatformAvatar(platform: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Text(platform.take(2).uppercase(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun StatusPill(label: String) {
    TonalTag(text = label, containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
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
