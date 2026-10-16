package io.github.zxyszx.subnest

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubNestRoot(model: WorkspaceViewModel, state: WorkspaceState) {
    var tab by rememberSaveable(state.user?.id) { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val savedScreens = rememberSaveableStateHolder()
    LaunchedEffect(state.message, state.error, state.user) {
        if (state.user != null) {
            state.message?.let { snackbar.showSnackbar(resources.getString(it)) }
            state.error?.let { snackbar.showSnackbar(it) }
            model.dismissFeedback()
        }
    }
    BackHandler(state.user != null && tab != 0 && state.detail == null) { tab = 0 }
    val labels = listOf(R.string.subscriptions, R.string.sharing, R.string.account)
    val icons = listOf(Icons.AutoMirrored.Filled.List, Icons.Default.AccountBox, Icons.Default.AccountCircle)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.user == null) stringResource(R.string.app_name) else stringResource(labels[tab])) },
                actions = {
                    if (state.user != null && tab != 2) {
                        IconButton(onClick = model::refresh, enabled = !state.busy) {
                            Icon(Icons.Default.Refresh, stringResource(R.string.refresh))
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (state.user != null) {
                NavigationBar {
                    labels.forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Icon(icons[index], null) },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.fillMaxWidth().height(4.dp)) {
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (state.user == null) {
                LoginScreen(state, model::login, model::verify, model::cancelMfa)
            } else {
                savedScreens.SaveableStateProvider("${state.user.id}:$tab") {
                    when (tab) {
                        0 -> SubscriptionScreen(state.subscriptions, state.busy, model::renew)
                        1 -> SharingScreen(state.accounts, state.busy, model::openAccount)
                        else -> AccountScreen(state, model::logout)
                    }
                }
            }
        }
    }
    state.detail?.let { SeatSheet(it, model::closeAccount) }
}
