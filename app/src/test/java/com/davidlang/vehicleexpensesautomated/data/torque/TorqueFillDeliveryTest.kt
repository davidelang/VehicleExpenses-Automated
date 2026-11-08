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

/**
 * Contract samples and the fill-delivery rules.
 * Hashes are SHA-256 of the canonical lines in torque-ve-contract-20261108.md.
 */
class TorqueFillDeliveryTest {
    @Test
    fun schemaIs25() {
        assertEquals(25, APP_DB_SCHEMA_VERSION)
    }

    @Test
    fun contractSampleHashes() {
        assertEquals(
            "a78bf232b713d22b74debc56250077405e3b07baffc2a5d507577ffb449228fe",
            TorqueFillJson.sha256(SAMPLE_A),
        )
        assertEquals(479, SAMPLE_A.toByteArray(Charsets.UTF_8).size)
        assertEquals(
            "12195446d9e4992e52fa623b59ec45d9f1fc70a6fdf42667c6a51b746b2d4198",
            TorqueFillJson.sha256(SAMPLE_B),
        )
        assertEquals(482, SAMPLE_B.toByteArray(Charsets.UTF_8).size)
        assertEquals(
            "edc4d5c2efce7e51364daf5fb127004f93c5a3fe7a7a1c70282f37272976ac2a",
            TorqueFillJson.sha256(SAMPLE_C),
        )
        assertEquals(989, SAMPLE_C.toByteArray(Charsets.UTF_8).size)
        assertEquals(SAMPLE_A, TorqueFillJson.canonical(TorqueFillJson.parse(SAMPLE_A)))
        assertEquals(SAMPLE_C, TorqueFillJson.canonical(TorqueFillJson.parse(SAMPLE_C)))
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
        assertEquals(SAMPLE_A, TorqueFillPayload.upsert(entry, vehicle, null).canonical)
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
        assertEquals(SAMPLE_B, TorqueFillPayload.void(entry, vehicle, "deleted", null).canonical)
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
        assertTrue(email.canonical.contains("\"odometerUnit\":\"mi\""))
        assertTrue(email.canonical.contains("\"vin\":\"opaque-vin\""))
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

    private companion object {
        const val SAMPLE_A =
            """{"cost":null,"economyIgnored":false,"fillExtent":"unknown","occurredAtEpochMs":0,"odometer":null,"operation":"upsert","provenance":{"fields":{},"occurrenceTimeQuality":"unknown","originDeviceId":null,"producerBuildAtResolution":null,"sourceKind":"legacy_unknown","sourceUpdatedAtEpochMs":null},"quantity":null,"rawTorqueOdometer":null,"vehicle":{"make":null,"model":null,"name":"Example vehicle","odometerUnit":"mi","syncId":"vehicle-1","vin":null,"year":null},"voidReason":null}"""
        const val SAMPLE_B =
            """{"cost":null,"economyIgnored":false,"fillExtent":"unknown","occurredAtEpochMs":0,"odometer":null,"operation":"void","provenance":{"fields":{},"occurrenceTimeQuality":"unknown","originDeviceId":null,"producerBuildAtResolution":null,"sourceKind":"legacy_unknown","sourceUpdatedAtEpochMs":null},"quantity":null,"rawTorqueOdometer":null,"vehicle":{"make":null,"model":null,"name":"Example vehicle","odometerUnit":"mi","syncId":"vehicle-1","vin":null,"year":null},"voidReason":"deleted"}"""
        const val SAMPLE_C =
            """{"cost":{"currency":"USD","value":"42.5"},"economyIgnored":false,"fillExtent":"full","occurredAtEpochMs":1794096000000,"odometer":{"observedAtEpochMs":1794096000000,"source":"torque","unit":"km","value":"68825.2"},"operation":"upsert","provenance":{"fields":{"/cost":{"confirmed":null,"observedAtEpochMs":null,"source":"email","sourceRef":null},"/odometer":{"confirmed":null,"observedAtEpochMs":1794096000000,"source":"torque","sourceRef":null},"/quantity":{"confirmed":null,"observedAtEpochMs":null,"source":"email","sourceRef":"café-1"}},"occurrenceTimeQuality":"source_reported","originDeviceId":null,"producerBuildAtResolution":null,"sourceKind":"mixed","sourceUpdatedAtEpochMs":null},"quantity":{"unit":"L","value":"38.800470786"},"rawTorqueOdometer":{"kilometers":"68825.2","observedAtEpochMs":1794096000000,"resolutionKm":"0.1"},"vehicle":{"make":null,"model":null,"name":"Example vehicle","odometerUnit":"mi","syncId":"vehicle-1","vin":"opaque-vin","year":null},"voidReason":null}"""
    }
}
