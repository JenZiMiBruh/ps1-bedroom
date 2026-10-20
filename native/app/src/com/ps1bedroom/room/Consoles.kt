package com.ps1bedroom.room

import com.ps1bedroom.room.engine.Canvas2D
import com.ps1bedroom.room.engine.CatmullRomCurve3
import com.ps1bedroom.room.engine.Geo
import com.ps1bedroom.room.engine.GeoCurves
import com.ps1bedroom.room.engine.Geometry
import com.ps1bedroom.room.engine.Group
import com.ps1bedroom.room.engine.Material
import com.ps1bedroom.room.engine.Mesh
import com.ps1bedroom.room.engine.PS1
import com.ps1bedroom.room.engine.createCanvasTex
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The console under the TV: PS1 (the original, in Ps1.kt) or a PS2, PS3, PS4 or PS5. Each one is modelled from the
 * real console's measurements, scaled exactly like the PS1 (x2 across and deep, x1.67 tall), with its real front,
 * top, sides, rear, lights and controller. They all use the PS1's tap tags (power, eject, body, controller), so
 * powering on, discs, the TV input and the games work the same. Nothing is flush with anything else (flush faces
 * flicker) and nothing dips into the rug.
 */
class Consoles(private val room: Room, private val ps1: Ps1) {
    companion object {
        val NAMES = linkedMapOf("ps1" to "PS1", "ps2" to "PS2", "ps3" to "PS3", "ps4" to "PS4", "ps5" to "PS5")
        val FULL = mapOf("ps1" to "PlayStation", "ps2" to "PlayStation 2 Slim", "ps3" to "PlayStation 3 Slim", "ps4" to "PlayStation 4 Slim", "ps5" to "PlayStation 5 Slim")
        /** Half-width of each console: the jewel case on the floor moves out past it. */
        private val HALF_W = mapOf("ps1" to 0.27f, "ps2" to 0.23f, "ps3" to 0.29f, "ps4" to 0.265f, "ps5" to 0.358f)
    }
    var current = "ps1"; private set
    /** Where the disc goes in each console (the console groups share the PS1 group's transform, so these are in its space). */
    class DiscPath(val lid: Boolean, val home: com.ps1bedroom.room.engine.Vec3, val front: com.ps1bedroom.room.engine.Vec3, val scale: Float)
    private val PATHS = mapOf(
        "ps2" to DiscPath(true, com.ps1bedroom.room.engine.Vec3(0.0835f, 0.031f, 0f), com.ps1bedroom.room.engine.Vec3(0.0835f, 0.031f, 0f), 0.93f),
        "ps3" to DiscPath(false, com.ps1bedroom.room.engine.Vec3(0.09f, 0.028f, 0.12f), com.ps1bedroom.room.engine.Vec3(0.09f, 0.028f, 0.43f), 1f),
        "ps4" to DiscPath(false, com.ps1bedroom.room.engine.Vec3(0.04f, 0.028f, 0.1f), com.ps1bedroom.room.engine.Vec3(0.04f, 0.028f, 0.4f), 1f),
        "ps5" to DiscPath(false, com.ps1bedroom.room.engine.Vec3(0.18f, 0.04f, 0.04f), com.ps1bedroom.room.engine.Vec3(0.18f, 0.04f, 0.36f), 1f))
    /** null for the PS1 (its own lid and tray). */
    fun discPath(): DiscPath? = PATHS[current]
    private val groups = HashMap<String, Group>()
    private lateinit var ps2Lid: Group
    private lateinit var ps2PowerLed: Material; private lateinit var ps2EjectLed: Material; private lateinit var ps2PadLed: Material
    private lateinit var ps3Led: Material; private lateinit var ps3PadLeds: Material
    private lateinit var ps4Bar: Material; private lateinit var ps4PadBar: Material
    private lateinit var ps5Light: Material; private lateinit var ps5PadLight: Material
    /** Toasts, set by the app. */
    var toast: (String) -> Unit = {}

    private fun part(g: Group, geo: Geometry, mat: Material, x: Float, y: Float, z: Float, tag: String? = null): Mesh {
        val m = Mesh(geo, mat); m.position.set(x, y, z); m.castShadow = true; m.receiveShadow = true
        g.add(m); if (tag != null) room.registerInteractable(m, tag); return m
    }
    private fun box(g: Group, w: Float, h: Float, d: Float, mat: Material, x: Float, y: Float, z: Float, tag: String? = null) = part(g, Geo.box(w, h, d), mat, x, y, z, tag)
    private fun cyl(g: Group, r: Float, h: Float, mat: Material, x: Float, y: Float, z: Float, seg: Int = 10, tag: String? = null) = part(g, Geo.cylinder(r, r, h, seg), mat, x, y, z, tag)
    private fun tex(w: Int, h: Int, draw: (Canvas2D, Int, Int) -> Unit) = PS1.material(color = 0xffffff, map = createCanvasTex(w, h, draw = draw).tex)
    private fun newGroup(key: String): Group {
        val g = Group()
        g.position.copy(ps1.ps1Group.position); g.rotation.y = ps1.ps1Group.rotation.y
        g.visible = false
        room.scene.add(g); room.extraDynamicRoots += g
        groups[key] = g
        return g
    }
    private fun cable(g: Group, mat: Material, vararg pts: DoubleArray) {
        val m = Mesh(GeoCurves.tube(CatmullRomCurve3(pts.toList()), 14, 0.008f, 5), mat); m.castShadow = true; g.add(m)
    }

