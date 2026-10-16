package com.ps1bedroom.ambience;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import org.json.JSONObject;

/** "My window" widget: a live pixel-art view out of the bedroom window, with the clock and weather. */
public class WindowWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) mgr.updateAppWidget(id, build(context));
    }

    static RemoteViews build(Context c) {
        JSONObject s = AppState.get(c);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_window);
        v.setImageViewBitmap(R.id.window_art, WidgetArt.render(c, s, AppState.hourNow(), 96, 60, 4));
        v.setTextViewText(R.id.window_status, AppState.statusLine(s));
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        v.setOnClickPendingIntent(R.id.window_root, PendingIntent.getActivity(c, 10, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return v;
    }

    static void updateAll(Context c) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(c);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(c, WindowWidget.class));
        if (ids.length == 0) return;
        RemoteViews v = build(c);
        for (int id : ids) mgr.updateAppWidget(id, v);
    }
}
