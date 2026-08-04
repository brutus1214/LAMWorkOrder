package com.lamworkorder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lamworkorder.data.CreateWorkOrder
import com.lamworkorder.data.WorkOrder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { WorkOrderApp() } }
    }
}

@Composable
fun WorkOrderApp(model: WorkOrderViewModel = viewModel()) {
    val state by model.state.collectAsState()
    var search by remember { mutableStateOf("") }
    var selectedOrder by remember { mutableStateOf<WorkOrder?>(null) }

    selectedOrder?.let { order ->
        WorkOrderDetail(order = order, onBack = { selectedOrder = null })
        return
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFFF0F4F7)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("LAM OPERATIONS", color = Color(0xFF0D8278), fontWeight = FontWeight.Bold)
        Text("Work orders", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(search, { search = it }, label = { Text("Search") }, modifier = Modifier.weight(1f))
            Button(onClick = { model.refresh(search) }, modifier = Modifier.padding(top = 8.dp)) { Text("Go") }
        }
        if (state.loading) CircularProgressIndicator()
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(state.orders, key = { it.id }) { order ->
                WorkOrderCard(order = order, onClick = { selectedOrder = order })
            }
        }
        Intake(model)
    }
}

@Composable
private fun WorkOrderCard(order: WorkOrder, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(order.workOrderNumber, fontWeight = FontWeight.Bold)
                Text(order.priority, color = if (order.priority == "Emergency") Color.Red else Color(0xFF0D8278))
            }
            Text(order.title, style = MaterialTheme.typography.titleMedium)
            Text("Store ${order.storeNumber} · ${order.location} · ${order.status}", color = Color.Gray)
        }
    }
}

@Composable
private fun WorkOrderDetail(order: WorkOrder, onBack: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().background(Color(0xFFF0F4F7)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Button(onClick = onBack) { Text("Back to work orders") }
        }
        item { Text(order.workOrderNumber, color = Color(0xFF0D8278), fontWeight = FontWeight.Bold) }
        item { Text(order.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item { HorizontalDivider() }
        item { DetailRow("Store", order.storeNumber.toString()) }
        item { DetailRow("Status", order.status) }
        item { DetailRow("Priority", order.priority) }
        item { DetailRow("Location", order.location) }
        item { DetailRow("Requested by", order.requestedBy) }
        item { DetailRow("Assigned to", order.assignedTo ?: "Not assigned") }
        item { DetailRow("Description", order.description) }
        order.statusNote?.takeIf { it.isNotBlank() }?.let { note ->
            item { DetailRow("Status note", note) }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = Color.Gray, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Intake(model: WorkOrderViewModel) {
    var expanded by remember { mutableStateOf(false) }
    var storeNumber by remember { mutableStateOf("1") }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var requester by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    Button(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
        Text(if (expanded) "Close intake" else "New work order")
    }
    if (expanded) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            storeNumber,
            { value -> storeNumber = value.filter(Char::isDigit) },
            label = { Text("Store number") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(description, { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(requester, { requester = it }, label = { Text("Requested by") }, modifier = Modifier.weight(1f))
            OutlinedTextField(location, { location = it }, label = { Text("Location") }, modifier = Modifier.weight(1f))
        }
        Button(
            enabled = storeNumber.toIntOrNull()?.let { it > 0 } == true &&
                listOf(title, description, requester, location).all { it.isNotBlank() },
            onClick = {
                model.create(
                    CreateWorkOrder(
                        storeNumber = storeNumber.toInt(),
                        title = title,
                        description = description,
                        requestedBy = requester,
                        location = location,
                    )
                ) {
                    storeNumber = "1"
                    title = ""
                    description = ""
                    requester = ""
                    location = ""
                    expanded = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Create") }
        Spacer(Modifier.height(4.dp))
    }
}
