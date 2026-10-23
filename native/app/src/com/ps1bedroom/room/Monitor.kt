package com.ps1bedroom.room

import com.ps1bedroom.room.engine.Canvas2D
import com.ps1bedroom.room.engine.CanvasTex
import com.ps1bedroom.room.engine.Material
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/**
 * HTML 956-1428 + 10628 + the 'pc' tap: the PC monitor's screen. The XP desktop (cached wallpaper, icons, buddy list,
 * WinBeats with its live equalizer and the taskbar clock) and four 2001-02 PC game parodies, all drawn at 128 x 96
 * into the 256 x 192 screen with CRT scanlines and a vignette. Tap the tower to power it, the screen tile to switch.
 */
class Monitor(private val mon: CanvasTex, private val screenMat: Material, private val room: Room, private val pcLedMat: Material, private val env: Environment) {
    var currentChannel = 0; private set
    var pcPoweredOn = true; private set
    companion object {
        val CHANNEL_NAMES = arrayOf(
            "WinXP Desktop + Winamp + AIM Messenger (2002)",
            "Counter-Strikeout 1.5: de_dustbin (2002 FPS Parody)",
            "Grand Theft Carts III: Liberty Suburbs (2001 Parody)",
            "War-Crafty III: Rain of Chaos (2002 RTS Parody)",
            "Max Payne-Killer: Bullet-Time Noir (2001 Parody)"
        )
        val CHANNEL_LIGHT_COLORS = intArrayOf(0x00d8ff, 0xfacc15, 0x38bdf8, 0x22c55e, 0xf97316)
    }

    // Pre-rendered static desktop wallpaper & icons, so per-frame updates only draw the equalizer
    private val xpStatic = Canvas2D(256, 192).also { c ->
        c.setTransform(2, 0, 0, 2, 0, 0)
        drawXpWallpaper(c)
        val iconPalette = arrayOf("#facc15", "#38bdf8", "#f87171", "#f8fafc", "#4ade80", "#c084fc", "#fb923c")
        for (col in 0 until 7) {
            val maxRows = if (col < 3) 8 else (if (col < 5) 7 else 5)
            for (row in 0 until maxRows) {
                if ((col * 5 + row * 3) % 7 == 0) continue
                val ix = 5 + col * 13.0; val iy = 4 + row * 10.0
                c.fillStyle = "rgba(0,0,0,0.45)"; c.fillRect(ix + 0.5, iy + 0.5, 5, 5)
                c.fillStyle = iconPalette[(col + row) % iconPalette.size]; c.fillRect(ix, iy, 4.5, 4.5)
                c.fillStyle = "#ffffff"; c.fillRect(ix + 1, iy + 1, 2, 1.5)
                c.fillStyle = "rgba(8, 28, 68, 0.75)"; c.fillRect(ix - 2, iy + 5.5, 8.5, 2)
                c.fillStyle = "#e0f2fe"; c.fillRect(ix - 1, iy + 6, 6.5, 1)
            }
        }
        // AIM buddy list window (top right), promised by the channel name
        c.fillStyle = "#f8fafc"; c.fillRect(100, 4, 24, 44)
        c.fillStyle = "#1e40af"; c.fillRect(100, 4, 24, 4)
        c.fillStyle = "#fde047"; c.fillRect(101, 5, 2, 2)
        c.strokeStyle = "#64748b"; c.lineWidth = 0.5f; c.strokeRect(100, 4, 24, 44)
        c.fillStyle = "#1d4ed8"; c.fillRect(102, 10, 12, 1.5); c.fillRect(102, 24, 10, 1.5)
        c.fillStyle = "#334155"
        intArrayOf(13, 16, 19, 27, 30, 33, 36, 39).forEachIndexed { i, ly -> c.fillRect(104, ly, if (i % 3 == 0) 16 else 12, 1.2) }
        c.fillStyle = "#181824"; c.fillRect(80, 60, 44, 26)
        c.strokeStyle = "#64748b"; c.lineWidth = 1f; c.strokeRect(80, 60, 44, 26)
        c.fillStyle = "#334155"; c.fillRect(81, 61, 42, 3.5)
        c.fillStyle = "#1d4ed8"; c.fillRect(0, 88, 128, 8)
        c.fillStyle = "#3b82f6"; c.fillRect(0, 88, 128, 1.5)
        c.fillStyle = "#16a34a"; c.fillRect(0, 88, 22, 8)
        c.fillStyle = "#ffffff"; c.font = "bold 5px sans-serif"; c.fillText("start", 4, 94)
        c.fillStyle = "#0284c7"; c.fillRect(104, 88, 24, 8)
        c.setTransform(1, 0, 0, 1, 0, 0)
    }

