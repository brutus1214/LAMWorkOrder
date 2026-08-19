package com.lamworkorder

import com.lamworkorder.data.WorkOrder
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkOrderFiltersTest {
    private val now = Instant.parse("2026-08-18T12:00:00Z")

    @Test
    fun allFilterOnlyShowsWorkOrdersNewerThanThirtyDays() {
        val recent = workOrder("recent", "WO-3-2026-0001", "2026-08-01T12:00:00Z")
        val older = workOrder("older", "WO-3-2026-0002", "2026-07-01T12:00:00Z")

        val visible = visibleWorkOrders(listOf(recent, older), "All", now)

        assertEquals(listOf("recent"), visible.map { it.id })
    }

    @Test
    fun statusFilterCanStillShowOlderMatchingWorkOrders() {
        val older = workOrder(
            id = "older",
            number = "WO-3-2026-0002",
            createdAt = "2026-07-01T12:00:00Z",
            status = "Completed",
        )

        val visible = visibleWorkOrders(listOf(older), "Completed", now)

        assertEquals(listOf("older"), visible.map { it.id })
    }

    @Test
    fun createdDateFormatsCompactlyForListDisplay() {
        assertEquals(
            "8/18/26",
            formatWorkOrderCreatedDate("2026-08-18T12:30:00Z", ZoneOffset.UTC),
        )
    }

    private fun workOrder(
        id: String,
        number: String,
        createdAt: String,
        status: String = "New",
    ) = WorkOrder(
        id = id,
        workOrderNumber = number,
        storeNumber = 3,
        title = "Replace line filter",
        description = "Filter housing is leaking.",
        requestedBy = "Requester",
        location = "Line 2 / Bay 4",
        priority = "High",
        status = status,
        createdAt = createdAt,
        updatedAt = createdAt,
    )
}
