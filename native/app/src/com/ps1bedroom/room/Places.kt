package com.ps1bedroom.room

import com.ps1bedroom.room.engine.Canvas2D
import com.ps1bedroom.room.engine.Color
import com.ps1bedroom.room.engine.DOUBLE_SIDE
import com.ps1bedroom.room.engine.Geo
import com.ps1bedroom.room.engine.GeoCurves
import com.ps1bedroom.room.engine.Geometry
import com.ps1bedroom.room.engine.Group
import com.ps1bedroom.room.engine.Material
import com.ps1bedroom.room.engine.Mesh
import com.ps1bedroom.room.engine.Object3D
import com.ps1bedroom.room.engine.PS1
import com.ps1bedroom.room.engine.Texture
import com.ps1bedroom.room.engine.createCanvasTex
import com.ps1bedroom.room.engine.makeRng
import android.os.SystemClock
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** One place outside the window (HTML OUTSIDE_ENVS). */
class OutsideEnv(
    val name: String, val icon: String, val city: Double, val trees: Boolean = false, val wind: Double = 0.0, val waves: Double = 0.0,
    val ground: String? = null, val temp: Int = 0, val noSnow: Boolean = false, val noGround: Boolean = false, val tropical: Boolean = false,
    val climate: Map<String, List<String>>? = null
)

/**
 * HTML 14241-15609: 1.2 OUTSIDE THE WINDOW. Pick where the bedroom is: every place follows the real sky, time of day,
 * seasons and weather, and has its own painted backdrop ([PlacesPaint]), 3D things out front, climate and temperature.
 * (The sounds of each place come with the audio phase.)
 */
