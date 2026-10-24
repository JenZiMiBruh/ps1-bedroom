package com.ps1bedroom.room

import com.ps1bedroom.room.engine.Canvas2D
import com.ps1bedroom.room.engine.CanvasTex
import com.ps1bedroom.room.engine.Geo
import com.ps1bedroom.room.engine.Group
import com.ps1bedroom.room.engine.Material
import com.ps1bedroom.room.engine.Mesh
import com.ps1bedroom.room.engine.Object3D
import com.ps1bedroom.room.engine.PS1
import com.ps1bedroom.room.engine.Vec3
import com.ps1bedroom.room.engine.createCanvasTex
import com.ps1bedroom.room.engine.makeRng
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * JASPER (HTML lines 12505-12765): a German Shepherd built Blockbench-style from chunky boxes with pixel fur,
 * jointed like a toy so sitting and lying fold at the joints. Ice-blue eyes, his left one half brown. He wanders
 * between favourite spots chosen by the time, the weather and the sunbeam, hops on the bed at night and in storms,
 * breathes, looks around, and wags when you pet him.
 */
class Jasper(private val room: Room, private val env: Environment) {
    class Preset(val name: String, val coat: String, val dark: String, val light: String)
    companion object {
        val PRESETS = linkedMapOf(
            "classic" to Preset("Black & tan", "#b97a3e", "#1b1410", "#d9b27a"),
            "sable" to Preset("Sable", "#8a6a45", "#2e241a", "#b8996e"),
            "silver" to Preset("Black & silver", "#a8a6a0", "#151515", "#d6d4cf"),
            "red" to Preset("Black & red", "#a3532a", "#1a120e", "#d39a6a"),
            "black" to Preset("All black", "#1d1b1a", "#0e0d0d", "#3a3633"),
            "white" to Preset("White", "#ece7dc", "#cfc8ba", "#f8f5ee")
        )
        fun shortAngle(from: Float, to: Float): Float {
            var d = (to - from) % (PI.toFloat() * 2)
            if (d > PI) d -= PI.toFloat() * 2
            if (d < -PI) d += PI.toFloat() * 2
            return d
        }
    }

    val dogGroup = Group()
    private val dogBody = Group()
    private val dogHead = Group()
    private val dogTail = Group()
    private val dogLegs = ArrayList<Group>(); private val dogKnees = ArrayList<Group>()
    private val tailSegs = ArrayList<Group>()
    private lateinit var dogTan: Material; private lateinit var dogDark: Material; private lateinit var dogCream: Material
    private val rnd = Random.Default

    // Pose joint angles (radians): front = [shoulder, knee], back = [hip, hock]; +x swings a leg backward
    private val FRONT_LIE = floatArrayOf(-1.4f, 0.2f); private val FRONT_SIT = floatArrayOf(0.8f, 0.0f)
    private val BACK_LIE = floatArrayOf(-2.0f, 2.8f); private val BACK_SIT = floatArrayOf(-1.0f, 3.1f)
    private val TAIL_STAND = -1.15f; private val TAIL_LIE = -0.6f; private val TAIL_SIT = -1.6f; private val TAIL_LIE2 = 0f; private val TAIL_SIT2 = -0.4f
    private val HEAD_SLEEP = 0.25f; private val SIT_DROP = 0.24f; private val SIT_TILT = 0.85f

    // ---- Behaviour state ----
    private class Spot(val name: String, val pos: Vec3, val pose: String, var w: Float, val bed: Boolean = false)
    private class Jump(val from: Vec3, val to: Vec3, var t: Float, val fromY: Float, val toY: Float)
    val pathfinder = JasperPathfinder()
    private val pos = Vec3(0.6f, 0f, 1.6f)
    private var heading = PI.toFloat()
    private var state = "idle"
    var pose = "lie"; private set
    private var poseK = 1f; private var sitK = 0f
    private val path = ArrayList<Vec3>()
    private var stuckTimer = 0f
    private val lastWalkPos = Vec3()
    private var until = 0f
    private var spot: Spot? = null
    private var onBed = false
    private var wagUntil = 0f
    private var y = 0f
    private var jump: Jump? = null
    /** Things he reacts to that live elsewhere (the TV, the door, sitting at the desk come with later phases). */
    var doorOpen = false
    var bedroomTvOn = true
    var sittingAtDesk = false

    // ---- Optional care mechanics (Eat, Drink, Sleep, Play) ----
    var careEnabled = false
        set(v) { field = v; if (::bowlsGroup.isInitialized) bowlsGroup.visible = true }
    var hunger = 88f      // 0..100 (100 = full)
    var thirst = 90f      // 0..100 (100 = hydrated)
    var energy = 85f      // 0..100 (100 = rested)
    var funLevel = 86f    // 0..100 (100 = happy)
    private var actionUntil = 0f
    private var actionKind = "" // "eat", "drink", "play"
    private var pendingAction = "" // "eat", "drink", "play" while he walks over to the bowl / rug
    private var eatK = 0f
    private var nextEatSfxAt = 0f
    private var foodBowlFill = 0f   // 0f = empty, 1f = full of kibble
    private var waterBowlFill = 0f  // 0f = empty, 1f = full of water
    lateinit var bowlsGroup: Group; private set
    private lateinit var kibbleGroup: Group
    private lateinit var waterGroup: Group
    private lateinit var lowerJawMesh: Mesh
    private lateinit var tongueMesh: Mesh
    var onStateChanged: (() -> Unit)? = null

    fun moodLabel(): String = when {
        actionKind == "eat" || pendingAction == "eat" -> "Eating kibble 🍖"
        actionKind == "drink" || pendingAction == "drink" -> "Drinking water 💧"
        actionKind == "play" || pendingAction == "play" -> "Playing zoomies 🎾"
        pose == "sleep" || onBed -> "Sleeping zZz 💤"
        careEnabled && hunger < 30f -> "Hungry 🍖"
        careEnabled && thirst < 30f -> "Thirsty 💧"
        careEnabled && energy < 25f -> "Sleepy 💤"
        careEnabled && funLevel < 30f -> "Wants to play 🎾"
        else -> "Happy & cozy 🐾"
    }

