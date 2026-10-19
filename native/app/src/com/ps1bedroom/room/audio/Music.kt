package com.ps1bedroom.room.audio

/**
 * HTML 9023-9517, 6743-6810, 7262-7533, 12911-12927: every piece of music in the room. The PC speaker channels, the
 * bedroom TV channels, the boombox stations, each game's soundtrack and the VCR tapes' music, ported note for note.
 * A builder takes the channel's output and a bag `N` that keeps everything it makes (disposed when the channel goes).
 */
class Bag(val ctx: AudioCtx) {
    private val items = ArrayList<Any>()
    fun <T : Any> n(x: T): T { items += x; return x }
    fun disposeAll() {
        for (i in items) when (i) { is Node -> i.dispose(); is Instrument -> i.dispose(); is Part<*> -> i.dispose(); is Lfo -> i.dispose() }
        items.clear()
    }
}
/** What a channel exposes to the periodic events (the HTML's api objects). */
class ChannelApi {
    var gun: NoiseSynth? = null; var boom: MembraneSynth? = null; var beep: Synth? = null; var busyUntil = 0.0
    var engine: Oscillator? = null; var horn: PolySynth<Synth>? = null; var clash: MetalSynth? = null; var shot: MembraneSynth? = null
    var droneFilter: Filter? = null; var sting: PolySynth<Synth>? = null; var host: Voice? = null
    var ps1 = false; var music: Gain? = null; var menu: Gain? = null
}
typealias Builder = (Node, Bag) -> ChannelApi

/** nativeLfo: a bare oscillator scaled onto a param around its midpoint. */
class Lfo(ctx: AudioCtx, param: Param, frequency: Double, min: Double, max: Double, type: String = "sine") {
    val osc = Oscillator(ctx, frequency, type).also { it.start(0.0) }
    private val depth = Gain(ctx, (max - min) / 2)
    init { param.cancelScheduledValues(0.0); param.value = (max + min) / 2; osc.connect(depth); depth.connect(param) }
    fun dispose() { osc.dispose(); depth.dispose() }
}

/** Formant "voice": sawtooth through two vowel band-passes, gated per syllable. */
class Voice(ctx: AudioCtx, dest: Node, val baseHz: Double, level: Double) {
    val osc = Oscillator(ctx, baseHz, "sawtooth").also { it.start(0.0) }
    val f1 = Filter(ctx, "bandpass", 700.0, 5.0)
    val f2 = Filter(ctx, "bandpass", 1200.0, 7.0)
    val env = Gain(ctx, 0.0)
    val out = Gain(ctx, level)
    var busyUntil = 0.0
    init { osc.fan(f1, f2); f1.connect(env); f2.connect(env); env.connect(out); out.connect(dest) }
    val parts: List<Node> get() = listOf(osc, f1, f2, env, out)
}

object Music {
    private fun filt(ctx: AudioCtx, frequency: Double = 1000.0, type: String = "lowpass", Q: Double = 1.0) = Filter(ctx, type, frequency, Q)
    fun bar(b: Int, beat: Int = 0, six: Int = 0) = "$b:$beat:$six"
    private fun <T> loopPart(N: Bag, ev: List<Pair<String, T>>, bars: Int, cb: (Double, T) -> Unit) { N.n(Part(N.ctx, ev, bars, cb)) }
    private fun env(ctx: AudioCtx, a: Double, d: Double, s: Double, r: Double) = Envelope(ctx, a, d, s, r)
    private fun synth(N: Bag, type: String, a: Double, d: Double, s: Double, r: Double, db: Double, count: Int = 3, spread: Double = 20.0): Synth =
        N.n(Synth(N.ctx, type, env(N.ctx, a, d, s, r), count, spread)).also { it.volume.value = db }
    private fun poly(N: Bag, type: String, a: Double, d: Double, s: Double, r: Double, db: Double, count: Int = 3, spread: Double = 20.0): PolySynth<Synth> =
        N.n(PolySynth(N.ctx) { Synth(N.ctx, type, env(N.ctx, a, d, s, r), count, spread) }).also { it.volume.value = db }
    private fun polyFm(N: Bag, harm: Double, idx: Double, a: Double, d: Double, s: Double, r: Double, ma: Double, md: Double, ms: Double, mr: Double, db: Double, modType: String = "square"): PolySynth<FMSynth> =
        N.n(PolySynth(N.ctx) { FMSynth(N.ctx, harm, idx, "sine", modType, env(N.ctx, a, d, s, r), env(N.ctx, ma, md, ms, mr)) }).also { it.volume.value = db }
    private fun membrane(N: Bag, pd: Double, oct: Double, a: Double, d: Double, s: Double, r: Double, db: Double) =
        N.n(MembraneSynth(N.ctx, pd, oct, env(N.ctx, a, d, s, r))).also { it.volume.value = db }
    private fun noiseS(N: Bag, type: String, a: Double, d: Double, s: Double, r: Double, db: Double) =
        N.n(NoiseSynth(N.ctx, type, env(N.ctx, a, d, s, r))).also { it.volume.value = db }
    private fun mono(N: Bag, type: String, q: Double, base: Double, oct: Double, fa: Double, fd: Double, fs: Double, fr: Double, a: Double, d: Double, s: Double, r: Double, db: Double, rolloff: Int = -24) =
        N.n(MonoSynth(N.ctx, type, q, "lowpass", rolloff, env(N.ctx, fa, fd, fs, fr), base, oct, env(N.ctx, a, d, s, r))).also { it.volume.value = db }
    private fun delay(N: Bag, t: String, fb: Double, wet: Double) = N.n(FeedbackDelay(N.ctx, N.ctx.transport.toSeconds(t), fb, wet))
    private fun F(N: Bag, f: Double, type: String = "lowpass", q: Double = 1.0) = N.n(filt(N.ctx, f, type, q))
    private fun G(N: Bag, g: Double) = N.n(Gain(N.ctx, g))
    private fun L(vararg s: String) = s.toList()
    private class E(val k: String, val n: Any? = null, val v: Double = 0.0, val soft: Boolean = false)
    private fun transpose(n: String, semis: Int) = AudioCtx.noteHz(n) * Math.pow(2.0, semis / 12.0)

