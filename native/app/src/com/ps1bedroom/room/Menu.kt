package com.ps1bedroom.room

import android.annotation.SuppressLint
import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * The top bar (MENU + the status chip whose parts step through options) and the menu sheet. v0.44: the sheet opens
 * on a home page (quick toggles + one card per category); each category has a few short sub-tabs instead of one long
 * scroll, and every colour comes from [UiTheme] (light / dark / phone / room clock, plus colour themes).
 * UI thread only: every change is posted to the GL thread through the app.
 */
@SuppressLint("SetTextI18n", "ClickableViewAccessibility")
class Menu(private val act: Activity, private val root: FrameLayout, private val app: App) {
    private val T = UiTheme
    private val PANEL get() = T.panel
    private val PANEL2 get() = T.panel2
    private val LINE get() = T.line
    private val LINE_HI get() = T.lineHi
    private val TEXT get() = T.text
    private val MUTED get() = T.muted
    private val PINK get() = T.accent
    private val CYAN get() = T.accent2
    private val GOLD get() = T.gold
    private val ACTIVE_BG get() = T.activeBg
    private val ON_TEXT get() = T.activeText
    private val SEP get() = T.sep
    private val MONO = Typeface.create("monospace", Typeface.BOLD)
    private val MONO_N = Typeface.create("monospace", Typeface.NORMAL)
    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, act.resources.displayMetrics)
    private fun dpi(v: Float) = dp(v).toInt()
    private val s get() = app.settings
    private val env get() = app.env
    private fun post(fn: () -> Unit) { app.post(fn) }

    private fun box(fill: Int, stroke: Int, radius: Float, shadow: Float = 0f, strokeW: Float = 2f): android.graphics.drawable.Drawable {
        val bg = GradientDrawable().apply { setColor(fill); cornerRadius = dp(radius); if (stroke != 0) setStroke(dpi(strokeW), stroke) }
        if (shadow <= 0f) return bg
        val sh = GradientDrawable().apply { setColor(T.shadow); cornerRadius = dp(radius) }
        return LayerDrawable(arrayOf(sh, bg)).apply { setLayerInset(0, dpi(shadow), dpi(shadow), 0, 0); setLayerInset(1, 0, 0, dpi(shadow), dpi(shadow)) }
    }
    private fun text(t: String, color: Int = TEXT, size: Float = 12f, bold: Boolean = true) = TextView(act).apply {
        text = t; setTextColor(color); typeface = if (bold) MONO else MONO_N; setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
    }
    private fun pill(t: String, color: Int) = text(t, color).apply {
        setPadding(dpi(11f), dpi(7f), dpi(14f), dpi(10f))
        background = box(PANEL, LINE, 6f, 3f)
        isFocusable = true
    }

    // ================= Bottom status bar (thumb-reachable above the dock; no top-of-screen UI) =================
    private val topbar = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val menuBtn = pill("☰ MENU", PINK)
    private val statusChip = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val sbViews = LinkedHashMap<String, TextView>()
    private val sbSeps = ArrayList<TextView>()
    private val postureView = text("FREE CAM", MUTED, 11f)
    private val timerChip = pill("⏾ --:--", GOLD).apply { visibility = View.GONE }
    private val focusChip = pill("--:--", T.good).apply { visibility = View.GONE }

    // ================= Sheet =================
    private val scrim = View(act).apply { setBackgroundColor(T.scrim); visibility = View.GONE }
    private val sheet = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE; isClickable = true }
    private val backBtn = pill("‹ HOME", TEXT)
    private val closeBtn = pill("✕ CLOSE", TEXT)
    private val diamond = View(act)
    private val titleView = text("PS1 BEDROOM ", PINK, 14f).apply { letterSpacing = 0.14f }
    private val versionSmall = text("", MUTED, 11f, false)
    private val subRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
    private val subScroll = HorizontalScrollView(act).apply { isHorizontalScrollBarEnabled = false }
    private val topDivider = View(act)
    private val divider = View(act)
    private val body = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
    private val bodyScroll = ScrollView(act)
    private var safeInsets = android.graphics.Rect()

    /** A category: its icon, its name and its sub-tabs (key to label). */
    private class Page(val icon: String, val title: String, val subs: List<Pair<String, String>>)
    private val PAGES = linkedMapOf(
        "world" to Page("🌙", "World", listOf("time" to "Time & season", "weather" to "Weather", "sky" to "Sky & view")),
        "sound" to Page("🔊", "Sound", listOf("main" to "Audio", "music" to "My Music", "mixer" to "Mixer")),
        "room" to Page("🛋", "Room", listOf("lights" to "Lights", "things" to "Appliances", "walls" to "Walls & posters", "console" to "PlayStation", "jasper" to "Jasper", "decor" to "Holidays")),
        "look" to Page("🎨", "Look", listOf("style" to "Style", "ps1" to "PS1 effects")),
        "timer" to Page("⏾", "Timers", listOf("sleep" to "Sleep", "focus" to "Focus", "alarm" to "Alarm")),
        "settings" to Page("⚙", "Settings", listOf("theme" to "Theme", "perf" to "Performance", "screen" to "Screen", "more" to "More")),
        "about" to Page("ℹ", "About", listOf("about" to "About", "ach" to "Achievements"))
    )
    private var page = "home"
    private val subOf = HashMap<String, String>()
    private val sub get() = subOf[page] ?: PAGES[page]?.subs?.firstOrNull()?.first ?: ""
    var isOpen = false; private set
    private var cityDraft = ""

    init {
        // ---- Bottom status bar: quick-step status chip + active timer chips right above the bottom dock ----
        menuBtn.setOnClickListener { if (isOpen) close() else open(null); app.hud?.refreshDockStates() }
        menuBtn.visibility = View.GONE // Menu button is now right in the main bottom dock
        topbar.addView(menuBtn)
        statusChip.setPadding(dpi(10f), dpi(5f), dpi(12f), dpi(8f))
        fun sep() = text("·", SEP, 11f).apply { setPadding(dpi(5f), 0, dpi(5f), 0); sbSeps += this }
        for ((i, k) in listOf("time", "temp", "season", "weather", "place").withIndex()) {
            if (i > 0) statusChip.addView(sep())
            val v = text("--", if (k == "time") CYAN else MUTED, 11f).apply {
                setPadding(dpi(4f), dpi(2f), dpi(4f), dpi(2f))
                isFocusable = true
            }
            // Tap: next option · hold: the World menu
            var held = false
            val holdRun = Runnable { held = true; open("world") }
            v.setOnTouchListener { view, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { held = false; view.postDelayed(holdRun, 550); view.background = box(Color.argb(64, Color.red(PINK), Color.green(PINK), Color.blue(PINK)), 0, 6f) }
                    MotionEvent.ACTION_UP -> { view.removeCallbacks(holdRun); view.postDelayed({ view.background = null }, 180); if (!held) post { s.stepStatus(k) } }
                    MotionEvent.ACTION_CANCEL -> { view.removeCallbacks(holdRun); view.background = null }
                }
                true
            }
            v.setOnClickListener { post { s.stepStatus(k) } }
            sbViews[k] = v
            statusChip.addView(v)
        }
        statusChip.addView(sep().also { it.tag = "psep" })
        statusChip.addView(postureView)
        val chipScroll = HorizontalScrollView(act).apply { isHorizontalScrollBarEnabled = false; addView(statusChip) }
        topbar.addView(chipScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        timerChip.setOnClickListener { open("timer") }
        focusChip.setOnClickListener { go("timer", "focus"); if (!isOpen) open("timer") }
        topbar.addView(timerChip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dpi(6f) })
        topbar.addView(focusChip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dpi(6f) })
        root.addView(topbar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            setMargins(dpi(8f), 0, dpi(8f), dpi(74f))
        })

        // ---- Menu sheet: compact title at top, scrollable body in middle, thumb-friendly sub-tabs + Back/Close bar at the bottom ----
        scrim.setOnClickListener { close(); app.hud?.refreshDockStates() }
        root.addView(scrim, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val titleBar = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        diamond.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(0xef, 0x44, 0x44), Color.rgb(0xfa, 0xcc, 0x15), Color.rgb(0x3b, 0x82, 0xf6), Color.rgb(0x22, 0xc5, 0x5e))).apply { setStroke(dpi(2f), Color.BLACK) }
        diamond.rotation = 45f
        titleBar.addView(diamond, LinearLayout.LayoutParams(dpi(14f), dpi(14f)).apply { marginEnd = dpi(8f); marginStart = dpi(2f) })
        titleBar.addView(titleView)
        titleBar.addView(versionSmall, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dpi(6f) })
        sheet.addView(titleBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dpi(4f) })
        sheet.addView(topDivider, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpi(2f)))

        body.setPadding(dpi(2f), dpi(8f), dpi(2f), dpi(12f))
        bodyScroll.addView(body)
        sheet.addView(bodyScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        // Bottom thumb navigation bar inside the sheet: divider, sub-tabs, and ‹ HOME / ✕ CLOSE
        sheet.addView(divider, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpi(2f)).apply { bottomMargin = dpi(6f) })
        subScroll.addView(subRow)
        sheet.addView(subScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dpi(6f) })
        val bottomNav = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        backBtn.setOnClickListener { go("home") }
        backBtn.contentDescription = "Back to Menu Home"
        bottomNav.addView(backBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dpi(8f) })
        bottomNav.addView(View(act), LinearLayout.LayoutParams(0, 1, 1f))
        closeBtn.setOnClickListener { close(); app.hud?.refreshDockStates() }
        bottomNav.addView(closeBtn)
        sheet.addView(bottomNav, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(sheet)
        restyle()
        root.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or_, ob -> if (r - l != or_ - ol || b - t != ob - ot) layoutSheet() }
        T.listeners += { restyle() }
    }

    /** The theme changed: repaint everything that stays on screen, then the open page. */
    private fun restyle() {
        menuBtn.setTextColor(PINK); menuBtn.background = box(PANEL, LINE, 6f, 3f)
        statusChip.background = box(PANEL, LINE, 8f, 3f)
        for ((k, v) in sbViews) v.setTextColor(if (k == "time") CYAN else MUTED)
        for (v in sbSeps) v.setTextColor(SEP)
        postureView.setTextColor(MUTED)
        timerChip.setTextColor(GOLD); timerChip.background = box(PANEL, LINE, 6f, 3f)
        focusChip.setTextColor(T.good); focusChip.background = box(PANEL, LINE, 6f, 3f)
        scrim.setBackgroundColor(T.scrim)
        for (b in listOf(backBtn, closeBtn)) { b.setTextColor(TEXT); b.background = box(PANEL2, LINE, 8f, 2f) }
        titleView.setTextColor(PINK); versionSmall.setTextColor(MUTED)
        topDivider.setBackgroundColor(LINE)
        divider.setBackgroundColor(LINE)
        layoutSheet()
        refreshHeader()
        build()
    }

    fun applySafeInsets(insets: android.graphics.Rect) {
        safeInsets.set(insets)
        layoutSheet()
    }

    /** Bottom-anchored sheet in both portrait and landscape so controls are never out of reach at the top. */
    private fun layoutSheet() {
        val w = root.width.takeIf { it > 0 } ?: act.resources.displayMetrics.widthPixels
        val h = root.height.takeIf { it > 0 } ?: act.resources.displayMetrics.heightPixels
        val d = act.resources.displayMetrics.density
        val short = h / d <= 460f
        val portrait = w.toFloat() / h <= 0.85f
        val lp = if (portrait) {
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, minOf(dpi(540f), (h * 0.68f).toInt()), Gravity.BOTTOM)
        } else {
            FrameLayout.LayoutParams(minOf(dpi(440f), (w * 0.92f).toInt()), minOf(dpi(460f), (h * 0.88f).toInt()), Gravity.BOTTOM or Gravity.START).apply {
                leftMargin = safeInsets.left
            }
        }
        sheet.layoutParams = lp
        sheet.setPadding(
            dpi(12f) + if (portrait) safeInsets.left else 0,
            dpi(10f),
            dpi(12f) + if (portrait) safeInsets.right else 0,
            dpi(10f) + safeInsets.bottom
        )
        sheet.background = GradientDrawable().apply {
            setColor(PANEL)
            cornerRadii = floatArrayOf(dp(16f), dp(16f), dp(16f), dp(16f), 0f, 0f, 0f, 0f)
            setStroke(dpi(2f), LINE)
        }
        sheetPortrait = portrait

        val tlp = topbar.layoutParams as FrameLayout.LayoutParams
        tlp.bottomMargin = dpi(if (short) 52f else 74f) + safeInsets.bottom
        tlp.leftMargin = dpi(8f) + safeInsets.left
        tlp.rightMargin = dpi(8f) + safeInsets.right
        topbar.layoutParams = tlp
    }
    private var sheetPortrait = true

    /** Opens the menu: on its home page, or straight on a category ("world", "timer"... the old tab names work too). */
    fun open(pageName: String?) {
        page = when (pageName) { null -> "home"; "display" -> "settings"; else -> if (pageName in PAGES) pageName else "home" }
        isOpen = true
        scrim.visibility = View.VISIBLE
        sheet.visibility = View.VISIBLE
        val from = sheet.height.takeIf { it > 0 }?.toFloat() ?: dp(520f)
        sheet.translationX = 0f; sheet.translationY = from
        sheet.animate().translationX(0f).translationY(0f).setDuration(220).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        go(page)
        app.hud?.refreshDockStates()
    }
    fun close() {
        if (!isOpen || !app.isBuilt) return
        isOpen = false
        scrim.visibility = View.GONE
        val anim = sheet.animate().setDuration(200).translationY(sheet.height.toFloat().coerceAtLeast(dp(400f)))
        anim.withEndAction { if (!isOpen) sheet.visibility = View.GONE }.start()
        app.hud?.refreshDockStates()
    }
    /** Android back: a category goes back to the home page, the home page closes the menu. */
    fun back() { if (page != "home") go("home") else close() }

    /** Controller L1 / R1 switches sub-tabs on the open category page. */
    fun cycleSubTab(dir: Int) {
        val p = PAGES[page] ?: return
        if (p.subs.size < 2) return
        val curIdx = p.subs.indexOfFirst { it.first == sub }.coerceAtLeast(0)
        val nextIdx = (curIdx + dir + p.subs.size) % p.subs.size
        go(page, p.subs[nextIdx].first)
    }

    private fun go(p: String, subKey: String? = null) {
        page = p
        if (subKey != null) subOf[p] = subKey
        refreshHeader()
        build()
        bodyScroll.scrollTo(0, 0)
    }
    private fun refreshHeader() {
        val home = page == "home"
        backBtn.visibility = if (home) View.GONE else View.VISIBLE
        diamond.visibility = if (home) View.VISIBLE else View.GONE
        versionSmall.visibility = if (home) View.VISIBLE else View.INVISIBLE
        titleView.text = if (home) "PS1 BEDROOM " else PAGES[page]?.let { "${it.icon} ${it.title.uppercase()}" } ?: ""
        subRow.removeAllViews()
        val p = PAGES[page]
        subScroll.visibility = if (p == null || p.subs.size < 2) View.GONE else View.VISIBLE
        if (p != null) for ((k, label) in p.subs) {
            val on = k == sub
            subRow.addView(text(label, if (on) ON_TEXT else MUTED, 12f).apply {
                setPadding(dpi(12f), dpi(8f), dpi(12f), dpi(8f))
                background = if (on) box(ACTIVE_BG, PINK, 999f) else box(PANEL2, LINE, 999f, strokeW = 1f)
                isFocusable = true
                setOnClickListener { go(page, k) }
            }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dpi(6f) })
        }
    }

    // ================= Building blocks =================
    private fun h4(label: String, right: String? = null) {
        val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
        row.addView(text(label.uppercase(), PINK, 11f).apply { letterSpacing = 0.14f }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (right != null) row.addView(text(right, CYAN, 11f))
        body.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(if (body.childCount == 0) 4f else 16f); bottomMargin = dpi(7f) })
    }
    private fun note(t: String, small: Boolean = false) {
        body.addView(text(t, MUTED, if (small) 10f else 11f, false).apply { setLineSpacing(0f, 1.3f); alpha = if (small) 0.85f else 1f },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(6f) })
    }
    private fun seg(options: List<Pair<String, String>>, active: String, onPick: (String) -> Unit) {
        val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; background = box(0, LINE, 9f) }
        options.forEachIndexed { i, (v, label) ->
            if (i > 0) row.addView(View(act).apply { setBackgroundColor(LINE) }, LinearLayout.LayoutParams(dpi(2f), LinearLayout.LayoutParams.MATCH_PARENT))
            val on = v == active
            val b = text(label, if (on) ON_TEXT else MUTED, 11f).apply {
                gravity = Gravity.CENTER; setPadding(dpi(4f), dpi(8f), dpi(4f), dpi(8f))
                if (on) setBackgroundColor(ACTIVE_BG)
                isFocusable = true
                setOnClickListener { onPick(v) }
            }
            row.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        row.clipToOutline = true
        body.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }
    /** Flex-wrapping chips (the HTML's .chips). */
    private fun chips(items: List<Triple<String, Boolean, () -> Unit>>, groupLabels: Map<Int, String> = emptyMap()) {
        val flow = FlowLayout(act, dpi(6f))
        items.forEachIndexed { i, (label, on, fn) ->
            groupLabels[i]?.let { g -> flow.addView(text(g, MUTED, 10f, false).apply { tag = FlowLayout.FULL_ROW; setPadding(0, dpi(4f), 0, 0) }) }
            flow.addView(text(label, if (on) ON_TEXT else TEXT, 11f).apply {
                setPadding(dpi(10f), dpi(6f), dpi(10f), dpi(6f))
                background = box(if (on) ACTIVE_BG else PANEL2, if (on) PINK else LINE, 999f)
                isFocusable = true
                setOnClickListener { fn() }
            })
        }
        body.addView(flow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(2f) })
    }
    /** Named colours for Jasper's custom coat. */
    private val DOG_COLOURS = listOf("#1d1b1a" to "Jet black", "#3a2a1c" to "Dark chocolate", "#5a3a22" to "Chocolate", "#8a6a45" to "Sable",
        "#b97a3e" to "Tan", "#a3532a" to "Rust red", "#d9b27a" to "Cream", "#a8a6a0" to "Silver", "#d6d4cf" to "Light grey",
        "#ece7dc" to "Off-white", "#f8f5ee" to "Snow white", "#6b7280" to "Blue grey")
    /** Colour swatches (the HTML's <input type=color>, as a palette): the colour itself with its name, the picked one ringed. */
    private fun swatchRow(colors: List<Pair<String, String>>, current: String, onPick: (String) -> Unit) {
        val flow = FlowLayout(act, dpi(6f))
        for ((hex, name) in colors) {
            val on = hex.equals(current, true)
            flow.addView(LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dpi(2f), dpi(4f), dpi(2f), dpi(4f))
                background = if (on) GradientDrawable().apply { setColor(ACTIVE_BG); cornerRadius = dp(10f); setStroke(dpi(2f), PINK) } else null
                addView(View(act).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor(hex)); setStroke(dpi(1f), LINE_HI) } },
                    LinearLayout.LayoutParams(dpi(32f), dpi(32f)))
                addView(text(name, if (on) ON_TEXT else MUTED, 9f, on).apply { gravity = Gravity.CENTER; maxLines = 2 }, LinearLayout.LayoutParams(dpi(60f), -2).apply { topMargin = dpi(3f) })
                contentDescription = name
                isFocusable = true
                setOnClickListener { onPick(hex) }
            }, ViewGroup.LayoutParams(dpi(66f), ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        body.addView(flow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(4f) })
    }
    private val sheetW get() = sheet.width.takeIf { it > 0 } ?: minOf(root.width.takeIf { it > 0 } ?: act.resources.displayMetrics.widthPixels, dpi(420f))
    /** Grid of tiles (the HTML's .tiles: auto-fill, min 112 px). */
    private fun tiles(items: List<Triple<String, Boolean, () -> Unit>>) {
        val cols = maxOf(2, (sheetW - dpi(28f)) / dpi(118f))
        var row: LinearLayout? = null
        items.forEachIndexed { i, (label, on, fn) ->
            if (i % cols == 0) { row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }; body.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(6f) }) }
            row!!.addView(text(label, if (on) ON_TEXT else TEXT, 11f).apply {
                gravity = Gravity.CENTER; minHeight = dpi(44f); setPadding(dpi(6f), dpi(10f), dpi(8f), dpi(12f))
                background = box(if (on) ACTIVE_BG else PANEL2, if (on) PINK else LINE, 10f, 2f)
                isFocusable = true
                setOnClickListener { fn() }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dpi(6f) })
        }
        val last = row
        if (last != null) while (last.childCount < cols) last.addView(View(act), LinearLayout.LayoutParams(0, 1, 1f).apply { marginEnd = dpi(6f) })
    }
    private fun bigToggle(label: String, on: Boolean, fn: () -> Unit) {
        body.addView(text(label, if (on) (if (T.isLight) T.good else Color.rgb(0xb9, 0xff, 0xcf)) else TEXT, 14f).apply {
            gravity = Gravity.CENTER; letterSpacing = 0.07f; setPadding(dpi(14f), dpi(14f), dpi(17f), dpi(17f))
            background = box(if (on) T.goodBg else PANEL2, if (on) T.good else LINE, 12f, 3f)
            isFocusable = true
            setOnClickListener { fn() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }
    private fun slider(label: String, min: Int, max: Int, step: Int, value: Int, fmt: (Int) -> String, onChange: (Int) -> Unit) {
        val head = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        val valView = text(fmt(value), MUTED, 11f, false)
        head.addView(text(label, MUTED, 11f, false), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(valView)
        body.addView(head, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(9f) })
        val bar = SeekBar(act).apply {
            this.max = (max - min) / step; progress = (value - min) / step
            minimumHeight = dpi(36f)   // a bigger touch target for the thumb
            progressTintList = ColorStateList.valueOf(PINK); thumbTintList = progressTintList
            progressBackgroundTintList = ColorStateList.valueOf(LINE)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(b: SeekBar, p: Int, user: Boolean) { if (!user) return; val v = min + p * step; valView.text = fmt(v); onChange(v) }
                override fun onStartTrackingTouch(b: SeekBar) { dragging = true; b.parent?.requestDisallowInterceptTouchEvent(true) }
                override fun onStopTrackingTouch(b: SeekBar) { dragging = false; if (buildPending) b.post { build() } }
            })
        }
        body.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }
    private fun check(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
        val cb = CheckBox(act).apply {
            text = label; isChecked = on; setTextColor(TEXT); typeface = MONO_N; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            buttonTintList = ColorStateList.valueOf(PINK)
            isFocusable = true
            setOnCheckedChangeListener { _, c -> onChange(c) }
        }
        body.addView(cb, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(6f) })
    }
    /** The HTML's <details>: a collapsible block. */
    private var openDetails = HashSet<String>()
    private fun details(summary: String, content: () -> Unit) {
        val open = summary in openDetails
        val head = text((if (open) "▾ " else "▸ ") + summary, CYAN, 12f).apply {
            setPadding(dpi(10f), dpi(8f), dpi(10f), dpi(8f))
            isFocusable = true
            setOnClickListener { if (!openDetails.remove(summary)) openDetails += summary; build() }
        }
        val frame = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; background = box(0, LINE, 10f); setPadding(0, 0, dpi(10f), dpi(if (open) 8f else 0f)) }
        frame.addView(head)
        body.addView(frame, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(12f) })
        if (open) {
            val saved = body.childCount
            content()
            // move what content() added into the frame
            val added = (saved until body.childCount).map { body.getChildAt(it) }
            for (v in added) { body.removeView(v); (v.layoutParams as? ViewGroup.MarginLayoutParams)?.marginStart = dpi(10f); frame.addView(v) }
        }
    }
    /** A row of square quick toggles: a big icon over a short label. */
    private class Quick(val icon: String, val label: String, val on: Boolean, val fn: () -> Unit)
    private fun quickRow(items: List<Quick>) {
        val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        items.forEachIndexed { i, q ->
            row.addView(LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dpi(2f), dpi(9f), dpi(4f), dpi(11f))
                background = box(if (q.on) ACTIVE_BG else PANEL2, if (q.on) PINK else LINE, 12f, 2f)
                addView(text(q.icon, TEXT, 20f, false).apply { gravity = Gravity.CENTER })
                addView(text(q.label, if (q.on) ON_TEXT else MUTED, 9f).apply { gravity = Gravity.CENTER; letterSpacing = 0.06f; maxLines = 1 }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(3f) })
                contentDescription = q.label
                isFocusable = true
                setOnClickListener { it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY); q.fn() }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { if (i < items.size - 1) marginEnd = dpi(6f) })
        }
        body.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(4f) })
    }

    // ================= Pages =================
    /** A slider is being dragged: rebuilding the sheet would delete it mid-drag, so wait until it's let go. */
    private var dragging = false
    private var buildPending = false
    /** refreshMenu: rebuild the open page from the current state. */
    fun build() {
        if (!isOpen || !app.isBuilt) return
        if (dragging) { buildPending = true; return }
        buildPending = false
        body.removeAllViews()
        versionSmall.text = "v${Changelog.entries.first().version}"
        val sk = sub
        when (page) {
            "home" -> buildHome()
            "world" -> when (sk) { "weather" -> buildWeather(); "sky" -> buildSky(); else -> buildTime() }
            "sound" -> when (sk) { "music" -> buildMyMusic(); "mixer" -> buildMixer(); else -> buildSound() }
            "room" -> when (sk) { "things" -> buildAppliances(); "walls" -> buildWalls(); "console" -> buildConsole(); "jasper" -> buildJasper(); "decor" -> buildDecor(); else -> buildLights() }
            "look" -> when (sk) { "ps1" -> buildPs1Fx(); else -> buildStyle() }
            "timer" -> when (sk) { "focus" -> buildFocus(); "alarm" -> buildAlarm(); else -> buildSleep() }
            "settings" -> when (sk) { "perf" -> buildPerf(); "screen" -> buildScreen(); "more" -> buildMore(); else -> buildTheme() }
            "about" -> when (sk) { "ach" -> buildAchievements(); else -> buildAbout() }
        }
    }

    // ---- Home: quick toggles + a card per category ----
    private fun buildHome() {
        val snd = app.sound
        quickRow(listOf(
            Quick(if (snd.playing) "🔊" else "🔈", "SOUND", snd.playing) { post { app.toggleAudio() } },
            Quick("💡", "LIGHT", app.fanLightOn) { post { app.toggleFanLight(); s.changed() } },
            Quick("😴", "SLEEP", s.sleepEnd > 0) { close(); post { s.startSleepMode() } },
            Quick("📷", "PHOTO", false) { close(); app.hud?.overlays?.enterPhotoMode() },
            Quick(if (T.isLight) "☀️" else "🌙", if (T.isLight) "LIGHT UI" else "DARK UI", false) { T.set(act, mode = if (T.isLight) "dark" else "light") }
        ))
        if (app.ytActive) {
            tiles(listOf(
                Triple("⏹ STOP YOUTUBE ON TV", true, { app.hud?.yt?.stop(); build() }),
                Triple("▶ CHANGE YOUTUBE VIDEO", false, { close(); app.hud?.yt?.openDialog() })
            ))
        }
        tiles(listOf(
            Triple("📺 TV REMOTE", false, { close(); app.hud?.openTvRemote() }),
            Triple("⚡ DEV DEBUG TOOL", false, { close(); app.hud?.openDebugTool() })
        ))
        val cols = if (sheetW / act.resources.displayMetrics.density >= 520f) 3 else 2
        var row: LinearLayout? = null
        PAGES.entries.forEachIndexed { i, (key, p) ->
            if (i % cols == 0) { row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }; body.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(8f) }) }
            row!!.addView(LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dpi(12f), dpi(10f), dpi(14f), dpi(13f))
                background = box(PANEL2, LINE, 12f, 3f)
                isFocusable = true
                val top = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                top.addView(text(p.icon, TEXT, 19f, false))
                top.addView(text(p.title.uppercase(), PINK, 12f).apply { letterSpacing = 0.1f }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dpi(8f) })
                top.addView(text("›", MUTED, 15f))
                addView(top)
                addView(text(cardLine(key), MUTED, 10f, false).apply { maxLines = 2; setLineSpacing(0f, 1.2f); minLines = 2 }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(5f) })
                setOnClickListener { go(key) }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { if ((i % cols) < cols - 1) marginEnd = dpi(8f) })
        }
        val last = row
        if (last != null) while (last.childCount < cols) last.addView(View(act), LinearLayout.LayoutParams(0, 1, 1f))
        note("Tap the time, weather or place on the bottom status bar to cycle it (or hold to jump to World). Almost everything in the room can be tapped directly too.", true)
    }
    /** What each card says under its name: the state that matters most right now. */
    private fun cardLine(key: String): String = try {
        when (key) {
            "world" -> "${env.formatHour(env.hour)} · ${env.W.label}\n${env.currentSeason.replaceFirstChar { it.uppercase() }}"
            "sound" -> if (!app.sound.playing) "Audio off\nRadio, music, mixer" else when (val st = app.extras.radioStation) {
                0 -> "Audio on\nRadio off"
                app.myMusic.MY_MUSIC_STATION -> "Audio on\n${app.myMusic.current?.title ?: "My Music"}"
                else -> "Audio on\n📻 ${app.extras.RADIO_STATIONS[st].substringBefore(':')}"
            }
            "room" -> "${Consoles.NAMES[app.consoles.current] ?: "PS1"} · Jasper\nLights, posters, paint"
            "look" -> "${Settings.VIBES[s.vibeMode]?.label ?: "Default"}\nPS1 effects"
            "timer" -> if (s.sleepEnd > 0) "Sleep: ${s.sleepLeftText()} left\nFocus, alarm" else if (app.alarm.on) "Alarm ${app.alarm.time}\nSleep, focus" else "Sleep, focus\nSunrise alarm"
            "settings" -> "${T.MODES.firstOrNull { it.first == T.mode }?.second?.substringAfter(' ') ?: "Phone"} · ${T.accentInfo.name}\nQuality, screen"
            "about" -> "v${Changelog.entries.first().version}\n🏆 ${app.achievements.count} / ${Achievements.LIST.size}"
            else -> ""
        }
    } catch (e: Exception) { "" }

    // ---- World ----
    private fun buildTime() {
        h4("Time of day", "${env.dayOfWeekName}, ${env.formatHour(env.hour)}")
        seg(listOf("live" to "Live clock", "preset" to "Pick a time", "cycle" to "Time-lapse"), env.timeMode) { m -> post { s.setTimeMode(m) } }
        if (env.timeMode == "preset") {
            chips(env.TIME_PRESETS.map { (name, fn) -> Triple(name, kotlin.math.abs(((fn() % 24) + 24) % 24 - env.presetHour) < 0.02, { post { s.setTimePreset(name) } }) })
            slider("Exact time", 0, 2395, 5, (env.presetHour * 100).roundToInt(), { env.formatHour(it / 100.0) }) { v -> post { s.setPresetHour(v / 100.0) } }
            h4("Day of the week", env.dayOfWeekName)
            val dows = listOf(2 to "Mon", 3 to "Tue", 4 to "Wed", 5 to "Thu", 6 to "Fri", 7 to "Sat", 1 to "Sun")
            chips(dows.map { (d, label) -> Triple(label, env.dayOfWeek == d, { post { s.setPresetDayOfWeek(d) } }) })
            note("Weekdays feature morning & evening commuter rush hours and earlier lights-out; weekends have late-night cruising and party lights that stay on late.", true)
        }
        if (env.timeMode == "cycle") chips(listOf(5.0 to "1 day = 5 min", 20.0 to "1 day = 20 min", 60.0 to "1 day = 1 hour").map { (m, l) -> Triple(l, env.cycleMinutes == m, { post { s.setCycleMinutes(m) } }) })
        note(s.sunInfo())
        h4("Season")
        chips(listOf("live" to "Auto (today)", "spring" to "Spring", "summer" to "Summer", "fall" to "Fall", "winter" to "Winter").map { (v, l) ->
            Triple(l + (if (v == "live" && env.seasonMode == "live") " · ${env.currentSeason}" else ""), if (v == "live") env.seasonMode == "live" else env.seasonMode != "live" && env.currentSeason == v, { post { s.setSeasonChoice(v) } })
        })
        h4("Nighttime window lights")
        seg(listOf("gradual" to "Turn off late", "early" to "Turn off early", "never" to "Stay on all night"), env.lightsOut) { m -> post { s.setLightsOut(m) } }
        note("Windows in houses, trailers, apartments, and distant buildings gradually go dark one by one as the night gets late (staying lit significantly later on weekends).", true)
    }

    private fun buildDebrisControls() {
        val amtPct = (s.debrisAmount * 100).roundToInt()
        h4("Falling leaves & outdoor debris", if (s.debrisMode == "off" || amtPct == 0) "Off" else "${s.debrisMode.replaceFirstChar { it.uppercase() }} · $amtPct%")
        seg(listOf("weather" to "Weather-tied", "always" to "Always fall", "off" to "Off"), s.debrisMode) { m -> post { s.setDebrisMode(m) } }
        if (s.debrisMode != "off") {
            val presets = listOf(
                Triple("Gentle", 0.5f to 0.7f, 0.7f),
                Triple("Normal", 1.0f to 1.0f, 1.0f),
                Triple("Breezy", 1.5f to 1.4f, 1.4f),
                Triple("Heavy", 2.2f to 1.8f, 1.9f)
            )
            chips(presets.map { (name, af, freq) ->
                val (amt, spd) = af
                val on = kotlin.math.abs(s.debrisAmount - amt) < 0.08f && kotlin.math.abs(s.debrisSpeed - spd) < 0.15f
                Triple(name, on, {
                    post {
                        s.setDebrisAmount(amt)
                        s.setDebrisSpeed(spd)
                        s.setDebrisFreq(freq)
                    }
                })
            })
            slider("How much (amount)", 0, 250, 10, (s.debrisAmount * 100).roundToInt(), { if (it == 0) "Off" else "$it%" }) { v -> post { s.setDebrisAmount(v / 100f) } }
            slider("How fast (fall & wind speed)", 20, 250, 10, (s.debrisSpeed * 100).roundToInt(), { "$it%" }) { v -> post { s.setDebrisSpeed(v / 100f) } }
            slider("How often (gusts & spawn rate)", 20, 250, 10, (s.debrisFreq * 100).roundToInt(), { "$it%" }) { v -> post { s.setDebrisFreq(v / 100f) } }
            slider("Leaves hitting window", 0, 100, 5, (s.windowHitRate * 100).roundToInt(), { if (it == 0) "Never (0%)" else "$it%" }) { v -> post { s.setWindowHitRate(v / 100f) } }
        }
        note("Weather-tied scales leaves, blossoms, and debris with the wind, rain, storm intensity, and whether the place outside has trees. All rain, window drops, and leaves blow in the same wind direction.", true)
    }

    private fun buildWeather() {
        h4("Weather", env.W.label + (if (s.weatherMode == "live") s.liveTempC?.let { " · ${s.formatTemp(it)}" } ?: "" else ""))
        seg(listOf("manual" to "Pick", "auto" to "Auto-change", "live" to "Live (your city)"), s.weatherMode) { m -> post { s.setWeatherMode(m) } }
        when (s.weatherMode) {
            "manual" -> {
                val groups = listOf("Clear skies" to listOf("clear", "partly", "overcast", "windy"), "Rain" to listOf("drizzle", "rain", "downpour", "sunshower"),
                    "Storms" to listOf("storm", "heat"), "Snow" to listOf("flurries", "snow", "blizzard"), "Fog" to listOf("mist", "fog"))
                val items = ArrayList<Triple<String, Boolean, () -> Unit>>(); val labels = HashMap<Int, String>()
                for ((g, kinds) in groups) { labels[items.size] = g; for (k in kinds) items += Triple(Environment.WEATHER_KINDS.getValue(k).label, env.weatherKind == k, { post { s.pickWeather(k) } }) }
                chips(items, labels)
            }
            "live" -> {
                val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val input = EditText(act).apply {
                    hint = "Your city (or leave blank for auto)"; setHintTextColor(MUTED); setTextColor(TEXT); typeface = MONO_N; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setText(cityDraft.ifEmpty { s.cityName }); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS; imeOptions = EditorInfo.IME_ACTION_GO; isSingleLine = true
                    background = box(T.field, LINE, 8f); setPadding(dpi(8f), dpi(8f), dpi(8f), dpi(8f))
                    addTextChangedListener(object : android.text.TextWatcher {
                        override fun afterTextChanged(e: android.text.Editable?) { cityDraft = e?.toString() ?: "" }
                        override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                        override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                    })
                }
                fun go() { val city = input.text.toString(); hideKeyboard(input); post { s.setWeatherMode("live"); s.fetchLiveWeather(city, false) } }
                input.setOnEditorActionListener { _, _, _ -> go(); true }
                row.addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(pill("Use", TEXT).apply { setOnClickListener { go() } }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dpi(6f) })
                body.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(8f) })
                note(s.liveStatus)
                val refOpts = listOf(2 to "2 min", 5 to "5 min", 10 to "10 min", 15 to "15 min", 30 to "30 min", 60 to "1 hour")
                h4("Auto-refresh weather every", refOpts.firstOrNull { it.first == s.liveWeatherRefreshMinutes }?.second ?: "${s.liveWeatherRefreshMinutes} min")
                chips(refOpts.map { (mins, label) -> Triple(label, s.liveWeatherRefreshMinutes == mins, { post { s.setLiveWeatherRefreshMinutes(mins) } }) })
                tiles(listOf(Triple("🔄 REFRESH LIVE WEATHER NOW", false, { post { s.fetchLiveWeather(s.cityName, false); app.sfx("ui"); app.toast("🔄 Refreshing live weather from Open-Meteo...") } })))
                note("Live weather comes from Open-Meteo (with automatic city detection if left blank) and updates the Downtown LED building ticker & billboard in real time.", true)
            }
            "auto" -> note("The weather drifts naturally on its own every 10-20 minutes, fitting the season.")
        }
        val lfOpts = listOf("off" to "Off", "rare" to "Rare", "normal" to "Normal", "frequent" to "Frequent", "constant" to "Constant")
        h4("Lightning frequency", lfOpts.firstOrNull { it.first == s.lightningFreq }?.second ?: "Normal")
        chips(lfOpts.map { (k, label) -> Triple(label, s.lightningFreq == k, { post { s.setLightningFreq(k) } }) })
        note("Controls how often lightning flashes and thunder rolls during thunderstorms and heatwaves.", true)
        buildDebrisControls()
    }
    private fun buildSky() {
        h4("Outside the window")
        chips(Places.OUTSIDE_ENVS.map { (k, e) -> Triple("${e.icon} ${e.name}", app.sky.outsideEnv == k, { post { s.setOutside(k) } }) })
        h4("Parked cars outside")
        seg(listOf("random" to "Random & moving", "classic" to "Fixed classic", "none" to "No parked cars"), app.places.carsMode) { m -> post { s.setCarsMode(m) } }
        if (app.places.carsMode == "random") {
            slider("How many cars (density)", 25, 100, 5, (app.places.carsDensity * 100).roundToInt(), { "$it%" }) { v -> post { s.setCarsDensity(v / 100f) } }
            val stayOpts = listOf(2 to "2 min", 5 to "5 min", 15 to "15 min", 20 to "20 min", 45 to "45 min", 120 to "2 hours", 0 to "Stay put")
            val curStay = app.places.carsStayMinutes.roundToInt()
            h4("Cars leave / arrive after", stayOpts.firstOrNull { it.first == curStay }?.second ?: "$curStay min")
            chips(stayOpts.map { (m, label) -> Triple(label, curStay == m, { post { s.setCarsStayMinutes(m) } }) })
            tiles(listOf(Triple("🚗 RESHUFFLE PARKED CARS NOW", false, { post { app.places.reshuffleCars(); app.sfx("ui"); app.toast("🚗 Reshuffled parked cars outside!") } })))
        }
        note("Parked cars in Suburbs, City Block, and Trailer Park randomize whenever you leave and come back, and drive off or pull in over time.", true)
        h4("Nighttime window lights")
        seg(listOf("gradual" to "Turn off late", "early" to "Turn off early", "never" to "Stay on all night"), env.lightsOut) { m -> post { s.setLightsOut(m) } }
        h4("Sky")
        chips(Settings.SKY_STYLES.map { (k, l) -> Triple(l, env.skyStyle == k, { post { s.setSkyStyle(k) } }) })
        h4("Moon", s.moonReadout())
        seg(listOf("cinematic" to "Cinematic path", "real" to "Real astronomy"), if (env.realisticMoon) "real" else "cinematic") { v -> post { s.setRealisticMoon(v == "real") } }
        note("Cinematic keeps the moon gliding low across your window all night. Real astronomy puts it exactly where it is in the sky (some nights it won't be up). Either way the phase matches tonight's real moon.")
        buildDebrisControls()
    }

    // ---- Sound ----
    private fun buildSound() {
        val snd = app.sound
        bigToggle(if (snd.playing) "AUDIO: ON" else "AUDIO: OFF", snd.playing) { post { app.toggleAudio() } }
        slider("Master volume", 0, 100, 1, (snd.masterVolume * 100).roundToInt(), { "$it%" }) { v -> post { snd.setMasterVolume(v / 100.0); s.changed() } }
        h4("Play on")
        val tv = app.tv
        val playItems = ArrayList<Triple<String, Boolean, () -> Unit>>()
        playItems += Triple(if (tv.bedroomTvOn) "TV: ${Tv.TV_CHANNEL_NAMES[tv.bedroomTvChannel].substringBefore(':')}" else "TV CHANNEL", tv.bedroomTvOn, { post { app.tvNextChannel(); app.sfx("tvChannel"); s.changed() } })
        playItems += Triple(if (app.extras.radioStation > 0) "RADIO: ${app.extras.RADIO_STATIONS[app.extras.radioStation].substringBefore(':')}" else "RADIO: OFF", app.extras.radioStation > 0, { post { app.extras.cycleRadio(app.furniture.radioDisplayMat); app.sfx("radioTune"); s.changed() } })
        playItems += Triple(if (app.ytActive) "▶ YOUTUBE: PLAYING" else "▶ YOUTUBE ON TV", app.ytActive, { close(); app.hud?.yt?.openDialog() })
        if (app.ytActive) {
            playItems += Triple("⏹ STOP YOUTUBE", true, { app.hud?.yt?.stop(); build() })
        }
        playItems += Triple("PC SCREEN", app.monitor.pcPoweredOn, { post { val on = app.monitor.pcPoweredOn; app.monitorNextChannel(); app.sfx(if (on) "monitorChannel" else "pcOn"); s.changed() } })
        playItems += Triple("📻 BOOMBOX", false, { close(); app.hud?.openBoombox() })
        tiles(playItems)
        note("The room's own sounds (fan, rain, the PC, the living room TV) each have a volume in Mixer.", true)
    }
    /** v0.54: one-tap mixes (keys: pc, fan, ac, livtv, bedtv, pcspk, weather, nature, sfx, radio). */
    private val MIX_KEYS = listOf("pc", "fan", "ac", "livtv", "bedtv", "pcspk", "weather", "nature", "sfx", "radio")
    private val MIX_PRESETS = linkedMapOf(
        "🏠 Default" to doubleArrayOf(0.75, 0.70, 0.65, 0.35, 1.00, 0.75, 0.90, 0.80, 0.80, 0.90),
        "📚 Study" to doubleArrayOf(0.50, 0.60, 0.40, 0.00, 0.30, 0.40, 0.70, 0.40, 0.30, 0.60),
        "🌧 Rainy night" to doubleArrayOf(0.30, 0.40, 0.00, 0.15, 0.40, 0.30, 1.40, 0.30, 0.50, 0.50),
        "😴 Sleep" to doubleArrayOf(0.20, 1.00, 0.80, 0.00, 0.20, 0.00, 0.90, 0.30, 0.10, 0.30),
        "🏙 Lively" to doubleArrayOf(0.80, 0.60, 0.60, 0.70, 1.00, 0.90, 0.90, 1.30, 1.00, 1.00),
        "🎧 Music first" to doubleArrayOf(0.25, 0.25, 0.20, 0.00, 0.25, 0.30, 0.30, 0.20, 0.40, 1.20)
    )
    private val mixPrefs get() = act.getSharedPreferences("ps1bedroom", android.content.Context.MODE_PRIVATE)
    private fun myMixes(): org.json.JSONObject = try { org.json.JSONObject(mixPrefs.getString("mixPresets", "{}") ?: "{}") } catch (e: Exception) { org.json.JSONObject() }
    private fun mixMatches(v: DoubleArray) = MIX_KEYS.withIndex().all { (i, k) -> kotlin.math.abs((app.sound.vol[k] ?: 1.0) - v[i]) < 0.01 }
    private fun applyMix(name: String, v: DoubleArray) = post {
        for ((i, k) in MIX_KEYS.withIndex()) app.sound.vol[k] = v[i]
        app.sound.updateLayered(); s.changed(); app.sfx("ui"); app.toast("🎚 $name")
    }
    private fun buildMixer() {
        val snd = app.sound
        h4("Presets")
        val mine = myMixes()
        val mineArr = mine.keys().asSequence().toList().associateWith { n -> mine.getJSONArray(n).let { a -> DoubleArray(MIX_KEYS.size) { a.optDouble(it, 1.0) } } }
        chips((MIX_PRESETS + mineArr).map { (name, v) -> Triple(name, mixMatches(v), { applyMix(name, v) }) })
        chips(listOf(Triple("＋ Save this mix", false, {
            val n = (1..99).first { !mine.has("⭐ My mix $it") }
            if (mine.length() >= 6) app.toast("🎚 6 saved mixes is the limit: remove one first")
            else {
                mine.put("⭐ My mix $n", org.json.JSONArray(MIX_KEYS.map { snd.vol[it] ?: 1.0 }))
                mixPrefs.edit().putString("mixPresets", mine.toString()).apply(); app.toast("🎚 Saved as My mix $n"); build()
            }
        })) + mineArr.keys.map { n -> Triple("✕ ${n.removePrefix("⭐ ")}", false, { mine.remove(n); mixPrefs.edit().putString("mixPresets", mine.toString()).apply(); build() }) })
        h4("Mixer")
        for ((k, label) in listOf("pc" to "PC tower fan & HDD", "fan" to "Ceiling fan", "ac" to "Wall AC", "livtv" to "Living room TV (muffled)", "bedtv" to "Bedroom TV & PS1",
                "weather" to "Rain, wind & storms", "nature" to "Outdoors: city, birds, crickets", "pcspk" to "PC speakers", "sfx" to "Clicks, PS1 drive, lava lamp", "radio" to "Boombox radio"))
            slider(label, 0, 150, 5, ((snd.vol[k] ?: 1.0) * 100).roundToInt(), { "$it%" }) { v -> post { snd.vol[k] = v / 100.0; snd.updateLayered(); s.changed() } }
    }
    /** HTML 13600-13788: the boombox player. */
    private fun buildMyMusic() {
        val mm = app.myMusic
        h4("📻 My Music", if (mm.tracks.isEmpty()) null else "${mm.tracks.size} songs")
        val cur = mm.current
        val onMusic = app.extras.radioStation == mm.MY_MUSIC_STATION
        note(if (cur != null) "${if (onMusic && !mm.paused) "▶" else "⏸"} ${cur.title}${if (cur.artist.isNotEmpty()) " · ${cur.artist}" else ""}${if (cur.album.isNotEmpty()) "  (${cur.album})" else ""}" else mm.statusText())
        if (mm.tracks.isNotEmpty()) {
            tiles(listOf(
                Triple("⏮", false, { post { mm.mediaControl("prev") } }),
                Triple(if (onMusic && !mm.paused) "⏸ PAUSE" else "▶ PLAY", onMusic && !mm.paused, { post { mm.mediaControl("toggle") } }),
                Triple("⏭", false, { post { mm.mediaControl("next") } })
            ))
            tiles(listOf(
                Triple(if (mm.shuffle) "🔀 SHUFFLE: ON" else "🔀 SHUFFLE: OFF", mm.shuffle, { post { mm.toggleShuffle() } }),
                Triple(mapOf("all" to "🔁 REPEAT ALL", "one" to "🔂 REPEAT ONE", "off" to "➡️ REPEAT OFF").getValue(mm.repeat), mm.repeat != "off", { post { mm.cycleRepeat() } })
            ))
            details("Albums") {
                for (a in mm.albums()) {
                    chips(listOf(Triple("💿 ${a.title}${if (a.artist.isNotEmpty()) " · ${a.artist}" else ""} (${a.tracks.size})", a.tracks.contains(cur), { post { mm.playList(a.tracks) } })) +
                        a.tracks.mapIndexed { i, t -> Triple("${if (t.track > 0) "${t.track}. " else ""}${t.title}", t == cur, { post { mm.playList(a.tracks, i) } }) })
                }
            }
        }
        tiles(listOf(
            Triple("＋ ADD SONGS", false, { app.pickMusic?.invoke(false) }),
            Triple("＋ ALBUM FOLDER", false, { app.pickMusic?.invoke(true) })
        ))
        if (mm.tracks.isNotEmpty()) details("Manage") {
            chips(mm.albums().map { a -> Triple("✕ ${a.title}", false, { post { mm.removeAlbum(a); app.toast("🎵 Removed ${a.title}") } }) })
            chips(listOf(Triple("🗑 Clear My Music", false, { post { mm.clear() } })))
        }
        note("Your songs stay on your device: the app only remembers where they are (plus a small copy of each cover). Tap the boombox in the room to switch between the FM stations and MY MUSIC. The \"Now playing\" poster shows the album cover.", true)
    }

    // ---- Room ----
    private fun buildLights() {
        val blindLabels = arrayOf("SLATTED", "TILTED OPEN", "ROLLED UP", "CLOSED")
        val strNames = arrayOf("OFF", "WARM", "RAINBOW")
        h4("Lights")
        tiles(listOf(
            Triple("FAN LIGHT: ${if (app.fanLightOn) "ON" else "OFF"}", app.fanLightOn, { post { app.toggleFanLight(); s.changed() } }),
            Triple("LAMP: ${if (app.furniture.lampOn) "ON" else "OFF"}", app.furniture.lampOn, { post { app.furniture.toggleLamp(); s.changed() } }),
            Triple("STRING LIGHTS: ${strNames[app.decor.stringLightsMode]}", app.decor.stringLightsMode > 0, { post { app.decor.cycleStringLights(); s.changed() } }),
            Triple(if (app.desk.lavaOn) "LAVA LAMP: ${app.desk.LAVA_NAMES[app.desk.lavaIdx].uppercase()}" else "LAVA LAMP: OFF", app.desk.lavaOn, { post { app.toast(app.desk.cycleLava()); app.sfx("lavaSwitch"); s.changed() } })
        ))
        h4("Window")
        tiles(listOf(
            Triple("BLINDS: ${blindLabels.getOrElse(app.room.blindsOpenState) { "SLATTED" }}", app.room.blindsOpenState != 3, { post { app.room.cycleWindowBlinds(); s.changed() } }),
            Triple("WINDOW: ${if (app.room.windowOpen) "OPEN (LOUD)" else "CLOSED (MUFFLED)"}", app.room.windowOpen, { post { app.room.toggleWindow(); app.sound.updateLayered(); s.changed() } })
        ))
        note("Slide the window sash open to flood the bedroom with crisp, loud outdoor sounds (rain, wind, crickets, traffic), or close it to muffle the sound behind glass. The blinds filter visual light.", true)
    }
    private fun buildAppliances() {
        h4("Appliances")
        val appItems = ArrayList<Triple<String, Boolean, () -> Unit>>()
        appItems += Triple("CEILING FAN: ${if (app.fan.fanSpinning) "ON" else "OFF"}", app.fan.fanSpinning, { post { app.fan.toggleCeilingFanSpin(); s.changed() } })
        appItems += Triple("AC UNIT: ${if (app.room.acUnitOn) "ON" else "OFF"}", app.room.acUnitOn, { post { app.room.toggleAcUnit(); s.changed() } })
        appItems += Triple("SPACE HEATER: ${app.extras.heaterMode.uppercase()}", app.extras.heaterMode != "off", { post { app.extras.cycleHeater(); s.changed() } })
        appItems += Triple("BEDROOM TV: ${if (app.tv.bedroomTvOn) "ON" else "OFF"}", app.tv.bedroomTvOn, { post { app.tvPower(); s.changed() } })
        if (app.ytActive) {
            appItems += Triple("⏹ STOP YOUTUBE", true, { app.hud?.yt?.stop(); build() })
        }
        appItems += Triple("LIVING RM TV: ${if (app.sound.livingRoomTvOn) "ON" else "OFF"}", app.sound.livingRoomTvOn, { post { app.sound.livingRoomTvOn = !app.sound.livingRoomTvOn; app.sfx("ui"); app.sound.updateLayered(); s.changed() } })
        appItems += Triple("🖥 USE THE PC", false, { close(); post { app.openPc() } })
        appItems += Triple("📺 TV REMOTE", false, { close(); app.hud?.openTvRemote() })
        appItems += Triple("TV WEATHER: ${s.weatherChannelView.uppercase()}", s.weatherChannelView == "simple", { post { app.toggleWeatherView() } })
        appItems += Triple("⚡ DEV DEBUG TOOL", false, { close(); app.hud?.openDebugTool() })
        tiles(appItems)
        h4("Tidy up")
        tiles(listOf(
            Triple("📚 GAMES BACK IN ORDER", false, { post { app.library.sortGameShelves(); s.changed() } }),
            Triple("DOOR: CLOSED", false, { post { app.toast("🚧 UNDER CONSTRUCTION: The rest of the house is still being built. Check back in a future update!") } })
        ))
    }
    private fun buildWalls() {
        for ((part, title) in listOf("walls" to "Wall paint", "carpet" to "Carpet", "sheets" to "Bedsheets")) {
            h4(title)
            chips(app.extras.colorOptions(part).map { (k, l) -> Triple(l, app.extras.roomColors[part] == k, { post { app.extras.setRoomColor(part, k); s.changed() } }) })
        }
        h4("Poster above the desk")
        chips(app.extras.POSTER_NAMES.map { (k, l) -> Triple(l, app.extras.posterChoice == k, { post { app.extras.choosePoster(k); s.changed() } }) })
        h4("Poster above the boombox")
        chips(app.furniture.BAND_POSTERS.filter { (k, _) -> k != "photo" || app.furniture.photoFor("band") != null }.map { (k, l) -> Triple(l, app.furniture.bandPoster == k, { post { app.furniture.setBandPoster(k); app.sfx("caseClick"); s.changed() } }) })
        h4("Your own photos")
        tiles(listOf(Triple("📷 BOOMBOX POSTER", false, { app.pickPosterPhoto?.invoke("band") }), Triple("📷 DESK POSTER", false, { app.pickPosterPhoto?.invoke("main") })))
        note("Pick any picture from your phone: it's cropped to fit the poster. Only a small copy is kept in the app.", true)
        h4("Wall calendar")
        chips(listOf("2001" to "2001 (Retro Nostalgia)", "2026" to "2026 (Live Real-World Date)").map { (yr, l) ->
            Triple(l, s.calendarYear == yr, { post { s.setCalendarYear(yr); app.sfx("caseClick") } })
        })
        note("Switch between October 2001 nostalgia (Wednesday 24th) and current live 2026 calendar with exact days of the week. You can also tap the wall calendar in the room directly!", true)
    }
    private fun buildConsole() {
        h4("PlayStation under the TV")
        chips(Consoles.NAMES.map { (k, l) -> Triple(l, app.consoles.current == k, { post { if (app.ps1.ps1On) app.ps1.powerToggle(app.lastFrameT); app.consoles.select(k); app.sfx("ui"); s.changed(); app.hud?.overlays?.setConsole(k) } }) })
        note("Each one has its own model, controller, lights, boot-up and sound, and the start screen follows it.", true)
    }
    private fun buildJasper() {
        val dog = app.jasper
        h4("Jasper · ${dog.moodLabel()}")
        tiles(listOf(
            Triple("🍖 FEED", false, { post { dog.feed(app.lastFrameT); s.changed() } }),
            Triple("💧 WATER", false, { post { dog.giveWater(app.lastFrameT); s.changed() } }),
            Triple("🎾 PLAY", false, { post { dog.playWith(app.lastFrameT); s.changed() } }),
            Triple("🛏️ SLEEP", dog.pose == "sleep", { post { dog.sendToSleep(app.lastFrameT); s.changed() } })
        ))
        check("Optional pet-care stats (hunger, thirst, energy & playfulness tick over time)", dog.careEnabled) { c ->
            post { dog.careEnabled = c; s.changed() }
        }
        if (dog.careEnabled) {
            note("🍖 Full: ${dog.hunger.roundToInt()}%   ·   💧 Hydrated: ${dog.thirst.roundToInt()}%   ·   💤 Energy: ${dog.energy.roundToInt()}%   ·   🎾 Happy: ${dog.funLevel.roundToInt()}%\nTip: You can also tap his red & blue bowls by the foot of the bed, or use the Jasper home-screen widget!", true)
        } else {
            note("Tap Feed, Water, Play or Sleep anytime (or tap his bowls in the room) to interact with Jasper.", true)
        }
        h4("Jasper's coat")
        chips(Jasper.PRESETS.map { (k, p) -> Triple(p.name, dog.colorPreset == k, { post { dog.applyColors(p, k); app.sfx("ui"); s.changed() } }) })
        // Jasper's three colours right now, by name
        val names = DOG_COLOURS.toMap()
        fun nameOf(hex: String) = names.entries.firstOrNull { it.key.equals(hex, true) }?.value ?: "Custom"
        val preview = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        for ((hex, label) in listOf(dog.colors.coat to "Coat", dog.colors.dark to "Saddle", dog.colors.light to "Chest")) {
            preview.addView(LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                addView(View(act).apply { background = GradientDrawable().apply { setColor(Color.parseColor(hex)); cornerRadius = dp(8f); setStroke(1, LINE) } }, LinearLayout.LayoutParams(-1, dpi(26f)))
                addView(text(label, MUTED, 10f, false).apply { gravity = Gravity.CENTER })
                addView(text(nameOf(hex), TEXT, 11f).apply { gravity = Gravity.CENTER })
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dpi(6f) })
        }
        body.addView(preview, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(10f) })
        details("Custom colours") {
            for ((part, label) in listOf("coat" to "Coat (most of him)", "dark" to "Saddle & mask (his back and face)", "light" to "Chest & paws")) {
                val cur = when (part) { "coat" -> dog.colors.coat; "dark" -> dog.colors.dark; else -> dog.colors.light }
                h4(label)
                swatchRow(DOG_COLOURS, cur) { hex -> post { dog.setPart(part, hex); app.sfx("ui"); s.changed() } }
            }
        }
    }
    private fun buildDecor() {
        h4("Decorations")
        chips(listOf(
            "auto" to "Auto (by date)",
            "off" to "None",
            "halloween" to "Halloween",
            "winter" to "Winter holidays",
            "valentine" to "Valentine's",
            "stpatrick" to "St. Patrick's",
            "easter" to "Easter / Spring",
            "summer" to "Summer / 4th",
            "thanksgiving" to "Thanksgiving",
            "newyear" to "New Year's"
        ).map { (k, l) ->
            Triple(l + (if (k == "auto" && app.extras.decorMode == "auto") " · ${app.extras.activeDecor()}" else ""), app.extras.decorMode == k, { post { app.extras.decorMode = k; app.extras.applyDecor(); s.changed() } })
        })
        note("Auto puts up decorations for each holiday: Halloween in October, Thanksgiving in November, Winter tree in December, New Year's confetti, Valentine hearts in February, St. Patrick's clovers in March, Easter eggs in April, and 4th of July bunting in summer. The room's hanging string lights dynamically tint to match every holiday!")
    }

    // ---- Look ----
    private fun buildStyle() {
        h4("Look")
        chips(Settings.VIBES.map { (k, v) -> Triple(v.label, s.vibeMode == k, { post { s.setVibe(k) } }) })
        slider("Light rays", 0, 200, 10, (env.userVolumetricSetting * 100).roundToInt(), { "$it%" }) { v -> post { env.userVolumetricSetting = v / 100f; env.applyEnvironmentSettings(); s.changed() } }
        slider("Camera breathing", 0, 100, 5, (app.swayAmount * 100).roundToInt(), { "$it%" }) { v -> post { app.swayAmount = v / 100f; s.changed() } }
        app.hud?.yt?.let { yt -> check("PS1 look on YouTube: a pixel grid the size of the room's pixels, chunkier scanlines, and the picture wobbles with the TV", yt.ps1Look) { c -> yt.ps1Look = c } }
    }
    private fun buildPs1Fx() {
        h4("PS1 effects")
        slider("Resolution", 160, 2160, 40, app.resShort, {
            when {
                it >= 2160 -> "4K UHD (2160p)"
                it >= 1440 -> "2K QHD (${it}p)"
                it >= 1080 -> "Full HD (${it}p)"
                it >= 720 -> "HD (${it}p)"
                it == 240 -> "240p (Authentic PS1)"
                else -> "${it}p"
            }
        }) { v -> post { app.setResolution(v); s.changed() } }
        chips(listOf(
            180 to "180p",
            240 to "240p (PS1)",
            360 to "360p",
            480 to "480p",
            720 to "720p HD",
            1080 to "1080p FHD",
            1440 to "1440p 2K",
            2160 to "4K UHD (2160p)"
        ).map { (r, label) -> Triple(label, app.resShort == r, { post { app.setResolution(r); s.changed(); build() } }) })
        slider("Vertex wobble", 0, 300, 5, app.jitterBase.roundToInt(), { if (it == 0) "Off (Crisp)" else "$it" }) { v -> post { app.setJitter(v.toFloat()); s.changed() } }
        slider("Texture warp", 0, 100, 5, (app.renderer.ps1Uniforms.affineStrength * 100).roundToInt(), { "$it%" }) { v -> post { app.renderer.ps1Uniforms.affineStrength = v / 100f; s.changed() } }
        slider("Dither & 15-bit colour", 0, 100, 10, (app.renderer.post.ditherStrength * 100).roundToInt(), { "$it%" }) { v -> post { app.renderer.post.ditherStrength = v / 100f; s.changed() } }
        slider("Ambient occlusion", 0, 150, 10, (app.aoBase * 100).roundToInt(), { "$it%" }) { v -> post { app.aoBase = v / 100f; app.renderer.post.aoStrength = app.aoBase; s.changed() } }
        note("Picking a Look sets these for you; change them here to fine-tune it.", true)
    }

    // ---- Timers ----
    private fun buildSleep() {
        h4("Sleep mode", if (s.sleepEnd > 0) "${s.sleepLeftText()} left" else null)
        bigToggle("😴 SLEEP MODE", false) { close(); post { s.startSleepMode() } }
        note("One tap: lights down, fan on, a soft glow, you in bed, and everything fades out in 45 minutes.")
        h4("Sleep timer", if (s.sleepEnd > 0) "${s.sleepLeftText()} left" else "Off")
        chips(listOf(0, 15, 30, 45, 60, 90).map { m -> Triple(if (m > 0) "$m min" else "Off", m == 0 && s.sleepEnd == 0L, { post { s.setSleepTimer(m) } }) })
        check("Fade the screen to black when it ends", s.sleepDim) { c -> post { s.sleepDim = c; s.changed() } }
    }
    private fun buildFocus() {
        h4("Focus timer")
        chips(listOf("off" to "Off", "short" to "25 min + 5 break", "long" to "50 min + 10 break").map { (k, l) -> Triple(l, s.focus.mode == k, { post { s.setFocus(k) } }) })
        note("Work in focused blocks with short breaks. A soft bell rings when it's time to switch.")
    }
    private fun buildAlarm() {
        val al = app.alarm
        h4("⏰ Sunrise alarm", if (al.on) al.time else "Off")
        check("On", al.on) { c -> post { al.set(on = c); app.sfx("ui"); app.toast(if (c) "⏰ Alarm set for ${al.time}. The room starts to glow 20 minutes before." else "⏰ Alarm off"); s.changed() } }
        val hours = (4..11).flatMap { h -> listOf("%02d:00".format(h), "%02d:30".format(h)) }
        chips(hours.map { t -> Triple(t, al.time == t, { post { al.set(time = t, on = true); s.changed() } }) })
        check("Wake to my music (otherwise birdsong)", al.music) { c -> post { al.set(music = c); s.changed() } }
        note("The room slowly fills with warm light for 20 minutes before your alarm, then your music or birdsong plays. Keep the app open (sleep mode works well).", true)
    }

    // ---- Settings ----
    private fun buildTheme() {
        h4("Light or dark", if (T.isLight) "Light now" else "Dark now")
        seg(T.MODES, T.mode) { m -> T.set(act, mode = m) }
        note(when (T.mode) {
            "auto" -> "Follows your phone's dark mode."
            "room" -> "Light while the sun is up in the room, dark after sunset: it follows the room's clock (and your time-lapse)."
            "dark" -> "Always dark."
            else -> "Always light."
        }, true)
        h4("Colour theme", T.accentInfo.name)
        val flow = FlowLayout(act, dpi(8f))
        val cardW = (sheetW - dpi(40f)) / maxOf(2, (sheetW - dpi(40f)) / dpi(118f))
        for (t in T.ACCENTS) {
            val on = t.key == T.accentKey
            val pv = T.preview(t)
            flow.addView(LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dpi(8f), dpi(8f), dpi(8f), dpi(9f))
                background = box(pv[0], if (on) pv[2] else pv[1], 10f, 2f, if (on) 3f else 2f)
                isFocusable = true
                val stripes = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
                for ((i, c) in listOf(pv[2], pv[3], pv[1]).withIndex())
                    stripes.addView(View(act).apply { background = GradientDrawable().apply { setColor(c); cornerRadius = dp(4f) } }, LinearLayout.LayoutParams(0, dpi(14f), 1f).apply { if (i < 2) marginEnd = dpi(4f) })
                addView(stripes, LinearLayout.LayoutParams(-1, -2))
                addView(text((if (on) "✓ " else "") + t.name, pv[2], 11f).apply { maxLines = 1 }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(6f) })
                contentDescription = t.name
                setOnClickListener { T.set(act, accent = t.key) }
            }, ViewGroup.LayoutParams(cardW - dpi(8f), ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        body.addView(flow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(4f) })
        note("Themes colour the menu, the status bar, the dock, the boombox and the pop-ups. The room itself never changes.", true)
    }
    private fun buildPerf() {
        h4("Quality", "${app.renderer.internalW}×${app.renderer.internalH}")
        chips(listOf(
            "saver" to "Battery saver (180p)",
            "balanced" to "Balanced (240p)",
            "high" to "High (480p)",
            "ultra" to "Ultra (1080p)",
            "cinema4k" to "4K UHD (2160p)"
        ).map { (k, l) -> Triple(l, s.qualityMode == k, { post { s.setQuality(k); build() } }) })
        note(Settings.QUALITY.getValue(s.qualityMode).note)
        h4("Frame rate")
        seg(listOf("auto" to "Auto", "30" to "30", "60" to "60", "120" to "120 Hz"), s.fpsChoice) { v -> post { s.setFps(v) } }
        check("Eco idle: gently step down to 30 fps after 3 min untouched (24 fps after 10 min)", s.ecoIdle) { c -> post { s.ecoIdle = c; s.applyFps(); s.changed() } }
    }
    private fun buildScreen() {
        app.cutout?.let { co ->
            h4("Screen cutout (notch / hole-punch)")
            note(co.info, true)
            seg(Cutout.MODES.map { (k, l) -> k to l }, co.mode) { v -> co.apply(v); build() }
            note("Full screen runs the room right under the camera while keeping all UI buttons clear of the notch. Clear when sideways adds a clean margin only in landscape. Always clear keeps the margin in all orientations.", true)
        }
        h4("When you leave")
        check("Picture-in-picture: keep the room in a small window when you leave the app", app.pipEnabled) { c -> app.pipEnabled = c; act.getSharedPreferences("ps1bedroom", android.content.Context.MODE_PRIVATE).edit().putBoolean("pip", c).apply() }
        check("Show the start screen when the app opens", s.startScreenEnabled) { c -> post { s.startScreenEnabled = c; s.changed() } }
    }
    private fun buildMore() {
        h4("Temperature")
        seg(listOf("F" to "°F  Fahrenheit", "C" to "°C  Celsius"), s.tempUnit) { v -> post { s.tempUnit = v; s.changed(); app.hud?.refreshStatus() } }
        h4("Help")
        tiles(listOf(Triple("❓ HOW TO", false, { close(); app.hud?.overlays?.startTutorial() }), Triple("📜 CHANGELOG", false, { Changelog.show(act) })))
        h4("Debug")
        check("The free camera can leave the room", s.debugFreeCam) { c -> post { s.debugFreeCam = c } }
    }

    // ---- About ----
    private fun buildAbout() {
        h4("PS1 Bedroom", "v${Changelog.entries.first().version}")
        note("A little late-night room to keep you company. Real time, real sky, real weather, a PlayStation full of games, and a soundscape for every corner of the room.")
        note("Made by Daymion Gallo, aka Galloade, aka JenZiMiBruh.")
        tiles(listOf(Triple("📜 CHANGELOG", false, { Changelog.show(act) }), Triple("❓ HOW TO", false, { close(); app.hud?.overlays?.startTutorial() })))
    }
    private fun buildAchievements() {
        val ach = app.achievements
        h4("🏆 Achievements", "${ach.count} / ${Achievements.LIST.size}")
        note("Tap any achievement card for a reminder toast, or read how to unlock it right below.", true)
        for ((id, a) in Achievements.LIST) {
            val got = ach.has(id)
            val card = LinearLayout(act).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dpi(12f), dpi(10f), dpi(12f), dpi(11f))
                background = box(if (got) ACTIVE_BG else PANEL2, if (got) GOLD else LINE, 10f, 2f)
                isFocusable = true
                isClickable = true
                addView(text(if (got) a[0] else "🔒", TEXT, 20f, false).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dpi(36f), -2).apply { marginEnd = dpi(10f) })
                val col = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
                val topRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                topRow.addView(text(a[1], if (got) GOLD else TEXT, 12f), LinearLayout.LayoutParams(0, -2, 1f))
                topRow.addView(text(if (got) "✓ UNLOCKED" else "HOW TO GET", if (got) T.good else CYAN, 9f))
                col.addView(topRow, LinearLayout.LayoutParams(-1, -2))
                col.addView(text(a[2], if (got) ON_TEXT else MUTED, 11f, false).apply { setLineSpacing(0f, 1.2f) }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(3f) })
                addView(col, LinearLayout.LayoutParams(0, -2, 1f))
                setOnClickListener {
                    it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    post { app.sfx("ui") }
                    app.toast("${if (got) "✅ ${a[0]}" else "🔒 HOW TO GET:"} ${a[1]} — ${a[2]}")
                }
            }
            body.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(7f) })
        }
    }

    private fun hideKeyboard(v: View) {
        (act.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0)
    }

    // ================= Status bar =================
    /** Setting a TextView's text re-measures and re-lays it out even when it is the same: skip that. */
    private fun TextView.setIfChanged(s: CharSequence) { if (text.toString() != s.toString()) text = s }
    fun refreshStatus() {
        if (!app.isBuilt) return
        // "Phone" and "Room clock" themes follow the phone's dark mode / the room's sun (checked once a second)
        T.phoneChanged(act)
        T.roomDaylight(env.daylight > 0.35)
        val st = s.status()
        sbViews["time"]?.setIfChanged(st.time)
        sbViews["temp"]?.setIfChanged(st.temp)
        sbViews["season"]?.setIfChanged(st.season)
        sbViews["weather"]?.setIfChanged(st.weather)
        sbViews["place"]?.setIfChanged(st.place)
        postureView.setIfChanged(st.posture)
        // Narrow phones hide the posture label (the HTML's max-width: 520px rule)
        val narrow = root.width / act.resources.displayMetrics.density <= 520f
        postureView.visibility = if (narrow) View.GONE else View.VISIBLE
        statusChip.findViewWithTag<View>("psep")?.visibility = postureView.visibility
        timerChip.visibility = if (st.timer != null) View.VISIBLE else View.GONE
        st.timer?.let { timerChip.setIfChanged(it) }
        focusChip.visibility = if (st.focus != null) View.VISIBLE else View.GONE
        st.focus?.let { focusChip.setIfChanged(it) }
    }
    /** Photo mode, sleeping: hide the bars. */
    fun setBarsVisible(v: Boolean) { topbar.visibility = if (v) View.VISIBLE else View.GONE }
}

