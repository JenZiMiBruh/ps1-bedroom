package com.ps1bedroom.room

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder

/**
 * The room as a proper Android media app (like Spotify): a MediaSession with the song (or the room) and its cover,
 * so the phone shows it in the notification shade's media player, on the lock screen and on watches / cars, and
 * headphone and Bluetooth buttons work. Also keeps the process alive with the screen off, takes audio focus (pauses
 * for calls and other players), and pauses when headphones come out. The sound itself runs on the app's audio thread.
 */
class MediaService : Service() {
    /** What the session shows. */
    class State(val playing: Boolean, val title: String, val artist: String, val album: String, val art: Bitmap?,
                val music: Boolean, val posMs: Long, val durMs: Long) {
        fun sameAs(o: State?) = o != null && o.playing == playing && o.title == title && o.artist == artist && o.album == album && o.art === art && o.music == music &&
            o.durMs == durMs && kotlin.math.abs((o.posMs) - posMs) < 2500
    }
    companion object {
        const val CHANNEL = "playback"
        const val ACTION_CONTROL = "com.ps1bedroom.room.CONTROL"
        /** Set by the activity: play / pause / toggle / next / prev / stop, and seek (ms). */
        @Volatile var onControl: ((String) -> Unit)? = null
        @Volatile var onSeek: ((Long) -> Unit)? = null
        @Volatile var state: State? = null
        @Volatile private var instance: MediaService? = null
        /** Push a new state: starts the service while something plays, updates it otherwise. */
        fun update(ctx: Context, s: State) {
            val old = state
            state = s
            val svc = instance
            if (svc != null) { if (!s.sameAs(old)) svc.publish() }
            else if (s.playing) {
                val i = Intent(ctx, MediaService::class.java)
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
            }
        }
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, MediaService::class.java)) }
    }

    private lateinit var session: MediaSession
    private lateinit var am: AudioManager
    private var focus: AudioFocusRequest? = null
    private var hasFocus = false
    private var pausedByFocus = false
    private var foreground = false

    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { if (i?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY && state?.playing == true) onControl?.invoke("pause") }
    }
    private val focusListener = AudioManager.OnAudioFocusChangeListener { ch ->
        when (ch) {
            AudioManager.AUDIOFOCUS_LOSS -> { hasFocus = false; if (state?.playing == true) onControl?.invoke("pause") }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (state?.playing == true) { pausedByFocus = true; onControl?.invoke("pause") }
            AudioManager.AUDIOFOCUS_GAIN -> { hasFocus = true; if (pausedByFocus) { pausedByFocus = false; onControl?.invoke("play") } }
            // a navigation prompt or notification ding: the room is quiet ambience, it just keeps going underneath
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        am = getSystemService(AudioManager::class.java)
        session = MediaSession(this, "PS1Bedroom").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { onControl?.invoke("play") }
                override fun onPause() { onControl?.invoke("pause") }
                override fun onStop() { onControl?.invoke("pause") }
                override fun onSkipToNext() { onControl?.invoke("next") }
                override fun onSkipToPrevious() { onControl?.invoke("prev") }
                override fun onSeekTo(pos: Long) { onSeek?.invoke(pos) }
            })
            setSessionActivity(PendingIntent.getActivity(this@MediaService, 0, Intent(this@MediaService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE))
            isActive = true
        }
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Now playing", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A widget tap while the room isn't open: nothing to control, and nothing to show
        if (onControl == null) { stopSelf(); return START_NOT_STICKY }
        if (intent?.action == ACTION_CONTROL) { intent.getStringExtra("cmd")?.let { if (it == "dismiss") { stopSelf(); return START_NOT_STICKY }; onControl?.invoke(it) } }
        publish()
        return START_NOT_STICKY
    }

    private fun ctl(cmd: String, code: Int) = PendingIntent.getService(this, code, Intent(this, MediaService::class.java).setAction(ACTION_CONTROL).putExtra("cmd", cmd), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** Session metadata + playback state + the MediaStyle notification. */
    private val roomArt: Bitmap? by lazy { try { android.graphics.BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher) } catch (_: Exception) { null } }
    fun publish() {
        val s0 = state ?: State(true, "PS1 Bedroom", "The room is playing", "", null, false, 0, 0)
        // The room always has a cover (like any music app), so the phone's media player card looks right
        val s = if (s0.art == null) State(s0.playing, s0.title, s0.artist, s0.album, roomArt, s0.music, s0.posMs, s0.durMs) else s0
        val meta = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, s.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, s.artist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, s.album)
        if (s.durMs > 0) meta.putLong(MediaMetadata.METADATA_KEY_DURATION, s.durMs)
        if (s.art != null) meta.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, s.art)
        session.setMetadata(meta.build())
        var actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP or
            PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS
        if (s.music && s.durMs > 0) actions = actions or PlaybackState.ACTION_SEEK_TO
        session.setPlaybackState(PlaybackState.Builder().setActions(actions)
            .setState(if (s.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, if (s.music) s.posMs else PlaybackState.PLAYBACK_POSITION_UNKNOWN, if (s.playing) 1f else 0f).build())

        if (s.playing) requestFocus()
        else if (!pausedByFocus && hasFocus) { abandonFocus(); hasFocus = false }
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this)
        @Suppress("DEPRECATION")
        b.setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(s.title).setContentText(s.artist)
            .setLargeIcon(s.art)
            .setContentIntent(session.controller.sessionActivity)
            .setDeleteIntent(ctl("dismiss", 9))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(s.playing)
            .addAction(Notification.Action.Builder(android.R.drawable.ic_media_previous, "Previous", ctl("prev", 2)).build())
            .addAction(if (s.playing) Notification.Action.Builder(android.R.drawable.ic_media_pause, "Pause", ctl("pause", 3)).build()
                       else Notification.Action.Builder(android.R.drawable.ic_media_play, "Play", ctl("play", 4)).build())
            .addAction(Notification.Action.Builder(android.R.drawable.ic_media_next, "Next", ctl("next", 5)).build())
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
        val n = b.build()
        if (s.playing) {
            if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) else startForeground(1, n)
            foreground = true
        } else {
            // Paused: like any music app the notification stays (swipe it away to close), but the app may sleep
            if (foreground) { if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_DETACH) else @Suppress("DEPRECATION") stopForeground(false); foreground = false }
            getSystemService(NotificationManager::class.java).notify(1, n)
        }
    }

    private fun requestFocus() {
        if (hasFocus) return
        hasFocus = if (Build.VERSION.SDK_INT >= 26) {
            val req = focus ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setWillPauseWhenDucked(false).setOnAudioFocusChangeListener(focusListener).build().also { focus = it }
            am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else @Suppress("DEPRECATION") (am.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
    }

    private fun abandonFocus() { if (Build.VERSION.SDK_INT >= 26) focus?.let { am.abandonAudioFocusRequest(it) } else @Suppress("DEPRECATION") am.abandonAudioFocus(focusListener) }

    override fun onDestroy() {
        instance = null
        try { unregisterReceiver(noisy) } catch (e: Exception) { }
        if (Build.VERSION.SDK_INT >= 26) focus?.let { am.abandonAudioFocusRequest(it) } else @Suppress("DEPRECATION") am.abandonAudioFocus(focusListener)
        session.isActive = false; session.release()
        getSystemService(NotificationManager::class.java).cancel(1)
        super.onDestroy()
    }

    /** Swiping the app away from recents stops the sound too. */
    override fun onTaskRemoved(rootIntent: Intent?) { onControl?.invoke("pause"); stopSelf() }
}