    // ---------------- PC SPEAKER CHANNELS ----------------
    val PC_CHANNELS: List<Builder> = listOf(
        // CH0 · Winamp: chill 4-bar trance loop (Am - F - C - G)
        { out, N ->
            val pad = poly(N, "fatsawtooth", 0.5, 0.3, 0.7, 1.4, -24.0, 3, 18.0); pad.chain(F(N, 1500.0), out)
            val bass = mono(N, "square", 2.0, 110.0, 2.6, 0.005, 0.15, 0.2, 0.1, 0.005, 0.18, 0.3, 0.1, -17.0); bass.connect(out)
            val kick = membrane(N, 0.04, 5.0, 0.001, 0.3, 0.0, 0.05, -12.0); kick.connect(out)
            val hat = noiseS(N, "white", 0.001, 0.035, 0.0, 0.01, -30.0); hat.chain(F(N, 7000.0, "highpass"), out)
            val arp = synth(N, "square", 0.003, 0.12, 0.0, 0.05, -28.0); arp.chain(F(N, 2200.0), delay(N, "8n.", 0.25, 0.3), out)
            val chords = listOf(L("A3", "C4", "E4"), L("F3", "A3", "C4"), L("C4", "E4", "G4"), L("G3", "B3", "D4"))
            val arps = listOf(L("A4", "C5", "E5", "C5"), L("F4", "A4", "C5", "A4"), L("C5", "E5", "G5", "E5"), L("G4", "B4", "D5", "B4"))
            val roots = L("A1", "F1", "C2", "G1")
            val ev = ArrayList<Pair<String, E>>()
            for (b in 0 until 4) {
                ev += bar(b) to E("pad", chords[b])
                for (q in 0 until 4) { ev += bar(b, q) to E("kick"); ev += bar(b, q, 2) to E("hat"); ev += bar(b, q, 2) to E("bass", roots[b]) }
                for (s in 0 until 16) if (s % 4 != 3) ev += bar(b, s / 4, s % 4) to E("arp", arps[b][s % 4])
            }
            loopPart(N, ev, 4) { time, e ->
                when (e.k) { "pad" -> pad.triggerAttackRelease(e.n!!, "1m", time, 0.6); "kick" -> kick.triggerAttackRelease("C1", "8n", time, 0.8); "hat" -> hat.triggerAttackRelease("32n", time, 0.6)
                    "bass" -> bass.triggerAttackRelease(e.n!!, "16n", time, 0.8); else -> arp.triggerAttackRelease(e.n!!, "32n", time, 0.5) }
            }
            ChannelApi()
        },
        // CH1 · Counter-Strikeout: desert wind bed; gunfire, footsteps and bomb beeps are random events
        { out, N ->
            N.n(Noise(N.ctx, "brown").start() as Noise).chain(F(N, 520.0, "bandpass", 0.8), G(N, 0.4), out)
            val gun = noiseS(N, "white", 0.001, 0.09, 0.0, 0.03, -12.0); gun.chain(F(N, 2200.0), out)
            val boom = membrane(N, 0.02, 3.0, 0.001, 0.12, 0.0, 0.02, -12.0); boom.connect(out)
            val beep = synth(N, "sine", 0.002, 0.08, 0.0, 0.02, -26.0); beep.connect(out)
            ChannelApi().also { it.gun = gun; it.boom = boom; it.beep = beep }
        },
        // CH2 · Grand Theft Carts: idling muscle-car engine + funk on the car radio + the odd horn
        { out, N ->
            val engine = N.n(Oscillator(N.ctx, 46.0, "sawtooth").start() as Oscillator); engine.chain(F(N, 240.0), G(N, 0.14), out)
            val radio = G(N, 1.0); radio.chain(F(N, 650.0, "highpass"), F(N, 2600.0), out)
            val fbass = mono(N, "sawtooth", 3.0, 200.0, 3.0, 0.005, 0.1, 0.1, 0.1, 0.005, 0.15, 0.2, 0.05, -14.0); fbass.connect(radio)
            val clav = poly(N, "square", 0.002, 0.08, 0.0, 0.03, -24.0); clav.connect(radio)
            val snare = noiseS(N, "white", 0.001, 0.12, 0.0, 0.02, -20.0); snare.connect(radio)
            val kick = membrane(N, 0.03, 4.0, 0.001, 0.2, 0.0, 0.03, -12.0); kick.connect(radio)
            val horn = poly(N, "square", 0.01, 0.05, 0.9, 0.05, -26.0); horn.chain(F(N, 1400.0), out)
            val riff = listOf("E2" to 0, "E2" to 3, "G2" to 6, "A2" to 8, "B2" to 10, "D3" to 12, "B2" to 14)
            val ev = ArrayList<Pair<String, E>>()
            for (b in 0 until 2) {
                riff.forEach { (n, s) -> ev += bar(b, s / 4, s % 4) to E("bass", n) }
                intArrayOf(2, 6, 10, 14).forEach { s -> ev += bar(b, s / 4, s % 4) to E("clav") }
                intArrayOf(0, 7, 8).forEach { s -> ev += bar(b, s / 4, s % 4) to E("kick") }
                intArrayOf(4, 12).forEach { s -> ev += bar(b, s / 4, s % 4) to E("snare") }
            }
            loopPart(N, ev, 2) { time, e ->
                when (e.k) { "bass" -> fbass.triggerAttackRelease(e.n!!, "16n", time, 0.8); "clav" -> clav.triggerAttackRelease(L("E4", "G4", "D5"), "32n", time, 0.6)
                    "kick" -> kick.triggerAttackRelease("C1", "8n", time, 0.8); else -> snare.triggerAttackRelease("16n", time, 0.7) }
            }
            ChannelApi().also { it.engine = engine; it.horn = horn }
        },
        // CH3 · War-Crafty: war drums + low brass (Dm - Bb - C - Dm) + clashing steel
        { out, N ->
            val tom = membrane(N, 0.08, 2.5, 0.002, 0.45, 0.0, 0.1, -9.0); tom.connect(out)
            val brass = poly(N, "sawtooth", 0.14, 0.3, 0.6, 0.6, -24.0); brass.chain(F(N, 1100.0), out)
            val clash = N.n(MetalSynth(N.ctx, 8.0, 20.0, 3500.0, 1.0, env(N.ctx, 0.001, 0.25, 0.0, 0.1))).also { it.volume.value = -30.0 }; clash.connect(out)
            val drumHits = intArrayOf(1, 0, 1, 1, 0, 1, 0, 0)
            val chords = listOf(L("D3", "F3", "A3"), L("A#2", "D3", "F3"), L("C3", "E3", "G3"), L("D3", "F3", "A3"))
            val ev = ArrayList<Pair<String, E>>()
            for (b in 0 until 2) {
                drumHits.forEachIndexed { i, h -> if (h == 1) ev += bar(b, i / 2, (i % 2) * 2) to E("tom", if (i == 5) "D2" else "G1") }
                ev += bar(b, 0) to E("brass", chords[b * 2]); ev += bar(b, 2) to E("brass", chords[b * 2 + 1])
            }
            loopPart(N, ev, 2) { time, e -> if (e.k == "tom") tom.triggerAttackRelease(e.n!!, "8n", time, 0.8) else brass.triggerAttackRelease(e.n!!, "2n", time, 0.6) }
            ChannelApi().also { it.clash = clash }
        },
        // CH4 · Max Payne-Killer: slow D-minor piano, bowed cello, slow-mo gunshots
        { out, N ->
            val piano = poly(N, "triangle", 0.004, 1.6, 0.0, 1.2, -14.0); piano.chain(F(N, 3000.0), out)
            val cello = synth(N, "sawtooth", 0.7, 0.2, 0.8, 1.5, -24.0); cello.chain(F(N, 650.0), out)
            val shot = membrane(N, 0.45, 6.0, 0.002, 1.3, 0.0, 0.4, -16.0); shot.connect(out)
            val melody = listOf(Triple("D5", 0, 0), Triple("F5", 0, 2), Triple("E5", 0, 3), Triple("D5", 1, 0), Triple("A4", 1, 2), Triple("A#4", 2, 0), Triple("A4", 2, 2), Triple("G4", 2, 3), Triple("A4", 3, 0))
            val left = listOf(L("D3", "A3"), L("F3", "C4"), L("G2", "D3"), L("A2", "E3"))
            val ev = ArrayList<Pair<String, E>>()
            melody.forEach { (n, b, q) -> ev += bar(b, q) to E("mel", n) }
            left.forEachIndexed { b, n -> ev += bar(b) to E("left", n) }
            ev += bar(0) to E("cello", "D2"); ev += bar(2) to E("cello", "A1")
            loopPart(N, ev, 4) { time, e ->
                when (e.k) { "mel" -> piano.triggerAttackRelease(e.n!!, "2n", time, 0.5); "left" -> piano.triggerAttackRelease(e.n!!, "1m", time, 0.35); else -> cello.triggerAttackRelease(e.n!!, "2m", time, 0.6) }
            }
            ChannelApi().also { it.shot = shot }
        }
    )

