package com.ps1bedroom.room.engine

/** Binds a texture to one of the units reserved for custom shaders and returns the unit index for the sampler. */
fun interface TexBinder { fun bind(slot: Int, t: Texture): Int }

/**
 * three.js ShaderMaterial: your own GLSL (ES 3.00) with modelViewMatrix / projectionMatrix supplied by the renderer
 * (attributes: position at 0, normal 1, uv 2). Set it as Material.shader; the material still decides side, blending
 * and depth state. Uniform locations are looked up once per GL context in locate().
 */
abstract class CustomShader(@JvmField val vert: String, @JvmField val frag: String) {
    @JvmField var program = 0
    @JvmField var mvLoc = -1
    @JvmField var projLoc = -1
    open fun locate(p: Int) {}
    abstract fun apply(tex: TexBinder)
}
