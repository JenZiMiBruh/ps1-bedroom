package com.ps1bedroom.room

import com.ps1bedroom.room.engine.CanvasTex
import com.ps1bedroom.room.engine.DOUBLE_SIDE
import com.ps1bedroom.room.engine.Geo
import com.ps1bedroom.room.engine.Geometry
import com.ps1bedroom.room.engine.Group
import com.ps1bedroom.room.engine.InstancedMesh
import com.ps1bedroom.room.engine.Material
import com.ps1bedroom.room.engine.Mesh
import com.ps1bedroom.room.engine.PS1
import com.ps1bedroom.room.engine.Texture
import com.ps1bedroom.room.engine.Vec3
import com.ps1bedroom.room.engine.Weather
import com.ps1bedroom.room.engine.createCanvasTex
import com.ps1bedroom.room.engine.weatherCanvas
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * HTML 8225-8445 (+ the lightning in the animate loop): weather and season FX. Every particle type is one
 * InstancedMesh (one draw call): dust in the light beam, rain + splashes, snow, falling leaves / blossoms,
 * fireflies; plus the weather-aware window pane, the seasonal ground outside and the soft window glow.
 */
class WeatherFx(private val room: Room, private val env: Environment, private val sky: Sky) {
    private val scene = room.scene
    private val rnd = Random.Default
    private fun randIn(a: Float, b: Float) = a + rnd.nextFloat() * (b - a)
    private fun rand() = rnd.nextFloat()

    // Outside volume seen through the window
    private object OUT { const val x0 = -1.8f; const val x1 = 3.4f; const val y0 = 0.4f; const val y1 = 4.8f; const val z0 = -3.25f; const val z1 = -6.2f; const val ledgeY = 1.6f }

    private fun makeInstanced(group: Group, geo: Geometry, mat: Material, count: Int): InstancedMesh {
        val mesh = InstancedMesh(geo, mat, count); group.add(mesh); return mesh
    }

    // Dust motes drifting in the moonbeam
    private class Mote(var s: Float, var u: Float, var v: Float, val speed: Float, val phase: Float, val drift: Float)
    private val dustGroup = Group()
    private val dustMesh: InstancedMesh
    private val dustMotes: List<Mote>
    private val dustWhite = com.ps1bedroom.room.engine.Color(0xffffff)
    private val dustTintA = com.ps1bedroom.room.engine.Color(0xffffff); private val dustTintB = com.ps1bedroom.room.engine.Color(0xffffff); private val dustMix = com.ps1bedroom.room.engine.Color(0xffffff)
    /** For the post pass's sky tint (set by the app). */
    var renderer: com.ps1bedroom.room.engine.Renderer? = null
    private val beamOrigin = Vec3(0.8f, 2.6f, -2.95f)
    private val beamDir = Vec3(); private val beamSide = Vec3(); private val beamUp = Vec3()

    // Rain, splashes, snow
    private class Drop(var x: Float, var y: Float, var z: Float, val v: Float, val ph: Float = 0f)
    private class Splash(var life: Float = 0f, var x: Float = 0f, var y: Float = -50f, var z: Float = 0f)
    private val weather3DGroup = Group()
    private val RAIN_COUNT = 260
    private val rainMesh: InstancedMesh
    private val rainDrops: List<Drop>
    private val SPLASH_COUNT = 70
    private val splashMesh: InstancedMesh
    private val splashes = List(SPLASH_COUNT) { Splash() }
    private var splashNext = 0
    private val SNOW_COUNT = 300
    private val snowMesh: InstancedMesh
    private val snowFlakes: List<Drop>

    // Falling autumn leaves / spring blossoms / seasonal & windy outdoor debris (per-instance colour).
    // The leaves on the window ARE the exact same leaves that fall in the wind: they fall, hit the window,
    // roll across the glass pane or sill, and get caught by the wind to fly off into the sky (no fake leaves).
    private class Leaf(
        var x: Float, var y: Float, var z: Float,
        val vy: Float, val vx: Float, val ph: Float, val rs: Float,
        var wait: Float = 0f,
        var state: Int = 0,         // 0 = falling in air, 1 = hit window & rolling on glass or ledge, 2 = flying off window
        var contactTimer: Float = 0f,
        var rollAngle: Float = 0f,
        var rollSpeed: Float = 0f,
        var vxOff: Float = 0f,
        var vyOff: Float = 0f,
        var vzOff: Float = 0f,
        var rotX: Float = 0f,
        var rotY: Float = 0f,
        var rotZ: Float = 0f
    )
    private val foliage3DGroup = Group()
    private val LEAF_COUNT = 160
    private val leafMesh: InstancedMesh
    private val leaves: List<Leaf>
    private val FALL_LEAF_COLS = intArrayOf(0xea580c, 0xdc2626, 0xeab308, 0xd97706, 0x92400e)
    private val SPRING_PETAL_COLS = intArrayOf(0xfbcfe8, 0xf9a8d4, 0xffffff, 0xf472b6, 0x86efac)
    private val SUMMER_DEBRIS_COLS = intArrayOf(0xfef9c3, 0xecfccb, 0x4ade80, 0x22c55e, 0xfde047)
    private val WINTER_DEBRIS_COLS = intArrayOf(0xe2e8f0, 0xcbd5e1, 0x94a3b8, 0x78350f, 0x14532d)
    private val DESERT_DEBRIS_COLS = intArrayOf(0xfde68a, 0xf59e0b, 0xd97706, 0xb45309, 0xa16207)
    private var leafPalette: IntArray? = null
    // User-configurable debris / leaf mode & multipliers (mode: "weather" | "always" | "off", plus amount, speed, frequency)
    var debrisMode = "weather"
        set(v) { field = v; updateSettings() }
    var debrisAmount = 1.0f; private set
    var debrisSpeed = 1.0f; private set
    var debrisFreq = 1.0f; private set
    /** Controls the percentage (0..1) of leaves that drift toward and hit the window glass & sill. */
    var windowHitRate = 0.50f; private set
    /** User-configurable lightning frequency ("off", "rare", "normal", "frequent", "constant"). */
    var lightningFreq = "normal"
    private var lastWeatherKind = ""
    private var stormIntroTimer = 0f
    private var lightningCooldown = 0f

