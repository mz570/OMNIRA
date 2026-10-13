package com.omnira.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Download
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
import com.omnira.data.Post
import com.omnira.ui.components.OmniraLoadingLogo
import com.omnira.ui.theme.GreenPrimary
import com.omnira.ui.theme.neumorphic
import com.omnira.utils.downloadFile
import com.omnira.utils.formatDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalMaterialsScreen(
    onBackClick: () -> Unit = {}
) {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val context = LocalContext.current
    val currentUserId = auth.currentUser?.uid ?: ""

    var allMaterials by remember { mutableStateOf<List<Pair<Post, String>>>(emptyList()) } 
    var userClasses by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) } 
    var selectedClassId by remember { mutableStateOf("All") }
    
    var isLoading by remember { mutableStateOf(false) }
    var isInitialLoading by remember { mutableStateOf(true) }

    fun fetchMaterials() {
        if (userClasses.isEmpty()) {
            isInitialLoading = false
            return
        }

        isLoading = true
        
        val classIdsToFetch = if (selectedClassId == "All") {
            userClasses.filter { it.first != "All" }.map { it.first }
        } else {
            listOf(selectedClassId)
        }

        val tasks = classIdsToFetch.map { classId ->
            db.collection("classes").document(classId)
                .collection("materials")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(20)
                .get()
        }

        Tasks.whenAllComplete(tasks).addOnCompleteListener { _ ->
            val mergedResults = mutableListOf<Pair<Post, String>>()
            val classMap = userClasses.toMap()
            
            tasks.forEachIndexed { index, task ->
                if (task.isSuccessful) {
                    val classIdFromQuery = classIdsToFetch.getOrNull(index) ?: ""
                    val nameFromMap = classMap[classIdFromQuery]
                    
                    val posts = task.result.documents.mapNotNull { doc ->
                        val post = doc.toObject(Post::class.java)?.copy(id = doc.id)
                        if (post != null) {
                            // Robust class name determination
                            val displayClassName = when {
                                // Prefer name from current user's class list (most accurate/up-to-date)
                                nameFromMap != null && nameFromMap != "Unnamed Class" -> nameFromMap
                                // Fallback to name stored in post if valid
                                post.className.isNotBlank() && 
                                post.className != "Loading..." && 
                                post.className != "Unknown" && 
                                post.className != "Unknown Class" -> post.className
                                // Last resort generic names
                                nameFromMap != null -> nameFromMap
                                else -> "Class Material"
                            }
                            post to displayClassName
                        } else null
                    }
                    mergedResults.addAll(posts)
                }
            }

            allMaterials = mergedResults.sortedByDescending { it.first.timestamp }
            isLoading = false
            isInitialLoading = false
        }.addOnFailureListener { e ->
            Log.e("GlobalMaterials", "Error fetching materials", e)
            isLoading = false
            isInitialLoading = false
            Toast.makeText(context, "Failed to load materials", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotEmpty()) {
            val memberQuery = db.collection("classes").whereArrayContains("members", currentUserId).get()
            val creatorQuery = db.collection("classes").whereEqualTo("creatorId", currentUserId).get()

            Tasks.whenAllComplete(memberQuery, creatorQuery).addOnSuccessListener {
                val memberDocs = memberQuery.result?.documents ?: emptyList()
                val creatorDocs = creatorQuery.result?.documents ?: emptyList()
                
                val combinedClasses = (memberDocs + creatorDocs)
                    .distinctBy { it.id }
                    .map { it.id to (it.getString("name") ?: "Unnamed Class") }
                
                val classes = mutableListOf("All" to "All Classes")
                classes.addAll(combinedClasses)
                userClasses = classes
                
                if (combinedClasses.isEmpty()) {
                    isInitialLoading = false
                } else {
                    fetchMaterials()
                }
            }
        } else {
            isInitialLoading = false
        }
    }

    LaunchedEffect(selectedClassId) {
        if (userClasses.size > 1) { 
            fetchMaterials()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Study Materials", style = MaterialTheme.typography.titleLarge, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GreenPrimary)
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            
            if (userClasses.size > 2) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 0.dp
                ) {
                    Column(modifier = Modifier.padding(vertical = 12.dp)) {
                        Text(
                            text = "Filter by Class:",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.Gray,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(userClasses) { (id, name) ->
                                Box(
                                    modifier = Modifier
                                        .neumorphic(elevation = if (selectedClassId == id) 0.dp else 4.dp)
                                        .background(
                                            if (selectedClassId == id) GreenPrimary else MaterialTheme.colorScheme.surface,
                                            RoundedCornerShape(24.dp)
                                        )
                                        .clickable { selectedClassId = id }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = name,
                                        color = if (selectedClassId == id) Color.White else GreenPrimary,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (isInitialLoading) {
                    OmniraLoadingLogo(modifier = Modifier.align(Alignment.Center), size = 80.dp)
                } else if (allMaterials.isEmpty()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (userClasses.size <= 1) "You haven't joined any classes." else "No materials found.",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        itemsIndexed(allMaterials, key = { _, pair -> pair.first.id + pair.first.timestamp }) { _, (material, className) ->
                            MaterialGlobalCard(material, className) { url ->
                                downloadFile(context, url, material.fileName)
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
}

@Composable
fun MaterialGlobalCard(material: Post, className: String, onOpenClick: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphic(elevation = 6.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = GreenPrimary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = className,
                        style = MaterialTheme.typography.labelSmall,
                        color = GreenPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formatDate(material.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .neumorphic(elevation = 2.dp)
                    .background(Color(0xFFF1F3F4), RoundedCornerShape(8.dp))
                    .clickable { material.fileUrl?.let { onOpenClick(it) } }
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Download, null, tint = GreenPrimary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = material.fileName ?: "Material File",
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text("Download material", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                    IconButton(onClick = { material.fileUrl?.let { onOpenClick(it) } }) {
                        Icon(Icons.Default.Download, "Download", tint = GreenPrimary)
                    }
                }
            }
        }
    }
}
