package com.ps1bedroom.room

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout

/**
 * Notches, hole-punch cameras and curved edges (Menu → Display → Screen cutout). v0.31: the whole picture either runs
 * under the cutout or keeps clear of it with a black bar (always, or only with the phone sideways, where a side camera
 * covers part of the PC screen). The buttons already sit clear of the camera by themselves, so nothing else is moved.
 * What this phone has is detected and described.
 */
class Cutout(private val act: Activity, private val root: FrameLayout, @Suppress("unused") private val glView: View) {
    companion object {
        val MODES = linkedMapOf(
            "full" to "Full screen",
            "landscape" to "Clear when sideways",
            "clear" to "Always clear"
        )
        /** (kept for views that map GL coordinates; nothing is shifted per view any more) */
        const val NO_INSET = "noinset"
    }
    private val prefs = act.getSharedPreferences("ps1bedroom", Context.MODE_PRIVATE)
    var mode = prefs.getString("cutoutMode", "full")!!.let { if (MODES.containsKey(it)) it else "full" }; private set
    /** e.g. "Hole-punch camera at the top centre · curved side edges". */
    var info = "Checking your screen..."; private set
    val safe = Rect()
    /** Safe insets that HUD / Menu views should respect when root itself is unpadded (e.g. in "full" mode). */
    val uiSafe = Rect()
    var onSafeChanged: ((Rect) -> Unit)? = null

    init {
        apply(mode, false)
        root.setOnApplyWindowInsetsListener { _, ins -> readInsets(ins); ins }
        root.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
            if (r - l != or - ol || b - t != ob - ot) {
                root.post {
                    root.rootWindowInsets?.let { readInsets(it) } ?: layoutForMode()
                }
            }
        }
    }

    fun apply(m: String, save: Boolean = true) {
        mode = if (MODES.containsKey(m)) m else "full"
        if (save) prefs.edit().putString("cutoutMode", mode).apply()
        // The window always runs edge to edge; the modes do their own padding so they behave the same on every phone
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = act.window.attributes
            val want = if (Build.VERSION.SDK_INT >= 30) WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            if (lp.layoutInDisplayCutoutMode != want) { lp.layoutInDisplayCutoutMode = want; act.window.attributes = lp }
        }
        if (Build.VERSION.SDK_INT >= 30) act.window.setDecorFitsSystemWindows(false)
        root.requestApplyInsets()
        root.rootWindowInsets?.let { readInsets(it) } ?: layoutForMode()
    }

    private fun readInsets(ins: WindowInsets) {
        safe.setEmpty()
        val parts = ArrayList<String>()
        val dm = act.resources.displayMetrics
        val w = root.width.takeIf { it > 0 } ?: dm.widthPixels
        val h = root.height.takeIf { it > 0 } ?: dm.heightPixels
        if (Build.VERSION.SDK_INT >= 28) {
            val c = ins.displayCutout
            if (c != null) {
                safe.set(c.safeInsetLeft, c.safeInsetTop, c.safeInsetRight, c.safeInsetBottom)
                for (r in c.boundingRects) {
                    val kind = when {
                        r.width() > w * 0.25f -> "Notch"
                        r.width() > w * 0.12f -> "Small notch"
                        else -> "Hole-punch camera"
                    }
                    val cx = r.centerX().toFloat() / maxOf(1, w)
                    val where = when {
                        r.top <= 4 -> if (cx < 0.3f) "top left" else if (cx > 0.7f) "top right" else "top centre"
                        r.bottom >= h - 4 -> "bottom"
                        r.left <= 4 -> "left edge"
                        else -> "right edge"
                    }
                    parts += "$kind at the $where"
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 29) {
            val wf = ins.displayCutout?.waterfallInsets
            if (wf != null && (wf.left > 0 || wf.right > 0 || wf.top > 0 || wf.bottom > 0)) {
                parts += "curved screen edges"
                safe.left = maxOf(safe.left, wf.left); safe.right = maxOf(safe.right, wf.right); safe.top = maxOf(safe.top, wf.top); safe.bottom = maxOf(safe.bottom, wf.bottom)
            }
        }
        info = if (parts.isEmpty()) "No notch or hole-punch on this screen: nothing to do." else parts.joinToString(" · ").replaceFirstChar { it.uppercase() }
        layoutForMode()
    }

    /** Keep clear: the whole view moves off the cutout and leaves a black bar; in Full mode, only UI overlays stay clear of the cutout. */
    private fun layoutForMode() {
        val dm = act.resources.displayMetrics
        val w = root.width.takeIf { it > 0 } ?: dm.widthPixels
        val h = root.height.takeIf { it > 0 } ?: dm.heightPixels
        val sideways = w > h
        val clear = mode == "clear" || (mode == "landscape" && sideways)
        if (clear) {
            if (root.paddingLeft != safe.left || root.paddingTop != safe.top || root.paddingRight != safe.right || root.paddingBottom != safe.bottom) {
                root.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            }
            uiSafe.set(0, 0, 0, 0)
        } else {
            if (root.paddingLeft or root.paddingTop or root.paddingRight or root.paddingBottom != 0) {
                root.setPadding(0, 0, 0, 0)
            }
            uiSafe.set(safe)
        }
        root.setBackgroundColor(android.graphics.Color.BLACK)
        onSafeChanged?.invoke(uiSafe)
    }
}
