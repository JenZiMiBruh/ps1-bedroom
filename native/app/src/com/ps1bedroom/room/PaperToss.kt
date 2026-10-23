package com.ps1bedroom.room

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * PAPER TOSS (v0.48): flick crumpled paper into the wastebasket, PS1-style. Drawn into a small low-res bitmap and
 * blown up with no smoothing so it looks like a real 1998 mini-game. A desk fan blows a different wind every throw
 * (and the ceiling fan adds a little drift when it's spinning); the basket moves further away the longer your streak.
 * Swish (no rim) scores a bonus. Your best is remembered.
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

    fun open() { view.visibility = View.VISIBLE; view.bringToFront(); view.reset(true); view.postInvalidateOnAnimation() }
    fun close() { view.visibility = View.GONE }

    private inner class GameView : View(act) {
        // ---- low-res frame ----
        private var W = 240; private var H = 400
        private var frame: Bitmap? = null
        private var fc: Canvas? = null
        private val p = Paint().apply { isAntiAlias = false }
        private val txt = Paint().apply { isAntiAlias = false; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); color = Color.WHITE }
        private val blit = Paint().apply { isFilterBitmap = false }
        private val dstR = RectF(); private val srcR = Rect()

        // ---- world ----
        private val camH = 1.0f; private val zc = 1.2f
        private var horizon = 150f; private var k = 200f
        private val rimR = 0.19f; private val rimH = 0.42f; private val ballR = 0.065f
        private var binX = 0f; private var binZ = 3f
        private var bx = 0f; private var bz = 0f; private var bh = 0.35f
        private var vx = 0f; private var vz = 0f; private var vh = 0f
        private var spin = 0f
        private var wind = 0f
        private var state = 0       // 0 ready · 1 flying · 2 scored · 3 missed
        private var stateT = 0f
        private var rimHit = false
        private var score = 0; private var streak = 0; private var best = prefs.getInt("paperToss.best", 0); private var throws = 0
        private var banner = ""; private var bannerCol = Color.WHITE
        private var lastNs = 0L
        // ---- touch ----
        private var dragging = false; private var dx0 = 0f; private var dy0 = 0f; private var dx1 = 0f; private var dy1 = 0f; private var downT = 0L
        private val closeR = RectF()

        fun reset(newGame: Boolean) {
            if (newGame) { score = 0; streak = 0; throws = 0; banner = "FLICK UP TO THROW"; bannerCol = Color.rgb(0xff, 0xd2, 0x7d) }
            binZ = 2.6f + min(streak, 12) * 0.28f
            binX = (rnd.nextFloat() - 0.5f) * min(0.9f, 0.2f + streak * 0.08f)
            val maxW = min(2.2f, 0.25f + streak * 0.18f)
            wind = ((rnd.nextFloat() * 2f - 1f) * maxW * 10f).toInt() / 10f
            bx = 0f; bz = 0f; bh = 0.35f; vx = 0f; vz = 0f; vh = 0f; spin = 0f
            state = 0; stateT = 0f; rimHit = false
        }

        private fun fanDrift(): Float = if (app.isBuilt && app.fan.fanSpinning) 0.25f else 0f

        // ---- projection ----
        private fun sx(x: Float, z: Float) = W / 2f + x * k / (z + zc)
        private fun sy(h: Float, z: Float) = horizon + (camH - h) * k / (z + zc)
        private fun sc(z: Float) = k / (z + zc)

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            super.onSizeChanged(w, h, ow, oh)
            if (w <= 0 || h <= 0) return
            W = 240; H = max(140, (240f * h / w).toInt())
            frame?.recycle()
            frame = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888); fc = Canvas(frame!!)
            horizon = H * 0.36f
            k = (H * 0.93f - horizon) * zc / camH
        }

        private fun step(dt: Float) {
            stateT += dt
            when (state) {
                1 -> {
                    val sub = 4; val d = dt / sub
                    repeat(sub) {
                        if (state != 1) return@repeat
                        vh -= 9.8f * d
                        vx += (wind + fanDrift() * sin(stateT * 6f)) * d
                        val ph = bh
                        bx += vx * d; bz += vz * d; bh += vh * d
                        spin += d * 12f
                        val ddx = bx - binX; val ddz = bz - binZ; val dist = hypot(ddx, ddz)
                        // Dropping through the opening
                        if (ph >= rimH && bh < rimH && vh < 0) {
                            if (dist < rimR - ballR * 0.6f) { scored(); return@repeat }
                            if (dist < rimR + ballR) {
                                // Rim: kicked out and up
                                rimHit = true
                                val nx = ddx / max(0.001f, dist); val nz = ddz / max(0.001f, dist)
                                vx = nx * 1.4f + vx * 0.2f; vz = nz * 1.4f + vz * 0.15f; vh = 1.6f; bh = rimH + 0.01f
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
                        if (bz < -0.5f || bz > 12f) missed()
                    }
                }
                2 -> { if (bh > 0.08f) bh -= dt * 1.2f; if (stateT > 1.1f) reset(false) }
                3 -> {
                    // A little roll along the carpet
                    bx += vx * dt * 0.3f; bz += vz * dt * 0.3f; vx *= 0.92f; vz *= 0.92f
                    if (stateT > 1.0f) reset(false)
                }
            }
        }

        private fun scored() {
            state = 2; stateT = 0f; throws++
            val swish = !rimHit
            val pts = (if (swish) 2 else 1) + (if (abs(wind) >= 1.2f) 1 else 0)
            score += pts; streak++
            banner = when { swish && streak >= 5 -> "SWISH x$streak!"; swish -> "SWISH! +$pts"; else -> "IN OFF THE RIM +$pts" }
            bannerCol = if (swish) Color.rgb(0x7c, 0xff, 0x6b) else Color.rgb(0x2e, 0xf2, 0xff)
            bx = binX; bz = binZ
            if (score > best) { best = score; prefs.edit().putInt("paperToss.best", best).apply() }
            app.sfx("ps1Button")
            if (streak == 10) app.toast("🗑️ PAPER TOSS: 10 in a row! The wastebasket fears you.")
        }
        private fun missed() {
            if (state != 1) return
            state = 3; stateT = 0f; throws++
            banner = if (rimHit) "RIMMED OUT!" else listOf("MISS!", "SO CLOSE", "AIRBALL", "CHECK THE WIND").random()
            bannerCol = Color.rgb(0xff, 0x4d, 0x6d)
            streak = 0
            app.sfx("caseClick")
        }

        // ---- drawing ----
        override fun onDraw(c: Canvas) {
            val now = System.nanoTime()
            val dt = if (lastNs == 0L) 0f else min(0.05f, (now - lastNs) / 1e9f)
            lastNs = now
            step(dt)
            val g = fc ?: return
            drawRoom(g)
            val inBin = state == 2
            if (inBin) { drawBinBack(g); drawBall(g); drawBinFront(g) }
            else if (bz > binZ) { drawShadow(g); drawBall(g); drawBinBack(g); drawBinFront(g) }
            else { drawBinBack(g); drawBinFront(g); drawShadow(g); drawBall(g) }
            drawAim(g)
            drawHud(g)
            // blow it up, letterboxed, pixel-sharp
            srcR.set(0, 0, W, H); dstR.set(0f, 0f, width.toFloat(), height.toFloat())
            c.drawColor(Color.BLACK)
            c.drawBitmap(frame!!, srcR, dstR, blit)
            // scanlines
            p.color = Color.argb(28, 0, 0, 0)
            var y = 0f; val step = max(2f, height / H.toFloat())
            while (y < height) { c.drawRect(0f, y, width.toFloat(), y + step * 0.4f, p); y += step }
            if (isOpen) postInvalidateOnAnimation()
        }

        private fun drawRoom(g: Canvas) {
            // Wall (the bedroom's purple, dithered) and the carpet floor in perspective
            p.color = Color.rgb(0x2a, 0x1f, 0x4a); g.drawRect(0f, 0f, W.toFloat(), horizon, p)
            p.color = Color.rgb(0x33, 0x27, 0x5a)
            var y = 0; while (y < horizon) { var x = (y / 2) % 4; while (x < W) { g.drawRect(x.toFloat(), y.toFloat(), x + 1f, y + 1f, p); x += 4 }; y += 2 }
            // A poster and the window glow on the wall
            p.color = Color.rgb(0xff, 0x3e, 0xa5); g.drawRect(W * 0.08f, horizon * 0.25f, W * 0.26f, horizon * 0.7f, p)
            p.color = Color.rgb(0x1a, 0x10, 0x30); g.drawRect(W * 0.1f, horizon * 0.3f, W * 0.24f, horizon * 0.62f, p)
            p.color = Color.rgb(0x2e, 0xf2, 0xff); g.drawRect(W * 0.13f, horizon * 0.42f, W * 0.21f, horizon * 0.5f, p)
            p.color = Color.rgb(0x10, 0x16, 0x34); g.drawRect(W * 0.62f, horizon * 0.18f, W * 0.92f, horizon * 0.78f, p)
            p.color = Color.rgb(0x5b, 0x6a, 0xa8); g.drawRect(W * 0.765f, horizon * 0.18f, W * 0.775f, horizon * 0.78f, p); g.drawRect(W * 0.62f, horizon * 0.47f, W * 0.92f, horizon * 0.49f, p)
            // baseboard
            p.color = Color.rgb(0xd8, 0xd0, 0xe8); g.drawRect(0f, horizon - 3, W.toFloat(), horizon, p)
            // carpet
            p.color = Color.rgb(0x3b, 0x2c, 0x52); g.drawRect(0f, horizon, W.toFloat(), H.toFloat(), p)
            p.color = Color.rgb(0x46, 0x35, 0x62)
            for (i in -8..8) { val x0 = W / 2f + i * 0.5f * sc(0f); val x1 = W / 2f + i * 0.5f * sc(30f); line(g, x0, sy(0f, 0f), x1, sy(0f, 30f)) }
            var z = 0.5f; while (z < 14f) { val yy = sy(0f, z); g.drawRect(0f, yy, W.toFloat(), yy + 1f, p); z += 0.5f + z * 0.15f }
        }
        private fun line(g: Canvas, x0: Float, y0: Float, x1: Float, y1: Float) {
            val n = max(abs(x1 - x0), abs(y1 - y0)).toInt().coerceAtMost(800)
            for (i in 0..n) { val t = i / max(1f, n.toFloat()); g.drawRect(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, x0 + (x1 - x0) * t + 1, y0 + (y1 - y0) * t + 1, p) }
        }
        private fun ellipse(g: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, col: Int) { p.color = col; g.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, p) }

        private fun drawBinBack(g: Canvas) {
            val s = sc(binZ); val cx = sx(binX, binZ)
            ellipse(g, cx, sy(0f, binZ) + 1, rimR * s * 1.15f, rimR * s * 0.3f, Color.argb(110, 0, 0, 0)) // shadow
            ellipse(g, cx, sy(rimH, binZ), rimR * s, rimR * s * 0.32f, Color.rgb(0x0b, 0x0f, 0x18))       // the dark opening
            // paper already inside
            ellipse(g, cx - rimR * s * 0.3f, sy(rimH, binZ) + 1, rimR * s * 0.3f, rimR * s * 0.12f, Color.rgb(0xc8, 0xcc, 0xd4))
            ellipse(g, cx + rimR * s * 0.35f, sy(rimH, binZ), rimR * s * 0.25f, rimR * s * 0.1f, Color.rgb(0xe5, 0xe7, 0xeb))
        }
        private fun drawBinFront(g: Canvas) {
            val s = sc(binZ); val cx = sx(binX, binZ)
            val topY = sy(rimH, binZ); val botY = sy(0f, binZ)
            val rt = rimR * s; val rb = rimR * 0.76f * s
            val path = Path().apply {
                moveTo(cx - rt, topY); lineTo(cx + rt, topY); lineTo(cx + rb, botY); lineTo(cx - rb, botY); close()
            }
            p.color = Color.rgb(0x33, 0x41, 0x55); g.drawPath(path, p)
            // mesh stripes
            p.color = Color.rgb(0x47, 0x55, 0x69)
            for (i in -3..3) { val f = i / 4f; line(g, cx + rt * f, topY + 2, cx + rb * f, botY - 1) }
            p.color = Color.rgb(0x1e, 0x29, 0x3b); g.drawRect(cx - rb, botY - 2, cx + rb, botY, p)
            // front rim lip
            p.color = Color.rgb(0x94, 0xa3, 0xb8); p.style = Paint.Style.STROKE; p.strokeWidth = max(1f, s * 0.02f)
            g.drawArc(cx - rt, topY - rt * 0.32f, cx + rt, topY + rt * 0.32f, 0f, 180f, false, p)
            p.style = Paint.Style.FILL
        }
        private fun drawShadow(g: Canvas) {
            if (state == 2) return
            val s = sc(bz); ellipse(g, sx(bx, bz), sy(0f, bz), ballR * s * 1.2f, ballR * s * 0.35f, Color.argb(90, 0, 0, 0))
        }
        private fun drawBall(g: Canvas) {
            val s = sc(bz); val cx = sx(bx, bz); val cy = sy(bh, bz); val r = max(2f, ballR * s)
            ellipse(g, cx, cy, r, r, Color.rgb(0xe5, 0xe7, 0xeb))
            ellipse(g, cx + r * 0.25f, cy + r * 0.25f, r * 0.7f, r * 0.7f, Color.rgb(0xc4, 0xc8, 0xd0))
            ellipse(g, cx - r * 0.2f, cy - r * 0.25f, r * 0.5f, r * 0.45f, Color.rgb(0xf8, 0xfa, 0xfc))
            // crumple creases that turn with the spin
            p.color = Color.rgb(0x9c, 0xa3, 0xaf)
            for (i in 0 until 3) { val a = spin + i * 2.1f; g.drawRect(cx + kotlin.math.cos(a) * r * 0.5f, cy + sin(a) * r * 0.5f, cx + kotlin.math.cos(a) * r * 0.5f + max(1f, r * 0.3f), cy + sin(a) * r * 0.5f + 1f, p) }
        }
        private fun drawAim(g: Canvas) {
            if (!dragging || state != 0) return
            val kx = W / width.toFloat(); val ky = H / height.toFloat()
            val ax = dx0 * kx; val ay = dy0 * ky; val bxx = dx1 * kx; val byy = dy1 * ky
            p.color = Color.rgb(0xff, 0xd2, 0x7d)
            val n = 10
            for (i in 1..n) { val t = i / n.toFloat(); if (i % 2 == 0) g.drawRect(ax + (bxx - ax) * t - 1, ay + (byy - ay) * t - 1, ax + (bxx - ax) * t + 1, ay + (byy - ay) * t + 1, p) }
            val pow = powerOf()
            p.color = Color.argb(160, 0, 0, 0); g.drawRect(8f, H - 16f, 8f + 60f, H - 10f, p)
            p.color = if (pow > 0.85f) Color.rgb(0xff, 0x4d, 0x6d) else Color.rgb(0x7c, 0xff, 0x6b); g.drawRect(9f, H - 15f, 9f + 58f * pow, H - 11f, p)
        }
        private fun drawHud(g: Canvas) {
            txt.textSize = 11f
            fun t(s: String, x: Float, y: Float, col: Int, center: Boolean = false) {
                txt.textAlign = if (center) Paint.Align.CENTER else Paint.Align.LEFT
                txt.color = Color.BLACK; g.drawText(s, x + 1, y + 1, txt)
                txt.color = col; g.drawText(s, x, y, txt)
            }
            p.color = Color.argb(150, 10, 6, 24); g.drawRect(0f, 0f, W.toFloat(), 30f, p)
            t("🗑 PAPER TOSS", 6f, 12f, Color.rgb(0xff, 0x77, 0xee))
            t("SCORE $score  STREAK $streak  BEST $best", 6f, 25f, Color.WHITE)
            // close button
            closeR.set(W - 24f, 4f, W - 4f, 24f)
            p.color = Color.rgb(0xcc, 0x00, 0x33); g.drawRect(closeR, p)
            t("X", W - 17f, 18f, Color.WHITE)
            // wind: a little desk fan and an arrow
            val wy = 44f
            val dir = if (wind < 0) "<" else if (wind > 0) ">" else "-"
            val arrows = dir.repeat(min(4, max(1, (abs(wind) * 2).toInt() + if (wind == 0f) 0 else 1)))
            t("WIND ${"%.1f".format(java.util.Locale.US, abs(wind))} $arrows", W / 2f, wy, if (abs(wind) > 1.2f) Color.rgb(0xff, 0x9f, 0x43) else Color.rgb(0x7d, 0xd3, 0xfc), true)
            if (fanDrift() > 0f) t("ceiling fan on: drift!", W / 2f, wy + 12f, Color.rgb(0xb9, 0xb4, 0xe6), true)
            t("${"%.1f".format(java.util.Locale.US, binZ)} m", sx(binX, binZ), sy(0f, binZ) + 12f, Color.rgb(0xb9, 0xb4, 0xe6), true)
            if (banner.isNotEmpty() && (state != 0 || throws == 0)) {
                txt.textSize = 16f
                val by = horizon * 0.62f + (if (state == 2) -stateT * 10f else 0f)
                t(banner, W / 2f, by, bannerCol, true)
            }
        }

        private fun powerOf(): Float {
            val dy = dy0 - dy1
            return (dy / (height * 0.5f)).coerceIn(0f, 1f)
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            val kx = W / width.toFloat(); val ky = H / height.toFloat()
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (closeR.contains(e.x * kx, e.y * ky)) { close(); return true }
                    if (state == 0) { dragging = true; dx0 = e.x; dy0 = e.y; dx1 = e.x; dy1 = e.y; downT = System.currentTimeMillis() }
                }
                MotionEvent.ACTION_MOVE -> if (dragging) { dx1 = e.x; dy1 = e.y }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) {
                    dragging = false; dx1 = e.x; dy1 = e.y
                    val up = dy0 - dy1
                    if (state == 0 && up > height * 0.05f) {
                        val ms = max(60L, System.currentTimeMillis() - downT)
                        // a quick flick adds a bit of zip, like the original
                        val speedBoost = (300f / ms).coerceIn(0.85f, 1.15f)
                        val pw = (powerOf() * speedBoost).coerceIn(0.1f, 1.25f)
                        vh = 3.4f
                        vz = 1.2f + pw * 6.6f
                        vx = ((dx1 - dx0) / width) * vz * 1.1f
                        state = 1; stateT = 0f; rimHit = false; banner = ""
                        app.sfx("ui")
                    }
                }
            }
            return true
        }
    }
}
