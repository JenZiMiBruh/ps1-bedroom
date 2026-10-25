package com.ps1bedroom.room

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.ps1bedroom.room.engine.AmbientLight
import com.ps1bedroom.room.engine.Color
import com.ps1bedroom.room.engine.DOUBLE_SIDE
import com.ps1bedroom.room.engine.Geo
import com.ps1bedroom.room.engine.Group
import com.ps1bedroom.room.engine.HemisphereLight
import com.ps1bedroom.room.engine.Material
import com.ps1bedroom.room.engine.Mesh
import com.ps1bedroom.room.engine.Object3D
import com.ps1bedroom.room.engine.PerspectiveCamera
import com.ps1bedroom.room.engine.PointLight
import com.ps1bedroom.room.engine.RenderTarget
import com.ps1bedroom.room.engine.Scene
import com.ps1bedroom.room.engine.SpotLight
import com.ps1bedroom.room.engine.Texture
import com.ps1bedroom.room.engine.Vec3
import com.ps1bedroom.room.engine.createCanvasTex
import com.ps1bedroom.room.engine.makeRng
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * PLAYABLE GTA (v0.58, maps redone in v0.59): tap the TV while a 3D-era GTA is in game and you take the controller.
 * Each game's city follows the real map's layout in miniature: Liberty City's three islands and their bridges (III,
 * LCS), Vice City's mainland, Starfish Island and the beach island (VC, VCS), Los Santos with Vinewood's hills, the
 * beaches and the airport (SA, V), GTA IV's Alderney / Algonquin with Middle Park / Broker-Dukes / Bohan, and VI's
 * Vice City, Vice Beach and the Leonida Keys. Traffic and people keep out of each other's way; drive, walk, take
 * cars, go into the safehouse and shops, pick up cash, get wanted, get WASTED; pause for the map and stats.
 *
 * Threads: [GtaWorld] lives on the GL thread (built, simulated, rendered there). [GtaPlayView] is the Android overlay
 * with the HUD and touch controls; it writes [GtaInput] and reads the world's @Volatile snapshot fields.
 */

/** Touch state written by the overlay (UI thread), read by the world (GL thread). */
class GtaInput {
    @Volatile var joyX = 0f; @Volatile var joyY = 0f
    @Volatile var gas = false; @Volatile var brake = false
    @Volatile var action = false        // enter / exit / interact (consumed by the world)
    @Volatile var paused = false
    private var camDx = 0f
    @Synchronized fun addCam(dx: Float) { camDx += dx }
    @Synchronized fun takeCam(): Float { val d = camDx; camDx = 0f; return d }
}

/** An island / landmass with a street grid every 40 m. kind: city · hills · airport. beach: 'E' / 'W' sand outside that edge. */
class GtaIsle(val name: String, val x0: Float, val z0: Float, val x1: Float, val z1: Float, val districts: Array<String> = arrayOf(name),
              val kind: String = "city", val beach: Char = ' ', val bldH: Float = 1f, val parkZ0: Float = -1f, val parkZ1: Float = -1f)
/** A bridge deck (axis-aligned rectangle over the water). */
class GtaSpan(val x0: Float, val z0: Float, val x1: Float, val z1: Float, val name: String)
/** A named area for the district read-out (falls back to the island's own bands). */
class GtaRegion(val name: String, val x0: Float, val z0: Float, val x1: Float, val z1: Float)

/** One game's city: map, places, cars, people and look. */
class GtaSpec(
    val key: String, val title: String, val city: String, val hud: String,
    val isles: List<GtaIsle>, val bridges: List<GtaSpan>, val regions: List<GtaRegion>,
    /** kind = "house" (sleep / save), "food" (+health), "guns" (armour), "clothes" (new outfit); in block (bx, bz) of isle [isle] */
    val places: List<Place>,
    val cars: Array<String>, val carCols: IntArray, val playerCar: Int,
    val skin: Int, val shirt: Int, val pants: Int,
    val sky: Int, val skyLow: Int, val fog: Int, val fogD: Float, val night: Boolean,
    val bld: IntArray, val bldH: Float, val ground: Int, val palms: Boolean, val rain: Boolean,
    val accent: Int, val startCash: Int, val sign: String? = null
) {
    class Place(val name: String, val kind: String, val isle: Int, val bx: Int, val bz: Int)
}

object GtaSpecs {
    private fun p(n: String, k: String, i: Int, x: Int, z: Int) = GtaSpec.Place(n, k, i, x, z)
    private fun libertyIsles(stories: Boolean) = listOf(
        GtaIsle("SHORESIDE VALE", 0f, 40f, 120f, 200f, arrayOf("FRANCIS INTL. AIRPORT", "PIKE CREEK", "WICHITA GARDENS", "COCHRANE DAM"), bldH = 0.6f),
        GtaIsle("STAUNTON ISLAND", 200f, 0f, 360f, 200f, arrayOf("ROCKFORD", "BEDFORD POINT", "NEWPORT", "TORRINGTON"), bldH = 1.45f, parkZ0 = 80f, parkZ1 = 120f),
        GtaIsle("PORTLAND", 440f, 40f, 600f, 200f, if (stories) arrayOf("PORTLAND VIEW", "SAINT MARK'S", "HARWOOD", "CHINATOWN") else arrayOf("PORTLAND HARBOR", "SAINT MARK'S", "RED LIGHT DISTRICT", "CHINATOWN"), bldH = 0.9f)
    )
    private val libertyBridges = listOf(GtaSpan(120f, 114f, 200f, 126f, "SHORESIDE LIFT BRIDGE"), GtaSpan(360f, 154f, 440f, 166f, "CALLAHAN BRIDGE"))
    private val viceIsles = listOf(
        GtaIsle("MAINLAND", 0f, 0f, 160f, 320f, arrayOf("DOWNTOWN", "LITTLE HAITI", "LITTLE HAVANA"), bldH = 0.9f),
        GtaIsle("STARFISH ISLAND", 240f, 120f, 320f, 200f, bldH = 0.5f),
        GtaIsle("VICE BEACH", 400f, 0f, 480f, 320f, arrayOf("VICE POINT", "WASHINGTON BEACH", "OCEAN BEACH"), beach = 'E', bldH = 0.75f)
    )
    private val viceBridges = listOf(GtaSpan(160f, 154f, 240f, 166f, "STARFISH CAUSEWAY"), GtaSpan(320f, 154f, 400f, 166f, "STARFISH CAUSEWAY"), GtaSpan(160f, 34f, 400f, 46f, "NORTH BRIDGE"))

    val ALL = mapOf(
        "iii" to GtaSpec("iii", "GRAND THEFT AUTO III", "LIBERTY CITY", "iii", libertyIsles(false), libertyBridges, emptyList(),
            listOf(p("PORTLAND SAFEHOUSE", "house", 2, 1, 0), p("AMMU-NATION", "guns", 2, 2, 2), p("MARCO'S BISTRO", "food", 2, 3, 3)),
            arrayOf("KURUMA", "STALLION", "IDAHO", "CABBIE", "BANSHEE", "MESA GRANDE"), intArrayOf(0xd4a017, 0x6b7280, 0x7f1d1d, 0x14532d, 0x1e3a5f, 0xe5e7eb), 0x9ca3af,
            0xd6a77a, 0x111111, 0x2b2b33, 0x10141f, 0x2c3446, 0x2b3242, 0.02f, true,
            intArrayOf(0x3b3f47, 0x4a4f58, 0x55534e, 0x6b4f3a), 1.2f, 0x5b5f66, false, true, 0xf0a030, 250),
        "lcs" to GtaSpec("lcs", "GTA: LIBERTY CITY STORIES", "LIBERTY CITY", "lcs", libertyIsles(true), libertyBridges, emptyList(),
            listOf(p("TONI'S SAFEHOUSE", "house", 2, 1, 1), p("MA CIPRIANI'S", "food", 2, 2, 1), p("AMMU-NATION", "guns", 1, 1, 3)),
            arrayOf("SENTINEL", "STALLION", "KURUMA", "ESPERANTO", "CABBIE", "IDAHO"), intArrayOf(0xd4a017, 0x334155, 0x9a3412, 0x475569, 0xe2e8f0, 0x065f46), 0x111827,
            0xd6a77a, 0x5b3a1e, 0x1f2937, 0x5f7186, 0xb8c4d2, 0x7e8a99, 0.02f, false,
            intArrayOf(0x6b7280, 0x4b5563, 0x78716c, 0x57534e), 1.1f, 0x6b6f76, false, false, 0xef4444, 250),
        "vc" to GtaSpec("vc", "GRAND THEFT AUTO: VICE CITY", "VICE CITY", "vc", viceIsles, viceBridges, emptyList(),
            listOf(p("OCEAN VIEW HOTEL", "house", 2, 0, 7), p("AMMU-NATION", "guns", 0, 2, 1), p("GASH", "clothes", 2, 1, 1)),
            arrayOf("INFERNUS", "CHEETAH", "STINGER", "ADMIRAL", "IDAHO", "VOODOO"), intArrayOf(0xffffff, 0xf472b6, 0x2dd4bf, 0xfacc15, 0x60a5fa, 0xef4444), 0xf8fafc,
            0xe0b48c, 0x22d3ee, 0x1e3a8a, 0x2a0b45, 0xff5fa2, 0x3a1450, 0.016f, true,
            intArrayOf(0xf9a8d4, 0x99f6e4, 0xf5f5f4, 0xfde68a), 0.8f, 0xd8c8a8, true, false, 0xff5fa2, 300),
        "vcs" to GtaSpec("vcs", "GTA: VICE CITY STORIES", "VICE CITY", "vcs", viceIsles, viceBridges, emptyList(),
            listOf(p("VIC'S APARTMENT", "house", 0, 1, 6), p("AMMU-NATION", "guns", 2, 0, 4), p("CHERRY POPPER", "food", 0, 2, 3)),
            arrayOf("STINGER", "CHEETAH", "BANSHEE", "IDAHO", "ADMIRAL", "PONY"), intArrayOf(0xfef3c7, 0xf43f5e, 0x22d3ee, 0xffffff, 0xfb923c, 0x34d399), 0x0ea5e9,
            0xc9a07a, 0x556b2f, 0x3b3b2a, 0xff8a5c, 0xffe0b0, 0xd88a7a, 0.013f, false,
            intArrayOf(0xfbcfe8, 0xa5f3fc, 0xfef3c7, 0xfecaca), 0.7f, 0xe0d0b0, true, false, 0xf472b6, 300),
        "sa" to GtaSpec("sa", "GRAND THEFT AUTO: SAN ANDREAS", "LOS SANTOS", "sa",
            listOf(GtaIsle("VINEWOOD", 80f, 0f, 400f, 80f, kind = "hills"),
                GtaIsle("LOS SANTOS", 0f, 80f, 400f, 320f, beach = 'W', bldH = 0.7f),
                GtaIsle("LOS SANTOS INTL.", 0f, 320f, 160f, 400f, kind = "airport")),
            emptyList(),
            listOf(GtaRegion("SANTA MARIA BEACH", -40f, 80f, 80f, 320f), GtaRegion("RODEO", 80f, 80f, 160f, 200f), GtaRegion("DOWNTOWN", 160f, 80f, 280f, 200f),
                GtaRegion("VERONA BEACH", 80f, 200f, 200f, 320f), GtaRegion("IDLEWOOD", 200f, 200f, 320f, 280f), GtaRegion("JEFFERSON", 280f, 80f, 400f, 240f),
                GtaRegion("GANTON", 320f, 240f, 400f, 320f), GtaRegion("EL CORONA", 200f, 280f, 320f, 320f)),
            listOf(p("GROVE STREET (HOME)", "house", 1, 8, 5), p("CLUCKIN' BELL", "food", 1, 6, 3), p("BINCO", "clothes", 1, 9, 4)),
            arrayOf("GREENWOOD", "SAVANNA", "VOODOO", "SABRE", "ADMIRAL", "ELEGANT"), intArrayOf(0x166534, 0x6d28d9, 0xca8a04, 0x78350f, 0x0f172a, 0xb91c1c), 0x1f7a3a,
            0x5a3a26, 0xf8fafc, 0x1d4ed8, 0x3b2a6b, 0xffb067, 0x8a5a4a, 0.012f, false,
            intArrayOf(0xc2a27e, 0xa47d5b, 0x8c6b52, 0xd6b48a), 0.6f, 0xa08a6a, true, false, 0x2f7d32, 200, sign = "VINEWOOD"),
        "iv" to GtaSpec("iv", "GRAND THEFT AUTO IV", "LIBERTY CITY", "iv",
            listOf(GtaIsle("ALDERNEY", 0f, 40f, 120f, 320f, arrayOf("ALDERNEY CITY", "ACTER", "PORT TUDOR"), bldH = 0.8f),
                GtaIsle("ALGONQUIN", 200f, 0f, 320f, 400f, arrayOf("NORTHWOOD", "MIDDLE PARK", "STAR JUNCTION", "CHINATOWN"), bldH = 1.8f, parkZ0 = 120f, parkZ1 = 200f),
                GtaIsle("BOHAN", 360f, 0f, 520f, 120f, bldH = 0.7f),
                GtaIsle("BROKER / DUKES", 400f, 160f, 600f, 400f, arrayOf("DUKES", "BROKER"), bldH = 0.75f)),
            listOf(GtaSpan(120f, 194f, 200f, 206f, "BOOTH TUNNEL"), GtaSpan(320f, 74f, 360f, 86f, "NORTHERN EXPRESSWAY"), GtaSpan(320f, 194f, 400f, 206f, "EAST BOROUGH BRIDGE"), GtaSpan(320f, 354f, 400f, 366f, "BROKER BRIDGE")),
            emptyList(),
            listOf(p("ROMAN'S APARTMENT", "house", 3, 1, 5), p("BURGER SHOT", "food", 1, 1, 7), p("PERSEUS", "clothes", 1, 2, 1)),
            arrayOf("SULTAN", "PMP 600", "TAXI", "ADMIRAL", "INFERNUS", "BLISTA"), intArrayOf(0xd4a017, 0x1f2937, 0x52525b, 0x7c2d12, 0x374151, 0xd6d3d1), 0x4b5563,
            0xd8b490, 0x5b4636, 0x374151, 0x56605c, 0xa9ab9a, 0x6f766c, 0.02f, false,
            intArrayOf(0x5c5a52, 0x4a4842, 0x6b665c, 0x3f3d38), 1.0f, 0x55575a, false, false, 0xe5e7eb, 100),
        "v" to GtaSpec("v", "GRAND THEFT AUTO V", "LOS SANTOS", "v",
            listOf(GtaIsle("VINEWOOD HILLS", 40f, 0f, 440f, 120f, kind = "hills"),
                GtaIsle("LOS SANTOS", 0f, 120f, 480f, 400f, beach = 'W'),
                GtaIsle("LOS SANTOS INTL. AIRPORT", 80f, 400f, 240f, 480f, kind = "airport")),
            emptyList(),
            listOf(GtaRegion("DEL PERRO", -40f, 120f, 80f, 280f), GtaRegion("VESPUCCI BEACH", -40f, 280f, 80f, 400f), GtaRegion("ROCKFORD HILLS", 80f, 120f, 200f, 240f),
                GtaRegion("VINEWOOD", 200f, 120f, 360f, 160f), GtaRegion("DOWNTOWN", 200f, 160f, 320f, 280f), GtaRegion("MIRROR PARK", 360f, 120f, 480f, 200f),
                GtaRegion("STRAWBERRY", 200f, 280f, 360f, 400f), GtaRegion("EAST LOS SANTOS", 360f, 200f, 480f, 400f), GtaRegion("LITTLE SEOUL", 80f, 240f, 200f, 400f)),
            listOf(p("FRANKLIN'S HOUSE", "house", 0, 5, 2), p("24/7", "food", 1, 7, 5), p("PONSONBYS", "clothes", 1, 3, 1)),
            arrayOf("BUFFALO", "ELEGY", "ZENTORNO", "FUTO", "BAGGER", "PREMIER"), intArrayOf(0xffffff, 0x111827, 0x9ca3af, 0xdc2626, 0x1d4ed8, 0xfacc15), 0xffffff,
            0x6b4226, 0xf8fafc, 0x1e293b, 0x3b82f6, 0xdff1ff, 0xb5d4ef, 0.01f, false,
            intArrayOf(0xc9d6e3, 0x8fb3d9, 0xe7e1d6, 0x9fb8a8), 1.0f, 0x9ca3a8, true, false, 0x4ade80, 500, sign = "VINEWOOD"),
        "vi" to GtaSpec("vi", "GRAND THEFT AUTO VI", "VICE CITY, LEONIDA", "vi",
            listOf(GtaIsle("VICE CITY", 0f, 0f, 200f, 280f, arrayOf("DOWNTOWN", "LITTLE HAVANA", "VICE CITY PORT"), bldH = 1.1f),
                GtaIsle("VICE BEACH", 280f, 0f, 360f, 280f, beach = 'E', bldH = 0.8f),
                GtaIsle("LEONIDA KEYS", 40f, 360f, 120f, 400f, bldH = 0.3f),
                GtaIsle("LEONIDA KEYS", 200f, 360f, 280f, 400f, bldH = 0.3f),
                GtaIsle("LEONIDA KEYS", 360f, 360f, 440f, 400f, bldH = 0.3f)),
            listOf(GtaSpan(200f, 114f, 280f, 126f, "VENETIAN CAUSEWAY"), GtaSpan(74f, 280f, 86f, 360f, "OVERSEAS HIGHWAY"),
                GtaSpan(120f, 354f, 200f, 366f, "OVERSEAS HIGHWAY"), GtaSpan(280f, 354f, 360f, 366f, "OVERSEAS HIGHWAY")),
            emptyList(),
            listOf(p("LUCIA'S APARTMENT", "house", 0, 1, 5), p("CORNER STORE", "food", 1, 0, 3), p("AMMU-NATION", "guns", 0, 3, 1)),
            arrayOf("CHEETAH", "INFERNUS", "BANSHEE", "STALLION", "SENTINEL", "BAGGER"), intArrayOf(0xffffff, 0x0f172a, 0xf472b6, 0x22d3ee, 0xa3e635, 0xf59e0b), 0xe11d48,
            0xb98a64, 0xf472b6, 0x1e293b, 0x1c0838, 0xff9a5c, 0x5a2a6a, 0.012f, true,
            intArrayOf(0xffd1e8, 0xbdf5ff, 0xfff3d6, 0xe0c8ff), 0.85f, 0xd8c8a8, true, false, 0xff7ad9, 400)
    )
}

