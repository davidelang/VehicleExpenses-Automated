package com.davidlang.vehicleexpensesautomated.ui.experiment

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import java.io.File

@Composable
fun ExperimentAlignmentScreen(navController: NavHostController? = null) {
    val context = LocalContext.current
    val experimentDir = File(context.filesDir, "experiment_photos")

    var status by remember { mutableStateOf("Checking experiment folder...") }

    LaunchedEffect(Unit) {
        if (!experimentDir.exists()) {
            experimentDir.mkdirs()
            status = "✅ Created experiment_photos folder.\n\nDownload test photos from your Amazon Photos album into this folder."
        } else if (experimentDir.listFiles()?.isEmpty() == true) {
            status = "⚠️ Folder is empty.\n\nPlease add photos from Amazon Photos album."
        } else {
            status = "✅ Found ${experimentDir.listFiles()?.size ?: 0} photos ready for alignment testing."
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Alignment Experiment") })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = status,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp)
            )

            Button(
                onClick = {
                    Toast.makeText(
                        context,
                        "Alignment experiment started (stub - full runExperiment will be added next)",
                        Toast.LENGTH_LONG
                    ).show()
                    // TODO: call runExperiment(...) once vehicles + full logic are wired in
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🚀 Run Alignment Experiment Now")
            }

            Button(
                onClick = { navController?.popBackStack() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Back to Quick Fill-up")
            }
        }
    }
}
