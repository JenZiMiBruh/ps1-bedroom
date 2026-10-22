package com.ps1bedroom.room

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

/**
 * The menus' colours (v0.44): light / dark / follow the phone / follow the room's clock, and a colour theme on top.
 * Every panel (menu, top bar, dock, toasts, boombox, start screen pills) reads its colours from here, and redraws
 * itself when [listeners] fire. UI thread only.
 */
object UiTheme {
    /** A colour theme: the hue the panels are tinted with (and how strongly), plus its two accent colours. */
    class Accent(val key: String, val name: String, val hue: Float, val sat: Float, val a1: Int, val a2: Int)
    val ACCENTS = listOf(
        Accent("ps1", "PS1 Night", 231f, 1f, 0xff77ee, 0x7ff3ff),
        Accent("memcard", "Memory Card", 215f, 0.32f, 0x6aa8ff, 0xc9d6ea),
        Accent("vapor", "Vaporwave", 284f, 0.9f, 0xff6ad5, 0x4df0d0),
        Accent("gameboy", "Game Boy", 84f, 0.7f, 0xa8d13a, 0xe4f7a8),
        Accent("sunset", "Sunset", 16f, 0.8f, 0xff8a3d, 0xffd23f),
        Accent("arcade", "Arcade", 352f, 0.85f, 0xff4d6d, 0x5ce1ff),
    )
    val MODES = listOf("auto" to "📱 Phone", "room" to "🌗 Room clock", "dark" to "🌙 Dark", "light" to "☀️ Light")

    var mode = "auto"; private set
    var accentKey = "ps1"; private set
    val accentInfo get() = ACCENTS.firstOrNull { it.key == accentKey } ?: ACCENTS[0]
    var isLight = false; private set
    /** "Room clock": light while the sun is up in the room (fed by the menu's status refresh). */
    private var roomDay = false
    private var phoneNight = true

    // ---- the tokens ----
    var panel = 0; private set
    var panel2 = 0; private set
    var line = 0; private set
    var lineHi = 0; private set
    var text = 0; private set
    var muted = 0; private set
    var accent = 0; private set
    var accent2 = 0; private set
    var gold = 0; private set
    var good = 0; private set
    var goodBg = 0; private set
    var activeBg = 0; private set
    var activeText = 0; private set
    var sep = 0; private set
    var shadow = 0; private set
    var field = 0; private set
    var scrim = 0; private set
    var toastLine = 0; private set
    var toastText = 0; private set

    val listeners = ArrayList<() -> Unit>()

    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences("ps1bedroom", Context.MODE_PRIVATE)
        mode = p.getString("uiMode", "auto") ?: "auto"
        accentKey = p.getString("uiAccent", "ps1") ?: "ps1"
        phoneNight = phoneIsNight(ctx)
        compute()
    }
    fun set(ctx: Context, mode: String? = null, accent: String? = null) {
        if (mode != null) this.mode = mode
        if (accent != null) accentKey = accent
        ctx.getSharedPreferences("ps1bedroom", Context.MODE_PRIVATE).edit().putString("uiMode", this.mode).putString("uiAccent", accentKey).apply()
        phoneNight = phoneIsNight(ctx)
        refresh(true)
    }
    /** The phone switched dark mode, or the room's sun came up / went down. */
    fun phoneChanged(ctx: Context) { val n = phoneIsNight(ctx); if (n != phoneNight) { phoneNight = n; if (mode == "auto") refresh(false) } }
    fun roomDaylight(day: Boolean) { if (day != roomDay) { roomDay = day; if (mode == "room") refresh(false) } }
    private fun phoneIsNight(ctx: Context) = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun refresh(force: Boolean) {
        val was = isLight; val oldAccent = accent
        compute()
        if (force || was != isLight || oldAccent != accent) for (l in listeners.toList()) l()
    }

    private fun hsv(h: Float, s: Float, v: Float, a: Int = 255) = Color.HSVToColor(a, floatArrayOf(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)))
    private fun mix(a: Int, b: Int, t: Float) = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(), (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(), (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt())
    /** A colour made darker / more saturated so it reads on a light panel. */
    private fun deepen(c: Int, v: Float): Int { val f = FloatArray(3); Color.colorToHSV(c, f); f[1] = (f[1] * 1.15f).coerceAtMost(1f); f[2] = minOf(f[2], v); return Color.HSVToColor(f) }

    private fun compute() {
        isLight = when (mode) { "light" -> true; "dark" -> false; "room" -> roomDay; else -> !phoneNight }
        val t = accentInfo; val h = t.hue; val k = t.sat
        val a1 = Color.rgb(t.a1 shr 16 and 255, t.a1 shr 8 and 255, t.a1 and 255)
        val a2 = Color.rgb(t.a2 shr 16 and 255, t.a2 shr 8 and 255, t.a2 and 255)
        if (!isLight) {
            panel = hsv(h, 0.68f * k, 0.11f, 240); panel2 = hsv(h, 0.67f * k, 0.235f, 230)
            line = hsv(h, 0.58f * k, 0.55f); lineHi = hsv(h, 0.5f * k, 0.84f)
            text = hsv(h, 0.16f * k, 1f); muted = hsv(h, 0.3f * k, 0.82f); sep = hsv(h, 0.5f * k, 0.6f)
            accent = a1; accent2 = a2; gold = Color.rgb(0xff, 0xd2, 0x3f)
            good = Color.rgb(0x22, 0xc5, 0x5e); goodBg = Color.rgb(0x0f, 0x2a, 0x1c)
            activeBg = mix(hsv(h, 0.68f * k, 0.13f), a1, 0.2f); activeText = Color.WHITE
            shadow = Color.BLACK; field = hsv(h, 0.7f * k, 0.09f); scrim = Color.argb(89, 0, 0, 0)
            toastLine = mix(a1, Color.rgb(0xc0, 0x84, 0xfc), 0.5f); toastText = mix(text, a1, 0.25f)
        } else {
            panel = hsv(h, 0.06f * k + 0.02f, 0.985f, 246); panel2 = hsv(h, 0.1f * k + 0.03f, 0.94f, 240)
            line = hsv(h, 0.26f * k + 0.06f, 0.76f); lineHi = hsv(h, 0.5f * k + 0.05f, 0.55f)
            text = hsv(h, 0.55f * k + 0.1f, 0.2f); muted = hsv(h, 0.28f * k + 0.06f, 0.44f); sep = line
            accent = deepen(a1, 0.62f); accent2 = deepen(a2, 0.5f); gold = Color.rgb(0xa8, 0x6e, 0x00)
            good = Color.rgb(0x15, 0x8a, 0x3e); goodBg = Color.rgb(0xdc, 0xf5, 0xe3)
            activeBg = mix(hsv(h, 0.06f * k + 0.02f, 0.985f), a1, 0.2f); activeText = text
            shadow = hsv(h, 0.3f * k + 0.08f, 0.45f, 150); field = Color.WHITE; scrim = Color.argb(60, 0, 0, 0)
            toastLine = accent; toastText = text
        }
    }
    /** A swatch for the theme picker: its panel, line and two accents in the current light / dark mode. */
    fun preview(t: Accent): IntArray {
        val keep = accentKey; accentKey = t.key; compute()
        val out = intArrayOf(panel or (0xff shl 24), line, accent, accent2)
        accentKey = keep; compute()
        return out
    }
}
