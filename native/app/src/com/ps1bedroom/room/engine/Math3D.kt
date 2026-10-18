package com.ps1bedroom.room.engine

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

// Small 3D maths kit that mirrors three.js r128 (same conventions: right-handed, column-major matrices,
// Euler order 'XYZ' by default), so positions, rotations and sizes port from the HTML version number for number.

class Vec3(@JvmField var x: Float = 0f, @JvmField var y: Float = 0f, @JvmField var z: Float = 0f) {
    fun set(x: Float, y: Float, z: Float): Vec3 { this.x = x; this.y = y; this.z = z; return this }
    fun set(x: Double, y: Double, z: Double): Vec3 = set(x.toFloat(), y.toFloat(), z.toFloat())
    fun copy(v: Vec3): Vec3 = set(v.x, v.y, v.z)
    fun clone() = Vec3(x, y, z)
    fun add(v: Vec3): Vec3 { x += v.x; y += v.y; z += v.z; return this }
    fun sub(v: Vec3): Vec3 { x -= v.x; y -= v.y; z -= v.z; return this }
    fun subVectors(a: Vec3, b: Vec3): Vec3 = set(a.x - b.x, a.y - b.y, a.z - b.z)
    fun multiplyScalar(s: Float): Vec3 { x *= s; y *= s; z *= s; return this }
    fun addScaledVector(v: Vec3, s: Float): Vec3 { x += v.x * s; y += v.y * s; z += v.z * s; return this }
    fun dot(v: Vec3) = x * v.x + y * v.y + z * v.z
    fun length() = sqrt(x * x + y * y + z * z)
    fun lengthSq() = x * x + y * y + z * z
    fun normalize(): Vec3 { val l = length(); if (l > 0f) multiplyScalar(1f / l); return this }
    fun crossVectors(a: Vec3, b: Vec3): Vec3 = set(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x)
    fun distanceTo(v: Vec3): Float { val dx = x - v.x; val dy = y - v.y; val dz = z - v.z; return sqrt(dx * dx + dy * dy + dz * dz) }
    fun lerp(v: Vec3, t: Float): Vec3 { x += (v.x - x) * t; y += (v.y - y) * t; z += (v.z - z) * t; return this }
    fun applyMatrix4(m: Mat4): Vec3 {
        val e = m.e
        val w = 1f / (e[3] * x + e[7] * y + e[11] * z + e[15])
        val nx = (e[0] * x + e[4] * y + e[8] * z + e[12]) * w
        val ny = (e[1] * x + e[5] * y + e[9] * z + e[13]) * w
        val nz = (e[2] * x + e[6] * y + e[10] * z + e[14]) * w
        return set(nx, ny, nz)
    }
    /** Direction only (ignores translation), like three's transformDirection (but not normalised). */
    fun applyMatrix4Dir(m: Mat4): Vec3 {
        val e = m.e
        return set(e[0] * x + e[4] * y + e[8] * z, e[1] * x + e[5] * y + e[9] * z, e[2] * x + e[6] * y + e[10] * z)
    }
    fun setFromMatrixPosition(m: Mat4): Vec3 = set(m.e[12], m.e[13], m.e[14])
    override fun toString() = "($x, $y, $z)"
}

/** Euler angles in radians. Order is 'XYZ' unless set (three.js default). */
class Euler(@JvmField var x: Float = 0f, @JvmField var y: Float = 0f, @JvmField var z: Float = 0f, @JvmField var order: String = "XYZ") {
    fun set(x: Float, y: Float, z: Float, order: String = this.order): Euler { this.x = x; this.y = y; this.z = z; this.order = order; return this }
    fun set(x: Double, y: Double, z: Double): Euler = set(x.toFloat(), y.toFloat(), z.toFloat())
    fun copy(o: Euler): Euler = set(o.x, o.y, o.z, o.order)
}

