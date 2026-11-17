package com.example.data

import android.util.Log
import android.util.Xml
import com.example.model.NewsArticle
import com.example.model.WeatherInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class WeatherAndNewsRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchWeatherByCity(city: String): Result<WeatherInfo> = withContext(Dispatchers.IO) {
        try {
            val encodedCity = URLEncoder.encode(city.trim().ifBlank { "New Delhi" }, "UTF-8")
            val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=$encodedCity&count=1&language=en&format=json"
            val geoJson = executeGet(geoUrl)
            val results = JSONObject(geoJson).optJSONArray("results")
            if (results == null || results.length() == 0) {
                return@withContext Result.failure(IllegalArgumentException("City \"$city\" not found."))
            }
            val first = results.getJSONObject(0)
            val lat = first.getDouble("latitude")
            val lon = first.getDouble("longitude")
            val resolvedName = listOfNotNull(
                first.optString("name").takeIf { it.isNotBlank() },
                first.optString("country_code").takeIf { it.isNotBlank() }
            ).joinToString(", ")

            fetchWeatherByCoordinates(lat, lon, resolvedName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed fetching weather by city", e)
            Result.failure(e)
        }
    }

    suspend fun fetchWeatherByCoordinates(
        latitude: Double,
        longitude: Double,
        displayCity: String = "Current Location"
    ): Result<WeatherInfo> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$latitude&longitude=$longitude" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,weather_code,wind_speed_10m" +
                "&timezone=auto"
            val body = executeGet(url)
            val root = JSONObject(body)
            val current = root.getJSONObject("current")
            val wmoCode = current.optInt("weather_code", 0)
            val (condEn, condHi) = mapWmoCode(wmoCode)

            Result.success(
                WeatherInfo(
                    city = displayCity,
                    temperatureC = current.optDouble("temperature_2m", 0.0),
                    apparentTempC = current.optDouble("apparent_temperature", 0.0),
                    humidityPercent = current.optInt("relative_humidity_2m", 0),
                    windKmh = current.optDouble("wind_speed_10m", 0.0),
                    conditionEn = condEn,
                    conditionHi = condHi,
                    isDay = current.optInt("is_day", 1) == 1,
                    fetchedAtMillis = System.currentTimeMillis()
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed fetching weather coordinates", e)
            Result.failure(e)
        }
    }

    suspend fun fetchHeadlines(hindi: Boolean = false, query: String? = null): Result<List<NewsArticle>> =
        withContext(Dispatchers.IO) {
            try {
                val url = when {
                    !query.isNullOrBlank() -> {
                        val q = URLEncoder.encode(query.trim(), "UTF-8")
                        if (hindi) "https://news.google.com/rss/search?q=$q&hl=hi&gl=IN&ceid=IN:hi"
                        else "https://news.google.com/rss/search?q=$q&hl=en-IN&gl=IN&ceid=IN:en"
                    }
                    hindi -> "https://news.google.com/rss?hl=hi&gl=IN&ceid=IN:hi"
                    else -> "https://news.google.com/rss?hl=en-IN&gl=IN&ceid=IN:en"
                }
                val xml = executeGet(url)
                val articles = parseRssFeed(xml).take(10)
                Result.success(articles)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed fetching news RSS", e)
                Result.failure(e)
            }
        }

    private fun executeGet(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "NOVA-AI-Android/1.0")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}: ${response.message}")
            }
            return response.body?.string() ?: throw IllegalStateException("Empty response body")
        }
    }

    private fun parseRssFeed(xml: String): List<NewsArticle> {
        val articles = mutableListOf<NewsArticle>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))

        var eventType = parser.eventType
        var insideItem = false
        var title = ""
        var link = ""
        var pubDate = ""
        var source = "News"

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (tagName.equals("item", ignoreCase = true)) {
                        insideItem = true
                        title = ""
                        link = ""
                        pubDate = ""
                        source = "News"
                    } else if (insideItem) {
                        when {
                            tagName.equals("title", ignoreCase = true) -> title = parser.nextText().trim()
                            tagName.equals("link", ignoreCase = true) -> link = parser.nextText().trim()
                            tagName.equals("pubDate", ignoreCase = true) -> pubDate = parser.nextText().trim()
                            tagName.equals("source", ignoreCase = true) -> source = parser.nextText().trim()
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (tagName.equals("item", ignoreCase = true) && insideItem) {
                        if (title.isNotBlank()) {
                            articles.add(
                                NewsArticle(
                                    title = title.substringBeforeLast(" - ").ifBlank { title },
                                    source = source,
                                    link = link,
                                    pubDate = pubDate.take(22)
                                )
                            )
                        }
                        insideItem = false
                    }
                }
            }
            eventType = parser.next()
        }
        return articles
    }

    private fun mapWmoCode(code: Int): Pair<String, String> = when (code) {
        0 -> "Clear Sky" to "साफ़ आसमान"
        1, 2 -> "Partly Cloudy" to "हल्के बादल"
        3 -> "Overcast" to "घने बादल"
        45, 48 -> "Foggy" to "कोहरा"
        51, 53, 55 -> "Light Drizzle" to "हल्की बूंदाबांदी"
        61, 63, 65 -> "Rain" to "बारिश"
        71, 73, 75 -> "Snowfall" to "बर्फबारी"
        80, 81, 82 -> "Rain Showers" to "तेज़ बौछारें"
        95, 96, 99 -> "Thunderstorm" to "आंधी-तूफान"
        else -> "Moderate Conditions" to "सामान्य मौसम"
    }

    private companion object {
        const val TAG = "WeatherNewsRepo"
    }
}