    private fun dogPart(parent: Object3D, w: Float, h: Float, d: Float, mat: Material, x: Float, y: Float, z: Float, rx: Float = 0f, ry: Float = 0f, rz: Float = 0f): Mesh {
        val m = Mesh(Geo.box(w, h, d), mat)
        m.position.set(x, y, z); m.rotation.set(rx, ry, rz)
        m.castShadow = true; m.receiveShadow = true
        parent.add(m)
        room.registerInteractable(m, "dog")
        return m
    }

    fun build() {
        room.scene.add(dogGroup)
        // Grey pixel fur (tinted by each material's colour): little 2x1 strokes in a few shades
        val furTex = createCanvasTex(16, 16) { ctx, w, h ->
            val r = makeRng(77)
            for (yy in 0 until h) for (x in 0 until w) { val v = 222 + floor(r() * 3).toInt() * 11; ctx.fillStyle = "rgb($v,$v,$v)"; ctx.fillRect(x, yy, 1, 1) }
            for (i in 0 until 26) { ctx.fillStyle = "#cfcfcf"; ctx.fillRect(floor(r() * w), floor(r() * h), 2, 1) }
        }.tex
        fun furMat() = PS1.material(color = 0xffffff, map = furTex)
        dogTan = furMat(); dogDark = furMat(); dogCream = furMat()
        PS1.syncGrime(313)
        val dogNose = PS1.material(color = 0x0a0a0a)
        val dogPink = PS1.material(color = 0x8a4d48)
        // Body (origin at the middle of the torso; he faces +Z)
        dogBody.position.y = 0.6f
        dogGroup.add(dogBody)
        dogPart(dogBody, 0.28f, 0.28f, 0.66f, dogTan, 0f, 0f, -0.04f)              // torso
        dogPart(dogBody, 0.3f, 0.32f, 0.2f, dogTan, 0f, 0.0f, 0.33f)               // chest
        dogPart(dogBody, 0.3f, 0.09f, 0.5f, dogDark, 0f, 0.135f, -0.07f)           // black saddle
        dogPart(dogBody, 0.22f, 0.2f, 0.06f, dogCream, 0f, -0.03f, 0.43f)          // chest bib
        dogPart(dogBody, 0.2f, 0.05f, 0.46f, dogCream, 0f, -0.14f, 0.0f)           // belly
        val collarMat = PS1.material(color = 0xb91c1c)   // (made here, in the HTML's order, for the grime map)
        val tagTex = createCanvasTex(8, 8) { ctx, _, _ -> ctx.fillStyle = "#d4a017"; ctx.fillRect(1, 1, 6, 6); ctx.fillStyle = "#8a6a10"; ctx.fillRect(3, 3, 2, 2) }.tex
        val tagMat = PS1.material(map = tagTex, color = 0xffffff)
        // Head: block skull, cheeks, snout with a black mask and nose, upright ears
        dogHead.position.set(0f, 0.33f, 0.52f)
        dogBody.add(dogHead)
        dogPart(dogHead, 0.22f, 0.2f, 0.22f, dogTan, 0f, 0f, 0f)                    // skull
        // Neck (native fix v0.30): it belongs to the HEAD and slopes back down into the chest, so it turns with the skull
        // and the joint at the top can never open or poke through. Its lower end stays buried in the chest at any head angle.
        dogPart(dogHead, 0.13f, 0.24f, 0.12f, dogTan, 0f, -0.135f, -0.06f, 0.49f)
        dogPart(dogHead, 0.155f, 0.035f, 0.145f, collarMat, 0f, -0.15f, -0.07f, 0.49f) // collar
        dogPart(dogHead, 0.05f, 0.05f, 0.012f, tagMat, 0f, -0.185f, 0.02f) // tag hanging from the collar
        dogPart(dogHead, 0.2f, 0.06f, 0.21f, dogDark, 0f, 0.09f, -0.01f)           // dark crown (v0.31: its back sits 5 mm proud of the skull; flush, the two faces flickered)
        dogPart(dogHead, 0.13f, 0.1f, 0.16f, dogDark, 0f, -0.05f, 0.18f)           // snout (black mask)
        lowerJawMesh = dogPart(dogHead, 0.11f, 0.035f, 0.13f, dogCream, 0f, -0.105f, 0.15f) // lower jaw (animated when eating/drinking)
        tongueMesh = dogPart(dogHead, 0.052f, 0.014f, 0.085f, dogPink, 0f, -0.092f, 0.20f)  // pink tongue (extends when lapping water)
        tongueMesh.visible = false
        dogPart(dogHead, 0.06f, 0.04f, 0.03f, dogNose, 0f, -0.01f, 0.265f)         // nose
        for (s in intArrayOf(-1, 1)) {
            dogPart(dogHead, 0.08f, 0.09f, 0.035f, dogDark, s * 0.07f, 0.15f, -0.04f, 0f, 0f, s * -0.12f)   // ear base
            dogPart(dogHead, 0.045f, 0.07f, 0.03f, dogDark, s * 0.08f, 0.225f, -0.04f, 0f, 0f, s * -0.12f)  // ear tip
            dogPart(dogHead, 0.04f, 0.07f, 0.006f, dogPink, s * 0.07f, 0.165f, -0.02f, 0f, 0f, s * -0.12f) // inner ear
            dogPart(dogHead, 0.06f, 0.06f, 0.04f, dogCream, s * 0.085f, -0.06f, 0.08f)                      // cheeks
        }
        // Eyes: pixel-art, ice blue; his LEFT eye (the +X side) is brown on the bottom half
        eyeCanvases.clear(); eyeMats.clear()
        for ((s, split) in listOf(-1 to false, 1 to true)) {
            val obj = createCanvasTex(8, 8) { ctx, _, _ -> drawDogEye(ctx, split, closed = false) }
            val mat = PS1.material(map = obj.tex, color = 0xffffff, emissive = 0xffffff, emissiveMap = obj.tex, emissiveIntensity = 0.12f,
                polygonOffset = true, polygonOffsetFactor = -3f, polygonOffsetUnits = -3f)
            val eye = Mesh(Geo.plane(0.05f, 0.05f), mat)
            eye.position.set(s * 0.06f, 0.03f, 0.111f)
            dogHead.add(eye)
            eyeCanvases += obj to split
            eyeMats += mat
            dogPart(dogHead, 0.06f, 0.02f, 0.02f, dogCream, s * 0.06f, 0.07f, 0.105f) // tan eyebrow spot
        }
        // Legs: hip / shoulder pivots, plus a knee / hock joint in each so they fold under him
        arrayOf(floatArrayOf(0.09f, 0.3f), floatArrayOf(-0.09f, 0.3f), floatArrayOf(0.09f, -0.3f), floatArrayOf(-0.09f, -0.3f)).forEachIndexed { i, xz ->
            val back = i >= 2
            val pivot = Group()
            pivot.position.set(xz[0], -0.1f, xz[1])
            dogBody.add(pivot)
            dogPart(pivot, if (back) 0.11f else 0.085f, 0.24f, if (back) 0.14f else 0.09f, if (back) dogDark else dogTan, 0f, -0.12f, if (back) -0.01f else 0f)
            val knee = Group()
            knee.position.set(0f, -0.24f, 0f)
            pivot.add(knee)
            dogPart(knee, 0.065f, 0.22f, 0.07f, dogTan, 0f, -0.11f, 0f)
            dogPart(knee, 0.08f, 0.04f, 0.11f, dogCream, 0f, -0.235f, 0.02f) // paw
            dogLegs += pivot; dogKnees += knee
        }
        // Bushy tail: three blocks on joints so it can hang, wag and lie flat
        dogTail.position.set(0f, 0.06f, -0.38f)
        dogBody.add(dogTail)
        var tailParent: Object3D = dogTail
        val segs = arrayOf(Triple(0.075f, 0.15f, dogDark), Triple(0.09f, 0.15f, dogTan), Triple(0.075f, 0.12f, dogDark))
        segs.forEachIndexed { i, (s, len, mat) ->
            val j = Group()
            if (i > 0) j.position.set(0f, 0f, -floatArrayOf(0f, 0.15f, 0.15f)[i])
            tailParent.add(j)
            dogPart(j, s, s, len, mat, 0f, 0f, -len / 2)
            tailSegs += j; tailParent = j
        }
        applyColors(PRESETS.getValue("classic"))
        buildBowls()
        sendDog(pickDogSpot())
    }

