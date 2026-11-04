package com.example.drivedeck.car

import android.app.Presentation
import android.content.Context
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
) : Presentation(
    outerContext,
    display,
    android.R.style.Theme_Black_NoTitleBar_Fullscreen
) {
    @Volatile
    private var currentDiagnostics: SurfaceDiagnostics = initialDiagnostics

    private var webView: WebView? = null
    private var pageLoaded = false
    private var isCleanedUp = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "Presentation.onCreate: displayId=${display.displayId}, name=${display.name}")

        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        try {
            val wv = WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
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
                        consoleMessage?.let {
                            Log.d(
                                TAG,
                                "WebView Console [${it.messageLevel()}]: ${it.message()} (line ${it.lineNumber()})"
                            )
                        }
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

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        Log.e(
                            TAG,
                            "WebView.onReceivedError: url=${request?.url}, code=${error?.errorCode}, desc=${error?.description}"
                        )
                    }
                }

                loadUrl(DASHBOARD_ASSET_URL)
            }

            webView = wv
            setContentView(wv)
            Log.i(TAG, "Presentation.onCreate: WebView attached to Presentation window")
        } catch (e: Exception) {
            Log.e(TAG, "Presentation.onCreate: Failed to initialize WebView", e)
        }
    }

    override fun onStart() {
        super.onStart()
        Log.i(TAG, "Presentation.onStart: Presentation active on displayId=${display.displayId}")
    }

    override fun onStop() {
        Log.i(TAG, "Presentation.onStop: Presentation stopped on displayId=${display.displayId}")
        super.onStop()
    }

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
            Log.i(TAG, "Presentation.reloadWebView: Reloading existing WebView instance")
            pageLoaded = false
            wv.reload()
        }
    }

    private fun pushDiagnosticsToWebView(diag: SurfaceDiagnostics) {
        if (isCleanedUp) return
        val wv = webView ?: return
        val stateJson = buildPoCStateJson(diag)
        wv.post {
            if (isCleanedUp || !pageLoaded || webView == null) return@post
            wv.evaluateJavascript("render($stateJson);", null)
        }
    }

    private fun buildPoCStateJson(diag: SurfaceDiagnostics): String {
        val lines = JSONArray()
            .put("Surface: ${diag.width}×${diag.height} px @ ${diag.dpi} dpi")
            .put("Visible: ${diag.visibleArea} | Stable: ${diag.stableArea}")
            .put("Display ID: ${diag.displayId} • Updates: ${diag.updateCount}")

        return JSONObject()
            .put("connected", true)
            .put("connectionText", "● POC SURFACE ACTIVE")
            .put("title", "Custom Surface PoC Ready")
            .put("artist", "Resolution: ${diag.width}×${diag.height} px • DPI: ${diag.dpi}")
            .put("source", "VIRTUALDISPLAY + PRESENTATION + WEBVIEW")
            .put("elapsed", "VISIBLE ${diag.visibleArea}")
            .put("duration", "DPI ${diag.dpi}")
            .put("status", "PHASE 1 POC • CAR API ${diag.carApiLevel}")
            .put("playing", false)
            .put("artwork", "")
            .put("lines", lines)
            .toString()
    }

    fun cleanup() {
        if (isCleanedUp) return
        isCleanedUp = true
        pageLoaded = false
        Log.i(TAG, "Presentation.cleanup: Dismissing presentation and destroying WebView")

        val wv = webView
        webView = null

        try {
            if (isShowing) dismiss()
        } catch (e: Exception) {
            Log.w(TAG, "Presentation.cleanup: Error dismissing presentation", e)
        }

        if (wv != null) {
            try {
                wv.stopLoading()
                wv.removeJavascriptInterface(BRIDGE_NAME)
                wv.webChromeClient = null
                wv.webViewClient = WebViewClient()
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv.removeAllViews()
                wv.destroy()
                Log.i(TAG, "Presentation.cleanup: WebView detached and destroyed cleanly")
            } catch (e: Exception) {
                Log.w(TAG, "Presentation.cleanup: Error destroying WebView", e)
            }
        }
    }

    inner class PoCDashboardBridge {
        @JavascriptInterface
        fun getState(): String {
            Log.d(TAG, "DriveDeckCar.getState() invoked from JS")
            return buildPoCStateJson(currentDiagnostics)
        }

        @JavascriptInterface
        fun action(name: String) {
            Log.i(TAG, "DriveDeckCar.action('$name') invoked in Phase 1 PoC (no-op)")
        }
    }

    companion object {
        private const val TAG = "DriveDeckPoC"
        private const val BRIDGE_NAME = "DriveDeckCar"
        private const val DASHBOARD_ASSET_URL = "file:///android_asset/car_dashboard.html"
    }
}

data class SurfaceDiagnostics(
    val width: Int,
    val height: Int,
    val dpi: Int,
    val displayId: Int,
    val carApiLevel: Int,
    val visibleArea: String = "N/A",
    val stableArea: String = "N/A",
    val updateCount: Int = 1
)
