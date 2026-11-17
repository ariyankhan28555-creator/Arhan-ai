package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.firebase.UiState
import com.example.model.AssistantLanguage
import com.example.model.AssistantPhase
import com.example.model.ChatMessageEntity
import com.example.model.MessageSender
import com.example.model.PendingConfirmation
import com.example.ui.components.NovaOrbVisualizer
import com.example.ui.theme.AmberSignal
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.EmeraldOk
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.LuminousTeal
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianVoid
import com.example.viewmodel.NovaViewModel

@Composable
fun NovaCoreScreen(
    viewModel: NovaViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val snapshot by viewModel.deviceSnapshot.collectAsStateWithLifecycle()
    val phase by viewModel.assistantPhase.collectAsStateWithLifecycle()
    val rmsLevel by viewModel.voiceController.rmsLevel.collectAsStateWithLifecycle()
    val partialTranscript by viewModel.voiceController.partialTranscript.collectAsStateWithLifecycle()
    val isWakeWordListening by viewModel.voiceController.isWakeWordListening.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    var textInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.voiceController.startActiveListening()
        } else {
            viewModel.setStatusBanner("Microphone permission denied. Enable it in Settings to use voice commands.")
        }
    }

    val wakeMicLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setWakeWordEnabled(true)
        } else {
            viewModel.setStatusBanner("Microphone permission is required for Hey NOVA wake mode.")
        }
    }

    val onMicTrigger: () -> Unit = {
        when (phase) {
            AssistantPhase.LISTENING -> viewModel.voiceController.stopListening()
            AssistantPhase.SPEAKING -> viewModel.voiceController.stopSpeaking()
            else -> {
                val hasMic = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (hasMic) {
                    viewModel.voiceController.startActiveListening()
                } else {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    }

    val messages = (messagesState as? UiState.Success)?.data.orEmpty()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 16.dp)
    ) {
        // Top Telemetry & Controls Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = ElevatedCard,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, GlassBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (snapshot.isCharging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd,
                            contentDescription = "Battery percentage",
                            tint = if (snapshot.batteryPercent <= 20) CoralAlert else EmeraldOk,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${snapshot.batteryPercent}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }

                if (!snapshot.isOnline) {
                    Surface(
                        color = Color(0xFF3F2807),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WifiOff,
                                contentDescription = "Offline fallback active",
                                tint = AmberSignal,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "OFFLINE",
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberSignal
                            )
                        }
                    }
                }

                if (isWakeWordListening) {
                    Surface(
                        color = Color(0xFF0A2E38),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = "Microphone listening for Hey NOVA",
                                tint = ElectricCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "MIC: HEY NOVA",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan
                            )
                        }
                    }
                }
            }

            if (messages.isNotEmpty()) {
                IconButton(
                    onClick = { viewModel.clearChatHistory() },
                    modifier = Modifier.testTag("clear_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteSweep,
                        contentDescription = "Clear conversation",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Language & Mode Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistantLanguage.entries.forEach { lang ->
                FilterChip(
                    selected = settings.recognitionLanguage == lang,
                    onClick = { viewModel.setRecognitionLanguage(lang) },
                    label = { Text(lang.label, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF0E2F4D),
                        selectedLabelColor = ElectricCyan
                    ),
                    modifier = Modifier.testTag("lang_chip_${lang.code}")
                )
            }
            FilterChip(
                selected = settings.wakeWordEnabled,
                onClick = {
                    if (settings.wakeWordEnabled) {
                        viewModel.setWakeWordEnabled(false)
                    } else {
                        val hasMic = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasMic) viewModel.setWakeWordEnabled(true)
                        else wakeMicLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                label = { Text("Hey NOVA", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = {
                    Icon(
                        imageVector = if (settings.wakeWordEnabled) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                },
                modifier = Modifier.testTag("wake_word_chip")
            )
            FilterChip(
                selected = settings.continuousModeEnabled,
                onClick = { viewModel.setContinuousModeEnabled(!settings.continuousModeEnabled) },
                label = { Text("Continuous", style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.testTag("continuous_mode_chip")
            )
        }

        // Animated Circular AI Orb
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            NovaOrbVisualizer(
                phase = phase,
                rmsLevel = rmsLevel,
                onOrbClick = onMicTrigger,
                orbSize = if (messages.isEmpty()) 192.dp else 136.dp
            )
        }

        // Live Partial Transcript Banner
        AnimatedVisibility(visible = partialTranscript.isNotBlank()) {
            Surface(
                color = Color(0xFF0E2440),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "\"$partialTranscript\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ElectricCyan,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // Status / Error Banner
        AnimatedVisibility(visible = !statusBanner.isNullOrBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = statusBanner.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.setStatusBanner(null) }) {
                        Icon(
                            imageVector = Icons.Rounded.Stop,
                            contentDescription = "Dismiss banner",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Sensitive Action Confirmation Card (Calls, SMS, Preferences, Brightness Settings)
        pendingConfirmation?.let { pending ->
            ConfirmationActionCard(
                confirmation = pending,
                onConfirm = { param1, param2 ->
                    viewModel.confirmPendingAction(param1, param2)
                },
                onDismiss = { viewModel.dismissPendingConfirmation() }
            )
        }

        // Conversation Stream or Quick Bilingual Prompts
        Box(modifier = Modifier.weight(1f)) {
            when (messagesState) {
                is UiState.Error -> {
                    val msg = (messagesState as UiState.Error).message
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = msg,
                            color = CoralAlert,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                else -> {
                    if (messages.isEmpty()) {
                        EmptyConversationSuggestions(
                            onSuggestionClick = { prompt ->
                                viewModel.submitPrompt(prompt, activity)
                            }
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(messages, key = { it.id.ifBlank { it.hashCode() } }) { msg ->
                                ChatBubbleItem(
                                    message = msg,
                                    onReplaySpeak = {
                                        viewModel.voiceController.speak(
                                            msg.text,
                                            AssistantLanguage.fromCode(msg.language)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Voice + Text Input Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = {
                    Text(
                        "Ask NOVA in Hindi, English or Hinglish...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricCyan,
                    unfocusedBorderColor = GlassBorder,
                    focusedContainerColor = MidnightSurface,
                    unfocusedContainerColor = MidnightSurface
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (textInput.isNotBlank()) {
                            val q = textInput
                            textInput = ""
                            viewModel.submitPrompt(q, activity)
                        }
                    }
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("prompt_text_input")
            )

            Surface(
                shape = CircleShape,
                color = if (textInput.isNotBlank()) ElectricCyan else ElevatedCard,
                modifier = Modifier.size(50.dp)
            ) {
                IconButton(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            val q = textInput
                            textInput = ""
                            viewModel.submitPrompt(q, activity)
                        } else {
                            onMicTrigger()
                        }
                    },
                    modifier = Modifier.testTag("send_or_mic_button")
                ) {
                    Icon(
                        imageVector = when {
                            textInput.isNotBlank() -> Icons.AutoMirrored.Rounded.Send
                            phase == AssistantPhase.LISTENING -> Icons.Rounded.Stop
                            else -> Icons.Rounded.Mic
                        },
                        contentDescription = if (textInput.isNotBlank()) "Send prompt" else "Voice input",
                        tint = if (textInput.isNotBlank()) ObsidianVoid else ElectricCyan
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyConversationSuggestions(
    onSuggestionClick: (String) -> Unit
) {
    val suggestions = remember {
        listOf(
            "मेरा फोन बैटरी कितना है?",
            "YouTube खोलो",
            "आज का मौसम बताओ",
            "Explain quantum computing simply",
            "Translate 'Good morning, how are you?' to Hindi",
            "245 × 18 + 90"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Try asking NOVA • उदाहरण पूछें",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        suggestions.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pair.forEach { sample ->
                    AssistChip(
                        onClick = { onSuggestionClick(sample) },
                        label = {
                            Text(
                                text = sample,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(15.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = ElevatedCard,
                            labelColor = Color.White
                        ),
                        border = BorderStroke(1.dp, GlassBorder),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubbleItem(
    message: ChatMessageEntity,
    onReplaySpeak: () -> Unit
) {
    val isUser = MessageSender.fromRaw(message.sender) == MessageSender.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isUser) Color(0xFF15325B) else ElevatedCard,
            border = BorderStroke(
                width = 1.dp,
                color = if (isUser) ElectricCyan.copy(alpha = 0.45f) else NeonViolet.copy(alpha = 0.35f)
            ),
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            modifier = Modifier.fillMaxWidth(0.88f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) "YOU" else "NOVA AI",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUser) ElectricCyan else NeonViolet,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (message.actionTag.isNotBlank()) {
                            Surface(
                                color = Color(0xFF0E2938),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = message.actionTag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LuminousTeal,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (!isUser) {
                            IconButton(
                                onClick = onReplaySpeak,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VolumeUp,
                                    contentDescription = "Speak reply",
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun ConfirmationActionCard(
    confirmation: PendingConfirmation,
    onConfirm: (editedParam1: String?, editedParam2: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var phoneOrTarget by remember(confirmation.id) {
        mutableStateOf(
            when (confirmation) {
                is PendingConfirmation.CallConfirmation -> confirmation.phoneNumber
                is PendingConfirmation.SmsConfirmation -> confirmation.phoneNumber
                else -> ""
            }
        )
    }
    var smsBody by remember(confirmation.id) {
        mutableStateOf(
            (confirmation as? PendingConfirmation.SmsConfirmation)?.messageBody.orEmpty()
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141D3D)),
        border = BorderStroke(1.5.dp, AmberSignal.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .testTag("confirmation_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = when (confirmation) {
                        is PendingConfirmation.CallConfirmation -> Icons.Rounded.Call
                        is PendingConfirmation.SmsConfirmation -> Icons.Rounded.Sms
                        is PendingConfirmation.PreferenceConfirmation -> Icons.Rounded.Psychology
                        is PendingConfirmation.SystemSettingsConfirmation -> Icons.Rounded.Settings
                    },
                    contentDescription = "Confirmation required",
                    tint = AmberSignal
                )
                Text(
                    text = "CONFIRMATION REQUIRED",
                    style = MaterialTheme.typography.labelMedium,
                    color = AmberSignal,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = confirmation.promptText,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )

            if (confirmation is PendingConfirmation.CallConfirmation ||
                confirmation is PendingConfirmation.SmsConfirmation
            ) {
                OutlinedTextField(
                    value = phoneOrTarget,
                    onValueChange = { phoneOrTarget = it },
                    label = { Text("Phone number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (confirmation is PendingConfirmation.SmsConfirmation) {
                OutlinedTextField(
                    value = smsBody,
                    onValueChange = { smsBody = it },
                    label = { Text("SMS message") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("confirm_cancel_button")
                ) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(10.dp))
                Button(
                    onClick = { onConfirm(phoneOrTarget, smsBody) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = ObsidianVoid
                    ),
                    modifier = Modifier.testTag("confirm_proceed_button")
                ) {
                    Text("Confirm", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