    private fun buildBowls() {
        bowlsGroup = Group()
        bowlsGroup.position.set(1.82f, 0f, 2.32f)
        room.scene.add(bowlsGroup)
        val matMat = PS1.material(color = 0x334155)
        val matRim = PS1.material(color = 0x94a3b8)
        val matWell = PS1.material(color = 0x1e293b)
        val matFoodBowl = PS1.material(color = 0xdc2626)
        val matWaterBowl = PS1.material(color = 0x2563eb)
        val matKibbleA = PS1.material(color = 0x92400e)
        val matKibbleB = PS1.material(color = 0xb45309)
        val matKibbleC = PS1.material(color = 0x78350f)
        val matWater = PS1.material(color = 0x38bdf8, emissive = 0x0ea5e9, emissiveIntensity = 0.42f)
        val matWaterShine = PS1.material(color = 0xbae6fd, emissive = 0x7dd3fc, emissiveIntensity = 0.55f)
        val pad = Mesh(Geo.box(0.68f, 0.014f, 0.36f), matMat)
        pad.position.set(0f, 0.007f, 0f); pad.receiveShadow = true; bowlsGroup.add(pad)
        room.registerInteractable(pad, "dogFood")

        // Food bowl (left, at x = -0.16 -> world (1.66, 0, 2.32))
        val fb = Mesh(Geo.cylinder(0.115f, 0.135f, 0.062f, 10), matFoodBowl)
        fb.position.set(-0.16f, 0.036f, 0f); fb.castShadow = true; bowlsGroup.add(fb); room.registerInteractable(fb, "dogFood")
        val fbRim = Mesh(Geo.cylinder(0.120f, 0.120f, 0.012f, 10), matRim)
        fbRim.position.set(-0.16f, 0.066f, 0f); bowlsGroup.add(fbRim); room.registerInteractable(fbRim, "dogFood")
        val fbWell = Mesh(Geo.cylinder(0.098f, 0.098f, 0.004f, 10), matWell)
        fbWell.position.set(-0.16f, 0.071f, 0f); bowlsGroup.add(fbWell); room.registerInteractable(fbWell, "dogFood")

        // Kibble mound group sitting ABOVE the dark bowl interior (y = 0.073+), starting empty until tapped
        kibbleGroup = Group()
        kibbleGroup.position.set(-0.16f, 0.072f, 0f)
        bowlsGroup.add(kibbleGroup)
        val kBase = Mesh(Geo.cylinder(0.092f, 0.096f, 0.018f, 10), matKibbleA)
        kBase.position.set(0f, 0.009f, 0f); kibbleGroup.add(kBase); room.registerInteractable(kBase, "dogFood")
        val kMound = Mesh(Geo.cylinder(0.062f, 0.088f, 0.016f, 8), matKibbleB)
        kMound.position.set(0f, 0.022f, 0f); kibbleGroup.add(kMound); room.registerInteractable(kMound, "dogFood")
        val nuggets = arrayOf(
            floatArrayOf(0f, 0.031f, 0f), floatArrayOf(-0.035f, 0.024f, 0.022f), floatArrayOf(0.036f, 0.024f, 0.018f),
            floatArrayOf(-0.025f, 0.025f, -0.032f), floatArrayOf(0.028f, 0.025f, -0.028f), floatArrayOf(0.052f, 0.018f, -0.005f),
            floatArrayOf(-0.052f, 0.018f, -0.004f), floatArrayOf(0.004f, 0.020f, 0.048f), floatArrayOf(-0.006f, 0.020f, -0.048f)
        )
        nuggets.forEachIndexed { idx, p ->
            val mat = when (idx % 3) { 0 -> matKibbleB; 1 -> matKibbleA; else -> matKibbleC }
            val nug = Mesh(Geo.box(0.026f, 0.016f, 0.024f), mat)
            nug.position.set(p[0], p[1], p[2]); nug.rotation.y = idx * 0.7f
            kibbleGroup.add(nug); room.registerInteractable(nug, "dogFood")
        }
        kibbleGroup.visible = false

        // Water bowl (right, at x = +0.16 -> world (1.98, 0, 2.32))
        val wb = Mesh(Geo.cylinder(0.115f, 0.135f, 0.062f, 10), matWaterBowl)
        wb.position.set(0.16f, 0.036f, 0f); wb.castShadow = true; bowlsGroup.add(wb); room.registerInteractable(wb, "dogWater")
        val wbRim = Mesh(Geo.cylinder(0.120f, 0.120f, 0.012f, 10), matRim)
        wbRim.position.set(0.16f, 0.066f, 0f); bowlsGroup.add(wbRim); room.registerInteractable(wbRim, "dogWater")
        val wbWell = Mesh(Geo.cylinder(0.098f, 0.098f, 0.004f, 10), matWell)
        wbWell.position.set(0.16f, 0.071f, 0f); bowlsGroup.add(wbWell); room.registerInteractable(wbWell, "dogWater")

        // Glossy cyan water surface sitting ABOVE the dark bowl interior (y = 0.073+), starting empty until tapped
        waterGroup = Group()
        waterGroup.position.set(0.16f, 0.072f, 0f)
        bowlsGroup.add(waterGroup)
        val wDisc = Mesh(Geo.cylinder(0.095f, 0.096f, 0.014f, 10), matWater)
        wDisc.position.set(0f, 0.007f, 0f); waterGroup.add(wDisc); room.registerInteractable(wDisc, "dogWater")
        val wRipple = Mesh(Geo.cylinder(0.058f, 0.058f, 0.003f, 10), matWaterShine)
        wRipple.position.set(0.01f, 0.015f, -0.008f); waterGroup.add(wRipple); room.registerInteractable(wRipple, "dogWater")
        waterGroup.visible = false
    }

