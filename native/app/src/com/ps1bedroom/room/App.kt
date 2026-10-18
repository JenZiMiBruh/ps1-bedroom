package com.ps1bedroom.room

import android.opengl.GLSurfaceView
import android.os.SystemClock
import com.ps1bedroom.room.engine.OrbitControls
import com.ps1bedroom.room.engine.PerspectiveCamera
import com.ps1bedroom.room.engine.Renderer
import com.ps1bedroom.room.engine.Scene
import com.ps1bedroom.room.engine.Vec3
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The app: scene, camera, controls and the per-frame loop (the HTML's animate()), running on the GL thread.
 * Input arrives from MainActivity through the thread-safe methods at the bottom.
 */
class App : GLSurfaceView.Renderer {
    val scene = Scene()
    // Default orbit view from inside the room, by the front wall, looking toward the desk and window
    val defaultCamPos = Vec3(0.6f, 3.15f, 3.1f)
    val defaultTarget = Vec3(0.1f, 2.0f, -1.2f)
    val camera = PerspectiveCamera(45f, 1f, 0.04f, 60f)
    val controls = OrbitControls(camera)
    val renderer = Renderer()
    private lateinit var room: Room

    // settings (the HTML defaults: Balanced quality, 240p, wobble 160, camera breathing 40%)
    var resShort = 240
    var jitterBase = 160f
    var swayAmount = 0.4f
    private val basePos = Vec3()
    private var startMs = 0L
    private var built = false

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        renderer.init()
        if (!built) {
            val tex = Textures()
            room = Room(scene, tex)
            room.build()
            renderer.post.rayLight = room.moonLight
            camera.position.copy(defaultCamPos)
            controls.target.copy(defaultTarget)
            controls.enableDamping = true
            controls.dampingFactor = 0.08f
            controls.maxPolarAngle = (Math.PI * 0.52).toFloat()
            controls.minDistance = 0.01f
            controls.maxDistance = 25.0f
            controls.update()
            startMs = SystemClock.uptimeMillis()
            built = true
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        renderer.resize(width, height, resShort, jitterBase)
        camera.aspect = width.toFloat() / max(1, height)
        camera.updateProjectionMatrix()
    }

    override fun onDrawFrame(gl: GL10?) {
        val t = (SystemClock.uptimeMillis() - startMs) / 1000f
        applyInput()
        controls.update()
        // Keep the free camera inside the room
        camera.position.x = max(Room.MIN_X + 0.2f, min(Room.MAX_X - 0.2f, camera.position.x))
        camera.position.y = max(Room.MIN_Y + 0.25f, min(Room.MAX_Y - 0.2f, camera.position.y))
        camera.position.z = max(Room.MIN_Z + 0.2f, min(Room.MAX_Z - 0.2f, camera.position.z))
        basePos.copy(camera.position)
        // Camera breathing (the HTML's orbit sway)
        if (swayAmount > 0.001f) {
            camera.position.x += sin(t * 0.55f) * 0.035f * swayAmount
            camera.position.y += cos(t * 0.42f) * 0.025f * swayAmount
        }
        renderer.post.time = t
        renderer.render(scene, camera)
        camera.position.copy(basePos)
    }

    // ---- input from the UI thread ----
    private val lock = Any()
    private var dragX = 0f; private var dragY = 0f; private var pinch = 1f; private var panX = 0f; private var panY = 0f; private var wheel = 0f
    @Volatile var viewHeight = 1f
    fun onDrag(dx: Float, dy: Float) = synchronized(lock) { dragX += dx; dragY += dy }
    fun onPinch(ratio: Float) = synchronized(lock) { pinch *= ratio }
    fun onPan(dx: Float, dy: Float) = synchronized(lock) { panX += dx; panY += dy }
    fun onWheel(d: Float) = synchronized(lock) { wheel += d }
    private fun applyInput() = synchronized(lock) {
        if (dragX != 0f || dragY != 0f) controls.rotateBy(dragX, dragY, viewHeight)
        if (pinch != 1f) controls.dollyBy(pinch)
        if (panX != 0f || panY != 0f) controls.panBy(panX, panY, viewHeight)
        if (wheel != 0f) controls.wheel(wheel)
        dragX = 0f; dragY = 0f; pinch = 1f; panX = 0f; panY = 0f; wheel = 0f
    }
}
