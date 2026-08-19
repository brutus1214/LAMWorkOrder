package com.lamworkorder

import com.lamworkorder.data.User
import com.lamworkorder.data.WorkOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkOrderSharingTest {
    @Test
    fun shareTextContainsWorkOrderDetails() {
        val text = workOrderShareText(workOrder())

        assertTrue(text.contains("Work Order WO-6-2026-0001"))
        assertTrue(text.contains("Created:"))
        assertTrue(text.contains("Store: 6"))
        assertTrue(text.contains("Location: machine room"))
        assertTrue(text.contains("Status: New"))
        assertTrue(text.contains("Priority: High"))
        assertTrue(text.contains("Requested by: Lam06 Chang"))
        assertFalse(text.contains("Attachments:"))
    }

    @Test
    fun contactCanBeMatchedByDisplayNameOrUsername() {
        val users = listOf(user("1", "tech06", "Tech Six"))

        assertEquals("1", findWorkOrderContact(users, " tech six ")?.id)
        assertEquals("1", findWorkOrderContact(users, "TECH06")?.id)
        assertNull(findWorkOrderContact(users, "Unknown"))
    }

    @Test
    fun phoneAndEmailMustBeUsableBeforeSendingDirectly() {
        val contact = user("1", "tech06", "Tech Six", "tech@example.com", "202-555-0100")

        assertEquals("202-555-0100", textNumber(contact))
        assertEquals("tech@example.com", emailAddress(contact))
        assertNull(textNumber(contact.copy(phoneNumber = "555")))
        assertNull(emailAddress(contact.copy(email = "missing-at")))
    }

    private fun workOrder() = WorkOrder(
        id = "order-1",
        workOrderNumber = "WO-6-2026-0001",
        storeNumber = 6,
        title = "test 99",
        description = "Check machine room",
        requestedBy = "Lam06 Chang",
        location = "machine room",
        priority = "High",
        status = "New",
        assignedTo = "Tech Six",
        createdAt = "2026-08-18T12:30:00Z",
        updatedAt = "2026-08-18T12:30:00Z",
    )

    private fun user(
        id: String,
        username: String,
        displayName: String,
        email: String? = null,
        phoneNumber: String? = null,
    ) = User(
        id = id,
        username = username,
        displayName = displayName,
        email = email,
        phoneNumber = phoneNumber,
        role = "Technician",
    )
}
