package com.ps1bedroom.room

import com.ps1bedroom.room.engine.AmbientLight
import com.ps1bedroom.room.engine.Geo
import com.ps1bedroom.room.engine.Group
import com.ps1bedroom.room.engine.HemisphereLight
import com.ps1bedroom.room.engine.Mesh
import com.ps1bedroom.room.engine.PS1
import com.ps1bedroom.room.engine.PointLight
import com.ps1bedroom.room.engine.Scene
import com.ps1bedroom.room.engine.SpotLight
import com.ps1bedroom.room.engine.Vec3
import kotlin.math.PI

/**
 * The bedroom shell, ported from src/ps1_bedroom.html in the same order (lines 2464-2665 and 8141-8222):
 * floor, ceiling, rug, walls around the window, side and front walls, baseboards, the light switch, the door,
 * and the room lights.
 */
class Room(val scene: Scene, val tex: Textures) {
    val interactables = ArrayList<Mesh>()
    fun registerInteractable(m: Mesh, tag: String) { m.userData["interactTag"] = tag; interactables += m }
    /** Groups that move or animate: never merged by static batching (the HTML's DYNAMIC_ROOTS). */
    fun dynamicRoots(): Set<com.ps1bedroom.room.engine.Object3D> = setOf(doorLeaf, acLouvre) + extraDynamicRoots
    /** Other moving groups (the outside world, the sky) registered by their builders. */
    val extraDynamicRoots = HashSet<com.ps1bedroom.room.engine.Object3D>()
    val extraBatchExcluded = HashSet<com.ps1bedroom.room.engine.Object3D>()
    /** Meshes that change on their own (the HTML skips snowCapMeshes, blindSlats and lavaBlobs when batching). */
    fun batchExcluded(): Set<com.ps1bedroom.room.engine.Object3D> = (snowCapMeshes + blindSlats).toSet() + extraBatchExcluded

    val matWall = PS1.material(map = tex.wall)
    val matWood = PS1.material(map = tex.wood)
    val matDarkPlastic = PS1.material(color = 0x1b2030)
    val matBeigePlastic = PS1.material(color = 0x9aa4b8)

    // ---- Room bounds (inside faces): X -3.73..4.38, Z -2.98..3.48, floor 0, ceiling 5.0
    companion object {
        const val MIN_X = -3.73f; const val MAX_X = 4.38f; const val MIN_Z = -2.98f; const val MAX_Z = 3.48f; const val MIN_Y = 0f; const val MAX_Y = 5.0f
        const val DOOR_X = 2.85f
        val celestialPos = Vec3(-0.75f, 3.45f, -5.85f)
        val roomLightTargetPos = Vec3(2.35f, 1.45f, -0.15f)
    }

    lateinit var ambientLight: AmbientLight
    lateinit var dayHemi: HemisphereLight
    lateinit var moonLight: SpotLight
    lateinit var monitorLight: SpotLight
    lateinit var lavaLight: PointLight
    lateinit var fanLight: SpotLight
    lateinit var fanHemiFill: HemisphereLight
    lateinit var bedroomTvLight: PointLight
    lateinit var doorLeaf: Group

