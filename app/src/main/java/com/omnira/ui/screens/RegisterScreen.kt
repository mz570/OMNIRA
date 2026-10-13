package com.omnira.ui.screens

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.omnira.ui.theme.GreenPrimary
import com.omnira.ui.theme.OMNIRATheme
import com.omnira.ui.theme.YellowSecondary
import com.omnira.ui.theme.getNeumorphicTextStyle
import com.omnira.ui.theme.neumorphic

@Composable
fun RegisterScreen(
    onBackClick: () -> Unit = {},
    onLoginClick: () -> Unit = {},
    onRegisterSuccess: () -> Unit = {}
) {
    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var isLoading by rememberSaveable { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (!isLandscape) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(color = GreenPrimary, shape = RoundedCornerShape(bottomStart = 80.dp))
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(if (isLandscape) 16.dp else 48.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = if (isLandscape) GreenPrimary else Color.White)
                }
                if (!isLandscape) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text("Register", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Spacer(modifier = Modifier.weight(1.2f))
                }
            }

            if (isLandscape) {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Column(modifier = Modifier.weight(0.4f).padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Join Omnira", style = getNeumorphicTextStyle(MaterialTheme.typography.displayLarge, GreenPrimary))
                        Text("Create your account", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
                    }
                    Column(modifier = Modifier.weight(0.6f)) {
                        RegisterNeumorphicContainer {
                            RegisterFormFields(
                                firstName, { firstName = it }, lastName, { lastName = it },
                                email, { email = it }, phone, { phone = it },
                                password, { password = it }, confirmPassword, { confirmPassword = it },
                                isLoading,
                                onRegisterClick = { performRegistration(context, auth, db, firstName, lastName, email, phone, password, confirmPassword, { isLoading = it }, onRegisterSuccess) },
                                onLoginClick = onLoginClick
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(40.dp))
                RegisterNeumorphicContainer {
                    RegisterFormFields(
                        firstName, { firstName = it }, lastName, { lastName = it },
                        email, { email = it }, phone, { phone = it },
                        password, { password = it }, confirmPassword, { confirmPassword = it },
                        isLoading,
                        onRegisterClick = { performRegistration(context, auth, db, firstName, lastName, email, phone, password, confirmPassword, { isLoading = it }, onRegisterSuccess) },
                        onLoginClick = onLoginClick
                    )
                }
            }
            Spacer(modifier = Modifier.height(if (isLandscape) 16.dp else 80.dp))
        }
    }
}

@Composable
fun RegisterNeumorphicContainer(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphic(elevation = 8.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
fun RegisterFormFields(
    firstName: String, onFirstNameChange: (String) -> Unit,
    lastName: String, onLastNameChange: (String) -> Unit,
    email: String, onEmailChange: (String) -> Unit,
    phone: String, onPhoneChange: (String) -> Unit,
    password: String, onPasswordChange: (String) -> Unit,
    confirmPassword: String, onConfirmPasswordChange: (String) -> Unit,
    isLoading: Boolean,
    onRegisterClick: () -> Unit,
    onLoginClick: () -> Unit
) {
    Column {
        RegisterTextField(value = firstName, onValueChange = onFirstNameChange, label = "First Name", icon = Icons.Default.Person)
        Spacer(modifier = Modifier.height(12.dp))
        RegisterTextField(value = lastName, onValueChange = onLastNameChange, label = "Last Name", icon = Icons.Default.Person)
        Spacer(modifier = Modifier.height(12.dp))
        RegisterTextField(value = email, onValueChange = onEmailChange, label = "Email", icon = Icons.Default.Email)
        Spacer(modifier = Modifier.height(12.dp))
        RegisterTextField(value = phone, onValueChange = onPhoneChange, label = "Phone No.", icon = Icons.Default.Phone)
        Spacer(modifier = Modifier.height(16.dp))

        RegisterTextField(value = password, onValueChange = onPasswordChange, label = "Password", icon = Icons.Default.Lock, isPassword = true)
        Spacer(modifier = Modifier.height(12.dp))
        RegisterTextField(value = confirmPassword, onValueChange = onConfirmPasswordChange, label = "Confirm Password", icon = Icons.Default.Lock, isPassword = true)
        Spacer(modifier = Modifier.height(32.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .neumorphic(elevation = 4.dp)
                .background(if (isLoading) Color.Gray else GreenPrimary, RoundedCornerShape(8.dp))
                .clickable(enabled = !isLoading) { onRegisterClick() },
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            else Text("Register", style = MaterialTheme.typography.titleMedium, color = Color.White)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("Already have an account? ", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            Text("Login", 
                color = GreenPrimary, 
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable { onLoginClick() }
            )
        }
    }
}

private fun performRegistration(context: android.content.Context, auth: FirebaseAuth, db: FirebaseFirestore, firstName: String, lastName: String, email: String, phone: String, password: String, confirmPassword: String, setLoading: (Boolean) -> Unit, onSuccess: () -> Unit) {
    if (email.isEmpty() || password.isEmpty() || firstName.isEmpty() || lastName.isEmpty()) {
        Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
        return
    }
    if (password != confirmPassword) {
        Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show()
        return
    }
    setLoading(true)
    auth.createUserWithEmailAndPassword(email.trim(), password).addOnCompleteListener { task ->
        if (task.isSuccessful) {
            val userId = auth.currentUser?.uid
            val userMap = hashMapOf("firstName" to firstName.trim(), "lastName" to lastName.trim(), "email" to email.trim(), "phone" to phone.trim(), "uid" to userId)
            if (userId != null) {
                db.collection("users").document(userId).set(userMap).addOnSuccessListener { setLoading(false); onSuccess() }.addOnFailureListener { setLoading(false); Toast.makeText(context, "Failed to save user data", Toast.LENGTH_SHORT).show() }
            }
        } else {
            setLoading(false)
            Toast.makeText(context, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun RegisterTextField(value: String, onValueChange: (String) -> Unit, label: String, icon: ImageVector, isPassword: Boolean = false) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val isError = value.startsWith(" ")
    OutlinedTextField(
        value = value, 
        onValueChange = onValueChange, 
        label = { Text(label, style = MaterialTheme.typography.bodyMedium) }, 
        leadingIcon = { Icon(icon, null, tint = Color.Gray) }, 
        trailingIcon = {
            if (isPassword) IconButton(onClick = { passwordVisible = !passwordVisible }) { Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null, tint = Color.Gray) }
        }, 
        isError = isError, 
        supportingText = { if (isError) Text("Leading space is not allowed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }, 
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None, 
        modifier = Modifier.fillMaxWidth(), 
        shape = RoundedCornerShape(12.dp), 
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = GreenPrimary, 
            unfocusedBorderColor = Color.LightGray,
            unfocusedContainerColor = MaterialTheme.colorScheme.background,
            focusedContainerColor = MaterialTheme.colorScheme.background
        ),
        textStyle = MaterialTheme.typography.bodyLarge
    )
}

@Preview(showBackground = true)
@Composable
fun RegisterScreenPreview() { OMNIRATheme { RegisterScreen() } }