    /** The 128 x 88 XP wallpaper: sky, clouds, sea, a sandbar with three palms (also the PC desktop's wallpaper). */
    fun drawXpWallpaper(ctx: Canvas2D) {
        val sky = ctx.createLinearGradient(0, 0, 0, 58)
        sky.addColorStop(0, "#1452b8"); sky.addColorStop(0.55, "#3b82f6"); sky.addColorStop(1, "#93c5fd")
        ctx.fillStyle = sky; ctx.fillRect(0, 0, 128, 58)
        ctx.fillStyle = "rgba(240, 249, 255, 0.92)"
        for (r in arrayOf(intArrayOf(20, 20, 22, 7), intArrayOf(34, 16, 18, 10), intArrayOf(78, 24, 28, 8), intArrayOf(90, 20, 16, 8), intArrayOf(14, 36, 34, 7), intArrayOf(56, 32, 22, 6))) ctx.fillRect(r[0], r[1], r[2], r[3])
        val sea = ctx.createLinearGradient(0, 58, 0, 88)
        sea.addColorStop(0, "#0369a1"); sea.addColorStop(0.35, "#0891b2"); sea.addColorStop(0.75, "#06b6d4"); sea.addColorStop(1, "#2dd4bf")
        ctx.fillStyle = sea; ctx.fillRect(0, 58, 128, 30)
        ctx.fillStyle = "#fef3c7"
        ctx.beginPath(); ctx.moveTo(74, 58); ctx.quadraticCurveTo(96, 53, 122, 58); ctx.lineTo(122, 61); ctx.lineTo(74, 60); ctx.fill()
        ctx.strokeStyle = "#451a03"; ctx.lineWidth = 1.5f
        for (p in arrayOf(intArrayOf(92, 56, 89, 47, 85, 39), intArrayOf(101, 56, 103, 48, 107, 41), intArrayOf(96, 56, 95, 49, 94, 44))) { ctx.beginPath(); ctx.moveTo(p[0], p[1]); ctx.quadraticCurveTo(p[2], p[3], p[4], p[5]); ctx.stroke() }
        ctx.fillStyle = "#14532d"
        for (r in arrayOf(intArrayOf(81, 37, 9, 3), intArrayOf(84, 35, 6, 4), intArrayOf(103, 39, 9, 3), intArrayOf(105, 37, 6, 4), intArrayOf(91, 42, 7, 3))) ctx.fillRect(r[0], r[1], r[2], r[3])
    }