    /**
     * The controllers. Same body as the PS1's DualShock (grips, sticks, D-pad, face buttons), sitting ON the rug (the
     * grips' undersides at y = 0.002), plus each generation's own parts: shoulder buttons, START / SELECT or SHARE /
     * OPTIONS, the PS button, the DualShock 4's touchpad and light bar, the DualSense's two-tone body and lights.
     */
    private fun pad(g: Group, kind: String, x: Float, z: Float, rotY: Float, body: Material, grips: Material, dark: Material, light: Material? = null): Group {
        val p = Group(); p.position.set(x, 0.04f, z); p.rotation.y = rotY; g.add(p)
        val bridge = Mesh(Geo.box(0.24f, 0.038f, 0.115f), body); bridge.castShadow = true; p.add(bridge); room.registerInteractable(bridge, "ps1Pad")
        for (cx in floatArrayOf(-0.085f, 0.085f)) {
            val c = Mesh(Geo.cylinder(0.062f, 0.058f, 0.04f, 12), body); c.position.set(cx, 0.002f, 0f); c.castShadow = true; p.add(c); room.registerInteractable(c, "ps1Pad")
            val grip = Mesh(Geo.cylinder(0.034f, 0.022f, 0.135f, 8), grips); grip.rotation.x = (PI / 2).toFloat(); grip.rotation.z = if (cx < 0) 0.24f else -0.24f
            grip.position.set(cx * 1.15f, -0.004f, 0.065f); grip.castShadow = true; p.add(grip)
            val stick = Mesh(Geo.cylinder(0.022f, 0.016f, 0.03f, 10), dark); stick.position.set(cx * 0.52f, 0.03f, 0.04f); p.add(stick)
            val cap = Mesh(Geo.cylinder(0.025f, 0.025f, 0.006f, 10), dark); cap.position.set(cx * 0.52f, 0.046f, 0.04f); p.add(cap)
            // L1 / R1 and L2 / R2 on the back edge
            box(p, 0.05f, 0.012f, 0.02f, dark, cx * 1.1f, 0.012f, -0.064f); box(p, 0.046f, 0.016f, 0.022f, dark, cx * 1.1f, -0.006f, -0.07f)
        }
        val dh = Mesh(Geo.box(0.048f, 0.012f, 0.016f), dark); dh.position.set(-0.085f, 0.024f, -0.005f); p.add(dh)
        val dv = Mesh(Geo.box(0.016f, 0.012f, 0.048f), dark); dv.position.set(-0.085f, 0.024f, -0.005f); p.add(dv)
        val faceCols = if (kind == "ps5") intArrayOf(0x9ca3af, 0x9ca3af, 0x9ca3af, 0x9ca3af) else intArrayOf(0x22c55e, 0xef4444, 0x3b82f6, 0xec4899)
        for ((i, bz) in arrayOf(floatArrayOf(0.085f, -0.024f), floatArrayOf(0.106f, -0.004f), floatArrayOf(0.085f, 0.016f), floatArrayOf(0.064f, -0.004f)).withIndex()) {
            val btn = Mesh(Geo.cylinder(0.008f, 0.008f, 0.012f, 8), PS1.material(color = faceCols[i])); btn.position.set(bz[0], 0.024f, bz[1]); p.add(btn)
        }
        when (kind) {
            "ps2" -> { // DualShock 2: SELECT / START, the ANALOG button with its red LED
                box(p, 0.018f, 0.004f, 0.008f, dark, -0.022f, 0.0205f, -0.01f); box(p, 0.018f, 0.004f, 0.008f, dark, 0.022f, 0.0205f, -0.01f)
                cyl(p, 0.007f, 0.004f, dark, 0f, 0.0205f, 0.024f, 8)
                box(p, 0.006f, 0.003f, 0.004f, light!!, 0f, 0.0205f, 0.038f)
            }
            "ps3" -> { // DualShock 3: the round PS button, SELECT / START, four player LEDs on the back edge
                cyl(p, 0.009f, 0.004f, PS1.material(color = 0x9ca3af), 0f, 0.0205f, 0.024f, 10)
                box(p, 0.016f, 0.004f, 0.006f, dark, -0.022f, 0.0205f, -0.012f); box(p, 0.016f, 0.004f, 0.006f, dark, 0.022f, 0.0205f, -0.012f)
                for (i in 0 until 4) box(p, 0.006f, 0.004f, 0.003f, if (i == 0) light!! else dark, -0.018f + i * 0.012f, 0.008f, -0.059f)
            }
            "ps4" -> { // DualShock 4: the big touchpad, SHARE / OPTIONS, PS button, light bar across the back
                box(p, 0.082f, 0.004f, 0.052f, dark, 0f, 0.0205f, -0.022f)
                box(p, 0.008f, 0.004f, 0.012f, dark, -0.052f, 0.0205f, -0.03f); box(p, 0.008f, 0.004f, 0.012f, dark, 0.052f, 0.0205f, -0.03f)
                cyl(p, 0.008f, 0.004f, dark, 0f, 0.0205f, 0.03f, 10)
                box(p, 0.09f, 0.012f, 0.006f, light!!, 0f, 0.006f, -0.0605f)
            }
            "ps5" -> { // DualSense: black centre, white touchpad, blue light down both sides of it, PS logo button, create / options
                box(p, 0.13f, 0.004f, 0.075f, dark, 0f, 0.0205f, 0.005f)
                box(p, 0.08f, 0.005f, 0.05f, grips, 0f, 0.0225f, -0.024f)
                box(p, 0.004f, 0.004f, 0.046f, light!!, -0.043f, 0.0225f, -0.024f); box(p, 0.004f, 0.004f, 0.046f, light, 0.043f, 0.0225f, -0.024f)
                cyl(p, 0.008f, 0.005f, grips, 0f, 0.0225f, 0.03f, 10)
                box(p, 0.006f, 0.005f, 0.012f, grips, -0.05f, 0.0225f, -0.03f); box(p, 0.006f, 0.005f, 0.012f, grips, 0.05f, 0.0225f, -0.03f)
            }
        }
        return p
    }
    /** Plugs on the back for the room's A/V and power cables (they come in at the PS1's rear ports). */
    private fun rearPlugs(g: Group, rearZ: Float, dark: Material, y: Float = 0.055f) {
        for (x in floatArrayOf(-0.13f, 0.19f)) box(g, 0.06f, 0.022f, 0.03f, dark, x, y, rearZ - 0.0145f)
    }
    private fun feet(g: Group, hw: Float, hd: Float, mat: Material) {
        for (sx in intArrayOf(-1, 1)) for (sz in intArrayOf(-1, 1)) box(g, 0.04f, 0.006f, 0.04f, mat, sx * (hw - 0.04f), 0.003f, sz * (hd - 0.04f))
    }

