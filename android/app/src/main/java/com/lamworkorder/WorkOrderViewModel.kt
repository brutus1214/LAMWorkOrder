package com.lamworkorder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lamworkorder.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MultipartBody

data class QueueState(
    val orders: List<WorkOrder> = emptyList(), val user: User? = null,
    val users: List<User> = emptyList(), val assignees: List<User> = emptyList(),
    val notificationRecipients: List<User> = emptyList(),
    val loading: Boolean = false, val error: String? = null,
)

class WorkOrderViewModel(private val api: WorkOrderApi = WorkOrderApi.create()) : ViewModel() {
    private val _state = MutableStateFlow(QueueState())
    val state: StateFlow<QueueState> = _state.asStateFlow()
    private var token: String? = null
    private fun auth() = "Bearer ${token ?: error("Not signed in")}"

    fun login(username: String, password: String) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        runCatching { api.login(LoginRequest(username.trim(), password)) }
            .onSuccess { token = it.token; _state.value = QueueState(user = it.user); refresh(); loadAssignees() }
            .onFailure { _state.value = QueueState(error = it.message ?: "Unable to sign in") }
    }

    fun register(request: RegistrationRequest) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        runCatching { api.register(request) }
            .onSuccess { token = it.token; _state.value = QueueState(user = it.user); refresh(); loadAssignees() }
            .onFailure { _state.value = QueueState(error = it.message ?: "Unable to create user") }
    }

    fun logout() { token = null; _state.value = QueueState() }

    fun refresh(search: String? = null) = viewModelScope.launch {
        if (token == null) return@launch
        _state.value = _state.value.copy(loading = true, error = null)
        runCatching { api.list(auth(), search = search?.takeIf(String::isNotBlank)) }
            .onSuccess { _state.value = _state.value.copy(orders = it, loading = false) }
            .onFailure { _state.value = _state.value.copy(loading = false, error = it.message ?: "Unable to load queue") }
    }

    fun create(
        request: CreateWorkOrder,
        attachments: List<MultipartBody.Part> = emptyList(),
        done: (WorkOrder) -> Unit,
        failed: () -> Unit = {},
    ) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        runCatching {
            val created = api.create(auth(), request)
            val uploaded = if (attachments.isNotEmpty()) {
                runCatching { api.upload(auth(), created.id, attachments) }
            } else {
                Result.success(emptyList())
            }
            created.copy(attachments = uploaded.getOrDefault(emptyList())) to uploaded.exceptionOrNull()
        }
            .onSuccess { (created, attachmentError) ->
                _state.value = _state.value.copy(
                    loading = false,
                    error = attachmentError?.let { "Work order created, but attachments failed to upload." },
                )
                done(created)
                refresh()
            }
            .onFailure {
                _state.value = _state.value.copy(loading = false, error = it.message)
                failed()
            }
    }
    fun update(id: String, request: UpdateWorkOrder, done: () -> Unit) = perform({ api.update(auth(), id, request) }, done)
    fun updateStatus(id: String, status: String, note: String?, done: () -> Unit) = perform({ api.updateStatus(auth(), id, StatusUpdate(status, note)) }, done)

    fun deleteWorkOrder(id: String, done: () -> Unit) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        runCatching {
            val response = api.deleteWorkOrder(auth(), id)
            if (!response.isSuccessful) error("Unable to delete work order")
        }
            .onSuccess {
                _state.value = _state.value.copy(loading = false)
                done()
                refresh()
            }
            .onFailure {
                _state.value = _state.value.copy(loading = false, error = it.message)
            }
    }

    fun updateProfile(name: String, email: String?, done: () -> Unit) = viewModelScope.launch {
        runCatching { api.updateProfile(auth(), ProfileUpdate(name, email?.takeIf(String::isNotBlank))) }
            .onSuccess { _state.value = _state.value.copy(user = it, error = null); done() }
            .onFailure { _state.value = _state.value.copy(error = it.message) }
    }

    fun loadUsers() = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        runCatching { api.users(auth()) }
            .onSuccess { _state.value = _state.value.copy(users = it, loading = false) }
            .onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
    }

    fun updateUser(id: String, request: UserAdminUpdate, done: () -> Unit) = viewModelScope.launch {
        runCatching { api.updateUser(auth(), id, request) }
            .onSuccess { loadUsers(); done() }
            .onFailure { _state.value = _state.value.copy(error = it.message) }
    }

    fun resetPassword(id: String, password: String, done: () -> Unit) = viewModelScope.launch {
        runCatching { api.resetPassword(auth(), id, PasswordReset(password)) }
            .onSuccess { done() }
            .onFailure { _state.value = _state.value.copy(error = it.message) }
    }

    fun upload(id: String, parts: List<MultipartBody.Part>, done: () -> Unit) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        runCatching { api.upload(auth(), id, parts) }
            .onSuccess { refresh(); done() }
            .onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
    }

    fun loadAssignees() = viewModelScope.launch {
        if (token == null) return@launch
        runCatching { api.assignees(auth()) }
            .onSuccess { _state.value = _state.value.copy(assignees = it) }
            .onFailure {
                _state.value = _state.value.copy(
                    error = "Unable to load assignees. Restart the backend, then tap Refresh.",
                )
            }
    }

    fun loadNotificationRecipients(storeNumber: Int) = viewModelScope.launch {
        if (token == null) return@launch
        runCatching { api.notificationRecipients(auth(), storeNumber) }
            .onSuccess { _state.value = _state.value.copy(notificationRecipients = it) }
    }

    suspend fun attachmentBytes(id: String): ByteArray = api.attachmentContent(auth(), id).bytes()

    private fun perform(block: suspend () -> WorkOrder, done: () -> Unit) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        runCatching { block() }
            .onSuccess { refresh(); done() }
            .onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
    }
}
