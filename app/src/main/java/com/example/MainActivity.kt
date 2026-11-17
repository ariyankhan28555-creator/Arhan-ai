package com.example

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Adjust
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Adjust
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ai.NovaAiEngine
import com.example.data.FirestoreRepository
import com.example.data.SettingsDataStore
import com.example.data.WeatherAndNewsRepository
import com.example.device.DeviceController
import com.example.firebase.authStateFlow
import com.example.firebase.configureAppCheck
import com.example.reminders.ReminderScheduler
import com.example.ui.auth.AuthScreen
import com.example.ui.screens.AssistantOrbStageScreen
import com.example.ui.screens.HubDashboardScreen
import com.example.ui.screens.NovaCoreScreen
import com.example.ui.screens.SettingsPrivacyScreen
import com.example.ui.screens.WorkspaceScreen
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.NovaTheme
import com.example.ui.theme.ObsidianVoid
import com.example.viewmodel.NovaViewModel
import com.example.voice.NovaVoiceController
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

const val ROUTE_NOVA_CORE = "nova_core"
const val ROUTE_ORB_STAGE = "nova_orb_stage"
const val ROUTE_HUB = "nova_hub"
const val ROUTE_WORKSPACE = "nova_workspace"
const val ROUTE_SETTINGS = "nova_settings"

data class NavDestinationItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val navItems = listOf(
    NavDestinationItem(ROUTE_NOVA_CORE, "NOVA", Icons.Rounded.AutoAwesome, Icons.Outlined.AutoAwesome),
    NavDestinationItem(ROUTE_ORB_STAGE, "Stage", Icons.Rounded.Adjust, Icons.Outlined.Adjust),
    NavDestinationItem(ROUTE_HUB, "Hub", Icons.Rounded.Dashboard, Icons.Outlined.Dashboard),
    NavDestinationItem(ROUTE_WORKSPACE, "Tasks", Icons.Rounded.CheckBox, Icons.Outlined.CheckBox),
    NavDestinationItem(ROUTE_SETTINGS, "Privacy", Icons.Rounded.Settings, Icons.Outlined.Settings)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            NovaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ObsidianVoid
                ) {
                    NovaAppNavigation()
                }
            }
        }
    }
}

@Composable
fun NovaAppNavigation() {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = { })
    } else {
        key(user.uid) {
            AuthenticatedNovaShell(currentUserId = user.uid)
        }
    }
}

@Composable
fun AuthenticatedNovaShell(currentUserId: String) {
    val activity = LocalContext.current as? Activity

    val viewModel: NovaViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val firestoreRepo = FirestoreRepository(db)
                val settingsStore = SettingsDataStore(app)
                val weatherNewsRepo = WeatherAndNewsRepository()
                val aiEngine = NovaAiEngine()
                val deviceController = DeviceController(app)
                val reminderScheduler = ReminderScheduler(app)

                lateinit var vmRef: NovaViewModel
                val voiceController = NovaVoiceController(
                    context = app,
                    onFinalSpeech = { transcript, isWake ->
                        vmRef.handleVoiceResult(transcript, isWake)
                    },
                    onSpeechError = { err ->
                        vmRef.setStatusBanner(err)
                    }
                )
                vmRef = NovaViewModel(
                    currentUserId = currentUserId,
                    firestoreRepo = firestoreRepo,
                    settingsStore = settingsStore,
                    weatherNewsRepo = weatherNewsRepo,
                    aiEngine = aiEngine,
                    deviceController = deviceController,
                    reminderScheduler = reminderScheduler,
                    voiceController = voiceController
                )
                vmRef
            }
        }
    )

    androidx.compose.runtime.LaunchedEffect(viewModel, activity) {
        viewModel.windowBrightnessEvent.collect { pct ->
            viewModel.deviceController.setBrightness(pct, activity)
        }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val navigateTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = ObsidianVoid,
            bottomBar = {
                if (!useRail) {
                    NavigationBar(containerColor = MidnightSurface) {
                        navItems.forEach { item ->
                            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag("nav_${item.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(containerColor = MidnightSurface) {
                        navItems.forEach { item ->
                            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag("rail_${item.route}")
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_NOVA_CORE,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(ROUTE_NOVA_CORE) { NovaCoreScreen(viewModel = viewModel) }
                    composable(ROUTE_ORB_STAGE) { AssistantOrbStageScreen() }
                    composable(ROUTE_HUB) { HubDashboardScreen(viewModel = viewModel) }
                    composable(ROUTE_WORKSPACE) { WorkspaceScreen(viewModel = viewModel) }
                    composable(ROUTE_SETTINGS) { SettingsPrivacyScreen(viewModel = viewModel) }
                }
            }
        }
    }
}
