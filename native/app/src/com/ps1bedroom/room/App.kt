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
    lateinit var room: Room; private set
    lateinit var env: Environment; private set
    lateinit var sky: Sky; private set
    lateinit var exterior: Exterior; private set
    lateinit var desk: Desk; private set
    lateinit var bedroom: Bedroom; private set
    lateinit var fan: Fan; private set
    lateinit var furniture: Furniture; private set
    lateinit var decor: Decor; private set
    lateinit var jasper: Jasper; private set
    lateinit var extras: Extras; private set
    lateinit var weatherFx: WeatherFx; private set
    lateinit var places: Places; private set
    lateinit var monitor: Monitor; private set
    /** My Music (made by the activity: it needs a Context). */
    lateinit var myMusic: MyMusic
    /** Song changes for the media notification (title, artist, cover). */
    @Volatile var onNowPlaying: ((String?, String?, android.graphics.Bitmap?) -> Unit)? = null
    /** Opens the system file picker for songs (false) or a whole album folder (true). */
    @Volatile var pickMusic: ((Boolean) -> Unit)? = null
    /** Opens the photo picker for a poster ("band" above the boombox, "main" over the desk). */
    @Volatile var pickPosterPhoto: ((String) -> Unit)? = null
    /** Loads your saved poster photo (set by the activity). */
    @Volatile var posterPhotoFor: (String) -> android.graphics.Bitmap? = { null }
    /** YouTube is playing on the bedroom TV (YouTubeTv); the TV's own channel sound steps aside. */
    @Volatile var ytActive = false
    /** Keeps the screen on while the room is in use; the sleep timer hands it back to Android (set by the activity). */
    @Volatile var keepScreenOn: ((Boolean) -> Unit)? = null
    /** Notch / hole-punch handling (made by the activity). */
    var cutout: Cutout? = null
    /** 1.3: achievements and the sunrise alarm (made by the activity). */
    lateinit var achievements: Achievements
    lateinit var alarm: Alarm
    lateinit var surprises: Surprises; private set
    private val steadyTextures by lazy { SteadyTextures(scene) }
    private val YT_CORNERS = arrayOf(Vec3(-0.37f, 0.28f, 0.005f), Vec3(0.37f, 0.28f, 0.005f), Vec3(-0.37f, -0.28f, 0.005f), Vec3(0.37f, -0.28f, 0.005f))
    private val ytPts = FloatArray(8); private val ytTmp = Vec3(); private val ytView = Vec3(); private val ytC = Vec3(); private val ytN = Vec3()
    /** ytPlaceOverlay: the TV glass's 4 corners on screen, snapped like the PS1 vertex shader snaps the TV. */
    private fun placeYouTube() {
        val yt = hud?.yt ?: return
        if (!tv.bedroomTvOn) {
            ytActive = false
            tv.ytShowing = false
            tv.useFeed(null)
            hud?.runUi { yt.stop(true) }
            return
        }
        if (yt.isFull) return
        val modalOpen = hud?.let { it.menu.isOpen || it.pcDesktop.isOpen || it.isBoomboxOpen || it.isSleeping } == true
        val m = tv.screenMesh.matrixWorld
        ytC.set(0f, 0f, 0.02f).applyMatrix4(m)
        ytN.set(0f, 0f, 1f).applyMatrix4Dir(m).normalize()
        ytTmp.subVectors(camera.position, ytC)
        var show = !modalOpen && ytN.dot(ytTmp) > 0.05f
        val jg = renderer.ps1Uniforms.jitterGrid
        if (show) for (i in 0 until 4) {
            ytTmp.copy(YT_CORNERS[i]).applyMatrix4(m)
            ytView.copy(ytTmp).applyMatrix4(camera.matrixWorldInverse)
            if (ytView.z > -0.05f) { show = false; break }
            ytTmp.applyMatrix4(camera.projectionMatrix.let { p -> ytProj.multiplyMatrices(p, camera.matrixWorldInverse) })
            if (ps1Jitter) { val k = maxOf(1f, -ytView.z * 0.5f); ytTmp.x = Math.floor((jg[0] * k * ytTmp.x + 0.5f).toDouble()).toFloat() / (jg[0] * k); ytTmp.y = Math.floor((jg[1] * k * ytTmp.y + 0.5f).toDouble()).toFloat() / (jg[1] * k) }
            ytPts[i * 2] = (ytTmp.x + 1) / 2 * surfaceW; ytPts[i * 2 + 1] = (1 - ytTmp.y) / 2 * surfaceH
        }
        val wPx = maxOf(8f, Math.hypot((ytPts[2] - ytPts[0]).toDouble(), (ytPts[3] - ytPts[1]).toDouble()).toFloat() / surfaceW * renderer.internalW)
        yt.place(ytPts, show, 640f / wPx)
    }
    private val ytProj = com.ps1bedroom.room.engine.Mat4()

    // ---- The Window widget: a snapshot through the bedroom window, rendered off-screen every so often ----
    /** Set by the activity: there are Window widgets on the home screen (no snapshots otherwise). */
    @Volatile var windowWidgets = false
    /** A light vibration tick (set by the activity). */
    @Volatile var onHaptic: (() -> Unit)? = null
    /** Picture-in-picture when you leave the app (Menu → Display). */
    @Volatile var pipEnabled = true
    /** Hands the two snapshots (blinds hidden / as in the room) plus the weather line to the widget. */
    @Volatile var onWindowShot: ((android.graphics.Bitmap, android.graphics.Bitmap, String, String) -> Unit)? = null
    @Volatile var windowShotNow = false
    @Volatile var windowShotIntervalSec = 300f
    private var lastShotT = -1000f
    private var lastShotKey = ""
    private fun windowShotDue(t: Float): Boolean {
        if (!windowWidgets || onWindowShot == null || !built || t < 3.0f || surfaceW <= 10 || surfaceH <= 10) return false
        // every N minutes (configured in WindowWidgetConfig), and straight away when the place, the weather or the time of day changes
        val key = sky.outsideEnv + "|" + env.weatherKind + "|" + env.currentTime + "|" + room.blindsOpenState
        if (windowShotNow || key != lastShotKey || t - lastShotT > windowShotIntervalSec) { windowShotNow = false; lastShotKey = key; lastShotT = t; return true }
        return false
    }
    private val shotPos = Vec3()
    /** GL thread, right before the app goes to the background: one fresh snapshot so the Window widget shows the room as you left it. */
    fun windowShotOnLeave() { if (built && windowWidgets && onWindowShot != null && surfaceW > 10 && surfaceH > 10) { lastShotT = lastT; takeWindowShot() } }
    private fun takeWindowShot() {
        if (surfaceW <= 10 || surfaceH <= 10) return
        try {
            val px = camera.position.x; val py = camera.position.y; val pz = camera.position.z
            val rx = camera.rotation.x; val ry = camera.rotation.y; val rz = camera.rotation.z
            val fov = camera.fov
            // From just inside the glass, looking out; the field of view is set so a 3:2 crop of the screen spans the window
            val aspect = surfaceW.toFloat() / maxOf(1, surfaceH)
            val half = if (aspect < 1.5f) 0.62f / aspect else 0.62f / 1.5f
            camera.fov = (Math.toDegrees(2 * Math.atan(half.toDouble()))).toFloat()
            camera.updateProjectionMatrix()
            camera.position.set(0.8f, 2.62f, -1.75f); camera.lookAtTarget(shotPos.set(0.8f, 2.72f, -8f))   // the camera version (looks down -Z, uses the new position)
            val withBlinds = shot3x2() ?: return
            for (s in room.blindSlats) s.visible = false
            val clear = shot3x2() ?: return
            for (s in room.blindSlats) s.visible = true
            camera.position.set(px, py, pz); camera.rotation.set(rx, ry, rz); camera.fov = fov; camera.updateProjectionMatrix()
            val st = settings.status()
            val p = places.envDef()
            onWindowShot?.invoke(clear, withBlinds, "${st.time.substringBefore(' ')} ${st.temp} ${st.weather}", "${p.icon} ${p.name}")
        } catch (e: Throwable) {
            android.util.Log.w("PS1Bedroom", "takeWindowShot failed", e)
        }
    }
    /** Renders the current camera and keeps the middle 3:2 at 480 x 320. */
    private fun shot3x2(): android.graphics.Bitmap? {
        return try {
            renderer.render(scene, camera)
            val full = readScreen() ?: return null
            val w = full.width; val h = full.height
            if (w <= 10 || h <= 10) { full.recycle(); return null }
            val cw = maxOf(1, if (w.toFloat() / h > 1.5f) (h * 1.5f).toInt() else w).coerceAtMost(w)
            val ch = maxOf(1, if (w.toFloat() / h > 1.5f) h else (w / 1.5f).toInt()).coerceAtMost(h)
            val cropX = ((w - cw) / 2).coerceIn(0, maxOf(0, w - cw))
            val cropY = ((h - ch) / 2).coerceIn(0, maxOf(0, h - ch))
            val crop = android.graphics.Bitmap.createBitmap(full, cropX, cropY, cw, ch)
            val out = android.graphics.Bitmap.createScaledBitmap(crop, 480, 320, true)
            if (crop !== full) crop.recycle(); full.recycle()
            out
        } catch (e: Throwable) {
            android.util.Log.w("PS1Bedroom", "shot3x2 failed", e)
            null
        }
    }
    /** The YouTube PS1 look (also snaps the overlay to the wobble). */
    val ps1Jitter get() = hud?.yt?.ps1Look ?: true
    val phone = Phone(this)
    /** The sound engine (HTML Tone.js soundscape). */
    val sound = com.ps1bedroom.room.audio.Sound(this)
    fun sfx(name: String) = sound.sfx(name)
    /** toggleAmbienceAudio: the first time builds the engine. */
    /** The activity starts / stops the media service with this. */
    @Volatile var onAudioState: ((Boolean) -> Unit)? = null
    /** False while the activity is in the background: the GL thread is paused, so posted work would never run. */
    @Volatile var glActive = true
    /** Runs on the GL thread when it's running, otherwise right here (nothing else touches the room then). */
    fun postOrRun(fn: () -> Unit) { if (glActive) post(fn) else synchronized(posted) { fn() } }
    /** The phone's media controls (notification, lock screen, headphones, Bluetooth, watch). Pause silences the room like any player. */
    fun mediaCommand(cmd: String) {
        if (!isBuilt) return
        when (cmd) {
            "play" -> { if (!sound.playing) toggleAudio(); if (extras.radioStation == myMusic.MY_MUSIC_STATION && myMusic.paused) myMusic.mediaControl("play") }
            "pause", "stop" -> if (sound.playing) toggleAudio()
            "toggle" -> mediaCommand(if (sound.playing) "pause" else "play")
            "next", "prev" -> { if (!sound.playing) toggleAudio(); myMusic.mediaControl(cmd) }
        }
        publishMedia()
    }
    /** Song or room changes for the media session (MediaService). */
    @Volatile var onMediaState: ((MediaService.State) -> Unit)? = null
    fun publishMedia() {
        if (!isBuilt) return
        val mm = myMusic
        val st = extras.radioStation
        val t = mm.current
        val music = st == mm.MY_MUSIC_STATION && t != null
        val stream = sound.musicStream
        val place = Places.OUTSIDE_ENVS[sky.outsideEnv]
        onMediaState?.invoke(MediaService.State(sound.playing && (!music || !mm.paused),
            if (music) t!!.title else if (st > 0) extras.RADIO_STATIONS[st].substringBefore(':') else "PS1 Bedroom",
            if (music) t!!.artist.ifEmpty { "My Music" } else if (st > 0) extras.RADIO_STATIONS[st].substringAfter(": ") else "${env.W.label} · ${place?.name ?: "Your room"}",
            if (music) t!!.album else "PS1 Bedroom",
            if (music) mm.nowArt else null, music,
            if (music && stream != null) (stream.positionSec * 1000).toLong() else 0L,
            if (music && stream != null && !stream.durationSec.isNaN()) (stream.durationSec * 1000).toLong() else 0L))
    }
    fun toggleAudio() {
        val first = !sound.ready
        sound.toggle()
        if (first && sound.playing) toast("🔊 SOUND ON: every sound is placed in the room. Walk around and listen!")
        hud?.audioChanged(sound.playing)
        sound.heaterHum(extras.heaterIsOn)
        onAudioState?.invoke(sound.playing)
        publishMedia()
        settings.changed()
    }
    /** The start screen (or launch without it) turns the sound on if it was on last time. */
    fun startAudioIfWanted() { if (sound.wantAudio && !sound.playing) toggleAudio() }
    private var lastMonitorUpdate = 0f
    private val SPHERE_MONITOR_C = Vec3()
    /** Walk / sit / lie down (HTML playerMode); the on-screen UI that drives it. */
    val player: Player = Player(camera, { toast(it) }) { postureChanged() }
    private fun postureChanged() { hud?.postureChanged(player.mode, player.pullChairLabel()) }
    @Volatile var hud: Hud? = null
    fun toast(msg: String) { hud?.toast(msg); Unit }
    private val posted = java.util.concurrent.ConcurrentLinkedQueue<() -> Unit>()
    /** Runs [fn] on the GL thread at the start of the next frame (UI buttons). */
    fun post(fn: () -> Unit) = posted.add(fn)
    private var lastHeld: Int? = null
    lateinit var tv: Tv; private set
    lateinit var ps1: Ps1; private set
    /** PS2 / PS3 / PS4 / PS5 (built after everything else, so the grime map is untouched). */
    lateinit var consoles: Consoles; private set
    lateinit var library: GameLibrary; private set
    private var gta3d: Gta3d? = null
    /** The 3D-era GTA games render a chase-cam drive into a texture that the TV shows (only while one is playing). */
    private fun updateGtaTvFeed(t: Float) {
        if ((ytActive || hud?.yt?.active == true) && !tv.bedroomTvOn) {
            ytActive = false
            tv.ytShowing = false
            tv.useFeed(null)
            hud?.runUi { hud?.yt?.stop(true) }
            return
        }
        val game = library.currentGame()
        val active = tv.bedroomTvOn && tv.bedroomTvChannel == Tv.PS1_TV_CHANNEL && game.template == "gta3d" && ps1.phase(t) == "game" && !ytActive
        if (active) {
            val g3 = gta3d ?: Gta3d(renderer, env).also { gta3d = it }
            g3.setTheme(game.theme3d!!)
            g3.render(ps1.gameTime(t), SystemClock.uptimeMillis())
            tv.useFeed(g3.rt.texture)
        } else {
            // YouTube plays on the TV's own display: its newest frame becomes the CRT's picture
            val f = ytFeed
            if (ytActive && f != null && hud?.yt?.isFull != true) {
                f.update()
                tv.useFeed(f.rt.texture)
            } else {
                tv.useFeed(null)
            }
        }
    }
    /** YouTube's texture on the TV (made on the GL thread when playback starts). */
    @Volatile var ytFeed: YouTubeFeed? = null
    fun ensureYtFeed(ready: (android.view.Surface) -> Unit) = post {
        val f = ytFeed ?: YouTubeFeed(640, 480).also { ytFeed = it }
        ready(f.surface)
    }
    fun releaseYtFeed() = post {
        ytFeed?.release()
        ytFeed = null
    }
    private var lastTvUpdate = 0.05f
    private val SPHERE_TV_C = Vec3(-2.05f, 1.3f, 0.65f)
    /** Set by MainActivity before the GL thread starts (for raw model files). */
    lateinit var res: android.content.res.Resources
    private var lastCalendarCheck = 0f
    private var lastClockDraw = -99f
    private var lastSkylineUpdate = -99f
    private val SPHERE_WINDOW_C = Vec3(0.8f, 2.65f, -3.4f)

    // settings (the HTML defaults: Balanced quality, 240p, wobble 160, camera breathing 40%)
    var resShort = 240
    lateinit var settings: Settings
    @Volatile var pacer: FramePacer? = null
    fun setResolution(r: Int) { resShort = r; if (surfaceW > 1) renderer.resize(surfaceW, surfaceH, effectiveRes(), jitterBase) }
    fun setJitter(j: Float) { jitterBase = j; if (surfaceW > 1) renderer.resize(surfaceW, surfaceH, effectiveRes(), jitterBase) }
    var jitterBase = 160f
    var swayAmount = 0.4f
    private val basePos = Vec3()
    private var lastT = -1f
    val lastFrameT get() = lastT
    private var startMs = 0L
    private var built = false
    val isBuilt get() = built

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        try {
            renderer.init()
            if (!built) {
                val tex = Textures()
                room = Room(scene, tex)
                env = Environment(scene, room, renderer)
                // Each part is built at its place in the HTML's creation order (that keeps the grime maps 1:1)
                room.onBuildTv = { tv = Tv(room, env); tv.build() }
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
                    library = GameLibrary(room); library.camera = camera
                    desk = Desk(room, tex, library); desk.build()
                    monitor = Monitor(tex.monCanvasObj, desk.monScreenMat, room, desk.pcLedMat, env); monitor.update(0f)
                    ps1 = Ps1(room, tv); ps1.library = library; library.ps1 = ps1
                    ps1.build(desk.matJewelPlastic); tv.ps1 = ps1
                    room.extraDynamicRoots += ps1.ps1Group   // (not batched: the console under the TV can be swapped)
                    library.buildGtaShelf(desk.matJewelPlastic)   // HTML 6242, after the hookups
                    room.extraBatchExcluded.addAll(library.batchExcluded()); room.extraDynamicRoots += ps1.disc
                    room.extraDynamicRoots += ps1.ps1LidPivot
                    bedroom = Bedroom(room); bedroom.build()
                    room.extraDynamicRoots += bedroom.chairGroup
                    player.chairGroup = bedroom.chairGroup
                    fan = Fan(room); fan.build()
                    room.extraDynamicRoots += fan.fanBladesGroup; room.extraDynamicRoots += fan.fanPullChain; room.extraDynamicRoots += fan.lightPullChain
                    com.ps1bedroom.room.engine.PS1.syncGrime(256)   // HTML 8225: weather and season FX
                    weatherFx = WeatherFx(room, env, sky); weatherFx.renderer = renderer; room.extraDynamicRoots.addAll(weatherFx.dynamicRoots())
                    env.envHooks += { weatherFx.updateSettings() }
                    furniture = Furniture(room, env); furniture.build()
                    env.envHooks += { furniture.refreshCalendar() }
                    decor = Decor(room, res); decor.build()
                    room.extraBatchExcluded.addAll(decor.stringBulbs)
                    jasper = Jasper(room, env); jasper.build()
                    room.extraDynamicRoots += jasper.dogGroup
                    room.extraDynamicRoots += jasper.bowlsGroup
                    extras = Extras(room, env, tv, desk, bedroom); extras.build(); tv.tapes = extras
                    room.extraDynamicRoots.addAll(extras.dynamicRoots())
                    myMusic.buildRoomParts(); room.extraDynamicRoots.addAll(myMusic.dynamicRoots())
                    places = Places(room, env, sky, exterior, weatherFx) { renderer.shadowsNeedUpdate = true }   // HTML 14241: the places outside
                    room.extraDynamicRoots.addAll(places.dynamicRoots())
                    sky.placesPaint = places.paint
                    player.obstacles = listOf(tv.tvStandGroup to 0.7f, bedroom.chairGroup to 0.5f, jasper.dogGroup to 0.45f, extras.heaterGroup to 0.45f)
                    jasper.obstacles = listOf(tv.tvStandGroup to 0.75f, bedroom.chairGroup to 0.55f, extras.heaterGroup to 0.5f)
                    weatherFx.outsideGroundPaint = { c, w, h -> places.paint.outsideGroundPaint(c, w, h) }
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
                myMusic.onChanged = { hud?.refreshMenu(); hud?.refreshBoombox(); publishMedia() }
                scene.add(alarm.light)
                tv.tempText = { settings.formatTemp(settings.currentTempC()) }
                tv.cityText = { settings.cityName.ifEmpty { "LOCAL AREA" }.uppercase() }
                tv.rawTempC = { settings.currentTempC() }
                tv.tempUnit = { settings.tempUnit }
                tv.onPowerChanged = { on ->
                    if (!on) {
                        ytActive = false
                        tv.ytShowing = false
                        tv.useFeed(null)
                        hud?.runUi { hud?.yt?.stop(true) }
                    }
                }
                exterior.tempReadout = { settings.formatTemp(settings.currentTempC()) }
                exterior.cityReadout = { if (settings.weatherMode == "live") settings.cityName else "" }
                exterior.nowPlayingReadout = { if (ps1.ps1On) library.currentGame().title.uppercase() else "PS1 BEDROOM" }
                surprises = Surprises(this, alarm.context); surprises.build()
                consoles = Consoles(room, ps1); consoles.build(); ps1.consoles = consoles; consoles.toast = { toast(it) }
                jasper.onPet = { achievements.petted() }
                library.onInserted = { achievements.achieve("gamer") }
                myMusic.load()
                furniture.photoFor = { posterPhotoFor(it) }; extras.photoFor = { posterPhotoFor(it) }   // (before restore: a saved photo poster needs it)
                settings.restore()   // the HTML's applySavedSettings, once everything exists
                exterior.markWeatherDirty()
                ps1.sfx = { sound.sfx(it) }; ps1.stopBootSound = { sound.stopPs1BootSound() }; library.sfx = { sound.sfx(it) }
                env.envHooks += { sound.updateLayered() }
                weatherFx.onThunder = { sound.thunderclap() }

                exterior.onTrainPass = { sound.trainPass() }
                player.onStep = { onRug -> sound.sfx(if (onRug) "stepRug" else "step") }
                player.sfx = { sound.sfx(it) }
                jasper.onSound = { kind, at -> sound.dogSound(kind, at) }; jasper.toast = { toast(it) }
                extras.onHeaterHum = { on -> sound.heaterHum(on) }; extras.toast = { toast(it) }; extras.sfx = { sound.sfx(it) }
                hud?.refreshMenu(); hud?.refreshStatus()
                built = true
            }
        } catch (e: Throwable) {
            android.util.Log.e("PS1Bedroom", "Fatal error during onSurfaceCreated", e)
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
        if (!built || surfaceW <= 10 || surfaceH <= 10) return
        try {
            val t = (SystemClock.uptimeMillis() - startMs) / 1000f
            val dt = minOf(0.05f, if (lastT < 0f) 0f else t - lastT)
            lastT = t
            applyPerfLevel()
            while (true) { val fn = posted.poll() ?: break; fn() }
            if (built) settings.heartbeat()
            if (built) { sound.updateListener(); sound.periodic(lastT.coerceAtLeast(0f)); sound.tick() }
            applyInput()
            applyController(dt)
            handleTaps()
            env.tick(System.currentTimeMillis())
            if (debugPresetStep) { debugPresetStep = false; debugPreset = (debugPreset + 1) % env.TIME_PRESETS.size; env.setTimePreset(env.TIME_PRESETS[debugPreset].first) }
            if (debugPlaceStep) { debugPlaceStep = false; val keys = Places.OUTSIDE_ENVS.keys.toList(); places.setOutsideEnv(keys[(keys.indexOf(sky.outsideEnv) + 1) % keys.size]) }
            if (debugWeatherStep) { debugWeatherStep = false; val keys = Environment.WEATHER_KINDS.keys.toList(); env.weatherKind = keys[(keys.indexOf(env.weatherKind) + 1) % keys.size]; env.applyEnvironmentSettings() }
            weatherFx.update(t, dt)
            exterior.update(t, dt, sky.outsideEnv)
            places.update(t, dt)
            desk.update(t)
            monitor.tick(t)
            phone.update(t)
            myMusic.tick()
            achievements.tick()
            steadyTextures.tick()
            alarm.tick(); bedroom.alarmSet = alarm.on
            // The monitor redraws ~6x a second, only while the PC is on and the screen is in view
            desk.monScreen.updateMatrixWorld(); SPHERE_MONITOR_C.setFromMatrixPosition(desk.monScreen.matrixWorld)
            if (monitor.pcPoweredOn && t - lastMonitorUpdate > 0.16f && renderer.sphereInView(SPHERE_MONITOR_C, 0.6f)) { monitor.update(t); lastMonitorUpdate = t }
            fan.update(dt)
            decor.update(t)
            jasper.update(t, dt)
            extras.update(t, dt, fanLightOn, furniture.lampOn, decor.stringLightsOn)
            jasper.bedroomTvOn = tv.bedroomTvOn
            tv.updateLight(t)
            ps1.update(dt)
            consoles.update(t, dt)
            library.update(t, dt)
            if (library.heldGame != lastHeld) { lastHeld = library.heldGame; hud?.holdingGame(lastHeld != null) }
            if (player.updateChair(dt)) renderer.shadowsNeedUpdate = true
            updateGtaTvFeed(t)
            // The TV redraws ~6x a second (the PlayStation ~16x) and only while it is on and on screen, as in the HTML
            if (tv.bedroomTvOn && t - lastTvUpdate > (if (tv.bedroomTvChannel == Tv.PS1_TV_CHANNEL) 0.06f else 0.16f) && renderer.sphereInView(SPHERE_TV_C, 0.7f)) { tv.updateBedroomTvScreen(t); lastTvUpdate = t }
            room.updateAc(t, dt)
            if (t - lastCalendarCheck > 60f) { lastCalendarCheck = t; furniture.refreshCalendar() }
            if (t - lastClockDraw > 1f) { lastClockDraw = t; bedroom.drawAlarmClock(env.currentHour()) }
            room.updateBlinds(dt)
            weatherFx.updateLightning(dt * 60f)
            // The backdrop redraws ~3x a second while the window is on screen, every 2.5 s otherwise (as in the HTML)
            if (t - lastSkylineUpdate > (if (renderer.sphereInView(SPHERE_WINDOW_C, 2.0f)) 0.33f else 2.5f)) {
                sky.updateCitySkyline(t)
                lastSkylineUpdate = t
            }
            // Camera modes: orbit, first-person walk, sitting in the chair, or lying on the bed
            if (player.mode == "orbit") {
                controls.update()
                // Keep the free camera inside the room (the debug option lifts this)
                if (!settings.debugFreeCam) {
                    camera.position.x = max(Room.MIN_X + 0.2f, min(Room.MAX_X - 0.2f, camera.position.x))
                    camera.position.y = max(Room.MIN_Y + 0.25f, min(Room.MAX_Y - 0.2f, camera.position.y))
                    camera.position.z = max(Room.MIN_Z + 0.2f, min(Room.MAX_Z - 0.2f, camera.position.z))
                }
                basePos.copy(camera.position)
                // Camera breathing (the HTML's orbit sway)
                if (swayAmount > 0.001f) {
                    camera.position.x += sin(t * 0.55f) * 0.035f * swayAmount
                    camera.position.y += cos(t * 0.42f) * 0.025f * swayAmount
                }
            } else {
                player.updateCamera(t, dt, swayAmount)
                basePos.copy(camera.position)
            }
            renderer.post.time = t
            if (windowShotDue(t)) takeWindowShot()
            renderer.render(scene, camera)
            if (ytActive) placeYouTube()
            photoCallback?.let { cb -> photoCallback = null; readScreen()?.let { b -> cb(b) } }
            camera.position.copy(basePos)
        } catch (e: Throwable) {
            android.util.Log.e("PS1Bedroom", "Error in onDrawFrame", e)
        }
    }

    // ---- Automatic quality (the HTML's setPerfLevel), driven here by Android's real thermal status ----
    // level 0: as set · 1: fewer light-ray steps · 2: 80% resolution, no ambient occlusion · 3: 65% resolution
    @Volatile private var wantedPerfLevel = 0
    private var perfLevel = 0
    var aoBase = 0.8f
    var marchSteps = 14
    private fun effectiveRes() = maxOf(120, (resShort * floatArrayOf(1f, 1f, 0.85f, 0.7f)[perfLevel]).toInt())
    fun onThermal(status: Int) {
        wantedPerfLevel = when {
            status >= android.os.PowerManager.THERMAL_STATUS_CRITICAL -> 2
            status >= android.os.PowerManager.THERMAL_STATUS_SEVERE -> 1
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
    fun tapCenter() = onTap(surfaceW * 0.5f, surfaceH * 0.5f, surfaceW.toFloat(), surfaceH.toFloat())
    private fun handleTaps() {
        val list = synchronized(lock) { if (taps.isEmpty()) return; ArrayList(taps).also { taps.clear() } }
        for (p in list) {
            raycaster.setFromCamera(p[0], p[1], camera)
            val hit = raycaster.intersect(room.interactables) ?: continue
            onHaptic?.invoke()   // a light tick when you tap something in the room
            triggerEasterEgg(hit.first.userData["interactTag"] as String? ?: continue)
        }
    }
    private fun triggerEasterEgg(tag: String) {
        val isPs1Tag = tag == "ps1Power" || tag == "ps1Reset" || tag == "ps1Open" || tag == "ps1Body" || tag == "ps1Pad"
        if (library.swapping && (isPs1Tag || tag.startsWith("gamebox:"))) return // let the disc swap finish
        if (library.heldGame != null && isPs1Tag) { library.insertHeldGame(lastT); return } // holding a game + tapping the console = put it in
        if (tag.startsWith("gamebox:")) { library.tapCase(tag.substringAfter(':').toInt()); return }
        if (tag.startsWith("vhs:")) { extras.insertTape(tag.substringAfter(':').toInt(), lastT); return }
        when (tag) {
            "blinds" -> room.cycleWindowBlinds()
            "switch" -> toggleFanLight()
            "lavalamp" -> toast(desk.cycleLava())
            "cat" -> bedroom.toggleCatEyes()
            "fan" -> toggleFanLight()
            "fanspin" -> fan.toggleCeilingFanSpin()
            "ac" -> room.toggleAcUnit()
            "lamp" -> furniture.toggleLamp()
            "string" -> decor.cycleStringLights()
            "dog" -> jasper.petDog(lastT)
            "dogFood" -> jasper.feed(lastT)
            "wastebasket" -> { sfx("caseClick"); hud?.openPaperToss() }
            "dogWater" -> jasper.giveWater(lastT)
            "tv" -> if (ytActive) hud?.yt?.let { yt -> hud?.runUi { yt.togglePlay() } } else tv.cycleBedroomTvChannel(lastT)
            "tvPower" -> tvPower()
            "ps1Power" -> ps1.powerToggle(lastT)
            "ps1Reset" -> ps1.resetPress(lastT)
            "ps1Open" -> ps1.lidToggle(lastT, true)
            "ps1Pad" -> ps1.padPress(lastT)
            "machine" -> phone.playMessages { ms, fn -> hud?.later(ms, fn) }
            "heater" -> extras.cycleHeater()
            "poster" -> extras.cyclePoster()
            "boombox", "bbscreen" -> hud?.openBoombox()   // the boombox player (HTML openBoombox)
            "pc" -> { monitor.setPower(!monitor.pcPoweredOn); toast(if (monitor.pcPoweredOn) "💾 Rebooting HP Pavilion... Windows 98 / XP ScanDisk complete." else "⚠️ \"DO NOT TURN OFF!\" You just killed a 48-hour Napster download at 99%!") }
            "monitor" -> openPc()
            "phone" -> if (phone.ringing) phone.answer() else { toast("📞 56k V.90 MODEM: \"MOM HANG UP THE PHONE! I'M IN A STARCRAFT MATCH!\""); sfx("phone") }
            "door" -> toast("🚧 UNDER CONSTRUCTION: The rest of the house is still being built. Check back in a future update!")
            "surprise" -> surprises.find()
            "bandposter" -> { toast(furniture.cycleBandPoster()); sfx("caseClick") }
            "discman" -> toast("💿 SONIX D-SPIN Discman: 45-second anti-skip! A burned mix CD is still inside.")
            "gba" -> toast("🎮 GAME KID ADVANCE: The battery light is red. Of course it is.")
            "brickphone" -> toast("📱 NOKA 3310: 1 new text message. You have 2 bars and a high score in Snake.")
            "tama" -> toast("🥚 TAMAGOTCHU: Your pet is hungry. Again. It has been hungry since 1998.")
            "box" -> toast("📦 A box of old stuff: Pogs, a Tamagotchi with dead batteries, a Furby that still talks at night.")
            "dresser" -> toast("🗄️ DRESSER: Stuffed with band tees, a Tamagotchi with dead batteries and a stack of GamePro magazines.")
            "chair" -> if (player.mode != "chair") setPlayerMode("chair") else pullChair()
            "bed" -> setPlayerMode(if (player.mode == "bed") "walk" else "bed")
        }
        when (tag) {
            "cat" -> toast("🐱 MIDNIGHT CAT: *Low-Bit PS1 Meow* (Night-vision eyes toggled!)")
            "ps1Body" -> toast(if (consoles.current == "ps1") "🎮 PLAYSTATION: Click POWER (left), RESET (small) or OPEN (right) on top, or the lid itself." else "🎮 ${Consoles.FULL[consoles.current]?.uppercase()}: tap the POWER or EJECT buttons on the front, or the controller. Change consoles in Menu → Room.")
        }
        // The HTML plays these from inside each toggle
        when (tag) {
            "blinds" -> sfx("blinds")
            "switch" -> { sfx("lightSwitch"); sfx("lightChain") }
            "fan" -> sfx("lightChain")
            "fanspin" -> sfx("fanChain")
            "ac" -> sfx(if (room.acUnitOn) "acOn" else "acOff")
            "tv" -> sfx("tvChannel")
            "tvPower" -> sfx(if (tv.bedroomTvOn) "tvOn" else "tvOff")
            "lavalamp" -> sfx("lavaSwitch")
            "cat" -> sfx(if (bedroom.catEyesGlowing) "meow" else "purr")
            "pc" -> sfx(if (monitor.pcPoweredOn) "pcOn" else "pcOff")
            "door", "discman", "brickphone", "tama", "box", "dresser" -> sfx("caseClick")
            "gba" -> sfx("ps1Button")
            "boombox", "bbscreen" -> sfx("ui")
            "lamp", "string" -> sfx("ui")
        }
        sound.updateLayered()
    }
    fun setPlayerMode(mode: String) = player.setMode(mode) {
        camera.position.copy(defaultCamPos)
        controls.target.copy(defaultTarget)
        controls.update()
    }
    fun pullChair() = player.moveChair(1) { setPlayerMode("orbit") }
    fun putBackHeld() = library.putBackHeld()
    fun tvNextChannel() = tv.cycleBedroomTvChannel(lastT)
    fun tvPower() {
        tv.toggleBedroomTv(lastT)
        if (!tv.bedroomTvOn) {
            ytActive = false
            tv.ytShowing = false
            tv.useFeed(null)
            hud?.runUi { hud?.yt?.stop(true) }
        }
    }
    fun monitorNextChannel() = toast(monitor.cycleChannel())
    /** Tap the monitor: sit down at the desk and use the PC (PcDesktop). */
    fun openPc() {
        if (!monitor.pcPoweredOn) { toast("🖥 The PC is off. Tap the tower to boot it up."); return }
        if (player.mode != "chair" || player.chairSpot != 1) player.goToChairSpot(1) { setPlayerMode("orbit") }
        hud?.openPc()
    }
    // ---- Photo mode: the next frame is read back from the screen ----
    @Volatile private var photoCallback: ((android.graphics.Bitmap) -> Unit)? = null
    fun requestPhoto(cb: (android.graphics.Bitmap) -> Unit) { photoCallback = cb }
    private fun readScreen(): android.graphics.Bitmap? {
        val w = surfaceW; val h = surfaceH
        if (w <= 10 || h <= 10) return null
        return try {
            val buf = java.nio.ByteBuffer.allocateDirect(w * h * 4).order(java.nio.ByteOrder.nativeOrder())
            android.opengl.GLES30.glBindFramebuffer(android.opengl.GLES30.GL_FRAMEBUFFER, 0)
            android.opengl.GLES30.glReadPixels(0, 0, w, h, android.opengl.GLES30.GL_RGBA, android.opengl.GLES30.GL_UNSIGNED_BYTE, buf)
            val src = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            buf.rewind(); src.copyPixelsFromBuffer(buf)
            // GL rows run bottom-up: flip
            val m = android.graphics.Matrix().apply { preScale(1f, -1f) }
            val out = android.graphics.Bitmap.createBitmap(src, 0, 0, w, h, m, false)
            src.recycle()
            out
        } catch (e: Throwable) {
            android.util.Log.w("PS1Bedroom", "readScreen failed", e)
            null
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
    @Volatile private var debugWeatherStep = false
    fun debugNextWeather() { debugWeatherStep = true }
    @Volatile private var debugPlaceStep = false
    fun debugNextPlace() { debugPlaceStep = true }
    private var debugPreset = -1
    fun debugNextTimePreset() { debugPresetStep = true }

    // ---- Controller / Gamepad state (from MainActivity) ----
    @Volatile var padLx = 0f
    @Volatile var padLy = 0f
    @Volatile var padRx = 0f
    @Volatile var padRy = 0f
    @Volatile var padZoom = 0f
    private var padWasMoving = false
    private fun applyController(dt: Float) {
        if (dt <= 0f) return
        when (player.mode) {
            "walk" -> {
                if (padLx != 0f || padLy != 0f) {
                    player.stickX = padLx
                    player.stickY = -padLy
                    padWasMoving = true
                    hud?.updateStickVisual(padLx, -padLy)
                } else if (padWasMoving) {
                    player.stickX = 0f
                    player.stickY = 0f
                    padWasMoving = false
                    hud?.updateStickVisual(0f, 0f)
                }
                if (padRx != 0f || padRy != 0f) player.look(padRx * dt * 420f, padRy * dt * 310f)
                if (padZoom != 0f) player.pinchZoom(1f + padZoom * dt * 1.4f)
            }
            "chair", "bed" -> {
                if (padWasMoving) { padWasMoving = false; hud?.updateStickVisual(0f, 0f) }
                val lx = (padLx + padRx).coerceIn(-1f, 1f)
                val ly = (padLy + padRy).coerceIn(-1f, 1f)
                if (lx != 0f || ly != 0f) player.look(lx * dt * 400f, ly * dt * 300f)
                if (padZoom != 0f) player.pinchZoom(1f + padZoom * dt * 1.4f)
            }
            else -> {
                if (padWasMoving) { padWasMoving = false; hud?.updateStickVisual(0f, 0f) }
                if (padLx != 0f || padLy != 0f) controls.panBy(-padLx * dt * 380f * density, -padLy * dt * 380f * density, viewHeight)
                if (padRx != 0f || padRy != 0f) controls.rotateBy(padRx * dt * 460f * density, padRy * dt * 340f * density, viewHeight)
                if (padZoom != 0f) controls.dollyBy(1f + padZoom * dt * 1.4f)
            }
        }
    }

    // ---- input from the UI thread ----
    private val lock = Any()
    private var dragX = 0f; private var dragY = 0f; private var pinch = 1f; private var panX = 0f; private var panY = 0f; private var wheel = 0f
    @Volatile var viewHeight = 1f
    @Volatile var density = 1f
    fun onDrag(dx: Float, dy: Float) = synchronized(lock) { dragX += dx; dragY += dy }
    fun onPinch(ratio: Float) = synchronized(lock) { pinch *= ratio }
    fun onPan(dx: Float, dy: Float) = synchronized(lock) { panX += dx; panY += dy }
    fun onWheel(d: Float) = synchronized(lock) { wheel += d }
    private fun applyInput() = synchronized(lock) {
        if (player.mode != "orbit") {
            // First person: one finger looks around, two fingers pinch-zoom the field of view
            val k = 1f / density // the HTML turns 0.0045 rad per CSS pixel
            if (dragX != 0f || dragY != 0f) player.look(dragX * k, dragY * k)
            if (pinch != 1f) player.pinchZoom(pinch)
        } else {
            if (dragX != 0f || dragY != 0f) controls.rotateBy(dragX, dragY, viewHeight)
            if (pinch != 1f) controls.dollyBy(pinch)
            if (panX != 0f || panY != 0f) controls.panBy(panX, panY, viewHeight)
            if (wheel != 0f) controls.wheel(wheel)
        }
        dragX = 0f; dragY = 0f; pinch = 1f; panX = 0f; panY = 0f; wheel = 0f
    }
}