    private val eyeCanvases = ArrayList<Pair<CanvasTex, Boolean>>()
    private val eyeMats = ArrayList<Material>()
    private var eyesClosedState = false
    private var nextBlinkAt = 2.4f
    private var blinkEndAt = 0f
    private var doubleBlinkAt = 0f

    private fun drawDogEye(ctx: Canvas2D, split: Boolean, closed: Boolean = false) {
        ctx.clearRect(0, 0, 8, 8)
        if (closed) {
            ctx.fillStyle = colors.coat; ctx.fillRect(0, 0, 8, 8)
            ctx.fillStyle = colors.dark; ctx.fillRect(0, 0, 8, 3)       // upper lid fold
            ctx.fillStyle = "#090705"; ctx.fillRect(0, 4, 8, 2)         // closed lash line
            ctx.fillStyle = colors.light; ctx.fillRect(1, 6, 6, 1)      // soft lower lid highlight
            return
        }
        ctx.fillStyle = "#120c08"; ctx.fillRect(0, 0, 8, 8)            // dark lid rim
        ctx.fillStyle = "#2e7fd0"; ctx.fillRect(1, 1, 6, 6)            // iris
        ctx.fillStyle = "#7cc4f5"; ctx.fillRect(1, 1, 6, 2)            // lighter top
        if (split) { ctx.fillStyle = "#5a2f12"; ctx.fillRect(1, 4, 6, 3); ctx.fillStyle = "#7a4318"; ctx.fillRect(1, 4, 6, 1) } // brown below
        ctx.fillStyle = "#050505"; ctx.fillRect(3, 3, 2, 2)            // pupil
        ctx.fillStyle = "#ffffff"; ctx.fillRect(4, 2, 1, 1)            // catch-light
    }

    private fun refreshEyes(closed: Boolean) {
        eyesClosedState = closed
        for (i in eyeCanvases.indices) {
            val (obj, split) = eyeCanvases[i]
            drawDogEye(obj.ctx, split, closed)
            obj.tex.needsUpdate()
            eyeMats[i].emissiveIntensity = if (closed) 0f else 0.12f
        }
    }

    /** jasperColors: the chosen preset (or "custom") and its three colours. */
    var colors = PRESETS.getValue("classic"); private set
    var colorPreset = "classic"; private set
    fun applyColors(p: Preset, key: String = PRESETS.entries.firstOrNull { it.value === p }?.key ?: "custom") {
        colors = p; colorPreset = key
        dogTan.color.setStyle(p.coat); dogDark.color.setStyle(p.dark); dogCream.color.setStyle(p.light)
        if (eyeCanvases.isNotEmpty()) refreshEyes(eyesClosedState)
    }
    /** One part's colour from the custom swatches ("coat", "dark" or "light"). */
    fun setPart(part: String, hex: String) {
        val c = colors
        applyColors(Preset("Custom", if (part == "coat") hex else c.coat, if (part == "dark") hex else c.dark, if (part == "light") hex else c.light), "custom")
    }

    // ---- Favourite spots, chosen by the time, the weather and what you're doing ----
    private fun sunbeamFloorPoint(): Vec3? {
        val from = Vec3(0.8f, 2.4f, -2.9f)
        val dir = Vec3().subVectors(Room.roomLightTargetPos, from)
        if (dir.y > -0.05f) return null
        val k = -from.y / dir.y
        val p = from.clone().addScaledVector(dir, k)
        if (p.z < -2.4f || p.z > 2.8f || p.x < -2.9f || p.x > 1.3f) return null // landed on the bed / out of reach
        return p
    }
    private fun dogSpots(): List<Spot> {
        val h = env.hour
        val night = h >= 22 || h < 7
        val spots = arrayListOf(
            Spot("rug", Vec3(0.6f, 0f, 1.5f), if (night) "sleep" else "lie", 2f),
            Spot("door", Vec3(2.3f, 0f, 2.65f), "sit", if (doorOpen) 3f else 1f),
            Spot("bed", Vec3(2.75f, 0.85f, 0.25f), "sleep", if (night) 6f else 1f, bed = true),
            Spot("tv", Vec3(-1.25f, 0f, 1.55f), "lie", if (bedroomTvOn) 2.5f else 0.5f),
            Spot("desk", Vec3(-0.35f, 0f, 0.15f), "sit", if (sittingAtDesk) 5f else 0.6f),
            Spot("bowls", Vec3(1.66f, 0f, 2.02f), "sit", if (careEnabled && (hunger < 45f || thirst < 45f)) 4.5f else 0.7f)
        )
        val sun = if (env.daylight > 0.35 && room.blindsOpenState != 2 && env.moonBaseIntensity > 0.5f) sunbeamFloorPoint() else null
        if (sun != null) spots += Spot("sunbeam", sun, "lie", 5f)
        if (env.isPrecipNow() && env.W.base == "storm") for (sp in spots) if (sp.name == "bed") sp.w += 4f // hides on the bed in storms
        return spots
    }
    private fun pickDogSpot(): Spot {
        val spots = dogSpots().filter { spot == null || it.name != spot!!.name }
        var r = rnd.nextDouble() * spots.sumOf { it.w.toDouble() }
        for (sp in spots) { r -= sp.w; if (r <= 0) return sp }
        return spots[0]
    }
    private fun sendDog(s: Spot) {
        spot = s
        path.clear()
        stuckTimer = 0f
        val startFloor = if (onBed) {
            jump = Jump(pos.clone(), Vec3(1.25f, 0f, 0.25f), 0f, 0.9f, 0f)
            onBed = false
            Vec3(1.25f, 0f, 0.25f)
        } else {
            pos.clone()
        }
        val targetFloor = if (s.bed) Vec3(1.25f, 0f, 0.25f) else Vec3(s.pos.x, 0f, s.pos.z)
        val computed = pathfinder.findPath(startFloor, targetFloor, obstacles)
        path.addAll(computed)
        lastWalkPos.copy(pos)
        state = if (path.isNotEmpty() || jump != null) "walk" else "rest"
    }

