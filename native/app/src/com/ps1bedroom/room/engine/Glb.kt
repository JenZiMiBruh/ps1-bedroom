package com.ps1bedroom.room.engine

import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal glTF 2.0 binary (.glb) loader for the Kenney furniture models, built the way three r128's GLTFLoader
 * builds them: a node with a mesh becomes that mesh (or a Group of one Mesh per primitive when there are several),
 * child nodes are added after the primitives, node TRS is applied, and each primitive gets its own geometry
 * (POSITION / NORMAL / TEXCOORD_0 + indices). Materials are handed to [makeMaterial] with the baseColorFactor
 * colour (three's Color.getHex), so the caller can swap in PS1 materials in the same order as the HTML.
 */
object Glb {
    fun load(bytes: ByteArray, makeMaterial: (colorHex: Int) -> Material): Group {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val jsonLen = bb.getInt(12)
        val json = JSONObject(String(bytes, 20, jsonLen, Charsets.UTF_8))
        val binStart = 20 + jsonLen + 8
        val views = json.getJSONArray("bufferViews")
        val accessors = json.getJSONArray("accessors")
        val materials = json.optJSONArray("materials") ?: JSONArray()
        val meshes = json.getJSONArray("meshes")
        val nodes = json.getJSONArray("nodes")

        fun floats(acc: Int): FloatArray {
            val a = accessors.getJSONObject(acc)
            val v = views.getJSONObject(a.getInt("bufferView"))
            val comps = when (a.getString("type")) { "VEC2" -> 2; "VEC3" -> 3; "VEC4" -> 4; else -> 1 }
            val n = a.getInt("count") * comps
            val off = binStart + v.optInt("byteOffset", 0) + a.optInt("byteOffset", 0)
            val stride = v.optInt("byteStride", 0).let { if (it == 0) comps * 4 else it }
            return FloatArray(n) { i -> bb.getFloat(off + (i / comps) * stride + (i % comps) * 4) }
        }
        fun ints(acc: Int): IntArray {
            val a = accessors.getJSONObject(acc)
            val v = views.getJSONObject(a.getInt("bufferView"))
            val n = a.getInt("count")
            val off = binStart + v.optInt("byteOffset", 0) + a.optInt("byteOffset", 0)
            return when (a.getInt("componentType")) {
                5125 -> IntArray(n) { bb.getInt(off + it * 4) }
                5123 -> IntArray(n) { bb.getShort(off + it * 2).toInt() and 0xffff }
                else -> IntArray(n) { bytes[off + it].toInt() and 0xff }
            }
        }
        fun colorOf(matIdx: Int): Int {
            if (matIdx < 0 || matIdx >= materials.length()) return 0xcccccc
            val f = materials.getJSONObject(matIdx).optJSONObject("pbrMetallicRoughness")?.optJSONArray("baseColorFactor") ?: return 0xffffff
            return Color(f.getDouble(0).toFloat(), f.getDouble(1).toFloat(), f.getDouble(2).toFloat()).getHex()
        }
        fun primitiveMesh(p: JSONObject): Mesh {
            val attr = p.getJSONObject("attributes")
            val pos = floats(attr.getInt("POSITION"))
            val nrm = if (attr.has("NORMAL")) floats(attr.getInt("NORMAL")) else FloatArray(pos.size)
            val uv = if (attr.has("TEXCOORD_0")) floats(attr.getInt("TEXCOORD_0")) else FloatArray(pos.size / 3 * 2)
            val idx = if (p.has("indices")) ints(p.getInt("indices")) else null
            val g = Geometry(pos, nrm, uv, idx)
            if (!attr.has("NORMAL")) g.computeVertexNormals()
            return Mesh(g, makeMaterial(colorOf(p.optInt("material", -1))))
        }
        val tmpM = Mat4(); val tmpQ = Quat(); val one = Vec3(1f, 1f, 1f); val zero = Vec3()
        fun buildNode(i: Int): Object3D {
            val n = nodes.getJSONObject(i)
            val obj: Object3D = if (n.has("mesh")) {
                val prims = meshes.getJSONObject(n.getInt("mesh")).getJSONArray("primitives")
                if (prims.length() == 1) primitiveMesh(prims.getJSONObject(0))
                else Group().also { grp -> for (k in 0 until prims.length()) grp.add(primitiveMesh(prims.getJSONObject(k))) }
            } else Group()
            n.optJSONArray("translation")?.let { obj.position.set(it.getDouble(0), it.getDouble(1), it.getDouble(2)) }
            n.optJSONArray("rotation")?.let {
                tmpQ.x = it.getDouble(0).toFloat(); tmpQ.y = it.getDouble(1).toFloat(); tmpQ.z = it.getDouble(2).toFloat(); tmpQ.w = it.getDouble(3).toFloat()
                tmpM.compose(zero, tmpQ, one).toEulerXYZ(obj.rotation)
            }
            n.optJSONArray("scale")?.let { obj.scale.set(it.getDouble(0), it.getDouble(1), it.getDouble(2)) }
            n.optJSONArray("children")?.let { ch -> for (k in 0 until ch.length()) obj.add(buildNode(ch.getInt(k))) }
            return obj
        }
        val root = Group()
        val sceneIdx = json.optInt("scene", 0)
        val sceneNodes = json.getJSONArray("scenes").getJSONObject(sceneIdx).getJSONArray("nodes")
        for (k in 0 until sceneNodes.length()) root.add(buildNode(sceneNodes.getInt(k)))
        return root
    }
}
