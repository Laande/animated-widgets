package com.animatedwidgets

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

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val prefs = WidgetPreferences(context)
        
        for (widgetId in appWidgetIds) {
            val imageUri = prefs.getWidgetImage(widgetId)
            if (imageUri != null) {
                updateWidget(context, appWidgetManager, widgetId, imageUri)
            }
        }
        
        val intent = Intent(context, GifAnimationService::class.java)
        context.startService(intent)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val intent = Intent(context, GifAnimationService::class.java)
        context.startService(intent)
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
                        
                        appWidgetManager.updateAppWidget(widgetId, views)
                        
                        val intent = Intent(context, GifAnimationService::class.java)
                        context.startService(intent)
                    }
                    
                } catch (e: Exception) {
                }
            }
        }
    }
}
