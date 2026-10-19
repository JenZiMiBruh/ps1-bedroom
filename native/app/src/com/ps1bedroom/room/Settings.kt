package com.ps1bedroom.room

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * HTML 15719-16516: the app shell's settings. Quality, looks, live / auto weather, the sleep and focus timers,
 * the status bar text and saved settings. Everything that changes the room runs on the GL thread (the menu posts
 * its taps there); the menu reads these fields to draw itself.
 */
class Settings(private val app: App, private val ctx: Context) {
    private val env get() = app.env
    private val rnd = Random.Default

    var qualityMode = "balanced"; private set
    var vibeMode = "signature"; private set
    var fpsChoice = "auto"
    var ecoIdle = true
    var tempUnit = if (Locale.getDefault().country == "US") "F" else "C"
    var startScreenEnabled = true
    var tutorialSeen = false
    var sleepDim = true
    var masterVolume = 0.85f
    var debugFreeCam = false
    // Live weather (Open-Meteo)
    var weatherMode = "manual"; private set   // manual · auto · live
    var cityName = ""
    var liveTempC: Double? = null
    @Volatile var liveStatus = "Type a city to sync the weather (and the sun's position) with the real world."
    private var autoNextAt = 0L
    private var liveNextAt = 0L
    // Timers
    var sleepEnd = 0L; private set
    private var sleepFadingDone = false
    @Volatile var sleeping = false
    class Focus(var mode: String = "off", var phase: String = "work", var end: Long = 0L)
    val focus = Focus()
    val FOCUS_MODES = mapOf("off" to null, "short" to intArrayOf(25, 5), "long" to intArrayOf(50, 10))

    companion object {
        class Quality(val res: Int, val fps: Int, val steps: Int, val shadow: Int, val ao: Float, val note: String)
        val QUALITY = linkedMapOf(
            "saver" to Quality(180, 30, 8, 1024, 0f, "Lower resolution and 30 fps. Gentle on battery for leaving it on all night."),
            "balanced" to Quality(240, 60, 14, 1024, 0.8f, "The classic PS1 look at a smooth 60 fps."),
            "high" to Quality(360, 60, 18, 2048, 1f, "Sharper picture and richer light rays. Uses more battery.")
        )
        class Vibe(val label: String, val tint: FloatArray, val sat: Float, val con: Float, val vig: Float)
        val VIBES = linkedMapOf(
            "signature" to Vibe("Signature", floatArrayOf(1.02f, 0.98f, 1.05f), 1.08f, 1.05f, 0.38f),
            "none" to Vibe("Raw PS1", floatArrayOf(1f, 1f, 1f), 1f, 1f, 0f),
            "vhs" to Vibe("Warm VHS", floatArrayOf(1.08f, 1.0f, 0.86f), 0.82f, 0.95f, 0.5f),
            "cool" to Vibe("Cool Night", floatArrayOf(0.9f, 0.98f, 1.12f), 0.95f, 1.05f, 0.42f),
            "neon" to Vibe("Neon Dream", floatArrayOf(1.06f, 0.92f, 1.14f), 1.4f, 1.1f, 0.42f),
            "noir" to Vibe("Noir", floatArrayOf(1f, 1f, 1f), 0.0f, 1.22f, 0.6f)
        )
        val SKY_STYLES = linkedMapOf("natural" to "Natural", "vapor" to "Vaporwave", "aurora" to "Northern Lights", "meteor" to "Meteor Shower", "blood" to "Blood Moon")
        val AUTO_NEXT = mapOf(
            "clear" to listOf("clear", "partly", "partly", "windy", "heat"), "partly" to listOf("clear", "partly", "overcast", "sunshower"),
            "overcast" to listOf("partly", "drizzle", "rain", "mist"), "drizzle" to listOf("rain", "overcast", "partly"), "rain" to listOf("downpour", "drizzle", "storm", "overcast"),
            "downpour" to listOf("rain", "storm"), "storm" to listOf("rain", "downpour", "overcast"), "mist" to listOf("fog", "partly", "overcast"), "fog" to listOf("mist", "overcast"),
            "windy" to listOf("partly", "clear", "overcast"), "heat" to listOf("clear", "storm", "partly"), "sunshower" to listOf("partly", "clear"),
            "flurries" to listOf("snow", "overcast", "partly"), "snow" to listOf("snow", "flurries", "blizzard", "overcast"), "blizzard" to listOf("snow", "flurries")
        )
        val PHASE_NAMES = arrayOf("New moon", "Waxing crescent", "First quarter", "Waxing gibbous", "Full moon", "Waning gibbous", "Last quarter", "Waning crescent")
    }

