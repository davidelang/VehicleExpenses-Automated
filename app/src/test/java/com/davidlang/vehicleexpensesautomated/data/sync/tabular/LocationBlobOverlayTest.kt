package com.davidlang.vehicleexpensesautomated.data.sync.tabular

import com.davidlang.vehicleexpensesautomated.data.batch.FuelLocationJson
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Scenario tests for [LocationBlobOverlay] / [FuelLocationJson.mergeBlobs]
 * after library full-row LWW (parity with PolicySync S6 vehicle overlay coverage).
 *
 * Cases cataloged from mergeBlobs product rules:
 * 1) Place vs no place → place wins
 * 2) Confirmed place trumps unconfirmed
 * 3) Empty vs non-empty blob
 * 4) Coords-only vs place
 * 5) applyToFuelList / applyToExpenseList map by syncId
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LocationBlobOverlayTest {

    private fun place(
        name: String,
        confirmed: Boolean = false,
        lookedUpAt: Long? = null,
        lat: Double? = null,
        lon: Double? = null,
    ): String {
        val encoded = FuelLocationJson.encode(
            FuelLocationJson.Blob(
                lat = lat,
                lon = lon,
                name = name,
                address = "1 Main",
                confirmed = confirmed,
                lookedUpAt = lookedUpAt,
                source = "test",
            ),
        )
        assertNotNull(encoded)
        return encoded!!
    }

    private fun coordsOnly(lat: Double = 34.0, lon: Double = -118.0): String {
        val encoded = FuelLocationJson.encode(
            FuelLocationJson.Blob(lat = lat, lon = lon, accuracyM = 10.0, source = "device"),
        )
        assertNotNull(encoded)
        return encoded!!
    }

    private fun fuel(
        syncId: String,
        location: String?,
        updatedAt: Long,
    ): FuelEntry = FuelEntry(
        vehicleId = 1,
        odometer = 1000,
        gallons = 10.0,
        cost = 40.0,
        timestamp = 1L,
        location = location,
        syncId = syncId,
        updatedAt = updatedAt,
    )

    private fun expense(
        syncId: String,
        location: String?,
        updatedAt: Long,
    ): ExpenseEntry = ExpenseEntry(
        vehicleId = 1,
        amount = 20.0,
        description = "test",
        date = 1L,
        location = location,
        syncId = syncId,
        updatedAt = updatedAt,
    )

    /** S-L1: LWW winner is remote thin (coords only); local has place → place wins via mergeBlobs. */
    @Test
    fun fuel_placeWinsOverThinCoords() {
        val thick = place("Shell", confirmed = false)
        val thin = coordsOnly()
        val local = fuel("f1", thick, updatedAt = 100L)
        val remote = fuel("f1", thin, updatedAt = 200L)
        // Library LWW would pick remote (newer ts) including thin location cell
        val winner = remote
        val out = LocationBlobOverlay.applyFuel(winner, local, remote)
        val blob = FuelLocationJson.parseBlob(out.location)
        assertNotNull(blob)
        assertTrue(blob!!.hasPlace())
        assertEquals("Shell", blob.name)
        // ts remains LWW winner's
        assertEquals(200L, out.updatedAt)
    }

    /** S-L2: confirmed place beats unconfirmed even if unconfirmed is LWW row winner. */
    @Test
    fun fuel_confirmedPlaceTrumpsUnconfirmed() {
        val confirmed = place("Chevron", confirmed = true, lookedUpAt = 50L)
        val unconfirmed = place("Shell", confirmed = false, lookedUpAt = 99L)
        val local = fuel("f2", confirmed, updatedAt = 100L)
        val remote = fuel("f2", unconfirmed, updatedAt = 300L)
        val winner = remote // newer row
        val out = LocationBlobOverlay.applyFuel(winner, local, remote)
        val blob = FuelLocationJson.parseBlob(out.location)
        assertNotNull(blob)
        assertTrue(blob!!.confirmed)
        assertEquals("Chevron", blob.name)
    }

    /** S-L3: empty local location + remote place on winner path → remote place kept. */
    @Test
    fun fuel_emptyVsNonEmpty() {
        val remotePlace = place("Costco", confirmed = true)
        val local = fuel("f3", null, updatedAt = 100L)
        val remote = fuel("f3", remotePlace, updatedAt = 200L)
        val winner = remote
        val out = LocationBlobOverlay.applyFuel(winner, local, remote)
        assertEquals(FuelLocationJson.parseBlob(remotePlace)?.name, FuelLocationJson.parseBlob(out.location)?.name)
    }

    /** S-L4: expense overlay uses same mergeBlobs rules. */
    @Test
    fun expense_confirmedTrumpsUnconfirmed() {
        val confirmed = place("Office", confirmed = true)
        val unconfirmed = place("Cafe", confirmed = false)
        val local = expense("e1", confirmed, updatedAt = 10L)
        val remote = expense("e1", unconfirmed, updatedAt = 99L)
        val out = LocationBlobOverlay.applyExpense(remote, local, remote)
        val blob = FuelLocationJson.parseBlob(out.location)
        assertNotNull(blob)
        assertTrue(blob!!.confirmed)
        assertEquals("Office", blob.name)
    }

    /** S-L5: list apply maps by syncId; single-side key skips overlay. */
    @Test
    fun applyToFuelList_bySyncId() {
        val thick = place("Shell", confirmed = true)
        val thin = coordsOnly()
        val local = fuel("a", thick, 100L)
        val remote = fuel("a", thin, 200L)
        val onlyLocal = fuel("b", thick, 50L)
        val winners = listOf(remote, onlyLocal) // LWW-shaped list
        val out = LocationBlobOverlay.applyToFuelList(
            winners = winners,
            localBySyncId = mapOf("a" to local, "b" to onlyLocal),
            remoteBySyncId = mapOf("a" to remote),
        )
        val a = out.first { it.syncId == "a" }
        assertEquals("Shell", FuelLocationJson.parseBlob(a.location)?.name)
        val b = out.first { it.syncId == "b" }
        // only local — no overlay, location unchanged
        assertEquals(thick, b.location)
    }

    /** Direct mergeBlobs catalog (no entries) for regression of rule 1. */
    @Test
    fun mergeBlobs_placeOverNoPlace() {
        val withPlace = place("A")
        val coords = coordsOnly()
        val merged = FuelLocationJson.mergeBlobs(withPlace, coords, updatedAtA = 1L, updatedAtB = 99L)
        assertEquals("A", FuelLocationJson.parseBlob(merged)?.name)
    }
}
