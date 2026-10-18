package com.ps1bedroom.room.engine

import android.opengl.GLES30.*
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Draws a Scene the way the HTML does with three.js r128:
 *  1. shadow maps for the shadow-casting spot lights (only when flagged, like shadowMap.autoUpdate = false)
 *  2. the scene at PS1 resolution into a colour + depth render target
 *  3. the post pass (AO, light rays, grade, dither) into a second low-res target
 *  4. that target stretched to the screen with nearest filtering (the CSS "image-rendering: pixelated")
 *
 * Built to run all night: no allocations per frame (no garbage collector work), uniform locations looked up
 * once, draw lists reused, state changes skipped when nothing changed.
 */
class Renderer {
    // ---- Programs and their uniform locations (looked up once) ----
    private var ps1Prog = 0; private var depthProg = 0; private var postProg = 0; private var blitProg = 0
    private class Ps1Loc(p: Int) {
        val modelMatrix = glGetUniformLocation(p, "modelMatrix"); val modelViewMatrix = glGetUniformLocation(p, "modelViewMatrix")
        val projectionMatrix = glGetUniformLocation(p, "projectionMatrix"); val normalMatrix = glGetUniformLocation(p, "normalMatrix")
        val uvTransform = glGetUniformLocation(p, "uvTransform"); val jitterGrid = glGetUniformLocation(p, "uJitterGrid")
        val affine = glGetUniformLocation(p, "uAffineStrength"); val ps1 = glGetUniformLocation(p, "uPs1")
        val shadowMatrix = intArrayOf(glGetUniformLocation(p, "uShadowMatrix0"), glGetUniformLocation(p, "uShadowMatrix1"))
        val shadowMap = intArrayOf(glGetUniformLocation(p, "uShadowMap0"), glGetUniformLocation(p, "uShadowMap1"))
        val shadowBias = intArrayOf(glGetUniformLocation(p, "uShadowBias0"), glGetUniformLocation(p, "uShadowBias1"))
        val diffuse = glGetUniformLocation(p, "diffuse"); val opacity = glGetUniformLocation(p, "opacity"); val emissive = glGetUniformLocation(p, "emissive")
        val hasMap = glGetUniformLocation(p, "uHasMap"); val hasEm = glGetUniformLocation(p, "uHasEmissiveMap"); val macro = glGetUniformLocation(p, "uMacro")
        val lit = glGetUniformLocation(p, "uLit"); val fog = glGetUniformLocation(p, "uFog"); val doubleSide = glGetUniformLocation(p, "uDoubleSide")
        val receive = glGetUniformLocation(p, "uReceiveShadow"); val vColors = glGetUniformLocation(p, "uVertexColors")
        val map = glGetUniformLocation(p, "map"); val emMap = glGetUniformLocation(p, "emissiveMap")
        val ambient = glGetUniformLocation(p, "ambientLightColor"); val fogColor = glGetUniformLocation(p, "fogColor"); val fogDensity = glGetUniformLocation(p, "fogDensity")
        val hemiCount = glGetUniformLocation(p, "uHemiCount"); val hemiSky = glGetUniformLocation(p, "uHemiSky"); val hemiGround = glGetUniformLocation(p, "uHemiGround"); val hemiDir = glGetUniformLocation(p, "uHemiDir")
        val pointCount = glGetUniformLocation(p, "uPointCount"); val pointPos = glGetUniformLocation(p, "uPointPos"); val pointColor = glGetUniformLocation(p, "uPointColor"); val pointDD = glGetUniformLocation(p, "uPointDistDecay")
        val spotCount = glGetUniformLocation(p, "uSpotCount"); val spotPos = glGetUniformLocation(p, "uSpotPos"); val spotDir = glGetUniformLocation(p, "uSpotDir")
        val spotColor = glGetUniformLocation(p, "uSpotColor"); val spotParams = glGetUniformLocation(p, "uSpotParams"); val spotShadow = glGetUniformLocation(p, "uSpotShadow")
    }
    private class PostLoc(p: Int) {
        val tDiffuse = glGetUniformLocation(p, "tDiffuse"); val tDepth = glGetUniformLocation(p, "tDepth"); val tShadowMap = glGetUniformLocation(p, "tShadowMap")
        val hasDepth = glGetUniformLocation(p, "uHasDepthTex"); val resolution = glGetUniformLocation(p, "uResolution"); val dither = glGetUniformLocation(p, "uDitherStrength")
        val volumetric = glGetUniformLocation(p, "uVolumetricStrength"); val ao = glGetUniformLocation(p, "uAOStrength"); val steps = glGetUniformLocation(p, "uMarchSteps")
        val invProj = glGetUniformLocation(p, "uInvProjMatrix"); val invView = glGetUniformLocation(p, "uInvViewMatrix"); val shadowMatrix = glGetUniformLocation(p, "uShadowMatrix")
        val cameraPos = glGetUniformLocation(p, "uCameraPos"); val lightPos = glGetUniformLocation(p, "uLightPos"); val lightDir = glGetUniformLocation(p, "uLightDir")
        val lightColor = glGetUniformLocation(p, "uLightColor"); val cosAngle = glGetUniformLocation(p, "uLightCosAngle"); val penumbraCos = glGetUniformLocation(p, "uLightPenumbraCos")
        val fanOn = glGetUniformLocation(p, "uFanLightOn"); val fanPos = glGetUniformLocation(p, "uFanLightPos"); val time = glGetUniformLocation(p, "uTime")
        val tint = glGetUniformLocation(p, "uGradeTint"); val sat = glGetUniformLocation(p, "uGradeSat"); val contrast = glGetUniformLocation(p, "uGradeContrast"); val vignette = glGetUniformLocation(p, "uVignette")
    }
    private lateinit var L: Ps1Loc
    private lateinit var P: PostLoc
    private var depthMvp = 0; private var blitTex = 0
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
    /** Draw calls in the last frame (for tuning). */
    @JvmField var lastDrawCalls = 0

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
        ps1Prog = program(Shaders.PS1_VERT, Shaders.PS1_FRAG); L = Ps1Loc(ps1Prog)
        depthProg = program(Shaders.DEPTH_VERT, Shaders.DEPTH_FRAG); depthMvp = glGetUniformLocation(depthProg, "mvp")
        postProg = program(Shaders.QUAD_VERT, Shaders.POST_FRAG); P = PostLoc(postProg)
        blitProg = program(Shaders.QUAD_VERT, Shaders.BLIT_FRAG); blitTex = glGetUniformLocation(blitProg, "tex")
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
        customShaders.forEach { it.program = 0 }; customShaders.clear()
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
    private class DrawItem { lateinit var mesh: Mesh; lateinit var mat: Material; var group: IntArray? = null; var z = 0f }
    private val pool = ArrayList<DrawItem>()
    private var poolUsed = 0
    private val opaque = ArrayList<DrawItem>()
    private val transparent = ArrayList<DrawItem>()
    private val opaqueCmp = Comparator<DrawItem> { a, b -> if (a.mesh.renderOrder != b.mesh.renderOrder) a.mesh.renderOrder - b.mesh.renderOrder else b.z.compareTo(a.z) }
    private val transparentCmp = Comparator<DrawItem> { a, b -> if (a.mesh.renderOrder != b.mesh.renderOrder) a.mesh.renderOrder - b.mesh.renderOrder else a.z.compareTo(b.z) }
    private val spots = ArrayList<SpotLight>()
    private val points = ArrayList<PointLight>()
    private val hemis = ArrayList<HemisphereLight>()
    private val ambient = Color(0)
    private var ar = 0f; private var ag = 0f; private var ab = 0f

