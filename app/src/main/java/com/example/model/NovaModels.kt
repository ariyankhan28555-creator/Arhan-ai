package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class TodoPriority {
    LOW, NORMAL, HIGH;

    companion object {
        fun fromRaw(raw: String?): TodoPriority =
            entries.firstOrNull { it.name == raw } ?: NORMAL
    }
}

enum class MessageSender {
    USER, NOVA;

    companion object {
        fun fromRaw(raw: String?): MessageSender =
            entries.firstOrNull { it.name == raw } ?: NOVA
    }
}

enum class AssistantLanguage(val code: String, val label: String, val bcp47: String) {
    HINGLISH("hinglish", "Auto / Hinglish", "en-IN"),
    HINDI("hi", "हिन्दी (Hindi)", "hi-IN"),
    ENGLISH("en", "English", "en-US");

    companion object {
        fun fromCode(code: String?): AssistantLanguage =
            entries.firstOrNull { it.code == code } ?: HINGLISH
    }
}

enum class VoiceGender(val label: String) {
    FEMALE("Female (NOVA)"),
    MALE("Male (NOVA Prime)")
}

enum class AssistantPhase(val labelEn: String, val labelHi: String) {
    IDLE("Ready", "तैयार"),
    LISTENING("Listening...", "सुन रही हूँ..."),
    THINKING("Thinking...", "सोच रही हूँ..."),
    SPEAKING("Speaking...", "बोल रही हूँ...")
}

@Keep
@IgnoreExtraProperties
data class NoteItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val content: String = "",
    val tag: String = "General",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class TodoItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val completed: Boolean = false,
    val priority: String = TodoPriority.NORMAL.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ReminderItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val dueTimeMillis: Long = 0L,
    val active: Boolean = true,
    val category: String = "Personal",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserPreference(
    @DocumentId val id: String = "",
    val userId: String = "",
    val prefKey: String = "",
    val prefValue: String = "",
    val approvedByUser: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessageEntity(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sender: String = MessageSender.NOVA.name,
    val text: String = "",
    val language: String = AssistantLanguage.ENGLISH.code,
    val actionTag: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class NovaSettings(
    val voiceGender: VoiceGender = VoiceGender.FEMALE,
    val speechRate: Float = 1.0f,
    val recognitionLanguage: AssistantLanguage = AssistantLanguage.HINGLISH,
    val wakeWordEnabled: Boolean = false,
    val continuousModeEnabled: Boolean = false,
    val preferredCity: String = "New Delhi"
)

data class WeatherInfo(
    val city: String,
    val temperatureC: Double,
    val apparentTempC: Double,
    val humidityPercent: Int,
    val windKmh: Double,
    val conditionEn: String,
    val conditionHi: String,
    val isDay: Boolean,
    val fetchedAtMillis: Long
)

data class NewsArticle(
    val title: String,
    val source: String,
    val link: String,
    val pubDate: String
)

data class CalendarEventItem(
    val id: Long,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val location: String
)

data class DeviceSnapshot(
    val batteryPercent: Int = 0,
    val isCharging: Boolean = false,
    val torchOn: Boolean = false,
    val mediaVolume: Int = 0,
    val maxMediaVolume: Int = 15,
    val brightnessPercent: Int = 50,
    val canWriteSystemSettings: Boolean = false,
    val isOnline: Boolean = true
)

data class InstalledAppInfo(
    val label: String,
    val packageName: String
)

sealed interface PendingConfirmation {
    val id: String
    val promptText: String

    data class CallConfirmation(
        override val id: String,
        override val promptText: String,
        val targetDisplay: String,
        val phoneNumber: String
    ) : PendingConfirmation

    data class SmsConfirmation(
        override val id: String,
        override val promptText: String,
        val recipientDisplay: String,
        val phoneNumber: String,
        val messageBody: String
    ) : PendingConfirmation

    data class PreferenceConfirmation(
        override val id: String,
        override val promptText: String,
        val prefKey: String,
        val prefValue: String
    ) : PendingConfirmation

    data class SystemSettingsConfirmation(
        override val id: String,
        override val promptText: String,
        val requestedBrightnessPercent: Int
    ) : PendingConfirmation
}
