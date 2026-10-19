package com.ps1bedroom.room

import com.ps1bedroom.room.engine.CanvasTex
import com.ps1bedroom.room.engine.Canvas2D
import com.ps1bedroom.room.engine.Geo
import com.ps1bedroom.room.engine.Geometry
import com.ps1bedroom.room.engine.Group
import com.ps1bedroom.room.engine.Material
import com.ps1bedroom.room.engine.Mesh
import com.ps1bedroom.room.engine.PS1
import com.ps1bedroom.room.engine.Weather
import com.ps1bedroom.room.engine.createCanvasTex
import com.ps1bedroom.room.engine.rand
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * The office chair, the bed with its clock radio, and the wall shelf over it (HTML lines 7535-7998).
 */
class Bedroom(private val room: Room) {
    lateinit var chairGroup: Group
    lateinit var bedGroup: Group
    private lateinit var alarmClockObj: CanvasTex
    private var alarmClockShown = ""
    private lateinit var catCanvasObj: CanvasTex
    var catEyesGlowing = false; private set

    // ---- Textures (HTML 1531-1655) ----
    val spaceBedTex = createCanvasTex(256, 256, 2f, 2f, Weather(seed = 45.0, blotch = 0.24, speckle = 0.09)) { ctx, w, h ->
        ctx.fillStyle = "#06102e"; ctx.fillRect(0, 0, w, h)
        // Subtle purple/blue cosmic nebula clouds
        val neb1 = ctx.createRadialGradient(70, 90, 8, 70, 90, 68)
        neb1.addColorStop(0, "rgba(88, 28, 135, 0.42)"); neb1.addColorStop(0.5, "rgba(30, 58, 138, 0.25)"); neb1.addColorStop(1, "rgba(6, 16, 46, 0)")
        ctx.fillStyle = neb1; ctx.fillRect(0, 0, w, h)
        val neb2 = ctx.createRadialGradient(190, 175, 10, 190, 175, 75)
        neb2.addColorStop(0, "rgba(14, 116, 144, 0.35)"); neb2.addColorStop(0.6, "rgba(49, 46, 129, 0.2)"); neb2.addColorStop(1, "rgba(6, 16, 46, 0)")
        ctx.fillStyle = neb2; ctx.fillRect(0, 0, w, h)
        // Quilted stitching lines & soft fold shadows
        var q = 0
        while (q < w) {
            ctx.strokeStyle = "rgba(2, 6, 23, 0.45)"; ctx.lineWidth = 1.5f
            ctx.beginPath(); ctx.moveTo(q, 0); ctx.lineTo(q, h); ctx.stroke()
            ctx.beginPath(); ctx.moveTo(0, q); ctx.lineTo(w, q); ctx.stroke()
            q += 32
        }
        // Starfield & 4-point pixel sparkles
        for (i in 0 until 340) {
            val sx = (i * 73 + (i % 7) * 19) % w; val sy = (i * 41 + (i % 5) * 29) % h
            ctx.fillStyle = if (i % 5 == 0) "#fde047" else (if (i % 3 == 0) "#93c5fd" else "#ffffff")
            ctx.fillRect(sx, sy, if (i % 9 == 0) 2 else 1, if (i % 9 == 0) 2 else 1)
            if (i % 18 == 0) { ctx.fillRect(sx - 2, sy, 5, 1); ctx.fillRect(sx, sy - 2, 1, 5) }
        }
        // Shooting star trails
        ctx.strokeStyle = "rgba(254, 240, 138, 0.65)"; ctx.lineWidth = 1.5f
        ctx.beginPath(); ctx.moveTo(140, 28); ctx.lineTo(168, 42); ctx.stroke()
        ctx.beginPath(); ctx.moveTo(32, 195); ctx.lineTo(58, 208); ctx.stroke()
        // Ringed planets
        fun drawPlanet(px: Int, py: Int, r: Int, col: String, stripeCol: String, ringCol: String, tilt: Double) {
            ctx.save(); ctx.translate(px, py)
            ctx.strokeStyle = ringCol; ctx.lineWidth = 3f
            ctx.beginPath(); ctx.ellipse(0, 0, r * 2.25, r * 0.65, tilt, PI, PI * 2); ctx.stroke()
            ctx.fillStyle = col; ctx.beginPath(); ctx.arc(0, 0, r, 0, PI * 2); ctx.fill()
            ctx.fillStyle = stripeCol; ctx.fillRect(-r + 2, -r * 0.25, r * 2 - 4, r * 0.45)
            ctx.beginPath(); ctx.ellipse(0, 0, r * 2.25, r * 0.65, tilt, 0, PI); ctx.stroke()
            ctx.restore()
        }
        drawPlanet(54, 58, 15, "#2563eb", "#60a5fa", "#bfdbfe", -0.32)
        drawPlanet(186, 88, 19, "#7c3aed", "#a78bfa", "#e9d5ff", 0.26)
        drawPlanet(92, 184, 17, "#0284c7", "#38bdf8", "#bae6fd", -0.22)
        drawPlanet(204, 208, 14, "#d97706", "#fbbf24", "#fde68a", 0.35)
        // Golden crescent moon motif
        ctx.fillStyle = "#facc15"; ctx.beginPath(); ctx.arc(128, 118, 12, 0, PI * 2); ctx.fill()
        ctx.fillStyle = "#06102e"; ctx.beginPath(); ctx.arc(133, 115, 10, 0, PI * 2); ctx.fill()
    }.tex
    private val sheetStripeTex = createCanvasTex(64, 64, 4f, 4f, Weather(seed = 46.0, blotch = 0.26, speckle = 0.06)) { ctx, w, h ->
        ctx.fillStyle = "#cbd5e1"; ctx.fillRect(0, 0, w, h)
        ctx.fillStyle = "#94a3b8"
        var x = 0; while (x < w) { ctx.fillRect(x, 0, 3, h); x += 8 }
        var y = 0; while (y < h) { ctx.fillStyle = "rgba(15, 23, 42, 0.05)"; ctx.fillRect(0, y, w, 2); y += 4 }
    }.tex
    private val pillowFabricTex = createCanvasTex(64, 64, weather = Weather(seed = 47.0, blotch = 0.22, speckle = 0.07, stains = 1, warm = 0.25)) { ctx, w, h ->
        ctx.fillStyle = "#dce6ff"; ctx.fillRect(0, 0, w, h)
        ctx.strokeStyle = "#94a3b8"; ctx.lineWidth = 2f
        ctx.beginPath(); ctx.moveTo(8, 8); ctx.lineTo(26, 24); ctx.stroke()
        ctx.beginPath(); ctx.moveTo(56, 10); ctx.lineTo(38, 24); ctx.stroke()
        ctx.beginPath(); ctx.moveTo(10, 54); ctx.lineTo(28, 40); ctx.stroke()
        ctx.beginPath(); ctx.moveTo(54, 54); ctx.lineTo(36, 40); ctx.stroke()
        ctx.strokeStyle = "#b4c6f0"; ctx.strokeRect(2, 2, w - 4, h - 4)
    }.tex
    private val chairTex = createCanvasTex(64, 64, 2f, 2f, Weather(seed = 48.0, blotch = 0.26, speckle = 0.12)) { ctx, w, h ->
        ctx.fillStyle = "#101d42"; ctx.fillRect(0, 0, w, h)
        val colors = arrayOf("#243b7a", "#3a629c", "#6a4c9c", "#25727a", "#9a7b3e")
        for (i in 0 until 380) { ctx.fillStyle = colors[i % colors.size]; ctx.fillRect((rand() * w).toInt(), (rand() * h).toInt(), 2, 2) }
    }.tex

