package com.davidlang.vehicleexpensesautomated.data.email

import android.util.Log
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.repository.FuelEntryRepository
import com.davidlang.vehicleexpensesautomated.data.repository.VehicleRepository
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room writer for parsed email fuel receipts.
 * vehicleId=0, odo=0, isPartialFill=false, economyIgnored=false; skip if syncId exists.
 * Does not reimplement Unassigned vehicle creation (uses [VehicleRepository.ensureUnassignedVehicle]).
 */
@Singleton
class FuelReceiptIngest @Inject constructor(
    private val fuelEntryRepository: FuelEntryRepository,
    private val vehicleRepository: VehicleRepository,
) {
    data class Result(
        val inserted: Boolean,
        val skippedDuplicate: Boolean,
        val syncId: String,
        val entryId: Long? = null,
    )

    suspend fun ingest(
        parsed: ParsedFuelReceipt,
        gmailMessageId: String? = null,
        originDeviceId: String = EmailReceiptIds.ORIGIN_ANDROID_EMAIL_POLLER,
        /** Stable message key for Sync ID (Gmail id or IMAP Message-ID / folder|uid). */
        messageId: String? = gmailMessageId,
        /** Sync-ID provider segment: gmail | imap | fixture | … */
        messageProvider: String = "gmail",
    ): Result {
        vehicleRepository.ensureUnassignedVehicle()
        val mid = messageId ?: gmailMessageId
        val syncId = EmailReceiptIds.syncIdFor(parsed, mid, messageProvider)
        val existing = fuelEntryRepository.findBySyncId(syncId)
        if (existing != null) {
            Log.i(TAG, "skip duplicate syncId=$syncId")
            return Result(inserted = false, skippedDuplicate = true, syncId = syncId, entryId = existing.id)
        }
        val manifest = try {
            JSONObject()
                .put("src", "email")
                .put("provider", parsed.brand.lowercase())
                .put("msgId", mid ?: parsed.messageKey)
                .put("transport", messageProvider)
                .apply {
                    if (!parsed.siteId.isNullOrBlank()) put("siteId", parsed.siteId)
                    if (!parsed.product.isNullOrBlank()) put("product", parsed.product)
                }
                .toString()
        } catch (_: Exception) {
            null
        }
        val entry = FuelEntry(
            vehicleId = EmailReceiptIds.UNASSIGNED_VEHICLE_ID,
            odometer = 0,
            gallons = parsed.gallons,
            cost = parsed.cost,
            currency = parsed.currency.ifBlank { "USD" },
            timestamp = parsed.timestampMs,
            photoUrl = null,
            isPartialFill = false,
            economyIgnored = false,
            // location is sole geo/place field (legacy plain text still accepted)
            location = parsed.locationText,
            cloudManifest = manifest,
            deleted = false,
            deletedAt = null,
            syncId = syncId,
            originDeviceId = originDeviceId,
            updatedAt = System.currentTimeMillis(),
        )
        fuelEntryRepository.insertFuelEntry(entry)
        val written = fuelEntryRepository.findBySyncId(syncId)
        Log.i(TAG, "inserted fuel email receipt syncId=$syncId cost=${parsed.cost} gal=${parsed.gallons}")
        return Result(
            inserted = true,
            skippedDuplicate = false,
            syncId = syncId,
            entryId = written?.id,
        )
    }

    companion object {
        private const val TAG = "FuelReceiptIngest"
    }
}
