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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.omnira.data.RoutineData
import com.omnira.ui.theme.GreenPrimary
import com.omnira.ui.theme.OMNIRATheme
import com.omnira.ui.theme.YellowSecondary
import com.omnira.ui.theme.neumorphic
import com.omnira.utils.getDayOfWeekFromDate
import com.omnira.utils.isDateTimeExpired
import kotlinx.coroutines.delay
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassRoutineScreen(
    onBackClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onCalendarClick: () -> Unit = {}
) {
    var selectedDay by remember { mutableIntStateOf(0) }
    val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    val shortDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val currentUserId = auth.currentUser?.uid ?: ""

    val routinesMap = remember { mutableStateMapOf<String, List<RoutineData>>() }
    var isLoading by remember { mutableStateOf(true) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while(true) {
            delay(30000)
            tick++
        }
    }

    LaunchedEffect(tick, routinesMap.toMap()) {
        routinesMap.forEach { (classId, routines) ->
            routines.forEach { routine ->
                if (routine.rescheduledDate.isNotEmpty()) {
                    val expirationTime = routine.rescheduledEndTime.ifEmpty { routine.rescheduledTime }
                    if (isDateTimeExpired(routine.rescheduledDate, expirationTime)) {
                        db.collection("classes").document(classId).collection("routines").document(routine.id)
                            .update(
                                "isCancelled", false,
                                "rescheduledDate", "",
                                "rescheduledTime", "",
                                "rescheduledEndTime", ""
                            )
                    }
                }
            }
        }
    }

    DisposableEffect(currentUserId) {
        if (currentUserId.isEmpty()) return@DisposableEffect onDispose {}

        val routineListeners = mutableMapOf<String, ListenerRegistration>()
        
        val classesListener = db.collection("classes")
            .whereArrayContains("members", currentUserId)
            .addSnapshotListener { classesSnapshot, _ ->
                if (classesSnapshot == null) return@addSnapshotListener
                
                val currentClassIds = classesSnapshot.documents.map { it.id }.toSet()
                
                val idsToRemove = routineListeners.keys - currentClassIds
                idsToRemove.forEach { id ->
                    routineListeners[id]?.remove()
                    routineListeners.remove(id)
                    routinesMap.remove(id)
                }

                classesSnapshot.documents.forEach { classDoc ->
                    val classId = classDoc.id
                    
                    if (!routineListeners.containsKey(classId)) {
                        val listener = classDoc.reference.collection("routines")
                            .addSnapshotListener { routinesSnapshot, _ ->
                                if (routinesSnapshot != null) {
                                    val routines = routinesSnapshot.documents.mapNotNull { doc ->
                                        doc.toObject(RoutineData::class.java)?.copy(id = doc.id, classCode = classId)
                                    }
                                    routinesMap[classId] = routines
                                    isLoading = false
                                }
                            }
                        routineListeners[classId] = listener
                    }
                }
                if (currentClassIds.isEmpty()) isLoading = false
            }

        onDispose {
            classesListener.remove()
            routineListeners.values.forEach { it.remove() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Calendar & Routine", style = MaterialTheme.typography.titleLarge, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GreenPrimary)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = GreenPrimary) {
                NavigationBarItem(selected = false, onClick = onBackClick, icon = { Icon(Icons.Default.Home, null, tint = Color.White) }, label = { Text("Home", color = Color.White, style = MaterialTheme.typography.labelSmall) })
                NavigationBarItem(selected = true, onClick = { }, icon = { Icon(Icons.Default.CalendarMonth, null, tint = YellowSecondary) }, label = { Text("Calendar", color = Color.White, style = MaterialTheme.typography.labelSmall) })
                NavigationBarItem(selected = false, onClick = onProfileClick, icon = { Icon(Icons.Default.Person, null, tint = Color.White) }, label = { Text("Profile", color = Color.White, style = MaterialTheme.typography.labelSmall) })
            }
        }
    ) { innerPadding ->
        val allRoutinesList = routinesMap.values.flatten()

        if (isLandscape) {
            Row(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                Column(modifier = Modifier.weight(0.3f).fillMaxHeight().background(MaterialTheme.colorScheme.surface).padding(vertical = 16.dp)) {
                    Text("Select Day", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium, color = GreenPrimary)
                    days.forEachIndexed { index, day ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .neumorphic(elevation = if (selectedDay == index) 0.dp else 4.dp)
                                .background(
                                    if (selectedDay == index) GreenPrimary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedDay = index }
                                .padding(12.dp)
                        ) {
                            Text(day, color = if (selectedDay == index) GreenPrimary else Color.Black, style = if (selectedDay == index) MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                Box(modifier = Modifier.weight(0.7f)) {
                    RoutineContent(
                        isLoading = isLoading,
                        selectedDay = selectedDay,
                        days = days,
                        allRoutines = allRoutinesList,
                        tick = tick
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                ScrollableTabRow(
                    selectedTabIndex = selectedDay,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = GreenPrimary,
                    edgePadding = 16.dp,
                    divider = {},
                    indicator = { tabPositions ->
                        if (selectedDay < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedDay]),
                                color = GreenPrimary
                            )
                        }
                    }
                ) {
                    shortDays.forEachIndexed { index, day ->
                        Tab(
                            selected = selectedDay == index,
                            onClick = { selectedDay = index },
                            text = {
                                Text(
                                    day,
                                    color = if (selectedDay == index) GreenPrimary else Color.Gray,
                                    style = if (selectedDay == index) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium
                                )
                            }
                        )
                    }
                }

                RoutineContent(
                    isLoading = isLoading,
                    selectedDay = selectedDay,
                    days = days,
                    allRoutines = allRoutinesList,
                    tick = tick
                )
            }
        }
    }
}

@Composable
fun RoutineContent(
    isLoading: Boolean,
    selectedDay: Int,
    days: List<String>,
    allRoutines: List<RoutineData>,
    tick: Int
) {
    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = GreenPrimary)
        }
    } else {
        val currentDayName = days[selectedDay]
        
        val dayRoutines = remember(allRoutines, selectedDay, tick) {
            allRoutines.filter { routine ->
                val isOriginalDay = routine.day == currentDayName
                val rescheduledDay = if (routine.rescheduledDate.isNotEmpty()) getDayOfWeekFromDate(routine.rescheduledDate) else ""
                val isRescheduledValid = routine.rescheduledDate.isNotEmpty() && !isDateTimeExpired(routine.rescheduledDate, routine.rescheduledEndTime.ifEmpty { routine.rescheduledTime })
                
                isOriginalDay || (isRescheduledValid && rescheduledDay == currentDayName)
            }.sortedBy { it.time }
        }

        if (dayRoutines.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No classes scheduled for ${days[selectedDay]}.",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(dayRoutines) { routine ->
                    GlobalRoutineItem(
                        routine = routine,
                        currentDayName = currentDayName
                    )
                }
            }
        }
    }
}

