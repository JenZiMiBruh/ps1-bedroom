package com.ps1bedroom.room

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Home-screen "Now playing" widget: the song (with its cover) or the room, and ⏮ ⏯ ⏭ that go through the same
 * media service as the notification. Updated only when the playing state actually changes (no polling).
 */
class NowPlayingWidget : AppWidgetProvider() {
    companion object {
        private var last: MediaService.State? = null
        fun update(ctx: Context, s: MediaService.State?) {
            if (s != null && s.sameAs(last)) return
            last = s
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, NowPlayingWidget::class.java))
            if (ids.isEmpty()) return
            mgr.updateAppWidget(ids, views(ctx, s))
        }
        private fun views(ctx: Context, s: MediaService.State?): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_now_playing)
            v.setTextViewText(R.id.w_title, s?.title ?: "PS1 Bedroom")
            v.setTextViewText(R.id.w_sub, s?.artist ?: "Tap to open your room")
            if (s?.art != null) v.setImageViewBitmap(R.id.w_art, s.art) else v.setImageViewResource(R.id.w_art, R.mipmap.ic_launcher)
            v.setImageViewResource(R.id.w_play, if (s?.playing == true) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            fun ctl(cmd: String, code: Int) = PendingIntent.getService(ctx, 100 + code, Intent(ctx, MediaService::class.java).setAction(MediaService.ACTION_CONTROL).putExtra("cmd", cmd),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            // Before the room has been opened there's nothing to control yet: every tap opens it
            val open = PendingIntent.getActivity(ctx, 99, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
            v.setOnClickPendingIntent(R.id.w_root, open)
            val live = s != null
            v.setOnClickPendingIntent(R.id.w_prev, if (live) ctl("prev", 1) else open)
            v.setOnClickPendingIntent(R.id.w_play, if (live) ctl("toggle", 2) else open)
            v.setOnClickPendingIntent(R.id.w_next, if (live) ctl("next", 3) else open)
            return v
        }
    }
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) { mgr.updateAppWidget(ids, views(ctx, MediaService.state)) }
}
