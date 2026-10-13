package com.omnira.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnira.ui.theme.GreenPrimary
import com.omnira.ui.theme.OMNIRATheme
import com.omnira.ui.theme.getNeumorphicTextStyle
import com.omnira.ui.theme.neumorphic

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassesSelectionScreen(
    onBackClick: () -> Unit = {},
    onCreateClassClick: () -> Unit = {},
    onJoinClassClick: () -> Unit = {},
    onEnrolledClassClick: () -> Unit = {}
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (!isLandscape) {
            // Top Green Curve Design
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(
                        color = GreenPrimary,
                        shape = RoundedCornerShape(bottomStart = 80.dp)
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(if (isLandscape) 16.dp else 48.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack, 
                        contentDescription = "Back", 
                        tint = if (isLandscape) GreenPrimary else Color.White
                    )
                }
                if (!isLandscape) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "Omnira Home",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.weight(1.2f))
                }
            }

            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Side: Branding
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Classroom Management",
                            style = getNeumorphicTextStyle(MaterialTheme.typography.displayMedium, GreenPrimary),
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.width(32.dp))

                    // Right Side: Options
                    Column(modifier = Modifier.weight(1f)) {
                        SelectionOptions(
                            onCreateClassClassClick = onCreateClassClick,
                            onJoinClassClick = onJoinClassClick,
                            onEnrolledClassClick = onEnrolledClassClick
                        )
                    }
                }
            } else {
                // Portrait Layout
                Spacer(modifier = Modifier.height(80.dp))
                
                SelectionOptions(
                    onCreateClassClassClick = onCreateClassClick,
                    onJoinClassClick = onJoinClassClick,
                    onEnrolledClassClick = onEnrolledClassClick
                )
            }
            
            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}

@Composable
fun SelectionOptions(
    onCreateClassClassClick: () -> Unit,
    onJoinClassClick: () -> Unit,
    onEnrolledClassClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        NeumorphicSelectionButton(text = "Create Class", onClick = onCreateClassClassClick)
        NeumorphicSelectionButton(text = "Join Class", onClick = onJoinClassClick)
        NeumorphicSelectionButton(text = "Enrolled Classes", onClick = onEnrolledClassClick)
    }
}

@Composable
fun NeumorphicSelectionButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .neumorphic(elevation = 6.dp, shape = RoundedCornerShape(28.dp))
            .background(GreenPrimary, RoundedCornerShape(28.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ClassesSelectionScreenPreview() {
    OMNIRATheme {
        ClassesSelectionScreen()
    }
}
