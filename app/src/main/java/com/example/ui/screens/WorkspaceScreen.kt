package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.NoteAlt
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.firebase.UiState
import com.example.model.NoteItem
import com.example.model.ReminderItem
import com.example.model.TodoItem
import com.example.model.TodoPriority
import com.example.ui.theme.AmberSignal
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.EmeraldOk
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.LuminousTeal
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianVoid
import com.example.viewmodel.NovaViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WorkspaceScreen(
    viewModel: NovaViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("To-Do", "Reminders", "Notes")

    val todosState by viewModel.todosState.collectAsStateWithLifecycle()
    val remindersState by viewModel.remindersState.collectAsStateWithLifecycle()
    val notesState by viewModel.notesState.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        showCreateDialog = true
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("workspace_tabs")
            ) {
                tabs.forEachIndexed { index, title ->
                    SegmentedButton(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                        label = { Text(title) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (selectedTab) {
                0 -> TodosListSection(
                    state = todosState,
                    onToggle = { viewModel.toggleTodo(it) },
                    onDelete = { viewModel.deleteTodo(it.id) }
                )
                1 -> RemindersListSection(
                    state = remindersState,
                    onToggle = { viewModel.toggleReminder(it) },
                    onDelete = { viewModel.deleteReminder(it.id) }
                )
                2 -> NotesListSection(
                    state = notesState,
                    onDelete = { viewModel.deleteNote(it.id) }
                )
            }
        }

        FloatingActionButton(
            onClick = {
                if (selectedTab == 1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!granted) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        showCreateDialog = true
                    }
                } else {
                    showCreateDialog = true
                }
            },
            containerColor = ElectricCyan,
            contentColor = ObsidianVoid,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("workspace_add_fab")
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Add item")
        }
    }

    if (showCreateDialog) {
        when (selectedTab) {
            0 -> AddTodoDialog(
                onDismiss = { showCreateDialog = false },
                onCreate = { title, priority ->
                    viewModel.addTodo(title, priority)
                    showCreateDialog = false
                }
            )
            1 -> AddReminderDialog(
                onDismiss = { showCreateDialog = false },
                onCreate = { title, mins, cat ->
                    viewModel.addReminder(title, mins, cat)
                    showCreateDialog = false
                }
            )
            else -> AddNoteDialog(
                onDismiss = { showCreateDialog = false },
                onCreate = { title, content, tag ->
                    viewModel.addNote(title, content, tag)
                    showCreateDialog = false
                }
            )
        }
    }
}

@Composable
private fun TodosListSection(
    state: UiState<List<TodoItem>>,
    onToggle: (TodoItem) -> Unit,
    onDelete: (TodoItem) -> Unit
) {
    when (state) {
        is UiState.Loading -> LoadingBox()
        is UiState.Error -> ErrorBox(state.message)
        is UiState.Success -> {
            if (state.data.isEmpty()) {
                EmptyBox("No tasks yet. Tap + or say \"Add buy milk to my to-do list\".")
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 84.dp)
                ) {
                    items(state.data, key = { it.id }) { item ->
                        val priority = TodoPriority.fromRaw(item.priority)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                            border = BorderStroke(1.dp, GlassBorder),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { onToggle(item) }) {
                                    Icon(
                                        imageVector = if (item.completed) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                        contentDescription = "Toggle task",
                                        tint = if (item.completed) EmeraldOk else ElectricCyan
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (item.completed) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                                        textDecoration = if (item.completed) TextDecoration.LineThrough else TextDecoration.None
                                    )
                                    Text(
                                        text = priority.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when (priority) {
                                            TodoPriority.HIGH -> CoralAlert
                                            TodoPriority.NORMAL -> ElectricCyan
                                            TodoPriority.LOW -> LuminousTeal
                                        }
                                    )
                                }
                                IconButton(onClick = { onDelete(item) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete task", tint = CoralAlert)
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
private fun RemindersListSection(
    state: UiState<List<ReminderItem>>,
    onToggle: (ReminderItem) -> Unit,
    onDelete: (ReminderItem) -> Unit
) {
    val fmt = remember { SimpleDateFormat("EEE, MMM d • hh:mm a", Locale.ENGLISH) }
    when (state) {
        is UiState.Loading -> LoadingBox()
        is UiState.Error -> ErrorBox(state.message)
        is UiState.Success -> {
            if (state.data.isEmpty()) {
                EmptyBox("No smart reminders yet. Tap + or say \"Remind me in 20 minutes\".")
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 84.dp)
                ) {
                    items(state.data, key = { it.id }) { rem ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                            border = BorderStroke(1.dp, GlassBorder),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.NotificationsActive,
                                    contentDescription = null,
                                    tint = if (rem.active) AmberSignal else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 12.dp)
                                ) {
                                    Text(rem.title, style = MaterialTheme.typography.titleSmall, color = Color.White)
                                    Text(
                                        text = "${rem.category} • ${fmt.format(Date(rem.dueTimeMillis))}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ElectricCyan
                                    )
                                }
                                Switch(
                                    checked = rem.active,
                                    onCheckedChange = { onToggle(rem) }
                                )
                                IconButton(onClick = { onDelete(rem) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete reminder", tint = CoralAlert)
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
private fun NotesListSection(
    state: UiState<List<NoteItem>>,
    onDelete: (NoteItem) -> Unit
) {
    when (state) {
        is UiState.Loading -> LoadingBox()
        is UiState.Error -> ErrorBox(state.message)
        is UiState.Success -> {
            if (state.data.isEmpty()) {
                EmptyBox("No notes saved yet. Tap + or say \"Create a note about project ideas\".")
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 84.dp)
                ) {
                    items(state.data, key = { it.id }) { note ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                            border = BorderStroke(1.dp, GlassBorder),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Rounded.NoteAlt, contentDescription = null, tint = NeonViolet, modifier = Modifier.size(18.dp))
                                        Text(note.title, style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                    IconButton(onClick = { onDelete(note) }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "Delete note", tint = CoralAlert)
                                    }
                                }
                                if (note.content.isNotBlank()) {
                                    Text(
                                        text = note.content,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "#${note.tag}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricCyan
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddTodoDialog(
    onDismiss: () -> Unit,
    onCreate: (String, TodoPriority) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(TodoPriority.NORMAL) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task description") },
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TodoPriority.entries.forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(p.name) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (title.isNotBlank()) onCreate(title, priority) },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianVoid)
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddReminderDialog(
    onDismiss: () -> Unit,
    onCreate: (String, Int, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var minutesStr by remember { mutableStateOf("15") }
    var category by remember { mutableStateOf("Personal") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Smart Reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Remind me to...") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = minutesStr,
                    onValueChange = { minutesStr = it.filter { c -> c.isDigit() } },
                    label = { Text("In how many minutes?") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val mins = minutesStr.toIntOrNull() ?: 15
                    if (title.isNotBlank()) onCreate(title, mins, category)
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianVoid)
            ) { Text("Schedule") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddNoteDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("General") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Note") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("Content") }, minLines = 3)
                OutlinedTextField(value = tag, onValueChange = { tag = it }, label = { Text("Tag") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = { if (title.isNotBlank()) onCreate(title, content, tag) },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianVoid)
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun LoadingBox() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = ElectricCyan)
    }
}

@Composable
private fun ErrorBox(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = CoralAlert, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EmptyBox(message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
