package com.omnira.ui.screens

import android.app.DatePickerDialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.omnira.data.AttendanceSheetRecord
import com.omnira.data.AttendanceUser
import com.omnira.ui.components.OmniraLoadingLogo
import com.omnira.ui.theme.GreenPrimary
import com.omnira.ui.theme.OMNIRATheme
import com.omnira.ui.theme.YellowSecondary
import com.omnira.ui.theme.neumorphic
import com.omnira.utils.downloadFile
import com.omnira.utils.uploadToCloudinary
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(
    onBackClick: () -> Unit = {},
    onProfileClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val currentUserId = auth.currentUser?.uid ?: ""

    var classes by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var selectedClassId by rememberSaveable { mutableStateOf("") }
    var selectedClassName by rememberSaveable { mutableStateOf("Select Class") }
    var classBatch by rememberSaveable { mutableStateOf("") }
    var classMenuExpanded by rememberSaveable { mutableStateOf(false) }
    
    var selectedDate by rememberSaveable { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
    var isTeacher by rememberSaveable { mutableStateOf(false) }
    var students by remember { mutableStateOf<List<AttendanceUser>>(emptyList()) }
    
    var savedSheets by remember { mutableStateOf<List<AttendanceSheetRecord>>(emptyList()) }
    var isLoadingSheets by remember { mutableStateOf(false) }
    var lastSheetDoc by remember { mutableStateOf<DocumentSnapshot?>(null) }
    var isLastSheetsPage by remember { mutableStateOf(false) }
    val sheetsPageSize = 10L

    var isLoading by rememberSaveable { mutableStateOf(false) }
    var isSavingPdf by rememberSaveable { mutableStateOf(false) }
    var isFetchingClasses by rememberSaveable { mutableStateOf(true) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val calendar = Calendar.getInstance()
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, day ->
            val cal = Calendar.getInstance()
            cal.set(year, month, day)
            selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    fun fetchAttendanceSheets(isFirstPage: Boolean = false) {
        if (selectedClassId.isEmpty() || isLoadingSheets || (isLastSheetsPage && !isFirstPage)) return
        
        isLoadingSheets = true
        var query = db.collection("classes").document(selectedClassId).collection("attendance_sheets")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(sheetsPageSize)
            
        if (!isFirstPage && lastSheetDoc != null) {
            query = query.startAfter(lastSheetDoc!!)
        }

        query.get().addOnSuccessListener { snapshot ->
            val newSheets = snapshot.documents.mapNotNull { doc ->
                doc.toObject(AttendanceSheetRecord::class.java)?.copy(id = doc.id)
            }
            if (isFirstPage) {
                savedSheets = newSheets
            } else {
                savedSheets = savedSheets + newSheets
            }
            lastSheetDoc = snapshot.documents.lastOrNull()
            isLastSheetsPage = snapshot.size() < sheetsPageSize
            isLoadingSheets = false
        }.addOnFailureListener {
            isLoadingSheets = false
        }
    }

    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotEmpty()) {
            db.collection("classes")
                .whereEqualTo("creatorId", currentUserId)
                .get()
                .addOnSuccessListener { snapshot ->
                    classes = snapshot.documents.map { it.id to (it.getString("name") ?: "Unnamed") }
                    isFetchingClasses = false
                }
                .addOnFailureListener {
                    isFetchingClasses = false
                }
        }
    }

    LaunchedEffect(selectedClassId, selectedDate) {
        if (selectedClassId.isNotEmpty()) {
            isLoading = true
            db.collection("classes").document(selectedClassId).get().addOnSuccessListener { doc ->
                isTeacher = doc.getString("creatorId") == currentUserId
                classBatch = doc.getString("batch") ?: "General"
                val members = (doc.get("members") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                
                if (members.isNotEmpty()) {
                    db.collection("users").whereIn("uid", members).get().addOnSuccessListener { userSnapshot ->
                        val userList = userSnapshot.documents.map { uDoc ->
                            val first = uDoc.getString("firstName") ?: ""
                            val last = uDoc.getString("lastName") ?: ""
                            val name = "$first $last".trim().ifEmpty { "Unknown" }
                            AttendanceUser(uDoc.id, name)
                        }

                        db.collection("classes").document(selectedClassId)
                            .collection("attendance").document(selectedDate).get()
                            .addOnSuccessListener { attDoc ->
                                val record = attDoc.data ?: emptyMap<String, Any>()
                                students = userList.map { user ->
                                    user.copy(isPresent = record[user.id] as? Boolean ?: false)
                                }
                                isLoading = false
                            }
                            .addOnFailureListener {
                                students = userList
                                isLoading = false
                            }
                    }.addOnFailureListener {
                        isLoading = false
                        Toast.makeText(context, "Failed to load students", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    students = emptyList()
                    isLoading = false
                }
            }
            
            isLastSheetsPage = false
            lastSheetDoc = null
            fetchAttendanceSheets(isFirstPage = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Attendance Manager", style = MaterialTheme.typography.titleLarge, color = Color.White) },
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GreenPrimary)
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
            AttendanceSelectors(
                selectedClassName, { classMenuExpanded = true },
                selectedDate, { datePickerDialog.show() }
            )
            DropdownMenu(expanded = classMenuExpanded, onDismissRequest = { classMenuExpanded = false }) {
                classes.forEach { (id, name) ->
                    DropdownMenuItem(text = { Text(name, style = MaterialTheme.typography.bodyLarge) }, onClick = {
                        selectedClassId = id
                        selectedClassName = name
                        classMenuExpanded = false
                    })
                }
            }

            if (selectedClassId.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = GreenPrimary,
                    divider = {},
                    indicator = { tabPositions ->
                        if (selectedTab < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = GreenPrimary
                            )
                        }
                    }
                ) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Mark Attendance", style = MaterialTheme.typography.titleSmall) })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Attendance Sheets", style = MaterialTheme.typography.titleSmall) })
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (selectedTab == 0) {
                if (isLandscape) {
                    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        if (selectedClassId.isNotEmpty() && !isLoading) {
                            Column(modifier = Modifier.weight(0.4f)) {
                                AttendanceStats(students)
                            }
                        }
                        Column(modifier = Modifier.weight(0.6f)) {
                            AttendanceContent(
                                selectedClassId, isFetchingClasses, classes.isEmpty(), isLoading, students, isTeacher, isSavingPdf,
                                onStatusChange = { student, newState ->
                                    students = students.map { if (it.id == student.id) it.copy(isPresent = newState) else it }
                                },
                                onSave = {
                                    isSavingPdf = true
                                    saveAttendanceWithPdf(db, currentUserId, selectedClassId, selectedClassName, classBatch, selectedDate, students, context) {
                                        isSavingPdf = false
                                        isLastSheetsPage = false
                                        lastSheetDoc = null
                                        fetchAttendanceSheets(isFirstPage = true)
                                    }
                                }
                            )
                        }
                    }
                } else {
                    AttendanceContent(
                        selectedClassId, isFetchingClasses, classes.isEmpty(), isLoading, students, isTeacher, isSavingPdf,
                        onStatusChange = { student, newState ->
                            students = students.map { if (it.id == student.id) it.copy(isPresent = newState) else it }
                        },
                        onSave = {
                            isSavingPdf = true
                            saveAttendanceWithPdf(db, currentUserId, selectedClassId, selectedClassName, classBatch, selectedDate, students, context) {
                                isSavingPdf = false
                                isLastSheetsPage = false
                                lastSheetDoc = null
                                fetchAttendanceSheets(isFirstPage = true)
                            }
                        },
                        showStats = true
                    )
                }
            } else {
                if (savedSheets.isEmpty() && !isLoadingSheets) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No PDF sheets generated for this class yet.", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(savedSheets, key = { _, sheet -> sheet.id }) { index, sheet ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .neumorphic(elevation = 4.dp)
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                    .clickable {
                                        downloadFile(context, sheet.fileUrl, "Attendance_${sheet.className}_${sheet.date}.pdf")
                                    }
                                    .padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(40.dp).background(Color(0xFFFFEBEE), CircleShape), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.PictureAsPdf, null, tint = Color.Red)
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${sheet.className} (${sheet.batch})", style = MaterialTheme.typography.titleMedium)
                                        Text("Date: ${sheet.date}", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                                    }
                                    Icon(Icons.Default.Download, null, tint = GreenPrimary)
                                }
                            }
                            
                            if (index >= savedSheets.size - 1 && !isLastSheetsPage && !isLoadingSheets) {
                                LaunchedEffect(Unit) { fetchAttendanceSheets() }
                            }
                        }
                        
                        if (isLoadingSheets) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                                    OmniraLoadingLogo(size = 40.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AttendanceSelectors(
    className: String, onClassClick: () -> Unit,
    date: String, onDateClick: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .neumorphic(elevation = 4.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .clickable { onClassClick() }
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Class", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(className, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.Gray)
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .neumorphic(elevation = 4.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .clickable { onDateClick() }
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Date", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(date, style = MaterialTheme.typography.titleSmall)
                }
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.Default.CalendarMonth, null, tint = Color.Gray)
            }
        }
    }
}

