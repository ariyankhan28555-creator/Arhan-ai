package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AssistantLanguage
import com.example.model.NovaSettings
import com.example.model.VoiceGender
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.novaDataStore: DataStore<Preferences> by preferencesDataStore(name = "nova_settings")

class SettingsDataStore(context: Context) {
    private val appContext = context.applicationContext

    private object Keys {
        val VOICE_GENDER = stringPreferencesKey("voice_gender")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val RECOGNITION_LANG = stringPreferencesKey("recognition_language")
        val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        val CONTINUOUS_MODE = booleanPreferencesKey("continuous_mode_enabled")
        val PREFERRED_CITY = stringPreferencesKey("preferred_city")
    }

    val settingsFlow: Flow<NovaSettings> = appContext.novaDataStore.data.map { prefs ->
        NovaSettings(
            voiceGender = runCatching {
                VoiceGender.valueOf(prefs[Keys.VOICE_GENDER] ?: VoiceGender.FEMALE.name)
            }.getOrDefault(VoiceGender.FEMALE),
            speechRate = (prefs[Keys.SPEECH_RATE] ?: 1.0f).coerceIn(0.6f, 1.8f),
            recognitionLanguage = AssistantLanguage.fromCode(prefs[Keys.RECOGNITION_LANG]),
            wakeWordEnabled = prefs[Keys.WAKE_WORD_ENABLED] ?: false,
            continuousModeEnabled = prefs[Keys.CONTINUOUS_MODE] ?: false,
            preferredCity = prefs[Keys.PREFERRED_CITY]?.takeIf { it.isNotBlank() } ?: "New Delhi"
        )
    }

    suspend fun setVoiceGender(gender: VoiceGender) {
        appContext.novaDataStore.edit { it[Keys.VOICE_GENDER] = gender.name }
    }

    suspend fun setSpeechRate(rate: Float) {
        appContext.novaDataStore.edit { it[Keys.SPEECH_RATE] = rate.coerceIn(0.6f, 1.8f) }
    }

    suspend fun setRecognitionLanguage(language: AssistantLanguage) {
        appContext.novaDataStore.edit { it[Keys.RECOGNITION_LANG] = language.code }
    }

    suspend fun setWakeWordEnabled(enabled: Boolean) {
        appContext.novaDataStore.edit { it[Keys.WAKE_WORD_ENABLED] = enabled }
    }

    suspend fun setContinuousModeEnabled(enabled: Boolean) {
        appContext.novaDataStore.edit { it[Keys.CONTINUOUS_MODE] = enabled }
    }

    suspend fun setPreferredCity(city: String) {
        val clean = city.trim().take(60)
        if (clean.isNotEmpty()) {
            appContext.novaDataStore.edit { it[Keys.PREFERRED_CITY] = clean }
        }
    }
}
