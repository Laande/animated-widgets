package com.animatedwidgets

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.widget.RemoteViews
import pl.droidsonroids.gif.GifDrawable

class GifAnimationService : Service() {
    
    private val handler = Handler(Looper.getMainLooper())
    private val gifDrawables = mutableMapOf<Int, GifDrawable>()
    private var updateRunnable: Runnable? = null
    private var isRunning = false
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "gif_widget_channel"
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            
            val prefs = WidgetPreferences(applicationContext)
            if (prefs.isContinuousModeEnabled()) {
                try {
                    startForegroundMode()
                } catch (e: Exception) {
                }
            }
            
            loadGifs()
            startAnimation()
        }
        
        return START_STICKY
    }
    
    private fun startForegroundMode() {
        createNotificationChannel()
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Widget Animations",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keeps GIF widgets animated continuously"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        
        builder
            .setContentTitle("Animated Widgets")
            .setContentText("Continuous mode active")
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            builder.setPriority(Notification.PRIORITY_MIN)
        }
        
        return builder.build()
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
                    } catch (e: Exception) {
                    }
                }
            }
            
            if (!hasGifs) {
                stopSelf()
            }
        } catch (e: Exception) {
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
        var minDelay = 100L
        
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
                            val scale = maxSize.toFloat() / Math.max(currentFrame.width, currentFrame.height)
                            val newWidth = (currentFrame.width * scale).toInt()
                            val newHeight = (currentFrame.height * scale).toInt()
                            val scaledBitmap = Bitmap.createScaledBitmap(currentFrame, newWidth, newHeight, true)
                            
                            val views = RemoteViews(packageName, R.layout.widget_layout)
                            views.setImageViewBitmap(R.id.widget_image, scaledBitmap)
                            
                            appWidgetManager.updateAppWidget(widgetId, views)
                            
                            val currentFrameIndex = gifDrawable.currentFrameIndex
                            val frameDuration = gifDrawable.getFrameDuration(currentFrameIndex).toLong()
                            if (frameDuration > 0 && frameDuration < minDelay) {
                                minDelay = frameDuration
                            }
                        }
                    } catch (e: Exception) {
                    }
                }
            }
            
            if (!hasAnimatingWidget) {
                stopSelf()
            }
            
        } catch (e: Exception) {
        }
        
        return minDelay
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
            if (prefs.getAllWidgets().any { prefs.getWidgetAnimateGif(it.widgetId) }) {
                val intent = Intent(applicationContext, ServiceRestartReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    applicationContext,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                val alarmManager = getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
                alarmManager?.setExactAndAllowWhileIdle(
                    android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    android.os.SystemClock.elapsedRealtime() + 1000,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
