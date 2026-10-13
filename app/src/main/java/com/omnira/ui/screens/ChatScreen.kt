package com.omnira.ui.screens

import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.omnira.data.ChatMessage
import com.omnira.data.Notification
import coil.compose.AsyncImage
import com.omnira.ui.theme.GreenPrimary
import com.omnira.ui.theme.neumorphic
import com.omnira.utils.downloadFile
import com.omnira.utils.getFileName
import com.omnira.utils.isImage
import com.omnira.utils.uploadToCloudinary
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    classCode: String,
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val currentUserId = auth.currentUser?.uid ?: ""
    var currentUserName by rememberSaveable { mutableStateOf("User") }
    var currentUserProfileImageUrl by remember { mutableStateOf<String?>(null) }
    var profileImages by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
    var isAdmin by remember { mutableStateOf(false) }
    var retentionMonths by remember { mutableIntStateOf(12) }
    var showRetentionDialog by remember { mutableStateOf(false) }
    var retentionInput by remember { mutableStateOf("12") }
    var selectedMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var dialogMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var editMessageText by remember { mutableStateOf("") }
    var showEditDialog by remember { mutableStateOf(false) }
    var reportReason by remember { mutableStateOf("") }
    var showReportDialog by remember { mutableStateOf(false) }
    
    var messageText by rememberSaveable { mutableStateOf("") }
    var isUploadingAttachment by remember { mutableStateOf(false) }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    val listState = rememberLazyListState()

    // Pagination states
    var isLoadingOlder by remember { mutableStateOf(false) }
    var lastDocument by remember { mutableStateOf<DocumentSnapshot?>(null) }
    var isLastPage by remember { mutableStateOf(false) }
    val pageSize = 20L

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isError = messageText.startsWith(" ")
    val chatRef = db.collection("classes").document(classCode).collection("chats")

    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            val attachmentName = getFileName(context, selectedUri)
            val attachedText = messageText.trim()
            isUploadingAttachment = true
            uploadToCloudinary(context, selectedUri, "classes/$classCode/chat") { url ->
                if (url.isNullOrBlank()) {
                    isUploadingAttachment = false
                    Toast.makeText(context, "Attachment upload failed", Toast.LENGTH_SHORT).show()
                } else {
                    val message = ChatMessage(
                        senderId = currentUserId,
                        senderName = currentUserName,
                        message = attachedText,
                        timestamp = System.currentTimeMillis(),
                        senderProfileImageUrl = currentUserProfileImageUrl,
                        attachmentUrl = url,
                        attachmentName = attachmentName
                    )
                    chatRef.add(message)
                        .addOnSuccessListener {
                            messageText = ""
                            isUploadingAttachment = false
                        }
                        .addOnFailureListener {
                            isUploadingAttachment = false
                            Toast.makeText(context, "Could not send attachment", Toast.LENGTH_SHORT).show()
                        }
                }
            }
        }
    }

    DisposableEffect(classCode, currentUserId) {
        val registration = db.collection("classes").document(classCode)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(context, "Could not load classroom settings", Toast.LENGTH_SHORT).show()
                } else if (snapshot != null && snapshot.exists()) {
                    val admins = (snapshot.get("admins") as? List<*>)?.filterIsInstance<String>().orEmpty()
                    isAdmin = snapshot.getString("creatorId") == currentUserId || currentUserId in admins
                    retentionMonths = (snapshot.getLong("chatRetentionMonths") ?: 12L).toInt().coerceIn(1, 12)
                    if (isAdmin) pruneExpiredChat(db, classCode, retentionMonths, context)
                }
            }
        onDispose { registration.remove() }
    }

    // Fetch current user name
    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotEmpty()) {
            db.collection("users").document(currentUserId).get().addOnSuccessListener { doc ->
                val first = doc.getString("firstName") ?: ""
                val last = doc.getString("lastName") ?: ""
                currentUserName = "$first $last".trim().ifEmpty { "User" }
                currentUserProfileImageUrl = doc.getString("profileImageUrl")
            }
        }
    }

    LaunchedEffect(messages.map { it.senderId }.distinct()) {
        val missingIds = messages.map { it.senderId }.filter { it.isNotBlank() }
            .distinct().filterNot { profileImages.containsKey(it) }
        missingIds.chunked(30).forEach { chunk ->
            db.collection("users").whereIn(FieldPath.documentId(), chunk).get()
                .addOnSuccessListener { snapshot ->
                    profileImages = profileImages + snapshot.documents.associate {
                        it.id to it.getString("profileImageUrl")
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Could not load chat profile photos", Toast.LENGTH_SHORT).show()
                }
        }
    }

    fun loadOlderMessages() {
        if (isLoadingOlder || isLastPage || lastDocument == null) return
        isLoadingOlder = true
        
        chatRef
            .whereGreaterThanOrEqualTo("timestamp", chatExpiryCutoff(retentionMonths))
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .startAfter(lastDocument!!)
            .limit(pageSize)
            .get()
            .addOnSuccessListener { snapshot ->
                val olderMessages = snapshot.documents.mapNotNull { it.toObject(ChatMessage::class.java)?.copy(id = it.id) }
                    .filter { currentUserId !in it.deletedFor }
                if (olderMessages.isNotEmpty()) {
                    messages = olderMessages.reversed() + messages
                    lastDocument = snapshot.documents.lastOrNull()
                }
                isLastPage = snapshot.size() < pageSize
                isLoadingOlder = false
            }
            .addOnFailureListener {
                isLoadingOlder = false
            }
    }

    // Initial listener for NEW messages (last page)
    DisposableEffect(classCode, retentionMonths, currentUserId) {
        val registration = chatRef
            .whereGreaterThanOrEqualTo("timestamp", chatExpiryCutoff(retentionMonths))
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(pageSize)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val fetchedMessages = snapshot.documents.mapNotNull { it.toObject(ChatMessage::class.java)?.copy(id = it.id) }
                    val unreadIncoming = snapshot.documents.filter { document ->
                        val chatMessage = document.toObject(ChatMessage::class.java)
                        chatMessage != null &&
                            chatMessage.senderId != currentUserId &&
                            currentUserId !in chatMessage.readBy
                    }
                    if (unreadIncoming.isNotEmpty()) {
                        val batch = db.batch()
                        unreadIncoming.forEach { batch.update(it.reference, "readBy", FieldValue.arrayUnion(currentUserId)) }
                        batch.commit()
                    }
                    val newBatch = fetchedMessages
                        .filter { currentUserId !in it.deletedFor }
                        .reversed()
                    val latestIds = snapshot.documents.map { it.id }.toSet()
                    val olderLoaded = messages.filter { it.id !in latestIds }
                    messages = (olderLoaded + newBatch).distinctBy { it.id }.sortedBy { it.timestamp }
                    lastDocument = snapshot.documents.lastOrNull()
                    isLastPage = snapshot.size() < pageSize
                }
            }
        onDispose { registration.remove() }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && !isLoadingOlder) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Class Chat", style = MaterialTheme.typography.titleLarge, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    if (isAdmin) {
                        IconButton(onClick = {
                            retentionInput = retentionMonths.toString()
                            showRetentionDialog = true
                        }) {
                            Icon(Icons.Default.Settings, "Chat retention settings", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GreenPrimary),
                windowInsets = WindowInsets.statusBars
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .imePadding()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = if (isLandscape) 48.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                if (!isLastPage && messages.isNotEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            if (isLoadingOlder) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = GreenPrimary)
                            } else {
                                LaunchedEffect(Unit) {
                                    loadOlderMessages()
                                }
                            }
                        }
                    }
                }

                itemsIndexed(messages, key = { _, msg -> msg.id }) { index, message ->
                    ChatItem(
                        message = message,
                        isMe = message.senderId == currentUserId,
                        isLandscape = isLandscape,
                        profileImageUrl = message.senderProfileImageUrl ?: profileImages[message.senderId],
                        onAttachmentClick = { url, name -> downloadFile(context, url, name) },
                        onClick = {
                            if (!message.senderId.equals(currentUserId) && currentUserId !in message.readBy) {
                                chatRef.document(message.id)
                                    .update("readBy", FieldValue.arrayUnion(currentUserId))
                            }
                        },
                        onLongClick = { selectedMessage = message }
                    )
                }
            }

            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (isLandscape) 48.dp else 0.dp)
                ) {
                    if (isError) {
                        Text(
                            text = "Leading space is not allowed",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { attachmentPicker.launch("*/*") },
                            enabled = !isUploadingAttachment
                        ) {
                            Icon(Icons.Default.AttachFile, "Attach photo or file", tint = GreenPrimary)
                        }
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            placeholder = { Text("Type a message...", style = MaterialTheme.typography.bodyMedium) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            maxLines = if (isLandscape) 2 else 5,
                            enabled = !isUploadingAttachment,
                            isError = isError,
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.background,
                                focusedContainerColor = MaterialTheme.colorScheme.background
                            ),
                            textStyle = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .neumorphic(elevation = 4.dp, shape = RoundedCornerShape(24.dp))
                                .background(GreenPrimary, RoundedCornerShape(24.dp))
                                .clickable(enabled = !isError && messageText.isNotBlank() && !isUploadingAttachment) {
                                    val msg = ChatMessage(
                                        senderId = currentUserId,
                                        senderName = currentUserName,
                                        message = messageText.trim(),
                                        timestamp = System.currentTimeMillis(),
                                        senderProfileImageUrl = currentUserProfileImageUrl
                                    )
                                    chatRef.add(msg)
                                        .addOnSuccessListener { messageText = "" }
                                        .addOnFailureListener {
                                            Toast.makeText(context, "Could not send message", Toast.LENGTH_SHORT).show()
                                        }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isUploadingAttachment) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRetentionDialog) {
        AlertDialog(
            onDismissRequest = { showRetentionDialog = false },
            title = { Text("Chat retention") },
            text = {
                Column {
                    Text("Choose how many months chat messages stay (1-12).")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = retentionInput,
                        onValueChange = { retentionInput = it.filter(Char::isDigit).take(2) },
                        label = { Text("Months") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val months = retentionInput.toIntOrNull()
                    if (months == null || months !in 1..12) {
                        Toast.makeText(context, "Enter a number from 1 to 12", Toast.LENGTH_SHORT).show()
                    } else {
                        db.collection("classes").document(classCode)
                            .update("chatRetentionMonths", months)
                            .addOnSuccessListener {
                                retentionMonths = months
                                pruneExpiredChat(db, classCode, months, context)
                                showRetentionDialog = false
                            }
                            .addOnFailureListener {
                                Toast.makeText(context, "Could not update chat retention", Toast.LENGTH_SHORT).show()
                            }
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRetentionDialog = false }) { Text("Cancel") }
            }
        )
    }

    selectedMessage?.let { message ->
        val canManageForEveryone = isAdmin || message.senderId == currentUserId
        AlertDialog(
            onDismissRequest = { selectedMessage = null },
            title = { Text(if (message.senderId == currentUserId) "Your message" else message.senderName) },
            text = { Text(message.message) },
            confirmButton = {
                if (canManageForEveryone) {
                    TextButton(onClick = {
                        dialogMessage = message
                        editMessageText = message.message
                        showEditDialog = true
                        selectedMessage = null
                    }) { Text("Edit") }
                } else {
                    TextButton(onClick = {
                        dialogMessage = message
                        reportReason = ""
                        showReportDialog = true
                        selectedMessage = null
                    }) { Text("Report to admin") }
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        if (!canManageForEveryone) {
                            chatRef.document(message.id)
                                .update("deletedFor", FieldValue.arrayUnion(currentUserId))
                                .addOnSuccessListener {
                                    messages = messages.filterNot { it.id == message.id }
                                    selectedMessage = null
                                }
                                .addOnFailureListener {
                                    Toast.makeText(context, "Could not hide message", Toast.LENGTH_SHORT).show()
                                }
                        } else {
                            chatRef.document(message.id).delete()
                                .addOnSuccessListener {
                                    messages = messages.filterNot { it.id == message.id }
                                    selectedMessage = null
                                }
                                .addOnFailureListener {
                                    Toast.makeText(context, "Could not delete message", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }) {
                        Text(
                            if (canManageForEveryone) "Delete" else "Delete for me",
                            color = if (canManageForEveryone) Color.Red else GreenPrimary
                        )
                    }
                    TextButton(onClick = { selectedMessage = null }) { Text("Cancel") }
                }
            }
        )
    }

    if (showEditDialog) {
        val message = dialogMessage
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit message") },
            text = {
                OutlinedTextField(
                    value = editMessageText,
                    onValueChange = { editMessageText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = message
                    if (target == null || editMessageText.isBlank()) {
                        Toast.makeText(context, "Message cannot be empty", Toast.LENGTH_SHORT).show()
                    } else {
                        chatRef.document(target.id).update("message", editMessageText.trim())
                            .addOnSuccessListener { showEditDialog = false; dialogMessage = null }
                            .addOnFailureListener {
                                Toast.makeText(context, "Could not edit message", Toast.LENGTH_SHORT).show()
                            }
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showEditDialog = false; dialogMessage = null }) { Text("Cancel") } }
        )
    }

    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Report message") },
            text = {
                OutlinedTextField(
                    value = reportReason,
                    onValueChange = { reportReason = it },
                    label = { Text("Reason") },
                    placeholder = { Text("Describe why you are reporting this message") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val targetMessage = dialogMessage
                    if (reportReason.isBlank()) {
                        Toast.makeText(context, "Enter a reason for the report", Toast.LENGTH_SHORT).show()
                    } else if (targetMessage == null) {
                        Toast.makeText(context, "Message is no longer available", Toast.LENGTH_SHORT).show()
                    } else {
                        submitChatReport(
                            db, classCode, currentUserId, currentUserName, targetMessage,
                            reportReason.trim(), context
                        ) { submitted ->
                            if (submitted) {
                                showReportDialog = false
                                dialogMessage = null
                            }
                        }
                    }
                }) { Text("Submit report") }
            },
            dismissButton = { TextButton(onClick = { showReportDialog = false; dialogMessage = null }) { Text("Cancel") } }
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun ChatItem(
    message: ChatMessage,
    isMe: Boolean,
    isLandscape: Boolean,
    profileImageUrl: String? = message.senderProfileImageUrl,
    onClick: () -> Unit = {},
    onAttachmentClick: (String, String?) -> Unit = { _, _ -> },
    onLongClick: () -> Unit = {}
) {
    val alignment = if (isMe) Alignment.End else Alignment.Start
    val bgColor = if (isMe) GreenPrimary else MaterialTheme.colorScheme.surface
    val textColor = if (isMe) Color.White else Color.Black
    val shape = if (isMe) {
        RoundedCornerShape(12.dp, 12.dp, 0.dp, 12.dp)
    } else {
        RoundedCornerShape(12.dp, 12.dp, 12.dp, 0.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        if (!isMe) {
            Text(
                text = message.senderName,
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            if (!isMe) ChatProfileImage(message.senderName, profileImageUrl)
            if (!isMe) Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .widthIn(max = if (isLandscape) 400.dp else 280.dp)
                    .neumorphic(elevation = 3.dp, shape = shape)
                    .background(bgColor, shape)
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column {
                    if (message.message.isNotBlank()) {
                        Text(text = message.message, style = MaterialTheme.typography.bodyLarge, color = textColor)
                    }
                    if (!message.attachmentUrl.isNullOrBlank()) {
                        if (isImage(message.attachmentName)) {
                            AsyncImage(
                                model = message.attachmentUrl,
                                contentDescription = message.attachmentName ?: "Chat photo",
                                modifier = Modifier
                                    .padding(top = if (message.message.isNotBlank()) 8.dp else 0.dp)
                                    .widthIn(max = if (isLandscape) 360.dp else 240.dp)
                                    .heightIn(max = 280.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        onAttachmentClick(message.attachmentUrl, message.attachmentName)
                                    },
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            TextButton(
                                onClick = { onAttachmentClick(message.attachmentUrl, message.attachmentName) },
                                contentPadding = PaddingValues(top = 6.dp, start = 0.dp, end = 0.dp, bottom = 0.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AttachFile, null, tint = textColor)
                                    Text(
                                        message.attachmentName ?: "Download file",
                                        color = textColor,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
            if (isMe) Spacer(modifier = Modifier.width(8.dp))
            if (isMe) ChatProfileImage(message.senderName, profileImageUrl)
        }
    }
}

@Composable
private fun ChatProfileImage(name: String, imageUrl: String?) {
    Box(
        modifier = Modifier.size(32.dp).clip(CircleShape).background(GreenPrimary.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "$name profile",
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(name.take(1).uppercase(), color = GreenPrimary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun chatExpiryCutoff(retentionMonths: Int): Long =
    Calendar.getInstance().apply { add(Calendar.MONTH, -retentionMonths.coerceIn(1, 12)) }.timeInMillis

private fun pruneExpiredChat(
    db: FirebaseFirestore,
    classCode: String,
    retentionMonths: Int,
    context: android.content.Context
) {
    db.collection("classes").document(classCode).collection("chats")
        .whereLessThan("timestamp", chatExpiryCutoff(retentionMonths))
        .get()
        .addOnSuccessListener { snapshot ->
            snapshot.documents.chunked(450).forEach { documents ->
                val batch = db.batch()
                documents.forEach { batch.delete(it.reference) }
                batch.commit().addOnFailureListener {
                    Toast.makeText(context, "Could not remove expired chat messages", Toast.LENGTH_SHORT).show()
                }
            }
        }
        .addOnFailureListener {
            Toast.makeText(context, "Could not check chat expiration", Toast.LENGTH_SHORT).show()
        }
}

private fun submitChatReport(
    db: FirebaseFirestore,
    classCode: String,
    reporterId: String,
    reporterName: String,
    message: ChatMessage,
    reason: String,
    context: android.content.Context,
    onComplete: (Boolean) -> Unit
) {
    val classRef = db.collection("classes").document(classCode)
    classRef.get()
        .addOnSuccessListener { classSnapshot ->
            val adminIds = (
                (classSnapshot.get("admins") as? List<*>)?.filterIsInstance<String>().orEmpty() +
                    (classSnapshot.getString("creatorId") ?: "")
                ).filter { it.isNotBlank() }.distinct()
            if (adminIds.isEmpty()) {
                Toast.makeText(context, "No class admin found to receive this report", Toast.LENGTH_SHORT).show()
                onComplete(false)
                return@addOnSuccessListener
            }

            val reportRef = classRef.collection("chatReports").document()
            val timestamp = System.currentTimeMillis()
            val reportContent = "Reported by $reporterName about ${message.senderName}'s message:\n${message.message}\n\nReason: $reason"
            val batch = db.batch()
            batch.set(
                reportRef,
                mapOf(
                    "reporterId" to reporterId,
                    "reporterName" to reporterName,
                    "messageId" to message.id,
                    "messageSenderId" to message.senderId,
                    "messageSenderName" to message.senderName,
                    "message" to message.message,
                    "reason" to reason,
                    "timestamp" to timestamp
                )
            )
            adminIds.forEach { adminId ->
                val notificationRef = db.collection("notifications")
                    .document("${classCode}_CHAT_REPORT_${reportRef.id}_$adminId")
                batch.set(
                    notificationRef,
                    mapOf(
                        "title" to "Chat message report",
                        "content" to reportContent,
                        "timestamp" to timestamp,
                        "authorName" to reporterName,
                        "className" to (classSnapshot.getString("name") ?: "Class"),
                        "classId" to classCode,
                        "type" to "CHAT_REPORT",
                        "recipientId" to adminId,
                        "requestId" to reportRef.id,
                        "reportId" to reportRef.id
                    )
                )
            }
            batch.commit()
                .addOnSuccessListener {
                    Toast.makeText(context, "Report sent to class admins", Toast.LENGTH_SHORT).show()
                    onComplete(true)
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Could not submit report", Toast.LENGTH_SHORT).show()
                    onComplete(false)
                }
        }
        .addOnFailureListener {
            Toast.makeText(context, "Could not find class admins", Toast.LENGTH_SHORT).show()
            onComplete(false)
        }
}
