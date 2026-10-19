package com.ps1bedroom.room.engine

// Scene graph that mirrors three.js r128: Object3D / Group / Mesh / Scene, cameras and lights.

open class Object3D {
    @JvmField val position = Vec3()
    @JvmField val rotation = Euler()
    @JvmField val scale = Vec3(1f, 1f, 1f)
    @JvmField val quaternion = Quat()
    @JvmField val matrix = Mat4()
    @JvmField val matrixWorld = Mat4()
    @JvmField var visible = true
    @JvmField var parent: Object3D? = null
    @JvmField val children = ArrayList<Object3D>()
    @JvmField var castShadow = false
    @JvmField var receiveShadow = false
    @JvmField var renderOrder = 0
    @JvmField var frustumCulled = true
    @JvmField var name = ""
    /** Tag used by tap interactions (the HTML's userData.interactTag) and free-form data. */
    @JvmField val userData = HashMap<String, Any?>()

    fun add(vararg objs: Object3D): Object3D {
        for (o in objs) { o.parent?.children?.remove(o); o.parent = this; children.add(o) }
        return this
    }
    fun remove(o: Object3D) { if (children.remove(o)) o.parent = null }
    /** three's Object3D.attach: re-parent while keeping the world transform (for unscaled objects). */
    fun attach(o: Object3D) {
        updateMatrixWorld(); o.updateMatrixWorld()
        val inv = Mat4().invert(matrixWorld)
        val local = Mat4().multiplyMatrices(inv, o.matrixWorld)
        o.position.setFromMatrixPosition(local)
        local.toEulerXYZ(o.rotation)
        add(o)
    }
    /** Sets rotation (XYZ order) from a quaternion. */
    fun setRotationFromQuat(q: Quat) { sceneTmpM.compose(sceneZero, q, sceneOne).toEulerXYZ(rotation) }


    fun updateMatrix() { quaternion.setFromEuler(rotation); matrix.compose(position, quaternion, scale) }

    /** false for baked static batches: their matrix stays identity (three's matrixAutoUpdate). */
    @JvmField var matrixAutoUpdate = true

    fun updateMatrixWorld(force: Boolean = true) {
        if (matrixAutoUpdate) updateMatrix()
        val p = parent
        if (p == null) matrixWorld.copy(matrix) else matrixWorld.multiplyMatrices(p.matrixWorld, matrix)
        val n = children.size
        for (i in 0 until n) children[i].updateMatrixWorld(force)
    }

    /** Safe while the callback removes objects (iterates a snapshot); used at build time only. */
    fun traverse(fn: (Object3D) -> Unit) { fn(this); for (c in ArrayList(children)) c.traverse(fn) }
    /** Per-frame traversal: no allocation. */
    fun traverseVisible(fn: (Object3D) -> Unit) {
        if (!visible) return
        fn(this)
        val n = children.size
        for (i in 0 until n) children[i].traverseVisible(fn)
    }

    fun getWorldPosition(out: Vec3): Vec3 = out.setFromMatrixPosition(matrixWorld)

    /** Rotates so local +Z faces the point (three's Object3D.lookAt for non-cameras). */
    fun lookAt(target: Vec3) {
        val pos = Vec3().setFromMatrixPosition(matrixWorld)
        val m = Mat4().lookAt(target, pos, Vec3(0f, 1f, 0f))
        m.toEulerXYZ(rotation)
    }

    /** Moves along local Z (three's translateZ). */
    fun translateZ(d: Float) {
        quaternion.setFromEuler(rotation)
        val q = quaternion
        // rotate (0,0,1) by q
        val x = 2 * (q.x * q.z + q.w * q.y); val y = 2 * (q.y * q.z - q.w * q.x); val z = 1 - 2 * (q.x * q.x + q.y * q.y)
        position.x += x * d; position.y += y * d; position.z += z * d
    }
}

class Group : Object3D()

class Scene : Object3D() {
    @JvmField var background = Color(0x040612)
    @JvmField var fogColor = Color(0x050817)
    @JvmField var fogDensity = 0.025f
}

open class Mesh(@JvmField var geometry: Geometry, materials: List<Material>) : Object3D() {
    @JvmField var materials: MutableList<Material> = materials.toMutableList()
    constructor(geometry: Geometry, material: Material) : this(geometry, listOf(material))
    var material: Material
        get() = materials[0]
        set(v) { materials = mutableListOf(v) }
    /** True when built with one material per face group (three's material arrays). */
    val multiMaterial get() = materials.size > 1
}

class PerspectiveCamera(@JvmField var fov: Float, @JvmField var aspect: Float, @JvmField var near: Float, @JvmField var far: Float) : Object3D() {
    @JvmField val projectionMatrix = Mat4()
    @JvmField val projectionMatrixInverse = Mat4()
    @JvmField val matrixWorldInverse = Mat4()
    @JvmField val up = Vec3(0f, 1f, 0f)
    private val target = Vec3()
    fun updateProjectionMatrix() { projectionMatrix.makePerspective(fov, aspect, near, far); projectionMatrixInverse.invert(projectionMatrix) }