@Composable
fun GlobalRoutineItem(
    routine: RoutineData,
    currentDayName: String = ""
) {
    val isRescheduledValid = routine.rescheduledDate.isNotEmpty() && !isDateTimeExpired(routine.rescheduledDate, routine.rescheduledEndTime.ifEmpty { routine.rescheduledTime })
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .neumorphic(elevation = 4.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.width(100.dp)) {
                    Text(
                        text = if (routine.endTime.isNotEmpty()) "${routine.time} - ${routine.endTime}" else routine.time,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isRescheduledValid) Color.Red else GreenPrimary
                    )
                    Text(
                        text = "Original (${routine.day})",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
                
                VerticalDivider(
                    modifier = Modifier.height(40.dp).width(1.dp),
                    color = Color.LightGray.copy(alpha = 0.5f)
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = routine.subject,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isRescheduledValid) Color.Gray else Color.Black
                    )
                    
                    if (routine.roomNumber.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Room: ${routine.roomNumber}", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                        }
                    }
                    
                    Text(
                        text = if (isRescheduledValid) "Class Moved / Rescheduled" else "Weekly Class",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isRescheduledValid) Color.Red else GreenPrimary
                    )
                    Text(
                        text = routine.className,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }
        }

        if (isRescheduledValid) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ArrowDownward,
                        contentDescription = "Directing to rescheduled class",
                        tint = YellowSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Rescheduled Details Below",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = YellowSecondary
                    )
                }
            }

            val displayRescheduledTime = if (routine.rescheduledEndTime.isNotEmpty()) "${routine.rescheduledTime} - ${routine.rescheduledEndTime}" else routine.rescheduledTime
            val rescheduledDayName = getDayOfWeekFromDate(routine.rescheduledDate)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp)
                    .neumorphic(elevation = 4.dp)
                    .background(Color(0xFFF0FDF4), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.width(100.dp)) {
                        Text(
                            text = displayRescheduledTime,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = GreenPrimary
                        )
                        Text(
                            text = "${routine.rescheduledDate} ($rescheduledDayName)",
                            style = MaterialTheme.typography.labelSmall,
                            color = GreenPrimary
                        )
                    }
                    
                    VerticalDivider(
                        modifier = Modifier.height(40.dp).width(1.dp),
                        color = Color.LightGray.copy(alpha = 0.5f)
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = routine.subject,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.Black
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.EventAvailable, null, tint = GreenPrimary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New Active Rescheduled Slot", style = MaterialTheme.typography.labelMedium, color = GreenPrimary)
                        }
                        Text(
                            text = routine.className,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ClassRoutineScreenPreview() {
    OMNIRATheme {
        ClassRoutineScreen()
    }
}
