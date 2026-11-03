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
import androidx.compose.ui.res.stringResource
import com.davidlang.vehicleexpensesautomated.R

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
            stringResource(R.string.experiment_done_1_s, jobState.resultPath)
        jobState.kind == "multi_scale_det" && jobState.status == "failed" ->
            stringResource(R.string.experiment_failed_1_s, jobState.error)
        else -> stringResource(R.string.experiment_ready_dash_ml_kit_paddle_long_edge_det_rec)
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
            stringResource(R.string.experiment_dash_ml_kit_paddle_long_edge_det_rec),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(status, style = MaterialTheme.typography.bodyMedium)
        Text(
            "arch=$arch · product_det (${models.joinToString().ifEmpty { "missing" }})\n" +
                stringResource(R.string.experiment_long_edges_1_s_single_feed, MultiScaleDetRunner.OUTER_SCALES) +
                "rows=${MultiScaleDetRunner.effectiveRowCount()} · " +
                stringResource(R.string.experiment_rot_g_vert_0_34_l_r_0_54_0_55_no_expand_no) +
                stringResource(R.string.experiment_fgs_keeps_the_job_alive_if_you_leave_this),
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            stringResource(R.string.experiment_dash_photos_1_s, counts["dash"] ?: photos.size) +
                stringResource(R.string.experiment_full_run_is_every_dash_photo_pump_and_expe),
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
                stringResource(R.string.experiment_run_all_dash_photos_1_s, photos.size),
                fontWeight = FontWeight.Bold,
            )
        }
        Button(
            onClick = { startJob(5) },
            enabled = !ExperimentJobRunner.isRunning() && photos.isNotEmpty() && models.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.experiment_smoke_first_5_dash_photos))
        }
        Button(
            onClick = { startSelectedSample() },
            enabled = !ExperimentJobRunner.isRunning() && selectedOnDevice > 0 && models.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(R.string.experiment_selected_dash_sample_1_s_on_device_2_s, SelectedSamplePhotos.DASH.size, selectedOnDevice),
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            stringResource(R.string.experiment_selected_sample_1_s_dash_names, SelectedSamplePhotos.DASH.size) +
                stringResource(R.string.experiment_deep_link_vehicleexpenses_experiment_multi) +
                "(auto=1 smoke, auto=selected dash sample).",
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.experiment_one_ml_kit_pass_on_the_raw_photo_and_one_o) +
                stringResource(R.string.experiment_each_long_edge_is_one_product_det_square_f) +
                "on both grays. Reports: …/multi_scale_det_reports/.",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
