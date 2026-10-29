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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.data.repository.VehicleRepository
import com.davidlang.vehicleexpensesautomated.ui.util.HeatmapStageDump
import com.davidlang.vehicleexpensesautomated.ui.util.PreprocessStageDump
import java.io.File

private const val TAG = "HeatmapStage"

private val HEATMAP_KINDS = setOf("heatmap_stage", "preprocess_stage", "deskew_odo")

/**
 * Fast heatmap-only matrix: fp16 and/or fp32 packs, no deskew/OCR.
 *
 * Long jobs run on [ExperimentJobRunner] + [ExperimentForegroundService] so leaving
 * the screen / backgrounding does **not** cancel (unlike rememberCoroutineScope).
 *
 * logcat: adb logcat -s HeatmapStage:I ExperimentJobRunner:I PreprocessStage:I
 *
 * Deep links: vehicleexpenses://experiment/preprocess?auto=triage
 *             vehicleexpenses://experiment/heatmap?auto=heatbins
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperimentHeatmapStageScreen(
    navController: NavHostController,
    vehicleRepository: VehicleRepository? = null,
) {
    val context = LocalContext.current
    val jobState by ExperimentJobRunner.state.collectAsState()
    var autoStarted by remember { mutableStateOf(false) }

    val ours = jobState.kind in HEATMAP_KINDS
    val running = jobState.active && ours

    val status = when {
        running -> jobState.status
        ours && jobState.status == "done" ->
            "Done → ${jobState.resultPath.ifEmpty { jobState.status }}"
        ours && jobState.status == "failed" ->
            "FAILED: ${jobState.error}"
        ExperimentJobRunner.isRunning() ->
            "Busy: other experiment (${jobState.kind}) — wait or finish that first"
        else ->
            "Ready — heatmap stage (FGS background-safe; writeBins on full runs)"
    }

    fun startHeatmap(modes: List<String>, allowNames: List<String>? = null): Boolean {
        val ok = ExperimentJobRunner.start(context, kind = "heatmap_stage") { progress, log, statusLine ->
            statusLine("running heatmap_stage")
            val outs = mutableListOf<String>()
            for (mode in modes) {
                val dir = when (mode) {
                    "fp32" -> "prod_u8fp32_u8"
                    else -> "prod_u8fp16"
                }
                log("RUN_PACK $dir writeBins=true (feeds + heat f32/u8z)")
                val res = HeatmapStageDump.run(
                    context = context,
                    forceProdDir = dir,
                    writeBins = true,
                    allowNames = allowNames,
                    onLog = log,
                    onProgress = { done, total, name ->
                        progress(done, total, "$mode $name")
                    },
                )
                log(res.message)
                log("OUT ${res.outDir.absolutePath}")
                outs.add(res.outDir.absolutePath)
            }
            statusLine("done")
            outs.joinToString(" | ")
        }
        if (!ok) Log.w(TAG, "could not start heatmap — another experiment job is running")
        return ok
    }

    fun startPreprocess(allowNames: List<String>? = null): Boolean {
        val ok = ExperimentJobRunner.start(context, kind = "preprocess_stage") { progress, log, statusLine ->
            statusLine("running preprocess_stage")
            log("PREPROCESS start allow=${allowNames?.size ?: "default_triage"}")
            val res = if (allowNames != null) {
                PreprocessStageDump.run(
                    context = context,
                    allowNames = allowNames,
                    onLog = log,
                    onProgress = progress,
                )
            } else {
                PreprocessStageDump.run(
                    context = context,
                    onLog = log,
                    onProgress = progress,
                )
            }
            log(res.message)
            log("OUT ${res.outDir.absolutePath}")
            statusLine("done")
            res.outDir.absolutePath
        }
        if (!ok) Log.w(TAG, "could not start preprocess — another experiment job is running")
        return ok
    }

    fun startOdoExport(allowNames: Collection<String>? = null): Boolean {
        if (vehicleRepository == null) {
            Log.e(TAG, "VehicleRepository not injected")
            return false
        }
        val repo = vehicleRepository
        val ok = ExperimentJobRunner.start(context, kind = "deskew_odo") { progress, log, statusLine ->
            statusLine("running deskew_odo")
            log("DESKEW_ODO_CROP export start allow=${allowNames?.size ?: "all"}")
            val res = DeskewOdoCropExport.run(
                context = context,
                vehicleRepository = repo,
                allowNames = allowNames,
                onLog = log,
                onProgress = progress,
            )
            log(res.message)
            statusLine("done ok=${res.nOk} fail=${res.nFail}")
            "ok=${res.nOk} fail=${res.nFail}"
        }
        if (!ok) Log.w(TAG, "could not start odo export — another experiment job is running")
        return ok
    }

    // adb deep links (vehicleexpenses://experiment/…):
    //   preprocess?auto=triage  — preprocess dumps only
    //   heatmap?auto=heatbins   — det + write feed/heat bins (feed-matched allowlist)
    //   odo-export?auto=1 or deskew?auto=odo — dash odo crops after deskew+align
    LaunchedEffect(Unit) {
        if (autoStarted) return@LaunchedEffect
        val act = context as? android.app.Activity ?: return@LaunchedEffect
        val data = act.intent?.data ?: return@LaunchedEffect
        if (data.host != "experiment") return@LaunchedEffect
        val auto = data.getQueryParameter("auto").orEmpty()
        val path = data.path.orEmpty()
        val wantPrep = path.contains("preprocess") || auto == "triage" || auto == "preprocess"
        val wantHeatBins = path.contains("heatmap") &&
            (auto == "heatbins" || auto == "bins" || auto == "writebins")
        val wantHeatSelected = path.contains("heatmap") &&
            (auto == "selected" || auto == "selected_sample")
        val wantPrepSelected = (path.contains("preprocess") || auto == "triage") &&
            (auto == "selected" || auto == "selected_sample" || auto == "triage_selected")
        val wantOdo = path.contains("odo-export") || path.contains("deskew") ||
            auto == "odo" || auto == "odo-export" || auto == "deskew_odo"
        val wantOdoSelected = wantOdo && (auto == "selected" || auto == "selected_sample" ||
            auto == "odo_selected")
        if (!wantPrep && !wantHeatBins && !wantHeatSelected && !wantOdo && !wantPrepSelected) {
            return@LaunchedEffect
        }
        if (ExperimentJobRunner.isRunning()) {
            Log.w(TAG, "auto-start skipped — job already running")
            return@LaunchedEffect
        }
        autoStarted = true
        when {
            wantOdoSelected -> startOdoExport(SelectedSamplePhotos.DASH)
            wantOdo -> startOdoExport()
            wantPrepSelected -> startPreprocess(SelectedSamplePhotos.PUMP)
            wantPrep -> startPreprocess()
            wantHeatSelected ->
                startHeatmap(listOf("fp32", "fp16"), allowNames = SelectedSamplePhotos.PUMP)
            else -> {
                val allow = listOf(
                    "PXL_20221230_182006230.dng",
                    "PXL_20230414_023123861.dng",
                    "PXL_20230705_105304742.dng",
                    "PXL_20250703_032207597.jpg",
                    "fuel_1784243183762.jpg",
                    "fuel_1784570913514.jpg",
                )
                startHeatmap(listOf("fp32", "fp16"), allowNames = allow)
            }
        }
    }

    val photoDir = File(context.getExternalFilesDir(null), "pump_photos")
    val nPhotos = photoDir.listFiles { f ->
        f.isFile && f.extension.lowercase() in setOf("jpg", "jpeg", "png", "dng")
    }?.size ?: 0
    val idle = !ExperimentJobRunner.isRunning()

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(status, style = MaterialTheme.typography.bodyMedium)
        Text(
            "Photos in pump_photos: $nPhotos (uses whatever is on device). " +
                "Scales: 224, 608, 1024, 2048. This build: full-square 2048 " +
                "(useTiledLargeDet=false; det_mode=single) for A/B vs prior tiled gallery. " +
                "Jobs use ExperimentJobRunner + FGS — leaving this screen does not cancel. " +
                "No deskew rotate / no OCR. Gates: source_sha256 → feed_sha256 → heat_crc / hist_sha.",
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
        if (ours && jobState.detail.isNotEmpty()) {
            Text(
                jobState.detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                startHeatmap(listOf("fp16", "fp32"), allowNames = SelectedSamplePhotos.PUMP)
            },
            enabled = idle && nPhotos > 0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "Selected sample heatmap (${SelectedSamplePhotos.PUMP.size} pump, both packs)",
                fontWeight = FontWeight.Bold,
            )
        }
        Button(
            onClick = { startHeatmap(listOf("fp32")) },
            enabled = idle && nPhotos > 0,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Run fp32 heatmap stage (writeBins)") }
        Button(
            onClick = { startHeatmap(listOf("fp16")) },
            enabled = idle && nPhotos > 0,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Run fp16 heatmap stage (writeBins)") }
        Button(
            onClick = { startHeatmap(listOf("fp16", "fp32")) },
            enabled = idle && nPhotos > 0,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Run both packs fp16 then fp32 (writeBins)") }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { startPreprocess(SelectedSamplePhotos.PUMP) },
            enabled = idle && nPhotos > 0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "Selected sample preprocess (${SelectedSamplePhotos.PUMP.size} pump)",
                fontWeight = FontWeight.Bold,
            )
        }
        Button(
            onClick = { startPreprocess() },
            enabled = idle && nPhotos > 0,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Preprocess triage dump (no paddle)") }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (vehicleRepository == null) {
                    Log.e(TAG, "VehicleRepository not injected")
                } else {
                    startOdoExport(SelectedSamplePhotos.DASH)
                }
            },
            enabled = idle && vehicleRepository != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "Selected sample odo export (${SelectedSamplePhotos.DASH.size} dash)",
                fontWeight = FontWeight.Bold,
            )
        }
        Button(
            onClick = {
                if (vehicleRepository == null) {
                    Log.e(TAG, "VehicleRepository not injected")
                } else {
                    startOdoExport()
                }
            },
            enabled = idle && vehicleRepository != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Deskew odo crop export (all dash bins)") }
        Spacer(Modifier.height(8.dp))
        Text(
            "Selected sample = coverage subset (pump for heatmap/preprocess; dash for odo). " +
                "Deep link: …/heatmap?auto=selected · …/preprocess?auto=selected · " +
                "…/odo-export?auto=selected\n" +
                "Output: heatmap_stage/ preprocess_stage/ deskew_odo_export/\n" +
                "Push dash_bins_for_device.json to files/deskew_odo_export/\n" +
                "Push dash photos to files/dash_photos/\n" +
                "logcat: HeatmapStage ExperimentJobRunner PreprocessStage DeskewOdoCrop",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
