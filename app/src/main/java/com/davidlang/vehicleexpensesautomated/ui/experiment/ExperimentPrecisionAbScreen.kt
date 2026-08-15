package com.davidlang.vehicleexpensesautomated.ui.experiment

import android.os.Build
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.baidu.paddle.lite.MobileConfig
import com.baidu.paddle.lite.PaddlePredictor
import com.davidlang.vehicleexpensesautomated.BuildConfig
import com.davidlang.vehicleexpensesautomated.ui.util.NativePaddleEngine
import com.davidlang.vehicleexpensesautomated.ui.vehicle.VehicleViewModel
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "PrecisionAb"

/**
 * Overnight-friendly suite: full alignment + full pump for one or more product packs.
 *
 * Log markers (filter: `adb logcat -s PrecisionAb:I`):
 * - SUITE_START / PHASE_START / PHASE_PROGRESS / PHASE_DONE / PHASE_FAIL / SUITE_DONE
 *
 * **Do not auto-start heavy model loads on compose** — arm64 product SO is FP16-tailored;
 * loading uint8→fp32 graphs without float kernels aborts the process (SIGABRT in light SO).
 * Preflight loads det once before the suite; failures stay in UI/log without crashing when
 * Java can catch them (native abort still requires a dual-kernel SO).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperimentPrecisionAbScreen(
    navController: NavHostController,
    /** auto=fp32 | auto=both — only after explicit user open; prefers not to crash-on-navigate */
    autoMode: String = "",
) {
    val context = LocalContext.current
    val vehicleViewModel: VehicleViewModel = hiltViewModel()
    val vehicles by vehicleViewModel.vehicles.collectAsState()
    val jobState by ExperimentJobRunner.state.collectAsState()

    var status by remember { mutableStateOf("Ready — pick a suite (does not auto-run on open)") }
    var detailLog by remember { mutableStateOf("") }
    var progress by remember { mutableFloatStateOf(0f) }
    var currentPhotoName by remember { mutableStateOf("") }
    var phaseLabel by remember { mutableStateOf("") }
    var autoStarted by remember { mutableStateOf(false) }
    val isRunning = jobState.active && jobState.kind == "precision_ab"
    /** Status line: green-ish primary when dual-kernel ready; error only for real blockers. */
    var bannerNote by remember { mutableStateOf("") }
    var bannerIsError by remember { mutableStateOf(false) }

    val extRoot = context.getExternalFilesDir(null)
    val pumpPhotoDir = remember(extRoot) {
        File(extRoot ?: context.filesDir, "pump_photos").also { it.mkdirs() }
    }
    val pumpReportDir = remember(extRoot) {
        File(extRoot ?: context.filesDir, "pump_reports").also { it.mkdirs() }
    }
    val alignPhotoDir = remember {
        File(context.filesDir, "experiment_photos").also { it.mkdirs() }
    }
    val alignReportDir = remember {
        File(context.filesDir, "experiment_reports").also { it.mkdirs() }
    }

    // JobRunner.log is used inside start(); keep Compose detail via jobState.detail.

    /** Asset probe + optional det-only load (Java-visible failures only). */
    suspend fun preflightPack(arch: String, prodDir: String): String? = withContext(Dispatchers.IO) {
        val assetProbe = "paddle/$prodDir/det_$arch.nb"
        try {
            context.assets.open(assetProbe).close()
        } catch (_: Exception) {
            return@withContext "missing_asset=$assetProbe"
        }
        // Copy det and try create predictor alone — if SO lacks kernels this may still SIGABRT.
        val tmp = File(context.cacheDir, "preflight_${prodDir}_det_$arch.nb")
        try {
            context.assets.open(assetProbe).use { inp ->
                FileOutputStream(tmp).use { out -> inp.copyTo(out) }
            }
            val config = MobileConfig()
            config.setThreads(1)
            config.setModelFromFile(tmp.absolutePath)
            val p = PaddlePredictor.createPaddlePredictor(config)
                ?: return@withContext "createPaddlePredictor_null for $assetProbe"
            // force free
            try {
                val m = PaddlePredictor::class.java.getDeclaredMethod("clear")
                m.isAccessible = true
                m.invoke(p)
            } catch (_: Throwable) {
            }
            null
        } catch (t: Throwable) {
            "preflight_exception=${t.javaClass.simpleName}: ${t.message}"
        } finally {
            tmp.delete()
        }
    }

    /**
     * @param mode fp16 | fp32 | both
     * @param selectedSample if true, align uses [SelectedSamplePhotos.DASH] and pump uses
     *   [SelectedSamplePhotos.PUMP] only (no full corpus).
     */
    val runSuite: (mode: String, selectedSample: Boolean) -> Unit = { mode, selectedSample ->
        val vehicleSnap = vehicles.toList()
        val app = context.applicationContext
        progress = 0f
        currentPhotoName = ""
        detailLog = ""
        val ok = ExperimentJobRunner.start(app, kind = "precision_ab") block@{ progressCb, log, statusLine ->
            val arch = NativePaddleEngine.modelArchForPrimaryAbi()
            Log.i(
                TAG,
                "SUITE_START mode=$mode selectedSample=$selectedSample arch=$arch model=${Build.MODEL}",
            )
            log(
                "SUITE_START mode=$mode selectedSample=$selectedSample arch=$arch model=${Build.MODEL}",
            )

            val alignSubset: Map<String, Int>? =
                if (selectedSample) {
                    SelectedSamplePhotos.subsetMapPresent(alignPhotoDir, SelectedSamplePhotos.DASH)
                        .also {
                            log(
                                "ALIGN_SUBSET selected dash matched=${it.size}/" +
                                    "${SelectedSamplePhotos.DASH.size}",
                            )
                        }
                } else {
                    null
                }
            val pumpSubset: List<String>? =
                if (selectedSample) {
                    SelectedSamplePhotos.presentInOrder(pumpPhotoDir, SelectedSamplePhotos.PUMP)
                        .also {
                            log(
                                "PUMP_SUBSET selected pump matched=${it.size}/" +
                                    "${SelectedSamplePhotos.PUMP.size}",
                            )
                        }
                } else {
                    null
                }
            if (selectedSample) {
                if (alignSubset.isNullOrEmpty()) {
                    log("SUITE_FAIL selected sample: 0 dash photos in experiment_photos")
                    return@block "SUITE_FAIL no selected dash photos"
                }
                if (pumpSubset.isNullOrEmpty()) {
                    log("SUITE_FAIL selected sample: 0 pump photos in pump_photos")
                    return@block "SUITE_FAIL no selected pump photos"
                }
            }

            val packs: List<Pair<String, String>> = when (mode) {
                "both" -> listOf(
                    "prod_u8fp16" to NativePaddleEngine.PROD_PATH_ID,
                    "prod_u8fp32_u8" to NativePaddleEngine.PROD_PATH_ID_FP32,
                )
                "fp16" -> listOf("prod_u8fp16" to NativePaddleEngine.PROD_PATH_ID)
                else -> listOf("prod_u8fp32_u8" to NativePaddleEngine.PROD_PATH_ID_FP32)
            }

            var suiteOk = true
            try {
                for ((prodDir, pathId) in packs) {
                    val pf = preflightPack(arch, prodDir)
                    if (pf != null) {
                        suiteOk = false
                        log("SUITE_FAIL path=$pathId preflight=$pf")
                        if (pf.contains("missing_asset") && prodDir == "prod_u8fp32_u8") {
                            log(
                                "HINT: need paddle/prod_u8fp32_u8/det_$arch.nb in APK " +
                                    "(x86_64 models for emu; armv8 for phone)",
                            )
                        }
                        if (pf.contains("exception") || pf.contains("null")) {
                            log(
                                "HINT: arm64 product SO is often FP16-tailored only; " +
                                    "fp32 graphs need float kernels (union-tailor SO). " +
                                    "Loading without them can hard-crash (SIGABRT).",
                            )
                        }
                        break
                    }
                    log("PREFLIGHT_OK path=$pathId dir=$prodDir arch=$arch")

                    withContext(Dispatchers.IO) {
                        NativePaddleEngine.loadProductionModels(
                            app,
                            forceArch = arch,
                            forceProdDir = prodDir,
                        )
                    }
                    log("MODELS_LOADED path=$pathId dir=$prodDir arch=$arch")

                    // --- ALIGN ---
                    statusLine("align $pathId")
                    log("PHASE_START path=$pathId job=align")
                    if (vehicleSnap.isEmpty()) {
                        suiteOk = false
                        log("PHASE_FAIL path=$pathId job=align reason=no_vehicles")
                        break
                    }
                    val alignJson = try {
                        runAlignmentExperiment(
                            alignPhotoDir,
                            alignReportDir,
                            vehicleSnap,
                            app,
                            { msg -> if (msg.isNotBlank()) log("align_log: $msg") },
                            alignSubset,
                        ) { res, p ->
                            val done = (p * 1000).toInt().coerceIn(1, 1000)
                            progressCb(done, 1000, "align ${res.photoName}")
                            Log.i(
                                TAG,
                                "PHASE_PROGRESS path=$pathId job=align photo=${res.photoName} frac=$p",
                            )
                        }
                    } catch (t: CancellationException) {
                        throw t
                    } catch (t: Throwable) {
                        Log.e(TAG, "PHASE_FAIL path=$pathId job=align", t)
                        log("PHASE_FAIL path=$pathId job=align err=${t.message}")
                        suiteOk = false
                        null
                    }
                    if (alignJson == null) {
                        suiteOk = false
                        log("PHASE_FAIL path=$pathId job=align reason=null_report")
                        break
                    }
                    log("PHASE_DONE path=$pathId job=align json=${alignJson.absolutePath}")

                    // --- PUMP ---
                    statusLine("pump $pathId")
                    log("PHASE_START path=$pathId job=pump")
                    val pumpJson = try {
                        runPumpExperiment(
                            pumpPhotoDir,
                            pumpReportDir,
                            app,
                            { msg -> if (msg.isNotBlank()) log("pump_log: $msg") },
                            pumpSubset,
                        ) { res, p ->
                            val done = (p * 1000).toInt().coerceIn(1, 1000)
                            progressCb(done, 1000, "pump ${res.photoName}")
                            Log.i(
                                TAG,
                                "PHASE_PROGRESS path=$pathId job=pump photo=${res.photoName} frac=$p",
                            )
                        }
                    } catch (t: CancellationException) {
                        throw t
                    } catch (t: Throwable) {
                        Log.e(TAG, "PHASE_FAIL path=$pathId job=pump", t)
                        log("PHASE_FAIL path=$pathId job=pump err=${t.message}")
                        suiteOk = false
                        null
                    }
                    if (pumpJson == null) {
                        suiteOk = false
                        log("PHASE_FAIL path=$pathId job=pump reason=null_report")
                        break
                    }
                    log("PHASE_DONE path=$pathId job=pump json=${pumpJson.absolutePath}")
                }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                suiteOk = false
                Log.e(TAG, "SUITE_FAIL", t)
                log("SUITE_FAIL err=${t.message}")
            } finally {
                try {
                    NativePaddleEngine.loadProductionModels(app)
                    log(
                        "MODELS_RESTORED path=${NativePaddleEngine.activeProductPathId} " +
                            "dir=${NativePaddleEngine.activeProductDir}",
                    )
                } catch (t: Throwable) {
                    Log.e(TAG, "restore default models failed", t)
                    log("MODELS_RESTORE_FAIL err=${t.message}")
                }
                log("SUITE_DONE ok=$suiteOk")
                Log.i(TAG, "SUITE_DONE ok=$suiteOk")
            }
            if (suiteOk) "SUITE_DONE ok=true" else "SUITE_DONE ok=false"
        }
        if (!ok) {
            status = "Another experiment is already running (${ExperimentJobRunner.state.value.kind})"
        } else {
            status = "SUITE_START mode=$mode"
        }
    }

    LaunchedEffect(jobState) {
        if (jobState.kind != "precision_ab") return@LaunchedEffect
        when (jobState.status) {
            "running", "starting" -> {
                status = jobState.status
                currentPhotoName = jobState.current
                progress = jobState.progress
                phaseLabel = jobState.current
                if (jobState.detail.isNotEmpty()) detailLog = jobState.detail.takeLast(4000)
            }
            "done" -> {
                progress = 1f
                phaseLabel = "done"
                status = "SUITE_DONE ok=true ${jobState.resultPath}"
            }
            "failed" -> {
                phaseLabel = "failed"
                status = "SUITE_DONE ok=false — ${jobState.error}"
            }
        }
    }

    // Optional auto: only if explicitly requested AND user navigated with intent.
    // Default drawer no longer passes auto=fp32 (that hard-crashed phone on open).
    // auto=selected | selected_fp32 | selected_fp16 | selected_both — coverage subset suite.
    LaunchedEffect(autoMode, vehicles.size) {
        if (autoStarted || ExperimentJobRunner.isRunning()) return@LaunchedEffect
        val m = autoMode.lowercase()
        val selected = m == "selected" || m.startsWith("selected_")
        val mode = when {
            m == "selected" || m == "selected_sample" || m == "selected_fp32" -> "fp32"
            m == "selected_fp16" -> "fp16"
            m == "selected_both" -> "both"
            m == "fp32" || m == "fp16" || m == "both" -> m
            else -> return@LaunchedEffect
        }
        if (vehicles.isEmpty()) {
            Log.w(TAG, "autoMode=$m waiting for vehicles…")
            return@LaunchedEffect
        }
        autoStarted = true
        Log.i(TAG, "auto starting mode=$mode selectedSample=$selected (explicit)")
        runSuite(mode, selected)
    }

    LaunchedEffect(Unit) {
        val arch = NativePaddleEngine.modelArchForPrimaryAbi()
        val stamp = BuildConfig.PADDLE_SO_STAMP
        val arm64Sha = BuildConfig.PADDLE_SO_ARM64_V8A
        // Product FP16-only tailor (pre-v0.98-40 crash SO). Interim slim dual-kernel is 68489071…
        val knownFp16OnlyArm64 =
            arm64Sha.startsWith("5e7b6909") || arm64Sha.startsWith("96fbc6a5")
        val hasFp32Asset = try {
            context.assets.open("paddle/prod_u8fp32_u8/det_$arch.nb").close()
            true
        } catch (_: Exception) {
            false
        }
        when {
            arch == "armv8" && knownFp16OnlyArm64 -> {
                bannerIsError = true
                bannerNote =
                    "arch=$arch so=$stamp — arm64 light SO is FP16-tailored only; " +
                        "loading fp32 models will hard-crash. Use a dual-kernel SO build."
            }
            !hasFp32Asset -> {
                bannerIsError = true
                bannerNote =
                    "arch=$arch so=$stamp — missing assets paddle/prod_u8fp32_u8/det_$arch.nb"
            }
            else -> {
                bannerIsError = false
                bannerNote =
                    "arch=$arch so=$stamp — dual-precision ready: " +
                        "prod_u8fp16 and prod_u8fp32_u8 models present; SO stamp is not the " +
                        "old FP16-only product tailor. Safe to run fp32 / fp16 / both."
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            status,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (bannerNote.isNotEmpty()) {
            Text(
                bannerNote,
                style = MaterialTheme.typography.labelSmall,
                color = if (bannerIsError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
        }
        if (phaseLabel.isNotEmpty()) {
            Text(
                phaseLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
        if (detailLog.isNotEmpty()) {
            Text(
                detailLog,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (isRunning) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(currentPhotoName, style = MaterialTheme.typography.labelSmall)
                    Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Jobs use ExperimentJobRunner + FGS — screen lock / leaving this page does not cancel. " +
                "fp16 pack = prod_u8fp16. fp32 pack = prod_u8fp32_u8 (u8 in, enable_fp16=false). " +
                "Same light SO must contain both kernel sets on arm64.",
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { runSuite("fp32", true) },
            enabled = !isRunning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "Selected sample fp32 " +
                    "(${SelectedSamplePhotos.DASH.size} dash + ${SelectedSamplePhotos.PUMP.size} pump)",
                fontWeight = FontWeight.Bold,
            )
        }
        Button(
            onClick = { runSuite("fp16", true) },
            enabled = !isRunning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Selected sample fp16 (coverage subset)")
        }
        Button(
            onClick = { runSuite("both", true) },
            enabled = !isRunning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Selected sample both packs (coverage subset)")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { runSuite("fp32", false) },
            enabled = !isRunning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Run fp32 full (align + pump)")
        }
        Button(
            onClick = { runSuite("fp16", false) },
            enabled = !isRunning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Run fp16 full (align + pump)")
        }
        Button(
            onClick = { runSuite("both", false) },
            enabled = !isRunning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Run both (fp16 then fp32)")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Selected sample = coverage subset (dash for align, pump for pump; no cross-domain). " +
                "Deep link: vehicleexpenses://experiment/precision_ab?auto=selected\n" +
                "logcat: adb logcat -s PrecisionAb:I\n" +
                "pump photos: ${pumpPhotoDir.absolutePath}\n" +
                "align photos: ${alignPhotoDir.absolutePath}",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
