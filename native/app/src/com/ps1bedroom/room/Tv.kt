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
            "CH 04: Late-Night 90s Anime Broadcast", "CH 12: 90s Weather Channel (Local Radar)", "CH 03: VCR (whatever tape is in the deck)",
            "CH 24: Midnight Retro Shopping Network", "CH 99: Snowy CRT No-Signal Static", "CH 06: Saturday-Morning Cartoons",
            "CH 32: Late-Night Music Video Countdown", "AV 1: PlayStation Video Input"
        )
        val TV_CHANNEL_COLORS = intArrayOf(0xec4899, 0x38bdf8, 0x22c55e, 0xf59e0b, 0x94a3b8, 0x7dd3fc, 0xc084fc, 0xfb923c)
        val PS1_TV_CHANNEL = TV_CHANNEL_NAMES.size - 1 // the A/V input is always the last channel
    }
    var bedroomTvOn = true; private set
    var bedroomTvChannel = 0; private set
    /** The PlayStation is on (it adds A/V 1 to the rotation): comes with the console phase. */
    var ps1On = false
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
            val label = Mesh(Geo.box(0.16f, 0.018f, 0.002f), PS1.material(color = 0xffffff, map = PS1.grimeTextures[0]))
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
        val tvScreenMesh = Mesh(tvScreenGeo, tvScreenMat)
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
        tvScreenMat.emissiveIntensity = if (bedroomTvOn) 0.95f else 0.04f
        room.bedroomTvLight.intensity = if (bedroomTvOn) 1.15f else 0.0f
        tvLedMat.emissiveIntensity = if (bedroomTvOn) 1.0f else 0.05f
        updateBedroomTvScreen(t) // the screen isn't redrawn while the TV is off, so blank (or restore) it once here
    }
    fun cycleBedroomTvChannel(t: Float) {
        if (!bedroomTvOn) { toggleBedroomTv(t); return }
        // The A/V input is only in the rotation while the PlayStation is on; flipping past the last channel turns the TV off
        val next = (bedroomTvChannel + 1) % (if (ps1On) TV_CHANNEL_NAMES.size else PS1_TV_CHANNEL)
        if (next == 0) { bedroomTvChannel = 0; toggleBedroomTv(t); return }
        bedroomTvChannel = next
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
        if (!bedroomTvOn) {
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
            1 -> { // 90s weather channel, local on the 8s
                ctx.fillStyle = "#1e3a8a"; ctx.fillRect(0, 0, w, h)
                ctx.fillStyle = "#d97706"; ctx.fillRect(0, 0, w, 16)
                ctx.fillStyle = "#ffffff"; ctx.font = "bold 8px sans-serif"; ctx.fillText("LOCALDOPPLER RADAR", 8, 11)
                ctx.fillStyle = "#0f172a"; ctx.fillRect(10, 20, 72, 56)
                val rx = 38 + sin(t * 0.8) * 12; val ry = 46 + cos(t * 0.6) * 6
                ctx.fillStyle = "#22c55e"; ctx.beginPath(); ctx.arc(rx, ry, 18, 0, PI * 2); ctx.fill()
                ctx.fillStyle = "#eab308"; ctx.beginPath(); ctx.arc(rx + 2, ry - 1, 10, 0, PI * 2); ctx.fill()
                ctx.fillStyle = "#ef4444"; ctx.beginPath(); ctx.arc(rx + 3, ry - 2, 5, 0, PI * 2); ctx.fill()
                ctx.fillStyle = "#172554"; ctx.fillRect(86, 20, 34, 56)
                // Forecast follows the actual season / weather / time settings
                val forecast = env.W.label.uppercase().take(9)
                val temp = mapOf("summer" to "74°F", "fall" to "52°F", "winter" to "27°F", "spring" to "61°F").getValue(env.currentSeason)
                ctx.fillStyle = "#fde047"; ctx.font = "bold 7px monospace"
                ctx.fillText(if (env.currentTime == "day") "TODAY" else "TONIGHT", 88, 30)
                ctx.fillStyle = "#ffffff"; ctx.fillText(temp, 92, 42); ctx.fillText(forecast, 89, 54)
                ctx.fillStyle = "#1e40af"; ctx.fillRect(0, 80, w, 16)
                ctx.fillStyle = "#f8fafc"
                val tick = "LOCAL TIME ${env.formatHour(env.hour)} · SUNSET ${env.formatHour(env.sunset % 24)} · MOON ${(env.moonIllum * 100).roundToInt()}% · BAROMETER 29.92 IN   "
                ctx.fillText(tick + tick, 6 - ((t * 18) % (tick.length * 4.3)), 90)
            }
            2 -> { // VHS sci-fi / horror movie with tracking bars (the tapes you can insert come with the VCR tapes)
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
            PS1_TV_CHANNEL -> { // A/V 1 with the PlayStation off (its boot, menu and games come with the console)
                ctx.fillStyle = "#1e3a8a"; ctx.fillRect(0, 0, w, h)
                ctx.fillStyle = "#f8fafc"; ctx.font = "bold 9px monospace"
                ctx.textAlign = "left"; ctx.fillText("AV 1", 8, 14)
                ctx.textAlign = "center"; ctx.fillText("NO SIGNAL", 64, 52)
                ctx.textAlign = "left"
            }
            else -> { // Snowy CRT static
                var y = 0
                while (y < h) {
                    var x = 0
                    while (x < w) { val v = (rnd.nextDouble() * 215 + 25).toInt(); ctx.fillRectArgb(x, y, 3, 3, (0xff shl 24) or (v shl 16) or (v shl 8) or min(255, v + 20)); x += 3 }
                    y += 3
                }
                ctx.fillStyle = "#22c55e"; ctx.font = "bold 9px monospace"; ctx.fillText("CH 03", 8, 16)
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
}
