package com.ps1bedroom.room

import android.content.res.Resources
import com.ps1bedroom.room.engine.CatmullRomCurve3
import com.ps1bedroom.room.engine.Geo
import com.ps1bedroom.room.engine.GeoCurves
import com.ps1bedroom.room.engine.Geometry
import com.ps1bedroom.room.engine.Glb
import com.ps1bedroom.room.engine.Group
import com.ps1bedroom.room.engine.Material
import com.ps1bedroom.room.engine.Mesh
import com.ps1bedroom.room.engine.Object3D
import com.ps1bedroom.room.engine.PS1
import com.ps1bedroom.room.engine.PointLight
import com.ps1bedroom.room.engine.Vec3
import com.ps1bedroom.room.engine.Weather
import com.ps1bedroom.room.engine.createCanvasTex
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * String lights, windowsill plants, the Kenney furniture-kit models and the 2000s gadgets
 * (HTML lines 12290-12505: string lights, succulent & cactus, Kenney models, Discman, Game Kid, Noka, Tamagotchu).
 */
class Decor(private val room: Room, private val res: Resources) {
    private val scene = room.scene
    private val stringBulbMats = ArrayList<Material>()
    val stringBulbs = ArrayList<Mesh>()
    private lateinit var stringGlowA: PointLight
    private lateinit var stringGlowB: PointLight
    var stringLightsMode = 0; private set   // 0 off · 1 warm · 2 rainbow
    var stringLightsOn = false; private set
    /** Built at the end of the room: the HTML's models load asynchronously, after everything else (grime 686+). */
    val kenneyRoots = ArrayList<Object3D>()

    private fun propPart(parent: Object3D, geo: Geometry, mat: Material, x: Float, y: Float, z: Float, tag: String? = null): Mesh {
        val m = Mesh(geo, mat)
        m.position.set(x, y, z); m.castShadow = true; m.receiveShadow = true
        parent.add(m)
        if (tag != null) room.registerInteractable(m, tag)
        return m
    }

