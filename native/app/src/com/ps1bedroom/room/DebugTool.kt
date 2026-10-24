package com.ps1bedroom.room

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Calendar

/**
 * PS1 DEVELOPER DEBUG SUITE (In-App Rapid Testing Tool).
 * Allows instant 1-tap testing of:
 * - Weather presets, live wind, instant lightning strikes, leaf flurry bursts, window hit rates
 * - Time warp (1x - 300x), day/night presets, day of week switcher, moon phases
 * - Instant jump across all 15 outside locations and freeway traffic modes
 * - TV channels, TV remote, Simple vs Detailed WeatherStar view, TV zoom camera
 * - Jasper behaviors, locations, needs, and room appliances
 * - Live performance diagnostics (FPS, heap RAM, particle counts, weather metrics)
 */
@SuppressLint("SetTextI18n")
class DebugTool(
    private val act: Activity,
    private val root: FrameLayout,
    private val app: App
) {
    private val MONO = Typeface.create("monospace", Typeface.BOLD)
    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, act.resources.displayMetrics)
    private fun dpi(v: Float) = dp(v).toInt()

    private val overlay = FrameLayout(act)
    private val panel = LinearLayout(act)
    private val contentScroll = ScrollView(act)
    private val contentContainer = LinearLayout(act)
    private val tabRow = LinearLayout(act)
    private var currentTab = "WEATHER"

    private val handler = Handler(Looper.getMainLooper())
    private var statsTicker: Runnable? = null
    private var statsView: TextView? = null

    val isOpen: Boolean get() = overlay.visibility == View.VISIBLE

    init {
        overlay.visibility = View.GONE
        overlay.setBackgroundColor(Color.argb(170, 0, 0, 0))
        overlay.setOnClickListener { close() } // tap backdrop to dismiss

        // Main Dialog Card
        panel.orientation = LinearLayout.VERTICAL
        panel.setPadding(dpi(12f), dpi(10f), dpi(12f), dpi(12f))

        val shadow = GradientDrawable().apply { setColor(Color.argb(200, 0, 0, 0)); cornerRadius = dp(12f) }
        val body = GradientDrawable().apply {
            setColor(Color.parseColor("#0d121d"))
            cornerRadius = dp(12f)
            setStroke(dpi(2f), Color.parseColor("#0284c7"))
        }
        panel.background = LayerDrawable(arrayOf(shadow, body)).apply {
            setLayerInset(0, dpi(4f), dpi(4f), 0, 0)
            setLayerInset(1, 0, 0, dpi(4f), dpi(4f))
        }
        panel.isClickable = true // don't close when tapping panel itself

        // Header
        val header = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dpi(6f))
        }
        val headerTitle = TextView(act).apply {
            text = "⚡ PS1 DEV DEBUG TOOL"
            setTextColor(Color.parseColor("#38bdf8"))
            typeface = MONO
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn = TextView(act).apply {
            text = "✕ CLOSE"
            setTextColor(Color.parseColor("#f87171"))
            typeface = MONO
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dpi(8f), dpi(4f), dpi(8f), dpi(4f))
            setOnClickListener { close() }
        }
        header.addView(headerTitle)
        header.addView(closeBtn)
        panel.addView(header)

        // Tab Navigation Bar
        val tabScroll = HorizontalScrollView(act).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpi(8f)
            }
        }
        tabRow.orientation = LinearLayout.HORIZONTAL
        tabScroll.addView(tabRow)
        panel.addView(tabScroll)

        // Scrollable Body
        contentContainer.orientation = LinearLayout.VERTICAL
        contentScroll.addView(contentContainer)
        panel.addView(contentScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val lp = FrameLayout.LayoutParams(dpi(340f), dpi(420f)).apply {
            gravity = Gravity.CENTER
        }
        overlay.addView(panel, lp)
        root.addView(overlay, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        buildTabs()
        renderTab(currentTab)
    }

    private fun btn(
        text: String,
        bgCol: String = "#1e293b",
        textCol: String = "#f8fafc",
        borderCol: String = "#334155",
        textSizeSp: Float = 11f,
        heightDp: Float = 34f,
        onClick: () -> Unit
    ): TextView = TextView(act).apply {
        this.text = text
        setTextColor(Color.parseColor(textCol))
        typeface = MONO
        setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            setColor(Color.parseColor(bgCol))
            cornerRadius = dp(6f)
            setStroke(dpi(1.2f), Color.parseColor(borderCol))
        }
        layoutParams = LinearLayout.LayoutParams(0, dpi(heightDp), 1f).apply {
            setMargins(dpi(2f), dpi(2f), dpi(2f), dpi(2f))
        }
        isClickable = true
        isFocusable = true
        setOnClickListener {
            app.sound.sfx("ps1Button")
            onClick()
        }
    }

    private fun sectionTitle(label: String): TextView = TextView(act).apply {
        text = "▪ $label"
        setTextColor(Color.parseColor("#facc15"))
        typeface = MONO
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
        setPadding(0, dpi(8f), 0, dpi(3f))
    }

    private fun buildTabs() {
        tabRow.removeAllViews()
        val tabs = arrayOf("WEATHER", "TIME", "PLACES", "TV / MEDIA", "JASPER", "STATS")
        for (tab in tabs) {
            val tv = TextView(act).apply {
                text = tab
                val isSel = tab == currentTab
                setTextColor(if (isSel) Color.parseColor("#0f172a") else Color.parseColor("#94a3b8"))
                typeface = MONO
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setPadding(dpi(9f), dpi(5f), dpi(9f), dpi(5f))
                background = GradientDrawable().apply {
                    setColor(if (isSel) Color.parseColor("#38bdf8") else Color.parseColor("#182234"))
                    cornerRadius = dp(6f)
                    if (!isSel) setStroke(dpi(1f), Color.parseColor("#334155"))
                }
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, dpi(4f), 0)
                }
                setOnClickListener {
                    currentTab = tab
                    buildTabs()
                    renderTab(currentTab)
                }
            }
            tabRow.addView(tv)
        }
    }

    private fun renderTab(tab: String) {
        stopStatsTicker()
        contentContainer.removeAllViews()

        when (tab) {
            "WEATHER" -> renderWeatherTab()
            "TIME" -> renderTimeTab()
            "PLACES" -> renderPlacesTab()
            "TV / MEDIA" -> renderTvTab()
            "JASPER" -> renderJasperTab()
            "STATS" -> renderStatsTab()
        }
    }

    // =========================================================================
    // 1. WEATHER & ATMOSPHERE TAB
    // =========================================================================
    private fun renderWeatherTab() {
        contentContainer.addView(sectionTitle("INSTANT LIGHTNING & STORM TESTING"))

        // Giant Instant Lightning Trigger Button
        val strikeRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        val strikeBtn = btn("⚡ STRIKE LIGHTNING NOW (Audio + Flash)", bgCol = "#7f1d1d", textCol = "#fef08a", borderCol = "#ef4444", textSizeSp = 11.5f, heightDp = 40f) {
            app.post {
                app.weatherFx.forceLightningStrike()
                app.toast("⚡ Lightning Struck! Flash + Thunderclap executed.")
            }
        }
        strikeRow.addView(strikeBtn)
        contentContainer.addView(strikeRow)

        contentContainer.addView(sectionTitle("THUNDER ACOUSTIC EQ"))
        val eqRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        eqRow.addView(btn("⚡ FULL SPECTRUM") {
            app.post {
                app.settings.setThunderFreqMode("full")
                app.toast("⚡ Thunder EQ: Full Spectrum (30Hz - 16kHz)")
            }
        })
        eqRow.addView(btn("🔊 LOW BASS (Sub 80Hz)") {
            app.post {
                app.settings.setThunderFreqMode("low")
                app.toast("🔊 Thunder EQ: Low Bass Rumble (<80Hz sub-bass)")
            }
        })
        eqRow.addView(btn("💥 HIGH CRACKLE") {
            app.post {
                app.settings.setThunderFreqMode("high")
                app.toast("💥 Thunder EQ: High Crackle (Supersonic shockwave)")
            }
        })
        contentContainer.addView(eqRow)

        contentContainer.addView(sectionTitle("WEATHER KIND PRESETS"))
        val wRow1 = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        wRow1.addView(btn("☀️ CLEAR", bgCol = "#065f46", textCol = "#a7f3d0") { app.post { app.settings.pickWeather("clear"); app.toast("Weather: Clear") } })
        wRow1.addView(btn("⛅ PARTLY", bgCol = "#1e3a8a", textCol = "#bfdbfe") { app.post { app.settings.pickWeather("partly"); app.toast("Weather: Partly Cloudy") } })
        wRow1.addView(btn("☁️ OVERCAST", bgCol = "#334155", textCol = "#cbd5e1") { app.post { app.settings.pickWeather("overcast"); app.toast("Weather: Overcast") } })
        contentContainer.addView(wRow1)

        val wRow2 = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        wRow2.addView(btn("🌧️ RAIN", bgCol = "#0369a1", textCol = "#bae6fd") { app.post { app.settings.pickWeather("rain"); app.toast("Weather: Rain") } })
        wRow2.addView(btn("⛈️ STORM", bgCol = "#4c1d95", textCol = "#e9d5ff") { app.post { app.settings.pickWeather("storm"); app.toast("Weather: Thunderstorm") } })
        wRow2.addView(btn("❄️ SNOW", bgCol = "#0284c7", textCol = "#e0f2fe") { app.post { app.settings.pickWeather("snow"); app.toast("Weather: Snow") } })
        contentContainer.addView(wRow2)

        val wRow3 = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        wRow3.addView(btn("🌫️ FOG", bgCol = "#475569", textCol = "#f1f5f9") { app.post { app.settings.pickWeather("fog"); app.toast("Weather: Dense Fog") } })
        wRow3.addView(btn("🍃 WINDY", bgCol = "#0f766e", textCol = "#ccfbf1") { app.post { app.settings.pickWeather("windy"); app.toast("Weather: High Wind") } })
        wRow3.addView(btn("🌧️ DRIZZLE", bgCol = "#1e40af", textCol = "#dbeafe") { app.post { app.settings.pickWeather("drizzle"); app.toast("Weather: Drizzle") } })
        contentContainer.addView(wRow3)

        contentContainer.addView(sectionTitle("LIVE WIND SPEED"))
        val windRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        windRow.addView(btn("0 MPH (Calm)") { app.post { app.env.liveWindSpeedKmH = 0f; app.env.applyEnvironmentSettings(); app.toast("Wind: Calm (0 mph)") } })
        windRow.addView(btn("10 MPH (Breeze)") { app.post { app.env.liveWindSpeedKmH = 16f; app.env.applyEnvironmentSettings(); app.toast("Wind: Breeze (10 mph)") } })
        windRow.addView(btn("25 MPH (Windy)") { app.post { app.env.liveWindSpeedKmH = 40f; app.env.applyEnvironmentSettings(); app.toast("Wind: Windy (25 mph)") } })
        windRow.addView(btn("50 MPH (Gale)") { app.post { app.env.liveWindSpeedKmH = 80f; app.env.applyEnvironmentSettings(); app.toast("Wind: Gale (50 mph)") } })
        contentContainer.addView(windRow)

        contentContainer.addView(sectionTitle("WIND DIRECTION"))
        val dirRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        for (d in arrayOf("N 0°" to 0f, "NE 45°" to 45f, "E 90°" to 90f, "SE 135°" to 135f, "S 180°" to 180f, "SW 225°" to 225f, "W 270°" to 270f, "NW 315°" to 315f)) {
            dirRow.addView(btn(d.first, textSizeSp = 8.5f) {
                app.post {
                    app.env.liveWindDirDeg = d.second
                    app.env.applyEnvironmentSettings()
                    app.toast("Wind Direction: ${d.first}")
                }
            })
        }
        contentContainer.addView(dirRow)

        contentContainer.addView(sectionTitle("BEDROOM WINDOW SASH & OUTDOOR SOUND"))
        val winRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        winRow.addView(btn("🪟 TOGGLE WINDOW (Open / Closed)", bgCol = "#065f46", textCol = "#a7f3d0", borderCol = "#10b981", heightDp = 38f) {
            app.post {
                val nowOpen = app.room.toggleWindow()
                app.sound.updateLayered()
                app.settings.changed()
                app.toast("🪟 Window: ${if (nowOpen) "OPEN (Loud outdoor audio, leaves drift inside)" else "CLOSED (Muffled glass)"}")
            }
        })
        contentContainer.addView(winRow)

        contentContainer.addView(sectionTitle("LEAF PHYSICS & WINDOW HITS"))
        val leafRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        leafRow.addView(btn("🍂 BURST LEAF FLURRY", bgCol = "#b45309", textCol = "#fef3c7", borderCol = "#f59e0b") {
            app.post {
                app.weatherFx.spawnLeafFlurry(24)
                app.toast("🍂 Spawned 24 leaves drifting toward window pane!")
            }
        })
        contentContainer.addView(leafRow)

        val hitRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        hitRow.addView(btn("Hits: 10%") { app.post { app.settings.setWindowHitRate(0.10f); app.toast("Window Leaf Hit Rate: 10%") } })
        hitRow.addView(btn("Hits: 50%") { app.post { app.settings.setWindowHitRate(0.50f); app.toast("Window Leaf Hit Rate: 50%") } })
        hitRow.addView(btn("Hits: 100% (ALL)") { app.post { app.settings.setWindowHitRate(1.00f); app.toast("Window Leaf Hit Rate: 100% (Always Hit)") } })
        contentContainer.addView(hitRow)

        contentContainer.addView(sectionTitle("LIVE WEATHER REFRESH INTERVAL"))
        val refRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        refRow.addView(btn("5 MIN") { app.post { app.settings.setLiveWeatherRefreshMinutes(5) } })
        refRow.addView(btn("15 MIN") { app.post { app.settings.setLiveWeatherRefreshMinutes(15) } })
        refRow.addView(btn("30 MIN") { app.post { app.settings.setLiveWeatherRefreshMinutes(30) } })
        refRow.addView(btn("60 MIN") { app.post { app.settings.setLiveWeatherRefreshMinutes(60) } })
        contentContainer.addView(refRow)

        contentContainer.addView(sectionTitle("SEASONS"))
        val sRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        sRow.addView(btn("🌸 SPRING") { app.post { app.settings.setSeasonChoice("spring"); app.toast("Season: Spring") } })
        sRow.addView(btn("☀️ SUMMER") { app.post { app.settings.setSeasonChoice("summer"); app.toast("Season: Summer") } })
        sRow.addView(btn("🍂 FALL") { app.post { app.settings.setSeasonChoice("fall"); app.toast("Season: Fall") } })
        sRow.addView(btn("❄️ WINTER") { app.post { app.settings.setSeasonChoice("winter"); app.toast("Season: Winter") } })
        contentContainer.addView(sRow)
    }

    // =========================================================================
    // 2. TIME & CALENDAR TAB
    // =========================================================================
    private fun renderTimeTab() {
        contentContainer.addView(sectionTitle("DAY & NIGHT TIME PRESETS"))
        val tRow1 = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        tRow1.addView(btn("🌅 DAWN (6 AM)") { app.post { app.settings.setPresetHour(6.0); app.toast("Time: 6:00 AM (Dawn)") } })
        tRow1.addView(btn("☀️ NOON (12 PM)") { app.post { app.settings.setPresetHour(12.0); app.toast("Time: 12:00 PM (Noon)") } })
        tRow1.addView(btn("🌇 DUSK (5:30 PM)") { app.post { app.settings.setPresetHour(17.5); app.toast("Time: 5:30 PM (Golden Hour)") } })
        contentContainer.addView(tRow1)

        val tRow2 = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        tRow2.addView(btn("🌙 NIGHT (9 PM)") { app.post { app.settings.setPresetHour(21.0); app.toast("Time: 9:00 PM (Night)") } })
        tRow2.addView(btn("🌌 MIDNIGHT") { app.post { app.settings.setPresetHour(0.0); app.toast("Time: 12:00 AM (Midnight)") } })
        tRow2.addView(btn("🕯️ 3 AM WITCHING") { app.post { app.settings.setPresetHour(3.0); app.toast("Time: 3:00 AM (Late Night)") } })
        contentContainer.addView(tRow2)

        contentContainer.addView(sectionTitle("TIME WARP MULTIPLIER"))
        val warpRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        warpRow.addView(btn("1x Real") { app.post { app.settings.setTimeMode("live"); app.toast("Time: Real-Time Live Clock") } })
        warpRow.addView(btn("5x Fast") { app.post { app.settings.setCycleMinutes(288.0); app.toast("Time Warp: 5x Speed") } })
        warpRow.addView(btn("20x Warp") { app.post { app.settings.setCycleMinutes(72.0); app.toast("Time Warp: 20x Speed") } })
        warpRow.addView(btn("60x Hyper") { app.post { app.settings.setCycleMinutes(24.0); app.toast("Time Warp: 60x Speed") } })
        warpRow.addView(btn("300x Ultra") { app.post { app.settings.setCycleMinutes(4.8); app.toast("Time Warp: 300x Ultra Speed") } })
        contentContainer.addView(warpRow)

        contentContainer.addView(sectionTitle("DAY OF THE WEEK"))
        val dowRow1 = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        val dowRow2 = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        val dows = arrayOf("MON" to Calendar.MONDAY, "TUE" to Calendar.TUESDAY, "WED" to Calendar.WEDNESDAY, "THU" to Calendar.THURSDAY)
        val dows2 = arrayOf("FRI" to Calendar.FRIDAY, "SAT" to Calendar.SATURDAY, "SUN" to Calendar.SUNDAY)
        for ((name, id) in dows) {
            dowRow1.addView(btn(name) { app.post { app.settings.setPresetDayOfWeek(id); app.toast("Day of Week set to $name") } })
        }
        for ((name, id) in dows2) {
            dowRow2.addView(btn(name) { app.post { app.settings.setPresetDayOfWeek(id); app.toast("Day of Week set to $name") } })
        }
        dowRow2.addView(btn("LIVE CLOCK") { app.post { app.settings.setTimeMode("live"); app.toast("Synced to real-world day & hour") } })
        contentContainer.addView(dowRow1)
        contentContainer.addView(dowRow2)

        contentContainer.addView(sectionTitle("MOON CONTROLS"))
        val moonRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        moonRow.addView(btn("🌑 NEW") { app.post { app.env.realisticMoon = false; app.env.moonPhase = 0.0; app.env.applyEnvironmentSettings(); app.toast("Moon: New Moon") } })
        moonRow.addView(btn("🌓 QUARTER") { app.post { app.env.realisticMoon = false; app.env.moonPhase = 0.25; app.env.applyEnvironmentSettings(); app.toast("Moon: First Quarter") } })
        moonRow.addView(btn("🌕 FULL") { app.post { app.env.realisticMoon = false; app.env.moonPhase = 0.5; app.env.applyEnvironmentSettings(); app.toast("Moon: Full Moon") } })
        moonRow.addView(btn("REALISTIC") { app.post { app.settings.setRealisticMoon(!app.env.realisticMoon); app.toast("Realistic Moon: ${app.env.realisticMoon}") } })
        contentContainer.addView(moonRow)

        contentContainer.addView(sectionTitle("CALENDAR YEAR"))
        val calRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        calRow.addView(btn("📅 2001 NOSTALGIA") { app.post { app.settings.setCalendarYear("2001"); app.toast("📅 Wall Calendar: 2001 (Wed Oct 24)") } })
        calRow.addView(btn("📅 2026 LIVE YEAR") { app.post { app.settings.setCalendarYear("2026"); app.toast("📅 Wall Calendar: 2026 (Live Year)") } })
        calRow.addView(btn("TOGGLE YEAR") { app.post { val yr = app.settings.toggleCalendarYear(); app.toast("📅 Wall Calendar: $yr") } })
        contentContainer.addView(calRow)
    }

    // =========================================================================
    // 3. PLACES & TRAFFIC TAB
    // =========================================================================
    private fun renderPlacesTab() {
        contentContainer.addView(sectionTitle("JUMP TO OUTSIDE LOCATIONS (15 PLACES)"))
        val places = arrayOf(
            "city" to "🏙️ Downtown", "skyscraper" to "🏢 Skyscraper", "neon" to "🌆 Neon City",
            "neighborhood" to "🏡 Neighborhood", "suburbs" to "🏘️ Suburbs", "house" to "🌳 Backyard Swings",
            "trailer" to "🚐 Trailer Park", "arizona" to "🏜️ Arizona", "desert" to "🌵 Desert Canyon",
            "island" to "🏝️ Tropical Island", "beach" to "🏖️ Beach Boardwalk", "lake" to "🏞️ Lakeside",
            "forest" to "🌲 Forest", "mountains" to "⛰️ Mountains", "rainforest" to "🌴 Rainforest"
        )
        for (i in places.indices step 3) {
            val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
            for (j in 0 until 3) {
                if (i + j < places.size) {
                    val p = places[i + j]
                    row.addView(btn(p.second, textSizeSp = 9.5f) {
                        app.post {
                            app.settings.setOutside(p.first)
                            app.toast("Switched to ${p.second}")
                        }
                    })
                }
            }
            contentContainer.addView(row)
        }

        contentContainer.addView(sectionTitle("FREEWAY & CAR TRAFFIC"))
        val carRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        carRow.addView(btn("🚗 NORMAL") { app.post { app.settings.setCarsMode("on"); app.toast("Cars: Normal Traffic") } })
        carRow.addView(btn("🏎️ DENSE") { app.post { app.settings.setCarsMode("on"); app.settings.setCarsDensity(2.0f); app.toast("Cars: Heavy Freeway Rush") } })
        carRow.addView(btn("🚙 SPARSE") { app.post { app.settings.setCarsMode("on"); app.settings.setCarsDensity(0.4f); app.toast("Cars: Sparse") } })
        carRow.addView(btn("🛑 OFF") { app.post { app.settings.setCarsMode("off"); app.toast("Cars: Disabled") } })
        contentContainer.addView(carRow)
    }

    // =========================================================================
    // 4. TV & MEDIA TAB
    // =========================================================================
    private fun renderTvTab() {
        contentContainer.addView(sectionTitle("TV POWER & VIEW MODES"))
        val tvActionRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        tvActionRow.addView(btn("⏻ TV POWER", bgCol = "#7f1d1d", textCol = "#fecaca", borderCol = "#ef4444") {
            app.post { app.tvPower() }
        })
        tvActionRow.addView(btn("📺 OPEN REMOTE", bgCol = "#1e293b", textCol = "#38bdf8", borderCol = "#0284c7") {
            app.hud?.openTvRemote()
        })
        tvActionRow.addView(btn("🔍 ZOOM TV", bgCol = "#1e1b4b", textCol = "#c7d2fe", borderCol = "#818cf8") {
            app.post { app.toggleTvZoom() }
        })
        contentContainer.addView(tvActionRow)

        contentContainer.addView(sectionTitle("WEATHER CHANNEL VIEW MODE"))
        val wViewRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        wViewRow.addView(btn("TOGGLE VIEW (Simple ↔ 90s WeatherStar)", bgCol = "#172554", textCol = "#facc15", borderCol = "#3b82f6", heightDp = 38f) {
            app.post { app.toggleWeatherView() }
        })
        contentContainer.addView(wViewRow)

        contentContainer.addView(sectionTitle("DIRECT CHANNEL SELECTOR"))
        val chs = arrayOf(
            0 to "CH 04 Anime", 1 to "CH 12 Weather", 2 to "CH 03 VCR", 3 to "CH 24 Shopping",
            4 to "CH 99 Static", 5 to "CH 06 Cartoons", 6 to "CH 32 Music", Tv.PS1_TV_CHANNEL to "AV 1 PS1"
        )
        for (i in chs.indices step 2) {
            val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
            val c1 = chs[i]
            row.addView(btn(c1.second) { app.post { app.tvSetChannel(c1.first); app.toast("Switched to ${c1.second}") } })
            if (i + 1 < chs.size) {
                val c2 = chs[i + 1]
                row.addView(btn(c2.second) { app.post { app.tvSetChannel(c2.first); app.toast("Switched to ${c2.second}") } })
            }
            contentContainer.addView(row)
        }

        contentContainer.addView(sectionTitle("YOUTUBE CRT BROADCAST"))
        val ytRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        ytRow.addView(btn("▶ OPEN YOUTUBE ON CRT", bgCol = "#7f1d1d", textCol = "#ffffff", borderCol = "#dc2626", heightDp = 36f) {
            app.post { app.openYouTube() }
        })
        contentContainer.addView(ytRow)
    }

    // =========================================================================
    // 5. JASPER & ROOM TAB
    // =========================================================================
    private fun renderJasperTab() {
        contentContainer.addView(sectionTitle("JASPER TELEPORT & SPOTS"))
        val jSpotRow1 = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        jSpotRow1.addView(btn("🛏️ BED") { app.post { app.jasper.goToBed(); app.toast("Jasper sent to bed") } })
        jSpotRow1.addView(btn("🧶 RUG") { app.post { app.jasper.dogGroup.position.set(0.5f, 0f, 0.4f); app.toast("Jasper moved to rug") } })
        jSpotRow1.addView(btn("🪟 WINDOW") { app.post { app.jasper.dogGroup.position.set(1.2f, 0f, -2.1f); app.toast("Jasper moved to window") } })
        contentContainer.addView(jSpotRow1)

        val jSpotRow2 = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        jSpotRow2.addView(btn("🥣 FOOD BOWL") { app.post { app.jasper.feed(app.lastFrameT); app.toast("Fed Jasper") } })
        jSpotRow2.addView(btn("💧 WATER BOWL") { app.post { app.jasper.giveWater(app.lastFrameT); app.toast("Gave Jasper water") } })
        jSpotRow2.addView(btn("🎾 PLAY WITH") { app.post { app.jasper.playWith(app.lastFrameT); app.toast("Played with Jasper") } })
        contentContainer.addView(jSpotRow2)

        contentContainer.addView(sectionTitle("JASPER ACTIONS & NEEDS"))
        val jActionRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        jActionRow.addView(btn("SLEEP") { app.post { app.jasper.sendToSleep(app.lastFrameT); app.toast("Jasper sleeping") } })
        jActionRow.addView(btn("PET DOG") { app.post { app.jasper.petDog(app.lastFrameT); app.toast("Petted Jasper ❤️") } })
        jActionRow.addView(btn("REFILL 100% NEEDS", bgCol = "#065f46", textCol = "#a7f3d0") {
            app.post {
                app.jasper.hunger = 1f; app.jasper.thirst = 1f; app.jasper.energy = 1f; app.jasper.funLevel = 1f
                app.toast("Jasper needs refilled to 100%!")
            }
        })
        contentContainer.addView(jActionRow)

        contentContainer.addView(sectionTitle("BEDROOM APPLIANCES & LIGHTS"))
        val lightRow1 = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        lightRow1.addView(btn("💡 FAN LIGHT") { app.post { app.toggleFanLight(); app.toast("Fan Light Toggled") } })
        lightRow1.addView(btn("🌀 FAN SPIN") { app.post { app.fan.toggleCeilingFanSpin(); app.toast("Fan Spin Toggled") } })
        lightRow1.addView(btn("🛋️ DESK LAMP") { app.post { app.furniture.toggleLamp(); app.toast("Lamp Toggled") } })
        contentContainer.addView(lightRow1)

        val lightRow2 = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        lightRow2.addView(btn("🪟 BLINDS") { app.post { app.room.cycleWindowBlinds(); app.toast("Blinds Cycled") } })
        lightRow2.addView(btn("🪟 WINDOW OPEN/CLOSE") { app.post { val op = app.room.toggleWindow(); app.sound.updateLayered(); app.toast("Window: ${if (op) "OPEN (LOUD)" else "CLOSED (MUFFLED)"}") } })
        lightRow2.addView(btn("❄️ AC UNIT") { app.post { app.room.toggleAcUnit(); app.toast("AC Unit Toggled") } })
        contentContainer.addView(lightRow2)

        val lightRow3 = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        lightRow3.addView(btn("✨ STRING LIGHTS") { app.post { app.decor.cycleStringLights(); app.toast("String Lights Mode: ${app.decor.stringLightsMode}") } })
        contentContainer.addView(lightRow3)

        contentContainer.addView(sectionTitle("HOLIDAY DECORATIONS"))
        val holRow1 = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        val holRow2 = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        holRow1.addView(btn("🎃 HALLOWEEN") { app.post { app.extras.decorMode = "halloween"; app.extras.applyDecor(); app.toast("Holiday: Halloween") } })
        holRow1.addView(btn("🎄 WINTER") { app.post { app.extras.decorMode = "winter"; app.extras.applyDecor(); app.toast("Holiday: Winter Tree") } })
        holRow1.addView(btn("💖 VALENTINE") { app.post { app.extras.decorMode = "valentine"; app.extras.applyDecor(); app.toast("Holiday: Valentine's") } })
        holRow1.addView(btn("🍀 ST PATRICK") { app.post { app.extras.decorMode = "stpatrick"; app.extras.applyDecor(); app.toast("Holiday: St. Patrick's") } })
        holRow2.addView(btn("🐰 EASTER") { app.post { app.extras.decorMode = "easter"; app.extras.applyDecor(); app.toast("Holiday: Easter / Spring") } })
        holRow2.addView(btn("🎆 SUMMER / 4TH") { app.post { app.extras.decorMode = "summer"; app.extras.applyDecor(); app.toast("Holiday: Summer / 4th") } })
        holRow2.addView(btn("🦃 THANKSGIVING") { app.post { app.extras.decorMode = "thanksgiving"; app.extras.applyDecor(); app.toast("Holiday: Thanksgiving") } })
        holRow2.addView(btn("🎉 NEW YEAR") { app.post { app.extras.decorMode = "newyear"; app.extras.applyDecor(); app.toast("Holiday: New Year's") } })
        contentContainer.addView(holRow1)
        contentContainer.addView(holRow2)
    }

    // =========================================================================
    // 6. STATS & DIAGNOSTICS TAB
    // =========================================================================
    private fun renderStatsTab() {
        contentContainer.addView(sectionTitle("REAL-TIME ENGINE DIAGNOSTICS"))
        val tv = TextView(act).apply {
            setTextColor(Color.parseColor("#38bdf8"))
            typeface = MONO
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            setPadding(dpi(6f), dpi(6f), dpi(6f), dpi(6f))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#070b14"))
                cornerRadius = dp(6f)
                setStroke(dpi(1f), Color.parseColor("#1e293b"))
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        statsView = tv
        contentContainer.addView(tv)

        startStatsTicker()
    }

    private fun startStatsTicker() {
        stopStatsTicker()
        statsTicker = object : Runnable {
            override fun run() {
                updateStatsText()
                handler.postDelayed(this, 500)
            }
        }
        handler.post(statsTicker!!)
    }

    private fun stopStatsTicker() {
        statsTicker?.let { handler.removeCallbacks(it) }
        statsTicker = null
    }

    private fun updateStatsText() {
        val tv = statsView ?: return
        val rt = Runtime.getRuntime()
        val usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
        val maxMb = rt.maxMemory() / (1024 * 1024)

        val env = app.env
        val windKmh = if (env.liveWindSpeedKmH >= 0f) env.liveWindSpeedKmH else (env.W.wind * 28f)
        val windMph = (windKmh * 0.621371f).toInt()
        val moonPct = (env.moonIllum * 100).toInt()

        tv.text = """
            |----------------- ENGINE STATS -----------------
            |● FPS:          ${app.lastFps} fps (${app.effectiveFpsCap} cap)
            |● FRAME TIME:   ${String.format("%.1f", app.lastFrameTimeMs)} ms
            |● HEAP MEMORY:  ${usedMb} MB / ${maxMb} MB
            |● ACTIVE LEAVES:${app.weatherFx.activeParticleCount}
            |----------------- ENVIRONMENT -----------------
            |● TIME / DOW:   ${env.formatHour(env.hour)} (${env.dayOfWeekName})
            |● TIME MODE:    ${env.timeMode.uppercase()}
            |● WEATHER:      ${env.W.label.uppercase()}
            |● WIND:         ${windMph} MPH (${env.liveWindDirDeg.toInt()}°)
            |● MOON ILLUM:   ${moonPct}% (${if (env.realisticMoon) "Realistic" else "Manual"})
            |● DAYLIGHT:     ${(env.daylight * 100).toInt()}%
            |● LOCATION:     ${app.places.outsideEnv.uppercase()}
            |----------------- TELEVISION -------------------
            |● TV POWER:     ${if (app.tv.bedroomTvOn) "ON" else "OFF"}
            |● CHANNEL:      ${Tv.TV_CHANNEL_NAMES.getOrNull(app.tv.bedroomTvChannel) ?: "NONE"}
            |● WEATHER VIEW: ${app.settings.weatherChannelView.uppercase()}
            |● YOUTUBE:      ${if (app.ytActive) "ACTIVE" else "INACTIVE"}
            |● CAMERA:       ${app.player.postureLabel()}
        """.trimMargin()
    }

    fun open() {
        overlay.visibility = View.VISIBLE
        buildTabs()
        renderTab(currentTab)
    }

    fun close() {
        stopStatsTicker()
        overlay.visibility = View.GONE
    }

    fun toggle() {
        if (isOpen) close() else open()
    }
}
