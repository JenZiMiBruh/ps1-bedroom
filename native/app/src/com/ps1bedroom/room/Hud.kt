package com.ps1bedroom.room

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.hypot

/**
 * The HTML's on-screen UI as native views, same colours and sizes as its CSS: the bottom dock, the "CHAIR →" pill,
 * the walk joystick, the crosshair, the toast that drops in from the top and the "PUT GAME BACK" pill.
 * (The top bar and the menu sheet come with the menus phase.) Runs on the UI thread; actions go to the GL thread.
 */
@SuppressLint("ClickableViewAccessibility", "SetTextI18n")
class Hud(private val act: Activity, private val root: FrameLayout, private val app: App) {
    // ---- CSS variables (from UiTheme) ----
    private val T = UiTheme
    private val PANEL get() = T.panel
    private val LINE get() = T.line
    private val MUTED get() = T.muted
    private val PINK get() = T.accent
    private val CYAN get() = T.accent2
    private val GOLD get() = T.gold
    private val ACTIVE_BG get() = T.activeBg
    private val ON_TEXT get() = T.activeText
    private val MONO = Typeface.create("monospace", Typeface.BOLD)
    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, act.resources.displayMetrics)
    private fun dpi(v: Float) = dp(v).toInt()

    /** A panel with the HTML's hard drop shadow (box-shadow: Npx Npx 0 shadow). */
    private fun panel(fill: Int, stroke: Int, radius: Float, shadow: Float): LayerDrawable {
        val sh = GradientDrawable().apply { setColor(T.shadow); cornerRadius = dp(radius) }
        val bg = GradientDrawable().apply { setColor(fill); cornerRadius = dp(radius); setStroke(dpi(2f), stroke) }
        return LayerDrawable(arrayOf(sh, bg)).apply {
            setLayerInset(0, dpi(shadow), dpi(shadow), 0, 0)
            setLayerInset(1, 0, 0, dpi(shadow), dpi(shadow))
        }
    }
    private fun pill(text: String, color: Int): TextView = TextView(act).apply {
        this.text = text; setTextColor(color); typeface = MONO; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); letterSpacing = 0.04f
        setPadding(dpi(11f), dpi(7f), dpi(14f), dpi(10f))
        background = panel(PANEL, LINE, 6f, 3f)
    }

    // ---- Dock ----
    private class DockBtn(val view: LinearLayout, val icon: TextView, val label: TextView)
    private val dockBtns = LinkedHashMap<String, DockBtn>()
    private val dock = LinearLayout(act)
    private val dockSep = View(act)
    private val pullChair = pill("CHAIR → DESK", CYAN)
    private val putBack = pill("↩ PUT GAME BACK", GOLD)
    private val crosshair = View(act)
    private val toastView = TextView(act)
    private val stick = FrameLayout(act)
    private val knob = View(act)

    init {
        // Bottom dock: Menu | Walk, Sit, Bed, Free | Sound, Sleep, Photo (all thumb-reachable at the bottom)
        dock.orientation = LinearLayout.HORIZONTAL
        dock.setPadding(dpi(6f), dpi(6f), dpi(10f), dpi(10f))
        dock.background = panel(PANEL, LINE, 14f, 4f)
        fun dockBtn(key: String, icon: String, label: String, onClick: () -> Unit) {
            val b = LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setPadding(dpi(6f), dpi(5f), dpi(6f), dpi(4f)); minimumWidth = dpi(50f)
                isFocusable = true
            }
            val i = TextView(act).apply { text = icon; setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f); gravity = Gravity.CENTER }
            val l = TextView(act).apply { text = label; setTextColor(MUTED); typeface = MONO; setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f); gravity = Gravity.CENTER }
            b.addView(i); b.addView(l)
            b.setOnClickListener { it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY); onClick() }
            dock.addView(b, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dpi(4f) })
            dockBtns[key] = DockBtn(b, i, l)
        }
        dockBtn("menu", "☰", "Menu") { if (menu.isOpen) menu.close() else menu.open(null); refreshDockStates() }
        dockBtn("walk", "🚶", "Walk") { app.post { app.setPlayerMode("walk") } }
        dockBtn("chair", "🪑", "Sit") { app.post { app.setPlayerMode("chair") } }
        dockBtn("bed", "🛏", "Bed") { app.post { app.setPlayerMode("bed") } }
        dockBtn("orbit", "🎥", "Free") { app.post { app.setPlayerMode("orbit") } }
        dock.addView(dockSep.apply { setBackgroundColor(LINE) }, LinearLayout.LayoutParams(dpi(2f), LinearLayout.LayoutParams.MATCH_PARENT).apply { setMargins(dpi(3f), dpi(4f), dpi(7f), dpi(4f)) })
        dockBtn("audio", "🔈", "Sound") { app.post { app.toggleAudio() } }
        dockBtn("timer", "☾", "Sleep") { menu.open("timer"); refreshDockStates() }
        dockBtn("photo", "📷", "Photo") { menu.close(); overlays.enterPhotoMode() }
        root.addView(dock, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dpi(10f) })
        root.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or_, ob -> if (r - l != or_ - ol || b - t != ob - ot) layoutForSize(r - l, b - t) }
        setActive("orbit")

        // Contextual pill (roll the chair) above the bottom status bar & dock
        pullChair.visibility = View.GONE
        pullChair.isFocusable = true
        pullChair.setOnClickListener { app.post { app.pullChair() } }
        root.addView(pullChair, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dpi(126f) })

        // Put the held game back
        putBack.visibility = View.GONE
        putBack.isFocusable = true
        putBack.setOnClickListener { app.post { app.putBackHeld() } }
        root.addView(putBack, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply { bottomMargin = dpi(126f); marginEnd = dpi(14f) })

        // Crosshair: a 10 px ring in the middle of the screen while you're in first person (or using a controller)
        crosshair.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setStroke(maxOf(1, dpi(1.5f)), Color.argb(215, 240, 171, 252)) }
        crosshair.visibility = View.GONE
        root.addView(crosshair, FrameLayout.LayoutParams(dpi(10f), dpi(10f), Gravity.CENTER))

        // Walk joystick (bottom-left)
        stick.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.argb(89, 20, 28, 70)); setStroke(dpi(2f), Color.argb(140, 160, 180, 255)) }
        knob.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.argb(140, 240, 171, 252)); setStroke(dpi(2f), Color.rgb(0xf0, 0xab, 0xfc)) }
        stick.addView(knob, FrameLayout.LayoutParams(dpi(50f), dpi(50f), Gravity.CENTER))
        stick.visibility = View.GONE
        stick.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val half = v.width / 2f
                    var dx = (e.x - half) / half; var dy = (e.y - half) / half
                    val len = hypot(dx, dy)
                    if (len > 1) { dx /= len; dy /= len }
                    knob.translationX = dx * half * 0.6f; knob.translationY = dy * half * 0.6f
                    val dead = hypot(dx, dy) < 0.15f
                    app.player.stickX = if (dead) 0f else dx
                    app.player.stickY = if (dead) 0f else -dy
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { app.player.stickX = 0f; app.player.stickY = 0f; knob.translationX = 0f; knob.translationY = 0f }
            }
            true
        }
        root.addView(stick, FrameLayout.LayoutParams(dpi(120f), dpi(120f), Gravity.BOTTOM or Gravity.START).apply { leftMargin = dpi(22f); bottomMargin = dpi(118f) })

        // Toast: rises in near the bottom (above dock & status bar, and elevated above the menu sheet)
        toastView.apply {
            setTextColor(T.toastText); typeface = MONO; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); gravity = Gravity.CENTER
            setPadding(dpi(14f), dpi(9f), dpi(18f), dpi(13f))
            background = panel(PANEL, T.toastLine, 8f, 4f)
            elevation = dp(32f)
            maxWidth = minOf(dpi(560f), (act.resources.displayMetrics.widthPixels * 0.92f).toInt())
            visibility = View.INVISIBLE
        }
        root.addView(toastView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dpi(134f) })
        T.listeners += { restyle() }
    }

    private fun restyle() {
        dock.background = panel(PANEL, LINE, 14f, 4f)
        dockSep.setBackgroundColor(LINE)
        pullChair.setTextColor(CYAN); pullChair.background = panel(PANEL, LINE, 6f, 3f)
        putBack.setTextColor(GOLD); putBack.background = panel(PANEL, LINE, 6f, 3f)
        toastView.setTextColor(T.toastText); toastView.background = panel(PANEL, T.toastLine, 8f, 4f)
        setActive(currentMode)
        refreshDockStates()
    }

    fun refreshDockStates() {
        dockBtns["menu"]?.let { b ->
            val on = menu.isOpen
            b.view.background = if (on) GradientDrawable().apply { setColor(ACTIVE_BG); cornerRadius = dp(10f); setStroke(dpi(2f), PINK) } else null
            b.label.setTextColor(if (on) ON_TEXT else PINK)
            b.icon.setTextColor(if (on) ON_TEXT else PINK)
        }
        dockBtns["audio"]?.let { b ->
            val on = app.isBuilt && app.sound.playing
            b.icon.text = if (on) "🔊" else "🔈"
            b.view.background = if (on) GradientDrawable().apply { setColor(ACTIVE_BG); cornerRadius = dp(10f); setStroke(dpi(2f), PINK) } else null
            b.label.setTextColor(if (on) ON_TEXT else MUTED)
        }
        dockBtns["timer"]?.let { b ->
            val on = app.isBuilt && app.settings.sleepEnd > 0
            b.view.background = if (on) GradientDrawable().apply { setColor(ACTIVE_BG); cornerRadius = dp(10f); setStroke(dpi(2f), PINK) } else if (currentMode == "timer") b.view.background else null
            b.label.setTextColor(if (on) ON_TEXT else MUTED)
        }
    }

    private fun setActive(mode: String) {
        for ((k, b) in dockBtns) {
            if (k == "menu" || k == "audio" || k == "timer") continue
            val on = k == mode
            b.view.background = if (on) GradientDrawable().apply { setColor(ACTIVE_BG); cornerRadius = dp(10f); setStroke(dpi(2f), PINK) } else null
            b.label.setTextColor(if (on) ON_TEXT else MUTED)
        }
    }

    private var controllerActive = false
    fun setControllerActive(active: Boolean) = act.runOnUiThread {
        if (controllerActive == active) return@runOnUiThread
        controllerActive = active
        if (!uiHidden) crosshair.visibility = if (currentMode != "orbit" || controllerActive) View.VISIBLE else View.GONE
    }

    fun updateStickVisual(x: Float, y: Float) = act.runOnUiThread {
        val half = (stick.width.takeIf { it > 0 } ?: dpi(120f)) / 2f
        knob.translationX = x * half * 0.6f
        knob.translationY = -y * half * 0.6f
    }

    /** updatePostureUI, from the GL thread. */
    fun postureChanged(mode: String, pullLabel: String) = act.runOnUiThread {
        currentMode = mode; menu.refreshStatus()
        setActive(mode)
        val firstPerson = mode != "orbit"
        crosshair.visibility = if ((firstPerson || controllerActive) && !uiHidden) View.VISIBLE else View.GONE
        stick.visibility = if (mode == "walk" && !uiHidden) View.VISIBLE else View.GONE
        pullChair.visibility = if (mode == "chair" && !uiHidden) View.VISIBLE else View.GONE
        pullChair.text = pullLabel
        if (mode != "walk") { app.player.stickX = 0f; app.player.stickY = 0f; knob.translationX = 0f; knob.translationY = 0f }
    }
    fun holdingGame(holding: Boolean) = act.runOnUiThread { putBack.visibility = if (holding && !uiHidden) View.VISIBLE else View.GONE }

    private val hideToast = Runnable { slideToast(false) }
    private var toastAnim: ValueAnimator? = null
    /** showToast: any thread. */
    fun toast(msg: String) = act.runOnUiThread {
        toastView.text = msg
        toastView.removeCallbacks(hideToast)
        slideToast(true)
        toastView.postDelayed(hideToast, 4200)
    }
    private fun slideToast(show: Boolean) {
        toastAnim?.cancel()
        toastView.bringToFront()
        val off = (toastView.height.takeIf { it > 0 } ?: dpi(60f)) + dp(110f)
        val from = toastView.translationY.takeIf { toastView.visibility == View.VISIBLE } ?: off
        toastView.visibility = View.VISIBLE
        toastAnim = ValueAnimator.ofFloat(from, if (show) 0f else off).apply {
            duration = 250
            interpolator = if (show) OvershootInterpolator(1.4f) else null
            addUpdateListener { toastView.translationY = it.animatedValue as Float; toastView.alpha = if (show) it.animatedFraction.coerceAtLeast(toastView.alpha) else 1 - it.animatedFraction }
            if (!show) addListener(object : android.animation.AnimatorListenerAdapter() { override fun onAnimationEnd(a: android.animation.Animator) { toastView.visibility = View.INVISIBLE } })
            start()
        }
    }

    private var safeInsets = android.graphics.Rect()
    fun applySafeInsets(insets: android.graphics.Rect) = act.runOnUiThread {
        safeInsets.set(insets)
        val w = root.width.takeIf { it > 0 } ?: act.resources.displayMetrics.widthPixels
        val h = root.height.takeIf { it > 0 } ?: act.resources.displayMetrics.heightPixels
        layoutForSize(w, h)
        menu.applySafeInsets(insets)
    }

    /** The HTML's @media rules: narrow phones stretch the dock edge to edge, short landscape screens drop the labels. */
    private fun layoutForSize(wPx: Int, hPx: Int) {
        val d = act.resources.displayMetrics.density
        val narrow = wPx / d <= 560f
        val short = hPx / d <= 460f
        val lp = dock.layoutParams as FrameLayout.LayoutParams
        lp.width = if (narrow) FrameLayout.LayoutParams.MATCH_PARENT else FrameLayout.LayoutParams.WRAP_CONTENT
        val baseMargin = if (narrow) dpi(8f) else 0
        lp.leftMargin = baseMargin + safeInsets.left
        lp.rightMargin = baseMargin + safeInsets.right
        lp.bottomMargin = dpi(if (short) 6f else 10f) + safeInsets.bottom
        dock.layoutParams = lp
        val pad = if (short) 4f else if (narrow) 5f else 6f
        dock.setPadding(dpi(if (narrow) 4f else pad), dpi(pad), dpi((if (narrow) 4f else pad) + 4f), dpi(pad + 4f))
        dockSep.visibility = if (narrow) View.GONE else View.VISIBLE
        for (b in dockBtns.values) {
            val blp = b.view.layoutParams as LinearLayout.LayoutParams
            if (narrow) { blp.width = 0; blp.weight = 1f; blp.marginEnd = dpi(2f) } else { blp.width = LinearLayout.LayoutParams.WRAP_CONTENT; blp.weight = 0f; blp.marginEnd = dpi(4f) }
            b.view.layoutParams = blp
            b.view.minimumWidth = if (short) dpi(44f) else if (narrow) 0 else dpi(50f)
            if (short) b.view.setPadding(dpi(4f), dpi(3f), dpi(4f), dpi(3f))
            else if (narrow) b.view.setPadding(dpi(1f), dpi(4f), dpi(1f), dpi(3f))
            else b.view.setPadding(dpi(6f), dpi(5f), dpi(6f), dpi(4f))
            b.label.visibility = if (short) View.GONE else View.VISIBLE
            b.label.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (narrow) 9f else 10f)
            b.icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (narrow) 17f else 19f)
        }
        val above = dpi(if (short) 92f else 124f) + safeInsets.bottom
        (stick.layoutParams as FrameLayout.LayoutParams).apply {
            bottomMargin = above
            leftMargin = dpi(22f) + safeInsets.left
        }
        (putBack.layoutParams as FrameLayout.LayoutParams).apply {
            bottomMargin = above
            marginEnd = dpi(14f) + safeInsets.right
        }
        (pullChair.layoutParams as FrameLayout.LayoutParams).bottomMargin = dpi(if (short) 90f else 122f) + safeInsets.bottom
        (toastView.layoutParams as FrameLayout.LayoutParams).bottomMargin = dpi(if (short) 98f else 134f) + safeInsets.bottom
        stick.requestLayout(); putBack.requestLayout(); pullChair.requestLayout(); toastView.requestLayout()
    }

    // ---- Top bar + menu sheet (Menu.kt), the sleep overlay ----
    val menu: Menu by lazy { Menu(act, root, app) }
    val overlays: Overlays by lazy { Overlays(act, root, app) }
    val pcDesktop: PcDesktop by lazy { PcDesktop(act, root, app) }
    val yt: YouTubeTv by lazy { YouTubeTv(act, root, app) }
    val achPop: AchievementPop by lazy { AchievementPop(act, root) }
    fun runUi(fn: () -> Unit) = act.runOnUiThread(fn)
    fun openPc() = act.runOnUiThread { menu.close(); pcDesktop.show() }
    private var uiHidden = false
    /** Photo mode: everything but the photo bar goes away. */
    fun setUiHidden(h: Boolean) {
        uiHidden = h
        val v = if (h) View.GONE else View.VISIBLE
        dock.visibility = v; menu.setBarsVisible(!h); toastView.visibility = if (h) View.GONE else View.INVISIBLE
        if (h) { crosshair.visibility = View.GONE; stick.visibility = View.GONE; pullChair.visibility = View.GONE; putBack.visibility = View.GONE }
        else postureChanged(currentMode, app.player.pullChairLabel())
    }
    private val sleepOverlay = TextView(act).apply {
        text = "SLEEPING · TAP TO WAKE"; setTextColor(Color.argb(90, 214, 221, 255)); typeface = MONO; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        gravity = Gravity.CENTER; setBackgroundColor(Color.BLACK); visibility = View.GONE
        setOnClickListener { visibility = View.GONE; app.post { app.settings.wake() } }
    }
    fun attachMenu() {
        menu.refreshStatus()
        root.addView(sleepOverlay, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        overlays.showBoot(app.settings.peek("startScreen", true))
    }
    fun refreshMenu() = act.runOnUiThread { menu.build(); refreshDockStates() }
    fun refreshStatus() = act.runOnUiThread { menu.refreshStatus() }
    /** The dock's Sound button mirrors the audio state (🔊 / 🔈). */
    fun audioChanged(on: Boolean) = act.runOnUiThread { dockBtns["audio"]?.let { b -> b.icon.text = if (on) "🔊" else "🔈"; b.view.background = if (on) GradientDrawable().apply { setColor(ACTIVE_BG); cornerRadius = dp(10f); setStroke(dpi(2f), PINK) } else null; b.label.setTextColor(if (on) ON_TEXT else MUTED) } }
    /** setTimeout on the UI thread. */
    fun later(ms: Long, fn: () -> Unit) { root.postDelayed(fn, ms) }
    fun showSleeping(on: Boolean) = act.runOnUiThread { sleepOverlay.visibility = if (on) View.VISIBLE else View.GONE }
    private var currentMode = "orbit"
    /** Android back: close the menu, then stand up / leave first person. */
    /** The boombox player (tap the boombox); made on the UI thread the first time it opens. */
    @Volatile private var boombox: BoomboxPanel? = null
    val isBoomboxOpen: Boolean get() = boombox?.isOpen == true
    val isSleeping: Boolean get() = sleepOverlay.visibility == View.VISIBLE
    fun wakeFromSleep() = act.runOnUiThread { sleepOverlay.visibility = View.GONE; app.post { app.settings.wake() } }
    fun openBoombox() = act.runOnUiThread { (boombox ?: BoomboxPanel(act, root, app).also { boombox = it }).open() }
    fun refreshBoombox() { boombox?.refresh() }
    private val POSTURE_ORDER = listOf("walk", "chair", "bed", "orbit")
    fun cyclePosture(dir: Int) {
        val idx = POSTURE_ORDER.indexOf(currentMode).coerceAtLeast(0)
        val next = POSTURE_ORDER[(idx + dir + POSTURE_ORDER.size) % POSTURE_ORDER.size]
        app.post { app.setPlayerMode(next) }
    }
    fun back(): Boolean {
        boombox?.let { if (it.isOpen) { it.close(); return true } }
        if (pcDesktop.isOpen) { pcDesktop.close(); return true }
        if (app.ytActive || yt.dialogOpen) { if (yt.back()) return true }
        if (overlays.back()) return true
        if (menu.isOpen) { menu.back(); refreshDockStates(); return true }
        if (currentMode != "orbit") { app.post { app.setPlayerMode("orbit") }; return true }
        return false
    }
}
