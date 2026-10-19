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
        app.hud = Hud(this, root, app).also { it.attachMenu() }
        setContentView(root)
        pacer = FramePacer(this, view, app)
        app.pacer = pacer
        hideSystemBars()
    }

    override fun onResume() { super.onResume(); view.onResume(); pacer.interacted(); pacer.start(); hideSystemBars() }
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
