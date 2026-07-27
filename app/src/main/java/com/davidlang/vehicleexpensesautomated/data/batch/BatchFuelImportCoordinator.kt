package com.davidlang.vehicleexpensesautomated.data.batch

import android.content.Context
import android.util.Log
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.repository.FuelEntryRepository
import com.davidlang.vehicleexpensesautomated.ui.util.FuelPhotoJson
import com.davidlang.vehicleexpensesautomated.ui.util.ImageIngestionProvider
import com.davidlang.vehicleexpensesautomated.ui.util.NativePaddleEngine
import com.davidlang.vehicleexpensesautomated.ui.util.OcrHarness
import com.davidlang.vehicleexpensesautomated.ui.util.PhotoExifMetaReader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

data class BatchImportProgress(
    val phase: String,
    val current: Int,
    val total: Int,
    val message: String,
    val dashInserted: Int = 0,
    val pumpInserted: Int = 0,
    val pendingCount: Int = 0,
    val errors: Int = 0,
)

data class BatchImportResult(
    val dashInserted: Int,
    val pumpInserted: Int,
    val pending: List<BatchPendingItem>,
    val errors: List<String>,
    val cancelled: Boolean,
)

/**
 * Stage A batch ingest: walk hard-coded experiment photo dirs, OCR, insert partials.
 * Merge (Stage B) is a separate call / button.
 */
