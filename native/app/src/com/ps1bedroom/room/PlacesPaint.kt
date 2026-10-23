package com.ps1bedroom.room

import com.ps1bedroom.room.engine.Canvas2D
import com.ps1bedroom.room.engine.makeRng
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * HTML 14244-14773: the 15 places outside the window, painted on the 512 x 256 backdrop (the ground meets it
 * around y = 214): each place's landscape (static, rebuilt with the sky), the side walls of the sky box and the
 * live bits drawn a few times a second (the Ferris wheel, the lighthouse, trains, helicopters, glitter on water).
 */
class PlacesPaint(private val sky: Sky, private val env: Environment, private val places: Places) {
    private val SKY_W = Sky.SKY_W
    private val SKY_H = Sky.SKY_H
    private val rnd = Random.Default

    private fun T(n: IntArray, d: IntArray, hz: Double = 0.0) = sky.T(n, d, hz)
    private fun i3(a: Int, b: Int, c: Int) = intArrayOf(a, b, c)
    private fun isNight() = sky.isNight()
    private fun litWindow(ctx: Canvas2D, x: Number, y: Number, w: Number, h: Number, k: Double, col: String? = null) = sky.litWindow(ctx, x, y, w, h, k, col)

    // ---- Painting helpers ----
    fun seasonGreen(): Pair<IntArray, IntArray> = when (env.currentSeason) {
        "spring" -> i3(14, 34, 20) to i3(86, 150, 66)
        "summer" -> i3(10, 26, 16) to i3(48, 104, 46)
        "fall" -> i3(30, 20, 12) to i3(158, 98, 40)
        "winter" -> i3(44, 50, 66) to i3(222, 230, 240)
        else -> i3(10, 26, 16) to i3(48, 104, 46)
    }
    fun winterNow() = env.currentSeason == "winter" && !places.envDef().noSnow
    private fun snowCap() = if (winterNow()) "rgba(236,242,250,0.85)" else null
    private fun ridge(ctx: Canvas2D, pts: List<DoubleArray>, fill: String) {
        ctx.fillStyle = fill
        ctx.beginPath(); ctx.moveTo(-20, SKY_H)
        for (p in pts) ctx.lineTo(p[0], p[1])
        ctx.lineTo(SKY_W + 20, SKY_H); ctx.closePath(); ctx.fill()
    }
    private fun ridgePts(seed: Int, base: Double, amp: Double, step: Double, jag: Double): List<DoubleArray> {
        val r = makeRng(seed); val pts = ArrayList<DoubleArray>()
        var x = -20.0
        while (x <= SKY_W + 20) {
            pts += doubleArrayOf(x, base - amp * (0.5 + 0.5 * sin(x * 0.013 + seed)) - (r() - 0.5) * jag)
            x += step * (0.6 + r() * 0.8)
        }
        return pts
    }
    private fun pines(ctx: Canvas2D, seed: Int, base: Double, hMin: Double, hMax: Double, gap: Double, col: String, snow: String?) {
        val r = makeRng(seed)
        var x = -10.0
        while (x < SKY_W + 10) {
            val ph = hMin + r() * (hMax - hMin); val pw = ph * (0.34 + r() * 0.1); val by = base + r() * 4
            ctx.fillStyle = col
            ctx.beginPath(); ctx.moveTo(x, by - ph); ctx.lineTo(x + pw / 2, by); ctx.lineTo(x - pw / 2, by); ctx.closePath(); ctx.fill()
            if (snow != null) { ctx.fillStyle = snow; ctx.beginPath(); ctx.moveTo(x, by - ph); ctx.lineTo(x + pw * 0.16, by - ph * 0.62); ctx.lineTo(x - pw * 0.16, by - ph * 0.62); ctx.closePath(); ctx.fill() }
            x += gap * (0.5 + r())
        }
    }
    private fun blobTrees(ctx: Canvas2D, seed: Int, base: Double, sMin: Double, sMax: Double, gap: Double, col: String, bare: Boolean) {
        val r = makeRng(seed)
        var x = -10.0
        while (x < SKY_W + 10) {
            val s = sMin + r() * (sMax - sMin)
            ctx.fillStyle = col
            ctx.fillRect(x - 1, base - s * 0.6, 2, s * 0.6)
            if (!bare) { ctx.beginPath(); ctx.ellipse(x, base - s * 0.9, s * 0.55, s * 0.5, 0, 0, PI * 2); ctx.fill() }
            else { ctx.fillRect(x - s * 0.3, base - s * 1.1, 1, s * 0.5); ctx.fillRect(x + s * 0.25, base - s, 1, s * 0.4); ctx.fillRect(x - s * 0.3, base - s * 1.1, s * 0.55, 1) }
            x += gap * (0.5 + r())
        }
    }
    private fun bareTrees() = env.currentSeason == "winter" && !places.envDef().noSnow
    private fun palmSil(ctx: Canvas2D, x: Double, base: Double, ph: Double, col: String) {
        ctx.fillStyle = col; ctx.strokeStyle = col; ctx.lineWidth = 1.5f
        ctx.beginPath(); ctx.moveTo(x, base); ctx.quadraticCurveTo(x + 3, base - ph * 0.5, x + 1, base - ph); ctx.stroke()
        for (i in 0 until 7) {
            val a = -PI / 2 + (i - 3) * 0.5
            ctx.beginPath(); ctx.moveTo(x + 1, base - ph)
            ctx.quadraticCurveTo(x + 1 + cos(a) * 9, base - ph + sin(a) * 6, x + 1 + cos(a) * 14, base - ph + 5 + abs(cos(a)) * 4); ctx.stroke()
        }
    }
    private fun saguaro(ctx: Canvas2D, x: Double, base: Double, ch: Double, col: String) {
        ctx.fillStyle = col; ctx.fillRect(x, base - ch, 4, ch)
        ctx.fillRect(x - 5, base - ch * 0.6, 5, 3); ctx.fillRect(x - 5, base - ch * 0.85, 3, ch * 0.28)
        ctx.fillRect(x + 4, base - ch * 0.5, 5, 3); ctx.fillRect(x + 7, base - ch * 0.75, 3, ch * 0.28)
    }
    private fun powerLine(ctx: Canvas2D, y: Double, col: String) {
        ctx.strokeStyle = col; ctx.lineWidth = 1f
        ctx.beginPath(); ctx.moveTo(0, y); ctx.quadraticCurveTo(128, y + 6, 256, y); ctx.quadraticCurveTo(384, y + 6, 512, y); ctx.stroke()
        for (px in intArrayOf(0, 256, 512)) { ctx.fillStyle = col; ctx.fillRect(px - 1, y - 2, 2, 44); ctx.fillRect(px - 6, y, 12, 1) }
    }
    private fun paintWater(ctx: Canvas2D, top: Double, dayTop: IntArray, dayBot: IntArray) {
        val g = ctx.createLinearGradient(0, top, 0, SKY_H)
        g.addColorStop(0, T(i3(12, 22, 44), dayTop, 0.25)); g.addColorStop(1, T(i3(4, 8, 18), dayBot))
        ctx.fillStyle = g; ctx.fillRect(0, top, SKY_W, SKY_H - top)
        ctx.fillStyle = "rgba(255,255,255,${0.12 + 0.2 * env.daylight})"; ctx.fillRect(0, top, SKY_W, 1)
    }
    private fun glitter(ctx: Canvas2D, top: Double, t: Float, calm: Boolean) {
        val W = env.W
        val bodyX: Float; val bodyC: IntArray
        if (env.sunDeg > -2 && sky.sunX > -90) { bodyX = sky.sunX; bodyC = i3(255, 220, 160) }
        else if (sky.moonX > -90 && env.moonEl > 0) { bodyX = sky.moonX; bodyC = i3(210, 225, 255) }
        else return
        if (W.cloud > 0.85f) return
        val r = makeRng(floor(t * 3.0))
        val str = (if (bodyC[2] > 200) 0.35 + 0.45 * env.moonIllum else 0.8) * (1 - W.cloud * 0.8)
        var y = top + 2
        while (y < SKY_H) {
            val spread = 4 + (y - top) * (if (calm) 0.25 else 0.55)
            for (k in 0 until (if (calm) 1 else 2)) {
                if (r() > 0.75) continue
                ctx.fillStyle = sky.rgbC(bodyC, str * (0.4 + r() * 0.6))
                ctx.fillRect(bodyX + (r() - 0.5) * spread * 2, y, 2 + r() * (if (calm) 10 else 6), 1)
            }
            y += if (calm) 3 else 2
        }
    }
    private fun house(ctx: Canvas2D, x: Double, base: Double, hw: Double, hh: Double, wallC: IntArray, roofC: IntArray, r: () -> Double, flat: Boolean = false) {
        ctx.fillStyle = T(i3(22, 24, 34), wallC); ctx.fillRect(x, base - hh, hw, hh)
        ctx.fillStyle = T(i3(12, 12, 20), roofC)
        if (flat) ctx.fillRect(x - 1, base - hh - 2, hw + 2, 3)
        else { ctx.beginPath(); ctx.moveTo(x - 3, base - hh); ctx.lineTo(x + hw / 2, base - hh - 10 - r() * 5); ctx.lineTo(x + hw + 3, base - hh); ctx.closePath(); ctx.fill() }
        if (winterNow() && !flat) { ctx.fillStyle = "rgba(240,245,252,0.9)"; ctx.fillRect(x, base - hh - 2, hw, 2) }
        litWindow(ctx, x + 4, base - hh + 4, 5, 4, r()); litWindow(ctx, x + hw - 10, base - hh + 4, 5, 4, r())
        ctx.fillStyle = T(i3(30, 26, 24), i3(120, 90, 70)); ctx.fillRect(x + hw / 2 - 3, base - 7, 6, 7)
        if (isNight()) { ctx.fillStyle = "rgba(255,220,150,0.85)"; ctx.fillRect(x + hw / 2 + 4, base - 6, 1, 1) }
    }
    private fun scaled(c: IntArray, k: Double) = IntArray(3) { (c[it] * k).roundToInt() }

