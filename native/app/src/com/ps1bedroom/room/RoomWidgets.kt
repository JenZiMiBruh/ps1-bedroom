package com.ps1bedroom.room

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.widget.RemoteViews
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar
import kotlin.math.roundToInt

/**
 * Interactive Home-Screen Widgets for PS1 Bedroom:
 * 1. ClockWeatherWidget ("Retro Clock & Weather")
 * 2. QuickControlsWidget ("Room Quick Controls")
 * 3. SleepAlarmWidget ("Sleep & Sunrise Alarm")
 * 4. JasperWidget ("Jasper Companion")
 * Plus RoomWidgetReceiver to handle 1-tap widget actions whether the room is currently open or in the background.
 */
class RoomWidgetReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_WIDGET_CMD = "com.ps1bedroom.room.WIDGET_CMD"
        const val ACTION_WIDGET_ALARM = "com.ps1bedroom.room.ACTION_WIDGET_ALARM"
        @Volatile var onCommand: ((String) -> Boolean)? = null

        fun cmdPendingIntent(ctx: Context, cmd: String, reqCode: Int): PendingIntent =
            PendingIntent.getBroadcast(
                ctx, reqCode,
                Intent(ctx, RoomWidgetReceiver::class.java).setAction(ACTION_WIDGET_CMD).putExtra("cmd", cmd),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

        fun openPendingIntent(ctx: Context, reqCode: Int, action: String? = null): PendingIntent =
            PendingIntent.getActivity(
                ctx, reqCode,
                Intent(ctx, MainActivity::class.java).apply {
                    if (action != null) this.action = action
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

        private fun readSettingsJson(ctx: Context): JSONObject {
            val raw = ctx.getSharedPreferences("ps1bedroom", Context.MODE_PRIVATE).getString("settings.v1", null)
            return if (raw != null) try { JSONObject(raw) } catch (_: Exception) { JSONObject() } else JSONObject()
        }

        private fun writeSettingsJson(ctx: Context, obj: JSONObject) {
            ctx.getSharedPreferences("ps1bedroom", Context.MODE_PRIVATE).edit().putString("settings.v1", obj.toString()).apply()
        }

        private fun fetchJson(urlStr: String, timeout: Int): JSONObject? {
            return try {
                val conn = URL(urlStr).openConnection() as HttpURLConnection
                conn.connectTimeout = timeout
                conn.readTimeout = timeout
                conn.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            } catch (_: Exception) {
                null
            }
        }

        private fun wmoToKind(code: Int, wind: Double): String {
            var k = "clear"
            if (code in 1..2) k = "partly"
            else if (code == 3) k = "overcast"
            else if (code in listOf(45, 48)) k = "fog"
            else if (code in 51..57 || code in listOf(61, 80)) k = "drizzle"
            else if (code in listOf(63, 81, 66, 67)) k = "rain"
            else if (code in listOf(65, 82)) k = "downpour"
            else if (code in listOf(71, 77, 85)) k = "flurries"
            else if (code in listOf(73, 86)) k = "snow"
            else if (code == 75) k = if (wind > 30) "blizzard" else "snow"
            else if (code >= 95) k = "storm"
            if ((k == "clear" || k == "partly") && wind > 35) k = "windy"
            return k
        }

        private fun weatherIcon(kind: String): String = when (kind) {
            "storm" -> "⛈️"
            "heat" -> "⚡"
            "rain", "downpour" -> "🌧️"
            "drizzle", "sunshower" -> "🌦️"
            "snow", "blizzard", "flurries" -> "🌨️"
            "fog", "mist" -> "🌫️"
            "windy" -> "💨"
            "overcast", "partly" -> "⛅"
            else -> "☀️"
        }

        /** Background worker that updates live weather, day/night lighting, and renders fresh snapshots without requiring the app to be open or in PiP. */
        fun refreshWindowWidgetBackground(ctx: Context, forceSnapshot: Boolean = false, onComplete: (() -> Unit)? = null) {
            Thread {
                try {
                    val s = readSettingsJson(ctx)
                    val p = ctx.getSharedPreferences("window_widget", Context.MODE_PRIVATE)
                    val weatherMode = s.optString("weatherMode", "live")
                    val cityName = s.optString("cityName", "").trim()

                    var weatherKind = s.optString("weatherKind", "clear")
                    var tempStr = p.getString("infoTemp", "72°F") ?: "72°F"
                    var desc = Environment.WEATHER_KINDS[weatherKind]?.label ?: "Clear"
                    var windKmH = s.optDouble("liveWindSpeedKmH", -1.0).toFloat()

                    if (weatherMode == "live") {
                        try {
                            var lat = s.optDouble("lat", 40.7128)
                            var lon = s.optDouble("lon", -74.0060)
                            var name = cityName

                            if (cityName.isNotEmpty()) {
                                val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(cityName, "UTF-8")}&count=1&language=en&format=json"
                                val geo = fetchJson(geoUrl, 4000)
                                val res = geo?.optJSONArray("results")
                                if (res != null && res.length() > 0) {
                                    val r0 = res.getJSONObject(0)
                                    lat = r0.getDouble("latitude"); lon = r0.getDouble("longitude")
                                    name = r0.optString("name", cityName)
                                }
                            } else if (!s.optBoolean("lonKnown", false)) {
                                try {
                                    val ip = fetchJson("https://get.geojs.io/v1/ip/geo.json", 3000)
                                    if (ip != null) {
                                        lat = ip.getString("latitude").toDouble()
                                        lon = ip.getString("longitude").toDouble()
                                        name = ip.optString("city", "").ifEmpty { ip.optString("region", "Local") }
                                    }
                                } catch (_: Exception) { }
                            }

                            val fcUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code,wind_speed_10m,wind_direction_10m&timezone=auto"
                            val fc = fetchJson(fcUrl, 5000)
                            val c = fc?.optJSONObject("current")
                            if (c != null) {
                                val tempC = c.getDouble("temperature_2m")
                                val wCode = c.getInt("weather_code")
                                windKmH = c.optDouble("wind_speed_10m", -1.0).toFloat()
                                val windDir = c.optDouble("wind_direction_10m", -1.0).toFloat()
                                weatherKind = wmoToKind(wCode, if (windKmH >= 0) windKmH.toDouble() else 0.0)
                                val tempF = (tempC * 9 / 5 + 32).roundToInt()
                                tempStr = "${tempF}°F"
                                desc = Environment.WEATHER_KINDS[weatherKind]?.label ?: "Clear"

                                s.put("weatherKind", weatherKind)
                                s.put("liveTempC", tempC)
                                s.put("liveWindSpeedKmH", windKmH.toDouble())
                                s.put("liveWindDirDeg", windDir.toDouble())
                                if (name.isNotEmpty()) s.put("cityName", name)
                                writeSettingsJson(ctx, s)
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("PS1Bedroom", "Live weather background fetch failed", e)
                        }
                    }

                    val icon = weatherIcon(weatherKind)
                    val cal = Calendar.getInstance()
                    val hour = cal.get(Calendar.HOUR)
                    val h12 = if (hour == 0) 12 else hour
                    val min = cal.get(Calendar.MINUTE).toString().padStart(2, '0')
                    val ampm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
                    val timeStr = "$h12:$min $ampm"

                    val outsideKey = s.optString("outside", "city")
                    val placeDef = Places.OUTSIDE_ENVS[outsideKey] ?: Places.OUTSIDE_ENVS["city"]!!
                    val placeStr = "${placeDef.icon} ${placeDef.name.replace(Regex(" \\(.*\\)$"), "")}"

                    p.edit()
                        .putString("infoTemp", tempStr)
                        .putString("infoWeatherOnly", "$icon $desc")
                        .putString("infoWeather", "$timeStr  $tempStr  $icon $desc")
                        .putString("infoPlace", placeStr)
                        .putLong("shotAt", System.currentTimeMillis())
                        .apply()

                    WindowWidget.generateAndSaveOutdoorSnapshot(ctx, s, outsideKey, weatherKind)
                    WindowWidget.updateAll(ctx)
                    ClockWeatherWidget.updateAll(ctx)
                    WindowWidget.scheduleNextRefresh(ctx)

                    onComplete?.invoke()
                } catch (e: Exception) {
                    android.util.Log.w("PS1Bedroom", "refreshWindowWidgetBackground error", e)
                }
            }.start()
        }

        /** The alarm-time button steps through sensible wake-up times. */
        fun nextAlarmTime(cur: String): String {
            val opts = listOf("05:30", "06:00", "06:30", "07:00", "07:30", "08:00", "08:30", "09:00", "10:00")
            return opts[(opts.indexOf(cur) + 1).let { if (it <= 0 && cur !in opts) 0 else it } % opts.size]
        }

        /** Updates all home-screen widgets from the latest room state (or persisted preferences). */
        fun updateAllWidgets(ctx: Context, app: App? = null) {
            try {
                if (app != null && app.isBuilt) {
                    val st = app.settings.status()
                    val p = app.places.envDef()
                    ctx.getSharedPreferences("window_widget", Context.MODE_PRIVATE).edit()
                        .putString("infoTemp", st.temp)
                        .putString("infoWeatherOnly", st.weather)
                        .putString("infoPlace", "${p.icon} ${p.name.replace(Regex(" \\(.*\\)$"), "")}")
                        .putString("infoWeather", "${st.time.substringBefore(' ')} ${st.temp} ${st.weather}")
                        .putString("infoSeason", st.season)
                        .putString("infoStation", app.extras.RADIO_STATIONS.getOrElse(app.extras.radioStation) { "OFF" }.substringBefore(':'))
                        .putString("sleepLeft", st.timer ?: "")
                        .putBoolean("alarmOn", app.alarm.on)
                        .putString("alarmTime", app.alarm.time)
                        .putString("jasperMood", app.jasper.moodLabel())
                        .apply()
                }
            } catch (_: Exception) { }
            try { ClockWeatherWidget.updateAll(ctx) } catch (_: Exception) { }
            try { QuickControlsWidget.updateAll(ctx) } catch (_: Exception) { }
            try { SleepAlarmWidget.updateAll(ctx) } catch (_: Exception) { }
            try { JasperWidget.updateAll(ctx) } catch (_: Exception) { }
            try { WindowWidget.updateAll(ctx) } catch (_: Exception) { }
            try { NowPlayingWidget.update(ctx, MediaService.state, force = true) } catch (_: Exception) { }
        }
    }

    override fun onReceive(ctx: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == ACTION_WIDGET_ALARM || action == Intent.ACTION_BOOT_COMPLETED) {
            refreshWindowWidgetBackground(ctx)
            return
        }
        if (action != ACTION_WIDGET_CMD) return
        val cmd = intent.getStringExtra("cmd") ?: return
        if (cmd == "refresh_window") {
            onCommand?.invoke(cmd)
            refreshWindowWidgetBackground(ctx, forceSnapshot = true)
            return
        }
        // If MainActivity is alive, handle directly on the live room
        val handledLive = onCommand?.invoke(cmd) == true
        if (!handledLive) {
            // Update persisted state so the widget reflects the change immediately and the room restores it on open
            val s = readSettingsJson(ctx)
            when (cmd) {
                "toggle_sound" -> s.put("audioOn", !s.optBoolean("audioOn", true))
                "toggle_light" -> s.put("fanLight", !s.optBoolean("fanLight", false))
                "toggle_fan" -> s.put("fanSpin", !s.optBoolean("fanSpin", true))
                "toggle_lamp" -> s.put("lamp", !s.optBoolean("lamp", false))
                "toggle_tv" -> s.put("tv", !s.optBoolean("tv", true))
                "cycle_blinds" -> {
                    val blindOrder = intArrayOf(0, 1, 3, 2)
                    val cur = s.optInt("blinds", 0)
                    val idx = blindOrder.indexOf(cur).let { if (it < 0) 0 else it }
                    val next = blindOrder[(idx + 1) % blindOrder.size]
                    s.put("blinds", next)
                }
                "next_station" -> {
                    val r = (s.optInt("radio", 0) + 1) % 4
                    s.put("radio", r)
                    ctx.getSharedPreferences("window_widget", Context.MODE_PRIVATE).edit().putString("infoStation", listOf("OFF", "Q 97.1 FM", "ALT 104.3 FM", "MY MUSIC")[r]).apply()
                }
                "step_season" -> {
                    val list = listOf("spring", "summer", "fall", "winter")
                    val cur = s.optString("season", "fall")
                    val next = list[(list.indexOf(cur) + 1) % list.size]
                    s.put("seasonMode", "manual"); s.put("season", next)
                    ctx.getSharedPreferences("window_widget", Context.MODE_PRIVATE).edit().putString("infoSeason", next.replaceFirstChar { it.uppercase() }).apply()
                }
                "alarm_time" -> {
                    val ap = ctx.getSharedPreferences("ps1bedroom", Context.MODE_PRIVATE)
                    val nt = nextAlarmTime(ap.getString("alarm.time", "07:00") ?: "07:00")
                    ap.edit().putString("alarm.time", nt).apply()
                    ctx.getSharedPreferences("window_widget", Context.MODE_PRIVATE).edit().putString("alarmTime", nt).apply()
                }
                "jasper_pet" -> ctx.getSharedPreferences("window_widget", Context.MODE_PRIVATE).edit().putString("jasperMood", "Tail wagging! 🐾💕").apply()
                "paper_toss" -> ctx.startActivity(Intent(ctx, MainActivity::class.java).apply { setAction("com.ps1bedroom.room.WIDGET_paper_toss"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP) })
                "step_weather" -> {
                    val keys = Environment.WEATHER_KINDS.keys.toList()
                    val cur = s.optString("weatherKind", "clear")
                    val next = keys[(keys.indexOf(cur) + 1) % keys.size]
                    s.put("weatherMode", "manual"); s.put("weatherKind", next)
                    val wLabel = Environment.WEATHER_KINDS[next]?.label ?: "Clear"
                    val wp = ctx.getSharedPreferences("window_widget", Context.MODE_PRIVATE)
                    val oldInfo = wp.getString("infoWeather", "") ?: ""
                    val timePart = oldInfo.substringBefore(' ', "12:00 PM")
                    val tempPart = wp.getString("infoTemp", "72°F")
                    val icon = when (next) {
                        "storm" -> "⛈️"
                        "heat" -> "⚡"
                        "rain", "downpour" -> "🌧️"
                        "drizzle", "sunshower" -> "🌦️"
                        "snow", "blizzard", "flurries" -> "🌨️"
                        "fog", "mist" -> "🌫️"
                        "windy" -> "💨"
                        "overcast", "partly" -> "⛅"
                        else -> "☀️"
                    }
                    wp.edit()
                        .putString("infoWeatherOnly", wLabel)
                        .putString("infoWeather", "$timePart $tempPart $icon $wLabel".trim())
                        .apply()
                }
                "step_place" -> {
                    val keys = Places.OUTSIDE_ENVS.keys.toList()
                    val cur = s.optString("outside", "city")
                    val next = keys[(keys.indexOf(cur) + 1) % keys.size]
                    s.put("outside", next)
                    val p = Places.OUTSIDE_ENVS[next]
                    if (p != null) ctx.getSharedPreferences("window_widget", Context.MODE_PRIVATE).edit()
                        .putString("infoPlace", "${p.icon} ${p.name.replace(Regex(" \\(.*\\)$"), "")}").apply()
                }
                "sleep_mode", "sleep_15" -> {
                    ctx.startActivity(Intent(ctx, MainActivity::class.java).apply {
                        setAction("com.ps1bedroom.room.WIDGET_$cmd")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    })
                }
                "toggle_alarm" -> {
                    val ap = ctx.getSharedPreferences("ps1bedroom", Context.MODE_PRIVATE)
                    val on = !ap.getBoolean("alarm.on", false)
                    ap.edit().putBoolean("alarm.on", on).apply()
                    ctx.getSharedPreferences("window_widget", Context.MODE_PRIVATE).edit().putBoolean("alarmOn", on).apply()
                }
                "jasper_feed", "jasper_water", "jasper_play", "jasper_sleep" -> {
                    val j = s.optJSONObject("jasper") ?: JSONObject()
                    when (cmd) {
                        "jasper_feed" -> { j.put("hunger", 100.0); j.put("fun", (j.optDouble("fun", 86.0) + 10.0).coerceAtMost(100.0)) }
                        "jasper_water" -> j.put("thirst", 100.0)
                        "jasper_play" -> { j.put("fun", 100.0); j.put("energy", (j.optDouble("energy", 85.0) - 8.0).coerceAtLeast(15.0)) }
                        "jasper_sleep" -> j.put("energy", (j.optDouble("energy", 85.0) + 35.0).coerceAtMost(100.0))
                    }
                    s.put("jasper", j)
                    val mood = when (cmd) {
                        "jasper_feed" -> "Eating kibble 🍖"
                        "jasper_water" -> "Drinking water 💧"
                        "jasper_play" -> "Playing zoomies 🎾"
                        else -> "Sleeping zZz 💤"
                    }
                    ctx.getSharedPreferences("window_widget", Context.MODE_PRIVATE).edit().putString("jasperMood", mood).apply()
                }
            }
            writeSettingsJson(ctx, s)
            updateAllWidgets(ctx, null)
            if (cmd in listOf("step_weather", "step_place", "cycle_blinds")) {
                refreshWindowWidgetBackground(ctx, forceSnapshot = true)
            }
        } else {
            if (cmd in listOf("step_weather", "step_place", "cycle_blinds")) {
                refreshWindowWidgetBackground(ctx, forceSnapshot = true)
            }
        }
    }
}

