package com.ps1bedroom.room

import android.app.Activity
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.hypot

/** Full-screen GL view of the room. Touch: one finger orbits, two fingers pinch-zoom and pan. */
class MainActivity : Activity() {
    private lateinit var view: GLSurfaceView
    private val app = App()
    private lateinit var pacer: FramePacer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view = object : GLSurfaceView(this) {
            override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { super.onSizeChanged(w, h, ow, oh); app.viewHeight = h.toFloat() }
        }
        view.setEGLContextClientVersion(3)
        // No depth / stencil on the window surface: the 3D renders into its own low-res target
        view.setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        view.preserveEGLContextOnPause = true
        app.res = resources
        view.setRenderer(app)
        view.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        view.setOnTouchListener { _, e -> onTouch(e); true }
        val root = android.widget.FrameLayout(this)
        root.addView(view)
        app.density = resources.displayMetrics.density
        app.settings = Settings(app, this)
        app.myMusic = MyMusic(app, this)
        app.posterPhotoFor = { which -> posterPhoto(which) }
        app.achievements = Achievements(app, this); app.alarm = Alarm(app, this)
        app.pickMusic = { folder -> runOnUiThread { pickMusic(folder) } }
        app.pickPosterPhoto = { which -> runOnUiThread { posterTarget = which
            @Suppress("DEPRECATION") try { startActivityForResult(android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).addCategory(android.content.Intent.CATEGORY_OPENABLE).setType("image/*"), 44) } catch (e: Exception) { app.toast("📷 No photo picker on this device") } } }
        app.onMediaState = { s -> runOnUiThread { MediaService.update(this, s); NowPlayingWidget.update(this, s) } }
        MediaService.onControl = { cmd -> app.postOrRun { app.mediaCommand(cmd) } }
        MediaService.onSeek = { ms -> app.postOrRun { app.sound.musicStream?.seek(ms / 1000.0); app.publishMedia() } }
        app.cutout = Cutout(this, root, view)
        app.onHaptic = { view.post { view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK) } }
        app.pipEnabled = getSharedPreferences("ps1bedroom", MODE_PRIVATE).getBoolean("pip", true)
        app.onWindowShot = { clear, blinds, weather, place -> Thread { WindowWidget.saveInfo(this, weather, place); WindowWidget.saveShots(this, clear, blinds); RoomWidgetReceiver.updateAllWidgets(this, app); clear.recycle(); blinds.recycle() }.start() }
        RoomWidgetReceiver.onCommand = { cmd -> handleWidgetCommand(cmd) }
        app.keepScreenOn = { on -> runOnUiThread { if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } }
        UiTheme.load(this)
        app.hud = Hud(this, root, app).also { it.attachMenu() }
        app.cutout?.let { co ->
            co.onSafeChanged = { rect -> app.hud?.applySafeInsets(rect) }
            app.hud?.applySafeInsets(co.uiSafe)
        }
        setContentView(root)
        pacer = FramePacer(this, view, app)
        app.pacer = pacer
        app.onAudioState = { on -> if (on) runOnUiThread { askNotifications() } }
        // (straight away: the GL thread, which runs posted work, is paused while the app is in the background)
        hideSystemBars()
        handleShare(intent)
        handleShortcut(intent)
    }
    private fun handleWidgetCommand(cmd: String): Boolean {
        if (!app.isBuilt) return false
        app.postOrRun {
            when (cmd) {
                "toggle_sound" -> app.toggleAudio()
                "toggle_light" -> { app.toggleFanLight(); app.settings.changed() }
                "toggle_fan" -> { app.fan.toggleCeilingFanSpin(); app.sound.updateLayered(); app.settings.changed() }
                "toggle_lamp" -> { app.furniture.toggleLamp(); app.settings.changed() }
                "toggle_tv" -> { app.tvPower(); app.settings.changed() }
                "cycle_blinds" -> { app.room.cycleWindowBlinds(); app.settings.changed() }
                "step_weather" -> app.settings.stepStatus("weather")
                "step_season" -> app.settings.stepStatus("season")
                "step_place" -> app.settings.stepStatus("place")
                "next_station" -> { val n = app.extras.RADIO_STATIONS.size; app.extras.setStation((app.extras.radioStation + 1) % n, app.furniture.radioDisplayMat); if (!app.sound.playing && app.extras.radioStation > 0) app.toggleAudio(); app.publishMedia(); app.settings.changed() }
                "sleep_mode" -> app.settings.startSleepMode()
                "sleep_15" -> app.settings.setSleepTimer(if (app.settings.sleepEnd > 0) 0 else 15)
                "toggle_alarm" -> { app.alarm.set(on = !app.alarm.on); app.settings.changed() }
                "alarm_time" -> { app.alarm.set(time = RoomWidgetReceiver.nextAlarmTime(app.alarm.time)); app.toast("⏰ Alarm set for ${app.alarm.time}"); app.settings.changed() }
                "jasper_pet" -> { app.jasper.petDog(app.lastFrameT); app.settings.changed() }
                "jasper_feed" -> { app.jasper.feed(app.lastFrameT); app.settings.changed() }
                "jasper_water" -> { app.jasper.giveWater(app.lastFrameT); app.settings.changed() }
                "jasper_play" -> { app.jasper.playWith(app.lastFrameT); app.settings.changed() }
                "jasper_sleep" -> { app.jasper.sendToSleep(app.lastFrameT); app.settings.changed() }
                "refresh_window" -> { app.windowShotNow = true; app.toast("🪟 Updated Window widget snapshot!") }
                "paper_toss" -> app.hud?.runUi { app.hud?.openPaperToss() }
            }
            app.windowShotNow = true   // every widget change shows up on the Window widget straight away
            app.settings.saveNow()
            view.post {
                try {
                    RoomWidgetReceiver.updateAllWidgets(this@MainActivity, app)
                } catch (_: Exception) { }
            }
        }
        return true
    }

    /** Long-press app shortcuts (res/xml/shortcuts.xml), widget actions, and the Quick Settings tile. */
    private fun handleShortcut(i: android.content.Intent?) {
        val a = i?.action ?: return
        if (a.startsWith("com.ps1bedroom.room.WIDGET_")) {
            val cmd = a.removePrefix("com.ps1bedroom.room.WIDGET_")
            view.postDelayed({ handleWidgetCommand(cmd) }, if (app.isBuilt) 0L else 2800L)
            return
        }
        val run: () -> Unit = when (a) {
            "com.ps1bedroom.room.SLEEP" -> { { app.post { app.settings.startSleepMode() } } }
            "com.ps1bedroom.room.MUSIC" -> { { app.hud?.openBoombox() } }
            "com.ps1bedroom.room.PHOTO" -> { { app.hud?.overlays?.enterPhotoMode() } }
            "com.ps1bedroom.room.PC" -> { { app.post { app.openPc() } } }
            else -> return
        }
        view.postDelayed(run, if (app.isBuilt) 0L else 3000L)
    }
    /** Leaving the app (home button / gesture) while the room is up: shrink it into picture-in-picture. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (app.pipEnabled && android.os.Build.VERSION.SDK_INT >= 26 && app.isBuilt && app.hud?.overlays?.bootShowing != true) {
            val w = view.width.coerceAtLeast(1); val h = view.height.coerceAtLeast(1)
            // PiP allows 1:2.39 to 2.39:1
            val r = (w.toFloat() / h).coerceIn(0.42f, 2.39f)
            val ratio = if (r < 1f) android.util.Rational(1000, (1000 / r).toInt()) else android.util.Rational((1000 * r).toInt(), 1000)
            try { enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().setAspectRatio(ratio).build()) } catch (e: Exception) { }
        }
    }
    override fun onPictureInPictureModeChanged(inPip: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(inPip, newConfig)
        app.hud?.setUiHidden(inPip)
        if (!inPip && !hasWindowFocus()) { /* closed from PiP: onStop follows */ }
    }
    /** A YouTube link shared to PS1 Bedroom plays on the bedroom TV. */
    private fun handleShare(i: android.content.Intent?) {
        if (i?.action != android.content.Intent.ACTION_SEND) return
        val text = i.getStringExtra(android.content.Intent.EXTRA_TEXT) ?: return
        view.postDelayed({ app.hud?.yt?.playLink(text) }, if (app.isBuilt) 0L else 2500L)
    }
    override fun onNewIntent(intent: android.content.Intent?) { super.onNewIntent(intent); setIntent(intent); handleShare(intent); handleShortcut(intent) }

    // ---- My Music: the system file picker (songs, or a whole album folder) ----
    @Suppress("DEPRECATION")
    private fun pickMusic(folder: Boolean) {
        val i = if (folder) android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT_TREE)
        else android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).addCategory(android.content.Intent.CATEGORY_OPENABLE).setType("audio/*").putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, true)
        i.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        try { startActivityForResult(i, if (folder) 41 else 40) } catch (e: Exception) { app.toast("🎵 No file picker on this device") }
    }
    @Deprecated("Activity results")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 44 && resultCode == RESULT_OK && data?.data != null) { savePosterPhoto(data.data!!); return }
        if (resultCode != RESULT_OK || data == null || (requestCode != 40 && requestCode != 41)) return
        val mm = app.myMusic
        Thread {
            val items = ArrayList<MyMusic.Item>()
            if (requestCode == 41) {
                data.data?.let { tree -> mm.takePermission(tree); items += mm.listTree(tree) }
            } else {
                val uris = ArrayList<android.net.Uri>()
                data.clipData?.let { c -> for (k in 0 until c.itemCount) uris += c.getItemAt(k).uri } ?: data.data?.let { uris += it }
                for (u in uris) {
                    var name = u.lastPathSegment ?: "song"
                    try { contentResolver.query(u, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) name = c.getString(0) ?: name } } catch (e: Exception) { }
                    items += MyMusic.Item(u, name, name, contentResolver.getType(u) ?: "")
                }
            }
            mm.import(items)
        }.start()
    }

    /** Memory pressure (or the app went to the background): drop what can be re-made. */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) app.postOrRun { app.myMusic.trim() }
    }

    // ---- Your own photos on the posters (#18): a small copy (max 512 px) kept in the app ----
    private var posterTarget = "band"
    private val posterPhotos = HashMap<String, android.graphics.Bitmap?>()
    private fun posterFile(which: String) = java.io.File(filesDir, "poster_$which.jpg")
    private fun posterPhoto(which: String): android.graphics.Bitmap? = posterPhotos.getOrPut(which) { posterFile(which).takeIf { it.exists() }?.let { android.graphics.BitmapFactory.decodeFile(it.path) } }
    private fun savePosterPhoto(uri: android.net.Uri) {
        val which = posterTarget
        Thread {
            try {
                val src = contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) } ?: throw IllegalStateException()
                val k = minOf(1f, 512f / maxOf(src.width, src.height))
                val b = if (k < 1f) android.graphics.Bitmap.createScaledBitmap(src, (src.width * k).toInt().coerceAtLeast(1), (src.height * k).toInt().coerceAtLeast(1), true) else src
                java.io.FileOutputStream(posterFile(which)).use { b.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
                posterPhotos[which] = b
                app.post { if (which == "band") app.furniture.setBandPoster("photo") else app.extras.setPoster("photo"); app.toast("📷 Your photo is up on the poster"); app.settings.changed() }
            } catch (e: Exception) { app.toast("📷 Couldn't open that picture") }
        }.start()
    }

    override fun onResume() {
        super.onResume(); app.glActive = true; view.onResume(); pacer.interacted(); pacer.start(); hideSystemBars()
        app.windowWidgets = WindowWidget.hasWidgets(this)
        app.windowShotIntervalSec = WindowWidget.refreshIntervalSec(this)
        if (app.windowWidgets) app.windowShotNow = true
        try {
            RoomWidgetReceiver.updateAllWidgets(this, if (app.isBuilt) app else null)
        } catch (_: Exception) { }
    }
    private fun askNotifications() { if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7) }
    override fun onDestroy() { RoomWidgetReceiver.onCommand = null; MediaService.onControl = null; MediaService.onSeek = null; MediaService.stop(this); NowPlayingWidget.update(this, null); app.sound.shutdown(); super.onDestroy() }
    @Deprecated("Activity back handling")
    override fun onBackPressed() { if (app.hud?.back() != true) @Suppress("DEPRECATION") super.onBackPressed() }
    override fun onPause() {
        // In picture-in-picture the activity is paused but still on screen: keep drawing
        if (android.os.Build.VERSION.SDK_INT >= 24 && isInPictureInPictureMode) { super.onPause(); return }
        // One last Window-widget snapshot of the room as you leave it (waits at most ~400 ms)
        if (app.isBuilt && app.windowWidgets) {
            val latch = java.util.concurrent.CountDownLatch(1)
            view.queueEvent { try { app.windowShotOnLeave() } catch (_: Exception) { } finally { latch.countDown() } }
            try { latch.await(400, java.util.concurrent.TimeUnit.MILLISECONDS) } catch (_: Exception) { }
        }
        pacer.stop(); view.onPause(); app.glActive = false; super.onPause()
    }
    override fun onStop() { if (android.os.Build.VERSION.SDK_INT >= 24 && isInPictureInPictureMode) { pacer.stop(); view.onPause(); app.glActive = false }; super.onStop() }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) hideSystemBars() }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        }
    }

    // ---- touch: same gestures as OrbitControls on a phone ----
    private var lastX = 0f; private var lastY = 0f
    private var lastDist = 0f; private var lastMidX = 0f; private var lastMidY = 0f
    private var downX = 0f; private var downY = 0f; private var wasPinching = false
    private fun onTouch(e: MotionEvent) {
        pacer.interacted()
        app.hud?.overlays?.onScreenTouched()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastX = e.x; lastY = e.y; downX = e.x; downY = e.y; wasPinching = false }
            MotionEvent.ACTION_UP -> {
                // a tap: the finger barely moved and it wasn't a pinch (14 CSS px, as in the HTML)
                if (!wasPinching && hypot(e.x - downX, e.y - downY) <= 14f * resources.displayMetrics.density) app.onTap(e.x, e.y, view.width.toFloat(), view.height.toFloat())
            }
            MotionEvent.ACTION_POINTER_DOWN -> if (e.pointerCount >= 2) {
                wasPinching = true
                lastDist = hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))
                lastMidX = (e.getX(0) + e.getX(1)) / 2; lastMidY = (e.getY(0) + e.getY(1)) / 2
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val keep = if (e.actionIndex == 0) 1 else 0
                lastX = e.getX(keep); lastY = e.getY(keep)
            }
            MotionEvent.ACTION_MOVE -> {
                if (e.pointerCount >= 2) {
                    val d = hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))
                    val mx = (e.getX(0) + e.getX(1)) / 2; val my = (e.getY(0) + e.getY(1)) / 2
                    if (lastDist > 0f) app.onPinch(d / lastDist)
                    app.onPan(mx - lastMidX, my - lastMidY)
                    lastDist = d; lastMidX = mx; lastMidY = my
                } else {
                    app.onDrag(e.x - lastX, e.y - lastY)
                    lastX = e.x; lastY = e.y
                }
            }
        }
    }

    // ---- Full controller / gamepad support across the entire app ----
    private var dpadUp = false; private var dpadDown = false; private var dpadLeft = false; private var dpadRight = false
    private var stickLx = 0f; private var stickLy = 0f
    private fun deadzone(v: Float, dz: Float = 0.14f): Float = if (abs(v) < dz) 0f else ((abs(v) - dz) / (1f - dz)) * kotlin.math.sign(v)
    private fun syncLeftPad() {
        val dx = (if (dpadRight) 1f else 0f) - (if (dpadLeft) 1f else 0f)
        val dy = (if (dpadDown) 1f else 0f) - (if (dpadUp) 1f else 0f)
        app.padLx = if (abs(stickLx) >= abs(dx)) stickLx else dx
        app.padLy = if (abs(stickLy) >= abs(dy)) stickLy else dy
    }
    private fun isUiModalOpen(): Boolean {
        val h = app.hud ?: return false
        return h.menu.isOpen || h.yt.dialogOpen || h.pcDesktop.isOpen || h.isBoomboxOpen
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        val src = ev.source
        if ((src and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
            (src and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) {
            if (ev.action == MotionEvent.ACTION_MOVE) {
                pacer.interacted()
                app.hud?.setControllerActive(true)
                val lx = deadzone(ev.getAxisValue(MotionEvent.AXIS_X))
                val ly = deadzone(ev.getAxisValue(MotionEvent.AXIS_Y))
                val hatX = deadzone(ev.getAxisValue(MotionEvent.AXIS_HAT_X), 0.2f)
                val hatY = deadzone(ev.getAxisValue(MotionEvent.AXIS_HAT_Y), 0.2f)
                // Right stick: standard Android gamepads use AXIS_Z / AXIS_RZ; some use AXIS_RX / AXIS_RY
                val rxRaw = ev.getAxisValue(MotionEvent.AXIS_Z).let { if (abs(it) > 0.08f) it else ev.getAxisValue(MotionEvent.AXIS_RX) }
                val ryRaw = ev.getAxisValue(MotionEvent.AXIS_RZ).let { if (abs(it) > 0.08f) it else ev.getAxisValue(MotionEvent.AXIS_RY) }
                val rx = deadzone(rxRaw)
                val ry = deadzone(ryRaw)
                val lt = maxOf(ev.getAxisValue(MotionEvent.AXIS_LTRIGGER), ev.getAxisValue(MotionEvent.AXIS_BRAKE))
                val rt = maxOf(ev.getAxisValue(MotionEvent.AXIS_RTRIGGER), ev.getAxisValue(MotionEvent.AXIS_GAS))

                if (isUiModalOpen()) {
                    stickLx = 0f; stickLy = 0f; syncLeftPad()
                    app.padRx = 0f; app.padRy = 0f; app.padZoom = 0f
                    return super.dispatchGenericMotionEvent(ev)
                }
                stickLx = if (abs(lx) >= abs(hatX)) lx else hatX
                stickLy = if (abs(ly) >= abs(hatY)) ly else hatY
                syncLeftPad()
                app.padRx = rx
                app.padRy = ry
                app.padZoom = deadzone(rt - lt, 0.08f)
                return true
            }
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    override fun dispatchKeyEvent(ev: KeyEvent): Boolean {
        val code = ev.keyCode
        val down = ev.action == KeyEvent.ACTION_DOWN
        val up = ev.action == KeyEvent.ACTION_UP
        val repeat = ev.repeatCount > 0
        if (down || up) pacer.interacted()
        val isPadKey = KeyEvent.isGamepadButton(code) ||
            code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN ||
            code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT ||
            code == KeyEvent.KEYCODE_DPAD_CENTER
        if (isPadKey && down) app.hud?.setControllerActive(true)

        val hud = app.hud
        if (hud != null && hud.isSleeping && down && !repeat) {
            hud.wakeFromSleep()
            return true
        }

        // Start / Menu button toggles the main menu from anywhere
        if ((code == KeyEvent.KEYCODE_BUTTON_START || code == KeyEvent.KEYCODE_MENU) && down && !repeat) {
            hud?.let {
                if (it.overlays.bootShowing || it.overlays.tutorialOpen || it.overlays.photoMode) {
                    it.overlays.onControllerConfirm()
                } else if (it.menu.isOpen) {
                    it.menu.close()
                } else {
                    it.menu.open(null)
                }
            }
            return true
        }

        // Select / Back button toggles Photo Mode when in the room
        if (code == KeyEvent.KEYCODE_BUTTON_SELECT && down && !repeat) {
            hud?.let {
                if (it.overlays.photoMode) it.overlays.exitPhotoMode()
                else if (!isUiModalOpen() && !it.overlays.bootShowing) it.overlays.enterPhotoMode()
            }
            return true
        }

        // Boot / tutorial / photo mode overlays
        if (hud != null && (hud.overlays.bootShowing || hud.overlays.tutorialOpen || hud.overlays.photoMode)) {
            if (down && !repeat) {
                when (code) {
                    KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        hud.overlays.onControllerConfirm()
                        return true
                    }
                    KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_ESCAPE -> {
                        hud.back()
                        return true
                    }
                }
            }
            if (!hud.overlays.photoMode) return super.dispatchKeyEvent(ev)
        }

        // When a UI sheet/dialog is open: D-pad navigates focus, A clicks, B goes back, L1/R1 switches sub-tabs
        if (isUiModalOpen()) {
            dpadUp = false; dpadDown = false; dpadLeft = false; dpadRight = false; syncLeftPad()
            if (down && !repeat) {
                when (code) {
                    KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_ESCAPE -> { hud?.back(); return true }
                    KeyEvent.KEYCODE_BUTTON_L1 -> if (hud?.menu?.isOpen == true) { hud.menu.cycleSubTab(-1); return true }
                    KeyEvent.KEYCODE_BUTTON_R1 -> if (hud?.menu?.isOpen == true) { hud.menu.cycleSubTab(1); return true }
                    KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER -> {
                        val focused = currentFocus
                        if (focused != null && focused.isShown) {
                            focused.performClick()
                            return true
                        }
                    }
                }
            }
            return super.dispatchKeyEvent(ev)
        }

        // In the 3D room: D-pad / WASD drives movement (walk) or pan (orbit)
        when (code) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> { dpadUp = down; syncLeftPad(); return true }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> { dpadDown = down; syncLeftPad(); return true }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> { dpadLeft = down; syncLeftPad(); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> { dpadRight = down; syncLeftPad(); return true }
        }

        if (down && !repeat) {
            when (code) {
                KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_SPACE -> {
                    app.post { app.tapCenter() }
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_B -> {
                    if (app.isBuilt && app.library.heldGame != null) {
                        app.post { app.putBackHeld() }
                        return true
                    }
                    if (hud?.back() == true) return true
                    hud?.menu?.open(null)
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_X -> {
                    app.post {
                        if (app.player.mode == "chair") app.pullChair()
                        else { app.toggleFanLight(); app.sfx("lightChain"); app.settings.changed() }
                    }
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_Y -> {
                    app.post { app.toggleAudio() }
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_L1 -> {
                    hud?.cyclePosture(-1)
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_R1 -> {
                    hud?.cyclePosture(1)
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_THUMBL -> {
                    app.post { app.setPlayerMode(if (app.player.mode == "walk") "orbit" else "walk") }
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_THUMBR -> {
                    app.post { app.setPlayerMode(if (app.player.mode == "chair") "bed" else "chair") }
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(ev)
    }
}
