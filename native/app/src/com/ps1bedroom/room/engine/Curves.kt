package com.ps1bedroom.room.engine

import kotlin.math.acos
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** three.js r128 CatmullRomCurve3 (centripetal, open) with Curve's arc-length helpers, in double precision like JS. */
class CatmullRomCurve3(private val points: List<DoubleArray>) {
    private class Cubic { var c0 = 0.0; var c1 = 0.0; var c2 = 0.0; var c3 = 0.0
        fun init(x0: Double, x1: Double, t0: Double, t1: Double) { c0 = x0; c1 = t0; c2 = -3 * x0 + 3 * x1 - 2 * t0 - t1; c3 = 2 * x0 - 2 * x1 + t0 + t1 }
        fun initNonuniform(x0: Double, x1: Double, x2: Double, x3: Double, dt0: Double, dt1: Double, dt2: Double) {
            var t1 = (x1 - x0) / dt0 - (x2 - x0) / (dt0 + dt1) + (x2 - x1) / dt1
            var t2 = (x2 - x1) / dt1 - (x3 - x1) / (dt1 + dt2) + (x3 - x2) / dt2
            t1 *= dt1; t2 *= dt1
            init(x1, x2, t1, t2)
        }
        fun calc(t: Double) = c0 + c1 * t + c2 * t * t + c3 * t * t * t
    }
    private val px = Cubic(); private val py = Cubic(); private val pz = Cubic()
    private fun d2(a: DoubleArray, b: DoubleArray): Double { val x = a[0] - b[0]; val y = a[1] - b[1]; val z = a[2] - b[2]; return x * x + y * y + z * z }

    fun getPoint(t: Double): DoubleArray {
        val l = points.size
        val p = (l - 1) * t
        var intPoint = floor(p).toInt()
        var weight = p - intPoint
        if (weight == 0.0 && intPoint == l - 1) { intPoint = l - 2; weight = 1.0 }
        val p0 = if (intPoint > 0) points[(intPoint - 1) % l] else DoubleArray(3) { points[0][it] - points[1][it] + points[0][it] }
        val p1 = points[intPoint % l]; val p2 = points[(intPoint + 1) % l]
        val p3 = if (intPoint + 2 < l) points[(intPoint + 2) % l] else DoubleArray(3) { points[l - 1][it] - points[l - 2][it] + points[l - 1][it] }
        val pow = 0.25
        var dt0 = d2(p0, p1).pow(pow); var dt1 = d2(p1, p2).pow(pow); var dt2 = d2(p2, p3).pow(pow)
        if (dt1 < 1e-4) dt1 = 1.0
        if (dt0 < 1e-4) dt0 = dt1
        if (dt2 < 1e-4) dt2 = dt1
        px.initNonuniform(p0[0], p1[0], p2[0], p3[0], dt0, dt1, dt2)
        py.initNonuniform(p0[1], p1[1], p2[1], p3[1], dt0, dt1, dt2)
        pz.initNonuniform(p0[2], p1[2], p2[2], p3[2], dt0, dt1, dt2)
        return doubleArrayOf(px.calc(weight), py.calc(weight), pz.calc(weight))
    }

    private val lengths: DoubleArray by lazy {
        val divisions = 200
        val out = DoubleArray(divisions + 1)
        var last = getPoint(0.0); var sum = 0.0
        for (p in 1..divisions) { val cur = getPoint(p.toDouble() / divisions); sum += sqrt(d2(cur, last)); out[p] = sum; last = cur }
        out
    }
    fun getUtoTmapping(u: Double): Double {
        val arc = lengths; val il = arc.size
        val target = u * arc[il - 1]
        var low = 0; var high = il - 1; var i: Int
        while (low <= high) {
            i = floor(low + (high - low) / 2.0).toInt()
            val cmp = arc[i] - target
            if (cmp < 0) low = i + 1 else if (cmp > 0) high = i - 1 else { high = i; break }
        }
        i = high
        if (arc[i] == target) return i.toDouble() / (il - 1)
        val before = arc[i]; val after = arc[i + 1]
        return (i + (target - before) / (after - before)) / (il - 1)
    }
    fun getPointAt(u: Double) = getPoint(getUtoTmapping(u))
    fun getTangentAt(u: Double): DoubleArray {
        val t = getUtoTmapping(u)
        val delta = 0.0001
        val t1 = maxOf(0.0, t - delta); val t2 = minOf(1.0, t + delta)
        val a = getPoint(t1); val b = getPoint(t2)
        return norm(doubleArrayOf(b[0] - a[0], b[1] - a[1], b[2] - a[2]))
    }

