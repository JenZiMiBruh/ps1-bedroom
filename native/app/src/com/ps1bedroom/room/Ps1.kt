package com.ps1bedroom.room

import com.ps1bedroom.room.engine.Canvas2D
import com.ps1bedroom.room.engine.CatmullRomCurve3
import com.ps1bedroom.room.engine.Geo
import com.ps1bedroom.room.engine.GeoCurves
import com.ps1bedroom.room.engine.Geometry
import com.ps1bedroom.room.engine.Group
import com.ps1bedroom.room.engine.Material
import com.ps1bedroom.room.engine.Mesh
import com.ps1bedroom.room.engine.Object3D
import com.ps1bedroom.room.engine.PS1
import com.ps1bedroom.room.engine.Vec3
import com.ps1bedroom.room.engine.Weather
import com.ps1bedroom.room.engine.createCanvasTex
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * PLAYSTATION (SCPH-1001) and its HOOKUPS (HTML lines 4489-4502, 4756-4835, 5361-5770): two-tone shell with vents,
 * disc tray and spinning Bandi-coot Rush disc, hinged lid, POWER / RESET / OPEN buttons that press down, LED,
 * memory cards and ports, the DualShock and its cable, the game case lying beside it, then the A/V cable up to the
 * TV's jacks and both power cords to a floor power strip at the wall outlet. Also the console's TV picture:
 * boot screens, the system menu (lid open), the title screen and the Bandi-coot Rush game.
 */
class Ps1(private val room: Room, private val tv: Tv) {
    val ps1Group = Group()
    lateinit var ps1LidPivot: Group
    private lateinit var ps1Disc: Mesh
    val disc get() = ps1Disc
    lateinit var discHome: com.ps1bedroom.room.engine.Vec3
    lateinit var library: GameLibrary
    private lateinit var ps1LedMat: Material
    private class Button(val mesh: Mesh, val baseY: Float) { var press = 0f }
    private val buttons = HashMap<String, Button>()
    var ps1On = false; private set
    var lidOpen = false; private set
    private var bootStart = 0f
    private var discSpeed = 0f
    private var padPressAt = -10f
    private var tvChannelBeforePs1 = 0
    val PS1_BOOT_SECONDS = 7.5f

    private fun ps1Part(geo: Geometry, mat: Material, x: Float, y: Float, z: Float, tag: String? = null): Mesh {
        val m = Mesh(geo, mat); m.position.set(x, y, z); m.castShadow = true; m.receiveShadow = true
        ps1Group.add(m); if (tag != null) room.registerInteractable(m, tag); return m
    }

