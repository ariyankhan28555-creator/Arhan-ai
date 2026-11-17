package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.firebase.UiState
import com.example.model.AssistantLanguage
import com.example.model.VoiceGender
import com.example.ui.auth.signOut
import com.example.ui.theme.AmberSignal
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.EmeraldOk
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianVoid
import com.example.viewmodel.NovaViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@Composable
fun SettingsPrivacyScreen(
    viewModel: NovaViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val prefsState by viewModel.preferencesState.collectAsStateWithLifecycle()
    var permRefreshTick by remember { mutableIntStateOf(0) }

    val singlePermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        permRefreshTick++
    }

    val hasMic = remember(permRefreshTick) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    val hasLoc = remember(permRefreshTick) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    val hasCalendar = remember(permRefreshTick) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }
    val hasContacts = remember(permRefreshTick) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }
    val hasNotifs = remember(permRefreshTick) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Voice & Language Controls
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                border = BorderStroke(1.dp, GlassBorder),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.RecordVoiceOver, contentDescription = null, tint = ElectricCyan)
                        Text("VOICE & LANGUAGE", style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
                    }

                    Text("Assistant Voice Persona", style = MaterialTheme.typography.titleSmall, color = Color.White)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VoiceGender.entries.forEach { gender ->
                            FilterChip(
                                selected = settings.voiceGender == gender,
                                onClick = { viewModel.setVoiceGender(gender) },
                                label = { Text(gender.label) },
                                modifier = Modifier.testTag("voice_gender_${gender.name}")
                            )
                        }
                    }

                    Text("Speech Rate (${String.format("%.1fx", settings.speechRate)})", style = MaterialTheme.typography.titleSmall, color = Color.White)
                    Slider(
                        value = settings.speechRate,
                        onValueChange = { viewModel.setSpeechRate(it) },
                        valueRange = 0.7f..1.5f,
                        colors = SliderDefaults.colors(thumbColor = ElectricCyan, activeTrackColor = ElectricCyan)
                    )

                    OutlinedButton(onClick = { viewModel.testAssistantVoice() }) {
                        Icon(Icons.Rounded.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Test Voice")
                    }

                    Text("Recognition Language", style = MaterialTheme.typography.titleSmall, color = Color.White)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistantLanguage.entries.forEach { lang ->
                            FilterChip(
                                selected = settings.recognitionLanguage == lang,
                                onClick = { viewModel.setRecognitionLanguage(lang) },
                                label = { Text(lang.label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Wake Word (\"Hey NOVA\")", style = MaterialTheme.typography.titleSmall, color = Color.White)
                            Text(
                                "Explicitly shows live mic badge while listening. Never records in secret.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.wakeWordEnabled,
                            onCheckedChange = { wantEnable ->
                                if (wantEnable && !hasMic) {
                                    singlePermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    viewModel.setWakeWordEnabled(wantEnable)
                                }
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Continuous Conversation Mode", style = MaterialTheme.typography.titleSmall, color = Color.White)
                            Text(
                                "Automatically listen again after NOVA finishes speaking.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.continuousModeEnabled,
                            onCheckedChange = { viewModel.setContinuousModeEnabled(it) }
                        )
                    }
                }
            }
        }

        // Security & Permissions Control Center
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                border = BorderStroke(1.dp, GlassBorder),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Security, contentDescription = null, tint = NeonViolet)
                        Text("PRIVACY & PERMISSION CONTROLS", style = MaterialTheme.typography.labelMedium, color = NeonViolet)
                    }

                    PermissionControlRow(
                        title = "Microphone (Voice Commands)",
                        granted = hasMic,
                        onRequest = { singlePermLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        PermissionControlRow(
                            title = "Notifications (Smart Reminders)",
                            granted = hasNotifs,
                            onRequest = { singlePermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                        )
                    }
                    PermissionControlRow(
                        title = "Coarse Location (Local Weather)",
                        granted = hasLoc,
                        onRequest = { singlePermLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
                    )
                    PermissionControlRow(
                        title = "Calendar (Schedule Integration)",
                        granted = hasCalendar,
                        onRequest = { singlePermLauncher.launch(Manifest.permission.READ_CALENDAR) }
                    )
                    PermissionControlRow(
                        title = "Contacts (Voice Call/SMS Lookup)",
                        granted = hasContacts,
                        onRequest = { singlePermLauncher.launch(Manifest.permission.READ_CONTACTS) }
                    )

                    TextButton(
                        onClick = {
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}")
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        }
                    ) {
                        Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Manage All App Permissions in Android Settings")
                    }
                }
            }
        }

        // User-Approved Preferences Memory
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Psychology, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                Text("USER-APPROVED MEMORIES", style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
            }
        }

        when (val pState = prefsState) {
            is UiState.Success -> {
                if (pState.data.isEmpty()) {
                    item {
                        Text(
                            text = "No personal preferences saved yet. Ask NOVA \"Remember that I prefer...\" and approve it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(pState.data, key = { it.id }) { pref ->
                        Surface(
                            color = ElevatedCard,
                            border = BorderStroke(1.dp, GlassBorder),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(pref.prefKey, style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
                                    Text(pref.prefValue, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                }
                                IconButton(onClick = { viewModel.deletePreference(pref.id) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Forget preference", tint = CoralAlert)
                                }
                            }
                        }
                    }
                }
            }
            is UiState.Error -> {
                item { Text(pState.message, color = CoralAlert, style = MaterialTheme.typography.bodySmall) }
            }
            UiState.Loading -> Unit
        }

        // Account & Sign Out
        item {
            val user = Firebase.auth.currentUser
            Card(
                colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                border = BorderStroke(1.dp, GlassBorder),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("ACCOUNT", style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
                    Text(
                        text = user?.displayName ?: "Signed In User",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!user?.email.isNullOrBlank()) {
                        Text(
                            text = user?.email.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            viewModel.voiceController.stopListening()
                            viewModel.voiceController.stopSpeaking()
                            signOut(credentialManager, scope)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sign Out")
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionControlRow(
    title: String,
    granted: Boolean,
    onRequest: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )
        if (granted) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = "Granted", tint = EmeraldOk, modifier = Modifier.size(16.dp))
                Text("Granted", style = MaterialTheme.typography.labelSmall, color = EmeraldOk)
            }
        } else {
            TextButton(onClick = onRequest) {
                Text("Grant", color = AmberSignal, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
