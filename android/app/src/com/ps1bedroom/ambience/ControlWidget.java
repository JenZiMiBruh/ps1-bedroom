package com.ps1bedroom.ambience;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import org.json.JSONObject;

/** "Room controls" widget: play / pause the ambience and start a 30-minute sleep timer. */
public class ControlWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) mgr.updateAppWidget(id, build(context));
    }

    static RemoteViews build(Context c) {
        JSONObject s = AppState.get(c);
        boolean playing = s.optBoolean("playing", false) && MainActivity.current != null;
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_control);
        v.setImageViewBitmap(R.id.control_art, WidgetArt.render(c, s, AppState.hourNow(), 40, 40, 4));
        int sleep = s.optInt("sleepLeftMin", 0);
        String status = playing ? (sleep > 0 ? "Playing · sleep in " + sleep + " min" : "Playing · " + AppState.statusLine(s)) : "Tap play to start your room";
        v.setTextViewText(R.id.control_status, status);
        v.setImageViewResource(R.id.control_play, playing ? R.drawable.ic_pause : R.drawable.ic_play);

        // Play has to open the app (the sound lives in it); pause and sleep go straight to the running service
        PendingIntent playPi;
        if (playing) {
            playPi = PendingIntent.getService(c, 21, new Intent(c, AmbienceService.class).setAction(AmbienceService.ACTION_PAUSE),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            Intent i = new Intent(c, MainActivity.class).setAction(MainActivity.ACTION_PLAY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            playPi = PendingIntent.getActivity(c, 22, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }
        v.setOnClickPendingIntent(R.id.control_play, playPi);
        PendingIntent sleepPi;
        if (playing) {
            sleepPi = PendingIntent.getService(c, 23, new Intent(c, AmbienceService.class).setAction(AmbienceService.ACTION_SLEEP),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            Intent i = new Intent(c, MainActivity.class).setAction(MainActivity.ACTION_SLEEP30).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            sleepPi = PendingIntent.getActivity(c, 24, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }
        v.setOnClickPendingIntent(R.id.control_sleep, sleepPi);
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        v.setOnClickPendingIntent(R.id.control_art, PendingIntent.getActivity(c, 25, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return v;
    }

    static void updateAll(Context c) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(c);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(c, ControlWidget.class));
        if (ids.length == 0) return;
        RemoteViews v = build(c);
        for (int id : ids) mgr.updateAppWidget(id, v);
    }
}
