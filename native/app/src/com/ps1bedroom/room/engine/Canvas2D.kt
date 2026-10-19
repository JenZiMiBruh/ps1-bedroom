package com.ps1bedroom.room.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.PI

/**
 * The HTML Canvas 2D API on top of android.graphics, so the texture-drawing code from the HTML version
 * ports almost line for line: fillStyle / fillRect / paths / arcs / gradients / text / image data.
 */
class Canvas2D(@JvmField val width: Int, @JvmField val height: Int) {
    @JvmField val bitmap: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)
    private val fill = Paint().apply { style = Paint.Style.FILL; isAntiAlias = false }
    private val stroke = Paint().apply { style = Paint.Style.STROKE; isAntiAlias = true; strokeWidth = 1f; strokeCap = Paint.Cap.BUTT }
    private val text = Paint().apply { isAntiAlias = true }
    private var path = Path()
    private var fillShader: Shader? = null
    private var strokeShader: Shader? = null
    private var fillArgb = 0xff000000.toInt()
    private var strokeArgb = 0xff000000.toInt()
    private class State(val fa: Int, val sa: Int, val fs: Shader?, val ss: Shader?, val lw: Float, val ga: Float, val gco: String, val font: String, val align: String, val base: String)
    private val stack = ArrayList<State>()

    var globalAlpha = 1f
    var globalCompositeOperation = "source-over"
        set(v) {
            field = v
            val mode = when (v) {
                "source-atop" -> PorterDuff.Mode.SRC_ATOP
                "destination-in" -> PorterDuff.Mode.DST_IN
                "destination-out" -> PorterDuff.Mode.DST_OUT
                "lighter" -> PorterDuff.Mode.ADD
                "multiply" -> PorterDuff.Mode.MULTIPLY
                "screen" -> PorterDuff.Mode.SCREEN
                else -> null
            }
            val x = mode?.let { PorterDuffXfermode(it) }
            fill.xfermode = x; stroke.xfermode = x; text.xfermode = x
        }
    var lineWidth = 1f
        set(v) { field = v; stroke.strokeWidth = v }
    var lineJoin = "miter"
        set(v) {
            field = v
            val j = when (v) { "round" -> Paint.Join.ROUND; "bevel" -> Paint.Join.BEVEL; else -> Paint.Join.MITER }
            stroke.strokeJoin = j; text.strokeJoin = j
        }
    var imageSmoothingEnabled = false
    var font = "10px sans-serif"
        set(v) { field = v; applyFont() }
    var textAlign = "start"
    var textBaseline = "alphabetic"

    var fillStyle: Any = "#000000"
        set(v) { field = v; if (v is Gradient) { fillShader = v.shader() } else { fillShader = null; fillArgb = parseCss(v.toString()) } }
    var strokeStyle: Any = "#000000"
        set(v) { field = v; if (v is Gradient) { strokeShader = v.shader() } else { strokeShader = null; strokeArgb = parseCss(v.toString()) } }

    private fun prepFill(p: Paint = fill) {
        if (fillShader != null) { p.shader = fillShader; p.color = 0xff000000.toInt(); p.alpha = (globalAlpha * 255).toInt() }
        else { p.shader = null; p.color = withAlpha(fillArgb, globalAlpha) }
    }
    private fun prepStroke() {
        if (strokeShader != null) { stroke.shader = strokeShader; stroke.color = 0xff000000.toInt(); stroke.alpha = (globalAlpha * 255).toInt() }
        else { stroke.shader = null; stroke.color = withAlpha(strokeArgb, globalAlpha) }
    }

    fun fillRect(x: Number, y: Number, w: Number, h: Number) { prepFill(); canvas.drawRect(x.toFloat(), y.toFloat(), x.toFloat() + w.toFloat(), y.toFloat() + h.toFloat(), fill) }
    fun strokeRect(x: Number, y: Number, w: Number, h: Number) { prepStroke(); canvas.drawRect(x.toFloat(), y.toFloat(), x.toFloat() + w.toFloat(), y.toFloat() + h.toFloat(), stroke) }
    private val clearPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    private val imgPaint = Paint()
    private val dstRect = RectF(); private val srcRect = android.graphics.Rect()
    fun clearRect(x: Number, y: Number, w: Number, h: Number) {
        val fx = x.toFloat(); val fy = y.toFloat(); val fw = w.toFloat(); val fh = h.toFloat()
        // whole canvas: wipe the bitmap directly (cheapest)
        if (fx <= 0f && fy <= 0f && fx + fw >= width && fy + fh >= height && canvas.saveCount == 1) { bitmap.eraseColor(0); return }
        canvas.drawRect(fx, fy, fx + fw, fy + fh, clearPaint)
    }

    fun beginPath() { path = Path() }
    fun moveTo(x: Number, y: Number) { path.moveTo(x.toFloat(), y.toFloat()) }
    fun lineTo(x: Number, y: Number) { if (path.isEmpty) path.moveTo(x.toFloat(), y.toFloat()) else path.lineTo(x.toFloat(), y.toFloat()) }
    fun closePath() { path.close() }
    fun quadraticCurveTo(cx: Number, cy: Number, x: Number, y: Number) { path.quadTo(cx.toFloat(), cy.toFloat(), x.toFloat(), y.toFloat()) }
    fun bezierCurveTo(c1x: Number, c1y: Number, c2x: Number, c2y: Number, x: Number, y: Number) {
        path.cubicTo(c1x.toFloat(), c1y.toFloat(), c2x.toFloat(), c2y.toFloat(), x.toFloat(), y.toFloat())
    }
    fun rect(x: Number, y: Number, w: Number, h: Number) { path.addRect(x.toFloat(), y.toFloat(), x.toFloat() + w.toFloat(), y.toFloat() + h.toFloat(), Path.Direction.CW) }
    fun roundRect(x: Number, y: Number, w: Number, h: Number, r: Number) {
        path.addRoundRect(RectF(x.toFloat(), y.toFloat(), x.toFloat() + w.toFloat(), y.toFloat() + h.toFloat()), r.toFloat(), r.toFloat(), Path.Direction.CW)
    }
    fun arc(cx: Number, cy: Number, r: Number, a0: Number, a1: Number, ccw: Boolean = false) =
        ellipse(cx, cy, r, r, 0, a0, a1, ccw)
    fun ellipse(cx: Number, cy: Number, rx: Number, ry: Number, rot: Number, a0: Number, a1: Number, ccw: Boolean = false) {
        val s = Math.toDegrees(a0.toDouble()).toFloat()
        var sweep = Math.toDegrees(a1.toDouble() - a0.toDouble()).toFloat()
        if (!ccw && sweep < 0) sweep = (sweep % 360f) + 360f
        if (ccw && sweep > 0) sweep = (sweep % 360f) - 360f
        if (!ccw && a1.toDouble() - a0.toDouble() >= 2 * PI) sweep = 360f
        if (ccw && a0.toDouble() - a1.toDouble() >= 2 * PI) sweep = -360f
        val oval = RectF(cx.toFloat() - rx.toFloat(), cy.toFloat() - ry.toFloat(), cx.toFloat() + rx.toFloat(), cy.toFloat() + ry.toFloat())
        val sub = Path()
        if (kotlin.math.abs(sweep) >= 360f) sub.addOval(oval, Path.Direction.CW) else sub.arcTo(oval, s, sweep, true)
        if (rot.toFloat() != 0f) {
            val m = android.graphics.Matrix(); m.setRotate(Math.toDegrees(rot.toDouble()).toFloat(), cx.toFloat(), cy.toFloat()); sub.transform(m)
        }
        path.addPath(sub)
    }
    fun fill() { prepFill(); canvas.drawPath(path, fill) }
    fun stroke() { prepStroke(); canvas.drawPath(path, stroke) }

    fun save() { stack.add(State(fillArgb, strokeArgb, fillShader, strokeShader, lineWidth, globalAlpha, globalCompositeOperation, font, textAlign, textBaseline)); canvas.save() }
    fun restore() {
        canvas.restore()
        val s = stack.removeLastOrNull() ?: return
        fillArgb = s.fa; strokeArgb = s.sa; fillShader = s.fs; strokeShader = s.ss; lineWidth = s.lw; globalAlpha = s.ga
        globalCompositeOperation = s.gco; font = s.font; textAlign = s.align; textBaseline = s.base
    }
    fun translate(x: Number, y: Number) { canvas.translate(x.toFloat(), y.toFloat()) }
    private val tm = android.graphics.Matrix(); private val tmv = FloatArray(9)
    /** ctx.setTransform(a, b, c, d, e, f): replaces the current transform. */
    fun setTransform(a: Number, b: Number, c: Number, d: Number, e: Number, f: Number) {
        tmv[0] = a.toFloat(); tmv[1] = c.toFloat(); tmv[2] = e.toFloat()
        tmv[3] = b.toFloat(); tmv[4] = d.toFloat(); tmv[5] = f.toFloat()
        tmv[6] = 0f; tmv[7] = 0f; tmv[8] = 1f
        tm.setValues(tmv)
        @Suppress("DEPRECATION") canvas.setMatrix(tm)
    }
    /** Fast path for per-pixel fills (TV static): an ARGB colour instead of a CSS string, same result. */
    fun fillRectArgb(x: Number, y: Number, w: Number, h: Number, argb: Int) {
        fill.shader = null; fill.color = withAlpha(argb, globalAlpha)
        canvas.drawRect(x.toFloat(), y.toFloat(), x.toFloat() + w.toFloat(), y.toFloat() + h.toFloat(), fill)
    }
    fun rotate(a: Number) { canvas.rotate(Math.toDegrees(a.toDouble()).toFloat()) }
    fun scale(x: Number, y: Number) { canvas.scale(x.toFloat(), y.toFloat()) }
    fun clip() { canvas.clipPath(path) }

    // ---- Gradients ----
    inner class Gradient(private val radial: Boolean, private val a: FloatArray) {
        private val stops = ArrayList<Pair<Float, Int>>()
        fun addColorStop(o: Number, css: String) { stops += o.toFloat() to parseCss(css) }
        fun shader(): Shader {
            val s = stops.sortedBy { it.first }
            val cols = s.map { it.second }.toIntArray(); val pos = s.map { it.first }.toFloatArray()
            val c2 = if (cols.size == 1) intArrayOf(cols[0], cols[0]) else cols
            val p2 = if (pos.size == 1) floatArrayOf(0f, 1f) else pos
            return if (!radial) LinearGradient(a[0], a[1], a[2], a[3], c2, p2, Shader.TileMode.CLAMP)
            else RadialGradient(a[3], a[4], maxOf(0.001f, a[5]), c2, p2, Shader.TileMode.CLAMP)
        }
    }
    fun createLinearGradient(x0: Number, y0: Number, x1: Number, y1: Number) = Gradient(false, floatArrayOf(x0.toFloat(), y0.toFloat(), x1.toFloat(), y1.toFloat()))
    /** Android only has a single-circle radial gradient: uses the outer circle (the HTML always uses the same centre). */
    fun createRadialGradient(x0: Number, y0: Number, r0: Number, x1: Number, y1: Number, r1: Number) =
        Gradient(true, floatArrayOf(x0.toFloat(), y0.toFloat(), r0.toFloat(), x1.toFloat(), y1.toFloat(), r1.toFloat()))

    // ---- Text ----
    private fun applyFont() {
        val m = Regex("(\\d+(?:\\.\\d+)?)px\\s*(.*)").find(font) ?: return
        text.textSize = m.groupValues[1].toFloat()
        val pre = font.substring(0, m.range.first).lowercase()
        val family = m.groupValues[2].lowercase()
        val bold = pre.contains("bold") || pre.contains("700") || pre.contains("800") || pre.contains("900")
        val italic = pre.contains("italic")
        val tf = when {
            family.contains("mono") || family.contains("courier") -> Typeface.MONOSPACE
            family.contains("serif") && !family.contains("sans") || family.contains("georgia") -> Typeface.SERIF
            else -> Typeface.SANS_SERIF
        }
        text.typeface = Typeface.create(tf, when { bold && italic -> Typeface.BOLD_ITALIC; bold -> Typeface.BOLD; italic -> Typeface.ITALIC; else -> Typeface.NORMAL })
    }
    private fun textX(x: Float, w: Float) = when (textAlign) { "center" -> x - w / 2; "right", "end" -> x - w; else -> x }
    private fun textY(y: Float): Float {
        val fm = text.fontMetrics
        return when (textBaseline) { "top", "hanging" -> y - fm.ascent; "middle" -> y - (fm.ascent + fm.descent) / 2; "bottom" -> y - fm.descent; else -> y }
    }
    fun measureText(s: String): Float = text.measureText(s)
    fun fillText(s: String, x: Number, y: Number, maxWidth: Number? = null) {
        if (fillShader != null) { text.shader = fillShader; text.color = 0xff000000.toInt(); text.alpha = (globalAlpha * 255).toInt() } else { text.shader = null; text.color = withAlpha(fillArgb, globalAlpha) }
        text.style = Paint.Style.FILL
        drawTextScaled(s, x.toFloat(), y.toFloat(), maxWidth?.toFloat())
    }
    fun strokeText(s: String, x: Number, y: Number) {
        text.shader = null; text.color = withAlpha(strokeArgb, globalAlpha); text.style = Paint.Style.STROKE; text.strokeWidth = lineWidth
        drawTextScaled(s, x.toFloat(), y.toFloat(), null)
        text.style = Paint.Style.FILL
    }
    private fun drawTextScaled(s: String, x: Float, y: Float, maxWidth: Float?) {
        val w = text.measureText(s)
        if (maxWidth != null && w > maxWidth && w > 0) {
            canvas.save(); val k = maxWidth / w
            val ax = textX(x, maxWidth)
            canvas.translate(ax, 0f); canvas.scale(k, 1f)
            canvas.drawText(s, 0f, textY(y), text)
            canvas.restore()
        } else canvas.drawText(s, textX(x, w), textY(y), text)
    }

    // ---- Images and pixels ----
    fun drawImage(src: Canvas2D, dx: Number, dy: Number) = drawImage(src.bitmap, dx, dy, src.width, src.height)
    fun drawImage(src: Canvas2D, dx: Number, dy: Number, dw: Number, dh: Number) = drawImage(src.bitmap, dx, dy, dw, dh)
    fun drawImage(src: Bitmap, dx: Number, dy: Number, dw: Number, dh: Number) {
        val p = imgPaint; p.isFilterBitmap = imageSmoothingEnabled; p.alpha = (globalAlpha * 255).toInt(); p.xfermode = fill.xfermode
        dstRect.set(dx.toFloat(), dy.toFloat(), dx.toFloat() + dw.toFloat(), dy.toFloat() + dh.toFloat())
        canvas.drawBitmap(src, null, dstRect, p)
    }
    fun drawImage(src: Canvas2D, sx: Number, sy: Number, sw: Number, sh: Number, dx: Number, dy: Number, dw: Number, dh: Number) {
        val p = imgPaint; p.isFilterBitmap = imageSmoothingEnabled; p.alpha = (globalAlpha * 255).toInt(); p.xfermode = fill.xfermode
        srcRect.set(sx.toInt(), sy.toInt(), (sx.toFloat() + sw.toFloat()).toInt(), (sy.toFloat() + sh.toFloat()).toInt())
        dstRect.set(dx.toFloat(), dy.toFloat(), dx.toFloat() + dw.toFloat(), dy.toFloat() + dh.toFloat())
        canvas.drawBitmap(src.bitmap, srcRect, dstRect, p)
    }
    /** RGBA bytes like ImageData.data (0..255 per channel, row by row). */
    fun getImageData(): IntArray {
        val px = IntArray(width * height); bitmap.getPixels(px, 0, width, 0, 0, width, height)
        val d = IntArray(width * height * 4)
        for (i in px.indices) { val c = px[i]; d[i * 4] = (c shr 16) and 255; d[i * 4 + 1] = (c shr 8) and 255; d[i * 4 + 2] = c and 255; d[i * 4 + 3] = (c ushr 24) and 255 }
        return d
    }
    fun putImageData(d: IntArray) {
        val px = IntArray(width * height)
        for (i in px.indices) px[i] = (d[i * 4 + 3].coerceIn(0, 255) shl 24) or (d[i * 4].coerceIn(0, 255) shl 16) or (d[i * 4 + 1].coerceIn(0, 255) shl 8) or d[i * 4 + 2].coerceIn(0, 255)
        bitmap.setPixels(px, 0, width, 0, 0, width, height)
    }

    companion object {
        private val named = mapOf("white" to 0xffffffff.toInt(), "black" to 0xff000000.toInt(), "transparent" to 0, "red" to 0xffff0000.toInt())
        fun withAlpha(argb: Int, ga: Float): Int { val a = ((argb ushr 24) * ga).toInt().coerceIn(0, 255); return (a shl 24) or (argb and 0xffffff) }
        /** '#rgb', '#rrggbb', 'rgb()', 'rgba()', 'hsl()' and a few names -> ARGB */
        // Parsed colours are cached: the same few style strings are set over and over every redraw
        private val cssCache = HashMap<String, Int>()
        private val RGB_RE = Regex("rgba?\\(([^)]*)\\)"); private val HSL_RE = Regex("hsla?\\(([^)]*)\\)")
        fun parseCss(s: String): Int {
            cssCache[s]?.let { return it }
            val v = parseCssUncached(s)
            if (cssCache.size > 4096) cssCache.clear()
            cssCache[s] = v
            return v
        }
        private fun parseCssUncached(s: String): Int {
            val t = s.trim().lowercase()
            named[t]?.let { return it }
            if (t.startsWith("#")) {
                val h = t.substring(1)
                val full = if (h.length == 3) "" + h[0] + h[0] + h[1] + h[1] + h[2] + h[2] else h
                return (0xff shl 24) or Integer.parseInt(full.substring(0, 6), 16)
            }
            RGB_RE.find(t)?.let { m ->
                val p = m.groupValues[1].split(",").map { it.trim() }
                val r = p[0].toFloat().toInt().coerceIn(0, 255); val g = p[1].toFloat().toInt().coerceIn(0, 255); val b = p[2].toFloat().toInt().coerceIn(0, 255)
                val a = if (p.size > 3) (p[3].toFloat() * 255).toInt().coerceIn(0, 255) else 255
                return (a shl 24) or (r shl 16) or (g shl 8) or b
            }
            HSL_RE.find(t)?.let { m ->
                val p = m.groupValues[1].split(",").map { it.trim().removeSuffix("%").removeSuffix("deg") }
                val h = p[0].toFloat() / 360f; val sat = p[1].toFloat() / 100f; val l = p[2].toFloat() / 100f
                val q = if (l < 0.5f) l * (1 + sat) else l + sat - l * sat; val pp = 2 * l - q
                fun hue(tt0: Float): Float { var tt = tt0; if (tt < 0) tt += 1f; if (tt > 1) tt -= 1f
                    return when { tt < 1f / 6 -> pp + (q - pp) * 6 * tt; tt < 0.5f -> q; tt < 2f / 3 -> pp + (q - pp) * (2f / 3 - tt) * 6; else -> pp } }
                val r = (hue(h + 1f / 3) * 255).toInt(); val g = (hue(h) * 255).toInt(); val b = (hue(h - 1f / 3) * 255).toInt()
                val a = if (p.size > 3) (p[3].toFloat() * 255).toInt() else 255
                return (a shl 24) or (r shl 16) or (g shl 8) or b
            }
            return 0xff000000.toInt()
        }
    }
}
