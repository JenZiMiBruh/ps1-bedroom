package com.ps1bedroom.room

import android.graphics.Bitmap
import com.ps1bedroom.room.engine.Canvas2D
import com.ps1bedroom.room.engine.CanvasTex
import com.ps1bedroom.room.engine.DOUBLE_SIDE
import com.ps1bedroom.room.engine.Geo
import com.ps1bedroom.room.engine.GeoCurves
import com.ps1bedroom.room.engine.Geometry
import com.ps1bedroom.room.engine.Group
import com.ps1bedroom.room.engine.Material
import com.ps1bedroom.room.engine.Mesh
import com.ps1bedroom.room.engine.PS1
import com.ps1bedroom.room.engine.PointLight
import com.ps1bedroom.room.engine.Texture
import com.ps1bedroom.room.engine.Weather
import com.ps1bedroom.room.engine.createCanvasTex
import com.ps1bedroom.room.engine.makeRng
import com.ps1bedroom.room.engine.weatherCanvas
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * HTML lines 12794-13296: the VCR tapes and what they play, the answering machine, glow-in-the-dark ceiling
 * constellations, the space heater, the poster above the desk, room colours, the seasonal decorations and the
 * boombox radio stations. (The phone ringing, the heater hum and the radio sound come with the audio phase.)
 */
class Extras(private val room: Room, private val env: Environment, private val tv: Tv, private val desk: Desk, private val bedroom: Bedroom) {
    private val scene = room.scene
    private val rnd = Random.Default

    // ---- 1.2 VCR tapes: tap a tape on the TV cart to put it in the VCR (channel 3 plays whatever's in the deck) ----
    private class Tape(val title: String, val label: String, val ink: String)
    private val TAPES = listOf(Tape("ALIEN NIGHT", "#f8fafc", "#14532d"), Tape("TAPED OFF TV '99", "#fde047", "#7c2d12"),
        Tape("KUNG FU MIDNIGHT", "#fca5a5", "#7f1d1d"), Tape("OCEAN DREAMS", "#a5f3fc", "#0c4a6e"))
    private lateinit var tapeLabelTex: List<Texture>
    /** Which tape sits in each shelf slot (the one not on the shelf is in the VCR). */
    private val shelfTapes = intArrayOf(1, 2, 3)
    var currentTape = 0; private set
    private var tapeStartedAt = 0f

    // ---- Answering machine ----
    lateinit var machineLedMat: Material
    val messages = ArrayList<Pair<String, String>>()

    // ---- Ceiling stars, heater ----
    private lateinit var ceilingStarMat: Material
    private var starCharge = 1f
    private lateinit var coilMat: Material
    private lateinit var heaterLight: PointLight
    var heaterMode = "auto"; private set
    private var heaterOn = false
    private var heaterGlow = 0f

    // ---- Poster, room colours, decorations ----
    private lateinit var posterObj: CanvasTex
    private lateinit var posterWall: Mesh
    var posterChoice = "none"; private set
    private class Recolor(val tex: Texture, val opts: LinkedHashMap<String, Pair<String, String>>) { lateinit var original: Bitmap }
    private lateinit var RECOLOR: Map<String, Recolor>
    val roomColors = linkedMapOf("walls" to "navy", "carpet" to "navy", "sheets" to "space")
    private val decor = linkedMapOf("halloween" to Group(), "winter" to Group(), "valentine" to Group(), "summer" to Group())
    private lateinit var faceMat: Material
    private lateinit var pumpkinLight: PointLight
    private lateinit var treeBulbMats: List<Material>
    private lateinit var treeLight: PointLight
    private lateinit var fanBlades: Group
    var decorMode = "auto"
    // Boombox radio: OFF -> Q 97.1 Slow Jams -> ALT 104.3 -> OFF
    val RADIO_STATIONS = listOf("OFF", "Q 97.1 FM: Late-Night Slow Jams", "ALT 104.3 FM: Pop-Punk Countdown")
    var radioStation = 0; private set

    private fun star(): Geometry {
        val pts = ArrayList<FloatArray>()
        for (i in 0 until 10) { val a = (i / 10.0) * PI * 2 - PI / 2; val r = if (i % 2 == 1) 0.4 else 1.0; pts += floatArrayOf((cos(a) * r).toFloat(), (sin(a) * r).toFloat()) }
        return GeoCurves.shape(pts)
    }
    private fun heart(): Geometry {
        // two cubic Béziers: (0,-0.6) -> (0,0.45) on the left, and back on the right
        val pts = ArrayList<FloatArray>()
        fun bez(p0: FloatArray, p1: FloatArray, p2: FloatArray, p3: FloatArray) {
            for (k in 1..12) {
                val t = k / 12f; val u = 1 - t
                pts += floatArrayOf(u * u * u * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t * t * t * p3[0], u * u * u * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t * t * t * p3[1])
            }
        }
        val start = floatArrayOf(0f, -0.6f)
        pts += start
        bez(start, floatArrayOf(-1.2f, 0.2f), floatArrayOf(-0.6f, 1.1f), floatArrayOf(0f, 0.45f))
        bez(floatArrayOf(0f, 0.45f), floatArrayOf(0.6f, 1.1f), floatArrayOf(1.2f, 0.2f), start)
        pts.removeAt(pts.size - 1)
        return GeoCurves.shape(pts, 0f, 0.1f)
    }

