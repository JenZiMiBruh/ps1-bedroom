package com.ps1bedroom.room

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.ps1bedroom.room.engine.Canvas2D
import org.json.JSONObject
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * TAMAGOTCHU (v0.59): the virtual pet on the dresser is alive. It hatches, grows up (baby, child, teen, then one of
 * three adults depending on how well you look after it), gets hungry and bored on the real clock, sleeps at night
 * (turn its light off), poops, gets sick, calls for attention (sometimes just to test you: scold it), and can die
 * if neglected. Everything keeps going while the app is closed and catches up when you come back.
 *
 * Tap it in the room to pick it up: an egg-shaped device with the classic eight icons and A / B / C buttons
 * (A picks an icon, B uses it, C cancels), or tap an icon straight away. Its 32 x 16 LCD also shows in the room.
 */
class Tamagotchu(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("ps1bedroom", Context.MODE_PRIVATE)
    private val rnd = Random.Default
    private val MIN = 60_000L; private val HOUR = 60 * MIN

    // ---- state ----
    var stage = 0; private set              // 0 egg · 1 baby · 2 child · 3 teen · 4 adult · 5 gone
    var form = 0; private set               // adult look (0 best care .. 2 rough)
    var born = 0L; private set
    private var stageAt = 0L
    var hunger = 4; private set             // 0..4 hearts
    var happy = 4; private set
    var discipline = 0; private set         // 0..100
    var weight = 5; private set
    var poops = 0; private set
    var sick = false; private set
    private var sickSince = 0L
    private var doses = 0
    var lightsOff = false; private set
    var mistakes = 0; private set
    private var lastTick = 0L
    private var hungerClock = 0L; private var happyClock = 0L; private var poopClock = 0L
    private var zeroSince = 0L
    private var needSince = 0L
    var fakeCall = false; private set
    private var nextFakeCall = 0L
    private var fakeSince = 0L
    var generation = 1; private set
    var name = "TAMA"; private set

    init { load() }

    // ---------------------------------------------------------------- persistence
    private fun load() {
        val raw = prefs.getString("tamagotchu", null)
        if (raw == null) { newEgg(System.currentTimeMillis()); return }
        try {
            val j = JSONObject(raw)
            stage = j.optInt("stage"); form = j.optInt("form"); born = j.optLong("born"); stageAt = j.optLong("stageAt")
            hunger = j.optInt("hunger", 4); happy = j.optInt("happy", 4); discipline = j.optInt("disc"); weight = j.optInt("weight", 5)
            poops = j.optInt("poops"); sick = j.optBoolean("sick"); sickSince = j.optLong("sickSince"); doses = j.optInt("doses")
            lightsOff = j.optBoolean("lightsOff"); mistakes = j.optInt("mistakes"); lastTick = j.optLong("lastTick")
            hungerClock = j.optLong("hClock"); happyClock = j.optLong("pClock"); poopClock = j.optLong("poopClock")
            zeroSince = j.optLong("zeroSince"); needSince = j.optLong("needSince"); fakeCall = j.optBoolean("fake"); nextFakeCall = j.optLong("nextFake")
            generation = j.optInt("gen", 1); name = j.optString("name", "TAMA")
        } catch (e: Exception) { newEgg(System.currentTimeMillis()) }
    }
    private fun save() {
        val j = JSONObject()
        j.put("stage", stage); j.put("form", form); j.put("born", born); j.put("stageAt", stageAt)
        j.put("hunger", hunger); j.put("happy", happy); j.put("disc", discipline); j.put("weight", weight)
        j.put("poops", poops); j.put("sick", sick); j.put("sickSince", sickSince); j.put("doses", doses)
        j.put("lightsOff", lightsOff); j.put("mistakes", mistakes); j.put("lastTick", lastTick)
        j.put("hClock", hungerClock); j.put("pClock", happyClock); j.put("poopClock", poopClock)
        j.put("zeroSince", zeroSince); j.put("needSince", needSince); j.put("fake", fakeCall); j.put("nextFake", nextFakeCall)
        j.put("gen", generation); j.put("name", name)
        prefs.edit().putString("tamagotchu", j.toString()).apply()
    }
    @Synchronized fun newEgg(now: Long) {
        val names = listOf("TAMA", "MAMETCHI", "KUCHI", "MIMI", "POCHI", "NYORO", "GINJI", "KUTCHI")
        if (stage == 5) generation++
        name = names[rnd.nextInt(names.size)]
        stage = 0; form = 0; born = now; stageAt = now; hunger = 4; happy = 4; discipline = 0; weight = 5; poops = 0
        sick = false; doses = 0; lightsOff = false; mistakes = 0; lastTick = now
        hungerClock = now; happyClock = now; poopClock = now; zeroSince = 0L; needSince = 0L; fakeCall = false; nextFakeCall = now + 6 * HOUR
        save()
    }

    // ---------------------------------------------------------------- the clock
    private fun asleepAt(t: Long): Boolean {
        if (stage == 0 || stage == 5) return false
        val c = Calendar.getInstance().apply { timeInMillis = t }
        val h = c.get(Calendar.HOUR_OF_DAY)
        return if (stage == 1) (h >= 20 || h < 9) else (h >= 21 || h < 8)
    }
    val asleep get() = asleepAt(System.currentTimeMillis())
    /** Something it needs: hungry, bored, sick, dirty, or the light still on while it sleeps (or a test call). */
    val calling get() = stage in 1..4 && (hunger == 0 || happy == 0 || sick || poops >= 3 || (asleep && !lightsOff) || fakeCall)
    val ageDays get() = ((System.currentTimeMillis() - born) / (24 * HOUR)).toInt()

    /** Catches up minute by minute (up to three days) since the last tick. Returns true if it started calling. */
    @Synchronized fun tick(now: Long): Boolean {
        if (lastTick == 0L) lastTick = now
        val wasCalling = calling
        var t = lastTick
        val end = min(now, lastTick + 72 * HOUR)
        if (end - t < MIN) return false
        while (t + MIN <= end) { t += MIN; step(t) }
        lastTick = now
        save()
        return !wasCalling && calling
    }
    private fun step(t: Long) {
        when (stage) {
            0 -> { if (t - stageAt > 3 * MIN) { stage = 1; stageAt = t; hungerClock = t; happyClock = t; poopClock = t } ; return }
            5 -> return
        }
        val sleeping = asleepAt(t)
        if (!sleeping && lightsOff && stage >= 1) lightsOff = false   // morning: the light comes back on by itself
        if (!sleeping) {
            val hungerEvery = if (stage == 1) 30 * MIN else 70 * MIN
            val happyEvery = if (stage == 1) 30 * MIN else 60 * MIN
            val poopEvery = if (stage == 1) 90 * MIN else 3 * HOUR
            if (t - hungerClock >= hungerEvery) { hungerClock = t; hunger = max(0, hunger - 1) }
            if (t - happyClock >= happyEvery) { happyClock = t; happy = max(0, happy - 1) }
            if (t - poopClock >= poopEvery) { poopClock = t; poops = min(4, poops + 1) }
            if (!sick && ((poops >= 2 && t - poopClock > HOUR) || rnd.nextInt(6000) == 0)) { sick = true; sickSince = t; doses = 0 }
            if (!fakeCall && t > nextFakeCall && stage in 1..3) { fakeCall = true; fakeSince = t; nextFakeCall = t + (4 + rnd.nextInt(6)) * HOUR }
        } else {
            hungerClock = t; happyClock = t; poopClock = t   // nothing drops while it sleeps
        }
        // a need ignored for 15 minutes is a care mistake (counts once per need)
        val needs = hunger == 0 || happy == 0 || (sleeping && !lightsOff)
        if (needs) { if (needSince == 0L) needSince = t else if (needSince > 0L && t - needSince > 15 * MIN) { mistakes++; needSince = -1L } } else needSince = 0L
        if (fakeCall && t - fakeSince > 30 * MIN) fakeCall = false   // a test call you missed: it gives up after half an hour
        // neglect long enough and it leaves for good
        if (hunger == 0 && happy == 0) { if (zeroSince == 0L) zeroSince = t else if (t - zeroSince > 12 * HOUR) die(t) } else zeroSince = 0L
        if (sick && t - sickSince > 12 * HOUR) die(t)
        // growing up
        val age = t - stageAt
        when {
            stage == 1 && age > HOUR -> grow(t, 2)
            stage == 2 && age > 24 * HOUR -> grow(t, 3)
            stage == 3 && age > 72 * HOUR -> { form = when { mistakes <= 2 && discipline >= 50 -> 0; mistakes <= 5 -> 1; else -> 2 }; grow(t, 4) }
        }
    }
    private fun grow(t: Long, s: Int) { stage = s; stageAt = t }
    private fun die(t: Long) { stage = 5; stageAt = t; sick = false; poops = 0 }

    // ---------------------------------------------------------------- care (each returns a short message)
    @Synchronized fun feedMeal(): String {
        if (stage !in 1..4) return "..."
        if (asleep) return "ZZZ..."
        if (hunger >= 4) return "NO THANKS"
        hunger++; weight++; needSince = 0L; save(); return "YUM!"
    }
    @Synchronized fun feedSnack(): String {
        if (stage !in 1..4) return "..."
        if (asleep) return "ZZZ..."
        happy = min(4, happy + 1); weight += 2; save(); return "SWEET!"
    }
    @Synchronized fun playResult(wins: Int): String {
        if (wins >= 3) { happy = min(4, happy + 1); weight = max(1, weight - 1) }
        save(); return if (wins >= 3) "YOU WIN!" else "TRY AGAIN"
    }
    @Synchronized fun toggleLight(): String { lightsOff = !lightsOff; save(); return if (lightsOff) "LIGHT OFF" else "LIGHT ON" }
    @Synchronized fun medicine(): String {
        if (!sick) return "NOT SICK"
        doses++; if (doses >= 2) { sick = false; doses = 0; save(); return "ALL BETTER" }
        save(); return "ONE MORE"
    }
    @Synchronized fun clean(): String { if (poops == 0) return "ALL CLEAN"; poops = 0; save(); return "FLUSH!" }
    @Synchronized fun scold(): String {
        if (fakeCall) { fakeCall = false; discipline = min(100, discipline + 25); save(); return "GOOD!" }
        happy = max(0, happy - 1); save(); return "SAD..."
    }
    val canPlay get() = stage in 1..4 && !asleep && !sick

    // ---------------------------------------------------------------- pixel art (16 x 16, '#' = dark pixel)
    private val EGG = arrayOf(
        "................", "......####......", ".....#....#.....", "....#..##..#....", "...#..#..#..#...", "...#........#...", "..#..##..##..#..", "..#.#..##..#.#..",
        "..#..........#..", "..#..##..##..#..", "..#.#..##..#.#..", "...#........#...", "...#........#...", "....#......#....", ".....######.....", "................")
    private val BABY = arrayOf(
        "................", "................", "................", "................", "................", "......####......", ".....######.....", "....##.##.##....",
        "....########....", "....###..###....", "....########....", ".....######.....", "......####......", "................", "................", "................")
    private val CHILD = arrayOf(
        "................", "................", "......####......", ".....#....#.....", "....#.#..#.#....", "....#......#....", "....#..##..#....", ".....#....#.....",
        "......####......", ".....#.##.#.....", "....#.####.#....", "......####......", "......#..#......", ".....##..##.....", "................", "................")
    private val TEEN = arrayOf(
        "................", "....#......#....", "....##....##....", "....########....", "...#........#...", "...#.##..##.#...", "...#........#...", "...#...##...#...",
        "....#......#....", ".....######.....", "....#.####.#....", "...#..####..#...", "......####......", "......#..#......", ".....##..##.....", "................")
    private val ADULT = arrayOf(
        arrayOf(".......##.......", "......#..#......", ".......##.......", ".....######.....", "....#......#....", "...#.##..##.#...", "...#.##..##.#...", "...#........#...",
            "...#..####..#...", "....#......#....", ".....######.....", "...##.####.##...", "..#...####...#..", "......#..#......", ".....##..##.....", "................"),
        arrayOf("................", ".....#####......", "....#.....#.....", "...#..#....#....", "...#.......####.", "...#.......#....", "...#......#.....", "....#.....#.....",
            ".....#####......", "....#.....#.....", "...#.......#....", "...#.......#....", "....#.....#.....", ".....#...#......", "....##...##.....", "................"),
        arrayOf("................", "...#........#...", "...##......##...", "....########....", "...#........#...", "..#.##....##.#..", "..#..#....#..#..", "..#..........#..",
            "..#..######..#..", "..#..#....#..#..", "...#........#...", "....########....", "....#..##..#....", "...##..##..##...", "................", "................"))
    private val GHOST = arrayOf(
        ".....######.....", "....#......#....", ".....######.....", "................", ".....######.....", "....#......#....", "...#..#..#..#...", "...#........#...",
        "...#..####..#...", "...#........#...", "...#........#...", "...#.#.##.#.#...", "...##.#..#.##...", "................", "................", "................")
    private val POOP = arrayOf("...#....", "..##....", "..###...", ".#####..", ".######.", "########")
    private val SKULL = arrayOf("..####..", ".######.", "##.##.##", "########", ".######.", "..#..#..")
    private val RICE = arrayOf("...##...", "..####..", ".######.", "########", "#..##..#", "########")
    private val CAKE = arrayOf("...#....", "..###...", "########", "#.#.#.#.", "########", "########")
    private val HEART = arrayOf(".##..##.", "########", "########", ".######.", "..####..", "...##...")
    private val HEART_O = arrayOf(".##..##.", "#..##..#", "#......#", ".#....#.", "..#..#..", "...##...")
    private val ZZZ = arrayOf("####....", "..#.....", ".#......", "####.###", ".....#..", "....###.")

    private fun sprite(): Array<String> = when (stage) { 0 -> EGG; 1 -> BABY; 2 -> CHILD; 3 -> TEEN; 4 -> ADULT[form.coerceIn(0, 2)]; else -> GHOST }

    /** What the LCD shows right now: "main", or a screen the device panel is on (meter pages, food, game, message). */
    @Volatile var screen = "main"
    @Volatile var screenArg = 0
    @Volatile var message = ""
    @Volatile var messageUntil = 0L
    @Volatile var gameRound = 0; @Volatile var gameWins = 0; @Volatile var gameDir = 0; @Volatile var gameShowUntil = 0L

    /** Draws the 32 x 16 LCD into [put] (x, y, on) — shared by the room texture and the device panel. */
    @Synchronized fun drawLcd(now: Long, put: (Int, Int) -> Unit) {
        val frame = ((now / 500) % 2).toInt()
        fun blit(img: Array<String>, ox: Int, oy: Int, flip: Boolean = false) {
            for (y in img.indices) { val row = img[y]; for (x in row.indices) if (row[if (flip) row.length - 1 - x else x] == '#') { val px = ox + x; val py = oy + y; if (px in 0..31 && py in 0..15) put(px, py) } }
        }
        fun digits(s: String, ox: Int, oy: Int) {
            // 3 x 5 font, numbers and a few letters
            val F = mapOf('0' to "###.#.#.#.#.###", '1' to ".#.##..#..#.###", '2' to "###..#####..###", '3' to "###..###..#.###", '4' to "#.##.####..#..#",
                '5' to "####..###..####", '6' to "####..####.####", '7' to "###..#..#..#..#", '8' to "####.#####.####", '9' to "####.####..####",
                'Y' to "#.##.#.#..#..#.", 'G' to "####..#.##.####", 'D' to "##.#.##.##.###.", 'L' to "#..#..#..#..###", 'B' to "##.#.###.#.###.", ' ' to "...............")
            var x = ox
            for (ch in s) { val g = F[ch] ?: F[' ']!!; for (i in 0 until 15) if (g[i] == '#') { val px = x + i % 3; val py = oy + i / 3; if (px in 0..31 && py in 0..15) put(px, py) }; x += 4 }
        }
        if (now < messageUntil && message.isNotEmpty()) {
            // a little reaction: the pet with a heart or a "no" cross
            blit(sprite(), 0, 0)
            if (message.endsWith("!") || message.startsWith("ALL") || message.startsWith("LIGHT")) blit(HEART, 20, 4) else { for (i in 0 until 6) { put(20 + i, 4 + i); put(25 - i, 4 + i) } }
            return
        }
        when (screen) {
            "meter" -> when (screenArg) {
                0 -> { digits("${ageDays}Y", 1, 2); digits("${weight}G", 1, 9) }
                1 -> { for (i in 0 until 4) blit(if (i < hunger) HEART else HEART_O, i * 8, 6) }
                2 -> { for (i in 0 until 4) blit(if (i < happy) HEART else HEART_O, i * 8, 6) }
                else -> { for (x in 1..30) { put(x, 5); put(x, 10) }; put(1, 6); put(1, 7); put(1, 8); put(1, 9); put(30, 6); put(30, 7); put(30, 8); put(30, 9)
                    val n = (discipline * 28 / 100); for (x in 2 until 2 + n) for (y in 6..9) put(x, y) }
            }
            "food" -> { blit(RICE, 4, 5); blit(CAKE, 20, 5); val sx = if (screenArg == 0) 4 else 20; for (x in sx - 1 until sx + 9) put(x, 13) }
            "game" -> {
                blit(sprite(), 8, 0, flip = now < gameShowUntil && gameDir > 0)
                if (now >= gameShowUntil) { put(2, 8); put(3, 7); put(3, 9); put(29, 8); put(28, 7); put(28, 9) }   // < ? >
                digits("${gameWins}", 0, 11)
            }
            else -> {
                if (stage == 5) { blit(GHOST, 8, (frame)); return }
                val walk = if (stage == 0) (if (frame == 0) 0 else 1) else ((now / 1000) % 6).toInt().let { if (it < 3) it else 6 - it } * 2
                val sp = sprite()
                if (asleep && stage > 0) { blit(sp, 6, 1); blit(ZZZ, 22, frame); if (!lightsOff) for (x in 0..31) if ((x + frame) % 4 == 0) put(x, 0) }
                else blit(sp, 2 + walk, if (stage == 1) frame else 0, flip = frame == 1 && stage > 1)
                for (i in 0 until min(poops, 2)) blit(POOP, 24, 2 + i * 7)
                if (sick) blit(SKULL, 16, 0)
            }
        }
    }

    /** The room's copy of the LCD (drawn on the GL thread a couple of times a second). */
    fun paintRoomLcd(ctx: Canvas2D, now: Long) {
        ctx.fillStyle = if (lightsOff && asleep) "#4a5a30" else "#9fbf6a"; ctx.fillRect(0, 0, 32, 16)
        ctx.fillStyle = "#26341a"
        drawLcd(now) { x, y -> ctx.fillRect(x, y, 1, 1) }
    }
}

