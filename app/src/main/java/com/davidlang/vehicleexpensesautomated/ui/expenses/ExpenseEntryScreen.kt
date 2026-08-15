package com.davidlang.vehicleexpensesautomated.ui.expenses

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import com.davidlang.vehicleexpensesautomated.ui.util.CameraCaptureProfile
import com.davidlang.vehicleexpensesautomated.ui.util.CameraResolutionPicker
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
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
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

private const val TAG = "ExpenseEntry"

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
    var isPhotoSaving by remember { mutableStateOf(false) }
    var isDownloadingCloud by remember { mutableStateOf(false) }
    var photoStatus by remember { mutableStateOf<String?>(null) }
    var showLiveCamera by remember { mutableStateOf(true) }

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
        title = "Expense entry",
        "Disk = save · white circle = take receipt · gallery icon = pick image · Retake clears the photo.",
        "Tap the currency symbol on amount to change currency.",
        "Time is now (default on create) stamps the save time; uncheck to pick date/time.",
        "Manage categories… stays pinned at the bottom of the category menu " +
            "(scroll the list above if long; select a vehicle first).",
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
            locationStatus = "Offline — place lookup when online"
            lookupName = null
            lookupAddress = null
            lookupSource = null
            return@LaunchedEffect
        }
        locationStatus = "Looking up place…"
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
            locationStatus = "No place found (will retry after save if online)"
        }
    }
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
                    showLiveCamera = expenseLocalMissingOrDead(e.photoUrl, photoStorage) &&
                        !expenseHasArchiveIdentity(e, photoDest?.id)
                    loadedExpense = e
                    loadedId = editId
                } else {
                    loadedExpense = null
                    Toast.makeText(context, "Expense not found", Toast.LENGTH_LONG).show()
                    navController?.popBackStack()
                        ?: navController?.navigate("expenselist") { launchSingleTop = true }
                }
            }
        } else {
            loadedExpense = null
            if (selectedVehicleId == null && vehicles.isNotEmpty()) {
                selectedVehicleId = vehicles.first().id
            }
            if (photoUrl == null) {
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

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            if (prefs.getBoolean("save_expense_photos", true)) {
                photoUrl = uri.toString()
                photoStatus = null
                showLiveCamera = false
                // Gallery: row lat/lon from EXIF only — never current device GPS.
                photoFromGallery = true
                val meta = PhotoExifMetaReader.read(context, uri)
                rowLat = meta.latitude
                rowLon = meta.longitude
                rowAccuracyM = meta.accuracyM
                Toast.makeText(context, "Photo selected", Toast.LENGTH_SHORT).show()
            } else {
                photoUrl = null
                showLiveCamera = true
            }
        }
    }

    fun saveExpense() {
        if (isSaving) return
        if (editId != null && !editLoadReady) {
            Toast.makeText(context, "Still loading expense…", Toast.LENGTH_SHORT).show()
            return
        }
        val vehicleId = selectedVehicleId
        if (vehicleId == null) {
            Toast.makeText(context, "Select a vehicle", Toast.LENGTH_SHORT).show()
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
                    if (editId != null) "Expense updated" else "Expense saved",
                    Toast.LENGTH_SHORT
                ).show()
                navController?.navigate("expenselist") {
                    popUpTo("expenselist") { inclusive = false }
                    launchSingleTop = true
                } ?: navController?.popBackStack()
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "Save failed: ${e.message ?: e}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                isSaving = false
            }
        }
    }

    fun takePicture() {
        if (isPhotoSaving) return
        if (!prefs.getBoolean("save_expense_photos", true)) {
            photoUrl = null
            photoStatus = null
            showLiveCamera = true
            isPhotoSaving = false
            return
        }
        isPhotoSaving = true
        photoStatus = "Saving photo…"
        showLiveCamera = true
        // Camera path: row uses once-per-screen device fix (restore after any gallery pick).
        photoFromGallery = false
        val locForExif = deviceLocation
        rowLat = locForExif?.latitude
        rowLon = locForExif?.longitude
        rowAccuracyM = locForExif?.let { if (it.hasAccuracy()) it.accuracy.toDouble() else null }
        var rotationDegrees = 0
        try {
            val display = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                context.display
            } else {
                @Suppress("DEPRECATION")
                (context.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay
            }
            val rotation = display?.rotation ?: android.view.Surface.ROTATION_0
            imageCapture.targetRotation = rotation
            rotationDegrees = when (rotation) {
                android.view.Surface.ROTATION_0 -> 0
                android.view.Surface.ROTATION_90 -> 90
                android.view.Surface.ROTATION_180 -> 180
                android.view.Surface.ROTATION_270 -> 270
                else -> 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set target rotation", e)
        }

        try {
            val resolver = context.contentResolver
            val contentValues = android.content.ContentValues().apply {
                put(
                    android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                    "expense_${System.currentTimeMillis()}.jpg"
                )
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(
                        android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_DCIM + "/Camera"
                    )
                }
            }
            val captureMetadata = ImageCapture.Metadata().apply {
                location = locForExif
            }
            val outputOptions = ImageCapture.OutputFileOptions.Builder(
                resolver,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ).setMetadata(captureMetadata).build()

            val orientForExif = rotationDegrees
            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        val savedUri = output.savedUri
                        Log.i(TAG, "Expense photo saved: $savedUri")
                        if (savedUri == null) {
                            photoStatus = "Photo save failed"
                            Toast.makeText(
                                context,
                                "Could not save expense photo to Camera roll: missing MediaStore URI",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            PhotoExifWriter.writeGpsAndOrientation(
                                context,
                                savedUri,
                                locForExif,
                                orientForExif,
                                timestampMs = System.currentTimeMillis(),
                                userComment = "ve:tag=expense",
                            )
                            photoUrl = savedUri.toString()
                            photoStatus = null
                            showLiveCamera = false
                            Toast.makeText(context, "Photo saved to Camera", Toast.LENGTH_SHORT).show()
                        }
                        isPhotoSaving = false
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "Expense photo capture failed", exception)
                        photoStatus = "Photo save failed"
                        isPhotoSaving = false
                        Toast.makeText(
                            context,
                            "Could not save expense photo to Camera roll: ${exception.message ?: exception}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Expense takePicture setup failed", e)
            photoStatus = "Photo save failed"
            isPhotoSaving = false
            Toast.makeText(
                context,
                "Could not save expense photo to Camera roll: ${e.message ?: e}",
                Toast.LENGTH_LONG
            ).show()
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
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
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
                        "Receipt is in cloud backup only",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = {
                            val entry = loadedExpense ?: return@Button
                            scope.launch {
                                isDownloadingCloud = true
                                photoStatus = "Fetching image…"
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
                                        Toast.makeText(context, "Image fetched", Toast.LENGTH_SHORT).show()
                                    } else {
                                        photoStatus = "Fetch failed"
                                        Toast.makeText(context, "Could not fetch image", Toast.LENGTH_LONG).show()
                                    }
                                } catch (e: Exception) {
                                    photoStatus = "Fetch failed"
                                    Toast.makeText(context, e.message ?: "Fetch failed", Toast.LENGTH_LONG).show()
                                } finally {
                                    isDownloadingCloud = false
                                }
                            }
                        },
                        enabled = !isDownloadingCloud,
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Text(if (isDownloadingCloud) "Fetching…" else "Fetch image from archive")
                    }
                }
            } else if (showLiveCamera || localPhotoMissing) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    imageCapture = imageCapture,
                    onImageCaptured = { proxy -> proxy.close() },
                    onZoomControlChanged = { zoomControl = it }
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
                                            "${ratio.toLong()}x"
                                        } else {
                                            "${ratio}x"
                                        },
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                var showZoom by remember(photoUrl) { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { showZoom = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(photoUrl),
                        contentDescription = "Expense photo — tap to zoom",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                    Text(
                        "Tap to zoom",
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
                        title = "Expense photo",
                        onDismiss = { showZoom = false },
                    )
                }
            }
        }

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
                    contentDescription = "Save expense",
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(
                onClick = {
                    showLiveCamera = true
                    takePicture()
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
                onClick = { pickImageLauncher.launch("image/*") },
                enabled = !isPhotoSaving
            ) {
                Icon(
                    imageVector = Icons.Filled.PhotoLibrary,
                    contentDescription = "Pick picture from gallery",
                    modifier = Modifier.size(32.dp)
                )
            }

            if (photoUrl != null) {
                TextButton(
                    onClick = {
                        photoUrl = null
                        showLiveCamera = true
                        photoStatus = null
                    },
                    enabled = !isPhotoSaving,
                ) {
                    Text("Retake")
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
                Text("Time is now", style = MaterialTheme.typography.bodyMedium)
            }
            if (!timeIsNow) {
                AppDateTimeField(
                    label = "Date: ${dateFmt.format(Date(date))}",
                    onClick = { showDatePicker = true },
                )
            }

            val vehicleName = vehicles.find { it.id == selectedVehicleId }?.name ?: "Select vehicle"
            ExposedDropdownMenuBox(
                expanded = vehicleDropdownExpanded,
                onExpandedChange = { vehicleDropdownExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = vehicleName,
                    onValueChange = {},
                    label = { Text("Vehicle") },
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
                label = { Text("Vendor") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            com.davidlang.vehicleexpensesautomated.ui.components.CaretEnabledOutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
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
                    label = { Text("Category") },
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
                    manageLabel = "Manage categories…",
                    onManageClick = {
                        categoryMenuExpanded = false
                        if (selectedVehicle == null) {
                            Toast.makeText(
                                context,
                                "Select a vehicle first",
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
                label = { Text("Odometer (optional)") },
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