    fun build(matJewelPlastic: Material) {
        val scene = room.scene
        val matDarkPlastic = room.matDarkPlastic

        PS1.syncGrime(210)
        ps1Group.position.set(-1.45f, 0.032f, 0.62f) // on the rug in front of the TV cart
        ps1Group.rotation.y = 0.55f
        scene.add(ps1Group)
        // Real PS1 grey is a mid, slightly warm grey; lighter values read as white under the room lights
        val matPs1Grey = PS1.material(color = 0x80858f)
        val matPs1DarkGrey = PS1.material(color = 0x4f5560)
        val matPs1Recess = PS1.material(color = 0x1a1d24)
        // Two-tone shell: dark base, light-grey top (top surface at Y = 0.10, front face Z = +0.188)
        ps1Part(Geo.box(0.52f, 0.03f, 0.36f, 4, 1, 4), matPs1DarkGrey, 0f, 0.015f, 0f, "ps1Body")
        ps1Part(Geo.box(0.54f, 0.07f, 0.376f, 4, 1, 4), matPs1Grey, 0f, 0.065f, 0f, "ps1Body")
        ps1Part(Geo.box(0.544f, 0.005f, 0.38f), matPs1Recess, 0f, 0.031f, 0f) // seam between the halves
        for (side in intArrayOf(-1, 1)) for (v in 0 until 5) ps1Part(Geo.box(0.003f, 0.005f, 0.12f), matPs1Recess, side * 0.2705f, 0.05f + v * 0.008f, -0.06f) // side vents
        // Disc tray (seen when the lid is open), the game disc, spindle
        val LID_R = 0.135f; val LID_Z = -0.035f
        ps1Part(Geo.cylinder(LID_R + 0.006f, LID_R + 0.006f, 0.004f, 28), matPs1Recess, 0f, 0.102f, LID_Z)
        val discTex = createCanvasTex(128, 128, weather = Weather(seed = 54.0, blotch = 0.06, scratches = 14)) { ctx, w, h ->
            val g = ctx.createRadialGradient(64, 64, 10, 64, 64, 64)
            g.addColorStop(0, "#e2e8f0"); g.addColorStop(0.45, "#fb923c"); g.addColorStop(1, "#7c2d12")
            ctx.fillStyle = g; ctx.fillRect(0, 0, w, h)
            ctx.fillStyle = "#166534"; ctx.fillRect(0, 76, w, 20)
            ctx.fillStyle = "#fef3c7"; ctx.font = "bold 13px Impact, sans-serif"; ctx.textAlign = "center"
            ctx.fillText("BANDI-COOT", 64, 38); ctx.fillText("RUSH", 64, 53)
            ctx.fillStyle = "#111827"; ctx.font = "bold 7px sans-serif"; ctx.fillText("COMPACT DISC", 64, 110)
            ctx.fillStyle = "#1f2937"; ctx.beginPath(); ctx.arc(64, 64, 9, 0, PI * 2); ctx.fill()
        }.tex
        ps1Disc = Mesh(Geo.cylinder(0.118f, 0.118f, 0.002f, 32), listOf(PS1.material(color = 0xcbd5e1), PS1.material(map = discTex), PS1.material(color = 0x050507)))
        ps1Disc.position.set(0f, 0.1055f, LID_Z); ps1Disc.castShadow = true; ps1Disc.receiveShadow = true
        discHome = ps1Disc.position.clone()
        ps1Group.add(ps1Disc); room.registerInteractable(ps1Disc, "ps1Open")
        ps1Part(Geo.cylinder(0.014f, 0.014f, 0.004f, 10), matPs1DarkGrey, 0f, 0.106f, LID_Z)
        // Hinged lid: pivots along its rear edge and springs open
        ps1LidPivot = Group()
        ps1LidPivot.position.set(0f, 0.109f, LID_Z - LID_R)
        ps1Group.add(ps1LidPivot)
        val ps1Lid = Mesh(Geo.cylinder(LID_R, LID_R, 0.018f, 28), matPs1Grey)
        ps1Lid.position.set(0f, 0.009f, LID_R); ps1Lid.castShadow = true; ps1Lid.receiveShadow = true
        ps1LidPivot.add(ps1Lid); room.registerInteractable(ps1Lid, "ps1Open")
        val ps1LidRing = Mesh(Geo.cylinder(0.085f, 0.085f, 0.002f, 24), PS1.material(color = 0x6f747e))
        ps1LidRing.position.set(0f, 0.0185f, LID_R); ps1LidPivot.add(ps1LidRing)
        val ps1Hinge = Mesh(Geo.box(0.08f, 0.012f, 0.012f), matPs1Recess)
        ps1Hinge.position.set(0f, 0.006f, 0f); ps1LidPivot.add(ps1Hinge)
        // Top buttons on dark surround rings: POWER, RESET, OPEN (they press down when tapped)
        for ((name, x, z, r, tag) in listOf(B("power", -0.19f, 0.125f, 0.03f, "ps1Power"), B("reset", -0.115f, 0.14f, 0.017f, "ps1Reset"), B("open", 0.19f, 0.125f, 0.03f, "ps1Open"))) {
            ps1Part(Geo.cylinder(r + 0.006f, r + 0.006f, 0.003f, 16), matPs1Recess, x, 0.1012f, z)
            val b = ps1Part(Geo.cylinder(r, r, 0.012f, 16), matPs1Grey, x, 0.106f, z, tag)
            buttons[name] = Button(b, 0.106f)
        }
        ps1LedMat = PS1.material(color = 0x14532d, emissive = 0x22c55e, emissiveIntensity = 0.0f)
        ps1Part(Geo.box(0.012f, 0.004f, 0.012f), ps1LedMat, -0.15f, 0.1015f, 0.168f)
        val psLogoTex = createCanvasTex(16, 16) { ctx, _, _ ->
            for ((c, x, y) in arrayOf(Triple("#ef4444", 0, 0), Triple("#facc15", 8, 0), Triple("#22c55e", 0, 8), Triple("#3b82f6", 8, 8))) { ctx.fillStyle = c; ctx.fillRect(x, y, 8, 8) }
        }.tex
        ps1Part(Geo.box(0.03f, 0.002f, 0.03f), PS1.material(map = psLogoTex), 0.07f, 0.101f, 0.152f)
        // Front: memory card slots (upper) and controller ports (lower); cards in both slots, pad plugged into port 1
        for (px in floatArrayOf(-0.12f, 0.12f)) {
            ps1Part(Geo.box(0.062f, 0.012f, 0.006f), matPs1Recess, px, 0.078f, 0.1885f)
            ps1Part(Geo.box(0.078f, 0.026f, 0.006f), matPs1Recess, px, 0.047f, 0.1885f)
        }
        ps1Part(Geo.box(0.054f, 0.009f, 0.03f), matPs1Grey, -0.12f, 0.078f, 0.2f)
        ps1Part(Geo.box(0.054f, 0.009f, 0.03f), PS1.material(color = 0xdc2626, emissive = 0x7f1d1d, emissiveIntensity = 0.25f), 0.12f, 0.078f, 0.2f)
        ps1Part(Geo.box(0.07f, 0.022f, 0.035f), matPs1DarkGrey, -0.12f, 0.047f, 0.205f)
        // Rear: AV MULTI OUT, serial I/O, parallel port and AC IN, with the A/V and power plugs inserted
        ps1Part(Geo.box(0.07f, 0.022f, 0.006f), matPs1Recess, -0.13f, 0.055f, -0.1885f)
        ps1Part(Geo.box(0.05f, 0.014f, 0.006f), matPs1Recess, 0.03f, 0.06f, -0.1885f)
        ps1Part(Geo.box(0.11f, 0.012f, 0.006f), matPs1Recess, 0.02f, 0.038f, -0.1885f)
        ps1Part(Geo.box(0.04f, 0.024f, 0.006f), matPs1Recess, 0.19f, 0.055f, -0.1885f)
        ps1Part(Geo.box(0.064f, 0.02f, 0.03f), matDarkPlastic, -0.13f, 0.055f, -0.2f)
        ps1Part(Geo.box(0.036f, 0.022f, 0.03f), matDarkPlastic, 0.19f, 0.055f, -0.2f)

        // ---- DualShock: handles, D-pad, action buttons, twin analog sticks ----
        val padGroup = Group()
        padGroup.position.set(0.42f, 0.026f, 0.24f); padGroup.rotation.y = -0.38f
        ps1Group.add(padGroup)
        val padBridge = Mesh(Geo.box(0.24f, 0.038f, 0.115f), matPs1Grey)
        padBridge.castShadow = true; padGroup.add(padBridge); room.registerInteractable(padBridge, "ps1Pad")
        for (cx in floatArrayOf(-0.085f, 0.085f)) {
            val padCircle = Mesh(Geo.cylinder(0.062f, 0.058f, 0.04f, 10), matPs1Grey)
            padCircle.position.set(cx, 0.002f, 0f); padCircle.castShadow = true; padGroup.add(padCircle)
            val grip = Mesh(Geo.cylinder(0.034f, 0.022f, 0.135f, 8), matPs1Grey)
            grip.rotation.x = (PI / 2).toFloat(); grip.rotation.z = if (cx < 0) 0.24f else -0.24f
            grip.position.set(cx * 1.15f, -0.004f, 0.065f); grip.castShadow = true; padGroup.add(grip)
            val stick = Mesh(Geo.cylinder(0.024f, 0.016f, 0.026f, 8), matDarkPlastic)
            stick.position.set(cx * 0.52f, 0.026f, 0.038f); padGroup.add(stick)
        }
        val dpadH = Mesh(Geo.box(0.048f, 0.012f, 0.016f), matDarkPlastic); dpadH.position.set(-0.085f, 0.022f, -0.005f); padGroup.add(dpadH)
        val dpadV = Mesh(Geo.box(0.016f, 0.012f, 0.048f), matDarkPlastic); dpadV.position.set(-0.085f, 0.022f, -0.005f); padGroup.add(dpadV)
        // Triangle green, circle red, cross blue, square pink
        for ((bx, bz, bcol) in arrayOf(Triple(0.085f, -0.024f, 0x22c55e), Triple(0.106f, -0.004f, 0xef4444), Triple(0.085f, 0.016f, 0x3b82f6), Triple(0.064f, -0.004f, 0xec4899))) {
            val btn = Mesh(Geo.cylinder(0.008f, 0.008f, 0.012f, 6), PS1.material(color = bcol))
            btn.position.set(bx, 0.022f, bz); padGroup.add(btn)
        }
        // The case for the disc that's in the console, lying face-up beside it
        val ps1Case = Games.buildPs1JewelCase(Games.BANDICOOT_RUSH, matJewelPlastic)
        ps1Case.rotation.set((-PI / 2).toFloat(), 0f, 0.28f)
        ps1Case.position.set(-0.44f, 0.011f, 0.14f)
        ps1Group.add(ps1Case)
        library.registerFloorCase(ps1Case)
        val padCable = Mesh(GeoCurves.tube(CatmullRomCurve3(listOf(doubleArrayOf(-0.12, 0.047, 0.222), doubleArrayOf(0.04, 0.012, 0.35),
            doubleArrayOf(0.26, 0.012, 0.31), doubleArrayOf(0.39, 0.025, 0.20))), 12, 0.009f, 5), matDarkPlastic)
        padCable.castShadow = true; ps1Group.add(padCable)
        // Every console / controller part shades the same way
        ps1Group.traverse { if (it is Mesh) it.receiveShadow = true }

        buildHookups()
    }
    private data class B(val name: String, val x: Float, val z: Float, val r: Float, val tag: String)