    fun build() {
        // ---- String lights draped along the top of the back and right walls (off -> warm white -> rainbow -> off) ----
        PS1.syncGrime(288)
        for (c in intArrayOf(0xffd9a0, 0xff6b9d, 0x7ff3ff, 0xffe066, 0x9dff8a)) stringBulbMats += PS1.material(color = c, emissive = c, emissiveIntensity = 0.05f)
        val stringWireMat = PS1.material(color = 0x1a1a1a)
        fun drapeString(points: List<FloatArray>, sag: Float, bulbEvery: Float) {
            val curvePts = ArrayList<DoubleArray>()
            for (s in 0 until points.size - 1) {
                val a = points[s]; val b = points[s + 1]
                for (k in 0 until 12) {
                    val u = k / 12.0
                    curvePts += doubleArrayOf(a[0] + (b[0] - a[0]) * u, a[1] + (b[1] - a[1]) * u - sin(PI * u) * sag, a[2] + (b[2] - a[2]) * u)
                }
            }
            val last = points[points.size - 1]
            curvePts += doubleArrayOf(last[0].toDouble(), last[1].toDouble(), last[2].toDouble())
            val curve = CatmullRomCurve3(curvePts)
            val wire = Mesh(GeoCurves.tube(curve, curvePts.size * 2, 0.006f, 4), stringWireMat)
            scene.add(wire)
            val len = curve.getLength()
            var d = bulbEvery * 0.5
            while (d < len) {
                val p = curve.getPointAt(d / len)
                val bulb = Mesh(Geo.sphere(0.028f, 6, 4), stringBulbMats[0])
                bulb.position.set(p[0], p[1] - 0.03, p[2])
                bulb.scale.y = 1.35f
                scene.add(bulb); room.registerInteractable(bulb, "string")
                stringBulbs += bulb
                d += bulbEvery
            }
        }
        drapeString(listOf(floatArrayOf(-3.6f, 4.86f, -2.9f), floatArrayOf(-1.6f, 4.86f, -2.9f), floatArrayOf(0.4f, 4.86f, -2.9f),
            floatArrayOf(2.4f, 4.86f, -2.9f), floatArrayOf(4.25f, 4.86f, -2.9f)), 0.14f, 0.24f)
        drapeString(listOf(floatArrayOf(4.28f, 4.86f, -2.85f), floatArrayOf(4.28f, 4.86f, -0.9f), floatArrayOf(4.28f, 4.86f, 1.1f), floatArrayOf(4.28f, 4.86f, 3.3f)), 0.14f, 0.24f)
        stringGlowA = PointLight(0xffc98a, 0f, 4.5f, 1.8f); stringGlowA.position.set(0.4f, 4.55f, -2.5f); scene.add(stringGlowA)
        stringGlowB = PointLight(0xffc98a, 0f, 4.5f, 1.8f); stringGlowB.position.set(3.9f, 4.55f, 0.4f); scene.add(stringGlowB)

        // ---- A little succulent and a cactus on the windowsill ----
        val potMat = PS1.material(color = 0xb4532a)
        val leafMat = PS1.material(color = 0x3f8f4f)
        for ((px, kind) in listOf(1.82f to 0, -0.28f to 1)) {
            propPart(scene, Geo.cylinder(0.075f, 0.055f, 0.12f, 8), potMat, px, 1.635f, -2.86f)
            propPart(scene, Geo.cylinder(0.068f, 0.068f, 0.012f, 8), PS1.material(color = 0x3b2516), px, 1.69f, -2.86f)
            if (kind == 0) {
                for (i in 0 until 7) {
                    val leaf = propPart(scene, Geo.sphere(0.045f, 5, 3), leafMat, px + cos(i * 0.9f) * 0.04f, 1.72f + (i % 3) * 0.012f, -2.86f + sin(i * 0.9f) * 0.04f)
                    leaf.scale.set(0.5f, 1.5f, 0.5f)
                    leaf.rotation.set(sin(i * 0.9f) * 0.6f, 0f, -cos(i * 0.9f) * 0.6f)
                }
            } else {
                propPart(scene, Geo.cylinder(0.03f, 0.035f, 0.16f, 7), PS1.material(color = 0x4d7c3a), px, 1.77f, -2.86f)
                val arm = propPart(scene, Geo.cylinder(0.018f, 0.02f, 0.07f, 6), PS1.material(color = 0x4d7c3a), px + 0.04f, 1.78f, -2.86f)
                arm.rotation.z = -0.9f
                propPart(scene, Geo.sphere(0.014f, 5, 3), PS1.material(color = 0xff6fa8, emissive = 0xff6fa8, emissiveIntensity = 0.2f), px, 1.86f, -2.86f)
            }
        }

        // ---- 2000s gadgets (lookalikes with parody names) ----
        // Portable CD player with earbuds, lying on the bed
        val discman = Group()
        discman.position.set(2.75f, 0.93f, -0.6f); discman.rotation.y = 0.5f
        scene.add(discman)
        propPart(discman, Geo.cylinder(0.14f, 0.14f, 0.035f, 20), PS1.material(color = 0xb8bec8), 0f, 0.0175f, 0f, "discman")
        propPart(discman, Geo.cylinder(0.105f, 0.105f, 0.004f, 20), PS1.material(color = 0x8a93a3), 0f, 0.037f, 0f, "discman")
        propPart(discman, Geo.cylinder(0.02f, 0.02f, 0.006f, 10), PS1.material(color = 0x3b4252), 0f, 0.04f, 0f, "discman")
        propPart(discman, Geo.box(0.06f, 0.008f, 0.02f), PS1.material(color = 0x1f2937, emissive = 0x22c55e, emissiveIntensity = 0.4f), 0.07f, 0.038f, 0.09f, "discman")
        // Earbud cable: headphone jack -> Y-splitter -> a wire to each bud
        val wireMat = PS1.material(color = 0x15161a)
        fun wire(pts: List<DoubleArray>) = propPart(discman, GeoCurves.tube(CatmullRomCurve3(pts), 20, 0.0075f, 5), wireMat, 0f, 0f, 0f)
        wire(listOf(doubleArrayOf(-0.135, 0.02, 0.04), doubleArrayOf(-0.2, 0.024, 0.1), doubleArrayOf(-0.33, 0.022, 0.16), doubleArrayOf(-0.38, 0.022, 0.28), doubleArrayOf(-0.32, 0.022, 0.34)))
        wire(listOf(doubleArrayOf(-0.32, 0.022, 0.34), doubleArrayOf(-0.38, 0.022, 0.42), doubleArrayOf(-0.4, 0.022, 0.5)))
        wire(listOf(doubleArrayOf(-0.32, 0.022, 0.34), doubleArrayOf(-0.25, 0.022, 0.42), doubleArrayOf(-0.2, 0.022, 0.5)))
        propPart(discman, Geo.cylinder(0.011f, 0.011f, 0.03f, 6), wireMat, -0.32f, 0.024f, 0.34f).rotation.x = (PI / 2).toFloat() // splitter
        propPart(discman, Geo.cylinder(0.009f, 0.009f, 0.03f, 6), PS1.material(color = 0x9ca3af), -0.13f, 0.02f, 0.035f).rotation.z = (PI / 2).toFloat() // jack
        for ((bx, bz) in listOf(-0.4f to 0.52f, -0.2f to 0.52f)) {
            propPart(discman, Geo.sphere(0.024f, 8, 6), wireMat, bx, 0.026f, bz)
            propPart(discman, Geo.cylinder(0.016f, 0.016f, 0.006f, 8), PS1.material(color = 0x3b3f47), bx, 0.048f, bz)
        }
        // Handheld game console on the nightstand, resting on the book behind the clock radio
        val gba = Group()
        gba.position.set(1.25f, 0.656f, -2.73f); gba.rotation.y = 0.15f
        scene.add(gba)
        val gbaTex = createCanvasTex(64, 32, weather = Weather(seed = 404.0, blotch = 0.12, scratches = 3)) { ctx, w, h ->
            ctx.fillStyle = "#5b3fa8"; ctx.fillRect(0, 0, w, h)
            ctx.fillStyle = "#1b1530"; ctx.fillRect(18, 5, 28, 21)
            ctx.fillStyle = "#9bbc0f"; ctx.fillRect(21, 8, 22, 15)
            ctx.fillStyle = "#306230"; ctx.fillRect(25, 13, 6, 6); ctx.fillRect(34, 15, 4, 4)
            ctx.fillStyle = "#2a1d55"; ctx.fillRect(5, 12, 9, 3); ctx.fillRect(8, 9, 3, 9)
            ctx.beginPath(); ctx.arc(52, 13, 2.5, 0, PI * 2); ctx.arc(57, 18, 2.5, 0, PI * 2); ctx.fill()
            ctx.fillStyle = "#d6d0f0"; ctx.font = "bold 4px sans-serif"; ctx.fillText("GAME KID ADVANCE", 15, 30)
        }.tex
        propPart(gba, Geo.box(0.2f, 0.03f, 0.09f, 2, 1, 1), PS1.material(color = 0x5b3fa8), 0f, 0.015f, 0f, "gba")
        val gbaFace = Mesh(Geo.plane(0.2f, 0.09f), PS1.material(map = gbaTex))
        gbaFace.rotation.x = (-PI / 2).toFloat(); gbaFace.position.set(0f, 0.031f, 0f)
        gba.add(gbaFace); room.registerInteractable(gbaFace, "gba")
        // Brick phone and a virtual pet on the dresser
        val brick = Group()
        brick.position.set(-3.3f, 1.43f, 1.85f); brick.rotation.y = 1.3f
        scene.add(brick)
        val brickTex = createCanvasTex(32, 64, weather = Weather(seed = 3310.0, blotch = 0.15, scratches = 4)) { ctx, w, h ->
            ctx.fillStyle = "#26334a"; ctx.fillRect(0, 0, w, h)
            ctx.fillStyle = "#9fbf6a"; ctx.fillRect(6, 8, 20, 16)
            ctx.fillStyle = "#2b3a1a"; ctx.fillRect(8, 11, 10, 2); ctx.fillRect(8, 15, 14, 2); ctx.fillRect(8, 19, 6, 2)
            ctx.fillStyle = "#c9d1dc"
            for (r in 0 until 4) for (c in 0 until 3) ctx.fillRect(7 + c * 7, 32 + r * 7, 5, 4)
            ctx.fillStyle = "#e2e8f0"; ctx.font = "bold 5px sans-serif"; ctx.fillText("NOKA", 10, 6)
        }.tex
        propPart(brick, Geo.box(0.06f, 0.025f, 0.13f, 1, 1, 2), PS1.material(color = 0x26334a), 0f, 0.0125f, 0f, "brickphone")
        val brickFace = Mesh(Geo.plane(0.06f, 0.13f), PS1.material(map = brickTex))
        brickFace.rotation.x = (-PI / 2).toFloat(); brickFace.position.y = 0.026f
        brick.add(brickFace); room.registerInteractable(brickFace, "brickphone")
        val tama = Group()
        tama.position.set(-3.22f, 1.43f, 1.93f) // native fix: on the dresser top, between the trophy and the boombox (it used to share the trophy's spot)
        scene.add(tama)
        val egg = propPart(tama, Geo.sphere(0.04f, 10, 8), PS1.material(color = 0xff8fc8), 0f, 0.049f, 0f, "tama")
        egg.scale.set(1f, 1.2f, 0.6f)
        val tamaFace = Mesh(Geo.plane(0.035f, 0.03f), PS1.material(color = 0x9fbf6a, emissive = 0x9fbf6a, emissiveIntensity = 0.15f))
        tamaFace.position.set(0.026f, 0.054f, 0f); tamaFace.rotation.y = (PI / 2).toFloat()
        tama.add(tamaFace); room.registerInteractable(tamaFace, "tama")
    }

