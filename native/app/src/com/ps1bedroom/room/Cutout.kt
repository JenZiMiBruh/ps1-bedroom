package com.ps1bedroom.room

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import java.util.WeakHashMap

/**
 * Notches, hole-punch cameras and curved edges (Menu → Display → Screen cutout). The room can draw edge to edge
 * under the cutout while the buttons and menus are kept clear of it, fill everything, or keep clear entirely.
 * What this phone has is detected and described, and the default picks itself from it.
 */
class Cutout(private val act: Activity, private val root: FrameLayout, private val glView: View) {
    companion object {
        val MODES = linkedMapOf(
            "fill" to "Fill the screen, buttons kept clear",
            "fillAll" to "Fill everything (buttons too)",
            "clear" to "Keep clear (black bar)"
        )
        /** Views that map GL coordinates (YouTube on the TV) must never be shifted. */
        const val NO_INSET = "noinset"
    }
    private val prefs = act.getSharedPreferences("ps1bedroom", Context.MODE_PRIVATE)
    var mode = prefs.getString("cutoutMode", "fill")!!; private set
    /** e.g. "Hole-punch camera at the top centre · curved side edges". */
    var info = "Checking your screen..."; private set
    private val safe = Rect()
    private val orig = WeakHashMap<View, IntArray>()
    private val applied = WeakHashMap<View, IntArray>()

    init {
        apply(mode, false)
        root.setOnApplyWindowInsetsListener { _, ins -> readInsets(ins); ins }
        // Views added later (menus, pop-ups) get the same safe margins
        root.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View?, child: View?) { child?.let { inset(it) } }
            override fun onChildViewRemoved(parent: View?, child: View?) { child?.let { orig.remove(it); applied.remove(it) } }
        })
    }

    fun apply(m: String, save: Boolean = true) {
        mode = if (MODES.containsKey(m)) m else "fill"
        if (save) prefs.edit().putString("cutoutMode", mode).apply()
        // v0.30: the window ALWAYS runs edge to edge and the modes do their own layout, so each one visibly differs on
        // every phone (switching the window's own cutout mode at runtime didn't reliably change anything)
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = act.window.attributes
            val want = if (Build.VERSION.SDK_INT >= 30) WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            if (lp.layoutInDisplayCutoutMode != want) { lp.layoutInDisplayCutoutMode = want; act.window.attributes = lp }
        }
        if (Build.VERSION.SDK_INT >= 30) act.window.setDecorFitsSystemWindows(false)
        root.requestApplyInsets()
        layoutForMode()
    }

    private fun readInsets(ins: WindowInsets) {
        safe.setEmpty()
        val parts = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= 28) {
            val c = ins.displayCutout
            if (c != null) {
                safe.set(c.safeInsetLeft, c.safeInsetTop, c.safeInsetRight, c.safeInsetBottom)
                val w = root.width.takeIf { it > 0 } ?: act.resources.displayMetrics.widthPixels
                for (r in c.boundingRects) {
                    val kind = when {
                        r.width() > w * 0.25f -> "Notch"
                        r.width() > w * 0.12f -> "Small notch"
                        else -> "Hole-punch camera"
                    }
                    val cx = r.centerX().toFloat() / w
                    val where = if (r.top <= 2) (if (cx < 0.3f) "top left" else if (cx > 0.7f) "top right" else "top centre") else if (r.bottom >= root.height - 2) "bottom" else if (r.left <= 2) "left edge" else "right edge"
                    parts += "$kind at the $where"
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 29) {
            val wf = ins.displayCutout?.waterfallInsets
            if (wf != null && (wf.left > 0 || wf.right > 0 || wf.top > 0)) {
                parts += "curved screen edges"
                safe.left = maxOf(safe.left, wf.left); safe.right = maxOf(safe.right, wf.right); safe.top = maxOf(safe.top, wf.top); safe.bottom = maxOf(safe.bottom, wf.bottom)
            }
        }
        if (Build.VERSION.SDK_INT >= 31) {
            // Rounded display corners: keep corner buttons a little way in
            val rc = ins.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)
            if (rc != null && rc.radius > 0) { parts += "rounded corners"; val k = (rc.radius * 0.3f).toInt(); safe.top = maxOf(safe.top, k); safe.bottom = maxOf(safe.bottom, k) }
        }
        info = if (parts.isEmpty()) "No notch or hole-punch on this screen: nothing to do." else parts.joinToString(" · ").replaceFirstChar { it.uppercase() }
        layoutForMode()
    }

    /** Keep clear: the whole view (the room too) moves off the cutout and leaves a black bar. Fill: only the UI moves. */
    private fun layoutForMode() {
        if (mode == "clear") root.setPadding(safe.left, safe.top, safe.right, safe.bottom) else root.setPadding(0, 0, 0, 0)
        root.setBackgroundColor(android.graphics.Color.BLACK)
        insetAll()
    }

    private fun insetAll() { for (i in 0 until root.childCount) inset(root.getChildAt(i)) }
    /** Buttons and panels keep clear of the cutout in "fill" (the room behind them still fills it). */
    private fun inset(v: View) {
        if (v === glView || v.tag == NO_INSET) return
        val lp = v.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val cur = intArrayOf(lp.leftMargin, lp.topMargin, lp.rightMargin, lp.bottomMargin)
        // The view moved itself since we last touched it (e.g. the dock re-laid out for rotation): that's its new base
        val last = applied[v]
        if (last == null || !last.contentEquals(cur)) orig[v] = cur
        val o = orig.getValue(v)
        val s = if (mode == "fill") safe else Rect()
        val l = o[0] + s.left; val t = o[1] + s.top; val r = o[2] + s.right; val b = o[3] + s.bottom
        if (lp.leftMargin != l || lp.topMargin != t || lp.rightMargin != r || lp.bottomMargin != b) {
            lp.setMargins(l, t, r, b); v.layoutParams = lp
        }
        applied[v] = intArrayOf(l, t, r, b)
    }
}