    // ---------------- BEDROOM CRT TV CHANNELS ----------------
    private val vhsHorror: Builder = { out, N ->
        val droneFilter = F(N, 420.0); droneFilter.chain(G(N, 0.09), out)
        N.n(Oscillator(N.ctx, 55.0, "sawtooth").start() as Oscillator).connect(droneFilter)
        N.n(Oscillator(N.ctx, 55.7, "sawtooth").start() as Oscillator).connect(droneFilter)
        N.n(Noise(N.ctx, "white").start() as Noise).chain(F(N, 5000.0, "highpass"), G(N, 0.012), out)
        val sting = poly(N, "sawtooth", 0.3, 1.0, 0.4, 2.0, -24.0); sting.chain(F(N, 1500.0), out)
        val heart = membrane(N, 0.05, 2.0, 0.002, 0.25, 0.0, 0.05, -10.0); heart.connect(out)
        val ev = listOf(bar(0, 0) to false, bar(0, 0, 1) to true, bar(0, 2) to false, bar(0, 2, 1) to true)
        loopPart(N, ev, 1) { time, soft -> heart.triggerAttackRelease("A0", "16n", time, if (soft) 0.5 else 0.8) }
        ChannelApi().also { it.droneFilter = droneFilter; it.sting = sting }
    }
    private val cartoons: Builder = { out, N ->
        val xylo = poly(N, "triangle", 0.001, 0.18, 0.0, 0.1, -15.0); xylo.connect(out)
        val tuba = synth(N, "sawtooth", 0.02, 0.15, 0.4, 0.1, -16.0); tuba.chain(F(N, 700.0), out)
        val wood = membrane(N, 0.005, 1.0, 0.001, 0.05, 0.0, 0.02, -18.0); wood.connect(out)
        val whistle = synth(N, "sine", 0.02, 0.1, 0.8, 0.1, -22.0); whistle.connect(out)
        val run = L("C5", "D5", "E5", "G5", "A5", "G5", "E5", "D5", "C5", "E5", "G5", "C6", "B5", "G5", "F5", "D5")
        val tubaLine = L("C3", "G2", "C3", "G2", "F2", "C3", "G2", "G2")
        val ev = ArrayList<Pair<String, E>>()
        for (b in 0 until 2) {
            run.forEachIndexed { s, n -> ev += bar(b, s / 4, s % 4) to E("x", n) }
            for (q in 0 until 4) { ev += bar(b, q) to E("t", tubaLine[b * 4 + q]); ev += bar(b, q, 2) to E("w") }
        }
        ev += bar(1, 3, 2) to E("slide")
        loopPart(N, ev, 2) { time, e ->
            when (e.k) {
                "x" -> xylo.triggerAttackRelease(e.n!!, "16n", time, 0.55); "t" -> tuba.triggerAttackRelease(e.n!!, "8n", time, 0.7)
                "w" -> wood.triggerAttackRelease("G5", "32n", time, 0.4)
                else -> { whistle.triggerAttack(500.0, time, 0.6); whistle.frequency.exponentialRampToValueAtTime(1500.0, time + 0.35); whistle.triggerRelease(time + 0.38) }
            }
        }
        ChannelApi()
    }
    private val anime: Builder = { out, N ->
        val lead = synth(N, "square", 0.005, 0.15, 0.3, 0.1, -20.0); lead.chain(F(N, 3500.0), delay(N, "8n", 0.2, 0.2), out)
        val keys = poly(N, "triangle", 0.01, 0.3, 0.4, 0.3, -24.0); keys.connect(out)
        val bass = mono(N, "sawtooth", 1.0, 180.0, 2.0, 0.005, 0.1, 0.3, 0.1, 0.005, 0.1, 0.5, 0.05, -18.0); bass.connect(out)
        val snare = noiseS(N, "white", 0.001, 0.14, 0.0, 0.02, -20.0); snare.chain(F(N, 1800.0, "bandpass", 0.8), out)
        val kick = membrane(N, 0.03, 4.0, 0.001, 0.2, 0.0, 0.03, -14.0); kick.connect(out)
        val chords = listOf(L("F3", "A3", "C4"), L("G3", "B3", "D4"), L("E3", "G3", "B3"), L("A3", "C4", "E4"))
        val roots = L("F3", "G3", "E3", "A3")
        val mel = listOf(listOf("E5", "E5", "D5", "C5", "D5", null, "E5", "G5"), listOf("D5", null, "B4", null, "G4", "A4", "B4", "D5"),
            listOf("E5", "E5", "D5", "C5", "B4", null, "G4", "B4"), listOf("A4", null, null, null, "C5", "B4", "A4", null))
        val ev = ArrayList<Pair<String, E>>()
        for (b in 0 until 4) {
            ev += bar(b, 0) to E("keys", chords[b]); ev += bar(b, 2, 2) to E("keys", chords[b])
            for (e in 0 until 8) { mel[b][e]?.let { ev += bar(b, e / 2, (e % 2) * 2) to E("lead", it) }; ev += bar(b, e / 2, (e % 2) * 2) to E("bass", roots[b]) }
            intArrayOf(0, 2).forEach { q -> ev += bar(b, q) to E("kick") }
            intArrayOf(1, 3).forEach { q -> ev += bar(b, q) to E("snare") }
        }
        loopPart(N, ev, 4) { time, e ->
            when (e.k) { "lead" -> lead.triggerAttackRelease(e.n!!, "8n", time, 0.7); "keys" -> keys.triggerAttackRelease(e.n!!, "4n", time, 0.5)
                "bass" -> bass.triggerAttackRelease(e.n!!, "16n", time, 0.7); "kick" -> kick.triggerAttackRelease("C1", "8n", time, 0.8); else -> snare.triggerAttackRelease("16n", time, 0.6) }
        }
        ChannelApi()
    }
    private val popPunkVideos: Builder = { out, N ->
        val gtr = poly(N, "fatsawtooth", 0.005, 0.2, 0.5, 0.08, -22.0, 2, 12.0); gtr.chain(F(N, 2400.0), out)
        val bass = synth(N, "square", 0.005, 0.12, 0.4, 0.05, -18.0); bass.chain(F(N, 600.0), out)
        val kick = membrane(N, 0.03, 4.0, 0.001, 0.2, 0.0, 0.03, -12.0); kick.connect(out)
        val snare = noiseS(N, "white", 0.001, 0.13, 0.0, 0.02, -19.0); snare.connect(out)
        val roots = listOf(L("E3", "B3"), L("C3", "G3"), L("G3", "D4"), L("D3", "A3"))
        val bassN = L("E2", "C2", "G2", "D2")
        val ev = ArrayList<Pair<String, E>>()
        for (b in 0 until 4) {
            for (e in 0 until 8) { ev += bar(b, e / 2, (e % 2) * 2) to E("g", roots[b]); ev += bar(b, e / 2, (e % 2) * 2) to E("b", bassN[b]) }
            intArrayOf(0, 2).forEach { q -> ev += bar(b, q) to E("k") }
            ev += bar(b, 2, 2) to E("k")
            intArrayOf(1, 3).forEach { q -> ev += bar(b, q) to E("s") }
        }
        loopPart(N, ev, 4) { time, e ->
            when (e.k) { "g" -> gtr.triggerAttackRelease(e.n!!, "16n", time, 0.6); "b" -> bass.triggerAttackRelease(e.n!!, "16n", time, 0.7)
                "k" -> kick.triggerAttackRelease("C1", "8n", time, 0.85); else -> snare.triggerAttackRelease("16n", time, 0.7) }
        }
        ChannelApi()
    }
    /** The TV channel builders; index 2 (the VCR) plays whichever tape is in; the last one is the PlayStation. */
    fun tvChannel(index: Int, variant: Int, gameMusic: String?): Builder = when (index) {
        0 -> anime
        1 -> { out, N ->
            // CH12 · Weather channel: smooth-jazz Rhodes (with tremolo), walking bass and brushes
            val trem = G(N, 1.0); trem.connect(out)
            N.n(Lfo(N.ctx, trem.gain, 4.0, 0.7, 1.0))
            val rhodes = polyFm(N, 3.01, 1.6, 0.005, 1.4, 0.25, 1.2, 0.002, 0.4, 0.1, 0.5, -14.0, "sine"); rhodes.connect(trem)
            val wbass = synth(N, "triangle", 0.01, 0.4, 0.5, 0.2, -12.0); wbass.connect(out)
            val brush = noiseS(N, "pink", 0.02, 0.18, 0.0, 0.05, -30.0); brush.chain(F(N, 5000.0), out)
            val chords = listOf(L("F3", "A3", "C4", "E4"), L("F3", "B3", "E4", "A4"), L("E3", "G3", "B3", "D4"), L("G3", "C#4", "F4"))
            val walk = listOf(L("D3", "F3", "A3", "C4"), L("G2", "B2", "D3", "F3"), L("C3", "E3", "G3", "B3"), L("A2", "C#3", "E3", "G3"))
            val ev = ArrayList<Pair<String, E>>()
            for (b in 0 until 4) {
                ev += bar(b, 0) to E("rh", chords[b], 0.55); ev += bar(b, 2, 2) to E("rh", chords[b], 0.3)
                for (q in 0 until 4) { ev += bar(b, q) to E("bass", walk[b][q]); ev += bar(b, q) to E("brush", null, if (q % 2 == 1) 0.7 else 0.35) }
            }
            loopPart(N, ev, 4) { time, e ->
                when (e.k) { "rh" -> rhodes.triggerAttackRelease(e.n!!, "2n", time, e.v); "bass" -> wbass.triggerAttackRelease(e.n!!, "4n", time, 0.7); else -> brush.triggerAttackRelease("8n", time, e.v) }
            }
            ChannelApi()
        }
        2 -> when (variant) { 1 -> cartoons; 2 -> GAME.getValue("hero"); 3 -> ocean; else -> vhsHorror }
        3 -> { out, N ->
            // CH24 · Midnight shopping network: cheesy marimba jingle under a talking host
            val marimba = poly(N, "sine", 0.002, 0.35, 0.0, 0.2, -17.0); marimba.connect(out)
            val mbass = synth(N, "triangle", 0.005, 0.3, 0.3, 0.1, -20.0); mbass.connect(out)
            val mel = listOf(listOf("C5", "E5", "G5", "E5", "F5", "A5", "G5", "E5"), listOf("D5", "F5", "E5", "C5", "D5", "B4", "C5", null))
            val bassN = listOf(L("C3", "G3"), L("F3", "G3"))
            val ev = ArrayList<Pair<String, E>>()
            for (b in 0 until 2) {
                mel[b].forEachIndexed { e, n -> if (n != null) ev += bar(b, e / 2, (e % 2) * 2) to E("m", n) }
                ev += bar(b, 0) to E("b", bassN[b][0]); ev += bar(b, 2) to E("b", bassN[b][1])
            }
            loopPart(N, ev, 2) { time, e -> if (e.k == "m") marimba.triggerAttackRelease(e.n!!, "8n", time, 0.5) else mbass.triggerAttackRelease(e.n!!, "2n", time, 0.6) }
            val host = Voice(N.ctx, out, 135.0, 6.0); host.parts.forEach { N.n(it) }
            ChannelApi().also { it.host = host }
        }
        4 -> { out, N ->
            // CH99 · No-signal static: band-passed white noise + crackle
            N.n(Noise(N.ctx, "white").start() as Noise).chain(F(N, 2600.0, "bandpass", 0.4), G(N, 0.1), out)
            val crackle = N.n(Player(N.ctx, staticCrackle(N.ctx))); crackle.loop = true
            crackle.chain(F(N, 3000.0, "highpass"), G(N, 0.3), out); crackle.start()
            ChannelApi()
        }
        5 -> cartoons
        6 -> popPunkVideos
        else -> ps1(gameMusic)
    }
    private var crackleBuf: AudioBuffer? = null
    private fun staticCrackle(ctx: AudioCtx) = crackleBuf ?: Buffers.ticks(ctx, 3.0, 30.0, 0.001, 0.004, 3.0).also { crackleBuf = it }