/** 1. Retro 90s Digital Clock + Weather & Place Widget */
class ClockWeatherWidget : AppWidgetProvider() {
    companion object {
        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, ClockWeatherWidget::class.java))
            if (ids.isEmpty()) return
            for (id in ids) mgr.updateAppWidget(id, views(ctx))
        }
        private fun views(ctx: Context): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_clock_weather)
            val wp = ctx.getSharedPreferences("window_widget", Context.MODE_PRIVATE)
            val temp = wp.getString("infoTemp", "72°F") ?: "72°F"
            val weather = wp.getString("infoWeatherOnly", "☀️ Clear") ?: "☀️ Clear"
            val place = wp.getString("infoPlace", "🌆 Downtown") ?: "🌆 Downtown"
            val season = wp.getString("infoSeason", "") ?: ""
            val sIcon = when (season.lowercase()) { "spring" -> "🌸"; "summer" -> "☀️"; "fall" -> "🍂"; "winter" -> "❄️"; else -> "" }
            v.setTextViewText(R.id.cw_temp, temp)
            v.setTextViewText(R.id.cw_weather, listOfNotNull(if (season.isNotEmpty()) "$sIcon $season" else null, weather, place).joinToString("  ·  "))
            v.setOnClickPendingIntent(R.id.cw_root, RoomWidgetReceiver.openPendingIntent(ctx, 300))
            v.setOnClickPendingIntent(R.id.cw_step_weather, RoomWidgetReceiver.cmdPendingIntent(ctx, "step_weather", 301))
            v.setOnClickPendingIntent(R.id.cw_step_place, RoomWidgetReceiver.cmdPendingIntent(ctx, "step_place", 302))
            v.setOnClickPendingIntent(R.id.cw_step_season, RoomWidgetReceiver.cmdPendingIntent(ctx, "step_season", 303))
            return v
        }
    }
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) { for (id in ids) mgr.updateAppWidget(id, views(ctx)) }
}

