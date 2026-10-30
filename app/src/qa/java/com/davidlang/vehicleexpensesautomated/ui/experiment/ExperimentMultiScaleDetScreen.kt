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

private const val TAG = "MultiScaleDetUI"

/**
 * Dash ML Kit plus product_det / rot-G-- at the runner long-edges.
 * Jobs run on [ExperimentJobRunner] + FGS so leaving the screen does not cancel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperimentMultiScaleDetScreen(
    navController: NavHostController,
    autoStart: Boolean = false,
    /** Deep link auto=selected — dash coverage sample. */
    autoSelectedSample: Boolean = false,
) {
    val context = LocalContext.current
    val jobState by ExperimentJobRunner.state.collectAsState()

    val photos = remember { MultiScaleDetRunner.collectPhotos(context) }
    val counts = remember(photos) { MultiScaleDetRunner.countByDomain(photos) }
    val arch = remember { MultiScaleDetRunner.modelArchForDevice() }
    val models = remember { MultiScaleDetRunner.availableModels(context, arch) }
    val selectedOnDevice = remember(photos) {
        val want = SelectedSamplePhotos.DASH.toSet()
        photos.count { it.displayName in want || it.file.name in want }
    }

    val running = jobState.active && jobState.kind == "multi_scale_det"
    val status = when {
        running -> jobState.status
        jobState.kind == "multi_scale_det" && jobState.status == "done" ->
            "Done → ${jobState.resultPath}"
        jobState.kind == "multi_scale_det" && jobState.status == "failed" ->
            "FAILED: ${jobState.error}"
        else -> "Ready — dash ML Kit + paddle long-edge det/rec (FGS background-safe)"
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
            allowNames = SelectedSamplePhotos.DASH,
            allowDomains = setOf("dash"),
        )
    }

    // auto=1 is the first-5 dash smoke. auto=selected is the dash sample.
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
        Text(
            "Dash ML Kit + paddle long-edge det/rec",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(status, style = MaterialTheme.typography.bodyMedium)
        Text(
            "arch=$arch · product_det (${models.joinToString().ifEmpty { "missing" }})\n" +
                "Long-edges=${MultiScaleDetRunner.OUTER_SCALES} · single feed · " +
                "rows=${MultiScaleDetRunner.effectiveRowCount()} · " +
                "rot-G-- vert 0.34 L/R 0.54/0.55 · no expand · no seed merge · " +
                "FGS keeps the job alive if you leave this screen.",
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            "Dash photos: ${counts["dash"] ?: photos.size}. " +
                "Full run is every dash photo. Pump and expense are not collected.",
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
                "Run all dash photos (${photos.size})",
                fontWeight = FontWeight.Bold,
            )
        }
        Button(
            onClick = { startJob(5) },
            enabled = !ExperimentJobRunner.isRunning() && photos.isNotEmpty() && models.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Smoke: first 5 dash photos")
        }
        Button(
            onClick = { startSelectedSample() },
            enabled = !ExperimentJobRunner.isRunning() && selectedOnDevice > 0 && models.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "Selected dash sample (${SelectedSamplePhotos.DASH.size}; on device $selectedOnDevice)",
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            "Selected sample = ${SelectedSamplePhotos.DASH.size} dash names. " +
                "Deep link: vehicleexpenses://experiment/multiscale_det " +
                "(auto=1 smoke, auto=selected dash sample).",
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "One ML Kit pass on the raw photo and one on the deskewed copy. " +
                "Each long-edge is one product_det square feed plus rot-G-- recognition " +
                "on both grays. Reports: …/multi_scale_det_reports/.",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
