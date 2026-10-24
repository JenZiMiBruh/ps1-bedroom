package com.ps1bedroom.room

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * PAPER TOSS (v0.48, rebuilt as a full game in v0.55): flick crumpled paper into the wastebasket, PS1-style. Drawn
 * into a small low-res bitmap and blown up with no smoothing so it looks like a real 1998 mini-game.
 *
 * A run: you have a few strikes; every miss costs one. Every 5 baskets the stage goes up and adds something new
 * (wind, a sliding basket, a cardboard box in the way, gusts mid-flight), and the basket backs away. Streaks build a
 * combo multiplier (3+ in a row = ON FIRE, double points), swishes and long shots score more, and now and then a
 * golden wad is worth triple. Points become coins at the end of the run, spent in the upgrade shop between runs.
 * Best score, coins, upgrades and lifetime stats are remembered.
 */
class PaperToss(private val act: Activity, private val root: FrameLayout, private val app: App) {
    private val prefs = act.getSharedPreferences("ps1bedroom", Context.MODE_PRIVATE)
    private val rnd = Random.Default
    private val view = GameView()
    val isOpen get() = view.visibility == View.VISIBLE

    init {
        view.visibility = View.GONE
        view.elevation = 60f
        root.addView(view, FrameLayout.LayoutParams(-1, -1))
    }

    fun open() { view.visibility = View.VISIBLE; view.bringToFront(); view.toTitle(); view.postInvalidateOnAnimation() }
    fun close() { view.visibility = View.GONE }

    /** A shop upgrade: id (prefs key), name, what it does, max level, base cost. */
    private class Upgrade(val id: String, val name: String, val desc: String, val max: Int, val base: Int) {
        fun cost(lvl: Int) = (base * (lvl + 1.0).pow(1.6)).roundToInt()
    }
    private val UPGRADES = listOf(
        Upgrade("bin", "BIGGER BASKET", "wider opening", 4, 40),
        Upgrade("aim", "AIM GUIDE", "shows the arc", 3, 60),
        Upgrade("steady", "STEADY HAND", "less wind", 3, 50),
        Upgrade("life", "EXTRA STRIKE", "+1 miss allowed", 2, 120),
        Upgrade("lucky", "LUCKY RIM", "rim shots drop in", 3, 70),
        Upgrade("gold", "GOLDEN TOUCH", "more golden wads", 3, 55),
        Upgrade("magnet", "COIN MAGNET", "+25% coins", 3, 80)
    )
    private fun lvl(id: String) = prefs.getInt("paperToss.up.$id", 0)