    // ---- The painted places (static part, rebuilt with the sky every few seconds) ----
    fun paintLandscape(ctx: Canvas2D, w: Int, h: Int) {
        val W = env.W
        when (places.outsideEnv) {
            "skyscraper" -> {
                // From way up: the horizon at eye level, other towers at your height, the street grid far below
                val r = makeRng(61)
                ctx.fillStyle = T(i3(10, 14, 28), i3(120, 136, 160), 0.6)
                run { var x = -5.0; while (x < w) { val bh = 4 + r() * 16; ctx.fillRect(x, 150 - bh, 5 + r() * 8, bh); x += 6 + r() * 10 } }
                val ground = ctx.createLinearGradient(0, 150, 0, 256)
                ground.addColorStop(0, T(i3(12, 16, 30), i3(110, 120, 136), 0.55)); ground.addColorStop(1, T(i3(6, 8, 16), i3(70, 76, 86)))
                ctx.fillStyle = ground; ctx.fillRect(0, 150, w, 106)
                // City blocks: rooftops get bigger toward the bottom (closer to the building)
                for (row in 0 until 9) {
                    val y = 152 + row * row * 1.3 + row * 3; val bh = 2 + row * 1.6
                    var x = -10 + (row % 2) * 7
                    while (x < w) {
                        ctx.fillStyle = T(i3(16, 18, 28), i3(90 + x % 40, 96 + x % 30, 110), 0.4 - row * 0.04)
                        ctx.fillRect(x, y, 7 + row * 3, bh)
                        if (isNight() && r() > 0.4) { ctx.fillStyle = "rgba(255,214,140,0.8)"; ctx.fillRect(x + 1 + r() * (5 + row * 2), y + r() * bh, 1, 1) }
                        x += 10 + row * 4
                    }
                }
                // Towers rising past your floor on both sides, with rooftop lights
                arrayOf(intArrayOf(30, 70, 34), intArrayOf(92, 104, 22), intArrayOf(372, 84, 30), intArrayOf(452, 46, 44)).forEachIndexed { i, (x, top, bw) ->
                    ctx.fillStyle = T(i3(8, 12, 24), i3(92, 108, 130), 0.25); ctx.fillRect(x, top, bw, 256 - top)
                    var wy = top + 4
                    while (wy < 250) { var wx = x + 2; while (wx < x + bw - 2) { litWindow(ctx, wx, wy, 2, 2, makeRng(wx * 7 + wy)(), if (isNight()) "rgba(255,224,160,0.85)" else null); wx += 4 }; wy += 5 }
                    ctx.fillStyle = T(i3(6, 8, 14), i3(60, 70, 80)); ctx.fillRect(x + bw / 2 - 1, top - 14 - i * 3, 2, 14 + i * 3)
                }
                if (W.fog > 0.2f) { ctx.fillStyle = "rgba(200,206,220,${W.fog * 0.45})"; ctx.fillRect(0, 120, w, 70) }
            }
            "neon" -> {
                // Dense towers covered in vertical neon signs, an elevated train line
                val r = makeRng(71)
                ctx.fillStyle = T(i3(10, 10, 22), i3(96, 100, 120), 0.55)
                run { var x = -5.0; while (x < w) { val top = 110 + r() * 60; ctx.fillRect(x, top, 12 + r() * 14, 214 - top); x += 12 + r() * 14 } }
                val neonCols = arrayOf("#ff3ea5", "#2ef2ff", "#ffe14d", "#7cff6b", "#b26bff", "#ff6b3d")
                var x = -5.0
                while (x < w) {
                    val bw = 22 + r() * 22; val top = 130 + r() * 50
                    ctx.fillStyle = T(i3(6, 6, 14), i3(70, 70, 86), 0.2); ctx.fillRect(x, top, bw, 230 - top)
                    var wy = top + 4
                    while (wy < 226) { var wx = x + 2; while (wx < x + bw - 2) { litWindow(ctx, wx, wy, 3, 2, r(), "rgba(255,230,190,0.8)"); wx += 5 }; wy += 6 }
                    if (r() > 0.3) {
                        val c = neonCols[floor(r() * neonCols.size).toInt()]; val sx = x + (if (r() > 0.5) 2.0 else bw - 8); val sy = top + 6 + r() * 20; val sh = 20 + r() * 26
                        ctx.fillStyle = if (isNight()) c else T(i3(20, 20, 30), i3(150, 150, 160)); ctx.fillRect(sx, sy, 6, sh)
                        ctx.fillStyle = if (isNight()) "rgba(0,0,0,0.55)" else "rgba(255,255,255,0.4)"
                        var gy = sy + 3; while (gy < sy + sh - 2) { ctx.fillRect(sx + 1, gy, 4, 2); gy += 5 }
                        if (isNight()) { ctx.fillStyle = "${c}33"; ctx.fillRect(sx - 4, sy - 4, 14, sh + 8) }
                    }
                    x += bw + 1
                }
                // The elevated rail line
                ctx.fillStyle = T(i3(8, 8, 12), i3(60, 62, 70)); ctx.fillRect(0, 196, w, 4)
                run { var px = 10; while (px < w) { ctx.fillRect(px, 200, 4, 30); px += 40 } }
                ctx.fillStyle = "rgba(0,0,0,0.25)"; ctx.fillRect(0, 230, w, 26)
            }
            "neighborhood" -> {
                // Brownstones and row houses across a narrow street, fire escapes, the corner store
                val r = makeRng(81)
                ctx.fillStyle = T(i3(12, 14, 24), i3(110, 120, 136), 0.5)
                run { var x = 0.0; while (x < w) { ctx.fillRect(x, 150 + r() * 30, 18 + r() * 10, 80); x += 20 + r() * 20 } }
                val bricks = arrayOf(i3(150, 70, 54), i3(120, 64, 50), i3(168, 92, 70), i3(110, 84, 70), i3(140, 110, 90))
                var x = -6.0
                while (x < w) {
                    val bw = 40 + r() * 18; val top = 140 + r() * 30; val col = bricks[floor(r() * bricks.size).toInt()]
                    ctx.fillStyle = T(i3(20, 14, 16), col); ctx.fillRect(x, top, bw, 236 - top)
                    ctx.fillStyle = T(i3(10, 8, 10), scaled(col, 0.6)); ctx.fillRect(x - 1, top - 3, bw + 2, 3)
                    var wy = top + 7
                    while (wy < 222) { var wx = x + 5; while (wx < x + bw - 6) { litWindow(ctx, wx, wy, 5, 7, r()); wx += 11 }; wy += 13 }
                    // Fire escape zig-zag
                    ctx.strokeStyle = T(i3(4, 4, 6), i3(40, 40, 44)); ctx.lineWidth = 1f
                    ctx.beginPath()
                    var fy = top + 14; var k = 0
                    while (fy < 222) { ctx.moveTo(x + bw * 0.55, fy); ctx.lineTo(x + bw * 0.55 + 16, fy); ctx.lineTo(if (k % 2 == 1) x + bw * 0.55 else x + bw * 0.55 + 16, fy + 13); fy += 13; k++ }
                    ctx.stroke()
                    ctx.fillStyle = T(i3(30, 26, 24), i3(150, 140, 128)); ctx.fillRect(x + 6, 230, 12, 6)
                    x += bw + 2
                }
                // Corner store: striped awning and a glowing OPEN sign
                ctx.fillStyle = T(i3(20, 20, 24), i3(210, 200, 180)); ctx.fillRect(380, 196, 70, 40)
                for (i in 0 until 7) { ctx.fillStyle = if (i % 2 == 1) T(i3(30, 10, 10), i3(200, 40, 40)) else T(i3(40, 40, 40), i3(240, 240, 240)); ctx.fillRect(380 + i * 10, 200, 10, 7) }
                ctx.fillStyle = if (isNight()) "rgba(255,230,170,0.9)" else T(i3(30, 34, 40), i3(150, 170, 180)); ctx.fillRect(386, 212, 58, 18)
                ctx.fillStyle = if (isNight()) "#ff3b6b" else T(i3(40, 20, 20), i3(180, 60, 70)); ctx.font = "bold 9px monospace"; ctx.fillText("OPEN 24H", 394, 224)
                ctx.fillStyle = T(i3(10, 10, 12), i3(60, 60, 64)); for (lx in intArrayOf(60, 250)) ctx.fillRect(lx, 200, 2, 36)
            }
            "suburbs" -> {
                val (gn, gd) = seasonGreen()
                ridge(ctx, ridgePts(5, 196.0, 10.0, 26.0, 4.0), T(i3(14, 20, 34), i3(92, 118, 120), 0.45))
                blobTrees(ctx, 6, 206.0, 10.0, 16.0, 14.0, T(gn, gd, 0.35), bareTrees())
                val tc = T(i3(20, 24, 38), i3(150, 160, 170), 0.3)
                ctx.fillStyle = tc; ctx.fillRect(398, 160, 2, 40); ctx.fillRect(420, 160, 2, 40); ctx.fillRect(398, 178, 24, 1)
                ctx.beginPath(); ctx.ellipse(410, 154, 16, 9, 0, 0, PI * 2); ctx.fill(); ctx.fillRect(394, 154, 32, 6)
                val r = makeRng(12)
                val walls = arrayOf(i3(200, 180, 150), i3(170, 190, 200), i3(210, 200, 170), i3(190, 160, 160), i3(180, 200, 170))
                var x = -12.0
                while (x < w + 10) {
                    val hw = 30 + r() * 18; val hh = 15 + r() * 9
                    house(ctx, x, 226.0, hw, hh, walls[floor(r() * 5).toInt()], i3(110, 70, 60), r)
                    blobTrees(ctx, floor(x).toInt() + 99, 227.0, 9.0, 14.0, 999.0, T(gn, gd, 0.1), bareTrees())
                    x += hw + 8 + r() * 10
                }
                powerLine(ctx, 190.0, T(i3(8, 8, 12), i3(60, 60, 66)))
            }
            "house" -> {
                // Over the back fence: the neighbours' roofs, big trees, a church steeple in the distance
                val (gn, gd) = seasonGreen()
                ridge(ctx, ridgePts(91, 190.0, 8.0, 30.0, 4.0), T(i3(14, 20, 34), i3(100, 124, 130), 0.5))
                ctx.fillStyle = T(i3(16, 18, 26), i3(200, 200, 196), 0.35); ctx.fillRect(300, 150, 10, 46)
                ctx.beginPath(); ctx.moveTo(298, 150); ctx.lineTo(305, 118); ctx.lineTo(312, 150); ctx.closePath(); ctx.fill(); ctx.fillRect(304, 110, 2, 9); ctx.fillRect(301, 113, 8, 2)
                blobTrees(ctx, 92, 210.0, 18.0, 30.0, 30.0, T(gn, gd, 0.25), bareTrees())
                val r = makeRng(93)
                var x = -20.0
                while (x < w) {
                    ctx.fillStyle = T(i3(12, 12, 18), i3(96, 70, 64))
                    ctx.beginPath(); ctx.moveTo(x, 214); ctx.lineTo(x + 30, 194 - r() * 6); ctx.lineTo(x + 60, 214); ctx.closePath(); ctx.fill()
                    if (winterNow()) { ctx.fillStyle = "rgba(240,245,252,0.85)"; ctx.beginPath(); ctx.moveTo(x + 10, 208); ctx.lineTo(x + 30, 196); ctx.lineTo(x + 50, 208); ctx.closePath(); ctx.fill() }
                    if (isNight() && r() > 0.4) { ctx.fillStyle = "rgba(255,210,130,0.85)"; ctx.fillRect(x + 26, 204, 6, 4) }
                    x += 60 + r() * 30
                }
                powerLine(ctx, 176.0, T(i3(8, 8, 12), i3(56, 56, 60)))
            }
            "trailer" -> {
                // Rows of mobile homes, the park's water tower, satellite dishes, scrubby hills, the highway sign
                ridge(ctx, ridgePts(101, 188.0, 16.0, 28.0, 8.0), T(i3(18, 18, 30), i3(150, 136, 120), 0.5))
                blobTrees(ctx, 102, 206.0, 6.0, 10.0, 26.0, T(i3(10, 16, 12), i3(90, 100, 60), 0.3), bareTrees())
                ctx.fillStyle = T(i3(20, 22, 30), i3(170, 176, 180), 0.3); ctx.fillRect(60, 156, 2, 44); ctx.fillRect(84, 156, 2, 44)
                ctx.beginPath(); ctx.ellipse(73, 148, 18, 11, 0, 0, PI * 2); ctx.fill()
                ctx.fillStyle = T(i3(40, 40, 50), i3(70, 90, 140)); ctx.font = "bold 7px monospace"; ctx.fillText("SUNNY ACRES", 51, 151)
                val r = makeRng(103)
                val pals = arrayOf(i3(226, 226, 220), i3(190, 214, 210), i3(230, 214, 180), i3(200, 200, 210))
                for (row in 0 until 2) {
                    var x = -20.0 + row * 25
                    while (x < w) {
                        val base = 214.0 + row * 14; val tw = 44 + r() * 8; val th = 9.0 + row * 2; val col = pals[floor(r() * pals.size).toInt()]
                        ctx.fillStyle = T(i3(24, 26, 32), col); ctx.fillRect(x, base - th, tw, th)
                        ctx.fillStyle = T(i3(20, 40, 50), i3(60, 140, 150)); ctx.fillRect(x, base - th * 0.55, tw, 1.5)
                        ctx.fillStyle = T(i3(10, 10, 12), i3(70, 70, 76)); ctx.fillRect(x - 1, base - th - 1, tw + 2, 1.5); ctx.fillRect(x, base, tw, 2)
                        litWindow(ctx, x + 6, base - th + 3, 5, 3, r(), if (r() > 0.6) "rgba(120,170,255,0.85)" else null); litWindow(ctx, x + tw - 12, base - th + 3, 5, 3, r())
                        if (r() > 0.5) { ctx.fillStyle = T(i3(30, 30, 34), i3(200, 200, 204)); ctx.beginPath(); ctx.ellipse(x + tw * 0.5, base - th - 3, 3, 2.5, 0.5, 0, PI * 2); ctx.fill() }
                        x += 58 + r() * 18
                    }
                }
                ctx.fillStyle = T(i3(10, 10, 12), i3(70, 70, 74)); ctx.fillRect(470, 170, 2, 44)
                ctx.fillStyle = if (isNight()) "rgba(255,90,60,0.95)" else T(i3(40, 20, 10), i3(220, 120, 40)); ctx.fillRect(456, 160, 30, 12)
                ctx.fillStyle = if (isNight()) "#fff" else "#2b1a0a"; ctx.font = "bold 6px monospace"; ctx.fillText("GAS FOOD", 458, 169)
                powerLine(ctx, 182.0, T(i3(8, 8, 12), i3(56, 56, 60)))
            }
            "arizona" -> {
                // A jagged desert mountain range, stucco houses with tile roofs, tall skinny palms and saguaros,
                // and the block walls every backyard here has
                ridge(ctx, ridgePts(111, 168.0, 34.0, 12.0, 30.0), T(i3(30, 20, 42), i3(156, 120, 128), 0.45))
                ridge(ctx, ridgePts(112, 196.0, 10.0, 20.0, 8.0), T(i3(24, 18, 30), i3(176, 140, 110), 0.3))
                val r = makeRng(113)
                run { var x = 0.0; while (x < w) { ctx.fillStyle = T(i3(12, 16, 12), i3(96, 110, 70), 0.2); ctx.beginPath(); ctx.ellipse(x, 208, 3, 2, 0, 0, PI * 2); ctx.fill(); x += 8 + r() * 10 } }
                val stucco = arrayOf(i3(214, 186, 146), i3(226, 196, 170), i3(204, 168, 136), i3(232, 214, 186), i3(210, 160, 140))
                var x = -10.0
                while (x < w) {
                    val hw = 38 + r() * 20; val hh = 12 + r() * 5; val tile = r() > 0.35
                    ctx.fillStyle = T(i3(26, 22, 28), stucco[floor(r() * stucco.size).toInt()]); ctx.fillRect(x, 222 - hh, hw, hh)
                    if (tile) { ctx.fillStyle = T(i3(30, 12, 10), i3(184, 86, 54)); ctx.beginPath(); ctx.moveTo(x - 3, 222 - hh); ctx.lineTo(x + 8, 222 - hh - 7); ctx.lineTo(x + hw - 8, 222 - hh - 7); ctx.lineTo(x + hw + 3, 222 - hh); ctx.closePath(); ctx.fill() }
                    else { ctx.fillStyle = T(i3(20, 18, 20), i3(190, 170, 150)); ctx.fillRect(x - 1, 222 - hh - 2, hw + 2, 2) }
                    ctx.fillStyle = T(i3(14, 14, 18), i3(200, 196, 190)); ctx.fillRect(x + hw * 0.55, 222 - 9, hw * 0.35, 9) // garage
                    litWindow(ctx, x + 5, 222 - hh + 4, 6, 4, r())
                    x += hw + 10 + r() * 14
                }
                val palmC = T(i3(8, 10, 10), i3(70, 90, 50))
                for (p in arrayOf(intArrayOf(40, 228, 86), intArrayOf(168, 228, 100), intArrayOf(182, 228, 92), intArrayOf(330, 228, 110), intArrayOf(480, 228, 80))) palmSil(ctx, p[0].toDouble(), p[1].toDouble(), p[2].toDouble(), palmC)
                for (p in arrayOf(intArrayOf(100, 232, 34), intArrayOf(270, 230, 24), intArrayOf(430, 234, 40))) saguaro(ctx, p[0].toDouble(), p[1].toDouble(), p[2].toDouble(), T(i3(8, 12, 10), i3(64, 100, 62)))
                // Cinder-block wall along the front
                ctx.fillStyle = T(i3(30, 26, 26), i3(196, 172, 140)); ctx.fillRect(0, 230, w, 14)
                ctx.fillStyle = T(i3(20, 18, 18), i3(170, 146, 116))
                run { var y = 233; while (y < 244) { var bx = if (y % 8 != 0) 0 else 6; while (bx < w) { ctx.fillRect(bx, y, 1, 4); bx += 12 }; y += 4 } }
                ctx.fillRect(0, 230, w, 1.5)
                // Monsoon season: a wall of dust rolls in with summer storms
                if (env.currentSeason == "summer" && (W.base == "storm" || W.wind > 0.8f)) {
                    val dust = ctx.createLinearGradient(0, 100, 0, 256)
                    dust.addColorStop(0, "rgba(160,110,60,0)"); dust.addColorStop(0.5, "rgba(170,120,70,0.45)"); dust.addColorStop(1, "rgba(150,100,60,0.55)")
                    ctx.fillStyle = dust; ctx.fillRect(0, 100, w, 156)
                }
            }
            "desert" -> {
                fun mesa(x: Double, top: Double, mw: Double, col: String) { ctx.fillStyle = col; ctx.beginPath(); ctx.moveTo(x - 8, 214); ctx.lineTo(x, top); ctx.lineTo(x + mw, top); ctx.lineTo(x + mw + 10, 214); ctx.closePath(); ctx.fill() }
                val far = T(i3(30, 20, 40), i3(190, 130, 110), 0.5)
                mesa(30.0, 162.0, 90.0, far); mesa(250.0, 150.0, 60.0, far); mesa(400.0, 170.0, 120.0, far)
                val near = T(i3(24, 14, 26), i3(176, 96, 60), 0.2)
                mesa(150.0, 178.0, 40.0, near); mesa(330.0, 186.0, 26.0, near)
                val sand = ctx.createLinearGradient(0, 204, 0, 256)
                sand.addColorStop(0, T(i3(30, 22, 30), i3(214, 170, 120), 0.2)); sand.addColorStop(1, T(i3(20, 14, 20), i3(190, 140, 90)))
                ctx.fillStyle = sand; ctx.beginPath(); ctx.moveTo(0, 256)
                run { var x = 0; while (x <= 512) { ctx.lineTo(x, 210 + sin(x * 0.02) * 4 + sin(x * 0.07) * 2); x += 16 } }
                ctx.lineTo(512, 256); ctx.closePath(); ctx.fill()
                for (p in arrayOf(intArrayOf(60, 236, 30), intArrayOf(210, 228, 20), intArrayOf(470, 240, 36))) saguaro(ctx, p[0].toDouble(), p[1].toDouble(), p[2].toDouble(), T(i3(8, 12, 10), i3(60, 96, 60)))
                // Route 66-style motel sign that buzzes at night
                ctx.fillStyle = T(i3(10, 10, 14), i3(80, 80, 86)); ctx.fillRect(110, 192, 2, 20)
                ctx.fillStyle = if (isNight()) "rgba(80,220,255,0.95)" else T(i3(30, 20, 20), i3(200, 60, 50)); ctx.fillRect(98, 180, 26, 12)
                ctx.fillStyle = if (isNight()) "#ff4d6d" else "#fff"; ctx.font = "bold 6px monospace"; ctx.fillText("MOTEL", 101, 189)
            }
            "island" -> {
                // Turquoise lagoon, a volcano island, overwater bungalows on stilts
                ctx.fillStyle = T(i3(16, 18, 30), i3(70, 110, 90), 0.4)
                ctx.beginPath(); ctx.moveTo(150, 212); ctx.lineTo(220, 150); ctx.lineTo(236, 146); ctx.lineTo(300, 212); ctx.closePath(); ctx.fill()
                ctx.fillStyle = T(i3(12, 14, 20), i3(60, 96, 70), 0.3)
                ctx.beginPath(); ctx.ellipse(60, 212, 50, 10, 0, PI, 0); ctx.fill(); ctx.beginPath(); ctx.ellipse(420, 212, 70, 9, 0, PI, 0); ctx.fill()
                paintWater(ctx, 211.0, i3(90, 210, 214), i3(24, 140, 170))
                val palmC = T(i3(8, 12, 10), i3(40, 80, 46))
                for (p in arrayOf(intArrayOf(40, 206, 26), intArrayOf(70, 207, 20), intArrayOf(400, 206, 24), intArrayOf(440, 207, 28))) palmSil(ctx, p[0].toDouble(), p[1].toDouble(), p[2].toDouble(), palmC)
                for (i in 0 until 6) {
                    val x = 300 + i * 30; val y = 222 + (i % 2) * 3
                    ctx.fillStyle = T(i3(16, 12, 10), i3(120, 90, 60)); ctx.fillRect(x, y, 16, 7); ctx.fillRect(x + 2, y + 7, 1, 8); ctx.fillRect(x + 13, y + 7, 1, 8)
                    ctx.fillStyle = T(i3(24, 20, 12), i3(180, 150, 90)); ctx.beginPath(); ctx.moveTo(x - 3, y); ctx.lineTo(x + 8, y - 7); ctx.lineTo(x + 19, y); ctx.closePath(); ctx.fill()
                    litWindow(ctx, x + 6, y + 2, 4, 3, i / 7.0)
                    if (i < 5) { ctx.fillStyle = T(i3(16, 12, 10), i3(120, 90, 60)); ctx.fillRect(x + 16, y + 4, 14, 1) }
                }
            }
            "beach" -> {
                ctx.fillStyle = T(i3(14, 18, 34), i3(110, 130, 150), 0.55)
                ctx.beginPath(); ctx.ellipse(300, 212, 90, 11, 0, PI, 0); ctx.fill()
                paintWater(ctx, 211.0, i3(70, 140, 190), i3(18, 70, 120))
                // The pier out over the water, with the amusement park on it (the wheel turns, drawn live)
                ctx.fillStyle = T(i3(14, 12, 12), i3(120, 100, 80)); ctx.fillRect(40, 214, 170, 4)
                run { var x = 44; while (x < 210) { ctx.fillRect(x, 218, 2, 14); x += 12 } }
                ctx.fillStyle = T(i3(12, 12, 16), i3(180, 70, 60)); ctx.fillRect(150, 196, 30, 18); ctx.fillStyle = T(i3(20, 20, 24), i3(240, 220, 120)); ctx.fillRect(150, 192, 30, 4)
                // Lighthouse on the rocks
                ctx.fillStyle = T(i3(10, 10, 16), i3(96, 88, 80))
                ctx.beginPath(); ctx.moveTo(408, 256); ctx.lineTo(420, 214); ctx.lineTo(446, 204); ctx.lineTo(484, 206); ctx.lineTo(512, 212); ctx.lineTo(512, 256); ctx.closePath(); ctx.fill()
                for (i in 0 until 6) { ctx.fillStyle = if (i % 2 == 1) T(i3(30, 12, 14), i3(200, 40, 40)) else T(i3(40, 42, 50), i3(240, 240, 236)); ctx.fillRect(458, 168 + i * 6, 10, 6) }
                ctx.fillStyle = T(i3(14, 14, 20), i3(60, 60, 66)); ctx.fillRect(456, 160, 14, 8); ctx.fillRect(460, 154, 6, 6)
                ctx.fillStyle = "rgba(255,255,255,${0.25 + 0.35 * env.daylight})"
                run { var x = 0; while (x < 410) { ctx.fillRect(x, 250 + sin(x * 0.3) * 2, 6, 1); x += 9 } }
            }
            "lake" -> {
                ridge(ctx, ridgePts(21, 172.0, 34.0, 30.0, 14.0), T(i3(18, 22, 40), i3(120, 140, 170), 0.55))
                pines(ctx, 22, 208.0, 10.0, 20.0, 7.0, T(i3(8, 16, 14), i3(40, 70, 50), 0.2), snowCap())
                paintWater(ctx, 208.0, i3(120, 160, 190), i3(40, 80, 100))
                ctx.save(); ctx.globalAlpha = 0.28f; ctx.translate(0, 416); ctx.scale(1, -1)
                pines(ctx, 22, 208.0, 10.0, 20.0, 7.0, T(i3(8, 16, 14), i3(40, 70, 50), 0.2), null)
                ctx.restore()
                ctx.fillStyle = T(i3(14, 10, 10), i3(90, 60, 40)); ctx.fillRect(150, 199, 16, 9)
                ctx.fillStyle = T(i3(24, 14, 14), i3(120, 62, 50)); ctx.fillRect(161, 191, 3, 6) // cabin chimney
                ctx.fillStyle = T(i3(14, 10, 10), i3(90, 60, 40))
                ctx.beginPath(); ctx.moveTo(147, 199); ctx.lineTo(158, 192); ctx.lineTo(169, 199); ctx.closePath(); ctx.fill()
                litWindow(ctx, 154, 202, 4, 3, 0.05)
                if (isNight()) { ctx.fillStyle = "rgba(255,200,120,0.35)"; ctx.fillRect(155, 210, 2, 18) }
            }
            "forest" -> {
                val (gn, gd) = seasonGreen()
                ridge(ctx, ridgePts(31, 180.0, 26.0, 34.0, 6.0), T(i3(16, 22, 38), i3(104, 128, 150), 0.6))
                pines(ctx, 32, 198.0, 12.0, 22.0, 6.0, T(i3(10, 18, 20), i3(58, 86, 80), 0.45), snowCap())
                val tc = T(i3(14, 14, 20), i3(90, 74, 60), 0.3)
                ctx.fillStyle = tc; ctx.fillRect(372, 160, 2, 36); ctx.fillRect(388, 160, 2, 36); ctx.fillRect(368, 152, 26, 9)
                ctx.beginPath(); ctx.moveTo(366, 152); ctx.lineTo(381, 144); ctx.lineTo(396, 152); ctx.closePath(); ctx.fill()
                if (isNight()) { ctx.fillStyle = "rgba(255,214,140,0.9)"; ctx.fillRect(378, 155, 5, 3) }
                pines(ctx, 33, 216.0, 24.0, 40.0, 9.0, T(i3(6, 12, 12), IntArray(3) { ((gn[it] + gd[it]) / 2.6).roundToInt() }, 0.15), snowCap())
                pines(ctx, 34, 246.0, 46.0, 74.0, 16.0, T(i3(3, 6, 8), i3(22, 40, 30)), snowCap())
            }
            "mountains" -> {
                val far = ridgePts(41, 118.0, 46.0, 22.0, 30.0)
                ridge(ctx, far, T(i3(22, 26, 46), i3(120, 136, 168), 0.5))
                ctx.fillStyle = T(i3(70, 76, 100), i3(240, 244, 250), 0.3)
                far.forEachIndexed { i, (x, y) ->
                    if (i > 0 && y < 104) {
                        val (px, py) = far[i - 1]
                        ctx.beginPath(); ctx.moveTo(px, py + (if (winterNow()) 36 else 14)); ctx.lineTo(x, y); ctx.lineTo(x + 10, y + (if (winterNow()) 40 else 16)); ctx.closePath(); ctx.fill()
                    }
                }
                ridge(ctx, ridgePts(42, 168.0, 24.0, 20.0, 16.0), T(i3(14, 18, 30), i3(80, 96, 110), 0.35))
                ctx.fillStyle = T(i3(12, 10, 12), i3(100, 70, 50)); ctx.fillRect(196, 186, 22, 10)
                ctx.fillStyle = T(i3(24, 14, 14), i3(120, 62, 50)); ctx.fillRect(212, 177, 3, 7) // lodge chimney
                ctx.fillStyle = T(i3(12, 10, 12), i3(100, 70, 50))
                ctx.beginPath(); ctx.moveTo(192, 186); ctx.lineTo(207, 177); ctx.lineTo(222, 186); ctx.closePath(); ctx.fill()
                if (isNight()) {
                    ctx.fillStyle = "rgba(255,210,130,0.95)"; for (x in intArrayOf(199, 205, 211)) ctx.fillRect(x, 189, 3, 2)
                    for (i in 0 until 9) ctx.fillRect(222 + i * 9, 184 - i * 6.5, 1, 1)
                }
                pines(ctx, 43, 222.0, 18.0, 34.0, 7.0, T(i3(6, 12, 12), i3(36, 62, 48), 0.15), snowCap())
            }
            "countryside" -> {
                val (gn, gd) = seasonGreen()
                ctx.fillStyle = T(i3(16, 22, 36), i3(110, 140, 130), 0.5)
                ctx.beginPath(); ctx.moveTo(0, 256); run { var x = 0; while (x <= 512) { ctx.lineTo(x, 190 + sin(x * 0.012 + 1) * 10); x += 8 } }; ctx.lineTo(512, 256); ctx.fill()
                val fields = arrayOf(gn to gd, i3(30, 26, 12) to i3(190, 170, 80), i3(14, 24, 14) to i3(70, 120, 50))
                for (i in 0 until 9) {
                    val c = fields[i % 3]
                    ctx.fillStyle = if (winterNow()) T(i3(46, 50, 66), i3(226, 232, 242)) else T(c.first, c.second, 0.15)
                    ctx.beginPath(); val x0 = i * 60 - 30
                    ctx.moveTo(x0, 256); run { var x = x0; while (x <= x0 + 64) { ctx.lineTo(x, 206 + sin(x * 0.02) * 6); x += 8 } }; ctx.lineTo(x0 + 64, 256); ctx.closePath(); ctx.fill()
                }
                blobTrees(ctx, 51, 206.0, 9.0, 15.0, 40.0, T(i3(10, 22, 12), i3(40, 80, 40), 0.3), bareTrees())
                ctx.fillStyle = T(i3(30, 10, 10), i3(170, 40, 34)); ctx.fillRect(110, 186, 34, 20)
                ctx.beginPath(); ctx.moveTo(106, 186); ctx.lineTo(116, 176); ctx.lineTo(138, 176); ctx.lineTo(148, 186); ctx.closePath(); ctx.fill()
                ctx.fillStyle = T(i3(40, 40, 40), i3(236, 230, 220)); ctx.fillRect(122, 194, 10, 12)
                ctx.fillStyle = T(i3(24, 26, 32), i3(170, 176, 180)); ctx.fillRect(148, 170, 12, 36)
                ctx.beginPath(); ctx.ellipse(154, 170, 6, 5, 0, PI, 0); ctx.fill()
                ctx.fillStyle = T(i3(22, 22, 26), i3(230, 224, 206)); ctx.fillRect(320, 192, 26, 14)
                ctx.fillStyle = T(i3(28, 14, 14), i3(135, 64, 52)); ctx.fillRect(339, 181, 3, 8) // farmhouse chimney
                ctx.fillStyle = T(i3(14, 12, 14), i3(80, 60, 56)); ctx.beginPath(); ctx.moveTo(316, 192); ctx.lineTo(333, 182); ctx.lineTo(350, 192); ctx.closePath(); ctx.fill()
                litWindow(ctx, 325, 196, 5, 4, 0.1); litWindow(ctx, 337, 196, 5, 4, 0.3)
                ctx.fillStyle = T(i3(14, 14, 18), i3(120, 120, 126)); ctx.beginPath(); ctx.moveTo(424, 206); ctx.lineTo(429, 166); ctx.lineTo(431, 166); ctx.lineTo(436, 206); ctx.closePath(); ctx.fill()
                // Cows in the near field (they stay put at night)
                val r = makeRng(52)
                for (i in 0 until 5) {
                    val x = 30 + r() * 250; val y = 216 + r() * 6
                    ctx.fillStyle = T(i3(20, 20, 22), i3(240, 240, 236)); ctx.fillRect(x, y, 8, 4)
                    ctx.fillStyle = T(i3(6, 6, 8), i3(30, 30, 30)); ctx.fillRect(x + 2, y + 1, 3, 2); ctx.fillRect(x + 8, y - 1, 2, 3); ctx.fillRect(x + 1, y + 4, 1, 2); ctx.fillRect(x + 6, y + 4, 1, 2)
                }
                ctx.fillStyle = T(i3(20, 16, 12), i3(120, 96, 70))
                run { var x = 0; while (x < 512) { ctx.fillRect(x, 226, 2, 8); x += 12 } }
                ctx.fillRect(0, 228, 512, 1); ctx.fillRect(0, 231, 512, 1)
            }
        }
    }

