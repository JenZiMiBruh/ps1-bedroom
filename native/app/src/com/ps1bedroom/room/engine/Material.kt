package com.ps1bedroom.room.engine

import android.graphics.Bitmap

/** A GL texture from a Bitmap (the HTML's CanvasTexture). Nearest filtering, optional repeat, like createCanvasTex. */
class Texture(@JvmField var bitmap: Bitmap) {
    @JvmField var repeatX = 1f
    @JvmField var repeatY = 1f
    @JvmField var offsetX = 0f
    @JvmField var offsetY = 0f
    @JvmField var wrapRepeat = false
    @JvmField var nearest = true
    @JvmField var mipmaps = false
    /** Bump when the bitmap is redrawn so the renderer re-uploads it (three's needsUpdate). */
    @JvmField var version = 1
    @JvmField var glId = 0
    @JvmField var uploadedVersion = 0
    fun needsUpdate() { version++ }
}

const val FRONT_SIDE = 0
const val BACK_SIDE = 1
const val DOUBLE_SIDE = 2

/**
 * The PS1 material (makePS1Material in the HTML): MeshPhongMaterial with specular 0, plus vertex snapping,
 * affine texture warp, a weathering "grime" map on flat-coloured surfaces and large-scale macro variation.
 */
class Material(
    @JvmField val color: Color = Color(0xffffff),
    @JvmField var map: Texture? = null,
    @JvmField val emissive: Color = Color(0x000000),
    @JvmField var emissiveIntensity: Float = 1f,
    @JvmField var emissiveMap: Texture? = null,
    @JvmField var transparent: Boolean = false,
    @JvmField var opacity: Float = 1f,
    @JvmField var side: Int = FRONT_SIDE,
    @JvmField var depthWrite: Boolean = true,
    @JvmField var depthTest: Boolean = true,
    @JvmField var polygonOffset: Boolean = false,
    @JvmField var polygonOffsetFactor: Float = 0f,
    @JvmField var polygonOffsetUnits: Float = 0f,
    @JvmField var visible: Boolean = true,
    /** false for plain unlit materials (three's MeshBasicMaterial) */
    @JvmField var lit: Boolean = true,
    /** PS1 vertex snap + affine warp (off for MeshBasicMaterial / sky layers) */
    @JvmField var ps1: Boolean = true,
    @JvmField var macro: Boolean = false,
    @JvmField var fog: Boolean = true
)

/** makePS1Material from the HTML, same defaults and the same grime / macro rules. */
object PS1 {
    lateinit var grimeTextures: List<Texture>
    private var grimeCursor = 0

    fun material(
        color: Int = 0xffffff, map: Texture? = null, emissive: Int = 0x000000, emissiveIntensity: Float? = null,
        emissiveMap: Texture? = null, transparent: Boolean = false, opacity: Float = 1f, side: Int = FRONT_SIDE,
        polygonOffset: Boolean = false, polygonOffsetFactor: Float = 0f, polygonOffsetUnits: Float = 0f,
        depthWrite: Boolean = true, noMacro: Boolean = false
    ): Material {
        var m = map
        // Flat-coloured surfaces get a subtle weathering map; glowing and see-through parts stay clean
        // (same test as the HTML: an explicit emissiveIntensity above 0.5 skips it)
        if (m == null && !transparent && !((emissiveIntensity ?: 0f) > 0.5f)) {
            m = grimeTextures[grimeCursor++ % grimeTextures.size]
        }
        val macro = !noMacro && emissiveMap == null && !transparent
        return Material(
            color = Color(color), map = m, emissive = Color(emissive), emissiveIntensity = emissiveIntensity ?: 1f, emissiveMap = emissiveMap,
            transparent = transparent, opacity = opacity, side = side, depthWrite = depthWrite,
            polygonOffset = polygonOffset, polygonOffsetFactor = polygonOffsetFactor, polygonOffsetUnits = polygonOffsetUnits,
            macro = macro
        )
    }

    /** three.js MeshBasicMaterial: unlit, no PS1 snapping. */
    fun basic(color: Int = 0xffffff, map: Texture? = null, transparent: Boolean = false, opacity: Float = 1f,
              side: Int = FRONT_SIDE, depthWrite: Boolean = true, fog: Boolean = true) =
        Material(color = Color(color), map = map, transparent = transparent, opacity = opacity, side = side,
            depthWrite = depthWrite, lit = false, ps1 = false, fog = fog)
}
