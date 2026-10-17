package io.github.zxyszx.subnest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun AccountScreen(state: WorkspaceState, onLogout: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(state.user?.name.orEmpty(), style = MaterialTheme.typography.titleLarge)
        Text(state.user?.email.orEmpty())
        Text(state.origin, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        Text(stringResource(R.string.version, BuildConfig.VERSION_NAME))
        Text(stringResource(R.string.license), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.license_note))
        TextButton(onClick = { uri.openUri("https://github.com/zxyszx/subnest-android") }) { Text(stringResource(R.string.source)) }
        HorizontalDivider()
        OutlinedButton(onClick = { confirming = true }, enabled = !state.busy) { Text(stringResource(R.string.logout)) }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.logout_confirm)) },
            text = { Text(stringResource(R.string.logout_note)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onLogout()
                }) { Text(stringResource(R.string.sign_out)) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