    /** Side walls of the sky box for every place but the city (same heights as the back wall). */
    fun paintSideHorizonPlace(ctx: Canvas2D, w: Int, h: Int, r: () -> Double, cityRow: (Int, Int, Int, String, Boolean) -> Unit) {
        val e = places.outsideEnv
        when (e) {
            "skyscraper" -> {
                cityRow(150, 4, 18, T(i3(10, 14, 28), i3(120, 136, 160), 0.6), false)
                ctx.fillStyle = T(i3(12, 16, 30), i3(100, 110, 126), 0.5); ctx.fillRect(0, 150, w, 106)
                for (row in 0 until 9) { var x = (row % 2) * 7; while (x < w) { ctx.fillStyle = T(i3(16, 18, 28), i3(90, 96, 110), 0.4 - row * 0.04); ctx.fillRect(x, 152 + row * row * 1.3 + row * 3, 7 + row * 3, 2 + row * 1.6); x += 10 + row * 4 } }
            }
            "beach", "island", "lake" -> {
                ctx.fillStyle = T(i3(14, 18, 34), i3(110, 130, 150), 0.55); ctx.beginPath(); ctx.ellipse(w * 0.4, 212, 70, 9, 0, PI, 0); ctx.fill()
                paintWater(ctx, if (e == "lake") 208.0 else 211.0, if (e == "island") i3(90, 210, 214) else i3(70, 140, 190), if (e == "island") i3(24, 140, 170) else i3(18, 70, 120))
                if (e == "lake") pines(ctx, 3, 208.0, 8.0, 16.0, 6.0, T(i3(8, 16, 14), i3(40, 70, 50), 0.2), snowCap())
            }
            "forest", "mountains" -> {
                ridge(ctx, ridgePts(if (e == "mountains") 141 else 131, if (e == "mountains") 130.0 else 182.0, if (e == "mountains") 40.0 else 24.0, 22.0, 20.0), T(i3(18, 22, 40), i3(116, 132, 160), 0.5))
                pines(ctx, 132, 212.0, 14.0, 30.0, 7.0, T(i3(8, 14, 14), i3(40, 66, 52), 0.2), snowCap())
                pines(ctx, 133, 244.0, 40.0, 64.0, 14.0, T(i3(3, 6, 8), i3(22, 40, 30)), null)
            }
            "desert", "arizona" -> {
                ridge(ctx, ridgePts(151, 172.0, 30.0, 14.0, 26.0), T(i3(30, 20, 42), i3(160, 120, 120), 0.45))
                ctx.fillStyle = T(i3(28, 20, 26), i3(206, 168, 124), 0.2); ctx.fillRect(0, 208, w, 48)
                for (i in 0 until 4) saguaro(ctx, 30 + i * 60 + r() * 20, 236.0, 16 + r() * 16, T(i3(8, 12, 10), i3(64, 100, 62)))
                if (e == "arizona") { ctx.fillStyle = T(i3(30, 26, 26), i3(196, 172, 140)); ctx.fillRect(0, 230, w, 14) }
            }
            else -> {
                val (gn, gd) = seasonGreen()
                ridge(ctx, ridgePts(161, 196.0, 10.0, 26.0, 4.0), T(i3(14, 20, 34), i3(96, 120, 124), 0.45))
                blobTrees(ctx, 162, 210.0, 10.0, 18.0, 16.0, T(gn, gd, 0.3), bareTrees())
                if (e != "countryside") { var x = 0.0; while (x < w) { house(ctx, x, 226.0, 30.0, 14.0, i3(200, 186, 160), i3(110, 70, 60), r); x += 40 + r() * 20 } }
                else { ctx.fillStyle = if (winterNow()) T(i3(46, 50, 66), i3(226, 232, 242)) else T(gn, gd, 0.15); ctx.fillRect(0, 214, w, 42) }
            }
        }
    }

