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
            cyl(g, 0.11f, 0.002f, PS1.material(color = 0xd4d8e8), 0.0835f, 0.029f, 0f, 24)   // (on top of the well floor)
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
        run {
            val g = newGroup("ps3")
            val matte = PS1.material(color = 0x202125)
            val gloss = PS1.material(color = 0x0b0c0f)
            val grey = PS1.material(color = 0x9ca3af)
            feet(g, 0.27f, 0.27f, rubber)
            // the body, then the top curving down toward the front in steps (higher at the back)
            box(g, 0.58f, 0.085f, 0.58f, matte, 0f, 0.0465f, 0f, "ps1Body")
            box(g, 0.57f, 0.008f, 0.54f, matte, 0f, 0.092f, -0.01f, "ps1Body")
            box(g, 0.55f, 0.008f, 0.47f, matte, 0f, 0.1f, -0.04f, "ps1Body")
            box(g, 0.52f, 0.006f, 0.38f, matte, 0f, 0.107f, -0.08f, "ps1Body")
            // the embossed PS3 logo at the front right of the top
            val logo = tex(48, 16) { c, w, h -> c.fillStyle = "#202125"; c.fillRect(0, 0, w, h); c.fillStyle = "#34363d"; c.font = "bold 12px serif"; c.textAlign = "center"; c.fillText("PS3", w / 2, 13) }
            part(g, Geo.box(0.11f, 0.002f, 0.036f), logo, 0.15f, 0.0965f, 0.22f)
            // front (z = 0.29): the recessed glossy band with the slot, the eject triangle and power, the grey lip below
            box(g, 0.54f, 0.04f, 0.004f, gloss, 0f, 0.05f, 0.2915f)
            box(g, 0.28f, 0.008f, 0.004f, recess, 0.02f, 0.054f, 0.2945f)
            box(g, 0.012f, 0.006f, 0.004f, grey, 0.2f, 0.054f, 0.2945f, "ps1Open")
            box(g, 0.012f, 0.006f, 0.004f, grey, 0.235f, 0.054f, 0.2945f, "ps1Power")
            ps3Led = PS1.material(color = 0x3f1010, emissive = 0xef4444, emissiveIntensity = 0.9f)
            box(g, 0.006f, 0.003f, 0.003f, ps3Led, 0.25f, 0.054f, 0.2945f)
            for (ux in floatArrayOf(-0.23f, -0.2f)) box(g, 0.02f, 0.008f, 0.004f, recess, ux, 0.05f, 0.2945f)
            box(g, 0.56f, 0.008f, 0.006f, grey, 0f, 0.024f, 0.292f)
            // vents down both sides and across the back, and the room's plugs
            for (side in intArrayOf(-1, 1)) for (i in 0 until 5) box(g, 0.003f, 0.006f, 0.38f, recess, side * 0.2905f, 0.03f + i * 0.012f, -0.04f)
            for (i in 0 until 5) box(g, 0.3f, 0.005f, 0.003f, recess, 0f, 0.03f + i * 0.012f, -0.2915f)
            rearPlugs(g, -0.29f, dark)
            ps3PadLeds = PS1.material(color = 0x3f1010, emissive = 0xef4444, emissiveIntensity = 0f)
            val padBody = PS1.material(color = 0x101114)
            pad(g, "ps3", 0.3f, 0.45f, -0.3f, padBody, padBody, dark, ps3PadLeds)
        }

        // ================= PS4 Slim (CUH-2000): 265 x 39 x 288 mm -> 0.530 x 0.065 x 0.576 =================
        run {
            val g = newGroup("ps4")
            val matte = PS1.material(color = 0x16171b)
            feet(g, 0.24f, 0.26f, rubber)
            // the lower body set back, the gap all round the front, the thin top shell over it, its rounded edge
            box(g, 0.51f, 0.03f, 0.54f, matte, 0f, 0.018f, -0.018f, "ps1Body")
            box(g, 0.5f, 0.01f, 0.53f, recess, 0f, 0.037f, -0.012f)
            box(g, 0.53f, 0.022f, 0.576f, matte, 0f, 0.053f, 0f, "ps1Body")
            box(g, 0.51f, 0.004f, 0.556f, matte, 0f, 0.0655f, 0f, "ps1Body")
            // the chrome PS logo in the middle of the top
            val logo = tex(32, 32) { c, w, h -> c.fillStyle = "#16171b"; c.fillRect(0, 0, w, h); c.fillStyle = "#9ca3af"; c.font = "bold 18px sans-serif"; c.textAlign = "center"; c.fillText("PS", w / 2, 23) }
            part(g, Geo.box(0.05f, 0.002f, 0.05f), logo, 0f, 0.0685f, 0f)
            // in the front gap (z = 0.255): the slot, two USB ports, the little light bar, POWER and EJECT
            box(g, 0.28f, 0.005f, 0.004f, PS1.material(color = 0x000000), 0.02f, 0.037f, 0.2575f)
            for (ux in floatArrayOf(-0.19f, -0.16f)) box(g, 0.018f, 0.005f, 0.004f, PS1.material(color = 0x000000), ux, 0.037f, 0.2575f)
            ps4Bar = PS1.material(color = 0x0b0b10, emissive = 0x60a5fa, emissiveIntensity = 0f)
            box(g, 0.04f, 0.004f, 0.004f, ps4Bar, -0.235f, 0.037f, 0.2575f)
            box(g, 0.016f, 0.005f, 0.006f, PS1.material(color = 0x2a2c33), 0.19f, 0.037f, 0.258f, "ps1Power")
            box(g, 0.016f, 0.005f, 0.006f, PS1.material(color = 0x2a2c33), 0.215f, 0.037f, 0.258f, "ps1Open")
            // vents along the back of the shell, and the room's plugs
            for (i in 0 until 8) box(g, 0.04f, 0.012f, 0.003f, recess, -0.2f + i * 0.057f, 0.053f, -0.2895f)
            rearPlugs(g, -0.288f, dark, 0.05f)   // (lower, so they stay inside the shell's height)
            ps4PadBar = PS1.material(color = 0x0b0b10, emissive = 0x3b82f6, emissiveIntensity = 0f)
            val padBody = PS1.material(color = 0x17181d)
            pad(g, "ps4", 0.28f, 0.45f, -0.3f, padBody, padBody, dark, ps4PadBar)
        }

        // ================= PS5 Slim (lying down): 358 x 80 x 216 mm -> 0.716 x 0.134 x 0.432 =================
        // Four cover panels: glossy white on the left, matte white on the right (the seam shows the black core),
        // the black core between them, the removable disc drive at the right end, blue light along the front.
        run {
            val g = newGroup("ps5")
            val glossW = PS1.material(color = 0xf4f5f8)
            val matteW = PS1.material(color = 0xdfe2e7)
            val core = PS1.material(color = 0x111216)
            feet(g, 0.1f, 0.1f, rubber)
            box(g, 0.2f, 0.004f, 0.2f, PS1.material(color = 0x1a1b20), 0f, 0.008f, 0f)                       // the stand
            box(g, 0.66f, 0.1f, 0.39f, core, 0f, 0.068f, 0f, "ps1Body")                                        // the core (y 0.018 - 0.118)
            // bottom and top covers, each split at x = 0.14 (glossy left, matte right)
            box(g, 0.495f, 0.017f, 0.432f, glossW, -0.1105f, 0.0185f, 0f, "ps1Body"); box(g, 0.215f, 0.017f, 0.432f, matteW, 0.2505f, 0.0185f, 0f, "ps1Body")
            box(g, 0.495f, 0.018f, 0.432f, glossW, -0.1105f, 0.125f, 0f, "ps1Body"); box(g, 0.215f, 0.018f, 0.432f, matteW, 0.2505f, 0.125f, 0f, "ps1Body")
            box(g, 0.44f, 0.004f, 0.38f, glossW, -0.12f, 0.1355f, -0.005f, "ps1Body")                           // the covers' gentle bulge
            box(g, 0.18f, 0.004f, 0.38f, matteW, 0.25f, 0.1355f, -0.005f, "ps1Body")
            // blue light in both seams along the front
            ps5Light = PS1.material(color = 0x0b1220, emissive = 0x60a5fa, emissiveIntensity = 0f)
            box(g, 0.64f, 0.003f, 0.004f, ps5Light, 0f, 0.112f, 0.1975f); box(g, 0.64f, 0.003f, 0.004f, ps5Light, 0f, 0.031f, 0.1975f)   // (above the bottom cover, not behind it)
            // front of the core (z = 0.195): USB-C x2 and POWER on the left, the disc drive's slot and EJECT on the right
            for (ux in floatArrayOf(-0.29f, -0.268f)) box(g, 0.012f, 0.005f, 0.004f, PS1.material(color = 0x000000), ux, 0.06f, 0.1975f)
            box(g, 0.026f, 0.006f, 0.006f, PS1.material(color = 0x2a2c33), -0.3f, 0.09f, 0.198f, "ps1Power")
            box(g, 0.2f, 0.07f, 0.008f, core, 0.21f, 0.068f, 0.199f, "ps1Body")                                // the disc drive's front
            box(g, 0.17f, 0.005f, 0.004f, PS1.material(color = 0x000000), 0.21f, 0.075f, 0.2045f)
            box(g, 0.026f, 0.006f, 0.006f, PS1.material(color = 0x2a2c33), 0.21f, 0.052f, 0.205f, "ps1Open")
            // vent fins along the back, and the room's plugs
            for (i in 0 until 10) box(g, 0.004f, 0.08f, 0.008f, recess, -0.27f + i * 0.06f, 0.068f, -0.197f)
            rearPlugs(g, -0.195f, dark)
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

/** The consoles' TV screens, shared by the TV (with its light) and the start screen (without). */
object ConsoleScreens {
    private fun centerText(c: Canvas2D, s: String, x: Number, y: Number, font: String, color: String) { c.font = font; c.fillStyle = color; c.textAlign = "center"; c.fillText(s, x, y) }
    fun boot(key: String, c: Canvas2D, w: Int, h: Int, e: Float, light: (Int) -> Unit = {}) {
        c.fillStyle = "#000000"; c.fillRect(0, 0, w, h)
        val cx = w / 2.0
        when (key) {
            "ps2" -> {
                if (e < 4.0f) {
                    // the towers: blue-grey columns rising out of the fog, lit from below
                    val k = min(1f, e / 1.2f)
                    for (i in 0 until 14) {
                        val col = i % 7; val row = i / 7
                        val x = 6 + col * 17 + row * 8; val base = 74 + row * 10
                        val hgt = (8 + ((i * 37) % 28)) * k * (0.6f + 0.4f * sin(e * 1.3f + i))
                        c.fillStyle = if (row == 0) "#334155" else "#475569"; c.fillRect(x, (base - hgt).toInt(), 10, hgt.toInt())
                        c.fillStyle = "#93c5fd"; c.fillRect(x, (base - hgt).toInt(), 10, 1)
                    }
                    val fog = c.createLinearGradient(0, 40, 0, h); fog.addColorStop(0, "rgba(30,58,138,0)"); fog.addColorStop(1, "rgba(30,58,138,0.85)")
                    c.fillStyle = fog; c.fillRect(0, 40, w, h - 40)
                    c.globalAlpha = max(0f, min(1f, (e - 1.5f) / 0.8f)) * max(0f, min(1f, (4f - e) / 0.4f))
                    centerText(c, "SONY COMPUTER ENTERTAINMENT", cx, 22, "bold 5px sans-serif", "#e2e8f0"); c.globalAlpha = 1f
                } else if (e > 4.4f) {
                    c.globalAlpha = min(1f, (e - 4.4f) / 0.5f)
                    centerText(c, "PS2", cx, 54, "bold italic 26px sans-serif", "#e5e7eb")
                    centerText(c, "PlayStation 2", cx, 68, "6px sans-serif", "#94a3b8"); c.globalAlpha = 1f
                }
                light(0x3b82f6)
            }
            "ps3" -> {
                if (e > 0.6f && e < 3.6f) {
                    c.globalAlpha = min(1f, (e - 0.6f) / 0.6f) * min(1f, max(0f, (3.6f - e) / 0.5f))
                    centerText(c, "SONY", cx, 46, "bold 12px serif", "#e5e7eb")
                    centerText(c, "COMPUTER ENTERTAINMENT", cx, 56, "5px sans-serif", "#9ca3af"); c.globalAlpha = 1f
                } else if (e > 4.0f) {
                    xmbWave(c, w, h, e)
                    c.globalAlpha = min(1f, (e - 4.0f) / 0.6f)
                    centerText(c, "PLAYSTATION 3", cx, 46, "10px serif", "#f1f5f9"); c.globalAlpha = 1f
                }
                light(0x93c5fd)
            }
            "ps4" -> {
                // the bright blue light screen, then the logo over drifting shapes
                val g = c.createLinearGradient(0, 0, 0, h); g.addColorStop(0, "#1e3a8a"); g.addColorStop(1, "#60a5fa")
                c.globalAlpha = min(1f, e / 0.8f); c.fillStyle = g; c.fillRect(0, 0, w, h); c.globalAlpha = 1f
                for (i in 0 until 9) {
                    val x = ((i * 29 + e * (6 + i)) % (w + 20)) - 10; val y = (i * 13 + 20) % h
                    c.fillStyle = "rgba(255,255,255,0.12)"; c.beginPath(); c.arc(x, y, 6 + (i % 3) * 4, 0, PI * 2); c.fill()
                }
                c.globalAlpha = max(0f, min(1f, (e - 1.2f) / 0.8f))
                centerText(c, "PS4", cx, 54, "bold 24px sans-serif", "#ffffff"); c.globalAlpha = 1f
                light(0x60a5fa)
            }
            else -> { // ps5: particles drifting in to the middle, a white flash, then the logo
                if (e < 3.6f) {
                    val k = min(1f, e / 3.2f)
                    for (i in 0 until 40) {
                        val a = i * 2.4; val r0 = 70.0 * (1 - k) + 6
                        val x = cx + cos(a + e * 0.6) * r0 * (0.4 + (i % 5) * 0.15); val y = 48 + sin(a + e * 0.6) * r0 * 0.5 * (0.4 + (i % 5) * 0.15)
                        c.fillStyle = if (i % 3 == 0) "#93c5fd" else "#f8fafc"; c.fillRect(x.toInt(), y.toInt(), 1, 1)
                    }
                } else if (e < 4.3f) {
                    c.globalAlpha = 1f - (e - 3.6f) / 0.7f; c.fillStyle = "#ffffff"; c.fillRect(0, 0, w, h); c.globalAlpha = 1f
                } else {
                    val g = c.createLinearGradient(0, 0, 0, h); g.addColorStop(0, "#020617"); g.addColorStop(1, "#1e3a8a")
                    c.fillStyle = g; c.fillRect(0, 0, w, h)
                    c.globalAlpha = min(1f, (e - 4.3f) / 0.6f)
                    centerText(c, "PS5", cx, 56, "bold 24px sans-serif", "#ffffff"); c.globalAlpha = 1f
                }
                light(0xe0f2fe)
            }
        }
        c.textAlign = "left"
    }
    /** The PS3's wave: soft ribbons drifting over a blue gradient. */
    private fun xmbWave(c: Canvas2D, w: Int, h: Int, t: Float) {
        val g = c.createLinearGradient(0, 0, w, h); g.addColorStop(0, "#0c4a6e"); g.addColorStop(1, "#1e3a8a")
        c.fillStyle = g; c.fillRect(0, 0, w, h)
        for (r in 0 until 3) {
            c.strokeStyle = "rgba(255,255,255,${0.18 - r * 0.04})"; c.lineWidth = 1f; c.beginPath()
            for (x in 0..w step 4) { val y = 64 + sin(x * 0.05 + t * (0.6 + r * 0.2) + r) * (6 + r * 3); if (x == 0) c.moveTo(x, y) else c.lineTo(x, y) }
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
