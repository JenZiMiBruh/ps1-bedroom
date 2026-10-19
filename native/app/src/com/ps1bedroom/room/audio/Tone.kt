package com.ps1bedroom.room.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlin.random.Random

/**
 * A small native stand-in for the parts of Tone.js / WebAudio the HTML's sound engine uses, so the soundscape
 * ports almost line for line. A pull graph rendered in 128-frame blocks on one audio thread into an AudioTrack.
 *
 * Threading: every graph change (connect, trigger, automation) happens under [AudioCtx.lock]; the render thread
 * takes the same lock per block. Part callbacks run on the render thread (already holding it).
 * Performance: a node whose output is known silent (a gain held at 0, an idle envelope, a stopped source)
 * doesn't pull its inputs at all, so silent layers cost nothing (the HTML's SOURCE_GATES, for free).
 */
const val BLOCK = 128

class AudioCtx {
    val lock = Any()
    val sampleRate: Int = AudioTrack.getNativeOutputSampleRate(AudioManagerStream).coerceIn(22050, 96000)
    val sr = sampleRate.toDouble()
    @Volatile var frame = 0L; private set
    val currentTime get() = frame / sr
    fun now() = currentTime
    var block = 0L; private set
    val destination = Destination(this)
    val transport = Transport(this)
    private var thread: Thread? = null
    @Volatile private var running = false
    @Volatile var suspended = false
    private var track: AudioTrack? = null

    fun start() {
        if (running) return
        running = true
        val ch = AudioFormat.CHANNEL_OUT_STEREO
        val minBuf = AudioTrack.getMinBufferSize(sampleRate, ch, AudioFormat.ENCODING_PCM_FLOAT)
        // Big, steady buffers (the HTML asks for "playback" latency for the same reason: no crackle while the 3D renders)
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(sampleRate).setChannelMask(ch).build())
            .setBufferSizeInBytes(max(minBuf * 4, sampleRate / 5 * 8))
            .setTransferMode(AudioTrack.MODE_STREAM)
            // the deep-buffer output path: the CPU sleeps between big writes (ambience doesn't need low latency)
            .apply { if (android.os.Build.VERSION.SDK_INT >= 26) setPerformanceMode(AudioTrack.PERFORMANCE_MODE_POWER_SAVING) }
            .build()
        track = t
        t.play()
        thread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val chunkBlocks = 8
            val out = FloatArray(BLOCK * 2 * chunkBlocks)
            while (running) {
                if (suspended) { try { Thread.sleep(40) } catch (e: InterruptedException) { }; continue }
                for (b in 0 until chunkBlocks) {
                    synchronized(lock) { renderBlock(out, b * BLOCK * 2) }
                }
                t.write(out, 0, out.size, AudioTrack.WRITE_BLOCKING)
            }
        }, "PS1Audio").also { it.start() }
    }
    fun stop() {
        running = false
        thread?.join(500)
        track?.let { try { it.stop() } catch (e: Exception) {}; it.release() }
        track = null
    }
    fun pauseOutput(p: Boolean) {
        suspended = p
        track?.let { if (p) it.pause() else it.play() }
    }

    private fun renderBlock(out: FloatArray, off: Int) {
        block++
        transport.dispatch(frame, frame + BLOCK)
        destination.pull(block)
        val l = destination.outL; val r = destination.outR
        if (destination.silent) for (i in 0 until BLOCK) { out[off + i * 2] = 0f; out[off + i * 2 + 1] = 0f }
        else for (i in 0 until BLOCK) { out[off + i * 2] = l[i]; out[off + i * 2 + 1] = r[i] }
        frame += BLOCK
    }

    // Main-thread helpers
    inline fun <T> sync(fn: () -> T): T = synchronized(lock) { fn() }

    companion object {
        const val AudioManagerStream = android.media.AudioManager.STREAM_MUSIC
        fun dbToGain(db: Double) = 10.0.pow(db / 20.0)
        private val NOTE = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11)
        /** "A4", "C#3", "Bb2" → Hz (A4 = 440). */
        fun noteHz(n: String): Double {
            val base = NOTE[n[0].uppercaseChar()] ?: return 440.0
            var i = 1; var semis = base
            while (i < n.length && (n[i] == '#' || n[i] == 'b')) { semis += if (n[i] == '#') 1 else -1; i++ }
            val oct = n.substring(i).toIntOrNull() ?: 4
            val midi = (oct + 1) * 12 + semis
            return 440.0 * 2.0.pow((midi - 69) / 12.0)
        }
    }
}

