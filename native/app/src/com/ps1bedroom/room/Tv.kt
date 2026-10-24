package com.ps1bedroom.room

import com.ps1bedroom.room.engine.Canvas2D
import com.ps1bedroom.room.engine.CanvasTex
import com.ps1bedroom.room.engine.Geo
import com.ps1bedroom.room.engine.Group
import com.ps1bedroom.room.engine.Material
import com.ps1bedroom.room.engine.Mesh
import com.ps1bedroom.room.engine.PS1
import com.ps1bedroom.room.engine.Weather
import com.ps1bedroom.room.engine.createCanvasTex
import java.util.Calendar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * BEDROOM CRT TELEVISION & VCR MEDIA CART (HTML lines 2785-3490): the rolling walnut cart, VCR with its 12:00 clock,
 * VHS tapes and game cases, the 20" CRT with domed glass, control strip, A/V jacks and rabbit ears, and every
 * broadcast channel drawn live on the screen. Also the rest of that HTML section: the back and right baseboards,
 * the wall outlet and the glow-in-the-dark stars.
 */
class Tv(private val room: Room, private val env: Environment) {
    companion object {
        val TV_CHANNEL_NAMES = arrayOf(
            "CH 04: Late-Night 90s Anime Broadcast", "CH 12: The Weather Channel (Local on the 8s)", "CH 03: VCR (whatever tape is in the deck)",
            "CH 24: Midnight Retro Shopping Network", "CH 99: Snowy CRT No-Signal Static", "CH 06: Saturday-Morning Cartoons",
            "CH 32: Late-Night Music Video Countdown", "AV 1: PlayStation Video Input"
        )
        val TV_CHANNEL_COLORS = intArrayOf(0xec4899, 0x38bdf8, 0x22c55e, 0xf59e0b, 0x94a3b8, 0x7dd3fc, 0xc084fc, 0xfb923c)
        val PS1_TV_CHANNEL = TV_CHANNEL_NAMES.size - 1 // the A/V input is always the last channel
    }
    var bedroomTvOn = true; private set
    /** True while YouTube is pinned over the CRT glass so the underlying channel canvas stays dark. */
    var ytShowing = false
    /** Fired whenever the TV turns on or off from any source (power button, channel wrap-around, menu, etc.). */
    var onPowerChanged: ((Boolean) -> Unit)? = null
    /** The weather channel's temperature, in the player's °F / °C (set by the app). */
    var tempText: () -> String = { "--" }
    var cityText: () -> String = { "LOCAL AREA" }
    var rawTempC: () -> Double = { 20.0 }
    var tempUnit: () -> String = { "F" }
    /** Weather view mode: "simple" (high legibility from room view) vs "detailed" (WeatherStar 4000). */
    var weatherView: () -> String = { "simple" }
    /** The picture (YouTube is pinned over it). */
    lateinit var screenMesh: Mesh
    var bedroomTvChannel = 0; private set
    /** The PlayStation is on (it adds A/V 1 to the rotation): comes with the console phase. */
    var ps1On = false
    /** The console (it draws its own picture on A/V 1). */
    var ps1: Ps1? = null
    /** The VCR tapes (Extras): channel 2 plays whichever is in the deck. */
    var tapes: Extras? = null
    val vhsLabelMats = ArrayList<Material>()
    /** Switch the channel directly (the PlayStation puts the TV on A/V 1 and back). */
    /** The GTA chase-cam feed replaces the drawn picture while a 3D-era game is playing (null = back to the canvas). */
    fun useFeed(tex: com.ps1bedroom.room.engine.Texture?) {
        val t = tex ?: tvCanvasObj.tex
        if (tvScreenMat.map !== t) { tvScreenMat.map = t; tvScreenMat.emissiveMap = t }
    }
    fun setChannel(ch: Int) { bedroomTvChannel = ch; room.bedroomTvLight.color.setHex(TV_CHANNEL_COLORS[ch]) }
    val tvStandGroup = Group()
    private lateinit var tvCanvasObj: CanvasTex
    private lateinit var tvScreenMat: Material
    private lateinit var tvLedMat: Material
    lateinit var glowStarMat: Material
    private var tvLastChannel = -1
    private var tvChannelChangedAt = -10f
    private val rnd = Random.Default

