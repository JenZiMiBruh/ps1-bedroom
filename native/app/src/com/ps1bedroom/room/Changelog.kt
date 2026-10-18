package com.ps1bedroom.room

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * The native build's changelog, from 0.1 to the final release. Newest first. Add an entry with every version bump
 * (the version tag in the corner opens it; it moves into the Settings menu once the menus are ported).
 */
object Changelog {
    class Entry(val version: String, val title: String, val notes: List<String>)

    val entries = listOf(
        Entry("0.7", "Ceiling fan, dresser, nightstand and more", listOf(
            "Ceiling fan: blades spin up and coast down, the glass shade glows when the light is on, pull chains jump",
            "Tap the fan to toggle its light, tap the blades or motor to start / stop it, tap the AC to switch it",
            "AC louvre sweeps while the unit runs",
            "Dresser with drawers, boombox, family photo, trophy and the SMASH CITY band poster",
            "Nightstand with a reading lamp (tap it), water glass and a book",
            "Wastebasket with crumpled paper (and one missed shot), backpack by the dresser",
            "Wall calendar showing today's month like 2001, with a pixel picture for every month and the days crossed off",
            "THE MATRIKS movie poster over the bed"
        )),
        Entry("0.6", "The bed, the chair and an in-app changelog", listOf(
            "The bed: bookcase headboard with turned posts and finials, rails, footboard, shoe box and skateboard underneath",
            "Fitted sheet, wrinkled space comforter, folded top sheet and plump pillows",
            "Jumbo LED clock radio on the nightstand showing the room's time",
            "2001 office chair with casters, gas lift, contoured seat and lumbar backrest",
            "Wall shelf with the cat photo (tap it for night-vision eyes), plant and books",
            "Tap the lava lamp to switch its colours",
            "Baseboard now runs all the way around the room, right up to the door casing",
            "This changelog (tap the version tag in the corner)"
        )),
        Entry("0.5", "The desk corner", listOf(
            "Desk and hutch with the live-edge front, CD rack, strategy guide and a loose CD",
            "HP Pavilion tower with the \"DO NOT TURN OFF\" sticky note, monitor, keyboard and mouse",
            "Cordless phone, MP3 player and its cable, Surge can, lava lamp with rising blobs",
            "Wall AC unit, the construction sign on the door, hinges, knobs and the light under the door",
            "Weathering maps now match the web version surface for surface"
        )),
        Entry("0.4", "The city outside", listOf(
            "3D towers with lit windows by night and glass by day, roof beacons and a back row of towers",
            "Highway traffic that follows the clock and the weather: rush hours, quiet nights, police, wet roads",
            "Commuter train on the viaduct, tri-vision billboards and the LED ticker, trees swaying with the seasons"
        )),
        Entry("0.3", "The sky", listOf(
            "Sky dome with time-of-day colours, sunset glow, twinkling stars and the Milky Way",
            "Sun and moon where the real sky puts them, with tonight's moon phase",
            "Drifting clouds, horizon ring and the painted city backdrop",
            "Airliners, birds, shooting stars, rainbows, distant rain and snow, haze"
        )),
        Entry("0.2.1", "Fixes", listOf(
            "Fixed the bright wedge and stripes from the shadows",
            "Objects no longer disappear before they leave the screen",
            "Faster texture updates with no memory churn"
        )),
        Entry("0.2", "Performance, blinds and time of day", listOf(
            "Zero-garbage renderer, merged static furniture, shadows redrawn only when something moves",
            "60 fps cap, 30 / 20 fps when idle, and automatic cool-down when the phone gets warm",
            "Window blinds with all four states; tap the blinds or the light switch",
            "Real sun and moon positions: the light through the window follows the time of day"
        )),
        Entry("0.1", "First native build", listOf(
            "Kotlin + OpenGL ES 3 engine: three.js-identical geometry, the PS1 shader and the post pass",
            "The room shell: walls, floor, ceiling, rug, door and the room lights",
            "Orbit camera with drag, pinch and two-finger pan"
        ))
    )

    private fun dp(a: Activity, v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, a.resources.displayMetrics).toInt()

    /** Small version tag in the bottom-left corner; tapping it opens the changelog. */
    fun addVersionTag(activity: Activity, root: FrameLayout) {
        val tag = TextView(activity).apply {
            text = "NATIVE v${entries.first().version}"
            setTextColor(Color.argb(200, 226, 232, 240))
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(dp(activity, 10f), dp(activity, 6f), dp(activity, 10f), dp(activity, 6f))
            background = GradientDrawable().apply { setColor(Color.argb(150, 8, 10, 24)); cornerRadius = dp(activity, 8f).toFloat(); setStroke(dp(activity, 1f), Color.argb(120, 167, 139, 250)) }
            setOnClickListener { show(activity) }
        }
        val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START)
        lp.setMargins(dp(activity, 12f), 0, 0, dp(activity, 12f))
        root.addView(tag, lp)
    }

    fun show(activity: Activity) {
        val sb = SpannableStringBuilder()
        for ((i, e) in entries.withIndex()) {
            if (i > 0) sb.append("\n\n")
            val start = sb.length
            sb.append("v${e.version}  ")
            sb.setSpan(ForegroundColorSpan(Color.rgb(167, 139, 250)), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val t0 = sb.length
            sb.append(e.title.uppercase())
            sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(RelativeSizeSpan(1.1f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(ForegroundColorSpan(Color.rgb(248, 250, 252)), t0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            for (n in e.notes) sb.append("\n  · ").append(n)
        }
        val body = TextView(activity).apply {
            text = sb
            setTextColor(Color.rgb(203, 213, 225))
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setLineSpacing(0f, 1.2f)
            setPadding(dp(activity, 20f), dp(activity, 8f), dp(activity, 20f), dp(activity, 8f))
        }
        val scroll = ScrollView(activity).apply { addView(body) }
        AlertDialog.Builder(activity, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("PS1 Bedroom native: changelog")
            .setView(scroll)
            .setPositiveButton("Close", null)
            .show()
    }
}
