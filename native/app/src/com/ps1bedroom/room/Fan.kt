package com.ps1bedroom.room

import com.ps1bedroom.room.engine.Geo
import com.ps1bedroom.room.engine.GeoCurves
import com.ps1bedroom.room.engine.Group
import com.ps1bedroom.room.engine.Material
import com.ps1bedroom.room.engine.Mesh
import com.ps1bedroom.room.engine.PS1
import com.ps1bedroom.room.engine.createCanvasTex
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * The ceiling fan (HTML lines 8000-8139 and its animation from animate()): canopy, downrod and motor, four wooden
 * blades on brass irons that spin up and coast down, the light kit with the ribbed glass shade that glows when the
 * light is on, and the two beaded pull chains that jump when pulled.
 */
class Fan(private val room: Room) {
    lateinit var fanBladesGroup: Group
    lateinit var fanPullChain: Group
    lateinit var lightPullChain: Group
    private val shadeObj = createCanvasTex(64, 64) { _, _, _ -> }
    var fanSpinning = true; private set
    private var fanSpinAngle = 0f
    private var fanSpeedCurrent = 1.35f
    /** For the sound: the real (coasting) blade speed and angle. */
    val speed get() = fanSpeedCurrent.toDouble()
    val angle get() = fanSpinAngle.toDouble()
    private var fanChainOffset = 0f
    var lightChainOffset = 0f
    private val PULL_CHAIN_Y = -0.06f

    fun build() {
        PS1.syncGrime(253)
        val ceilingFanGroup = Group()
        ceilingFanGroup.position.set(0.6f, 4.65f, -0.4f)
        room.scene.add(ceilingFanGroup)
        val matFanBronze = PS1.material(color = 0x2a221e)
        val matFanBrass = PS1.material(color = 0xb48a3e)
        // Stationary ceiling canopy, downrod & motor housing
        val fanCanopy = Mesh(Geo.cylinder(0.11f, 0.14f, 0.06f, 8), matFanBronze)
        fanCanopy.position.y = 0.32f; ceilingFanGroup.add(fanCanopy)
        val fanDownrod = Mesh(Geo.cylinder(0.025f, 0.025f, 0.28f, 6), matFanBronze)
        fanDownrod.position.y = 0.18f; ceilingFanGroup.add(fanDownrod)
        val fanHub = Mesh(Geo.cylinder(0.22f, 0.22f, 0.11f, 10), matFanBronze)
        fanHub.castShadow = true; ceilingFanGroup.add(fanHub); room.registerInteractable(fanHub, "fanspin")

        // Rotating group only for the 4 wooden blades (with brass blade irons)
        fanBladesGroup = Group()
        ceilingFanGroup.add(fanBladesGroup)
        for (b in 0 until 4) {
            val angle = (b * PI / 2).toFloat()
            // Blade iron: a short arm out of the motor that bends up into the blade
            val iron = Group()
            iron.position.set(cos(angle) * 0.2f, 0f, sin(angle) * 0.2f)
            iron.rotation.y = -angle
            fanBladesGroup.add(iron)
            fun ironPart(w: Float, h: Float, d: Float, x: Float, y: Float, z: Float, mat: Material = matFanBrass) {
                val m = Mesh(Geo.box(w, h, d), mat); m.position.set(x, y, z); m.castShadow = true
                iron.add(m); room.registerInteractable(m, "fanspin")
            }
            ironPart(0.16f, 0.02f, 0.06f, 0.08f, -0.03f, 0f)   // arm out of the motor
            // (native fix: the HTML's bend had the arm's exact depth and stopped right at the blade's underside, so their faces
            //  shared planes and flickered; it is a little deeper now and its top is buried in the blade)
            ironPart(0.03f, 0.05f, 0.068f, 0.16f, -0.025f, 0f)  // bend up into the blade
            val blade = Mesh(Geo.box(1.12f, 0.024f, 0.23f, 3, 1, 1), room.matWood)
            blade.position.set(cos(angle) * 0.68f, 0.0f, sin(angle) * 0.68f)
            blade.rotation.order = "YXZ" // pitch around the blade's own length, so all four blades sit the same way
            blade.rotation.y = -angle
            blade.rotation.x = 0.08f
            blade.castShadow = true
            fanBladesGroup.add(blade); room.registerInteractable(blade, "fanspin")
        }

        // Light kit: switch cup, brass collar, lathed ribbed frosted glass shade, finial
        val fanLightKitGroup = Group()
        ceilingFanGroup.add(fanLightKitGroup)
        // (native fix: the HTML's 10-sided collar never lined up with the 12-sided glass, and the glass rim stopped short of it,
        //  leaving dark notches at the seam; collar and cup are 12-sided now and the rim tucks up inside the collar)
        val fanSwitchCup = Mesh(Geo.cylinder(0.085f, 0.085f, 0.075f, 12), matFanBronze)
        fanSwitchCup.position.y = -0.085f; fanLightKitGroup.add(fanSwitchCup); room.registerInteractable(fanSwitchCup, "fan")
        val fanFitterCollar = Mesh(Geo.cylinder(0.095f, 0.125f, 0.042f, 12), matFanBrass)
        fanFitterCollar.position.y = -0.135f; fanLightKitGroup.add(fanFitterCollar); room.registerInteractable(fanFitterCollar, "fan")
        drawFanShadeTexture(false)
        val shadeProfile = listOf(floatArrayOf(0.001f, -0.295f), floatArrayOf(0.065f, -0.290f), floatArrayOf(0.128f, -0.255f),
            floatArrayOf(0.148f, -0.205f), floatArrayOf(0.122f, -0.150f), floatArrayOf(0.11f, -0.138f))
        val fanBulbGlobe = Mesh(GeoCurves.lathe(shadeProfile, 12), PS1.basic(map = shadeObj.tex))
        fanLightKitGroup.add(fanBulbGlobe); room.registerInteractable(fanBulbGlobe, "fan")
        val shadeFinial = Mesh(Geo.cylinder(0.024f, 0.012f, 0.025f, 8), matFanBrass)
        shadeFinial.position.y = -0.305f; fanLightKitGroup.add(shadeFinial); room.registerInteractable(shadeFinial, "fan")

        // Two beaded pull chains (fan motor and light); radius 0.19 keeps them clear of the shade and collar
        val matChainSilver = PS1.material(color = 0xe2e8f0)
        fun createPullChain(angle: Float, fobMat: Material, tag: String): Group {
            val group = Group()
            group.position.set(cos(angle) * 0.19f, PULL_CHAIN_Y, sin(angle) * 0.19f)
            ceilingFanGroup.add(group)
            // All 12 beads in one mesh (one draw call, one tap target)
            val beads = Mesh(GeoCurves.merge((0 until 12).map { c -> Geo.sphere(0.007f, 5, 5).translate(0f, -c * 0.022f, 0f) }), matChainSilver)
            group.add(beads); room.registerInteractable(beads, tag)
            val fob = Mesh(Geo.cylinder(0.012f, 0.020f, 0.042f, 7), fobMat)
            fob.position.y = -0.285f
            group.add(fob); room.registerInteractable(fob, tag)
            return group
        }
        fanPullChain = createPullChain((PI * 0.25).toFloat(), room.matWood, "fanspin")
        lightPullChain = createPullChain((PI * 1.25).toFloat(), matFanBrass, "fan")
    }

