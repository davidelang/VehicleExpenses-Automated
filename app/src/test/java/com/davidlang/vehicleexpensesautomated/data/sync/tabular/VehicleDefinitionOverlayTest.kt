package com.davidlang.vehicleexpensesautomated.data.sync.tabular

import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kotlin unit tests for [VehicleDefinitionOverlay] (source of truth).
 * Complements Python S6 in policysync_scenarios.py which only mirrors rules.
 */
class VehicleDefinitionOverlayTest {

    private fun vehicle(
        syncId: String,
        updatedAt: Long,
        name: String = "Car",
        odoL: Float? = null,
        odoT: Float? = null,
        odoR: Float? = null,
        odoB: Float? = null,
        otherL: Float? = null,
        otherT: Float? = null,
        otherR: Float? = null,
        otherB: Float? = null,
        landmarks: String? = null,
        cloudManifest: String? = null,
        refPhoto: String? = null,
        cleanedPhoto: String? = null,
    ): Vehicle = Vehicle(
        name = name,
        syncId = syncId,
        updatedAt = updatedAt,
        odometerCropLeft = odoL,
        odometerCropTop = odoT,
        odometerCropRight = odoR,
        odometerCropBottom = odoB,
        otherTextCropLeft = otherL,
        otherTextCropTop = otherT,
        otherTextCropRight = otherR,
        otherTextCropBottom = otherB,
        landmarkTextBlocksJson = landmarks,
        cloudManifest = cloudManifest,
        referenceDashPhotoUrl = refPhoto,
        cleanedReferenceDashPhotoUrl = cleanedPhoto,
    )

    private fun thickCrops(syncId: String, updatedAt: Long) = vehicle(
        syncId = syncId,
        updatedAt = updatedAt,
        odoL = 0.1f,
        odoT = 0.2f,
        odoR = 0.3f,
        odoB = 0.4f,
        otherL = 0.5f,
        otherT = 0.6f,
        otherR = 0.7f,
        otherB = 0.8f,
        landmarks = """[{"t":"odo"}]""",
        cloudManifest = """{"id":"m1"}""",
        refPhoto = "/data/local/ref.jpg",
        cleanedPhoto = "/data/local/ref_c.jpg",
    )

    private fun thin(syncId: String, updatedAt: Long) = vehicle(
        syncId = syncId,
        updatedAt = updatedAt,
        name = "Car-remote",
    )

    @Test
    fun thinWinner_getsOdoCropsFromThickLoser() {
        val local = thickCrops("v1", updatedAt = 100L)
        val remote = thin("v1", updatedAt = 200L)
        // LWW picks remote (newer) as winner
        val out = VehicleDefinitionOverlay.applyAfterLww(remote, local, remote)
        assertTrue(VehicleDefinitionOverlay.hasCompleteOdoCrops(out))
        assertEquals(0.1f, out.odometerCropLeft)
        assertEquals(0.4f, out.odometerCropBottom)
        assertEquals(200L, out.updatedAt)
        assertEquals("Car-remote", out.name)
    }

    @Test
    fun thinWinner_getsOtherTextCropsFromLoser() {
        val local = thickCrops("v1", updatedAt = 100L)
        val remote = thin("v1", updatedAt = 300L)
        val out = VehicleDefinitionOverlay.applyAfterLww(remote, local, remote)
        assertTrue(VehicleDefinitionOverlay.hasCompleteOtherCrops(out))
        assertEquals(0.5f, out.otherTextCropLeft)
        assertEquals(0.8f, out.otherTextCropBottom)
    }

    @Test
    fun thinWinner_getsLandmarksAndCloudManifest() {
        val local = thickCrops("v1", updatedAt = 50L)
        val remote = thin("v1", updatedAt = 90L)
        val out = VehicleDefinitionOverlay.applyAfterLww(remote, local, remote)
        assertEquals("""[{"t":"odo"}]""", out.landmarkTextBlocksJson)
        assertEquals("""{"id":"m1"}""", out.cloudManifest)
    }

