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
    
    private data class GifFrames(
        val frames: List<Bitmap>,
        val delays: List<Int>
    )
    
    private data class WidgetState(
        val frames: GifFrames,
        var currentFrame: Int = 0,
        var nextUpdateTime: Long = 0
    )
    
    private val widgets = mutableMapOf<Int, WidgetState>()
    private val cache = mutableMapOf<String, GifFrames>()
    
    @Volatile private var isRunning = false
    @Volatile private var isPaused = false
    private var thread: Thread? = null
    
    companion object {
        const val ACTION_PAUSE = "com.animatedwidgets.ACTION_PAUSE"
        const val ACTION_RESUME = "com.animatedwidgets.ACTION_RESUME"
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> isPaused = true
            ACTION_RESUME -> isPaused = false
            else -> {
                if (!isRunning) {
                    isRunning = true
                    loadWidgets()
                    startAnimationLoop()
                }
            }
        }
        return START_STICKY
    }
    
    private fun extractGifFrames(uri: String): GifFrames? {
        return try {
            val parsedUri = Uri.parse(uri)
            contentResolver.openInputStream(parsedUri)?.use { stream ->
                val gif = GifDrawable(stream.buffered())
                val frames = mutableListOf<Bitmap>()
                val delays = mutableListOf<Int>()
                
                for (i in 0 until gif.numberOfFrames) {
                    gif.seekToFrame(i)
                    val frame = gif.currentFrame ?: continue
                    
                    val size = 128
                    val scale = size.toFloat() / max(frame.width, frame.height)
                    val w = (frame.width * scale).toInt()
                    val h = (frame.height * scale).toInt()
                    
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
                    val canvas = Canvas(bitmap)
                    canvas.drawBitmap(frame, 
                        android.graphics.Rect(0, 0, frame.width, frame.height),
                        android.graphics.Rect(0, 0, w, h), null)
                    
                    frames.add(bitmap)
                    delays.add(gif.getFrameDuration(i))
                }
                
                gif.recycle()
                if (frames.isEmpty()) null else GifFrames(frames, delays)
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun loadWidgets() {
        val prefs = WidgetPreferences(applicationContext)
        val manager = AppWidgetManager.getInstance(applicationContext)
        val component = ComponentName(applicationContext, ImageWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(component)
        
        val now = System.currentTimeMillis()
        var offset = 20
        
        for (id in ids) {
            if (!prefs.getWidgetAnimateGif(id)) continue
            val uri = prefs.getWidgetImage(id) ?: continue
            
            val frames = cache[uri] ?: extractGifFrames(uri) ?: continue
            cache[uri] = frames
            
            widgets[id] = WidgetState(frames, 0, now + offset)
            offset += 20
        }
        
        if (widgets.isEmpty()) stopSelf()
    }
    
    private fun startAnimationLoop() {
        thread = Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY)
            val manager = AppWidgetManager.getInstance(applicationContext)
            
            while (isRunning) {
                if (isPaused) {
                    Thread.sleep(100)
                    continue
                }
                
                val now = System.currentTimeMillis()
                var nextWakeup = now + 1000
                val toUpdate = mutableListOf<Triple<Int, Bitmap, WidgetState>>()
                
                for ((widgetId, state) in widgets) {
                    if (now >= state.nextUpdateTime) {
                        toUpdate.add(Triple(widgetId, state.frames.frames[state.currentFrame], state))
                    }
                    
                    if (state.nextUpdateTime < nextWakeup) {
                        nextWakeup = state.nextUpdateTime
                    }
                }
                
                for ((widgetId, frame, state) in toUpdate) {
                    try {
                        val views = RemoteViews(packageName, R.layout.widget_layout)
                        views.setImageViewBitmap(R.id.widget_image, frame)
                        manager.updateAppWidget(widgetId, views)
                        
                        val delay = state.frames.delays[state.currentFrame].toLong()
                        state.currentFrame = (state.currentFrame + 1) % state.frames.frames.size
                        state.nextUpdateTime += delay
                    } catch (_: Exception) {
                    }
                }
                
                val sleep = (nextWakeup - System.currentTimeMillis()).coerceIn(1, 100)
                Thread.sleep(sleep)
            }
        }.apply { start() }
    }
    
    override fun onDestroy() {
        isRunning = false
        thread?.interrupt()
        widgets.clear()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