    fun build() {
        val scene = room.scene
        // Beside the PS1 (and clear of the door swing), angled toward the bed and chair
        tvStandGroup.position.set(-2.35f, 0f, 0.75f)
        tvStandGroup.rotation.y = 1.75f
        scene.add(tvStandGroup)
        tvCanvasObj = createCanvasTex(256, 192) { _, _, _ -> } // drawn at 2x for crisp shapes and text

        // ---- Rolling walnut-laminate media cart: three shelves, back panel, edge banding, casters ----
        val cartLaminateTex = createCanvasTex(64, 64, 2f, 2f, Weather(seed = 53.0, blotch = 0.22, scratches = 12, speckle = 0.05)) { ctx, w, h ->
            ctx.fillStyle = "#2b1d16"; ctx.fillRect(0, 0, w, h)
            for (y in 0 until h) {
                val v = sin(y * 0.45 + sin(y * 0.11) * 3) * 10
                ctx.fillStyle = "rgba(${(70 + v).toInt()}, ${(46 + v * 0.7).toInt()}, ${(32 + v * 0.5).toInt()}, 0.55)"
                ctx.fillRect(0, y, w, 1)
            }
        }.tex
        PS1.syncGrime(19)
        val matTvCabinet = PS1.material(map = cartLaminateTex)
        val matCartEdge = PS1.material(color = 0x4a3426)
        fun addCartPart(w: Float, h: Float, d: Float, x: Float, y: Float, z: Float, mat: Material = matTvCabinet): Mesh {
            val m = Mesh(Geo.box(w, h, d, 2, 1, 2), mat)
            m.position.set(x, y, z); m.castShadow = true; m.receiveShadow = true
            tvStandGroup.add(m); return m
        }
        addCartPart(1.06f, 0.05f, 0.66f, 0f, 0.865f, 0f)                    // top (the TV sits at Y = 0.89)
        addCartPart(1.07f, 0.02f, 0.02f, 0f, 0.865f, 0.335f, matCartEdge)   // front edge banding
        addCartPart(0.96f, 0.04f, 0.60f, 0f, 0.50f, 0.01f)                  // VCR shelf
        addCartPart(1.00f, 0.05f, 0.62f, 0f, 0.13f, 0f)                     // bottom shelf
        addCartPart(1.01f, 0.02f, 0.02f, 0f, 0.13f, 0.315f, matCartEdge)
        for (sx in floatArrayOf(-0.49f, 0.49f)) addCartPart(0.05f, 0.80f, 0.64f, sx, 0.49f, 0f) // sides
        addCartPart(0.94f, 0.72f, 0.02f, 0f, 0.49f, -0.31f)                 // back panel
        for (c in arrayOf(floatArrayOf(-0.45f, 0.25f), floatArrayOf(0.45f, 0.25f), floatArrayOf(-0.45f, -0.25f), floatArrayOf(0.45f, -0.25f))) {
            val stem = Mesh(Geo.cylinder(0.015f, 0.015f, 0.04f, 6), room.matDarkPlastic)
            stem.position.set(c[0], 0.085f, c[1]); tvStandGroup.add(stem)
            val wheel = Mesh(Geo.cylinder(0.035f, 0.035f, 0.03f, 10), room.matDarkPlastic)
            wheel.rotation.z = (PI / 2).toFloat(); wheel.position.set(c[0], 0.035f, c[1] + 0.015f); wheel.castShadow = true
            tvStandGroup.add(wheel)
        }
        // VCR deck on the middle shelf: tape door, transport buttons, glowing "12:00" clock
        val vcrDeck = Mesh(Geo.box(0.68f, 0.12f, 0.44f, 2, 1, 2), room.matDarkPlastic)
        vcrDeck.position.set(0f, 0.58f, 0.04f); vcrDeck.castShadow = true; vcrDeck.receiveShadow = true
        tvStandGroup.add(vcrDeck); room.registerInteractable(vcrDeck, "tv")
        val vcrDoor = Mesh(Geo.box(0.30f, 0.035f, 0.008f), PS1.material(color = 0x0b0d12))
        vcrDoor.position.set(-0.13f, 0.595f, 0.262f); tvStandGroup.add(vcrDoor)
        val matVcrButton = PS1.material(color = 0x475569)
        for (i in 0 until 5) { val vb = Mesh(Geo.box(0.026f, 0.012f, 0.008f), matVcrButton); vb.position.set(0.07f + i * 0.034f, 0.553f, 0.262f); tvStandGroup.add(vb) }
        val vcrClock = Mesh(Geo.plane(0.12f, 0.035f), PS1.material(color = 0x4ade80, emissive = 0x22c55e, emissiveIntensity = 0.9f, polygonOffset = true, polygonOffsetFactor = -2f, polygonOffsetUnits = -2f))
        vcrClock.position.set(0.18f, 0.605f, 0.261f); tvStandGroup.add(vcrClock)
        // VHS tapes and PlayStation game cases on the bottom shelf (the tape labels get their art with the VCR tapes)
        for (i in 0 until 3) {
            val tape = Mesh(Geo.box(0.2f, 0.026f, 0.11f), room.matDarkPlastic)
            room.registerInteractable(tape, "vhs:$i")
            tape.position.set(-0.25f, 0.168f + i * 0.027f, 0.08f); tape.rotation.y = (i - 1) * 0.06f; tape.castShadow = true
            tvStandGroup.add(tape)
            val labelMat = PS1.material(color = 0xffffff, map = PS1.grimeTextures[0]); vhsLabelMats += labelMat
            val label = Mesh(Geo.box(0.16f, 0.018f, 0.002f), labelMat)
            label.position.set(0f, 0f, 0.056f)
            tape.add(label); room.registerInteractable(label, "vhs:$i")
        }
        intArrayOf(0xf97316, 0x7c3aed, 0x16a34a).forEachIndexed { i, spineCol ->
            val gameCase = Mesh(Geo.box(0.012f, 0.13f, 0.15f), PS1.material(color = 0x0b0d12))
            gameCase.position.set(0.14f + i * 0.022f, 0.22f, -0.02f); gameCase.castShadow = true
            tvStandGroup.add(gameCase)
            val spine = Mesh(Geo.box(0.013f, 0.1f, 0.01f), PS1.material(color = spineCol))
            spine.position.set(0f, 0f, 0.072f); gameCase.add(spine)
        }

        // ---- 20" CRT: chunky front bezel, square-tapered tube housing, domed glass, speaker grille, controls, A/V jacks ----
        val matCrtShell = PS1.material(color = 0x2a2f3d)
        val matCrtDark = PS1.material(color = 0x14171f)
        val crtBody = Mesh(Geo.box(0.96f, 0.82f, 0.12f, 4, 4, 1), matCrtShell) // front bezel, face at Z = 0.34
        crtBody.position.set(0f, 1.30f, 0.28f); crtBody.castShadow = true; crtBody.receiveShadow = true
        tvStandGroup.add(crtBody); room.registerInteractable(crtBody, "tv")
        val crtMid = Mesh(Geo.box(0.92f, 0.78f, 0.20f, 4, 4, 1), matCrtShell)
        crtMid.position.set(0f, 1.30f, 0.12f); crtMid.castShadow = true; crtMid.receiveShadow = true
        tvStandGroup.add(crtMid); room.registerInteractable(crtMid, "tv")
        // Wide end against the bezel, narrow tube neck toward the back
        val crtRearGeo = Geo.cylinder(0.26f, 0.56f, 0.36f, 4, 1).rotateY((PI / 4).toFloat()).rotateX((-PI / 2).toFloat())
        val crtRear = Mesh(crtRearGeo, matCrtShell)
        crtRear.scale.y = 0.86f; crtRear.position.set(0f, 1.29f, -0.16f); crtRear.castShadow = true; crtRear.receiveShadow = true
        tvStandGroup.add(crtRear)
        val crtNeckCap = Mesh(Geo.box(0.30f, 0.24f, 0.06f), matCrtDark)
        crtNeckCap.position.set(0f, 1.29f, -0.36f); crtNeckCap.castShadow = true; tvStandGroup.add(crtNeckCap)
        tvScreenMat = PS1.material(map = tvCanvasObj.tex, emissive = 0xffffff, emissiveMap = tvCanvasObj.tex, emissiveIntensity = 0.95f,
            polygonOffset = true, polygonOffsetFactor = -2f, polygonOffsetUnits = -2f)
        // Domed CRT glass
        val tvScreenGeo = Geo.plane(0.74f, 0.56f, 8, 6)
        run {
            val p = tvScreenGeo.positions
            for (i in 0 until tvScreenGeo.vertexCount) { val nx = p[i * 3] / 0.37f; val ny = p[i * 3 + 1] / 0.28f; p[i * 3 + 2] = 0.022f * (1 - 0.5f * nx * nx - 0.5f * ny * ny) }
            tvScreenGeo.computeVertexNormals()
        }
        // Layered with real depth (the PS1 wobble shifts depth by a centimetre at an angle): the black tube face is a
        // solid block 2.2 cm proud of the bezel, the picture floats 1.5 cm in front of that
        val tvScreenMesh = Mesh(tvScreenGeo, tvScreenMat); screenMesh = tvScreenMesh
        tvScreenMesh.position.set(0f, 1.36f, 0.377f)
        tvStandGroup.add(tvScreenMesh); room.registerInteractable(tvScreenMesh, "tv")
        val tvGlassMask = Mesh(Geo.box(0.79f, 0.60f, 0.022f), PS1.material(color = 0x07080c))
        tvGlassMask.position.set(0f, 1.36f, 0.351f)
        tvStandGroup.add(tvGlassMask); room.registerInteractable(tvGlassMask, "tv")
        // Control strip: speaker slots, brand badge, channel / power buttons, LED, A/V jacks
        for (i in 0 until 6) { val slot = Mesh(Geo.box(0.30f, 0.007f, 0.004f), matCrtDark); slot.position.set(-0.25f, 0.935f + i * 0.016f, 0.341f); tvStandGroup.add(slot) }
        val tvBadgeTex = createCanvasTex(64, 16) { ctx, w, _ ->
            ctx.fillStyle = "#9ca3af"; ctx.fillRect(0, 0, w, 16)
            ctx.fillStyle = "#111827"; ctx.font = "bold 9px sans-serif"; ctx.textAlign = "center"; ctx.fillText("MAGNAVOLT", w / 2, 12)
        }.tex
        val tvBadge = Mesh(Geo.box(0.12f, 0.028f, 0.004f), PS1.material(map = tvBadgeTex))
        tvBadge.position.set(0f, 1.035f, 0.342f); tvStandGroup.add(tvBadge)
        // Power button, with an invisible pad so a near-miss still counts
        val tvPowerButton = Mesh(Geo.box(0.075f, 0.04f, 0.014f), PS1.material(color = 0x9a1c1c, emissive = 0xff2a2a, emissiveIntensity = 0.25f))
        tvPowerButton.position.set(0.33f, 0.95f, 0.343f); tvStandGroup.add(tvPowerButton); room.registerInteractable(tvPowerButton, "tvPower")
        val tvPowerPad = Mesh(Geo.box(0.18f, 0.1f, 0.02f), PS1.basic().also { it.visible = false })
        tvPowerPad.position.set(0.35f, 0.95f, 0.35f); tvStandGroup.add(tvPowerPad); room.registerInteractable(tvPowerPad, "tvPower")
        for (bx in floatArrayOf(0.24f, 0.272f)) { val chBtn = Mesh(Geo.box(0.024f, 0.02f, 0.01f), matCrtDark); chBtn.position.set(bx, 0.95f, 0.342f); tvStandGroup.add(chBtn); room.registerInteractable(chBtn, "tv") }
        tvLedMat = PS1.material(color = 0x7f1d1d, emissive = 0xef4444, emissiveIntensity = if (bedroomTvOn) 1.0f else 0.05f)
        val tvPowerLed = Mesh(Geo.box(0.014f, 0.014f, 0.01f), tvLedMat)
        tvPowerLed.position.set(0.39f, 0.95f, 0.342f); tvStandGroup.add(tvPowerLed)
        // Front composite A/V input jacks: video (yellow), audio L (white), audio R (red)
        for ((jx, col) in listOf(0.10f to 0xfacc15, 0.135f to 0xf8fafc, 0.17f to 0xdc2626)) {
            val jack = Mesh(Geo.cylinder(0.009f, 0.009f, 0.012f, 8), PS1.material(color = col))
            jack.rotation.x = (PI / 2).toFloat(); jack.position.set(jx, 0.925f, 0.345f); tvStandGroup.add(jack)
        }
        // Rabbit-ear V-dipole antennas on top of the set
        val antennaBase = Mesh(Geo.box(0.12f, 0.03f, 0.08f), matCrtDark)
        antennaBase.position.set(0f, 1.725f, 0.1f); tvStandGroup.add(antennaBase)
        for (angle in floatArrayOf(-0.35f, 0.35f)) {
            val ant = Mesh(Geo.cylinder(0.005f, 0.008f, 0.52f, 5), PS1.material(color = 0xcbd5e1))
            ant.position.set(sin(angle) * 0.26f, 1.74f + cos(angle) * 0.26f, 0.1f); ant.rotation.z = -angle
            tvStandGroup.add(ant)
        }

        // ---- Baseboards on the back and right walls, and the wall outlet ----
        val baseboardBack = Mesh(Geo.box(8.11f, 0.14f, 0.06f, 8, 1, 1), room.matWood)
        baseboardBack.position.set(0.325f, 0.07f, -2.96f); baseboardBack.receiveShadow = true; scene.add(baseboardBack)
        val baseboardRight = Mesh(Geo.box(0.06f, 0.14f, 8.5f, 1, 1, 8), room.matWood)
        baseboardRight.position.set(4.36f, 0.07f, 1.2f); baseboardRight.receiveShadow = true; scene.add(baseboardRight)
        val outletPlate = Mesh(Geo.box(0.14f, 0.22f, 0.03f), room.matBeigePlastic)
        outletPlate.position.set(-0.25f, 0.55f, -2.97f); scene.add(outletPlate)
        // Glow-in-the-dark stars on the walls by the ceiling
        glowStarMat = PS1.material(color = 0xa7f3d0, emissive = 0x34d399, emissiveIntensity = 0.75f)
        arrayOf(floatArrayOf(1.6f, 4.35f, -2.95f), floatArrayOf(2.4f, 4.5f, -2.95f), floatArrayOf(3.3f, 4.25f, -2.95f), floatArrayOf(3.9f, 4.6f, -2.95f),
            floatArrayOf(4.35f, 4.3f, -1.8f), floatArrayOf(4.35f, 4.5f, -0.7f)).forEachIndexed { idx, s ->
            val star = Mesh(Geo.octahedron(0.055f, 0), glowStarMat)
            star.position.set(s[0], s[1], s[2]); star.rotation.z = idx * 0.5f
            scene.add(star)
        }
        updateBedroomTvScreen(0f)
    }

    // ---- Power and channels ----
    fun toggleBedroomTv(t: Float) {
        bedroomTvOn = !bedroomTvOn
        if (!bedroomTvOn) ytShowing = false
        tvScreenMat.emissiveIntensity = if (bedroomTvOn) 0.95f else 0.04f
        room.bedroomTvLight.intensity = if (bedroomTvOn) 1.15f else 0.0f
        tvLedMat.emissiveIntensity = if (bedroomTvOn) 1.0f else 0.05f
        updateBedroomTvScreen(t) // the screen isn't redrawn while the TV is off, so blank (or restore) it once here
        onPowerChanged?.invoke(bedroomTvOn)
    }
    fun cycleBedroomTvChannel(t: Float) {
        if (!bedroomTvOn) { toggleBedroomTv(t); return }
        // The A/V input is only in the rotation while the PlayStation is on; flipping past the last channel turns the TV off
        val next = (bedroomTvChannel + 1) % (if (ps1On) TV_CHANNEL_NAMES.size else PS1_TV_CHANNEL)
        if (next == 0) { bedroomTvChannel = 0; toggleBedroomTv(t); return }
        bedroomTvChannel = next
        room.bedroomTvLight.color.setHex(TV_CHANNEL_COLORS[bedroomTvChannel])
    }
    fun stepChannel(delta: Int, t: Float = 0f) {
        if (!bedroomTvOn) { toggleBedroomTv(t); return }
        val maxCh = if (ps1On) TV_CHANNEL_NAMES.size else PS1_TV_CHANNEL
        val next = (bedroomTvChannel + delta + maxCh) % maxCh
        bedroomTvChannel = next
        room.bedroomTvLight.color.setHex(TV_CHANNEL_COLORS[bedroomTvChannel])
    }
    fun setTvChannelDirect(ch: Int, t: Float = 0f) {
        if (!bedroomTvOn) toggleBedroomTv(t)
        val maxCh = if (ps1On) TV_CHANNEL_NAMES.size else PS1_TV_CHANNEL
        bedroomTvChannel = ch.coerceIn(0, maxCh - 1)
        room.bedroomTvLight.color.setHex(TV_CHANNEL_COLORS[bedroomTvChannel])
    }
    /** The set flickers its glow onto the room while it's on (animate loop). */
    fun updateLight(t: Float) { if (bedroomTvOn) room.bedroomTvLight.intensity = 1.10f + sin(t * 23.0f) * 0.12f }

