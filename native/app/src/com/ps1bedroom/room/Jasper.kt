package com.ps1bedroom.room

import com.ps1bedroom.room.engine.Canvas2D
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
    private val DOG_HUB = Vec3(0.45f, 0f, 0.85f) // open floor in the middle of the room
    private val pos = Vec3(0.6f, 0f, 1.6f)
    private var heading = PI.toFloat()
    private var state = "idle"
    var pose = "lie"; private set
    private var poseK = 1f; private var sitK = 0f
    private val path = ArrayList<Vec3>()
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
        // Neck leans forward from the shoulders into the head, both ends buried (inside the chest and the skull)
        dogPart(dogBody, 0.11f, 0.28f, 0.11f, dogTan, 0f, 0.129f, 0.384f, 0.72f)    // neck (native fix v0.29: thinner, and its top-back corner ends inside the head's neck stub even with the head turned)
        dogPart(dogBody, 0.17f, 0.04f, 0.17f, PS1.material(color = 0xb91c1c), 0f, 0.17f, 0.42f, 0.72f) // collar (follows the neck)
        val tagTex = createCanvasTex(8, 8) { ctx, _, _ -> ctx.fillStyle = "#d4a017"; ctx.fillRect(1, 1, 6, 6); ctx.fillStyle = "#8a6a10"; ctx.fillRect(3, 3, 2, 2) }.tex
        dogPart(dogBody, 0.05f, 0.05f, 0.012f, PS1.material(map = tagTex, color = 0xffffff), 0f, 0.09f, 0.49f) // tag hanging from the collar
        // Head: block skull, cheeks, snout with a black mask and nose, upright ears
        dogHead.position.set(0f, 0.33f, 0.52f)
        dogBody.add(dogHead)
        dogPart(dogHead, 0.22f, 0.2f, 0.22f, dogTan, 0f, 0f, 0f)                    // skull
        dogPart(dogHead, 0.16f, 0.16f, 0.18f, dogTan, 0f, -0.1f, -0.03f)            // neck stub (deeper at the back: v0.29): turns with the head, so the joint never opens or pokes through
        dogPart(dogHead, 0.2f, 0.06f, 0.2f, dogDark, 0f, 0.09f, -0.01f)            // dark crown
        dogPart(dogHead, 0.13f, 0.1f, 0.16f, dogDark, 0f, -0.05f, 0.18f)           // snout (black mask)
        dogPart(dogHead, 0.11f, 0.035f, 0.13f, dogCream, 0f, -0.105f, 0.15f)       // lower jaw
        dogPart(dogHead, 0.06f, 0.04f, 0.03f, dogNose, 0f, -0.01f, 0.265f)         // nose
        for (s in intArrayOf(-1, 1)) {
            dogPart(dogHead, 0.08f, 0.09f, 0.035f, dogDark, s * 0.07f, 0.15f, -0.04f, 0f, 0f, s * -0.12f)   // ear base
            dogPart(dogHead, 0.045f, 0.07f, 0.03f, dogDark, s * 0.08f, 0.225f, -0.04f, 0f, 0f, s * -0.12f)  // ear tip
            dogPart(dogHead, 0.04f, 0.07f, 0.006f, dogPink, s * 0.07f, 0.165f, -0.02f, 0f, 0f, s * -0.12f) // inner ear
            dogPart(dogHead, 0.06f, 0.06f, 0.04f, dogCream, s * 0.085f, -0.06f, 0.08f)                      // cheeks
        }
        // Eyes: pixel-art, ice blue; his LEFT eye (the +X side) is brown on the bottom half
        for ((s, split) in listOf(-1 to false, 1 to true)) {
            val obj = createCanvasTex(8, 8) { ctx, _, _ -> drawDogEye(ctx, split) }
            val eye = Mesh(Geo.plane(0.05f, 0.05f), PS1.material(map = obj.tex, color = 0xffffff, emissive = 0xffffff, emissiveMap = obj.tex, emissiveIntensity = 0.12f,
                polygonOffset = true, polygonOffsetFactor = -3f, polygonOffsetUnits = -3f))
            eye.position.set(s * 0.06f, 0.03f, 0.111f)
            dogHead.add(eye)
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
        sendDog(pickDogSpot())
    }

    private fun drawDogEye(ctx: Canvas2D, split: Boolean) {
        ctx.clearRect(0, 0, 8, 8)
        ctx.fillStyle = "#120c08"; ctx.fillRect(0, 0, 8, 8)            // dark lid rim
        ctx.fillStyle = "#2e7fd0"; ctx.fillRect(1, 1, 6, 6)            // iris
        ctx.fillStyle = "#7cc4f5"; ctx.fillRect(1, 1, 6, 2)            // lighter top
        if (split) { ctx.fillStyle = "#5a2f12"; ctx.fillRect(1, 4, 6, 3); ctx.fillStyle = "#7a4318"; ctx.fillRect(1, 4, 6, 1) } // brown below
        ctx.fillStyle = "#050505"; ctx.fillRect(3, 3, 2, 2)            // pupil
        ctx.fillStyle = "#ffffff"; ctx.fillRect(4, 2, 1, 1)            // catch-light
    }

    /** jasperColors: the chosen preset (or "custom") and its three colours. */
    var colors = PRESETS.getValue("classic"); private set
    var colorPreset = "classic"; private set
    fun applyColors(p: Preset, key: String = PRESETS.entries.firstOrNull { it.value === p }?.key ?: "custom") {
        colors = p; colorPreset = key
        dogTan.color.setStyle(p.coat); dogDark.color.setStyle(p.dark); dogCream.color.setStyle(p.light)
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
            Spot("bed", Vec3(2.95f, 0.83f, -0.55f), "sleep", if (night) 6f else 1f, bed = true),
            Spot("tv", Vec3(-1.25f, 0f, 1.55f), "lie", if (bedroomTvOn) 2.5f else 0.5f),
            Spot("desk", Vec3(-0.35f, 0f, 0.15f), "sit", if (sittingAtDesk) 5f else 0.6f)
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
        if (onBed) { jump = Jump(pos.clone(), Vec3(1.25f, 0f, -0.55f), 0f, 0.9f, 0f); onBed = false }
        path += DOG_HUB.clone()
        if (s.bed) path += Vec3(1.25f, 0f, -0.55f)
        if (!s.bed) path += Vec3(s.pos.x, 0f, s.pos.z) // native fix: for the bed he stops at its edge and jumps up from there
        state = "walk"
    }

    /** Furniture he walks around (TV cart, chair, heater) with clearance radii; set by the app. */
    var obstacles: List<Pair<Object3D, Float>> = emptyList()
    // native fix: he used to walk straight through the bed, the desk, the chair and everything else
    private fun keepClear() {
        if (pos.x > 1.35f && pos.z < 1.65f) pos.x = 1.35f            // bed
        if (pos.x < -0.1f && pos.z < -0.5f) pos.z = -0.5f            // desk
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
            val target = path[0]
            to.set(target.x - pos.x, 0f, target.z - pos.z)
            val dist = to.length()
            val want = atan2(to.x, to.z)
            heading += shortAngle(heading, want) * min(1f, dt * 5)
            pose = "walk"
            if (dist < 0.08f) {
                path.removeAt(0)
                if (path.isEmpty()) {
                    val s = spot!!
                    if (s.bed) { jump = Jump(pos.clone(), Vec3(s.pos.x, 0f, s.pos.z), 0f, 0f, 0.83f); onBed = true }
                    state = "rest"
                    pose = s.pose
                    until = t + 40 + (rnd.nextDouble() * 80).toFloat()
                }
            } else {
                pos.addScaledVector(to.normalize(), min(dist, 0.85f * dt))
                keepClear()
            }
        } else if (state == "rest" || state == "idle") {
            if (spot?.name == "desk" && !sittingAtDesk) until = min(until, t + 5)
            if (t > until) sendDog(pickDogSpot())
        }
        if (jump == null) y += ((if (onBed) 0.83f else 0f) - y) * min(1f, dt * 6)
        // Blend between poses
        val lying = if (pose == "lie" || pose == "sleep") 1f else 0f
        val sitting = if (pose == "sit") 1f else 0f
        poseK += (lying - poseK) * min(1f, dt * 3)
        sitK += (sitting - sitK) * min(1f, dt * 3)
        dogGroup.position.set(pos.x, y, pos.z)
        dogGroup.rotation.y = heading
        val breathe = sin(t * (if (pose == "sleep") 1.4f else 3f)) * (if (pose == "sleep") 0.012f else 0.005f)
        dogBody.position.y = 0.6f - poseK * 0.38f - sitK * SIT_DROP + breathe
        dogBody.rotation.x = -sitK * SIT_TILT
        val walkSwing = if (pose == "walk") sin(t * 9) * 0.55f else 0f
        // Joint angles per pose [shoulder/hip, knee/hock], blended by how far into the pose he is
        val lieK = poseK; val standK = max(0f, 1 - lieK - sitK)
        for (i in dogLegs.indices) {
            val front = i < 2
            val phase = if (i == 0 || i == 3) 1f else -1f
            val walk = walkSwing * phase
            val lie = if (front) FRONT_LIE else BACK_LIE; val sit = if (front) FRONT_SIT else BACK_SIT
            dogLegs[i].rotation.x = walk * standK + lie[0] * lieK + sit[0] * sitK
            dogKnees[i].rotation.x = (if (front) -0.25f else 0.35f) * max(0f, walk * phase * (if (front) -1f else 1f)) * standK + lie[1] * lieK + sit[1] * sitK
        }
        // Head: looks around, rests down when asleep, tilts when you pet him
        val petting = t < wagUntil
        dogHead.rotation.x = if (pose == "sleep") HEAD_SLEEP else sin(t * 0.4f) * 0.08f
        dogHead.rotation.y = if (pose == "sleep") 0.3f else sin(t * 0.27f) * 0.35f
        dogHead.rotation.z = if (petting) sin(t * 3) * 0.25f else 0f
        // Tail: hangs in a soft curve, wags side to side, rests on the floor when he's down
        dogTail.rotation.x = TAIL_STAND * standK + TAIL_LIE * lieK + TAIL_SIT * sitK
        tailSegs[1].rotation.x = -0.25f * standK + TAIL_LIE2 * lieK + TAIL_SIT2 * sitK
        tailSegs[2].rotation.x = -0.3f * standK
        dogTail.rotation.y = sin(t * (if (petting) 16f else (if (pose == "walk") 7f else 2f))) * (if (petting) 0.6f else (if (pose == "sleep") 0.04f else 0.15f))
        // Soft snores while asleep at night
        if (pose == "sleep" && state == "rest" && t > nextSnore) { nextSnore = t + 4.2f + rnd.nextFloat() * 1.5f; onSound("snore", soundPos()) }
    }
    private var nextSnore = 0f
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

    /** Tap: tail thumps and a head tilt; a sleeping Jasper opens one eye and lies awake. */
    /** 1.3 hooks: petting (achievements), sleep mode sends him to bed, the alarm makes him wag. */
    var onPet: () -> Unit = {}
    fun goToBed() { dogSpots().firstOrNull { it.name == "bed" }?.let { sendDog(it) } }
    fun wag(t: Float, secs: Float) { wagUntil = t + secs }
    fun petDog(t: Float) {
        onPet()
        wagUntil = t + 2.5f
        if (pose == "sleep") { pose = "lie"; toast("🐕 Jasper opens one eye (the half-brown one), yawns, and thumps his tail twice.") }
        else toast("🐕 JASPER: ${DOG_PETS[rnd.nextInt(DOG_PETS.size)]}")
        onSound("boof", soundPos())
    }

}
