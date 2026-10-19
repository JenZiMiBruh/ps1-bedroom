package com.ps1bedroom.room

import com.ps1bedroom.room.engine.CanvasTex
import com.ps1bedroom.room.engine.Color
import com.ps1bedroom.room.engine.DOUBLE_SIDE
import com.ps1bedroom.room.engine.Geo
import com.ps1bedroom.room.engine.GeoCurves
import com.ps1bedroom.room.engine.Geometry
import com.ps1bedroom.room.engine.Group
import com.ps1bedroom.room.engine.Material
import com.ps1bedroom.room.engine.Mesh
import com.ps1bedroom.room.engine.Object3D
import com.ps1bedroom.room.engine.PerspectiveCamera
import com.ps1bedroom.room.engine.Quat
import com.ps1bedroom.room.engine.RenderTarget
import com.ps1bedroom.room.engine.Renderer
import com.ps1bedroom.room.engine.Scene
import com.ps1bedroom.room.engine.SpotLight
import com.ps1bedroom.room.engine.AmbientLight
import com.ps1bedroom.room.engine.Texture
import com.ps1bedroom.room.engine.Vec3
import com.ps1bedroom.room.engine.createCanvasTex
import com.ps1bedroom.room.engine.makeRng
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan
import kotlin.random.Random

/**
 * The 3D-era GTA games on the TV (HTML lines 6900-7260): a small chase-cam drive (III, Vice City, San Andreas, the
 * Stories, IV, V, VI) rendered into a texture that the TV shows. Themes set the city, sky, light and car for each game.
 * Native notes: three's DirectionalLight is a far-away wide spot light (same lighting), the background texture and the
 * HUD ride on quads locked to the camera, and the rain lines are one mesh of thin streaks.
 */
class Gta3d(private val renderer: Renderer, private val env: Environment) {
    private class Sun(val y: Float, val col: String)
    private class Theme(val sky: Array<String>, val fog: Int, val fogD: Float, val bld: Array<String>, val lit: Float, val road: String, val car: Int,
                        val palms: Int, val lamps: Int, val hud: String, val glow: Float? = null, val neon: Boolean = false, val rain: Boolean = false,
                        val sun: Sun? = null, val ocean: Boolean = false, val res: IntArray? = null)
    private val THEMES = mapOf(
        "iii" to Theme(arrayOf("#0d1220", "#2c3446", "#4a5468"), 0x2b3242, 0.045f, arrayOf("#3b3f47", "#4a4f58", "#2f333a", "#55534e"), 0.75f, "#26272a", 0xc8b36a, 0, 0xffb347, "classic", rain = true),
        "vc" to Theme(arrayOf("#14062e", "#5b1a6b", "#ff5fa2"), 0x3a1450, 0.03f, arrayOf("#f9a8d4", "#99f6e4", "#f5f5f4", "#fde68a"), 0.6f, "#2b2533", 0xf8fafc, 1, 0xff5fa2, "classic", glow = 0.38f, neon = true),
        "sa" to Theme(arrayOf("#2a1f5e", "#c8507a", "#ffb067"), 0x8a5a4a, 0.032f, arrayOf("#c2a27e", "#a47d5b", "#8c6b52", "#d6b48a"), 0.2f, "#3a3530", 0x1f7a3a, 1, 0xffc070, "classic", sun = Sun(0.18f, "#ffcf7a")),
        "lcs" to Theme(arrayOf("#5f7186", "#8a9bb0", "#b8c4d2"), 0x7e8a99, 0.035f, arrayOf("#6b7280", "#4b5563", "#78716c", "#57534e"), 0.1f, "#3f4146", 0x2563eb, 0, 0xffe0a0, "classic"),
        "vcs" to Theme(arrayOf("#ff8a5c", "#ffb48a", "#ffe0b0"), 0xd88a7a, 0.028f, arrayOf("#fbcfe8", "#a5f3fc", "#fef3c7", "#fecaca"), 0.1f, "#4a4248", 0x0ea5e9, 1, 0xffe0a0, "classic", sun = Sun(0.3f, "#fff1c2")),
        "iv" to Theme(arrayOf("#56605c", "#7d857c", "#a9ab9a"), 0x6f766c, 0.042f, arrayOf("#5c5a52", "#4a4842", "#6b665c", "#3f3d38"), 0.15f, "#34342f", 0x4b5563, 0, 0xfff0c0, "modern"),
        "v" to Theme(arrayOf("#3b82f6", "#7cb8ff", "#dff1ff"), 0xb5d4ef, 0.022f, arrayOf("#c9d6e3", "#8fb3d9", "#e7e1d6", "#9fb8a8"), 0.05f, "#45464a", 0xff7a00, 1, 0xffffff, "modern", sun = Sun(0.55f, "#fff7d6")),
        "vi" to Theme(arrayOf("#1c0838", "#a02a86", "#ff9a5c"), 0x5a2a6a, 0.024f, arrayOf("#ffd1e8", "#bdf5ff", "#fff3d6", "#e0c8ff"), 0.55f, "#2a2236", 0xe11d48, 2, 0xff7ad9, "vi", glow = 0.22f, neon = true, sun = Sun(0.24f, "#ffd27a"), ocean = true, res = intArrayOf(400, 300))
    )

