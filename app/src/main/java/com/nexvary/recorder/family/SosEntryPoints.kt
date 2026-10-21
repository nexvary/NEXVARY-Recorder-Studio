package com.nexvary.recorder.family

import android.app.PendingIntent
import android.appwidget.*
import android.content.*
import android.os.Build
import android.service.quicksettings.*
import android.widget.RemoteViews
import com.nexvary.recorder.R

class SafetySosTile: TileService() {
    override fun onStartListening() { super.onStartListening();qsTile?.state=Tile.STATE_INACTIVE;qsTile?.updateTile() }
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated") // Legacy branch only on API <34.
    override fun onClick() {
        super.onClick()
        unlockAndRun {
            val intent=Intent(this,FamilyDashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("trigger",true)
            if(Build.VERSION.SDK_INT>=34) startActivityAndCollapse(PendingIntent.getActivity(this,50,intent,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            else { @Suppress("DEPRECATION") startActivityAndCollapse(intent) }
        }
    }
}
class SafetySosWidget: AppWidgetProvider() {
    override fun onUpdate(context: Context,manager: AppWidgetManager,ids: IntArray) {
        ids.forEach { id ->
            val views=RemoteViews(context.packageName,R.layout.widget_safety_sos)
            val open=PendingIntent.getActivity(context,51,Intent(context,FamilyDashboardActivity::class.java).putExtra("trigger",true),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            views.setOnClickPendingIntent(R.id.fsWidgetSos,open);manager.updateAppWidget(id,views)
        }
    }
}
