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
    private object OUT { const val x0 = -1.8f; const val x1 = 3.4f; const val y0 = 0.4f; const val y1 = 4.8f; const val z0 = -3.25f; const val z1 = -6.2f; const val ledgeY = 1.6f; const val wallZ = -3.23f }

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
    /** Leaves that blew in through the window: false = they fade away after INDOOR_LEAF_LIFE s on the floor, true = they stay. */
    var indoorLeavesStay = false
    private val INDOOR_LEAF_LIFE = 45f
    /** User-configurable lightning frequency ("off", "rare", "normal", "frequent", "constant"). */
    var lightningFreq = "normal"
    private var lastWeatherKind = ""
    private var stormIntroTimer = 0f
    private var lightningCooldown = 0f

    var onThunder: (() -> Unit)? = null

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
        if (isStorm || instantThunder) {
            onThunder?.invoke()
        }
    }

    /** Unconditionally triggers a bright lightning flash and loud thunderclap immediately (for debug testing). */
    fun forceLightningStrike() {
        lightningFlash = 1.0f
        lightningCooldown = nextLightningInterval()
        onThunder?.invoke()
    }

    /** Spawns a burst of leaves positioned to drift into and strike the window pane (for instant visual testing). */
    fun spawnLeafFlurry(count: Int = 20) {
        var spawned = 0
        for (l in leaves) {
            if (spawned >= count) break
            l.state = 0
            l.wait = 0f
            l.x = randIn(0.2f, 1.8f)
            l.y = randIn(2.8f, 4.2f)
            l.z = randIn(-3.24f, -3.15f)
            spawned++
        }
    }

    /** Live count of leaves currently active in simulation. */
    val activeParticleCount: Int
        get() {
            var c = 0
            for (l in leaves) if (l.wait <= 0f) c++
            return c
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

    // Window panes: Upper fixed glass pane and Lower sliding sash glass pane
    private class GlassParticle(
        var x: Float, var y: Float, var r: Float, var v: Float,
        var age: Float = 0f,
        var isSnow: Boolean = false,
        var meltTime: Float = 0f
    )
    private val upperGlassCanvasObj: CanvasTex
    private val lowerGlassCanvasObj: CanvasTex
    private val upperGlassParticles = ArrayList<GlassParticle>()
    private val lowerGlassParticles = ArrayList<GlassParticle>()
    private var glassAccum = 0f
    private var glassKey = ""
    private val groundCanvasObj: CanvasTex
    private val windowGlowMat: Material
    /** The ground below the window (hidden 40 floors up). */
    val outdoorGround: Mesh

    /** The whole-sky flash; the sky reads it, the room lights spike with it. */
    var lightningFlash = 0f; private set

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

        // Window panes: Upper fixed glass pane and Lower sliding sash glass pane
        upperGlassCanvasObj = createCanvasTex(128, 64) { _, _, _ -> }
        lowerGlassCanvasObj = createCanvasTex(128, 64) { _, _, _ -> }

        val upperGlass = Mesh(Geo.plane(2.70f, 1.04f), PS1.basic(map = upperGlassCanvasObj.tex, transparent = true, depthWrite = false, fog = false))
        upperGlass.position.set(0.8f, 3.19f, -3.125f)
        scene.add(upperGlass)

        val lowerGlass = Mesh(Geo.plane(2.68f, 1.01f), PS1.basic(map = lowerGlassCanvasObj.tex, transparent = true, depthWrite = false, fog = false))
        lowerGlass.position.set(0.8f, 2.105f, -3.115f)
        room.windowSashGroup.add(lowerGlass)
        room.registerInteractable(lowerGlass, "window")

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

    private fun drawPane(canvasObj: CanvasTex, drops: ArrayList<GlassParticle>, isUpper: Boolean, dt: Float) {
        val raining = env.isRainingNow()
        val snowing = env.isSnowingNow()
        val winter = env.currentSeason == "winter"
        val ctx = canvasObj.ctx
        val w = 128; val h = 64
        ctx.clearRect(0, 0, w, h)

        // Base glass reflection
        ctx.fillStyle = "rgba(175, 195, 230, 0.04)"
        ctx.fillRect(0, 0, w, h)

        // Condensation
        if (env.currentWeather == "foggy" || raining || snowing) {
            val condCol = if (env.currentWeather == "foggy") "rgba(215, 226, 240, 0.22)" else if (snowing) "rgba(225, 236, 252, 0.12)" else "rgba(200, 215, 235, 0.09)"
            ctx.fillStyle = condCol; ctx.fillRect(0, 0, w, h)
        }

        // Heatwave sun shimmer
        if (env.W.base == "heat") {
            val heat = ctx.createRadialGradient(w / 2, h / 2, 12, w / 2, h / 2, 50)
            heat.addColorStop(0, "rgba(255, 200, 110, 0)")
            heat.addColorStop(1, "rgba(255, 190, 95, 0.20)")
            ctx.fillStyle = heat; ctx.fillRect(0, 0, w, h)
        }

        // Frost in winter or snow
        if (winter || snowing) {
            val frost = ctx.createRadialGradient(w / 2, h / 2, 16, w / 2, h / 2, 48)
            frost.addColorStop(0, "rgba(230, 240, 255, 0)")
            frost.addColorStop(1, if (env.weatherKind == "blizzard") "rgba(235, 245, 255, 0.70)" else "rgba(230, 240, 255, 0.50)")
            ctx.fillStyle = frost; ctx.fillRect(0, 0, w, h)
            ctx.fillStyle = "rgba(255, 255, 255, 0.40)"
            for (i in 0 until 80) {
                val a = (i * 2.399) % 6.283
                val r = 0.42 + ((i * 37) % 23) / 60.0
                ctx.fillRect((w / 2 + cos(a) * r * w * 0.55).toInt(), (h / 2 + sin(a) * r * h * 0.65).toInt(), 1, 1)
            }
        }

        // Snow accumulation along bottom sash frame
        if (snowing) {
            ctx.fillStyle = "rgba(244, 248, 252, 0.85)"
            for (x in 0 until w) {
                val corner = min(1.0, kotlin.math.abs(x - w / 2.0) / (w * 0.48))
                val sh = (1.5 + corner * 4.0 + sin(x * 0.28) * 1.2).toInt()
                ctx.fillRect(x, h - sh, 1, sh)
            }
        }

        // Wind direction & facing vector
        val windDeg = if (env.liveWindDirDeg >= 0f) env.liveWindDirDeg else 225f
        val windRad = Math.toRadians(windDeg.toDouble())
        val blowDirX = -sin(windRad).toFloat()
        val blowDirZ = cos(windRad).toFloat()
        val windSpeedNorm = (if (env.liveWindSpeedKmH >= 0f) env.liveWindSpeedKmH / 32.0f else env.W.wind).coerceIn(0.08f, 2.0f)
        val windSlant = blowDirX * (0.12f + windSpeedNorm * 0.35f)
        val windFacingMult = max(0.2f, 0.6f + blowDirZ * 0.75f)

        // Spawning:
        if (snowing) {
            val maxSnow = (20 + env.W.rain * 25).roundToInt()
            val spawnSnow = (1 + (env.W.rain * 2 * windFacingMult).roundToInt())
            for (i in 0 until spawnSnow) if (drops.count { it.isSnow } < maxSnow) {
                drops += GlassParticle(rand() * w, rand() * (h - 4), 1.2f + rand() * 1.4f, 0f, 0f, isSnow = true, meltTime = randIn(3.0f, 6.5f))
            }
        }
        if (raining) {
            val maxRain = (35 + env.W.rain * 45).roundToInt()
            val spawn = (1 + (env.W.rain * 4 * windFacingMult).roundToInt())
            for (i in 0 until spawn) if (drops.count { !it.isSnow } < maxRain) {
                drops += GlassParticle(rand() * w, rand() * (h - 4), 0.75f + rand() * 1.35f, 0f, 0f, isSnow = false)
            }
        }

        // Updating particles:
        var i = drops.size - 1
        while (i >= 0) {
            val d = drops[i]
            d.age += dt

            if (d.isSnow) {
                d.meltTime -= dt
                if (d.meltTime <= 0f) {
                    d.isSnow = false
                    d.v = 8f + rand() * 14f + env.W.rain * 12f
                }
                // Draw snowflake
                ctx.fillStyle = "rgba(248, 252, 255, 0.92)"
                ctx.beginPath(); ctx.arc(d.x, d.y, d.r, 0, PI * 2); ctx.fill()
                ctx.fillStyle = "rgba(230, 240, 255, 0.45)"
                ctx.beginPath(); ctx.arc(d.x, d.y, d.r * 1.5f, 0, PI * 2); ctx.fill()
            } else {
                if (raining && d.v == 0f) {
                    d.r += dt * (0.24f + env.W.rain * 0.38f) * (0.5f + rand())
                    if (d.r > 1.5f || (d.age > 3.0f && rand() < 0.28f)) {
                        d.v = 11f + rand() * 20f + env.W.rain * 14f
                    }
                } else if (!raining && d.v > 0f) {
                    d.v *= max(0f, 1f - dt * 0.8f)
                }

                if (d.v > 0f) {
                    val streakLen = min(8f, 3.0f + d.v * 0.20f)
                    ctx.strokeStyle = "rgba(220, 235, 255, 0.22)"
                    ctx.beginPath(); ctx.moveTo(d.x, d.y); ctx.lineTo(d.x - windSlant * streakLen, d.y - streakLen); ctx.stroke()
                    d.y += d.v * dt
                    d.x += (windSlant * d.v + (rand() - 0.5f) * 1.8f) * dt
                }
                if (!raining && rand() < 0.08f) d.r -= 0.25f

                if (d.y > h + 3 || d.x < -3 || d.x > w + 3 || d.r <= 0.2f) {
                    drops.removeAt(i); i--; continue
                }

                // Draw raindrop
                ctx.fillStyle = "rgba(15, 23, 42, 0.26)"
                ctx.beginPath(); ctx.arc(d.x, d.y + 0.4f, d.r, 0, PI * 2); ctx.fill()
                ctx.fillStyle = "rgba(225, 238, 255, 0.62)"
                ctx.beginPath(); ctx.arc(d.x, d.y, d.r * 0.82f, 0, PI * 2); ctx.fill()
                ctx.fillStyle = "rgba(255, 255, 255, 0.92)"
                ctx.fillRect((d.x - d.r * 0.3f).toInt(), (d.y - d.r * 0.4f).toInt(), 1, 1)
            }
            i--
        }
        canvasObj.tex.needsUpdate()
    }

    private fun drawWindowGlass(dt: Float) {
        val raining = env.isRainingNow()
        val snowing = env.isSnowingNow()
        val winter = env.currentSeason == "winter"
        val key = "${env.currentWeather}|${env.weatherKind}|$raining|$snowing|$winter|${env.currentSeason}|${env.liveWindDirDeg}|${room.windowOpen}"
        if (upperGlassParticles.isEmpty() && lowerGlassParticles.isEmpty() && !raining && !snowing && key == glassKey) return
        glassKey = key

        drawPane(upperGlassCanvasObj, upperGlassParticles, isUpper = true, dt = dt)
        drawPane(lowerGlassCanvasObj, lowerGlassParticles, isUpper = false, dt = dt)
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
        val desertPlace = sky.outsideEnv in SMALL_LEAF_PLACES
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
    private val SMALL_LEAF_PLACES = setOf("desert", "canyon", "pyramid", "redrock", "oasis", "beach", "arizona")
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
        val windDeg = if (env.liveWindDirDeg >= 0f) env.liveWindDirDeg else 225f
        val windRad = Math.toRadians(windDeg.toDouble())
        val blowDirX = -sin(windRad).toFloat() // East (90°) blows left (-X), West (270°) blows right (+X)
        val blowDirZ = cos(windRad).toFloat()  // North (0°) blows into window (+Z), South (180°) blows away (-Z)
        val windSpeedNorm = (if (env.liveWindSpeedKmH >= 0f) env.liveWindSpeedKmH / 32.0f else W.wind).coerceIn(0.08f, 2.0f)

        if (rainMesh.visible) {
            val active = (RAIN_COUNT * (0.15f + 0.85f * W.rain)).roundToInt()
            val speedX = blowDirX * (0.35f + windSpeedNorm * 2.8f)
            val speedZ = blowDirZ * (0.25f + windSpeedNorm * 1.8f)
            val slantZ = -blowDirX * (0.08f + windSpeedNorm * 0.30f)
            val slantX = blowDirZ * (0.06f + windSpeedNorm * 0.22f)
            for (i in 0 until RAIN_COUNT) {
                val d = rainDrops[i]
                if (i >= active) { rainMesh.setInstance(i, 0f, -50f, 0f, 0f, 0f, 0f, 0f); continue }
                d.y -= d.v * (0.9f + W.rain * 0.45f) * dt
                d.x += speedX * dt
                d.z += speedZ * dt
                val overLedge = d.x > -0.6f && d.x < 2.2f && d.z > -3.42f
                val floorY = if (overLedge) OUT.ledgeY else OUT.y0
                if (d.y < floorY || d.z > -2.95f || d.z < OUT.z1 - 1f || d.x < OUT.x0 - 1.5f || d.x > OUT.x1 + 1.5f) {
                    if (d.y < floorY && rand() < 0.6f) {
                        val s = splashes[splashNext]
                        splashNext = (splashNext + 1) % SPLASH_COUNT
                        s.life = 0.25f; s.x = d.x; s.y = floorY; s.z = d.z
                    }
                    d.y = OUT.y1 + rand() * 0.5f
                    d.x = randIn(OUT.x0, OUT.x1 + 1.2f)
                    d.z = randIn(OUT.z0, OUT.z1)
                }
                rainMesh.setInstance(i, d.x, d.y, d.z, slantX, 0f, slantZ, 1f)
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
            val activeSnow = (SNOW_COUNT * (0.2f + 0.8f * W.rain)).roundToInt()
            val speedX = blowDirX * (0.22f + windSpeedNorm * 1.8f)
            val speedZ = blowDirZ * (0.18f + windSpeedNorm * 1.4f)
            for (i in 0 until SNOW_COUNT) {
                val f = snowFlakes[i]
                if (i >= activeSnow) { snowMesh.setInstance(i, 0f, -50f, 0f, 0f, 0f, 0f, 0f); continue }
                f.y -= f.v * (0.75f + windSpeedNorm * 0.6f) * dt
                f.x += (speedX + sin(t * 1.6f + f.ph) * 0.35f) * dt
                f.z += (speedZ + cos(t * 1.3f + f.ph) * 0.25f) * dt
                if (f.y < OUT.y0 || f.z > -2.95f || f.z < OUT.z1 - 1f || f.x < OUT.x0 - 1f || f.x > OUT.x1 + 1.5f) {
                    f.y = OUT.y1 + rand() * 0.4f
                    f.x = randIn(OUT.x0, OUT.x1 + 1.5f)
                    f.z = randIn(OUT.z0, OUT.z1)
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
            val freqBoost = max(0.2f, debrisFreq * (0.7f + W.wind * 0.9f))
            val scale = when {
                sky.outsideEnv in SMALL_LEAF_PLACES -> 0.48f
                env.currentSeason == "spring" -> 0.68f
                env.currentSeason == "summer" -> 0.58f
                env.currentSeason == "winter" -> 0.55f
                else -> 1.0f
            }
            for (i in 0 until LEAF_COUNT) {
                val l = leaves[i]
                if (i >= activeCount && l.state != 3) {
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
                        val driftX = blowDirX * (0.35f + windSpeedNorm * 1.6f) * debrisSpeed
                        val driftZ = blowDirZ * (0.25f + windSpeedNorm * 1.4f) * debrisSpeed
                        val sway = sin(t * (1.6f + l.rs * 0.3f) + l.ph) * 0.28f
                        l.y -= fallSpeed * dt
                        l.x += (driftX + sway) * dt
                        l.z += (driftZ + cos(t * 1.2f + l.ph) * 0.04f) * dt
                        // The back wall's outer face is at Z -3.22: a leaf blown toward the house presses against it
                        // instead of drifting through the wall into the bedroom (only the open sash below lets one in)
                        if (l.z > OUT.wallZ) l.z = OUT.wallZ

                        // Smooth organic aerial tumble aligned with wind direction
                        l.rotX = -blowDirZ * 0.30f + sin(t * 1.8f + l.ph) * 0.35f + t * l.rs * 0.5f * debrisSpeed
                        l.rotY = t * l.rs * 0.5f * debrisSpeed
                        l.rotZ = blowDirX * 0.35f + sin(t * 2.0f + l.ph) * 0.45f

                        // Collision with window glass or aperture:
                        val inWindowX = l.x in -0.55f..2.15f
                        val hitGlassZ = l.z >= OUT.wallZ - 0.02f
                        if (inWindowX && hitGlassZ && (windowHitRate > 0.01f)) {
                            val windowRaised = room.windowOpen && room.windowRaise > 0.30f
                            val inOpeningY = l.y in 1.58f..(1.58f + room.windowRaise)
                            if (windowRaised && inOpeningY && l.x in -0.45f..2.05f && (blowDirZ >= -0.15f && rand() < 0.28f)) {
                                // BLOW IN THE WINDOW! Occasional leaf drifts into bedroom through open sash
                                l.state = 3
                                l.vxOff = blowDirX * 0.40f + randIn(-0.12f, 0.12f)
                                l.vyOff = -0.30f
                                l.vzOff = 0.60f + max(0f, blowDirZ) * 0.55f
                                l.contactTimer = 0f
                            } else if (l.y in 1.58f..3.75f) {
                                // Hit window glass or sill
                                l.state = 1
                                l.contactTimer = randIn(3.0f, 7.5f) / max(0.5f, debrisFreq)
                                l.rollSpeed = randIn(0.18f, 0.40f) * (0.8f + windSpeedNorm * 0.6f) * debrisSpeed
                                l.z = if (l.y > 1.62f) -3.14f else -3.18f
                            } else if (l.y < OUT.y0 + 0.1f || l.x < OUT.x0 - 0.8f || l.x > OUT.x1 + 1.8f) {
                                l.wait = randIn(0.1f, 1.5f) / freqBoost
                            }
                        } else if (l.y < OUT.y0 + 0.1f || l.x < OUT.x0 - 0.8f || l.x > OUT.x1 + 1.8f) {
                            l.wait = randIn(0.1f, 1.5f) / freqBoost
                        }
                    }
                    1 -> {
                        // HIT THE WINDOW: rolling & sliding across the glass pane or sill
                        l.contactTimer -= dt
                        if (l.y > 1.61f) {
                            // Sliding down and rolling sideways along the window glass pane
                            l.x += blowDirX * (l.rollSpeed + windSpeedNorm * 0.28f) * dt
                            l.y -= (0.10f + if (env.isRainingNow()) 0.30f else 0.05f) * dt
                            l.z = -3.14f
                            l.rollAngle += l.rollSpeed * 5.0f * dt
                            l.rotX = sin(t * 6f + l.ph) * 0.10f
                            l.rotY = cos(t * 3f + l.ph) * 0.08f
                            l.rotZ = l.rollAngle
                        } else {
                            // Reached the exterior window sill: rolls along the ledge
                            l.y = OUT.ledgeY // 1.60f
                            l.z = -3.18f
                            l.x += blowDirX * (l.rollSpeed * 0.8f + windSpeedNorm * 0.32f) * dt
                            l.rollAngle += l.rollSpeed * 4.0f * dt
                            l.rotX = (-PI / 2).toFloat() + sin(t * 7f + l.ph) * (0.04f + W.wind * 0.12f)
                            l.rotY = sin(t * 4f + l.ph) * 0.08f
                            l.rotZ = l.rollAngle
                        }

                        // Wind catches it and it takes flight off the window!
                        if (l.contactTimer <= 0f || l.x < -0.55f || l.x > 2.15f || (W.wind >= 0.75f && rand() < 0.02f)) {
                            l.state = 2 // FLY OFF!
                            l.vxOff = blowDirX * (0.75f + windSpeedNorm * 1.55f) * debrisSpeed
                            l.vyOff = (0.35f + windSpeedNorm * 0.60f) * debrisSpeed
                            l.vzOff = -(0.30f + rand() * 0.45f)
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

                        if (l.x < OUT.x0 - 0.8f || l.x > OUT.x1 + 1.8f || l.y < OUT.y0 || l.z < OUT.z1) {
                            l.wait = randIn(0.1f, 1.2f) / freqBoost
                        }
                    }
                    3 -> {
                        // IN_ROOM: drifted inside the bedroom through the open window. contactTimer > 0 = landed.
                        if (l.contactTimer > 0f) {
                            // Resting flat where it landed; fades (shrinks) away after a while unless leaves are set to stay
                            if (!indoorLeavesStay) {
                                l.contactTimer += dt
                                if (l.contactTimer > INDOOR_LEAF_LIFE) { l.wait = randIn(1.0f, 5.0f); l.state = 0; l.contactTimer = 0f }
                            }
                            val fade = if (indoorLeavesStay) 1f else ((INDOOR_LEAF_LIFE - l.contactTimer) / 2.5f).coerceIn(0f, 1f)
                            leafMesh.setInstance(i, l.x, l.y, l.z, l.rotX, l.rotY, l.rotZ, scale * fade)
                            continue
                        }
                        l.x += (l.vxOff + sin(t * 2.1f + l.ph) * 0.12f) * dt
                        l.y += l.vyOff * dt
                        l.z += l.vzOff * dt
                        l.vzOff = max(0.08f, l.vzOff - 0.18f * dt) // air drag: it slows as it crosses the room
                        l.rotX += 2.2f * dt
                        l.rotY += 3.0f * dt
                        l.rotZ += 1.8f * dt
                        // Until it's through the wall it stays inside the opening; after that, inside the room
                        if (l.z < -2.9f) {
                            l.x = l.x.coerceIn(-0.5f, 2.1f); l.y = l.y.coerceIn(1.6f, 1.55f + room.windowRaise)
                        } else {
                            if (l.x < Room.MIN_X + 0.05f || l.x > Room.MAX_X - 0.05f) { l.x = l.x.coerceIn(Room.MIN_X + 0.05f, Room.MAX_X - 0.05f); l.vxOff = -l.vxOff * 0.3f }
                            if (l.z > Room.MAX_Z - 0.05f) { l.z = Room.MAX_Z - 0.05f; l.vzOff = 0f }
                        }

                        // Lands on the desk top or the floor and stays put
                        val onDesk = l.x in 1.7f..3.1f && l.z in -2.6f..-1.4f && l.y <= 1.38f && l.y > 1.2f
                        val onFloor = l.y <= 0.045f
                        if (onDesk || onFloor) {
                            l.y = if (onDesk) 1.385f else 0.045f
                            l.vxOff = 0f; l.vyOff = 0f; l.vzOff = 0f
                            l.rotX = (-PI / 2).toFloat(); l.rotZ = 0f
                            l.contactTimer = 0.001f
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