    fun build() {
        val dark = room.matDarkPlastic
        val recess = PS1.material(color = 0x050608)
        val rubber = PS1.material(color = 0x0a0a0c)

        // ================= PS2 Slim (SCPH-70000): 230 x 28 x 152 mm -> 0.460 x 0.047 x 0.304 =================
        // Top-loading: the right two thirds of the top is the lid (hinged at the back), the raised PS2 band between.
        run {
            val g = newGroup("ps2")
            val black = PS1.material(color = 0x1c1d22)
            val line = PS1.material(color = 0x0c0d10)
            feet(g, 0.22f, 0.142f, rubber)
            // the layered body: lower half, the dark seam line all round, and the fixed left part of the top
            box(g, 0.452f, 0.021f, 0.296f, black, 0f, 0.0135f, 0f, "ps1Body")
            box(g, 0.446f, 0.004f, 0.29f, line, 0f, 0.026f, 0f)
            box(g, 0.143f, 0.02f, 0.304f, black, -0.1585f, 0.037f, 0f, "ps1Body")
            // the raised band with "PS2" embossed, running front to back
            val band = tex(16, 64) { c, w, h -> c.fillStyle = "#1a1b20"; c.fillRect(0, 0, w, h); c.save(); c.translate(11, 40); c.rotate(-PI / 2)
                c.fillStyle = "#2b2d34"; c.font = "bold 9px sans-serif"; c.textAlign = "center"; c.fillText("PS2", 0, 0); c.restore() }
            part(g, Geo.box(0.024f, 0.026f, 0.304f), band, -0.075f, 0.036f, 0f, "ps1Body")
            // the disc in its well (under the lid), and the lid on its hinge at the back
            ps2Lid = Group(); ps2Lid.position.set(0.0835f, 0.047f, -0.152f); g.add(ps2Lid)
            box(ps2Lid, 0.293f, 0.02f, 0.304f, black, 0f, -0.01f, 0.152f, "ps1Open")
            // front, lower half (z = 0.148): memory card slot + controller port x2, USB x2, the PS logo, POWER, OPEN, the vent grille
            for (px in floatArrayOf(-0.19f, -0.135f)) {
                box(g, 0.044f, 0.006f, 0.004f, recess, px, 0.019f, 0.1495f)
                box(g, 0.046f, 0.008f, 0.004f, recess, px, 0.009f, 0.1495f)
            }
            for (ux in floatArrayOf(-0.05f, -0.022f)) box(g, 0.018f, 0.008f, 0.004f, recess, ux, 0.013f, 0.1495f)
            val logo = tex(16, 16) { c, _, _ -> for ((col, x, y) in arrayOf(Triple("#ef4444", 0, 0), Triple("#facc15", 8, 0), Triple("#22c55e", 0, 8), Triple("#3b82f6", 8, 8))) { c.fillStyle = col; c.fillRect(x, y, 8, 8) } }
            part(g, Geo.box(0.014f, 0.014f, 0.003f), logo, 0.03f, 0.014f, 0.1495f)
            ps2PowerLed = PS1.material(color = 0x3f1010, emissive = 0xef4444, emissiveIntensity = 0.9f)
            box(g, 0.02f, 0.01f, 0.005f, black, 0.06f, 0.014f, 0.15f, "ps1Power"); box(g, 0.006f, 0.003f, 0.003f, ps2PowerLed, 0.06f, 0.0215f, 0.1495f)
            ps2EjectLed = PS1.material(color = 0x0b1a3a, emissive = 0x3b82f6, emissiveIntensity = 0f)
            box(g, 0.02f, 0.01f, 0.005f, black, 0.088f, 0.014f, 0.15f, "ps1Open"); box(g, 0.006f, 0.003f, 0.003f, ps2EjectLed, 0.088f, 0.0215f, 0.1495f)
            for (i in 0 until 5) box(g, 0.09f, 0.002f, 0.003f, recess, 0.17f, 0.007f + i * 0.004f, 0.1495f)
            // side vents and the rear; the room's A/V and power cables come down into the ports on the back
            for (i in 0 until 4) box(g, 0.003f, 0.003f, 0.1f, recess, 0.2265f, 0.008f + i * 0.004f, -0.04f)
            for (x in floatArrayOf(-0.13f, 0.19f)) {
                box(g, 0.05f, 0.014f, 0.02f, dark, x, 0.014f, -0.157f)
                cable(g, dark, doubleArrayOf(x.toDouble(), 0.055, -0.2), doubleArrayOf(x.toDouble(), 0.03, -0.182), doubleArrayOf(x.toDouble(), 0.016, -0.166))
            }
            // DualShock 2 plugged into port 1
            ps2PadLed = PS1.material(color = 0x3f1010, emissive = 0xef4444, emissiveIntensity = 0f)
            val padBody = PS1.material(color = 0x1d1f24)
            pad(g, "ps2", 0.3f, 0.3f, -0.38f, padBody, padBody, dark, ps2PadLed)
            box(g, 0.036f, 0.01f, 0.02f, dark, -0.19f, 0.009f, 0.16f)
            cable(g, dark, doubleArrayOf(-0.19, 0.009, 0.17), doubleArrayOf(-0.14, 0.009, 0.28), doubleArrayOf(0.08, 0.009, 0.33), doubleArrayOf(0.25, 0.03, 0.25))
        }

        // ================= PS3 Slim (CECH-2000): 290 x 65 x 290 mm -> 0.580 x 0.109 x 0.580 =================
        // One curved shell (Ps3Shell): the dome, highest toward the back, rolling down over the front and the sides,
        // rounded corners, the bottom tucked in. Under the front's rolled lip on the right, the recessed glossy pocket
        // with the slot and the silver lip below it; SONY and two USB ports on the plain left; PS3 on the top.
        run {
            val g = newGroup("ps3")
            val matte = PS1.material(color = 0x232428)
            val gloss = PS1.material(color = 0x08090b)
            val grey = PS1.material(color = 0xa9aeb6)
            val black = PS1.material(color = 0x000000)
            feet(g, 0.25f, 0.25f, rubber)
            part(g, Ps3Shell.geometry(), matte, 0f, 0.004f, 0f, "ps1Body")
            // the PS3 logo, lying on the curve of the top (tilted to its slope there, 2 mm above it)
            val logo = tex(48, 12) { c, w, h -> c.fillStyle = "#232428"; c.fillRect(0, 0, w, h); c.fillStyle = "#3d3f46"; c.font = "bold 11px serif"; c.textAlign = "center"; c.fillText("PS3", w / 2, 11) }
            val lx = 0.15f; val lz = 0.17f
            val logoMesh = part(g, Geo.box(0.12f, 0.002f, 0.024f), logo, lx, 0.004f + Ps3Shell.topY(lx, lz) + 0.002f, lz)
            logoMesh.rotation.set(-kotlin.math.atan(Ps3Shell.slopeZ(lx, lz)), 0f, kotlin.math.atan(Ps3Shell.slopeX(lx, lz)))
            // front (z = 0.29): the pocket (12 mm deep, so it stays in the shell where the bottom tucks in), the shadow
            // under the lip, the slot, the eject and power touch marks, the LED, the silver lip
            box(g, 0.32f, 0.028f, 0.012f, gloss, 0.1f, 0.026f, 0.286f)
            box(g, 0.32f, 0.004f, 0.006f, black, 0.1f, 0.041f, 0.2935f)
            box(g, 0.24f, 0.006f, 0.004f, black, 0.09f, 0.028f, 0.2945f)
            box(g, 0.008f, 0.003f, 0.003f, PS1.material(color = 0xe5e7eb), 0.09f, 0.035f, 0.2945f, "ps1Open")
            box(g, 0.008f, 0.003f, 0.003f, PS1.material(color = 0xe5e7eb), 0.09f, 0.02f, 0.2945f, "ps1Power")
            ps3Led = PS1.material(color = 0x3f1010, emissive = 0xef4444, emissiveIntensity = 0.9f)
            box(g, 0.005f, 0.003f, 0.003f, ps3Led, 0.105f, 0.02f, 0.2945f)
            box(g, 0.32f, 0.005f, 0.008f, grey, 0.1f, 0.009f, 0.288f)
            val sony = tex(32, 8) { c, w, h -> c.fillStyle = "#232428"; c.fillRect(0, 0, w, h); c.fillStyle = "#6b6f78"; c.font = "bold 7px sans-serif"; c.textAlign = "center"; c.fillText("SONY", w / 2, 7) }
            part(g, Geo.box(0.06f, 0.01f, 0.002f), sony, -0.22f, 0.026f, 0.2912f)
            for (ux in floatArrayOf(-0.25f, -0.22f)) box(g, 0.02f, 0.007f, 0.008f, black, ux, 0.012f, 0.2895f)
            // vents low down both sides and across the back, and the room's plugs
            for (side in intArrayOf(-1, 1)) for (i in 0 until 3) box(g, 0.003f, 0.005f, 0.3f, recess, side * 0.2905f, 0.022f + i * 0.009f, -0.02f)
            for (i in 0 until 4) box(g, 0.3f, 0.005f, 0.003f, recess, 0f, 0.03f + i * 0.011f, -0.2915f)
            rearPlugs(g, -0.29f, dark)
            ps3PadLeds = PS1.material(color = 0x3f1010, emissive = 0xef4444, emissiveIntensity = 0f)
            val padBody = PS1.material(color = 0x101114)
            pad(g, "ps3", 0.3f, 0.45f, -0.3f, padBody, padBody, dark, ps3PadLeds)
        }

        // ================= PS4 Slim (CUH-2000): 265 x 39 x 288 mm -> 0.530 x 0.065 x 0.576 =================
        // From the photos: the top shell is most of the height (22 mm), then a thin dark gap (3 mm) holding the slot,
        // buttons, USB ports and a little light, then a lower body (14 mm) almost the same size. SONY and PS4 on the
        // shell's front edge, the PlayStation symbol in the middle of the top.
        run {
            val g = newGroup("ps4")
            val matte = PS1.material(color = 0x17181c)
            feet(g, 0.24f, 0.26f, rubber)
            box(g, 0.522f, 0.023f, 0.566f, matte, 0f, 0.0145f, -0.005f, "ps1Body")                          // lower body
            box(g, 0.51f, 0.006f, 0.555f, recess, 0f, 0.028f, -0.008f)                                      // the gap
            box(g, 0.53f, 0.034f, 0.576f, matte, 0f, 0.047f, 0f, "ps1Body")                                  // top shell
            box(g, 0.514f, 0.004f, 0.56f, matte, 0f, 0.0645f, 0f, "ps1Body")                                 // its rounded top edge
            val symbol = tex(32, 32) { c, w, h -> c.fillStyle = "#17181c"; c.fillRect(0, 0, w, h); ConsoleScreens.psSymbol(c, w / 2.0, h / 2.0, 24.0, "#55585f", "#17181c") }
            part(g, Geo.box(0.06f, 0.002f, 0.06f), symbol, 0f, 0.0675f, 0f)
            val edge = tex(128, 8) { c, w, h -> c.fillStyle = "#17181c"; c.fillRect(0, 0, w, h); c.fillStyle = "#5c6068"; c.font = "bold 6px sans-serif"
                c.textAlign = "left"; c.fillText("SONY", 4, 6); c.textAlign = "right"; c.fillText("PS4", w - 4, 6) }
            part(g, Geo.box(0.5f, 0.012f, 0.002f), edge, 0f, 0.047f, 0.289f)
            // in the gap (front at z = 0.2695): slot, the light, power and eject, two USB ports
            box(g, 0.26f, 0.004f, 0.004f, PS1.material(color = 0x000000), 0.04f, 0.028f, 0.271f)
            ps4Bar = PS1.material(color = 0x0b0b10, emissive = 0x60a5fa, emissiveIntensity = 0f)
            box(g, 0.03f, 0.003f, 0.004f, ps4Bar, -0.12f, 0.028f, 0.271f)
            box(g, 0.012f, 0.004f, 0.005f, PS1.material(color = 0x2a2c33), -0.235f, 0.028f, 0.2715f, "ps1Power")
            box(g, 0.012f, 0.004f, 0.005f, PS1.material(color = 0x2a2c33), 0.2f, 0.028f, 0.2715f, "ps1Open")
            for (ux in floatArrayOf(-0.21f, -0.185f)) box(g, 0.018f, 0.004f, 0.004f, PS1.material(color = 0x000000), ux, 0.028f, 0.271f)
            for (i in 0 until 8) box(g, 0.04f, 0.014f, 0.003f, recess, -0.2f + i * 0.057f, 0.047f, -0.2895f)
            rearPlugs(g, -0.288f, dark, 0.047f)
            ps4PadBar = PS1.material(color = 0x0b0b10, emissive = 0x3b82f6, emissiveIntensity = 0f)
            val padBody = PS1.material(color = 0x17181d)
            pad(g, "ps4", 0.28f, 0.45f, -0.3f, padBody, padBody, dark, ps4PadBar)
        }

        // ================= PS5 Slim (lying down): 358 x 80 x 216 mm -> 0.716 x 0.134 x 0.432 =================
        // From the photos: a thin white top cover with its diagonal seam (glossy one side, matte the other), a narrow
        // black core band, and the thicker white bottom cover with the disc slot along its front; blue light at the core.
        run {
            val g = newGroup("ps5")
            val glossW = PS1.material(color = 0xf3f4f7)
            val matteW = PS1.material(color = 0xdfe2e7)
            val core = PS1.material(color = 0x111216)
            feet(g, 0.1f, 0.1f, rubber)
            box(g, 0.2f, 0.004f, 0.2f, PS1.material(color = 0x1a1b20), 0f, 0.006f, 0f)                       // the stand
            box(g, 0.716f, 0.052f, 0.432f, matteW, 0f, 0.034f, 0f, "ps1Body")                                  // bottom cover
            box(g, 0.69f, 0.046f, 0.4f, core, 0f, 0.081f, 0f, "ps1Body")                                       // the core
            box(g, 0.716f, 0.03f, 0.432f, glossW, 0f, 0.117f, 0f, "ps1Body")                                    // top cover
            box(g, 0.66f, 0.004f, 0.39f, glossW, 0f, 0.1335f, 0f, "ps1Body")                                    // its gentle rise
            part(g, Geo.box(0.005f, 0.003f, 0.42f), PS1.material(color = 0x9aa0aa), 0.16f, 0.1365f, 0f).rotation.y = 0.45f   // the diagonal seam (on the rise, 2.5 mm proud)
            // bottom cover front (z = 0.216): the disc slot and eject; core front (z = 0.2): USB-C x2, power, the blue light
            box(g, 0.24f, 0.005f, 0.004f, PS1.material(color = 0x000000), 0.18f, 0.04f, 0.2175f)
            box(g, 0.02f, 0.005f, 0.005f, PS1.material(color = 0xc3c7cf), 0.04f, 0.04f, 0.218f, "ps1Open")
            for (ux in floatArrayOf(-0.3f, -0.28f)) box(g, 0.012f, 0.005f, 0.004f, PS1.material(color = 0x000000), ux, 0.081f, 0.2015f)
            box(g, 0.022f, 0.006f, 0.005f, PS1.material(color = 0x2a2c33), -0.25f, 0.081f, 0.202f, "ps1Power")
            ps5Light = PS1.material(color = 0x0b1220, emissive = 0x60a5fa, emissiveIntensity = 0f)
            box(g, 0.64f, 0.003f, 0.004f, ps5Light, 0f, 0.098f, 0.2015f); box(g, 0.64f, 0.003f, 0.004f, ps5Light, 0f, 0.063f, 0.2015f)
            for (i in 0 until 10) box(g, 0.004f, 0.036f, 0.008f, recess, -0.27f + i * 0.06f, 0.081f, -0.202f)
            rearPlugs(g, -0.2f, dark)
            ps5PadLight = PS1.material(color = 0x0b1220, emissive = 0x60a5fa, emissiveIntensity = 0f)
            pad(g, "ps5", 0.33f, 0.4f, -0.3f, PS1.material(color = 0x15161a), glossW, PS1.material(color = 0x1d1e23), ps5PadLight)
        }
        for (g in groups.values) g.traverse { if (it is Mesh) it.receiveShadow = true }
    }