    // ---------- Temperature ----------
    /** Live temperature when synced to a city, otherwise a believable estimate for the season, hour and weather. */
    fun currentTempC(): Double {
        if (weatherMode == "live") liveTempC?.let { return it }
        val base = mapOf("summer" to 24.0, "spring" to 14.0, "fall" to 11.0, "winter" to -1.0).getValue(env.currentSeason)
        return base + app.places.outsideTempOffset() + (env.daylight - 0.5) * 8 - (if (env.isPrecipNow()) 2 else 0) - env.W.wind * 2
    }
    fun formatTemp(c: Double) = if (tempUnit == "F") "${(c * 9 / 5 + 32).roundToInt()}°F" else "${c.roundToInt()}°C"

    // ---------- Environment controls ----------
    fun setWeatherKind(kind0: String, quiet: Boolean = false) {
        val kind = if (Environment.WEATHER_KINDS.containsKey(kind0)) kind0 else "clear"
        env.weatherKind = kind
        env.applyEnvironmentSettings()
        if (!quiet) app.toast("🌦 WEATHER: ${env.W.label}")
        changed()
    }
    fun setTimeMode(mode: String) { env.setTimeMode(mode); changed() }
    fun setPresetHour(h: Double) { env.setPresetHour(h); changed() }
    fun setTimePreset(name: String) {
        env.updateAstronomy()
        env.setTimePreset(name)
        app.toast("🕰 ${name.uppercase()} · ${env.formatHour(env.presetHour)}")
        changed()
    }
    fun setSeasonChoice(s: String) {
        if (s !in listOf("spring", "summer", "fall", "winter")) env.seasonMode = "live" else { env.seasonMode = "manual"; env.currentSeason = s }
        env.applyEnvironmentSettings()
        changed()
    }
    fun setSkyStyle(st: String) {
        env.skyStyle = st
        env.applyEnvironmentSettings()
        app.toast("🌌 SKY: ${SKY_STYLES[st]}")
        changed()
    }
    fun setRealisticMoon(on: Boolean) { env.realisticMoon = on; env.applyEnvironmentSettings(); changed() }
    fun setCycleMinutes(m: Double) { env.cycleMinutes = m; setTimeMode("cycle") }

