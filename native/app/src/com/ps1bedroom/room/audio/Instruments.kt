package com.ps1bedroom.room.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Tone's `.volume` in dB, on an output gain. */
class Volume(private val g: Gain) {
    var value: Double = 0.0
        set(v) { field = v; g.gain.value = if (v <= -100) 0.0 else AudioCtx.dbToGain(v) }
}

/** Tone.Envelope driving a gain param: linear attack, exponential decay and release (Tone's defaults). */
class Envelope(private val ctx: AudioCtx, var attack: Double = 0.01, var decay: Double = 0.1, var sustain: Double = 1.0, var release: Double = 1.0) {
    val param = Param(ctx, 0.0)
    fun triggerAttack(time: Double, velocity: Double = 1.0) {
        val cur = param.getValueAtTime(time)
        param.cancelScheduledValues(time)
        param.setValueAtTime(cur, time)
        val a = max(attack, 1e-4)
        param.linearRampToValueAtTime(velocity, time + a)
        if (sustain * velocity != velocity) param.exponentialApproach(sustain * velocity, time + a, max(decay, 1e-4))
    }
    fun triggerRelease(time: Double) {
        val cur = param.getValueAtTime(time)
        param.cancelScheduledValues(time)
        param.setValueAtTime(cur, time)
        param.exponentialApproach(0.0, time, max(release, 1e-3))
    }
}

/** Common to every Tone instrument: an output gain with .volume (dB), connect / chain. */
abstract class Instrument(val ctx: AudioCtx) {
    val output = Gain(ctx, 1.0)
    val volume = Volume(output)
    fun connect(n: Node): Node = output.connect(n)
    fun chain(vararg nodes: Node): Instrument { var prev: Node = output; for (n in nodes) { prev.connect(n); prev = n }; return this }
    fun toSec(d: Any) = ctx.transport.toSeconds(d)
    fun hz(n: Any): Double = when (n) { is String -> AudioCtx.noteHz(n); is Number -> n.toDouble(); else -> 440.0 }
    open fun dispose() { output.dispose() }
    /** When the last scheduled note (with its release) is over; PolySynth hands notes to free voices by this. */
    var busyUntil = 0.0
}

/** Tone.Synth: oscillator → amplitude envelope → volume. */
open class Synth(ctx: AudioCtx, oscType: String = "triangle", val envelope: Envelope = Envelope(ctx, 0.005, 0.1, 0.3, 1.0), count: Int = 3, spread: Double = 20.0) : Instrument(ctx) {
    /** Tone's portamento: glide to each new note over this many seconds. */
    var portamento = 0.0
    val oscillator = Oscillator(ctx, 440.0, oscType).also { it.count = count; it.spread = spread; it.start(0.0) }
    val frequency get() = oscillator.frequency
    private val amp = Gain(ctx, 0.0, envelope.param)
    init { oscillator.connect(amp); amp.connect(output) }
    open fun triggerAttack(note: Any, time: Double = ctx.currentTime, velocity: Double = 1.0) {
        val f = hz(note)
        if (portamento > 0) { val cur = frequency.getValueAtTime(time); frequency.cancelScheduledValues(time); frequency.setValueAtTime(cur, time); frequency.exponentialRampToValueAtTime(f, time + portamento) }
        else { frequency.cancelScheduledValues(time); frequency.setValueAtTime(f, time) }
        envelope.triggerAttack(time, velocity)
        busyUntil = Double.MAX_VALUE
    }
    open fun triggerRelease(time: Double = ctx.currentTime) { envelope.triggerRelease(time); busyUntil = time + envelope.release }
    fun triggerAttackRelease(note: Any, duration: Any, time: Double = ctx.currentTime, velocity: Double = 1.0) {
        triggerAttack(note, time, velocity); triggerRelease(time + toSec(duration))
    }
    override fun dispose() { oscillator.dispose(); amp.dispose(); super.dispose() }
}

