package com.ps1bedroom.room

import com.ps1bedroom.room.engine.Canvas2D
import com.ps1bedroom.room.engine.PS1
import com.ps1bedroom.room.engine.Texture
import com.ps1bedroom.room.engine.Weather
import com.ps1bedroom.room.engine.createCanvasTex
import com.ps1bedroom.room.engine.rand
import kotlin.math.cos
import kotlin.math.sin

// The room's base textures, ported line for line from src/ps1_bedroom.html (lines 868-954 and the door).

class Textures {
    val grime: List<Texture> = listOf(3, 7, 13, 19).map { seed ->
        createCanvasTex(64, 64, weather = Weather(seed = seed.toDouble(), blotch = 0.24, speckle = 0.1, edge = 0.3, scratches = 4, smudges = 2, scuffs = 4)) { ctx, w, h ->
            ctx.fillStyle = "#ffffff"; ctx.fillRect(0, 0, w, h)
        }.tex
    }.also { PS1.grimeTextures = it }

    // Wall-to-wall carpet: dark navy berber loops, fibre speckle, a worn path and a couple of old stains
    val carpet = createCanvasTex(128, 128, 6f, 5f, Weather(seed = 61.0, blotch = 0.35, speckle = 0.3, stains = 1, scuffs = 6)) { ctx, w, h ->
        ctx.fillStyle = "#1a2340"; ctx.fillRect(0, 0, w, h)
        var y = 0
        while (y < h) {
            var x = (y / 2) % 2
            while (x < w) {
                ctx.fillStyle = if ((x * 7 + y * 3) % 5 == 0) "rgba(70, 86, 140, 0.35)" else "rgba(8, 12, 26, 0.25)"
                ctx.fillRect(x, y, 1, 1)
                x += 2
            }
            y += 2
        }
    }.tex

    // Popcorn ceiling with one old yellow water stain (single tile across the whole ceiling)
    val ceiling = createCanvasTex(256, 256, weather = Weather(seed = 71.0, blotch = 0.25, speckle = 0.08, smudges = 3)) { ctx, w, h ->
        ctx.fillStyle = "#3a4156"; ctx.fillRect(0, 0, w, h)
        repeat(9000) {
            val v = rand()
            ctx.fillStyle = if (v < 0.5) "rgba(255, 255, 255, 0.10)" else "rgba(0, 0, 0, 0.16)"
            ctx.fillRect((rand() * w).toInt(), (rand() * h).toInt(), 1 + (if (v > 0.92) 1 else 0), 1)
        }
        val sx = 168; val sy = 92
        var r = 26
        while (r > 4) {
            ctx.strokeStyle = "rgba(150, 110, 50, ${0.1 + (26 - r) * 0.004})"
            ctx.lineWidth = 2f
            ctx.beginPath(); ctx.ellipse(sx, sy, r * 1.3, r, 0.4, 0, Math.PI * 2); ctx.stroke()
            r -= 5
        }
    }.tex

    val wood = createCanvasTex(128, 128, weather = Weather(seed = 11.0, blotch = 0.24, speckle = 0.06, scratches = 18, edge = 0.18, scuffs = 3)) { ctx, w, h ->
        ctx.fillStyle = "#362318"; ctx.fillRect(0, 0, w, h)
        for (y in 0 until h) for (x in 0 until w) {
            val grain = sin(x * 0.11 + sin(y * 0.25) * 2.5) * 14
            val fine = rand() * 10 - 5
            val n = grain + fine
            val r = (58 + n).coerceIn(0.0, 255.0).toInt(); val g = (38 + n * 0.72).coerceIn(0.0, 255.0).toInt(); val b = (26 + n * 0.5).coerceIn(0.0, 255.0).toInt()
            ctx.fillStyle = "rgb($r,$g,$b)"; ctx.fillRect(x, y, 1, 1)
        }
        ctx.strokeStyle = "rgba(22, 12, 6, 0.55)"; ctx.lineWidth = 2f
        ctx.beginPath(); ctx.arc(92, 84, 11, 0, Math.PI * 1.85); ctx.stroke()
        ctx.beginPath(); ctx.arc(95, 86, 10, 0.4, Math.PI * 1.5); ctx.stroke()
        ctx.fillStyle = "rgba(120, 88, 62, 0.14)"; ctx.fillRect(38, 78, 44, 26)
    }.tex

    val wall = createCanvasTex(128, 128, 4f, 2f, Weather(seed = 21.0, blotch = 0.38, speckle = 0.07, smudges = 5, stains = 1, scuffs = 8)) { ctx, w, h ->
        for (y in 0 until h) for (x in 0 until w) {
            val v = (rand() * 10 - 5).toInt()
            val smudge = sin(x * 0.06) * cos(y * 0.06) * 4
            ctx.fillStyle = "rgb(${(28 + v + smudge).toInt()}, ${(38 + v + smudge).toInt()}, ${(68 + v + smudge).toInt()})"
            ctx.fillRect(x, y, 1, 1)
        }
    }.tex

    val rug = createCanvasTex(128, 128, weather = Weather(seed = 31.0, blotch = 0.32, speckle = 0.16, stains = 3, worn = 0.22, scuffs = 6)) { ctx, w, h ->
        ctx.fillStyle = "#121932"; ctx.fillRect(0, 0, w, h)
        ctx.strokeStyle = "#2d407a"; ctx.lineWidth = 4f; ctx.strokeRect(6, 6, w - 12, h - 12)
        ctx.strokeStyle = "#4c1d95"; ctx.lineWidth = 2f; ctx.strokeRect(12, 12, w - 24, h - 24)
        ctx.strokeStyle = "#1e3a8a"
        ctx.beginPath(); ctx.moveTo(w / 2, 20); ctx.lineTo(w - 20, h / 2); ctx.lineTo(w / 2, h - 20); ctx.lineTo(20, h / 2); ctx.closePath(); ctx.stroke()
        for (i in 0 until 650) {
            ctx.fillStyle = if (i % 3 == 0) "#1d2952" else if (i % 2 == 0) "#0f152b" else "#263466"
            ctx.fillRect((rand() * w).toInt(), (rand() * h).toInt(), 2, 2)
        }
    }.tex

    // Six-panel colonial door
    val door = createCanvasTex(128, 256, weather = Weather(seed = 50.0, blotch = 0.22, speckle = 0.05, scratches = 12, edge = 0.15, smudges = 4, scuffs = 10)) { ctx, w, h ->
        ctx.fillStyle = "#4a3222"; ctx.fillRect(0, 0, w, h)
        var y = 0
        while (y < h) { ctx.fillStyle = if (y % 4 == 0) "rgba(30, 16, 8, 0.18)" else "rgba(90, 62, 40, 0.12)"; ctx.fillRect(0, y, w, 1); y += 2 }
        ctx.strokeStyle = "#29190f"; ctx.lineWidth = 4f; ctx.strokeRect(2, 2, w - 4, h - 4)
        val rows = listOf(18 to 46, 78 to 74, 166 to 72)
        for ((ry, rh) in rows) for (cx in listOf(14, 70)) {
            ctx.fillStyle = "#362216"; ctx.fillRect(cx, ry, 44, rh)
            ctx.strokeStyle = "#1f120a"; ctx.lineWidth = 2f; ctx.strokeRect(cx, ry, 44, rh)
            ctx.fillStyle = "#422a1c"; ctx.fillRect(cx + 4, ry + 4, 36, rh - 8)
        }
    }.tex
}

@Suppress("unused") private fun unusedCanvas(c: Canvas2D) = c