/** 2. Room Quick-Controls Widget (Sound, Fan Light, Bedroom TV, Blinds, Sleep Mode) */
class QuickControlsWidget : AppWidgetProvider() {
    companion object {
        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, QuickControlsWidget::class.java))
            if (ids.isEmpty()) return
            for (id in ids) mgr.updateAppWidget(id, views(ctx))
        }
        private fun views(ctx: Context): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_quick_controls)
            val raw = ctx.getSharedPreferences("ps1bedroom", Context.MODE_PRIVATE).getString("settings.v1", null)
            val s = if (raw != null) try { JSONObject(raw) } catch (_: Exception) { JSONObject() } else JSONObject()
            val sndOn = s.optBoolean("audioOn", true)
            val lightOn = s.optBoolean("fanLight", false)
            val tvOn = s.optBoolean("tv", true)
            val blinds = s.optInt("blinds", 0)
            val blindNames = arrayOf("Slatted", "Open", "Up", "Shut")
            val fanOn = s.optBoolean("fanSpin", true)
            val dim = Color.rgb(0xb9, 0xb4, 0xe6)
            v.setTextViewText(R.id.qc_sound, "${if (sndOn) "🔊" else "🔈"}\n${if (sndOn) "On" else "Muted"}")
            v.setTextColor(R.id.qc_sound, if (sndOn) Color.rgb(0x86, 0xef, 0xac) else dim)
            v.setTextViewText(R.id.qc_light, "💡\n${if (lightOn) "On" else "Off"}")
            v.setTextColor(R.id.qc_light, if (lightOn) Color.rgb(0xff, 0xd2, 0x7d) else dim)
            v.setTextViewText(R.id.qc_fan, "🌀\n${if (fanOn) "On" else "Off"}")
            v.setTextColor(R.id.qc_fan, if (fanOn) Color.rgb(0x7d, 0xd3, 0xfc) else dim)
            v.setTextViewText(R.id.qc_tv, "📺\n${if (tvOn) "On" else "Off"}")
            v.setTextColor(R.id.qc_tv, if (tvOn) Color.rgb(0x7d, 0xd3, 0xfc) else dim)
            v.setTextViewText(R.id.qc_blinds, "🪟\n${blindNames.getOrElse(blinds) { "Slatted" }}")
            v.setTextColor(R.id.qc_blinds, if (blinds != 3) Color.WHITE else dim)
            val wp = ctx.getSharedPreferences("window_widget", Context.MODE_PRIVATE)
            v.setTextViewText(R.id.qc_status, "${wp.getString("infoTemp", "") ?: ""} ${wp.getString("infoWeatherOnly", "") ?: ""}".trim().ifEmpty { "Tap to toggle" })
            v.setOnClickPendingIntent(R.id.qc_root, RoomWidgetReceiver.openPendingIntent(ctx, 400))
            v.setOnClickPendingIntent(R.id.qc_sound, RoomWidgetReceiver.cmdPendingIntent(ctx, "toggle_sound", 401))
            v.setOnClickPendingIntent(R.id.qc_light, RoomWidgetReceiver.cmdPendingIntent(ctx, "toggle_light", 402))
            v.setOnClickPendingIntent(R.id.qc_fan, RoomWidgetReceiver.cmdPendingIntent(ctx, "toggle_fan", 406))
            v.setOnClickPendingIntent(R.id.qc_tv, RoomWidgetReceiver.cmdPendingIntent(ctx, "toggle_tv", 403))
            v.setOnClickPendingIntent(R.id.qc_blinds, RoomWidgetReceiver.cmdPendingIntent(ctx, "cycle_blinds", 404))
            v.setOnClickPendingIntent(R.id.qc_sleep, RoomWidgetReceiver.cmdPendingIntent(ctx, "sleep_mode", 405))
            return v
        }
    }
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) { for (id in ids) mgr.updateAppWidget(id, views(ctx)) }
}

