package io.github.zxyszx.subnest

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.zxyszx.subnest.data.AccountDetail
import io.github.zxyszx.subnest.data.ApiException
import io.github.zxyszx.subnest.data.LoginResult
import io.github.zxyszx.subnest.data.SharedAccount
import io.github.zxyszx.subnest.data.SubNestRepository
import io.github.zxyszx.subnest.data.Subscription
import io.github.zxyszx.subnest.data.User
import io.github.zxyszx.subnest.data.createSubNestRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WorkspaceState(
    val origin: String = "",
    val user: User? = null,
    val ticket: LoginResult? = null,
    val subscriptions: List<Subscription> = emptyList(),
    val accounts: List<SharedAccount> = emptyList(),
    val detail: AccountDetail? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val message: Int? = null,
)

class WorkspaceViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("subnest-settings", 0)
    private val mutable = MutableStateFlow(WorkspaceState(origin = preferences.getString("server", "").orEmpty()))
    val state = mutable.asStateFlow()
    private var repository: SubNestRepository? = null

    init {
        if (mutable.value.origin.isNotEmpty()) {
            work(restoring = true) {
                repository = newRepository(mutable.value.origin)
                acceptLogin(requireNotNull(repository).session())
            }
        }
    }

    private fun newRepository(origin: String): SubNestRepository {
        val app = getApplication<Application>()
        val debug = app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        return createSubNestRepository(app, origin, debug)
    }

    fun login(origin: String, email: String, password: String) = work {
        val next = newRepository(origin)
        // Never send a session from the previous server to the newly selected origin.
        if (origin.trimEnd('/') != mutable.value.origin.trimEnd('/')) next.clearSession()
        repository = next
        preferences.edit { putString("server", origin.trim().trimEnd('/')) }
        mutable.value = mutable.value.copy(origin = origin.trim().trimEnd('/'), ticket = null)
        acceptLogin(next.login(email.trim(), password))
    }

    fun verify(code: String, recovery: Boolean) = work {
        val ticket = requireNotNull(mutable.value.ticket?.ticketId)
        acceptLogin(requireNotNull(repository).verify(ticket, code.trim(), recovery))
    }

    private suspend fun acceptLogin(result: LoginResult) {
        when (result.type) {
            "mfa_required" -> {
                require(!result.ticketId.isNullOrBlank() && result.methods.isNotEmpty())
                mutable.value = mutable.value.copy(ticket = result)
            }

            "session" -> {
                mutable.value = mutable.value.copy(user = requireNotNull(result.user), ticket = null)
                loadLists()
            }

            else -> error("Unsupported authentication response")
        }
    }

    private suspend fun loadLists() {
        val api = requireNotNull(repository)
        val subscriptions = api.subscriptions()
        val accounts = api.accounts()
        mutable.value = mutable.value.copy(subscriptions = subscriptions, accounts = accounts)
    }

    fun refresh() = work { loadLists() }

    fun openAccount(id: String) = work {
        mutable.value = mutable.value.copy(detail = requireNotNull(repository).detail(id))
    }

    fun closeAccount() {
        mutable.value = mutable.value.copy(detail = null)
    }

    fun cancelMfa() {
        mutable.value = mutable.value.copy(ticket = null, error = null)
    }

    fun dismissFeedback() {
        mutable.value = mutable.value.copy(error = null, message = null)
    }

    fun renew(subscription: Subscription, nextDate: String) = work {
        requireNotNull(repository).renew(subscription, nextDate)
        // The write has succeeded even if the subsequent refresh fails. Never invite a duplicate write.
        mutable.value = mutable.value.copy(message = R.string.renewed)
        loadLists()
    }

    fun logout() = work {
        try {
            requireNotNull(repository).logout()
        } finally {
            mutable.value = WorkspaceState(origin = mutable.value.origin, busy = true)
        }
    }

    private fun work(restoring: Boolean = false, block: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, error = null, message = null)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (error is ApiException && error.status == 401 && (mutable.value.user != null || restoring)) {
                    withContext(Dispatchers.IO) { repository?.clearSession() }
                    mutable.value = WorkspaceState(origin = mutable.value.origin, busy = true)
                }
                val message = when {
                    restoring && error is ApiException && error.status == 401 -> null
                    error is ApiException -> error.message
                    error is IllegalArgumentException -> getApplication<Application>().getString(R.string.invalid_input)
                    else -> getApplication<Application>().getString(R.string.network_error)
                }
                mutable.value = mutable.value.copy(error = message)
            } finally {
                mutable.value = mutable.value.copy(busy = false)
            }
        }
    }
}
