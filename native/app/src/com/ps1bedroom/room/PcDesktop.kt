package com.ps1bedroom.room

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Html
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.ps1bedroom.room.engine.Canvas2D
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * HTML 16518-16933: THE PC. Tap the monitor to sit down at a 2001-style desktop: the island wallpaper, a crowded icon
 * grid (each one a joke), Messenger with buddies who chat back once you dial in, WinBeats (the boombox stations),
 * Minesweeper and the dial-up connection, with a Luna taskbar and CRT scanlines. UI thread only.
 */
@SuppressLint("SetTextI18n", "ClickableViewAccessibility", "ViewConstructor")
class PcDesktop(private val act: Activity, private val root: FrameLayout, private val app: App) {
    private val rnd = Random.Default
    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, act.resources.displayMetrics)
    private fun dpi(v: Float) = dp(v).toInt()
    private val TAHOMA = Typeface.create("sans-serif", Typeface.NORMAL)
    private val TAHOMA_B = Typeface.create("sans-serif", Typeface.BOLD)
    private val COURIER = Typeface.create("monospace", Typeface.NORMAL)
    private fun text(t: String, color: Int = Color.BLACK, size: Float = 12f, bold: Boolean = false, face: Typeface? = null) = TextView(act).apply {
        text = t; setTextColor(color); setTextSize(TypedValue.COMPLEX_UNIT_SP, size); typeface = face ?: (if (bold) TAHOMA_B else TAHOMA)
    }
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private val pc = FrameLayout(act).apply { setBackgroundColor(Color.rgb(0x14, 0x52, 0xb8)); visibility = View.GONE; isClickable = true }
    private val wall = ImageView(act).apply { scaleType = ImageView.ScaleType.FIT_XY }
    private val icons = GridLayout(act)
    private val desk = FrameLayout(act)          // windows live here
    private val tasks = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val trayNet = text("📴", Color.WHITE, 12f)
    private val trayClock = text("", Color.WHITE, 12f)
    var isOpen = false; private set

    // ---- Windows ----
    private inner class Win(val key: String, title: String, val label: String?, val bodyView: View, val widthDp: Float, val place: (FrameLayout.LayoutParams, Int, Int) -> Unit, barColors: IntArray = intArrayOf(0xff2a78f5.toInt(), 0xff0f53d8.toInt()), border: Int = 0xff0a46d6.toInt(), bg: Int = 0xffece9d8.toInt()) {
        val frame = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE; elevation = dp(6f) }
        val titleView = text(title, Color.WHITE, 12f, true).apply { maxLines = 1 }
        init {
            frame.background = GradientDrawable().apply { setColor(bg); cornerRadii = floatArrayOf(dp(8f), dp(8f), dp(8f), dp(8f), 0f, 0f, 0f, 0f); setStroke(dpi(3f), border) }
            val bar = LinearLayout(act).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dpi(8f), 0, dpi(4f), 0)
                background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, barColors).apply { cornerRadii = floatArrayOf(dp(6f), dp(6f), dp(6f), dp(6f), 0f, 0f, 0f, 0f) }
            }
            bar.addView(titleView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            val x = text("×", Color.WHITE, 14f, true).apply {
                gravity = Gravity.CENTER
                background = GradientDrawable().apply { setColor(0xffe0522e.toInt()); cornerRadius = dp(3f); setStroke(dpi(1f), Color.WHITE) }
                setOnClickListener { close(key) }
            }
            bar.addView(x, LinearLayout.LayoutParams(dpi(21f), dpi(21f)))
            frame.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpi(26f)))
            frame.addView(bodyView)
            // Drag by the title bar (on wide screens), tap anywhere to bring to front
            var sx = 0f; var sy = 0f
            bar.setOnTouchListener { _, e ->
                val lp = frame.layoutParams as FrameLayout.LayoutParams
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { front(this); sx = e.rawX - lp.leftMargin; sy = e.rawY - lp.topMargin }
                    MotionEvent.ACTION_MOVE -> if (!narrow()) { lp.gravity = Gravity.TOP or Gravity.START; lp.leftMargin = max(0, (e.rawX - sx).toInt()); lp.topMargin = max(0, (e.rawY - sy).toInt()); lp.rightMargin = 0; lp.bottomMargin = 0; frame.layoutParams = lp }
                }
                true
            }
            frame.setOnTouchListener { _, e -> if (e.actionMasked == MotionEvent.ACTION_DOWN) front(this); false }
            desk.addView(frame, FrameLayout.LayoutParams(dpi(widthDp), FrameLayout.LayoutParams.WRAP_CONTENT))
        }
        val shown get() = frame.visibility == View.VISIBLE
    }
    private val wins = LinkedHashMap<String, Win>()
    private fun narrow() = root.width / act.resources.displayMetrics.density < 600
    private fun front(w: Win) { w.frame.bringToFront(); w.frame.elevation = dp(6f) + (zTop++ % 1000) * 0.01f }
    private var zTop = 6
    fun open(k: String) {
        val w = wins.getValue(k)
        if (!w.shown) {
            val lp = w.frame.layoutParams as FrameLayout.LayoutParams
            if (narrow()) { lp.gravity = Gravity.TOP or Gravity.START; lp.width = root.width - dpi(12f); lp.leftMargin = dpi(6f); lp.topMargin = dpi(70f); lp.rightMargin = 0; lp.bottomMargin = 0 }
            else { lp.width = dpi(w.widthDp); w.place(lp, root.width, root.height) }
            w.frame.layoutParams = lp
        }
        w.frame.visibility = View.VISIBLE; front(w); refreshTasks()
    }
    private fun close(k: String) { wins.getValue(k).frame.visibility = View.GONE; refreshTasks() }
    private fun refreshTasks() {
        tasks.removeAllViews()
        for ((k, label) in listOf("im" to "Messenger", "chat" to "Chat", "amp" to "WinBeats", "ms" to "Minesweeper", "dial" to "Dial-Up")) {
            val w = wins[k] ?: continue
            if (!w.shown) continue
            tasks.addView(text(label, Color.WHITE, 11f).apply {
                gravity = Gravity.CENTER_VERTICAL; maxLines = 1; maxWidth = dpi(120f); setPadding(dpi(10f), 0, dpi(10f), 0)
                background = GradientDrawable().apply { setColor(0xff3c6fe0.toInt()); cornerRadius = dp(3f); setStroke(dpi(1f), 0xff1e3fa0.toInt()) }
                setOnClickListener { front(w) }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpi(26f)).apply { marginEnd = dpi(6f) })
        }
    }
    private fun xpButton(t: String, fn: () -> Unit) = text(t, Color.BLACK, 11f).apply {
        setPadding(dpi(10f), dpi(3f), dpi(10f), dpi(3f)); gravity = Gravity.CENTER
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.WHITE, 0xffece9d8.toInt())).apply { cornerRadius = dp(3f); setStroke(dpi(1f), 0xff003c74.toInt()) }
        setOnClickListener { fn() }
    }
    private fun body(pad: Float = 8f) = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(pad), dpi(pad), dpi(pad), dpi(pad)) }

    // ---- Icons: the useful ones first, then the clutter every 2001 desktop had ----
    private val ICON_COLS = intArrayOf(0xfacc15, 0x38bdf8, 0xf87171, 0xf8fafc, 0x4ade80, 0xc084fc, 0xfb923c)
    private val ICONS = listOf(
        listOf("My Computer", "🖥️", null, "C:\\ is 94% full. Most of it is \"Kazaa Downloads\"."),
        listOf("Messenger", "💬", "im", null), listOf("WinBeats", "🎵", "amp", null), listOf("Minesweeper", "💣", "ms", null), listOf("Connect to Internet", "🌐", "dial", null),
        listOf("My Documents", "📁", null, "Contains: homework (unfinished), 14 copies of \"untitled.bmp\" and a folder called \"DO NOT OPEN\"."),
        listOf("Recycle Bin", "🗑️", null, "The Recycle Bin is full of things you will need again tomorrow."),
        listOf("Kazaa Lite", "🦘", null, "Connecting to supernode... 0 of 1 sources found for \"the_real_slim_shady_FINAL(2).mp3\"."),
        listOf("Napster", "😺", null, "Napster could not connect. The server has been shut down (by lawyers)."),
        listOf("Half-Life", "🧪", null, "Half-Life needs 3D acceleration. Your Voodoo card is in the drawer."),
        listOf("The Sims", "🏠", null, "Reticulating splines... A Sim is on fire. It is the cooking one."),
        listOf("Diablo II", "😈", null, "Diablo II: Please insert Play Disc. (It is under the bed with the Install Disc.)"),
        listOf("RealPlayer", "▶️", null, "Buffering... 3%. Buffering... 2%."),
        listOf("Paint", "🎨", null, "Paint is already open. It has been open since March."),
        listOf("Notepad", "📝", null, "away msg ideas:\n- brb shower\n- *~*sTaRiNg At ThE sTaRs*~*\n- 4 ppl who care, ill b back"),
        listOf("homework.doc", "📄", null, "Word cannot open this document. It was never started."),
        listOf("Solitaire", "🃏", null, "You have played 1,204 games. You have won 3."),
        listOf("ICQ", "🌼", null, "Uh oh! ICQ is not connected."),
        listOf("Winamp skins", "💿", null, "Installed 46 skins. You only use the default."),
        listOf("Pictures", "🖼️", null, "Loading 1 of 212 pictures over 56K... estimated time: 3 hours."),
        listOf("SETI@home", "👽", null, "No aliens found yet. Your CPU fan disagrees."),
        listOf("Encarta 98", "📚", null, "Please insert Encarta Disc 2 of 2."),
        listOf("Roller Coaster Tycoon", "🎢", null, "A guest wants to get off Mr. Bones' Wild Ride."),
        listOf("Shortcut to Shortcut", "🔗", null, "The item that this shortcut refers to has been changed or moved.")
    )
    private val iconLabels = ArrayList<TextView>()

    // ---- Error box ----
    private val errIcon = text("⚠️", Color.BLACK, 28f)
    private val errText = text("", Color.BLACK, 12f)
    // ---- Messenger / chat ----
    private class Buddy(val name: String, val lines: List<String>)
    private val BUDDIES = listOf(
        Buddy("xXSk8rBoiXx", listOf("yo did u beat the water level yet", "my mom took my memory card lol", "tony hawk 3 >>> everything", "brb dinner")),
        Buddy("CoolGurl2001", listOf("omg did u see the new music video", "lol", "r u going to the dance friday?", "gtg my brother needs the phone")),
        Buddy("LinkinParkFan99", listOf("in the end it doesnt even matter", "crawling in my skiiin", "ok burning u a cd tomorrow", ":P")),
        Buddy("PizzaDude", listOf("pizza friday?", "pepperoni or riot", "my modem is so slow omg", "ok cya")),
        Buddy("Mom", listOf("Dinner is in the fridge.", "Are you still on the computer?", "I need the phone line in 5 minutes.", "Love you. Bed by 11."))
    )
    private var online = false
    private val imStatus = text("Offline", Color.WHITE, 12f, true)
    private val imList = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
    private val awayOn = CheckBox(act).apply { text = "I'm away"; setTextColor(Color.BLACK); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f) }
    private var chatWith: Buddy? = null
    private val chatLogs = HashMap<String, ArrayList<Pair<String, String>>>()
    private val chatLog = TextView(act).apply { setTextColor(Color.BLACK); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); setTextIsSelectable(true) }
    private val chatScroll = ScrollView(act)
    private val chatIn = EditText(act)
    // ---- WinBeats ----
    private val ampNow = text("Stopped", 0xff00ff3c.toInt(), 12f, face = COURIER)
    private val ampTime = text("--:--", 0xff99ffff.toInt(), 12f, face = COURIER)
    private val ampList = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
    private val vizBars = ArrayList<View>()
    // ---- Minesweeper ----
    private class Cell(var mine: Boolean = false, var open: Boolean = false, var flag: Boolean = false, var adj: Int = 0, var boom: Boolean = false)
    private val MSN = 9; private val MINES = 10
    private var cells = List(MSN * MSN) { Cell() }
    private var msOver = false; private var msStarted = 0L; private var msFlags = 0; private var firstClick = true
    private val msGrid = GridLayout(act).apply { columnCount = MSN; rowCount = MSN }
    private val msLeft = lcd("010"); private val msTime = lcd("000")
    private val msFace = text("🙂", Color.BLACK, 18f)
    private val msFlagMode = CheckBox(act).apply { text = "🚩 Flag mode (or long-press)"; setTextColor(Color.BLACK); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f) }
    private fun lcd(t: String) = text(t, Color.RED, 18f, face = Typeface.create("monospace", Typeface.BOLD)).apply { setBackgroundColor(Color.BLACK); setPadding(dpi(4f), 0, dpi(4f), 0); minWidth = dpi(44f); gravity = Gravity.END }
    // ---- Dial-up ----
    private val dialLog = text("Not connected.", Color.BLACK, 12f, face = COURIER).apply { minHeight = dpi(60f) }

    init {
        pc.addView(wall, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        // Icon grid down the left, columns of 70 dp
        icons.orientation = GridLayout.VERTICAL
        ICONS.forEachIndexed { i, ic ->
            val cell = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
            val glyph = text(ic[1] as String, Color.BLACK, 19f).apply {
                gravity = Gravity.CENTER
                val c = ICON_COLS[i % ICON_COLS.size]
                background = android.graphics.drawable.LayerDrawable(arrayOf(
                    GradientDrawable().apply { setColor(Color.argb(115, 0, 0, 0)); cornerRadius = dp(3f) },
                    GradientDrawable().apply { setColor(Color.rgb(c shr 16 and 255, c shr 8 and 255, c and 255)); cornerRadius = dp(3f); setStroke(dpi(1f), Color.argb(90, 255, 255, 255)) }
                )).apply { setLayerInset(0, dpi(3f), dpi(3f), 0, 0); setLayerInset(1, 0, 0, dpi(3f), dpi(3f)) }
            }
            cell.addView(glyph, LinearLayout.LayoutParams(dpi(35f), dpi(35f)))
            val label = text(ic[0] as String, 0xffe0f2fe.toInt(), 11f).apply {
                gravity = Gravity.CENTER; maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; setLineSpacing(0f, 1.0f)
                setPadding(dpi(3f), dpi(1f), dpi(3f), dpi(1f)); setBackgroundColor(Color.argb(191, 8, 28, 68))
            }
            iconLabels += label
            cell.addView(label, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(3f) })
            cell.setOnClickListener {
                iconLabels.forEachIndexed { j, l -> l.setBackgroundColor(if (j == i) 0xff316ac5.toInt() else Color.argb(191, 8, 28, 68)) }
                val win = ic[2] as String?
                if (win != null) { if (win == "im") renderBuddies(); open(win); return@setOnClickListener }
                wins.getValue("err").titleView.text = ic[0] as String
                errIcon.text = ic[1] as String
                errText.text = ic[3] as String
                open("err")
            }
            icons.addView(cell, GridLayout.LayoutParams().apply { width = dpi(70f); height = dpi(70f); setMargins(dpi(2f), dpi(1f), dpi(2f), dpi(1f)) })
        }
        pc.addView(icons, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT).apply { leftMargin = dpi(8f); topMargin = dpi(8f); bottomMargin = dpi(44f) })
        pc.addView(desk, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT).apply { bottomMargin = dpi(36f) })

        // Error box
        val errBody = body().apply {
            val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(errIcon)
            val col = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
            col.addView(errText)
            col.addView(xpButton("OK") { close("err") }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.END; topMargin = dpi(10f) })
            row.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dpi(10f) })
            addView(row)
        }
        wins["err"] = Win("err", "Error", null, errBody, 300f, { lp, w, h -> lp.gravity = Gravity.TOP or Gravity.START; lp.leftMargin = (w * 0.3f).toInt(); lp.topMargin = (h * 0.3f).toInt() })

        // Messenger
        val imBody = body().apply {
            addView(imList)
            addView(text("Away message:", Color.BLACK, 12f, true), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(8f) })
            addView(EditText(act).apply { setText("brb, getting pizza 🍕 also my mom is on the phone so i might get kicked off lol"); setTextColor(Color.BLACK); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); minLines = 2; setBackgroundColor(Color.WHITE); setPadding(dpi(4f), dpi(4f), dpi(4f), dpi(4f)) })
            addView(awayOn)
        }
        wins["im"] = Win("im", "", null, imBody, 210f, { lp, _, _ -> lp.gravity = Gravity.TOP or Gravity.END; lp.rightMargin = dpi(10f); lp.topMargin = dpi(8f) },
            intArrayOf(0xff2f55c8.toInt(), 0xff1e40af.toInt()), 0xff1e40af.toInt(), 0xfff8fafc.toInt())
        wins.getValue("im").titleView.apply { (parent as ViewGroup).removeView(this) }
        (wins.getValue("im").frame.getChildAt(0) as LinearLayout).addView(LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(text("Messenger · ", Color.WHITE, 12f, true)); addView(imStatus)
        }, 0, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        // Chat
        val chatBody = body().apply {
            chatScroll.addView(chatLog)
            chatScroll.setBackgroundColor(Color.WHITE); chatScroll.setPadding(dpi(4f), dpi(4f), dpi(4f), dpi(4f))
            addView(chatScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpi(150f)))
            val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            chatIn.apply { hint = "Type a message..."; setTextColor(Color.BLACK); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); setBackgroundColor(Color.WHITE); setPadding(dpi(4f), dpi(4f), dpi(4f), dpi(4f)); isSingleLine = true; imeOptions = EditorInfo.IME_ACTION_SEND
                setOnEditorActionListener { _, _, _ -> send(); true } }
            row.addView(chatIn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(xpButton("Send") { send() }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dpi(4f) })
            addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(6f) })
        }
        wins["chat"] = Win("chat", "Chat", null, chatBody, 300f, { lp, _, _ -> lp.gravity = Gravity.TOP or Gravity.END; lp.rightMargin = dpi(234f); lp.topMargin = dpi(30f) })

        // WinBeats
        val ampBody = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(0xff232836.toInt()); setPadding(dpi(8f), dpi(8f), dpi(8f), dpi(8f))
            val disp = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM; setBackgroundColor(Color.BLACK); setPadding(dpi(6f), dpi(6f), dpi(6f), dpi(6f)) }
            val viz = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
            repeat(14) {
                val bar = View(act).apply { background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xffff3b3b.toInt(), 0xffffe600.toInt(), 0xff00ff3c.toInt())) }
                vizBars += bar
                viz.addView(bar, LinearLayout.LayoutParams(dpi(5f), dpi(2f)).apply { marginEnd = dpi(2f) })
            }
            disp.addView(viz, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpi(40f)))
            val nowCol = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; addView(ampNow); addView(ampTime) }
            disp.addView(nowCol, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dpi(10f) })
            addView(disp, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpi(54f)))
            val btns = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
            fun ampBtn(t: String, fn: () -> Unit) = text(t, 0xffdddddd.toInt(), 12f, face = COURIER).apply { setBackgroundColor(0xff3a4252.toInt()); setPadding(dpi(8f), dpi(4f), dpi(8f), dpi(4f)); setOnClickListener { fn() } }
            btns.addView(ampBtn("▶ Play") { app.post { if (app.extras.radioStation == 0) app.extras.cycleRadio(app.furniture.radioDisplayMat); app.settings.changed() }; handler.postDelayed({ renderAmp() }, 80) })
            btns.addView(ampBtn("■ Stop") { app.post { while (app.extras.radioStation != 0) app.extras.cycleRadio(app.furniture.radioDisplayMat); app.settings.changed() }; handler.postDelayed({ renderAmp() }, 80) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dpi(4f) })
            btns.addView(ampBtn("⏭ Next") { app.post { app.extras.cycleRadio(app.furniture.radioDisplayMat); if (app.extras.radioStation == 0) app.extras.cycleRadio(app.furniture.radioDisplayMat); app.settings.changed() }; handler.postDelayed({ renderAmp() }, 80) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dpi(4f) })
            addView(btns, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(6f); bottomMargin = dpi(6f) })
            addView(ampList)
            addView(text("Plays on the boombox speakers. Add your own songs in Menu → Sound → My Music.", 0xff888888.toInt(), 11f, face = COURIER), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(4f) })
        }
        wins["amp"] = Win("amp", "WINBEATS 2.81", null, ampBody, 290f, { lp, _, _ -> lp.gravity = Gravity.BOTTOM or Gravity.END; lp.rightMargin = dpi(10f); lp.bottomMargin = dpi(10f) },
            intArrayOf(0xff475569.toInt(), 0xff334155.toInt()), 0xff64748b.toInt(), 0xff181824.toInt())

        // Minesweeper
        val msBody = body().apply {
            val head = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(0xffc0c0c0.toInt()); setPadding(dpi(4f), dpi(4f), dpi(4f), dpi(4f)) }
            head.addView(msLeft); head.addView(View(act), LinearLayout.LayoutParams(0, 1, 1f))
            head.addView(xpButton("") {}.apply { visibility = View.GONE })
            msFace.setOnClickListener { msNew() }
            head.addView(msFace); head.addView(View(act), LinearLayout.LayoutParams(0, 1, 1f)); head.addView(msTime)
            addView(head)
            addView(msFlagMode, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(6f); bottomMargin = dpi(6f) })
            msGrid.setBackgroundColor(0xffbdbdbd.toInt())
            addView(msGrid, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        wins["ms"] = Win("ms", "Minesweeper", null, msBody, 9 * 26f + 28f, { lp, _, _ -> lp.gravity = Gravity.TOP or Gravity.START; lp.leftMargin = dpi(430f); lp.topMargin = dpi(30f) })

        // Dial-up
        val dialBody = body().apply {
            addView(text("📞 555-0199 · 56K V.90 Modem", Color.BLACK, 12f, face = COURIER))
            addView(dialLog, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(8f); bottomMargin = dpi(8f) })
            val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(xpButton("Connect") { dialUp() })
            row.addView(xpButton("Hang Up") { hangUp() }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dpi(6f) })
            addView(row)
        }
        wins["dial"] = Win("dial", "Dial-Up Connection", null, dialBody, 300f, { lp, _, _ -> lp.gravity = Gravity.TOP or Gravity.START; lp.leftMargin = dpi(200f); lp.topMargin = dpi(120f) })

        // CRT scanlines and the dark edges
        pc.addView(object : View(act) {
            private val line = Paint().apply { color = Color.argb(20, 0, 0, 0) }
            private val vig = Paint()
            override fun onDraw(c: Canvas) {
                var y = 0f; val step = dp(3f)
                while (y < height) { c.drawRect(0f, y, width.toFloat(), y + dp(1f), line); y += step }
                vig.shader = RadialGradient(width / 2f, height / 2f, max(width, height) * 0.75f, intArrayOf(Color.TRANSPARENT, Color.argb(115, 0, 0, 0)), floatArrayOf(0.55f, 1f), Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), vig)
            }
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // Taskbar
        val task = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xff3a7bf5.toInt(), 0xff2457d6.toInt(), 0xff1d47b8.toInt()))
        }
        val start = text("⏻ start", Color.WHITE, 15f, true).apply {
            setTypeface(TAHOMA_B, Typeface.BOLD_ITALIC); gravity = Gravity.CENTER_VERTICAL; setPadding(dpi(10f), 0, dpi(16f), 0)
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xff4fb84a.toInt(), 0xff2f8f2a.toInt())).apply { cornerRadii = floatArrayOf(0f, 0f, dp(12f), dp(12f), dp(12f), dp(12f), 0f, 0f) }
            setOnClickListener { close() }
        }
        task.addView(start, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpi(30f)))
        task.addView(tasks, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply { marginStart = dpi(6f) })
        val tray = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(0xff1192e8.toInt()); setPadding(dpi(12f), 0, dpi(12f), 0) }
        tray.addView(text("🔊 ", Color.WHITE, 12f)); tray.addView(trayNet); tray.addView(text(" ", Color.WHITE, 12f)); tray.addView(trayClock)
        task.addView(tray, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT))
        pc.addView(task, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dpi(36f), Gravity.BOTTOM))
        pc.addView(View(act).apply { setBackgroundColor(0xff5f9bff.toInt()) }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dpi(2f), Gravity.BOTTOM).apply { bottomMargin = dpi(36f) })

        root.addView(pc, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        renderBuddies()
        msNew()
    }

    // ---- Wallpaper: the monitor's 128 x 88 picture, stretched to this screen ----
    private fun drawWallpaper() {
        val W = 320; val H = max(120, (320f * root.height / max(1, root.width)).roundToInt())
        val c = Canvas2D(W, H)
        c.setTransform(W / 128f, 0, 0, H / 88f, 0, 0)
        app.monitor.drawXpWallpaper(c)
        c.setTransform(1, 0, 0, 1, 0, 0)
        val bmp = Bitmap.createScaledBitmap(c.bitmap, root.width.coerceAtLeast(1), root.height.coerceAtLeast(1), false)
        wall.setImageBitmap(bmp)
    }

    // ---- Dial-up ----
    private fun dialUp() {
        if (online) return
        val steps = listOf("Dialing 555-0199...", "Verifying username and password...", "Logging on to network...", "Connected at 53,333 bps! 🎉")
        dialLog.text = steps[0]
        steps.drop(1).forEachIndexed { i, s -> handler.postDelayed({
            dialLog.text = s
            if (i == 2) { online = true; trayNet.text = "🌐"; imStatus.text = "Online"; renderBuddies(); scheduleBuddyChatter() }
        }, 2800L + i * 3000L) }
    }
    private fun hangUp() { online = false; dialLog.text = "Disconnected."; trayNet.text = "📴"; imStatus.text = "Offline"; renderBuddies() }

    // ---- Messenger ----
    private fun renderBuddies() {
        imList.removeAllViews()
        imList.addView(text("Buddies (${if (online) BUDDIES.size else 0}/${BUDDIES.size})", Color.BLACK, 12f, true), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dpi(4f) })
        BUDDIES.forEachIndexed { i, b ->
            val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dpi(6f), dpi(3f), dpi(6f), dpi(3f)); setOnClickListener { openChat(b) } }
            row.addView(View(act).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (!online) 0xff9ca3af.toInt() else if (i == 3) 0xfffacc15.toInt() else 0xff22c55e.toInt()) } }, LinearLayout.LayoutParams(dpi(8f), dpi(8f)).apply { marginEnd = dpi(6f) })
            row.addView(text(b.name, Color.BLACK, 12f))
            imList.addView(row)
        }
    }
    private fun addLine(b: Buddy, who: String, t: String) {
        chatLogs.getOrPut(b.name) { ArrayList() } += who to t
        if (chatWith === b) renderChat()
    }
    private fun renderChat() {
        val b = chatWith ?: return
        val html = (chatLogs[b.name] ?: emptyList()).joinToString("<br>") { (who, t) ->
            val name = if (who == "me") "<font color='#cc0000'><b>You:</b></font>" else "<font color='#0000cc'><b>${b.name}:</b></font>"
            "$name ${android.text.TextUtils.htmlEncode(t)}"
        }
        chatLog.text = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }
    private fun openChat(b: Buddy) {
        if (!online) { dialLog.text = "Connect to the Internet first!"; open("dial"); return }
        chatWith = b
        wins.getValue("chat").titleView.text = "${b.name} - Instant Message"
        open("chat"); renderChat()
    }
    private fun buddySays(b: Buddy) {
        if (!online) return
        addLine(b, "them", b.lines[rnd.nextInt(b.lines.size)])
        if (!wins.getValue("chat").shown || chatWith !== b) { chatWith = b; wins.getValue("chat").titleView.text = "${b.name} - Instant Message"; open("chat"); renderChat() }
    }
    private fun send() {
        val v = chatIn.text.toString().trim()
        val b = chatWith
        if (v.isEmpty() || b == null) return
        addLine(b, "me", v)
        chatIn.setText("")
        if (awayOn.isChecked) return
        handler.postDelayed({ buddySays(b) }, (1500 + rnd.nextDouble() * 2500).toLong())
    }
    private val chatter = Runnable { if (online && isOpen && !awayOn.isChecked) buddySays(BUDDIES[rnd.nextInt(BUDDIES.size)]); scheduleBuddyChatter() }
    private fun scheduleBuddyChatter() { handler.removeCallbacks(chatter); handler.postDelayed(chatter, (25000 + rnd.nextDouble() * 35000).toLong()) }

    // ---- WinBeats: the boombox stations (My Music joins them with the audio update) ----
    private fun renderAmp() {
        ampList.removeAllViews()
        val st = app.extras.radioStation
        app.extras.RADIO_STATIONS.forEachIndexed { i, n ->
            if (i == 0) return@forEachIndexed
            val cur = st == i
            ampList.addView(text("$i. 📻 ${n.substringBefore(':')}", if (cur) Color.WHITE else 0xff00ff3c.toInt(), 12f, face = COURIER).apply {
                setPadding(dpi(4f), dpi(2f), dpi(4f), dpi(2f)); if (cur) setBackgroundColor(0xff0a3d8f.toInt())
                setOnClickListener { app.post { var guard = 0; while (app.extras.radioStation != i && guard++ < 5) app.extras.cycleRadio(app.furniture.radioDisplayMat); app.settings.changed() }; handler.postDelayed({ renderAmp() }, 80) }
            })
        }
        ampNow.text = if (st > 0) app.extras.RADIO_STATIONS[st].substringBefore(':') else "Stopped"
    }

    // ---- Minesweeper (9x9, 10 mines) ----
    private fun msNew() {
        cells = List(MSN * MSN) { Cell() }
        msOver = false; msStarted = 0L; msFlags = 0; firstClick = true
        msFace.text = "🙂"; msTime.text = "000"
        renderMs()
    }
    private fun nbrs(i: Int): List<Int> {
        val x = i % MSN; val y = i / MSN; val out = ArrayList<Int>()
        for (dy in -1..1) for (dx in -1..1) { if (dx == 0 && dy == 0) continue; val nx = x + dx; val ny = y + dy; if (nx in 0 until MSN && ny in 0 until MSN) out += ny * MSN + nx }
        return out
    }
    private fun placeMines(safe: Int) {
        var placed = 0
        while (placed < MINES) { val i = rnd.nextInt(cells.size); if (i == safe || cells[i].mine) continue; cells[i].mine = true; placed++ }
        cells.forEachIndexed { i, c -> c.adj = nbrs(i).count { cells[it].mine } }
    }
    private fun reveal(i: Int) {
        val c = cells[i]
        if (c.open || c.flag) return
        c.open = true
        if (c.adj == 0 && !c.mine) nbrs(i).forEach { reveal(it) }
    }
    private fun msClick(i: Int, flag: Boolean) {
        if (msOver) return
        val c = cells[i]
        if (flag) { if (!c.open) { c.flag = !c.flag; msFlags += if (c.flag) 1 else -1 }; renderMs(); return }
        if (c.flag) return
        if (firstClick) { placeMines(i); firstClick = false; msStarted = System.currentTimeMillis() }
        if (c.mine) { msOver = true; c.boom = true; cells.forEach { if (it.mine) it.open = true }; msFace.text = "😵" }
        else { reveal(i); if (cells.all { it.mine || it.open }) { msOver = true; msFace.text = "😎"; app.achievements.achieve("minesweeper") } }
        renderMs()
    }
    private val NUMCOL = intArrayOf(0, 0xff0000ff.toInt(), 0xff008000.toInt(), 0xffff0000.toInt(), 0xff000080.toInt(), 0xff800000.toInt(), 0xff008080.toInt(), Color.BLACK, 0xff808080.toInt())
    private fun renderMs() {
        msGrid.removeAllViews()
        cells.forEachIndexed { i, c ->
            val b = text("", Color.BLACK, 14f, true).apply {
                gravity = Gravity.CENTER
                if (c.open) {
                    text = if (c.mine) "💣" else if (c.adj > 0) "${c.adj}" else ""
                    setTextColor(NUMCOL[c.adj])
                    background = GradientDrawable().apply { setColor(if (c.boom) Color.RED else 0xffbdbdbd.toInt()); setStroke(dpi(1f), 0xff7b7b7b.toInt()) }
                } else {
                    if (c.flag) text = "🚩"
                    background = android.graphics.drawable.LayerDrawable(arrayOf(GradientDrawable().apply { setColor(0xff7b7b7b.toInt()) }, GradientDrawable().apply { setColor(Color.WHITE) }, GradientDrawable().apply { setColor(0xffc0c0c0.toInt()) })).apply {
                        setLayerInset(1, 0, 0, dpi(3f), dpi(3f)); setLayerInset(2, dpi(3f), dpi(3f), dpi(3f), dpi(3f))
                    }
                }
                var pressT = 0L
                setOnTouchListener { _, e -> if (e.actionMasked == MotionEvent.ACTION_DOWN) pressT = System.currentTimeMillis(); false }
                setOnClickListener { val held = pressT > 0 && System.currentTimeMillis() - pressT > 450; pressT = 0; msClick(i, msFlagMode.isChecked || held) }
                setOnLongClickListener { msClick(i, true); true }
            }
            msGrid.addView(b, GridLayout.LayoutParams().apply { width = dpi(26f); height = dpi(26f) })
        }
        msLeft.text = max(0, MINES - msFlags).toString().padStart(3, '0')
    }

    // ---- Open / close ----
    private var opened = false
    private val tick = object : Runnable {
        override fun run() {
            if (!isOpen) return
            trayClock.text = app.env.formatHour(app.env.hour)
            ampTime.text = if (app.extras.radioStation > 0) "LIVE" else "--:--"
            if (msStarted > 0 && !msOver) msTime.text = min(999L, (System.currentTimeMillis() - msStarted) / 1000).toString().padStart(3, '0')
            val playing = app.extras.radioStation > 0
            val now = System.currentTimeMillis()
            vizBars.forEachIndexed { i, bar -> val lp = bar.layoutParams; lp.height = if (playing) dpi(4f + abs(sin(now / 180.0 + i * 1.7)).toFloat() * 34f) else dpi(2f); bar.layoutParams = lp }
            handler.postDelayed(this, 120)
        }
    }
    fun show() {
        handler.postDelayed({
            isOpen = true
            pc.visibility = View.VISIBLE
            pc.bringToFront()
            pc.post { drawWallpaper() }
            // Like the monitor: the buddy list and the music player are already up when you sit down
            if (!opened && !narrow()) { opened = true; renderBuddies(); open("im"); open("amp") }
            renderAmp()
            handler.removeCallbacks(tick); handler.post(tick)
        }, 600)
    }
    fun close() {
        isOpen = false
        pc.visibility = View.GONE
        handler.removeCallbacks(tick)
        (act.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(pc.windowToken, 0)
    }
    fun back(): Boolean { if (isOpen) { close(); return true }; return false }
}
