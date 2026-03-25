package com.davidlang.vehicleexpensesautomated

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.davidlang.vehicleexpensesautomated.ui.VehicleExpensesApp
import dagger.hilt.android.AndroidEntryPoint
import android.widget.Toast

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Camera permission denied. Photo features will be disabled.", Toast.LENGTH_LONG).show()
            // Continue anyway — UI will grey out photo options
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request CAMERA permission on launch (storage is handled by PhotoStorageManager)
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    VehicleExpensesApp()
                }
            }
        }
    }
}
