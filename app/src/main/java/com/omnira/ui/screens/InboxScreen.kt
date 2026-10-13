package com.omnira.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.omnira.data.DirectMessage
import com.omnira.ui.theme.GreenPrimary
import com.omnira.ui.theme.neumorphic
import com.omnira.utils.downloadFile
import com.omnira.utils.getFileName
import com.omnira.utils.uploadToCloudinary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class InboxThread(
    val id: String,
    val otherUserId: String,
    val lastMessage: String,
    val timestamp: Long,
    val unread: Boolean
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun InboxScreen(
    onBackClick: () -> Unit = {},
    onConversationClick: (String) -> Unit = {},
    onHomeClick: () -> Unit = {},
    onCalendarClick: () -> Unit = {},
    onProfileClick: () -> Unit = {}
) {
    val db = FirebaseFirestore.getInstance()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    var threads by remember { mutableStateOf<List<InboxThread>>(emptyList()) }
    var profiles by remember { mutableStateOf<Map<String, Pair<String, String?>>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    val context = LocalContext.current

    DisposableEffect(currentUserId) {
        if (currentUserId.isBlank()) {
            loading = false
            onDispose { }
        } else {
            val requestedProfiles = mutableSetOf<String>()
            val registration = db.collection("directThreads")
                .whereArrayContains("participants", currentUserId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        loading = false
                        Toast.makeText(context, "Could not load inbox", Toast.LENGTH_SHORT).show()
                    } else {
                        val docs = snapshot?.documents.orEmpty()
                            .filter { currentUserId !in (it.get("hiddenFor") as? List<*>).orEmpty() }
                        val nextThreads = docs.mapNotNull { doc ->
                            val otherId = (doc.get("participants") as? List<*>)
                                ?.filterIsInstance<String>()?.firstOrNull { it != currentUserId } ?: return@mapNotNull null
                            InboxThread(
                                id = doc.id,
                                otherUserId = otherId,
                                lastMessage = doc.getString("lastMessage") ?: "",
                                timestamp = doc.getLong("lastTimestamp") ?: 0L,
                                unread = doc.getString("lastSenderId") != currentUserId &&
                                    currentUserId !in (doc.get("lastReadBy") as? List<*>).orEmpty()
                            )
                        }.sortedByDescending { it.timestamp }
                        threads = nextThreads
                        loading = false
                        val missing = nextThreads.map { it.otherUserId }.distinct()
                            .filter { !profiles.containsKey(it) && requestedProfiles.add(it) }
                        missing.chunked(30).forEach { ids ->
                            db.collection("users").whereIn(com.google.firebase.firestore.FieldPath.documentId(), ids)
                                .get().addOnSuccessListener { users ->
                                    profiles = profiles + users.documents.associate {
                                        it.id to (
                                            listOfNotNull(it.getString("firstName"), it.getString("lastName"))
                                                .joinToString(" ").trim().ifEmpty { "User" } to it.getString("profileImageUrl")
                                            )
                                    }
                                }
                        }
                    }
                }
            onDispose { registration.remove() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inbox", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GreenPrimary)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = GreenPrimary) {
                NavigationBarItem(selected = false, onClick = onHomeClick, icon = { Icon(Icons.Default.Home, null, tint = Color.White) }, label = { Text("Home", color = Color.White) })
                NavigationBarItem(selected = false, onClick = onCalendarClick, icon = { Icon(Icons.Default.CalendarMonth, null, tint = Color.White) }, label = { Text("Calendar", color = Color.White) })
                NavigationBarItem(selected = true, onClick = {}, icon = { Icon(Icons.Default.Inbox, null, tint = Color.White) }, label = { Text("Inbox", color = Color.White) })
                NavigationBarItem(selected = false, onClick = onProfileClick, icon = { Icon(Icons.Default.Person, null, tint = Color.White) }, label = { Text("Profile", color = Color.White) })
            }
        }
    ) { padding ->
        Box(
            Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)
        ) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = GreenPrimary)
                threads.isEmpty() -> Text("Your messages will appear here.", Modifier.align(Alignment.Center), color = Color.Gray)
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(threads, key = { it.id }) { thread ->
                        val (name, photo) = profiles[thread.otherUserId] ?: ("User" to null)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .neumorphic(shape = RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                                .combinedClickable(
                                    onClick = { onConversationClick(thread.otherUserId) },
                                    onLongClick = {
                                        db.collection("directThreads").document(thread.id)
                                            .update("hiddenFor", FieldValue.arrayUnion(currentUserId))
                                            .addOnFailureListener {
                                                Toast.makeText(context, "Could not remove conversation", Toast.LENGTH_SHORT).show()
                                            }
                                    }
                                )
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatar(name, photo, 48)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(name, style = MaterialTheme.typography.titleMedium, color = Color.Black)
                                Text(thread.lastMessage, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.Gray)
                            }
                            if (thread.unread) Badge { Text("1") }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectMessageScreen(
    recipientId: String,
    onBackClick: () -> Unit = {}
) {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val currentUserId = auth.currentUser?.uid.orEmpty()
    val context = LocalContext.current
    val threadId = remember(currentUserId, recipientId) { listOf(currentUserId, recipientId).sorted().joinToString("_") }
    val threadRef = db.collection("directThreads").document(threadId)
    val messageRef = threadRef.collection("messages")
    var recipientName by remember { mutableStateOf("User") }
    var recipientPhoto by remember { mutableStateOf<String?>(null) }
    var currentUserName by remember { mutableStateOf("User") }
    var currentUserPhoto by remember { mutableStateOf<String?>(null) }
    var messages by remember { mutableStateOf<List<DirectMessage>>(emptyList()) }
    var messageText by rememberSaveable { mutableStateOf("") }
    var selectedMessage by remember { mutableStateOf<DirectMessage?>(null) }
    var editMessage by remember { mutableStateOf<DirectMessage?>(null) }
    var editText by remember { mutableStateOf("") }
    var isUploading by remember { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(currentUserId, recipientId) {
        db.collection("users").document(recipientId).get().addOnSuccessListener { user ->
            recipientName = listOfNotNull(user.getString("firstName"), user.getString("lastName"))
                .joinToString(" ").trim().ifEmpty { "User" }
            recipientPhoto = user.getString("profileImageUrl")
        }
        db.collection("users").document(currentUserId).get().addOnSuccessListener { user ->
            currentUserName = listOfNotNull(user.getString("firstName"), user.getString("lastName"))
                .joinToString(" ").trim().ifEmpty { "User" }
            currentUserPhoto = user.getString("profileImageUrl")
        }
    }

    DisposableEffect(threadId, currentUserId) {
        threadRef.set(
            mapOf("id" to threadId, "participants" to listOf(currentUserId, recipientId)),
            com.google.firebase.firestore.SetOptions.merge()
        )
        val registration = messageRef.orderBy("timestamp", Query.Direction.ASCENDING).limitToLast(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(context, "Could not load conversation", Toast.LENGTH_SHORT).show()
                } else if (snapshot != null) {
                    messages = snapshot.documents.mapNotNull { it.toObject(DirectMessage::class.java)?.copy(id = it.id) }
                    threadRef.set(
                        mapOf("lastReadBy" to FieldValue.arrayUnion(currentUserId)),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                    val unread = snapshot.documents.filter { document ->
                        val msg = document.toObject(DirectMessage::class.java)
                        msg != null && msg.senderId != currentUserId && currentUserId !in msg.readBy
                    }
                    if (unread.isNotEmpty()) {
                        val batch = db.batch()
                        unread.forEach { batch.update(it.reference, "readBy", FieldValue.arrayUnion(currentUserId)) }
                        batch.commit()
                    }
                }
            }
        onDispose { registration.remove() }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { uriToUpload ->
            val name = getFileName(context, uriToUpload) ?: "Attachment"
            isUploading = true
            uploadToCloudinary(context, uriToUpload, "users/$currentUserId/direct_messages") { url ->
                if (url == null) {
                    isUploading = false
                    Toast.makeText(context, "Attachment upload failed", Toast.LENGTH_SHORT).show()
                } else sendDirectMessage(
                    db, threadRef, messageRef, threadId, currentUserId, recipientId,
                    currentUserName, currentUserPhoto, "", url, name, context
                ) { isUploading = false }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(recipientName, recipientPhoto, 36)
                        Spacer(Modifier.width(10.dp))
                        Text(recipientName, color = Color.White)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        threadRef.update("hiddenFor", FieldValue.arrayUnion(currentUserId))
                            .addOnSuccessListener { onBackClick() }
                            .addOnFailureListener {
                                Toast.makeText(context, "Could not remove conversation", Toast.LENGTH_SHORT).show()
                            }
                    }) { Icon(Icons.Default.Delete, "Delete conversation from inbox", tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GreenPrimary)
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding().background(MaterialTheme.colorScheme.background)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(messages, key = { _, message -> message.id }) { _, message ->
                    DirectMessageBubble(
                        message = message,
                        isMe = message.senderId == currentUserId,
                        imageUrl = if (message.senderId == currentUserId) currentUserPhoto else recipientPhoto,
                        onLongClick = { if (message.senderId == currentUserId) selectedMessage = message },
                        onAttachmentClick = { url, name -> downloadFile(context, url, name) }
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { filePicker.launch("*/*") }, enabled = !isUploading) {
                    Icon(Icons.Default.AttachFile, "Attach photo or file", tint = GreenPrimary)
                }
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message...") },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4
                )
                IconButton(
                    enabled = messageText.isNotBlank() && !isUploading,
                    onClick = {
                        sendDirectMessage(
                            db, threadRef, messageRef, threadId, currentUserId, recipientId,
                            currentUserName, currentUserPhoto, messageText.trim(), null, null, context
                        ) { success -> if (success) messageText = "" }
                    }
                ) {
                    if (isUploading) CircularProgressIndicator(Modifier.size(20.dp), color = GreenPrimary)
                    else Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = GreenPrimary)
                }
            }
        }
    }

    selectedMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { selectedMessage = null },
            title = { Text("Message options") },
            text = { Text(message.message.ifBlank { message.attachmentName ?: "Attachment" }) },
            confirmButton = {
                TextButton(onClick = {
                    editText = message.message
                    editMessage = message
                    selectedMessage = null
                }) { Text("Edit") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        messageRef.document(message.id).delete()
                            .addOnSuccessListener { selectedMessage = null }
                            .addOnFailureListener { Toast.makeText(context, "Could not delete message", Toast.LENGTH_SHORT).show() }
                    }) { Text("Delete", color = Color.Red) }
                    TextButton(onClick = { selectedMessage = null }) { Text("Cancel") }
                }
            }
        )
    }
    editMessage?.let { target ->
        AlertDialog(
            onDismissRequest = { editMessage = null },
            title = { Text("Edit message") },
            text = {
                OutlinedTextField(editText, { editText = it }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editText.isBlank()) {
                        Toast.makeText(context, "Message cannot be empty", Toast.LENGTH_SHORT).show()
                    } else messageRef.document(target.id).update("message", editText.trim())
                        .addOnSuccessListener { editMessage = null }
                        .addOnFailureListener { Toast.makeText(context, "Could not edit message", Toast.LENGTH_SHORT).show() }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editMessage = null }) { Text("Cancel") } }
        )
    }
}

