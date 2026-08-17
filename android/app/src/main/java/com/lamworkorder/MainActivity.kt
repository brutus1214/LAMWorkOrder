package com.lamworkorder

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Patterns
import android.os.Bundle
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
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
    selected?.let { Detail(model, it, state.user!!, { selected = null }); return }
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
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
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
    if (intakeOpen) { Intake(model, currentUser) { intakeOpen = false }; return }
    val visibleOrders = remember(state.orders, filter) {
        state.orders.filter { order -> when (filter) {
            "New" -> order.status == "New"
            "Open/In Progress" -> order.status in listOf("Scheduled", "InProgress", "Blocked")
            "Completed" -> order.status == "Completed"
            "Closed/Cancelled" -> order.status == "Cancelled"
            else -> true
        }}.sortedWith(compareBy<WorkOrder> { when (it.status) {
            "New" -> 0; "Scheduled" -> 1; "InProgress" -> 2; "Blocked" -> 3
            "Completed" -> 4; "Cancelled" -> 5; else -> 6
        }}.thenBy { it.workOrderNumber })
    }
    Box(Modifier.fillMaxSize().background(Color(0xFFF0F4F7))) {
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
        Row{OutlinedTextField(search,{search=it},label={Text("Search")},modifier=Modifier.weight(1f));Button({model.refresh(search)}){Text("Go")}}
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
                    Text(order.workOrderNumber,fontWeight=FontWeight.Bold)
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
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

@Composable private fun Profile(model:WorkOrderViewModel,user:User,back:()->Unit){var name by remember{mutableStateOf(user.displayName)};var email by remember{mutableStateOf(user.email.orEmpty())};Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Profile",style=MaterialTheme.typography.headlineLarge);Text("${user.username} · ${user.role}");OutlinedTextField(name,{name=it},label={Text("Display name")});OutlinedTextField(email,{email=it},label={Text("Email")});Button({model.updateProfile(name,email,back)},enabled=name.isNotBlank()){Text("Save")};TextButton(back){Text("Cancel")}}}

@Composable private fun Detail(model:WorkOrderViewModel,o:WorkOrder,user:User,back:()->Unit){
    val full=user.role in listOf("Admin","Manager"); val statusEdit=full||user.role=="Technician"
    var title by remember{mutableStateOf(o.title)};var description by remember{mutableStateOf(o.description)};var requester by remember{mutableStateOf(o.requestedBy)};var location by remember{mutableStateOf(o.location)};var priority by remember{mutableStateOf(o.priority)};var assigned by remember{mutableStateOf(o.assignedTo.orEmpty())};var status by remember{mutableStateOf(o.status)};var note by remember{mutableStateOf(o.statusNote.orEmpty())}
    val context=LocalContext.current
    var mediaMenuOpen by remember { mutableStateOf(false) }
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
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){item{Button(back){Text("Back")}};item{Text(o.workOrderNumber,fontWeight=FontWeight.Bold)}
        item{OutlinedTextField(value="LA Mart ${o.storeNumber}",onValueChange={},readOnly=true,label={Text("Store")},supportingText={Text("Set when work order was created")},modifier=Modifier.fillMaxWidth())};item{Field("Title",title,{title=it},full)};item{Field("Description",description,{description=it},full)};item{Field("Requested by",requester,{requester=it},full)};item{Field("Location",location,{location=it},full)}
        item{SelectionField("Priority",priority,listOf("Low","Normal","High","Emergency"),{priority=it},full)}
        item{Field("Assigned to",assigned,{assigned=it},full)}
        item{SelectionField("Status",status,listOf("New","Scheduled","InProgress","Blocked","Completed","Cancelled"),{status=it},statusEdit) { if(it=="InProgress") "In Progress" else it }}
        item{Field("Status note",note,{note=it},statusEdit)}
        if(full)item{Button({model.update(o.id,UpdateWorkOrder(o.storeNumber,title,description,requester,location,priority,assigned.ifBlank{null},o.dueAt,status,note.ifBlank{null}),back)}){Text("Save all details")}}
        else if(statusEdit)item{Button({model.updateStatus(o.id,status,note,back)}){Text("Save status")}}
        if(statusEdit)item{
            Button({mediaMenuOpen=true}){Text("Add photos or videos")}
            DropdownMenu(expanded=mediaMenuOpen,onDismissRequest={mediaMenuOpen=false}){
                DropdownMenuItem(text={Text("Take photo")},onClick={mediaMenuOpen=false;captureUri=newCaptureUri("jpg");photoCapture.launch(captureUri!!)})
                DropdownMenuItem(text={Text("Record video")},onClick={mediaMenuOpen=false;captureUri=newCaptureUri("mp4");videoCapture.launch(captureUri!!)})
                DropdownMenuItem(text={Text("Choose from device")},onClick={mediaMenuOpen=false;picker.launch(arrayOf("image/*","video/*"))})
            }
        }
        item{Text("Attachments",style=MaterialTheme.typography.titleMedium)};items(o.attachments){a->Text("${a.originalName} (${a.sizeBytes/1024} KB)")}
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

@Composable private fun Intake(model: WorkOrderViewModel, user: User, close: () -> Unit) {
    var store by remember(user.id) { mutableIntStateOf(user.storeNumber) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("Normal") }
    var attachments by remember { mutableStateOf<List<MultipartBody.Part>>(emptyList()) }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        attachments = uris.mapNotNull { uri ->
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                val type = context.contentResolver.getType(uri) ?: "application/octet-stream"
                MultipartBody.Part.createFormData(
                    "files",
                    uri.lastPathSegment ?: "attachment",
                    bytes.toRequestBody(type.toMediaType()),
                )
            }
        }
    }

    Scaffold(
        topBar = { Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("New Work Order", style = MaterialTheme.typography.headlineMedium)
            TextButton(close) { Text("Cancel") }
        }},
        bottomBar = { Surface(shadowElevation = 8.dp) { Button({
            model.create(CreateWorkOrder(store, title, description, user.displayName, location, priority), attachments, close)
        }, enabled = title.isNotBlank() && description.isNotBlank() && location.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Create Work Order")
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
        OutlinedButton(
            { picker.launch(arrayOf("image/*", "video/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (attachments.isEmpty()) "Add pictures or videos" else "${attachments.size} attachment(s) selected")
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