    private var built = false
    private var themeKey: String? = null
    private lateinit var th: Theme
    val rt = RenderTarget(320, 240)
    private val scene3 = Scene()
    private val cam3 = PerspectiveCamera(62f, 4f / 3f, 0.1f, 160f)
    private val camRig = Group()
    private lateinit var skyObj: CanvasTex
    private lateinit var hudObj: CanvasTex
    private lateinit var roadTex: Texture
    private lateinit var roadMat: Material
    private lateinit var walkR: Material
    private lateinit var sunL: SpotLight
    private val windowTexes = ArrayList<CanvasTex>()
    private val emissiveMats = ArrayList<Material>()
    private val segLen = 90f
    private class Mover(val obj: Object3D, val kind: String?, val head: Material? = null)
    private val movers = ArrayList<Mover>()
    private class Car(val g: Group, val v: Float, val body: Material, val cabin: Mesh, val riders: List<Mesh>, val wheels: List<Mesh>)
    private val traffic = ArrayList<Car>()
    private lateinit var player: Car
    private lateinit var rainLines: Mesh
    private lateinit var ocean: Mesh
    private lateinit var oceanTex: Texture
    private lateinit var sunSprite: Mesh
    private lateinit var sunMat: Material
    private var lastRender = 0L
    private val rnd = Random.Default

    private fun mat(c: Int, side: Int = com.ps1bedroom.room.engine.FRONT_SIDE) = Material(color = Color(c), ps1 = false, side = side)   // MeshLambertMaterial
    private fun basic(c: Int) = Material(color = Color(c), lit = false, ps1 = false)                                                      // MeshBasicMaterial
    private fun makeWindowTex(base: String, litChance: Double, seed: Int) = createCanvasTex(32, 64) { ctx, w, h ->
        ctx.fillStyle = base; ctx.fillRect(0, 0, w, h)
        val r = makeRng(seed)
        var y = 3; while (y < h - 2) { var x = 2; while (x < w - 2) { val v = r(); ctx.fillStyle = if (v < litChance) (if (v < litChance * 0.3) "#9fd8ff" else "#ffd88a") else "rgba(10, 14, 24, 0.75)"; ctx.fillRect(x, y, 2, 2); x += 4 }; y += 4 }
    }.also { it.tex.nearest = true }

