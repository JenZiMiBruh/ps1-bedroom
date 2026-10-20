package com.ps1bedroom.room

import android.opengl.GLES30.glGetUniformLocation
import android.opengl.GLES30.glUniform1f
import android.opengl.GLES30.glUniform1i
import android.opengl.GLES30.glUniform2f
import android.opengl.GLES30.glUniform3f
import android.os.SystemClock
import com.ps1bedroom.room.engine.BACK_SIDE
import com.ps1bedroom.room.engine.Canvas2D
import com.ps1bedroom.room.engine.CanvasTex
import com.ps1bedroom.room.engine.Color
import com.ps1bedroom.room.engine.CustomShader
import com.ps1bedroom.room.engine.Geo
import com.ps1bedroom.room.engine.Group
import com.ps1bedroom.room.engine.Material
import com.ps1bedroom.room.engine.Mesh
import com.ps1bedroom.room.engine.PS1
import com.ps1bedroom.room.engine.Scene
import com.ps1bedroom.room.engine.TexBinder
import com.ps1bedroom.room.engine.Texture
import com.ps1bedroom.room.engine.Vec3
import com.ps1bedroom.room.engine.createCanvasTex
import com.ps1bedroom.room.engine.makeFbm
import com.ps1bedroom.room.engine.makeRng
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The sky outside the window (HTML lines 1818-2140 and 3496-3726): a seamless dome with the sky colours, sunset glow,
 * stars and Milky Way; the sun and moon as painted cards; a drifting cloud shell; a horizon ring; and the painted
 * backdrop in front with the skyline, which is redrawn a few times a second for the moving things in the sky.
 */
class Sky(private val scene: Scene, private val env: Environment) {
    companion object {
        const val SKY_W = 512
        const val SKY_H = 256
        val SKY_C = Vec3(0.8f, 1.0f, -4.0f)
        const val SKY_R = 26f
        const val BACKDROP_PX = 24f / SKY_W // metres per backdrop pixel

        private const val SKY_VERT = """#version 300 es
layout(location = 0) in vec3 position;
uniform mat4 modelViewMatrix;
uniform mat4 projectionMatrix;
out vec3 vDir;
void main() { vDir = position; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }"""
        private const val SKY_AZEL = "vec3 d = normalize(vDir); float el = asin(clamp(d.y, -1.0, 1.0)); float u = atan(d.x, -d.z) / 6.2831853 + 0.5;"
        private const val DOME_FRAG = """#version 300 es
precision highp float;
uniform vec3 top, mid, hor, fogCol; uniform sampler2D starTex; uniform vec2 glowDir;
uniform float starAmt, milkyAmt, time, glowAmt, flash, fogAmt, gain;
in vec3 vDir;
out vec4 fragColor;
void main() {
  $SKY_AZEL
  vec3 c = el < 0.03 ? mix(hor, mid, smoothstep(-0.16, 0.03, el)) : mix(mid, top, smoothstep(0.03, 0.36, el));
  vec2 hz = normalize(d.xz + 1e-5);
  c = mix(c, vec3(1.0, 0.55, 0.27), glowAmt * pow(max(0.0, dot(hz, glowDir)), 3.0) * (1.0 - smoothstep(-0.05, 0.45, el)));
  vec4 s = texture(starTex, vec2(u, (el + 0.1) / 1.6708));
  float tw = 0.6 + 0.4 * sin(time * 1.7 + u * 913.0 + el * 377.0);
  c += vec3(0.9, 0.93, 1.0) * (s.r * starAmt * tw + s.g * milkyAmt);
  c = mix(c, vec3(0.80, 0.84, 0.88), flash);
  c = mix(c, fogCol, fogAmt);
  fragColor = vec4(c * gain, 1.0);
}"""
        private const val CLOUD_FRAG = """#version 300 es
precision highp float;
uniform sampler2D cloudTex; uniform float cloudOff, cloudAmt, gain;
in vec3 vDir;
out vec4 fragColor;
void main() {
  $SKY_AZEL
  float v = (el - 0.02) / 0.46;
  if (v < 0.0 || v > 1.0) discard;
  vec4 cl = texture(cloudTex, vec2(fract(u * 2.0 + cloudOff), v));
  float a = cl.a * smoothstep(0.0, 0.1, v) * (1.0 - smoothstep(0.8, 1.0, v)) * cloudAmt;
  fragColor = vec4(cl.rgb * gain, a);
}"""
        /** All the custom sky shaders, for the WebGL shader check. */
        val SHADERS = listOf("SKY_VERT" to SKY_VERT, "DOME_FRAG" to DOME_FRAG, "CLOUD_FRAG" to CLOUD_FRAG)
    }

    // ---- Dome uniforms (skyDomeU) ----
    private class DomeShader(val starTex: Texture) : CustomShader(SKY_VERT, DOME_FRAG) {
        val top = Color(0x0b1030); val mid = Color(0x1a2050); val hor = Color(0x303860)
        var starAmt = 0f; var milkyAmt = 0f; var time = 0f
        var glowX = 0f; var glowY = -1f; var glowAmt = 0f
        var flash = 0f; var fogAmt = 0f; val fogCol = Color(0x8890a0); var gain = 0.85f
        private val loc = IntArray(14)
        override fun locate(p: Int) {
            val names = arrayOf("top", "mid", "hor", "fogCol", "starTex", "glowDir", "starAmt", "milkyAmt", "time", "glowAmt", "flash", "fogAmt", "gain")
            for (i in names.indices) loc[i] = glGetUniformLocation(p, names[i])
        }
        override fun apply(tex: TexBinder) {
            glUniform3f(loc[0], top.r, top.g, top.b); glUniform3f(loc[1], mid.r, mid.g, mid.b); glUniform3f(loc[2], hor.r, hor.g, hor.b)
            glUniform3f(loc[3], fogCol.r, fogCol.g, fogCol.b)
            glUniform1i(loc[4], tex.bind(0, starTex))
            glUniform2f(loc[5], glowX, glowY)
            glUniform1f(loc[6], starAmt); glUniform1f(loc[7], milkyAmt); glUniform1f(loc[8], time); glUniform1f(loc[9], glowAmt)
            glUniform1f(loc[10], flash); glUniform1f(loc[11], fogAmt); glUniform1f(loc[12], gain)
        }
    }
    private class CloudShader(val cloudTex: Texture, val dome: DomeShader) : CustomShader(SKY_VERT, CLOUD_FRAG) {
        var cloudOff = 0f; var cloudAmt = 0f
        private val loc = IntArray(4)
        override fun locate(p: Int) { loc[0] = glGetUniformLocation(p, "cloudTex"); loc[1] = glGetUniformLocation(p, "cloudOff"); loc[2] = glGetUniformLocation(p, "cloudAmt"); loc[3] = glGetUniformLocation(p, "gain") }
        override fun apply(tex: TexBinder) {
            glUniform1i(loc[0], tex.bind(0, cloudTex)); glUniform1f(loc[1], cloudOff); glUniform1f(loc[2], cloudAmt); glUniform1f(loc[3], dome.gain)
        }
    }

