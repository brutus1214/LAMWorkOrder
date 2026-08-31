package com.lamworkorder

import com.lamworkorder.data.User
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
    fun meFilterShowsOrdersRequestedByOrAssignedToCurrentUser() {
        val requestedByMe = workOrder(
            id = "requested",
            number = "WO-3-2026-0001",
            createdAt = "2026-08-01T12:00:00Z",
            requestedBy = "James Chang",
        )
        val assignedToMe = workOrder(
            id = "assigned",
            number = "WO-3-2026-0002",
            createdAt = "2026-08-01T12:00:00Z",
            assignedTo = "James Chang - All Stores",
        )
        val other = workOrder(
            id = "other",
            number = "WO-3-2026-0003",
            createdAt = "2026-08-01T12:00:00Z",
            requestedBy = "Other User",
            assignedTo = "Other User - LA Mart 3",
        )

        val visible = visibleWorkOrders(
            listOf(requestedByMe, assignedToMe, other),
            "Me",
            now,
            currentUser = user("jc", "James Chang", 99),
        )

        assertEquals(listOf("requested", "assigned"), visible.map { it.id })
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
        requestedBy: String = "Requester",
        assignedTo: String? = null,
    ) = WorkOrder(
        id = id,
        workOrderNumber = number,
        storeNumber = 3,
        title = "Replace line filter",
        description = "Filter housing is leaking.",
        requestedBy = requestedBy,
        location = "Line 2 / Bay 4",
        priority = "High",
        status = status,
        assignedTo = assignedTo,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    private fun user(username: String, displayName: String, storeNumber: Int) = User(
        id = "user-$username",
        username = username,
        displayName = displayName,
        storeNumber = storeNumber,
        role = "Manager",
    )
}
