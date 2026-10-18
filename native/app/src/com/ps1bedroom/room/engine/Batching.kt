package com.ps1bedroom.room.engine

/**
 * Static batching, ported from batchStaticMeshes() in the HTML: every mesh that never moves, glows, changes or
 * gets tapped is baked into world space and merged into one mesh per texture (its colour moves into vertex
 * colours). Hundreds of draw calls become a few dozen, which is most of the frame time on a phone.
 */
object Batching {
    class Result(val removed: Int, val added: Int)

    fun batchStaticMeshes(
        scene: Scene,
        dynamicRoots: Set<Object3D>,
        noBatch: Set<Object3D>,
        excluded: Set<Object3D>,
        staticTags: Set<String>,
        interactables: MutableList<Mesh>
    ): Result {
        scene.updateMatrixWorld()
        fun isDynamic(o: Object3D): Boolean {
            var p: Object3D? = o
            while (p != null) { if (dynamicRoots.contains(p) || !p.visible) return true; p = p.parent }
            return false
        }
        val buckets = LinkedHashMap<String, MutableList<Mesh>>()
        val sharedEmissive = LinkedHashMap<String, MutableList<Mesh>>()
        scene.traverse { o ->
            if (o !is Mesh || o.multiMaterial || isDynamic(o)) return@traverse
            val tag = o.userData["interactTag"] as String?
            if (tag != null && !staticTags.contains(tag)) return@traverse
            if (tag == null && noBatch.contains(o)) return@traverse
            if (excluded.contains(o)) return@traverse
            val m = o.material
            if (!m.lit || m.transparent || m.vertexColors) return@traverse
            if (m.emissive.getHex() != 0) {
                val k = "${System.identityHashCode(m)}|${o.castShadow}|${o.receiveShadow}|${tag ?: ""}"
                sharedEmissive.getOrPut(k) { ArrayList() } += o
                return@traverse
            }
            val key = listOf(tag ?: "", m.map?.let { System.identityHashCode(it).toString() } ?: "-",
                if (m.polygonOffset) "${m.polygonOffsetFactor},${m.polygonOffsetUnits}" else "n", m.side, o.castShadow, o.receiveShadow).joinToString("|")
            buckets.getOrPut(key) { ArrayList() } += o
        }
        fun retag(batch: Mesh, list: List<Mesh>) {
            val tag = list[0].userData["interactTag"] as String? ?: return
            list.forEach { interactables.remove(it) }
            batch.userData["interactTag"] = tag; interactables += batch
        }
        for (list in sharedEmissive.values) {
            if (list.size < 2) continue
            val merged = merge(list, withColors = false)
            val batch = Mesh(merged, list[0].material)
            batch.castShadow = list[0].castShadow; batch.receiveShadow = list[0].receiveShadow; batch.matrixAutoUpdate = false
            scene.add(batch); retag(batch, list)
            list.forEach { it.parent?.remove(it) }
        }
        var removed = 0; var added = 0
        for (list in buckets.values) {
            if (list.size < 2) continue
            val merged = merge(list, withColors = true)
            val src = list[0].material
            val mat = PS1.material(map = src.map, side = src.side, polygonOffset = src.polygonOffset,
                polygonOffsetFactor = src.polygonOffsetFactor, polygonOffsetUnits = src.polygonOffsetUnits)
            mat.vertexColors = true
            val batch = Mesh(merged, mat)
            batch.castShadow = list[0].castShadow; batch.receiveShadow = list[0].receiveShadow; batch.matrixAutoUpdate = false
            scene.add(batch); added++
            retag(batch, list)
            list.forEach { it.parent?.remove(it); removed++ }
        }
        return Result(removed, added)
    }

    /** Non-indexed copies baked into world space, concatenated (three's toNonIndexed + mergeBufferGeometries). */
    private fun merge(list: List<Mesh>, withColors: Boolean): Geometry {
        var verts = 0
        val parts = list.map { m ->
            val g = expand(m.geometry).transformed(m.matrixWorld)
            verts += g.vertexCount
            Pair(g, m.material.color)
        }
        val pos = FloatArray(verts * 3); val nor = FloatArray(verts * 3); val uv = FloatArray(verts * 2)
        val col = if (withColors) FloatArray(verts * 3) else null
        var v = 0
        for ((g, c) in parts) {
            val n = g.vertexCount
            System.arraycopy(g.positions, 0, pos, v * 3, n * 3)
            System.arraycopy(g.normals, 0, nor, v * 3, n * 3)
            if (g.uvs.size >= n * 2) System.arraycopy(g.uvs, 0, uv, v * 2, n * 2)
            if (col != null) for (i in 0 until n) { col[(v + i) * 3] = c.r; col[(v + i) * 3 + 1] = c.g; col[(v + i) * 3 + 2] = c.b }
            v += n
        }
        return Geometry(pos, nor, uv, null, emptyList(), col)
    }

    private fun expand(g: Geometry): Geometry {
        val idx = g.indices ?: return g
        val p = FloatArray(idx.size * 3); val n = FloatArray(idx.size * 3); val u = FloatArray(idx.size * 2)
        for (i in idx.indices) {
            val k = idx[i]
            p[i * 3] = g.positions[k * 3]; p[i * 3 + 1] = g.positions[k * 3 + 1]; p[i * 3 + 2] = g.positions[k * 3 + 2]
            n[i * 3] = g.normals[k * 3]; n[i * 3 + 1] = g.normals[k * 3 + 1]; n[i * 3 + 2] = g.normals[k * 3 + 2]
            if (g.uvs.size > k * 2 + 1) { u[i * 2] = g.uvs[k * 2]; u[i * 2 + 1] = g.uvs[k * 2 + 1] }
        }
        return Geometry(p, n, u, null)
    }
}