// =====================================================================================================================
class GtaWorld(private val env: Environment, private val sfx: (String) -> Unit, private val release: (Object3D) -> Unit = {}) {
    val input = GtaInput()
    @Volatile var active = false
    lateinit var spec: GtaSpec; private set
    val scene = Scene()
    val cam = PerspectiveCamera(60f, 16f / 9f, 0.1f, 230f)
    val rt = RenderTarget(640, 360)

    val B = 40f          // block pitch (road centre to road centre)
    val R = 12f          // road width
    private class Box(val x0: Float, val z0: Float, val x1: Float, val z1: Float)
    private val solids = ArrayList<Box>()
    private val land = ArrayList<Box>()
    private val rnd = Random(7)
    // map bounds (for the pause map)
    var minX = 0f; var minZ = 0f; var maxX = 1f; var maxZ = 1f

    // ---- actors ----
    private inner class Car(val g: Group, val name: String, val wheels: List<Mesh>) {
        var x = 0f; var z = 0f; var heading = 0f; var speed = 0f
        var ai = true; var parked = false
        var isle = 0
        var dirX = 0; var dirZ = -1           // AI: travel axis
        var cruise = 10f
        var nextTurnAt = Float.NaN
    }
    private inner class Ped(val g: Group, val legL: Mesh, val legR: Mesh, val armL: Mesh, val armR: Mesh, val torso: Mesh) {
        var x = 0f; var z = 0f; var heading = 0f; var walk = 0f
        var down = 0f
        var isle = 0; var bx = 0; var bz = 0; var u = 0f   // AI: block and position around its sidewalk loop
        var speed = 1.3f
    }
    private val cars = ArrayList<Car>()
    private val peds = ArrayList<Ped>()
    private lateinit var hero: Ped
    private var heroCar: Car? = null
    private class Pickup(val mesh: Mesh, val kind: String) { var timer = 0f }
    private val pickups = ArrayList<Pickup>()
    private class Marker(val mesh: Mesh, val place: GtaSpec.Place?, val x: Float, val z: Float, val inside: Boolean, val role: String)
    private val markers = ArrayList<Marker>()
    private val cityGroup = Group()
    private val interior = Group()
    private var insidePlace: GtaSpec.Place? = null
    private var outsideReturn = floatArrayOf(0f, 0f)
    private lateinit var heroLight: PointLight
    private var camYaw = 0f
    private var built = false

    // ---- snapshot for the HUD (read on the UI thread) ----
    @Volatile var money = 0; @Volatile var health = 100f; @Volatile var armour = 0f; @Volatile var wanted = 0
    @Volatile var clockMin = 0f
    @Volatile var px = 0f; @Volatile var pz = 0f; @Volatile var pHeading = 0f; @Volatile var viewYaw = 0f
    @Volatile var inCar = false; @Volatile var nearCar = false
    @Volatile var promptText = ""; @Volatile var zoneName = ""; @Volatile var zoneT = 0f
    @Volatile var vehicleName = ""; @Volatile var vehicleT = 0f
    @Volatile var noticeText = ""; @Volatile var noticeT = 0f
    @Volatile var wastedT = 0f; @Volatile var insideName = ""
    @Volatile var speedKmh = 0
    @Volatile var canAct = false
    var statsDriven = 0f; var statsPickups = 0; var statsPeds = 0; var statsCars = 0
    private var wantedDecay = 0f
    private var lastZone = ""

    private fun isle(i: Int) = spec.isles[i]
    private fun nx(s: GtaIsle) = ((s.x1 - s.x0) / B).roundToInt()
    private fun nz(s: GtaIsle) = ((s.z1 - s.z0) / B).roundToInt()
    private fun bcx(s: GtaIsle, bx: Int) = s.x0 + bx * B + B / 2
    private fun bcz(s: GtaIsle, bz: Int) = s.z0 + bz * B + B / 2
    fun placesForMap() = spec.places.map { val s = isle(it.isle); Triple(it.name, it.kind, floatArrayOf(bcx(s, it.bx), bcz(s, it.bz))) }

    // ================================================================ build
    fun load(key: String, aspect: Float, shortSide: Int) {
        val s = GtaSpecs.ALL[key] ?: GtaSpecs.ALL.getValue("iii")
        val w = if (aspect >= 1) (shortSide * aspect).roundToInt() else shortSide
        val h = if (aspect >= 1) shortSide else (shortSide / aspect).roundToInt()
        rt.setSize(w, h); cam.aspect = aspect; cam.updateProjectionMatrix()
        if (!built || s.key != spec.key) { spec = s; buildCity() }
        input.paused = false
        active = true
    }

    private fun mat(c: Int, side: Int = com.ps1bedroom.room.engine.FRONT_SIDE) = Material(color = Color(c), ps1 = false, side = side)
    private fun basic(c: Int, opacity: Float = 1f) = Material(color = Color(c), lit = false, ps1 = false, transparent = opacity < 1f, opacity = opacity, depthWrite = opacity >= 1f)
    private fun plane(w: Float, d: Float, x: Float, y: Float, z: Float, m: Material, rotZ: Boolean = false): Mesh {
        val pl = Mesh(Geo.plane(w, d), m); pl.rotation.x = (-PI / 2).toFloat(); if (rotZ) pl.rotation.z = (PI / 2).toFloat(); pl.position.set(x, y, z); cityGroup.add(pl); return pl
    }

    private fun windowTex(base: Int, lit: Double, seed: Int): Texture = createCanvasTex(32, 64) { ctx, w, h ->
        ctx.fillStyle = String.format("#%06x", base); ctx.fillRect(0, 0, w, h)
        val r = makeRng(seed)
        var y = 3; while (y < h - 2) { var x = 2; while (x < w - 2) { val v = r(); ctx.fillStyle = if (v < lit) "#ffd88a" else "rgba(12, 16, 28, 0.7)"; ctx.fillRect(x, y, 3, 3); x += 5 }; y += 6 }
    }.tex.also { it.wrapRepeat = true }

    private fun signTex(text: String, bg: Int, fg: String, w: Int = 128): Texture = createCanvasTex(w, 32) { ctx, cw, _ ->
        if (bg >= 0) { ctx.fillStyle = String.format("#%06x", bg); ctx.fillRect(0, 0, cw, 32) } else ctx.clearRect(0, 0, cw, 32)
        ctx.fillStyle = fg; ctx.font = "bold 15px sans-serif"; ctx.textAlign = "center"; ctx.fillText(text, cw / 2, 21, cw - 6)
    }.tex.also { it.nearest = false }

