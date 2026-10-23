package com.ps1bedroom.room

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * HTML openBoombox: tapping the boombox opens its player. The FM stations and MY MUSIC, what's playing with its
 * cover, play / pause / skip, shuffle and repeat, your albums, and adding songs. Everything goes through MyMusic on
 * the GL thread; the panel redraws when MyMusic says something changed.
 */
class BoomboxPanel(private val act: Activity, private val root: FrameLayout, private val app: App) {
    private val T = UiTheme
    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, act.resources.displayMetrics)
    private fun dpi(v: Float) = dp(v).toInt()
    private val BG get() = T.panel
    private val PANEL get() = T.panel2
    private val LINE get() = T.line
    private val TEXT get() = T.text
    private val MUTED get() = T.muted
    private val PINK get() = T.accent
    private val ORANGE get() = T.accent2
    private val ACTIVE_BG get() = T.activeBg
    private val ON_TEXT get() = T.activeText

    private val scrim = FrameLayout(act).apply { setBackgroundColor(T.scrim); visibility = View.GONE; isClickable = true; setOnClickListener { close() } }
    private val body = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(14f), dpi(12f), dpi(14f), dpi(14f)) }
    private val card = FrameLayout(act)
    val isOpen get() = scrim.visibility == View.VISIBLE

    init {
        card.apply {
            isClickable = true
            addView(ScrollView(act).apply { addView(body) })
        }
        val w = minOf(dpi(460f), act.resources.displayMetrics.widthPixels - dpi(20f))
        scrim.addView(card, FrameLayout.LayoutParams(w, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { topMargin = dpi(60f); bottomMargin = dpi(20f) })
        root.addView(scrim, FrameLayout.LayoutParams(-1, -1))
        T.listeners += { restyle() }
        restyle()
    }

    private fun restyle() {
        scrim.setBackgroundColor(T.scrim)
        card.background = GradientDrawable().apply { setColor(BG); cornerRadius = dp(16f); setStroke(dpi(2f), LINE) }
        if (isOpen) render()
    }

    fun open() { restyle(); scrim.visibility = View.VISIBLE; scrim.bringToFront(); render() }
    fun close() { scrim.visibility = View.GONE }
    /** MyMusic changed (any thread): redraw if it's showing. */
    fun refresh() { if (isOpen) act.runOnUiThread { if (isOpen) render() } }

    private fun text(t: String, color: Int = TEXT, size: Float = 12f, bold: Boolean = false) = TextView(act).apply {
        text = t; setTextColor(color); setTextSize(TypedValue.COMPLEX_UNIT_SP, size); typeface = Typeface.create(Typeface.MONOSPACE, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
    private fun pill(t: String, on: Boolean, size: Float = 12f, fn: () -> Unit) = text(t, if (on) ON_TEXT else TEXT, size, true).apply {
        gravity = Gravity.CENTER; setPadding(dpi(10f), dpi(8f), dpi(10f), dpi(8f))
        isFocusable = true
        background = GradientDrawable().apply { setColor(if (on) ACTIVE_BG else PANEL); cornerRadius = dp(999f); setStroke(dpi(if (on) 2f else 1f), if (on) PINK else LINE) }
        setOnClickListener { fn() }
    }
    private fun row(vararg views: View, weights: Boolean = true) = LinearLayout(act).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        views.forEachIndexed { i, v -> addView(v, if (weights) LinearLayout.LayoutParams(0, -2, 1f).apply { if (i > 0) marginStart = dpi(6f) } else LinearLayout.LayoutParams(-2, -2).apply { if (i > 0) marginStart = dpi(6f) }) }
    }
    private fun add(v: View, top: Float = 10f) = body.addView(v, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(top) })
    private fun go(fn: () -> Unit) = app.post(fn)

    private fun render() {
        body.removeAllViews()
        val mm = app.myMusic
        val st = app.extras.radioStation
        val onMusic = st == mm.MY_MUSIC_STATION
        // Header
        val close = text("✕", TEXT, 16f, true).apply { setPadding(dpi(10f), dpi(4f), dpi(4f), dpi(4f)); setOnClickListener { close() } }
        body.addView(LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(text("📻 BOOMBOX", ORANGE, 15f, true), LinearLayout.LayoutParams(0, -2, 1f)); addView(close)
        })
        // Now playing: cover + title
        val cur = mm.current
        val art = ImageView(act).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply { setColor(PANEL); cornerRadius = dp(8f) }
            clipToOutline = true
            val a = if (onMusic) mm.nowArt else null
            if (a != null) setImageBitmap(a) else { setImageDrawable(null) }
        }
        val title = text(when {
            st == 0 -> "Radio off"
            onMusic -> cur?.title ?: "MY MUSIC"
            else -> app.extras.RADIO_STATIONS[st].substringBefore(':')
        }, TEXT, 14f, true).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
        val sub = text(when {
            st == 0 -> "Pick a station or play your music"
            onMusic -> cur?.let { listOf(it.artist, it.album).filter { s -> s.isNotEmpty() }.joinToString(" · ") }?.ifEmpty { null } ?: mm.statusText()
            else -> app.extras.RADIO_STATIONS[st].substringAfter(": ")
        }, MUTED, 11f).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
        val info = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; addView(title); addView(sub) }
        add(LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(art, LinearLayout.LayoutParams(dpi(72f), dpi(72f)))
            addView(info, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dpi(12f) })
        })
        // Transport
        val playing = app.sound.playing && st != 0 && (!onMusic || !mm.paused)
        add(row(pill("⏮", false, 15f) { go { mm.mediaControl("prev", true) } },
            pill(if (playing) "⏸" else "▶", playing, 15f) { go { mm.mediaControl("toggle", true) } },
            pill("⏭", false, 15f) { go { mm.mediaControl("next", true) } }))
        if (onMusic) add(row(pill(if (mm.shuffle) "🔀 Shuffle on" else "🔀 Shuffle off", mm.shuffle, 11f) { go { mm.toggleShuffle() } },
            pill(mapOf("all" to "🔁 Repeat all", "one" to "🔂 Repeat one", "off" to "➡️ Repeat off").getValue(mm.repeat), mm.repeat != "off", 11f) { go { mm.cycleRepeat() } }), 6f)
        // Stations
        add(text("STATIONS", MUTED, 10f, true).apply { letterSpacing = 0.15f }, 14f)
        val names = app.extras.RADIO_STATIONS.mapIndexed { i, n -> i to (if (i == 0) "OFF" else n.substringBefore(':')) }
        add(row(*names.take(2).map { (i, n) -> pill(n, st == i, 11f) { go { mm.boomboxTab = if (i == mm.MY_MUSIC_STATION) "music" else "fm"; mm.setRadioStation(i); if (i == mm.MY_MUSIC_STATION) mm.mediaControl("play", true) } } }.toTypedArray()), 6f)
        add(row(*names.drop(2).map { (i, n) -> pill(n, st == i, 11f) { go { mm.boomboxTab = if (i == mm.MY_MUSIC_STATION) "music" else "fm"; mm.setRadioStation(i); if (i == mm.MY_MUSIC_STATION) mm.mediaControl("play", true) } } }.toTypedArray()), 6f)
        // My Music library
        add(text("MY MUSIC", MUTED, 10f, true).apply { letterSpacing = 0.15f }, 14f)
        val albums = mm.albums()
        if (albums.isEmpty()) add(text("No songs yet. Add songs or a whole album folder from your phone: they stay where they are, and play through this boombox.", MUTED, 11f), 6f)
        for (a in albums) {
            val isCur = a.tracks.contains(cur)
            val cover = ImageView(act).apply { scaleType = ImageView.ScaleType.CENTER_CROP; mm.art(a.key)?.let { setImageBitmap(it) }; background = GradientDrawable().apply { setColor(LINE); cornerRadius = dp(4f) }; clipToOutline = true }
            val label = LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(a.title, if (isCur) PINK else TEXT, 12f, true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
                addView(text("${a.artist.ifEmpty { "Unknown artist" }} · ${a.tracks.size} song${if (a.tracks.size > 1) "s" else ""}", MUTED, 10f).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
            }
            add(LinearLayout(act).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dpi(6f), dpi(6f), dpi(6f), dpi(6f))
                background = GradientDrawable().apply { setColor(PANEL); cornerRadius = dp(10f); setStroke(1, if (isCur) PINK else LINE) }
                addView(cover, LinearLayout.LayoutParams(dpi(40f), dpi(40f)))
                addView(label, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dpi(10f) })
                addView(text("▶", ORANGE, 14f, true).apply { setPadding(dpi(8f), 0, dpi(6f), 0) })
                setOnClickListener { go { mm.playList(a.tracks) } }
            }, 6f)
            // The songs of the album that's playing
            if (isCur) for ((i, t) in a.tracks.withIndex()) add(text("${if (t == cur) "▶ " else "   "}${if (t.track > 0) "${t.track}. " else ""}${t.title}", if (t == cur) PINK else TEXT, 11f).apply {
                maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setPadding(dpi(14f), dpi(5f), dpi(6f), dpi(5f))
                setOnClickListener { go { mm.playList(a.tracks, i) } }
            }, 0f)
        }
        add(row(pill("＋ Add songs", false, 11f) { app.pickMusic?.invoke(false) }, pill("＋ Album folder", false, 11f) { app.pickMusic?.invoke(true) }), 10f)
    }
}
