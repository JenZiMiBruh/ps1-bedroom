package com.ps1bedroom.room

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar

/**
 * "Window" home-screen widget: the real view out of your bedroom window (a snapshot the room renders through the
 * glass every so often while it's open), a live clock and date (ticked by Android itself, so they're always right
 * even when the app is closed), and the weather, temperature and place. Each widget has its own options.
 */
class WindowWidget : AppWidgetProvider() {
    class Options(val time: Boolean, val date: Boolean, val weather: Boolean, val place: Boolean, val frame: Boolean, val blinds: Boolean, val refreshBtn: Boolean = true, val refreshMin: Int = 5)
    companion object {
        private const val PREFS = "window_widget"
        fun refreshIntervalSec(ctx: Context): Float =
            (ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("refreshMin", 5).coerceIn(1, 60) * 60).toFloat()
        fun options(ctx: Context, id: Int): Options {
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return Options(p.getBoolean("time$id", true), p.getBoolean("date$id", true), p.getBoolean("weather$id", true),
                p.getBoolean("place$id", true), p.getBoolean("frame$id", true), p.getBoolean("blinds$id", true),
                p.getBoolean("refreshBtn$id", true), p.getInt("refreshMin", 5))
        }
        fun saveOptions(ctx: Context, id: Int, o: Options) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean("time$id", o.time).putBoolean("date$id", o.date).putBoolean("weather$id", o.weather)
                .putBoolean("place$id", o.place).putBoolean("frame$id", o.frame).putBoolean("blinds$id", o.blinds)
                .putBoolean("refreshBtn$id", o.refreshBtn).putInt("refreshMin", o.refreshMin).apply()
        }
        private fun shotFile(ctx: Context, blinds: Boolean) = File(ctx.filesDir, if (blinds) "window_view_blinds.png" else "window_view.png")
        /** The text under the clock: "☀️ 82°F Clear · 🌆 Downtown". */
        fun saveInfo(ctx: Context, weather: String, place: String) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("infoWeather", weather).putString("infoPlace", place).apply()
        }
        fun scheduleNextRefresh(ctx: Context) {
            try {
                if (!hasWidgets(ctx)) return
                val am = ctx.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager ?: return
                val pi = PendingIntent.getBroadcast(
                    ctx, 888,
                    Intent(ctx, RoomWidgetReceiver::class.java).setAction(RoomWidgetReceiver.ACTION_WIDGET_ALARM),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val intervalSec = refreshIntervalSec(ctx)
                val intervalMs = (intervalSec * 1000L).toLong().coerceIn(60_000L, 3600_000L)
                val triggerAt = System.currentTimeMillis() + intervalMs
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    am.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pi)
                } else {
                    am.set(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pi)
                }
            } catch (e: Exception) {
                android.util.Log.w("PS1Bedroom", "scheduleNextRefresh failed", e)
            }
        }
        fun hasWidgets(ctx: Context) = AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, WindowWidget::class.java)).isNotEmpty()
        /** New snapshots from the room (any thread): saved, then every window widget redrawn. */
        fun saveShots(ctx: Context, clear: Bitmap, withBlinds: Bitmap) {
            for ((b, f) in listOf(clear to shotFile(ctx, false), withBlinds to shotFile(ctx, true)))
                try { FileOutputStream(f).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } } catch (e: Exception) { }
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("shotAt", System.currentTimeMillis()).apply()
            updateAll(ctx)
        }
        fun updateAll(ctx: Context) {
            try {
                val mgr = AppWidgetManager.getInstance(ctx)
                for (id in mgr.getAppWidgetIds(ComponentName(ctx, WindowWidget::class.java))) {
                    try { mgr.updateAppWidget(id, views(ctx, id)) } catch (e: Exception) {
                        android.util.Log.w("PS1Bedroom", "updateAppWidget failed for $id", e)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("PS1Bedroom", "WindowWidget.updateAll failed", e)
            }
        }
        private fun views(ctx: Context, id: Int): android.widget.RemoteViews {
            val v = android.widget.RemoteViews(ctx.packageName, R.layout.widget_window)
            try {
                val o = options(ctx, id)
                val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

                // Persisted room settings
                val raw = ctx.getSharedPreferences("ps1bedroom", Context.MODE_PRIVATE).getString("settings.v1", null)
                val s = if (raw != null) try { org.json.JSONObject(raw) } catch (_: Exception) { org.json.JSONObject() } else org.json.JSONObject()
                val blindsState = s.optInt("blinds", 0) // 0 slatted · 1 tilted open · 3 rolled up · 2 closed

                // Base snapshot from room (or dynamic outdoor view)
                val shotAt = p.getLong("shotAt", 0L)
                val now = System.currentTimeMillis()
                val outsideKey = s.optString("outside", "city")
                val lastShotPlace = p.getString("shotPlace", outsideKey)
                val weatherKind = s.optString("weatherKind", "clear")
                val isStale = (now - shotAt) > 20 * 60 * 1000L || outsideKey != lastShotPlace

                val baseShot = try {
                    if (!isStale && shotFile(ctx, false).exists() && shotFile(ctx, false).length() > 0) {
                        BitmapFactory.decodeFile(shotFile(ctx, false).path) ?: generateOutdoorSnapshot(ctx, s, outsideKey, weatherKind)
                    } else {
                        generateOutdoorSnapshot(ctx, s, outsideKey, weatherKind)
                    }
                } catch (_: Exception) {
                    generateOutdoorSnapshot(ctx, s, outsideKey, weatherKind)
                }

                val rendered = renderWindow(baseShot, blindsState, o.blinds, o.frame, s)
                if (rendered != null) {
                    try {
                        v.setImageViewBitmap(R.id.ww_view, rendered)
                    } catch (_: Exception) {
                        v.setImageViewResource(R.id.ww_view, R.mipmap.ic_launcher)
                    }
                } else {
                    v.setImageViewResource(R.id.ww_view, R.mipmap.ic_launcher)
                }

                v.setViewVisibility(R.id.ww_time, if (o.time) View.VISIBLE else View.GONE)
                v.setViewVisibility(R.id.ww_date, if (o.date) View.VISIBLE else View.GONE)
                val weather = p.getString("infoWeather", null); val place = p.getString("infoPlace", null)
                val ago = if (shotAt > 0) ((now - shotAt) / 60000L).let { m -> if (m < 1) "now" else if (m < 60) "${m}m ago" else "${m / 60}h ago" } else null
                val weatherText = weather ?: "${p.getString("infoTemp", "72°F")} ${p.getString("infoWeatherOnly", "☀️ Clear")}"
                val line = listOfNotNull(weatherText.takeIf { o.weather }, place.takeIf { o.place }, ago?.let { "📷 $it" }).joinToString("  ·  ")
                v.setTextViewText(R.id.ww_weather, line.ifEmpty { "PS1 Bedroom Window" })
                v.setViewVisibility(R.id.ww_weather, if (line.isNotEmpty()) View.VISIBLE else View.GONE)
                v.setViewVisibility(R.id.ww_info, if (o.time || o.date || line.isNotEmpty()) View.VISIBLE else View.GONE)
                v.setViewVisibility(R.id.ww_refresh, if (o.refreshBtn) View.VISIBLE else View.GONE)

                if (shotAt <= 0L || isStale) {
                    RoomWidgetReceiver.refreshWindowWidgetBackground(ctx)
                }
                val open = PendingIntent.getActivity(ctx, 200, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
                val refreshPi = PendingIntent.getBroadcast(ctx, 201, Intent(ctx, RoomWidgetReceiver::class.java).setAction(RoomWidgetReceiver.ACTION_WIDGET_CMD).putExtra("cmd", "refresh_window"),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                v.setOnClickPendingIntent(R.id.ww_root, open)
                v.setOnClickPendingIntent(R.id.ww_refresh, refreshPi)

                // Room controls right on the window (work whether the room is open or not)
                val on = Color.WHITE; val off = Color.argb(150, 255, 255, 255)
                v.setTextColor(R.id.ww_light_btn, if (s.optBoolean("fanLight", false)) on else off)
                v.setTextViewText(R.id.ww_sound_btn, if (s.optBoolean("audioOn", true)) "🔊" else "🔈")
                v.setTextColor(R.id.ww_tv_btn, if (s.optBoolean("tv", true)) on else off)
                // Blinds indicator: show closed highlight when shut
                val blindIcons = listOf("🪟", "🪟", "🚪", "🔼")
                v.setTextViewText(R.id.ww_blinds_btn, blindIcons.getOrElse(blindsState) { "🪟" })
                v.setTextColor(R.id.ww_blinds_btn, if (blindsState == 2) Color.rgb(255, 185, 90) else on)

                v.setOnClickPendingIntent(R.id.ww_light_btn, RoomWidgetReceiver.cmdPendingIntent(ctx, "toggle_light", 202))
                v.setOnClickPendingIntent(R.id.ww_tv_btn, RoomWidgetReceiver.cmdPendingIntent(ctx, "toggle_tv", 203))
                v.setOnClickPendingIntent(R.id.ww_sound_btn, RoomWidgetReceiver.cmdPendingIntent(ctx, "toggle_sound", 204))
                v.setOnClickPendingIntent(R.id.ww_weather_btn, RoomWidgetReceiver.cmdPendingIntent(ctx, "step_weather", 205))
                v.setOnClickPendingIntent(R.id.ww_blinds_btn, RoomWidgetReceiver.cmdPendingIntent(ctx, "cycle_blinds", 206))
                v.setOnClickPendingIntent(R.id.ww_place_btn, RoomWidgetReceiver.cmdPendingIntent(ctx, "step_place", 207))
            } catch (e: Exception) {
                android.util.Log.e("PS1Bedroom", "WindowWidget views failed", e)
                v.setImageViewResource(R.id.ww_view, R.mipmap.ic_launcher)
            }
            return v
        }
        /** Composites live window blinds, weather atmosphere, and wooden casing over the window shot. */
        private fun renderWindow(base: Bitmap, blindsState: Int, showBlinds: Boolean, frame: Boolean, s: org.json.JSONObject): Bitmap? {
            return try {
                val bw = base.width.coerceIn(120, 360)
                val bh = (bw * 2 / 3).coerceIn(80, 240)
                val out = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
                val c = Canvas(out)
                c.drawBitmap(base, null, android.graphics.RectF(0f, 0f, bw.toFloat(), bh.toFloat()), null)

                val w = bw.toFloat()
                val h = bh.toFloat()
                val t = if (frame) w * 0.038f else 0f
                val left = t
                val top = t
                val right = w - t
                val bottom = h - t
                val winW = (right - left).coerceAtLeast(10f)
                val winH = (bottom - top).coerceAtLeast(10f)
                val safeW = winW.toInt().coerceAtLeast(1)
                val safeH = winH.toInt().coerceAtLeast(1)

                // Weather atmosphere overlay on the glass
                val weatherKind = s.optString("weatherKind", "clear")
                if (weatherKind in listOf("rain", "downpour", "sunshower", "storm")) {
                    val rainP = Paint().apply { color = Color.argb(120, 205, 225, 255); strokeWidth = 1.8f }
                    for (i in 0 until 24) {
                        val rx = left + ((i * 37) % safeW).toFloat()
                        val ry = top + ((i * 53) % safeH).toFloat()
                        c.drawLine(rx, ry, rx - 5f, ry + 16f, rainP)
                    }
                } else if (weatherKind in listOf("snow", "blizzard", "flurries")) {
                    val snowP = Paint().apply { color = Color.argb(170, 242, 248, 255) }
                    for (i in 0 until 28) {
                        val sx = left + ((i * 43) % safeW).toFloat()
                        val sy = top + ((i * 61) % safeH).toFloat()
                        c.drawCircle(sx, sy, if (i % 3 == 0) 2.0f else 1.2f, snowP)
                    }
                }

                // Blinds: if blinds are closed in the room (blindsState == 2), ALWAYS show them closed!
                val mustShowClosed = blindsState == 2
                val drawBlinds = showBlinds || mustShowClosed

                if (drawBlinds) {
                    val slatBase = Color.rgb(0x8a, 0x9c, 0xc2)
                    val slatLight = Color.rgb(0xb8, 0xca, 0xe8)
                    val slatDark = Color.rgb(0x52, 0x60, 0x7a)
                    val cordCol = Color.argb(220, 0x42, 0x4e, 0x68)
                    val cordHi = Color.argb(180, 0xd8, 0xe4, 0xf6)
                    val cordPaint = Paint().apply { strokeWidth = 1.6f }
                    val cordX = floatArrayOf(left + winW * 0.22f, left + winW * 0.50f, left + winW * 0.78f)

                    when (blindsState) {
                        2 -> {
                            // 2 = CLOSED: Venetian blinds tilted fully shut covering the window opening
                            val numSlats = 18
                            val slatH = winH / numSlats
                            val pSlat = Paint().apply { color = slatBase }
                            val pHi = Paint().apply { color = slatLight; strokeWidth = 1.5f }
                            val pSh = Paint().apply { color = slatDark; strokeWidth = 2.0f }

                            // Top header box
                            val pHeader = Paint().apply { color = Color.rgb(0x76, 0x88, 0xad) }
                            c.drawRect(left, top, right, top + slatH * 0.9f, pHeader)
                            c.drawLine(left, top + slatH * 0.9f, right, top + slatH * 0.9f, pSh)

                            for (i in 0 until numSlats) {
                                val y0 = top + i * slatH
                                val y1 = y0 + slatH + 1.5f
                                c.drawRect(left, y0, right, y1, pSlat)
                                c.drawLine(left, y0, right, y0, pHi)
                                c.drawLine(left, y1, right, y1, pSh)
                            }

                            // Bottom weight rail
                            val pRail = Paint().apply { color = Color.rgb(0x6e, 0x7e, 0xa2) }
                            c.drawRect(left, bottom - slatH * 0.85f, right, bottom, pRail)
                            c.drawLine(left, bottom - slatH * 0.85f, right, bottom - slatH * 0.85f, pHi)

                            // 3 Vertical pull cords over the closed slats
                            for (cx in cordX) {
                                cordPaint.color = cordCol; c.drawLine(cx, top, cx, bottom, cordPaint)
                                cordPaint.color = cordHi; c.drawLine(cx + 1f, top, cx + 1f, bottom, cordPaint)
                            }
                        }
                        0 -> {
                            // 0 = SLATTED: horizontal slat blades with clear gaps revealing the outside
                            val numSlats = 18
                            val stepY = winH / numSlats
                            val slatThick = stepY * 0.44f
                            val pSlat = Paint().apply { color = slatBase }
                            val pHi = Paint().apply { color = slatLight; strokeWidth = 1.2f }
                            val pDropShadow = Paint().apply { color = Color.argb(90, 0, 0, 0) }

                            // Top header
                            c.drawRect(left, top, right, top + stepY * 0.8f, Paint().apply { color = Color.rgb(0x76, 0x88, 0xad) })

                            for (i in 0 until numSlats) {
                                val y0 = top + i * stepY
                                val y1 = y0 + slatThick
                                c.drawRect(left, y1, right, y1 + 3f, pDropShadow)
                                c.drawRect(left, y0, right, y1, pSlat)
                                c.drawLine(left, y0, right, y0, pHi)
                            }

                            // Vertical pull cords
                            for (cx in cordX) {
                                cordPaint.color = cordCol; c.drawLine(cx, top, cx, bottom, cordPaint)
                                cordPaint.color = cordHi; c.drawLine(cx + 1f, top, cx + 1f, bottom, cordPaint)
                            }
                        }
                        1 -> {
                            // 1 = TILTED OPEN: slats turned edge-on (thin horizontal lines, wide clear view)
                            val numSlats = 18
                            val stepY = winH / numSlats
                            val pLine = Paint().apply { color = Color.argb(200, 0x8a, 0x9c, 0xc2); strokeWidth = 2.0f }
                            c.drawRect(left, top, right, top + stepY * 0.7f, Paint().apply { color = Color.rgb(0x76, 0x88, 0xad) })
                            for (i in 0 until numSlats) {
                                val y = top + i * stepY
                                c.drawLine(left, y, right, y, pLine)
                            }
                            for (cx in cordX) {
                                cordPaint.color = cordCol; c.drawLine(cx, top, cx, bottom, cordPaint)
                            }
                        }
                        3 -> {
                            // 3 = ROLLED UP: slats stacked in a compact bundle at top header, glass completely open
                            val stackH = winH * 0.12f
                            val pStack = Paint().apply { color = Color.rgb(0x7e, 0x90, 0xb6) }
                            c.drawRect(left, top, right, top + stackH, pStack)
                            val pLine = Paint().apply { color = Color.argb(140, 0x48, 0x54, 0x6e); strokeWidth = 1.0f }
                            for (i in 1..8) {
                                val ly = top + i * (stackH / 8f)
                                c.drawLine(left, ly, right, ly, pLine)
                            }
                            cordPaint.color = cordCol
                            c.drawLine(left + winW * 0.08f, top + stackH, left + winW * 0.08f, top + winH * 0.55f, cordPaint)
                            c.drawLine(left + winW * 0.10f, top + stackH, left + winW * 0.10f, top + winH * 0.58f, cordPaint)
                        }
                    }
                }

                // Outer wooden window frame & center mullion / sash
                if (frame) {
                    val wood = Paint().apply { color = Color.rgb(0xe8, 0xe4, 0xda) }
                    val shade = Paint().apply { color = Color.argb(90, 0, 0, 0) }
                    val woodDark = Paint().apply { color = Color.rgb(0xb0, 0xaa, 0x9e) }
                    c.drawRect(0f, 0f, w, t, wood)
                    c.drawRect(0f, h - t, w, h, wood)
                    c.drawRect(0f, 0f, t, h, wood)
                    c.drawRect(w - t, 0f, w, h, wood)
                    c.drawRect(w / 2 - t * 0.4f, 0f, w / 2 + t * 0.4f, h, wood)
                    c.drawRect(t, t, w - t, t + t * 0.35f, shade)
                    c.drawRect(t, t, t + t * 0.35f, h - t, shade)

                    // Horizontal sash rail (raised up if window is open!)
                    val windowOpen = s.optBoolean("windowOpen", false)
                    val sashY = if (windowOpen) top + winH * 0.26f else top + winH * 0.50f
                    val sashH = t * 0.65f
                    c.drawRect(t, sashY - sashH * 0.5f, w - t, sashY + sashH * 0.5f, wood)
                    c.drawLine(t, sashY + sashH * 0.5f, w - t, sashY + sashH * 0.5f, woodDark)
                    if (windowOpen) {
                        // Open aperture indicator at bottom sill
                        val sillAperture = Paint().apply { color = Color.argb(60, 0, 0, 0) }
                        c.drawRect(t, bottom - t * 0.4f, w - t, bottom, sillAperture)
                    }
                }

                if (out.width > 360 || out.height > 240) Bitmap.createScaledBitmap(out, 360, 240, true) else out
            } catch (e: Exception) {
                android.util.Log.e("PS1Bedroom", "renderWindow failed", e)
                null
            }
        }

        /** Generates a self-sufficient, time-of-day and weather-accurate PS1 pixel-art outdoor scene in 2D Canvas. */
        fun generateOutdoorSnapshot(ctx: Context, s: JSONObject, outsideKey: String, weatherKind: String): Bitmap {
            val w = 360; val h = 240
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)

            val cal = Calendar.getInstance()
            val hour24 = cal.get(Calendar.HOUR_OF_DAY)
            val timeMode = s.optString("timeMode", "live")
            val phase = when (timeMode) {
                "day" -> "day"
                "sunset", "dusk" -> "dusk"
                "night" -> "night"
                "dawn" -> "dawn"
                else -> when (hour24) {
                    in 6..7 -> "dawn"
                    in 8..17 -> "day"
                    in 18..20 -> "dusk"
                    else -> "night"
                }
            }
            val isNight = phase == "night"
            val isDusk = phase == "dusk"
            val isDawn = phase == "dawn"
            val isWinter = s.optString("season", "fall") == "winter" || weatherKind in listOf("snow", "blizzard", "flurries")

            // 1. Sky Gradient
            val (skyTop, skyBot) = when {
                weatherKind == "storm" -> Color.rgb(18, 20, 32) to Color.rgb(42, 46, 62)
                weatherKind in listOf("rain", "downpour") -> Color.rgb(44, 54, 72) to Color.rgb(96, 112, 132)
                weatherKind in listOf("snow", "blizzard") -> Color.rgb(105, 120, 138) to Color.rgb(175, 190, 208)
                weatherKind == "overcast" -> Color.rgb(72, 88, 110) to Color.rgb(138, 155, 178)
                isNight -> Color.rgb(8, 12, 28) to Color.rgb(24, 30, 56)
                isDawn -> Color.rgb(48, 32, 68) to Color.rgb(250, 170, 120)
                isDusk -> Color.rgb(40, 24, 62) to Color.rgb(245, 140, 65)
                else -> Color.rgb(55, 120, 205) to Color.rgb(160, 208, 248)
            }
            val skyPaint = Paint().apply {
                shader = LinearGradient(0f, 0f, 0f, 190f, skyTop, skyBot, Shader.TileMode.CLAMP)
            }
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), skyPaint)

            // 2. Celestial Elements (Sun / Moon / Stars)
            if (weatherKind !in listOf("storm", "downpour", "blizzard", "overcast")) {
                if (isNight) {
                    val starPaint = Paint().apply { color = Color.argb(190, 255, 255, 255) }
                    val starSeeds = intArrayOf(25, 45, 78, 112, 145, 190, 220, 260, 295, 330, 50, 85, 130, 175, 240, 280, 315)
                    val starY = intArrayOf(18, 32, 15, 40, 22, 12, 35, 18, 42, 25, 60, 52, 68, 55, 64, 48, 58)
                    for (i in starSeeds.indices) {
                        c.drawRect(starSeeds[i].toFloat(), starY[i].toFloat(), starSeeds[i] + 1.5f, starY[i] + 1.5f, starPaint)
                    }
                    val moonGlow = Paint().apply { color = Color.argb(35, 230, 240, 255); isAntiAlias = true }
                    val moonPaint = Paint().apply { color = Color.rgb(245, 248, 255); isAntiAlias = true }
                    val mx = 270f; val my = 48f
                    c.drawCircle(mx, my, 22f, moonGlow)
                    c.drawCircle(mx, my, 13f, moonPaint)
                    val shadowPaint = Paint().apply { color = skyTop; isAntiAlias = true }
                    c.drawCircle(mx + 6f, my - 2f, 11f, shadowPaint)
                } else if (isDusk || isDawn) {
                    val sunGlow = Paint().apply { color = Color.argb(60, 255, 180, 80); isAntiAlias = true }
                    val sunPaint = Paint().apply { color = if (isDusk) Color.rgb(255, 160, 60) else Color.rgb(255, 190, 110); isAntiAlias = true }
                    val sx = if (isDusk) 265f else 95f; val sy = 100f
                    c.drawCircle(sx, sy, 34f, sunGlow)
                    c.drawCircle(sx, sy, 18f, sunPaint)
                } else {
                    val sunGlow = Paint().apply { color = Color.argb(55, 255, 235, 140); isAntiAlias = true }
                    val sunPaint = Paint().apply { color = Color.rgb(255, 248, 180); isAntiAlias = true }
                    val sx = 260f; val sy = 46f
                    c.drawCircle(sx, sy, 32f, sunGlow)
                    c.drawCircle(sx, sy, 16f, sunPaint)
                }
            }

            // 3. Clouds (for partly, overcast, or nice day skies)
            if (weatherKind in listOf("partly", "overcast", "drizzle", "mist", "fog")) {
                val cloudPaint = Paint().apply {
                    color = if (isNight) Color.argb(70, 30, 38, 65) else if (isDusk) Color.argb(120, 190, 100, 120) else Color.argb(140, 240, 245, 255)
                    isAntiAlias = true
                }
                c.drawCircle(80f, 65f, 24f, cloudPaint)
                c.drawCircle(105f, 60f, 30f, cloudPaint)
                c.drawCircle(135f, 68f, 22f, cloudPaint)
                c.drawCircle(220f, 75f, 20f, cloudPaint)
                c.drawCircle(245f, 70f, 26f, cloudPaint)
                c.drawCircle(270f, 76f, 18f, cloudPaint)
            }

            // 4. Outside Environments
            when (outsideKey) {
                "city", "skyscraper" -> {
                    val isSky = outsideKey == "skyscraper"
                    val bDark = if (isNight) Color.rgb(14, 18, 38) else Color.rgb(90, 110, 138)
                    val bMid = if (isNight) Color.rgb(20, 26, 52) else Color.rgb(115, 135, 165)
                    val bLight = if (isNight) Color.rgb(28, 36, 70) else Color.rgb(140, 160, 190)

                    // Distant layer
                    c.drawRect(25f, 100f, 75f, 220f, Paint().apply { color = bDark })
                    c.drawRect(100f, 85f, 155f, 220f, Paint().apply { color = bDark })
                    c.drawRect(180f, 110f, 240f, 220f, Paint().apply { color = bDark })
                    c.drawRect(260f, 90f, 320f, 220f, Paint().apply { color = bDark })

                    // Mid layer buildings
                    c.drawRect(10f, 125f, 80f, 220f, Paint().apply { color = bMid })
                    c.drawRect(75f, 70f, 150f, 220f, Paint().apply { color = bLight })
                    c.drawRect(145f, 95f, 215f, 220f, Paint().apply { color = bMid })
                    c.drawRect(210f, 60f, 285f, 220f, Paint().apply { color = bLight })
                    c.drawRect(280f, 115f, 350f, 220f, Paint().apply { color = bMid })

                    // Antennas & red blinkers
                    val antPaint = Paint().apply { color = Color.rgb(60, 70, 90); strokeWidth = 2f }
                    c.drawLine(112f, 70f, 112f, 40f, antPaint)
                    c.drawCircle(112f, 40f, 2.5f, Paint().apply { color = Color.rgb(255, 60, 60); isAntiAlias = true })
                    c.drawLine(247f, 60f, 247f, 32f, antPaint)
                    c.drawCircle(247f, 32f, 2.5f, Paint().apply { color = Color.rgb(255, 60, 60); isAntiAlias = true })

                    // Water tower on building 3
                    val tankPaint = Paint().apply { color = if (isNight) Color.rgb(40, 30, 25) else Color.rgb(120, 95, 75) }
                    c.drawRect(170f, 80f, 192f, 95f, tankPaint)
                    c.drawLine(173f, 95f, 173f, 99f, antPaint)
                    c.drawLine(189f, 95f, 189f, 99f, antPaint)

                    // Lit windows
                    val winLitAmber = Paint().apply { color = Color.rgb(255, 225, 110) }
                    val winLitCyan = Paint().apply { color = Color.rgb(120, 225, 245) }
                    val winDark = Paint().apply { color = if (isNight) Color.rgb(10, 14, 28) else Color.rgb(75, 95, 120) }

                    fun drawWindows(bx: Float, by: Float, bw: Float, bh: Float, seed: Int) {
                        val cols = ((bw - 10f) / 10f).toInt().coerceAtLeast(1)
                        val rows = ((bh - 15f) / 12f).toInt().coerceAtLeast(1)
                        for (r in 0 until rows) {
                            for (col in 0 until cols) {
                                val wx = bx + 6f + col * 10f
                                val wy = by + 8f + r * 12f
                                val on = ((r * 7 + col * 13 + seed) % 4 != 0)
                                val p = if (isNight) {
                                    if (on) (if ((r + col) % 3 == 0) winLitCyan else winLitAmber) else winDark
                                } else {
                                    winDark
                                }
                                c.drawRect(wx, wy, wx + 5f, wy + 7f, p)
                            }
                        }
                    }
                    drawWindows(75f, 70f, 75f, 150f, 3)
                    drawWindows(145f, 95f, 70f, 125f, 7)
                    drawWindows(210f, 60f, 75f, 160f, 11)
                    drawWindows(280f, 115f, 70f, 105f, 17)

                    if (isSky) {
                        // High altitude cloud shelf
                        val mistP = Paint().apply { color = Color.argb(110, 200, 215, 235) }
                        c.drawRect(0f, 175f, 360f, 195f, mistP)
                    }
                }
                "neon" -> {
                    val bDark = Color.rgb(10, 12, 24)
                    c.drawRect(15f, 80f, 85f, 220f, Paint().apply { color = bDark })
                    c.drawRect(80f, 50f, 160f, 220f, Paint().apply { color = Color.rgb(16, 18, 34) })
                    c.drawRect(155f, 90f, 230f, 220f, Paint().apply { color = bDark })
                    c.drawRect(225f, 40f, 305f, 220f, Paint().apply { color = Color.rgb(18, 20, 38) })
                    c.drawRect(300f, 100f, 355f, 220f, Paint().apply { color = bDark })

                    // Vibrant neon signs
                    val pMag = Paint().apply { color = Color.rgb(255, 55, 175); strokeWidth = 2.5f }
                    val pCyan = Paint().apply { color = Color.rgb(35, 235, 255); strokeWidth = 2.5f }
                    val pGold = Paint().apply { color = Color.rgb(255, 220, 45); strokeWidth = 2.5f }

                    c.drawLine(90f, 65f, 90f, 155f, pMag)
                    c.drawLine(93f, 65f, 93f, 155f, pMag)
                    c.drawRect(110f, 80f, 145f, 120f, Paint().apply { color = Color.argb(80, 255, 55, 175) })
                    c.drawRect(110f, 80f, 145f, 120f, Paint().apply { color = Color.rgb(255, 55, 175); style = Paint.Style.STROKE; strokeWidth = 2f })

                    c.drawLine(240f, 55f, 290f, 55f, pCyan)
                    c.drawLine(240f, 75f, 290f, 75f, pGold)
                    c.drawLine(290f, 55f, 290f, 130f, pCyan)
                    c.drawRect(250f, 90f, 280f, 145f, Paint().apply { color = Color.argb(90, 35, 235, 255) })
                    c.drawRect(250f, 90f, 280f, 145f, Paint().apply { color = Color.rgb(35, 235, 255); style = Paint.Style.STROKE; strokeWidth = 2f })
                }
                "suburbs", "neighborhood" -> {
                    val lawnCol = if (isWinter) Color.rgb(220, 230, 242) else if (isNight) Color.rgb(22, 42, 30) else Color.rgb(75, 135, 65)
                    c.drawRect(0f, 160f, 360f, 220f, Paint().apply { color = lawnCol })

                    val houseCol = if (isNight) Color.rgb(32, 38, 58) else Color.rgb(185, 170, 155)
                    val roofCol = if (isWinter) Color.rgb(240, 245, 255) else if (isNight) Color.rgb(20, 24, 38) else Color.rgb(120, 65, 50)
                    val winLit = Paint().apply { color = if (isNight) Color.rgb(255, 220, 110) else Color.rgb(90, 120, 145) }

                    // House 1 (left)
                    c.drawRect(35f, 140f, 115f, 185f, Paint().apply { color = houseCol })
                    val rPath1 = Path().apply { moveTo(25f, 140f); lineTo(75f, 105f); lineTo(125f, 140f); close() }
                    c.drawPath(rPath1, Paint().apply { color = roofCol })
                    c.drawRect(50f, 148f, 65f, 165f, winLit)
                    c.drawRect(85f, 148f, 100f, 165f, winLit)

                    // House 2 (right)
                    c.drawRect(210f, 130f, 300f, 185f, Paint().apply { color = houseCol })
                    val rPath2 = Path().apply { moveTo(200f, 130f); lineTo(255f, 98f); lineTo(310f, 130f); close() }
                    c.drawPath(rPath2, Paint().apply { color = roofCol })
                    c.drawRect(225f, 140f, 242f, 158f, winLit)
                    c.drawRect(268f, 140f, 285f, 158f, winLit)

                    // Suburban tree
                    val trunkCol = if (isNight) Color.rgb(25, 20, 18) else Color.rgb(85, 60, 45)
                    c.drawRect(155f, 140f, 165f, 180f, Paint().apply { color = trunkCol })
                    val leavesCol = if (isWinter) Color.rgb(200, 215, 230) else if (isNight) Color.rgb(18, 48, 28) else Color.rgb(55, 125, 50)
                    c.drawCircle(160f, 125f, 26f, Paint().apply { color = leavesCol; isAntiAlias = true })
                    c.drawCircle(145f, 132f, 18f, Paint().apply { color = leavesCol; isAntiAlias = true })
                    c.drawCircle(175f, 132f, 18f, Paint().apply { color = leavesCol; isAntiAlias = true })

                    // Streetlamp
                    val lampPole = Paint().apply { color = Color.rgb(65, 75, 85); strokeWidth = 2.5f }
                    c.drawLine(185f, 180f, 185f, 135f, lampPole)
                    c.drawLine(185f, 135f, 195f, 132f, lampPole)
                    c.drawCircle(195f, 134f, 4f, Paint().apply { color = Color.rgb(255, 240, 160); isAntiAlias = true })
                    if (isNight) {
                        val conePaint = Paint().apply {
                            shader = LinearGradient(195f, 134f, 195f, 190f, Color.argb(90, 255, 240, 160), Color.argb(0, 255, 240, 160), Shader.TileMode.CLAMP)
                        }
                        val conePath = Path().apply { moveTo(195f, 134f); lineTo(170f, 190f); lineTo(220f, 190f); close() }
                        c.drawPath(conePath, conePaint)
                    }
                }
                "house" -> {
                    val lawnCol = if (isWinter) Color.rgb(215, 228, 240) else if (isNight) Color.rgb(20, 38, 26) else Color.rgb(68, 132, 58)
                    c.drawRect(0f, 140f, 360f, 220f, Paint().apply { color = lawnCol })

                    // Wooden fence
                    val fenceCol = if (isNight) Color.rgb(38, 34, 46) else Color.rgb(155, 125, 95)
                    c.drawRect(0f, 135f, 360f, 160f, Paint().apply { color = fenceCol })
                    val plankP = Paint().apply { color = if (isNight) Color.rgb(28, 24, 35) else Color.rgb(125, 98, 72); strokeWidth = 1.5f }
                    for (fx in 0..360 step 18) c.drawLine(fx.toFloat(), 135f, fx.toFloat(), 160f, plankP)

                    // Neatly aligned Blue Swing Set
                    val bluePaint = Paint().apply { color = Color.rgb(60, 130, 215); strokeWidth = 3f; isAntiAlias = true }
                    // Left A-frame
                    c.drawLine(70f, 195f, 85f, 138f, bluePaint)
                    c.drawLine(100f, 195f, 85f, 138f, bluePaint)
                    // Right A-frame
                    c.drawLine(150f, 195f, 165f, 138f, bluePaint)
                    c.drawLine(180f, 195f, 165f, 138f, bluePaint)
                    // Top crossbar
                    c.drawLine(78f, 138f, 172f, 138f, bluePaint)
                    // Swing chains & seat
                    val chainP = Paint().apply { color = Color.rgb(180, 185, 195); strokeWidth = 1.2f }
                    c.drawLine(110f, 138f, 110f, 172f, chainP)
                    c.drawLine(124f, 138f, 124f, 172f, chainP)
                    c.drawRect(107f, 172f, 127f, 176f, Paint().apply { color = Color.rgb(215, 60, 50) })

                    c.drawLine(135f, 138f, 135f, 172f, chainP)
                    c.drawLine(149f, 138f, 149f, 172f, chainP)
                    c.drawRect(132f, 172f, 152f, 176f, Paint().apply { color = Color.rgb(215, 60, 50) })

                    // Tree branch from upper right
                    val branchP = Paint().apply { color = if (isNight) Color.rgb(28, 22, 20) else Color.rgb(85, 62, 45); strokeWidth = 5f; isAntiAlias = true }
                    c.drawLine(360f, 35f, 260f, 65f, branchP)
                    c.drawLine(280f, 60f, 240f, 85f, branchP)
                    val leafCol = if (isNight) Color.rgb(22, 48, 28) else Color.rgb(55, 135, 50)
                    val leafP = Paint().apply { color = leafCol; isAntiAlias = true }
                    c.drawCircle(265f, 62f, 14f, leafP)
                    c.drawCircle(242f, 82f, 16f, leafP)
                    c.drawCircle(228f, 88f, 12f, leafP)
                }
                "trailer" -> {
                    val mountCol = if (isNight) Color.rgb(25, 22, 40) else Color.rgb(155, 105, 95)
                    val mPath = Path().apply { moveTo(0f, 145f); lineTo(90f, 115f); lineTo(180f, 135f); lineTo(270f, 108f); lineTo(360f, 145f); lineTo(360f, 220f); lineTo(0f, 220f); close() }
                    c.drawPath(mPath, Paint().apply { color = mountCol })

                    val gravelCol = if (isNight) Color.rgb(35, 32, 42) else Color.rgb(165, 155, 140)
                    c.drawRect(0f, 150f, 360f, 220f, Paint().apply { color = gravelCol })

                    // Trailer home
                    val trCol = if (isNight) Color.rgb(42, 48, 65) else Color.rgb(215, 218, 222)
                    c.drawRoundRect(RectF(60f, 135f, 240f, 185f), 6f, 6f, Paint().apply { color = trCol })
                    c.drawRect(60f, 155f, 240f, 160f, Paint().apply { color = Color.rgb(40, 140, 165) })
                    val winP = Paint().apply { color = if (isNight) Color.rgb(255, 215, 105) else Color.rgb(85, 110, 130) }
                    c.drawRect(80f, 144f, 105f, 158f, winP)
                    c.drawRect(120f, 144f, 145f, 158f, winP)
                    c.drawRect(175f, 140f, 195f, 185f, Paint().apply { color = if (isNight) Color.rgb(28, 32, 45) else Color.rgb(160, 165, 170) })
                    c.drawLine(55f, 138f, 150f, 135f, Paint().apply { color = Color.rgb(210, 75, 60); strokeWidth = 3f })

                    // Utility pole & wires
                    val poleP = Paint().apply { color = Color.rgb(75, 60, 50); strokeWidth = 2.5f }
                    c.drawLine(290f, 185f, 290f, 85f, poleP)
                    c.drawLine(278f, 95f, 302f, 95f, poleP)
                    val wireP = Paint().apply { color = Color.argb(140, 40, 40, 45); strokeWidth = 1f }
                    c.drawLine(0f, 98f, 278f, 95f, wireP)
                    c.drawLine(302f, 95f, 360f, 98f, wireP)
                }
                "arizona", "desert" -> {
                    val mesaCol = if (isNight) Color.rgb(38, 25, 35) else Color.rgb(180, 78, 52)
                    val mesaPath = Path().apply {
                        moveTo(0f, 155f); lineTo(30f, 115f); lineTo(120f, 115f); lineTo(145f, 155f)
                        lineTo(185f, 155f); lineTo(205f, 105f); lineTo(295f, 105f); lineTo(325f, 155f); lineTo(360f, 155f); lineTo(360f, 220f); lineTo(0f, 220f); close()
                    }
                    c.drawPath(mesaPath, Paint().apply { color = mesaCol })

                    val sandCol = if (isNight) Color.rgb(45, 34, 40) else Color.rgb(225, 155, 95)
                    c.drawRect(0f, 152f, 360f, 220f, Paint().apply { color = sandCol })

                    val cactCol = if (isNight) Color.rgb(20, 35, 25) else Color.rgb(55, 105, 52)
                    val cactP = Paint().apply { color = cactCol; strokeWidth = 4.5f; isAntiAlias = true; strokeCap = Paint.Cap.ROUND }
                    c.drawLine(80f, 185f, 80f, 115f, cactP)
                    c.drawLine(80f, 145f, 68f, 145f, cactP)
                    c.drawLine(68f, 145f, 68f, 128f, cactP)
                    c.drawLine(80f, 152f, 94f, 152f, cactP)
                    c.drawLine(94f, 152f, 94f, 134f, cactP)

                    c.drawLine(275f, 180f, 275f, 135f, cactP)
                    c.drawLine(275f, 155f, 266f, 155f, cactP)
                    c.drawLine(266f, 155f, 266f, 142f, cactP)
                }
                "beach", "island" -> {
                    val seaTop = if (isNight) Color.rgb(12, 22, 45) else Color.rgb(35, 95, 175)
                    val seaBot = if (isNight) Color.rgb(18, 34, 65) else Color.rgb(55, 140, 205)
                    val seaPaint = Paint().apply { shader = LinearGradient(0f, 115f, 0f, 175f, seaTop, seaBot, Shader.TileMode.CLAMP) }
                    c.drawRect(0f, 115f, 360f, 175f, seaPaint)

                    val waveP = Paint().apply { color = Color.argb(160, 255, 255, 255); strokeWidth = 1.5f }
                    c.drawLine(20f, 135f, 75f, 135f, waveP)
                    c.drawLine(140f, 148f, 210f, 148f, waveP)
                    c.drawLine(60f, 162f, 150f, 162f, waveP)
                    c.drawLine(230f, 165f, 310f, 165f, waveP)

                    val sandCol = if (isNight) Color.rgb(42, 38, 48) else Color.rgb(235, 205, 145)
                    c.drawRect(0f, 172f, 360f, 220f, Paint().apply { color = sandCol })

                    if (outsideKey == "beach") {
                        val pierCol = if (isNight) Color.rgb(20, 24, 38) else Color.rgb(85, 95, 115)
                        c.drawCircle(80f, 95f, 26f, Paint().apply { color = pierCol; style = Paint.Style.STROKE; strokeWidth = 2f })
                        c.drawLine(80f, 95f, 65f, 125f, Paint().apply { color = pierCol; strokeWidth = 2f })
                        c.drawLine(80f, 95f, 95f, 125f, Paint().apply { color = pierCol; strokeWidth = 2f })
                    }

                    val trunkP = Paint().apply { color = if (isNight) Color.rgb(28, 24, 25) else Color.rgb(115, 80, 55); strokeWidth = 5.5f; isAntiAlias = true }
                    val palmTrunk = Path().apply { moveTo(310f, 195f); quadTo(325f, 140f, 285f, 95f) }
                    c.drawPath(palmTrunk, Paint(trunkP).apply { style = Paint.Style.STROKE })

                    val frondP = Paint().apply { color = if (isNight) Color.rgb(20, 45, 28) else Color.rgb(48, 135, 45); strokeWidth = 3f; isAntiAlias = true; style = Paint.Style.STROKE }
                    c.drawPath(Path().apply { moveTo(285f, 95f); quadTo(250f, 85f, 235f, 105f) }, frondP)
                    c.drawPath(Path().apply { moveTo(285f, 95f); quadTo(270f, 70f, 255f, 65f) }, frondP)
                    c.drawPath(Path().apply { moveTo(285f, 95f); quadTo(305f, 70f, 320f, 75f) }, frondP)
                    c.drawPath(Path().apply { moveTo(285f, 95f); quadTo(325f, 90f, 335f, 110f) }, frondP)
                }
                "lake" -> {
                    val mPath = Path().apply { moveTo(0f, 135f); lineTo(70f, 95f); lineTo(155f, 125f); lineTo(240f, 90f); lineTo(320f, 120f); lineTo(360f, 110f); lineTo(360f, 220f); lineTo(0f, 220f); close() }
                    c.drawPath(mPath, Paint().apply { color = if (isNight) Color.rgb(18, 24, 40) else Color.rgb(85, 110, 125) })

                    val lakeTop = if (isNight) Color.rgb(14, 20, 38) else Color.rgb(55, 115, 155)
                    val lakeBot = if (isNight) Color.rgb(20, 30, 52) else Color.rgb(90, 155, 195)
                    c.drawRect(0f, 125f, 360f, 220f, Paint().apply { shader = LinearGradient(0f, 125f, 0f, 220f, lakeTop, lakeBot, Shader.TileMode.CLAMP) })

                    val dockP = Paint().apply { color = if (isNight) Color.rgb(32, 28, 35) else Color.rgb(135, 100, 75) }
                    c.drawRect(80f, 160f, 140f, 220f, dockP)
                    val postP = Paint().apply { color = if (isNight) Color.rgb(22, 18, 25) else Color.rgb(95, 70, 52); strokeWidth = 3f }
                    c.drawLine(82f, 160f, 82f, 175f, postP)
                    c.drawLine(138f, 160f, 138f, 175f, postP)
                }
                "forest" -> {
                    fun drawPines(baseY: Float, col: Int, count: Int, height: Float, step: Float) {
                        val p = Paint().apply { color = col }
                        for (i in 0 until count) {
                            val px = i * step + (i % 3) * 6f
                            val pth = Path().apply { moveTo(px, baseY); lineTo(px + step * 0.5f, baseY - height); lineTo(px + step, baseY); close() }
                            c.drawPath(pth, p)
                        }
                    }
                    val colBack = if (isNight) Color.rgb(14, 22, 32) else Color.rgb(45, 80, 85)
                    val colMid = if (isNight) Color.rgb(18, 30, 36) else Color.rgb(38, 95, 65)
                    val colFront = if (isNight) Color.rgb(22, 40, 38) else Color.rgb(28, 115, 55)

                    drawPines(140f, colBack, 15, 38f, 26f)
                    drawPines(165f, colMid, 12, 52f, 32f)
                    drawPines(195f, colFront, 10, 68f, 40f)

                    c.drawRect(0f, 185f, 360f, 220f, Paint().apply { color = if (isNight) Color.rgb(16, 28, 25) else Color.rgb(32, 75, 42) })
                }
                "mountains" -> {
                    val mBack = Path().apply { moveTo(0f, 140f); lineTo(85f, 50f); lineTo(175f, 120f); lineTo(270f, 42f); lineTo(360f, 130f); lineTo(360f, 220f); lineTo(0f, 220f); close() }
                    c.drawPath(mBack, Paint().apply { color = if (isNight) Color.rgb(18, 22, 40) else Color.rgb(95, 110, 135) })

                    val snowP = Paint().apply { color = Color.rgb(240, 246, 255) }
                    val cap1 = Path().apply { moveTo(85f, 50f); lineTo(65f, 75f); lineTo(80f, 70f); lineTo(92f, 78f); lineTo(105f, 72f); close() }
                    c.drawPath(cap1, snowP)
                    val cap2 = Path().apply { moveTo(270f, 42f); lineTo(248f, 70f); lineTo(262f, 65f); lineTo(278f, 72f); lineTo(292f, 66f); close() }
                    c.drawPath(cap2, snowP)

                    c.drawRect(0f, 160f, 360f, 220f, Paint().apply { color = if (isNight) Color.rgb(15, 25, 28) else Color.rgb(42, 85, 60) })
                }
                "countryside" -> {
                    val hillCol = if (isWinter) Color.rgb(225, 235, 245) else if (isNight) Color.rgb(20, 38, 26) else Color.rgb(72, 138, 62)
                    c.drawOval(RectF(-60f, 130f, 240f, 240f), Paint().apply { color = hillCol })
                    c.drawOval(RectF(140f, 140f, 440f, 240f), Paint().apply { color = hillCol })

                    // Barn
                    val barnCol = if (isNight) Color.rgb(48, 22, 25) else Color.rgb(168, 48, 42)
                    c.drawRect(70f, 135f, 135f, 175f, Paint().apply { color = barnCol })
                    val barnRoof = Path().apply { moveTo(62f, 135f); lineTo(102f, 108f); lineTo(143f, 135f); close() }
                    c.drawPath(barnRoof, Paint().apply { color = if (isWinter) Color.rgb(245, 248, 255) else Color.rgb(215, 218, 222) })
                    c.drawRect(92f, 150f, 112f, 175f, Paint().apply { color = Color.rgb(240, 240, 245) })

                    // Silo
                    c.drawRoundRect(RectF(140f, 115f, 162f, 175f), 5f, 5f, Paint().apply { color = Color.rgb(180, 188, 195) })

                    // Split-rail fence
                    val fenceP = Paint().apply { color = if (isNight) Color.rgb(32, 28, 26) else Color.rgb(120, 92, 70); strokeWidth = 2f }
                    c.drawLine(190f, 165f, 330f, 165f, fenceP)
                    c.drawLine(190f, 175f, 330f, 175f, fenceP)
                    for (postX in 190..330 step 35) c.drawLine(postX.toFloat(), 158f, postX.toFloat(), 182f, fenceP)
                }
                else -> {
                    val bMid = if (isNight) Color.rgb(20, 26, 52) else Color.rgb(115, 135, 165)
                    c.drawRect(40f, 100f, 110f, 220f, Paint().apply { color = bMid })
                    c.drawRect(130f, 80f, 210f, 220f, Paint().apply { color = bMid })
                    c.drawRect(230f, 95f, 310f, 220f, Paint().apply { color = bMid })
                }
            }

            // 5. Weather Precipitation Overlay
            if (weatherKind == "storm") {
                val lightP = Paint().apply { color = Color.rgb(240, 225, 255); strokeWidth = 2.4f; isAntiAlias = true }
                val pth = Path().apply { moveTo(190f, 15f); lineTo(175f, 60f); lineTo(185f, 65f); lineTo(165f, 120f) }
                c.drawPath(pth, lightP)
            }
            if (weatherKind in listOf("rain", "downpour", "storm", "sunshower")) {
                val rainP = Paint().apply { color = Color.argb(130, 200, 225, 255); strokeWidth = 1.6f }
                for (i in 0 until 35) {
                    val rx = (i * 29) % 360f; val ry = (i * 37) % 200f
                    c.drawLine(rx, ry, rx - 6f, ry + 16f, rainP)
                }
            } else if (weatherKind in listOf("snow", "blizzard", "flurries")) {
                val snowP = Paint().apply { color = Color.argb(200, 245, 250, 255) }
                for (i in 0 until 45) {
                    val sx = (i * 31) % 360f; val sy = (i * 41) % 210f
                    c.drawCircle(sx, sy, if (i % 4 == 0) 2.2f else 1.3f, snowP)
                }
            } else if (weatherKind in listOf("fog", "mist")) {
                val fogP = Paint().apply { color = Color.argb(70, 210, 225, 240) }
                c.drawRect(0f, 120f, 360f, 185f, fogP)
            }

            // 6. Foreground Window Sill
            val sillCol = if (isNight) Color.rgb(32, 36, 52) else Color.rgb(85, 72, 64)
            c.drawRect(0f, 216f, 360f, 240f, Paint().apply { color = sillCol })
            c.drawLine(0f, 216f, 360f, 216f, Paint().apply { color = if (isNight) Color.rgb(55, 62, 85) else Color.rgb(120, 105, 95); strokeWidth = 2f })

            return bmp
        }

        fun generateAndSaveOutdoorSnapshot(ctx: Context, s: JSONObject, outsideKey: String, weatherKind: String) {
            try {
                val bmp = generateOutdoorSnapshot(ctx, s, outsideKey, weatherKind)
                saveShots(ctx, bmp, bmp)
                ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putLong("shotAt", System.currentTimeMillis())
                    .putString("shotPlace", outsideKey)
                    .apply()
            } catch (e: Exception) {
                android.util.Log.e("PS1Bedroom", "generateAndSaveOutdoorSnapshot failed", e)
            }
        }

        private fun fallbackWindow(ctx: Context, s: org.json.JSONObject): Bitmap =
            generateOutdoorSnapshot(ctx, s, s.optString("outside", "city"), s.optString("weatherKind", "clear"))
    }

    override fun onEnabled(ctx: Context) {
        super.onEnabled(ctx)
        scheduleNextRefresh(ctx)
        RoomWidgetReceiver.refreshWindowWidgetBackground(ctx, forceSnapshot = true)
    }

    override fun onDisabled(ctx: Context) {
        super.onDisabled(ctx)
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
            val pi = PendingIntent.getBroadcast(
                ctx, 888,
                Intent(ctx, RoomWidgetReceiver::class.java).setAction(RoomWidgetReceiver.ACTION_WIDGET_ALARM),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (am != null && pi != null) {
                am.cancel(pi)
                pi.cancel()
            }
        } catch (_: Exception) { }
    }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        scheduleNextRefresh(ctx)
        for (id in ids) {
            try { mgr.updateAppWidget(id, views(ctx, id)) } catch (_: Exception) { }
        }
        RoomWidgetReceiver.refreshWindowWidgetBackground(ctx, forceSnapshot = false)
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle?) {
        try { mgr.updateAppWidget(id, views(ctx, id)) } catch (_: Exception) { }
    }
}