    fun onLightningFreqChanged() {
        lightningCooldown = 0f
        if (lightningFreq != "off") {
            // Immediate lightning flash and acoustic thunderclap so user gets instant response to the setting
            triggerLightningStrike(instantThunder = true)
        }
    }

    fun triggerLightningStrike(instantThunder: Boolean = false) {
        if (lightningFreq == "off") return
        val isStorm = env.W.base == "storm"
        lightningFlash = if (isStorm || instantThunder) 1.0f else 0.7f
        lightningCooldown = nextLightningInterval()
        if (isStorm || instantThunder) onThunder?.invoke()
    }

    private fun nextLightningInterval(): Float = when (lightningFreq) {
        "constant" -> 0.7f + rand() * 0.9f    // 0.7s - 1.6s between strikes
        "frequent" -> 2.0f + rand() * 1.6f    // 2.0s - 3.6s
        "rare"     -> 14.0f + rand() * 8.0f   // 14s - 22s
        else       -> 5.0f + rand() * 4.0f    // 5.0s - 9.0s ("normal")
    }
    fun setDebrisParams(amount: Float, speed: Float, freq: Float, windowHits: Float = windowHitRate) {
        debrisAmount = amount.coerceIn(0f, 2.5f)
        debrisSpeed = speed.coerceIn(0.2f, 2.5f)
        debrisFreq = freq.coerceIn(0.2f, 2.5f)
        windowHitRate = windowHits.coerceIn(0f, 1f)
        updateSettings()
    }
    fun setWindowHitRate(rate: Float) {
        windowHitRate = rate.coerceIn(0f, 1f)
    }
    // Exterior window ledge weather accumulation (puffy snow bank in winter)
    private val ledgeSnowGroup = Group()

    // Fireflies blinking around the trees on clear summer nights
    private class Firefly(val x: Float, val y: Float, val z: Float, val ph: Float, val sp: Float)
    private val fireflyGroup = Group()
    private val FIREFLY_COUNT = 30
    private val fireflyMesh: InstancedMesh
    private val fireflies: List<Firefly>

    // Window pane, ground, glow
    private class GlassDrop(var x: Float, var y: Float, var r: Float, var v: Float, var age: Float = 0f)
    private val glassCanvasObj: CanvasTex
    private val glassDrops = ArrayList<GlassDrop>()
    private var glassAccum = 0f
    private var glassKey = ""
    private val groundCanvasObj: CanvasTex
    private val windowGlowMat: Material
    /** The ground below the window (hidden 40 floors up). */
    val outdoorGround: Mesh

    /** The whole-sky flash; the sky reads it, the room lights spike with it. */
    var lightningFlash = 0f; private set
    /** Fired on a storm's flash (the thunderclap comes with the audio phase). */
    var onThunder: (() -> Unit)? = null