    @Test
    fun pickPhoto_prefersInjectableChoice() {
        val local = vehicle(
            "v1",
            updatedAt = 100L,
            refPhoto = "/local/ref.jpg",
            cleanedPhoto = "/local/clean.jpg",
        )
        val remote = vehicle(
            "v1",
            updatedAt = 200L,
            refPhoto = null,
            cleanedPhoto = null,
        )
        // Prefer loser path when winner blank (default pickPhoto behavior)
        val outDefault = VehicleDefinitionOverlay.applyAfterLww(remote, local, remote)
        assertEquals("/local/ref.jpg", outDefault.referenceDashPhotoUrl)
        assertEquals("/local/clean.jpg", outDefault.cleanedReferenceDashPhotoUrl)

        // Injectable: always take second arg (loser) even if first set
        val winnerWithRemotePath = remote.copy(referenceDashPhotoUrl = "content://remote")
        val out = VehicleDefinitionOverlay.overlay(
            winner = winnerWithRemotePath,
            loser = local,
            pickPhoto = { _, existing -> existing },
        )
        assertEquals("/local/ref.jpg", out.referenceDashPhotoUrl)
    }

    @Test
    fun loserOfPair_remoteWinsOnlyIfStrictlyNewer() {
        val local = vehicle("v1", updatedAt = 100L)
        val remoteNewer = vehicle("v1", updatedAt = 200L)
        val remoteOlder = vehicle("v1", updatedAt = 50L)
        val remoteTie = vehicle("v1", updatedAt = 100L)

        assertEquals(local.syncId, VehicleDefinitionOverlay.loserOfPair(local, remoteNewer).syncId)
        assertEquals(local.updatedAt, VehicleDefinitionOverlay.loserOfPair(local, remoteNewer).updatedAt)
        // remote newer → loser is local
        assertEquals(100L, VehicleDefinitionOverlay.loserOfPair(local, remoteNewer).updatedAt)

        // local newer → loser is remote
        assertEquals(50L, VehicleDefinitionOverlay.loserOfPair(local, remoteOlder).updatedAt)

        // tie → prefer local winner → loser remote
        assertEquals(100L, VehicleDefinitionOverlay.loserOfPair(local, remoteTie).updatedAt)
        // same updatedAt on both; loser is remote object (name default Car for both — check identity via copy)
        val remoteTieNamed = remoteTie.copy(name = "Remote")
        assertEquals("Remote", VehicleDefinitionOverlay.loserOfPair(local, remoteTieNamed).name)
    }

    @Test
    fun applyToMergedList_bySyncId_singleSideNoOp() {
        val localThick = thickCrops("a", 100L)
        val remoteThin = thin("a", 200L)
        val onlyLocal = thickCrops("b", 50L)

        val winners = listOf(remoteThin, onlyLocal)
        val out = VehicleDefinitionOverlay.applyToMergedList(
            winners = winners,
            localBySyncId = mapOf("a" to localThick, "b" to onlyLocal),
            remoteBySyncId = mapOf("a" to remoteThin),
        )
        val a = out.first { it.syncId == "a" }
        assertTrue(VehicleDefinitionOverlay.hasCompleteOdoCrops(a))
        val b = out.first { it.syncId == "b" }
        // single-side: unchanged thick
        assertTrue(VehicleDefinitionOverlay.hasCompleteOdoCrops(b))
        assertEquals(onlyLocal.landmarkTextBlocksJson, b.landmarkTextBlocksJson)
    }

    @Test
    fun completeWinner_notOverwrittenByLoserCrops() {
        val local = thickCrops("v1", 100L).copy(
            odometerCropLeft = 9f,
            odometerCropTop = 9f,
            odometerCropRight = 9f,
            odometerCropBottom = 9f,
        )
        val remote = thickCrops("v1", 200L).copy(
            odometerCropLeft = 1f,
            odometerCropTop = 1f,
            odometerCropRight = 1f,
            odometerCropBottom = 1f,
            name = "RemoteThick",
        )
        // Remote wins LWW and already has complete crops — keep remote crops
        val out = VehicleDefinitionOverlay.applyAfterLww(remote, local, remote)
        assertEquals(1f, out.odometerCropLeft)
        assertEquals("RemoteThick", out.name)
    }

    @Test
    fun hasCompleteOdoCrops_requiresAllFour() {
        assertFalse(
            VehicleDefinitionOverlay.hasCompleteOdoCrops(
                vehicle("v", 1L, odoL = 1f, odoT = 1f, odoR = 1f, odoB = null),
            ),
        )
        assertTrue(
            VehicleDefinitionOverlay.hasCompleteOdoCrops(
                vehicle("v", 1L, odoL = 1f, odoT = 1f, odoR = 1f, odoB = 1f),
            ),
        )
        assertNull(thin("v", 1L).landmarkTextBlocksJson)
    }
}
