package com.animatedwidgets

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class WidgetAdapter(
    private val widgets: List<WidgetData>,
    private val onEditClick: (WidgetData) -> Unit,
    private val onToggleAnimateClick: (WidgetData) -> Unit,
    private val onDeleteClick: (WidgetData) -> Unit
) : RecyclerView.Adapter<WidgetAdapter.WidgetViewHolder>() {

    class WidgetViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgPreview: ImageView = view.findViewById(R.id.img_preview)
        val txtWidgetId: TextView = view.findViewById(R.id.txt_widget_id)
        val btnEdit: ImageButton = view.findViewById(R.id.btn_edit)
        val btnToggleAnimate: ImageButton = view.findViewById(R.id.btn_toggle_animate)
        val btnDelete: ImageButton = view.findViewById(R.id.btn_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WidgetViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_widget, parent, false)
        return WidgetViewHolder(view)
    }

    override fun onBindViewHolder(holder: WidgetViewHolder, position: Int) {
        val widget = widgets[position]
        val prefs = WidgetPreferences(holder.itemView.context)
        val isAnimating = prefs.getWidgetAnimateGif(widget.widgetId)
        val context = holder.itemView.context
        
        holder.txtWidgetId.text = prefs.getWidgetName(widget.widgetId) ?: "Widget #${widget.displayId}"
        
        Glide.with(holder.imgPreview.context)
            .load(Uri.parse(widget.imageUri))
            .centerCrop()
            .into(holder.imgPreview)

        val uri = Uri.parse(widget.imageUri)
        val mimeType = context.contentResolver.getType(uri)
        val isGif = mimeType?.contains("gif") == true || widget.imageUri.lowercase().contains(".gif")
        
        if (isGif) {
            holder.btnToggleAnimate.visibility = View.VISIBLE
            holder.btnToggleAnimate.setImageResource(
                if (isAnimating) android.R.drawable.ic_media_pause 
                else android.R.drawable.ic_media_play
            )
            holder.btnToggleAnimate.setOnClickListener { onToggleAnimateClick(widget) }
        } else {
            holder.btnToggleAnimate.visibility = View.GONE
        }

        holder.btnEdit.setOnClickListener { view ->
            val popup = android.widget.PopupMenu(context, view)
            popup.menu.add(0, 1, 0, "Rename")
            popup.menu.add(0, 2, 1, "Change Image")
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> { showRenameDialog(context, widget); true }
                    2 -> { onEditClick(widget); true }
                    else -> false
                }
            }
            popup.show()
        }
        
        holder.btnDelete.setOnClickListener { onDeleteClick(widget) }
    }
    
    private fun showRenameDialog(context: android.content.Context, widget: WidgetData) {
        val prefs = WidgetPreferences(context)
        val currentName = prefs.getWidgetName(widget.widgetId) ?: "Widget #${widget.displayId}"
        
        val input = android.widget.EditText(context)
        input.setText(currentName)
        input.setTextColor(android.graphics.Color.WHITE)
        
        AlertDialog.Builder(context)
            .setTitle("Rename Widget")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val newName = input.text.toString()
                if (newName.isNotBlank()) {
                    prefs.saveWidgetName(widget.widgetId, newName)
                    notifyDataSetChanged()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun getItemCount() = widgets.size
}