    fun build() {
        val scene = room.scene
        val matWood = room.matWood; val matDarkPlastic = room.matDarkPlastic

        // ---- 2001 office chair: five-star base with twin-wheel casters, gas lift with bellows, contoured seat,
        //      curved lumbar backrest with a plastic back shell, T-armrests with soft pads, seat-height lever ----
        PS1.syncGrime(236)
        chairGroup = Group()
        chairGroup.position.set(-0.25f, 0f, 0.9f) // starts in front of the TV (CHAIR_SPOTS[0])
        chairGroup.rotation.y = -1.62f
        scene.add(chairGroup)
        val matChairFabric = PS1.material(map = chairTex)
        val matChairShell = PS1.material(color = 0x1c1f27)
        val matChairChrome = PS1.material(color = 0x9ca3af)
        fun chairPart(geo: Geometry, mat: Material, x: Float, y: Float, z: Float, tag: String? = null): Mesh {
            val m = Mesh(geo, mat)
            m.position.set(x, y, z); m.castShadow = true; m.receiveShadow = true
            chairGroup.add(m)
            if (tag != null) room.registerInteractable(m, tag)
            return m
        }
        chairPart(Geo.cylinder(0.075f, 0.09f, 0.08f, 10), matChairShell, 0f, 0.13f, 0f)
        for (i in 0 until 5) {
            val a = (i * PI * 2 / 5).toFloat()
            val armGeo = Geo.box(0.075f, 0.05f, 0.36f, 1, 1, 4)
            val ap = armGeo.positions
            for (v in 0 until armGeo.vertexCount) {
                val t01 = (ap[v * 3 + 2] + 0.18f) / 0.36f // 0 at hub, 1 at tip
                ap[v * 3] = ap[v * 3] * (1 - 0.45f * t01)
                ap[v * 3 + 1] = ap[v * 3 + 1] * (1 - 0.3f * t01) - 0.035f * t01
            }
            armGeo.computeVertexNormals()
            val arm = chairPart(armGeo, matChairShell, sin(a) * 0.2f, 0.12f, cos(a) * 0.2f)
            arm.rotation.y = a
            // Twin-wheel caster: swivel fork + two wheels
            val cx = sin(a) * 0.37f; val cz = cos(a) * 0.37f
            chairPart(Geo.box(0.03f, 0.05f, 0.05f), matChairShell, cx, 0.075f, cz)
            for (off in floatArrayOf(-0.022f, 0.022f)) {
                val wheel = chairPart(Geo.cylinder(0.035f, 0.035f, 0.02f, 10), matDarkPlastic, cx + cos(a) * off, 0.036f, cz - sin(a) * off)
                wheel.rotation.z = (PI / 2).toFloat(); wheel.rotation.y = a
            }
        }
        // Gas lift: chrome cylinder, ribbed rubber bellows, sleeve
        chairPart(Geo.cylinder(0.028f, 0.028f, 0.47f, 8), matChairChrome, 0f, 0.365f, 0f)
        for (r in 0 until 6) { val rr = if (r % 2 == 1) 0.04f else 0.047f; chairPart(Geo.cylinder(rr, rr, 0.03f, 10), matDarkPlastic, 0f, 0.2f + r * 0.03f, 0f) }
        chairPart(Geo.cylinder(0.045f, 0.045f, 0.24f, 8), matChairShell, 0f, 0.68f, 0f)
        // Seat mechanism with the height lever
        chairPart(Geo.box(0.3f, 0.07f, 0.32f), matChairShell, 0f, 0.83f, 0f)
        val lever = chairPart(Geo.cylinder(0.008f, 0.008f, 0.22f, 6), matChairShell, 0.24f, 0.82f, 0.05f)
        lever.rotation.z = (PI / 2 - 0.15).toFloat() // (native fix: droops down to the ball; the HTML tilted it up, away from it)
        chairPart(Geo.sphere(0.02f, 6, 5), matDarkPlastic, 0.35f, 0.8f, 0.05f)
        // Contoured seat cushion: rounded edges, waterfall front, slight dish where you sit
        val seatGeo = Geo.box(0.8f, 0.12f, 0.78f, 8, 2, 8)
        run {
            val sp = seatGeo.positions
            for (v in 0 until seatGeo.vertexCount) {
                val nx = sp[v * 3] / 0.4; val nz = sp[v * 3 + 2] / 0.39; val y = sp[v * 3 + 1]
                if (y > 0) {
                    val edge = max(abs(nx), abs(nz))
                    var ny = y - 0.035 * (max(0.0, edge - 0.6) / 0.4).pow(2)                       // rounded rim
                    ny -= 0.022 * max(0.0, 1 - (nx * nx + (nz + 0.1) * (nz + 0.1)) * 1.6)          // seat dish
                    if (nz > 0.7) ny -= 0.04 * (nz - 0.7) / 0.3                                      // waterfall front edge
                    sp[v * 3 + 1] = ny.toFloat()
                }
            }
            seatGeo.computeVertexNormals()
        }
        chairPart(seatGeo, matChairFabric, 0f, 0.94f, 0.02f, "chair")
        chairPart(Geo.box(0.76f, 0.03f, 0.74f), matChairShell, 0f, 0.875f, 0.02f)
        // Backrest: wraps around the sitter, lumbar bulge, narrower at the top; plastic shell behind it
        fun makeBackGeo(w: Float, h: Float, d: Float, bulge: Float): Geometry {
            val g = Geo.box(w, h, d, 8, 10, 1)
            val p = g.positions
            for (v in 0 until g.vertexCount) {
                val x = p[v * 3]; val y = p[v * 3 + 1]
                val ty = (y + h / 2) / h
                p[v * 3] = x * (1 - 0.14f * ty)
                p[v * 3 + 2] = (p[v * 3 + 2] - 0.09 * (x / (w / 2.0)).pow(2) + bulge * exp(-((ty - 0.28) / 0.18).pow(2))).toFloat()
            }
            g.computeVertexNormals()
            return g
        }
        val chairBack = chairPart(makeBackGeo(0.72f, 0.85f, 0.09f, 0.035f), matChairFabric, 0f, 1.5f, -0.34f, "chair")
        chairBack.rotation.x = -0.1f
        val backShell = chairPart(makeBackGeo(0.75f, 0.88f, 0.03f, 0.03f), matChairShell, 0f, 1.5f, -0.4f)
        backShell.rotation.x = -0.1f
        // Spine: from the back of the seat mechanism up into the bottom of the back shell, both ends buried
        // (native fix: the HTML's spine leaned the other way, so it floated behind the backrest)
        val spine = chairPart(Geo.box(0.1f, 0.48f, 0.05f), matChairShell, 0f, 0.975f, -0.235f)
        spine.rotation.x = -0.75f
        // T-armrests with soft pads
        for (side in intArrayOf(-1, 1)) {
            chairPart(Geo.box(0.16f, 0.035f, 0.06f), matChairShell, side * 0.33f, 0.86f, -0.02f)
            chairPart(Geo.box(0.05f, 0.3f, 0.07f), matChairShell, side * 0.42f, 1.03f, -0.02f)
            chairPart(Geo.box(0.09f, 0.045f, 0.32f, 1, 1, 3), matDarkPlastic, side * 0.42f, 1.2f, 0.0f)
        }

        // ---- The bed ----
        bedGroup = Group()
        bedGroup.position.set(2.85f, 0f, -0.45f)
        scene.add(bedGroup)
        val matDarkWood = PS1.material(color = 0x261710)
        val matSheet = PS1.material(map = sheetStripeTex)
        val pillowMat = PS1.material(map = pillowFabricTex)
        fun bedMesh(g: Geometry, m: Material, x: Float, y: Float, z: Float, cast: Boolean = true, receive: Boolean = true, tag: String? = null): Mesh {
            val mesh = Mesh(g, m); mesh.position.set(x, y, z); mesh.castShadow = cast; mesh.receiveShadow = receive
            bedGroup.add(mesh); if (tag != null) room.registerInteractable(mesh, tag); return mesh
        }
        // 1. Bookcase headboard with turned corner posts & finials
        bedMesh(Geo.box(2.48f, 1.75f, 0.10f, 6, 4, 1), matWood, 0f, 0.96f, -2.24f, tag = "bed")
        for (p in -4..4) bedMesh(Geo.box(0.22f, 0.72f, 0.03f, 1, 2, 1), matDarkWood, p * 0.25f, 1.18f, -2.18f, cast = false, tag = "bed")
        bedMesh(Geo.box(2.54f, 0.06f, 0.28f, 6, 1, 2), matWood, 0f, 1.48f, -2.15f, tag = "bed")
        bedMesh(Geo.box(2.62f, 0.09f, 0.32f, 6, 1, 2), matWood, 0f, 1.84f, -2.14f, tag = "bed")
        for (dx in floatArrayOf(-1.21f, -0.38f, 0.38f, 1.21f)) bedMesh(Geo.box(0.05f, 0.32f, 0.26f), matWood, dx, 1.66f, -2.15f, receive = false)
        for (px in floatArrayOf(-1.28f, 1.28f)) {
            bedMesh(Geo.box(0.14f, 1.92f, 0.14f, 2, 6, 2), matWood, px, 0.96f, -2.15f, tag = "bed")
            bedMesh(Geo.cylinder(0.045f, 0.065f, 0.06f, 8), matDarkWood, px, 1.95f, -2.15f, cast = false, receive = false)
            bedMesh(Geo.sphere(0.075f, 8, 6), matWood, px, 2.03f, -2.15f, receive = false)
        }
        // 90s jumbo LED clock radio on the nightstand, turned toward the default view
        val nsX = 1.2f - 2.85f; val nsY = 0.62f; val nsZ = -2.47f + 0.45f // nightstand top, in bedGroup space
        val clockBody = bedMesh(Geo.box(0.44f, 0.18f, 0.14f, 2, 1, 1), matDarkPlastic, nsX, nsY + 0.09f, nsZ, receive = false, tag = "clock")
        clockBody.rotation.y = -0.11f
        alarmClockObj = createCanvasTex(64, 24) { _, _, _ -> }
        drawAlarmClock(3 + 14 / 60.0)
        val clockFace = Mesh(Geo.plane(0.4f, 0.15f), PS1.material(map = alarmClockObj.tex, emissive = 0xffffff, emissiveMap = alarmClockObj.tex, emissiveIntensity = 1.0f,
            polygonOffset = true, polygonOffsetFactor = -2f, polygonOffsetUnits = -2f))
        clockFace.position.set(nsX + sin(-0.11f) * 0.071f, nsY + 0.095f, nsZ + cos(-0.11f) * 0.071f)
        clockFace.rotation.y = -0.11f
        bedGroup.add(clockFace); room.registerInteractable(clockFace, "clock")
        // Bedtime novel & Walkman on the headboard ledge
        bedMesh(Geo.box(0.22f, 0.045f, 0.16f), PS1.material(color = 0x7f1d1d), 0.04f, 1.53f, -2.10f, receive = false).rotation.y = -0.22f
        bedMesh(Geo.box(0.15f, 0.038f, 0.11f), PS1.material(color = 0x3b82f6), 0.75f, 1.53f, -2.11f, receive = false).rotation.y = 0.35f
        // 2. Side rails, footboard, corner posts & under-bed storage
        for (rx in floatArrayOf(-1.20f, 1.20f)) bedMesh(Geo.box(0.08f, 0.22f, 3.88f, 1, 2, 8), matWood, rx, 0.32f, -0.18f, tag = "bed")
        bedMesh(Geo.box(2.48f, 0.68f, 0.09f, 6, 2, 1), matWood, 0f, 0.48f, 1.76f, tag = "bed")
        bedMesh(Geo.box(2.58f, 0.06f, 0.14f, 6, 1, 1), matDarkWood, 0f, 0.84f, 1.76f, receive = false)
        for (px in floatArrayOf(-1.24f, 1.24f)) {
            bedMesh(Geo.box(0.13f, 0.92f, 0.13f, 2, 4, 2), matWood, px, 0.46f, 1.76f, tag = "bed")
            bedMesh(Geo.sphere(0.065f, 8, 6), matWood, px, 0.97f, 1.76f, receive = false)
        }
        // Shoe box & skateboard peeking out from underneath
        bedMesh(Geo.box(0.42f, 0.16f, 0.30f, 2, 1, 2), PS1.material(color = 0xb45309), -0.92f, 0.08f, 0.45f).rotation.y = 0.24f
        bedMesh(Geo.box(0.44f, 0.035f, 0.32f, 2, 1, 2), PS1.material(color = 0xd97706), -0.92f, 0.165f, 0.45f, cast = false, receive = false).rotation.y = 0.21f
        bedMesh(Geo.box(0.24f, 0.022f, 0.78f, 2, 1, 4), PS1.material(color = 0x1e293b), -0.85f, 0.065f, 1.38f, receive = false).rotation.y = -0.35f
        for (wz in floatArrayOf(-0.22f, 0.22f)) {
            val wheelAxle = bedMesh(Geo.cylinder(0.026f, 0.026f, 0.22f, 6), PS1.material(color = 0xef4444),
                -0.85f - sin(-0.35f) * wz, 0.028f, 1.38f + cos(-0.35f) * wz, cast = false, receive = false)
            wheelAxle.rotation.z = (PI / 2).toFloat()
        }
        // 3. Fitted sheet mattress base & wrinkled space comforter
        bedMesh(Geo.box(2.30f, 0.28f, 3.74f, 6, 2, 8), matSheet, 0f, 0.46f, -0.20f, tag = "bed")
        val comforterGeo = Geo.box(2.44f, 0.22f, 2.86f, 14, 3, 14)
        run {
            val p = comforterGeo.positions
            for (i in 0 until comforterGeo.vertexCount) {
                val vx = p[i * 3].toDouble(); var vy = p[i * 3 + 1].toDouble(); val vz = p[i * 3 + 2].toDouble()
                if (vy > 0) {
                    // Taper the head edge down so the folded top sheet sits cleanly over it
                    val headTaper = if (vz < -0.95) ((vz + 0.95) / 0.48) * 0.035 else 0.0
                    val quiltPuff = abs(sin(vx * 3.6)) * abs(cos(vz * 3.0)) * 0.018
                    val rumple = sin(vx * 2.2 + vz * 1.8) * 0.014
                    // Side overhang drape only outside the base mattress width
                    val sideDrape = if (abs(vx) > 1.12) ((abs(vx) - 1.12) / 0.12).pow(1.5) * 0.05 else 0.0
                    vy += quiltPuff + rumple + headTaper - sideDrape
                } else {
                    // Keep the inner bottom face flat above the base mattress; only drop the outer skirt
                    // native fix: the skirt hangs down over the mattress sides and foot instead of ending in a block
                    if (abs(vx) > 1.16 || vz > 1.37) vy -= 0.24 else vy = -0.10
                }
                p[i * 3 + 1] = vy.toFloat()
            }
            comforterGeo.computeVertexNormals()
        }
        bedMesh(comforterGeo, PS1.material(map = spaceBedTex, polygonOffset = true, polygonOffsetFactor = -1f, polygonOffsetUnits = -1f), 0f, 0.72f, 0.25f, tag = "bed")
        // Folded-back top sheet collar over the head edge of the comforter
        val foldSheetGeo = Geo.box(2.46f, 0.055f, 0.40f, 10, 1, 8)
        run {
            val p = foldSheetGeo.positions
            for (i in 0 until foldSheetGeo.vertexCount) {
                val fx = p[i * 3].toDouble(); val fy = p[i * 3 + 1].toDouble(); val fz = p[i * 3 + 2].toDouble()
                val sideDrape = if (abs(fx) > 1.14) ((abs(fx) - 1.14) / 0.10).pow(1.5) * 0.045 else 0.0
                val ripple = if (fy > 0) sin(fx * 3.0 + fz * 3.5) * 0.008 else 0.0
                // native fix: past the comforter's head edge the sheet folds down onto the mattress instead of hovering
                val headDrop = if (fz < -0.02) ((-0.02 - fz) / 0.18).pow(0.7) * 0.2 else 0.0
                p[i * 3 + 1] = (fy + ripple - sideDrape - headDrop).toFloat()
            }
            foldSheetGeo.computeVertexNormals()
        }
        bedMesh(foldSheetGeo, PS1.material(map = pillowFabricTex, polygonOffset = true, polygonOffsetFactor = -2f, polygonOffsetUnits = -2f), 0f, 0.865f, -1.16f, tag = "bed")
        // 4. Plump contoured pillows
        val plumpPillowGeo = run {
            val w = 0.94f; val h = 0.22f; val d = 0.50f
            val g = Geo.box(w, h, d, 8, 4, 6)
            val p = g.positions; val hw = w / 2; val hd = d / 2
            for (i in 0 until g.vertexCount) {
                val px = p[i * 3]; val py = p[i * 3 + 1]; val pz = p[i * 3 + 2]
                val nx = px / hw; val nz = pz / hd
                val bulge = max(0f, (1.0f - nx * nx * 0.68f) * (1.0f - nz * nz * 0.68f))
                val headIndent = exp(-(nx * nx * 2.8f + nz * nz * 2.8f)) * 0.024f
                p[i * 3 + 1] = py * (0.48f + 0.85f * bulge) - (if (py > 0) headIndent else 0f)
            }
            g.computeVertexNormals(); g
        }
        for (cfg in arrayOf(floatArrayOf(-0.60f, 0.74f, -1.76f, 0.16f, 0.04f, -0.02f), floatArrayOf(0.60f, 0.74f, -1.76f, 0.15f, -0.05f, 0.02f))) {
            val pillow = bedMesh(plumpPillowGeo, pillowMat, cfg[0], cfg[1], cfg[2], tag = "bed")
            pillow.rotation.set(cfg[3], cfg[4], cfg[5])
        }

        // ---- Wall shelf over the bed: cat photo, plant and books ----
        val wallShelf = Mesh(Geo.box(2.1f, 0.08f, 0.38f, 4, 1, 2), matWood)
        wallShelf.position.set(3.25f, 3.1f, -2.78f); wallShelf.castShadow = true; wallShelf.receiveShadow = true; scene.add(wallShelf)
        catCanvasObj = createCanvasTex(32, 32) { _, _, _ -> }
        drawCatPhoto()
        val catFrame = Mesh(Geo.box(0.42f, 0.48f, 0.05f, 2, 2, 1), PS1.material(map = catCanvasObj.tex))
        catFrame.position.set(2.55f, 3.38f, -2.82f); catFrame.rotation.x = -0.1f; catFrame.castShadow = true; catFrame.receiveShadow = true
        scene.add(catFrame); room.registerInteractable(catFrame, "cat")
        val plantPot = Mesh(Geo.cylinder(0.13f, 0.1f, 0.28f, 6), PS1.material(color = 0x7c3f2c))
        plantPot.position.set(3.05f, 3.28f, -2.76f); plantPot.castShadow = true; plantPot.receiveShadow = true; scene.add(plantPot)
        val plantLeaves = Mesh(Geo.dodecahedron(0.22f, 0), PS1.material(color = 0x1b4332))
        plantLeaves.position.set(3.05f, 3.55f, -2.76f); plantLeaves.castShadow = true; plantLeaves.receiveShadow = true; scene.add(plantLeaves)
        val shelfBookCols = intArrayOf(0xd4d4d8, 0x1e293b, 0x9a3412, 0x1e3a8a, 0x0f172a)
        for (i in 0 until 5) {
            val b = Mesh(Geo.box(0.09f, 0.46f, 0.28f), PS1.material(color = shelfBookCols[i]))
            // The leaning first book rests on the shelf without cutting into its neighbor
            b.position.set(if (i == 0) 3.41f else 3.45f + i * 0.1f, if (i == 0) 3.374f else 3.37f, -2.76f)
            b.rotation.z = if (i == 0) 0.18f else 0f
            b.castShadow = true; b.receiveShadow = true
            scene.add(b)
        }
    }