    // ---- Live bits, drawn a few times a second over the cached picture ----
    private var train = -200.0; private var trainNext = 0.0; private var heli = -60.0; private var heliNext = 20.0
    fun paintLandscapeLive(ctx: Canvas2D, t: Float) {
        val night = isNight()
        val W = env.W
        when (places.outsideEnv) {
            "beach" -> {
                glitter(ctx, 212.0, t, false)
                // Ferris wheel on the pier
                val a = t * 0.25
                ctx.strokeStyle = if (night) "rgba(255,120,200,0.9)" else T(i3(20, 20, 26), i3(220, 220, 230)); ctx.lineWidth = 1f
                ctx.beginPath(); ctx.arc(110, 186, 24, 0, PI * 2); ctx.stroke()
                val cols = arrayOf("#ffe14d", "#2ef2ff", "#ff3ea5", "#7cff6b")
                for (i in 0 until 8) {
                    val b = a + i * PI / 4; val x = 110 + cos(b) * 24; val y = 186 + sin(b) * 24
                    ctx.beginPath(); ctx.moveTo(110, 186); ctx.lineTo(x, y); ctx.stroke()
                    ctx.fillStyle = if (night) cols[i % 4] else T(i3(20, 20, 26), i3(230, 80, 70)); ctx.fillRect(x - 2, y, 4, 3)
                }
                ctx.fillStyle = T(i3(14, 14, 18), i3(90, 90, 96)); ctx.fillRect(108, 186, 4, 28)
                if (night) {
                    val lb = t * 0.9f; val c = cos(lb)
                    ctx.save(); ctx.globalAlpha = 0.18f + 0.2f * max(0f, sin(lb)); ctx.fillStyle = "#fff6c8"
                    ctx.beginPath(); ctx.moveTo(463, 163); ctx.lineTo(463 + c * 260, 150); ctx.lineTo(463 + c * 260, 176); ctx.closePath(); ctx.fill(); ctx.restore()
                    ctx.fillStyle = "rgba(255,246,200,${0.5 + 0.5 * max(0f, sin(lb))})"; ctx.fillRect(460, 161, 6, 4)
                }
            }
            "lake" -> glitter(ctx, 209.0, t, true)
            "island" -> {
                glitter(ctx, 212.0, t, false)
                // The volcano smokes by day and glows at night
                val k = 0.5 + 0.5 * sin(t * 0.7)
                ctx.fillStyle = if (night) "rgba(255,90,30,${0.35 + 0.35 * k})" else "rgba(200,200,205,${0.25 + 0.1 * k})"
                for (i in 0 until 5) { ctx.beginPath(); ctx.ellipse(228 + i * 5 + sin(t * 0.3 + i) * 3, 140 - i * 9, 6 + i * 3, 4 + i * 2, 0, 0, PI * 2); ctx.fill() }
            }
            "countryside" -> {
                val a = t * (0.6 + W.wind * 2.2)
                ctx.strokeStyle = T(i3(20, 20, 24), i3(200, 200, 205)); ctx.lineWidth = 1.5f
                for (i in 0 until 4) { val b = a + i * PI / 2; ctx.beginPath(); ctx.moveTo(430, 166); ctx.lineTo(430 + cos(b) * 13, 166 + sin(b) * 13); ctx.stroke() }
            }
            "neon" -> {
                // Trains glide along the elevated line every minute or so
                if (t > trainNext) {
                    train += 3.2
                    for (c in 0 until 6) {
                        val x = train - c * 26
                        ctx.fillStyle = T(i3(16, 16, 22), i3(190, 196, 200)); ctx.fillRect(x, 186, 24, 10)
                        ctx.fillStyle = if (night) "rgba(255,240,200,0.95)" else "rgba(40,60,80,0.8)"; var wx = 2; while (wx < 22) { ctx.fillRect(x + wx, 188, 3, 3); wx += 5 }
                    }
                    if (train > 700) { train = -10.0; trainNext = t + 40 + rnd.nextDouble() * 50 }
                }
                // Neon flicker
                if (night && rnd.nextDouble() < 0.3) { ctx.fillStyle = "rgba(0,0,0,0.35)"; ctx.fillRect(rnd.nextDouble() * 500, 130 + rnd.nextDouble() * 60, 6, 20) }
            }
            "skyscraper" -> {
                // Street grid far below: tiny headlights and taillights streaming along the avenues
                for (lane in 0 until 4) {
                    val y = 168 + lane * 18; val speed = (if (lane % 2 == 1) -1 else 1) * (14 + lane * 6)
                    for (i in 0 until 14) {
                        val x = (((i * 47 + t * speed) % 560) + 560) % 560 - 24
                        ctx.fillStyle = if (lane % 2 == 1) "rgba(255,70,60,0.9)" else "rgba(255,240,190,0.95)"
                        if (night || env.daylight < 0.8) ctx.fillRect(x, y, 2, 1) else { ctx.fillStyle = "rgba(40,40,50,0.6)"; ctx.fillRect(x, y, 2, 1) }
                    }
                }
                // Aircraft warning lights on the tower tops, and the odd helicopter
                if (sin(t * 3) > 0.3f) { ctx.fillStyle = "#ff2a2a"; for ((x, y) in arrayOf(47 to 56, 103 to 87, 387 to 67, 474 to 29)) ctx.fillRect(x - 1, y - 1, 3, 3) }
                if (t > heliNext) {
                    heli += 1.6
                    val hx = heli; val hy = 96 + sin(t * 0.5) * 6
                    ctx.fillStyle = T(i3(10, 10, 14), i3(50, 54, 60)); ctx.fillRect(hx, hy, 8, 3); ctx.fillRect(hx + 8, hy + 1, 6, 1); ctx.fillRect(hx - 2, hy - 2, 12, 1)
                    if (sin(t * 9) > 0) { ctx.fillStyle = "#ff3b3b"; ctx.fillRect(hx + 13, hy, 2, 2) }
                    if (heli > 560) { heli = -40.0; heliNext = t + 60 + rnd.nextDouble() * 90 }
                }
            }
            "trailer" -> if (night) {
                // Blue TV flicker in a couple of trailer windows
                val k = rnd.nextDouble()
                ctx.fillStyle = "rgba(110,160,255,${0.4 + k * 0.5})"; ctx.fillRect(117, 207, 5, 3); ctx.fillRect(309, 221, 5, 3)
            }
            "neighborhood" -> if (night && rnd.nextDouble() < 0.08) {
                ctx.fillStyle = "rgba(0,0,0,0.6)"; ctx.fillRect(394, 217, 46, 9) // the OPEN sign buzzes
            }
        }
        // Raindrop ripples on water surfaces when raining
        if (env.isRainingNow() && places.outsideEnv in setOf("beach", "island", "lake")) {
            ctx.strokeStyle = "rgba(210, 235, 255, 0.45)"
            ctx.lineWidth = 1f
            val nRipples = (6 + W.rain * 12).toInt()
            for (i in 0 until nRipples) {
                val ph = (t * 2.1 + i * 0.73) % 1.0
                val rx = ((i * 83 + (t * 2f).toInt() * 19) % 480) + 16
                val ry = 212 + ((i * 29) % 36)
                ctx.beginPath()
                ctx.ellipse(rx.toDouble(), ry.toDouble(), 2.0 + ph * 9.0, 0.7 + ph * 2.6, 0.0, 0.0, PI * 2)
                ctx.stroke()
            }
        }
        // Cozy chimney smoke curling into the sky in winter, snow, or chilly fall evenings/mornings (drifting -X with the wind)
        val chillyFall = env.currentSeason == "fall" && (env.currentTime in setOf("night", "dusk", "dawn") || env.isPrecipNow())
        val coldSmoke = (env.currentSeason == "winter" || env.isSnowingNow() || chillyFall) && env.windowActivity() > 0.15
        if (coldSmoke && places.outsideEnv in setOf("suburbs", "house", "countryside", "lake", "mountains")) {
            val chimneys = when (places.outsideEnv) {
                "suburbs" -> arrayOf(118 to 198, 252 to 196, 396 to 198)
                "countryside" -> arrayOf(340 to 181)
                "lake" -> arrayOf(162 to 191)
                "mountains" -> arrayOf(213 to 177)
                else -> arrayOf(188 to 198, 344 to 196)
            }
            ctx.fillStyle = if (night) "rgba(180, 195, 220, 0.24)" else "rgba(235, 240, 248, 0.30)"
            for ((cx, cy) in chimneys) {
                for (p in 0 until 5) {
                    val age = (t * 0.65 + p * 0.2) % 1.0
                    // Drift left (-X) in the exact same wind direction as rain, snow, and falling leaves
                    val sx = cx - age * (9.0 + W.wind * 24.0) + sin(t * 1.8 + p) * 2.0
                    val sy = cy - age * 24.0
                    val rad = 1.8 + age * 4.8
                    ctx.beginPath(); ctx.ellipse(sx, sy, rad, rad * 0.75, 0.0, 0.0, PI * 2); ctx.fill()
                }
            }
        }
    }

