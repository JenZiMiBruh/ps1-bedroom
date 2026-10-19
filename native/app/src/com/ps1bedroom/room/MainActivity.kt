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
        app.pickMusic = { folder -> runOnUiThread { pickMusic(folder) } }
        app.onNowPlaying = { title, artist, _ -> runOnUiThread { MediaService.nowPlaying = title?.let { if (artist.isNullOrEmpty()) it else "$it · $artist" }; if (app.sound.playing) MediaService.start(this) } }
        MediaService.onControl = { cmd -> app.post { app.myMusic.mediaControl(cmd) } }
        app.hud = Hud(this, root, app).also { it.attachMenu() }
        setContentView(root)
        pacer = FramePacer(this, view, app)
        app.pacer = pacer
        app.onAudioState = { on -> runOnUiThread { if (on) { askNotifications(); MediaService.start(this) } else MediaService.stop(this) } }
        // (straight away: the GL thread, which runs posted work, is paused while the app is in the background)
        MediaService.onPause = { if (app.sound.playing) app.toggleAudio() }
        hideSystemBars()
    }

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

    override fun onResume() { super.onResume(); view.onResume(); pacer.interacted(); pacer.start(); hideSystemBars() }
    private fun askNotifications() { if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7) }
    override fun onDestroy() { MediaService.stop(this); app.sound.shutdown(); super.onDestroy() }
    @Deprecated("Activity back handling")
    override fun onBackPressed() { if (app.hud?.back() != true) @Suppress("DEPRECATION") super.onBackPressed() }
    override fun onPause() { pacer.stop(); view.onPause(); super.onPause() }
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
