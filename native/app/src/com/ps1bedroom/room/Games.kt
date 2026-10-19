package com.ps1bedroom.room

import com.ps1bedroom.room.engine.Canvas2D
import com.ps1bedroom.room.engine.Geo
import com.ps1bedroom.room.engine.Material
import com.ps1bedroom.room.engine.Mesh
import com.ps1bedroom.room.engine.PS1
import com.ps1bedroom.room.engine.Weather
import com.ps1bedroom.room.engine.createCanvasTex
import com.ps1bedroom.room.engine.makeRng
import com.ps1bedroom.room.engine.rand
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Parameters for the corridor-shooter gameplay style. */
class ShooterStyle(val wall: String, val floor: String, val foe: String, val tint: String, val dino: Boolean = false)

/** One game in the library (HTML PS1_GAMES / BANDICOOT_RUSH / GTA_GAMES merged with GAME_STYLE). */
class Game(
    val title: String, val pub: String, val year: Int, val esrb: String, val discs: Int, val spineCol: String,
    val template: String, val accent: String, val music: String,
    val platform: String = "ps1", val topTheme: String? = null, val theme3d: String? = null, val shooter: ShooterStyle? = null,
    val art: (Canvas2D, Double, Double, Double) -> Unit
)

/**
 * The game library's data and cover art (HTML lines 4489-4835 and 5778-6302): 11 PS1 games for the hutch shelf,
 * Bandi-coot Rush, and every Grand Theft Auto with hand-drawn recreations of the cover layouts, plus the jewel-case
 * and keep-case builders.
 */
object Games {
    // ---- Shared drawing helpers ----
    fun fillPoly(ctx: Canvas2D, pts: Array<DoubleArray>) {
        ctx.beginPath(); ctx.moveTo(pts[0][0], pts[0][1])
        for (i in 1 until pts.size) ctx.lineTo(pts[i][0], pts[i][1])
        ctx.closePath(); ctx.fill()
    }
    private fun p(x: Number, y: Number) = doubleArrayOf(x.toDouble(), y.toDouble())
    fun outlinedText(ctx: Canvas2D, text: String, x: Number, y: Number, font: String, fill: Any, stroke: String, lineWidth: Number = 2) {
        ctx.font = font; ctx.lineJoin = "round"; ctx.lineWidth = lineWidth.toFloat()
        ctx.strokeStyle = stroke; ctx.strokeText(text, x, y)
        ctx.fillStyle = fill; ctx.fillText(text, x, y)
    }
    fun skaterSilhouette(ctx: Canvas2D, x: Double, y: Double, s: Double, col: String) {
        ctx.fillStyle = col
        ctx.beginPath(); ctx.arc(x, y - 18 * s, 4 * s, 0, PI * 2); ctx.fill()
        fillPoly(ctx, arrayOf(p(x - 4 * s, y - 14 * s), p(x + 5 * s, y - 13 * s), p(x + 3 * s, y - 2 * s), p(x - 5 * s, y - 3 * s)))
        fillPoly(ctx, arrayOf(p(x - 4 * s, y - 12 * s), p(x - 16 * s, y - 18 * s), p(x - 15 * s, y - 15 * s), p(x - 4 * s, y - 9 * s)))
        fillPoly(ctx, arrayOf(p(x + 4 * s, y - 12 * s), p(x + 15 * s, y - 6 * s), p(x + 14 * s, y - 3 * s), p(x + 3 * s, y - 9 * s)))
        fillPoly(ctx, arrayOf(p(x - 5 * s, y - 3 * s), p(x - 11 * s, y + 4 * s), p(x - 8 * s, y + 6 * s), p(x, y - 1 * s)))
        fillPoly(ctx, arrayOf(p(x + 3 * s, y - 2 * s), p(x + 9 * s, y + 3 * s), p(x + 7 * s, y + 6 * s), p(x, y)))
        ctx.save(); ctx.translate(x - 1 * s, y + 8 * s); ctx.rotate(-0.35)
        ctx.fillRect(-14 * s, -1.5 * s, 28 * s, 3 * s)
        ctx.restore()
    }
    /** Four-colour PlayStation mark used on covers and spines. */
    fun drawPsMark(ctx: Canvas2D, x: Number, y: Number, s: Number) {
        val xx = x.toDouble(); val yy = y.toDouble(); val ss = s.toDouble()
        for ((c, ix, iy) in arrayOf(Triple("#ef4444", 0, 0), Triple("#facc15", 1, 0), Triple("#22c55e", 0, 1), Triple("#3b82f6", 1, 1))) {
            ctx.fillStyle = c; ctx.fillRect(xx + ix * ss, yy + iy * ss, ss, ss)
        }
    }
    private fun ellipse(ctx: Canvas2D, cx: Number, cy: Number, rx: Number, ry: Number, rot: Number = 0) { ctx.beginPath(); ctx.ellipse(cx, cy, rx, ry, rot, 0, PI * 2); ctx.fill() }
    private fun circle(ctx: Canvas2D, cx: Number, cy: Number, r: Number) { ctx.beginPath(); ctx.arc(cx, cy, r, 0, PI * 2); ctx.fill() }
    private fun vgrad(ctx: Canvas2D, h: Double, vararg stops: Pair<Double, String>): Canvas2D.Gradient {
        val g = ctx.createLinearGradient(0, 0, 0, h); for ((o, c) in stops) g.addColorStop(o, c); return g
    }

