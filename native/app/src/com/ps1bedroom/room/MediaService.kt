package com.ps1bedroom.room

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Keeps the room's sound playing with the screen off or in other apps (the HTML app's Android media service):
 * a foreground service with an ongoing notification and a Pause button. The sound itself runs on the app's
 * audio thread; this only keeps the process alive and in the foreground.
 */
class MediaService : Service() {
    companion object {
        const val CHANNEL = "playback"
        const val ACTION_PAUSE = "com.ps1bedroom.room.PAUSE"
        /** Set by the activity: what the notification's Pause button does. */
        @Volatile var onPause: (() -> Unit)? = null
        fun start(ctx: Context) {
            val i = Intent(ctx, MediaService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, MediaService::class.java)) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PAUSE) { onPause?.invoke(); stopSelf(); return START_NOT_STICKY }
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Room sounds", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val pause = PendingIntent.getService(this, 1, Intent(this, MediaService::class.java).setAction(ACTION_PAUSE), PendingIntent.FLAG_IMMUTABLE)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this)
        val n = b.setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("PS1 Bedroom")
            .setContentText("The room is playing")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Pause", pause).build())
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) else startForeground(1, n)
        return START_NOT_STICKY
    }

    /** Swiping the app away stops the sound too. */
    override fun onTaskRemoved(rootIntent: Intent?) { onPause?.invoke(); stopSelf() }
}