/** The widget's options screen: shown when you add one, and from the widget's own settings later (reconfigurable). */
class WindowWidgetConfig : Activity() {
    private var id = AppWidgetManager.INVALID_APPWIDGET_ID
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        id = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val o = WindowWidget.options(this, id)
        fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()
        val pink = Color.rgb(0xff, 0x77, 0xee)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20f), dp(24f), dp(20f), dp(20f)); setBackgroundColor(Color.rgb(0x0b, 0x0a, 0x1a)) }
        col.addView(TextView(this).apply { text = "🪟 WINDOW WIDGET"; setTextColor(pink); setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f); typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) })
        col.addView(TextView(this).apply {
            text = "The real view out of your bedroom window. It refreshes every few minutes while PS1 Bedroom is open, and the clock always keeps time."
            setTextColor(Color.rgb(0xb9, 0xb4, 0xe6)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); setPadding(0, dp(6f), 0, dp(12f))
        })
        fun check(label: String, on: Boolean) = CheckBox(this).apply {
            text = label; isChecked = on; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            buttonTintList = android.content.res.ColorStateList.valueOf(pink); setPadding(dp(6f), dp(8f), 0, dp(8f))
        }.also { col.addView(it) }
        val cTime = check("Show the clock", o.time)
        val cDate = check("Show the date", o.date)
        val cWeather = check("Show the weather and temperature", o.weather)
        val cPlace = check("Show where the room is", o.place)
        val cFrame = check("Draw the window frame", o.frame)
        val cBlinds = check("Show the blinds as they are in the room", o.blinds)
        val cRefreshBtn = check("Show the manual refresh (↻) button", o.refreshBtn)
        var pickedMin = o.refreshMin
        col.addView(TextView(this).apply {
            text = "SNAPSHOT REFRESH INTERVAL"; setTextColor(pink); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); setPadding(0, dp(12f), 0, dp(6f))
        })
        val minRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val minBtns = ArrayList<Pair<Int, TextView>>()
        fun styleMinBtns() {
            for ((m, b) in minBtns) {
                val on = m == pickedMin
                b.setTextColor(if (on) Color.WHITE else Color.rgb(0xb9, 0xb4, 0xe6))
                b.background = GradientDrawable().apply {
                    setColor(if (on) Color.rgb(0x3b, 0x1d, 0x4f) else Color.rgb(0x16, 0x14, 0x2c))
                    cornerRadius = dp(8f).toFloat()
                    setStroke(dp(if (on) 2f else 1f), if (on) pink else Color.rgb(0x32, 0x2d, 0x5a))
                }
            }
        }
        for (m in listOf(1, 5, 10, 15, 30, 60)) {
            val b = TextView(this).apply {
                text = if (m == 60) "1h" else "${m}m"
                gravity = Gravity.CENTER; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
                setOnClickListener { pickedMin = m; styleMinBtns() }
            }
            minBtns += m to b
            minRow.addView(b, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4f) })
        }
        styleMinBtns()
        col.addView(minRow)
        col.addView(TextView(this).apply {
            text = "SAVE WIDGET"; gravity = Gravity.CENTER; setTextColor(Color.WHITE); typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setPadding(0, dp(14f), 0, dp(14f))
            background = GradientDrawable().apply { setColor(Color.rgb(0x3b, 0x1d, 0x4f)); cornerRadius = dp(12f).toFloat(); setStroke(dp(2f), pink) }
            setOnClickListener {
                WindowWidget.saveOptions(this@WindowWidgetConfig, id, WindowWidget.Options(cTime.isChecked, cDate.isChecked, cWeather.isChecked, cPlace.isChecked, cFrame.isChecked, cBlinds.isChecked, cRefreshBtn.isChecked, pickedMin))
                WindowWidget.updateAll(this@WindowWidgetConfig)
                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                finish()
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18f) })
        setContentView(ScrollView(this).apply { addView(col); setBackgroundColor(Color.rgb(0x0b, 0x0a, 0x1a)) })
    }
}