    /** AV 1 · PlayStation: each game's soundtrack once the boot finishes, a soft pad on the system menu. */
    private fun ps1(gameMusic: String?): Builder = { out, N ->
        val music = G(N, 0.0); music.connect(out)
        val menu = G(N, 0.0); menu.connect(out)
        val other = gameMusicBuilder(gameMusic)
        if (other != null) other(music, N)
        else {
            val marimba = poly(N, "sine", 0.002, 0.25, 0.0, 0.15, -15.0); marimba.connect(music)
            val bass = synth(N, "triangle", 0.005, 0.2, 0.3, 0.08, -11.0); bass.connect(music)
            val bongo = membrane(N, 0.008, 1.5, 0.001, 0.12, 0.0, 0.03, -16.0); bongo.connect(music)
            val shaker = noiseS(N, "white", 0.002, 0.04, 0.0, 0.01, -30.0); shaker.chain(F(N, 6000.0, "highpass"), music)
            val lead = synth(N, "square", 0.005, 0.1, 0.25, 0.08, -25.0); lead.chain(F(N, 3000.0), music)
            val chords = listOf(L("G4", "B4", "D5"), L("E4", "G4", "B4"), L("C4", "E4", "G4"), L("D4", "F#4", "A4"))
            val roots = L("G3", "E3", "C3", "D3")
            val arp = intArrayOf(0, 1, 2, 1, 0, 2, 1, 2)
            val leadLine = mapOf(1 to L("B5", "A5", "G5", "E5"), 3 to L("A5", "B5", "D6", "B5"))
            val ev = ArrayList<Pair<String, E>>()
            for (b in 0 until 4) {
                arp.forEachIndexed { e, ci -> ev += bar(b, e / 2, (e % 2) * 2) to E("m", chords[b][ci]) }
                intArrayOf(0, 3, 6).forEach { e -> ev += bar(b, e / 2, (e % 2) * 2) to E("b", roots[b]) }
                listOf(0 to "A3", 3 to "D4", 4 to "A3", 6 to "D4", 7 to "D4").forEach { (e, n) -> ev += bar(b, e / 2, (e % 2) * 2) to E("g", n) }
                intArrayOf(1, 3, 5, 7).forEach { e -> ev += bar(b, e / 2, (e % 2) * 2) to E("s") }
                leadLine[b]?.forEachIndexed { q, n -> ev += bar(b, q) to E("l", n) }
            }
            loopPart(N, ev, 4) { time, e ->
                when (e.k) { "m" -> marimba.triggerAttackRelease(e.n!!, "16n", time, 0.6); "b" -> bass.triggerAttackRelease(e.n!!, "8n", time, 0.7)
                    "g" -> bongo.triggerAttackRelease(e.n!!, "16n", time, 0.6); "s" -> shaker.triggerAttackRelease("32n", time, 0.5); else -> lead.triggerAttackRelease(e.n!!, "8n", time, 0.5) }
            }
        }
        // System menu: slow shimmering pad (Cmaj7 -> Am9)
        val pad = poly(N, "sine", 1.2, 0.5, 0.7, 2.0, -18.0); pad.connect(menu)
        loopPart(N, listOf(bar(0) to L("C4", "E4", "G4", "B4"), bar(1) to L("A3", "C4", "E4", "G4", "B4")), 2) { time, notes -> pad.triggerAttackRelease(notes, "1m", time, 0.5) }
        ChannelApi().also { it.ps1 = true; it.music = music; it.menu = menu }
    }

