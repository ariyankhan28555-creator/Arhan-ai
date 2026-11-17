package com.example.viewmodel

import android.app.Activity
import android.media.AudioManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.NovaActionType
import com.example.ai.NovaAiEngine
import com.example.ai.NovaDirective
import com.example.ai.OfflineFallbackEngine
import com.example.data.FirestoreRepository
import com.example.data.SettingsDataStore
import com.example.data.WeatherAndNewsRepository
import com.example.device.DeviceController
import com.example.firebase.UiState
import com.example.model.AssistantLanguage
import com.example.model.AssistantPhase
import com.example.model.CalendarEventItem
import com.example.model.ChatMessageEntity
import com.example.model.DeviceSnapshot
import com.example.model.InstalledAppInfo
import com.example.model.MessageSender
import com.example.model.NewsArticle
import com.example.model.NoteItem
import com.example.model.NovaSettings
import com.example.model.PendingConfirmation
import com.example.model.ReminderItem
import com.example.model.TodoItem
import com.example.model.TodoPriority
import com.example.model.UserPreference
import com.example.model.VoiceGender
import com.example.model.WeatherInfo
import com.example.reminders.ReminderScheduler
import com.example.voice.NovaVoiceController
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

class NovaViewModel(
    private val currentUserId: String,
    private val firestoreRepo: FirestoreRepository,
    private val settingsStore: SettingsDataStore,
    private val weatherNewsRepo: WeatherAndNewsRepository,
    private val aiEngine: NovaAiEngine,
    val deviceController: DeviceController,
    private val reminderScheduler: ReminderScheduler,
    val voiceController: NovaVoiceController
) : ViewModel() {

    val settings: StateFlow<NovaSettings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), NovaSettings())

    val deviceSnapshot: StateFlow<DeviceSnapshot> = deviceController.snapshot

    val notesState: StateFlow<UiState<List<NoteItem>>> = firestoreRepo.observeNotes(currentUserId)
        .asUiState("Failed to load notes")

    val todosState: StateFlow<UiState<List<TodoItem>>> = firestoreRepo.observeTodos(currentUserId)
        .asUiState("Failed to load tasks")

    val remindersState: StateFlow<UiState<List<ReminderItem>>> = firestoreRepo.observeReminders(currentUserId)
        .asUiState("Failed to load reminders")

    val preferencesState: StateFlow<UiState<List<UserPreference>>> = firestoreRepo.observePreferences(currentUserId)
        .asUiState("Failed to load preferences")

    val messagesState: StateFlow<UiState<List<ChatMessageEntity>>> = firestoreRepo.observeMessages(currentUserId)
        .asUiState("Failed to load conversation")

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    val assistantPhase: StateFlow<AssistantPhase> = combine(
        voiceController.isListening,
        _isThinking,
        voiceController.isSpeaking
    ) { listening, thinking, speaking ->
        when {
            listening -> AssistantPhase.LISTENING
            thinking -> AssistantPhase.THINKING
            speaking -> AssistantPhase.SPEAKING
            else -> AssistantPhase.IDLE
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AssistantPhase.IDLE)

    private val _pendingConfirmation = MutableStateFlow<PendingConfirmation?>(null)
    val pendingConfirmation: StateFlow<PendingConfirmation?> = _pendingConfirmation.asStateFlow()

    private val _weatherState = MutableStateFlow<UiState<WeatherInfo>>(UiState.Loading)
    val weatherState: StateFlow<UiState<WeatherInfo>> = _weatherState.asStateFlow()

    private val _newsState = MutableStateFlow<UiState<List<NewsArticle>>>(UiState.Loading)
    val newsState: StateFlow<UiState<List<NewsArticle>>> = _newsState.asStateFlow()

    private val _calendarEvents = MutableStateFlow<List<CalendarEventItem>>(emptyList())
    val calendarEvents: StateFlow<List<CalendarEventItem>> = _calendarEvents.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _windowBrightnessEvent = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val windowBrightnessEvent: SharedFlow<Int> = _windowBrightnessEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            settings.collect { s ->
                voiceController.updatePreferences(
                    language = s.recognitionLanguage,
                    voiceGender = s.voiceGender,
                    speechRate = s.speechRate,
                    wakeWord = s.wakeWordEnabled,
                    continuousMode = s.continuousModeEnabled
                )
            }
        }
        refreshDashboardData()
    }

    fun refreshDashboardData() {
        deviceController.refreshSnapshot()
        _calendarEvents.value = deviceController.readUpcomingCalendarEvents()
        _installedApps.value = deviceController.getInstalledLauncherApps().take(24)
        refreshWeather()
        refreshNews()
    }

    fun refreshCalendarAndApps() {
        _calendarEvents.value = deviceController.readUpcomingCalendarEvents()
        _installedApps.value = deviceController.getInstalledLauncherApps().take(24)
    }

    fun refreshWeather(cityOverride: String? = null) {
        viewModelScope.launch {
            _weatherState.value = UiState.Loading
            val loc = if (cityOverride.isNullOrBlank()) deviceController.getLastCoarseLocation() else null
            val res = if (loc != null) {
                weatherNewsRepo.fetchWeatherByCoordinates(loc.latitude, loc.longitude)
            } else {
                val city = cityOverride?.takeIf { it.isNotBlank() } ?: settings.value.preferredCity
                weatherNewsRepo.fetchWeatherByCity(city)
            }
            _weatherState.value = res.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.localizedMessage ?: "Unable to load weather.") }
            )
        }
    }

    fun refreshNews(hindi: Boolean = false, topic: String? = null) {
        viewModelScope.launch {
            _newsState.value = UiState.Loading
            val useHindi = hindi || settings.value.recognitionLanguage == AssistantLanguage.HINDI
            val res = weatherNewsRepo.fetchHeadlines(hindi = useHindi, query = topic)
            _newsState.value = res.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.localizedMessage ?: "Unable to fetch news.") }
            )
        }
    }

    fun handleVoiceResult(transcript: String, isWakeWordTrigger: Boolean) {
        val clean = transcript.trim()
        if (isWakeWordTrigger && clean.isEmpty()) {
            val lang = settings.value.recognitionLanguage
            val greeting = if (lang == AssistantLanguage.ENGLISH) {
                "Yes, I'm listening. How can I help you?"
            } else {
                "जी, बताइए! मैं आपकी क्या मदद कर सकती हूँ?"
            }
            viewModelScope.launch {
                firestoreRepo.addMessage(currentUserId, MessageSender.NOVA, greeting, lang, "WAKE_GREETING")
            }
            voiceController.speak(greeting, lang)
            return
        }
        if (clean.isNotEmpty()) {
            submitPrompt(clean, null)
        }
    }

    fun submitPrompt(promptText: String, activity: Activity?) {
        val clean = promptText.trim()
        if (clean.isEmpty() || _isThinking.value) return

        viewModelScope.launch {
            _statusBanner.value = null
            val currentSettings = settings.value
            val detectedLang = when {
                OfflineFallbackEngine.containsDevanagari(clean) -> AssistantLanguage.HINDI
                currentSettings.recognitionLanguage == AssistantLanguage.ENGLISH -> AssistantLanguage.ENGLISH
                else -> currentSettings.recognitionLanguage
            }
            firestoreRepo.addMessage(currentUserId, MessageSender.USER, clean, detectedLang)

            _isThinking.value = true
            try {
                val snap = deviceController.refreshSnapshot()
                val history = (messagesState.value as? UiState.Success)?.data.orEmpty()
                val prefs = (preferencesState.value as? UiState.Success)?.data.orEmpty()

                val directive: NovaDirective = if (snap.isOnline) {
                    val aiResult = aiEngine.processQuery(
                        userInput = clean,
                        history = history,
                        preferences = prefs,
                        snapshot = snap,
                        preferredLanguage = currentSettings.recognitionLanguage,
                        preferredCity = currentSettings.preferredCity
                    )
                    aiResult.getOrElse { error ->
                        Log.w(TAG, "AI request failed, checking offline fallback", error)
                        val offline = OfflineFallbackEngine.evaluate(clean, snap, currentSettings.recognitionLanguage)
                        if (offline.action == NovaActionType.NONE &&
                            OfflineFallbackEngine.tryEvaluateMath(clean) == null
                        ) {
                            val errMsg = error.localizedMessage ?: "AI request failed."
                            _statusBanner.value = errMsg
                            NovaDirective(
                                action = NovaActionType.NONE,
                                language = detectedLang,
                                spokenResponse = "AI service error: $errMsg",
                                displayResponse = "Unable to reach NOVA cloud AI: $errMsg"
                            )
                        } else {
                            offline
                        }
                    }
                } else {
                    OfflineFallbackEngine.evaluate(clean, snap, currentSettings.recognitionLanguage)
                }

                val (finalSpoken, finalDisplay, actionLabel) = executeDirective(directive, activity)

                firestoreRepo.addMessage(
                    userId = currentUserId,
                    sender = MessageSender.NOVA,
                    text = finalDisplay,
                    language = directive.language,
                    actionTag = actionLabel
                )
                voiceController.speak(finalSpoken, directive.language)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Prompt handling failed", e)
                _statusBanner.value = e.localizedMessage ?: "Unexpected error."
            } finally {
                _isThinking.value = false
            }
        }
    }

    private suspend fun executeDirective(
        directive: NovaDirective,
        activity: Activity?
    ): Triple<String, String, String> {
        var spoken = directive.spokenResponse
        var display = directive.displayResponse
        val tag = if (directive.action == NovaActionType.NONE) "" else directive.action.name

        when (directive.action) {
            NovaActionType.OPEN_APP -> {
                val res = deviceController.launchAppByName(directive.param1)
                if (res.isFailure) {
                    val msg = if (directive.language == AssistantLanguage.ENGLISH) {
                        "I couldn't find ${directive.param1} installed on this device."
                    } else {
                        "मुझे आपके फोन में ${directive.param1} ऐप नहीं मिला।"
                    }
                    spoken = msg
                    display = msg
                } else {
                    val launchedName = res.getOrNull() ?: directive.param1
                    display = "$display\n\nLaunched: $launchedName"
                }
            }
            NovaActionType.CLOSE_APP_INFO -> {
                val msg = if (directive.language == AssistantLanguage.ENGLISH) {
                    "For security, Android does not allow third-party assistants to force-close other apps. You can return to the Home screen anytime."
                } else {
                    "सुरक्षा कारणों से Android किसी थर्ड-पार्टी ऐप को दूसरे ऐप बंद करने की अनुमति नहीं देता। मैं आपको होम स्क्रीन पर ले जा सकती हूँ।"
                }
                spoken = msg
                display = msg
            }
            NovaActionType.MAKE_CALL -> {
                val query = directive.param1.ifBlank { "Contact" }
                val contact = if (query.any { it.isDigit() }) {
                    query to query
                } else {
                    deviceController.lookupContactPhone(query) ?: (query to "")
                }
                val confirmText = if (directive.language == AssistantLanguage.ENGLISH) {
                    "Confirm calling ${contact.first}?"
                } else {
                    "क्या मैं ${contact.first} को कॉल करूँ? कृपया पुष्टि करें।"
                }
                _pendingConfirmation.value = PendingConfirmation.CallConfirmation(
                    id = UUID.randomUUID().toString(),
                    promptText = confirmText,
                    targetDisplay = contact.first,
                    phoneNumber = contact.second
                )
                spoken = confirmText
                display = confirmText
            }
            NovaActionType.SEND_SMS -> {
                val query = directive.param1.ifBlank { "Recipient" }
                val contact = if (query.any { it.isDigit() }) {
                    query to query
                } else {
                    deviceController.lookupContactPhone(query) ?: (query to "")
                }
                val confirmText = if (directive.language == AssistantLanguage.ENGLISH) {
                    "Please review and confirm sending SMS to ${contact.first}."
                } else {
                    "${contact.first} को संदेश भेजने से पहले कृपया पुष्टि करें।"
                }
                _pendingConfirmation.value = PendingConfirmation.SmsConfirmation(
                    id = UUID.randomUUID().toString(),
                    promptText = confirmText,
                    recipientDisplay = contact.first,
                    phoneNumber = contact.second,
                    messageBody = directive.param2
                )
                spoken = confirmText
                display = "$confirmText\nMessage: \"${directive.param2}\""
            }
            NovaActionType.SET_ALARM -> {
                val parts = directive.param1.split(":")
                val hour = parts.getOrNull(0)?.toIntOrNull() ?: 7
                val min = parts.getOrNull(1)?.toIntOrNull() ?: 0
                deviceController.setAlarm(hour, min, directive.param2.ifBlank { "NOVA Alarm" })
            }
            NovaActionType.SET_TIMER -> {
                val sec = directive.param1.toIntOrNull() ?: 60
                deviceController.setTimer(sec, directive.param2.ifBlank { "NOVA Timer" })
            }
            NovaActionType.FLASHLIGHT_ON -> {
                deviceController.setTorch(true).onFailure {
                    spoken = it.localizedMessage ?: "Flashlight unavailable."
                    display = spoken
                }
            }
            NovaActionType.FLASHLIGHT_OFF -> {
                deviceController.setTorch(false).onFailure {
                    spoken = it.localizedMessage ?: "Flashlight unavailable."
                    display = spoken
                }
            }
            NovaActionType.VOLUME_UP -> deviceController.adjustVolume(AudioManager.ADJUST_RAISE)
            NovaActionType.VOLUME_DOWN -> deviceController.adjustVolume(AudioManager.ADJUST_LOWER)
            NovaActionType.VOLUME_MUTE -> deviceController.adjustVolume(AudioManager.ADJUST_MUTE)
            NovaActionType.SET_BRIGHTNESS -> {
                val pct = directive.param1.toIntOrNull() ?: 60
                val wroteSystem = deviceController.setBrightness(pct, activity)
                _windowBrightnessEvent.tryEmit(pct)
                if (!wroteSystem) {
                    _pendingConfirmation.value = PendingConfirmation.SystemSettingsConfirmation(
                        id = UUID.randomUUID().toString(),
                        promptText = "App brightness updated to $pct%. Grant Modify System Settings permission to control system-wide screen brightness?",
                        requestedBrightnessPercent = pct
                    )
                }
            }
            NovaActionType.OPEN_CAMERA -> deviceController.openCamera()
            NovaActionType.OPEN_SETTINGS -> deviceController.openSettings(directive.param1)
            NovaActionType.OPEN_CALENDAR -> {
                refreshCalendarAndApps()
                if (directive.param1.isNotBlank()) {
                    deviceController.insertCalendarEvent(directive.param1)
                }
            }
            NovaActionType.ADD_NOTE -> {
                val title = directive.param1.ifBlank { "NOVA Note" }
                val body = directive.param2.ifBlank { title }
                firestoreRepo.addNote(currentUserId, title, body, "Voice").onFailure {
                    _statusBanner.value = it.localizedMessage
                }
            }
            NovaActionType.ADD_TODO -> {
                val title = directive.param1.ifBlank { "New Task" }
                val priority = TodoPriority.fromRaw(directive.param2)
                firestoreRepo.addTodo(currentUserId, title, priority).onFailure {
                    _statusBanner.value = it.localizedMessage
                }
            }
            NovaActionType.ADD_REMINDER -> {
                val title = directive.param1.ifBlank { "Reminder" }
                val dueMillis = parseReminderTime(directive.param2)
                firestoreRepo.addReminder(currentUserId, title, dueMillis, "Assistant")
                    .onSuccess { remId ->
                        reminderScheduler.scheduleReminder(remId, title, "Assistant", dueMillis)
                    }
                    .onFailure {
                        _statusBanner.value = it.localizedMessage
                    }
            }
            NovaActionType.SAVE_PREFERENCE -> {
                val key = directive.param1.ifBlank { "Preference" }
                val value = directive.param2
                if (value.isNotBlank()) {
                    val confirmText = if (directive.language == AssistantLanguage.ENGLISH) {
                        "Should I remember \"$key: $value\" in your personal profile?"
                    } else {
                        "क्या मैं आपकी यह पसंद याद रखूँ: \"$key: $value\"?"
                    }
                    _pendingConfirmation.value = PendingConfirmation.PreferenceConfirmation(
                        id = UUID.randomUUID().toString(),
                        promptText = confirmText,
                        prefKey = key,
                        prefValue = value
                    )
                    spoken = confirmText
                    display = confirmText
                }
            }
            NovaActionType.GET_WEATHER -> {
                val city = directive.param1.takeIf { it.isNotBlank() }
                val loc = if (city == null) deviceController.getLastCoarseLocation() else null
                val res = if (loc != null) {
                    weatherNewsRepo.fetchWeatherByCoordinates(loc.latitude, loc.longitude)
                } else {
                    weatherNewsRepo.fetchWeatherByCity(city ?: settings.value.preferredCity)
                }
                res.onSuccess { w ->
                    _weatherState.value = UiState.Success(w)
                    val summary = if (directive.language == AssistantLanguage.ENGLISH) {
                        "In ${w.city}, it is ${w.temperatureC}°C with ${w.conditionEn}. Humidity ${w.humidityPercent}% and wind ${w.windKmh} km/h."
                    } else {
                        "${w.city} में अभी तापमान ${w.temperatureC}°C है और मौसम ${w.conditionHi} है। नमी ${w.humidityPercent}% है।"
                    }
                    spoken = summary
                    display = summary
                }.onFailure { err ->
                    val errText = "Weather update failed: ${err.localizedMessage}"
                    spoken = errText
                    display = errText
                }
            }
            NovaActionType.GET_NEWS -> {
                val useHindi = directive.language != AssistantLanguage.ENGLISH
                val res = weatherNewsRepo.fetchHeadlines(
                    hindi = useHindi,
                    query = directive.param1.takeIf { it.isNotBlank() }
                )
                res.onSuccess { articles ->
                    _newsState.value = UiState.Success(articles)
                    val topText = articles.take(3).mapIndexed { i, a -> "${i + 1}. ${a.title}" }
                        .joinToString("\n")
                    if (topText.isNotBlank()) {
                        val intro = if (useHindi) "ताज़ा मुख्य समाचार:\n" else "Top news headlines:\n"
                        spoken = intro + articles.take(2).joinToString(". ") { it.title }
                        display = intro + topText
                    }
                }.onFailure { err ->
                    val errText = "News fetch failed: ${err.localizedMessage}"
                    spoken = errText
                    display = errText
                }
            }
            NovaActionType.CHECK_BATTERY,
            NovaActionType.CHECK_DATETIME,
            NovaActionType.NONE -> Unit
        }
        return Triple(spoken, display, tag)
    }

    fun confirmPendingAction(editedPhoneOrValue: String? = null, editedSmsBody: String? = null) {
        val pending = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        when (pending) {
            is PendingConfirmation.CallConfirmation -> {
                val number = editedPhoneOrValue?.takeIf { it.isNotBlank() } ?: pending.phoneNumber
                deviceController.openDialer(number)
            }
            is PendingConfirmation.SmsConfirmation -> {
                val number = editedPhoneOrValue?.takeIf { it.isNotBlank() } ?: pending.phoneNumber
                val body = editedSmsBody ?: pending.messageBody
                deviceController.openSmsComposer(number, body)
            }
            is PendingConfirmation.PreferenceConfirmation -> {
                viewModelScope.launch {
                    firestoreRepo.saveApprovedPreference(currentUserId, pending.prefKey, pending.prefValue)
                        .onFailure { _statusBanner.value = it.localizedMessage }
                }
            }
            is PendingConfirmation.SystemSettingsConfirmation -> {
                deviceController.openWriteSettingsPermissionScreen()
            }
        }
    }

    fun dismissPendingConfirmation() {
        _pendingConfirmation.value = null
    }

    // --- WORKSPACE CRUD ---
    fun addNote(title: String, content: String, tag: String) {
        viewModelScope.launch {
            firestoreRepo.addNote(currentUserId, title, content, tag)
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            firestoreRepo.deleteNote(currentUserId, noteId)
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun addTodo(title: String, priority: TodoPriority) {
        viewModelScope.launch {
            firestoreRepo.addTodo(currentUserId, title, priority)
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun toggleTodo(todo: TodoItem) {
        viewModelScope.launch {
            firestoreRepo.toggleTodo(currentUserId, todo.id, !todo.completed)
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun deleteTodo(todoId: String) {
        viewModelScope.launch {
            firestoreRepo.deleteTodo(currentUserId, todoId)
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun addReminder(title: String, minutesFromNow: Int, category: String) {
        val due = System.currentTimeMillis() + minutesFromNow.coerceAtLeast(1) * 60_000L
        viewModelScope.launch {
            firestoreRepo.addReminder(currentUserId, title, due, category)
                .onSuccess { id -> reminderScheduler.scheduleReminder(id, title, category, due) }
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun toggleReminder(item: ReminderItem) {
        val newActive = !item.active
        viewModelScope.launch {
            firestoreRepo.toggleReminder(currentUserId, item.id, newActive)
                .onSuccess {
                    if (newActive) {
                        reminderScheduler.scheduleReminder(item.id, item.title, item.category, item.dueTimeMillis)
                    } else {
                        reminderScheduler.cancelReminder(item.id)
                    }
                }
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun deleteReminder(reminderId: String) {
        reminderScheduler.cancelReminder(reminderId)
        viewModelScope.launch {
            firestoreRepo.deleteReminder(currentUserId, reminderId)
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun deletePreference(prefId: String) {
        viewModelScope.launch {
            firestoreRepo.deletePreference(currentUserId, prefId)
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            firestoreRepo.clearConversation(currentUserId)
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    // --- SETTINGS ---
    fun setVoiceGender(gender: VoiceGender) {
        viewModelScope.launch { settingsStore.setVoiceGender(gender) }
    }

    fun setSpeechRate(rate: Float) {
        viewModelScope.launch { settingsStore.setSpeechRate(rate) }
    }

    fun setRecognitionLanguage(language: AssistantLanguage) {
        viewModelScope.launch { settingsStore.setRecognitionLanguage(language) }
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setWakeWordEnabled(enabled) }
    }

    fun setContinuousModeEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setContinuousModeEnabled(enabled) }
    }

    fun setPreferredCity(city: String) {
        viewModelScope.launch {
            settingsStore.setPreferredCity(city)
            refreshWeather(city)
        }
    }

    fun setStatusBanner(message: String?) {
        _statusBanner.value = message
    }

    fun testAssistantVoice() {
        val lang = settings.value.recognitionLanguage
        val sample = if (lang == AssistantLanguage.ENGLISH) {
            "Hello! I am NOVA, your personal AI voice assistant."
        } else {
            "नमस्ते! मैं नोवा हूँ, आपकी व्यक्तिगत एआई वॉइस असिस्टेंट।"
        }
        voiceController.speak(sample, lang)
    }

    private fun parseReminderTime(param: String): Long {
        val now = System.currentTimeMillis()
        val mins = param.toLongOrNull()
        if (mins != null && mins > 0) {
            return now + mins * 60_000L
        }
        if (param.contains(":")) {
            val parts = param.split(":")
            val h = parts.getOrNull(0)?.toIntOrNull()
            val m = parts.getOrNull(1)?.toIntOrNull()
            if (h != null && m != null) {
                var target = ZonedDateTime.now(ZoneId.systemDefault())
                    .with(LocalTime.of(h.coerceIn(0, 23), m.coerceIn(0, 59)))
                if (target.toInstant().toEpochMilli() <= now) {
                    target = target.plusDays(1)
                }
                return target.toInstant().toEpochMilli()
            }
        }
        return now + 15L * 60_000L
    }

    private fun <T> Flow<T>.asUiState(fallbackError: String): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, fallbackError, rootError)
                emit(UiState.Error(rootError.message ?: fallbackError))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    override fun onCleared() {
        super.onCleared()
        voiceController.release()
        deviceController.release()
    }

    private companion object {
        const val TAG = "NovaViewModel"
    }
}