// =====================================================================================================================
/** The device you hold: an egg with the LCD, the eight icons and the A / B / C buttons. */
class TamagotchuPanel(private val act: Activity, private val root: FrameLayout, private val tama: Tamagotchu, private val beep: () -> Unit) : View(act) {
    private val dp = act.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif-medium", Typeface.BOLD) }
    val isOpen get() = visibility == VISIBLE
    private val ICONS = listOf("🍙" to "FOOD", "💡" to "LIGHT", "🎾" to "PLAY", "💉" to "MEDICINE", "🛁" to "TOILET", "📊" to "STATUS", "📣" to "SCOLD", "❗" to "CALL")
    private var sel = -1
    private val iconR = Array(8) { RectF() }
    private val btnR = Array(3) { RectF() }
    private val closeR = RectF(); private val newR = RectF()
    private val tmp = RectF()

    init { visibility = GONE; elevation = 58f; root.addView(this, FrameLayout.LayoutParams(-1, -1)) }
    fun open() { visibility = VISIBLE; bringToFront(); tama.screen = "main"; sel = -1; tama.tick(System.currentTimeMillis()); postInvalidateOnAnimation() }
    fun close() { visibility = GONE; tama.screen = "main" }

    override fun onDraw(c: Canvas) {
        try { drawDevice(c) } catch (e: Throwable) { CrashLog.recordNonFatal("Tamagotchu", e) }
        if (isOpen) postInvalidateOnAnimation()
    }
    private fun drawDevice(c: Canvas) {
        val W = width.toFloat(); val H = height.toFloat()
        p.shader = null; p.color = 0xcc0b0712.toInt(); c.drawRect(0f, 0f, W, H, p)
        val ew = min(W * 0.86f, H * 0.62f); val eh = ew * 1.18f
        val cx = W / 2; val cy = H / 2
        val egg = RectF(cx - ew / 2, cy - eh / 2, cx + ew / 2, cy + eh / 2)
        // the egg shell: glossy pink with printed dots
        p.shader = LinearGradient(egg.left, egg.top, egg.right, egg.bottom, 0xffffb3d9.toInt(), 0xffe8579c.toInt(), Shader.TileMode.CLAMP)
        c.drawOval(egg, p); p.shader = null
        p.color = 0x33ffffff; for (i in 0 until 26) { val a = i * 2.39f; val r = ew * 0.42f * ((i % 7) / 7f + 0.2f); c.drawCircle(cx + kotlin.math.cos(a) * r, cy + kotlin.math.sin(a) * r * 1.15f, 4 * dp, p) }
        p.color = 0x55ffffff; tmp.set(egg.left + ew * 0.16f, egg.top + eh * 0.06f, egg.left + ew * 0.42f, egg.top + eh * 0.2f); c.drawOval(tmp, p)
        // the bezel and LCD
        val lw = ew * 0.62f; val lh = lw * 0.5f
        val lcd = RectF(cx - lw / 2, cy - lh * 0.75f, cx + lw / 2, cy + lh * 0.25f)
        p.color = 0xff3b0d2a.toInt(); tmp.set(lcd.left - 26 * dp, lcd.top - 44 * dp, lcd.right + 26 * dp, lcd.bottom + 44 * dp); c.drawRoundRect(tmp, 26 * dp, 26 * dp, p)
        val dark = tama.asleep && tama.lightsOff
        p.color = if (dark) 0xff4a5a30.toInt() else 0xff9fbf6a.toInt(); c.drawRect(lcd, p)
        val px = lw / 32f
        p.color = 0x18000000; for (x in 0 until 32) for (y in 0 until 16) c.drawRect(lcd.left + x * px + 0.6f, lcd.top + y * px + 0.6f, lcd.left + (x + 1) * px - 0.6f, lcd.top + (y + 1) * px - 0.6f, p)
        p.color = 0xff26341a.toInt()
        tama.drawLcd(System.currentTimeMillis()) { x, y -> c.drawRect(lcd.left + x * px + 0.6f, lcd.top + y * px + 0.6f, lcd.left + (x + 1) * px - 0.6f, lcd.top + (y + 1) * px - 0.6f, p) }
        // the icons: four above the screen, four below (selected = lit, the call icon lights by itself)
        val iy0 = lcd.top - 26 * dp; val iy1 = lcd.bottom + 26 * dp
        for (i in 0 until 8) {
            val col = i % 4; val ix = lcd.left + lw * (col + 0.5f) / 4f; val iy = if (i < 4) iy0 else iy1
            iconR[i].set(ix - 18 * dp, iy - 16 * dp, ix + 18 * dp, iy + 16 * dp)
            val lit = i == sel || (i == 7 && tama.calling)
            p.color = if (lit) 0xfffde047.toInt() else 0x22ffffff; c.drawRoundRect(iconR[i], 8 * dp, 8 * dp, p)
            tp.textSize = 18 * dp; tp.textAlign = Paint.Align.CENTER; tp.color = if (lit) 0xff000000.toInt() else 0xbbffffff.toInt()
            c.drawText(ICONS[i].first, ix, iy + 7 * dp, tp)
        }
        // A B C buttons
        val by = egg.bottom - eh * 0.2f
        for (i in 0 until 3) {
            val bx = cx + (i - 1) * ew * 0.2f; val r = 18 * dp
            btnR[i].set(bx - r, by - r + (if (i == 1) 10 * dp else 0f), bx + r, by + r + (if (i == 1) 10 * dp else 0f))
            p.color = 0xfffacc15.toInt(); c.drawOval(btnR[i], p)
            p.color = 0x66000000; p.style = Paint.Style.STROKE; p.strokeWidth = 2 * dp; c.drawOval(btnR[i], p); p.style = Paint.Style.FILL
            tp.textSize = 13 * dp; tp.color = 0xff7a3a10.toInt(); c.drawText("ABC"[i].toString(), btnR[i].centerX(), btnR[i].centerY() + 5 * dp, tp)
        }
        // labels
        tp.textAlign = Paint.Align.CENTER; tp.color = 0xffffffff.toInt(); tp.textSize = 16 * dp
        c.drawText("TAMAGOTCHU · ${tama.name}", cx, egg.top - 18 * dp, tp)
        tp.textSize = 12 * dp; tp.color = 0xffd1d5db.toInt()
        val stageName = arrayOf("EGG", "BABY", "CHILD", "TEEN", "ADULT", "GONE")[tama.stage]
        val status = when {
            tama.stage == 5 -> "It went back to its home planet. Gen ${tama.generation}"
            tama.screen == "game" -> "Which way will it look? A = left · C = right (round ${tama.gameRound + 1}/5)"
            sel >= 0 -> "${ICONS[sel].second}: press B to use, C to cancel"
            tama.calling -> "It's calling you! Check the ❗ status"
            tama.asleep -> "Sleeping. Turn the light off (💡)"
            else -> "$stageName · age ${tama.ageDays} · ${tama.weight}g · mistakes ${tama.mistakes}"
        }
        c.drawText(status, cx, egg.bottom + 26 * dp, tp)
        closeR.set(W - 60 * dp, 16 * dp, W - 16 * dp, 60 * dp)
        p.color = 0xffcc0033.toInt(); c.drawRoundRect(closeR, 10 * dp, 10 * dp, p)
        tp.textSize = 18 * dp; tp.color = 0xffffffff.toInt(); c.drawText("✕", closeR.centerX(), closeR.centerY() + 6 * dp, tp)
        if (tama.stage == 5) {
            newR.set(cx - 90 * dp, egg.bottom + 40 * dp, cx + 90 * dp, egg.bottom + 80 * dp)
            p.color = 0xff1d4ed8.toInt(); c.drawRoundRect(newR, 12 * dp, 12 * dp, p)
            tp.textSize = 15 * dp; c.drawText("HATCH A NEW EGG", newR.centerX(), newR.centerY() + 5 * dp, tp)
        } else newR.setEmpty()
    }