    fun build() {
        // ---- VCR tapes ----
        tapeLabelTex = TAPES.map { tp ->
            createCanvasTex(64, 12) { ctx, w, h ->
                ctx.fillStyle = tp.label; ctx.fillRect(0, 0, w, h)
                ctx.fillStyle = tp.ink; ctx.font = "bold 8px sans-serif"; ctx.textAlign = "center"; ctx.fillText(tp.title, w / 2, 9, w - 4)
            }.tex
        }
        refreshTapeLabels()

        // ---- Answering machine on the desk (its red light blinks when there are messages) ----
        PS1.syncGrime(316)
        val machine = Group()
        machine.position.set(-0.86f, 1.495f, 0.58f); machine.rotation.y = -0.2f
        desk.deskGroup.add(machine)
        fun machinePart(geo: Geometry, mat: Material, x: Float, y: Float, z: Float): Mesh { val m = Mesh(geo, mat); m.position.set(x, y, z); m.castShadow = true; machine.add(m); room.registerInteractable(m, "machine"); return m }
        machinePart(Geo.box(0.22f, 0.05f, 0.15f), PS1.material(color = 0x2b2e36), 0f, 0.025f, 0f)
        machinePart(Geo.box(0.1f, 0.006f, 0.07f), PS1.material(color = 0x111317), -0.04f, 0.053f, 0f)
        machineLedMat = PS1.material(color = 0x4a0b0b, emissive = 0xff2222, emissiveIntensity = 0.0f)
        machinePart(Geo.box(0.018f, 0.01f, 0.018f), machineLedMat, 0.07f, 0.055f, -0.03f)
        machinePart(Geo.box(0.03f, 0.008f, 0.02f), PS1.material(color = 0x9ca3af), 0.07f, 0.054f, 0.03f)

        // ---- Glow-in-the-dark stars on the ceiling: real constellations that fade in as the room goes dark ----
        PS1.syncGrime(320)
        val starShape = star()
        ceilingStarMat = PS1.material(color = 0xc8f7dc, emissive = 0x7dffb0, emissiveIntensity = 0.1f, side = DOUBLE_SIDE)
        val CONSTELLATIONS = listOf(
            // Big Dipper over the bed
            listOf(2.2f to -0.6f, 2.55f to -0.45f, 2.85f to -0.35f, 3.15f to -0.45f, 3.25f to -0.85f, 3.65f to -0.9f, 3.6f to -0.45f),
            // Orion over the middle of the room
            listOf(0.2f to 0.6f, 0.95f to 0.75f, 0.45f to 1.2f, 0.58f to 1.24f, 0.71f to 1.28f, 0.15f to 1.8f, 1.0f to 1.75f),
            // Cassiopeia's W over the desk
            listOf(-2.2f to -1.6f, -1.85f to -1.25f, -1.5f to -1.5f, -1.15f to -1.2f, -0.8f to -1.55f)
        )
        val ceilingStars = Group(); scene.add(ceilingStars)
        val rngStars = makeRng(55)
        val all = CONSTELLATIONS.flatten() + List(14) { (rngStars() * 7 - 3.3).toFloat() to (rngStars() * 5.5 - 2.6).toFloat() }
        all.forEachIndexed { i, (x, z) ->
            val st = Mesh(starShape, ceilingStarMat)
            val sz = if (i < 19) 0.07f else (0.04 + rngStars() * 0.03).toFloat()
            st.scale.set(sz, sz, sz)
            st.rotation.set((PI / 2).toFloat(), 0f, (rngStars() * 6).toFloat())
            st.position.set(x, 4.975f, z)
            ceilingStars.add(st)
        }
        val moonSticker = Mesh(GeoCurves.ring(0.08f, 0.16f, 12, 1, 0.6f, 4.2f), ceilingStarMat)
        moonSticker.rotation.x = (PI / 2).toFloat(); moonSticker.position.set(-0.6f, 4.975f, 2.0f)
        ceilingStars.add(moonSticker)

        // ---- Space heater by the bed: glowing coils, a warm orange light ----
        PS1.syncGrime(321)
        val heater = Group()
        heater.position.set(1.2f, 0f, 1.38f); heater.rotation.y = (-PI / 2 + 0.5).toFloat()
        scene.add(heater)
        fun heaterPart(geo: Geometry, mat: Material, x: Float, y: Float, z: Float): Mesh { val m = Mesh(geo, mat); m.position.set(x, y, z); m.castShadow = true; m.receiveShadow = true; heater.add(m); room.registerInteractable(m, "heater"); return m }
        val heaterBody = PS1.material(color = 0xd8d2c4)
        heaterPart(Geo.box(0.5f, 0.42f, 0.18f, 2, 2, 1), heaterBody, 0f, 0.26f, 0f)
        heaterPart(Geo.box(0.56f, 0.05f, 0.24f), PS1.material(color = 0x4b4f58), 0f, 0.025f, 0f)
        heaterPart(Geo.box(0.42f, 0.3f, 0.02f), PS1.material(color = 0x1b1d22), 0f, 0.27f, 0.09f)
        coilMat = PS1.material(color = 0x5a2a12, emissive = 0xff5a14, emissiveIntensity = 0f)
        for (i in 0 until 4) heaterPart(Geo.cylinder(0.012f, 0.012f, 0.38f, 6), coilMat, 0f, 0.17f + i * 0.065f, 0.1f).rotation.z = (PI / 2).toFloat()
        for (i in 0 until 9) heaterPart(Geo.box(0.006f, 0.3f, 0.01f), PS1.material(color = 0x9ca3af), -0.2f + i * 0.05f, 0.27f, 0.105f)
        heaterPart(Geo.box(0.2f, 0.03f, 0.05f), PS1.material(color = 0x4b4f58), 0f, 0.5f, 0f)
        heaterLight = PointLight(0xff7a2a, 0f, 3.2f, 1.6f)
        heaterLight.position.set(1.0f, 0.35f, 1.25f); scene.add(heaterLight)

        // ---- Poster above the desk (original designs) ----
        posterObj = createCanvasTex(128, 96) { _, _, _ -> }
        val posterWallMat = PS1.material(map = posterObj.tex)
        PS1.syncGrime(335)
        val frame = List(5) { PS1.material(color = 0x111111) }
        posterWall = Mesh(Geo.box(1.2f, 0.86f, 0.03f, 4, 4, 1), listOf(frame[0], frame[1], frame[2], frame[3], posterWallMat, frame[4]))
        posterWall.position.set(-0.05f, 3.56f, -0.72f); posterWall.castShadow = true
        desk.deskGroup.add(posterWall); room.registerInteractable(posterWall, "poster")
        setPoster("none")

        // ---- Room colours: recolour the wall paint, carpet and bedsheets (keeps all the texture detail) ----
        RECOLOR = linkedMapOf(
            "walls" to Recolor(room.tex.wall, linkedMapOf("navy" to ("Midnight navy" to ""), "sage" to ("Sage green" to "hue-rotate(-105deg) saturate(0.55) brightness(1.35)"),
                "rose" to ("Dusty rose" to "hue-rotate(125deg) saturate(0.6) brightness(1.45)"), "lavender" to ("Lavender" to "hue-rotate(40deg) saturate(0.8) brightness(1.5)"),
                "terracotta" to ("Terracotta" to "hue-rotate(165deg) saturate(1.1) brightness(1.3)"), "teal" to ("Deep teal" to "hue-rotate(-45deg) brightness(1.1)"),
                "charcoal" to ("Charcoal" to "saturate(0.1) brightness(1.05)"))),
            "carpet" to Recolor(room.tex.carpet, linkedMapOf("navy" to ("Navy" to ""), "forest" to ("Forest" to "hue-rotate(-95deg) saturate(0.8)"),
                "maroon" to ("Maroon" to "hue-rotate(130deg) saturate(1.1)"), "shag" to ("Orange shag" to "hue-rotate(175deg) saturate(1.3) brightness(1.4)"),
                "grey" to ("Grey" to "saturate(0.1) brightness(1.1)"))),
            "sheets" to Recolor(bedroom.spaceBedTex, linkedMapOf("space" to ("Outer space" to ""), "sunset" to ("Sunset" to "hue-rotate(140deg) saturate(1.2)"),
                "mint" to ("Mint" to "hue-rotate(-80deg) brightness(1.1)"), "grape" to ("Grape soda" to "hue-rotate(55deg) saturate(1.3)"),
                "mono" to ("Black & white" to "saturate(0) brightness(1.1)")))
        )
        for (r in RECOLOR.values) r.original = r.tex.bitmap.copy(Bitmap.Config.ARGB_8888, false)

        // ---- Seasonal decorations that come out on their own by date (or pick one) ----
        PS1.syncGrime(340)
        for (g in decor.values) { g.visible = false; scene.add(g) }
        // Halloween: a carved pumpkin on the sill and paper bats on the wall
        val pumpkin = Mesh(Geo.sphere(0.13f, 10, 8), PS1.material(color = 0xea6a12))
        pumpkin.scale.set(1.15f, 0.85f, 1f); pumpkin.position.set(1.5f, 1.68f, -2.86f); decor.getValue("halloween").add(pumpkin)
        val stem = Mesh(Geo.cylinder(0.015f, 0.02f, 0.06f, 5), PS1.material(color = 0x3f6212))
        stem.position.set(1.5f, 1.8f, -2.86f); decor.getValue("halloween").add(stem)
        faceMat = PS1.material(color = 0xffb020, emissive = 0xff9a1a, emissiveIntensity = 1.0f)
        for ((x, y) in listOf(-0.045f to 0.03f, 0.045f to 0.03f)) { val e = Mesh(Geo.cone(0.025f, 0.04f, 3), faceMat); e.position.set(1.5f + x, 1.68f + y, -2.86f + 0.13f); e.rotation.x = (PI / 2).toFloat(); decor.getValue("halloween").add(e) }
        val mouth = Mesh(Geo.box(0.1f, 0.02f, 0.01f), faceMat); mouth.position.set(1.5f, 1.64f, -2.735f); decor.getValue("halloween").add(mouth)
        val batTex = createCanvasTex(32, 16) { ctx, _, _ ->
            ctx.fillStyle = "#0b0b0f"
            ctx.beginPath(); ctx.moveTo(16, 6); ctx.lineTo(4, 2); ctx.lineTo(7, 7); ctx.lineTo(0, 8); ctx.lineTo(8, 11); ctx.lineTo(16, 10); ctx.lineTo(24, 11); ctx.lineTo(32, 8); ctx.lineTo(25, 7); ctx.lineTo(28, 2); ctx.closePath(); ctx.fill()
            ctx.beginPath(); ctx.arc(16, 7, 3, 0, PI * 2); ctx.fill()
        }.tex
        val batMat = PS1.basic(map = batTex, transparent = true, side = DOUBLE_SIDE)
        for (b in arrayOf(floatArrayOf(4.35f, 3.9f, -1.6f, 0.2f), floatArrayOf(4.35f, 4.25f, -1.1f, -0.15f), floatArrayOf(4.35f, 3.6f, -0.7f, 0.1f), floatArrayOf(-3.71f, 3.9f, 1.3f, 0.25f))) {
            val bat = Mesh(Geo.plane(0.34f, 0.17f), batMat)
            bat.position.set(b[0], b[1], b[2]); bat.rotation.set(0f, if (b[0] > 0) (-PI / 2).toFloat() else (PI / 2).toFloat(), b[3])
            decor.getValue("halloween").add(bat)
        }
        pumpkinLight = PointLight(0xff8a20, 0.0f, 1.6f, 2f); pumpkinLight.position.set(1.5f, 1.7f, -2.6f); decor.getValue("halloween").add(pumpkinLight)
        // Winter holidays: a little tree with blinking lights and a star, snowflake window clings
        val tree = Group(); tree.position.set(3.75f, 0f, 3.0f); decor.getValue("winter").add(tree)
        for (c in arrayOf(floatArrayOf(0.42f, 0.55f, 0.35f), floatArrayOf(0.34f, 0.5f, 0.7f), floatArrayOf(0.24f, 0.45f, 1.0f))) {
            val cone = Mesh(Geo.cone(c[0], c[1], 8), PS1.material(color = 0x166534)); cone.position.y = c[2]; cone.castShadow = true; tree.add(cone)
        }
        val trunk = Mesh(Geo.cylinder(0.05f, 0.06f, 0.2f, 6), PS1.material(color = 0x5b3a1a)); trunk.position.y = 0.1f; tree.add(trunk)
        treeBulbMats = intArrayOf(0xff3b3b, 0xffd23f, 0x3b82f6, 0x22c55e).map { PS1.material(color = it, emissive = it, emissiveIntensity = 1f) }
        for (i in 0 until 22) {
            val a = i * 2.4f; val y = 0.25f + (i / 22f) * 0.95f; val r = 0.4f * (1 - (y - 0.15f) / 1.2f) + 0.03f
            val b = Mesh(Geo.sphere(0.022f, 5, 4), treeBulbMats[i % 4]); b.position.set(cos(a) * r, y, sin(a) * r); tree.add(b)
        }
        val treeStar = Mesh(starShape, PS1.material(color = 0xffe066, emissive = 0xffd23f, emissiveIntensity = 1f, side = DOUBLE_SIDE))
        treeStar.scale.set(0.09f, 0.09f, 0.09f); treeStar.position.y = 1.3f; tree.add(treeStar)
        treeLight = PointLight(0xffc070, 0.0f, 2.5f, 2f); treeLight.position.set(3.6f, 0.9f, 2.8f); decor.getValue("winter").add(treeLight)
        val flakeMat = PS1.basic(color = 0xffffff, transparent = true, opacity = 0.85f, side = DOUBLE_SIDE)
        for ((x, y) in listOf(0.0f to 3.3f, 0.45f to 2.2f, 1.25f to 3.4f, 1.85f to 2.4f, -0.35f to 2.0f)) {
            val f = Mesh(starShape, flakeMat); f.scale.set(0.08f, 0.08f, 0.08f); f.position.set(x, y, -3.1f); decor.getValue("winter").add(f)
        }
        // Valentine's: a garland of hearts along the top of the window and a card on the desk
        val heartShape = heart()
        val heartMats = intArrayOf(0xff4d6d, 0xff8fab, 0xffffff).map { PS1.material(color = it, side = DOUBLE_SIDE) }
        for (i in 0 until 10) {
            val hrt = Mesh(heartShape, heartMats[i % 3]); hrt.scale.set(0.07f, 0.07f, 0.07f)
            hrt.position.set(-0.45f + i * 0.28f, 3.6f - sin((i / 9.0) * PI).toFloat() * 0.14f, -2.9f); decor.getValue("valentine").add(hrt)
        }
        val card = Mesh(Geo.box(0.14f, 0.18f, 0.01f), PS1.material(color = 0xff8fab))
        card.position.set(-1.0f, 1.58f, -1.25f); card.rotation.set(-0.15f, 0.5f, 0f); decor.getValue("valentine").add(card)
        // Summer: a box fan on the floor, blades spinning
        val boxFan = Group(); boxFan.position.set(-0.9f, 0f, 2.6f); boxFan.rotation.y = (PI * 0.9).toFloat(); decor.getValue("summer").add(boxFan)
        val fanFrame = Mesh(Geo.box(0.55f, 0.55f, 0.12f), PS1.material(color = 0xe5e7eb)); fanFrame.position.y = 0.3f; boxFan.add(fanFrame)
        val grille = Mesh(Geo.circle(0.24f, 16), PS1.material(color = 0x374151)); grille.position.set(0f, 0.3f, 0.062f); boxFan.add(grille)
        fanBlades = Group(); fanBlades.position.set(0f, 0.3f, 0.07f); boxFan.add(fanBlades)
        for (i in 0 until 3) { val bl = Mesh(Geo.box(0.06f, 0.2f, 0.005f), PS1.material(color = 0x93c5fd)); bl.position.y = 0.1f; val pv = Group(); pv.rotation.z = i * 2.094f; pv.add(bl); fanBlades.add(pv) }
        applyDecor()
    }
    /** Everything here that moves or switches on and off stays out of static batching. */
    fun dynamicRoots() = decor.values.toList() + fanBlades