    /** Puts a console under the TV (the PS1 is hidden for the others; its jewel case stays on the floor, clear of it). */
    fun select(key: String, quiet: Boolean = false) {
        current = if (NAMES.containsKey(key)) key else "ps1"
        apply()
        if (!quiet) toast("🎮 ${FULL[current]} under the TV. Tap POWER on it, or the controller.")
    }
    private fun apply() {
        ps1.showModel(current == "ps1")
        for ((k, g) in groups) g.visible = k == current
        ps1.console = current
        ps1.library.moveFloorCase(-(HALF_W.getValue(current) + 0.17f))
        // the game's disc: in the PS1's tray, in the PS2 Slim's well (seen when the lid opens), or out of sight inside a slot loader
        val p = PATHS[current]
        val d = ps1.disc
        if (p == null) { d.visible = true; d.position.copy(ps1.discHome); d.scale.set(1f, 1f, 1f) }
        else { d.visible = p.lid; d.position.copy(p.home); d.scale.set(p.scale, p.scale, p.scale) }
        room.onShadowsChanged()
    }

    private var lastShadowPoke = 0f
    /** Lights and the PS2's tray, every frame. */
    fun update(t: Float, dt: Float) {
        if (current == "ps1") return
        val on = ps1.ps1On; val ph = ps1.phase(t)
        when (current) {
            "ps2" -> {
                ps2PowerLed.emissive.setHex(if (on) 0x22c55e else 0xef4444); ps2PowerLed.emissiveIntensity = 0.9f
                ps2EjectLed.emissiveIntensity = if (on) 0.9f else 0f
                ps2PadLed.emissiveIntensity = if (on) 0.8f else 0f
                // the top-loading lid swings up on its back hinge
                val target = if (ps1.lidOpen) -1.1f else 0f
                val d = target - ps2Lid.rotation.x
                if (abs(d) > 0.001f) { ps2Lid.rotation.x += d * min(1f, dt * 9); if (t - lastShadowPoke > 0.1f) { lastShadowPoke = t; room.onShadowsChanged() } }
            }
            "ps3" -> { ps3Led.emissive.setHex(if (on) 0x22c55e else 0xef4444); ps3PadLeds.emissiveIntensity = if (on) 0.9f else 0f }
            "ps4" -> {
                // light bar: pulsing blue while it boots, white when on, off when off
                val k = if (!on) 0f else if (ph == "boot") 0.6f + 0.4f * sin(t * 4) else 0.9f
                ps4Bar.emissive.setHex(if (ph == "boot") 0x3b82f6 else 0xf8fafc); ps4Bar.emissiveIntensity = k
                ps4PadBar.emissiveIntensity = if (on) 0.9f else 0f
            }
            "ps5" -> {
                val k = if (!on) 0f else if (ph == "boot") 0.5f + 0.5f * sin(t * 3) else 0.85f
                ps5Light.emissiveIntensity = k; ps5PadLight.emissiveIntensity = if (on) 0.8f else 0f
            }
        }
    }

