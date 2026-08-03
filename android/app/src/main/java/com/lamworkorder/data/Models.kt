package com.lamworkorder.data

import kotlinx.serialization.Serializable

@Serializable
data class WorkOrder(
    val id: String,
    val workOrderNumber: String,
    val title: String,
    val description: String,
    val requestedBy: String,
    val location: String,
    val priority: String,
    val status: String,
    val assignedTo: String? = null,
    val dueAt: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val statusNote: String? = null,
)

@Serializable
data class CreateWorkOrder(
    val title: String,
    val description: String,
    val requestedBy: String,
    val location: String,
    val priority: String = "Normal",
    val assignedTo: String? = null,
)

