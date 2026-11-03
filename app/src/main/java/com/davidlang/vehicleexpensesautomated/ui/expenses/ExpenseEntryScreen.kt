package com.davidlang.vehicleexpensesautomated.ui.expenses

import android.widget.Toast
import androidx.camera.core.ImageCapture
import com.davidlang.vehicleexpensesautomated.ui.util.CameraCaptureProfile
import com.davidlang.vehicleexpensesautomated.ui.util.CameraResolutionPicker
import com.davidlang.vehicleexpensesautomated.ui.util.deviceHasCamera
import com.davidlang.vehicleexpensesautomated.ui.util.CaptureLocation
import com.davidlang.vehicleexpensesautomated.ui.util.NetworkStatus
import com.davidlang.vehicleexpensesautomated.ui.util.PhotoExifMetaReader
import com.davidlang.vehicleexpensesautomated.ui.util.PhotoExifWriter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.data.batch.FuelLocationJson
import com.davidlang.vehicleexpensesautomated.data.location.LocationLookup
import com.davidlang.vehicleexpensesautomated.data.location.LocationLookupScheduler
import com.davidlang.vehicleexpensesautomated.data.expense.ExpenseCategories
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry
import com.davidlang.vehicleexpensesautomated.data.repository.forUserPicker
import com.davidlang.vehicleexpensesautomated.data.sync.SyncDestinationStore
import com.davidlang.vehicleexpensesautomated.ui.components.AppDateTimeField
import com.davidlang.vehicleexpensesautomated.ui.components.CameraPreview
import com.davidlang.vehicleexpensesautomated.ui.components.CameraZoomControl
import com.davidlang.vehicleexpensesautomated.ui.components.ExposedDropdownMenuWithManageFooter
import com.davidlang.vehicleexpensesautomated.ui.components.LocationConfirmBlock
import com.davidlang.vehicleexpensesautomated.ui.components.RegisterPageHelp
import com.davidlang.vehicleexpensesautomated.ui.components.StationPickerDialog
import com.davidlang.vehicleexpensesautomated.data.location.LocationLookupKind
import com.davidlang.vehicleexpensesautomated.ui.components.expenseHasArchiveIdentity
import com.davidlang.vehicleexpensesautomated.ui.components.expenseLocalMissingOrDead
import com.davidlang.vehicleexpensesautomated.ui.settings.SettingsViewModel
import com.davidlang.vehicleexpensesautomated.ui.util.CurrencyCodes
import com.davidlang.vehicleexpensesautomated.ui.vehicle.VehicleViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Currency
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import androidx.compose.ui.res.stringResource
import com.davidlang.vehicleexpensesautomated.R
import com.davidlang.vehicleexpensesautomated.ui.util.AppStrings

private fun surfaceRotationToDegrees(rotation: Int): Int = when (rotation) {
    android.view.Surface.ROTATION_0 -> 0
    android.view.Surface.ROTATION_90 -> 90
    android.view.Surface.ROTATION_180 -> 180
    android.view.Surface.ROTATION_270 -> 270
    else -> 0
}

/** CameraX relative rotation for EXIF (matches [androidx.camera.core.ImageInfo.rotationDegrees]). */
private fun relativeRotationDegreesForCapture(
    context: android.content.Context,
    listenerSurfaceRotation: Int,
): Int {
    val cameraManager =
        context.getSystemService(android.content.Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
    val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
        val facing = cameraManager.getCameraCharacteristics(id)
            .get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)
        facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK
    } ?: return 0
    val characteristics = cameraManager.getCameraCharacteristics(cameraId)
    val sensorOrientation =
        characteristics.get(android.hardware.camera2.CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
    val lensFacing = characteristics.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)
        ?: android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK
    val targetDegrees = surfaceRotationToDegrees(listenerSurfaceRotation)
    return if (lensFacing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT) {
        (sensorOrientation + targetDegrees) % 360
    } else {
        (sensorOrientation - targetDegrees + 360) % 360
    }
}

private data class ClickSnapshot(
    val surfaceRotation: Int,
    val location: android.location.Location?,
)

private fun expenseFireCapturedShot(
    setIsPhotoSaving: (Boolean) -> Unit,
    setPhotoStatus: (String?) -> Unit,
    takePhoto: () -> Unit,
) {
    setIsPhotoSaving(true)
    setPhotoStatus(AppStrings.get(R.string.expense_saving_photo))
    takePhoto()
}