    init {
        scene.add(dustGroup)
        val dustMat = PS1.material(color = 0xffffff, emissive = 0x60a5fa, emissiveIntensity = 0.85f)
        dustMesh = makeInstanced(dustGroup, Geo.box(0.02f, 0.02f, 0.02f), dustMat, 120)   // v0.41: bigger, more of them, brighter
        dustMotes = List(120) { Mote(rand(), randIn(-1f, 1f), randIn(-1f, 1f), randIn(0.2f, 0.6f), randIn(0f, 6.28f), randIn(0.01f, 0.03f)) }

        scene.add(weather3DGroup)
        rainMesh = makeInstanced(weather3DGroup, Geo.box(0.012f, 0.36f, 0.012f), PS1.basic(color = 0xbfdbfe, transparent = true, opacity = 0.55f, depthWrite = false), RAIN_COUNT)
        rainDrops = List(RAIN_COUNT) { Drop(randIn(OUT.x0, OUT.x1), randIn(OUT.y0, OUT.y1), randIn(OUT.z0, OUT.z1), randIn(7f, 10f)) }
        splashMesh = makeInstanced(weather3DGroup, Geo.sphere(0.03f, 5, 3, 0f, (PI * 2).toFloat(), 0f, (PI / 2).toFloat()),
            PS1.basic(color = 0xdbeafe, transparent = true, opacity = 0.6f, depthWrite = false), SPLASH_COUNT)
        snowMesh = makeInstanced(weather3DGroup, Geo.box(0.04f, 0.04f, 0.04f), PS1.basic(color = 0xffffff, transparent = true, opacity = 0.9f), SNOW_COUNT)
        snowFlakes = List(SNOW_COUNT) { Drop(randIn(OUT.x0, OUT.x1), randIn(OUT.y0, OUT.y1), randIn(OUT.z0, OUT.z1), randIn(0.45f, 0.95f), randIn(0f, 6.28f)) }

        scene.add(foliage3DGroup)
        leafMesh = makeInstanced(foliage3DGroup, Geo.plane(0.085f, 0.065f), PS1.basic(color = 0xffffff, side = DOUBLE_SIDE), LEAF_COUNT)
        leaves = List(LEAF_COUNT) { idx ->
            val nearWindow = idx < (LEAF_COUNT * (0.08f + windowHitRate * 0.35f)).toInt()
            val z = if (nearWindow) randIn(-3.24f, -3.15f) else randIn(OUT.z0, OUT.z1)
            val preHit = idx < (4 * windowHitRate).roundToInt()
            val leaf = Leaf(
                if (preHit) randIn(-0.2f, 1.8f) else randIn(OUT.x0, OUT.x1),
                if (preHit) randIn(1.60f, 2.2f) else randIn(OUT.y0 + 0.6f, OUT.y1),
                if (preHit) -3.15f else z,
                randIn(0.4f, 0.95f),
                randIn(0.2f, 0.6f),
                randIn(0f, 6.28f),
                randIn(1.5f, 4f)
            )
            if (preHit) {
                leaf.state = 1
                leaf.contactTimer = randIn(3.0f, 6.5f)
                leaf.rollSpeed = randIn(0.20f, 0.40f)
            }
            leaf
        }
        recolorLeaves(FALL_LEAF_COLS)

        scene.add(fireflyGroup)
        fireflyMesh = makeInstanced(fireflyGroup, Geo.sphere(0.028f, 5, 4), PS1.basic(color = 0xd9f99d), FIREFLY_COUNT)
        fireflies = List(FIREFLY_COUNT) { Firefly(randIn(-1.6f, 3.3f), randIn(0.7f, 3.2f), randIn(-3.6f, -5.8f), randIn(0f, 6.28f), randIn(0.3f, 0.8f)) }

        // Window pane: beads of rain that run down the glass, frost from the corners, condensation in fog
        glassCanvasObj = createCanvasTex(128, 100) { _, _, _ -> }
        val windowGlass = Mesh(Geo.plane(2.74f, 2.14f), PS1.basic(map = glassCanvasObj.tex, transparent = true, depthWrite = false))
        windowGlass.position.set(0.8f, 2.65f, -3.13f)
        scene.add(windowGlass)

        // Seasonal ground below the window (grass, flowering grass, fallen leaves, snow; darker when wet)
        groundCanvasObj = createCanvasTex(128, 128, 7f, 5f) { _, _, _ -> }
        outdoorGround = Mesh(Geo.plane(26f, 12.2f, 8, 4), Material(map = groundCanvasObj.tex, ps1 = false)) // MeshLambertMaterial
        outdoorGround.rotation.x = (-PI / 2).toFloat()
        outdoorGround.position.set(0.8f, 0.32f, -9.4f)
        scene.add(outdoorGround)

        // Soft glow just outside the window so it reads as the room's light source
        val glow = createCanvasTex(64, 64) { ctx, _, _ ->
            val g = ctx.createRadialGradient(32, 32, 2, 32, 32, 32)
            g.addColorStop(0, "rgba(255, 255, 255, 0.9)"); g.addColorStop(0.5, "rgba(255, 255, 255, 0.3)"); g.addColorStop(1, "rgba(255, 255, 255, 0)")
            ctx.fillStyle = g; ctx.fillRect(0, 0, 64, 64)
        }.tex
        glow.nearest = false // a plain THREE.CanvasTexture filters linearly
        windowGlowMat = Material(map = glow, transparent = true, additive = true, depthWrite = false, opacity = 0.35f, lit = false, ps1 = false)
        val windowGlow = Mesh(Geo.plane(4.2f, 3.4f), windowGlowMat)
        windowGlow.position.set(0.8f, 2.75f, -3.45f)
        scene.add(windowGlow)

        // Exterior window ledge accumulation: puffy snow drift in winter/snow
        weather3DGroup.add(ledgeSnowGroup)
        val snowDriftMat = PS1.basic(color = 0xf1f5f9)
        for ((dx, dw, dh) in arrayOf(Triple(-0.15f, 0.95f, 0.055f), Triple(0.78f, 1.15f, 0.068f), Triple(1.68f, 0.88f, 0.052f))) {
            val bank = Mesh(Geo.box(dw, dh, 0.14f, 3, 1, 1), snowDriftMat)
            bank.position.set(dx, OUT.ledgeY - 0.02f + dh * 0.5f, -3.20f)
            ledgeSnowGroup.add(bank)
        }
        ledgeSnowGroup.visible = false
    }

    /** Everything here moves every frame: none of it takes part in static batching. */
    fun dynamicRoots() = listOf(dustGroup, weather3DGroup, foliage3DGroup, fireflyGroup)

