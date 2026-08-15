package com.davidlang.vehicleexpensesautomated.ui.experiment

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.ui.util.MultiScaleDetRunner

private const val TAG = "MultiScaleDetUI"

/**
 * Multi-scale × multi-det experiment UI.
 * Jobs run on [ExperimentJobRunner] + FGS so leaving the screen/background does not cancel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperimentMultiScaleDetScreen(
    navController: NavHostController,
    autoStart: Boolean = false,
    /** Deep link auto=selected — coverage pump+dash + expense seed. */
    autoSelectedSample: Boolean = false,
) {
    val context = LocalContext.current
    val jobState by ExperimentJobRunner.state.collectAsState()

    val photos = remember { MultiScaleDetRunner.collectPhotos(context) }
    val counts = remember(photos) { MultiScaleDetRunner.countByDomain(photos) }
    val arch = remember { MultiScaleDetRunner.modelArchForDevice() }
    val models = remember { MultiScaleDetRunner.availableModels(context, arch) }
    val selectedOnDevice = remember(photos) {
        val want = SelectedSamplePhotos.MULTI_SCALE.toSet()
        photos.count { it.displayName in want || it.file.name in want }
    }

    val running = jobState.active && jobState.kind == "multi_scale_det"
    val status = when {
        running -> jobState.status
        jobState.kind == "multi_scale_det" && jobState.status == "done" ->
            "Done → ${jobState.resultPath}"
        jobState.kind == "multi_scale_det" && jobState.status == "failed" ->
            "FAILED: ${jobState.error}"
        else -> "Ready — multi-scale × multi-det + expand P (FGS background-safe)"
    }

    fun startJob(
        maxPhotos: Int?,
        allowNames: Collection<String>? = null,
        allowDomains: Set<String>? = null,
    ) {
        val ok = ExperimentJobRunner.start(context, kind = "multi_scale_det") { progress, log, statusLine ->
            statusLine("running multi_scale_det")
            val res = MultiScaleDetRunner.run(
                context = context,
                maxPhotos = maxPhotos,
                allowNames = allowNames,
                allowDomains = allowDomains,
                onLog = log,
                onProgress = progress,
            )
            statusLine("done")
            res.jsonFile.absolutePath
        }
        if (!ok) {
            Log.w(TAG, "could not start — another experiment job is running")
        }
    }

    fun startSelectedSample() {
        startJob(
            maxPhotos = null,
            allowNames = SelectedSamplePhotos.MULTI_SCALE,
            allowDomains = setOf("pump", "dash", "expense"),
        )
    }

    // Auto-start only if idle. Deep-link auto uses 5-photo smoke (full run is huge).
    androidx.compose.runtime.LaunchedEffect(autoStart, autoSelectedSample) {
        if (ExperimentJobRunner.isRunning()) return@LaunchedEffect
        when {
            autoSelectedSample -> startSelectedSample()
            autoStart -> startJob(5)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(status, style = MaterialTheme.typography.bodyMedium)
        Text(
            "arch=$arch · models (${models.size}): ${models.joinToString()}\n" +
                "Outers=${MultiScaleDetRunner.OUTER_SCALES} · strategies=single/square/hspan/vspan · " +
                "rows=${MultiScaleDetRunner.effectiveRowCount()} · expand=P · threads=4 · " +
                "deskew ang≥1/≥2 from u8 heat · FGS keeps the job alive if you leave this screen.",
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            "Photos: ${photos.size} " +
                "(pump=${counts["pump"] ?: 0}, dash=${counts["dash"] ?: 0}, " +
                "expense=${counts["expense"] ?: 0}). Existing pump+dash; expense seeded from APK.",
            style = MaterialTheme.typography.labelSmall,
        )
        if (running) {
            Spacer(Modifier.height(8.dp))
            Text(jobState.current, style = MaterialTheme.typography.labelMedium)
            LinearProgressIndicator(
                progress = { jobState.progress },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (jobState.detail.isNotEmpty() && jobState.kind == "multi_scale_det") {
            Text(
                jobState.detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { startJob(null) },
            enabled = !ExperimentJobRunner.isRunning() && photos.isNotEmpty() && models.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "Run full multi-scale × ${models.size} models (${photos.size} photos)",
                fontWeight = FontWeight.Bold,
            )
        }
        Button(
            onClick = { startJob(5) },
            enabled = !ExperimentJobRunner.isRunning() && photos.isNotEmpty() && models.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Smoke: first 5 photos only")
        }
        Button(
            onClick = { startSelectedSample() },
            enabled = !ExperimentJobRunner.isRunning() && selectedOnDevice > 0 && models.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "Selected sample (pump+dash+expense ${SelectedSamplePhotos.MULTI_SCALE.size}; " +
                    "on device $selectedOnDevice)",
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            "Selected sample = coverage pump+dash + seeded expense " +
                "(${SelectedSamplePhotos.EXPENSE.joinToString()}). " +
                "Deep link: vehicleexpenses://experiment/multiscale_det?auto=selected",
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Models: product + v4/v5 mobile only (no server; Lite max 4096). " +
                "Expense domain uses box cap 2000 (else 200). " +
                "Reports: …/multi_scale_det_reports/ flat multi_scale_det_*_<ts>.*; tray cells/ only.",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