/** 3. Sleep Timer & Sunrise Alarm Widget */
class SleepAlarmWidget : AppWidgetProvider() {
    companion object {
        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, SleepAlarmWidget::class.java))
            if (ids.isEmpty()) return
            for (id in ids) mgr.updateAppWidget(id, views(ctx))
        }
        private fun views(ctx: Context): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_sleep_alarm)
            val wp = ctx.getSharedPreferences("window_widget", Context.MODE_PRIVATE)
            val ap = ctx.getSharedPreferences("ps1bedroom", Context.MODE_PRIVATE)
            val alarmOn = ap.getBoolean("alarm.on", wp.getBoolean("alarmOn", false))
            val alarmTime = ap.getString("alarm.time", wp.getString("alarmTime", "07:00")) ?: "07:00"
            val sleepLeft = wp.getString("sleepLeft", "") ?: ""
            v.setTextViewText(R.id.sa_alarm_status, if (alarmOn) "⏰ $alarmTime ON" else "⏰ $alarmTime Off")
            v.setTextColor(R.id.sa_alarm_status, if (alarmOn) Color.rgb(0x86, 0xef, 0xac) else Color.rgb(0xb9, 0xb4, 0xe6))
            v.setTextViewText(R.id.sa_timer_status, if (sleepLeft.isNotEmpty()) "Sleep timer active: $sleepLeft remaining" else "One tap: lights down, fan on & Jasper on the bed")
            v.setTextViewText(R.id.sa_btn_alarm, if (alarmOn) "⏰ ON" else "⏰ OFF")
            v.setTextViewText(R.id.sa_btn_alarm_time, "🕒 $alarmTime")
            v.setTextViewText(R.id.sa_btn_15, if (sleepLeft.isNotEmpty()) "⏾ Cancel" else "⏾ 15m")
            v.setOnClickPendingIntent(R.id.sa_root, RoomWidgetReceiver.openPendingIntent(ctx, 500))
            v.setOnClickPendingIntent(R.id.sa_btn_sleep, RoomWidgetReceiver.cmdPendingIntent(ctx, "sleep_mode", 501))
            v.setOnClickPendingIntent(R.id.sa_btn_15, RoomWidgetReceiver.cmdPendingIntent(ctx, "sleep_15", 502))
            v.setOnClickPendingIntent(R.id.sa_btn_alarm, RoomWidgetReceiver.cmdPendingIntent(ctx, "toggle_alarm", 503))
            v.setOnClickPendingIntent(R.id.sa_btn_alarm_time, RoomWidgetReceiver.cmdPendingIntent(ctx, "alarm_time", 504))
            return v
        }
    }
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) { for (id in ids) mgr.updateAppWidget(id, views(ctx)) }
}

