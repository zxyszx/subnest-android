package io.github.zxyszx.subnest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import io.github.zxyszx.subnest.data.Subscription
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenewDialog(row: Subscription, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val date by rememberSaveable(row.id) {
        mutableStateOf(runCatching { row.suggestedNextDate().toString() }.getOrDefault(""))
    }
    val parsed = runCatching { LocalDate.parse(date) }.getOrNull()
    val valid = parsed != null && parsed > LocalDate.parse(row.nextBillingDate)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.renew_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${row.platform} #${row.accountNumber ?: ""}", style = MaterialTheme.typography.titleMedium)
                Text("${row.price} ${row.currency}")
                Text(stringResource(R.string.renew_notice))
                OutlinedTextField(
                    date,
                    {},
                    Modifier.fillMaxWidth(),
                    singleLine = true,
                    readOnly = true,
                    label = { Text(stringResource(R.string.next_date)) },
                    isError = !valid,
                    supportingText = { if (!valid) Text(stringResource(R.string.date_error)) },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(date) }, enabled = valid) { Text(stringResource(R.string.confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
