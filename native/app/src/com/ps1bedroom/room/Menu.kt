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
 * The HTML's top bar (MENU + the status chip whose parts step through options) and the menu sheet with its
 * World / Sound / Room / Display / Timers / About tabs, as native views with the same look. UI thread only:
 * every change is posted to the GL thread through the app.
 */
@SuppressLint("SetTextI18n", "ClickableViewAccessibility")
class Menu(private val act: Activity, private val root: FrameLayout, private val app: App) {
    private val PANEL = Color.argb((0.94 * 255).toInt(), 9, 11, 28)
    private val PANEL2 = Color.argb((0.9 * 255).toInt(), 20, 26, 60)
    private val LINE = Color.rgb(0x3a, 0x4b, 0x8c)
    private val LINE_HI = Color.rgb(0x6b, 0x7f, 0xd6)
    private val TEXT = Color.rgb(0xd6, 0xdd, 0xff)
    private val MUTED = Color.rgb(0x8f, 0x9c, 0xd0)
    private val PINK = Color.rgb(0xff, 0x77, 0xee)
    private val CYAN = Color.rgb(0x7f, 0xf3, 0xff)
    private val GOLD = Color.rgb(0xff, 0xd2, 0x3f)
    private val ACTIVE_BG = Color.rgb(0x2a, 0x18, 0x44)
    private val SEP = Color.rgb(0x4a, 0x57, 0x99)
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
        val sh = GradientDrawable().apply { setColor(Color.BLACK); cornerRadius = dp(radius) }
        return LayerDrawable(arrayOf(sh, bg)).apply { setLayerInset(0, dpi(shadow), dpi(shadow), 0, 0); setLayerInset(1, 0, 0, dpi(shadow), dpi(shadow)) }
    }
    private fun text(t: String, color: Int = TEXT, size: Float = 12f, bold: Boolean = true) = TextView(act).apply {
        text = t; setTextColor(color); typeface = if (bold) MONO else MONO_N; setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
    }
    private fun pill(t: String, color: Int) = text(t, color).apply { setPadding(dpi(11f), dpi(7f), dpi(14f), dpi(10f)); background = box(PANEL, LINE, 6f, 3f) }

    // ================= Top bar =================
    private val topbar = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val statusChip = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val sbViews = LinkedHashMap<String, TextView>()
    private val postureView = text("FREE CAM", MUTED, 11f)
    private val timerChip = pill("⏾ --:--", GOLD).apply { visibility = View.GONE }
    private val focusChip = pill("--:--", Color.rgb(0x4a, 0xde, 0x80)).apply { visibility = View.GONE }

    // ================= Sheet =================
    private val scrim = View(act).apply { setBackgroundColor(Color.argb(89, 0, 0, 0)); visibility = View.GONE }
    private val sheet = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE; isClickable = true }
    private val tabsRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
    private val body = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
    private val bodyScroll = ScrollView(act)
    private val versionSmall = text("", MUTED, 11f, false)
    private val TABS = linkedMapOf("world" to "World", "sound" to "Sound", "room" to "Room", "display" to "Display", "timer" to "Timers", "about" to "About")
    private var tab = "world"
    private val tabButtons = HashMap<String, TextView>()
    var isOpen = false; private set
    private var cityDraft = ""

    init {
        // ---- Top bar: MENU, status chip, sleep / focus chips ----
        val menuBtn = pill("☰ MENU", PINK)
        menuBtn.setOnClickListener { open(null) }
        topbar.addView(menuBtn)
        statusChip.background = box(PANEL, LINE, 6f, 3f)
        statusChip.setPadding(dpi(9f), dpi(6f), dpi(12f), dpi(9f))
        fun sep() = text("·", SEP, 11f).apply { setPadding(dpi(5f), 0, dpi(5f), 0) }
        for ((i, k) in listOf("time", "temp", "season", "weather", "place").withIndex()) {
            if (i > 0) statusChip.addView(sep())
            val v = text("--", if (k == "time") CYAN else MUTED, 11f)
            v.setPadding(dpi(3f), dpi(2f), dpi(3f), dpi(2f))
            // Tap: next option · hold: the World menu
            var held = false
            val holdRun = Runnable { held = true; open("world") }
            v.setOnTouchListener { view, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { held = false; view.postDelayed(holdRun, 550); view.background = box(Color.argb(64, 255, 62, 165), 0, 6f) }
                    MotionEvent.ACTION_UP -> { view.removeCallbacks(holdRun); view.postDelayed({ view.background = null }, 180); if (!held) post { s.stepStatus(k) } }
                    MotionEvent.ACTION_CANCEL -> { view.removeCallbacks(holdRun); view.background = null }
                }
                true
            }
            sbViews[k] = v
            statusChip.addView(v)
        }
        statusChip.addView(sep().also { it.tag = "psep" })
        statusChip.addView(postureView)
        val chipScroll = HorizontalScrollView(act).apply { isHorizontalScrollBarEnabled = false; addView(statusChip) }
        topbar.addView(chipScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dpi(8f); weight = 0f })
        topbar.addView(timerChip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dpi(8f) })
        topbar.addView(focusChip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dpi(8f) })
        root.addView(topbar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP).apply { setMargins(dpi(10f), dpi(10f), dpi(10f), 0) })

        // ---- Menu sheet ----
        scrim.setOnClickListener { close() }
        root.addView(scrim, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val head = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val diamond = View(act).apply { background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(0xef, 0x44, 0x44), Color.rgb(0xfa, 0xcc, 0x15), Color.rgb(0x3b, 0x82, 0xf6), Color.rgb(0x22, 0xc5, 0x5e))).apply { setStroke(dpi(2f), Color.BLACK) }; rotation = 45f }
        head.addView(diamond, LinearLayout.LayoutParams(dpi(16f), dpi(16f)).apply { marginEnd = dpi(10f); marginStart = dpi(2f) })
        head.addView(text("PS1 BEDROOM ", PINK, 14f).apply { letterSpacing = 0.14f })
        head.addView(versionSmall, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(pill("✕", TEXT).apply { setOnClickListener { close() } })
        sheet.addView(head, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dpi(8f) })
        for ((k, label) in TABS) {
            val b = text(label, MUTED, 12f).apply { setPadding(dpi(9f), dpi(6f), dpi(9f), dpi(6f)); setOnClickListener { selectTab(k) } }
            tabButtons[k] = b
            tabsRow.addView(b, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dpi(4f) })
        }
        sheet.addView(HorizontalScrollView(act).apply { isHorizontalScrollBarEnabled = false; addView(tabsRow) })
        sheet.addView(View(act).apply { setBackgroundColor(LINE) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpi(2f)).apply { topMargin = dpi(6f) })
        body.setPadding(dpi(2f), dpi(8f), dpi(2f), dpi(16f))
        bodyScroll.addView(body)
        sheet.addView(bodyScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(sheet)
        layoutSheet()
        root.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or_, ob -> if (r - l != or_ - ol || b - t != ob - ot) layoutSheet() }
    }

    /** Landscape: a panel down the left side · portrait (max-aspect-ratio 4/5): a bottom sheet, 76% high. */
    private fun layoutSheet() {
        val w = root.width.takeIf { it > 0 } ?: act.resources.displayMetrics.widthPixels
        val h = root.height.takeIf { it > 0 } ?: act.resources.displayMetrics.heightPixels
        val portrait = w.toFloat() / h <= 0.8f
        val lp = if (portrait) FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, (h * 0.76f).toInt(), Gravity.BOTTOM)
                 else FrameLayout.LayoutParams(minOf(dpi(400f), (w * 0.92f).toInt()), FrameLayout.LayoutParams.MATCH_PARENT, Gravity.START)
        sheet.layoutParams = lp
        sheet.setPadding(dpi(12f), dpi(10f), dpi(12f), dpi(10f))
        sheet.background = if (portrait) GradientDrawable().apply { setColor(PANEL); cornerRadii = floatArrayOf(dp(16f), dp(16f), dp(16f), dp(16f), 0f, 0f, 0f, 0f); setStroke(dpi(2f), LINE) }
                           else GradientDrawable().apply { setColor(PANEL); setStroke(dpi(2f), LINE) }
        sheetPortrait = portrait
    }
    private var sheetPortrait = true

    fun open(tabName: String?) {
        if (tabName != null) tab = tabName
        isOpen = true
        scrim.visibility = View.VISIBLE
        sheet.visibility = View.VISIBLE
        val from = if (sheetPortrait) sheet.height.takeIf { it > 0 }?.toFloat() ?: dp(600f) else -(sheet.width.takeIf { it > 0 }?.toFloat() ?: dp(400f))
        if (sheetPortrait) { sheet.translationX = 0f; sheet.translationY = from } else { sheet.translationY = 0f; sheet.translationX = from }
        sheet.animate().translationX(0f).translationY(0f).setDuration(220).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        selectTab(tab)
    }
    fun close() {
        if (!isOpen || !app.isBuilt) return
        isOpen = false
        scrim.visibility = View.GONE
        val anim = sheet.animate().setDuration(200)
        if (sheetPortrait) anim.translationY(sheet.height.toFloat()) else anim.translationX(-sheet.width.toFloat())
        anim.withEndAction { if (!isOpen) sheet.visibility = View.GONE }.start()
    }
    private fun selectTab(k: String) {
        tab = k
        for ((key, b) in tabButtons) {
            val on = key == k
            b.setTextColor(if (on) Color.WHITE else MUTED)
            b.background = if (on) box(PANEL2, LINE_HI, 8f) else null
        }
        build()
        bodyScroll.scrollTo(0, 0)
    }

    // ================= Building blocks =================
    private fun h4(label: String, right: String? = null) {
        val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
        row.addView(text(label.uppercase(), PINK, 11f).apply { letterSpacing = 0.14f }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (right != null) row.addView(text(right, CYAN, 11f))
        body.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(if (body.childCount == 0) 4f else 14f); bottomMargin = dpi(7f) })
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
            val b = text(label, if (on) Color.WHITE else MUTED, 11f).apply {
                gravity = Gravity.CENTER; setPadding(dpi(4f), dpi(8f), dpi(4f), dpi(8f))
                if (on) setBackgroundColor(ACTIVE_BG)
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
            flow.addView(text(label, if (on) Color.WHITE else TEXT, 11f).apply {
                setPadding(dpi(10f), dpi(6f), dpi(10f), dpi(6f))
                background = box(if (on) ACTIVE_BG else PANEL2, if (on) PINK else LINE, 999f)
                setOnClickListener { fn() }
            })
        }
        body.addView(flow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(2f) })
    }
    /** Grid of tiles (the HTML's .tiles: auto-fill, min 112 px). */
    private fun tiles(items: List<Triple<String, Boolean, () -> Unit>>) {
        val cols = maxOf(2, ((sheet.width.takeIf { it > 0 } ?: dpi(360f)) - dpi(28f)) / dpi(118f))
        var row: LinearLayout? = null
        items.forEachIndexed { i, (label, on, fn) ->
            if (i % cols == 0) { row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }; body.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(6f) }) }
            row!!.addView(text(label, if (on) Color.WHITE else TEXT, 11f).apply {
                gravity = Gravity.CENTER; minHeight = dpi(44f); setPadding(dpi(6f), dpi(10f), dpi(8f), dpi(12f))
                background = box(if (on) ACTIVE_BG else PANEL2, if (on) PINK else LINE, 10f, 2f)
                setOnClickListener { fn() }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dpi(6f) })
        }
        val last = row
        if (last != null) while (last.childCount < cols) last.addView(View(act), LinearLayout.LayoutParams(0, 1, 1f).apply { marginEnd = dpi(6f) })
    }
    private fun bigToggle(label: String, on: Boolean, fn: () -> Unit) {
        body.addView(text(label, if (on) Color.rgb(0xb9, 0xff, 0xcf) else TEXT, 14f).apply {
            gravity = Gravity.CENTER; letterSpacing = 0.07f; setPadding(dpi(14f), dpi(14f), dpi(17f), dpi(17f))
            background = box(if (on) Color.rgb(0x0f, 0x2a, 0x1c) else PANEL2, if (on) Color.rgb(0x22, 0xc5, 0x5e) else LINE, 12f, 3f)
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
            progressTintList = ColorStateList.valueOf(Color.rgb(0xd9, 0x46, 0xef)); thumbTintList = progressTintList
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(b: SeekBar, p: Int, user: Boolean) { if (!user) return; val v = min + p * step; valView.text = fmt(v); onChange(v) }
                override fun onStartTrackingTouch(b: SeekBar) {}
                override fun onStopTrackingTouch(b: SeekBar) {}
            })
        }
        body.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }
    private fun check(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
        val cb = CheckBox(act).apply {
            text = label; isChecked = on; setTextColor(TEXT); typeface = MONO_N; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            buttonTintList = ColorStateList.valueOf(Color.rgb(0xd9, 0x46, 0xef))
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

    // ================= Tabs =================
    /** refreshMenu: rebuild the open tab from the current state. */
    fun build() {
        if (!isOpen || !app.isBuilt) return
        body.removeAllViews()
        versionSmall.text = "v${Changelog.entries.first().version}"
        when (tab) {
            "world" -> buildWorld()
            "sound" -> buildSound()
            "room" -> buildRoom()
            "display" -> buildDisplay()
            "timer" -> buildTimers()
            "about" -> buildAbout()
        }
    }
    private fun buildWorld() {
        h4("Time of day", env.formatHour(env.hour))
        seg(listOf("live" to "Live clock", "preset" to "Pick a time", "cycle" to "Time-lapse"), env.timeMode) { m -> post { s.setTimeMode(m) } }
        if (env.timeMode == "preset") {
            chips(env.TIME_PRESETS.map { (name, fn) -> Triple(name, kotlin.math.abs(((fn() % 24) + 24) % 24 - env.presetHour) < 0.02, { post { s.setTimePreset(name) } }) })
            slider("Exact time", 0, 2395, 5, (env.presetHour * 100).roundToInt(), { env.formatHour(it / 100.0) }) { v -> post { s.setPresetHour(v / 100.0) } }
        }
        if (env.timeMode == "cycle") chips(listOf(5.0 to "1 day = 5 min", 20.0 to "1 day = 20 min", 60.0 to "1 day = 1 hour").map { (m, l) -> Triple(l, env.cycleMinutes == m, { post { s.setCycleMinutes(m) } }) })
        note(s.sunInfo())
        h4("Outside the window")
        chips(Places.OUTSIDE_ENVS.map { (k, e) -> Triple("${e.icon} ${e.name}", app.sky.outsideEnv == k, { post { s.setOutside(k) } }) })
        h4("Season")
        chips(listOf("live" to "Auto (today)", "spring" to "Spring", "summer" to "Summer", "fall" to "Fall", "winter" to "Winter").map { (v, l) ->
            Triple(l + (if (v == "live" && env.seasonMode == "live") " · ${env.currentSeason}" else ""), if (v == "live") env.seasonMode == "live" else env.seasonMode != "live" && env.currentSeason == v, { post { s.setSeasonChoice(v) } })
        })
        h4("Weather", env.W.label + (if (s.weatherMode == "live") s.liveTempC?.let { " · ${it.roundToInt()}°C" } ?: "" else ""))
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
                    hint = "Your city, e.g. Chicago"; setHintTextColor(MUTED); setTextColor(TEXT); typeface = MONO_N; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setText(cityDraft.ifEmpty { s.cityName }); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS; imeOptions = EditorInfo.IME_ACTION_GO; isSingleLine = true
                    background = box(Color.rgb(7, 9, 24), LINE, 8f); setPadding(dpi(8f), dpi(8f), dpi(8f), dpi(8f))
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
                note("Live weather comes from Open-Meteo, a free weather service. Only the city name you type is sent to it. Refreshes every 20 minutes.", true)
            }
            "auto" -> note("The weather drifts naturally on its own every 10-20 minutes, fitting the season.")
        }
        h4("Sky")
        chips(Settings.SKY_STYLES.map { (k, l) -> Triple(l, env.skyStyle == k, { post { s.setSkyStyle(k) } }) })
        h4("Moon", s.moonReadout())
        seg(listOf("cinematic" to "Cinematic path", "real" to "Real astronomy"), if (env.realisticMoon) "real" else "cinematic") { v -> post { s.setRealisticMoon(v == "real") } }
        note("Cinematic keeps the moon gliding low across your window all night. Real astronomy puts it exactly where it is in the sky (some nights it won't be up). Either way the phase matches tonight's real moon.")
    }
    private fun buildSound() {
        val snd = app.sound
        bigToggle(if (snd.playing) "AUDIO: ON" else "AUDIO: OFF", snd.playing) { post { app.toggleAudio() } }
        slider("Master volume", 0, 100, 1, (snd.masterVolume * 100).roundToInt(), { "$it%" }) { v -> post { snd.setMasterVolume(v / 100.0); s.changed() } }
        val tv = app.tv
        tiles(listOf(
            Triple(if (tv.bedroomTvOn) "TV: ${Tv.TV_CHANNEL_NAMES[tv.bedroomTvChannel].substringBefore(':')}" else "TV CHANNEL", tv.bedroomTvOn, { post { app.tvNextChannel(); app.sfx("tvChannel"); s.changed() } }),
            Triple(if (app.extras.radioStation > 0) "RADIO: ${app.extras.RADIO_STATIONS[app.extras.radioStation].substringBefore(':')}" else "RADIO: OFF", app.extras.radioStation > 0, { post { app.extras.cycleRadio(app.furniture.radioDisplayMat); app.sfx("radioTune"); s.changed() } }),
            Triple("▶ YOUTUBE ON TV", app.ytActive, { close(); app.hud?.yt?.openDialog() }),
            Triple("PC SCREEN", app.monitor.pcPoweredOn, { post { val on = app.monitor.pcPoweredOn; app.monitorNextChannel(); app.sfx(if (on) "monitorChannel" else "pcOn"); s.changed() } })
        ))
        buildMyMusic()
        details("Mixer") {
            for ((k, label) in listOf("pc" to "PC tower fan & HDD", "fan" to "Ceiling fan", "ac" to "Wall AC", "livtv" to "Living room TV (muffled)", "bedtv" to "Bedroom TV & PS1",
                    "weather" to "Rain, wind & storms", "nature" to "Outdoors: city, birds, crickets", "pcspk" to "PC speakers", "sfx" to "Clicks, PS1 drive, lava lamp", "radio" to "Boombox radio"))
                slider(label, 0, 150, 5, ((snd.vol[k] ?: 1.0) * 100).roundToInt(), { "$it%" }) { v -> post { snd.vol[k] = v / 100.0; snd.updateLayered(); s.changed() } }
        }
    }
    /** HTML 13600-13788: the boombox player, in the Sound tab. */
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
    private fun buildRoom() {
        val blindLabels = arrayOf("SLATTED", "TILTED OPEN", "ROLLED UP", "CLOSED")
        h4("Window")
        tiles(listOf(Triple("BLINDS: ${blindLabels.getOrElse(app.room.blindsOpenState) { "SLATTED" }}", false, { post { app.room.cycleWindowBlinds(); s.changed() } })))
        note("Tap the blinds (here or in the room) to cycle: slatted → tilted open → rolled all the way up → closed.")
        h4("Lights")
        val strNames = arrayOf("OFF", "WARM", "RAINBOW")
        tiles(listOf(
            Triple("FAN LIGHT: ${if (app.fanLightOn) "ON" else "OFF"}", app.fanLightOn, { post { app.toggleFanLight(); s.changed() } }),
            Triple("LAMP: ${if (app.furniture.lampOn) "ON" else "OFF"}", app.furniture.lampOn, { post { app.furniture.toggleLamp(); s.changed() } }),
            Triple("STRING LIGHTS: ${strNames[app.decor.stringLightsMode]}", app.decor.stringLightsMode > 0, { post { app.decor.cycleStringLights(); s.changed() } }),
            Triple("SPACE HEATER: ${app.extras.heaterMode.uppercase()}", app.extras.heaterMode != "off", { post { app.extras.cycleHeater(); s.changed() } }),
            Triple("📚 GAMES BACK IN ORDER", false, { post { app.library.sortGameShelves(); s.changed() } })
        ))
        h4("Appliances")
        tiles(listOf(
            Triple("CEILING FAN: ${if (app.fan.fanSpinning) "ON" else "OFF"}", app.fan.fanSpinning, { post { app.fan.toggleCeilingFanSpin(); s.changed() } }),
            Triple("AC UNIT: ${if (app.room.acUnitOn) "ON" else "OFF"}", app.room.acUnitOn, { post { app.room.toggleAcUnit(); s.changed() } }),
            Triple("BEDROOM TV: ${if (app.tv.bedroomTvOn) "ON" else "OFF"}", app.tv.bedroomTvOn, { post { app.tvPower(); s.changed() } }),
            Triple("LIVING RM TV: ${if (app.sound.livingRoomTvOn) "ON" else "OFF"}", app.sound.livingRoomTvOn, { post { app.sound.livingRoomTvOn = !app.sound.livingRoomTvOn; app.sfx("ui"); app.sound.updateLayered(); s.changed() } }),
            Triple("🖥 USE THE PC", false, { close(); post { app.openPc() } }),
            Triple("DOOR: CLOSED", false, { post { app.toast("🚧 UNDER CONSTRUCTION: The rest of the house is still being built. Check back in a future update!") } })
        ))
        h4("Poster above the desk")
        chips(app.extras.POSTER_NAMES.map { (k, l) -> Triple(l, app.extras.posterChoice == k, { post { app.extras.choosePoster(k); s.changed() } }) })
        for ((part, title) in listOf("walls" to "Wall paint", "carpet" to "Carpet", "sheets" to "Bedsheets")) {
            h4(title)
            chips(app.extras.colorOptions(part).map { (k, l) -> Triple(l, app.extras.roomColors[part] == k, { post { app.extras.setRoomColor(part, k); s.changed() } }) })
        }
        h4("Decorations")
        chips(listOf("auto" to "Auto (by date)", "off" to "None", "halloween" to "Halloween", "winter" to "Winter holidays", "valentine" to "Valentine's", "summer" to "Summer").map { (k, l) ->
            Triple(l + (if (k == "auto" && app.extras.decorMode == "auto") " · ${app.extras.activeDecor()}" else ""), app.extras.decorMode == k, { post { app.extras.decorMode = k; app.extras.applyDecor(); s.changed() } })
        })
        note("Auto puts up decorations for the time of year: Halloween in October, a little tree in December, hearts for Valentine's and a box fan in summer.")
        note("Almost everything in the room can be tapped directly too: the TV, the PlayStation and its buttons, the games on the shelves, the lamp, the boombox, the door and the light switch.")
    }
    private fun buildDisplay() {
        h4("Quality", "${app.renderer.internalW}×${app.renderer.internalH}")
        seg(listOf("saver" to "Battery saver", "balanced" to "Balanced", "high" to "High"), s.qualityMode) { v -> post { s.setQuality(v) } }
        note(Settings.QUALITY.getValue(s.qualityMode).note)
        h4("Frame rate")
        seg(listOf("auto" to "Auto", "30" to "30", "60" to "60", "120" to "120 Hz"), s.fpsChoice) { v -> post { s.setFps(v) } }
        check("Eco idle: drop to 30 fps when you haven't touched anything for a bit, then 20 fps after 2 minutes", s.ecoIdle) { c -> post { s.ecoIdle = c; s.applyFps(); s.changed() } }
        h4("Look")
        chips(Settings.VIBES.map { (k, v) -> Triple(v.label, s.vibeMode == k, { post { s.setVibe(k) } }) })
        details("PS1 effects (advanced)") {
            slider("Resolution", 160, 720, 20, app.resShort, { "${it}p" }) { v -> post { app.setResolution(v); s.changed() } }
            slider("Vertex wobble", 45, 300, 5, app.jitterBase.roundToInt(), { "$it" }) { v -> post { app.setJitter(v.toFloat()); s.changed() } }
            slider("Texture warp", 0, 100, 5, (app.renderer.ps1Uniforms.affineStrength * 100).roundToInt(), { "$it%" }) { v -> post { app.renderer.ps1Uniforms.affineStrength = v / 100f; s.changed() } }
            slider("Dither & 15-bit colour", 0, 100, 10, (app.renderer.post.ditherStrength * 100).roundToInt(), { "$it%" }) { v -> post { app.renderer.post.ditherStrength = v / 100f; s.changed() } }
            slider("Ambient occlusion", 0, 150, 10, (app.aoBase * 100).roundToInt(), { "$it%" }) { v -> post { app.aoBase = v / 100f; app.renderer.post.aoStrength = app.aoBase; s.changed() } }
            slider("Light rays", 0, 200, 10, (env.userVolumetricSetting * 100).roundToInt(), { "$it%" }) { v -> post { env.userVolumetricSetting = v / 100f; env.applyEnvironmentSettings(); s.changed() } }
            slider("Camera breathing", 0, 100, 5, (app.swayAmount * 100).roundToInt(), { "$it%" }) { v -> post { app.swayAmount = v / 100f; s.changed() } }
        }
        h4("Temperature")
        seg(listOf("F" to "°F  Fahrenheit", "C" to "°C  Celsius"), s.tempUnit) { v -> post { s.tempUnit = v; s.changed(); app.hud?.refreshStatus() } }
        app.hud?.yt?.let { yt -> check("PS1 look on YouTube: a pixel grid the size of the room's pixels, chunkier scanlines, and the picture wobbles with the TV", yt.ps1Look) { c -> yt.ps1Look = c } }
        check("Show the start screen when the app opens", s.startScreenEnabled) { c -> post { s.startScreenEnabled = c; s.changed() } }
        check("Debug: the free camera can leave the room", s.debugFreeCam) { c -> post { s.debugFreeCam = c } }
    }
    private fun buildTimers() {
        h4("Sleep timer", if (s.sleepEnd > 0) "${s.sleepLeftText()} left" else "Off")
        bigToggle("😴 SLEEP MODE", false) { close(); post { s.startSleepMode() } }
        note("One tap: lights down, fan on, a soft glow, you in bed, and everything fades out in 45 minutes.")
        h4("Sleep timer")
        chips(listOf(0, 15, 30, 45, 60, 90).map { m -> Triple(if (m > 0) "$m min" else "Off", m == 0 && s.sleepEnd == 0L, { post { s.setSleepTimer(m) } }) })
        check("Fade the screen to black when it ends", s.sleepDim) { c -> post { s.sleepDim = c; s.changed() } }
        h4("Focus timer")
        chips(listOf("off" to "Off", "short" to "25 min + 5 break", "long" to "50 min + 10 break").map { (k, l) -> Triple(l, s.focus.mode == k, { post { s.setFocus(k) } }) })
        note("Work in focused blocks with short breaks. A soft bell rings when it's time to switch (with the audio update).")
        val al = app.alarm
        h4("⏰ Sunrise alarm", if (al.on) al.time else "Off")
        check("On", al.on) { c -> post { al.set(on = c); app.sfx("ui"); app.toast(if (c) "⏰ Alarm set for ${al.time}. The room starts to glow 20 minutes before." else "⏰ Alarm off"); s.changed() } }
        val hours = (4..11).flatMap { h -> listOf("%02d:00".format(h), "%02d:30".format(h)) }
        chips(hours.map { t -> Triple(t, al.time == t, { post { al.set(time = t, on = true); s.changed() } }) })
        check("Wake to my music (otherwise birdsong)", al.music) { c -> post { al.set(music = c); s.changed() } }
        note("The room slowly fills with warm light for 20 minutes before your alarm, then your music or birdsong plays. Keep the app open (sleep mode works well).", true)
    }
    private fun buildAbout() {
        h4("PS1 Bedroom", "v${Changelog.entries.first().version}")
        note("A little late-night room to keep you company. Real time, real sky, real weather, a PlayStation full of games, and a soundscape for every corner of the room.")
        note("Made by Daymion Gallo, aka Galloade, aka JenZiMiBruh.")
        tiles(listOf(Triple("📜 CHANGELOG", false, { Changelog.show(act) }), Triple("❓ HOW TO", false, { close(); app.hud?.overlays?.startTutorial() })))
        val ach = app.achievements
        h4("🏆 Achievements", "${ach.count} / ${Achievements.LIST.size}")
        chips(Achievements.LIST.map { (id, a) -> val got = ach.has(id); Triple("${if (got) a[0] else "🔒"} ${a[1]}: ${a[2]}", got, { }) })
    }

    private fun hideKeyboard(v: View) {
        (act.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0)
    }

    // ================= Status bar =================
    fun refreshStatus() {
        if (!app.isBuilt) return
        val st = s.status()
        sbViews["time"]?.text = st.time
        sbViews["temp"]?.text = st.temp
        sbViews["season"]?.text = st.season
        sbViews["weather"]?.text = st.weather
        sbViews["place"]?.text = st.place
        postureView.text = st.posture
        // Narrow phones hide the posture label (the HTML's max-width: 520px rule)
        val narrow = root.width / act.resources.displayMetrics.density <= 520f
        postureView.visibility = if (narrow) View.GONE else View.VISIBLE
        statusChip.findViewWithTag<View>("psep")?.visibility = postureView.visibility
        timerChip.visibility = if (st.timer != null) View.VISIBLE else View.GONE
        st.timer?.let { timerChip.text = it }
        focusChip.visibility = if (st.focus != null) View.VISIBLE else View.GONE
        st.focus?.let { focusChip.text = it }
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
            c.measure(if (full) MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.EXACTLY) else MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
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