// ============================ Params (WebAudio automation) ============================
class Param(private val ctx: AudioCtx, initial: Double, val minValue: Double = -1e9, val maxValue: Double = 1e9) {
    private class Ev(val type: Int, val time: Double, val value: Double, val tau: Double = 0.0)
    private val events = ArrayList<Ev>()
    private var lastV = initial
    private var lastT = 0.0
    private var targetActive = false
    private var targetV = 0.0; private var targetStart = 0.0; private var targetFrom = 0.0; private var targetTau = 1.0
    /** Nodes summed onto the value (an LFO into a gain, an FM modulator into a frequency). */
    val mods = ArrayList<Node>()
    private val buf = FloatArray(BLOCK)

    var value: Double
        get() = valueAt(ctx.currentTime)
        set(v) { events.clear(); targetActive = false; lastV = v; lastT = ctx.currentTime }

    private fun insert(e: Ev) {
        var i = events.size
        while (i > 0 && events[i - 1].time > e.time) i--
        events.add(i, e)
    }
    fun setValueAtTime(v: Double, t: Double): Param { insert(Ev(0, t, v)); return this }
    fun linearRampToValueAtTime(v: Double, t: Double): Param { insert(Ev(1, t, v)); return this }
    fun exponentialRampToValueAtTime(v: Double, t: Double): Param { insert(Ev(2, t, if (abs(v) < 1e-6) 1e-6 else v)); return this }
    fun setTargetAtTime(v: Double, t: Double, tau: Double): Param { insert(Ev(3, t, v, max(1e-4, tau))); return this }
    /** WebAudio cancelScheduledValues: drops every event at or after t (callers set the value they want to hold). */
    fun cancelScheduledValues(t: Double): Param { events.removeAll { it.time >= t }; return this }
    // Tone's helpers
    fun rampTo(v: Double, ramp: Double, startTime: Double = ctx.currentTime, exponential: Boolean = false) {
        val from = valueAt(startTime)
        cancelScheduledValues(startTime)
        setValueAtTime(from, startTime)
        if (exponential) exponentialRampToValueAtTime(v, startTime + ramp) else linearRampToValueAtTime(v, startTime + ramp)
    }
    /** Tone's exponentialApproachValueAtTime (envelope decay / release curves). */
    fun exponentialApproach(v: Double, time: Double, rampTime: Double) {
        val tau = ln(rampTime + 1) / ln(200.0)
        setTargetAtTime(v, time, tau)
        setValueAtTime(v, time + rampTime)
    }
    fun getValueAtTime(t: Double) = valueAt(t)

    /** Consume events up to time t (inclusive). */
    private fun advance(t: Double) {
        while (events.isNotEmpty() && events[0].time <= t) {
            val e = events.removeAt(0)
            when (e.type) {
                0, 1, 2 -> { lastV = e.value; lastT = e.time; targetActive = false }
                3 -> { val from = currentNoEvents(e.time); targetActive = true; targetV = e.value; targetStart = e.time; targetFrom = from; targetTau = e.tau; lastT = e.time; lastV = from }
            }
        }
    }
    private fun currentNoEvents(t: Double): Double =
        if (targetActive) targetV + (targetFrom - targetV) * exp(-(t - targetStart) / targetTau) else lastV
    private fun valueAt(t: Double): Double {
        // look ahead without consuming
        var lv = lastV; var lt = lastT; var tg = targetActive; var tV = targetV; var tS = targetStart; var tF = targetFrom; var tT = targetTau
        for (e in events) {
            if (e.time > t) {
                val start = if (tg) tV + (tF - tV) * exp(-(lt - tS) / tT) else lv
                return when (e.type) {
                    1 -> if (e.time <= lt) e.value else start + (e.value - start) * ((t - lt) / (e.time - lt))
                    2 -> if (e.time <= lt || start * e.value <= 0) start else start * (e.value / start).pow((t - lt) / (e.time - lt))
                    else -> if (tg) tV + (tF - tV) * exp(-(t - tS) / tT) else lv
                }
            }
            when (e.type) {
                0, 1, 2 -> { lv = e.value; lt = e.time; tg = false }
                3 -> { val from = if (tg) tV + (tF - tV) * exp(-(e.time - tS) / tT) else lv; tg = true; tV = e.value; tS = e.time; tF = from; tT = e.tau; lt = e.time; lv = from }
            }
        }
        return if (tg) tV + (tF - tV) * exp(-(t - tS) / tT) else lv
    }
    /** True when nothing is scheduled ahead and nothing modulates it: [constant] holds the value. */
    var constant = 0f; private set
    fun isConstant(blockStart: Double): Boolean {
        advance(blockStart)
        if (mods.isNotEmpty() || events.isNotEmpty()) return false
        if (targetActive) {
            val v = currentNoEvents(blockStart)
            if (abs(v - targetV) < 1e-5) { targetActive = false; lastV = targetV } else return false
        }
        constant = lastV.coerceIn(minValue, maxValue).toFloat()
        return true
    }
    /** Fills the block's values (a-rate) and returns the array. */
    fun fill(blockId: Long, frame0: Long, sr: Double): FloatArray {
        val t0 = frame0 / sr
        if (isConstant(t0)) { java.util.Arrays.fill(buf, constant); return buf }
        for (i in 0 until BLOCK) {
            val t = (frame0 + i) / sr
            if (events.isNotEmpty() && events[0].time <= t) advance(t)
            buf[i] = (if (events.isEmpty()) currentNoEvents(t) else valueAt(t)).coerceIn(minValue, maxValue).toFloat()
        }
        for (m in mods) { m.pull(blockId); if (!m.silent) { val ml = m.outL; for (i in 0 until BLOCK) buf[i] += ml[i] } }
        return buf
    }
    /** k-rate: one value per block (filters, panners). */
    fun kValue(blockId: Long, frame0: Long, sr: Double): Double {
        val t = frame0 / sr
        advance(t)
        var v = if (events.isEmpty()) currentNoEvents(t) else valueAt(t)
        for (m in mods) { m.pull(blockId); if (!m.silent) v += m.outL[0] }
        return v.coerceIn(minValue, maxValue)
    }
}

