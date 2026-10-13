package com.omnira.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.omnira.ui.theme.GreenPrimary
import com.omnira.ui.theme.OMNIRATheme
import com.omnira.ui.theme.YellowSecondary
import com.omnira.ui.theme.neumorphic

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinedClassesScreen(
    onBackClick: () -> Unit = {},
    onJoinAnotherClick: () -> Unit = {},
    onClassClick: (JoinedClass) -> Unit = {},
    onProfileClick: () -> Unit = {}
) {
    var enrolledClasses by remember { mutableStateOf<List<JoinedClass>>(emptyList()) }
    var isLoading by rememberSaveable { mutableStateOf(true) }

    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            db.collection("classes")
                .whereArrayContains("members", currentUser.uid)
                .addSnapshotListener { snapshot, _ ->
                    isLoading = false
                    if (snapshot != null) {
                        val classes = snapshot.documents.map { doc ->
                            JoinedClass(
                                id = doc.id,
                                name = doc.getString("name") ?: "Unknown Class",
                                imageUrl = doc.getString("imageUrl")
                            )
                        }
                        enrolledClasses = classes
                    }
                }
        } else {
            isLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Green Design
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isLandscape) 80.dp else 140.dp)
                .background(
                    color = GreenPrimary,
                    shape = RoundedCornerShape(bottomStart = if (isLandscape) 40.dp else 80.dp)
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(if (isLandscape) 16.dp else 48.dp))

            Row(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Enrolled Classes",
                    style = if (isLandscape) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Spacer(modifier = Modifier.weight(1.2f))
            }

            Spacer(modifier = Modifier.height(if (isLandscape) 24.dp else 40.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GreenPrimary)
                }
            } else if (enrolledClasses.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "You haven't enrolled in any classes yet.",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Box(
                        modifier = Modifier
                            .height(50.dp)
                            .widthIn(min = 150.dp)
                            .neumorphic(elevation = 4.dp, shape = RoundedCornerShape(8.dp))
                            .background(YellowSecondary, RoundedCornerShape(8.dp))
                            .clickable { onJoinAnotherClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Join a Class", 
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.Black, 
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                if (isLandscape) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        contentPadding = PaddingValues(bottom = 32.dp, top = 8.dp)
                    ) {
                        items(enrolledClasses) { item ->
                            ClassCard(item, onClassClick)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        contentPadding = PaddingValues(bottom = 32.dp, top = 8.dp)
                    ) {
                        items(enrolledClasses) { item ->
                            ClassCard(item, onClassClick)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ClassCard(item: JoinedClass, onClassClick: (JoinedClass) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphic(elevation = 4.dp, shape = RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .clickable { onClassClick(item) }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF1F3F4)),
                contentAlignment = Alignment.Center
            ) {
                if (!item.imageUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.Groups, 
                        contentDescription = null, 
                        tint = GreenPrimary.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Text(
                item.name, 
                style = MaterialTheme.typography.titleMedium,
                color = GreenPrimary,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = GreenPrimary)
        }
    }
}

data class JoinedClass(val id: String = "", val name: String, val imageUrl: String? = null)

@Preview(showBackground = true)
@Composable
fun JoinedClassesScreenPreview() {
    OMNIRATheme {
        JoinedClassesScreen()
    }
}