    /** Furniture he walks around (TV cart, chair, heater) with clearance radii; set by the app. */
    var obstacles: List<Pair<Object3D, Float>> = emptyList()
    // native fix: he used to walk straight through the bed, the desk, the chair and everything else
    private fun keepClear() {
        if (pos.x > 1.38f && pos.z < 1.58f) pos.x = 1.38f            // bed
        if (pos.x < -0.1f && pos.z < -0.45f) pos.z = -0.45f          // desk
        if (pos.x < -2.8f && pos.z > 1.5f && pos.z < 3.05f) pos.x = -2.8f // dresser
        if (pos.x > 0.7f && pos.x < 1.55f && pos.z < -2.15f) pos.z = -2.15f // nightstand
        for ((o, r) in obstacles) {
            val dx = pos.x - o.position.x; val dz = pos.z - o.position.z
            val d = kotlin.math.hypot(dx, dz)
            if (d < r && d > 0.0001f) { pos.x = o.position.x + dx / d * r; pos.z = o.position.z + dz / d * r }
        }
    }
    private val to = Vec3()
    fun update(t: Float, dt: Float) {
        val j = jump
        if (j != null) {
            // Jumping on / off the bed
            j.t = min(1f, j.t + dt * 1.8f)
            val k = j.t
            pos.set(j.from.x + (j.to.x - j.from.x) * k, j.from.y + (j.to.y - j.from.y) * k, j.from.z + (j.to.z - j.from.z) * k)
            y = j.fromY + (j.toY - j.fromY) * k + sin(PI.toFloat() * k) * 0.45f
            if (k >= 1) jump = null
        } else if (state == "walk") {
            if (path.isEmpty()) {
                val s = spot
                if (s != null && s.bed && !onBed && jump == null) {
                    jump = Jump(pos.clone(), Vec3(s.pos.x, 0f, s.pos.z), 0f, 0f, 0.83f)
                    onBed = true
                }
                state = "rest"
                pose = s?.pose ?: "lie"
            } else {
                val target = path[0]
                to.set(target.x - pos.x, 0f, target.z - pos.z)
                val dist = to.length()
                val want = atan2(to.x, to.z)
                heading += shortAngle(heading, want) * min(1f, dt * 5)
                pose = "walk"
                val arrivalDist = if (path.size > 1) 0.12f else 0.08f
                if (dist < arrivalDist) {
                    path.removeAt(0)
                    stuckTimer = 0f
                    if (path.isEmpty()) {
                        val s = spot!!
                        if (s.bed) { jump = Jump(pos.clone(), Vec3(s.pos.x, 0f, s.pos.z), 0f, 0f, 0.83f); onBed = true }
                        state = "rest"
                        if (pendingAction == "eat") {
                            heading = 0f // face +Z directly toward the food bowl
                            actionKind = "eat"
                            pendingAction = ""
                            actionUntil = t + 6.0f
                            wagUntil = t + 6.5f
                            nextEatSfxAt = t + 0.15f
                            pose = "eat"
                            until = t + 28f
                            onStateChanged?.invoke()
                        } else if (pendingAction == "drink") {
                            heading = 0f // face +Z directly toward the water bowl
                            actionKind = "drink"
                            pendingAction = ""
                            actionUntil = t + 6.0f
                            wagUntil = t + 6.5f
                            nextEatSfxAt = t + 0.15f
                            pose = "eat"
                            until = t + 28f
                            onStateChanged?.invoke()
                        } else if (pendingAction == "play") {
                            actionKind = "play"
                            pendingAction = ""
                            actionUntil = t + 6.0f
                            wagUntil = t + 6.5f
                            pose = s.pose
                            until = t + 35f
                            onStateChanged?.invoke()
                        } else {
                            pose = s.pose
                            until = t + 40 + (rnd.nextDouble() * 80).toFloat()
                        }
                    }
                } else {
                    val walkSpd = if (pendingAction.isNotEmpty()) 1.25f else 0.85f
                    val stepDist = min(dist, walkSpd * dt)
                    pos.addScaledVector(to.normalize(), stepDist)
                    keepClear()

                    // Anti-stuck detector: if dynamic obstacles or pushback prevent forward motion, replan path
                    val actualProgress = kotlin.math.hypot(pos.x - lastWalkPos.x, pos.z - lastWalkPos.z)
                    if (actualProgress < stepDist * 0.25f) {
                        stuckTimer += dt
                        if (stuckTimer > 1.2f) {
                            stuckTimer = 0f
                            val dest = spot?.let { if (it.bed) Vec3(1.25f, 0f, 0.25f) else Vec3(it.pos.x, 0f, it.pos.z) } ?: target
                            val replanned = pathfinder.findPath(pos, dest, obstacles)
                            path.clear()
                            path.addAll(replanned)
                        }
                    } else {
                        stuckTimer = 0f
                    }
                }
            }
            lastWalkPos.set(pos.x, pos.y, pos.z)
        } else if (state == "rest" || state == "idle") {
            if (spot?.name == "desk" && !sittingAtDesk) until = min(until, t + 5)
            if (t > until && pendingAction.isEmpty() && t >= actionUntil) sendDog(pickDogSpot())
        }
        if (jump == null) y += ((if (onBed) 0.83f else 0f) - y) * min(1f, dt * 6)

        if (actionUntil > 0f && t >= actionUntil) {
            if (actionKind == "eat") {
                foodBowlFill = 0f
                if (::kibbleGroup.isInitialized) kibbleGroup.visible = false
                pose = "sit"
            } else if (actionKind == "drink") {
                waterBowlFill = 0f
                if (::waterGroup.isInitialized) waterGroup.visible = false
                pose = "sit"
            }
            actionUntil = 0f
            actionKind = ""
            onStateChanged?.invoke()
        }
        val eatingOrDrinking = t < actionUntil && (actionKind == "eat" || actionKind == "drink")
        val playingNow = t < actionUntil && actionKind == "play"
        if (eatingOrDrinking) {
            // Turn directly toward the bowl at +Z (heading = 0f)
            heading += shortAngle(heading, 0f) * min(1f, dt * 8f)
            // Deplete the food / water in the bowl visibly as he eats / drinks
            val rem = ((actionUntil - t) / 6.0f).coerceIn(0f, 1f)
            if (actionKind == "eat") {
                foodBowlFill = rem
                if (::kibbleGroup.isInitialized) {
                    kibbleGroup.visible = rem > 0.04f
                    val s = 0.25f + 0.75f * rem
                    kibbleGroup.scale.set(0.65f + 0.35f * rem, s, 0.65f + 0.35f * rem)
                }
                if (t >= nextEatSfxAt) {
                    nextEatSfxAt = t + 0.52f + rnd.nextFloat() * 0.18f
                    onSound("eat", soundPos())
                }
            } else {
                waterBowlFill = rem
                if (::waterGroup.isInitialized) {
                    waterGroup.visible = rem > 0.04f
                    val s = 0.25f + 0.75f * rem
                    waterGroup.scale.set(0.75f + 0.25f * rem, s, 0.75f + 0.25f * rem)
                }
                if (t >= nextEatSfxAt) {
                    nextEatSfxAt = t + 0.38f + rnd.nextFloat() * 0.14f
                    onSound("drink", soundPos())
                }
            }
        }
        // Blend between poses
        val lying = if ((pose == "lie" || pose == "sleep") && !eatingOrDrinking) 1f else 0f
        val sitting = if (pose == "sit" && !eatingOrDrinking) 1f else 0f
        val eatingTarget = if (eatingOrDrinking) 1f else 0f
        poseK += (lying - poseK) * min(1f, dt * 3)
        sitK += (sitting - sitK) * min(1f, dt * 3)
        eatK += (eatingTarget - eatK) * min(1f, dt * 5f)
        dogGroup.position.set(pos.x, y, pos.z)
        dogGroup.rotation.y = heading
        val breathe = sin(t * (if (pose == "sleep") 1.4f else 3f)) * (if (pose == "sleep") 0.012f else 0.005f)
        // When eating/drinking (eatK > 0), lower his chest and pitch his body DOWN (+0.38 rad) so his snout reaches into the bowl
        dogBody.position.y = 0.6f - poseK * 0.38f - sitK * SIT_DROP - eatK * 0.15f + breathe
        dogBody.rotation.x = -sitK * SIT_TILT + eatK * 0.38f
        // a thunder flinch: a quick hop and a shiver that settles over ~1.5 s
        if (startle > 0f) { startle = max(0f, startle - dt * 0.7f); dogBody.position.y += startle * startle * 0.07f; dogBody.rotation.z = sin(t * 38f) * 0.05f * startle } else dogBody.rotation.z = 0f
        val walkSwing = if (pose == "walk") sin(t * 9) * 0.55f else 0f
        // Joint angles per pose [shoulder/hip, knee/hock], blended by how far into the pose he is
        val lieK = poseK; val standK = max(0f, 1 - lieK - sitK - eatK)
        for (i in dogLegs.indices) {
            val front = i < 2
            val phase = if (i == 0 || i == 3) 1f else -1f
            val walk = walkSwing * phase
            val lie = if (front) FRONT_LIE else BACK_LIE; val sit = if (front) FRONT_SIT else BACK_SIT
            val eatLeg = if (front) -0.58f else -0.22f
            val eatKnee = if (front) 0.42f else 0.12f
            dogLegs[i].rotation.x = walk * standK + lie[0] * lieK + sit[0] * sitK + eatLeg * eatK
            dogKnees[i].rotation.x = (if (front) -0.25f else 0.35f) * max(0f, walk * phase * (if (front) -1f else 1f)) * standK + lie[1] * lieK + sit[1] * sitK + eatKnee * eatK
        }
        // Head: looks around, rests down when asleep, tilts when you pet him, or bobs into the bowl when eating/drinking
        val petting = t < wagUntil
        if (careEnabled) {
            // Gentle need decay over ~30-45 minutes; sleeping restores energy
            hunger = (hunger - dt * 0.032f).coerceIn(10f, 100f)
            thirst = (thirst - dt * 0.042f).coerceIn(10f, 100f)
            funLevel = (funLevel - dt * 0.028f).coerceIn(10f, 100f)
            energy = if (pose == "sleep" || onBed) (energy + dt * 0.25f).coerceIn(10f, 100f) else (energy - dt * 0.022f).coerceIn(10f, 100f)
        }
        val chewRate = if (actionKind == "drink") 16f else 12f
        val chewWave = max(0f, sin(t * chewRate))
        dogHead.rotation.x = when {
            eatingOrDrinking -> 0.56f + sin(t * chewRate) * 0.18f
            playingNow -> -0.15f + sin(t * 9f) * 0.18f
            pose == "sleep" -> HEAD_SLEEP
            else -> sin(t * 0.4f) * 0.08f
        }
        dogHead.rotation.y = when {
            eatingOrDrinking -> sin(t * 3.2f) * 0.06f
            pose == "sleep" -> 0.3f
            else -> sin(t * (if (playingNow) 4.5f else 0.27f)) * 0.35f
        }
        dogHead.rotation.z = if (eatingOrDrinking) 0f else if (petting || playingNow) sin(t * (if (playingNow) 6f else 3f)) * 0.25f else 0f
        // v0.54: turn to look at whatever just happened (thunder at the window, the TV, the boombox, you)
        if (!eatingOrDrinking && pose != "sleep") reactHead(t)
        if (::lowerJawMesh.isInitialized) {
            lowerJawMesh.position.y = -0.105f - (if (eatingOrDrinking) chewWave * 0.022f else 0f)
            lowerJawMesh.rotation.x = if (eatingOrDrinking) chewWave * 0.28f else 0f
        }
        if (::tongueMesh.isInitialized) {
            val lapping = eatingOrDrinking && actionKind == "drink"
            tongueMesh.visible = lapping
            if (lapping) {
                tongueMesh.position.z = 0.19f + chewWave * 0.055f
                tongueMesh.position.y = -0.095f - chewWave * 0.018f
                tongueMesh.rotation.x = 0.25f + chewWave * 0.35f
            }
        }
        // Tail: hangs in a soft curve, wags side to side, rests on the floor when he's down
        dogTail.rotation.x = TAIL_STAND * standK + TAIL_LIE * lieK + TAIL_SIT * sitK
        tailSegs[1].rotation.x = -0.25f * standK + TAIL_LIE2 * lieK + TAIL_SIT2 * sitK
        tailSegs[2].rotation.x = -0.3f * standK
        dogTail.rotation.y = sin(t * (if (petting) 16f else (if (pose == "walk") 7f else 2f))) * (if (petting) 0.6f else (if (pose == "sleep") 0.04f else 0.15f))
        // Eyes: closed whenever he's sleeping or curled up on the bed; natural blinking animation when awake
        val sleepingOrOnBed = pose == "sleep" || onBed
        val blinking = if (!sleepingOrOnBed) {
            if (t >= nextBlinkAt) {
                blinkEndAt = t + 0.14f
                // Occasional double-blink for extra life
                if (doubleBlinkAt == 0f && rnd.nextFloat() < 0.24f) {
                    doubleBlinkAt = blinkEndAt + 0.12f
                    nextBlinkAt = doubleBlinkAt
                } else {
                    doubleBlinkAt = 0f
                    nextBlinkAt = t + 2.6f + rnd.nextFloat() * 3.4f
                }
            }
            t < blinkEndAt
        } else false
        val wantEyesClosed = sleepingOrOnBed || blinking
        if (wantEyesClosed != eyesClosedState) refreshEyes(wantEyesClosed)
        // Soft snores while asleep at night
        if (pose == "sleep" && state == "rest" && t > nextSnore) { nextSnore = t + 4.2f + rnd.nextFloat() * 1.5f; onSound("snore", soundPos()) }
    }
    private var nextSnore = 0f