    fun build() {
        // Solid 3D floor and ceiling so shadows and light are physically bounded
        val floor = Mesh(Geo.box(12f, 0.2f, 10f, 12, 1, 12), PS1.material(map = tex.carpet))
        floor.position.set(0f, -0.1f, 0f); floor.receiveShadow = true; floor.castShadow = true
        scene.add(floor)
        val ceiling = Mesh(Geo.box(12f, 0.2f, 10f, 8, 1, 8), PS1.material(map = tex.ceiling))
        ceiling.position.set(0f, 5.1f, 0f); ceiling.receiveShadow = true; ceiling.castShadow = true
        scene.add(ceiling)

        // Rug 3 cm up (no polygonOffset: the vertex snap keeps depth on each surface's plane)
        val areaRug = Mesh(Geo.plane(4.2f, 3.2f, 8, 8), PS1.material(map = tex.rug))
        areaRug.rotation.x = (-PI / 2).toFloat()
        areaRug.position.set(-0.2f, 0.03f, 0.5f); areaRug.receiveShadow = true
        scene.add(areaRug)

        // Walls around the window aperture (opening: X -0.6..2.2, Y 1.55..3.75, Z -3.0)
        fun addSolidWall(w: Float, h: Float, d: Float, x: Float, y: Float, z: Float): Mesh {
            val m = Mesh(Geo.box(w, h, d, 10, 8, 2), matWall)
            m.position.set(x, y, z); m.castShadow = true; m.receiveShadow = true
            scene.add(m); return m
        }
        addSolidWall(4.4f, 5.5f, 0.24f, -2.8f, 2.75f, -3.10f)   // left of window
        addSolidWall(2.4f, 5.5f, 0.24f, 3.4f, 2.75f, -3.10f)    // right of window
        addSolidWall(2.8f, 1.55f, 0.24f, 0.8f, 0.775f, -3.10f)  // below window
        addSolidWall(2.8f, 1.75f, 0.24f, 0.8f, 4.625f, -3.10f)  // above window
        val rightWall = Mesh(Geo.box(0.24f, 5.5f, 10.0f, 2, 12, 16), matWall)
        rightWall.position.set(4.50f, 2.75f, 1.5f); rightWall.castShadow = true; rightWall.receiveShadow = true
        scene.add(rightWall)
        val leftWall = Mesh(Geo.box(0.24f, 5.5f, 6.94f, 2, 10, 12), matWall)
        leftWall.position.set(-3.85f, 2.75f, 0.25f); leftWall.castShadow = true; leftWall.receiveShadow = true
        scene.add(leftWall)

        // Front wall (Z = 3.60) with the door opening (X 1.95..3.75, up to Y 3.7)
        addSolidWall(5.92f, 5.5f, 0.24f, -1.01f, 2.75f, 3.60f)
        addSolidWall(0.87f, 5.5f, 0.24f, 4.185f, 2.75f, 3.60f)
        addSolidWall(1.80f, 1.8f, 0.24f, DOOR_X, 4.6f, 3.60f)
        // (1.3: the hallway is built in the HTML but never added to the scene; its materials still advance the
        //  grime rotation, so create them in the same order to keep every later surface's grime map identical)
        PS1.material(map = tex.carpet); PS1.material(map = tex.ceiling)
        PS1.material(color = 0x7f1d1d)
        PS1.material(color = 0x1b1d2c, side = com.ps1bedroom.room.engine.BACK_SIDE)
        PS1.material(color = 0x3f2a1d)
        PS1.material(color = 0x93c5fd, emissive = 0x60a5fa, emissiveIntensity = 0.9f)
        PS1.material(color = 0xfef3c7, emissive = 0xfde68a, emissiveIntensity = 0.9f)
        repeat(2) { PS1.material(color = 0x3f2a14); PS1.material(color = 0) }

        // Baseboards along the left and front walls
        val baseboardLeft = Mesh(Geo.box(0.06f, 0.14f, 6.46f, 1, 1, 6), matWood)
        baseboardLeft.position.set(-3.70f, 0.07f, 0.25f)
        scene.add(baseboardLeft)
        // Front wall: from the left corner right up to the door casing, and from the casing to the right corner
        // (native fix: the HTML's runs stopped short of the door, leaving a bare gap)
        val casingL = DOOR_X - 0.90f; val casingR = DOOR_X + 0.90f
        for ((x0, x1) in listOf(MIN_X to casingL, casingR to MAX_X)) {
            val bb = Mesh(Geo.box(x1 - x0, 0.14f, 0.06f, 4, 1, 1), matWood)
            bb.position.set((x0 + x1) / 2, 0.07f, 3.45f)
            scene.add(bb)
        }
        // Window wall and right wall, so the baseboard runs all the way around the room
        val bbBack = Mesh(Geo.box(MAX_X - MIN_X, 0.14f, 0.06f, 8, 1, 1), matWood)
        bbBack.position.set((MIN_X + MAX_X) / 2, 0.07f, MIN_Z + 0.03f)
        scene.add(bbBack)
        val bbRight = Mesh(Geo.box(0.06f, 0.14f, MAX_Z - MIN_Z, 1, 1, 6), matWood)
        bbRight.position.set(MAX_X - 0.03f, 0.07f, (MIN_Z + MAX_Z) / 2)
        scene.add(bbRight)

        // Light switch beside the door (controls the ceiling fan light)
        val switchPlate = Mesh(Geo.box(0.12f, 0.2f, 0.025f), matBeigePlastic)
        switchPlate.position.set(DOOR_X - 1.08f, 1.75f, 3.47f)
        scene.add(switchPlate); registerInteractable(switchPlate, "switch")
        val switchToggle = Mesh(Geo.box(0.03f, 0.06f, 0.03f), PS1.material(color = 0xf5f5f4))
        switchToggle.position.set(DOOR_X - 1.08f, 1.75f, 3.45f)
        scene.add(switchToggle); registerInteractable(switchToggle, "switch")

        // Door assembly on the front wall. Local X = through the wall (+X into the bedroom), Z = across the opening
        val doorGroup = Group()
        doorGroup.position.set(DOOR_X, 0f, 3.60f)
        doorGroup.rotation.y = (PI / 2).toFloat()
        scene.add(doorGroup)
        val doorFrameTop = Mesh(Geo.box(0.30f, 0.14f, 1.82f, 1, 1, 4), matWood)
        doorFrameTop.position.set(0f, 3.62f, 0f); doorFrameTop.castShadow = true
        doorGroup.add(doorFrameTop)
        for (dz in listOf(-0.84f, 0.84f)) {
            val jamb = Mesh(Geo.box(0.30f, 3.65f, 0.12f, 1, 6, 1), matWood)
            jamb.position.set(0f, 1.825f, dz); jamb.castShadow = true
            doorGroup.add(jamb)
        }
        doorLeaf = Group()
        doorLeaf.position.set(0f, 0f, -0.76f)
        doorGroup.add(doorLeaf)
        val doorPanel = Mesh(Geo.box(0.06f, 3.48f, 1.52f, 1, 8, 4), PS1.material(map = tex.door))
        doorPanel.position.set(0f, 1.78f, 0.76f); doorPanel.castShadow = true; doorPanel.receiveShadow = true
        doorLeaf.add(doorPanel); registerInteractable(doorPanel, "door")
        // 1.3: an UNDER CONSTRUCTION sign taped to the door (the hallway is closed for now)
        val constructionTex = com.ps1bedroom.room.engine.createCanvasTex(64, 48) { ctx, w, h ->
            ctx.fillStyle = "#facc15"; ctx.fillRect(0, 0, w, h)
            var x = -48
            while (x < w) {
                ctx.fillStyle = "#111"; ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x + 6, 0); ctx.lineTo(x + 6 + 10, 9); ctx.lineTo(x + 10, 9); ctx.fill()
                ctx.beginPath(); ctx.moveTo(x, h - 9); ctx.lineTo(x + 6, h - 9); ctx.lineTo(x + 16, h); ctx.lineTo(x + 10, h); ctx.fill()
                x += 12
            }
            ctx.fillStyle = "#111"; ctx.font = "bold 9px monospace"; ctx.textAlign = "center"
            ctx.fillText("UNDER", w / 2, 21); ctx.fillText("CONSTRUCTION", w / 2, 31)
            ctx.font = "6px monospace"; ctx.fillText("KEEP OUT", w / 2, 38)
        }.tex
        for (s in intArrayOf(-1, 1)) {
            val sign = Mesh(Geo.plane(0.62f, 0.46f), PS1.material(map = constructionTex, color = 0xffffff, polygonOffset = true, polygonOffsetFactor = -2f, polygonOffsetUnits = -2f))
            sign.position.set(s * 0.033f, 2.25f, 0.76f); sign.rotation.set(0f, (s * PI / 2).toFloat(), s * 0.04f)
            doorLeaf.add(sign); registerInteractable(sign, "door")
        }
        for (hy in floatArrayOf(0.45f, 1.8f, 3.15f)) {
            val hinge = Mesh(Geo.box(0.07f, 0.14f, 0.03f), PS1.material(color = 0x8a7355))
            hinge.position.set(0f, hy, 0.01f); doorLeaf.add(hinge)
        }
        // Brass knobs on both faces
        val doorknob = Mesh(Geo.sphere(0.068f, 8, 6), PS1.material(color = 0xf59e0b))
        doorknob.position.set(0.10f, 1.56f, 1.38f); doorLeaf.add(doorknob); registerInteractable(doorknob, "door")
        for (nx in floatArrayOf(0.055f, -0.055f)) {
            val knobNeck = Mesh(Geo.cylinder(0.022f, 0.032f, 0.05f, 8), PS1.material(color = 0xb45309))
            knobNeck.rotation.z = (PI / 2).toFloat(); knobNeck.position.set(nx, 1.56f, 1.38f); doorLeaf.add(knobNeck)
        }
        val hallKnob = Mesh(Geo.sphere(0.068f, 8, 6), PS1.material(color = 0xf59e0b))
        hallKnob.position.set(-0.10f, 1.56f, 1.38f); doorLeaf.add(hallKnob)
        // Warm living-room light strip seeping under the door (sits entirely in the gap under the slab)
        val doorCrackMat = PS1.material(color = 0xfde047, emissive = 0xf59e0b, emissiveIntensity = 0.95f)
        val doorLightCrack = Mesh(Geo.box(0.05f, 0.03f, 1.48f), doorCrackMat)
        doorLightCrack.position.set(0f, 0.02f, 0f); doorGroup.add(doorLightCrack)

