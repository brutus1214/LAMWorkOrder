package com.lamworkorder

import com.lamworkorder.data.User
import com.lamworkorder.data.WorkOrder

internal fun workOrderShareSubject(order: WorkOrder): String =
    "Work Order ${order.workOrderNumber}"

internal fun workOrderShareText(
    order: WorkOrder,
    title: String = order.title,
    description: String = order.description,
    requestedBy: String = order.requestedBy,
    location: String = order.location,
    priority: String = order.priority,
    assignedTo: String? = order.assignedTo,
    status: String = order.status,
    statusNote: String? = order.statusNote,
): String = buildList {
    add("Work Order ${order.workOrderNumber}")
    add("Created: ${formatWorkOrderCreatedDate(order.createdAt)}")
    add("Store: ${order.storeNumber}")
    add("Location: $location")
    add("Status: ${displayStatus(status)}")
    add("Priority: $priority")
    add("Title: $title")
    add("Description: $description")
    add("Requested by: $requestedBy")
    assignedTo?.takeIf(String::isNotBlank)?.let { add("Assigned to: $it") }
    statusNote?.takeIf(String::isNotBlank)?.let { add("Status note: $it") }
}.joinToString("\n")

internal fun findWorkOrderContact(users: List<User>, name: String?): User? {
    val normalizedName = name.normalizedContactName()
    if (normalizedName.isBlank()) return null
    return users.firstOrNull { user ->
        user.displayName.normalizedContactName() == normalizedName ||
            user.username.normalizedContactName() == normalizedName
    }
}

internal fun textNumber(user: User?): String? =
    user?.phoneNumber?.takeIf { it.count(Char::isDigit) >= 7 }

internal fun emailAddress(user: User?): String? =
    user?.email?.takeIf { it.contains("@") && it.contains(".") }

private fun displayStatus(status: String): String =
    if (status == "InProgress") "In Progress" else status

private fun String?.normalizedContactName(): String =
    orEmpty().trim().lowercase().replace(Regex("\\s+"), " ")
