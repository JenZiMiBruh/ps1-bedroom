package com.ps1bedroom.room

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.ps1bedroom.room.engine.PointLight
import org.json.JSONObject
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** HTML 17178-17340: little goals, unlocked as you live in the room. */
class Achievements(private val app: App, ctx: Context) {
    companion object {
        val LIST = linkedMapOf(
            "firstNight" to arrayOf("🌙", "First night", "Open the room after dark"),
            "sunrise" to arrayOf("🌅", "Early bird", "Watch the sun come up"),
            "storm" to arrayOf("⛈️", "Storm watcher", "Sit through a thunderstorm"),
            "blizzard" to arrayOf("❄️", "Snowed in", "Survive a blizzard"),
            "petJasper" to arrayOf("🐕", "Good boy", "Pet Jasper"),
            "petJasper10" to arrayOf("🦴", "Best friends", "Pet Jasper 10 times"),
            "traveler" to arrayOf("🧳", "World traveler", "Visit 8 places outside the window"),
            "allPlaces" to arrayOf("🗺️", "Seen it all", "Visit all 15 places"),
            "gamer" to arrayOf("🎮", "Player one", "Put a game in the PlayStation"),
            "dj" to arrayOf("📻", "DJ", "Play your own music on the boombox"),
            "tuber" to arrayOf("📺", "Couch potato", "Watch YouTube on the TV"),
            "sleeper" to arrayOf("😴", "Lights out", "Fall asleep with sleep mode"),
            "alarm" to arrayOf("⏰", "Rise and shine", "Wake up to the sunrise alarm"),
            "photographer" to arrayOf("📷", "Photographer", "Take a photo"),
            "sharer" to arrayOf("💌", "Postcard", "Make a share card"),
            "surprise" to arrayOf("🎁", "Surprise!", "Find a daily surprise"),
            "surprise7" to arrayOf("🎉", "Regular", "Find surprises on 7 different days"),
            "minesweeper" to arrayOf("💣", "Sweeper", "Win a game of Minesweeper")
        )
        fun hash(id: String): Int { var h = 7L; for (ch in id) h = (h * 31 + ch.code) and 0xffffffffL; return h.toInt() }
    }
    private val prefs = ctx.getSharedPreferences("ps1bedroom", Context.MODE_PRIVATE)
    private val unlocked: JSONObject = try { JSONObject(prefs.getString("achievements", "{}")!!) } catch (e: Exception) { JSONObject() }
    fun has(id: String) = synchronized(unlocked) { unlocked.has(id) }
    val count get() = synchronized(unlocked) { LIST.keys.count { unlocked.has(it) } }

    /** Any thread. Nothing unlocks behind the title screen; the checks run again once the room is up. */
    fun achieve(id: String) {
        if (!LIST.containsKey(id) || !app.isBuilt || app.hud?.overlays?.bootShowing == true) return
        synchronized(unlocked) {
            if (unlocked.has(id)) return
            unlocked.put(id, System.currentTimeMillis())
            prefs.edit().putString("achievements", unlocked.toString()).apply()
        }
        val h = hash(id)
        app.hud?.later(600) { app.hud?.achPop?.show(LIST.getValue(id), h) }
        app.post { app.sound.achievementJingle(h) }
        app.hud?.refreshMenu()
    }

    // ---- The hooks (petDog, setOutsideEnv, insertHeldGame, the 5-second checks) ----
    fun petted() { val n = prefs.getInt("pets", 0) + 1; prefs.edit().putInt("pets", n).apply(); achieve("petJasper"); if (n >= 10) achieve("petJasper10") }
    private var lastPlace = ""
    private fun placeSeen(k: String) {
        if (k == lastPlace) return
        lastPlace = k
        val seen = try { JSONObject(prefs.getString("placesSeen", "{}")!!) } catch (e: Exception) { JSONObject() }
        if (!seen.has(k)) { seen.put(k, 1); prefs.edit().putString("placesSeen", seen.toString()).apply() }
        if (seen.length() >= 8) achieve("traveler")
        if (seen.length() >= Places.OUTSIDE_ENVS.size) achieve("allPlaces")
    }
    private var lastCheck = 0L
    /** GL thread, every frame (works every 5 s). */
    fun tick() {
        val now = System.currentTimeMillis()
        if (now - lastCheck < 5000) return
        lastCheck = now
        val env = app.env
        placeSeen(app.sky.outsideEnv)
        if (env.daylight < 0.2) achieve("firstNight")
        if (env.weatherKind == "storm" && app.sound.playing) achieve("storm")
        if (env.weatherKind == "blizzard") achieve("blizzard")
        if (env.sunDeg > -2 && env.sunDeg < 4 && env.morning) achieve("sunrise")
        if (app.myMusic.shouldPlay()) achieve("dj")
        if (app.ytActive) achieve("tuber")
    }
}

