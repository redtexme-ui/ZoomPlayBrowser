package com.zoomplay.browser

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Single-tab WebView browser with native controls for accessible HTML5 media.
 * The site serves the media: this app never decrypts or bypasses DRM.
 */
class MainActivity : Activity() {
    private val dark = Color.rgb(11, 15, 24)
    private val panel = Color.rgb(22, 29, 44)
    private val cyan = Color.rgb(80, 210, 231)
    private val muted = Color.rgb(176, 190, 209)
    private lateinit var root: FrameLayout
    private lateinit var shell: LinearLayout
    private lateinit var top: LinearLayout
    private lateinit var navigation: LinearLayout
    private lateinit var browser: WebView
    private lateinit var address: EditText
    private lateinit var progress: ProgressBar
    private lateinit var controls: LinearLayout
    private lateinit var seek: SeekBar
    private lateinit var zoomSlider: SeekBar
    private lateinit var zoomLabel: TextView
    private lateinit var timeLabel: TextView
    private lateinit var infoLabel: TextView
    private lateinit var playButton: TextView
    private lateinit var speedButton: TextView
    private lateinit var fitButton: TextView
    private lateinit var fullButton: TextView
    private lateinit var backButton: TextView
    private lateinit var nextButton: TextView
    private var source = ""
    private var fit = "contain"
    private var speed = 1.0
    private var fullscreen = false
    private var siteFullView: View? = null
    private var siteFullCallback: WebChromeClient.CustomViewCallback? = null
    private var dragging = false
    private val handler = Handler(Looper.getMainLooper())
    private val statusTask = object : Runnable {
        override fun run() {
            if (controls.visibility == View.VISIBLE && siteFullView == null) {
                invokeVideo("status()") { data -> if (data != null) updateStatus(data) }
                handler.postDelayed(this, 1000L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = dark
        window.navigationBarColor = dark
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        source = assets.open("player.js").bufferedReader().use { it.readText() }
        makeScreen()
        setupBrowser()
        browser.loadUrl(savedInstanceState?.getString("url")?.takeIf { it.startsWith("https://") }
            ?: "https://www.google.com/")
    }

    private fun dp(n: Int): Int = (n * resources.displayMetrics.density + 0.5f).toInt()
    private fun row() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private fun round(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(12).toFloat()
    }
    private fun button(title: String, highlighted: Boolean = false): TextView =
        TextView(this).apply {
            text = title
            setTextColor(if (highlighted) dark else Color.WHITE)
            textSize = if (title.length > 3) 12f else 17f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = round(if (highlighted) cyan else Color.rgb(38, 49, 67))
            isClickable = true
            isFocusable = true
        }
    private fun addAction(parent: LinearLayout, child: TextView, weight: Float = 1f) {
        parent.addView(child, LinearLayout.LayoutParams(0, dp(42), weight).apply {
            leftMargin = dp(2)
            rightMargin = dp(2)
        })
    }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    @Suppress("DEPRECATION")
    private fun makeScreen() {
        root = FrameLayout(this).apply { setBackgroundColor(dark) }
        shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(dark)
        }
        root.addView(shell, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (!fullscreen && siteFullView == null) {
                view.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
            } else view.setPadding(0, 0, 0, 0)
            insets
        }

        top = row().apply { setPadding(dp(7), dp(7), dp(7), dp(5)) }
        val logo = TextView(this).apply {
            text = "ZP"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(cyan)
            gravity = Gravity.CENTER
        }
        top.addView(logo, LinearLayout.LayoutParams(dp(43), dp(45)))
        address = EditText(this).apply {
            hint = "Search or enter website"
            setTextColor(Color.WHITE)
            setHintTextColor(muted)
            textSize = 14f
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_GO
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_URI
            setPadding(dp(12), dp(5), dp(12), dp(5))
            background = round(panel)
            setOnEditorActionListener { _, action, event ->
                if (action == EditorInfo.IME_ACTION_GO || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                    navigate(text.toString())
                    true
                } else false
            }
        }
        top.addView(address, LinearLayout.LayoutParams(0, dp(45), 1f))
        val go = button("GO", true)
        top.addView(go, LinearLayout.LayoutParams(dp(52), dp(45)).apply {
            leftMargin = dp(6)
        })
        go.setOnClickListener { navigate(address.text.toString()) }
        shell.addView(top)

        navigation = row().apply { setPadding(dp(5), 0, dp(5), dp(6)) }
        backButton = button("←")
        nextButton = button("→")
        val refresh = button("⟳")
        val video = button("VIDEO", true)
        val play = button("PLAY")
        addAction(navigation, backButton)
        addAction(navigation, nextButton)
        addAction(navigation, refresh)
        addAction(navigation, video, 1.65f)
        addAction(navigation, play, 1.35f)
        shell.addView(navigation)
        backButton.setOnClickListener { if (browser.canGoBack()) browser.goBack() }
        nextButton.setOnClickListener { if (browser.canGoForward()) browser.goForward() }
        refresh.setOnClickListener { browser.reload() }
        video.setOnClickListener { openVideo() }
        play.setOnClickListener { invokeVideo("play()") { showResult(it, "Play requested") } }

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
            progressTintList = android.content.res.ColorStateList.valueOf(cyan)
        }
        shell.addView(progress, LinearLayout.LayoutParams(-1, dp(3)))

        browser = WebView(this).apply { setBackgroundColor(Color.BLACK) }
        shell.addView(browser, LinearLayout.LayoutParams(-1, 0, 1f))

        controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(7), dp(6), dp(7), dp(8))
            setBackgroundColor(panel)
            visibility = View.GONE
        }
        infoLabel = TextView(this).apply {
            text = "HTML5 VIDEO"
            textSize = 11f
            setTextColor(cyan)
            maxLines = 2
        }
        controls.addView(infoLabel)
        val seekerRow = row()
        timeLabel = TextView(this).apply {
            text = "00:00 / 00:00"
            setTextColor(muted)
            textSize = 11f
        }
        seekerRow.addView(timeLabel, LinearLayout.LayoutParams(dp(98), dp(36)))
        seek = SeekBar(this).apply {
            max = 1000
            progressTintList = android.content.res.ColorStateList.valueOf(cyan)
            thumbTintList = android.content.res.ColorStateList.valueOf(cyan)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {}
                override fun onStartTrackingTouch(s: SeekBar) { dragging = true }
                override fun onStopTrackingTouch(s: SeekBar) {
                    dragging = false
                    val duration = s.tag as? Double ?: 0.0
                    if (duration > 0) invokeVideo("goTo(" + (duration * s.progress / 1000.0) + ")")
                }
            })
        }
        seekerRow.addView(seek, LinearLayout.LayoutParams(0, dp(36), 1f))
        controls.addView(seekerRow)

        val actions = row()
        val rewind = button("−10")
        playButton = button("▶", true)
        val forward = button("+10")
        speedButton = button("1×")
        fitButton = button("FIT")
        fullButton = button("⛶")
        val close = button("✕")
        listOf(rewind, playButton, forward, speedButton, fitButton, fullButton, close)
            .forEach { addAction(actions, it) }
        controls.addView(actions)
        rewind.setOnClickListener { invokeVideo("seek(-10)") }
        forward.setOnClickListener { invokeVideo("seek(10)") }
        playButton.setOnClickListener { invokeVideo("toggle()") { showResult(it) } }
        speedButton.setOnClickListener {
            val speeds = doubleArrayOf(1.0, 1.25, 1.5, 2.0, 0.5)
            speed = speeds[(speeds.indexOfFirst { it == speed } + 1) % speeds.size]
            speedButton.text = speed.toString() + "×"
            invokeVideo("setSpeed(" + speed + ")")
        }
        fitButton.setOnClickListener {
            fit = if (fit == "contain") "cover" else "contain"
            fitButton.text = if (fit == "contain") "FIT" else "FILL"
            invokeVideo("setFit('" + fit + "')")
        }
        fullButton.setOnClickListener { toggleFullscreen() }
        close.setOnClickListener { hideVideo() }

        val zoomRow = row()
        val label = TextView(this).apply {
            text = "ZOOM"
            setTextColor(muted)
            textSize = 11f
        }
        zoomRow.addView(label, LinearLayout.LayoutParams(dp(45), dp(43)))
        zoomSlider = SeekBar(this).apply {
            max = 200
            progressTintList = android.content.res.ColorStateList.valueOf(cyan)
            thumbTintList = android.content.res.ColorStateList.valueOf(cyan)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, value: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val zoom = 1.0 + value / 100.0
                    zoomLabel.text = String.format(Locale.US, "%.2f×", zoom)
                    invokeVideo("setZoom(" + zoom + ")")
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        }
        zoomRow.addView(zoomSlider, LinearLayout.LayoutParams(0, dp(43), 1f))
        zoomLabel = TextView(this).apply {
            text = "1.00×"
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            textSize = 12f
        }
        zoomRow.addView(zoomLabel, LinearLayout.LayoutParams(dp(55), dp(43)))
        val nextVideo = button("NEXT")
        zoomRow.addView(nextVideo, LinearLayout.LayoutParams(dp(64), dp(39)))
        nextVideo.setOnClickListener { invokeVideo("next()") { showResult(it) } }
        controls.addView(zoomRow)
        shell.addView(controls)
    }

    @Suppress("DEPRECATION")
    private fun setupBrowser() {
        WebView.setWebContentsDebuggingEnabled(false)
        with(browser.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            useWideViewPort = true
            loadWithOverviewMode = true
            allowFileAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
        }
        android.webkit.CookieManager.getInstance().setAcceptCookie(true)
        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(browser, false)
        browser.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                hideVideo(false)
                progress.visibility = View.VISIBLE
                address.setText(url ?: "")
                updateNav()
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                address.setText(url ?: "")
                updateNav()
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                val scheme = request.url.scheme?.lowercase(Locale.US) ?: return true
                if (scheme == "http" || scheme == "https") return false
                if (scheme == "mailto" || scheme == "tel") {
                    try { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                    catch (_: ActivityNotFoundException) { toast("No app available") }
                }
                return true
            }
        }
        browser.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, value: Int) {
                progress.progress = value
                progress.visibility = if (value >= 100) View.GONE else View.VISIBLE
            }
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (siteFullView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                siteFullView = view
                siteFullCallback = callback
                shell.visibility = View.GONE
                root.addView(view, FrameLayout.LayoutParams(-1, -1))
                showSystemFullscreen(true)
            }
            override fun onHideCustomView() { closeSiteFullscreen(false) }
        }
    }

    private fun updateNav() {
        backButton.alpha = if (browser.canGoBack()) 1f else 0.5f
        nextButton.alpha = if (browser.canGoForward()) 1f else 0.5f
    }
    private fun navigate(value: String) {
        val query = value.trim()
        if (query.isEmpty()) return
        val url = when {
            query.startsWith("https://", true) -> query
            query.startsWith("http://", true) -> "https://" + query.substring(7)
            !query.contains(' ') && query.contains('.') && !query.contains(':') -> "https://" + query
            else -> "https://www.google.com/search?q=" + URLEncoder.encode(query, "UTF-8")
        }
        val scheme = Uri.parse(url).scheme?.lowercase(Locale.US)
        if (scheme != "https" && scheme != "http") {
            toast("Enter a web address or a search term")
            return
        }
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(address.windowToken, 0)
        address.clearFocus()
        browser.loadUrl(url)
    }

    private fun invokeVideo(command: String, callback: ((JSONObject?) -> Unit)? = null) {
        if (siteFullView != null) return
        browser.evaluateJavascript(source + "\nJSON.stringify(window.__zoomPlay." + command + ");") { raw ->
            val data = try {
                when (val value = JSONTokener(raw ?: "null").nextValue()) {
                    is String -> JSONObject(value)
                    is JSONObject -> value
                    else -> null
                }
            } catch (_: Exception) { null }
            callback?.invoke(data)
        }
    }
    private fun openVideo() {
        invokeVideo("focus(0)") { result ->
            if (result?.optBoolean("ok") == true) {
                controls.visibility = View.VISIBLE
                zoomSlider.progress = 0
                zoomLabel.text = "1.00×"
                speed = 1.0
                speedButton.text = "1×"
                fit = "contain"
                fitButton.text = "FIT"
                handler.removeCallbacks(statusTask)
                handler.post(statusTask)
                invokeVideo("play()") { if (it?.optBoolean("ok") != true) showResult(it) }
            } else {
                invokeVideo("frameInfo()") { embedded ->
                    val url = embedded?.optString("url") ?: ""
                    if (embedded?.optBoolean("ok") == true && url.startsWith("https://")) {
                        AlertDialog.Builder(this)
                            .setTitle("Embedded player")
                            .setMessage("Try opening the embedded player's website?")
                            .setPositiveButton("Open") { _, _ -> browser.loadUrl(url) }
                            .setNegativeButton("Cancel", null)
                            .show()
                    } else showResult(result)
                }
            }
        }
    }
    private fun hideVideo(callPage: Boolean = true) {
        handler.removeCallbacks(statusTask)
        if (callPage && siteFullView == null) invokeVideo("exit()")
        controls.visibility = View.GONE
        if (fullscreen) toggleFullscreen()
    }
    private fun showResult(data: JSONObject?, success: String = "") {
        if (data?.optBoolean("ok") == true) {
            if (success.isNotBlank()) toast(success)
        } else toast(data?.optString("reason")?.takeIf { it.isNotBlank() }
            ?: "This site has no accessible HTML5 video")
    }
    private fun time(seconds: Double): String {
        if (!seconds.isFinite() || seconds < 0) return "00:00"
        val s = seconds.toLong()
        return if (s >= 3600) String.format(Locale.US, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
        else String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
    }
    private fun updateStatus(data: JSONObject) {
        val duration = data.optDouble("duration", 0.0)
        val current = data.optDouble("current", 0.0)
        timeLabel.text = time(current) + " / " + time(duration)
        if (!dragging) {
            seek.tag = duration
            seek.progress = if (duration > 0) ((current / duration) * 1000).roundToInt().coerceIn(0, 1000) else 0
        }
        playButton.text = if (data.optBoolean("paused", true)) "▶" else "Ⅱ"
        val error = data.optString("error", "")
        infoLabel.text = if (error.isNotBlank()) error.take(110) else
            "VIDEO MODE • " + data.optInt("count", 1) + " detected • Zoom and crop available"
    }

    private fun toggleFullscreen() {
        fullscreen = !fullscreen
        top.visibility = if (fullscreen) View.GONE else View.VISIBLE
        navigation.visibility = if (fullscreen) View.GONE else View.VISIBLE
        fullButton.text = if (fullscreen) "↙" else "⛶"
        showSystemFullscreen(fullscreen)
    }
    @Suppress("DEPRECATION")
    private fun showSystemFullscreen(enabled: Boolean) {
        window.decorView.systemUiVisibility = if (enabled)
            (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        else 0
        root.requestApplyInsets()
    }
    private fun closeSiteFullscreen(notify: Boolean) {
        val old = siteFullView ?: return
        val callback = siteFullCallback
        siteFullView = null
        siteFullCallback = null
        root.removeView(old)
        shell.visibility = View.VISIBLE
        showSystemFullscreen(fullscreen)
        if (notify) callback?.onCustomViewHidden()
    }

    @Deprecated("Single Activity WebView navigation")
    override fun onBackPressed() {
        when {
            siteFullView != null -> closeSiteFullscreen(true)
            fullscreen -> toggleFullscreen()
            controls.visibility == View.VISIBLE -> hideVideo()
            browser.canGoBack() -> browser.goBack()
            else -> super.onBackPressed()
        }
    }
    override fun onSaveInstanceState(state: Bundle) {
        super.onSaveInstanceState(state)
        state.putString("url", browser.url)
    }
    override fun onDestroy() {
        handler.removeCallbacks(statusTask)
        closeSiteFullscreen(true)
        browser.stopLoading()
        browser.destroy()
        super.onDestroy()
    }
}
