package com.lamworkorder

import com.lamworkorder.data.User
import com.lamworkorder.data.WorkOrder
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private const val ALL_WORK_ORDER_MAX_AGE_DAYS = 30L
private val WORK_ORDER_DATE_FORMAT = DateTimeFormatter.ofPattern("M/d/yy", Locale.US)

private val FILTER_GROUPS = mapOf(
    "New" to setOf("New"),
    "Open/In Progress" to setOf("Scheduled", "InProgress", "Blocked"),
    "Completed" to setOf("Completed"),
    "Closed/Cancelled" to setOf("Cancelled"),
)

internal fun visibleWorkOrders(
    orders: List<WorkOrder>,
    filter: String,
    now: Instant = Instant.now(),
    currentUser: User? = null,
): List<WorkOrder> {
    val cutoff = now.minus(ALL_WORK_ORDER_MAX_AGE_DAYS, ChronoUnit.DAYS)
    return orders
        .filter { order ->
            when (filter) {
                "Me" -> order.hasUserName(currentUser)
                "All" -> order.createdAfter(cutoff)
                else -> FILTER_GROUPS[filter]?.contains(order.status) ?: order.createdAfter(cutoff)
            }
        }
        .sortedWith(
            compareBy<WorkOrder> {
                when (it.status) {
                    "New" -> 0
                    "Scheduled" -> 1
                    "InProgress" -> 2
                    "Blocked" -> 3
                    "Completed" -> 4
                    "Cancelled" -> 5
                    else -> 6
                }
            }.thenBy { it.workOrderNumber }
        )
}

internal fun formatWorkOrderCreatedDate(
    createdAt: String,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    val created = parseCreatedAt(createdAt) ?: return createdAt.substringBefore("T")
    return WORK_ORDER_DATE_FORMAT.withZone(zoneId).format(created)
}

private fun WorkOrder.createdAfter(cutoff: Instant): Boolean {
    val created = parseCreatedAt(createdAt) ?: return true
    return created.isAfter(cutoff)
}

private fun WorkOrder.hasUserName(user: User?): Boolean {
    if (user == null) return false
    val names = setOf(
        user.displayName.normalizedName(),
        user.username.normalizedName(),
        "${user.displayName} - ${user.storeLabel()}".normalizedName(),
    )
    return listOf(requestedBy, assignedTo)
        .map { it.normalizedName() }
        .any { field ->
            field in names || field.withoutStoreLabel() == user.displayName.normalizedName()
        }
}

private fun parseCreatedAt(value: String): Instant? =
    runCatching { OffsetDateTime.parse(value).toInstant() }
        .recoverCatching { Instant.parse(value) }
        .recoverCatching { LocalDateTime.parse(value).toInstant(ZoneOffset.UTC) }
        .getOrNull()

private fun User.storeLabel(): String =
    if (storeNumber == 99) "All Stores" else "LA Mart $storeNumber"

private fun String?.normalizedName(): String =
    orEmpty().trim().lowercase().replace(Regex("\\s+"), " ")

private fun String.withoutStoreLabel(): String =
    replace(Regex("\\s+-\\s+(la mart \\d+|all stores)$"), "")
