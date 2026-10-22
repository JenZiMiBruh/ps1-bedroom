package com.ps1bedroom.room

import android.app.Activity
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
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
        app.onWindowShot = { clear, blinds, weather, place -> Thread { WindowWidget.saveInfo(this, weather, place); WindowWidget.saveShots(this, clear, blinds); clear.recycle(); blinds.recycle() }.start() }
        app.keepScreenOn = { on -> runOnUiThread { if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } }
        UiTheme.load(this)
        app.hud = Hud(this, root, app).also { it.attachMenu() }
        setContentView(root)
        pacer = FramePacer(this, view, app)
        app.pacer = pacer
        app.onAudioState = { on -> if (on) runOnUiThread { askNotifications() } }
        // (straight away: the GL thread, which runs posted work, is paused while the app is in the background)
        hideSystemBars()
        handleShare(intent)
        handleShortcut(intent)
    }
    /** Long-press app shortcuts (res/xml/shortcuts.xml) and the Quick Settings tile. */
    private fun handleShortcut(i: android.content.Intent?) {
        val a = i?.action ?: return
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
        app.windowWidgets = WindowWidget.hasWidgets(this); if (app.windowWidgets) app.windowShotNow = true
    }
    private fun askNotifications() { if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7) }
    override fun onDestroy() { MediaService.onControl = null; MediaService.onSeek = null; MediaService.stop(this); NowPlayingWidget.update(this, null); app.sound.shutdown(); super.onDestroy() }
    @Deprecated("Activity back handling")
    override fun onBackPressed() { if (app.hud?.back() != true) @Suppress("DEPRECATION") super.onBackPressed() }
    override fun onPause() {
        // In picture-in-picture the activity is paused but still on screen: keep drawing
        if (android.os.Build.VERSION.SDK_INT >= 24 && isInPictureInPictureMode) { super.onPause(); return }
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
}