    /** Ground under the window for each place (sand, gravel, desert, wet asphalt...); false = the seasonal default. */
    fun outsideGroundPaint(ctx: Canvas2D, w: Int, h: Int): Boolean {
        if (places.outsideEnv == "city") return false
        val style = places.envDef().ground
        val winter = env.currentSeason == "winter"
        val P: Pair<String, Array<String>> = when (style) {
            "sand" -> "#d9bf8c" to arrayOf("#e8d4a8", "#c4a676", "#f1e3c0")
            "desert" -> "#c99a64" to arrayOf("#b8834e", "#d9ae7a", "#a86f3c")
            "gravel" -> "#b9a184" to arrayOf("#8f7a63", "#d4c0a4", "#6f5f4e", "#c9b293")
            "asphalt" -> "#2a2c33" to arrayOf("#363944", "#1f2127", "#4a4d57")
            "forest" -> (if (winter) "#dfe7f1" else "#2f3a22") to (if (winter) arrayOf("#ffffff", "#c7d2e2") else arrayOf("#4a3a22", "#243018", "#6b4f2a", "#3f5a2a"))
            else -> return false
        }
        ctx.fillStyle = P.first; ctx.fillRect(0, 0, w, h)
        for (i in 0 until 2600) { ctx.fillStyle = P.second[i % P.second.size]; ctx.fillRect((rnd.nextDouble() * w).toInt(), (rnd.nextDouble() * h).toInt(), if (style == "gravel") 2 else 1, 1) }
        return true
    }
}