private fun expenseShutterClick(
    saveExpensePhotos: Boolean,
    hasCamera: Boolean,
    isPhotoSaving: Boolean,
    shotPending: Boolean,
    previewBound: Boolean,
    previewBindFailed: Boolean,
    showLiveCamera: Boolean,
    hasCloudOnlyReceipt: Boolean,
    context: android.content.Context,
    surfaceRotation: Int,
    deviceLocation: android.location.Location?,
    clickSnapshot: AtomicReference<ClickSnapshot?>,
    setPhotoUrl: (String?) -> Unit,
    setPhotoStatus: (String?) -> Unit,
    setShowLiveCamera: (Boolean) -> Unit,
    setPhotoFromGallery: (Boolean) -> Unit,
    setRowLat: (Double?) -> Unit,
    setRowLon: (Double?) -> Unit,
    setRowAccuracyM: (Double?) -> Unit,
    setShotPending: (Boolean) -> Unit,
    setPreviewBindFailed: (Boolean) -> Unit,
    setIsPhotoSaving: (Boolean) -> Unit,
    takePhoto: () -> Unit,
) {
    if (!saveExpensePhotos) {
        setPhotoUrl(null)
        setPhotoStatus(null)
        setShowLiveCamera(true)
        return
    }
    if (!hasCamera || isPhotoSaving || shotPending) return
    val snapshot = ClickSnapshot(surfaceRotation, deviceLocation)
    clickSnapshot.set(snapshot)
    setPhotoFromGallery(false)
    setRowLat(snapshot.location?.latitude)
    setRowLon(snapshot.location?.longitude)
    setRowAccuracyM(snapshot.location?.let { if (it.hasAccuracy()) it.accuracy.toDouble() else null })
    if (!previewBound) {
        // The live preview's factory binds once. A false already delivered means no later onBound.
        // A cloud-only receipt stays in front of that preview, so this tap gets no onBound either.
        val previewAlreadyComposed = showLiveCamera && !hasCloudOnlyReceipt
        setShowLiveCamera(true)
        if (hasCloudOnlyReceipt || (previewBindFailed && previewAlreadyComposed)) {
            clickSnapshot.set(null)
            Toast.makeText(context, context.getString(R.string.expense_camera_unavailable), Toast.LENGTH_LONG).show()
            return
        }
        setPreviewBindFailed(false)
        setShotPending(true)
        return
    }
    expenseFireCapturedShot(setIsPhotoSaving, setPhotoStatus, takePhoto)
}

