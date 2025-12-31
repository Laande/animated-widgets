package com.animatedwidgets

import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.RemoteViews
import pl.droidsonroids.gif.GifDrawable
import kotlin.math.max

class GifAnimationService : Service() {
    
    private val handler = Handler(Looper.getMainLooper())
    private val gifDrawables = mutableMapOf<Int, GifDrawable>()
    private var updateRunnable: Runnable? = null
    private var isRunning = false
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            loadGifs()
            startAnimation()
        }
        
        return START_STICKY
    }
    
    private fun loadGifs() {
        try {
            val prefs = WidgetPreferences(applicationContext)
            val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
            val componentName = ComponentName(applicationContext, ImageWidgetProvider::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
            
            var hasGifs = false
            
            for (widgetId in widgetIds) {
                val shouldAnimate = prefs.getWidgetAnimateGif(widgetId)
                if (!shouldAnimate) continue
                
                val imageUri = prefs.getWidgetImage(widgetId)
                if (imageUri != null && !gifDrawables.containsKey(widgetId)) {
                    try {
                        val uri = Uri.parse(imageUri)
                        val mimeType = contentResolver.getType(uri)
                        val isGif = mimeType?.contains("gif") == true || imageUri.lowercase().contains(".gif")
                        
                        if (isGif) {
                            val inputStream = contentResolver.openInputStream(uri)
                            if (inputStream != null) {
                                val bufferedInputStream = java.io.BufferedInputStream(inputStream)
                                val gifDrawable = GifDrawable(bufferedInputStream)
                                gifDrawable.loopCount = 0
                                gifDrawables[widgetId] = gifDrawable
                                hasGifs = true
                            }
                        }
                    } catch (e: pl.droidsonroids.gif.GifIOException) {
                        android.util.Log.e("GifAnimationService", "GIF IO error for widget $widgetId", e)
                    } catch (e: Exception) {
                        android.util.Log.e("GifAnimationService", "Error loading GIF for widget $widgetId", e)
                    }
                }
            }
            
            if (!hasGifs) {
                stopSelf()
            }
        } catch (e: Exception) {
            android.util.Log.e("GifAnimationService", "Error in loadGifs", e)
        }
    }
    
    private fun startAnimation() {
        updateRunnable = object : Runnable {
            override fun run() {
                val nextDelay = updateWidgets()
                handler.postDelayed(this, nextDelay)
            }
        }
        handler.post(updateRunnable!!)
    }
    
    private fun updateWidgets(): Long {
        val fixedDelay = 67L
        
        try {
            val prefs = WidgetPreferences(applicationContext)
            val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
            val componentName = ComponentName(applicationContext, ImageWidgetProvider::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
            
            var hasAnimatingWidget = false
            
            for (widgetId in widgetIds) {
                val shouldAnimate = prefs.getWidgetAnimateGif(widgetId)
                if (!shouldAnimate) continue
                
                hasAnimatingWidget = true
                
                val gifDrawable = gifDrawables[widgetId]
                if (gifDrawable != null) {
                    try {
                        val currentFrame = gifDrawable.currentFrame
                        
                        if (currentFrame != null && !currentFrame.isRecycled) {
                            val maxSize = 256
                            val scale = maxSize.toFloat() / max(currentFrame.width, currentFrame.height)
                            val newWidth = (currentFrame.width * scale).toInt()
                            val newHeight = (currentFrame.height * scale).toInt()
                            val scaledBitmap = Bitmap.createScaledBitmap(currentFrame, newWidth, newHeight, true)
                            
                            val views = RemoteViews(packageName, R.layout.widget_layout)
                            views.setImageViewBitmap(R.id.widget_image, scaledBitmap)
                            
                            appWidgetManager.updateAppWidget(widgetId, views)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("GifAnimationService", "Error updating widget $widgetId", e)
                    }
                }
            }
            
            if (!hasAnimatingWidget) {
                stopSelf()
            }
            
        } catch (e: Exception) {
            android.util.Log.e("GifAnimationService", "Error in updateWidgets", e)
        }
        
        return fixedDelay
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        updateRunnable?.let { handler.removeCallbacks(it) }
        gifDrawables.values.forEach { it.recycle() }
        gifDrawables.clear()
        
        scheduleRestart()
    }
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        scheduleRestart()
    }
    
    private fun scheduleRestart() {
        try {
            val prefs = WidgetPreferences(applicationContext)
            if (!prefs.isContinuousModeEnabled()) {
                return
            }
            
            if (prefs.getAllWidgets().any { prefs.getWidgetAnimateGif(it.widgetId) }) {
                val alarmManager = getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
                
                if (alarmManager != null) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        if (!alarmManager.canScheduleExactAlarms()) {
                            android.util.Log.w("GifAnimationService", "Cannot schedule exact alarms")
                            return
                        }
                    }
                    
                    val intent = Intent(applicationContext, ServiceRestartReceiver::class.java)
                    val pendingIntent = android.app.PendingIntent.getBroadcast(
                        applicationContext,
                        0,
                        intent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                    
                    try {
                        alarmManager.setExactAndAllowWhileIdle(
                            android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            android.os.SystemClock.elapsedRealtime() + 1000,
                            pendingIntent
                        )
                    } catch (e: SecurityException) {
                        android.util.Log.e("GifAnimationService", "SecurityException scheduling alarm", e)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("GifAnimationService", "Error scheduling restart", e)
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
