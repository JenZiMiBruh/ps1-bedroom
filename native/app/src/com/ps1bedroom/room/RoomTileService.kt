package com.ps1bedroom.room

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tile "Room sounds": pull down the shade and tap to play or pause the room (the song, or the
 * ambience), like a music app's tile. If the room isn't open yet, the tap opens it.
 */
class RoomTileService : TileService() {
    override fun onStartListening() { refresh() }

    override fun onClick() {
        val control = MediaService.onControl
        if (control != null) { control("toggle"); qsTile?.let { it.state = if (it.state == Tile.STATE_ACTIVE) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE; it.updateTile() } }
        else {
            val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 300, i, PendingIntent.FLAG_IMMUTABLE))
            else @Suppress("DEPRECATION") startActivityAndCollapse(i)
        }
    }

    private fun refresh() {
        val t = qsTile ?: return
        val s = MediaService.state
        t.state = if (s?.playing == true) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = "Room sounds"
        if (Build.VERSION.SDK_INT >= 29) t.subtitle = s?.title ?: "PS1 Bedroom"
        t.updateTile()
    }
}