    private fun buildCity() {
        // free the previous city's GPU objects before throwing it away
        if (built) release(scene)
        for (c in ArrayList(scene.children)) scene.remove(c)
        for (c in ArrayList(cityGroup.children)) cityGroup.remove(c)
        for (c in ArrayList(interior.children)) interior.remove(c)
        solids.clear(); land.clear(); cars.clear(); peds.clear(); pickups.clear(); markers.clear()
        val s = spec
        scene.background.setHex(s.sky); scene.fogColor.setHex(s.fog); scene.fogDensity = s.fogD
        scene.add(AmbientLight(0xffffff, if (s.night) 0.42f else 0.6f))
        scene.add(HemisphereLight(s.skyLow, s.ground, if (s.night) 0.25f else 0.45f))
        val sun = SpotLight(if (s.night) 0x8090c0 else 0xfff1d6, if (s.night) 0.35f else 0.9f); sun.angle = 1.55f; sun.penumbra = 0f; sun.distance = 0f
        sun.position.set(-1200f, 3200f, -1600f); scene.add(sun)
        heroLight = PointLight(0xffe0a0, if (s.night) 0.8f else 0f, 14f, 1.5f); scene.add(heroLight)
        scene.add(cityGroup); scene.add(interior)
        interior.visible = false

        minX = s.isles.minOf { it.x0 } - 60f; minZ = s.isles.minOf { it.z0 } - 60f
        maxX = s.isles.maxOf { it.x1 } + 60f; maxZ = s.isles.maxOf { it.z1 } + 60f
        // the sea under everything
        plane(maxX - minX + 600f, maxZ - minZ + 600f, (minX + maxX) / 2, -0.4f, (minZ + maxZ) / 2, mat(if (s.night) 0x0b2a4a else 0x1e78b4))

        val roadTex = createCanvasTex(32, 64, 1f, 1f) { ctx, w, h ->
            ctx.fillStyle = "#ffffff"; ctx.fillRect(0, 0, w, h)
            ctx.fillStyle = "rgba(0,0,0,0.07)"; for (i in 0 until 120) ctx.fillRect(Math.random() * w, Math.random() * h, 1, 1)
            ctx.fillStyle = "#ffe680"; ctx.fillRect(w / 2 - 1, 0, 2, h / 2)
        }.tex.also { it.wrapRepeat = true; it.nearest = false }
        val roadCol = if (s.key == "sa") 0x3a3530 else if (s.key == "iv") 0x2f302c else 0x34363a
        val roadMat = Material(map = roadTex, color = Color(roadCol), ps1 = false)
        val walkMat = mat(0x8b8f96)
        val texes = (0 until 4).map { windowTex(s.bld[it], if (s.night) 0.45 else 0.12, 31 + it + s.key.length * 5) }
        val placeAt = HashMap<String, GtaSpec.Place>(); for (p in s.places) placeAt["${p.isle},${p.bx},${p.bz}"] = p
        val inner = B - R

        s.isles.forEachIndexed { ii, isl ->
            val nx = nx(isl); val nz = nz(isl)
            val lx0 = isl.x0 - R / 2; val lx1 = isl.x1 + R / 2; val lz0 = isl.z0 - R / 2; val lz1 = isl.z1 + R / 2
            land += Box(lx0, lz0, lx1, lz1)
            // the island itself, with a sea wall
            val groundCol = when (isl.kind) { "hills" -> 0x6b7a3a; "airport" -> 0x6f7378; else -> s.ground }
            plane(lx1 - lx0 + 4f, lz1 - lz0 + 4f, (lx0 + lx1) / 2, 0f, (lz0 + lz1) / 2, mat(groundCol))
            if (isl.beach != ' ') {
                val bxx = if (isl.beach == 'E') lx1 + 15f else lx0 - 15f
                plane(30f, lz1 - lz0, bxx, 0.02f, (lz0 + lz1) / 2, mat(0xead7a4))
                land += Box(bxx - 15f, lz0, bxx + 15f, lz1)
                var zz = lz0 + 10f; while (zz < lz1 - 5f) { tree(bxx + (if (isl.beach == 'E') -8f else 8f), zz, true); zz += 24f }
            }
            // streets: every 40 m both ways (the lane texture repeats every 12 m)
            for (i in 0..nx) { val len = isl.z1 - isl.z0 + R; val m = Material(map = roadTex, color = Color(roadCol), ps1 = false); plane(R, len, isl.x0 + i * B, 0.03f, (isl.z0 + isl.z1) / 2, m) }
            for (j in 0..nz) { val len = isl.x1 - isl.x0 + R; plane(R, len, (isl.x0 + isl.x1) / 2, 0.035f, isl.z0 + j * B, roadMat, rotZ = true) }
            // blocks
            for (bx in 0 until nx) for (bz in 0 until nz) {
                val cx = bcx(isl, bx); val cz = bcz(isl, bz)
                val place = placeAt["$ii,$bx,$bz"]
                val slab = Mesh(Geo.box(inner, 0.18f, inner), walkMat); slab.position.set(cx, 0.09f, cz); cityGroup.add(slab)
                if (place != null) { buildPlace(place, cx, cz, inner, texes); continue }
                when {
                    isl.kind == "hills" -> {
                        val hm = mat(if ((bx + bz) % 2 == 0) 0x5f6b33 else 0x6b5a3a)
                        val hill = Mesh(Geo.cone(inner * 0.62f, 10f + ((bx * 7 + bz * 3) % 5) * 3f, 7), hm); hill.position.set(cx, 4f, cz); cityGroup.add(hill)
                        solids += Box(cx - inner * 0.35f, cz - inner * 0.35f, cx + inner * 0.35f, cz + inner * 0.35f)
                        tree(cx - inner / 2 + 2f, cz + inner / 2 - 2f, false)
                    }
                    isl.kind == "airport" -> {
                        val tarmac = Mesh(Geo.box(inner, 0.2f, inner), mat(0x4b5057)); tarmac.position.set(cx, 0.1f, cz); cityGroup.add(tarmac)
                        val stripe = Mesh(Geo.box(inner, 0.21f, 1.2f), basic(0xf8fafc)); stripe.position.set(cx, 0.1f, cz); cityGroup.add(stripe)
                        if ((bx + bz) % 3 == 0) { val hangar = Mesh(Geo.box(inner * 0.6f, 8f, inner * 0.5f), mat(0x9ca3af)); hangar.position.set(cx, 4f, cz - 4f); cityGroup.add(hangar); solids += Box(cx - inner * 0.3f, cz - 4f - inner * 0.25f, cx + inner * 0.3f, cz - 4f + inner * 0.25f) }
                    }
                    (isl.parkZ0 >= 0f && cz > isl.parkZ0 && cz < isl.parkZ1) || (bx * 5 + bz * 3 + ii) % 9 == 4 -> {
                        val grass = Mesh(Geo.box(inner - 4, 0.22f, inner - 4), mat(0x3f7d3a)); grass.position.set(cx, 0.11f, cz); cityGroup.add(grass)
                        for (k in 0 until 4) tree(cx - 7 + (k % 2) * 14, cz - 7 + (k / 2) * 14, s.palms && s.key != "iv")
                    }
                    else -> {
                        for (lx in 0..1) for (lz in 0..1) {
                            val lot = inner / 2 - 2
                            val h = (5f + rnd.nextFloat() * 18f) * s.bldH * isl.bldH
                            val tex = texes[(bx + bz + lx * 2 + lz) % 4]
                            val m = Material(map = tex, emissive = Color(0xffffff), emissiveMap = tex, emissiveIntensity = if (s.night) 0.5f else 0.05f, ps1 = false)
                            val bw = lot - rnd.nextFloat() * 3; val bd = lot - rnd.nextFloat() * 3
                            val x = cx + (lx - 0.5f) * (lot + 2); val z = cz + (lz - 0.5f) * (lot + 2)
                            val b = Mesh(Geo.box(bw, h, bd), m); b.position.set(x, h / 2, z); cityGroup.add(b)
                            solids += Box(x - bw / 2, z - bd / 2, x + bw / 2, z + bd / 2)
                        }
                        if (s.palms) tree(cx - inner / 2 + 1.2f, cz - inner / 2 + 1.2f, true)
                        if ((bx + bz) % 2 == 0) lamp(cx + inner / 2 - 1f, cz - inner / 2 + 1f)
                    }
                }
            }
        }
        // bridges: a deck with railings on piers
        val railMat = mat(0x9ca3af)
        for (b in s.bridges) {
            land += Box(b.x0, b.z0, b.x1, b.z1)
            val alongX = (b.x1 - b.x0) > (b.z1 - b.z0)
            val w = b.x1 - b.x0; val d = b.z1 - b.z0
            val deck = Mesh(Geo.box(w, 0.6f, d), mat(0x4b4f55)); deck.position.set((b.x0 + b.x1) / 2, -0.2f, (b.z0 + b.z1) / 2); cityGroup.add(deck)
            // (the lane texture runs along the plane's length, so an east-west deck is a turned plane)
            if (alongX) plane(d, w, (b.x0 + b.x1) / 2, 0.12f, (b.z0 + b.z1) / 2, roadMat, rotZ = true)
            else plane(w, d, (b.x0 + b.x1) / 2, 0.12f, (b.z0 + b.z1) / 2, Material(map = roadTex, color = Color(roadCol), ps1 = false))
            // railings only over the water, so they never block the shore roads the bridge meets
            val inset = R / 2 + 0.5f
            for (side in intArrayOf(-1, 1)) {
                if (alongX) {
                    val zz = if (side < 0) b.z0 else b.z1
                    val rail = Mesh(Geo.box(w - inset * 2, 1f, 0.3f), railMat); rail.position.set((b.x0 + b.x1) / 2, 0.6f, zz); cityGroup.add(rail)
                    solids += Box(b.x0 + inset, zz - 0.3f, b.x1 - inset, zz + 0.3f)
                } else {
                    val xx = if (side < 0) b.x0 else b.x1
                    val rail = Mesh(Geo.box(0.3f, 1f, d - inset * 2), railMat); rail.position.set(xx, 0.6f, (b.z0 + b.z1) / 2); cityGroup.add(rail)
                    solids += Box(xx - 0.3f, b.z0 + inset, xx + 0.3f, b.z1 - inset)
                }
            }
            val len = if (alongX) w else d
            var t = 10f; while (t < len - 5f) {
                val pier = Mesh(Geo.box(2f, 6f, 2f), mat(0x6b6f76))
                if (alongX) pier.position.set(b.x0 + t, -3.4f, (b.z0 + b.z1) / 2) else pier.position.set((b.x0 + b.x1) / 2, -3.4f, b.z0 + t)
                cityGroup.add(pier); t += 20f
            }
        }
        // VINEWOOD sign on the hills
        s.sign?.let { text ->
            val hills = s.isles.first { it.kind == "hills" }
            val sign = Mesh(Geo.plane(46f, 6f), Material(map = signTex(text, -1, "#f8fafc", 256), lit = false, ps1 = false, transparent = true, depthWrite = false))
            sign.position.set((hills.x0 + hills.x1) / 2, 16f, hills.z0 + 14f); cityGroup.add(sign)
        }
        // pickups on city sidewalks
        val cityIsles = s.isles.indices.filter { s.isles[it].kind == "city" }
        for (i in 0 until 14) {
            val kind = if (i % 3 == 0) "health" else "cash"
            val m = Mesh(if (kind == "cash") Geo.box(0.8f, 0.5f, 0.12f) else Geo.box(0.7f, 0.7f, 0.2f), basic(if (kind == "cash") 0x4ade80 else 0xf43f5e))
            val isl = isle(cityIsles[i % cityIsles.size])
            m.position.set(bcx(isl, rnd.nextInt(nx(isl))) + (rnd.nextFloat() - 0.5f) * (inner - 4), 1f, bcz(isl, rnd.nextInt(nz(isl))) + inner / 2 - 0.8f)
            cityGroup.add(m); pickups += Pickup(m, kind)
        }
        // traffic: spread over the islands, in their lanes, spaced along the streets
        val roadIsles = s.isles.indices.filter { s.isles[it].kind != "airport" }
        for (i in 0 until 16) {
            val c = makeCar(s.carCols[i % s.carCols.size], s.cars[i % s.cars.size])
            val ii = roadIsles[i % roadIsles.size]; val isl = isle(ii)
            c.isle = ii
            val vertical = i % 2 == 0
            val dir = if ((i / 2) % 2 == 0) 1 else -1
            if (vertical) {
                val line = (i / 3) % (nx(isl) + 1)
                c.dirX = 0; c.dirZ = dir; c.x = isl.x0 + line * B + laneOff(0, dir); c.z = bcz(isl, (i / 5) % nz(isl))
            } else {
                val line = (i / 3) % (nz(isl) + 1)
                c.dirX = dir; c.dirZ = 0; c.z = isl.z0 + line * B + laneOff(dir, 0); c.x = bcx(isl, (i / 5) % nx(isl))
            }
            c.heading = atan2(-c.dirX.toFloat(), -c.dirZ.toFloat())
            c.cruise = 8f + rnd.nextFloat() * 5f
            cars += c
        }
        // home: the safehouse, its marker and the player's own car parked at the kerb
        val home = s.places.first { it.kind == "house" }
        val hs = isle(home.isle)
        val homeX = bcx(hs, home.bx); val homeFront = bcz(hs, home.bz) + inner / 2
        val mine = makeCar(s.playerCar, s.cars[4 % s.cars.size])
        mine.ai = false; mine.parked = true; mine.isle = home.isle
        mine.x = homeX + 8f; mine.z = homeFront + R / 2 - 2.6f; mine.heading = (PI / 2).toFloat()
        cars += mine
        // people walking around city blocks
        val shirts = intArrayOf(0xef4444, 0x3b82f6, 0xf8fafc, 0x22c55e, 0xfacc15, 0x111827)
        for (i in 0 until 16) {
            val pd = makePed(intArrayOf(0xd6a77a, 0x8a5a3a, 0xe8c4a0)[i % 3], shirts[i % 6], 0x1f2937, cityGroup)
            pd.isle = cityIsles[i % cityIsles.size]; val isl = isle(pd.isle)
            pd.bx = rnd.nextInt(nx(isl)); pd.bz = rnd.nextInt(nz(isl)); pd.u = rnd.nextFloat() * 4f; pd.speed = 1.1f + rnd.nextFloat() * 0.6f
            peds += pd
        }
        // the hero (on the scene root, so he stays visible indoors), outside the safehouse
        hero = makePed(s.skin, s.shirt, s.pants, scene)
        hero.x = homeX; hero.z = homeFront + 1.5f; hero.heading = PI.toFloat()
        heroCar = null; insidePlace = null; cityGroup.visible = true
        money = s.startCash; health = 100f; armour = 0f; wanted = 0; wastedT = 0f
        clockMin = (env.hour * 60).toFloat()
        camYaw = PI.toFloat(); lastZone = ""
        statsDriven = 0f; statsPickups = 0; statsPeds = 0; statsCars = 0
        built = true
        notice(s.city)
    }
    /** Lane offset from a road's centre line: traffic drives on the right. */
    private fun laneOff(dx: Int, dz: Int): Float = 2.6f * (if (dx != 0) dx.toFloat() else -dz.toFloat())

