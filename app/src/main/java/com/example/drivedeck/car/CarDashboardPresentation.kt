package com.example.drivedeck.car

import android.app.Presentation
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject

class CarDashboardPresentation(
    outerContext: Context,
    display: Display,
    initialDiagnostics: SurfaceDiagnostics
) : Presentation(outerContext, display, android.R.style.Theme_Black_NoTitleBar_Fullscreen) {
    @Volatile private var currentDiagnostics: SurfaceDiagnostics = initialDiagnostics
    private val dashboardPrefs: SharedPreferences by lazy {
        context.getSharedPreferences("drivedeck_settings", Context.MODE_PRIVATE)
    }
    private val lyricsPrefs: SharedPreferences by lazy {
        context.getSharedPreferences("drivedeck_lyrics", Context.MODE_PRIVATE)
    }
    private val dashboardPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "dashboardLayout" && !isCleanedUp) {
            Log.i(TAG, "Dashboard layout changed on phone; updating Android Auto without reconnect")
            updateDiagnostics(currentDiagnostics)
        }
    }
    private val weatherPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key?.startsWith("weather") == true && !isCleanedUp) updateDiagnostics(currentDiagnostics)
    }
    private val lyricsPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in setOf("title", "artist", "package", "positionMs", "playbackSpeed") && !isCleanedUp) {
            Log.i(TAG, "Media metadata changed; refreshing dashboard")
            updateDiagnostics(currentDiagnostics)
        }
    }
    private var webView: WebView? = null
    private var pageLoaded = false
    private var isCleanedUp = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "Presentation.onCreate: displayId=${display.displayId}, name=${display.name}")
        dashboardPrefs.registerOnSharedPreferenceChangeListener(dashboardPrefsListener)
        lyricsPrefs.registerOnSharedPreferenceChangeListener(lyricsPrefsListener)
        dashboardPrefs.registerOnSharedPreferenceChangeListener(weatherPrefsListener)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        try {
            val wv = WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(Color.rgb(8, 12, 19))
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    mediaPlaybackRequiresUserGesture = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                }
                addJavascriptInterface(PoCDashboardBridge(), BRIDGE_NAME)
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        consoleMessage?.let { Log.d(TAG, "WebView Console [${it.messageLevel()}]: ${it.message()} (line ${it.lineNumber()})") }
                        return true
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (isCleanedUp) return
                        pageLoaded = true
                        Log.i(TAG, "WebView.onPageFinished: url=$url")
                        pushDiagnosticsToWebView(currentDiagnostics)
                    }
                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                        super.onReceivedError(view, request, error)
                        Log.e(TAG, "WebView.onReceivedError: url=${request?.url}, code=${error?.errorCode}, desc=${error?.description}")
                    }
                }
                loadUrl(DASHBOARD_ASSET_URL)
            }
            webView = wv
            setContentView(wv)
        } catch (e: Exception) {
            Log.e(TAG, "Presentation.onCreate: Failed to initialize WebView", e)
        }
    }

    override fun onStart() { super.onStart(); Log.i(TAG, "Presentation.onStart: displayId=${display.displayId}") }
    override fun onStop() { Log.i(TAG, "Presentation.onStop: displayId=${display.displayId}"); super.onStop() }

    fun updateDiagnostics(diagnostics: SurfaceDiagnostics) {
        if (isCleanedUp) return
        currentDiagnostics = diagnostics
        if (pageLoaded) pushDiagnosticsToWebView(diagnostics)
    }

    fun reloadWebView() {
        if (isCleanedUp) return
        val wv = webView ?: return
        wv.post {
            if (isCleanedUp || webView == null) return@post
            pageLoaded = false
            Log.i(TAG, "Presentation.reloadWebView: Reloading existing WebView instance")
            wv.reload()
        }
    }

    private fun pushDiagnosticsToWebView(diag: SurfaceDiagnostics) {
        if (isCleanedUp) return
        val wv = webView ?: return
        val state = buildPoCStateJson(diag)
        wv.post { if (!isCleanedUp && pageLoaded && webView != null) wv.evaluateJavascript("render($state);", null) }
    }

    private fun buildPoCStateJson(diag: SurfaceDiagnostics): String {
        val lines = JSONArray()
            .put("Surface: ${diag.width}×${diag.height} px @ ${diag.dpi} dpi")
            .put("Visible: ${diag.visibleArea} | Stable: ${diag.stableArea}")
            .put("Display ID: ${diag.displayId} • Updates: ${diag.updateCount}")
        return JSONObject()
            .put("connected", true)
            .put("connectionText", "● DRIVEDECK CONNECTED")
            .put("title", "Custom Surface PoC Ready")
            .put("artist", "Resolution: ${diag.width}×${diag.height} px • DPI: ${diag.dpi}")
            .put("source", "VIRTUALDISPLAY + PRESENTATION + WEBVIEW")
            .put("elapsed", "VISIBLE ${diag.visibleArea}")
            .put("duration", "DPI ${diag.dpi}")
            .put("status", "PHASE 1 POC • CAR API ${diag.carApiLevel}")
            .put("playing", false)
            .put("artwork", "")
            .put("lines", lines)
            .put("dashboardLayout", readDashboardLayout())
            .put("musicTitle", lyricsPrefs.getString("title", "").orEmpty())
            .put("musicArtist", lyricsPrefs.getString("artist", "").orEmpty())
            .put("musicPackage", lyricsPrefs.getString("package", "").orEmpty())
            .put("musicPositionMs", lyricsPrefs.getLong("positionMs", 0L))
            .put("musicPlaybackSpeed", lyricsPrefs.getFloat("playbackSpeed", 0f).toDouble())
            .put("weatherReady", dashboardPrefs.getBoolean("weatherReady", false))
            .put("weatherTemperature", dashboardPrefs.getString("weatherTemperature", "").orEmpty())
            .put("weatherDescription", dashboardPrefs.getString("weatherDescription", "").orEmpty())
            .put("weatherLocation", dashboardPrefs.getString("weatherLocation", "").orEmpty())
            .put("weatherFeelsLike", dashboardPrefs.getString("weatherFeelsLike", "").orEmpty())
            .put("weatherHumidity", dashboardPrefs.getInt("weatherHumidity", 0))
            .put("weatherWind", dashboardPrefs.getFloat("weatherWind", 0f).toDouble())
            .put("weatherUpdatedAt", dashboardPrefs.getLong("weatherUpdatedAt", 0L))
            .toString()
    }

    private fun readDashboardLayout(): JSONObject {
        val fallback = """{"widgets":[{"id":"clock-1","type":"clock","x":0,"y":0,"w":4,"h":2},{"id":"weather-1","type":"weather","x":4,"y":0,"w":4,"h":2},{"id":"air-1","type":"air","x":8,"y":0,"w":4,"h":2},{"id":"prayer-1","type":"prayer","x":0,"y":2,"w":6,"h":2},{"id":"music-1","type":"music","x":6,"y":2,"w":6,"h":2}]}"""
        return try {
            val raw = dashboardPrefs.getString("dashboardLayout", fallback) ?: fallback
            JSONObject(raw)
        } catch (e: Exception) {
            Log.w(TAG, "Invalid saved dashboard layout; using defaults", e)
            JSONObject(fallback)
        }
    }

    fun cleanup() {
        if (isCleanedUp) return
        isCleanedUp = true
        pageLoaded = false
        try {
            dashboardPrefs.unregisterOnSharedPreferenceChangeListener(dashboardPrefsListener)
            lyricsPrefs.unregisterOnSharedPreferenceChangeListener(lyricsPrefsListener)
            dashboardPrefs.unregisterOnSharedPreferenceChangeListener(weatherPrefsListener)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister dashboard preference listener", e)
        }
        val wv = webView
        webView = null
        try { if (isShowing) dismiss() } catch (e: Exception) { Log.w(TAG, "Dismiss failed", e) }
        if (wv != null) try {
            wv.stopLoading()
            wv.removeJavascriptInterface(BRIDGE_NAME)
            wv.webChromeClient = null
            wv.webViewClient = WebViewClient()
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.removeAllViews()
            wv.destroy()
        } catch (e: Exception) { Log.w(TAG, "WebView cleanup failed", e) }
    }

    inner class PoCDashboardBridge {
        @JavascriptInterface fun getState(): String = buildPoCStateJson(currentDiagnostics)
        @JavascriptInterface fun action(name: String) { Log.i(TAG, "DriveDeckCar.action('$name') no-op in layout PoC") }
    }

    companion object {
        private const val TAG = "DriveDeckPoC"
        private const val BRIDGE_NAME = "DriveDeckCar"
        private const val DASHBOARD_ASSET_URL = "file:///android_asset/car_dashboard.html"
    }
}

data class SurfaceDiagnostics(
    val width: Int, val height: Int, val dpi: Int, val displayId: Int, val carApiLevel: Int,
    val visibleArea: String = "N/A", val stableArea: String = "N/A", val updateCount: Int = 1
)