    // ---- HOOKUPS: A/V cable into the TV's front jacks; PS1 and TV power into a floor power strip at the wall ----
    private fun buildHookups() {
        val scene = room.scene
        val matDarkPlastic = room.matDarkPlastic
        scene.updateMatrixWorld()
        fun local(g: Object3D, x: Double, y: Double, z: Double): DoubleArray { val v = Vec3(x.toFloat(), y.toFloat(), z.toFloat()).applyMatrix4(g.matrixWorld); return doubleArrayOf(v.x.toDouble(), v.y.toDouble(), v.z.toDouble()) }
        fun ps1Local(x: Double, y: Double, z: Double) = local(ps1Group, x, y, z)
        fun tvLocal(x: Double, y: Double, z: Double) = local(tv.tvStandGroup, x, y, z)
        // Cables lie on the rug (top at Y = 0.02) or on the bare floor
        fun restOnFloor(p: DoubleArray, r: Double = 0.011): DoubleArray {
            val onRugArea = p[0] > -2.3 && p[0] < 1.9 && p[2] > -1.1 && p[2] < 2.1
            p[1] = (if (onRugArea) 0.03 else 0.0) + r; return p
        }
        fun floorAt(x: Double, z: Double, r: Double) = restOnFloor(doubleArrayOf(x, 0.0, z), r)
        PS1.syncGrime(222)
        val matCable = PS1.material(color = 0x15171c)
        fun addCable(points: List<DoubleArray>, radius: Float = 0.011f, mat: Material = matCable): Mesh {
            val mesh = Mesh(GeoCurves.tube(CatmullRomCurve3(points), max(24, points.size * 12), radius, 6), mat)
            mesh.castShadow = true; scene.add(mesh); return mesh
        }
        // Wall outlet on the left wall + power strip on the floor (its rocker switch glows)
        val wallOutlet = Mesh(Geo.box(0.03f, 0.2f, 0.13f), room.matBeigePlastic); wallOutlet.position.set(-3.715f, 0.32f, 0.25f); scene.add(wallOutlet)
        for (sy in floatArrayOf(0.36f, 0.28f)) { val socket = Mesh(Geo.box(0.006f, 0.045f, 0.05f), matDarkPlastic); socket.position.set(-3.699f, sy, 0.25f); scene.add(socket) }
        val powerStrip = Mesh(Geo.box(0.1f, 0.05f, 0.46f, 1, 1, 4), room.matBeigePlastic)
        powerStrip.position.set(-3.45f, 0.025f, 0.42f); powerStrip.castShadow = true; powerStrip.receiveShadow = true; scene.add(powerStrip)
        for (i in 0 until 4) { val outlet = Mesh(Geo.box(0.05f, 0.004f, 0.05f), matDarkPlastic); outlet.position.set(-3.45f, 0.051f, 0.28f + i * 0.09f); scene.add(outlet) }
        val stripSwitch = Mesh(Geo.box(0.04f, 0.012f, 0.03f), PS1.material(color = 0xef4444, emissive = 0xdc2626, emissiveIntensity = 0.9f))
        stripSwitch.position.set(-3.45f, 0.055f, 0.205f); scene.add(stripSwitch)
        // Strip cord up into the wall outlet
        addCable(listOf(doubleArrayOf(-3.45, 0.025, 0.19), floorAt(-3.5, 0.12, 0.01), floorAt(-3.66, 0.14, 0.01), doubleArrayOf(-3.7, 0.12, 0.2), doubleArrayOf(-3.69, 0.28, 0.25)), 0.01f)
        val wallPlug = Mesh(Geo.box(0.03f, 0.045f, 0.04f), matDarkPlastic); wallPlug.position.set(-3.687f, 0.28f, 0.25f); scene.add(wallPlug)
        fun stripPlug(z: Double): DoubleArray {
            val plug = Mesh(Geo.box(0.04f, 0.03f, 0.04f), matDarkPlastic); plug.position.set(-3.45f, 0.066f, z.toFloat()); scene.add(plug)
            return doubleArrayOf(-3.45, 0.08, z)
        }
        // TV power cord: out of the back of the set, down behind the cart, along the floor to the strip
        addCable(listOf(tvLocal(0.12, 1.2, -0.39), tvLocal(0.12, 1.0, -0.44), tvLocal(0.12, 0.35, -0.42),
            restOnFloor(tvLocal(0.12, 0.0, -0.5), 0.01), floorAt(-3.15, 0.5, 0.01), floorAt(-3.36, 0.55, 0.01), stripPlug(0.55)), 0.01f)
        // PS1 power cord: from AC IN at the back, around the cart's end, to the strip
        addCable(listOf(ps1Local(0.19, 0.055, -0.215), restOnFloor(ps1Local(0.19, 0.0, -0.32), 0.009),
            floorAt(-1.95, 0.08, 0.009), floorAt(-2.7, 0.02, 0.009), floorAt(-3.2, 0.2, 0.009), floorAt(-3.36, 0.37, 0.009), stripPlug(0.37)), 0.009f)
        // A/V cable: AV MULTI OUT across the rug, up the front of the cart, splitting into yellow / white / red plugs
        val avSplit = tvLocal(0.135, 0.885, 0.385)
        addCable(listOf(ps1Local(-0.13, 0.055, -0.215), restOnFloor(ps1Local(-0.13, 0.0, -0.33), 0.009),
            floorAt(-1.86, 0.5, 0.009), restOnFloor(tvLocal(0.135, 0.0, 0.43), 0.009),
            tvLocal(0.135, 0.3, 0.41), tvLocal(0.135, 0.7, 0.395), avSplit), 0.009f)
        for ((jx, col) in listOf(0.10 to 0xfacc15, 0.135 to 0xf8fafc, 0.17 to 0xdc2626)) {
            val matLead = PS1.material(color = col)
            val lead = Mesh(GeoCurves.tube(CatmullRomCurve3(listOf(doubleArrayOf(0.135, 0.885, 0.385), doubleArrayOf((0.135 + jx) / 2, 0.9, 0.39), doubleArrayOf(jx, 0.925, 0.37))), 8, 0.005f, 5), matLead)
            tv.tvStandGroup.add(lead)
            val rcaPlug = Mesh(Geo.cylinder(0.008f, 0.008f, 0.024f, 8), matLead)
            rcaPlug.rotation.x = (PI / 2).toFloat(); rcaPlug.position.set(jx.toFloat(), 0.925f, 0.362f)
            tv.tvStandGroup.add(rcaPlug)
        }
    }