    // ---- Tapes ----
    private fun refreshTapeLabels() { tv.vhsLabelMats.forEachIndexed { i, m -> m.map = tapeLabelTex[shelfTapes[i]] } }
    fun insertTape(slotIndex: Int, t: Float) {
        val incoming = shelfTapes[slotIndex]
        shelfTapes[slotIndex] = currentTape
        currentTape = incoming
        refreshTapeLabels()
        if (!tv.bedroomTvOn) tv.toggleBedroomTv(t)
        tv.setChannel(2)
        tapeStartedAt = t
    }
    /** What each tape shows (drawn at 128x96 into the 2x TV canvas); returns false for the original tape (the TV draws it). */
    fun drawTapeVideo(ctx: Canvas2D, w: Int, h: Int, t: Float): Boolean {
        if (currentTape == 0) return false
        val e = t - tapeStartedAt
        if (currentTape == 1) {
            // Taped off TV: a reel of late-90s commercials
            when (floor(e / 6.0).toInt() % 4) {
                0 -> {
                    val g = ctx.createLinearGradient(0, 0, 0, h); g.addColorStop(0, "#fde047"); g.addColorStop(1, "#f97316")
                    ctx.fillStyle = g; ctx.fillRect(0, 0, w, h)
                    ctx.fillStyle = "#ffffff"; ctx.beginPath(); ctx.ellipse(64, 72, 30, 12, 0, 0, PI * 2); ctx.fill()
                    ctx.fillStyle = "#b45309"; for (i in 0 until 12) { ctx.beginPath(); ctx.arc(44 + (i % 6) * 8, 66 + (i / 6) * 5, 3, 0, PI * 2); ctx.fill() }
                    val hop = abs(sin(e * 4)) * 8
                    ctx.fillStyle = "#7c3aed"; ctx.beginPath(); ctx.arc(98, 38 - hop, 10, 0, PI * 2); ctx.fill()
                    ctx.fillStyle = "#fff"; ctx.fillRect(93, 34 - hop, 4, 4); ctx.fillRect(100, 34 - hop, 4, 4)
                    ctx.fillStyle = "#b91c1c"; ctx.font = "bold 14px Impact, sans-serif"; ctx.fillText("CRUNCHY-O'S", 8, 22)
                    ctx.fillStyle = "#7c2d12"; ctx.font = "bold 7px sans-serif"; ctx.fillText("PART OF A COMPLETE BREAKFAST", 8, 32)
                }
                1 -> {
                    ctx.fillStyle = "#22d3ee"; ctx.fillRect(0, 0, w, h)
                    ctx.fillStyle = "#4ade80"; ctx.fillRect(0, 70, w, 26)
                    ctx.fillStyle = "#f97316"; ctx.fillRect(28, 44, 30, 8); ctx.fillStyle = "#facc15"; ctx.fillRect(50, 38, 10, 8)
                    ctx.strokeStyle = "#e0f2fe"; ctx.lineWidth = 2f
                    for (i in 0 until 6) { val k = (e * 3 + i * 0.3f) % 2; ctx.beginPath(); ctx.moveTo(58, 48); ctx.quadraticCurveTo(80 + k * 10, 30 - k * 6, 96 + k * 20, 48 + k * 18); ctx.stroke() }
                    ctx.fillStyle = "#1e3a8a"; ctx.font = "bold 12px Impact, sans-serif"; ctx.fillText("AQUA BLASTER 3000", 8, 16)
                    ctx.fillStyle = "#fff"; ctx.font = "bold 7px sans-serif"; ctx.fillText("BATTERIES NOT INCLUDED", 8, 92)
                }
                2 -> {
                    ctx.fillStyle = "#1d4ed8"; ctx.fillRect(0, 0, w, h)
                    ctx.fillStyle = "#fde047"; ctx.font = "bold 13px Impact, sans-serif"; ctx.textAlign = "center"; ctx.fillText("CALL NOW!", 64, 30)
                    ctx.fillStyle = "#fff"; ctx.font = "bold 11px monospace"; ctx.fillText("1-800-555-DIME", 64, 52)
                    ctx.font = "bold 6px sans-serif"; ctx.fillText("SAVE ON LONG DISTANCE · OPERATORS STANDING BY", 64, 70)
                    ctx.textAlign = "left"
                }
                else -> {
                    ctx.fillStyle = "#0f172a"; ctx.fillRect(0, 0, w, h)
                    val sweep = (e * 40) % 160 - 20
                    ctx.fillStyle = "#b91c1c"; ctx.fillRect(sweep - 60, 36, 60, 22)
                    ctx.fillStyle = "#fff"; ctx.font = "bold 10px Impact, sans-serif"; ctx.fillText("EYEWITNESS 4", 14, 50)
                    ctx.fillStyle = "#93c5fd"; ctx.font = "bold 7px sans-serif"; ctx.fillText("NEWS AT 11", 14, 64)
                }
            }
        } else if (currentTape == 2) {
            // Kung-fu movie: two fighters against a sunset, badly dubbed subtitles
            val g = ctx.createLinearGradient(0, 0, 0, h); g.addColorStop(0, "#7c2d12"); g.addColorStop(0.6, "#f97316"); g.addColorStop(1, "#451a03")
            ctx.fillStyle = g; ctx.fillRect(0, 0, w, h)
            ctx.fillStyle = "#fde68a"; ctx.beginPath(); ctx.arc(64, 44, 16, 0, PI * 2); ctx.fill()
            ctx.fillStyle = "#1c0a00"
            for (i in 0 until 6) ctx.fillRect(4 + i * 22, 20 + (i % 2) * 8, 2, 76)
            ctx.fillRect(0, 76, w, 20)
            val clash = sin(e * 5)
            Games.figure(ctx, (50 + clash * 6).toDouble(), 76.0, 0.75, "#0b0b0f", if (clash > 0.3f) 1 else 3)
            Games.figure(ctx, (82 - clash * 6).toDouble(), 76.0, 0.75, "#0b0b0f", if (clash < -0.3f) 1 else 2)
            val subs = arrayOf("YOUR KUNG FU IS WEAK!", "YOU KILLED MY MASTER...", "NOW... THE FINAL LESSON.", "HIIIYAAA!")
            ctx.fillStyle = "rgba(0,0,0,0.6)"; ctx.fillRect(8, 82, 112, 11)
            ctx.fillStyle = "#facc15"; ctx.font = "bold 7px monospace"; ctx.textAlign = "center"
            ctx.fillText(subs[floor(e / 3.0).toInt() % subs.size], 64, 90)
            ctx.textAlign = "left"
        } else {
            // Ocean relaxation tape: light rays, drifting fish, jellyfish, bubbles
            val g = ctx.createLinearGradient(0, 0, 0, h); g.addColorStop(0, "#0ea5e9"); g.addColorStop(1, "#082f49")
            ctx.fillStyle = g; ctx.fillRect(0, 0, w, h)
            ctx.fillStyle = "rgba(224, 242, 254, 0.12)"
            for (i in 0 until 4) { val x = 20 + i * 30 + sin(e * 0.4f + i) * 8; ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x + 10, 0); ctx.lineTo(x + 30, h); ctx.lineTo(x + 6, h); ctx.fill() }
            arrayOf("#facc15", "#fb923c", "#f472b6", "#a3e635").forEachIndexed { i, c ->
                val x = ((e * (8 + i * 3) + i * 37) % 160) - 16; val y = 20 + i * 16 + sin(e + i) * 4
                ctx.fillStyle = c; ctx.beginPath(); ctx.ellipse(x, y, 6, 3, 0, 0, PI * 2); ctx.fill()
                ctx.beginPath(); ctx.moveTo(x - 5, y); ctx.lineTo(x - 10, y - 3); ctx.lineTo(x - 10, y + 3); ctx.fill()
            }
            ctx.fillStyle = "rgba(244, 196, 255, 0.55)"
            val jy = 50 + sin(e * 0.6f) * 12
            ctx.beginPath(); ctx.arc(96, jy, 8, PI, 0); ctx.fill()
            for (k in 0 until 4) ctx.fillRect(90 + k * 4, jy, 1, 10 + sin(e * 2 + k) * 3)
            ctx.fillStyle = "rgba(255,255,255,0.6)"
            for (i in 0 until 8) { val by = h - ((e * 12 + i * 13) % h); ctx.beginPath(); ctx.arc(30 + (i * 11) % 70, by, 1.2, 0, PI * 2); ctx.fill() }
            ctx.fillStyle = "#e0f2fe"; ctx.font = "italic 6px Georgia, serif"; ctx.fillText("Ocean Dreams · Relaxation Video Vol. 2", 6, 92)
        }
        // VHS overlay: PLAY, counter, a tracking band that wanders
        ctx.fillStyle = "#ffffff"; ctx.font = "bold 8px monospace"
        if (e < 4) ctx.fillText("PLAY ►", 8, 12)
        val secs = floor(e.toDouble()).toInt() + 3725
        ctx.fillText("SP ${secs / 3600}:${((secs / 60) % 60).toString().padStart(2, '0')}:${(secs % 60).toString().padStart(2, '0')}", 76, 12)
        val trackY = (t * 23) % (h + 40) - 20
        ctx.fillStyle = "rgba(255,255,255,0.18)"; ctx.fillRect(0, trackY, w, 3)
        return true
    }

    // ---- Glow stars, heater, decorations (per frame) ----
    fun update(t: Float, dt: Float, fanLightOn: Boolean, lampOn: Boolean, stringLightsOn: Boolean) {
        machineLedMat.emissiveIntensity = if (messages.isNotEmpty() && sin(t * 6) > 0) 1.2f else 0.0f
        // Lights charge the stars; in the dark they glow and slowly fade over ~20 minutes
        val lit = maxOf(if (fanLightOn) 1f else 0f, if (lampOn) 0.7f else 0f, if (stringLightsOn) 0.35f else 0f, env.daylight.toFloat())
        starCharge = if (lit > 0.6f) min(1f, starCharge + dt * 0.05f) else max(0.25f, starCharge - dt / 1200)
        val glow = (1 - lit) * (0.3f + 0.7f * starCharge)
        ceilingStarMat.emissiveIntensity += (glow * 1.1f - ceilingStarMat.emissiveIntensity) * min(1f, dt * 0.8f)
        tv.glowStarMat.emissiveIntensity = 0.1f + ceilingStarMat.emissiveIntensity * 0.7f
        // Heater coils take a while to heat up and cool down
        heaterOn = heaterShouldBeOn()
        heaterGlow += ((if (heaterOn) 1f else 0f) - heaterGlow) * min(1f, dt * 0.35f)
        coilMat.emissiveIntensity = heaterGlow * (1.1f + sin(t * 2.3f) * 0.06f)
        heaterLight.intensity = heaterGlow * (0.85f + sin(t * 1.7f) * 0.05f)
        // Seasonal decorations
        val night = (1 - env.daylight).toFloat()
        if (decor.getValue("halloween").visible) { faceMat.emissiveIntensity = 0.8f + sin(t * 9) * 0.12f + (if (rnd.nextDouble() < 0.05) -0.3f else 0f); pumpkinLight.intensity = 0.25f + 0.5f * night }
        if (decor.getValue("winter").visible) { treeBulbMats.forEachIndexed { i, m -> m.emissiveIntensity = if (sin(t * 1.5f + i * 1.6f) > -0.2f) 1.1f else 0.2f }; treeLight.intensity = 0.2f + 0.5f * night }
        if (decor.getValue("summer").visible) fanBlades.rotation.z -= dt * 14
    }
    private fun heaterShouldBeOn() = when (heaterMode) { "on" -> true; "off" -> false; else -> env.currentSeason == "winter" && (env.daylight < 0.4 || env.isSnowingNow()) }
    fun cycleHeater() { heaterMode = mapOf("auto" to "on", "on" to "off", "off" to "auto").getValue(heaterMode) }

    // ---- Poster ----
    private val POSTERS: Map<String, ((Canvas2D, Int, Int) -> Unit)?> = linkedMapOf(
        "none" to null,
        "band" to { ctx, w, h ->
            val g = ctx.createLinearGradient(0, 0, 0, h); g.addColorStop(0, "#0b0b12"); g.addColorStop(1, "#5b0f2e")
            ctx.fillStyle = g; ctx.fillRect(0, 0, w, h)
            ctx.fillStyle = "#ff3b6b"; ctx.font = "bold 22px Impact, sans-serif"; ctx.textAlign = "center"; ctx.fillText("NEON HEARTBREAK", w / 2, 26)
            ctx.fillStyle = "#000"; intArrayOf(30, 64, 98).forEachIndexed { i, x -> ctx.beginPath(); ctx.arc(x, 52 + (i % 2) * 4, 7, 0, PI * 2); ctx.fill(); ctx.fillRect(x - 7, 58 + (i % 2) * 4, 14, 30) }
            ctx.fillStyle = "#ffd1dc"; ctx.font = "bold 7px sans-serif"; ctx.fillText("THE \"SOMEWHERE IN 2001\" TOUR", w / 2, h - 6)
        },
        "galaxy" to { ctx, w, h ->
            ctx.fillStyle = "#020617"; ctx.fillRect(0, 0, w, h)
            for (i in 0 until 70) { ctx.fillStyle = "rgba(255,255,255,${0.3 + (i % 5) * 0.14})"; ctx.fillRect((i * 37) % w, (i * 23 + i * i) % h, 1, 1) }
            ctx.fillStyle = "#7c3aed"; ctx.beginPath(); ctx.arc(96, 30, 16, 0, PI * 2); ctx.fill()
            ctx.strokeStyle = "#c4b5fd"; ctx.lineWidth = 2f; ctx.beginPath(); ctx.ellipse(96, 30, 26, 6, -0.3, 0, PI * 2); ctx.stroke()
            ctx.fillStyle = "#e5e7eb"; ctx.beginPath(); ctx.moveTo(24, 72); ctx.lineTo(54, 60); ctx.lineTo(30, 80); ctx.fill()
            ctx.fillStyle = "#f97316"; ctx.fillRect(18, 74, 8, 3)
            ctx.fillStyle = "#facc15"; ctx.font = "bold 15px Impact, sans-serif"; ctx.textAlign = "center"; ctx.fillText("GALAXY RACERS", w / 2, h - 8)
        },
        "vapor" to { ctx, w, h ->
            val g = ctx.createLinearGradient(0, 0, 0, h); g.addColorStop(0, "#2a0b55"); g.addColorStop(0.55, "#ff5fa2"); g.addColorStop(0.56, "#14082b"); g.addColorStop(1, "#14082b")
            ctx.fillStyle = g; ctx.fillRect(0, 0, w, h)
            ctx.fillStyle = "#ffd56b"; ctx.beginPath(); ctx.arc(w / 2, h * 0.55, 22, PI, 0); ctx.fill()
            ctx.strokeStyle = "#ff5fa2"; ctx.lineWidth = 1f
            for (i in 0 until 6) { val y = h * 0.56 + i * i * 1.6; ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(w, y); ctx.stroke() }
            for (i in -6..6) { ctx.beginPath(); ctx.moveTo(w / 2, h * 0.56); ctx.lineTo(w / 2 + i * 22, h); ctx.stroke() }
            ctx.fillStyle = "#7ff3ff"; ctx.font = "italic bold 12px Georgia, serif"; ctx.textAlign = "center"; ctx.fillText("ｖａｐｏｒ ｄｒｅａｍｓ", w / 2, 16)
        },
        "skate" to { ctx, w, h ->
            ctx.fillStyle = "#facc15"; ctx.fillRect(0, 0, w, h)
            ctx.fillStyle = "#111"; ctx.beginPath(); ctx.moveTo(10, 86); ctx.quadraticCurveTo(64, 40, 118, 86); ctx.lineTo(118, 96); ctx.lineTo(10, 96); ctx.fill()
            Games.skaterSilhouette(ctx, 82.0, 40.0, 1.4, "#dc2626")
            ctx.fillStyle = "#111"; ctx.font = "bold 20px Impact, sans-serif"; ctx.textAlign = "left"; ctx.fillText("SKATE", 6, 22); ctx.fillText("OR DIE", 6, 42)
            ctx.font = "bold 7px sans-serif"; ctx.fillText("SUMMER JAM 2001", 6, 54)
        },
        "kitten" to { ctx, w, h ->
            ctx.fillStyle = "#7dd3fc"; ctx.fillRect(0, 0, w, h)
            ctx.fillStyle = "#4ade80"; ctx.fillRect(0, 50, w, 6)
            ctx.fillStyle = "#9ca3af"; ctx.beginPath(); ctx.arc(64, 44, 12, 0, PI * 2); ctx.fill()
            ctx.fillRect(56, 56, 16, 26)
            ctx.fillStyle = "#fff"; ctx.fillRect(58, 40, 3, 3); ctx.fillRect(67, 40, 3, 3)
            ctx.fillStyle = "#92400e"; ctx.fillRect(0, 46, w, 3)
            ctx.fillStyle = "#1e3a8a"; ctx.font = "bold 14px Impact, sans-serif"; ctx.textAlign = "center"; ctx.fillText("HANG IN THERE", w / 2, 20)
            ctx.font = "bold 8px sans-serif"; ctx.fillText("BABY", w / 2, 92)
        }
    )
    val POSTER_NAMES = linkedMapOf("none" to "No poster", "band" to "Band tour", "galaxy" to "Galaxy Racers", "vapor" to "Vapor Dreams", "skate" to "Skate or Die", "kitten" to "Hang in there")
    fun setPoster(key: String) {
        posterChoice = if (POSTERS.containsKey(key)) key else "none"
        posterWall.visible = posterChoice != "none"
        POSTERS[posterChoice]?.let { draw ->
            val ctx = posterObj.ctx
            ctx.save(); draw(ctx, 128, 96); ctx.restore(); ctx.textAlign = "left"
            weatherCanvas(ctx, 128, 96, Weather(seed = posterChoice.length * 7.0, blotch = 0.15, creases = 3, edge = 0.2, warm = 0.15))
            posterObj.tex.needsUpdate()
        }
        room.onShadowsChanged.invoke()
    }
    fun cyclePoster() {
        val keys = POSTER_NAMES.keys.filter { it != "none" }
        setPoster(keys[(keys.indexOf(posterChoice) + 1) % keys.size])
    }

    // ---- Room colours ----
    fun setRoomColor(part: String, key0: String) {
        val r = RECOLOR.getValue(part)
        val key = if (r.opts.containsKey(key0)) key0 else r.opts.keys.first()
        roomColors[part] = key
        val w = r.original.width; val h = r.original.height
        val ctx = Canvas2D(w, h)
        ctx.filter = r.opts.getValue(key).second.ifEmpty { "none" }
        ctx.drawImage(r.original, 0, 0, w, h)
        val out = android.graphics.Canvas(r.tex.bitmap)
        out.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR)
        out.drawBitmap(ctx.bitmap, 0f, 0f, null)
        r.tex.needsUpdate()
    }

    // ---- Decorations ----
    fun activeDecor(): String {
        if (decorMode != "auto") return decorMode
        val d = Calendar.getInstance(); val m = d.get(Calendar.MONTH); val day = d.get(Calendar.DAY_OF_MONTH)
        if (m == 9) return "halloween"
        if (m == 11 || (m == 0 && day <= 6)) return "winter"
        if (m == 1 && day <= 14) return "valentine"
        if ((m == 5 && day >= 15) || m == 6 || m == 7) return "summer"
        return "off"
    }
    fun applyDecor() {
        val a = activeDecor()
        for ((k, g) in decor) g.visible = a == k
        room.onShadowsChanged.invoke()
    }

    // ---- Boombox radio (the stations' sound comes with the audio phase) ----
    fun cycleRadio(radioDisplayMat: Material) {
        radioStation = (radioStation + 1) % RADIO_STATIONS.size
        radioDisplayMat.emissiveIntensity = if (radioStation > 0) 0.9f else 0.05f
    }
}
