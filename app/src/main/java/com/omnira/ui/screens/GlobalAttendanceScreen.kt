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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.omnira.data.AttendanceSheetRecord
import com.omnira.ui.components.OmniraLoadingLogo
import com.omnira.ui.theme.GreenPrimary
import com.omnira.ui.theme.getNeumorphicTextStyle
import com.omnira.ui.theme.neumorphic
import com.omnira.utils.downloadFile

private const val TAG = "OMNIRA_ATTENDANCE"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalAttendanceScreen(
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val currentUserId = auth.currentUser?.uid ?: ""

    var allSheets by remember { mutableStateOf<List<AttendanceSheetRecord>>(emptyList()) }
    var userClasses by remember { mutableStateOf<List<String>>(emptyList()) }
    
    var isLoading by remember { mutableStateOf(false) }
    var isInitialLoading by remember { mutableStateOf(true) }

    fun fetchAllAttendanceSheets() {
        if (userClasses.isEmpty()) {
            isInitialLoading = false
            return
        }

        isLoading = true
        val tasks = userClasses.map { classId ->
            db.collection("classes").document(classId)
                .collection("attendance_sheets")
                .limit(50) 
                .get()
        }

        Tasks.whenAllComplete(tasks).addOnCompleteListener { _ ->
            val mergedSheets = mutableListOf<AttendanceSheetRecord>()
            
            tasks.forEach { task ->
                if (task.isSuccessful) {
                    val sheets = task.result.documents.mapNotNull { doc ->
                        doc.toObject(AttendanceSheetRecord::class.java)?.copy(id = doc.id)
                    }
                    mergedSheets.addAll(sheets)
                }
            }

            allSheets = mergedSheets.sortedByDescending { it.timestamp }
            isLoading = false
            isInitialLoading = false
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to merge attendance sheets", e)
            isLoading = false
            isInitialLoading = false
            Toast.makeText(context, "Error loading reports", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotEmpty()) {
            val memberQuery = db.collection("classes").whereArrayContains("members", currentUserId).get()
            val creatorQuery = db.collection("classes").whereEqualTo("creatorId", currentUserId).get()

            Tasks.whenAllComplete(memberQuery, creatorQuery).addOnSuccessListener {
                val memberIds = memberQuery.result?.documents?.map { it.id } ?: emptyList()
                val creatorIds = creatorQuery.result?.documents?.map { it.id } ?: emptyList()
                
                userClasses = (memberIds + creatorIds).distinct()
                
                if (userClasses.isNotEmpty()) {
                    fetchAllAttendanceSheets()
                } else {
                    isInitialLoading = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Attendance Reports", style = MaterialTheme.typography.titleLarge, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GreenPrimary)
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (isInitialLoading) {
                OmniraLoadingLogo(modifier = Modifier.align(Alignment.Center), size = 80.dp)
            } else if (allSheets.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.History, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (userClasses.isEmpty()) "You don't have any classes yet." else "No attendance reports found.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(allSheets, key = { _, sheet -> sheet.id + sheet.timestamp }) { _, sheet ->
                        AttendanceGlobalCard(sheet) {
                            downloadFile(context, sheet.fileUrl, "Attendance_${sheet.className}_${sheet.date}.pdf")
                        }
                    }
                    
                    if (isLoading) {
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

@Composable
fun AttendanceGlobalCard(sheet: AttendanceSheetRecord, onDownloadClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphic(elevation = 4.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(28.dp))
            .clickable { onDownloadClick() }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(0xFFFFEBEE), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PictureAsPdf, null, tint = Color.Red, modifier = Modifier.size(24.dp))
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sheet.className,
                    style = getNeumorphicTextStyle(MaterialTheme.typography.titleMedium, Color.Black),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Batch: ${sheet.batch} • Date: ${sheet.date}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }
            
            IconButton(onClick = onDownloadClick) {
                Icon(Icons.Default.Download, "Download", tint = GreenPrimary)
            }
        }
    }
}