class Places(
    private val room: Room, private val env: Environment, private val sky: Sky, private val exterior: Exterior,
    private val weatherFx: WeatherFx, private val onShadows: () -> Unit
) {
    private val scene = room.scene
    private val rnd = Random.Default

    companion object {
        val OUTSIDE_ENVS: Map<String, OutsideEnv> = linkedMapOf(
            "city" to OutsideEnv("Downtown", "🏙️", 1.0, trees = true),
            "skyscraper" to OutsideEnv("Skyscraper (40th floor)", "🏢", 0.45, wind = 0.55, noGround = true, temp = -3,
                climate = mapOf("any" to listOf("windy", "mist", "partly", "clear", "overcast"))),
            "neon" to OutsideEnv("Neon City", "🌃", 1.0, ground = "asphalt", climate = mapOf("any" to listOf("drizzle", "rain", "mist", "overcast", "partly"))),
            "neighborhood" to OutsideEnv("Neighborhood", "🏘️", 0.6, ground = "asphalt"),
            "suburbs" to OutsideEnv("Suburbs", "🏡", 0.3, trees = true),
            "house" to OutsideEnv("Backyard", "🛝", 0.12, trees = true),
            "trailer" to OutsideEnv("Trailer Park", "🚐", 0.18, ground = "gravel", temp = 2),
            "arizona" to OutsideEnv("Arizona", "🌵", 0.15, ground = "gravel", temp = 9, noSnow = true,
                climate = mapOf("summer" to listOf("clear", "clear", "heat", "storm", "windy", "partly"), "any" to listOf("clear", "clear", "clear", "partly", "windy"))),
            "desert" to OutsideEnv("Desert Canyon", "🏜️", 0.0, wind = 0.6, ground = "desert", temp = 6, noSnow = true,
                climate = mapOf("any" to listOf("clear", "clear", "windy", "partly", "heat"))),
            "island" to OutsideEnv("Tropical Island", "🏝️", 0.0, waves = 0.8, ground = "sand", temp = 9, noSnow = true, tropical = true,
                climate = mapOf("any" to listOf("partly", "clear", "sunshower", "rain", "partly", "storm"))),
            "beach" to OutsideEnv("Beach Boardwalk", "🎡", 0.1, waves = 1.0, ground = "sand", temp = 2),
            "lake" to OutsideEnv("Lakeside", "🛶", 0.0, waves = 0.25, climate = mapOf("any" to listOf("mist", "clear", "partly", "fog"))),
            "forest" to OutsideEnv("Forest", "🌲", 0.0, trees = true, ground = "forest", climate = mapOf("any" to listOf("mist", "fog", "partly", "drizzle", "clear"))),
            "mountains" to OutsideEnv("Mountains", "🏔️", 0.0, wind = 0.45, temp = -7,
                climate = mapOf("winter" to listOf("snow", "flurries", "blizzard", "clear"), "any" to listOf("clear", "windy", "mist", "partly"))),
            "countryside" to OutsideEnv("Countryside Farm", "🐄", 0.03, trees = true)
        )
    }

    val outsideEnv get() = sky.outsideEnv
    fun envDef() = OUTSIDE_ENVS[outsideEnv] ?: OUTSIDE_ENVS.getValue("city")
    fun outsideCityFactor() = if (outsideEnv == "city") 1.0 else envDef().city
    fun outsideTempOffset() = if (outsideEnv == "city") 0 else envDef().temp
    /** Auto weather leans toward each place's climate (Arizona monsoons, misty forests, island showers...). */
    fun climatePick(options: List<String>): String {
        val e = envDef()
        var opts = options
        if (e.noSnow) opts = opts.map { k -> when (k) { "flurries" -> "partly"; "snow" -> if (e.tropical) "rain" else "overcast"; "blizzard" -> if (e.tropical) "storm" else "windy"; else -> k } }
        val pool = e.climate?.let { it[env.currentSeason] ?: it["any"] }
        if (pool != null && rnd.nextDouble() < 0.55) {
            val p = pool.filter { k -> env.currentSeason == "winter" || !(Environment.WEATHER_KINDS[k]?.snow ?: false) }
            if (p.isNotEmpty()) return p[floor(rnd.nextDouble() * p.size).toInt()]
        }
        return opts.getOrNull(floor(rnd.nextDouble() * opts.size).toInt()) ?: "clear"
    }

    val paint = PlacesPaint(sky, env, this)

    // ---- 3D things out front (only the chosen place's group is shown) ----
    private val G = LinkedHashMap<String, Group>()
    private val abyss: Mesh
    private val pineMat: Material
    private val pineSnowMat: Material
    private val barkMat: Material
    private val palmLeafMat: Material
    private val swayers = ArrayList<Group>()
    private val windTrees = ArrayList<Group>()
    private val laundryItems = ArrayList<Mesh>()
    private val snowCaps = ArrayList<Mesh>()
    private val nightGlowMats = ArrayList<Material>() // lamps, porch lights, string lights: on after dark
    private val neonMats = ArrayList<Material>()
    private val crowns = ArrayList<Mesh>()
    private val summerOnly = ArrayList<Mesh>()
    private val facades = ArrayList<Mesh>()
    private val puddleMats = ArrayList<Material>()
    private val swings = ArrayList<Group>()
    private val chimeRods = ArrayList<Mesh>()
    private val tikiFlames = ArrayList<Mesh>()
    private lateinit var tumbleweed: Mesh
    private lateinit var foam: Mesh
    private class Water(val mesh: Mesh, val tex: Texture, val mat: Material)
    private lateinit var islandSea: Water
    private lateinit var beachSea: Water
    private lateinit var lakeWater: Water
    private class SkyAd(val ctx: Canvas2D, val tex: Texture, val mat: Material, var next: Long, val beacon: Material)
    private lateinit var skyAd: SkyAd
    private lateinit var suburbWindowMat: Material
    private lateinit var litWinMat: Material
    private lateinit var tvWinMat: Material
    private lateinit var darkWinMat: Material
    private val leafMats = HashMap<String, Material>()
    private lateinit var decidMat: Material
    private lateinit var bushMat: Material
    private lateinit var tuftMat: Material
    private lateinit var pineSpr: Material

    // Big flat faces warp and swim with the PS1 texture effect when they're a single polygon: every large box and
    // plane outside is cut into a coarse grid (~1.6-unit pieces), the HTML's re-cut pass done at creation
    private fun seg(v: Float) = min(8, max(1, (v / 1.6f).roundToInt()))
    private fun boxG(w: Float, h: Float, d: Float, ws: Int = 1, hs: Int = 1, ds: Int = 1) =
        if (max(w, max(h, d)) > 2.4f) Geo.box(w, h, d, seg(w), seg(h), seg(d)) else Geo.box(w, h, d, ws, hs, ds)
    private fun planeG(w: Float, h: Float, ws: Int = 1, hs: Int = 1) =
        if (max(w, h) > 2.4f) Geo.plane(w, h, seg(w), seg(h)) else Geo.plane(w, h, ws, hs)

    private fun mat(col: Int) = PS1.material(color = col)
    private fun box(group: Object3D, w: Float, h: Float, d: Float, col: Int, x: Float, y: Float, z: Float, m: Material? = null): Mesh {
        val mesh = Mesh(boxG(w, h, d), m ?: mat(col)); mesh.position.set(x, y, z); group.add(mesh); return mesh
    }
    private fun cyl(group: Object3D, rt: Float, rb: Float, h: Float, col: Int, x: Float, y: Float, z: Float, seg: Int = 7, m: Material? = null): Mesh {
        val mesh = Mesh(Geo.cylinder(rt, rb, h, seg), m ?: mat(col)); mesh.position.set(x, y, z); group.add(mesh); return mesh
    }
    private fun add(group: Object3D, geo: Geometry, m: Material, x: Float, y: Float, z: Float): Mesh { val mesh = Mesh(geo, m); mesh.position.set(x, y, z); group.add(mesh); return mesh }
    private fun sub(group: Object3D, x: Float, y: Float, z: Float, rotY: Float = 0f): Group { val g = Group(); g.position.set(x, y, z); g.rotation.y = rotY; group.add(g); return g }
    private val P2 = (PI / 2).toFloat()
    private val P4 = (PI / 4).toFloat()
    private val PF = PI.toFloat()

    private fun pine3D(group: Group, x: Float, z: Float, s: Float): Group {
        val p = sub(group, x, 0.32f, z); p.scale.set(s, s, s)
        cyl(p, 0.06f, 0.09f, 0.6f, 0, 0f, 0.3f, 0f, 5, barkMat)
        for ((r, hh, y) in listOf(Triple(0.62f, 0.9f, 0.75f), Triple(0.48f, 0.8f, 1.25f), Triple(0.32f, 0.7f, 1.7f))) {
            add(p, Geo.cone(r, hh, 7), pineMat, 0f, y, 0f)
            snowCaps += add(p, Geo.cone(r * 0.55f, hh * 0.45f, 7), pineSnowMat, 0f, y + hh * 0.28f, 0f)
        }
        windTrees += p
        return p
    }
    private fun palm3D(group: Group, x: Float, z: Float, s: Float, lean: Float, tall: Boolean = false): Group {
        val p = sub(group, x, 0.32f, z); p.scale.set(s, s, s); p.rotation.z = lean
        val n = if (tall) 10 else 6
        for (i in 0 until n) {
            val segm = cyl(p, (if (tall) 0.05f else 0.07f) - i * 0.003f, (if (tall) 0.06f else 0.09f) - i * 0.003f, 0.42f, 0,
                sin(i * 0.25f) * (if (tall) 0.02f else 0.08f) * i, 0.21f + i * 0.4f, 0f, 6, barkMat)
            segm.rotation.z = (if (tall) -0.01f else -0.05f) * i
        }
        val top = sub(p, if (tall) 0.15f else 0.5f, n * 0.41f, 0f)
        for (i in 0 until 8) {
            val pv = Group(); pv.rotation.set(0f, (i / 8f) * PF * 2, -0.5f); top.add(pv)
            add(pv, Geo.box(1.0f, 0.02f, 0.2f), palmLeafMat, 0.45f, 0f, 0f)
        }
        swayers += top
        return p
    }
    private fun cactus3D(group: Group, x: Float, z: Float, s: Float, col: Int = 0x3f7a46) {
        val g = sub(group, x, 0.32f, z); g.scale.set(s, s, s)
        val cac = mat(col)
        cyl(g, 0.13f, 0.15f, 1.8f, 0, 0f, 0.9f, 0f, 7, cac)
        for ((side, y, len) in listOf(Triple(-1, 0.8f, 0.5f), Triple(1, 1.1f, 0.4f))) {
            cyl(g, 0.08f, 0.08f, 0.35f, 0, side * 0.28f, y, 0f, 6, cac).rotation.z = P2
            cyl(g, 0.08f, 0.08f, len, 0, side * 0.43f, y + len / 2, 0f, 6, cac)
        }
    }
    private fun rocks(group: Group, list: List<FloatArray>, col: Int): List<Mesh> {
        val m = mat(col)
        return list.map { (x, z, s) -> val r = add(group, Geo.dodecahedron(s, 0), m, x, 0.32f + s * 0.35f, z); r.scale.y = 0.6f; r }
    }
    private fun parkedCar(group: Group, kind: String, x: Float, z: Float, rotY: Float = 0f): Group {
        val v = exterior.parkedVehicle(kind)
        v.scale.set(2.3f, 2.3f, 2.3f); v.position.set(x, 0.32f - 0.04f * 2.3f, z); v.rotation.y = rotY
        group.add(v)
        return v
    }
    private fun road(group: Group, z: Float, wdt: Float, col: Int = 0x2b2f36): Mesh { val m = add(group, planeG(30f, wdt), mat(col), 0.8f, 0.335f, z); m.rotation.x = -P2; return m }
    private fun lampPost(group: Group, x: Float, z: Float, glowMats: MutableList<Material>) {
        cyl(group, 0.03f, 0.04f, 2.2f, 0x2f3540, x, 1.42f, z, 5)
        val m = PS1.material(color = 0xfff3c4, emissive = 0xffd27d, emissiveIntensity = 0f); glowMats += m
        box(group, 0.28f, 0.08f, 0.14f, 0, x + 0.12f, 2.5f, z, m)
    }
    private fun waterPlane(group: Group, z0: Float, depth: Float, colA: String, calm: Boolean): Water {
        val tex = createCanvasTex(64, 64, 10f, 4f) { c, _, _ ->
            c.fillStyle = colA; c.fillRect(0, 0, 64, 64)
            val r = makeRng(if (calm) 3 else 9)
            for (i in 0 until (if (calm) 40 else 120)) { c.fillStyle = "rgba(255,255,255,${0.08 + r() * 0.22})"; c.fillRect(r() * 64, r() * 64, 3 + r() * 8, 1) }
        }.tex
        tex.wrapRepeat = true; tex.linearMin = true
        val m = PS1.material(map = tex, emissive = 0xffffff, emissiveMap = tex, emissiveIntensity = 0.3f)
        val mesh = add(group, planeG(30f, depth, 10, 3), m, 0.8f, 0.34f, z0 - depth / 2); mesh.rotation.x = -P2
        return Water(mesh, tex, m)
    }
    private fun signTexture(text: String, fg: String, bg: String, wpx: Int = 128, hpx: Int = 32): Texture = createCanvasTex(wpx, hpx) { c, _, _ ->
        c.fillStyle = bg; c.fillRect(0, 0, wpx, hpx)
        val vertical = hpx > wpx
        c.fillStyle = fg; c.font = "bold ${floor(if (vertical) min(wpx * 0.85, (hpx.toDouble() / text.length) * 0.9) else min(hpx * 0.7, (wpx.toDouble() / text.length) * 1.5)).toInt()}px monospace"
        c.textAlign = "center"; c.textBaseline = "middle"
        if (vertical) text.forEachIndexed { i, ch -> c.fillText(ch.toString(), wpx / 2.0, (i + 0.5) * (hpx.toDouble() / text.length)) } else c.fillText(text, wpx / 2.0, hpx / 2.0 + 1)
    }.tex
    private fun neonSign(group: Object3D, text: String, fg: String, x: Float, y: Float, z: Float, w: Float, h: Float, vertical: Boolean): Mesh {
        val tex = signTexture(text, fg, "#0a0a12", if (vertical) 32 else 128, if (vertical) 128 else 32)
        val m = PS1.material(map = tex, emissive = 0xffffff, emissiveMap = tex, emissiveIntensity = 0.4f); neonMats += m
        return add(group, boxG(w, h, 0.06f), m, x, y, z)
    }

    // ---- Texture kit for the outside: small hand-made PS1-style textures (64 px, crunchy, tiled) ----
    private val TEXKIT = HashMap<String, Texture>()
    private fun kitTex(kind: String, col: String, rx: Float = 1f, ry: Float = 1f): Texture {
        val key = "$kind|$col|$rx|$ry"
        TEXKIT[key]?.let { return it }
        val S = 64
        val r = makeRng(kind.length * 13 + (col.removePrefix("#").toInt(16) % 997))
        val base = Color().setStyle(col)
        fun shade(k: Double) = "rgb(${min(255.0, base.r * 255.0 * k).toInt()},${min(255.0, base.g * 255.0 * k).toInt()},${min(255.0, base.b * 255.0 * k).toInt()})"
        val t = createCanvasTex(S, S, rx, ry) { c, _, _ ->
            c.fillStyle = shade(1.0); c.fillRect(0, 0, S, S)
            fun speck(n: Int, lo: Double, hi: Double, sz: Int = 1) { for (i in 0 until n) { c.fillStyle = shade(lo + r() * (hi - lo)); c.fillRect(r() * S, r() * S, sz, sz) } }
            when (kind) {
                "siding" -> { var y = 0; while (y < S) { c.fillStyle = shade(0.72); c.fillRect(0, y + 7, S, 1); c.fillStyle = shade(1.08); c.fillRect(0, y, S, 1); y += 8 }; speck(80, 0.9, 1.05) }
                "shingles" -> { var y = 0; while (y < S) { var x = if ((y / 8) % 2 == 1) 4 else 0; while (x < S) { c.fillStyle = shade(0.75 + r() * 0.35); c.fillRect(x, y, 7, 7); c.fillStyle = shade(0.55); c.fillRect(x, y + 7, 8, 1); x += 8 }; y += 8 } }
                "tiles" -> { var y = 0; while (y < S) { var x = 0; while (x < S) { c.fillStyle = shade(0.85 + r() * 0.25); c.beginPath(); c.ellipse(x + 4, y + 4, 4, 5, 0, 0, PI * 2); c.fill(); x += 8 }; c.fillStyle = shade(0.6); c.fillRect(0, y + 7, S, 1); y += 8 } }
                "stucco" -> { speck(900, 0.86, 1.1); speck(40, 0.75, 0.85, 2) }
                "cinder" -> { var y = 0; while (y < S) { var x = if ((y / 16) % 2 == 1) 16 else 0; while (x < S + 16) { c.fillStyle = shade(0.68); c.fillRect(x - 1, y, 1, 16); c.fillRect(x - 32, y + 15, 32, 1); x += 32 }; y += 16 }; speck(300, 0.85, 1.08) }
                "brick" -> { var y = 0; while (y < S) { var x = if ((y / 8) % 2 == 1) 8 else 0; while (x < S + 16) { c.fillStyle = shade(0.8 + r() * 0.35); c.fillRect(x - 16, y, 15, 7); x += 16 }; y += 8 }; c.fillStyle = "rgba(200,190,180,0.35)"; var yy = 7; while (yy < S) { c.fillRect(0, yy, S, 1); yy += 8 } }
                "wood" -> { var x = 0; while (x < S) { c.fillStyle = shade(0.82 + r() * 0.3); c.fillRect(x, 0, 15, S); c.fillStyle = shade(0.55); c.fillRect(x + 15, 0, 1, S); for (k in 0 until 6) { c.fillStyle = shade(0.7); c.fillRect(x + 2 + r() * 11, r() * S, 1, 8 + r() * 14) }; x += 16 } }
                "bark" -> { var x = 0; while (x < S) { c.fillStyle = shade(0.6 + r() * 0.6); c.fillRect(x, 0, 2, S); x += 3 }; for (i in 0 until 30) { c.fillStyle = shade(0.45); c.fillRect(r() * S, r() * S, 3, 1) } }
                "palmbark" -> { var y = 0; while (y < S) { c.fillStyle = shade(0.6); c.fillRect(0, y, S, 2); c.fillStyle = shade(1.15); c.fillRect(0, y + 2, S, 1); y += 6 }; speck(100, 0.8, 1.1) }
                "corrugated" -> { var x = 0; while (x < S) { c.fillStyle = shade(1.12); c.fillRect(x, 0, 1, S); c.fillStyle = shade(0.78); c.fillRect(x + 2, 0, 1, S); x += 4 }; speck(60, 0.85, 1.0) }
                "concrete" -> { speck(700, 0.88, 1.08); c.strokeStyle = shade(0.7); c.beginPath(); c.moveTo(0, 32); c.lineTo(64, 32); c.moveTo(32, 0); c.lineTo(32, 64); c.stroke() }
                "rock" -> { var y = 0.0; while (y < S) { c.fillStyle = shade(0.75 + r() * 0.45); c.fillRect(0, y, S, 6); y += 5 + r() * 6 }; speck(300, 0.7, 1.15) }
                "thatch" -> for (i in 0 until 220) { c.fillStyle = shade(0.65 + r() * 0.6); c.fillRect(r() * S, r() * S, 1, 6 + r() * 8) }
                "needles" -> for (i in 0 until 260) { c.fillStyle = shade(0.6 + r() * 0.7); val x = r() * S; val y = r() * S; c.fillRect(x, y, 4, 1); c.fillRect(x + 1, y + 1, 3, 1) }
                "leaves" -> for (i in 0 until 180) { c.fillStyle = shade(0.6 + r() * 0.7); c.beginPath(); c.ellipse(r() * S, r() * S, 2 + r() * 2, 1.5, r() * 3, 0, PI * 2); c.fill() }
                "frond" -> { c.fillStyle = shade(0.6); c.fillRect(0, 30, S, 4); var x = 0; while (x < S) { c.fillStyle = shade(0.85 + r() * 0.35); c.fillRect(x, 4 + r() * 6, 2, 24); c.fillRect(x, 34, 2, 24 - r() * 6); x += 3 } }
                "awning" -> { var x = 0; while (x < S) { c.fillStyle = "#f1f1ea"; c.fillRect(x + 8, 0, 8, S); x += 16 } }
                "hay" -> for (i in 0 until 300) { c.fillStyle = shade(0.7 + r() * 0.5); c.fillRect(r() * S, r() * S, 6, 1) }
                "garage" -> { var y = 0; while (y < S) { c.fillStyle = shade(0.75); c.fillRect(0, y + 11, S, 1); y += 12 }; var x = 0; while (x < S) { c.fillStyle = shade(0.88); c.fillRect(x, 0, 1, S); x += 16 } }
                else -> speck(300, 0.85, 1.1)
            }
        }.tex
        t.wrapRepeat = true
        TEXKIT[key] = t
        return t
    }
    private fun kitMat(kind: String, col: String, rx: Float = 1f, ry: Float = 1f) = PS1.material(color = 0xffffff, map = kitTex(kind, col, rx, ry))

    private var winPick = 0 // deterministic mix: most windows warm, some TV-blue, a third dark
    private fun windowPane(group: Object3D, w: Float, h: Float, x: Float, y: Float, z: Float, rotY: Float = 0f): Mesh {
        val frame = add(group, Geo.box(w + 0.08f, h + 0.08f, 0.03f), mat(0xf1f1ea), x, y, z); frame.rotation.y = rotY
        val mix = (winPick++ * 7) % 10 // 0-2 dark, 3 TV blue, the rest warm
        val pane = add(group, planeG(w, h), if (mix < 3) darkWinMat else (if (mix == 3) tvWinMat else litWinMat), x + sin(rotY) * 0.02f, y, z + cos(rotY) * 0.02f); pane.rotation.y = rotY
        return pane
    }
    // Simple low-poly deciduous tree with a textured, lumpy crown (bare in winter)
    private fun tree3D(group: Group, x: Float, z: Float, s: Float): Group {
        val t = sub(group, x, 0.32f, z); t.scale.set(s, s, s)
        add(t, Geo.cylinder(0.07f, 0.12f, 1.2f, 6), kitMat("bark", "#5b3d26", 1f, 2f), 0f, 0.6f, 0f)
        for (c in listOf(floatArrayOf(0f, 1.55f, 0f, 0.62f), floatArrayOf(0.35f, 1.35f, 0.15f, 0.45f), floatArrayOf(-0.3f, 1.4f, -0.1f, 0.48f), floatArrayOf(0.05f, 1.9f, 0.1f, 0.42f)))
            crowns += add(t, GeoCurves.icosahedron(c[3], 0), leafMats.getValue("green"), c[0], c[1], c[2])
        windTrees += t
        return t
    }
    private fun bush3D(group: Group, x: Float, z: Float, s: Float, col: String = "#3b6b33"): Mesh { val b = add(group, GeoCurves.icosahedron(0.3f * s, 0), kitMat("leaves", col), x, 0.32f + 0.2f * s, z); b.scale.y = 0.7f; return b }
    private fun pyramidRoof(r: Float, h: Float) = Geo.cylinder(0.01f, r, h, 4, 1).rotateY(P4)
    // Gable-roofed house with siding, shingle roof, windows that light up, a door, porch light and a chimney
    private fun house3D(group: Group, x: Float, z: Float, w: Float = 2.4f, h: Float = 1.3f, d: Float = 1.7f, wall: String = "#d6c7a1", roof: String = "#5b3a32",
                        rotY: Float = 0f, garage: Boolean = false, flat: Boolean = false, roofKind: String = "shingles", wallKind: String = "siding"): Group {
        val g = sub(group, x, 0.32f, z, rotY)
        add(g, boxG(w, h, d), kitMat(wallKind, wall, w / 1.2f, h / 1.2f), 0f, h / 2, 0f)
        if (flat) add(g, boxG(w + 0.1f, 0.14f, d + 0.1f), kitMat("stucco", wall), 0f, h + 0.07f, 0f)
        else {
            val rf = add(g, pyramidRoof(1f, 1f), kitMat(roofKind, roof, 3f, 2f), 0f, h + 0.42f, 0f); rf.scale.set((w + 0.3f) / 1.414f, 0.85f, (d + 0.3f) / 1.414f)
            add(g, Geo.box(0.22f, 0.6f, 0.22f), kitMat("brick", "#8a4a3a"), w * 0.28f, h + 0.6f, -d * 0.15f)
        }
        val front = d / 2 + 0.01f
        floatArrayOf(-w * 0.3f, w * 0.3f).forEachIndexed { i, wx -> if (!(garage && i == 1)) windowPane(g, 0.42f, 0.36f, wx, h * 0.6f, front) }
        val doorX = if (garage) -w * 0.05f else 0f
        add(g, Geo.plane(0.34f, 0.66f), kitMat("wood", "#6b3f2a", 0.5f, 1f), doorX, 0.33f, front)
        if (garage) add(g, planeG(w * 0.38f, 0.7f), kitMat("garage", "#e8e6df"), w * 0.28f, 0.35f, front)
        val pl = PS1.material(color = 0xfff3c4, emissive = 0xffc977, emissiveIntensity = 0f); nightGlowMats += pl
        add(g, Geo.box(0.06f, 0.08f, 0.04f), pl, doorX + 0.26f, 0.6f, front + 0.02f)
        add(g, Geo.box(0.6f, 0.08f, 0.3f), kitMat("concrete", "#b8b4ac"), doorX, 0.04f, front + 0.15f)
        return g
    }
    /** A wireframe icosahedron (the tumbleweed) as thin merged struts: the engine has no line mode. */
    private fun wireIcosahedron(radius: Float, detail: Int, thick: Float): Geometry {
        val src = GeoCurves.icosahedron(radius, detail)
        val p = src.positions
        val seen = HashSet<String>()
        val parts = ArrayList<Geometry>()
        fun key(i: Int) = "${(p[i * 3] * 1000).roundToInt()},${(p[i * 3 + 1] * 1000).roundToInt()},${(p[i * 3 + 2] * 1000).roundToInt()}"
        val n = src.indices?.size ?: src.vertexCount
        var t = 0
        while (t + 2 < n) {
            val tri = IntArray(3) { src.indices?.get(t + it) ?: (t + it) }
            for (e in 0 until 3) {
                val a = tri[e]; val b = tri[(e + 1) % 3]
                val ka = key(a); val kb = key(b)
                val k = if (ka < kb) "$ka|$kb" else "$kb|$ka"
                if (!seen.add(k)) continue
                val ax = p[a * 3]; val ay = p[a * 3 + 1]; val az = p[a * 3 + 2]
                val dx = p[b * 3] - ax; val dy = p[b * 3 + 1] - ay; val dz = p[b * 3 + 2] - az
                val len = sqrt(dx * dx + dy * dy + dz * dz)
                // three's setFromUnitVectors((0, 1, 0), edge direction)
                val tx = dx / len; val ty = dy / len; val tz = dz / len
                val q = if (ty + 1 < 1e-6f) com.ps1bedroom.room.engine.Quat(1f, 0f, 0f, 0f) else {
                    val qx = tz; val qz = -tx; val qw = ty + 1; val l = sqrt(qx * qx + qz * qz + qw * qw)
                    com.ps1bedroom.room.engine.Quat(qx / l, 0f, qz / l, qw / l)
                }
                val m = com.ps1bedroom.room.engine.Mat4().compose(com.ps1bedroom.room.engine.Vec3(ax + dx / 2, ay + dy / 2, az + dz / 2), q, com.ps1bedroom.room.engine.Vec3(1f, 1f, 1f))
                parts += Geo.box(thick, len, thick).transformed(m)
            }
            t += 3
        }
        return GeoCurves.merge(parts)
    }

    private val suburbPlaceholders = ArrayList<Group>()
    private val houseFence = ArrayList<Mesh>()
    private val trailerBodies = ArrayList<Mesh>()
    private val trailerSkirts = ArrayList<Mesh>()
    private lateinit var arizonaWall: Mesh
    private val desertRocks = ArrayList<Mesh>()
    private lateinit var boardwalk: Mesh
    private val mountainRocks = ArrayList<Mesh>()
    private val countryHay = ArrayList<Mesh>()
    private val countryWood = ArrayList<Mesh>()
    private lateinit var neonShops: Mesh
    private val SPRITE_TEX = HashMap<String, Texture>()

    init {
        PS1.syncGrime(358) // HTML 14781
        for (k in OUTSIDE_ENVS.keys) if (k != "city") { val g = Group(); g.visible = false; scene.add(g); G[k] = g }
        // Far below (only seen from the skyscraper): the city from above. Drawn without the PS1 wobble (a huge
        // plane seen at a steep angle swims badly with it): streets, blocks of rooftops, parks, moving-car dots
        val abyssTex = createCanvasTex(256, 256) { ctx, w, h ->
            val r = makeRng(9)
            ctx.fillStyle = "#2a2d34"; ctx.fillRect(0, 0, w, h)
            var by = 0
            while (by < h) {
                var bx = 0
                while (bx < w) {
                    if (r() < 0.12) { ctx.fillStyle = "#2f5a2c"; ctx.fillRect(bx + 4, by + 4, 24, 24); for (k in 0 until 6) { ctx.fillStyle = "#3f7a35"; ctx.beginPath(); ctx.arc(bx + 7 + r() * 18, by + 7 + r() * 18, 2 + r() * 2, 0, 7); ctx.fill() }; bx += 32; continue }
                    for (k in 0 until 4) {
                        val x = bx + 4 + (k % 2) * 12; val y = by + 4 + (k / 2) * 12; val v = (70 + r() * 70).toInt()
                        ctx.fillStyle = "rgb($v,${v + 4},${v + 10})"; ctx.fillRect(x, y, 11, 11)
                        ctx.fillStyle = "rgba(0,0,0,0.35)"; ctx.fillRect(x + 11, y + 2, 1, 10); ctx.fillRect(x + 2, y + 11, 10, 1)
                        if (r() < 0.4) { ctx.fillStyle = "#5b6170"; ctx.fillRect(x + 3, y + 3, 3, 3) }
                    }
                    bx += 32
                }
                by += 32
            }
            ctx.fillStyle = "#d8c26a"
            run { var i = 0; while (i < w) { var j = 0; while (j < h) { ctx.fillRect(i + 1, j, 1, 3); ctx.fillRect(j, i + 1, 3, 1); j += 6 }; i += 32 } }
            for (i in 0 until 200) { ctx.fillStyle = if (r() > 0.5) "#fff3c4" else "#ff5a4a"; val lane = floor(r() * 8) * 32; if (r() > 0.5) ctx.fillRect(lane + 1, r() * h, 1, 1) else ctx.fillRect(r() * w, lane + 1, 1, 1) }
        }.tex
        abyssTex.nearest = false
        abyss = Mesh(Geo.plane(26f, 14f, 12, 8), PS1.basic(color = 0x8a8f9c, map = abyssTex))
        abyss.rotation.x = -P2; abyss.position.set(0.8f, -2.38f, -9.6f); abyss.visible = false
        sky.skyBoxGroup.add(abyss)

        pineMat = mat(0x1f4a2c)
        pineSnowMat = mat(0xeef3fa)
        barkMat = mat(0x4a3020)
        palmLeafMat = PS1.material(color = 0x2f7a3a, side = DOUBLE_SIDE)
        // A lit-window texture: dark glass by day, warm light at night (a shared material per look)
        val windowLitTex = createCanvasTex(32, 32) { c, _, _ ->
            c.fillStyle = "#ffd88a"; c.fillRect(0, 0, 32, 32)
            c.fillStyle = "#c99652"; c.fillRect(15, 0, 2, 32); c.fillRect(0, 15, 32, 2)
            c.fillStyle = "rgba(255,255,255,0.35)"; c.fillRect(3, 3, 8, 4)
        }.tex
        litWinMat = PS1.material(color = 0x334155, map = windowLitTex, emissive = 0xffb860, emissiveMap = windowLitTex, emissiveIntensity = 0f)
        tvWinMat = PS1.material(color = 0x334155, map = windowLitTex, emissive = 0x6fa8ff, emissiveMap = windowLitTex, emissiveIntensity = 0f)
        darkWinMat = PS1.material(color = 0x2a3340, map = windowLitTex)
        nightGlowMats += tvWinMat
        nightGlowMats += litWinMat
        leafMats["green"] = kitMat("leaves", "#3f7a35"); leafMats["fall"] = kitMat("leaves", "#c2702a"); leafMats["spring"] = kitMat("leaves", "#5fa04a")
        buildFirstPass()
        buildDetailPass()
        buildSprites()
        buildWaterExtras()
        env.envHooks += { updateSpriteSeason() }
        env.envHooks += { updateOutsideEnvLook() }
    }

    /** Every place's group: they move or switch, so none of it takes part in static batching. */
    fun dynamicRoots(): List<Object3D> = G.values.toList()


    private fun buildFirstPass() {
        // Skyscraper: no ground (you're 40 floors up); a neighbouring tower with a big rooftop billboard right at eye level
        run {
            val g = G.getValue("skyscraper")
            val (_, t2, t3) = exterior.buildingTextures
            val facade = PS1.material(map = t2, emissive = 0xffffff, emissiveMap = t2, emissiveIntensity = 0.5f)
            val tower = box(g, 3.2f, 6f, 3f, 0, 4.6f, -1.0f, -10.5f, facade)
            box(g, 3.3f, 0.12f, 3.1f, 0x334155, 4.6f, 2.05f, -10.5f)
            val tower2 = box(g, 2.6f, 7f, 2.6f, 0, -3.2f, -0.4f, -13.5f, PS1.material(map = t3, emissive = 0xffffff, emissiveMap = t3, emissiveIntensity = 0.5f))
            facades += tower; facades += tower2
            val adObj = createCanvasTex(128, 48) { _, _, _ -> }
            val adMat = PS1.material(map = adObj.tex, emissive = 0xffffff, emissiveMap = adObj.tex, emissiveIntensity = 0.7f)
            val sideMats = List(4) { mat(0x1f2937) }
            val last = mat(0x1f2937)
            val board = Mesh(boxG(2.6f, 1.0f, 0.08f), listOf(sideMats[0], sideMats[1], sideMats[2], sideMats[3], adMat, last))
            board.position.set(4.3f, 2.75f, -9.0f); board.rotation.y = -0.35f; g.add(board)
            for (dx in floatArrayOf(-0.9f, 0.9f)) cyl(g, 0.03f, 0.03f, 0.7f, 0x475569, 4.3f + dx * cos(0.35f), 2.2f, -9.0f + dx * sin(0.35f), 4)
            val beacon = PS1.material(color = 0xff2222, emissive = 0xff0000, emissiveIntensity = 1f)
            box(g, 0.08f, 0.08f, 0.08f, 0, 4.6f, 2.2f, -11.9f, beacon)
            skyAd = SkyAd(adObj.ctx, adObj.tex, adMat, 0L, beacon)
        }
        // Neon City: wet street, a tall vertical sign and shop signs on the building next door
        run {
            val g = G.getValue("neon")
            road(g, -6.6f, 2.4f, 0x15171d)
            box(g, 1.6f, 5.5f, 4.0f, 0, 4.4f, 2.9f, -7.0f, kitMat("brick", "#5a4a44", 3f, 4f)) // the building next door
            for (fl in 1 until 4) for (z in floatArrayOf(-6.5f, -7.6f, -8.6f)) windowPane(g, 0.42f, 0.4f, 3.59f, 1.5f + fl * 1.05f, z, -P2) // clear of the HOTEL and RAMEN signs
            neonSign(g, "HOTEL", "#ff3ea5", 3.55f, 3.0f, -5.6f, 0.36f, 1.6f, true)
            neonSign(g, "RAMEN", "#ffe14d", 3.58f, 1.25f, -6.6f, 0.06f, 0.45f, false).rotation.y = P2
            neonSign(g, "KARAOKE", "#2ef2ff", -1.4f, 2.05f, -9.7f, 1.8f, 0.45f, false)
            neonSign(g, "ARCADE", "#7cff6b", 1.6f, 1.15f, -9.7f, 1.4f, 0.4f, false)
            neonShops = box(g, 9f, 1.5f, 0.5f, 0x221f2b, 0.4f, 1.05f, -10.0f) // a low row of shops, so the neon towers show above it
            lampPost(g, -0.8f, -5.4f, nightGlowMats)
        }
        // Neighborhood: the street, a fire hydrant, a streetlamp, cars parked along the curb
        run {
            val g = G.getValue("neighborhood")
            road(g, -6.4f, 2.4f)
            box(g, 30f, 0.08f, 0.9f, 0x8e8e8a, 0.8f, 0.36f, -4.9f)
            cyl(g, 0.09f, 0.1f, 0.42f, 0xd32f2f, 1.9f, 0.6f, -4.8f); cyl(g, 0.11f, 0.11f, 0.06f, 0xd32f2f, 1.9f, 0.83f, -4.8f)
            lampPost(g, -1.2f, -5.0f, nightGlowMats)
            parkedCar(g, "sedan", 0.4f, -5.85f); parkedCar(g, "van", 3.2f, -5.85f); parkedCar(g, "hatch", -2.6f, -7.0f, PF)
        }
        // Suburbs: picket fence, mailbox, the houses across the street
        suburbWindowMat = PS1.material(color = 0x1f2937, emissive = 0xffc977, emissiveIntensity = 0f)
        run {
            val g = G.getValue("suburbs")
            val white = mat(0xf1f1ea)
            for (i in 0 until 53) box(g, 0.08f, 0.5f, 0.03f, 0, -11.0f + i * 0.45f, 0.57f, -4.6f, white)
            box(g, 24f, 0.05f, 0.03f, 0, 0.8f, 0.68f, -4.6f, white)
            box(g, 0.2f, 0.16f, 0.3f, 0x1d4ed8, 2.7f, 1.0f, -4.9f); box(g, 0.05f, 0.7f, 0.05f, 0, 2.7f, 0.65f, -4.9f, white)
            road(g, -6.4f, 2.2f)
            parkedCar(g, "sedan", -1.6f, -7.3f, PF)
            for ((x, col) in listOf(-2.2f to 0xd6c7a1, 1.0f to 0x9fb7c9, 4.2f to 0xc99a9a)) {
                val h = sub(g, x, 0.32f, -9.4f)
                box(h, 2.2f, 1.3f, 1.6f, col, 0f, 0.65f, 0f)
                val roof = add(h, Geo.cone(1.75f, 0.9f, 4), mat(0x5b3a32), 0f, 1.75f, 0f); roof.rotation.y = P4; roof.scale.set(1f, 1f, 0.75f)
                for (wx in floatArrayOf(-0.6f, 0.6f)) add(h, Geo.plane(0.4f, 0.35f), suburbWindowMat, wx, 0.8f, 0.81f)
                add(h, Geo.plane(0.35f, 0.65f), mat(0x4a2f22), 0f, 0.33f, 0.81f)
                suburbPlaceholders += h
            }
        }
        // Backyard: privacy fence, swing set, trampoline, shed, grill, and wind chimes by the window
        run {
            val g = G.getValue("house")
            val wood = mat(0x9a7552)
            for (i in 0 until 110) houseFence += box(g, 0.2f, 1.5f, 0.04f, 0, -11.1f + i * 0.22f, 1.07f, -10.5f, wood) // runs wall to wall
            box(g, 24f, 0.08f, 0.06f, 0, 0.8f, 1.5f, -10.45f, wood)
            val metal = mat(0x3b82f6)
            for (side in intArrayOf(-1, 1)) for (dz in floatArrayOf(-0.35f, 0.35f)) { val leg = cyl(g, 0.025f, 0.025f, 1.7f, 0, -0.6f + side * 1.0f, 1.08f, -6.8f + dz, 5, metal); leg.rotation.x = if (dz > 0) -0.2f else 0.2f }
            cyl(g, 0.03f, 0.03f, 2.1f, 0, -0.6f, 1.9f, -6.8f, 5, metal).rotation.z = P2
            for (sx in floatArrayOf(-1.0f, -0.2f)) {
                val pivot = sub(g, sx, 1.9f, -6.8f)
                for (dx in floatArrayOf(-0.12f, 0.12f)) box(pivot, 0.01f, 1.2f, 0.01f, 0x111111, dx, -0.6f, 0f)
                box(pivot, 0.32f, 0.03f, 0.14f, 0xdc2626, 0f, -1.2f, 0f)
                swings += pivot
            }
            cyl(g, 0.9f, 0.9f, 0.05f, 0x111827, 2.6f, 0.82f, -8.0f, 16); cyl(g, 0.95f, 0.95f, 0.04f, 0x16a34a, 2.6f, 0.84f, -8.0f, 16)
            for (i in 0 until 4) { val a = i * PF / 2; cyl(g, 0.02f, 0.02f, 0.5f, 0x6b7280, 2.6f + cos(a) * 0.8f, 0.57f, -8.0f + sin(a) * 0.8f, 4) }
            val shed = sub(g, -3.0f, 0.32f, -9.2f)
            box(shed, 1.6f, 1.3f, 1.2f, 0xb45309, 0f, 0.65f, 0f)
            add(shed, Geo.cone(1.3f, 0.5f, 4), mat(0x374151), 0f, 1.55f, 0f).rotation.y = P4
            box(g, 0.5f, 0.3f, 0.35f, 0x111111, 1.6f, 0.95f, -5.4f); cyl(g, 0.02f, 0.02f, 0.6f, 0x333333, 1.6f, 0.6f, -5.4f, 4)
            for ((x, z) in listOf(-2.2f to -5.6f, -1.6f to -5.6f)) box(g, 0.5f, 0.12f, 1.4f, 0x5b3a1a, x, 0.38f, z)
            // Wind chimes hanging just outside the window
            val cg = sub(g, 2.0f, 3.35f, -3.35f)
            cyl(cg, 0.12f, 0.12f, 0.02f, 0x7c5a3a, 0f, 0f, 0f, 8)
            for (i in 0 until 5) { val a = i / 5f * PF * 2; chimeRods += cyl(cg, 0.01f, 0.01f, 0.28f + i * 0.04f, 0xcbd5e1, cos(a) * 0.09f, -0.2f - i * 0.02f, sin(a) * 0.09f, 4) }
        }
        // Trailer park: mobile homes, chain-link fence, satellite dish, clothesline, string lights, a pink flamingo
        run {
            val g = G.getValue("trailer")
            fun trailer(x: Float, z: Float, col: Int, stripe: Int, rotY: Float): Group {
                val t = sub(g, x, 0.32f, z, rotY)
                trailerBodies += box(t, 4.2f, 1.15f, 1.4f, col, 0f, 0.85f, 0f)
                box(t, 4.2f, 0.12f, 1.42f, stripe, 0f, 0.75f, 0f)
                box(t, 4.3f, 0.06f, 1.5f, 0x9ca3af, 0f, 1.45f, 0f)
                trailerSkirts += box(t, 4.1f, 0.3f, 1.3f, 0x6b6560, 0f, 0.15f, 0f)
                val winMat = PS1.material(color = 0x1f2937, emissive = 0xffc977, emissiveIntensity = 0f); nightGlowMats += winMat
                for (wx in floatArrayOf(-1.2f, 0.6f)) box(t, 0.5f, 0.35f, 0.02f, 0, wx, 1.0f, 0.71f, winMat)
                box(t, 0.35f, 0.25f, 0.25f, 0xd1d5db, 1.4f, 0.95f, 0.82f) // window AC
                box(t, 0.6f, 0.3f, 0.4f, 0x8a6a4a, -0.3f, 0.15f, 0.9f) // steps
                return t
            }
            trailer(-1.4f, -6.2f, 0xe8e6df, 0x0f766e, 0f)
            trailer(3.6f, -9.5f, 0xe9dcc0, 0xb45309, 0.1f)
            trailer(-2.8f, -12.0f, 0xd9e4e8, 0x1d4ed8, 0f)
            cyl(g, 0.02f, 0.02f, 0.35f, 0x9ca3af, -0.4f, 1.95f, -6.0f, 4) // stands on the roof (top at 1.77)
            val dish = add(g, Geo.sphere(0.3f, 10, 4, 0f, PF * 2, 0f, PF / 3.2f), PS1.material(color = 0xe5e7eb, side = DOUBLE_SIDE), -0.35f, 2.15f, -5.92f)
            dish.rotation.set(-1.9f, 0f, 0.5f) // bowl faces up and out toward the sky
            val chain = mat(0x9ca3af)
            for (i in 0 until 25) cyl(g, 0.02f, 0.02f, 0.9f, 0, -11.0f + i * 1.0f, 0.77f, -4.5f, 4, chain)
            box(g, 24f, 0.02f, 0.02f, 0, 0.8f, 1.2f, -4.5f, chain)
            add(g, planeG(24f, 0.85f), PS1.basic(color = 0xb8bec6, transparent = true, opacity = 0.22f, depthWrite = false), 0.8f, 0.77f, -4.5f)
            // Clothesline with laundry
            for (x in floatArrayOf(1.2f, 3.4f)) cyl(g, 0.02f, 0.02f, 1.6f, 0x6b7280, x, 1.1f, -7.0f, 4)
            box(g, 2.2f, 0.01f, 0.01f, 0x111111, 2.3f, 1.85f, -7.0f)
            for ((x, col) in listOf(1.6f to 0xf87171, 2.1f to 0x60a5fa, 2.6f to 0xfacc15, 3.0f to 0xf1f5f9)) laundryItems += box(g, 0.3f, 0.38f, 0.02f, col, x, 1.65f, -7.0f)
            // String lights between the trailers
            for (i in 0 until 12) {
                val m = PS1.material(color = 0xfff3c4, emissive = intArrayOf(0xffd27d, 0xff7aa8, 0x7ad7ff)[i % 3], emissiveIntensity = 0f); nightGlowMats += m
                box(g, 0.06f, 0.06f, 0.06f, 0, -3.4f + i * 0.55f, 2.0f - sin(i / 11f * PF) * 0.25f, -5.4f, m)
            }
            cyl(g, 0.22f, 0.22f, 0.7f, 0xf8fafc, 2.0f, 0.6f, -5.7f, 8).rotation.z = P2 // propane tank
            val fl = sub(g, 0.9f, 0.32f, -5.0f)
            box(fl, 0.14f, 0.12f, 0.22f, 0xff6fb5, 0f, 0.55f, 0f); cyl(fl, 0.01f, 0.01f, 0.5f, 0x111111, 0f, 0.25f, 0f, 3); box(fl, 0.03f, 0.18f, 0.03f, 0xff6fb5, 0f, 0.7f, 0.1f); box(fl, 0.05f, 0.05f, 0.08f, 0xff6fb5, 0f, 0.8f, 0.14f)
            parkedCar(g, "truck", 1.6f, -10.8f, 0f)
        }
        // Arizona: block wall with a cap, gravel, a saguaro, tall fan palms, a citrus tree, a basketball hoop
        run {
            val g = G.getValue("arizona")
            val block = mat(0xc8ab86)
            arizonaWall = box(g, 24f, 0.95f, 0.2f, 0, 0.8f, 0.8f, -7.6f, block); box(g, 24.1f, 0.08f, 0.26f, 0xb89a74, 0.8f, 1.3f, -7.6f) // wall to wall
            cactus3D(g, -1.4f, -5.6f, 1.15f); cactus3D(g, 3.2f, -6.4f, 0.8f)
            palm3D(g, 0.9f, -9.2f, 1.0f, 0.03f, true); palm3D(g, 3.9f, -10.4f, 1.05f, -0.04f, true); palm3D(g, -2.6f, -11f, 0.95f, 0.02f, true)
            val citrus = sub(g, 2.0f, 0.32f, -5.5f)
            cyl(citrus, 0.06f, 0.08f, 0.6f, 0, 0f, 0.3f, 0f, 5, barkMat)
            add(citrus, Geo.sphere(0.55f, 8, 6), mat(0x2f5d2a), 0f, 0.95f, 0f)
            val orange = mat(0xf59e0b)
            for (i in 0 until 9) { val a = i * 2.4f; add(citrus, Geo.sphere(0.06f, 5, 4), orange, cos(a) * 0.5f, 0.8f + (i % 3) * 0.15f, sin(a) * 0.5f) }
            cyl(g, 0.04f, 0.04f, 2.6f, 0x374151, -0.4f, 1.62f, -6.8f, 5); box(g, 0.7f, 0.45f, 0.04f, 0xf3f4f6, -0.4f, 2.8f, -6.75f); cyl(g, 0.16f, 0.16f, 0.02f, 0xea580c, -0.4f, 2.62f, -6.55f, 10)
            rocks(g, listOf(floatArrayOf(1.0f, -6.0f, 0.18f), floatArrayOf(-2.2f, -5.4f, 0.22f), floatArrayOf(2.8f, -5.0f, 0.15f)), 0x9a7b5f)
        }
        // Desert canyon: saguaros, red rocks, a tumbleweed
        run {
            val g = G.getValue("desert")
            cactus3D(g, -1.3f, -5.4f, 1.0f); cactus3D(g, 3.3f, -6.8f, 1.15f); cactus3D(g, 1.0f, -10.5f, 0.9f); cactus3D(g, -2.4f, -9.0f, 0.8f)
            desertRocks += rocks(g, listOf(floatArrayOf(2.0f, -5.6f, 0.4f), floatArrayOf(-0.3f, -7.6f, 0.55f), floatArrayOf(4.0f, -9.0f, 0.7f)), 0xa0674a)
            tumbleweed = add(g, wireIcosahedron(0.25f, 1, 0.012f), PS1.basic(color = 0x9b7b4f), -6f, 0.57f, -6.2f)
        }
        // Tropical island: palms, tiki torches, a hammock, an outrigger canoe, the lagoon
        islandSea = waterPlane(G.getValue("island"), -8.6f, 7.5f, "#2bb3b8", false)
        run {
            val g = G.getValue("island")
            palm3D(g, -1.4f, -5.2f, 1.05f, 0.18f); palm3D(g, 3.4f, -6.4f, 0.95f, -0.22f); palm3D(g, 1.6f, -7.6f, 0.8f, 0.1f)
            for ((x, z) in listOf(0.2f to -5.0f, 2.4f to -5.4f)) {
                cyl(g, 0.03f, 0.04f, 1.4f, 0x7c5a3a, x, 1.02f, z, 5)
                val flame = PS1.material(color = 0xffb347, emissive = 0xff7a1a, emissiveIntensity = 0f)
                tikiFlames += box(g, 0.08f, 0.16f, 0.08f, 0, x, 1.8f, z, flame)
            }
            box(g, 2.5f, 0.03f, 0.5f, 0xf2e8c9, 1.0f, 0.95f, -5.8f).rotation.z = 0.08f
            box(g, 1.8f, 0.14f, 0.3f, 0x8b5a2b, -2.0f, 0.4f, -7.2f).rotation.y = 0.3f
        }
        // Beach boardwalk: palms, a lifeguard stand, an umbrella, the sea rolling in
        beachSea = waterPlane(G.getValue("beach"), -9.0f, 7f, "#2a6f9e", false)
        foam = add(G.getValue("beach"), planeG(30f, 0.35f), PS1.basic(color = 0xffffff, transparent = true, opacity = 0.55f, depthWrite = false), 0.8f, 0.35f, -9.0f)
        foam.rotation.x = -P2
        run {
            val g = G.getValue("beach")
            palm3D(g, -1.4f, -5.2f, 1.0f, 0.15f); palm3D(g, 3.6f, -6.8f, 0.9f, -0.2f)
            val s = sub(g, 1.8f, 0.32f, -7.2f)
            val wood = mat(0xe8e4dc)
            for ((x, z) in listOf(-0.3f to -0.3f, 0.3f to -0.3f, -0.3f to 0.3f, 0.3f to 0.3f)) box(s, 0.06f, 1.2f, 0.06f, 0, x, 0.6f, z, wood)
            box(s, 0.8f, 0.06f, 0.8f, 0, 0f, 1.2f, 0f, wood); box(s, 0.7f, 0.5f, 0.6f, 0xdc2626, 0f, 1.5f, 0f)
            add(g, Geo.cone(0.7f, 0.3f, 8), mat(0xfacc15), -0.8f, 1.6f, -5.9f)
            cyl(g, 0.02f, 0.02f, 1.2f, 0, -0.8f, 0.92f, -5.9f, 4, wood)
            boardwalk = box(g, 30f, 0.1f, 1.0f, 0x8b6b4a, 0.8f, 0.4f, -4.4f) // the boardwalk itself
        }
        // Lakeside: still water, dock, rowboat, pines along the shore
        lakeWater = waterPlane(G.getValue("lake"), -7.6f, 5.2f, "#2c5466", true) // leaves a far shore for the cabin and pines
        run {
            val g = G.getValue("lake")
            val plank = mat(0x7a5a3a)
            box(g, 0.9f, 0.06f, 3.2f, 0, 1.4f, 0.5f, -8.9f, plank)
            for (i in 0 until 4) for (dx in floatArrayOf(-0.4f, 0.4f)) cyl(g, 0.04f, 0.04f, 0.5f, 0, 1.4f + dx, 0.32f, -7.5f - i, 5, plank)
            box(g, 0.5f, 0.18f, 1.3f, 0xb91c1c, 2.15f, 0.38f, -9.4f).rotation.y = 0.2f
            for (p in listOf(floatArrayOf(-1.6f, -4.6f, 1.1f), floatArrayOf(3.5f, -5.2f, 1.2f), floatArrayOf(-2.4f, -6.5f, 0.9f))) pine3D(g, p[0], p[1], p[2])
        }
        // Forest and mountains
        for (p in listOf(floatArrayOf(-1.4f, -4.5f, 1.25f), floatArrayOf(3.2f, -4.8f, 1.3f), floatArrayOf(-0.2f, -6.4f, 1.0f), floatArrayOf(1.9f, -7.2f, 1.15f), floatArrayOf(-2.6f, -8.0f, 1.2f),
                floatArrayOf(3.9f, -8.6f, 1.0f), floatArrayOf(0.8f, -9.8f, 1.3f), floatArrayOf(-1.2f, -11.0f, 1.1f), floatArrayOf(2.8f, -11.5f, 1.2f))) pine3D(G.getValue("forest"), p[0], p[1], p[2])
        box(G.getValue("forest"), 1.4f, 0.22f, 0.22f, 0x5b3a1a, 0.9f, 0.43f, -5.6f).rotation.y = 0.4f // fallen log
        for (p in listOf(floatArrayOf(-1.6f, -4.8f, 1.0f), floatArrayOf(3.4f, -5.2f, 1.1f), floatArrayOf(0.9f, -8.5f, 0.8f), floatArrayOf(-0.8f, -10.5f, 1.0f))) pine3D(G.getValue("mountains"), p[0], p[1], p[2])
        mountainRocks += rocks(G.getValue("mountains"), listOf(floatArrayOf(2.0f, -6.2f, 0.5f), floatArrayOf(-0.6f, -7.4f, 0.35f), floatArrayOf(3.6f, -9.6f, 0.6f)), 0x6b7280)
        // Countryside: fence, hay bales, a scarecrow
        run {
            val g = G.getValue("countryside")
            val wood = mat(0x8a6a4a)
            for (i in 0 until 32) countryWood += box(g, 0.07f, 0.6f, 0.07f, 0, -11.0f + i * 0.75f, 0.62f, -6.0f, wood)
            for (y in floatArrayOf(0.45f, 0.7f)) countryWood += box(g, 24f, 0.05f, 0.04f, 0, 0.8f, y, -6.0f, wood)
            val hay = mat(0xd4b04a)
            for ((x, z) in listOf(1.6f to -8.0f, 2.6f to -8.6f, -0.8f to -9.5f)) { val hb = cyl(g, 0.45f, 0.45f, 0.6f, 0, x, 0.77f, z, 10, hay); hb.rotation.z = P2; countryHay += hb }
            cyl(g, 0.03f, 0.03f, 1.6f, 0x6b4a2a, -1.8f, 1.1f, -7.6f, 4); box(g, 0.9f, 0.04f, 0.04f, 0x6b4a2a, -1.8f, 1.55f, -7.6f)
            box(g, 0.32f, 0.45f, 0.16f, 0x2563eb, -1.8f, 1.4f, -7.6f); box(g, 0.2f, 0.2f, 0.18f, 0xf5deb3, -1.8f, 1.8f, -7.6f); box(g, 0.34f, 0.05f, 0.3f, 0xa16207, -1.8f, 1.93f, -7.6f)
        }
    }

    private fun buildDetailPass() {
        // Suburbs: the houses across the street get siding, shingles, garages and front yards with trees and bushes
        run {
            val g = G.getValue("suburbs")
            for (h in suburbPlaceholders) h.visible = false // retire the plain placeholder houses
            for ((x, wall, roof, garage) in listOf(listOf(-2.6f, "#d6c7a1", "#5b3a32", false), listOf(0.6f, "#9fb7c9", "#3f4650", true), listOf(3.9f, "#c99a9a", "#4a3a32", false), listOf(-5.6f, "#cfd8b8", "#5b3a32", true)))
                house3D(g, x as Float, -9.6f, wall = wall as String, roof = roof as String, garage = garage as Boolean)
            add(g, Geo.plane(0.9f, 2.0f), kitMat("concrete", "#a8a49c", 1f, 2f), 1.4f, 0.34f, -7.9f).rotation.x = -P2
            add(g, planeG(30f, 0.6f), kitMat("concrete", "#b0aca4", 30f, 1f), 0.8f, 0.338f, -7.6f).rotation.x = -P2
            tree3D(g, -1.0f, -8.3f, 1.1f); tree3D(g, 2.6f, -8.6f, 0.95f); tree3D(g, -3.6f, -5.6f, 1.2f)
            for ((x, z) in listOf(-2.0f to -8.6f, -3.2f to -8.6f, 3.2f to -8.7f, 0.0f to -8.7f)) bush3D(g, x, z, 1f)
            lampPost(g, 3.4f, -5.2f, nightGlowMats)
        }
        // Neighborhood: a real brownstone row across the street, with stoops, fire escapes and the corner store
        run {
            val g = G.getValue("neighborhood")
            add(g, planeG(30f, 1.0f), kitMat("concrete", "#9c9890", 30f, 1f), 0.8f, 0.345f, -8.1f).rotation.x = -P2
            val bricks = arrayOf("#8f4a3a", "#7a4636", "#a0624a", "#6f5444")
            for (i in 0 until 5) {
                val x = -5.2f + i * 2.25f; val h = 3.4f + (i % 2) * 0.7f
                add(g, boxG(2.2f, h, 2f), kitMat("brick", bricks[i % 4], 3f, h / 0.7f), x, 0.32f + h / 2, -9.7f)
                add(g, boxG(2.3f, 0.14f, 2.1f), mat(0x3b3430), x, 0.32f + h + 0.07f, -9.7f)
                for (fl in 0 until 3) { var wx = -0.6f; while (wx <= 0.6f + 1e-4f) { windowPane(g, 0.32f, 0.46f, x + wx, 1.4f + fl * 0.95f, -8.69f); wx += 0.6f } }
                // stoop
                for (s in 0 until 3) add(g, Geo.box(0.7f, 0.12f, 0.3f), kitMat("concrete", "#8d8780"), x - 0.5f, 0.38f + s * 0.12f, -8.45f + (2 - s) * 0.12f)
                // fire escape
                if (i % 2 == 0) for (fl in 1 until 3) {
                    add(g, Geo.box(0.9f, 0.04f, 0.35f), mat(0x1f2329), x + 0.55f, 1.15f + fl * 0.95f, -8.5f)
                    add(g, Geo.box(0.9f, 0.25f, 0.02f), mat(0x1f2329), x + 0.55f, 1.28f + fl * 0.95f, -8.33f)
                }
            }
            // corner store at the end of the row
            add(g, boxG(2.4f, 2.2f, 2f), kitMat("brick", "#c8b48a", 3f, 3f), 6.3f, 1.42f, -9.7f)
            add(g, boxG(2.4f, 0.05f, 0.6f), kitMat("awning", "#c0392b", 2f, 1f), 6.3f, 1.6f, -8.45f).rotation.x = 0.3f
            add(g, Geo.plane(1.6f, 0.7f), litWinMat, 6.3f, 1.0f, -8.69f)
            neonSign(g, "OPEN", "#ff3b6b", 5.7f, 1.1f, -8.66f, 0.5f, 0.2f, false)
            neonSign(g, "BODEGA", "#ffe14d", 6.3f, 2.2f, -8.68f, 1.4f, 0.32f, false)
            for ((x, z) in listOf(-2.4f to -7.7f, 2.1f to -7.7f)) tree3D(g, x, z, 0.8f)
            add(g, Geo.cylinder(0.16f, 0.14f, 0.45f, 8), mat(0x2f5d3a), -0.6f, 0.55f, -4.75f)
        }
        // Backyard: textured fence, grass patch, a patio with chairs, a kiddie pool and a gnome
        run {
            val g = G.getValue("house")
            val fenceMat = kitMat("wood", "#9a7552", 0.25f, 1.5f)
            for (m in houseFence) m.material = fenceMat
            add(g, planeG(3.2f, 1.6f), kitMat("concrete", "#b9b2a6", 3f, 2f), 0.6f, 0.336f, -4.6f).rotation.x = -P2
            for ((x, z, ry) in listOf(Triple(0.0f, -4.5f, 0.3f), Triple(1.1f, -4.5f, -0.3f))) {
                val ch = sub(g, x, 0.32f, z, ry)
                add(ch, Geo.box(0.45f, 0.06f, 0.45f), mat(0xf1f1ea), 0f, 0.32f, 0f)
                add(ch, Geo.box(0.45f, 0.45f, 0.05f), mat(0xf1f1ea), 0f, 0.55f, -0.2f).rotation.x = -0.2f
                for ((lx, lz) in listOf(-0.2f to -0.2f, 0.2f to -0.2f, -0.2f to 0.2f, 0.2f to 0.2f)) add(ch, Geo.box(0.04f, 0.32f, 0.04f), mat(0xd6d3c9), lx, 0.16f, lz)
            }
            summerOnly += add(g, Geo.cylinder(0.75f, 0.75f, 0.25f, 16), mat(0x3b82f6), 2.8f, 0.45f, -6.0f)
            val water2 = add(g, Geo.circle(0.7f, 16), PS1.material(color = 0x7dd3fc, emissive = 0x38bdf8, emissiveIntensity = 0.25f), 2.8f, 0.58f, -6.0f); water2.rotation.x = -P2; summerOnly += water2
            val gnome = sub(g, -1.4f, 0.32f, -5.0f)
            add(gnome, Geo.cylinder(0.06f, 0.09f, 0.18f, 6), mat(0x2563eb), 0f, 0.09f, 0f)
            add(gnome, Geo.sphere(0.06f, 6, 5), mat(0xf5c9a8), 0f, 0.22f, 0f)
            add(gnome, Geo.cone(0.07f, 0.18f, 6), mat(0xdc2626), 0f, 0.34f, 0f)
            tree3D(g, 3.8f, -9.6f, 1.3f); tree3D(g, -4.0f, -9.4f, 1.1f)
            for ((x, z) in listOf(-2.5f to -10.1f, -1.5f to -10.15f, 1.8f to -10.1f)) bush3D(g, x, z, 1.2f, "#4a7a3a")
        }
        // Trailer park: corrugated siding, skirting, a wooden deck with lawn chairs and a grill, a mailbox row
        run {
            val g = G.getValue("trailer")
            for (m in trailerBodies) m.material = kitMat("corrugated", "#" + Integer.toHexString(m.material.color.getHex()).padStart(6, '0'), 6f, 2f)
            for (m in trailerSkirts) m.material = kitMat("wood", "#6b6560", 8f, 0.5f)
            add(g, Geo.box(1.6f, 0.12f, 1.0f), kitMat("wood", "#8a6a4a", 2f, 1f), -0.4f, 0.4f, -5.1f)
            for ((x, z) in listOf(-0.9f to -5.0f, -0.2f to -5.1f)) {
                add(g, Geo.box(0.36f, 0.05f, 0.36f), mat(0x16a34a), x, 0.7f, z)
                add(g, Geo.box(0.36f, 0.4f, 0.04f), mat(0x16a34a), x, 0.88f, z - 0.17f).rotation.x = -0.25f
            }
            add(g, Geo.sphere(0.2f, 8, 6, 0f, PF * 2, 0f, PF / 2), mat(0x111111), 0.5f, 0.95f, -5.2f).rotation.x = PF
            for (i in 0 until 4) add(g, Geo.box(0.16f, 0.14f, 0.26f), mat(intArrayOf(0x9ca3af, 0x1d4ed8, 0xdc2626, 0x111111)[i]), 3.2f + i * 0.2f, 1.0f, -4.8f)
            add(g, Geo.box(0.9f, 0.04f, 0.3f), kitMat("wood", "#7a5a3a"), 3.5f, 0.92f, -4.8f)
            add(g, Geo.box(0.06f, 0.6f, 0.06f), kitMat("wood", "#7a5a3a"), 3.5f, 0.62f, -4.8f)
            tree3D(g, -3.8f, -8.4f, 1.0f); bush3D(g, 1.0f, -6.0f, 0.9f, "#6b7a3a")
        }
        // Arizona: block-wall texture, palm bark, a stucco house with a tile roof beyond the wall, gravel yard plants
        run {
            val g = G.getValue("arizona")
            arizonaWall.material = kitMat("cinder", "#c8ab86", 24f, 1.2f)
            house3D(g, -2.4f, -11.2f, w = 3.2f, h = 1.2f, d = 2.0f, wall = "#e2c9a6", roof = "#b4553a", roofKind = "tiles", wallKind = "stucco", garage = true)
            house3D(g, 2.6f, -11.6f, w = 3.0f, h = 1.25f, d = 2.0f, wall = "#ddb894", wallKind = "stucco", flat = true)
            // Ocotillo: tall thin whips
            val oc = sub(g, -2.8f, 0.32f, -6.2f)
            for (i in 0 until 9) { val pv = Group(); pv.rotation.set((i % 3 - 1) * 0.25f, 0f, (i - 4) * 0.08f); oc.add(pv); add(pv, Geo.cylinder(0.012f, 0.02f, 1.6f, 4), mat(0x5b6b3a), 0f, 0.8f, 0f) }
            for ((x, z) in listOf(0.6f to -5.4f, -0.6f to -6.6f, 1.8f to -6.9f)) bush3D(g, x, z, 0.7f, "#6f7f4a")
        }
        // Desert canyon: big layered sandstone buttes up close
        run {
            val g = G.getValue("desert")
            for (b in listOf(floatArrayOf(-4.2f, -12.5f, 3.2f, 3.4f), floatArrayOf(4.8f, -13.0f, 2.6f, 4.2f), floatArrayOf(1.0f, -14.2f, 4.0f, 2.4f)))
                add(g, Geo.cylinder(b[2] * 0.42f, b[2] * 0.55f, b[3], 7), kitMat("rock", "#b4643c", 2f, b[3] / 1.2f), b[0], 0.32f + b[3] / 2, b[1])
            val rockMat = kitMat("rock", "#a0674a")
            for (m in desertRocks) m.material = rockMat
        }
        // Tropical island: overwater bungalows on stilts out in the lagoon, textured palms
        run {
            val g = G.getValue("island")
            for (i in 0 until 4) {
                val b = sub(g, -3f + i * 2.2f, 0.34f, -12.2f - (i % 2) * 0.6f)
                add(b, Geo.box(1.0f, 0.55f, 0.9f), kitMat("wood", "#a0743c", 1f, 1f), 0f, 0.75f, 0f)
                add(b, Geo.cylinder(0.01f, 0.95f, 0.6f, 4).rotateY(P4), kitMat("thatch", "#c8a35a", 2f, 1f), 0f, 1.32f, 0f)
                for ((x, z) in listOf(-0.4f to -0.35f, 0.4f to -0.35f, -0.4f to 0.35f, 0.4f to 0.35f)) add(b, Geo.cylinder(0.03f, 0.03f, 0.5f, 4), kitMat("wood", "#6b4a2a"), x, 0.25f, z)
                windowPane(b, 0.28f, 0.22f, 0f, 0.8f, 0.46f)
                add(b, Geo.box(0.25f, 0.04f, 1.6f), kitMat("wood", "#8a6a4a"), 0f, 0.48f, 1.2f)
            }
        }
        // Beach boardwalk: plank texture on the boardwalk, towels and a cooler on the sand
        run {
            val g = G.getValue("beach")
            boardwalk.material = kitMat("wood", "#8b6b4a", 30f, 0.6f)
            for ((x, z, col) in listOf(Triple(0.2f, -6.2f, "#ef4444"), Triple(2.6f, -6.6f, "#3b82f6"))) add(g, Geo.plane(0.5f, 1.0f), kitMat("awning", col), x, 0.336f, z).rotation.x = -P2
            add(g, Geo.box(0.35f, 0.25f, 0.22f), mat(0x2563eb), 0.8f, 0.45f, -6.0f)
            add(g, Geo.box(0.37f, 0.05f, 0.24f), mat(0xf8fafc), 0.8f, 0.6f, -6.0f)
        }
        // Lakeside and forest: a cabin across the water, stumps, ferns and mushrooms in the woods
        run {
            val cabin = house3D(G.getValue("lake"), -3.0f, -13.6f, w = 2.0f, h = 1.1f, d = 1.5f, wall = "#7a5232", roof = "#3f3a30", wallKind = "wood", roofKind = "shingles")
            cabin.rotation.y = 0.3f
            val f = G.getValue("forest")
            for ((x, z) in listOf(0.2f to -5.0f, 2.6f to -6.0f, -1.8f to -6.6f)) add(f, Geo.cylinder(0.18f, 0.22f, 0.25f, 8), kitMat("bark", "#5b3d26"), x, 0.45f, z)
            for (i in 0 until 8) add(f, Geo.cone(0.22f, 0.3f, 5), kitMat("needles", "#3f6b2f"), -3f + i * 0.9f, 0.47f, -5.2f - (i % 3) * 0.9f).rotation.x = PF
            for ((x, z) in listOf(1.2f to -5.4f, 1.35f to -5.5f)) {
                add(f, Geo.cylinder(0.02f, 0.025f, 0.1f, 5), mat(0xf1ead8), x, 0.37f, z)
                add(f, Geo.sphere(0.06f, 6, 4, 0f, PF * 2, 0f, PF / 2), mat(0xc0392b), x, 0.42f, z)
            }
            pineMat.map = kitTex("needles", "#ffffff", 2f, 2f)
            val rockMat = kitMat("rock", "#7b818c")
            for (m in mountainRocks) m.material = rockMat
            val fm = G.getValue("mountains")
            for (i in 0 until 6) {
                add(fm, Geo.box(0.08f, 0.55f, 0.08f), kitMat("wood", "#6b4a2a"), -3f + i * 1.2f, 0.6f, -5.4f)
                if (i < 5) add(fm, Geo.box(1.2f, 0.06f, 0.06f), kitMat("wood", "#6b4a2a"), -2.4f + i * 1.2f, 0.75f, -5.4f).rotation.z = 0.05f * (if (i % 2 == 1) 1 else -1)
            }
        }
        // Countryside: a red barn with white trim and a silo in 3D, textured fence and hay, a couple of cows
        run {
            val g = G.getValue("countryside")
            val hayMat = kitMat("hay", "#d4b04a")
            for (m in countryHay) m.material = hayMat
            val postMat = kitMat("wood", "#8a6a4a", 1f, 1f); val railMat = kitMat("wood", "#8a6a4a", 20f, 1f)
            countryWood.forEachIndexed { i, m -> m.material = if (i < 32) postMat else railMat }
            val barn = sub(g, 2.8f, 0.32f, -12.5f, -0.3f)
            add(barn, boxG(3.2f, 2.0f, 2.6f), kitMat("wood", "#a8322a", 4f, 2f), 0f, 1.0f, 0f)
            add(barn, Geo.cylinder(0.01f, 1.0f, 1.0f, 4).rotateY(P4), kitMat("corrugated", "#5b5f66", 4f, 2f), 0f, 2.55f, 0f).scale.set(2.4f, 1.1f, 2.0f)
            add(barn, Geo.plane(1.2f, 1.4f), kitMat("wood", "#8f2a24", 2f, 1f), 0f, 0.7f, 1.31f)
            for (rz in floatArrayOf(0.8f, -0.8f)) add(barn, Geo.box(1.7f, 0.08f, 0.02f), mat(0xf1f1ea), 0f, 0.7f, 1.32f).rotation.z = rz
            add(g, Geo.cylinder(0.6f, 0.6f, 3.2f, 10), kitMat("corrugated", "#c9cdd2", 4f, 3f), 5.0f, 1.92f, -12.0f)
            add(g, Geo.sphere(0.6f, 10, 6, 0f, PF * 2, 0f, PF / 2), mat(0xa8adb4), 5.0f, 3.52f, -12.0f)
            val cowMat = kitMat("default", "#f1f1ea")
            for ((x, z, ry) in listOf(Triple(-1.2f, -8.6f, 0.5f), Triple(0.4f, -9.4f, -0.9f))) {
                val cow = sub(g, x, 0.32f, z, ry)
                add(cow, Geo.box(0.75f, 0.38f, 0.32f), cowMat, 0f, 0.48f, 0f)
                for (s in listOf(floatArrayOf(0.15f, 0.6f, 0.17f), floatArrayOf(-0.2f, 0.5f, -0.17f))) add(cow, Geo.box(0.22f, 0.2f, 0.02f), mat(0x1f1f1f), s[0], s[1], s[2])
                add(cow, Geo.box(0.2f, 0.22f, 0.2f), cowMat, 0.45f, 0.58f, 0f)
                for ((lx, lz) in listOf(-0.28f to -0.1f, 0.28f to -0.1f, -0.28f to 0.1f, 0.28f to 0.1f)) add(cow, Geo.box(0.07f, 0.3f, 0.07f), cowMat, lx, 0.15f, lz)
            }
        }
        // Skyscraper: rooftop clutter on the neighbouring tower (AC units, a water tank, a helipad marking)
        run {
            val g = G.getValue("skyscraper")
            for ((x, z) in listOf(3.8f to -10.2f, 5.2f to -11.0f)) add(g, Geo.box(0.5f, 0.35f, 0.5f), kitMat("corrugated", "#9ca3af"), x, 2.29f, z)
            add(g, Geo.cylinder(0.35f, 0.35f, 0.6f, 10), kitMat("wood", "#6b4a2a", 3f, 1f), 5.6f, 2.75f, -9.6f)
            add(g, Geo.cone(0.38f, 0.25f, 10), mat(0x3f3a30), 5.6f, 3.17f, -9.6f)
            for (dx in floatArrayOf(-0.25f, 0.25f)) add(g, Geo.box(0.04f, 0.3f, 0.04f), mat(0x334155), 5.6f + dx, 2.3f, -9.6f)
        }
        val signTexH = createCanvasTex(64, 64) { c, _, _ ->
            c.fillStyle = "#3f4450"; c.fillRect(0, 0, 64, 64); c.strokeStyle = "#facc15"; c.lineWidth = 3f; c.beginPath(); c.arc(32, 32, 27, 0, 7); c.stroke()
            c.fillStyle = "#f1f1ea"; c.font = "bold 34px monospace"; c.textAlign = "center"; c.textBaseline = "middle"; c.fillText("H", 32, 34)
        }.tex
        signTexH.nearest = false
        val signMatH = PS1.material(map = signTexH)
        // Skyscraper: the rest of downtown below your floor, real towers with lit facades and busy rooftops
        run {
            val g = G.getValue("skyscraper")
            val fmats = exterior.buildingTextures.map { t -> PS1.material(map = t, emissive = 0xffffff, emissiveMap = t, emissiveIntensity = 0.5f) }
            val taken = arrayListOf(floatArrayOf(4.6f, -10.5f, 3.4f, 3.2f), floatArrayOf(-3.2f, -13.5f, 2.8f, 2.8f)) // x, z, w, d of the towers already there
            fun fits(x: Float, z: Float, w: Float, d: Float) = taken.all { (tx, tz, tw, td) -> abs(x - tx) > (w + tw) / 2 + 0.3f || abs(z - tz) > (d + td) / 2 + 0.3f }
            val r = makeRng(77)
            var placed = 0
            var tries = 0
            while (tries < 200 && placed < 12) {
                tries++
                val w = (1.4 + r() * 1.6).toFloat(); val d = (1.4 + r() * 1.6).toFloat(); val x = (-9 + r() * 20).toFloat(); val z = (-5.5 - r() * 9.5).toFloat(); val top = (-1.6 + r() * 3.4).toFloat()
                if (!fits(x, z, w, d) || (abs(x - 0.8f) < 2.6f && z > -7)) continue // keep the view straight down open
                taken += floatArrayOf(x, z, w, d); placed++
                val hgt = top + 2.38f
                facades += add(g, boxG(w, hgt, d, 1, 4, 1), fmats[placed % 3], x, -2.38f + hgt / 2, z)
                add(g, boxG(w + 0.06f, 0.08f, d + 0.06f), kitMat("concrete", "#6b6f78", 2f, 2f), x, top + 0.04f, z)
                when (placed % 4) {
                    0 -> add(g, boxG(w * 0.35f, 0.3f, d * 0.3f), kitMat("corrugated", "#9ca3af"), x - w * 0.2f, top + 0.23f, z)
                    1 -> { add(g, Geo.cylinder(0.3f, 0.3f, 0.5f, 10), kitMat("wood", "#6b4a2a", 3f, 1f), x + w * 0.2f, top + 0.45f, z); add(g, Geo.cone(0.33f, 0.22f, 10), mat(0x3f3a30), x + w * 0.2f, top + 0.81f, z) }
                    2 -> add(g, Geo.circle(min(w, d) * 0.4f, 16), signMatH, x, top + 0.085f, z).rotation.x = -P2
                    3 -> { add(g, Geo.cylinder(0.02f, 0.03f, 0.9f, 4), mat(0x475569), x, top + 0.5f, z); add(g, Geo.box(0.07f, 0.07f, 0.07f), PS1.material(color = 0xff2222, emissive = 0xff0000, emissiveIntensity = 1f), x, top + 0.97f, z) }
                }
            }
        }
        // Neon City: wet puddles that catch the neon at night, a glowing vending machine, paper lanterns
        run {
            val g = G.getValue("neon")
            for ((x, z, col) in listOf(Triple(-0.8f, -6.3f, 0xff3ea5), Triple(1.8f, -6.9f, 0x2ef2ff), Triple(3.0f, -6.0f, 0xffe14d))) {
                val m = PS1.basic(color = col, transparent = true, opacity = 0f, depthWrite = false); puddleMats += m
                val p = add(g, Geo.circle(0.5f, 10), m, x, 0.345f, z); p.rotation.x = -P2; p.scale.set(1.6f, 0.8f, 1f)
            }
            // Shop fronts along the row: lit windows, striped awnings, more signs
            neonShops.material = kitMat("concrete", "#3a3542", 9f, 1.5f)
            for ((x, col) in listOf(-2.6f to "#c0392b", 0.1f to "#1d4ed8", 2.8f to "#16a34a")) {
                add(g, Geo.plane(1.6f, 0.7f), litWinMat, x + 0.4f, 0.95f, -9.74f)
                add(g, Geo.box(2.0f, 0.04f, 0.5f), kitMat("awning", col, 2f, 1f), x + 0.4f, 1.45f, -9.5f).rotation.x = 0.35f
            }
            neonSign(g, "PACHINKO", "#ff6b3d", -3.6f, 2.6f, -9.9f, 0.32f, 1.4f, true)
            neonSign(g, "NOODLE", "#ff3ea5", 4.4f, 2.0f, -9.74f, 1.2f, 0.34f, false)
            add(g, boxG(1.8f, 6f, 3.2f), kitMat("brick", "#4a3f52", 2.5f, 4.5f), -3.4f, 3.3f, -6.8f)
            for (fl in 0 until 4) windowPane(g, 0.36f, 0.42f, -2.48f, 1.4f + fl * 1.1f, -6.4f, P2)
            neonSign(g, "CAFE", "#2ef2ff", -2.45f, 1.6f, -7.6f, 0.06f, 0.3f, false).rotation.y = P2
            add(g, Geo.box(0.6f, 1.4f, 0.5f), mat(0xdc2626), -2.6f, 1.02f, -9.4f)
            val vendFace = PS1.material(color = 0xffffff, emissive = 0xbfe8ff, emissiveIntensity = 0f); nightGlowMats += vendFace
            add(g, Geo.plane(0.45f, 0.8f), vendFace, -2.6f, 1.15f, -9.14f)
            for (i in 0 until 6) {
                val lm = PS1.material(color = 0xff5a3c, emissive = 0xff3b1a, emissiveIntensity = 0f); nightGlowMats += lm
                val l = add(g, Geo.sphere(0.12f, 8, 6), lm, -3f + i * 1.1f, 2.0f - sin(i / 5f * PF) * 0.15f, -9.6f); l.scale.y = 1.3f
            }
        }
    }

    // ---- PS1-style filler: crossed-quad sprite trees, cacti, palms, bushes and grass tufts with chunky
    // 32/64 px cut-out textures, the way PlayStation games dressed their outdoor worlds ----
    private fun spriteTex(kind: String): Texture {
        SPRITE_TEX[kind]?.let { return it }
        val W2 = 32; val H2 = if (kind == "tuft" || kind == "bush" || kind == "rocks") 16 else 64
        val r = makeRng(kind.length * 17)
        val t = createCanvasTex(W2, H2) { c, _, _ ->
            fun px(x: Double, y: Double, col: String) { c.fillStyle = col; c.fillRect(x.toInt(), y.toInt(), 1, 1) }
            when (kind) {
                "pine" -> {
                    c.fillStyle = "#4a3020"; c.fillRect(15, 52, 3, 12)
                    val cols = arrayOf("#1f4a2c", "#2a5e36", "#173a22", "#356b3e")
                    for (y in 2 until 56) { val half = (y / 56.0) * 14 + (if ((y % 8) < 3) 2 else 0); var x = 16 - half; while (x < 16 + half) { if (r() > 0.12) px(x, y.toDouble(), cols[floor(r() * 4).toInt()]); x += 1 } }
                }
                "tree" -> {
                    c.fillStyle = "#5b3d26"; c.fillRect(14, 36, 4, 28)
                    val cols = arrayOf("#ffffff", "#e6e6e6", "#cfcfcf", "#bdbdbd")
                    for (i in 0 until 520) { val a = r() * 6.283; val d = sqrt(r()) * 14; px(16 + cos(a) * d, 22 + sin(a) * d * 1.1, cols[floor(r() * 4).toInt()]) }
                }
                "palm" -> {
                    for (y in 20 until 64) { c.fillStyle = if (y % 4 != 0) "#7a5a3a" else "#5b4028"; c.fillRect(15 + sin(y * 0.08) * 2, y, 3, 1) }
                    for (i in 0 until 7) { val a = -PI / 2 + (i - 3) * 0.55; for (tt in 0 until 14) { val x = 16 + cos(a) * tt; val y = 18 + sin(a) * tt * 0.7 + tt * tt * 0.04; px(x, y, "#2f7a3a"); px(x, y + 1, "#235e2c") } }
                }
                "cactus" -> {
                    c.fillStyle = "#3f7a46"; c.fillRect(13, 8, 6, 56); c.fillRect(5, 30, 8, 4); c.fillRect(5, 16, 4, 16); c.fillRect(19, 26, 8, 4); c.fillRect(23, 12, 4, 16)
                    c.fillStyle = "#5a9a5e"; c.fillRect(14, 8, 1, 56); c.fillRect(6, 16, 1, 16); c.fillRect(24, 12, 1, 16)
                }
                "bush" -> { val cols = arrayOf("#ffffff", "#dcdcdc", "#c4c4c4"); for (i in 0 until 200) { val a = r() * 6.283; val d = sqrt(r()); px(16 + cos(a) * d * 15, 10 + sin(a) * d * 6, cols[floor(r() * 3).toInt()]) } }
                "tuft" -> { val cols = arrayOf("#ffffff", "#d9d9d9", "#bcbcbc"); for (i in 0 until 26) { val x = 2 + r() * 28; val h = 4 + r() * 11; c.fillStyle = cols[i % 3]; c.fillRect(x, 16 - h, 1, h) } }
                "rocks" -> { val cols = arrayOf("#8a7a6a", "#a08c78", "#6f6458"); for (i in 0 until 6) { val x = 3 + r() * 24; val w = 3 + r() * 5; c.fillStyle = cols[i % 3]; c.fillRect(x, 16 - w * 0.6, w, w * 0.6) } }
            }
        }.tex
        SPRITE_TEX[kind] = t
        return t
    }
    // Tinted sprite materials so seasons and places can recolour the white "tree" / "bush" / "tuft" sprites
    private fun spriteMat(kind: String, tint: Int = 0xffffff) = PS1.material(color = tint, map = spriteTex(kind), alphaTest = 0.5f, side = DOUBLE_SIDE)
    private fun crossSprite(group: Group, m: Material, x: Float, z: Float, w: Float, h: Float) {
        val g = sub(group, x, 0.32f, z)
        for (i in 0 until 2) { val q = add(g, Geo.plane(w, h), m, 0f, h / 2, 0f); q.rotation.y = i * P2 + 0.4f }
    }
    // Scatter sprites in a band, skipping anything already standing there
    private fun scatter(group: Group, m: Material, count: Int, x0: Float, x1: Float, z0: Float, z1: Float, wMin: Float, wMax: Float, aspect: Float, seed: Int, avoid: List<FloatArray> = emptyList()) {
        val r = makeRng(seed)
        var placed = 0; var tries = 0
        while (tries < count * 6 && placed < count) {
            tries++
            val x = (x0 + r() * (x1 - x0)).toFloat(); val z = (z0 + r() * (z1 - z0)).toFloat(); val w = (wMin + r() * (wMax - wMin)).toFloat()
            if (avoid.any { (ax, az, ar) -> hypot(x - ax, z - az) < ar + w * 0.4f }) continue
            crossSprite(group, m, x, z, w, w * aspect); placed++
        }
    }
    private fun treeTint() = when (env.currentSeason) { "spring" -> 0x7cbf5a; "summer" -> 0x4f8f3e; "fall" -> 0xd08a3a; "winter" -> 0x8a7a6a; else -> 0x4f8f3e }
    private fun grassTint() = when (env.currentSeason) { "spring" -> 0x7cbf5a; "summer" -> 0x5f9a45; "fall" -> 0xb09050; "winter" -> 0xd8dde6; else -> 0x5f9a45 }
    private fun fa(vararg v: Float) = v
    private fun buildSprites() {
        decidMat = spriteMat("tree"); bushMat = spriteMat("bush"); tuftMat = spriteMat("tuft")
        pineSpr = spriteMat("pine"); val palmSpr = spriteMat("palm"); val cactusSpr = spriteMat("cactus"); val rockSpr = spriteMat("rocks")
        val dryTuft = spriteMat("tuft", 0xb8a070); val dryBush = spriteMat("bush", 0x7f8a4a)
        // Forest, lakeside far shore and mountains: dense sprite pines behind the 3D trees
        scatter(G.getValue("forest"), pineSpr, 46, -11f, 12.6f, -8.5f, -15.4f, 1.4f, 2.6f, 2f, 501, listOf(fa(0.8f, -4f, 1.5f)))
        scatter(G.getValue("forest"), tuftMat, 30, -6f, 7f, -4.2f, -8f, 0.4f, 0.7f, 0.5f, 502)
        scatter(G.getValue("lake"), pineSpr, 30, -11f, 12.6f, -13.2f, -15.4f, 1.2f, 2.2f, 2f, 503, listOf(fa(-3.0f, -13.6f, 1.4f)))
        scatter(G.getValue("lake"), tuftMat, 18, -6f, 7f, -4.2f, -7.4f, 0.4f, 0.6f, 0.5f, 504)
        scatter(G.getValue("mountains"), pineSpr, 16, -11f, 12.6f, -7f, -15.4f, 0.9f, 1.5f, 2f, 505, listOf(fa(0.8f, -11f, 3.2f))) // keep the middle open so the peaks show
        scatter(G.getValue("mountains"), rockSpr, 10, -6f, 7f, -4.4f, -8f, 0.6f, 1.0f, 0.5f, 506)
        // Leafy places: street trees and hedges in the middle distance, grass tufts near the window
        scatter(G.getValue("suburbs"), decidMat, 10, -11f, 12.6f, -11.5f, -14.5f, 1.6f, 2.4f, 1.6f, 511)
        scatter(G.getValue("suburbs"), tuftMat, 16, -6f, 7f, -4.0f, -5.2f, 0.3f, 0.5f, 0.5f, 512)
        scatter(G.getValue("house"), decidMat, 14, -11f, 12.6f, -11.0f, -14.8f, 1.8f, 2.8f, 1.6f, 513)
        scatter(G.getValue("house"), tuftMat, 26, -6f, 7f, -4.0f, -9.8f, 0.3f, 0.55f, 0.5f, 514, listOf(fa(2.6f, -8.0f, 1.0f), fa(-0.6f, -6.8f, 1.2f), fa(-3.0f, -9.2f, 1.0f), fa(2.8f, -6.0f, 0.8f)))
        scatter(G.getValue("countryside"), decidMat, 12, -11f, 12.6f, -13.5f, -15.4f, 1.6f, 2.6f, 1.6f, 515)
        scatter(G.getValue("countryside"), tuftMat, 30, -8f, 9f, -6.4f, -11f, 0.4f, 0.7f, 0.5f, 516, listOf(fa(2.8f, -12.5f, 2.6f), fa(5.0f, -12.0f, 0.8f), fa(1.6f, -8.0f, 0.6f), fa(2.6f, -8.6f, 0.6f), fa(-0.8f, -9.5f, 0.6f)))
        scatter(G.getValue("trailer"), dryBush, 10, -11f, 12.6f, -13f, -15.2f, 1.0f, 1.8f, 0.6f, 517)
        scatter(G.getValue("trailer"), dryTuft, 20, -8f, 9f, -4.7f, -12.5f, 0.3f, 0.5f, 0.5f, 518, listOf(fa(-1.4f, -6.2f, 2.3f), fa(3.6f, -9.5f, 2.3f), fa(-2.8f, -12.0f, 2.3f), fa(1.6f, -10.8f, 1.2f)))
        scatter(G.getValue("neighborhood"), decidMat, 6, -11f, 12.6f, -12.0f, -14.5f, 1.6f, 2.4f, 1.6f, 519)
        // Hot places: palms, cacti, desert scrub and rocks
        scatter(G.getValue("arizona"), palmSpr, 10, -11f, 12.6f, -12.8f, -15.4f, 1.6f, 2.4f, 2f, 521)
        scatter(G.getValue("arizona"), cactusSpr, 6, -11f, 12.6f, -12.5f, -15.4f, 0.8f, 1.2f, 2f, 522)
        scatter(G.getValue("arizona"), rockSpr, 10, -6f, 7f, -4.2f, -7.2f, 0.4f, 0.7f, 0.5f, 523, listOf(fa(-1.4f, -5.6f, 0.5f), fa(3.2f, -6.4f, 0.5f), fa(2.0f, -5.5f, 0.8f)))
        scatter(G.getValue("desert"), cactusSpr, 16, -11f, 12.6f, -7f, -15.4f, 0.8f, 1.6f, 2f, 524, listOf(fa(-4.2f, -12.5f, 2f), fa(4.8f, -13f, 1.8f), fa(1f, -14.2f, 2.4f)))
        scatter(G.getValue("desert"), dryBush, 14, -11f, 12.6f, -4.5f, -12f, 0.5f, 0.9f, 0.6f, 525)
        scatter(G.getValue("desert"), rockSpr, 12, -8f, 9f, -4.2f, -9f, 0.5f, 0.9f, 0.5f, 526)
        scatter(G.getValue("island"), palmSpr, 8, -11f, 12.6f, -4.6f, -8.0f, 1.6f, 2.6f, 2f, 527, listOf(fa(-1.4f, -5.2f, 1.2f), fa(3.4f, -6.4f, 1.2f), fa(1.6f, -7.6f, 1.0f), fa(0.2f, -5.0f, 0.4f), fa(2.4f, -5.4f, 0.4f), fa(1.0f, -5.8f, 1.4f)))
        scatter(G.getValue("beach"), palmSpr, 6, -11f, 12.6f, -4.0f, -4.9f, 1.6f, 2.4f, 2f, 528, listOf(fa(-1.4f, -5.2f, 1.2f), fa(1.8f, -7.2f, 1f)))
        // Real mountains up front for the Mountains view: textured rock with snowcaps (kept inside the sky box)
        val g = G.getValue("mountains")
        for (m in listOf(fa(-7.5f, -13.2f, 2.6f, 5.2f), fa(-2.6f, -13.6f, 2.2f, 6.4f), fa(3.6f, -13.4f, 2.6f, 5.6f), fa(9.2f, -13.0f, 2.4f, 4.8f))) {
            val (x, z, rad, h) = m
            add(g, Geo.cone(rad, h, 7, 4), kitMat("rock", "#5c606c", 3f, 3f), x, 0.32f + h / 2, z)
            add(g, Geo.cone(rad * 0.4f, h * 0.4f, 7, 1), mat(0xdfe8f5), x, 0.32f + h - h * 0.19f + 0.02f, z)
        }
    }
    private fun updateSpriteSeason() {
        decidMat.color.setHex(treeTint()); bushMat.color.setHex(treeTint()); tuftMat.color.setHex(grassTint())
        pineSpr.color.setHex(if (env.currentSeason == "winter" && !envDef().noSnow) 0xc8d4cc else 0xffffff)
    }

    // Water places, extra life: a snack shack and umbrellas on the beach, a jetty and boat at the island,
    // lily pads and a canoe on the lake
    private fun buildWaterExtras() {
        val b = G.getValue("beach")
        val shack = sub(b, -3.4f, 0.32f, -7.0f, 0.35f)
        add(shack, Geo.box(1.6f, 1.1f, 1.2f), kitMat("wood", "#d9c7a0", 2f, 1f), 0f, 0.55f, 0f)
        add(shack, Geo.box(1.9f, 0.08f, 1.5f), kitMat("awning", "#2563eb", 3f, 1f), 0f, 1.18f, 0f)
        add(shack, Geo.plane(1.1f, 0.4f), litWinMat, 0f, 0.7f, 0.61f)
        neonSign(shack, "SNACKS", "#ff6b3d", 0f, 1.42f, 0.5f, 1.0f, 0.26f, false)
        for ((x, z, col) in listOf(Triple(3.4f, -5.6f, "#ef4444"), Triple(-1.6f, -6.9f, "#22c55e"))) {
            add(b, Geo.cone(0.65f, 0.28f, 8), kitMat("awning", col, 2f, 1f), x, 1.5f, z)
            add(b, Geo.cylinder(0.02f, 0.02f, 1.15f, 4), mat(0xe8e4dc), x, 0.9f, z)
            val t = add(b, Geo.plane(0.5f, 1.0f), kitMat("awning", col), x + 0.5f, 0.336f, z + 0.3f); t.rotation.x = -P2; t.rotation.z = 0.4f
        }
        val i = G.getValue("island")
        add(i, Geo.box(0.6f, 0.06f, 2.6f), kitMat("wood", "#8a6a4a", 1f, 3f), -1.6f, 0.46f, -9.6f)
        for (k in 0 until 4) for (dx in floatArrayOf(-0.26f, 0.26f)) add(i, Geo.cylinder(0.03f, 0.03f, 0.4f, 4), kitMat("wood", "#6b4a2a"), -1.6f + dx, 0.32f, -8.5f - k * 0.75f)
        val boat = sub(i, -0.9f, 0.36f, -10.4f, 0.15f)
        add(boat, Geo.box(0.5f, 0.18f, 1.4f), kitMat("wood", "#f1f1ea", 1f, 2f), 0f, 0.09f, 0f)
        add(boat, Geo.box(0.52f, 0.04f, 1.42f), mat(0x0ea5e9), 0f, 0.14f, 0f)
        val l = G.getValue("lake")
        val padMat = PS1.material(color = 0x3f7a35, side = DOUBLE_SIDE)
        for (k in 0 until 9) { val pad = add(l, Geo.circle(0.14f + (k % 3) * 0.04f, 7, 0.3f, 5.6f), padMat, -2.6f + (k % 5) * 0.4f + (if (k > 4) 0.2f else 0f), 0.35f, -8.0f - (k / 5) * 0.5f); pad.rotation.x = -P2 }
        val canoe = add(l, Geo.cylinder(0.16f, 0.16f, 1.5f, 8, 1, false, 0f, PF), kitMat("wood", "#b45309", 2f, 1f), -0.6f, 0.42f, -6.2f)
        canoe.rotation.set(0f, 0.6f, P2)
    }

    // ---- Switching places ----
    fun setOutsideEnv(key: String) {
        sky.outsideEnv = if (OUTSIDE_ENVS.containsKey(key)) key else "city"
        val e = envDef(); val city = outsideEnv == "city"
        for ((o, isTree) in exterior.cityParts()) {
            if (o === sky.cityBackdrop) continue
            o.visible = if (isTree) e.trees else city
        }
        for ((k, g) in G) g.visible = k == outsideEnv
        weatherFx.outdoorGround.visible = !e.noGround
        abyss.visible = e.noGround
        env.applyEnvironmentSettings() // re-tints everything, redraws the ground, leaves only where there are trees
        sky.cityStaticDirty = true
        onShadows()
    }
    // Seasons, time of day and weather on the props
    private fun updateOutsideEnvLook() {
        val winter = env.currentSeason == "winter"
        val snowing = env.isSnowingNow()
        val night = 1 - env.daylight
        pineMat.color.setHex(if (winter || snowing) 0x2a4a3a else (if (env.currentSeason == "fall") 0x26432c else 0x1f4a2c))
        for (c in snowCaps) c.visible = (winter || snowing) && !envDef().noSnow
        palmLeafMat.color.setHex(if (env.currentSeason == "fall" && !envDef().noSnow) 0x5f7a2a else 0x2f7a3a)
        val lit = night > 0.4 || env.isPrecipNow() && night > 0.2
        suburbWindowMat.emissiveIntensity = if (lit && (env.hour < 0.5 || env.hour > 17 || env.isPrecipNow())) 1.0f else 0f
        for (m in nightGlowMats) m.emissiveIntensity = if (lit) 1.0f else 0f
        litWinMat.emissiveIntensity = if (lit) 0.95f else 0f
        // Tree crowns follow the season (bare in winter, except where it never snows)
        val bare = winter && !envDef().noSnow
        val leafMat = leafMats.getValue(if (env.currentSeason == "fall") "fall" else (if (env.currentSeason == "spring") "spring" else "green"))
        for (c in crowns) { c.visible = !bare; c.material = leafMat }
        for (o in summerOnly) o.visible = env.currentSeason == "summer" || env.currentSeason == "spring"
        for (m in puddleMats) m.opacity = if (lit) (if (env.isPrecipNow()) 0.58f else 0.22f) else (if (env.isPrecipNow()) 0.24f else 0f)
        for (m in neonMats) m.emissiveIntensity = if (lit) 1.2f else 0.35f
        for ((wtr, day) in listOf(beachSea to intArrayOf(40, 110, 160), lakeWater to intArrayOf(44, 84, 100), islandSea to intArrayOf(60, 190, 196))) {
            val c = sky.toneC(intArrayOf(8, 14, 28), day, 0.2)
            wtr.mat.color.setRGB(c[0] / 255f, c[1] / 255f, c[2] / 255f)
            wtr.mat.emissiveIntensity = (0.12 + 0.35 * env.daylight).toFloat()
        }
        for (f in facades) f.material.emissiveIntensity = if (env.daylight > 0.55) 0.15f else (0.5 + 0.4 * night).toFloat()
    }
    // Per-frame motion: waves, foam, swaying palms and trees, swings, laundry, tiki torches, tumbleweeds, the rooftop billboard
    fun update(t: Float, dt: Float) {
        val e = outsideEnv
        if (e == "city") return
        val W = env.W
        val sway = 0.04f + W.wind * 0.14f
        if (e == "beach" || e == "island" || e == "arizona") swayers.forEachIndexed { i, top -> if (top.parent?.parent?.visible == true) top.rotation.x = sin(t * (1.1f + W.wind) + i) * sway }
        val treeSway = 0.012f + W.wind * 0.065f
        val treeSpeed = 1.3f + W.wind * 2.1f
        windTrees.forEachIndexed { i, tr ->
            if (tr.parent?.visible == true) {
                tr.rotation.z = sin(t * treeSpeed + i * 1.4f) * treeSway
                tr.rotation.x = cos(t * (treeSpeed * 0.8f) + i) * (treeSway * 0.5f)
            }
        }
        when (e) {
            "beach" -> {
                beachSea.tex.offsetY = (t * (0.03f + W.wind * 0.03f)) % 1
                val surge = sin(t * (0.8f + W.wind * 0.7f))
                foam.position.z = -9.0f + surge * (0.35f + W.wind * 0.2f)
                foam.material.opacity = 0.35f + 0.25f * max(0f, surge)
            }
            "island" -> {
                islandSea.tex.offsetY = (t * (0.02f + W.wind * 0.02f)) % 1
                tikiFlames.forEachIndexed { i, f -> f.material.emissiveIntensity = if (sky.isNight() && !env.isRainingNow()) 1.1f + sin(t * 13 + i * 2) * 0.25f else 0f; f.scale.y = 1 + sin(t * 11 + i) * 0.15f }
            }
            "lake" -> lakeWater.tex.offsetX = (t * (0.004f + W.wind * 0.008f)) % 1
            "house" -> {
                swings.forEachIndexed { i, s -> s.rotation.x = sin(t * 1.6f + i * 1.3f) * (0.05f + W.wind * 0.25f) }
                chimeRods.forEachIndexed { i, r -> r.rotation.x = sin(t * 2.3f + i) * (0.05f + W.wind * 0.35f); r.rotation.z = cos(t * 1.9f + i * 2) * (0.04f + W.wind * 0.3f) }
            }
            "trailer" -> {
                laundryItems.forEachIndexed { i, l ->
                    l.rotation.x = sin(t * (2.2f + W.wind * 3.5f) + i * 1.1f) * (0.06f + W.wind * 0.42f)
                }
            }
            "desert" -> {
                tumbleweed.position.x += dt * (0.4f + W.wind * 1.6f)
                tumbleweed.rotation.z -= dt * (1.5f + W.wind * 6)
                tumbleweed.position.y = 0.57f + abs(sin(t * 3)) * 0.15f
                if (tumbleweed.position.x > 7) tumbleweed.position.x = -7 - rnd.nextFloat() * 10
            }
            "skyscraper" -> {
                val now = SystemClock.uptimeMillis()
                if (now > skyAd.next) { exterior.drawNextPlacesAd(skyAd.ctx); skyAd.tex.needsUpdate(); skyAd.next = now + 25000 }
                skyAd.mat.emissiveIntensity = (0.3 + 0.6 * (1 - env.daylight)).toFloat()
                skyAd.beacon.emissiveIntensity = if (sin(t * 3) > 0.3f) 1f else 0.1f
            }
        }
    }
}
