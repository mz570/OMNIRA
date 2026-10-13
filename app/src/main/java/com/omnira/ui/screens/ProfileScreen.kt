package com.omnira.ui.screens

import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.omnira.ui.theme.GreenPrimary
import com.omnira.ui.theme.OMNIRATheme
import com.omnira.ui.theme.YellowSecondary
import com.omnira.ui.theme.getNeumorphicTextStyle
import com.omnira.ui.theme.neumorphic
import com.omnira.utils.uploadToCloudinary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBackClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {}
) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    val currentUser = auth.currentUser
    val uid = currentUser?.uid ?: ""

    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf(currentUser?.email ?: "") }
    var phone by rememberSaveable { mutableStateOf("") }
    var profileImageUrl by rememberSaveable { mutableStateOf("") }
    var isLoading by rememberSaveable { mutableStateOf(true) }
    var isUploading by rememberSaveable { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            if (uid.isNotEmpty()) {
                isUploading = true
                uploadToCloudinary(context, it, "users/$uid/profiles") { url ->
                    if (url != null) {
                        db.collection("users").document(uid)
                            .update("profileImageUrl", url)
                            .addOnSuccessListener {
                                profileImageUrl = url
                                isUploading = false
                                Toast.makeText(context, "Photo Updated", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener {
                                isUploading = false
                                Toast.makeText(context, "Failed to update database", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        isUploading = false
                        Toast.makeText(context, "Upload Failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    LaunchedEffect(uid) {
        if (uid.isNotEmpty()) {
            db.collection("users").document(uid).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        firstName = document.getString("firstName") ?: ""
                        lastName = document.getString("lastName") ?: ""
                        phone = document.getString("phone") ?: ""
                        profileImageUrl = document.getString("profileImageUrl") ?: ""
                    }
                    isLoading = false
                }
                .addOnFailureListener {
                    isLoading = false
                    Toast.makeText(context, "Failed to load profile", Toast.LENGTH_SHORT).show()
                }
        } else {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Profile", style = MaterialTheme.typography.titleLarge, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GreenPrimary)
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GreenPrimary)
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isLandscape) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(0.4f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            ProfilePictureSection(
                                imageUrl = profileImageUrl,
                                isUploading = isUploading,
                                onImageClick = { imagePickerLauncher.launch("image/*") }
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .neumorphic(elevation = 4.dp)
                                    .background(Color.White, RoundedCornerShape(12.dp))
                                    .clickable {
                                        auth.signOut()
                                        onLogoutClick()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Logout", style = MaterialTheme.typography.labelLarge, color = Color.Red)
                            }
                        }

                        Column(modifier = Modifier.weight(0.6f)) {
                            ProfileFields(
                                firstName = firstName, onFirstNameChange = { firstName = it },
                                lastName = lastName, onLastNameChange = { lastName = it },
                                email = email,
                                phone = phone, onPhoneChange = { phone = it },
                                onSaveClick = {
                                    saveProfile(auth, db, firstName, lastName, phone, context)
                                }
                            )
                        }
                    }
                } else {
                    ProfilePictureSection(
                        imageUrl = profileImageUrl,
                        isUploading = isUploading,
                        onImageClick = { imagePickerLauncher.launch("image/*") }
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    ProfileFields(
                        firstName = firstName, onFirstNameChange = { firstName = it },
                        lastName = lastName, onLastNameChange = { lastName = it },
                        email = email,
                        phone = phone, onPhoneChange = { phone = it },
                        onSaveClick = {
                            saveProfile(auth, db, firstName, lastName, phone, context)
                        }
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(55.dp)
                            .neumorphic(elevation = 4.dp)
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .clickable {
                                auth.signOut()
                                onLogoutClick()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Logout", style = MaterialTheme.typography.labelLarge, color = Color.Red)
                    }
                }
            }
        }
    }
}

@Composable
fun ProfilePictureSection(
    imageUrl: String,
    isUploading: Boolean,
    onImageClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .neumorphic(shape = CircleShape, elevation = 8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .clickable(enabled = !isUploading) { onImageClick() },
            contentAlignment = Alignment.Center
        ) {
            if (imageUrl.isNotEmpty()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "Profile Picture",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "Profile Picture",
                    modifier = Modifier.size(60.dp),
                    tint = GreenPrimary.copy(alpha = 0.6f)
                )
            }

            if (isUploading) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(30.dp))
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(36.dp)
                    .background(YellowSecondary, CircleShape)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = "Change Photo",
                    modifier = Modifier.size(20.dp),
                    tint = Color.Black
                )
            }
        }

        Text(
            text = if (isUploading) "Uploading..." else "Tap to change photo",
            style = MaterialTheme.typography.labelMedium,
            color = GreenPrimary,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

@Composable
fun ProfileFields(
    firstName: String, onFirstNameChange: (String) -> Unit,
    lastName: String, onLastNameChange: (String) -> Unit,
    email: String,
    phone: String, onPhoneChange: (String) -> Unit,
    onSaveClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphic(elevation = 6.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            ProfileInfoField(label = "First Name", value = firstName, onValueChange = onFirstNameChange)
            Spacer(modifier = Modifier.height(16.dp))
            ProfileInfoField(label = "Last Name", value = lastName, onValueChange = onLastNameChange)
            Spacer(modifier = Modifier.height(16.dp))
            ProfileInfoField(label = "Email Address", value = email, onValueChange = { /* Read only */ }, enabled = false)
            Spacer(modifier = Modifier.height(16.dp))
            ProfileInfoField(label = "Phone Number", value = phone, onValueChange = onPhoneChange)
        }
    }

    Spacer(modifier = Modifier.height(32.dp))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(55.dp)
            .neumorphic(elevation = 4.dp)
            .background(GreenPrimary, RoundedCornerShape(12.dp))
            .clickable { onSaveClick() },
        contentAlignment = Alignment.Center
    ) {
        Text("Save Changes", style = MaterialTheme.typography.titleMedium, color = Color.White)
    }
}

private fun saveProfile(
    auth: FirebaseAuth, db: FirebaseFirestore,
    firstName: String, lastName: String, phone: String,
    context: android.content.Context
) {
    val uid = auth.currentUser?.uid
    if (uid != null) {
        val userMap = hashMapOf(
            "firstName" to firstName,
            "lastName" to lastName,
            "phone" to phone
        )
        db.collection("users").document(uid).update(userMap as Map<String, Any>)
            .addOnSuccessListener {
                Toast.makeText(context, "Profile Updated", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(context, "Update failed", Toast.LENGTH_SHORT).show()
            }
    }
}

@Composable
fun ProfileInfoField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            enabled = enabled,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GreenPrimary,
                unfocusedBorderColor = Color.LightGray,
                disabledBorderColor = Color.LightGray,
                disabledTextColor = Color.Gray,
                unfocusedContainerColor = MaterialTheme.colorScheme.background,
                focusedContainerColor = MaterialTheme.colorScheme.background,
                disabledContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.5f)
            ),
            textStyle = MaterialTheme.typography.bodyLarge
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ProfileScreenPreview() {
    OMNIRATheme {
        ProfileScreen()
    }
}
