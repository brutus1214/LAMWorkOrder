package com.lamworkorder

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lamworkorder.data.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { MaterialTheme { WorkOrderApp() } } }
}

@Composable fun WorkOrderApp(model: WorkOrderViewModel = viewModel()) {
    val state by model.state.collectAsState()
    if (state.user == null) { Login(model, state); return }
    var selected by remember { mutableStateOf<WorkOrder?>(null) }
    var profile by remember { mutableStateOf(false) }
    if (profile) { Profile(model, state.user!!, { profile = false }); return }
    selected?.let { Detail(model, it, state.user!!, { selected = null }); return }
    Queue(model, state, { selected = it }, { profile = true })
}

@Composable private fun Login(model: WorkOrderViewModel, state: QueueState) {
    var username by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("LAM OPERATIONS", color=Color(0xFF0D8278), fontWeight=FontWeight.Bold)
        Text("Sign in", style=MaterialTheme.typography.headlineLarge)
        OutlinedTextField(username,{username=it},label={Text("Username")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(password,{password=it},label={Text("Password")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
        state.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        Button({model.login(username,password)}, enabled=!state.loading&&username.isNotBlank()&&password.isNotBlank(), modifier=Modifier.fillMaxWidth()){Text("Sign in")}
    }
}

@Composable private fun Queue(model: WorkOrderViewModel, state: QueueState, select:(WorkOrder)->Unit, showProfile:()->Unit) {
    var search by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Color(0xFFF0F4F7)).padding(16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column{Text("Work orders",style=MaterialTheme.typography.headlineLarge);Text("${state.user!!.displayName} · ${state.user.role}")};TextButton(showProfile){Text("Profile")};TextButton(model::logout){Text("Logout")}}
        Row{OutlinedTextField(search,{search=it},label={Text("Search")},modifier=Modifier.weight(1f));Button({model.refresh(search)}){Text("Go")}}
        if(state.loading) LinearProgressIndicator(Modifier.fillMaxWidth()); state.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){items(state.orders,key={it.id}){o->Card(Modifier.fillMaxWidth().clickable{select(o)}){Column(Modifier.padding(14.dp)){Text(o.workOrderNumber,fontWeight=FontWeight.Bold);Text(o.title,style=MaterialTheme.typography.titleMedium);Text("Store ${o.storeNumber} · ${o.location} · ${o.status}")}}}}
        Intake(model)
    }
}

@Composable private fun Profile(model:WorkOrderViewModel,user:User,back:()->Unit){var name by remember{mutableStateOf(user.displayName)};var email by remember{mutableStateOf(user.email.orEmpty())};Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Profile",style=MaterialTheme.typography.headlineLarge);Text("${user.username} · ${user.role}");OutlinedTextField(name,{name=it},label={Text("Display name")});OutlinedTextField(email,{email=it},label={Text("Email")});Button({model.updateProfile(name,email,back)},enabled=name.isNotBlank()){Text("Save")};TextButton(back){Text("Cancel")}}}

@Composable private fun Detail(model:WorkOrderViewModel,o:WorkOrder,user:User,back:()->Unit){
    val full=user.role in listOf("Admin","Manager"); val statusEdit=full||user.role=="Technician"
    var store by remember{mutableStateOf(o.storeNumber.toString())};var title by remember{mutableStateOf(o.title)};var description by remember{mutableStateOf(o.description)};var requester by remember{mutableStateOf(o.requestedBy)};var location by remember{mutableStateOf(o.location)};var priority by remember{mutableStateOf(o.priority)};var assigned by remember{mutableStateOf(o.assignedTo.orEmpty())};var status by remember{mutableStateOf(o.status)};var note by remember{mutableStateOf(o.statusNote.orEmpty())}
    val context=LocalContext.current
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris:List<Uri>->val parts=uris.mapNotNull{uri->context.contentResolver.openInputStream(uri)?.use{input->val bytes=input.readBytes();val type=context.contentResolver.getType(uri)?:"application/octet-stream";MultipartBody.Part.createFormData("files","attachment",bytes.toRequestBody(type.toMediaType()))}};if(parts.isNotEmpty())model.upload(o.id,parts,back)}
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){item{Button(back){Text("Back")}};item{Text(o.workOrderNumber,fontWeight=FontWeight.Bold)}
        item{Field("Store",store,{store=it},full)};item{Field("Title",title,{title=it},full)};item{Field("Description",description,{description=it},full)};item{Field("Requested by",requester,{requester=it},full)};item{Field("Location",location,{location=it},full)};item{Field("Priority",priority,{priority=it},full)};item{Field("Assigned to",assigned,{assigned=it},full)};item{Field("Status",status,{status=it},statusEdit)};item{Field("Status note",note,{note=it},statusEdit)}
        if(full)item{Button({model.update(o.id,UpdateWorkOrder(store.toInt(),title,description,requester,location,priority,assigned.ifBlank{null},o.dueAt,status,note.ifBlank{null}),back)},enabled=store.toIntOrNull()!=null){Text("Save all details")}}
        else if(statusEdit)item{Button({model.updateStatus(o.id,status,note,back)}){Text("Save status")}}
        if(statusEdit)item{Button({picker.launch(arrayOf("image/*","video/*"))}){Text("Add photos or videos")}}
        item{Text("Attachments",style=MaterialTheme.typography.titleMedium)};items(o.attachments){a->Text("${a.originalName} (${a.sizeBytes/1024} KB)")}
    }
}

@Composable private fun Field(label:String,value:String,change:(String)->Unit,enabled:Boolean){OutlinedTextField(value,change,label={Text(label)},enabled=enabled,modifier=Modifier.fillMaxWidth())}

@Composable private fun Intake(model:WorkOrderViewModel){var open by remember{mutableStateOf(false)};var store by remember{mutableStateOf("1")};var title by remember{mutableStateOf("")};var description by remember{mutableStateOf("")};var requester by remember{mutableStateOf("")};var location by remember{mutableStateOf("")};Button({open=!open},Modifier.fillMaxWidth()){Text(if(open)"Close intake" else "New work order")};if(open)Column{Field("Store",store,{store=it.filter(Char::isDigit)},true);Field("Title",title,{title=it},true);Field("Description",description,{description=it},true);Field("Requested by",requester,{requester=it},true);Field("Location",location,{location=it},true);Button({model.create(CreateWorkOrder(store.toInt(),title,description,requester,location)){open=false}},enabled=store.toIntOrNull()!=null&&listOf(title,description,requester,location).all(String::isNotBlank)){Text("Create")}}}