    private fun tree(x: Float, z: Float, palm: Boolean) {
        val g = Group()
        if (palm) {
            val trunk = Mesh(Geo.cylinder(0.18f, 0.26f, 7f, 5), mat(0x6b4a2b)); trunk.position.y = 3.5f; g.add(trunk)
            val fm = mat(0x2f7d3a, DOUBLE_SIDE)
            for (f in 0 until 6) { val fr = Mesh(Geo.plane(3.2f, 0.6f), fm); fr.position.set(cos(f * 1.05f) * 1.3f, 7f, sin(f * 1.05f) * 1.3f); fr.rotation.set(0f, -f * 1.05f, -0.45f); g.add(fr) }
        } else {
            val trunk = Mesh(Geo.cylinder(0.2f, 0.3f, 3f, 5), mat(0x5b4030)); trunk.position.y = 1.5f; g.add(trunk)
            val top = Mesh(Geo.dodecahedron(2f, 0), mat(0x2f6b34)); top.position.y = 4f; g.add(top)
        }
        g.position.set(x, 0f, z); cityGroup.add(g)
        solids += Box(x - 0.4f, z - 0.4f, x + 0.4f, z + 0.4f)
    }
    private fun lamp(x: Float, z: Float) {
        val g = Group()
        val pole = Mesh(Geo.cylinder(0.08f, 0.1f, 5f, 5), mat(0x3a3f47)); pole.position.y = 2.5f; g.add(pole)
        val head = Mesh(Geo.box(0.6f, 0.15f, 0.3f), basic(0xffd27a)); head.position.set(0f, 5f, 0f); g.add(head)
        g.position.set(x, 0f, z); cityGroup.add(g)
        solids += Box(x - 0.2f, z - 0.2f, x + 0.2f, z + 0.2f)
    }

    /** A named building with its sign and a glowing entry marker on the sidewalk in front (street side = +Z). */
    private fun buildPlace(p: GtaSpec.Place, cx: Float, cz: Float, inner: Float, texes: List<Texture>) {
        val s = spec
        val w = inner - 6; val d = inner - 8
        val h = if (p.kind == "house") 7f else 6f
        val col = when (p.kind) { "house" -> 0x8a6a52; "guns" -> 0x3f4a3a; "food" -> 0xf1e6d0; else -> 0xe8d8f0 }
        val b = Mesh(Geo.box(w, h, d), Material(map = texes[0], color = Color(col), ps1 = false)); b.position.set(cx, h / 2, cz - 2); cityGroup.add(b)
        solids += Box(cx - w / 2, cz - 2 - d / 2, cx + w / 2, cz - 2 + d / 2)
        val front = cz - 2 + d / 2
        val sign = Mesh(Geo.plane(8f, 2f), Material(map = signTex(p.name, if (p.kind == "house") 0x1f2937 else s.accent, if (p.kind == "food") "#7f1d1d" else "#ffffff"), lit = false, ps1 = false))
        sign.position.set(cx, h - 0.8f, front + 0.06f); cityGroup.add(sign)
        val door = Mesh(Geo.box(2f, 2.8f, 0.1f), mat(0x2a1a10)); door.position.set(cx, 1.4f, front + 0.06f); cityGroup.add(door)
        addMarker(cx, front + 2.2f, p, false, "enter")
    }
    private fun addMarker(x: Float, z: Float, p: GtaSpec.Place?, inside: Boolean, role: String) {
        val col = when (role) { "exit" -> 0xfacc15; "buy" -> 0x4ade80; "save" -> 0x60a5fa; else -> if (spec.key == "iii" || spec.key == "lcs") 0xfde047 else 0xf472b6 }
        val m = Mesh(Geo.cylinder(0.9f, 0.9f, 1.4f, 14, 1, true), basic(col, 0.45f)); m.position.set(x, 0.7f, z)
        m.material.side = DOUBLE_SIDE
        (if (inside) interior else cityGroup).add(m)
        markers += Marker(m, p, x, z, inside, role)
    }

    private fun makeCar(col: Int, name: String): Car {
        val g = Group()
        val bodyMat = mat(col)
        val body = Mesh(Geo.box(1.9f, 0.6f, 4.3f), bodyMat); body.position.y = 0.62f
        val cabin = Mesh(Geo.box(1.6f, 0.55f, 2.0f), mat(0x1b2230)); cabin.position.set(0f, 1.18f, 0.15f)
        g.add(body, cabin)
        val wm = mat(0x0b0b0f); val wheels = ArrayList<Mesh>()
        for ((x, z) in arrayOf(-0.95f to 1.4f, 0.95f to 1.4f, -0.95f to -1.4f, 0.95f to -1.4f)) {
            val wh = Mesh(Geo.cylinder(0.38f, 0.38f, 0.3f, 8), wm); wh.rotation.z = (PI / 2).toFloat(); wh.position.set(x, 0.38f, z); g.add(wh); wheels += wh
        }
        val lights = Mesh(Geo.box(1.6f, 0.14f, 4.36f), basic(0x3a0a0a)); lights.position.set(0f, 0.72f, 0f); g.add(lights)
        cityGroup.add(g)
        return Car(g, name, wheels)
    }
    private fun makePed(skin: Int, shirt: Int, pants: Int, parent: Object3D): Ped {
        val g = Group()
        val pm = mat(pants); val sm = mat(shirt); val km = mat(skin)
        val legL = Mesh(Geo.box(0.22f, 0.85f, 0.24f), pm); legL.position.set(-0.13f, 0.43f, 0f)
        val legR = Mesh(Geo.box(0.22f, 0.85f, 0.24f), pm); legR.position.set(0.13f, 0.43f, 0f)
        val torso = Mesh(Geo.box(0.56f, 0.72f, 0.3f), sm); torso.position.y = 1.22f
        val armL = Mesh(Geo.box(0.16f, 0.66f, 0.18f), sm); armL.position.set(-0.37f, 1.2f, 0f)
        val armR = Mesh(Geo.box(0.16f, 0.66f, 0.18f), sm); armR.position.set(0.37f, 1.2f, 0f)
        val head = Mesh(Geo.box(0.3f, 0.32f, 0.3f), km); head.position.y = 1.76f
        g.add(legL, legR, torso, armL, armR, head)
        parent.add(g)
        return Ped(g, legL, legR, armL, armR, torso)
    }

    // ================================================================ simulation
    private fun notice(t: String) { noticeText = t; noticeT = 3.5f }
    private fun onLand(x: Float, z: Float): Boolean { for (b in land) if (x > b.x0 && x < b.x1 && z > b.z0 && z < b.z1) return true; return false }
    /** The district name for a point: a named region, an island's band, a bridge, or the open water. */
    fun districtAt(x: Float, z: Float): String {
        for (r in spec.regions) if (x >= r.x0 && x < r.x1 && z >= r.z0 && z < r.z1) return r.name
        for (b in spec.bridges) if (x >= b.x0 - 1 && x <= b.x1 + 1 && z >= b.z0 - 1 && z <= b.z1 + 1) return b.name
        for (s in spec.isles) if (x >= s.x0 - R && x <= s.x1 + R && z >= s.z0 - R && z <= s.z1 + R) {
            val alongZ = (s.z1 - s.z0) >= (s.x1 - s.x0)
            val t = if (alongZ) (z - s.z0) / (s.z1 - s.z0) else (x - s.x0) / (s.x1 - s.x0)
            return s.districts[(t.coerceIn(0f, 0.999f) * s.districts.size).toInt()]
        }
        return spec.city
    }

    fun update(dt0: Float) {
        if (!built) return
        val dt = min(dt0, 0.05f)
        if (input.paused) return
        clockMin = (clockMin + dt) % 1440f
        zoneT -= dt; vehicleT -= dt; noticeT -= dt
        camYaw -= input.takeCam() * 0.006f
        if (wastedT > 0f) {
            wastedT -= dt
            if (wastedT <= 0f) respawn()
            updateCamera(dt); return
        }
        val action = input.action; input.action = false
        if (insidePlace != null) updateInside(dt, action) else updateOutside(dt, action)
        updateCamera(dt)
        px = hero.x; pz = hero.z; viewYaw = camYaw
        pHeading = heroCar?.heading ?: hero.heading
    }

    private fun updateOutside(dt: Float, action: Boolean) {
        val car = heroCar
        if (car != null) driveHero(car, dt) else walkHero(dt)
        nearCar = false
        if (car == null) {
            val near = cars.minByOrNull { hypot(it.x - hero.x, it.z - hero.z) }
            if (near != null && hypot(near.x - hero.x, near.z - hero.z) < 3.6f) {
                nearCar = true
                if (action) {
                    if (near.ai) { statsCars++; if (wanted < 1) { wanted = 1; wantedDecay = 25f } }
                    near.ai = false; near.parked = false; near.speed = 0f
                    heroCar = near; hero.g.visible = false; inCar = true
                    vehicleName = near.name; vehicleT = 3f; sfx("door")
                }
            }
        } else if (action && abs(car.speed) < 6f) {
            car.speed = 0f; car.parked = true; heroCar = null; inCar = false
            val ex = car.x - cos(car.heading) * 2.2f; val ez = car.z + sin(car.heading) * 2.2f
            if (onLand(ex, ez)) { hero.x = ex; hero.z = ez } else { hero.x = car.x + cos(car.heading) * 2.2f; hero.z = car.z - sin(car.heading) * 2.2f }
            hero.g.visible = true; sfx("door")
        }
        updateTraffic(dt)
        updatePeds(dt)
        for (pk in pickups) {
            val m = pk.mesh
            if (!m.visible) { pk.timer -= dt; if (pk.timer <= 0f) m.visible = true; continue }
            m.rotation.y += dt * 2.5f; m.position.y = 1f + sin(clockMin * 3f) * 0.15f
            if (hypot(m.position.x - hero.x, m.position.z - hero.z) < 1.8f) {
                m.visible = false; pk.timer = 40f; statsPickups++; sfx("ui")
                if (pk.kind == "cash") { val add = 100 + rnd.nextInt(5) * 50; money += add; notice("+\$$add") } else { health = min(100f, health + 40f); notice("HEALTH") }
            }
        }
        promptText = ""
        for (mk in markers) if (!mk.inside) {
            mk.mesh.rotation.y += dt
            val pl = mk.place ?: continue
            val d = hypot(mk.x - hero.x, mk.z - hero.z)
            if (heroCar == null && d < 1.3f) { enterPlace(pl); return }
            if (d < 7f) promptText = pl.name
        }
        canAct = heroCar != null || nearCar
        if (wanted > 0) { wantedDecay -= dt; if (wantedDecay <= 0f) { wanted--; wantedDecay = 20f } }
        val zn = districtAt(hero.x, hero.z)
        if (zn != lastZone) { lastZone = zn; zoneName = zn; zoneT = 3.5f }
        heroLight.position.set(hero.x, 6f, hero.z)
        if (health <= 0f) { wastedT = 4f; health = 0f; heroCar?.speed = 0f; sfx("caseClick") }
    }