    // ---- Painted backdrop state ----
    val cityCanvasObj: CanvasTex = createCanvasTex(SKY_W, SKY_H) { _, _, _ -> }
    private val skyRng = makeRng(777)
    // Far and near skyline rows: [x, top, width, roof style]
    private val skylineFar = ArrayList<IntArray>()
    private val skylineNear = ArrayList<IntArray>()
    private class WindowLight(val x: Int, val y: Int, val tone: Double, val owl: Double, val col: String)
    private val windowLights = ArrayList<WindowLight>()
    private val cloudSprites: List<Canvas2D>
    private val cloudLayer = Canvas2D(SKY_W * 2, 150)
    private val cityStaticCache = Canvas2D(SKY_W, SKY_H)
    var cityStaticDirty = true
    private var lastSkyRebuild = 0L
    // Where the sun and moon land on the backdrop (canvas pixels), from updateCelestialPosition
    var sunX = -99f; var sunY = -99f; var moonX = -99f; var moonY = -99f
    var lightningFlash = 0f
    var outsideEnv = "city"
    /** The 15 places outside the window (set once they are built). */
    var placesPaint: PlacesPaint? = null

    // ---- 3D layers ----
    val skyBoxGroup = Group()
    private val dome: DomeShader
    private val clouds: CloudShader
    private val sunCard = createCanvasTex(128, 128) { _, _, _ -> }
    private val moonCard = createCanvasTex(128, 128) { _, _, _ -> }
    private val sunMesh: Mesh
    private val moonMesh: Mesh
    private val ringObj = createCanvasTex(256, 256) { _, _, _ -> }
    val cityBackdrop: Mesh
    private val backdropMask = Canvas2D(SKY_W, SKY_H)

    init {
        run {
            var x = -4
            while (x < SKY_W) {
                val w = 14 + floor(skyRng() * 26).toInt()
                val y = 150 + floor(skyRng() * 50).toInt(); val st = floor(skyRng() * 5).toInt()
                skylineFar += intArrayOf(x, y, w, st)
                x += w + floor(skyRng() * 4).toInt()
            }
            x = -6
            while (x < SKY_W) {
                val w = 22 + floor(skyRng() * 40).toInt()
                val y = 140 + floor(skyRng() * 70).toInt(); val st = floor(skyRng() * 6).toInt()
                skylineNear += intArrayOf(x, y, w, st)
                x += w + 2 + floor(skyRng() * 10).toInt()
            }
            for (b in skylineNear) {
                val bx = b[0]; val by = b[1]; val bw = b[2]
                var wy = by + 6
                while (wy < 246) {
                    var wx = bx + 3
                    while (wx < bx + bw - 3) {
                        val tone = skyRng(); val owl = skyRng()
                        val col = if (skyRng() > 0.8) "#9fd8ff" else (if (skyRng() > 0.5) "#ffd27d" else "#ffe9b0")
                        windowLights += WindowLight(wx, wy, tone, owl, col)
                        wx += 5
                    }
                    wy += 6
                }
            }
            // (the HTML's backdrop star list: the dome draws the stars now, but it still advances the same random sequence)
            repeat(170 * 5) { skyRng() }
        }
        // Soft cloud sprites (white with alpha), tinted per time of day when the cache rebuilds
        cloudSprites = (0 until 4).map { s ->
            val c = Canvas2D(128, 40)
            val n = makeFbm(128, 40, 300.0 + s * 17)
            val img = IntArray(128 * 40 * 4)
            for (y in 0 until 40) for (x in 0 until 128) {
                val dx = (x - 64) / 64.0; val dy = (y - 22) / 18.0
                val shape = max(0.0, 1 - dx * dx - dy * dy * 1.6)
                val a = max(0.0, n(x.toDouble(), y.toDouble()) * 1.6 - 0.55) * shape * 2.2
                val i = (y * 128 + x) * 4
                img[i] = 255; img[i + 1] = 255; img[i + 2] = 255
                img[i + 3] = min(255.0, a * 255).toInt()
            }
            c.putImageData(img)
            c
        }
        cloudLayer.imageSmoothingEnabled = true // a browser canvas smooths scaled images by default

        // ---- Sky dome ----
        val starObj = createCanvasTex(512, 256) { ctx, w, h ->
            ctx.fillStyle = "#000"; ctx.fillRect(0, 0, w, h)
            val r = makeRng(4242)
            fun vOf(el: Double) = h - ((el + 0.1) / (PI / 2 + 0.1)) * h
            for (i in 0 until 900) {
                val el = kotlin.math.asin(0.03 + r() * 0.97); val x = r() * w; val y = vOf(el); val b = 90 + r() * 165
                ctx.fillStyle = "rgb(${b.toInt()},0,0)"; ctx.fillRect(x.toInt(), y.toInt(), if (r() > 0.94) 2 else 1, 1)
            }
            ctx.globalCompositeOperation = "lighter"
            for (x in 0 until w) {
                val c = vOf(0.75 + 0.45 * sin((x.toDouble() / w) * PI * 2 + 1.2))
                val g = ctx.createLinearGradient(0, c - 14, 0, c + 14)
                g.addColorStop(0, "rgba(0,0,0,0)"); g.addColorStop(0.5, "rgba(0,160,0,1)"); g.addColorStop(1, "rgba(0,0,0,0)")
                ctx.fillStyle = g; ctx.fillRect(x, c - 14, 1, 28)
            }
            ctx.globalCompositeOperation = "source-over"
        }
        dome = DomeShader(starObj.tex)
        val domeMesh = Mesh(Geo.sphere(SKY_R, 32, 16), Material(side = BACK_SIDE, depthWrite = false, fog = false, transparent = true, lit = false, ps1 = false, shader = dome))
        domeMesh.position.copy(SKY_C); domeMesh.renderOrder = -6
        skyBoxGroup.add(domeMesh)
        // Sun and moon: little painted cards stuck to the inside of the dome where the sky puts them
        fun skyCard(obj: CanvasTex): Mesh {
            val m = Mesh(Geo.plane(1f, 1f), PS1.basic(map = obj.tex, transparent = true, depthWrite = false, fog = false))
            m.renderOrder = -5; m.visible = false; skyBoxGroup.add(m); return m
        }
        sunMesh = skyCard(sunCard); moonMesh = skyCard(moonCard)
        // Clouds: a slightly smaller shell that wraps the shared cloud strip around the sky and slides it with the wind
        val cloudTex = Texture(cloudLayer.bitmap).apply { nearest = true; linearMin = true }
        clouds = CloudShader(cloudTex, dome)
        val cloudMesh = Mesh(Geo.sphere(SKY_R - 1.5f, 32, 12, 0f, (PI * 2).toFloat(), 0f, (PI * 0.5).toFloat()),
            Material(side = BACK_SIDE, depthWrite = false, fog = false, transparent = true, lit = false, ps1 = false, shader = clouds))
        cloudMesh.position.copy(SKY_C); cloudMesh.renderOrder = -4
        skyBoxGroup.add(cloudMesh)
        // Horizon ring: each place's generic skyline, repeated all the way round (sky above it is see-through)
        ringObj.tex.wrapRepeat = true; ringObj.tex.repeatX = 8f; ringObj.tex.repeatY = 1f
        val sideSkyMat = PS1.basic(color = 0xd9d9d9, map = ringObj.tex, transparent = true, depthWrite = false, fog = false, side = BACK_SIDE)
        val skyRing = Mesh(Geo.cylinder(SKY_R - 5, SKY_R - 5, 12f, 48, 1, true), sideSkyMat)
        skyRing.position.set(SKY_C.x, 3.6f, SKY_C.z); skyRing.renderOrder = -3
        skyBoxGroup.add(skyRing)

        // The painted backdrop blends over all of that (it lives in the exterior world group in the HTML)
        val cityTex = cityCanvasObj.tex
        cityBackdrop = Mesh(Geo.plane(24.0f, 12.0f, 6, 4), PS1.material(map = cityTex, emissive = 0xffffff, emissiveMap = cityTex, emissiveIntensity = 0.75f))
        cityBackdrop.position.set(0.8f, 3.6f, -15.5f)
        cityBackdrop.material.transparent = true
        cityBackdrop.material.depthWrite = false
        cityBackdrop.renderOrder = -2
        run {
            val img = IntArray(SKY_W * SKY_H * 4)
            for (y in 0 until SKY_H) for (x in 0 until SKY_W) {
                val fx = min(1.0, min(x, SKY_W - 1 - x) / (SKY_W * 0.13))
                val fy = min(1.0, y / (SKY_H * 0.22))
                val a = fx * fx * (3 - 2 * fx) * fy * fy * (3 - 2 * fy)
                img[(y * SKY_W + x) * 4 + 3] = (a * 255).toInt()
            }
            backdropMask.putImageData(img)
        }
    }

