package com.ps1bedroom.room.engine

/**
 * three.js Raycaster.setFromCamera + intersectObjects(list, false): finds the nearest hit among the given meshes,
 * respecting each material's side (front faces only for FrontSide) like three does.
 */
class Raycaster {
    private val origin = Vec3(); private val dir = Vec3()
    private val inv = Mat4()
    private val lo = Vec3(); private val ld = Vec3()
    private val a = Vec3(); private val b = Vec3(); private val c = Vec3()
    private val e1 = Vec3(); private val e2 = Vec3(); private val p = Vec3(); private val q = Vec3(); private val tv = Vec3()
    private val hitLocal = Vec3(); private val hitWorld = Vec3()

    /** ndc: -1..1 with +y up. */
    fun setFromCamera(ndcX: Float, ndcY: Float, camera: PerspectiveCamera) {
        origin.setFromMatrixPosition(camera.matrixWorld)
        dir.set(ndcX, ndcY, 0.5f).applyMatrix4(camera.projectionMatrixInverse).applyMatrix4(camera.matrixWorld).sub(origin).normalize()
    }

    /** Nearest mesh hit (or null) and its distance. */
    fun intersect(meshes: List<Mesh>): Pair<Mesh, Float>? {
        var best: Mesh? = null; var bestD = Float.MAX_VALUE
        for (m in meshes) {
            if (!isVisible(m)) continue   // (three tests object visibility only: invisible-material tap pads still count)
            val d = intersectMesh(m) ?: continue
            if (d < bestD) { bestD = d; best = m }
        }
        return best?.let { it to bestD }
    }

    private fun isVisible(o: Object3D): Boolean { var x: Object3D? = o; while (x != null) { if (!x.visible) return false; x = x.parent }; return true }

    private fun intersectMesh(m: Mesh): Float? {
        val g = m.geometry
        if (g.boundingRadius < 0) g.computeBoundingSphere()
        inv.invert(m.matrixWorld)
        lo.copy(origin).applyMatrix4(inv)
        ld.copy(origin).add(dir).applyMatrix4(inv).sub(lo)   // local direction (not normalised: t stays in world units)
        // bounding sphere reject
        val ocx = g.boundingCenter.x - lo.x; val ocy = g.boundingCenter.y - lo.y; val ocz = g.boundingCenter.z - lo.z
        val l2 = ld.lengthSq()
        val tca = (ocx * ld.x + ocy * ld.y + ocz * ld.z) / l2
        val d2 = ocx * ocx + ocy * ocy + ocz * ocz - tca * tca * l2
        if (d2 > g.boundingRadius * g.boundingRadius) return null
        val front = m.material.side == FRONT_SIDE
        val back = m.material.side == BACK_SIDE
        var best = Float.MAX_VALUE
        val idx = g.indices
        val count = idx?.size ?: g.vertexCount
        var i = 0
        while (i + 2 < count) {
            val i0 = idx?.get(i) ?: i; val i1 = idx?.get(i + 1) ?: (i + 1); val i2 = idx?.get(i + 2) ?: (i + 2)
            a.set(g.positions[i0 * 3], g.positions[i0 * 3 + 1], g.positions[i0 * 3 + 2])
            b.set(g.positions[i1 * 3], g.positions[i1 * 3 + 1], g.positions[i1 * 3 + 2])
            c.set(g.positions[i2 * 3], g.positions[i2 * 3 + 1], g.positions[i2 * 3 + 2])
            val t = triangle(front, back)
            if (t != null && t < best) best = t
            i += 3
        }
        if (best == Float.MAX_VALUE) return null
        // distance in world units
        hitLocal.copy(lo).addScaledVector(ld, best)
        hitWorld.copy(hitLocal).applyMatrix4(m.matrixWorld)
        return hitWorld.distanceTo(origin)
    }

    /** Möller–Trumbore; returns t along the local ray. */
    private fun triangle(frontOnly: Boolean, backOnly: Boolean): Float? {
        e1.subVectors(b, a); e2.subVectors(c, a)
        p.crossVectors(ld, e2)
        val det = e1.dot(p)
        if (frontOnly && det < 1e-9f) return null       // ray hits the back of the face
        if (backOnly && det > -1e-9f) return null
        if (kotlin.math.abs(det) < 1e-9f) return null
        val invDet = 1f / det
        tv.subVectors(lo, a)
        val u = tv.dot(p) * invDet
        if (u < 0f || u > 1f) return null
        q.crossVectors(tv, e1)
        val v = ld.dot(q) * invDet
        if (v < 0f || u + v > 1f) return null
        val t = e2.dot(q) * invDet
        return if (t > 0f) t else null
    }
}
