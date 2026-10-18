package com.ps1bedroom.room.engine

import android.opengl.GLES30.*
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Draws a Scene the way the HTML does with three.js r128:
 *  1. shadow maps for the shadow-casting spot lights (only when flagged, like shadowMap.autoUpdate = false)
 *  2. the scene at PS1 resolution into a colour + depth render target
 *  3. the post pass (AO, light rays, grade, dither) into a second low-res target
 *  4. that target stretched to the screen with nearest filtering (the CSS "image-rendering: pixelated")
 */
class Renderer {
    // ---- Programs ----
    private var ps1Prog = 0
    private var depthProg = 0
    private var postProg = 0
    private var blitProg = 0
    private val loc = HashMap<String, Int>()
    private var quadVao = 0

    // ---- Targets ----
    var internalW = 320; private set
    var internalH = 240; private set
    var screenW = 1; private set
    var screenH = 1; private set
    private var sceneFbo = 0; private var sceneColor = 0; private var sceneDepth = 0
    private var postFbo = 0; private var postColor = 0
    private class ShadowTarget(val fbo: Int, val depth: Int, val size: Int)
    private val shadowTargets = HashMap<SpotLight, ShadowTarget>()
    private var whiteTex = 0

    /** Set by the app: re-render shadow maps on the next frame (three's shadowMap.needsUpdate). */
    @JvmField var shadowsNeedUpdate = true
    @JvmField val ps1Uniforms = Ps1Uniforms()
    @JvmField val post = PostUniforms()

    class Ps1Uniforms { @JvmField val jitterGrid = floatArrayOf(160f, 120f); @JvmField var affineStrength = 0.85f }
    class PostUniforms {
        @JvmField var ditherStrength = 1f
        @JvmField var volumetricStrength = 1f
        @JvmField var aoStrength = 0.8f
        @JvmField var marchSteps = 14
        @JvmField var fanLightOn = 0f
        @JvmField val fanLightPos = Vec3(0.6f, 4.30f, -0.4f)
        @JvmField val gradeTint = Vec3(1f, 1f, 1f)
        @JvmField var gradeSat = 1f
        @JvmField var gradeContrast = 1f
        @JvmField var vignette = 0f
        @JvmField var time = 0f
        /** The spot light that drives the light rays (moonLight in the HTML). */
        @JvmField var rayLight: SpotLight? = null
        @JvmField val lightColor = Color(0x5b86ff)
        @JvmField var lightCosAngle = cos(0.66f)
        @JvmField var lightPenumbraCos = cos(0.66f * 0.88f)
    }

