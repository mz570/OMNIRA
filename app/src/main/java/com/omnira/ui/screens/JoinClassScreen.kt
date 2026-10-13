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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.omnira.R
import com.omnira.data.Notification
import com.omnira.ui.components.OmniraLoadingLogo
import com.omnira.ui.theme.GreenPrimary
import com.omnira.ui.theme.OMNIRATheme
import com.omnira.ui.theme.neumorphic

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinClassScreen(
    onBackClick: () -> Unit = {},
    onJoinRequestSent: (String) -> Unit = {}
) {
    var classCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Join Class", style = MaterialTheme.typography.titleLarge, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GreenPrimary)
            )
        }
    ) { innerPadding ->
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        // Branded Logo Card (White bg to show Mint Green logo)
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .neumorphic(shape = RoundedCornerShape(28.dp), elevation = 6.dp)
                                .background(Color.White, RoundedCornerShape(28.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_omnira_logo),
                                contentDescription = null,
                                modifier = Modifier.size(70.dp),
                                tint = Color.Unspecified
                            )
                        }
                    }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enter the class code provided by your teacher",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Start,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        JoinNeumorphicContainer {
                            JoinForm(
                                classCode = classCode,
                                onValueChange = { classCode = it },
                                isLoading = isLoading,
                                onJoinClick = {
                                    handleJoin(classCode, context, auth, db, { isLoading = it }, onJoinRequestSent)
                                },
                                onCancelClick = onBackClick
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(20.dp))
                
                // Branded Logo Card
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .neumorphic(shape = RoundedCornerShape(28.dp), elevation = 8.dp)
                        .background(Color.White, RoundedCornerShape(28.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_omnira_logo),
                        contentDescription = null,
                        modifier = Modifier.size(75.dp),
                        tint = Color.Unspecified
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "Enter the class code provided by your teacher",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(24.dp))
                JoinNeumorphicContainer {
                    JoinForm(
                        classCode = classCode,
                        onValueChange = { classCode = it },
                        isLoading = isLoading,
                        onJoinClick = {
                            handleJoin(classCode, context, auth, db, { isLoading = it }, onJoinRequestSent)
                        },
                        onCancelClick = onBackClick
                    )
                }
            }
        }
    }
}

@Composable
fun JoinNeumorphicContainer(content: @Composable () -> Unit) {
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
fun JoinForm(
    classCode: String,
    onValueChange: (String) -> Unit,
    isLoading: Boolean,
    onJoinClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    Column {
        OutlinedTextField(
            value = classCode,
            onValueChange = onValueChange,
            placeholder = { Text("Enter Class Code", style = MaterialTheme.typography.bodyMedium) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            enabled = !isLoading,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.background,
                focusedContainerColor = MaterialTheme.colorScheme.background
            ),
            textStyle = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(32.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
                .neumorphic(elevation = 4.dp)
                .background(if (classCode.isBlank() || isLoading) Color.Gray else GreenPrimary, RoundedCornerShape(12.dp))
                .clickable(enabled = classCode.isNotBlank() && !isLoading) { onJoinClick() },
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                // Branded Animation with contrast for green button
                OmniraLoadingLogo(size = 35.dp, mintColor = Color.White, orbitColor = Color.White.copy(alpha = 0.5f))
            } else {
                Text("Join Class", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
        }

        TextButton(
            onClick = onCancelClick,
            enabled = !isLoading,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
        ) {
            Text("Cancel", style = MaterialTheme.typography.labelLarge, color = GreenPrimary)
        }
    }
}

private fun handleJoin(
    classCode: String,
    context: android.content.Context,
    auth: FirebaseAuth,
    db: FirebaseFirestore,
    setLoading: (Boolean) -> Unit,
    onJoinRequestSent: (String) -> Unit
) {
    if (classCode.isBlank()) return
    if (classCode.startsWith(" ")) {
        Toast.makeText(context, "Leading space is not allowed", Toast.LENGTH_SHORT).show()
        return
    }
    setLoading(true)
    val userId = auth.currentUser?.uid ?: ""
    val codeToJoin = classCode.trim()
    db.collection("classes").document(codeToJoin).get()
        .addOnSuccessListener { document ->
            if (document.exists()) {
                if (userId.isBlank()) {
                    setLoading(false)
                    Toast.makeText(context, "Please sign in to request access", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                val members = (document.get("members") as? List<*>)?.filterIsInstance<String>().orEmpty()
                if (userId in members) {
                    setLoading(false)
                    Toast.makeText(context, "You are already a member of this class", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                val classRef = db.collection("classes").document(codeToJoin)
                db.collection("users").document(userId).get()
                    .addOnSuccessListener { userDoc ->
                        val userName = listOfNotNull(
                            userDoc.getString("firstName"),
                            userDoc.getString("lastName")
                        ).joinToString(" ").trim().ifEmpty { auth.currentUser?.displayName ?: "Student" }
                        val adminIds = ((document.get("admins") as? List<*>)?.filterIsInstance<String>().orEmpty() +
                            (document.getString("creatorId") ?: "")).filter { it.isNotBlank() }.distinct()
                        if (adminIds.isEmpty()) {
                            setLoading(false)
                            Toast.makeText(context, "This class has no administrator to approve your request", Toast.LENGTH_LONG).show()
                            return@addOnSuccessListener
                        }
                        val requestRef = classRef.collection("joinRequests").document(userId)
                        val batch = db.batch()
                        batch.set(
                            requestRef,
                            mapOf(
                                "userId" to userId,
                                "userName" to userName,
                                "timestamp" to System.currentTimeMillis()
                            )
                        )
                        val className = document.getString("name") ?: "Class"
                        val requestConfirmation = Notification(
                            id = java.util.UUID.randomUUID().toString(),
                            title = "Join request sent",
                            content = "Your request to join $className is waiting for admin approval.",
                            timestamp = System.currentTimeMillis(),
                            authorName = userName,
                            className = className,
                            classId = codeToJoin,
                            type = "CLASS_JOIN_PENDING",
                            recipientId = userId
                        )
                        batch.set(db.collection("notifications").document(requestConfirmation.id), requestConfirmation)
                        adminIds.forEach { adminId ->
                            val notificationRef = db.collection("notifications")
                                .document("${codeToJoin}_${userId}_$adminId")
                            batch.set(
                                notificationRef,
                                mapOf(
                                    "title" to "Class join request",
                                    "content" to "$userName requested to join $className.",
                                    "timestamp" to System.currentTimeMillis(),
                                    "authorName" to userName,
                                    "className" to className,
                                    "classId" to codeToJoin,
                                    "type" to "JOIN_REQUEST",
                                    "recipientId" to adminId,
                                    "requestId" to userId
                                )
                            )
                        }
                        batch.commit()
                            .addOnSuccessListener {
                                setLoading(false)
                                Toast.makeText(context, "Request sent. An admin must approve you before you can join.", Toast.LENGTH_LONG).show()
                                onJoinRequestSent(codeToJoin)
                            }
                            .addOnFailureListener { e ->
                                setLoading(false)
                                Toast.makeText(context, "Failed to send request: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .addOnFailureListener { e ->
                        setLoading(false)
                        Toast.makeText(context, "Could not load your profile: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            } else {
                setLoading(false)
                Toast.makeText(context, "Class not found. Check the code.", Toast.LENGTH_SHORT).show()
            }
        }
        .addOnFailureListener { e ->
            setLoading(false)
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
}

@Preview(showBackground = true)
@Composable
fun JoinClassScreenPreview() {
    OMNIRATheme { JoinClassScreen() }
}
