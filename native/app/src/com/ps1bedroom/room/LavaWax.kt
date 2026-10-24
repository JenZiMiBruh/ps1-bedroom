package com.ps1bedroom.room

import com.ps1bedroom.room.engine.Geometry
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The lava lamp's wax as one real, gooey surface (v0.54): a handful of metaballs plus a pool of wax at the bottom,
 * turned into a mesh every few frames (marching tetrahedra on a small grid inside the glass). Warm wax swells off the
 * pool, stretches as it rises, rounds off and cools at the top, then sinks back and melts into the pool, and blobs
 * that pass each other merge and pinch apart like the real thing. Coordinates are the lamp group's (glass: y 0.26-0.80).
 */
class LavaWax {
    private val NX = 14; private val NY = 36; private val NZ = 14
    private val X0 = -0.115f; private val X1 = 0.115f
    private val Y0 = 0.262f; private val Y1 = 0.79f
    private val Z0 = -0.115f; private val Z1 = 0.115f
    private val dx = (X1 - X0) / NX; private val dy = (Y1 - Y0) / NY; private val dz = (Z1 - Z0) / NZ
    private val MAX_TRIS = 9000
    val geometry = Geometry(FloatArray(MAX_TRIS * 9), FloatArray(MAX_TRIS * 9), FloatArray(MAX_TRIS * 6), null).apply {
        drawCount = 0
        boundingCenter.set(0f, 0.53f, 0f); boundingRadius = 0.32f
    }
    private val field = FloatArray((NX + 1) * (NY + 1) * (NZ + 1))

    /** One blob: where it is in its rise-and-fall, how fast its wax heats, how big it is. */
    private class Blob(val r: Float, val period: Float, var phase: Float, val wobble: Float, val ox: Float, val oz: Float) {
        var x = 0f; var y = 0f; var z = 0f; var stretch = 1f; var rr = r
    }
    private val blobs = listOf(
        Blob(0.040f, 23f, 0.00f, 0.9f, 0.012f, -0.006f),
        Blob(0.034f, 29f, 0.37f, 1.3f, -0.016f, 0.010f),
        Blob(0.030f, 19f, 0.62f, 1.7f, 0.006f, 0.016f),
        Blob(0.026f, 34f, 0.81f, 1.1f, -0.010f, -0.014f),
        Blob(0.022f, 26f, 0.18f, 2.1f, 0.018f, 0.004f)
    )
    private var poolH = 0.05f

    /** Inner radius of the tapered glass at height y (0.112 at the bottom to 0.056 at the top, less the glass). */
    private fun glassR(y: Float) = 0.112f + (0.056f - 0.112f) * ((y - 0.26f) / 0.54f).coerceIn(0f, 1f) - 0.012f

    /** Moves the blobs. warmth 0 = cold (everything settled in the pool) .. 1 = fully hot. */
    fun simulate(t: Float, warmth: Float) {
        for (b in blobs) {
            // A real lamp: a slow rise, a pause at the top, a quicker sink, a long rest melting in the pool
            val u = ((t / b.period + b.phase) % 1f + 1f) % 1f
            val h = when {
                u < 0.15f -> 0f                                                   // heating in the pool
                u < 0.55f -> smooth((u - 0.15f) / 0.40f)                          // rising
                u < 0.65f -> 1f                                                   // cooling at the top
                else -> 1f - smooth((u - 0.65f) / 0.35f)                          // sinking
            }
            val rising = u in 0.15f..0.55f
            val top = 0.70f - b.r * 0.6f
            val bottom = Y0 + poolH * 0.7f
            val hot = bottom + (top - bottom) * h
            b.y = bottom + (hot - bottom) * warmth
            val sway = warmth * (0.35f + 0.65f * h)
            b.x = b.ox + sin(t * 0.21f * b.wobble + b.phase * 6f) * 0.018f * sway
            b.z = b.oz + cos(t * 0.17f * b.wobble + b.phase * 4f) * 0.016f * sway
            // stretched tall while it pulls up off the pool, squashed a little as it lands
            val pull = if (rising) sin(((u - 0.15f) / 0.40f) * Math.PI.toFloat()) else 0f
            b.stretch = 1f + (0.55f * pull - 0.12f * (if (!rising && u > 0.9f) 1f else 0f)) * warmth
            b.rr = b.r * (0.75f + 0.25f * warmth) * (1f + 0.06f * sin(t * 1.3f * b.wobble + b.phase * 9f) * warmth)
        }
        poolH = 0.034f + 0.03f * (1f - warmth)
    }

    private fun smooth(x: Float) = x * x * (3 - 2 * x)

    /** The wax field: > 0 inside. Metaballs (stretched tall) + the pool on the bottom, all kept inside the glass. */
    private fun f(x: Float, y: Float, z: Float): Float {
        var s = 0f
        for (b in blobs) {
            val ax = x - b.x; val ay = (y - b.y) / b.stretch; val az = z - b.z
            val d2 = ax * ax + ay * ay + az * az + 1e-6f
            s += b.rr * b.rr / d2
        }
        // the pool: a soft layer over the bottom, slightly domed in the middle
        val r2 = x * x + z * z
        val surface = Y0 + poolH + 0.012f * (1f - min(1f, r2 / 0.006f))
        val dp = y - surface
        s += if (dp <= 0f) 1f - dp * 20f else 0.018f / (dp + 0.018f)
        // the glass: the wax can't pass through it
        val gr = glassR(y)
        val r = sqrt(r2)
        if (r > gr - 0.004f) s *= max(0f, 1f - (r - gr + 0.004f) / 0.02f)
        if (y < Y0 + 0.004f) s = 0f   // closes the pool off at the bottom (hidden in the base)
        if (y > Y1 - 0.02f) s *= max(0f, (Y1 - y) / 0.02f)
        return s - 1f
    }