    // ---------------- BOOMBOX RADIO STATIONS ----------------
    private val slowJams: Builder = { out, N ->
        val keys = polyFm(N, 2.01, 1.2, 0.01, 1.2, 0.3, 1.0, 0.005, 0.3, 0.1, 0.4, -16.0)
        keys.connect(out)
        val sub = synth(N, "sine", 0.01, 0.4, 0.6, 0.3, -10.0); sub.connect(out)
        val kick = membrane(N, 0.08, 5.0, 0.001, 0.45, 0.0, 0.1, -12.0); kick.connect(out)
        val clap = noiseS(N, "pink", 0.002, 0.16, 0.0, 0.05, -20.0); clap.chain(F(N, 1500.0, "bandpass", 0.8), out)
        val hat = noiseS(N, "white", 0.001, 0.03, 0.0, 0.01, -32.0); hat.chain(F(N, 7000.0, "highpass"), out)
        val chords = listOf(L("F3", "A3", "C4", "E4"), L("E3", "G3", "B3", "D4"), L("D3", "F3", "A3", "C4"), L("C3", "E3", "G3", "B3"))
        val bassN = L("F1", "E1", "D1", "C2")
        val ev = ArrayList<Pair<String, E>>()
        for (b in 0 until 4) {
            ev += bar(b, 0) to E("c", chords[b]); ev += bar(b, 1, 3) to E("c", chords[b], soft = true)
            ev += bar(b, 0) to E("sub", bassN[b]); ev += bar(b, 2, 2) to E("sub", bassN[b])
            listOf(0 to 0, 1 to 2, 2 to 3).forEach { (q, s) -> ev += bar(b, q, s) to E("k") }
            intArrayOf(1, 3).forEach { q -> ev += bar(b, q) to E("cl") }
            for (e in 0 until 8) ev += bar(b, e / 2, (e % 2) * 2 + (e % 2)) to E("h")
        }
        loopPart(N, ev, 4) { time, e ->
            when (e.k) { "c" -> keys.triggerAttackRelease(e.n!!, "2n", time, if (e.soft) 0.3 else 0.5); "sub" -> sub.triggerAttackRelease(e.n!!, "4n", time, 0.8)
                "k" -> kick.triggerAttackRelease("A0", "8n", time, 0.8); "cl" -> clap.triggerAttackRelease("16n", time, 0.6); else -> hat.triggerAttackRelease("32n", time, 0.5) }
        }
        ChannelApi()
    }
    private val popPunkRadio: Builder = { out, N ->
        val gtr = poly(N, "fatsawtooth", 0.004, 0.15, 0.5, 0.06, -20.0, 2, 14.0); gtr.chain(F(N, 2800.0), out)
        val bass = synth(N, "square", 0.004, 0.1, 0.4, 0.04, -16.0); bass.chain(F(N, 700.0), out)
        val kick = membrane(N, 0.03, 4.0, 0.001, 0.18, 0.0, 0.03, -11.0); kick.connect(out)
        val snare = noiseS(N, "white", 0.001, 0.12, 0.0, 0.02, -18.0); snare.connect(out)
        val roots = listOf(L("A3", "E4"), L("F3", "C4"), L("C4", "G4"), L("G3", "D4"))
        val bassN = L("A2", "F2", "C3", "G2")
        val ev = ArrayList<Pair<String, E>>()
        for (b in 0 until 4) {
            var s = 0
            while (s < 16) { ev += bar(b, s / 4, s % 4) to E("g", roots[b]); ev += bar(b, s / 4, s % 4) to E("b", bassN[b]); s += 2 }
            for (q in 0 until 4) ev += bar(b, q) to E(if (q % 2 == 1) "s" else "k")
            ev += bar(b, 1, 2) to E("k")
        }
        loopPart(N, ev, 4) { time, e ->
            when (e.k) { "g" -> gtr.triggerAttackRelease(e.n!!, "16n", time, 0.65); "b" -> bass.triggerAttackRelease(e.n!!, "16n", time, 0.7)
                "k" -> kick.triggerAttackRelease("C1", "8n", time, 0.85); else -> snare.triggerAttackRelease("16n", time, 0.75) }
        }
        ChannelApi()
    }
    val RADIO_CHANNELS: List<Builder> = listOf(slowJams, popPunkRadio)

