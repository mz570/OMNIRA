package com.omnira

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.omnira.R
import com.omnira.ui.screens.*
import com.omnira.ui.theme.GreenPrimary
import com.omnira.ui.theme.OMNIRATheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable verbose Firestore logging
        FirebaseFirestore.setLoggingEnabled(true)
        Log.d("OMNIRA_INIT", "App Started")

        enableEdgeToEdge()
        setContent {
            OMNIRATheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val auth = FirebaseAuth.getInstance()
    val context = androidx.compose.ui.platform.LocalContext.current
    var hasInternet by remember { mutableStateOf(context.hasInternetConnection()) }

    DisposableEffect(context) {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                hasInternet = context.hasInternetConnection()
            }

            override fun onLost(network: Network) {
                hasInternet = context.hasInternetConnection()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
        }

        connectivityManager.registerDefaultNetworkCallback(callback)
        hasInternet = context.hasInternetConnection()

        onDispose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }
    
    // State to handle initial auth check
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val currentUser = auth.currentUser
        Log.d("OMNIRA_AUTH", "Current User UID: ${currentUser?.uid}")
        
        // On a fresh install or if not logged in, currentUser will be null
        startDestination = if (currentUser != null) "dashboard" else "login"
    }

    // Show a loading screen while determining the start destination
    if (startDestination == null) {
        Box(modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator(
                color = GreenPrimary,
                modifier = Modifier.align(Alignment.Center)
            )
            if (!hasInternet) OfflineBanner()
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
      NavHost(navController = navController, startDestination = startDestination!!) {
        composable("login") { 
            LoginScreen(
                onLoginSuccess = { 
                    navController.navigate("dashboard") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onRegisterClick = { navController.navigate("register") }
            ) 
        }
        composable("register") {
            RegisterScreen(
                onBackClick = { navController.popBackStack() },
                onLoginClick = { navController.navigate("login") },
                onRegisterSuccess = {
                    navController.navigate("dashboard") {
                        popUpTo("register") { inclusive = true }
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }
        composable("dashboard") { 
            DashboardScreen(
                onQuickAccessClick = { item ->
                    when (item) {
                        "Classes" -> navController.navigate("classes_selection")
                        "Attendance" -> navController.navigate("global_attendance")
                        "Notification" -> navController.navigate("notifications")
                        "Assignments" -> navController.navigate("global_assignments")
                        "Materials" -> navController.navigate("global_materials")
                    }
                },
                onProfileClick = { navController.navigate("profile") },
                onCalendarClick = { navController.navigate("routine") },
                onInboxClick = { navController.navigate("inbox") }
            )
        }
        composable("inbox") {
            InboxScreen(
                onBackClick = { navController.popBackStack() },
                onConversationClick = { userId -> navController.navigate("direct_message/$userId") },
                onHomeClick = { navController.navigate("dashboard") },
                onCalendarClick = { navController.navigate("routine") },
                onProfileClick = { navController.navigate("profile") }
            )
        }
        composable(
            route = "direct_message/{recipientId}",
            arguments = listOf(navArgument("recipientId") { type = NavType.StringType })
        ) { entry ->
            DirectMessageScreen(
                recipientId = entry.arguments?.getString("recipientId").orEmpty(),
                onBackClick = { navController.popBackStack() }
            )
        }
        composable("notifications") {
            NotificationScreen(
                onBackClick = { navController.popBackStack() },
                onNotificationClick = { classId, type ->
                    if (classId.isNotEmpty() && classId != "General") {
                        if (type == "CHAT_REPORT") {
                            navController.navigate("chat/$classId")
                        } else {
                            val tabIndex = when (type) {
                                "POST" -> 0
                                "MATERIAL" -> 1
                                "ASSIGNMENT" -> 2
                                "ROUTINE" -> 3
                                "JOIN_REQUEST" -> 5
                                else -> 0
                            }
                            navController.navigate("classroom_home/$classId?initialTab=$tabIndex")
                        }
                    }
                }
            )
        }
        composable("global_assignments") {
            GlobalAssignmentsScreen(onBackClick = { navController.popBackStack() })
        }
        composable("global_materials") {
            GlobalMaterialsScreen(onBackClick = { navController.popBackStack() })
        }
        composable("global_attendance") {
            GlobalAttendanceScreen(onBackClick = { navController.popBackStack() })
        }
        composable("classes_selection") {
            ClassesSelectionScreen(
                onBackClick = { navController.popBackStack() },
                onCreateClassClick = { navController.navigate("create_class") },
                onJoinClassClick = { navController.navigate("join_class") },
                onEnrolledClassClick = { navController.navigate("joined_classes") }
            )
        }
        composable("joined_classes") {
            JoinedClassesScreen(
                onBackClick = { navController.popBackStack() },
                onJoinAnotherClick = { navController.navigate("join_class") },
                onClassClick = { joinedClass -> navController.navigate("classroom_home/${joinedClass.id}") },
                onProfileClick = { navController.navigate("profile") }
            )
        }
        composable("join_class") {
            JoinClassScreen(
                onBackClick = { navController.popBackStack() },
                onJoinRequestSent = { navController.popBackStack() }
            )
        }
        composable("create_class") {
            CreateClassScreen(
                onBackClick = { navController.popBackStack() },
                onCreateSuccess = { code -> navController.navigate("classroom_home/$code") }
            )
        }
        composable(
            route = "classroom_home/{classCode}?initialTab={initialTab}",
            arguments = listOf(
                navArgument("classCode") { type = NavType.StringType },
                navArgument("initialTab") { type = NavType.IntType; defaultValue = 0 }
            )
        ) { backStackEntry ->
            val classCode = backStackEntry.arguments?.getString("classCode") ?: ""
            val initialTab = backStackEntry.arguments?.getInt("initialTab") ?: 0
            ClassroomHomeScreen(
                classCode = classCode,
                initialTab = initialTab,
                onBackClick = { navController.popBackStack() },
                onProfileClick = { navController.navigate("profile") },
                onCalendarClick = { navController.navigate("routine") },
                onChatClick = { code -> navController.navigate("chat/$code") },
                onDirectMessageClick = { userId -> navController.navigate("direct_message/$userId") },
                onInboxClick = { navController.navigate("inbox") }
            )
        }
        composable(
            route = "chat/{classCode}",
            arguments = listOf(navArgument("classCode") { type = NavType.StringType })
        ) { backStackEntry ->
            val classCode = backStackEntry.arguments?.getString("classCode") ?: ""
            ChatScreen(classCode = classCode, onBackClick = { navController.popBackStack() })
        }
        composable("attendance") {
            AttendanceScreen(onBackClick = { navController.popBackStack() }, onProfileClick = { navController.navigate("profile") })
        }
        composable("routine") {
            ClassRoutineScreen(onBackClick = { navController.popBackStack() }, onProfileClick = { navController.navigate("profile") })
        }
        composable("profile") {
            ProfileScreen(
                onBackClick = { navController.popBackStack() },
                onLogoutClick = {
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
      }

      if (!hasInternet) OfflineBanner()
    }
}

@Composable
private fun BoxScope.OfflineBanner() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .align(Alignment.TopCenter),
        color = MaterialTheme.colorScheme.error
    ) {
        Text(
            text = stringResource(R.string.no_internet_connection),
            color = MaterialTheme.colorScheme.onError,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

private fun Context.hasInternetConnection(): Boolean {
    val connectivityManager =
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
