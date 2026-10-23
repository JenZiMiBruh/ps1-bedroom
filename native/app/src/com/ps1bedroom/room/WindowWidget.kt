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
import android.graphics.Paint
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
import java.io.File
import java.io.FileOutputStream

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
                p.getBoolean("place$id", true), p.getBoolean("frame$id", true), p.getBoolean("blinds$id", false),
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
        fun hasWidgets(ctx: Context) = AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, WindowWidget::class.java)).isNotEmpty()
        /** New snapshots from the room (any thread): saved, then every window widget redrawn. */
        fun saveShots(ctx: Context, clear: Bitmap, withBlinds: Bitmap) {
            for ((b, f) in listOf(clear to shotFile(ctx, false), withBlinds to shotFile(ctx, true)))
                try { FileOutputStream(f).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } } catch (e: Exception) { }
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("shotAt", System.currentTimeMillis()).apply()
            updateAll(ctx)
        }
        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            for (id in mgr.getAppWidgetIds(ComponentName(ctx, WindowWidget::class.java))) mgr.updateAppWidget(id, views(ctx, id))
        }
        private fun views(ctx: Context, id: Int): android.widget.RemoteViews {
            val o = options(ctx, id)
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val v = android.widget.RemoteViews(ctx.packageName, R.layout.widget_window)
            val shot = shotFile(ctx, o.blinds).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
            if (shot != null) v.setImageViewBitmap(R.id.ww_view, if (o.frame) framed(shot) else shot)
            else v.setImageViewResource(R.id.ww_view, R.mipmap.ic_launcher)
            v.setViewVisibility(R.id.ww_time, if (o.time) View.VISIBLE else View.GONE)
            v.setViewVisibility(R.id.ww_date, if (o.date) View.VISIBLE else View.GONE)
            val weather = p.getString("infoWeather", null); val place = p.getString("infoPlace", null)
            val shotAt = p.getLong("shotAt", 0L)
            val ago = if (shotAt > 0) ((System.currentTimeMillis() - shotAt) / 60000L).let { m -> if (m < 1) "now" else if (m < 60) "${m}m ago" else "${m / 60}h ago" } else null
            val line = listOfNotNull(weather.takeIf { o.weather }, place.takeIf { o.place }, ago?.let { "📷 $it" }).joinToString("  ·  ")
            v.setTextViewText(R.id.ww_weather, if (weather == null) "Open PS1 Bedroom to look outside" else line)
            v.setViewVisibility(R.id.ww_weather, if (weather == null || line.isNotEmpty()) View.VISIBLE else View.GONE)
            v.setViewVisibility(R.id.ww_info, if (o.time || o.date || line.isNotEmpty() || weather == null) View.VISIBLE else View.GONE)
            v.setViewVisibility(R.id.ww_refresh, if (o.refreshBtn) View.VISIBLE else View.GONE)
            val open = PendingIntent.getActivity(ctx, 200, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
            val refreshPi = PendingIntent.getBroadcast(ctx, 201, Intent(ctx, RoomWidgetReceiver::class.java).setAction(RoomWidgetReceiver.ACTION_WIDGET_CMD).putExtra("cmd", "refresh_window"),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            v.setOnClickPendingIntent(R.id.ww_root, open)
            v.setOnClickPendingIntent(R.id.ww_refresh, refreshPi)
            // Room controls right on the window (work whether the room is open or not)
            val raw = ctx.getSharedPreferences("ps1bedroom", Context.MODE_PRIVATE).getString("settings.v1", null)
            val s = if (raw != null) try { org.json.JSONObject(raw) } catch (_: Exception) { org.json.JSONObject() } else org.json.JSONObject()
            val on = Color.WHITE; val off = Color.argb(150, 255, 255, 255)
            v.setTextColor(R.id.ww_light_btn, if (s.optBoolean("fanLight", false)) on else off)
            v.setTextViewText(R.id.ww_sound_btn, if (s.optBoolean("audioOn", true)) "🔊" else "🔈")
            v.setTextColor(R.id.ww_tv_btn, if (s.optBoolean("tv", true)) on else off)
            v.setOnClickPendingIntent(R.id.ww_light_btn, RoomWidgetReceiver.cmdPendingIntent(ctx, "toggle_light", 202))
            v.setOnClickPendingIntent(R.id.ww_tv_btn, RoomWidgetReceiver.cmdPendingIntent(ctx, "toggle_tv", 203))
            v.setOnClickPendingIntent(R.id.ww_sound_btn, RoomWidgetReceiver.cmdPendingIntent(ctx, "toggle_sound", 204))
            v.setOnClickPendingIntent(R.id.ww_weather_btn, RoomWidgetReceiver.cmdPendingIntent(ctx, "step_weather", 205))
            v.setOnClickPendingIntent(R.id.ww_blinds_btn, RoomWidgetReceiver.cmdPendingIntent(ctx, "cycle_blinds", 206))
            v.setOnClickPendingIntent(R.id.ww_place_btn, RoomWidgetReceiver.cmdPendingIntent(ctx, "step_place", 207))
            return v
        }
        /** The PS1 window frame: a painted wooden frame with a cross bar, like the bedroom's own window. */
        private fun framed(src: Bitmap): Bitmap {
            val out = src.copy(Bitmap.Config.ARGB_8888, true)
            val c = Canvas(out); val w = out.width.toFloat(); val h = out.height.toFloat()
            val t = w * 0.035f
            val wood = Paint().apply { color = Color.rgb(0xe8, 0xe4, 0xda) }
            val shade = Paint().apply { color = Color.argb(90, 0, 0, 0) }
            c.drawRect(0f, 0f, w, t, wood); c.drawRect(0f, h - t, w, h, wood); c.drawRect(0f, 0f, t, h, wood); c.drawRect(w - t, 0f, w, h, wood)
            c.drawRect(w / 2 - t * 0.4f, 0f, w / 2 + t * 0.4f, h, wood)          // the middle bar
            c.drawRect(t, t, w - t, t + t * 0.35f, shade); c.drawRect(t, t, t + t * 0.35f, h - t, shade)   // inner shadow
            return out
        }
    }
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) { for (id in ids) mgr.updateAppWidget(id, views(ctx, id)) }
    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle?) { mgr.updateAppWidget(id, views(ctx, id)) }
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