    /** Adds the sky layers to the scene (the backdrop goes in the exterior group with the rest of the outside). */
    fun addTo(exteriorWorldGroup: Group) {
        exteriorWorldGroup.add(cityBackdrop)
        scene.add(skyBoxGroup)
    }

    // ---- Sky colours by sun elevation (top, middle, horizon) ----
    private val SKY_KEYS = arrayOf(
        doubleArrayOf(-18.0, 3.0, 5.0, 15.0, 7.0, 12.0, 34.0, 14.0, 22.0, 52.0),
        doubleArrayOf(-10.0, 5.0, 9.0, 28.0, 13.0, 24.0, 66.0, 32.0, 44.0, 92.0),
        doubleArrayOf(-5.0, 14.0, 20.0, 62.0, 56.0, 40.0, 104.0, 182.0, 82.0, 120.0),
        doubleArrayOf(0.0, 28.0, 40.0, 104.0, 124.0, 58.0, 112.0, 244.0, 138.0, 74.0),
        doubleArrayOf(5.0, 44.0, 84.0, 170.0, 196.0, 122.0, 110.0, 255.0, 196.0, 122.0),
        doubleArrayOf(14.0, 37.0, 99.0, 235.0, 95.0, 158.0, 232.0, 191.0, 224.0, 255.0),
        doubleArrayOf(50.0, 29.0, 78.0, 216.0, 79.0, 149.0, 240.0, 168.0, 216.0, 255.0)
    )
    private val VAPOR_KEYS = arrayOf(
        doubleArrayOf(-18.0, 16.0, 3.0, 40.0, 60.0, 10.0, 90.0, 140.0, 30.0, 130.0),
        doubleArrayOf(0.0, 40.0, 8.0, 80.0, 150.0, 40.0, 160.0, 255.0, 110.0, 170.0),
        doubleArrayOf(50.0, 90.0, 40.0, 170.0, 220.0, 110.0, 210.0, 255.0, 190.0, 220.0)
    )
    private val skyCols = DoubleArray(9)
    /** The sky's horizon colour right now (the light rays and dust pick it up). */
    val horizonColor: com.ps1bedroom.room.engine.Color get() = dome.hor
    /** skyColorsAt: fills skyCols with top, mid, horizon RGB (0-255, already truncated like the HTML's `| 0`). */
    private fun skyColorsAt(deg: Double) {
        val keys = if (env.skyStyle == "vapor") VAPOR_KEYS else SKY_KEYS
        var a = keys[0]; var b = keys[keys.size - 1]
        for (i in 0 until keys.size - 1) if (deg >= keys[i][0] && deg <= keys[i + 1][0]) { a = keys[i]; b = keys[i + 1]; break }
        if (deg < keys[0][0]) b = a
        if (deg > keys[keys.size - 1][0]) a = b
        val k = if (a === b) 0.0 else (deg - a[0]) / (b[0] - a[0])
        for (i in 0 until 9) skyCols[i] = a[i + 1] + (b[i + 1] - a[i + 1]) * k
        // Clouds wash the sky out toward grey (lit orange-grey by the city at night)
        val grey = min(0.85, env.W.cloud * 0.85)
        if (grey > 0) {
            val night = 1 - env.daylight
            for (c in 0 until 3) {
                val r = skyCols[c * 3]; val g = skyCols[c * 3 + 1]; val bb = skyCols[c * 3 + 2]
                val lum = r * 0.3 + g * 0.59 + bb * 0.11
                val gr = lum * 0.95 + night * 14; val gg = lum * 0.97 + night * 10; val gb = lum * 1.02 + night * 8
                skyCols[c * 3] = r + (gr - r) * grey; skyCols[c * 3 + 1] = g + (gg - g) * grey; skyCols[c * 3 + 2] = bb + (gb - bb) * grey
            }
        }
        if (env.skyStyle == "blood" && env.daylight < 0.5) for (c in 0 until 3) {
            skyCols[c * 3] = skyCols[c * 3] * 1.25 + 18; skyCols[c * 3 + 1] *= 0.75; skyCols[c * 3 + 2] *= 0.7
        }
        for (i in 0 until 9) skyCols[i] = trunc(skyCols[i])
    }
    private fun trunc(v: Double) = if (v < 0) kotlin.math.ceil(v) else floor(v)