class Quat(@JvmField var x: Float = 0f, @JvmField var y: Float = 0f, @JvmField var z: Float = 0f, @JvmField var w: Float = 1f) {
    fun setFromEuler(e: Euler): Quat {
        val c1 = cos(e.x / 2); val c2 = cos(e.y / 2); val c3 = cos(e.z / 2)
        val s1 = sin(e.x / 2); val s2 = sin(e.y / 2); val s3 = sin(e.z / 2)
        when (e.order) {
            "XYZ" -> { x = s1 * c2 * c3 + c1 * s2 * s3; y = c1 * s2 * c3 - s1 * c2 * s3; z = c1 * c2 * s3 + s1 * s2 * c3; w = c1 * c2 * c3 - s1 * s2 * s3 }
            "YXZ" -> { x = s1 * c2 * c3 + c1 * s2 * s3; y = c1 * s2 * c3 - s1 * c2 * s3; z = c1 * c2 * s3 - s1 * s2 * c3; w = c1 * c2 * c3 + s1 * s2 * s3 }
            "ZXY" -> { x = s1 * c2 * c3 - c1 * s2 * s3; y = c1 * s2 * c3 + s1 * c2 * s3; z = c1 * c2 * s3 + s1 * s2 * c3; w = c1 * c2 * c3 - s1 * s2 * s3 }
            "ZYX" -> { x = s1 * c2 * c3 - c1 * s2 * s3; y = c1 * s2 * c3 + s1 * c2 * s3; z = c1 * c2 * s3 - s1 * s2 * c3; w = c1 * c2 * c3 + s1 * s2 * s3 }
            "YZX" -> { x = s1 * c2 * c3 + c1 * s2 * s3; y = c1 * s2 * c3 + s1 * c2 * s3; z = c1 * c2 * s3 - s1 * s2 * c3; w = c1 * c2 * c3 - s1 * s2 * s3 }
            "XZY" -> { x = s1 * c2 * c3 - c1 * s2 * s3; y = c1 * s2 * c3 - s1 * c2 * s3; z = c1 * c2 * s3 + s1 * s2 * c3; w = c1 * c2 * c3 + s1 * s2 * s3 }
        }
        return this
    }
}

/** 4x4 matrix, column-major like three.js (e[12..14] is the translation). */
class Mat4 {
    @JvmField val e = FloatArray(16).also { it[0] = 1f; it[5] = 1f; it[10] = 1f; it[15] = 1f }

    fun identity(): Mat4 { e.fill(0f); e[0] = 1f; e[5] = 1f; e[10] = 1f; e[15] = 1f; return this }
    fun copy(m: Mat4): Mat4 { System.arraycopy(m.e, 0, e, 0, 16); return this }

    fun compose(p: Vec3, q: Quat, s: Vec3): Mat4 {
        val x2 = q.x + q.x; val y2 = q.y + q.y; val z2 = q.z + q.z
        val xx = q.x * x2; val xy = q.x * y2; val xz = q.x * z2
        val yy = q.y * y2; val yz = q.y * z2; val zz = q.z * z2
        val wx = q.w * x2; val wy = q.w * y2; val wz = q.w * z2
        e[0] = (1 - (yy + zz)) * s.x; e[1] = (xy + wz) * s.x; e[2] = (xz - wy) * s.x; e[3] = 0f
        e[4] = (xy - wz) * s.y; e[5] = (1 - (xx + zz)) * s.y; e[6] = (yz + wx) * s.y; e[7] = 0f
        e[8] = (xz + wy) * s.z; e[9] = (yz - wx) * s.z; e[10] = (1 - (xx + yy)) * s.z; e[11] = 0f
        e[12] = p.x; e[13] = p.y; e[14] = p.z; e[15] = 1f
        return this
    }

    /** this = a * b */
    fun multiplyMatrices(a: Mat4, b: Mat4): Mat4 {
        val ae = a.e; val be = b.e
        val r = FloatArray(16)
        for (c in 0 until 4) for (row in 0 until 4) {
            r[c * 4 + row] = ae[row] * be[c * 4] + ae[4 + row] * be[c * 4 + 1] + ae[8 + row] * be[c * 4 + 2] + ae[12 + row] * be[c * 4 + 3]
        }
        System.arraycopy(r, 0, e, 0, 16)
        return this
    }