/** The trophy card that drops in with a shine sweep, pixel confetti, and its own colour and icon move. */
class AchievementPop(private val act: Activity, private val root: FrameLayout) {
    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, act.resources.displayMetrics)
    private fun dpi(v: Float) = dp(v).toInt()
    private val icon = TextView(act).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f); gravity = Gravity.CENTER }
    private val small = TextView(act).apply { text = "ACHIEVEMENT UNLOCKED"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f); letterSpacing = 0.3f; typeface = Typeface.MONOSPACE }
    private val name = TextView(act).apply { setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); letterSpacing = 0.07f }
    private val desc = TextView(act).apply { setTextColor(Color.rgb(170, 179, 230)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f); typeface = Typeface.MONOSPACE }
    private val shine = View(act)
    private val card = FrameLayout(act).apply { visibility = View.INVISIBLE; clipToOutline = true }
    private val iconBg = GradientDrawable().apply { gradientType = GradientDrawable.RADIAL_GRADIENT; cornerRadius = dp(10f) }
    private val cardBg = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.rgb(26, 31, 69), Color.rgb(12, 16, 48))).apply { cornerRadius = dp(14f) }
    init {
        val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dpi(12f), dpi(10f), dpi(18f), dpi(10f)) }
        icon.background = iconBg
        row.addView(icon, LinearLayout.LayoutParams(dpi(44f), dpi(44f)))
        val tx = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; addView(small); addView(name); addView(desc) }
        row.addView(tx, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dpi(12f) })
        card.addView(row)
        shine.background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0, Color.argb(90, 255, 255, 255), 0))
        card.addView(shine, FrameLayout.LayoutParams(dpi(90f), -1))
        card.background = cardBg
        card.minimumWidth = dpi(240f)
        card.elevation = dp(12f)
        root.addView(card, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dpi(14f) })
    }
    private var hideRun: Runnable? = null
    fun show(a: Array<String>, h: Int) {
        val hue = ((h.toLong() and 0xffffffffL) % 360).toFloat()
        val col = Color.HSVToColor(floatArrayOf(hue, 0.75f, 1f))
        val col2 = Color.HSVToColor(floatArrayOf((hue + 60) % 360, 0.7f, 1f))
        cardBg.setStroke(dpi(2f), col)
        iconBg.colors = intArrayOf(col, Color.TRANSPARENT); iconBg.gradientRadius = dp(26f)
        small.setTextColor(col)
        icon.text = a[0]; name.text = a[1]; desc.text = a[2]
        card.visibility = View.VISIBLE
        card.outlineSpotShadowColorCompat(col)
        // ach-drop: in, hold, out
        card.translationY = -dp(160f)
        card.animate().cancel()
        card.animate().translationY(0f).setDuration(420).setInterpolator(OvershootInterpolator(1.2f)).start()
        hideRun?.let { root.removeCallbacks(it) }
        hideRun = Runnable { card.animate().translationY(-dp(160f)).setDuration(500).setInterpolator(DecelerateInterpolator()).withEndAction { card.visibility = View.INVISIBLE }.start() }.also { root.postDelayed(it, 3700) }
        // shine sweep
        shine.translationX = -dp(140f)
        root.postDelayed({ shine.animate().translationX(card.width + dp(40f)).setDuration(1100).start() }, 500)
        // the icon's own move (spin / bounce / wobble / flip)
        icon.rotation = 0f; icon.scaleX = 1f; icon.scaleY = 1f; icon.translationY = 0f; icon.rotationY = 0f
        val move = ((h.toLong() and 0xffffffffL) ushr 2).toInt() % 4
        root.postDelayed({
            when (move) {
                0 -> { icon.rotation = -200f; icon.scaleX = 0.3f; icon.scaleY = 0.3f; icon.animate().rotation(0f).scaleX(1f).scaleY(1f).setDuration(900).setInterpolator(OvershootInterpolator(2f)).start() }
                1 -> { icon.translationY = -dp(30f); icon.animate().translationY(0f).setDuration(900).setInterpolator(android.view.animation.BounceInterpolator()).start() }
                2 -> ObjectAnimator.ofFloat(icon, "rotation", 0f, -18f, 14f, -9f, 5f, 0f).setDuration(900).start()
                else -> { icon.animate().rotationY(720f).setDuration(900).setInterpolator(DecelerateInterpolator()).start() }
            }
        }, 350)
        // pixel confetti from behind the icon
        root.postDelayed({ confetti(h, col, col2) }, 450)
    }
    private fun confetti(h: Int, col: Int, col2: Int) {
        val loc = IntArray(2); icon.getLocationInWindow(loc)
        val rl = IntArray(2); root.getLocationInWindow(rl)
        val cx = loc[0] - rl[0] + icon.width / 2f; val cy = loc[1] - rl[1] + icon.height / 2f
        val hu = h.toLong() and 0xffffffffL
        for (i in 0 until 18) {
            val px = View(act).apply { setBackgroundColor(if (i % 3 == 0) Color.WHITE else if (i % 3 == 1) col else col2); elevation = dp(13f) }
            root.addView(px, FrameLayout.LayoutParams(dpi(6f), dpi(6f)))
            px.x = cx; px.y = cy
            val ang = i / 18.0 * Math.PI * 2 + (hu % 7) * 0.1
            val dist = dp((50 + ((hu ushr (i % 13)) % 60)).toFloat())
            px.animate().translationXBy((cos(ang) * dist).toFloat()).translationYBy((sin(ang) * dist).toFloat() + dp(30f))
                .scaleX(0.4f).scaleY(0.4f).rotation(if (i % 2 == 1) 180f else -180f).alpha(0f).setDuration(1100).setInterpolator(DecelerateInterpolator())
                .withEndAction { root.removeView(px) }.start()
        }
    }
    private fun View.outlineSpotShadowColorCompat(c: Int) { if (android.os.Build.VERSION.SDK_INT >= 28) { outlineSpotShadowColor = c; outlineAmbientShadowColor = c } }
    @Suppress("unused") private val keep = ValueAnimator::class
}

