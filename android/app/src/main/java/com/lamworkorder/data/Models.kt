package com.lamworkorder.data

import kotlinx.serialization.Serializable

@Serializable
data class WorkOrder(
    val id: String,
    val workOrderNumber: String,
    val storeNumber: Int,
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
    val attachments: List<Attachment> = emptyList(),
)

@Serializable
data class CreateWorkOrder(
    val storeNumber: Int,
    val title: String,
    val description: String,
    val requestedBy: String,
    val location: String,
    val priority: String = "Normal",
    val assignedTo: String? = null,
    val dueAt: String? = null,
)

@Serializable data class Attachment(val id: String, val originalName: String, val contentType: String, val sizeBytes: Long, val createdAt: String, val url: String)
@Serializable data class User(val id: String, val username: String, val displayName: String, val email: String? = null, val role: String)
@Serializable data class LoginRequest(val username: String, val password: String)
@Serializable data class LoginResponse(val token: String, val user: User)
@Serializable data class ProfileUpdate(val displayName: String, val email: String? = null)
@Serializable data class StatusUpdate(val status: String, val note: String? = null)
@Serializable data class UpdateWorkOrder(
    val storeNumber: Int, val title: String, val description: String, val requestedBy: String,
    val location: String, val priority: String, val assignedTo: String? = null,
    val dueAt: String? = null, val status: String, val statusNote: String? = null,
)