    fun invert(src: Mat4 = this): Mat4 {
        val m = src.e
        val inv = FloatArray(16)
        inv[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15] + m[9] * m[7] * m[14] + m[13] * m[6] * m[11] - m[13] * m[7] * m[10]
        inv[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15] - m[8] * m[7] * m[14] - m[12] * m[6] * m[11] + m[12] * m[7] * m[10]
        inv[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15] + m[8] * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9]
        inv[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14] - m[8] * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9]
        inv[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15] - m[9] * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10]
        inv[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15] + m[8] * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10]
        inv[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15] - m[8] * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9]
        inv[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14] + m[8] * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9]
        inv[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15] + m[5] * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6]
        inv[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15] - m[4] * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6]
        inv[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15] + m[4] * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5]
        inv[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14] - m[4] * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5]
        inv[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11] - m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6]
        inv[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11] + m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6]
        inv[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11] - m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5]
        inv[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10] + m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5]
        var det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12]
        if (det == 0f) return identity()
        det = 1f / det
        for (i in 0 until 16) e[i] = inv[i] * det
        return this
    }

    fun makePerspective(fovDeg: Float, aspect: Float, near: Float, far: Float): Mat4 {
        // three.js PerspectiveCamera.updateProjectionMatrix (zoom 1, no view offset)
        val top = near * tan(Math.toRadians(0.5 * fovDeg)).toFloat()
        val height = 2 * top
        val width = aspect * height
        val left = -0.5f * width
        val right = left + width
        val bottom = top - height
        val x = 2 * near / (right - left); val y = 2 * near / (top - bottom)
        val a = (right + left) / (right - left); val b = (top + bottom) / (top - bottom)
        val c = -(far + near) / (far - near); val d = -2 * far * near / (far - near)
        e.fill(0f)
        e[0] = x; e[8] = a; e[5] = y; e[9] = b; e[10] = c; e[14] = d; e[11] = -1f
        return this
    }

    fun makeOrthographic(left: Float, right: Float, top: Float, bottom: Float, near: Float, far: Float): Mat4 {
        val w = 1f / (right - left); val h = 1f / (top - bottom); val p = 1f / (far - near)
        e.fill(0f)
        e[0] = 2 * w; e[12] = -(right + left) * w
        e[5] = 2 * h; e[13] = -(top + bottom) * h
        e[10] = -2 * p; e[14] = -(far + near) * p
        e[15] = 1f
        return this
    }

    /** Rotation part that makes an object at `eye` face `target` (three's Matrix4.lookAt; cameras look down -Z). */
    fun lookAt(eye: Vec3, target: Vec3, up: Vec3): Mat4 {
        val z = Vec3().subVectors(eye, target)
        if (z.lengthSq() == 0f) z.z = 1f
        z.normalize()
        val x = Vec3().crossVectors(up, z)
        if (x.lengthSq() == 0f) {
            if (abs(up.z) == 1f) z.x += 0.0001f else z.z += 0.0001f
            z.normalize(); x.crossVectors(up, z)
        }
        x.normalize()
        val y = Vec3().crossVectors(z, x)
        e[0] = x.x; e[4] = y.x; e[8] = z.x
        e[1] = x.y; e[5] = y.y; e[9] = z.y
        e[2] = x.z; e[6] = y.z; e[10] = z.z
        return this
    }

    /** Normal matrix (upper 3x3 inverse-transpose) of this, written into a 9-float array. */
    fun normalMatrixInto(out: FloatArray) {
        val inv = Mat4().invert(this).e
        // transpose of the inverse, upper 3x3, column-major
        out[0] = inv[0]; out[1] = inv[4]; out[2] = inv[8]
        out[3] = inv[1]; out[4] = inv[5]; out[5] = inv[9]
        out[6] = inv[2]; out[7] = inv[6]; out[8] = inv[10]
    }

    /** Euler (XYZ) from the rotation part, three's Euler.setFromRotationMatrix. */
    fun toEulerXYZ(out: Euler): Euler {
        val m11 = e[0]; val m12 = e[4]; val m13 = e[8]
        val m22 = e[5]; val m23 = e[9]; val m32 = e[6]; val m33 = e[10]
        out.y = asin(m13.coerceIn(-1f, 1f))
        if (abs(m13) < 0.9999999f) { out.x = atan2(-m23, m33); out.z = atan2(-m12, m11) } else { out.x = atan2(m32, m22); out.z = 0f }
        out.order = "XYZ"
        return out
    }
}

/** RGB colour in 0..1 (three.js Color, linear, no colour management, like r128's defaults). */
class Color(@JvmField var r: Float = 1f, @JvmField var g: Float = 1f, @JvmField var b: Float = 1f) {
    constructor(hex: Int) : this() { setHex(hex) }
    fun setHex(hex: Int): Color { r = ((hex shr 16) and 255) / 255f; g = ((hex shr 8) and 255) / 255f; b = (hex and 255) / 255f; return this }
    fun setRGB(r: Float, g: Float, b: Float): Color { this.r = r; this.g = g; this.b = b; return this }
    fun copy(c: Color): Color { r = c.r; g = c.g; b = c.b; return this }
    fun getHex(): Int = ((r * 255).toInt().coerceIn(0, 255) shl 16) or ((g * 255).toInt().coerceIn(0, 255) shl 8) or (b * 255).toInt().coerceIn(0, 255)
    /** CSS colour strings used by the HTML: '#rrggbb', '#rgb', 'rgb(r,g,b)'. */
    fun setStyle(s: String): Color {
        val t = s.trim()
        if (t.startsWith("#")) {
            val h = t.substring(1)
            return if (h.length == 3) setHex(Integer.parseInt("" + h[0] + h[0] + h[1] + h[1] + h[2] + h[2], 16)) else setHex(Integer.parseInt(h.substring(0, 6), 16))
        }
        val m = Regex("rgba?\\(([^)]*)\\)").find(t)
        if (m != null) {
            val p = m.groupValues[1].split(",").map { it.trim().toFloat() }
            return setRGB(p[0] / 255f, p[1] / 255f, p[2] / 255f)
        }
        return this
    }
    fun clone() = Color(r, g, b)
}

const val PI_F = Math.PI.toFloat()
fun smoothstep(e0: Float, e1: Float, x: Float): Float { val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f); return t * t * (3 - 2 * t) }