    private fun build() {
        scene3.add(AmbientLight(0xffffff, 0.55f))
        // three's DirectionalLight from (-3, 8, -4): a spot light far out along that direction with a wide cone and no falloff
        sunL = SpotLight(0xffffff, 0.7f); sunL.angle = 1.55f; sunL.penumbra = 0f; sunL.distance = 0f
        sunL.position.set(-3f * 400, 8f * 400, -4f * 400)
        scene3.add(sunL)
        skyObj = createCanvasTex(4, 128) { _, _, _ -> }.also { it.tex.nearest = false }
        scene3.fogColor.setHex(0x000000); scene3.fogDensity = 0.03f
        // Background (the sky gradient) and the HUD ride on quads locked to the camera
        scene3.add(camRig)
        val farH = tan(31.0 * PI / 180).toFloat() * 150f * 2 * 1.2f
        val sky = Mesh(Geo.plane(farH * 4 / 3, farH), Material(map = skyObj.tex, lit = false, ps1 = false, fog = false, depthWrite = false))
        sky.position.z = -150f; sky.renderOrder = -10; sky.frustumCulled = false; camRig.add(sky)
        hudObj = createCanvasTex(320, 240) { _, _, _ -> }.also { it.tex.nearest = false }
        val nearH = tan(31.0 * PI / 180).toFloat() * 0.15f * 2
        val hud = Mesh(Geo.plane(nearH * 4 / 3, nearH), Material(map = hudObj.tex, lit = false, ps1 = false, fog = false, transparent = true, depthTest = false, depthWrite = false))
        hud.position.z = -0.15f; hud.renderOrder = 100; hud.frustumCulled = false; camRig.add(hud)
        // Road with scrolling lane markings, sidewalks either side
        roadTex = createCanvasTex(64, 64, 1f, 12f) { ctx, w, h ->
            ctx.fillStyle = "#ffffff"; ctx.fillRect(0, 0, w, h)
            ctx.fillStyle = "rgba(0,0,0,0.08)"; for (i in 0 until 300) ctx.fillRect(rnd.nextDouble() * w, rnd.nextDouble() * h, 1, 1)
            ctx.fillStyle = "#ffe680"; ctx.fillRect(w / 2 - 1, 0, 2, h / 2)
            ctx.fillStyle = "#f8fafc"; ctx.fillRect(w * 0.18, 0, 1, h); ctx.fillRect(w * 0.82 - 1, 0, 1, h)
        }.tex.also { it.nearest = false }
        roadMat = Material(map = roadTex, ps1 = false)
        val road = Mesh(Geo.plane(10f, segLen * 2), roadMat)
        road.rotation.x = (-PI / 2).toFloat(); road.position.z = -segLen * 0.8f; scene3.add(road)
        for (x in floatArrayOf(-7.5f, 7.5f)) {
            val m = mat(0x8b8f96)
            val walk = Mesh(Geo.box(5f, 0.2f, segLen * 2), m)
            walk.position.set(x, 0.1f, -segLen * 0.8f); scene3.add(walk)
            if (x > 0) walkR = m
        }
        // Buildings (two rows each side), recoloured per theme
        for (i in 0 until 4) windowTexes += makeWindowTex("#ffffff", 0.4, 11 + i)
        for (i in 0 until 28) {
            val side = if (i % 2 == 1) 1 else -1
            val hgt = 4 + rnd.nextFloat() * 14
            val m = Material(map = windowTexes[i % 4].tex, emissive = Color(0xffffff), emissiveMap = windowTexes[i % 4].tex, emissiveIntensity = 0.3f, ps1 = false)
            emissiveMats += m
            val b = Mesh(Geo.box(4 + rnd.nextFloat() * 2, hgt, 5 + rnd.nextFloat() * 3), m)
            b.position.set(side * (12 + rnd.nextFloat() * 3), hgt / 2, -(i / 2f) * 6.5f)
            scene3.add(b); movers += Mover(b, null)
        }
        // Streetlights and palms
        for (i in 0 until 12) {
            val side = if (i % 2 == 1) 1f else -1f
            val pole = Group()
            val stick = Mesh(Geo.cylinder(0.07f, 0.09f, 4.2f, 5), mat(0x3a3f47)); stick.position.y = 2.1f
            val arm = Mesh(Geo.box(1.2f, 0.08f, 0.1f), mat(0x3a3f47)); arm.position.set(-side * 0.55f, 4.15f, 0f)
            val headMat = basic(0xffd27a)
            val head = Mesh(Geo.box(0.45f, 0.12f, 0.25f), headMat); head.position.set(-side * 1.05f, 4.05f, 0f)
            pole.add(stick, arm, head)
            pole.position.set(side * 5.6f, 0f, -i * 7.5f)
            scene3.add(pole); movers += Mover(pole, "lamp", headMat)
        }
        for (i in 0 until 14) {
            val side = if (i % 2 == 1) 1f else -1f
            val p = Group()
            val trunkMat = mat(0x6b4a2b)
            for (s in 0 until 4) {
                val seg = Mesh(Geo.cylinder(0.16f - s * 0.02f, 0.18f - s * 0.02f, 1.6f, 5), trunkMat)
                seg.position.set(s * 0.12f, 0.8f + s * 1.55f, 0f); seg.rotation.z = -0.05f * s
                p.add(seg)
            }
            val frondMat = mat(0x2f7d3a, DOUBLE_SIDE)
            for (f in 0 until 7) {
                val frond = Mesh(Geo.plane(2.6f, 0.45f), frondMat)
                frond.position.set(0.45f + cos(f * 0.9f) * 1.1f, 6.3f, sin(f * 0.9f) * 1.1f)
                frond.rotation.set(0f, -f * 0.9f, -0.5f)
                p.add(frond)
            }
            p.position.set(side * 6.8f, 0f, -i * 6.4f - 3)
            scene3.add(p); movers += Mover(p, "palm")
        }
        // Neon signs on some buildings (Vice City / VI)
        for (i in 0 until 6) {
            val sign = Mesh(Geo.plane(2.4f, 0.6f), basic(intArrayOf(0xff4fa3, 0x4ff3ff, 0xffd23f)[i % 3]))
            sign.position.set((if (i % 2 == 1) 1 else -1) * 9.4f, 3f + (i % 3), -i * 14f - 6)
            sign.rotation.y = if (i % 2 == 1) (-PI / 2).toFloat() else (PI / 2).toFloat()
            scene3.add(sign); movers += Mover(sign, "neon")
        }
        // Ocean along the right for VI: shimmering sunset water
        oceanTex = createCanvasTex(64, 64, 4f, 20f) { ctx, w, h ->
            ctx.fillStyle = "#ffffff"; ctx.fillRect(0, 0, w, h)
            for (i in 0 until 160) { ctx.fillStyle = "rgba(255, ${(200 + rnd.nextDouble() * 55).toInt()}, 220, ${0.3 + rnd.nextDouble() * 0.5})"; ctx.fillRect(rnd.nextDouble() * w, rnd.nextDouble() * h, 3 + rnd.nextDouble() * 6, 1) }
        }.tex
        ocean = Mesh(Geo.plane(80f, segLen * 2), Material(map = oceanTex, color = Color(0x6a3a9a), lit = false, ps1 = false))
        ocean.rotation.x = (-PI / 2).toFloat(); ocean.position.set(50f, -0.15f, -segLen * 0.8f); scene3.add(ocean)
        val sunCanvas = createCanvasTex(64, 64) { ctx, w, h ->
            val g = ctx.createRadialGradient(32, 32, 4, 32, 32, 32)
            g.addColorStop(0, "rgba(255,255,230,1)"); g.addColorStop(0.35, "rgba(255,220,140,0.9)"); g.addColorStop(1, "rgba(255,160,90,0)")
            ctx.fillStyle = g; ctx.fillRect(0, 0, w, h)
        }.also { it.tex.nearest = false }
        sunMat = Material(map = sunCanvas.tex, lit = false, ps1 = false, fog = false, depthWrite = false, transparent = true)
        sunSprite = Mesh(Geo.plane(26f, 26f), sunMat); scene3.add(sunSprite)
        // Traffic
        val trafficCols = intArrayOf(0x94a3b8, 0x16a34a, 0xf8fafc, 0x7c3aed, 0xeab308, 0x0ea5e9)
        for (i in 0 until 6) {
            val v = if (i % 2 == 1) 8 + rnd.nextFloat() * 3 else -14f
            val c = makeCar(trafficCols[i], false, v)
            c.g.position.set(if (i % 2 == 1) 1.9f else -1.9f, 0f, -12f - i * 14)
            if (v < 0) c.g.rotation.y = PI.toFloat()
            scene3.add(c.g); traffic += c
        }
        player = makeCar(0xff0000, true, 0f); scene3.add(player.g)
        // Rain streaks (III): one mesh of 300 thin streaks
        val parts = ArrayList<Geometry>()
        for (i in 0 until 300) {
            val x = (rnd.nextFloat() - 0.5f) * 24; val y = rnd.nextFloat() * 12; val z = -rnd.nextFloat() * 30
            val q = Geo.plane(0.025f, 0.7f); val p = q.positions
            for (k in 0 until q.vertexCount) { val top = p[k * 3 + 1] > 0; p[k * 3] += x - (if (top) 0f else 0.05f); p[k * 3 + 1] = if (top) y else y - 0.7f; p[k * 3 + 2] = z }
            parts += q
        }
        rainLines = Mesh(GeoCurves.merge(parts), Material(color = Color(0xb8c6e0), lit = false, ps1 = false, transparent = true, opacity = 0.45f, side = DOUBLE_SIDE))
        rainLines.frustumCulled = false
        scene3.add(rainLines)
        built = true
    }
    private fun makeCar(col: Int, isPlayer: Boolean, v: Float): Car {
        val g = Group()
        val bodyMat = mat(col)
        val body = Mesh(Geo.box(1.8f, 0.55f, 4.0f), bodyMat); body.position.y = 0.55f
        val cabin = Mesh(Geo.box(1.5f, 0.5f, 1.9f), mat(0x1b2230)); cabin.position.set(0f, 1.05f, 0.2f)
        g.add(body, cabin)
        val wheelMat = mat(0x0b0b0f)
        val wheels = ArrayList<Mesh>()
        for ((x, z) in arrayOf(-0.9f to 1.3f, 0.9f to 1.3f, -0.9f to -1.3f, 0.9f to -1.3f)) {
            val wh = Mesh(Geo.cylinder(0.36f, 0.36f, 0.3f, 10), wheelMat)
            wh.rotation.z = (PI / 2).toFloat(); wh.position.set(x, 0.36f, z); g.add(wh)
            if (isPlayer) wheels += wh
        }
        val tail = Mesh(Geo.box(1.5f, 0.12f, 0.05f), basic(0xff2a2a)); tail.position.set(0f, 0.62f, 2.02f)
        val head = Mesh(Geo.box(1.4f, 0.12f, 0.05f), basic(0xfff6c8)); head.position.set(0f, 0.62f, -2.02f)
        g.add(tail, head)
        val riders = ArrayList<Mesh>()
        if (isPlayer) for (x in floatArrayOf(-0.38f, 0.38f)) {
            // Two silhouettes up front (they're needed for VI's convertible)
            val d = Mesh(Geo.box(0.42f, 0.55f, 0.42f), mat(0x14081f)); d.position.set(x, 1.15f, 0.35f); d.visible = false
            g.add(d); riders += d
        }
        return Car(g, v, bodyMat, cabin, riders, wheels)
    }

