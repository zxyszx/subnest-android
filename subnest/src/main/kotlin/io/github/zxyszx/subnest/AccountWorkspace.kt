package io.github.zxyszx.subnest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.component.LayerCard
import io.github.plaza.designsys.component.TonalTag
import io.github.zxyszx.subnest.data.AccountDetail
import io.github.zxyszx.subnest.data.SeatLink
import io.github.zxyszx.subnest.data.SeatUpdate
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeatSheet(detail: AccountDetail, busy: Boolean, error: String?, links: List<SeatLink>?, onDismiss: () -> Unit, onSave: (String, SeatUpdate, () -> Unit) -> Unit, onCopyPassword: () -> Unit, onLoadLinks: () -> Unit, onGenerateLink: (String) -> Unit, onRevokeLink: (String) -> Unit) {
    var tab by rememberSaveable(detail.account.id) { mutableIntStateOf(0) }
    var editingId by rememberSaveable(detail.account.id) { mutableStateOf<String?>(null) }
    var renewal by rememberSaveable(detail.account.id) { mutableStateOf(false) }
    var revokingId by rememberSaveable(detail.account.id) { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val labels = listOf(R.string.seat_tab, R.string.credentials_tab, R.string.links_tab, R.string.finance_tab)
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxHeight(0.95f)) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${detail.account.subscription.name} #${detail.account.accountNumber}", style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.seats, detail.account.occupiedSeats, detail.account.capacity, detail.account.available), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.close)) }
            }
            PrimaryTabRow(selectedTabIndex = tab) {
                labels.forEachIndexed { index, label ->
                    Tab(selected = tab == index, onClick = {
                        tab = index
                        if (index == 2) onLoadLinks()
                    }, text = { Text(stringResource(label)) })
                }
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (error != null) item { Text(error, color = MaterialTheme.colorScheme.error) }
                when (tab) {
                    0 -> items(detail.seats.sortedBy { it.seatNumber }, key = { it.id }) { seat ->
                        val vacant = seat.status in setOf("vacant", "archived") || seat.memberName.isNullOrBlank()
                        val expired = seat.expiresAt?.let { it < LocalDate.now().toString() } == true
                        LayerCard(Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(stringResource(R.string.seat_number, seat.seatNumber), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(seat.memberName?.takeIf { !vacant } ?: stringResource(R.string.unassigned), style = MaterialTheme.typography.titleMedium)
                                    }
                                    if (!vacant) TonalTag(text = stringResource(if (expired) R.string.expired else statusLabel(seat.status)), containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                                if (!vacant) {
                                    seat.contact?.takeIf { it.isNotBlank() }?.let { contact ->
                                        TextButton(onClick = { copyWorkspaceText(context, contact) }, contentPadding = PaddingValues(0.dp)) { Text(contact) }
                                    }
                                    Text(stringResource(R.string.expires, seat.expiresAt.orEmpty()), color = if (expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("${seat.monthlyPrice.orEmpty()} ${seat.currency.orEmpty()} / ${stringResource(R.string.monthly)}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                        seat.currentReceivable?.let { receipt -> Text(stringResource(if (receipt.status in setOf("paid", "waived")) R.string.paid else R.string.pending), color = MaterialTheme.colorScheme.primary) }
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (!vacant) {
                                        Button(onClick = {
                                            renewal = true
                                            editingId = seat.id
                                        }, enabled = !busy, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.renew)) }
                                    }
                                    OutlinedButton(onClick = {
                                        renewal = false
                                        editingId = seat.id
                                    }, enabled = !busy, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.edit_seat)) }
                                }
                            }
                        }
                    }

                    1 -> item {
                        LayerCard(Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(detail.account.loginAccount, style = MaterialTheme.typography.titleMedium)
                                OutlinedButton(onClick = { copyWorkspaceText(context, detail.account.loginAccount) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.copy_account)) }
                                if (detail.account.hasPassword) OutlinedButton(onClick = onCopyPassword, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.copy_password)) }
                                detail.account.verificationLink?.takeIf { it.isNotBlank() }?.let { link ->
                                    OutlinedButton(onClick = { copyWorkspaceText(context, link, sensitive = true) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.copy_link)) }
                                } ?: Text(stringResource(R.string.no_link), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    2 -> {
                        item {
                            Text(stringResource(R.string.links_note), style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = onLoadLinks, enabled = !busy) { Text(stringResource(R.string.reload_links)) }
                        }
                        items(detail.seats.filter { !it.memberName.isNullOrBlank() && it.status in setOf("active", "paused") }, key = { "link-${it.id}" }) { seat ->
                            val link = links?.firstOrNull { it.seatId == seat.id && it.valid }
                            val eligible = seat.expiresAt?.let { it >= LocalDate.now().toString() } ?: true
                            LayerCard(Modifier.fillMaxWidth()) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("${stringResource(R.string.seat_number, seat.seatNumber)} · ${seat.memberName}", style = MaterialTheme.typography.titleMedium)
                                    Text(stringResource(R.string.expires, seat.expiresAt.orEmpty()), style = MaterialTheme.typography.bodySmall)
                                    if (link != null) {
                                        Button(onClick = { copyWorkspaceText(context, link.url, sensitive = true) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.copy_link)) }
                                        TextButton(onClick = { revokingId = link.id }, enabled = !busy) { Text(stringResource(R.string.revoke_link)) }
                                    } else {
                                        Text(stringResource(R.string.no_seat_link), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Button(onClick = { onGenerateLink(seat.id) }, enabled = !busy && links != null && eligible) { Text(stringResource(R.string.generate_link)) }
                                    }
                                }
                            }
                        }
                    }

                    else -> item {
                        LayerCard(Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                // Show each currency independently; monthly allocation is not cash received.
                                MoneySummary(stringResource(R.string.monthly_revenue), detail.account.monthlyRevenueByCurrency)
                                MoneySummary(stringResource(R.string.outstanding), detail.account.outstandingAmountByCurrency)
                                Text(stringResource(R.string.due, detail.account.nextBillingDate))
                                detail.account.paymentMethod?.let { Text("${stringResource(R.string.payment_method)} · $it ${detail.account.cardLast4?.let { last -> "•••• $last" }.orEmpty()}") }
                            }
                        }
                    }
                }
            }
        }
    }
    detail.seats.firstOrNull { it.id == editingId }?.let { seat ->
        SeatEditor(seat, renewal, busy, error, { editingId = null }) { changes -> onSave(seat.id, changes) { editingId = null } }
    }
    if (revokingId != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) revokingId = null },
            title = { Text(stringResource(R.string.revoke_link_title)) },
            text = { Text(stringResource(R.string.revoke_link_note)) },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    revokingId?.let(onRevokeLink)
                    revokingId = null
                }) { Text(stringResource(R.string.revoke_link)) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { revokingId = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun MoneySummary(title: String, amounts: Map<String, String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        if (amounts.isEmpty()) Text(stringResource(R.string.no_amount))
        amounts.toSortedMap().forEach { (currency, amount) -> Text("$amount $currency", style = MaterialTheme.typography.titleLarge) }
    }
}