// ============================ Nodes ============================
abstract class Node(val ctx: AudioCtx) {
    val inputs = ArrayList<Node>()
    private val outputs = ArrayList<Any>()  // Node or Param
    val outL = FloatArray(BLOCK); val outR = FloatArray(BLOCK)
    /** Stereo output (after a panner); mono nodes copy L into R. */
    var stereo = false; protected set
    var silent = true; protected set
    private var rendered = -1L
    var disposed = false; private set

    open val input: Node get() = this
    open val output: Node get() = this

    fun connect(dest: Node): Node { val o = output; val d = dest.input; if (!d.inputs.contains(o)) d.inputs.add(o); o.outputs.add(d); return dest }
    fun connect(p: Param): Param { val o = output; p.mods.add(o); o.outputs.add(p); return p }
    fun chain(vararg nodes: Node): Node { var prev: Node = this; for (n in nodes) { prev.connect(n); prev = n }; return this }
    fun fan(vararg nodes: Node): Node { for (n in nodes) connect(n); return this }
    open fun disconnect() {
        val o = output
        for (d in o.outputs) when (d) { is Node -> d.inputs.remove(o); is Param -> d.mods.remove(o) }
        o.outputs.clear()
    }
    open fun dispose() { if (disposed) return; disposed = true; disconnect(); inputs.clear() }

    fun pull(blockId: Long) {
        if (rendered == blockId) return
        rendered = blockId
        render(blockId)
    }
    protected abstract fun render(blockId: Long)
    protected val frame0 get() = ctx.frame

    /** Sums every input into outL / outR; returns false if all inputs are silent. */
    protected fun sumInputs(blockId: Long): Boolean {
        var any = false; var st = false
        for (n in inputs) {
            n.pull(blockId)
            if (n.silent) continue
            if (!any) { System.arraycopy(n.outL, 0, outL, 0, BLOCK); System.arraycopy(if (n.stereo) n.outR else n.outL, 0, outR, 0, BLOCK); any = true }
            else { val a = n.outL; val b = if (n.stereo) n.outR else n.outL; for (i in 0 until BLOCK) { outL[i] += a[i]; outR[i] += b[i] } }
            if (n.stereo) st = true
        }
        stereo = st
        return any
    }
}

/** Tone.Gain / GainNode. A gain held at 0 doesn't even pull its inputs. */
open class Gain(ctx: AudioCtx, g: Double = 1.0, gainParam: Param? = null) : Node(ctx) {
    val gain = gainParam ?: Param(ctx, g)
    override fun render(blockId: Long) {
        val t0 = frame0 / ctx.sr
        if (gain.isConstant(t0) && gain.constant == 0f) { silent = true; return }
        if (!sumInputs(blockId)) { silent = true; return }
        silent = false
        if (gain.isConstant(t0)) {
            val g = gain.constant
            if (g != 1f) for (i in 0 until BLOCK) { outL[i] *= g; outR[i] *= g }
        } else {
            val gb = gain.fill(blockId, frame0, ctx.sr)
            for (i in 0 until BLOCK) { outL[i] *= gb[i]; outR[i] *= gb[i] }
        }
    }
}

