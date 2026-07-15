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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry
import com.davidlang.vehicleexpensesautomated.data.sync.CloudManifest
import com.davidlang.vehicleexpensesautomated.data.sync.SyncDestinationStore
import com.davidlang.vehicleexpensesautomated.ui.components.CameraPreview
import com.davidlang.vehicleexpensesautomated.ui.components.CameraZoomControl
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
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vehicle_settings", android.content.Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val editId = (mode as? ExpenseEntryMode.Edit)?.id

    val vehicles by vehicleViewModel.vehicles.collectAsState(initial = emptyList())
    var selectedVehicleId by rememberSaveable { mutableStateOf<Int?>(null) }
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
    var category by rememberSaveable { mutableStateOf("Other") }
    var odometerText by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var photoUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val photoDest = remember { SyncDestinationStore(context).photoDestination() }
    val hasCloudOnlyReceipt = remember(loadedExpense, photoUrl, photoDest) {
        val destId = photoDest?.id ?: return@remember false
        photoUrl == null &&
            CloudManifest.hasRole(loadedExpense?.cloudManifest, destId, CloudManifest.ROLE_EXPENSE_RECEIPT)
    }

    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    // Prefill for edit; auto-select first vehicle for create
    LaunchedEffect(mode, vehicles) {
        if (editId != null) {
            if (loadedId != editId) {
                val entry = viewModel.getExpenseById(editId)
                if (entry != null) {
                    selectedVehicleId = entry.vehicleId
                    amount = if (entry.amount == 0.0) "" else entry.amount.toString()
                    currencySymbol = CurrencyCodes.displaySymbol(
                        entry.currency,
                        defaultCurrencySymbol,
                    )
                    vendor = entry.vendor
                    description = entry.description
                    category = entry.category
                    odometerText = entry.odometer?.toString() ?: ""
                    date = entry.date
                    photoUrl = entry.photoUrl
                    showLiveCamera = entry.photoUrl == null
                    loadedExpense = entry
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
            if (editId == null && photoUrl == null) {
                showLiveCamera = true
            }
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
        // copy() from loadedExpense preserves photoUrl, lat/long, location, cloudManifest
        val base = loadedExpense
        val toSave = (base ?: ExpenseEntry(
            vehicleId = vehicleId,
            amount = 0.0,
            description = "",
            date = date
        )).copy(
            id = editId ?: 0L,
            vehicleId = vehicleId,
            amount = amountVal,
            currency = storedCurrency,
            description = description,
            vendor = vendor,
            odometer = odo,
            category = category,
            date = date,
            photoUrl = if (prefs.getBoolean("save_expense_photos", true)) photoUrl else null
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
        try {
            val display = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                context.display
            } else {
                @Suppress("DEPRECATION")
                (context.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay
            }
            val rotation = display?.rotation ?: android.view.Surface.ROTATION_0
            imageCapture.targetRotation = rotation
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
            val outputOptions = ImageCapture.OutputFileOptions.Builder(
                resolver,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ).build()

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
            if (hasCloudOnlyReceipt && photoUrl == null) {
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
                                photoStatus = "Downloading receipt…"
                                try {
                                    val local = viewModel.downloadExpensePhoto(entry)
                                    if (local != null) {
                                        photoUrl = local
                                        showLiveCamera = false
                                        photoStatus = null
                                        Toast.makeText(context, "Receipt downloaded", Toast.LENGTH_SHORT).show()
                                    } else {
                                        photoStatus = "Download failed"
                                        Toast.makeText(context, "Could not download receipt", Toast.LENGTH_LONG).show()
                                    }
                                } catch (e: Exception) {
                                    photoStatus = "Download failed"
                                    Toast.makeText(context, e.message ?: "Download failed", Toast.LENGTH_LONG).show()
                                } finally {
                                    isDownloadingCloud = false
                                }
                            }
                        },
                        enabled = !isDownloadingCloud,
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Text(if (isDownloadingCloud) "Downloading…" else "Download receipt")
                    }
                }
            } else if (showLiveCamera || photoUrl == null) {
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
                ZoomPanPhotoViewer(
                    photoUrl = photoUrl!!,
                    modifier = Modifier.fillMaxSize()
                )
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
                    imageVector = ExpenseSaveIcon,
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
                    imageVector = ExpensePhotoLibraryIcon,
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
            Text(
                if (editId != null) "Edit Expense" else "New Expense",
                style = MaterialTheme.typography.titleLarge
            )

            // Date display + picker
            OutlinedTextField(
                value = dateFmt.format(Date(date)),
                onValueChange = {},
                label = { Text("Date") },
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    TextButton(onClick = { showDatePicker = true }) { Text("Change") }
                }
            )

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
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = vendor,
                onValueChange = { vendor = it },
                label = { Text("Vendor") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth()
            )

            var showCurrencyMenu by remember { mutableStateOf(false) }
            val currencySymbols = remember {
                Currency.getAvailableCurrencies()
                    .map { it.getSymbol(Locale.getDefault()) }
                    .distinct()
                    .sorted()
            }
            OutlinedTextField(
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
                singleLine = true
            )

            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text("Category") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = odometerText,
                onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) odometerText = it },
                label = { Text("Odometer (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

/** View-only pinch zoom + pan (ManageVehicles-style). */
@Composable
private fun ZoomPanPhotoViewer(
    photoUrl: String,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 10f)
                    offset += pan
                }
            }
    ) {
        Image(
            painter = rememberAsyncImagePainter(photoUrl),
            contentDescription = "Expense photo",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                ),
            contentScale = ContentScale.Fit
        )
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SmallFloatingActionButton(
                onClick = { scale = (scale * 1.2f).coerceIn(1f, 10f) },
                containerColor = Color.White.copy(alpha = 0.7f)
            ) { Text("+") }
            SmallFloatingActionButton(
                onClick = { scale = (scale / 1.2f).coerceIn(1f, 10f) },
                containerColor = Color.White.copy(alpha = 0.7f)
            ) { Text("-") }
        }
    }
}

