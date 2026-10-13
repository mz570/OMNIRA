package com.omnira.ui.screens

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.omnira.R
import com.omnira.ui.components.OmniraLoadingLogo
import com.omnira.ui.theme.GreenPrimary
import com.omnira.ui.theme.OMNIRATheme
import com.omnira.ui.theme.YellowSecondary
import com.omnira.ui.theme.getNeumorphicTextStyle
import com.omnira.ui.theme.neumorphic

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit = {},
    onRegisterClick: () -> Unit = {}
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val scrollState = rememberScrollState()

    val emailError = email.startsWith(" ")
    val passwordError = password.startsWith(" ")

    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (!isLandscape) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(color = GreenPrimary, shape = RoundedCornerShape(bottomStart = 100.dp))
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Branded Logo Card (White background to show Mint Green logo)
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .neumorphic(shape = RoundedCornerShape(28.dp), elevation = 6.dp)
                                .background(Color.White, RoundedCornerShape(28.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_omnira_logo),
                                    contentDescription = null,
                                    modifier = Modifier.size(65.dp),
                                    tint = Color.Unspecified // Shows the Mint Green center
                                )
                                Text(
                                    text = "Omnira",
                                    color = GreenPrimary,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Classroom",
                            style = getNeumorphicTextStyle(MaterialTheme.typography.headlineMedium, GreenPrimary),
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(32.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        LoginNeumorphicContainer {
                            LoginFormFields(
                                email = email, onEmailChange = { email = it },
                                password = password, onPasswordChange = { password = it },
                                passwordVisible = passwordVisible, onTogglePassword = { passwordVisible = !passwordVisible },
                                emailError = emailError, passwordError = passwordError,
                                isLoading = isLoading,
                                onLoginClick = { 
                                    handleLogin(email, password, emailError, passwordError, context, auth, { isLoading = it }, onLoginSuccess)
                                },
                                onRegisterClick = onRegisterClick
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(60.dp))
                
                // Branded Logo Card (White background to show Mint Green logo)
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .neumorphic(shape = RoundedCornerShape(28.dp), elevation = 8.dp)
                        .background(Color.White, RoundedCornerShape(28.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_omnira_logo),
                            contentDescription = "Logo",
                            modifier = Modifier.size(80.dp),
                            tint = Color.Unspecified // Shows the Mint Green center
                        )
                        Text(
                            text = "Omnira",
                            color = GreenPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Classroom",
                    style = getNeumorphicTextStyle(MaterialTheme.typography.displayMedium, GreenPrimary),
                    textAlign = TextAlign.Center,
                    lineHeight = 32.sp
                )
                
                Spacer(modifier = Modifier.height(40.dp))
                
                LoginNeumorphicContainer {
                    LoginFormFields(
                        email = email, onEmailChange = { email = it },
                        password = password, onPasswordChange = { password = it },
                        passwordVisible = passwordVisible, onTogglePassword = { passwordVisible = !passwordVisible },
                        emailError = emailError, passwordError = passwordError,
                        isLoading = isLoading,
                        onLoginClick = { 
                            handleLogin(email, password, emailError, passwordError, context, auth, { isLoading = it }, onLoginSuccess)
                        },
                        onRegisterClick = onRegisterClick
                    )
                }
            }
        }
    }
}

@Composable
fun LoginNeumorphicContainer(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphic(elevation = 8.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(28.dp))
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
fun LoginFormFields(
    email: String, onEmailChange: (String) -> Unit,
    password: String, onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean, onTogglePassword: () -> Unit,
    emailError: Boolean, passwordError: Boolean,
    isLoading: Boolean,
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit
) {
    Column {
        OutlinedTextField(
            value = email, onValueChange = onEmailChange,
            label = { Text("Email", style = MaterialTheme.typography.bodyMedium) },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            isError = emailError,
            supportingText = { if (emailError) Text("Leading space is not allowed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) },
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.background,
                focusedContainerColor = MaterialTheme.colorScheme.background
            ),
            textStyle = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = password, onValueChange = onPasswordChange,
            label = { Text("Password", style = MaterialTheme.typography.bodyMedium) },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = onTogglePassword) {
                    Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null)
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            isError = passwordError,
            supportingText = { if (passwordError) Text("Leading space is not allowed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) },
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.background,
                focusedContainerColor = MaterialTheme.colorScheme.background
            ),
            textStyle = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(24.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
                .neumorphic(elevation = 4.dp)
                .background(if (isLoading) Color.Gray else GreenPrimary, RoundedCornerShape(12.dp))
                .clickable(enabled = !isLoading) { onLoginClick() },
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                // Branded Loading Animation on button
                OmniraLoadingLogo(size = 35.dp, mintColor = Color.White, orbitColor = Color.White.copy(alpha = 0.5f))
            } else {
                Text("Login", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            Text("Don't have an account? ", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            Text("Register", 
                style = MaterialTheme.typography.labelLarge,
                color = GreenPrimary, 
                modifier = Modifier.clickable { onRegisterClick() }
            )
        }
    }
}

private fun handleLogin(
    email: String, password: String, 
    emailError: Boolean, passwordError: Boolean,
    context: android.content.Context, auth: FirebaseAuth,
    setLoading: (Boolean) -> Unit, onLoginSuccess: () -> Unit
) {
    if (email.isEmpty() || password.isEmpty()) {
        Toast.makeText(context, "Please enter email and password", Toast.LENGTH_SHORT).show()
        return
    }
    if (emailError || passwordError) {
        Toast.makeText(context, "Please fix errors before proceeding", Toast.LENGTH_SHORT).show()
        return
    }
    setLoading(true)
    auth.signInWithEmailAndPassword(email.trim(), password)
        .addOnCompleteListener { task ->
            setLoading(false)
            if (task.isSuccessful) {
                Toast.makeText(context, "Login Successful", Toast.LENGTH_SHORT).show()
                onLoginSuccess()
            } else {
                Toast.makeText(context, "Login Failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
            }
        }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    OMNIRATheme { LoginScreen() }
}
