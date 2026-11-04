package com.example.drivedeck

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
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
    }

    inner class SettingsBridge {
        @JavascriptInterface
        fun openNotificationSettings() {
            runOnUiThread {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
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
}