    /** Ribbed frosted glass: cool grey when off, warm filament glow when the light is on. */
    fun drawFanShadeTexture(isOn: Boolean) {
        val ctx = shadeObj.ctx; val w = 64; val h = 64
        val grad = ctx.createLinearGradient(0, 0, 0, h)
        if (isOn) { grad.addColorStop(0.0, "#d97706"); grad.addColorStop(0.35, "#fde047"); grad.addColorStop(0.75, "#fef9c3"); grad.addColorStop(1.0, "#fde68a") }
        else { grad.addColorStop(0.0, "#475569"); grad.addColorStop(0.5, "#94a3b8"); grad.addColorStop(1.0, "#cbd5e1") }
        ctx.fillStyle = grad; ctx.fillRect(0, 0, w, h)
        // Subtle vertical glass ribs
        var x = 0
        while (x < w) { ctx.fillStyle = if (isOn) "rgba(180, 83, 9, 0.14)" else "rgba(15, 23, 42, 0.16)"; ctx.fillRect(x, 0, 2, h); x += 4 }
        shadeObj.tex.needsUpdate()
    }

    fun toggleCeilingFanSpin() { fanSpinning = !fanSpinning; fanChainOffset = -0.045f }

    /** Spin up / coast the blades; pulled chains spring back. */
    fun update(dt: Float) {
        val targetFanSpeed = if (fanSpinning) 1.85f else 0.0f
        fanSpeedCurrent += (targetFanSpeed - fanSpeedCurrent) * dt * 1.8f
        fanSpinAngle += fanSpeedCurrent * dt
        fanBladesGroup.rotation.y = fanSpinAngle
        if (abs(lightChainOffset) > 0.0005f) {
            lightChainOffset *= 0.02f.pow(dt)
            lightPullChain.position.y = PULL_CHAIN_Y + lightChainOffset
        }
        if (abs(fanChainOffset) > 0.0005f) {
            fanChainOffset *= 0.02f.pow(dt)
            fanPullChain.position.y = PULL_CHAIN_Y + fanChainOffset
        }
    }
}