    // ---------------- On the TV: each console's boot-up (7.5 s, like the PS1's) and home menu ----------------
    private val tvLight: (Int) -> Unit = { room.bedroomTvLight.color.setHex(it) }
    fun drawBoot(c: Canvas2D, w: Int, h: Int, e: Float) = ConsoleScreens.boot(current, c, w, h, e, tvLight)
    fun drawMenu(c: Canvas2D, w: Int, h: Int, t: Float) = ConsoleScreens.menu(current, c, w, h, t, tvLight)
}

/**
 * The consoles' boot-ups, recreated from recordings of the real ones (timed down to the PS1's 7.5 s boot), and their
 * home screens. Everything is laid out from the centre in units of u = min(w, h) / 96, so the same drawing fills the
 * TV (128 x 96) and the whole phone screen on the app's start screen, portrait or landscape.
 */
object ConsoleScreens {
    private fun fade(e: Float, a: Float, b: Float, inT: Float = 0.5f, outT: Float = 0.5f) =
        max(0f, min(1f, min((e - a) / inT, (b - e) / outT)))
    private fun centerText(c: Canvas2D, s: String, x: Number, y: Number, font: String, color: String) { c.font = font; c.fillStyle = color; c.textAlign = "center"; c.fillText(s, x, y) }

    /** The PlayStation symbol: the bold P standing on the flattened S (checked against the real logo). (cx, cy) is its centre, s its height. */
    fun psSymbol(c: Canvas2D, cx: Double, cy: Double, s: Double, color: String, bg: String) {
        c.fillStyle = color
        // the P: a tall stem, and the big round bowl at the top right with its hole
        c.beginPath(); c.moveTo(cx - 0.16 * s, cy - 0.5 * s); c.lineTo(cx + 0.04 * s, cy - 0.45 * s); c.lineTo(cx + 0.04 * s, cy + 0.3 * s); c.lineTo(cx - 0.16 * s, cy + 0.4 * s); c.closePath(); c.fill()
        c.beginPath(); c.moveTo(cx, cy - 0.46 * s); c.bezierCurveTo(cx + 0.44 * s, cy - 0.4 * s, cx + 0.46 * s, cy, cx + 0.12 * s, cy + 0.02 * s); c.lineTo(cx + 0.02 * s, cy - 0.02 * s); c.closePath(); c.fill()
        c.fillStyle = bg
        c.beginPath(); c.moveTo(cx + 0.04 * s, cy - 0.3 * s); c.bezierCurveTo(cx + 0.16 * s, cy - 0.27 * s, cx + 0.17 * s, cy - 0.16 * s, cx + 0.08 * s, cy - 0.15 * s); c.lineTo(cx + 0.04 * s, cy - 0.16 * s); c.closePath(); c.fill()
        c.fillStyle = color
        // the S, lying flat in front: its left arm folds back on itself, its right arm sweeps up to the right
        c.beginPath(); c.moveTo(cx - 0.54 * s, cy + 0.3 * s); c.lineTo(cx - 0.18 * s, cy + 0.15 * s); c.lineTo(cx - 0.18 * s, cy + 0.27 * s); c.lineTo(cx - 0.33 * s, cy + 0.33 * s)
        c.lineTo(cx - 0.18 * s, cy + 0.37 * s); c.lineTo(cx - 0.18 * s, cy + 0.48 * s); c.lineTo(cx - 0.56 * s, cy + 0.39 * s); c.closePath(); c.fill()
        c.beginPath(); c.moveTo(cx + 0.06 * s, cy + 0.27 * s); c.lineTo(cx + 0.46 * s, cy + 0.1 * s); c.bezierCurveTo(cx + 0.6 * s, cy + 0.09 * s, cx + 0.58 * s, cy + 0.25 * s, cx + 0.42 * s, cy + 0.3 * s)
        c.lineTo(cx + 0.06 * s, cy + 0.42 * s); c.closePath(); c.fill()
    }