    private fun walkHero(dt: Float) {
        val jx = input.joyX; val jy = input.joyY
        val mag = min(1f, hypot(jx, jy))
        val ox = hero.x; val oz = hero.z
        if (mag > 0.12f) {
            val fwdX = -sin(camYaw); val fwdZ = -cos(camYaw)
            val rx = cos(camYaw); val rz = -sin(camYaw)
            val mx = rx * jx - fwdX * jy; val mz = rz * jx - fwdZ * jy
            val sp = if (mag > 0.85f) 6.5f else 3.2f
            val l = max(0.001f, hypot(mx, mz))
            hero.x += mx / l * sp * mag * dt; hero.z += mz / l * sp * mag * dt
            hero.heading += angleDiff(hero.heading, atan2(-mx, -mz)) * min(1f, dt * 10f)
            hero.walk += dt * sp * 2.2f
        } else hero.walk = 0f
        if (insidePlace == null) {
            pushOut(hero, 0.4f)
            for (c in cars) { val d = hypot(c.x - hero.x, c.z - hero.z); if (d < 1.9f && d > 0.001f) { val k = (1.9f - d) / d; hero.x -= (c.x - hero.x) * k; hero.z -= (c.z - hero.z) * k } }
            if (!onLand(hero.x, hero.z)) { hero.x = ox; hero.z = oz }   // the water's edge
        }
        posePed(hero)
    }
    private fun angleDiff(a: Float, b: Float): Float { var d = (b - a) % (2 * PI.toFloat()); if (d > PI) d -= 2 * PI.toFloat(); if (d < -PI) d += 2 * PI.toFloat(); return d }

    private fun driveHero(c: Car, dt: Float) {
        val maxV = when (spec.key) { "v", "vi" -> 34f; "iv" -> 28f; else -> 30f }
        when {
            input.gas -> c.speed += (if (c.speed < 0) 18f else 11f) * dt
            input.brake -> c.speed -= (if (c.speed > 0) 20f else 7f) * dt
            else -> c.speed -= c.speed * min(1f, dt * 0.8f)
        }
        c.speed = c.speed.coerceIn(-9f, maxV)
        c.heading += -input.joyX * 2.1f * dt * (c.speed / 10f).coerceIn(-1f, 1f)
        val ox = c.x; val oz = c.z
        c.x += -sin(c.heading) * c.speed * dt; c.z += -cos(c.heading) * c.speed * dt
        // buildings and the sea wall: stop dead and bounce back a little
        if (hitsSolid(c.x, c.z, 1.4f) || !onLand(c.x, c.z)) {
            if (abs(c.speed) > 12f) { health -= abs(c.speed) * 0.4f; sfx("caseClick") }
            c.x = ox; c.z = oz; c.speed *= -0.3f
        }
        for (o in cars) if (o !== c) {
            val dx = o.x - c.x; val dz = o.z - c.z
            val d = hypot(dx, dz)
            if (d < 3.3f && d > 0.001f) {
                val k = (3.3f - d) / d * 0.5f
                c.x -= dx * k; c.z -= dz * k; o.x += dx * k; o.z += dz * k
                if (abs(c.speed) > 10f) health -= abs(c.speed) * 0.15f
                c.speed *= 0.4f; o.speed *= 0.3f
            }
        }
        if (abs(c.speed) > 5f) for (p in peds) if (p.down <= 0f && hypot(p.x - c.x, p.z - c.z) < 1.8f) {
            p.down = 8f; statsPeds++; wanted = min(3, wanted + 1); wantedDecay = 30f; sfx("caseClick")
        }
        statsDriven += abs(c.speed) * dt
        speedKmh = (abs(c.speed) * 3.6f).roundToInt()
        hero.x = c.x; hero.z = c.z
        poseCar(c, dt)
    }

    private fun hitsSolid(x: Float, z: Float, r: Float): Boolean {
        for (b in solids) if (x + r > b.x0 && x - r < b.x1 && z + r > b.z0 && z - r < b.z1) return true
        return false
    }
    private fun pushOut(p: Ped, r: Float) {
        for (b in solids) {
            if (p.x + r > b.x0 && p.x - r < b.x1 && p.z + r > b.z0 && p.z - r < b.z1) {
                val l = p.x + r - b.x0; val rr = b.x1 - (p.x - r); val t = p.z + r - b.z0; val bb = b.z1 - (p.z - r)
                val m = minOf(minOf(l, rr), minOf(t, bb))
                when (m) { l -> p.x -= l; rr -> p.x += rr; t -> p.z -= t; else -> p.z += bb }
            }
        }
    }

    /** Traffic: keeps to its lane on its island's street grid, turns at junctions, and stops for anything ahead. */
    private fun updateTraffic(dt: Float) {
        for (c in cars) {
            if (!c.ai) { if (c !== heroCar) poseCar(c, dt); continue }
            val isl = isle(c.isle)
            val fx = c.dirX.toFloat(); val fz = c.dirZ.toFloat()
            var target = c.cruise
            fun blockIfAhead(ox: Float, oz: Float, gap: Float) {
                val dx = ox - c.x; val dz = oz - c.z
                val along = dx * fx + dz * fz; val side = abs(dx * fz - dz * fx)
                if (along > 0f && along < gap && side < 2.4f) target = min(target, max(0f, (along - 5.5f) * 1.2f))
            }
            for (o in cars) if (o !== c) blockIfAhead(o.x, o.z, 14f)
            blockIfAhead(hero.x, hero.z, 12f)
            for (p in peds) if (p.down <= 0f) blockIfAhead(p.x, p.z, 8f)
            c.speed += (target - c.speed) * min(1f, dt * (if (target < c.speed) 4f else 1.2f))
            c.x += fx * c.speed * dt; c.z += fz * c.speed * dt
            // junction: at the next cross street, go straight or turn onto it (never off the island)
            val alongX = c.dirX != 0
            val origin = if (alongX) isl.x0 else isl.z0
            val nAlong = if (alongX) nx(isl) else nz(isl)
            val nCross = if (alongX) nz(isl) else nx(isl)
            val rel = (if (alongX) c.x else c.z) - origin
            val dir = if (alongX) c.dirX else c.dirZ
            val roadLine = (((if (alongX) c.z - isl.z0 else c.x - isl.x0)) / B).roundToInt().coerceIn(0, nCross)
            if (c.nextTurnAt.isNaN()) c.nextTurnAt = (if (dir > 0) floor(rel / B) + 1 else kotlin.math.ceil(rel / B) - 1) * B
            if ((dir > 0 && rel >= c.nextTurnAt) || (dir < 0 && rel <= c.nextTurnAt)) {
                val lIdx = (c.nextTurnAt / B).roundToInt().coerceIn(0, nAlong)
                val opts = ArrayList<Int>(4)
                if ((dir > 0 && lIdx < nAlong) || (dir < 0 && lIdx > 0)) { opts += 0; opts += 0 }
                for (nd in intArrayOf(-1, 1)) if ((nd > 0 && roadLine < nCross) || (nd < 0 && roadLine > 0)) opts += nd
                val pick = if (opts.isEmpty()) 2 else opts[rnd.nextInt(opts.size)]
                val atLine = origin + lIdx * B
                when {
                    pick == 2 -> { c.dirX = -c.dirX; c.dirZ = -c.dirZ; if (alongX) c.z = isl.z0 + roadLine * B + laneOff(c.dirX, 0) else c.x = isl.x0 + roadLine * B + laneOff(0, c.dirZ) }
                    pick != 0 && alongX -> { c.dirX = 0; c.dirZ = pick; c.x = atLine + laneOff(0, pick); c.z = isl.z0 + roadLine * B }
                    pick != 0 -> { c.dirZ = 0; c.dirX = pick; c.z = atLine + laneOff(pick, 0); c.x = isl.x0 + roadLine * B }
                }
                c.nextTurnAt = Float.NaN
            }
            c.heading += angleDiff(c.heading, atan2(-c.dirX.toFloat(), -c.dirZ.toFloat())) * min(1f, dt * 6f)
            poseCar(c, dt)
        }
        // safety net: an AI car that ever overlaps another is pushed apart
        for (i in cars.indices) for (j in i + 1 until cars.size) {
            val a = cars[i]; val b = cars[j]
            if (!a.ai && !b.ai) continue
            val dx = b.x - a.x; val dz = b.z - a.z
            val d = hypot(dx, dz)
            if (d < 3.2f && d > 0.001f) {
                val k = (3.2f - d) / d * (if (a.ai && b.ai) 0.5f else 1f)
                if (a.ai) { a.x -= dx * k; a.z -= dz * k }
                if (b.ai) { b.x += dx * k; b.z += dz * k }
            }
        }
    }
    private fun poseCar(c: Car, dt: Float) {
        c.g.position.set(c.x, 0f, c.z); c.g.rotation.y = c.heading
        for (w in c.wheels) w.rotation.x -= c.speed * dt * 2.6f
    }

    private fun updatePeds(dt: Float) {
        val half = (B - R) / 2 - 0.9f
        for (p in peds) {
            if (p.down > 0f) {
                p.down -= dt
                p.g.rotation.x = -(PI / 2).toFloat(); p.g.position.set(p.x, 0.3f, p.z)
                if (p.down <= 0f) { p.g.rotation.x = 0f; val isl = isle(p.isle); p.bx = rnd.nextInt(nx(isl)); p.bz = rnd.nextInt(nz(isl)) }
                continue
            }
            val blocked = hypot(hero.x - p.x, hero.z - p.z) < 1.2f
            if (!blocked) p.u = (p.u + p.speed * dt / (half * 2)) % 4f
            val side = p.u.toInt(); val f = p.u - side
            val isl = isle(p.isle)
            val cx = bcx(isl, p.bx); val cz = bcz(isl, p.bz)
            when (side) {
                0 -> { p.x = cx - half + f * half * 2; p.z = cz - half; p.heading = -(PI / 2).toFloat() }
                1 -> { p.x = cx + half; p.z = cz - half + f * half * 2; p.heading = PI.toFloat() }
                2 -> { p.x = cx + half - f * half * 2; p.z = cz + half; p.heading = (PI / 2).toFloat() }
                else -> { p.x = cx - half; p.z = cz + half - f * half * 2; p.heading = 0f }
            }
            p.walk = if (blocked) 0f else p.walk + dt * p.speed * 3f
            posePed(p)
        }
    }
    private fun posePed(p: Ped) {
        val sw = sin(p.walk) * 0.7f
        p.legL.rotation.x = sw; p.legR.rotation.x = -sw; p.armL.rotation.x = -sw * 0.8f; p.armR.rotation.x = sw * 0.8f
        p.g.position.set(p.x, if (insidePlace != null && p === hero) 0f else 0.18f, p.z); p.g.rotation.y = p.heading
    }

