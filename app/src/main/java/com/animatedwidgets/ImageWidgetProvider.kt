package com.animatedwidgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImageWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        if (intent.action == ACTION_START_SERVICE) {
            try {
                val serviceIntent = Intent(context, GifAnimationService::class.java)
                context.startService(serviceIntent)
            } catch (e: Exception) {
                android.util.Log.e("ImageWidgetProvider", "Error starting service", e)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val prefs = WidgetPreferences(context)
        
        for (widgetId in appWidgetIds) {
            val imageUri = prefs.getWidgetImage(widgetId)
            if (imageUri != null && imageUri.isNotEmpty()) {
                updateWidget(context, appWidgetManager, widgetId, imageUri)
            } else {
                prefs.saveWidgetImage(widgetId, "")
                showPlaceholder(context, appWidgetManager, widgetId)
            }
        }
        
        startService(context)
    }
    
    private fun showPlaceholder(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout_placeholder)
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("configure_widget_id", widgetId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            widgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        views.setOnClickPendingIntent(R.id.widget_placeholder_text, pendingIntent)
        
        appWidgetManager.updateAppWidget(widgetId, views)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        startService(context)
    }
    
    private fun startService(context: Context) {
        try {
            val intent = Intent(context, GifAnimationService::class.java)
            context.startService(intent)
        } catch (e: Exception) {
            android.util.Log.e("ImageWidgetProvider", "Error starting service", e)
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        val intent = Intent(context, GifAnimationService::class.java)
        context.stopService(intent)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = WidgetPreferences(context)
        for (widgetId in appWidgetIds) {
            prefs.removeWidget(widgetId)
        }
    }

    companion object {
        const val ACTION_START_SERVICE = "com.animatedwidgets.START_SERVICE"
        
        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int, imageUri: String, @Suppress("UNUSED_PARAMETER") animateGif: Boolean = true) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val bitmap = Glide.with(context.applicationContext)
                        .asBitmap()
                        .load(Uri.parse(imageUri))
                        .submit()
                        .get()
                    
                    withContext(Dispatchers.Main) {
                        val views = RemoteViews(context.packageName, R.layout.widget_layout)
                        views.setImageViewBitmap(R.id.widget_image, bitmap)
                        
                        val prefs = WidgetPreferences(context)
                        val isGif = prefs.getWidgetAnimateGif(widgetId)
                        
                        if (isGif) {
                            val intent = Intent(context, ImageWidgetProvider::class.java).apply {
                                action = ACTION_START_SERVICE
                            }
                            val pendingIntent = PendingIntent.getBroadcast(
                                context,
                                widgetId,
                                intent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                            views.setOnClickPendingIntent(R.id.widget_image, pendingIntent)
                        }
                        
                        appWidgetManager.updateAppWidget(widgetId, views)
                        
                        try {
                            val intent = Intent(context, GifAnimationService::class.java)
                            context.startService(intent)
                        } catch (e: Exception) {
                            android.util.Log.e("ImageWidgetProvider", "Error starting service for widget $widgetId", e)
                        }
                    }
                    
                } catch (e: Exception) {
                    android.util.Log.e("ImageWidgetProvider", "Error updating widget $widgetId", e)
                }
            }
        }
    }
}
