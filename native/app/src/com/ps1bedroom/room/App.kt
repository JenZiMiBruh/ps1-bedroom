package com.ps1bedroom.room

import android.opengl.GLSurfaceView
import android.os.SystemClock
import com.ps1bedroom.room.engine.OrbitControls
import com.ps1bedroom.room.engine.PerspectiveCamera
import com.ps1bedroom.room.engine.Renderer
import com.ps1bedroom.room.engine.Scene
import com.ps1bedroom.room.engine.Vec3
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The app: scene, camera, controls and the per-frame loop (the HTML's animate()), running on the GL thread.
 * Input arrives from MainActivity through the thread-safe methods at the bottom.
 */
class App : GLSurfaceView.Renderer {
    companion object {
        // Clickable parts that never move can still merge, as long as they merge with parts of the same tag
        val STATIC_TAGS = setOf("bed", "pc", "monitor", "tv", "tvPower", "ac", "lavalamp", "phone", "fan", "fanspin", "cat", "ps1Body", "ps1Pad", "dresser", "lamp", "boombox", "switch")
    }
    val scene = Scene()
    // Default orbit view from inside the room, by the front wall, looking toward the desk and window
    val defaultCamPos = Vec3(0.6f, 3.15f, 3.1f)
    val defaultTarget = Vec3(0.1f, 2.0f, -1.2f)
    val camera = PerspectiveCamera(45f, 1f, 0.04f, 60f)
    val controls = OrbitControls(camera)
    val renderer = Renderer()
    private lateinit var room: Room
    lateinit var env: Environment; private set
    lateinit var sky: Sky; private set
    lateinit var exterior: Exterior; private set
    lateinit var desk: Desk; private set
    lateinit var bedroom: Bedroom; private set
    lateinit var fan: Fan; private set
    lateinit var furniture: Furniture; private set
    lateinit var decor: Decor; private set
    /** Set by MainActivity before the GL thread starts (for raw model files). */
    lateinit var res: android.content.res.Resources
    private var lastCalendarCheck = 0f
    private var lastClockDraw = -99f
    private var lastSkylineUpdate = -99f
    private val SPHERE_WINDOW_C = Vec3(0.8f, 2.65f, -3.4f)

    // settings (the HTML defaults: Balanced quality, 240p, wobble 160, camera breathing 40%)
    var resShort = 240
    var jitterBase = 160f
    var swayAmount = 0.4f
    private val basePos = Vec3()
    private var lastT = -1f
    private var startMs = 0L
    private var built = false

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        renderer.init()
        if (!built) {
            val tex = Textures()
            room = Room(scene, tex)
            env = Environment(scene, room, renderer)
            // Each part is built at its place in the HTML's creation order (that keeps the grime maps 1:1)
            room.onBuildOutside = {
                // The outside: painted backdrop, sky dome, sun, moon, clouds, horizon ring, then the 3D city.
                // All of it moves or redraws, so it never takes part in static batching.
                val exteriorWorldGroup = com.ps1bedroom.room.engine.Group(); scene.add(exteriorWorldGroup)
                sky = Sky(scene, env)
                sky.defaultCamPos = defaultCamPos
                sky.addTo(exteriorWorldGroup)
                com.ps1bedroom.room.engine.PS1.syncGrime(37)
                exterior = Exterior(exteriorWorldGroup, env, room)
                room.extraDynamicRoots += exteriorWorldGroup; room.extraDynamicRoots += sky.skyBoxGroup
            }
            room.onBuildInterior = {
                desk = Desk(room, tex); desk.build()
                bedroom = Bedroom(room); bedroom.build()
                room.extraDynamicRoots += bedroom.chairGroup
                fan = Fan(room); fan.build()
                room.extraDynamicRoots += fan.fanBladesGroup; room.extraDynamicRoots += fan.fanPullChain; room.extraDynamicRoots += fan.lightPullChain
                furniture = Furniture(room); furniture.build()
                decor = Decor(room, res); decor.build()
                room.extraBatchExcluded.addAll(decor.stringBulbs)
                decor.buildKenney()   // the HTML's GLB models finish loading after everything else
                room.extraBatchExcluded.addAll(desk.lavaBlobs)
            }
            room.build()
            // Merge everything that never moves into a few big meshes (the HTML's batchStaticMeshes)
            val r = com.ps1bedroom.room.engine.Batching.batchStaticMeshes(scene, room.dynamicRoots(), emptySet(), room.batchExcluded(), STATIC_TAGS, room.interactables)
            room.onShadowsChanged = { renderer.shadowsNeedUpdate = true }
            room.onBlindsChanged = { env.applyEnvironmentSettings() }
            env.sky = sky; env.eye = defaultCamPos
            env.applyEnvironmentSettings()
            android.util.Log.i("PS1Bedroom", "static batching: ${r.removed} meshes -> ${r.added} batches")
            renderer.post.rayLight = room.moonLight
            camera.position.copy(defaultCamPos)
            controls.target.copy(defaultTarget)
            controls.enableDamping = true
            controls.dampingFactor = 0.08f
            controls.maxPolarAngle = (Math.PI * 0.52).toFloat()
            controls.minDistance = 0.01f
            controls.maxDistance = 25.0f
            controls.update()
            startMs = SystemClock.uptimeMillis()
            built = true
        }
    }

    private var surfaceW = 1; private var surfaceH = 1
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceW = width; surfaceH = height
        renderer.resize(width, height, effectiveRes(), jitterBase)
        camera.aspect = width.toFloat() / max(1, height)
        camera.updateProjectionMatrix()
    }

    override fun onDrawFrame(gl: GL10?) {
        val t = (SystemClock.uptimeMillis() - startMs) / 1000f
        val dt = minOf(0.05f, if (lastT < 0f) 0f else t - lastT)
        lastT = t
        applyPerfLevel()
        applyInput()
        handleTaps()
        env.tick(System.currentTimeMillis())
        if (debugPresetStep) { debugPresetStep = false; debugPreset = (debugPreset + 1) % env.TIME_PRESETS.size; env.setTimePreset(env.TIME_PRESETS[debugPreset].first) }
        exterior.update(t, dt, sky.outsideEnv)
        desk.update(t)
        fan.update(dt)
        decor.update(t)
        room.updateAc(t, dt)
        if (t - lastCalendarCheck > 60f) { lastCalendarCheck = t; furniture.refreshCalendar() }
        if (t - lastClockDraw > 1f) { lastClockDraw = t; bedroom.drawAlarmClock(env.currentHour()) }
        room.updateBlinds(dt)
        // The backdrop redraws ~3x a second while the window is on screen, every 2.5 s otherwise (as in the HTML)
        if (t - lastSkylineUpdate > (if (renderer.sphereInView(SPHERE_WINDOW_C, 2.0f)) 0.33f else 2.5f)) {
            sky.updateCitySkyline(t)
            lastSkylineUpdate = t
        }
        controls.update()
        // Keep the free camera inside the room
        camera.position.x = max(Room.MIN_X + 0.2f, min(Room.MAX_X - 0.2f, camera.position.x))
        camera.position.y = max(Room.MIN_Y + 0.25f, min(Room.MAX_Y - 0.2f, camera.position.y))
        camera.position.z = max(Room.MIN_Z + 0.2f, min(Room.MAX_Z - 0.2f, camera.position.z))
        basePos.copy(camera.position)
        // Camera breathing (the HTML's orbit sway)
        if (swayAmount > 0.001f) {
            camera.position.x += sin(t * 0.55f) * 0.035f * swayAmount
            camera.position.y += cos(t * 0.42f) * 0.025f * swayAmount
        }
        renderer.post.time = t
        renderer.render(scene, camera)
        camera.position.copy(basePos)
    }

    // ---- Automatic quality (the HTML's setPerfLevel), driven here by Android's real thermal status ----
    // level 0: as set · 1: fewer light-ray steps · 2: 80% resolution, no ambient occlusion · 3: 65% resolution
    @Volatile private var wantedPerfLevel = 0
    private var perfLevel = 0
    var aoBase = 0.8f
    var marchSteps = 14
    private fun effectiveRes() = maxOf(120, (resShort * floatArrayOf(1f, 1f, 0.8f, 0.65f)[perfLevel]).toInt())
    fun onThermal(status: Int) {
        wantedPerfLevel = when {
            status >= android.os.PowerManager.THERMAL_STATUS_SEVERE -> 3
            status >= android.os.PowerManager.THERMAL_STATUS_MODERATE -> 2
            status >= android.os.PowerManager.THERMAL_STATUS_LIGHT -> 1
            else -> 0
        }
    }
    private fun applyPerfLevel() {
        val n = wantedPerfLevel
        if (n == perfLevel && built) return
        perfLevel = n
        renderer.post.marchSteps = if (n >= 1) minOf(marchSteps, 8) else marchSteps
        renderer.post.aoStrength = if (n >= 2) 0f else aoBase
        renderer.resize(surfaceW, surfaceH, effectiveRes(), jitterBase)
    }

    // ---- Taps (the HTML's pointerup -> raycast interactables -> triggerEasterEgg) ----
    private val raycaster = com.ps1bedroom.room.engine.Raycaster()
    private val taps = ArrayList<FloatArray>()
    var fanLightOn = false; private set
    fun onTap(x: Float, y: Float, w: Float, h: Float) = synchronized(lock) { taps += floatArrayOf(x / w * 2 - 1, -(y / h) * 2 + 1) }
    private fun handleTaps() {
        val list = synchronized(lock) { if (taps.isEmpty()) return; ArrayList(taps).also { taps.clear() } }
        for (p in list) {
            raycaster.setFromCamera(p[0], p[1], camera)
            val hit = raycaster.intersect(room.interactables) ?: continue
            triggerEasterEgg(hit.first.userData["interactTag"] as String? ?: continue)
        }
    }
    private fun triggerEasterEgg(tag: String) {
        when (tag) {
            "blinds" -> room.cycleWindowBlinds()
            "switch" -> toggleFanLight()
            "lavalamp" -> desk.cycleLava()
            "cat" -> bedroom.toggleCatEyes()
            "fan" -> toggleFanLight()
            "fanspin" -> fan.toggleCeilingFanSpin()
            "ac" -> room.toggleAcUnit()
            "lamp" -> furniture.toggleLamp()
            "string" -> decor.cycleStringLights()
        }
    }
    fun toggleFanLight() {
        fanLightOn = !fanLightOn
        room.fanLight.intensity = if (fanLightOn) 1.85f else 0.0f
        room.fanHemiFill.intensity = if (fanLightOn) 0.52f else 0.0f
        renderer.post.fanLightOn = if (fanLightOn) 1f else 0f
        fan.drawFanShadeTexture(fanLightOn)
        fan.lightChainOffset = -0.045f
        renderer.shadowsNeedUpdate = true
    }

    // Temporary test shortcut until the menus are ported: a three-finger tap steps through the time presets
    @Volatile private var debugPresetStep = false
    private var debugPreset = -1
    fun debugNextTimePreset() { debugPresetStep = true }

    // ---- input from the UI thread ----
    private val lock = Any()
    private var dragX = 0f; private var dragY = 0f; private var pinch = 1f; private var panX = 0f; private var panY = 0f; private var wheel = 0f
    @Volatile var viewHeight = 1f
    fun onDrag(dx: Float, dy: Float) = synchronized(lock) { dragX += dx; dragY += dy }
    fun onPinch(ratio: Float) = synchronized(lock) { pinch *= ratio }
    fun onPan(dx: Float, dy: Float) = synchronized(lock) { panX += dx; panY += dy }
    fun onWheel(d: Float) = synchronized(lock) { wheel += d }
    private fun applyInput() = synchronized(lock) {
        if (dragX != 0f || dragY != 0f) controls.rotateBy(dragX, dragY, viewHeight)
        if (pinch != 1f) controls.dollyBy(pinch)
        if (panX != 0f || panY != 0f) controls.panBy(panX, panY, viewHeight)
        if (wheel != 0f) controls.wheel(wheel)
        dragX = 0f; dragY = 0f; pinch = 1f; panX = 0f; panY = 0f; wheel = 0f
    }
}