    // ---------- Live weather (Open-Meteo, no key needed) ----------
    private fun wmoToKind(code: Int, wind: Double): String {
        var k = "clear"
        if (code == 1 || code == 2) k = "partly"
        else if (code == 3) k = "overcast"
        else if (code == 45 || code == 48) k = "fog"
        else if (code in 51..57) k = "drizzle"
        else if (code == 61 || code == 80) k = "drizzle"
        else if (code == 63 || code == 81 || code == 66 || code == 67) k = "rain"
        else if (code == 65 || code == 82) k = "downpour"
        else if (code == 71 || code == 77 || code == 85) k = "flurries"
        else if (code == 73 || code == 86) k = "snow"
        else if (code == 75) k = if (wind > 30) "blizzard" else "snow"
        else if (code >= 95) k = "storm"
        if ((k == "clear" || k == "partly") && wind > 35) k = "windy"
        return k
    }
    private fun getJson(url: String): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        try { return JSONObject(c.inputStream.bufferedReader().use { it.readText() }) } finally { c.disconnect() }
    }
    /** Looks the city up and syncs the weather, the temperature and the sun's position (network on its own thread). */
    fun fetchLiveWeather(city: String, quiet: Boolean) {
        val q = city.trim()
        if (q.isEmpty()) return
        liveStatus = "Looking up $q…"; changed()
        liveNextAt = System.currentTimeMillis() + 20 * 60000L
        Thread {
            try {
                val geo = getJson("https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(q, "UTF-8")}&count=1&language=en&format=json")
                val results = geo.optJSONArray("results")
                if (results == null || results.length() == 0) { liveStatus = "Couldn't find \"$q\". Try a nearby bigger city."; app.post { changed() }; return@Thread }
                val place = results.getJSONObject(0)
                val lat = place.getDouble("latitude"); val lon = place.getDouble("longitude")
                val fc = getJson("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code,wind_speed_10m&timezone=auto")
                val c = fc.getJSONObject("current")
                val tempC = c.getDouble("temperature_2m")
                val kind = wmoToKind(c.getInt("weather_code"), c.getDouble("wind_speed_10m"))
                val name = place.getString("name"); val admin = place.optString("admin1", "")
                app.post {
                    env.lat = lat; env.lon = lon; env.lonKnown = true
                    cityName = name
                    liveTempC = tempC
                    val desc = Environment.WEATHER_KINDS.getValue(kind).label
                    liveStatus = "$name${if (admin.isNotEmpty()) ", $admin" else ""} · ${tempC.roundToInt()}°C / ${(tempC * 9 / 5 + 32).roundToInt()}°F · $desc"
                    if (weatherMode == "live") setWeatherKind(kind, quiet) else { env.applyEnvironmentSettings(); changed() }
                }
            } catch (e: Exception) {
                liveStatus = "No connection right now. Keeping the last weather and trying again later."
                app.post { changed() }
            }
        }.start()
    }

    // ---------- Auto-changing weather (a gentle Markov chain that fits the season) ----------
    private fun autoWeatherStep() {
        var options = AUTO_NEXT[env.weatherKind] ?: listOf("clear")
        options = if (env.currentSeason == "winter") options.map { k -> mapOf("drizzle" to "flurries", "rain" to "snow", "downpour" to "snow", "storm" to "blizzard", "sunshower" to "partly", "heat" to "clear")[k] ?: k }
        else options.filter { k -> k !in listOf("flurries", "snow", "blizzard") && (k != "heat" || env.currentSeason == "summer") }
        if (options.isEmpty()) options = listOf("partly")
        setWeatherKind(app.places.climatePick(options), true) // leans toward the climate of the place outside
        autoNextAt = System.currentTimeMillis() + ((10 + rnd.nextDouble() * 10) * 60000).toLong()
    }
    fun setWeatherMode(mode: String) {
        weatherMode = mode
        if (mode == "auto") autoNextAt = System.currentTimeMillis() + 8 * 60000L
        if (mode == "live" && cityName.isNotEmpty()) fetchLiveWeather(cityName, true)
        changed()
    }
    fun pickWeather(kind: String) { weatherMode = "manual"; setWeatherKind(kind) }

    // ---------- Display: quality, looks ----------
    fun setQuality(mode: String, quiet: Boolean = false) {
        qualityMode = if (QUALITY.containsKey(mode)) mode else "balanced"
        val q = QUALITY.getValue(qualityMode)
        app.marchSteps = q.steps
        app.aoBase = q.ao
        app.setResolution(q.res)
        applyFps()
        changed()
    }
    fun setVibe(v: String) {
        vibeMode = if (VIBES.containsKey(v)) v else "signature"
        val g = VIBES.getValue(vibeMode)
        val post = app.renderer.post
        post.gradeTint.set(g.tint[0], g.tint[1], g.tint[2])
        post.gradeSat = g.sat; post.gradeContrast = g.con; post.vignette = g.vig
        changed()
    }
    fun setFps(choice: String) {
        fpsChoice = choice
        if (choice == "120") app.toast("⚡ 120 FPS: buttery smooth on 120 Hz screens. Uses more battery.")
        applyFps(); changed()
    }
    fun applyFps() {
        val p = app.pacer ?: return
        p.baseCap = if (sleeping) 4 else if (fpsChoice == "auto") QUALITY.getValue(qualityMode).fps else fpsChoice.toInt()
        p.saver = qualityMode == "saver"
        p.ecoIdle = ecoIdle
    }

    // ---------- Sleep timer ----------
    fun setSleepTimer(min: Int, quiet: Boolean = false) {
        sleepFadingDone = false
        sleepEnd = if (min > 0) System.currentTimeMillis() + min * 60000L else 0L
        if (!quiet) app.toast(if (min > 0) "⏾ SLEEP TIMER: $min minutes. Everything fades out when it ends." else "⏾ SLEEP TIMER: Off")
        changed()
    }
    fun sleepLeftText(): String {
        val left = maxOf(0L, sleepEnd - System.currentTimeMillis())
        return "${left / 60000}:${((left % 60000) / 1000).toString().padStart(2, '0')}"
    }
    private fun sleepTick() {
        if (sleepEnd == 0L) return
        val left = sleepEnd - System.currentTimeMillis()
        if (left <= 0) {
            sleepEnd = 0L
            // (the sound fades out over the last minute once the audio is ported)
            if (sleepDim) { sleeping = true; applyFps(); app.hud?.showSleeping(true) }
            changed()
        }
    }
    fun wake() { sleeping = false; applyFps() }
    /** One tap: lights down, fan on, you in bed, a 45-minute timer. */
    fun startSleepMode() {
        if (app.fanLightOn) app.toggleFanLight()
        if (app.furniture.lampOn) app.furniture.toggleLamp()
        if (!app.fan.fanSpinning) app.fan.toggleCeilingFanSpin()
        if (app.decor.stringLightsMode != 1) app.decor.setStringLights(1)
        app.setPlayerMode("bed")
        setSleepTimer(45, true)
        app.toast("😴 SLEEP MODE: Lights down, fan on. Everything fades out in 45 minutes. Goodnight.")
    }

    // ---------- Focus timer (study / Pomodoro) ----------
    fun setFocus(mode: String) {
        focus.mode = if (FOCUS_MODES.containsKey(mode)) mode else "off"
        focus.phase = "work"
        focus.end = FOCUS_MODES[focus.mode]?.let { System.currentTimeMillis() + it[0] * 60000L } ?: 0L
        app.toast(if (focus.mode == "off") "🎯 Focus timer off" else FOCUS_MODES.getValue(focus.mode)!!.let { "🎯 FOCUS: ${it[0]} minutes of work, then a ${it[1]}-minute break. The clock by the bed counts down." })
        changed()
    }
    fun focusLeftText(): String {
        val left = maxOf(0L, focus.end - System.currentTimeMillis())
        return "${(left / 60000).toString().padStart(2, '0')}:${((left % 60000) / 1000).toString().padStart(2, '0')}"
    }
    private fun focusTick() {
        if (focus.mode == "off") return
        if (System.currentTimeMillis() >= focus.end) {
            val (work, rest) = FOCUS_MODES.getValue(focus.mode)!!.let { it[0] to it[1] }
            focus.phase = if (focus.phase == "work") "break" else "work"
            focus.end = System.currentTimeMillis() + (if (focus.phase == "work") work else rest) * 60000L
            app.toast(if (focus.phase == "break") "☕ BREAK TIME: $rest minutes. Stretch, pet Jasper, look out the window." else "🎯 BACK TO IT: $work minutes of focus.")
        }
    }

    // ---------- Status bar ----------
    class Status(val time: String, val temp: String, val season: String, val weather: String, val place: String, val posture: String, val timer: String?, val focus: String?)
    fun status(): Status {
        val icon = if (env.daylight > 0.5) (if (env.W.cloud > 0.6f) "☁️" else "☀️") else (if (env.daylight > 0.05) "🌅" else "🌙")
        val p = app.places.envDef()
        return Status("$icon ${env.formatHour(env.hour)}", formatTemp(currentTempC()), env.currentSeason.replaceFirstChar { it.uppercase() }, env.W.label,
            "${p.icon} ${p.name.replace(Regex(" \\(.*\\)$"), "")}", app.player.postureLabel(),
            if (sleepEnd > 0) "⏾ ${sleepLeftText()}" else null,
            if (focus.mode != "off") "${if (focus.phase == "work") "🎯" else "☕"} ${focusLeftText()}" else null)
    }
    // Status bar buttons: each tap steps to the next option (with a toast)
    private fun <T> nextOf(list: List<T>, cur: T) = list[(list.indexOf(cur) + 1) % list.size]
    private fun presetNameNow(): String? = if (env.timeMode != "preset") null else env.TIME_PRESETS.firstOrNull { (_, fn) -> kotlin.math.abs(((fn() % 24) + 24) % 24 - env.presetHour) < 0.02 }?.first
    fun stepStatus(which: String) {
        when (which) {
            "time" -> {
                val next = nextOf(listOf("live") + env.TIME_PRESETS.map { it.first }, presetNameNow() ?: (if (env.timeMode == "preset") "" else "live"))
                if (next == "live") { setTimeMode("live"); app.toast("🕰 LIVE CLOCK · ${env.formatHour(env.hour)}") } else setTimePreset(next)
            }
            "temp" -> { tempUnit = if (tempUnit == "F") "C" else "F"; app.toast("🌡 ${formatTemp(currentTempC())}"); changed() }
            "season" -> {
                val next = nextOf(listOf("live", "spring", "summer", "fall", "winter"), if (env.seasonMode == "live") "live" else env.currentSeason)
                setSeasonChoice(next)
                app.toast(if (next == "live") "🍂 SEASON: AUTO (${env.currentSeason.uppercase()})" else "🍂 SEASON: ${next.uppercase()}")
            }
            "weather" -> {
                val kinds = Environment.WEATHER_KINDS.keys.toList()
                val list = if (cityName.isNotEmpty()) kinds + "live" else kinds
                val next = nextOf(list, if (weatherMode == "live") "live" else env.weatherKind)
                if (next == "live") { setWeatherMode("live"); app.toast("🌦 LIVE WEATHER · $cityName"); return }
                pickWeather(next)
            }
            "place" -> { setOutside(nextOf(Places.OUTSIDE_ENVS.keys.toList(), app.sky.outsideEnv)) }
        }
    }
    fun setOutside(k: String, quiet: Boolean = false) {
        app.places.setOutsideEnv(k)
        val e = app.places.envDef()
        if (!quiet) app.toast("${e.icon} Outside the window: ${e.name}")
        changed()
    }

    // ---------- Heartbeat (once a second, from the frame loop) ----------
    private var lastBeat = 0L
    fun heartbeat() {
        val now = System.currentTimeMillis()
        if (now - lastBeat < 1000) return
        lastBeat = now
        sleepTick()
        if (weatherMode == "auto" && now > autoNextAt) autoWeatherStep()
        if (weatherMode == "live" && cityName.isNotEmpty() && liveNextAt in 1..now) fetchLiveWeather(cityName, true)
        focusTick()
        app.hud?.refreshStatus()
        if (now - lastSaved > 4000) saveNow()
    }

    /** Something the menu shows changed: redraw it and save soon. */
    fun changed() { app.hud?.refreshMenu(); dirty = true }

    // ---------- Saved settings ----------
    private val prefs = ctx.getSharedPreferences("ps1bedroom", Context.MODE_PRIVATE)
    private var dirty = false
    private var lastSaved = 0L
    private var lastJson = ""
    fun snapshot(): JSONObject = JSONObject().apply {
        put("timeMode", env.timeMode); put("presetHour", env.presetHour); put("cycleMinutes", env.cycleMinutes)
        put("seasonMode", env.seasonMode); put("season", env.currentSeason); put("weatherMode", weatherMode); put("weatherKind", env.weatherKind)
        put("skyStyle", env.skyStyle); put("realisticMoon", env.realisticMoon); put("city", cityName); put("lat", env.lat); put("lon", env.lon); put("lonKnown", env.lonKnown)
        put("blinds", app.room.blindsOpenState); put("fanLight", app.fanLightOn); put("fanSpin", app.fan.fanSpinning); put("ac", app.room.acUnitOn)
        put("lamp", app.furniture.lampOn); put("string", app.decor.stringLightsMode); put("tv", app.tv.bedroomTvOn)
        put("tvCh", if (app.tv.bedroomTvChannel == Tv.PS1_TV_CHANNEL) 0 else app.tv.bedroomTvChannel); put("radio", app.extras.radioStation)
        put("master", masterVolume.toDouble()); put("quality", qualityMode); put("vibe", vibeMode); put("res", app.resShort)
        put("jitter", app.jitterBase.toDouble()); put("affine", app.renderer.ps1Uniforms.affineStrength.toDouble()); put("dither", app.renderer.post.ditherStrength.toDouble())
        put("volumetric", env.userVolumetricSetting.toDouble()); put("sway", app.swayAmount.toDouble()); put("ao", app.aoBase.toDouble())
        put("tempUnit", tempUnit); put("fpsChoice", fpsChoice); put("ecoIdle", ecoIdle)
        put("poster", app.extras.posterChoice); put("colors", JSONObject(app.extras.roomColors as Map<*, *>)); put("decor", app.extras.decorMode); put("heater", app.extras.heaterMode)
        put("outside", app.sky.outsideEnv); put("startScreen", startScreenEnabled); put("tutorialSeen", tutorialSeen); put("sleepDim", sleepDim)
    }
    fun saveNow() {
        lastSaved = System.currentTimeMillis()
        if (!dirty && lastJson.isNotEmpty()) { dirty = false }
        val json = snapshot().toString()
        if (json == lastJson) return
        lastJson = json; dirty = false
        prefs.edit().putString("settings.v1", json).apply()
    }
    /** A saved value read straight from storage (the start screen decides before the room is built). */
    fun peek(key: String, def: Boolean): Boolean = prefs.getString("settings.v1", null)?.let { try { JSONObject(it).optBoolean(key, def) } catch (e: Exception) { def } } ?: def
    /** applySavedSettings: on the GL thread once the room is built. */
    fun restore() {
        val s = prefs.getString("settings.v1", null)?.let { try { JSONObject(it) } catch (e: Exception) { null } }
        if (s == null) { setQuality("balanced", true); setVibe("signature"); lastJson = snapshot().toString(); return }
        try {
            env.timeMode = s.optString("timeMode", "live"); env.presetHour = s.optDouble("presetHour", 0.0); env.cycleMinutes = s.optDouble("cycleMinutes", 20.0)
            env.seasonMode = s.optString("seasonMode", "live")
            weatherMode = s.optString("weatherMode", "manual")
            env.skyStyle = s.optString("skyStyle", "natural").let { if (SKY_STYLES.containsKey(it)) it else "natural" }
            env.realisticMoon = s.optBoolean("realisticMoon", false)
            cityName = s.optString("city", ""); env.lat = s.optDouble("lat", 40.0); env.lon = s.optDouble("lon", 0.0); env.lonKnown = s.optBoolean("lonKnown", false)
            if (env.timeMode == "cycle") { env.cycleStart = android.os.SystemClock.uptimeMillis(); env.cycleBaseHour = env.presetHour }
            if (env.seasonMode != "live") env.currentSeason = s.optString("season", env.currentSeason)
            env.weatherKind = s.optString("weatherKind", "clear").let { if (Environment.WEATHER_KINDS.containsKey(it)) it else "clear" }
            if (s.has("blinds")) app.room.setBlindsState(s.getInt("blinds"))
            if (s.optBoolean("fanLight", false) != app.fanLightOn) app.toggleFanLight()
            if (!s.optBoolean("fanSpin", true) && app.fan.fanSpinning) app.fan.toggleCeilingFanSpin()
            if (!s.optBoolean("ac", true) && app.room.acUnitOn) app.room.toggleAcUnit()
            if (s.optBoolean("lamp", false) != app.furniture.lampOn) app.furniture.toggleLamp()
            s.optInt("string", 0).let { if (it > 0) app.decor.setStringLights(it) }
            if (!s.optBoolean("tv", true) && app.tv.bedroomTvOn) app.tv.toggleBedroomTv(0f)
            s.optInt("tvCh", 0).let { if (it in 1 until Tv.PS1_TV_CHANNEL) app.tv.setChannel(it) }
            repeat(s.optInt("radio", 0)) { app.extras.cycleRadio(app.furniture.radioDisplayMat) }
            masterVolume = s.optDouble("master", 0.85).toFloat()
            app.jitterBase = s.optDouble("jitter", 160.0).toFloat()
            app.renderer.ps1Uniforms.affineStrength = s.optDouble("affine", 0.85).toFloat()
            app.renderer.post.ditherStrength = s.optDouble("dither", 1.0).toFloat()
            env.userVolumetricSetting = s.optDouble("volumetric", 1.0).toFloat()
            app.swayAmount = s.optDouble("sway", 0.4).toFloat()
            fpsChoice = s.optString("fpsChoice", "auto")
            ecoIdle = s.optBoolean("ecoIdle", true)
            setQuality(s.optString("quality", "balanced"), true)
            if (s.has("ao")) app.aoBase = s.getDouble("ao").toFloat()
            if (s.has("res")) app.setResolution(s.getInt("res"))
            setVibe(s.optString("vibe", "signature"))
            s.optString("tempUnit", "").let { if (it == "C" || it == "F") tempUnit = it }
            app.extras.setPoster(s.optString("poster", "none"))
            s.optJSONObject("colors")?.let { c -> for (part in c.keys()) app.extras.setRoomColor(part, c.getString(part)) }
            app.extras.decorMode = s.optString("decor", "auto"); app.extras.applyDecor()
            s.optString("outside", "city").let { if (it != "city") app.places.setOutsideEnv(it) }
            val heater = s.optString("heater", "auto"); var guard = 0
            while (app.extras.heaterMode != heater && guard++ < 3) app.extras.cycleHeater()
            startScreenEnabled = s.optBoolean("startScreen", true)
            tutorialSeen = s.optBoolean("tutorialSeen", false)
            sleepDim = s.optBoolean("sleepDim", true)
        } catch (e: Exception) { android.util.Log.w("PS1Bedroom", "Some saved settings could not be restored", e) }
        env.applyEnvironmentSettings()
        if (weatherMode == "live" && cityName.isNotEmpty()) fetchLiveWeather(cityName, true)
        if (weatherMode == "auto") autoNextAt = System.currentTimeMillis() + 10 * 60000L
        lastJson = snapshot().toString()
    }

    /** Sunrise / sunset line under the time controls. */
    fun sunInfo(): String = "Sunrise ${env.formatHour(((env.sunrise % 24) + 24) % 24)} · Sunset ${env.formatHour(env.sunset % 24)}${if (cityName.isNotEmpty()) " in $cityName" else ""}. The sun and moon move across the sky in real time."
    fun moonReadout() = "${PHASE_NAMES[(env.moonPhase * 8).roundToInt() % 8]} · ${(env.moonIllum * 100).roundToInt()}% lit"
}
