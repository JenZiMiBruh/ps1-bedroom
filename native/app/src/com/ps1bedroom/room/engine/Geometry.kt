package com.ps1bedroom.room.engine

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Buffer geometry plus generators that produce exactly the same vertices, normals, UVs, index order and
// material groups as three.js r128 (BoxGeometry, PlaneGeometry, CylinderGeometry, SphereGeometry, ...),
// so every mesh in the HTML version comes out identical.

class Geometry(
    @JvmField val positions: FloatArray,
    @JvmField val normals: FloatArray,
    @JvmField val uvs: FloatArray,
    @JvmField val indices: IntArray?,
    /** (start, count, materialIndex) in indices, like three's geometry.groups */
    @JvmField val groups: List<IntArray> = emptyList(),
    /** Per-vertex RGB (static batches carry each part's material colour here, like three's vertexColors) */
    @JvmField val colors: FloatArray? = null
) {
    val vertexCount get() = positions.size / 3
    // GL handles, filled in by the renderer
    @JvmField var vao = 0
    @JvmField var vbo = IntArray(4)
    @JvmField var uploaded = false
    @JvmField var boundingRadius = -1f
    @JvmField val boundingCenter = Vec3()

    fun computeBoundingSphere() {
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        var i = 0
        while (i < positions.size) {
            val x = positions[i]; val y = positions[i + 1]; val z = positions[i + 2]
            if (x < minX) minX = x; if (y < minY) minY = y; if (z < minZ) minZ = z
            if (x > maxX) maxX = x; if (y > maxY) maxY = y; if (z > maxZ) maxZ = z
            i += 3
        }
        boundingCenter.set((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2)
        var r2 = 0f
        i = 0
        while (i < positions.size) {
            val dx = positions[i] - boundingCenter.x; val dy = positions[i + 1] - boundingCenter.y; val dz = positions[i + 2] - boundingCenter.z
            val d = dx * dx + dy * dy + dz * dz
            if (d > r2) r2 = d
            i += 3
        }
        boundingRadius = sqrt(r2)
    }

    /** Bakes a transform into a copy (used by static batching and the three.js geometry.rotateX/translate calls). */
    fun transformed(m: Mat4): Geometry {
        val p = positions.copyOf(); val n = normals.copyOf()
        val nm = FloatArray(9); m.normalMatrixInto(nm)
        val v = Vec3()
        var i = 0
        while (i < p.size) {
            v.set(p[i], p[i + 1], p[i + 2]).applyMatrix4(m); p[i] = v.x; p[i + 1] = v.y; p[i + 2] = v.z
            val nx = n[i]; val ny = n[i + 1]; val nz = n[i + 2]
            var tx = nm[0] * nx + nm[3] * ny + nm[6] * nz; var ty = nm[1] * nx + nm[4] * ny + nm[7] * nz; var tz = nm[2] * nx + nm[5] * ny + nm[8] * nz
            val l = sqrt(tx * tx + ty * ty + tz * tz); if (l > 0) { tx /= l; ty /= l; tz /= l }
            n[i] = tx; n[i + 1] = ty; n[i + 2] = tz
            i += 3
        }
        return Geometry(p, n, uvs.copyOf(), indices?.copyOf(), groups, colors?.copyOf())
    }
    fun rotateX(a: Float) = transformed(Mat4().compose(Vec3(), Quat().setFromEuler(Euler(a, 0f, 0f)), Vec3(1f, 1f, 1f)))
    fun rotateY(a: Float) = transformed(Mat4().compose(Vec3(), Quat().setFromEuler(Euler(0f, a, 0f)), Vec3(1f, 1f, 1f)))
    fun rotateZ(a: Float) = transformed(Mat4().compose(Vec3(), Quat().setFromEuler(Euler(0f, 0f, a)), Vec3(1f, 1f, 1f)))
    fun translate(x: Float, y: Float, z: Float) = transformed(Mat4().compose(Vec3(x, y, z), Quat(), Vec3(1f, 1f, 1f)))
}

private class Builder {
    val pos = ArrayList<Float>(); val nor = ArrayList<Float>(); val uv = ArrayList<Float>(); val idx = ArrayList<Int>()
    val groups = ArrayList<IntArray>()
    fun v(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, u: Float, vv: Float) { pos += x; pos += y; pos += z; nor += nx; nor += ny; nor += nz; uv += u; uv += vv }
    fun tri(a: Int, b: Int, c: Int) { idx += a; idx += b; idx += c }
    fun build() = Geometry(pos.toFloatArray(), nor.toFloatArray(), uv.toFloatArray(), idx.toIntArray(), groups)
}

object Geo {
    /** three.js r128 BoxGeometry (6 groups, materialIndex 0..5 = +x, -x, +y, -y, +z, -z). */
    fun box(width: Float, height: Float, depth: Float, ws: Int = 1, hs: Int = 1, ds: Int = 1): Geometry {
        val b = Builder()
        var vertexCount = 0; var groupStart = 0
        fun plane(u: Int, v: Int, w: Int, udir: Float, vdir: Float, pw: Float, ph: Float, pd: Float, gx: Int, gy: Int, mi: Int) {
            val segW = pw / gx; val segH = ph / gy
            val wh = pw / 2; val hh = ph / 2; val dh = pd / 2
            val gx1 = gx + 1; val gy1 = gy + 1
            var vc = 0
            val vec = FloatArray(3); val nrm = FloatArray(3)
            for (iy in 0 until gy1) {
                val y = iy * segH - hh
                for (ix in 0 until gx1) {
                    val x = ix * segW - wh
                    vec[u] = x * udir; vec[v] = y * vdir; vec[w] = dh
                    nrm[u] = 0f; nrm[v] = 0f; nrm[w] = if (pd > 0) 1f else -1f
                    b.v(vec[0], vec[1], vec[2], nrm[0], nrm[1], nrm[2], ix.toFloat() / gx, 1 - iy.toFloat() / gy)
                    vc++
                }
            }
            var gc = 0
            for (iy in 0 until gy) for (ix in 0 until gx) {
                val a = vertexCount + ix + gx1 * iy
                val bb = vertexCount + ix + gx1 * (iy + 1)
                val c = vertexCount + (ix + 1) + gx1 * (iy + 1)
                val d = vertexCount + (ix + 1) + gx1 * iy
                b.tri(a, bb, d); b.tri(bb, c, d); gc += 6
            }
            b.groups += intArrayOf(groupStart, gc, mi)
            groupStart += gc; vertexCount += vc
        }
        plane(2, 1, 0, -1f, -1f, depth, height, width, ds, hs, 0)
        plane(2, 1, 0, 1f, -1f, depth, height, -width, ds, hs, 1)
        plane(0, 2, 1, 1f, 1f, width, depth, height, ws, ds, 2)
        plane(0, 2, 1, 1f, -1f, width, depth, -height, ws, ds, 3)
        plane(0, 1, 2, 1f, -1f, width, height, depth, ws, hs, 4)
        plane(0, 1, 2, -1f, -1f, width, height, -depth, ws, hs, 5)
        return b.build()
    }

    /** three.js r128 PlaneGeometry (in XY, facing +Z). */
    fun plane(width: Float, height: Float, ws: Int = 1, hs: Int = 1): Geometry {
        val b = Builder()
        val wh = width / 2; val hh = height / 2
        val gx1 = ws + 1; val gy1 = hs + 1
        val segW = width / ws; val segH = height / hs
        for (iy in 0 until gy1) {
            val y = iy * segH - hh
            for (ix in 0 until gx1) {
                val x = ix * segW - wh
                b.v(x, -y, 0f, 0f, 0f, 1f, ix.toFloat() / ws, 1 - iy.toFloat() / hs)
            }
        }
        for (iy in 0 until hs) for (ix in 0 until ws) {
            val a = ix + gx1 * iy; val bb = ix + gx1 * (iy + 1); val c = (ix + 1) + gx1 * (iy + 1); val d = (ix + 1) + gx1 * iy
            b.tri(a, bb, d); b.tri(bb, c, d)
        }
        return b.build()
    }

    /** three.js r128 CylinderGeometry (groups: 0 = side, 1 = top cap, 2 = bottom cap). */
    fun cylinder(rt: Float, rb: Float, height: Float, radialSegments: Int = 8, heightSegments: Int = 1, openEnded: Boolean = false,
                 thetaStart: Float = 0f, thetaLength: Float = (Math.PI * 2).toFloat()): Geometry {
        val b = Builder()
        var index = 0; var groupStart = 0
        val halfHeight = height / 2
        val indexArray = ArrayList<IntArray>()
        // torso
        val slope = (rb - rt) / height
        for (y in 0..heightSegments) {
            val row = IntArray(radialSegments + 1)
            val v = y.toFloat() / heightSegments
            val radius = v * (rb - rt) + rt
            for (x in 0..radialSegments) {
                val u = x.toFloat() / radialSegments
                val theta = u * thetaLength + thetaStart
                val st = sin(theta); val ct = cos(theta)
                var nx = st; var ny = slope; var nz = ct
                val l = sqrt(nx * nx + ny * ny + nz * nz); nx /= l; ny /= l; nz /= l
                b.v(radius * st, -v * height + halfHeight, radius * ct, nx, ny, nz, u, 1 - v)
                row[x] = index++
            }
            indexArray += row
        }
        var gc = 0
        for (x in 0 until radialSegments) for (y in 0 until heightSegments) {
            val a = indexArray[y][x]; val bb = indexArray[y + 1][x]; val c = indexArray[y + 1][x + 1]; val d = indexArray[y][x + 1]
            b.tri(a, bb, d); b.tri(bb, c, d); gc += 6
        }
        b.groups += intArrayOf(groupStart, gc, 0); groupStart += gc
        fun cap(top: Boolean) {
            val centerIndexStart = index
            val radius = if (top) rt else rb
            val sign = if (top) 1f else -1f
            for (x in 1..radialSegments) { b.v(0f, halfHeight * sign, 0f, 0f, sign, 0f, 0.5f, 0.5f); index++ }
            val centerIndexEnd = index
            for (x in 0..radialSegments) {
                val u = x.toFloat() / radialSegments
                val theta = u * thetaLength + thetaStart
                val ct = cos(theta); val st = sin(theta)
                b.v(radius * st, halfHeight * sign, radius * ct, 0f, sign, 0f, ct * 0.5f + 0.5f, st * 0.5f * sign + 0.5f)
                index++
            }
            var cc = 0
            for (x in 0 until radialSegments) {
                val c = centerIndexStart + x; val i = centerIndexEnd + x
                if (top) b.tri(i, i + 1, c) else b.tri(i + 1, i, c)
                cc += 3
            }
            b.groups += intArrayOf(groupStart, cc, if (top) 1 else 2); groupStart += cc
        }
        if (!openEnded) { if (rt > 0) cap(true); if (rb > 0) cap(false) }
        return b.build()
    }

    /** three.js r128 ConeGeometry = CylinderGeometry(0, radius, ...). */
    fun cone(radius: Float, height: Float, radialSegments: Int = 8, heightSegments: Int = 1, openEnded: Boolean = false) =
        cylinder(0f, radius, height, radialSegments, heightSegments, openEnded)

    /** three.js r128 SphereGeometry (defaults 8 x 6). */
    fun sphere(radius: Float, ws: Int = 8, hs: Int = 6, phiStart: Float = 0f, phiLength: Float = (Math.PI * 2).toFloat(),
               thetaStart: Float = 0f, thetaLength: Float = Math.PI.toFloat()): Geometry {
        val b = Builder()
        val widthSegments = maxOf(3, ws); val heightSegments = maxOf(2, hs)
        val thetaEnd = minOf(thetaStart + thetaLength, Math.PI.toFloat())
        var index = 0
        val grid = ArrayList<IntArray>()
        for (iy in 0..heightSegments) {
            val row = IntArray(widthSegments + 1)
            val v = iy.toFloat() / heightSegments
            var uOffset = 0f
            if (iy == 0 && thetaStart == 0f) uOffset = 0.5f / widthSegments
            else if (iy == heightSegments && thetaEnd == Math.PI.toFloat()) uOffset = -0.5f / widthSegments
            for (ix in 0..widthSegments) {
                val u = ix.toFloat() / widthSegments
                val x = -radius * cos(phiStart + u * phiLength) * sin(thetaStart + v * thetaLength)
                val y = radius * cos(thetaStart + v * thetaLength)
                val z = radius * sin(phiStart + u * phiLength) * sin(thetaStart + v * thetaLength)
                val l = sqrt(x * x + y * y + z * z).let { if (it == 0f) 1f else it }
                b.v(x, y, z, x / l, y / l, z / l, u + uOffset, 1 - v)
                row[ix] = index++
            }
            grid += row
        }
        for (iy in 0 until heightSegments) for (ix in 0 until widthSegments) {
            val a = grid[iy][ix + 1]; val bb = grid[iy][ix]; val c = grid[iy + 1][ix]; val d = grid[iy + 1][ix + 1]
            if (iy != 0 || thetaStart > 0) b.tri(a, bb, d)
            if (iy != heightSegments - 1 || thetaEnd < Math.PI) b.tri(bb, c, d)
        }
        return b.build()
    }

    /**
     * three.js r128 PolyhedronGeometry: non-indexed, each face subdivided `detail` times and pushed onto the sphere of
     * `radius`, with three's spherical UVs (seam and pole fixes included). Detail 0 gets flat face normals.
     */
    fun polyhedron(vertices: DoubleArray, indices: IntArray, radius: Float, detail: Int): Geometry {
        val vb = ArrayList<Double>()
        fun push(p: DoubleArray) { vb += p[0]; vb += p[1]; vb += p[2] }
        fun lerp(a: DoubleArray, b: DoubleArray, t: Double) = doubleArrayOf(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t)
        fun vtx(i: Int) = doubleArrayOf(vertices[i * 3], vertices[i * 3 + 1], vertices[i * 3 + 2])
        var f = 0
        while (f < indices.size) {
            val a = vtx(indices[f]); val b = vtx(indices[f + 1]); val c = vtx(indices[f + 2])
            val cols = detail + 1
            val v = Array(cols + 1) { ArrayList<DoubleArray>() }
            for (i in 0..cols) {
                val aj = lerp(a, c, i.toDouble() / cols); val bj = lerp(b, c, i.toDouble() / cols)
                val rows = cols - i
                for (j in 0..rows) v[i] += if (j == 0 && i == cols) aj else lerp(aj, bj, j.toDouble() / rows)
            }
            for (i in 0 until cols) for (j in 0 until 2 * (cols - i) - 1) {
                val k = j / 2
                if (j % 2 == 0) { push(v[i][k + 1]); push(v[i + 1][k]); push(v[i][k]) }
                else { push(v[i][k + 1]); push(v[i + 1][k + 1]); push(v[i + 1][k]) }
            }
            f += 3
        }
        val pos = DoubleArray(vb.size) { vb[it] }
        // applyRadius
        var i = 0
        while (i < pos.size) {
            val l = sqrt(pos[i] * pos[i] + pos[i + 1] * pos[i + 1] + pos[i + 2] * pos[i + 2])
            pos[i] = pos[i] / l * radius; pos[i + 1] = pos[i + 1] / l * radius; pos[i + 2] = pos[i + 2] / l * radius
            i += 3
        }
        // generateUVs
        fun azimuth(x: Double, z: Double) = kotlin.math.atan2(z, -x)
        fun inclination(x: Double, y: Double, z: Double) = kotlin.math.atan2(-y, sqrt(x * x + z * z))
        val n = pos.size / 3
        val uv = DoubleArray(n * 2)
        for (k in 0 until n) {
            val x = pos[k * 3]; val y = pos[k * 3 + 1]; val z = pos[k * 3 + 2]
            uv[k * 2] = azimuth(x, z) / 2 / Math.PI + 0.5
            uv[k * 2 + 1] = 1 - (inclination(x, y, z) / Math.PI + 0.5)
        }
        // correctUVs
        var t = 0
        while (t < n) {
            val cx = (pos[t * 3] + pos[t * 3 + 3] + pos[t * 3 + 6]) / 3; val cz = (pos[t * 3 + 2] + pos[t * 3 + 5] + pos[t * 3 + 8]) / 3
            val azi = azimuth(cx, cz)
            for (q in 0 until 3) {
                val vi = t + q
                if (azi < 0 && uv[vi * 2] == 1.0) uv[vi * 2] = uv[vi * 2] - 1
                if (pos[vi * 3] == 0.0 && pos[vi * 3 + 2] == 0.0) uv[vi * 2] = azi / 2 / Math.PI + 0.5
            }
            t += 3
        }
        // correctSeam
        var s = 0
        while (s < uv.size) {
            val x0 = uv[s]; val x1 = uv[s + 2]; val x2 = uv[s + 4]
            if (maxOf(x0, x1, x2) > 0.9 && minOf(x0, x1, x2) < 0.1) {
                if (x0 < 0.2) uv[s] += 1.0
                if (x1 < 0.2) uv[s + 2] += 1.0
                if (x2 < 0.2) uv[s + 4] += 1.0
            }
            s += 6
        }
        val p = FloatArray(pos.size) { pos[it].toFloat() }
        val nrm = FloatArray(pos.size)
        if (detail == 0) {
            // computeVertexNormals on a non-indexed geometry: one flat normal per triangle, (C - B) x (A - B)
            var tri = 0
            while (tri < n) {
                val o = tri * 3
                val cbx = p[o + 6] - p[o + 3]; val cby = p[o + 7] - p[o + 4]; val cbz = p[o + 8] - p[o + 5]
                val abx = p[o] - p[o + 3]; val aby = p[o + 1] - p[o + 4]; val abz = p[o + 2] - p[o + 5]
                var nx = cby * abz - cbz * aby; var ny = cbz * abx - cbx * abz; var nz = cbx * aby - cby * abx
                val l = sqrt(nx * nx + ny * ny + nz * nz).let { if (it == 0f) 1f else it }
                nx /= l; ny /= l; nz /= l
                for (q in 0 until 3) { nrm[o + q * 3] = nx; nrm[o + q * 3 + 1] = ny; nrm[o + q * 3 + 2] = nz }
                tri += 3
            }
        } else {
            for (k in 0 until n) { val l = sqrt(p[k * 3] * p[k * 3] + p[k * 3 + 1] * p[k * 3 + 1] + p[k * 3 + 2] * p[k * 3 + 2]); nrm[k * 3] = p[k * 3] / l; nrm[k * 3 + 1] = p[k * 3 + 1] / l; nrm[k * 3 + 2] = p[k * 3 + 2] / l }
        }
        return Geometry(p, nrm, FloatArray(uv.size) { uv[it].toFloat() }, null)
    }

    /** three.js r128 DodecahedronGeometry. */
    fun dodecahedron(radius: Float = 1f, detail: Int = 0): Geometry {
        val t = (1 + sqrt(5.0)) / 2; val r = 1 / t
        val v = doubleArrayOf(
            -1.0, -1.0, -1.0, -1.0, -1.0, 1.0, -1.0, 1.0, -1.0, -1.0, 1.0, 1.0,
            1.0, -1.0, -1.0, 1.0, -1.0, 1.0, 1.0, 1.0, -1.0, 1.0, 1.0, 1.0,
            0.0, -r, -t, 0.0, -r, t, 0.0, r, -t, 0.0, r, t,
            -r, -t, 0.0, -r, t, 0.0, r, -t, 0.0, r, t, 0.0,
            -t, 0.0, -r, t, 0.0, -r, -t, 0.0, r, t, 0.0, r
        )
        val idx = intArrayOf(
            3, 11, 7, 3, 7, 15, 3, 15, 13, 7, 19, 17, 7, 17, 6, 7, 6, 15, 17, 4, 8, 17, 8, 10, 17, 10, 6,
            8, 0, 16, 8, 16, 2, 8, 2, 10, 0, 12, 1, 0, 1, 18, 0, 18, 16, 6, 10, 2, 6, 2, 13, 6, 13, 15,
            2, 16, 18, 2, 18, 3, 2, 3, 13, 18, 1, 9, 18, 9, 11, 18, 11, 3, 4, 14, 12, 4, 12, 0, 4, 0, 8,
            11, 9, 5, 11, 5, 19, 11, 19, 7, 19, 5, 14, 19, 14, 4, 19, 4, 17, 1, 12, 14, 1, 14, 5, 1, 5, 9
        )
        return polyhedron(v, idx, radius, detail)
    }

    /** three.js r128 OctahedronGeometry. */
    fun octahedron(radius: Float = 1f, detail: Int = 0) = polyhedron(
        doubleArrayOf(1.0, 0.0, 0.0, -1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, -1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, -1.0),
        intArrayOf(0, 2, 4, 0, 4, 3, 0, 3, 5, 0, 5, 2, 1, 2, 5, 1, 5, 3, 1, 3, 4, 1, 4, 2), radius, detail)

    /** three.js r128 CircleGeometry (in XY, facing +Z). */
    fun circle(radius: Float, segments: Int = 8, thetaStart: Float = 0f, thetaLength: Float = (Math.PI * 2).toFloat()): Geometry {
        val b = Builder()
        val segs = maxOf(3, segments)
        b.v(0f, 0f, 0f, 0f, 0f, 1f, 0.5f, 0.5f)
        for (s in 0..segs) {
            val segment = thetaStart + s.toFloat() / segs * thetaLength
            val x = radius * cos(segment); val y = radius * sin(segment)
            b.v(x, y, 0f, 0f, 0f, 1f, (x / radius + 1) / 2, (y / radius + 1) / 2)
        }
        for (i in 1..segs) b.tri(i, i + 1, 0)
        return b.build()
    }
}
