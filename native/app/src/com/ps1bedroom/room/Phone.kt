package com.ps1bedroom.room

import kotlin.math.sin
import kotlin.random.Random

/**
 * HTML 12928-13003: PHONE CALLS. The cordless phone rings now and then (every 15-35 minutes while the sound is
 * on, not in the middle of the night). Tap it to answer; miss it and the answering machine on the desk takes a
 * message (its red light blinks until you play it).
 */
class Phone(private val app: App) {
    private val CALLS = listOf(
        "Mom" to "Hi sweetie, I'm working late. There's leftover pizza in the fridge. Don't stay up too late!",
        "Wrong number" to "Is this Pizza Planet? ... No? ... Okay, sorry.",
        "Your best friend" to "Dude, did you beat the water temple yet? Call me back. Also bring my Memory Card tomorrow.",
        "Grandma" to "Just calling to say I love you. Did you get the \$5 I put in your birthday card?",
        "Telemarketer" to "Congratulations! You've been selected for a FREE cruise! Just press 1 to—",
        "The video store" to "Hi, this is Blockbusted Video. Your copy of \"The Matriks\" is 3 days late.",
        "Your crush" to "Hey... um... it's me. Call me back. Or don't. Whatever. Bye."
    )
    var ringing = false; private set
    private var until = 0f
    private var nextRing = 0f
    private var caller = CALLS[0]
    private val messages get() = app.extras.messages
    private val rnd = Random.Default

    /** "ring" = normal · "silent" = calls go straight to the answering machine, no ringing · "off" = nobody calls */
    var mode = "ring"
        set(v) { field = v; if (v != "ring" && ringing) stopRing() }

    private fun ring(t: Float) {
        if (mode == "off") return
        if (mode == "silent") { caller = CALLS[rnd.nextInt(CALLS.size)]; messages += caller; return }
        if (!app.sound.ready || !app.sound.playing || ringing) return
        if (!app.sound.ringPhone()) return
        ringing = true
        caller = CALLS[rnd.nextInt(CALLS.size)]
        until = t + 24
        app.toast("📞 The phone is ringing! Tap it to answer.")
    }
    private fun stopRing() { ringing = false; app.sound.stopRing() }
    /** Tap on the phone while it rings. */
    fun answer() {
        val (who, line) = caller
        stopRing()
        app.sfx("caseClick")
        app.toast("📞 $who: \"$line\"")
    }
    fun update(t: Float) {
        if (ringing && t > until) {
            stopRing()
            messages += caller
            app.toast("📟 The answering machine picked up. Its red light is blinking.")
        }
        // Calls come in every 15-35 minutes while the sound is on (not in the middle of the night)
        if (nextRing == 0f) nextRing = t + 600 + rnd.nextFloat() * 900
        val h = app.env.hour
        if (t > nextRing) {
            nextRing = t + 900 + rnd.nextFloat() * 1200
            if (!(h >= 0.5 && h < 7.5)) ring(t)
        }
    }
    /** Tap on the answering machine. */
    fun playMessages(handler: (Long, () -> Unit) -> Unit) {
        if (messages.isEmpty()) { app.sfx("caseClick"); app.toast("📟 \"You have... no... new messages.\""); return }
        val msgs = ArrayList(messages); messages.clear()
        app.sfx("caseClick")
        app.toast("📟 \"You have ${msgs.size} new message${if (msgs.size > 1) "s" else ""}.\"")
        msgs.forEachIndexed { i, (who, line) -> handler(4500L * (i + 1)) { app.toast("📟 $who: \"$line\" *beep*") } }
    }
    @Suppress("unused") private fun unused() = sin(0.0)
}
