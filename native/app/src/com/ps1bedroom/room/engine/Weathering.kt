package com.ps1bedroom.room.engine

import kotlin.math.floor
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt

// The HTML's weathering toolkit (makeRng, makeNoiseField, makeFbm, weatherCanvas, createCanvasTex), ported 1-to-1.

/** Park-Miller generator, identical sequence to makeRng(seed) in the HTML. */
fun makeRng(seed: Double): () -> Double {
    var s = (floor(seed * 9973) % 2147483646).toLong() + 1
    return {
        s = (s * 16807) % 2147483647
        (s - 1).toDouble() / 2147483646.0
    }
}
fun makeRng(seed: Number) = makeRng(seed.toDouble())

/** Smooth value noise in [0, 1]; tiles when the cell size divides the texture size. */
fun makeNoiseField(w: Int, h: Int, cell: Double, seed: Double): (Double, Double) -> Double {
    val gw = maxOf(1, round(w / cell).toInt())
    val gh = maxOf(1, round(h / cell).toInt())
    val rnd = makeRng(seed)
    val grid = DoubleArray(gw * gh) { rnd() }
    fun g(ix: Int, iy: Int) = grid[(((iy % gh) + gh) % gh) * gw + (((ix % gw) + gw) % gw)]
    return { x, y ->
        val gx = (x / w) * gw; val gy = (y / h) * gh
        val x0 = floor(gx).toInt(); val y0 = floor(gy).toInt()
        val fx = gx - x0; val fy = gy - y0
        val sx = fx * fx * (3 - 2 * fx); val sy = fy * fy * (3 - 2 * fy)
        val a = g(x0, y0); val b = g(x0 + 1, y0); val c = g(x0, y0 + 1); val d = g(x0 + 1, y0 + 1)
        a + (b - a) * sx + (c - a) * sy + (a - b - c + d) * sx * sy
    }
}

fun makeFbm(w: Int, h: Int, seed: Double): (Double, Double) -> Double {
    val base = min(w, h).toDouble()
    val layers = listOf(4, 8, 16, 32).mapIndexed { i, div -> makeNoiseField(w, h, maxOf(2.0, base / div), seed + i * 31) }
    return { x, y -> layers[0](x, y) * 0.5 + layers[1](x, y) * 0.27 + layers[2](x, y) * 0.15 + layers[3](x, y) * 0.08 }
}

/** Options for weatherCanvas (all optional, same meaning as the HTML). */
data class Weather(
    val seed: Double = 1.0, val blotch: Double = 0.0, val speckle: Double = 0.0, val edge: Double = 0.0, val warm: Double = 0.0,
    val worn: Double = 0.0, val stains: Int = 0, val smudges: Int = 0, val scratches: Int = 0, val scuffs: Int = 0, val creases: Int = 0
)