    fun render(scene: Scene, camera: PerspectiveCamera) {
        scene.updateMatrixWorld()
        camera.updateCameraWorld()
        collectLights(scene)
        if (shadowsNeedUpdate) { renderShadows(scene); shadowsNeedUpdate = false }
        lastDrawCalls = 0

        glBindFramebuffer(GL_FRAMEBUFFER, sceneFbo)
        glViewport(0, 0, internalW, internalH)
        glClearColor(scene.background.r, scene.background.g, scene.background.b, 1f)
        glDepthMask(true); curDepthWrite = true
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        buildDrawLists(scene, camera)
        glUseProgram(ps1Prog)
        setFrameUniforms(scene, camera)
        resetState()
        glEnable(GL_DEPTH_TEST); glDepthFunc(GL_LEQUAL); curDepthTest = true
        glDisable(GL_BLEND)
        for (i in opaque.indices) drawItem(opaque[i], camera)
        glEnable(GL_BLEND); glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA); curAdditive = false
        for (i in transparent.indices) drawItem(transparent[i], camera)
        glDisable(GL_BLEND); glDepthMask(true); glDisable(GL_POLYGON_OFFSET_FILL)

        glBindFramebuffer(GL_FRAMEBUFFER, postFbo)
        glViewport(0, 0, internalW, internalH)
        glDisable(GL_DEPTH_TEST); glDisable(GL_CULL_FACE)
        glUseProgram(postProg)
        setPostUniforms(camera)
        glBindVertexArray(quadVao); glDrawArrays(GL_TRIANGLES, 0, 6)