    // ---- interiors (built far from the city, around 3000, 3000) ----
    private val IX = 3000f
    private fun enterPlace(p: GtaSpec.Place) {
        insidePlace = p; insideName = p.name
        outsideReturn = floatArrayOf(hero.x, hero.z + 3.5f)
        buildInterior(p)
        cityGroup.visible = false; interior.visible = true
        hero.x = IX; hero.z = IX + 2.8f; hero.heading = 0f; camYaw = 0f
        scene.fogDensity = 0f
        sfx("door"); notice(p.name)
    }
    private fun leavePlace() {
        insidePlace = null; insideName = ""
        interior.visible = false; cityGroup.visible = true
        hero.x = outsideReturn[0]; hero.z = outsideReturn[1]; hero.heading = PI.toFloat(); camYaw = PI.toFloat()
        scene.fogDensity = spec.fogD
        markers.removeAll { it.inside }
        sfx("door")
    }
    private fun buildInterior(p: GtaSpec.Place) {
        release(interior)
        for (c in ArrayList(interior.children)) interior.remove(c)
        markers.removeAll { it.inside }
        val ox = IX; val oz = IX
        val wallCol = when (p.kind) { "house" -> 0x9c8a74; "guns" -> 0x4a5040; "food" -> 0xf3ead8; else -> 0xe9e0f2 }
        val floorCol = when (p.kind) { "house" -> 0x6b4a33; "food" -> 0xb91c1c; "guns" -> 0x3a3a3a; else -> 0xd6d3d1 }
        fun box(w: Float, h: Float, d: Float, x: Float, y: Float, z: Float, c: Int, glow: Boolean = false) {
            val m = Mesh(Geo.box(w, h, d), if (glow) basic(c) else mat(c)); m.position.set(ox + x, y, oz + z); interior.add(m)
        }
        box(12f, 0.2f, 12f, 0f, -0.1f, 0f, floorCol)
        box(12f, 4f, 0.2f, 0f, 2f, -6f, wallCol); box(12f, 4f, 0.2f, 0f, 2f, 6f, wallCol)
        box(0.2f, 4f, 12f, -6f, 2f, 0f, wallCol); box(0.2f, 4f, 12f, 6f, 2f, 0f, wallCol)
        box(12f, 0.2f, 12f, 0f, 4.1f, 0f, 0xd6d3d1)
        box(2f, 0.1f, 0.4f, 0f, 3.95f, 0f, 0xfff6d6, true)
        box(2f, 2.8f, 0.1f, 0f, 1.4f, 5.85f, 0x2a1a10)
        when (p.kind) {
            "house" -> {
                box(2.2f, 0.6f, 3.4f, -4f, 0.3f, -3.6f, 0x7f1d1d); box(2.2f, 0.25f, 0.6f, -4f, 0.75f, -5.1f, 0xf8fafc)
                box(2.6f, 0.9f, 1f, 3.5f, 0.45f, -5f, 0x3b2a1e); box(1.6f, 1.1f, 0.2f, 3.5f, 1.5f, -5.4f, 0x60a5fa, true)
                box(3.2f, 0.8f, 1.2f, 3.2f, 0.4f, 0.5f, spec.accent); box(4f, 0.05f, 3f, 0f, 0.03f, 0f, 0x334155)
                addMarker(ox - 2f, oz - 2.5f, p, true, "save")
            }
            "food" -> {
                box(8f, 1.1f, 1f, 0f, 0.55f, -3f, 0xfacc15); box(8f, 1.2f, 0.2f, 0f, 3.1f, -5.8f, 0x7f1d1d, true)
                for (i in 0 until 3) box(1.4f, 0.8f, 1.4f, -3f + i * 3f, 0.4f, 2.5f, 0xe5e7eb)
                addMarker(ox, oz - 1.8f, p, true, "buy")
            }
            "guns" -> {
                box(8f, 1.1f, 1f, 0f, 0.55f, -3f, 0x1f2937)
                for (i in 0 until 4) box(1.8f, 2.6f, 0.4f, -4.5f + i * 3f, 1.6f, -5.6f, 0x57534e)
                addMarker(ox, oz - 1.8f, p, true, "buy")
            }
            else -> {
                for (i in 0 until 4) { box(0.2f, 2f, 3f, -4.5f + i * 3f, 1f, -3f, 0x9ca3af); for (k in 0 until 3) box(0.5f, 0.9f, 0.1f, -4.5f + i * 3f, 1.4f, -4f + k, intArrayOf(0xef4444, 0x3b82f6, 0x22c55e, 0xfacc15)[(i + k) % 4]) }
                addMarker(ox + 3f, oz - 1f, p, true, "buy")
            }
        }
        addMarker(ox, oz + 5.2f, p, true, "exit")
    }
    private fun updateInside(dt: Float, action: Boolean) {
        walkHero(dt)
        hero.x = hero.x.coerceIn(IX - 5.4f, IX + 5.4f); hero.z = hero.z.coerceIn(IX - 5.4f, IX + 5.4f)
        posePed(hero)
        promptText = ""; canAct = false
        val p = insidePlace ?: return
        for (mk in markers) if (mk.inside) {
            mk.mesh.rotation.y += dt
            if (hypot(mk.x - hero.x, mk.z - hero.z) > 1.3f) continue
            when (mk.role) {
                "exit" -> { leavePlace(); return }
                "save" -> {
                    promptText = "SLEEP / SAVE GAME"; canAct = true
                    if (action) { clockMin = (clockMin + 360f) % 1440f; health = 100f; notice("GAME SAVED · 6 HOURS LATER"); sfx("ui") }
                }
                "buy" -> {
                    val (label, cost) = when (p.kind) { "food" -> "MEAL" to 10; "guns" -> "BODY ARMOUR" to 100; else -> "NEW OUTFIT" to 50 }
                    promptText = "$label  \$$cost"; canAct = true
                    if (action) {
                        if (money < cost) notice("NOT ENOUGH MONEY") else {
                            money -= cost; sfx("ui")
                            when (p.kind) {
                                "food" -> { health = min(100f, health + 50f); notice("HEALTH +50") }
                                "guns" -> { armour = 100f; notice("ARMOUR 100") }
                                else -> { hero.torso.material.color.setHex(intArrayOf(0xef4444, 0x22c55e, 0xfacc15, 0x8b5cf6, 0xf8fafc, 0x0ea5e9)[rnd.nextInt(6)]); notice("LOOKING GOOD") }
                            }
                        }
                    }
                }
            }
            return
        }
    }

    private fun respawn() {
        val home = spec.places.first { it.kind == "house" }
        val hs = isle(home.isle)
        heroCar?.parked = true; heroCar = null; inCar = false
        hero.g.visible = true
        hero.x = bcx(hs, home.bx); hero.z = bcz(hs, home.bz) + (B - R) / 2 + 1.5f
        health = 100f; armour = 0f; wanted = 0; money = max(0, money - 100)
        notice("HOSPITAL · -\$100")
    }

    private val camPos = Vec3(); private val camLook = Vec3()
    private fun updateCamera(dt: Float) {
        val car = heroCar
        if (car != null && abs(car.speed) > 2f) camYaw += angleDiff(camYaw, car.heading) * min(1f, dt * 2.5f)
        val inside = insidePlace != null
        val dist = if (car != null) 9f else if (inside) 4.2f else 5.5f
        val hgt = if (car != null) 3.6f else 2.6f
        var cx = hero.x + sin(camYaw) * dist; var cz = hero.z + cos(camYaw) * dist
        if (inside) { cx = cx.coerceIn(IX - 5.2f, IX + 5.2f); cz = cz.coerceIn(IX - 5.2f, IX + 5.2f) }
        camPos.set(cx, hgt, cz)
        if (hypot(cam.position.x - camPos.x, cam.position.z - camPos.z) > 30f) cam.position.copy(camPos) else cam.position.lerp(camPos, min(1f, dt * 8f))
        cam.lookAtTarget(camLook.set(hero.x, if (car != null) 1.2f else 1.4f, hero.z))
        cam.updateCameraWorld()
    }
}

// =====================================================================================================================
/** The HUD, pause menu, map and touch controls, drawn in each game's own style. */
class GtaPlayView(private val act: Activity, private val root: FrameLayout, private val onQuit: () -> Unit) : View(act) {
    var world: GtaWorld? = null
    private val dp = act.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val PRICEDOWN = Typeface.create(Typeface.create("sans-serif-condensed", Typeface.BOLD), Typeface.BOLD)
    private val RAGE = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
    private val CLEAN = Typeface.create("sans-serif", Typeface.NORMAL)
    private val CLEAN_B = Typeface.create("sans-serif-medium", Typeface.BOLD)
    private val BANK = Typeface.create(Typeface.create("sans-serif-condensed", Typeface.NORMAL), Typeface.BOLD)
    val isOpen get() = visibility == VISIBLE

    private var joyId = -1; private var joyCx = 0f; private var joyCy = 0f; private var joyX = 0f; private var joyY = 0f
    private var camId = -1; private var camLastX = 0f
    private val gasR = RectF(); private val brakeR = RectF(); private val actR = RectF(); private val pauseR = RectF()
    private val menuItems = ArrayList<Pair<RectF, String>>()
    private var menuPage = "main"   // main · map · stats · help
    private var gasId = -1; private var brakeId = -1
    private val tmpR = RectF()
    private val clipPath = Path()

    init { visibility = GONE; elevation = 55f; root.addView(this, FrameLayout.LayoutParams(-1, -1)) }

    fun open() { visibility = VISIBLE; bringToFront(); menuPage = "main"; postInvalidateOnAnimation() }
    fun close() { visibility = GONE; joyId = -1; camId = -1; world?.input?.let { it.joyX = 0f; it.joyY = 0f; it.gas = false; it.brake = false } }
    fun back(): Boolean {
        val w = world ?: return false
        if (w.input.paused) { if (menuPage != "main") menuPage = "main" else w.input.paused = false } else w.input.paused = true
        return true
    }