    // ---- Painting helpers (shared with the places outside) ----
    fun mixC(a: IntArray, b: IntArray, k: Double): IntArray { val kk = max(0.0, min(1.0, k)); return IntArray(3) { i -> Math.round(a[i] + (b[i] - a[i]) * kk).toInt() } }
    fun rgbC(c: IntArray, a: Double = 1.0) = if (a >= 1) "rgb(${c[0]},${c[1]},${c[2]})" else "rgba(${c[0]},${c[1]},${c[2]},${jsNum(a)})"
    fun hazeC() = mixC(mixC(intArrayOf(16, 22, 44), intArrayOf(168, 186, 210), env.daylight), intArrayOf(255, 160, 120), env.twilight * 0.35)
    // night colour -> day colour, warmed at sunset and pushed toward the horizon haze (more in fog)
    fun toneC(nightC: IntArray, dayC: IntArray, haze: Double = 0.0): IntArray {
        var c = mixC(nightC, dayC, env.daylight)
        c = mixC(c, intArrayOf(255, 150, 105), env.twilight * 0.18)
        return mixC(c, hazeC(), min(0.9, haze + env.W.fog * 0.45))
    }
    fun T(n: IntArray, d: IntArray, hz: Double = 0.0) = rgbC(toneC(n, d, hz))
    fun isNight() = 1 - env.daylight > 0.4
    // Windows light up in the evening, fewer in the small hours
    fun litWindow(ctx: Canvas2D, x: Number, y: Number, w2: Number, h2: Number, k: Double, col: String? = null) {
        val night = 1 - env.daylight
        val act = if (env.hour >= 1 && env.hour < 5) 0.18 else (if (env.hour >= 23 || env.hour < 1) 0.4 else 0.65)
        if (night < 0.35 || k > act) { ctx.fillStyle = T(intArrayOf(20, 24, 36), intArrayOf(70, 90, 110)); ctx.fillRect(x, y, w2, h2); return }
        ctx.fillStyle = col ?: "rgba(255, 210, 130, ${jsNum(0.6 + 0.4 * night)})"; ctx.fillRect(x, y, w2, h2)
    }
    /** Number formatting for CSS strings (the parsed value is what matters, not the exact text). */
    private fun jsNum(v: Double): String = if (v == floor(v) && abs(v) < 1e15) v.toLong().toString() else v.toString()

    // ---- Sun & moon disc ----
    private fun drawMoonDisc(ctx: Canvas2D, x: Double, y: Double, r: Double, phase: Double, lit: String, dark: String) {
        ctx.fillStyle = dark
        ctx.beginPath(); ctx.arc(x, y, r, 0, PI * 2); ctx.fill()
        if (phase < 0.03 || phase > 0.97) return // new moon: just the faint dark disc
        val waxing = (phase < 0.5) != (env.lat < 0)
        val k = cos(phase * 2 * PI)
        ctx.fillStyle = lit
        ctx.beginPath()
        ctx.arc(x, y, r, -PI / 2, PI / 2, !waxing)
        val rightHalf = if (waxing) k > 0 else k < 0
        if (rightHalf) ctx.ellipse(x, y, abs(k) * r, r, 0, PI / 2, -PI / 2, true)
        else ctx.ellipse(x, y, abs(k) * r, r, 0, PI / 2, PI * 1.5, false)
        ctx.fill()
        // A few maria on the lit face
        ctx.fillStyle = "rgba(80, 90, 120, 0.25)"
        for (m in arrayOf(doubleArrayOf(-0.3, -0.2, 0.25), doubleArrayOf(0.2, -0.35, 0.18), doubleArrayOf(0.15, 0.25, 0.22), doubleArrayOf(-0.25, 0.3, 0.14))) {
            ctx.beginPath(); ctx.arc(x + m[0] * r, y + m[1] * r, m[2] * r, 0, PI * 2); ctx.fill()
        }
    }
    private fun roofShape(ctx: Canvas2D, x: Int, y: Int, w: Int, style: Int) {
        when (style) {
            1 -> ctx.fillRect(x + w * 0.2, y - 6, w * 0.6, 6)                                     // stepped top
            2 -> ctx.fillRect(x + w / 2.0 - 1, y - 16, 2, 16)                                     // antenna
            3 -> { ctx.beginPath(); ctx.moveTo(x, y); ctx.lineTo(x + w / 2.0, y - 12); ctx.lineTo(x + w, y); ctx.fill() } // spire
            4 -> { ctx.fillRect(x + w * 0.6, y - 9, 7, 7); ctx.fillRect(x + w * 0.6 + 1, y - 2, 1, 2); ctx.fillRect(x + w * 0.6 + 5, y - 2, 1, 2) } // water tower
            5 -> { ctx.fillRect(x + 2, y - 4, w - 4, 4); ctx.fillRect(x + w * 0.3, y - 8, w * 0.4, 4) }
        }
    }