    private fun say(msg: String) { tama.message = msg; tama.messageUntil = System.currentTimeMillis() + 1600; beep() }

    private fun press(btn: Int) {
        beep()
        val now = System.currentTimeMillis()
        when (tama.screen) {
            "game" -> {
                if (btn == 1 || now < tama.gameShowUntil) return
                val guess = if (btn == 0) -1 else 1
                tama.gameDir = if (Random.nextBoolean()) -1 else 1
                if (guess == tama.gameDir) tama.gameWins++
                tama.gameShowUntil = now + 900; tama.gameRound++
                if (tama.gameRound >= 5) postDelayed({ val r = tama.playResult(tama.gameWins); tama.screen = "main"; say(r) }, 950)
                return
            }
            "meter" -> { if (btn == 2) tama.screen = "main" else tama.screenArg = (tama.screenArg + 1) % 4; return }
            "food" -> {
                when (btn) { 0 -> tama.screenArg = 1 - tama.screenArg; 1 -> { val r = if (tama.screenArg == 0) tama.feedMeal() else tama.feedSnack(); tama.screen = "main"; say(r) }; else -> tama.screen = "main" }
                return
            }
        }
        when (btn) {
            0 -> sel = (sel + 1) % 7   // (the call icon only lights up, like the real one)
            1 -> if (sel >= 0) use(sel)
            2 -> sel = -1
        }
    }
    private fun use(i: Int) {
        when (i) {
            0 -> { tama.screen = "food"; tama.screenArg = 0 }
            1 -> say(tama.toggleLight())
            2 -> if (tama.canPlay) { tama.screen = "game"; tama.gameRound = 0; tama.gameWins = 0; tama.gameShowUntil = 0L } else say("NOT NOW")
            3 -> say(tama.medicine())
            4 -> say(tama.clean())
            5 -> { tama.screen = "meter"; tama.screenArg = 0 }
            6 -> say(tama.scold())
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked != MotionEvent.ACTION_DOWN) return true
        val x = e.x; val y = e.y
        when {
            closeR.contains(x, y) -> close()
            !newR.isEmpty && newR.contains(x, y) -> { tama.newEgg(System.currentTimeMillis()); beep() }
            else -> {
                for (i in 0 until 3) if (btnR[i].contains(x, y)) { press(i); return true }
                for (i in 0 until 7) if (iconR[i].contains(x, y)) { if (tama.screen == "main" || tama.screen == "meter") { tama.screen = "main"; sel = i; beep(); use(i) }; return true }
            }
        }
        return true
    }
}