    // ---- The picture ----
    private var tvVignette: Canvas2D.Gradient? = null
    fun updateBedroomTvScreen(t: Float) {
        val ctx = tvCanvasObj.ctx
        val w = 128; val h = 96
        ctx.setTransform(2, 0, 0, 2, 0, 0)
        if (tvLastChannel == -1) tvLastChannel = bedroomTvChannel // first draw: no channel-change effect
        else if (bedroomTvChannel != tvLastChannel) { tvLastChannel = bedroomTvChannel; tvChannelChangedAt = t }
        if (!bedroomTvOn || ytShowing) {
            ctx.fillStyle = "#05060a"; ctx.fillRect(0, 0, w, h)
            ctx.setTransform(1, 0, 0, 1, 0, 0)
            tvCanvasObj.tex.needsUpdate()
            return
        }
        when (bedroomTvChannel) {
            0 -> { // Late-night 90s anime mecha / action scene
                val bg = ctx.createLinearGradient(0, 0, 0, h)
                bg.addColorStop(0, "#1e1b4b"); bg.addColorStop(0.6, "#701a75"); bg.addColorStop(1, "#f43f5e")
                ctx.fillStyle = bg; ctx.fillRect(0, 0, w, h)
                // Speed lines
                ctx.strokeStyle = "rgba(253, 224, 71, 0.55)"; ctx.lineWidth = 1f
                for (i in 0 until 12) { val ly = (i * 9 + floor(t * 40.0).toInt()) % h; ctx.beginPath(); ctx.moveTo(0, ly); ctx.lineTo(w, ly - 6); ctx.stroke() }
                // Mecha silhouette & glowing eyes
                val bob = sin(t * 5) * 2
                ctx.fillStyle = "#0f172a"
                ctx.beginPath(); ctx.moveTo(36, 96); ctx.lineTo(48, 34 + bob); ctx.lineTo(64, 22 + bob); ctx.lineTo(80, 34 + bob); ctx.lineTo(92, 96); ctx.fill()
                ctx.fillStyle = "#38bdf8"; ctx.fillRect(53, 40 + bob, 8, 3); ctx.fillRect(67, 40 + bob, 8, 3)
                // Yellow broadcast subtitles
                ctx.fillStyle = "rgba(0,0,0,0.65)"; ctx.fillRect(14, 78, 100, 12)
                ctx.fillStyle = "#facc15"; ctx.font = "bold 7px monospace"
                ctx.fillText("\"IT CAN'T BE... OVER 9000?!\"", 18, 86)
            }
            1 -> drawWeatherChannel(ctx, w, h, t)
            2 -> if (tapes?.drawTapeVideo(ctx, w, h, t) != true) { // VHS: ALIEN NIGHT (other tapes draw themselves)
                ctx.fillStyle = "#05140c"; ctx.fillRect(0, 0, w, h)
                ctx.fillStyle = "#14532d"; ctx.beginPath(); ctx.moveTo(64, 14); ctx.lineTo(24, 88); ctx.lineTo(104, 88); ctx.closePath(); ctx.fill()
                ctx.fillStyle = "#86efac"; ctx.beginPath(); ctx.arc(64, 24 + sin(t * 3) * 3, 12, 0, PI * 2); ctx.fill()
                ctx.fillStyle = "#ffffff"; ctx.font = "bold 8px monospace"
                ctx.fillText("PLAY ►", 8, 14); ctx.fillText("SLP 01:42:19", 8, 88)
                val trackY = (t * 32) % h
                ctx.fillStyle = "rgba(255,255,255,0.35)"; ctx.fillRect(0, trackY, w, 4)
            }
            3 -> { // Midnight shopping network
                ctx.fillStyle = "#7c2d12"; ctx.fillRect(0, 0, w, h)
                ctx.fillStyle = "#facc15"; ctx.fillRect(6, 6, 48, 22)
                ctx.fillStyle = "#7f1d1d"; ctx.font = "bold 8px sans-serif"; ctx.fillText("ONLY \$19.99", 9, 19)
                ctx.strokeStyle = "#fde047"; ctx.lineWidth = 2f; ctx.strokeRect(56 + sin(t * 2) * 4, 22, 42, 42)
                ctx.fillStyle = "#2563eb"; ctx.fillRect(0, 74, w, 22)
                ctx.fillStyle = "#ffffff"; ctx.font = "bold 8px monospace"; ctx.fillText("CALL 1-800-555-GEMS", 12, 88)
            }
            5 -> { // Saturday-morning cartoon: a cat chasing a mouse across a sunny yard
                ctx.fillStyle = "#7dd3fc"; ctx.fillRect(0, 0, w, 62)
                ctx.fillStyle = "#4ade80"; ctx.fillRect(0, 62, w, h - 62)
                ctx.fillStyle = "#ffffff"
                for ((cx, cy) in arrayOf(20 to 16, 70 to 24, 108 to 12)) {
                    ctx.beginPath(); ctx.arc((cx + t * 6) % (w + 30) - 15, cy, 7, 0, PI * 2); ctx.arc((cx + t * 6) % (w + 30) - 6, cy - 3, 6, 0, PI * 2); ctx.fill()
                }
                ctx.fillStyle = "#92400e"; ctx.fillRect(0, 54, w, 4)
                var x = 4; while (x < w) { ctx.fillRect(x, 44, 3, 12); x += 10 }
                val chase = (t * 38) % (w + 60) - 30
                val hop = abs(sin(t * 12)) * 4
                ctx.fillStyle = "#9ca3af" // mouse
                ctx.beginPath(); ctx.ellipse(chase + 26, 72 - hop, 6, 4, 0, 0, PI * 2); ctx.fill()
                ctx.beginPath(); ctx.arc(chase + 31, 68 - hop, 3, 0, PI * 2); ctx.fill()
                ctx.fillStyle = "#f97316" // cat
                ctx.beginPath(); ctx.ellipse(chase, 70 - hop * 0.5, 11, 7, 0, 0, PI * 2); ctx.fill()
                ctx.beginPath(); ctx.arc(chase + 10, 62 - hop * 0.5, 6, 0, PI * 2); ctx.fill()
                ctx.fillStyle = "#ffffff"; ctx.fillRect(chase + 11, 60 - hop * 0.5, 3, 3)
                ctx.fillStyle = "#000000"; ctx.fillRect(chase + 12, 61 - hop * 0.5, 1, 1)
                if (floor(t * 1.5).toInt() % 4 == 0) {
                    ctx.fillStyle = "#fde047"; ctx.beginPath()
                    for (i in 0 until 16) { val a = (i / 16.0) * PI * 2; val r = if (i % 2 == 1) 9 else 16; ctx.lineTo(96 + cos(a) * r, 30 + sin(a) * r) }
                    ctx.fill()
                    ctx.fillStyle = "#dc2626"; ctx.font = "bold 8px Impact, sans-serif"; ctx.textAlign = "center"
                    ctx.fillText("WHAM!", 96, 33); ctx.textAlign = "left"
                }
            }
            6 -> { // Late-night music video countdown: band silhouettes under sweeping stage lights
                ctx.fillStyle = "#0b0618"; ctx.fillRect(0, 0, w, h)
                for ((c, ph) in arrayOf("rgba(236, 72, 153, 0.35)" to 0, "rgba(56, 189, 248, 0.35)" to 2, "rgba(250, 204, 21, 0.3)" to 4)) {
                    val bx = 64 + sin(t * 1.3 + ph) * 50
                    ctx.fillStyle = c
                    ctx.beginPath(); ctx.moveTo(bx - 4, 0); ctx.lineTo(bx + 4, 0); ctx.lineTo(bx + 26, 80); ctx.lineTo(bx - 26, 80); ctx.fill()
                }
                ctx.fillStyle = "#1f1b2e"; ctx.fillRect(0, 72, w, 10)
                for ((bx, i) in arrayOf(30 to 0, 64 to 1, 98 to 2)) {
                    val jump = max(0.0, sin(t * 9.0 + i * 2)) * 5
                    ctx.fillStyle = "#000000"
                    ctx.beginPath(); ctx.arc(bx, 44 - jump, 5, 0, PI * 2); ctx.fill()
                    ctx.fillRect(bx - 5, 49 - jump, 10, 18)
                    ctx.fillRect(bx - 6, 67 - jump, 4, 6); ctx.fillRect(bx + 2, 67 - jump, 4, 6)
                    if (i != 1) { ctx.fillStyle = "#7c2d12"; ctx.fillRect(bx - 9, 56 - jump, 16, 4) }
                }
                ctx.fillStyle = "rgba(0, 0, 0, 0.75)"; ctx.fillRect(0, 82, w, 14)
                ctx.fillStyle = "#facc15"; ctx.fillRect(0, 82, 16, 14)
                ctx.fillStyle = "#000000"; ctx.font = "bold 9px Impact, sans-serif"; ctx.fillText("#3", 2, 93)
                ctx.fillStyle = "#ffffff"; ctx.font = "bold 6px sans-serif"; ctx.fillText("SMASH CITY", 20, 88)
                ctx.fillStyle = "#a5b4fc"; ctx.fillText("\"All Star Kids\" (Blink-ish Records)", 20, 94)
            }
            PS1_TV_CHANNEL -> ps1?.drawScreen(ctx, w, h, t)
            else -> { // Snowy CRT static
                var y = 0
                while (y < h) {
                    var x = 0
                    while (x < w) { val v = (rnd.nextDouble() * 215 + 25).toInt(); ctx.fillRectArgb(x, y, 3, 3, (0xff shl 24) or (v shl 16) or (v shl 8) or min(255, v + 20)); x += 3 }
                    y += 3
                }
                // (native fix: the HTML also painted a stale "CH 03" here, doubling the real channel number)
            }
        }
        // Channel-change static burst and the green on-screen channel number
        val sinceChange = t - tvChannelChangedAt
        if (sinceChange < 0.3f) {
            val a = ((0.85 - sinceChange * 2) * 255).toInt().coerceIn(0, 255)
            var y = 0
            while (y < h) { var x = 0; while (x < w) { val v = (rnd.nextDouble() * 200 + 30).toInt(); ctx.fillRectArgb(x, y, 2, 2, (a shl 24) or (v shl 16) or (v shl 8) or v); x += 2 }; y += 2 }
        }
        if (sinceChange < 2.5f && bedroomTvChannel != PS1_TV_CHANNEL) {
            val label = TV_CHANNEL_NAMES[bedroomTvChannel].substringBefore(':')
            ctx.font = "bold 9px monospace"; ctx.textAlign = "right"
            ctx.fillStyle = "#000"; ctx.fillText(label, w - 5, 13)
            ctx.fillStyle = "#4ade80"; ctx.fillText(label, w - 6, 12)
            ctx.textAlign = "left"
        }
        // CRT: fine scanlines at full resolution, phosphor glow fading toward the corners, a slow rolling band
        ctx.setTransform(1, 0, 0, 1, 0, 0)
        ctx.fillStyle = "rgba(0, 0, 0, 0.22)"
        var sy = 0; while (sy < 192) { ctx.fillRect(0, sy, 256, 1); sy += 2 }
        val roll = (t * 40) % 260 - 30
        ctx.fillStyle = "rgba(255, 255, 255, 0.035)"; ctx.fillRect(0, roll, 256, 18)
        val vig = tvVignette ?: ctx.createRadialGradient(128, 96, 60, 128, 96, 170).also {
            it.addColorStop(0, "rgba(0,0,0,0)"); it.addColorStop(1, "rgba(0,0,0,0.55)"); tvVignette = it
        }
        ctx.fillStyle = vig; ctx.fillRect(0, 0, 256, 192)
        tvCanvasObj.tex.needsUpdate()
    }