/** Native BiquadFilterNode with WebAudio's formulas (low/high-pass Q is in dB, the others linear). */
class Filter(ctx: AudioCtx, var type: String = "lowpass", frequency: Double = 1000.0, q: Double = 1.0, val stages: Int = 1) : Node(ctx) {
    val frequency = Param(ctx, frequency, 10.0, 22000.0)
    val Q = Param(ctx, q)
    val gain = Param(ctx, 0.0)
    /** MonoSynth: a filter envelope sweeps the cutoff from envBase up `envOctaves` octaves (Tone's FrequencyEnvelope, exponent 2). */
    var envelope: Param? = null
    var envBase: (() -> Double)? = null
    var envOctaves: (() -> Double)? = null
    private val envBuf = FloatArray(BLOCK)
    private var b0 = 1.0; private var b1 = 0.0; private var b2 = 0.0; private var a1 = 0.0; private var a2 = 0.0
    private val zl = DoubleArray(4 * stages); private val zr = DoubleArray(4 * stages)
    private var lf = -1.0; private var lq = -1.0; private var lg = -1.0; private var lt = ""
    private var tail = 0
    private fun coeffs(f0: Double, q: Double, gdb: Double) {
        if (f0 == lf && q == lq && gdb == lg && type == lt) return
        lf = f0; lq = q; lg = gdb; lt = type
        val w0 = 2 * PI * min(f0, ctx.sr * 0.49) / ctx.sr
        val cw = cos(w0); val sw = sin(w0)
        var B0: Double; var B1: Double; var B2: Double; val A0: Double; var A1: Double; var A2: Double
        when (type) {
            "highpass" -> { val alpha = sw / (2 * 10.0.pow(q / 20)); B0 = (1 + cw) / 2; B1 = -(1 + cw); B2 = (1 + cw) / 2; A0 = 1 + alpha; A1 = -2 * cw; A2 = 1 - alpha }
            "bandpass" -> { val alpha = sw / (2 * max(1e-4, q)); B0 = alpha; B1 = 0.0; B2 = -alpha; A0 = 1 + alpha; A1 = -2 * cw; A2 = 1 - alpha }
            "notch" -> { val alpha = sw / (2 * max(1e-4, q)); B0 = 1.0; B1 = -2 * cw; B2 = 1.0; A0 = 1 + alpha; A1 = -2 * cw; A2 = 1 - alpha }
            "peaking" -> { val A = 10.0.pow(gdb / 40); val alpha = sw / (2 * max(1e-4, q)); B0 = 1 + alpha * A; B1 = -2 * cw; B2 = 1 - alpha * A; A0 = 1 + alpha / A; A1 = -2 * cw; A2 = 1 - alpha / A }
            "lowshelf" -> { val A = 10.0.pow(gdb / 40); val alpha = sw / 2 * sqrt(2.0); val sa = 2 * sqrt(A) * alpha
                B0 = A * ((A + 1) - (A - 1) * cw + sa); B1 = 2 * A * ((A - 1) - (A + 1) * cw); B2 = A * ((A + 1) - (A - 1) * cw - sa); A0 = (A + 1) + (A - 1) * cw + sa; A1 = -2 * ((A - 1) + (A + 1) * cw); A2 = (A + 1) + (A - 1) * cw - sa }
            "highshelf" -> { val A = 10.0.pow(gdb / 40); val alpha = sw / 2 * sqrt(2.0); val sa = 2 * sqrt(A) * alpha
                B0 = A * ((A + 1) + (A - 1) * cw + sa); B1 = -2 * A * ((A - 1) + (A + 1) * cw); B2 = A * ((A + 1) + (A - 1) * cw - sa); A0 = (A + 1) - (A - 1) * cw + sa; A1 = 2 * ((A - 1) - (A + 1) * cw); A2 = (A + 1) - (A - 1) * cw - sa }
            else -> { val alpha = sw / (2 * 10.0.pow(q / 20)); B0 = (1 - cw) / 2; B1 = 1 - cw; B2 = (1 - cw) / 2; A0 = 1 + alpha; A1 = -2 * cw; A2 = 1 - alpha }
        }
        b0 = B0 / A0; b1 = B1 / A0; b2 = B2 / A0; a1 = A1 / A0; a2 = A2 / A0
    }
    override fun render(blockId: Long) {
        val any = sumInputs(blockId)
        if (!any) {
            if (tail <= 0) { silent = true; return }
            tail--; java.util.Arrays.fill(outL, 0f); java.util.Arrays.fill(outR, 0f)
        } else tail = 64 // keep ringing out for a few blocks
        silent = false
        val q = Q.kValue(blockId, frame0, ctx.sr); val g = gain.kValue(blockId, frame0, ctx.sr)
        val env = envelope
        val fixed = env == null && frequency.isConstant(frame0 / ctx.sr)
        val fb = if (fixed) null else if (env != null) {
            val e = env.fill(blockId, frame0, ctx.sr); val base = envBase?.invoke() ?: 200.0; val top = base * 2.0.pow(envOctaves?.invoke() ?: 1.0)
            for (i in 0 until BLOCK) envBuf[i] = (base + (top - base) * e[i] * e[i]).toFloat(); envBuf
        } else frequency.fill(blockId, frame0, ctx.sr)
        if (fixed) coeffs(frequency.constant.toDouble(), q, g)
        for (s in 0 until stages) {
            val o = s * 4
            var x1l = zl[o]; var x2l = zl[o + 1]; var y1l = zl[o + 2]; var y2l = zl[o + 3]
            var x1r = zr[o]; var x2r = zr[o + 1]; var y1r = zr[o + 2]; var y2r = zr[o + 3]
            for (i in 0 until BLOCK) {
                if (fb != null && (i and 15) == 0) coeffs(fb[i].toDouble(), q, g)
                val xl = outL[i].toDouble()
                val yl = b0 * xl + b1 * x1l + b2 * x2l - a1 * y1l - a2 * y2l
                x2l = x1l; x1l = xl; y2l = y1l; y1l = yl; outL[i] = yl.toFloat()
                if (stereo) {
                    val xr = outR[i].toDouble()
                    val yr = b0 * xr + b1 * x1r + b2 * x2r - a1 * y1r - a2 * y2r
                    x2r = x1r; x1r = xr; y2r = y1r; y1r = yr; outR[i] = yr.toFloat()
                }
            }
            if (abs(y1l) < 1e-15) y1l = 0.0
            if (abs(y2l) < 1e-15) y2l = 0.0
            zl[o] = x1l; zl[o + 1] = x2l; zl[o + 2] = y1l; zl[o + 3] = y2l
            zr[o] = x1r; zr[o + 1] = x2r; zr[o + 2] = y1r; zr[o + 3] = y2r
        }
        if (!stereo) System.arraycopy(outL, 0, outR, 0, BLOCK)
    }
}

