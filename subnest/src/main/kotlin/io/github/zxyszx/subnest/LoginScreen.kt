package io.github.zxyszx.subnest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(
    state: WorkspaceState,
    onLogin: (String, String, String) -> Unit,
    onVerify: (String, Boolean) -> Unit,
    onCancelMfa: () -> Unit,
) {
    var origin by rememberSaveable { mutableStateOf(state.origin.ifBlank { "https://dingyue.xzys.me" }) }
    var email by rememberSaveable { mutableStateOf("") }
    // Secrets must not enter Android saved-instance state or preferences.
    var password by remember { mutableStateOf("") }
    var code by remember(state.ticket?.ticketId) { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var recovery by remember(state.ticket?.ticketId) {
        mutableStateOf(state.ticket?.methods?.contains("totp") == false)
    }
    Column(
        Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(if (state.ticket == null) R.string.login else R.string.verify), style = MaterialTheme.typography.headlineSmall)
            if (state.ticket == null) {
                OutlinedTextField(
                    origin,
                    { origin = it },
                    Modifier.fillMaxWidth(),
                    enabled = !state.busy,
                    label = { Text(stringResource(R.string.server)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(
                    email,
                    { email = it },
                    Modifier.fillMaxWidth(),
                    enabled = !state.busy,
                    label = { Text(stringResource(R.string.email)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                OutlinedTextField(
                    password, { password = it }, Modifier.fillMaxWidth(), enabled = !state.busy,
                    label = { Text(stringResource(R.string.password)) }, singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        TextButton(onClick = { visible = !visible }) {
                            Text(stringResource(if (visible) R.string.hide_password else R.string.show_password))
                        }
                    },
                )
                Button(
                    onClick = {
                        onLogin(origin, email, password)
                        password = ""
                    },
                    enabled = !state.busy && origin.isNotBlank() && email.isNotBlank() && password.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.login)) }
            } else {
                OutlinedTextField(
                    code,
                    { code = it },
                    Modifier.fillMaxWidth(),
                    enabled = !state.busy,
                    label = { Text(stringResource(R.string.code)) },
                    singleLine = true,
                )
                if (state.ticket.methods.containsAll(listOf("totp", "recovery_code"))) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(recovery, { recovery = it }, enabled = !state.busy)
                        Text(stringResource(R.string.recovery))
                    }
                }
                Button(onClick = {
                    onVerify(code, recovery)
                    code = ""
                }, enabled = !state.busy && code.isNotBlank()) {
                    Text(stringResource(R.string.verify))
                }
                TextButton(onClick = onCancelMfa, enabled = !state.busy) { Text(stringResource(R.string.back_login)) }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
