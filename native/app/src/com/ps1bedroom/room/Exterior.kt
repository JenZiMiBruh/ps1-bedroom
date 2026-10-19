package com.ps1bedroom.room

import android.os.SystemClock
import com.ps1bedroom.room.engine.Canvas2D
import com.ps1bedroom.room.engine.CanvasTex
import com.ps1bedroom.room.engine.Geo
import com.ps1bedroom.room.engine.Group
import com.ps1bedroom.room.engine.Material
import com.ps1bedroom.room.engine.Mesh
import com.ps1bedroom.room.engine.PS1
import com.ps1bedroom.room.engine.Texture
import com.ps1bedroom.room.engine.Weather
import com.ps1bedroom.room.engine.createCanvasTex
import java.util.Calendar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * DETAILED 3D EXTERIOR WORLD (HTML lines 3730-4290): towers with lit windows and roof beacons, the elevated highway
 * with traffic, streetlights, swaying trees, day / night facades, a back row of towers, tri-vision billboards and an
 * LED ticker, and the elevated commuter train. Everything here moves or changes, so none of it is batched.
 */
class Exterior(private val group: Group, private val env: Environment, private val room: Room) {
    private val rnd = Random.Default

    // ---- Procedural lit building facade texture ----
    private fun createBuildingTex(seedHue: Double): Texture = createCanvasTex(64, 128) { ctx, w, h ->
        ctx.fillStyle = "#0a0f1f"
        ctx.fillRect(0, 0, w, h)
        ctx.strokeStyle = "#17213a"
        ctx.lineWidth = 2f
        var x = 0
        while (x < w) { ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, h); ctx.stroke(); x += 8 }
        var y = 6
        while (y < h - 6) {
            var xx = 4
            while (xx < w - 4) {
                val r = sin(xx * 12.9898 + y * 78.233 + seedHue) * 43758.5453
                val frac = r - floor(r)
                if (frac > 0.44) {
                    ctx.fillStyle = if (frac > 0.72) "#fde047" else (if (frac > 0.58) "#7dd3fc" else "#fed7aa")
                    ctx.fillRect(xx, y, 5, 3)
                } else {
                    ctx.fillStyle = "#111827"
                    ctx.fillRect(xx, y, 5, 3)
                }
                xx += 8
            }
            y += 6
        }
    }.tex

    private val bldgTex1 = createBuildingTex(1.1)
    private val bldgTex2 = createBuildingTex(2.7)
    private val bldgTex3 = createBuildingTex(4.3)
    private val bldgMat1 = PS1.material(map = bldgTex1, emissive = 0xffffff, emissiveMap = bldgTex1, emissiveIntensity = 0.65f)
    private val bldgMat2 = PS1.material(map = bldgTex2, emissive = 0xffffff, emissiveMap = bldgTex2, emissiveIntensity = 0.65f)
    private val bldgMat3 = PS1.material(map = bldgTex3, emissive = 0xffffff, emissiveMap = bldgTex3, emissiveIntensity = 0.65f)
    private val bldgRoofMat = PS1.material(color = 0x1e293b)
    private val roofBeacons = ArrayList<Material>()

    private class BldgSpec(val x: Float, val z: Float, val w: Float, val h: Float, val d: Float, val mat: Material, val beacon: Boolean)

    // ---- Traffic ----
    private class VehicleType(val kind: String, val len: Float, val h: Float, val cabin: FloatArray?, val cols: IntArray, val w: Double,
                              val sign: Boolean = false, val box: Boolean = false, val bus: Boolean = false, val police: Boolean = false)
    private val VEHICLE_TYPES = listOf(
        VehicleType("sedan", 0.5f, 0.13f, floatArrayOf(0.26f, 0.11f), intArrayOf(0xdc2626, 0x2563eb, 0x94a3b8, 0x16a34a, 0xf8fafc, 0x1f2937, 0x7c3aed, 0xb45309), 6.0),
        VehicleType("hatch", 0.42f, 0.14f, floatArrayOf(0.22f, 0.12f), intArrayOf(0xeab308, 0x0ea5e9, 0xe11d48, 0x65a30d), 4.0),
        VehicleType("taxi", 0.5f, 0.13f, floatArrayOf(0.26f, 0.11f), intArrayOf(0xfacc15), 3.0, sign = true),
        VehicleType("van", 0.6f, 0.24f, null, intArrayOf(0xf1f5f9, 0x334155, 0x92400e), 2.0),
        VehicleType("truck", 0.85f, 0.3f, null, intArrayOf(0xe5e7eb, 0x7f1d1d, 0x1e3a8a), 1.5, box = true),
        VehicleType("bus", 1.25f, 0.3f, null, intArrayOf(0x1d4ed8, 0xf59e0b), 1.0, bus = true),
        VehicleType("police", 0.52f, 0.13f, floatArrayOf(0.26f, 0.11f), intArrayOf(0x0b0b0f), 0.6, police = true)
    )
    private val vehicleWeightTotal = VEHICLE_TYPES.sumOf { it.w }
    private fun pickVehicleType(): VehicleType { var r = rnd.nextDouble() * vehicleWeightTotal; for (v in VEHICLE_TYPES) { r -= v.w; if (r <= 0) return v }; return VEHICLE_TYPES[0] }
    private class Lane(val z: Float, val dir: Int)
    private val LANES = listOf(Lane(-5.65f, 1), Lane(-6.65f, -1))
    private class Vehicle(val g: Group, val type: VehicleType, val dir: Int, val cruise: Double, val beam: Mesh, val snow: Mesh, val len: Float) {
        var speed = 0.0; var active = false; lateinit var lane: Lane
    }
    private val trafficCars3D = ArrayList<Vehicle>()
    private val VEHICLE_POOL = 20
    private lateinit var matHeadlight: Material
    private lateinit var matTaillight: Material
    private lateinit var busWinMat: Material
    private lateinit var roofSnowMat: Material
    private lateinit var wetGlowMat: Material
    private lateinit var wetRedMat: Material
    private lateinit var policeRed: Material
    private lateinit var policeBlue: Material
    private var policeChase = 0.0

    // ---- Trees ----
    private val exteriorTrees = ArrayList<Pair<Group, Float>>()   // group, sway phase
    private class Foliage(val mesh: Mesh, val mat: Material, val idx: Int)
    private val treeFoliageClusters = ArrayList<Foliage>()

    // ---- Facades, billboards, ticker, train ----
    private class FacadeSet(val mat: Material, val night: Texture, val day: Texture)
    private val facadeSets = ArrayList<FacadeSet>()
    private class Ad(val lines: Array<String>?, val a: String, val b: String, val bg: String, val `when`: (() -> Boolean)? = null, val dyn: (() -> Array<String>)? = null)
    private val AD_LIB: List<Ad>
    private class Billboard(val obj: CanvasTex, val mat: Material, val phase: Double, var cur: Ad, var next: Ad?, var flipT: Double, var nextAt: Long, val a: Canvas2D, val b: Canvas2D)
    private val billboards = ArrayList<Billboard>()
    private lateinit var tickerObj: CanvasTex
    private lateinit var tickerMat: Material
    private var tickerLast = 0f
    private val streetLampMats = ArrayList<Material>()
    private lateinit var trainMat: Material
    private val trainGroup = Group()
    private var trainX = -30.0; private var trainDir = 1; private val trainSpeed = 4.2; private var trainNext = 12.0; private var trainHeard = false
    /** Called when the train passes the window (the pass sound comes with the audio phase). */
    var onTrainPass: () -> Unit = {}
    var tempUnit = if (Locale.getDefault().let { it.language == "en" && it.country == "US" }) "F" else "C"

    init {
        // ---- Towers with HVAC, winter snow caps and roof beacons ----
        val bldgSpecs3D = listOf(
            BldgSpec(-4.2f, -9.8f, 1.9f, 7.5f, 1.8f, bldgMat1, true),
            BldgSpec(-2.1f, -8.6f, 1.5f, 5.8f, 1.5f, bldgMat2, false),
            // Low tower in the middle leaves an open patch of sky where the moon sits from the default view
            BldgSpec(-0.35f, -10.8f, 1.4f, 5.0f, 2.0f, bldgMat3, false),
            BldgSpec(1.65f, -9.2f, 1.6f, 6.4f, 1.6f, bldgMat1, true),
            BldgSpec(3.45f, -10.4f, 2.0f, 8.2f, 1.9f, bldgMat2, true),
            BldgSpec(5.5f, -8.9f, 1.8f, 6.8f, 1.7f, bldgMat3, false)
        )
        for (b in bldgSpecs3D) {
            val bGroup = Group()
            bGroup.position.set(b.x, b.h / 2 - 2.2f, b.z)
            bGroup.add(Mesh(Geo.box(b.w, b.h, b.d, 2, 6, 2), b.mat))
            val hvac = Mesh(Geo.box(b.w * 0.45f, 0.35f, b.d * 0.45f), bldgRoofMat)
            hvac.position.set(0f, b.h / 2 + 0.175f, 0f); bGroup.add(hvac)
            val snowCap = Mesh(Geo.box(b.w + 0.04f, 0.08f, b.d + 0.04f), PS1.material(color = 0xf1f5f9, emissive = 0x94a3b8, emissiveIntensity = 0.2f))
            snowCap.position.set(0f, b.h / 2 + 0.04f, 0f); snowCap.visible = false
            bGroup.add(snowCap); room.snowCapMeshes += snowCap
            if (b.beacon) {
                val antennaMast = Mesh(Geo.cylinder(0.02f, 0.03f, 0.9f, 5), bldgRoofMat)
                antennaMast.position.set(0f, b.h / 2 + 0.6f, 0f); bGroup.add(antennaMast)
                val beaconMat = PS1.material(color = 0xff2222, emissive = 0xff0000, emissiveIntensity = 1.0f)
                val beaconBulb = Mesh(Geo.sphere(0.07f, 6, 6), beaconMat)
                beaconBulb.position.set(0f, b.h / 2 + 1.08f, 0f); bGroup.add(beaconBulb)
                roofBeacons += beaconMat
            }
            group.add(bGroup)
        }

        // ---- Elevated highway overpass & streetlights ----
        val highwayDeck = Mesh(Geo.box(24.2f, 0.28f, 2.1f, 16, 1, 2), PS1.material(color = 0x111827))
        highwayDeck.position.set(0.8f, 0.65f, -6.2f); group.add(highwayDeck)
        val guardRailFront = Mesh(Geo.box(24.2f, 0.18f, 0.08f), PS1.material(color = 0x334155))
        guardRailFront.position.set(0.8f, 0.86f, -5.18f); group.add(guardRailFront)
        for (lx in floatArrayOf(-7.2f, -3.2f, 0.8f, 4.8f, 8.8f)) {
            val pole = Mesh(Geo.cylinder(0.03f, 0.04f, 1.35f, 5), PS1.material(color = 0x475569))
            pole.position.set(lx, 1.45f, -7.15f); group.add(pole)
            val lampMat = PS1.material(color = 0xfef08a, emissive = 0xf59e0b, emissiveIntensity = 0.95f)
            val lampHead = Mesh(Geo.box(0.22f, 0.06f, 0.12f), lampMat)
            lampHead.position.set(lx, 2.12f, -6.95f); group.add(lampHead)
            streetLampMats += lampMat
        }

        // ---- Traffic: a pool of mixed vehicles whose numbers, speed and lights follow the clock and the weather ----
        matHeadlight = PS1.material(color = 0xfef9c3, emissive = 0xfef08a, emissiveIntensity = 1.0f)
        matTaillight = PS1.material(color = 0xef4444, emissive = 0xdc2626, emissiveIntensity = 1.0f)
        val busWinTex = createCanvasTex(64, 8) { ctx, w, h ->
            ctx.fillStyle = "#1e293b"; ctx.fillRect(0, 0, w, h)
            var x = 2; while (x < w - 2) { ctx.fillStyle = "#ffe9a8"; ctx.fillRect(x, 1, 4, 5); x += 6 }
        }.tex
        busWinMat = PS1.material(map = busWinTex, emissive = 0xffffff, emissiveMap = busWinTex, emissiveIntensity = 0.5f)
        roofSnowMat = PS1.material(color = 0xf8fafc, emissive = 0x94a3b8, emissiveIntensity = 0.2f)
        wetGlowMat = PS1.basic(color = 0xfff1c2, transparent = true, opacity = 0.0f, depthWrite = false).also { it.additive = true }
        wetRedMat = PS1.basic(color = 0xff3b3b, transparent = true, opacity = 0.0f, depthWrite = false).also { it.additive = true }
        policeRed = PS1.material(color = 0xff2020, emissive = 0xff0000, emissiveIntensity = 0.1f)
        policeBlue = PS1.material(color = 0x2050ff, emissive = 0x0030ff, emissiveIntensity = 0.1f)
        for (i in 0 until VEHICLE_POOL) {
            val lane = LANES[i % 2]
            val v = buildVehicle(pickVehicleType(), lane.dir)
            v.g.position.set(-30f, 0.79f, lane.z)
            v.g.visible = false
            v.lane = lane
            group.add(v.g)
            trafficCars3D += v
        }

        // ---- Swaying trees (left & right of the window, reacting to seasons & wind) ----
        val treeBarkMat = PS1.material(color = 0x3b2314)
        val treePositions = arrayOf(floatArrayOf(-0.95f, 0.4f, -4.25f, 1.1f), floatArrayOf(2.35f, 0.35f, -4.15f, 1.18f), floatArrayOf(-1.85f, 0.3f, -5.1f, 0.95f), floatArrayOf(3.35f, 0.3f, -5.0f, 1.0f))
        val branchOffsets = arrayOf(
            floatArrayOf(0.32f, 1.95f, 0.12f, -0.58f, 0.2f, 0.46f),
            floatArrayOf(-0.30f, 2.15f, -0.1f, 0.52f, -0.15f, 0.42f),
            floatArrayOf(0.08f, 2.45f, 0.18f, -0.18f, 0.35f, 0.52f),
            floatArrayOf(-0.15f, 2.68f, -0.08f, 0.22f, -0.2f, 0.44f)
        )
        treePositions.forEachIndexed { idx, tp ->
            val tGroup = Group()
            tGroup.position.set(tp[0], tp[1], tp[2])
            tGroup.scale.set(tp[3], tp[3], tp[3])
            val trunk = Mesh(Geo.cylinder(0.08f, 0.15f, 2.3f, 6), treeBarkMat)
            trunk.position.y = 1.15f; tGroup.add(trunk)
            branchOffsets.forEachIndexed { bIdx, bo ->
                val bMesh = Mesh(Geo.cylinder(0.03f, 0.06f, 0.85f, 5), treeBarkMat)
                bMesh.position.set(bo[0] * 0.6f, bo[1] - 0.15f, bo[2] * 0.6f)
                bMesh.rotation.set(bo[4], 0f, bo[3])
                tGroup.add(bMesh)
                // Low-poly foliage cluster
                val folMat = PS1.material(color = 0x15803d)
                val folMesh = Mesh(Geo.dodecahedron(bo[5], 0), folMat)
                folMesh.position.set(bo[0], bo[1] + 0.18f, bo[2])
                tGroup.add(folMesh)
                treeFoliageClusters += Foliage(folMesh, folMat, idx + bIdx)
                // Snow cap on branch for winter
                val branchSnow = Mesh(Geo.box(0.28f, 0.05f, 0.22f), PS1.material(color = 0xf8fafc, emissive = 0x94a3b8, emissiveIntensity = 0.25f))
                branchSnow.position.set(bo[0] * 0.8f, bo[1] + 0.06f, bo[2] * 0.8f)
                branchSnow.visible = false
                tGroup.add(branchSnow); room.snowCapMeshes += branchSnow
            }
            group.add(tGroup)
            exteriorTrees += tGroup to idx * 1.4f
        }

        // ---- 1.1 Day / night facades and a back row of towers ----
        fun createBuildingDayTex(seedHue: Double, wall: String) = createCanvasTex(64, 128, 1f, 1f, Weather(seed = seedHue * 10, blotch = 0.25, speckle = 0.05, edge = 0.15)) { ctx, w, h ->
            ctx.fillStyle = wall
            ctx.fillRect(0, 0, w, h)
            var y = 6
            while (y < h - 6) {
                var x = 4
                while (x < w - 4) {
                    val r = sin(x * 12.9898 + y * 78.233 + seedHue) * 43758.5453
                    val frac = r - floor(r)
                    // Daytime glass: sky reflections, a few open blinds, the odd lit office
                    ctx.fillStyle = if (frac > 0.9) "#e8f1fb" else (if (frac > 0.55) "#7b9cc0" else (if (frac > 0.2) "#4f6a8c" else "#2b3a52"))
                    ctx.fillRect(x, y, 5, 3)
                    x += 8
                }
                y += 6
            }
            ctx.fillStyle = "rgba(0, 0, 0, 0.18)"
            var x = 0; while (x < w) { ctx.fillRect(x, 0, 1, h); x += 8 }
        }.tex
        facadeSets += FacadeSet(bldgMat1, bldgTex1, createBuildingDayTex(1.1, "#8a8f99"))
        facadeSets += FacadeSet(bldgMat2, bldgTex2, createBuildingDayTex(2.7, "#a08a76"))
        facadeSets += FacadeSet(bldgMat3, bldgTex3, createBuildingDayTex(4.3, "#77808c"))
        val backTex = createBuildingTex(6.9)
        val backMat = PS1.material(map = backTex, emissive = 0xffffff, emissiveMap = backTex, emissiveIntensity = 0.55f)
        facadeSets += FacadeSet(backMat, backTex, createBuildingDayTex(6.9, "#9aa1ab"))
        for (s in arrayOf(floatArrayOf(-6.4f, -13.2f, 2.2f, 9.5f), floatArrayOf(-1.4f, -13.8f, 1.8f, 10.5f), floatArrayOf(0.9f, -14.2f, 2.4f, 6.2f), floatArrayOf(5.1f, -13.0f, 2.0f, 9.0f), floatArrayOf(7.8f, -12.6f, 2.2f, 7.4f))) {
            val (x, z, wd, ht) = s
            val tower = Mesh(Geo.box(wd, ht, 1.8f, 2, 6, 2), backMat)
            tower.position.set(x, ht / 2 - 2.2f, z); group.add(tower)
            val cap = Mesh(Geo.box(wd + 0.04f, 0.08f, 1.84f), PS1.material(color = 0xf1f5f9, emissive = 0x94a3b8, emissiveIntensity = 0.2f))
            cap.position.set(x, ht - 2.16f, z); cap.visible = false
            group.add(cap); room.snowCapMeshes += cap
        }

        // ---- 1.2 Billboards: tri-vision signs that flip between ads picked for the time, weather and season ----
        AD_LIB = listOf(
            Ad(arrayOf("NEO COLA", "ICE COLD · 24 HRS"), "#ff3fa4", "#7ff3ff", "#0b0614"),
            Ad(arrayOf("104.3 FM", "THE NIGHT SHIFT"), "#ffd23f", "#ff7a3f", "#120a02", { env.hour >= 19 || env.hour < 5 }),
            // (reads the PlayStation's game once the console is ported)
            Ad(null, "#6ad6ff", "#ffffff", "#06101c", dyn = { arrayOf("NOW PLAYING", nowPlaying()) }),
            Ad(arrayOf("CLUCK SHACK", "2 PC + FRIES \$1.99"), "#ffb703", "#ffffff", "#7f1d1d"),
            Ad(arrayOf("MEGABYTE PIZZA", "HOT IN 30 MIN OR FREE"), "#ff5a36", "#fff3b0", "#1a1206"),
            Ad(arrayOf("CYBERZONE", "ARCADE · OPEN LATE"), "#a855f7", "#22d3ee", "#0a0418"),
            Ad(arrayOf("Y2K READY?", "UPGRADE TODAY · COMPUMART"), "#4ade80", "#e5e7eb", "#05140a"),
            Ad(arrayOf("THE MATRIKS", "IN THEATERS NOW"), "#4ade80", "#d1fae5", "#020617"),
            Ad(arrayOf("JAVA JOLT", "MORNING BLEND 99¢"), "#e0b07a", "#ffffff", "#2a1606", { env.hour >= 5 && env.hour < 11 }),
            Ad(arrayOf("STARLITE DINER", "OPEN 24/7 · FRESH PIE"), "#ff5fa2", "#7ff3ff", "#0b0614", { env.hour >= 21 || env.hour < 5 }),
            Ad(arrayOf("GETTING WET?", "UMBRELLAS \$5 · CORNER STOP"), "#60a5fa", "#ffffff", "#0b1530", { env.isRainingNow() }),
            Ad(arrayOf("HOT COCOA", "WARM UP · 2 FOR \$1"), "#fbbf24", "#ffffff", "#3b1d0a", { env.currentSeason == "winter" || env.isSnowingNow() }),
            Ad(arrayOf("FREEZY POPS", "BEAT THE HEAT"), "#38bdf8", "#f472b6", "#04202a", { env.currentSeason == "summer" && env.daylight > 0.5 }),
            Ad(arrayOf("SPOOK-O-RAMA", "COSTUMES 50% OFF"), "#f97316", "#a855f7", "#0a0505", { Calendar.getInstance().get(Calendar.MONTH) == 9 }),
            Ad(arrayOf("SNOW DAY?", "SLEDS · BOOTS · MITTENS"), "#e0f2fe", "#7dd3fc", "#0b1a2e", { env.isSnowingNow() })
        )
        makeBillboard(1.65f, 4.75f, -8.35f, 1.3f, 0.48f, 0)
        makeBillboard(-4.2f, 5.95f, -8.85f, 1.4f, 0.52f, 1)
        makeBillboard(5.5f, 5.15f, -7.95f, 1.1f, 0.42f, 3) // (the PS1 ad reads game state, so it only appears in rotation later)
        // LED ticker on the tall tower's face
        tickerObj = createCanvasTex(256, 24) { _, _, _ -> }
        tickerMat = PS1.material(map = tickerObj.tex, emissive = 0xffffff, emissiveMap = tickerObj.tex, emissiveIntensity = 1f)
        val ticker = Mesh(Geo.plane(1.75f, 0.17f), tickerMat)
        ticker.position.set(3.45f, 3.3f, -9.43f)
        group.add(ticker)

        // ---- Elevated commuter train on a viaduct between the highway and the towers ----
        val viaduct = Mesh(Geo.box(24.2f, 0.14f, 0.7f), PS1.material(color = 0x3b4252))
        viaduct.position.set(0.8f, 1.62f, -7.7f); group.add(viaduct)
        var px = -10.2f
        while (px <= 12.3f) {
            val pier = Mesh(Geo.box(0.16f, 1.4f, 0.3f), PS1.material(color = 0x2c3240))
            pier.position.set(px, 0.9f, -7.7f); group.add(pier)
            px += 2.5f
        }
        val trainWinTex = createCanvasTex(64, 16) { ctx, w, h ->
            ctx.fillStyle = "#c9ced6"; ctx.fillRect(0, 0, w, h)
            ctx.fillStyle = "#1f6feb"; ctx.fillRect(0, h - 4, w, 2)
            var x = 3; while (x < w - 3) { ctx.fillStyle = "#ffe9a8"; ctx.fillRect(x, 3, 5, 6); x += 7 }
        }.tex
        trainMat = PS1.material(map = trainWinTex, emissive = 0xffffff, emissiveMap = trainWinTex, emissiveIntensity = 0.6f)
        for (c in 0 until 4) {
            val carMesh = Mesh(Geo.box(1.7f, 0.34f, 0.36f), trainMat)
            carMesh.position.set(c * 1.78f, 0f, 0f)
            trainGroup.add(carMesh)
        }
        trainGroup.position.set(-30f, 1.87f, -7.7f)
        group.add(trainGroup)

        env.envHooks += { updateExteriorForTime() }
    }

    private fun nowPlaying() = "PS1 BEDROOM"

    private fun buildVehicle(type: VehicleType, dir: Int): Vehicle {
        val g = Group()
        val col = type.cols[floor(rnd.nextDouble() * type.cols.size).toInt()]
        val body = Mesh(Geo.box(type.len, type.h, 0.24f, 2, 1, 1), PS1.material(color = col))
        body.position.y = type.h / 2 + 0.04f
        g.add(body)
        var roofY = type.h + 0.04f
        val cabin = type.cabin
        if (cabin != null) {
            val cab = Mesh(Geo.box(cabin[0], cabin[1], 0.21f), PS1.material(color = if (type.police) 0xf8fafc else 0x1e293b))
            cab.position.set(if (dir > 0) -0.03f else 0.03f, type.h + 0.04f + cabin[1] / 2, 0f)
            g.add(cab)
            roofY += cabin[1]
            if (type.police) {
                body.material.color.setHex(0x0b0b0f)
                val door = Mesh(Geo.box(0.2f, type.h * 0.7f, 0.245f), PS1.material(color = 0xf8fafc))
                door.position.y = body.position.y; g.add(door)
            }
        }
        if (type.bus) {
            for (z in floatArrayOf(0.123f, -0.123f)) {
                val win = Mesh(Geo.plane(type.len * 0.9f, 0.08f), busWinMat)
                win.position.set(0f, type.h * 0.72f + 0.04f, z); win.rotation.y = if (z > 0) 0f else PI.toFloat(); g.add(win)
            }
        }
        if (type.box) {
            val cab = Mesh(Geo.box(0.2f, 0.2f, 0.24f), PS1.material(color = 0x334155))
            cab.position.set(if (dir > 0) type.len / 2 + 0.08f else -type.len / 2 - 0.08f, 0.14f, 0f)
            g.add(cab)
        }
        if (type.sign) {
            val sign = Mesh(Geo.box(0.08f, 0.035f, 0.05f), PS1.material(color = 0xfff7cc, emissive = 0xffe680, emissiveIntensity = 0.8f))
            sign.position.set(0f, roofY + 0.02f, 0f)
            g.add(sign)
        }
        if (type.police) {
            val r = Mesh(Geo.box(0.05f, 0.03f, 0.08f), policeRed); r.position.set(0f, roofY + 0.015f, -0.045f); g.add(r)
            val b = Mesh(Geo.box(0.05f, 0.03f, 0.08f), policeBlue); b.position.set(0f, roofY + 0.015f, 0.045f); g.add(b)
        }
        val snow = Mesh(Geo.box(if (cabin != null) cabin[0] else type.len * 0.95f, 0.025f, 0.2f), roofSnowMat)
        snow.position.y = roofY + 0.012f
        g.add(snow)
        val front = if (dir > 0) type.len / 2 + (if (type.box) 0.18f else 0f) + 0.01f else -type.len / 2 - (if (type.box) 0.18f else 0f) - 0.01f
        for (lz in floatArrayOf(-0.07f, 0.07f)) {
            val hl = Mesh(Geo.box(0.03f, 0.035f, 0.045f), matHeadlight); hl.position.set(front, 0.1f, lz); g.add(hl)
            val tl = Mesh(Geo.box(0.03f, 0.035f, 0.045f), matTaillight); tl.position.set(if (dir > 0) -type.len / 2 - 0.01f else type.len / 2 + 0.01f, 0.1f, lz); g.add(tl)
        }
        // Headlight beam on the road, and wet-road reflections for rainy nights
        val beam = Mesh(Geo.cone(0.18f, 0.65f, 6), PS1.basic(color = 0xfef08a, transparent = true, opacity = 0.22f, depthWrite = false))
        beam.rotation.z = if (dir > 0) (PI / 2).toFloat() else (-PI / 2).toFloat()
        beam.position.set(front + (if (dir > 0) 0.32f else -0.32f), 0.08f, 0f)
        g.add(beam)
        val wetFront = Mesh(Geo.plane(0.9f, 0.12f), wetGlowMat)
        wetFront.rotation.x = (-PI / 2).toFloat(); wetFront.position.set(front + (if (dir > 0) 0.5f else -0.5f), 0.012f, 0f); g.add(wetFront)
        val wetBack = Mesh(Geo.plane(0.6f, 0.08f), wetRedMat)
        wetBack.rotation.x = (-PI / 2).toFloat(); wetBack.position.set(if (dir > 0) -type.len / 2 - 0.35f else type.len / 2 + 0.35f, 0.012f, 0f); g.add(wetBack)
        return Vehicle(g, type, dir, 1.7 + rnd.nextDouble() * 0.9, beam, snow, type.len + (if (type.box) 0.3f else 0f))
    }


    // ---- Shared with the places outside the window (HTML parkedCar, bldgTex1-3, the skyscraper billboard) ----
    val buildingTextures get() = listOf(bldgTex1, bldgTex2, bldgTex3)
    fun parkedVehicle(kind: String): Group {
        val v = buildVehicle(VEHICLE_TYPES.firstOrNull { it.kind == kind } ?: VEHICLE_TYPES[0], 1)
        v.beam.visible = false; v.snow.visible = false
        return v.g
    }
    private var placesAd: Ad? = null
    fun drawNextPlacesAd(ctx: Canvas2D) { val a = pickAd(listOfNotNull(placesAd)); placesAd = a; drawAd(ctx, a, 128, 48) }
    /** Everything of the city (setOutsideEnv hides it elsewhere); the trees show wherever the place has trees. */
    fun cityParts(): List<Pair<com.ps1bedroom.room.engine.Object3D, Boolean>> = group.children.map { o -> o to exteriorTrees.any { it.first === o } }

    // How busy the highway is right now (0..1): rush hours, a quiet middle of the night, fewer cars in storms
    private fun trafficDensity(): Double {
        val h = env.hour
        fun bump(c: Double, w: Double, a: Double) = a * exp(-((h - c) * (h - c)) / (2 * w * w))
        var d = 0.12 + bump(8.0, 1.2, 0.7) + bump(17.5, 1.5, 0.75) + bump(12.5, 2.5, 0.3) + bump(21.0, 2.0, 0.2)
        if (h >= 1 && h < 5) d = 0.06 + bump(5.0, 1.0, 0.1)
        val W = env.W
        if (W.base == "storm") d *= 0.5
        else if (env.isSnowingNow()) d *= 0.6
        else if (W.fog > 0.5f) d *= 0.7
        return min(1.0, d)
    }

    private fun updateTraffic(t: Float, dt: Float) {
        val W = env.W
        val density = trafficDensity()
        val wantActive = max(1, (VEHICLE_POOL * density).roundToInt())
        val weatherSlow = if (env.isSnowingNow()) 0.55 else (if (env.isRainingNow()) 0.8 else (if (W.fog > 0.5f) 0.7 else 1.0))
        val jam = if (density > 0.7) 0.6 else 1.0 // rush-hour crawl
        val night = 1 - env.daylight
        val lightsOn = night > 0.35 || env.isPrecipNow() || W.fog > 0.3f
        val wet = env.isRainingNow() && night > 0.3
        wetGlowMat.opacity = if (wet) 0.35f else 0f
        wetRedMat.opacity = if (wet) 0.3f else 0f
        val flash = sin(t * 14) > 0
        policeRed.emissiveIntensity = if (policeChase > 0 && flash) 1.2f else 0.1f
        policeBlue.emissiveIntensity = if (policeChase > 0 && !flash) 1.2f else 0.1f
        if (policeChase > 0) policeChase -= dt
        else if (night > 0.5 && rnd.nextDouble() < 0.0004) policeChase = 25.0
        var active = 0
        for (v in trafficCars3D) if (v.active) active++
        for (v in trafficCars3D) {
            if (!v.active) {
                // Spawn at the far end of its lane when the road needs more cars and the entry is clear
                if (active < wantActive && rnd.nextDouble() < dt * 0.6) {
                    val startX = if (v.dir > 0) -10f else 10f
                    var blocked = false
                    for (o in trafficCars3D) if (o !== v && o.active && o.lane === v.lane && abs(o.g.position.x - startX) < 1.6f) { blocked = true; break }
                    if (!blocked) { v.active = true; active++; v.g.visible = true; v.g.position.x = startX; v.speed = v.cruise * weatherSlow * jam }
                }
                continue
            }
            // Follow the car ahead in the same lane instead of driving through it
            var target = v.cruise * weatherSlow * jam
            for (o in trafficCars3D) {
                if (o === v || !o.active || o.lane !== v.lane) continue
                val gap = (o.g.position.x - v.g.position.x) * v.dir
                if (gap > 0 && gap < 0.45f + (v.len + o.len) / 2) target = min(target, o.speed * 0.9)
            }
            v.speed += (target - v.speed) * min(1.0, dt * 1.5)
            v.g.position.x += (v.dir * v.speed * dt).toFloat()
            v.beam.visible = lightsOn
            v.snow.visible = env.isSnowingNow() || (env.currentSeason == "winter" && env.daylight < 0.6)
            if ((v.dir > 0 && v.g.position.x > 10.5f) || (v.dir < 0 && v.g.position.x < -10.5f) || active > wantActive + 2) {
                v.active = false; v.g.visible = false; v.g.position.x = -30f; active--
            }
        }
        matHeadlight.emissiveIntensity = if (lightsOn) 1.0f else 0.15f
        matTaillight.emissiveIntensity = if (lightsOn) 1.0f else 0.3f
        busWinMat.emissiveIntensity = (0.15 + 0.6 * night).toFloat()
    }

    // ---- Billboards ----
    private fun drawAd(ctx: Canvas2D, ad: Ad, w: Int, h: Int) {
        val lines = ad.dyn?.invoke() ?: ad.lines!!
        val l1 = lines[0]; val l2 = lines[1]
        ctx.fillStyle = ad.bg; ctx.fillRect(0, 0, w, h)
        ctx.strokeStyle = ad.a; ctx.lineWidth = 2f; ctx.strokeRect(3, 3, w - 6, h - 6)
        ctx.textAlign = "center"
        ctx.fillStyle = ad.a
        ctx.font = "bold ${if (l1.length > 11) 14 else 18}px Impact, \"sans-serif-condensed\", sans-serif"
        ctx.fillText(l1, w / 2, 24, w - 10)
        ctx.fillStyle = ad.b
        ctx.font = "bold 9px sans-serif"
        ctx.fillText(l2, w / 2, 39, w - 10)
        ctx.textAlign = "left"
    }
    private fun makeBillboard(x: Float, y: Float, z: Float, w: Float, h: Float, startIdx: Int) {
        val obj = createCanvasTex(128, 48) { _, _, _ -> }
        val mat = PS1.material(map = obj.tex, emissive = 0xffffff, emissiveMap = obj.tex, emissiveIntensity = 0.9f)
        val board = Mesh(Geo.plane(w, h), mat)
        board.position.set(x, y, z)
        group.add(board)
        for (ox in floatArrayOf(-w * 0.35f, w * 0.35f)) {
            val leg = Mesh(Geo.box(0.05f, 0.5f, 0.05f), bldgRoofMat)
            leg.position.set(x + ox, y - h / 2 - 0.2f, z - 0.05f)
            group.add(leg)
        }
        val bb = Billboard(obj, mat, rnd.nextDouble() * 10, AD_LIB[startIdx], null, -1.0, SystemClock.uptimeMillis() + 8000 + (rnd.nextDouble() * 14000).toLong(), Canvas2D(128, 48), Canvas2D(128, 48))
        drawAd(obj.ctx, bb.cur, 128, 48)
        obj.tex.needsUpdate()
        billboards += bb
    }
    private fun pickAd(exclude: List<Ad>): Ad {
        val now = AD_LIB.filter { (it.`when` == null || it.`when`.invoke()) && it !in exclude }
        // Ads for this exact moment (morning coffee, umbrellas in the rain...) win more often
        val special = now.filter { it.`when` != null }
        val pool = if (special.isNotEmpty() && rnd.nextDouble() < 0.6) special else now
        return if (pool.isEmpty()) AD_LIB[0] else pool[floor(rnd.nextDouble() * pool.size).toInt()]
    }
    private fun currentTempC(): Double {
        val base = mapOf("summer" to 24.0, "spring" to 14.0, "fall" to 11.0, "winter" to -1.0).getValue(env.currentSeason)
        return base + 0 + (env.daylight - 0.5) * 8 - (if (env.isPrecipNow()) 2 else 0) - env.W.wind * 2
    }
    fun formatTemp(c: Double) = if (tempUnit == "F") "${Math.round(c * 9 / 5 + 32)}°F" else "${Math.round(c)}°C"

    private fun updateBillboards(t: Float) {
        val now = SystemClock.uptimeMillis()
        val night = 1 - env.daylight
        for (bb in billboards) {
            val flick = if (sin(t * 13 + bb.phase) > 0.97 || sin(t * 0.7 + bb.phase) > 0.995) 0.25 else 1.0
            bb.mat.emissiveIntensity = ((0.3 + 0.7 * night) * flick).toFloat()
            if (bb.flipT < 0 && now > bb.nextAt) {
                val ex = ArrayList<Ad>(); for (o in billboards) { ex += o.cur; o.next?.let { ex += it } }
                bb.next = pickAd(ex)
                drawAd(bb.a, bb.cur, 128, 48)
                drawAd(bb.b, bb.next!!, 128, 48)
                bb.flipT = 0.0
            }
            if (bb.flipT >= 0) {
                // Tri-vision flip: eight vertical slats turn one after another
                bb.flipT += 1.0 / 50
                val ctx = bb.obj.ctx
                ctx.fillStyle = "#05060a"; ctx.fillRect(0, 0, 128, 48)
                for (i in 0 until 8) {
                    val p = max(0.0, min(1.0, (bb.flipT - i * 0.06) / 0.5))
                    val src = if (p < 0.5) bb.a else bb.b
                    val sx = i * 16
                    val scale = abs(cos(p * PI))
                    val dw = 16 * scale
                    ctx.drawImage(src, sx, 0, 16, 48, sx + (16 - dw) / 2, 0, dw, 48)
                }
                bb.obj.tex.needsUpdate()
                if (bb.flipT >= 1) {
                    bb.cur = bb.next!!
                    bb.flipT = -1.0
                    bb.nextAt = now + 15000 + (rnd.nextDouble() * 20000).toLong()
                    drawAd(ctx, bb.cur, 128, 48)
                    bb.obj.tex.needsUpdate()
                }
            }
        }
        // Ticker: refresh ~10x a second
        if (t - tickerLast > 0.1f) {
            tickerLast = t
            val ctx = tickerObj.ctx
            ctx.fillStyle = "#0a0602"; ctx.fillRect(0, 0, 256, 24)
            val temp = formatTemp(currentTempC())
            val W = env.W
            val advisory = if (env.isSnowingNow()) "SNOW ADVISORY · DRIVE SLOW" else (if (env.isRainingNow()) "WET ROADS · DRIVE SAFE" else (if (W.fog > 0.4f) "FOG · LOW VISIBILITY" else "HAVE A GOOD NIGHT"))
            val msg = "   ${env.formatHour(env.hour)}  ·  $temp  ·  ${W.label.uppercase()}  ·  $advisory   "
            ctx.font = "bold 15px monospace"
            ctx.fillStyle = "#ffb020"
            val wMsg = ctx.measureText(msg)
            val off = (t * 30) % wMsg
            ctx.fillText(msg + msg, -off, 18)
            // LED dot grid
            ctx.fillStyle = "rgba(0,0,0,0.35)"
            var x = 0; while (x < 256) { ctx.fillRect(x, 0, 1, 24); x += 2 }
            tickerObj.tex.needsUpdate()
            tickerMat.emissiveIntensity = (0.5 + 0.5 * night).toFloat()
        }
    }

    // ---- Streetlights, facades and the train switch with the time (an env hook) ----
    private fun updateExteriorForTime() {
        val night = 1 - env.daylight
        for (f in facadeSets) {
            val tex = if (env.daylight > 0.55) f.day else f.night
            if (f.mat.map !== tex) { f.mat.map = tex; f.mat.emissiveMap = tex }
            f.mat.emissiveIntensity = (if (env.daylight > 0.55) 0.12 else 0.25 + 0.45 * night).toFloat()
        }
        for (m in streetLampMats) m.emissiveIntensity = (0.05 + 0.95 * Environment.smooth(0.25, 0.7, night)).toFloat()
        trainMat.emissiveIntensity = (0.15 + 0.6 * night).toFloat()
        // Exterior trees: seasonal foliage (the HTML does this in applyEnvironmentSettings)
        val isWinter = env.currentSeason == "winter"
        val cols = when (env.currentSeason) { "fall" -> intArrayOf(0xea580c, 0xdc2626, 0xd97706, 0xf59e0b); "spring" -> intArrayOf(0x16a34a, 0xf472b6, 0x22c55e, 0xfbcfe8); else -> intArrayOf(0x14532d, 0x166534, 0x15803d, 0x1b4332) }
        for (fc in treeFoliageClusters) {
            if (isWinter) fc.mesh.visible = false // Bare branches in winter
            else { fc.mesh.visible = true; fc.mat.color.setHex(cols[fc.idx % cols.size]) }
        }
    }

    /** Per frame (the HTML's updateExteriorFx plus the beacon pulse and tree sway from animate). */
    fun update(t: Float, dt: Float, outsideEnv: String) {
        if (outsideEnv == "city") {
            updateBillboards(t)
            updateTraffic(t, dt)
            trainNext -= dt
            if (trainNext <= 0) {
                trainX += trainDir * trainSpeed * dt
                trainGroup.position.x = (if (trainDir > 0) trainX else -trainX).toFloat()
                if (abs(trainX) < 0.2 && !trainHeard) { trainHeard = true; onTrainPass() }
                if (trainX > 16) {
                    trainX = -22.0; trainDir *= -1; trainHeard = false
                    trainNext = 40 + rnd.nextDouble() * 60
                    trainGroup.position.x = -40f
                }
            }
        }
        // Skyscraper beacons and swaying trees
        val beaconPulse = if (sin(t * 4.2) > 0) 1.0f else 0.1f
        for (bm in roofBeacons) bm.emissiveIntensity = beaconPulse
        val windStrength = 0.015f + env.W.wind * 0.075f
        val windSpeed = 1.6f + env.W.wind * 2.6f
        for ((tree, phase) in exteriorTrees) {
            tree.rotation.z = sin(t * windSpeed + phase) * windStrength
            tree.rotation.x = cos(t * (windSpeed * 0.75f) + phase) * (windStrength * 0.5f)
        }
    }
}
