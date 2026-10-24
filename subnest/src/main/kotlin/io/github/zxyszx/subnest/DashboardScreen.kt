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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.component.LayerCard
import io.github.zxyszx.subnest.data.ListFilter
import io.github.zxyszx.subnest.data.subscriptionsMatching
import java.time.LocalDate

@Composable
fun DashboardScreen(state: WorkspaceState, onSubscriptions: (ListFilter) -> Unit, onSharing: (ListFilter) -> Unit) {
    val today = LocalDate.now()
    val upcoming = subscriptionsMatching(state.subscriptions, "", null, ListFilter.UPCOMING, today)
    val overdue = subscriptionsMatching(state.subscriptions, "", null, ListFilter.OVERDUE, today)
    val dueToday = subscriptionsMatching(state.subscriptions, "", null, ListFilter.TODAY, today)
    val available = state.accounts.filter { it.status == "active" }.sumOf { it.available }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.dashboard_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.dashboard_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.subscriptions_total, state.subscriptions.size, state.accounts.size), style = MaterialTheme.typography.labelLarge)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TaskCard(stringResource(R.string.today), dueToday.size.toString(), Modifier.weight(1f)) { onSubscriptions(ListFilter.TODAY) }
                TaskCard(stringResource(R.string.overdue), overdue.size.toString(), Modifier.weight(1f)) { onSubscriptions(ListFilter.OVERDUE) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TaskCard(stringResource(R.string.upcoming), upcoming.size.toString(), Modifier.weight(1f)) { onSubscriptions(ListFilter.UPCOMING) }
                TaskCard(stringResource(R.string.vacant), available.toString(), Modifier.weight(1f)) { onSharing(ListFilter.VACANT) }
            }
        }
        item { Text(stringResource(R.string.next_tasks), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium) }
        if (upcoming.isEmpty() && overdue.isEmpty()) item { Text(stringResource(R.string.no_tasks), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items((overdue + upcoming).distinctBy { it.id }.take(5), key = { it.id }) { row ->
            LayerCard(Modifier.fillMaxWidth(), onClick = { onSubscriptions(if (row.nextBillingDate < today.toString()) ListFilter.OVERDUE else ListFilter.UPCOMING) }) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${row.name} #${row.accountNumber ?: ""}", style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.due, row.nextBillingDate), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${row.price} ${row.currency}")
                }
            }
        }
        item { TextButton(onClick = { onSubscriptions(ListFilter.ALL) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.subscriptions)) } }
    }
}

@Composable
private fun TaskCard(title: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    LayerCard(modifier, onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}