    /** Cameras look down -Z: rotation so -Z faces the target. */
    fun lookAtTarget(t: Vec3) {
        target.copy(t)
        val m = Mat4().lookAt(position, target, up)
        m.toEulerXYZ(rotation)
    }

    fun updateCameraWorld() { updateMatrixWorld(); matrixWorldInverse.invert(matrixWorld) }
}

// ---- Lights (three.js r128, physicallyCorrectLights off) ----

abstract class Light(hex: Int, @JvmField var intensity: Float) : Object3D() {
    @JvmField val color = Color(hex)
}

class AmbientLight(hex: Int, intensity: Float) : Light(hex, intensity)

class HemisphereLight(skyHex: Int, groundHex: Int, intensity: Float) : Light(skyHex, intensity) {
    @JvmField val groundColor = Color(groundHex)
    init { position.set(0f, 1f, 0f) }
}

class PointLight(hex: Int, intensity: Float, @JvmField var distance: Float = 0f, @JvmField var decay: Float = 1f) : Light(hex, intensity)

class SpotLight(hex: Int, intensity: Float) : Light(hex, intensity) {
    @JvmField var distance = 0f
    @JvmField var decay = 1f
    @JvmField var angle = (Math.PI / 3).toFloat()
    @JvmField var penumbra = 0f
    @JvmField val target = Object3D()
    @JvmField val shadow = SpotShadow()
    init { position.set(0f, 1f, 0f) }
}

class SpotShadow {
    @JvmField var mapSize = 512
    @JvmField var near = 0.5f
    @JvmField var far = 500f
    @JvmField var bias = 0f
    @JvmField val camera = PerspectiveCamera(50f, 1f, 0.5f, 500f)
    /** bias * projection * view: world -> shadow-map uv/depth */
    @JvmField val matrix = Mat4()
    @JvmField var needsUpdate = true
}

// GL-thread scratch for setRotationFromQuat (no per-object allocations)
private val sceneTmpM = Mat4()
private val sceneZero = Vec3()
private val sceneOne = Vec3(1f, 1f, 1f)

/**
 * three's InstancedMesh: many copies of one geometry in a single draw call. Set each copy with [setInstance]
 * (or [setColorAt]), then call [needsUpdate] once; the matrices go to the GPU in one buffer upload.
 * Never frustum culled (like the HTML's particles) and never in static batches.
 */
class InstancedMesh(geometry: Geometry, material: Material, @JvmField val count: Int) : Mesh(geometry, material) {
    @JvmField val instanceMatrices = FloatArray(count * 16)
    @JvmField var instanceColors: FloatArray? = null
    @JvmField var version = 1
    // GL state, owned by the renderer
    @JvmField var uploadedVersion = 0
    @JvmField var instVao = 0
    @JvmField var instVbo = IntArray(2)
    init {
        frustumCulled = false
        for (i in 0 until count) for (k in 0 until 4) instanceMatrices[i * 16 + k * 5] = 1f
    }
    /** compose(position, quaternion from Euler XYZ, uniform scale), as the HTML's setInstance does. */
    fun setInstance(i: Int, x: Float, y: Float, z: Float, rx: Float, ry: Float, rz: Float, s: Float) {
        val a = kotlin.math.cos(rx); val b = kotlin.math.sin(rx)
        val c = kotlin.math.cos(ry); val d = kotlin.math.sin(ry)
        val e = kotlin.math.cos(rz); val f = kotlin.math.sin(rz)
        val m = instanceMatrices; val o = i * 16
        // three Matrix4.makeRotationFromEuler, order XYZ (column-major)
        m[o] = c * e * s; m[o + 4] = -c * f * s; m[o + 8] = d * s
        m[o + 1] = (a * f + b * e * d) * s; m[o + 5] = (a * e - b * f * d) * s; m[o + 9] = -b * c * s
        m[o + 2] = (b * f - a * e * d) * s; m[o + 6] = (b * e + a * f * d) * s; m[o + 10] = a * c * s
        m[o + 3] = 0f; m[o + 7] = 0f; m[o + 11] = 0f
        m[o + 12] = x; m[o + 13] = y; m[o + 14] = z; m[o + 15] = 1f
    }
    fun setColorAt(i: Int, hex: Int) {
        val cols = instanceColors ?: FloatArray(count * 3) { 1f }.also { instanceColors = it }
        cols[i * 3] = ((hex shr 16) and 255) / 255f; cols[i * 3 + 1] = ((hex shr 8) and 255) / 255f; cols[i * 3 + 2] = (hex and 255) / 255f
    }
    fun needsUpdate() { version++ }
}