/** HTML 17350-17385: the sunrise alarm. The room slowly brightens for 20 minutes before it, then music or birdsong. */
class Alarm(private val app: App, val context: Context) {
    private val prefs = context.getSharedPreferences("ps1bedroom", Context.MODE_PRIVATE)
    var on = prefs.getBoolean("alarm.on", false); private set
    var time = prefs.getString("alarm.time", "07:00")!!; private set
    var music = prefs.getBoolean("alarm.music", true); private set
    private var firedOn = ""
    val light = PointLight(0xffa04a, 0f, 9f, 1.4f).apply { position.set(0.8f, 2.6f, -2.4f) }
    fun set(on: Boolean = this.on, time: String = this.time, music: Boolean = this.music) {
        this.on = on; this.time = time; this.music = music
        prefs.edit().putBoolean("alarm.on", on).putString("alarm.time", time).putBoolean("alarm.music", music).apply()
        if (!on) light.intensity = 0f
    }
    private fun minutes() = time.split(":").let { (it.getOrNull(0)?.toIntOrNull() ?: 7) * 60 + (it.getOrNull(1)?.toIntOrNull() ?: 0) }
    private var last = 0L
    /** GL thread every frame (works once a second). */
    fun tick() {
        val ms = System.currentTimeMillis()
        if (ms - last < 1000) return
        last = ms
        if (!on) { light.intensity = 0f; return }
        val now = Calendar.getInstance()
        val mins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE) + now.get(Calendar.SECOND) / 60.0
        var until = minutes() - mins; if (until < -60) until += 1440
        val k = if (until > 20 || until < -30) 0.0 else min(1.0, (20 - until) / 20)
        light.intensity = (k * 2.2).toFloat()
        val today = "${now.get(Calendar.YEAR)}-${now.get(Calendar.DAY_OF_YEAR)}"
        if (until <= 0 && until > -1 && firedOn != today) {
            firedOn = today
            val s = app.settings
            s.wake(); app.hud?.showSleeping(false)
            if (!app.sound.playing) app.toggleAudio()
            if (app.room.blindsOpenState == 2) app.room.setBlindsState(0)
            if (music && app.myMusic.tracks.isNotEmpty()) app.myMusic.mediaControl("play") else app.sound.birdChorus(6)
            app.toast("⏰ GOOD MORNING! It's ${app.env.formatHour(now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE) / 60.0)}. Jasper is already wagging.")
            app.jasper.wag(app.lastFrameT, 8f)
            app.achievements.achieve("alarm")
        }
    }
}