    private class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val max: Float, val col: Int, val size: Float, val grav: Float)
    private class Popup(var x: Float, var y: Float, val text: String, val col: Int, var life: Float)

    private inner class GameView : View(act) {
        // ---- low-res frame ----
        private var W = 240; private var H = 400
        private var frame: Bitmap? = null
        private var fc: Canvas? = null
        private var bg: Bitmap? = null
        private var bgAge = 99f
        private val p = Paint().apply { isAntiAlias = false }
        private val txt = Paint().apply { isAntiAlias = false; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); color = Color.WHITE }
        private val blit = Paint().apply { isFilterBitmap = false }
        private val dstR = RectF(); private val srcR = Rect()

        // ---- world ----
        private val camH = 1.0f; private val zc = 1.2f
        private var horizon = 150f; private var k = 200f
        private val baseRimR = 0.19f; private val rimH = 0.42f; private val ballR = 0.065f
        private val rimR get() = baseRimR * (1f + 0.08f * lvl("bin"))
        private var binBaseX = 0f; private var binX = 0f; private var binZ = 3f
        private var binSlide = 0f; private var binSlideSpeed = 0f; private var binClock = 0f
        // cardboard box obstacle (x, z centre; half width / depth; height)
        private var boxOn = false; private var boxX = 0f; private var boxZ = 0f; private val boxW = 0.28f; private val boxD = 0.2f; private val boxH = 0.36f
        private var bx = 0f; private var bz = 0f; private var bh = 0.35f
        private var vx = 0f; private var vz = 0f; private var vh = 0f
        private var spin = 0f
        private var wind = 0f
        private var gusts = false
        private var golden = false
        private val trail = FloatArray(16); private var trailN = 0

        // ---- run ----
        private var screen = 0      // 0 title · 1 playing · 2 shop · 3 game over · 4 stats
        private var state = 0       // 0 ready · 1 flying · 2 scored · 3 missed
        private var stateT = 0f
        private var rimHit = false; private var boxHit = false
        private var score = 0; private var streak = 0; private var baskets = 0; private var stage = 1
        private var lives = 3; private var maxLives = 3; private var runSwishes = 0; private var runBestStreak = 0
        private var coinsEarned = 0; private var newBest = false
        private var best = prefs.getInt("paperToss.best", 0)
        private var coins = prefs.getInt("paperToss.coins", 0)
        private var banner = ""; private var bannerCol = Color.WHITE; private var bannerT = 0f
        private var stageBanner = ""; private var stageBannerT = 0f
        private var shake = 0f
        private var clock = 0f
        private var lastNs = 0L
        private val particles = ArrayList<Particle>()
        private val popups = ArrayList<Popup>()
        // ---- touch ----
        private var dragging = false; private var dx0 = 0f; private var dy0 = 0f; private var dx1 = 0f; private var dy1 = 0f; private var downT = 0L
        private val closeR = RectF()
        private val buttons = ArrayList<Pair<RectF, () -> Unit>>()

        // ================================================================ flow
        fun toTitle() { screen = 0; particles.clear(); popups.clear(); state = 0; resetBall(); lastNs = 0L }

        private fun startRun() {
            screen = 1; score = 0; streak = 0; baskets = 0; stage = 1; runSwishes = 0; runBestStreak = 0; newBest = false
            maxLives = 3 + lvl("life"); lives = maxLives
            particles.clear(); popups.clear()
            prefs.edit().putInt("paperToss.games", prefs.getInt("paperToss.games", 0) + 1).apply()
            stageBanner = "STAGE 1"; stageBannerT = 2f
            nextThrow()
            banner = "FLICK UP TO THROW"; bannerCol = Color.rgb(0xff, 0xd2, 0x7d); bannerT = 99f
            app.sfx("titleSting")
        }

        private fun endRun() {
            screen = 3; stateT = 0f
            coinsEarned = (score * (1f + 0.25f * lvl("magnet"))).roundToInt()
            coins += coinsEarned
            newBest = score > best
            if (newBest) best = score
            val e = prefs.edit().putInt("paperToss.coins", coins).putInt("paperToss.best", best)
            if (runBestStreak > prefs.getInt("paperToss.longest", 0)) e.putInt("paperToss.longest", runBestStreak)
            e.putInt("paperToss.topStage", max(stage, prefs.getInt("paperToss.topStage", 0)))
            e.apply()
            app.sfx(if (newBest) "titleSting" else "caseClick")
            if (newBest && score >= 50) app.toast("🗑️ PAPER TOSS: new best, $score points!")
        }

        private fun resetBall() { bx = 0f; bz = 0f; bh = 0.35f; vx = 0f; vz = 0f; vh = 0f; spin = 0f; trailN = 0 }

        /** Sets up the next throw for the current stage. */
        private fun nextThrow() {
            val s = stage
            binZ = 2.6f + min(baskets, 30) * 0.09f + rnd.nextFloat() * 0.4f
            binBaseX = (rnd.nextFloat() - 0.5f) * min(1.1f, 0.15f + s * 0.12f)
            binSlide = if (s >= 3) min(0.55f, 0.2f + (s - 3) * 0.08f) else 0f
            binSlideSpeed = if (s >= 3) 0.8f + (s - 3) * 0.15f + rnd.nextFloat() * 0.4f else 0f
            binX = binBaseX
            val maxW = when { s <= 1 -> 0f; s == 2 -> 0.9f; else -> min(2.4f, 0.9f + (s - 2) * 0.3f) } * (1f - 0.15f * lvl("steady"))
            wind = ((rnd.nextFloat() * 2f - 1f) * maxW * 10f).roundToInt() / 10f
            gusts = s >= 5
            boxOn = s >= 4 && rnd.nextFloat() < 0.75f
            if (boxOn) { boxZ = binZ * (0.45f + rnd.nextFloat() * 0.2f); boxX = binBaseX * 0.5f + (rnd.nextFloat() - 0.5f) * 0.5f }
            golden = rnd.nextFloat() < 0.07f + 0.05f * lvl("gold")
            resetBall()
            state = 0; stateT = 0f; rimHit = false; boxHit = false
        }

        private fun fanDrift(): Float = if (app.isBuilt && app.fan.fanSpinning) 0.25f else 0f
        private fun windNow(t: Float) = wind + fanDrift() * sin(t * 6f) + (if (gusts) sin(t * 2.7f + binZ) * 0.6f else 0f)
        private fun multiplier() = 1 + min(streak, 9) / 3
        private fun onFire() = streak >= 3

        // ---- projection ----
        private fun sx(x: Float, z: Float) = W / 2f + x * k / (z + zc)
        private fun sy(h: Float, z: Float) = horizon + (camH - h) * k / (z + zc)
        private fun sc(z: Float) = k / (z + zc)

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            super.onSizeChanged(w, h, ow, oh)
            if (w <= 0 || h <= 0) return
            W = 240; H = max(140, (240f * h / w).toInt())
            frame?.recycle(); bg?.recycle()
            frame = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888); fc = Canvas(frame!!)
            bg = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888); bgAge = 99f
            horizon = H * 0.36f
            k = (H * 0.93f - horizon) * zc / camH
        }

        // ================================================================ simulation
        private fun step(dt: Float) {
            clock += dt; stateT += dt; bannerT -= dt; stageBannerT -= dt; shake = max(0f, shake - dt * 3f)
            stepParticles(dt)
            if (screen != 1) return
            binClock += dt
            if (state == 0 || state == 1) binX = binBaseX + sin(binClock * binSlideSpeed) * binSlide
            when (state) {
                1 -> {
                    val sub = 4; val d = dt / sub
                    repeat(sub) {
                        if (state != 1) return@repeat
                        vh -= 9.8f * d
                        vx += windNow(stateT) * d
                        val ph = bh
                        bx += vx * d; bz += vz * d; bh += vh * d
                        spin += d * 12f
                        if (boxOn) collideBox()
                        val ddx = bx - binX; val ddz = bz - binZ; val dist = hypot(ddx, ddz)
                        // Dropping through the opening
                        if (ph >= rimH && bh < rimH && vh < 0) {
                            if (dist < rimR - ballR * 0.6f) { scored(false); return@repeat }
                            if (dist < rimR + ballR) {
                                if (rnd.nextFloat() < 0.15f * lvl("lucky")) { rimHit = true; scored(true); return@repeat }
                                // Rim: kicked out and up
                                rimHit = true
                                val nx = ddx / max(0.001f, dist); val nz = ddz / max(0.001f, dist)
                                vx = nx * 1.4f + vx * 0.2f; vz = nz * 1.4f + vz * 0.15f; vh = 1.6f; bh = rimH + 0.01f
                                shake = 0.35f; burst(sx(bx, bz), sy(bh, bz), 6, Color.rgb(0x94, 0xa3, 0xb8), 1.5f)
                                app.sfx("caseClick")
                            }
                        }
                        // Outer wall of the basket
                        if (bh < rimH && bh > 0f && dist < rimR + ballR && dist > rimR - ballR * 0.6f) {
                            val nx = ddx / max(0.001f, dist); val nz = ddz / max(0.001f, dist)
                            val vn = vx * nx + vz * nz
                            if (vn < 0) { vx -= 1.5f * vn * nx; vz -= 1.5f * vn * nz; vx *= 0.5f; vz *= 0.5f; rimHit = true }
                        }
                        if (bh <= 0f) { bh = 0f; missed() }
                        if (bz < -0.5f || bz > 14f || abs(bx) > 6f) missed()
                    }
                    // trail
                    for (i in trail.size - 1 downTo 2) trail[i] = trail[i - 2]
                    trail[0] = sx(bx, bz); trail[1] = sy(bh, bz); trailN = min(trail.size / 2, trailN + 1)
                    if (onFire() && rnd.nextFloat() < 0.6f) spark(sx(bx, bz), sy(bh, bz), if (rnd.nextBoolean()) Color.rgb(0xff, 0x9f, 0x1c) else Color.rgb(0xff, 0xe0, 0x5c))
                    if (golden && rnd.nextFloat() < 0.4f) spark(sx(bx, bz), sy(bh, bz), Color.rgb(0xff, 0xe8, 0x7c))
                }
                2 -> { if (bh > 0.08f) bh -= dt * 1.2f; if (stateT > 1.1f) nextThrow() }
                3 -> {
                    // A little roll along the carpet
                    bx += vx * dt * 0.3f; bz += vz * dt * 0.3f; vx *= 0.92f; vz *= 0.92f
                    if (stateT > 1.0f) { if (lives <= 0) endRun() else nextThrow() }
                }
            }
        }

        /** Bounces the wad off the cardboard box (an axis-aligned block). */
        private fun collideBox() {
            val ex = boxW + ballR; val ez = boxD + ballR
            val dx = bx - boxX; val dz = bz - boxZ
            if (abs(dx) >= ex || abs(dz) >= ez || bh >= boxH + ballR || bh <= 0f) return
            val px = ex - abs(dx); val pz = ez - abs(dz); val ph = boxH + ballR - bh
            when {
                ph < px && ph < pz -> { bh = boxH + ballR; if (vh < 0) vh = -vh * 0.45f; vx *= 0.8f; vz *= 0.8f }
                px < pz -> { bx = boxX + (if (dx > 0) ex else -ex); vx = -vx * 0.5f }
                else -> { bz = boxZ + (if (dz > 0) ez else -ez); vz = -vz * 0.45f }
            }
            if (!boxHit) { boxHit = true; shake = 0.25f; app.sfx("caseClick"); burst(sx(bx, bz), sy(bh, bz), 5, Color.rgb(0xc0, 0x8a, 0x4e), 1.2f) }
        }

        private fun scored(lucky: Boolean) {
            state = 2; stateT = 0f
            val swish = !rimHit && !boxHit
            streak++; baskets++
            runBestStreak = max(runBestStreak, streak)
            if (swish) runSwishes++
            val distBonus = max(0, ((binZ - 2.6f) / 0.9f).toInt())
            val base = 1 + (if (swish) 1 else 0) + distBonus + (if (abs(wind) >= 1.2f) 1 else 0) + (if (binSlide > 0f) 1 else 0) + (if (boxHit) 2 else 0)
            var pts = base * multiplier() * (if (onFire()) 2 else 1)
            if (golden) pts *= 3
            score += pts
            banner = when {
                lucky -> "LUCKY BOUNCE!"
                boxHit -> "OFF THE BOX!"
                golden -> "GOLDEN!"
                swish && streak >= 5 -> "SWISH x$streak!"
                swish -> "SWISH!"
                else -> "IN OFF THE RIM"
            }
            bannerCol = when { golden -> Color.rgb(0xff, 0xd7, 0x3a); swish -> Color.rgb(0x7c, 0xff, 0x6b); else -> Color.rgb(0x2e, 0xf2, 0xff) }
            bannerT = 1.1f
            bx = binX; bz = binZ
            val cx = sx(binX, binZ); val cy = sy(rimH, binZ)
            popups += Popup(cx, cy - 8f, "+$pts", bannerCol, 1.2f)
            burst(cx, cy, if (swish) 22 else 12, 0, 2.6f)
            if (streak == 3) { popups += Popup(W / 2f, horizon * 0.9f, "ON FIRE! x2", Color.rgb(0xff, 0x9f, 0x1c), 1.6f) }
            val e = prefs.edit().putInt("paperToss.baskets", prefs.getInt("paperToss.baskets", 0) + 1)
            if (swish) e.putInt("paperToss.swishes", prefs.getInt("paperToss.swishes", 0) + 1)
            e.apply()
            app.sfx("ps1Button")
            if (streak == 10) app.toast("🗑️ PAPER TOSS: 10 in a row! The wastebasket fears you.")
            if (baskets % 5 == 0) {
                stage++
                stageBanner = "STAGE $stage"; stageBannerT = 2.2f
                popups += Popup(W / 2f, horizon * 0.55f, stageNews(stage), Color.rgb(0xff, 0x77, 0xee), 2.2f)
            }
        }
        private fun stageNews(s: Int) = when (s) {
            2 -> "THE FAN KICKS ON"
            3 -> "THE BASKET SLIDES!"
            4 -> "A BOX IN THE WAY"
            5 -> "GUSTY!"
            else -> "FURTHER AWAY..."
        }
        private fun missed() {
            if (state != 1) return
            state = 3; stateT = 0f
            lives--
            banner = when { lives <= 0 -> "GAME OVER"; rimHit -> "RIMMED OUT!"; else -> listOf("MISS!", "SO CLOSE", "AIRBALL", "CHECK THE WIND").random() }
            bannerCol = Color.rgb(0xff, 0x4d, 0x6d); bannerT = 1.0f
            streak = 0; shake = 0.4f
            app.sfx("caseClick")
        }

        // ---- particles ----
        private val CONFETTI = intArrayOf(Color.rgb(0xff, 0x3e, 0xa5), Color.rgb(0x2e, 0xf2, 0xff), Color.rgb(0x7c, 0xff, 0x6b), Color.rgb(0xff, 0xd2, 0x3f), Color.WHITE)
        private fun burst(x: Float, y: Float, n: Int, col: Int, speed: Float) {
            repeat(n) {
                if (particles.size > 140) return
                val a = rnd.nextFloat() * 6.283f; val s = (0.4f + rnd.nextFloat()) * speed * 30f
                particles += Particle(x, y, cos(a) * s, sin(a) * s - 40f, 0.9f + rnd.nextFloat() * 0.5f, 1.4f,
                    if (col == 0) CONFETTI[rnd.nextInt(CONFETTI.size)] else col, if (rnd.nextFloat() < 0.3f) 2f else 1f, 140f)
            }
        }
        private fun spark(x: Float, y: Float, col: Int) {
            if (particles.size > 140) return
            particles += Particle(x + rnd.nextFloat() * 4 - 2, y + rnd.nextFloat() * 4 - 2, rnd.nextFloat() * 10 - 5, -10f - rnd.nextFloat() * 14f, 0.35f, 0.35f, col, 1f, -10f)
        }
        private fun stepParticles(dt: Float) {
            val it = particles.iterator()
            while (it.hasNext()) { val q = it.next(); q.life -= dt; if (q.life <= 0f) { it.remove(); continue }; q.vy += q.grav * dt; q.x += q.vx * dt; q.y += q.vy * dt; q.vx *= 0.985f }
            val ip = popups.iterator()
            while (ip.hasNext()) { val q = ip.next(); q.life -= dt; q.y -= dt * 14f; if (q.life <= 0f) ip.remove() }
        }

        // ================================================================ drawing
        override fun onDraw(c: Canvas) {
            val now = System.nanoTime()
            val dt = if (lastNs == 0L) 0f else min(0.05f, (now - lastNs) / 1e9f)
            lastNs = now
            step(dt)
            val g = fc ?: return
            bgAge += dt
            if (bgAge > 5f) { bgAge = 0f; drawRoom(Canvas(bg!!)) }
            g.save()
            if (shake > 0f) g.translate((rnd.nextFloat() - 0.5f) * shake * 8f, (rnd.nextFloat() - 0.5f) * shake * 6f)
            g.drawBitmap(bg!!, 0f, 0f, null)
            if (screen == 1 || screen == 3) drawWorld(g)
            drawParticles(g)
            g.restore()
            buttons.clear()
            when (screen) {
                0 -> drawTitle(g)
                1 -> { drawAim(g); drawHud(g) }
                2 -> drawShop(g)
                3 -> { drawHud(g); drawGameOver(g) }
                4 -> drawStats(g)
            }
            drawClose(g)
            // blow it up, pixel-sharp
            srcR.set(0, 0, W, H); dstR.set(0f, 0f, width.toFloat(), height.toFloat())
            c.drawColor(Color.BLACK)
            c.drawBitmap(frame!!, srcR, dstR, blit)
            // scanlines
            p.color = Color.argb(28, 0, 0, 0)
            var y = 0f; val step = max(2f, height / H.toFloat())
            while (y < height) { c.drawRect(0f, y, width.toFloat(), y + step * 0.4f, p); y += step }
            if (isOpen) postInvalidateOnAnimation()
        }

        private fun drawWorld(g: Canvas) {
            // Painter's order: farthest first. The basket and the box are fixed; the wad goes wherever it is.
            val inBin = state == 2
            val ballZ = bz
            val items = ArrayList<Pair<Float, () -> Unit>>(3)
            items += binZ to { if (inBin) { drawBinBack(g); drawBall(g); drawBinFront(g) } else { drawBinBack(g); drawBinFront(g) } }
            if (boxOn) items += boxZ to { drawBox(g) }
            if (!inBin) items += (ballZ - 0.001f) to { drawShadow(g); drawTrail(g); drawBall(g) }
            items.sortByDescending { it.first }
            for (i in items) i.second()
        }

        private fun drawRoom(g: Canvas) {
            val day = if (app.isBuilt) app.env.daylight.toFloat().coerceIn(0f, 1f) else 0.3f
            // Wall: the bedroom's navy paint with a soft light falloff toward the ceiling
            p.shader = LinearGradient(0f, 0f, 0f, horizon, Color.rgb(0x12, 0x18, 0x30), Color.rgb(0x26, 0x30, 0x58), Shader.TileMode.CLAMP)
            g.drawRect(0f, 0f, W.toFloat(), horizon, p); p.shader = null
            // ordered-dither plaster
            p.color = Color.argb(40, 0, 0, 0)
            var y = 0; while (y < horizon) { var x = (y / 2) % 4; while (x < W) { g.drawRect(x.toFloat(), y.toFloat(), x + 1f, y + 1f, p); x += 4 }; y += 2 }
            // Window: sky by time of day, a moon or sun, the cross frame
            val wx0 = W * 0.6f; val wx1 = W * 0.92f; val wy0 = horizon * 0.14f; val wy1 = horizon * 0.74f
            val skyTop = blend(Color.rgb(0x06, 0x0a, 0x22), Color.rgb(0x3b, 0x82, 0xf6), day)
            val skyBot = blend(Color.rgb(0x1e, 0x1b, 0x4b), Color.rgb(0xbf, 0xdb, 0xfe), day)
            p.shader = LinearGradient(0f, wy0, 0f, wy1, skyTop, skyBot, Shader.TileMode.CLAMP); g.drawRect(wx0, wy0, wx1, wy1, p); p.shader = null
            if (day < 0.4f) {
                p.color = Color.WHITE
                for (i in 0 until 9) { val sx0 = wx0 + ((i * 37) % 70) / 70f * (wx1 - wx0); val sy0 = wy0 + ((i * 53) % 50) / 50f * (wy1 - wy0) * 0.7f; g.drawRect(sx0, sy0, sx0 + 1, sy0 + 1, p) }
                p.color = Color.rgb(0xf1, 0xf5, 0xf9); g.drawCircle(wx0 + (wx1 - wx0) * 0.72f, wy0 + 10f, 5f, p)
            } else { p.color = Color.rgb(0xfe, 0xf0, 0x8a); g.drawCircle(wx0 + (wx1 - wx0) * 0.25f, wy0 + 12f, 6f, p) }
            // a far skyline silhouette
            p.color = blend(Color.rgb(0x05, 0x07, 0x14), Color.rgb(0x47, 0x55, 0x69), day)
            var bxx = wx0; var n = 0
            while (bxx < wx1) { val bw = 6f + (n * 7) % 9; val bhh = 6f + (n * 13) % 14; g.drawRect(bxx, wy1 - bhh, min(wx1, bxx + bw), wy1, p); bxx += bw + 1; n++ }
            p.color = Color.rgb(0xd8, 0xd0, 0xe8)
            g.drawRect(wx0 - 2, wy0 - 2, wx1 + 2, wy0, p); g.drawRect(wx0 - 2, wy1, wx1 + 2, wy1 + 3, p)
            g.drawRect(wx0 - 2, wy0, wx0, wy1, p); g.drawRect(wx1, wy0, wx1 + 2, wy1, p)
            g.drawRect((wx0 + wx1) / 2 - 1, wy0, (wx0 + wx1) / 2 + 1, wy1, p); g.drawRect(wx0, (wy0 + wy1) / 2 - 1, wx1, (wy0 + wy1) / 2 + 1, p)
            // window light spilling on the wall
            p.shader = RadialGradient((wx0 + wx1) / 2, wy1, (wx1 - wx0), Color.argb((30 + day * 40).toInt(), 0xbf, 0xdb, 0xfe), Color.argb(0, 0, 0, 0), Shader.TileMode.CLAMP)
            g.drawRect(0f, 0f, W.toFloat(), horizon, p); p.shader = null
            // A poster
            p.color = Color.rgb(0x11, 0x11, 0x11); g.drawRect(W * 0.08f - 1, horizon * 0.2f - 1, W * 0.3f + 1, horizon * 0.72f + 1, p)
            p.shader = LinearGradient(0f, horizon * 0.2f, 0f, horizon * 0.72f, Color.rgb(0x70, 0x1a, 0x75), Color.rgb(0xf4, 0x3f, 0x5e), Shader.TileMode.CLAMP)
            g.drawRect(W * 0.08f, horizon * 0.2f, W * 0.3f, horizon * 0.72f, p); p.shader = null
            p.color = Color.rgb(0xfa, 0xcc, 0x15); g.drawCircle(W * 0.19f, horizon * 0.42f, 7f, p)
            p.color = Color.rgb(0x1e, 0x1b, 0x4b); g.drawRect(W * 0.08f, horizon * 0.52f, W * 0.3f, horizon * 0.72f, p)
            txt.textSize = 7f; txt.textAlign = Paint.Align.CENTER; txt.color = Color.rgb(0x2e, 0xf2, 0xff); g.drawText("NEON", W * 0.19f, horizon * 0.64f, txt)
            // baseboard
            p.color = Color.rgb(0xd8, 0xd0, 0xe8); g.drawRect(0f, horizon - 4, W.toFloat(), horizon, p)
            p.color = Color.rgb(0x9a, 0x94, 0xae); g.drawRect(0f, horizon - 1, W.toFloat(), horizon, p)
            // carpet: navy berber, darker in the distance, with a perspective weave
            p.shader = LinearGradient(0f, horizon, 0f, H.toFloat(), Color.rgb(0x0e, 0x14, 0x2a), Color.rgb(0x26, 0x32, 0x5c), Shader.TileMode.CLAMP)
            g.drawRect(0f, horizon, W.toFloat(), H.toFloat(), p); p.shader = null
            p.color = Color.argb(60, 0x5a, 0x6a, 0xa8)
            for (i in -10..10) { val x0 = W / 2f + i * 0.5f * sc(0f); val x1 = W / 2f + i * 0.5f * sc(30f); line(g, x0, sy(0f, 0f), x1, sy(0f, 30f)) }
            var z = 0.5f; while (z < 14f) { val yy = sy(0f, z); g.drawRect(0f, yy, W.toFloat(), yy + 1f, p); z += 0.5f + z * 0.15f }
            // speckle
            val r = Random(7)
            p.color = Color.argb(50, 0, 0, 0)
            repeat(260) { val x = r.nextInt(W).toFloat(); val yy = horizon + r.nextFloat() * (H - horizon); g.drawRect(x, yy, x + 1, yy + 1, p) }
            // vignette
            p.shader = RadialGradient(W / 2f, H * 0.55f, H * 0.75f, intArrayOf(Color.argb(0, 0, 0, 0), Color.argb(0, 0, 0, 0), Color.argb(150, 0, 0, 0)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
            g.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p); p.shader = null
        }
        private fun blend(a: Int, b: Int, t: Float) = Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(), (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(), (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt())
        private fun line(g: Canvas, x0: Float, y0: Float, x1: Float, y1: Float) {
            val n = max(abs(x1 - x0), abs(y1 - y0)).toInt().coerceAtMost(800)
            for (i in 0..n) { val t = i / max(1f, n.toFloat()); g.drawRect(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, x0 + (x1 - x0) * t + 1, y0 + (y1 - y0) * t + 1, p) }
        }
        private fun ellipse(g: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, col: Int) { p.color = col; g.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, p) }

        private fun drawBinBack(g: Canvas) {
            val s = sc(binZ); val cx = sx(binX, binZ); val rr = rimR
            ellipse(g, cx, sy(0f, binZ) + 1, rr * s * 1.2f, rr * s * 0.32f, Color.argb(120, 0, 0, 0)) // shadow
            ellipse(g, cx, sy(rimH, binZ), rr * s, rr * s * 0.32f, Color.rgb(0x0b, 0x0f, 0x18))        // the dark opening
            ellipse(g, cx, sy(rimH, binZ) + rr * s * 0.06f, rr * s * 0.86f, rr * s * 0.24f, Color.rgb(0x05, 0x07, 0x0c))
            // paper already inside (more as the run goes on)
            val wads = min(5, 1 + baskets / 3)
            for (i in 0 until wads) {
                val ox = ((i * 37) % 9 - 4) / 6f; val col = if (i % 2 == 0) Color.rgb(0xc8, 0xcc, 0xd4) else Color.rgb(0xe5, 0xe7, 0xeb)
                ellipse(g, cx + rr * s * ox * 0.6f, sy(rimH, binZ) + 1 + (i % 2), rr * s * 0.26f, rr * s * 0.1f, col)
            }
        }
        private fun drawBinFront(g: Canvas) {
            val s = sc(binZ); val cx = sx(binX, binZ)
            val topY = sy(rimH, binZ); val botY = sy(0f, binZ)
            val rt = rimR * s; val rb = rimR * 0.76f * s
            val path = Path().apply { moveTo(cx - rt, topY); lineTo(cx + rt, topY); lineTo(cx + rb, botY); lineTo(cx - rb, botY); close() }
            // a cylinder: lit on the left, dark on the right
            p.shader = LinearGradient(cx - rt, 0f, cx + rt, 0f, intArrayOf(Color.rgb(0x2a, 0x35, 0x48), Color.rgb(0x64, 0x74, 0x8b), Color.rgb(0x3a, 0x47, 0x5c), Color.rgb(0x15, 0x1c, 0x28)),
                floatArrayOf(0f, 0.3f, 0.65f, 1f), Shader.TileMode.CLAMP)
            g.drawPath(path, p); p.shader = null
            // mesh stripes
            p.color = Color.argb(90, 0x0b, 0x0f, 0x18)
            for (i in -4..4) { val f = i / 5f; line(g, cx + rt * f, topY + 2, cx + rb * f, botY - 1) }
            var hy = topY + (botY - topY) * 0.33f
            while (hy < botY - 2) { val f = (hy - topY) / (botY - topY); val hw = rt + (rb - rt) * f; g.drawRect(cx - hw, hy, cx + hw, hy + 1, p); hy += (botY - topY) * 0.33f }
            p.color = Color.rgb(0x1e, 0x29, 0x3b); g.drawRect(cx - rb, botY - 2, cx + rb, botY, p)
            // front rim lip with a glint
            p.style = Paint.Style.STROKE; p.strokeWidth = max(1f, s * 0.025f)
            p.color = Color.rgb(0xb8, 0xc4, 0xd4)
            g.drawArc(cx - rt, topY - rt * 0.32f, cx + rt, topY + rt * 0.32f, 0f, 180f, false, p)
            p.color = Color.WHITE
            g.drawArc(cx - rt, topY - rt * 0.32f, cx + rt, topY + rt * 0.32f, 120f, 25f, false, p)
            p.style = Paint.Style.FILL
            if (binSlide > 0f && (state == 0 || state == 1)) { // little casters, so the sliding reads
                p.color = Color.rgb(0x11, 0x11, 0x11); g.drawRect(cx - rb, botY, cx - rb + 2, botY + 2, p); g.drawRect(cx + rb - 2, botY, cx + rb, botY + 2, p)
            }
        }
        private fun drawBox(g: Canvas) {
            val zf = boxZ - boxD; val zb = boxZ + boxD
            val fl = sx(boxX - boxW, zf); val fr = sx(boxX + boxW, zf); val ft = sy(boxH, zf); val fb = sy(0f, zf)
            val bl = sx(boxX - boxW, zb); val br = sx(boxX + boxW, zb); val bt = sy(boxH, zb)
            ellipse(g, (fl + fr) / 2, fb + 1, (fr - fl) * 0.62f, (fr - fl) * 0.12f, Color.argb(110, 0, 0, 0))
            // top
            p.color = Color.rgb(0xd9, 0xa8, 0x6c)
            g.drawPath(Path().apply { moveTo(fl, ft); lineTo(fr, ft); lineTo(br, bt); lineTo(bl, bt); close() }, p)
            p.color = Color.rgb(0xb8, 0x86, 0x4b); g.drawRect((fl + fr) / 2 - 1, bt, (fl + fr) / 2 + 1, ft, p) // tape seam
            // front
            p.shader = LinearGradient(0f, ft, 0f, fb, Color.rgb(0xb4, 0x7a, 0x3e), Color.rgb(0x8a, 0x5a, 0x2b), Shader.TileMode.CLAMP)
            g.drawRect(fl, ft, fr, fb, p); p.shader = null
            p.color = Color.rgb(0xc8, 0x9c, 0x5e); g.drawRect(fl, ft + (fb - ft) * 0.18f, fr, ft + (fb - ft) * 0.18f + 1, p)
            txt.textSize = max(5f, (fr - fl) * 0.18f); txt.textAlign = Paint.Align.CENTER; txt.color = Color.argb(150, 0x3b, 0x22, 0x0e)
            g.drawText("FRAGILE", (fl + fr) / 2, ft + (fb - ft) * 0.65f, txt)
        }
        private fun drawShadow(g: Canvas) {
            val s = sc(bz); ellipse(g, sx(bx, bz), sy(0f, bz), ballR * s * 1.2f, ballR * s * 0.35f, Color.argb(90, 0, 0, 0))
        }
        private fun drawTrail(g: Canvas) {
            if (state != 1 || trailN < 2) return
            val r = max(1.5f, ballR * sc(bz))
            val col = when { onFire() -> Color.rgb(0xff, 0x8a, 0x1c); golden -> Color.rgb(0xff, 0xd7, 0x3a); else -> Color.rgb(0xe5, 0xe7, 0xeb) }
            for (i in 1 until trailN) {
                val a = (1f - i / trailN.toFloat())
                p.color = Color.argb((a * (if (onFire() || golden) 170 else 70)).toInt(), Color.red(col), Color.green(col), Color.blue(col))
                g.drawCircle(trail[i * 2], trail[i * 2 + 1], r * (0.4f + a * 0.6f), p)
            }
        }
        private fun drawBall(g: Canvas) {
            val s = sc(bz); val cx = sx(bx, bz); val cy = sy(bh, bz); val r = max(2f, ballR * s)
            val (c0, c1, c2, cr) = if (golden) listOf(Color.rgb(0xf5, 0xc5, 0x3a), Color.rgb(0xc9, 0x93, 0x1c), Color.rgb(0xff, 0xf1, 0xa8), Color.rgb(0x9a, 0x6b, 0x0e))
                else listOf(Color.rgb(0xe5, 0xe7, 0xeb), Color.rgb(0xc4, 0xc8, 0xd0), Color.rgb(0xf8, 0xfa, 0xfc), Color.rgb(0x9c, 0xa3, 0xaf))
            if (onFire() && state == 1) ellipse(g, cx, cy, r * 1.6f, r * 1.6f, Color.argb(90, 0xff, 0x7a, 0x1c))
            ellipse(g, cx, cy, r, r, c0)
            ellipse(g, cx + r * 0.25f, cy + r * 0.25f, r * 0.7f, r * 0.7f, c1)
            ellipse(g, cx - r * 0.2f, cy - r * 0.25f, r * 0.5f, r * 0.45f, c2)
            // crumple creases that turn with the spin
            p.color = cr
            for (i in 0 until 4) { val a = spin + i * 1.6f; g.drawRect(cx + cos(a) * r * 0.5f, cy + sin(a) * r * 0.5f, cx + cos(a) * r * 0.5f + max(1f, r * 0.3f), cy + sin(a) * r * 0.5f + 1f, p) }
        }
        private fun drawParticles(g: Canvas) {
            for (q in particles) {
                val a = (q.life / q.max).coerceIn(0f, 1f)
                p.color = Color.argb((255 * min(1f, a * 1.5f)).toInt(), Color.red(q.col), Color.green(q.col), Color.blue(q.col))
                g.drawRect(q.x, q.y, q.x + q.size, q.y + q.size, p)
            }
        }

        /** Launch velocity (vx, vz, vh) for a flick from (ax, ay) to (bx, by) in screen pixels taking [ms]. */
        private fun launch(ax: Float, ay: Float, bxp: Float, byp: Float, ms: Long): FloatArray {
            val speedBoost = (300f / max(60L, ms)).coerceIn(0.85f, 1.15f)
            val pw = (((ay - byp) / (height * 0.5f)).coerceIn(0f, 1f) * speedBoost).coerceIn(0.1f, 1.25f)
            val z = 1.2f + pw * 6.6f
            return floatArrayOf(((bxp - ax) / width) * z * 1.1f, z, 3.4f)
        }

        private fun drawAim(g: Canvas) {
            if (!dragging || state != 0) return
            val kx = W / width.toFloat(); val ky = H / height.toFloat()
            val ax = dx0 * kx; val ay = dy0 * ky; val bxx = dx1 * kx; val byy = dy1 * ky
            p.color = Color.rgb(0xff, 0xd2, 0x7d)
            for (i in 1..10) { val t = i / 10f; if (i % 2 == 0) g.drawRect(ax + (bxx - ax) * t - 1, ay + (byy - ay) * t - 1, ax + (bxx - ax) * t + 1, ay + (byy - ay) * t + 1, p) }
            val pow = ((dy0 - dy1) / (height * 0.5f)).coerceIn(0f, 1f)
            p.color = Color.argb(160, 0, 0, 0); g.drawRect(8f, H - 16f, 8f + 60f, H - 10f, p)
            p.color = if (pow > 0.85f) Color.rgb(0xff, 0x4d, 0x6d) else Color.rgb(0x7c, 0xff, 0x6b); g.drawRect(9f, H - 15f, 9f + 58f * pow, H - 11f, p)
            // Aim guide upgrade: dotted arc (level 3 includes the wind)
            val aim = lvl("aim")
            if (aim > 0 && dy0 - dy1 > height * 0.05f) {
                val v = launch(dx0, dy0, dx1, dy1, System.currentTimeMillis() - downT)
                var x = 0f; var z = 0f; var h = 0.35f; var vxx = v[0]; val vzz = v[1]; var vhh = v[2]
                val steps = when (aim) { 1 -> 14; 2 -> 28; else -> 70 }
                val d = 1f / 60f
                for (i in 0 until steps) {
                    vhh -= 9.8f * d; if (aim >= 3) vxx += wind * d
                    x += vxx * d; z += vzz * d; h += vhh * d
                    if (h < 0f) break
                    if (i % 3 == 0) { p.color = Color.argb(200 - i * 2, 0xff, 0xff, 0xff); val px = sx(x, z); val py = sy(h, z); g.drawRect(px, py, px + 1.5f, py + 1.5f, p) }
                }
            }
        }

        // ---- text helpers ----
        private fun t(g: Canvas, s: String, x: Float, y: Float, col: Int, size: Float = 11f, align: Paint.Align = Paint.Align.LEFT) {
            txt.textSize = size; txt.textAlign = align
            txt.color = Color.BLACK; g.drawText(s, x + 1, y + 1, txt)
            txt.color = col; g.drawText(s, x, y, txt)
        }
        private fun button(g: Canvas, label: String, x: Float, y: Float, w: Float, h: Float, col: Int, enabled: Boolean = true, onTap: () -> Unit) {
            val r = RectF(x, y, x + w, y + h)
            p.color = Color.argb(200, 8, 6, 20); g.drawRect(x + 2, y + 2, x + w + 2, y + h + 2, p)
            p.color = if (enabled) col else Color.rgb(0x3a, 0x3a, 0x4a); g.drawRect(r, p)
            p.color = Color.argb(70, 255, 255, 255); g.drawRect(x, y, x + w, y + 2, p)
            p.color = Color.argb(70, 0, 0, 0); g.drawRect(x, y + h - 2, x + w, y + h, p)
            t(g, label, x + w / 2, y + h / 2 + 4, if (enabled) Color.WHITE else Color.rgb(0x88, 0x88, 0x99), 11f, Paint.Align.CENTER)
            if (enabled) buttons += r to onTap
        }
        private fun panel(g: Canvas, x: Float, y: Float, w: Float, h: Float) {
            p.color = Color.argb(215, 10, 8, 26); g.drawRect(x, y, x + w, y + h, p)
            p.color = Color.rgb(0xff, 0x3e, 0xa5); g.drawRect(x, y, x + w, y + 1, p); g.drawRect(x, y + h - 1, x + w, y + h, p)
            p.color = Color.rgb(0x2e, 0xf2, 0xff); g.drawRect(x, y, x + 1, y + h, p); g.drawRect(x + w - 1, y, x + w, y + h, p)
        }
        private fun drawClose(g: Canvas) {
            closeR.set(W - 24f, 4f, W - 4f, 24f)
            p.color = Color.rgb(0xcc, 0x00, 0x33); g.drawRect(closeR, p)
            t(g, "X", W - 17.5f, 18f, Color.WHITE)
        }

        // ---- screens ----
        private fun drawTitle(g: Canvas) {
            p.color = Color.argb(120, 0, 0, 0); g.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
            val bob = sin(clock * 2f) * 3f
            t(g, "PAPER", W / 2f, H * 0.17f + bob, Color.rgb(0xff, 0x3e, 0xa5), 30f, Paint.Align.CENTER)
            t(g, "TOSS", W / 2f, H * 0.17f + 30 + bob, Color.rgb(0x2e, 0xf2, 0xff), 30f, Paint.Align.CENTER)
            t(g, "DELUXE", W / 2f, H * 0.17f + 46 + bob, Color.rgb(0xff, 0xd2, 0x3f), 11f, Paint.Align.CENTER)
            // a spinning wad
            val cx = W / 2f; val cy = H * 0.42f; val r = 14f
            ellipse(g, cx, cy, r, r, Color.rgb(0xe5, 0xe7, 0xeb)); ellipse(g, cx + 3, cy + 3, r * 0.7f, r * 0.7f, Color.rgb(0xc4, 0xc8, 0xd0)); ellipse(g, cx - 3, cy - 4, r * 0.45f, r * 0.4f, Color.rgb(0xf8, 0xfa, 0xfc))
            p.color = Color.rgb(0x9c, 0xa3, 0xaf)
            for (i in 0 until 5) { val a = clock * 3f + i * 1.3f; g.drawRect(cx + cos(a) * 7, cy + sin(a) * 7, cx + cos(a) * 7 + 4, cy + sin(a) * 7 + 1, p) }
            t(g, "BEST $best", W / 2f, H * 0.52f, Color.WHITE, 12f, Paint.Align.CENTER)
            t(g, "COINS $coins", W / 2f, H * 0.52f + 14, Color.rgb(0xff, 0xd2, 0x3f), 11f, Paint.Align.CENTER)
            val bw = 140f; val bx0 = (W - bw) / 2
            button(g, "PLAY", bx0, H * 0.6f, bw, 26f, Color.rgb(0xcc, 0x22, 0x77)) { startRun() }
            button(g, "UPGRADES", bx0, H * 0.6f + 34, bw, 22f, Color.rgb(0x1d, 0x4e, 0xd8)) { screen = 2; app.sfx("ui") }
            button(g, "STATS", bx0, H * 0.6f + 64, bw, 22f, Color.rgb(0x33, 0x41, 0x55)) { screen = 4; app.sfx("ui") }
            t(g, "flick up to throw · mind the wind", W / 2f, H - 12f, Color.rgb(0xb9, 0xb4, 0xe6), 8f, Paint.Align.CENTER)
        }

        private fun drawShop(g: Canvas) {
            p.color = Color.argb(150, 0, 0, 0); g.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
            t(g, "UPGRADES", 8f, 18f, Color.rgb(0xff, 0x77, 0xee), 14f)
            t(g, "COINS $coins", 8f, 32f, Color.rgb(0xff, 0xd2, 0x3f), 10f)
            val rowH = min(38f, (H - 90f) / UPGRADES.size)
            var y = 40f
            for (u in UPGRADES) {
                val l = lvl(u.id); val maxed = l >= u.max; val cost = u.cost(l)
                panel(g, 6f, y, W - 12f, rowH - 4)
                t(g, u.name, 12f, y + 12, Color.WHITE, 9.5f)
                t(g, u.desc, 12f, y + 23, Color.rgb(0xb9, 0xb4, 0xe6), 7.5f)
                for (i in 0 until u.max) { p.color = if (i < l) Color.rgb(0x7c, 0xff, 0x6b) else Color.rgb(0x3a, 0x3a, 0x4a); g.drawRect(12f + i * 8, y + rowH - 12, 18f + i * 8, y + rowH - 8, p) }
                val label = if (maxed) "MAX" else "$cost"; val rowY = y
                button(g, label, W - 64f, y + (rowH - 4) / 2 - 9, 52f, 18f, Color.rgb(0x0f, 0x76, 0x6e), !maxed && coins >= cost) {
                    coins -= cost
                    prefs.edit().putInt("paperToss.coins", coins).putInt("paperToss.up.${u.id}", l + 1).apply()
                    burst(W - 38f, rowY + rowH / 2, 14, 0, 1.6f); app.sfx("ps1Button")
                }
                y += rowH
            }
            button(g, "BACK", (W - 100f) / 2, H - 40f, 100f, 24f, Color.rgb(0x33, 0x41, 0x55)) { screen = 0; app.sfx("ui") }
        }

        private fun drawStats(g: Canvas) {
            p.color = Color.argb(150, 0, 0, 0); g.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
            t(g, "STATS", 8f, 18f, Color.rgb(0x2e, 0xf2, 0xff), 14f)
            panel(g, 8f, 30f, W - 16f, 150f)
            val rows = listOf(
                "BEST SCORE" to "$best",
                "HIGHEST STAGE" to "${prefs.getInt("paperToss.topStage", 0)}",
                "LONGEST STREAK" to "${prefs.getInt("paperToss.longest", 0)}",
                "BASKETS" to "${prefs.getInt("paperToss.baskets", 0)}",
                "SWISHES" to "${prefs.getInt("paperToss.swishes", 0)}",
                "RUNS PLAYED" to "${prefs.getInt("paperToss.games", 0)}",
                "COINS" to "$coins"
            )
            var y = 50f
            for ((a, b) in rows) { t(g, a, 16f, y, Color.rgb(0xb9, 0xb4, 0xe6), 9.5f); t(g, b, W - 16f, y, Color.WHITE, 10f, Paint.Align.RIGHT); y += 19f }
            button(g, "BACK", (W - 100f) / 2, H - 40f, 100f, 24f, Color.rgb(0x33, 0x41, 0x55)) { screen = 0; app.sfx("ui") }
        }

        private fun drawGameOver(g: Canvas) {
            val a = min(1f, stateT * 2f)
            p.color = Color.argb((150 * a).toInt(), 0, 0, 0); g.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
            panel(g, 20f, H * 0.2f, W - 40f, 190f)
            t(g, "GAME OVER", W / 2f, H * 0.2f + 26, Color.rgb(0xff, 0x4d, 0x6d), 20f, Paint.Align.CENTER)
            t(g, "SCORE $score", W / 2f, H * 0.2f + 52, Color.WHITE, 14f, Paint.Align.CENTER)
            if (newBest && sin(clock * 8f) > -0.3f) t(g, "NEW BEST!", W / 2f, H * 0.2f + 68, Color.rgb(0xff, 0xd2, 0x3f), 11f, Paint.Align.CENTER)
            t(g, "STAGE $stage  ·  $baskets IN  ·  $runSwishes SWISH", W / 2f, H * 0.2f + 86, Color.rgb(0xb9, 0xb4, 0xe6), 8f, Paint.Align.CENTER)
            t(g, "+$coinsEarned COINS", W / 2f, H * 0.2f + 104, Color.rgb(0xff, 0xd2, 0x3f), 12f, Paint.Align.CENTER)
            if (stateT > 0.6f) {
                button(g, "AGAIN", 34f, H * 0.2f + 120, W - 68f, 24f, Color.rgb(0xcc, 0x22, 0x77)) { startRun() }
                button(g, "MENU", 34f, H * 0.2f + 152, W - 68f, 22f, Color.rgb(0x33, 0x41, 0x55)) { toTitle(); app.sfx("ui") }
            }
        }

        private fun drawHud(g: Canvas) {
            p.color = Color.argb(160, 10, 6, 24); g.drawRect(0f, 0f, W.toFloat(), 30f, p)
            t(g, "$score", 6f, 14f, Color.WHITE, 13f)
            t(g, "STAGE $stage", 6f, 26f, Color.rgb(0xff, 0x77, 0xee), 8.5f)
            // strikes left, as little paper wads
            for (i in 0 until maxLives) {
                val cx = 90f + i * 11f
                ellipse(g, cx, 12f, 4f, 4f, if (i < lives) Color.rgb(0xe5, 0xe7, 0xeb) else Color.rgb(0x3a, 0x3a, 0x4a))
                if (i >= lives) { p.color = Color.rgb(0xff, 0x4d, 0x6d); g.drawRect(cx - 4, 11.5f, cx + 4, 12.5f, p) }
            }
            // combo
            if (streak > 0) {
                val m = multiplier() * (if (onFire()) 2 else 1)
                t(g, "x$m  STREAK $streak", 86f, 26f, if (onFire()) Color.rgb(0xff, 0x9f, 0x1c) else Color.rgb(0x7d, 0xd3, 0xfc), 8.5f)
            }
            t(g, "BEST $best", W - 30f, 26f, Color.rgb(0xb9, 0xb4, 0xe6), 8f, Paint.Align.RIGHT)
            if (screen != 1) return
            // wind: arrow + number
            val wy = 44f
            val dir = if (wind < 0) "<" else if (wind > 0) ">" else "-"
            val arrows = dir.repeat(min(4, max(1, (abs(wind) * 2).toInt() + if (wind == 0f) 0 else 1)))
            t(g, "WIND ${"%.1f".format(java.util.Locale.US, abs(wind))} $arrows${if (gusts) " GUSTY" else ""}", W / 2f, wy, if (abs(wind) > 1.2f) Color.rgb(0xff, 0x9f, 0x43) else Color.rgb(0x7d, 0xd3, 0xfc), 11f, Paint.Align.CENTER)
            if (fanDrift() > 0f) t(g, "ceiling fan on: drift!", W / 2f, wy + 12f, Color.rgb(0xb9, 0xb4, 0xe6), 9f, Paint.Align.CENTER)
            if (golden && state == 0) t(g, "GOLDEN WAD x3", W / 2f, wy + 24f, Color.rgb(0xff, 0xd7, 0x3a), 10f, Paint.Align.CENTER)
            t(g, "${"%.1f".format(java.util.Locale.US, binZ)} m", sx(binX, binZ), sy(0f, binZ) + 12f, Color.rgb(0xb9, 0xb4, 0xe6), 9f, Paint.Align.CENTER)
            for (q in popups) {
                val a = (q.life / 1.2f).coerceIn(0f, 1f)
                t(g, q.text, q.x, q.y, Color.argb((255 * min(1f, a * 2f)).toInt(), Color.red(q.col), Color.green(q.col), Color.blue(q.col)), 12f, Paint.Align.CENTER)
            }
            if (stageBannerT > 0f) {
                val s = 22f + max(0f, stageBannerT - 1.8f) * 30f
                t(g, stageBanner, W / 2f, horizon * 0.42f, Color.rgb(0xff, 0x3e, 0xa5), s, Paint.Align.CENTER)
            }
            if (banner.isNotEmpty() && bannerT > 0f) {
                val pop = 16f + max(0f, bannerT - 0.85f) * 24f
                val by = horizon * 0.72f + (if (state == 2) -stateT * 10f else 0f)
                t(g, banner, W / 2f, by, bannerCol, pop, Paint.Align.CENTER)
            }
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            val kx = W / width.toFloat(); val ky = H / height.toFloat()
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val lx = e.x * kx; val ly = e.y * ky
                    if (closeR.contains(lx, ly)) { close(); return true }
                    for ((r, f) in buttons) if (r.contains(lx, ly)) { f(); return true }
                    if (screen == 1 && state == 0) { dragging = true; dx0 = e.x; dy0 = e.y; dx1 = e.x; dy1 = e.y; downT = System.currentTimeMillis() }
                }
                MotionEvent.ACTION_MOVE -> if (dragging) { dx1 = e.x; dy1 = e.y }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) {
                    dragging = false; dx1 = e.x; dy1 = e.y
                    if (screen == 1 && state == 0 && dy0 - dy1 > height * 0.05f) {
                        val v = launch(dx0, dy0, dx1, dy1, System.currentTimeMillis() - downT)
                        vx = v[0]; vz = v[1]; vh = v[2]
                        state = 1; stateT = 0f; rimHit = false; boxHit = false; trailN = 0
                        if (bannerT > 50f) bannerT = 0f
                        app.sfx("ui")
                    }
                }
            }
            return true
        }
    }
}
