package com.omnira.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.omnira.data.Notification
import com.omnira.data.RoutineData
import com.omnira.ui.components.OmniraLoadingLogo
import com.omnira.ui.theme.GreenPrimary
import com.omnira.ui.theme.OMNIRATheme
import com.omnira.ui.theme.YellowSecondary
import com.omnira.ui.theme.getNeumorphicTextStyle
import com.omnira.ui.theme.neumorphic
import com.omnira.utils.getDayOfWeekFromDate
import com.omnira.utils.isDateTimeExpired
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onQuickAccessClick: (String) -> Unit = {},
    onProfileClick: () -> Unit = {},
    onCalendarClick: () -> Unit = {},
    onInboxClick: () -> Unit = {}
) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val currentUserId = auth.currentUser?.uid ?: ""
    
    var todaysClasses by remember { mutableStateOf<List<RoutineData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isTeacherOfAnyClass by remember { mutableStateOf(false) }
    var quickAccessUnread by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var badgeNotifications by remember { mutableStateOf<List<Notification>>(emptyList()) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while(true) {
            delay(30000)
            tick++
        }
    }

    LaunchedEffect(currentUserId, tick) {
        if (currentUserId.isNotEmpty()) {
            val calendar = Calendar.getInstance()
            val dayOfWeek = calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.ENGLISH) ?: ""
            val todayDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(calendar.time)

            db.collection("classes")
                .whereArrayContains("members", currentUserId)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        val allRoutines = mutableListOf<RoutineData>()
                        val docs = snapshot.documents
                        
                        isTeacherOfAnyClass = docs.any { it.getString("creatorId") == currentUserId }

                        if (docs.isEmpty()) {
                            isLoading = false
                            todaysClasses = emptyList()
                            return@addSnapshotListener
                        }

                        var processed = 0
                        docs.forEach { doc ->
                            val classId = doc.id
                            doc.reference.collection("routines")
                                .get()
                                .addOnSuccessListener { routineSnapshot ->
                                    routineSnapshot.documents.forEach { routineDoc ->
                                        routineDoc.toObject(RoutineData::class.java)?.let { routine ->
                                            val r = routine.copy(id = routineDoc.id, classCode = classId)
                                            if (r.rescheduledDate.isNotEmpty() && isDateTimeExpired(r.rescheduledDate, r.rescheduledEndTime.ifEmpty { r.rescheduledTime })) {
                                                db.collection("classes").document(classId).collection("routines").document(r.id)
                                                    .update("isCancelled", false, "rescheduledDate", "", "rescheduledTime", "", "rescheduledEndTime", "")
                                            } else {
                                                allRoutines.add(r)
                                            }
                                        }
                                    }
                                    processed++
                                    if (processed == docs.size) {
                                        todaysClasses = allRoutines.filter { routine ->
                                            val isOriginalToday = routine.day == dayOfWeek
                                            val isRescheduledToday = routine.rescheduledDate == todayDate
                                            isOriginalToday || isRescheduledToday
                                        }.sortedBy { it.time }
                                        isLoading = false
                                    }
                                }
                                .addOnFailureListener {
                                    processed++
                                    if (processed == docs.size) isLoading = false
                                }
                        }
                    } else {
                        isLoading = false
                    }
                }
        }
    }

    DisposableEffect(currentUserId) {
        if (currentUserId.isBlank()) {
            quickAccessUnread = emptyMap()
            onDispose { }
        } else {
            val registrations = mutableListOf<ListenerRegistration>()
            val notificationsBySource = mutableMapOf<String, Map<String, Notification>>()
            fun updateCounts() {
                val unread = notificationsBySource.values.flatMap { it.values }
                    .distinctBy { it.id }
                    .filter { currentUserId !in it.readBy }
                badgeNotifications = unread
                quickAccessUnread = mapOf(
                    "Notification" to unread.size,
                    "Materials" to unread.count { it.type == "MATERIAL" },
                    "Assignments" to unread.count { it.type == "ASSIGNMENT" }
                )
            }
            val memberTask = db.collection("classes").whereArrayContains("members", currentUserId).get()
            val creatorTask = db.collection("classes").whereEqualTo("creatorId", currentUserId).get()
            val adminTask = db.collection("classes").whereArrayContains("admins", currentUserId).get()
            Tasks.whenAllComplete(memberTask, creatorTask, adminTask).addOnSuccessListener {
                val classIds = (memberTask.result?.documents.orEmpty() +
                    creatorTask.result?.documents.orEmpty() +
                    adminTask.result?.documents.orEmpty()).map { it.id }.distinct()
                fun listen(sourceKey: String, query: com.google.firebase.firestore.Query) {
                    registrations.add(query.addSnapshotListener { snapshot, _ ->
                        notificationsBySource[sourceKey] = snapshot?.documents.orEmpty().mapNotNull { document ->
                            document.toObject(Notification::class.java)?.copy(id = document.id)
                                ?.let { it.id to it }
                        }.toMap()
                        updateCounts()
                    })
                }
                classIds.forEach { classId ->
                    listen("class:$classId", db.collection("notifications").whereEqualTo("classId", classId))
                }
                listen(
                    "recipient:$currentUserId",
                    db.collection("notifications").whereEqualTo("recipientId", currentUserId)
                )
            }
            onDispose { registrations.forEach { it.remove() } }
        }
    }

    val quickAccessItems = remember(isTeacherOfAnyClass, quickAccessUnread) {
        buildList {
            add(QuickAccessItem("Classes", Icons.Default.Groups, Color(0xFFE8F5E9), GreenPrimary))
            add(QuickAccessItem("Assignments", Icons.AutoMirrored.Filled.Assignment, Color(0xFFE8F5E9), GreenPrimary, quickAccessUnread["Assignments"] ?: 0))
            add(QuickAccessItem("Materials", Icons.AutoMirrored.Filled.MenuBook, Color(0xFFE8F5E9), GreenPrimary, quickAccessUnread["Materials"] ?: 0))
            add(QuickAccessItem("Attendance", Icons.Default.HowToReg, Color(0xFFE8F5E9), GreenPrimary))
            add(QuickAccessItem("Notification", Icons.Default.Campaign, Color(0xFFE8F5E9), GreenPrimary, quickAccessUnread["Notification"] ?: 0))
        }
    }

    fun openQuickAccess(title: String) {
        val type = when (title) {
            "Materials" -> "MATERIAL"
            "Assignments" -> "ASSIGNMENT"
            else -> null
        }
        if (type != null) {
            badgeNotifications.filter { it.type == type }.forEach { notification ->
                db.collection("notifications").document(notification.id)
                    .update("readBy", com.google.firebase.firestore.FieldValue.arrayUnion(currentUserId))
            }
        }
        onQuickAccessClick(title)
    }

    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (isLandscape) {
            NavigationRail(
                containerColor = GreenPrimary,
                contentColor = Color.White
            ) {
                NavigationRailItem(
                    selected = true,
                    onClick = {},
                    icon = { Icon(Icons.Default.Home, null) },
                    label = { Text("Home", style = MaterialTheme.typography.labelSmall) },
                    colors = NavigationRailItemDefaults.colors(selectedIconColor = YellowSecondary, selectedTextColor = YellowSecondary, unselectedIconColor = Color.White, unselectedTextColor = Color.White)
                )
                NavigationRailItem(
                    selected = false,
                    onClick = onCalendarClick,
                    icon = { Icon(Icons.Default.CalendarMonth, null) },
                    label = { Text("Calendar", style = MaterialTheme.typography.labelSmall) },
                    colors = NavigationRailItemDefaults.colors(unselectedIconColor = Color.White, unselectedTextColor = Color.White)
                )
                NavigationRailItem(
                    selected = false,
                    onClick = onInboxClick,
                    icon = { Icon(Icons.Default.Inbox, null) },
                    label = { Text("Inbox", style = MaterialTheme.typography.labelSmall) },
                    colors = NavigationRailItemDefaults.colors(unselectedIconColor = Color.White, unselectedTextColor = Color.White)
                )
                NavigationRailItem(
                    selected = false,
                    onClick = onProfileClick,
                    icon = { Icon(Icons.Default.Person, null) },
                    label = { Text("Profile", style = MaterialTheme.typography.labelSmall) },
                    colors = NavigationRailItemDefaults.colors(unselectedIconColor = Color.White, unselectedTextColor = Color.White)
                )
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Dashboard", style = getNeumorphicTextStyle(MaterialTheme.typography.headlineMedium, Color.White)) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = GreenPrimary)
                )
            },
            bottomBar = {
                if (!isLandscape) {
                    NavigationBar(containerColor = GreenPrimary, contentColor = Color.White) {
                        NavigationBarItem(selected = true, onClick = { }, icon = { Icon(Icons.Default.Home, null, tint = YellowSecondary) }, label = { Text("Home", color = Color.White, style = MaterialTheme.typography.labelSmall) })
                        NavigationBarItem(selected = false, onClick = onCalendarClick, icon = { Icon(Icons.Default.CalendarMonth, null, tint = Color.White) }, label = { Text("Calendar", color = Color.White, style = MaterialTheme.typography.labelSmall) })
                        NavigationBarItem(selected = false, onClick = onInboxClick, icon = { Icon(Icons.Default.Inbox, null, tint = Color.White) }, label = { Text("Inbox", color = Color.White, style = MaterialTheme.typography.labelSmall) })
                        NavigationBarItem(selected = false, onClick = onProfileClick, icon = { Icon(Icons.Default.Person, null, tint = Color.White) }, label = { Text("Profile", color = Color.White, style = MaterialTheme.typography.labelSmall) })
                    }
                }
            }
        ) { innerPadding ->
            val contentModifier = Modifier.padding(innerPadding).fillMaxSize().padding(16.dp)
            
            if (isLandscape) {
                Row(modifier = contentModifier) {
                    Column(modifier = Modifier.weight(0.4f)) {
                        Text("Quick Access", style = getNeumorphicTextStyle(MaterialTheme.typography.titleMedium))
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(quickAccessItems) { item ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .neumorphic()
                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(28.dp))
                                        .clickable { openQuickAccess(item.title) }
                                        .padding(12.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        BadgedBox(badge = { if (item.unreadCount > 0) Badge { Text(item.unreadCount.toString()) } }) {
                                            Icon(item.icon, contentDescription = item.title, tint = item.tintColor, modifier = Modifier.size(24.dp))
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(item.title, style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(24.dp))
                    Column(modifier = Modifier.weight(0.6f)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Today's Classes", style = getNeumorphicTextStyle(MaterialTheme.typography.titleMedium))
                            Text(text = "View All", color = GreenPrimary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clickable { onCalendarClick() })
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        if (isLoading) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { OmniraLoadingLogo(size = 80.dp) }
                        else if (todaysClasses.isEmpty()) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No classes today.", color = Color.Gray, style = MaterialTheme.typography.bodyMedium) }
                        else LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) { 
                            items(todaysClasses) { routine -> TodaysClassItem(routine) } 
                        }
                    }
                }
            } else {
                Column(modifier = contentModifier) {
                    Text("Quick Access", style = getNeumorphicTextStyle(MaterialTheme.typography.titleMedium))
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickAccessItems.forEach { item ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { openQuickAccess(item.title) }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .neumorphic(elevation = 4.dp)
                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(28.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    BadgedBox(
                                        badge = { if (item.unreadCount > 0) Badge { Text(item.unreadCount.toString()) } }
                                    ) {
                                        Icon(
                                            item.icon,
                                            contentDescription = item.title,
                                            tint = item.tintColor,
                                            modifier = Modifier.size(38.dp).padding(6.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    item.title, 
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1, 
                                    textAlign = TextAlign.Center,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Today's Classes", style = getNeumorphicTextStyle(MaterialTheme.typography.titleMedium))
                        Text(text = "View All", color = GreenPrimary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clickable { onCalendarClick() })
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    if (isLoading) Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { OmniraLoadingLogo(size = 80.dp) }
                    else if (todaysClasses.isEmpty()) Box(modifier = Modifier.fillMaxWidth().padding(top = 20.dp), contentAlignment = Alignment.Center) { Text(text = "No classes scheduled for today.", color = Color.Gray, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
                    else LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.weight(1f)) { 
                        items(todaysClasses) { routine -> TodaysClassItem(routine) } 
                    }
                }
            }
        }
    }
}

@Composable
fun TodaysClassItem(routine: RoutineData) {
    val isRescheduledValid = routine.rescheduledDate.isNotEmpty() && !isDateTimeExpired(routine.rescheduledDate, routine.rescheduledEndTime.ifEmpty { routine.rescheduledTime })
    val calendar = Calendar.getInstance()
    val todayDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(calendar.time)
    val dayOfWeek = calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.ENGLISH) ?: ""
    
    val isOriginalToday = routine.day == dayOfWeek
    val isRescheduledToday = routine.rescheduledDate == todayDate

    Column(modifier = Modifier.fillMaxWidth()) {
        if (isOriginalToday) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .neumorphic(elevation = 3.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(28.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(40.dp).background(if (isRescheduledValid) Color.Gray.copy(alpha = 0.1f) else GreenPrimary.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.School, null, tint = if (isRescheduledValid) Color.Gray else GreenPrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            routine.subject, 
                            style = getNeumorphicTextStyle(MaterialTheme.typography.titleMedium, if (isRescheduledValid) Color.Gray else Color.Black),
                        )
                        Text(routine.className, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                        if (isRescheduledValid) {
                            Text("Moved to ${routine.rescheduledDate}", style = MaterialTheme.typography.labelSmall, color = Color.Red, fontWeight = FontWeight.Bold)
                        }
                    }
                    val timeText = if (routine.endTime.isNotEmpty()) "${routine.time} - ${routine.endTime}" else routine.time
                    Text(
                        timeText, 
                        style = MaterialTheme.typography.labelLarge, 
                        color = if (isRescheduledValid) Color.Red else GreenPrimary
                    )
                }
            }
        }

        if (isRescheduledValid && (isRescheduledToday || isOriginalToday)) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ArrowDownward, null, tint = YellowSecondary, modifier = Modifier.size(20.dp))
            }

            if (isRescheduledToday) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp)
                        .neumorphic(elevation = 3.dp)
                        .background(Color(0xFFF0FDF4), RoundedCornerShape(28.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(40.dp).background(GreenPrimary.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.EventAvailable, null, tint = GreenPrimary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(routine.subject, style = getNeumorphicTextStyle(MaterialTheme.typography.titleMedium))
                            Text("New Rescheduled Slot", style = MaterialTheme.typography.labelSmall, color = GreenPrimary, fontWeight = FontWeight.Bold)
                        }
                        val resTimeText = if (routine.rescheduledEndTime.isNotEmpty()) "${routine.rescheduledTime} - ${routine.rescheduledEndTime}" else routine.rescheduledTime
                        Text(resTimeText, style = MaterialTheme.typography.labelLarge, color = GreenPrimary)
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp)
                        .neumorphic(elevation = 2.dp)
                        .background(Color(0xFFFFF9C4), RoundedCornerShape(28.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "Class rescheduled to ${routine.rescheduledDate} at ${routine.rescheduledTime}",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.DarkGray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

data class QuickAccessItem(
    val title: String,
    val icon: ImageVector,
    val bgColor: Color,
    val tintColor: Color,
    val unreadCount: Int = 0
)

@Preview(showBackground = true)
@Composable
fun DashboardScreenPreview() {
    OMNIRATheme { DashboardScreen() }
}
