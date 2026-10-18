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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view = object : GLSurfaceView(this) {
            override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { super.onSizeChanged(w, h, ow, oh); app.viewHeight = h.toFloat() }
        }
        view.setEGLContextClientVersion(3)
        view.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        view.preserveEGLContextOnPause = true
        view.setRenderer(app)
        view.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        view.setOnTouchListener { _, e -> onTouch(e); true }
        setContentView(view)
        hideSystemBars()
    }

    override fun onResume() { super.onResume(); view.onResume(); hideSystemBars() }
    override fun onPause() { view.onPause(); super.onPause() }
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
    private fun onTouch(e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastX = e.x; lastY = e.y }
            MotionEvent.ACTION_POINTER_DOWN -> if (e.pointerCount >= 2) {
                lastDist = hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))
                lastMidX = (e.getX(0) + e.getX(1)) / 2; lastMidY = (e.getY(0) + e.getY(1)) / 2
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // keep orbiting smoothly with the finger that stays down
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