    // Six tetrahedra per cube around the 0-7 diagonal (corner index = x + 2y + 4z)
    private val TET = intArrayOf(0, 7, 1, 3, 0, 7, 3, 2, 0, 7, 2, 6, 0, 7, 6, 4, 0, 7, 4, 5, 0, 7, 5, 1)
    private val cx = FloatArray(8); private val cy = FloatArray(8); private val cz = FloatArray(8); private val cv = FloatArray(8)
    private var nTri = 0

    /** Rebuilds the mesh from the current blobs. */
    fun mesh() {
        val sx = NX + 1; val sxy = (NX + 1) * (NY + 1)
        for (k in 0..NZ) for (j in 0..NY) for (i in 0..NX) field[i + j * sx + k * sxy] = f(X0 + i * dx, Y0 + j * dy, Z0 + k * dz)
        nTri = 0
        for (k in 0 until NZ) for (j in 0 until NY) for (i in 0 until NX) {
            var anyIn = false; var anyOut = false
            for (c in 0..7) {
                val ci = i + (c and 1); val cj = j + ((c shr 1) and 1); val ck = k + ((c shr 2) and 1)
                val v = field[ci + cj * sx + ck * sxy]
                cv[c] = v; cx[c] = X0 + ci * dx; cy[c] = Y0 + cj * dy; cz[c] = Z0 + ck * dz
                if (v > 0f) anyIn = true else anyOut = true
            }
            if (!anyIn || !anyOut) continue
            var q = 0
            while (q < 24) { tetra(TET[q], TET[q + 1], TET[q + 2], TET[q + 3]); q += 4 }
            if (nTri >= MAX_TRIS - 2) break
        }
        geometry.drawCount = nTri * 3
        geometry.version++
    }

    private val ein = IntArray(4); private val eout = IntArray(4)
    private fun tetra(a: Int, b: Int, c: Int, d: Int) {
        var ni = 0; var no = 0
        if (cv[a] > 0f) ein[ni++] = a else eout[no++] = a
        if (cv[b] > 0f) ein[ni++] = b else eout[no++] = b
        if (cv[c] > 0f) ein[ni++] = c else eout[no++] = c
        if (cv[d] > 0f) ein[ni++] = d else eout[no++] = d
        when (ni) {
            1 -> tri(edge(ein[0], eout[0]), edge(ein[0], eout[1]), edge(ein[0], eout[2]))
            3 -> tri(edge(eout[0], ein[0]), edge(eout[0], ein[1]), edge(eout[0], ein[2]))
            2 -> {
                val p0 = edge(ein[0], eout[0]); val p1 = edge(ein[0], eout[1]); val p2 = edge(ein[1], eout[1]); val p3 = edge(ein[1], eout[0])
                tri(p0, p1, p2); tri(p0, p2, p3)
            }
        }
    }
    /** The point where the surface crosses the edge between two corners, packed into a scratch slot. */
    private val ptX = FloatArray(4); private val ptY = FloatArray(4); private val ptZ = FloatArray(4); private var ptN = 0
    private fun edge(p: Int, q: Int): Int {
        val t = (cv[p] / (cv[p] - cv[q])).coerceIn(0f, 1f)
        val s = ptN; ptN = (ptN + 1) and 3
        ptX[s] = cx[p] + (cx[q] - cx[p]) * t; ptY[s] = cy[p] + (cy[q] - cy[p]) * t; ptZ[s] = cz[p] + (cz[q] - cz[p]) * t
        return s
    }
    private fun tri(a: Int, b: Int, c: Int) {
        if (nTri >= MAX_TRIS) return
        val pos = geometry.positions; val nor = geometry.normals
        var o = nTri * 9
        // outward = down the field's gradient; wind the triangle to face that way
        val ux = ptX[b] - ptX[a]; val uy = ptY[b] - ptY[a]; val uz = ptZ[b] - ptZ[a]
        val vx = ptX[c] - ptX[a]; val vy = ptY[c] - ptY[a]; val vz = ptZ[c] - ptZ[a]
        val fnx = uy * vz - uz * vy; val fny = uz * vx - ux * vz; val fnz = ux * vy - uy * vx
        val mx = (ptX[a] + ptX[b] + ptX[c]) / 3; val my = (ptY[a] + ptY[b] + ptY[c]) / 3; val mz = (ptZ[a] + ptZ[b] + ptZ[c]) / 3
        val e = 0.004f
        val gx = f(mx + e, my, mz) - f(mx - e, my, mz); val gy = f(mx, my + e, mz) - f(mx, my - e, mz); val gz = f(mx, my, mz + e) - f(mx, my, mz - e)
        val flip = fnx * -gx + fny * -gy + fnz * -gz < 0f
        for (n in 0..2) {
            val p = when (n) { 0 -> a; 1 -> if (flip) c else b; else -> if (flip) b else c }
            pos[o] = ptX[p]; pos[o + 1] = ptY[p]; pos[o + 2] = ptZ[p]
            // smooth shading: the field's gradient at the vertex
            val px = ptX[p]; val py = ptY[p]; val pz = ptZ[p]
            var nx = f(px - e, py, pz) - f(px + e, py, pz); var ny = f(px, py - e, pz) - f(px, py + e, pz); var nz = f(px, py, pz - e) - f(px, py, pz + e)
            val l = sqrt(nx * nx + ny * ny + nz * nz).takeIf { it > 1e-6f } ?: 1f
            nx /= l; ny /= l; nz /= l
            nor[o] = nx; nor[o + 1] = ny; nor[o + 2] = nz
            o += 3
        }
        nTri++
    }
}
