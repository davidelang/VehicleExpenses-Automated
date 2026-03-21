package com.davidlang.vehicleexpensesautomated.data.model.converters
import androidx.room.TypeConverter
import java.time.Instant
class InstantConverter {
    @TypeConverter fun fromInstant(instant: Instant?): Long? = instant?.toEpochMilli()
    @TypeConverter fun toInstant(millis: Long?): Instant? = millis?.let { Instant.ofEpochMilli(it) }
}
