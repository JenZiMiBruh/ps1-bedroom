package com.ps1bedroom.room

import com.ps1bedroom.room.engine.AmbientLight
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
import com.ps1bedroom.room.engine.Texture
import com.ps1bedroom.room.engine.Vec3
import com.ps1bedroom.room.engine.createCanvasTex
import com.ps1bedroom.room.engine.makeRng
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan
import kotlin.random.Random

/**
 * The 3D-era GTA games on the TV (HTML lines 6900-7260): a small chase-cam drive (III, Vice City, San Andreas, the
 * Stories, IV, V, VI) rendered into a texture that the TV shows.
 *
 * v0.57 rework:
 *  - Traffic drives in real lanes (two each way). Cars in a lane keep a safe gap and slow down behind slower ones, and
 *    the player car is a driver too: it changes lanes only into a clear gap, overtakes through the oncoming lane only
 *    when it's empty, and brakes when boxed in. Nothing drives through anything any more.
 *  - Each game has its own look: city height and density, time of day and weather, a colour grade, era-specific
 *    traffic colours and props (Liberty City's elevated railway, San Andreas' desert hills and power lines, Los
 *    Santos' hills and VINEWOOD sign, Algonquin's tall grey towers), and its own HUD.
 */
class Gta3d(private val renderer: Renderer, private val env: Environment) {
    private class Sun(val y: Float, val col: String)
    private class Theme(
        val sky: Array<String>, val fog: Int, val fogD: Float, val bld: Array<String>, val lit: Float, val road: String, val car: Int,
        val palms: Int, val lamps: Int, val hud: String, val glow: Float? = null, val neon: Boolean = false, val rain: Boolean = false,
        val sun: Sun? = null, val ocean: Boolean = false, val res: IntArray? = null,
        /** building height multiplier (low desert towns .. Algonquin towers) */
        val bldH: Float = 1f,
        /** full-screen colour grade drawn under the HUD, or null */
        val grade: String? = null,
        val traffic: IntArray = intArrayOf(0x94a3b8, 0x16a34a, 0xf8fafc, 0x7c3aed, 0xeab308, 0x0ea5e9),
        val rail: Boolean = false, val hills: Int? = null, val sign: Boolean = false, val poles: Boolean = false,
        val speed: Float = 22f, val chaseH: Float = 2.9f
    )
    private val THEMES = mapOf(
        "iii" to Theme(arrayOf("#0d1220", "#2c3446", "#4a5468"), 0x2b3242, 0.045f, arrayOf("#3b3f47", "#4a4f58", "#2f333a", "#55534e"), 0.75f, "#1f2023", 0xc8b36a, 0, 0xffb347, "iii",
            rain = true, bldH = 1.25f, grade = "rgba(40, 64, 96, 0.20)", traffic = intArrayOf(0xd4a017, 0x6b7280, 0x7f1d1d, 0x14532d, 0x1e3a5f, 0xe5e7eb), rail = true, speed = 20f),
        "vc" to Theme(arrayOf("#14062e", "#5b1a6b", "#ff5fa2"), 0x3a1450, 0.03f, arrayOf("#f9a8d4", "#99f6e4", "#f5f5f4", "#fde68a"), 0.6f, "#2b2533", 0xf8fafc, 1, 0xff5fa2, "vc",
            glow = 0.38f, neon = true, bldH = 0.8f, grade = "rgba(255, 60, 170, 0.10)", traffic = intArrayOf(0xffffff, 0xf472b6, 0x2dd4bf, 0xfacc15, 0x60a5fa, 0xef4444), speed = 23f),
        "sa" to Theme(arrayOf("#2a1f5e", "#c8507a", "#ffb067"), 0x8a5a4a, 0.026f, arrayOf("#c2a27e", "#a47d5b", "#8c6b52", "#d6b48a"), 0.2f, "#3a3530", 0x1f7a3a, 1, 0xffc070, "sa",
            sun = Sun(0.18f, "#ffcf7a"), bldH = 0.45f, grade = "rgba(255, 140, 60, 0.15)", traffic = intArrayOf(0x6d28d9, 0x166534, 0xca8a04, 0x78350f, 0x0f172a, 0xb91c1c), hills = 0x8a5a3a, poles = true),
        "lcs" to Theme(arrayOf("#5f7186", "#8a9bb0", "#b8c4d2"), 0x7e8a99, 0.04f, arrayOf("#6b7280", "#4b5563", "#78716c", "#57534e"), 0.1f, "#3f4146", 0x2563eb, 0, 0xffe0a0, "stories",
            bldH = 1.15f, grade = "rgba(130, 150, 175, 0.16)", traffic = intArrayOf(0xd4a017, 0x334155, 0x9a3412, 0x475569, 0xe2e8f0, 0x065f46), rail = true, speed = 21f),
        "vcs" to Theme(arrayOf("#ff8a5c", "#ffb48a", "#ffe0b0"), 0xd88a7a, 0.028f, arrayOf("#fbcfe8", "#a5f3fc", "#fef3c7", "#fecaca"), 0.1f, "#4a4248", 0x0ea5e9, 1, 0xffe0a0, "stories",
            sun = Sun(0.3f, "#fff1c2"), bldH = 0.7f, grade = "rgba(255, 200, 120, 0.08)", traffic = intArrayOf(0xfef3c7, 0xf43f5e, 0x22d3ee, 0xffffff, 0xfb923c, 0x34d399)),
        "iv" to Theme(arrayOf("#56605c", "#7d857c", "#a9ab9a"), 0x6f766c, 0.046f, arrayOf("#5c5a52", "#4a4842", "#6b665c", "#3f3d38"), 0.15f, "#2f302c", 0x4b5563, 0, 0xfff0c0, "iv",
            bldH = 1.75f, grade = "rgba(110, 100, 60, 0.24)", traffic = intArrayOf(0xd4a017, 0x1f2937, 0x52525b, 0x7c2d12, 0x374151, 0xd6d3d1), speed = 21f, chaseH = 2.6f),
        "v" to Theme(arrayOf("#3b82f6", "#7cb8ff", "#dff1ff"), 0xb5d4ef, 0.02f, arrayOf("#c9d6e3", "#8fb3d9", "#e7e1d6", "#9fb8a8"), 0.05f, "#45464a", 0xff7a00, 1, 0xffffff, "v",
            sun = Sun(0.55f, "#fff7d6"), traffic = intArrayOf(0xffffff, 0x111827, 0x9ca3af, 0xdc2626, 0x1d4ed8, 0xfacc15), hills = 0x7a8a4a, sign = true, speed = 25f),
        "vi" to Theme(arrayOf("#1c0838", "#a02a86", "#ff9a5c"), 0x5a2a6a, 0.024f, arrayOf("#ffd1e8", "#bdf5ff", "#fff3d6", "#e0c8ff"), 0.55f, "#2a2236", 0xe11d48, 2, 0xff7ad9, "vi",
            glow = 0.22f, neon = true, sun = Sun(0.24f, "#ffd27a"), ocean = true, res = intArrayOf(400, 300), grade = "rgba(255, 90, 160, 0.07)",
            traffic = intArrayOf(0xffffff, 0x0f172a, 0xf472b6, 0x22d3ee, 0xa3e635, 0xf59e0b), speed = 26f, chaseH = 2.5f)
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
    private class Mover(val obj: Object3D, val kind: String?, val head: Material? = null, val baseH: Float = 0f)
    private val movers = ArrayList<Mover>()

    // ---- driving ----
    /** Lane centres: 0, 1 oncoming (coming toward the camera, +Z), 2, 3 our direction (driving away, -Z). */
    private val LANES = floatArrayOf(-3.75f, -1.25f, 1.25f, 3.75f)
    private val CAR_LEN = 4.2f
    private class Car(val g: Group, val body: Material, val cabin: Mesh, val riders: List<Mesh>, val wheels: List<Mesh>) {
        var lane = 2
        /** world speed along its direction of travel (m/s) */
        var want = 14f; var v = 14f
        var z = 0f
    }
    private val traffic = ArrayList<Car>()
    private lateinit var player: Car
    private var px = LANES[2]; private var pTargetLane = 2; private var pSpeed = 22f
    private var nextDecision = 0f; private var nextWander = 6f; private var driveT = 0f

    private lateinit var rainLines: Mesh
    private lateinit var ocean: Mesh
    private lateinit var oceanTex: Texture
    private lateinit var sunSprite: Mesh
    private lateinit var sunMat: Material
    private val railParts = ArrayList<Object3D>()
    private lateinit var hillGroup: Group
    private val hillMats = ArrayList<Material>()
    private lateinit var signMesh: Mesh
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
        // Distant hills (San Andreas, Los Santos): fixed far away, unfogged but tinted toward the haze per theme
        hillGroup = Group()
        for (i in 0 until 7) {
            val m = Material(color = Color(0x8a5a3a), lit = false, ps1 = false, fog = false); hillMats += m
            val r = 18f + (i * 7 % 5) * 6f; val hh = 8f + (i * 5 % 4) * 4f
            val hill = Mesh(Geo.cone(r, hh, 7), m)
            hill.position.set(-70f + i * 24f, hh / 2 - 1f, -128f - (i % 2) * 6f)
            hillGroup.add(hill)
        }
        val signTex = createCanvasTex(256, 32) { ctx, w, h ->
            ctx.clearRect(0, 0, w, h); ctx.fillStyle = "#f8fafc"; ctx.font = "bold 28px sans-serif"; ctx.textAlign = "center"; ctx.fillText("VINEWOOD", w / 2, 27)
        }.also { it.tex.nearest = false }
        signMesh = Mesh(Geo.plane(22f, 2.75f), Material(map = signTex.tex, lit = false, ps1 = false, fog = false, transparent = true, depthWrite = false))
        signMesh.position.set(-14f, 10.5f, -120f); hillGroup.add(signMesh)
        scene3.add(hillGroup)
        // Road: double yellow centre line, dashed white lane lines, solid edges; sidewalks either side
        roadTex = createCanvasTex(64, 64, 1f, 12f) { ctx, w, h ->
            ctx.fillStyle = "#ffffff"; ctx.fillRect(0, 0, w, h)
            ctx.fillStyle = "rgba(0,0,0,0.08)"; for (i in 0 until 300) ctx.fillRect(rnd.nextDouble() * w, rnd.nextDouble() * h, 1, 1)
            ctx.fillStyle = "#ffe680"; ctx.fillRect(w / 2 - 2, 0, 1, h); ctx.fillRect(w / 2 + 1, 0, 1, h)
            ctx.fillStyle = "#f8fafc"; ctx.fillRect(w * 0.25 - 0.5, 0, 1, h / 2); ctx.fillRect(w * 0.75 - 0.5, 0, 1, h / 2)
            ctx.fillRect(1, 0, 1, h); ctx.fillRect(w - 2, 0, 1, h)
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
        // Buildings (two rows each side), recoloured and resized per theme
        for (i in 0 until 4) windowTexes += makeWindowTex("#ffffff", 0.4, 11 + i)
        for (i in 0 until 28) {
            val side = if (i % 2 == 1) 1 else -1
            val hgt = 4 + rnd.nextFloat() * 14
            val m = Material(map = windowTexes[i % 4].tex, emissive = Color(0xffffff), emissiveMap = windowTexes[i % 4].tex, emissiveIntensity = 0.3f, ps1 = false)
            emissiveMats += m
            val b = Mesh(Geo.box(4 + rnd.nextFloat() * 2, 1f, 5 + rnd.nextFloat() * 3), m)
            b.scale.y = hgt; b.position.set(side * (12 + rnd.nextFloat() * 3), hgt / 2, -(i / 2f) * 6.5f)
            scene3.add(b); movers += Mover(b, null, baseH = hgt)
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
        // Wooden power poles with a crossbar (San Andreas' back roads)
        for (i in 0 until 8) {
            val p = Group()
            val wood = mat(0x5b4030)
            val post = Mesh(Geo.cylinder(0.1f, 0.13f, 7f, 5), wood); post.position.y = 3.5f
            val bar = Mesh(Geo.box(2.2f, 0.12f, 0.12f), wood); bar.position.y = 6.5f
            p.add(post, bar)
            p.position.set(-9.6f, 0f, -i * 11.25f)
            scene3.add(p); movers += Mover(p, "pole")
        }
        // Liberty City's elevated railway over the left sidewalk: steel pillars that scroll, a deck that doesn't need to
        val steel = mat(0x3b3a36)
        val deck = Mesh(Geo.box(3.4f, 0.5f, segLen * 2), steel); deck.position.set(-8.4f, 6.4f, -segLen * 0.8f); scene3.add(deck); railParts += deck
        for (side in floatArrayOf(-1f, 1f)) {
            val railLine = Mesh(Geo.box(0.12f, 0.14f, segLen * 2), mat(0x6b6b66)); railLine.position.set(-8.4f + side * 0.75f, 6.72f, -segLen * 0.8f); scene3.add(railLine); railParts += railLine
        }
        for (i in 0 until 10) {
            val pillar = Mesh(Geo.box(0.35f, 6.2f, 0.35f), steel); pillar.position.set(-7.0f, 3.1f, -i * 9f)
            scene3.add(pillar); movers += Mover(pillar, "rail"); railParts += pillar
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
        // Traffic: 5 cars each way, spaced out in their lanes
        for (i in 0 until 10) {
            val c = makeCar(0x94a3b8, false)
            c.lane = if (i < 5) (i % 2) else 2 + (i % 2)
            c.want = if (c.lane < 2) 12f + rnd.nextFloat() * 5f else 11f + rnd.nextFloat() * 6f
            c.v = c.want
            c.z = -10f - (i % 5) * 17f - rnd.nextFloat() * 4f
            if (c.lane < 2) c.g.rotation.y = PI.toFloat()
            scene3.add(c.g); traffic += c
        }
        player = makeCar(0xff0000, true); scene3.add(player.g)
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
    private fun makeCar(col: Int, isPlayer: Boolean): Car {
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
            wheels += wh
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
        return Car(g, bodyMat, cabin, riders, wheels)
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
                "pole" -> m.obj.visible = th.poles
                "rail" -> m.obj.visible = th.rail
                null -> {
                    // Buildings: height per city (with some variety), and VI's beach has no buildings on the right
                    val h = m.baseH * th.bldH * (if (th.bldH > 1.4f) 0.8f + rnd.nextFloat() * 0.6f else 1f)
                    m.obj.scale.y = h; m.obj.position.y = h / 2
                    m.obj.visible = !(th.ocean && m.obj.position.x > 0)
                }
            }
        }
        for (p in railParts) p.visible = th.rail
        hillGroup.visible = th.hills != null
        th.hills?.let { hc ->
            val haze = Color(th.fog)
            hillMats.forEachIndexed { i, m -> m.color.setHex(hc); m.color.lerp(haze, 0.35f + (i % 3) * 0.1f) }
        }
        signMesh.visible = th.sign
        ocean.visible = th.ocean
        walkR.color.setHex(if (th.ocean) 0xf0d49a else 0x8b8f96)
        rainLines.visible = th.rain
        sunSprite.visible = th.sun != null
        th.sun?.let { sunMat.color.setStyle(it.col) }
        player.body.color.setHex(th.car)
        traffic.forEachIndexed { i, c -> c.body.color.setHex(th.traffic[i % th.traffic.size]) }
        val convertible = key == "vi" || key == "vc"
        player.cabin.visible = !convertible
        for (d in player.riders) d.visible = convertible
        pSpeed = th.speed
    }

    // ================================================================ driving
    /** True if the car travels toward -Z (our direction). */
    private fun forward(lane: Int) = lane >= 2
    /** Signed world velocity along Z for a car (forward cars drive toward -Z). */
    private fun vz(c: Car) = if (forward(c.lane)) -c.v else c.v

    /** Lane(s) the player currently occupies (two while changing lanes). */
    private fun playerLanes(): IntArray {
        var best = 0; var bd = 99f
        for (i in LANES.indices) { val d = abs(px - LANES[i]); if (d < bd) { bd = d; best = i } }
        if (bd < 0.55f) return intArrayOf(best)
        val other = if (px > LANES[best]) best + 1 else best - 1
        return if (other in LANES.indices) intArrayOf(best, other) else intArrayOf(best)
    }

    /** Free road ahead of the player in [lane] (metres, scaled by how fast the gap closes for oncoming cars). */
    private fun clearAhead(lane: Int): Float {
        var best = 200f
        for (c in traffic) if (c.lane == lane && c.z < 0f) {
            val gap = -c.z - CAR_LEN
            val eff = if (forward(lane)) gap else gap * pSpeed / (pSpeed + c.v)
            if (eff < best) best = eff
        }
        return best
    }
    /** Nothing alongside the player in [lane] (a car there right now, or about to be). */
    private fun sideClear(lane: Int): Boolean {
        for (c in traffic) if (c.lane == lane) {
            val reach = if (forward(lane)) 3f else 3f + (pSpeed + c.v) * 2.5f
            if (c.z > -CAR_LEN - reach && c.z < CAR_LEN + 2f) return false
        }
        return true
    }

    private fun drive(step: Float) {
        driveT += step
        // ---- player AI ----
        val cur = playerLanes()[0]
        if (driveT > nextDecision) {
            nextDecision = driveT + 0.35f
            val here = clearAhead(pTargetLane)
            if (pTargetLane == 1) {
                // in the oncoming lane: get back as soon as there's room
                if (sideClear(2)) pTargetLane = 2
            } else if (here < 24f) {
                val options = intArrayOf(pTargetLane - 1, pTargetLane + 1).filter { it in 1..3 && sideClear(it) }
                val pick = options.maxByOrNull { clearAhead(it) }
                if (pick != null && clearAhead(pick) > here + 10f && (pick != 1 || clearAhead(1) > 70f)) pTargetLane = pick
            } else if (driveT > nextWander) {
                nextWander = driveT + 5f + rnd.nextFloat() * 6f
                val other = if (pTargetLane == 2) 3 else 2
                if (sideClear(other) && clearAhead(other) > 30f) pTargetLane = other
            }
        }
        // speed: cruise, or follow the car ahead when boxed in
        var target = th.speed
        for (l in playerLanes()) {
            for (c in traffic) if (c.lane == l && c.z < 0f) {
                val gap = -c.z - CAR_LEN
                if (forward(l)) { if (gap < 14f) target = min(target, c.v + (gap - 6f) * 0.8f) }
                else if (gap < 30f) target = min(target, 6f)
            }
        }
        target = target.coerceIn(0f, th.speed)
        pSpeed += (target - pSpeed) * min(1f, step * (if (target < pSpeed) 3.5f else 1.2f))
        // steering: ease toward the target lane
        val tx = LANES[pTargetLane]
        val steer = (tx - px).coerceIn(-2.6f * step, 2.6f * step)
        px += steer
        player.g.position.set(px, 0f, 0f)
        player.g.rotation.y = -(steer / max(0.001f, step)) * 0.05f + sin(driveT * 0.9f) * 0.01f
        for (wh in player.wheels) wh.rotation.x -= pSpeed * step * 2.6f

        // ---- traffic: keep lane order and safe gaps (the player counts as a car in its lanes) ----
        val pl = playerLanes()
        for (lane in 0..3) {
            val fwd = forward(lane)
            val inLane = traffic.filter { it.lane == lane }.sortedBy { if (fwd) it.z else -it.z } // front of the queue first
            var aheadZ = Float.NaN; var aheadV = 0f
            for (c in inLane) {
                // the player is ahead of a forward car that's behind it
                if (fwd && lane in pl && c.z > 0f && (aheadZ.isNaN() || aheadZ < 0f)) { aheadZ = 0f; aheadV = pSpeed }
                var v = c.want
                if (!aheadZ.isNaN()) {
                    val gap = abs(c.z - aheadZ) - CAR_LEN
                    if (gap < 9f) v = min(v, aheadV + (gap - 4f) * 0.9f).coerceAtLeast(0f)
                }
                c.v += (v - c.v) * min(1f, step * 3f)
                c.z += (pSpeed + vz(c)) * step
                if (!aheadZ.isNaN()) {
                    // hard rule: never overlap the car in front
                    val minGap = CAR_LEN + 1.2f
                    if (fwd && c.z - aheadZ < minGap) c.z = aheadZ + minGap
                    if (!fwd && aheadZ - c.z < minGap) c.z = aheadZ - minGap
                }
                aheadZ = c.z; aheadV = c.v
            }
            // and a car just in front of the player in its lane is never inside it
            if (fwd && lane in pl) for (c in inLane) if (c.z <= 0f && c.z > -CAR_LEN - 1.2f) c.z = -CAR_LEN - 1.2f
            // oncoming cars must never reach the player head-on: if one is closing in our lane, it stops dead
            if (!fwd && lane in pl) for (c in inLane) if (c.z < 0f && c.z > -CAR_LEN - 1.5f) { c.z = -CAR_LEN - 1.5f; c.v = 0f }
        }
        // ---- respawn cars that left the view, behind the last car of a lane, never on top of anything ----
        for (c in traffic) {
            if (c.z > 16f || c.z < -segLen - 5f) {
                val goFar = c.z > 16f // passed us: come back far ahead
                val lanes = if (forward(c.lane)) intArrayOf(2, 3) else intArrayOf(0, 1)
                val lane = lanes[rnd.nextInt(2)]
                val spawnZ = if (goFar) -segLen + rnd.nextFloat() * 8f else 14f
                val free = traffic.none { it !== c && it.lane == lane && abs(it.z - spawnZ) < CAR_LEN + 6f } &&
                    !(goFar.not() && lane in pl)
                if (free) {
                    c.lane = lane; c.z = spawnZ
                    c.want = if (lane < 2) 12f + rnd.nextFloat() * 5f else 11f + rnd.nextFloat() * 6f; c.v = c.want
                    c.g.rotation.y = if (lane < 2) PI.toFloat() else 0f
                } else c.z = if (goFar) 16f else -segLen - 5f // try again next frame
            }
            c.g.position.set(LANES[c.lane], 0f, c.z)
            for (wh in c.wheels) wh.rotation.x -= c.v * step * 2.6f
        }
    }

    // ================================================================ HUD
    private fun drawHud(g: Float) {
        val ctx = hudObj.ctx
        val W2 = 320; val H2 = 240
        ctx.clearRect(0, 0, W2, H2)
        th.grade?.let { ctx.fillStyle = it; ctx.fillRect(0, 0, W2, H2) }
        val moneyN = (floor(g * 731.0 + 1337).toLong() % 100000000)
        val money = moneyN.toString().padStart(8, '0')
        val stars = 1 + floor((g / 7.0) % 5).toInt()
        val hh = floor(env.hour).toInt() % 24; val mm = floor((env.hour % 1) * 60).toInt()
        val clock = "${hh.toString().padStart(2, '0')}:${mm.toString().padStart(2, '0')}"
        fun radar(cx: Float, cy: Float, r: Float, ring: String, square: Boolean = false) {
            ctx.fillStyle = "rgba(0, 0, 0, 0.45)"
            ctx.beginPath(); if (square) ctx.roundRect(cx - r, cy - r * 0.75f, r * 2, r * 1.5f, 6) else ctx.arc(cx, cy, r, 0, PI * 2); ctx.fill()
            ctx.strokeStyle = ring; ctx.lineWidth = 2f; ctx.stroke()
            ctx.save(); ctx.beginPath(); if (square) ctx.rect(cx - r + 2, cy - r * 0.75f + 2, r * 2 - 4, r * 1.5f - 4) else ctx.arc(cx, cy, r - 2, 0, PI * 2); ctx.clip()
            ctx.translate(cx, cy); ctx.rotate(sin(g * 0.3) * 0.3)
            ctx.strokeStyle = "rgba(200, 200, 200, 0.55)"; ctx.lineWidth = 4f
            for (i in -2..2) {
                ctx.beginPath(); ctx.moveTo(i * 18, -40); ctx.lineTo(i * 18, 40); ctx.stroke()
                ctx.beginPath(); ctx.moveTo(-40, i * 18 + (g * 8) % 18); ctx.lineTo(40, i * 18 + (g * 8) % 18); ctx.stroke()
            }
            ctx.restore()
            ctx.fillStyle = "#ffffff"; ctx.beginPath(); ctx.moveTo(cx, cy - 6); ctx.lineTo(cx - 5, cy + 4); ctx.lineTo(cx + 5, cy + 4); ctx.fill()
        }
        fun bar(x: Float, y: Float, w: Float, k: Float, col: String) {
            ctx.fillStyle = "rgba(0,0,0,0.6)"; ctx.fillRect(x - 1, y - 1, w + 2, 7)
            ctx.fillStyle = col; ctx.fillRect(x, y, w * k, 5)
        }
        fun outlined(s: String, x: Float, y: Float, font: String, fill: String, stroke: String = "#000") {
            ctx.font = font; ctx.lineWidth = 3f; ctx.strokeStyle = stroke; ctx.strokeText(s, x, y); ctx.fillStyle = fill; ctx.fillText(s, x, y)
        }
        fun starRow(y: Float, on: String) {
            ctx.font = "bold 15px sans-serif"
            for (i in 0 until 6) { ctx.fillStyle = if (i < stars) on else "rgba(255,255,255,0.15)"; ctx.fillText("★", W2 - 18f - i * 15, y) }
        }
        ctx.textAlign = "right"
        when (th.hud) {
            "vi" -> {
                // Modern minimal HUD: rounded minimap, white money, no clutter
                ctx.textAlign = "left"
                ctx.fillStyle = "rgba(10, 6, 20, 0.55)"
                ctx.beginPath(); ctx.roundRect(10, H2 - 70, 92, 60, 10); ctx.fill()
                ctx.strokeStyle = "rgba(255,255,255,0.7)"; ctx.lineWidth = 1.5f; ctx.stroke()
                ctx.strokeStyle = "rgba(255, 150, 210, 0.9)"; ctx.lineWidth = 2f
                ctx.beginPath(); ctx.moveTo(56, H2 - 18); ctx.lineTo(56 + sin(g * 0.4) * 10, H2 - 40); ctx.lineTo(70, H2 - 62); ctx.stroke()
                ctx.fillStyle = "#ffffff"; ctx.beginPath(); ctx.moveTo(56, H2 - 46); ctx.lineTo(51, H2 - 36); ctx.lineTo(61, H2 - 36); ctx.fill()
                ctx.textAlign = "right"
                ctx.font = "bold 15px sans-serif"; ctx.fillStyle = "#ffffff"
                ctx.fillText("\$" + String.format(java.util.Locale.US, "%,d", moneyN), W2 - 22f, 30f)
                ctx.font = "10px sans-serif"; ctx.fillStyle = "rgba(255,255,255,0.8)"
                ctx.fillText("LEONIDA · VICE CITY", W2 - 22f, 44f)
            }
            "v" -> {
                // Rectangular minimap with health / armour / ability strips beneath
                radar(52f, H2 - 50f, 40f, "rgba(255,255,255,0.35)", square = true)
                bar(13f, H2 - 14f, 38f, 0.82f, "#4ade80"); bar(53f, H2 - 14f, 22f, 0.5f, "#38bdf8"); bar(77f, H2 - 14f, 14f, 0.3f, "#facc15")
                ctx.font = "bold 16px sans-serif"; ctx.fillStyle = "#ffffff"
                ctx.fillText("\$" + String.format(java.util.Locale.US, "%,d", moneyN), W2 - 16f, 28f)
                starRow(46f, "#ffffff")
            }
            "iv" -> {
                // Grey rounded radar with the health / armour ring, small white money
                radar(46f, H2 - 46f, 32f, "#9ca3af")
                ctx.strokeStyle = "#22c55e"; ctx.lineWidth = 3f; ctx.beginPath(); ctx.arc(46, H2 - 46, 35, PI * 0.55, PI * 1.45); ctx.stroke()
                ctx.strokeStyle = "#38bdf8"; ctx.beginPath(); ctx.arc(46, H2 - 46, 35, -PI * 0.45, PI * 0.45); ctx.stroke()
                ctx.font = "bold 14px sans-serif"; ctx.fillStyle = "#e5e7eb"
                ctx.fillText("\$$moneyN", W2 - 16f, 26f)
                ctx.font = "bold 11px sans-serif"; ctx.fillStyle = "rgba(229,231,235,0.85)"; ctx.fillText("ALGONQUIN", W2 - 16f, 40f)
            }
            "sa" -> {
                // Clock, money, health & armour bars (top right), round radar
                radar(46f, H2 - 46f, 32f, "#000000")
                outlined(clock, W2 - 16f, 22f, "bold 15px monospace", "#ffffff")
                bar(W2 - 96f, 30f, 80f, 0.9f, "#b91c1c"); bar(W2 - 96f, 40f, 80f, 0.55f, "#e5e7eb")
                outlined("\$$money", W2 - 16f, 64f, "bold 16px \"Arial Black\", Impact, sans-serif", "#2f7d32")
                starRow(82f, "#fbbf24")
            }
            "vc" -> {
                // Pink money, a weapon box, the round radar with a pink ring
                radar(46f, H2 - 46f, 32f, "#f472b6")
                ctx.fillStyle = "rgba(0,0,0,0.4)"; ctx.fillRect(16f, 14f, 34f, 34f); ctx.strokeStyle = "#f9a8d4"; ctx.lineWidth = 1.5f; ctx.strokeRect(16f, 14f, 34f, 34f)
                ctx.fillStyle = "#fbcfe8"; ctx.beginPath(); ctx.arc(33, 31, 8, 0, PI * 2); ctx.fill()
                outlined(clock, W2 - 18f, 24f, "bold 14px monospace", "#ffffff")
                outlined("\$$money", W2 - 18f, 44f, "bold 17px \"Arial Black\", Impact, sans-serif", "#f472b6", "#3b0a2a")
                starRow(62f, "#f9a8d4")
            }
            "stories" -> {
                radar(46f, H2 - 46f, 32f, "#e5e7eb")
                outlined("\$$money", W2 - 18f, 28f, "bold 16px \"Arial Black\", Impact, sans-serif", "#86efac")
                bar(W2 - 90f, 36f, 72f, 0.75f, "#ef4444")
                starRow(60f, "#f5f5f4")
                ctx.font = "bold 12px monospace"; ctx.fillStyle = "#ffffff"; ctx.fillText(clock, W2 - 18f, 76f)
            }
            else -> {
                // GTA III: radar circle, green money, wanted stars, clock
                radar(46f, H2 - 46f, 32f, "#9ca3af")
                outlined("\$$money", W2 - 20f, 30f, "bold 17px \"Arial Black\", Impact, sans-serif", "#5fd35f")
                starRow(50f, "#f5f5f4")
                ctx.font = "bold 13px monospace"; ctx.fillStyle = "#ffffff"; ctx.fillText(clock, W2 - 20f, 68f)
            }
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
        val step = if (lastRender == 0L) 0.033f else min(0.1f, (nowMs - lastRender) / 1000f)
        lastRender = nowMs
        drive(step)
        val speed = pSpeed
        // Scroll the world toward the camera and wrap it around
        for (m in movers) { m.obj.position.z += speed * step; if (m.obj.position.z > 12) m.obj.position.z -= segLen }
        roadTex.offsetY = (roadTex.offsetY + speed * step / 15) % 1
        if (ocean.visible) oceanTex.offsetY = (oceanTex.offsetY + speed * step / 40) % 1
        // Chase cam follows with a little lag and sway
        cam3.position.set(px * 0.6f, th.chaseH, 7.2f)
        cam3.lookAtTarget(camTarget.set(px * 0.85f, 1.0f, -6f))
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
