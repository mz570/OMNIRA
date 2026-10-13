package com.omnira.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.omnira.data.Notification
import com.omnira.ui.components.OmniraLoadingLogo
import com.omnira.ui.theme.GreenPrimary
import com.omnira.ui.theme.OMNIRATheme
import com.omnira.ui.theme.neumorphic
import com.omnira.utils.formatDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    onBackClick: () -> Unit = {},
    onNotificationClick: (String, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val currentUserId = auth.currentUser?.uid ?: ""

    var notifications by remember { mutableStateOf<List<Notification>>(emptyList()) }
    var myClasses by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    
    var isLoading by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun fetchNotifications() {
        if (currentUserId.isEmpty()) return
        isLoading = true
        errorMessage = null

        val generalTask = db.collection("notifications")
            .whereEqualTo("classId", "General")
            .limit(50)
            .get()
            
        val emptyTask = db.collection("notifications")
            .whereEqualTo("classId", "")
            .limit(50)
            .get()

        val recipientTask = db.collection("notifications")
            .whereEqualTo("recipientId", currentUserId)
            .limit(100)
            .get()

        val classTasks = myClasses.map { (classId, _) ->
            db.collection("notifications")
                .whereEqualTo("classId", classId)
                .limit(20)
                .get()
        }

        val allTasks = mutableListOf(generalTask, emptyTask, recipientTask)
        allTasks.addAll(classTasks)

        Tasks.whenAllComplete(allTasks).addOnCompleteListener { _ ->
            val mergedList = mutableListOf<Notification>()
            allTasks.forEach { task ->
                if (task.isSuccessful) {
                    val docs = task.result.documents.mapNotNull { it.toObject(Notification::class.java)?.copy(id = it.id) }
                    mergedList.addAll(docs)
                }
            }
            
            notifications = mergedList.distinctBy { it.id }
                .filter { it.recipientId.isEmpty() || it.recipientId == currentUserId }
                .sortedByDescending { it.timestamp }
            isLoading = false
            isRefreshing = false
        }.addOnFailureListener { e ->
            isLoading = false
            isRefreshing = false
            errorMessage = "Failed to load notifications."
            Log.e("NoticeScreen", "Error merging notifications", e)
        }
    }

    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotEmpty()) {
            val memberQuery = db.collection("classes").whereArrayContains("members", currentUserId).get()
            val creatorQuery = db.collection("classes").whereEqualTo("creatorId", currentUserId).get()
            val adminQuery = db.collection("classes").whereArrayContains("admins", currentUserId).get()

            Tasks.whenAllComplete(memberQuery, creatorQuery, adminQuery).addOnSuccessListener {
                val memberDocs = memberQuery.result?.documents ?: emptyList()
                val creatorDocs = creatorQuery.result?.documents ?: emptyList()
                val adminDocs = adminQuery.result?.documents ?: emptyList()
                
                myClasses = (memberDocs + creatorDocs + adminDocs)
                    .distinctBy { it.id }
                    .map { it.id to (it.getString("name") ?: "Class") }
                
                fetchNotifications()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications", style = MaterialTheme.typography.titleLarge, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GreenPrimary)
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true; fetchNotifications() },
            modifier = Modifier.padding(innerPadding).fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                if (notifications.isEmpty() && isLoading) {
                    OmniraLoadingLogo(size = 100.dp, modifier = Modifier.align(Alignment.Center))
                } else if (notifications.isEmpty()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Campaign, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = errorMessage ?: "No notifications yet.", 
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Gray, 
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        itemsIndexed(notifications, key = { _, it -> it.id }) { _, notification ->
                            NotificationItem(
                                notification = notification,
                                isRead = currentUserId in notification.readBy,
                                onClick = {
                                    if (currentUserId !in notification.readBy) {
                                        db.collection("notifications").document(notification.id)
                                            .update("readBy", com.google.firebase.firestore.FieldValue.arrayUnion(currentUserId))
                                            .addOnSuccessListener {
                                                notifications = notifications.map {
                                                    if (it.id == notification.id) it.copy(readBy = it.readBy + currentUserId) else it
                                                }
                                            }
                                            .addOnFailureListener {
                                                Toast.makeText(context, "Could not mark notification as read", Toast.LENGTH_SHORT).show()
                                            }
                                    }
                                    onNotificationClick(notification.classId, notification.type)
                                },
                                onApprovalAction = if (
                                    notification.type in setOf("JOIN_REQUEST", "POST_APPROVAL", "COMMENT_APPROVAL")
                                ) { approve ->
                                    processClassApproval(db, notification, approve) { success ->
                                        if (success) {
                                            notifications = notifications.filterNot {
                                                it.classId == notification.classId &&
                                                    it.type == notification.type &&
                                                    it.requestId == notification.requestId
                                            }
                                        } else {
                                            Toast.makeText(context, "Could not process approval request", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else null,
                                onDeleteClick = {
                                    db.collection("notifications").document(notification.id).delete()
                                        .addOnSuccessListener {
                                            notifications = notifications.filter { it.id != notification.id }
                                        }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationItem(
    notification: Notification,
    isRead: Boolean = false,
    onClick: () -> Unit,
    onApprovalAction: ((Boolean) -> Unit)? = null,
    onDeleteClick: () -> Unit
) {
    val isClickable = notification.classId.isNotEmpty() && notification.classId != "General"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphic()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(28.dp))
            .then(if (isClickable) Modifier.clickable { onClick() } else Modifier)
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(GreenPrimary.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Campaign, null, tint = GreenPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = notification.title,
                        style = if (isRead) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.Black
                    )
                    Text(
                        text = "${notification.authorName} • ${formatDate(notification.timestamp)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Gray
                    )
                }
                if (notification.className.isNotEmpty()) {
                    Surface(
                        color = GreenPrimary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = notification.className,
                            style = MaterialTheme.typography.labelSmall,
                            color = GreenPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                if (onApprovalAction == null) {
                    IconButton(onClick = onDeleteClick) {
                        Icon(Icons.Default.Delete, "Delete", tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = notification.content,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.DarkGray,
                lineHeight = 20.sp
            )

            if (onApprovalAction != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { onApprovalAction(false) }) {
                        Text("Decline", color = Color.Red)
                    }
                    TextButton(onClick = { onApprovalAction(true) }) {
                        Text("Approve", color = GreenPrimary)
                    }
                }
            }
            
            if (isClickable && onApprovalAction == null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap to view details",
                    style = MaterialTheme.typography.labelLarge,
                    color = GreenPrimary,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}