private var _expenseSaveIcon: ImageVector? = null
private val ExpenseSaveIcon: ImageVector
    get() {
        _expenseSaveIcon?.let { return it }
        _expenseSaveIcon = ImageVector.Builder(
            name = "ExpenseSave",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(17f, 3f)
                horizontalLineTo(5f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(14f)
                curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f)
                horizontalLineToRelative(14f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(7f)
                lineToRelative(-4f, -4f)
                close()
                moveTo(12f, 19f)
                curveToRelative(-1.66f, 0f, -3f, -1.34f, -3f, -3f)
                reflectiveCurveToRelative(1.34f, -3f, 3f, -3f)
                reflectiveCurveToRelative(3f, 1.34f, 3f, 3f)
                reflectiveCurveToRelative(-1.34f, 3f, -3f, 3f)
                close()
                moveTo(15f, 9f)
                horizontalLineTo(5f)
                verticalLineTo(5f)
                horizontalLineToRelative(10f)
                verticalLineTo(9f)
                close()
            }
        }.build()
        return _expenseSaveIcon!!
    }

private var _expensePhotoLibraryIcon: ImageVector? = null
private val ExpensePhotoLibraryIcon: ImageVector
    get() {
        _expensePhotoLibraryIcon?.let { return it }
        _expensePhotoLibraryIcon = ImageVector.Builder(
            name = "ExpensePhotoLibrary",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(22f, 16f)
                verticalLineTo(4f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                horizontalLineTo(8f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(12f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(12f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                close()
                moveTo(11.5f, 9f)
                lineToRelative(2.03f, 2.71f)
                lineTo(16f, 9f)
                lineToRelative(4f, 5f)
                horizontalLineTo(8f)
                lineToRelative(3.5f, -5f)
                close()
                moveTo(2f, 6f)
                verticalLineToRelative(14f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(14f)
                verticalLineToRelative(-2f)
                horizontalLineTo(4f)
                verticalLineTo(6f)
                horizontalLineTo(2f)
                close()
            }
        }.build()
        return _expensePhotoLibraryIcon!!
    }
