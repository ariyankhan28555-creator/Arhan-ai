package com.example.ai

import com.example.model.AssistantLanguage
import com.example.model.DeviceSnapshot
import java.text.DecimalFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.pow

enum class NovaActionType {
    NONE,
    OPEN_APP,
    CLOSE_APP_INFO,
    MAKE_CALL,
    SEND_SMS,
    SET_ALARM,
    SET_TIMER,
    FLASHLIGHT_ON,
    FLASHLIGHT_OFF,
    CHECK_BATTERY,
    CHECK_DATETIME,
    VOLUME_UP,
    VOLUME_DOWN,
    VOLUME_MUTE,
    SET_BRIGHTNESS,
    OPEN_CAMERA,
    OPEN_SETTINGS,
    ADD_NOTE,
    ADD_TODO,
    ADD_REMINDER,
    SAVE_PREFERENCE,
    GET_WEATHER,
    GET_NEWS,
    OPEN_CALENDAR
}

data class NovaDirective(
    val action: NovaActionType = NovaActionType.NONE,
    val param1: String = "",
    val param2: String = "",
    val language: AssistantLanguage = AssistantLanguage.ENGLISH,
    val spokenResponse: String = "",
    val displayResponse: String = ""
)

object OfflineFallbackEngine {

    fun evaluate(
        input: String,
        snapshot: DeviceSnapshot,
        preferredLanguage: AssistantLanguage
    ): NovaDirective {
        val raw = input.trim()
        val lower = raw.lowercase(Locale.ROOT)
        val isHindiOrHinglish = containsDevanagari(raw) ||
            preferredLanguage == AssistantLanguage.HINDI ||
            listOf("hai", "kya", "kitna", "kholo", "batao", "karo", "chalu", "band", "samay", "mausam").any { lower.contains(it) }

        val lang = when {
            containsDevanagari(raw) -> AssistantLanguage.HINDI
            isHindiOrHinglish -> AssistantLanguage.HINGLISH
            else -> AssistantLanguage.ENGLISH
        }

        // 1. Battery
        if (lower.contains("battery") || lower.contains("बैटरी") || lower.contains("charge")) {
            val chargingStr = if (snapshot.isCharging) {
                if (lang == AssistantLanguage.HINDI) " और फोन चार्ज हो रहा है" else " (Charging)"
            } else ""
            val reply = if (lang == AssistantLanguage.HINDI || lang == AssistantLanguage.HINGLISH) {
                "आपके फोन की बैटरी ${snapshot.batteryPercent} प्रतिशत है$chargingStr।"
            } else {
                "Your phone battery is at ${snapshot.batteryPercent}%$chargingStr."
            }
            return NovaDirective(NovaActionType.CHECK_BATTERY, language = lang, spokenResponse = reply, displayResponse = reply)
        }

        // 2. Flashlight
        if (lower.contains("flashlight") || lower.contains("torch") || lower.contains("टॉर्च") || lower.contains("फ़्लैशलाइट")) {
            val turnOff = lower.contains("off") || lower.contains("band") || lower.contains("बंद") || lower.contains("disable")
            return if (turnOff) {
                val msg = if (lang == AssistantLanguage.ENGLISH) "Turning off the flashlight." else "जी, टॉर्च बंद कर रही हूँ।"
                NovaDirective(NovaActionType.FLASHLIGHT_OFF, language = lang, spokenResponse = msg, displayResponse = msg)
            } else {
                val msg = if (lang == AssistantLanguage.ENGLISH) "Turning on the flashlight." else "जी, टॉर्च चालू कर रही हूँ।"
                NovaDirective(NovaActionType.FLASHLIGHT_ON, language = lang, spokenResponse = msg, displayResponse = msg)
            }
        }

        // 3. Date & Time
        if (lower.contains("time") || lower.contains("date") || lower.contains("samay") ||
            lower.contains("समय") || lower.contains("तारीख") || lower.contains("baje") || lower.contains("आज क्या")
        ) {
            val now = LocalDateTime.now()
            val locale = if (lang == AssistantLanguage.HINDI) Locale("hi", "IN") else Locale.ENGLISH
            val formatted = now.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy, hh:mm a", locale))
            val msg = if (lang == AssistantLanguage.ENGLISH) "It is $formatted." else "अभी का समय और तारीख है: $formatted।"
            return NovaDirective(NovaActionType.CHECK_DATETIME, language = lang, spokenResponse = msg, displayResponse = msg)
        }

        // 4. Camera / Settings
        if (lower.contains("camera") || lower.contains("कैमरा")) {
            val msg = if (lang == AssistantLanguage.ENGLISH) "Opening camera." else "जी, कैमरा खोल रही हूँ।"
            return NovaDirective(NovaActionType.OPEN_CAMERA, language = lang, spokenResponse = msg, displayResponse = msg)
        }
        if (lower.contains("setting") || lower.contains("सेटिंग")) {
            val msg = if (lang == AssistantLanguage.ENGLISH) "Opening settings." else "जी, सेटिंग्स खोल रही हूँ।"
            return NovaDirective(NovaActionType.OPEN_SETTINGS, language = lang, spokenResponse = msg, displayResponse = msg)
        }

        // 5. Volume
        if (lower.contains("volume") || lower.contains("आवाज़") || lower.contains("awaz")) {
            return when {
                lower.contains("mute") || lower.contains("silent") || lower.contains("बंद") -> {
                    val msg = if (lang == AssistantLanguage.ENGLISH) "Muting media volume." else "मीडिया वॉल्यूम म्यूट कर रही हूँ।"
                    NovaDirective(NovaActionType.VOLUME_MUTE, language = lang, spokenResponse = msg, displayResponse = msg)
                }
                lower.contains("down") || lower.contains("decrease") || lower.contains("kam") || lower.contains("कम") -> {
                    val msg = if (lang == AssistantLanguage.ENGLISH) "Lowering the volume." else "वॉल्यूम कम कर रही हूँ।"
                    NovaDirective(NovaActionType.VOLUME_DOWN, language = lang, spokenResponse = msg, displayResponse = msg)
                }
                else -> {
                    val msg = if (lang == AssistantLanguage.ENGLISH) "Increasing the volume." else "वॉल्यूम बढ़ा रही हूँ।"
                    NovaDirective(NovaActionType.VOLUME_UP, language = lang, spokenResponse = msg, displayResponse = msg)
                }
            }
        }

        // 6. Open App (e.g., "YouTube खोलो", "open chrome")
        if (lower.startsWith("open ") || lower.endsWith("खोलो") || lower.endsWith("kholo")) {
            val appQuery = raw
                .replace(Regex("(?i)^open\\s+"), "")
                .replace(Regex("(?i)\\s*(खोलो|kholo|app)$"), "")
                .trim()
            if (appQuery.isNotEmpty()) {
                val msg = if (lang == AssistantLanguage.ENGLISH) "Opening $appQuery." else "जी, $appQuery खोल रही हूँ।"
                return NovaDirective(NovaActionType.OPEN_APP, param1 = appQuery, language = lang, spokenResponse = msg, displayResponse = msg)
            }
        }

        // 7. Weather & News
        if (lower.contains("weather") || lower.contains("mausam") || lower.contains("मौसम")) {
            val msg = if (lang == AssistantLanguage.ENGLISH) "Fetching today's weather update." else "आज के मौसम की जानकारी प्राप्त कर रही हूँ।"
            return NovaDirective(NovaActionType.GET_WEATHER, language = lang, spokenResponse = msg, displayResponse = msg)
        }
        if (lower.contains("news") || lower.contains("headlines") || lower.contains("समाचार") || lower.contains("खबर")) {
            val msg = if (lang == AssistantLanguage.ENGLISH) "Fetching the latest news headlines." else "ताज़ा समाचार ला रही हूँ।"
            return NovaDirective(NovaActionType.GET_NEWS, language = lang, spokenResponse = msg, displayResponse = msg)
        }

        // 8. Local Math Evaluator (works 100% offline!)
        val mathResult = tryEvaluateMath(raw)
        if (mathResult != null) {
            val formatted = DecimalFormat("#.######").format(mathResult)
            val msg = if (lang == AssistantLanguage.ENGLISH) "The answer is $formatted." else "इसका उत्तर $formatted है।"
            return NovaDirective(NovaActionType.NONE, language = lang, spokenResponse = msg, displayResponse = "$raw = $formatted")
        }

        // 9. Offline greeting / fallback message
        val fallback = if (lang == AssistantLanguage.ENGLISH) {
            "I'm currently in offline fallback mode. I can still check battery, date/time, toggle flashlight, adjust volume, open apps, or solve math expressions. Reconnect to the internet for full Gemini AI intelligence."
        } else {
            "अभी इंटरनेट उपलब्ध नहीं है, इसलिए मैं ऑफ़लाइन मोड में हूँ। आप मुझसे बैटरी, समय, टॉर्च, वॉल्यूम, ऐप्स खोलने या गणित के सवाल पूछ सकते हैं।"
        }
        return NovaDirective(NovaActionType.NONE, language = lang, spokenResponse = fallback, displayResponse = fallback)
    }