private fun expensePreviewOnBound(
    bound: Boolean,
    shotPending: Boolean,
    context: android.content.Context,
    clickSnapshot: AtomicReference<ClickSnapshot?>,
    setPreviewBound: (Boolean) -> Unit,
    setShotPending: (Boolean) -> Unit,
    setPreviewBindFailed: (Boolean) -> Unit,
    setIsPhotoSaving: (Boolean) -> Unit,
    setPhotoStatus: (String?) -> Unit,
    takePhoto: () -> Unit,
) {
    setPreviewBound(bound)
    if (bound) {
        setPreviewBindFailed(false)
        if (!shotPending) return
        setShotPending(false)
        expenseFireCapturedShot(setIsPhotoSaving, setPhotoStatus, takePhoto)
    } else {
        setPreviewBindFailed(true)
        if (shotPending) {
            setShotPending(false)
            clickSnapshot.set(null)
            Toast.makeText(context, context.getString(R.string.expense_camera_unavailable), Toast.LENGTH_LONG).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEntryScreen(
    navController: NavHostController? = null,
    mode: ExpenseEntryMode = ExpenseEntryMode.Create,
) {
    // D4: reset all form state when switching create ↔ edit or edit id (no flash of prior expense).
    val resetKey = when (mode) {
        ExpenseEntryMode.Create -> 0L
        is ExpenseEntryMode.Edit -> mode.id
    }
    key(resetKey) {
        ExpenseEntryScreenBody(navController = navController, mode = mode)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseEntryScreenBody(
    navController: NavHostController? = null,
    mode: ExpenseEntryMode = ExpenseEntryMode.Create,
) {
    val viewModel: ExpenseViewModel = hiltViewModel()
    val vehicleViewModel: VehicleViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val photoStorage = settingsViewModel.photoStorageManager
    val context = LocalContext.current
    val hasCamera = remember { deviceHasCamera(context) }
    val prefs = remember { context.getSharedPreferences("vehicle_settings", android.content.Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val editId = (mode as? ExpenseEntryMode.Edit)?.id

    val allVehicles by vehicleViewModel.vehicles.collectAsState(initial = emptyList())
    val vehicles = remember(allVehicles) {
        allVehicles.forUserPicker()
    }
    var selectedVehicleId by rememberSaveable { mutableStateOf<Int?>(null) }
    val selectedVehicle = remember(vehicles, selectedVehicleId) {
        vehicles.firstOrNull { it.id == selectedVehicleId }
    }
    var vehicleDropdownExpanded by remember { mutableStateOf(false) }
    var loadedId by rememberSaveable { mutableStateOf<Long?>(null) }
    /** Full row from DB on edit — preserves metadata not shown in the form. */
    var loadedExpense by remember { mutableStateOf<ExpenseEntry?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val editLoadReady = editId == null || (loadedExpense != null && loadedId == editId)

    val imageCapture: ImageCapture = remember {
        ImageCapture.Builder()
            .setResolutionSelector(
                CameraResolutionPicker.resolutionSelector(CameraCaptureProfile.RECEIPT_MAX),
            )
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }

    var zoomControl by remember { mutableStateOf<CameraZoomControl?>(null) }
    var listenerSurfaceRotation by remember { mutableIntStateOf(android.view.Surface.ROTATION_0) }
    var isPhotoSaving by remember { mutableStateOf(false) }
    var previewBound by remember { mutableStateOf(false) }
    var previewBindFailed by remember { mutableStateOf(false) }
    var shotPending by remember { mutableStateOf(false) }
    val clickSnapshot = remember { AtomicReference<ClickSnapshot?>(null) }
    var isDownloadingCloud by remember { mutableStateOf(false) }
    var photoStatus by remember { mutableStateOf<String?>(null) }
    var showLiveCamera by remember { mutableStateOf(hasCamera) }

    var amount by rememberSaveable { mutableStateOf("") }
    val defaultCurrencySymbol = remember {
        CurrencyCodes.settingsDefaultSymbol(prefs)
    }
    var currencySymbol by rememberSaveable { mutableStateOf(defaultCurrencySymbol) }
    var vendor by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var showManageCategories by remember { mutableStateOf(false) }
    val categoryOptions = remember(selectedVehicle?.expenseCategoriesJson, selectedVehicle?.id, category) {
        val base = ExpenseCategories.parse(selectedVehicle?.expenseCategoriesJson)
        if (category.isNotBlank() && base.none { it.equals(category, ignoreCase = true) }) {
            listOf(category) + base
        } else {
            base
        }
    }
    var odometerText by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    /** Create defaults true; edit load sets false (D4). */
    var timeIsNow by rememberSaveable { mutableStateOf(editId == null) }
    var photoUrl by rememberSaveable { mutableStateOf<String?>(null) }

    RegisterPageHelp(
        title = stringResource(R.string.expense_expense_entry),
        "Disk = save · white circle = take receipt · gallery icon = pick image · Retake clears the photo.",
        stringResource(R.string.expense_tap_the_currency_symbol_on_amount_to_change_curr),
        stringResource(R.string.expense_time_is_now_default_on_create_stamps_the_save_ti),
        stringResource(R.string.expense_manage_categories_stays_pinned_at_the_bott) +
            stringResource(R.string.expense_scroll_the_list_above_if_long_select_a_vehicle_f),
    )
    var showDatePicker by remember { mutableStateOf(false) }
    /** Once-per-screen device fix for camera path EXIF + row (isolated from gallery). */
    var deviceLocation by remember { mutableStateOf<android.location.Location?>(null) }
    /** Lat/lon persisted on save (camera → device; gallery → EXIF-or-null). */
    var rowLat by remember { mutableStateOf<Double?>(null) }
    var rowLon by remember { mutableStateOf<Double?>(null) }
    var rowAccuracyM by remember { mutableStateOf<Double?>(null) }
    /** True when attached photo is gallery-sourced (device GPS must not win on row). */
    var photoFromGallery by remember { mutableStateOf(false) }
    var locationStatus by remember { mutableStateOf("") }
    var placeName by remember { mutableStateOf("") }
    var placeAddress by remember { mutableStateOf("") }
    var lookupName by remember { mutableStateOf<String?>(null) }
    var lookupAddress by remember { mutableStateOf<String?>(null) }
    var lookupSource by remember { mutableStateOf<String?>(null) }
    var showStationPicker by remember { mutableStateOf(false) }
    val photoDest = remember { SyncDestinationStore(context).photoDestination() }

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Composable
    fun rememberRowLocation() {
        // One-shot device GPS for camera path (not re-fetched per shutter).
        LaunchedEffect(Unit) {
            val fix = CaptureLocation.captureLocationOrNull(context)
            deviceLocation = fix
            if (fix != null && editId == null && !photoFromGallery) {
                rowLat = fix.latitude
                rowLon = fix.longitude
                rowAccuracyM = if (fix.hasAccuracy()) fix.accuracy.toDouble() else null
            }
        }

        LaunchedEffect(rowLat, rowLon, category) {
            val la = rowLat
            val lo = rowLon
            if (la == null || lo == null) {
                locationStatus = ""
                lookupName = null
                lookupAddress = null
                lookupSource = null
                return@LaunchedEffect
            }
            if (!NetworkStatus.hasUsableNetwork(context)) {
                locationStatus = AppStrings.get(R.string.expense_offline_place_lookup_when_online)
                lookupName = null
                lookupAddress = null
                lookupSource = null
                return@LaunchedEffect
            }
            locationStatus = AppStrings.get(R.string.expense_looking_up_place)
            val kind = LocationLookup.kindForExpenseCategory(category)
            val result = LocationLookup.lookup(
                lat = la,
                lon = lo,
                kind = kind,
                accuracyM = rowAccuracyM,
                uiTimeout = true,
            )
            if (result != null && result.hasPlace()) {
                placeName = result.name
                placeAddress = result.address
                lookupName = result.name
                lookupAddress = result.address
                lookupSource = result.source
                // Happy path: address fields only — no duplicate "Resolved:" banner.
                locationStatus = ""
            } else {
                lookupName = null
                lookupAddress = null
                lookupSource = null
                locationStatus = AppStrings.get(R.string.expense_no_place_found_will_retry_after_save_if_on)
            }
        }
    }
    rememberRowLocation()
    val localPhotoMissing = remember(photoUrl) {
        expenseLocalMissingOrDead(photoUrl, photoStorage)
    }
    val hasCloudOnlyReceipt = remember(loadedExpense, photoUrl, photoDest, localPhotoMissing) {
        val destId = photoDest?.id ?: return@remember false
        localPhotoMissing && expenseHasArchiveIdentity(loadedExpense, destId)
    }

    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    // Prefill for edit; auto-select first vehicle for create
    LaunchedEffect(mode, vehicles) {
        if (editId != null) {
            if (loadedId != editId) {
                val entry = viewModel.getExpenseById(editId)
                if (entry != null) {
                    var e = entry
                    if (expenseLocalMissingOrDead(e.photoUrl, photoStorage) && !e.photoUrl.isNullOrBlank()) {
                        e = viewModel.scrubUnreadableExpensePhotos(e)
                    }
                    selectedVehicleId = e.vehicleId
                    amount = if (e.amount == 0.0) "" else e.amount.toString()
                    currencySymbol = CurrencyCodes.displaySymbol(
                        e.currency,
                        defaultCurrencySymbol,
                    )
                    vendor = e.vendor
                    description = e.description
                    category = e.category
                    odometerText = e.odometer?.toString() ?: ""
                    date = e.date
                    timeIsNow = false
                    photoUrl = e.photoUrl
                    rowLat = FuelLocationJson.lat(e.location)
                    rowLon = FuelLocationJson.lon(e.location)
                    rowAccuracyM = FuelLocationJson.accuracyM(e.location)
                    val blob = FuelLocationJson.parseBlob(e.location)
                    placeName = blob?.name.orEmpty()
                    placeAddress = blob?.address.orEmpty()
                    photoFromGallery = false
                    showLiveCamera = hasCamera &&
                        expenseLocalMissingOrDead(e.photoUrl, photoStorage) &&
                        !expenseHasArchiveIdentity(e, photoDest?.id)
                    loadedExpense = e
                    loadedId = editId
                } else {
                    loadedExpense = null
                    Toast.makeText(context, AppStrings.get(R.string.expense_expense_not_found), Toast.LENGTH_LONG).show()
                    navController?.popBackStack()
                        ?: navController?.navigate("expenselist") { launchSingleTop = true }
                }
            }
        } else {
            loadedExpense = null
            if (selectedVehicleId == null && vehicles.isNotEmpty()) {
                selectedVehicleId = vehicles.first().id
            }
            if (photoUrl == null && hasCamera) {
                showLiveCamera = true
            }
        }
    }
    // Default category = first catalog entry when creating (not when editing loaded row)
    LaunchedEffect(selectedVehicleId, selectedVehicle?.expenseCategoriesJson, editId, loadedId) {
        if (editId != null) return@LaunchedEffect
        if (category.isBlank()) {
            category = ExpenseCategories.defaultCategory(selectedVehicle?.expenseCategoriesJson)
        }
    }

    val imagePicker = com.davidlang.vehicleexpensesautomated.ui.components.rememberImagePicker(
        allowCamera = true,
        photoStorageManager = photoStorage,
        captureProfile = CameraCaptureProfile.RECEIPT_MAX,
        imageCapture = imageCapture,
        displayName = { "expense_${System.currentTimeMillis()}.jpg" },
        beforeSave = { file ->
            val snapshot = clickSnapshot.get()
                ?: throw IllegalStateException(AppStrings.get(R.string.expense_missing_click_snapshot))
            PhotoExifWriter.writeGpsAndOrientation(
                context,
                file.absolutePath,
                snapshot.location,
                relativeRotationDegreesForCapture(context, snapshot.surfaceRotation),
                userComment = "ve:tag=expense",
            )
        },
        onCaptured = { uri ->
            photoUrl = uri.toString()
            photoFromGallery = false
            isPhotoSaving = false
            photoStatus = null
            clickSnapshot.set(null)
            showLiveCamera = false
        },
        onCaptureFailed = {
            isPhotoSaving = false
            photoStatus = AppStrings.get(R.string.expense_photo_save_failed)
            clickSnapshot.set(null)
        },
    ) { uri ->
        if (prefs.getBoolean("save_expense_photos", true)) {
            photoUrl = uri.toString()
            photoStatus = null
            showLiveCamera = false
            photoFromGallery = true
            val meta = PhotoExifMetaReader.read(context, uri)
            rowLat = meta.latitude
            rowLon = meta.longitude
            rowAccuracyM = meta.accuracyM
            Toast.makeText(context, AppStrings.get(R.string.expense_photo_selected), Toast.LENGTH_SHORT).show()
        } else {
            photoUrl = null
            if (hasCamera) {
                showLiveCamera = true
            }
        }
    }

    fun saveExpense() {
        if (isSaving) return
        if (editId != null && !editLoadReady) {
            Toast.makeText(context, AppStrings.get(R.string.expense_still_loading_expense), Toast.LENGTH_SHORT).show()
            return
        }
        val vehicleId = selectedVehicleId
        if (vehicleId == null) {
            Toast.makeText(context, AppStrings.get(R.string.expense_select_a_vehicle), Toast.LENGTH_SHORT).show()
            return
        }
        val amountVal = amount.toDoubleOrNull() ?: 0.0
        val storedCurrency = CurrencyCodes.fromSymbolOrCode(currencySymbol)
        val odo = odometerText.trim().toIntOrNull()
        val saveDate = if (timeIsNow) System.currentTimeMillis() else date
        // copy() from loadedExpense preserves photoUrl, lat/long, location, cloudManifest
        val base = loadedExpense
        val toSave = (base ?: ExpenseEntry(
            vehicleId = vehicleId,
            amount = 0.0,
            description = "",
            date = saveDate
        )).copy(
            id = editId ?: 0L,
            vehicleId = vehicleId,
            amount = amountVal,
            currency = storedCurrency,
            description = description,
            vendor = vendor,
            odometer = odo,
            category = category,
            date = saveDate,
            photoUrl = if (prefs.getBoolean("save_expense_photos", true)) photoUrl else null,
            location = run {
                val base = FuelLocationJson.fromCoords(
                    rowLat,
                    rowLon,
                    rowAccuracyM,
                    source = if (photoFromGallery) "exif" else "device",
                ) ?: FuelLocationJson.Blob()
                val placeBlank = placeName.isBlank() && placeAddress.isBlank()
                val kind = LocationLookup.kindForExpenseCategory(category)
                // Non-blank place → confirmed=true; blank → coords-only.
                val saveBlob = if (!placeBlank) {
                    base.withPlace(
                        name = placeName,
                        address = placeAddress,
                        confirmed = true,
                        source = FuelLocationJson.placeSourceForConfirm(
                            placeName,
                            placeAddress,
                            lookupName,
                            lookupAddress,
                            lookupSource,
                        ),
                        kind = kind.blobKindTag(),
                        lookedUpAt = System.currentTimeMillis(),
                    )
                } else {
                    base.coordsOnly()
                }
                if (saveBlob.hasCoordsWithoutPlace()) {
                    LocationLookupScheduler.enqueueSoon(context)
                }
                FuelLocationJson.encode(saveBlob)
            },
        )
        // D5: await persistence before navigate
        scope.launch {
            isSaving = true
            try {
                viewModel.saveExpense(toSave)
                Toast.makeText(
                    context,
                    if (editId != null) AppStrings.get(R.string.expense_expense_updated) else AppStrings.get(R.string.expense_expense_saved),
                    Toast.LENGTH_SHORT
                ).show()
                navController?.navigate("expenselist") {
                    popUpTo("expenselist") { inclusive = false }
                    launchSingleTop = true
                } ?: navController?.popBackStack()
            // kept: failure stays on this call so the screen or caller can continue
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    AppStrings.get(R.string.expense_save_failed_1_s, e.message ?: e),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                isSaving = false
            }
        }
    }


    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            // DatePicker is UTC midnight; keep local calendar day
                            val cal = Calendar.getInstance().apply {
                                timeInMillis = millis
                            }
                            val local = Calendar.getInstance().apply {
                                set(Calendar.YEAR, cal.get(Calendar.YEAR))
                                set(Calendar.MONTH, cal.get(Calendar.MONTH))
                                set(Calendar.DAY_OF_MONTH, cal.get(Calendar.DAY_OF_MONTH))
                                set(Calendar.HOUR_OF_DAY, 12)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            date = local.timeInMillis
                        }
                        showDatePicker = false
                    }
                ) { Text(stringResource(R.string.settings_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.settings_cancel)) }
            }
        ) {
            DatePicker(state = state)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
        @Composable
        fun ColumnScope.ExpensePhotoRegion() {
            // Photo / camera region
            Box(
                modifier = Modifier
                    .weight(0.40f)
                    .fillMaxWidth()
                    .background(Color.Black)
            ) {
                if (hasCloudOnlyReceipt) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            stringResource(R.string.expense_receipt_is_in_cloud_backup_only),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(
                            onClick = {
                                val entry = loadedExpense ?: return@Button
                                scope.launch {
                                    isDownloadingCloud = true
                                    photoStatus = AppStrings.get(R.string.expense_fetching_image)
                                    try {
                                        val scrubbed = viewModel.scrubUnreadableExpensePhotos(entry)
                                        loadedExpense = scrubbed
                                        val local = viewModel.downloadExpensePhoto(scrubbed)
                                        if (local != null) {
                                            photoUrl = local
                                            showLiveCamera = false
                                            photoStatus = null
                                            val refreshed = viewModel.getExpenseById(scrubbed.id)
                                            if (refreshed != null) loadedExpense = refreshed
                                            Toast.makeText(context, AppStrings.get(R.string.fuel_image_fetched), Toast.LENGTH_SHORT).show()
                                        } else {
                                            photoStatus = AppStrings.get(R.string.expense_fetch_failed)
                                            Toast.makeText(context, AppStrings.get(R.string.fuel_could_not_fetch_image), Toast.LENGTH_LONG).show()
                                        }
                                    // kept: failure stays on this call so the screen or caller can continue
                                    } catch (e: Exception) {
                                        photoStatus = AppStrings.get(R.string.expense_fetch_failed)
                                        Toast.makeText(context, e.message ?: AppStrings.get(R.string.expense_fetch_failed), Toast.LENGTH_LONG).show()
                                    } finally {
                                        isDownloadingCloud = false
                                    }
                                }
                            },
                            enabled = !isDownloadingCloud,
                            modifier = Modifier.padding(top = 12.dp),
                        ) {
                            Text(if (isDownloadingCloud) stringResource(R.string.fuel_fetching) else stringResource(R.string.fuel_fetch_image_from_archive))
                        }
                    }
                } else if (showLiveCamera) {
                    // A new composition is a new factory and binds again. Clear the previous
                    // instance's failure here, before this instance reports onBound.
                    DisposableEffect(Unit) {
                        previewBindFailed = false
                        onDispose { }
                    }
                    CameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        imageCapture = imageCapture,
                        onZoomControlChanged = { zoomControl = it },
                        onListenerSurfaceRotation = { listenerSurfaceRotation = it },
                        onBound = { bound ->
                            expensePreviewOnBound(
                                bound = bound,
                                shotPending = shotPending,
                                context = context,
                                clickSnapshot = clickSnapshot,
                                setPreviewBound = { previewBound = it },
                                setShotPending = { shotPending = it },
                                setPreviewBindFailed = { previewBindFailed = it },
                                setIsPhotoSaving = { isPhotoSaving = it },
                                setPhotoStatus = { photoStatus = it },
                                takePhoto = { imagePicker.takePhoto() },
                            )
                        },
                    )
                    zoomControl?.let { zoom ->
                        if (zoom.availableRatios.size > 1) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                zoom.availableRatios.forEach { ratio ->
                                    val selected = kotlin.math.abs(zoom.currentRatio - ratio) < 0.05f
                                    FilledTonalButton(
                                        onClick = { zoom.setZoomRatio(ratio) },
                                        modifier = Modifier.height(32.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = if (selected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            }
                                        ),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) {
                                        Text(
                                            text = if (ratio == ratio.toLong().toFloat()) {
                                                AppStrings.get(R.string.expense_s_1_sx, ratio.toLong())
                                            } else {
                                                AppStrings.get(R.string.expense_s_1_sx, ratio)
                                            },
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (!photoUrl.isNullOrBlank() && localPhotoMissing) {
                    ExpenseMissingMark(photoUrl!!)
                } else {
                    var showZoom by remember(photoUrl) { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { showZoom = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        com.davidlang.vehicleexpensesautomated.ui.components.DecodedPhotoImage(
                            uri = photoUrl!!,
                            contentDescription = stringResource(R.string.expense_expense_photo_tap_to_zoom),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                        Text(
                            stringResource(R.string.expense_tap_to_zoom),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.45f))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    if (showZoom) {
                        com.davidlang.vehicleexpensesautomated.ui.components.ZoomablePhotoDialog(
                            uris = listOf(photoUrl!!),
                            title = stringResource(R.string.expense_expense_photo),
                            onDismiss = { showZoom = false },
                        )
                    }
                }
            }
        }
        ExpensePhotoRegion()

        @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
        @Composable
        fun ExpenseCaptureControls() {
            // Controls: Save | Shutter | Gallery | Retake when photo shown
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { saveExpense() },
                    enabled = !isPhotoSaving && !isSaving && editLoadReady
                ) {
                    Icon(
                        imageVector = Icons.Filled.Save,
                        contentDescription = stringResource(R.string.expense_save_expense),
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(
                    onClick = {
                        expenseShutterClick(
                            saveExpensePhotos = prefs.getBoolean("save_expense_photos", true),
                            hasCamera = hasCamera,
                            isPhotoSaving = isPhotoSaving,
                            shotPending = shotPending,
                            previewBound = previewBound,
                            previewBindFailed = previewBindFailed,
                            showLiveCamera = showLiveCamera,
                            hasCloudOnlyReceipt = hasCloudOnlyReceipt,
                            context = context,
                            surfaceRotation = listenerSurfaceRotation,
                            deviceLocation = deviceLocation,
                            clickSnapshot = clickSnapshot,
                            setPhotoUrl = { photoUrl = it },
                            setPhotoStatus = { photoStatus = it },
                            setShowLiveCamera = { showLiveCamera = it },
                            setPhotoFromGallery = { photoFromGallery = it },
                            setRowLat = { rowLat = it },
                            setRowLon = { rowLon = it },
                            setRowAccuracyM = { rowAccuracyM = it },
                            setShotPending = { shotPending = it },
                            setPreviewBindFailed = { previewBindFailed = it },
                            setIsPhotoSaving = { isPhotoSaving = it },
                            takePhoto = { imagePicker.takePhoto() },
                        )
                    },
                    enabled = !isPhotoSaving,
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color.White, CircleShape)
                        .border(4.dp, Color.Gray, CircleShape)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                if (isPhotoSaving) MaterialTheme.colorScheme.error else Color.White,
                                CircleShape
                            )
                    )
                }

                IconButton(
                    onClick = { imagePicker.pickFromGallery() },
                    enabled = !isPhotoSaving && !shotPending
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = stringResource(R.string.expense_pick_picture_from_gallery),
                        modifier = Modifier.size(32.dp)
                    )
                }

                if (photoUrl != null) {
                    TextButton(
                        onClick = {
                            photoUrl = null
                            showLiveCamera = true
                            photoStatus = null
                            shotPending = false
                            clickSnapshot.set(null)
                        },
                        enabled = !isPhotoSaving,
                    ) {
                        Text(stringResource(R.string.expense_retake))
                    }
                }
            }

            photoStatus?.let { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isPhotoSaving) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }
        }
        ExpenseCaptureControls()

        @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
        @Composable
        fun ColumnScope.ExpenseForm() {
            // Form
            Column(
                modifier = Modifier
                    .weight(0.60f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = timeIsNow,
                        onCheckedChange = { checked ->
                            timeIsNow = checked
                            if (checked) date = System.currentTimeMillis()
                        },
                    )
                    Text(stringResource(R.string.expense_time_is_now), style = MaterialTheme.typography.bodyMedium)
                }
                if (!timeIsNow) {
                    AppDateTimeField(
                        label = stringResource(R.string.expense_date_1_s, dateFmt.format(Date(date))),
                        onClick = { showDatePicker = true },
                    )
                }

                val vehicleName = vehicles.find { it.id == selectedVehicleId }?.name ?: stringResource(R.string.expense_select_vehicle)
                ExposedDropdownMenuBox(
                    expanded = vehicleDropdownExpanded,
                    onExpandedChange = { vehicleDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = vehicleName,
                        onValueChange = {},
                        label = { Text(stringResource(R.string.fuel_vehicle)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                        readOnly = true,
                        singleLine = true
                    )
                    ExposedDropdownMenu(
                        expanded = vehicleDropdownExpanded,
                        onDismissRequest = { vehicleDropdownExpanded = false }
                    ) {
                        vehicles.forEach { vehicle ->
                            DropdownMenuItem(
                                text = { Text(vehicle.name) },
                                onClick = {
                                    selectedVehicleId = vehicle.id
                                    vehicleDropdownExpanded = false
                                    if (editId == null) {
                                        category = ExpenseCategories.defaultCategory(
                                            vehicle.expenseCategoriesJson,
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                com.davidlang.vehicleexpensesautomated.ui.components.CaretEnabledOutlinedTextField(
                    value = vendor,
                    onValueChange = { vendor = it },
                    label = { Text(stringResource(R.string.expense_vendor)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                com.davidlang.vehicleexpensesautomated.ui.components.CaretEnabledOutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.expense_description)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 4,
                )

                var showCurrencyMenu by remember { mutableStateOf(false) }
                val currencySymbols = remember {
                    Currency.getAvailableCurrencies()
                        .map { it.getSymbol(Locale.getDefault()) }
                        .distinct()
                        .sorted()
                }
                com.davidlang.vehicleexpensesautomated.ui.components.CaretEnabledOutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = {
                        Box {
                            Text(
                                text = currencySymbol.take(1),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.clickable { showCurrencyMenu = true }
                            )
                            DropdownMenu(
                                expanded = showCurrencyMenu,
                                onDismissRequest = { showCurrencyMenu = false }
                            ) {
                                currencySymbols.forEach { symbol ->
                                    DropdownMenuItem(
                                        text = { Text(symbol) },
                                        onClick = {
                                            currencySymbol = symbol
                                            showCurrencyMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    showCaretButtons = true,
                )

                ExposedDropdownMenuBox(
                    expanded = categoryMenuExpanded,
                    onExpandedChange = { categoryMenuExpanded = it },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = category.ifBlank {
                            ExpenseCategories.defaultCategory(selectedVehicle?.expenseCategoriesJson)
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.expense_category)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
                        singleLine = true,
                    )
                    ExposedDropdownMenuWithManageFooter(
                        expanded = categoryMenuExpanded,
                        onDismissRequest = { categoryMenuExpanded = false },
                        items = categoryOptions,
                        onItemClick = { cat ->
                            category = cat
                            categoryMenuExpanded = false
                        },
                        manageLabel = stringResource(R.string.expense_manage_categories),
                        onManageClick = {
                            categoryMenuExpanded = false
                            if (selectedVehicle == null) {
                                Toast.makeText(
                                    context,
                                    AppStrings.get(R.string.expense_select_a_vehicle_first),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } else {
                                showManageCategories = true
                            }
                        },
                    )
                }

                com.davidlang.vehicleexpensesautomated.ui.components.CaretEnabledOutlinedTextField(
                    value = odometerText,
                    onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) odometerText = it },
                    label = { Text(stringResource(R.string.expense_odometer_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    showCaretButtons = true,
                )

                if (rowLat != null && rowLon != null) {
                    val expenseKind = LocationLookup.kindForExpenseCategory(category)
                    LocationConfirmBlock(
                        statusLine = locationStatus,
                        name = placeName,
                        address = placeAddress,
                        onNameChange = { placeName = it },
                        onAddressChange = { placeAddress = it },
                        pickerKind = expenseKind,
                        hasCoords = true,
                        onWrongStationClick = if (expenseKind != LocationLookupKind.ADDRESS_ONLY) {
                            { showStationPicker = true }
                        } else {
                            null
                        },
                    )
                }
                if (showStationPicker) {
                    val pla = rowLat
                    val plo = rowLon
                    val expenseKind = LocationLookup.kindForExpenseCategory(category)
                    if (pla != null && plo != null && expenseKind != LocationLookupKind.ADDRESS_ONLY) {
                        StationPickerDialog(
                            lat = pla,
                            lon = plo,
                            kind = expenseKind,
                            onSelect = { picked ->
                                placeName = picked.name
                                placeAddress = picked.address
                                lookupName = picked.name
                                lookupAddress = picked.address
                                lookupSource = "user"
                                showStationPicker = false
                            },
                            onManual = { showStationPicker = false },
                            onDismiss = { showStationPicker = false },
                        )
                    } else {
                        showStationPicker = false
                    }
                }
            }
        }
        ExpenseForm()
    }

    if (showManageCategories && selectedVehicle != null) {
        ManageExpenseCategoriesDialog(
            vehicle = selectedVehicle!!,
            onDismiss = { showManageCategories = false },
            onSave = { updated ->
                scope.launch {
                    vehicleViewModel.updateVehicle(updated)
                    showManageCategories = false
                    // Refresh selection if deleted current category
                    val cats = ExpenseCategories.parse(updated.expenseCategoriesJson)
                    if (cats.none { it.equals(category, ignoreCase = true) }) {
                        category = cats.firstOrNull().orEmpty()
                    }
                }
            },
        )
    }
}


