package com.ps1bedroom.room.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

/**
 * The HTML streams My Music through an <audio> element into the boombox's speaker (so it's placed and muffled
 * like the radio). Natively: a decoder thread (MediaExtractor + MediaCodec) turns the file into PCM, a small ring
 * buffer hands it to this node on the audio thread, resampled to the engine's rate. Streaming keeps memory low.
 */
class StreamSource(ctx: AudioCtx, private val app: Context) : Node(ctx) {
    @Volatile var playing = false
    @Volatile var positionSec = 0.0; private set
    @Volatile var durationSec = Double.NaN; private set
    /** Called (on the decoder thread) when the song ends, or with an error. */
    var onEnded: (() -> Unit)? = null
    var onError: (() -> Unit)? = null

    // ring buffer of interleaved stereo frames at the file's rate
    private val cap = 1 shl 17
    private val ring = FloatArray(cap * 2)
    @Volatile private var wr = 0L
    @Volatile private var rd = 0L
    @Volatile private var srcRate = 44100
    private var frac = 0.0
    private var decoder: Thread? = null
    @Volatile private var generation = 0
    @Volatile private var seekTo = -1.0
    @Volatile private var finished = false
    private val lockObj = Object()

    fun load(uri: Uri) {
        val gen = ++generation
        playing = false
        synchronized(lockObj) { wr = 0; rd = 0; frac = 0.0; finished = false; positionSec = 0.0; durationSec = Double.NaN; seekTo = -1.0; lockObj.notifyAll() }
        decoder = Thread({ decode(uri, gen) }, "PS1Music").also { it.start() }
    }
    fun seek(sec: Double) { seekTo = sec; synchronized(lockObj) { lockObj.notifyAll() } }
    fun unload() { generation++; playing = false; synchronized(lockObj) { lockObj.notifyAll() } }

    private fun decode(uri: Uri, gen: Int) {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(app, uri, null)
            var track = -1
            for (i in 0 until ex.trackCount) if (ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { track = i; break }
            if (track < 0) throw IllegalStateException("no audio")
            ex.selectTrack(track)
            val fmt = ex.getTrackFormat(track)
            if (fmt.containsKey(MediaFormat.KEY_DURATION)) durationSec = fmt.getLong(MediaFormat.KEY_DURATION) / 1e6
            var channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            srcRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(fmt, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var pcmFloat = false
            while (gen == generation) {
                val s = seekTo
                if (s >= 0) {
                    seekTo = -1.0
                    ex.seekTo((s * 1e6).toLong(), MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    codec.flush(); inputDone = false
                    synchronized(lockObj) { wr = 0; rd = 0; frac = 0.0; finished = false }
                    positionSec = s
                }
                if (!inputDone) {
                    val ii = codec.dequeueInputBuffer(5000)
                    if (ii >= 0) {
                        val buf = codec.getInputBuffer(ii)!!
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0) { codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                        else { codec.queueInputBuffer(ii, 0, n, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val oi = codec.dequeueOutputBuffer(info, 5000)
                if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val of = codec.outputFormat
                    channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT); srcRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    pcmFloat = of.containsKey(MediaFormat.KEY_PCM_ENCODING) && of.getInteger(MediaFormat.KEY_PCM_ENCODING) == android.media.AudioFormat.ENCODING_PCM_FLOAT
                } else if (oi >= 0) {
                    val out = codec.getOutputBuffer(oi)!!.order(ByteOrder.nativeOrder())
                    out.position(info.offset); out.limit(info.offset + info.size)
                    val frames = if (pcmFloat) info.size / 4 / channels else info.size / 2 / channels
                    var f = 0
                    while (f < frames && gen == generation && seekTo < 0) {
                        // wait for room in the ring
                        synchronized(lockObj) { while (wr - rd >= cap - 1 && gen == generation && seekTo < 0) lockObj.wait(50) }
                        val room = (cap - 1 - (wr - rd)).toInt()
                        val n = min(room, frames - f)
                        for (k in 0 until n) {
                            var l: Float; var r: Float
                            if (pcmFloat) { l = out.getFloat(); r = if (channels > 1) out.getFloat() else l; for (c in 2 until channels) out.getFloat() }
                            else { l = out.getShort() / 32768f; r = if (channels > 1) out.getShort() / 32768f else l; for (c in 2 until channels) out.getShort() }
                            val w = ((wr + k) % cap).toInt() * 2
                            ring[w] = l; ring[w + 1] = r
                        }
                        wr += n; f += n
                    }
                    codec.releaseOutputBuffer(oi, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        finished = true
                        // let the ring drain, then report the end
                        while (gen == generation && rd < wr && seekTo < 0) Thread.sleep(30)
                        if (gen == generation && seekTo < 0) { playing = false; onEnded?.invoke() }
                        break
                    }
                }
            }
        } catch (e: Exception) {
            if (gen == generation) { playing = false; onError?.invoke() }
        } finally {
            try { codec?.stop(); codec?.release() } catch (e: Exception) { }
            ex.release()
        }
    }

    override fun render(blockId: Long) {
        if (!playing) { silent = true; return }
        val step = srcRate / ctx.sr
        var any = false
        for (i in 0 until BLOCK) {
            if (rd + 1 >= wr) { outL[i] = 0f; outR[i] = 0f; continue }
            val i0 = (rd % cap).toInt() * 2; val i1 = ((rd + 1) % cap).toInt() * 2
            val f = frac.toFloat()
            outL[i] = ring[i0] + (ring[i1] - ring[i0]) * f
            outR[i] = ring[i0 + 1] + (ring[i1 + 1] - ring[i0 + 1]) * f
            frac += step
            val adv = frac.toLong(); frac -= adv; rd += adv
            positionSec += step / srcRate
            any = true
        }
        if (any) synchronized(lockObj) { lockObj.notifyAll() }
        silent = !any; stereo = true
    }
    @Suppress("unused") private fun unused() = max(0, 0)
}
