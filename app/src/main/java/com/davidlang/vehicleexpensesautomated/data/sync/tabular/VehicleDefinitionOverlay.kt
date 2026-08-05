package com.davidlang.vehicleexpensesautomated.data.sync.tabular

import com.davidlang.vehicleexpensesautomated.data.model.Vehicle

/**
 * Shared vehicle **definition-field** overlay after LWW.
 *
 * When the LWW winner is "thin" (missing crops / landmarks / manifest / local photos),
 * fill from the non-winning side. Used by coordinator after
 * [PolicySyncBridge.mergeVehiclesViaLwwRow]. Domain logic stays in VE
 * (not remotetable MergeSync).
 */
object VehicleDefinitionOverlay {

    fun hasCompleteOdoCrops(vehicle: Vehicle): Boolean =
        vehicle.odometerCropLeft != null && vehicle.odometerCropTop != null &&
            vehicle.odometerCropRight != null && vehicle.odometerCropBottom != null

    fun hasCompleteOtherCrops(vehicle: Vehicle): Boolean =
        vehicle.otherTextCropLeft != null && vehicle.otherTextCropTop != null &&
            vehicle.otherTextCropRight != null && vehicle.otherTextCropBottom != null

    /**
     * Which side is the non-winning definition donor for overlay.
     * Matches coordinator LWW: remote wins only if [remote.updatedAt] > [local.updatedAt];
     * ties prefer local → loser is remote.
     */
    fun loserOfPair(local: Vehicle, remote: Vehicle): Vehicle =
        when {
            remote.updatedAt > local.updatedAt -> local
            local.updatedAt > remote.updatedAt -> remote
            else -> remote
        }

    /**
     * Overlay definition fields from [loser] onto [winner] when winner is thin.
     *
     * @param pickPhoto prefer local-readable paths (inject [PhotoStorageManager.pickPreferredLocalPath]
     *   from coordinator; default keeps first non-blank).
     * @param log optional forensic logger (tag free; coordinator passes Log.i)
     */
    fun overlay(
        winner: Vehicle,
        loser: Vehicle,
        pickPhoto: (incoming: String?, existing: String?) -> String? = { a, b ->
            when {
                !a.isNullOrBlank() -> a
                !b.isNullOrBlank() -> b
                else -> null
            }
        },
        log: ((String) -> Unit)? = null,
    ): Vehicle {
        var result = winner
        if (!hasCompleteOdoCrops(result) && hasCompleteOdoCrops(loser)) {
            log?.invoke(
                "Vehicle overlay: odo crops from syncId=${loser.syncId} onto winner=${winner.syncId}",
            )
            result = result.copy(
                odometerCropLeft = loser.odometerCropLeft,
                odometerCropTop = loser.odometerCropTop,
                odometerCropRight = loser.odometerCropRight,
                odometerCropBottom = loser.odometerCropBottom,
            )
        }
        if (!hasCompleteOtherCrops(result) && hasCompleteOtherCrops(loser)) {
            log?.invoke(
                "Vehicle overlay: other-text crops from syncId=${loser.syncId} onto winner=${winner.syncId}",
            )
            result = result.copy(
                otherTextCropLeft = loser.otherTextCropLeft,
                otherTextCropTop = loser.otherTextCropTop,
                otherTextCropRight = loser.otherTextCropRight,
                otherTextCropBottom = loser.otherTextCropBottom,
            )
        }
        if (result.landmarkTextBlocksJson.isNullOrBlank() && !loser.landmarkTextBlocksJson.isNullOrBlank()) {
            log?.invoke(
                "Vehicle overlay: landmarks from syncId=${loser.syncId} onto winner=${winner.syncId}",
            )
            result = result.copy(landmarkTextBlocksJson = loser.landmarkTextBlocksJson)
        }
        if (result.cloudManifest.isNullOrBlank() && !loser.cloudManifest.isNullOrBlank()) {
            result = result.copy(cloudManifest = loser.cloudManifest)
        }
        val ref = pickPhoto(result.referenceDashPhotoUrl, loser.referenceDashPhotoUrl)
        val cleaned = pickPhoto(result.cleanedReferenceDashPhotoUrl, loser.cleanedReferenceDashPhotoUrl)
        if (ref != result.referenceDashPhotoUrl || cleaned != result.cleanedReferenceDashPhotoUrl) {
            result = result.copy(
                referenceDashPhotoUrl = ref,
                cleanedReferenceDashPhotoUrl = cleaned,
            )
        }
        return result
    }

    /**
     * Apply overlay for one syncId when both local and remote existed pre-merge.
     * If only one side, returns [winner] unchanged.
     */
    fun applyAfterLww(
        winner: Vehicle,
        local: Vehicle?,
        remote: Vehicle?,
        pickPhoto: (String?, String?) -> String? = { a, b ->
            when {
                !a.isNullOrBlank() -> a
                !b.isNullOrBlank() -> b
                else -> null
            }
        },
        log: ((String) -> Unit)? = null,
    ): Vehicle {
        if (local == null || remote == null) return winner
        val loser = loserOfPair(local, remote)
        return overlay(winner, loser, pickPhoto = pickPhoto, log = log)
    }

    /**
     * Map library (or any) LWW winners through definition overlay using pre-merge local/remote maps.
     */
    fun applyToMergedList(
        winners: List<Vehicle>,
        localBySyncId: Map<String, Vehicle>,
        remoteBySyncId: Map<String, Vehicle>,
        pickPhoto: (String?, String?) -> String? = { a, b ->
            when {
                !a.isNullOrBlank() -> a
                !b.isNullOrBlank() -> b
                else -> null
            }
        },
        log: ((String) -> Unit)? = null,
    ): List<Vehicle> =
        winners.map { w ->
            if (w.syncId.isBlank()) w
            else applyAfterLww(
                winner = w,
                local = localBySyncId[w.syncId],
                remote = remoteBySyncId[w.syncId],
                pickPhoto = pickPhoto,
                log = log,
            )
        }
}