    fun containsDevanagari(text: String): Boolean =
        text.any { it in '\u0900'..'\u097F' }

    fun tryEvaluateMath(input: String): Double? {
        val normalized = input.lowercase(Locale.ROOT)
            .replace("what is", "")
            .replace("calculate", "")
            .replace("solve", "")
            .replace("कितना होता है", "")
            .replace("का मान", "")
            .replace("×", "*")
            .replace("÷", "/")
            .replace("plus", "+")
            .replace("जोड़", "+")
            .replace("minus", "-")
            .replace("घटा", "-")
            .replace("into", "*")
            .replace("multiplied by", "*")
            .replace("times", "*")
            .replace("गुणा", "*")
            .replace("divided by", "/")
            .replace("divide by", "/")
            .replace("भागा", "/")
            .replace("x", "*")
            .replace(Regex("[^0-9.+\\-*/^() ]"), " ")
            .trim()

        if (normalized.isBlank() || !normalized.any { it.isDigit() } || !normalized.any { it in "+-*/^" }) {
            return null
        }

        return runCatching { MathParser(normalized).parse() }.getOrNull()
    }

    private class MathParser(private val str: String) {
        private var pos = -1
        private var ch = 0

        private fun nextChar() {
            ch = if (++pos < str.length) str[pos].code else -1
        }

        private fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            if (pos < str.length) throw IllegalArgumentException("Unexpected char")
            return x
        }

        private fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                when {
                    eat('+'.code) -> x += parseTerm()
                    eat('-'.code) -> x -= parseTerm()
                    else -> return x
                }
            }
        }

        private fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                when {
                    eat('*'.code) -> x *= parseFactor()
                    eat('/'.code) -> {
                        val d = parseFactor()
                        if (d == 0.0) throw ArithmeticException("Division by zero")
                        x /= d
                    }
                    else -> return x
                }
            }
        }

        private fun parseFactor(): Double {
            if (eat('+'.code)) return parseFactor()
            if (eat('-'.code)) return -parseFactor()
            var x: Double
            val startPos = pos
            if (eat('('.code)) {
                x = parseExpression()
                if (!eat(')'.code)) throw IllegalArgumentException("Missing ')'")
            } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                x = str.substring(startPos, pos).toDouble()
            } else {
                throw IllegalArgumentException("Unexpected char")
            }
            if (eat('^'.code)) x = x.pow(parseFactor())
            return x
        }
    }
}
