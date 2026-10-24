package com.ps1bedroom.room

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * HTML 16936-17177: YOUTUBE ON THE BEDROOM TV. Paste a link (or Share a video to PS1 Bedroom) and it plays on the
 * CRT. YouTube's own player runs in a WebView pinned exactly over the TV's glass with a perspective transform from
 * the screen's 4 corners (worked out on the GL thread every frame), so the room stays around it.
 */
class YouTubeTv(private val act: Activity, private val root: FrameLayout, private val app: App) {
    class Vid(val id: String?, val list: String?, val start: Int = 0)
    private val T = UiTheme
    private val prefs = act.getSharedPreferences("ps1bedroom", Context.MODE_PRIVATE)
    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, act.resources.displayMetrics)
    private fun dpi(v: Float) = dp(v).toInt()
    var ps1Look = prefs.getBoolean("ytPs1", true)
        set(v) { field = v; prefs.edit().putBoolean("ytPs1", v).apply(); web?.evaluateJavascript("document.body.classList.toggle('ps1', $v)", null) }
    var isFull = false; private set
    private val SW = 640; private val SH = 480

    // ---- The screen: a 640x480 view pinned onto the TV glass ----
    private val matrix = Matrix()
    private val inverse = Matrix()
    private val src = floatArrayOf(0f, 0f, SW.toFloat(), 0f, 0f, SH.toFloat(), SW.toFloat(), SH.toFloat())
    private val dst = FloatArray(8)
    private var show = false
    private val layer = object : FrameLayout(act) {
        override fun dispatchTouchEvent(ev: MotionEvent): Boolean = if (isFull) super.dispatchTouchEvent(ev) else false
    }.apply { visibility = View.GONE; tag = Cutout.NO_INSET }

    private val screen = FrameLayout(act).apply { setBackgroundColor(Color.BLACK) }
    @Volatile var active = false; private set
    private var web: WebView? = null

    // ---- The bar (YouTube / Full / Stop) and the dialog ----
    private val bar = LinearLayout(act).apply {
        orientation = LinearLayout.HORIZONTAL; visibility = View.GONE; setPadding(dpi(4f), dpi(4f), dpi(4f), dpi(4f))
    }
    private val barBtns = ArrayList<Pair<TextView, Boolean>>()
    private lateinit var fullBtn: TextView
    private lateinit var playBtn: TextView
    private val dialog = FrameLayout(act).apply { visibility = View.GONE; isClickable = true }
    private lateinit var dialogBox: LinearLayout
    private lateinit var dialogTitle: TextView
    private lateinit var recentHead: TextView
    private lateinit var dialogNote: TextView
    private val dialogBtns = ArrayList<Pair<TextView, Boolean>>()
    private lateinit var input: EditText
    private lateinit var recentBox: LinearLayout

    init {
        layer.addView(screen, FrameLayout.LayoutParams(SW, SH))
        // Insert right above the GLSurfaceView (index 1) so the HUD dock, menu, PC desktop, and boombox sit above it when on the TV
        root.addView(layer, minOf(1, root.childCount), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        fun btn(t: String, red: Boolean, fn: () -> Unit) = TextView(act).apply {
            text = t; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(dpi(10f), dpi(6f), dpi(10f), dpi(6f))
            isFocusable = true
            setOnClickListener { fn() }
            barBtns += this to red
        }
        // #21: drag the grip to move the buttons anywhere (remembered), tap – to tuck them into a small tab
        val grip = btn("⠿", false) { }
        grip.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0f; var sy = 0f; var tx = 0f; var ty = 0f
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; tx = bar.translationX; ty = bar.translationY }
                    MotionEvent.ACTION_MOVE -> {
                        bar.translationX = tx + e.rawX - sx
                        bar.translationY = ty + e.rawY - sy
                        clampBar()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        clampBar()
                        prefs.edit().putFloat("ytBarX", bar.translationX).putFloat("ytBarY", bar.translationY).apply()
                    }
                }
                return true
            }
        })
        bar.addView(grip)
        val main = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        playBtn = btn("⏸", false) { togglePlay() }
        main.addView(playBtn)
        main.addView(btn("📺 YouTube", false) { openDialog() }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dpi(4f) })
        fullBtn = btn("⛶ Full", false) { setFull(!isFull) }
        main.addView(fullBtn, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dpi(4f) })
        main.addView(btn("✕ Stop", true) { stop() }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dpi(4f) })
        bar.addView(main, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dpi(4f) })
        lateinit var tuck: TextView
        tuck = btn("–", false) {
            val hide = main.visibility == View.VISIBLE
            main.visibility = if (hide) View.GONE else View.VISIBLE
            tuck.text = if (hide) "📺" else "–"
            prefs.edit().putBoolean("ytBarTucked", hide).apply()
            bar.post { clampBar() }
        }
        bar.addView(tuck, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dpi(4f) })
        if (prefs.getBoolean("ytBarTucked", false)) { main.visibility = View.GONE; tuck.text = "📺" }
        bar.translationX = prefs.getFloat("ytBarX", 0f); bar.translationY = prefs.getFloat("ytBarY", 0f)
        bar.elevation = dp(18f)
        // Bottom-right thumb-friendly placement above the main dock, with automatic screen-bounds clamping
        root.addView(bar, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { bottomMargin = dpi(114f); marginEnd = dpi(12f) })
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> bar.post { clampBar() } }
        buildDialog()
        root.addView(dialog, FrameLayout.LayoutParams(-1, -1))
        T.listeners += { restyle() }
        restyle()
    }

    /** Ensures the floating YouTube control bar can never drift or rotate off-screen. */
    fun clampBar() {
        val rw = root.width.takeIf { it > 0 } ?: act.resources.displayMetrics.widthPixels
        val rh = root.height.takeIf { it > 0 } ?: act.resources.displayMetrics.heightPixels
        val bw = bar.width.takeIf { it > 0 } ?: dpi(240f)
        val bh = bar.height.takeIf { it > 0 } ?: dpi(42f)
        val minTx = -(rw - bw - dpi(24f)).coerceAtLeast(0).toFloat()
        val maxTx = 0f
        val minTy = -(rh - bh - dpi(130f)).coerceAtLeast(0).toFloat()
        val maxTy = dpi(48f).toFloat()
        bar.translationX = bar.translationX.coerceIn(minTx, maxTx)
        bar.translationY = bar.translationY.coerceIn(minTy, maxTy)
    }

    private fun buildDialog() {
        dialogBox = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dpi(14f), dpi(14f), dpi(14f), dpi(14f))
            isClickable = true
        }
        fun tv(t: String, size: Float, color: Int = T.text, bold: Boolean = false) = TextView(act).apply {
            text = t; setTextColor(color); setTextSize(TypedValue.COMPLEX_UNIT_SP, size); typeface = Typeface.create(Typeface.MONOSPACE, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        dialogTitle = tv("▶  Watch on the bedroom TV", 15f, bold = true)
        dialogBox.addView(dialogTitle)
        input = EditText(act).apply {
            hint = "Paste a YouTube link, or type something to search"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); typeface = Typeface.MONOSPACE; isSingleLine = true; imeOptions = EditorInfo.IME_ACTION_GO
            setPadding(dpi(10f), dpi(10f), dpi(10f), dpi(10f))
            setOnEditorActionListener { _, _, _ -> goOrSearch(); true }
        }
        dialogBox.addView(input, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(8f) })
        val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        fun rb(t: String, go: Boolean, fn: () -> Unit) = tv(t, 12f, if (go) Color.WHITE else T.text, true).apply {
            gravity = Gravity.CENTER; setPadding(dpi(6f), dpi(9f), dpi(6f), dpi(9f))
            setOnClickListener { fn() }
            dialogBtns += this to go
        }
        row.addView(rb("▶ PLAY / 🔎", true) { goOrSearch() }, LinearLayout.LayoutParams(0, -2, 1.4f))
        row.addView(rb("📋 PASTE", false) {
            val c = (act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
            val s = c?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(act)?.toString()
            if (s.isNullOrEmpty()) app.toast("Long-press the box and choose Paste") else input.setText(s)
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dpi(6f) })
        row.addView(rb("CLOSE", false) { closeDialog() }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dpi(6f) })
        dialogBox.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(8f) })
        recentHead = tv("RECENTLY WATCHED", 12f, T.muted).apply { letterSpacing = 0.1f }
        dialogBox.addView(recentHead, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dpi(12f) })
        recentBox = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        dialogBox.addView(recentBox)
        dialogNote = tv("Plays through YouTube's own player and needs the internet. Some videos don't allow playing inside other apps; YouTube will say so. Searching opens YouTube: find a video, tap Share, and pick PS1 Bedroom (or copy the link and paste it here).", 11f, T.muted)
        dialogBox.addView(dialogNote, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(8f) })
        val scroll = ScrollView(act).apply { addView(dialogBox) }
        dialog.addView(scroll, FrameLayout.LayoutParams(minOf(dpi(520f), act.resources.displayMetrics.widthPixels - dpi(28f)), -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dpi(24f) })
        dialog.setOnClickListener { closeDialog() }
    }

    private fun restyle() {
        bar.background = GradientDrawable().apply { setColor(T.panel); cornerRadius = dp(999f); setStroke(dpi(1f), T.line) }
        for ((b, red) in barBtns) {
            b.setTextColor(if (red) Color.WHITE else T.text)
            b.background = GradientDrawable().apply { setColor(if (red) Color.rgb(204, 0, 0) else T.panel2); cornerRadius = dp(999f) }
        }
        dialog.setBackgroundColor(T.scrim)
        dialogBox.background = GradientDrawable().apply { setColor(T.panel); cornerRadius = dp(14f); setStroke(dpi(2f), T.line) }
        dialogTitle.setTextColor(T.text); recentHead.setTextColor(T.muted); dialogNote.setTextColor(T.muted)
        input.setHintTextColor(T.muted); input.setTextColor(T.text)
        input.background = GradientDrawable().apply { setColor(T.field); cornerRadius = dp(8f); setStroke(1, T.line) }
        for ((b, go) in dialogBtns) {
            b.setTextColor(if (go) Color.WHITE else T.text)
            b.background = GradientDrawable().apply { setColor(if (go) Color.rgb(204, 0, 0) else T.panel2); cornerRadius = dp(8f); if (!go) setStroke(1, T.line) }
        }
        if (dialogOpen) renderRecent()
    }

    // ---- Links: youtu.be, watch?v=, shorts/, live/, embed/, playlists, bare IDs, or "Check this out https://..." ----
    fun parse(text0: String?): Vid? {
        var s = (text0 ?: "").trim()
        Regex("""https?://\S+""").find(s)?.let { s = it.value }
        if (Regex("""^[\w-]{11}$""").matches(s)) return Vid(s, null)
        val u = try { Uri.parse(if (s.contains("://")) s else "https://$s") } catch (e: Exception) { return null }
        val host = (u.host ?: return null).replace(Regex("^(www|m|music)\\."), "")
        var id: String? = null
        if (host == "youtu.be") id = u.path?.drop(1)?.take(11)
        else if (Regex("""(^|\.)youtube(-nocookie)?\.com$""").containsMatchIn(host)) {
            id = try { u.getQueryParameter("v") } catch (e: Exception) { null }
            Regex("""/(shorts|live|embed|v)/([\w-]{11})""").find(u.path ?: "")?.let { id = it.groupValues[2] }
        } else return null
        val q = { k: String -> try { u.getQueryParameter(k) } catch (e: Exception) { null } }
        val list = q("list")
        val start = (q("t") ?: q("start"))?.let { Regex("""^\d+""").find(it)?.value?.toIntOrNull() } ?: 0
        if (id != null && !Regex("""^[\w-]{11}$""").matches(id!!)) id = null
        return if (id != null || list != null) Vid(id, list, start) else null
    }
    private val ORIGIN = "https://jenzimibruh.github.io"
    private fun embedUrl(v: Vid): String {
        val p = StringBuilder("autoplay=1&playsinline=1&enablejsapi=1&rel=0&modestbranding=1&iv_load_policy=3&fs=0&origin=").append(URLEncoder.encode(ORIGIN, "UTF-8"))
        if (v.list != null) p.append("&list=").append(URLEncoder.encode(v.list, "UTF-8"))
        if (v.start > 0) p.append("&start=").append(v.start)
        return if (v.id != null) "https://www.youtube.com/embed/${v.id}?$p" else "https://www.youtube.com/embed/videoseries?$p"
    }
    /** The page in the WebView: the player fits edge-to-edge flush with no border on the TV display. */
    private fun page(v: Vid) = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"><style>
        html,body{margin:0;padding:0;width:100%;height:100%;background:#000;overflow:hidden}
        iframe{position:absolute;left:0;top:0;width:100%;height:100%;border:0;margin:0;padding:0;display:block;filter:saturate(1.12) contrast(1.05) brightness(0.96)}
        .scan{position:absolute;left:0;top:0;width:100%;height:100%;pointer-events:none;background:repeating-linear-gradient(rgba(0,0,0,.10) 0 2px,transparent 2px 4px);box-shadow:none}
        body{--px:6px}
        .px{position:absolute;left:0;top:0;width:100%;height:100%;pointer-events:none;display:none;background-image:linear-gradient(90deg,rgba(0,0,0,.2) 1px,transparent 1px),linear-gradient(rgba(0,0,0,.2) 1px,transparent 1px);background-size:var(--px) var(--px);mix-blend-mode:multiply}
        body.ps1 .px{display:block}
        body.ps1 iframe{filter:saturate(1.3) contrast(1.14) brightness(.95) blur(.35px)}
        body.ps1 .scan{background:repeating-linear-gradient(rgba(0,0,0,.16) 0 calc(var(--px)/2),transparent calc(var(--px)/2) var(--px))}
        </style></head><body class="${if (ps1Look) "ps1" else ""}${if (isFull) " full" else ""}">
        <iframe src="${embedUrl(v)}" allow="autoplay; encrypted-media; picture-in-picture" referrerpolicy="strict-origin-when-cross-origin" title="YouTube on the bedroom TV"></iframe>
        <div class="scan"></div><div class="px"></div></body></html>"""

    // ---- v0.48: the player really lives ON the TV. It runs on a private off-screen display whose pixels go straight
    // into the CRT's screen texture (YouTubeFeed), so it's lit, wobbles and curves with the glass like any channel.
    // Only Full screen moves the player into the normal window. ----
    private var vd: android.hardware.display.VirtualDisplay? = null
    private var pres: android.app.Presentation? = null
    private var presRoot: FrameLayout? = null
    private var feedSurface: android.view.Surface? = null
    private fun ensureTvDisplay(): FrameLayout? {
        presRoot?.let { return it }
        val s = feedSurface ?: return null
        return try {
            val dm = act.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
            val flags = android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
            val d = dm.createVirtualDisplay("PS1BedroomTV", SW, SH, 160, s, flags)
                ?: dm.createVirtualDisplay("PS1BedroomTV", SW, SH, 160, s, 0)
                ?: return null
            vd = d
            val rootV = FrameLayout(act).apply { setBackgroundColor(Color.rgb(4, 4, 5)) }
            val p = android.app.Presentation(act, d.display)
            p.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.BLACK))
            p.setContentView(rootV)
            p.setCancelable(false)
            p.show()
            pres = p; presRoot = rootV
            rootV
        } catch (e: Exception) {
            android.util.Log.w("PS1Bedroom", "TV display failed, trying fallback flag 0", e)
            try {
                val dm = act.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
                val d = dm.createVirtualDisplay("PS1BedroomTV", SW, SH, 160, s, 0) ?: return null
                vd = d
                val rootV = FrameLayout(act).apply { setBackgroundColor(Color.rgb(4, 4, 5)) }
                val p = android.app.Presentation(act, d.display)
                p.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.BLACK))
                p.setContentView(rootV)
                p.setCancelable(false)
                p.show()
                pres = p; presRoot = rootV
                rootV
            } catch (e2: Exception) {
                android.util.Log.w("PS1Bedroom", "TV display fallback failed", e2)
                null
            }
        }
    }
    private fun releaseTvDisplay() {
        try { pres?.dismiss() } catch (_: Exception) { }
        try { vd?.release() } catch (_: Exception) { }
        pres = null; presRoot = null; vd = null
    }
    /** Puts the WebView where it should be: on the TV's own display, or the real window when Full. */
    private fun placeWeb() {
        val w = web ?: return
        val target: ViewGroup = if (isFull) screen else (ensureTvDisplay() ?: screen)
        if (w.parent !== target) {
            (w.parent as? ViewGroup)?.removeView(w)
            target.addView(w, 0, FrameLayout.LayoutParams(-1, -1))
        }
        val onTv = target !== screen
        layer.visibility = if (isFull || (!onTv && active)) View.VISIBLE else View.GONE
        screen.visibility = if (isFull) View.VISIBLE else if (onTv) View.GONE else View.INVISIBLE
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun newWeb(): WebView = WebView(act).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        // Strip the Android WebView "; wv" marker so YouTube's embed player doesn't block playback inside WebView
        settings.userAgentString = settings.userAgentString.replace("; wv", "").replace("Version/4.0 ", "")
        webChromeClient = WebChromeClient()
        webViewClient = android.webkit.WebViewClient()
        try { android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true) } catch (_: Exception) { }
        setBackgroundColor(Color.BLACK)
    }

    // ---- Recently watched (SharedPreferences, like the HTML's localStorage) ----
    private fun loadRecent(): List<JSONObject> = try { JSONArray(prefs.getString("yt.recent", "[]")).let { a -> List(a.length()) { a.getJSONObject(it) } } } catch (e: Exception) { emptyList() }
    private fun saveRecent(l: List<JSONObject>) { prefs.edit().putString("yt.recent", JSONArray(l.take(12)).toString()).apply() }

    fun play(v: Vid?, label: String? = null) {
        if (v == null) { app.toast("📺 That doesn't look like a YouTube link"); return }
        web?.let {
            try { it.loadUrl("about:blank"); it.onPause() } catch (_: Exception) { }
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        val w = newWeb(); web = w
        active = true
        placeWeb()
        w.loadDataWithBaseURL("$ORIGIN/", page(v), "text/html", "utf-8", null)
        if (feedSurface == null) {
            app.ensureYtFeed { s ->
                act.runOnUiThread {
                    feedSurface = s
                    if (active) placeWeb()
                }
            }
        }
        playing = true
        playBtn.text = "⏸"
        bar.visibility = View.VISIBLE
        bar.bringToFront()
        bar.post { clampBar() }
        closeDialog()
        // Turn on the TV synchronously on the GL thread BEFORE setting ytActive = true so updateBedroomTvScreen paints black
        app.post {
            if (!app.tv.bedroomTvOn) app.tv.toggleBedroomTv(app.lastFrameT)
            app.tv.ytShowing = true
            app.tv.updateBedroomTvScreen(app.lastFrameT)
            app.ytActive = true
            app.sound.updateLayered()
            app.sfx("tvChannel")
            app.settings.changed()
        }
        val rec = loadRecent().filter { !(it.optString("id") == (v.id ?: "") && it.optString("list") == (v.list ?: "")) }.toMutableList()
        rec.add(0, JSONObject().put("id", v.id ?: "").put("list", v.list ?: "").put("title", label ?: (if (v.id != null) "youtu.be/${v.id}" else "Playlist")))
        saveRecent(rec)
        // Fill in the real title from YouTube (oEmbed); titles are a nice-to-have
        if (label == null && v.id != null) Thread {
            try {
                val c = URL("https://www.youtube.com/oembed?format=json&url=" + URLEncoder.encode("https://www.youtube.com/watch?v=${v.id}", "UTF-8")).openConnection() as HttpURLConnection
                c.connectTimeout = 6000; c.readTimeout = 6000
                val title = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).optString("title")
                if (title.isNotEmpty()) act.runOnUiThread { saveRecent(loadRecent().map { if (it.optString("id") == v.id) it.put("title", title) else it }) }
            } catch (e: Exception) { }
        }.start()
        app.toast("📺 YouTube on the bedroom TV. Tap ⛶ Full to watch full screen.")
    }
    fun playLink(text: String?) = play(parse(text))
    fun stop(quiet: Boolean = false) {
        active = false
        app.ytActive = false
        feedSurface = null
        web?.let {
            try {
                it.evaluateJavascript("(function(){var f=document.querySelector('iframe');if(f)f.src='about:blank';})()", null)
                it.loadUrl("about:blank")
                it.onPause()
            } catch (_: Exception) { }
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        web = null
        releaseTvDisplay()
        setFull(false)
        layer.visibility = View.GONE; screen.visibility = View.GONE; bar.visibility = View.GONE
        app.releaseYtFeed()
        app.post {
            app.tv.ytShowing = false
            app.tv.useFeed(null)
            app.tv.updateBedroomTvScreen(app.lastFrameT)
            app.sound.updateLayered()
            app.settings.changed()
            if (!quiet && app.tv.bedroomTvOn) app.toast("📺 Back to ${Tv.TV_CHANNEL_NAMES[app.tv.bedroomTvChannel]}")
        }
    }
    /** Play / pause through the YouTube player's own API (the TV can't be touched like a screen). */
    var playing = true; private set
    fun togglePlay() {
        playing = !playing
        web?.evaluateJavascript("(function(){var f=document.querySelector('iframe');if(f)f.contentWindow.postMessage(JSON.stringify({event:'command',func:'${if (playing) "playVideo" else "pauseVideo"}',args:[]}),'*');})()", null)
        app.toast(if (playing) "📺 ▶ Playing" else "📺 ⏸ Paused (tap the TV to keep watching)")
        playBtn.text = if (playing) "⏸" else "▶"
    }
    fun setFull(f: Boolean) {
        isFull = f
        fullBtn.text = if (f) "📺 On TV" else "⛶ Full"
        screen.layoutParams = if (f) FrameLayout.LayoutParams(-1, -1) else FrameLayout.LayoutParams(SW, SH)
        layer.elevation = if (f) dp(20f) else 0f
        bar.elevation = dp(26f)
        if (f) {
            screen.scaleX = 1f; screen.scaleY = 1f; screen.rotation = 0f; screen.translationX = 0f; screen.translationY = 0f
            screen.visibility = View.VISIBLE
            layer.visibility = View.VISIBLE
            layer.setBackgroundColor(Color.BLACK)
            bar.bringToFront()
        } else {
            layer.setBackgroundColor(Color.TRANSPARENT)
            layer.visibility = View.GONE
            screen.visibility = View.GONE
        }
        web?.evaluateJavascript("document.body.classList.toggle('full', $f)", null)
        if (web != null) placeWeb() else if (!f) layer.visibility = View.GONE
        layer.invalidate()
    }
    val dialogOpen get() = dialog.visibility == View.VISIBLE
    private fun renderRecent() {
        recentBox.removeAllViews()
        val rec = loadRecent()
        if (rec.isEmpty()) recentBox.addView(TextView(act).apply { text = "Nothing yet."; setTextColor(T.muted); setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f) })
        for (r in rec) {
            val id = r.optString("id").ifEmpty { null }; val list = r.optString("list").ifEmpty { null }; val title = r.optString("title")
            recentBox.addView(TextView(act).apply {
                text = "▶  $title"; setTextColor(T.text); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); typeface = Typeface.MONOSPACE
                isSingleLine = true; ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dpi(8f), dpi(9f), dpi(8f), dpi(9f))
                background = GradientDrawable().apply { setColor(T.panel2); cornerRadius = dp(8f); setStroke(1, T.line) }
                setOnClickListener { play(Vid(id, list), title) }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(6f) })
        }
    }
    fun openDialog() {
        renderRecent()
        dialog.visibility = View.VISIBLE
        app.post { app.sfx("ui") }
    }
    fun closeDialog() {
        (act.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
        dialog.visibility = View.GONE
    }
    /** A link plays; anything else is a search, opened in the YouTube app / browser (Share a video back to play it). */
    private fun goOrSearch() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) { input.requestFocus(); return }
        val v = parse(text)
        if (v != null) { play(v); return }
        try { act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + URLEncoder.encode(text, "UTF-8")))) } catch (e: Exception) { }
        app.toast("🔎 Pick a video, tap Share, then PS1 Bedroom (or copy the link and paste it here)")
    }
    /** Back: dialog, then full screen. */
    fun back(): Boolean {
        if (dialogOpen) { closeDialog(); return true }
        if (isFull) { setFull(false); return true }
        return false
    }

    // ---- Pinning onto the glass (the GL thread hands over the 4 corners in screen pixels) ----
    private val pending = FloatArray(8)
    @Volatile private var pendingShow = false
    @Volatile private var pendingPx = 6f
    private var lastPx = 0f
    private val applyPlacement: Runnable = Runnable {
        if (!app.ytActive && !active) return@Runnable
        synchronized(pending) { System.arraycopy(pending, 0, dst, 0, 8) }
        show = pendingShow
        if (isFull) return@Runnable
        // hidden when the TV faces away, is behind the camera, or is off the screen entirely
        val minX = minOf(minOf(dst[0], dst[2]), minOf(dst[4], dst[6])); val maxX = maxOf(maxOf(dst[0], dst[2]), maxOf(dst[4], dst[6]))
        val minY = minOf(minOf(dst[1], dst[3]), minOf(dst[5], dst[7])); val maxY = maxOf(maxOf(dst[1], dst[3]), maxOf(dst[5], dst[7]))
        val onScreen = maxX > 0 && maxY > 0 && minX < layer.width && minY < layer.height
        screen.visibility = if (show && onScreen) View.VISIBLE else View.INVISIBLE
        if (!show || !onScreen) return@Runnable
        // the glass's corners TL, TR, BL -> position, size and tilt (the TV's slight perspective is averaged out)
        val ax = (dst[0] + dst[4]) / 2; val ay = (dst[1] + dst[5]) / 2                 // middle of the left edge
        val bx = (dst[2] + dst[6]) / 2; val by = (dst[3] + dst[7]) / 2                 // middle of the right edge
        val width = kotlin.math.hypot((bx - ax).toDouble(), (by - ay).toDouble()).toFloat()
        val height = (kotlin.math.hypot((dst[4] - dst[0]).toDouble(), (dst[5] - dst[1]).toDouble()) + kotlin.math.hypot((dst[6] - dst[2]).toDouble(), (dst[7] - dst[3]).toDouble())).toFloat() / 2
        val ang = Math.toDegrees(kotlin.math.atan2((by - ay).toDouble(), (bx - ax).toDouble())).toFloat()
        val cx = (dst[0] + dst[2] + dst[4] + dst[6]) / 4; val cy = (dst[1] + dst[3] + dst[5] + dst[7]) / 4
        screen.pivotX = SW / 2f; screen.pivotY = SH / 2f
        screen.scaleX = (width * 1.01f) / SW; screen.scaleY = (height * 1.01f) / SH; screen.rotation = ang
        screen.translationX = cx - SW / 2f; screen.translationY = cy - SH / 2f
        if (ps1Look && kotlin.math.abs(pendingPx - lastPx) > 0.4f) { lastPx = pendingPx; web?.evaluateJavascript("document.body.style.setProperty('--px','${"%.2f".format(java.util.Locale.US, pendingPx / act.resources.displayMetrics.density)}px')", null) }
        layer.invalidate()
    }
    /** GL thread: corners TL, TR, BL, BR in view pixels (src is in the same order), and the PS1 grid size. */
    private val lastPts = FloatArray(8); private var lastShow = false
    fun place(pts: FloatArray, visible: Boolean, px: Float) {
        // When rendered into the TV's own display (YouTubeFeed), no window overlay is displayed
        if (presRoot != null) return
        // A still camera gives the same corners every frame: no UI-thread work then
        if (visible == lastShow && pts.contentEquals(lastPts)) return
        System.arraycopy(pts, 0, lastPts, 0, 8); lastShow = visible
        synchronized(pending) { System.arraycopy(pts, 0, pending, 0, 8) }
        pendingShow = visible; pendingPx = px
        layer.removeCallbacks(applyPlacement); layer.post(applyPlacement)
    }
}
