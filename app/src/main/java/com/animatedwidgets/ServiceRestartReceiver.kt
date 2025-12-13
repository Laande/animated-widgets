package com.animatedwidgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class ServiceRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = WidgetPreferences(context)
        if (prefs.getAllWidgets().any { prefs.getWidgetAnimateGif(it.widgetId) }) {
            val serviceIntent = Intent(context, GifAnimationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && prefs.isContinuousModeEnabled()) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