    // ---- v0.54: he reacts to what happens in the room ----
    private var lookAt: Vec3? = null
    private var lookUntil = 0f
    private var startle = 0f
    private var lastReact = HashMap<String, Float>()
    /** Where you are (the camera), fed by the app each frame while you walk around or sit. */
    var playerPos: Vec3? = null
    private var nextPlayerLook = 0f
    private val TV_POS = Vec3(-1.95f, 1.2f, 0.65f)
    private val WINDOW_POS = Vec3(0.8f, 2.4f, -3.3f)
    private val BOOMBOX_POS = Vec3(-2.6f, 1.3f, -1.0f)
    private val DOOR_POS = Vec3(2.3f, 1.0f, 3.0f)
    private fun lookFor(at: Vec3, t: Float, secs: Float) { lookAt = at; lookUntil = t + secs }
    private val awake get() = pose != "sleep" && !onBed
    private val busy get() = pendingAction.isNotEmpty() || actionKind.isNotEmpty() || jump != null
    /**
     * Something happened: "thunder", "tv" (TV on or YouTube starting), "music" (radio / your music starts),
     * "console" (a PlayStation boots), "lightsOut" (the room goes dark at night), "door".
     */
    fun react(kind: String, t: Float) {
        // the same thing over and over doesn't keep getting a reaction
        if (t - (lastReact[kind] ?: -99f) < (if (kind == "thunder") 6f else 12f)) return
        lastReact[kind] = t
        when (kind) {
            "thunder" -> {
                startle = 1f
                lookFor(WINDOW_POS, t, 3.5f)
                if (!awake && !onBed) { pose = "lie" }
                if (!onBed && !busy && rnd.nextFloat() < 0.55f) {
                    // a close one: he hides on the bed
                    dogSpots().firstOrNull { it.name == "bed" }?.let { sendDog(it) }
                    toast("⛈ Jasper scrambles onto the bed to hide from the thunder")
                } else if (rnd.nextFloat() < 0.4f) onSound("boof", soundPos())
            }
            "tv" -> {
                if (!awake) return
                lookFor(TV_POS, t, 4f); wagUntil = t + 2f
                if (!busy && spot?.name != "tv" && rnd.nextFloat() < 0.6f) dogSpots().firstOrNull { it.name == "tv" }?.let { sendDog(it) }
            }
            "music" -> {
                if (!awake) return
                lookFor(BOOMBOX_POS, t, 3f); wagUntil = t + 4f   // ears up, head tilt, happy tail
            }
            "console" -> {
                if (!awake) return
                lookFor(TV_POS, t, 3.5f); wagUntil = t + 1.5f
            }
            "door" -> {
                if (!awake) return
                lookFor(DOOR_POS, t, 3f)
                if (!busy) dogSpots().firstOrNull { it.name == "door" }?.let { sendDog(it) }
                onSound("boof", soundPos())
            }
            "lightsOut" -> {
                if (busy || onBed) return
                // big yawn, then off to bed
                if (rnd.nextFloat() < 0.7f) { dogSpots().firstOrNull { it.name == "bed" }?.let { sendDog(it) }; toast("🌙 Jasper yawns and pads off to bed") }
            }
        }
    }
    /** Head turn toward whatever caught his attention (and you, now and then, when you're close). Returns false if nothing. */
    private fun reactHead(t: Float): Boolean {
        val pp = playerPos
        if (t > lookUntil && pp != null && awake && t > nextPlayerLook) {
            nextPlayerLook = t + 6f + rnd.nextFloat() * 8f
            val d = kotlin.math.hypot(pp.x - pos.x, pp.z - pos.z)
            if (d < 1.8f) { lookFor(pp, t, 2.5f); if (d < 1.1f) wagUntil = t + 2f }
        }
        val at = lookAt ?: return false
        if (t > lookUntil) { lookAt = null; return false }
        val want = atan2(at.x - pos.x, at.z - pos.z)
        val yaw = shortAngle(heading, want).coerceIn(-1.0f, 1.0f)
        dogHead.rotation.y += (yaw - dogHead.rotation.y) * 0.25f
        val dist = kotlin.math.hypot(at.x - pos.x, at.z - pos.z).coerceAtLeast(0.3f)
        val pitch = -kotlin.math.atan((at.y - (y + 0.75f)) / dist).coerceIn(-0.5f, 0.3f)
        dogHead.rotation.x += (pitch - dogHead.rotation.x) * 0.25f
        return true
    }
    /** Sound hook (Sound.dogSound) and toasts, set by the app. */
    var onSound: (String, Vec3) -> Unit = { _, _ -> }
    var toast: (String) -> Unit = {}
    private fun soundPos() = Vec3(dogGroup.position.x, y + 0.5f, dogGroup.position.z)
    private val DOG_PETS = listOf(
        "He thumps his tail and leans his whole weight into your hand.",
        "Big stretch, a happy sneeze, then he flops over for belly rubs.",
        "One ice-blue eye and one half brown, staring right into your soul. He knows he is handsome.",
        "He gives a soft \"boof\" and nudges your hand for more.",
        "Ears up! He heard something outside. False alarm.",
        "He rests his chin on your knee and sighs like it has been a long day."
    )

