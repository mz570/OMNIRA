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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.omnira.data.Notification
import com.omnira.ui.theme.GreenPrimary
import com.omnira.ui.theme.OMNIRATheme
import com.omnira.ui.theme.YellowSecondary
import com.omnira.ui.theme.getNeumorphicTextStyle
import com.omnira.ui.theme.neumorphic
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateClassScreen(
    onBackClick: () -> Unit = {},
    onCreateSuccess: (String) -> Unit = {}
) {
    var className by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("") }
    var batch by remember { mutableStateOf("") }
    var teacherName by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val scrollState = rememberScrollState()

    val generatedCode = remember { generateClassCode() }

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
                    .background(
                        color = GreenPrimary,
                        shape = RoundedCornerShape(bottomStart = 80.dp)
                    )
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(120.dp)
                    .background(
                        color = YellowSecondary,
                        shape = RoundedCornerShape(topStart = 120.dp)
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(if (isLandscape) 16.dp else 48.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack, 
                        contentDescription = "Back", 
                        tint = if (isLandscape) GreenPrimary else Color.White
                    )
                }
                if (!isLandscape) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "Create Class",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.weight(1.2f))
                }
            }

            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    Column(modifier = Modifier.weight(0.4f)) {
                        Text(
                            text = "Create Class",
                            style = getNeumorphicTextStyle(MaterialTheme.typography.headlineMedium, GreenPrimary)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        ClassCodeCard(generatedCode)
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = onBackClick, enabled = !isLoading) {
                            Text("Cancel", style = MaterialTheme.typography.labelLarge, color = GreenPrimary)
                        }
                    }

                    Column(modifier = Modifier.weight(0.6f)) {
                        CreateClassNeumorphicContainer {
                            CreateClassForm(
                                className, { className = it },
                                section, { section = it },
                                batch, { batch = it },
                                teacherName, { teacherName = it },
                                isLoading,
                                onCreateClick = {
                                    handleCreateClass(context, db, auth, className, section, batch, teacherName, generatedCode, { isLoading = it }, onCreateSuccess)
                                }
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(40.dp))
                CreateClassNeumorphicContainer {
                    CreateClassForm(
                        className, { className = it },
                        section, { section = it },
                        batch, { batch = it },
                        teacherName, { teacherName = it },
                        isLoading,
                        onCreateClick = {
                            handleCreateClass(context, db, auth, className, section, batch, teacherName, generatedCode, { isLoading = it }, onCreateSuccess)
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                ClassCodeCard(generatedCode)
                
                Spacer(modifier = Modifier.height(32.dp))
                TextButton(
                    onClick = onBackClick,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    enabled = !isLoading
                ) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge, color = GreenPrimary)
                }
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun CreateClassNeumorphicContainer(content: @Composable () -> Unit) {
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
fun ClassCodeCard(generatedCode: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphic(elevation = 4.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Auto-generated Class Code", style = MaterialTheme.typography.labelMedium, color = GreenPrimary)
            Text(generatedCode, style = MaterialTheme.typography.displayMedium, color = GreenPrimary)
        }
    }
}

@Composable
fun CreateClassForm(
    className: String, onClassNameChange: (String) -> Unit,
    section: String, onSectionChange: (String) -> Unit,
    batch: String, onBatchChange: (String) -> Unit,
    teacherName: String, onTeacherNameChange: (String) -> Unit,
    isLoading: Boolean,
    onCreateClick: () -> Unit
) {
    Column {
        Text("Class Name", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
        OutlinedTextField(
            value = className,
            onValueChange = onClassNameChange,
            placeholder = { Text("Enter class name", style = MaterialTheme.typography.bodyMedium) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            enabled = !isLoading,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.background,
                focusedContainerColor = MaterialTheme.colorScheme.background
            ),
            textStyle = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Section", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
        OutlinedTextField(
            value = section,
            onValueChange = onSectionChange,
            placeholder = { Text("Enter section", style = MaterialTheme.typography.bodyMedium) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            enabled = !isLoading,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.background,
                focusedContainerColor = MaterialTheme.colorScheme.background
            ),
            textStyle = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Batch", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
        OutlinedTextField(
            value = batch,
            onValueChange = onBatchChange,
            placeholder = { Text("Enter batch", style = MaterialTheme.typography.bodyMedium) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            enabled = !isLoading,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.background,
                focusedContainerColor = MaterialTheme.colorScheme.background
            ),
            textStyle = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Course Teacher", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
        OutlinedTextField(
            value = teacherName,
            onValueChange = onTeacherNameChange,
            placeholder = { Text("Enter teacher name", style = MaterialTheme.typography.bodyMedium) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            enabled = !isLoading,
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
                .height(50.dp)
                .neumorphic(elevation = 4.dp)
                .background(if (isLoading) Color.Gray else GreenPrimary, RoundedCornerShape(8.dp))
                .clickable(enabled = !isLoading) { onCreateClick() },
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            else Text("Create Class", style = MaterialTheme.typography.titleMedium, color = Color.White)
        }
    }
}

private fun handleCreateClass(
    context: android.content.Context, db: FirebaseFirestore, auth: FirebaseAuth,
    className: String, section: String, batch: String, teacherName: String,
    generatedCode: String, setLoading: (Boolean) -> Unit, onCreateSuccess: (String) -> Unit
) {
    if (className.isEmpty() || teacherName.isEmpty()) {
        Toast.makeText(context, "Class Name and Teacher Name are required", Toast.LENGTH_SHORT).show()
        return
    }

    if (className.startsWith(" ") || section.startsWith(" ") || batch.startsWith(" ") || teacherName.startsWith(" ")) {
        Toast.makeText(context, "Leading space is not allowed", Toast.LENGTH_SHORT).show()
        return
    }
    
    setLoading(true)
    val userId = auth.currentUser?.uid ?: ""
    val classData = hashMapOf(
        "name" to className.trim(),
        "section" to section.trim(),
        "batch" to batch.trim(),
        "teacher" to teacherName.trim(),
        "code" to generatedCode,
        "creatorId" to userId,
        "members" to listOf(userId)
    )
    
    val batch = db.batch()
    val classRef = db.collection("classes").document(generatedCode)
    batch.set(classRef, classData)
    val notification = Notification(
        id = java.util.UUID.randomUUID().toString(),
        title = "Class created",
        content = "You created ${className.trim()}.",
        timestamp = System.currentTimeMillis(),
        authorName = auth.currentUser?.displayName ?: "Class creator",
        className = className.trim(),
        classId = generatedCode,
        type = "CLASS_CREATED",
        recipientId = userId
    )
    batch.set(db.collection("notifications").document(notification.id), notification)
    batch.commit()
        .addOnSuccessListener {
            setLoading(false)
            onCreateSuccess(generatedCode)
        }
        .addOnFailureListener { e ->
            setLoading(false)
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
}

fun generateClassCode(): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    return (1..6)
        .map { chars[Random.nextInt(chars.length)] }
        .joinToString("")
}

@Preview(showBackground = true)
@Composable
fun CreateClassScreenPreview() {
    OMNIRATheme {
        CreateClassScreen()
    }
}
