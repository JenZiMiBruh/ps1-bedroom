package com.ps1bedroom.room

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * RETRO CRT TV REMOTE CONTROL (Sony RM-90s Style).
 * Compact, tactile floating remote control for managing the bedroom CRT TV:
 * Power, channel steppers (up/down), quick channel presets, Weather Channel View toggle
 * (Simple High-Legibility vs Detailed 90s WeatherStar 4000), camera zoom to CRT glass, and mute.
 */
@SuppressLint("SetTextI18n")
class TvRemote(
    private val act: Activity,
    private val root: FrameLayout,
    private val app: App
) {
    private val MONO = Typeface.create("monospace", Typeface.BOLD)
    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, act.resources.displayMetrics)
    private fun dpi(v: Float) = dp(v).toInt()

    private val container = LinearLayout(act)
    private var isMuted = false

    val isOpen: Boolean get() = container.visibility == View.VISIBLE

    init {
        container.orientation = LinearLayout.VERTICAL
        container.visibility = View.GONE
        container.setPadding(dpi(10f), dpi(8f), dpi(10f), dpi(10f))

        // Remote chassis: dark charcoal matte plastic with retro PS1 beveled drop shadow
        val shadow = GradientDrawable().apply { setColor(Color.argb(160, 0, 0, 0)); cornerRadius = dp(14f) }
        val body = GradientDrawable().apply {
            setColor(Color.parseColor("#181b22"))
            cornerRadius = dp(14f)
            setStroke(dpi(2f), Color.parseColor("#334155"))
        }
        container.background = LayerDrawable(arrayOf(shadow, body)).apply {
            setLayerInset(0, dpi(4f), dpi(4f), 0, 0)
            setLayerInset(1, 0, 0, dpi(4f), dpi(4f))
        }

        buildRemoteUi()

        val lp = FrameLayout.LayoutParams(dpi(165f), FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            setMargins(0, 0, dpi(12f), dpi(75f))
        }
        root.addView(container, lp)
    }

    private fun remoteBtn(
        text: String,
        bgCol: String = "#242936",
        textCol: String = "#f8fafc",
        borderCol: String = "#475569",
        textSizeSp: Float = 10f,
        heightDp: Float = 32f,
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

    private fun buildRemoteUi() {
        container.removeAllViews()

        // 1. Header Bar: Brand logo & Close button
        val header = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpi(2f), 0, dpi(2f), dpi(6f))
        }
        val title = TextView(act).apply {
            text = "TRINITRON RM-90"
            setTextColor(Color.parseColor("#38bdf8"))
            typeface = MONO
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn = TextView(act).apply {
            text = "✕"
            setTextColor(Color.parseColor("#94a3b8"))
            typeface = MONO
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            setPadding(dpi(6f), dpi(2f), dpi(6f), dpi(2f))
            setOnClickListener { close() }
        }
        header.addView(title)
        header.addView(closeBtn)
        container.addView(header)

        // 2. Power & Mute Row
        val rowPower = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        val pwrBtn = remoteBtn("⏻ PWR", bgCol = "#7f1d1d", textCol = "#fecaca", borderCol = "#ef4444") {
            app.post { app.tvPower() }
        }
        val muteBtn = remoteBtn("🔇 MUTE", bgCol = "#312e81", textCol = "#e0e7ff", borderCol = "#6366f1") {
            isMuted = !isMuted
            app.post {
                app.sound.vol["bedtv"] = if (isMuted) 0.0 else 1.0
                app.sound.updateLayered()
            }
            app.toast(if (isMuted) "🔇 TV Sound Muted" else "🔊 TV Sound Restored")
        }
        rowPower.addView(pwrBtn)
        rowPower.addView(muteBtn)
        container.addView(rowPower)

        // 3. Channel Stepper Row: [CH ▲] and [CH ▼]
        val rowChStep = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        val chDown = remoteBtn("CH ▼", bgCol = "#1e293b", textCol = "#38bdf8", borderCol = "#0284c7") {
            app.post {
                app.tvPrevChannel()
                app.toast("📺 ${Tv.TV_CHANNEL_NAMES[app.tv.bedroomTvChannel]}")
            }
        }
        val chUp = remoteBtn("CH ▲", bgCol = "#1e293b", textCol = "#38bdf8", borderCol = "#0284c7") {
            app.post {
                app.tvNextChannel()
                app.toast("📺 ${Tv.TV_CHANNEL_NAMES[app.tv.bedroomTvChannel]}")
            }
        }
        rowChStep.addView(chDown)
        rowChStep.addView(chUp)
        container.addView(rowChStep)

        // 4. Quick Channel Jump Row 1: Weather (12) & Anime (04)
        val rowDirect1 = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        val btnWthr = remoteBtn("CH 12 WTHR", bgCol = "#0c4a6e", textCol = "#bae6fd", borderCol = "#0284c7") {
            app.post {
                app.tvSetChannel(1)
                app.toast("📺 CH 12: The Weather Channel")
            }
        }
        val btnAnime = remoteBtn("CH 04 ANIME", bgCol = "#4c0519", textCol = "#fecdd3", borderCol = "#f43f5e") {
            app.post {
                app.tvSetChannel(0)
                app.toast("📺 CH 04: Late-Night 90s Anime")
            }
        }
        rowDirect1.addView(btnWthr)
        rowDirect1.addView(btnAnime)
        container.addView(rowDirect1)

        // 5. Quick Channel Jump Row 2: VCR (03) & PlayStation (AV1)
        val rowDirect2 = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        val btnVcr = remoteBtn("CH 03 VCR", bgCol = "#064e3b", textCol = "#a7f3d0", borderCol = "#10b981") {
            app.post {
                app.tvSetChannel(2)
                app.toast("📺 CH 03: VCR Deck")
            }
        }
        val btnPs1 = remoteBtn("AV 1 PS1", bgCol = "#78350f", textCol = "#fde68a", borderCol = "#f59e0b") {
            app.post {
                app.tvSetChannel(Tv.PS1_TV_CHANNEL)
                app.toast("📺 AV 1: PlayStation Video")
            }
        }
        rowDirect2.addView(btnVcr)
        rowDirect2.addView(btnPs1)
        container.addView(rowDirect2)

        // 6. Weather Channel View Mode Toggle (Simple ↔ Detailed 90s)
        val viewModeBtn = TextView(act).apply {
            text = "VIEW: ${app.settings.weatherChannelView.uppercase()}"
            setTextColor(Color.parseColor("#facc15"))
            typeface = MONO
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#172554"))
                cornerRadius = dp(6f)
                setStroke(dpi(1.2f), Color.parseColor("#3b82f6"))
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpi(30f)).apply {
                setMargins(dpi(2f), dpi(3f), dpi(2f), dpi(2f))
            }
            setOnClickListener {
                app.sound.sfx("ps1Button")
                app.post {
                    val next = app.toggleWeatherView()
                    act.runOnUiThread { text = "VIEW: ${next.uppercase()}" }
                }
            }
        }
        container.addView(viewModeBtn)

        // 7. Action Row: Zoom to TV screen & YouTube
        val rowActions = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        val zoomBtn = remoteBtn("🔍 ZOOM TV", bgCol = "#1e1b4b", textCol = "#c7d2fe", borderCol = "#818cf8") {
            app.post { app.toggleTvZoom() }
        }
        val ytBtn = remoteBtn("▶ YOUTUBE", bgCol = "#7f1d1d", textCol = "#ffffff", borderCol = "#dc2626") {
            app.post {
                app.openYouTube()
            }
        }
        rowActions.addView(zoomBtn)
        rowActions.addView(ytBtn)
        container.addView(rowActions)
    }

    fun open() {
        buildRemoteUi()
        container.visibility = View.VISIBLE
    }

    fun close() {
        container.visibility = View.GONE
    }

    fun toggle() {
        if (isOpen) close() else open()
    }
}