    fun init() {
        ps1Prog = program(Shaders.PS1_VERT, Shaders.PS1_FRAG)
        depthProg = program(Shaders.DEPTH_VERT, Shaders.DEPTH_FRAG)
        postProg = program(Shaders.QUAD_VERT, Shaders.POST_FRAG)
        blitProg = program(Shaders.QUAD_VERT, Shaders.BLIT_FRAG)
        // full-screen triangle pair
        val vao = IntArray(1); glGenVertexArrays(1, vao, 0); quadVao = vao[0]
        glBindVertexArray(quadVao)
        val vb = IntArray(1); glGenBuffers(1, vb, 0); glBindBuffer(GL_ARRAY_BUFFER, vb[0])
        val q = floatArrayOf(-1f, -1f, 1f, -1f, 1f, 1f, -1f, -1f, 1f, 1f, -1f, 1f)
        glBufferData(GL_ARRAY_BUFFER, q.size * 4, floats(q), GL_STATIC_DRAW)
        glEnableVertexAttribArray(0); glVertexAttribPointer(0, 2, GL_FLOAT, false, 0, 0)
        glBindVertexArray(0)
        val t = IntArray(1); glGenTextures(1, t, 0); whiteTex = t[0]
        glBindTexture(GL_TEXTURE_2D, whiteTex)
        val px = ByteBuffer.allocateDirect(4).put(byteArrayOf(-1, -1, -1, -1)); px.position(0)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, px)
        nearest(GL_CLAMP_TO_EDGE)
        // geometry / textures from a previous GL context are gone
        uploadedGeometries.forEach { it.uploaded = false }; uploadedGeometries.clear()
        uploadedTextures.forEach { it.glId = 0; it.uploadedVersion = 0 }; uploadedTextures.clear()
        shadowTargets.clear()
        shadowsNeedUpdate = true
    }

    /** The HTML's resizeRenderTargets: the short side = resolution setting, long side follows the screen's shape. */
    fun resize(w: Int, h: Int, resShort: Int, jitterBase: Float) {
        screenW = max(1, w); screenH = max(1, h)
        val aspect = screenW.toFloat() / screenH
        if (aspect >= 1) { internalH = resShort; internalW = (resShort * aspect).roundToInt() } else { internalW = resShort; internalH = (resShort / aspect).roundToInt() }
        val g = jitterBase * 0.75f
        ps1Uniforms.jitterGrid[0] = if (aspect >= 1) g * aspect else g
        ps1Uniforms.jitterGrid[1] = if (aspect >= 1) g else g / aspect
        deleteTargets()
        // scene target: colour + depth texture
        val f = IntArray(1); val t = IntArray(2)
        glGenFramebuffers(1, f, 0); sceneFbo = f[0]
        glGenTextures(2, t, 0); sceneColor = t[0]; sceneDepth = t[1]
        glBindTexture(GL_TEXTURE_2D, sceneColor)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, internalW, internalH, 0, GL_RGBA, GL_UNSIGNED_BYTE, null); nearest(GL_CLAMP_TO_EDGE)
        glBindTexture(GL_TEXTURE_2D, sceneDepth)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, internalW, internalH, 0, GL_DEPTH_COMPONENT, GL_UNSIGNED_INT, null); nearest(GL_CLAMP_TO_EDGE)
        glBindFramebuffer(GL_FRAMEBUFFER, sceneFbo)
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, sceneColor, 0)
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, sceneDepth, 0)
        // post target
        glGenFramebuffers(1, f, 0); postFbo = f[0]
        glGenTextures(1, t, 0); postColor = t[0]
        glBindTexture(GL_TEXTURE_2D, postColor)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, internalW, internalH, 0, GL_RGBA, GL_UNSIGNED_BYTE, null); nearest(GL_CLAMP_TO_EDGE)
        glBindFramebuffer(GL_FRAMEBUFFER, postFbo)
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, postColor, 0)
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
        shadowsNeedUpdate = true
    }

    private fun deleteTargets() {
        if (sceneFbo != 0) { glDeleteFramebuffers(2, intArrayOf(sceneFbo, postFbo), 0); glDeleteTextures(3, intArrayOf(sceneColor, sceneDepth, postColor), 0) }
        sceneFbo = 0; postFbo = 0
    }

    // ---------------------------------------------------------------- frame
    private val opaque = ArrayList<DrawItem>()
    private val transparent = ArrayList<DrawItem>()
    private class DrawItem(val mesh: Mesh, val mat: Material, val group: IntArray?, var z: Float)
    private val spots = ArrayList<SpotLight>()
    private val points = ArrayList<PointLight>()
    private val hemis = ArrayList<HemisphereLight>()
    private var ambient = Color(0)

    fun render(scene: Scene, camera: PerspectiveCamera) {
        scene.updateMatrixWorld()
        camera.updateCameraWorld()
        collectLights(scene)
        if (shadowsNeedUpdate) { renderShadows(scene); shadowsNeedUpdate = false }

        // 2. scene -> low-res target
        glBindFramebuffer(GL_FRAMEBUFFER, sceneFbo)
        glViewport(0, 0, internalW, internalH)
        glClearColor(scene.background.r, scene.background.g, scene.background.b, 1f)
        glDepthMask(true)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        buildDrawLists(scene, camera)
        glUseProgram(ps1Prog)
        setFrameUniforms(scene, camera)
        glEnable(GL_DEPTH_TEST); glDepthFunc(GL_LEQUAL)
        glDisable(GL_BLEND)
        for (d in opaque) drawItem(d, camera)
        glEnable(GL_BLEND); glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
        for (d in transparent) drawItem(d, camera)
        glDisable(GL_BLEND); glDepthMask(true); glDisable(GL_POLYGON_OFFSET_FILL)

        // 3. post pass -> low-res target
        glBindFramebuffer(GL_FRAMEBUFFER, postFbo)
        glViewport(0, 0, internalW, internalH)
        glDisable(GL_DEPTH_TEST); glDisable(GL_CULL_FACE)
        glUseProgram(postProg)
        setPostUniforms(camera)
        glBindVertexArray(quadVao); glDrawArrays(GL_TRIANGLES, 0, 6)

        // 4. stretch to the screen, pixel-sharp
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
        glViewport(0, 0, screenW, screenH)
        glUseProgram(blitProg)
        glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, postColor)
        glUniform1i(u(blitProg, "tex"), 0)
        glDrawArrays(GL_TRIANGLES, 0, 6)
        glBindVertexArray(0)
    }

    private fun collectLights(scene: Scene) {
        spots.clear(); points.clear(); hemis.clear()
        var ar = 0f; var ag = 0f; var ab = 0f
        scene.traverseVisible { o ->
            when (o) {
                is AmbientLight -> { ar += o.color.r * o.intensity; ag += o.color.g * o.intensity; ab += o.color.b * o.intensity }
                is HemisphereLight -> if (hemis.size < Shaders.MAX_HEMI) hemis += o
                is PointLight -> if (points.size < Shaders.MAX_POINT) points += o
                is SpotLight -> if (spots.size < Shaders.MAX_SPOT) spots += o
                else -> {}
            }
        }
        ambient.setRGB(ar, ag, ab)
        // shadow casters first so they get shadow slots 0 and 1
        spots.sortBy { if (it.castShadow) 0 else 1 }
    }

    // ---- 1. Shadows (three's SpotLightShadow: fov = 2*angle, aspect 1, far = light.distance) ----
    private val tmpM = Mat4(); private val tmpM2 = Mat4()
    private fun renderShadows(scene: Scene) {
        var slot = 0
        for (s in spots) {
            if (!s.castShadow || slot > 1) continue
            slot++
            val sh = s.shadow
            val target = shadowTargets.getOrPut(s) { makeShadowTarget(sh.mapSize) }
            val cam = sh.camera
            cam.fov = Math.toDegrees(2.0 * s.angle).toFloat(); cam.aspect = 1f
            cam.near = sh.near; cam.far = if (s.distance > 0) s.distance else sh.far
            cam.updateProjectionMatrix()
            s.target.updateMatrixWorld()
            val lp = Vec3().setFromMatrixPosition(s.matrixWorld)
            val tp = Vec3().setFromMatrixPosition(s.target.matrixWorld)
            cam.position.copy(lp); cam.lookAtTarget(tp); cam.updateCameraWorld()
            // matrix = bias * proj * view
            val bias = Mat4(); bias.e.fill(0f); bias.e[0] = 0.5f; bias.e[5] = 0.5f; bias.e[10] = 0.5f; bias.e[12] = 0.5f; bias.e[13] = 0.5f; bias.e[14] = 0.5f; bias.e[15] = 1f
            val pv = Mat4().multiplyMatrices(cam.projectionMatrix, cam.matrixWorldInverse)
            sh.matrix.multiplyMatrices(bias, pv)
            glBindFramebuffer(GL_FRAMEBUFFER, target.fbo)
            glViewport(0, 0, target.size, target.size)
            glDepthMask(true); glClear(GL_DEPTH_BUFFER_BIT)
            glEnable(GL_DEPTH_TEST); glDepthFunc(GL_LEQUAL)
            glColorMask(false, false, false, false)
            glEnable(GL_CULL_FACE); glCullFace(GL_BACK)
            glUseProgram(depthProg)
            val mvpLoc = u(depthProg, "mvp")
            scene.traverseVisible { o ->
                if (o is Mesh && o.castShadow && o.material.visible) {
                    upload(o.geometry)
                    tmpM.multiplyMatrices(pv, o.matrixWorld)
                    glUniformMatrix4fv(mvpLoc, 1, false, tmpM.e, 0)
                    // makePS1Material sets shadowSide FrontSide: render front faces
                    glBindVertexArray(o.geometry.vao)
                    drawGeometry(o.geometry, null)
                }
            }
            glColorMask(true, true, true, true)
        }
        glBindVertexArray(0)
    }

    private fun makeShadowTarget(size: Int): ShadowTarget {
        val f = IntArray(1); val t = IntArray(1)
        glGenFramebuffers(1, f, 0); glGenTextures(1, t, 0)
        glBindTexture(GL_TEXTURE_2D, t[0])
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, size, size, 0, GL_DEPTH_COMPONENT, GL_UNSIGNED_INT, null)
        nearest(GL_CLAMP_TO_EDGE)
        glBindFramebuffer(GL_FRAMEBUFFER, f[0])
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, t[0], 0)
        glDrawBuffers(1, intArrayOf(GL_NONE), 0); glReadBuffer(GL_NONE)
        return ShadowTarget(f[0], t[0], size)
    }

    // ---- 2. Scene ----
    private val camPos = Vec3()
    private val tmpV = Vec3()
    private fun buildDrawLists(scene: Scene, camera: PerspectiveCamera) {
        opaque.clear(); transparent.clear()
        camPos.setFromMatrixPosition(camera.matrixWorld)
        val vp = tmpM2.multiplyMatrices(camera.projectionMatrix, camera.matrixWorldInverse)
        scene.traverseVisible { o ->
            if (o !is Mesh) return@traverseVisible
            val g = o.geometry
            if (g.boundingRadius < 0) g.computeBoundingSphere()
            if (o.frustumCulled && !inFrustum(o, vp)) return@traverseVisible
            tmpV.copy(g.boundingCenter).applyMatrix4(o.matrixWorld)
            val z = tmpV.applyMatrix4(camera.matrixWorldInverse).z
            if (o.multiMaterial && g.groups.isNotEmpty()) {
                for (grp in g.groups) {
                    val m = o.materials.getOrNull(grp[2]) ?: continue
                    if (!m.visible) continue
                    (if (m.transparent) transparent else opaque) += DrawItem(o, m, grp, z)
                }
            } else {
                val m = o.material
                if (m.visible) (if (m.transparent) transparent else opaque) += DrawItem(o, m, null, z)
            }
        }
        // three.js: opaque front-to-back, transparent back-to-front, renderOrder first
        opaque.sortWith(compareBy<DrawItem> { it.mesh.renderOrder }.thenByDescending { it.z })
        transparent.sortWith(compareBy<DrawItem> { it.mesh.renderOrder }.thenBy { it.z })
    }

    private fun inFrustum(o: Mesh, vp: Mat4): Boolean {
        val g = o.geometry
        val c = Vec3().copy(g.boundingCenter).applyMatrix4(o.matrixWorld)
        val e = o.matrixWorld.e
        val sx = Vec3(e[0], e[1], e[2]).length(); val sy = Vec3(e[4], e[5], e[6]).length(); val sz = Vec3(e[8], e[9], e[10]).length()
        val r = g.boundingRadius * maxOf(sx, sy, sz)
        // clip-space test of the sphere centre with a generous margin (cheap and safe)
        val m = vp.e
        val x = m[0] * c.x + m[4] * c.y + m[8] * c.z + m[12]
        val y = m[1] * c.x + m[5] * c.y + m[9] * c.z + m[13]
        val w = m[3] * c.x + m[7] * c.y + m[11] * c.z + m[15]
        if (w < -r) return false
        val lim = w + r * 2.5f
        return x >= -lim && x <= lim && y >= -lim && y <= lim
    }

    private val nm = FloatArray(9)
    private val mv = Mat4()
    private val uvT = FloatArray(9)
    private fun drawItem(d: DrawItem, camera: PerspectiveCamera) {
        val o = d.mesh; val m = d.mat
        upload(o.geometry)
        mv.multiplyMatrices(camera.matrixWorldInverse, o.matrixWorld)
        mv.normalMatrixInto(nm)
        val p = ps1Prog
        glUniformMatrix4fv(u(p, "modelMatrix"), 1, false, o.matrixWorld.e, 0)
        glUniformMatrix4fv(u(p, "modelViewMatrix"), 1, false, mv.e, 0)
        glUniformMatrix3fv(u(p, "normalMatrix"), 1, false, nm, 0)
        glUniform3f(u(p, "diffuse"), m.color.r, m.color.g, m.color.b)
        glUniform1f(u(p, "opacity"), m.opacity)
        glUniform3f(u(p, "emissive"), m.emissive.r * m.emissiveIntensity, m.emissive.g * m.emissiveIntensity, m.emissive.b * m.emissiveIntensity)
        glUniform1i(u(p, "uLit"), if (m.lit) 1 else 0)
        glUniform1i(u(p, "uPs1"), if (m.ps1) 1 else 0)
        glUniform1i(u(p, "uMacro"), if (m.macro) 1 else 0)
        glUniform1i(u(p, "uFog"), if (m.fog) 1 else 0)
        glUniform1i(u(p, "uDoubleSide"), if (m.side == DOUBLE_SIDE) 1 else 0)
        glUniform1i(u(p, "uReceiveShadow"), if (o.receiveShadow) 1 else 0)
        val map = m.map
        glActiveTexture(GL_TEXTURE0)
        if (map != null) { bindTexture(map); glUniform1i(u(p, "uHasMap"), 1) } else { glBindTexture(GL_TEXTURE_2D, whiteTex); glUniform1i(u(p, "uHasMap"), 0) }
        glUniform1i(u(p, "map"), 0)
        // uv transform from the map (three uses map.matrix: repeat + offset)
        val t = map ?: m.emissiveMap
        uvT.fill(0f); uvT[0] = t?.repeatX ?: 1f; uvT[4] = t?.repeatY ?: 1f; uvT[6] = t?.offsetX ?: 0f; uvT[7] = t?.offsetY ?: 0f; uvT[8] = 1f
        glUniformMatrix3fv(u(p, "uvTransform"), 1, false, uvT, 0)
        glActiveTexture(GL_TEXTURE1)
        val em = m.emissiveMap
        if (em != null) { bindTexture(em); glUniform1i(u(p, "uHasEmissiveMap"), 1) } else { glBindTexture(GL_TEXTURE_2D, whiteTex); glUniform1i(u(p, "uHasEmissiveMap"), 0) }
        glUniform1i(u(p, "emissiveMap"), 1)
        // render state
        when (m.side) {
            FRONT_SIDE -> { glEnable(GL_CULL_FACE); glCullFace(GL_BACK) }
            BACK_SIDE -> { glEnable(GL_CULL_FACE); glCullFace(GL_FRONT) }
            else -> glDisable(GL_CULL_FACE)
        }
        if (m.depthTest) glEnable(GL_DEPTH_TEST) else glDisable(GL_DEPTH_TEST)
        glDepthMask(m.depthWrite)
        if (m.polygonOffset) { glEnable(GL_POLYGON_OFFSET_FILL); glPolygonOffset(m.polygonOffsetFactor, m.polygonOffsetUnits) } else glDisable(GL_POLYGON_OFFSET_FILL)
        glBindVertexArray(o.geometry.vao)
        drawGeometry(o.geometry, d.group)
    }

    private fun drawGeometry(g: Geometry, group: IntArray?) {
        val idx = g.indices
        if (idx != null) {
            if (group != null) glDrawElements(GL_TRIANGLES, group[1], GL_UNSIGNED_INT, group[0] * 4)
            else glDrawElements(GL_TRIANGLES, idx.size, GL_UNSIGNED_INT, 0)
        } else {
            if (group != null) glDrawArrays(GL_TRIANGLES, group[0], group[1]) else glDrawArrays(GL_TRIANGLES, 0, g.vertexCount)
        }
    }

    private val lightPosBuf = FloatArray(3 * Shaders.MAX_POINT)
    private val lightColBuf = FloatArray(3 * Shaders.MAX_POINT)
    private val lightDDBuf = FloatArray(2 * Shaders.MAX_POINT)
    private fun setFrameUniforms(scene: Scene, camera: PerspectiveCamera) {
        val p = ps1Prog
        glUniformMatrix4fv(u(p, "projectionMatrix"), 1, false, camera.projectionMatrix.e, 0)
        glUniform2f(u(p, "uJitterGrid"), ps1Uniforms.jitterGrid[0], ps1Uniforms.jitterGrid[1])
        glUniform1f(u(p, "uAffineStrength"), ps1Uniforms.affineStrength)
        glUniform3f(u(p, "ambientLightColor"), ambient.r, ambient.g, ambient.b)
        glUniform3f(u(p, "fogColor"), scene.fogColor.r, scene.fogColor.g, scene.fogColor.b)
        glUniform1f(u(p, "fogDensity"), scene.fogDensity)
        val view = camera.matrixWorldInverse
        val v = Vec3()
        // point lights (view space)
        points.forEachIndexed { i, l ->
            v.setFromMatrixPosition(l.matrixWorld).applyMatrix4(view)
            lightPosBuf[i * 3] = v.x; lightPosBuf[i * 3 + 1] = v.y; lightPosBuf[i * 3 + 2] = v.z
            lightColBuf[i * 3] = l.color.r * l.intensity; lightColBuf[i * 3 + 1] = l.color.g * l.intensity; lightColBuf[i * 3 + 2] = l.color.b * l.intensity
            lightDDBuf[i * 2] = l.distance; lightDDBuf[i * 2 + 1] = l.decay
        }
        glUniform1i(u(p, "uPointCount"), points.size)
        if (points.isNotEmpty()) {
            glUniform3fv(u(p, "uPointPos"), points.size, lightPosBuf, 0)
            glUniform3fv(u(p, "uPointColor"), points.size, lightColBuf, 0)
            glUniform2fv(u(p, "uPointDistDecay"), points.size, lightDDBuf, 0)
        }
        // spot lights
        val sp = FloatArray(3 * Shaders.MAX_SPOT); val sd = FloatArray(3 * Shaders.MAX_SPOT); val sc = FloatArray(3 * Shaders.MAX_SPOT)
        val spar = FloatArray(4 * Shaders.MAX_SPOT); val sshadow = IntArray(Shaders.MAX_SPOT) { -1 }
        var shadowSlot = 0
        val tp = Vec3()
        spots.forEachIndexed { i, l ->
            l.target.updateMatrixWorld()
            val lp = Vec3().setFromMatrixPosition(l.matrixWorld)
            tp.setFromMatrixPosition(l.target.matrixWorld)
            val dir = Vec3().subVectors(lp, tp).applyMatrix4Dir(view).normalize()
            lp.applyMatrix4(view)
            sp[i * 3] = lp.x; sp[i * 3 + 1] = lp.y; sp[i * 3 + 2] = lp.z
            sd[i * 3] = dir.x; sd[i * 3 + 1] = dir.y; sd[i * 3 + 2] = dir.z
            sc[i * 3] = l.color.r * l.intensity; sc[i * 3 + 1] = l.color.g * l.intensity; sc[i * 3 + 2] = l.color.b * l.intensity
            spar[i * 4] = cos(l.angle); spar[i * 4 + 1] = cos(l.angle * (1 - l.penumbra)); spar[i * 4 + 2] = l.distance; spar[i * 4 + 3] = l.decay
            if (l.castShadow && shadowSlot < 2) {
                val target = shadowTargets[l]
                if (target != null) {
                    sshadow[i] = shadowSlot
                    glActiveTexture(GL_TEXTURE2 + shadowSlot); glBindTexture(GL_TEXTURE_2D, target.depth)
                    glUniform1i(u(p, "uShadowMap$shadowSlot"), 2 + shadowSlot)
                    glUniformMatrix4fv(u(p, "uShadowMatrix$shadowSlot"), 1, false, l.shadow.matrix.e, 0)
                    glUniform1f(u(p, "uShadowBias$shadowSlot"), l.shadow.bias)
                }
                shadowSlot++
            }
        }
        for (s in shadowSlot until 2) {
            glActiveTexture(GL_TEXTURE2 + s); glBindTexture(GL_TEXTURE_2D, whiteTex); glUniform1i(u(p, "uShadowMap$s"), 2 + s)
            glUniformMatrix4fv(u(p, "uShadowMatrix$s"), 1, false, Mat4().e, 0)
        }
        glUniform1i(u(p, "uSpotCount"), spots.size)
        if (spots.isNotEmpty()) {
            glUniform3fv(u(p, "uSpotPos"), spots.size, sp, 0)
            glUniform3fv(u(p, "uSpotDir"), spots.size, sd, 0)
            glUniform3fv(u(p, "uSpotColor"), spots.size, sc, 0)
            glUniform4fv(u(p, "uSpotParams"), spots.size, spar, 0)
            glUniform1iv(u(p, "uSpotShadow"), spots.size, sshadow, 0)
        }
        // hemisphere lights: up direction (0,1,0) in view space
        val hs = FloatArray(3 * Shaders.MAX_HEMI); val hg = FloatArray(3 * Shaders.MAX_HEMI); val hd = FloatArray(3 * Shaders.MAX_HEMI)
        hemis.forEachIndexed { i, h ->
            val up = Vec3().setFromMatrixPosition(h.matrixWorld).normalize().applyMatrix4Dir(view).normalize()
            hs[i * 3] = h.color.r * h.intensity; hs[i * 3 + 1] = h.color.g * h.intensity; hs[i * 3 + 2] = h.color.b * h.intensity
            hg[i * 3] = h.groundColor.r * h.intensity; hg[i * 3 + 1] = h.groundColor.g * h.intensity; hg[i * 3 + 2] = h.groundColor.b * h.intensity
            hd[i * 3] = up.x; hd[i * 3 + 1] = up.y; hd[i * 3 + 2] = up.z
        }
        glUniform1i(u(p, "uHemiCount"), hemis.size)
        if (hemis.isNotEmpty()) {
            glUniform3fv(u(p, "uHemiSky"), hemis.size, hs, 0)
            glUniform3fv(u(p, "uHemiGround"), hemis.size, hg, 0)
            glUniform3fv(u(p, "uHemiDir"), hemis.size, hd, 0)
        }
    }

    // ---- 3. Post ----
    private fun setPostUniforms(camera: PerspectiveCamera) {
        val p = postProg
        glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, sceneColor); glUniform1i(u(p, "tDiffuse"), 0)
        glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_2D, sceneDepth); glUniform1i(u(p, "tDepth"), 1)
        val ray = post.rayLight
        val st = if (ray != null) shadowTargets[ray] else null
        glActiveTexture(GL_TEXTURE2); glBindTexture(GL_TEXTURE_2D, st?.depth ?: whiteTex); glUniform1i(u(p, "tShadowMap"), 2)
        glUniform1f(u(p, "uHasDepthTex"), 1f)
        glUniform2f(u(p, "uResolution"), internalW.toFloat(), internalH.toFloat())
        glUniform1f(u(p, "uDitherStrength"), post.ditherStrength)
        glUniform1f(u(p, "uVolumetricStrength"), if (st != null) post.volumetricStrength else 0f)
        glUniform1f(u(p, "uAOStrength"), post.aoStrength)
        glUniform1i(u(p, "uMarchSteps"), post.marchSteps)
        glUniformMatrix4fv(u(p, "uInvProjMatrix"), 1, false, camera.projectionMatrixInverse.e, 0)
        glUniformMatrix4fv(u(p, "uInvViewMatrix"), 1, false, camera.matrixWorld.e, 0)
        val cp = Vec3().setFromMatrixPosition(camera.matrixWorld)
        glUniform3f(u(p, "uCameraPos"), cp.x, cp.y, cp.z)
        if (ray != null) {
            val sc = ray.shadow.camera
            val pv = Mat4().multiplyMatrices(sc.projectionMatrix, sc.matrixWorldInverse)
            glUniformMatrix4fv(u(p, "uShadowMatrix"), 1, false, pv.e, 0)
            val lp = Vec3().setFromMatrixPosition(ray.matrixWorld)
            val tp = Vec3().setFromMatrixPosition(ray.target.matrixWorld)
            val ld = Vec3().subVectors(tp, lp).normalize()
            glUniform3f(u(p, "uLightPos"), lp.x, lp.y, lp.z)
            glUniform3f(u(p, "uLightDir"), ld.x, ld.y, ld.z)
        }
        glUniform3f(u(p, "uLightColor"), post.lightColor.r, post.lightColor.g, post.lightColor.b)
        glUniform1f(u(p, "uLightCosAngle"), post.lightCosAngle)
        glUniform1f(u(p, "uLightPenumbraCos"), post.lightPenumbraCos)
        glUniform1f(u(p, "uFanLightOn"), post.fanLightOn)
        glUniform3f(u(p, "uFanLightPos"), post.fanLightPos.x, post.fanLightPos.y, post.fanLightPos.z)
        glUniform1f(u(p, "uTime"), post.time)
        glUniform3f(u(p, "uGradeTint"), post.gradeTint.x, post.gradeTint.y, post.gradeTint.z)
        glUniform1f(u(p, "uGradeSat"), post.gradeSat)
        glUniform1f(u(p, "uGradeContrast"), post.gradeContrast)
        glUniform1f(u(p, "uVignette"), post.vignette)
    }

    // ---------------------------------------------------------------- uploads
    private val uploadedGeometries = ArrayList<Geometry>()
    private val uploadedTextures = ArrayList<Texture>()

    private fun upload(g: Geometry) {
        if (g.uploaded) return
        val vao = IntArray(1); glGenVertexArrays(1, vao, 0); g.vao = vao[0]
        glBindVertexArray(g.vao)
        val b = IntArray(4); glGenBuffers(4, b, 0); g.vbo = b
        glBindBuffer(GL_ARRAY_BUFFER, b[0]); glBufferData(GL_ARRAY_BUFFER, g.positions.size * 4, floats(g.positions), GL_STATIC_DRAW)
        glEnableVertexAttribArray(0); glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0)
        glBindBuffer(GL_ARRAY_BUFFER, b[1]); glBufferData(GL_ARRAY_BUFFER, g.normals.size * 4, floats(g.normals), GL_STATIC_DRAW)
        glEnableVertexAttribArray(1); glVertexAttribPointer(1, 3, GL_FLOAT, false, 0, 0)
        glBindBuffer(GL_ARRAY_BUFFER, b[2]); glBufferData(GL_ARRAY_BUFFER, g.uvs.size * 4, floats(g.uvs), GL_STATIC_DRAW)
        glEnableVertexAttribArray(2); glVertexAttribPointer(2, 2, GL_FLOAT, false, 0, 0)
        g.indices?.let { idx ->
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, b[3])
            val ib = ByteBuffer.allocateDirect(idx.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer().put(idx); ib.position(0)
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, idx.size * 4, ib, GL_STATIC_DRAW)
        }
        glBindVertexArray(0)
        g.uploaded = true
        uploadedGeometries += g
    }

    /** Uploads like a three.js CanvasTexture: flipY on, no premultiplied alpha, nearest filtering. */
    private fun bindTexture(t: Texture) {
        if (t.glId == 0) { val id = IntArray(1); glGenTextures(1, id, 0); t.glId = id[0]; uploadedTextures += t }
        glBindTexture(GL_TEXTURE_2D, t.glId)
        if (t.uploadedVersion != t.version) {
            val bm = t.bitmap; val w = bm.width; val h = bm.height
            val px = IntArray(w * h); bm.getPixels(px, 0, w, 0, 0, w, h)   // un-premultiplied ARGB
            val buf = ByteBuffer.allocateDirect(w * h * 4)
            for (y in h - 1 downTo 0) { // flipY
                val row = y * w
                for (x in 0 until w) { val c = px[row + x]; buf.put((c shr 16).toByte()); buf.put((c shr 8).toByte()); buf.put(c.toByte()); buf.put((c ushr 24).toByte()) }
            }
            buf.position(0)
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf)
            val wrap = if (t.wrapRepeat) GL_REPEAT else GL_CLAMP_TO_EDGE
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wrap); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, wrap)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, if (t.nearest) GL_NEAREST else GL_LINEAR)
            if (t.mipmaps) { glGenerateMipmap(GL_TEXTURE_2D); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_NEAREST) }
            else glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, if (t.nearest) GL_NEAREST else GL_LINEAR)
            t.uploadedVersion = t.version
        }
    }

    // ---------------------------------------------------------------- GL helpers
    private fun nearest(wrap: Int) {
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wrap); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, wrap)
    }
    private fun u(prog: Int, name: String): Int = loc.getOrPut("$prog/$name") { glGetUniformLocation(prog, name) }
    private fun floats(a: FloatArray): FloatBuffer = ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(a).also { it.position(0) }
    private fun shader(type: Int, src: String): Int {
        val s = glCreateShader(type); glShaderSource(s, src); glCompileShader(s)
        val ok = IntArray(1); glGetShaderiv(s, GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) { val log = glGetShaderInfoLog(s); Log.e(TAG, "shader compile failed: $log"); throw RuntimeException("Shader compile failed: $log") }
        return s
    }
    private fun program(vs: String, fs: String): Int {
        val p = glCreateProgram()
        glAttachShader(p, shader(GL_VERTEX_SHADER, vs)); glAttachShader(p, shader(GL_FRAGMENT_SHADER, fs))
        glLinkProgram(p)
        val ok = IntArray(1); glGetProgramiv(p, GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) { val log = glGetProgramInfoLog(p); Log.e(TAG, "program link failed: $log"); throw RuntimeException("Program link failed: $log") }
        return p
    }
    companion object { const val TAG = "PS1Bedroom" }
}