/** A minimal wrapping row layout for chips (Android has no flex-wrap without extra libraries). */
class FlowLayout(ctx: android.content.Context, private val gap: Int) : ViewGroup(ctx) {
    companion object { const val FULL_ROW = "fullRow" }
    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val maxW = MeasureSpec.getSize(wSpec)
        var x = 0; var y = 0; var rowH = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            val full = c.tag == FULL_ROW
            val lpW = c.layoutParams?.width ?: ViewGroup.LayoutParams.WRAP_CONTENT
            c.measure(if (full) MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.EXACTLY) else if (lpW > 0) MeasureSpec.makeMeasureSpec(minOf(lpW, maxW), MeasureSpec.EXACTLY) else MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            if (full || (x > 0 && x + c.measuredWidth > maxW)) { if (x > 0) { y += rowH + gap; x = 0; rowH = 0 } }
            x += c.measuredWidth + gap; rowH = maxOf(rowH, c.measuredHeight)
            if (full) { y += rowH + gap; x = 0; rowH = 0 }
        }
        setMeasuredDimension(maxW, y + rowH)
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxW = r - l
        var x = 0; var y = 0; var rowH = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            val full = c.tag == FULL_ROW
            if (full || (x > 0 && x + c.measuredWidth > maxW)) { if (x > 0) { y += rowH + gap; x = 0; rowH = 0 } }
            c.layout(x, y, x + c.measuredWidth, y + c.measuredHeight)
            x += c.measuredWidth + gap; rowH = maxOf(rowH, c.measuredHeight)
            if (full) { y += rowH + gap; x = 0; rowH = 0 }
        }
    }
}