    // ---- Where the sun and moon land on the backdrop (from updateCelestialPosition) ----
    fun projectBodies(eye: Vec3) {
        project(env.sunAz, env.sunEl, eye, true)
        project(env.moonAz, env.moonEl, eye, false)
    }
    private fun project(realAz: Double, realEl: Double, eye: Vec3, sun: Boolean) {
        // The window only frames a narrow, low slice of sky, so the painted sky is squeezed: the whole arc
        // from horizon to zenith and east to west fits the backdrop (the room light keeps the real angle)
        val az = realAz * 0.5
        val el = realEl * 0.24 + 0.012
        val dx = sin(az) * cos(el); val dy = sin(el); val dz = -cos(az) * cos(el)
        if (dz > -0.05) { if (sun) { sunX = -99f; sunY = -99f } else { moonX = -99f; moonY = -99f }; return }
        val tP = (cityBackdrop.position.z - eye.z) / dz
        val px = (((eye.x + dx * tP - (cityBackdrop.position.x - 12)) / 24) * SKY_W).toFloat()
        val py = ((((cityBackdrop.position.y + 6) - (eye.y + dy * tP)) / 12) * SKY_H).toFloat()
        if (sun) { sunX = px; sunY = py } else { moonX = px; moonY = py }
    }

    // Backdrop pixel -> where that spot sits on a sphere of radius rr around the dome centre, seen from the default
    // view (so the sun and moon line up with the window). Returns how much to scale sizes by.
    private val skyTmpP = Vec3(); private val skyTmpD = Vec3(); private val skyGlowP = Vec3()
    lateinit var defaultCamPos: Vec3
    private fun backdropToSky(px: Float, py: Float, rr: Float, out: Vec3): Float {
        skyTmpP.set(cityBackdrop.position.x - 12 + (px / SKY_W) * 24, cityBackdrop.position.y + 6 - (py / SKY_H) * 12, cityBackdrop.position.z)
        val eye = defaultCamPos
        val near = skyTmpP.distanceTo(eye)
        skyTmpD.copy(skyTmpP).sub(eye).normalize()
        val ox = eye.x - SKY_C.x; val oy = eye.y - SKY_C.y; val oz = eye.z - SKY_C.z
        val b = ox * skyTmpD.x + oy * skyTmpD.y + oz * skyTmpD.z; val c = ox * ox + oy * oy + oz * oz - rr * rr
        val t = -b + sqrt(max(0f, b * b - c))
        out.copy(eye).addScaledVector(skyTmpD, t)
        return t / near
    }

    // ---- The dome, sun, moon and horizon ring follow the time, weather and place (called when the backdrop rebuilds) ----
    private fun paintSideSky() {
        skyColorsAt(env.sunDeg)
        dome.top.setRGB((skyCols[0] / 255).toFloat(), (skyCols[1] / 255).toFloat(), (skyCols[2] / 255).toFloat())
        dome.mid.setRGB((skyCols[3] / 255).toFloat(), (skyCols[4] / 255).toFloat(), (skyCols[5] / 255).toFloat())
        dome.hor.setRGB((skyCols[6] / 255).toFloat(), (skyCols[7] / 255).toFloat(), (skyCols[8] / 255).toFloat())
        val W = env.W
        val night = 1 - env.daylight
        val clearSky = max(0.0, 1 - W.cloud * 1.15)
        dome.starAmt = (if (night > 0.35) night * clearSky * (if (env.skyStyle == "meteor") 1.0 else 0.75) else 0.0).toFloat()
        dome.milkyAmt = (if (night > 0.85 && clearSky > 0.5) 0.09 * clearSky * (if (env.skyStyle == "meteor") 1.8 else 1.0) else 0.0).toFloat()
        // Sunrise / sunset glow pooled on the sun's side of the horizon
        dome.glowAmt = (if (env.twilight > 0.02 && env.skyStyle != "vapor") 0.55 * env.twilight * (1 - W.cloud * 0.6) else 0.0).toFloat()
        val gx = if (sunX > -90) sunX else (if (env.morning) 40f else SKY_W - 40f)
        backdropToSky(gx, SKY_H * 0.72f, SKY_R, skyGlowP)
        run { val x = skyGlowP.x - SKY_C.x; val z = skyGlowP.z - SKY_C.z; val l = sqrt(x * x + z * z); dome.glowX = x / l; dome.glowY = z / l }
        clouds.cloudAmt = if (W.cloud > 0.04f) 1f else 0f
        clouds.cloudTex.needsUpdate()
        // Sun
        sunMesh.visible = env.sunDeg > -2 && sunY > -30
        if (sunMesh.visible) {
            val ctx = sunCard.ctx; val k = 64.0 / 70
            val low = 1 - Environment.smooth(0.0, 20.0, env.sunDeg)
            val vis = max(0.15, 1 - W.cloud * 0.9)
            ctx.clearRect(0, 0, 128, 128)
            val halo = ctx.createRadialGradient(64, 64, 4 * k, 64, 64, 64)
            halo.addColorStop(0, "rgba(255, ${(210 - low * 90).toInt()}, ${(150 - low * 100).toInt()}, ${0.7 * vis})")
            halo.addColorStop(1, "rgba(255, 180, 90, 0)")
            ctx.fillStyle = halo; ctx.fillRect(0, 0, 128, 128)
            ctx.globalAlpha = vis.toFloat()
            if (env.skyStyle == "vapor") {
                val sg = ctx.createLinearGradient(0, 64 - 22 * k, 0, 64 + 22 * k)
                sg.addColorStop(0, "#ffe66d"); sg.addColorStop(1, "#ff4fa3")
                ctx.fillStyle = sg; ctx.beginPath(); ctx.arc(64, 64, 22 * k, 0, PI * 2); ctx.fill()
                ctx.globalCompositeOperation = "destination-out"
                for (i in 0 until 6) ctx.fillRect(64 - 24 * k, 64 + (2 + i * 4) * k, 48 * k, (1 + i * 0.5) * k)
                ctx.globalCompositeOperation = "source-over"
            } else {
                ctx.fillStyle = "rgb(255, ${(245 - low * 110).toInt()}, ${(215 - low * 160).toInt()})"
                ctx.beginPath(); ctx.ellipse(64, 64, (12 + low * 4) * k, (12 + low * 2) * k, 0, 0, PI * 2); ctx.fill()
            }
            ctx.globalAlpha = 1f
            sunCard.tex.needsUpdate()
            val s = backdropToSky(sunX, sunY, SKY_R - 0.8f, sunMesh.position)
            val sc = 140 * BACKDROP_PX * s; sunMesh.scale.set(sc, sc, sc)
            sunMesh.updateMatrixWorld(); sunMesh.lookAt(SKY_C)
        }
        // Moon (with tonight's phase)
        moonMesh.visible = moonY > -30 && env.moonEl > -0.05
        if (moonMesh.visible) {
            val ctx = moonCard.ctx; val k = 64.0 / 46
            val vis = max(0.12, 1 - W.cloud * 0.85) * (1 - env.daylight * 0.75)
            val blood = env.skyStyle == "blood"
            ctx.clearRect(0, 0, 128, 128)
            val halo = ctx.createRadialGradient(64, 64, 4 * k, 64, 64, 64)
            halo.addColorStop(0, if (blood) "rgba(255, 70, 40, ${0.55 * vis})" else "rgba(200, 220, 255, ${0.45 * vis * (0.3 + env.moonIllum * 0.7)})")
            halo.addColorStop(1, "rgba(200, 220, 255, 0)")
            ctx.fillStyle = halo; ctx.fillRect(0, 0, 128, 128)
            ctx.globalAlpha = vis.toFloat()
            drawMoonDisc(ctx, 64.0, 64.0, (if (blood) 15 else 11) * k, if (blood) 0.5 else env.moonPhase, if (blood) "#d9472b" else "#e8eefc", "rgba(40, 50, 80, 0.55)")
            ctx.globalAlpha = 1f
            moonCard.tex.needsUpdate()
            val s = backdropToSky(moonX, moonY, SKY_R - 0.9f, moonMesh.position)
            val sc = 92 * BACKDROP_PX * s; moonMesh.scale.set(sc, sc, sc)
            moonMesh.updateMatrixWorld(); moonMesh.lookAt(SKY_C)
        }
        // Horizon ring: the place's generic skyline
        val rctx = ringObj.ctx
        rctx.clearRect(0, 0, 256, 256)
        paintSideHorizon(rctx, 256, 256)
        ringObj.tex.needsUpdate()
    }