    private fun recolorLeaves(palette: IntArray) {
        if (leafPalette === palette) return
        leafPalette = palette
        for (i in 0 until LEAF_COUNT) leafMesh.setColorAt(i, palette[i % palette.size])
        leafMesh.needsUpdate()
    }

    private fun drawWindowGlass(dt: Float) {
        val raining = env.isRainingNow()
        val snowing = env.isSnowingNow()
        val winter = env.currentSeason == "winter"
        val key = "${env.currentWeather}|${env.weatherKind}|$raining|$snowing|$winter|${env.currentSeason}"
        // Nothing on the glass changes between draws unless drops are moving or the weather changed
        if (glassDrops.isEmpty() && !raining && key == glassKey) return
        glassKey = key
        val ctx = glassCanvasObj.ctx
        val w = 128; val h = 100
        ctx.clearRect(0, 0, w, h)
        ctx.fillStyle = "rgba(170, 190, 225, 0.05)"
        ctx.fillRect(0, 0, w, h)
        // Condensation in fog / rain / cold snow
        if (env.currentWeather == "foggy" || raining || snowing) {
            ctx.fillStyle = if (env.currentWeather == "foggy") "rgba(210, 220, 235, 0.22)" else if (snowing) "rgba(220, 232, 250, 0.10)" else "rgba(200, 210, 230, 0.08)"
            ctx.fillRect(0, 0, w, h)
        }
        // Heatwave warm sun-shimmer haze along the window edges
        if (env.W.base == "heat") {
            val heat = ctx.createRadialGradient(w / 2, h / 2, min(w, h) * 0.2, w / 2, h / 2, max(w, h) * 0.72)
            heat.addColorStop(0, "rgba(255, 200, 110, 0)")
            heat.addColorStop(1, "rgba(255, 190, 95, 0.22)")
            ctx.fillStyle = heat; ctx.fillRect(0, 0, w, h)
        }
        // Frost creeping in from the edges in winter or snow
        if (winter || snowing) {
            val frost = ctx.createRadialGradient(w / 2, h / 2, min(w, h) * 0.25, w / 2, h / 2, max(w, h) * 0.7)
            frost.addColorStop(0, "rgba(230, 240, 255, 0)")
            frost.addColorStop(1, if (env.weatherKind == "blizzard") "rgba(235, 245, 255, 0.72)" else "rgba(230, 240, 255, 0.55)")
            ctx.fillStyle = frost
            ctx.fillRect(0, 0, w, h)
            ctx.fillStyle = "rgba(255, 255, 255, 0.42)"
            for (i in 0 until 140) {
                val a = (i * 2.399) % 6.283
                val r = 0.40 + ((i * 37) % 23) / 60.0
                ctx.fillRect((w / 2 + cos(a) * r * w * 0.7).toInt(), (h / 2 + sin(a) * r * h * 0.7).toInt(), 1, 1)
            }
            if (snowing) {
                // Snow build-up along the bottom window sill and corners
                ctx.fillStyle = "rgba(241, 245, 249, 0.78)"
                for (x in 0 until w) {
                    val corner = min(1.0, kotlin.math.abs(x - w / 2.0) / (w * 0.48))
                    val sh = (2.5 + corner * 5.5 + sin(x * 0.22) * 1.4).toInt()
                    ctx.fillRect(x, h - sh, 1, sh)
                }
            }
        }
        // Raindrops beading on the glass; every drop grows in the rain and eventually streaks down so they never stall
        if (raining) {
            val maxDrops = (45 + env.W.rain * 55).roundToInt()
            val spawn = (1 + env.W.rain * 5).roundToInt()
            for (i in 0 until spawn) if (glassDrops.size < maxDrops) {
                glassDrops += GlassDrop(rand() * w, rand() * (h - 6), 0.75f + rand() * 1.35f, 0f, 0f)
            }
        }
        val windSlant = 0.08f + env.W.wind * 0.30f
        var i = glassDrops.size - 1
        while (i >= 0) {
            val d = glassDrops[i]
            d.age += dt
            if (raining && d.v == 0f) {
                // Static beads collect more water and grow until gravity pulls them down
                d.r += dt * (0.24f + env.W.rain * 0.38f) * (0.5f + rand())
                if (d.r > 1.5f || (d.age > 3.2f && rand() < 0.25f)) {
                    d.v = 11f + rand() * 20f + env.W.rain * 14f
                }
            } else if (!raining && d.v > 0f) {
                d.v *= max(0f, 1f - dt * 0.8f)
            }
            if (d.v > 0f) {
                val streakLen = min(10f, 3.5f + d.v * 0.25f)
                ctx.strokeStyle = "rgba(220, 235, 255, 0.22)"
                // Trail extends up (+X, -Y) behind the drop as the drop streaks down-left (-X, +Y), matching 3D rain & leaves
                ctx.beginPath(); ctx.moveTo(d.x, d.y); ctx.lineTo(d.x + windSlant * streakLen, d.y - streakLen); ctx.stroke()
                d.y += d.v * dt
                d.x -= (windSlant * d.v + (rand() - 0.5f) * 2.2f) * dt
            }
            if (!raining && rand() < 0.08f) d.r -= 0.25f // dries up when the rain stops
            if (d.y > h + 3 || d.x < -3 || d.x > w + 3 || d.r <= 0.2f) { glassDrops.removeAt(i); i--; continue }
            ctx.fillStyle = "rgba(15, 23, 42, 0.25)"
            ctx.beginPath(); ctx.arc(d.x, d.y + 0.4f, d.r, 0, PI * 2); ctx.fill()
            ctx.fillStyle = "rgba(225, 238, 255, 0.58)"
            ctx.beginPath(); ctx.arc(d.x, d.y, d.r * 0.8f, 0, PI * 2); ctx.fill()
            ctx.fillStyle = "rgba(255, 255, 255, 0.9)"
            ctx.fillRect((d.x - d.r * 0.3f).toInt(), (d.y - d.r * 0.4f).toInt(), 1, 1)
            i--
        }
        glassCanvasObj.tex.needsUpdate()
    }

