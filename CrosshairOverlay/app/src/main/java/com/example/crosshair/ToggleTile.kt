package com.example.crosshair

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class ToggleTile : TileService() {
    override fun onStartListening() { refresh() }
    override fun onClick() {
        if (OverlayService.running) {
            startService(Intent(this, OverlayService::class.java).setAction("STOP"))
        } else if (Settings.canDrawOverlays(this)) {
            startForegroundService(Intent(this, OverlayService::class.java))
        } else {
            openApp()   // needs "Display over other apps" first: open the app, which explains it
        }
        qsTile?.let { it.state = if (OverlayService.running) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE; it.updateTile() }
    }

    /** Android 14+ only accepts a PendingIntent here; the old Intent version throws and the tile crashed. */
    @Suppress("DEPRECATION")
    private fun openApp() {
        val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE))
        } else {
            startActivityAndCollapse(i)
        }
    }

    private fun refresh() { qsTile?.let { it.state = if (OverlayService.running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE; it.label = "Crosshair"; it.updateTile() } }
}
