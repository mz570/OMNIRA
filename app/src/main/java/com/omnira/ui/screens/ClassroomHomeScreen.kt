package com.omnira.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.omnira.data.*
import com.omnira.ui.theme.GreenPrimary
import com.omnira.ui.theme.OMNIRATheme
import com.omnira.ui.theme.YellowSecondary
import com.omnira.ui.theme.getNeumorphicTextStyle
import com.omnira.ui.theme.neumorphic
import com.omnira.utils.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassroomHomeScreen(
    classCode: String = "",
    initialTab: Int = 0,
    onBackClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onCalendarClick: () -> Unit = {},
    onChatClick: (String) -> Unit = {},
    onDirectMessageClick: (String) -> Unit = {},
    onInboxClick: () -> Unit = {}
) {
    var selectedTab by rememberSaveable(classCode, initialTab) { mutableIntStateOf(initialTab) }
    var className by remember(classCode) { mutableStateOf("Loading...") }
    var classBatch by remember(classCode) { mutableStateOf("") }
    var classSection by remember(classCode) { mutableStateOf("") }
    var teacherName by remember(classCode) { mutableStateOf("Loading...") }
    var classImageUrl by remember(classCode) { mutableStateOf<String?>(null) }
    var isCreator by remember(classCode) { mutableStateOf(false) }
    var isAdmin by remember(classCode) { mutableStateOf(false) }
    var currentUserName by rememberSaveable { mutableStateOf("") }
    var pendingApprovals by remember(classCode) { mutableStateOf<List<Notification>>(emptyList()) }
    var showApprovals by remember(classCode) { mutableStateOf(false) }
    var unreadChatCount by remember(classCode) { mutableIntStateOf(0) }
    var chatRetentionMonths by remember(classCode) { mutableIntStateOf(12) }
    
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }

    var isRefreshing by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val context = LocalContext.current
    val currentUserId = auth.currentUser?.uid ?: ""

    val tabs = remember {
        listOf("Stream", "Materials", "Assignments", "Routine", "Attendance", "Students")
    }

    val classImagePicker = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            Toast.makeText(context, "Updating class image...", Toast.LENGTH_SHORT).show()
            uploadToCloudinary(context, it, "classes/$classCode/profile") { url ->
                if (url != null) {
                    db.collection("classes").document(classCode).update("imageUrl", url).addOnSuccessListener {
                        classImageUrl = url
                        Toast.makeText(context, "Image updated", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    LaunchedEffect(classCode, currentUserId, refreshTrigger) {
        if (classCode.isNotEmpty()) {
            db.collection("classes").document(classCode).get().addOnSuccessListener { snapshot ->
                isRefreshing = false
                if (snapshot.exists()) {
                    className = snapshot.getString("name") ?: "Unknown"
                    classBatch = snapshot.getString("batch") ?: ""
                    classSection = snapshot.getString("section") ?: ""
                    teacherName = snapshot.getString("teacher") ?: "Unknown Teacher"
                    classImageUrl = snapshot.getString("imageUrl")
                    chatRetentionMonths = (snapshot.getLong("chatRetentionMonths") ?: 12L).toInt().coerceIn(1, 12)
                    val creatorId = snapshot.getString("creatorId") ?: ""
                    isCreator = creatorId == currentUserId
                    val adminIds = (snapshot.get("admins") as? List<*>)?.filterIsInstance<String>().orEmpty()
                    isAdmin = isCreator || currentUserId in adminIds
                }
            }.addOnFailureListener { isRefreshing = false }

            db.collection("users").document(currentUserId).get().addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val fName = doc.getString("firstName") ?: ""
                    val lName = doc.getString("lastName") ?: ""
                    currentUserName = "$fName $lName".trim().ifEmpty { "User" }
                }
            }
        }
    }

    DisposableEffect(classCode, isAdmin) {
        if (classCode.isNotEmpty() && isAdmin) {
            val registration = db.collection("notifications")
                .whereEqualTo("classId", classCode)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Toast.makeText(context, "Could not load approval requests", Toast.LENGTH_SHORT).show()
                    } else {
                        pendingApprovals = snapshot?.documents.orEmpty()
                            .mapNotNull { it.toObject(Notification::class.java)?.copy(id = it.id) }
                            .filter { it.recipientId.isEmpty() || it.recipientId == currentUserId }
                            .sortedByDescending { it.timestamp }
                    }
                }
            onDispose { registration.remove() }
        } else {
            pendingApprovals = emptyList()
            onDispose { }
        }
    }

    DisposableEffect(classCode, currentUserId, chatRetentionMonths) {
        val cutoff = Calendar.getInstance().apply { add(Calendar.MONTH, -chatRetentionMonths) }.timeInMillis
        val registration = db.collection("classes").document(classCode).collection("chats")
            .whereGreaterThanOrEqualTo("timestamp", cutoff)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(context, "Could not load unread chat count", Toast.LENGTH_SHORT).show()
                } else {
                    unreadChatCount = snapshot?.documents.orEmpty().count { message ->
                        message.getString("senderId") != currentUserId &&
                            currentUserId !in (message.get("readBy") as? List<*>).orEmpty()
                    }
                }
            }
        onDispose { registration.remove() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Classroom", style = MaterialTheme.typography.titleLarge)
                        Text("Code: $classCode", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.9f))
                    }
                },
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) } },
                actions = {
                    if (isAdmin) {
                        BadgedBox(
                            badge = {
                                if (pendingApprovals.isNotEmpty()) {
                                    val unreadCount = pendingApprovals.count { currentUserId !in it.readBy }
                                    if (unreadCount > 0) Badge { Text(unreadCount.toString()) }
                                }
                            }
                        ) {
                            IconButton(onClick = { showApprovals = true }) {
                                Icon(Icons.Default.Notifications, "Class approval requests", tint = Color.White)
                            }
                        }
                    }
                    if (isCreator) {
                        IconButton(onClick = { showDeleteConfirm = true }) { 
                            Icon(Icons.Default.Delete, "Delete Class", tint = Color.White) 
                        }
                    } else {
                        IconButton(onClick = { showLeaveConfirm = true }) { 
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, "Leave Class", tint = Color.White) 
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GreenPrimary, titleContentColor = Color.White)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = GreenPrimary) {
                NavigationBarItem(
                    selected = true, 
                    onClick = onBackClick, 
                    icon = { Icon(Icons.Default.Home, null, tint = Color.White) }, 
                    label = { Text("Home", color = Color.White, style = MaterialTheme.typography.labelSmall) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = Color.White.copy(alpha = 0.2f))
                )
                NavigationBarItem(
                    selected = false, 
                    onClick = onCalendarClick, 
                    icon = { Icon(Icons.Outlined.CalendarMonth, null, tint = Color.White) }, 
                    label = { Text("Calendar", color = Color.White, style = MaterialTheme.typography.labelSmall) }
                )
                NavigationBarItem(
                    selected = false, 
                    onClick = { onChatClick(classCode) }, 
                    icon = {
                        BadgedBox(badge = { if (unreadChatCount > 0) Badge { Text(unreadChatCount.toString()) } }) {
                            Icon(Icons.AutoMirrored.Outlined.Chat, null, tint = Color.White)
                        }
                    },
                    label = { Text("Chat", color = Color.White, style = MaterialTheme.typography.labelSmall) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onInboxClick,
                    icon = { Icon(Icons.Default.Inbox, null, tint = Color.White) },
                    label = { Text("Inbox", color = Color.White, style = MaterialTheme.typography.labelSmall) }
                )
                NavigationBarItem(
                    selected = false, 
                    onClick = onProfileClick, 
                    icon = { Icon(Icons.Outlined.Person, null, tint = Color.White) }, 
                    label = { Text("Profile", color = Color.White, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true; refreshTrigger++ },
            modifier = Modifier.padding(innerPadding).fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                HeaderCard(
                    className = className,
                    section = classSection,
                    batch = classBatch,
                    teacherName = teacherName,
                    imageUrl = classImageUrl,
                    isAdmin = isAdmin,
                    onImageClick = { classImagePicker.launch("image/*") }
                )

                ScrollableTabRow(
                    selectedTabIndex = selectedTab.coerceAtMost(tabs.size - 1),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = GreenPrimary,
                    edgePadding = 16.dp,
                    indicator = { tabPositions ->
                        if (selectedTab < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTab]), 
                                color = GreenPrimary,
                                height = 3.dp
                            )
                        }
                    },
                    divider = { HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f)) }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { 
                                Text(
                                    title, 
                                    style = if (selectedTab == index) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                                    color = if (selectedTab == index) GreenPrimary else Color.Gray
                                ) 
                            }
                        )
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    val currentTab = tabs.getOrElse(selectedTab) { "Stream" }
                    if (currentTab == "Students") {
                        StudentsContent(classCode, refreshTrigger, currentUserId, onDirectMessageClick)
                    } else {
                        ContentSwitch(
                            tab = currentTab,
                            code = classCode,
                            uid = currentUserId,
                            name = currentUserName,
                            isAdmin = isAdmin,
                            className = className,
                            classBatch = classBatch,
                            trigger = refreshTrigger
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Class?", style = MaterialTheme.typography.titleLarge) },
            text = { Text("This will permanently remove the class and all its data. This action cannot be undone.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                Button(
                    onClick = {
                        db.collection("classes").document(classCode).delete().addOnSuccessListener {
                            Toast.makeText(context, "Class deleted", Toast.LENGTH_SHORT).show()
                            onBackClick()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("Delete", color = Color.White, style = MaterialTheme.typography.labelLarge) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel", style = MaterialTheme.typography.labelLarge) } }
        )
    }

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text("Leave Class?", style = MaterialTheme.typography.titleLarge) },
            text = { Text("Are you sure you want to leave this class?", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                Button(
                    onClick = {
                        db.collection("classes").document(classCode)
                            .update("members", FieldValue.arrayRemove(currentUserId))
                            .addOnSuccessListener {
                                Toast.makeText(context, "You left the class", Toast.LENGTH_SHORT).show()
                                onBackClick()
                            }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("Leave", color = Color.White, style = MaterialTheme.typography.labelLarge) }
            },
            dismissButton = { TextButton(onClick = { showLeaveConfirm = false }) { Text("Cancel", style = MaterialTheme.typography.labelLarge) } }
        )
    }

    if (showApprovals) {
        AlertDialog(
            onDismissRequest = { showApprovals = false },
            title = { Text("Class Notifications (${pendingApprovals.size})") },
            text = {
                if (pendingApprovals.isEmpty()) {
                    Text("There are no class notifications.")
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(pendingApprovals, key = { it.id }) { approval ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (currentUserId !in approval.readBy) {
                                            db.collection("notifications").document(approval.id)
                                                .update("readBy", FieldValue.arrayUnion(currentUserId))
                                                .addOnSuccessListener {
                                                    pendingApprovals = pendingApprovals.map {
                                                        if (it.id == approval.id) it.copy(readBy = it.readBy + currentUserId) else it
                                                    }
                                                }
                                                .addOnFailureListener {
                                                    Toast.makeText(context, "Could not mark alert as read", Toast.LENGTH_SHORT).show()
                                                }
                                        }
                                    }
                            ) {
                                Text(approval.title, style = MaterialTheme.typography.titleSmall)
                                Text(approval.content, style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                    if (approval.type == "CHAT_REPORT") {
                                        TextButton(onClick = {
                                            processClassApproval(db, approval, approve = false) { success ->
                                                Toast.makeText(
                                                    context,
                                                    if (success) "Report dismissed" else "Could not dismiss report",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }) { Text("Dismiss") }
                                    } else if (approval.type in setOf("JOIN_REQUEST", "POST_APPROVAL", "COMMENT_APPROVAL")) {
                                        TextButton(onClick = {
                                            processClassApproval(db, approval, approve = false) { success ->
                                                Toast.makeText(
                                                    context,
                                                    if (success) "Request declined" else "Could not decline request",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }) { Text("Decline", color = Color.Red) }
                                        TextButton(onClick = {
                                            processClassApproval(db, approval, approve = true) { success ->
                                                Toast.makeText(
                                                    context,
                                                    if (success) "Request approved" else "Could not approve request",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }) { Text("Approve") }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showApprovals = false }) { Text("Done") }
            }
        )
    }
}

fun processClassApproval(
    db: FirebaseFirestore,
    notification: Notification,
    approve: Boolean,
    onComplete: (Boolean) -> Unit
) {
    val classRef = db.collection("classes").document(notification.classId)
    val batch = db.batch()
    when (notification.type) {
        "JOIN_REQUEST" -> {
            if (approve) batch.update(classRef, "members", FieldValue.arrayUnion(notification.requestId))
            batch.delete(classRef.collection("joinRequests").document(notification.requestId))
            val resultNotification = Notification(
                id = UUID.randomUUID().toString(),
                title = if (approve) "Join request approved" else "Join request declined",
                content = if (approve) {
                    "Your request to join ${notification.className} was approved."
                } else {
                    "Your request to join ${notification.className} was declined."
                },
                timestamp = System.currentTimeMillis(),
                authorName = "Class admin",
                className = notification.className,
                classId = notification.classId,
                type = "CLASS_JOIN_RESULT",
                recipientId = notification.requestId
            )
            batch.set(db.collection("notifications").document(resultNotification.id), resultNotification)
        }
        "POST_APPROVAL" -> {
            val postRef = classRef.collection("posts").document(notification.postId)
            if (approve) batch.update(postRef, "approved", true) else batch.delete(postRef)
        }
        "COMMENT_APPROVAL" -> {
            val commentRef = classRef.collection("posts").document(notification.postId)
                .collection("comments").document(notification.commentId)
            if (approve) batch.update(commentRef, "approved", true) else batch.delete(commentRef)
        }
        "CHAT_REPORT" -> {
            batch.delete(db.collection("notifications").document(notification.id))
        }
        else -> {
            onComplete(false)
            return
        }
    }

    db.collection("notifications")
        .whereEqualTo("classId", notification.classId)
        .whereEqualTo("type", notification.type)
        .whereEqualTo("requestId", notification.requestId)
        .get()
        .addOnSuccessListener { snapshot ->
            snapshot.documents.forEach { batch.delete(it.reference) }
            batch.commit().addOnSuccessListener { onComplete(true) }
                .addOnFailureListener { onComplete(false) }
        }
        .addOnFailureListener { onComplete(false) }
}

private fun postAutoNotification(
    db: FirebaseFirestore,
    classId: String,
    className: String,
    authorName: String,
    title: String,
    content: String,
    type: String = "GENERAL"
) {
    val notification = Notification(
        id = UUID.randomUUID().toString(),
        title = title,
        content = content,
        timestamp = System.currentTimeMillis(),
        authorName = authorName,
        className = className,
        classId = classId,
        type = type
    )
    db.collection("notifications").document(notification.id).set(notification)
}

private fun notifyClassUser(
    db: FirebaseFirestore,
    context: android.content.Context,
    classId: String,
    recipientId: String,
    title: String,
    content: String,
    authorName: String = "Class admin"
) {
    db.collection("classes").document(classId).get()
        .addOnSuccessListener { classSnapshot ->
            val notification = Notification(
                id = UUID.randomUUID().toString(),
                title = title,
                content = content,
                timestamp = System.currentTimeMillis(),
                authorName = authorName,
                className = classSnapshot.getString("name") ?: "Class",
                classId = classId,
                type = "CLASS_MEMBER_UPDATE",
                recipientId = recipientId
            )
            db.collection("notifications").document(notification.id).set(notification)
                .addOnFailureListener {
                    Toast.makeText(context, "Could not send class notification", Toast.LENGTH_SHORT).show()
                }
        }
        .addOnFailureListener {
            Toast.makeText(context, "Could not load class details for notification", Toast.LENGTH_SHORT).show()
        }
}

@Composable
fun HeaderCard(
    className: String, 
    section: String, 
    batch: String, 
    teacherName: String, 
    imageUrl: String? = null, 
    isAdmin: Boolean = false, 
    onImageClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .neumorphic(elevation = 6.dp, shape = RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
            .padding(24.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF1F3F4))
                        .then(if (isAdmin) Modifier.clickable { onImageClick() } else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    if (!imageUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = "Class Image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Default.Groups, null, tint = GreenPrimary.copy(alpha = 0.7f), modifier = Modifier.size(32.dp))
                    }
                    
                    if (isAdmin) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            Icon(
                                Icons.Default.Edit, 
                                null, 
                                tint = Color.White, 
                                modifier = Modifier.size(16.dp).padding(4.dp).background(GreenPrimary, CircleShape).padding(2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(20.dp))
                Column {
                    Text(className.uppercase(), style = getNeumorphicTextStyle(MaterialTheme.typography.headlineMedium, GreenPrimary), lineHeight = 26.sp)
                    val infoText = buildString {
                        if (section.isNotEmpty()) append(section)
                        if (section.isNotEmpty() && batch.isNotEmpty()) append(" | ")
                        if (batch.isNotEmpty()) append(batch)
                    }
                    if (infoText.isNotEmpty()) {
                        Text(infoText, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.School, null, tint = GreenPrimary, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = "Teacher: $teacherName", style = MaterialTheme.typography.titleMedium, color = Color.DarkGray)
            }
        }
    }
}

private fun createClassApprovalNotifications(
    db: FirebaseFirestore,
    context: android.content.Context,
    classId: String,
    title: String,
    content: String,
    type: String,
    requestId: String,
    postId: String = "",
    commentId: String = ""
) {
    db.collection("classes").document(classId).get()
        .addOnSuccessListener { classSnapshot ->
            val adminIds = (
                (classSnapshot.get("admins") as? List<*>)?.filterIsInstance<String>().orEmpty() +
                    (classSnapshot.getString("creatorId") ?: "")
                ).filter { it.isNotBlank() }.distinct()
            if (adminIds.isEmpty()) {
                Toast.makeText(context, "No class admin found to receive this approval request", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }
            val timestamp = System.currentTimeMillis()
            val batch = db.batch()
            adminIds.forEach { adminId ->
                val notificationRef = db.collection("notifications")
                    .document("${classId}_${type}_${requestId}_${adminId}")
                batch.set(
                    notificationRef,
                    mapOf(
                        "title" to title,
                        "content" to content,
                        "timestamp" to timestamp,
                        "authorName" to "",
                        "className" to (classSnapshot.getString("name") ?: "Class"),
                        "classId" to classId,
                        "type" to type,
                        "recipientId" to adminId,
                        "requestId" to requestId,
                        "postId" to postId,
                        "commentId" to commentId
                    )
                )
            }
            batch.commit().addOnFailureListener {
                Toast.makeText(context, "Could not send approval notification", Toast.LENGTH_SHORT).show()
            }
        }
        .addOnFailureListener {
            Toast.makeText(context, "Could not find class admins for approval", Toast.LENGTH_SHORT).show()
        }
}

@Composable
fun ContentSwitch(
    tab: String, code: String, uid: String, name: String, isAdmin: Boolean, className: String, classBatch: String, trigger: Int
) {
    when (tab) {
        "Stream" -> StreamContent(code, uid, name, isAdmin, trigger, className)
        "Materials" -> MaterialsContent(code, uid, name, isAdmin, trigger, className)
        "Assignments" -> AssignmentsContent(code, uid, name, isAdmin, trigger, className)
        "Routine" -> RoutineContent(code, isAdmin, className, trigger, name)
        "Attendance" -> AttendanceContent(code, isAdmin, className, classBatch, trigger)
    }
}

@Composable
fun StudentsContent(
    classCode: String,
    trigger: Int,
    currentUserId: String = FirebaseAuth.getInstance().currentUser?.uid.orEmpty(),
    onMessageClick: (String) -> Unit = {}
) {
    var admins by remember { mutableStateOf<List<ClassMember>>(emptyList()) }
    var classmates by remember { mutableStateOf<List<ClassMember>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedMember by remember { mutableStateOf<ClassMember?>(null) }
    var creatorId by remember { mutableStateOf("") }
    var adminIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    val isCurrentUserAdmin = currentUserId == creatorId || currentUserId in adminIds

    LaunchedEffect(classCode, trigger) {
        isLoading = true
        db.collection("classes").document(classCode).get().addOnSuccessListener { doc ->
            val members = (doc.get("members") as? List<*>)?.filterIsInstance<String>().orEmpty()
            creatorId = doc.getString("creatorId")?.trim().orEmpty()
            adminIds = (doc.get("admins") as? List<*>)?.filterIsInstance<String>()
                ?.filter { it.isNotBlank() }?.toSet().orEmpty()
            val effectiveAdminIds = (adminIds + creatorId).filter { it.isNotBlank() }.distinct()
            val studentIds = members.filter { it.isNotBlank() && it !in effectiveAdminIds }.distinct()
            val profileIds = (effectiveAdminIds + studentIds).distinct()

            if (profileIds.isNotEmpty()) {
                val chunks = profileIds.chunked(30)
                val fetchedProfiles = mutableListOf<ClassMember>()
                var chunksProcessed = 0

                chunks.forEach { chunk ->
                    db.collection("users").whereIn(FieldPath.documentId(), chunk).get().addOnSuccessListener { snapshot ->
                        val profileMap = snapshot.documents.associateBy { it.id }
                        chunk.forEach { uid ->
                            val userDoc = profileMap[uid]
                            val fName = userDoc?.getString("firstName") ?: ""
                            val lName = userDoc?.getString("lastName") ?: ""
                            val fallbackName = if (uid == creatorId || uid in adminIds) "Admin" else "Student"
                            fetchedProfiles.add(
                                ClassMember(
                                    "$fName $lName".trim().ifEmpty { "$fallbackName (${uid.take(5)})" },
                                    userDoc?.getString("profileImageUrl"),
                                    uid
                                )
                            )
                        }
                        chunksProcessed++
                        if (chunksProcessed == chunks.size) {
                            val profileById = fetchedProfiles.associateBy { it.uid }
                            admins = effectiveAdminIds.mapNotNull(profileById::get)
                            classmates = studentIds.mapNotNull(profileById::get)
                            isLoading = false
                        }
                    }.addOnFailureListener {
                        chunksProcessed++
                        if (chunksProcessed == chunks.size) {
                            isLoading = false
                            Toast.makeText(context, "Profile load error", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                admins = emptyList()
                classmates = emptyList()
                isLoading = false
            }
        }.addOnFailureListener {
            isLoading = false
            Toast.makeText(context, "Member load error", Toast.LENGTH_SHORT).show()
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = GreenPrimary)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                Column {
                    Text("Admins", style = MaterialTheme.typography.titleMedium, color = GreenPrimary, modifier = Modifier.padding(horizontal = 8.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    if (admins.isEmpty()) {
                        Text("No admins found.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray, modifier = Modifier.padding(8.dp))
                    }
                    admins.forEach { admin ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .neumorphic(elevation = 4.dp, shape = RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                .then(if (admin.uid != currentUserId) Modifier.clickable { onMessageClick(admin.uid) } else Modifier)
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF2E7D32)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!admin.profileImageUrl.isNullOrEmpty()) {
                                        AsyncImage(
                                            model = admin.profileImageUrl,
                                            contentDescription = "Admin Profile",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Text(admin.name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = Color.White)
                                    }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    "${admin.name}${if (admin.uid == creatorId) " · Creator" else ""}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.Black,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isCurrentUserAdmin && admin.uid != creatorId && admin.uid != currentUserId) {
                                    IconButton(onClick = { selectedMember = admin }) {
                                        Icon(Icons.Default.MoreVert, "Manage admin", tint = Color.Gray)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            item {
                Text("Classmates", style = MaterialTheme.typography.titleMedium, color = GreenPrimary, modifier = Modifier.padding(horizontal = 8.dp))
            }
            
            if (classmates.isEmpty()) {
                item { Text("No classmates joined yet.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray, modifier = Modifier.padding(8.dp)) }
            } else {
                items(classmates) { member ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .neumorphic(elevation = 3.dp, shape = RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                            .then(if (member.uid != currentUserId) Modifier.clickable { onMessageClick(member.uid) } else Modifier)
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2E7D32)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!member.profileImageUrl.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = member.profileImageUrl,
                                        contentDescription = "Profile",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(member.name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = Color.White)
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(member.name, style = MaterialTheme.typography.bodyLarge, color = Color.Black, modifier = Modifier.weight(1f))
                            if (isCurrentUserAdmin) {
                                IconButton(onClick = { selectedMember = member }) {
                                    Icon(Icons.Default.MoreVert, "Manage member", tint = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    selectedMember?.let { member ->
        val memberIsAdmin = member.uid in adminIds
        val canDemote = memberIsAdmin && (member.uid != creatorId || adminIds.any { it != member.uid })
        AlertDialog(
            onDismissRequest = { selectedMember = null },
            title = { Text(member.name) },
            text = { Text(if (memberIsAdmin) "Manage this classroom admin." else "Manage this classroom member.") },
            confirmButton = {
                if (memberIsAdmin && canDemote) {
                    TextButton(onClick = {
                        db.collection("classes").document(classCode)
                            .update("admins", FieldValue.arrayRemove(member.uid))
                            .addOnSuccessListener {
                                adminIds = adminIds - member.uid
                                admins = admins.filterNot { it.uid == member.uid }
                                classmates = classmates + member
                                selectedMember = null
                                notifyClassUser(
                                    db, context, classCode, member.uid, "Admin role removed",
                                    "You are no longer an admin of this class."
                                )
                                Toast.makeText(context, "Admin demoted", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener {
                                Toast.makeText(context, "Could not demote admin", Toast.LENGTH_SHORT).show()
                            }
                    }) { Text("Demote") }
                } else if (!memberIsAdmin) {
                    TextButton(onClick = {
                        db.collection("classes").document(classCode)
                            .update("admins", FieldValue.arrayUnion(member.uid))
                            .addOnSuccessListener {
                                adminIds = adminIds + member.uid
                                classmates = classmates.filterNot { it.uid == member.uid }
                                admins = admins + member
                                selectedMember = null
                                notifyClassUser(
                                    db, context, classCode, member.uid, "Promoted to admin",
                                    "You have been promoted to admin of this class."
                                )
                                Toast.makeText(context, "Promoted to admin", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener {
                                Toast.makeText(context, "Could not promote member", Toast.LENGTH_SHORT).show()
                            }
                    }) { Text("Promote") }
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { selectedMember = null }) { Text("Cancel") }
                    TextButton(onClick = {
                        db.collection("classes").document(classCode).update(
                            mapOf(
                                "members" to FieldValue.arrayRemove(member.uid),
                                "admins" to FieldValue.arrayRemove(member.uid)
                            )
                        ).addOnSuccessListener {
                            adminIds = adminIds - member.uid
                            admins = admins.filterNot { it.uid == member.uid }
                            classmates = classmates.filterNot { it.uid == member.uid }
                            selectedMember = null
                            notifyClassUser(
                                db, context, classCode, member.uid, "Removed from class",
                                "You have been removed from this class."
                            )
                            Toast.makeText(context, "Member removed from classroom", Toast.LENGTH_SHORT).show()
                        }.addOnFailureListener {
                            Toast.makeText(context, "Could not remove member", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("Remove", color = Color.Red) }
                }
            }
        )
    }
}

@Composable
fun StreamContent(classCode: String, userId: String, userName: String, isTeacher: Boolean, trigger: Int, className: String) {
    var posts by remember { mutableStateOf<List<Post>>(emptyList()) }
    var newPostContent by remember { mutableStateOf("") }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    
    // Pagination States
    var isLoading by remember { mutableStateOf(false) }
    var lastDocument by remember { mutableStateOf<DocumentSnapshot?>(null) }
    var isLastPage by remember { mutableStateOf(false) }
    val pageSize = 10L

    var postToDelete by remember { mutableStateOf<Post?>(null) }

    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()

    val filePicker = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedFileUri = it
            selectedFileName = getFileName(context, it)
        }
    }

    fun fetchPosts(isFirstPage: Boolean = false) {
        if (isLoading || (isLastPage && !isFirstPage)) return
        isLoading = true

        var query = db.collection("classes").document(classCode).collection("posts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(pageSize)

        if (!isFirstPage && lastDocument != null) {
            query = query.startAfter(lastDocument!!)
        }

        query.get().addOnSuccessListener { snapshot ->
            val allBatch = snapshot.documents.mapNotNull { it.toObject(Post::class.java)?.copy(id = it.id) }
            val filteredBatch = if (isTeacher) allBatch else allBatch.filter { it.isApproved || it.authorId == userId }
            
            if (isFirstPage) {
                posts = filteredBatch
            } else {
                posts = posts + filteredBatch
            }
            
            lastDocument = snapshot.documents.lastOrNull()
            isLastPage = snapshot.size() < pageSize
            isLoading = false
        }.addOnFailureListener { isLoading = false }
    }

    LaunchedEffect(classCode, trigger) {
        lastDocument = null
        isLastPage = false
        fetchPosts(isFirstPage = true)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .neumorphic(elevation = 6.dp, shape = RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Column {
                    OutlinedTextField(
                        value = newPostContent,
                        onValueChange = { newPostContent = it },
                        placeholder = { Text("Share something with your class...", style = MaterialTheme.typography.bodyMedium) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GreenPrimary, 
                            unfocusedBorderColor = Color.LightGray.copy(alpha = 0.5f),
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                            unfocusedContainerColor = MaterialTheme.colorScheme.background,
                            focusedContainerColor = MaterialTheme.colorScheme.background
                        ),
                        shape = RoundedCornerShape(12.dp),
                        textStyle = MaterialTheme.typography.bodyLarge
                    )
                    
                    selectedFileName?.let { name ->
                        Row(modifier = Modifier.padding(top = 8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AttachFile, null, tint = GreenPrimary, modifier = Modifier.size(16.dp))
                            Text(name, modifier = Modifier.weight(1f).padding(horizontal = 8.dp), maxLines = 1, style = MaterialTheme.typography.bodyMedium, color = Color.Black)
                            IconButton(onClick = { selectedFileUri = null; selectedFileName = null }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { filePicker.launch("*/*") }) { Icon(Icons.Default.AddPhotoAlternate, "Attach File", tint = GreenPrimary) }
                        
                        Box(
                            modifier = Modifier
                                .height(40.dp)
                                .widthIn(min = 80.dp)
                                .neumorphic(elevation = 3.dp, shape = RoundedCornerShape(8.dp))
                                .background(if (!isUploading && (newPostContent.isNotBlank() || selectedFileUri != null)) GreenPrimary else Color.Gray, RoundedCornerShape(8.dp))
                                .clickable(enabled = !isUploading && (newPostContent.isNotBlank() || selectedFileUri != null)) {
                                    isUploading = true
                                    val sendPost = { fileUrl: String?, fileName: String? ->
                                        val post = Post(
                                            authorId = userId,
                                            authorName = userName,
                                            classId = classCode,
                                            className = className,
                                            content = newPostContent.trim(),
                                            timestamp = System.currentTimeMillis(),
                                            fileUrl = fileUrl,
                                            fileName = fileName,
                                            isApproved = isTeacher
                                        )
                                        db.collection("classes").document(classCode).collection("posts")
                                            .add(post).addOnSuccessListener { postRef ->
                                                if (isTeacher) {
                                                    postAutoNotification(db, classCode, className, userName, "New Stream Update", "A new post has been added to the stream: ${newPostContent.take(50)}...", "POST")
                                                } else {
                                                    createClassApprovalNotifications(
                                                        db, context, classCode,
                                                        "Stream post approval",
                                                        "$userName submitted a post for approval.",
                                                        "POST_APPROVAL", postRef.id, postRef.id
                                                    )
                                                }
                                                newPostContent = ""; selectedFileUri = null; selectedFileName = null; isUploading = false
                                                if (!isTeacher) Toast.makeText(context, "Post sent for approval", Toast.LENGTH_SHORT).show()
                                                fetchPosts(isFirstPage = true)
                                            }.addOnFailureListener { isUploading = false; Toast.makeText(context, "Failed to post", Toast.LENGTH_SHORT).show() }
                                    }
                                    val uri = selectedFileUri
                                    if (uri != null) {
                                        val dedicatedFolder = if (isImage(selectedFileName)) "users/$userId/images" else "users/$userId/posts"
                                        uploadToCloudinary(context, uri, dedicatedFolder) { url ->
                                            if (url != null) sendPost(url, selectedFileName)
                                            else { isUploading = false; Toast.makeText(context, "Upload failed", Toast.LENGTH_SHORT).show() }
                                        }
                                    } else sendPost(null, null)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isUploading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            else Text("Post", color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
        
        itemsIndexed(posts, key = { _, post -> post.id }) { index, post ->
            PostItem(
                post = post,
                classCode = classCode,
                userId = userId,
                userName = userName,
                isTeacher = isTeacher,
                className = className,
                onDeleteClick = { postToDelete = post }
            )
            
            if (index >= posts.size - 1 && !isLastPage && !isLoading) {
                LaunchedEffect(Unit) { fetchPosts() }
            }
        }
        
        if (isLoading && posts.isNotEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GreenPrimary, modifier = Modifier.size(24.dp))
                }
            }
        }
    }

    postToDelete?.let { post ->
        AlertDialog(
            onDismissRequest = { postToDelete = null },
            title = { Text("Delete Post?", style = MaterialTheme.typography.titleLarge) },
            text = { Text("Are you sure you want to delete this post?", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    db.collection("classes").document(classCode).collection("posts").document(post.id).delete()
                        .addOnSuccessListener {
                            Toast.makeText(context, "Post deleted", Toast.LENGTH_SHORT).show()
                            posts = posts.filter { it.id != post.id }
                            postToDelete = null
                        }
                }) { Text("Delete", color = Color.Red, style = MaterialTheme.typography.labelLarge) }
            },
            dismissButton = { TextButton(onClick = { postToDelete = null }) { Text("Cancel", style = MaterialTheme.typography.labelLarge) } }
        )
    }
}

@Composable
fun PostItem(
    post: Post,
    classCode: String,
    userId: String,
    userName: String,
    isTeacher: Boolean,
    className: String,
    onDeleteClick: () -> Unit
) {
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var newCommentContent by remember { mutableStateOf("") }
    var authorImageUrl by remember { mutableStateOf<String?>(null) }
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current

    LaunchedEffect(post.authorId) {
        if (post.authorId.isNotEmpty()) {
            db.collection("users").document(post.authorId).get()
                .addOnSuccessListener { authorImageUrl = it.getString("profileImageUrl") }
        }
    }

    LaunchedEffect(post.id) {
        db.collection("classes").document(classCode).collection("posts").document(post.id)
            .collection("comments").orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val allComments = snapshot.documents.mapNotNull { it.toObject(Comment::class.java)?.copy(id = it.id) }
                    comments = if (isTeacher) allComments else allComments.filter { it.isApproved || it.authorId == userId }
                }
            }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphic(elevation = 4.dp, shape = RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        val approved = post.isApproved
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2E7D32)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!authorImageUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = authorImageUrl,
                            contentDescription = "Author Profile",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(post.authorName.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = Color.White)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(post.authorName, style = MaterialTheme.typography.titleMedium, color = Color.Black)
                    if (!approved) Text("Pending Approval", style = MaterialTheme.typography.labelSmall, color = Color(0xFFD4AF37), fontWeight = FontWeight.Bold)
                }
                if (isTeacher) {
                    Row {
                        IconButton(onClick = onDeleteClick, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, "Delete", tint = Color.Red.copy(alpha = 0.7f))
                        }
                    }
                }
            }
            Text(post.content, style = MaterialTheme.typography.bodyLarge, color = Color.DarkGray, modifier = Modifier.padding(top = 8.dp))
            post.fileName?.let { name ->
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .neumorphic(elevation = 2.dp, shape = RoundedCornerShape(8.dp))
                        .background(Color(0xFFF1F3F4), RoundedCornerShape(8.dp))
                        .clickable { post.fileUrl?.let { url -> downloadFile(context, url, post.fileName) } }
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AttachFile, null, tint = GreenPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(name, style = MaterialTheme.typography.bodyMedium, color = Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.Download, null, tint = GreenPrimary, modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (comments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(12.dp))
                comments.forEach { comment ->
                    CommentItem(comment, isTeacher, classCode, post.id)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newCommentContent, onValueChange = { newCommentContent = it },
                    placeholder = { Text("Add a comment...", style = MaterialTheme.typography.bodyMedium, color = Color.Gray) },
                    modifier = Modifier.weight(1f).height(48.dp), 
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black, 
                        unfocusedTextColor = Color.Black, 
                        focusedBorderColor = Color.LightGray, 
                        unfocusedBorderColor = Color(0xFFF1F3F4),
                        unfocusedContainerColor = MaterialTheme.colorScheme.background,
                        focusedContainerColor = MaterialTheme.colorScheme.background
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (newCommentContent.isNotBlank()) {
                            val comment = Comment(authorId = userId, authorName = userName, content = newCommentContent.trim(), timestamp = System.currentTimeMillis(), isApproved = isTeacher)
                            db.collection("classes").document(classCode).collection("posts").document(post.id).collection("comments")
                                .add(comment)
                                .addOnSuccessListener { commentRef ->
                                    if (!isTeacher) {
                                        createClassApprovalNotifications(
                                            db, context, classCode,
                                            "Stream comment approval",
                                            "$userName submitted a comment for approval.",
                                            "COMMENT_APPROVAL",
                                            "${post.id}_${commentRef.id}",
                                            post.id,
                                            commentRef.id
                                        )
                                        Toast.makeText(context, "Comment sent for approval", Toast.LENGTH_SHORT).show()
                                    }
                                    newCommentContent = ""
                                }
                                .addOnFailureListener {
                                    Toast.makeText(context, "Could not submit comment", Toast.LENGTH_SHORT).show()
                                }
                        }
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .background(if (newCommentContent.isNotBlank()) GreenPrimary else Color.Gray.copy(alpha = 0.3f), CircleShape)
                ) { Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Color.White, modifier = Modifier.size(18.dp)) }
            }
        }
    }
}

@Composable
fun CommentItem(comment: Comment, isTeacher: Boolean, classCode: String, postId: String) {
    var commenterImageUrl by remember { mutableStateOf<String?>(null) }
    val db = FirebaseFirestore.getInstance()
    
    LaunchedEffect(comment.authorId) {
        if (!comment.authorId.isNullOrEmpty()) {
            db.collection("users").document(comment.authorId).get()
                .addOnSuccessListener { commenterImageUrl = it.getString("profileImageUrl") }
        }
    }

    Column(modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2E7D32)),
                contentAlignment = Alignment.Center
            ) {
                if (!commenterImageUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = commenterImageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(comment.authorName.take(1).uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(comment.authorName, style = MaterialTheme.typography.titleSmall, color = Color.Black)
                if (!comment.isApproved) Text("Pending Approval", style = MaterialTheme.typography.labelSmall, color = Color(0xFFD4AF37))
            }
        }
        Text(comment.content, style = MaterialTheme.typography.bodyMedium, color = Color.Gray, modifier = Modifier.padding(start = 38.dp))
    }
}

@Composable
fun MaterialsContent(classCode: String, userId: String, authorName: String, isAdmin: Boolean, trigger: Int, className: String) {
    var materials by remember { mutableStateOf<List<Post>>(emptyList()) }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    
    // Pagination
    var isLoading by remember { mutableStateOf(false) }
    var lastDoc by remember { mutableStateOf<DocumentSnapshot?>(null) }
    var isLastPage by remember { mutableStateOf(false) }
    val pageSize = 15L

    var itemToDelete by remember { mutableStateOf<Post?>(null) }

    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()

    val filePicker = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedFileUri = it
            selectedFileName = getFileName(context, it)
        }
    }

    fun fetchMaterials(isFirstPage: Boolean = false) {
        if (isLoading || (isLastPage && !isFirstPage)) return
        isLoading = true
        var query = db.collection("classes").document(classCode).collection("materials")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(pageSize)
        if (!isFirstPage && lastDoc != null) query = query.startAfter(lastDoc!!)
        
        query.get().addOnSuccessListener { snapshot ->
            val newBatch = snapshot.documents.mapNotNull { it.toObject(Post::class.java)?.copy(id = it.id) }
            materials = if (isFirstPage) newBatch else materials + newBatch
            lastDoc = snapshot.documents.lastOrNull()
            isLastPage = snapshot.size() < pageSize
            isLoading = false
        }.addOnFailureListener { isLoading = false }
    }

    LaunchedEffect(classCode, trigger) {
        lastDoc = null
        isLastPage = false
        fetchMaterials(isFirstPage = true)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        if (isAdmin) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .neumorphic(elevation = 6.dp, shape = RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Text("Upload Material", style = MaterialTheme.typography.titleMedium, color = GreenPrimary)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(45.dp)
                                .neumorphic(elevation = 2.dp, shape = RoundedCornerShape(8.dp))
                                .background(Color(0xFFF1F3F4), RoundedCornerShape(8.dp))
                                .clickable { filePicker.launch("*/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AttachFile, null, tint = GreenPrimary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(selectedFileName ?: "Choose File or Photo", style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
                            }
                        }

                        val uri = selectedFileUri
                        if (uri != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Box(
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .height(40.dp)
                                    .widthIn(min = 100.dp)
                                    .neumorphic(elevation = 4.dp, shape = RoundedCornerShape(8.dp))
                                    .background(GreenPrimary, RoundedCornerShape(8.dp))
                                    .clickable(enabled = !isUploading) {
                                        isUploading = true
                                        val dedicatedFolder = if (isImage(selectedFileName)) "users/$userId/images" else "users/$userId/materials"
                                        uploadToCloudinary(context, uri, dedicatedFolder) { url ->
                                            if (url != null) {
                                                val post = Post(
                                                    authorId = userId,
                                                    authorName = authorName,
                                                    classId = classCode,
                                                    className = className,
                                                    timestamp = System.currentTimeMillis(),
                                                    fileUrl = url,
                                                    fileName = selectedFileName,
                                                    isApproved = true
                                                )
                                                db.collection("classes").document(classCode).collection("materials")
                                                    .add(post).addOnSuccessListener {
                                                        postAutoNotification(db, classCode, className, authorName, "New Material Uploaded", "New class material: $selectedFileName", "MATERIAL")
                                                        selectedFileUri = null
                                                        selectedFileName = null
                                                        isUploading = false
                                                        Toast.makeText(context, "Material uploaded successfully", Toast.LENGTH_SHORT).show()
                                                        fetchMaterials(isFirstPage = true)
                                                    }.addOnFailureListener {
                                                        isUploading = false
                                                        Toast.makeText(context, "Failed to upload material data", Toast.LENGTH_SHORT).show()
                                                    }
                                            } else {
                                                isUploading = false
                                                Toast.makeText(context, "File upload failed", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isUploading) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Text("Upload", color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        itemsIndexed(materials, key = { _, item -> item.id }) { index, item ->
            FileCard(
                item = item,
                onDelete = if (isAdmin) { { itemToDelete = item } } else null,
                onDownload = { item.fileUrl?.let { url -> downloadFile(context, url, item.fileName) } }
            )
            if (index >= materials.size - 1 && !isLastPage && !isLoading) {
                LaunchedEffect(Unit) { fetchMaterials() }
            }
        }
        
        if (isLoading && materials.isNotEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GreenPrimary, modifier = Modifier.size(24.dp))
                }
            }
        }
    }

    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Delete Material?", style = MaterialTheme.typography.titleLarge) },
            text = { Text("Are you sure you want to delete this material?", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    db.collection("classes").document(classCode).collection("materials").document(item.id).delete()
                        .addOnSuccessListener {
                            Toast.makeText(context, "Material deleted", Toast.LENGTH_SHORT).show()
                            materials = materials.filter { it.id != item.id }
                            itemToDelete = null
                        }
                }) { Text("Delete", color = Color.Red, style = MaterialTheme.typography.labelLarge) }
            },
            dismissButton = { TextButton(onClick = { itemToDelete = null }) { Text("Cancel", style = MaterialTheme.typography.labelLarge) } }
        )
    }
}

@Composable
fun AssignmentsContent(classCode: String, userId: String, authorName: String, isAdmin: Boolean, trigger: Int, className: String) {
    var assignments by remember { mutableStateOf<List<Post>>(emptyList()) }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    
    // Pagination
    var isLoading by remember { mutableStateOf(false) }
    var lastDoc by remember { mutableStateOf<DocumentSnapshot?>(null) }
    var isLastPage by remember { mutableStateOf(false) }
    val pageSize = 15L

    var itemToDelete by remember { mutableStateOf<Post?>(null) }

    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()

    val filePicker = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedFileUri = it
            selectedFileName = getFileName(context, it)
        }
    }

    fun fetchAssignments(isFirstPage: Boolean = false) {
        if (isLoading || (isLastPage && !isFirstPage)) return
        isLoading = true
        var query = db.collection("classes").document(classCode).collection("assignments")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(pageSize)
        if (!isFirstPage && lastDoc != null) query = query.startAfter(lastDoc!!)
        
        query.get().addOnSuccessListener { snapshot ->
            val newBatch = snapshot.documents.mapNotNull { it.toObject(Post::class.java)?.copy(id = it.id) }
            assignments = if (isFirstPage) newBatch else assignments + newBatch
            lastDoc = snapshot.documents.lastOrNull()
            isLastPage = snapshot.size() < pageSize
            isLoading = false
        }.addOnFailureListener { isLoading = false }
    }

    LaunchedEffect(classCode, trigger) {
        lastDoc = null
        isLastPage = false
        fetchAssignments(isFirstPage = true)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        if (isAdmin) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .neumorphic(elevation = 6.dp, shape = RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Text("Upload Assignment", style = MaterialTheme.typography.titleMedium, color = GreenPrimary)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(45.dp)
                                .neumorphic(elevation = 2.dp, shape = RoundedCornerShape(8.dp))
                                .background(Color(0xFFF1F3F4), RoundedCornerShape(8.dp))
                                .clickable { filePicker.launch("*/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AttachFile, null, tint = GreenPrimary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(selectedFileName ?: "Choose File or Photo", style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
                            }
                        }

                        val uri = selectedFileUri
                        if (uri != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Box(
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .height(40.dp)
                                    .widthIn(min = 100.dp)
                                    .neumorphic(elevation = 4.dp, shape = RoundedCornerShape(8.dp))
                                    .background(GreenPrimary, RoundedCornerShape(8.dp))
                                    .clickable(enabled = !isUploading) {
                                        isUploading = true
                                        val dedicatedFolder = if (isImage(selectedFileName)) "users/$userId/images" else "users/$userId/assignments"
                                        uploadToCloudinary(context, uri, dedicatedFolder) { url ->
                                            if (url != null) {
                                                val post = Post(
                                                    authorId = userId,
                                                    authorName = authorName,
                                                    classId = classCode,
                                                    className = className,
                                                    timestamp = System.currentTimeMillis(),
                                                    fileUrl = url,
                                                    fileName = selectedFileName,
                                                    isApproved = true
                                                )
                                                db.collection("classes").document(classCode).collection("assignments")
                                                    .add(post).addOnSuccessListener {
                                                        postAutoNotification(db, classCode, className, authorName, "New Assignment Posted", "New assignment: $selectedFileName", "ASSIGNMENT")
                                                        selectedFileUri = null
                                                        selectedFileName = null
                                                        isUploading = false
                                                        Toast.makeText(context, "Assignment uploaded successfully", Toast.LENGTH_SHORT).show()
                                                        fetchAssignments(isFirstPage = true)
                                                    }.addOnFailureListener {
                                                        isUploading = false
                                                        Toast.makeText(context, "Failed to upload assignment data", Toast.LENGTH_SHORT).show()
                                                    }
                                            } else {
                                                isUploading = false
                                                Toast.makeText(context, "File upload failed", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isUploading) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Text("Upload", color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        itemsIndexed(assignments, key = { _, item -> item.id }) { index, item ->
            FileCard(
                item = item,
                onDelete = if (isAdmin) { { itemToDelete = item } } else null,
                onDownload = { item.fileUrl?.let { url -> downloadFile(context, url, item.fileName) } }
            )
            if (index >= assignments.size - 1 && !isLastPage && !isLoading) {
                LaunchedEffect(Unit) { fetchAssignments() }
            }
        }
        
        if (isLoading && assignments.isNotEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GreenPrimary, modifier = Modifier.size(24.dp))
                }
            }
        }
    }

    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Delete Assignment?", style = MaterialTheme.typography.titleLarge) },
            text = { Text("Are you sure you want to delete this assignment?", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    db.collection("classes").document(classCode).collection("assignments").document(item.id).delete()
                        .addOnSuccessListener {
                            Toast.makeText(context, "Assignment deleted", Toast.LENGTH_SHORT).show()
                            assignments = assignments.filter { it.id != item.id }
                            itemToDelete = null
                        }
                }) { Text("Delete", color = Color.Red, style = MaterialTheme.typography.labelLarge) }
            },
            dismissButton = { TextButton(onClick = { itemToDelete = null }) { Text("Cancel", style = MaterialTheme.typography.labelLarge) } }
        )
    }
}

@Composable
fun FileCard(item: Post, onDelete: (() -> Unit)? = null, onDownload: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphic(elevation = 3.dp, shape = RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .clickable { onDownload() }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.fileName ?: "File", style = MaterialTheme.typography.bodyLarge, color = Color.Black, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            
            if (item.fileUrl != null) {
                IconButton(onClick = onDownload) { Icon(Icons.Default.Download, contentDescription = "Download", tint = GreenPrimary) }
            }
            
            if (onDelete != null) {
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RoutineContent(classCode: String, isAdmin: Boolean, className: String, trigger: Int, authorName: String) {
    var routines by remember { mutableStateOf<List<RoutineData>>(emptyList()) }
    var courseName by remember { mutableStateOf("") }
    var roomNumber by remember { mutableStateOf("") }
    var selectedStartTime by remember { mutableStateOf("") }
    var selectedEndTime by remember { mutableStateOf("") }
    var selectedDays by remember { mutableStateOf(setOf<String>()) }
    var isSaving by remember { mutableStateOf(false) }

    var showRescheduleDialog by remember { mutableStateOf<RoutineData?>(null) }

    val daysList = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()

    LaunchedEffect(classCode, trigger) {
        db.collection("classes").document(classCode).collection("routines")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    routines = snapshot.documents.mapNotNull { it.toObject(RoutineData::class.java)?.copy(id = it.id) }
                }
            }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), 
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        if (isAdmin) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .neumorphic(elevation = 6.dp, shape = RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Text("Add Routine Entry", style = MaterialTheme.typography.titleMedium, color = GreenPrimary)
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = courseName,
                            onValueChange = { courseName = it },
                            label = { Text("Course Name", style = MaterialTheme.typography.bodyMedium) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GreenPrimary,
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black,
                                unfocusedContainerColor = MaterialTheme.colorScheme.background,
                                focusedContainerColor = MaterialTheme.colorScheme.background
                            ),
                            shape = RoundedCornerShape(8.dp),
                            textStyle = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = roomNumber,
                            onValueChange = { roomNumber = it },
                            label = { Text("Room Number (Optional)", style = MaterialTheme.typography.bodyMedium) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GreenPrimary,
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black,
                                unfocusedContainerColor = MaterialTheme.colorScheme.background,
                                focusedContainerColor = MaterialTheme.colorScheme.background
                            ),
                            shape = RoundedCornerShape(8.dp),
                            textStyle = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(45.dp)
                                    .neumorphic(elevation = 2.dp, shape = RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF1F3F4), RoundedCornerShape(8.dp))
                                    .clickable {
                                        val calendar = Calendar.getInstance()
                                        TimePickerDialog(context, { _, hourOfDay, minute ->
                                            selectedStartTime = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute)
                                        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AccessTime, null, tint = GreenPrimary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (selectedStartTime.isEmpty()) "Start" else selectedStartTime, style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(45.dp)
                                    .neumorphic(elevation = 2.dp, shape = RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF1F3F4), RoundedCornerShape(8.dp))
                                    .clickable {
                                        val calendar = Calendar.getInstance()
                                        TimePickerDialog(context, { _, hourOfDay, minute ->
                                            selectedEndTime = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute)
                                        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AccessTime, null, tint = GreenPrimary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (selectedEndTime.isEmpty()) "End" else selectedEndTime, style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Select Day(s):", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            daysList.forEach { day ->
                                val isSelected = selectedDays.contains(day)
                                Box(
                                    modifier = Modifier
                                        .padding(bottom = 8.dp)
                                        .neumorphic(elevation = if (isSelected) 0.dp else 3.dp, shape = RoundedCornerShape(20.dp))
                                        .background(if (isSelected) GreenPrimary else MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                                        .clickable {
                                            selectedDays = if (isSelected) selectedDays - day else selectedDays + day
                                        }
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(day, color = if (isSelected) Color.White else Color.Black, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(top = 8.dp)
                                .height(45.dp)
                                .neumorphic(elevation = 4.dp, shape = RoundedCornerShape(8.dp))
                                .background(GreenPrimary, RoundedCornerShape(8.dp))
                                .clickable(enabled = !isSaving) {
                                    if (courseName.isNotBlank() && selectedStartTime.isNotEmpty() && selectedEndTime.isNotEmpty() && selectedDays.isNotEmpty()) {
                                        isSaving = true
                                        var count = 0
                                        val total = selectedDays.size
                                        selectedDays.forEach { day ->
                                            val routine = RoutineData(
                                                subject = courseName.trim(),
                                                time = selectedStartTime,
                                                endTime = selectedEndTime,
                                                day = day,
                                                dayIndex = daysList.indexOf(day),
                                                classCode = classCode,
                                                className = className,
                                                roomNumber = roomNumber.ifBlank { "TBA" }
                                            )
                                            db.collection("classes").document(classCode).collection("routines")
                                                .add(routine)
                                                .addOnCompleteListener {
                                                    count++
                                                    if (count == total) {
                                                        postAutoNotification(db, classCode, className, authorName, "Class Routine Updated", "New routine entry added for $courseName on ${selectedDays.joinToString(", ")}", "ROUTINE")
                                                        courseName = ""
                                                        roomNumber = ""
                                                        selectedStartTime = ""
                                                        selectedEndTime = ""
                                                        selectedDays = emptySet()
                                                        isSaving = false
                                                        Toast.makeText(context, "Routine entries updated", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                        }
                                    } else {
                                        Toast.makeText(context, "Please complete all fields", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSaving) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            else Text("Save & Add to Calendar", color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }

        if (routines.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No routine scheduled yet.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                }
            }
        } else {
            items(routines.sortedBy { daysList.indexOf(it.day) * 100 + (it.time.replace(":","").toIntOrNull() ?: 0) }) { routine ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .neumorphic(elevation = 3.dp, shape = RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(routine.subject, style = MaterialTheme.typography.titleMedium, color = Color.Black)
                            Text("${routine.day} • ${routine.time} - ${routine.endTime}", style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
                            if (routine.roomNumber.isNotEmpty() && routine.roomNumber != "TBA") {
                                Text("Room: ${routine.roomNumber}", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                            }
                            if (routine.rescheduledDate.isNotEmpty()) {
                                Text(
                                    "Rescheduled to ${routine.rescheduledDate} at ${routine.rescheduledTime}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFD4AF37),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (isAdmin) {
                            Row {
                                if (routine.rescheduledDate.isNotEmpty()) {
                                    IconButton(onClick = {
                                        db.collection("classes").document(classCode).collection("routines").document(routine.id)
                                            .update("isCancelled", false, "rescheduledDate", "", "rescheduledTime", "", "rescheduledEndTime", "")
                                    }) {
                                        Icon(Icons.Default.CheckCircle, "Reset", tint = GreenPrimary)
                                    }
                                } else {
                                    IconButton(onClick = { showRescheduleDialog = routine }) {
                                        Icon(Icons.Default.EventBusy, "Reschedule", tint = Color(0xFFD4AF37))
                                    }
                                }
                                IconButton(onClick = {
                                    db.collection("classes").document(classCode).collection("routines").document(routine.id).delete()
                                }) {
                                    Icon(Icons.Default.Delete, "Remove Entry", tint = Color.Red.copy(alpha = 0.7f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    showRescheduleDialog?.let { routine ->
        RescheduleDialog(
            routine = routine,
            onDismiss = { showRescheduleDialog = null },
            onReschedule = { date, start, end ->
                db.collection("classes").document(classCode).collection("routines").document(routine.id)
                    .update(
                        "isCancelled", true,
                        "rescheduledDate", date,
                        "rescheduledTime", start,
                        "rescheduledEndTime", end
                    ).addOnSuccessListener {
                        postAutoNotification(db, classCode, className, authorName, "Class Rescheduled", "Class ${routine.subject} has been rescheduled to $date at $start.", "ROUTINE")
                        Toast.makeText(context, "Class rescheduled", Toast.LENGTH_SHORT).show()
                        showRescheduleDialog = null
                    }
            }
        )
    }
}

@Composable
fun RescheduleDialog(
    routine: RoutineData,
    onDismiss: () -> Unit,
    onReschedule: (String, String, String) -> Unit
) {
    var selectedDate by remember { mutableStateOf("") }
    var selectedStartTime by remember { mutableStateOf("") }
    var selectedEndTime by remember { mutableStateOf("") }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reschedule Class", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text("Class: ${routine.subject}", style = MaterialTheme.typography.titleMedium, color = Color.Black)
                Spacer(modifier = Modifier.height(16.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(45.dp)
                        .neumorphic(elevation = 2.dp, shape = RoundedCornerShape(8.dp))
                        .background(Color(0xFFF1F3F4), RoundedCornerShape(8.dp))
                        .clickable {
                            val calendar = Calendar.getInstance()
                            DatePickerDialog(context, { _, year, month, dayOfMonth ->
                                selectedDate = String.format(Locale.getDefault(), "%02d/%02d/%d", dayOfMonth, month + 1, year)
                            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(18.dp), tint = GreenPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (selectedDate.isEmpty()) "Select New Date" else "Date: $selectedDate", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(45.dp)
                            .neumorphic(elevation = 2.dp, shape = RoundedCornerShape(8.dp))
                            .background(Color(0xFFF1F3F4), RoundedCornerShape(8.dp))
                            .clickable {
                                val calendar = Calendar.getInstance()
                                TimePickerDialog(context, { _, hourOfDay, minute ->
                                    selectedStartTime = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute)
                                }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (selectedStartTime.isEmpty()) "Start" else selectedStartTime, style = MaterialTheme.typography.bodyMedium)
                    }
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(45.dp)
                            .neumorphic(elevation = 2.dp, shape = RoundedCornerShape(8.dp))
                            .background(Color(0xFFF1F3F4), RoundedCornerShape(8.dp))
                            .clickable {
                                val calendar = Calendar.getInstance()
                                TimePickerDialog(context, { _, hourOfDay, minute ->
                                    selectedEndTime = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute)
                                }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (selectedEndTime.isEmpty()) "End" else selectedEndTime, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedDate.isNotEmpty() && selectedStartTime.isNotEmpty() && selectedEndTime.isNotEmpty()) {
                        onReschedule(selectedDate, selectedStartTime, selectedEndTime)
                    } else {
                        Toast.makeText(context, "Please complete all selections", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
            ) {
                Text("Reschedule", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", style = MaterialTheme.typography.labelLarge) }
        }
    )
}

@Composable
fun AttendanceContent(classCode: String, isAdmin: Boolean, className: String, classBatch: String, trigger: Int) {
    var records by remember { mutableStateOf<List<AttendanceRecord>>(emptyList()) }
    var isTakingAttendance by remember { mutableStateOf(false) }
    var students by remember { mutableStateOf<List<ClassMember>>(emptyList()) }
    var isLoadingStudents by remember { mutableStateOf(false) }
    var attendanceTitle by remember { mutableStateOf("") }
    val presentMap = remember { mutableStateMapOf<String, Boolean>() }
    var isSubmitting by remember { mutableStateOf(false) }
    
    var recordToDelete by remember { mutableStateOf<AttendanceRecord?>(null) }

    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val currentUserId = auth.currentUser?.uid ?: ""
    val context = LocalContext.current

    LaunchedEffect(classCode, trigger) {
        db.collection("classes").document(classCode).collection("attendance").orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) records = snapshot.documents.mapNotNull { it.toObject(AttendanceRecord::class.java)?.copy(id = it.id) }
            }
    }

    if (isTakingAttendance) {
        val scrollState = rememberScrollState()

        LaunchedEffect(classCode, trigger, isTakingAttendance) {
            isLoadingStudents = true
            students = emptyList()
            db.collection("classes").document(classCode).get().addOnSuccessListener { doc ->
                val members = (doc.get("members") as? List<*>)?.mapNotNull { it?.toString()?.trim() } ?: emptyList()
                val creatorId = doc.getString("creatorId")?.trim() ?: ""
                val studentIds = members.filter { it.isNotEmpty() && !it.equals(creatorId, ignoreCase = true) }.distinct()
                
                if (studentIds.isNotEmpty()) {
                    val chunks = studentIds.chunked(30)
                    val fetchedProfiles = mutableListOf<ClassMember>()
                    var chunksProcessed = 0
                    
                    chunks.forEach { chunk ->
                        db.collection("users").whereIn(FieldPath.documentId(), chunk).get().addOnSuccessListener { snapshot ->
                            val profileMap = snapshot.documents.associateBy { it.id }
                            chunk.forEach { uid ->
                                val userDoc = profileMap[uid]
                                if (userDoc != null) {
                                    val fName = userDoc.getString("firstName") ?: ""
                                    val lName = userDoc.getString("lastName") ?: ""
                                    fetchedProfiles.add(ClassMember(
                                        "$fName $lName".trim().ifEmpty { "Student" },
                                        userDoc.getString("profileImageUrl"),
                                        uid
                                    ))
                                } else {
                                    fetchedProfiles.add(ClassMember("Student (${uid.take(5)})", null, uid))
                                }
                            }
                            chunksProcessed++
                            if (chunksProcessed == chunks.size) {
                                students = fetchedProfiles
                                presentMap.clear()
                                fetchedProfiles.forEach { presentMap[it.uid] = true }
                                isLoadingStudents = false
                            }
                        }.addOnFailureListener {
                            chunksProcessed++
                            if (chunksProcessed == chunks.size) isLoadingStudents = false
                            Toast.makeText(context, "Profile fetch failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    students = emptyList()
                    presentMap.clear()
                    isLoadingStudents = false
                }
            }.addOnFailureListener {
                isLoadingStudents = false
                Toast.makeText(context, "Class load failed", Toast.LENGTH_SHORT).show()
            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(16.dp).background(MaterialTheme.colorScheme.background).verticalScroll(scrollState)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { isTakingAttendance = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text("Take Attendance", style = MaterialTheme.typography.titleLarge, color = Color.Black)
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .neumorphic(elevation = 6.dp, shape = RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Column {
                    OutlinedTextField(
                        value = attendanceTitle,
                        onValueChange = { attendanceTitle = it },
                        label = { Text("Session Title (e.g. Theory Class 1)", style = MaterialTheme.typography.bodyMedium) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GreenPrimary,
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                            unfocusedContainerColor = MaterialTheme.colorScheme.background,
                            focusedContainerColor = MaterialTheme.colorScheme.background
                        ),
                        shape = RoundedCornerShape(8.dp),
                        textStyle = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (isLoadingStudents) {
                            Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = GreenPrimary)
                            }
                        } else if (students.isEmpty()) {
                            Text(
                                "No students joined this class yet.", 
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )
                        } else {
                            students.forEach { student ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF2E7D32)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!student.profileImageUrl.isNullOrEmpty()) {
                                                AsyncImage(
                                                    model = student.profileImageUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Text(student.name.take(1).uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.White)
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(student.name, style = MaterialTheme.typography.bodyLarge, color = Color.Black)
                                    }
                                    
                                    Switch(
                                        checked = presentMap[student.uid] ?: false,
                                        onCheckedChange = { isPresent -> 
                                            presentMap[student.uid] = isPresent
                                        },
                                        colors = SwitchDefaults.colors(checkedThumbColor = GreenPrimary, checkedTrackColor = GreenPrimary.copy(alpha = 0.5f))
                                    )
                                }
                                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .neumorphic(elevation = 4.dp, shape = RoundedCornerShape(12.dp))
                            .background(if (!isSubmitting && students.isNotEmpty() && attendanceTitle.isNotBlank()) GreenPrimary else Color.Gray, RoundedCornerShape(12.dp))
                            .clickable(enabled = !isSubmitting && students.isNotEmpty() && attendanceTitle.isNotBlank()) {
                                isSubmitting = true
                                val presentNames = students.filter { presentMap[it.uid] == true }.map { it.name }
                                val absentNames = students.filter { presentMap[it.uid] != true }.map { it.name }
                                val date = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
                                
                                val pdfFile = createAttendancePdf(context, className, date, presentNames, absentNames)
                                
                                uploadToCloudinary(context, Uri.fromFile(pdfFile), "users/$currentUserId/attendance_sheets/$classCode") { fileUrl ->
                                    if (fileUrl != null) {
                                        val sheetRecord = AttendanceSheetRecord(
                                            userId = currentUserId,
                                            classId = classCode,
                                            className = className,
                                            batch = classBatch.ifEmpty { "General" },
                                            date = date,
                                            fileUrl = fileUrl,
                                            timestamp = System.currentTimeMillis()
                                        )
                                        db.collection("classes").document(classCode).collection("attendance_sheets").add(sheetRecord)
                                    }
                                    
                                    saveFileToDownloads(context, pdfFile, "Attendance_${className.replace(" ", "_")}_${System.currentTimeMillis()}.pdf")
                                    
                                    val record = AttendanceRecord(
                                        title = attendanceTitle.trim(),
                                        teacherId = currentUserId,
                                        classId = classCode,
                                        timestamp = System.currentTimeMillis(),
                                        presentStudents = presentNames,
                                        absentStudents = absentNames,
                                        pdfUrl = fileUrl ?: ""
                                    )
                                    
                                    db.collection("classes").document(classCode).collection("attendance").add(record)
                                        .addOnSuccessListener {
                                            isSubmitting = false
                                            isTakingAttendance = false
                                            attendanceTitle = ""
                                            Toast.makeText(context, "Attendance saved and report generated", Toast.LENGTH_SHORT).show()
                                        }
                                        .addOnFailureListener {
                                            isSubmitting = false
                                            Toast.makeText(context, "Failed to save record", Toast.LENGTH_SHORT).show()
                                        }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSubmitting) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        else Text("Save Attendance & Generate PDF", color = Color.White, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp).background(MaterialTheme.colorScheme.background)) {
            if (isAdmin) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                        .height(50.dp)
                        .neumorphic(elevation = 4.dp, shape = RoundedCornerShape(12.dp))
                        .background(GreenPrimary, RoundedCornerShape(12.dp))
                        .clickable { isTakingAttendance = true },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Take New Attendance", color = Color.White, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }

            if (records.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No attendance records found.", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(records) { record ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .neumorphic(elevation = 3.dp, shape = RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(record.title, style = MaterialTheme.typography.titleMedium, color = Color.Black)
                                        Text(SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(record.timestamp)), style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                                    }
                                    if (isAdmin) {
                                        Row {
                                            IconButton(onClick = {
                                                if (record.pdfUrl.isNotEmpty()) {
                                                    downloadFile(context, record.pdfUrl, "Attendance_${record.title.replace(" ", "_")}.pdf")
                                                } else {
                                                    val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(record.timestamp))
                                                    val file = createAttendancePdf(context, className, dateStr, record.presentStudents, record.absentStudents)
                                                    saveFileToDownloads(context, file, "Attendance_${record.title.replace(" ", "_")}.pdf")
                                                }
                                            }) {
                                                Icon(Icons.Default.Download, "Download Report", tint = GreenPrimary)
                                            }
                                            IconButton(onClick = { recordToDelete = record }) {
                                                Icon(Icons.Default.Delete, "Delete Record", tint = Color.Red.copy(alpha = 0.7f))
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text("Present: ${record.presentStudents.size}", color = Color(0xFF2E7D32), style = MaterialTheme.typography.labelLarge)
                                    Text("Absent: ${record.absentStudents.size}", color = Color.Red, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    recordToDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text("Delete Attendance Record?", style = MaterialTheme.typography.titleLarge) },
            text = { Text("Are you sure you want to delete this record? This action cannot be undone.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                Button(
                    onClick = {
                        db.collection("classes").document(classCode).collection("attendance").document(record.id).delete()
                            .addOnSuccessListener {
                                Toast.makeText(context, "Record deleted", Toast.LENGTH_SHORT).show()
                                recordToDelete = null
                            }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("Delete", color = Color.White, style = MaterialTheme.typography.labelLarge) }
            },
            dismissButton = { TextButton(onClick = { recordToDelete = null }) { Text("Cancel", style = MaterialTheme.typography.labelLarge) } }
        )
    }
}

data class ClassMember(val name: String, val profileImageUrl: String?, val uid: String = "")

@Preview(showBackground = true)
@Composable
fun ClassroomHomeScreenPreview() { OMNIRATheme { ClassroomHomeScreen() } }
