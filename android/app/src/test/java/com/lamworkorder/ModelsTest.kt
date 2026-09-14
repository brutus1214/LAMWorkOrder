package com.lamworkorder

import com.lamworkorder.data.CreateWorkOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelsTest {
    @Test
    fun createWorkOrderDefaultsToNormalPriority() {
        val request = CreateWorkOrder(1, "Title", "Description", "Requester", "Location")
        assertEquals("Normal", request.priority)
    }
}