/** Tone.MonoSynth: oscillator → filter (with its own frequency envelope) → amplitude envelope. */
class MonoSynth(ctx: AudioCtx, oscType: String = "square", filterQ: Double = 6.0, filterType: String = "lowpass", rolloff: Int = -24,
                val filterEnvelope: Envelope = Envelope(ctx, 0.06, 0.2, 0.5, 2.0), var baseFrequency: Double = 200.0, var octaves: Double = 7.0,
                val envelope: Envelope = Envelope(ctx, 0.005, 0.1, 0.9, 1.0)) : Instrument(ctx) {
    val oscillator = Oscillator(ctx, 440.0, oscType).also { it.start(0.0) }
    val frequency get() = oscillator.frequency
    val filter = Filter(ctx, filterType, baseFrequency, filterQ, if (rolloff <= -24) 2 else 1)
    private val amp = Gain(ctx, 0.0, envelope.param)
    init {
        filter.envelope = filterEnvelope.param; filter.envBase = { baseFrequency }; filter.envOctaves = { octaves }
        oscillator.chain(filter, amp, output)
    }
    fun triggerAttack(note: Any, time: Double = ctx.currentTime, velocity: Double = 1.0) {
        frequency.cancelScheduledValues(time); frequency.setValueAtTime(hz(note), time)
        envelope.triggerAttack(time, velocity); filterEnvelope.triggerAttack(time)
        busyUntil = Double.MAX_VALUE
    }
    fun triggerRelease(time: Double = ctx.currentTime) { envelope.triggerRelease(time); filterEnvelope.triggerRelease(time); busyUntil = time + envelope.release }
    fun triggerAttackRelease(note: Any, duration: Any, time: Double = ctx.currentTime, velocity: Double = 1.0) { triggerAttack(note, time, velocity); triggerRelease(time + toSec(duration)) }
    override fun dispose() { oscillator.dispose(); filter.dispose(); amp.dispose(); super.dispose() }
}

/** Tone.NoiseSynth. */
class NoiseSynth(ctx: AudioCtx, noiseType: String = "white", val envelope: Envelope = Envelope(ctx, 0.005, 0.1, 0.0, 1.0)) : Instrument(ctx) {
    val noise = Noise(ctx, noiseType).also { it.start(0.0) }
    private val amp = Gain(ctx, 0.0, envelope.param)
    init { noise.connect(amp); amp.connect(output) }
    fun triggerAttack(time: Double = ctx.currentTime, velocity: Double = 1.0) { envelope.triggerAttack(time, velocity); busyUntil = Double.MAX_VALUE }
    fun triggerRelease(time: Double = ctx.currentTime) { envelope.triggerRelease(time); busyUntil = time + envelope.release }
    fun triggerAttackRelease(duration: Any, time: Double = ctx.currentTime, velocity: Double = 1.0) { triggerAttack(time, velocity); triggerRelease(time + toSec(duration)) }
    override fun dispose() { noise.dispose(); amp.dispose(); super.dispose() }
}

/** Tone.MembraneSynth: a sine that drops `octaves` over `pitchDecay` (kicks, toms, thumps). */
class MembraneSynth(ctx: AudioCtx, var pitchDecay: Double = 0.05, var octaves: Double = 10.0, val envelope: Envelope = Envelope(ctx, 0.001, 0.4, 0.01, 1.4), oscType: String = "sine") : Instrument(ctx) {
    val oscillator = Oscillator(ctx, 440.0, oscType).also { it.start(0.0) }
    private val amp = Gain(ctx, 0.0, envelope.param)
    init { oscillator.connect(amp); amp.connect(output) }
    fun triggerAttack(note: Any, time: Double = ctx.currentTime, velocity: Double = 1.0) {
        val f = hz(note); val maxNote = f * octaves
        oscillator.frequency.cancelScheduledValues(time)
        oscillator.frequency.setValueAtTime(maxNote, time)
        oscillator.frequency.exponentialRampToValueAtTime(f, time + pitchDecay)
        envelope.triggerAttack(time, velocity)
        busyUntil = Double.MAX_VALUE
    }
    fun triggerRelease(time: Double = ctx.currentTime) { envelope.triggerRelease(time); busyUntil = time + envelope.release }
    fun triggerAttackRelease(note: Any, duration: Any, time: Double = ctx.currentTime, velocity: Double = 1.0) { triggerAttack(note, time, velocity); triggerRelease(time + toSec(duration)) }
    override fun dispose() { oscillator.dispose(); amp.dispose(); super.dispose() }
}

