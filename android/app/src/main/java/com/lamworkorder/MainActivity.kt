package com.lamworkorder

import android.graphics.BitmapFactory
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Patterns
import android.os.Bundle
import android.widget.Toast
import android.widget.MediaController
import android.widget.VideoView
import androidx.core.content.FileProvider
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lamworkorder.data.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { MaterialTheme { WorkOrderApp() } } }
}

@Composable fun WorkOrderApp(model: WorkOrderViewModel = viewModel()) {
    val state by model.state.collectAsState()
    if (state.user == null) { Login(model, state); return }
    var selected by remember { mutableStateOf<WorkOrder?>(null) }
    var profile by remember { mutableStateOf(false) }
    var manageUsers by remember { mutableStateOf(false) }
    if (profile) { Profile(model, state.user!!, { profile = false }); return }
    if (manageUsers) { ManageUsers(model, state, { manageUsers = false }); return }
    selected?.let { Detail(model, it, state, { selected = null }); return }
    Queue(model, state, { selected = it }, { profile = true }, { manageUsers = true; model.loadUsers() })
}

@Composable private fun Login(model: WorkOrderViewModel, state: QueueState) {
    var createMode by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var store by remember { mutableIntStateOf(1) }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf(TextFieldValue("")) }
    val emailFocus = remember { FocusRequester() }
    val phoneFocus = remember { FocusRequester() }
    val usernameFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val validEmail = email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val validPhone = phone.text.count(Char::isDigit) == 10
    val validUsername = username.trim().length >= 2 &&
        username.trim().all { it.isLetterOrDigit() || it in "_.-" }
    val validPassword = password.length >= 8

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        BrandLogo(Modifier.fillMaxWidth().height(92.dp))
        Spacer(Modifier.height(12.dp))
        Text("LA MART OPERATIONS", color = Color(0xFF0D8278), fontWeight = FontWeight.Bold)
        Text(if (createMode) "Create new user" else "Sign in", style = MaterialTheme.typography.headlineLarge)
        if (createMode) {
            Text("New accounts start as Requester. A manager can change the role later.")
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full name") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { emailFocus.requestFocus() }),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            StoreDropdown(
                selectedStore = store,
                onStoreSelected = { store = it },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it.trim() },
                label = { Text("Email") },
                supportingText = {
                    if (email.isNotBlank() && !validEmail) Text("Enter a valid email, such as name@example.com")
                },
                isError = email.isNotBlank() && !validEmail,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onNext = { phoneFocus.requestFocus() }),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(emailFocus),
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { incoming ->
                    val formatted = formatUsPhone(incoming.text)
                    phone = TextFieldValue(
                        text = formatted,
                        selection = TextRange(formatted.length),
                    )
                },
                label = { Text("Phone number for texting") },
                supportingText = {
                    if (phone.text.isNotBlank() && !validPhone) Text("Enter a 10-digit phone number")
                },
                isError = phone.text.isNotBlank() && !validPhone,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onNext = { usernameFocus.requestFocus() }),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(phoneFocus),
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                supportingText = {
                    if (username.isNotBlank() && !validUsername) {
                        Text("Use at least 2 letters or numbers; . _ and - are allowed")
                    }
                },
                isError = username.isNotBlank() && !validUsername,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(usernameFocus),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password (8+ characters)") },
                supportingText = {
                    if (password.isNotBlank() && !validPassword) Text("Password must be at least 8 characters")
                },
                isError = password.isNotBlank() && !validPassword,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(passwordFocus),
            )
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                {
                    model.register(
                        RegistrationRequest(
                            username.trim(), password, name.trim(), store,
                            email.trim(), phone.text.trim(),
                        )
                    )
                },
                enabled = !state.loading && name.isNotBlank() &&
                    validEmail && validPhone && validUsername && validPassword,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Create user and sign in") }
            TextButton({ createMode = false }, modifier = Modifier.fillMaxWidth()) {
                Text("Already have an account? Sign in")
            }
        } else {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username or email") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(passwordFocus),
            )
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                { model.login(username, password) },
                enabled = !state.loading && username.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Sign in") }
            OutlinedButton({ createMode = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Create new user")
            }
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

private fun formatUsPhone(input: String): String {
    val digits = input.filter(Char::isDigit).take(10)
    return when {
        digits.length <= 3 -> digits
        digits.length <= 6 -> "${digits.take(3)}-${digits.drop(3)}"
        else -> "${digits.take(3)}-${digits.substring(3, 6)}-${digits.drop(6)}"
    }
}

@Composable private fun BrandLogo(modifier: Modifier = Modifier) {
    val logo = remember {
        val bytes = Base64.decode(LA_MART_LOGO_BASE64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap()
    }
    Image(
        bitmap = logo,
        contentDescription = "LA Mart",
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun StoreDropdown(
    selectedStore: Int,
    onStoreSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val stores = remember { listOf(99) + (1..9) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = if (selectedStore == 99) "LA Mart 99 — All Stores" else "LA Mart $selectedStore",
            onValueChange = {},
            readOnly = true,
            label = { Text("Store") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            stores.forEach { storeNumber ->
                DropdownMenuItem(
                    text = {
                        Text(
                            if (storeNumber == 99) "LA Mart 99 — All Stores"
                            else "LA Mart $storeNumber"
                        )
                    },
                    onClick = {
                        onStoreSelected(storeNumber)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable private fun Queue(model: WorkOrderViewModel, state: QueueState, select:(WorkOrder)->Unit, showProfile:()->Unit, showUsers:()->Unit) {
    val currentUser = state.user ?: return
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }
    var menuOpen by remember { mutableStateOf(false) }
    var intakeOpen by remember { mutableStateOf(false) }
    LaunchedEffect(currentUser.id) {
        if (state.technicians.isEmpty()) model.loadTechnicians()
    }
    if (intakeOpen) { Intake(model, state) { intakeOpen = false }; return }
    val visibleOrders = remember(state.orders, filter) { visibleWorkOrders(state.orders, filter) }
    Box(Modifier.fillMaxSize().background(Color(0xFFF0F4F7)).safeDrawingPadding()) {
      Column(Modifier.fillMaxSize().padding(16.dp).padding(bottom = 58.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
        BrandLogo(Modifier.fillMaxWidth().height(54.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text("Work orders", style=MaterialTheme.typography.headlineLarge); Text(currentUser.displayName) }
            Box {
                IconButton({ menuOpen = true }) { Text("\u22EE", style = MaterialTheme.typography.headlineMedium) }
                DropdownMenu(menuOpen, { menuOpen = false }) {
                    if(currentUser.role in listOf("Admin","Manager")) DropdownMenuItem({ Text("Manage Users") }, { menuOpen=false; showUsers() })
                    DropdownMenuItem({ Text("Profile") }, { menuOpen=false; showProfile() })
                    DropdownMenuItem({ Text("Logout") }, { menuOpen=false; model.logout() })
                }
            }
        }
        /* Previous expanded account actions:
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column{Text("Work orders",style=MaterialTheme.typography.headlineLarge);Text("${currentUser.displayName} · ${if(currentUser.role=="Admin") "Administrator" else currentUser.role}")};Column{if(currentUser.role in listOf("Admin","Manager")) TextButton(showUsers){Text("Manage Users")};TextButton(showProfile){Text("Profile")};TextButton(model::logout){Text("Logout")}}}
        */
        Row{OutlinedTextField(search,{search=it},label={Text("Search")},modifier=Modifier.weight(1f));Button({model.refresh(search);model.loadTechnicians()}){Text("Go")}}
        OutlinedButton({model.refresh(search);model.loadTechnicians()}, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) { Text("Refresh") }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "New", "Open/In Progress", "Completed", "Closed/Cancelled").forEach { option ->
                FilterChip(selected = filter == option, onClick = { filter = option }, label = { Text(option) })
            }
        }
        if(state.loading) LinearProgressIndicator(Modifier.fillMaxWidth()); state.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        /* Unfiltered list:
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){items(state.orders,key={it.id}){o->Card(Modifier.fillMaxWidth().clickable{select(o)}){Column(Modifier.padding(14.dp)){Text(o.workOrderNumber,fontWeight=FontWeight.Bold);Text(o.title,style=MaterialTheme.typography.titleMedium);Text("Store ${o.storeNumber} · ${o.location} · ${o.status}")}}}}
        */
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            items(visibleOrders,key={it.id}) { order ->
                Card(Modifier.fillMaxWidth().clickable{select(order)}) { Column(Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(order.workOrderNumber,fontWeight=FontWeight.Bold)
                        Text(formatWorkOrderCreatedDate(order.createdAt), style = MaterialTheme.typography.labelMedium)
                    }
                    Text(order.title,style=MaterialTheme.typography.titleMedium)
                    Text("Store ${order.storeNumber} - ${order.location} - ${if(order.status=="InProgress") "In Progress" else order.status}")
                }}
            }
        }
      }
      Button({ intakeOpen = true }, Modifier.align(androidx.compose.ui.Alignment.BottomCenter).fillMaxWidth().padding(16.dp)) {
          Text("New Work Order")
      }
    }
}

@Composable private fun ManageUsers(model: WorkOrderViewModel, state: QueueState, back: () -> Unit) {
    var selected by remember { mutableStateOf<User?>(null) }
    selected?.let { EditUser(model, it, state.user!!, { selected = null }) ; return }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Manage Users", style = MaterialTheme.typography.headlineLarge)
            TextButton(back) { Text("Back") }
        }
        Text(if (state.user?.role == "Admin") "All stores" else "LA Mart ${state.user?.storeNumber}")
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.users, key = { it.id }) { user ->
                Card(Modifier.fillMaxWidth().clickable { selected = user }) {
                    Column(Modifier.padding(14.dp)) {
                        Text(user.displayName, fontWeight = FontWeight.Bold)
                        Text("${user.username} · ${if(user.role=="Admin") "Administrator" else user.role}")
                        Text("${if(user.storeNumber==99) "All Stores" else "LA Mart ${user.storeNumber}"} · ${if(user.isActive) "Active" else "Inactive"}")
                    }
                }
            }
        }
    }
}

@Composable private fun EditUser(model: WorkOrderViewModel, user: User, actor: User, back: () -> Unit) {
    var name by remember { mutableStateOf(user.displayName) }; var store by remember { mutableIntStateOf(user.storeNumber) }
    var role by remember { mutableStateOf(user.role) }; var email by remember { mutableStateOf(user.email.orEmpty()) }
    var phone by remember { mutableStateOf(TextFieldValue(user.phoneNumber.orEmpty())) }; var active by remember { mutableStateOf(user.isActive) }
    var password by remember { mutableStateOf("") }; var roleOpen by remember { mutableStateOf(false) }
    val roles = if(actor.role=="Admin") listOf("Requester","Technician","Manager","Admin") else listOf("Requester","Technician")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Edit User", style = MaterialTheme.typography.headlineLarge); Text("@${user.username}")
        OutlinedTextField(name,{name=it},label={Text("Full name")},modifier=Modifier.fillMaxWidth())
        StoreDropdown(store,{store=it},Modifier.fillMaxWidth())
        Box { OutlinedButton({roleOpen=true}){Text("Role: ${if(role=="Admin") "Administrator" else role}")}; DropdownMenu(roleOpen,{roleOpen=false}){roles.forEach{r->DropdownMenuItem({Text(if(r=="Admin") "Administrator" else r)},{role=r;roleOpen=false})}} }
        OutlinedTextField(email,{email=it.trim()},label={Text("Email")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email),modifier=Modifier.fillMaxWidth())
        OutlinedTextField(phone,{incoming->val formatted=formatUsPhone(incoming.text);phone=TextFieldValue(formatted,selection=TextRange(formatted.length))},label={Text("Phone number for texting")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone),modifier=Modifier.fillMaxWidth())
        Row { Switch(active,{active=it}); Spacer(Modifier.width(8.dp)); Text(if(active) "Active — can sign in" else "Inactive — sign-in blocked") }
        Button({model.updateUser(user.id,UserAdminUpdate(name.trim(),store,role,email.ifBlank{null},phone.text.ifBlank{null},active),back)},enabled=name.isNotBlank()){Text("Save user")}
        HorizontalDivider(); Text("Reset password",style=MaterialTheme.typography.titleMedium)
        OutlinedTextField(password,{password=it},label={Text("Temporary password (8+ characters)")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
        OutlinedButton({model.resetPassword(user.id,password){password=""}},enabled=password.length>=8){Text("Reset password")}
        TextButton(back){Text("Cancel")}
    }
}

@Composable private fun Profile(model:WorkOrderViewModel,user:User,back:()->Unit){var name by remember{mutableStateOf(user.displayName)};var email by remember{mutableStateOf(user.email.orEmpty())};Column(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Profile",style=MaterialTheme.typography.headlineLarge);Text("${user.username} · ${user.role}");OutlinedTextField(name,{name=it},label={Text("Display name")});OutlinedTextField(email,{email=it},label={Text("Email")});Button({model.updateProfile(name,email,back)},enabled=name.isNotBlank()){Text("Save")};TextButton(back){Text("Cancel")}}}

private fun technicianOptions(state: QueueState): List<User> =
    (state.technicians + state.users.filter { it.role == "Technician" })
        .distinctBy { it.id }
        .sortedBy { it.displayName }

@Composable private fun Detail(model:WorkOrderViewModel,o:WorkOrder,state:QueueState,back:()->Unit){
    val user = state.user ?: return
    val full=user.role in listOf("Admin","Manager"); val statusEdit=full||user.role=="Technician"
    var title by remember{mutableStateOf(o.title)};var description by remember{mutableStateOf(o.description)};var requester by remember{mutableStateOf(o.requestedBy)};var location by remember{mutableStateOf(o.location)};var priority by remember{mutableStateOf(o.priority)};var assigned by remember{mutableStateOf(o.assignedTo.orEmpty())};var status by remember{mutableStateOf(o.status)};var note by remember{mutableStateOf(o.statusNote.orEmpty())}
    val context=LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(o.id, user.role) {
        if (state.technicians.isEmpty()) {
            model.loadTechnicians()
        }
        model.loadNotificationRecipients(o.storeNumber)
        if (user.role in listOf("Admin", "Manager") && state.users.isEmpty()) {
            model.loadUsers()
        }
    }
    var captureUri by remember { mutableStateOf<Uri?>(null) }
    fun uploadUris(uris: List<Uri>) {
        val parts=uris.mapNotNull{uri->context.contentResolver.openInputStream(uri)?.use{input->
            val bytes=input.readBytes()
            val type=context.contentResolver.getType(uri)?:"application/octet-stream"
            val extension=if(type.startsWith("video/")) "mp4" else if(type.startsWith("image/")) "jpg" else "bin"
            MultipartBody.Part.createFormData("files","attachment_${System.currentTimeMillis()}.$extension",bytes.toRequestBody(type.toMediaType()))
        }}
        if(parts.isNotEmpty())model.upload(o.id,parts,back)
    }
    fun newCaptureUri(extension: String): Uri {
        val directory=File(context.cacheDir,"captured_media").apply { mkdirs() }
        val file=File.createTempFile("work_order_", ".$extension", directory)
        return FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file)
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris:List<Uri>->uploadUris(uris)}
    val photoCapture=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){saved->if(saved)captureUri?.let{uploadUris(listOf(it))}}
    val videoCapture=rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()){saved->if(saved)captureUri?.let{uploadUris(listOf(it))}}
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { if (!state.loading) confirmDelete = false },
            title = { Text("Delete work order?") },
            text = { Text("${o.workOrderNumber} will be permanently deleted.") },
            confirmButton = {
                Button(
                    {
                        model.deleteWorkOrder(o.id) {
                            confirmDelete = false
                            back()
                        }
                    },
                    enabled = !state.loading,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton({ confirmDelete = false }, enabled = !state.loading) { Text("Cancel") }
            },
        )
    }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){item{Button(back){Text("Back")}};item{Text(o.workOrderNumber,fontWeight=FontWeight.Bold)}
        item{WorkOrderSendActions(model,o,state.users + technicianOptions(state),user,title,description,requester,location,priority,assigned,status,note)}
        item{OutlinedTextField(value="LA Mart ${o.storeNumber}",onValueChange={},readOnly=true,label={Text("Store")},supportingText={Text("Set when work order was created")},modifier=Modifier.fillMaxWidth())};item{Field("Title",title,{title=it},full)};item{Field("Description",description,{description=it},full)}
        if(o.attachments.isNotEmpty())item{AttachmentGallery(model,o.attachments)}
        item{Field("Requested by",requester,{requester=it},full)};item{Field("Location",location,{location=it},full)}
        item{SelectionField("Priority",priority,listOf("Low","Normal","High","Emergency"),{priority=it},full)}
        item{AssigneeDropdown(assigned,technicianOptions(state),{assigned=it},full)}
        item{SelectionField("Status",status,listOf("New","Scheduled","InProgress","Blocked","Completed","Cancelled"),{status=it},statusEdit) { if(it=="InProgress") "In Progress" else it }}
        item{Field("Status note",note,{note=it},statusEdit)}
        if(full)item{Button({
            val shouldNotify = assigned.isNotBlank() && assigned != o.assignedTo.orEmpty()
            model.update(o.id,UpdateWorkOrder(o.storeNumber,title,description,requester,location,priority,assigned.ifBlank{null},o.dueAt,status,note.ifBlank{null}),{
                if (shouldNotify) scope.launch { notifyAssignment(context,model,state.notificationRecipients,o,title,description,requester,location,priority,assigned,status,note) }
                back()
            })
        }){Text("Save all details")}}
        else if(statusEdit)item{Button({model.updateStatus(o.id,status,note,back)}){Text("Save status")}}
        if(user.username.equals("jc", ignoreCase = true))item{
            OutlinedButton(
                { confirmDelete = true },
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Delete Work Order") }
        }
        if(statusEdit)item{
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("Add photos or videos",style=MaterialTheme.typography.titleMedium)
                Button({captureUri=newCaptureUri("jpg");photoCapture.launch(captureUri!!)},Modifier.fillMaxWidth()){Text("Take photo")}
                Button({captureUri=newCaptureUri("mp4");videoCapture.launch(captureUri!!)},Modifier.fillMaxWidth()){Text("Record video")}
                OutlinedButton({picker.launch(arrayOf("image/*","video/*"))},Modifier.fillMaxWidth()){Text("Choose from device")}
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun AssigneeDropdown(
    assignedTo: String,
    technicians: List<User>,
    onAssigned: (String) -> Unit,
    enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    val technicianNames = remember(technicians) {
        technicians.map { it.displayName }.distinct().sorted()
    }
    val options = remember(technicianNames, assignedTo) {
        listOf("") + technicianNames +
            if (assignedTo.isNotBlank() && assignedTo !in technicianNames) listOf(assignedTo) else emptyList()
    }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = assignedTo.ifBlank { "Unassigned" },
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Assigned to") },
            supportingText = {
                Text(
                    if (technicianNames.isEmpty()) "No technicians loaded. Tap Refresh after backend restart."
                    else "Optional technician assignment"
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.ifBlank { "Unassigned" }) },
                    onClick = {
                        onAssigned(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable private fun WorkOrderSendActions(
    model: WorkOrderViewModel,
    order: WorkOrder,
    users: List<User>,
    currentUser: User,
    title: String,
    description: String,
    requestedBy: String,
    location: String,
    priority: String,
    assignedTo: String,
    status: String,
    statusNote: String?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var vendorEmail by remember { mutableStateOf("") }
    var sendMenuOpen by remember { mutableStateOf(false) }
    var preparingEmail by remember { mutableStateOf(false) }
    val contacts = remember(users, currentUser) {
        listOf(currentUser) + users.filter { it.id != currentUser.id }
    }
    val requester = remember(contacts, requestedBy) { findWorkOrderContact(contacts, requestedBy) }
    val technician = remember(contacts, assignedTo) { findWorkOrderContact(contacts, assignedTo) }
    val subject = remember(order.workOrderNumber) { workOrderShareSubject(order) }
    val message = workOrderShareText(
        order = order,
        title = title,
        description = description,
        requestedBy = requestedBy,
        location = location,
        priority = priority,
        assignedTo = assignedTo.ifBlank { null },
        status = status,
        statusNote = statusNote?.takeIf(String::isNotBlank),
    )
    val validVendorEmail = vendorEmail.isBlank() ||
        Patterns.EMAIL_ADDRESS.matcher(vendorEmail.trim()).matches()

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Send work order", style = MaterialTheme.typography.titleMedium)
        Box(Modifier.fillMaxWidth()) {
            Button(
                { sendMenuOpen = true },
                enabled = !preparingEmail,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (preparingEmail) "Preparing email..." else "Send...") }
            DropdownMenu(sendMenuOpen, { sendMenuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Share work order") },
                    onClick = {
                        sendMenuOpen = false
                        shareWorkOrder(context, subject, message)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Text requester") },
                    enabled = textNumber(requester) != null,
                    onClick = {
                        sendMenuOpen = false
                        textWorkOrder(context, textNumber(requester), message)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Email requester") },
                    enabled = emailAddress(requester) != null && !preparingEmail,
                    onClick = {
                        sendMenuOpen = false
                        scope.launch {
                            preparingEmail = true
                            emailWorkOrder(context, model, order.attachments, emailAddress(requester), subject, message)
                            preparingEmail = false
                        }
                    },
                )
                DropdownMenuItem(
                    text = { Text("Text technician") },
                    enabled = textNumber(technician) != null,
                    onClick = {
                        sendMenuOpen = false
                        textWorkOrder(context, textNumber(technician), message)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Email technician") },
                    enabled = emailAddress(technician) != null && !preparingEmail,
                    onClick = {
                        sendMenuOpen = false
                        scope.launch {
                            preparingEmail = true
                            emailWorkOrder(context, model, order.attachments, emailAddress(technician), subject, message)
                            preparingEmail = false
                        }
                    },
                )
                DropdownMenuItem(
                    text = { Text("Email vendor") },
                    enabled = vendorEmail.isNotBlank() && validVendorEmail && !preparingEmail,
                    onClick = {
                        sendMenuOpen = false
                        scope.launch {
                            preparingEmail = true
                            emailWorkOrder(context, model, order.attachments, vendorEmail.trim(), subject, message)
                            preparingEmail = false
                        }
                    },
                )
            }
        }
        OutlinedTextField(
            value = vendorEmail,
            onValueChange = { vendorEmail = it.trim() },
            label = { Text("Vendor email") },
            isError = !validVendorEmail,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun shareWorkOrder(context: Context, subject: String, message: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, message)
    }
    launchChooser(context, intent, "Share work order")
}

private fun textWorkOrder(context: Context, phoneNumber: String?, message: String) {
    if (phoneNumber.isNullOrBlank()) return
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("smsto:${Uri.encode(phoneNumber)}")
        putExtra("sms_body", message)
    }
    launchChooser(context, intent, "Text work order")
}

private suspend fun emailWorkOrder(
    context: Context,
    model: WorkOrderViewModel,
    attachments: List<Attachment>,
    email: String?,
    subject: String,
    message: String,
) {
    emailWorkOrder(context, model, attachments, listOfNotNull(email?.takeIf(String::isNotBlank)), subject, message)
}

private suspend fun emailWorkOrder(
    context: Context,
    model: WorkOrderViewModel,
    attachments: List<Attachment>,
    emails: List<String>,
    subject: String,
    message: String,
) {
    if (emails.isEmpty()) return
    val attachmentUris = prepareEmailAttachmentUris(context, model, attachments)
    val intent = if (attachmentUris.isEmpty()) {
        Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, emails.toTypedArray())
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, message)
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_EMAIL, emails.toTypedArray())
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, message)
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(attachmentUris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, "work order attachment", attachmentUris.first()).also { clip ->
                attachmentUris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            }
        }
    }
    launchChooser(context, intent, "Email work order")
}

private suspend fun prepareEmailAttachmentUris(
    context: Context,
    model: WorkOrderViewModel,
    attachments: List<Attachment>,
): List<Uri> {
    if (attachments.isEmpty()) return emptyList()
    val directory = File(context.cacheDir, "shared_attachments").apply { mkdirs() }
    directory.listFiles()?.forEach { it.delete() }
    return attachments.mapNotNull { attachment ->
        runCatching {
            val file = File(directory, "${attachment.id}_${safeAttachmentName(attachment.originalName)}")
            file.writeBytes(model.attachmentBytes(attachment.id))
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
    }
}

private fun safeAttachmentName(name: String): String =
    name.ifBlank { "attachment" }.replace(Regex("[^A-Za-z0-9._-]"), "_")

private suspend fun notifyAssignment(
    context: Context,
    model: WorkOrderViewModel,
    recipients: List<User>,
    order: WorkOrder,
    title: String,
    description: String,
    requestedBy: String,
    location: String,
    priority: String,
    assignedTo: String,
    status: String,
    statusNote: String?,
) {
    val emails = recipients.mapNotNull(::emailAddress).distinct()
    if (emails.isEmpty()) {
        Toast.makeText(context, "No JC or store manager email found.", Toast.LENGTH_LONG).show()
        return
    }
    val message = "Technician assigned: $assignedTo\n\n" + workOrderShareText(
        order = order,
        title = title,
        description = description,
        requestedBy = requestedBy,
        location = location,
        priority = priority,
        assignedTo = assignedTo,
        status = status,
        statusNote = statusNote?.takeIf(String::isNotBlank),
    )
    emailWorkOrder(context, model, order.attachments, emails, "Assigned: ${order.workOrderNumber}", message)
}

private fun launchChooser(context: Context, intent: Intent, title: String) {
    runCatching {
        context.startActivity(Intent.createChooser(intent, title))
    }.onFailure {
        Toast.makeText(context, "No matching app found.", Toast.LENGTH_LONG).show()
    }
}

@Composable private fun AttachmentGallery(model: WorkOrderViewModel, attachments: List<Attachment>) {
    val initial = attachments.firstOrNull { it.contentType.startsWith("image/") } ?: attachments.first()
    var selected by remember(attachments) { mutableStateOf(initial) }
    var mediaBytes by remember(selected.id) { mutableStateOf<ByteArray?>(null) }
    var loadError by remember(selected.id) { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    LaunchedEffect(selected.id) {
        mediaBytes = null
        loadError = null
        runCatching { model.attachmentBytes(selected.id) }
            .onSuccess { mediaBytes = it }
            .onFailure { loadError = "Unable to load ${selected.originalName}" }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Photos and videos", style = MaterialTheme.typography.titleMedium)
        when {
            loadError != null -> Text(loadError!!, color = MaterialTheme.colorScheme.error)
            mediaBytes == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
            selected.contentType.startsWith("image/") -> {
                val bitmap = remember(mediaBytes) { BitmapFactory.decodeByteArray(mediaBytes, 0, mediaBytes!!.size) }
                if (bitmap != null) Image(
                    bitmap.asImageBitmap(),
                    contentDescription = selected.originalName,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit,
                )
            }
            selected.contentType.startsWith("video/") -> {
                val videoFile = remember(selected.id, mediaBytes) {
                    File(context.cacheDir, "preview_${selected.id}.mp4").apply { writeBytes(mediaBytes!!) }
                }
                AndroidView(
                    factory = { videoContext -> VideoView(videoContext).apply {
                        val controls = MediaController(videoContext)
                        controls.setAnchorView(this)
                        setMediaController(controls)
                        setVideoPath(videoFile.absolutePath)
                        setOnPreparedListener { seekTo(1) }
                    } },
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                )
            }
            else -> Text("Preview unavailable for ${selected.originalName}")
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            attachments.forEachIndexed { index, attachment ->
                FilterChip(
                    selected = attachment.id == selected.id,
                    onClick = { selected = attachment },
                    label = { Text(if (attachment.contentType.startsWith("video/")) "Video ${index + 1}" else "Photo ${index + 1}") },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun SelectionField(
    label: String,
    value: String,
    options: List<String>,
    change: (String) -> Unit,
    enabled: Boolean,
    displayValue: (String) -> String = { it },
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = displayValue(value),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(displayValue(option)) },
                    onClick = {
                        change(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable private fun Field(
    label: String,
    value: String,
    change: (String) -> Unit,
    enabled: Boolean,
    translate: Boolean = true,
) {
    if (!translate) {
        OutlinedTextField(
            value,
            change,
            label = { Text(label) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    TranslatableField(label, value, change, enabled)
}

@Composable private fun TranslatableField(
    label: String,
    value: String,
    change: (String) -> Unit,
    enabled: Boolean,
) {
    var translatedText by remember(value) { mutableStateOf<String?>(null) }
    var translating by remember { mutableStateOf(false) }
    var translationError by remember { mutableStateOf<String?>(null) }

    val languageIdentifier = remember { LanguageIdentification.getClient() }

    val englishToSpanish = remember {
        Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.SPANISH)
                .build()
        )
    }
    val spanishToEnglish = remember {
        Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.SPANISH)
                .setTargetLanguage(TranslateLanguage.ENGLISH)
                .build()
        )
    }
    DisposableEffect(languageIdentifier, englishToSpanish, spanishToEnglish) {
        onDispose {
            languageIdentifier.close()
            englishToSpanish.close()
            spanishToEnglish.close()
        }
    }

    LaunchedEffect(translatedText) {
        if (translatedText != null) {
            delay(8_000)
            translatedText = null
        }
    }

    fun runTranslation(translator: Translator) {
        if (value.isBlank()) {
            translating = false
            return
        }
        val conditions = DownloadConditions.Builder().build()
        translator.downloadModelIfNeeded(conditions)
            .continueWithTask { translator.translate(value) }
            .addOnSuccessListener {
                translatedText = it
                translating = false
            }
            .addOnFailureListener {
                translationError = "Translation unavailable. Check internet once to download the language model."
                translating = false
            }
    }

    fun detectAndTranslate() {
        if (value.isBlank() || translating) return
        translating = true
        translatedText = null
        translationError = null
        languageIdentifier.identifyLanguage(value)
            .addOnSuccessListener { languageCode ->
                val translator = if (languageCode == "es") {
                    spanishToEnglish
                } else {
                    englishToSpanish
                }
                runTranslation(translator)
            }
            .addOnFailureListener {
                translationError = "Could not detect the language."
                translating = false
            }
    }

    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value,
            change,
            label = { Text(label) },
            enabled = enabled,
            trailingIcon = {
                IconButton(
                    onClick = { detectAndTranslate() },
                    enabled = value.isNotBlank() && !translating,
                ) {
                    Text("🌐", style = MaterialTheme.typography.titleMedium)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (translating) LinearProgressIndicator(Modifier.fillMaxWidth())
        translatedText?.let { translation ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp),
                    tonalElevation = 1.dp,
                    modifier = Modifier.widthIn(max = 320.dp),
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(
                            "Translation",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                        )
                        Text(translation, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        translationError?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable private fun Intake(model: WorkOrderViewModel, state: QueueState, close: () -> Unit) {
    val user = state.user ?: return
    var store by remember(user.id) { mutableIntStateOf(user.storeNumber) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("Normal") }
    var assigned by remember { mutableStateOf("") }
    var attachments by remember { mutableStateOf<List<MultipartBody.Part>>(emptyList()) }
    var captureUri by remember { mutableStateOf<Uri?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        if (state.technicians.isEmpty()) model.loadTechnicians()
        if (user.role in listOf("Admin", "Manager") && state.users.isEmpty()) model.loadUsers()
    }
    LaunchedEffect(store) {
        model.loadNotificationRecipients(store)
    }
    fun uriToPart(uri: Uri): MultipartBody.Part? = context.contentResolver.openInputStream(uri)?.use { input ->
        val bytes = input.readBytes()
        val type = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val extension = if (type.startsWith("video/")) "mp4" else if (type.startsWith("image/")) "jpg" else "bin"
        MultipartBody.Part.createFormData(
            "files",
            "attachment_${System.currentTimeMillis()}.$extension",
            bytes.toRequestBody(type.toMediaType()),
        )
    }
    fun newCaptureUri(extension: String): Uri {
        val directory = File(context.cacheDir, "captured_media").apply { mkdirs() }
        val file = File.createTempFile("work_order_", ".$extension", directory)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        attachments = attachments + uris.mapNotNull(::uriToPart)
    }
    val photoCapture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) captureUri?.let(::uriToPart)?.let { attachments = attachments + it }
    }
    val videoCapture = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { saved ->
        if (saved) captureUri?.let(::uriToPart)?.let { attachments = attachments + it }
    }

    Scaffold(
        modifier = Modifier.safeDrawingPadding(),
        topBar = { Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("New Work Order", style = MaterialTheme.typography.headlineMedium)
            TextButton(close) { Text("Cancel") }
        }},
        bottomBar = { Surface(shadowElevation = 8.dp) { Button({
            if (submitting) return@Button
            submitting = true
            val selectedAssignee = assigned
            model.create(
                CreateWorkOrder(store, title, description, user.displayName, location, priority, selectedAssignee.ifBlank { null }),
                attachments,
                done = { created ->
                    close()
                    if (selectedAssignee.isNotBlank()) {
                        scope.launch { notifyAssignment(context,model,state.notificationRecipients,created,title,description,user.displayName,location,priority,selectedAssignee,created.status,created.statusNote) }
                    }
                },
                failed = { submitting = false },
            )
        }, enabled = !submitting && !state.loading && title.isNotBlank() && description.isNotBlank() && location.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(if (submitting) "Creating..." else "Create Work Order")
        }}},
    ) { contentPadding ->
      Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StoreDropdown(store, { store = it }, Modifier.fillMaxWidth())
        Field("Title", title, { title = it }, true)
        Field("Description", description, { description = it }, true)
        OutlinedTextField(
            value = user.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Requested by") },
            supportingText = { Text("Current signed-in user") },
            modifier = Modifier.fillMaxWidth(),
        )
        Field("Location", location, { location = it }, true)
        SelectionField("Priority", priority, listOf("Low", "Normal", "High", "Emergency"), { priority = it }, true)
        AssigneeDropdown(assigned, technicianOptions(state), { assigned = it }, true)
        Text(
            if (attachments.isEmpty()) "Add photos or videos" else "${attachments.size} attachment(s) selected",
            style = MaterialTheme.typography.titleMedium,
        )
        Button({ captureUri = newCaptureUri("jpg"); photoCapture.launch(captureUri!!) }, Modifier.fillMaxWidth()) {
            Text("Take photo")
        }
        Button({ captureUri = newCaptureUri("mp4"); videoCapture.launch(captureUri!!) }, Modifier.fillMaxWidth()) {
            Text("Record video")
        }
        OutlinedButton({ picker.launch(arrayOf("image/*", "video/*")) }, Modifier.fillMaxWidth()) {
            Text("Choose from device")
        }
        /* Create action moved to the fixed bottom bar.
        Button(
            {
                model.create(
                    CreateWorkOrder(store, title, description, user.displayName, location, priority),
                    attachments,
                ) {
                    open = false
                    attachments = emptyList()
                }
            },
            enabled = title.isNotBlank() && description.isNotBlank() && location.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Create") }
        */
      }
    }
}
