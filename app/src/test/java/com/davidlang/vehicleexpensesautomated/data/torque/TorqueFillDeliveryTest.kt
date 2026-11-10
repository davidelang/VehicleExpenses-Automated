package com.davidlang.vehicleexpensesautomated.data.torque

import com.davidlang.vehicleexpensesautomated.data.db.APP_DB_SCHEMA_VERSION
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.repository.VehicleRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contract samples and the fill-delivery rules.
 * Hashes are SHA-256 of the canonical lines in docs/specs/torque-ve-contract-20261108.md.
 */
class TorqueFillDeliveryTest {
    @Test
    fun schemaIs25() {
        assertEquals(25, APP_DB_SCHEMA_VERSION)
    }

    @Test
    fun contractSampleHashes() {
        val samples = contractSamples()
        assertEquals(3, samples.size)
        for (sample in samples) {
            assertEquals(sample.sha, TorqueFillJson.sha256(sample.json))
            assertEquals(sample.length, sample.json.toByteArray(Charsets.UTF_8).size)
            assertEquals(sample.json, TorqueFillJson.canonical(TorqueFillJson.parse(sample.json)))
        }
        val pointer = "The only contract text is `docs/specs/torque-ve-contract-20261108.md`."
        assertTrue(repoFile("docs/specs/torque-integration-plan-20261108.md").readText().contains(pointer))
        assertTrue(repoFile("dev-ai-interaction/research/torque-integration-plan-20261108.md").readText().contains(pointer))
        val sandbox = repoFile("dev-ai-interaction/research/torque-ve-contract-20261108.md").readText()
        assertTrue(sandbox.contains(pointer))
        assertFalse(sandbox.contains("SHA-256:"))
    }

    @Test
    fun incompleteUpsertMatchesSampleA() {
        val entry = FuelEntry(
            vehicleId = 1,
            odometer = 0,
            gallons = 0.0,
            cost = 0.0,
            timestamp = 0L,
            syncId = "fill-1",
        )
        val vehicle = Vehicle(
            id = 1,
            name = "Example vehicle",
            syncId = "vehicle-1",
            odometerUnit = "mi",
        )
        assertEquals(contractSamples()[0].json, TorqueFillPayload.upsert(entry, vehicle, null).canonical)
    }

    @Test
    fun voidMatchesSampleB() {
        val entry = FuelEntry(
            vehicleId = 1,
            odometer = 0,
            gallons = 0.0,
            cost = 0.0,
            timestamp = 0L,
            syncId = "fill-1",
        )
        val vehicle = Vehicle(
            id = 1,
            name = "Example vehicle",
            syncId = "vehicle-1",
        )
        assertEquals(contractSamples()[1].json, TorqueFillPayload.void(entry, vehicle, "deleted", null).canonical)
    }

    @Test
    fun wireUnitsAndExtent() {
        assertEquals("38.800470786", TorqueFillPayload.liters(10.25))
        assertEquals("68825.2", TorqueFillPayload.tenthsToKm("688252"))
        assertNull(TorqueFillPayload.tenthsToKm("0"))
        assertNull(TorqueFillPayload.tenthsToKm(null))
        assertEquals("16.09344", TorqueFillPayload.storedToKm(10, "mi"))
        assertEquals("10", TorqueFillPayload.storedToKm(10, "km"))
        assertNull(TorqueFillPayload.storedToKm(0, "mi"))

        val full = fuel(odometer = 100, gallons = 10.25, cost = 42.5, partial = false, ignored = true)
        assertEquals("full", TorqueFillPayload.extent(full))
        assertEquals("partial", TorqueFillPayload.extent(full.copy(isPartialFill = true, economyIgnored = false)))
        assertEquals("unknown", TorqueFillPayload.extent(full.copy(gallons = 0.0)))

        val torque = fuel(
            odometer = 42770,
            gallons = 10.25,
            cost = 42.5,
            source = "torque",
            raw = "688252",
            timestamp = 1_794_096_000_000L,
        )
        val frozen = TorqueFillPayload.upsert(torque, car(), null)
        assertTrue(frozen.canonical.contains("\"value\":\"68825.2\""))
        assertTrue(frozen.canonical.contains("\"kilometers\":\"68825.2\""))
        assertTrue(frozen.canonical.contains("\"fillExtent\":\"full\""))
        assertTrue(frozen.canonical.contains("\"economyIgnored\":false"))
        assertFalse(frozen.canonical.contains("\"odometer\":null"))
        val ignored = TorqueFillPayload.upsert(torque.copy(economyIgnored = true), car(), null)
        assertTrue(ignored.canonical.contains("\"fillExtent\":\"full\""))
        assertTrue(ignored.canonical.contains("\"economyIgnored\":true"))

        val zero = TorqueFillPayload.upsert(fuel(odometer = 0, gallons = 1.0, cost = 1.0), car(), null)
        assertTrue(zero.canonical.contains("\"odometer\":null"))
        assertTrue(zero.canonical.contains("\"rawTorqueOdometer\":null"))
        assertTrue(zero.canonical.contains("\"fillExtent\":\"unknown\""))

        val email = TorqueFillPayload.upsert(
            fuel(odometer = 10, gallons = 10.25, cost = 42.5).copy(costSource = "email", volumeSource = "email"),
            car(),
            null,
        )
        assertTrue(email.canonical.contains("\"/cost\""))
        assertTrue(email.canonical.contains("\"/quantity\""))
        assertTrue(email.canonical.contains("\"source\":\"email\""))
        assertTrue(email.canonical.contains("\"displayUnits\":\"imperial\""))
        assertTrue(email.canonical.contains("\"vin\":null"))
        assertFalse(email.canonical.contains("\"odometerUnit\""))
    }

