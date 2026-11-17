package com.example.device

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.Location
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.model.CalendarEventItem
import com.example.model.DeviceSnapshot
import com.example.model.InstalledAppInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

class DeviceController(context: Context) {

    private val appContext = context.applicationContext
    private val batteryManager = appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val cameraManager = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _snapshot = MutableStateFlow(readSnapshot())
    val snapshot: StateFlow<DeviceSnapshot> = _snapshot.asStateFlow()

    private val torchCameraId: String? by lazy {
        runCatching {
            cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val isBack = chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                hasFlash && isBack
            } ?: cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
    }

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchCameraId) {
                _snapshot.update { it.copy(torchOn = enabled) }
            }
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshSnapshot()
        }
    }

    init {
        runCatching {
            cameraManager.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper()))
        }
        runCatching {
            appContext.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }
    }

    fun release() {
        runCatching { cameraManager.unregisterTorchCallback(torchCallback) }
        runCatching { appContext.unregisterReceiver(batteryReceiver) }
    }

    fun refreshSnapshot(): DeviceSnapshot {
        val updated = readSnapshot()
        _snapshot.value = updated
        return updated
    }

    private fun readSnapshot(): DeviceSnapshot {
        val batteryIntent = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else {
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        }
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

        val sysBrightness = runCatching {
            Settings.System.getInt(appContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrDefault(128)
        val brightnessPct = ((sysBrightness / 255f) * 100).toInt().coerceIn(0, 100)

        val canWrite = Settings.System.canWrite(appContext)
        val online = isNetworkAvailable()

        return _snapshot.value.copy(
            batteryPercent = pct,
            isCharging = isCharging,
            mediaVolume = currentVol,
            maxMediaVolume = maxVol,
            brightnessPercent = brightnessPct,
            canWriteSystemSettings = canWrite,
            isOnline = online
        )
    }

    fun isNetworkAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun setTorch(enabled: Boolean): Result<Boolean> {
        val camId = torchCameraId
            ?: return Result.failure(IllegalStateException("Flashlight hardware is not available on this device."))
        return try {
            cameraManager.setTorchMode(camId, enabled)
            _snapshot.update { it.copy(torchOn = enabled) }
            Result.success(enabled)
        } catch (e: Exception) {
            Log.e(TAG, "Torch toggle failed", e)
            Result.failure(e)
        }
    }

    fun adjustVolume(direction: Int) {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            direction,
            AudioManager.FLAG_SHOW_UI
        )
        refreshSnapshot()
    }

    fun setMediaVolume(value: Int) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            value.coerceIn(0, max),
            AudioManager.FLAG_SHOW_UI
        )
        refreshSnapshot()
    }

    fun setBrightness(percent: Int, activity: Activity?): Boolean {
        val clamped = percent.coerceIn(5, 100)
        // Always update active window brightness immediately
        activity?.window?.let { win ->
            val lp = win.attributes
            lp.screenBrightness = clamped / 100f
            win.attributes = lp
        }
        val wroteSystem = if (Settings.System.canWrite(appContext)) {
            runCatching {
                val val255 = (clamped * 255) / 100
                Settings.System.putInt(
                    appContext.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
                Settings.System.putInt(
                    appContext.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    val255
                )
                true
            }.getOrDefault(false)
        } else {
            false
        }
        _snapshot.update { it.copy(brightnessPercent = clamped, canWriteSystemSettings = Settings.System.canWrite(appContext)) }
        return wroteSystem
    }

    fun openWriteSettingsPermissionScreen() {
        val intent = Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:${appContext.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
    }

    fun getInstalledLauncherApps(): List<InstalledAppInfo> {
        val pm = appContext.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }
        return resolveList
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == appContext.packageName) return@mapNotNull null
                val label = info.loadLabel(pm).toString()
                InstalledAppInfo(label = label, packageName = pkg)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.ROOT) }
    }

    fun launchAppByName(query: String): Result<String> {
        val cleanQuery = query.trim().lowercase(Locale.ROOT)
        if (cleanQuery.isBlank()) return Result.failure(IllegalArgumentException("App name is empty"))

        val aliases = mapOf(
            "youtube" to listOf("youtube", "com.google.android.youtube"),
            "whatsapp" to listOf("whatsapp", "com.whatsapp"),
            "chrome" to listOf("chrome", "com.android.chrome"),
            "maps" to listOf("maps", "google maps", "com.google.android.apps.maps"),
            "gmail" to listOf("gmail", "com.google.android.gm"),
            "calculator" to listOf("calculator", "calc"),
            "clock" to listOf("clock", "alarm", "deskclock"),
            "camera" to listOf("camera"),
            "settings" to listOf("settings"),
            "calendar" to listOf("calendar"),
            "play store" to listOf("play store", "vending"),
            "spotify" to listOf("spotify", "com.spotify.music"),
            "instagram" to listOf("instagram", "com.instagram.android"),
            "phone" to listOf("phone", "dialer"),
            "messages" to listOf("messages", "messaging")
        )

        val apps = getInstalledLauncherApps()
        val candidateTerms = aliases[cleanQuery] ?: listOf(cleanQuery)
        val match = apps.firstOrNull { app ->
            val lbl = app.label.lowercase(Locale.ROOT)
            val pkg = app.packageName.lowercase(Locale.ROOT)
            candidateTerms.any { term -> lbl == term || lbl.contains(term) || pkg.contains(term) }
        }

        if (match != null) {
            val launchIntent = appContext.packageManager.getLaunchIntentForPackage(match.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(launchIntent)
                return Result.success(match.label)
            }
        }
        return Result.failure(IllegalStateException("App \"$query\" is not installed on this device."))
    }

    fun openHomeScreen() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        appContext.startActivity(intent)
    }

    fun openCamera(): Boolean = runCatching {
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
        true
    }.getOrElse {
        runCatching {
            val fallback = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(fallback)
            true
        }.getOrDefault(false)
    }

    fun openSettings(subtype: String = "main"): Boolean {
        val action = when (subtype.lowercase(Locale.ROOT)) {
            "wifi", "wi-fi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display", "brightness" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound", "volume" -> Settings.ACTION_SOUND_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return runCatching {
            appContext.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }

    fun openDialer(phoneNumber: String): Boolean = runCatching {
        val clean = phoneNumber.trim()
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
        true
    }.getOrDefault(false)

    fun openSmsComposer(phoneNumber: String, body: String): Boolean = runCatching {
        val clean = phoneNumber.trim()
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$clean")).apply {
            putExtra("sms_body", body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
        true
    }.getOrDefault(false)

    fun setAlarm(hour: Int, minute: Int, message: String): Boolean = runCatching {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour.coerceIn(0, 23))
            putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
            if (message.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
        true
    }.getOrDefault(false)

    fun setTimer(seconds: Int, message: String): Boolean = runCatching {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds.coerceAtLeast(1))
            if (message.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
        true
    }.getOrDefault(false)

    fun openUrl(url: String): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
        true
    }.getOrDefault(false)

    fun insertCalendarEvent(title: String): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            if (title.isNotBlank()) putExtra(CalendarContract.Events.TITLE, title)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
        true
    }.getOrDefault(false)

    fun lookupContactPhone(nameQuery: String): Pair<String, String>? {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val clean = nameQuery.trim()
        if (clean.isEmpty()) return null
        return runCatching {
            appContext.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$clean%"),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0) ?: clean
                    val number = cursor.getString(1) ?: ""
                    if (number.isNotBlank()) name to number else null
                } else null
            }
        }.getOrNull()
    }

    fun readUpcomingCalendarEvents(limit: Int = 8): List<CalendarEventItem> {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }
        val now = System.currentTimeMillis()
        val weekAhead = now + 7L * 24L * 3600L * 1000L
        val list = mutableListOf<CalendarEventItem>()
        runCatching {
            appContext.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(
                    CalendarContract.Events._ID,
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.DTEND,
                    CalendarContract.Events.EVENT_LOCATION
                ),
                "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?",
                arrayOf(now.toString(), weekAhead.toString()),
                "${CalendarContract.Events.DTSTART} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext() && list.size < limit) {
                    val id = cursor.getLong(0)
                    val title = cursor.getString(1) ?: "Untitled Event"
                    val start = cursor.getLong(2)
                    val end = cursor.getLong(3)
                    val loc = cursor.getString(4) ?: ""
                    list.add(CalendarEventItem(id, title, start, end, loc))
                }
            }
        }
        return list
    }

    fun getLastCoarseLocation(): Location? {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return runCatching {
            lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        }.getOrNull()
    }

    private companion object {
        const val TAG = "DeviceController"
    }
}