/** Ages a canvas in place: grime, speckle, darker edges, yellowing, wear, then stains / smudges / scratches / scuffs / creases. */
fun weatherCanvas(ctx: Canvas2D, w: Int, h: Int, o: Weather) {
    val rnd = makeRng(if (o.seed == 0.0) 1.0 else o.seed)
    val n = makeFbm(w, h, if (o.seed == 0.0) 1.0 else o.seed)
    val d = ctx.getImageData()
    for (y in 0 until h) for (x in 0 until w) {
        val i = (y * w + x) * 4
        val nv = n(x.toDouble(), y.toDouble())
        var m = 1 - o.blotch * (nv - 0.45) * 1.8 + (rnd() - 0.5) * o.speckle
        if (o.edge > 0) {
            val e = min(min(x, y), min(w - 1 - x, h - 1 - y)) / (min(w, h) * 0.14)
            if (e < 1) m -= o.edge * (1 - e) * (1 - e)
        }
        if (o.worn > 0) {
            val dx = x.toDouble() / w - 0.5; val dy = y.toDouble() / h - 0.5
            m += o.worn * maxOf(0.0, 1 - sqrt(dx * dx + dy * dy) * 2.6) * (0.6 + nv * 0.6)
        }
        var r = d[i] * m; var g = d[i + 1] * m; var b = d[i + 2] * m
        if (o.warm > 0) {
            val k = o.warm * (0.6 + nv * 0.8)
            g *= 1 - k * 0.12; b *= 1 - k * 0.35; r = r * (1 - k * 0.02) + 6 * k
        }
        d[i] = r.coerceIn(0.0, 255.0).toInt(); d[i + 1] = g.coerceIn(0.0, 255.0).toInt(); d[i + 2] = b.coerceIn(0.0, 255.0).toInt()
    }
    ctx.putImageData(d)
    ctx.save()
    ctx.lineWidth = 1f
    val mn = min(w, h)
    repeat(o.stains) {
        val sx = rnd() * w; val sy = rnd() * h; val sr = (0.06 + rnd() * 0.1) * mn
        ctx.fillStyle = "rgba(120, 90, 45, 0.09)"; ctx.beginPath(); ctx.arc(sx, sy, sr, 0, Math.PI * 2); ctx.fill()
        ctx.strokeStyle = "rgba(90, 60, 25, 0.16)"; ctx.beginPath(); ctx.arc(sx, sy, sr * (0.9 + rnd() * 0.1), 0, Math.PI * 2); ctx.stroke()
    }
    repeat(o.smudges) {
        val sx = rnd() * w; val sy = rnd() * h; val sr = (0.05 + rnd() * 0.12) * mn
        val grad = ctx.createRadialGradient(sx, sy, 0, sx, sy, sr)
        grad.addColorStop(0, "rgba(0, 0, 0, 0.16)"); grad.addColorStop(1, "rgba(0, 0, 0, 0)")
        ctx.fillStyle = grad; ctx.fillRect(sx - sr, sy - sr, sr * 2, sr * 2)
    }
    val baseAngle = rnd() * Math.PI
    repeat(o.scratches) {
        val sx = rnd() * w; val sy = rnd() * h; val len = (0.05 + rnd() * 0.2) * mn
        val a = baseAngle + (rnd() - 0.5) * 0.9
        ctx.strokeStyle = if (rnd() < 0.6) "rgba(255, 255, 255, 0.16)" else "rgba(0, 0, 0, 0.18)"
        ctx.beginPath(); ctx.moveTo(sx, sy); ctx.lineTo(sx + Math.cos(a) * len, sy + Math.sin(a) * len); ctx.stroke()
    }
    repeat(o.scuffs) {
        val sx = rnd() * w; val sy = rnd() * h
        ctx.fillStyle = "rgba(10, 10, 14, ${0.12 + rnd() * 0.12})"
        ctx.fillRect(sx.toInt(), sy.toInt(), 2 + (rnd() * 7).toInt(), 1 + (rnd() * 2).toInt())
    }
    repeat(o.creases) {
        val x0 = rnd() * w; val y0 = if (rnd() < 0.5) 0.0 else rnd() * h
        val x1 = rnd() * w; val y1 = h.toDouble()
        ctx.strokeStyle = "rgba(255, 255, 255, 0.14)"; ctx.beginPath(); ctx.moveTo(x0, y0); ctx.lineTo(x1, y1); ctx.stroke()
        ctx.strokeStyle = "rgba(0, 0, 0, 0.14)"; ctx.beginPath(); ctx.moveTo(x0 + 1, y0); ctx.lineTo(x1 + 1, y1); ctx.stroke()
    }
    ctx.restore()
}

class CanvasTex(@JvmField val tex: Texture, @JvmField val ctx: Canvas2D)

/** createCanvasTex from the HTML: draw, optionally weather, nearest-filtered texture, optional repeat. */
fun createCanvasTex(w: Int, h: Int, repeatX: Float = 1f, repeatY: Float = 1f, weather: Weather? = null, draw: (Canvas2D, Int, Int) -> Unit): CanvasTex {
    val ctx = Canvas2D(w, h)
    draw(ctx, w, h)
    if (weather != null) weatherCanvas(ctx, w, h, weather)
    val tex = Texture(ctx.bitmap)
    if (repeatX > 1 || repeatY > 1) { tex.wrapRepeat = true; tex.repeatX = repeatX; tex.repeatY = repeatY }
    return CanvasTex(tex, ctx)
}

/** Math.random() stand-in (the HTML uses it for unseeded texture noise). */
fun rand() = Math.random()