/** Tone.MetalSynth stand-in: six inharmonic square partials, FM-roughened, high-passed at `resonance`. */
class MetalSynth(ctx: AudioCtx, harmonicity: Double = 5.1, modulationIndex: Double = 32.0, resonance: Double = 4000.0, octaves: Double = 1.5,
                 val envelope: Envelope = Envelope(ctx, 0.001, 1.4, 0.0, 0.2)) : Instrument(ctx) {
    private val RATIOS = doubleArrayOf(1.0, 1.483, 1.932, 2.546, 2.630, 3.897)
    private val oscs = RATIOS.map { Oscillator(ctx, 200.0 * it, "square").also { o -> o.start(0.0) } }
    private val hp = Filter(ctx, "highpass", resonance, 0.0)
    private val bp = Filter(ctx, "bandpass", resonance * 2.0.pow(octaves * 0.5), 1.0)
    private val sum = Gain(ctx, 1.0 / RATIOS.size)
    private val amp = Gain(ctx, 0.0, envelope.param)
    private val harm = harmonicity; private val modIdx = modulationIndex
    init { for (o in oscs) o.connect(sum); sum.chain(hp, amp, output); hp.connect(bp); bp.connect(amp) }
    fun triggerAttack(note: Any = 200.0, time: Double = ctx.currentTime, velocity: Double = 1.0) {
        val f = hz(note)
        oscs.forEachIndexed { i, o -> o.frequency.setValueAtTime(f * RATIOS[i] * (1 + 0.002 * modIdx * sin(i * harm)), time) }
        envelope.triggerAttack(time, velocity)
        busyUntil = Double.MAX_VALUE
    }
    fun triggerRelease(time: Double = ctx.currentTime) { envelope.triggerRelease(time); busyUntil = time + envelope.release }
    fun triggerAttackRelease(note: Any, duration: Any, time: Double = ctx.currentTime, velocity: Double = 1.0) { triggerAttack(note, time, velocity); triggerRelease(time + toSec(duration)) }
    override fun dispose() { oscs.forEach { it.dispose() }; hp.dispose(); bp.dispose(); sum.dispose(); amp.dispose(); super.dispose() }
}

/** Tone.FMSynth: a carrier whose frequency is modulated by a second oscillator (with its own envelope). */
class FMSynth(ctx: AudioCtx, var harmonicity: Double = 3.0, var modulationIndex: Double = 10.0, carrierType: String = "sine", modType: String = "square",
              val envelope: Envelope = Envelope(ctx, 0.01, 0.01, 1.0, 0.5), val modulationEnvelope: Envelope = Envelope(ctx, 0.5, 0.0, 1.0, 0.5)) : Instrument(ctx) {
    val oscillator = Oscillator(ctx, 440.0, carrierType).also { it.start(0.0) }
    private val modulator = Oscillator(ctx, 440.0, modType).also { it.start(0.0) }
    private val modAmp = Gain(ctx, 0.0, modulationEnvelope.param)
    private val modDepth = Gain(ctx, 0.0)
    private val amp = Gain(ctx, 0.0, envelope.param)
    val frequency get() = oscillator.frequency
    init { modulator.chain(modAmp, modDepth); modDepth.connect(oscillator.frequency); oscillator.connect(amp); amp.connect(output) }
    fun triggerAttack(note: Any, time: Double = ctx.currentTime, velocity: Double = 1.0) {
        val f = hz(note)
        oscillator.frequency.cancelScheduledValues(time); oscillator.frequency.setValueAtTime(f, time)
        modulator.frequency.cancelScheduledValues(time); modulator.frequency.setValueAtTime(f * harmonicity, time)
        modDepth.gain.cancelScheduledValues(time); modDepth.gain.setValueAtTime(f * modulationIndex, time)
        envelope.triggerAttack(time, velocity); modulationEnvelope.triggerAttack(time)
        busyUntil = Double.MAX_VALUE
    }
    fun triggerRelease(time: Double = ctx.currentTime) { envelope.triggerRelease(time); modulationEnvelope.triggerRelease(time); busyUntil = time + envelope.release }
    fun triggerAttackRelease(note: Any, duration: Any, time: Double = ctx.currentTime, velocity: Double = 1.0) { triggerAttack(note, time, velocity); triggerRelease(time + toSec(duration)) }
    override fun dispose() { oscillator.dispose(); modulator.dispose(); modAmp.dispose(); modDepth.dispose(); amp.dispose(); super.dispose() }
}

