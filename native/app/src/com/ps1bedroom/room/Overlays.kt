package com.ps1bedroom.room

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Environment as AEnv
import android.provider.MediaStore
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * HTML 16016-16061 and 16344-16420: photo mode (shutter, hide the bar, saved to Pictures / PS1 Bedroom), the
 * five-card how-to tutorial, and the PS1-style start screen (PRESS START → white diamond → black licence screen).
 */
@SuppressLint("SetTextI18n")
class Overlays(private val act: Activity, private val root: FrameLayout, private val app: App) {
    private val PANEL = Color.argb((0.94 * 255).toInt(), 9, 11, 28)
    private val LINE = Color.rgb(0x3a, 0x4b, 0x8c)
    private val TEXT = Color.rgb(0xd6, 0xdd, 0xff)
    private val MUTED = Color.rgb(0x8f, 0x9c, 0xd0)
    private val PINK = Color.rgb(0xff, 0x77, 0xee)
    private val GOLD = Color.rgb(0xff, 0xd2, 0x3f)
    private val MONO = Typeface.create("monospace", Typeface.BOLD)
    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, act.resources.displayMetrics)
    private fun dpi(v: Float) = dp(v).toInt()
    private fun text(t: String, color: Int, size: Float, spacing: Float = 0f) = TextView(act).apply {
        text = t; setTextColor(color); typeface = MONO; setTextSize(TypedValue.COMPLEX_UNIT_SP, size); letterSpacing = spacing; gravity = Gravity.CENTER
    }
    private fun panel(fill: Int, stroke: Int, radius: Float, shadow: Float): LayerDrawable {
        val sh = GradientDrawable().apply { setColor(Color.BLACK); cornerRadius = dp(radius) }
        val bg = GradientDrawable().apply { setColor(fill); cornerRadius = dp(radius); setStroke(dpi(2f), stroke) }
        return LayerDrawable(arrayOf(sh, bg)).apply { setLayerInset(0, dpi(shadow), dpi(shadow), 0, 0); setLayerInset(1, 0, 0, dpi(shadow), dpi(shadow)) }
    }
    private fun pill(t: String, color: Int) = text(t, color, 12f, 0.04f).apply { setPadding(dpi(11f), dpi(7f), dpi(14f), dpi(10f)); background = panel(PANEL, LINE, 6f, 3f) }
    /** A fresh full-size layout each time: one shared params object across several views moved them all together. */
    private val MATCH get() = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

    // ================= Photo mode =================
    private val photoBar = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; visibility = View.GONE }
    private val flash = View(act).apply { setBackgroundColor(Color.WHITE); alpha = 0f; visibility = View.GONE }
    var photoMode = false; private set
    private var barHidden = false
    init {
        val done = pill("✕ DONE", TEXT).apply { setOnClickListener { exitPhotoMode() } }
        val shutter = View(act).apply {
            background = LayerDrawable(arrayOf(GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.BLACK) },
                GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.argb(64, 255, 255, 255)); setStroke(dpi(4f), Color.WHITE) })).apply {
                setLayerInset(0, dpi(3f), dpi(3f), 0, 0); setLayerInset(1, 0, 0, dpi(3f), dpi(3f))
            }
            setOnClickListener { takePhoto() }
        }
        val hide = pill("HIDE", TEXT).apply { setOnClickListener { photoBar.alpha = 0f; barHidden = true } }
        photoBar.addView(done)
        photoBar.addView(shutter, LinearLayout.LayoutParams(dpi(67f), dpi(67f)).apply { marginStart = dpi(10f); marginEnd = dpi(10f) })
        photoBar.addView(hide)
        photoBar.addView(pill("💌 CARD", TEXT).apply { setOnClickListener { makeShareCard() } }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dpi(8f) })
        root.addView(flash, MATCH)
        root.addView(photoBar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dpi(16f) })
    }
    /** Any tap on the screen brings a hidden photo bar back. */
    fun onScreenTouched() { if (barHidden) { barHidden = false; photoBar.alpha = 1f } }
    fun enterPhotoMode() {
        photoMode = true
        app.hud?.setUiHidden(true)
        photoBar.visibility = View.VISIBLE; photoBar.alpha = 1f
    }
    fun exitPhotoMode() {
        photoMode = false
        photoBar.visibility = View.GONE
        app.hud?.setUiHidden(false)
    }
    /** makeShareCard: a postcard of your room (place, time, weather) saved to Pictures. */
    private fun makeShareCard() {
        app.sfx("recStop")
        flash.visibility = View.VISIBLE; flash.alpha = 0.85f
        flash.animate().alpha(0f).setDuration(400).withEndAction { flash.visibility = View.GONE }.start()
        app.requestPhoto { shot ->
            // (GL thread) the caption, then the drawing on a worker
            val e = Places.OUTSIDE_ENVS[app.sky.outsideEnv]
            val s = app.settings
            val line = "${e?.icon ?: ""} ${e?.name ?: ""} · ${app.env.formatHour(app.env.hour)} · ${app.env.W.label} · ${s.formatTemp(s.currentTempC())}"
            Thread {
                val W = 1080; val H = 1350
                val card = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
                val c = android.graphics.Canvas(card)
                c.drawColor(android.graphics.Color.rgb(11, 10, 26))
                val sc = maxOf(W.toFloat() / shot.width, 1000f / shot.height); val w = shot.width * sc; val h = shot.height * sc
                c.save(); c.clipRect(40f, 40f, W - 40f, 1040f)
                c.drawBitmap(shot, null, android.graphics.RectF((W - w) / 2, 40 + (1000 - h) / 2, (W + w) / 2, 40 + (1000 + h) / 2), android.graphics.Paint().apply { isFilterBitmap = false })
                c.restore()
                val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                p.style = android.graphics.Paint.Style.STROKE; p.strokeWidth = 6f; p.color = android.graphics.Color.rgb(255, 62, 165); c.drawRect(40f, 40f, W - 40f, 1040f, p)
                p.style = android.graphics.Paint.Style.FILL
                p.color = android.graphics.Color.rgb(255, 210, 125); p.textSize = 74f; p.typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD); c.drawText("PS1 BEDROOM", 40f, 1140f, p)
                p.color = android.graphics.Color.rgb(232, 228, 255); p.textSize = 36f; p.typeface = android.graphics.Typeface.DEFAULT; c.drawText(line, 40f, 1205f, p)
                p.color = android.graphics.Color.rgb(185, 180, 230); p.textSize = 28f; c.drawText("A cozy 2001 bedroom that lives on your real clock and weather", 40f, 1255f, p)
                p.color = android.graphics.Color.rgb(255, 62, 165); p.textSize = 28f; p.typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD); c.drawText("galloade.itch.io/ps1-bedroom", 40f, 1305f, p)
                val ok = save(card, "ps1-bedroom-card")
                app.toast(if (ok) "💌 Share card saved to Pictures / PS1 Bedroom" else "💌 Could not save the card")
                if (ok) app.achievements.achieve("sharer")
            }.start()
        }
    }
    private fun takePhoto() {
        flash.visibility = View.VISIBLE; flash.alpha = 0.85f
        flash.animate().alpha(0f).setDuration(400).withEndAction { flash.visibility = View.GONE }.start()
        app.achievements.achieve("photographer")
        app.requestPhoto { bmp -> Thread { val ok = save(bmp); app.toast(if (ok) "📷 Saved to Pictures / PS1 Bedroom" else "📷 Could not save the photo") }.start() }
    }
    private fun save(bmp: Bitmap, prefix: String = "ps1-bedroom"): Boolean = try {
        val name = "$prefix-${SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date())}.png"
        if (Build.VERSION.SDK_INT >= 29) {
            val cv = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name); put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, AEnv.DIRECTORY_PICTURES + "/PS1 Bedroom")
            }
            val uri = act.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv) ?: throw IllegalStateException("no uri")
            act.contentResolver.openOutputStream(uri)!!.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } else {
            val dir = File(act.getExternalFilesDir(AEnv.DIRECTORY_PICTURES), "PS1 Bedroom").apply { mkdirs() }
            FileOutputStream(File(dir, name)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        true
    } catch (e: Exception) { false } finally { bmp.recycle() }

    // ================= Tutorial =================
    private val TUTORIAL = listOf(
        Triple("🌙", "WELCOME HOME", "This is your room. It follows the real clock: the sun and moon move across the sky, and the city outside wakes up and goes to sleep with you."),
        Triple("👆", "LOOK AROUND", "Drag to look around and pinch to zoom. Tap WALK to get a joystick, or SIT and BED to get comfy."),
        Triple("🎮", "TOUCH EVERYTHING", "Tap the TV, the PlayStation and its buttons, the lamp, the blinds, the door, the boombox and the light switch. Grab any game off the shelves and put it in the PS1."),
        Triple("🌦", "MAKE IT YOURS", "Open MENU to pick the time, the season, 15 kinds of weather (or your city's live weather), different skies and looks."),
        Triple("⏾", "DRIFT OFF", "Set a sleep timer and let the room play in the background. Everything fades out gently when it ends.")
    )
    private var tutStep = 0
    private val tutOverlay = FrameLayout(act).apply { setBackgroundColor(Color.argb(184, 2, 3, 10)); visibility = View.GONE; isClickable = true }
    private val tutIcon = text("", TEXT, 40f)
    private val tutTitle = text("", PINK, 15f, 0.07f)
    private val tutText = TextView(act).apply { setTextColor(TEXT); typeface = Typeface.create("monospace", Typeface.NORMAL); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); gravity = Gravity.CENTER; setLineSpacing(0f, 1.4f); minHeight = dpi(54f) }
    private val tutDots = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
    private val tutNext = pill("NEXT →", PINK)
    init {
        val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; background = panel(PANEL, LINE, 10f, 4f); setPadding(dpi(14f), dpi(12f), dpi(18f), dpi(16f)) }
        box.addView(tutIcon, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(8f); bottomMargin = dpi(8f) })
        box.addView(tutTitle, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dpi(6f) })
        box.addView(tutText)
        box.addView(tutDots, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(12f); bottomMargin = dpi(8f) })
        val actions = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(pill("SKIP", TEXT).apply { setOnClickListener { endTutorial() } })
        actions.addView(View(act), LinearLayout.LayoutParams(0, 1, 1f))
        tutNext.setOnClickListener { if (tutStep < TUTORIAL.size - 1) { tutStep++; renderTutorial() } else endTutorial() }
        actions.addView(tutNext)
        box.addView(actions)
        tutOverlay.addView(box, FrameLayout.LayoutParams(minOf(dpi(420f), (act.resources.displayMetrics.widthPixels - dpi(28f))), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        root.addView(tutOverlay, MATCH)
    }
    val tutorialOpen get() = tutOverlay.visibility == View.VISIBLE
    fun startTutorial() { tutStep = 0; tutOverlay.visibility = View.VISIBLE; renderTutorial() }
    private fun renderTutorial() {
        val (icon, title, body) = TUTORIAL[tutStep]
        tutIcon.text = icon; tutTitle.text = title; tutText.text = body
        tutDots.removeAllViews()
        for (i in TUTORIAL.indices) tutDots.addView(View(act).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (i == tutStep) PINK else LINE) } },
            LinearLayout.LayoutParams(dpi(8f), dpi(8f)).apply { marginStart = dpi(3f); marginEnd = dpi(3f) })
        tutNext.text = if (tutStep == TUTORIAL.size - 1) "LET'S GO" else "NEXT →"
    }
    fun endTutorial() { tutOverlay.visibility = View.GONE; app.post { app.settings.tutorialSeen = true; app.settings.saveNow() } }

    // ================= Start screen =================
    private val boot = FrameLayout(act).apply { setBackgroundColor(Color.BLACK); isClickable = true }
    private val press = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
    private val white = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setBackgroundColor(Color.WHITE); alpha = 0f; visibility = View.GONE }
    private val black = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; alpha = 0f; visibility = View.GONE }
    private var bootStage = 0
    private val bootTimers = ArrayList<Runnable>()
    val bootShowing get() = boot.parent != null && bootStage < 3
    init {
        press.addView(text("PS1 BEDROOM", Color.WHITE, 26f, 0.23f).apply { setShadowLayer(0.01f, dp(3f), dp(3f), Color.rgb(0x3b, 0x1a, 0x7a)) })
        press.addView(text("AMBIENCE ENGINE", MUTED, 11f, 0.27f), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(8f) })
        val start = text("PRESS START", GOLD, 15f, 0.27f)
        // blink 1.1 s steps(2)
        ValueAnimator.ofInt(0, 2).apply { duration = 1100; repeatCount = ValueAnimator.INFINITE; addUpdateListener { start.alpha = if ((it.animatedValue as Int) < 1) 1f else 0f }; start() }
        press.addView(start, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(38f) })
        press.addView(text("TAP ANYWHERE", Color.rgb(0x55, 0x5f, 0x8f), 10f, 0.2f), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(10f) })
        press.addView(text("A GAME BY DAYMION GALLO · GALLOADE", Color.rgb(0x55, 0x5f, 0x8f), 10f, 0.2f).apply { alpha = 0.6f }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(28f) })
        // White screen: the four-colour diamond grows in, then BEDROOM / COMPUTER ENTERTAINMENT
        val diamond = FrameLayout(act).apply { rotation = 45f; scaleX = 0.2f; scaleY = 0.2f; alpha = 0f; tag = "diamond" }
        val half = dpi(46f)
        for ((i, c) in listOf(0xef4444, 0xfacc15, 0x22c55e, 0x3b82f6).withIndex()) diamond.addView(View(act).apply { setBackgroundColor(Color.rgb(c shr 16 and 255, c shr 8 and 255, c and 255)) },
            FrameLayout.LayoutParams(half, half).apply { leftMargin = (i % 2) * half; topMargin = (i / 2) * half })
        white.addView(diamond, LinearLayout.LayoutParams(half * 2, half * 2))
        val word = text("BEDROOM", Color.rgb(0x1f, 0x25, 0x40), 15f, 0.53f).apply { alpha = 0f; tag = "word" }
        val word2 = text("COMPUTER ENTERTAINMENT", Color.rgb(0x6b, 0x72, 0x80), 10f, 0.4f).apply { alpha = 0f; tag = "word2" }
        white.addView(word, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(46f) })
        white.addView(word2, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(8f) })
        black.addView(text("PS1 BEDROOM", Color.rgb(0xe5, 0xe7, 0xeb), 34f, 0.12f).apply { setShadowLayer(dp(14f), 0f, 0f, Color.argb(89, 127, 243, 255)) })
        black.addView(text("LICENSED FOR LATE NIGHTS\nAND RAINY DAYS\n\nBY DAYMION GALLO", Color.rgb(0x6b, 0x72, 0x80), 10f, 0.3f).apply { setLineSpacing(0f, 1.8f) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(18f) })
        boot.addView(press, MATCH); boot.addView(white, MATCH); boot.addView(black, MATCH)
        boot.setOnClickListener { startFromBoot() }
    }
    /** Shown on launch unless it's turned off in Display. */
    fun showBoot(enabled: Boolean) {
        if (!enabled) { bootStage = 3; app.post { app.startAudioIfWanted() }; if (!app.settings.peek("tutorialSeen", false)) startTutorial(); return }
        root.addView(boot, MATCH)
    }
    private fun later(ms: Long, fn: () -> Unit) { val r = Runnable(fn); bootTimers += r; boot.postDelayed(r, ms) }
    private fun startFromBoot() {
        if (bootStage == 1 || bootStage == 2) { finishBoot(); return }
        if (bootStage != 0) return
        app.post { app.startAudioIfWanted() }
        bootStage = 1
        press.animate().alpha(0f).setDuration(600).start()
        white.visibility = View.VISIBLE
        white.animate().alpha(1f).setDuration(600).start()
        white.findViewWithTag<View>("diamond").animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(1400).setInterpolator(DecelerateInterpolator(2f)).start()
        white.findViewWithTag<View>("word").animate().alpha(1f).setStartDelay(600).setDuration(1200).start()
        white.findViewWithTag<View>("word2").animate().alpha(1f).setStartDelay(1000).setDuration(1200).start()
        later(3400) {
            bootStage = 2
            white.animate().alpha(0f).setStartDelay(0).setDuration(600).start()
            black.visibility = View.VISIBLE
            black.animate().alpha(1f).setDuration(600).start()
        }
        later(5900) { finishBoot() }
    }
    private fun finishBoot() {
        for (r in bootTimers) boot.removeCallbacks(r)
        bootTimers.clear()
        bootStage = 3
        ObjectAnimator.ofFloat(boot, View.ALPHA, 1f, 0f).apply { duration = 800; start() }
        boot.postDelayed({ root.removeView(boot); if (!app.settings.peek("tutorialSeen", false) && !app.settings.tutorialSeen) startTutorial() }, 800)
    }

    /** Android back: tutorial, photo mode. */
    fun back(): Boolean {
        if (tutorialOpen) { endTutorial(); return true }
        if (photoMode) { exitPhotoMode(); return true }
        return false
    }
}
