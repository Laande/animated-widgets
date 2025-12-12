package com.animatedwidgets

import android.content.Context
import android.content.SharedPreferences

class WidgetPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)

    fun saveWidgetImage(widgetId: Int, imageUri: String) {
        prefs.edit().putString("widget_$widgetId", imageUri).apply()
        
        if (!prefs.contains("display_$widgetId")) {
            val nextDisplayId = getNextDisplayId()
            prefs.edit().putInt("display_$widgetId", nextDisplayId).apply()
        }
    }
    
    fun getWidgetName(widgetId: Int): String? {
        return prefs.getString("name_$widgetId", null)
    }
    
    fun saveWidgetName(widgetId: Int, name: String) {
        prefs.edit().putString("name_$widgetId", name).apply()
    }
    
    fun isContinuousModeEnabled(): Boolean {
        return prefs.getBoolean("continuous_mode", true)
    }
    
    fun setContinuousMode(enabled: Boolean) {
        prefs.edit().putBoolean("continuous_mode", enabled).apply()
    }

    fun getWidgetImage(widgetId: Int): String? {
        return prefs.getString("widget_$widgetId", null)
    }

    fun saveWidgetAnimateGif(widgetId: Int, animate: Boolean) {
        prefs.edit().putBoolean("animate_$widgetId", animate).apply()
    }

    fun getWidgetAnimateGif(widgetId: Int): Boolean {
        return prefs.getBoolean("animate_$widgetId", true)
    }

    fun getWidgetDisplayId(widgetId: Int): Int {
        return prefs.getInt("display_$widgetId", 0)
    }

    fun getCurrentFrame(widgetId: Int): Int {
        return prefs.getInt("frame_$widgetId", 0)
    }

    fun setCurrentFrame(widgetId: Int, frame: Int) {
        prefs.edit().putInt("frame_$widgetId", frame).apply()
    }

    private fun getNextDisplayId(): Int {
        val allWidgets = getAllWidgets()
        val usedIds = allWidgets.map { it.displayId }.toSet()
        
        var nextId = 1
        while (usedIds.contains(nextId)) {
            nextId++
        }
        return nextId
    }

    fun removeWidget(widgetId: Int) {
        prefs.edit()
            .remove("widget_$widgetId")
            .remove("animate_$widgetId")
            .remove("display_$widgetId")
            .remove("frame_$widgetId")
            .remove("frame_count_$widgetId")
            .remove("name_$widgetId")
            .apply()
    }
    
    fun getFrameCount(widgetId: Int): Int {
        return prefs.getInt("frame_count_$widgetId", 0)
    }
    
    fun setFrameCount(widgetId: Int, count: Int) {
        prefs.edit().putInt("frame_count_$widgetId", count).apply()
    }

    fun getAllWidgets(): List<WidgetData> {
        val widgets = mutableListOf<WidgetData>()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("widget_") && value is String) {
                val widgetId = key.removePrefix("widget_").toIntOrNull()
                if (widgetId != null) {
                    val displayId = getWidgetDisplayId(widgetId)
                    widgets.add(WidgetData(widgetId, value, displayId))
                }
            }
        }
        return widgets.sortedBy { it.displayId }
    }
}
