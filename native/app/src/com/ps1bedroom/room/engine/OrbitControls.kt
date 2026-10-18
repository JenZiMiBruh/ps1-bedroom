package com.ps1bedroom.room.engine

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * three.js r128 OrbitControls (the parts the app uses): one finger / mouse drag orbits, pinch dollies,
 * two fingers pan, damping. Same maths so the camera feels the same.
 */
class OrbitControls(private val camera: PerspectiveCamera) {
    @JvmField val target = Vec3()
    @JvmField var enableDamping = true
    @JvmField var dampingFactor = 0.08f
    @JvmField var minPolarAngle = 0f
    @JvmField var maxPolarAngle = PI.toFloat()
    @JvmField var minDistance = 0f
    @JvmField var maxDistance = Float.MAX_VALUE
    @JvmField var rotateSpeed = 1f
    @JvmField var zoomSpeed = 1f
    @JvmField var panSpeed = 1f
    @JvmField var enabled = true

    private var thetaD = 0f; private var phiD = 0f
    private var scale = 1f
    private val panOffset = Vec3()
    private var theta = 0f; private var phi = 0f; private var radius = 1f

    /** Called by the input layer with the drag since the last event, in screen pixels. */
    fun rotateBy(dx: Float, dy: Float, viewHeight: Float) {
        thetaD -= 2 * PI.toFloat() * dx / viewHeight * rotateSpeed
        phiD -= 2 * PI.toFloat() * dy / viewHeight * rotateSpeed
    }
    /** Pinch: ratio = new finger distance / old finger distance (>1 zooms in). */
    fun dollyBy(ratio: Float) { if (ratio > 0) scale /= ratio.pow(zoomSpeed) }
    /** Mouse wheel ticks (positive = away). */
    fun wheel(deltaY: Float) { val s = 0.95f.pow(zoomSpeed); if (deltaY < 0) scale *= s else if (deltaY > 0) scale /= s }
    /** Two-finger pan in screen pixels (three's panLeft / panUp with a perspective camera). */
    fun panBy(dx: Float, dy: Float, viewHeight: Float) {
        val offset = Vec3().subVectors(camera.position, target)
        var targetDistance = offset.length()
        targetDistance *= kotlin.math.tan(Math.toRadians(camera.fov / 2.0)).toFloat()
        val e = camera.matrix.e
        val left = Vec3(e[0], e[1], e[2]).multiplyScalar(-(2 * dx * targetDistance / viewHeight) * panSpeed)
        val up = Vec3(e[4], e[5], e[6]).multiplyScalar((2 * dy * targetDistance / viewHeight) * panSpeed)
        panOffset.add(left).add(up)
    }

    fun update() {
        val offset = Vec3().subVectors(camera.position, target)
        // to spherical (y up)
        radius = offset.length()
        if (radius == 0f) { theta = 0f; phi = 0f } else {
            theta = atan2(offset.x, offset.z)
            phi = acos((offset.y / radius).coerceIn(-1f, 1f))
        }
        if (enableDamping) { theta += thetaD * dampingFactor; phi += phiD * dampingFactor } else { theta += thetaD; phi += phiD }
        phi = max(minPolarAngle, min(maxPolarAngle, phi))
        phi = max(1e-6f, min(PI.toFloat() - 1e-6f, phi))
        radius *= scale
        radius = max(minDistance, min(maxDistance, radius))
        if (enableDamping) target.addScaledVector(panOffset, dampingFactor) else target.add(panOffset)
        val sinPhi = sin(phi)
        offset.set(radius * sinPhi * sin(theta), radius * cos(phi), radius * sinPhi * cos(theta))
        camera.position.copy(target).add(offset)
        camera.lookAtTarget(target)
        if (enableDamping) {
            thetaD *= 1 - dampingFactor; phiD *= 1 - dampingFactor
            panOffset.multiplyScalar(1 - dampingFactor)
        } else { thetaD = 0f; phiD = 0f; panOffset.set(0f, 0f, 0f) }
        scale = 1f
    }

    fun distance() = sqrt(Vec3().subVectors(camera.position, target).lengthSq())
}
