package com.animatedwidgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ScreenStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = WidgetPreferences(context)
        if (!prefs.isContinuousModeEnabled()) {
            return
        }
        
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                val serviceIntent = Intent(context, GifAnimationService::class.java)
                context.stopService(serviceIntent)
            }
            Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                if (prefs.getAllWidgets().any { prefs.getWidgetAnimateGif(it.widgetId) }) {
                    val serviceIntent = Intent(context, GifAnimationService::class.java)
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
