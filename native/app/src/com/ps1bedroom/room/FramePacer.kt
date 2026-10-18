package com.ps1bedroom.room

import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.view.Choreographer

/**
 * Decides when to draw. The GL view only renders on request, and this asks at most:
 *  - the frame-rate cap (Balanced = 60, Battery saver = 30, or the user's 30 / 60 / 120 choice)
 *  - 30 fps after 12 s untouched and 20 fps after 2 minutes (the HTML's eco idle; 20 / 15 on Battery saver)
 *  - less when Android says the phone is getting warm (the native extra: real thermal status)
 * Nothing at all while the app is in the background.
 */
class FramePacer(context: Context, private val view: GLSurfaceView, private val app: App) : Choreographer.FrameCallback {
    @Volatile var baseCap = 60
    @Volatile var ecoIdle = true
    @Volatile var saver = false
    @Volatile var lastInteractMs = SystemClock.uptimeMillis()
    private var lastFrameMs = 0L
    private var running = false
    private val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    @Volatile private var thermal = 0

    init {
        if (Build.VERSION.SDK_INT >= 29) {
            thermal = power.currentThermalStatus
            power.addThermalStatusListener { s -> thermal = s; app.onThermal(s) }
            app.onThermal(thermal)
        }
    }

    fun interacted() { lastInteractMs = SystemClock.uptimeMillis() }

    fun currentCap(now: Long): Int {
        var cap = baseCap
        if (ecoIdle) {
            val idle = now - lastInteractMs
            if (idle > 120_000) cap = minOf(cap, if (saver) 15 else 20)
            else if (idle > 12_000) cap = minOf(cap, if (saver) 20 else 30)
        }
        // Warm phone: back off before Android has to throttle us
        if (Build.VERSION.SDK_INT >= 29) {
            when {
                thermal >= PowerManager.THERMAL_STATUS_SEVERE -> cap = minOf(cap, 15)
                thermal >= PowerManager.THERMAL_STATUS_MODERATE -> cap = minOf(cap, 24)
                thermal >= PowerManager.THERMAL_STATUS_LIGHT -> cap = minOf(cap, 30)
            }
        }
        return cap
    }

    fun start() { if (!running) { running = true; Choreographer.getInstance().postFrameCallback(this) } }
    fun stop() { running = false; Choreographer.getInstance().removeFrameCallback(this) }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        val now = SystemClock.uptimeMillis()
        val cap = currentCap(now)
        // same test as the HTML: draw when at least one frame interval (minus 2 ms of slack) has passed
        if (now - lastFrameMs >= 1000 / cap - 2) {
            lastFrameMs = now
            view.requestRender()
        }
        Choreographer.getInstance().postFrameCallback(this)
    }
}
