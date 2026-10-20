package com.ps1bedroom.room

import com.ps1bedroom.room.engine.Geo
import com.ps1bedroom.room.engine.Geometry
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sign

/**
 * The original PS3 (CECHA, 325 x 98 x 274 mm -> 0.650 x 0.164 x 0.548) as one curved glossy shell, shaped to the
 * photo of the real console: a dome that curves strongly from side to side and stays nearly flat front to back,
 * rolling over only near the front and back edges; rounded corners in plan; the bottom edge tucked in. Built from a
 * finely divided box whose every column is pulled to the dome's height. (Tuned in a three.js preview against the
 * photo; the preview uses these exact numbers.)
 */
object Ps3Shell {
    const val W = 0.65f; const val H = 0.164f; const val D = 0.548f
    private const val FRONT = 0.14; private const val FEXP = 4.0; private const val BACK = 0.1; private const val BEXP = 4.0; private const val PEAK = 0.0
    private const val SIDE = 0.25; private const val SIDE4 = 0.13
    private const val RC = 0.1; private const val TUCK = 0.008; private const val MIN_H = 0.45

    /** How far below the peak the top is at normalised (nx, nz), as a fraction of the height. */
    private fun drop(nx: Double, nz: Double): Double {
        val f = if (nz > PEAK) FRONT * ((nz - PEAK) / (1 - PEAK)).pow(FEXP) else BACK * ((PEAK - nz) / (1 + PEAK)).pow(BEXP)
        return SIDE * nx * nx + SIDE4 * nx.pow(4) + f
    }
    private fun heightAt(nx: Double, nz: Double) = max(MIN_H, 1 - drop(nx, nz)) * H

    /** Height of the top above the shell's bottom at (x, z) in the console's space. */
    fun topY(x: Float, z: Float) = heightAt(x / (W / 2.0), z / (D / 2.0)).toFloat()
    fun slopeX(x: Float, z: Float) = (topY(x + 0.01f, z) - topY(x - 0.01f, z)) / 0.02f

    fun geometry(): Geometry {
        val g = Geo.box(W, H, D, 24, 8, 24)
        val p = g.positions
        val c = 1 - RC
        for (i in 0 until g.vertexCount) {
            var nx = p[i * 3] / (W / 2.0); val ny = p[i * 3 + 1] / (H / 2.0); var nz = p[i * 3 + 2] / (D / 2.0)
            val dx = abs(nx) - c; val dz = abs(nz) - c
            if (dx > 0 && dz > 0) { val l = hypot(dx, dz); if (l > RC) { nx = sign(nx) * (c + dx * RC / l); nz = sign(nz) * (c + dz * RC / l) } }
            val y = (ny + 1) / 2 * heightAt(nx, nz)
            val t = max(0.0, (0.18 * H - y) / (0.18 * H))
            val s = 1 - TUCK * t * t / (W / 2.0)
            p[i * 3] = (nx * W / 2 * s).toFloat(); p[i * 3 + 1] = y.toFloat(); p[i * 3 + 2] = (nz * D / 2 * s).toFloat()
        }
        g.computeVertexNormals()
        return g
    }
}
