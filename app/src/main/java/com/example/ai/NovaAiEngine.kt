package com.example.ai

import android.util.Log
import com.example.model.AssistantLanguage
import com.example.model.ChatMessageEntity
import com.example.model.DeviceSnapshot
import com.example.model.UserPreference
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class NovaAiEngine {

    private val directiveSchema = Schema.obj(
        properties = mapOf(
            "action" to Schema.enumeration(
                values = NovaActionType.entries.map { it.name },
                description = "Device or assistant action to execute, or NONE for pure Q&A/translation/explanation."
            ),
            "param1" to Schema.string(
                description = "Primary parameter: app name for OPEN_APP/CLOSE_APP_INFO; contact/phone for MAKE_CALL/SEND_SMS; HH:mm for SET_ALARM; seconds integer for SET_TIMER; 0-100 integer for SET_BRIGHTNESS; title for ADD_NOTE/ADD_TODO/ADD_REMINDER; preference key for SAVE_PREFERENCE; city for GET_WEATHER; topic for GET_NEWS; settings subtype (wifi/bluetooth/display/sound/main) for OPEN_SETTINGS; empty if unused."
            ),
            "param2" to Schema.string(
                description = "Secondary parameter: SMS message body for SEND_SMS; note content for ADD_NOTE; priority (LOW/NORMAL/HIGH) for ADD_TODO; relative minutes or HH:mm for ADD_REMINDER; preference value for SAVE_PREFERENCE; alarm label for SET_ALARM; empty if unused."
            ),
            "language" to Schema.enumeration(
                values = listOf("hi", "en", "hinglish"),
                description = "Language matching the user's request."
            ),
            "spokenResponse" to Schema.string(
                description = "Concise, natural voice response for TTS in the user's language (Hindi Devanagari for Hindi/Hinglish Hindi phrases, or natural English)."
            ),
            "displayResponse" to Schema.string(
                description = "Rich formatted text answer for the chat card (can include math steps, translations, or explanations)."
            )
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = directiveSchema
                temperature = 0.4f
            },
            systemInstruction = content {
                text(
                    """
                    You are NOVA AI, an intelligent, warm, futuristic personal Android voice assistant.
                    You understand Hindi, English, and Hinglish seamlessly and respond naturally in the same language/style the user used.
                    When speaking Hindi or Hinglish, use clean Devanagari Hindi for spokenResponse where appropriate (e.g. "जी, बताइए! मैं आपकी क्या मदद कर सकती हूँ?", "आपके फोन की बैटरी 75 प्रतिशत है।", "जी, YouTube खोल रही हूँ।", "आज के मौसम की जानकारी प्राप्त कर रही हूँ।").
                    Use a respectful, friendly, female-persona tone by default in Hindi ("कर सकती हूँ", "रही हूँ").
                    
                    Action routing rules:
                    - Open an app ("YouTube खोलो", "Open Spotify") -> action=OPEN_APP, param1=app name.
                    - Close app request -> action=CLOSE_APP_INFO, param1=app name, explain politely that Android restricts force-closing other apps and offer Home/App Info.
                    - Phone call -> action=MAKE_CALL, param1=contact name or phone number. Never claim the call is placed yet; ask user confirmation.
                    - SMS -> action=SEND_SMS, param1=recipient name or number, param2=message text. Ask user confirmation.
                    - Alarm -> action=SET_ALARM, param1=HH:mm (24h), param2=label.
                    - Timer -> action=SET_TIMER, param1=seconds as integer string, param2=label.
                    - Flashlight -> FLASHLIGHT_ON or FLASHLIGHT_OFF.
                    - Battery -> CHECK_BATTERY (read exact percentage from provided device context!).
                    - Date/time -> CHECK_DATETIME (use provided current time!).
                    - Volume -> VOLUME_UP, VOLUME_DOWN, or VOLUME_MUTE.
                    - Brightness -> SET_BRIGHTNESS, param1=0..100 integer string.
                    - Camera -> OPEN_CAMERA.
                    - Settings -> OPEN_SETTINGS, param1=wifi|bluetooth|display|sound|main.
                    - Calendar -> OPEN_CALENDAR.
                    - Notes -> ADD_NOTE, param1=title, param2=content.
                    - To-Do -> ADD_TODO, param1=task title, param2=LOW|NORMAL|HIGH.
                    - Reminder -> ADD_REMINDER, param1=reminder title, param2=minutes from now (e.g. "30") or HH:mm.
                    - Remember user preference ("Remember I prefer...", "मेरा नाम ... है") -> SAVE_PREFERENCE, param1=short key, param2=value. Explain that user confirmation is requested before saving.
                    - Weather -> GET_WEATHER, param1=city name if mentioned else empty.
                    - News -> GET_NEWS, param1=topic if mentioned else empty.
                    - General knowledge, math, explanations, Hindi<->English translation -> action=NONE with thorough displayResponse and concise spokenResponse.
                    """.trimIndent()
                )
            }
        )
    }

    suspend fun processQuery(
        userInput: String,
        history: List<ChatMessageEntity>,
        preferences: List<UserPreference>,
        snapshot: DeviceSnapshot,
        preferredLanguage: AssistantLanguage,
        preferredCity: String
    ): Result<NovaDirective> {
        return try {
            val nowStr = ZonedDateTime.now().format(
                DateTimeFormatter.ofPattern("EEEE, yyyy-MM-dd HH:mm z", Locale.ENGLISH)
            )
            val prefBlock = if (preferences.isEmpty()) {
                "None saved yet."
            } else {
                preferences.joinToString("; ") { "${it.prefKey}: ${it.prefValue}" }
            }

            val contextHeader = """
                [LIVE DEVICE CONTEXT]
                Current DateTime: $nowStr
                Battery: ${snapshot.batteryPercent}% (charging=${snapshot.isCharging})
                Flashlight: ${if (snapshot.torchOn) "ON" else "OFF"}
                Media Volume: ${snapshot.mediaVolume}/${snapshot.maxMediaVolume}
                Preferred City: $preferredCity
                User Recognition Mode: ${preferredLanguage.label}
                Approved User Memories: $prefBlock
                [USER REQUEST]
                $userInput
            """.trimIndent()

            val chatHistory: List<Content> = history.takeLast(10).map { msg ->
                val role = if (msg.sender == "USER") "user" else "model"
                content(role) { text(msg.text) }
            }

            val response = withTimeout(60_000L) {
                if (chatHistory.isNotEmpty()) {
                    val chat = model.startChat(history = chatHistory)
                    chat.sendMessage(contextHeader)
                } else {
                    model.generateContent(contextHeader)
                }
            }

            val jsonStr = response.text
                ?: throw IllegalStateException("NOVA AI returned an empty response.")
            val json = JSONObject(jsonStr)
            val actionRaw = json.optString("action", "NONE")
            val action = NovaActionType.entries.firstOrNull { it.name == actionRaw }
                ?: NovaActionType.NONE
            val lang = AssistantLanguage.fromCode(json.optString("language", "en"))
            val spoken = json.optString("spokenResponse").ifBlank { json.optString("displayResponse") }
            val display = json.optString("displayResponse").ifBlank { spoken }

            Result.success(
                NovaDirective(
                    action = action,
                    param1 = json.optString("param1", "").trim(),
                    param2 = json.optString("param2", "").trim(),
                    language = lang,
                    spokenResponse = spoken,
                    displayResponse = display
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini generation timed out", e)
            Result.failure(Exception("Response timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI Logic error", e)
            Result.failure(e)
        }
    }

    private companion object {
        const val TAG = "NovaAiEngine"
    }
}