    fun boot(key: String, c: Canvas2D, w: Int, h: Int, e: Float, light: (Int) -> Unit = {}) {
        val u = min(w, h) / 96.0
        val cx = w / 2.0; val cy = h / 2.0
        c.globalAlpha = 1f
        c.fillStyle = "#000000"; c.fillRect(0, 0, w, h)
        when (key) {
            // PS2 (from the real boot): white translucent blocks drifting through dark-blue space round a blue glow, with
            // "Sony Computer Entertainment"; a moment of black; the white "PlayStation 2" logo.
            "ps2" -> {
                if (e < 4.3f) {
                    val a = fade(e, 0f, 4.3f, 0.4f, 0.5f)
                    c.globalAlpha = a
                    val gx = cx - 18 * u; val gy = cy - 6 * u
                    val glow = c.createRadialGradient(gx, gy, 0, gx, gy, 46 * u)
                    glow.addColorStop(0, "rgba(59,92,220,0.9)"); glow.addColorStop(0.45, "rgba(23,37,120,0.55)"); glow.addColorStop(1, "rgba(0,0,0,0)")
                    c.fillStyle = glow; c.fillRect(0, 0, w, h)
                    // the blocks: each flies out from the glow along its own line, growing as it nears
                    for (i in 0 until 64) {
                        val ang = i * 2.399 + 0.3
                        val d = ((i * 0.137 + e * (0.05 + (i % 5) * 0.012)) % 1.0)
                        val r = (8 + d * 90) * u
                        val x = gx + cos(ang) * r * 1.3; val y = gy + sin(ang) * r * 0.75
                        val sz = (1.5 + d * 9) * u * (0.6 + (i % 3) * 0.3)
                        val lightK = 0.45 + d * 0.5
                        c.globalAlpha = a * min(1.0, d * 4).toFloat()
                        c.fillStyle = "rgba(${(200 * lightK).toInt()},${(206 * lightK).toInt()},${(225 * lightK).toInt()},0.85)"
                        c.fillRect(x - sz / 2, y - sz * 0.35, sz, sz * 0.7)
                        c.fillStyle = "rgba(240,244,255,${0.5 * lightK})"; c.fillRect(x - sz / 2, y - sz * 0.35, sz, sz * 0.18)
                    }
                    c.globalAlpha = a * fade(e, 0.5f, 3.6f, 0.7f, 0.6f)
                    centerText(c, "Sony Computer Entertainment", cx, cy + 4 * u, "${(6.5 * u).toInt()}px sans-serif", "#f1f5f9")
                    light(0x3b5cdc)
                } else if (e > 4.6f) {
                    c.globalAlpha = fade(e, 4.6f, 7.4f, 0.6f, 0.5f)
                    centerText(c, "PlayStation", cx - 4 * u, cy + 3 * u, "${(13 * u).toInt()}px sans-serif", "#f8fafc")
                    centerText(c, "2", cx + 33 * u, cy + 3 * u, "bold ${(15 * u).toInt()}px sans-serif", "#f8fafc")
                    light(0x94a3b8)
                }
            }
            // PS3: SONY COMPUTER ENTERTAINMENT, then PLAYSTATION 3 over the wave (kept as it was: it's right)
            "ps3" -> {
                if (e > 0.6f && e < 3.6f) {
                    c.globalAlpha = fade(e, 0.6f, 3.6f, 0.6f, 0.5f)
                    centerText(c, "SONY", cx, cy - 2 * u, "bold ${(12 * u).toInt()}px serif", "#e5e7eb")
                    centerText(c, "COMPUTER ENTERTAINMENT", cx, cy + 8 * u, "${(5 * u).toInt()}px sans-serif", "#9ca3af")
                } else if (e > 4.0f) {
                    xmbWave(c, w, h, e, u)
                    c.globalAlpha = min(1f, (e - 4.0f) / 0.6f)
                    centerText(c, "PLAYSTATION 3", cx, cy - 2 * u, "${(10 * u).toInt()}px serif", "#f1f5f9")
                }
                light(0x93c5fd)
            }
            // PS4 (from the real boot): the white PlayStation symbol alone on black, then the deep blue "Welcome Back to
            // PlayStation" screen with its light wave sweeping across the bottom.
            "ps4" -> {
                if (e < 4.4f) {
                    c.globalAlpha = fade(e, 0.3f, 4.4f, 0.7f, 0.4f)
                    psSymbol(c, cx, cy - 4 * u, 26 * u, "#ffffff", "#000000")
                    light(0x64748b)
                } else {
                    c.globalAlpha = min(1f, (e - 4.4f) / 0.5f)
                    val g = c.createLinearGradient(0, 0, 0, h); g.addColorStop(0, "#002b8a"); g.addColorStop(1, "#0047d1")
                    c.fillStyle = g; c.fillRect(0, 0, w, h)
                    for (k in 0 until 3) {
                        c.strokeStyle = "rgba(160,200,255,${0.32 - k * 0.09})"; c.lineWidth = ((3 - k) * u).toFloat(); c.beginPath()
                        var x = 0.0
                        while (x <= w) { val y = h - (18 + k * 7) * u + sin(x / w * 3.2 + e * 0.7 + k) * 8 * u; if (x == 0.0) c.moveTo(x, y) else c.lineTo(x, y); x += 2 * u }
                        c.stroke()
                    }
                    centerText(c, "Welcome Back to PlayStation", cx, cy - 6 * u, "${(5.5 * u).toInt()}px sans-serif", "#eef4ff")
                    centerText(c, "Press the PS button to use the controller.", cx, cy + 2 * u, "${(3.6 * u).toInt()}px sans-serif", "#c7d7ff")
                    light(0x2563eb)
                }
            }
            // PS5 (from the real boot): the silver PlayStation symbol on black, the dark navy glow with the health warning,
            // drifting specks of light, then into the console.
            else -> {
                if (e < 3.4f) {
                    c.globalAlpha = fade(e, 0.2f, 3.4f, 0.8f, 0.4f)
                    psSymbol(c, cx, cy - 2 * u, 22 * u, "#c9ccd3", "#000000")
                    light(0x475569)
                } else {
                    val g = c.createRadialGradient(cx, cy, 0, cx, cy, max(w, h) * 0.75)
                    g.addColorStop(0, "#0f2341"); g.addColorStop(1, "#02060f")
                    c.globalAlpha = min(1f, (e - 3.4f) / 0.4f); c.fillStyle = g; c.fillRect(0, 0, w, h)
                    if (e < 5.2f) {
                        c.globalAlpha = fade(e, 3.6f, 5.2f, 0.4f, 0.4f)
                        c.fillStyle = "#9aa7bd"
                        for (i in 0 until 4) { val lw = (70 - (if (i == 3) 34 else 0)) * u; c.fillRect(cx - 35 * u, cy - 6 * u + i * 3.5 * u, lw, 1.2 * u) }
                    } else {
                        c.globalAlpha = min(1f, (e - 5.2f) / 0.5f)
                        for (i in 0 until 46) {
                            val px = (i * 37.7 + e * (3 + i % 4) * u) % w; val py = (i * 23.3 + sin(e + i) * 3 * u) % h
                            c.fillStyle = if (i % 4 == 0) "#e0f2fe" else "#94a3b8"; c.fillRect(px, py, max(1.0, u * 0.6), max(1.0, u * 0.6))
                        }
                    }
                    light(0x1e3a8a)
                }
            }
        }
        c.globalAlpha = 1f
        c.textAlign = "left"
    }
    /** The PS3's wave: soft ribbons drifting over a blue gradient. */
    private fun xmbWave(c: Canvas2D, w: Int, h: Int, t: Float, u: Double = min(w, h) / 96.0) {
        val g = c.createLinearGradient(0, 0, w, h); g.addColorStop(0, "#0c4a6e"); g.addColorStop(1, "#1e3a8a")
        c.fillStyle = g; c.fillRect(0, 0, w, h)
        for (r in 0 until 3) {
            c.strokeStyle = "rgba(255,255,255,${0.18 - r * 0.04})"; c.lineWidth = u.toFloat(); c.beginPath()
            var x = 0.0
            while (x <= w) { val y = h * 0.66 + sin(x / u * 0.05 + t * (0.6 + r * 0.2) + r) * (6 + r * 3) * u; if (x == 0.0) c.moveTo(x, y) else c.lineTo(x, y); x += 4 * u }
            c.stroke()
        }
    }
    /** The home screen while the tray / disc is out (the PS1 shows its memory card / CD player menu). */
    fun menu(current: String, c: Canvas2D, w: Int, h: Int, t: Float, light: (Int) -> Unit = {}) {
        val sel = floor(t / 2.5).toInt()
        when (current) {
            "ps2" -> {
                c.fillStyle = "#0b1226"; c.fillRect(0, 0, w, h)
                for (i in 0 until 10) { val x = 6 + i * 12; val hh = 10 + (i * 7) % 18; c.fillStyle = "#1e293b"; c.fillRect(x, 92 - hh, 8, hh) }
                arrayOf("Browser", "System Configuration").forEachIndexed { i, s ->
                    val on = sel % 2 == i; c.fillStyle = if (on) "#bfdbfe" else "#64748b"; c.font = "bold 7px sans-serif"; c.textAlign = "center"; c.fillText(s, w / 2, 36 + i * 18)
                }
            }
            "ps3" -> {
                xmbWave(c, w, h, t)
                val items = arrayOf("Settings", "Photo", "Music", "Video", "Game", "Network")
                items.forEachIndexed { i, s ->
                    val on = sel % items.size == i; val x = 14 + i * 20
                    c.fillStyle = if (on) "#f8fafc" else "rgba(248,250,252,0.45)"; c.fillRect(x, 24, 10, 10)
                    if (on) { c.font = "6px sans-serif"; c.textAlign = "center"; c.fillText(s, x + 5, 44) }
                }
            }
            "ps4" -> {
                val g = c.createLinearGradient(0, 0, 0, h); g.addColorStop(0, "#1e3a8a"); g.addColorStop(1, "#0f172a"); c.fillStyle = g; c.fillRect(0, 0, w, h)
                for (i in 0 until 5) { val on = sel % 5 == i; c.fillStyle = if (on) "#60a5fa" else "#334155"; c.fillRect(8 + i * 23, if (on) 38 else 42, if (on) 22 else 18, if (on) 22 else 18) }
                c.font = "6px sans-serif"; c.fillStyle = "#e2e8f0"; c.textAlign = "left"; c.fillText("Insert a disc", 8, 76)
            }
            else -> {
                c.fillStyle = "#020617"; c.fillRect(0, 0, w, h)
                for (i in 0 until 4) { val on = sel % 4 == i; c.fillStyle = if (on) "#f8fafc" else "#1e293b"; c.fillRect(10 + i * 28, 30, 24, 32) }
                c.font = "6px sans-serif"; c.fillStyle = "#93c5fd"; c.textAlign = "left"; c.fillText("Insert a disc", 10, 76)
            }
        }
        light(0x60a5fa)
        c.textAlign = "left"
    }
}