        buildAc()
        // (the CRT TV & VCR cart, HTML 2785-3490, comes with the TV phase)
        onBuildOutside()           // HTML 3494: the exterior world (grime 37 onward)
        buildWindow()              // HTML 4296 (grime 141)
        onBuildInterior()          // HTML 4370 onward: the desk corner and the rest of the room
        buildLights()
    }
    /** Hooks for the parts built by other classes, called at their place in the HTML's creation order. */
    var onBuildOutside: () -> Unit = {}
    var onBuildInterior: () -> Unit = {}

    // ---------------------------------------------------------------- 90s wall-mounted AC (HTML 2716-2783)
    lateinit var acLouvre: Group
    lateinit var acLedMat: com.ps1bedroom.room.engine.Material
    private fun buildAc() {
        val acGroup = Group()
        acGroup.position.set(4.24f, 4.15f, -1.15f)
        scene.add(acGroup)
        // Curved front panel, slotted top intake, swinging louvre, temperature display, brand badge, line-set conduit.
        // Local -X faces the room.
        val matAcShell = PS1.material(color = 0xd6d3c4)
        val matAcTrim = PS1.material(color = 0xb8b4a2)
        fun acPart(geo: com.ps1bedroom.room.engine.Geometry, mat: com.ps1bedroom.room.engine.Material, x: Float, y: Float, z: Float, tag: String? = null): Mesh {
            val m = Mesh(geo, mat)
            m.position.set(x, y, z); m.castShadow = true; m.receiveShadow = true
            acGroup.add(m)
            if (tag != null) registerInteractable(m, tag)
            return m
        }
        acPart(Geo.box(0.26f, 0.4f, 1.25f, 2, 2, 4), matAcShell, 0.02f, 0f, 0f, "ac")
        // Front panel bows out toward the room
        val acFrontGeo = Geo.box(0.05f, 0.4f, 1.25f, 1, 6, 8)
        run {
            val p = acFrontGeo.positions
            for (v in 0 until acFrontGeo.vertexCount) if (p[v * 3] < 0) p[v * 3] = p[v * 3] - 0.035f * (1 - (p[v * 3 + 1] / 0.2f) * (p[v * 3 + 1] / 0.2f))
            acFrontGeo.computeVertexNormals()
        }
        acPart(acFrontGeo, matAcShell, -0.13f, 0f, 0f, "ac")
        for (i in 0 until 14) acPart(Geo.box(0.18f, 0.006f, 0.03f), matAcTrim, 0.0f, 0.203f, -0.52f + i * 0.08f)
        // Air outlet with a louvre flap that swings while the unit runs
        acPart(Geo.box(0.06f, 0.07f, 1.12f), matDarkPlastic, -0.14f, -0.17f, 0f)
        acLouvre = Group()
        acLouvre.position.set(-0.17f, -0.18f, 0f)
        acGroup.add(acLouvre)
        val louvreBlade = Mesh(Geo.box(0.07f, 0.012f, 1.08f), matAcTrim)
        louvreBlade.position.set(-0.03f, 0f, 0f); louvreBlade.castShadow = true
        acLouvre.add(louvreBlade)
        // Status display, power LED and brand badge
        val acDisplayTex = com.ps1bedroom.room.engine.createCanvasTex(32, 16) { ctx, w, h ->
            ctx.fillStyle = "#04140a"; ctx.fillRect(0, 0, w, h)
            ctx.fillStyle = "#4ade80"; ctx.font = "bold 11px monospace"; ctx.fillText("68°", 2, 12)
        }.tex
        val acDisplay = Mesh(Geo.plane(0.1f, 0.05f), PS1.material(map = acDisplayTex, emissive = 0xffffff, emissiveMap = acDisplayTex, emissiveIntensity = 0.9f))
        acDisplay.rotation.y = (-PI / 2).toFloat(); acDisplay.position.set(-0.19f, 0.05f, 0.42f); acGroup.add(acDisplay)
        acLedMat = PS1.material(color = 0x22c55e, emissive = 0x22c55e, emissiveIntensity = 1.0f)
        val acLed = Mesh(Geo.box(0.02f, 0.018f, 0.018f), acLedMat)
        acLed.position.set(-0.185f, 0.05f, 0.52f); acGroup.add(acLed)
        val acBadgeTex = com.ps1bedroom.room.engine.createCanvasTex(64, 16, weather = com.ps1bedroom.room.engine.Weather(seed = 81.0, blotch = 0.1, scratches = 2)) { ctx, w, h ->
            ctx.fillStyle = "#c9c5b3"; ctx.fillRect(0, 0, w, h)
            ctx.fillStyle = "#1e3a8a"; ctx.font = "bold italic 10px sans-serif"; ctx.fillText("COOLAIRE", 4, 12)
        }.tex
        val acBadge = Mesh(Geo.plane(0.2f, 0.05f), PS1.material(map = acBadgeTex))
        acBadge.rotation.y = (-PI / 2).toFloat(); acBadge.position.set(-0.185f, 0.09f, -0.35f); acGroup.add(acBadge)
        // Refrigerant line-set conduit: out of the unit's lower end and down the wall into the floor
        acPart(Geo.box(0.08f, 0.08f, 0.2f), matAcShell, 0.08f, -0.17f, -0.7f)
        acPart(Geo.box(0.08f, 4.0f, 0.08f), matAcShell, 0.08f, -2.2f, -0.76f)
    }

    // ---------------------------------------------------------------- window and blinds (HTML 4296-4368, 10644-10681)
    val blindSlats = ArrayList<Mesh>()
    val snowCapMeshes = ArrayList<Mesh>()
    /** 0 slatted · 1 tilted open · 2 closed · 3 rolled all the way up (tap order 0 -> 1 -> 3 -> 2) */
    var blindsOpenState = 0; private set
    private var blindsRaise = 0f
    private val slatTilt = FloatArray(17) { 0.34f }
    /** Called when something that casts shadows moved (the renderer re-renders its shadow maps once). */
    var onShadowsChanged: () -> Unit = {}
    /** setBlindsState re-applies the environment (how much light gets through). */
    var onBlindsChanged: () -> Unit = {}

    private fun buildWindow() {
        PS1.syncGrime(141)
        // Window sill snow drift (visible in winter)
        val sillSnowMesh = Mesh(Geo.box(2.88f, 0.065f, 0.16f, 8, 1, 2), PS1.material(color = 0xf1f5f9, emissive = 0x94a3b8, emissiveIntensity = 0.25f))
        sillSnowMesh.position.set(0.8f, 1.58f, -3.05f); sillSnowMesh.visible = false
        scene.add(sillSnowMesh); snowCapMeshes += sillSnowMesh

        val blindGroup = Group()
        val matBlind = PS1.material(color = 0x8a9cc2)
        val matWindowFrame = PS1.material(color = 0x5c6b8a)
        // Frame jambs and centre mullion (cast the window-frame shadow into the room)
        fun part(m: Mesh, x: Float, y: Float, z: Float): Mesh { m.position.set(x, y, z); m.castShadow = true; blindGroup.add(m); return m }
        part(Mesh(Geo.box(2.88f, 0.08f, 0.28f), matWindowFrame), 0.8f, 3.73f, -3.08f)
        part(Mesh(Geo.box(0.08f, 2.24f, 0.28f), matWindowFrame), -0.58f, 2.65f, -3.08f)
        part(Mesh(Geo.box(0.08f, 2.24f, 0.28f), matWindowFrame), 2.18f, 2.65f, -3.08f)
        part(Mesh(Geo.box(0.05f, 2.20f, 0.06f), matWindowFrame), 0.8f, 2.65f, -3.14f)
        // 17 venetian blind slats casting slatted shadows and slicing the light rays
        for (i in 0 until 17) {
            val slat = Mesh(Geo.box(2.74f, 0.024f, 0.095f, 6, 1, 1), matBlind)
            slat.position.set(0.8f, 1.62f + i * 0.125f, -2.98f)
            slat.rotation.x = 0.34f
            slat.castShadow = true; slat.receiveShadow = true
            blindGroup.add(slat); blindSlats += slat
            registerInteractable(slat, "blinds")
        }
        val windowSill = Mesh(Geo.box(2.96f, 0.08f, 0.28f, 6, 1, 1), matBlind)
        windowSill.position.set(0.8f, 1.53f, -2.92f); windowSill.castShadow = true; windowSill.receiveShadow = true
        blindGroup.add(windowSill)
        val blindHeader = part(Mesh(Geo.box(2.84f, 0.12f, 0.16f), matBlind), 0.8f, 3.74f, -2.96f)
        registerInteractable(blindHeader, "blinds")
        for (cx in listOf(-0.25f, 0.8f, 1.85f)) part(Mesh(Geo.cylinder(0.01f, 0.01f, 2.15f, 4), matBlind), cx, 2.65f, -2.97f)
        part(Mesh(Geo.cylinder(0.012f, 0.012f, 1.45f, 5), matBlind), -0.48f, 2.85f, -2.88f)
        scene.add(blindGroup)
    }

    private val blindOrder = intArrayOf(0, 1, 3, 2)
    fun setBlindsState(n: Int) {
        blindsOpenState = n
        if (n != 3) slatTilt.fill(floatArrayOf(0.34f, 0.0f, 1.38f)[n])
        onBlindsChanged()
        onShadowsChanged()
    }
    fun cycleWindowBlinds() = setBlindsState(blindOrder[(blindOrder.indexOf(blindsOpenState) + 1) % blindOrder.size])

    /** Slats glide up into a tight stack under the header (or back down), tilting flat as they go. */
    fun updateBlinds(dt: Float) {
        val target = if (blindsOpenState == 3) 1f else 0f
        val prev = blindsRaise
        blindsRaise += (target - blindsRaise) * minOf(1f, dt * 2.6f)
        if (kotlin.math.abs(target - blindsRaise) < 0.002f) blindsRaise = target
        var moved = kotlin.math.abs(blindsRaise - prev) > 0.0001f
        val n = blindSlats.size
        for (i in 0 until n) {
            val sl = blindSlats[i]
            val baseY = 1.62f + i * 0.125f
            val stackY = 3.66f - (n - 1 - i) * 0.013f
            sl.position.y = baseY + (stackY - baseY) * blindsRaise
            val tilt = slatTilt[i] * (1 - blindsRaise)
            if (kotlin.math.abs(sl.rotation.x - tilt) > 0.001f) { sl.rotation.x += (tilt - sl.rotation.x) * minOf(1f, dt * 8f); moved = true }
        }
        if (moved) onShadowsChanged()
    }

    /** Room lights (HTML lines 8141-8222), with their starting values before the environment updates them. */
    private fun buildLights() {
        ambientLight = AmbientLight(0x131b3a, 0.65f); scene.add(ambientLight)
        dayHemi = HemisphereLight(0xbcd4ff, 0x4a3a2c, 0.0f); scene.add(dayHemi)

        // Primary celestial spot light (moon / sun) with 512x512 slatted shadows; drives the light rays
        moonLight = SpotLight(0x5b86ff, 3.6f).apply {
            position.copy(celestialPos); target.position.copy(roomLightTargetPos)
            angle = 0.66f; penumbra = 0.12f; decay = 1.1f; distance = 22.0f
            castShadow = true; shadow.mapSize = 512; shadow.near = 1.2f; shadow.far = 16.5f; shadow.bias = -0.0012f
        }
        scene.add(moonLight); scene.add(moonLight.target)

        monitorLight = SpotLight(0x00d8ff, 1.55f).apply {
            position.set(-1.35f, 2.15f, -1.55f); target.position.set(-0.45f, 1.1f, 0.65f)
            angle = (PI / 2.3).toFloat(); penumbra = 0.35f; decay = 1.3f; distance = 8.5f
        }
        scene.add(monitorLight); scene.add(monitorLight.target)

        lavaLight = PointLight(0xd946ef, 1.15f, 3.2f).apply { position.set(-0.75f, 2.05f, -1.55f) }
        scene.add(lavaLight)

        fanLight = SpotLight(0xffcf87, 0.0f).apply {
            position.set(0.6f, 4.30f, -0.4f); target.position.set(0.6f, 0.0f, -0.4f)
            angle = (PI * 0.46).toFloat(); penumbra = 0.82f; decay = 1.25f; distance = 14.0f
            castShadow = true; shadow.mapSize = 512; shadow.near = 0.35f; shadow.far = 9.0f; shadow.bias = -0.0015f
        }
        scene.add(fanLight); scene.add(fanLight.target)
        fanHemiFill = HemisphereLight(0xffe0b2, 0x3b2d22, 0.0f); scene.add(fanHemiFill)

        bedroomTvLight = PointLight(0xec4899, 1.15f, 4.2f).apply { position.set(-1.92f, 1.32f, 0.65f) }
        scene.add(bedroomTvLight)
    }
}
