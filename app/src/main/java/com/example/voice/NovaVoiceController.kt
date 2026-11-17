package com.example.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.ai.OfflineFallbackEngine
import com.example.model.AssistantLanguage
import com.example.model.VoiceGender
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

class NovaVoiceController(
    context: Context,
    private val onFinalSpeech: (transcript: String, isWakeWordTrigger: Boolean) -> Unit,
    private val onSpeechError: (message: String) -> Unit
) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = TextToSpeech(appContext, this)
    private var ttsInitialized = false

    private var isWakeWordPass = false
    private var wakeWordEnabled = false
    private var continuousModeEnabled = false
    private var currentLanguage = AssistantLanguage.HINGLISH
    private var currentVoiceGender = VoiceGender.FEMALE
    private var currentSpeechRate = 1.0f

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isWakeWordListening = MutableStateFlow(false)
    val isWakeWordListening: StateFlow<Boolean> = _isWakeWordListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _speechAvailable = MutableStateFlow(SpeechRecognizer.isRecognitionAvailable(appContext))
    val speechAvailable: StateFlow<Boolean> = _speechAvailable.asStateFlow()

    fun updatePreferences(
        language: AssistantLanguage,
        voiceGender: VoiceGender,
        speechRate: Float,
        wakeWord: Boolean,
        continuousMode: Boolean
    ) {
        currentLanguage = language
        currentVoiceGender = voiceGender
        currentSpeechRate = speechRate
        val wakeChanged = wakeWordEnabled != wakeWord
        wakeWordEnabled = wakeWord
        continuousModeEnabled = continuousMode

        if (wakeChanged) {
            if (wakeWord && !_isListening.value && !_isSpeaking.value) {
                startWakeWordLoopIfPermitted()
            } else if (!wakeWord && _isWakeWordListening.value) {
                stopListening()
            }
        }
    }

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun startActiveListening() {
        if (!hasMicPermission()) {
            onSpeechError("Microphone permission is required for voice commands.")
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            onSpeechError("Speech recognition service is not available on this device.")
            return
        }
        stopSpeaking()
        mainHandler.post {
            beginRecognizerSession(wakePass = false)
        }
    }

    fun startWakeWordLoopIfPermitted() {
        if (!wakeWordEnabled || !hasMicPermission() || !SpeechRecognizer.isRecognitionAvailable(appContext)) {
            return
        }
        if (_isListening.value || _isSpeaking.value) return
        mainHandler.post {
            beginRecognizerSession(wakePass = true)
        }
    }

    fun stopListening() {
        mainHandler.post {
            _isListening.value = false
            _isWakeWordListening.value = false
            _rmsLevel.value = 0f
            _partialTranscript.value = ""
            runCatching { speechRecognizer?.cancel() }
            runCatching { speechRecognizer?.destroy() }
            speechRecognizer = null
        }
    }

    private fun beginRecognizerSession(wakePass: Boolean) {
        runCatching { speechRecognizer?.destroy() }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
            setRecognitionListener(createRecognitionListener())
        }
        isWakeWordPass = wakePass
        _isListening.value = !wakePass
        _isWakeWordListening.value = wakePass
        _partialTranscript.value = ""
        _rmsLevel.value = 0f

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguage.bcp47)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
        }
        runCatching {
            speechRecognizer?.startListening(intent)
        }.onFailure { err ->
            Log.e(TAG, "Failed to start SpeechRecognizer", err)
            _isListening.value = false
            _isWakeWordListening.value = false
            if (!wakePass) onSpeechError("Could not start voice recognition.")
        }
    }

    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {
            // Normalize typical SpeechRecognizer rmsdB (-2..10+) to 0f..1f
            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            _rmsLevel.value = normalized
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            _rmsLevel.value = 0f
        }

        override fun onError(error: Int) {
            val wasWakePass = isWakeWordPass
            _isListening.value = false
            _isWakeWordListening.value = false
            _rmsLevel.value = 0f

            if (wasWakePass) {
                if (wakeWordEnabled && !_isSpeaking.value) {
                    mainHandler.postDelayed({ startWakeWordLoopIfPermitted() }, 650L)
                }
                return
            }

            val message = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that. Please try speaking again."
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    "Speech recognition network error."
                else -> "Voice recognition interrupted (code $error)."
            }
            if (error != SpeechRecognizer.ERROR_CLIENT) {
                onSpeechError(message)
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val best = matches.firstOrNull().orEmpty().trim()
            val wasWakePass = isWakeWordPass
            _isListening.value = false
            _isWakeWordListening.value = false
            _rmsLevel.value = 0f
            _partialTranscript.value = ""

            if (best.isBlank()) {
                if (wasWakePass && wakeWordEnabled) {
                    mainHandler.postDelayed({ startWakeWordLoopIfPermitted() }, 500L)
                }
                return
            }

            if (wasWakePass) {
                val wakeRegex = Regex("(?i)\\b(hey\\s+nova|ok\\s+nova|nova|हे\\s*नोवा|नोवा)\\b[,:\\s]*")
                val match = wakeRegex.find(best)
                if (match != null) {
                    val remainder = best.substring(match.range.last + 1).trim()
                    onFinalSpeech(remainder, true)
                } else if (wakeWordEnabled) {
                    mainHandler.postDelayed({ startWakeWordLoopIfPermitted() }, 400L)
                }
            } else {
                onFinalSpeech(best, false)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (!isWakeWordPass) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                _partialTranscript.value = partial
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    // --- TEXT TO SPEECH ---
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsInitialized = true
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    mainHandler.post {
                        if (continuousModeEnabled && hasMicPermission()) {
                            startActiveListening()
                        } else if (wakeWordEnabled && hasMicPermission()) {
                            startWakeWordLoopIfPermitted()
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                }
            })
        } else {
            Log.e(TAG, "TTS initialization failed with status=$status")
        }
    }

    fun speak(text: String, hintLanguage: AssistantLanguage? = null) {
        val clean = text.trim()
        if (clean.isEmpty() || !ttsInitialized) return
        val tts = textToSpeech ?: return

        // Pause wake-word recognizer while NOVA speaks so it doesn't hear itself
        if (_isWakeWordListening.value) {
            stopListening()
        }

        val isHindiText = OfflineFallbackEngine.containsDevanagari(clean) ||
            hintLanguage == AssistantLanguage.HINDI
        val targetLocale = if (isHindiText) Locale("hi", "IN") else Locale("en", "IN")
        val langResult = tts.setLanguage(targetLocale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.US)
        }

        // Select female/male voice when available on the device TTS engine
        val voices = runCatching { tts.voices }.getOrNull().orEmpty()
        val localeVoices = voices.filter {
            it.locale.language.equals(targetLocale.language, ignoreCase = true)
        }
        val preferredVoice = when (currentVoiceGender) {
            VoiceGender.FEMALE -> localeVoices.firstOrNull {
                val n = it.name.lowercase(Locale.ROOT)
                n.contains("female") || n.contains("-f-") || n.contains("hin-x-hie") || n.contains("sfg") || n.contains("ena")
            } ?: localeVoices.firstOrNull()
            VoiceGender.MALE -> localeVoices.firstOrNull {
                val n = it.name.lowercase(Locale.ROOT)
                n.contains("male") || n.contains("-m-") || n.contains("hid") || n.contains("sfb") || n.contains("iom")
            } ?: localeVoices.lastOrNull()
        }
        if (preferredVoice != null) {
            runCatching { tts.voice = preferredVoice }
        }

        val pitch = if (currentVoiceGender == VoiceGender.MALE) 0.86f else 1.04f
        tts.setPitch(pitch)
        tts.setSpeechRate(currentSpeechRate)

        val utteranceId = UUID.randomUUID().toString()
        tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stopSpeaking() {
        runCatching { textToSpeech?.stop() }
        _isSpeaking.value = false
    }

    fun release() {
        stopListening()
        runCatching {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        }
        textToSpeech = null
    }

    private companion object {
        const val TAG = "NovaVoiceController"
    }
}