    private var vignette: Canvas2D.Gradient? = null
    fun update(t: Float) {
        val ctx = mon.ctx
        ctx.save()
        if (!pcPoweredOn) {
            ctx.setTransform(2, 0, 0, 2, 0, 0)
            ctx.fillStyle = "#050608"; ctx.fillRect(0, 0, 128, 96)
            ctx.fillStyle = "#0e1320"; ctx.fillRect(12, 10, 26, 16)
            ctx.restore(); ctx.setTransform(1, 0, 0, 1, 0, 0)
            mon.tex.needsUpdate()
            return
        }
        when (currentChannel) {
            0 -> {
                // Fast blit of the cached XP desktop, then the WinBeats equalizer
                ctx.setTransform(1, 0, 0, 1, 0, 0)
                ctx.drawImage(xpStatic, 0, 0)
                ctx.setTransform(2, 0, 0, 2, 0, 0)
                for (b in 0 until 10) {
                    val bh = max(2, floor((sin(t * 8 + b * 1.1) * 0.5 + 0.5) * 13).toInt())
                    ctx.fillStyle = if (bh > 9) "#ef4444" else (if (bh > 5) "#facc15" else "#22c55e")
                    ctx.fillRect(83 + b * 3.8, 83 - bh, 2.8, bh)
                }
            }
            1 -> {
                // PARODY 1: "COUNTER-STRIKEOUT 1.5: DE_DUSTBIN"
                ctx.setTransform(2, 0, 0, 2, 0, 0)
                ctx.fillStyle = "#7dd3fc"; ctx.fillRect(0, 0, 128, 34)
                ctx.fillStyle = "#d6b37c"; ctx.fillRect(0, 10, 38, 52); ctx.fillRect(88, 8, 40, 54)
                ctx.fillStyle = "#b8925a"; ctx.fillRect(38, 14, 50, 44)
                ctx.fillStyle = "#292018"; ctx.fillRect(50, 24, 26, 34)
                ctx.fillStyle = "#cfa96e"; ctx.fillRect(0, 58, 128, 38)
                ctx.fillStyle = "#854d0e"; ctx.fillRect(16, 40, 20, 20)
                ctx.strokeStyle = "#422006"; ctx.lineWidth = 1f; ctx.strokeRect(16, 40, 20, 20)
                val enemyX = 58 + sin(t * 3.8) * 7
                ctx.fillStyle = "#1e293b"; ctx.fillRect(enemyX, 34, 6, 10)
                ctx.fillStyle = "#fca5a5"; ctx.fillRect(enemyX + 1, 30, 4, 4)
                ctx.fillStyle = "#334155"; ctx.fillRect(enemyX, 44, 2.5, 8); ctx.fillRect(enemyX + 3.5, 44, 2.5, 8)
                val recoil = if (sin(t * 14) > 0.35f) 2 else 0
                if (recoil > 0) { ctx.fillStyle = "#fef08a"; ctx.beginPath(); ctx.arc(82, 54, 7, 0, PI * 2); ctx.fill() }
                ctx.fillStyle = "#1e293b"
                ctx.beginPath(); ctx.moveTo(80 + recoil, 54 + recoil); ctx.lineTo(128, 76); ctx.lineTo(128, 96); ctx.lineTo(74 + recoil, 64 + recoil); ctx.closePath(); ctx.fill()
                ctx.fillStyle = "#78350f"; ctx.fillRect(88 + recoil, 64 + recoil, 14, 10)
                ctx.fillStyle = "rgba(6, 78, 59, 0.72)"; ctx.beginPath(); ctx.arc(14, 14, 10, 0, PI * 2); ctx.fill()
                ctx.fillStyle = "#4ade80"; ctx.fillRect(13, 13, 2, 2)
                ctx.fillStyle = "#ef4444"; ctx.fillRect(16, 9, 2, 2)
                ctx.fillStyle = "#fbbf24"; ctx.font = "bold 5px monospace"; ctx.fillText("pwnz0r [AK47] Camper02", 52, 8)
                ctx.strokeStyle = "#22c55e"; ctx.lineWidth = 1f
                ctx.beginPath(); ctx.moveTo(64, 41); ctx.lineTo(64, 44); ctx.moveTo(64, 48); ctx.lineTo(64, 51); ctx.moveTo(59, 46); ctx.lineTo(62, 46); ctx.moveTo(66, 46); ctx.lineTo(69, 46); ctx.stroke()
                ctx.fillStyle = "rgba(0,0,0,0.55)"; ctx.fillRect(0, 84, 128, 12)
                ctx.fillStyle = "#f59e0b"; ctx.font = "bold 6px monospace"; ctx.fillText("+100  🛡100   01:42   \$16000  30|90", 3, 92)
            }
            2 -> {
                // PARODY 2: "GRAND THEFT CARTS III: LIBERTY SUBURBS"
                ctx.setTransform(2, 0, 0, 2, 0, 0)
                val gSky = ctx.createLinearGradient(0, 0, 0, 42); gSky.addColorStop(0, "#0f172a"); gSky.addColorStop(1, "#475569")
                ctx.fillStyle = gSky; ctx.fillRect(0, 0, 128, 42)
                ctx.fillStyle = "#1e293b"
                ctx.beginPath(); ctx.moveTo(0, 8); ctx.lineTo(46, 38); ctx.lineTo(46, 52); ctx.lineTo(0, 74); ctx.fill()
                ctx.beginPath(); ctx.moveTo(128, 6); ctx.lineTo(82, 38); ctx.lineTo(82, 52); ctx.lineTo(128, 74); ctx.fill()
                ctx.fillStyle = "#334155"; ctx.beginPath(); ctx.moveTo(46, 42); ctx.lineTo(82, 42); ctx.lineTo(128, 96); ctx.lineTo(0, 96); ctx.fill()
                ctx.strokeStyle = "#facc15"; ctx.lineWidth = 2f
                val dashOffset = (t * 45) % 16
                for (d in 0 until 4) { val y1 = 44 + d * 13 + dashOffset * 0.6f; if (y1 < 92) { ctx.beginPath(); ctx.moveTo(64, y1); ctx.lineTo(64, y1 + 6); ctx.stroke() } }
                val carX = 64 + sin(t * 2.4f) * 10
                ctx.fillStyle = "#090d16"; ctx.fillRect(carX - 15, 76, 30, 5)
                ctx.fillStyle = "#1d4ed8"; ctx.fillRect(carX - 14, 64, 28, 12)
                ctx.fillStyle = "#60a5fa"; ctx.fillRect(carX - 11, 57, 22, 7)
                ctx.fillStyle = "#1e293b"; ctx.fillRect(carX - 9, 58, 18, 5)
                ctx.fillStyle = "#ef4444"; ctx.fillRect(carX - 13, 66, 6, 3); ctx.fillRect(carX + 7, 66, 6, 3)
                ctx.fillStyle = "#fef08a"; ctx.fillRect(carX - 3, 67, 6, 3)
                ctx.fillStyle = "#0f172a"; ctx.beginPath(); ctx.arc(16, 78, 12, 0, PI * 2); ctx.fill()
                ctx.strokeStyle = "#94a3b8"; ctx.lineWidth = 1.5f; ctx.stroke()
                ctx.fillStyle = "#38bdf8"; ctx.fillRect(15, 77, 3, 3)
                ctx.fillStyle = "#f59e0b"; ctx.font = "bold 6px monospace"; ctx.fillText("23:14", 98, 9)
                ctx.fillStyle = "#22c55e"; ctx.fillText("\$00420690", 84, 16)
                ctx.fillStyle = "#ef4444"; ctx.fillText("♥100 🛡85", 86, 23)
                ctx.fillStyle = if (sin(t * 10) > 0) "#facc15" else "#ca8a04"; ctx.fillText("★★★★★☆", 86, 30)
            }
            3 -> {
                // PARODY 3: "WAR-CRAFTY III: RAIN OF CHAOS"
                ctx.setTransform(2, 0, 0, 2, 0, 0)
                ctx.fillStyle = "#1c1917"; ctx.fillRect(0, 0, 128, 9)
                ctx.fillStyle = "#facc15"; ctx.font = "bold 5px monospace"; ctx.fillText("🪙1450  🌲620  🍖48/90 LOW UPKEEP", 4, 6)
                ctx.fillStyle = "#14532d"; ctx.fillRect(0, 9, 128, 59)
                ctx.fillStyle = "#166534"; ctx.fillRect(14, 16, 96, 44)
                ctx.fillStyle = "#78350f"; ctx.beginPath(); ctx.arc(22, 26, 10, 0, PI * 2); ctx.fill()
                ctx.fillStyle = "#facc15"; ctx.fillRect(18, 24, 8, 6)
                for (u in 0 until 6) {
                    val isOrc = u < 3
                    val ux = (if (isOrc) 44 else 68) + sin(t * 3.2f + u) * 5 + (u % 3) * 6
                    val uy = 24 + (u % 3) * 11 + cos(t * 2.7f + u) * 2
                    ctx.strokeStyle = "#22c55e"; ctx.lineWidth = 1f
                    ctx.beginPath(); ctx.ellipse(ux + 3, uy + 8, 5, 2.5, 0, 0, PI * 2); ctx.stroke()
                    ctx.fillStyle = if (isOrc) "#16a34a" else "#2563eb"; ctx.fillRect(ux, uy, 6, 7)
                    ctx.fillStyle = if (isOrc) "#dc2626" else "#cbd5e1"; ctx.fillRect(ux - 1, uy + 1, 8, 3)
                    ctx.fillStyle = "#000000"; ctx.fillRect(ux - 1, uy - 3, 8, 2)
                    ctx.fillStyle = "#22c55e"; ctx.fillRect(ux - 1, uy - 3, 6, 2)
                }
                ctx.fillStyle = "#292524"; ctx.fillRect(0, 68, 128, 28)
                ctx.strokeStyle = "#ca8a04"; ctx.lineWidth = 1.5f; ctx.strokeRect(1, 69, 126, 26)
                ctx.fillStyle = "#052e16"; ctx.fillRect(4, 71, 24, 22)
                ctx.fillStyle = "#22c55e"; ctx.fillRect(10, 76, 3, 3)
                ctx.fillStyle = "#ef4444"; ctx.fillRect(18, 82, 3, 3)
                ctx.fillStyle = "#0f172a"; ctx.fillRect(34, 71, 24, 22)
                ctx.fillStyle = "#15803d"; ctx.fillRect(40, 74, 12, 14)
                ctx.fillStyle = "#ef4444"; ctx.fillRect(42, 77, 2, 2); ctx.fillRect(48, 77, 2, 2)
                val jawY = abs(sin(t * 9)) * 2
                ctx.fillStyle = "#fef08a"; ctx.fillRect(41, 83 + jawY, 2, 3); ctx.fillRect(49, 83 + jawY, 2, 3)
                val cmd = arrayOf("#dc2626", "#2563eb", "#eab308", "#16a34a", "#9333ea", "#06b6d4")
                for (r in 0 until 3) for (c in 0 until 4) { ctx.fillStyle = cmd[(r * 4 + c) % cmd.size]; ctx.fillRect(88 + c * 9.5, 71 + r * 7.5, 8, 6.5) }
            }
            4 -> {
                // PARODY 4: "MAX PAYNE-KILLER: BULLET-TIME NOIR"
                ctx.setTransform(2, 0, 0, 2, 0, 0)
                ctx.fillStyle = "#1e293b"; ctx.fillRect(0, 0, 128, 96)
                ctx.fillStyle = "#334155"; ctx.fillRect(0, 14, 128, 48)
                ctx.fillStyle = "#0f172a"; ctx.fillRect(22, 8, 12, 64); ctx.fillRect(92, 8, 12, 64)
                ctx.fillStyle = "#1e222d"; ctx.fillRect(0, 62, 128, 34)
                val diveX = 48 + sin(t * 2.0f) * 8; val diveY = 52 + cos(t * 4.0f) * 3
                ctx.fillStyle = "#090b10"
                ctx.save(); ctx.translate(diveX, diveY); ctx.rotate(-0.95)
                ctx.fillRect(-6, -12, 12, 26)
                ctx.fillStyle = "#fecaca"; ctx.fillRect(-3, -17, 6, 5)
                ctx.fillStyle = "#dc2626"; ctx.fillRect(2, -10, 6, 2)
                ctx.restore()
                ctx.strokeStyle = "rgba(253, 224, 71, 0.85)"; ctx.lineWidth = 1.5f
                ctx.beginPath(); ctx.moveTo(diveX + 12, diveY - 4); ctx.lineTo(112, 36); ctx.moveTo(diveX + 10, diveY + 2); ctx.lineTo(116, 44); ctx.stroke()
                val ringX = 72 + ((t * 35) % 36)
                ctx.strokeStyle = "rgba(226, 232, 240, 0.65)"; ctx.beginPath(); ctx.ellipse(ringX, 40, 2, 5, 0.2, 0, PI * 2); ctx.stroke()
                ctx.fillStyle = "rgba(0, 0, 0, 0.65)"; ctx.fillRect(4, 68, 28, 24)
                ctx.fillStyle = "#ffffff"; ctx.fillRect(8, 72, 6, 16)
                ctx.fillStyle = "#dc2626"; ctx.fillRect(8, 78, 6, 10)
                ctx.fillStyle = "#f59e0b"; ctx.fillRect(18, 73, 4, 15)
                ctx.fillStyle = "#fde047"; ctx.font = "bold 5px monospace"; ctx.fillText("💊x4", 23, 86)
            }
        }
        ctx.restore()
        ctx.setTransform(1, 0, 0, 1, 0, 0)
        // The XP taskbar clock shows the room's time
        if (currentChannel == 0) {
            ctx.fillStyle = "#0284c7"; ctx.fillRect(208, 176, 48, 16)
            ctx.fillStyle = "#ffffff"; ctx.font = "bold 9px sans-serif"; ctx.textAlign = "center"
            ctx.fillText(env.formatHour(env.hour), 232, 188)
            ctx.textAlign = "left"
        }
        // CRT monitor: scanlines and a soft glow falloff toward the edges
        ctx.fillStyle = "rgba(0, 0, 0, 0.16)"
        var sy = 0; while (sy < 192) { ctx.fillRect(0, sy, 256, 1); sy += 2 }
        val v = vignette ?: ctx.createRadialGradient(128, 96, 70, 128, 96, 175).apply { addColorStop(0, "rgba(0,0,0,0)"); addColorStop(1, "rgba(0,0,0,0.45)") }.also { vignette = it }
        ctx.fillStyle = v; ctx.fillRect(0, 0, 256, 192)
        mon.tex.needsUpdate()
    }