/** Tone.PluckSynth: Karplus-Strong string. */
class PluckSynth(ctx: AudioCtx, var attackNoise: Double = 1.0, var dampening: Double = 4000.0, var resonance: Double = 0.7) : Instrument(ctx) {
    private val node = object : Node(ctx) {
        val buf = FloatArray((ctx.sr / 20).toInt() + 2)
        var len = 100; var idx = 0; var startAt = Double.MAX_VALUE; var pendingLen = 100; var vel = 1.0
        var energy = 0f; var lp = 0f
        override fun render(blockId: Long) {
            val t0 = frame0 / ctx.sr
            var any = energy > 1e-4f
            if (!any && startAt > t0 + BLOCK / ctx.sr) { silent = true; return }
            val damp = (1 - kotlin.math.exp(-2 * PI * dampening / ctx.sr)).toFloat()
            var e = 0f
            for (i in 0 until BLOCK) {
                val t = (frame0 + i) / ctx.sr
                if (t >= startAt) {
                    startAt = Double.MAX_VALUE; len = pendingLen; idx = 0
                    for (k in 0 until len) buf[k] = ((Random.nextDouble() * 2 - 1) * attackNoise * vel).toFloat()
                    any = true
                }
                val cur = buf[idx]; val nxt = buf[(idx + 1) % len]
                lp += damp * ((cur + nxt) * 0.5f - lp)
                buf[idx] = lp * resonance.toFloat().coerceIn(0f, 0.999f).let { 0.5f + it * 0.5f }
                outL[i] = cur; e = max(e, abs(cur))
                idx = (idx + 1) % len
            }
            energy = e; silent = false
            System.arraycopy(outL, 0, outR, 0, BLOCK)
        }
    }
    init { node.connect(output) }
    fun triggerAttack(note: Any, time: Double = ctx.currentTime) {
        node.pendingLen = (ctx.sr / hz(note)).toInt().coerceIn(2, node.buf.size - 1); node.startAt = time; node.vel = 1.0
    }
    fun triggerAttackRelease(note: Any, duration: Any, time: Double = ctx.currentTime, velocity: Double = 1.0) { triggerAttack(note, time); node.vel = velocity }
    override fun dispose() { node.dispose(); super.dispose() }
}

/** Tone.PolySynth: hands each note to a free voice (up to 32). */
class PolySynth<V : Instrument>(ctx: AudioCtx, private val factory: () -> V) : Instrument(ctx) {
    private val voices = ArrayList<V>()
    private fun voiceFor(time: Double): V? {
        voices.firstOrNull { it.busyUntil < time }?.let { return it }
        if (voices.size >= 32) return null
        return factory().also { it.connect(output); voices += it }
    }
    private fun notes(n: Any): List<Any> = when (n) { is List<*> -> n.filterNotNull(); is Array<*> -> n.filterNotNull(); else -> listOf(n) }
    fun triggerAttackRelease(notes: Any, duration: Any, time: Double = ctx.currentTime, velocity: Double = 1.0) {
        val d = toSec(duration)
        for (n in notes(notes)) {
            val v = voiceFor(time) ?: continue
            when (v) {
                is Synth -> v.triggerAttackRelease(n, d, time, velocity)
                is FMSynth -> v.triggerAttackRelease(n, d, time, velocity)
                is MonoSynth -> v.triggerAttackRelease(n, d, time, velocity)
                is MembraneSynth -> v.triggerAttackRelease(n, d, time, velocity)
            }
        }
    }
    override fun dispose() { voices.forEach { it.dispose() }; voices.clear(); super.dispose() }
}

