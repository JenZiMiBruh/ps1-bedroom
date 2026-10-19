package com.ps1bedroom.room

import com.ps1bedroom.room.engine.Object3D
import com.ps1bedroom.room.engine.PerspectiveCamera
import com.ps1bedroom.room.engine.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * HTML 10786-10912 + the camera half of animate(): the player posture & movement state machine
 * ('orbit' | 'walk' | 'chair' | 'bed'). Walk with the on-screen joystick, sit in the rolling chair (it rolls between
 * the TV, the desk and the window), or lie propped up on the pillows; dragging looks around, pinching zooms.
 */
class Player(private val camera: PerspectiveCamera, private val toast: (String) -> Unit, private val onModeChanged: () -> Unit) {
    /** The office chair (set once the bedroom is built). */
    lateinit var chairGroup: Object3D
    var mode = "orbit"; private set
    // The rolling chair has three spots: facing the TV, pulled up to the desk, turned toward the window
    class Spot(val name: String, val label: String, val pos: Vec3, val rot: Float, val pitch: Float)
    val CHAIR_SPOTS = listOf(
        Spot("TV", "WATCHING TV", Vec3(-0.25f, 0f, 0.9f), -1.62f, -0.34f),
        Spot("DESK", "SEATED AT PC DESK", Vec3(-1.26f, 0f, -0.58f), -2.82f, -0.02f),
        Spot("WINDOW", "GAZING OUT THE WINDOW", Vec3(0.8f, 0f, -1.75f), PI.toFloat(), 0.22f)
    )
    var chairSpot = 0; private set
    // While seated or lying down, dragging looks around relative to where the seat / bed faces
    private var seatLookYaw = 0f
    private var seatLookPitch = 0f
    private val BED_EYE = Vec3(2.85f, 1.28f, -1.9f)   // propped up on the pillows
    private val BED_YAW = PI.toFloat()                // looking down the bed toward the door
    private val BED_PITCH = 0.2f
    private fun shortAngle(from: Float, to: Float): Float {
        var d = (to - from) % (PI.toFloat() * 2)
        if (d > PI) d -= PI.toFloat() * 2
        if (d < -PI) d += PI.toFloat() * 2
        return d
    }
    private val walkPos = Vec3(-0.2f, 2.35f, 1.65f)
    private var lookYaw = 0.15f
    private var lookPitch = -0.06f
    /** -1..1 from the on-screen joystick (y = forward), written by the UI thread. */
    @Volatile var stickX = 0f
    @Volatile var stickY = 0f
    private var stepTimer = 0f
    private val seatEye = Vec3()
    private val lookTarget = Vec3()
    /** Things to walk around instead of through (TV cart, chair, Jasper, heater) with their radius. */
    var obstacles: List<Pair<Object3D, Float>> = emptyList()

    fun postureLabel() = when (mode) { "walk" -> "WALKING (1ST PERSON)"; "chair" -> CHAIR_SPOTS[chairSpot].label; "bed" -> "LAYING ON BED"; else -> "FREE CAM" }
    fun pullChairLabel() = "CHAIR → ${CHAIR_SPOTS[(chairSpot + 1) % CHAIR_SPOTS.size].name}"

    /** setPlayerMode: [resetOrbit] puts the free camera back at its default spot. */
    fun setMode(m: String, resetOrbit: () -> Unit) {
        val prevMode = mode
        mode = m
        camera.fov = 45f
        camera.updateProjectionMatrix()
        when (m) {
            "orbit" -> {
                camera.up.set(0f, 1f, 0f)
                resetOrbit()
                toast("🎥 FREE CAM: Drag to orbit, pinch to zoom")
            }
            "walk" -> {
                camera.up.set(0f, 1f, 0f)
                // Stand up next to wherever you were sitting / lying
                if (prevMode == "bed") { walkPos.set(1.2f, 2.35f, -0.9f); lookYaw = (-PI / 2).toFloat() }
                else if (prevMode == "chair") {
                    val spot = CHAIR_SPOTS[chairSpot]
                    walkPos.set(spot.pos.x + sin(spot.rot) * -0.75f, 2.35f, spot.pos.z + cos(spot.rot) * -0.75f)
                    lookYaw = spot.rot + PI.toFloat()
                } else lookYaw = 0.25f
                lookPitch = -0.05f
                toast("🚶 WALK MODE: Use the joystick to walk, drag the screen to look.")
            }
            "chair" -> {
                camera.up.set(0f, 1f, 0f)
                seatLookYaw = 0f; seatLookPitch = 0f
                toast("🪑 SEATED: Tap \"CHAIR →\" to roll between the TV, the desk and the window. Drag to look.")
            }
            "bed" -> {
                camera.up.set(0f, 1f, 0f)
                seatLookYaw = 0f; seatLookPitch = 0f
                toast("🛏️ LYING IN BED: Propped up on the pillows looking toward the door. Drag to look around.")
            }
        }
        onModeChanged()
    }

    // Rolls the chair to the next (+1) or previous (-1) spot: TV -> DESK -> WINDOW
    fun moveChair(step: Int, resetOrbit: () -> Unit) {
        chairSpot = (chairSpot + step + CHAIR_SPOTS.size) % CHAIR_SPOTS.size
        seatLookYaw = 0f; seatLookPitch = 0f
        if (mode != "chair") setMode("chair", resetOrbit) else onModeChanged()
        toast("🪑 Rolled the chair to the ${CHAIR_SPOTS[chairSpot].name.lowercase()}.")
    }