/** StereoPannerNode (equal power). */
class StereoPanner(ctx: AudioCtx) : Node(ctx) {
    val pan = Param(ctx, 0.0, -1.0, 1.0)
    override fun render(blockId: Long) {
        if (!sumInputs(blockId)) { silent = true; return }
        silent = false
        val p = pan.kValue(blockId, frame0, ctx.sr)
        if (!stereo) {
            val x = (p + 1) / 2
            val gl = cos(x * PI / 2).toFloat(); val gr = sin(x * PI / 2).toFloat()
            for (i in 0 until BLOCK) { val v = outL[i]; outL[i] = v * gl; outR[i] = v * gr }
        } else {
            // stereo input: WebAudio's stereo rule
            if (p <= 0) { val x = p + 1; val gl = cos(x * PI / 2).toFloat(); val gr = sin(x * PI / 2).toFloat(); for (i in 0 until BLOCK) { val l = outL[i]; val r = outR[i]; outL[i] = l + r * gl; outR[i] = r * gr } }
            else { val x = p; val gl = cos(x * PI / 2).toFloat(); val gr = sin(x * PI / 2).toFloat(); for (i in 0 until BLOCK) { val l = outL[i]; val r = outR[i]; outL[i] = l * gl; outR[i] = r + l * gr } }
        }
        stereo = true
    }
}

/** A start / stop source (Tone sources must be started). */
abstract class Source(ctx: AudioCtx) : Node(ctx) {
    private var startAt = Double.MAX_VALUE
    private var stopAt = Double.MAX_VALUE
    var onstop: (() -> Unit)? = null
    var playing = false; private set
    fun start(time: Double = ctx.currentTime): Source { startAt = time; stopAt = Double.MAX_VALUE; return this }
    fun stop(time: Double = ctx.currentTime): Source { stopAt = time; return this }
    /** Tone's .volume (dB) on sources. */
    val volume = VolumeParam(ctx)
    override fun render(blockId: Long) {
        val t0 = frame0 / ctx.sr; val t1 = (frame0 + BLOCK) / ctx.sr
        if (t1 <= startAt || t0 >= stopAt) {
            if (playing && t0 >= stopAt) { playing = false; onstop?.invoke() }
            silent = true; return
        }
        playing = true
        generate(blockId)
        // sample-accurate start / stop inside the block
        if (startAt > t0) { val n = ((startAt - t0) * ctx.sr).toInt().coerceIn(0, BLOCK); for (i in 0 until n) outL[i] = 0f }
        if (stopAt < t1) { val n = ((stopAt - t0) * ctx.sr).toInt().coerceIn(0, BLOCK); for (i in n until BLOCK) outL[i] = 0f }
        val v = volume.gain
        if (v != 1f) for (i in 0 until BLOCK) outL[i] *= v
        System.arraycopy(outL, 0, outR, 0, BLOCK)
        silent = false; stereo = false
    }
    protected abstract fun generate(blockId: Long)
}
class VolumeParam(private val ctx: AudioCtx) {
    var gain = 1f; private set
    /** dB */
    var value: Double = 0.0
        set(v) { field = v; gain = if (v <= -100) 0f else AudioCtx.dbToGain(v).toFloat() }
}