    /** Paints the ground for anywhere but the city; returns false to use the seasonal city ground (the places phase sets it). */
    var outsideGroundPaint: ((com.ps1bedroom.room.engine.Canvas2D, Int, Int) -> Boolean)? = null
    private fun drawOutdoorGround() {
        val ctx = groundCanvasObj.ctx
        val w = 128; val h = 128
        if (outsideGroundPaint?.invoke(ctx, w, h) != true) {
            val season = env.currentSeason
            ctx.fillStyle = mapOf("summer" to "#1f4d24", "spring" to "#2f6b2a", "fall" to "#5b3a1a", "winter" to "#dfe7f1").getValue(season)
            ctx.fillRect(0, 0, w, h)
            val specks = when (season) {
                "summer" -> arrayOf("#2d6a32", "#163a1a", "#3f7d3a")
                "spring" -> arrayOf("#3f8f37", "#f9a8d4", "#ffffff", "#facc15")
                "fall" -> arrayOf("#c2410c", "#b45309", "#eab308", "#7c2d12", "#365314")
                else -> arrayOf("#ffffff", "#c7d2e2", "#e5edf7")
            }
            val sz = if (season == "fall") 2 else 1
            for (i in 0 until 2600) {
                ctx.fillStyle = specks[i % specks.size]
                ctx.fillRect((rand() * w).toInt(), (rand() * h).toInt(), sz, sz)
            }
        }
        weatherCanvas(ctx, w, h, Weather(seed = 91.0, blotch = 0.35, speckle = 0.1))
        if (env.isPrecipNow()) {
            ctx.fillStyle = if (env.isSnowingNow()) "rgba(255, 255, 255, 0.25)" else "rgba(0, 10, 25, 0.35)"
            ctx.fillRect(0, 0, w, h)
        }
        groundCanvasObj.tex.needsUpdate()
    }

    private var groundKey = ""
    /** Called from applyEnvironmentSettings when the season / weather / time changes. */
    fun updateSettings() {
        val precip = env.isPrecipNow()
        val snowing = env.isSnowingNow()
        rainMesh.visible = env.isRainingNow()
        splashMesh.visible = env.isRainingNow()
        snowMesh.visible = snowing
        ledgeSnowGroup.visible = env.currentSeason == "winter" || snowing
        val hasTrees = Places.OUTSIDE_ENVS[sky.outsideEnv]?.trees == true || sky.outsideEnv in setOf("suburbs", "house", "forest", "lake", "mountains", "countryside", "neighborhood", "city")
        if (env.weatherKind != lastWeatherKind) {
            if (env.weatherKind == "storm" || env.W.base == "storm") stormIntroTimer = 0.65f
            lastWeatherKind = env.weatherKind
        }
        // Falling leaves, blossoms & outdoor debris tied to weather and trees (with user mode: "weather", "always", "off")
        val noDebrisPlace = sky.outsideEnv in setOf("space", "underwater", "ocean")
        val desertPlace = sky.outsideEnv in setOf("desert", "canyon", "pyramid", "redrock", "oasis", "beach", "arizona")
        val weatherActive = when (debrisMode) {
            "off" -> false
            "always" -> true
            else -> {
                // "weather": tied to wind, rain/storm, season, and whether the place has trees (or desert wind dust)
                val windyOrStormy = env.W.wind >= 0.22f || env.isRainingNow() || env.W.base == "storm"
                val seasonalTrees = hasTrees && !snowing && (env.currentSeason == "fall" || env.currentSeason == "spring" || windyOrStormy)
                val desertWind = desertPlace && env.W.wind >= 0.45f
                seasonalTrees || desertWind
            }
        }
        foliage3DGroup.visible = !noDebrisPlace && debrisAmount > 0.01f && weatherActive
        val palette = when {
            desertPlace -> DESERT_DEBRIS_COLS
            env.currentSeason == "fall" -> FALL_LEAF_COLS
            env.currentSeason == "spring" -> SPRING_PETAL_COLS
            env.currentSeason == "winter" -> WINTER_DEBRIS_COLS
            else -> SUMMER_DEBRIS_COLS
        }
        recolorLeaves(palette)
        fireflyGroup.visible = (env.currentSeason == "summer" || env.currentSeason == "spring") && (env.currentTime == "night" || env.currentTime == "dusk") && !precip && env.W.wind < 0.6f
        windowGlowMat.color.copy(room.moonLight.color)
        // Dust motes catch whatever is shining in: warm sunlight, orange at sunset, cool moonlight (native: the HTML keeps them blue)
        // v0.41: each speck sits somewhere between the light shining in and the sky's horizon colour
        val sunHex = room.moonLight.color.getHex(); val skyHex = sky.horizonColor.getHex()
        dustTintA.setHex(sunHex); dustTintB.setHex(skyHex)
        for (i in dustMotes.indices) {
            val k = (i % 5) / 4f
            dustMix.copy(dustTintA).lerp(dustTintB, k * 0.7f).lerp(dustWhite, 0.25f)
            dustMesh.setColorAt(i, dustMix.getHex())
        }
        dustMesh.material.color.setHex(0xffffff)
        dustMesh.material.emissive.copy(dustTintA).lerp(dustTintB, 0.35f)
        renderer?.post?.skyTint?.copy(sky.horizonColor)
        // Kept faint: it sits over the whole view outside, and stronger values wash out the scenery (fog still thickens it)
        val glowBase = 0.1f + 0.08f * env.daylight.toFloat()
        val glowWeather = if (env.currentWeather == "foggy") 1.4f else if (env.currentWeather == "storm") 0.55f else if (precip) 0.75f else 1f
        windowGlowMat.opacity = (if (room.blindsOpenState == 2) 0.05f else glowBase) * glowWeather
        // The ground only changes with the season, the weather and the place (the HTML repaints it every call)
        val key = "${env.currentSeason}|$precip|${env.isSnowingNow()}|${sky.outsideEnv}"
        if (key != groundKey) { groundKey = key; drawOutdoorGround() }
    }