// ============================ Transport & parts ============================
/** Tone.Transport: 100 BPM bars for the looping channel music. */
class Transport(private val ctx: AudioCtx) {
    var bpm = 100.0
    val PPQ = 192
    private var started = false
    private var startTime = 0.0
    private var baseTicks = 0.0
    private val parts = ArrayList<Part<*>>()
    fun ticksAt(t: Double) = if (started && t >= startTime) baseTicks + (t - startTime) * bpm / 60 * PPQ else baseTicks
    fun timeOfTick(tick: Double) = startTime + (tick - baseTicks) * 60 / (bpm * PPQ)
    val state get() = if (started) "started" else "paused"
    fun start(time: Double = ctx.currentTime + 0.1) { if (started) return; started = true; startTime = time }
    fun pause(time: Double = ctx.currentTime) { if (!started) return; baseTicks = ticksAt(time); started = false }
    fun add(p: Part<*>) { parts += p }
    fun remove(p: Part<*>) { parts.remove(p) }
    internal fun dispatch(f0: Long, f1: Long) {
        if (!started || parts.isEmpty()) return
        val a = ticksAt(f0 / ctx.sr); val b = ticksAt(f1 / ctx.sr)
        if (b <= a) return
        for (p in ArrayList(parts)) p.dispatch(a, b)
    }
    val ticksPerMeasure get() = PPQ * 4
    /** Tone time notation → seconds at this tempo: '4n', '8n.', '16n', '1m', '2m', '2n', or a number (seconds). */
    fun toSeconds(d: Any): Double = when (d) {
        is Number -> d.toDouble()
        is String -> {
            val q = 60.0 / bpm
            val dotted = d.endsWith(".")
            val s = d.removeSuffix(".")
            val v = when {
                s.endsWith("m") -> (s.dropLast(1).toDoubleOrNull() ?: 1.0) * 4 * q
                s.endsWith("n") -> 4 * q / (s.dropLast(1).toDoubleOrNull() ?: 4.0)
                s.endsWith("t") -> 4 * q / (s.dropLast(1).toDoubleOrNull() ?: 4.0) * 2 / 3
                else -> s.toDoubleOrNull() ?: 0.0
            }
            if (dotted) v * 1.5 else v
        }
        else -> 0.0
    }
    /** "bars:quarters:sixteenths" → ticks. */
    fun posTicks(p: String): Double { val parts = p.split(":"); val b = parts.getOrNull(0)?.toDoubleOrNull() ?: 0.0; val q = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0; val s = parts.getOrNull(2)?.toDoubleOrNull() ?: 0.0; return (b * 4 + q) * PPQ + s * PPQ / 4 }
}

/** Tone.Part looping every `bars` bars, starting on the next bar ('@1m'). */
class Part<T>(private val ctx: AudioCtx, events: List<Pair<String, T>>, bars: Int, private val cb: (Double, T) -> Unit) {
    private val tr = ctx.transport
    private val evTicks = events.map { tr.posTicks(it.first) to it.second }.sortedBy { it.first }
    private val loopLen = bars * tr.ticksPerMeasure.toDouble()
    private val startTick = ceil(tr.ticksAt(ctx.currentTime) / tr.ticksPerMeasure) * tr.ticksPerMeasure
    private var disposed = false
    init { tr.add(this) }
    internal fun dispatch(a: Double, b: Double) {
        if (disposed || b <= startTick) return
        val la = max(a, startTick) - startTick; val lb = b - startTick
        var k = floor0(la / loopLen)
        while (k * loopLen < lb) {
            val base = k * loopLen
            for ((et, v) in evTicks) {
                val abs = base + et
                if (abs >= la && abs < lb) cb(tr.timeOfTick(startTick + abs), v)
            }
            k += 1.0
        }
    }
    private fun floor0(x: Double) = kotlin.math.floor(x)
    fun dispose() { disposed = true; tr.remove(this) }
}

