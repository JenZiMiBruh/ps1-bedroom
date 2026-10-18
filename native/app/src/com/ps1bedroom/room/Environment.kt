package com.ps1bedroom.room

import com.ps1bedroom.room.engine.Color
import com.ps1bedroom.room.engine.Renderer
import com.ps1bedroom.room.engine.Scene
import com.ps1bedroom.room.engine.Vec3
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/** One weather kind (HTML WEATHER_KINDS): a coarse base plus the finer parameters. */
data class WeatherKind(
    val label: String, val base: String, val cloud: Float, val rain: Float, val fog: Float, val wind: Float,
    val dim: Float = 0f, val rainbow: Boolean = false, val forceRain: Boolean = false, val lightning: Boolean = false, val snow: Boolean = false
)

/**
 * ENVIRONMENT 1.1 (HTML lines 1686-1830 and 8553-8660): the real sun and moon, the time modes, and the room lighting
 * that follows them. The painted sky and the places outside come with the sky-dome phase.
 */
class Environment(private val scene: Scene, private val room: Room, private val renderer: Renderer) {
    companion object {
        val WEATHER_KINDS = linkedMapOf(
            "clear" to WeatherKind("Clear", "clear", 0.05f, 0f, 0f, 0.15f),
            "partly" to WeatherKind("Partly Cloudy", "clear", 0.45f, 0f, 0f, 0.3f),
            "overcast" to WeatherKind("Overcast", "clear", 0.95f, 0f, 0.15f, 0.3f, dim = 0.45f),
            "windy" to WeatherKind("Windy", "clear", 0.35f, 0f, 0f, 1.0f),
            "mist" to WeatherKind("Light Mist", "foggy", 0.5f, 0f, 0.45f, 0.1f),
            "fog" to WeatherKind("Heavy Fog", "foggy", 0.8f, 0f, 1.0f, 0.05f),
            "drizzle" to WeatherKind("Drizzle", "precip", 0.85f, 0.3f, 0.2f, 0.2f, dim = 0.6f),
            "rain" to WeatherKind("Rain", "precip", 0.95f, 0.65f, 0.3f, 0.4f, dim = 0.55f),
            "downpour" to WeatherKind("Downpour", "precip", 1.0f, 1.0f, 0.45f, 0.7f, dim = 0.45f),
            "sunshower" to WeatherKind("Sun Shower", "precip", 0.35f, 0.35f, 0f, 0.3f, rainbow = true, forceRain = true),
            "storm" to WeatherKind("Thunderstorm", "storm", 1.0f, 1.0f, 0.4f, 0.9f, dim = 0.4f, lightning = true),
            "heat" to WeatherKind("Heat Lightning", "clear", 0.55f, 0f, 0.05f, 0.2f, lightning = true),
            "flurries" to WeatherKind("Snow Flurries", "precip", 0.8f, 0.3f, 0.15f, 0.3f, snow = true, dim = 0.7f),
            "snow" to WeatherKind("Snowfall", "precip", 0.95f, 0.7f, 0.3f, 0.35f, snow = true, dim = 0.6f),
            "blizzard" to WeatherKind("Blizzard", "storm", 1.0f, 1.0f, 0.75f, 1.0f, snow = true, dim = 0.45f)
        )
        private val EPOCH_NEW_MOON = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(2000, 0, 6, 18, 14) }.timeInMillis
        fun clamp01(v: Double) = max(0.0, min(1.0, v))
        fun smooth(a: Double, b: Double, v: Double): Double { val k = clamp01((v - a) / (b - a)); return k * k * (3 - 2 * k) }
    }

    // ---- ENV state (same fields and defaults as the HTML) ----
    @set:JvmName("assignTimeMode") var timeMode = "live"        // live = device clock · preset = fixed time · cycle = time-lapse
    @set:JvmName("assignPresetHour") var presetHour = 0.0
    var cycleMinutes = 20.0
    var cycleStart = 0L
    var cycleBaseHour = 0.0
    var hour = 0.0
    var seasonMode = "live"
    var weatherKind = "clear"
    var skyStyle = "natural"
    var realisticMoon = false
    var lat = 40.0; var lon = 0.0; var lonKnown = false
    var sunEl = 0.0; var sunAz = 0.0; var moonEl = 0.0; var moonAz = 0.0; var moonPhase = 0.5; var moonIllum = 1.0
    var daylight = 0.0; var twilight = 0.0; var morning = false; var sunDeg = -30.0
    var noon = 12.4; var sunrise = 6.0; var sunset = 18.0
    var currentSeason = "winter"
    var currentTime = "night"
    var currentWeather = "clear"
    var W = WEATHER_KINDS.getValue("clear")
    var userVolumetricSetting = 1.0f

    // what lightning flashes fall back to
    var moonBaseIntensity = 3.6f; private set
    var ambientBaseIntensity = 0.65f; private set
    var activeBodyIsSun = false; private set
    /** Other parts (exterior lights, TV forecast, weather FX, sound) that react to time / weather changes. */
    val envHooks = ArrayList<() -> Unit>()

    fun isPrecipNow() = currentWeather == "precip" || currentWeather == "storm"
    fun isSnowingNow() = isPrecipNow() && (W.snow || (currentSeason == "winter" && !W.forceRain))
    fun isRainingNow() = isPrecipNow() && !isSnowingNow()

    // Presets follow the real sunrise / sunset for the season being shown
    val TIME_PRESETS: List<Pair<String, () -> Double>> = listOf(
        "Midnight" to { 0.0 }, "Late Night" to { 3.2 }, "Blue Hour" to { sunrise - 0.55 }, "Sunrise" to { sunrise + 0.05 },
        "Morning" to { sunrise + 2.5 }, "Noon" to { noon }, "Afternoon" to { noon + 3 },
        "Golden Hour" to { sunset - 0.75 }, "Sunset" to { sunset - 0.05 }, "Dusk" to { sunset + 0.45 }, "Evening" to { sunset + 1.8 }
    )

    private fun seasonForDate(d: Calendar, southern: Boolean): String {
        val m = d.get(Calendar.MONTH)
        val s = if (m < 2 || m == 11) "winter" else if (m < 5) "spring" else if (m < 8) "summer" else "fall"
        return if (southern) mapOf("winter" to "summer", "summer" to "winter", "spring" to "fall", "fall" to "spring").getValue(s) else s
    }
    private fun moonPhaseFor(nowMs: Long): Double {
        val synodic = 29.530588853
        val days = (nowMs - EPOCH_NEW_MOON) / 86400000.0
        return ((days % synodic) + synodic) % synodic / synodic // 0 new · 0.5 full
    }
    // Elevation and azimuth (from due south, + toward west) of a body at an hour angle
    private fun bodyPosition(hourAngleDeg: Double, decl: Double, lat: Double): DoubleArray {
        val h = hourAngleDeg * PI / 180
        val el = asin(max(-1.0, min(1.0, sin(lat) * sin(decl) + cos(lat) * cos(decl) * cos(h))))
        val az = atan2(sin(h), cos(h) * sin(lat) - tan(decl) * cos(lat))
        return doubleArrayOf(el, az)
    }
    fun currentHour(): Double {
        if (timeMode == "live") {
            val d = Calendar.getInstance()
            return d.get(Calendar.HOUR_OF_DAY) + d.get(Calendar.MINUTE) / 60.0 + d.get(Calendar.SECOND) / 3600.0
        }
        if (timeMode == "cycle") {
            val mins = (android.os.SystemClock.uptimeMillis() - cycleStart) / 60000.0
            return (cycleBaseHour + (mins / cycleMinutes) * 24) % 24
        }
        return presetHour
    }
    fun updateAstronomy() {
        val d = Calendar.getInstance()
        hour = currentHour()
        if (seasonMode == "live") currentSeason = seasonForDate(d, lat < 0)
        // The sun's path matches the season being shown (today's date in live mode)
        val n = if (seasonMode == "live") d.get(Calendar.DAY_OF_YEAR) else mapOf("winter" to 15, "spring" to 105, "summer" to 196, "fall" to 288).getValue(currentSeason)
        val decl = (23.44 * PI / 180) * sin(2 * PI * (284 + n) / 365) * (if (lat < 0) -1 else 1)
        val latR = abs(lat) * PI / 180
        val declN = if (lat < 0) -decl else decl
        val tzHours = d.timeZone.getOffset(d.timeInMillis) / 3600000.0
        val noonH = 12 + (if (lonKnown) (tzHours * 15 - lon) / 15 else 0.4)
        val sun = bodyPosition((hour - noonH) * 15, declN, latR)
        // Sunrise / sunset (sun's upper edge on the horizon, with refraction)
        val cosH0 = (sin(-0.0145) - sin(latR) * sin(declN)) / (cos(latR) * cos(declN))
        val halfDay = acos(max(-1.0, min(1.0, cosH0))) * 12 / PI
        noon = noonH
        sunrise = noonH - halfDay
        sunset = noonH + halfDay
        moonPhase = moonPhaseFor(d.timeInMillis)
        moonIllum = (1 - cos(2 * PI * moonPhase)) / 2
        val moon: DoubleArray
        if (realisticMoon) {
            moon = bodyPosition((hour - noonH - moonPhase * 24.84) * 15, declN * cos(2 * PI * moonPhase), latR)
        } else {
            // Cinematic moon: rises at the left of the window around sunset, arcs low across it and sets at the right by sunrise
            val nightLen = 24 - (sunset - sunrise)
            val p = ((hour - sunset + 0.6 + 24) % 24) / (nightLen + 1.2)
            moon = if (p <= 1) doubleArrayOf(-0.05 + 0.42 * sin(PI * p), -1.05 + 2.1 * p) else doubleArrayOf(-0.3, 1.2)
        }
        val flip = if (lat < 0) -1 else 1 // south of the equator the window looks north: east is on the right
        sunEl = sun[0]; sunAz = sun[1] * flip
        moonEl = moon[0]; moonAz = if (realisticMoon) moon[1] * flip else moon[1]
        sunDeg = sun[0] * 180 / PI
        daylight = smooth(-6.0, 9.0, sunDeg)
        twilight = max(0.0, 1 - abs(sunDeg - 1.5) / 10)
        morning = hour < noonH
        currentTime = if (sunDeg > 7) "day" else if (sunDeg > -7) (if (morning) "dawn" else "dusk") else "night"
    }
    fun formatHour(h: Double): String {
        val hh = floor(h).toInt() % 24; val mm = floor((h % 1) * 60).toInt()
        return "${((hh + 11) % 12) + 1}:${mm.toString().padStart(2, '0')} ${if (hh < 12) "AM" else "PM"}"
    }

    // ---- Sun & moon: shine the room light from whichever is brighter ----
    private val celestialDir = Vec3()
    fun updateCelestialPosition() {
        // (the painted sun / moon on the backdrop are placed by the sky-dome code)
        val sunK = smooth(-4.0, 6.0, sunDeg) * smooth(1.9, 1.3, abs(sunAz))
        val moonK = (1 - smooth(-9.0, -3.0, sunDeg)) * smooth(-0.04, 0.12, moonEl)
        activeBodyIsSun = sunK >= moonK * 0.9
        // Keep the beam coming in through the window even when the body is high or off to the side
        val el = max(0.07, min(0.62, if (activeBodyIsSun) sunEl else moonEl))
        val az = max(-0.85, min(0.85, if (activeBodyIsSun) sunAz else moonAz))
        celestialDir.set((sin(az) * cos(el)).toFloat(), sin(el).toFloat(), (-cos(az) * cos(el)).toFloat())
        Room.celestialPos.set(0.8f + celestialDir.x * 3.4f, 2.55f + celestialDir.y * 3.4f, -3.0f + celestialDir.z * 3.4f)
        room.moonLight.position.copy(Room.celestialPos)
        Room.roomLightTargetPos.set(0.8f - celestialDir.x * 4.6f, 2.45f - celestialDir.y * 4.6f, -3.0f - celestialDir.z * 4.6f)
        room.moonLight.target.position.copy(Room.roomLightTargetPos)
        room.moonLight.target.updateMatrixWorld()
        renderer.shadowsNeedUpdate = true
    }

    private val envColA = Color(); private val envColB = Color()
    fun applyEnvironmentSettings() {
        W = WEATHER_KINDS.getValue(weatherKind)
        currentWeather = W.base
        updateAstronomy()
        updateCelestialPosition()
        renderer.shadowsNeedUpdate = true
        val winter = currentSeason == "winter"

        // Light colour: deep orange at the horizon, warm white by day; cool blue moonlight at night
        if (activeBodyIsSun) {
            envColA.setHex(0xff7a3c).lerp(envColB.setHex(if (winter) 0xeef4ff else 0xfff1d6), smooth(0.0, 22.0, sunDeg).toFloat())
        } else {
            envColA.setHex(if (skyStyle == "blood") 0xff5a3c else if (skyStyle == "vapor") 0xc084fc else if (winter) 0x7dd3fc else 0x5b86ff)
        }
        val lightColor = envColA.getHex()
        val blindsK = floatArrayOf(1.0f, 1.17f, 0.1f, 1.3f)[room.blindsOpenState]
        val sunK = smooth(-4.0, 6.0, sunDeg) * smooth(1.9, 1.3, abs(sunAz))
        val moonK = (1 - smooth(-9.0, -3.0, sunDeg)) * smooth(-0.04, 0.12, moonEl) * (if (realisticMoon) 0.3 + 0.7 * moonIllum else 1.0)
        val presence = (if (activeBodyIsSun) sunK else moonK).toFloat()
        val dim = if (W.dim > 0f) W.dim else 1f
        val baseIntensity = (if (activeBodyIsSun) 4.6f else 3.6f) * presence * blindsK * dim

        // Ambient & fog follow the daylight, warmed at sunrise / sunset
        // Daylight fills the whole room through the window (less of it under heavy cloud)
        val dayFill = (daylight * (1 - W.cloud * 0.35)).toFloat()
        val tw = twilight.toFloat(); val dl = daylight.toFloat()
        room.ambientLight.color.setHex(0x131b3a)
            .lerp(envColB.setHex(if (winter) 0x7c8696 else 0x7d8090), dayFill)
            .lerp(envColB.setHex(0x5a3a4a), tw * 0.4f)
        if (skyStyle == "vapor") room.ambientLight.color.lerp(envColB.setHex(0x5b2a7a), 0.35f)
        room.ambientLight.intensity = 0.65f + 0.3f * dayFill
        // Skylight from the window: brighter from above, sky-tinted, warm bounce from the floor
        room.dayHemi.intensity = 0.75f * dayFill
        room.dayHemi.color.setHex(0xbcd4ff).lerp(envColB.setHex(0xffb37a), tw * 0.6f)
        scene.fogColor.setHex(0x050817)
            .lerp(envColB.setHex(0x1a2438), dl)
            .lerp(envColB.setHex(0x2a2030), tw * 0.3f)
        if (W.fog > 0f) scene.fogColor.lerp(envColB.setHex(0x4a5568).multiplyScalar(0.3f + dl * 0.7f), W.fog * 0.5f)
        scene.fogDensity = 0.025f + W.fog * 0.028f + (if (isPrecipNow()) 0.008f else 0f)
        val weatherVolMult = 1 + W.fog * 0.5f + (if (isPrecipNow()) 0.12f else 0f)

        room.moonLight.color.setHex(lightColor)
        moonBaseIntensity = baseIntensity
        ambientBaseIntensity = room.ambientLight.intensity
        room.moonLight.intensity = baseIntensity

        renderer.post.lightColor.setHex(lightColor)
        renderer.post.volumetricStrength = floatArrayOf(1.0f, 1.0f, 0.15f, 1.3f)[room.blindsOpenState] *
            userVolumetricSetting * weatherVolMult * min(1f, 0.2f + presence * dim)

        // Snow caps in winter (trees and seasonal foliage come with the places outside)
        for (sc in room.snowCapMeshes) sc.visible = winter

        for (fn in envHooks) fn()
    }

    // ---- Time controls (HTML setTimeMode / setPresetHour / setTimePreset) ----
    fun setTimeMode(mode: String) {
        updateAstronomy()
        if (mode == "cycle") { cycleBaseHour = hour; cycleStart = android.os.SystemClock.uptimeMillis() }
        if (mode == "preset") presetHour = hour
        timeMode = mode
        applyEnvironmentSettings()
    }
    fun setPresetHour(h: Double) {
        timeMode = "preset"
        presetHour = ((h % 24) + 24) % 24
        applyEnvironmentSettings()
    }
    fun setTimePreset(name: String) { TIME_PRESETS.firstOrNull { it.first == name }?.let { setPresetHour(it.second()) } }

    /** The HTML re-applies the environment every 20 s in live mode and every second in time-lapse (animate loop). */
    private var lastEnvApply = 0L
    fun tick(nowMs: Long) {
        if (timeMode != "preset" && nowMs - lastEnvApply > (if (timeMode == "cycle") 1000 else 20000)) {
            lastEnvApply = nowMs
            applyEnvironmentSettings()
        }
    }
}
