package com.lamworkorder

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Patterns
import android.os.Bundle
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
import androidx.compose.ui.text.font.FontWeight
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
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

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
    var createMode by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var store by remember { mutableIntStateOf(1) }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    val emailFocus = remember { FocusRequester() }
    val phoneFocus = remember { FocusRequester() }
    val usernameFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val validEmail = email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val validPhone = phone.count(Char::isDigit) == 10

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
                onValueChange = { phone = formatUsPhone(it) },
                label = { Text("Phone number for texting") },
                supportingText = {
                    if (phone.isNotBlank() && !validPhone) Text("Enter a 10-digit phone number")
                },
                isError = phone.isNotBlank() && !validPhone,
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
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(usernameFocus),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password (8+ characters)") },
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
                            email.trim(), phone.trim(),
                        )
                    )
                },
                enabled = !state.loading && name.isNotBlank() &&
                    validEmail && validPhone && username.length >= 3 && password.length >= 8,
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

@Composable private fun Queue(model: WorkOrderViewModel, state: QueueState, select:(WorkOrder)->Unit, showProfile:()->Unit) {
    var search by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Color(0xFFF0F4F7)).padding(16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
        BrandLogo(Modifier.fillMaxWidth().height(54.dp))
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
        item{Field("Store",store,{store=it},full,translate=false)};item{Field("Title",title,{title=it},full)};item{Field("Description",description,{description=it},full)};item{Field("Requested by",requester,{requester=it},full)};item{Field("Location",location,{location=it},full)};item{Field("Priority",priority,{priority=it},full)};item{Field("Assigned to",assigned,{assigned=it},full)};item{Field("Status",status,{status=it},statusEdit)};item{Field("Status note",note,{note=it},statusEdit)}
        if(full)item{Button({model.update(o.id,UpdateWorkOrder(store.toInt(),title,description,requester,location,priority,assigned.ifBlank{null},o.dueAt,status,note.ifBlank{null}),back)},enabled=store.toIntOrNull()!=null){Text("Save all details")}}
        else if(statusEdit)item{Button({model.updateStatus(o.id,status,note,back)}){Text("Save status")}}
        if(statusEdit)item{Button({picker.launch(arrayOf("image/*","video/*"))}){Text("Add photos or videos")}}
        item{Text("Attachments",style=MaterialTheme.typography.titleMedium)};items(o.attachments){a->Text("${a.originalName} (${a.sizeBytes/1024} KB)")}
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
    DisposableEffect(englishToSpanish, spanishToEnglish) {
        onDispose {
            englishToSpanish.close()
            spanishToEnglish.close()
        }
    }

    fun runTranslation(translator: Translator) {
        if (value.isBlank() || translating) return
        translating = true
        translationError = null
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

    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value,
            change,
            label = { Text(label) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = { runTranslation(englishToSpanish) },
                enabled = value.isNotBlank() && !translating,
            ) { Text("EN→ES") }
            TextButton(
                onClick = { runTranslation(spanishToEnglish) },
                enabled = value.isNotBlank() && !translating,
            ) { Text("ES→EN") }
        }
        if (translating) LinearProgressIndicator(Modifier.fillMaxWidth())
        translatedText?.let {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(it, Modifier.padding(10.dp))
            }
        }
        translationError?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable private fun Intake(model:WorkOrderViewModel){var open by remember{mutableStateOf(false)};var store by remember{mutableStateOf("1")};var title by remember{mutableStateOf("")};var description by remember{mutableStateOf("")};var requester by remember{mutableStateOf("")};var location by remember{mutableStateOf("")};Button({open=!open},Modifier.fillMaxWidth()){Text(if(open)"Close intake" else "New work order")};if(open)Column{Field("Store",store,{store=it.filter(Char::isDigit)},true,translate=false);Field("Title",title,{title=it},true);Field("Description",description,{description=it},true);Field("Requested by",requester,{requester=it},true);Field("Location",location,{location=it},true);Button({model.create(CreateWorkOrder(store.toInt(),title,description,requester,location)){open=false}},enabled=store.toIntOrNull()!=null&&listOf(title,description,requester,location).all(String::isNotBlank)){Text("Create")}}}
