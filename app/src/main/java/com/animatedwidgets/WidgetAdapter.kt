package com.animatedwidgets

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class WidgetAdapter(
    private val widgets: List<WidgetData>,
    private val onSettingsClick: (WidgetData) -> Unit
) : RecyclerView.Adapter<WidgetAdapter.WidgetViewHolder>() {

    class WidgetViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgPreview: ImageView = view.findViewById(R.id.img_preview)
        val txtWidgetId: TextView = view.findViewById(R.id.txt_widget_id)
        val btnSettings: ImageButton = view.findViewById(R.id.btn_settings)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WidgetViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_widget, parent, false)
        return WidgetViewHolder(view)
    }

    override fun onBindViewHolder(holder: WidgetViewHolder, position: Int) {
        val widget = widgets[position]
        val prefs = WidgetPreferences(holder.itemView.context)
        
        holder.txtWidgetId.text = prefs.getWidgetName(widget.widgetId) ?: "Widget #${widget.displayId}"
        
        Glide.with(holder.imgPreview.context)
            .load(Uri.parse(widget.imageUri))
            .centerCrop()
            .into(holder.imgPreview)

        holder.btnSettings.setOnClickListener {
            onSettingsClick(widget)
        }
    }
    
    override fun getItemCount() = widgets.size
}
