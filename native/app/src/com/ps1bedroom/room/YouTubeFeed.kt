package com.ps1bedroom.room

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30.*
import android.os.Handler
import android.os.Looper
import android.view.Surface
import com.ps1bedroom.room.engine.RenderTarget
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * YouTube on the TV, inside the 3D scene: the YouTube player draws into this SurfaceTexture via
 * VirtualDisplay / Presentation, and each video frame is copied into a texture that the CRT's
 * screen material uses in the 3D room.
 * GL thread only for update() and GL resources; surface is used by VirtualDisplay on UI thread.
 */
class YouTubeFeed(val w: Int, val h: Int) {
    private val oes: Int
    val surfaceTexture: SurfaceTexture
    val surface: Surface
    val rt = RenderTarget(w, h)
    @Volatile var frameReady = false
    private val texMatrix = FloatArray(16)
    private var prog = 0; private var vao = 0; private var uTex = 0; private var uMat = 0

    init {
        val t = IntArray(1); glGenTextures(1, t, 0); oes = t[0]
        glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oes)
        glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        // Must use main looper handler so listener reliably triggers across threads
        surfaceTexture = SurfaceTexture(oes).apply {
            setDefaultBufferSize(w, h)
            setOnFrameAvailableListener({ frameReady = true }, Handler(Looper.getMainLooper()))
        }
        surface = Surface(surfaceTexture)
        buildProgram()
    }

    private fun buildProgram() {
        val vs = """#version 300 es
in vec2 aPos;
out vec2 vUv;
void main() { vUv = aPos * 0.5 + 0.5; gl_Position = vec4(aPos, 0.0, 1.0); }"""
        val fs = """#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision mediump float;
uniform samplerExternalOES uTex;
uniform mat4 uMat;
in vec2 vUv;
out vec4 fragColor;
void main() { fragColor = vec4(texture(uTex, (uMat * vec4(vUv, 0.0, 1.0)).xy).rgb, 1.0); }"""
        fun sh(type: Int, src: String): Int { val s = glCreateShader(type); glShaderSource(s, src); glCompileShader(s); return s }
        prog = glCreateProgram(); glAttachShader(prog, sh(GL_VERTEX_SHADER, vs)); glAttachShader(prog, sh(GL_FRAGMENT_SHADER, fs))
        glBindAttribLocation(prog, 0, "aPos"); glLinkProgram(prog)
        uTex = glGetUniformLocation(prog, "uTex"); uMat = glGetUniformLocation(prog, "uMat")
        val quad = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        val buf = ByteBuffer.allocateDirect(quad.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(quad).also { it.position(0) }
        val a = IntArray(1); glGenVertexArrays(1, a, 0); vao = a[0]; glBindVertexArray(vao)
        val b = IntArray(1); glGenBuffers(1, b, 0); glBindBuffer(GL_ARRAY_BUFFER, b[0]); glBufferData(GL_ARRAY_BUFFER, quad.size * 4, buf, GL_STATIC_DRAW)
        glEnableVertexAttribArray(0); glVertexAttribPointer(0, 2, GL_FLOAT, false, 0, 0)
        glBindVertexArray(0)
    }

    /** Copies the newest video frame into [rt]. */
    fun update() {
        if (!frameReady) return
        frameReady = false
        try {
            surfaceTexture.updateTexImage()
            surfaceTexture.getTransformMatrix(texMatrix)
            rt.ensure()
            val prevFbo = IntArray(1); glGetIntegerv(GL_FRAMEBUFFER_BINDING, prevFbo, 0)
            val vp = IntArray(4); glGetIntegerv(GL_VIEWPORT, vp, 0)
            glBindFramebuffer(GL_FRAMEBUFFER, rt.fbo)
            glViewport(0, 0, w, h)
            glDisable(GL_DEPTH_TEST); glDisable(GL_BLEND); glDisable(GL_CULL_FACE)
            glUseProgram(prog)
            glActiveTexture(GL_TEXTURE0); glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oes); glUniform1i(uTex, 0)
            glUniformMatrix4fv(uMat, 1, false, texMatrix, 0)
            glBindVertexArray(vao); glDrawArrays(GL_TRIANGLE_STRIP, 0, 4); glBindVertexArray(0)
            glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
            glBindFramebuffer(GL_FRAMEBUFFER, prevFbo[0])
            glViewport(vp[0], vp[1], vp[2], vp[3])
        } catch (e: Exception) {
            android.util.Log.w("PS1Bedroom", "YouTubeFeed.update failed", e)
        }
    }

    fun release() { try { surface.release(); surfaceTexture.release() } catch (e: Exception) { } }
}