    // ---- GTA cover helpers: work in fractions of the area so the same art fits a case cover or the TV ----
    class Frame(val x0: Double, val W: Double, val H: Double) {
        fun X(f: Double) = x0 + f * W
        fun Y(f: Double) = f * H
        val S = min(W, H) / 100
    }
    fun coverFrame(x0: Double, w: Double, h: Double) = Frame(x0, w - x0, h)
    fun GTA_FONT(px: Double) = "bold ${max(4.0, px)}px \"Arial Black\", Impact, \"sans-serif-condensed\", sans-serif"
    fun gtaLogo(ctx: Canvas2D, cx: Double, cy: Double, s: Double, sub: String?, subFill: Any? = null, subFont: String? = null, subStroke: String? = null) {
        ctx.textAlign = "center"; ctx.textBaseline = "alphabetic"
        outlinedText(ctx, "GRAND THEFT AUTO", cx, cy, GTA_FONT(6.2 * s), "#ffffff", "#000000", max(1.0, 1.4 * s))
        if (sub != null) outlinedText(ctx, sub, cx, cy + 15 * s, subFont ?: GTA_FONT(15 * s), subFill ?: "#ffffff", subStroke ?: "#000000", max(1.5, 2.4 * s))
        ctx.textAlign = "left"
    }
    class Deco(val shirt: String? = null, val pattern: String? = null, val tie: String? = null, val jacket: String? = null, val hair: String? = null, val skin: String? = null, val hairCol: String? = null)
    fun figure(ctx: Canvas2D, x: Double, y: Double, s: Double, col: String, pose: Int = 0, deco: Deco? = null) {
        figureBase(ctx, x, y, s, col, pose)
        if (deco == null) return
        if (deco.jacket != null) {
            ctx.fillStyle = deco.jacket; fillPoly(ctx, arrayOf(p(x - 7.5 * s, y - 24 * s), p(x + 7.5 * s, y - 24 * s), p(x + 6.5 * s, y - 7 * s), p(x - 6.5 * s, y - 7 * s)))
            ctx.fillStyle = col; ctx.fillRect(x - 1.5 * s, y - 24 * s, 3 * s, 16 * s)
        }
        if (deco.shirt != null) {
            ctx.fillStyle = deco.shirt; ctx.fillRect(x - 6 * s, y - 23 * s, 12 * s, 15 * s)
            if (deco.pattern != null) { ctx.fillStyle = deco.pattern; for (i in 0 until 6) ctx.fillRect(x - 5 * s + (i % 3) * 4 * s, y - 21 * s + (i / 3) * 6 * s, 2 * s, 2 * s) }
        }
        if (deco.tie != null) { ctx.fillStyle = deco.tie; fillPoly(ctx, arrayOf(p(x - 1 * s, y - 23 * s), p(x + 1 * s, y - 23 * s), p(x + 1.5 * s, y - 13 * s), p(x, y - 11 * s), p(x - 1.5 * s, y - 13 * s))) }
        if (deco.skin != null) { ctx.fillStyle = deco.skin; circle(ctx, x, y - 30 * s, 4.2 * s) }
        ctx.fillStyle = deco.hairCol ?: col
        when (deco.hair) {
            "long" -> { ctx.beginPath(); ctx.arc(x, y - 32 * s, 5.2 * s, PI, 0); ctx.fill(); ctx.fillRect(x - 5.5 * s, y - 32 * s, 3 * s, 12 * s); ctx.fillRect(x + 2.5 * s, y - 32 * s, 3 * s, 12 * s) }
            "cap" -> { ctx.beginPath(); ctx.arc(x, y - 32 * s, 5.2 * s, PI, 0); ctx.fill(); ctx.fillRect(x, y - 33 * s, 8 * s, 1.8 * s) }
            "buzz" -> { ctx.beginPath(); ctx.arc(x, y - 32 * s, 4.8 * s, PI, 0); ctx.fill() }
        }
    }
    /** Simple standing silhouette; pose 1 = arm out holding something, 2 = arms crossed, 3 = running. */
    fun figureBase(ctx: Canvas2D, x: Double, y: Double, s: Double, col: String, pose: Int = 0) {
        ctx.fillStyle = col
        circle(ctx, x, y - 30 * s, 5 * s)
        fillPoly(ctx, arrayOf(p(x - 7 * s, y - 24 * s), p(x + 7 * s, y - 24 * s), p(x + 6 * s, y - 6 * s), p(x - 6 * s, y - 6 * s)))
        if (pose == 3) {
            fillPoly(ctx, arrayOf(p(x - 5 * s, y - 7 * s), p(x - 1 * s, y - 7 * s), p(x - 9 * s, y + 8 * s), p(x - 13 * s, y + 6 * s)))
            fillPoly(ctx, arrayOf(p(x + 1 * s, y - 7 * s), p(x + 5 * s, y - 7 * s), p(x + 12 * s, y + 6 * s), p(x + 8 * s, y + 8 * s)))
        } else { ctx.fillRect(x - 6 * s, y - 7 * s, 5 * s, 16 * s); ctx.fillRect(x + 1 * s, y - 7 * s, 5 * s, 16 * s) }
        when (pose) {
            1 -> { fillPoly(ctx, arrayOf(p(x + 6 * s, y - 23 * s), p(x + 22 * s, y - 21 * s), p(x + 22 * s, y - 18 * s), p(x + 6 * s, y - 18 * s))); ctx.fillRect(x + 20 * s, y - 23 * s, 6 * s, 2.5 * s) }
            2 -> ctx.fillRect(x - 8 * s, y - 19 * s, 16 * s, 4 * s)
            else -> { ctx.fillRect(x - 10 * s, y - 23 * s, 3.5 * s, 14 * s); ctx.fillRect(x + 6.5 * s, y - 23 * s, 3.5 * s, 14 * s) }
        }
    }
    fun carSide(ctx: Canvas2D, x: Double, y: Double, w: Double, col: String, convertible: Boolean = false) {
        val h = w * 0.28
        ctx.fillStyle = col
        fillPoly(ctx, arrayOf(p(x, y), p(x + w * 0.05, y - h * 0.6), p(x + w * 0.3, y - h * 0.7), p(x + w * 0.42, y - h * (if (convertible) 0.75 else 1.25)),
            p(x + w * 0.72, y - h * (if (convertible) 0.75 else 1.25)), p(x + w * 0.85, y - h * 0.7), p(x + w, y - h * 0.5), p(x + w, y)))
        if (!convertible) { ctx.fillStyle = "rgba(160, 200, 240, 0.7)"; fillPoly(ctx, arrayOf(p(x + w * 0.45, y - h * 0.75), p(x + w * 0.48, y - h * 1.15), p(x + w * 0.7, y - h * 1.15), p(x + w * 0.78, y - h * 0.75))) }
        ctx.fillStyle = "#0b0b0f"; for (f in doubleArrayOf(0.2, 0.8)) circle(ctx, x + w * f, y, h * 0.42)
        ctx.fillStyle = "#9ca3af"; for (f in doubleArrayOf(0.2, 0.8)) circle(ctx, x + w * f, y, h * 0.18)
    }
    fun palm(ctx: Canvas2D, x: Double, y: Double, s: Double, col: String) {
        ctx.strokeStyle = col; ctx.lineWidth = (2.2 * s).toFloat()
        ctx.beginPath(); ctx.moveTo(x, y); ctx.quadraticCurveTo(x + 6 * s, y - 25 * s, x + 3 * s, y - 48 * s); ctx.stroke()
        ctx.fillStyle = col
        for (i in 0 until 6) {
            val a = -PI / 2 + (i - 2.5) * 0.55
            ctx.save(); ctx.translate(x + 3 * s, y - 48 * s); ctx.rotate(a)
            ellipse(ctx, 10 * s, 2 * s, 12 * s, 2.6 * s, 0.25)
            ctx.restore()
        }
    }
    fun skyline(ctx: Canvas2D, x0: Double, x1: Double, base: Double, s: Double, col: String, seed: Int) {
        val r = makeRng(seed)
        ctx.fillStyle = col
        var x = x0
        while (x < x1) {
            val bw = (6 + r() * 12) * s; val bh = (10 + r() * 34) * s
            ctx.fillRect(x, base - bh, bw, bh)
            if (r() > 0.75) ctx.fillRect(x + bw / 2 - 0.5 * s, base - bh - 8 * s, 1 * s, 8 * s)
            x += bw + r() * 2 * s
        }
    }
    /** The famous panel-grid cover: panels = (x, y, w, h, colTop, colBottom, draw) in fractions of the art area. */
    class Panel(val x: Double, val y: Double, val w: Double, val h: Double, val c1: String, val c2: String, val draw: ((Canvas2D, Double, Double, Double, Double) -> Unit)?)
    fun panelCover(ctx: Canvas2D, x0: Double, w: Double, h: Double, panels: List<Panel>, gap: Double = 0.012): Frame {
        val F = coverFrame(x0, w, h)
        ctx.fillStyle = "#050505"; ctx.fillRect(x0, 0, F.W, h)
        for (pn in panels) {
            val X = F.X(pn.x + gap / 2); val Y = F.Y(pn.y + gap / 2); val PW = F.W * (pn.w - gap); val PH = h * (pn.h - gap)
            ctx.save(); ctx.beginPath(); ctx.rect(X, Y, PW, PH); ctx.clip()
            val g = ctx.createLinearGradient(0, Y, 0, Y + PH); g.addColorStop(0, pn.c1); g.addColorStop(1, pn.c2)
            ctx.fillStyle = g; ctx.fillRect(X, Y, PW, PH)
            pn.draw?.invoke(ctx, X, Y, PW, PH)
            ctx.restore()
            ctx.strokeStyle = "#000"; ctx.lineWidth = max(1.0, F.S * 0.9).toFloat()
            ctx.strokeRect(X, Y, PW, PH)
        }
        return F
    }
    // Panel drawing shortcuts
    private fun guy(col: String, pose: Int, k: Double = 0.95, deco: Deco? = null): (Canvas2D, Double, Double, Double, Double) -> Unit = { ctx, x, y, w, h -> figure(ctx, x + w * 0.5, y + h * k, min(w, h) / 42, col, pose, deco) }
    private fun car(col: String, conv: Boolean = false): (Canvas2D, Double, Double, Double, Double) -> Unit = { ctx, x, y, w, h -> carSide(ctx, x + w * 0.1, y + h * 0.85, w * 0.8, col, conv) }
    private fun heli(col: String): (Canvas2D, Double, Double, Double, Double) -> Unit = { ctx, x, y, w, h ->
        val cx = x + w * 0.5; val cy = y + h * 0.45; val s = min(w, h) / 40
        ctx.fillStyle = col
        ellipse(ctx, cx, cy, 9 * s, 5 * s)
        ctx.fillRect(cx + 6 * s, cy - 1 * s, 14 * s, 2 * s)
        ctx.fillRect(cx - 16 * s, cy - 7 * s, 32 * s, 1.2 * s)
        ctx.fillRect(cx - 0.5 * s, cy - 7 * s, 1 * s, 3 * s)
    }
    private fun blast(): (Canvas2D, Double, Double, Double, Double) -> Unit = { ctx, x, y, w, h ->
        val cx = x + w * 0.5; val cy = y + h * 0.55
        val g = ctx.createRadialGradient(cx, cy, 1, cx, cy, max(w, h) * 0.6)
        g.addColorStop(0, "#fff7c2"); g.addColorStop(0.35, "#ffb020"); g.addColorStop(0.7, "#d93a12"); g.addColorStop(1, "rgba(60,10,0,0)")
        ctx.fillStyle = g; ctx.fillRect(x, y, w, h)
    }
    private fun city(col: String, seed: Int): (Canvas2D, Double, Double, Double, Double) -> Unit = { ctx, x, y, w, h -> skyline(ctx, x, x + w, y + h, min(w, h) / 40, col, seed) }
    private fun palms(col: String): (Canvas2D, Double, Double, Double, Double) -> Unit = { ctx, x, y, w, h -> palm(ctx, x + w * 0.25, y + h, min(w, h) / 55, col); palm(ctx, x + w * 0.7, y + h, min(w, h) / 48, col) }
    private fun combo(vararg fns: (Canvas2D, Double, Double, Double, Double) -> Unit): (Canvas2D, Double, Double, Double, Double) -> Unit = { ctx, x, y, w, h -> for (f in fns) f(ctx, x, y, w, h) }
    private fun pn(x: Double, y: Double, w: Double, h: Double, c1: String, c2: String, d: ((Canvas2D, Double, Double, Double, Double) -> Unit)?) = Panel(x, y, w, h, c1, c2, d)