    // =========================================================================
    // 90s THE WEATHER CHANNEL BROADCAST ENGINE (WEATHERSTAR 4000 / LOCAL ON THE 8s)
    // =========================================================================
    data class HolidayDef(val name: String, val month: Int, val day: Int, val icon: String, val themeCol: String, val tag: String)
    data class HolidayEvent(val name: String, val icon: String, val daysUntil: Int, val themeCol: String, val tag: String)

    private var lastHolidayCheckMin = -1
    private var cachedHolidayPair: Pair<HolidayEvent, HolidayEvent>? = null

    private fun getHolidayList(year: Int): List<HolidayDef> {
        val list = ArrayList<HolidayDef>()
        list.add(HolidayDef("New Year's Day", Calendar.JANUARY, 1, "🎆", "#3b82f6", "HAPPY NEW YEAR!"))
        list.add(HolidayDef("Valentine's Day", Calendar.FEBRUARY, 14, "💖", "#ec4899", "VALENTINE'S SWEETS"))
        list.add(HolidayDef("St. Patrick's", Calendar.MARCH, 17, "☘", "#16a34a", "LUCK OF THE IRISH"))
        list.add(HolidayDef("Earth Day", Calendar.APRIL, 22, "🌍", "#10b981", "LOVE OUR PLANET"))
        list.add(HolidayDef("Cinco de Mayo", Calendar.MAY, 5, "🎉", "#f59e0b", "FIESTA CELEBRATION"))

        // Mother's Day: 2nd Sunday of May
        var c = Calendar.getInstance().apply { set(year, Calendar.MAY, 1) }
        var sunCount = 0
        while (c.get(Calendar.MONTH) == Calendar.MAY) {
            if (c.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                sunCount++
                if (sunCount == 2) {
                    list.add(HolidayDef("Mother's Day", Calendar.MAY, c.get(Calendar.DAY_OF_MONTH), "💐", "#f43f5e", "HAPPY MOTHER'S DAY"))
                    break
                }
            }
            c.add(Calendar.DAY_OF_MONTH, 1)
        }

        // Memorial Day: Last Monday of May
        c = Calendar.getInstance().apply { set(year, Calendar.MAY, 31) }
        while (c.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) { c.add(Calendar.DAY_OF_MONTH, -1) }
        list.add(HolidayDef("Memorial Day", Calendar.MAY, c.get(Calendar.DAY_OF_MONTH), "🎖", "#3b82f6", "HONOR & REMEMBER"))

        // Father's Day: 3rd Sunday of June
        c = Calendar.getInstance().apply { set(year, Calendar.JUNE, 1) }
        sunCount = 0
        while (c.get(Calendar.MONTH) == Calendar.JUNE) {
            if (c.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                sunCount++
                if (sunCount == 3) {
                    list.add(HolidayDef("Father's Day", Calendar.JUNE, c.get(Calendar.DAY_OF_MONTH), "👔", "#0284c7", "HAPPY FATHER'S DAY"))
                    break
                }
            }
            c.add(Calendar.DAY_OF_MONTH, 1)
        }

        list.add(HolidayDef("4th of July", Calendar.JULY, 4, "🎆", "#ef4444", "INDEPENDENCE DAY"))

        // Labor Day: 1st Monday of September
        c = Calendar.getInstance().apply { set(year, Calendar.SEPTEMBER, 1) }
        while (c.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) { c.add(Calendar.DAY_OF_MONTH, 1) }
        list.add(HolidayDef("Labor Day", Calendar.SEPTEMBER, c.get(Calendar.DAY_OF_MONTH), "🛠", "#d97706", "END OF SUMMER BASH"))

        list.add(HolidayDef("Halloween", Calendar.OCTOBER, 31, "🎃", "#ea580c", "TRICK OR TREAT!"))
        list.add(HolidayDef("Veterans Day", Calendar.NOVEMBER, 11, "🎖", "#3b82f6", "HONORING VETERANS"))

        // Thanksgiving: 4th Thursday of November
        c = Calendar.getInstance().apply { set(year, Calendar.NOVEMBER, 1) }
        var thuCount = 0
        while (c.get(Calendar.MONTH) == Calendar.NOVEMBER) {
            if (c.get(Calendar.DAY_OF_WEEK) == Calendar.THURSDAY) {
                thuCount++
                if (thuCount == 4) {
                    list.add(HolidayDef("Thanksgiving", Calendar.NOVEMBER, c.get(Calendar.DAY_OF_MONTH), "🦃", "#b45309", "HARVEST & FEAST"))
                    break
                }
            }
            c.add(Calendar.DAY_OF_MONTH, 1)
        }

        list.add(HolidayDef("Christmas Eve", Calendar.DECEMBER, 24, "✨", "#15803d", "HOLIDAY SPIRIT"))
        list.add(HolidayDef("Christmas", Calendar.DECEMBER, 25, "🎄", "#dc2626", "MERRY CHRISTMAS!"))
        list.add(HolidayDef("New Year's Eve", Calendar.DECEMBER, 31, "🥂", "#ca8a04", "COUNTDOWN TO MIDNIGHT"))

        return list
    }