    companion object {
        fun norm(v: DoubleArray): DoubleArray { val l = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]); return if (l == 0.0) v else doubleArrayOf(v[0] / l, v[1] / l, v[2] / l) }
        fun cross(a: DoubleArray, b: DoubleArray) = doubleArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
        fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
        /** Rotate v about a unit axis by theta (Matrix4.makeRotationAxis applied to a vector). */
        fun rotate(v: DoubleArray, k: DoubleArray, theta: Double): DoubleArray {
            val c = cos(theta); val s = sin(theta); val t = 1 - c
            val x = k[0]; val y = k[1]; val z = k[2]
            return doubleArrayOf(
                (t * x * x + c) * v[0] + (t * x * y - s * z) * v[1] + (t * x * z + s * y) * v[2],
                (t * x * y + s * z) * v[0] + (t * y * y + c) * v[1] + (t * y * z - s * x) * v[2],
                (t * x * z - s * y) * v[0] + (t * y * z + s * x) * v[1] + (t * z * z + c) * v[2])
        }
    }
}

object GeoCurves {
    /** three.js r128 TubeGeometry along a curve (open), with Curve.computeFrenetFrames. */
    fun tube(path: CatmullRomCurve3, tubularSegments: Int = 64, radius: Float = 1f, radialSegments: Int = 8): Geometry {
        val C = CatmullRomCurve3
        // Frenet frames
        val tangents = Array(tubularSegments + 1) { C.norm(path.getTangentAt(it.toDouble() / tubularSegments)) }
        val normals = arrayOfNulls<DoubleArray>(tubularSegments + 1); val binormals = arrayOfNulls<DoubleArray>(tubularSegments + 1)
        var min = Double.MAX_VALUE
        val t0 = tangents[0]
        val tx = abs(t0[0]); val ty = abs(t0[1]); val tz = abs(t0[2])
        var normal = doubleArrayOf(0.0, 0.0, 0.0)
        if (tx <= min) { min = tx; normal = doubleArrayOf(1.0, 0.0, 0.0) }
        if (ty <= min) { min = ty; normal = doubleArrayOf(0.0, 1.0, 0.0) }
        if (tz <= min) { normal = doubleArrayOf(0.0, 0.0, 1.0) }
        val vec0 = C.norm(C.cross(t0, normal))
        normals[0] = C.cross(t0, vec0)
        binormals[0] = C.cross(t0, normals[0]!!)
        for (i in 1..tubularSegments) {
            var n = normals[i - 1]!!.copyOf()
            val vec = C.cross(tangents[i - 1], tangents[i])
            if (sqrt(C.dot(vec, vec)) > 2.220446049250313e-16) {
                val axis = C.norm(vec)
                val theta = acos(C.dot(tangents[i - 1], tangents[i]).coerceIn(-1.0, 1.0))
                n = C.rotate(n, axis, theta)
            }
            normals[i] = n
            binormals[i] = C.cross(tangents[i], n)
        }
        // Vertices, normals, UVs
        val pos = ArrayList<Float>(); val nrm = ArrayList<Float>(); val uv = ArrayList<Float>()
        for (i in 0..tubularSegments) {
            val P = path.getPointAt(i.toDouble() / tubularSegments)
            val N = normals[i]!!; val B = binormals[i]!!
            for (j in 0..radialSegments) {
                val v = j.toDouble() / radialSegments * Math.PI * 2
                val s = sin(v); val c = -cos(v)
                val nn = C.norm(doubleArrayOf(c * N[0] + s * B[0], c * N[1] + s * B[1], c * N[2] + s * B[2]))
                nrm += nn[0].toFloat(); nrm += nn[1].toFloat(); nrm += nn[2].toFloat()
                pos += (P[0] + radius * nn[0]).toFloat(); pos += (P[1] + radius * nn[1]).toFloat(); pos += (P[2] + radius * nn[2]).toFloat()
            }
        }
        for (i in 0..tubularSegments) for (j in 0..radialSegments) { uv += i.toFloat() / tubularSegments; uv += j.toFloat() / radialSegments }
        val idx = ArrayList<Int>()
        for (j in 1..tubularSegments) for (i in 1..radialSegments) {
            val a = (radialSegments + 1) * (j - 1) + (i - 1); val b = (radialSegments + 1) * j + (i - 1)
            val c = (radialSegments + 1) * j + i; val d = (radialSegments + 1) * (j - 1) + i
            idx += a; idx += b; idx += d; idx += b; idx += c; idx += d
        }
        return Geometry(pos.toFloatArray(), nrm.toFloatArray(), uv.toFloatArray(), idx.toIntArray())
    }