    fun setTheme(key: String) {
        if (!built) build()
        if (themeKey == key) return
        themeKey = key
        th = THEMES[key] ?: THEMES.getValue("iii")
        val res = th.res ?: intArrayOf(320, 240)
        rt.setSize(res[0], res[1])
        // Sky gradient
        val sctx = skyObj.ctx
        val sg = sctx.createLinearGradient(0, 0, 0, 128); sg.addColorStop(0, th.sky[0]); sg.addColorStop(0.55, th.sky[1]); sg.addColorStop(1, th.sky[2])
        sctx.fillStyle = sg; sctx.fillRect(0, 0, 4, 128); skyObj.tex.needsUpdate()
        scene3.fogColor.setHex(th.fog); scene3.fogDensity = th.fogD
        roadMat.color.setStyle(th.road)
        windowTexes.forEachIndexed { i, ct ->
            val ctx = ct.ctx
            val r = makeRng(40 + i + key.length * 7)
            ctx.fillStyle = th.bld[i]; ctx.fillRect(0, 0, 32, 64)
            var y = 3; while (y < 62) { var x = 2; while (x < 30) { val v = r(); ctx.fillStyle = if (v < th.lit) (if (v < th.lit * 0.3) "#9fd8ff" else "#ffd88a") else "rgba(20, 26, 40, 0.65)"; ctx.fillRect(x, y, 2, 2); x += 4 }; y += 4 }
            ct.tex.needsUpdate()
        }
        val night = th.lit > 0.4f
        val glow = th.glow ?: (if (night) 0.5f else 0.08f)
        for (m in emissiveMats) m.emissiveIntensity = glow
        sunL.intensity = if (night) 0.25f else 0.85f
        sunL.color.setStyle(th.sun?.col ?: "#c8d0e0")
        for (m in movers) {
            when (m.kind) {
                "palm" -> m.obj.visible = th.palms > 0 && (th.palms > 1 || rnd.nextDouble() > 0.35)
                "neon" -> m.obj.visible = th.neon
                "lamp" -> m.head!!.color.setHex(th.lamps)
            }
        }
        ocean.visible = th.ocean
        // VI drives up the beach: ocean and sand on the right instead of buildings
        for (m in movers) if (m.kind == null) m.obj.visible = !(th.ocean && m.obj.position.x > 0)
        walkR.color.setHex(if (th.ocean) 0xf0d49a else 0x8b8f96)
        rainLines.visible = th.rain
        sunSprite.visible = th.sun != null
        th.sun?.let { sunMat.color.setStyle(it.col) }
        player.body.color.setHex(th.car)
        val convertible = key == "vi" || key == "vc"
        player.cabin.visible = !convertible
        for (d in player.riders) d.visible = convertible
    }

