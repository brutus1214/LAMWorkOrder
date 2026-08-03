package com.lamworkorder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lamworkorder.data.CreateWorkOrder
import com.lamworkorder.data.WorkOrder
import com.lamworkorder.data.WorkOrderApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class QueueState(
    val orders: List<WorkOrder> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

class WorkOrderViewModel(private val api: WorkOrderApi = WorkOrderApi.create()) : ViewModel() {
    private val _state = MutableStateFlow(QueueState())
    val state: StateFlow<QueueState> = _state.asStateFlow()

    init { refresh() }

    fun refresh(search: String? = null) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        runCatching { api.list(search = search?.takeIf { it.isNotBlank() }) }
            .onSuccess { _state.value = QueueState(orders = it) }
            .onFailure { _state.value = QueueState(error = it.message ?: "Unable to load queue") }
    }

    fun create(request: CreateWorkOrder, onComplete: () -> Unit) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        runCatching { api.create(request) }
            .onSuccess { refresh(); onComplete() }
            .onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
    }
}