@Composable
fun AttendanceStats(students: List<AttendanceUser>) {
    val presentCount = students.count { it.isPresent }
    val absentCount = students.size - presentCount

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StatCard("Present", presentCount.toString(), Color(0xFFE8F5E9), GreenPrimary, Modifier.fillMaxWidth())
        StatCard("Absent", absentCount.toString(), Color(0xFFFFEBEE), Color.Red, Modifier.fillMaxWidth())
        StatCard("Total", students.size.toString(), Color(0xFFE3F2FD), Color(0xFF1976D2), Modifier.fillMaxWidth())
    }
}

@Composable
fun AttendanceContent(
    selectedClassId: String, isFetchingClasses: Boolean, noClasses: Boolean,
    isLoading: Boolean, students: List<AttendanceUser>, isTeacher: Boolean, isSavingPdf: Boolean,
    onStatusChange: (AttendanceUser, Boolean) -> Unit, onSave: () -> Unit,
    showStats: Boolean = false
) {
    if (selectedClassId.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (isFetchingClasses) OmniraLoadingLogo(size = 80.dp)
            else if (noClasses) Text("You have not created any classes yet", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
            else Text("Please select a class to continue", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
        }
    } else if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            OmniraLoadingLogo(size = 80.dp)
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            if (showStats) {
                val presentCount = students.count { it.isPresent }
                val absentCount = students.size - presentCount
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatCard("Present", presentCount.toString(), Color(0xFFE8F5E9), GreenPrimary, Modifier.weight(1f))
                    StatCard("Absent", absentCount.toString(), Color(0xFFFFEBEE), Color.Red, Modifier.weight(1f))
                    StatCard("Total", students.size.toString(), Color(0xFFE3F2FD), Color(0xFF1976D2), Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                items(students) { student ->
                    AttendanceItem(student, isTeacher) { newState ->
                        onStatusChange(student, newState)
                    }
                }
            }

            if (isTeacher) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .height(50.dp)
                        .neumorphic(elevation = 4.dp)
                        .background(if (isSavingPdf) Color.Gray else GreenPrimary, RoundedCornerShape(8.dp))
                        .clickable(enabled = !isSavingPdf) { onSave() },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSavingPdf) {
                        OmniraLoadingLogo(size = 30.dp, mintColor = Color.White)
                    } else {
                        Text("Save Attendance & Generate PDF", style = MaterialTheme.typography.titleSmall, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun AttendanceItem(user: AttendanceUser, canEdit: Boolean, onStatusChange: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphic(elevation = 2.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(32.dp).background(Color.LightGray, CircleShape), contentAlignment = Alignment.Center) {
                Text(user.name.take(1).uppercase(), style = MaterialTheme.typography.titleSmall)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(user.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            
            if (canEdit) {
                Switch(
                    checked = user.isPresent,
                    onCheckedChange = onStatusChange,
                    colors = SwitchDefaults.colors(checkedThumbColor = GreenPrimary, checkedTrackColor = GreenPrimary.copy(alpha = 0.5f))
                )
            } else {
                Text(
                    text = if (user.isPresent) "Present" else "Absent",
                    color = if (user.isPresent) GreenPrimary else Color.Red,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
fun StatCard(title: String, count: String, bgColor: Color, textColor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .neumorphic(elevation = 3.dp)
            .background(bgColor, RoundedCornerShape(12.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.7f))
            Text(count, style = MaterialTheme.typography.titleLarge, color = textColor)
        }
    }
}

private fun saveAttendanceWithPdf(
    db: FirebaseFirestore,
    userId: String,
    classId: String,
    className: String,
    batch: String,
    date: String,
    students: List<AttendanceUser>,
    context: Context,
    onComplete: () -> Unit
) {
    val record = students.associate { it.id to it.isPresent }
    db.collection("classes").document(classId)
        .collection("attendance").document(date)
        .set(record)
        .addOnSuccessListener {
            try {
                val pdfDocument = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas
                val paint = Paint()

                paint.textSize = 20f
                paint.isFakeBoldText = true
                paint.color = android.graphics.Color.BLACK
                canvas.drawText("OMNIRA CLASSROOM - ATTENDANCE REPORT", 50f, 60f, paint)

                paint.textSize = 13f
                paint.isFakeBoldText = false
                canvas.drawText("Class Name: $className", 50f, 100f, paint)
                canvas.drawText("Batch: $batch", 50f, 120f, paint)
                canvas.drawText("Date: $date", 50f, 140f, paint)

                val presentList = students.filter { it.isPresent }
                val absentList = students.filter { !it.isPresent }
                canvas.drawText("Summary: ${presentList.size} Present, ${absentList.size} Absent (Total: ${students.size})", 50f, 170f, paint)

                paint.isFakeBoldText = true
                canvas.drawText("Attended / Present Students", 50f, 210f, paint)
                canvas.drawText("Absent Students", 320f, 210f, paint)

                paint.isFakeBoldText = false
                var presentY = 235f
                if (presentList.isEmpty()) {
                    canvas.drawText("No students present", 50f, presentY, paint)
                } else {
                    presentList.forEach { s ->
                        if (presentY < 800f) {
                            canvas.drawText("✓ ${s.name}", 50f, presentY, paint)
                            presentY += 20f
                        }
                    }
                }

                var absentY = 235f
                if (absentList.isEmpty()) {
                    canvas.drawText("No students absent", 320f, absentY, paint)
                } else {
                    absentList.forEach { s ->
                        if (absentY < 800f) {
                            canvas.drawText("✗ ${s.name}", 320f, absentY, paint)
                            absentY += 20f
                        }
                    }
                }

                pdfDocument.finishPage(page)

                val cacheFile = File(context.cacheDir, "Attendance_${classId}_${date}.pdf")
                val fos = FileOutputStream(cacheFile)
                pdfDocument.writeTo(fos)
                fos.close()
                pdfDocument.close()

                uploadToCloudinary(context, Uri.fromFile(cacheFile), "users/$userId/attendance_sheets/$classId") { fileUrl ->
                    if (fileUrl != null) {
                        val sheetData = AttendanceSheetRecord(
                            userId = userId,
                            classId = classId,
                            className = className,
                            batch = batch,
                            date = date,
                            fileUrl = fileUrl,
                            timestamp = System.currentTimeMillis()
                        )
                        db.collection("classes").document(classId).collection("attendance_sheets")
                            .add(sheetData)
                            .addOnSuccessListener {
                                Toast.makeText(context, "Attendance saved and PDF sheet generated!", Toast.LENGTH_LONG).show()
                                onComplete()
                            }
                            .addOnFailureListener {
                                Toast.makeText(context, "Attendance saved, but failed to save sheet info", Toast.LENGTH_SHORT).show()
                                onComplete()
                            }
                    } else {
                        Toast.makeText(context, "Attendance saved locally, PDF upload failed", Toast.LENGTH_SHORT).show()
                        onComplete()
                    }
                }

            } catch (e: Exception) {
                Toast.makeText(context, "Error creating PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                onComplete()
            }
        }
        .addOnFailureListener {
            Toast.makeText(context, "Failed to save attendance record", Toast.LENGTH_SHORT).show()
            onComplete()
        }
}

@Preview(showBackground = true)
@Composable
fun AttendanceScreenPreview() {
    OMNIRATheme {
        AttendanceScreen()
    }
}