    @Test
    fun highWaterAndAck() {
        val off = TorqueFillRules.decide(true, eligible = false, highWater = 3, deleted = false, trip = false, hardRemoved = false)
        assertEquals(TorqueFillRules.Kind.NONE, off.kind)
        val on = TorqueFillRules.decide(true, eligible = true, highWater = 3, deleted = false, trip = false, hardRemoved = false)
        assertEquals(TorqueFillRules.Kind.UPSERT, on.kind)
        val noop = TorqueFillRules.decide(false, eligible = true, highWater = 3, deleted = false, trip = false, hardRemoved = false)
        assertEquals(TorqueFillRules.Kind.NONE, noop.kind)
        val unsent = TorqueFillRules.decide(true, eligible = true, highWater = 0, deleted = false, trip = false, hardRemoved = true)
        assertEquals(TorqueFillRules.Kind.NONE, unsent.kind)
        val sent = TorqueFillRules.decide(true, eligible = true, highWater = 2, deleted = false, trip = false, hardRemoved = true)
        assertEquals(TorqueFillRules.Kind.VOID, sent.kind)
        assertEquals("deleted", sent.voidReason)
        val offDelete = TorqueFillRules.decide(true, eligible = false, highWater = 2, deleted = true, trip = false, hardRemoved = false)
        assertEquals(TorqueFillRules.Kind.NONE, offDelete.kind)

        val pending = TorqueFillOutboxEntity(
            syncId = "fill-1",
            revision = 4,
            payloadHash = "abc",
            canonicalPayload = "{}",
            vehicleId = 1,
            held = false,
        )
        assertNull(TorqueFillRules.applyAck(pending, "ACCEPTED", 4, "abc"))
        assertNull(TorqueFillRules.applyAck(pending, "DUPLICATE", 4, "abc"))
        assertNull(TorqueFillRules.applyAck(pending, "STALE", 4, "abc"))
        assertTrue(TorqueFillRules.applyAck(pending, "CONFLICT", 4, "abc")!!.held)
        assertTrue(TorqueFillRules.applyAck(pending, "INVALID_EVENT", 4, "abc")!!.held)
        assertFalse(TorqueFillRules.applyAck(pending, "RETRY_LATER", 4, "abc")!!.held)
        assertFalse(TorqueFillRules.applyAck(pending, "ACCEPTED", 3, "abc")!!.held)
        assertNull(TorqueFillRules.applyAck(null, "ACCEPTED", 4, "abc"))
        assertFalse(TorqueFillRules.eligible(car().copy(integrateWithTorque = false)))
        assertFalse(
            TorqueFillRules.eligible(
                car().copy(
                    id = 0,
                    syncId = VehicleRepository.UNASSIGNED_VEHICLE_SYNC_ID,
                    integrateWithTorque = true,
                ),
            ),
        )
    }

    private fun car() = Vehicle(
        id = 7,
        name = "Example vehicle",
        syncId = "vehicle-1",
        vin = "opaque-vin",
        odometerUnit = "mi",
        integrateWithTorque = true,
    )

    private fun fuel(
        odometer: Int,
        gallons: Double,
        cost: Double,
        partial: Boolean = false,
        ignored: Boolean = false,
        source: String = "manual",
        raw: String? = null,
        timestamp: Long = 1_794_096_000_000L,
    ) = FuelEntry(
        vehicleId = 7,
        odometer = odometer,
        gallons = gallons,
        cost = cost,
        currency = "USD",
        timestamp = timestamp,
        isPartialFill = partial,
        economyIgnored = ignored,
        syncId = "fill-1",
        odometerSource = source,
        torqueOdometerRawKm = raw,
    )

    private data class ContractSample(val sha: String, val length: Int, val json: String)

    private fun contractSamples(): List<ContractSample> {
        val text = repoFile("docs/specs/torque-ve-contract-20261108.md").readText()
        val found = SAMPLE.findAll(text).map { match ->
            ContractSample(
                sha = match.groupValues[1],
                length = match.groupValues[2].toInt(),
                json = match.groupValues[3],
            )
        }.toList()
        assertEquals(3, found.size)
        return found
    }

    private fun repoFile(relative: String): File {
        var dir = File(System.getProperty("user.dir") ?: ".")
        repeat(6) {
            val candidate = File(dir, relative)
            if (candidate.isFile) return candidate
            dir = dir.parentFile ?: return candidate
        }
        return File(relative)
    }

    private companion object {
        val SAMPLE = Regex(
            """SHA-256: `([0-9a-f]{64})`; byte length: (\d+)\.\r?\n```json\r?\n(.*)\r?\n```""",
        )
    }
}
