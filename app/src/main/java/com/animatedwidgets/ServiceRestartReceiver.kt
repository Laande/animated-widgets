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
            context.startService(serviceIntent)
        }
    }
}
