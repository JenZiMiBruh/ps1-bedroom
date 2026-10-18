package com.ps1bedroom.room

import com.ps1bedroom.room.engine.Canvas2D
import com.ps1bedroom.room.engine.CanvasTex
import com.ps1bedroom.room.engine.DOUBLE_SIDE
import com.ps1bedroom.room.engine.Geo
import com.ps1bedroom.room.engine.GeoCurves
import com.ps1bedroom.room.engine.Geometry
import com.ps1bedroom.room.engine.Group
import com.ps1bedroom.room.engine.Material
import com.ps1bedroom.room.engine.Mesh
import com.ps1bedroom.room.engine.Object3D
import com.ps1bedroom.room.engine.PS1
import com.ps1bedroom.room.engine.PointLight
import com.ps1bedroom.room.engine.Weather
import com.ps1bedroom.room.engine.createCanvasTex
import com.ps1bedroom.room.engine.makeRng
import com.ps1bedroom.room.engine.weatherCanvas
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * MORE OF THE ROOM (HTML lines 12007-12290): dresser with the boombox, photo and trophy, the band poster,
 * nightstand with the reading lamp, water glass and book, wastebasket with crumpled paper, backpack, the wall
 * calendar (today's month laid out like 2001, days crossed off) and the movie poster over the bed.
 */
class Furniture(private val room: Room) {
    private val scene = room.scene
    lateinit var lampLight: PointLight
    private lateinit var lampShadeMat: Material
    var lampOn = false; private set
    lateinit var radioDisplayMat: Material
    private lateinit var calendarTex: CanvasTex
    private var calendarKey = ""
    private val CAL_WEATHER = Weather(seed = 103.0, blotch = 0.12, warm = 0.15, creases = 1)

    private fun propPart(parent: Object3D, geo: Geometry, mat: Material, x: Float, y: Float, z: Float, tag: String? = null): Mesh {
        val m = Mesh(geo, mat)
        m.position.set(x, y, z); m.castShadow = true; m.receiveShadow = true
        parent.add(m)
        if (tag != null) room.registerInteractable(m, tag)
        return m
    }
    private fun propPart(parent: Object3D, geo: Geometry, mats: List<Material>, x: Float, y: Float, z: Float, tag: String? = null): Mesh {
        val m = Mesh(geo, mats)
        m.position.set(x, y, z); m.castShadow = true; m.receiveShadow = true
        parent.add(m)
        if (tag != null) room.registerInteractable(m, tag)
        return m
    }
    private fun wallPicture(w: Float, h: Float, x: Float, y: Float, z: Float, rotY: Float, weather: Weather, draw: (Canvas2D, Int, Int) -> Unit): Mesh {
        val tex = createCanvasTex(64, (64 * h / w).roundToInt(), weather = weather, draw = draw).tex
        // A strong slope-scaled offset plus 1 cm off the wall: the PS1 vertex snap shifts the wall's depth at grazing
        // angles, which made far-away art flicker and clip into the wall
        val m = Mesh(Geo.plane(w, h), PS1.material(map = tex, polygonOffset = true, polygonOffsetFactor = -4f, polygonOffsetUnits = -4f))
        m.position.set(x, y, z); m.rotation.y = rotY
        m.translateZ(0.01f)
        scene.add(m)
        return m
    }

    fun build() {
        val woodTex = room.tex.wood
        // ---- Dresser on the left wall (front faces +X), six drawers with knobs ----
        PS1.syncGrime(257)
        val dresserGroup = Group()
        dresserGroup.position.set(-3.42f, 0f, 2.3f)
        scene.add(dresserGroup)
        val matDresser = PS1.material(map = woodTex, color = 0x8a6a55)
        propPart(dresserGroup, Geo.box(0.6f, 1.36f, 1.3f, 2, 4, 4), matDresser, 0f, 0.7f, 0f, "dresser")
        propPart(dresserGroup, Geo.box(0.64f, 0.05f, 1.36f), matDresser, 0.01f, 1.405f, 0f, "dresser")
        for (r in 0 until 3) for (dz in floatArrayOf(-0.32f, 0.32f)) {
            propPart(dresserGroup, Geo.box(0.03f, 0.36f, 0.6f), PS1.material(map = woodTex, color = 0x9c7a62), 0.31f, 0.27f + r * 0.42f, dz)
            propPart(dresserGroup, Geo.sphere(0.025f, 6, 5), PS1.material(color = 0xc9a227), 0.34f, 0.27f + r * 0.42f, dz)
        }
        // Boombox: twin speakers, cassette deck, handle, antenna, glowing tuner display
        val boomboxGroup = Group()
        boomboxGroup.position.set(0.02f, 1.43f, 0.05f)
        dresserGroup.add(boomboxGroup)
        val matBoombox = PS1.material(color = 0x2b2e36)
        propPart(boomboxGroup, Geo.box(0.2f, 0.3f, 0.66f, 1, 2, 4), matBoombox, 0f, 0.15f, 0f, "boombox")
        for (sz in floatArrayOf(-0.21f, 0.21f)) {
            propPart(boomboxGroup, Geo.cylinder(0.1f, 0.1f, 0.02f, 14), PS1.material(color = 0x111317), 0.1f, 0.14f, sz, "boombox").rotation.z = (PI / 2).toFloat()
            propPart(boomboxGroup, Geo.cylinder(0.035f, 0.035f, 0.025f, 10), PS1.material(color = 0x6b7280), 0.105f, 0.14f, sz).rotation.z = (PI / 2).toFloat()
        }
        // Cassette deck: its own front face shows the album cover when there is one (My Music phase)
        val cassetteDeckMat = PS1.material(color = 0x4b5563)
        propPart(boomboxGroup, Geo.box(0.03f, 0.1f, 0.18f), List(6) { cassetteDeckMat }, 0.1f, 0.13f, 0f, "bbscreen")
        propPart(boomboxGroup, Geo.box(0.04f, 0.04f, 0.5f), matBoombox, 0f, 0.34f, 0f)
        for (hz in floatArrayOf(-0.24f, 0.24f)) propPart(boomboxGroup, Geo.box(0.04f, 0.06f, 0.03f), matBoombox, 0f, 0.31f, hz)
        val boomAntenna = propPart(boomboxGroup, Geo.cylinder(0.005f, 0.005f, 0.5f, 4), PS1.material(color = 0xd1d5db), -0.05f, 0.5f, 0.26f)
        boomAntenna.rotation.x = -0.4f
        radioDisplayMat = PS1.material(color = 0x1e3a2a, emissive = 0x22c55e, emissiveIntensity = 0.05f)
        propPart(boomboxGroup, Geo.box(0.012f, 0.035f, 0.14f), List(6) { radioDisplayMat }, 0.105f, 0.245f, 0f, "bbscreen")
        // Photo frame, trophy, and a band poster above the dresser
        val photo = propPart(dresserGroup, Geo.box(0.03f, 0.22f, 0.17f), PS1.material(color = 0x1f2937), -0.1f, 1.54f, 0.5f)
        photo.rotation.z = 0.12f
        val photoTex = createCanvasTex(32, 40, weather = Weather(seed = 101.0, blotch = 0.2, warm = 0.3)) { ctx, w, h ->
            ctx.fillStyle = "#7dd3fc"; ctx.fillRect(0, 0, w, h * 0.6)
            ctx.fillStyle = "#65a30d"; ctx.fillRect(0, h * 0.6, w, h * 0.4)
            ctx.fillStyle = "#7c2d12"; ctx.fillRect(9, 14, 6, 14); ctx.fillRect(18, 16, 6, 12)
            ctx.fillStyle = "#fde68a"; ctx.beginPath(); ctx.arc(12, 11, 3, 0, PI * 2); ctx.arc(21, 13, 3, 0, PI * 2); ctx.fill()
        }.tex
        val photoFace = Mesh(Geo.plane(0.13f, 0.17f), PS1.material(map = photoTex))
        photoFace.rotation.set(0f, (PI / 2).toFloat(), 0.12f)
        photoFace.position.set(-0.084f, 1.54f, 0.5f)
        dresserGroup.add(photoFace)
        propPart(dresserGroup, Geo.box(0.1f, 0.05f, 0.1f), PS1.material(color = 0x3f2a14), 0f, 1.455f, -0.5f)
        propPart(dresserGroup, Geo.cylinder(0.012f, 0.03f, 0.14f, 6), PS1.material(color = 0xd4a017), 0f, 1.55f, -0.5f)
        propPart(dresserGroup, Geo.cylinder(0.05f, 0.02f, 0.07f, 8), PS1.material(color = 0xd4a017), 0f, 1.65f, -0.5f)
        wallPicture(0.9f, 1.25f, -3.715f, 2.95f, 2.3f, (PI / 2).toFloat(), Weather(seed = 102.0, blotch = 0.2, creases = 3, edge = 0.25)) { ctx, w, h ->
            val g = ctx.createLinearGradient(0, 0, 0, h)
            g.addColorStop(0, "#111827"); g.addColorStop(1, "#7f1d1d")
            ctx.fillStyle = g; ctx.fillRect(0, 0, w, h)
            ctx.fillStyle = "#facc15"; ctx.font = "bold 13px Impact, sans-serif"; ctx.textAlign = "center"
            ctx.fillText("SMASH", w / 2, 18); ctx.fillText("CITY", w / 2, 32)
            ctx.fillStyle = "#0b0b0f"
            intArrayOf(16, 32, 48).forEachIndexed { i, x -> ctx.beginPath(); ctx.arc(x, 52 + (i % 2) * 3, 5, 0, PI * 2); ctx.fill(); ctx.fillRect(x - 5, 56 + (i % 2) * 3, 10, 20) }
            ctx.fillStyle = "#f8fafc"; ctx.font = "bold 6px sans-serif"
            ctx.fillText("ALL STAR KIDS TOUR 2001", w / 2, h - 6)
        }

        // ---- Nightstand by the head of the bed, with a reading lamp, water glass and a book ----
        val nightstandGroup = Group()
        nightstandGroup.position.set(1.15f, 0f, -2.6f)
        scene.add(nightstandGroup)
        propPart(nightstandGroup, Geo.box(0.55f, 0.62f, 0.48f, 2, 2, 2), matDresser, 0f, 0.31f, 0f)
        propPart(nightstandGroup, Geo.box(0.03f, 0.18f, 0.4f), PS1.material(map = woodTex, color = 0x9c7a62), 0f, 0.46f, 0.245f).rotation.y = (PI / 2).toFloat()
        propPart(nightstandGroup, Geo.sphere(0.02f, 6, 5), PS1.material(color = 0xc9a227), 0f, 0.46f, 0.27f)
        propPart(nightstandGroup, Geo.cylinder(0.08f, 0.1f, 0.04f, 10), PS1.material(color = 0x3f3f46), -0.08f, 0.64f, -0.05f, "lamp")
        propPart(nightstandGroup, Geo.cylinder(0.012f, 0.012f, 0.4f, 6), PS1.material(color = 0xa1a1aa), -0.08f, 0.84f, -0.05f, "lamp")
        lampShadeMat = PS1.material(color = 0xe7dcc0, emissive = 0xffd28a, emissiveIntensity = 0.0f)
        lampShadeMat.side = DOUBLE_SIDE
        val lampShade = Mesh(Geo.cylinder(0.1f, 0.17f, 0.2f, 12, 1, true), lampShadeMat)
        lampShade.position.set(-0.08f, 1.06f, -0.05f)
        nightstandGroup.add(lampShade); room.registerInteractable(lampShade, "lamp")
        lampLight = PointLight(0xffc27a, 0f, 4.2f, 1.6f)
        lampLight.position.set(1.07f, 1.02f, -2.65f)
        scene.add(lampLight)
        val water = propPart(nightstandGroup, Geo.cylinder(0.035f, 0.03f, 0.11f, 8), PS1.material(color = 0xbfdbfe, transparent = true, opacity = 0.55f), -0.21f, 0.675f, 0.17f)
        water.castShadow = false
        propPart(nightstandGroup, Geo.box(0.16f, 0.035f, 0.22f), PS1.material(color = 0x1d4ed8), 0.1f, 0.638f, -0.1f).rotation.y = 0.3f

        // ---- Wastebasket by the desk with crumpled paper ----
        val basketGroup = Group()
        basketGroup.position.set(0.35f, 0.03f, -0.85f)
        scene.add(basketGroup)
        val basket = propPart(basketGroup, Geo.cylinder(0.17f, 0.13f, 0.42f, 12, 1, true), PS1.material(color = 0x334155, side = DOUBLE_SIDE), 0f, 0.21f, 0f)
        basket.castShadow = true
        propPart(basketGroup, Geo.cylinder(0.13f, 0.13f, 0.01f, 12), PS1.material(color = 0x1e293b), 0f, 0.005f, 0f)
        val paperMat = PS1.material(color = 0xe5e7eb)
        fun crumple(r: Float, seed: Int): Geometry {
            val g = GeoCurves.icosahedron(r, 1)
            val p = g.positions
            val rr = makeRng(seed)
            for (i in 0 until g.vertexCount) { val k = (0.75 + rr() * 0.4).toFloat(); p[i * 3] *= k; p[i * 3 + 1] *= k; p[i * 3 + 2] *= k }
            g.computeVertexNormals()
            return g
        }
        arrayOf(floatArrayOf(0.03f, 0.36f, 0.02f), floatArrayOf(-0.06f, 0.38f, -0.04f), floatArrayOf(0.05f, 0.4f, -0.06f)).forEachIndexed { i, pp ->
            propPart(basketGroup, crumple(0.06f, 7 + i), paperMat, pp[0], pp[1], pp[2])
        }
        propPart(basketGroup, crumple(0.055f, 13), paperMat, 0.26f, 0.0f, 0.1f) // missed shot on the floor

        // ---- Backpack standing by the dresser ----
        val backpack = Group()
        backpack.position.set(-3.02f, 0.01f, 1.42f)
        backpack.rotation.set(0f, 1.2f, 0f)
        scene.add(backpack)
        propPart(backpack, Geo.box(0.36f, 0.48f, 0.18f, 2, 2, 1), PS1.material(color = 0x0f766e), 0f, 0.24f, 0f)
        propPart(backpack, Geo.box(0.28f, 0.2f, 0.06f), PS1.material(color = 0x134e4a), 0f, 0.16f, 0.11f)
        for (sx in floatArrayOf(-0.1f, 0.1f)) propPart(backpack, Geo.box(0.04f, 0.4f, 0.025f), PS1.material(color = 0x0b1f1e), sx, 0.26f, -0.1f)
        propPart(backpack, Geo.box(0.12f, 0.03f, 0.03f), PS1.material(color = 0x0b1f1e), 0f, 0.5f, 0f) // carry handle

        // ---- Wall calendar on the left wall by the desk ----
        calendarTex = createCanvasTex(128, 198, weather = CAL_WEATHER) { ctx, w, h -> drawCalendar(ctx, w, h) }
        val calendarMesh = Mesh(Geo.plane(0.55f, 0.85f), PS1.material(map = calendarTex.tex, polygonOffset = true, polygonOffsetFactor = -4f, polygonOffsetUnits = -4f))
        calendarMesh.position.set(-3.715f, 2.55f, -0.15f)
        calendarMesh.rotation.y = (PI / 2).toFloat()
        calendarMesh.translateZ(0.01f)
        scene.add(calendarMesh)
        calendarKey = calendarDay().let { "${it[0]}/${it[1]}" }

        // ---- Movie poster over the bed ----
        wallPicture(0.85f, 1.2f, 4.365f, 2.9f, -0.6f, (-PI / 2).toFloat(), Weather(seed = 104.0, blotch = 0.2, creases = 2, edge = 0.2)) { ctx, w, h ->
            ctx.fillStyle = "#020617"; ctx.fillRect(0, 0, w, h)
            ctx.fillStyle = "#22c55e"
            for (i in 0 until 90) ctx.fillRect((i * 13) % w, (i * 29 + i * i) % h, 1, 2 + (i % 4))
            ctx.fillStyle = "#0b0b0f"
            ctx.beginPath(); ctx.arc(w / 2, 34, 8, 0, PI * 2); ctx.fill()
            ctx.fillRect(w / 2 - 9, 42, 18, 30)
            ctx.fillStyle = "#16a34a"; ctx.fillRect(w / 2 - 6, 32, 5, 2); ctx.fillRect(w / 2 + 1, 32, 5, 2)
            ctx.fillStyle = "#4ade80"; ctx.font = "bold 9px monospace"; ctx.textAlign = "center"
            ctx.fillText("THE", w / 2, 10); ctx.fillText("MATRIKS", w / 2, 20)
        }
    }

    fun toggleLamp() {
        lampOn = !lampOn
        lampLight.intensity = if (lampOn) 1.1f else 0f
        lampShadeMat.emissiveIntensity = if (lampOn) 0.85f else 0f
    }

    // ---- Calendar: today's month laid out like 2001, days crossed off up to today, a pixel picture per month ----
    private val CAL_MONTHS = arrayOf("JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE", "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER")
    private fun calPx(ctx: Canvas2D, c: String, x: Double, y: Double, w: Double = 1.0, h: Double = 1.0) {
        ctx.fillStyle = c; ctx.fillRect(jsRound(x), jsRound(y), jsRound(w), jsRound(h))
    }
    private fun calPx(ctx: Canvas2D, c: String, x: Number, y: Number, w: Number = 1, h: Number = 1) = calPx(ctx, c, x.toDouble(), y.toDouble(), w.toDouble(), h.toDouble())
    private fun jsRound(v: Double) = floor(v + 0.5)
    private fun drawCalArt(ctx: Canvas2D, m: Int, w: Int, h: Int) {
        when (m) {
            0 -> { // January: snowman in the snow
                calPx(ctx, "#1e3a8a", 0, 0, w, h); calPx(ctx, "#e0f2fe", 0, h - 18, w, 18)
                for (i in 0 until 40) calPx(ctx, "#ffffff", (i * 37) % w, (i * 23) % (h - 18), 2, 2)
                calPx(ctx, "#f8fafc", 50, 44, 28, 26); calPx(ctx, "#f8fafc", 54, 26, 20, 19)
                calPx(ctx, "#0f172a", 58, 32, 3, 3); calPx(ctx, "#0f172a", 67, 32, 3, 3); calPx(ctx, "#f97316", 63, 36, 6, 2)
                calPx(ctx, "#dc2626", 52, 44, 24, 4); calPx(ctx, "#111827", 52, 23, 24, 3); calPx(ctx, "#111827", 56, 10, 16, 13)
            }
            1 -> { // February: hearts
                calPx(ctx, "#fbcfe8", 0, 0, w, h)
                fun heart(x: Int, y: Int, s: Int, c: String) { calPx(ctx, c, x, y, 2 * s, 2 * s); calPx(ctx, c, x + 3 * s, y, 2 * s, 2 * s); calPx(ctx, c, x - s, y + s, 7 * s, 2 * s); calPx(ctx, c, x, y + 3 * s, 5 * s, s); calPx(ctx, c, x + s, y + 4 * s, 3 * s, s); calPx(ctx, c, x + 2 * s, y + 5 * s, s, s) }
                heart(46, 18, 7, "#dc2626"); heart(12, 12, 3, "#ec4899"); heart(98, 50, 3, "#ec4899"); heart(18, 58, 2, "#be185d"); heart(104, 10, 2, "#be185d")
            }
            2 -> { // March: kite over green hills
                calPx(ctx, "#7dd3fc", 0, 0, w, h); calPx(ctx, "#4ade80", 0, h - 22, w, 22); calPx(ctx, "#16a34a", 0, h - 12, w, 12)
                for (i in 0 until 14) { calPx(ctx, "#facc15", 70 - i, 8 + i, 2 * i + 1, 1); calPx(ctx, "#ef4444", 70 - (14 - i), 22 + i, 2 * (14 - i) + 1, 1) }
                for (i in 0 until 30) calPx(ctx, "#334155", 70 - i * 0.6, 36.0 + i, 1.0, 1.0)
                for (pp in arrayOf(intArrayOf(64, 44), intArrayOf(59, 54), intArrayOf(55, 62))) calPx(ctx, "#2563eb", pp[0], pp[1], 4, 2)
            }
            3 -> { // April: umbrella in the rain
                calPx(ctx, "#64748b", 0, 0, w, h)
                for (i in 0 until 50) calPx(ctx, "#bfdbfe", (i * 29) % w, (i * 17) % h, 1, 4)
                for (r in 0 until 14) calPx(ctx, if (r % 2 == 1) "#facc15" else "#f97316", 64 - r * 2.2, 16.0 + r, r * 4.4 + 1, 1.0)
                calPx(ctx, "#1f2937", 63, 30, 2, 34); calPx(ctx, "#1f2937", 58, 62, 7, 2); calPx(ctx, "#1f2937", 57, 58, 2, 5)
            }
            4 -> { // May: flowers
                calPx(ctx, "#bae6fd", 0, 0, w, h); calPx(ctx, "#22c55e", 0, h - 16, w, 16)
                arrayOf(20 to "#f472b6", 48 to "#facc15", 76 to "#a78bfa", 104 to "#fb923c").forEachIndexed { i, (x, c) ->
                    val y = 22 + (i % 2) * 10; calPx(ctx, "#15803d", x, y + 6, 2, h - y - 6)
                    calPx(ctx, c, x - 5, y, 12, 6); calPx(ctx, c, x - 2, y - 4, 6, 14); calPx(ctx, "#fef08a", x - 1, y + 1, 4, 4)
                }
            }
            5 -> { // June: sun over the beach
                calPx(ctx, "#38bdf8", 0, 0, w, h); calPx(ctx, "#2563eb", 0, h - 28, w, 10); calPx(ctx, "#fde68a", 0, h - 18, w, 18)
                calPx(ctx, "#facc15", 50, 8, 28, 28); calPx(ctx, "#fde047", 46, 14, 36, 16)
                for (i in 0 until 8) calPx(ctx, "#ffffff", i * 16 + 4, h - 27, 6, 1)
                calPx(ctx, "#7c2d12", 96, h - 34, 2, 22); for (r in 0 until 7) calPx(ctx, if (r % 2 == 1) "#ffffff" else "#ef4444", 97 - r * 3, h - 40 + r, r * 6 + 1, 1)
            }
            6 -> { // July: fireworks
                calPx(ctx, "#0b1026", 0, 0, w, h)
                for ((cx, cy, c) in arrayOf(Triple(34, 26, "#ef4444"), Triple(90, 22, "#60a5fa"), Triple(62, 50, "#facc15"))) {
                    for (a in 0 until 12) { val an = a / 12.0 * PI * 2; var r = 4; while (r < 16) { calPx(ctx, c, cx + cos(an) * r, cy + sin(an) * r, 2.0, 2.0); r += 3 } }
                    calPx(ctx, "#ffffff", cx - 1, cy - 1, 3, 3)
                }
            }
            7 -> { // August: ice cream cone
                calPx(ctx, "#fde68a", 0, 0, w, h)
                for (i in 0 until 30) calPx(ctx, if ((i shr 2) % 2 == 1) "#b45309" else "#d97706", 50 + i * 0.47, 40.0 + i, 28 - i * 0.94, 1.0)
                calPx(ctx, "#f9a8d4", 46, 22, 36, 20); calPx(ctx, "#fbcfe8", 50, 12, 28, 12); calPx(ctx, "#dc2626", 62, 5, 5, 7)
                calPx(ctx, "#60a5fa", 54, 24, 2, 2); calPx(ctx, "#34d399", 72, 28, 2, 2); calPx(ctx, "#facc15", 62, 34, 2, 2); calPx(ctx, "#a78bfa", 58, 16, 2, 2)
            }
            8 -> { // September: apple and pencil, back to school
                calPx(ctx, "#166534", 0, 0, w, h); calPx(ctx, "#78350f", 0, h - 10, w, 10)
                calPx(ctx, "#dc2626", 22, 26, 30, 30); calPx(ctx, "#dc2626", 18, 32, 38, 18); calPx(ctx, "#7c2d12", 36, 18, 3, 9); calPx(ctx, "#22c55e", 39, 18, 8, 4)
                calPx(ctx, "#facc15", 70, 34, 40, 10); calPx(ctx, "#fca5a5", 110, 34, 6, 10); calPx(ctx, "#fde68a", 62, 36, 8, 6); calPx(ctx, "#111827", 58, 38, 4, 2)
                ctx.fillStyle = "#f8fafc"; ctx.font = "bold 10px monospace"; ctx.textAlign = "left"; ctx.textBaseline = "top"; ctx.fillText("ABC", 74, 12)
            }
            9 -> { // October: jack-o'-lantern under the moon
                calPx(ctx, "#1e1b4b", 0, 0, w, h); calPx(ctx, "#fef3c7", 98, 8, 14, 14); calPx(ctx, "#1e1b4b", 104, 6, 10, 12)
                calPx(ctx, "#ea580c", 40, 28, 48, 34); calPx(ctx, "#f97316", 46, 24, 36, 42); calPx(ctx, "#166534", 62, 16, 5, 9)
                calPx(ctx, "#facc15", 50, 34, 8, 6); calPx(ctx, "#facc15", 70, 34, 8, 6); calPx(ctx, "#facc15", 52, 48, 24, 5); calPx(ctx, "#f97316", 58, 48, 4, 2); calPx(ctx, "#f97316", 68, 51, 4, 2)
                for (pp in arrayOf(intArrayOf(14, 14), intArrayOf(26, 24))) { val x = pp[0]; val y = pp[1]; calPx(ctx, "#0b0b0f", x, y, 8, 2); calPx(ctx, "#0b0b0f", x - 3, y - 2, 3, 2); calPx(ctx, "#0b0b0f", x + 8, y - 2, 3, 2) }
            }
            10 -> { // November: falling leaves
                calPx(ctx, "#fef3c7", 0, 0, w, h); calPx(ctx, "#92400e", 0, h - 8, w, 8)
                for ((x, y, c) in arrayOf(Triple(18, 14, "#dc2626"), Triple(52, 30, "#f59e0b"), Triple(88, 12, "#ea580c"), Triple(100, 46, "#b45309"), Triple(30, 50, "#ca8a04"), Triple(70, 56, "#dc2626"))) {
                    calPx(ctx, c, x, y, 10, 6); calPx(ctx, c, x + 3, y - 3, 4, 12); calPx(ctx, "#78350f", x + 4, y + 9, 2, 4)
                }
            }
            else -> { // December: tree with lights
                calPx(ctx, "#0f172a", 0, 0, w, h); for (i in 0 until 30) calPx(ctx, "#ffffff", (i * 41) % w, (i * 19) % h, 1, 1)
                for (r in 0 until 45) { val half = (r % 15) * 1.6 + floor(r / 15.0) * 4 + 1; calPx(ctx, if (r % 15 < 2) "#14532d" else "#16a34a", 64 - half, 14.0 + r, half * 2, 1.0) }
                calPx(ctx, "#78350f", 60, 59, 8, 9); calPx(ctx, "#facc15", 61, 7, 6, 6)
                for ((x, y, c) in arrayOf(Triple(56, 26, "#ef4444"), Triple(70, 34, "#60a5fa"), Triple(50, 46, "#facc15"), Triple(76, 52, "#ef4444"), Triple(64, 40, "#f472b6"), Triple(58, 54, "#60a5fa"))) calPx(ctx, c, x, y, 3, 3)
            }
        }
    }
    private fun calendarDay(): IntArray { val d = Calendar.getInstance(); return intArrayOf(d.get(Calendar.MONTH), d.get(Calendar.DAY_OF_MONTH)) }
    private fun drawCalendar(ctx: Canvas2D, w: Int, h: Int) {
        val (m, day) = calendarDay()
        val artH = (h * 0.4).roundToInt()
        ctx.fillStyle = "#f5f5f4"; ctx.fillRect(0, 0, w, h)
        ctx.save(); ctx.beginPath(); ctx.rect(4, 5, w - 8, artH - 5); ctx.clip(); ctx.translate(4, 5); ctx.scale((w - 8) / 128.0, (artH - 5) / 76.0)
        drawCalArt(ctx, m, 128, 76); ctx.restore()
        for (x in doubleArrayOf(w * 0.3, w * 0.7)) { ctx.fillStyle = "#6b7280"; ctx.fillRect(x - 2, 0, 4, 4) } // binding holes
        ctx.fillStyle = "#b91c1c"; ctx.font = "bold 13px monospace"; ctx.textAlign = "center"; ctx.textBaseline = "middle"
        ctx.fillText(CAL_MONTHS[m] + " 2001", w / 2, artH + 11)
        val first = Calendar.getInstance().apply { clear(); set(2001, m, 1) }.get(Calendar.DAY_OF_WEEK) - 1
        val days = Calendar.getInstance().apply { clear(); set(2001, m, 1) }.getActualMaximum(Calendar.DAY_OF_MONTH)
        val gx = 4.0; val gy = artH + 22.0; val cw = (w - 8) / 7.0; val chh = (h - gy - 4) / 7
        ctx.font = "bold 8px monospace"; ctx.fillStyle = "#374151"
        "SMTWTFS".forEachIndexed { c, l -> ctx.fillText(l.toString(), gx + c * cw + cw / 2, gy + chh / 2) }
        ctx.font = "bold 9px monospace"
        for (i in 0 until 42) {
            val r = i / 7 + 1; val c = i % 7; val x = gx + c * cw; val y = gy + r * chh; val dn = i - first + 1
            ctx.strokeStyle = "#d1d5db"; ctx.lineWidth = 1f; ctx.strokeRect(jsRound(x) + 0.5, jsRound(y) + 0.5, jsRound(cw) - 1, jsRound(chh) - 1)
            if (dn < 1 || dn > days) continue
            ctx.fillStyle = if (dn == day) "#b91c1c" else "#1f2937"; ctx.fillText(dn.toString(), x + cw / 2, y + chh / 2 + 1)
            if (dn < day) { ctx.strokeStyle = "rgba(220,38,38,0.8)"; ctx.lineWidth = 1.5f; ctx.beginPath(); ctx.moveTo(x + 2, y + 2); ctx.lineTo(x + cw - 2, y + chh - 2); ctx.moveTo(x + cw - 2, y + 2); ctx.lineTo(x + 2, y + chh - 2); ctx.stroke() }
            if (dn == day) { ctx.strokeStyle = "#dc2626"; ctx.lineWidth = 1.5f; ctx.beginPath(); ctx.ellipse(x + cw / 2, y + chh / 2, cw / 2 - 1, chh / 2 - 1, 0, 0, PI * 2); ctx.stroke() }
        }
        ctx.textBaseline = "alphabetic"; ctx.textAlign = "left"
    }
    /** Redraws the calendar when the date changes (the HTML checks once a minute). */
    fun refreshCalendar() {
        val (m, day) = calendarDay(); val k = "$m/$day"
        if (k == calendarKey) return
        calendarKey = k
        drawCalendar(calendarTex.ctx, 128, 198); weatherCanvas(calendarTex.ctx, 128, 198, CAL_WEATHER); calendarTex.tex.needsUpdate()
    }
}
