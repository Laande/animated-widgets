package com.animatedwidgets

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
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
    
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val switchContinuous = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_continuous_mode)
        if (!isGranted) {
            switchContinuous.isChecked = false
            widgetPrefs.setContinuousMode(false)
            Toast.makeText(this, "Continuous mode disabled - notification permission denied", Toast.LENGTH_LONG).show()
        } else {
            switchContinuous.isChecked = true
            widgetPrefs.setContinuousMode(true)
            restartService()
            Toast.makeText(this, "Continuous mode enabled", Toast.LENGTH_SHORT).show()
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

        setupContinuousModeSwitch()
        requestInitialPermission()
        loadWidgets()
    }
    
    private fun requestInitialPermission() {
        if (widgetPrefs.isContinuousModeEnabled()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            requestBatteryOptimizationExemption()
        }
    }
    
    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                AlertDialog.Builder(this)
                    .setTitle("Battery Optimization")
                    .setMessage("For continuous GIF animations, please disable battery optimization for this app.")
                    .setPositiveButton("Open Settings") { _, _ ->
                        try {
                            val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            intent.data = Uri.parse("package:$packageName")
                            startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            startActivity(intent)
                        }
                    }
                    .setNegativeButton("Skip", null)
                    .show()
            }
        }
    }
    
    private fun setupContinuousModeSwitch() {
        val switchContinuous = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_continuous_mode)
        switchContinuous.isChecked = widgetPrefs.isContinuousModeEnabled()
        
        switchContinuous.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        return@setOnCheckedChangeListener
                    }
                }
            }
            
            widgetPrefs.setContinuousMode(isChecked)
            restartService()
            
            val message = if (isChecked) "Continuous mode enabled" else "Continuous mode disabled"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun restartService() {
        val intent = Intent(this, GifAnimationService::class.java)
        stopService(intent)
        
        if (widgetPrefs.getAllWidgets().any { widgetPrefs.getWidgetAnimateGif(it.widgetId) }) {
            startService(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        val switchContinuous = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_continuous_mode)
        switchContinuous.isChecked = widgetPrefs.isContinuousModeEnabled()
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
            
            val mimeType = contentResolver.getType(uri)
            val isGif = mimeType?.contains("gif") == true || uri.toString().lowercase().contains(".gif")
            widgetPrefs.saveWidgetAnimateGif(widgetId, isGif)
            
            val appWidgetManager = AppWidgetManager.getInstance(this)
            ImageWidgetProvider.updateWidget(this, appWidgetManager, widgetId, uri.toString(), true)
            
            if (isGif) {
                val intent = Intent(this, GifAnimationService::class.java)
                stopService(intent)
                startService(intent)
            }
            
            val message = if (isEditingWidget) "Widget updated!" else "Widget created!"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            loadWidgets()
        }
        
        pendingWidgetId = null
        isEditingWidget = false
    }

    private fun loadWidgets() {
        val widgets = widgetPrefs.getAllWidgets()
        recyclerView.adapter = WidgetAdapter(widgets) { widget ->
            showWidgetSettingsDialog(widget)
        }
    }
    
    private fun showWidgetSettingsDialog(widget: WidgetData) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_widget_settings, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        val btnRename = dialogView.findViewById<Button>(R.id.btn_rename)
        val btnChangeImage = dialogView.findViewById<Button>(R.id.btn_change_image)
        val frameAnimate = dialogView.findViewById<android.widget.FrameLayout>(R.id.frame_animate)
        val switchAnimate = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_animate)
        val btnDelete = dialogView.findViewById<Button>(R.id.btn_delete)
        val btnClose = dialogView.findViewById<Button>(R.id.btn_close)
        
        val mimeType = contentResolver.getType(Uri.parse(widget.imageUri))
        val isGif = mimeType?.contains("gif") == true || widget.imageUri.lowercase().contains(".gif")
        
        if (isGif) {
            frameAnimate.visibility = android.view.View.VISIBLE
            val isAnimating = widgetPrefs.getWidgetAnimateGif(widget.widgetId)
            switchAnimate.isChecked = isAnimating
        } else {
            frameAnimate.visibility = android.view.View.GONE
        }
        
        btnRename.setOnClickListener {
            dialog.dismiss()
            showRenameDialog(widget)
        }
        
        btnChangeImage.setOnClickListener {
            dialog.dismiss()
            isEditingWidget = true
            pendingWidgetId = widget.widgetId
            selectImage()
        }
        
        switchAnimate.setOnCheckedChangeListener { _, isChecked ->
            widgetPrefs.saveWidgetAnimateGif(widget.widgetId, isChecked)
            
            if (isChecked) {
                val intent = Intent(this, GifAnimationService::class.java)
                try {
                    startService(intent)
                } catch (e: Exception) {
                }
            } else {
                showFirstFrame(widget.widgetId, widget.imageUri)
                
                val hasOtherAnimatingGifs = widgetPrefs.getAllWidgets().any { 
                    it.widgetId != widget.widgetId && widgetPrefs.getWidgetAnimateGif(it.widgetId) 
                }
                if (!hasOtherAnimatingGifs) {
                    try {
                        stopService(Intent(this, GifAnimationService::class.java))
                    } catch (e: Exception) {
                    }
                }
            }
            
            Toast.makeText(this, if (isChecked) "Animation enabled" else "Animation disabled", Toast.LENGTH_SHORT).show()
            loadWidgets()
        }
        
        btnDelete.setOnClickListener {
            dialog.dismiss()
            AlertDialog.Builder(this)
                .setTitle("Delete Widget")
                .setMessage("Remove this widget?")
                .setPositiveButton("Yes") { _, _ ->
                    widgetPrefs.removeWidget(widget.widgetId)
                    Toast.makeText(this, "Widget removed", Toast.LENGTH_SHORT).show()
                    loadWidgets()
                }
                .setNegativeButton("No", null)
                .show()
        }
        
        btnClose.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun showRenameDialog(widget: WidgetData) {
        val currentName = widgetPrefs.getWidgetName(widget.widgetId) ?: "Widget #${widget.displayId}"
        val input = android.widget.EditText(this)
        input.setText(currentName)
        input.setTextColor(android.graphics.Color.WHITE)
        
        AlertDialog.Builder(this)
            .setTitle("Rename Widget")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val newName = input.text.toString()
                if (newName.isNotBlank()) {
                    widgetPrefs.saveWidgetName(widget.widgetId, newName)
                    loadWidgets()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showFirstFrame(widgetId: Int, imageUri: String) {
        Thread {
            try {
                val uri = Uri.parse(imageUri)
                val bitmap = com.bumptech.glide.Glide.with(applicationContext)
                    .asBitmap()
                    .load(uri)
                    .submit()
                    .get()
                
                runOnUiThread {
                    try {
                        val appWidgetManager = AppWidgetManager.getInstance(this)
                        val views = android.widget.RemoteViews(packageName, R.layout.widget_layout)
                        views.setImageViewBitmap(R.id.widget_image, bitmap)
                        appWidgetManager.updateAppWidget(widgetId, views)
                    } catch (e: Exception) {
                    }
                }
            } catch (e: Exception) {
            }
        }.start()
    }
}
