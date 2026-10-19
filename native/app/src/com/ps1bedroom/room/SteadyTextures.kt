package com.ps1bedroom.room

import com.ps1bedroom.room.engine.Mesh
import com.ps1bedroom.room.engine.Scene
import com.ps1bedroom.room.engine.Texture
import java.util.IdentityHashMap

/**
 * HTML 17436-17477 (1.4): distant texture flicker. Every texture is nearest-filtered with no mipmaps, so far-away
 * walls, carpet and art shimmer as the camera moves. Textures that never redraw get mip levels (still nearest-sampled,
 * so they stay crunchy up close); one that later starts redrawing often goes back to plain sampling, so it never
 * pays for mip generation on every upload. GL thread, from the frame loop.
 */
class SteadyTextures(private val scene: Scene) {
    private class Seen(var v: Int, var changes: Double, var mip: Boolean, var ours: Boolean = false)
    private val seen = IdentityHashMap<Texture, Seen>()
    private var startMs = 0L
    private var nextMs = 0L
    private var stage = 0
    private fun collect(out: MutableSet<Texture>) {
        scene.traverse { o ->
            if (o is Mesh) for (m in o.materials) {
                for (t in arrayOf(m.map, m.emissiveMap)) if (t != null && t.external == null && t.bitmap.width >= 16) out += t
            }
        }
    }
    private val scratch = java.util.Collections.newSetFromMap(IdentityHashMap<Texture, Boolean>())
    private fun sample() {
        scratch.clear(); collect(scratch)
        for (t in scratch) {
            val s = seen[t]
            if (s == null) { seen[t] = Seen(t.version, 0.0, t.mipmaps); continue }
            s.changes = s.changes * 0.5 + (if (t.version != s.v) 1 else 0)
            s.v = t.version
        }
    }
    private fun setMip(t: Texture, on: Boolean) { t.mipmaps = on; t.needsUpdate() }
    /** Samples at 6 s and 10 s, then gives the steady ones mipmaps at 14 s, and keeps watching every 4 s. */
    fun tick() {
        val now = System.currentTimeMillis()
        if (startMs == 0L) { startMs = now; nextMs = now + 6000; return }
        if (now < nextMs) return
        nextMs = now + 4000
        sample()
        when (stage) {
            0, 1 -> stage++
            2 -> {
                stage = 3
                for ((t, s) in seen) if (s.changes == 0.0 && !s.mip && t.nearest && !t.linearMin) { setMip(t, true); s.mip = true; s.ours = true; s.v = t.version }
            }
            else -> for ((t, s) in seen) if (s.ours && s.mip && s.changes > 1.2) { setMip(t, false); s.mip = false; s.v = t.version }
        }
    }
}