    /** Low-poly models from Kenney's Furniture Kit (CC0 1.0, kenney.nl), materials swapped for PS1 ones. */
    fun buildKenney() {
        PS1.syncGrime(686)
        fun placeKenney(rawId: Int, pos: Vec3, rotY: Float = 0f, scale: Float = 1.45f, tag: String? = null) {
            val bytes = res.openRawResource(rawId).use { it.readBytes() }
            val root = Glb.load(bytes) { hex -> PS1.material(color = hex) }
            root.traverse { o ->
                if (o is Mesh) { o.castShadow = true; o.receiveShadow = true; if (tag != null) room.registerInteractable(o, tag) }
            }
            root.position.copy(pos); root.rotation.y = rotY; root.scale.set(scale, scale, scale)
            // native fix: rest the model's lowest point exactly on its surface (the box's flaps dipped into the floor)
            root.updateMatrixWorld()
            var minY = Float.MAX_VALUE
            val v = Vec3()
            root.traverse { o -> if (o is Mesh) { val p = o.geometry.positions; for (i in 0 until o.geometry.vertexCount) { v.set(p[i * 3], p[i * 3 + 1], p[i * 3 + 2]).applyMatrix4(o.matrixWorld); if (v.y < minY) minY = v.y } } }
            // (+1.5 cm: the PS1 vertex snap wobbles near-flush faces through the surface under them)
            if (minY != Float.MAX_VALUE) root.position.y += pos.y - minY + 0.015f
            scene.add(root); kenneyRoots += root
        }
        placeKenney(R.raw.kenney_speaker_small, Vec3(-1.95f, 1.5f, -1.45f), 0.55f, 1.35f)
        placeKenney(R.raw.kenney_speaker_small, Vec3(-0.72f, 1.5f, -1.32f), 0.1f, 1.35f)
        placeKenney(R.raw.kenney_potted_plant, Vec3(-3.35f, 0f, 3.15f), 0.6f, 1.5f)
        placeKenney(R.raw.kenney_coat_rack_standing, Vec3(1.5f, 0f, 3.15f), 0.3f, 2.4f)
        placeKenney(R.raw.kenney_cardboard_box_open, Vec3(-3.25f, 0f, 0.05f), 0.4f, 2.0f, "box")
        placeKenney(R.raw.kenney_plant_small2, Vec3(-3.45f, 1.43f, 2.8f), 0f, 1.4f)
    }

    fun cycleStringLights() = setStringLights((stringLightsMode + 1) % 3)
    fun setStringLights(mode: Int) {
        stringLightsMode = mode
        stringLightsOn = mode > 0
        stringBulbs.forEachIndexed { i, b -> b.material = if (mode == 2) stringBulbMats[1 + (i % 4)] else stringBulbMats[0] }
        val col = if (mode == 2) 0xd9a0ff else 0xffc98a
        for (l in arrayOf(stringGlowA, stringGlowB)) { l.color.setHex(col); l.intensity = if (mode > 0) 0.55f else 0f }
    }
    fun update(t: Float) {
        if (!stringLightsOn) { for (m in stringBulbMats) m.emissiveIntensity = 0.05f; return }
        for (i in stringBulbMats.indices) {
            stringBulbMats[i].emissiveIntensity = if (stringLightsMode == 2) 0.55f + 0.45f * max(0f, sin(t * 1.6f + i * 1.3f)) else 0.85f + sin(t * 0.9f + i) * 0.05f
        }
    }
}
