package com.ps1bedroom.room.audio

import com.ps1bedroom.room.App
import com.ps1bedroom.room.Places
import com.ps1bedroom.room.Tv
import com.ps1bedroom.room.engine.Mesh
import com.ps1bedroom.room.engine.Object3D
import com.ps1bedroom.room.engine.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * HTML 8669-10607 (+ the places' sounds, 15611-15715): the room's sound engine. Every source is placed at its object
 * (distance gain + stereo pan from the camera), each owns its own frequency band, outdoor sound comes in through
 * the window (muffled by the blinds), the living-room TV comes through the door, and a small room reverb + bus
 * compressor glue it together. Runs on [AudioCtx]'s thread; everything here is called from the GL thread.
 */
class Sound(private val app: App) {
    val ctx = AudioCtx()
    var ready = false; private set
    var playing = false; private set
    var wantAudio = true
    private val rnd = Random.Default
    private fun rand(a: Double, b: Double) = a + rnd.nextDouble() * (b - a)
    private fun <T> pick(l: List<T>) = l[rnd.nextInt(l.size)]

    // User volume multipliers (0.0 to 1.5) from the Soundscape Mixer
    val MIX_VERSION = 2
    val vol = linkedMapOf("pc" to 0.75, "fan" to 0.70, "ac" to 0.65, "livtv" to 0.35, "bedtv" to 1.00, "pcspk" to 0.75, "weather" to 0.90, "nature" to 0.80, "sfx" to 0.80, "radio" to 0.90)
    @set:JvmName("assignMasterVolume") var masterVolume = 0.85
    var livingRoomTvOn = true
    private val env get() = app.env

    // ---- placed sources ----
    private inner class Spot(var pos: Vec3, val ref: Double, val rolloff: Double, verb: Double, dest: Node, level: Double) {
        val level = Gain(ctx, level)
        val dist = Gain(ctx, 1.0)
        val pan = StereoPanner(ctx)
        var send: Gain? = null
        var follow: Object3D? = null
        var lastGain = -1.0; var lastPan = -9.0
        init {
            this.level.connect(dist); dist.connect(pan); pan.connect(dest)
            if (verb > 0) { send = Gain(ctx, verb).also { s -> pan.connect(s); s.connect(verbIn) } }
            spots += this
            update(ctx.currentTime, true)
        }
        fun update(t: Double, immediate: Boolean = false) {
            follow?.let { o -> o.updateMatrixWorld(); pos.setFromMatrixPosition(o.matrixWorld) }
            val cam = app.camera
            cam.updateMatrixWorld()
            val e = cam.matrixWorld.e
            fwd.set(-e[8], -e[9], -e[10]).normalize()
            right.crossVectors(fwd, cam.up).normalize()
            delta.set(pos.x - cam.position.x, pos.y - cam.position.y, pos.z - cam.position.z)
            val d = max(0.001, delta.length().toDouble())
            val g = ref / (ref + rolloff * (max(d, ref) - ref))
            val p = max(-1.0, min(1.0, (delta.x * right.x + delta.y * right.y + delta.z * right.z) / d)) * 0.8
            if (immediate) { dist.gain.value = g; pan.pan.value = p; lastGain = g; lastPan = p }
            else {
                if (abs(g - lastGain) > 0.004) { dist.gain.setTargetAtTime(g, t, 0.05); lastGain = g }
                if (abs(p - lastPan) > 0.01) { pan.pan.setTargetAtTime(p, t, 0.05); lastPan = p }
            }
        }
        fun remove() { spots.remove(this); this.level.dispose(); dist.dispose(); pan.dispose(); send?.dispose() }
    }
    private val spots = ArrayList<Spot>()
    private val fwd = Vec3(); private val right = Vec3(); private val delta = Vec3()
    private var lastSpatial = 0L
    /** Called every frame; recomputes placement ~12x a second. */
    fun updateListener() {
        if (!ready) return
        val now = System.currentTimeMillis()
        if (now - lastSpatial < 80) return
        lastSpatial = now
        ctx.sync { val t = ctx.currentTime; for (s in spots) s.update(t) }
    }

    private fun filt(frequency: Double = 1000.0, type: String = "lowpass", Q: Double = 1.0) = Filter(ctx, type, frequency, Q)
    private fun gain(g: Double) = Gain(ctx, g)

    // ---- the graph (HTML's SND) ----
    private lateinit var master: Gain
    private lateinit var trim: Gain
    private lateinit var verbIn: Filter
    private lateinit var outdoor: Gain
    private lateinit var windowFilter: Filter
    private lateinit var windowLevel: Gain
    private lateinit var farIn: Gain
    private lateinit var weatherBus: Gain
    private lateinit var natureBus: Gain
    private val pos = HashMap<String, Vec3>()
    private lateinit var pc: Spot; private lateinit var pcSpin: Gain; private lateinit var pcFanFilter: Filter; private lateinit var hddTick: NoiseSynth
    private lateinit var spk: Spot; private lateinit var spkPower: Gain; private lateinit var spkBus: Gain
    private lateinit var fan: Spot; private lateinit var fanSpeed: Gain; private lateinit var fanFilter: Filter; private lateinit var fanLfo: Lfo; private lateinit var fanTick: NoiseSynth
    private lateinit var ac: Spot; private lateinit var acRun: Gain
    private lateinit var tv: Spot; private lateinit var tvPower: Gain; private lateinit var tvBus: Gain
    private lateinit var hall: Spot; private lateinit var hallPower: Gain; private lateinit var hallMuffle: Filter; private lateinit var hallMuffle2: Filter; private lateinit var hallBoost: Gain
    private lateinit var lrVoices: List<Voice>; private lateinit var lrLaugh: Gain
    private lateinit var radio: Spot; private lateinit var radioBus: Gain
    private lateinit var lava: Spot; private lateinit var lavaBloop: Synth
    private lateinit var ps1Drive: Spot; private lateinit var ps1Seek: NoiseSynth
    private lateinit var rain: Gain; private lateinit var glassSpot: Spot; private lateinit var rainGlass: Gain; private lateinit var rainHeavy: Gain
    private lateinit var wind: Gain; private lateinit var windFilter: Filter
    private lateinit var thunderBufs: List<AudioBuffer>; private lateinit var thunderFilter: Filter
    private lateinit var crickets: Gain; private lateinit var leaves: Gain; private lateinit var breeze: Gain
    private lateinit var howl: Gain; private lateinit var howlFilter: Filter; private lateinit var city: Gain
    private lateinit var birds: List<Synth>; private lateinit var peeper: Synth
    private lateinit var passBus: Gain
    private class PassVoice(val filt: Filter, val env: Gain, val spot: Any, var busyUntil: Double = 0.0)
    private lateinit var passVoices: List<PassVoice>
    private val lfos = ArrayList<Lfo>()

    private fun tagPos(tag: String, fallback: Vec3): Vec3 {
        val v = Vec3(); var n = 0
        for (m in app.room.interactables) {
            if (m.userData["interactTag"] != tag) continue
            val g = m.geometry
            if (g.boundingRadius < 0) g.computeBoundingSphere()
            m.updateMatrixWorld()
            val c = Vec3().copy(g.boundingCenter).applyMatrix4(m.matrixWorld)
            v.x += c.x; v.y += c.y; v.z += c.z; n++
        }
        return if (n == 0) fallback else v.set(v.x / n, v.y / n, v.z / n)
    }
    private fun worldPos(o: Object3D): Vec3 { o.updateMatrixWorld(); return Vec3().setFromMatrixPosition(o.matrixWorld) }

    private fun build() = ctx.sync {
        ctx.transport.bpm = 100.0
        // ---- Master: gentle bus compression + limiter so stacked layers glue instead of clipping
        master = gain(0.0)
        val comp = Compressor(ctx, -22.0, 2.5, 0.03, 0.3, 12.0)
        val limiter = Compressor(ctx, -6.0, 20.0, 0.003, 0.1)
        // Soft clipper last: clean below -3 dB, rounding off anything louder (fed at half level and mapped back)
        val clipIn = gain(0.5)
        val softClip = WaveShaper(ctx) { x -> val v = x * 2; val a = abs(v); val knee = 0.7; if (a <= knee) v else Math.signum(v) * (knee + (1 - knee) * tanh((a - knee) / (1 - knee))) }
        trim = gain(0.85)
        master.chain(comp, limiter, trim, clipIn, softClip, ctx.destination)
        // Small bedroom reverb (high-passed send keeps the low end clean)
        val roomVerb = Reverb(ctx, 0.9, 0.008, 1.0)
        verbIn = filt(320.0, "highpass")
        verbIn.chain(roomVerb, gain(0.5), master)
        // ---- Outdoor: everything outside is heard through the window opening, muffled by the blinds
        outdoor = gain(1.0)
        windowFilter = filt(5500.0, "lowpass")
        val windowSpot = Spot(Vec3(0.8f, 2.6f, -3.0f), 2.2, 0.8, 0.0, windowFilter, 1.0)
        outdoor.connect(windowSpot.level)
        windowLevel = gain(1.0)
        windowFilter.connect(windowLevel)
        windowLevel.connect(master)
        val winSend = gain(0.1); windowLevel.connect(winSend); winSend.connect(verbIn)
        // Long outdoor reverb for distant horns, owls and thunder (shorter tail on phones)
        val farVerb = Reverb(ctx, 2.2, 0.03, 1.0)
        farIn = gain(1.0)
        farIn.chain(farVerb, gain(0.55), outdoor)
        weatherBus = gain(vol.getValue("weather")); weatherBus.connect(outdoor)
        natureBus = gain(vol.getValue("nature")); natureBus.connect(outdoor)
        val natureFar = gain(0.2); natureBus.connect(natureFar); natureFar.connect(farIn)

        pos["pc"] = tagPos("pc", Vec3(-1.6f, 2.2f, -1.4f))
        pos["monitor"] = worldPos(app.desk.monScreen)
        pos["phone"] = tagPos("phone", Vec3(-1.7f, 1.7f, -1.0f))
        pos["lava"] = tagPos("lavalamp", Vec3(0.0f, 1.9f, -1.2f))
        pos["ps1"] = tagPos("ps1Body", Vec3(-1.2f, 0.1f, 0.6f))
        pos["tv"] = tagPos("tv", Vec3(-2.05f, 1.3f, 0.65f))
        pos["door"] = tagPos("door", Vec3(2.9f, 1.4f, 3.45f))
        pos["blinds"] = Vec3(0.8f, 2.6f, -2.95f)
        pos["fan"] = Vec3(0.6f, 4.65f, -0.4f)
        pos["ac"] = tagPos("ac", Vec3(-3.6f, 3.4f, -1.0f))
        pos["cat"] = tagPos("cat", Vec3(2.55f, 3.38f, -2.82f))
        pos["boombox"] = tagPos("boombox", Vec3(-3.4f, 1.6f, 2.3f))
        pos["bed"] = tagPos("bed", Vec3(2.7f, 0.7f, -0.6f))

        // ---- PC tower: small-fan whirr (mid band), blade tone, PSU hum and IDE seek chatter
        pc = Spot(pos.getValue("pc"), 1.2, 1.0, 0.12, master, 0.0)
        pcSpin = gain(if (app.monitor.pcPoweredOn) 1.0 else 0.0); pcSpin.connect(pc.level)
        pcFanFilter = filt(1100.0, "bandpass", 0.7)
        Noise(ctx, "pink").start().chain(pcFanFilter, gain(0.6), pcSpin)
        Oscillator(ctx, 233.0, "sine").start().chain(gain(0.02), pcSpin)
        Oscillator(ctx, 120.0, "sine").start().chain(gain(0.04), pcSpin)
        hddTick = NoiseSynth(ctx, "white", Envelope(ctx, 0.0005, 0.012, 0.0, 0.005))
        hddTick.chain(filt(2600.0, "bandpass", 1.5), gain(0.6), pc.level)
        // ---- PC speakers (game audio from the monitor)
        spk = Spot(pos.getValue("monitor"), 1.3, 1.0, 0.2, master, 0.0)
        spkPower = gain(if (app.monitor.pcPoweredOn) 1.0 else 0.0)
        spkBus = gain(2.5)
        spkBus.chain(filt(150.0, "highpass"), filt(7000.0, "lowpass"), spkPower, spk.level)
        // ---- Ceiling fan: low blade whoosh pulsing at blade-pass rate, motor hum, once-per-revolution wobble tick
        fan = Spot(pos.getValue("fan"), 2.0, 1.0, 0.15, master, 0.0)
        fanSpeed = gain(if (app.fan.fanSpinning) 1.0 else 0.0); fanSpeed.connect(fan.level)
        fanFilter = filt(340.0, "bandpass", 0.8)
        val fanPulse = gain(0.7)
        fanLfo = Lfo(ctx, fanPulse.gain, 2.6, 0.45, 1.0).also { lfos += it }
        Noise(ctx, "brown").start().chain(fanFilter, fanPulse, gain(1.0), fanSpeed)
        Oscillator(ctx, 98.0, "sine").start().chain(gain(0.025), fanSpeed)
        fanTick = NoiseSynth(ctx, "pink", Envelope(ctx, 0.001, 0.02, 0.0, 0.01))
        fanTick.chain(filt(1800.0, "bandpass", 2.0), gain(0.15), fanSpeed)
        // ---- Wall AC: broadband air rush, 60 Hz compressor drone, fluttering louvre rattle
        ac = Spot(pos.getValue("ac"), 1.6, 1.0, 0.15, master, 0.0)
        acRun = gain(if (app.room.acUnitOn) 1.0 else 0.0); acRun.connect(ac.level)
        Noise(ctx, "pink").start().chain(filt(650.0, "bandpass", 0.45), filt(2600.0, "lowpass"), gain(0.75), acRun)
        Oscillator(ctx, 60.0, "sawtooth").start().chain(filt(160.0, "lowpass"), gain(0.08), acRun)
        val acRattle = gain(0.0)
        lfos += Lfo(ctx, acRattle.gain, 11.0, 0.0, 0.06)
        Noise(ctx, "white").start().chain(filt(1300.0, "bandpass", 6.0), acRattle, acRun)
        // ---- Bedroom CRT TV: everything goes through a small boxy speaker
        tv = Spot(pos.getValue("tv"), 1.4, 1.0, 0.22, master, 0.0)
        tvPower = gain(if (app.tv.bedroomTvOn) 1.0 else 0.0)
        tvBus = gain(1.8)
        tvBus.chain(filt(240.0, "highpass"), filt(4200.0, "lowpass"), tvPower, tv.level)
        Oscillator(ctx, 60.0, "square").start().chain(filt(200.0, "lowpass"), gain(0.012), tvPower)
        // ---- Living-room TV through the closed door, from down the hall: two steep low-passes, a boxy resonance, quiet
        val door = pos.getValue("door")
        hall = Spot(Vec3(door.x + 0.1f, 1.2f, door.z + 2.4f), 1.0, 1.4, 0.08, master, 0.0)
        hallPower = gain(if (livingRoomTvOn) 1.0 else 0.0)
        hallMuffle = filt(380.0, "lowpass", 0.5)
        hallMuffle2 = filt(650.0, "lowpass", 0.7)
        val hallBody = filt(240.0, "peaking", 1.2).also { it.gain.value = 4.0 }
        hallBoost = gain(0.55)
        hallMuffle.chain(hallMuffle2, hallBody, hallBoost, hallPower, hall.level)
        lrVoices = listOf(Voice(ctx, hallMuffle, 115.0, 4.0), Voice(ctx, hallMuffle, 205.0, 3.2))
        lrLaugh = gain(0.0)
        val laughTrem = gain(1.0)
        lfos += Lfo(ctx, laughTrem.gain, 7.0, 0.2, 1.0)
        Noise(ctx, "pink").start().chain(filt(900.0, "bandpass", 0.9), laughTrem, lrLaugh, hallMuffle)
        Noise(ctx, "brown").start().chain(filt(300.0, "lowpass"), gain(0.05), hallMuffle)
        // ---- Boombox radio on the dresser (small twin speakers)
        radio = Spot(pos.getValue("boombox"), 1.4, 1.0, 0.2, master, 0.0)
        radioBus = gain(2.0)
        radioBus.chain(filt(110.0, "highpass"), filt(6500.0, "lowpass"), radio.level)
        // ---- Lava lamp
        lava = Spot(pos.getValue("lava"), 1.0, 1.0, 0.3, master, 0.0)
        lavaBloop = Synth(ctx, "sine", Envelope(ctx, 0.02, 0.2, 0.5, 0.15)).also { it.volume.value = -12.0 }
        lavaBloop.chain(filt(600.0, "lowpass"), lava.level)
        // ---- PlayStation disc drive: spindle hum + laser-sled whirr while the disc spins, seek ticks while reading
        ps1Drive = Spot(pos.getValue("ps1"), 0.9, 1.0, 0.1, master, 0.0)
        Oscillator(ctx, 92.0, "sine").start().chain(gain(0.03), ps1Drive.level)
        Noise(ctx, "pink").start().chain(filt(1500.0, "bandpass", 1.2), gain(0.08), ps1Drive.level)
        ps1Seek = NoiseSynth(ctx, "white", Envelope(ctx, 0.0005, 0.01, 0.0, 0.005))
        ps1Seek.chain(filt(3000.0, "bandpass", 2.0), gain(0.35), ps1Drive.level)
        // ---- Weather layers
        rain = gain(0.0); rain.connect(weatherBus)
        Noise(ctx, "pink").start().chain(filt(500.0, "highpass"), filt(7500.0, "lowpass"), gain(1.0), rain)
        val drops = Player(ctx, Buffers.ticks(ctx, 4.0, 260.0, 0.002, 0.008, 2.2)); drops.loop = true
        drops.chain(filt(3200.0, "bandpass", 0.5), gain(0.85), rain); drops.start()
        // Rain on the window glass: a close, soft patter right at the window (inside the room, not muffled by it)
        glassSpot = Spot(Vec3(0.8f, 2.6f, -2.9f), 1.8, 1.0, 0.12, master, vol.getValue("weather"))
        rainGlass = gain(0.0); rainGlass.connect(glassSpot.level)
        val glassPat = gain(0.7)
        lfos += Lfo(ctx, glassPat.gain, 0.21, 0.18, 0.7)
        Noise(ctx, "pink").start().chain(filt(1800.0, "bandpass", 0.45), filt(5200.0, "lowpass"), glassPat, rainGlass)
        rainHeavy = gain(0.0); rainHeavy.connect(weatherBus)
        Noise(ctx, "brown").start().chain(filt(200.0, "lowpass"), gain(0.6), rainHeavy)
        // Wind: gusts from an LFO, the band-pass sweep is steered from the frame loop
        wind = gain(0.0); wind.connect(weatherBus)
        windFilter = filt(500.0, "bandpass", 1.2)
        val windGust = gain(0.6)
        lfos += Lfo(ctx, windGust.gain, 0.045, 0.25, 1.0)
        Noise(ctx, "brown").start().chain(windFilter, windGust, gain(1.5), wind)
        thunderBufs = listOf(Buffers.thunder(ctx), Buffers.thunder(ctx))
        thunderFilter = filt(900.0, "lowpass"); thunderFilter.connect(weatherBus)
        val thunderFar = gain(0.5); thunderFilter.connect(thunderFar); thunderFar.connect(farIn)
        // ---- Season / time layers
        crickets = gain(0.0); crickets.chain(filt(6000.0, "lowpass"), natureBus)
        val cr = Player(ctx, Buffers.crickets(ctx)); cr.loop = true; cr.chain(gain(0.12), crickets); cr.start()
        leaves = gain(0.0); leaves.connect(natureBus)
        val leafGust = gain(0.5); lfos += Lfo(ctx, leafGust.gain, 0.13, 0.15, 1.0)
        Noise(ctx, "pink").start().chain(filt(2400.0, "bandpass", 0.8), leafGust, gain(0.35), leaves)
        breeze = gain(0.0); breeze.connect(natureBus)
        val breezeGust = gain(0.6); lfos += Lfo(ctx, breezeGust.gain, 0.09, 0.3, 1.0)
        Noise(ctx, "pink").start().chain(filt(900.0, "lowpass"), breezeGust, gain(0.35), breeze)
        howl = gain(0.0); howl.connect(natureBus)
        howlFilter = filt(550.0, "bandpass", 6.0)
        val howlGust = gain(0.6); lfos += Lfo(ctx, howlGust.gain, 0.035, 0.2, 1.0)
        Noise(ctx, "brown").start().chain(howlFilter, howlGust, gain(2.5), howl)
        city = gain(0.0); city.connect(natureBus)
        Noise(ctx, "brown").start().chain(filt(220.0, "lowpass"), gain(0.5), city)
        birds = List(2) { Synth(ctx, "sine", Envelope(ctx, 0.004, 0.05, 0.8, 0.03)).also { s -> s.volume.value = -22.0; s.connect(natureBus) } }
        peeper = Synth(ctx, "sine", Envelope(ctx, 0.005, 0.03, 0.7, 0.02)).also { it.volume.value = -28.0; it.connect(natureBus) }
        // Highway pass-bys: two moving voices that follow the 3D cars across the window
        passBus = gain(vol.getValue("nature")); passBus.connect(windowFilter)
        passVoices = List(2) {
            val band = filt(400.0, "bandpass", 1.0)
            val spot = Spot(Vec3(0f, 0.9f, -6f), 3.0, 1.0, 0.0, passBus, 1.0)
            val e = gain(0.0)
            Noise(ctx, "pink").start().chain(band, e, spot.level)
            PassVoice(band, e, spot)
        }
        buildOutsideEnvSound()
    }

    // ---------------- channels: only the TV / PC / radio channel that's on exists ----------------
    private class Active(val index: Int, val variant: Int, val bag: Bag, val out: Gain, val api: ChannelApi)
    private val active = HashMap<String, Active?>()
    private fun setActiveChannel(kind: String, index: Int?, variant: Int = 0) {
        val current = active[kind]
        if (current != null && current.index == index && current.variant == variant) return
        if (current != null) {
            current.out.gain.rampTo(0.0, 0.05)
            val at = ctx.currentTime + 0.4
            pendingDispose += at to { current.bag.disposeAll() }
        }
        active[kind] = null
        if (index == null) return
        val bag = Bag(ctx)
        val out = bag.n(gain(0.0))
        out.connect(when (kind) { "pc" -> spkBus; "tv" -> tvBus; else -> radioBus })
        val b = when (kind) {
            "pc" -> Music.PC_CHANNELS[index]
            "tv" -> Music.tvChannel(index, variant, if (index == Tv.PS1_TV_CHANNEL) app.library.currentGame().music else null)
            else -> Music.RADIO_CHANNELS[index]
        }
        val api = b(out, bag)
        out.gain.rampTo(1.0, 0.08)
        active[kind] = Active(index, variant, bag, out, api)
    }
    private val pendingDispose = ArrayList<Pair<Double, () -> Unit>>()
    private fun runPendingDisposals() {
        val now = ctx.currentTime
        val due = pendingDispose.filter { it.first <= now }
        if (due.isEmpty()) return
        pendingDispose.removeAll(due.toSet())
        for ((_, fn) in due) fn()
    }

    // ---- Called whenever any setting changes: crossfades every layer to its target ----
    fun updateLayered() {
        if (!ready) return
        ctx.sync {
            val fade = 1.2
            val W = env.W
            val isStorm = env.currentWeather == "storm"
            val isPrecip = env.isPrecipNow()
            val isFog = env.currentWeather == "foggy"
            val isWinter = env.currentSeason == "winter"
            val ct = env.currentTime
            val night = ct == "night"; val dusk = ct == "dusk"; val dawn = ct == "dawn"; val day = ct == "day"
            val outdoorDuck = if (isStorm) 0.25 else if (isPrecip) 0.6 else 1.0
            // Mixer sliders
            pc.level.gain.rampTo(vol.getValue("pc"), 0.2)
            spk.level.gain.rampTo(vol.getValue("pcspk"), 0.2)
            fan.level.gain.rampTo(vol.getValue("fan"), 0.2)
            ac.level.gain.rampTo(vol.getValue("ac"), 0.2)
            tv.level.gain.rampTo(vol.getValue("bedtv"), 0.2)
            hall.level.gain.rampTo(vol.getValue("livtv"), 0.2)
            weatherBus.gain.rampTo(vol.getValue("weather"), 0.2)
            natureBus.gain.rampTo(vol.getValue("nature"), 0.2)
            passBus.gain.rampTo(vol.getValue("nature") * outdoorDuck, 0.5)
            lava.level.gain.rampTo(vol.getValue("sfx"), 0.2)
            val ps1 = app.ps1
            val driving = ps1.ps1On && !ps1.lidOpen
            ps1Drive.level.gain.rampTo(if (driving) vol.getValue("sfx") else 0.0, if (driving) 1.2 else 0.8)
            // Appliances
            acRun.gain.rampTo(if (app.room.acUnitOn) 1.0 else 0.0, if (app.room.acUnitOn) 1.6 else 2.2)
            spkPower.gain.rampTo(if (app.monitor.pcPoweredOn) 1.0 else 0.0, 0.3)
            hallPower.gain.rampTo(if (livingRoomTvOn) 1.0 else 0.0, 0.8)
            hallMuffle.frequency.setTargetAtTime(380.0, ctx.currentTime, 0.3)   // (the door stays shut for now)
            hallMuffle2.frequency.setTargetAtTime(650.0, ctx.currentTime, 0.3)
            hallBoost.gain.rampTo(0.55, 0.6)
            tvPower.gain.rampTo(if (app.tv.bedroomTvOn) 1.0 else 0.0, if (app.tv.bedroomTvOn) 0.4 else 0.15)
            setActiveChannel("pc", if (app.monitor.pcPoweredOn) app.monitor.currentChannel else null)
            val ch = app.tv.bedroomTvChannel
            setActiveChannel("tv", if (app.tv.bedroomTvOn) ch else null, if (ch == Tv.PS1_TV_CHANNEL) app.library.insertedGame else (if (ch == 2) app.extras.currentTape else 0))
            setActiveChannel("radio", if (app.extras.radioStation > 0) app.extras.radioStation - 1 else null)
            radio.level.gain.rampTo(vol.getValue("radio"), 0.2)
            // Weather
            val raining = env.isRainingNow(); val snowing = env.isSnowingNow()
            rain.gain.rampTo(if (raining) 0.25 + W.rain * 0.75 else 0.0, fade)
            rainHeavy.gain.rampTo(if (raining && W.rain > 0.75f) (W.rain - 0.5) * 2 else 0.0, fade)
            glassSpot.level.gain.rampTo(vol.getValue("weather"), 0.2)
            rainGlass.gain.rampTo(if (raining) (0.12 + W.rain * 0.4) * (if (app.room.blindsOpenState == 2) 0.7 else 1.0) else 0.0, fade)
            // Falling snow is hushed: just a soft breath of wind (a blizzard howls)
            wind.gain.rampTo(maxOf(if (isStorm) 1.0 else 0.0, W.wind * 0.85, if (snowing) 0.18 else 0.0, if (isFog) 0.12 else 0.06), fade)
            // Season & time of day (no crickets in rain, wildlife hushes in storms)
            val cricketLvl = if (env.currentSeason == "summer" && (night || dusk)) 1.0 else (if (env.currentSeason == "fall" && night) 0.35 else 0.0)
            crickets.gain.rampTo(if (isPrecip) 0.0 else cricketLvl, fade)
            leaves.gain.rampTo(if (env.currentSeason == "fall") 0.8 * outdoorDuck else 0.0, fade)
            val breezeLvl = mapOf("summer" to 0.3, "spring" to 0.5, "fall" to 0.2, "winter" to 0.0).getValue(env.currentSeason)
            breeze.gain.rampTo(breezeLvl * outdoorDuck, fade)
            howl.gain.rampTo(if (isWinter) (if (night) 0.7 else 0.5) * (if (isStorm) 0.6 else (if (isPrecip) 0.45 else 1.0)) else 0.0, fade)
            city.gain.rampTo((if (day) 0.9 else (if (dawn || dusk) 0.6 else 0.35)) * (if (isStorm) 0.4 else 1.0) * app.places.outsideCityFactor(), fade)
            // The window: blinds and snow / fog muffle everything outside
            val winHz = doubleArrayOf(5500.0, 9000.0, 2000.0, 10000.0)[app.room.blindsOpenState]
            val muffle = if (snowing) 0.6 else (if (isFog) 0.75 else 1.0)
            windowFilter.frequency.setTargetAtTime(winHz * muffle, ctx.currentTime, 0.2)
            windowLevel.gain.rampTo(if (app.room.blindsOpenState == 2) 0.6 else 1.0, 0.6)
            updateOutsideEnvSound()
        }
    }

    // ---- PC power: fan spin-up / down, PSU hum, boot HDD chatter ----
    private var hddBusyUntil = 0.0
    private fun soundPcPower(on: Boolean) {
        val t = ctx.currentTime
        pcSpin.gain.cancelScheduledValues(t); pcFanFilter.frequency.cancelScheduledValues(t)
        if (on) {
            pcSpin.gain.setValueAtTime(0.001, t); pcSpin.gain.exponentialRampToValueAtTime(1.0, t + 2.2)
            pcFanFilter.frequency.setValueAtTime(300.0, t); pcFanFilter.frequency.exponentialRampToValueAtTime(1100.0, t + 2.2)
            var tt = t + 1.6
            for (i in 0 until 14) { hddTick.triggerAttackRelease(0.01, tt, rand(0.5, 1.0)); tt += rand(0.04, 0.09) }
            hddBusyUntil = tt
        } else {
            pcSpin.gain.setValueAtTime(max(0.001, pcSpin.gain.getValueAtTime(t)), t); pcSpin.gain.exponentialRampToValueAtTime(0.001, t + 2.4)
            pcFanFilter.frequency.setValueAtTime(1100.0, t); pcFanFilter.frequency.exponentialRampToValueAtTime(250.0, t + 2.4)
        }
    }

    private var birdNext = 0
    private val birdBusy = DoubleArray(2)
    private fun birdCall(t: Double) {
        birdNext = (birdNext + 1) % birds.size
        val s = birds[birdNext]
        if (t < birdBusy[birdNext]) return
        val base = rand(2200.0, 3600.0)
        val type = rnd.nextDouble()
        if (type < 0.4) {
            val n = 2 + rnd.nextInt(3)
            for (i in 0 until n) { val tt = t + i * 0.13; s.triggerAttack(base, tt, 0.6); s.frequency.exponentialRampToValueAtTime(base * 1.35, tt + 0.07); s.triggerRelease(tt + 0.08) }
            birdBusy[birdNext] = t + n * 0.13 + 0.1
        } else if (type < 0.7) {
            for (i in 0 until 8) s.triggerAttackRelease(if (i % 2 == 1) base * 1.12 else base, 0.03, t + i * 0.045, 0.4)
            birdBusy[birdNext] = t + 0.45
        } else {
            s.triggerAttack(base * 0.8, t, 0.5)
            s.frequency.linearRampToValueAtTime(base * 1.05, t + 0.12)
            s.frequency.linearRampToValueAtTime(base * 0.9, t + 0.3)
            s.triggerRelease(t + 0.32)
            birdBusy[birdNext] = t + 0.4
        }
    }

    /** A car crossing the window gets a moving whoosh. */
    fun carPass(car: Object3D, speed: Double, isTruck: Boolean) {
        if (!ready || !playing) return
        ctx.sync {
            val t = ctx.currentTime + 0.05
            val v = passVoices.firstOrNull { it.busyUntil < t } ?: return@sync
            val dur = 6.6 / max(0.3, speed)
            val wet = env.isPrecipNow()
            v.busyUntil = t + dur
            @Suppress("UNCHECKED_CAST") val spot = v.spot as Spot
            spot.follow = car
            pendingDispose += (t + dur + 0.2) to { if (v.busyUntil <= ctx.currentTime + 0.1) spot.follow = null }
            val peak = (if (isTruck) 0.5 else 0.32) * (if (wet) 1.2 else 1.0)
            v.env.gain.cancelScheduledValues(t); v.env.gain.setValueAtTime(0.0, t)
            v.env.gain.linearRampToValueAtTime(peak, t + dur * 0.5); v.env.gain.linearRampToValueAtTime(0.0, t + dur)
            val f0 = if (wet) 1800.0 else (if (isTruck) 380.0 else 520.0)
            v.filt.Q.value = if (wet) 0.6 else 1.1
            v.filt.frequency.cancelScheduledValues(t); v.filt.frequency.setValueAtTime(f0, t)
            v.filt.frequency.linearRampToValueAtTime(f0 * 1.08, t + dur * 0.5); v.filt.frequency.exponentialRampToValueAtTime(f0 * 0.5, t + dur)
        }
    }

    /** Lightning: thunder arrives 0.15-2.8 s after the flash, quieter and darker the farther it is. */
    fun thunderclap() {
        if (!ready || !playing) return
        ctx.sync {
            val distance = rnd.nextDouble()
            val t = ctx.currentTime + 0.15 + distance * 2.6
            val p = Player(ctx, pick(thunderBufs))
            p.playbackRate = rand(0.75, 1.1)
            p.volume.value = -2 - distance * 9
            p.connect(thunderFilter)
            thunderFilter.frequency.setValueAtTime(1600 - distance * 1200, t)
            p.onstop = { p.dispose() }
            p.start(t)
        }
    }

    // ---- Every ~0.18 s: fan dynamics, slow sweeps, random one-shots ----
    private var lastPeriodic = 0f
    private var lastFanSp = -1.0
    private var lastFanRev = 0
    private var lavaBusyUntil = 0.0
    private var lastPs1Phase = ""
    private var ps1SeekBusy = 0.0
    private var nextFoghorn = 0.0
    fun periodic(t: Float) {
        if (!ready || !playing || t - lastPeriodic < 0.18f) return
        lastPeriodic = t
        try { ctx.sync { runPeriodic(t); runPendingDisposals(); crittersTick() } } catch (e: Exception) { /* a one-shot landed on a busy synth: skip it */ }
    }
    private fun runPeriodic(t: Float) {
        val now = ctx.currentTime + 0.05
        val isStorm = env.currentWeather == "storm"
        val isPrecip = env.isPrecipNow()
        val isWinter = env.currentSeason == "winter"
        val ct = env.currentTime
        val night = ct == "night"; val dusk = ct == "dusk"; val dawn = ct == "dawn"; val day = ct == "day"
        // Ceiling fan follows the real (coasting) blade speed
        val sp = min(1.0, app.fan.speed / 1.85)
        if (abs(sp - lastFanSp) > 0.003) {
            lastFanSp = sp
            fanSpeed.gain.rampTo(sp, 0.15)
            fanLfo.osc.frequency.setTargetAtTime(max(0.05, app.fan.speed * 1.4), now, 0.1)
            fanFilter.frequency.setTargetAtTime(170 + 170 * sp, now, 0.05)
        }
        val rev = floor(app.fan.angle / (PI * 2)).toInt()
        if (rev != lastFanRev) { lastFanRev = rev; if (sp > 0.2) fanTick.triggerAttackRelease(0.02, now, 0.6 * sp) }
        // Slow sweeps steered from here
        windFilter.frequency.setTargetAtTime(600 + 350 * sin(t * 0.44), now, 0.4)
        howlFilter.frequency.setTargetAtTime(550 + 170 * sin(t * 0.31 + 1), now, 0.4)
        val tvApi = active["tv"]?.api
        val pcApi = active["pc"]?.api
        tvApi?.droneFilter?.frequency?.setTargetAtTime(490 + 210 * sin(t * 0.38), now, 0.4)
        pcApi?.engine?.frequency?.rampTo(51 + 11 * sin(t * 1.13), 0.2, exponential = true)
        // IDE hard-drive seek chatter
        if (app.monitor.pcPoweredOn && now > hddBusyUntil && rnd.nextDouble() < 0.12) {
            val n = 1 + rnd.nextInt(5); var tt = now
            for (i in 0 until n) { hddTick.triggerAttackRelease(0.01, tt, rand(0.4, 1.0)); tt += rand(0.03, 0.07) }
            hddBusyUntil = tt
        }
        // Lava lamp: a soft wax "bloop" now and then
        if (now > lavaBusyUntil && rnd.nextDouble() < 0.025) {
            lavaBusyUntil = now + 0.5
            val f = rand(170.0, 260.0)
            lavaBloop.triggerAttack(f, now, 0.6); lavaBloop.frequency.exponentialRampToValueAtTime(f * 0.6, now + 0.3); lavaBloop.triggerRelease(now + 0.32)
        }
        // PC game audio events
        if (app.monitor.pcPoweredOn && pcApi != null) {
            val gun = pcApi.gun; val boom = pcApi.boom; val beep = pcApi.beep
            if (gun != null && boom != null && beep != null && now > pcApi.busyUntil) {
                pcApi.busyUntil = now + 2.2
                if (rnd.nextDouble() < 0.06) { val n = 3 + rnd.nextInt(5); for (i in 0 until n) { gun.triggerAttackRelease(0.05, now + i * 0.1, rand(0.7, 1.0)); boom.triggerAttackRelease("A1", 0.05, now + i * 0.1, 0.7) } }
                else if (rnd.nextDouble() < 0.04) { boom.triggerAttackRelease("C3", 0.03, now, 0.3); boom.triggerAttackRelease("C3", 0.03, now + 0.32, 0.25) }
                else if (rnd.nextDouble() < 0.02) { for (i in 0 until 4) beep.triggerAttackRelease(1600.0, 0.06, now + i * 0.5, 0.6) }
                else pcApi.busyUntil = 0.0
            } else if (pcApi.horn != null && rnd.nextDouble() < 0.015) pcApi.horn!!.triggerAttackRelease(listOf("A3", "C#4"), 0.35, now, 0.6)
            else if (pcApi.clash != null && rnd.nextDouble() < 0.05) pcApi.clash!!.triggerAttackRelease(pick(listOf("C5", "D5", "F5")), 0.1, now, rand(0.4, 0.8))
            else if (pcApi.shot != null && rnd.nextDouble() < 0.02) pcApi.shot!!.triggerAttackRelease("A0", 0.6, now, 0.8)
        }
        // PlayStation: game music only after the boot, menu pad while the lid is open, laser seek chatter while reading
        val ps1p = app.ps1.phase(t)
        if (tvApi != null && tvApi.ps1) {
            val musicOn = ps1p == "game" || ps1p == "title"
            tvApi.music!!.gain.rampTo(if (musicOn) 1.0 else 0.0, if (musicOn) 1.0 else 0.15)
            if (ps1p == "title" && lastPs1Phase != "title") sfxLocked("titleSting")
            tvApi.menu!!.gain.rampTo(if (ps1p == "menu") 1.0 else 0.0, 0.6)
        }
        lastPs1Phase = ps1p
        if ((ps1p == "boot" || ps1p == "game") && now > ps1SeekBusy && rnd.nextDouble() < (if (ps1p == "boot") 0.5 else 0.06)) {
            var tt = now; val n = 2 + rnd.nextInt(5)
            for (i in 0 until n) { ps1Seek.triggerAttackRelease(0.008, tt, rand(0.4, 1.0)); tt += rand(0.02, 0.06) }
            ps1SeekBusy = tt
        }
        // Bedroom TV events: talking shopping host, horror stingers
        if (app.tv.bedroomTvOn && tvApi != null) {
            val host = tvApi.host
            if (host != null && now > host.busyUntil + rand(0.3, 0.8)) speak(host, now, 5 + rnd.nextInt(8), 0.13)
            else if (tvApi.sting != null && rnd.nextDouble() < 0.01) tvApi.sting!!.triggerAttackRelease(listOf("C4", "C#4", "F#4"), 1.8, now, 0.6)
        }
        // Living room: sitcom dialogue between two muffled voices, then the laugh track
        if (livingRoomTvOn) {
            val a = lrVoices[0]; val b = lrVoices[1]
            if (now > max(a.busyUntil, b.busyUntil) + 0.25) {
                if (rnd.nextDouble() < 0.18) {
                    lrLaugh.gain.cancelScheduledValues(now); lrLaugh.gain.setValueAtTime(0.0, now)
                    lrLaugh.gain.linearRampToValueAtTime(0.5, now + 0.25); lrLaugh.gain.linearRampToValueAtTime(0.35, now + 1.0); lrLaugh.gain.linearRampToValueAtTime(0.0, now + 1.8)
                    a.busyUntil = now + 1.9
                } else speak(if (rnd.nextBoolean()) a else b, now, 3 + rnd.nextInt(8), 0.16)
            }
        }
        // Wildlife & city (quiet down in storms); rare voices are built on demand
        if (!isStorm && vol.getValue("nature") > 0.02) {
            val season = env.currentSeason
            val birdChance = if (isPrecip) 0.0 else (if (dawn) 0.25 else (if (day) (if (season == "spring") 0.15 else (if (isWinter) 0.02 else 0.08)) else (if (dusk) 0.04 else 0.0)))
            if (rnd.nextDouble() < birdChance) birdCall(now)
            if (night && !isPrecip && rnd.nextDouble() < 0.012) oneShot(3.0) { keep ->
                val owl = keep(Synth(ctx, "sine", Envelope(ctx, 0.06, 0.1, 0.8, 0.15))) as Synth
                owl.volume.value = -16.0
                owl.chain(keep(filt(900.0)) as Node, natureBus)
                for (c in arrayOf(doubleArrayOf(0.0, 390.0, 360.0, 0.38), doubleArrayOf(0.75, 400.0, 370.0, 0.22), doubleArrayOf(1.1, 395.0, 355.0, 0.42))) {
                    owl.triggerAttack(c[1], now + c[0], 0.7); owl.frequency.linearRampToValueAtTime(c[2], now + c[0] + c[3] - 0.03); owl.triggerRelease(now + c[0] + c[3])
                }
            }
            if (season == "spring" && (night || dusk) && rnd.nextDouble() < 0.45) {
                val f = rand(2500.0, 2900.0)
                peeper.triggerAttack(f, now, 0.6); peeper.frequency.exponentialRampToValueAtTime(f * 1.15, now + 0.07); peeper.triggerRelease(now + 0.08)
            }
            if (night && rnd.nextDouble() < 0.004 * max(app.places.outsideCityFactor(), if (app.sky.outsideEnv == "countryside") 0.5 else 0.0)) oneShot(5.0) { keep ->
                val horn = keep(PolySynth(ctx) { Synth(ctx, "sawtooth", Envelope(ctx, 0.25, 0.2, 0.85, 1.2)) }) as PolySynth<*>
                horn.volume.value = -26.0; horn.chain(keep(filt(480.0)) as Node, natureBus)
                horn.triggerAttackRelease(listOf("D3", "F#3", "A3"), 2.4, now, 0.5)
            }
            if (day && rnd.nextDouble() < 0.01 * app.places.outsideCityFactor()) oneShot(2.0) { keep ->
                val horn = keep(PolySynth(ctx) { Synth(ctx, "square", Envelope(ctx, 0.01, 0.05, 0.9, 0.1)) }) as PolySynth<*>
                horn.volume.value = -32.0; horn.chain(keep(filt(1200.0)) as Node, natureBus)
                horn.triggerAttackRelease(listOf("G3", "B3"), rand(0.2, 0.5), now, 0.5)
            }
        }
        // Fog horn every ~25 s in heavy fog
        if (env.currentWeather == "foggy" && now > nextFoghorn) {
            nextFoghorn = now + rand(22.0, 30.0)
            oneShot(7.0) { keep ->
                val horn = keep(PolySynth(ctx) { Synth(ctx, "sawtooth", Envelope(ctx, 0.5, 0.2, 0.9, 1.6)) }) as PolySynth<*>
                horn.volume.value = -16.0
                val lp = keep(filt(300.0)) as Filter; val far = keep(gain(0.8)) as Gain
                horn.chain(lp, weatherBus); lp.connect(far); far.connect(farIn)
                horn.triggerAttackRelease(listOf("A1", "E2"), 3.2, now, 0.8)
            }
        }
    }

    /** Speaks a phrase of syllables starting at t (falling intonation); returns its length. */
    private val VOWELS = arrayOf(doubleArrayOf(730.0, 1090.0), doubleArrayOf(270.0, 2290.0), doubleArrayOf(530.0, 1840.0), doubleArrayOf(570.0, 840.0), doubleArrayOf(440.0, 1020.0), doubleArrayOf(300.0, 870.0), doubleArrayOf(660.0, 1720.0))
    private fun speak(v: Voice, t: Double, syllables: Int, rate: Double = 0.15): Double {
        var tt = t
        for (i in 0 until syllables) {
            val (a, b) = VOWELS[rnd.nextInt(VOWELS.size)].let { it[0] to it[1] }
            val len = rate * rand(0.7, 1.3)
            val p = v.baseHz * rand(0.9, 1.18) * (1 - 0.14 * i / syllables)
            v.f1.frequency.setValueAtTime(a, tt); v.f2.frequency.setValueAtTime(b, tt)
            v.osc.frequency.setValueAtTime(p, tt); v.osc.frequency.linearRampToValueAtTime(p * 0.94, tt + len)
            v.env.gain.setValueAtTime(0.0, tt); v.env.gain.linearRampToValueAtTime(1.0, tt + 0.025)
            v.env.gain.linearRampToValueAtTime(0.7, tt + len * 0.6); v.env.gain.linearRampToValueAtTime(0.0, tt + len)
            tt += len + (if (rnd.nextDouble() < 0.15) rand(0.05, 0.15) else 0.01)
        }
        v.busyUntil = tt
        return tt - t
    }

    /** Builds a short-lived set of synths / nodes; everything is disposed after ttl seconds. */
    private fun oneShot(ttl: Double, build: ((Any) -> Any) -> Unit) {
        val parts = ArrayList<Any>()
        build { x -> parts += x; x }
        pendingDispose += (ctx.currentTime + ttl) to { for (p in parts) when (p) { is Node -> p.dispose(); is Instrument -> p.dispose() } }
    }

    // ---------------- Interaction SFX kit: synths are made only when a sound uses them ----------------
    private inner class Kit(val out: Node, private val keep: (Any) -> Unit) {
        val tickFilter by lazy { filt(2500.0, "bandpass", 1.2).also { keep(it) } }
        val tick by lazy { NoiseSynth(ctx, "white", Envelope(ctx, 0.001, 0.03, 0.0, 0.01)).also { keep(it); it.chain(tickFilter, out) } }
        val noiseFilter by lazy { filt(800.0, "bandpass", 1.0).also { keep(it) } }
        val noise by lazy { NoiseSynth(ctx, "pink", Envelope(ctx, 0.02, 0.3, 0.0, 0.1)).also { keep(it); it.chain(noiseFilter, out) } }
        val hiss by lazy { NoiseSynth(ctx, "white", Envelope(ctx, 0.005, 0.35, 0.0, 0.05)).also { keep(it); it.volume.value = -10.0; val f = filt(3500.0, "highpass"); keep(f); it.chain(f, out) } }
        val thump by lazy { MembraneSynth(ctx, 0.03, 4.0, Envelope(ctx, 0.001, 0.2, 0.0, 0.05)).also { keep(it); it.volume.value = -6.0; it.connect(out) } }
        val metal by lazy { MetalSynth(ctx, 5.1, 16.0, 3000.0, 1.2, Envelope(ctx, 0.001, 0.06, 0.0, 0.02)).also { keep(it); it.volume.value = -20.0; it.connect(out) } }
        val tone by lazy { Synth(ctx, "triangle", Envelope(ctx, 0.005, 0.1, 0.6, 0.08)).also { keep(it); it.volume.value = -8.0; it.connect(out) } }
        val tone2 by lazy { Synth(ctx, "sine", Envelope(ctx, 0.005, 0.1, 0.6, 0.08)).also { keep(it); it.volume.value = -8.0; it.connect(out) } }
        val pad by lazy { PolySynth(ctx) { Synth(ctx, "triangle", Envelope(ctx, 0.6, 0.4, 0.7, 2.0)) }.also { keep(it); it.volume.value = -14.0; it.connect(out) } }
        val voice by lazy { Voice(ctx, out, 500.0, 1.2).also { v -> v.parts.forEach { keep(it) } } }
        var disposeNow: () -> Unit = {}
    }
    /** A placed, self-disposing SFX kit. */
    private fun sfxAt(p: Vec3, ttl: Double = 3.0): Kit {
        val parts = ArrayList<Any>()
        val spot = Spot(Vec3().copy(p), 1.4, 1.0, 0.2, master, vol.getValue("sfx"))
        val kit = Kit(spot.level) { parts += it }
        var disposed = false
        kit.disposeNow = { if (!disposed) { disposed = true; for (x in parts) when (x) { is Node -> x.dispose(); is Instrument -> x.dispose() }; spot.remove() } }
        pendingDispose += (ctx.currentTime + ttl) to kit.disposeNow
        return kit
    }
    private var ps1BootKit: Kit? = null
    private var ps1SpinKit: Kit? = null
    fun stopPs1BootSound() = ctx.sync { ps1BootKit?.disposeNow?.invoke(); ps1SpinKit?.disposeNow?.invoke(); ps1BootKit = null; ps1SpinKit = null }
    private val DTMF = mapOf('0' to (941.0 to 1336.0), '1' to (697.0 to 1209.0), '5' to (770.0 to 1336.0), '9' to (852.0 to 1477.0))

    /** playSfx: placed at the object (or just below the listener for body sounds). */
    fun sfx(name: String) {
        if (!ready || !playing) return
        try { ctx.sync { sfxLocked(name) } } catch (e: Exception) { /* overlapping: skipped */ }
    }
    private fun sfxLocked(name: String) {
        val t = ctx.currentTime + 0.02
        val P = pos
        val near = { Vec3().copy(app.camera.position).also { it.y -= 2.6f } }
        val k: Kit
        when (name) {
            "ui" -> { k = sfxAt(near(), 1.0); k.tone.oscillator.type = "sine"; k.tone.triggerAttackRelease(1320.0, 0.035, t, 0.12); k.tone.triggerAttackRelease(1760.0, 0.045, t + 0.05, 0.1) }
            "blinds" -> {
                k = sfxAt(P.getValue("blinds")); k.tickFilter.frequency.value = 3200.0; k.tickFilter.Q.value = 2.0
                var tt = t; for (i in 0 until 14) { k.tick.triggerAttackRelease(0.015, tt, rand(0.3, 0.9)); tt += rand(0.025, 0.045) }
                k.noiseFilter.frequency.value = 2400.0; k.noise.triggerAttackRelease(0.3, t, 0.25)
            }
            "lightChain", "fanChain" -> {
                k = sfxAt(Vec3().copy(P.getValue("fan")).also { it.y -= 0.3f })
                k.metal.triggerAttackRelease(if (name == "fanChain") "C6" else "E6", 0.03, t, 0.3)
                k.tickFilter.frequency.value = 5200.0; k.tickFilter.Q.value = 3.0
                k.tick.triggerAttackRelease(0.015, t + 0.01, 0.7); k.tick.triggerAttackRelease(0.015, t + 0.09, 0.5)
                k.thump.triggerAttackRelease(if (name == "fanChain") "E3" else "A3", 0.03, t, 0.25)
            }
            "tvOn" -> {
                k = sfxAt(P.getValue("tv")); k.thump.triggerAttackRelease("A1", 0.15, t, 0.35)
                k.noiseFilter.frequency.value = 3000.0; k.noise.triggerAttackRelease(0.4, t + 0.05, 0.3)
                k.tickFilter.frequency.value = 4000.0
                var tt = t + 0.05; for (i in 0 until 10) { k.tick.triggerAttackRelease(0.01, tt, rand(0.2, 0.6)); tt += rand(0.03, 0.06) }
            }
            "tvOff" -> {
                k = sfxAt(P.getValue("tv")); k.tone.oscillator.type = "sine"
                k.tone.triggerAttack(1400.0, t, 0.25); k.tone.frequency.exponentialRampToValueAtTime(90.0, t + 0.25); k.tone.triggerRelease(t + 0.26)
                k.tickFilter.frequency.value = 4000.0; for (i in 0 until 4) k.tick.triggerAttackRelease(0.01, t + i * 0.05, 0.4)
            }
            "tvChannel" -> {
                k = sfxAt(P.getValue("tv")); k.tickFilter.frequency.value = 1800.0; k.tick.triggerAttackRelease(0.01, t, 0.9)
                k.noiseFilter.frequency.value = 2500.0; k.noiseFilter.Q.value = 0.5
                k.noise.envelope.attack = 0.005; k.noise.envelope.decay = 0.18; k.noise.triggerAttackRelease(0.15, t + 0.01, 0.5)
            }
            "monitorChannel" -> { k = sfxAt(P.getValue("monitor")); k.tickFilter.frequency.value = 1500.0; k.tick.triggerAttackRelease(0.01, t, 0.8); k.thump.triggerAttackRelease("E2", 0.05, t + 0.03, 0.25) }
            "pcOn" -> {
                k = sfxAt(P.getValue("pc"), 3.0); k.tickFilter.frequency.value = 1400.0; k.tick.triggerAttackRelease(0.01, t, 0.9)
                soundPcPower(true)
                // CRT degauss "thwomp" + POST beep
                k.thump.triggerAttackRelease("G1", 0.4, t + 0.3, 0.7)
                k.tone.oscillator.type = "sawtooth"; k.tone.triggerAttack(60.0, t + 0.3, 0.25); k.tone.triggerRelease(t + 0.9)
                k.tone2.triggerAttackRelease(1000.0, 0.14, t + 1.5, 0.25)
            }
            "pcOff" -> { k = sfxAt(P.getValue("pc")); k.tickFilter.frequency.value = 1400.0; k.tick.triggerAttackRelease(0.01, t, 0.9); k.thump.triggerAttackRelease("C2", 0.08, t + 0.02, 0.3); soundPcPower(false) }
            "acOn", "acOff" -> { k = sfxAt(P.getValue("ac")); k.thump.triggerAttackRelease("D1", 0.2, t, 0.7); k.metal.triggerAttackRelease("A3", 0.04, t + 0.02, 0.2); k.tickFilter.frequency.value = 1600.0; k.tick.triggerAttackRelease(0.01, t, 0.6) }
            "door" -> {
                k = sfxAt(P.getValue("door")); k.metal.envelope.decay = 0.05
                k.metal.triggerAttackRelease("G4", 0.03, t, 0.25); k.metal.triggerAttackRelease("G4", 0.03, t + 0.09, 0.2)
                k.tickFilter.frequency.value = 1800.0; k.tick.triggerAttackRelease(0.01, t + 0.18, 0.9); k.thump.triggerAttackRelease("B1", 0.06, t + 0.18, 0.3)
            }
            "chairSit" -> {
                k = sfxAt(near()); k.noiseFilter.frequency.value = 500.0; k.noise.envelope.decay = 0.4; k.noise.triggerAttackRelease(0.4, t, 0.4)
                k.hiss.triggerAttackRelease(0.3, t + 0.15, 0.5); k.thump.triggerAttackRelease("E1", 0.1, t + 0.05, 0.4)
            }
            "chairRoll" -> {
                k = sfxAt(near()); k.noiseFilter.frequency.value = 180.0; k.noise.noise.type = "brown"
                k.noise.envelope.attack = 0.08; k.noise.envelope.decay = 0.6; k.noise.triggerAttackRelease(0.6, t, 0.8)
                k.tickFilter.frequency.value = 700.0
                var tt = t + 0.05; for (i in 0 until 5) { k.tick.triggerAttackRelease(0.01, tt, 0.35); tt += rand(0.08, 0.13) }
            }
            "bedLay", "bedRise" -> {
                k = sfxAt(near()); k.metal.envelope.decay = 0.35; k.metal.triggerAttackRelease("A2", 0.2, t + 0.05, 0.12)
                k.thump.triggerAttackRelease("E1", 0.15, t, if (name == "bedLay") 0.3 else 0.2)
                k.noiseFilter.frequency.value = 400.0; k.noise.envelope.decay = 0.5; k.noise.triggerAttackRelease(0.5, t, 0.45)
            }
            "step", "stepRug" -> {
                k = sfxAt(near(), 1.0)
                if (name == "stepRug") { k.thump.triggerAttackRelease("A1", 0.06, t, 0.35); k.noiseFilter.frequency.value = 900.0; k.noise.envelope.decay = 0.08; k.noise.triggerAttackRelease(0.08, t, 0.3) }
                else { k.thump.triggerAttackRelease("D2", 0.05, t, 0.45); k.tickFilter.frequency.value = 1200.0; k.tick.triggerAttackRelease(0.01, t, 0.4) }
            }
            "lavaSwitch" -> {
                k = sfxAt(P.getValue("lava"), 1.0); k.tick.triggerAttackRelease(0.01, t, 0.8)
                lavaBloop.triggerAttack(240.0, t + 0.1, 0.8); lavaBloop.frequency.exponentialRampToValueAtTime(140.0, t + 0.4); lavaBloop.triggerRelease(t + 0.42)
                lavaBusyUntil = t + 0.6
            }
            "phone" -> {
                // Handset pickup, dial tone, DTMF digits, then a short 56k modem handshake
                k = sfxAt(P.getValue("phone"), 7.0); k.thump.triggerAttackRelease("C3", 0.08, t, 0.4)
                k.tone.oscillator.type = "sine"
                k.tone.triggerAttackRelease(350.0, 0.6, t + 0.15, 0.15); k.tone2.triggerAttackRelease(440.0, 0.6, t + 0.15, 0.15)
                var tt = t + 0.85
                for (d in "5550199") { val (lo, hi) = DTMF.getValue(d); k.tone.triggerAttackRelease(lo, 0.08, tt, 0.15); k.tone2.triggerAttackRelease(hi, 0.08, tt, 0.15); tt += 0.14 }
                tt += 0.3
                k.tone2.triggerAttackRelease(2100.0, 0.5, tt, 0.08)
                tt += 0.6
                for (i in 0 until 24) k.tone2.triggerAttackRelease(if (i % 2 == 1) 1200.0 else 2400.0, 0.03, tt + i * 0.035, 0.07)
                k.noiseFilter.frequency.value = 1800.0; k.noiseFilter.Q.value = 0.6; k.noise.envelope.attack = 0.05; k.noise.envelope.decay = 1.0
                k.noise.triggerAttackRelease(1.0, tt + 0.85, 0.3)
            }
            "ps1Boot" -> {
                // From the TV speaker, synced to the two boot screens
                k = sfxAt(P.getValue("tv"), 10.0); ps1BootKit = k
                k.noiseFilter.type = "lowpass"
                k.noiseFilter.frequency.setValueAtTime(200.0, t + 0.5); k.noiseFilter.frequency.exponentialRampToValueAtTime(2500.0, t + 2.0)
                k.noise.envelope.attack = 0.9; k.noise.envelope.decay = 1.6; k.noise.triggerAttackRelease(1.5, t + 0.5, 0.35)
                k.pad.triggerAttackRelease(listOf("C#3", "G#3", "C#4", "F4"), 3.0, t + 0.6, 0.5)
                k.tone.oscillator.type = "sine"
                k.tone.triggerAttackRelease("G#6", 0.3, t + 1.6, 0.1); k.tone.triggerAttackRelease("C#7", 0.4, t + 1.85, 0.1)
                k.thump.triggerAttackRelease("C#2", 0.6, t + 4.3, 0.45)
                k.pad.triggerAttackRelease(listOf("F#3", "C#4", "F4", "A#4"), 2.6, t + 4.3, 0.4)
                listOf("C#6", "F6", "G#6", "C#7").forEachIndexed { i, n -> k.tone2.triggerAttackRelease(n, 0.3, t + 4.4 + i * 0.12, 0.14) }
            }
            "ps1Power" -> { k = sfxAt(P.getValue("ps1"), 1.0); k.tickFilter.frequency.value = 1600.0; k.tick.triggerAttackRelease(0.01, t, 0.8); k.thump.triggerAttackRelease("C3", 0.06, t + 0.01, 0.3) }
            "ps1Reset", "ps1Button" -> { k = sfxAt(P.getValue("ps1"), 1.0); k.tickFilter.frequency.value = 2000.0; k.tick.triggerAttackRelease(0.01, t, 0.6) }
            "lightSwitch" -> {
                val d = P.getValue("door"); k = sfxAt(Vec3(d.x - 1.08f, d.y + 0.2f, d.z), 1.0)
                k.tickFilter.frequency.value = 3200.0; k.tickFilter.Q.value = 2.0; k.tick.triggerAttackRelease(0.008, t, 0.9); k.thump.triggerAttackRelease("C4", 0.02, t, 0.15)
            }
            "casePickup" -> {
                k = sfxAt(Vec3().copy(P.getValue("monitor")).also { it.y += 1f }, 1.0)
                k.tickFilter.frequency.value = 2600.0; k.tickFilter.Q.value = 2.0; k.tick.triggerAttackRelease(0.01, t, 0.8)
                k.noiseFilter.type = "lowpass"; k.noiseFilter.frequency.value = 900.0; k.noise.envelope.attack = 0.05; k.noise.envelope.decay = 0.25; k.noise.triggerAttackRelease(0.25, t + 0.02, 0.25)
            }
            "casePutBack" -> { k = sfxAt(Vec3().copy(P.getValue("monitor")).also { it.y += 1f }, 1.0); k.tickFilter.frequency.value = 2200.0; k.tick.triggerAttackRelease(0.01, t + 0.35, 0.8); k.thump.triggerAttackRelease("A3", 0.03, t + 0.35, 0.15) }
            "caseOpen" -> { k = sfxAt(P.getValue("ps1"), 1.0); k.tickFilter.frequency.value = 3200.0; k.tickFilter.Q.value = 2.5; k.tick.triggerAttackRelease(0.008, t + 0.6, 0.9); k.tick.triggerAttackRelease(0.008, t + 0.66, 0.6); k.metal.triggerAttackRelease("C6", 0.02, t + 0.6, 0.12) }
            "discOut" -> { k = sfxAt(P.getValue("ps1"), 1.0); k.hiss.envelope.decay = 0.12; k.hiss.triggerAttackRelease(0.1, t, 0.35); k.tickFilter.frequency.value = 4200.0; k.tick.triggerAttackRelease(0.006, t, 0.5) }
            "discIn" -> { k = sfxAt(P.getValue("ps1"), 1.0); k.thump.triggerAttackRelease("E4", 0.03, t, 0.3); k.tickFilter.frequency.value = 2000.0; k.tick.triggerAttackRelease(0.008, t, 0.9) }
            "titleSting" -> {
                // Each game's own title jingle, from the TV speaker
                val sting = Stings.BY_TITLE[app.library.currentGame().title] ?: return
                k = sfxAt(P.getValue("tv"), 4.0); k.tone.oscillator.type = sting.wave
                sting.notes.forEachIndexed { i, n -> k.tone.triggerAttackRelease(n, sting.step * 0.9, t + i * sting.step, 0.22) }
                sting.thump?.let { k.thump.triggerAttackRelease(it, 0.4, t, 0.6) }
                k.pad.triggerAttackRelease(sting.notes.takeLast(3), 1.4, t + sting.notes.size * sting.step, 0.3)
            }
            "radioTune" -> {
                k = sfxAt(P.getValue("boombox"), 1.0)
                k.noiseFilter.frequency.setValueAtTime(600.0, t); k.noiseFilter.frequency.exponentialRampToValueAtTime(3000.0, t + 0.35)
                k.noise.noise.type = "white"; k.noise.envelope.attack = 0.02; k.noise.envelope.decay = 0.4; k.noise.triggerAttackRelease(0.4, t, 0.4)
                k.tone2.triggerAttack(900.0, t, 0.05); k.tone2.frequency.exponentialRampToValueAtTime(1500.0, t + 0.3); k.tone2.triggerRelease(t + 0.32)
            }
            "caseClick" -> {
                k = sfxAt(Vec3().copy(P.getValue("monitor")).also { it.y += 1f }, 1.0)
                k.tickFilter.frequency.value = 2600.0; k.tickFilter.Q.value = 2.0
                k.tick.triggerAttackRelease(0.01, t, 0.8); k.tick.triggerAttackRelease(0.01, t + 0.05, 0.4); k.thump.triggerAttackRelease("A4", 0.03, t, 0.12)
            }
            "ps1LidOpen" -> {
                k = sfxAt(P.getValue("ps1"), 2.0); k.tickFilter.frequency.value = 2400.0; k.tick.triggerAttackRelease(0.01, t, 0.8)
                k.metal.envelope.decay = 0.12; k.metal.triggerAttackRelease("B4", 0.05, t + 0.01, 0.15)
                k.noiseFilter.frequency.value = 1200.0; k.noise.envelope.decay = 0.15; k.noise.triggerAttackRelease(0.12, t + 0.02, 0.2)
                k.thump.triggerAttackRelease("G3", 0.04, t + 0.13, 0.15)
            }
            "ps1LidClose" -> { k = sfxAt(P.getValue("ps1"), 1.0); k.thump.triggerAttackRelease("D3", 0.05, t, 0.35); k.tickFilter.frequency.value = 1800.0; k.tick.triggerAttackRelease(0.01, t + 0.005, 0.7) }
            "ps1Spin" -> {
                // Disc motor spinning up, then the laser sled hunting for the track
                k = sfxAt(P.getValue("ps1"), 4.0); ps1SpinKit = k
                k.tone2.triggerAttack(160.0, t, 0.06); k.tone2.frequency.exponentialRampToValueAtTime(820.0, t + 1.4); k.tone2.triggerRelease(t + 1.5)
                k.noiseFilter.frequency.value = 1200.0; k.noise.envelope.attack = 0.5; k.noise.envelope.decay = 1.2; k.noise.triggerAttackRelease(1.2, t, 0.2)
                k.tickFilter.frequency.value = 3200.0
                var tt = t + 1.5; for (i in 0 until 8) { k.tick.triggerAttackRelease(0.008, tt, rand(0.3, 0.7)); tt += rand(0.04, 0.09) }
            }
            "ps1Pad" -> {
                k = sfxAt(P.getValue("ps1"), 1.0); k.tickFilter.frequency.value = 3000.0; k.tick.triggerAttackRelease(0.008, t, 0.5)
                if (app.ps1.phase(app.lastFrameT) == "game" && app.tv.bedroomTvOn && app.tv.bedroomTvChannel == Tv.PS1_TV_CHANNEL) {
                    val tvk = sfxAt(P.getValue("tv"), 1.0) // the hero's jump "boing" from the TV
                    tvk.tone.oscillator.type = "square"
                    tvk.tone.triggerAttack(400.0, t + 0.02, 0.08); tvk.tone.frequency.exponentialRampToValueAtTime(820.0, t + 0.14); tvk.tone.triggerRelease(t + 0.15)
                }
            }
            "meow" -> {
                k = sfxAt(P.getValue("cat"), 2.0); val v = k.voice
                v.osc.frequency.setValueAtTime(520.0, t); v.osc.frequency.linearRampToValueAtTime(680.0, t + 0.2); v.osc.frequency.linearRampToValueAtTime(420.0, t + 0.6)
                v.f1.frequency.setValueAtTime(300.0, t); v.f2.frequency.setValueAtTime(900.0, t)
                v.f1.frequency.linearRampToValueAtTime(800.0, t + 0.18); v.f2.frequency.linearRampToValueAtTime(1700.0, t + 0.18)
                v.f1.frequency.linearRampToValueAtTime(500.0, t + 0.6); v.f2.frequency.linearRampToValueAtTime(900.0, t + 0.6)
                v.env.gain.setValueAtTime(0.0, t); v.env.gain.linearRampToValueAtTime(1.0, t + 0.08); v.env.gain.linearRampToValueAtTime(0.8, t + 0.45); v.env.gain.linearRampToValueAtTime(0.0, t + 0.62)
            }
            "purr" -> {
                k = sfxAt(P.getValue("cat"), 3.0); k.tone.oscillator.type = "sawtooth"
                k.tone.triggerAttack(27.0, t, 0.7); k.tone.triggerRelease(t + 0.9); k.tone.triggerAttack(24.0, t + 1.1, 0.6); k.tone.triggerRelease(t + 2.0)
            }
            "recStart" -> { k = sfxAt(near(), 1.0); k.tone2.triggerAttackRelease(880.0, 0.1, t, 0.15) }
            "recStop" -> { k = sfxAt(near(), 1.0); k.tone2.triggerAttackRelease(880.0, 0.08, t, 0.15); k.tone2.triggerAttackRelease(660.0, 0.1, t + 0.12, 0.15) }
        }
    }

    // ---------------- Sounds of each place (HTML 15611-15715) ----------------
    private lateinit var waves: Gain; private lateinit var wavesSurf: Gain
    private lateinit var openWind: Gain; private lateinit var openWindFilter: Filter
    private lateinit var cicadas: Gain; private lateinit var acHum: Gain
    private fun buildOutsideEnvSound() {
        waves = gain(0.0); waves.connect(natureBus)
        wavesSurf = gain(0.5)
        Noise(ctx, "pink").start().chain(filt(900.0, "lowpass", 0.4), wavesSurf, waves)
        openWind = gain(0.0); openWind.connect(natureBus)
        openWindFilter = filt(520.0, "bandpass", 0.8)
        Noise(ctx, "pink").start().chain(openWindFilter, gain(0.6), openWind)
        // Summer cicadas (Arizona), a fizzing high buzz that swells and fades
        cicadas = gain(0.0); cicadas.connect(natureBus)
        Noise(ctx, "white").start().chain(filt(5200.0, "bandpass", 6.0), gain(0.25), cicadas)
        // Window AC units humming around the trailer park
        acHum = gain(0.0); acHum.connect(natureBus)
        Noise(ctx, "brown").start().chain(filt(180.0, "lowpass"), gain(0.35), acHum)
    }
    private fun updateOutsideEnvSound() {
        val e = app.places.envDef()
        val on = if (playing) 1.0 else 0.0
        val day = env.daylight > 0.5
        val W = env.W
        waves.gain.rampTo(e.waves * on * (0.6 + W.wind * 0.6), 1.5)
        openWind.gain.rampTo(e.wind * on * (0.4 + W.wind * 0.9), 1.5)
        cicadas.gain.rampTo(if (app.sky.outsideEnv == "arizona" && env.currentSeason == "summer" && day && !env.isPrecipNow()) on * 0.5 else 0.0, 2.0)
        acHum.gain.rampTo(if (app.sky.outsideEnv == "trailer" && (env.currentSeason == "summer" || env.currentSeason == "spring")) on * 0.4 else 0.0, 2.0)
    }
    // One-off calls: synthesized, routed through the window like every other outdoor sound
    private fun critter(kind: String, now: Double) {
        fun vol(db: Double) = db - 4
        oneShot(6.0) { keep ->
            val out = keep(gain(0.9)) as Gain; out.connect(natureBus)
            fun glide(type: String, f0: Double, f1: Double, dur: Double, db: Double, at: Double = 0.0, cut: Double = 3000.0) {
                val s = keep(Synth(ctx, type, Envelope(ctx, 0.04, 0.1, 0.9, 0.2))) as Synth
                s.volume.value = vol(db); s.chain(keep(filt(cut)) as Node, out)
                s.triggerAttack(f0, now + at); s.frequency.linearRampToValueAtTime(f1, now + at + dur); s.triggerRelease(now + at + dur)
            }
            fun bell(notes: List<String>, step: Double, db: Double) {
                val s = keep(PolySynth(ctx) { Synth(ctx, "sine", Envelope(ctx, 0.002, 1.4, 0.0, 1.2)) }) as PolySynth<*>
                s.volume.value = vol(db); s.connect(out)
                notes.forEachIndexed { i, n -> s.triggerAttackRelease(n, 0.8, now + i * step) }
            }
            fun thumps(count: Int, gap: Double, f: String, db: Double) {
                val s = keep(MembraneSynth(ctx, 0.02, 2.0, Envelope(ctx, 0.001, 0.08, 0.0, 0.05))) as MembraneSynth
                s.volume.value = vol(db); s.connect(out)
                for (i in 0 until count) s.triggerAttackRelease(f, 0.05, now + i * gap)
            }
            when (kind) {
                "dove" -> { glide("sine", 520.0, 610.0, 0.25, -18.0, 0.0, 900.0); glide("sine", 600.0, 470.0, 0.5, -18.0, 0.3, 900.0); glide("sine", 470.0, 470.0, 0.3, -20.0, 0.95, 900.0); glide("sine", 470.0, 440.0, 0.3, -20.0, 1.4, 900.0) }
                "quail" -> { glide("triangle", 1700.0, 2300.0, 0.08, -22.0, 0.0); glide("triangle", 2500.0, 2100.0, 0.12, -20.0, 0.14); glide("triangle", 1900.0, 1700.0, 0.1, -22.0, 0.32) }
                "coyote" -> { glide("sawtooth", 600.0, 1100.0, 0.9, -26.0, 0.0, 1400.0); glide("sawtooth", 1100.0, 750.0, 0.7, -26.0, 0.9, 1400.0); for (i in 0 until 4) glide("sawtooth", 1200.0, 900.0, 0.07, -28.0, 1.8 + i * 0.12, 1600.0) }
                "hawk" -> glide("sawtooth", 2600.0, 1800.0, 1.4, -28.0, 0.0, 3200.0)
                "eagle" -> for (i in 0 until 5) glide("triangle", 2400.0, 2900.0, 0.06, -26.0, i * 0.09, 3500.0)
                "gull" -> for (i in 0 until 3) glide("sawtooth", 1500.0, 900.0, 0.22, -28.0, i * 0.3, 2200.0)
                "tropical" -> for (i in 0 until 6) glide("sine", 2200 + rnd.nextDouble() * 1600, 3200 + rnd.nextDouble() * 900, 0.05, -24.0, i * 0.08, 5000.0)
                "frog" -> { glide("sine", 1000.0, 1000.0, 0.08, -22.0, 0.0, 3000.0); glide("sine", 1600.0, 2100.0, 0.12, -22.0, 0.12, 3000.0) }
                "loon" -> { glide("sine", 700.0, 1050.0, 0.9, -22.0, 0.0, 1500.0); glide("sine", 1050.0, 900.0, 1.6, -22.0, 0.9, 1500.0) }
                "woodpecker" -> thumps(14, 0.045, "C5", -22.0)
                "rooster" -> { glide("sawtooth", 600.0, 900.0, 0.2, -26.0, 0.0, 1800.0); glide("sawtooth", 900.0, 1000.0, 0.6, -26.0, 0.2, 1800.0); glide("sawtooth", 1000.0, 700.0, 0.3, -26.0, 0.8, 1800.0) }
                "cow" -> { glide("sawtooth", 160.0, 200.0, 0.5, -24.0, 0.0, 700.0); glide("sawtooth", 200.0, 140.0, 0.8, -24.0, 0.5, 700.0) }
                "dog" -> for (i in 0 until 2 + rnd.nextInt(3)) glide("sawtooth", 520.0, 380.0, 0.12, -24.0, i * 0.3, 1200.0)
                "screendoor" -> { thumps(1, 0.0, "G2", -18.0); thumps(1, 0.0, "D3", -26.0) }
                "icecream" -> bell(listOf("E5", "G5", "A5", "G5", "E5", "C5", "D5", "E5", "C5"), 0.22, -24.0)
                "chimes" -> bell(listOf("C6", "E6", "G6", "A6", "D6", "C7").shuffled().take(2 + (env.W.wind * 4).toInt()), 0.35, -22.0)
                "mower" -> {
                    val s = keep(Oscillator(ctx, 62.0, "sawtooth")) as Oscillator; val g2 = keep(gain(0.0)) as Gain
                    s.chain(keep(filt(500.0)) as Node, g2, out); s.start(now)
                    g2.gain.setValueAtTime(0.0, now); g2.gain.linearRampToValueAtTime(0.06, now + 1.5); g2.gain.linearRampToValueAtTime(0.0, now + 5.5); s.stop(now + 5.6)
                }
                "bass" -> thumps(8, 0.42, "A1", -20.0)
                "siren" -> { glide("sine", 700.0, 1300.0, 1.2, -30.0, 0.0, 1600.0); glide("sine", 1300.0, 700.0, 1.2, -30.0, 1.2, 1600.0); glide("sine", 700.0, 1300.0, 1.2, -30.0, 2.4, 1600.0) }
                "crossing" -> { bell(List(6) { "A5" }, 0.5, -28.0); thumps(10, 0.3, "C2", -26.0) }
                "heli" -> thumps(30, 0.09, "E1", -26.0)
            }
        }
    }
    // How often each place's calls happen (chance per second), by time of day
    private var critterBusy = 0.0
    private var lastCritterTick = 0L
    private fun crittersTick() {
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastCritterTick < 500) return
        lastCritterTick = nowMs
        val now = ctx.currentTime
        val tt = nowMs / 1000.0
        wavesSurf.gain.setTargetAtTime(0.25 + 0.55 * max(0.0, sin(tt * 0.9)).pow(2), now, 0.4)
        openWindFilter.frequency.setTargetAtTime(420 + 260 * (0.5 + 0.5 * sin(tt * 0.37)), now, 0.6)
        updateOutsideEnvSound()
        if (now < critterBusy || env.W.base == "storm") return
        val d = env.daylight > 0.45; val n = env.daylight < 0.25; val s = env.currentSeason
        val hour = env.hour
        val table: List<Triple<Boolean, String, Double>> = when (app.sky.outsideEnv) {
            "arizona" -> listOf(Triple(d && s != "winter", "dove", 0.03), Triple(d, "quail", 0.012), Triple(n, "coyote", 0.006), Triple(n, "dog", 0.004))
            "desert" -> listOf(Triple(d, "hawk", 0.01), Triple(n, "coyote", 0.008))
            "trailer" -> listOf(Triple(true, "dog", 0.012), Triple(d, "screendoor", 0.008), Triple(n, "screendoor", 0.003))
            "neighborhood" -> listOf(Triple(d && (s == "summer" || s == "spring") && hour > 12 && hour < 19, "icecream", 0.003), Triple(true, "dog", 0.006), Triple(n, "bass", 0.004), Triple(true, "siren", 0.002))
            "house" -> listOf(Triple(env.W.wind > 0.2f, "chimes", 0.05 + env.W.wind * 0.1), Triple(d && s == "summer" && hour > 9 && hour < 18, "mower", 0.004), Triple(n, "dog", 0.003))
            "skyscraper" -> listOf(Triple(true, "siren", 0.006), Triple(true, "heli", 0.002))
            "neon" -> listOf(Triple(true, "crossing", 0.006), Triple(true, "siren", 0.003))
            "island" -> listOf(Triple(d, "tropical", 0.04), Triple(n, "frog", 0.08))
            "beach" -> listOf(Triple(d, "gull", 0.03))
            "lake" -> listOf(Triple(n, "loon", 0.01))
            "forest" -> listOf(Triple(d, "woodpecker", 0.012))
            "mountains" -> listOf(Triple(d, "eagle", 0.008))
            "countryside" -> listOf(Triple(hour > 5 && hour < 8, "rooster", 0.03), Triple(d, "cow", 0.012), Triple(true, "dog", 0.002))
            else -> return
        }
        for ((cond, kind, rate) in table) {
            if (!cond) continue
            if (rnd.nextDouble() < rate * 0.5 * (if (env.isPrecipNow() && kind != "chimes") 0.3 else 1.0)) { critter(kind, now + 0.05); critterBusy = now + 4; break }
        }
    }

    // ---- The cordless phone ringing: 4 cycles of 2 s fast warble, 4 s quiet ----
    private var ringNodes: List<Any>? = null
    fun ringPhone(): Boolean = ctx.sync {
        try {
            val spot = Spot(Vec3().copy(pos.getValue("phone")), 1.4, 1.0, 0.2, master, vol.getValue("sfx"))
            val g = gain(0.0); g.connect(spot.level)
            val a = Oscillator(ctx, 440.0, "sine"); val b = Oscillator(ctx, 480.0, "sine")
            a.volume.value = -10.0; b.volume.value = -10.0; a.connect(g); b.connect(g)
            val t0 = ctx.currentTime + 0.05
            a.start(t0); b.start(t0)
            for (c in 0 until 4) for (k in 0 until 40) g.gain.setValueAtTime(if (k % 2 == 1) 0.0 else 0.5, t0 + c * 6 + k * 0.05)
            g.gain.setValueAtTime(0.0, t0 + 24)
            a.stop(t0 + 24.2); b.stop(t0 + 24.2)
            ringNodes = listOf(a, b, g, spot)
            true
        } catch (e: Exception) { false }
    }
    fun stopRing() = ctx.sync {
        val n = ringNodes ?: return@sync
        ringNodes = null
        val g = n[2] as Gain
        g.gain.cancelScheduledValues(ctx.currentTime); g.gain.setValueAtTime(0.0, ctx.currentTime)
        pendingDispose += (ctx.currentTime + 0.3) to { (n[0] as Node).dispose(); (n[1] as Node).dispose(); g.dispose(); @Suppress("UNCHECKED_CAST") (n[3] as Spot).remove() }
    }

    // ---- Jasper: a soft "boof" when petted, slow snores while asleep ----
    fun dogSound(kind: String, at: Vec3) {
        if (!ready || !playing) return
        try { ctx.sync {
            val spot = Spot(Vec3().copy(at), 1.2, 1.0, 0.15, master, vol.getValue("sfx"))
            val t0 = ctx.currentTime + 0.02
            val parts = ArrayList<Any>()
            if (kind == "boof") {
                val thump = MembraneSynth(ctx, 0.05, 2.0, Envelope(ctx, 0.005, 0.18, 0.0, 0.05)); thump.volume.value = -10.0; thump.connect(spot.level); thump.triggerAttackRelease("A2", 0.15, t0, 0.8)
                val breath = Noise(ctx, "brown"); val bf = filt(650.0, "bandpass", 1.2); val bg = gain(0.0)
                breath.connect(bf); bf.connect(bg); bg.connect(spot.level)
                bg.gain.setValueAtTime(0.0, t0); bg.gain.linearRampToValueAtTime(0.5, t0 + 0.03); bg.gain.linearRampToValueAtTime(0.0, t0 + 0.22)
                breath.start(t0); breath.stop(t0 + 0.3)
                parts.addAll(listOf(thump, breath, bf, bg))
            } else {
                val n = Noise(ctx, "brown"); val f = filt(260.0, "lowpass", 0.8); val g = gain(0.0)
                n.connect(f); f.connect(g); g.connect(spot.level)
                g.gain.setValueAtTime(0.0, t0); g.gain.linearRampToValueAtTime(0.35, t0 + 1.1); g.gain.linearRampToValueAtTime(0.0, t0 + 2.2)
                n.start(t0); n.stop(t0 + 2.3)
                parts.addAll(listOf(n, f, g))
            }
            pendingDispose += (t0 + 3.2) to { for (p in parts) when (p) { is Node -> p.dispose(); is Instrument -> p.dispose() }; spot.remove() }
        } } catch (e: Exception) { }
    }
    // ---- The space heater's fan hum ----
    private var heaterHumGain: Gain? = null
    fun heaterHum(on: Boolean) {
        if (!ready) return
        ctx.sync {
            val g = heaterHumGain ?: run {
                val spot = Spot(Vec3(1.2f, 0.3f, 1.38f), 1.2, 1.0, 0.08, master, vol.getValue("sfx"))
                val g = gain(0.0); g.connect(spot.level)
                Noise(ctx, "brown").start().chain(filt(420.0, "lowpass", 0.6), gain(0.55), g)
                heaterHumGain = g; g
            }
            g.gain.rampTo(if (on && playing) 0.35 else 0.0, 1.5)
        }
    }

    // ---------------- On / off ----------------
    fun setMasterVolume(v: Double) {
        masterVolume = v.coerceIn(0.0, 1.0)
        if (!ready) return
        ctx.sync { trim.gain.rampTo(masterVolume, 0.15); ctx.destination.volume.value = if (masterVolume <= 0.001) -100.0 else 0.0 }
    }
    /** toggleAmbienceAudio. */
    fun toggle(): Boolean {
        if (!ready) {
            ctx.start()
            build()
            ready = true
            playing = true
            setMasterVolume(masterVolume)
        } else playing = !playing
        ctx.sync {
            if (playing) { ctx.pauseOutput(false); ctx.transport.start(ctx.currentTime + 0.1) } else ctx.transport.pause()
            master.gain.rampTo(if (playing) 0.9 else 0.0, if (playing) 1.2 else 0.4)
        }
        if (playing) updateLayered()
        else pauseAt = System.currentTimeMillis() + 1500
        return playing
    }
    private var pauseAt = 0L
    /** Paused audio stops the output thread's work entirely after the fade. */
    fun tick() { if (!playing && ready && pauseAt > 0 && System.currentTimeMillis() > pauseAt) { pauseAt = 0; ctx.pauseOutput(true) } }
    fun shutdown() { if (ready) ctx.stop() }
    @Suppress("unused") private fun unusedMesh(m: Mesh) = m
    @Suppress("unused") private val placesRef = Places.OUTSIDE_ENVS
}
