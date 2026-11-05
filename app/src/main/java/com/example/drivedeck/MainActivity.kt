package com.example.drivedeck

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import android.provider.Settings
import android.app.NotificationManager
import android.content.ComponentName
import android.view.WindowInsets
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.drivedeck.lyrics.MediaNotificationListener
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8, 11, 18)
        window.navigationBarColor = android.graphics.Color.rgb(8, 11, 18)
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            addJavascriptInterface(SettingsBridge(), "DriveDeck")
            loadUrl("file:///android_asset/home.html")
        }
        setContentView(webView)
        webView.setOnApplyWindowInsetsListener { view, insets ->
            val top = if (android.os.Build.VERSION.SDK_INT >= 30) {
                insets.getInsets(WindowInsets.Type.statusBars()).top
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetTop
            }
            view.setPadding(0, top, 0, 0)
            insets
        }
        webView.requestApplyInsets()
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) {
            webView.post { webView.evaluateJavascript("if(window.refreshNotificationAccess){refreshNotificationAccess()}", null) }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == WEATHER_LOCATION_REQUEST) {
            if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
                WeatherRepository.refresh(this) { _, message ->
                    runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
                }
            } else {
                Toast.makeText(this, "Location permission is needed for local weather", Toast.LENGTH_LONG).show()
            }
        }
    }

    inner class SettingsBridge {
        @JavascriptInterface
        fun requestWeatherLocation() {
            runOnUiThread {
                val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (fine || coarse) {
                    WeatherRepository.refresh(this@MainActivity) { _, message ->
                        runOnUiThread { Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show() }
                    }
                } else {
                    requestPermissions(
                        arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
                        WEATHER_LOCATION_REQUEST
                    )
                }
            }
        }

        @JavascriptInterface
        fun getWeatherState(): String {
            val prefs = getSharedPreferences("drivedeck_settings", MODE_PRIVATE)
            val permissionGranted =
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            return JSONObject()
                .put("permissionGranted", permissionGranted)
                .put("ready", prefs.getBoolean("weatherReady", false))
                .put("temperature", prefs.getString("weatherTemperature", "").orEmpty())
                .put("description", prefs.getString("weatherDescription", "").orEmpty())
                .put("location", prefs.getString("weatherLocation", "").orEmpty())
                .put("updatedAt", prefs.getLong("weatherUpdatedAt", 0L))
                .toString()
        }

        @JavascriptInterface
        fun openNotificationSettings() {
            runOnUiThread {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }

        @JavascriptInterface
        fun getNotificationAccessState(): String {
            val component = ComponentName(this@MainActivity, MediaNotificationListener::class.java)
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val granted = if (android.os.Build.VERSION.SDK_INT >= 27) {
                manager.isNotificationListenerAccessGranted(component)
            } else {
                Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
                    ?.contains(component.flattenToString()) == true
            }
            val prefs = getSharedPreferences(MediaNotificationListener.PREFS, MODE_PRIVATE)
            return JSONObject()
                .put("granted", granted)
                .put("listenerConnected", prefs.getBoolean("listenerConnected", false))
                .put("lastDetectedPackage", prefs.getString("lastDetectedPackage", "").orEmpty())
                .toString()
        }

        @JavascriptInterface
        fun getSetting(key: String): String {
            return getSharedPreferences("drivedeck_settings", MODE_PRIVATE).getString(key, "").orEmpty()
        }

        @JavascriptInterface
        fun setSetting(key: String, value: String) {
            if (key !in setOf("source", "lyrics", "metadata", "dashboardLayout")) return
            getSharedPreferences("drivedeck_settings", MODE_PRIVATE).edit().putString(key, value).apply()
        }

        @JavascriptInterface
        fun getState(): String {
            val prefs = getSharedPreferences(MediaNotificationListener.PREFS, MODE_PRIVATE)
            val title = prefs.getString("title", "").orEmpty()
            val artist = prefs.getString("artist", "").orEmpty()
            val pkg = prefs.getString("package", "").orEmpty()
            val synced = prefs.getString("syncedLyrics", "").orEmpty()
            val plain = prefs.getString("lyrics", "").orEmpty()
            val lines = if (synced.isNotBlank()) {
                Regex("\\[\\d{1,2}:\\d{2}(?:\\.\\d{1,3})?]([^\\r\\n]*)")
                    .findAll(synced).map { it.groupValues[1].trim() }.filter { it.isNotBlank() }.toList()
            } else plain.lines().map { it.trim() }.filter { it.isNotBlank() }
            val source = when (pkg) {
                "com.spotify.music" -> "SPOTIFY CONNECTED"
                "com.google.android.apps.youtube.music", "com.google.android.youtube" -> "YOUTUBE MUSIC CONNECTED"
                else -> ""
            }
            return JSONObject()
                .put("title", title)
                .put("artist", artist)
                .put("source", source)
                .put("hasLyrics", lines.isNotEmpty())
                .put("hasSynced", synced.isNotBlank())
                .put("lines", JSONArray(lines.take(3)))
                .toString()
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    companion object { private const val WEATHER_LOCATION_REQUEST = 4102 }
}
