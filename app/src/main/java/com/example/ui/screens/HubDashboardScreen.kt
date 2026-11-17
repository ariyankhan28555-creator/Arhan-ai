package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.EditLocationAlt
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.firebase.UiState
import com.example.ui.theme.AmberSignal
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.LuminousTeal
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianVoid
import com.example.viewmodel.NovaViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.util.Date
import java.util.Locale

@Composable
fun HubDashboardScreen(
    viewModel: NovaViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val snapshot by viewModel.deviceSnapshot.collectAsStateWithLifecycle()
    val weatherState by viewModel.weatherState.collectAsStateWithLifecycle()
    val newsState by viewModel.newsState.collectAsStateWithLifecycle()
    val calendarEvents by viewModel.calendarEvents.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()

    var showCityDialog by remember { mutableStateOf(false) }
    var cityDraft by remember(settings.preferredCity) { mutableStateOf(settings.preferredCity) }

    val calendarPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.refreshCalendarAndApps()
    }

    val locationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.refreshWeather()
    }

    val hasCalendarPerm = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_CALENDAR
    ) == PackageManager.PERMISSION_GRANTED

    val greeting = remember {
        val hour = LocalTime.now().hour
        val firstName = Firebase.auth.currentUser?.displayName?.substringBefore(" ") ?: "Friend"
        when {
            hour < 12 -> "Good Morning, $firstName • सुप्रभात"
            hour < 17 -> "Good Afternoon, $firstName • नमस्ते"
            else -> "Good Evening, $firstName • शुभ संध्या"
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Daily Greeting Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, Brush.linearGradient(listOf(ElectricCyan, NeonViolet))),
                shape = RoundedCornerShape(22.dp)
            ) {
                Surface(
                    color = MidnightSurface.copy(alpha = 0.94f),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = greeting,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = SimpleDateFormat("EEEE, dd MMMM yyyy • hh:mm a", Locale.ENGLISH).format(Date()),
                            style = MaterialTheme.typography.labelMedium,
                            color = ElectricCyan
                        )
                    }
                }
            }
        }

        // Device Control Deck
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                border = BorderStroke(1.dp, GlassBorder),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "DEVICE CONTROL DECK",
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricCyan
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { viewModel.deviceController.setTorch(!snapshot.torchOn) },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (snapshot.torchOn) AmberSignal else Color(0xFF1B274D),
                                contentColor = if (snapshot.torchOn) ObsidianVoid else Color.White
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("torch_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (snapshot.torchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                                contentDescription = "Toggle flashlight",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (snapshot.torchOn) "Torch ON" else "Torch OFF")
                        }

                        FilledTonalButton(
                            onClick = { viewModel.deviceController.openCamera() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_camera_button")
                        ) {
                            Icon(Icons.Rounded.CameraAlt, contentDescription = "Open camera", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Camera")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.deviceController.openSettings("main") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_settings_button")
                        ) {
                            Icon(Icons.Rounded.Settings, contentDescription = "Open settings", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Settings")
                        }

                        OutlinedButton(
                            onClick = { viewModel.deviceController.openHomeScreen() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("home_screen_button")
                        ) {
                            Icon(Icons.Rounded.Home, contentDescription = "Go to home screen", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Home")
                        }
                    }

                    // Volume Slider
                    var volSlider by remember(snapshot.mediaVolume) { mutableFloatStateOf(snapshot.mediaVolume.toFloat()) }
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Rounded.VolumeUp, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                                Text("Media Volume", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                            }
                            Text("${volSlider.toInt()} / ${snapshot.maxMediaVolume}", style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
                        }
                        Slider(
                            value = volSlider,
                            onValueChange = {
                                volSlider = it
                                viewModel.deviceController.setMediaVolume(it.toInt())
                            },
                            valueRange = 0f..snapshot.maxMediaVolume.toFloat(),
                            colors = SliderDefaults.colors(thumbColor = ElectricCyan, activeTrackColor = ElectricCyan)
                        )
                    }

                    // Brightness Slider
                    var brightSlider by remember(snapshot.brightnessPercent) { mutableFloatStateOf(snapshot.brightnessPercent.toFloat()) }
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Rounded.Brightness6, contentDescription = null, tint = NeonViolet, modifier = Modifier.size(18.dp))
                                Text("Screen Brightness", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                            }
                            Text("${brightSlider.toInt()}%", style = MaterialTheme.typography.labelMedium, color = NeonViolet)
                        }
                        Slider(
                            value = brightSlider,
                            onValueChange = {
                                brightSlider = it
                                viewModel.deviceController.setBrightness(it.toInt(), activity)
                            },
                            valueRange = 5f..100f,
                            colors = SliderDefaults.colors(thumbColor = NeonViolet, activeTrackColor = NeonViolet)
                        )
                        if (!snapshot.canWriteSystemSettings) {
                            TextButton(
                                onClick = { viewModel.deviceController.openWriteSettingsPermissionScreen() }
                            ) {
                                Text("Allow System Brightness Permission", style = MaterialTheme.typography.labelSmall, color = AmberSignal)
                            }
                        }
                    }
                }
            }
        }

        // Real Weather Widget (Open-Meteo)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                border = BorderStroke(1.dp, GlassBorder),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.WbSunny, contentDescription = "Weather", tint = AmberSignal, modifier = Modifier.size(20.dp))
                            Text("LIVE WEATHER", style = MaterialTheme.typography.labelMedium, color = AmberSignal)
                        }
                        Row {
                            IconButton(onClick = { showCityDialog = true }) {
                                Icon(Icons.Rounded.EditLocationAlt, contentDescription = "Change city", tint = ElectricCyan)
                            }
                            IconButton(onClick = {
                                val hasLoc = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.ACCESS_COARSE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasLoc) viewModel.refreshWeather()
                                else locationPermLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            }) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh weather", tint = ElectricCyan)
                            }
                        }
                    }

                    when (val wState = weatherState) {
                        is UiState.Loading -> {
                            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = ElectricCyan, modifier = Modifier.size(26.dp))
                            }
                        }
                        is UiState.Error -> {
                            Text(wState.message, color = CoralAlert, style = MaterialTheme.typography.bodySmall)
                        }
                        is UiState.Success -> {
                            val w = wState.data
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(w.city, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                                    Text("${w.conditionEn} • ${w.conditionHi}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        "Feels ${w.apparentTempC}°C • Humidity ${w.humidityPercent}% • Wind ${w.windKmh} km/h",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = LuminousTeal
                                    )
                                }
                                Text(
                                    text = "${w.temperatureC}°C",
                                    style = MaterialTheme.typography.displaySmall,
                                    color = ElectricCyan,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Installed App Quick Launcher
        if (installedApps.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                    border = BorderStroke(1.dp, GlassBorder),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.Apps, contentDescription = null, tint = LuminousTeal, modifier = Modifier.size(18.dp))
                            Text("QUICK LAUNCH APPS", style = MaterialTheme.typography.labelMedium, color = LuminousTeal)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            installedApps.take(12).forEach { app ->
                                AssistChip(
                                    onClick = { viewModel.deviceController.launchAppByName(app.label) },
                                    label = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Calendar Integration
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                border = BorderStroke(1.dp, GlassBorder),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = NeonViolet, modifier = Modifier.size(18.dp))
                            Text("CALENDAR SCHEDULE", style = MaterialTheme.typography.labelMedium, color = NeonViolet)
                        }
                        IconButton(onClick = { viewModel.deviceController.insertCalendarEvent("") }) {
                            Icon(Icons.Rounded.Add, contentDescription = "Add calendar event", tint = ElectricCyan)
                        }
                    }

                    if (!hasCalendarPerm) {
                        OutlinedButton(
                            onClick = { calendarPermLauncher.launch(Manifest.permission.READ_CALENDAR) }
                        ) {
                            Text("Grant Calendar Access to View Events")
                        }
                    } else if (calendarEvents.isEmpty()) {
                        Text(
                            text = "No upcoming events in the next 7 days.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val fmt = remember { SimpleDateFormat("EEE, MMM d • hh:mm a", Locale.ENGLISH) }
                        calendarEvents.forEach { ev ->
                            Surface(
                                color = MidnightSurface,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(ev.title, style = MaterialTheme.typography.titleSmall, color = Color.White)
                                    Text(fmt.format(Date(ev.startMillis)), style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Real Live News Headlines
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.Newspaper, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                    Text("LIVE NEWS HEADLINES", style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
                }
                IconButton(onClick = { viewModel.refreshNews() }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh news", tint = ElectricCyan)
                }
            }
        }

        when (val nState = newsState) {
            is UiState.Loading -> {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ElectricCyan, modifier = Modifier.size(24.dp))
                    }
                }
            }
            is UiState.Error -> {
                item {
                    Text(nState.message, color = CoralAlert, style = MaterialTheme.typography.bodySmall)
                }
            }
            is UiState.Success -> {
                items(nState.data) { article ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                        border = BorderStroke(1.dp, GlassBorder),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (article.link.isNotBlank()) {
                                    viewModel.deviceController.openUrl(article.link)
                                }
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = article.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${article.source} • ${article.pubDate}",
                                style = MaterialTheme.typography.labelSmall,
                                color = LuminousTeal
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCityDialog) {
        AlertDialog(
            onDismissRequest = { showCityDialog = false },
            title = { Text("Set Weather City") },
            text = {
                OutlinedTextField(
                    value = cityDraft,
                    onValueChange = { cityDraft = it },
                    label = { Text("City name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (cityDraft.isNotBlank()) {
                        viewModel.setPreferredCity(cityDraft)
                    }
                    showCityDialog = false
                }) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCityDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