    /** Tap: tail thumps and a head tilt; a sleeping Jasper opens one eye and lies awake (unless curled up on the bed). */
    /** 1.3 hooks: petting (achievements), sleep mode sends him to bed, the alarm makes him wag. */
    var onPet: () -> Unit = {}
    fun goToBed() { pendingAction = ""; actionKind = ""; actionUntil = 0f; dogSpots().firstOrNull { it.name == "bed" }?.let { sendDog(it) }; onStateChanged?.invoke() }
    fun wag(t: Float, secs: Float) { wagUntil = t + secs }
    fun feed(t: Float) {
        hunger = 100f
        funLevel = (funLevel + 10f).coerceAtMost(100f)
        foodBowlFill = 1f
        if (::kibbleGroup.isInitialized) {
            kibbleGroup.visible = true
            kibbleGroup.scale.set(1f, 1f, 1f)
        }
        wagUntil = t + 8.0f
        val foodSpot = Vec3(1.66f, 0f, 2.02f) // stands directly in front of the red food bowl at (1.66, 0, 2.32), facing +Z
        if (!onBed && jump == null && kotlin.math.hypot(pos.x - foodSpot.x, pos.z - foodSpot.z) < 0.22f) {
            path.clear(); state = "rest"; pose = "eat"; heading = 0f
            pendingAction = ""; actionKind = "eat"; actionUntil = t + 6.0f; nextEatSfxAt = t + 0.15f; until = t + 28f
        } else {
            actionKind = ""; actionUntil = 0f; pendingAction = "eat"
            sendDog(Spot("bowls", foodSpot, "eat", 5f))
        }
        toast("🍖 Poured crunchy kibble into Jasper's red food bowl! He trots over to eat.")
        onSound("fillFood", Vec3(1.66f, 0.1f, 2.32f))
        onSound("boof", soundPos())
        onStateChanged?.invoke()
    }
    fun giveWater(t: Float) {
        thirst = 100f
        waterBowlFill = 1f
        if (::waterGroup.isInitialized) {
            waterGroup.visible = true
            waterGroup.scale.set(1f, 1f, 1f)
        }
        wagUntil = t + 8.0f
        val waterSpot = Vec3(1.98f, 0f, 2.02f) // stands directly in front of the blue water bowl at (1.98, 0, 2.32), facing +Z
        if (!onBed && jump == null && kotlin.math.hypot(pos.x - waterSpot.x, pos.z - waterSpot.z) < 0.22f) {
            path.clear(); state = "rest"; pose = "eat"; heading = 0f
            pendingAction = ""; actionKind = "drink"; actionUntil = t + 6.0f; nextEatSfxAt = t + 0.15f; until = t + 28f
        } else {
            actionKind = ""; actionUntil = 0f; pendingAction = "drink"
            sendDog(Spot("bowls", waterSpot, "eat", 5f))
        }
        toast("💧 Poured cool fresh water into Jasper's blue water bowl! *lap lap lap*")
        onSound("fillWater", Vec3(1.98f, 0.1f, 2.32f))
        onSound("boof", soundPos())
        onStateChanged?.invoke()
    }
    fun playWith(t: Float) {
        funLevel = 100f
        energy = (energy - 8f).coerceAtLeast(15f)
        wagUntil = t + 8.0f
        onPet()
        val rugSpot = Vec3(0.6f, 0f, 1.5f)
        if (!onBed && jump == null && Vec3(pos.x - rugSpot.x, 0f, pos.z - rugSpot.z).length() < 0.25f) {
            pendingAction = ""; actionKind = "play"; actionUntil = t + 6.0f
        } else {
            actionKind = ""; actionUntil = 0f; pendingAction = "play"
            sendDog(Spot("rug", rugSpot, "sit", 5f))
        }
        toast("🎾 Playtime! Jasper does a play-bow, spins around on the rug, and wags his whole body!")
        onSound("boof", soundPos())
        onStateChanged?.invoke()
    }
    fun sendToSleep(t: Float) {
        energy = (energy + 35f).coerceAtMost(100f)
        pendingAction = ""; actionKind = ""; actionUntil = 0f; wagUntil = t + 2.0f
        goToBed()
        toast("🛏️ Tucked Jasper in on the bed for a cozy nap.")
        onStateChanged?.invoke()
    }
    fun petDog(t: Float) {
        onPet()
        funLevel = (funLevel + 15f).coerceAtMost(100f)
        wagUntil = t + 2.5f
        if (onBed) {
            pose = "sleep"
            toast("🐕 Jasper keeps his eyes closed on the bed, sighs happily, and thumps his tail twice.")
        } else if (pose == "sleep") {
            pose = "lie"
            toast("🐕 Jasper opens his eyes, yawns, and thumps his tail twice.")
        } else {
            toast("🐕 JASPER: ${DOG_PETS[rnd.nextInt(DOG_PETS.size)]}")
        }
        onSound("boof", soundPos())
        onStateChanged?.invoke()
    }

}