    /** Per-frame particle motion. */
    fun update(t: Float, dt: Float) {
        // Dust floats inside the light beam, wherever the sun or moon is shining in from (and fades with it)
        val tp = Room.roomLightTargetPos
        beamDir.set(tp.x - beamOrigin.x, tp.y - beamOrigin.y, tp.z - beamOrigin.z).normalize()
        beamSide.set(-beamDir.z, 0f, beamDir.x).normalize()
        beamUp.crossVectors(beamSide, beamDir).normalize()
        val beamLen = 4.2f
        dustGroup.visible = env.moonBaseIntensity > 0.25f && room.blindsOpenState != 2
        if (dustGroup.visible) {
            for (i in dustMotes.indices) {
                val m = dustMotes[i]
                m.s += m.drift * dt * 0.6f
                if (m.s > 1) { m.s = 0f; m.u = randIn(-1f, 1f); m.v = randIn(-1f, 1f) }
                val d = m.s * beamLen
                val spread = 0.8f + m.s * 0.5f // the beam widens a little as it crosses the room
                val su = m.u * spread * 0.75f + cos(t * m.speed + m.phase) * 0.06f
                val sv = m.v * spread * 0.55f + sin(t * m.speed * 0.8f + m.phase) * 0.08f
                val px = beamOrigin.x + beamDir.x * d + beamSide.x * su + beamUp.x * sv
                val py = beamOrigin.y + beamDir.y * d + beamSide.y * su + beamUp.y * sv
                val pz = beamOrigin.z + beamDir.z * d + beamSide.z * su + beamUp.z * sv
                val inRoom = pz > -2.9f && py > 0.1f && py < 4.9f && px > Room.MIN_X + 0.1f && px < Room.MAX_X - 0.1f
                // Specks only catch the light now and then, brightest in the middle of the beam and fading at both ends
                val edge = max(0f, 1 - (m.u * m.u + m.v * m.v) * 0.7f)
                val ends = min(1f, m.s * 6) * min(1f, (1 - m.s) * 3)
                val glint = max(0f, sin(t * m.speed * 1.7f + m.phase * 3))
                val k = if (inRoom) edge * ends * (0.5f + 0.7f * glint) else 0f   // (brighter and bigger at the glint)
                dustMesh.setInstance(i, px, py, pz, 0f, t * m.speed, 0f, k)
            }
            dustMesh.needsUpdate()
        }

        val W = env.W
        if (rainMesh.visible) {
            val active = (RAIN_COUNT * (0.15f + 0.85f * W.rain)).roundToInt()
            val slant = 0.08f + W.wind * 0.3f
            for (i in 0 until RAIN_COUNT) {
                val d = rainDrops[i]
                if (i >= active) { rainMesh.setInstance(i, 0f, -50f, 0f, 0f, 0f, 0f, 0f); continue }
                d.y -= d.v * (0.9f + W.rain * 0.45f) * dt
                d.x -= d.v * slant * dt
                val overLedge = d.x > -0.6f && d.x < 2.2f && d.z > -3.42f
                val floorY = if (overLedge) OUT.ledgeY else OUT.y0
                if (d.y < floorY) {
                    if (rand() < 0.6f) {
                        val s = splashes[splashNext]
                        splashNext = (splashNext + 1) % SPLASH_COUNT
                        s.life = 0.25f; s.x = d.x; s.y = floorY; s.z = d.z
                    }
                    d.y = OUT.y1 + rand() * 0.5f
                    d.x = randIn(OUT.x0, OUT.x1 + 1.2f)
                    d.z = randIn(OUT.z0, OUT.z1)
                }
                rainMesh.setInstance(i, d.x, d.y, d.z, 0f, 0f, -slant, 1f)
            }
            rainMesh.needsUpdate()
            for (i in 0 until SPLASH_COUNT) {
                val s = splashes[i]
                if (s.life > 0) s.life -= dt
                val k = if (s.life > 0) 1.6f - s.life * 4 else 0f
                splashMesh.setInstance(i, s.x, s.y, s.z, 0f, 0f, 0f, max(0f, k))
            }
            splashMesh.needsUpdate()
        }
        if (snowMesh.visible) {
            val blow = 0.1f + W.wind * 1.6f
            val activeSnow = (SNOW_COUNT * (0.2f + 0.8f * W.rain)).roundToInt()
            for (i in 0 until SNOW_COUNT) {
                val f = snowFlakes[i]
                if (i >= activeSnow) { snowMesh.setInstance(i, 0f, -50f, 0f, 0f, 0f, 0f, 0f); continue }
                f.y -= f.v * (0.8f + W.wind * 0.9f) * dt
                f.x += (sin(t * 1.7f + f.ph) * 0.35f - blow) * dt
                if (f.y < OUT.y0 || f.x < OUT.x0 - 0.5f) {
                    f.y = OUT.y1 + rand() * 0.4f
                    f.x = randIn(OUT.x0, OUT.x1 + W.wind * 2.5f)
                }
                snowMesh.setInstance(i, f.x, f.y, f.z, t * f.v, t * f.v * 0.7f, 0f, 1f)
            }
            snowMesh.needsUpdate()
        }
        if (foliage3DGroup.visible) {
            val weatherFactor = if (debrisMode == "always") {
                0.55f + W.wind * 0.35f
            } else {
                val seasonBase = when (env.currentSeason) {
                    "fall" -> 0.38f
                    "spring" -> 0.30f
                    else -> 0.08f
                }
                val rainBoost = if (env.isRainingNow()) 0.18f + W.rain * 0.22f else 0f
                val stormBoost = if (W.base == "storm") 0.25f else 0f
                seasonBase + W.wind * 0.55f + rainBoost + stormBoost
            }
            val activeCount = (LEAF_COUNT * (weatherFactor * (debrisAmount / 1.35f)).coerceIn(0.05f, 1f)).roundToInt()
            val gust = (0.65f + W.wind * 1.75f + (if (env.isRainingNow()) W.rain * 0.35f else 0f)) * debrisSpeed
            val windSlantX = (0.12f + W.wind * 1.45f) * debrisSpeed
            val freqBoost = max(0.2f, debrisFreq * (0.7f + W.wind * 0.9f))
            val scale = when {
                sky.outsideEnv in setOf("desert", "canyon", "pyramid", "redrock", "oasis", "beach", "arizona") -> 0.48f
                env.currentSeason == "spring" -> 0.68f
                env.currentSeason == "summer" -> 0.58f
                env.currentSeason == "winter" -> 0.55f
                else -> 1.0f
            }
            for (i in 0 until LEAF_COUNT) {
                val l = leaves[i]
                if (i >= activeCount) {
                    leafMesh.setInstance(i, 0f, -50f, 0f, 0f, 0f, 0f, 0f)
                    continue
                }
                if (l.wait > 0f) {
                    l.wait -= dt * freqBoost
                    leafMesh.setInstance(i, 0f, -50f, 0f, 0f, 0f, 0f, 0f)
                    if (l.wait <= 0f) {
                        l.y = OUT.y1 + rand() * 0.35f
                        l.x = randIn(OUT.x0, OUT.x1 + 1.2f + W.wind * 1.2f)
                        l.z = if (rand() < windowHitRate * 0.65f) randIn(-3.24f, -3.15f) else randIn(OUT.z0, OUT.z1)
                        l.state = 0
                    }
                    continue
                }

                when (l.state) {
                    0 -> {
                        // Real-life leaf terminal falling speed: gentle realistic glide (0.42 - 0.72 m/s)
                        val fallSpeed = (0.42f + l.vy * 0.30f) * debrisSpeed
                        val windDrift = (0.22f + W.wind * 0.75f) * debrisSpeed
                        val sway = sin(t * (1.6f + l.rs * 0.3f) + l.ph) * 0.28f
                        l.y -= fallSpeed * dt
                        l.x -= (windDrift + sway) * dt
                        l.z += cos(t * 1.2f + l.ph) * 0.04f * dt

                        // Smooth organic aerial tumble
                        l.rotX = t * l.rs * 0.7f * debrisSpeed
                        l.rotY = t * l.rs * 0.5f * debrisSpeed
                        l.rotZ = -0.22f - W.wind * 0.32f + sin(t * 2.0f + l.ph) * 0.45f

                        // Collision with window glass or exterior sill:
                        // Window pane: X in [-0.55f, 2.15f], Y in [1.58f, 3.75f], Z near glass [-3.26f, -3.13f]
                        val inWindowX = l.x in -0.55f..2.15f
                        val inWindowY = l.y in 1.58f..3.75f
                        val hitGlassZ = l.z in -3.26f..-3.13f
                        if (inWindowX && inWindowY && hitGlassZ && (windowHitRate > 0.01f && (rand() < windowHitRate * 1.4f || windowHitRate >= 0.95f))) {
                            // HIT THE WINDOW!
                            l.state = 1
                            l.contactTimer = randIn(3.0f, 7.5f) / max(0.5f, debrisFreq)
                            l.rollSpeed = randIn(0.18f, 0.40f) * (0.8f + W.wind * 0.6f) * debrisSpeed
                            l.z = if (l.y > 1.62f) -3.15f else -3.18f
                        } else if (l.y < OUT.y0 + 0.1f || l.x < OUT.x0 - 0.8f || l.x > OUT.x1 + 1.8f) {
                            l.wait = randIn(0.1f, 1.5f) / freqBoost
                        }
                    }
                    1 -> {
                        // HIT THE WINDOW: rolling & sliding across the glass pane or sill
                        l.contactTimer -= dt
                        if (l.y > 1.61f) {
                            // Sliding down and rolling sideways along the window glass pane
                            l.x -= (l.rollSpeed + W.wind * 0.28f) * dt
                            l.y -= (0.10f + if (env.isRainingNow()) 0.30f else 0.05f) * dt
                            l.z = -3.15f
                            l.rollAngle += l.rollSpeed * 5.0f * dt
                            l.rotX = sin(t * 6f + l.ph) * 0.10f
                            l.rotY = cos(t * 3f + l.ph) * 0.08f
                            l.rotZ = l.rollAngle
                        } else {
                            // Reached the exterior window sill: rolls along the ledge
                            l.y = OUT.ledgeY // 1.60f
                            l.z = -3.18f
                            l.x -= (l.rollSpeed * 0.8f + W.wind * 0.32f) * dt
                            l.rollAngle += l.rollSpeed * 4.0f * dt
                            l.rotX = (-PI / 2).toFloat() + sin(t * 7f + l.ph) * (0.04f + W.wind * 0.12f)
                            l.rotY = sin(t * 4f + l.ph) * 0.08f
                            l.rotZ = l.rollAngle
                        }

                        // Wind catches it and it takes flight off the window!
                        if (l.contactTimer <= 0f || l.x < -0.55f || (W.wind >= 0.75f && rand() < 0.02f)) {
                            l.state = 2 // FLY OFF!
                            l.vxOff = -(0.75f + W.wind * 1.55f) * debrisSpeed
                            l.vyOff = (0.35f + W.wind * 0.60f) * debrisSpeed // updraft lift away from sill
                            l.vzOff = -(0.30f + rand() * 0.45f) // blown backward into open street air
                        }
                    }
                    2 -> {
                        // FLYING OFF: carried away into the wind
                        l.x += l.vxOff * dt
                        l.y += l.vyOff * dt
                        l.z += l.vzOff * dt
                        l.vyOff -= 0.32f * dt
                        l.rotX += (4f + l.rs) * dt
                        l.rotY += (5f + l.rs) * dt
                        l.rotZ += (6f + l.rs) * dt

                        if (l.x < OUT.x0 - 0.8f || l.y < OUT.y0 || l.z < OUT.z1) {
                            l.wait = randIn(0.1f, 1.2f) / freqBoost
                        }
                    }
                }

                leafMesh.setInstance(i, l.x, l.y, l.z, l.rotX, l.rotY, l.rotZ, scale)
            }
            leafMesh.needsUpdate()
        }
        if (fireflyGroup.visible) {
            for (i in 0 until FIREFLY_COUNT) {
                val f = fireflies[i]
                val blink = max(0f, sin(t * f.sp * 3 + f.ph * 5))
                fireflyMesh.setInstance(i, f.x + sin(t * f.sp + f.ph) * 0.4f, f.y + sin(t * f.sp * 1.3f + f.ph) * 0.25f,
                    f.z + cos(t * f.sp * 0.7f + f.ph) * 0.3f, 0f, 0f, 0f, if (blink > 0.6f) blink else 0f)
            }
            fireflyMesh.needsUpdate()
        }
        glassAccum += dt
        if (glassAccum >= 0.08f) {
            drawWindowGlass(glassAccum.coerceAtMost(0.25f))
            glassAccum = 0f
        }
    }

    /** The animate loop's lightning: a random flash in storms (and far-off heat lightning), the room lights spiking with it. */
    fun updateLightning(frameScale: Float) {
        val W = env.W
        val dtSec = frameScale / 60f
        if (lightningCooldown > 0f) lightningCooldown = max(0f, lightningCooldown - dtSec)

        val triggerIntro = if (stormIntroTimer > 0f) {
            stormIntroTimer -= dtSec
            stormIntroTimer <= 0f && (W.base == "storm" || W.lightning) && lightningFreq != "off"
        } else false

        val hasLightning = W.lightning || (lightningFreq == "constant" && W.base in listOf("storm", "heat", "rain", "downpour", "overcast", "windy"))
        if (triggerIntro || (hasLightning && lightningFreq != "off" && lightningCooldown <= 0f)) {
            triggerLightningStrike()
        }

        if (lightningFlash > 0) {
            lightningFlash = max(0f, lightningFlash - 0.08f * frameScale)
            room.moonLight.intensity = env.moonBaseIntensity + lightningFlash * 5.0f
            room.ambientLight.intensity = env.ambientBaseIntensity + lightningFlash * 1.4f
        }
        sky.lightningFlash = lightningFlash
    }
}
