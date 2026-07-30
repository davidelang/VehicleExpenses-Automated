package com.davidlang.vehicleexpensesautomated.data.sync

import android.content.Context
import android.content.Intent
import android.util.Log
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry
import com.davidlang.vehicleexpensesautomated.data.model.ExpensePhotoUrls
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.repository.ExpenseEntryRepository
import com.davidlang.vehicleexpensesautomated.data.repository.FuelEntryRepository
import com.davidlang.vehicleexpensesautomated.data.repository.VehicleRepository
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoStorageManager
import com.davidlang.vehicleexpensesautomated.data.storage.PhotoType
import com.davidlang.vehicleexpensesautomated.ui.util.FuelPhotoJson
import com.davidlang.vehicleexpensesautomated.ui.util.FuelPhotoRef
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class PhotoSyncMode {
    /** Manual UI Sync now: full reconcile across all vehicles/fuel/expenses. */
    FULL,
    /** Background worker: only items already classified as pending. */
    PENDING_ONLY,
}

data class PhotoBackupResult(
    val success: Boolean,
    val message: String,
    val uploads: Int = 0,
    val downloads: Int = 0,
    val needsRemoteConsent: Boolean = false,
    val recoveryIntent: Intent? = null,
)

@Singleton
class PhotoBackupCoordinator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val driveAuth: GoogleDriveAuth,
    private val photoStorage: PhotoStorageManager,
    private val backendRegistry: PhotoBackendRegistry,
    private val vehicleRepository: VehicleRepository,
    private val fuelRepository: FuelEntryRepository,
    private val expenseRepository: ExpenseEntryRepository,
    private val spreadsheetSyncCoordinator: SpreadsheetSyncCoordinator,
    private val photoBackupManager: PhotoBackupManager,
    private val oneDriveSetup: RcloneOneDriveSetup,
) {

    /** Write → read metadata → delete test file in configured Drive folder. */
    suspend fun testConnection(accountHint: String?): PhotoBackupResult =
        testConnection(accountHint, dest = null)

    suspend fun testConnection(accountHint: String?, dest: PhotoDestination?): PhotoBackupResult =
        withContext(Dispatchers.IO) {
            val ctx = resolveContext(accountHint, dest) ?: return@withContext notConfiguredOrAuth(dest)
            prepareRcloneDestIfNeeded(ctx.dest, ctx.hint)
            val result = ctx.backend.testConnection(ctx.dest, ctx.hint)
            if (!result.success && ctx.dest.provider == PhotoProvider.ONEDRIVE) {
                return@withContext result.copy(message = mapOneDriveErrorMessage(result.message))
            }
            if (result.success && ctx.dest.provider == PhotoProvider.GOOGLE_DRIVE) {
                val folderId = ctx.backend.resolveFolderId(ctx.dest, ctx.hint)
                if (folderId.isNotBlank()) persistFolderId(ctx.store, ctx.dest, folderId)
            }
            result
        }

    /** Upload vehicle ref + cleaned ref for one vehicle; updates cloudManifest. */
    suspend fun uploadVehicleAssets(vehicle: Vehicle, accountHint: String? = null): Int =
        withContext(Dispatchers.IO) {
            val ctx = resolveContext(accountHint) ?: return@withContext 0
            prepareRcloneDestIfNeeded(ctx.dest, ctx.hint)
            uploadVehicleAssetsInternal(ctx, vehicle)
        }

    /** Phase 9–11: photo sync — [mode] FULL (manual) scans everything; PENDING_ONLY (worker) pending queue only. */
    suspend fun syncNow(
        accountHint: String? = null,
        mode: PhotoSyncMode = PhotoSyncMode.FULL,
        onProgress: SyncProgressListener? = null,
    ): PhotoBackupResult = withContext(Dispatchers.IO) {
        val store = SyncDestinationStore(context)
        val failureStore = SyncFailureStore(context)
        val destinations = when (mode) {
            PhotoSyncMode.FULL -> store.configuredPhoto()
            PhotoSyncMode.PENDING_ONLY -> store.enabledPhoto()
        }
        if (destinations.isEmpty()) {
            if (mode == PhotoSyncMode.PENDING_ONLY) {
                return@withContext PhotoBackupResult(false, "No enabled photo destinations")
            }
            val ctx = resolveContext(accountHint) ?: return@withContext notConfiguredOrAuth()
            onProgress?.onStatus("Syncing ${photoDestLabel(ctx.dest)}…")
            val single = syncSingleDestination(ctx, mode, sharedRebinds = 0)
            recordPhotoResult(failureStore, ctx.dest.id, photoDestLabel(ctx.dest), single)
            recountPendingAll(store)
            if (single.success && (single.uploads > 0 || single.downloads > 0)) {
                bestEffortSheetSync(accountHint)
            }
            return@withContext single
        }

        var sharedRebinds = 0
        if (mode == PhotoSyncMode.PENDING_ONLY) {
            sharedRebinds = rebindAllVehiclesOnDisk()
        }

        val results = mutableListOf<Pair<String, PhotoBackupResult>>()
        var totalUploads = 0
        var totalDownloads = 0
        var anyFailure = false
        var manifestChangedAny = false
        var consentResult: PhotoBackupResult? = null

        onProgress?.onStatus("Starting photo backup (${destinations.size} destinations)…")
        for (dest in destinations) {
            val hint = accountHint?.takeIf { it.isNotBlank() } ?: dest.accountHint
            val ctx = resolveContext(hint, dest)
            val label = photoDestLabel(dest)
            if (ctx == null) {
                onProgress?.onStatus("$label: not configured")
                val fail = notConfiguredOrAuth(dest)
                recordPhotoResult(failureStore, dest.id, label, fail)
                results.add(label to fail)
                anyFailure = true
                continue
            }
            onProgress?.onStatus("Syncing $label…")
            val result = syncSingleDestination(ctx, mode, sharedRebinds = sharedRebinds)
            recordPhotoResult(failureStore, dest.id, label, result)
            results.add(label to result)
            onProgress?.onStatus(
                if (result.success) {
                    "$label done (${result.uploads} up, ${result.downloads} down)"
                } else {
                    "$label failed"
                },
            )
            if (result.success) {
                totalUploads += result.uploads
                totalDownloads += result.downloads
                if (result.uploads > 0 || result.downloads > 0) manifestChangedAny = true
            } else {
                anyFailure = true
                if (result.needsRemoteConsent && consentResult == null) {
                    consentResult = result
                }
            }
        }

        recountPendingAll(store)

        if (manifestChangedAny) {
            bestEffortSheetSync(accountHint)
        }

        val message = SyncResultMessages.photoSummary(
            results = results,
            anyFailure = anyFailure,
            totalUploads = totalUploads,
            totalDownloads = totalDownloads,
        )
        if (consentResult != null && anyFailure) {
            val consentLabel = results.first { !it.second.success && it.second.needsRemoteConsent }.first
            val finalMessage = SyncResultMessages.consentWithDest(consentLabel, consentResult.message)
            onProgress?.onStatus(finalMessage)
            return@withContext consentResult.copy(message = finalMessage)
        }
        onProgress?.onStatus(message)
        PhotoBackupResult(
            success = !anyFailure,
            message = message,
            uploads = totalUploads,
            downloads = totalDownloads,
        )
    }

    private suspend fun syncSingleDestination(
        ctx: SyncContext,
        mode: PhotoSyncMode,
        sharedRebinds: Int,
    ): PhotoBackupResult {
        return try {
            prepareRcloneDestIfNeeded(ctx.dest, ctx.hint)
            val folderId = ctx.backend.resolveFolderId(ctx.dest, ctx.hint)
            val destWithFolder = if (folderId.isNotBlank()) {
                persistFolderId(ctx.store, ctx.dest, folderId)
                ctx.dest.copy(folderId = folderId)
            } else {
                ctx.dest
            }

            if (mode == PhotoSyncMode.PENDING_ONLY) {
                val breakdown = computePendingBreakdown(destWithFolder)
                Log.i(
                    TAG,
                    "Photo sync pending-only: upload=${breakdown.upload} download=${breakdown.download} " +
                        "rebind=$sharedRebinds destId=${destWithFolder.id}",
                )
                if (breakdown.total == 0 && sharedRebinds == 0) {
                    return PhotoBackupResult(
                        success = true,
                        message = "Photo sync complete: no pending work",
                    )
                }
            }

            var uploads = 0
            var downloads = 0
            var manifestChanged = false

            val vehicles = vehicleRepository.getAllIncludingDeleted().filter { !it.deleted }
            for (vehicle in vehicles) {
                val fresh = rebindOnDiskVehicleRefs(vehicleRepository.findBySyncId(vehicle.syncId) ?: vehicle)
                if (mode == PhotoSyncMode.PENDING_ONLY && !vehicleHasPendingWork(fresh, destWithFolder)) {
                    continue
                }
                val uploaded = uploadVehicleAssetsInternal(
                    ctx.copy(dest = destWithFolder),
                    fresh,
                )
                if (uploaded > 0) manifestChanged = true
                uploads += uploaded

                val (downloaded, changed) = downloadVehicleAssetsIfNeeded(
                    ctx.copy(dest = destWithFolder),
                    fresh,
                )
                downloads += downloaded
                if (changed) manifestChanged = true
            }

            for (fuel in fuelRepository.getAllIncludingDeleted().filter { !it.deleted }) {
                if (mode == PhotoSyncMode.PENDING_ONLY && !fuelHasPendingUpload(fuel, destWithFolder)) {
                    continue
                }
                val count = uploadFuelAssets(ctx.copy(dest = destWithFolder), fuel)
                if (count > 0) manifestChanged = true
                uploads += count
            }

            for (expense in expenseRepository.getAllIncludingDeleted().filter { !it.deleted }) {
                if (mode == PhotoSyncMode.PENDING_ONLY &&
                    !expenseNeedsUpload(expense, destWithFolder)
                ) {
                    continue
                }
                val count = uploadExpenseAsset(ctx.copy(dest = destWithFolder), expense)
                if (count > 0) manifestChanged = true
                uploads += count
                // D1: never bulk-download expense receipts (or fuel photos) on Sync now / worker.
                // On-demand: downloadExpensePhoto / downloadFuelPhoto from UI surfaces only.
            }

            val message = when {
                mode == PhotoSyncMode.PENDING_ONLY && uploads == 0 && downloads == 0 && sharedRebinds > 0 ->
                    "Photo sync complete: $sharedRebinds vehicle path(s) rebound"
                else ->
                    "Photo sync complete: $uploads uploaded, $downloads downloaded"
            }
            PhotoBackupResult(
                success = true,
                message = message,
                uploads = uploads,
                downloads = downloads,
            )
        } catch (e: Exception) {
            handleError("Photo sync failed", e, ctx.dest)
        }
    }

    /** Enqueue background photo backup (non-blocking). */
    fun enqueueAfterSave() {
        photoBackupManager.triggerImmediateBackup()
    }

    /** Phase 16: recompute pending upload/download count for Settings badge (sum across all configured dests). */
    suspend fun recountPending(): Int = withContext(Dispatchers.IO) {
        recountPendingAll(SyncDestinationStore(context))
    }

    private suspend fun recountPendingAll(store: SyncDestinationStore): Int {
        val dests = store.allPhoto().filter { store.isPhotoConfigured(it) }
        if (dests.isEmpty()) {
            store.setPendingCount(0)
            return 0
        }
        var total = 0
        for (dest in dests) {
            total += computePendingBreakdown(dest).total
        }
        store.setPendingCount(total)
        return total
    }

    private suspend fun uploadVehicleAssetsInternal(ctx: SyncContext, vehicle: Vehicle): Int {
        if (vehicle.syncId.isBlank()) return 0
        var uploads = 0
        var manifest = CloudManifest.stripObsoleteRoles(vehicle.cloudManifest) ?: vehicle.cloudManifest
        var manifestStrippedOnly = manifest != vehicle.cloudManifest
        val entries = mutableListOf<CloudManifest.Entry>()
        var dest = ctx.dest

        val refPath = vehicle.referenceDashPhotoUrl
        if (photoStorage.isLocalReadable(refPath) &&
            !CloudManifest.hasEntryForDest(manifest, dest.id, CloudManifest.ROLE_VEHICLE_REF)
        ) {
            val name = "vehicle_${vehicle.syncId}_ref.jpg"
            val result = ctx.backend.uploadFile(
                dest, ctx.hint, refPath!!, name, "image/jpeg",
                CloudManifest.fileIdForDest(manifest, dest.id, CloudManifest.ROLE_VEHICLE_REF),
            )
            if (result.resolvedFolderId.isNotBlank()) dest = dest.copy(folderId = result.resolvedFolderId)
            entries.add(
                CloudManifest.Entry(
                    destId = dest.id,
                    provider = ctx.backend.manifestProvider(),
                    fileId = result.fileId,
                    name = name,
                    role = CloudManifest.ROLE_VEHICLE_REF,
                    mimeType = "image/jpeg",
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            uploads++
        }

        val cleanedPath = vehicle.cleanedReferenceDashPhotoUrl
        if (photoStorage.isLocalReadable(cleanedPath) &&
            !CloudManifest.hasEntryForDest(manifest, dest.id, CloudManifest.ROLE_VEHICLE_REF_CLEANED)
        ) {
            val name = "vehicle_${vehicle.syncId}_ref_cleaned.jpg"
            val result = ctx.backend.uploadFile(
                dest, ctx.hint, cleanedPath!!, name, "image/jpeg",
                CloudManifest.fileIdForDest(manifest, dest.id, CloudManifest.ROLE_VEHICLE_REF_CLEANED),
            )
            if (result.resolvedFolderId.isNotBlank()) dest = dest.copy(folderId = result.resolvedFolderId)
            entries.add(
                CloudManifest.Entry(
                    destId = dest.id,
                    provider = ctx.backend.manifestProvider(),
                    fileId = result.fileId,
                    name = name,
                    role = CloudManifest.ROLE_VEHICLE_REF_CLEANED,
                    mimeType = "image/jpeg",
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            uploads++
        }

        if (entries.isNotEmpty()) {
            manifest = CloudManifest.merge(manifest, entries)
            vehicleRepository.updateVehiclePreservingTimestamp(
                vehicle.copy(cloudManifest = manifest),
            )
            persistFolderId(ctx.store, ctx.dest, dest.folderId)
        } else if (manifestStrippedOnly) {
            vehicleRepository.updateVehiclePreservingTimestamp(
                vehicle.copy(cloudManifest = manifest),
            )
        }
        return uploads
    }

    /** Phase 10: fuel dash/pump uploads; clears local photoUrl when save_fuel_photos is off. */
    private suspend fun uploadFuelAssets(ctx: SyncContext, fuel: FuelEntry): Int {
        if (fuel.syncId.isBlank() || fuel.photoUrl.isNullOrBlank()) return 0
        val saveLocal = context.getSharedPreferences(SyncDestinationStore.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("save_fuel_photos", true)
        var uploads = 0
        var manifest = CloudManifest.stripObsoleteRoles(fuel.cloudManifest) ?: fuel.cloudManifest
        val manifestStrippedOnly = manifest != fuel.cloudManifest
        val photos = parseFuelPhotos(fuel.photoUrl) ?: return 0

        for ((tag, uri) in photos) {
            if (!photoStorage.isLocalReadable(uri)) continue
            // dash→fuel_dash; pump→fuel_pump; pump_2→fuel_pump_2 (not dash)
            val role = fuelRoleForTag(tag)
            if (CloudManifest.hasEntryForDest(manifest, ctx.dest.id, role)) continue
            val name = "fuel_${fuel.syncId}_$tag.jpg"
            val result = ctx.backend.uploadFile(
                ctx.dest, ctx.hint, uri, name, "image/jpeg",
                CloudManifest.fileIdForDest(manifest, ctx.dest.id, role),
            )
            manifest = CloudManifest.merge(
                manifest,
                listOf(
                    CloudManifest.Entry(
                        destId = ctx.dest.id,
                        provider = ctx.backend.manifestProvider(),
                        fileId = result.fileId,
                        name = name,
                        role = role,
                        mimeType = "image/jpeg",
                        updatedAt = System.currentTimeMillis(),
                    ),
                ),
            )
            uploads++
            if (result.resolvedFolderId.isNotBlank()) {
                persistFolderId(ctx.store, ctx.dest, result.resolvedFolderId)
            }
        }

        if (uploads > 0) {
            val newPhotoUrl = if (saveLocal) fuel.photoUrl else null
            fuelRepository.updateFuelEntryPreservingTimestamp(
                fuel.copy(cloudManifest = manifest, photoUrl = newPhotoUrl),
            )
        } else if (manifestStrippedOnly) {
            fuelRepository.updateFuelEntryPreservingTimestamp(fuel.copy(cloudManifest = manifest))
        }
        return uploads
    }

    private suspend fun uploadExpenseAsset(ctx: SyncContext, expense: ExpenseEntry): Int {
        if (expense.syncId.isBlank()) return 0
        val saveLocal = context.getSharedPreferences(SyncDestinationStore.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("save_expense_photos", true)
        val pages = ExpensePhotoUrls.parse(expense.photoUrl)
        if (pages.isEmpty()) return 0
        var uploads = 0
        var manifest = CloudManifest.stripObsoleteRoles(expense.cloudManifest) ?: expense.cloudManifest
        val manifestStrippedOnly = manifest != expense.cloudManifest
        if (manifestStrippedOnly) {
            expenseRepository.updateExpenseEntryPreservingTimestamp(expense.copy(cloudManifest = manifest))
        }

        for (page in pages) {
            if (!photoStorage.isLocalReadable(page.uri)) continue
            val role = ExpensePhotoUrls.roleForPage(page.index)
            if (CloudManifest.hasEntryForDest(manifest, ctx.dest.id, role)) continue
            val name = ExpensePhotoUrls.remoteFileName(expense.syncId, page.index)
            val result = ctx.backend.uploadFile(
                ctx.dest, ctx.hint, page.uri, name, "image/jpeg",
                CloudManifest.fileIdForDest(manifest, ctx.dest.id, role),
            )
            manifest = CloudManifest.merge(
                manifest,
                listOf(
                    CloudManifest.Entry(
                        destId = ctx.dest.id,
                        provider = ctx.backend.manifestProvider(),
                        fileId = result.fileId,
                        name = name,
                        role = role,
                        mimeType = "image/jpeg",
                        updatedAt = System.currentTimeMillis(),
                    ),
                ),
            )
            uploads++
            if (result.resolvedFolderId.isNotBlank()) {
                persistFolderId(ctx.store, ctx.dest, result.resolvedFolderId)
            }
        }

        if (uploads > 0) {
            expenseRepository.updateExpenseEntryPreservingTimestamp(
                expense.copy(
                    cloudManifest = manifest,
                    photoUrl = if (saveLocal) expense.photoUrl else null,
                ),
            )
        } else if (manifestStrippedOnly) {
            expenseRepository.updateExpenseEntryPreservingTimestamp(expense.copy(cloudManifest = manifest))
        }
        return uploads
    }

    /** Bind Room paths when deterministic ref files already exist under vehicle_refs/. */
    private suspend fun rebindOnDiskVehicleRefs(vehicle: Vehicle): Vehicle {
        if (vehicle.syncId.isBlank()) return vehicle
        var updated = vehicle
        var changed = false

        if (!photoStorage.isLocalReadable(updated.referenceDashPhotoUrl)) {
            photoStorage.existingVehicleRefPath(vehicle.syncId, cleaned = false)?.let { path ->
                Log.i(TAG, "Rebind vehicle_ref from on-disk file syncId=${vehicle.syncId}")
                updated = updated.copy(
                    referenceDashPhotoUrl = path,
                    cleanedReferenceDashPhotoUrl = updated.cleanedReferenceDashPhotoUrl ?: path,
                )
                changed = true
            }
        }
        if (!photoStorage.isLocalReadable(updated.cleanedReferenceDashPhotoUrl)) {
            photoStorage.existingVehicleRefPath(vehicle.syncId, cleaned = true)?.let { path ->
                Log.i(TAG, "Rebind vehicle_ref_cleaned from on-disk file syncId=${vehicle.syncId}")
                updated = updated.copy(cleanedReferenceDashPhotoUrl = path)
                changed = true
            }
        }

        if (changed) {
            vehicleRepository.updateVehiclePreservingTimestamp(updated)
        }
        return updated
    }

    /** Phase 11: pull vehicle ref/cleaned when manifest has fileId but local missing. */
    private suspend fun downloadVehicleAssetsIfNeeded(ctx: SyncContext, vehicle: Vehicle): Pair<Int, Boolean> {
        if (vehicle.syncId.isBlank()) return 0 to false
        var downloads = 0
        var updated = rebindOnDiskVehicleRefs(vehicle)
        var changed = updated != vehicle
        val downloadBindings = mutableListOf<CloudManifest.DownloadBinding>()

        val refFileId = CloudManifest.getFileId(
            vehicle.cloudManifest,
            ctx.dest.id,
            CloudManifest.ROLE_VEHICLE_REF,
            ctx.backend.manifestProvider(),
        )
        if (refFileId == null) {
            Log.d(TAG, "Skip vehicle_ref download syncId=${vehicle.syncId}: no manifest fileId")
        } else if (photoStorage.isLocalReadable(updated.referenceDashPhotoUrl)) {
            Log.d(TAG, "Skip vehicle_ref download syncId=${vehicle.syncId}: local file present")
        } else {
            val viaFallback = !CloudManifest.hasEntryForDest(
                vehicle.cloudManifest, ctx.dest.id, CloudManifest.ROLE_VEHICLE_REF,
            )
            Log.i(
                TAG,
                "Downloading vehicle_ref syncId=${vehicle.syncId} fileId=$refFileId destId=${ctx.dest.id} fallback=$viaFallback",
            )
            val local = ctx.backend.downloadFile(
                ctx.dest,
                ctx.hint,
                refFileId,
                "vehicle_${vehicle.syncId}_ref.jpg",
                useMediaStore = false,
                photoType = PhotoType.DASH_REFERENCE,
            )
            updated = updated.copy(
                referenceDashPhotoUrl = local,
                cleanedReferenceDashPhotoUrl = updated.cleanedReferenceDashPhotoUrl ?: local,
            )
            downloadBindings.add(
                CloudManifest.DownloadBinding(
                    role = CloudManifest.ROLE_VEHICLE_REF,
                    fileId = refFileId,
                    name = "vehicle_${vehicle.syncId}_ref.jpg",
                ),
            )
            downloads++
            changed = true
        }

        val cleanedFileId = CloudManifest.getFileId(
            vehicle.cloudManifest,
            ctx.dest.id,
            CloudManifest.ROLE_VEHICLE_REF_CLEANED,
            ctx.backend.manifestProvider(),
        )
        if (cleanedFileId == null) {
            Log.d(TAG, "Skip vehicle_ref_cleaned download syncId=${vehicle.syncId}: no manifest fileId")
        } else if (photoStorage.isLocalReadable(updated.cleanedReferenceDashPhotoUrl)) {
            Log.d(TAG, "Skip vehicle_ref_cleaned download syncId=${vehicle.syncId}: local file present")
        } else {
            val viaFallback = !CloudManifest.hasEntryForDest(
                vehicle.cloudManifest, ctx.dest.id, CloudManifest.ROLE_VEHICLE_REF_CLEANED,
            )
            Log.i(
                TAG,
                "Downloading vehicle_ref_cleaned syncId=${vehicle.syncId} fileId=$cleanedFileId destId=${ctx.dest.id} fallback=$viaFallback",
            )
            val local = ctx.backend.downloadFile(
                ctx.dest,
                ctx.hint,
                cleanedFileId,
                "vehicle_${vehicle.syncId}_ref_cleaned.jpg",
                useMediaStore = false,
                photoType = PhotoType.DASH_REFERENCE,
            )
            updated = updated.copy(cleanedReferenceDashPhotoUrl = local)
            downloadBindings.add(
                CloudManifest.DownloadBinding(
                    role = CloudManifest.ROLE_VEHICLE_REF_CLEANED,
                    fileId = cleanedFileId,
                    name = "vehicle_${vehicle.syncId}_ref_cleaned.jpg",
                ),
            )
            downloads++
            changed = true
        }

        if (downloadBindings.isNotEmpty()) {
            val merged = CloudManifest.bindLocalDestAfterDownload(
                updated.cloudManifest,
                ctx.dest.id,
                downloadBindings,
                ctx.backend.manifestProvider(),
            )
            if (merged != updated.cloudManifest) {
                Log.i(
                    TAG,
                    "Bound local dest ${ctx.dest.id} for ${downloadBindings.size} vehicle image role(s) syncId=${vehicle.syncId}",
                )
                updated = updated.copy(cloudManifest = merged)
                changed = true
            }
        }

        if (changed) {
            vehicleRepository.updateVehiclePreservingTimestamp(updated)
        }
        return downloads to changed
    }

    /** Best-effort batch download after spreadsheet vehicle merge; failures log only. */
    suspend fun downloadMissingVehicleAssets(accountHint: String? = null): Int = withContext(Dispatchers.IO) {
        val ctx = resolveContext(accountHint)
        if (ctx == null) {
            Log.d(TAG, "Skip post-sync vehicle downloads: photo destination not configured or auth missing")
            return@withContext 0
        }
        prepareRcloneDestIfNeeded(ctx.dest, ctx.hint)
        var total = 0
        for (vehicle in vehicleRepository.getAllIncludingDeleted().filter { !it.deleted && it.syncId.isNotBlank() }) {
            try {
                val fresh = rebindOnDiskVehicleRefs(vehicleRepository.findBySyncId(vehicle.syncId) ?: vehicle)
                val (downloaded, _) = downloadVehicleAssetsIfNeeded(ctx, fresh)
                total += downloaded
            } catch (e: Exception) {
                Log.w(TAG, "Post-sync vehicle download failed syncId=${vehicle.syncId}", e)
            }
        }
        Log.i(TAG, "Post-sync vehicle asset downloads: $total file(s)")
        total
    }

    /** Phase 11/18: download vehicle assets when manifest has remote refs but local files are missing. */
    suspend fun downloadVehicleIfNeeded(vehicleId: Int): Boolean = withContext(Dispatchers.IO) {
        val ctx = resolveContext(null) ?: return@withContext false
        prepareRcloneDestIfNeeded(ctx.dest, ctx.hint)
        val vehicle = vehicleRepository.getVehicleById(vehicleId) ?: return@withContext false
        val rebound = rebindOnDiskVehicleRefs(vehicle)
        val (_, changed) = downloadVehicleAssetsIfNeeded(ctx, rebound)
        changed
    }

    /**
     * Drop local photo pointers that are no longer readable. Preserves [FuelEntry.cloudManifest].
     * Persists only when something changed, via [FuelEntryRepository.updateFuelEntryPreservingTimestamp].
     * @return updated entry (or original if unchanged)
     */
    suspend fun scrubUnreadableFuelPhotos(entry: FuelEntry): FuelEntry = withContext(Dispatchers.IO) {
        val photos = FuelPhotoJson.parse(entry.photoUrl)
        if (photos.isEmpty()) return@withContext entry
        val kept = photos.filter { photoStorage.isLocalReadable(it.uri) }
        if (kept.size == photos.size) return@withContext entry
        val newUrl = FuelPhotoJson.serialize(kept)
        val updated = entry.copy(photoUrl = newUrl)
        fuelRepository.updateFuelEntryPreservingTimestamp(updated)
        updated
    }

    /**
     * Drop expense local photo pages that are no longer readable. Preserves cloudManifest.
     * Persists only when something changed, via preserving-timestamp update.
     */
    suspend fun scrubUnreadableExpensePhotos(entry: ExpenseEntry): ExpenseEntry = withContext(Dispatchers.IO) {
        val pages = ExpensePhotoUrls.parse(entry.photoUrl)
        if (pages.isEmpty()) {
            // Legacy non-empty dead single path still needs scrub if parse returned empty? parse always returns page for non-blank.
            if (!entry.photoUrl.isNullOrBlank() && !photoStorage.isLocalReadable(entry.photoUrl)) {
                val updated = entry.copy(photoUrl = null)
                expenseRepository.updateExpenseEntryPreservingTimestamp(updated)
                return@withContext updated
            }
            return@withContext entry
        }
        val kept = pages.filter { photoStorage.isLocalReadable(it.uri) }
        if (kept.size == pages.size) return@withContext entry
        val newUrl = ExpensePhotoUrls.format(kept)
        val updated = entry.copy(photoUrl = newUrl)
        expenseRepository.updateExpenseEntryPreservingTimestamp(updated)
        updated
    }

    /** Download expense receipt page(s) from cloud manifest when local photos missing (primary dest). */
    suspend fun downloadExpensePhoto(expense: ExpenseEntry): String? = withContext(Dispatchers.IO) {
        val ctx = resolveContext(null) ?: return@withContext null
        downloadExpensePhoto(expense, ctx)
    }

    /**
     * On-demand download of fuel fill photos (dash/pump) from cloud manifest.
     * Never called by bulk Sync now / background worker.
     * @return new photoUrl JSON/path or null if nothing downloaded
     */
    suspend fun downloadFuelPhoto(fuel: FuelEntry): String? = withContext(Dispatchers.IO) {
        val ctx = resolveContext(null) ?: return@withContext null
        downloadFuelPhoto(fuel, ctx)
    }

    private suspend fun downloadFuelPhoto(fuel: FuelEntry, ctx: SyncContext): String? =
        withContext(Dispatchers.IO) {
            prepareRcloneDestIfNeeded(ctx.dest, ctx.hint)
            val saveLocal = context.getSharedPreferences(SyncDestinationStore.PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean("save_fuel_photos", true)
            val provider = ctx.backend.manifestProvider()
            val rolesToRestore = fuelPhotoRolesForDest(fuel, ctx.dest.id, provider)
            if (rolesToRestore.isEmpty()) return@withContext null

            val existing = FuelPhotoJson.parse(fuel.photoUrl).associateBy { it.tag }.toMutableMap()
            var downloads = 0
            val bindings = mutableListOf<CloudManifest.DownloadBinding>()

            for (role in rolesToRestore) {
                val tag = fuelTagFromRole(role)
                val current = existing[tag]
                if (current != null && photoStorage.isLocalReadable(current.uri)) continue
                val fileId = CloudManifest.getFileId(
                    fuel.cloudManifest,
                    ctx.dest.id,
                    role,
                    provider,
                ) ?: continue
                val name = "fuel_${fuel.syncId}_$tag.jpg"
                val local = ctx.backend.downloadFile(
                    ctx.dest,
                    ctx.hint,
                    fileId,
                    name,
                    useMediaStore = saveLocal,
                    photoType = PhotoType.FUEL,
                )
                existing[tag] = FuelPhotoRef(tag = tag, uri = local, ts = System.currentTimeMillis())
                bindings.add(
                    CloudManifest.DownloadBinding(
                        role = role,
                        fileId = fileId,
                        name = name,
                    ),
                )
                downloads++
            }

            if (downloads == 0) return@withContext fuel.photoUrl

            val newPhotoUrl = FuelPhotoJson.serialize(existing.values.toList())
            var updated = fuel.copy(photoUrl = if (saveLocal) newPhotoUrl else null)
            if (bindings.isNotEmpty()) {
                val merged = CloudManifest.bindLocalDestAfterDownload(
                    fuel.cloudManifest,
                    ctx.dest.id,
                    bindings,
                    provider,
                )
                if (merged != fuel.cloudManifest) {
                    updated = updated.copy(cloudManifest = merged)
                }
            }
            fuelRepository.updateFuelEntryPreservingTimestamp(updated)
            newPhotoUrl
        }

    /**
     * Tag ↔ CloudManifest role for fuel photos (single mapping for upload / pending / download).
     *
     * Examples:
     * - `dash` ↔ `fuel_dash`
     * - `pump` ↔ `fuel_pump`
     * - `pump_2` ↔ `fuel_pump_2` (must NOT collapse to dash)
     * - `pump_N` ↔ `fuel_pump_N`
     */
    private fun fuelRoleForTag(tag: String): String {
        val t = tag.trim()
        return when {
            t == "dash" || t.startsWith("dash") -> CloudManifest.ROLE_FUEL_DASH
            t == "pump" -> CloudManifest.ROLE_FUEL_PUMP
            t.startsWith("pump_") -> "fuel_$t" // pump_2 → fuel_pump_2
            t.startsWith("pump") -> "fuel_$t"
            else -> CloudManifest.ROLE_FUEL_DASH
        }
    }

    private fun fuelTagFromRole(role: String): String = when (role) {
        CloudManifest.ROLE_FUEL_PUMP -> "pump"
        CloudManifest.ROLE_FUEL_DASH -> "dash"
        else -> {
            // fuel_pump_2 → pump_2; fuel_pump_N → pump_N; other fuel_* → strip prefix
            when {
                role.startsWith("fuel_pump_") -> role.removePrefix("fuel_") // pump_2
                role.startsWith("fuel_") -> role.removePrefix("fuel_")
                else -> role.ifBlank { "dash" }
            }
        }
    }

    private fun isFuelPhotoRole(role: String): Boolean =
        role == CloudManifest.ROLE_FUEL_DASH ||
            role == CloudManifest.ROLE_FUEL_PUMP ||
            role.startsWith("fuel_pump") ||
            (role.startsWith("fuel_") && role != CloudManifest.ROLE_VEHICLE_REF &&
                role != CloudManifest.ROLE_VEHICLE_REF_CLEANED)

    private fun fuelPhotoRolesForDest(
        fuel: FuelEntry,
        destId: String,
        provider: String,
    ): List<String> {
        val fromManifest = CloudManifest.parse(fuel.cloudManifest)
            .filter { isFuelPhotoRole(it.role) }
            .filter { entry ->
                entry.destId == destId ||
                    (provider != CloudManifest.PROVIDER_RCLONE &&
                        entry.provider == CloudManifest.PROVIDER_GOOGLE_DRIVE)
            }
            .map { it.role }
            .distinct()
        if (fromManifest.isNotEmpty()) return fromManifest
        // Prefer known roles if getFileId can resolve via fallback (legacy + any pump_N in local tags)
        val fromTags = parseFuelPhotos(fuel.photoUrl)
            ?.map { fuelRoleForTag(it.first) }
            ?.distinct()
            .orEmpty()
        val candidates = (listOf(CloudManifest.ROLE_FUEL_DASH, CloudManifest.ROLE_FUEL_PUMP) + fromTags)
            .distinct()
        return candidates.filter { role ->
            CloudManifest.getFileId(fuel.cloudManifest, destId, role, provider) != null
        }
    }

    /** Download expense receipt page(s) for a specific photo destination context. */
    private suspend fun downloadExpensePhoto(expense: ExpenseEntry, ctx: SyncContext): String? =
        withContext(Dispatchers.IO) {
        prepareRcloneDestIfNeeded(ctx.dest, ctx.hint)
        val saveLocal = context.getSharedPreferences(SyncDestinationStore.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("save_expense_photos", true)
        val provider = ctx.backend.manifestProvider()
        val rolesToRestore = expenseReceiptRolesForDest(expense, ctx.dest.id, provider)
        if (rolesToRestore.isEmpty()) return@withContext null

        val existingPages = ExpensePhotoUrls.parse(expense.photoUrl).associateBy { it.index }.toMutableMap()
        var downloads = 0
        val bindings = mutableListOf<CloudManifest.DownloadBinding>()

        for (role in rolesToRestore) {
            val pageIndex = ExpensePhotoUrls.pageIndexFromRole(role)
            val current = existingPages[pageIndex]
            if (current != null && photoStorage.isLocalReadable(current.uri)) continue
            val fileId = CloudManifest.getFileId(
                expense.cloudManifest,
                ctx.dest.id,
                role,
                provider,
            ) ?: continue
            val name = ExpensePhotoUrls.remoteFileName(expense.syncId, pageIndex)
            val local = ctx.backend.downloadFile(
                ctx.dest,
                ctx.hint,
                fileId,
                name,
                useMediaStore = saveLocal,
                photoType = PhotoType.EXPENSE,
            )
            existingPages[pageIndex] = ExpensePhotoUrls.Page(pageIndex, local)
            bindings.add(
                CloudManifest.DownloadBinding(
                    role = role,
                    fileId = fileId,
                    name = name,
                ),
            )
            downloads++
        }

        if (downloads == 0) return@withContext expense.photoUrl

        val newPhotoUrl = ExpensePhotoUrls.format(existingPages.values.toList())
        var updated = expense.copy(photoUrl = if (saveLocal) newPhotoUrl else null)
        if (bindings.isNotEmpty()) {
            val merged = CloudManifest.bindLocalDestAfterDownload(
                expense.cloudManifest,
                ctx.dest.id,
                bindings,
                provider,
            )
            if (merged != expense.cloudManifest) {
                updated = updated.copy(cloudManifest = merged)
            }
        }
        expenseRepository.updateExpenseEntryPreservingTimestamp(updated)
        newPhotoUrl
    }

    private fun expenseReceiptRolesForDest(
        expense: ExpenseEntry,
        destId: String,
        provider: String,
    ): List<String> {
        val fromManifest = CloudManifest.parse(expense.cloudManifest)
            .filter { ExpensePhotoUrls.isExpenseReceiptRole(it.role) }
            .filter { entry ->
                entry.destId == destId ||
                    (provider != CloudManifest.PROVIDER_RCLONE &&
                        entry.provider == CloudManifest.PROVIDER_GOOGLE_DRIVE)
            }
            .map { it.role }
            .distinct()
        if (fromManifest.isNotEmpty()) return fromManifest.sortedBy { ExpensePhotoUrls.pageIndexFromRole(it) }
        val legacyFileId = CloudManifest.getFileId(
            expense.cloudManifest,
            destId,
            CloudManifest.ROLE_EXPENSE_RECEIPT,
            provider,
        )
        return if (legacyFileId != null) listOf(CloudManifest.ROLE_EXPENSE_RECEIPT) else emptyList()
    }

    private data class PendingBreakdown(val upload: Int, val download: Int) {
        val total: Int get() = upload + download
    }

    /** PENDING_ONLY: rebind vehicle ref paths from on-disk files before pending recount / early exit. */
    private suspend fun rebindAllVehiclesOnDisk(): Int {
        var rebindCount = 0
        for (vehicle in vehicleRepository.getAllIncludingDeleted().filter { !it.deleted }) {
            if (vehicle.syncId.isBlank()) continue
            val fresh = vehicleRepository.findBySyncId(vehicle.syncId) ?: vehicle
            val beforeRef = fresh.referenceDashPhotoUrl
            val beforeCleaned = fresh.cleanedReferenceDashPhotoUrl
            val rebound = rebindOnDiskVehicleRefs(fresh)
            if (rebound.referenceDashPhotoUrl != beforeRef ||
                rebound.cleanedReferenceDashPhotoUrl != beforeCleaned
            ) {
                rebindCount++
            }
        }
        return rebindCount
    }

    private fun photoDestLabel(dest: PhotoDestination): String =
        dest.displayName.ifBlank {
            when (dest.provider) {
                PhotoProvider.ONEDRIVE ->
                    RcloneDestConfig.parse(dest.configJson)?.pathPrefix?.ifBlank { "OneDrive" } ?: "OneDrive"
                PhotoProvider.S3 ->
                    RcloneDestConfig.parse(dest.configJson)?.pathPrefix?.ifBlank { "S3" } ?: "S3"
                PhotoProvider.OTHER ->
                    RcloneDestConfig.parse(dest.configJson)?.remote?.ifBlank { "Other" } ?: "Other"
                else -> dest.folderName.ifBlank { "Drive" }
            }
        }

    private suspend fun computePendingBreakdown(dest: PhotoDestination): PendingBreakdown {
        var upload = 0
        var download = 0
        val saveFuelLocal = context.getSharedPreferences(SyncDestinationStore.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("save_fuel_photos", true)

        for (vehicle in vehicleRepository.getAllIncludingDeleted().filter { !it.deleted }) {
            if (vehicle.syncId.isBlank()) continue
            if (photoStorage.isLocalReadable(vehicle.referenceDashPhotoUrl) &&
                !CloudManifest.hasEntryForDest(vehicle.cloudManifest, dest.id, CloudManifest.ROLE_VEHICLE_REF)
            ) upload++
            if (photoStorage.isLocalReadable(vehicle.cleanedReferenceDashPhotoUrl) &&
                !CloudManifest.hasEntryForDest(vehicle.cloudManifest, dest.id, CloudManifest.ROLE_VEHICLE_REF_CLEANED)
            ) upload++
            val refId = CloudManifest.getFileId(
                vehicle.cloudManifest,
                dest.id,
                CloudManifest.ROLE_VEHICLE_REF,
                manifestProviderFor(dest),
            )
            if (refId != null && !photoStorage.isLocalReadable(vehicle.referenceDashPhotoUrl) &&
                photoStorage.existingVehicleRefPath(vehicle.syncId, cleaned = false) == null
            ) {
                val viaFallback = !CloudManifest.hasEntryForDest(
                    vehicle.cloudManifest, dest.id, CloudManifest.ROLE_VEHICLE_REF,
                )
                Log.d(
                    TAG,
                    "Pending vehicle_ref download syncId=${vehicle.syncId} fileId=$refId fallback=$viaFallback",
                )
                download++
            }
            val cleanedId = CloudManifest.getFileId(
                vehicle.cloudManifest,
                dest.id,
                CloudManifest.ROLE_VEHICLE_REF_CLEANED,
                manifestProviderFor(dest),
            )
            if (cleanedId != null && !photoStorage.isLocalReadable(vehicle.cleanedReferenceDashPhotoUrl) &&
                photoStorage.existingVehicleRefPath(vehicle.syncId, cleaned = true) == null
            ) {
                val viaFallback = !CloudManifest.hasEntryForDest(
                    vehicle.cloudManifest, dest.id, CloudManifest.ROLE_VEHICLE_REF_CLEANED,
                )
                Log.d(
                    TAG,
                    "Pending vehicle_ref_cleaned download syncId=${vehicle.syncId} fileId=$cleanedId fallback=$viaFallback",
                )
                download++
            }
        }

        for (fuel in fuelRepository.getAllIncludingDeleted().filter { !it.deleted }) {
            val photos = parseFuelPhotos(fuel.photoUrl) ?: continue
            for ((tag, uri) in photos) {
                if (!photoStorage.isLocalReadable(uri)) continue
                val role = fuelRoleForTag(tag)
                if (!CloudManifest.hasEntryForDest(fuel.cloudManifest, dest.id, role)) upload++
            }
            if (!saveFuelLocal && fuel.photoUrl != null &&
                CloudManifest.parse(fuel.cloudManifest).any { it.destId == dest.id }
            ) {
                // uploaded without local keep — not pending
            }
        }

        for (expense in expenseRepository.getAllIncludingDeleted().filter { !it.deleted }) {
            // D2: pending download badge = vehicle refs only; expense archives are on-demand.
            // Expense pending = upload only (never count missing receipts as downloads).
            if (expenseNeedsUpload(expense, dest)) upload++
        }

        return PendingBreakdown(upload = upload, download = download)
    }

    /** Phase 15: after manifest change, best-effort sheet sync so Cloud Manifest column updates. */
    private suspend fun bestEffortSheetSync(accountHint: String?) {
        try {
            spreadsheetSyncCoordinator.syncNow(accountHint)
        } catch (e: Exception) {
            Log.w(TAG, "Best-effort sheet sync after manifest change failed", e)
        }
    }

    private fun parseFuelPhotos(photoUrl: String?): List<Pair<String, String>>? {
        if (photoUrl.isNullOrBlank()) return null
        return try {
            if (photoUrl.trimStart().startsWith("[")) {
                val arr = JSONArray(photoUrl)
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val tag = obj.optString("tag", "dash")
                        val uri = obj.optString("uri", "")
                        if (uri.isNotBlank()) add(tag to uri)
                    }
                }
            } else {
                listOf("dash" to photoUrl)
            }
        } catch (_: Exception) {
            listOf("dash" to photoUrl)
        }
    }

    private fun recordPhotoResult(
        failureStore: SyncFailureStore,
        destId: String,
        destName: String,
        result: PhotoBackupResult,
    ) {
        if (result.success) {
            failureStore.clearPhotoFailure(destId)
        } else {
            failureStore.recordPhotoFailure(destId, destName)
        }
    }

    private data class SyncContext(
        val store: SyncDestinationStore,
        val dest: PhotoDestination,
        val hint: String,
        val backend: PhotoSyncBackend,
    )

    private fun resolveContext(accountHint: String?, destOverride: PhotoDestination? = null): SyncContext? {
        val store = SyncDestinationStore(context)
        val dest = destOverride ?: store.photoDestination() ?: return null
        val backend = backendRegistry.forDestination(dest) ?: return null
        if (!backend.isConfigured(dest)) return null
        val hint = accountHint?.takeIf { it.isNotBlank() } ?: dest.accountHint
        if (dest.provider == PhotoProvider.GOOGLE_DRIVE &&
            driveAuth.resolveAccountFromHint(hint) == null
        ) {
            return null
        }
        if (dest.provider.usesRcloneBackend()) {
            val config = RcloneDestConfig.parse(dest.configJson) ?: return null
            if (!RcloneConfStorage.hasConf(context, dest.id, config)) {
                return null
            }
        }
        return SyncContext(store, dest, hint, backend)
    }

    private fun notConfiguredOrAuth(destOverride: PhotoDestination? = null): PhotoBackupResult {
        val store = SyncDestinationStore(context)
        val dest = destOverride ?: store.photoDestination()
        if (!store.isPhotoConfigured(dest)) {
            return PhotoBackupResult(false, "Photo destination not configured")
        }
        return when (dest?.provider) {
            PhotoProvider.ONEDRIVE -> PhotoBackupResult(false, "Sign in with Microsoft (OneDrive) first")
            PhotoProvider.S3 -> PhotoBackupResult(false, "Configure S3 keys and bucket first")
            PhotoProvider.OTHER -> PhotoBackupResult(false, "Create or select a remote first")
            else -> PhotoBackupResult(false, "Sign in with Google (Drive) first")
        }
    }

    private fun manifestProviderFor(dest: PhotoDestination): String =
        backendRegistry.forDestination(dest)?.manifestProvider()
            ?: CloudManifest.PROVIDER_GOOGLE_DRIVE

    private suspend fun prepareRcloneDestIfNeeded(dest: PhotoDestination, accountHint: String?) {
        if (dest.provider != PhotoProvider.ONEDRIVE) return
        val config = RcloneDestConfig.parse(dest.configJson) ?: return
        try {
            oneDriveSetup.refreshTokenIfPossible(dest.id, config, accountHint)
        } catch (e: Exception) {
            Log.w(TAG, "OneDrive token refresh failed destId=${dest.id}", e)
        }
    }

    private fun mapOneDriveErrorMessage(message: String): String {
        if (looksLikeOneDriveAuthFailure(message)) {
            return RcloneOneDriveSetup.SESSION_EXPIRED_MESSAGE
        }
        return message
    }

    private fun looksLikeOneDriveAuthFailure(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        val lower = message.lowercase()
        return lower.contains("unauthorized") ||
            lower.contains("401") ||
            lower.contains("invalid_grant") ||
            lower.contains("token") && (lower.contains("expired") || lower.contains("invalid")) ||
            lower.contains("authentication") ||
            lower.contains("auth") && lower.contains("fail")
    }

    private fun handleError(logMsg: String, e: Exception, dest: PhotoDestination? = null): PhotoBackupResult {
        Log.e(TAG, logMsg, e)
        val wrapped = DriveAuthRecovery.wrapIfRecoverable(e)
        if (wrapped is DriveRecoverableAuthException) {
            return PhotoBackupResult(
                success = false,
                message = wrapped.message ?: DriveAuthRecovery.NEED_REMOTE_CONSENT_MESSAGE,
                needsRemoteConsent = true,
                recoveryIntent = wrapped.recoveryIntent,
            )
        }
        val raw = DriveAuthRecovery.userMessage(wrapped)
        if (dest?.provider == PhotoProvider.ONEDRIVE || looksLikeOneDriveAuthFailure(raw)) {
            return PhotoBackupResult(false, mapOneDriveErrorMessage(raw))
        }
        return PhotoBackupResult(false, raw)
    }

    private fun persistFolderId(store: SyncDestinationStore, dest: PhotoDestination, folderId: String) {
        if (dest.folderId == folderId) return
        store.upsertPhoto(dest.copy(folderId = folderId))
    }

    private fun vehicleHasPendingWork(vehicle: Vehicle, dest: PhotoDestination): Boolean =
        vehicleNeedsUpload(vehicle, dest) || vehicleNeedsDownload(vehicle, dest)

    private fun vehicleNeedsUpload(vehicle: Vehicle, dest: PhotoDestination): Boolean {
        if (vehicle.syncId.isBlank()) return false
        if (photoStorage.isLocalReadable(vehicle.referenceDashPhotoUrl) &&
            !CloudManifest.hasEntryForDest(vehicle.cloudManifest, dest.id, CloudManifest.ROLE_VEHICLE_REF)
        ) return true
        if (photoStorage.isLocalReadable(vehicle.cleanedReferenceDashPhotoUrl) &&
            !CloudManifest.hasEntryForDest(vehicle.cloudManifest, dest.id, CloudManifest.ROLE_VEHICLE_REF_CLEANED)
        ) return true
        return false
    }

    private fun vehicleNeedsDownload(vehicle: Vehicle, dest: PhotoDestination): Boolean {
        if (vehicle.syncId.isBlank()) return false
        val refId = CloudManifest.getFileId(
            vehicle.cloudManifest,
            dest.id,
            CloudManifest.ROLE_VEHICLE_REF,
            manifestProviderFor(dest),
        )
        if (refId != null && !photoStorage.isLocalReadable(vehicle.referenceDashPhotoUrl) &&
            photoStorage.existingVehicleRefPath(vehicle.syncId, cleaned = false) == null
        ) return true
        val cleanedId = CloudManifest.getFileId(
            vehicle.cloudManifest,
            dest.id,
            CloudManifest.ROLE_VEHICLE_REF_CLEANED,
            manifestProviderFor(dest),
        )
        if (cleanedId != null && !photoStorage.isLocalReadable(vehicle.cleanedReferenceDashPhotoUrl) &&
            photoStorage.existingVehicleRefPath(vehicle.syncId, cleaned = true) == null
        ) return true
        return false
    }

    private fun fuelHasPendingUpload(fuel: FuelEntry, dest: PhotoDestination): Boolean {
        val photos = parseFuelPhotos(fuel.photoUrl) ?: return false
        for ((tag, uri) in photos) {
            if (!photoStorage.isLocalReadable(uri)) continue
            val role = fuelRoleForTag(tag)
            if (!CloudManifest.hasEntryForDest(fuel.cloudManifest, dest.id, role)) return true
        }
        return false
    }

    private fun expenseNeedsUpload(expense: ExpenseEntry, dest: PhotoDestination): Boolean {
        for (page in ExpensePhotoUrls.parse(expense.photoUrl)) {
            if (!photoStorage.isLocalReadable(page.uri)) continue
            val role = ExpensePhotoUrls.roleForPage(page.index)
            if (!CloudManifest.hasEntryForDest(expense.cloudManifest, dest.id, role)) return true
        }
        return false
    }

    companion object {
        private const val TAG = "PhotoBackupCoordinator"
    }
}