    /** One-finger drag in walk / chair / bed: look around. */
    fun look(dx: Float, dy: Float) {
        if (mode == "chair" || mode == "bed") {
            // Seated / lying: look around relative to where the seat or bed faces (like turning your head)
            seatLookYaw = max(-1.7f, min(1.7f, seatLookYaw - dx * 0.0045f))
            seatLookPitch = max(-0.9f, min(0.9f, seatLookPitch - dy * 0.0045f))
        } else {
            lookYaw -= dx * 0.0045f
            lookPitch = max(-1.15f, min(1.15f, lookPitch - dy * 0.0045f))
        }
    }
    /** Two-finger pinch in walk / chair / bed: optical zoom (field of view). */
    fun pinchZoom(ratio: Float) {
        camera.fov = max(8f, min(75f, camera.fov / ratio))
        camera.updateProjectionMatrix()
    }

    /** The chair rolls to its spot every frame, in every mode (animate()). */
    fun updateChair(dt: Float): Boolean {
        val spot = CHAIR_SPOTS[chairSpot]
        val chairTurn = shortAngle(chairGroup.rotation.y, spot.rot)
        val p = chairGroup.position
        val dx = spot.pos.x - p.x; val dz = spot.pos.z - p.z
        if (dx * dx + dz * dz > 0.0002f || abs(chairTurn) > 0.002f) {
            val k = min(1f, dt * 4.5f)
            p.x += dx * k; p.y += (spot.pos.y - p.y) * k; p.z += dz * k
            chairGroup.rotation.y += chairTurn * k
            return true
        }
        return false
    }

    /** Places the camera for walk / chair / bed (orbit is handled by the controls). */
    fun updateCamera(t: Float, dt: Float, swayAmount: Float) {
        val forwardX = -sin(lookYaw); val forwardZ = -cos(lookYaw)
        val rightX = cos(lookYaw); val rightZ = -sin(lookYaw)
        when (mode) {
            "walk" -> {
                val moveSpeed = 2.15f * dt
                var moved = false
                val sx = stickX; val sy = stickY
                // On-screen touch joystick (analog)
                if (sx != 0f || sy != 0f) {
                    walkPos.x += (forwardX * sy + rightX * sx) * moveSpeed
                    walkPos.z += (forwardZ * sy + rightZ * sx) * moveSpeed
                    moved = true
                }
                // Keep the player inside the room and clear of furniture (the hallway door stays shut for now)
                walkPos.z = max(Room.MIN_Z + 0.35f, min(if (walkPos.z > 3.2f) 7.0f else Room.MAX_Z - 0.3f, walkPos.z))
                if (walkPos.z > Room.MAX_Z - 0.3f) walkPos.x = max(2.2f, min(3.5f, walkPos.x)) // in the doorway / hall
                else walkPos.x = max(Room.MIN_X + 0.35f, min(Room.MAX_X - 0.35f, walkPos.x))
                if (walkPos.x > 1.45f && walkPos.z < 1.6f) walkPos.x = 1.45f // Bed collision boundary
                if (walkPos.x < -0.15f && walkPos.z < -0.55f) walkPos.z = -0.55f // Desk collision boundary
                if (walkPos.x < -2.85f && walkPos.z > 1.55f && walkPos.z < 3.0f) walkPos.x = -2.85f // Dresser
                if (walkPos.x > 0.75f && walkPos.x < 1.5f && walkPos.z < -2.2f) walkPos.z = -2.2f // Nightstand
                // Push out of the TV cart, the office chair, Jasper and the heater instead of walking through them
                for ((o, r) in obstacles) {
                    val dx = walkPos.x - o.position.x; val dz = walkPos.z - o.position.z
                    val d = hypot(dx, dz)
                    if (d < r && d > 0.0001f) { walkPos.x = o.position.x + (dx / d) * r; walkPos.z = o.position.z + (dz / d) * r }
                }
                var bobY = 0f
                if (moved) {
                    stepTimer += dt * 7.5f
                    bobY = abs(sin(stepTimer)) * 0.055f
                    // (the footstep sound comes with the audio phase)
                }
                camera.up.set(0f, 1f, 0f)
                camera.position.set(walkPos.x + sin(t * 0.55f) * 0.015f * swayAmount, walkPos.y + bobY + cos(t * 0.42f) * 0.015f * swayAmount, walkPos.z)
            }
            "chair" -> {
                // Seated head height over the seat, facing wherever the chair faces (follows it as it rolls and turns)
                chairGroup.updateMatrixWorld()
                seatEye.set(0f, 2.12f, -0.06f).applyMatrix4(chairGroup.matrixWorld)
                camera.up.set(0f, 1f, 0f)
                camera.position.set(seatEye.x + sin(t * 0.55f) * 0.01f * swayAmount, seatEye.y + cos(t * 0.42f) * 0.01f * swayAmount, seatEye.z)
                lookYaw = chairGroup.rotation.y + PI.toFloat() + seatLookYaw
                lookPitch = CHAIR_SPOTS[chairSpot].pitch + seatLookPitch
            }
            "bed" -> {
                // Propped up on the pillows, looking down the length of the bed (gentle breathing rise and fall)
                camera.up.set(0f, 1f, 0f)
                camera.position.set(BED_EYE.x + sin(t * 0.45f) * 0.008f * swayAmount, BED_EYE.y + sin(t * 0.9f) * 0.012f * swayAmount, BED_EYE.z)
                lookYaw = BED_YAW + seatLookYaw
                lookPitch = BED_PITCH + seatLookPitch
            }
        }
        lookTarget.set(camera.position.x - sin(lookYaw) * cos(lookPitch), camera.position.y + sin(lookPitch), camera.position.z - cos(lookYaw) * cos(lookPitch))
        camera.lookAtTarget(lookTarget)
    }
}
