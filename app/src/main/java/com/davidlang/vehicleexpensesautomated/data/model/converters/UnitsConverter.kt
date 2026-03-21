package com.davidlang.vehicleexpensesautomated.data.model.converters

import androidx.room.TypeConverter
import com.davidlang.vehicleexpensesautomated.data.model.Units

class UnitsConverter {
    @TypeConverter
    fun fromUnits(units: Units?): String? = units?.name

    @TypeConverter
    fun toUnits(name: String?): Units? = name?.let { Units.valueOf(it) }
}
