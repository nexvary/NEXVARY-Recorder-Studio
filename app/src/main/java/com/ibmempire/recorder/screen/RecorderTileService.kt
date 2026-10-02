package com.ibmempire.recorder.screen

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class RecorderTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.state =
            if (ScreenRecordService.isRecording) Tile.STATE_ACTIVE
            else Tile.STATE_INACTIVE
        qsTile?.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()

        if (ScreenRecordService.isRecording) {
            startService(
                Intent(this, ScreenRecordService::class.java).apply {
                    action = ScreenRecordService.ACTION_STOP
                }
            )
            qsTile?.state = Tile.STATE_INACTIVE
            qsTile?.updateTile()
            return
        }

        val intent =
            Intent(this, ScreenRecorderActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
            }

        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                android.app.PendingIntent.getActivity(
                    this,
                    100,
                    intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                        android.app.PendingIntent.FLAG_IMMUTABLE
                )
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