/** Tone.Oscillator: sine, square, sawtooth, triangle and the "fat" (detuned stack) variants, band-limited with polyBLEP. */
class Oscillator(ctx: AudioCtx, freq: Double = 440.0, type: String = "sine") : Source(ctx) {
    val frequency = Param(ctx, freq, 0.0, 22000.0)
    val detune = Param(ctx, 0.0)
    var type = type
        set(v) { field = v; parseType() }
    var count = 3
    var spread = 20.0
    private var base = "sine"; private var fat = false
    private val phases = DoubleArray(8) { Random.nextDouble() }
    private fun parseType() { fat = type.startsWith("fat"); base = type.removePrefix("fat") }
    init { parseType() }
    private fun blep(t: Double, dt: Double): Double = when {
        t < dt -> { val x = t / dt; x + x - x * x - 1 }
        t > 1 - dt -> { val x = (t - 1) / dt; x * x + x + x + 1 }
        else -> 0.0
    }
    override fun generate(blockId: Long) {
        val fb = frequency.fill(blockId, frame0, ctx.sr)
        val det = detune.kValue(blockId, frame0, ctx.sr)
        val voices = if (fat) count.coerceIn(1, 8) else 1
        java.util.Arrays.fill(outL, 0f)
        for (v in 0 until voices) {
            val cents = det + (if (voices > 1) -spread / 2 + spread * v / (voices - 1) else 0.0)
            val mul = 2.0.pow(cents / 1200)
            var ph = phases[v]
            for (i in 0 until BLOCK) {
                val dt = (fb[i] * mul / ctx.sr).coerceIn(0.0, 0.5)
                val s = when (base) {
                    "square" -> (if (ph < 0.5) 1.0 else -1.0) + blep(ph, dt) - blep((ph + 0.5) % 1.0, dt)
                    "sawtooth" -> (2 * ph - 1) - blep(ph, dt)
                    "triangle" -> 1 - 4 * abs(ph - 0.5)
                    else -> sin(2 * PI * ph)
                }
                outL[i] += (s / voices).toFloat()
                ph += dt; if (ph >= 1) ph -= 1
            }
            phases[v] = ph
        }
    }
}

/** Tone.Noise: white, pink (Paul Kellet) and brown, at Tone's levels. */
class Noise(ctx: AudioCtx, type: String = "white") : Source(ctx) {
    var type = type
    private var b0 = 0.0; private var b1 = 0.0; private var b2 = 0.0; private var b3 = 0.0; private var b4 = 0.0; private var b5 = 0.0; private var b6 = 0.0
    private var brown = 0.0
    private var seed = Random.nextInt() or 1
    private fun white(): Double { seed = seed xor (seed shl 13); seed = seed xor (seed ushr 17); seed = seed xor (seed shl 5); return seed / 2147483648.0 }
    override fun generate(blockId: Long) {
        when (type) {
            "pink" -> for (i in 0 until BLOCK) {
                val w = white()
                b0 = 0.99886 * b0 + w * 0.0555179; b1 = 0.99332 * b1 + w * 0.0750759; b2 = 0.969 * b2 + w * 0.153852
                b3 = 0.8665 * b3 + w * 0.3104856; b4 = 0.55 * b4 + w * 0.5329522; b5 = -0.7616 * b5 - w * 0.016898
                outL[i] = ((b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362) * 0.11).toFloat()
                b6 = w * 0.115926
            }
            "brown" -> for (i in 0 until BLOCK) { brown = (brown + 0.02 * white()) / 1.02; outL[i] = (brown * 3.5).toFloat() }
            else -> for (i in 0 until BLOCK) outL[i] = white().toFloat()
        }
    }
}

/** An audio buffer (mono). */
class AudioBuffer(val data: FloatArray, val sampleRate: Double)

/** Tone.Player: buffer playback with loop and playbackRate. */
class Player(ctx: AudioCtx, var buffer: AudioBuffer) : Source(ctx) {
    var loop = false
    var playbackRate = 1.0
    private var pos = 0.0
    override fun generate(blockId: Long) {
        val d = buffer.data; val step = playbackRate * buffer.sampleRate / ctx.sr
        for (i in 0 until BLOCK) {
            if (pos >= d.size) { if (loop) pos -= d.size else { outL[i] = 0f; if (i == BLOCK - 1) stop(ctx.currentTime); continue } }
            val i0 = pos.toInt(); val f = (pos - i0).toFloat()
            val a = d[i0]; val b = if (i0 + 1 < d.size) d[i0 + 1] else (if (loop) d[0] else 0f)
            outL[i] = a + (b - a) * f
            pos += step
        }
    }
}

