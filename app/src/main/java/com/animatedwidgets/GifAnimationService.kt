package com.animatedwidgets

import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.IBinder
import android.widget.RemoteViews
import pl.droidsonroids.gif.GifDrawable
import kotlin.math.max

class GifAnimationService : Service() {
    
    private val gifFrames = mutableMapOf<Int, List<Bitmap>>()
    private val frameIndices = mutableMapOf<Int, Int>()
    private val widgetIds = mutableSetOf<Int>()
    
    private val pauseLock = Object()
    @Volatile private var isPaused = false
    @Volatile private var isRunning = false
    
    private var animationThread: Thread? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                pauseAnimation()
                return START_STICKY
            }
            ACTION_RESUME -> {
                resumeAnimation()
                return START_STICKY
            }
        }
        
        if (!isRunning) {
            isRunning = true
            loadAndStartAnimations()
        }
        
        return START_STICKY
    }
    
    private fun pauseAnimation() {
        isPaused = true
        synchronized(pauseLock) {
            pauseLock.notifyAll()
        }
    }
    
    private fun resumeAnimation() {
        isPaused = false
        synchronized(pauseLock) {
            pauseLock.notifyAll()
        }
    }
    
    private fun loadAndStartAnimations() {
        val prefs = WidgetPreferences(applicationContext)
        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
        val componentName = ComponentName(applicationContext, ImageWidgetProvider::class.java)
        val ids = appWidgetManager.getAppWidgetIds(componentName)
        
        for (widgetId in ids) {
            if (!prefs.getWidgetAnimateGif(widgetId)) continue
            
            val imageUri = prefs.getWidgetImage(widgetId) ?: continue
            
            if (!gifFrames.containsKey(widgetId)) {
                val frames = extractFrames(imageUri)
                if (frames.isNotEmpty()) {
                    gifFrames[widgetId] = frames
                    frameIndices[widgetId] = 0
                    widgetIds.add(widgetId)
                }
            } else {
                widgetIds.add(widgetId)
            }
        }
        
        if (widgetIds.isEmpty()) {
            stopSelf()
        } else if (animationThread == null) {
            startSynchronizedAnimation()
        }
    }
    
    private fun extractFrames(imageUri: String): List<Bitmap> {
        return try {
            val uri = Uri.parse(imageUri)
            val mimeType = contentResolver.getType(uri)
            val isGif = mimeType?.contains("gif") == true || imageUri.lowercase().contains(".gif")
            
            if (!isGif) return emptyList()
            
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val gifDrawable = GifDrawable(inputStream.buffered())
                val frameCount = gifDrawable.numberOfFrames
                val frames = mutableListOf<Bitmap>()
                val maxSize = 256
                
                for (i in 0 until frameCount) {
                    gifDrawable.seekToFrame(i)
                    val currentFrame = gifDrawable.currentFrame ?: continue
                    if (currentFrame.isRecycled) continue
                    
                    val scale = maxSize.toFloat() / max(currentFrame.width, currentFrame.height)
                    val newWidth = (currentFrame.width * scale).toInt()
                    val newHeight = (currentFrame.height * scale).toInt()
                    
                    val bitmap = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.RGB_565)
                    val canvas = Canvas(bitmap)
                    val srcRect = android.graphics.Rect(0, 0, currentFrame.width, currentFrame.height)
                    val dstRect = android.graphics.Rect(0, 0, newWidth, newHeight)
                    canvas.drawBitmap(currentFrame, srcRect, dstRect, null)
                    
                    frames.add(bitmap)
                }
                
                gifDrawable.recycle()
                frames
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
    
    private fun startSynchronizedAnimation() {
        animationThread = Thread {
            val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
            val minUpdateInterval = 150L
            
            try {
                while (isRunning && !Thread.currentThread().isInterrupted) {
                    while (isPaused && isRunning) {
                        synchronized(pauseLock) {
                            pauseLock.wait()
                        }
                    }
                    
                    if (!isRunning) break
                    
                    updateAllWidgets(appWidgetManager)
                    
                    Thread.sleep(minUpdateInterval)
                }
            } catch (_: InterruptedException) {
            } catch (_: Exception) {
            }
        }.apply { start() }
    }
    
    private fun updateAllWidgets(appWidgetManager: AppWidgetManager) {
        if (isPaused) return
        
        try {
            for (widgetId in widgetIds) {
                val frames = gifFrames[widgetId] ?: continue
                if (frames.isEmpty()) continue
                
                val currentIndex = frameIndices[widgetId] ?: 0
                val bitmap = frames[currentIndex]
                
                if (bitmap.isRecycled) continue
                
                val views = RemoteViews(packageName, R.layout.widget_layout)
                views.setImageViewBitmap(R.id.widget_image, bitmap)
                appWidgetManager.updateAppWidget(widgetId, views)
                
                frameIndices[widgetId] = (currentIndex + 1) % frames.size
            }
        } catch (_: Exception) {
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        
        animationThread?.let {
            it.interrupt()
            try {
                it.join(1000)
            } catch (_: InterruptedException) {
            }
        }
        animationThread = null
        
        gifFrames.values.forEach { frames ->
            frames.forEach { it.recycle() }
        }
        gifFrames.clear()
        frameIndices.clear()
        widgetIds.clear()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    companion object {
        const val ACTION_PAUSE = "com.animatedwidgets.ACTION_PAUSE"
        const val ACTION_RESUME = "com.animatedwidgets.ACTION_RESUME"
    }
}
