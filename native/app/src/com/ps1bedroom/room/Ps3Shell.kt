package com.ps1bedroom.room

import com.ps1bedroom.room.engine.Geo
import com.ps1bedroom.room.engine.Geometry
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sign

/**
 * The PS3 Slim's body as one curved shell, shaped to the photos of the real console: a dome highest a little behind
 * the middle, flattish on the crown, rolling down hard over the front edge and more gently to the sides and back,
 * rounded corners in plan, and the bottom edge tucked in. Built from a finely divided box whose every column is
 * pulled to the dome's height, so the top and the sides curve together. (Tuned in a three.js preview first.)
 */
object Ps3Shell {
    const val W = 0.58f; const val H = 0.105f; const val D = 0.58f
    private const val FRONT = 0.48; private const val FEXP = 3.0; private const val SIDE = 0.14; private const val SIDE4 = 0.2
    private const val BACK = 0.1; private const val PEAK = -0.2; private const val RC = 0.16; private const val TUCK = 0.012; private const val MIN_H = 0.42

    /** How far below the peak the top is at normalised (nx, nz), as a fraction of the height. */
    private fun drop(nx: Double, nz: Double): Double {
        val f = if (nz > PEAK) FRONT * ((nz - PEAK) / (1 - PEAK)).pow(FEXP) else BACK * ((PEAK - nz) / (1 + PEAK)).pow(2)
        return SIDE * nx * nx + SIDE4 * nx.pow(4) + f
    }
    private fun heightAt(nx: Double, nz: Double) = max(MIN_H, 1 - drop(nx, nz)) * H

    /** Height of the top above the shell's bottom at (x, z) in the console's space. */
    fun topY(x: Float, z: Float) = heightAt(x / (W / 2.0), z / (D / 2.0)).toFloat()
    fun slopeZ(x: Float, z: Float) = (topY(x, z + 0.01f) - topY(x, z - 0.01f)) / 0.02f
    fun slopeX(x: Float, z: Float) = (topY(x + 0.01f, z) - topY(x - 0.01f, z)) / 0.02f

    fun geometry(): Geometry {
        val g = Geo.box(W, H, D, 24, 8, 24)
        val p = g.positions
        val c = 1 - RC
        for (i in 0 until g.vertexCount) {
            var nx = p[i * 3] / (W / 2.0); val ny = p[i * 3 + 1] / (H / 2.0); var nz = p[i * 3 + 2] / (D / 2.0)
            // rounded corners in plan
            val dx = abs(nx) - c; val dz = abs(nz) - c
            if (dx > 0 && dz > 0) { val l = hypot(dx, dz); if (l > RC) { nx = sign(nx) * (c + dx * RC / l); nz = sign(nz) * (c + dz * RC / l) } }
            // every column rises to the dome's height there
            val y = (ny + 1) / 2 * heightAt(nx, nz)
            // the lowest part tucks in
            val t = max(0.0, (0.18 * H - y) / (0.18 * H))
            val s = 1 - TUCK * t * t / (W / 2.0)
            p[i * 3] = (nx * W / 2 * s).toFloat(); p[i * 3 + 1] = y.toFloat(); p[i * 3 + 2] = (nz * D / 2 * s).toFloat()
        }
        g.computeVertexNormals()
        return g
    }
}