/** Tone.FeedbackDelay (equal-power wet / dry). */
class FeedbackDelay(ctx: AudioCtx, delaySec: Double, feedback: Double, wet: Double) : Node(ctx) {
    val delayTime = Param(ctx, delaySec, 0.0, 4.0)
    val feedback = Param(ctx, feedback)
    val wet = Param(ctx, wet, 0.0, 1.0)
    private val size = (ctx.sr * 4.1).toInt()
    private val bl = FloatArray(size); private val br = FloatArray(size)
    private var w = 0
    private var energy = 0f
    override fun render(blockId: Long) {
        val any = sumInputs(blockId)
        if (!any) { if (energy < 1e-5f) { silent = true; return }; java.util.Arrays.fill(outL, 0f); java.util.Arrays.fill(outR, 0f) }
        silent = false
        val d = (delayTime.kValue(blockId, frame0, ctx.sr) * ctx.sr).toInt().coerceIn(1, size - 1)
        val fbk = feedback.kValue(blockId, frame0, ctx.sr).toFloat()
        val wv = wet.kValue(blockId, frame0, ctx.sr)
        val gw = sin(wv * PI / 2).toFloat(); val gd = cos(wv * PI / 2).toFloat()
        var e = 0f
        for (i in 0 until BLOCK) {
            var r = w - d; if (r < 0) r += size
            val dl = bl[r]; val dr = br[r]
            val xl = outL[i]; val xr = outR[i]
            bl[w] = xl + dl * fbk; br[w] = xr + dr * fbk
            outL[i] = xl * gd + dl * gw; outR[i] = xr * gd + dr * gw
            e = max(e, abs(dl))
            w++; if (w >= size) w = 0
        }
        energy = if (any) 1f else e
        stereo = true
    }
}

/** Tone.Reverb stand-in: an algorithmic (Freeverb-style) room tuned to the same decay, cheap on a phone. */
class Reverb(ctx: AudioCtx, decay: Double, val preDelay: Double = 0.01, wet: Double = 1.0) : Node(ctx) {
    val wet = Param(ctx, wet, 0.0, 1.0)
    private val combLens = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
    private val apLens = intArrayOf(556, 441, 341, 225)
    private val scale = ctx.sr / 44100.0
    private class Comb(n: Int) { val b = FloatArray(n); var i = 0; var store = 0f }
    private class Ap(n: Int) { val b = FloatArray(n); var i = 0 }
    private val combsL = combLens.map { Comb((it * scale).toInt()) }
    private val combsR = combLens.map { Comb(((it + 23) * scale).toInt()) }
    private val apsL = apLens.map { Ap((it * scale).toInt()) }
    private val apsR = apLens.map { Ap(((it + 23) * scale).toInt()) }
    private val pre = FloatArray(max(1, (preDelay * ctx.sr).toInt()) + 1); private var pi = 0
    private var fbk = 0f
    private val damp = 0.3f
    private var tail = 0
    init {
        // RT60 = decay: comb gain g so that g^(T/len) = 0.001 over `decay` seconds (average comb length)
        val avg = combLens.average() * scale / ctx.sr
        fbk = 10.0.pow(-3 * avg / max(0.1, decay)).toFloat().coerceIn(0f, 0.98f)
        tail = 0
    }
    private val tailBlocks = ((decay + preDelay) * ctx.sr / BLOCK).toInt() + 4
    override fun render(blockId: Long) {
        val any = sumInputs(blockId)
        if (!any) { if (tail <= 0) { silent = true; return }; tail--; java.util.Arrays.fill(outL, 0f); java.util.Arrays.fill(outR, 0f) } else tail = tailBlocks
        silent = false
        val wv = wet.kValue(blockId, frame0, ctx.sr).toFloat()
        for (i in 0 until BLOCK) {
            val xin = (outL[i] + outR[i]) * 0.5f * 0.015f
            val x = pre[pi]; pre[pi] = xin; pi++; if (pi >= pre.size) pi = 0
            var l = 0f; var r = 0f
            for (c in combsL) { val y = c.b[c.i]; c.store = y * (1 - damp) + c.store * damp; c.b[c.i] = x + c.store * fbk; c.i++; if (c.i >= c.b.size) c.i = 0; l += y }
            for (c in combsR) { val y = c.b[c.i]; c.store = y * (1 - damp) + c.store * damp; c.b[c.i] = x + c.store * fbk; c.i++; if (c.i >= c.b.size) c.i = 0; r += y }
            for (a in apsL) { val bo = a.b[a.i]; val o = -l + bo; a.b[a.i] = l + bo * 0.5f; a.i++; if (a.i >= a.b.size) a.i = 0; l = o }
            for (a in apsR) { val bo = a.b[a.i]; val o = -r + bo; a.b[a.i] = r + bo * 0.5f; a.i++; if (a.i >= a.b.size) a.i = 0; r = o }
            outL[i] = outL[i] * (1 - wv) + l * wv * 3f; outR[i] = outR[i] * (1 - wv) + r * wv * 3f
        }
        stereo = true
    }
}

