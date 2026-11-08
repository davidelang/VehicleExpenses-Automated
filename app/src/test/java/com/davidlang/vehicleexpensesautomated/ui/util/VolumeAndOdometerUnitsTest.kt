package com.davidlang.vehicleexpensesautomated.ui.util

import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularSchema
import com.davidlang.vehicleexpensesautomated.ui.reports.lab.LabFullFillLeg
import com.davidlang.vehicleexpensesautomated.ui.reports.lab.excludeMpgOutliers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeAndOdometerUnitsTest {

    @Test
    fun gallonsAreStoredAsTyped_litersDivideOnce() {
        assertEquals(12.5, VolumeUnits.toStoredGallons(12.5, VolumeUnits.GALLONS), 0.0)
        assertEquals(1.0, VolumeUnits.toStoredGallons(VolumeUnits.GALLONS_PER_LITER, VolumeUnits.LITERS), 0.0)
    }

    @Test
    fun uneditedGallonsTextIsTheStoredNumber() {
        val stored = 10.123456789
        assertEquals(stored.toString(), VolumeUnits.editableVolumeText(stored, VolumeUnits.GALLONS))
        val parsed = VolumeUnits.editableVolumeText(stored, VolumeUnits.GALLONS).toDouble()
        assertEquals(stored, VolumeUnits.toStoredGallons(parsed, VolumeUnits.GALLONS), 0.0)
    }

    @Test
    fun uneditedLitersTextDisplaysTheSameFigure() {
        val text = VolumeUnits.editableVolumeText(10.0, VolumeUnits.LITERS)
        val stored = VolumeUnits.toStoredGallons(text.toDouble(), VolumeUnits.LITERS)
        assertEquals(text, VolumeUnits.editableVolumeText(stored, VolumeUnits.LITERS))
        assertEquals(
            VolumeUnits.halfUp(10.0 * VolumeUnits.GALLONS_PER_LITER, VolumeUnits.EDIT_VOLUME_DECIMALS),
            text.toDouble(),
            0.0,
        )
    }

    @Test
    fun distanceFactorIsKilometersPerMile() {
        assertEquals(1.609344, VolumeUnits.KILOMETERS_PER_MILE, 0.0)
        assertEquals(1.609344, VolumeUnits.convertDistance(1.0, UnitFormat.MI, UnitFormat.KM), 1e-12)
    }

    @Test
    fun blankOdometerUnitIsMiles_andKmDoesNotUseMpgClamp() {
        assertEquals(UnitFormat.MI, UnitFormat.normalizeOdometerUnit(null))
        assertEquals(UnitFormat.MI, UnitFormat.normalizeOdometerUnit("  "))
        assertEquals(UnitFormat.KM, UnitFormat.normalizeOdometerUnit("KM"))
        assertTrue(UnitFormat.usesMpgClamp(UnitFormat.MI))
        assertTrue(!UnitFormat.usesMpgClamp(UnitFormat.KM))
        assertEquals("mpg", UnitFormat.economyEfficiencyLabel(UnitFormat.MI))
        assertEquals("km/gal", UnitFormat.economyEfficiencyLabel(UnitFormat.KM))
    }

    @Test
    fun mpgClampDropsBelowFive_kmKeepsTheRatio() {
        val leg = LabFullFillLeg(
            endFill = fill(odometer = 100, timestamp = 2),
            prevFill = fill(odometer = 3, timestamp = 1),
            sumCostByCurrency = emptyMap(),
            sumVol = 1.0,
            mpg = 3.0,
            miles = 3,
        )
        assertTrue(excludeMpgOutliers(listOf(leg), applyMpgClamp = true).isEmpty())
        assertEquals(1, excludeMpgOutliers(listOf(leg), applyMpgClamp = false).size)
        val kept = LabFullFillLeg(
            endFill = fill(odometer = 40, timestamp = 2),
            prevFill = fill(odometer = 0, timestamp = 1),
            sumCostByCurrency = emptyMap(),
            sumVol = 1.0,
            mpg = 40.0,
            miles = 40,
        )
        assertEquals(1, excludeMpgOutliers(listOf(kept), applyMpgClamp = true).size)
    }

    @Test
    fun sheetBlankOdometerUnitStaysMiles_gallonsCellIsStoredGallons() {
        val headers = listOf("Sync ID", "ID", "Name", "Odometer Unit")
        val blank = TabularSchema.rowToVehicle(
            listOf("sid", "3", "Car", ""),
            TabularSchema.headerIndex(headers),
        )
        assertEquals(UnitFormat.MI, blank.odometerUnit)
        val km = Vehicle(id = 3, name = "Car", syncId = "sid", odometerUnit = UnitFormat.KM, odometerRolloverCount = 0)
        val row = TabularSchema.vehicleToRow(km)
        val back = TabularSchema.rowToVehicle(row, TabularSchema.headerIndex(TabularSchema.VEHICLE_HEADERS))
        assertEquals(UnitFormat.KM, back.odometerUnit)
        assertEquals(0, back.odometerRolloverCount)
        val entry = fill(odometer = 12345, gallons = 9.5)
        assertEquals("9.5", TabularSchema.fuelFieldMap(entry)["Gallons"])
        assertEquals("12345", TabularSchema.fuelFieldMap(entry)["Odometer"])
    }

    private fun fill(
        odometer: Int,
        timestamp: Long = 1,
        gallons: Double = 1.0,
    ) = FuelEntry(
        vehicleId = 1,
        odometer = odometer,
        gallons = gallons,
        cost = 4.0,
        timestamp = timestamp,
    )
}