    private fun drawHud(g: Float) {
        val ctx = hudObj.ctx
        val W2 = 320; val H2 = 240
        ctx.clearRect(0, 0, W2, H2)
        val moneyN = (floor(g * 731.0 + 1337).toLong() % 100000000)
        val money = moneyN.toString().padStart(8, '0')
        val stars = 1 + floor((g / 7.0) % 5).toInt()
        if (th.hud == "vi") {
            // Modern minimal HUD: rounded minimap, white money, no clutter
            ctx.fillStyle = "rgba(10, 6, 20, 0.55)"
            ctx.beginPath(); ctx.roundRect(10, H2 - 70, 92, 60, 10); ctx.fill()
            ctx.strokeStyle = "rgba(255,255,255,0.7)"; ctx.lineWidth = 1.5f; ctx.stroke()
            ctx.strokeStyle = "rgba(255, 150, 210, 0.9)"; ctx.lineWidth = 2f
            ctx.beginPath(); ctx.moveTo(56, H2 - 18); ctx.lineTo(56 + sin(g * 0.4) * 10, H2 - 40); ctx.lineTo(70, H2 - 62); ctx.stroke()
            ctx.fillStyle = "#ffffff"; ctx.beginPath(); ctx.moveTo(56, H2 - 46); ctx.lineTo(51, H2 - 36); ctx.lineTo(61, H2 - 36); ctx.fill()
            ctx.font = "bold 15px sans-serif"; ctx.textAlign = "right"; ctx.fillStyle = "#ffffff"
            ctx.fillText("\$" + String.format(java.util.Locale.US, "%,d", moneyN), W2 - 22, 30)
            ctx.font = "10px sans-serif"; ctx.fillStyle = "rgba(255,255,255,0.8)"
            ctx.fillText("LEONIDA · VICE CITY", W2 - 22, 44)
        } else {
            // Classic HUD: radar circle, green money, wanted stars
            ctx.fillStyle = "rgba(0, 0, 0, 0.45)"
            ctx.beginPath(); ctx.arc(46, H2 - 46, 32, 0, PI * 2); ctx.fill()
            ctx.strokeStyle = if (th.hud == "modern") "#e5e7eb" else "#9ca3af"; ctx.lineWidth = 2f; ctx.stroke()
            ctx.save(); ctx.beginPath(); ctx.arc(46, H2 - 46, 30, 0, PI * 2); ctx.clip()
            ctx.translate(46, H2 - 46); ctx.rotate(sin(g * 0.3) * 0.3)
            ctx.strokeStyle = "rgba(200, 200, 200, 0.55)"; ctx.lineWidth = 4f
            for (i in -2..2) {
                ctx.beginPath(); ctx.moveTo(i * 18, -40); ctx.lineTo(i * 18, 40); ctx.stroke()
                ctx.beginPath(); ctx.moveTo(-40, i * 18 + (g * 8) % 18); ctx.lineTo(40, i * 18 + (g * 8) % 18); ctx.stroke()
            }
            ctx.restore()
            ctx.fillStyle = "#ffffff"; ctx.beginPath(); ctx.moveTo(46, H2 - 52); ctx.lineTo(41, H2 - 42); ctx.lineTo(51, H2 - 42); ctx.fill()
            ctx.font = "bold 17px \"Arial Black\", Impact, sans-serif"; ctx.textAlign = "right"
            ctx.lineWidth = 3f; ctx.strokeStyle = "#000"; ctx.strokeText("\$$money", W2 - 20, 30)
            ctx.fillStyle = if (th.hud == "modern") "#9be59b" else "#5fd35f"; ctx.fillText("\$$money", W2 - 20, 30)
            ctx.font = "bold 16px sans-serif"
            for (i in 0 until 6) { ctx.fillStyle = if (i < stars) "#f5f5f4" else "rgba(255,255,255,0.15)"; ctx.fillText("★", W2 - 20 - i * 16, 50) }
            val hh = floor(env.hour).toInt() % 24; val mm = floor((env.hour % 1) * 60).toInt()
            ctx.font = "bold 13px monospace"; ctx.fillStyle = "#ffffff"
            ctx.fillText("${hh.toString().padStart(2, '0')}:${mm.toString().padStart(2, '0')}", W2 - 20, 68)
        }
        ctx.textAlign = "left"
        hudObj.tex.needsUpdate()
    }