        glBindFramebuffer(GL_FRAMEBUFFER, 0)
        glViewport(0, 0, screenW, screenH)
        glUseProgram(blitProg)
        glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, postColor)
        glUniform1i(blitTex, 0)
        glDrawArrays(GL_TRIANGLES, 0, 6)
        glBindVertexArray(0)
    }

    private val lightVisitor: (Object3D) -> Unit = { o ->
        when (o) {
            is AmbientLight -> { ar += o.color.r * o.intensity; ag += o.color.g * o.intensity; ab += o.color.b * o.intensity }
            is HemisphereLight -> if (hemis.size < Shaders.MAX_HEMI) hemis += o
            is PointLight -> if (points.size < Shaders.MAX_POINT && o.intensity > 0f) points += o
            is SpotLight -> if (spots.size < Shaders.MAX_SPOT) spots += o
            else -> {}
        }
    }
    private val spotCmp = Comparator<SpotLight> { a, b -> (if (a.castShadow) 0 else 1) - (if (b.castShadow) 0 else 1) }
    private fun collectLights(scene: Scene) {
        spots.clear(); points.clear(); hemis.clear()
        ar = 0f; ag = 0f; ab = 0f
        scene.traverseVisible(lightVisitor)
        ambient.setRGB(ar, ag, ab)
        // shadow casters first so they get shadow slots 0 and 1
        spots.sortWith(spotCmp)
    }

    // ---- 1. Shadows (three's SpotLightShadow: fov = 2*angle, aspect 1, far = light.distance) ----
    private val tmpM = Mat4(); private val tmpM2 = Mat4(); private val biasM = Mat4().also {
        it.e.fill(0f); it.e[0] = 0.5f; it.e[5] = 0.5f; it.e[10] = 0.5f; it.e[12] = 0.5f; it.e[13] = 0.5f; it.e[14] = 0.5f; it.e[15] = 1f
    }
    private val shadowPV = Mat4()
    private val sv1 = Vec3(); private val sv2 = Vec3()
    private var shadowCull = -1
    private val shadowVisitor: (Object3D) -> Unit = { o ->
        if (o is Mesh && o.castShadow && o.material.visible) {
            upload(o.geometry)
            // three r128 renders the opposite side into shadow maps (FrontSide -> BackSide, DoubleSide stays):
            // the room's walls face away from the moon, so their back faces are what block it
            val side = o.material.side
            if (side != shadowCull) {
                when (side) {
                    FRONT_SIDE -> { glEnable(GL_CULL_FACE); glCullFace(GL_FRONT) }
                    BACK_SIDE -> { glEnable(GL_CULL_FACE); glCullFace(GL_BACK) }
                    else -> glDisable(GL_CULL_FACE)
                }
                shadowCull = side
            }
            tmpM.multiplyMatrices(shadowPV, o.matrixWorld)
            glUniformMatrix4fv(depthMvp, 1, false, tmpM.e, 0)
            glBindVertexArray(o.geometry.vao)
            drawGeometry(o.geometry, null)
        }
    }
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
            sv1.setFromMatrixPosition(s.matrixWorld); sv2.setFromMatrixPosition(s.target.matrixWorld)
            cam.position.copy(sv1); cam.lookAtTarget(sv2); cam.updateCameraWorld()
            shadowPV.multiplyMatrices(cam.projectionMatrix, cam.matrixWorldInverse)
            sh.matrix.multiplyMatrices(biasM, shadowPV)
            glBindFramebuffer(GL_FRAMEBUFFER, target.fbo)
            glViewport(0, 0, target.size, target.size)
            glDepthMask(true); glClear(GL_DEPTH_BUFFER_BIT)
            glEnable(GL_DEPTH_TEST); glDepthFunc(GL_LEQUAL)
            glColorMask(false, false, false, false)
            shadowCull = -1
            glDisable(GL_POLYGON_OFFSET_FILL)
            glUseProgram(depthProg)
            scene.traverseVisible(shadowVisitor)
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
    private val tmpV = Vec3()
    private lateinit var curCamera: PerspectiveCamera
    private val drawVisitor: (Object3D) -> Unit = { o ->
        if (o is Mesh) {
            val g = o.geometry
            if (g.boundingRadius < 0) g.computeBoundingSphere()
            if (!o.frustumCulled || inFrustum(o)) {
                tmpV.copy(g.boundingCenter).applyMatrix4(o.matrixWorld)
                val z = tmpV.applyMatrix4(curCamera.matrixWorldInverse).z
                if (o.multiMaterial && g.groups.isNotEmpty()) {
                    for (grp in g.groups) {
                        val m = o.materials.getOrNull(grp[2]) ?: continue
                        if (m.visible) add(o, m, grp, z)
                    }
                } else {
                    val m = o.material
                    if (m.visible) add(o, m, null, z)
                }
            }
        }
    }
    private fun add(mesh: Mesh, m: Material, grp: IntArray?, z: Float) {
        if (poolUsed == pool.size) pool += DrawItem()
        val d = pool[poolUsed++]
        d.mesh = mesh; d.mat = m; d.group = grp; d.z = z
        (if (m.transparent) transparent else opaque) += d
    }
    private fun buildDrawLists(scene: Scene, camera: PerspectiveCamera) {
        opaque.clear(); transparent.clear(); poolUsed = 0
        curCamera = camera
        tmpM2.multiplyMatrices(camera.projectionMatrix, camera.matrixWorldInverse)
        setPlanes(tmpM2)
        scene.traverseVisible(drawVisitor)
        opaque.sortWith(opaqueCmp)
        transparent.sortWith(transparentCmp)
    }

    // three's Frustum.intersectsSphere: six normalised planes from projection * view, with a safety margin so
    // things never pop out while any part of them (or their PS1 wobble) could still be on screen
    private val fc = Vec3()
    private val planes = FloatArray(24)
    private fun setPlane(i: Int, a: Float, b: Float, c: Float, d: Float) {
        val l = kotlin.math.sqrt(a * a + b * b + c * c)
        planes[i * 4] = a / l; planes[i * 4 + 1] = b / l; planes[i * 4 + 2] = c / l; planes[i * 4 + 3] = d / l
    }
    private fun setPlanes(vp: Mat4) {
        val m = vp.e
        setPlane(0, m[3] - m[0], m[7] - m[4], m[11] - m[8], m[15] - m[12])
        setPlane(1, m[3] + m[0], m[7] + m[4], m[11] + m[8], m[15] + m[12])
        setPlane(2, m[3] + m[1], m[7] + m[5], m[11] + m[9], m[15] + m[13])
        setPlane(3, m[3] - m[1], m[7] - m[5], m[11] - m[9], m[15] - m[13])
        setPlane(4, m[3] - m[2], m[7] - m[6], m[11] - m[10], m[15] - m[14])
        setPlane(5, m[3] + m[2], m[7] + m[6], m[11] + m[10], m[15] + m[14])
    }
    /** three's Frustum.intersectsSphere against the last frame's view (the HTML's inView). */
    fun sphereInView(c: Vec3, r: Float): Boolean {
        for (i in 0 until 6) if (planes[i * 4] * c.x + planes[i * 4 + 1] * c.y + planes[i * 4 + 2] * c.z + planes[i * 4 + 3] < -r) return false
        return true
    }
    private fun inFrustum(o: Mesh): Boolean {
        val g = o.geometry
        fc.copy(g.boundingCenter).applyMatrix4(o.matrixWorld)
        val e = o.matrixWorld.e
        val sx = e[0] * e[0] + e[1] * e[1] + e[2] * e[2]; val sy = e[4] * e[4] + e[5] * e[5] + e[6] * e[6]; val sz = e[8] * e[8] + e[9] * e[9] + e[10] * e[10]
        val r = g.boundingRadius * kotlin.math.sqrt(maxOf(sx, sy, sz)) * 1.25f + 0.15f
        for (i in 0 until 6) {
            if (planes[i * 4] * fc.x + planes[i * 4 + 1] * fc.y + planes[i * 4 + 2] * fc.z + planes[i * 4 + 3] < -r) return false
        }
        return true
    }

    // State cache: skip GL calls when nothing changed between draws
    private var curAdditive = false
    private var curCull = -1; private var curDepthTest = true; private var curDepthWrite = true; private var curOffset = false
    private var curOffF = 0f; private var curOffU = 0f; private var curTex0 = -1; private var curTex1 = -1; private var curVao = -1
    private fun resetState() { curCull = -1; curOffset = false; glDisable(GL_POLYGON_OFFSET_FILL); curTex0 = -1; curTex1 = -1; curVao = -1 }

    private val nm = FloatArray(9)
    private val mv = Mat4()
    private val uvT = FloatArray(9)
    private fun drawItem(d: DrawItem, camera: PerspectiveCamera) {
        if (d.mat.shader != null) { drawCustom(d, camera); return }
        val o = d.mesh; val m = d.mat
        upload(o.geometry)
        mv.multiplyMatrices(camera.matrixWorldInverse, o.matrixWorld)
        mv.normalMatrixInto(nm)
        glUniformMatrix4fv(L.modelMatrix, 1, false, o.matrixWorld.e, 0)
        glUniformMatrix4fv(L.modelViewMatrix, 1, false, mv.e, 0)
        glUniformMatrix3fv(L.normalMatrix, 1, false, nm, 0)
        glUniform3f(L.diffuse, m.color.r, m.color.g, m.color.b)
        glUniform1f(L.opacity, m.opacity)
        glUniform3f(L.emissive, m.emissive.r * m.emissiveIntensity, m.emissive.g * m.emissiveIntensity, m.emissive.b * m.emissiveIntensity)
        glUniform1i(L.lit, if (m.lit) 1 else 0)
        glUniform1i(L.ps1, if (m.ps1) 1 else 0)
        glUniform1i(L.macro, if (m.macro) 1 else 0)
        glUniform1i(L.fog, if (m.fog) 1 else 0)
        glUniform1i(L.doubleSide, if (m.side == DOUBLE_SIDE) 1 else 0)
        glUniform1i(L.receive, if (o.receiveShadow) 1 else 0)
        glUniform1i(L.vColors, if (m.vertexColors && o.geometry.colors != null) 1 else 0)
        val map = m.map
        val t0 = if (map != null) texId(map) else whiteTex
        if (t0 != curTex0) { glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, t0); curTex0 = t0 }
        glUniform1i(L.hasMap, if (map != null) 1 else 0)
        val t = map ?: m.emissiveMap
        uvT[0] = t?.repeatX ?: 1f; uvT[4] = t?.repeatY ?: 1f; uvT[6] = t?.offsetX ?: 0f; uvT[7] = t?.offsetY ?: 0f; uvT[8] = 1f
        glUniformMatrix3fv(L.uvTransform, 1, false, uvT, 0)
        val em = m.emissiveMap
        val t1 = if (em != null) texId(em) else whiteTex
        if (t1 != curTex1) { glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_2D, t1); curTex1 = t1 }
        glUniform1i(L.hasEm, if (em != null) 1 else 0)
        applyState(m)
        val vao = o.geometry.vao
        if (vao != curVao) { glBindVertexArray(vao); curVao = vao }
        drawGeometry(o.geometry, d.group)
        lastDrawCalls++
    }

    // ---- three.js ShaderMaterial (sky dome, clouds): its own program, then back to the PS1 one ----
    private val customShaders = ArrayList<CustomShader>()
    // Custom shaders get texture units 4-6 so the PS1 program's maps (0, 1) and shadow maps (2, 3) stay bound
    private val texBinder = TexBinder { slot, t -> val id = texId(t); glActiveTexture(GL_TEXTURE4 + slot); glBindTexture(GL_TEXTURE_2D, id); 4 + slot }
    private fun drawCustom(d: DrawItem, camera: PerspectiveCamera) {
        val o = d.mesh; val m = d.mat; val s = m.shader!!
        upload(o.geometry)
        if (s.program == 0) {
            s.program = program(s.vert, s.frag)
            s.mvLoc = glGetUniformLocation(s.program, "modelViewMatrix"); s.projLoc = glGetUniformLocation(s.program, "projectionMatrix")
            s.locate(s.program)
            customShaders += s
        }
        glUseProgram(s.program)
        mv.multiplyMatrices(camera.matrixWorldInverse, o.matrixWorld)
        glUniformMatrix4fv(s.mvLoc, 1, false, mv.e, 0)
        glUniformMatrix4fv(s.projLoc, 1, false, camera.projectionMatrix.e, 0)
        s.apply(texBinder)
        applyState(m)
        val vao = o.geometry.vao
        if (vao != curVao) { glBindVertexArray(vao); curVao = vao }
        drawGeometry(o.geometry, d.group)
        lastDrawCalls++
        glUseProgram(ps1Prog)
    }

    private fun applyState(m: Material) {
        if (m.transparent && m.additive != curAdditive) {
            if (m.additive) glBlendFunc(GL_SRC_ALPHA, GL_ONE) else glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
            curAdditive = m.additive
        }
        val cull = m.side
        if (cull != curCull) {
            when (cull) {
                FRONT_SIDE -> { glEnable(GL_CULL_FACE); glCullFace(GL_BACK) }
                BACK_SIDE -> { glEnable(GL_CULL_FACE); glCullFace(GL_FRONT) }
                else -> glDisable(GL_CULL_FACE)
            }
            curCull = cull
        }
        if (m.depthTest != curDepthTest) { if (m.depthTest) glEnable(GL_DEPTH_TEST) else glDisable(GL_DEPTH_TEST); curDepthTest = m.depthTest }
        if (m.depthWrite != curDepthWrite) { glDepthMask(m.depthWrite); curDepthWrite = m.depthWrite }
        if (m.polygonOffset != curOffset) { if (m.polygonOffset) glEnable(GL_POLYGON_OFFSET_FILL) else glDisable(GL_POLYGON_OFFSET_FILL); curOffset = m.polygonOffset }
        if (m.polygonOffset && (m.polygonOffsetFactor != curOffF || m.polygonOffsetUnits != curOffU)) {
            glPolygonOffset(m.polygonOffsetFactor, m.polygonOffsetUnits); curOffF = m.polygonOffsetFactor; curOffU = m.polygonOffsetUnits
        }
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
    private val sp = FloatArray(3 * Shaders.MAX_SPOT); private val sd = FloatArray(3 * Shaders.MAX_SPOT); private val sc = FloatArray(3 * Shaders.MAX_SPOT)
    private val spar = FloatArray(4 * Shaders.MAX_SPOT); private val sshadow = IntArray(Shaders.MAX_SPOT)
    private val hs = FloatArray(3 * Shaders.MAX_HEMI); private val hg = FloatArray(3 * Shaders.MAX_HEMI); private val hd = FloatArray(3 * Shaders.MAX_HEMI)
    private val identity = Mat4()
    private val lv1 = Vec3(); private val lv2 = Vec3(); private val lv3 = Vec3()
    private fun setFrameUniforms(scene: Scene, camera: PerspectiveCamera) {
        glUniformMatrix4fv(L.projectionMatrix, 1, false, camera.projectionMatrix.e, 0)
        glUniform2f(L.jitterGrid, ps1Uniforms.jitterGrid[0], ps1Uniforms.jitterGrid[1])
        glUniform1f(L.affine, ps1Uniforms.affineStrength)
        glUniform3f(L.ambient, ambient.r, ambient.g, ambient.b)
        glUniform3f(L.fogColor, scene.fogColor.r, scene.fogColor.g, scene.fogColor.b)
        glUniform1f(L.fogDensity, scene.fogDensity)
        val view = camera.matrixWorldInverse
        for (i in points.indices) {
            val l = points[i]
            lv1.setFromMatrixPosition(l.matrixWorld).applyMatrix4(view)
            lightPosBuf[i * 3] = lv1.x; lightPosBuf[i * 3 + 1] = lv1.y; lightPosBuf[i * 3 + 2] = lv1.z
            lightColBuf[i * 3] = l.color.r * l.intensity; lightColBuf[i * 3 + 1] = l.color.g * l.intensity; lightColBuf[i * 3 + 2] = l.color.b * l.intensity
            lightDDBuf[i * 2] = l.distance; lightDDBuf[i * 2 + 1] = l.decay
        }
        glUniform1i(L.pointCount, points.size)
        if (points.isNotEmpty()) {
            glUniform3fv(L.pointPos, points.size, lightPosBuf, 0)
            glUniform3fv(L.pointColor, points.size, lightColBuf, 0)
            glUniform2fv(L.pointDD, points.size, lightDDBuf, 0)
        }
        var shadowSlot = 0
        for (i in spots.indices) {
            val l = spots[i]
            sshadow[i] = -1
            l.target.updateMatrixWorld()
            lv1.setFromMatrixPosition(l.matrixWorld)
            lv2.setFromMatrixPosition(l.target.matrixWorld)
            lv3.subVectors(lv1, lv2).applyMatrix4Dir(view).normalize()
            lv1.applyMatrix4(view)
            sp[i * 3] = lv1.x; sp[i * 3 + 1] = lv1.y; sp[i * 3 + 2] = lv1.z
            sd[i * 3] = lv3.x; sd[i * 3 + 1] = lv3.y; sd[i * 3 + 2] = lv3.z
            sc[i * 3] = l.color.r * l.intensity; sc[i * 3 + 1] = l.color.g * l.intensity; sc[i * 3 + 2] = l.color.b * l.intensity
            spar[i * 4] = cos(l.angle); spar[i * 4 + 1] = cos(l.angle * (1 - l.penumbra)); spar[i * 4 + 2] = l.distance; spar[i * 4 + 3] = l.decay
            if (l.castShadow && shadowSlot < 2) {
                val target = shadowTargets[l]
                if (target != null) {
                    sshadow[i] = shadowSlot
                    glActiveTexture(GL_TEXTURE2 + shadowSlot); glBindTexture(GL_TEXTURE_2D, target.depth)
                    glUniform1i(L.shadowMap[shadowSlot], 2 + shadowSlot)
                    glUniformMatrix4fv(L.shadowMatrix[shadowSlot], 1, false, l.shadow.matrix.e, 0)
                    glUniform1f(L.shadowBias[shadowSlot], l.shadow.bias)
                }
                shadowSlot++
            }
        }
        for (s in shadowSlot until 2) {
            glActiveTexture(GL_TEXTURE2 + s); glBindTexture(GL_TEXTURE_2D, whiteTex); glUniform1i(L.shadowMap[s], 2 + s)
            glUniformMatrix4fv(L.shadowMatrix[s], 1, false, identity.e, 0)
        }
        glUniform1i(L.spotCount, spots.size)
        if (spots.isNotEmpty()) {
            glUniform3fv(L.spotPos, spots.size, sp, 0)
            glUniform3fv(L.spotDir, spots.size, sd, 0)
            glUniform3fv(L.spotColor, spots.size, sc, 0)
            glUniform4fv(L.spotParams, spots.size, spar, 0)
            glUniform1iv(L.spotShadow, spots.size, sshadow, 0)
        }
        for (i in hemis.indices) {
            val h = hemis[i]
            lv1.setFromMatrixPosition(h.matrixWorld).normalize().applyMatrix4Dir(view).normalize()
            hs[i * 3] = h.color.r * h.intensity; hs[i * 3 + 1] = h.color.g * h.intensity; hs[i * 3 + 2] = h.color.b * h.intensity
            hg[i * 3] = h.groundColor.r * h.intensity; hg[i * 3 + 1] = h.groundColor.g * h.intensity; hg[i * 3 + 2] = h.groundColor.b * h.intensity
            hd[i * 3] = lv1.x; hd[i * 3 + 1] = lv1.y; hd[i * 3 + 2] = lv1.z
        }
        glUniform1i(L.hemiCount, hemis.size)
        if (hemis.isNotEmpty()) {
            glUniform3fv(L.hemiSky, hemis.size, hs, 0)
            glUniform3fv(L.hemiGround, hemis.size, hg, 0)
            glUniform3fv(L.hemiDir, hemis.size, hd, 0)
        }
        // samplers fixed to their units
        glUniform1i(L.map, 0); glUniform1i(L.emMap, 1)
    }

    // ---- 3. Post ----
    private val pv1 = Vec3(); private val pv2 = Vec3(); private val pv3 = Vec3(); private val postPV = Mat4()
    private fun setPostUniforms(camera: PerspectiveCamera) {
        val p = P
        glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, sceneColor); glUniform1i(p.tDiffuse, 0)
        glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_2D, sceneDepth); glUniform1i(p.tDepth, 1)
        val ray = post.rayLight
        val st = if (ray != null) shadowTargets[ray] else null
        glActiveTexture(GL_TEXTURE2); glBindTexture(GL_TEXTURE_2D, st?.depth ?: whiteTex); glUniform1i(p.tShadowMap, 2)
        glUniform1f(p.hasDepth, 1f)
        glUniform2f(p.resolution, internalW.toFloat(), internalH.toFloat())
        glUniform1f(p.dither, post.ditherStrength)
        glUniform1f(p.volumetric, if (st != null) post.volumetricStrength else 0f)
        glUniform1f(p.ao, post.aoStrength)
        glUniform1i(p.steps, post.marchSteps)
        glUniformMatrix4fv(p.invProj, 1, false, camera.projectionMatrixInverse.e, 0)
        glUniformMatrix4fv(p.invView, 1, false, camera.matrixWorld.e, 0)
        pv1.setFromMatrixPosition(camera.matrixWorld)
        glUniform3f(p.cameraPos, pv1.x, pv1.y, pv1.z)
        if (ray != null) {
            val sc = ray.shadow.camera
            postPV.multiplyMatrices(sc.projectionMatrix, sc.matrixWorldInverse)
            glUniformMatrix4fv(p.shadowMatrix, 1, false, postPV.e, 0)
            pv2.setFromMatrixPosition(ray.matrixWorld)
            pv3.setFromMatrixPosition(ray.target.matrixWorld)
            pv3.sub(pv2).normalize()
            glUniform3f(p.lightPos, pv2.x, pv2.y, pv2.z)
            glUniform3f(p.lightDir, pv3.x, pv3.y, pv3.z)
        }
        glUniform3f(p.lightColor, post.lightColor.r, post.lightColor.g, post.lightColor.b)
        glUniform1f(p.cosAngle, post.lightCosAngle)
        glUniform1f(p.penumbraCos, post.lightPenumbraCos)
        glUniform1f(p.fanOn, post.fanLightOn)
        glUniform3f(p.fanPos, post.fanLightPos.x, post.fanLightPos.y, post.fanLightPos.z)
        glUniform1f(p.time, post.time)
        glUniform3f(p.tint, post.gradeTint.x, post.gradeTint.y, post.gradeTint.z)
        glUniform1f(p.sat, post.gradeSat)
        glUniform1f(p.contrast, post.gradeContrast)
        glUniform1f(p.vignette, post.vignette)
    }

    // ---------------------------------------------------------------- uploads
    private val uploadedGeometries = ArrayList<Geometry>()
    private val uploadedTextures = ArrayList<Texture>()

    private fun upload(g: Geometry) {
        if (g.uploaded) return
        val vao = IntArray(1); glGenVertexArrays(1, vao, 0); g.vao = vao[0]
        glBindVertexArray(g.vao)
        val b = IntArray(5); glGenBuffers(5, b, 0); g.vbo = b
        glBindBuffer(GL_ARRAY_BUFFER, b[0]); glBufferData(GL_ARRAY_BUFFER, g.positions.size * 4, floats(g.positions), GL_STATIC_DRAW)
        glEnableVertexAttribArray(0); glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0)
        glBindBuffer(GL_ARRAY_BUFFER, b[1]); glBufferData(GL_ARRAY_BUFFER, g.normals.size * 4, floats(g.normals), GL_STATIC_DRAW)
        glEnableVertexAttribArray(1); glVertexAttribPointer(1, 3, GL_FLOAT, false, 0, 0)
        glBindBuffer(GL_ARRAY_BUFFER, b[2]); glBufferData(GL_ARRAY_BUFFER, g.uvs.size * 4, floats(g.uvs), GL_STATIC_DRAW)
        glEnableVertexAttribArray(2); glVertexAttribPointer(2, 2, GL_FLOAT, false, 0, 0)
        val cols = g.colors
        if (cols != null) {
            glBindBuffer(GL_ARRAY_BUFFER, b[4]); glBufferData(GL_ARRAY_BUFFER, cols.size * 4, floats(cols), GL_STATIC_DRAW)
            glEnableVertexAttribArray(3); glVertexAttribPointer(3, 3, GL_FLOAT, false, 0, 0)
        } else { glDisableVertexAttribArray(3); glVertexAttrib3f(3, 1f, 1f, 1f) }
        g.indices?.let { idx ->
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, b[3])
            val ib = ByteBuffer.allocateDirect(idx.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer().put(idx); ib.position(0)
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, idx.size * 4, ib, GL_STATIC_DRAW)
        }
        glBindVertexArray(0)
        curVao = -1
        g.uploaded = true
        uploadedGeometries += g
    }

    private var upPx = IntArray(0); private var upOut = IntArray(0); private var upBuf: ByteBuffer = ByteBuffer.allocateDirect(4)
    /** GL id for a texture, uploading it like a three.js CanvasTexture (flipY on, not premultiplied) when it changed. */
    private fun texId(t: Texture): Int {
        if (t.glId == 0) { val id = IntArray(1); glGenTextures(1, id, 0); t.glId = id[0]; uploadedTextures += t }
        if (t.uploadedVersion != t.version) {
            glActiveTexture(GL_TEXTURE7)
            glBindTexture(GL_TEXTURE_2D, t.glId)
            val bm = t.bitmap; val w = bm.width; val h = bm.height
            // Reused scratch buffers: canvases like the sky re-upload a few times a second, so no garbage per upload
            if (upPx.size < w * h) { upPx = IntArray(w * h); upOut = IntArray(w * h); upBuf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder()) }
            val px = upPx; val out = upOut
            bm.getPixels(px, 0, w, 0, 0, w, h)   // un-premultiplied ARGB
            // flipY, ARGB -> RGBA bytes (written as little-endian ints: 0xAABBGGRR)
            var o = 0
            for (y in h - 1 downTo 0) {
                val row = y * w
                for (x in 0 until w) { val c = px[row + x]; out[o++] = (c and 0xff00ff00.toInt()) or ((c shr 16) and 0xff) or ((c and 0xff) shl 16) }
            }
            val ib = upBuf.asIntBuffer(); ib.put(out, 0, w * h); upBuf.position(0)
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1)
            if (t.uploadedW == w && t.uploadedH == h) glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, upBuf)
            else { glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, upBuf); t.uploadedW = w; t.uploadedH = h }
            val wrap = if (t.wrapRepeat) GL_REPEAT else GL_CLAMP_TO_EDGE
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wrap); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, wrap)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, if (t.nearest) GL_NEAREST else GL_LINEAR)
            if (t.mipmaps) { glGenerateMipmap(GL_TEXTURE_2D); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_NEAREST) }
            else glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, if (t.nearest && !t.linearMin) GL_NEAREST else GL_LINEAR)
            t.uploadedVersion = t.version
            curTex0 = -1; curTex1 = -1
        }
        return t.glId
    }

    // ---------------------------------------------------------------- GL helpers
    private fun nearest(wrap: Int) {
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wrap); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, wrap)
    }
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
