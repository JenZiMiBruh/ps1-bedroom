package com.ps1bedroom.room

import com.ps1bedroom.room.engine.CatmullRomCurve3
import com.ps1bedroom.room.engine.Geo
import com.ps1bedroom.room.engine.GeoCurves
import com.ps1bedroom.room.engine.Group
import com.ps1bedroom.room.engine.Material
import com.ps1bedroom.room.engine.Mesh
import com.ps1bedroom.room.engine.PS1
import com.ps1bedroom.room.engine.Weather
import com.ps1bedroom.room.engine.createCanvasTex
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * The desk corner (HTML lines 4370-5360): live-edge desk and hutch, CD rack, strategy guide and loose CD, the HP
 * Pavilion tower with its sticky note, cordless phone, pocket MP3 player and cable, the monitor, keyboard, mouse,
 * soda can and the lava lamp. (The PS1 game shelf on the hutch comes with the game library.)
 */
class Desk(private val room: Room, private val tex: Textures) {
    val deskGroup = Group()
    val lavaBlobs = ArrayList<Mesh>()
    private val blobSpeed = ArrayList<Float>(); private val blobOffset = ArrayList<Float>()
    lateinit var pcLedMat: Material
    lateinit var monScreen: Mesh
    private val rnd = Random.Default

    fun build() {
        val matWood = room.matWood; val matDarkPlastic = room.matDarkPlastic
        deskGroup.position.set(-1.55f, 0f, -1.7f)
        deskGroup.rotation.y = 0.32f
        room.scene.add(deskGroup)

        fun addWoodBox(w: Float, h: Float, d: Float, x: Float, y: Float, z: Float, segs: Int = 4): Mesh {
            val mesh = Mesh(Geo.box(w, h, d, segs, segs, segs), matWood)
            mesh.position.set(x, y, z)
            mesh.castShadow = true; mesh.receiveShadow = true
            deskGroup.add(mesh)
            return mesh
        }
        // Thick wood desktop with organic live-edge front contour
        addWoodBox(3.15f, 0.09f, 1.45f, 0f, 1.45f, -0.04f, 6)
        val frontEdgeGeo = Geo.box(3.15f, 0.088f, 0.22f, 24, 2, 2)
        run {
            val p = frontEdgeGeo.positions
            for (i in 0 until frontEdgeGeo.vertexCount) {
                val vx = p[i * 3]; val vz = p[i * 3 + 2]
                if (vz > 0) p[i * 3 + 2] = (vz + sin(vx * 2.1) * 0.065 + cos(vx * 4.2) * 0.028).toFloat()
            }
            frontEdgeGeo.computeVertexNormals()
        }
        val wavyFrontMesh = Mesh(frontEdgeGeo, matWood)
        wavyFrontMesh.position.set(0f, 1.448f, 0.72f)
        wavyFrontMesh.castShadow = true; wavyFrontMesh.receiveShadow = true
        deskGroup.add(wavyFrontMesh)
        // Desk side panels, keyboard drawer rails, hutch uprights, CD cubby dividers & top hutch shelf
        addWoodBox(0.12f, 1.45f, 1.4f, -1.45f, 0.725f, 0f, 3)
        addWoodBox(0.8f, 1.45f, 1.4f, 1.1f, 0.725f, 0f, 3)
        addWoodBox(0.09f, 1.45f, 0.78f, -1.45f, 2.22f, -0.35f, 3)
        addWoodBox(0.05f, 1.45f, 0.76f, 1.07f, 2.22f, -0.35f, 3) // Inner CD rack wall
        addWoodBox(0.06f, 1.45f, 0.76f, 1.47f, 2.22f, -0.35f, 3) // Outer CD rack wall
        addWoodBox(0.42f, 1.45f, 0.04f, 1.27f, 2.22f, -0.71f, 2) // CD rack back panel
        addWoodBox(3.08f, 0.095f, 0.84f, -0.01f, 2.95f, -0.35f, 6) // Top hutch shelf
        addWoodBox(2.96f, 0.08f, 0.04f, -0.02f, 2.86f, -0.72f, 4) // Hutch upper valance

        // ---- 1. CD jewel cases in the right hutch rack + loose shiny CD-ROM ----
        PS1.syncGrime(144)
        val cdSpineColors = intArrayOf(0x2563eb, 0xdc2626, 0x16a34a, 0xd97706, 0x9333ea, 0xeab308, 0x0891b2, 0xe11d48)
        for (i in 0 until 18) {
            // Shelf divider slot every 2 cases
            if (i % 2 == 0 && i > 0) addWoodBox(0.36f, 0.014f, 0.32f, 1.27f, 1.52f + i * 0.076f - 0.02f, -0.14f, 1)
            val jewelGroup = Group()
            val offsetZ = (-0.08 + sin(i * 2.1) * 0.025).toFloat()
            jewelGroup.position.set(1.27f + ((rnd.nextDouble() - 0.5) * 0.02).toFloat(), 1.54f + i * 0.076f, offsetZ)
            jewelGroup.rotation.y = (cos(i * 1.7) * 0.045).toFloat()
            val caseShell = Mesh(Geo.box(0.33f, 0.032f, 0.28f), PS1.material(color = 0xcbd5e1))
            caseShell.castShadow = true; caseShell.receiveShadow = true
            jewelGroup.add(caseShell)
            val spineStrip = Mesh(Geo.box(0.31f, 0.026f, 0.02f), PS1.material(color = cdSpineColors[i % cdSpineColors.size]))
            spineStrip.position.z = 0.135f
            jewelGroup.add(spineStrip)
            deskGroup.add(jewelGroup)
        }
        // Strategy guide book + loose iridescent CD-ROM on the right desk corner
        val manualTex = createCanvasTex(128, 128, weather = Weather(seed = 51.0, blotch = 0.16, edge = 0.28, scratches = 6, creases = 2, warm = 0.15)) { ctx, w, h ->
            ctx.fillStyle = "#172554"; ctx.fillRect(0, 0, w, h)
            ctx.strokeStyle = "#60a5fa"; ctx.lineWidth = 3f; ctx.strokeRect(4, 4, w - 8, h - 8)
            ctx.fillStyle = "#facc15"; ctx.fillRect(12, 14, w - 24, 18)
            ctx.fillStyle = "#b91c1c"; ctx.fillRect(16, 42, w - 32, 54)
            ctx.fillStyle = "#f8fafc"; ctx.font = "bold 12px sans-serif"
            ctx.fillText("OFFICIAL", 26, 27); ctx.fillText("STRATEGY", 24, 68)
        }.tex
        val manualBook = Mesh(Geo.box(0.45f, 0.052f, 0.35f, 3, 1, 3), PS1.material(map = manualTex))
        manualBook.position.set(1.14f, 1.52f, 0.30f); manualBook.rotation.y = 0.24f
        manualBook.castShadow = true; manualBook.receiveShadow = true
        deskGroup.add(manualBook)
        val looseCd = Mesh(Geo.cylinder(0.11f, 0.11f, 0.006f, 14), PS1.material(color = 0xe0f2fe, emissive = 0x38bdf8, emissiveIntensity = 0.22f))
        looseCd.position.set(1.12f, 1.55f, 0.32f); deskGroup.add(looseCd)
        val looseCdHole = Mesh(Geo.cylinder(0.028f, 0.028f, 0.008f, 10), matDarkPlastic)
        looseCdHole.position.set(1.12f, 1.551f, 0.32f); deskGroup.add(looseCdHole)

        // (2. the PS1 game collection on the top hutch shelf comes with the game library)

        // ---- 3. HP Pavilion PC tower + curled sticky note ----
        PS1.syncGrime(182)
        val pcTowerGroup = Group()
        pcTowerGroup.position.set(-0.98f, 2.12f, -0.18f)
        deskGroup.add(pcTowerGroup)
        val matPcChassis = PS1.material(color = 0x5c6474)
        val matPcBezel = PS1.material(color = 0x737c8f)
        val matPcDark = PS1.material(color = 0x262b36)
        val pcChassis = Mesh(Geo.box(0.54f, 1.24f, 0.82f, 3, 4, 3), matPcChassis)
        pcChassis.position.set(0f, 0f, -0.04f); pcChassis.castShadow = true; pcChassis.receiveShadow = true
        pcTowerGroup.add(pcChassis); room.registerInteractable(pcChassis, "pc")
        val pcBezelMats = listOf(matPcBezel, matPcBezel, matPcBezel, matPcDark, PS1.material(map = tex.pcFront), matPcDark)
        val pcFrontBezel = Mesh(Geo.box(0.56f, 1.25f, 0.12f, 3, 5, 2), pcBezelMats)
        pcFrontBezel.position.set(0f, 0f, 0.41f); pcFrontBezel.castShadow = true; pcFrontBezel.receiveShadow = true
        pcTowerGroup.add(pcFrontBezel); room.registerInteractable(pcFrontBezel, "pc")
        for (b in 0 until 3) {
            val bayY = 0.44f - b * 0.185f
            val bayCover = Mesh(Geo.box(0.46f, 0.155f, 0.045f, 3, 1, 1), matPcBezel)
            bayCover.position.set(0f, bayY, 0.465f); bayCover.castShadow = true
            pcTowerGroup.add(bayCover); room.registerInteractable(bayCover, "pc")
            val baySlot = Mesh(Geo.box(0.38f, 0.036f, 0.03f), matPcDark)
            baySlot.position.set(0f, bayY + 0.015f, 0.478f); pcTowerGroup.add(baySlot)
            if (b < 2) {
                val ejectBtn = Mesh(Geo.box(0.045f, 0.022f, 0.02f), PS1.material(color = 0x94a3b8))
                ejectBtn.position.set(0.15f, bayY - 0.038f, 0.485f); pcTowerGroup.add(ejectBtn)
            }
        }
        val floppyBay = Mesh(Geo.box(0.42f, 0.11f, 0.04f), matPcChassis)
        floppyBay.position.set(0f, -0.09f, 0.462f); pcTowerGroup.add(floppyBay)
        val floppySlot = Mesh(Geo.box(0.28f, 0.022f, 0.025f), matPcDark)
        floppySlot.position.set(-0.02f, -0.08f, 0.478f); pcTowerGroup.add(floppySlot)
        val ioBay = Mesh(Geo.box(0.38f, 0.085f, 0.035f), matPcBezel)
        ioBay.position.set(0f, -0.21f, 0.462f); pcTowerGroup.add(ioBay)
        for (px in floatArrayOf(-0.10f, -0.02f, 0.06f)) {
            val usbHole = Mesh(Geo.box(0.042f, 0.024f, 0.025f), matPcDark)
            usbHole.position.set(px, -0.21f, 0.475f); pcTowerGroup.add(usbHole)
        }
        // Curled sticky note ("DO NOT TURN OFF") stuck to the drive bays
        val stickyGeo = Geo.plane(0.23f, 0.21f, 5, 5)
        run {
            val p = stickyGeo.positions
            for (i in 0 until stickyGeo.vertexCount) {
                val sy = p[i * 3 + 1]; val sx = p[i * 3]
                // Bottom half curls outward away from the bezel
                if (sy < 0.05f) p[i * 3 + 2] = ((0.05 - sy).pow(1.5) * 0.42 + (if (sx > 0) sx * 0.08 else 0.0)).toFloat()
            }
            stickyGeo.computeVertexNormals()
        }
        val stickyNoteMesh = Mesh(stickyGeo, PS1.material(map = tex.stickyNote, emissive = 0xded49c, emissiveMap = tex.stickyNote, emissiveIntensity = 0.18f,
            polygonOffset = true, polygonOffsetFactor = -2f, polygonOffsetUnits = -2f))
        stickyNoteMesh.position.set(0.02f, 0.11f, 0.495f); stickyNoteMesh.rotation.z = -0.04f; stickyNoteMesh.castShadow = true
        pcTowerGroup.add(stickyNoteMesh); room.registerInteractable(stickyNoteMesh, "pc")
        pcLedMat = PS1.material(color = 0x22ff55, emissive = 0x22ff55, emissiveIntensity = 1.0f)
        val pcLed = Mesh(Geo.box(0.026f, 0.026f, 0.02f), pcLedMat)
        pcLed.position.set(-0.14f, -0.09f, 0.482f); pcTowerGroup.add(pcLed)

        // ---- 4. 900MHz cordless phone & charging cradle ----
        val phoneGroup = Group()
        phoneGroup.position.set(-1.14f, 1.495f, 0.46f); phoneGroup.rotation.y = 0.12f
        deskGroup.add(phoneGroup)
        val matPhoneBlue = PS1.material(color = 0x172554)
        val matPhoneBody = PS1.material(color = 0x1e3a8a)
        val phoneBaseLower = Mesh(Geo.box(0.25f, 0.065f, 0.28f, 3, 1, 3), matPhoneBlue)
        phoneBaseLower.position.y = 0.032f; phoneBaseLower.castShadow = true; phoneBaseLower.receiveShadow = true
        phoneGroup.add(phoneBaseLower); room.registerInteractable(phoneBaseLower, "phone")
        val phoneBaseCollar = Mesh(Geo.cylinder(0.085f, 0.115f, 0.065f, 10), matPhoneBlue)
        phoneBaseCollar.position.set(0f, 0.075f, -0.01f); phoneBaseCollar.castShadow = true; phoneGroup.add(phoneBaseCollar)
        val chargeLed = Mesh(Geo.box(0.02f, 0.012f, 0.02f), PS1.material(color = 0xef4444, emissive = 0xef4444, emissiveIntensity = 0.9f))
        chargeLed.position.set(0f, 0.066f, 0.125f); phoneGroup.add(chargeLed)
        val handsetGroup = Group()
        handsetGroup.position.set(0f, 0.27f, -0.02f); handsetGroup.rotation.x = -0.16f
        phoneGroup.add(handsetGroup)
        val handsetBody = Mesh(Geo.box(0.105f, 0.44f, 0.085f, 3, 5, 2), matPhoneBody)
        handsetBody.castShadow = true; handsetBody.receiveShadow = true
        handsetGroup.add(handsetBody); room.registerInteractable(handsetBody, "phone")
        val earSwell = Mesh(Geo.cylinder(0.052f, 0.052f, 0.092f, 10), matPhoneBody)
        earSwell.rotation.x = (PI / 2).toFloat(); earSwell.position.set(0f, 0.18f, 0.008f); handsetGroup.add(earSwell)
        val phoneLcdTex = createCanvasTex(64, 32) { ctx, w, h ->
            ctx.fillStyle = "#4ade80"; ctx.fillRect(0, 0, w, h)
            ctx.strokeStyle = "#14532d"; ctx.lineWidth = 2f; ctx.strokeRect(1, 1, w - 2, h - 2)
            ctx.fillStyle = "#052e16"; ctx.font = "bold 9px monospace"
            ctx.fillText("LINE 1", 8, 12); ctx.fillText("555-0199", 6, 25)
        }.tex
        val phoneLcd = Mesh(Geo.plane(0.076f, 0.048f), PS1.material(map = phoneLcdTex, emissive = 0x4ade80, emissiveMap = phoneLcdTex, emissiveIntensity = 0.95f))
        phoneLcd.position.set(0f, 0.065f, 0.046f); handsetGroup.add(phoneLcd)
        val matKeyBtn = PS1.material(color = 0x93c5fd, emissive = 0x1d4ed8, emissiveIntensity = 0.35f)
        for (r in 0 until 4) for (c in 0 until 3) {
            val kbtn = Mesh(Geo.box(0.018f, 0.014f, 0.012f), matKeyBtn)
            kbtn.position.set((c - 1) * 0.025f, -0.015f - r * 0.032f, 0.045f); handsetGroup.add(kbtn)
        }
        val phoneAntenna = Mesh(Geo.cylinder(0.009f, 0.015f, 0.19f, 6), matDarkPlastic)
        phoneAntenna.position.set(0.032f, 0.28f, -0.01f); handsetGroup.add(phoneAntenna)

        // ---- 5. Pocket MP3 / portable drive + cord to the PC's front port bay ----
        val pocketDevice = Mesh(Geo.box(0.18f, 0.036f, 0.115f, 2, 1, 2), matDarkPlastic)
        pocketDevice.position.set(-0.68f, 1.515f, 0.45f); pocketDevice.rotation.y = 0.25f; pocketDevice.castShadow = true
        deskGroup.add(pocketDevice)
        val pocketTrim = Mesh(Geo.box(0.184f, 0.014f, 0.118f), PS1.material(color = 0x94a3b8))
        pocketTrim.position.set(-0.68f, 1.515f, 0.45f); pocketTrim.rotation.y = 0.25f; deskGroup.add(pocketTrim)
        val pocketScreen = Mesh(Geo.plane(0.095f, 0.058f), PS1.material(color = 0x38bdf8, emissive = 0xfacc15, emissiveIntensity = 0.85f))
        pocketScreen.rotation.x = (-PI / 2).toFloat(); pocketScreen.rotation.z = -0.25f
        pocketScreen.position.set(-0.69f, 1.535f, 0.45f); deskGroup.add(pocketScreen)
        val cableCurve = CatmullRomCurve3(listOf(
            doubleArrayOf(-0.97, 1.91, 0.29), doubleArrayOf(-0.86, 1.68, 0.38), doubleArrayOf(-0.90, 1.51, 0.50),
            doubleArrayOf(-0.50, 1.51, 0.58), doubleArrayOf(-0.62, 1.515, 0.45)))
        val cableMesh = Mesh(GeoCurves.tube(cableCurve, 18, 0.011f, 6), PS1.material(color = 0x090b10))
        cableMesh.castShadow = true; deskGroup.add(cableMesh)

        // ---- 6. Multimedia monitor with side speaker grilles & OSD buttons ----
        val monPedestalBase = Mesh(Geo.cylinder(0.24f, 0.28f, 0.045f, 12), PS1.material(color = 0x8a95a8))
        monPedestalBase.scale.set(1.15f, 1.0f, 0.82f); monPedestalBase.position.set(-0.08f, 1.518f, -0.18f)
        monPedestalBase.castShadow = true; monPedestalBase.receiveShadow = true; deskGroup.add(monPedestalBase)
        val monSwivelNeck = Mesh(Geo.cylinder(0.085f, 0.11f, 0.22f, 8), PS1.material(color = 0x788296))
        monSwivelNeck.position.set(-0.08f, 1.62f, -0.21f); monSwivelNeck.castShadow = true; deskGroup.add(monSwivelNeck)
        val matMonBezel = PS1.material(color = 0x9aa6ba)
        val monFrame = Group()
        monFrame.position.set(-0.08f, 2.15f, -0.18f); deskGroup.add(monFrame)
        val monInnerWell = Mesh(Geo.box(1.12f, 0.92f, 0.10f, 2, 2, 1), PS1.material(color = 0x5c667a))
        monInnerWell.position.set(0f, 0f, -0.045f); monInnerWell.castShadow = true; monInnerWell.receiveShadow = true
        monFrame.add(monInnerWell); room.registerInteractable(monInnerWell, "monitor")
        fun bezel(g: com.ps1bedroom.room.engine.Geometry, x: Float, y: Float) {
            val m = Mesh(g, matMonBezel); m.position.set(x, y, 0f); m.castShadow = true; m.receiveShadow = true
            monFrame.add(m); room.registerInteractable(m, "monitor")
        }
        bezel(Geo.box(1.18f, 0.085f, 0.15f, 4, 1, 2), 0f, 0.44f)
        bezel(Geo.box(1.18f, 0.115f, 0.15f, 4, 1, 2), 0f, -0.435f)
        bezel(Geo.box(0.10f, 0.78f, 0.15f, 1, 4, 2), -0.54f, 0.005f)
        bezel(Geo.box(0.10f, 0.78f, 0.15f, 1, 4, 2), 0.54f, 0.005f)
        val matGrille = PS1.material(map = tex.speakerGrille)
        for (gx in floatArrayOf(-0.44f, 0.44f)) {
            val grilleMesh = Mesh(Geo.plane(0.14f, 0.08f), matGrille)
            grilleMesh.position.set(gx, -0.435f, 0.077f); monFrame.add(grilleMesh)
        }
        val monLogoBadge = Mesh(Geo.cylinder(0.022f, 0.022f, 0.01f, 8), PS1.material(color = 0x334155))
        monLogoBadge.rotation.x = (PI / 2).toFloat(); monLogoBadge.position.set(0f, -0.435f, 0.076f); monFrame.add(monLogoBadge)
        for (bx in floatArrayOf(-0.14f, -0.09f, 0.09f, 0.14f)) {
            val osdBtn = Mesh(Geo.box(0.024f, 0.014f, 0.012f), PS1.material(color = 0x475569))
            osdBtn.position.set(bx, -0.442f, 0.076f); monFrame.add(osdBtn)
        }
        val monCrtBack = Mesh(Geo.box(0.82f, 0.70f, 0.34f, 3, 3, 2), PS1.material(color = 0x7c8699))
        monCrtBack.position.set(-0.08f, 2.14f, -0.41f); monCrtBack.castShadow = true; deskGroup.add(monCrtBack)
        val monPowerLed = Mesh(Geo.box(0.024f, 0.016f, 0.02f), PS1.material(color = 0x22c55e, emissive = 0x22c55e, emissiveIntensity = 1.0f))
        monPowerLed.position.set(0.15f, 1.71f, -0.102f); deskGroup.add(monPowerLed)
        val monScreenMat = PS1.material(map = tex.desktop, emissive = 0xffffff, emissiveMap = tex.desktop, emissiveIntensity = 0.95f,
            polygonOffset = true, polygonOffsetFactor = -2f, polygonOffsetUnits = -2f)
        monScreen = Mesh(Geo.plane(0.98f, 0.80f, 4, 4), monScreenMat)
        monScreen.position.set(-0.08f, 2.155f, -0.112f)
        deskGroup.add(monScreen); room.registerInteractable(monScreen, "monitor")

        // ---- 7. Ergonomic multimedia keyboard with curved wrist rest & keycap banks ----
        val kbGroup = Group()
        kbGroup.position.set(0.06f, 1.50f, 0.34f); kbGroup.rotation.y = -0.11f; kbGroup.rotation.x = 0.05f
        deskGroup.add(kbGroup)
        val kbDeck = Mesh(Geo.box(0.90f, 0.038f, 0.25f, 4, 1, 2), PS1.material(color = 0x64748b))
        kbDeck.position.set(0f, 0.022f, -0.04f); kbDeck.castShadow = true; kbDeck.receiveShadow = true; kbGroup.add(kbDeck)
        val wristRest = Mesh(Geo.cylinder(0.048f, 0.048f, 0.91f, 8), matDarkPlastic)
        wristRest.rotation.z = (PI / 2).toFloat(); wristRest.scale.set(0.45f, 1.0f, 1.35f); wristRest.position.set(0f, 0.016f, 0.115f)
        wristRest.castShadow = true; wristRest.receiveShadow = true; kbGroup.add(wristRest)
        val matKeycaps = PS1.material(map = tex.keyboard)
        val mainKeyBlock = Mesh(Geo.box(0.54f, 0.024f, 0.14f, 6, 1, 3), matKeycaps)
        mainKeyBlock.position.set(-0.14f, 0.046f, -0.025f); mainKeyBlock.castShadow = true; kbGroup.add(mainKeyBlock)
        val fnKeyRow = Mesh(Geo.box(0.54f, 0.022f, 0.032f, 6, 1, 1), matKeycaps)
        fnKeyRow.position.set(-0.14f, 0.047f, -0.125f); kbGroup.add(fnKeyRow)
        val spacebarMesh = Mesh(Geo.box(0.24f, 0.025f, 0.034f), PS1.material(color = 0x334155))
        spacebarMesh.position.set(-0.15f, 0.048f, 0.055f); kbGroup.add(spacebarMesh)
        val arrowBlock = Mesh(Geo.box(0.11f, 0.022f, 0.08f, 2, 1, 2), matKeycaps)
        arrowBlock.position.set(0.21f, 0.045f, 0.01f); kbGroup.add(arrowBlock)
        val numpadBlock = Mesh(Geo.box(0.14f, 0.024f, 0.16f, 2, 1, 3), matKeycaps)
        numpadBlock.position.set(0.36f, 0.046f, -0.02f); numpadBlock.castShadow = true; kbGroup.add(numpadBlock)
        floatArrayOf(0.32f, 0.35f, 0.38f).forEachIndexed { idx, lx ->
            val kLed = Mesh(Geo.box(0.012f, 0.008f, 0.012f), PS1.material(color = 0x22c55e, emissive = 0x22c55e, emissiveIntensity = if (idx < 2) 0.9f else 0.1f))
            kLed.position.set(lx, 0.044f, -0.13f); kbGroup.add(kLed)
        }

        // ---- 8. Two-tone optical mouse & mousepad ----
        val mousePad = Mesh(Geo.box(0.32f, 0.012f, 0.27f, 3, 1, 3), PS1.material(color = 0x1e293b))
        mousePad.position.set(0.74f, 1.501f, 0.22f); mousePad.rotation.y = -0.16f; mousePad.receiveShadow = true; deskGroup.add(mousePad)
        val mouseGroup = Group()
        mouseGroup.position.set(0.74f, 1.508f, 0.22f); mouseGroup.rotation.y = -0.16f; deskGroup.add(mouseGroup)
        val mouseLower = Mesh(Geo.cylinder(0.056f, 0.062f, 0.032f, 10), matDarkPlastic)
        mouseLower.scale.set(1.0f, 1.0f, 1.55f); mouseLower.position.y = 0.016f; mouseLower.castShadow = true; mouseGroup.add(mouseLower)
        val mouseDome = Mesh(Geo.sphere(0.056f, 10, 8), PS1.material(color = 0x94a3b8))
        mouseDome.scale.set(0.95f, 0.58f, 1.52f); mouseDome.position.set(0f, 0.024f, 0.005f); mouseDome.castShadow = true; mouseGroup.add(mouseDome)
        val scrollWheel = Mesh(Geo.cylinder(0.014f, 0.014f, 0.012f, 8), matDarkPlastic)
        scrollWheel.rotation.z = (PI / 2).toFloat(); scrollWheel.position.set(0f, 0.048f, -0.042f); mouseGroup.add(scrollWheel)
        val opticalGlow = Mesh(Geo.box(0.03f, 0.006f, 0.04f), PS1.material(color = 0xef4444, emissive = 0xef4444, emissiveIntensity = 0.9f))
        opticalGlow.position.set(0f, 0.004f, 0.02f); mouseGroup.add(opticalGlow)

        // ---- 9. Soda can with pull-tab ----
        val sodaTex = createCanvasTex(64, 64, weather = Weather(seed = 52.0, blotch = 0.16, scratches = 8, speckle = 0.05)) { ctx, w, h ->
            ctx.fillStyle = "#15803d"; ctx.fillRect(0, 0, w, h)
            ctx.fillStyle = "#facc15"
            ctx.beginPath(); ctx.moveTo(6, 46); ctx.lineTo(58, 14); ctx.lineTo(58, 30); ctx.lineTo(6, 58); ctx.fill()
            ctx.fillStyle = "#dc2626"; ctx.fillRect(16, 22, 32, 12)
            ctx.fillStyle = "#ffffff"; ctx.font = "bold 9px sans-serif"; ctx.fillText("SURGE", 20, 31)
        }.tex
        val sodaCan = Mesh(Geo.cylinder(0.052f, 0.052f, 0.17f, 10), PS1.material(map = sodaTex))
        sodaCan.position.set(0.96f, 1.585f, -0.22f); sodaCan.castShadow = true; sodaCan.receiveShadow = true; deskGroup.add(sodaCan)
        val sodaRim = Mesh(Geo.cylinder(0.046f, 0.052f, 0.016f, 10), PS1.material(color = 0xcbd5e1))
        sodaRim.position.set(0.96f, 1.676f, -0.22f); deskGroup.add(sodaRim)

        // ---- 10. Lathed hourglass lava lamp with 6 rising blobs ----
        val lavaGroup = Group()
        lavaGroup.position.set(0.64f, 1.495f, -0.20f); deskGroup.add(lavaGroup)
        val lavaBasePts = listOf(floatArrayOf(0.001f, 0.0f), floatArrayOf(0.125f, 0.0f), floatArrayOf(0.11f, 0.05f),
            floatArrayOf(0.072f, 0.16f), floatArrayOf(0.112f, 0.25f), floatArrayOf(0.095f, 0.27f))
        val lavaBase = Mesh(GeoCurves.lathe(lavaBasePts, 10), PS1.material(color = 0x26304d))
        lavaBase.castShadow = true; lavaBase.receiveShadow = true
        lavaGroup.add(lavaBase); room.registerInteractable(lavaBase, "lavalamp")
        val lavaGlassMat = PS1.material(color = 0x6b21a8, emissive = 0x581c87, emissiveIntensity = 0.88f, transparent = true, opacity = 0.86f)
        val lavaGlass = Mesh(Geo.cylinder(0.056f, 0.112f, 0.54f, 10, 4), lavaGlassMat)
        lavaGlass.position.y = 0.53f; lavaGroup.add(lavaGlass); room.registerInteractable(lavaGlass, "lavalamp")
        val lavaCap = Mesh(Geo.cylinder(0.036f, 0.058f, 0.15f, 10), PS1.material(color = 0x26304d))
        lavaCap.position.y = 0.87f; lavaCap.castShadow = true; lavaGroup.add(lavaCap); room.registerInteractable(lavaCap, "lavalamp")
        val blobMat = PS1.material(color = 0xff2a85, emissive = 0xff1493, emissiveIntensity = 1.0f)
        for (i in 0 until 6) {
            val blob = Mesh(GeoCurves.icosahedron(0.044f - i * 0.004f, 1), blobMat)
            blobSpeed += 0.85f + i * 0.38f; blobOffset += i * 1.35f
            lavaGroup.add(blob); lavaBlobs += blob
            room.registerInteractable(blob, "lavalamp")
        }
    }

    /** Lava blobs rise and fall inside the tapered glass (from the HTML's animate loop). */
    fun update(t: Float) {
        for (idx in lavaBlobs.indices) {
            val blob = lavaBlobs[idx]
            val cycle = sin(t * blobSpeed[idx] + blobOffset[idx])
            blob.position.y = 0.5f + cycle * 0.15f
            blob.position.x = cos(t * 0.7f + idx) * 0.015f
            val s = 0.9f + sin(t * 1.5f + idx) * 0.2f
            blob.scale.set(s, s, s)
        }
    }
}