    private val camQ = Quat()
    private val camTarget = Vec3()
    /** Renders a frame into [rt] at most ~30 times a second (plenty for a CRT). */
    fun render(g: Float, nowMs: Long) {
        if (!built) build()
        if (nowMs - lastRender < 33) return
        val step = min(0.1f, (nowMs - lastRender) / 1000f)
        lastRender = nowMs
        val speed = if (themeKey == "vi") 26f else 22f
        // Scroll the world toward the camera and wrap it around
        for (m in movers) { m.obj.position.z += speed * step; if (m.obj.position.z > 12) m.obj.position.z -= segLen }
        for (c in traffic) {
            c.g.position.z += (speed - c.v) * step
            if (c.g.position.z > 14) c.g.position.z -= segLen
            if (c.g.position.z < -segLen + 10) c.g.position.z += segLen
        }
        roadTex.offsetY = (roadTex.offsetY + speed * step / 15) % 1
        if (ocean.visible) oceanTex.offsetY = (oceanTex.offsetY + speed * step / 40) % 1
        // Player weaves between lanes; wheels spin; chase cam follows with a little lag and sway
        val lane = sin(g * 0.45f) * 1.9f + sin(g * 1.7f) * 0.25f
        player.g.position.set(lane, 0f, 0f)
        player.g.rotation.y = -cos(g * 0.45f) * 0.12f
        player.g.rotation.z = cos(g * 0.45f) * 0.03f
        for (wh in player.wheels) wh.rotation.x -= speed * step * 2.6f
        cam3.position.set(lane * 0.6f, if (themeKey == "vi") 2.5f else 2.9f, 7.2f)
        cam3.lookAtTarget(camTarget.set(lane * 0.85f, 1.0f, -6f))
        cam3.updateCameraWorld()
        camRig.position.copy(cam3.position)
        camRig.setRotationFromQuat(camQ.setFromRotationMatrix(cam3.matrixWorld))
        th.sun?.let { if (sunSprite.visible) sunSprite.position.set(-20f, 8 + it.y * 26, -110f) }
        if (rainLines.visible) { rainLines.position.y = -((g * 14) % 12); rainLines.position.z = (g * 4) % 10 }
        drawHud(g)
        cam3.aspect = rt.width.toFloat() / rt.height; cam3.updateProjectionMatrix()
        renderer.renderToTarget(scene3, cam3, rt)
    }
}
