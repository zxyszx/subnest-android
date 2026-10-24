package io.github.zxyszx.subnest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import io.github.zxyszx.subnest.data.Seat
import io.github.zxyszx.subnest.data.SeatUpdate
import java.math.RoundingMode
import java.time.LocalDate

@Composable
fun SeatEditor(seat: Seat, renewal: Boolean, busy: Boolean, error: String?, onDismiss: () -> Unit, onSave: (SeatUpdate) -> Unit) {
    var member by rememberSaveable(seat.id, renewal) { mutableStateOf(seat.memberName.orEmpty()) }
    var contact by rememberSaveable(seat.id, renewal) { mutableStateOf(seat.contact.orEmpty()) }
    var months by rememberSaveable(seat.id, renewal) { mutableStateOf((seat.billingMonths ?: 1).toString()) }
    var currency by rememberSaveable(seat.id, renewal) { mutableStateOf(seat.currency ?: "CNY") }
    var amount by rememberSaveable(seat.id, renewal) {
        mutableStateOf(seat.currentReceivable?.amount ?: seat.monthlyPrice?.toBigDecimalOrNull()?.multiply((seat.billingMonths ?: 1).toBigDecimal())?.stripTrailingZeros()?.toPlainString().orEmpty())
    }
    var start by rememberSaveable(seat.id, renewal) {
        mutableStateOf(if (renewal) maxOf(seat.expiresAt.orEmpty(), LocalDate.now().toString()) else seat.currentReceivable?.periodStart ?: seat.startDate ?: LocalDate.now().toString())
    }
    var end by rememberSaveable(seat.id, renewal) {
        mutableStateOf(if (renewal) LocalDate.parse(start).plusMonths((seat.billingMonths ?: 1).toLong()).toString() else seat.currentReceivable?.periodEnd ?: seat.expiresAt ?: LocalDate.parse(start).plusMonths(1).toString())
    }
    var paid by rememberSaveable(seat.id, renewal) { mutableStateOf(!renewal && seat.currentReceivable?.status in setOf("paid", "waived")) }
    var notes by rememberSaveable(seat.id, renewal) { mutableStateOf(seat.notes.orEmpty()) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val changes = runCatching {
        SeatUpdate(
            member.trim(), contact.trim(), seat.contactType ?: if (contact.isBlank()) "" else "other",
            amount.toBigDecimal().divide(months.toBigDecimal(), 6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(),
            amount.trim(), currency.trim().uppercase(), months.toInt(), start.trim(), end.trim(),
            if (seat.status in setOf("vacant", "archived")) "active" else seat.status,
            if (paid) "paid" else "pending", notes,
        ).also { it.validate() }
    }.getOrNull()
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(if (renewal) R.string.renew_member else R.string.edit_seat) + " · ${seat.seatNumber}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.seat_form_hint), style = MaterialTheme.typography.bodySmall)
                SeatField(R.string.member_name, member, { member = it }, busy)
                SeatField(R.string.contact, contact, { contact = it }, busy)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(amount, { amount = it }, Modifier.weight(2f), enabled = !busy, label = { Text(stringResource(R.string.billing_amount)) }, singleLine = true)
                    OutlinedTextField(currency, { currency = it }, Modifier.weight(1f), enabled = !busy, label = { Text(stringResource(R.string.currency)) }, singleLine = true)
                }
                SeatField(R.string.billing_months, months, { value ->
                    months = value
                    val count = value.toLongOrNull()
                    if (renewal && count != null && count in 1..120) runCatching { LocalDate.parse(start).plusMonths(count).toString() }.getOrNull()?.let { end = it }
                }, busy)
                SeatField(R.string.start_date, start, { start = it }, busy)
                SeatField(R.string.end_date, end, { end = it }, busy)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !paid, onClick = { paid = false }, enabled = !busy, label = { Text(stringResource(R.string.pending)) })
                    FilterChip(selected = paid, onClick = { paid = true }, enabled = !busy, label = { Text(stringResource(R.string.paid)) })
                }
                SeatField(R.string.notes, notes, { notes = it }, busy, singleLine = false)
                if (attempted && changes == null) Text(stringResource(R.string.seat_form_invalid), color = MaterialTheme.colorScheme.error)
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                attempted = true
                changes?.let(onSave)
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun SeatField(label: Int, value: String, onChange: (String) -> Unit, busy: Boolean, singleLine: Boolean = true) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), enabled = !busy, singleLine = singleLine, label = { Text(stringResource(label)) })
}
