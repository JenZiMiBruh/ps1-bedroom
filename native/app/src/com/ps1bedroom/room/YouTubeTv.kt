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
    private val prefs = act.getSharedPreferences("ps1bedroom", Context.MODE_PRIVATE)
    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, act.resources.displayMetrics)
    private fun dpi(v: Float) = dp(v).toInt()
    var ps1Look = prefs.getBoolean("ytPs1", true)
        set(v) { field = v; prefs.edit().putBoolean("ytPs1", v).apply(); web?.evaluateJavascript("document.body.classList.toggle('ps1', $v)", null) }
    var isFull = false; private set
    private val SW = 640; private val SH = 480

    // ---- The screen: a 640x480 view drawn through a perspective matrix ----
    private val matrix = Matrix()
    private val inverse = Matrix()
    private val src = floatArrayOf(0f, 0f, SW.toFloat(), 0f, 0f, SH.toFloat(), SW.toFloat(), SH.toFloat())
    private val dst = FloatArray(8)
    private var show = false
    private val layer = object : FrameLayout(act) {
        override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
            if (isFull) return super.drawChild(canvas, child, drawingTime)
            canvas.save(); canvas.concat(matrix); val r = super.drawChild(canvas, child, drawingTime); canvas.restore(); return r
        }
        private val pt = FloatArray(2)
        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            if (isFull) return super.dispatchTouchEvent(ev)
            if (!show || screen.visibility != View.VISIBLE) return false
            pt[0] = ev.x; pt[1] = ev.y; inverse.mapPoints(pt)
            if (ev.actionMasked == MotionEvent.ACTION_DOWN && (pt[0] < 0 || pt[1] < 0 || pt[0] > SW || pt[1] > SH)) return false
            val e = MotionEvent.obtain(ev); e.transform(inverse)
            val r = screen.dispatchTouchEvent(e); e.recycle(); return r
        }
    }.apply { visibility = View.GONE; setWillNotDraw(false); tag = Cutout.NO_INSET }
    private val screen = FrameLayout(act).apply { setBackgroundColor(Color.rgb(4, 4, 5)) }
    private var web: WebView? = null

    // ---- The bar (YouTube / Full / Stop) and the dialog ----
    private val bar = LinearLayout(act).apply {
        orientation = LinearLayout.HORIZONTAL; visibility = View.GONE; setPadding(dpi(4f), dpi(4f), dpi(4f), dpi(4f))
        background = GradientDrawable().apply { setColor(Color.argb(210, 8, 10, 24)); cornerRadius = dp(999f); setStroke(1, Color.argb(40, 255, 255, 255)) }
    }
    private lateinit var fullBtn: TextView
    private val dialog = FrameLayout(act).apply { visibility = View.GONE; setBackgroundColor(Color.argb(153, 4, 6, 18)); isClickable = true }
    private lateinit var input: EditText
    private lateinit var recentBox: LinearLayout

    init {
        layer.addView(screen, FrameLayout.LayoutParams(SW, SH))
        root.addView(layer, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        fun btn(t: String, red: Boolean, fn: () -> Unit) = TextView(act).apply {
            text = t; setTextColor(Color.rgb(243, 244, 246)); typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(dpi(10f), dpi(6f), dpi(10f), dpi(6f))
            background = GradientDrawable().apply { setColor(if (red) Color.rgb(204, 0, 0) else Color.rgb(31, 35, 53)); cornerRadius = dp(999f) }
            setOnClickListener { fn() }
        }
        bar.addView(btn("📺 YouTube", false) { openDialog() })
        fullBtn = btn("⛶ Full", false) { setFull(!isFull) }
        bar.addView(fullBtn, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dpi(4f) })
        bar.addView(btn("✕", true) { stop() }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dpi(4f) })
        root.addView(bar, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { topMargin = dpi(58f); marginEnd = dpi(10f) })
        buildDialog()
        root.addView(dialog, FrameLayout.LayoutParams(-1, -1))
    }

    private fun buildDialog() {
        val box = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dpi(14f), dpi(14f), dpi(14f), dpi(14f))
            background = GradientDrawable().apply { setColor(Color.rgb(18, 20, 31)); cornerRadius = dp(14f); setStroke(dpi(2f), Color.rgb(43, 48, 71)) }
            isClickable = true
        }
        fun tv(t: String, size: Float, color: Int = Color.rgb(229, 231, 235), bold: Boolean = false) = TextView(act).apply {
            text = t; setTextColor(color); setTextSize(TypedValue.COMPLEX_UNIT_SP, size); typeface = Typeface.create(Typeface.MONOSPACE, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        box.addView(tv("▶  Watch on the bedroom TV", 15f, bold = true))
        input = EditText(act).apply {
            hint = "Paste a YouTube link, or type something to search"; setHintTextColor(Color.rgb(107, 114, 128)); setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); typeface = Typeface.MONOSPACE; isSingleLine = true; imeOptions = EditorInfo.IME_ACTION_GO
            setPadding(dpi(10f), dpi(10f), dpi(10f), dpi(10f))
            background = GradientDrawable().apply { setColor(Color.rgb(10, 11, 18)); cornerRadius = dp(8f); setStroke(1, Color.rgb(59, 65, 96)) }
            setOnEditorActionListener { _, _, _ -> goOrSearch(); true }
        }
        box.addView(input, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(8f) })
        val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        fun rb(t: String, go: Boolean, fn: () -> Unit) = tv(t, 12f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER; setPadding(dpi(6f), dpi(9f), dpi(6f), dpi(9f))
            background = GradientDrawable().apply { setColor(if (go) Color.rgb(204, 0, 0) else Color.rgb(42, 47, 69)); cornerRadius = dp(8f) }
            setOnClickListener { fn() }
        }
        row.addView(rb("▶ PLAY / 🔎", true) { goOrSearch() }, LinearLayout.LayoutParams(0, -2, 1.4f))
        row.addView(rb("📋 PASTE", false) {
            val c = (act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
            val s = c?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(act)?.toString()
            if (s.isNullOrEmpty()) app.toast("Long-press the box and choose Paste") else input.setText(s)
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dpi(6f) })
        row.addView(rb("CLOSE", false) { closeDialog() }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dpi(6f) })
        box.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(8f) })
        box.addView(tv("RECENTLY WATCHED", 12f, Color.rgb(156, 163, 175)).apply { letterSpacing = 0.1f }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dpi(12f) })
        recentBox = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        box.addView(recentBox)
        box.addView(tv("Plays through YouTube's own player and needs the internet. Some videos don't allow playing inside other apps; YouTube will say so. Searching opens YouTube: find a video, tap Share, and pick PS1 Bedroom (or copy the link and paste it here).", 11f, Color.rgb(156, 163, 175)),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(8f) })
        val scroll = ScrollView(act).apply { addView(box) }
        dialog.addView(scroll, FrameLayout.LayoutParams(minOf(dpi(520f), act.resources.displayMetrics.widthPixels - dpi(32f)), -2, Gravity.CENTER))
        dialog.setOnClickListener { closeDialog() }
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
    private val ORIGIN = "https://ps1bedroom.app"
    private fun embedUrl(v: Vid): String {
        val p = StringBuilder("autoplay=1&playsinline=1&rel=0&modestbranding=1&iv_load_policy=3&fs=0&origin=").append(URLEncoder.encode(ORIGIN, "UTF-8"))
        if (v.list != null) p.append("&list=").append(URLEncoder.encode(v.list, "UTF-8"))
        if (v.start > 0) p.append("&start=").append(v.start)
        return if (v.id != null) "https://www.youtube.com/embed/${v.id}?$p" else "https://www.youtube.com/embed/videoseries?$p"
    }
    /** The page in the WebView: the player inset in a black tube face, with scanlines (and the PS1 pixel grid). */
    private fun page(v: Vid) = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><style>
        html,body{margin:0;height:100%;background:#040405;overflow:hidden}
        iframe,.scan,.px{position:absolute;left:3.6%;top:3.6%;width:92.8%;height:92.8%;border-radius:28px/32px}
        body.full iframe,body.full .scan,body.full .px{left:0;top:0;width:100%;height:100%;border-radius:10px}
        iframe{border:0;display:block;filter:saturate(1.12) contrast(1.05) brightness(0.96)}
        .scan{pointer-events:none;background:repeating-linear-gradient(rgba(0,0,0,.14) 0 2px,transparent 2px 4px);box-shadow:inset 0 0 70px rgba(0,0,0,.7),inset 0 0 8px rgba(0,0,0,.8)}
        body{--px:6px}
        .px{pointer-events:none;display:none;background-image:linear-gradient(90deg,rgba(0,0,0,.2) 1px,transparent 1px),linear-gradient(rgba(0,0,0,.2) 1px,transparent 1px);background-size:var(--px) var(--px);mix-blend-mode:multiply}
        body.ps1 .px{display:block}
        body.ps1 iframe{filter:saturate(1.3) contrast(1.14) brightness(.95) blur(.35px)}
        body.ps1 .scan{background:repeating-linear-gradient(rgba(0,0,0,.2) 0 calc(var(--px)/2),transparent calc(var(--px)/2) var(--px))}
        </style></head><body class="${if (ps1Look) "ps1" else ""}${if (isFull) " full" else ""}">
        <iframe src="${embedUrl(v)}" allow="autoplay; encrypted-media; picture-in-picture" referrerpolicy="strict-origin-when-cross-origin" title="YouTube on the bedroom TV"></iframe>
        <div class="scan"></div><div class="px"></div></body></html>"""

    @SuppressLint("SetJavaScriptEnabled")
    private fun newWeb(): WebView = WebView(act).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        webChromeClient = WebChromeClient()
        setBackgroundColor(Color.rgb(4, 4, 5))
    }

    // ---- Recently watched (SharedPreferences, like the HTML's localStorage) ----
    private fun loadRecent(): List<JSONObject> = try { JSONArray(prefs.getString("yt.recent", "[]")).let { a -> List(a.length()) { a.getJSONObject(it) } } } catch (e: Exception) { emptyList() }
    private fun saveRecent(l: List<JSONObject>) { prefs.edit().putString("yt.recent", JSONArray(l.take(12)).toString()).apply() }

    fun play(v: Vid?, label: String? = null) {
        if (v == null) { app.toast("📺 That doesn't look like a YouTube link"); return }
        app.post { if (!app.tv.bedroomTvOn) app.tvPower() }
        web?.let { screen.removeView(it); it.destroy() }
        val w = newWeb(); web = w
        screen.addView(w, 0, FrameLayout.LayoutParams(-1, -1))
        w.loadDataWithBaseURL("$ORIGIN/", page(v), "text/html", "utf-8", null)
        app.ytActive = true; lastPts.fill(Float.NaN)   // the first placement always lands
        layer.visibility = View.VISIBLE; screen.visibility = View.INVISIBLE   // until the first placement
        bar.visibility = View.VISIBLE
        closeDialog()
        app.post { app.sound.updateLayered(); app.sfx("tvChannel") }   // the TV's own channel sound steps aside
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
        web?.let { screen.removeView(it); it.destroy() }; web = null
        app.ytActive = false
        setFull(false)
        layer.visibility = View.GONE; bar.visibility = View.GONE
        app.post { app.sound.updateLayered(); if (!quiet) app.toast("📺 Back to ${Tv.TV_CHANNEL_NAMES[app.tv.bedroomTvChannel]}") }
    }
    fun setFull(f: Boolean) {
        isFull = f
        fullBtn.text = if (f) "📺 On TV" else "⛶ Full"
        screen.layoutParams = if (f) FrameLayout.LayoutParams(-1, -1) else FrameLayout.LayoutParams(SW, SH)
        layer.setBackgroundColor(if (f) Color.BLACK else Color.TRANSPARENT)
        if (f) screen.visibility = View.VISIBLE
        web?.evaluateJavascript("document.body.classList.toggle('full', $f)", null)
        layer.invalidate()
    }
    val dialogOpen get() = dialog.visibility == View.VISIBLE
    fun openDialog() {
        recentBox.removeAllViews()
        val rec = loadRecent()
        if (rec.isEmpty()) recentBox.addView(TextView(act).apply { text = "Nothing yet."; setTextColor(Color.rgb(156, 163, 175)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f) })
        for (r in rec) {
            val id = r.optString("id").ifEmpty { null }; val list = r.optString("list").ifEmpty { null }; val title = r.optString("title")
            recentBox.addView(TextView(act).apply {
                text = "▶  $title"; setTextColor(Color.rgb(229, 231, 235)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); typeface = Typeface.MONOSPACE
                isSingleLine = true; ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dpi(8f), dpi(9f), dpi(8f), dpi(9f))
                background = GradientDrawable().apply { setColor(Color.rgb(10, 11, 18)); cornerRadius = dp(8f); setStroke(1, Color.rgb(43, 48, 71)) }
                setOnClickListener { play(Vid(id, list), title) }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(6f) })
        }
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
        if (!app.ytActive) return@Runnable
        synchronized(pending) { System.arraycopy(pending, 0, dst, 0, 8) }
        show = pendingShow
        if (isFull) return@Runnable
        screen.visibility = if (show) View.VISIBLE else View.INVISIBLE
        if (!show) return@Runnable
        matrix.setPolyToPoly(src, 0, dst, 0, 4); matrix.invert(inverse)
        if (ps1Look && kotlin.math.abs(pendingPx - lastPx) > 0.4f) { lastPx = pendingPx; web?.evaluateJavascript("document.body.style.setProperty('--px','${"%.2f".format(java.util.Locale.US, pendingPx / act.resources.displayMetrics.density)}px')", null) }
        layer.invalidate()
    }
    /** GL thread: corners TL, TR, BL, BR in view pixels (src is in the same order), and the PS1 grid size. */
    private val lastPts = FloatArray(8); private var lastShow = false
    fun place(pts: FloatArray, visible: Boolean, px: Float) {
        // A still camera gives the same corners every frame: no UI-thread work then
        if (visible == lastShow && pts.contentEquals(lastPts)) return
        System.arraycopy(pts, 0, lastPts, 0, 8); lastShow = visible
        synchronized(pending) { System.arraycopy(pts, 0, pending, 0, 8) }
        pendingShow = visible; pendingPx = px
        layer.removeCallbacks(applyPlacement); layer.post(applyPlacement)
    }
}