    private fun hex(c: Int) = (0xff shl 24) or c
    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, col: Int, tf: Typeface, align: Paint.Align = Paint.Align.LEFT, outline: Int? = 0xff000000.toInt(), ow: Float = 3f) {
        tp.typeface = tf; tp.textSize = size * dp; tp.textAlign = align
        if (outline != null) { tp.style = Paint.Style.STROKE; tp.strokeWidth = ow * dp; tp.color = outline; c.drawText(s, x, y, tp) }
        tp.style = Paint.Style.FILL; tp.color = col; c.drawText(s, x, y, tp)
    }
    private fun clockStr(w: GtaWorld): String { val m = w.clockMin.toInt(); return String.format(java.util.Locale.US, "%02d:%02d", m / 60, m % 60) }
    private fun moneyStr(w: GtaWorld, pad: Boolean) = if (pad) "$" + w.money.toString().padStart(8, '0') else "$" + String.format(java.util.Locale.US, "%,d", w.money)

    override fun onDraw(c: Canvas) {
        val w = world ?: return
        val W = width.toFloat(); val H = height.toFloat()
        // (a drawing error must never take the whole app down: record it and keep going)
        try {
            if (w.wastedT > 0f) drawWasted(c, w, W, H) else { drawHud(c, w, W, H); drawControls(c, w, W, H) }
            if (w.input.paused) drawPause(c, w, W, H)
        } catch (e: Throwable) { CrashLog.recordNonFatal("GTA HUD", e) }
        if (isOpen) postInvalidateOnAnimation()
    }

    // ---------------------------------------------------------------- HUDs
    private fun drawHud(c: Canvas, w: GtaWorld, W: Float, H: Float) {
        val s = w.spec
        val m = 14 * dp
        val radarR = 58 * dp
        val rcx = m + radarR + 6 * dp; val rcy = H - m - radarR - 6 * dp
        when (s.hud) {
            "iii" -> {
                fist(c, W - m - 54 * dp, m, 54 * dp, 0xff9ca3af.toInt())
                val rx = W - m - 62 * dp
                text(c, clockStr(w), rx, m + 20 * dp, 20f, hex(0xf0a030), BANK, Paint.Align.RIGHT)
                text(c, moneyStr(w, true), rx, m + 42 * dp, 20f, hex(0x90b5d9), BANK, Paint.Align.RIGHT)
                text(c, "♥" + w.health.roundToInt().toString().padStart(3, '0'), rx, m + 64 * dp, 18f, hex(0xff9cb5), BANK, Paint.Align.RIGHT)
                if (w.armour > 0) text(c, "⛨" + w.armour.roundToInt().toString().padStart(3, '0'), rx - 70 * dp, m + 64 * dp, 18f, hex(0x7fb36a), BANK, Paint.Align.RIGHT)
                stars(c, w, rx, m + 86 * dp, 16f, hex(0xd8b04a))
                radar(c, w, rcx, rcy, radarR, hex(0x1a1a1a))
            }
            "vc" -> {
                fist(c, W - m - 54 * dp, m, 54 * dp, 0xffe879b8.toInt(), round = true)
                val rx = W - m - 62 * dp
                text(c, clockStr(w), rx, m + 20 * dp, 21f, hex(0xff8fd0), RAGE, Paint.Align.RIGHT)
                text(c, moneyStr(w, true), rx, m + 43 * dp, 21f, hex(0x6ac7f0), RAGE, Paint.Align.RIGHT)
                text(c, "♥" + w.health.roundToInt(), rx, m + 65 * dp, 19f, hex(0xff8fc0), RAGE, Paint.Align.RIGHT)
                if (w.armour > 0) text(c, "⛨" + w.armour.roundToInt(), rx - 64 * dp, m + 65 * dp, 19f, hex(0x9fdcff), RAGE, Paint.Align.RIGHT)
                stars(c, w, rx, m + 88 * dp, 17f, hex(0xffc4e6))
                radar(c, w, rcx, rcy, radarR, hex(0xf472b6))
            }
            "sa" -> {
                fist(c, W - m - 168 * dp, m, 56 * dp, 0xffd1d5db.toInt(), round = true)
                val rx = W - m
                text(c, clockStr(w), rx, m + 22 * dp, 22f, hex(0xe5e7eb), PRICEDOWN, Paint.Align.RIGHT)
                bar(c, rx - 104 * dp, m + 30 * dp, 104 * dp, 9 * dp, w.health / 100f, hex(0xb4191d))
                if (w.armour > 0) bar(c, rx - 104 * dp, m + 43 * dp, 104 * dp, 9 * dp, w.armour / 100f, hex(0xe1e1e1))
                text(c, moneyStr(w, true), rx, m + 76 * dp, 24f, hex(0x36682c), PRICEDOWN, Paint.Align.RIGHT)
                stars(c, w, rx, m + 98 * dp, 18f, hex(0x906210))
                radar(c, w, rcx, rcy, radarR, hex(0x000000))
            }
            "lcs", "vcs" -> {
                val pink = s.hud == "vcs"
                fist(c, W - m - 54 * dp, m, 54 * dp, if (pink) 0xfff472b6.toInt() else 0xffd1d5db.toInt(), round = true)
                val rx = W - m - 62 * dp
                text(c, clockStr(w), rx, m + 18 * dp, 18f, if (pink) hex(0xff9fd8) else hex(0xf5f5f4), if (pink) RAGE else BANK, Paint.Align.RIGHT)
                bar(c, rx - 96 * dp, m + 26 * dp, 96 * dp, 8 * dp, w.health / 100f, if (pink) hex(0xff4f9a) else hex(0xd03030))
                if (w.armour > 0) bar(c, rx - 96 * dp, m + 38 * dp, 96 * dp, 8 * dp, w.armour / 100f, hex(0x60a5fa))
                text(c, moneyStr(w, true), rx, m + 66 * dp, 20f, if (pink) hex(0x7fdcff) else hex(0x7fc97f), if (pink) RAGE else BANK, Paint.Align.RIGHT)
                stars(c, w, rx, m + 88 * dp, 16f, if (pink) hex(0xffc4e6) else hex(0xd8b04a))
                radar(c, w, rcx, rcy, radarR, if (pink) hex(0xf472b6) else hex(0x9ca3af))
            }
            "iv" -> {
                radar(c, w, rcx, rcy, radarR, hex(0x404040))
                arc(c, rcx, rcy, radarR + 6 * dp, 95f, 170f * (w.health / 100f), hex(0x6fc24a))
                if (w.armour > 0) arc(c, rcx, rcy, radarR + 6 * dp, 85f, -170f * (w.armour / 100f), hex(0x4aa3df))
                fist(c, W - m - 50 * dp, m, 50 * dp, 0xffe5e7eb.toInt())
                text(c, moneyStr(w, false), W - m - 58 * dp, m + 30 * dp, 20f, hex(0x9bd38a), CLEAN_B, Paint.Align.RIGHT, ow = 2f)
                stars(c, w, W - m - 58 * dp, m + 54 * dp, 16f, hex(0xffffff))
            }
            "v" -> {
                val mw = 170 * dp; val mh = 110 * dp
                val left = m; val top = H - m - mh - 12 * dp
                radarRect(c, w, left, top, mw, mh)
                bar(c, left, top + mh + 4 * dp, mw * 0.48f, 6 * dp, w.health / 100f, hex(0x52a447), bg = hex(0x1e3a1e))
                bar(c, left + mw * 0.5f, top + mh + 4 * dp, mw * 0.24f, 6 * dp, w.armour / 100f, hex(0x3d8fd8), bg = hex(0x1b2f45))
                bar(c, left + mw * 0.76f, top + mh + 4 * dp, mw * 0.24f, 6 * dp, 0.7f, hex(0xe6c52e), bg = hex(0x3d3415))
                text(c, moneyStr(w, false), W - m, m + 28 * dp, 24f, hex(0xffffff), PRICEDOWN, Paint.Align.RIGHT, ow = 2.5f)
                stars(c, w, W - m, m + 52 * dp, 17f, hex(0xffffff))
            }
            else -> {
                val mw = 150 * dp; val mh = 100 * dp
                val left = m; val top = H - m - mh
                radarRect(c, w, left, top, mw, mh, rounded = 16 * dp)
                bar(c, left + 8 * dp, top + mh - 10 * dp, mw * 0.4f, 4 * dp, w.health / 100f, hex(0xffffff), bg = 0x55000000)
                text(c, moneyStr(w, false), W - m, m + 26 * dp, 20f, hex(0xffffff), CLEAN_B, Paint.Align.RIGHT, outline = 0x88000000.toInt(), ow = 2f)
                stars(c, w, W - m, m + 48 * dp, 14f, hex(0xffffff))
            }
        }
        val zoneTf = when (s.hud) { "vc", "vcs" -> RAGE; "sa" -> PRICEDOWN; "iv", "v", "vi" -> CLEAN_B; else -> BANK }
        val zoneCol = when (s.hud) { "vc", "vcs" -> hex(0xff8fd0); "sa" -> hex(0x5a7fbf); "iii", "lcs" -> hex(0x6ab04c); else -> hex(0xffffff) }
        if (w.zoneT > 0f) text(c, w.zoneName, W - m, H - m - 6 * dp, 24f, zoneCol, zoneTf, Paint.Align.RIGHT)
        if (w.vehicleT > 0f) text(c, w.vehicleName, W - m, H - m - 36 * dp, 20f, if (s.hud == "sa") hex(0x6b9b3a) else hex(0x93c5fd), zoneTf, Paint.Align.RIGHT)
        if (w.promptText.isNotEmpty()) {
            tp.typeface = CLEAN_B; tp.textSize = 15 * dp; val tw = tp.measureText(w.promptText) + 30 * dp
            p.color = 0xcc000000.toInt(); tmpR.set(W / 2 - tw / 2, m, W / 2 + tw / 2, m + 32 * dp); c.drawRoundRect(tmpR, 8 * dp, 8 * dp, p)
            text(c, w.promptText, W / 2, m + 22 * dp, 15f, hex(0xffffff), CLEAN_B, Paint.Align.CENTER, outline = null)
        }
        if (w.noticeT > 0f) text(c, w.noticeText, W / 2, H * 0.3f, 26f, hex(s.accent), zoneTf, Paint.Align.CENTER)
        if (w.inCar) text(c, "${w.speedKmh} KM/H", W - m, H - m - 64 * dp, 13f, hex(0xffffff), CLEAN_B, Paint.Align.RIGHT, ow = 2f)
    }

    private fun fist(c: Canvas, x: Float, y: Float, size: Float, col: Int, round: Boolean = false) {
        p.style = Paint.Style.FILL; p.color = 0x66000000
        if (round) c.drawCircle(x + size / 2, y + size / 2, size / 2, p) else c.drawRect(x, y, x + size, y + size, p)
        p.color = col
        val k = size / 6
        for (i in 0 until 4) { tmpR.set(x + k * (1.1f + i), y + k * 1.6f, x + k * (1.9f + i), y + k * 3f); c.drawRoundRect(tmpR, k * 0.3f, k * 0.3f, p) }
        tmpR.set(x + k * 1.1f, y + k * 2.7f, x + k * 4.9f, y + k * 4.6f); c.drawRoundRect(tmpR, k * 0.5f, k * 0.5f, p)
        tmpR.set(x + k * 0.6f, y + k * 2.4f, x + k * 1.6f, y + k * 3.8f); c.drawRoundRect(tmpR, k * 0.4f, k * 0.4f, p)
    }
    private fun bar(c: Canvas, x: Float, y: Float, w: Float, h: Float, k: Float, col: Int, bg: Int = 0xaa000000.toInt()) {
        p.style = Paint.Style.FILL; p.color = 0xff000000.toInt(); c.drawRect(x - 1.5f * dp, y - 1.5f * dp, x + w + 1.5f * dp, y + h + 1.5f * dp, p)
        p.color = bg; c.drawRect(x, y, x + w, y + h, p)
        p.color = col; c.drawRect(x, y, x + w * k.coerceIn(0f, 1f), y + h, p)
    }
    private fun arc(c: Canvas, cx: Float, cy: Float, r: Float, start: Float, sweep: Float, col: Int) {
        p.style = Paint.Style.STROKE; p.strokeWidth = 6 * dp; p.color = col; p.strokeCap = Paint.Cap.BUTT
        tmpR.set(cx - r, cy - r, cx + r, cy + r); c.drawArc(tmpR, start, sweep, false, p); p.style = Paint.Style.FILL
    }
    private fun stars(c: Canvas, w: GtaWorld, right: Float, y: Float, size: Float, col: Int) {
        for (i in 0 until 6) {
            val on = i < w.wanted
            text(c, "★", right - i * (size + 2) * dp, y, size, if (on) col else 0x44ffffff, CLEAN, Paint.Align.RIGHT, outline = if (on) 0xff000000.toInt() else null, ow = 2f)
        }
    }

    private fun radar(c: Canvas, w: GtaWorld, cx: Float, cy: Float, r: Float, ring: Int) {
        c.save()
        clipPath.reset(); clipPath.addCircle(cx, cy, r, Path.Direction.CW); c.clipPath(clipPath)
        drawMapArea(c, w, cx, cy, r * 2.6f / 60f, rotate = true, labels = false)
        c.restore()
        p.style = Paint.Style.STROKE; p.strokeWidth = 3 * dp; p.color = ring; c.drawCircle(cx, cy, r, p); p.style = Paint.Style.FILL
        val a = w.viewYaw
        val nx = cx + sin(a) * r; val ny = cy - cos(a) * r
        p.color = 0xff000000.toInt(); c.drawCircle(nx, ny, 8 * dp, p)
        text(c, "N", nx, ny + 4.5f * dp, 12f, 0xffffffff.toInt(), CLEAN_B, Paint.Align.CENTER, outline = null)
        heroArrow(c, cx, cy, w)
    }
    private fun radarRect(c: Canvas, w: GtaWorld, left: Float, top: Float, mw: Float, mh: Float, rounded: Float = 0f) {
        c.save()
        tmpR.set(left, top, left + mw, top + mh)
        clipPath.reset(); clipPath.addRoundRect(tmpR, rounded, rounded, Path.Direction.CW); c.clipPath(clipPath)
        drawMapArea(c, w, left + mw / 2, top + mh * 0.62f, mw * 2.2f / 80f, rotate = true, labels = false)
        c.restore()
        p.style = Paint.Style.STROKE; p.strokeWidth = 2 * dp; p.color = 0x99000000.toInt(); tmpR.set(left, top, left + mw, top + mh); c.drawRoundRect(tmpR, rounded, rounded, p); p.style = Paint.Style.FILL
        heroArrow(c, left + mw / 2, top + mh * 0.62f, w)
    }
    private fun heroArrow(c: Canvas, cx: Float, cy: Float, w: GtaWorld) {
        c.save(); c.translate(cx, cy); c.rotate(-Math.toDegrees((w.pHeading - w.viewYaw).toDouble()).toFloat())
        val a = Path().apply { moveTo(0f, -9 * dp); lineTo(6 * dp, 7 * dp); lineTo(0f, 3 * dp); lineTo(-6 * dp, 7 * dp); close() }
        p.color = 0xffffffff.toInt(); c.drawPath(a, p); p.style = Paint.Style.STROKE; p.strokeWidth = 1.2f * dp; p.color = 0xff000000.toInt(); c.drawPath(a, p); p.style = Paint.Style.FILL
        c.restore()
    }
    /** The map top-down around a point ([scale] px per metre): sea, islands, street grid, beaches, bridges, blips. */
    private fun drawMapArea(c: Canvas, w: GtaWorld, cx: Float, cy: Float, scale: Float, rotate: Boolean, centreX: Float = w.px, centreZ: Float = w.pz, labels: Boolean) {
        val s = w.spec
        val B = w.B; val R = w.R
        val modern = s.hud == "iv" || s.hud == "v" || s.hud == "vi"
        c.save(); c.translate(cx, cy)
        if (rotate) c.rotate(Math.toDegrees(w.viewYaw.toDouble()).toFloat())
        c.translate(-centreX * scale, -centreZ * scale)
        p.style = Paint.Style.FILL
        p.color = if (modern) 0xff3b6f99.toInt() else 0xff2a5f8f.toInt()
        c.drawRect((w.minX - 600f) * scale, (w.minZ - 600f) * scale, (w.maxX + 600f) * scale, (w.maxZ + 600f) * scale, p)
        for (isl in s.isles) {
            if (isl.beach != ' ') {
                p.color = 0xffe8d8a8.toInt()
                val bx0 = if (isl.beach == 'E') isl.x1 + R / 2 else isl.x0 - R / 2 - 30f
                c.drawRect(bx0 * scale, (isl.z0 - R / 2) * scale, (bx0 + 30f) * scale, (isl.z1 + R / 2) * scale, p)
            }
            // streets (land colour), then the blocks on top
            p.color = if (modern) 0xff9aa0a8.toInt() else 0xffb8bcc2.toInt()
            c.drawRect((isl.x0 - R / 2) * scale, (isl.z0 - R / 2) * scale, (isl.x1 + R / 2) * scale, (isl.z1 + R / 2) * scale, p)
            val nx = ((isl.x1 - isl.x0) / B).roundToInt(); val nz = ((isl.z1 - isl.z0) / B).roundToInt()
            for (bx in 0 until nx) for (bz in 0 until nz) {
                val cz = isl.z0 + bz * B + B / 2
                p.color = when {
                    isl.kind == "hills" -> 0xff6b7a3a.toInt()
                    isl.kind == "airport" -> 0xff5b6067.toInt()
                    isl.parkZ0 >= 0f && cz > isl.parkZ0 && cz < isl.parkZ1 -> 0xff3f7d3a.toInt()
                    else -> if (modern) 0xff3a3f45.toInt() else 0xff4b5563.toInt()
                }
                c.drawRect((isl.x0 + bx * B + R / 2) * scale, (isl.z0 + bz * B + R / 2) * scale, (isl.x0 + (bx + 1) * B - R / 2) * scale, (isl.z0 + (bz + 1) * B - R / 2) * scale, p)
            }
        }
        p.color = if (modern) 0xff9aa0a8.toInt() else 0xffb8bcc2.toInt()
        for (b in s.bridges) c.drawRect(b.x0 * scale, b.z0 * scale, b.x1 * scale, b.z1 * scale, p)
        if (labels) {
            for (isl in s.isles) {
                c.save(); c.translate((isl.x0 + isl.x1) / 2 * scale, (isl.z0 + isl.z1) / 2 * scale)
                text(c, isl.name, 0f, 0f, 12f, 0xddffffff.toInt(), CLEAN_B, Paint.Align.CENTER, ow = 2.5f)
                c.restore()
            }
        }
        for ((_, kind, pos) in w.placesForMap()) {
            c.save(); c.translate(pos[0] * scale, pos[1] * scale); if (rotate) c.rotate(-Math.toDegrees(w.viewYaw.toDouble()).toFloat())
            p.color = 0xff000000.toInt(); c.drawCircle(0f, 0f, 9 * dp, p)
            val (glyph, col) = blip(kind)
            text(c, glyph, 0f, 4.5f * dp, 12f, col, CLEAN_B, Paint.Align.CENTER, outline = null)
            c.restore()
        }
        c.restore()
    }
    private fun blip(kind: String): Pair<String, Int> = when (kind) { "house" -> "H" to 0xff4ade80.toInt(); "food" -> "F" to 0xfffacc15.toInt(); "guns" -> "A" to 0xffef4444.toInt(); else -> "C" to 0xffc084fc.toInt() }

    // ---------------------------------------------------------------- controls
    private fun drawControls(c: Canvas, w: GtaWorld, W: Float, H: Float) {
        pauseR.set(12 * dp, 12 * dp, 56 * dp, 56 * dp)
        p.color = 0x88000000.toInt(); c.drawRoundRect(pauseR, 10 * dp, 10 * dp, p)
        p.color = 0xffffffff.toInt()
        for (i in 0 until 3) c.drawRect(pauseR.left + 12 * dp, pauseR.top + (14 + i * 7) * dp, pauseR.right - 12 * dp, pauseR.top + (17 + i * 7) * dp, p)
        if (joyId >= 0) {
            p.color = 0x44ffffff; c.drawCircle(joyCx, joyCy, 60 * dp, p)
            p.color = 0x99ffffff.toInt(); c.drawCircle(joyCx + joyX * 50 * dp, joyCy + joyY * 50 * dp, 26 * dp, p)
        }
        val bR = 40 * dp
        if (w.inCar) {
            gasR.set(W - 24 * dp - bR * 2, H - 24 * dp - bR * 2, W - 24 * dp, H - 24 * dp)
            brakeR.set(gasR.left - 16 * dp - bR * 2, gasR.top + 10 * dp, gasR.left - 16 * dp, gasR.bottom + 10 * dp)
            roundBtn(c, gasR, if (w.input.gas) 0xcc22c55e.toInt() else 0x8822c55e.toInt(), "GAS")
            roundBtn(c, brakeR, if (w.input.brake) 0xccef4444.toInt() else 0x88ef4444.toInt(), "BRAKE")
        } else { gasR.setEmpty(); brakeR.setEmpty() }
        if (w.canAct) {
            val lift = if (w.inCar) bR * 2 + 20 * dp else 0f
            actR.set(W - 24 * dp - bR * 2, H - 24 * dp - bR * 2 - lift, W - 24 * dp, H - 24 * dp - lift)
            roundBtn(c, actR, 0x88f59e0b.toInt(), if (w.inCar) "EXIT" else if (w.nearCar) "ENTER" else "USE")
        } else actR.setEmpty()
    }
    private fun roundBtn(c: Canvas, r: RectF, col: Int, label: String) {
        p.color = col; c.drawOval(r, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2 * dp; p.color = 0xccffffff.toInt(); c.drawOval(r, p); p.style = Paint.Style.FILL
        text(c, label, r.centerX(), r.centerY() + 5 * dp, 14f, 0xffffffff.toInt(), CLEAN_B, Paint.Align.CENTER, outline = null)
    }

    // ---------------------------------------------------------------- pause menu, map, stats
    private fun drawPause(c: Canvas, w: GtaWorld, W: Float, H: Float) {
        val s = w.spec
        menuItems.clear()
        val modern = s.hud == "iv" || s.hud == "v" || s.hud == "vi"
        p.color = if (modern) 0xdd0b0b0f.toInt() else 0xee000000.toInt(); c.drawRect(0f, 0f, W, H, p)
        val titleTf = when (s.hud) { "vc", "vcs" -> RAGE; "sa" -> PRICEDOWN; "iv", "v", "vi" -> CLEAN_B; else -> BANK }
        val accent = hex(s.accent)
        text(c, s.title, 30 * dp, 52 * dp, 24f, accent, titleTf, outline = null)
        val tabs = listOf("main" to "RESUME", "map" to "MAP", "stats" to "STATS", "help" to "CONTROLS", "quit" to "QUIT GAME")
        var x = 30 * dp
        for ((key, label) in tabs) {
            tp.typeface = titleTf; tp.textSize = 17 * dp
            val tw = tp.measureText(label)
            val r = RectF(x - 8 * dp, 66 * dp, x + tw + 8 * dp, 98 * dp)
            if (key == menuPage) { p.color = if (modern) 0x33ffffff else (accent and 0x55ffffff); c.drawRect(r, p) }
            text(c, label, x, 89 * dp, 17f, if (key == menuPage) hex(0xffffff) else 0xffbbbbbb.toInt(), titleTf, outline = null)
            menuItems += r to key
            x += tw + 30 * dp
        }
        val top = 112 * dp
        when (menuPage) {
            "map" -> {
                val box = RectF(30 * dp, top, W - 30 * dp, H - 24 * dp)
                c.save(); c.clipRect(box)
                p.color = 0xff111111.toInt(); c.drawRect(box, p)
                val mw = w.maxX - w.minX; val mh = w.maxZ - w.minZ
                val scale = min(box.width() / mw, box.height() / mh)
                val ccx = (w.minX + w.maxX) / 2; val ccz = (w.minZ + w.maxZ) / 2
                drawMapArea(c, w, box.centerX(), box.centerY(), scale, rotate = false, centreX = ccx, centreZ = ccz, labels = true)
                c.save(); c.translate(box.centerX() + (w.px - ccx) * scale, box.centerY() + (w.pz - ccz) * scale)
                c.rotate(-Math.toDegrees(w.pHeading.toDouble()).toFloat())
                val a = Path().apply { moveTo(0f, -10 * dp); lineTo(7 * dp, 8 * dp); lineTo(0f, 3 * dp); lineTo(-7 * dp, 8 * dp); close() }
                p.color = accent; c.drawPath(a, p); c.restore()
                c.restore()
                var ly = top + 24 * dp
                for ((name, kind, _) in w.placesForMap()) { val (g, col) = blip(kind); text(c, g, box.left + 18 * dp, ly, 13f, col, CLEAN_B, Paint.Align.CENTER, ow = 2f); text(c, name, box.left + 32 * dp, ly, 13f, 0xffffffff.toInt(), CLEAN_B, ow = 2f); ly += 20 * dp }
                text(c, "${w.zoneName.ifEmpty { s.city }} · ${s.city}", box.right - 12 * dp, box.bottom - 12 * dp, 15f, accent, titleTf, Paint.Align.RIGHT, ow = 2f)
            }
            "stats" -> {
                val rows = listOf(
                    "CITY" to s.city, "DISTRICT" to w.zoneName.ifEmpty { "-" }, "TIME" to clockStr(w), "MONEY" to moneyStr(w, false),
                    "DISTANCE DRIVEN" to String.format(java.util.Locale.US, "%.1f KM", w.statsDriven / 1000f), "CARS JACKED" to "${w.statsCars}",
                    "PICKUPS FOUND" to "${w.statsPickups}", "PEOPLE KNOCKED DOWN" to "${w.statsPeds}", "WANTED LEVEL" to "${w.wanted} ★"
                )
                var y = top + 26 * dp
                for ((a, b) in rows) { text(c, a, 40 * dp, y, 15f, 0xffbbbbbb.toInt(), CLEAN_B, outline = null); text(c, b, W - 40 * dp, y, 15f, 0xffffffff.toInt(), CLEAN_B, Paint.Align.RIGHT, outline = null); y += 26 * dp }
            }
            "help" -> {
                val lines = listOf(
                    "Left side: drag to walk (push all the way to run) or steer",
                    "Right side: drag to look around",
                    "GAS / BRAKE when driving (hold BRAKE to reverse)",
                    "ENTER near any car to get in. Taking someone's car raises your wanted level. EXIT to get out",
                    "Walk into a glowing marker to go inside: your safehouse (sleep to save and heal) or a shop",
                    "Cross the bridges to the other islands. Green cash and red hearts are pickups",
                    "Back button or the ≡ button pauses"
                )
                var y = top + 26 * dp
                for (l in lines) { text(c, "• $l", 40 * dp, y, 14f, 0xffffffff.toInt(), CLEAN, outline = null); y += 24 * dp }
            }
            else -> {
                text(c, "PAUSED", W / 2, H / 2, 34f, 0xffffffff.toInt(), titleTf, Paint.Align.CENTER, outline = null)
                text(c, "tap RESUME, or press back", W / 2, H / 2 + 30 * dp, 13f, 0xffaaaaaa.toInt(), CLEAN, Paint.Align.CENTER, outline = null)
            }
        }
    }

    private fun drawWasted(c: Canvas, w: GtaWorld, W: Float, H: Float) {
        p.color = 0x66000000; c.drawRect(0f, 0f, W, H, p)
        val (tf, col) = when (w.spec.hud) { "vc", "vcs" -> RAGE to hex(0xff4f9a); "sa" -> PRICEDOWN to hex(0xc0c0c0); "iv" -> CLEAN_B to hex(0xd02020); "v", "vi" -> PRICEDOWN to hex(0xb0001a); else -> BANK to hex(0xc02020) }
        text(c, "WASTED", W / 2, H / 2, 54f, col, tf, Paint.Align.CENTER, ow = 4f)
    }

    // ---------------------------------------------------------------- touch
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val w = world ?: return true
        val inp = w.input
        val idx = e.actionIndex; val id = e.getPointerId(idx)
        val x = e.getX(idx); val y = e.getY(idx)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (inp.paused) {
                    for ((r, key) in menuItems) if (r.contains(x, y)) {
                        when (key) { "main" -> inp.paused = false; "quit" -> { close(); onQuit() }; else -> menuPage = key }
                        return true
                    }
                    return true
                }
                when {
                    pauseR.contains(x, y) -> { inp.paused = true; menuPage = "main" }
                    !actR.isEmpty && actR.contains(x, y) -> inp.action = true
                    !gasR.isEmpty && gasR.contains(x, y) -> { gasId = id; inp.gas = true }
                    !brakeR.isEmpty && brakeR.contains(x, y) -> { brakeId = id; inp.brake = true }
                    x < width * 0.45f && joyId < 0 -> { joyId = id; joyCx = x; joyCy = y; joyX = 0f; joyY = 0f }
                    camId < 0 -> { camId = id; camLastX = x }
                }
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) {
                val pid = e.getPointerId(i)
                if (pid == joyId) {
                    val dx = (e.getX(i) - joyCx) / (50 * dp); val dy = (e.getY(i) - joyCy) / (50 * dp)
                    val l = max(1f, hypot(dx, dy)); joyX = dx / l; joyY = dy / l
                    inp.joyX = joyX; inp.joyY = joyY
                } else if (pid == camId) { inp.addCam(e.getX(i) - camLastX); camLastX = e.getX(i) }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                if (e.actionMasked == MotionEvent.ACTION_CANCEL) { joyId = -1; camId = -1; gasId = -1; brakeId = -1; inp.gas = false; inp.brake = false; inp.joyX = 0f; inp.joyY = 0f; return true }
                if (id == joyId) { joyId = -1; joyX = 0f; joyY = 0f; inp.joyX = 0f; inp.joyY = 0f }
                if (id == camId) camId = -1
                if (id == gasId) { gasId = -1; inp.gas = false }
                if (id == brakeId) { brakeId = -1; inp.brake = false }
            }
        }
        return true
    }
}
