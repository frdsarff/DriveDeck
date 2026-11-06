package com.example.drivedeck.car

import android.app.Presentation
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject
import com.example.drivedeck.lyrics.MediaNotificationListener

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
        if (key in setOf("dashboardLayout", "lyricsFontSize", "lyrics", "metadata") && !isCleanedUp) {
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
    private var youtubeWebView: WebView? = null
    private var presentationRoot: FrameLayout? = null
    private var dashboardView: WebView? = null
    private var pageLoaded = false
    private var isCleanedUp = false
    private var scrollGestureActive = false
    private var scrollPointerX = 0f
    private var scrollPointerY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "Presentation.onCreate: displayId=${display.displayId}, name=${display.name}")
        dashboardPrefs.registerOnSharedPreferenceChangeListener(dashboardPrefsListener)
        lyricsPrefs.registerOnSharedPreferenceChangeListener(lyricsPrefsListener)
        dashboardPrefs.registerOnSharedPreferenceChangeListener(weatherPrefsListener)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
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
            dashboardView = wv
            val root = FrameLayout(context).apply {
                setBackgroundColor(Color.rgb(8, 12, 19))
                addView(wv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
            presentationRoot = root
            setContentView(root)
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

    /**
     * Replays a host-provided tap against the dashboard WebView. Coordinates
     * are in the host surface's pixel space, matching the Presentation display.
     */
    fun dispatchDashboardClick(x: Float, y: Float) {
        if (isCleanedUp) return
        scrollGestureActive = false
        val root = presentationRoot ?: return
        root.post {
            if (isCleanedUp || presentationRoot !== root || root.width <= 0 || root.height <= 0) {
                Log.w(TAG, "dispatchDashboardClick: Presentation root is not ready")
                return@post
            }
            val px = x.coerceIn(0f, (root.width - 1).toFloat())
            val py = y.coerceIn(0f, (root.height - 1).toFloat())
            val downTime = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, px, py, 0)
            val up = MotionEvent.obtain(downTime, downTime + 60L, MotionEvent.ACTION_UP, px, py, 0)
            try {
                root.dispatchTouchEvent(down)
                root.dispatchTouchEvent(up)
                Log.i(TAG, "Presentation tap dispatched at ($px, $py)")
            } finally {
                down.recycle()
                up.recycle()
            }
        }
    }



    /** Forward Android Auto scroll deltas as a continuous WebView touch gesture. */
    fun dispatchDashboardScroll(distanceX: Float, distanceY: Float) {
        if (isCleanedUp || !distanceX.isFinite() || !distanceY.isFinite()) return
        val root = presentationRoot ?: return
        root.post {
            if (isCleanedUp || presentationRoot !== root || root.width <= 0 || root.height <= 0) return@post
            val now = SystemClock.uptimeMillis()
            if (!scrollGestureActive) {
                scrollPointerX = root.width / 2f
                scrollPointerY = root.height / 2f
                val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, scrollPointerX, scrollPointerY, 0)
                root.dispatchTouchEvent(down)
                down.recycle()
                scrollGestureActive = true
            }
            scrollPointerX = (scrollPointerX - distanceX).coerceIn(0f, (root.width - 1).toFloat())
            scrollPointerY = (scrollPointerY - distanceY).coerceIn(0f, (root.height - 1).toFloat())
            val move = MotionEvent.obtain(now, now + 16L, MotionEvent.ACTION_MOVE, scrollPointerX, scrollPointerY, 0)
            root.dispatchTouchEvent(move)
            move.recycle()
        }
    }

    /** Finish the scroll with a short velocity-based swipe so WebView can fling. */
    fun dispatchDashboardFling(velocityX: Float, velocityY: Float) {
        if (isCleanedUp || !velocityX.isFinite() || !velocityY.isFinite()) return
        val root = presentationRoot ?: return
        root.post {
            if (isCleanedUp || presentationRoot !== root || root.width <= 0 || root.height <= 0) return@post
            val now = SystemClock.uptimeMillis()
            if (!scrollGestureActive) {
                scrollPointerX = root.width / 2f
                scrollPointerY = root.height / 2f
                val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, scrollPointerX, scrollPointerY, 0)
                root.dispatchTouchEvent(down)
                down.recycle()
            }
            val targetX = (scrollPointerX - velocityX * 0.10f).coerceIn(0f, (root.width - 1).toFloat())
            val targetY = (scrollPointerY - velocityY * 0.10f).coerceIn(0f, (root.height - 1).toFloat())
            val move = MotionEvent.obtain(now, now + 80L, MotionEvent.ACTION_MOVE, targetX, targetY, 0)
            val up = MotionEvent.obtain(now, now + 100L, MotionEvent.ACTION_UP, targetX, targetY, 0)
            root.dispatchTouchEvent(move)
            root.dispatchTouchEvent(up)
            move.recycle()
            up.recycle()
            scrollGestureActive = false
            scrollPointerX = targetX
            scrollPointerY = targetY
        }
    }

    private fun openYoutubePage() {
        if (isCleanedUp) return
        val root = presentationRoot ?: return
        if (youtubeWebView == null) {
            val page = WebView(context).apply {
                setBackgroundColor(Color.BLACK)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)
                    loadWithOverviewMode = true
                    useWideViewPort = true
                }
                isFocusable = true
                isFocusableInTouchMode = true
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (view === youtubeWebView) installYoutubeKeyboard(view)
                    }
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val scheme = request?.url?.scheme ?: return true
                        return scheme != "https" && scheme != "http"
                    }
                }
            }
            youtubeWebView = page
            val pageFrame = FrameLayout(context).apply {
                setBackgroundColor(Color.BLACK)
                addView(page, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
            val toolbar = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(12, 8, 12, 8)
                setBackgroundColor(Color.rgb(10, 25, 40))
            }
            fun addNavButton(label: String, action: () -> Unit) {
                val button = TextView(context).apply {
                    text = label
                    textSize = 16f
                    setTextColor(Color.WHITE)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = android.view.Gravity.CENTER
                    setPadding(18, 10, 18, 10)
                    background = GradientDrawable().apply {
                        setColor(Color.rgb(28, 65, 88))
                        cornerRadius = 18f
                        setStroke(1, Color.rgb(85, 223, 244))
                    }
                    setOnClickListener { action() }
                }
                toolbar.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = 10 })
            }
            addNavButton("⌂  Home") { showDashboardPage() }
            addNavButton("‹  Back") {
                val current = youtubeWebView
                if (current != null && current.canGoBack()) current.goBack() else showDashboardPage()
            }
            val pageLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(pageFrame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            }
            root.addView(pageLayout, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            page.tag = pageLayout
        }
        dashboardView?.visibility = View.GONE
        (youtubeWebView?.tag as? View)?.visibility = View.VISIBLE
        val page = youtubeWebView
        if (page != null && page.url.isNullOrBlank()) page.loadUrl("https://m.youtube.com/")
        page?.isFocusableInTouchMode = true
        page?.requestFocus(View.FOCUS_DOWN)
        Log.i(TAG, "YouTube Web page opened inside DriveDeck")
    }

    /**
     * Android blocks the system IME on this app-owned VirtualDisplay.
     * Provide an in-page keyboard for YouTube search instead. It writes directly
     * to the currently focused search input and dispatches normal DOM events.
     */
    private fun installYoutubeKeyboard(page: WebView) {
        if (isCleanedUp) return
        val script = """
            (function() {
              if (window.__driveDeckKeyboardInstalled) return;
              window.__driveDeckKeyboardInstalled = true;
              let target = null;
              const style = document.createElement('style');
              style.textContent = '#ddk-keyboard{position:fixed;z-index:2147483647;left:0;right:0;bottom:0;padding:10px 2vw 14px;background:rgba(8,15,27,.98);border-top:2px solid #55dff4;box-shadow:0 -8px 28px #0009;display:none;flex-direction:column;gap:7px;font:600 18px system-ui,Roboto,sans-serif;touch-action:manipulation}#ddk-keyboard .ddk-row{display:flex;justify-content:center;gap:5px}#ddk-keyboard button{flex:1;min-width:0;height:clamp(38px,7vh,58px);border:1px solid #42617d;border-radius:9px;background:#1b3048;color:#fff;font-size:clamp(14px,2.4vh,22px);font-weight:700}#ddk-keyboard button.ddk-wide{flex:1.6;background:#15566a}#ddk-keyboard button.ddk-space{flex:5}#ddk-keyboard button.ddk-enter{background:#087f83;border-color:#55dff4}';
              document.head.appendChild(style);
              const board = document.createElement('div');
              board.id = 'ddk-keyboard';
              const rows = [
                'qwertyuiop','asdfghjkl','⇧zxcvbnm⌫',['123','@','SPACE','.','SEARCH','DONE']
              ];
              function emit() {
                if (!target || !target.isConnected) return;
                target.dispatchEvent(new Event('input',{bubbles:true}));
                target.dispatchEvent(new Event('change',{bubbles:true}));
              }
              function addKey(label, row) {
                const b=document.createElement('button'); b.type='button'; b.textContent=label;
                if (['⇧','⌫','123','SEARCH','DONE'].includes(label)) b.className='ddk-wide';
                if(label==='SPACE') b.className='ddk-space';
                if(label==='SEARCH') b.className='ddk-enter';
                b.addEventListener('click',function(ev){
                  ev.preventDefault(); ev.stopPropagation();
                  if(!target || !target.isConnected) return;
                  const start=typeof target.selectionStart==='number'?target.selectionStart:target.value.length;
                  const end=typeof target.selectionEnd==='number'?target.selectionEnd:target.value.length;
                  if(label==='DONE'){board.style.display='none';return;}
                  if(label==='SEARCH'){
                    emit();
                    const form=target.form;
                    if(form && form.requestSubmit) form.requestSubmit();
                    else target.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true}));
                    board.style.display='none'; target.blur(); return;
                  }
                  if(label==='⌫'){
                    if(start===end && start>0){target.setRangeText('',start-1,end,'end');}
                    else if(start!==end){target.setRangeText('',start,end,'end');}
                  } else if(label==='SPACE'){target.setRangeText(' ',start,end,'end');}
                  else if(label==='⇧'){board.dataset.shift=board.dataset.shift==='1'?'0':'1';return;}
                  else if(label==='123'){board.dataset.numeric=board.dataset.numeric==='1'?'0':'1';b.textContent=board.dataset.numeric==='1'?'ABC':'123';return;}
                  else {
                    let ch=label;
                    if(board.dataset.numeric==='1'){
                      const map={q:"1",w:"2",e:"3",r:"4",t:"5",y:"6",u:"7",i:"8",o:"9",p:"0",a:"-",s:"/",d:":",f:";",g:"(",h:")",j:"$",k:"&",l:"@",z:"!",x:"?",c:"'",v:"\"",b:"#",n:"%",m:"+"};
                      ch=map[ch]||ch;
                    } else if(board.dataset.shift==='1') ch=ch.toUpperCase();
                    target.setRangeText(ch,start,end,'end');
                    board.dataset.shift='0';
                  }
                  emit();
                });
                row.appendChild(b);
              }
              rows.forEach(function(chars){
                const row=document.createElement('div');row.className='ddk-row';
                const labels=Array.isArray(chars)?chars:chars.split('');
                labels.forEach(label=>addKey(label,row));board.appendChild(row);
              });
              document.body.appendChild(board);
              document.addEventListener('focusin',function(ev){
                const el=ev.target;
                if(el && (el.tagName==='INPUT'||el.tagName==='TEXTAREA') && !['checkbox','radio','button','submit'].includes((el.type||'').toLowerCase())){
                  target=el; board.style.display='flex';
                }
              },true);
              document.addEventListener('focusout',function(){setTimeout(function(){if(!target || !target.isConnected)board.style.display='none';},250);},true);
              window.addEventListener('resize',function(){if(target && target.isConnected)board.style.display='flex';});
            })();
        """.trimIndent()
        page.evaluateJavascript(script, null)
        Log.i(TAG, "Injected DriveDeck in-page YouTube keyboard")
    }

    private fun showDashboardPage() {
        if (isCleanedUp) return
        (youtubeWebView?.tag as? View)?.visibility = View.GONE
        dashboardView?.visibility = View.VISIBLE
        Log.i(TAG, "Returned to DriveDeck dashboard")
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
            .put("showMetadata", dashboardPrefs.getString("metadata", "true") != "false")
            .put("showLyrics", dashboardPrefs.getString("lyrics", "true") != "false")
            .put("lyricsFontSize", dashboardPrefs.getString("lyricsFontSize", "25")?.toIntOrNull()?.coerceIn(14, 36) ?: 25)
            .put("musicTitle", lyricsPrefs.getString("title", "").orEmpty())
            .put("musicArtist", lyricsPrefs.getString("artist", "").orEmpty())
            .put("musicPackage", lyricsPrefs.getString("package", "").orEmpty())
            .put("musicPositionMs", lyricsPrefs.getLong("positionMs", 0L))
            .put("musicPlaybackSpeed", lyricsPrefs.getFloat("playbackSpeed", 0f).toDouble())
            .put("musicIsPlaying", lyricsPrefs.getFloat("playbackSpeed", 0f) > 0f)
            .put("musicSyncedLyrics", if (dashboardPrefs.getString("lyrics", "true") != "false") lyricsPrefs.getString("syncedLyrics", "").orEmpty() else "")
            .put("musicLyrics", if (dashboardPrefs.getString("lyrics", "true") != "false") lyricsPrefs.getString("lyrics", "").orEmpty() else "")
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
        // Default only applies on a clean install. Saved phone layouts remain untouched.
        val fallback = """{"widgets":[{"id":"clock-1","type":"clock","x":0,"y":0,"w":4,"h":2},{"id":"weather-1","type":"weather","x":4,"y":0,"w":4,"h":2},{"id":"music-1","type":"music","x":8,"y":0,"w":4,"h":2},{"id":"shortcuts-1","type":"shortcuts","x":0,"y":2,"w":6,"h":2}]}"""
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
        val yt = youtubeWebView
        youtubeWebView = null
        presentationRoot = null
        dashboardView = null
        try { if (isShowing) dismiss() } catch (e: Exception) { Log.w(TAG, "Dismiss failed", e) }
        if (yt != null) try {
            yt.stopLoading()
            yt.webChromeClient = null
            yt.webViewClient = WebViewClient()
            (yt.parent as? ViewGroup)?.removeView(yt)
            yt.removeAllViews()
            yt.destroy()
        } catch (e: Exception) { Log.w(TAG, "YouTube WebView cleanup failed", e) }
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
        @JavascriptInterface fun openYoutubeWeb(): Boolean {
            if (isCleanedUp) return false
            webView?.post { openYoutubePage() }
            return true
        }
        @JavascriptInterface fun action(name: String): Boolean {
            val handled = MediaNotificationListener.controlPlayback(name)
            Log.i(TAG, "DriveDeckCar.action('$name'): handled=$handled")
            return handled
        }
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
,k:'&',l:'@',z:'!',x:'?',c:'\'',v:'"',b:'#',n:'%',m:'+'};ch=map[ch]||ch;}
                    else if(board.dataset.shift==='1') ch=ch.toUpperCase();
                    target.setRangeText(ch,start,end,'end');
                    board.dataset.shift='0';
                  }
                  emit();
                });
                row.appendChild(b);
              }
              rows.forEach(function(chars){
                const row=document.createElement('div');row.className='ddk-row';
                const labels=Array.isArray(chars)?chars:chars.split('');
                labels.forEach(label=>addKey(label,row));board.appendChild(row);
              });
              document.body.appendChild(board);
              document.addEventListener('focusin',function(ev){
                const el=ev.target;
                if(el && (el.tagName==='INPUT'||el.tagName==='TEXTAREA') && !['checkbox','radio','button','submit'].includes((el.type||'').toLowerCase())){
                  target=el; board.style.display='flex';
                }
              },true);
              document.addEventListener('focusout',function(){setTimeout(function(){if(!target || !target.isConnected)board.style.display='none';},250);},true);
              window.addEventListener('resize',function(){if(target && target.isConnected)board.style.display='flex';});
            })();
        """.trimIndent()
        page.evaluateJavascript(script, null)
        Log.i(TAG, "Injected DriveDeck in-page YouTube keyboard")
    }

    private fun showDashboardPage() {
        if (isCleanedUp) return
        (youtubeWebView?.tag as? View)?.visibility = View.GONE
        dashboardView?.visibility = View.VISIBLE
        Log.i(TAG, "Returned to DriveDeck dashboard")
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
            .put("showMetadata", dashboardPrefs.getString("metadata", "true") != "false")
            .put("showLyrics", dashboardPrefs.getString("lyrics", "true") != "false")
            .put("lyricsFontSize", dashboardPrefs.getString("lyricsFontSize", "25")?.toIntOrNull()?.coerceIn(14, 36) ?: 25)
            .put("musicTitle", lyricsPrefs.getString("title", "").orEmpty())
            .put("musicArtist", lyricsPrefs.getString("artist", "").orEmpty())
            .put("musicPackage", lyricsPrefs.getString("package", "").orEmpty())
            .put("musicPositionMs", lyricsPrefs.getLong("positionMs", 0L))
            .put("musicPlaybackSpeed", lyricsPrefs.getFloat("playbackSpeed", 0f).toDouble())
            .put("musicIsPlaying", lyricsPrefs.getFloat("playbackSpeed", 0f) > 0f)
            .put("musicSyncedLyrics", if (dashboardPrefs.getString("lyrics", "true") != "false") lyricsPrefs.getString("syncedLyrics", "").orEmpty() else "")
            .put("musicLyrics", if (dashboardPrefs.getString("lyrics", "true") != "false") lyricsPrefs.getString("lyrics", "").orEmpty() else "")
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
        // Default only applies on a clean install. Saved phone layouts remain untouched.
        val fallback = """{"widgets":[{"id":"clock-1","type":"clock","x":0,"y":0,"w":4,"h":2},{"id":"weather-1","type":"weather","x":4,"y":0,"w":4,"h":2},{"id":"music-1","type":"music","x":8,"y":0,"w":4,"h":2},{"id":"shortcuts-1","type":"shortcuts","x":0,"y":2,"w":6,"h":2}]}"""
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
        val yt = youtubeWebView
        youtubeWebView = null
        presentationRoot = null
        dashboardView = null
        try { if (isShowing) dismiss() } catch (e: Exception) { Log.w(TAG, "Dismiss failed", e) }
        if (yt != null) try {
            yt.stopLoading()
            yt.webChromeClient = null
            yt.webViewClient = WebViewClient()
            (yt.parent as? ViewGroup)?.removeView(yt)
            yt.removeAllViews()
            yt.destroy()
        } catch (e: Exception) { Log.w(TAG, "YouTube WebView cleanup failed", e) }
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
        @JavascriptInterface fun openYoutubeWeb(): Boolean {
            if (isCleanedUp) return false
            webView?.post { openYoutubePage() }
            return true
        }
        @JavascriptInterface fun action(name: String): Boolean {
            val handled = MediaNotificationListener.controlPlayback(name)
            Log.i(TAG, "DriveDeckCar.action('$name'): handled=$handled")
            return handled
        }
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