    /** three.js r128 LatheGeometry (points are (x, y) pairs), seam normals averaged for a full turn. */
    fun lathe(points: List<FloatArray>, segments: Int = 12, phiStart: Double = 0.0, phiLength: Double = Math.PI * 2): Geometry {
        val pl = phiLength.coerceIn(0.0, Math.PI * 2)
        val inv = 1.0 / segments
        val pos = ArrayList<Float>(); val uv = ArrayList<Float>()
        for (i in 0..segments) {
            val phi = phiStart + i * inv * pl
            val s = sin(phi); val c = cos(phi)
            for (j in points.indices) {
                pos += (points[j][0] * s).toFloat(); pos += points[j][1]; pos += (points[j][0] * c).toFloat()
                uv += i.toFloat() / segments; uv += j.toFloat() / (points.size - 1)
            }
        }
        val idx = ArrayList<Int>()
        for (i in 0 until segments) for (j in 0 until points.size - 1) {
            val base = j + i * points.size
            val a = base; val b = base + points.size; val c = base + points.size + 1; val d = base + 1
            idx += a; idx += b; idx += d; idx += b; idx += c; idx += d
        }
        val g = Geometry(pos.toFloatArray(), FloatArray(pos.size), uv.toFloatArray(), idx.toIntArray())
        g.computeVertexNormals()
        if (pl == Math.PI * 2) {
            val n = g.normals; val base = points.size * segments * 3
            var i = 0; var j = 0
            while (i < points.size) {
                val x = n[j] + n[base + j]; val y = n[j + 1] + n[base + j + 1]; val z = n[j + 2] + n[base + j + 2]
                val l = sqrt(x * x + y * y + z * z).let { if (it == 0f) 1f else it }
                n[j] = x / l; n[j + 1] = y / l; n[j + 2] = z / l
                n[base + j] = x / l; n[base + j + 1] = y / l; n[base + j + 2] = z / l
                i++; j += 3
            }
        }
        return g
    }

    /** BufferGeometryUtils.mergeBufferGeometries(list, false): one geometry, indices offset per part. */
    fun merge(parts: List<Geometry>): Geometry {
        val pos = ArrayList<Float>(); val nrm = ArrayList<Float>(); val uv = ArrayList<Float>(); val idx = ArrayList<Int>()
        var base = 0
        for (g in parts) {
            g.positions.forEach { pos += it }; g.normals.forEach { nrm += it }; g.uvs.forEach { uv += it }
            val ind = g.indices
            if (ind != null) ind.forEach { idx += it + base } else for (i in 0 until g.vertexCount) idx += i + base
            base += g.vertexCount
        }
        return Geometry(pos.toFloatArray(), nrm.toFloatArray(), uv.toFloatArray(), idx.toIntArray())
    }

    /** three.js r128 IcosahedronGeometry. */
    fun icosahedron(radius: Float = 1f, detail: Int = 0): Geometry {
        val t = (1 + sqrt(5.0)) / 2
        val v = doubleArrayOf(-1.0, t, 0.0, 1.0, t, 0.0, -1.0, -t, 0.0, 1.0, -t, 0.0, 0.0, -1.0, t, 0.0, 1.0, t, 0.0, -1.0, -t, 0.0, 1.0, -t,
            t, 0.0, -1.0, t, 0.0, 1.0, -t, 0.0, -1.0, -t, 0.0, 1.0)
        val idx = intArrayOf(0, 11, 5, 0, 5, 1, 0, 1, 7, 0, 7, 10, 0, 10, 11, 1, 5, 9, 5, 11, 4, 11, 10, 2, 10, 7, 6, 7, 1, 8,
            3, 9, 4, 3, 4, 2, 3, 2, 6, 3, 6, 8, 3, 8, 9, 4, 9, 5, 2, 4, 11, 6, 2, 10, 8, 6, 7, 9, 8, 1)
        return Geo.polyhedron(v, idx, radius, detail)
    }
}