@Singleton
class BatchFuelImportCoordinator @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val fuelEntryRepository: FuelEntryRepository,
) {
    companion object {
        private const val TAG = "BatchFuelImport"
        private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "dng")

        /** Limited import size (like experiment Golden/Problem subset buttons). */
        const val LIMITED_IMPORT_COUNT = 20

        /**
         * Fuel row with no vehicle yet (pump-only batch ingest).
         * Merge later pairs by time/location with dash rows and assigns a real vehicleId.
         * No Room FK; 0 is not a real [Vehicle.id].
         */
        const val UNASSIGNED_VEHICLE_ID = 0

        fun dashPhotoDir(context: Context): File =
            File(context.filesDir, "experiment_photos").also { it.mkdirs() }

        fun pumpPhotoDir(context: Context): File =
            File(context.getExternalFilesDir(null), "pump_photos").also { it.mkdirs() }

        fun durablePhotoDir(context: Context): File =
            File(context.filesDir, "batch_import_photos").also { it.mkdirs() }
    }

    private val cancelFlag = AtomicBoolean(false)

    fun requestCancel() {
        cancelFlag.set(true)
    }

    fun clearCancel() {
        cancelFlag.set(false)
    }

    /**
     * Copy source into app-private durable dir; returns durable absolute path.
     */
    fun copyToDurable(source: File, prefix: String): File {
        val dir = durablePhotoDir(appContext)
        val safeName = source.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val dest = File(dir, "${prefix}_${System.currentTimeMillis()}_$safeName")
        source.copyTo(dest, overwrite = true)
        return dest
    }

    private fun listImages(dir: File): List<File> {
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f ->
            f.isFile && f.extension.lowercase() in IMAGE_EXTS
        }?.sortedBy { it.name } ?: emptyList()
    }

    /**
     * Dash odometer from Set J is already [pickBestOdometer]-filtered (4–7 pure digits) or null.
     * Do **not** digit-concatenate arbitrary OCR soup.
     */
    private fun parseSetJOdometer(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        if (raw.length !in 4..7 || !raw.all { it.isDigit() }) return null
        return raw.toIntOrNull()?.takeIf { it > 0 }
    }

    private fun parseMoneyOrVol(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.replace(Regex("[^0-9.]"), "")
        if (cleaned.isBlank() || cleaned == ".") return null
        return cleaned.toDoubleOrNull()?.takeIf { it > 0.0 }
    }

    /**
     * Stage A: ingest dash + pump archives into fuel_entries partials / pending.
     * Does not merge.
     *
     * @param maxDash if non-null, only the first N dash images (sorted by name) are processed.
     * @param maxPump if non-null, only the first N pump images (sorted by name) are processed.
     */
    suspend fun runIngest(
        vehicles: List<Vehicle>,
        onProgress: (BatchImportProgress) -> Unit = {},
        maxDash: Int? = null,
        maxPump: Int? = null,
    ): BatchImportResult = withContext(Dispatchers.Default) {
        clearCancel()
        val pending = BatchImportPendingStore.load(appContext).toMutableList()
        val errors = mutableListOf<String>()
        var dashInserted = 0
        var pumpInserted = 0
        var errCount = 0

        val allDash = listImages(dashPhotoDir(appContext))
        val allPump = listImages(pumpPhotoDir(appContext))
        val dashFiles = if (maxDash != null) allDash.take(maxDash.coerceAtLeast(0)) else allDash
        val pumpFiles = if (maxPump != null) allPump.take(maxPump.coerceAtLeast(0)) else allPump
        val total = dashFiles.size + pumpFiles.size
        var done = 0

        fun report(phase: String, msg: String) {
            onProgress(
                BatchImportProgress(
                    phase = phase,
                    current = done,
                    total = total,
                    message = msg,
                    dashInserted = dashInserted,
                    pumpInserted = pumpInserted,
                    pendingCount = pending.size,
                    errors = errCount,
                ),
            )
        }

        val limitNote = when {
            maxDash != null || maxPump != null ->
                " (limited: dash ${dashFiles.size}/${allDash.size}, pump ${pumpFiles.size}/${allPump.size})"
            else -> ""
        }
        report("init", "Dash ${dashFiles.size} · pump ${pumpFiles.size}$limitNote")

        // --- Dash (Set J via runAutoFillPipeline) ---
        for (file in dashFiles) {
            coroutineContext.ensureActive()
            if (cancelFlag.get()) {
                BatchImportPendingStore.save(appContext, pending)
                return@withContext BatchImportResult(
                    dashInserted, pumpInserted, pending, errors, cancelled = true,
                )
            }
            done++
            report("dash", "Dash OCR ${file.name} ($done/$total)")
            try {
                processDash(file, vehicles, pending)?.let { dashInserted++ }
            } catch (e: Exception) {
                errCount++
                val m = "Dash ${file.name}: ${e.message}"
                Log.e(TAG, m, e)
                errors.add(m)
            }
        }

        // --- Pump (Set I): always OCR + insert; vehicleId left unassigned (0) until merge ---
        for (file in pumpFiles) {
            coroutineContext.ensureActive()
            if (cancelFlag.get()) {
                BatchImportPendingStore.save(appContext, pending)
                return@withContext BatchImportResult(
                    dashInserted, pumpInserted, pending, errors, cancelled = true,
                )
            }
            done++
            report("pump", "Pump Set I ${file.name} ($done/$total)")
            try {
                val inserted = processPump(file, pending)
                if (inserted) pumpInserted++
            } catch (e: Exception) {
                errCount++
                val m = "Pump ${file.name}: ${e.message}"
                Log.e(TAG, m, e)
                errors.add(m)
            }
        }

        BatchImportPendingStore.save(appContext, pending)
        report("done", "Finished: dash=$dashInserted pump=$pumpInserted pending=${pending.size}")
        BatchImportResult(dashInserted, pumpInserted, pending, errors, cancelled = false)
    }

    private suspend fun processDash(
        file: File,
        vehicles: List<Vehicle>,
        pending: MutableList<BatchPendingItem>,
    ): Boolean {
        val meta = PhotoExifMetaReader.read(file.absolutePath)
        val ts = meta.timestampMs ?: System.currentTimeMillis()
        val durable = copyToDurable(file, "dash")

        val (w, h) = ImageIngestionProvider.probeDimensions(appContext, file.absolutePath)
        if (w <= 0 || h <= 0) {
            pending.add(
                BatchPendingItem(
                    kind = BatchPendingKind.OTHER,
                    message = "Dash unreadable dimensions: ${file.name}",
                    photoPath = file.absolutePath,
                    durablePhotoPath = durable.absolutePath,
                    timestampMs = ts,
                ),
            )
            return false
        }

        val master = NativePaddleEngine.bufferSetA
        master.resize(w, h)
        ImageIngestionProvider.ingestFromFile(appContext, file.absolutePath, master.p)

        val activeVehicles = vehicles.filter { !it.deleted }
        val result = OcrHarness.runAutoFillPipeline(
            context = appContext,
            masterBuffer = master,
            allVehicles = activeVehicles,
            debug = false,
            cameraRotationDegrees = 0,
            onStage = null,
        )

        if (result.vehicleId == null) {
            pending.add(
                BatchPendingItem(
                    kind = BatchPendingKind.UNREADABLE_DASH_NO_VEHICLE,
                    message = "Could not identify vehicle for ${file.name}" +
                        (result.error?.let { ": $it" } ?: ""),
                    photoPath = file.absolutePath,
                    durablePhotoPath = durable.absolutePath,
                    timestampMs = ts,
                    latitude = meta.latitude,
                    longitude = meta.longitude,
                ),
            )
            return false
        }

        val odo = parseSetJOdometer(result.odometer)
        val photoJson = FuelPhotoJson.single("dash", durable.absolutePath, ts)

        if (odo == null) {
            // Blank marker row: zeros break both chains; not partial inventory.
            fuelEntryRepository.insertFuelEntry(
                FuelEntry(
                    vehicleId = result.vehicleId,
                    odometer = 0,
                    gallons = 0.0,
                    cost = 0.0,
                    currency = "USD",
                    timestamp = ts,
                    photoUrl = photoJson,
                    isPartialFill = false,
                    latitude = meta.latitude,
                    longitude = meta.longitude,
                    location = "batch_import_dash_blank:${file.name}",
                ),
            )
            Log.i(TAG, "Inserted blank dash marker vehicle=${result.vehicleId} ${file.name}")
            return true
        }

        fuelEntryRepository.insertFuelEntry(
            FuelEntry(
                vehicleId = result.vehicleId,
                odometer = odo,
                gallons = 0.0,
                cost = 0.0,
                currency = "USD",
                timestamp = ts,
                photoUrl = photoJson,
                isPartialFill = true,
                latitude = meta.latitude,
                longitude = meta.longitude,
                location = "batch_import_dash:${file.name}",
            ),
        )
        Log.i(TAG, "Inserted odo partial vehicle=${result.vehicleId} odo=$odo ${file.name}")
        return true
    }

    private suspend fun processPump(
        file: File,
        defaultVehicleId: Int?,
        pending: MutableList<BatchPendingItem>,
    ): Boolean {
        val meta = PhotoExifMetaReader.read(file.absolutePath)
        val ts = meta.timestampMs ?: System.currentTimeMillis()
        val durable = copyToDurable(file, "pump")

        if (defaultVehicleId == null) {
            pending.add(
                BatchPendingItem(
                    kind = BatchPendingKind.ASSIGN_VEHICLE,
                    message = "Which vehicle for pump photo ${file.name}?",
                    photoPath = file.absolutePath,
                    durablePhotoPath = durable.absolutePath,
                    timestampMs = ts,
                    latitude = meta.latitude,
                    longitude = meta.longitude,
                ),
            )
            return false
        }

        val (w, h) = ImageIngestionProvider.probeDimensions(appContext, file.absolutePath)
        if (w <= 0 || h <= 0) {
            pending.add(
                BatchPendingItem(
                    kind = BatchPendingKind.UNREADABLE_PUMP,
                    message = "Pump unreadable dimensions: ${file.name}",
                    photoPath = file.absolutePath,
                    durablePhotoPath = durable.absolutePath,
                    timestampMs = ts,
                    suggestedVehicleId = defaultVehicleId,
                ),
            )
            return false
        }

        val master = NativePaddleEngine.bufferSetA
        master.resize(w, h)
        ImageIngestionProvider.ingestFromFile(appContext, file.absolutePath, master.p)

        val result = OcrHarness.runPumpCostVolPipelineSetI(appContext, master, debug = false)
        val cost = parseMoneyOrVol(result.cost)
        val vol = parseMoneyOrVol(result.volume)

        if (cost == null && vol == null) {
            pending.add(
                BatchPendingItem(
                    kind = BatchPendingKind.UNREADABLE_PUMP,
                    message = "Unreadable pump ${file.name}" +
                        (result.error?.let { ": $it" } ?: ""),
                    photoPath = file.absolutePath,
                    durablePhotoPath = durable.absolutePath,
                    timestampMs = ts,
                    latitude = meta.latitude,
                    longitude = meta.longitude,
                    suggestedVehicleId = defaultVehicleId,
                ),
            )
            return false
        }

        val photoJson = FuelPhotoJson.single("pump", durable.absolutePath, ts)
        fuelEntryRepository.insertFuelEntry(
            FuelEntry(
                vehicleId = defaultVehicleId,
                odometer = 0,
                gallons = vol ?: 0.0,
                cost = cost ?: 0.0,
                currency = "USD",
                timestamp = ts,
                photoUrl = photoJson,
                isPartialFill = true,
                latitude = meta.latitude,
                longitude = meta.longitude,
                location = "batch_import_pump:${file.name}",
            ),
        )
        Log.i(TAG, "Inserted pump partial vehicle=$defaultVehicleId cost=$cost vol=$vol ${file.name}")
        return true
    }
}