private fun sendDirectMessage(
    db: FirebaseFirestore,
    threadRef: com.google.firebase.firestore.DocumentReference,
    messageRef: com.google.firebase.firestore.CollectionReference,
    threadId: String,
    senderId: String,
    recipientId: String,
    senderName: String,
    senderPhoto: String?,
    message: String,
    attachmentUrl: String?,
    attachmentName: String?,
    context: android.content.Context,
    onComplete: (Boolean) -> Unit
) {
    val now = System.currentTimeMillis()
    val batch = db.batch()
    val messageDocument = messageRef.document()
    batch.set(
        threadRef,
        mapOf(
            "id" to threadId,
            "participants" to listOf(senderId, recipientId),
            "lastMessage" to message.ifBlank { attachmentName ?: "Attachment" },
            "lastTimestamp" to now,
            "lastSenderId" to senderId,
            "lastReadBy" to listOf(senderId),
            "hiddenFor" to FieldValue.arrayRemove(senderId)
        ),
        com.google.firebase.firestore.SetOptions.merge()
    )
    batch.set(
        messageDocument,
        DirectMessage(
            senderId = senderId,
            senderName = senderName,
            senderProfileImageUrl = senderPhoto,
            message = message,
            attachmentUrl = attachmentUrl,
            attachmentName = attachmentName,
            timestamp = now,
            readBy = listOf(senderId)
        )
    )
    batch.commit()
        .addOnSuccessListener { onComplete(true) }
        .addOnFailureListener {
            Toast.makeText(context, "Could not send message", Toast.LENGTH_SHORT).show()
            onComplete(false)
        }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DirectMessageBubble(
    message: DirectMessage,
    isMe: Boolean,
    imageUrl: String?,
    onLongClick: () -> Unit,
    onAttachmentClick: (String, String?) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isMe) UserAvatar(message.senderName, imageUrl, 30)
        if (!isMe) Spacer(Modifier.width(6.dp))
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .combinedClickable(onClick = {}, onLongClick = onLongClick)
                .neumorphic(shape = RoundedCornerShape(14.dp))
                .background(if (isMe) GreenPrimary else MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                .padding(10.dp)
        ) {
            if (message.message.isNotBlank()) {
                Text(message.message, color = if (isMe) Color.White else Color.Black)
            }
            if (!message.attachmentUrl.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                AsyncImage(
                    model = message.attachmentUrl,
                    contentDescription = message.attachmentName,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .combinedClickable(
                            onClick = { onAttachmentClick(message.attachmentUrl, message.attachmentName) },
                            onLongClick = onLongClick
                        ),
                    contentScale = ContentScale.Crop
                )
                Text(
                    message.attachmentName ?: "Open attachment",
                    color = if (isMe) Color.White else GreenPrimary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                modifier = Modifier.align(Alignment.End),
                color = if (isMe) Color.White.copy(alpha = 0.7f) else Color.Gray,
                fontSize = 10.sp
            )
        }
        if (isMe) Spacer(Modifier.width(6.dp))
        if (isMe) UserAvatar(message.senderName, imageUrl, 30)
    }
}

@Composable
private fun UserAvatar(name: String, imageUrl: String?, size: Int) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(GreenPrimary.copy(alpha = 0.15f)),
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
            Text(name.take(1).uppercase(), color = GreenPrimary)
        }
    }
}