    // ---- State: power, lid, boot timeline and animation ----
    fun phase(t: Float): String {
        if (!ps1On) return "off"
        if (lidOpen) return "menu"
        val e = t - bootStart
        return if (e < PS1_BOOT_SECONDS) "boot" else (if (e < PS1_BOOT_SECONDS + 4) "title" else "game")
    }
    fun update(dt: Float) {
        val lidTarget = if (lidOpen) -1.25f else 0f
        val dl = lidTarget - ps1LidPivot.rotation.x
        if (abs(dl) > 0.001f) { ps1LidPivot.rotation.x += dl * min(1f, dt * 9); room.onShadowsChanged() }
        val spinTarget = if (ps1On && !lidOpen) 28f else 0f
        discSpeed += (spinTarget - discSpeed) * min(1f, dt * (if (spinTarget > discSpeed) 1.2f else 0.7f))
        ps1Disc.rotation.y += discSpeed * dt
        for (b in buttons.values) {
            if (b.press > 0) b.press = max(0f, b.press - dt)
            b.mesh.position.y = b.baseY - (if (b.press > 0) 0.006f else 0f)
        }
    }
    private fun press(name: String) { buttons.getValue(name).press = 0.14f }
    /** Puts the TV on the PlayStation's A/V input (turning the TV on if needed). */
    private fun switchTvToPs1(t: Float) {
        if (tv.bedroomTvChannel != Tv.PS1_TV_CHANNEL) tvChannelBeforePs1 = tv.bedroomTvChannel
        tv.setChannel(Tv.PS1_TV_CHANNEL)
        if (!tv.bedroomTvOn) tv.toggleBedroomTv(t)
        tv.updateBedroomTvScreen(t)
    }
    private fun startBoot(t: Float) { bootStart = t } // (the spin-up and boot jingle come with the audio phase)
    fun powerToggle(t: Float) {
        press("power")
        ps1On = !ps1On
        tv.ps1On = ps1On
        ps1LedMat.emissiveIntensity = if (ps1On) 1.0f else 0.0f
        if (ps1On) { switchTvToPs1(t); startBoot(t) }
        else if (tv.bedroomTvChannel == Tv.PS1_TV_CHANNEL) { tv.setChannel(tvChannelBeforePs1); tv.updateBedroomTvScreen(t) }
    }
    fun resetPress(t: Float) {
        press("reset")
        if (!ps1On) return
        switchTvToPs1(t); startBoot(t)
    }
    fun lidToggle(t: Float, fromButton: Boolean) {
        if (fromButton) press("open")
        lidOpen = !lidOpen
        if (ps1On && !lidOpen) startBoot(t)
    }
    fun padPress(t: Float) { padPressAt = t }
    /** Seconds into the game loop (after the boot and title screens). */
    fun gameTime(t: Float) = t - bootStart - PS1_BOOT_SECONDS - 4
    /** The disc swap drives the lid directly. */
    fun setLid(open: Boolean) { lidOpen = open }
    fun showDiscLabel(tex: com.ps1bedroom.room.engine.Texture) { ps1Disc.materials[1].map = tex }
    /** A new disc is in: power on (or reboot) and put the TV on A/V 1. */
    fun gameInserted(t: Float) { if (!ps1On) powerToggle(t) else { switchTvToPs1(t); startBoot(t) } }