    // ---------------- Game soundtracks ----------------
    private fun drumKit(out: Node, N: Bag, kickDb: Double = -10.0, snareDb: Double = -14.0, hatDb: Double = -28.0): Triple<MembraneSynth, NoiseSynth, NoiseSynth> {
        val kick = membrane(N, 0.05, 5.0, 0.001, 0.35, 0.0, 0.05, kickDb); kick.connect(out)
        val snare = noiseS(N, "white", 0.001, 0.16, 0.0, 0.04, snareDb); snare.chain(F(N, 1900.0, "bandpass", 0.7), out)
        val hat = noiseS(N, "white", 0.001, 0.03, 0.0, 0.01, hatDb); hat.chain(F(N, 8000.0, "highpass"), out)
        return Triple(kick, snare, hat)
    }
    private val ocean: Builder = { out, N ->
        val pad = poly(N, "sine", 2.5, 1.0, 0.8, 4.0, -18.0); pad.connect(out)
        val waves = N.n(Noise(N.ctx, "pink")); val wf = F(N, 700.0); val wg = G(N, 0.0)
        waves.connect(wf); wf.connect(wg); wg.connect(out); waves.start()
        val ev = ArrayList<Pair<String, E>>()
        listOf(L("D3", "A3", "E4"), L("B2", "F#3", "D4"), L("G2", "D3", "B3"), L("A2", "E3", "C#4")).forEachIndexed { b, c -> ev += bar(b, 0) to E("p", c); ev += bar(b, 0) to E("w"); ev += bar(b, 2) to E("w") }
        loopPart(N, ev, 4) { time, e ->
            if (e.k == "p") pad.triggerAttackRelease(e.n!!, "1m", time, 0.35)
            else { wg.gain.setValueAtTime(0.02, time); wg.gain.linearRampToValueAtTime(0.35, time + 1.6); wg.gain.linearRampToValueAtTime(0.02, time + 3.4) }
        }
        ChannelApi()
    }
    val GAME: Map<String, Builder> = mapOf(
        // Celesta-style waltz in E minor (3-beat feel inside 4/4 bars)
        "magic" to { out, N ->
            val cel = poly(N, "sine", 0.002, 0.6, 0.0, 0.4, -14.0); cel.chain(delay(N, "8n", 0.25, 0.25), out)
            val strings = poly(N, "triangle", 0.5, 0.4, 0.7, 1.2, -22.0); strings.connect(out)
            val line = L("B4", "E5", "G5", "F#5", "E5", "B5", "A5", "F#5", "E5", "G5", "F#5", "D#5", "F5", "B4")
            val ev = ArrayList<Pair<String, E>>()
            line.forEachIndexed { i, n -> ev += "0:0:${i * 3}" to E("c", n) }
            ev += "0:0:0" to E("s", L("E3", "G3", "B3")); ev += "1:0:0" to E("s", L("C3", "E3", "G3")); ev += "2:0:0" to E("s", L("A2", "C3", "E3"))
            loopPart(N, ev, 3) { time, e -> if (e.k == "c") cel.triggerAttackRelease(e.n!!, "8n", time, 0.5) else strings.triggerAttackRelease(e.n!!, "1m", time, 0.4) }
            ChannelApi()
        },
        // Heroic brass ostinato over driving toms
        "hero" to { out, N ->
            val brass = poly(N, "sawtooth", 0.03, 0.2, 0.6, 0.2, -22.0); brass.chain(F(N, 1800.0), out)
            val tom = membrane(N, 0.05, 3.0, 0.001, 0.25, 0.0, 0.05, -11.0); tom.connect(out)
            val hits = listOf(L("D4", "F4", "A4"), L("C4", "E4", "G4"), L("A#3", "D4", "F4"), L("C4", "E4", "A4"))
            val ev = ArrayList<Pair<String, E>>()
            for (b in 0 until 4) {
                intArrayOf(0, 3, 6).forEach { e -> ev += bar(b, e / 2, (e % 2) * 2) to E("b", hits[b]) }
                for (e in 0 until 8) ev += bar(b, e / 2, (e % 2) * 2) to E("t", if (e % 3 == 0) "D2" else "A1")
            }
            loopPart(N, ev, 4) { time, e -> if (e.k == "b") brass.triggerAttackRelease(e.n!!, "8n", time, 0.6) else tom.triggerAttackRelease(e.n!!, "16n", time, 0.6) }
            ChannelApi()
        },
        // Synthwave (Vice City / VCS / VI)
        "synthwave" to { out, N ->
            val bass = synth(N, "sawtooth", 0.005, 0.15, 0.3, 0.08, -17.0); bass.chain(F(N, 900.0), out)
            val pad = poly(N, "fatsawtooth", 0.4, 0.3, 0.7, 1.2, -27.0, 3, 22.0); pad.chain(F(N, 2200.0), out)
            val lead = synth(N, "square", 0.01, 0.2, 0.2, 0.2, -25.0); lead.chain(delay(N, "8n", 0.3, 0.25), out)
            val kick = membrane(N, 0.04, 5.0, 0.001, 0.3, 0.0, 0.05, -11.0); kick.connect(out)
            val snare = noiseS(N, "white", 0.001, 0.22, 0.0, 0.05, -18.0); snare.chain(F(N, 1800.0, "bandpass", 0.6), out)
            val roots = L("A1", "F1", "C2", "G1")
            val chords = listOf(L("A3", "C4", "E4"), L("F3", "A3", "C4"), L("C4", "E4", "G4"), L("G3", "B3", "D4"))
            val melody = L("E5", "D5", "C5", "D5", "E5", "G5", "E5", "D5")
            val ev = ArrayList<Pair<String, E>>()
            for (b in 0 until 4) {
                ev += bar(b, 0) to E("p", chords[b])
                for (e in 0 until 8) ev += bar(b, e / 2, (e % 2) * 2) to E("b", if (e % 2 == 1) transpose(roots[b], 12) else roots[b])
                intArrayOf(0, 2).forEach { q -> ev += bar(b, q) to E("k") }
                intArrayOf(1, 3).forEach { q -> ev += bar(b, q) to E("s") }
                if (b % 2 == 1) melody.forEachIndexed { i, n -> ev += bar(b, i / 2, (i % 2) * 2) to E("l", n) }
            }
            loopPart(N, ev, 4) { time, e ->
                when (e.k) { "p" -> pad.triggerAttackRelease(e.n!!, "1m", time, 0.4); "b" -> bass.triggerAttackRelease(e.n!!, "16n", time, 0.7)
                    "k" -> kick.triggerAttackRelease("A0", "8n", time, 0.9); "s" -> snare.triggerAttackRelease("8n", time, 0.7); else -> lead.triggerAttackRelease(e.n!!, "16n", time, 0.4) }
            }
            ChannelApi()
        },
        // G-funk (San Andreas)
        "gfunk" to { out, N ->
            val bass = mono(N, "sine", 6.0, 200.0, 2.0, 0.01, 0.2, 0.5, 2.0, 0.01, 0.3, 0.6, 0.2, -9.0); bass.connect(out)
            val whine = synth(N, "sine", 0.05, 0.2, 0.8, 0.4, -22.0).also { it.portamento = 0.12 }; whine.chain(N.n(Vibrato(N.ctx, 5.0, 0.15)), out)
            val keys = polyFm(N, 2.0, 1.5, 0.01, 0.8, 0.2, 0.6, 0.5, 0.0, 1.0, 0.5, -20.0); keys.connect(out)
            val kick = membrane(N, 0.06, 4.0, 0.001, 0.4, 0.0, 0.05, -10.0); kick.connect(out)
            val clap = noiseS(N, "pink", 0.002, 0.15, 0.0, 0.05, -17.0); clap.chain(F(N, 1400.0, "bandpass", 0.8), out)
            val hat = noiseS(N, "white", 0.001, 0.03, 0.0, 0.01, -30.0); hat.chain(F(N, 7500.0, "highpass"), out)
            val bassLine = listOf(Triple("G1", 0, 0), Triple("G1", 0, 3), Triple("A#1", 1, 2), Triple("C2", 2, 0), Triple("D2", 2, 3), Triple("F1", 3, 2))
            val ev = ArrayList<Pair<String, E>>()
            for (b in 0 until 2) {
                bassLine.forEach { (n, q, s) -> ev += bar(b, q, s) to E("b", n) }
                ev += bar(b, 0) to E("c", L("G3", "A#3", "D4", "F4"))
                listOf(0 to 0, 1 to 3, 2 to 2).forEach { (q, s) -> ev += bar(b, q, s) to E("k") }
                intArrayOf(1, 3).forEach { q -> ev += bar(b, q) to E("cl") }
                for (e in 0 until 8) ev += bar(b, e / 2, (e % 2) * 3) to E("h")
            }
            listOf(Triple("D5", 0, 0), Triple("F5", 0, 3), Triple("G5", 1, 2), Triple("F5", 2, 2), Triple("D5", 3, 0)).forEach { (n, q, s) -> ev += bar(1, q, s) to E("w", n) }
            loopPart(N, ev, 2) { time, e ->
                when (e.k) { "b" -> bass.triggerAttackRelease(e.n!!, "8n", time, 0.8); "c" -> keys.triggerAttackRelease(e.n!!, "2n", time, 0.4); "k" -> kick.triggerAttackRelease("G0", "8n", time, 0.9)
                    "cl" -> clap.triggerAttackRelease("16n", time, 0.7); "h" -> hat.triggerAttackRelease("32n", time, 0.4); else -> whine.triggerAttackRelease(e.n!!, "8n", time, 0.5) }
            }
            ChannelApi()
        },
        // Boom-bap (GTA, III, the Stories, IV, V, Chinatown Wars)
        "gtahiphop" to { out, N ->
            val piano = poly(N, "triangle", 0.003, 0.9, 0.1, 0.6, -16.0); piano.chain(F(N, 2600.0), out)
            val strings = poly(N, "sawtooth", 0.6, 0.3, 0.8, 1.5, -30.0); strings.chain(F(N, 1400.0), out)
            val sub = synth(N, "sine", 0.01, 0.5, 0.4, 0.3, -10.0); sub.connect(out)
            val kick = membrane(N, 0.05, 4.0, 0.001, 0.35, 0.0, 0.05, -9.0); kick.connect(out)
            val snare = noiseS(N, "brown", 0.001, 0.18, 0.0, 0.05, -12.0); snare.chain(F(N, 2200.0, "bandpass", 0.7), out)
            val hat = noiseS(N, "white", 0.001, 0.04, 0.0, 0.01, -29.0); hat.chain(F(N, 8000.0, "highpass"), out)
            val loop = listOf(Triple("D4", 0, 0), Triple("F4", 0, 2), Triple("A4", 1, 0), Triple("C5", 1, 3), Triple("A#4", 2, 2), Triple("A4", 3, 0), Triple("F4", 3, 2))
            val ev = ArrayList<Pair<String, E>>()
            for (b in 0 until 2) {
                loop.forEach { (n, q, s) -> ev += bar(b, q, s) to E("p", n) }
                ev += bar(b, 0) to E("st", if (b == 1) L("A#2", "D3", "F3") else L("D3", "F3", "A3"))
                ev += bar(b, 0) to E("sub", if (b == 1) "A#1" else "D2"); ev += bar(b, 2, 2) to E("sub", if (b == 1) "A1" else "D2")
                listOf(0 to 0, 0 to 3, 2 to 1).forEach { (q, s) -> ev += bar(b, q, s) to E("k") }
                intArrayOf(1, 3).forEach { q -> ev += bar(b, q) to E("s") }
                for (e in 0 until 8) ev += bar(b, e / 2, (e % 2) * 2) to E("h")
            }
            loopPart(N, ev, 2) { time, e ->
                when (e.k) { "p" -> piano.triggerAttackRelease(e.n!!, "8n", time, 0.6); "st" -> strings.triggerAttackRelease(e.n!!, "1m", time, 0.35); "sub" -> sub.triggerAttackRelease(e.n!!, "4n", time, 0.8)
                    "k" -> kick.triggerAttackRelease("C1", "8n", time, 0.95); "s" -> snare.triggerAttackRelease("8n", time, 0.8); else -> hat.triggerAttackRelease("32n", time, 0.45) }
            }
            ChannelApi()
        },
        // 80s pop station (Vice City)
        "flashpop" to { out, N ->
            val (kick, snare, hat) = drumKit(out, N, -10.0, -12.0, -30.0)
            val arp = synth(N, "triangle", 0.005, 0.15, 0.2, 0.1, -18.0); arp.chain(delay(N, "8n.", 0.3, 0.3), out)
            val lead = synth(N, "sawtooth", 0.02, 0.2, 0.5, 0.3, -24.0); lead.chain(F(N, 2600.0), out)
            val bass = synth(N, "square", 0.005, 0.1, 0.5, 0.05, -17.0); bass.chain(F(N, 800.0), out)
            val chords = listOf(L("C4", "E4", "G4"), L("A3", "C4", "E4"), L("F3", "A3", "C4"), L("G3", "B3", "D4"))
            val roots = L("C2", "A1", "F1", "G1")
            val tune = L("E5", "G5", "A5", "G5", "E5", "D5", "C5", "D5")
            val ev = ArrayList<Pair<String, E>>()
            for (b in 0 until 4) {
                for (s in 0 until 8) ev += bar(b, s / 2, (s % 2) * 2) to E("a", chords[b][s % 3])
                for (s in 0 until 4) ev += bar(b, s) to E("b", roots[b])
                intArrayOf(0, 2).forEach { q -> ev += bar(b, q) to E("k") }
                intArrayOf(1, 3).forEach { q -> ev += bar(b, q) to E("s") }
                for (s in 0 until 8) ev += bar(b, s / 2, (s % 2) * 2) to E("h")
                if (b >= 2) for (q in 0 until 4) ev += bar(b, q) to E("l", tune[(b - 2) * 4 + q])
            }
            loopPart(N, ev, 4) { time, e ->
                when (e.k) { "a" -> arp.triggerAttackRelease(e.n!!, "16n", time, 0.5); "b" -> bass.triggerAttackRelease(e.n!!, "8n", time, 0.7); "k" -> kick.triggerAttackRelease("C1", "8n", time, 0.9)
                    "s" -> snare.triggerAttackRelease("8n", time, 0.8); "h" -> hat.triggerAttackRelease("32n", time, 0.4); else -> lead.triggerAttackRelease(e.n!!, "4n", time, 0.5) }
            }
            ChannelApi()
        },
        // Electro station (GTA IV)
        "electro" to { out, N ->
            val (kick, snare, hat) = drumKit(out, N, -9.0, -16.0, -26.0)
            val acid = mono(N, "sawtooth", 6.0, 180.0, 3.5, 0.005, 0.12, 0.1, 2.0, 0.005, 0.15, 0.2, 0.05, -16.0); acid.connect(out)
            val stab = poly(N, "square", 0.002, 0.1, 0.0, 0.05, -24.0); stab.connect(out)
            val line = L("A1", "A1", "A2", "A1", "C2", "A1", "G2", "E2")
            val ev = ArrayList<Pair<String, E>>()
            for (b in 0 until 2) {
                for (s in 0 until 16) ev += bar(b, s / 4, s % 4) to E("a", line[s % 8])
                for (q in 0 until 4) { ev += bar(b, q) to E("k"); ev += bar(b, q, 2) to E("h") }
                intArrayOf(1, 3).forEach { q -> ev += bar(b, q) to E("s") }
                ev += bar(b, 1, 2) to E("st", L("A3", "C4", "E4"))
            }
            loopPart(N, ev, 2) { time, e ->
                when (e.k) { "a" -> acid.triggerAttackRelease(e.n!!, "16n", time, 0.7); "k" -> kick.triggerAttackRelease("A0", "8n", time, 0.95); "h" -> hat.triggerAttackRelease("32n", time, 0.5)
                    "s" -> snare.triggerAttackRelease("16n", time, 0.6); else -> stab.triggerAttackRelease(e.n!!, "16n", time, 0.6) }
            }
            ChannelApi()
        },
        // Modern pop station (GTA V)
        "nonstoppop" to { out, N ->
            val (kick, snare, hat) = drumKit(out, N, -8.0, -13.0, -28.0)
            val pad = poly(N, "fatsawtooth", 0.01, 0.3, 0.5, 0.4, -26.0, 3, 20.0); pad.chain(F(N, 3000.0), out)
            val pluck = synth(N, "triangle", 0.002, 0.2, 0.0, 0.1, -16.0); pluck.connect(out)
            val sub = synth(N, "sine", 0.01, 0.3, 0.6, 0.2, -11.0); sub.connect(out)
            val chords = listOf(L("F3", "A3", "C4", "E4"), L("D3", "F3", "A3", "C4"), L("A#2", "D3", "F3", "A3"), L("C3", "E3", "G3", "B3"))
            val roots = L("F1", "D2", "A#1", "C2")
            val hook = L("C5", "A4", "C5", "D5", "C5", "A4", "G4", "A4")
            val ev = ArrayList<Pair<String, E>>()
            for (b in 0 until 4) {
                for (q in 0 until 4) ev += bar(b, q, 1) to E("p", chords[b])
                ev += bar(b, 0) to E("sub", roots[b])
                for (q in 0 until 4) ev += bar(b, q) to E("k")
                intArrayOf(1, 3).forEach { q -> ev += bar(b, q) to E("s") }
                for (s in 0 until 8) ev += bar(b, s / 2, (s % 2) * 2 + 1) to E("h")
                hook.forEachIndexed { i, n -> ev += bar(b, i / 2, (i % 2) * 2) to E("l", n) }
            }
            loopPart(N, ev, 4) { time, e ->
                when (e.k) { "p" -> pad.triggerAttackRelease(e.n!!, "8n", time, 0.4); "sub" -> sub.triggerAttackRelease(e.n!!, "1m", time, 0.8); "k" -> kick.triggerAttackRelease("C1", "8n", time, 0.95)
                    "s" -> snare.triggerAttackRelease("8n", time, 0.75); "h" -> hat.triggerAttackRelease("32n", time, 0.4); else -> pluck.triggerAttackRelease(e.n!!, "16n", time, 0.55) }
            }
            ChannelApi()
        },
        // Trap station (GTA VI)
        "trap" to { out, N ->
            val (kick, snare, hat) = drumKit(out, N, -12.0, -13.0, -27.0)
            val bass808 = synth(N, "sine", 0.005, 0.8, 0.6, 0.5, -7.0).also { it.portamento = 0.08 }; bass808.connect(out)
            val bell = polyFm(N, 3.0, 4.0, 0.002, 1.0, 0.0, 0.6, 0.5, 0.0, 1.0, 0.5, -22.0); bell.chain(delay(N, "8n", 0.35, 0.3), out)
            val pad = poly(N, "sine", 0.8, 0.5, 0.8, 1.5, -24.0); pad.connect(out)
            val melody = L("E5", "B4", "G5", "F#5", "E5", "D5", "B4", "A4")
            val ev = ArrayList<Pair<String, E>>()
            for (b in 0 until 2) {
                ev += bar(b, 0) to E("8", if (b == 1) "C2" else "E2"); ev += bar(b, 1, 2) to E("8", if (b == 1) "D2" else "B1"); ev += bar(b, 2, 3) to E("8", if (b == 1) "B1" else "G1")
                ev += bar(b, 0) to E("pad", if (b == 1) L("C4", "E4", "G4") else L("E4", "G4", "B4"))
                listOf(0 to 0, 1 to 2, 2 to 3).forEach { (q, s) -> ev += bar(b, q, s) to E("k") }
                ev += bar(b, 2) to E("s")
                for (s in 0 until 16) ev += bar(b, s / 4, s % 4) to E("h")
                intArrayOf(0, 1).forEach { x -> ev += bar(b, 3, 2 + x) to E("h") }
                melody.subList(b * 4, b * 4 + 4).forEachIndexed { i, n -> ev += bar(b, i) to E("bell", n) }
            }
            loopPart(N, ev, 2) { time, e ->
                when (e.k) { "8" -> bass808.triggerAttackRelease(e.n!!, "4n", time, 0.9); "pad" -> pad.triggerAttackRelease(e.n!!, "2m", time, 0.3); "k" -> kick.triggerAttackRelease("C1", "8n", time, 0.8)
                    "s" -> snare.triggerAttackRelease("8n", time, 0.8); "h" -> hat.triggerAttackRelease("64n", time, 0.35); else -> bell.triggerAttackRelease(e.n!!, "8n", time, 0.5) }
            }
            ChannelApi()
        },
        // Pentatonic beat (Chinatown Wars)
        "pentatonic" to { out, N ->
            val (kick, snare, hat) = drumKit(out, N, -10.0, -15.0, -30.0)
            val zither = N.n(PluckSynth(N.ctx, 1.2, 3800.0, 0.92)).also { it.volume.value = -9.0 }; zither.connect(out)
            val bass = synth(N, "triangle", 0.01, 0.3, 0.4, 0.2, -12.0); bass.connect(out)
            val scale = L("D4", "E4", "G4", "A4", "B4", "D5", "E5")
            val pattern = intArrayOf(0, 2, 3, 5, 4, 3, 2, 1, 0, 3, 4, 6, 5, 3, 2, 0)
            val ev = ArrayList<Pair<String, E>>()
            for (b in 0 until 2) {
                pattern.forEachIndexed { i, p -> if (i % 3 != 2) ev += bar(b, i / 4, i % 4) to E("z", scale[p]) }
                ev += bar(b, 0) to E("b", "D2"); ev += bar(b, 2) to E("b", if (b == 1) "A1" else "G1")
                listOf(0 to 0, 2 to 2).forEach { (q, s) -> ev += bar(b, q, s) to E("k") }
                intArrayOf(1, 3).forEach { q -> ev += bar(b, q) to E("s") }
                for (s in 0 until 8) ev += bar(b, s / 2, (s % 2) * 2) to E("h")
            }
            loopPart(N, ev, 2) { time, e ->
                when (e.k) { "z" -> zither.triggerAttackRelease(e.n!!, "8n", time); "b" -> bass.triggerAttackRelease(e.n!!, "2n", time, 0.7); "k" -> kick.triggerAttackRelease("C1", "8n", time, 0.85)
                    "s" -> snare.triggerAttackRelease("16n", time, 0.6); else -> hat.triggerAttackRelease("32n", time, 0.35) }
            }
            ChannelApi()
        }
    )
    fun gameMusicBuilder(style: String?): Builder? = when (style) {
        null -> null
        "punk" -> RADIO_CHANNELS[1]
        "poppunk" -> popPunkVideos
        "anime" -> anime
        "piano" -> PC_CHANNELS[4]
        "cartoon" -> cartoons
        "funk" -> PC_CHANNELS[2]
        "march" -> PC_CHANNELS[3]
        "horror" -> vhsHorror
        "trance" -> PC_CHANNELS[0]
        else -> GAME[style]
    }
}