/** 4. Jasper Companion Widget (Pixel-art portrait in his custom coat colours + Eat, Drink, Play, Sleep buttons) */
class JasperWidget : AppWidgetProvider() {
    companion object {
        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, JasperWidget::class.java))
            if (ids.isEmpty()) return
            for (id in ids) mgr.updateAppWidget(id, views(ctx))
        }
        private fun drawPortrait(coatHex: String, darkHex: String, lightHex: String, sleeping: Boolean): Bitmap {
            val b = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            val p = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL }
            fun col(hex: String, def: Int): Int = try { Color.parseColor(hex) } catch (_: Exception) { def }
            val coat = col(coatHex, Color.rgb(0xb9, 0x7a, 0x3e))
            val dark = col(darkHex, Color.rgb(0x1b, 0x14, 0x10))
            val light = col(lightHex, Color.rgb(0xd9, 0xb2, 0x7a))
            fun rect(x: Int, y: Int, w: Int, h: Int, color: Int) { p.color = color; c.drawRect(x * 4f, y * 4f, (x + w) * 4f, (y + h) * 4f, p) }
            // Upright German Shepherd ears
            rect(2, 1, 3, 4, dark); rect(11, 1, 3, 4, dark)
            rect(3, 2, 1, 2, Color.rgb(0x8a, 0x4d, 0x48)); rect(12, 2, 1, 2, Color.rgb(0x8a, 0x4d, 0x48))
            // Skull & cheeks
            rect(3, 4, 10, 8, coat)
            rect(4, 4, 8, 2, dark) // crown
            rect(2, 8, 2, 3, light); rect(12, 8, 2, 3, light) // cheeks
            rect(4, 5, 2, 1, light); rect(10, 5, 2, 1, light) // eyebrows
            // Eyes (right eye ice-blue, left eye heterochromia blue/brown, or closed when sleeping)
            if (sleeping) {
                rect(4, 7, 2, 1, Color.rgb(0x09, 0x07, 0x05))
                rect(10, 7, 2, 1, Color.rgb(0x09, 0x07, 0x05))
            } else {
                rect(4, 6, 2, 2, Color.rgb(0x2e, 0x7f, 0xd0)); rect(4, 6, 1, 1, Color.WHITE)
                rect(10, 6, 2, 1, Color.rgb(0x2e, 0x7f, 0xd0)); rect(10, 7, 2, 1, Color.rgb(0x6b, 0x38, 0x16)); rect(10, 6, 1, 1, Color.WHITE)
            }
            // Snout & nose & red collar
            rect(5, 8, 6, 4, dark)
            rect(7, 8, 2, 2, Color.rgb(0x0a, 0x0a, 0x0a))
            rect(6, 12, 4, 1, light)
            rect(4, 13, 8, 2, Color.rgb(0xb9, 0x1c, 0x1c))
            rect(7, 14, 2, 2, Color.rgb(0xd4, 0xa0, 0x17))
            return b
        }
        private fun views(ctx: Context): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_jasper)
            val raw = ctx.getSharedPreferences("ps1bedroom", Context.MODE_PRIVATE).getString("settings.v1", null)
            val s = if (raw != null) try { JSONObject(raw) } catch (_: Exception) { JSONObject() } else JSONObject()
            val j = s.optJSONObject("jasper") ?: JSONObject()
            val coat = j.optString("coat", "#b97a3e")
            val dark = j.optString("dark", "#1b1410")
            val light = j.optString("light", "#d9b27a")
            val hunger = j.optDouble("hunger", 88.0).roundToInt().coerceIn(10, 100)
            val thirst = j.optDouble("thirst", 90.0).roundToInt().coerceIn(10, 100)
            val energy = j.optDouble("energy", 85.0).roundToInt().coerceIn(10, 100)
            val funLvl = j.optDouble("fun", 86.0).roundToInt().coerceIn(10, 100)
            val wp = ctx.getSharedPreferences("window_widget", Context.MODE_PRIVATE)
            val mood = wp.getString("jasperMood", "Happy & cozy 🐾") ?: "Happy & cozy 🐾"
            v.setImageViewBitmap(R.id.jw_avatar, drawPortrait(coat, dark, light, mood.contains("Sleep")))
            v.setTextViewText(R.id.jw_title, "🐕 JASPER · $mood")
            v.setTextViewText(R.id.jw_stats, "🍖 $hunger%   💧 $thirst%   💤 $energy%   🎾 $funLvl%")
            v.setOnClickPendingIntent(R.id.jw_root, RoomWidgetReceiver.openPendingIntent(ctx, 600))
            v.setOnClickPendingIntent(R.id.jw_pet, RoomWidgetReceiver.cmdPendingIntent(ctx, "jasper_pet", 605))
            v.setOnClickPendingIntent(R.id.jw_feed, RoomWidgetReceiver.cmdPendingIntent(ctx, "jasper_feed", 601))
            v.setOnClickPendingIntent(R.id.jw_water, RoomWidgetReceiver.cmdPendingIntent(ctx, "jasper_water", 602))
            v.setOnClickPendingIntent(R.id.jw_play, RoomWidgetReceiver.cmdPendingIntent(ctx, "jasper_play", 603))
            v.setOnClickPendingIntent(R.id.jw_sleep, RoomWidgetReceiver.cmdPendingIntent(ctx, "jasper_sleep", 604))
            return v
        }
    }
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) { for (id in ids) mgr.updateAppWidget(id, views(ctx)) }
}