    // ---- Clock radio: chunky 7-segment digits drawn pixel by pixel so they stay sharp at PS1 resolution ----
    private val SEG = mapOf('0' to "abcdef", '1' to "bc", '2' to "abged", '3' to "abgcd", '4' to "fgbc", '5' to "afgcd", '6' to "afgedc", '7' to "abc", '8' to "abcdefg", '9' to "abcdfg")
    private fun drawSegDigit(ctx: Canvas2D, ch: Char, x: Int, y: Int, on: String, off: String) {
        val w = 11; val h = 18; val t = 3
        val segs = if (ch == ' ') "" else SEG.getValue(ch)
        val rects = linkedMapOf('a' to intArrayOf(t, 0, w - 2 * t, t), 'b' to intArrayOf(w - t, t, t, h / 2 - t), 'c' to intArrayOf(w - t, h / 2 + 1, t, h / 2 - t - 1),
            'd' to intArrayOf(t, h - t, w - 2 * t, t), 'e' to intArrayOf(0, h / 2 + 1, t, h / 2 - t - 1), 'f' to intArrayOf(0, t, t, h / 2 - t), 'g' to intArrayOf(t, h / 2 - 1, w - 2 * t, t))
        for ((k, r) in rects) { ctx.fillStyle = if (segs.contains(k)) on else off; ctx.fillRect(x + r[0], y + r[1], r[2], r[3]) }
    }
    /** Shows the room's time (the focus timer and alarm dot come with the timer menu). Cheap to call: redraws only on change. */
    /** The sunrise alarm is on: a dot lights on the clock. */
    var alarmSet = false
    fun drawAlarmClock(hour: Double) {
        val hh = floor(hour).toInt() % 24; val mm = floor((hour % 1) * 60).toInt()
        val text = "${(((hh + 11) % 12) + 1).toString().padStart(2, ' ')}:${mm.toString().padStart(2, '0')}"
        val key = text + (if (hh >= 12) "p" else "a") + (if (alarmSet) "!" else "")
        if (key == alarmClockShown) return
        alarmClockShown = key
        val ctx = alarmClockObj.ctx
        val on = "#ff3b30"; val off = "#1c0a0a"
        ctx.fillStyle = "#080304"; ctx.fillRect(0, 0, 64, 24)
        val digits = text.replace(":", "")
        intArrayOf(6, 19, 35, 48).forEachIndexed { i, x -> drawSegDigit(ctx, digits[i], x, 3, on, off) }
        ctx.fillStyle = on; ctx.fillRect(31, 7, 2, 2); ctx.fillRect(31, 15, 2, 2) // colon
        ctx.fillStyle = if (hh >= 12) on else off; ctx.fillRect(1, 3, 3, 3)  // PM dot
        ctx.fillStyle = if (alarmSet) on else off; ctx.fillRect(1, 15, 3, 3)   // alarm dot
        alarmClockObj.tex.needsUpdate()
    }

    private fun drawCatPhoto() {
        val ctx = catCanvasObj.ctx
        ctx.fillStyle = "#3a2312"; ctx.fillRect(0, 0, 32, 32)
        ctx.fillStyle = "#d4d4d8"; ctx.fillRect(4, 4, 24, 24)
        ctx.fillStyle = "#181820"; ctx.fillRect(10, 12, 12, 11); ctx.fillRect(10, 8, 4, 4); ctx.fillRect(18, 8, 4, 4)
        ctx.fillStyle = "#ffffff"; ctx.fillRect(14, 15, 4, 6)
        ctx.fillStyle = if (catEyesGlowing) "#00ff66" else "#facc15"
        ctx.fillRect(12, 14, 2, 2); ctx.fillRect(18, 14, 2, 2)
        catCanvasObj.tex.needsUpdate()
    }
    /** Tap on the cat photo: night-vision eyes on / off. */
    fun toggleCatEyes() { catEyesGlowing = !catEyesGlowing; drawCatPhoto() }
}