    // ---- The PlayStation's picture on A/V 1 (HTML 2802-3010) ----
    fun drawScreen(ctx: Canvas2D, w: Int, h: Int, t: Float) {
        when (phase(t)) {
            "off" -> {
                ctx.fillStyle = "#1e3a8a"; ctx.fillRect(0, 0, w, h)
                ctx.fillStyle = "#f8fafc"; ctx.font = "bold 9px monospace"
                ctx.textAlign = "left"; ctx.fillText("AV 1", 8, 14)
                ctx.textAlign = "center"; ctx.fillText("NO SIGNAL", 64, 52)
            }
            "menu" -> drawMenu(ctx, w, h, t)
            "boot" -> drawBoot(ctx, w, h, t - bootStart)
            "title" -> library.drawTitleScreen(ctx, w, h, library.currentGame(), t - bootStart - PS1_BOOT_SECONDS)
            else -> library.drawGameplay(ctx, w, h, t - bootStart - PS1_BOOT_SECONDS - 4, t)
        }
        ctx.textAlign = "left"
    }
    private fun drawBoot(ctx: Canvas2D, w: Int, h: Int, e: Float) {
        ctx.fillStyle = "#000000"; ctx.fillRect(0, 0, w, h)
        if (e > 0.5f && e < 3.8f) {
            // White publisher screen with a four-colour diamond
            val fadeIn = min(1f, (e - 0.5f) / 0.6f); val fadeOut = min(1f, max(0f, (3.8f - e) / 0.4f))
            ctx.globalAlpha = min(fadeIn, fadeOut)
            ctx.fillStyle = "#f8fafc"; ctx.fillRect(0, 0, w, h)
            val cx = 64; val cy = 38; val s = 15
            for ((col, a, b) in arrayOf(Triple("#f97316", intArrayOf(cx, cy - s), intArrayOf(cx + s, cy)), Triple("#ef4444", intArrayOf(cx + s, cy), intArrayOf(cx, cy + s)),
                    Triple("#a855f7", intArrayOf(cx, cy + s), intArrayOf(cx - s, cy)), Triple("#facc15", intArrayOf(cx - s, cy), intArrayOf(cx, cy - s)))) {
                ctx.fillStyle = col; ctx.beginPath(); ctx.moveTo(cx, cy); ctx.lineTo(a[0], a[1]); ctx.lineTo(b[0], b[1]); ctx.closePath(); ctx.fill()
            }
            ctx.fillStyle = "#111827"; ctx.font = "bold 9px sans-serif"; ctx.textAlign = "center"; ctx.fillText("SONY", 64, 68)
            ctx.fillStyle = "#6b7280"; ctx.font = "5px sans-serif"; ctx.fillText("COMPUTER ENTERTAINMENT", 64, 76)
            ctx.globalAlpha = 1f
            room.bedroomTvLight.color.setHex(0xf1f5f9)
        } else if (e > 4.3f) {
            // Black console logo screen
            ctx.globalAlpha = min(1f, (e - 4.3f) / 0.4f)
            ctx.font = "bold 30px \"Arial Black\", Impact, sans-serif"; ctx.textAlign = "center"
            ctx.fillStyle = "#facc15"; ctx.fillText("P", 52, 50)
            ctx.fillStyle = "#ef4444"; ctx.fillText("P", 50, 48)
            ctx.fillStyle = "#3b82f6"; ctx.fillText("S", 76, 54)
            ctx.fillStyle = "#22c55e"; ctx.fillText("S", 74, 52)
            ctx.fillStyle = "#f8fafc"; ctx.font = "italic bold 10px sans-serif"; ctx.fillText("PlayStation", 64, 70)
            ctx.fillStyle = "#9ca3af"; ctx.font = "4px sans-serif"
            ctx.fillText("Licensed by", 64, 84); ctx.fillText("Sony Computer Entertainment America", 64, 90)
            ctx.globalAlpha = 1f
            room.bedroomTvLight.color.setHex(0x94a3b8)
        }
    }
    private fun drawMenu(ctx: Canvas2D, w: Int, h: Int, t: Float) {
        val bg = ctx.createLinearGradient(0, 0, 0, h)
        bg.addColorStop(0, "#0b1026"); bg.addColorStop(1, "#1e293b")
        ctx.fillStyle = bg; ctx.fillRect(0, 0, w, h)
        ctx.fillStyle = "rgba(147, 197, 253, 0.35)"
        for (i in 0 until 14) {
            val px = (i * 37 + t * (6 + (i % 3) * 3)) % w
            val py = (i * 23 + sin(t + i) * 4 + h) % h
            ctx.fillRect(px.toInt(), py.toInt(), 1, 1)
        }
        val selected = floor(t / 2.5).toInt() % 2
        arrayOf("MEMORY CARD", "CD PLAYER").forEachIndexed { i, label ->
            val y = 30 + i * 24; val on = i == selected
            ctx.fillStyle = if (on) "#1d4ed8" else "#111827"; ctx.fillRect(20, y, 88, 16)
            ctx.strokeStyle = if (on) "rgba(191, 219, 254, ${0.6 + 0.4 * sin(t * 6.0)})" else "#475569"; ctx.lineWidth = 1f
            ctx.strokeRect(20.5, y + 0.5, 87, 15)
            ctx.fillStyle = "#f8fafc"; ctx.font = "bold 7px sans-serif"; ctx.textAlign = "center"; ctx.fillText(label, 64, y + 10)
        }
        room.bedroomTvLight.color.setHex(0x60a5fa)
    }
    /** Bandi-coot Rush: sunset sky, parallax mountains and palms, crates, fruit, a hopping orange critter. */
    fun drawPlatform(ctx: Canvas2D, w: Int, h: Int, g: Float, t: Float) {
        val scroll = g * 42; val groundY = 70; val heroX = 28
        val sky = ctx.createLinearGradient(0, 0, 0, groundY)
        sky.addColorStop(0, "#1e1b4b"); sky.addColorStop(0.55, "#7c3aed"); sky.addColorStop(1, "#f97316")
        ctx.fillStyle = sky; ctx.fillRect(0, 0, w, groundY)
        ctx.fillStyle = "#fde68a"; ctx.beginPath(); ctx.arc(98, 38, 11, 0, PI * 2); ctx.fill()
        ctx.fillStyle = "#4c1d95"
        val mOff = (scroll * 0.2f) % 40
        for (i in -1 until 5) {
            val x = i * 40 - mOff
            val k = (((i + floor(scroll * 0.2 / 40).toInt()) % 2) + 2) % 2
            ctx.beginPath(); ctx.moveTo(x, groundY - 6); ctx.lineTo(x + 20, 34 + k * 8); ctx.lineTo(x + 40, groundY - 6); ctx.fill()
        }
        val pOff = (scroll * 0.5f) % 52
        for (i in -1 until 4) {
            val x = i * 52 - pOff + 10
            ctx.fillStyle = "#3f2a14"; ctx.fillRect(x, groundY - 26, 3, 26)
            ctx.fillStyle = "#14532d"
            for ((dx, dy) in arrayOf(-6 to -28, 4 to -29, -1 to -32, 8 to -26, -9 to -25)) { ctx.beginPath(); ctx.arc(x + 1 + dx, groundY + dy, 5, 0, PI * 2); ctx.fill() }
        }
        // Ground: grass top over striped dirt
        ctx.fillStyle = "#92400e"; ctx.fillRect(0, groundY, w, h - groundY)
        ctx.fillStyle = "#78350f"
        val dOff = scroll % 12
        var dx = -dOff; while (dx < w) { ctx.fillRect(dx.toInt(), groundY + 6, 6, 3); dx += 12 }
        ctx.fillStyle = "#22c55e"; ctx.fillRect(0, groundY, w, 3)
        // Crates every 70 px of track; fruit floats between them and is collected as the hero passes
        var jump = 0.0
        for (n in floor((scroll - 40) / 70.0).toInt() until floor((scroll + w) / 70.0).toInt() + 1) {
            val cx = 70 * n + 40 - scroll
            if (cx > -14 && cx < w) {
                ctx.fillStyle = "#b45309"; ctx.fillRect(cx.toInt(), groundY - 12, 12, 12)
                ctx.strokeStyle = "#fbbf24"; ctx.lineWidth = 1f
                ctx.strokeRect(cx.toInt() + 0.5, groundY - 11.5, 11, 11)
                ctx.beginPath(); ctx.moveTo(cx + 1, groundY - 11); ctx.lineTo(cx + 11, groundY - 1); ctx.moveTo(cx + 11, groundY - 11); ctx.lineTo(cx + 1, groundY - 1); ctx.stroke()
            }
            val ahead = cx - heroX
            if (ahead > -10 && ahead < 18) jump = max(jump, sin(((18 - ahead) / 28.0) * PI) * 20)
            val fx = 70 * n + 75 - scroll
            if (fx > heroX + 2 && fx < w) {
                val fy = 48 + sin(g * 4.0 + n) * 2
                ctx.fillStyle = "#f97316"; ctx.beginPath(); ctx.arc(fx, fy, 3, 0, PI * 2); ctx.fill()
                ctx.fillStyle = "#fde047"; ctx.fillRect(fx - 1, fy - 2, 1, 1)
            }
        }
        val fruit = max(0, floor((scroll + heroX - 75) / 70.0).toInt() + 1)
        // Controller press makes the hero hop
        val padAge = t - padPressAt
        if (padAge >= 0 && padAge < 0.6f) jump = max(jump, sin((padAge / 0.6) * PI) * 24)
        // Hero: orange critter with blue pants and red sneakers, two-frame run cycle
        val by = groundY - 17 - jump
        val frame = floor(g * 10.0).toInt() % 2
        ctx.fillStyle = "#ea580c"; ctx.fillRect(heroX, by + 4, 8, 7); ctx.fillRect(heroX + 1, by - 2, 7, 6)
        ctx.fillStyle = "#ffffff"; ctx.fillRect(heroX + 5, by, 2, 2)
        ctx.fillStyle = "#000000"; ctx.fillRect(heroX + 6, by, 1, 1)
        ctx.fillStyle = "#1d4ed8"; ctx.fillRect(heroX, by + 11, 8, 3)
        ctx.fillStyle = "#dc2626"
        if (jump > 1) { ctx.fillRect(heroX, by + 14, 3, 2); ctx.fillRect(heroX + 5, by + 14, 3, 2) }
        else if (frame == 1) { ctx.fillRect(heroX - 1, by + 14, 3, 3); ctx.fillRect(heroX + 5, by + 14, 3, 3) }
        else { ctx.fillRect(heroX + 1, by + 14, 3, 3); ctx.fillRect(heroX + 4, by + 14, 3, 3) }
        // HUD: fruit count and lives
        ctx.fillStyle = "#f97316"; ctx.beginPath(); ctx.arc(9, 9, 3, 0, PI * 2); ctx.fill()
        ctx.fillStyle = "#fde68a"; ctx.font = "bold 7px monospace"; ctx.textAlign = "left"
        ctx.fillText("x ${(fruit % 100).toString().padStart(2, '0')}", 15, 12)
        ctx.fillStyle = "#ea580c"; ctx.fillRect(104, 5, 6, 6)
        ctx.fillStyle = "#fde68a"; ctx.fillText("x3", 112, 12)
    }
}
