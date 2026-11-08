package com.davidlang.vehicleexpensesautomated.ui.fuel

import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.repository.VehicleRepository
import com.davidlang.vehicleexpensesautomated.ui.util.UnitFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TorqueLiveDecodeTest {
    private val vin = "1FTBW3XM5HKA12345"

    private val canVin = arrayOf(
        "7E8 10 14 49 02 01 31 46 54",
        "7E8 21 42 57 33 58 4D 35 48",
        "7E8 22 4B 41 31 32 33 34 35",
    )

    private val elmVin = arrayOf(
        "014",
        "0: 49 02 01 31 46 54",
        "1: 42 57 33 58 4D 35 48",
        "2: 4B 41 31 32 33 34 35",
    )

    @Test
    fun vinBlobStrips4902AndKeepsAnyLength() {
        assertEquals(vin, TorqueLiveDecode.vinBlob(canVin))
        assertEquals(vin, TorqueLiveDecode.vinBlob(elmVin))
        assertEquals("IOQ", TorqueLiveDecode.vinBlob(arrayOf("7E8 06 49 02 01 49 4F 51")))
        assertNull(TorqueLiveDecode.vinBlob(arrayOf("NO DATA")))
        assertNull(TorqueLiveDecode.vinBlob(arrayOf("")))
    }

    @Test
    fun disagreeingVinRespondersFail() {
        val frames = canVin + arrayOf(
            "7E9 10 14 49 02 01 31 46 54",
            "7E9 21 42 57 33 58 4D 35 48",
            "7E9 22 4B 41 31 32 33 34 36",
        )
        assertNull(TorqueLiveDecode.vinBlob(frames))
        val same = canVin + arrayOf(
            "7E9 10 14 49 02 01 31 46 54",
            "7E9 21 42 57 33 58 4D 35 48",
            "7E9 22 4B 41 31 32 33 34 35",
        )
        assertEquals(vin, TorqueLiveDecode.vinBlob(same))
    }

    @Test
    fun a6NeedsOnePositiveFourByteFrame() {
        assertEquals(10L, TorqueLiveDecode.a6Tenths(arrayOf("7E8 06 41 A6 00 00 00 0A")))
        assertEquals(10L, TorqueLiveDecode.a6Tenths(arrayOf("41 A6 00 00 00 0A 55 55")))
        assertNull(TorqueLiveDecode.a6Tenths(arrayOf("7E8 06 41 A6 00 00 00 00")))
        assertNull(TorqueLiveDecode.a6Tenths(arrayOf("7E8 03 41 A6 01")))
        assertNull(TorqueLiveDecode.a6Tenths(arrayOf("NO DATA")))
        assertNull(
            TorqueLiveDecode.a6Tenths(
                arrayOf(
                    "7E8 06 41 A6 00 00 00 0A",
                    "7E9 06 41 A6 00 00 00 0B",
                ),
            ),
        )
        assertEquals(
            10L,
            TorqueLiveDecode.a6Tenths(
                arrayOf(
                    "7E8 06 41 A6 00 00 00 0A",
                    "7E9 06 41 A6 00 00 00 0A",
                ),
            ),
        )
    }

    @Test
    fun convertsWithPlanFactorAndDoesNotWriteZero() {
        assertEquals(2, TorqueLiveDecode.storedOdometer(15L, UnitFormat.KM))
        assertEquals(1, TorqueLiveDecode.storedOdometer(15L, UnitFormat.MI))
        assertNull(TorqueLiveDecode.storedOdometer(8L, UnitFormat.MI))
        assertNull(TorqueLiveDecode.storedOdometer(0L, UnitFormat.KM))
        val miles = TorqueLiveDecode.storedOdometer(1609344L, UnitFormat.MI)
        assertEquals(100000, miles)
    }

    @Test
    fun blankVinDoesNotMatchAndExplicitMismatchDoesNotFill() {
        val reading = TorqueLiveDecode.Reading(vin, 15L, "15")
        val blank = vehicle(1, vin = null, opted = true)
        assertEquals(
            TorqueLiveDecode.PrefillKind.NONE,
            TorqueLiveDecode.prefill(reading, listOf(blank), null, false).kind,
        )
        val other = vehicle(2, vin = "OTHER", opted = true)
        val match = vehicle(3, vin = vin, opted = true, unit = UnitFormat.KM)
        val picked = TorqueLiveDecode.prefill(reading, listOf(other, match), 2, true)
        assertEquals(TorqueLiveDecode.PrefillKind.MISMATCH, picked.kind)
        val auto = TorqueLiveDecode.prefill(reading, listOf(other, match), 2, false)
        assertEquals(TorqueLiveDecode.PrefillKind.FILL, auto.kind)
        assertEquals(3, auto.vehicleId)
        assertEquals(2, auto.odometer)
        val unassigned = Vehicle(
            id = 0,
            name = VehicleRepository.UNASSIGNED_VEHICLE_NAME,
            syncId = VehicleRepository.UNASSIGNED_VEHICLE_SYNC_ID,
            vin = vin,
            integrateWithTorque = true,
            odometerUnit = UnitFormat.KM,
        )
        assertEquals(
            TorqueLiveDecode.PrefillKind.NONE,
            TorqueLiveDecode.prefill(reading, listOf(unassigned), null, false).kind,
        )
    }

    @Test
    fun dashPhotoAndTypingWinTheSavedSource() {
        val torque = TorqueLiveDecode.savedMark("2", false, false, 3, 3, "15")
        assertEquals(TorqueLiveDecode.SOURCE_TORQUE, torque.source)
        assertEquals("15", torque.rawTenthsKm)
        val typed = TorqueLiveDecode.savedMark("9", false, true, 3, 3, "15")
        assertEquals(TorqueLiveDecode.SOURCE_MANUAL, typed.source)
        assertNull(typed.rawTenthsKm)
        val photo = TorqueLiveDecode.savedMark("2", true, false, 3, 3, "15")
        assertEquals(TorqueLiveDecode.SOURCE_PHOTO, photo.source)
        assertNull(photo.rawTenthsKm)
        val none = TorqueLiveDecode.savedMark("", false, false, -1, 3, "")
        assertEquals("", none.source)
    }

    private fun vehicle(id: Int, vin: String?, opted: Boolean, unit: String = UnitFormat.MI) = Vehicle(
        id = id,
        name = "Car$id",
        vin = vin,
        integrateWithTorque = opted,
        odometerUnit = unit,
    )
}
