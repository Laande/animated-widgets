package com.animatedwidgets

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var widgetPrefs: WidgetPreferences
    private var pendingWidgetId: Int? = null
    private var isEditingWidget = false

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleImageSelected(it) }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            imagePickerLauncher.launch("image/*")
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        widgetPrefs = WidgetPreferences(this)
        
        recyclerView = findViewById(R.id.recycler_widgets)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        findViewById<Button>(R.id.btn_add_widget).setOnClickListener {
            requestAddWidget()
        }

        loadWidgets()
    }

    override fun onResume() {
        super.onResume()
        loadWidgets()
    }



    private fun requestAddWidget() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val myProvider = ComponentName(this, ImageWidgetProvider::class.java)

        if (appWidgetManager.isRequestPinAppWidgetSupported) {
            isEditingWidget = false
            appWidgetManager.requestPinAppWidget(myProvider, null, null)
            
            recyclerView.postDelayed({
                val widgetIds = appWidgetManager.getAppWidgetIds(myProvider)
                val newWidgetId = widgetIds.maxOrNull()
                if (newWidgetId != null && widgetPrefs.getWidgetImage(newWidgetId) == null) {
                    pendingWidgetId = newWidgetId
                    selectImage()
                }
            }, 2000)
        } else {
            Toast.makeText(this, "Widget pinning not supported", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == 100 && resultCode == RESULT_OK) {
            recyclerView.postDelayed({
                val appWidgetManager = AppWidgetManager.getInstance(this)
                val myProvider = ComponentName(this, ImageWidgetProvider::class.java)
                val widgetIds = appWidgetManager.getAppWidgetIds(myProvider)
                
                val newWidgetId = widgetIds.maxOrNull()
                if (newWidgetId != null && widgetPrefs.getWidgetImage(newWidgetId) == null) {
                    pendingWidgetId = newWidgetId
                    selectImage()
                }
            }, 500)
        }
    }

    private fun selectImage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                imagePickerLauncher.launch("image/*")
            }
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            imagePickerLauncher.launch("image/*")
        }
    }

    private fun handleImageSelected(uri: Uri) {
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        val widgetId = pendingWidgetId
        if (widgetId != null) {
            widgetPrefs.saveWidgetImage(widgetId, uri.toString())
            widgetPrefs.saveWidgetAnimateGif(widgetId, true)
            
            val appWidgetManager = AppWidgetManager.getInstance(this)
            ImageWidgetProvider.updateWidget(this, appWidgetManager, widgetId, uri.toString(), true)
            
            val intent = Intent(this, GifAnimationService::class.java)
            stopService(intent)
            startService(intent)
            
            val message = if (isEditingWidget) "Widget updated!" else "Widget created!"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            loadWidgets()
        }
        
        pendingWidgetId = null
        isEditingWidget = false
    }

    private fun loadWidgets() {
        val widgets = widgetPrefs.getAllWidgets()
        recyclerView.adapter = WidgetAdapter(
            widgets,
            onEditClick = { widget ->
                isEditingWidget = true
                pendingWidgetId = widget.widgetId
                selectImage()
            },
            onToggleAnimateClick = { widget ->
                val currentAnimate = widgetPrefs.getWidgetAnimateGif(widget.widgetId)
                val newAnimate = !currentAnimate
                widgetPrefs.saveWidgetAnimateGif(widget.widgetId, newAnimate)
                
                val appWidgetManager = AppWidgetManager.getInstance(this)
                
                if (!newAnimate) {
                    ImageWidgetProvider.updateWidget(this, appWidgetManager, widget.widgetId, widget.imageUri, false)
                } else {
                    val intent = Intent(this, GifAnimationService::class.java)
                    stopService(intent)
                    startService(intent)
                }
                
                val message = if (newAnimate) "Animation enabled" else "Animation disabled"
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                loadWidgets()
            },
            onDeleteClick = { widget ->
                AlertDialog.Builder(this)
                    .setTitle("Delete Widget")
                    .setMessage("Remove this widget from the list? (Widget will remain on home screen until manually removed)")
                    .setPositiveButton("Yes") { _, _ ->
                        widgetPrefs.removeWidget(widget.widgetId)
                        Toast.makeText(this, "Widget removed", Toast.LENGTH_SHORT).show()
                        loadWidgets()
                    }
                    .setNegativeButton("No", null)
                    .show()
            }
        )
    }
}