    // Side walls of the sky box: a plain continuation of each place's horizon (same heights as the back wall)
    private fun paintSideHorizon(ctx: Canvas2D, w: Int, h: Int) {
        val e = outsideEnv
        val r = makeRng(e.length * 31 + 7)
        fun cityRow(base: Int, hMin: Int, hMax: Int, col: String, lit: Boolean) {
            var x = -4.0
            while (x < w) {
                val bw = 10 + r() * 20; val top = base - hMin - r() * (hMax - hMin)
                ctx.fillStyle = col; ctx.fillRect(x, top, bw, h - top)
                if (lit) {
                    var wy = top + 4
                    while (wy < h - 4) { var wx = x + 2; while (wx < x + bw - 2) { litWindow(ctx, wx, wy, 2, 2, r()); wx += 5 }; wy += 6 }
                }
                x += bw + 1
            }
        }
        if (e == "city" || e == "neon" || e == "neighborhood") {
            cityRow(214, 20, 70, T(intArrayOf(10, 15, 32), intArrayOf(70, 84, 108), 0.35), false)
            cityRow(232, 10, 50, T(intArrayOf(6, 9, 22), intArrayOf(38, 48, 66), 0.1), true)
        } else placesPaint?.paintSideHorizonPlace(ctx, w, h, r, ::cityRow)
    }

    // ---- The cached backdrop: clouds strip, skyline, lit windows (rebuilt when the time / weather moves on) ----
    private fun rebuildCityStaticCache() {
        val ctx = cityStaticCache
        val w = SKY_W; val h = SKY_H
        val W = env.W
        ctx.clearRect(0, 0, w, h)
        // The sky itself (colours, glow, stars, sun and moon) lives on the dome behind; this canvas keeps it clear
        val night = 1 - env.daylight
        // Tinted cloud strip for the moving layer
        val cctx = cloudLayer
        cctx.clearRect(0, 0, cloudLayer.width, cloudLayer.height)
        if (W.cloud > 0.04f) {
            val cr = makeRng(41)
            val count = Math.round(6 + W.cloud * 34)
            for (i in 0 until count) {
                val spr = cloudSprites[i % 4]
                val s = 0.7 + cr() * 1.6
                cctx.globalAlpha = (min(1.0, 0.35 + W.cloud * 0.8) * (0.6 + cr() * 0.4)).toFloat()
                val cx0 = cr() * cloudLayer.width - 64; val cy0 = cr() * 110 - 10
                for (wrap in intArrayOf(-cloudLayer.width, 0, cloudLayer.width)) cctx.drawImage(spr, cx0 + wrap, cy0, 128 * s, 40 * s) // wraps seamlessly round the dome
            }
            cctx.globalAlpha = 1f
            // Tint: bright white by day, orange-pink at sunset, dull blue-grey lit by the city at night
            val sunsetTint = env.twilight * (1 - W.cloud * 0.5)
            val r = (220 * env.daylight + 70 * night) * (1 - sunsetTint) + 255 * sunsetTint
            val g = (225 * env.daylight + 62 * night) * (1 - sunsetTint) + 150 * sunsetTint
            val b = (232 * env.daylight + 78 * night) * (1 - sunsetTint) + 120 * sunsetTint
            val darken = if (W.base == "storm") 0.55 else (if (W.base == "precip") 0.75 else 1.0)
            cctx.globalCompositeOperation = "source-atop"
            cctx.fillStyle = "rgb(${(r * darken).toInt()},${(g * darken).toInt()},${(b * darken).toInt()})"
            cctx.fillRect(0, 0, cloudLayer.width, cloudLayer.height)
            cctx.globalCompositeOperation = "source-over"
        }
        paintSideSky()
        if (outsideEnv != "city") { placesPaint?.paintLandscape(ctx, w, h); cityStaticDirty = false; return }
        // Far skyline: hazy, lighter (atmospheric perspective)
        fun hazeMix(c1: IntArray, c2: IntArray, k: Double) = IntArray(3) { i -> (c1[i] + (c2[i] - c1[i]) * k).toInt() }
        val nightBldg = intArrayOf(10, 15, 32); val dayBldg = intArrayOf(70, 84, 108)
        val farCol = hazeMix(hazeMix(nightBldg, dayBldg, env.daylight), intArrayOf(140, 150, 170), 0.25 + W.fog * 0.3)
        ctx.fillStyle = "rgb(${farCol[0]},${farCol[1]},${farCol[2]})"
        for (b in skylineFar) { ctx.fillRect(b[0], b[1], b[2], h - b[1]); roofShape(ctx, b[0], b[1], b[2], b[3]) }
        if (night > 0.4) {
            ctx.fillStyle = "rgba(255, 214, 140, ${0.35 * night})"
            for (i in skylineFar.indices) {
                val (x, y, bw) = skylineFar[i]
                for (k in 0 until 6) ctx.fillRect(x + 2 + ((i * 7 + k * 11) % max(1, bw - 4)), y + 4 + ((i * 13 + k * 17) % 60), 1, 1)
            }
        }
        // Near skyline with real lit windows
        val nearCol = hazeMix(hazeMix(intArrayOf(6, 9, 22), intArrayOf(38, 48, 66), env.daylight), intArrayOf(120, 130, 150), W.fog * 0.25)
        ctx.fillStyle = "rgb(${nearCol[0]},${nearCol[1]},${nearCol[2]})"
        for (b in skylineNear) { ctx.fillRect(b[0], b[1], b[2], h - b[1]); roofShape(ctx, b[0], b[1], b[2], b[3]) }
        if (env.currentSeason == "winter") {
            ctx.fillStyle = "rgba(235, 242, 252, 0.85)"
            for (b in skylineNear) ctx.fillRect(b[0], b[1], b[2], 2)
        }
        // How busy the city is: lots of lights in the evening, few at 4 AM, reflective glass by day
        val hr = env.hour
        val activity = if (night < 0.3) 0.1 else (if (hr >= 1 && hr < 5) 0.18 else (if (hr >= 5 && hr < 7) 0.3 else (if (hr >= 22 || hr < 1) 0.45 else 0.62)))
        for (wl in windowLights) {
            if (env.daylight > 0.7) {
                if (wl.tone > 0.7) { ctx.fillStyle = "rgba(160, 200, 240, 0.35)"; ctx.fillRect(wl.x, wl.y, 3, 2) }
                continue
            }
            if (wl.owl > activity) continue
            ctx.fillStyle = wl.col
            ctx.globalAlpha = (0.65 + wl.tone * 0.35).toFloat()
            ctx.fillRect(wl.x, wl.y, 3, 2)
        }
        ctx.globalAlpha = 1f
        val ground = hazeMix(intArrayOf(4, 6, 14), intArrayOf(30, 36, 46), env.daylight)
        ctx.fillStyle = "rgb(${ground[0]},${ground[1]},${ground[2]})"
        ctx.fillRect(0, h - 10, w, 10)
        cityStaticDirty = false
    }

