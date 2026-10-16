package com.ps1bedroom.ambience;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import org.json.JSONObject;

/**
 * Keeps the room's sound going with the screen off or in other apps: a media-style notification
 * (which also shows on the lock screen with play / pause / sleep controls) and a CPU wake lock while playing.
 * The sound itself is made by the page in MainActivity; this service just keeps the app alive and
 * passes button presses back to it.
 */
public class AmbienceService extends Service {
    private static final String CHANNEL = "ambience";
    private static final int NOTE_ID = 1001;
    static final String ACTION_SYNC = "sync";
    static final String ACTION_PAUSE = "pause";
    static final String ACTION_PLAY = "play";
    static final String ACTION_SLEEP = "sleep30";
    static final String ACTION_STOP = "stop";

    private MediaSession session;
    private PowerManager.WakeLock wakeLock;
    private boolean foreground = false;

    static void sync(Context c, String json, boolean playing) {
        Intent i = new Intent(c, AmbienceService.class).setAction(ACTION_SYNC).putExtra("state", json);
        try {
            if (playing && Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (Exception ignored) {
            // Not allowed to start from the background right now; the next update will try again
        }
    }

    static void stop(Context c) {
        c.stopService(new Intent(c, AmbienceService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Ambience playback", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Shows while your room is playing, with play, pause and sleep controls");
            ch.setShowBadge(false);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
        session = new MediaSession(this, "PS1Bedroom");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { js("ps1App.play()"); }
            @Override public void onPause() { js("ps1App.pause()"); }
            @Override public void onStop() { js("ps1App.pause()"); }
            @Override public void onCustomAction(String action, android.os.Bundle extras) {
                if (ACTION_SLEEP.equals(action)) js("ps1App.sleep(30)");
            }
        });
        session.setActive(true);
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PS1Bedroom:ambience");
        wakeLock.setReferenceCounted(false);
    }

    private void js(String code) {
        MainActivity a = MainActivity.current;
        if (a != null) a.callJs("window.ps1App && " + code);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_PAUSE.equals(action)) js("ps1App.pause()");
        else if (ACTION_PLAY.equals(action)) js("ps1App.play()");
        else if (ACTION_SLEEP.equals(action)) js("ps1App.sleep(30)");
        else if (ACTION_STOP.equals(action)) { js("ps1App.pause()"); stopSelf(); return START_NOT_STICKY; }

        JSONObject state = AppState.get(this);
        if (intent != null && intent.hasExtra("state")) {
            try { state = new JSONObject(intent.getStringExtra("state")); } catch (Exception ignored) { }
        }
        boolean playing = state.optBoolean("playing", false);
        Notification n = buildNotification(state, playing);
        if (playing) {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            else startForeground(NOTE_ID, n);
            foreground = true;
            if (!wakeLock.isHeld()) wakeLock.acquire(10 * 60 * 60 * 1000L); // safety cap: 10 hours
        } else {
            if (foreground) {
                // Keep a dismissible "paused" card so it can be resumed from the lock screen
                if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_DETACH);
                else stopForeground(false);
                foreground = false;
            }
            if (wakeLock.isHeld()) wakeLock.release();
            if (MainActivity.current == null) { stopSelf(); return START_NOT_STICKY; }
            getSystemService(NotificationManager.class).notify(NOTE_ID, n);
        }
        return START_NOT_STICKY;
    }

    private PendingIntent servicePi(String action, int code) {
        Intent i = new Intent(this, AmbienceService.class).setAction(action);
        return PendingIntent.getService(this, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification buildNotification(JSONObject s, boolean playing) {
        String time = s.optString("time", "");
        String line = (time.length() > 0 ? time + " · " : "") + AppState.statusLine(s);
        int sleepLeft = s.optInt("sleepLeftMin", 0);
        String sub = sleepLeft > 0 ? "Sleep in " + sleepLeft + " min" : (playing ? "Playing in the background" : "Paused");
        Bitmap art = WidgetArt.render(this, s, AppState.hourNow(), 64, 64, 6);

        session.setMetadata(new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, "PS1 Bedroom")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, line)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, sub)
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art)
                .build());
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_STOP)
                .addCustomAction(new PlaybackState.CustomAction.Builder(ACTION_SLEEP, "Sleep 30 min", R.drawable.ic_sleep).build())
                .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build());

        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_stat_moon)
                .setContentTitle("PS1 Bedroom")
                .setContentText(line)
                .setSubText(sub)
                .setLargeIcon(art)
                .setContentIntent(openPi)
                .setDeleteIntent(servicePi(ACTION_STOP, 4))
                .setShowWhen(false)
                .setOngoing(playing)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, playing ? R.drawable.ic_pause : R.drawable.ic_play),
                        playing ? "Pause" : "Play", servicePi(playing ? ACTION_PAUSE : ACTION_PLAY, 1)).build())
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_sleep), "Sleep 30", servicePi(ACTION_SLEEP, 2)).build())
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_stop), "Stop", servicePi(ACTION_STOP, 3)).build())
                .setStyle(new Notification.MediaStyle()
                        .setMediaSession(session.getSessionToken())
                        .setShowActionsInCompactView(0, 1));
        return b.build();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (session != null) session.release();
        getSystemService(NotificationManager.class).cancel(NOTE_ID);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