    /** Tap the screen tile / the monitor's buttons: power on, or the next channel. Returns the toast. */
    fun cycleChannel(): String {
        if (!pcPoweredOn) setPower(true) else currentChannel = (currentChannel + 1) % CHANNEL_NAMES.size
        room.monitorLight.color.setHex(CHANNEL_LIGHT_COLORS[currentChannel])
        return "🖥 MONITOR: ${CHANNEL_NAMES[currentChannel]}"
    }
    fun setChannel(c: Int) { currentChannel = c.coerceIn(0, CHANNEL_NAMES.size - 1); room.monitorLight.color.setHex(CHANNEL_LIGHT_COLORS[currentChannel]) }
    /** Tap the tower. */
    fun setPower(on: Boolean) {
        pcPoweredOn = on
        screenMat.emissiveIntensity = if (on) 0.95f else 0.05f
        room.monitorLight.intensity = if (on) 1.55f else 0f
        pcLedMat.emissiveIntensity = if (on) 1.0f else 0f
        pcLedMat.color.setHex(if (on) 0x22ff55 else 0x0a2e16)
        if (on) room.monitorLight.color.setHex(CHANNEL_LIGHT_COLORS[currentChannel])
        update(0f) // the frame loop skips redraws while it's off, so blank it once here
    }
    /** animate(): the monitor flickers like the HTML's (green power LED stays solid while on). */
    fun tick(t: Float) {
        if (!pcPoweredOn) return
        room.monitorLight.intensity = 1.50f + sin(t * 19.0f) * 0.04f
        pcLedMat.emissiveIntensity = 1.0f
    }
}