// ============================ Buffers ============================
object Buffers {
    /** makeBuffer: fill, then normalize the peak to 0.9. */
    fun make(ctx: AudioCtx, seconds: Double, fill: (FloatArray, Double) -> Unit): AudioBuffer {
        val d = FloatArray((ctx.sr * seconds).toInt())
        fill(d, ctx.sr)
        var peak = 0f
        for (v in d) peak = max(peak, abs(v))
        if (peak > 0) for (i in d.indices) d[i] *= 0.9f / peak
        return AudioBuffer(d, ctx.sr)
    }
    fun rand(a: Double, b: Double) = a + Random.nextDouble() * (b - a)
    /** Sparse decaying noise ticks (rain on glass, leaf crackle, static), seamless at the loop point. */
    fun ticks(ctx: AudioCtx, seconds: Double, perSecond: Double, minLen: Double, maxLen: Double, ampPow: Double) = make(ctx, seconds) { d, sr ->
        val count = (seconds * perSecond).toInt()
        for (k in 0 until count) {
            val len = (sr * rand(minLen, maxLen)).toInt()
            val start = (Random.nextDouble() * (d.size - len - 1)).toInt()
            val amp = Random.nextDouble().pow(ampPow)
            var lp = 0.0
            for (i in 0 until len) { lp += (Random.nextDouble() * 2 - 1 - lp) * 0.6; d[start + i] += (lp * amp * kotlin.math.exp(-6.0 * i / len)).toFloat() }
        }
    }
    /** Three crickets in a seamless 4 s loop. */
    fun crickets(ctx: AudioCtx) = make(ctx, 4.0) { d, sr ->
        for (c in arrayOf(doubleArrayOf(4200.0, 0.5, 29.0, 0.0), doubleArrayOf(4550.0, 0.4, 33.0, 0.17), doubleArrayOf(3900.0, 0.8, 26.0, 0.31))) {
            val (hz, period, pulseHz, offset) = c
            for (i in d.indices) {
                val t = i / sr
                val ph = ((t + offset) % period) / period
                if (ph > 0.4) continue
                val pulse = sin(PI * ((t * pulseHz) % 1))
                val env = sin(PI * ph / 0.4)
                d[i] += (sin(2 * PI * hz * t) * pulse * pulse * env * 0.33).toFloat()
            }
        }
    }
    /** Sharp lightning crack + chesty low-mid thunder boom & rolling rumble swells (90–600 Hz body + sub punch). */
    fun thunder(ctx: AudioCtx) = make(ctx, 6.5) { d, sr ->
        var brown = 0.0
        var lowMid = 0.0
        var midCrack = 0.0
        val swells = arrayOf(
            doubleArrayOf(0.04, 0.16, 1.35),
            doubleArrayOf(rand(0.28, 0.55), 0.42, 1.05),
            doubleArrayOf(rand(0.95, 1.55), 0.75, 0.80),
            doubleArrayOf(rand(2.05, 2.85), 1.05, 0.55),
            doubleArrayOf(rand(3.40, 4.30), 1.25, 0.32)
        )
        for (i in d.indices) {
            val t = i / sr
            val w = Random.nextDouble() * 2 - 1
            brown = (brown + 0.045 * w) / 1.045
            lowMid = (lowMid + 0.18 * w) / 1.18
            midCrack = (midCrack + 0.42 * w) / 1.42
            var env = kotlin.math.exp(-t * 0.55) * 0.42
            for ((c, width, a) in swells) env += a * kotlin.math.exp(-((t - c) / width).pow(2))
            // Crisp electric tear / crackle in first 0.38s + deep resonant boom sweep (145 Hz -> 42 Hz)
            val snap = if (t < 0.38) (w * 0.55 + midCrack * 0.85) * kotlin.math.exp(-t * 11.0) * (0.65 + 0.35 * sin(t * 95.0)) else 0.0
            val boomFreq = 42.0 + 105.0 * kotlin.math.exp(-t * 4.2)
            val boom = sin(2 * PI * boomFreq * t) * kotlin.math.exp(-t * 1.65) * 0.95
            val subRumble = sin(2 * PI * (58.0 + 18.0 * sin(t * 2.7)) * t) * env * 0.55
            val body = (brown * 2.2 + (lowMid - brown) * 2.8) * env
            // Soft saturation so the crack and rumble sit evenly without squashing the tail
            val raw = body + boom + subRumble + snap
            d[i] = kotlin.math.tanh(raw * 0.85).toFloat()
        }
    }
    @Suppress("unused") private fun unused() = ln(min(1.0, 1.0))
}