/** Tone.Compressor / Tone.Limiter (feed-forward, linked stereo, soft knee). */
class Compressor(ctx: AudioCtx, val threshold: Double, val ratio: Double, attack: Double, release: Double, val knee: Double = 0.0) : Node(ctx) {
    private val att = exp(-1.0 / (attack * ctx.sr)); private val rel = exp(-1.0 / (release * ctx.sr))
    private var env = 0.0
    override fun render(blockId: Long) {
        if (!sumInputs(blockId)) { silent = true; env = 0.0; return }
        silent = false
        for (i in 0 until BLOCK) {
            val lvl = max(abs(outL[i]), abs(outR[i])).toDouble()
            env = if (lvl > env) att * env + (1 - att) * lvl else rel * env + (1 - rel) * lvl
            val db = 20 * kotlin.math.log10(max(env, 1e-9))
            val over = db - threshold
            val red = when {
                knee > 0 && over > -knee / 2 && over < knee / 2 -> (1 / ratio - 1) * (over + knee / 2).pow(2) / (2 * knee)
                over >= knee / 2 -> over * (1 / ratio - 1)
                else -> 0.0
            }
            val g = AudioCtx.dbToGain(red).toFloat()
            outL[i] *= g; outR[i] *= g
        }
        stereo = true
    }
}

/** Tone.WaveShaper with a curve function (applied directly). */
class WaveShaper(ctx: AudioCtx, val curve: (Double) -> Double) : Node(ctx) {
    private val table = FloatArray(4097) { curve(it / 2048.0 - 1).toFloat() }
    private fun shape(x: Float): Float { val p = ((x.coerceIn(-1f, 1f) + 1) * 2048f); val i = p.toInt().coerceIn(0, 4095); val f = p - i; return table[i] + (table[i + 1] - table[i]) * f }
    override fun render(blockId: Long) {
        if (!sumInputs(blockId)) { silent = true; return }
        silent = false
        for (i in 0 until BLOCK) { outL[i] = shape(outL[i]); outR[i] = shape(outR[i]) }
    }
}

class Destination(ctx: AudioCtx) : Node(ctx) {
    val volume = VolumeParam(ctx)
    override fun render(blockId: Long) {
        if (!sumInputs(blockId)) { silent = true; return }
        silent = false
        val g = volume.gain
        if (g != 1f) for (i in 0 until BLOCK) { outL[i] *= g; outR[i] *= g }
        if (!stereo) System.arraycopy(outL, 0, outR, 0, BLOCK)
    }
}

/** A node that just passes its inputs (Tone's "input"/"output" wrappers). */
class Pass(ctx: AudioCtx) : Node(ctx) { override fun render(blockId: Long) { silent = !sumInputs(blockId) } }


/** Tone.Vibrato: a short delay line swept by a sine (pitch wobble). */
class Vibrato(ctx: AudioCtx, val frequency: Double = 5.0, val depth: Double = 0.1) : Node(ctx) {
    private val maxDelay = 0.005
    private val size = (ctx.sr * maxDelay * 2).toInt() + 4
    private val bl = FloatArray(size); private val br = FloatArray(size)
    private var w = 0; private var ph = 0.0; private var tail = 0
    override fun render(blockId: Long) {
        val any = sumInputs(blockId)
        if (!any) { if (tail <= 0) { silent = true; return }; tail--; java.util.Arrays.fill(outL, 0f); java.util.Arrays.fill(outR, 0f) } else tail = 4
        silent = false
        val dph = frequency / ctx.sr
        for (i in 0 until BLOCK) {
            bl[w] = outL[i]; br[w] = outR[i]
            val d = (maxDelay * (1 + depth * sin(2 * PI * ph)) * ctx.sr)
            var r = w - d; if (r < 0) r += size
            val i0 = r.toInt(); val f = (r - i0).toFloat(); val i1 = (i0 + 1) % size
            outL[i] = bl[i0] + (bl[i1] - bl[i0]) * f; outR[i] = br[i0] + (br[i1] - br[i0]) * f
            w = (w + 1) % size; ph += dph; if (ph >= 1) ph -= 1
        }
    }
}