    // ---- Moving things in the sky, redrawn a few times a second over the cached backdrop ----
    private class Shooting(var x: Double, var y: Double, var life: Double, val dx: Double, val dy: Double)
    private class Plane(var x: Double, var y: Double, val v: Double, var next: Long)
    private class Birds(var x: Double, val y: Double, val n: Int)
    private var shooting: Shooting? = null
    private val plane = Plane(-40.0, 60.0, 9.0, 0L)
    private var birds: Birds? = null
    private var bolt: ArrayList<DoubleArray>? = null
    private val rnd = Random.Default
    private val rainbowCols = arrayOf("255,60,60", "255,160,40", "255,240,80", "80,220,90", "70,140,255", "150,80,220")

    fun updateCitySkyline(t: Float) {
        val now = SystemClock.uptimeMillis()
        val rebuildEvery = if (env.timeMode == "cycle") 900 else 15000
        if (cityStaticDirty || now - lastSkyRebuild > rebuildEvery) {
            if (!cityStaticDirty) { env.updateAstronomy(); env.updateCelestialPosition() }
            rebuildCityStaticCache()
            lastSkyRebuild = now
        }
        val W = env.W
        val ctx = cityCanvasObj.ctx
        val w = SKY_W; val h = SKY_H
        ctx.clearRect(0, 0, w, h)
        ctx.drawImage(cityStaticCache, 0, 0)
        if (outsideEnv != "city") placesPaint?.paintLandscapeLive(ctx, t)
        dome.time = t
        val night = 1 - env.daylight
        val clearSky = max(0.0, 1 - W.cloud * 1.15)
        // Aurora ribbons
        if (env.skyStyle == "aurora" && night > 0.5) {
            for (r in 0 until 3) {
                ctx.beginPath()
                var x = 0
                while (x <= w) {
                    val y = 40 + r * 18 + sin(x * 0.012 + t * 0.35 + r) * 16 + sin(x * 0.031 - t * 0.2) * 6
                    if (x == 0) ctx.moveTo(x, y) else ctx.lineTo(x, y)
                    x += 8
                }
                ctx.lineTo(w, 150); ctx.lineTo(0, 150); ctx.closePath()
                val ag = ctx.createLinearGradient(0, 30 + r * 18, 0, 150)
                ag.addColorStop(0, "rgba(${if (r == 2) 190 else 80}, ${if (r == 2) 90 else 255}, ${if (r == 2) 255 else 170}, ${0.22 * night * clearSky})")
                ag.addColorStop(1, "rgba(60, 255, 160, 0)")
                ctx.fillStyle = ag
                ctx.fill()
            }
        }
        // Clouds drift round the dome with the wind
        clouds.cloudOff = ((t * (3 + W.wind * 14) / cloudLayer.width) % 1)
        // Shooting stars (lots in the meteor-shower sky)
        if (night > 0.6 && clearSky > 0.4) {
            val rate = if (env.skyStyle == "meteor") 0.12 else 0.008
            if (shooting == null && rnd.nextDouble() < rate) shooting = Shooting(rnd.nextDouble() * w, rnd.nextDouble() * 60, 1.0, -(60 + rnd.nextDouble() * 60), 25 + rnd.nextDouble() * 25)
            shooting?.let { s ->
                val k = 1 - s.life
                val hx = s.x + s.dx * k; val hy = s.y + s.dy * k
                val grad = ctx.createLinearGradient(hx, hy, hx - s.dx * 0.25, hy - s.dy * 0.25)
                grad.addColorStop(0, "rgba(255, 255, 255, ${s.life})"); grad.addColorStop(1, "rgba(255, 255, 255, 0)")
                ctx.strokeStyle = grad; ctx.lineWidth = 1.5f
                ctx.beginPath(); ctx.moveTo(hx, hy); ctx.lineTo(hx - s.dx * 0.25, hy - s.dy * 0.25); ctx.stroke()
                s.life -= 0.12
                if (s.life <= 0) shooting = null
            }
        }
        // Airliner crossing: blinking nav lights at night, a tiny silhouette by day
        val p = plane
        if (now > p.next) {
            p.x += p.v * 0.22
            if (p.x > w + 30) { p.x = -30.0; p.y = 30 + rnd.nextDouble() * 70; p.next = now + 20000 + (rnd.nextDouble() * 40000).toLong() }
            if (night > 0.4) {
                if (sin(t * 6) > 0.6) { ctx.fillStyle = "#ff3b3b"; ctx.fillRect(p.x.toInt(), p.y.toInt(), 2, 2) }
                if (sin(t * 6 + 2) > 0.85) { ctx.fillStyle = "#ffffff"; ctx.fillRect((p.x + 4).toInt(), p.y.toInt(), 1, 1) }
            } else {
                ctx.fillStyle = "rgba(60, 70, 90, 0.8)"
                ctx.fillRect(p.x.toInt(), p.y.toInt(), 6, 1); ctx.fillRect((p.x + 2).toInt(), (p.y - 1).toInt(), 2, 3)
            }
        }
        // Birds at dawn and during the day
        if (env.daylight > 0.3 && !env.isPrecipNow() && (env.currentTime == "dawn" || env.currentTime == "day")) {
            if (birds == null && rnd.nextDouble() < 0.01) birds = Birds(-20.0, 60 + rnd.nextDouble() * 50, 3 + floor(rnd.nextDouble() * 5).toInt())
            birds?.let { b ->
                b.x += 3.2
                ctx.strokeStyle = "rgba(25, 30, 45, 0.85)"; ctx.lineWidth = 1f
                for (i in 0 until b.n) {
                    val bx = b.x - abs(i - b.n / 2.0) * 7; val by = b.y + i * 3
                    val flap = sin(t * 9.0 + i) * 2
                    ctx.beginPath(); ctx.moveTo(bx - 3, by - flap); ctx.lineTo(bx, by); ctx.lineTo(bx + 3, by - flap); ctx.stroke()
                }
                if (b.x > w + 40) birds = null
            }
        }
        // Rainbow during a sun shower
        if (W.rainbow && env.daylight > 0.4) {
            for (i in rainbowCols.indices) {
                ctx.strokeStyle = "rgba(${rainbowCols[i]}, 0.16)"; ctx.lineWidth = 3f
                ctx.beginPath(); ctx.arc(w * 0.35, h * 1.05, 150 - i * 3, PI * 1.08, PI * 1.75); ctx.stroke()
            }
        }
        // Lightning: whole-sky flash plus a forked bolt
        if (lightningFlash > 0.15f) {
            ctx.globalCompositeOperation = "source-atop" // the flash lights the skyline here and the whole sky on the dome
            ctx.fillStyle = "rgba(203, 213, 225, ${0.5 * lightningFlash})"
            ctx.fillRect(0, 0, w, h)
            ctx.globalCompositeOperation = "source-over"
            if (bolt == null) {
                val pts = arrayListOf(doubleArrayOf(60 + rnd.nextDouble() * (w - 120), 0.0))
                while (pts[pts.size - 1][1] < 170) { val l = pts[pts.size - 1]; pts += doubleArrayOf(l[0] + (rnd.nextDouble() - 0.5) * 26, l[1] + 10 + rnd.nextDouble() * 14) }
                bolt = pts
            }
            ctx.strokeStyle = "rgba(240, 245, 255, $lightningFlash)"; ctx.lineWidth = 2f
            ctx.beginPath(); bolt!!.forEachIndexed { i, pt -> if (i > 0) ctx.lineTo(pt[0], pt[1]) else ctx.moveTo(pt[0], pt[1]) }; ctx.stroke()
        } else bolt = null
        dome.flash = if (lightningFlash > 0.15f) 0.5f * lightningFlash else 0f
        // Beacons on the tallest roofs
        if (sin(t * 4.5) > 0) {
            ctx.fillStyle = "#ff2222"
            for (b in skylineNear) if (b[3] == 2) ctx.fillRect(b[0] + b[2] / 2.0 - 1, b[1] - 17, 2, 2)
        }
        // Precipitation streaks / snow in the distance
        if (env.isSnowingNow()) {
            ctx.fillStyle = "rgba(248, 250, 252, 0.9)"
            val n = Math.round(30 + W.rain * 60)
            for (i in 0 until n) {
                val sy = (i * 37 + t * (40 + (i % 5) * 10)) % h
                val sx = (i * 89 + sin(t * 1.5 + i) * 16 - t * W.wind * 40 + w * 40) % w
                ctx.fillRect(sx.toInt(), sy.toInt(), 2, 2)
            }
        } else if (env.isRainingNow()) {
            ctx.fillStyle = "rgba(191, 219, 254, 0.55)"
            val n = Math.round(25 + W.rain * 70)
            for (i in 0 until n) {
                val ry = (i * 41 + t * (330 + (i % 4) * 60)) % h
                val rx = (i * 83 - t * (40 + W.wind * 90) + w * 40) % w
                ctx.fillRect(rx.toInt(), ry.toInt(), 1, 12)
            }
        }
        // Haze: over the skyline here, and the same colour over the dome
        val hazeA = if (W.fog > 0.05f || W.base == "storm") min(0.6, W.fog * 0.45 + (if (W.base == "storm") 0.15 else 0.0)) else 0.0
        dome.fogAmt = hazeA.toFloat()
        if (hazeA > 0) {
            dome.fogCol.setRGB(((120 + env.daylight * 60) / 255).toFloat(), ((130 + env.daylight * 60) / 255).toFloat(), ((150 + env.daylight * 55) / 255).toFloat())
            ctx.globalCompositeOperation = "source-atop"
            ctx.fillStyle = "rgba(${(120 + env.daylight * 60).toInt()}, ${(130 + env.daylight * 60).toInt()}, ${(150 + env.daylight * 55).toInt()}, $hazeA)"
            ctx.fillRect(0, 0, w, h)
            ctx.globalCompositeOperation = "source-over"
        }
        // Fade the backdrop's edges so it melts into the horizon ring and the dome
        ctx.globalCompositeOperation = "destination-in"
        ctx.drawImage(backdropMask, 0, 0)
        ctx.globalCompositeOperation = "source-over"
        cityCanvasObj.tex.needsUpdate()
    }
}
