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
    // Always read the same app-wide preferences file written by MainActivity.
    // Presentation's display context can differ from the activity context.
    private val dashboardPrefs: SharedPreferences by lazy {
        outerContext.applicationContext.getSharedPreferences("drivedeck_settings", Context.MODE_PRIVATE)
    }
    private val lyricsPrefs: SharedPreferences by lazy {
        context.getSharedPreferences("drivedeck_lyrics", Context.MODE_PRIVATE)
    }
    private val dashboardPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key in setOf("dashboardLayout", "lyricsFontSize", "lyrics", "metadata") && !isCleanedUp) {
            if (key == "dashboardLayout") {
                val raw = prefs.getString("dashboardLayout", null)
                val widgetCount = try { JSONObject(raw ?: "{}").optJSONArray("widgets")?.length() ?: 0 } catch (_: Exception) { -1 }
                Log.i(TAG, "Dashboard layout preference changed: present=${raw != null}, chars=${raw?.length ?: 0}, widgets=$widgetCount")
            }
            Log.i(TAG, "Dashboard preference '$key' changed on phone; pushing fresh state to Android Auto")
            pushCurrentDiagnostics()
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
    private var youtubePageLayout: View? = null
    private var youtubeFullscreenView: View? = null
    private var youtubeFullscreenCallback: WebChromeClient.CustomViewCallback? = null
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
            scrollPointerX = px
            scrollPointerY = py
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

            // Android Auto scroll callbacks do not include pointer coordinates.
            // On the YouTube page, use a DOM wheel/scroll event so nested
            // recommendation and comment panes can scroll independently.
            val youtube = youtubeWebView
            if (youtube != null && youtube.visibility == View.VISIBLE) {
                val dx = distanceX.coerceIn(-1200f, 1200f)
                val dy = distanceY.coerceIn(-1200f, 1200f)
                val rootPos = IntArray(2)
                val youtubePos = IntArray(2)
                root.getLocationOnScreen(rootPos)
                youtube.getLocationOnScreen(youtubePos)
                val localX = scrollPointerX - (youtubePos[0] - rootPos[0])
                val localY = scrollPointerY - (youtubePos[1] - rootPos[1])
                val x = if (localX >= 1f && localX < youtube.width.toFloat()) localX.toInt() else (youtube.width * 0.78f).toInt()
                val y = if (localY >= 1f && localY < youtube.height.toFloat()) localY.toInt() else (youtube.height * 0.58f).toInt()
                youtube.evaluateJavascript(
                    "window.__driveDeckScrollBy && window.__driveDeckScrollBy($dx,$dy,$x,$y)",
                    null
                )
                scrollGestureActive = false
                return@post
            }

            val now = SystemClock.uptimeMillis()
            if (!scrollGestureActive) {
                // Android Auto reports scroll deltas without touch coordinates.
                // Use the last tap; default to YouTube's right-side content column.
                if (youtubeWebView?.visibility == View.VISIBLE && (scrollPointerX <= 0f || scrollPointerY <= 0f)) {
                    scrollPointerX = root.width * 0.78f
                    scrollPointerY = root.height * 0.55f
                } else if (scrollPointerX <= 0f || scrollPointerY <= 0f) {
                    scrollPointerX = root.width / 2f
                    scrollPointerY = root.height / 2f
                }
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
            val youtube = youtubeWebView
            if (youtube != null && youtube.visibility == View.VISIBLE) {
                val dx = (velocityX * 0.18f).coerceIn(-1800f, 1800f)
                val dy = (velocityY * 0.18f).coerceIn(-1800f, 1800f)
                val x = (youtube.width * 0.78f).toInt()
                val y = (youtube.height * 0.58f).toInt()
                youtube.evaluateJavascript(
                    "window.__driveDeckScrollBy && window.__driveDeckScrollBy($dx,$dy,$x,$y)",
                    null
                )
                scrollGestureActive = false
                return@post
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
                webChromeClient = object : WebChromeClient() {
                    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                        if (view == null) return
                        val rootView = presentationRoot ?: run {
                            callback?.onCustomViewHidden()
                            return
                        }
                        if (youtubeFullscreenView != null) {
                            callback?.onCustomViewHidden()
                            return
                        }
                        youtubeFullscreenView = view
                        youtubeFullscreenCallback = callback
                        youtubePageLayout?.visibility = View.GONE
                        dashboardView?.visibility = View.GONE
                        rootView.addView(view, FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        ))
                        view.requestFocus()
                        Log.i(TAG, "YouTube HTML5 fullscreen entered")
                    }

                    override fun onHideCustomView() {
                        val rootView = presentationRoot
                        youtubeFullscreenView?.let { view ->
                            (view.parent as? ViewGroup)?.removeView(view)
                        }
                        youtubeFullscreenView = null
                        youtubePageLayout?.visibility = View.VISIBLE
                        youtubeFullscreenCallback?.onCustomViewHidden()
                        youtubeFullscreenCallback = null
                        Log.i(TAG, "YouTube HTML5 fullscreen exited")
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.takeIf { it === youtubeWebView }?.let { installYoutubeKeyboard(it) }
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
            youtubePageLayout = pageLayout
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
            (function(){
              if(window.__ddkKeyboardV2) return;
              window.__ddkKeyboardV2=true;
              let target=null;
              const css=document.createElement('style');
              css.textContent='#ddk-keyboard{position:fixed;z-index:2147483647;left:0;right:0;bottom:0;padding:8px 2vw 12px;background:rgba(7,15,28,.98);border-top:2px solid #55dff4;display:none;flex-direction:column;gap:5px;font:700 18px system-ui;touch-action:manipulation}#ddk-keyboard .r{display:flex;gap:5px}#ddk-keyboard button{flex:1;min-width:0;height:clamp(36px,6vh,54px);border:1px solid #49647e;border-radius:8px;background:#1b3048;color:white;font-size:clamp(13px,2.2vh,20px)}#ddk-keyboard .wide{flex:1.5;background:#15566a}#ddk-keyboard .space{flex:4}#ddk-keyboard .enter{background:#087f83}';
              document.head.appendChild(css);
              const board=document.createElement('div');board.id='ddk-keyboard';
              const rows=['qwertyuiop','asdfghjkl','⇧zxcvbnm⌫',['123','@','SPACE','.','SEARCH','DONE']];
              function editable(el){return !!el&&((el.tagName==='INPUT'&&!['button','submit','checkbox','radio','hidden'].includes((el.type||'').toLowerCase()))||el.tagName==='TEXTAREA'||el.isContentEditable);}
              function activeDeep(root){let el=root.activeElement;while(el&&el.shadowRoot&&el.shadowRoot.activeElement)el=el.shadowRoot.activeElement;return el;}
              function capture(ev){
                const path=ev&&ev.composedPath?ev.composedPath():[];
                const candidate=path.find(editable)||activeDeep(document);
                if(editable(candidate)){target=candidate;board.style.display='flex';}
              }
              function fireInput(el){
                try{el.dispatchEvent(new InputEvent('input',{bubbles:true,composed:true,inputType:'insertText',data:el.value}));}
                catch(_){el.dispatchEvent(new Event('input',{bubbles:true,composed:true}));}
                el.dispatchEvent(new Event('change',{bubbles:true,composed:true}));
              }
              function setNativeValue(el,value){
                const proto=el.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;
                const setter=Object.getOwnPropertyDescriptor(proto,'value')?.set;
                if(setter)setter.call(el,value);else el.value=value;
              }
              function fireInput(el,data){
                try{el.dispatchEvent(new InputEvent('beforeinput',{bubbles:true,composed:true,inputType:'insertText',data:data||null}));}catch(_){}
                try{el.dispatchEvent(new InputEvent('input',{bubbles:true,composed:true,inputType:'insertText',data:data||null}));}
                catch(_){el.dispatchEvent(new Event('input',{bubbles:true,composed:true}));}
                el.dispatchEvent(new Event('change',{bubbles:true,composed:true}));
              }
              function insert(value){
                if(!target||!target.isConnected)return;
                target.focus();
                if(target.isContentEditable){document.execCommand('insertText',false,value);fireInput(target,value);return;}
                const old=String(target.value||'');
                const s=typeof target.selectionStart==='number'?target.selectionStart:old.length;
                const e=typeof target.selectionEnd==='number'?target.selectionEnd:old.length;
                const next=old.slice(0,s)+value+old.slice(e);
                setNativeValue(target,next);
                try{target.setSelectionRange(s+value.length,s+value.length);}catch(_){}
                target.dispatchEvent(new KeyboardEvent('keydown',{key:value, bubbles:true}));
                fireInput(target,value);
                target.dispatchEvent(new KeyboardEvent('keyup',{key:value, bubbles:true}));
              }
              function add(label,row){
                const b=document.createElement('button');b.type='button';b.textContent=label;
                if(['⇧','⌫','123','SEARCH','DONE'].includes(label))b.className='wide';
                if(label==='SPACE')b.className='space';if(label==='SEARCH')b.className='wide enter';
                b.addEventListener('click',function(ev){
                  ev.preventDefault();ev.stopPropagation();
                  if(label==='DONE'){board.style.display='none';return;}
                  if(!target||!target.isConnected){capture();if(!target)return;}
                  if(label==='SEARCH'){
                    fireInput(target,'');
                    target.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true}));
                    const form=target.form;
                    if(form&&form.requestSubmit)form.requestSubmit();
                    else target.dispatchEvent(new KeyboardEvent('keyup',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true}));
                    board.style.display='none';return;
                  }
                  if(label==='⇧'){board.dataset.shift=board.dataset.shift==='1'?'0':'1';return;}
                  if(label==='123'){board.dataset.num=board.dataset.num==='1'?'0':'1';b.textContent=board.dataset.num==='1'?'ABC':'123';return;}
                  if(label==='⌫'){
                    if(target.isContentEditable){document.execCommand('delete');fireInput(target,'');return;}
                    const s=target.selectionStart||0,e=target.selectionEnd||0;
                    const old=String(target.value||'');
                    const start=s===e&&s>0?s-1:s;
                    setNativeValue(target,old.slice(0,start)+old.slice(e));
                    try{target.setSelectionRange(start,start);}catch(_){}
                    fireInput(target,'');return;
                  }
                  if(label==='SPACE'){insert(' ');return;}
                  let ch=label;
                  if(board.dataset.num==='1'){const map={q:'1',w:'2',e:'3',r:'4',t:'5',y:'6',u:'7',i:'8',o:'9',p:'0'};ch=map[ch]||ch;}
                  else if(board.dataset.shift==='1')ch=ch.toUpperCase();
                  insert(ch);board.dataset.shift='0';
                });
                row.appendChild(b);
              }
              rows.forEach(chars=>{const row=document.createElement('div');row.className='r';(Array.isArray(chars)?chars:chars.split('')).forEach(k=>add(k,row));board.appendChild(row);});
              document.body.appendChild(board);
              // Android Auto sends scroll deltas without the current finger position.
              // Prefer the exact hit-tested pane; on YouTube watch pages explicitly
              // resolve the right-hand recommendations column instead of scrolling
              // only the video/player column.
              window.__driveDeckScrollBy=function(dx,dy,x,y){
                const scrollable=n=>{
                  if(!n||n===document.body||n===document.documentElement)return false;
                  const st=getComputedStyle(n);
                  return (((st.overflowY==='auto'||st.overflowY==='scroll')&&n.scrollHeight>n.clientHeight+2)||
                          ((st.overflowX==='auto'||st.overflowX==='scroll')&&n.scrollWidth>n.clientWidth+2));
                };
                let hit=document.elementFromPoint(x,y);
                if(hit&&hit.closest&&hit.closest('#ddk-keyboard')) hit=document.elementFromPoint(x,Math.max(0,y-80));
                let scroller=null;
                for(let node=hit;node&&node!==document.body&&node!==document.documentElement;node=node.parentElement){
                  if(scrollable(node)){scroller=node;break;}
                }
                const rightSide=x>window.innerWidth*0.52;
                if(rightSide){
                  const selectors=['#secondary','ytd-watch-next-secondary-results-renderer',
                    '#related','ytd-watch-next-results','ytm-item-section-renderer'];
                  for(const selector of selectors){
                    const candidates=Array.from(document.querySelectorAll(selector));
                    const pane=candidates.find(n=>{
                      const r=n.getBoundingClientRect();
                      return r.width>0&&r.height>0&&x>=r.left-2&&x<=r.right+2&&y>=r.top-2&&y<=r.bottom+2;
                    })||candidates.find(n=>{const r=n.getBoundingClientRect();return r.width>0&&r.height>0&&r.left>window.innerWidth*0.45;});
                    if(pane){
                      let node=pane;
                      while(node&&node!==document.body&&node!==document.documentElement){
                        if(scrollable(node)){scroller=node;break;}
                        node=node.parentElement;
                      }
                      if(scroller)break;
                    }
                  }
                }
                if(!scroller)scroller=document.scrollingElement||document.documentElement;
                if(scroller)scroller.scrollBy({left:dx,top:dy,behavior:'auto'});
                return !!scroller;
              };
              document.addEventListener('focusin',capture,true);
              document.addEventListener('click',function(){setTimeout(()=>capture(),80);},true);
              document.addEventListener('touchend',function(){setTimeout(()=>capture(),100);},true);
              document.addEventListener('keydown',function(ev){if(ev.key==='Escape')board.style.display='none';},true);
              const observer=new MutationObserver(()=>{if(target&&!target.isConnected){target=null;board.style.display='none';}});
              observer.observe(document.documentElement,{childList:true,subtree:true});
              setTimeout(()=>capture(),300);
            })();
        """.trimIndent()
        page.evaluateJavascript(script) { result ->
            Log.i(TAG, "YouTube keyboard injection result=$result")
        }
        Log.i(TAG, "Injected DriveDeck YouTube keyboard v2")
    }

    private fun showDashboardPage() {
        if (isCleanedUp) return
        youtubeFullscreenView?.let { view -> (view.parent as? ViewGroup)?.removeView(view) }
        youtubeFullscreenView = null
        youtubeFullscreenCallback?.onCustomViewHidden()
        youtubeFullscreenCallback = null
        youtubePageLayout?.visibility = View.GONE
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
        val layout = try {
            val widgets = JSONObject(state).optJSONObject("dashboardLayout")?.optJSONArray("widgets")
            (0 until (widgets?.length() ?: 0)).joinToString(" | ") { index ->
                val item = widgets!!.optJSONObject(index) ?: JSONObject()
                item.optString("type", "?") + ":" + item.optInt("x") + "," + item.optInt("y") +
                    " " + item.optInt("w") + "x" + item.optInt("h")
            }
        } catch (_: Exception) { "invalid" }
        Log.i(TAG, "Pushing dashboard state to WebView: pageLoaded=$pageLoaded layout=[$layout]")
        wv.post {
            if (!isCleanedUp && pageLoaded && webView === wv) {
                wv.evaluateJavascript("render($state);", null)
            } else {
                Log.w(TAG, "Dashboard state push skipped: pageLoaded=$pageLoaded, cleanedUp=$isCleanedUp")
            }
        }
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
