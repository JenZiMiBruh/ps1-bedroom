package com.ps1bedroom.room

import com.ps1bedroom.room.engine.Canvas2D
import com.ps1bedroom.room.engine.PS1
import com.ps1bedroom.room.engine.Texture
import com.ps1bedroom.room.engine.Weather
import com.ps1bedroom.room.engine.createCanvasTex
import com.ps1bedroom.room.engine.makeFbm
import com.ps1bedroom.room.engine.makeNoiseField
import com.ps1bedroom.room.engine.makeRng
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

    // Painted plaster walls. Native redo: the old one stamped stains / smudges / scuffs that didn't wrap and a sine
    // swirl that didn't tile, so every repeat showed a seam. Everything here tiles, and Room maps it in world space
    // (Room.WALL_TILE metres per repeat) so the separate wall slabs line up with each other.
    val wall = createCanvasTex(128, 128) { ctx, w, h ->
        val broad = makeFbm(w, h, 21.0)                   // soft, uneven paint coverage
        val roller = makeNoiseField(w, h, 16.0, 22.0)     // faint vertical paint-roller lanes (sampled along x only)
        val grain = makeNoiseField(w, h, 2.0, 23.0)       // fine plaster tooth
        val rnd = makeRng(24.0)
        val d = ctx.getImageData()
        for (y in 0 until h) for (x in 0 until w) {
            val i = (y * w + x) * 4
            val m = 1.0 + (broad(x.toDouble(), y.toDouble()) - 0.5) * 0.16 + (roller(x.toDouble(), 0.0) - 0.5) * 0.06 +
                (grain(x.toDouble(), y.toDouble()) - 0.5) * 0.1 + (rnd() - 0.5) * 0.035
            d[i] = (30 * m).toInt().coerceIn(0, 255); d[i + 1] = (40 * m).toInt().coerceIn(0, 255); d[i + 2] = (70 * m).toInt().coerceIn(0, 255); d[i + 3] = 255
        }
        ctx.putImageData(d)
    }.tex.also { it.wrapRepeat = true }

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

    // ---- PC monitor screen (the desktop and its channels are drawn by the PC phase; HTML 973) ----
    val monCanvasObj = createCanvasTex(256, 192) { ctx, w, h ->
        // (placeholder until updateMonitorScreen is ported: the teal desktop colour)
        ctx.fillStyle = "#0f766e"; ctx.fillRect(0, 0, w, h)
    }
    val desktop get() = monCanvasObj.tex

    // ---- HTML 1431-1530: sticky note, speaker grille, PC front, keyboard ----
    val stickyNote = createCanvasTex(128, 128, weather = Weather(seed = 41.0, blotch = 0.16, speckle = 0.05, edge = 0.16, creases = 3)) { ctx, w, h ->
        ctx.fillStyle = "#ded49c"
        ctx.fillRect(0, 0, w, h)
        // Subtle paper grain & top adhesive strip shadow
        ctx.fillStyle = "#cfc384"
        ctx.fillRect(0, 0, w, 18)
        for (i in 0 until 300) {
            ctx.fillStyle = if (i % 2 == 0) "rgba(0,0,0,0.04)" else "rgba(255,255,255,0.05)"
            ctx.fillRect((rand() * w).toInt(), (rand() * h).toInt(), 2, 2)
        }
        // Handwritten marker text: "DO NOT TURN OFF"
        ctx.fillStyle = "#111827"
        ctx.font = "bold 22px \"Courier New\", monospace"
        ctx.fillText("DO NOT", 16, 52)
        ctx.font = "bold 20px \"Courier New\", monospace"
        ctx.fillText("TURN OFF", 10, 82)
        // Double underline
        ctx.fillStyle = "#991b1b"
        ctx.fillRect(10, 92, 104, 4)
        ctx.fillRect(18, 101, 86, 3)
    }.tex

    val speakerGrille = createCanvasTex(32, 128, weather = Weather(seed = 42.0, blotch = 0.15, speckle = 0.06)) { ctx, w, h ->
        ctx.fillStyle = "#687387"
        ctx.fillRect(0, 0, w, h)
        ctx.fillStyle = "#1e2430"
        var y = 8
        while (y < h - 8) { var x = 4; while (x < w - 4) { ctx.fillRect(x, y, 1.5, 1.5); x += 3 }; y += 3 }
    }.tex

    val pcFront = createCanvasTex(128, 256, weather = Weather(seed = 43.0, blotch = 0.2, speckle = 0.06, scratches = 10, edge = 0.16, warm = 0.4, smudges = 3)) { ctx, w, h ->
        val bodyGrad = ctx.createLinearGradient(0, 0, w, h)
        bodyGrad.addColorStop(0, "#7c8596"); bodyGrad.addColorStop(0.5, "#6b7385"); bodyGrad.addColorStop(1, "#525a69")
        ctx.fillStyle = bodyGrad
        ctx.fillRect(0, 0, w, h)
        ctx.strokeStyle = "#3b4252"; ctx.lineWidth = 3f
        ctx.strokeRect(3, 3, w - 6, h - 6)
        // Lower vertical sculpted ribs & HP badge panel
        ctx.fillStyle = "#475061"; ctx.fillRect(14, 152, w - 28, 94)
        ctx.strokeStyle = "#2e3440"; ctx.lineWidth = 2f; ctx.strokeRect(14, 152, w - 28, 94)
        // Vertical recessed groove in lower bezel
        ctx.fillStyle = "#2a303c"; ctx.fillRect(w / 2 - 4, 154, 8, 90)
        // Blue HP Pavilion badge
        ctx.fillStyle = "#1d4ed8"; ctx.fillRect(w / 2 - 13, 204, 26, 20)
        ctx.strokeStyle = "#93c5fd"; ctx.lineWidth = 1f; ctx.strokeRect(w / 2 - 13, 204, 26, 20)
        ctx.fillStyle = "#ffffff"; ctx.font = "bold 11px sans-serif"; ctx.fillText("hp", w / 2 - 7, 218)
        // Intel Inside case badge (native fix: below the front ports, where nothing covers it)
        ctx.fillStyle = "#0284c7"; ctx.fillRect(22, 206, 16, 13)
    }.tex

    val keyboard = createCanvasTex(128, 64, weather = Weather(seed = 44.0, blotch = 0.22, speckle = 0.14, edge = 0.2, smudges = 4, warm = 0.15)) { ctx, w, h ->
        ctx.fillStyle = "#1b202c"; ctx.fillRect(0, 0, w, h)
        ctx.fillStyle = "#333b4d"
        var fx = 8; while (fx < 100) { ctx.fillRect(fx, 6, 5, 5); fx += 7 }
        for (r in 0 until 4) for (c in 0 until 13) {
            val kx = 8 + c * 6.5; val ky = 16 + r * 8
            val isWorn = (r == 1 && c in 1..3) || (r == 2 && c in 1..3)
            ctx.fillStyle = if (isWorn) "#475569" else "#2e3646"
            ctx.fillRect(kx, ky, 5.5, 6.5)
        }
        ctx.fillStyle = "#3b4559"; ctx.fillRect(28, 48, 42, 7)
        ctx.fillStyle = "#52607a"; ctx.fillRect(42, 49, 14, 5)
        ctx.fillStyle = "#2e3646"; ctx.fillRect(102, 16, 20, 38)
        ctx.fillStyle = "#22c55e"; ctx.fillRect(104, 8, 3, 2); ctx.fillRect(110, 8, 3, 2)
    }.tex
}

@Suppress("unused") private fun unusedCanvas(c: Canvas2D) = c