    // ---- The 11 PS1 games on the hutch shelf ----
    val PS1_GAMES: List<Game> = listOf(
        Game("Tony Hawk's Pro Skater 2", "Activision", 2000, "T", 1, "#facc15", "skate", "#facc15", "punk") { ctx, x0, w, h ->
            ctx.fillStyle = vgrad(ctx, h, 0.0 to "#fbbf24", 1.0 to "#dc2626"); ctx.fillRect(x0, 0, w - x0, h)
            ctx.fillStyle = "rgba(0, 0, 0, 0.16)"
            var y = 3.0; while (y < h) { var x = x0 + 3; while (x < w) { circle(ctx, x, y, 0.8 + (y / h) * 1.4); x += 6 }; y += 6 }
            skaterSilhouette(ctx, 78.0, 70.0, 1.5, "#0b0b0f")
            ctx.textAlign = "left"
            outlinedText(ctx, "TONY HAWK'S", x0 + 5, 15, "bold 11px Impact, sans-serif", "#ffffff", "#111827")
            outlinedText(ctx, "PRO SKATER", x0 + 5, 29, "bold 15px Impact, sans-serif", "#ffffff", "#111827")
            outlinedText(ctx, "2", w - 24, h - 8, "bold 40px Impact, sans-serif", "#dc2626", "#ffffff", 3)
        },
        Game("Spyro: Year of the Dragon", "Sony / Insomniac", 2000, "E", 1, "#c084fc", "glide", "#a855f7", "anime") { ctx, x0, w, h ->
            ctx.fillStyle = vgrad(ctx, h, 0.0 to "#38bdf8", 0.7 to "#0e7490", 1.0 to "#14532d"); ctx.fillRect(x0, 0, w - x0, h)
            ctx.fillStyle = "rgba(255, 255, 255, 0.7)"
            for (c in arrayOf(intArrayOf(40, 40, 9), intArrayOf(52, 36, 7), intArrayOf(100, 48, 8), intArrayOf(110, 44, 6))) circle(ctx, c[0], c[1], c[2])
            // purple dragon: wing, body, head, horns, eye
            ctx.fillStyle = "#5b21b6"; fillPoly(ctx, arrayOf(p(60, 70), p(36, 54), p(44, 76)))
            ctx.fillStyle = "#7c3aed"
            ellipse(ctx, 70, 82, 16, 12, -0.2); ellipse(ctx, 86, 64, 13, 10, -0.3)
            fillPoly(ctx, arrayOf(p(94, 62), p(112, 66), p(96, 72)))
            ctx.fillStyle = "#facc15"
            fillPoly(ctx, arrayOf(p(80, 56), p(74, 40), p(86, 54))); fillPoly(ctx, arrayOf(p(88, 55), p(90, 40), p(94, 56)))
            ctx.fillStyle = "#ffffff"; circle(ctx, 90, 62, 3)
            ctx.fillStyle = "#065f46"; circle(ctx, 91, 62, 1.5)
            ctx.fillStyle = "#fde047"; circle(ctx, 104, 82, 2) // Sparx
            ctx.textAlign = "center"
            outlinedText(ctx, "SPYRO", (x0 + w) / 2, 22, "bold 20px Georgia, serif", "#fbbf24", "#4c1d95", 3)
            outlinedText(ctx, "YEAR OF THE DRAGON", (x0 + w) / 2, 32, "bold 7px sans-serif", "#ffffff", "#4c1d95")
        },
        Game("Final Fantasy IX", "Square", 2000, "T", 4, "#e7d6a8", "rpg", "#93c5fd", "piano") { ctx, x0, w, h ->
            ctx.fillStyle = "#e8d8ac"; ctx.fillRect(x0, 0, w - x0, h)
            ctx.fillStyle = "rgba(120, 85, 40, 0.18)"
            for (i in 0 until 40) ctx.fillRect(x0 + rand() * (w - x0), rand() * h, 3, 1)
            val cg = ctx.createLinearGradient(70, 40, 84, 80); cg.addColorStop(0, "#bfdbfe"); cg.addColorStop(1, "#1d4ed8")
            ctx.fillStyle = cg; fillPoly(ctx, arrayOf(p(77, 38), p(88, 60), p(77, 84), p(66, 60)))
            // sepia sketch silhouettes: pointy-hat mage and a thief with a tail
            ctx.fillStyle = "#5b3a1a"; fillPoly(ctx, arrayOf(p(40, 64), p(48, 50), p(56, 64))); ctx.fillRect(43, 64, 10, 18)
            ctx.fillStyle = "#facc15"; ctx.fillRect(45, 67, 2, 2); ctx.fillRect(50, 67, 2, 2)
            ctx.fillStyle = "#5b3a1a"; circle(ctx, 104, 62, 5); ctx.fillRect(100, 67, 9, 18)
            ctx.strokeStyle = "#5b3a1a"; ctx.lineWidth = 2f
            ctx.beginPath(); ctx.moveTo(100, 82); ctx.quadraticCurveTo(90, 92, 96, 100); ctx.stroke()
            ctx.textAlign = "center"; ctx.fillStyle = "#3f2a14"
            ctx.font = "bold 11px Georgia, serif"; ctx.fillText("FINAL FANTASY", (x0 + w) / 2, 18)
            ctx.font = "bold 13px Georgia, serif"; ctx.fillText("IX", (x0 + w) / 2, 31)
        },
        Game("Spider-Man", "Activision", 2000, "T", 1, "#ef4444", "swing", "#ef4444", "hero") { ctx, x0, w, h ->
            ctx.fillStyle = "#b91c1c"; ctx.fillRect(x0, 0, w - x0, h)
            ctx.fillStyle = "#1e3a8a"; fillPoly(ctx, arrayOf(p(x0, h), p(x0, h * 0.55), p(w, h * 0.25), p(w, h)))
            val cx = 76.0; val cy = 62.0
            ctx.strokeStyle = "rgba(255, 255, 255, 0.55)"; ctx.lineWidth = 1f
            for (a in 0 until 12) { ctx.beginPath(); ctx.moveTo(cx, cy); ctx.lineTo(cx + cos(a * 0.524) * 90, cy + sin(a * 0.524) * 90); ctx.stroke() }
            var r = 10
            while (r < 90) {
                ctx.beginPath()
                for (a in 0..12) { val px = cx + cos(a * 0.524) * r; val py = cy + sin(a * 0.524) * r; if (a == 0) ctx.moveTo(px, py) else ctx.lineTo(px, py) }
                ctx.stroke(); r += 12
            }
            // spider emblem
            ctx.fillStyle = "#0b0b0f"; ellipse(ctx, cx, cy - 4, 4, 6); ellipse(ctx, cx, cy + 8, 5, 8)
            ctx.strokeStyle = "#0b0b0f"; ctx.lineWidth = 2f
            for ((sx, sy) in arrayOf(-1 to -1, 1 to -1, -1 to 1, 1 to 1)) { ctx.beginPath(); ctx.moveTo(cx, cy + 2); ctx.lineTo(cx + sx * 12, cy + sy * 6); ctx.lineTo(cx + sx * 16, cy + sy * 18); ctx.stroke() }
            ctx.textAlign = "center"
            outlinedText(ctx, "SPIDER-MAN", (x0 + w) / 2, 18, "bold 16px Impact, sans-serif", "#ffffff", "#1e3a8a", 3)
        },
        Game("Crash Bash", "Sony / Eurocom", 2000, "E", 1, "#fb923c", "platform", "#fb923c", "cartoon") { ctx, x0, w, h ->
            ctx.fillStyle = vgrad(ctx, h, 0.0 to "#2563eb", 0.65 to "#60a5fa", 0.66 to "#16a34a", 1.0 to "#14532d"); ctx.fillRect(x0, 0, w - x0, h)
            ctx.fillStyle = "#7c2d12"; fillPoly(ctx, arrayOf(p(60, 46), p(66, 30), p(70, 44), p(76, 28), p(80, 44), p(88, 32), p(90, 48)))
            ctx.fillStyle = "#f97316"; ellipse(ctx, 76, 64, 22, 20)
            ctx.fillStyle = "#fde68a"; ellipse(ctx, 76, 74, 16, 9)
            ctx.fillStyle = "#ffffff"; ellipse(ctx, 68, 58, 5, 6); ellipse(ctx, 84, 58, 5, 6)
            ctx.fillStyle = "#15803d"; ctx.fillRect(68, 57, 3, 4); ctx.fillRect(82, 57, 3, 4)
            ctx.fillStyle = "#7f1d1d"; fillPoly(ctx, arrayOf(p(64, 72), p(88, 72), p(82, 82), p(70, 82)))
            ctx.fillStyle = "#ffffff"; ctx.fillRect(66, 72, 20, 3)
            ctx.textAlign = "center"
            outlinedText(ctx, "CRASH", (x0 + w) / 2, 17, "bold 16px Impact, sans-serif", "#f97316", "#111827", 3)
            outlinedText(ctx, "BASH", (x0 + w) / 2, h - 8, "bold 16px Impact, sans-serif", "#facc15", "#111827", 3)
        },
        Game("Driver 2", "Infogrames", 2000, "T", 2, "#60a5fa", "drive", "#60a5fa", "funk") { ctx, x0, w, h ->
            ctx.fillStyle = "#0b1530"; ctx.fillRect(x0, 0, w - x0, h)
            ctx.fillStyle = "#1e293b"; var x = x0; while (x < w) { ctx.fillRect(x, 30 + ((x * 7) % 20), 12, 60); x += 14 }
            ctx.fillStyle = "#fde047"; for (i in 0 until 30) ctx.fillRect(x0 + ((i * 37) % (w - x0)), 36.0 + ((i * 23) % 40), 2, 2)
            ctx.fillStyle = "#334155"; ctx.fillRect(x0, 86, w - x0, 26)
            ctx.fillStyle = "#b91c1c"; fillPoly(ctx, arrayOf(p(46, 92), p(56, 84), p(92, 82), p(104, 90), p(104, 98), p(46, 100)))
            ctx.fillStyle = "#0f172a"; ctx.fillRect(60, 85, 24, 5)
            ctx.fillStyle = "#fef08a"; ctx.fillRect(100, 92, 4, 3)
            ctx.textAlign = "center"
            outlinedText(ctx, "DRIVER 2", (x0 + w) / 2, 20, "bold 17px Impact, sans-serif", "#e2e8f0", "#1d4ed8", 3)
        },
        Game("Medal of Honor: Underground", "Electronic Arts", 2000, "T", 1, "#e5e7eb", "shooter", "#d6d3d1", "march",
            shooter = ShooterStyle("#57534e", "#292524", "#1c1917", "rgba(120, 100, 60, 0.25)")) { ctx, x0, w, h ->
            ctx.fillStyle = vgrad(ctx, h, 0.0 to "#78716c", 1.0 to "#292524"); ctx.fillRect(x0, 0, w - x0, h)
            ctx.fillStyle = "#1c1917"
            ctx.beginPath(); ctx.ellipse(74, 64, 20, 13, 0, PI, PI * 2); ctx.fill()
            ctx.fillRect(54, 64, 40, 4)
            fillPoly(ctx, arrayOf(p(62, 68), p(86, 68), p(92, 112), p(56, 112)))
            ctx.fillStyle = "#ca8a04"; circle(ctx, 74, 90, 6)
            ctx.textAlign = "center"; ctx.fillStyle = "#f5f5f4"
            ctx.font = "bold 9px Georgia, serif"; ctx.fillText("MEDAL OF HONOR", (x0 + w) / 2, 18)
            ctx.font = "bold 8px Georgia, serif"; ctx.fillText("UNDERGROUND", (x0 + w) / 2, 28)
        },
        Game("Dino Crisis 2", "Capcom", 2000, "M", 1, "#dc2626", "shooter", "#dc2626", "horror",
            shooter = ShooterStyle("#14532d", "#1a2e05", "#3f6212", "rgba(120, 0, 0, 0.18)", dino = true)) { ctx, x0, w, h ->
            ctx.fillStyle = "#0a0a0a"; ctx.fillRect(x0, 0, w - x0, h)
            val rg = ctx.createRadialGradient(76, 70, 4, 76, 70, 60); rg.addColorStop(0, "#991b1b"); rg.addColorStop(1, "#0a0a0a")
            ctx.fillStyle = rg; ctx.fillRect(x0, 0, w - x0, h)
            ctx.fillStyle = "#1c1917"
            fillPoly(ctx, arrayOf(p(30, 70), p(110, 52), p(112, 66), p(40, 80)))
            fillPoly(ctx, arrayOf(p(34, 82), p(108, 74), p(104, 90), p(40, 92)))
            ctx.fillStyle = "#f5f5f4"
            for (i in 0 until 9) {
                fillPoly(ctx, arrayOf(p(44 + i * 7, 78 - i * 1.6), p(48 + i * 7, 78 - i * 1.6), p(46 + i * 7, 86 - i * 1.6)))
                fillPoly(ctx, arrayOf(p(44 + i * 7, 84 - i * 0.8), p(48 + i * 7, 84 - i * 0.8), p(46 + i * 7, 77 - i * 0.8)))
            }
            ctx.fillStyle = "#facc15"; circle(ctx, 98, 60, 2)
            ctx.textAlign = "center"
            outlinedText(ctx, "DINO CRISIS 2", (x0 + w) / 2, 20, "bold 13px Impact, sans-serif", "#d1d5db", "#7f1d1d", 3)
        },
        Game("Harry Potter and the Sorcerer's Stone", "Electronic Arts", 2001, "E", 1, "#fbbf24", "broom", "#fbbf24", "magic") { ctx, x0, w, h ->
            ctx.fillStyle = vgrad(ctx, h, 0.0 to "#0b1338", 1.0 to "#1e3a8a"); ctx.fillRect(x0, 0, w - x0, h)
            ctx.fillStyle = "#e0e7ff"; for (i in 0 until 26) ctx.fillRect(x0 + ((i * 41) % (w - x0)), ((i * 17) % 60).toDouble(), 1, 1)
            ctx.fillStyle = "#0f172a"
            fillPoly(ctx, arrayOf(p(x0, 112), p(x0, 88), p(40, 88), p(44, 70), p(48, 88), p(64, 88), p(70, 62), p(76, 88), p(92, 88), p(98, 74), p(104, 88), p(w, 88), p(w, 112)))
            ctx.fillStyle = "#fde047"; fillPoly(ctx, arrayOf(p(78, 30), p(70, 46), p(77, 46), p(70, 62), p(86, 42), p(79, 42), p(86, 30)))
            ctx.textAlign = "center"; ctx.fillStyle = "#fbbf24"
            ctx.font = "bold italic 12px Georgia, serif"; ctx.fillText("Harry Potter", (x0 + w) / 2, 16)
            ctx.font = "6px Georgia, serif"; ctx.fillText("and the Sorcerer's Stone", (x0 + w) / 2, 25)
        },
        Game("Yu-Gi-Oh! Forbidden Memories", "Konami", 2002, "E", 1, "#a855f7", "cards", "#c084fc", "trance") { ctx, x0, w, h ->
            ctx.fillStyle = vgrad(ctx, h, 0.0 to "#3b0764", 1.0 to "#0f172a"); ctx.fillRect(x0, 0, w - x0, h)
            for ((cx, cy, a) in arrayOf(Triple(34, 60, -0.3), Triple(96, 62, 0.3))) {
                ctx.save(); ctx.translate(cx, cy); ctx.rotate(a)
                ctx.fillStyle = "#a16207"; ctx.fillRect(-11, -16, 22, 32)
                ctx.fillStyle = "#7c2d12"; ctx.fillRect(-9, -14, 18, 13)
                ctx.restore()
            }
            ctx.fillStyle = "#facc15"; fillPoly(ctx, arrayOf(p(66, 44), p(86, 82), p(46, 82)))
            ctx.fillStyle = "#92400e"; ellipse(ctx, 66, 68, 7, 4)
            ctx.fillStyle = "#fef3c7"; circle(ctx, 66, 68, 2)
            ctx.textAlign = "center"
            outlinedText(ctx, "YU-GI-OH!", (x0 + w) / 2, 18, "bold 15px Impact, sans-serif", "#facc15", "#7c2d12", 3)
            outlinedText(ctx, "FORBIDDEN MEMORIES", (x0 + w) / 2, 28, "bold 6px sans-serif", "#ffffff", "#3b0764")
        },
        Game("Tony Hawk's Pro Skater 3", "Activision", 2001, "T", 1, "#2dd4bf", "skate", "#2dd4bf", "poppunk") { ctx, x0, w, h ->
            val g = ctx.createLinearGradient(0, 0, w, h); g.addColorStop(0, "#0e7490"); g.addColorStop(1, "#1e1b4b")
            ctx.fillStyle = g; ctx.fillRect(x0, 0, w - x0, h)
            ctx.strokeStyle = "rgba(255, 255, 255, 0.12)"
            var x = x0; while (x < w + h) { ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x - h, h); ctx.stroke(); x += 8 }
            skaterSilhouette(ctx, 70.0, 76.0, 1.5, "#020617")
            ctx.textAlign = "left"
            outlinedText(ctx, "TONY HAWK'S", x0 + 5, 15, "bold 11px Impact, sans-serif", "#ffffff", "#020617")
            outlinedText(ctx, "PRO SKATER", x0 + 5, 29, "bold 15px Impact, sans-serif", "#ffffff", "#020617")
            outlinedText(ctx, "3", w - 24, h - 8, "bold 40px Impact, sans-serif", "#2dd4bf", "#ffffff", 3)
        }
    )

    /** The disc that starts in the console (its case lies beside it). */
    val BANDICOOT_RUSH = Game("Bandi-coot Rush", "Naughty Pup", 1999, "E", 1, "#fb923c", "platform", "#fb923c", "jungle") { ctx, x0, w, h ->
        ctx.fillStyle = vgrad(ctx, h, 0.0 to "#4c1d95", 0.5 to "#f97316", 0.75 to "#16a34a", 1.0 to "#14532d"); ctx.fillRect(x0, 0, w - x0, h)
        ctx.fillStyle = "#fde68a"; circle(ctx, 100, 44, 10)
        ctx.fillStyle = "#ea580c"; ctx.fillRect(56, 54, 26, 24); ctx.fillRect(60, 36, 22, 18)
        ctx.fillStyle = "#ffffff"; ctx.fillRect(72, 42, 6, 6)
        ctx.fillStyle = "#000000"; ctx.fillRect(75, 43, 3, 3)
        ctx.fillStyle = "#1d4ed8"; ctx.fillRect(56, 78, 26, 9)
        ctx.fillStyle = "#b45309"; ctx.fillRect(92, 76, 18, 18)
        ctx.textAlign = "center"
        outlinedText(ctx, "BANDI-COOT", (x0 + w) / 2, 17, "bold 14px Impact, sans-serif", "#fef3c7", "#7c2d12", 3)
        outlinedText(ctx, "RUSH", (x0 + w) / 2, 31, "bold 14px Impact, sans-serif", "#facc15", "#7c2d12", 3)
    }

    // ---- Every Grand Theft Auto, in the right case for its console ----
    val GTA_GAMES: List<Game> = listOf(
        Game("Grand Theft Auto", "Rockstar Games", 1997, "M", 1, "#facc15", "gtatop", "#facc15", "gtahiphop", "ps1", topTheme = "gta1") { ctx, x0, w, h ->
            val F = coverFrame(x0, w, h)
            ctx.fillStyle = "#d9c38a"; ctx.fillRect(x0, 0, F.W, h)
            ctx.fillStyle = "#3f3f46"
            for (i in 0 until 4) { ctx.fillRect(x0, F.Y(0.28 + i * 0.2), F.W, F.S * 6); ctx.fillRect(F.X(0.12 + i * 0.27), 0, F.S * 6, h) }
            ctx.fillStyle = "#facc15"
            for (i in 0 until 4) for (k in 0 until 8) ctx.fillRect(x0 + k * F.W / 8, F.Y(0.28 + i * 0.2) + F.S * 2.7, F.S * 4, F.S * 0.7)
            ctx.fillStyle = "#4d7c3a"
            for ((fx, fy) in arrayOf(0.18 to 0.33, 0.45 to 0.53, 0.72 to 0.33, 0.18 to 0.73, 0.72 to 0.73)) ctx.fillRect(F.X(fx), F.Y(fy), F.W * 0.18, h * 0.12)
            ctx.save(); ctx.translate(F.X(0.52), F.Y(0.66)); ctx.rotate(-0.5)
            ctx.fillStyle = "#dc2626"; ctx.fillRect(-F.S * 5, -F.S * 9, F.S * 10, F.S * 18)
            ctx.fillStyle = "#111"; ctx.fillRect(-F.S * 4, -F.S * 4, F.S * 8, F.S * 6)
            ctx.restore()
            ctx.save(); ctx.translate(F.X(0.3), F.Y(0.9)); ctx.rotate(-0.3)
            ctx.fillStyle = "#f8fafc"; ctx.fillRect(-F.S * 5, -F.S * 9, F.S * 10, F.S * 18)
            ctx.fillStyle = "#1d4ed8"; ctx.fillRect(-F.S * 5, -F.S * 2, F.S * 5, F.S * 3)
            ctx.fillStyle = "#ef4444"; ctx.fillRect(0, -F.S * 2, F.S * 5, F.S * 3)
            ctx.restore()
            ctx.fillStyle = "#000"; ctx.fillRect(x0, 0, F.W, h * 0.24)
            gtaLogo(ctx, F.X(0.5), F.Y(0.1), F.S * 1.15, null)
            ctx.textAlign = "center"
            outlinedText(ctx, "GTA", F.X(0.5), F.Y(0.21), GTA_FONT(F.S * 11), "#facc15", "#000", F.S * 1.5)
            ctx.textAlign = "left"
        },
        Game("Grand Theft Auto: London 1969", "Rockstar Games", 1999, "M", 1, "#ef4444", "gtatop", "#3b82f6", "punk", "ps1", topTheme = "london") { ctx, x0, w, h ->
            val F = coverFrame(x0, w, h)
            ctx.fillStyle = "#1e3a8a"; ctx.fillRect(x0, 0, F.W, h)
            ctx.save(); ctx.beginPath(); ctx.rect(x0, 0, F.W, h); ctx.clip()
            ctx.strokeStyle = "#f8fafc"; ctx.lineWidth = (F.S * 14).toFloat()
            ctx.beginPath(); ctx.moveTo(x0, 0); ctx.lineTo(w, h); ctx.moveTo(w, 0); ctx.lineTo(x0, h); ctx.stroke()
            ctx.strokeStyle = "#dc2626"; ctx.lineWidth = (F.S * 5).toFloat()
            ctx.beginPath(); ctx.moveTo(x0, 0); ctx.lineTo(w, h); ctx.moveTo(w, 0); ctx.lineTo(x0, h); ctx.stroke()
            ctx.fillStyle = "#f8fafc"; ctx.fillRect(F.X(0.42), 0, F.W * 0.16, h); ctx.fillRect(x0, F.Y(0.42), F.W, h * 0.16)
            ctx.fillStyle = "#dc2626"; ctx.fillRect(F.X(0.45), 0, F.W * 0.1, h); ctx.fillRect(x0, F.Y(0.45), F.W, h * 0.1)
            ctx.restore()
            // Mod roundel
            for ((c, r) in arrayOf("#1e3a8a" to 0.24, "#f8fafc" to 0.17, "#dc2626" to 0.1)) { ctx.fillStyle = c; circle(ctx, F.X(0.5), F.Y(0.5), F.S * r * 100) }
            carSide(ctx, F.X(0.18), F.Y(0.9), F.W * 0.64, "#0f5132", true)
            ctx.fillStyle = "rgba(0,0,0,0.75)"; ctx.fillRect(x0, 0, F.W, h * 0.26)
            gtaLogo(ctx, F.X(0.5), F.Y(0.1), F.S * 1.1, "LONDON 1969", "#facc15", GTA_FONT(F.S * 11), "#b91c1c")
        },
        Game("Grand Theft Auto 2", "Rockstar Games", 1999, "T", 1, "#f97316", "gtatop", "#f97316", "trance", "ps1", topTheme = "gta2") { ctx, x0, w, h ->
            val F = coverFrame(x0, w, h)
            ctx.fillStyle = vgrad(ctx, h, 0.0 to "#ffb703", 0.55 to "#fb5607", 1.0 to "#3a0ca3"); ctx.fillRect(x0, 0, F.W, h)
            skyline(ctx, x0, w, F.Y(0.95), F.S * 1.2, "#240046", 22)
            ctx.fillStyle = "#ffd60a"
            for (i in 0 until 30) ctx.fillRect(F.X((i * 37 % 100) / 100.0), F.Y(0.6 + (i * 13 % 30) / 100.0), F.S, F.S * 1.2)
            figure(ctx, F.X(0.7), F.Y(0.95), F.S * 1.3, "#10002b", 1)
            ctx.textAlign = "center"
            outlinedText(ctx, "GTA2", F.X(0.48), F.Y(0.36), GTA_FONT(F.S * 30), "#ffd60a", "#10002b", F.S * 3)
            outlinedText(ctx, "RESPECT IS EVERYTHING", F.X(0.5), F.Y(0.46), GTA_FONT(F.S * 5), "#ffffff", "#10002b", F.S)
            ctx.textAlign = "left"
        },
        Game("Grand Theft Auto III", "Rockstar Games", 2001, "M", 1, "#e5e7eb", "gta3d", "#9ca3af", "gtahiphop", "ps2", theme3d = "iii") { ctx, x0, w, h ->
            panelCover(ctx, x0, w, h, listOf(
                pn(0.0, 0.0, 0.36, 0.25, "#f59e0b", "#7c2d12", heli("#111")),
                pn(0.36, 0.0, 0.32, 0.25, "#38bdf8", "#0c4a6e", guy("#0b0b0f", 1)),
                pn(0.68, 0.0, 0.32, 0.25, "#9ca3af", "#374151", guy("#111", 2)),
                pn(0.0, 0.25, 0.5, 0.25, "#1e3a8a", "#0b1530", combo(city("#020617", 3), car("#b91c1c"))),
                pn(0.5, 0.25, 0.5, 0.25, "#facc15", "#b45309", guy("#1c1917", 3)),
                pn(0.0, 0.5, 0.34, 0.25, "#2563eb", "#1e1b4b", guy("#0f172a", 0)),
                pn(0.34, 0.5, 0.33, 0.25, "#fb923c", "#7c2d12", guy("#000", 1, 0.95, Deco(jacket = "#5b3a1e", skin = "#d8a27a", hair = "buzz", hairCol = "#1a1208"))),
                pn(0.67, 0.5, 0.33, 0.25, "#000", "#000", blast()),
                pn(0.0, 0.75, 1.0, 0.25, "#0a0a0a", "#000", null)))
            val F = coverFrame(x0, w, h)
            gtaLogo(ctx, F.X(0.5), F.Y(0.82), F.S * 1.2, null)
            ctx.textAlign = "center"
            outlinedText(ctx, "III", F.X(0.5), F.Y(0.97), "bold ${F.S * 17}px Georgia, serif", "#ffffff", "#000", F.S * 1.6)
            ctx.textAlign = "left"
        },
        Game("Grand Theft Auto: Vice City", "Rockstar Games", 2002, "M", 1, "#f472b6", "gta3d", "#f472b6", "flashpop", "ps2", theme3d = "vc") { ctx, x0, w, h ->
            panelCover(ctx, x0, w, h, listOf(
                pn(0.0, 0.0, 0.42, 0.28, "#ff9ec7", "#c026d3", guy("#1b0a2a", 1, 0.95, Deco(shirt = "#14b8a6", pattern = "#f472b6", skin = "#e0ac7e", hair = "buzz", hairCol = "#2b1a10"))),
                pn(0.42, 0.0, 0.58, 0.28, "#fb7185", "#f59e0b", combo(palms("#3b0a45"), car("#f8fafc", true))),
                pn(0.0, 0.28, 0.33, 0.25, "#22d3ee", "#0e7490", guy("#082f49", 2)),
                pn(0.33, 0.28, 0.34, 0.25, "#f0abfc", "#7e22ce", guy("#2e1065", 0)),
                pn(0.67, 0.28, 0.33, 0.25, "#fde047", "#ea580c", heli("#3b0764")),
                pn(0.0, 0.53, 0.5, 0.22, "#5eead4", "#be185d", combo(city("#3b0764", 9), palms("#1e0b2e"))),
                pn(0.5, 0.53, 0.5, 0.22, "#f97316", "#9d174d", guy("#1e0b2e", 3)),
                pn(0.0, 0.75, 1.0, 0.25, "#0a0a0a", "#000", null)))
            val F = coverFrame(x0, w, h)
            gtaLogo(ctx, F.X(0.5), F.Y(0.82), F.S * 1.2, "Vice City", "#ff4fa3", "italic bold ${F.S * 16}px \"Brush Script MT\", Georgia, serif", "#ffffff")
        },
        Game("Grand Theft Auto: San Andreas", "Rockstar Games", 2004, "M", 1, "#fb923c", "gta3d", "#fb923c", "gfunk", "ps2", theme3d = "sa") { ctx, x0, w, h ->
            panelCover(ctx, x0, w, h, listOf(
                pn(0.0, 0.0, 0.33, 0.25, "#f59e0b", "#7c2d12", guy("#1c0f05", 1)),
                pn(0.33, 0.0, 0.34, 0.25, "#84cc16", "#365314", guy("#0b1a05", 2)),
                pn(0.67, 0.0, 0.33, 0.25, "#fbbf24", "#92400e", heli("#1c1917")),
                pn(0.0, 0.25, 0.3, 0.5, "#fdba74", "#9a3412", guy("#1c0f05", 0, 0.9)),
                // CJ: white tank top, bandana
                pn(0.3, 0.25, 0.4, 0.5, "#16a34a", "#052e16") { c2, x, y, pw, ph ->
                    figure(c2, x + pw / 2, y + ph * 0.95, min(pw, ph) / 34, "#3b2414", 2)
                    c2.fillStyle = "#f8fafc"; c2.fillRect(x + pw / 2 - pw * 0.13, y + ph * 0.36, pw * 0.26, ph * 0.22)
                    c2.fillStyle = "#1d4ed8"; c2.fillRect(x + pw / 2 - pw * 0.1, y + ph * 0.2, pw * 0.2, ph * 0.04)
                },
                pn(0.7, 0.25, 0.3, 0.25, "#fde68a", "#b45309", car("#166534")),
                pn(0.7, 0.5, 0.3, 0.25, "#f97316", "#431407", guy("#000", 3)),
                pn(0.0, 0.75, 1.0, 0.25, "#0a0a0a", "#000", null)))
            val F = coverFrame(x0, w, h)
            gtaLogo(ctx, F.X(0.5), F.Y(0.82), F.S * 1.2, "San Andreas", "#ffffff", "bold ${F.S * 13}px Georgia, serif", "#000000")
        },
        Game("Grand Theft Auto: Liberty City Stories", "Rockstar Games", 2005, "M", 1, "#93c5fd", "gta3d", "#93c5fd", "gtahiphop", "ps2", theme3d = "lcs") { ctx, x0, w, h ->
            panelCover(ctx, x0, w, h, listOf(
                pn(0.0, 0.0, 0.5, 0.3, "#cbd5e1", "#475569", guy("#0f172a", 1)),
                pn(0.5, 0.0, 0.5, 0.3, "#60a5fa", "#1e3a8a", combo(city("#0b1530", 14), heli("#020617"))),
                pn(0.0, 0.3, 0.34, 0.45, "#facc15", "#78350f", guy("#1c1917", 2, 0.9)),
                pn(0.34, 0.3, 0.33, 0.45, "#94a3b8", "#1e293b", guy("#020617", 0, 0.9)),
                pn(0.67, 0.3, 0.33, 0.22, "#ef4444", "#450a0a", car("#111827")),
                pn(0.67, 0.52, 0.33, 0.23, "#a3e635", "#14532d", guy("#052e16", 3)),
                pn(0.0, 0.75, 1.0, 0.25, "#0a0a0a", "#000", null)))
            val F = coverFrame(x0, w, h)
            gtaLogo(ctx, F.X(0.5), F.Y(0.81), F.S * 1.1, "Liberty City", "#facc15", GTA_FONT(F.S * 11), "#000")
            ctx.textAlign = "center"; outlinedText(ctx, "STORIES", F.X(0.5), F.Y(0.97), GTA_FONT(F.S * 6), "#ffffff", "#000", F.S); ctx.textAlign = "left"
        },
        Game("Grand Theft Auto: Vice City Stories", "Rockstar Games", 2006, "M", 1, "#fb7185", "gta3d", "#fb7185", "synthwave", "ps2", theme3d = "vcs") { ctx, x0, w, h ->
            panelCover(ctx, x0, w, h, listOf(
                pn(0.0, 0.0, 0.6, 0.32, "#fdba74", "#db2777", combo(palms("#4a044e"), guy("#2a0a2e", 1))),
                pn(0.6, 0.0, 0.4, 0.32, "#67e8f9", "#0e7490", guy("#083344", 2)),
                pn(0.0, 0.32, 0.4, 0.43, "#f472b6", "#701a75", guy("#2e1065", 0, 0.9)),
                pn(0.4, 0.32, 0.6, 0.2, "#fde047", "#f97316", car("#0ea5e9", true)),
                pn(0.4, 0.52, 0.3, 0.23, "#a78bfa", "#4c1d95", heli("#1e1b4b")),
                pn(0.7, 0.52, 0.3, 0.23, "#fb923c", "#7c2d12", guy("#1c0a00", 3)),
                pn(0.0, 0.75, 1.0, 0.25, "#0a0a0a", "#000", null)))
            val F = coverFrame(x0, w, h)
            gtaLogo(ctx, F.X(0.5), F.Y(0.81), F.S * 1.1, "Vice City", "#ff4fa3", "italic bold ${F.S * 14}px \"Brush Script MT\", Georgia, serif", "#ffffff")
            ctx.textAlign = "center"; outlinedText(ctx, "STORIES", F.X(0.5), F.Y(0.975), GTA_FONT(F.S * 5.5), "#ffffff", "#000", F.S); ctx.textAlign = "left"
        },
        Game("Grand Theft Auto IV", "Rockstar Games", 2008, "M", 1, "#d6d3d1", "gta3d", "#a8a29e", "electro", "ps3", theme3d = "iv") { ctx, x0, w, h ->
            panelCover(ctx, x0, w, h, listOf(
                pn(0.0, 0.0, 0.34, 0.3, "#d6d3d1", "#57534e", guy("#1c1917", 2)),
                pn(0.34, 0.0, 0.32, 0.3, "#a8a29e", "#292524", guy("#0c0a09", 1)),
                pn(0.66, 0.0, 0.34, 0.3, "#78716c", "#1c1917", heli("#0c0a09")),
                pn(0.0, 0.3, 0.55, 0.45, "#9ca3af", "#3f3f46") { c2, x, y, pw, ph ->
                    // Statue of Happiness holding up a coffee cup
                    val s = min(pw, ph) / 40
                    skyline(c2, x, x + pw, y + ph, s, "#27272a", 31)
                    c2.fillStyle = "#5f7a6a"
                    fillPoly(c2, arrayOf(p(x + pw * 0.45, y + ph), p(x + pw * 0.48, y + ph * 0.35), p(x + pw * 0.56, y + ph * 0.35), p(x + pw * 0.6, y + ph)))
                    circle(c2, x + pw * 0.52, y + ph * 0.3, 3 * s)
                    c2.fillRect(x + pw * 0.56, y + ph * 0.1, 1.6 * s, ph * 0.25)
                    c2.fillRect(x + pw * 0.545, y + ph * 0.06, 4 * s, 3 * s)
                },
                pn(0.55, 0.3, 0.45, 0.22, "#b45309", "#422006", car("#3f3f46")),
                pn(0.55, 0.52, 0.45, 0.23, "#e7e5e4", "#44403c", guy("#0c0a09", 3)),
                pn(0.0, 0.75, 1.0, 0.25, "#0a0a0a", "#000", null)))
            val F = coverFrame(x0, w, h)
            gtaLogo(ctx, F.X(0.5), F.Y(0.82), F.S * 1.2, null)
            ctx.textAlign = "center"
            outlinedText(ctx, "IV", F.X(0.5), F.Y(0.975), "bold ${F.S * 17}px Georgia, serif", "#ffffff", "#000", F.S * 1.6)
            ctx.textAlign = "left"
        },
        Game("Grand Theft Auto: Chinatown Wars", "Rockstar Games", 2009, "M", 1, "#facc15", "gtatop", "#ef4444", "pentatonic", "psp", topTheme = "ctw") { ctx, x0, w, h ->
            panelCover(ctx, x0, w, h, listOf(
                pn(0.0, 0.0, 0.5, 0.36, "#fde047", "#ea580c", guy("#000", 1)),
                pn(0.5, 0.0, 0.5, 0.36, "#ef4444", "#7f1d1d", guy("#000", 2)),
                pn(0.0, 0.36, 0.34, 0.39, "#22d3ee", "#1e3a8a", car("#facc15")),
                pn(0.34, 0.36, 0.32, 0.39, "#dc2626", "#450a0a") { c2, x, y, pw, ph ->
                    c2.fillStyle = "#facc15"; c2.beginPath()
                    for (i in 0 until 24) { val a = i / 24.0 * PI * 2; val r = (if (i % 2 == 1) 0.18 else 0.32) * min(pw, ph); c2.lineTo(x + pw / 2 + cos(a) * r, y + ph / 2 + sin(a) * r) }
                    c2.fill()
                },
                pn(0.66, 0.36, 0.34, 0.39, "#a3e635", "#14532d", guy("#000", 3)),
                pn(0.0, 0.75, 1.0, 0.25, "#0a0a0a", "#000", null)))
            val F = coverFrame(x0, w, h)
            gtaLogo(ctx, F.X(0.5), F.Y(0.82), F.S * 1.1, "Chinatown Wars", "#ef4444", GTA_FONT(F.S * 10), "#facc15")
        },
        Game("Grand Theft Auto V", "Rockstar Games", 2013, "M", 1, "#4ade80", "gta3d", "#4ade80", "nonstoppop", "ps4", theme3d = "v") { ctx, x0, w, h ->
            panelCover(ctx, x0, w, h, listOf(
                // The selfie on the beach across the top
                pn(0.0, 0.0, 1.0, 0.3, "#7dd3fc", "#0369a1") { c2, x, y, pw, ph ->
                    c2.fillStyle = "#fde68a"; c2.fillRect(x, y + ph * 0.75, pw, ph * 0.25)
                    figure(c2, x + pw * 0.38, y + ph * 1.02, ph / 36, "#7c2d12", 1)
                    c2.fillStyle = "#dc2626"; c2.fillRect(x + pw * 0.355, y + ph * 0.42, pw * 0.05, ph * 0.06)
                    palm(c2, x + pw * 0.85, y + ph, ph / 55, "#0f3b2e")
                },
                pn(0.0, 0.3, 0.34, 0.24, "#d4d4d8", "#3f3f46", guy("#18181b", 2, 0.95, Deco(jacket = "#1f2a44", tie = "#b91c1c", skin = "#e0b48a", hair = "buzz", hairCol = "#3a2a1a"))),
                pn(0.34, 0.3, 0.32, 0.24, "#86efac", "#14532d", guy("#052e16", 1, 0.95, Deco(shirt = "#15803d", skin = "#5a3a24", hair = "buzz", hairCol = "#120a06"))),
                pn(0.66, 0.3, 0.34, 0.24, "#fca5a5", "#7f1d1d", guy("#1c0a0a", 0, 0.95, Deco(shirt = "#f1f0e8", pattern = "#9a3412", skin = "#e3b08c", hair = "buzz", hairCol = "#4a3426"))),
                pn(0.0, 0.54, 0.4, 0.21, "#fdba74", "#9a3412", heli("#1c1917")),
                pn(0.4, 0.54, 0.3, 0.21, "#60a5fa", "#1e3a8a", car("#f8fafc")),
                pn(0.7, 0.54, 0.3, 0.21, "#fde047", "#a16207", guy("#000", 3)),
                pn(0.0, 0.75, 1.0, 0.25, "#0a0a0a", "#000", null)))
            val F = coverFrame(x0, w, h)
            gtaLogo(ctx, F.X(0.42), F.Y(0.84), F.S * 1.05, null)
            // Money-green V
            val vg = ctx.createLinearGradient(0, F.Y(0.77), 0, F.Y(0.99)); vg.addColorStop(0, "#bbf7d0"); vg.addColorStop(0.5, "#22c55e"); vg.addColorStop(1, "#14532d")
            ctx.textAlign = "center"
            outlinedText(ctx, "V", F.X(0.83), F.Y(0.975), "bold ${F.S * 24}px Georgia, serif", vg, "#ffffff", F.S * 1.6)
            outlinedText(ctx, "FIVE", F.X(0.42), F.Y(0.95), GTA_FONT(F.S * 6), "#4ade80", "#000", F.S)
            ctx.textAlign = "left"
        },
        Game("Grand Theft Auto VI", "Rockstar Games", 2026, "M", 1, "#f0abfc", "gta3d", "#f472b6", "trap", "ps5", theme3d = "vi") { ctx, x0, w, h ->
            val F = coverFrame(x0, w, h)
            ctx.fillStyle = vgrad(ctx, h, 0.0 to "#2a0b55", 0.35 to "#b02a8a", 0.6 to "#ff7a59", 0.78 to "#ffc46b", 1.0 to "#14082b"); ctx.fillRect(x0, 0, F.W, h)
            ctx.fillStyle = "rgba(255, 220, 140, 0.9)"; circle(ctx, F.X(0.5), F.Y(0.62), F.S * 14)
            ctx.fillStyle = "#ff7a59"
            for (i in 0 until 5) ctx.fillRect(x0, F.Y(0.62 + i * 0.03), F.W, F.S * (0.6 + i * 0.3))
            skyline(ctx, x0, w, F.Y(0.72), F.S * 0.9, "#3b0d4a", 61)
            ctx.fillStyle = "#1d0b33"; ctx.fillRect(x0, F.Y(0.72), F.W, h * 0.28)
            ctx.fillStyle = "rgba(255, 150, 120, 0.35)"
            for (i in 0 until 8) ctx.fillRect(F.X(0.4 + (i % 3) * 0.04), F.Y(0.74 + i * 0.02), F.W * (0.2 - i * 0.015), F.S * 0.6)
            palm(ctx, F.X(0.1), F.Y(0.96), F.S * 1.5, "#12061f")
            palm(ctx, F.X(0.88), F.Y(0.98), F.S * 1.8, "#12061f")
            // Lucia & Jason
            figure(ctx, F.X(0.4), F.Y(0.92), F.S * 1.45, "#12061f", 2, Deco(shirt = "#ff5fa2", skin = "#b8835e", hair = "long", hairCol = "#1a0f0a"))
            figure(ctx, F.X(0.58), F.Y(0.93), F.S * 1.55, "#12061f", 1, Deco(shirt = "#f5f0e6", skin = "#e2b48c", hair = "cap", hairCol = "#1e293b"))
            // VI logo
            val lg = ctx.createLinearGradient(F.X(0.3), F.Y(0.12), F.X(0.7), F.Y(0.34)); lg.addColorStop(0, "#ffd56b"); lg.addColorStop(0.5, "#ff5fa2"); lg.addColorStop(1, "#7b5cff")
            ctx.textAlign = "center"
            outlinedText(ctx, "GRAND THEFT AUTO", F.X(0.5), F.Y(0.1), GTA_FONT(F.S * 6.5), "#ffffff", "#1a0630", F.S * 1.2)
            outlinedText(ctx, "VI", F.X(0.5), F.Y(0.36), "bold ${F.S * 30}px Georgia, serif", lg, "#ffffff", F.S * 1.4)
            ctx.textAlign = "left"
        }
    )

    /** PS1 shelf games, then Bandi-coot Rush, then every GTA (the HTML's ALL_GAMES order). */
    val ALL_GAMES: List<Game> = PS1_GAMES + BANDICOOT_RUSH + GTA_GAMES
    val BANDICOOT_INDEX = PS1_GAMES.size
    val GTA_FIRST_INDEX = PS1_GAMES.size + 1

    // ---- Case builders ----
    /** buildPs1JewelCase: front cover, back cover, black spine; thicker "fat" case for multi-disc games. */
    fun buildPs1JewelCase(game: Game, matJewelPlastic: Material): Mesh {
        val front = createCanvasTex(128, 112, weather = Weather(seed = (game.year + game.title.length).toDouble(), blotch = 0.12, speckle = 0.05, edge = 0.14, scratches = 6)) { ctx, w, h ->
            game.art(ctx, 14.0, w.toDouble(), h.toDouble())
            ctx.fillStyle = "#050507"; ctx.fillRect(0, 0, 14, h)
            drawPsMark(ctx, 3, 4, 4)
            ctx.save(); ctx.translate(10, h - 18); ctx.rotate(-PI / 2)
            ctx.fillStyle = "#e5e7eb"; ctx.font = "bold 6px sans-serif"; ctx.textAlign = "left"; ctx.fillText("PlayStation", 0, 0)
            ctx.restore()
            ctx.fillStyle = "#ffffff"; ctx.fillRect(2, h - 15, 10, 13)
            ctx.fillStyle = "#000000"; ctx.font = "bold 9px Impact, sans-serif"; ctx.textAlign = "center"; ctx.fillText(game.esrb, 7, h - 5)
            // Light scratches and glare on the clear plastic over the insert
            ctx.strokeStyle = "rgba(255, 255, 255, 0.10)"; ctx.lineWidth = 3f
            ctx.beginPath(); ctx.moveTo(w * 0.55, 0); ctx.lineTo(w * 0.2, h); ctx.stroke()
        }.tex
        val back = createCanvasTex(64, 56, weather = Weather(seed = game.year * 3.0, blotch = 0.1, scratches = 3)) { ctx, w, h ->
            ctx.fillStyle = "#0b0d12"; ctx.fillRect(0, 0, w, h)
            ctx.fillStyle = game.spineCol; ctx.fillRect(3, 3, w - 6, 6)
            arrayOf(intArrayOf(4, 13), intArrayOf(24, 13), intArrayOf(44, 13)).forEachIndexed { i, s -> ctx.fillStyle = arrayOf("#334155", "#475569", "#1e293b")[i]; ctx.fillRect(s[0], s[1], 16, 12) }
            ctx.fillStyle = "#9ca3af"
            for (i in 0 until 6) ctx.fillRect(4, 30 + i * 3, 30 + ((i * 13) % 24), 1)
            ctx.fillStyle = "#f8fafc"; ctx.fillRect(w - 18, h - 12, 15, 9)
            ctx.fillStyle = "#000000"
            for (i in 0 until 12) if (i % 3 != 1) ctx.fillRect(w - 17 + i, h - 11, 1, 6)
        }.tex
        val spine = createCanvasTex(16, 128, weather = Weather(seed = game.title.length * 7.0, blotch = 0.12, scratches = 2)) { ctx, w, h ->
            ctx.fillStyle = "#050507"; ctx.fillRect(0, 0, w, h)
            drawPsMark(ctx, 4, 3, 4)
            ctx.save(); ctx.translate(w / 2 + 2, 14); ctx.rotate(PI / 2)
            ctx.fillStyle = game.spineCol; ctx.font = "bold ${if (game.title.length > 26) 6 else 7}px sans-serif"; ctx.textAlign = "left"
            ctx.fillText(game.title.uppercase(), 0, 0)
            ctx.restore()
            ctx.fillStyle = "#ffffff"; ctx.fillRect(3, h - 13, 10, 10)
            ctx.fillStyle = "#000000"; ctx.font = "bold 8px Impact, sans-serif"; ctx.textAlign = "center"; ctx.fillText(game.esrb, 8, h - 5)
        }.tex
        val thick = if (game.discs > 1) 0.044f else 0.022f
        val mesh = Mesh(Geo.box(0.27f, 0.24f, thick), listOf(matJewelPlastic, PS1.material(map = spine), matJewelPlastic, matJewelPlastic,
            PS1.material(map = front), PS1.material(map = back)))
        mesh.castShadow = true; mesh.receiveShadow = true
        mesh.userData["caseHeight"] = 0.24f
        return mesh
    }

    // ---- Keep-cases for PS2 / PS3 / PS4 / PS5 / PSP (taller than a jewel case, with the console's banner) ----
    private class Banner(val bg: String, val fg: String, val text: String, val caseCol: Int)
    private val PLATFORM_BANNER = mapOf(
        "ps2" to Banner("#05070c", "#e5e7eb", "PlayStation 2", 0x111317), "ps3" to Banner("#05070c", "#ffffff", "PS3", 0x1a2742),
        "ps4" to Banner("#1d4ed8", "#ffffff", "PS4", 0x1e3a8a), "ps5" to Banner("#f8fafc", "#111827", "PS5", 0x1d4ed8),
        "psp" to Banner("#05070c", "#e5e7eb", "PSP", 0xb6c2cc)
    )
    fun buildKeepCase(game: Game): Mesh {
        val b = PLATFORM_BANNER.getValue(game.platform)
        val tall = if (game.platform == "psp") 0.3f else 0.38f
        val wide = if (game.platform == "psp") 0.19f else 0.27f
        val front = createCanvasTex(192, (192 * tall / wide).roundToInt(), weather = Weather(seed = (game.year + game.title.length).toDouble(), blotch = 0.08, speckle = 0.04, edge = 0.1, scratches = 5)) { ctx, w, h ->
            val band = (h * 0.065).roundToInt()
            ctx.save(); ctx.translate(0, band); game.art(ctx, 0.0, w.toDouble(), (h - band).toDouble()); ctx.restore()
            ctx.fillStyle = b.bg; ctx.fillRect(0, 0, w, band)
            ctx.fillStyle = b.fg; ctx.font = "bold ${(band * 0.62).roundToInt()}px sans-serif"; ctx.textAlign = "left"
            ctx.fillText(b.text, 6, band * 0.74)
            ctx.fillStyle = "#ffffff"; ctx.fillRect(4, h - 30, 18, 26)
            ctx.fillStyle = "#000"; ctx.font = "bold 16px Impact, sans-serif"; ctx.textAlign = "center"; ctx.fillText(game.esrb, 13, h - 10)
            ctx.strokeStyle = "rgba(255, 255, 255, 0.10)"; ctx.lineWidth = 5f
            ctx.beginPath(); ctx.moveTo(w * 0.6, 0); ctx.lineTo(w * 0.15, h); ctx.stroke()
        }.tex
        val spine = createCanvasTex(16, 160, weather = Weather(seed = game.year.toDouble(), blotch = 0.1, scratches = 2)) { ctx, w, h ->
            ctx.fillStyle = b.bg; ctx.fillRect(0, 0, w, 14)
            ctx.fillStyle = "#05070c"; ctx.fillRect(0, 14, w, h - 14)
            ctx.fillStyle = b.fg; ctx.font = "bold 6px sans-serif"; ctx.textAlign = "center"; ctx.fillText(b.text.replace("PlayStation ", "PS"), w / 2, 10)
            ctx.save(); ctx.translate(w / 2 + 2, 20); ctx.rotate(PI / 2)
            ctx.fillStyle = game.spineCol; ctx.font = "bold 7px sans-serif"; ctx.textAlign = "left"
            ctx.fillText(game.title.replace("Grand Theft Auto", "GTA").uppercase(), 0, 0)
            ctx.restore()
        }.tex
        val back = createCanvasTex(64, 90, weather = Weather(seed = game.year * 3.0, blotch = 0.1)) { ctx, w, h ->
            ctx.fillStyle = "#0b0d12"; ctx.fillRect(0, 0, w, h)
            ctx.fillStyle = game.accent; ctx.fillRect(4, 4, w - 8, 5)
            arrayOf(intArrayOf(4, 14), intArrayOf(24, 14), intArrayOf(44, 14), intArrayOf(4, 32), intArrayOf(24, 32), intArrayOf(44, 32)).forEachIndexed { i, s ->
                ctx.fillStyle = arrayOf("#334155", "#475569", "#1e293b")[i % 3]; ctx.fillRect(s[0], s[1], 16, 14)
            }
            ctx.fillStyle = "#9ca3af"
            for (i in 0 until 8) ctx.fillRect(4, 52 + i * 3, 30 + ((i * 13) % 24), 1)
            ctx.fillStyle = "#f8fafc"; ctx.fillRect(w - 18, h - 12, 15, 9)
        }.tex
        val caseMat = PS1.material(color = b.caseCol)
        val mesh = Mesh(Geo.box(wide, tall, 0.03f), listOf(caseMat, PS1.material(map = spine), caseMat, caseMat, PS1.material(map = front), PS1.material(map = back)))
        mesh.castShadow = true; mesh.receiveShadow = true
        mesh.userData["caseHeight"] = tall
        return mesh
    }

    @Suppress("unused") private fun unused() = floor(0.0)
}