    private fun getUpcomingHolidays(): Pair<HolidayEvent, HolidayEvent> {
        val now = Calendar.getInstance()
        val curMin = now.get(Calendar.MINUTE)
        val cached = cachedHolidayPair
        if (cached != null && curMin == lastHolidayCheckMin) return cached

        val curYear = now.get(Calendar.YEAR)
        val curMonth = now.get(Calendar.MONTH)
        val curDay = now.get(Calendar.DAY_OF_MONTH)
        val todayStart = Calendar.getInstance().apply {
            set(curYear, curMonth, curDay, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val candidates = ArrayList<HolidayEvent>()
        for (yr in curYear..(curYear + 1)) {
            val hols = getHolidayList(yr)
            for (h in hols) {
                val target = Calendar.getInstance().apply {
                    set(yr, h.month, h.day, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val diffMs = target.timeInMillis - todayStart.timeInMillis
                val diffDays = (diffMs / 86400000L).toInt()
                if (diffDays >= 0) {
                    candidates.add(HolidayEvent(h.name, h.icon, diffDays, h.themeCol, h.tag))
                }
            }
        }
        candidates.sortBy { it.daysUntil }
        val first = candidates.getOrNull(0) ?: HolidayEvent("New Year", "🎆", 30, "#3b82f6", "HAPPY NEW YEAR")
        val second = candidates.getOrNull(1) ?: HolidayEvent("Spring", "🌱", 60, "#22c55e", "SPRING AHEAD")
        val result = first to second
        cachedHolidayPair = result
        lastHolidayCheckMin = curMin
        return result
    }

    private fun moonPhaseName(phase: Double): String {
        val p = ((phase % 1.0) + 1.0) % 1.0
        return when {
            p < 0.04 || p >= 0.96 -> "NEW MOON"
            p < 0.21 -> "WAXING CRESCENT"
            p < 0.29 -> "FIRST QUARTER"
            p < 0.46 -> "WAXING GIBBOUS"
            p < 0.54 -> "FULL MOON"
            p < 0.71 -> "WANING GIBBOUS"
            p < 0.79 -> "LAST QUARTER"
            else -> "WANING CRESCENT"
        }
    }

    private fun drawWeatherIcon(ctx: Canvas2D, kind: String, cx: Float, cy: Float) {
        when (kind) {
            "sun" -> {
                ctx.fillStyle = "#facc15"
                ctx.beginPath(); ctx.arc(cx, cy, 3.5f, 0, PI * 2); ctx.fill()
                ctx.strokeStyle = "#fbbf24"; ctx.lineWidth = 1f
                ctx.beginPath()
                ctx.moveTo(cx, cy - 6f); ctx.lineTo(cx, cy - 4.5f)
                ctx.moveTo(cx, cy + 4.5f); ctx.lineTo(cx, cy + 6f)
                ctx.moveTo(cx - 6f, cy); ctx.lineTo(cx - 4.5f, cy)
                ctx.moveTo(cx + 4.5f, cy); ctx.lineTo(cx + 6f, cy)
                ctx.stroke()
            }
            "rain" -> {
                ctx.fillStyle = "#64748b"
                ctx.beginPath()
                ctx.arc(cx - 3f, cy + 1f, 3.2f, 0, PI * 2)
                ctx.arc(cx + 3f, cy + 1f, 3.2f, 0, PI * 2)
                ctx.arc(cx, cy - 1.2f, 4f, 0, PI * 2)
                ctx.fill()
                ctx.strokeStyle = "#38bdf8"; ctx.lineWidth = 1f
                ctx.beginPath()
                ctx.moveTo(cx - 3f, cy + 4.5f); ctx.lineTo(cx - 4.5f, cy + 7.5f)
                ctx.moveTo(cx, cy + 4.5f); ctx.lineTo(cx - 1.5f, cy + 7.5f)
                ctx.moveTo(cx + 3f, cy + 4.5f); ctx.lineTo(cx + 1.5f, cy + 7.5f)
                ctx.stroke()
            }
            "storm" -> {
                ctx.fillStyle = "#334155"
                ctx.beginPath()
                ctx.arc(cx - 3f, cy + 1f, 3.2f, 0, PI * 2)
                ctx.arc(cx + 3f, cy + 1f, 3.2f, 0, PI * 2)
                ctx.arc(cx, cy - 1.2f, 4f, 0, PI * 2)
                ctx.fill()
                ctx.strokeStyle = "#facc15"; ctx.lineWidth = 1.2f
                ctx.beginPath()
                ctx.moveTo(cx + 1f, cy + 3.5f); ctx.lineTo(cx - 1.5f, cy + 6f)
                ctx.lineTo(cx + 1f, cy + 6f); ctx.lineTo(cx - 2f, cy + 8.5f)
                ctx.stroke()
            }
            "snow" -> {
                ctx.fillStyle = "#60a5fa"
                ctx.beginPath()
                ctx.arc(cx - 3f, cy + 1f, 3.2f, 0, PI * 2)
                ctx.arc(cx + 3f, cy + 1f, 3.2f, 0, PI * 2)
                ctx.arc(cx, cy - 1.2f, 4f, 0, PI * 2)
                ctx.fill()
                ctx.fillStyle = "#ffffff"
                ctx.fillRect(cx - 3f, cy + 5f, 1.5f, 1.5f)
                ctx.fillRect(cx, cy + 6.5f, 1.5f, 1.5f)
                ctx.fillRect(cx + 3f, cy + 5f, 1.5f, 1.5f)
            }
            else -> { // cloud / overcast / partly
                ctx.fillStyle = "#94a3b8"
                ctx.beginPath()
                ctx.arc(cx - 3.2f, cy + 1.5f, 3.2f, 0, PI * 2)
                ctx.arc(cx + 3.2f, cy + 1.5f, 3.2f, 0, PI * 2)
                ctx.arc(cx, cy - 1.2f, 4.2f, 0, PI * 2)
                ctx.fill()
                ctx.fillStyle = "#e2e8f0"
                ctx.beginPath()
                ctx.arc(cx - 1.5f, cy, 3f, 0, PI * 2)
                ctx.fill()
            }
        }
    }

    private fun drawMoonGraphic(ctx: Canvas2D, cx: Float, cy: Float, r: Float, phase: Double) {
        val p = ((phase % 1.0) + 1.0) % 1.0
        val termX = -r * cos(2.0 * PI * p).toFloat()

        // Draw vertical pixel strips across the lunar circle
        val ir = r.toInt()
        for (ix in -ir..ir) {
            val hHalf = kotlin.math.sqrt(max(0f, r * r - ix * ix))
            if (hHalf <= 0.5f) continue
            val isLit = if (p < 0.5) ix > termX else ix < termX
            ctx.fillStyle = if (isLit) "#f1f5f9" else "#0b1426"
            ctx.fillRect(cx + ix, cy - hHalf, 1f, hHalf * 2f)
        }

        // Subtle crater marks on lit area
        if (p in 0.35..0.65) {
            ctx.fillStyle = "#cbd5e1"
            ctx.fillRect(cx - 2f, cy - 2f, 1.5f, 1.5f)
            ctx.fillRect(cx + 2f, cy + 2.5f, 1.5f, 1.5f)
            ctx.fillRect(cx + 1f, cy - 3.5f, 1f, 1f)
        }

        // Outer border rim
        ctx.strokeStyle = "#334155"; ctx.lineWidth = 0.8f
        ctx.beginPath(); ctx.arc(cx, cy, r, 0, PI * 2); ctx.stroke()
    }

    private fun drawWeatherChannel(ctx: Canvas2D, w: Int, h: Int, t: Float) {
        val now = Calendar.getInstance()
        val curMonth = now.get(Calendar.MONTH)
        val curDay = now.get(Calendar.DAY_OF_MONTH)
        val MONTH_NAMES = arrayOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
        val monthStr = MONTH_NAMES.getOrElse(curMonth) { "OCT" }
        val dayStr = env.dayOfWeekShort.uppercase()
        val dateStr = "$dayStr $monthStr $curDay"
        val timeStr = env.formatHour(env.hour)

        val (firstHoli, secondHoli) = getUpcomingHolidays()

        // Seasonal & holiday theming
        val themeHeaderCol = when {
            firstHoli.daysUntil <= 5 -> firstHoli.themeCol
            curMonth in 8..10 -> "#b45309"   // Autumn amber
            curMonth == 11 || curMonth in 0..1 -> "#0284c7" // Winter icy blue
            curMonth in 2..4 -> "#059669"    // Spring emerald
            else -> "#1d4ed8"                 // Summer blue
        }

        // Atmospheric metrics
        val cTemp = rawTempC()
        val humidity = when {
            env.W.snow -> 84
            env.W.rain > 0.8f -> 95
            env.W.rain > 0.4f -> 90
            env.W.fog > 0.5f -> 96
            env.W.cloud > 0.8f -> 78
            env.W.cloud > 0.4f -> 62
            else -> 46
        }
        val (baroInHg, baroTrend) = when {
            env.W.lightning -> 29.58 to "▼"
            env.W.rain > 0.5f -> 29.74 to "▼"
            env.W.fog > 0.5f -> 29.88 to "▲"
            env.W.cloud > 0.7f -> 29.96 to "–"
            else -> 30.14 to "▲"
        }
        val baroStr = if (tempUnit() == "F") String.format(Locale.US, "%.2f IN %s", baroInHg, baroTrend) else "${(baroInHg * 33.8639).roundToInt()} HPA $baroTrend"
        val dewPtC = cTemp - ((100 - humidity) / 5.0)
        val dewPtStr = if (tempUnit() == "F") "${(dewPtC * 1.8 + 32).roundToInt()}°" else "${dewPtC.roundToInt()}°"

        val windSpeedKmH = if (env.liveWindSpeedKmH >= 0f) env.liveWindSpeedKmH else (env.W.wind * 28f)
        val windMph = (windSpeedKmH * 0.621371f).roundToInt()
        val windSpeedStr = if (tempUnit() == "F") "$windMph MPH" else "${windSpeedKmH.roundToInt()} KM/H"
        val gustKmH = (windSpeedKmH * 1.4f).roundToInt()
        val gustMph = (gustKmH * 0.621371f).roundToInt()
        val gustStr = if (tempUnit() == "F") "$gustMph MPH" else "$gustKmH KM/H"
        val windDeg = if (env.liveWindDirDeg >= 0f) env.liveWindDirDeg else 225f
        val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val windCompass = dirs[(((windDeg + 22.5f) / 45f).toInt()) % 8]

        val feelsLikeC = if (windMph > 4 && cTemp < 10.0) {
            13.12 + 0.6215 * cTemp - 11.37 * Math.pow(windSpeedKmH.toDouble(), 0.16) + 0.3965 * cTemp * Math.pow(windSpeedKmH.toDouble(), 0.16)
        } else if (cTemp > 24.0 && humidity > 60) {
            cTemp + (humidity - 50) * 0.08
        } else {
            cTemp
        }
        val feelsLikeStr = if (tempUnit() == "F") "${(feelsLikeC * 1.8 + 32).roundToInt()}°" else "${feelsLikeC.roundToInt()}°"

        if (weatherView() == "simple") {
            drawSimpleWeatherView(
                ctx, w, h, t, dateStr, timeStr,
                themeHeaderCol, firstHoli, cTemp, feelsLikeStr,
                windCompass, windSpeedStr, curMonth
            )
            return
        }

        // Base screen background: retro CRT deep navy
        ctx.fillStyle = "#070e1c"; ctx.fillRect(0, 0, w, h)

        // 1. Header Banner (y = 0..13)
        ctx.fillStyle = themeHeaderCol; ctx.fillRect(0, 0, w, 13)
        ctx.fillStyle = "#facc15"; ctx.fillRect(0, 13, w, 1) // 1px gold separator

        // Weather Channel mini box logo
        ctx.fillStyle = "#003399"; ctx.fillRect(3, 2, 22, 9)
        ctx.strokeStyle = "#ffffff"; ctx.lineWidth = 0.8f; ctx.strokeRect(3, 2, 22, 9)
        ctx.fillStyle = "#ffffff"; ctx.font = "bold 5px sans-serif"; ctx.textAlign = "center"
        ctx.fillText("THE", 14, 5.5)
        ctx.fillStyle = "#facc15"; ctx.font = "bold 4.5px sans-serif"
        ctx.fillText("WEATHER", 14, 9.5)

        // Title text / Location / Time
        ctx.textAlign = "left"
        if (firstHoli.daysUntil <= 5) {
            ctx.fillStyle = "#fde047"; ctx.font = "bold 5.5px monospace"
            ctx.fillText("${firstHoli.icon} SPECIAL", 28, 9)
        } else {
            ctx.fillStyle = "#ffffff"; ctx.font = "bold 5.5px monospace"
            ctx.fillText("WEATHERSTAR", 28, 9)
        }

        ctx.textAlign = "right"
        ctx.fillStyle = "#ffffff"; ctx.font = "bold 6px monospace"
        ctx.fillText("${cityText().take(8)} $timeStr", w - 4, 9)
        ctx.textAlign = "left"

        // 2. Subheader Ribbon (y = 14..22)
        val slide = ((t / 7.0).toInt()) % 4
        ctx.fillStyle = "#0f172a"; ctx.fillRect(0, 14, w, 8)
        ctx.fillStyle = "#fde047"; ctx.font = "bold 5px monospace"
        when (slide) {
            0 -> ctx.fillText("● CURRENT CONDITIONS & RADAR", 4, 20)
            1 -> ctx.fillText("● 5-DAY EXTENDED LOCAL OUTLOOK", 4, 20)
            2 -> ctx.fillText("● CELESTIAL ALMANAC & LUNAR PHASES", 4, 20)
            3 -> ctx.fillText("● REGIONAL HOLIDAYS & CLIMATE", 4, 20)
        }
        ctx.textAlign = "right"
        ctx.fillStyle = "#38bdf8"; ctx.font = "bold 5px monospace"
        ctx.fillText("PAGE ${slide + 1}/4", w - 4, 20)
        ctx.textAlign = "left"

        // 3. Body Slides (y = 22..77)
        when (slide) {
            0 -> { // Slide 0: Current Conditions & Live Doppler Radar 4000
                // Left: Radar scope (x = 3, y = 24, w = 56, h = 52)
                ctx.fillStyle = "#040914"; ctx.fillRect(3, 24, 56, 52)
                ctx.strokeStyle = "#1e293b"; ctx.lineWidth = 1f; ctx.strokeRect(3, 24, 56, 52)

                val rcx = 31f; val rcy = 50f
                // Range rings
                ctx.strokeStyle = "#14532d"; ctx.lineWidth = 1f
                ctx.beginPath(); ctx.arc(rcx, rcy, 23f, 0, PI * 2); ctx.stroke()
                ctx.strokeStyle = "rgba(34, 197, 94, 0.4)"; ctx.lineWidth = 0.8f
                ctx.beginPath(); ctx.arc(rcx, rcy, 15f, 0, PI * 2); ctx.stroke()
                ctx.strokeStyle = "rgba(34, 197, 94, 0.25)"
                ctx.beginPath(); ctx.arc(rcx, rcy, 7f, 0, PI * 2); ctx.stroke()

                // Crosshairs
                ctx.strokeStyle = "rgba(34, 197, 94, 0.35)"; ctx.lineWidth = 0.8f
                ctx.beginPath()
                ctx.moveTo(8f, rcy); ctx.lineTo(54f, rcy)
                ctx.moveTo(rcx, 27f); ctx.lineTo(rcx, 73f)
                ctx.stroke()

                // Cardinal indicators
                ctx.fillStyle = "rgba(74, 222, 128, 0.75)"; ctx.font = "bold 4.5px monospace"
                ctx.textAlign = "center"
                ctx.fillText("N", rcx, 29); ctx.fillText("S", rcx, 74)
                ctx.fillText("W", 8, rcy + 2); ctx.fillText("E", 54, rcy + 2)
                ctx.textAlign = "left"

                // Drifting animated weather cells
                val rad = Math.toRadians(windDeg.toDouble())
                val driftSpeed = (windSpeedKmH / 12f).coerceIn(0.4f, 2.2f)
                val driftX = (sin(rad) * (t * driftSpeed * 3.2f)).toFloat() % 40f - 20f
                val driftY = (-cos(rad) * (t * driftSpeed * 3.2f)).toFloat() % 40f - 20f

                if (env.W.rain > 0.1f || env.W.snow || env.W.lightning) {
                    // Cell 1
                    val c1x = rcx + 5f + driftX; val c1y = rcy - 3f + driftY
                    ctx.fillStyle = if (env.W.snow) "rgba(56, 189, 248, 0.7)" else "rgba(34, 197, 94, 0.75)"
                    ctx.beginPath(); ctx.arc(c1x, c1y, 11f, 0, PI * 2); ctx.fill()
                    ctx.fillStyle = if (env.W.snow) "rgba(224, 242, 254, 0.8)" else "rgba(234, 179, 8, 0.85)"
                    ctx.beginPath(); ctx.arc(c1x + 1.5f, c1y - 1f, 6.5f, 0, PI * 2); ctx.fill()
                    if (env.W.lightning || env.W.rain > 0.6f) {
                        ctx.fillStyle = "rgba(239, 68, 68, 0.9)"
                        ctx.beginPath(); ctx.arc(c1x + 2f, c1y - 1f, 3.2f, 0, PI * 2); ctx.fill()
                    }
                    // Cell 2
                    val c2x = rcx - 9f + driftX; val c2y = rcy + 6f + driftY
                    ctx.fillStyle = if (env.W.snow) "rgba(56, 189, 248, 0.55)" else "rgba(34, 197, 94, 0.6)"
                    ctx.beginPath(); ctx.arc(c2x, c2y, 8f, 0, PI * 2); ctx.fill()
                } else {
                    // Fair weather ground clutter dots
                    ctx.fillStyle = "rgba(34, 197, 94, 0.5)"
                    ctx.fillRect(rcx + 1f, rcy - 2f, 1.5f, 1.5f)
                    ctx.fillRect(rcx - 3f, rcy + 1f, 1.5f, 1.5f)
                    ctx.fillRect(rcx + 4f, rcy + 3f, 1.5f, 1.5f)
                }

                // Rotating radar sweep line
                val sweep = (t * 2.6f) % (2f * PI.toFloat())
                ctx.strokeStyle = "#86efac"; ctx.lineWidth = 1.2f
                ctx.beginPath()
                ctx.moveTo(rcx, rcy)
                ctx.lineTo(rcx + cos(sweep) * 23f, rcy + sin(sweep) * 23f)
                ctx.stroke()
                // Phosphor decay trail
                ctx.strokeStyle = "rgba(74, 222, 128, 0.35)"; ctx.lineWidth = 1.8f
                ctx.beginPath()
                ctx.moveTo(rcx, rcy)
                ctx.lineTo(rcx + cos(sweep - 0.12f) * 22f, rcy + sin(sweep - 0.12f) * 22f)
                ctx.stroke()

                // Radar badges
                ctx.fillStyle = "#facc15"; ctx.font = "bold 4.5px monospace"
                ctx.fillText("DOPPLER 4000", 5, 29)
                ctx.textAlign = "right"
                ctx.fillStyle = "#4ade80"
                ctx.fillText("50 MI", 57, 74)
                ctx.textAlign = "left"

                // Right: Atmosphere readings (x = 62, y = 24, w = 63, h = 52)
                ctx.fillStyle = "#0b1426"; ctx.fillRect(62, 24, 63, 52)
                ctx.strokeStyle = "#1e293b"; ctx.lineWidth = 1f; ctx.strokeRect(62, 24, 63, 52)

                // Condition pill
                ctx.fillStyle = "#1e3a8a"; ctx.fillRect(64, 26, 59, 8)
                ctx.fillStyle = "#fde047"; ctx.font = "bold 5px monospace"; ctx.textAlign = "center"
                ctx.fillText(env.W.label.take(13).uppercase(), 93, 32)
                ctx.textAlign = "left"

                // Large Temp & Feels Like
                ctx.fillStyle = "#ffffff"; ctx.font = "bold 11px monospace"
                ctx.fillText(tempText(), 65, 43)
                ctx.fillStyle = "#94a3b8"; ctx.font = "bold 4.5px monospace"
                ctx.fillText("FEELS LIKE $feelsLikeStr", 86, 40)

                // Stats Table
                ctx.fillStyle = "#f8fafc"; ctx.font = "5px monospace"
                ctx.fillText("WIND:  $windCompass $windSpeedStr", 64, 49)
                ctx.fillText("GUST:  $gustStr", 64, 55)
                ctx.fillText("HUMID: $humidity%", 64, 61)
                ctx.fillText("DEWPT: $dewPtStr", 64, 67)
                ctx.fillText("BARO:  $baroStr", 64, 73)
            }
            1 -> { // Slide 1: 5-Day Extended Local Outlook
                val colW = 23f; val colGap = 1.5f
                val dayOffsets = arrayOf(3.0 to -4.0, 1.0 to -5.5, 4.0 to -3.5, 2.0 to -6.0, 3.5 to -4.5)
                val basePop = (env.W.rain * 100).roundToInt().coerceIn(10, 95)
                val popList = intArrayOf(
                    basePop,
                    ((basePop * 0.7f + 25f) % 85f).toInt() + 5,
                    ((basePop * 0.4f + 15f) % 75f).toInt() + 5,
                    ((basePop * 0.85f + 10f) % 80f).toInt() + 5,
                    ((basePop * 0.6f + 20f) % 80f).toInt() + 5
                )
                val dowNames = arrayOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")

                for (i in 0 until 5) {
                    val cx = 3f + i * (colW + colGap)
                    ctx.fillStyle = "#0b1426"; ctx.fillRect(cx.toInt(), 24, colW.toInt(), 52)
                    ctx.strokeStyle = "#1e293b"; ctx.lineWidth = 1f; ctx.strokeRect(cx.toInt(), 24, colW.toInt(), 52)

                    // Day Header Badge
                    val dayName = if (i == 0) "TODAY" else dowNames[(env.dayOfWeek - 1 + i) % 7]
                    ctx.fillStyle = if (i == 0) "#1d4ed8" else "#1e293b"
                    ctx.fillRect(cx.toInt(), 24, colW.toInt(), 8)
                    ctx.fillStyle = "#ffffff"; ctx.font = "bold 4.5px monospace"; ctx.textAlign = "center"
                    ctx.fillText(dayName, cx + colW / 2, 30)

                    // Weather Icon
                    val iconType = when {
                        i == 0 && (env.W.lightning || env.W.rain > 0.7f) -> "storm"
                        i == 0 && env.W.rain > 0.2f -> "rain"
                        i == 0 && env.W.snow -> "snow"
                        i == 0 && env.W.cloud > 0.4f -> "cloud"
                        i == 0 -> "sun"
                        popList[i] >= 65 -> if (curMonth == 11 || curMonth in 0..1) "snow" else "storm"
                        popList[i] >= 40 -> "rain"
                        popList[i] >= 25 -> "cloud"
                        else -> "sun"
                    }
                    drawWeatherIcon(ctx, iconType, cx + colW / 2f, 39f)

                    // Short condition label
                    val condShort = when (iconType) {
                        "sun" -> "SUNNY"
                        "rain" -> "RAIN"
                        "storm" -> "STORM"
                        "snow" -> "SNOW"
                        else -> "CLOUD"
                    }
                    ctx.fillStyle = "#fde047"; ctx.font = "bold 4px monospace"
                    ctx.fillText(condShort, cx + colW / 2, 49)

                    // Temps High / Low
                    val hiC = cTemp + dayOffsets[i].first
                    val loC = cTemp + dayOffsets[i].second
                    val hiStr = if (tempUnit() == "F") "${(hiC * 1.8 + 32).roundToInt()}°" else "${hiC.roundToInt()}°"
                    val loStr = if (tempUnit() == "F") "${(loC * 1.8 + 32).roundToInt()}°" else "${loC.roundToInt()}°"
                    ctx.fillStyle = "#f87171"; ctx.font = "bold 4.5px monospace"
                    ctx.fillText("H $hiStr", cx + colW / 2, 57)
                    ctx.fillStyle = "#38bdf8"
                    ctx.fillText("L $loStr", cx + colW / 2, 64)

                    // Precip probability
                    ctx.fillStyle = "#93c5fd"; ctx.font = "bold 4px monospace"
                    ctx.fillText("${popList[i]}%", cx + colW / 2, 72)
                    ctx.textAlign = "left"
                }
            }
            2 -> { // Slide 2: Celestial Almanac & Lunar Phases
                // Left: Almanac & Moon (x = 3, y = 24, w = 58, h = 52)
                ctx.fillStyle = "#0b1426"; ctx.fillRect(3, 24, 58, 52)
                ctx.strokeStyle = "#1e293b"; ctx.lineWidth = 1f; ctx.strokeRect(3, 24, 58, 52)

                ctx.fillStyle = "#facc15"; ctx.font = "bold 5px monospace"
                ctx.fillText("CELESTIAL ALMANAC", 6, 31)

                // Sun times
                ctx.fillStyle = "#ffffff"; ctx.font = "5px monospace"
                ctx.fillText("SUNRISE  ${env.formatHour(env.sunrise)}", 6, 38)
                ctx.fillText("SUNSET   ${env.formatHour(env.sunset)}", 6, 45)

                // Pixel Moon Graphic
                drawMoonGraphic(ctx, 16f, 60f, 9f, env.moonPhase)

                // Moon Details
                ctx.fillStyle = "#ffffff"; ctx.font = "bold 5px monospace"
                val mPhase = moonPhaseName(env.moonPhase)
                val mShort = if (mPhase.length > 10) mPhase.take(10) else mPhase
                ctx.fillText(mShort, 29, 56)
                ctx.fillStyle = "#38bdf8"; ctx.font = "5px monospace"
                ctx.fillText("${(env.moonIllum * 100).roundToInt()}% ILLUM", 29, 63)
                ctx.fillStyle = "#4ade80"; ctx.font = "5px monospace"
                ctx.fillText("$windCompass $windSpeedStr", 29, 70)

                // Right: Atmospheric Telemetry & Solar Progress (x = 64, y = 24, w = 61, h = 52)
                ctx.fillStyle = "#0b1426"; ctx.fillRect(64, 24, 61, 52)
                ctx.strokeStyle = "#1e293b"; ctx.lineWidth = 1f; ctx.strokeRect(64, 24, 61, 52)

                ctx.fillStyle = "#facc15"; ctx.font = "bold 5px monospace"
                ctx.fillText("SOLAR & BARO", 67, 31)

                // Solar daylight meter
                val dayPct = (env.daylight * 100).roundToInt().coerceIn(0, 100)
                ctx.fillStyle = "#ffffff"; ctx.font = "5px monospace"
                ctx.fillText("DAYLIGHT $dayPct%", 67, 39)
                ctx.fillStyle = "#1e293b"; ctx.fillRect(67, 42, 54, 4)
                ctx.fillStyle = "#facc15"; ctx.fillRect(67, 42, (54 * (dayPct / 100f)).toInt(), 4)

                ctx.fillStyle = "#f8fafc"; ctx.font = "5px monospace"
                ctx.fillText("BARO:  $baroStr", 67, 54)
                ctx.fillText("HUMID: $humidity%", 67, 61)
                ctx.fillText("DEWPT: $dewPtStr", 67, 68)
            }
            3 -> { // Slide 3: Regional Holidays & Climate Records
                // Left: Upcoming Holiday Card (x = 3, y = 24, w = 60, h = 52)
                ctx.fillStyle = if (firstHoli.daysUntil <= 5) "#1e1b4b" else "#0b1426"
                ctx.fillRect(3, 24, 60, 52)
                ctx.strokeStyle = "#1e293b"; ctx.lineWidth = 1f; ctx.strokeRect(3, 24, 60, 52)

                ctx.fillStyle = "#facc15"; ctx.font = "bold 5px monospace"
                ctx.fillText("UPCOMING HOLIDAY", 6, 31)

                // Holiday Title
                ctx.fillStyle = "#ffffff"; ctx.font = "bold 6px monospace"
                ctx.fillText("${firstHoli.icon} ${firstHoli.name.take(9).uppercase()}", 6, 39)

                // Countdown badge
                ctx.fillStyle = firstHoli.themeCol; ctx.fillRect(6, 43, 54, 10)
                ctx.fillStyle = "#ffffff"; ctx.font = "bold 6px monospace"; ctx.textAlign = "center"
                val countText = when (firstHoli.daysUntil) {
                    0 -> "TODAY! 🎊"
                    1 -> "TOMORROW!"
                    else -> "${firstHoli.daysUntil} DAYS AWAY"
                }
                ctx.fillText(countText, 33, 50.5)
                ctx.textAlign = "left"

                // Holiday tagline & Secondary Holiday preview
                ctx.fillStyle = "#fde047"; ctx.font = "5px monospace"
                ctx.fillText(firstHoli.tag.take(13), 6, 60)
                ctx.fillStyle = "#94a3b8"; ctx.font = "4.5px monospace"
                ctx.fillText("THEN: ${secondHoli.name.take(6)} (${secondHoli.daysUntil}D)", 6, 69)

                // Right: Climate Normals (x = 65, y = 24, w = 60, h = 52)
                ctx.fillStyle = "#0b1426"; ctx.fillRect(65, 24, 60, 52)
                ctx.strokeStyle = "#1e293b"; ctx.lineWidth = 1f; ctx.strokeRect(65, 24, 60, 52)

                ctx.fillStyle = "#facc15"; ctx.font = "bold 5px monospace"
                ctx.fillText("$monthStr CLIMATE NORM", 68, 31)

                val avgHi = if (tempUnit() == "F") "68°F" else "20°C"
                val avgLo = if (tempUnit() == "F") "48°F" else "9°C"
                val recHi = if (tempUnit() == "F") "86°F" else "30°C"
                val recLo = if (tempUnit() == "F") "27°F" else "-3°C"

                ctx.fillStyle = "#ffffff"; ctx.font = "5px monospace"
                ctx.fillText("AVG HIGH: $avgHi", 68, 40)
                ctx.fillText("AVG LOW:  $avgLo", 68, 48)
                ctx.fillStyle = "#fca5a5"; ctx.fillText("REC HIGH: $recHi", 68, 56)
                ctx.fillStyle = "#93c5fd"; ctx.fillText("REC LOW:  $recLo", 68, 64)
                ctx.fillStyle = "#a7f3d0"; ctx.font = "bold 4.5px monospace"
                ctx.fillText("SEASON: ${env.currentSeason.uppercase()}", 68, 72)
            }
        }

        // 4. Always-On Lower-Third Double-Deck Crawler (y = 78..95)
        // Top deck (y = 78..85, height 7)
        ctx.fillStyle = "#080e1e"; ctx.fillRect(0, 78, w, 8)
        ctx.fillStyle = "#1d4ed8"; ctx.fillRect(0, 78, w, 1) // top border line
        ctx.fillStyle = "#f8fafc"; ctx.font = "bold 5px monospace"
        ctx.fillText("$dateStr  $timeStr", 4, 84)
        ctx.textAlign = "right"
        ctx.fillStyle = "#facc15"
        ctx.fillText("${cityText().take(9)} ${tempText()}", w - 4, 84)
        ctx.textAlign = "left"

        // Lower deck (y = 86..95, height 10)
        val alertMode = env.W.lightning || windSpeedKmH > 35f
        ctx.fillStyle = if (alertMode) "#7f1d1d" else "#1e3a8a"; ctx.fillRect(0, 86, w, 10)

        // Left TWC badge
        ctx.fillStyle = if (alertMode) "#dc2626" else "#d97706"; ctx.fillRect(2, 87, 18, 8)
        ctx.fillStyle = "#ffffff"; ctx.font = "bold 5px monospace"; ctx.textAlign = "center"
        ctx.fillText("TWC ►", 11, 93)
        ctx.textAlign = "left"

        // Marquee scrolling ticker
        val ticker = "${cityText()} · ${env.W.label.uppercase()} ${tempText()} · WIND $windCompass $windSpeedStr GUST $gustStr · BARO $baroStr · HUMIDITY $humidity% · MOON ${moonPhaseName(env.moonPhase)} ${(env.moonIllum * 100).roundToInt()}% · SUNSET ${env.formatHour(env.sunset)} · ${firstHoli.icon} ${firstHoli.name.uppercase()} IN ${firstHoli.daysUntil} DAYS · ${firstHoli.tag} · LOCAL ON THE 8s   "
        ctx.font = "5px monospace"
        val tw = max(10f, ctx.measureText(ticker))
        val scrollX = 22f - ((t * 22f) % tw)

        ctx.save()
        ctx.beginPath()
        ctx.rect(22, 86, w - 24, 10)
        ctx.clip()
        ctx.fillStyle = "#ffffff"
        ctx.fillText(ticker, scrollX, 93)
        ctx.fillText(ticker, scrollX + tw, 93)
        ctx.restore()
    }

    /**
     * High-legibility Weather Channel Simple View:
     * Specially designed with bold 26px temperature, prominent animated 28px weather icon,
     * high-contrast condition ribbon, and lower status strips so conditions are immediately
     * legible from across the bedroom in the default room camera view.
     */
    private fun drawSimpleWeatherView(
        ctx: Canvas2D, w: Int, h: Int, t: Float,
        dateStr: String, timeStr: String,
        themeHeaderCol: String, firstHoli: HolidayEvent,
        cTemp: Double, feelsLikeStr: String,
        windCompass: String, windSpeedStr: String,
        curMonth: Int
    ) {
        // 1. Background: Deep rich retro CRT indigo-navy
        ctx.fillStyle = "#050a16"; ctx.fillRect(0, 0, w, h)

        // 2. Header Banner (y = 0..13)
        ctx.fillStyle = themeHeaderCol; ctx.fillRect(0, 0, w, 13)
        ctx.fillStyle = "#facc15"; ctx.fillRect(0, 13, w, 1)

        // Left badge
        ctx.fillStyle = "#003399"; ctx.fillRect(3, 2, 28, 9)
        ctx.strokeStyle = "#ffffff"; ctx.lineWidth = 0.8f; ctx.strokeRect(3, 2, 28, 9)
        ctx.fillStyle = "#ffffff"; ctx.font = "bold 6px monospace"; ctx.textAlign = "center"
        ctx.fillText("WEATHER", 17, 8.5)
        ctx.textAlign = "left"

        // Right city & time (large and crisp)
        ctx.textAlign = "right"
        ctx.fillStyle = "#ffffff"; ctx.font = "bold 6.5px monospace"
        ctx.fillText("${cityText().take(8)} $timeStr", w - 4, 9)
        ctx.textAlign = "left"

        // 3. Main Center Display (y = 15..50)
        // Weather Icon (large 28x28 centered at cx = 23, cy = 32)
        val iconType = when {
            env.W.lightning || env.W.rain > 0.7f -> "storm"
            env.W.rain > 0.2f -> "rain"
            env.W.snow -> "snow"
            env.W.fog > 0.4f -> "fog"
            env.W.cloud > 0.4f -> "cloud"
            else -> "sun"
        }
        drawLargeWeatherIcon(ctx, iconType, 23f, 32f, t)

        // Giant 28px Temperature (bold white, with shadow for CRT pop)
        ctx.textAlign = "left"
        ctx.fillStyle = "rgba(0,0,0,0.75)"
        ctx.font = "bold 28px monospace"
        ctx.fillText(tempText(), 52, 38) // drop shadow
        ctx.fillStyle = "#ffffff"
        ctx.fillText(tempText(), 50, 37)

        // Feels like tag
        ctx.fillStyle = "#93c5fd"; ctx.font = "bold 6px monospace"
        ctx.fillText("FEEL $feelsLikeStr", 52, 19)

        // High / Low estimation
        val hiC = cTemp + 3.0; val loC = cTemp - 4.0
        val hiStr = if (tempUnit() == "F") "${(hiC * 1.8 + 32).roundToInt()}°" else "${hiC.roundToInt()}°"
        val loStr = if (tempUnit() == "F") "${(loC * 1.8 + 32).roundToInt()}°" else "${loC.roundToInt()}°"
        ctx.fillStyle = "#f87171"; ctx.font = "bold 6.5px monospace"
        ctx.fillText("▲ $hiStr", 52, 47)
        ctx.fillStyle = "#38bdf8"
        ctx.fillText("▼ $loStr", 86, 47)

        // 4. Prominent Weather Condition Banner (y = 51..65)
        ctx.fillStyle = "#1e3a8a"; ctx.fillRect(2, 51, w - 4, 14)
        ctx.strokeStyle = "#38bdf8"; ctx.lineWidth = 1f; ctx.strokeRect(2, 51, w - 4, 14)
        ctx.fillStyle = "#000000"; ctx.font = "bold 9px monospace"; ctx.textAlign = "center"
        ctx.fillText(env.W.label.take(15).uppercase(), w / 2 + 0.5f, 62f)
        ctx.fillStyle = "#fef08a"
        ctx.fillText(env.W.label.take(15).uppercase(), w / 2, 61.5)
        ctx.textAlign = "left"

        // 5. High-Contrast Lower Bars (y = 66..96)
        // Bar 1: Day/Date & Wind Vector (y = 66..80, height 14)
        val windDeg = if (env.liveWindDirDeg >= 0f) env.liveWindDirDeg else 225f
        val windArrow = when ((((windDeg + 22.5f) / 45f).toInt()) % 8) {
            0 -> "↓"
            1 -> "↙"
            2 -> "←"
            3 -> "↖"
            4 -> "↑"
            5 -> "↗"
            6 -> "→"
            else -> "↘"
        }
        ctx.fillStyle = "#091428"; ctx.fillRect(0, 66, w, 14)
        ctx.fillStyle = "#1e293b"; ctx.fillRect(0, 66, w, 1)
        ctx.fillStyle = "#ffffff"; ctx.font = "bold 6.5px monospace"
        ctx.fillText(dateStr, 4, 76.5)
        ctx.textAlign = "right"
        ctx.fillStyle = "#4ade80"; ctx.font = "bold 6.5px monospace"
        ctx.fillText("$windArrow $windCompass $windSpeedStr", w - 4, 76.5)
        ctx.textAlign = "left"

        // Bar 2: Moon Phase & Holiday countdown (y = 81..96, height 15)
        ctx.fillStyle = if (env.W.lightning) "#7f1d1d" else "#0c254c"
        ctx.fillRect(0, 81, w, 15)
        ctx.fillStyle = "#38bdf8"; ctx.fillRect(0, 81, w, 1)
        ctx.fillStyle = "#93c5fd"; ctx.font = "bold 6px monospace"
        val moonGlyph = when {
            env.moonIllum < 0.10 -> "🌑"
            env.moonPhase < 0.50 -> "🌓"
            env.moonIllum > 0.85 -> "🌕"
            else -> "🌗"
        }
        ctx.fillText("$moonGlyph ${(env.moonIllum * 100).roundToInt()}%", 4, 91.5)
        ctx.textAlign = "right"
        ctx.fillStyle = "#fde047"; ctx.font = "bold 6px monospace"
        val holiText = if (firstHoli.daysUntil == 0) "${firstHoli.icon} TODAY!" else "${firstHoli.icon} ${firstHoli.name.take(6).uppercase()} ${firstHoli.daysUntil}D"
        ctx.fillText(holiText, w - 4, 91.5)
        ctx.textAlign = "left"
    }

    private fun drawLargeWeatherIcon(ctx: Canvas2D, type: String, cx: Float, cy: Float, t: Float) {
        val windDeg = if (env.liveWindDirDeg >= 0f) env.liveWindDirDeg else 225f
        val slantX = sin(Math.toRadians(windDeg.toDouble())).toFloat() * 3.5f

        when (type) {
            "sun" -> {
                val isNight = env.daylight < 0.25f
                if (isNight) {
                    drawMoonGraphic(ctx, cx, cy, 11f, env.moonPhase)
                    ctx.fillStyle = "#ffffff"
                    val starTwinkle = (sin(t * 5f) * 0.5f + 0.5f)
                    if (starTwinkle > 0.3f) {
                        ctx.fillRect(cx + 10f, cy - 8f, 1.5f, 1.5f)
                        ctx.fillRect(cx - 9f, cy + 7f, 1.5f, 1.5f)
                        ctx.fillRect(cx + 8f, cy + 9f, 1f, 1f)
                    }
                } else {
                    ctx.fillStyle = "#facc15"
                    ctx.beginPath(); ctx.arc(cx, cy, 9f, 0, PI * 2); ctx.fill()
                    ctx.fillStyle = "#fef08a"
                    ctx.beginPath(); ctx.arc(cx, cy, 6.5f, 0, PI * 2); ctx.fill()
                    ctx.strokeStyle = "#fbbf24"; ctx.lineWidth = 1.6f
                    for (i in 0 until 8) {
                        val a = i * (PI / 4).toFloat() + t * 0.45f
                        val r1 = 11f; val r2 = 14.5f + sin(t * 3f + i) * 1.5f
                        ctx.beginPath()
                        ctx.moveTo(cx + cos(a) * r1, cy + sin(a) * r1)
                        ctx.lineTo(cx + cos(a) * r2, cy + sin(a) * r2)
                        ctx.stroke()
                    }
                }
            }
            "cloud" -> {
                ctx.fillStyle = "#475569"
                ctx.beginPath(); ctx.arc(cx - 5f, cy + 1f, 8f, 0, PI * 2); ctx.arc(cx + 5f, cy + 1f, 7f, 0, PI * 2); ctx.arc(cx, cy - 4f, 8f, 0, PI * 2); ctx.fill()
                ctx.fillStyle = "#cbd5e1"
                ctx.beginPath(); ctx.arc(cx - 4f, cy, 7.5f, 0, PI * 2); ctx.arc(cx + 4f, cy, 6.5f, 0, PI * 2); ctx.arc(cx, cy - 3f, 7f, 0, PI * 2); ctx.fill()
            }
            "rain" -> {
                ctx.fillStyle = "#334155"
                ctx.beginPath(); ctx.arc(cx - 5f, cy - 3f, 8.5f, 0, PI * 2); ctx.arc(cx + 5f, cy - 3f, 7.5f, 0, PI * 2); ctx.arc(cx, cy - 7f, 7.5f, 0, PI * 2); ctx.fill()
                ctx.strokeStyle = "#38bdf8"; ctx.lineWidth = 1.6f
                for (i in -1..1) {
                    val rx = cx + i * 7f
                    val ry = cy + 4f + ((t * 24f + i * 5f) % 11f)
                    ctx.beginPath()
                    ctx.moveTo(rx, ry)
                    ctx.lineTo(rx - slantX, ry + 4.5f)
                    ctx.stroke()
                }
            }
            "storm" -> {
                ctx.fillStyle = "#1e1b4b"
                ctx.beginPath(); ctx.arc(cx - 5f, cy - 3f, 8.5f, 0, PI * 2); ctx.arc(cx + 5f, cy - 3f, 7.5f, 0, PI * 2); ctx.arc(cx, cy - 7f, 7.5f, 0, PI * 2); ctx.fill()
                val flash = env.W.lightning || (sin(t * 7.5f) > 0.35f)
                if (flash) {
                    ctx.fillStyle = "#fde047"
                    ctx.beginPath()
                    ctx.moveTo(cx + 1f, cy - 2f)
                    ctx.lineTo(cx - 4f, cy + 5f)
                    ctx.lineTo(cx - 1f, cy + 5f)
                    ctx.lineTo(cx - 3.5f, cy + 13f)
                    ctx.lineTo(cx + 3.5f, cy + 4f)
                    ctx.lineTo(cx + 0.5f, cy + 4f)
                    ctx.closePath()
                    ctx.fill()
                    ctx.strokeStyle = "#ffffff"; ctx.lineWidth = 0.8f; ctx.stroke()
                } else {
                    ctx.strokeStyle = "#38bdf8"; ctx.lineWidth = 1.5f
                    for (i in -1..1) {
                        val rx = cx + i * 7f
                        val ry = cy + 5f + ((t * 26f + i * 5f) % 9f)
                        ctx.beginPath(); ctx.moveTo(rx, ry); ctx.lineTo(rx - slantX, ry + 4f); ctx.stroke()
                    }
                }
            }
            "snow" -> {
                ctx.fillStyle = "#334155"
                ctx.beginPath(); ctx.arc(cx - 5f, cy - 3f, 8f, 0, PI * 2); ctx.arc(cx + 5f, cy - 3f, 7f, 0, PI * 2); ctx.arc(cx, cy - 7f, 7f, 0, PI * 2); ctx.fill()
                ctx.fillStyle = "#ffffff"
                for (i in -1..1) {
                    val sx = cx + i * 7f + sin(t * 3f + i) * 2f - slantX * 0.5f
                    val sy = cy + 4f + ((t * 9f + i * 4f) % 11f)
                    ctx.fillRect(sx - 1f, sy - 1f, 2f, 2f)
                    ctx.fillRect(sx, sy - 2f, 1f, 4f)
                    ctx.fillRect(sx - 2f, sy, 4f, 1f)
                }
            }
            "fog" -> {
                ctx.fillStyle = "rgba(148, 163, 184, 0.85)"
                val drift = sin(t * 2f) * 2f
                ctx.fillRect(cx - 13f + drift, cy - 6f, 26f, 3.5f)
                ctx.fillRect(cx - 16f - drift, cy, 32f, 3.5f)
                ctx.fillRect(cx - 11f + drift * 0.7f, cy + 6f, 22f, 3.5f)
            }
        }
    }
}
